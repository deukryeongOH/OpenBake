#!/usr/bin/env python3
from __future__ import annotations

import csv
import json
import os
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
RUNS = Path(os.environ.get("RESULTS_ROOT", BASE / "results" / "runs"))
OUT_CSV = Path(__file__).resolve().parent / "capacity-summary.csv"
OUT_MD = Path(__file__).resolve().parent / "capacity-summary.md"


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


def metric(summary: dict, name: str, key: str):
    values = summary.get("metrics", {}).get(name, {}).get("values", {})
    return values.get(key, "")


def count(summary: dict, name: str):
    values = summary.get("metrics", {}).get(name, {}).get("values", {})
    return values.get("count", "")


rows = []
if RUNS.exists():
    for run_dir in sorted(RUNS.iterdir()):
        if not run_dir.is_dir() or "lock-concurrency" not in run_dir.name:
            continue
        meta = parse_metadata(run_dir / "metadata.env")
        summary_file = run_dir / "summary.json"
        if not summary_file.exists():
            continue
        try:
            summary = json.loads(summary_file.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        rows.append({
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
        })

rows.sort(key=lambda r: int(r["users"]) if str(r["users"]).isdigit() else 10**9)

fields = [
    "run", "users", "drop_id", "result", "p95_ms", "p99_ms",
    "http_p95_ms", "http_failed_rate", "success", "sold_out",
    "lock_timeout", "unexpected",
]
with OUT_CSV.open("w", newline="", encoding="utf-8") as f:
    writer = csv.DictWriter(f, fieldnames=fields)
    writer.writeheader()
    writer.writerows(rows)

lines = [
    "# OpenBake Lock Capacity Summary",
    "",
    "| Users | Result | P95(ms) | P99(ms) | HTTP fail | Success | Sold out | Lock timeout | Unexpected |",
    "|---:|:---:|---:|---:|---:|---:|---:|---:|---:|",
]
for r in rows:
    lines.append(
        f"| {r['users']} | {r['result']} | {r['p95_ms']} | {r['p99_ms']} | "
        f"{r['http_failed_rate']} | {r['success']} | {r['sold_out']} | "
        f"{r['lock_timeout']} | {r['unexpected']} |"
    )
OUT_MD.write_text("\n".join(lines) + "\n", encoding="utf-8")

print(f"runs={len(rows)}")
print(f"csv={OUT_CSV}")
print(f"markdown={OUT_MD}")
