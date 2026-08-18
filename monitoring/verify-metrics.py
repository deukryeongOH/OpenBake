#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import urllib.parse
import urllib.request


def query(base: str, expression: str) -> list:
    params = urllib.parse.urlencode({"query": expression})
    url = f"{base.rstrip('/')}/api/v1/query?{params}"
    with urllib.request.urlopen(url, timeout=10) as resp:
        payload = json.load(resp)
    if payload.get("status") != "success":
        return []
    return payload.get("data", {}).get("result", [])


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--prometheus-url", default="http://localhost:9090")
    p.add_argument("--job", default="openbake-core")
    p.add_argument(
        "--require-lock-metrics",
        action="store_true",
        help="Fail when Phase 5 custom Drop lock metrics are missing.",
    )
    args = p.parse_args()

    j = args.job.replace('"', '\\"')
    metrics = [
        ("Process CPU", f'process_cpu_usage{{job="{j}"}}', True),
        ("JVM Heap", f'jvm_memory_used_bytes{{job="{j}",area="heap"}}', True),
        ("HTTP Server", f'http_server_requests_seconds_count{{job="{j}"}}', True),
        ("Tomcat Busy Threads", f'tomcat_threads_busy_threads{{job="{j}"}}', False),
        ("Hikari Active", f'hikaricp_connections_active{{job="{j}"}}', False),
        ("Hikari Pending", f'hikaricp_connections_pending{{job="{j}"}}', False),
        ("HTTP Histogram Bucket", f'http_server_requests_seconds_bucket{{job="{j}"}}', False),
        # Phase 5 application metrics. Timers appear after at least one lock-start request.
        ("Drop Lock Wait Histogram", f'openbake_drop_lock_wait_seconds_bucket{{job="{j}"}}', args.require_lock_metrics),
        ("Drop Lock Hold Histogram", f'openbake_drop_lock_hold_seconds_bucket{{job="{j}"}}', args.require_lock_metrics),
        ("Drop Lock decreaseQuantity Histogram", f'openbake_drop_lock_decrease_seconds_bucket{{job="{j}"}}', args.require_lock_metrics),
        ("Drop Lock Waiters", f'openbake_drop_lock_waiters{{job="{j}"}}', args.require_lock_metrics),
        ("Drop Lock Holders", f'openbake_drop_lock_holders{{job="{j}"}}', args.require_lock_metrics),
        ("Drop Lock Map Size", f'openbake_drop_lock_map_size{{job="{j}"}}', args.require_lock_metrics),
    ]

    missing_required = []
    for label, expr, required in metrics:
        try:
            found = bool(query(args.prometheus_url, expr))
        except Exception as exc:
            print(f"ERROR: Prometheus query 실패: {exc}")
            return 2
        mark = "OK" if found else ("FAIL" if required else "WARN")
        print(f"{mark:4} {label}")
        if required and not found:
            missing_required.append(label)

    if missing_required:
        print("\n필수 metric 누락: " + ", ".join(missing_required))
        if args.require_lock_metrics:
            print("Phase 5 lock metric이 없다면 instrumentation/lock 패치 적용, 앱 재시작, lock-start 1회 호출을 확인하세요.")
        return 1

    print("\n필수 metric 확인 완료. WARN 항목은 서버/풀/히스토그램/Phase 5 패치 적용 여부에 따라 없을 수 있습니다.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
