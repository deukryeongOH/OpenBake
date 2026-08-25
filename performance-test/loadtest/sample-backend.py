#!/usr/bin/env python3
"""
backend Pod 지표를 1초 해상도로 수집한다.

왜 Prometheus 를 쓰지 않는가:
    k8s/monitoring/prometheus/configmap.yaml 의 scrape_interval 이 15s 다.
    confirm-entry / lock 시나리오는 per-vu-iterations 버스트라 수 초 만에 끝나므로
    15초 간격으로는 표본이 0~1개밖에 안 남는다. 그 표본으로 "스레드 풀이 포화됐다"를
    말할 수 없다. 그래서 부하 구간에만 직접 긁는다.

왜 port-forward 인가:
    openbake 네임스페이스에 default-deny NetworkPolicy 가 걸려 있고
    backend 로의 인바운드는 api-gateway 와 prometheus 만 허용된다(allow/ 참고).
    port-forward 는 API server -> kubelet 경로라 NetworkPolicy 와 무관하게 붙는다.

HPA(min 1 / max 2)로 Pod 가 늘어날 수 있으므로 주기적으로 다시 탐색해
새로 생긴 Pod 에도 port-forward 를 붙인다.

사용 예:
    python3 sample-backend.py --out sample.csv                # Ctrl+C 로 종료
    python3 sample-backend.py --out sample.csv --duration 120
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import shlex
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

FIELDS = [
    "timestamp", "elapsed_sec", "pod", "node",
    "tomcat_busy", "tomcat_current", "tomcat_max", "tomcat_busy_pct",
    "process_cpu", "system_cpu",
    "heap_used_mb", "heap_max_mb", "heap_pct",
    "gc_pause_sec_per_sec",
    "hikari_active", "hikari_idle", "hikari_pending", "hikari_max",
    "http_req_per_sec", "http_latency_avg_ms",
    "replicas_ready", "replicas_desired", "hpa_current_pct", "hpa_target_pct",
]


def kubectl_base() -> list[str]:
    return shlex.split(os.getenv("PERF_KUBECTL", "kubectl"))


def run(cmd: list[str], timeout: float = 15.0) -> str:
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    if result.returncode != 0:
        raise RuntimeError(f"명령 실패: {' '.join(cmd)}\n{result.stderr.strip()}")
    return result.stdout


def parse_prometheus_text(body: str) -> dict[str, list[tuple[dict[str, str], float]]]:
    """
    /actuator/prometheus 응답을 {메트릭명: [(라벨, 값), ...]} 로 만든다.
    외부 라이브러리 없이 쓰려고 최소한만 파싱한다.
    """
    out: dict[str, list[tuple[dict[str, str], float]]] = {}
    for line in body.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        try:
            head, raw_value = line.rsplit(" ", 1)
            value = float(raw_value)
        except ValueError:
            continue

        labels: dict[str, str] = {}
        if "{" in head and head.endswith("}"):
            name, label_part = head.split("{", 1)
            for chunk in label_part[:-1].split(","):
                if "=" not in chunk:
                    continue
                k, v = chunk.split("=", 1)
                labels[k.strip()] = v.strip().strip('"')
        else:
            name = head
        out.setdefault(name.strip(), []).append((labels, value))
    return out


def total(metrics: dict[str, list[tuple[dict[str, str], float]]],
          name: str, **label_filter: str) -> float | None:
    series = metrics.get(name)
    if not series:
        return None
    picked = [
        value for labels, value in series
        if all(labels.get(k) == v for k, v in label_filter.items())
    ]
    return sum(picked) if picked else None


class PodProbe:
    """Pod 하나에 붙은 port-forward 와 직전 누적값(rate 계산용)."""

    def __init__(self, pod: str, node: str, port: int, kubectl: list[str], namespace: str):
        self.pod = pod
        self.node = node
        self.port = port
        self.prev: dict[str, tuple[float, float]] = {}  # name -> (timestamp, cumulative)
        self.proc = subprocess.Popen(
            [*kubectl, "-n", namespace, "port-forward", f"pod/{pod}", f"{port}:8080"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )

    def alive(self) -> bool:
        return self.proc.poll() is None

    def stop(self) -> None:
        if self.alive():
            self.proc.terminate()
            try:
                self.proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.proc.kill()

    def scrape(self, timeout: float = 3.0) -> dict[str, list[tuple[dict[str, str], float]]] | None:
        url = f"http://127.0.0.1:{self.port}/actuator/prometheus"
        try:
            with urllib.request.urlopen(url, timeout=timeout) as resp:
                return parse_prometheus_text(resp.read().decode("utf-8", errors="replace"))
        except (urllib.error.URLError, OSError):
            return None

    def rate(self, name: str, cumulative: float | None, now: float) -> float | None:
        """누적 카운터를 초당 증가량으로 바꾼다. 첫 표본은 기준점이라 비운다."""
        if cumulative is None:
            return None
        previous = self.prev.get(name)
        self.prev[name] = (now, cumulative)
        if previous is None:
            return None
        elapsed = now - previous[0]
        if elapsed <= 0:
            return None
        delta = cumulative - previous[1]
        if delta < 0:  # Pod 재시작으로 카운터가 리셋된 경우
            return None
        return delta / elapsed


def discover_pods(kubectl: list[str], namespace: str, selector: str) -> list[tuple[str, str]]:
    raw = run([
        *kubectl, "-n", namespace, "get", "pods", "-l", selector,
        "--field-selector=status.phase=Running",
        "-o", "jsonpath={range .items[*]}{.metadata.name} {.spec.nodeName}{'\\n'}{end}",
    ])
    pods = []
    for line in raw.splitlines():
        parts = line.split()
        if len(parts) >= 2:
            pods.append((parts[0], parts[1]))
    return pods


def read_scale_state(kubectl: list[str], namespace: str,
                     deployment: str, hpa: str) -> dict[str, Any]:
    state: dict[str, Any] = {
        "replicas_ready": "", "replicas_desired": "",
        "hpa_current_pct": "", "hpa_target_pct": "",
    }
    try:
        raw = run([*kubectl, "-n", namespace, "get", "deploy", deployment,
                   "-o", "jsonpath={.status.readyReplicas} {.status.replicas}"])
        parts = raw.split()
        if len(parts) >= 1:
            state["replicas_ready"] = parts[0]
        if len(parts) >= 2:
            state["replicas_desired"] = parts[1]
    except Exception:
        pass

    try:
        raw = run([*kubectl, "-n", namespace, "get", "hpa", hpa, "-o", "jsonpath="
                   "{.status.currentMetrics[0].resource.current.averageUtilization} "
                   "{.spec.metrics[0].resource.target.averageUtilization}"])
        parts = raw.split()
        if len(parts) >= 1:
            state["hpa_current_pct"] = parts[0]
        if len(parts) >= 2:
            state["hpa_target_pct"] = parts[1]
    except Exception:
        pass

    return state


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="backend Pod 지표 1초 해상도 수집")
    p.add_argument("--out", type=Path, required=True)
    p.add_argument("--namespace", default=os.getenv("PERF_K8S_NAMESPACE", "openbake"))
    p.add_argument("--deployment", default=os.getenv("PERF_BACKEND_DEPLOYMENT", "backend"))
    p.add_argument("--hpa", default=os.getenv("PERF_BACKEND_HPA", "backend"))
    p.add_argument("--selector",
                   default=os.getenv("PERF_BACKEND_SELECTOR",
                                     "app.kubernetes.io/name=backend"))
    p.add_argument("--interval", type=float, default=float(os.getenv("SAMPLE_INTERVAL", "1")))
    p.add_argument("--duration", type=int, default=0, help="0이면 SIGINT/SIGTERM 까지")
    p.add_argument("--base-port", type=int, default=int(os.getenv("SAMPLE_BASE_PORT", "18080")))
    return p.parse_args()


def main() -> int:
    args = parse_args()
    kubectl = kubectl_base()

    try:
        pods = discover_pods(kubectl, args.namespace, args.selector)
    except Exception as exc:
        print(f"ERROR: Pod 탐색 실패: {exc}", file=sys.stderr)
        return 2
    if not pods:
        print(f"ERROR: Running 상태 Pod 가 없습니다 "
              f"(-n {args.namespace} -l {args.selector})", file=sys.stderr)
        return 2

    probes: dict[str, PodProbe] = {}
    next_port = args.base_port
    for pod, node in pods:
        probes[pod] = PodProbe(pod, node, next_port, kubectl, args.namespace)
        next_port += 1

    print(f"[sample] namespace={args.namespace} pods={len(probes)} interval={args.interval}s")
    print(f"[sample] out={args.out}")

    # port-forward 가 붙을 시간을 준다. 첫 표본이 통째로 비는 것을 막는다.
    time.sleep(2)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    handle = args.out.open("w", encoding="utf-8", newline="")
    writer = csv.DictWriter(handle, fieldnames=FIELDS)
    writer.writeheader()

    stopping = {"flag": False}

    def on_signal(_sig: int, _frame: Any) -> None:
        stopping["flag"] = True

    signal.signal(signal.SIGINT, on_signal)
    signal.signal(signal.SIGTERM, on_signal)

    start = time.time()
    tick = 0
    try:
        while not stopping["flag"]:
            now = time.time()
            elapsed = now - start
            if args.duration and elapsed >= args.duration:
                break

            # HPA 가 Pod 를 늘릴 수 있으므로 주기적으로 다시 본다.
            if tick % 10 == 0:
                try:
                    current = dict(discover_pods(kubectl, args.namespace, args.selector))
                except Exception:
                    current = {p: v.node for p, v in probes.items()}
                for pod in list(probes):
                    if pod not in current or not probes[pod].alive():
                        probes.pop(pod).stop()
                for pod, node in current.items():
                    if pod not in probes:
                        probes[pod] = PodProbe(pod, node, next_port, kubectl, args.namespace)
                        next_port += 1
                        print(f"\n[sample] 새 Pod 감지: {pod} ({node})")

            scale = read_scale_state(kubectl, args.namespace, args.deployment, args.hpa)
            stamp = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(now))

            for pod, probe in list(probes.items()):
                metrics = probe.scrape()
                row: dict[str, Any] = {f: "" for f in FIELDS}
                row.update({
                    "timestamp": stamp,
                    "elapsed_sec": f"{elapsed:.1f}",
                    "pod": pod,
                    "node": probe.node,
                    **scale,
                })

                if metrics:
                    busy = total(metrics, "tomcat_threads_busy_threads")
                    current_threads = total(metrics, "tomcat_threads_current_threads")
                    max_threads = total(metrics, "tomcat_threads_config_max_threads")
                    heap_used = total(metrics, "jvm_memory_used_bytes", area="heap")
                    heap_max = total(metrics, "jvm_memory_max_bytes", area="heap")
                    gc_sum = total(metrics, "jvm_gc_pause_seconds_sum")
                    req_count = total(metrics, "http_server_requests_seconds_count")
                    req_sum = total(metrics, "http_server_requests_seconds_sum")

                    row["tomcat_busy"] = busy if busy is not None else ""
                    row["tomcat_current"] = current_threads if current_threads is not None else ""
                    row["tomcat_max"] = max_threads if max_threads is not None else ""
                    if busy is not None and max_threads:
                        row["tomcat_busy_pct"] = f"{100 * busy / max_threads:.1f}"

                    cpu = total(metrics, "process_cpu_usage")
                    sys_cpu = total(metrics, "system_cpu_usage")
                    row["process_cpu"] = f"{cpu:.4f}" if cpu is not None else ""
                    row["system_cpu"] = f"{sys_cpu:.4f}" if sys_cpu is not None else ""

                    if heap_used is not None:
                        row["heap_used_mb"] = f"{heap_used / 1048576:.1f}"
                    if heap_max is not None and heap_max > 0:
                        row["heap_max_mb"] = f"{heap_max / 1048576:.1f}"
                        if heap_used is not None:
                            row["heap_pct"] = f"{100 * heap_used / heap_max:.1f}"

                    gc_rate = probe.rate("gc", gc_sum, now)
                    if gc_rate is not None:
                        row["gc_pause_sec_per_sec"] = f"{gc_rate:.4f}"

                    for key, name in (
                            ("hikari_active", "hikaricp_connections_active"),
                            ("hikari_idle", "hikaricp_connections_idle"),
                            ("hikari_pending", "hikaricp_connections_pending"),
                            ("hikari_max", "hikaricp_connections_max"),
                    ):
                        value = total(metrics, name)
                        row[key] = value if value is not None else ""

                    req_rate = probe.rate("http_count", req_count, now)
                    if req_rate is not None:
                        row["http_req_per_sec"] = f"{req_rate:.2f}"
                    sum_rate = probe.rate("http_sum", req_sum, now)
                    if req_rate and sum_rate is not None and req_rate > 0:
                        row["http_latency_avg_ms"] = f"{1000 * sum_rate / req_rate:.1f}"

                writer.writerow(row)

            handle.flush()
            busy_display = ", ".join(
                f"{p.split('-')[-1]}:{probes[p].port}" for p in list(probes)[:2]
            )
            print(f"\r[{elapsed:6.1f}s] pods={len(probes)} "
                  f"ready={scale['replicas_ready']}/{scale['replicas_desired']} "
                  f"hpa={scale['hpa_current_pct'] or '-'}%/{scale['hpa_target_pct'] or '-'}% "
                  f"({busy_display})   ", end="", flush=True)

            tick += 1
            sleep_left = args.interval - (time.time() - now)
            if sleep_left > 0:
                time.sleep(sleep_left)
    finally:
        for probe in probes.values():
            probe.stop()
        handle.close()

    print()
    print(f"[sample] 완료: {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())