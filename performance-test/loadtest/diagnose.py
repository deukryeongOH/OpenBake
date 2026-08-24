#!/usr/bin/env python3
"""
부하 테스트 결과 판정 + 초과 시 원인 진단.

기준(NFR)
    P95 <= 1500ms
    P99 <= 3000ms

넘겼을 때 "느리다"만 남기면 다음에 뭘 고쳐야 하는지 알 수 없다.
그래서 서버 지표를 근거로 병목 후보를 좁혀 로그에 적는다.
판정 근거는 두 축이다.

    클라이언트(k6 summary.json)  응답시간·처리량·성공/실패
    서버(sample-backend.py CSV)  스레드 풀·CPU·커넥션·GC·Pod 수

사용 예:
    python3 diagnose.py --summary run/summary.json --sample run/sample.csv \\
        --scenario confirm --users 300 --expected-success 300 \\
        --log results/nfr-log.md
"""
from __future__ import annotations

import argparse
import csv
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

# 리포트에 화살표·em dash 를 쓰므로 콘솔 인코딩이 UTF-8 이 아니면 출력이 깨진다.
# 서버(Linux)에서는 문제없지만 Windows 콘솔에서 확인할 때를 대비한다.
for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        try:
            _stream.reconfigure(encoding="utf-8", errors="replace")
        except (ValueError, OSError):
            pass

P95_LIMIT_MS = 1500.0
P99_LIMIT_MS = 3000.0

# 병목 판정 임계값. 근거를 남기려고 상수로 모아둔다.
TOMCAT_BUSY_WARN_PCT = 80.0
POD_CPU_WARN = 0.85          # process_cpu_usage. CPU limit 1000m 기준 1.0 = 한도
NODE_CPU_WARN = 0.90         # system_cpu_usage
HEAP_WARN_PCT = 90.0
GC_WARN_SEC_PER_SEC = 0.10   # 시간의 10% 이상을 GC 로 쓰면 압박
HIKARI_PENDING_WARN = 1.0


# ---------------------------------------------------------------------------
# 입력 읽기
# ---------------------------------------------------------------------------

def load_summary(path: Path) -> dict[str, dict[str, float]]:
    """
    k6 --summary-export 결과를 {metric: {stat: value}} 로 정규화한다.
    k6 버전에 따라 값이 한 겹 더 감싸여 있어(values) 양쪽을 모두 받는다.
    """
    raw = json.loads(path.read_text(encoding="utf-8"))
    metrics = raw.get("metrics", raw)
    out: dict[str, dict[str, float]] = {}
    for name, body in metrics.items():
        if not isinstance(body, dict):
            continue
        values = body.get("values") if isinstance(body.get("values"), dict) else body
        out[name] = {k: v for k, v in values.items() if isinstance(v, (int, float))}
    return out


def load_sample(path: Path | None) -> list[dict[str, str]]:
    if not path or not path.exists():
        return []
    with path.open(encoding="utf-8", newline="") as handle:
        return [row for row in csv.DictReader(handle)]


def numbers(rows: list[dict[str, str]], column: str) -> list[float]:
    out = []
    for row in rows:
        text = (row.get(column) or "").strip()
        if not text:
            continue
        try:
            out.append(float(text))
        except ValueError:
            continue
    return out


def peak(rows: list[dict[str, str]], column: str) -> float | None:
    values = numbers(rows, column)
    return max(values) if values else None


def mean(rows: list[dict[str, str]], column: str) -> float | None:
    values = numbers(rows, column)
    return sum(values) / len(values) if values else None


# ---------------------------------------------------------------------------
# 진단
# ---------------------------------------------------------------------------

class Finding:
    def __init__(self, hit: bool, title: str, evidence: str, advice: str = ""):
        self.hit = hit
        self.title = title
        self.evidence = evidence
        self.advice = advice

    def render(self) -> list[str]:
        mark = "[X]" if self.hit else "[ ]"
        lines = [f"  {mark} {self.title}  —  {self.evidence}"]
        if self.hit and self.advice:
            for piece in self.advice.split("\n"):
                lines.append(f"        → {piece}")
        return lines


def fmt(value: float | None, suffix: str = "", digits: int = 2) -> str:
    return "측정값 없음" if value is None else f"{value:.{digits}f}{suffix}"


def _autoscale_observation(rows: list[dict[str, str]]) -> list[str]:
    """
    HPA 관련 사실을 판정 없이 기록한다.

    이 시나리오(per-vu-iterations 버스트)는 HPA 기본 평가 주기(15s)와 Pod
    기동 시간을 채우기 어렵다. 채우지 못한 실행에서 "replica가 안 늘었다"를
    findings 의 [X]로 붙이면 그 자체가 오진이라, 판정을 findings 와 분리한다.
    """
    MIN_WINDOW_SEC = 60.0  # HPA 평가를 여러 번 거치고 Pod 기동까지 볼 수 있는 최소 창

    hpa_installed = any((row.get("hpa_exists") or "").strip() == "true" for row in rows)
    if not hpa_installed:
        return [
            "HPA 미배포 상태다 (k8s/openbake/autoscaling 이 아직 클러스터에 apply되지 않았다). "
            "replica는 고정 1개이고, 이 실행은 오토스케일과 무관한 단일 인스턴스 기준선이다."
        ]

    elapsed = numbers(rows, "elapsed_sec")
    test_duration = max(elapsed) if elapsed else 0.0

    ready = numbers(rows, "replicas_ready")
    replica_min = min(ready) if ready else None
    replica_max = max(ready) if ready else None
    scaled = bool(ready) and replica_max != replica_min

    hpa_cur = peak(rows, "hpa_current_pct")
    hpa_target = peak(rows, "hpa_target_pct")
    replica_desc = f"replica {fmt(replica_min, digits=0)} -> {fmt(replica_max, digits=0)}"
    hpa_desc = f"HPA {fmt(hpa_cur, '%', 0)} / 목표 {fmt(hpa_target, '%', 0)}"

    notes: list[str] = []
    if test_duration < MIN_WINDOW_SEC:
        notes.append(
            f"테스트 길이 {test_duration:.0f}초는 HPA 평가/Pod 기동 시간을 채우기에 부족하다 "
            f"(권장 {MIN_WINDOW_SEC:.0f}초 이상, 버스트가 아닌 지속 부하 필요). "
            f"{replica_desc} 는 'HPA가 반응 안 했다'가 아니라 "
            f"'반응할 시간이 없었다'로 읽어야 한다."
        )
    elif hpa_cur is not None and hpa_target is not None and hpa_cur > hpa_target and not scaled:
        notes.append(
            f"테스트 길이 {test_duration:.0f}초로 평가 시간은 충분했는데도 "
            f"{hpa_desc} 인 채 replica가 늘지 않았다. 이건 실제로 살펴볼 문제다."
        )
    else:
        notes.append(f"{replica_desc}, {hpa_desc}. (테스트 길이 {test_duration:.0f}초)")

    if replica_max is not None and replica_max >= 2:
        notes.append(f"replica가 {replica_max:.0f}개까지 늘었다. maxReplicas=2 상한 도달.")

    return notes


def diagnose(summary: dict[str, dict[str, float]],
             rows: list[dict[str, str]],
             scenario: str) -> tuple[list[Finding], list[str], list[str]]:
    findings: list[Finding] = []
    notes: list[str] = []

    latency_metric = {
        "confirm": "confirm_entry_business_duration",
        "lock": "drop_lock_business_duration",
    }.get(scenario, "http_req_duration")
    latency = summary.get(latency_metric) or summary.get("http_req_duration", {})

    throughput = (summary.get("iterations", {}) or {}).get("rate")
    avg_ms = latency.get("avg")

    # --- 1. Tomcat 스레드 풀 ---
    busy_pct = peak(rows, "tomcat_busy_pct")
    busy = peak(rows, "tomcat_busy")
    max_threads = peak(rows, "tomcat_max")
    findings.append(Finding(
        hit=busy_pct is not None and busy_pct >= TOMCAT_BUSY_WARN_PCT,
        title="Tomcat 스레드 풀 포화",
        evidence=f"busy 최대 {fmt(busy, digits=0)}/{fmt(max_threads, digits=0)} "
                 f"({fmt(busy_pct, '%', 1)})",
        advice="요청이 처리 스레드를 기다리고 있다. 스레드가 CPU 를 쓰는 게 아니라\n"
               "DB·Redis 응답을 기다리는 중이면 스레드를 늘려도 지연은 그대로다.\n"
               "아래 커넥션/CPU 항목을 함께 보고 진짜 대기 지점을 정할 것.",
    ))

    # --- 2. Pod CPU (limit 1000m) ---
    pod_cpu = peak(rows, "process_cpu")
    findings.append(Finding(
        hit=pod_cpu is not None and pod_cpu >= POD_CPU_WARN,
        title="Pod CPU 한도 도달",
        evidence=f"process_cpu_usage 최대 {fmt(pod_cpu)} (1.0 = CPU limit 1000m 전부)",
        advice="애플리케이션이 CPU 를 다 썼다. 이 경우에만 replica 증설이 직접 효과가 있다.\n"
               "HPA maxReplicas 가 2 이므로 그 이상은 매니페스트를 고쳐야 한다.",
    ))

    # --- 3. 노드 전체 CPU ---
    node_cpu = peak(rows, "system_cpu")
    findings.append(Finding(
        hit=node_cpu is not None and node_cpu >= NODE_CPU_WARN,
        title="노드 CPU 포화",
        evidence=f"system_cpu_usage 최대 {fmt(node_cpu)}",
        advice="노드가 꽉 찼다. backend 는 nodeAffinity(required, node-pool=core)로\n"
               "node-a 에만 스케줄되므로 같은 노드의 다른 Pod 와 CPU 를 다툰다.\n"
               "Pod CPU 가 낮은데 이 값이 높으면, 느린 원인은 우리 앱이 아니라 이웃이다.",
    ))

    # --- 4. DB 커넥션 풀 ---
    pending = peak(rows, "hikari_pending")
    active = peak(rows, "hikari_active")
    pool_max = peak(rows, "hikari_max")
    findings.append(Finding(
        hit=pending is not None and pending >= HIKARI_PENDING_WARN,
        title="DB 커넥션 대기",
        evidence=f"pending 최대 {fmt(pending, digits=0)}, "
                 f"active 최대 {fmt(active, digits=0)}/{fmt(pool_max, digits=0)}",
        advice="커넥션을 못 얻어 대기한 요청이 있다. maximum-pool-size 가 미설정이면 기본 10 이다.\n"
               "다만 replica 를 늘리면 DB 커넥션 총량도 같이 늘어 Postgres 가 더 힘들어진다.\n"
               "풀 크기 조정을 먼저 검토할 것.",
    ))

    # --- 5. 힙 / GC ---
    heap_pct = peak(rows, "heap_pct")
    gc_rate = peak(rows, "gc_pause_sec_per_sec")
    findings.append(Finding(
        hit=(heap_pct is not None and heap_pct >= HEAP_WARN_PCT)
            or (gc_rate is not None and gc_rate >= GC_WARN_SEC_PER_SEC),
        title="힙 부족 / GC 압박",
        evidence=f"heap 최대 {fmt(heap_pct, '%', 1)}, "
                 f"GC {fmt(gc_rate, ' sec/sec', 3)}",
        advice="JAVA_TOOL_OPTIONS 가 -Xmx512m, 컨테이너 memory limit 은 1Gi 다.\n"
               "GC 가 시간의 10% 이상을 먹으면 그만큼 응답시간에 그대로 실린다.\n"
               "힙을 올리려면 memory limit 도 함께 올려야 OOMKilled 를 피한다.",
    ))

    # --- 6. 오토스케일 관측 ---
    # HPA는 병목 여부를 "진단"할 대상이 아니다. 평가할 시간을 못 준 테스트에서
    # replica가 안 늘었다고 [X]를 붙이면 그 자체가 오진이다. 그래서 findings와
    # 분리된 별도 사실 기록으로 둔다 — 판단은 사람이 하고 여긴 근거만 댄다.
    autoscale_notes = _autoscale_observation(rows)

    # --- 7. 응답시간 분해 (서버 내부 vs 네트워크/클라이언트) ---
    waiting = (summary.get("http_req_waiting", {}) or {}).get("p(95)")
    blocked = (summary.get("http_req_blocked", {}) or {}).get("p(95)")
    connecting = (summary.get("http_req_connecting", {}) or {}).get("p(95)")
    p95 = latency.get("p(95)")
    if waiting is not None and p95:
        share = 100 * waiting / p95
        findings.append(Finding(
            hit=share < 70,
            title="서버 밖 구간이 지연을 지배",
            evidence=f"p95 {p95:.0f}ms 중 서버 처리(waiting) {waiting:.0f}ms "
                     f"({share:.0f}%), blocked {fmt(blocked, 'ms', 0)}, "
                     f"connecting {fmt(connecting, 'ms', 0)}",
            advice="지연의 30% 이상이 서버 처리 밖(연결 수립·대기)에서 발생했다.\n"
                   "부하 생성기를 측정 대상과 같은 호스트에서 돌렸다면 그 경쟁이 원인일 수 있다.\n"
                   "별도 호스트에서 재측정할 것.",
        ))

    # http_req_blocked 는 TCP 연결 수립·TLS 핸드셰이크·k6 자체 커넥션 풀 대기다.
    # 서버가 응답을 늦게 준 게 아니라 연결 자체가 밀린 것이므로 위 waiting 기반
    # 판정과 별개로 명시한다. p95보다 blocked p95 가 더 큰 경우도 있는데(서로 다른
    # 요청 부분집합의 백분위라 수학적으로 가능하다), 그 자체가 "연결 단계가 크게
    # 흔들렸다"는 신호라 원인을 서버 쪽으로 잘못 돌리지 않도록 따로 잡는다.
    if blocked is not None and p95 and blocked >= p95 * 0.3:
        findings.append(Finding(
            hit=True,
            title="클라이언트 측 연결 대기 과다",
            evidence=f"http_req_blocked p95 {blocked:.0f}ms "
                     f"(전체 p95 {p95:.0f}ms 대비 {100 * blocked / p95:.0f}%)",
            advice="TCP 연결 수립·TLS 핸드셰이크·k6 커넥션 풀 대기 시간이다. 서버 처리 시간이 아니다.\n"
                   "부하 생성기를 측정 대상과 같은 호스트(특히 2 vCPU 노드)에서 돌리면\n"
                   "커넥션 수립 자체가 CPU 경쟁에 밀려 이 값이 부풀 수 있다.\n"
                   "별도 호스트에서 재측정해 이 값이 줄어드는지로 확인할 것.",
        ))

    # --- 8. 동시성 (Little's law) ---
    if throughput and avg_ms:
        in_flight = throughput * (avg_ms / 1000.0)
        limit_note = ""
        if max_threads:
            limit_note = f" / Tomcat max {max_threads:.0f}"
        notes.append(f"동시 처리량 추정 {in_flight:.0f}건"
                     f"{limit_note} (처리량 {throughput:.1f}/s x 평균 {avg_ms:.0f}ms)")
        if max_threads and in_flight >= max_threads * 0.9:
            findings.append(Finding(
                hit=True,
                title="동시 요청 수가 스레드 상한에 근접",
                evidence=f"in-flight {in_flight:.0f} vs Tomcat max {max_threads:.0f}",
                advice="스레드가 전부 잡혀 나머지는 커널 accept 큐에서 대기한다.\n"
                       "이 상태가 길어지면 연결 거부나 게이트웨이 타임아웃으로 번진다.",
            ))

    return findings, notes, autoscale_notes


def check_outcome(summary: dict[str, dict[str, float]], scenario: str,
                  users: int, expected_success: int | None,
                  expected_sold_out: int | None) -> list[str]:
    """성공/실패 개수가 기대와 맞는지 본다. 성능과 별개인 정합성 축이다."""
    lines: list[str] = []

    def count(name: str) -> float:
        return (summary.get(name, {}) or {}).get("count", 0.0)

    if scenario == "confirm":
        success = count("confirm_entry_success")
        target = expected_success if expected_success is not None else users
        ok = "OK" if success == target else "MISMATCH"
        lines.append(f"  [{ok}] 입장 확정 성공 {success:.0f} / 기대 {target}")
        for label, name in (("미인증(DR008)", "confirm_entry_unauthorized"),
                            ("드롭 없음(DR001)", "confirm_entry_drop_not_found"),
                            ("예상밖", "confirm_entry_unexpected")):
            value = count(name)
            if value:
                lines.append(f"  [WARN] {label} {value:.0f}건")
    elif scenario == "lock":
        success = count("drop_lock_success")
        sold_out = count("drop_lock_sold_out")
        ok_s = "OK" if expected_success is None or success == expected_success else "MISMATCH"
        ok_o = "OK" if expected_sold_out is None or sold_out == expected_sold_out else "MISMATCH"
        lines.append(f"  [{ok_s}] 재고 선점 성공 {success:.0f} / 기대 {expected_success}")
        lines.append(f"  [{ok_o}] 품절(DR007) {sold_out:.0f} / 기대 {expected_sold_out}"
                     f"   <- 재고보다 사용자가 많으면 정상 결과다")
        for label, name in (("상태 오류(DR014)", "drop_lock_invalid_state"),
                            ("예상밖", "drop_lock_unexpected")):
            value = count(name)
            if value:
                lines.append(f"  [WARN] {label} {value:.0f}건")

    failed = (summary.get("http_req_failed", {}) or {}).get("rate")
    if failed is not None:
        hint = ""
        if scenario == "lock" and expected_sold_out:
            hint = "   <- 품절(DR007)이 HTTP 400 이라 여기 포함된다. 판정에 쓰지 말 것"
        lines.append(f"  [INFO] http_req_failed {100 * failed:.2f}%{hint}")

    return lines


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="부하 테스트 NFR 판정 + 원인 진단")
    p.add_argument("--summary", type=Path, required=True, help="k6 --summary-export 결과")
    p.add_argument("--sample", type=Path, default=None, help="sample-backend.py CSV")
    p.add_argument("--scenario", required=True, choices=["confirm", "lock"])
    p.add_argument("--users", type=int, required=True)
    p.add_argument("--stock", type=int, default=None)
    p.add_argument("--expected-success", type=int, default=None)
    p.add_argument("--expected-sold-out", type=int, default=None)
    p.add_argument("--drop-id", default="")
    p.add_argument("--log", type=Path, default=None, help="이어붙일 로그 파일")
    p.add_argument("--out", type=Path, default=None, help="이번 실행 리포트 저장 경로")

    # 측정 신뢰도 — 단정하지 않고 실측값을 그대로 보여준다.
    # 값이 안 넘어오면(None) "확인 안 함"으로 표시하지, 좋다/나쁘다로 추측하지 않는다.
    p.add_argument("--active-profile", default="",
                   help="kubectl exec deployment/<name> -- printenv SPRING_PROFILES_ACTIVE 결과")
    p.add_argument("--hibernate-log-count", type=int, default=None,
                   help="kubectl logs ... | grep -c '^Hibernate:' 결과. show-sql 실제 동작 여부")
    p.add_argument("--load-generator-location", default="",
                   help="k6 를 실행한 위치. 측정 대상과 같은 호스트면 그 이름")
    return p.parse_args()


def main() -> int:
    args = parse_args()
    summary = load_summary(args.summary)
    rows = load_sample(args.sample)

    latency_metric = {
        "confirm": "confirm_entry_business_duration",
        "lock": "drop_lock_business_duration",
    }[args.scenario]
    latency = summary.get(latency_metric) or summary.get("http_req_duration", {})

    p95 = latency.get("p(95)")
    p99 = latency.get("p(99)")
    p95_over = p95 is not None and p95 > P95_LIMIT_MS
    p99_over = p99 is not None and p99 > P99_LIMIT_MS
    # p99 가 없으면 "통과"가 아니라 "판정 불가"다. summaryTrendStats 에 p(99) 를
    # 포함하지 않으면 k6 --summary-export 자체에서 빠지므로(기본값에 p99 없음),
    # 이 경우 verdict 를 함부로 PASS 로 단정하지 않는다.
    p99_unmeasured = p99 is None
    verdict = "FAIL" if (p95_over or p99_over) else ("UNKNOWN" if p99_unmeasured else "PASS")

    throughput = (summary.get("iterations", {}) or {}).get("rate")
    req_rate = (summary.get("http_reqs", {}) or {}).get("rate")
    total_reqs = (summary.get("http_reqs", {}) or {}).get("count")

    findings, notes, autoscale_notes = diagnose(summary, rows, args.scenario)
    outcome = check_outcome(summary, args.scenario, args.users,
                            args.expected_success, args.expected_sold_out)

    stamp = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%SZ")
    L: list[str] = []
    L.append("")
    L.append("=" * 70)
    L.append(f" [{verdict}] {args.scenario}  사용자 {args.users}명"
             + (f" / 재고 {args.stock}" if args.stock is not None else "")
             + (f" / drop {args.drop_id}" if args.drop_id else ""))
    L.append(f" {stamp}   표본 {len(rows)}행")
    L.append("=" * 70)
    L.append("")
    L.append(" [NFR 판정]  P95 <= 1500ms,  P99 <= 3000ms")
    L.append(f"   P95  {fmt(p95, 'ms', 0)}   {'초과' if p95_over else '통과'}")
    p99_status = "초과" if p99_over else ("측정 안 됨 — summaryTrendStats 확인 필요" if p99_unmeasured else "통과")
    L.append(f"   P99  {fmt(p99, 'ms', 0)}   {p99_status}")
    L.append(f"   p50 {fmt(latency.get('med'), 'ms', 0)}   "
             f"평균 {fmt(latency.get('avg'), 'ms', 0)}   "
             f"최소 {fmt(latency.get('min'), 'ms', 0)}   "
             f"최대 {fmt(latency.get('max'), 'ms', 0)}")
    L.append("")
    L.append(" [처리량]")
    L.append(f"   초당 처리량   {fmt(throughput, ' iter/s', 1)}")
    L.append(f"   초당 요청 수  {fmt(req_rate, ' req/s', 1)}  (총 {fmt(total_reqs, '건', 0)})")
    server_rps = peak(rows, "http_req_per_sec")
    if server_rps is not None:
        L.append(f"   서버측 최대   {fmt(server_rps, ' req/s', 1)}  (Pod 별 actuator 기준)")
    L.append("")
    L.append(" [자원 사용]")
    L.append(f"   Tomcat busy   최대 {fmt(peak(rows, 'tomcat_busy'), digits=0)}"
             f" / {fmt(peak(rows, 'tomcat_max'), digits=0)}"
             f"  ({fmt(peak(rows, 'tomcat_busy_pct'), '%', 1)})")
    L.append(f"   Pod CPU       최대 {fmt(peak(rows, 'process_cpu'))}"
             f"   평균 {fmt(mean(rows, 'process_cpu'))}   (1.0 = limit 1000m)")
    L.append(f"   노드 CPU      최대 {fmt(peak(rows, 'system_cpu'))}")
    L.append(f"   힙            최대 {fmt(peak(rows, 'heap_pct'), '%', 1)}"
             f"  ({fmt(peak(rows, 'heap_used_mb'), 'MB', 0)} / "
             f"{fmt(peak(rows, 'heap_max_mb'), 'MB', 0)})")
    L.append(f"   GC            최대 {fmt(peak(rows, 'gc_pause_sec_per_sec'), ' sec/sec', 3)}")
    L.append(f"   Hikari        active 최대 {fmt(peak(rows, 'hikari_active'), digits=0)}"
             f" / max {fmt(peak(rows, 'hikari_max'), digits=0)}"
             f",  pending 최대 {fmt(peak(rows, 'hikari_pending'), digits=0)}")
    L.append("")
    L.append(" [정합성]")
    L.extend(outcome)

    if verdict == "FAIL":
        L.append("")
        L.append(" [원인 진단]  기준 초과. 아래 [X] 항목이 병목 후보다.")
        for finding in findings:
            L.extend(finding.render())
        if not any(f.hit for f in findings):
            L.append("   해당하는 자원 포화 신호가 없다.")
            L.append("     → 앱 밖(Redis 직렬화, DB 쿼리 자체, 게이트웨이 홉)을 의심할 것.")
            L.append("     → 표본이 적으면(위 표본 행 수 확인) 포화를 놓쳤을 수 있다.")
    else:
        L.append("")
        L.append(" [원인 진단] 기준 통과. 참고용 신호만 표시한다.")
        for finding in findings:
            if finding.hit:
                L.extend(finding.render())

    L.append("")
    L.append(" [오토스케일 관측]  판정이 아니라 사실 기록이다.")
    for note in autoscale_notes:
        L.append(f"   - {note}")

    if notes:
        L.append("")
        L.append(" [참고]")
        for note in notes:
            L.append(f"   - {note}")

    L.append("")
    L.append(" [측정 신뢰도]")

    # SQL 로깅: 정적 추측 대신 실측값만 말한다.
    # application-prod.yml 이 show-sql=false / bind=WARN 으로 덮으므로
    # ConfigMap 에 오버라이드가 없다는 사실만으로 "로그가 켜져 있다"고 단정하면 오진이다.
    if args.hibernate_log_count is None:
        L.append("   - SQL 로깅: 확인 안 함. --hibernate-log-count 를 넘기면 실측한다.")
    elif args.hibernate_log_count > 0:
        L.append(f"   - SQL 로깅이 실제로 켜져 있다 (Hibernate 로그 {args.hibernate_log_count}줄).")
        L.append("     모든 요청이 쿼리를 stdout 에 쓰면서 스레드가 콘솔 락에 직렬화된다.")
        L.append(f"     활성 프로파일: {args.active_profile or '확인 안 함'}")
    else:
        L.append(f"   - SQL 로깅 꺼져 있음 (Hibernate 로그 0줄, "
                 f"프로파일 {args.active_profile or '확인 안 함'}). 측정 왜곡 없음.")

    if args.load_generator_location:
        L.append(f"   - 부하 생성기 위치: {args.load_generator_location}")

    L.append("   - startupProbe 유예가 240초다. 재기동 직후 측정하면 JIT 미예열로 느리게 나온다.")
    if len(rows) < 5:
        L.append(f"   - 서버 표본이 {len(rows)}행뿐이다. 버스트가 짧아 자원 포화를 놓쳤을 수 있다.")
    L.append("=" * 70)

    report = "\n".join(L)
    print(report)

    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(report + "\n", encoding="utf-8")
    if args.log:
        args.log.parent.mkdir(parents=True, exist_ok=True)
        with args.log.open("a", encoding="utf-8") as handle:
            handle.write(report + "\n")

    # UNKNOWN(p99 미측정)도 0으로 넘기지 않는다. 판정 못 한 걸 통과로 오인하면 안 된다.
    return 0 if verdict == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())