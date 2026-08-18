#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import statistics
from collections import defaultdict
from pathlib import Path
from typing import Any


def parse_args() -> argparse.Namespace:
    here = Path(__file__).resolve().parent
    perf = here.parent
    p = argparse.ArgumentParser(description="OpenBake baseline/candidate 성능 실험을 VU별로 비교합니다.")
    p.add_argument("--runs", type=Path, default=perf / "results" / "runs")
    p.add_argument("--baseline", default="baseline")
    p.add_argument("--candidate", default="candidate")
    p.add_argument("--output-dir", type=Path, default=here)
    p.add_argument("--material-change-pct", type=float, default=10.0,
                   help="이 값 이상의 P95 변화만 실질 변화로 표시합니다. 통계적 유의성 검정은 아닙니다.")
    return p.parse_args()


def meta(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    if not path.exists():
        return result
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" in line:
            k, v = line.split("=", 1)
            result[k] = v
    return result


def load(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}


def n(v: Any) -> float | None:
    if v in (None, ""):
        return None
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def summary_metric(data: dict[str, Any], name: str, key: str) -> float | None:
    return n(data.get("metrics", {}).get(name, {}).get("values", {}).get(key))


def summary_count(data: dict[str, Any], name: str) -> float:
    return n(data.get("metrics", {}).get(name, {}).get("values", {}).get("count")) or 0.0


def obs_max(data: dict[str, Any], name: str) -> float | None:
    return n(data.get("metrics", {}).get(name, {}).get("stats", {}).get("max"))


def med(values: list[float | None]) -> float | None:
    xs = [v for v in values if v is not None]
    return statistics.median(xs) if xs else None


def pct_delta(base: float | None, cand: float | None) -> float | None:
    if base is None or cand is None or base == 0:
        return None
    return 100.0 * (cand - base) / base


def fmt(v: float | None, suffix: str = "", digits: int = 1) -> str:
    return "-" if v is None else f"{v:.{digits}f}{suffix}"


def main() -> int:
    args = parse_args()
    groups: dict[tuple[str, int], list[dict[str, Any]]] = defaultdict(list)

    if not args.runs.exists():
        print(f"ERROR: run directory 없음: {args.runs}")
        return 2

    for d in sorted(args.runs.iterdir()):
        if not d.is_dir():
            continue
        m = meta(d / "metadata.env")
        variant = m.get("experiment_variant", "")
        if variant not in {args.baseline, args.candidate}:
            continue
        try:
            users = int(m.get("user_count", ""))
        except ValueError:
            continue
        s = load(d / "summary.json")
        if not s:
            continue
        o = load(d / "observability.json")
        groups[(variant, users)].append({
            "run": d.name,
            "p95": summary_metric(s, "drop_lock_business_duration", "p(95)"),
            "p99": summary_metric(s, "drop_lock_business_duration", "p(99)"),
            "lock_wait": obs_max(o, "lock_wait_p95_ms"),
            "lock_hold": obs_max(o, "lock_hold_p95_ms"),
            "decrease": obs_max(o, "lock_decrease_p95_ms"),
            "cpu": obs_max(o, "process_cpu_percent"),
            "hikari_pending": obs_max(o, "hikari_pending"),
            "lock_timeout": summary_count(s, "drop_lock_timeout"),
            "unexpected": summary_count(s, "drop_lock_unexpected"),
            "invalid_state": summary_count(s, "drop_lock_invalid_state"),
        })

    users_set = sorted({u for _, u in groups})
    rows: list[dict[str, Any]] = []

    for users in users_set:
        b = groups.get((args.baseline, users), [])
        c = groups.get((args.candidate, users), [])
        if not b or not c:
            continue

        def agg(items: list[dict[str, Any]], key: str) -> float | None:
            return med([n(x.get(key)) for x in items])

        bp95, cp95 = agg(b, "p95"), agg(c, "p95")
        bp99, cp99 = agg(b, "p99"), agg(c, "p99")
        bwait, cwait = agg(b, "lock_wait"), agg(c, "lock_wait")
        bhold, chold = agg(b, "lock_hold"), agg(c, "lock_hold")
        bdec, cdec = agg(b, "decrease"), agg(c, "decrease")
        berr = sum(x["lock_timeout"] + x["unexpected"] + x["invalid_state"] for x in b)
        cerr = sum(x["lock_timeout"] + x["unexpected"] + x["invalid_state"] for x in c)
        delta = pct_delta(bp95, cp95)

        if cerr > berr:
            verdict = "FUNCTIONAL_REGRESSION"
        elif delta is None:
            verdict = "INSUFFICIENT_DATA"
        elif delta <= -args.material_change_pct:
            verdict = "P95_IMPROVED"
        elif delta >= args.material_change_pct:
            verdict = "P95_REGRESSED"
        else:
            verdict = "NO_MATERIAL_CHANGE"

        rows.append({
            "users": users,
            "baseline_runs": len(b),
            "candidate_runs": len(c),
            "baseline_p95_ms": bp95,
            "candidate_p95_ms": cp95,
            "p95_delta_pct": delta,
            "baseline_p99_ms": bp99,
            "candidate_p99_ms": cp99,
            "p99_delta_pct": pct_delta(bp99, cp99),
            "baseline_lock_wait_p95_ms": bwait,
            "candidate_lock_wait_p95_ms": cwait,
            "lock_wait_delta_pct": pct_delta(bwait, cwait),
            "baseline_lock_hold_p95_ms": bhold,
            "candidate_lock_hold_p95_ms": chold,
            "hold_delta_pct": pct_delta(bhold, chold),
            "baseline_decrease_p95_ms": bdec,
            "candidate_decrease_p95_ms": cdec,
            "decrease_delta_pct": pct_delta(bdec, cdec),
            "baseline_cpu_max_pct_median": agg(b, "cpu"),
            "candidate_cpu_max_pct_median": agg(c, "cpu"),
            "baseline_hikari_pending_median": agg(b, "hikari_pending"),
            "candidate_hikari_pending_median": agg(c, "hikari_pending"),
            "baseline_error_events": berr,
            "candidate_error_events": cerr,
            "verdict": verdict,
        })

    args.output_dir.mkdir(parents=True, exist_ok=True)
    out_csv = args.output_dir / "experiment-comparison.csv"
    out_md = args.output_dir / "experiment-comparison.md"

    fields = list(rows[0].keys()) if rows else [
        "users", "baseline_runs", "candidate_runs", "baseline_p95_ms", "candidate_p95_ms",
        "p95_delta_pct", "baseline_error_events", "candidate_error_events", "verdict"
    ]
    with out_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)

    lines = [
        "# Phase 6 - Baseline vs Candidate",
        "",
        f"- baseline label: `{args.baseline}`",
        f"- candidate label: `{args.candidate}`",
        f"- material change rule: |P95 delta| >= {args.material_change_pct:.1f}% (편의상 gate이며 통계적 유의성 검정이 아님)",
        "- 각 값은 동일 VU에서 수집된 반복 run의 **median**입니다.",
        "- 상태 변경 API이므로 각 run은 독립 Drop을 사용해야 합니다.",
        "",
        "| VU | B runs | C runs | B P95 | C P95 | Δ P95 | B wait | C wait | B hold | C hold | B err | C err | Verdict |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|",
    ]
    for r in rows:
        lines.append(
            f"| {r['users']} | {r['baseline_runs']} | {r['candidate_runs']} | "
            f"{fmt(r['baseline_p95_ms'], 'ms')} | {fmt(r['candidate_p95_ms'], 'ms')} | {fmt(r['p95_delta_pct'], '%')} | "
            f"{fmt(r['baseline_lock_wait_p95_ms'], 'ms')} | {fmt(r['candidate_lock_wait_p95_ms'], 'ms')} | "
            f"{fmt(r['baseline_lock_hold_p95_ms'], 'ms')} | {fmt(r['candidate_lock_hold_p95_ms'], 'ms')} | "
            f"{int(r['baseline_error_events'])} | {int(r['candidate_error_events'])} | {r['verdict']} |"
        )

    if not rows:
        lines += [
            "",
            "비교 가능한 VU 쌍이 없습니다. baseline/candidate에 동일한 `USER_COUNT`가 최소 1개씩 필요합니다.",
        ]
    else:
        insufficient_repeat = [r for r in rows if r["baseline_runs"] < 3 or r["candidate_runs"] < 3]
        lines += ["", "## Interpretation", ""]
        if insufficient_repeat:
            lines.append("- ⚠️ 일부 VU는 baseline/candidate 반복이 3회 미만입니다. 결과 재현성을 위해 독립 Drop으로 3회 이상 반복을 권장합니다.")
        lines.append("- `FUNCTIONAL_REGRESSION`이면 응답시간이 빨라져도 candidate를 채택하지 않습니다.")
        lines.append("- P95 개선이 있어도 Lock wait/hold/decrease가 어디서 줄었는지 함께 확인합니다.")
        lines.append("- 이 결과만으로 운영 배포 결정을 자동화하지 않습니다.")

    out_md.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"OK: {out_csv}")
    print(f"OK: {out_md}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
