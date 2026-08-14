#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import math
import os
import statistics
import sys
import urllib.parse
import urllib.request
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

BASE = Path(__file__).resolve().parent.parent
DEFAULT_RUNS = Path(os.environ.get("RESULTS_ROOT", BASE / "results" / "runs"))


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="Collect Prometheus server metrics for k6 run windows and save them into each run directory."
    )
    p.add_argument("--runs-root", type=Path, default=DEFAULT_RUNS)
    p.add_argument("--run-dir", type=Path, help="Collect only one run directory")
    p.add_argument("--prometheus-url", default=os.environ.get("PROMETHEUS_URL", "http://localhost:9090"))
    p.add_argument("--job", default=os.environ.get("PROMETHEUS_JOB", "openbake-core"))
    p.add_argument("--padding-seconds", type=int, default=int(os.environ.get("PROMETHEUS_WINDOW_PADDING", "5")))
    p.add_argument("--step", default=os.environ.get("PROMETHEUS_QUERY_STEP", "1s"))
    p.add_argument(
        "--rate-window",
        default=os.environ.get("PROMETHEUS_RATE_WINDOW", "5s"),
        help="PromQL rate/increase window. 1s scrape + short burst test default: 5s",
    )
    p.add_argument("--overwrite", action="store_true")
    return p.parse_args()


def read_metadata(path: Path) -> dict[str, str]:
    data: dict[str, str] = {}
    if not path.exists():
        return data
    for raw in path.read_text(encoding="utf-8").splitlines():
        if "=" not in raw:
            continue
        key, value = raw.split("=", 1)
        data[key] = value
    return data


def parse_time(value: str) -> datetime:
    value = value.strip()
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    return datetime.fromisoformat(value)


def prometheus_get(base_url: str, path: str, params: dict[str, str]) -> dict[str, Any]:
    query = urllib.parse.urlencode(params)
    url = f"{base_url.rstrip('/')}{path}?{query}"
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as resp:
        payload = json.load(resp)
    if payload.get("status") != "success":
        raise RuntimeError(f"Prometheus API failed: {payload}")
    return payload


def range_values(base_url: str, expression: str, start: datetime, end: datetime, step: str) -> list[float]:
    payload = prometheus_get(
        base_url,
        "/api/v1/query_range",
        {
            "query": expression,
            "start": str(start.timestamp()),
            "end": str(end.timestamp()),
            "step": step,
        },
    )
    result = payload.get("data", {}).get("result", [])
    values: list[float] = []
    for series in result:
        for _, raw in series.get("values", []):
            try:
                val = float(raw)
            except (TypeError, ValueError):
                continue
            if math.isfinite(val):
                values.append(val)
    return values


def summarize(values: list[float]) -> dict[str, float | int]:
    if not values:
        return {}
    return {
        "samples": len(values),
        "min": min(values),
        "avg": statistics.fmean(values),
        "max": max(values),
        "last": values[-1],
    }


def expressions(job: str, rate_window: str) -> dict[str, dict[str, Any]]:
    j = job.replace('"', '\\"')
    rw = rate_window
    return {
        "process_cpu_percent": {
            "label": "Process CPU (%)",
            "candidates": [f'100 * process_cpu_usage{{job="{j}"}}'],
        },
        "system_cpu_percent": {
            "label": "System CPU (%)",
            "candidates": [f'100 * system_cpu_usage{{job="{j}"}}'],
        },
        "jvm_heap_percent": {
            "label": "JVM Heap Used (%)",
            "candidates": [
                f'100 * sum(jvm_memory_used_bytes{{job="{j}",area="heap"}}) / clamp_min(sum(jvm_memory_max_bytes{{job="{j}",area="heap"}}), 1)'
            ],
        },
        "gc_pause_seconds_per_second": {
            "label": "GC Pause (sec/sec)",
            "candidates": [
                f'sum(rate(jvm_gc_pause_seconds_sum{{job="{j}"}}[{rw}]))',
            ],
        },
        "tomcat_busy_threads": {
            "label": "Tomcat Busy Threads",
            "candidates": [f'sum(tomcat_threads_busy_threads{{job="{j}"}})'],
        },
        "tomcat_busy_percent": {
            "label": "Tomcat Busy Threads (%)",
            "candidates": [
                f'100 * sum(tomcat_threads_busy_threads{{job="{j}"}}) / clamp_min(sum(tomcat_threads_config_max_threads{{job="{j}"}}), 1)'
            ],
        },
        "hikari_active": {
            "label": "Hikari Active",
            "candidates": [f'sum(hikaricp_connections_active{{job="{j}"}})'],
        },
        "hikari_pending": {
            "label": "Hikari Pending",
            "candidates": [f'sum(hikaricp_connections_pending{{job="{j}"}})'],
        },
        "hikari_active_percent": {
            "label": "Hikari Active (%)",
            "candidates": [
                f'100 * sum(hikaricp_connections_active{{job="{j}"}}) / clamp_min(sum(hikaricp_connections_max{{job="{j}"}}), 1)'
            ],
        },
        "server_rps": {
            "label": "Spring HTTP RPS",
            "candidates": [
                f'sum(rate(http_server_requests_seconds_count{{job="{j}"}}[{rw}]))',
            ],
        },
        "server_5xx_rps": {
            "label": "Spring HTTP 5xx RPS",
            "candidates": [
                f'sum(rate(http_server_requests_seconds_count{{job="{j}",status=~"5.."}}[{rw}]))',
            ],
        },
        "server_p95_ms": {
            "label": "Spring HTTP P95 (ms)",
            "candidates": [
                f'1000 * histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{{job="{j}"}}[{rw}])))',
            ],
        },
        "server_p99_ms": {
            "label": "Spring HTTP P99 (ms)",
            "candidates": [
                f'1000 * histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{{job="{j}"}}[{rw}])))',
            ],
        },
        # Phase 5 custom lock metrics. These remain Available=N until the application patch is applied.
        "lock_wait_p95_ms": {
            "label": "Drop Lock Wait P95 (ms)",
            "candidates": [
                f'1000 * histogram_quantile(0.95, sum by (le) (rate(openbake_drop_lock_wait_seconds_bucket{{job="{j}",result="acquired"}}[{rw}])))'
            ],
        },
        "lock_wait_p99_ms": {
            "label": "Drop Lock Wait P99 (ms)",
            "candidates": [
                f'1000 * histogram_quantile(0.99, sum by (le) (rate(openbake_drop_lock_wait_seconds_bucket{{job="{j}",result="acquired"}}[{rw}])))'
            ],
        },
        "lock_hold_p95_ms": {
            "label": "Drop Lock Hold P95 (ms)",
            "candidates": [
                f'1000 * histogram_quantile(0.95, sum by (le) (rate(openbake_drop_lock_hold_seconds_bucket{{job="{j}"}}[{rw}])))'
            ],
        },
        "lock_decrease_p95_ms": {
            "label": "decreaseQuantity P95 (ms)",
            "candidates": [
                f'1000 * histogram_quantile(0.95, sum by (le) (rate(openbake_drop_lock_decrease_seconds_bucket{{job="{j}"}}[{rw}])))'
            ],
        },
        "lock_waiters": {
            "label": "Drop Lock Waiters",
            "candidates": [f'sum(openbake_drop_lock_waiters{{job="{j}"}})'],
        },
        "lock_holders": {
            "label": "Drop Lock Holders",
            "candidates": [f'sum(openbake_drop_lock_holders{{job="{j}"}})'],
        },
        "lock_map_size": {
            "label": "Drop Lock Map Size",
            "candidates": [f'sum(openbake_drop_lock_map_size{{job="{j}"}})'],
        },
        "lock_timeout_rate": {
            "label": "Drop Lock Timeout/sec",
            "candidates": [f'sum(rate(openbake_drop_lock_timeout_total{{job="{j}"}}[{rw}]))'],
        },
        "lock_interrupted_rate": {
            "label": "Drop Lock Interrupted/sec",
            "candidates": [f'sum(rate(openbake_drop_lock_interrupted_total{{job="{j}"}}[{rw}]))'],
        },
    }


def collect_one(run_dir: Path, args: argparse.Namespace) -> bool:
    metadata = read_metadata(run_dir / "metadata.env")
    started_raw = metadata.get("started_at")
    finished_raw = metadata.get("finished_at")
    if not started_raw or not finished_raw:
        print(f"SKIP {run_dir.name}: started_at/finished_at 없음")
        return False

    output_json = run_dir / "observability.json"
    output_md = run_dir / "observability.md"
    if output_json.exists() and not args.overwrite:
        print(f"SKIP {run_dir.name}: observability.json 이미 존재 (--overwrite로 재수집)")
        return True

    start = parse_time(started_raw) - timedelta(seconds=args.padding_seconds)
    end = parse_time(finished_raw) + timedelta(seconds=args.padding_seconds)

    metrics: dict[str, Any] = {}
    for key, spec in expressions(args.job, args.rate_window).items():
        chosen = None
        stats: dict[str, Any] = {}
        errors: list[str] = []
        for expr in spec["candidates"]:
            try:
                values = range_values(args.prometheus_url, expr, start, end, args.step)
            except Exception as exc:  # Prometheus response should not abort all metrics.
                errors.append(str(exc))
                continue
            if values:
                chosen = expr
                stats = summarize(values)
                break
        metrics[key] = {
            "label": spec["label"],
            "available": bool(stats),
            "expression": chosen,
            "stats": stats,
        }
        if errors and not stats:
            metrics[key]["errors"] = errors

    payload = {
        "run": run_dir.name,
        "prometheus_url": args.prometheus_url,
        "job": args.job,
        "window": {
            "started_at": start.isoformat(),
            "finished_at": end.isoformat(),
            "padding_seconds": args.padding_seconds,
            "step": args.step,
            "rate_window": args.rate_window,
        },
        "metrics": metrics,
    }
    output_json.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    lines = [
        f"# Observability - {run_dir.name}",
        "",
        f"- Prometheus job: `{args.job}`",
        f"- Query window: `{start.isoformat()}` ~ `{end.isoformat()}`",
        f"- PromQL rate window: `{args.rate_window}`",
        "",
        "| Metric | Available | Avg | Max | Last |",
        "|---|:---:|---:|---:|---:|",
    ]
    for item in metrics.values():
        stats = item.get("stats", {})

        def fmt(name: str) -> str:
            value = stats.get(name)
            return "-" if value is None else f"{value:.3f}"

        lines.append(
            f"| {item['label']} | {'Y' if item['available'] else 'N'} | {fmt('avg')} | {fmt('max')} | {fmt('last')} |"
        )
    lines.extend([
        "",
        "> `Available=N`은 반드시 이상 상태를 의미하지 않습니다. 해당 Micrometer metric이 현재 애플리케이션/서버 구성에서 노출되지 않거나 histogram 설정이 없을 수 있습니다.",
        "> Phase 5 lock metric이 N이면 `instrumentation/lock` 패치 적용 및 애플리케이션 재시작 여부를 확인하세요.",
        "",
    ])
    output_md.write_text("\n".join(lines), encoding="utf-8")
    print(f"OK   {run_dir.name}: {output_json.name}, {output_md.name}")
    return True


def main() -> int:
    args = parse_args()
    try:
        prometheus_get(args.prometheus_url, "/api/v1/query", {"query": "up"})
    except Exception as exc:
        print(f"ERROR: Prometheus 연결 실패: {exc}", file=sys.stderr)
        return 2

    if args.run_dir:
        dirs = [args.run_dir]
    else:
        if not args.runs_root.exists():
            print(f"ERROR: runs root가 없습니다: {args.runs_root}", file=sys.stderr)
            return 2
        dirs = [p for p in sorted(args.runs_root.iterdir()) if p.is_dir() and "lock-concurrency" in p.name]

    if not dirs:
        print("WARN: 수집할 lock run이 없습니다.")
        return 0

    ok = 0
    for run_dir in dirs:
        if collect_one(run_dir, args):
            ok += 1
    print(f"collected={ok}/{len(dirs)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
