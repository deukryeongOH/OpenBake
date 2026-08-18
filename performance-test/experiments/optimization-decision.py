#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
from pathlib import Path
from typing import Any


def num(value: Any) -> float | None:
    if value in (None, "", "-"):
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def fmt(v: float | None, suffix: str = "", digits: int = 1) -> str:
    return "-" if v is None else f"{v:.{digits}f}{suffix}"


def parse_args() -> argparse.Namespace:
    here = Path(__file__).resolve().parent
    default_summary = here.parent / "capacity" / "capacity-summary.csv"
    p = argparse.ArgumentParser(description="OpenBake capacity 결과를 바탕으로 다음 성능 실험의 방향을 결정합니다.")
    p.add_argument("--summary", type=Path, default=default_summary)
    p.add_argument("--output", type=Path, default=here / "optimization-decision.md")
    p.add_argument("--p95-limit-ms", type=float, default=1500.0)
    p.add_argument("--p99-limit-ms", type=float, default=3000.0)
    return p.parse_args()


def main() -> int:
    args = parse_args()
    if not args.summary.exists():
        args.output.write_text(
            "# Optimization Decision\n\n"
            "**DEFER** — `capacity-summary.csv`가 없습니다. 먼저 Capacity Scan을 실행하세요.\n",
            encoding="utf-8",
        )
        print(f"DEFER: capacity summary 없음 -> {args.output}")
        return 0

    with args.summary.open(encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))

    if not rows:
        args.output.write_text(
            "# Optimization Decision\n\n**DEFER** — 분석 가능한 Capacity 결과가 없습니다.\n",
            encoding="utf-8",
        )
        print(f"DEFER: capacity row 없음 -> {args.output}")
        return 0

    def nfr_broken(r: dict[str, str]) -> bool:
        p95 = num(r.get("p95_ms"))
        p99 = num(r.get("p99_ms"))
        return ((p95 is not None and p95 >= args.p95_limit_ms) or
                (p99 is not None and p99 >= args.p99_limit_ms))

    failing = [r for r in rows if nfr_broken(r)]
    failing.sort(key=lambda r: int(r.get("users", "999999")) if str(r.get("users", "")).isdigit() else 999999)

    lines = [
        "# Phase 6 - Optimization Decision Gate",
        "",
        "> 이 문서는 root cause를 자동 확정하지 않습니다. Capacity + server-side lock metric으로 **다음 실험의 우선순위**만 정합니다.",
        "",
        f"- NFR: P95 < {args.p95_limit_ms:.0f}ms, P99 < {args.p99_limit_ms:.0f}ms",
        f"- 분석 run 수: {len(rows)}",
        f"- NFR 실패 run 수: {len(failing)}",
        "",
    ]

    if not failing:
        lines += [
            "## Decision",
            "",
            "**NO CHANGE YET** — 현재 수집 결과에서 Lock NFR 실패가 확인되지 않았습니다.",
            "",
            "현재 락 전략을 변경하지 말고 더 높은 부하 또는 반복 실행으로 Capacity Point를 먼저 확인하세요.",
            "",
        ]
        args.output.write_text("\n".join(lines), encoding="utf-8")
        print(f"NO CHANGE: NFR 실패 없음 -> {args.output}")
        return 0

    first = failing[0]
    users = first.get("users", "-")
    p95 = num(first.get("p95_ms"))
    p99 = num(first.get("p99_ms"))
    cpu = num(first.get("process_cpu_max_pct"))
    hikari_pending = num(first.get("hikari_pending_max"))
    hikari_active = num(first.get("hikari_active_max_pct"))
    wait = num(first.get("lock_wait_p95_max_ms"))
    hold = num(first.get("lock_hold_p95_max_ms"))
    decrease = num(first.get("lock_decrease_p95_max_ms"))
    wait_share = num(first.get("lock_wait_share_pct"))
    decrease_share = num(first.get("decrease_hold_share_pct"))
    lock_metrics = first.get("lock_metrics", "N")
    lock_timeout = num(first.get("lock_timeout")) or 0

    evidence = [
        f"- First failing load: **{users} VU**",
        f"- k6 P95/P99: **{fmt(p95, 'ms')} / {fmt(p99, 'ms')}**",
        f"- Lock wait P95: **{fmt(wait, 'ms')}** (wait/P95={fmt(wait_share, '%')})",
        f"- Lock hold P95: **{fmt(hold, 'ms')}**",
        f"- decreaseQuantity P95: **{fmt(decrease, 'ms')}** (decrease/hold={fmt(decrease_share, '%')})",
        f"- Process CPU max: **{fmt(cpu, '%')}**",
        f"- Hikari pending max: **{fmt(hikari_pending)}**, active/max: **{fmt(hikari_active, '%')}**",
        f"- Lock timeout count: **{int(lock_timeout)}**",
        f"- Custom lock metrics: **{lock_metrics}**",
    ]

    lines += ["## Evidence", "", *evidence, "", "## Decision", ""]

    decisions: list[str] = []
    next_steps: list[str] = []

    if lock_metrics != "Y":
        decisions.append("**DEFER LOCK CHANGE** — custom lock metric이 부족합니다.")
        next_steps.append("Phase 5 계측이 실제 Core에 적용됐는지 확인하고 동일 조건을 재실행합니다.")
    else:
        if hikari_pending is not None and hikari_pending > 0:
            decisions.append("**DB/CONNECTION PATH FIRST** — Hikari pending이 발생했습니다.")
            next_steps.append("`decreaseQuantity()` 내부 SQL/transaction/connection 점유 시간을 먼저 분해합니다.")
        elif hikari_active is not None and hikari_active >= 90:
            decisions.append("**DB POOL SATURATION CANDIDATE** — connection pool 사용률이 90% 이상입니다.")
            next_steps.append("pool 크기 변경 전에 query latency와 transaction duration을 먼저 확인합니다.")

        if cpu is not None and cpu >= 85:
            decisions.append("**CPU PATH FIRST** — process CPU가 포화 구간에 접근했습니다.")
            next_steps.append("JFR/프로파일링으로 hot method를 확인한 뒤 CPU 원인을 분리합니다.")

        if wait_share is not None and wait_share >= 60 and (hold is None or hold < 200):
            decisions.append("**LOCK SERIALIZATION CANDIDATE** — 응답 지연의 큰 비중이 lock wait이며 hold 자체는 상대적으로 짧습니다.")
            next_steps.append("락 제거/분산락 전환을 바로 하지 말고 DB 원자적 차감 또는 동시성 제어 전략을 별도 candidate branch에서 실험합니다.")

        if hold is not None and hold >= 200:
            if decrease_share is not None and decrease_share >= 80:
                decisions.append("**CRITICAL SECTION / decreaseQuantity CANDIDATE** — lock hold의 대부분을 `decreaseQuantity()`가 차지합니다.")
                next_steps.append("`DropLockService.decreaseQuantity()`와 repository/cart write 경로를 확보해 SQL 수와 transaction 범위를 줄일 수 있는지 검토합니다.")
            else:
                decisions.append("**LOCK HOLD CANDIDATE** — 임계영역 시간이 큽니다.")
                next_steps.append("임계영역 안의 각 작업을 추가 계측해 어떤 작업을 밖으로 이동할 수 있는지 검토합니다.")

        if lock_timeout > 0:
            decisions.append("**LOCK TIMEOUT IS A SYMPTOM** — 3초 timeout 자체를 늘리는 것은 capacity 개선으로 간주하지 않습니다.")
            next_steps.append("timeout 변경보다 wait/hold/decrease 원인을 먼저 줄입니다.")

    if not decisions:
        decisions.append("**NO SINGLE BOTTLENECK SIGNAL** — 현재 지표만으로 lock 전략 변경 근거가 부족합니다.")
        next_steps.append("동일 VU를 독립 Drop으로 3회 이상 반복하고 중앙값으로 다시 판단합니다.")

    lines.extend([f"- {d}" for d in decisions])
    lines += ["", "## Next experiment", ""]
    lines.extend([f"- {s}" for s in dict.fromkeys(next_steps)])
    lines += [
        "",
        "## Safety gate",
        "",
        "다음 중 하나라도 충족하지 않으면 Java lock 전략 변경은 보류합니다.",
        "",
        "- 동일 부하에서 baseline 반복 결과가 재현될 것",
        "- `DropLockService.decreaseQuantity()` 및 관련 repository/transaction 경계를 확인할 것",
        "- candidate 적용 전/후 동일한 사용자 수와 독립 Drop으로 비교할 것",
        "- 성공/품절/timeout/예상 외 오류 정합성이 baseline보다 악화되지 않을 것",
        "",
    ]

    args.output.write_text("\n".join(lines), encoding="utf-8")
    print(f"OK: optimization decision -> {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
