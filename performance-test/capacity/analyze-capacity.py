#!/usr/bin/env python3
from __future__ import annotations

import csv
import json
import os
import statistics
from collections import defaultdict
from pathlib import Path
from typing import Any

BASE = Path(__file__).resolve().parent.parent
RUNS = Path(os.environ.get("RESULTS_ROOT", BASE / "results" / "runs"))
OUT_DIR = Path(__file__).resolve().parent
OUT_CSV = OUT_DIR / "capacity-summary.csv"
OUT_MD = OUT_DIR / "capacity-summary.md"
OUT_GROUP_CSV = OUT_DIR / "capacity-repeat-summary.csv"
OUT_GROUP_MD = OUT_DIR / "capacity-repeat-summary.md"

LOCK_P95_LIMIT_MS = float(os.environ.get("LOCK_P95_LIMIT_MS", "1500"))
LOCK_P99_LIMIT_MS = float(os.environ.get("LOCK_P99_LIMIT_MS", "3000"))


def parse_metadata(path: Path) -> dict[str, str]:
    data: dict[str, str] = {}
    if not path.exists():
        return data
    for raw in path.read_text(encoding="utf-8").splitlines():
        if "=" not in raw:
            continue
        key, value = raw.split("=", 1)
        data[key] = value
    return data


def load_json(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}


def metric(summary: dict[str, Any], name: str, key: str):
    values = summary.get("metrics", {}).get(name, {}).get("values", {})
    return values.get(key, "")


def count(summary: dict[str, Any], name: str):
    values = summary.get("metrics", {}).get(name, {}).get("values", {})
    return values.get("count", "")


def obs_max(obs: dict[str, Any], name: str):
    return obs.get("metrics", {}).get(name, {}).get("stats", {}).get("max", "")


def obs_available(obs: dict[str, Any], name: str) -> bool:
    return bool(obs.get("metrics", {}).get(name, {}).get("available", False))


def num(value: Any) -> float | None:
    if value in (None, ""):
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def ratio_pct(numerator: Any, denominator: Any) -> float | str:
    n = num(numerator)
    d = num(denominator)
    if n is None or d is None or d <= 0:
        return ""
    return 100.0 * n / d


def fmt(value: Any, digits: int = 1) -> str:
    value = num(value)
    if value is None:
        return "-"
    return f"{value:.{digits}f}"


def diagnose(row: dict[str, Any]) -> str:
    """Return bottleneck *candidates*, never a confirmed root cause."""
    hints: list[str] = []
    cpu = num(row.get("process_cpu_max_pct"))
    heap = num(row.get("jvm_heap_max_pct"))
    gc = num(row.get("gc_pause_max_sec_per_sec"))
    tomcat = num(row.get("tomcat_busy_max_pct"))
    hikari_pending = num(row.get("hikari_pending_max"))
    hikari_active = num(row.get("hikari_active_max_pct"))
    server_5xx = num(row.get("server_5xx_max_rps"))
    p95 = num(row.get("p95_ms"))
    p99 = num(row.get("p99_ms"))
    lock_timeout = num(row.get("lock_timeout"))
    unexpected = num(row.get("unexpected"))

    lock_wait_p95 = num(row.get("lock_wait_p95_max_ms"))
    lock_hold_p95 = num(row.get("lock_hold_p95_max_ms"))
    decrease_p95 = num(row.get("lock_decrease_p95_max_ms"))
    waiters = num(row.get("lock_waiters_max"))
    wait_share = num(row.get("lock_wait_share_pct"))
    decrease_share = num(row.get("decrease_hold_share_pct"))
    lock_timeout_rate = num(row.get("lock_timeout_rate_max"))

    if cpu is not None and cpu >= 85:
        hints.append("CPU 포화 후보")
    if tomcat is not None and tomcat >= 80:
        hints.append("Tomcat thread 포화 후보")
    if hikari_pending is not None and hikari_pending > 0:
        hints.append("DB connection pool 대기 후보")
    elif hikari_active is not None and hikari_active >= 90:
        hints.append("DB connection pool 포화 근접")
    if heap is not None and heap >= 85 and gc is not None and gc >= 0.10:
        hints.append("Heap/GC pressure 후보")
    if server_5xx is not None and server_5xx > 0:
        hints.append("서버 5xx 발생")

    # Phase 5: server-side ReentrantLock instrumentation.
    if waiters is not None and waiters > 0 and lock_wait_p95 is not None and lock_wait_p95 >= 500:
        hints.append("ReentrantLock 대기 증가 후보")
    if wait_share is not None and wait_share >= 60:
        hints.append("응답 P95 대비 lock-wait P95 비중 큼")
    if lock_hold_p95 is not None and lock_hold_p95 >= 200:
        if decrease_share is not None and decrease_share >= 80:
            hints.append("임계영역의 대부분을 decreaseQuantity가 차지하는 후보")
        else:
            hints.append("Lock hold time 증가 후보")
    elif decrease_p95 is not None and decrease_p95 >= 200:
        hints.append("decreaseQuantity 지연 후보")

    if (lock_timeout is not None and lock_timeout > 0) or (lock_timeout_rate is not None and lock_timeout_rate > 0):
        hints.append("Lock timeout 발생")
    if unexpected is not None and unexpected > 0:
        hints.append("예상 외 비즈니스 오류")

    nfr_broken = (
            (p95 is not None and p95 >= LOCK_P95_LIMIT_MS)
            or (p99 is not None and p99 >= LOCK_P99_LIMIT_MS)
    )

    if nfr_broken and row.get("lock_metrics") != "Y":
        hints.append("custom lock metric 미수집 → Phase 5 계측 적용 필요")
    if nfr_broken and not hints:
        hints.append("서버 자원 포화 증거 없음 → DB/외부 의존성 추가 계측 필요")

    # Preserve order while removing duplicates.
    unique = list(dict.fromkeys(hints))
    return "; ".join(unique) if unique else "관측 지표상 뚜렷한 포화 신호 없음"


rows: list[dict[str, Any]] = []
if RUNS.exists():
    for run_dir in sorted(RUNS.iterdir()):
        if not run_dir.is_dir() or "lock-concurrency" not in run_dir.name:
            continue
        meta = parse_metadata(run_dir / "metadata.env")
        summary = load_json(run_dir / "summary.json")
        if not summary:
            continue
        obs = load_json(run_dir / "observability.json")
        row: dict[str, Any] = {
            "run": run_dir.name,
            "users": meta.get("user_count", ""),
            "drop_id": meta.get("drop_id", ""),
            "result": meta.get("result", ""),
            "p95_ms": metric(summary, "drop_lock_business_duration", "p(95)"),
            "p99_ms": metric(summary, "drop_lock_business_duration", "p(99)"),
            "http_p95_ms": metric(summary, "http_req_duration", "p(95)"),
            "http_failed_rate": metric(summary, "http_req_failed", "rate"),
            "success": count(summary, "drop_lock_success"),
            "sold_out": count(summary, "drop_lock_sold_out"),
            "lock_timeout": count(summary, "drop_lock_timeout"),
            "unexpected": count(summary, "drop_lock_unexpected"),
            "process_cpu_max_pct": obs_max(obs, "process_cpu_percent"),
            "jvm_heap_max_pct": obs_max(obs, "jvm_heap_percent"),
            "gc_pause_max_sec_per_sec": obs_max(obs, "gc_pause_seconds_per_second"),
            "tomcat_busy_max_pct": obs_max(obs, "tomcat_busy_percent"),
            "hikari_active_max_pct": obs_max(obs, "hikari_active_percent"),
            "hikari_pending_max": obs_max(obs, "hikari_pending"),
            "server_rps_max": obs_max(obs, "server_rps"),
            "server_5xx_max_rps": obs_max(obs, "server_5xx_rps"),
            "server_p95_max_ms": obs_max(obs, "server_p95_ms"),
            "lock_wait_p95_max_ms": obs_max(obs, "lock_wait_p95_ms"),
            "lock_wait_p99_max_ms": obs_max(obs, "lock_wait_p99_ms"),
            "lock_hold_p95_max_ms": obs_max(obs, "lock_hold_p95_ms"),
            "lock_decrease_p95_max_ms": obs_max(obs, "lock_decrease_p95_ms"),
            "lock_waiters_max": obs_max(obs, "lock_waiters"),
            "lock_holders_max": obs_max(obs, "lock_holders"),
            "lock_map_size_max": obs_max(obs, "lock_map_size"),
            "lock_timeout_rate_max": obs_max(obs, "lock_timeout_rate"),
            "lock_interrupted_rate_max": obs_max(obs, "lock_interrupted_rate"),
            "observability": "Y" if obs else "N",
            "lock_metrics": "Y" if obs_available(obs, "lock_wait_p95_ms") and obs_available(obs, "lock_hold_p95_ms") else "N",
        }
        row["lock_wait_share_pct"] = ratio_pct(row["lock_wait_p95_max_ms"], row["p95_ms"])
        row["decrease_hold_share_pct"] = ratio_pct(row["lock_decrease_p95_max_ms"], row["lock_hold_p95_max_ms"])
        row["bottleneck_candidates"] = diagnose(row)
        rows.append(row)

rows.sort(key=lambda r: (int(r["users"]) if str(r["users"]).isdigit() else 10**9, r["run"]))

fields = [
    "run", "users", "drop_id", "result", "p95_ms", "p99_ms",
    "http_p95_ms", "http_failed_rate", "success", "sold_out",
    "lock_timeout", "unexpected", "process_cpu_max_pct", "jvm_heap_max_pct",
    "gc_pause_max_sec_per_sec", "tomcat_busy_max_pct", "hikari_active_max_pct",
    "hikari_pending_max", "server_rps_max", "server_5xx_max_rps",
    "server_p95_max_ms", "lock_wait_p95_max_ms", "lock_wait_p99_max_ms",
    "lock_hold_p95_max_ms", "lock_decrease_p95_max_ms", "lock_waiters_max",
    "lock_holders_max", "lock_map_size_max", "lock_timeout_rate_max",
    "lock_interrupted_rate_max", "lock_wait_share_pct", "decrease_hold_share_pct",
    "observability", "lock_metrics", "bottleneck_candidates",
]
with OUT_CSV.open("w", newline="", encoding="utf-8") as f:
    writer = csv.DictWriter(f, fieldnames=fields)
    writer.writeheader()
    writer.writerows(rows)

lines = [
    "# OpenBake Lock Capacity + Observability Summary",
    "",
    f"- Lock NFR: P95 < {LOCK_P95_LIMIT_MS:.0f}ms, P99 < {LOCK_P99_LIMIT_MS:.0f}ms",
    "- Bottleneck 항목은 원인 확정이 아니라 **후보 신호**입니다.",
    "- `wait/P95`, `decrease/hold`는 서로 다른 percentile의 비율이므로 정확한 시간 분해가 아니라 방향성 비교용입니다.",
    "",
    "| Users | Result | k6 P95 | Lock wait P95 | wait/P95 | Hold P95 | decrease P95 | Waiters max | Hikari pending | CPU max | Lock metric | Candidate |",
    "|---:|:---:|---:|---:|---:|---:|---:|---:|---:|---:|:---:|---|",
]
for r in rows:
    lines.append(
        f"| {r['users']} | {r['result']} | {fmt(r['p95_ms'])}ms | {fmt(r['lock_wait_p95_max_ms'])}ms | "
        f"{fmt(r['lock_wait_share_pct'])}% | {fmt(r['lock_hold_p95_max_ms'])}ms | "
        f"{fmt(r['lock_decrease_p95_max_ms'])}ms | {fmt(r['lock_waiters_max'])} | "
        f"{fmt(r['hikari_pending_max'])} | {fmt(r['process_cpu_max_pct'])}% | {r['lock_metrics']} | "
        f"{r['bottleneck_candidates']} |"
    )
OUT_MD.write_text("\n".join(lines) + "\n", encoding="utf-8")

# Group repeated runs by VU count. This becomes useful when the same VU level is re-run
# with independent Drops to reduce warm-up/cache/GC noise.
groups: dict[int, list[dict[str, Any]]] = defaultdict(list)
for r in rows:
    if str(r["users"]).isdigit():
        groups[int(r["users"])].append(r)

group_rows: list[dict[str, Any]] = []
for users in sorted(groups):
    items = groups[users]
    p95s = [v for v in (num(x["p95_ms"]) for x in items) if v is not None]
    p99s = [v for v in (num(x["p99_ms"]) for x in items) if v is not None]
    wait_p95s = [v for v in (num(x["lock_wait_p95_max_ms"]) for x in items) if v is not None]
    passes = sum(1 for x in items if x.get("result") == "PASS")
    group_rows.append({
        "users": users,
        "runs": len(items),
        "pass_count": passes,
        "pass_rate": passes / len(items) if items else 0,
        "p95_median_ms": statistics.median(p95s) if p95s else "",
        "p95_min_ms": min(p95s) if p95s else "",
        "p95_max_ms": max(p95s) if p95s else "",
        "p99_median_ms": statistics.median(p99s) if p99s else "",
        "p99_min_ms": min(p99s) if p99s else "",
        "p99_max_ms": max(p99s) if p99s else "",
        "lock_wait_p95_median_ms": statistics.median(wait_p95s) if wait_p95s else "",
    })

group_fields = [
    "users", "runs", "pass_count", "pass_rate", "p95_median_ms", "p95_min_ms", "p95_max_ms",
    "p99_median_ms", "p99_min_ms", "p99_max_ms", "lock_wait_p95_median_ms",
]
with OUT_GROUP_CSV.open("w", newline="", encoding="utf-8") as f:
    writer = csv.DictWriter(f, fieldnames=group_fields)
    writer.writeheader()
    writer.writerows(group_rows)

group_lines = [
    "# OpenBake Lock Capacity Repeatability Summary",
    "",
    "동일 VU를 독립 Drop으로 반복 실행했을 때의 분산을 확인합니다.",
    "",
    "| Users | Runs | Pass | P95 median | P95 min~max | P99 median | Lock wait P95 median |",
    "|---:|---:|---:|---:|---:|---:|---:|",
]
for r in group_rows:
    group_lines.append(
        f"| {r['users']} | {r['runs']} | {r['pass_count']}/{r['runs']} | "
        f"{fmt(r['p95_median_ms'])}ms | {fmt(r['p95_min_ms'])}~{fmt(r['p95_max_ms'])}ms | "
        f"{fmt(r['p99_median_ms'])}ms | {fmt(r['lock_wait_p95_median_ms'])}ms |"
    )
OUT_GROUP_MD.write_text("\n".join(group_lines) + "\n", encoding="utf-8")

print(f"runs={len(rows)}")
print(f"csv={OUT_CSV}")
print(f"markdown={OUT_MD}")
print(f"repeat_csv={OUT_GROUP_CSV}")
print(f"repeat_markdown={OUT_GROUP_MD}")
