#!/usr/bin/env python3
"""backend Pod 의 누적 카운터를 1초 해상도로 기록한다.

sample-backend.py 는 게이지(active/pending/process_cpu)만 본다. 그런데
19번 문서 4-2 에서 정리했듯 `process_cpu_usage` 는 JVM 이 자체 계산하는
롤링값이라 1초 해상도로 처리량과 짝지으면 오차가 커서, 같은 CSV 안에서도
0.0 과 1.0 이 섞여 나온다. 그걸로 요청당 CPU 를 계산했다가 "CPU 가 병목"
이라는 오진을 한 번 냈다.

그래서 이 스크립트는 게이지 대신 **누적 카운터**만 모은다.

    요청당 CPU   = cpu.stat usage_usec 구간 차이 / 요청 수 구간 차이
    acquire 평균 = hikaricp_connections_acquire_seconds_sum 차이 / count 차이
    usage 평균   = hikaricp_connections_usage_seconds_sum   차이 / count 차이

두 지점의 차이만 쓰므로 샘플 하나의 흔들림에 영향을 받지 않는다.

수집 경로
  - cpu.stat  : 노드의 컨테이너 cgroup 을 직접 읽는다(그래서 sudo 가 필요하고,
                backend Pod 가 뜬 노드에서 실행해야 한다). kubectl exec 를 매초
                돌리면 그 자체가 CPU 를 먹어 측정을 흔든다.
  - 나머지    : Pod IP 로 /actuator/prometheus 를 직접 긁는다. k3s 노드에서는
                Pod CIDR 이 라우팅되므로 port-forward 없이 닿는다.

Pod 는 롤아웃마다 바뀌므로 읽기에 실패하면 대상을 다시 찾는다.

사용법
    sudo -E python3 poll-backend.py <출력.csv>        # Ctrl-C 로 종료
"""
import csv
import re
import subprocess
import sys
import time

NS = "openbake"
LABEL_SELECTOR = "app.kubernetes.io/name=backend"

FIELDS = [
    "ts", "pod",
    "cpu_usage_usec", "throttled_usec", "nr_throttled", "nr_periods",
    "acq_sum", "acq_cnt", "use_sum", "use_cnt",
    "http_cnt", "http_sum",
    "tomcat_busy", "hikari_active", "hikari_pending", "process_cpu",
]

# 라벨 값에 /api/v1/drops/{dropId}/... 처럼 '}' 가 들어간다.
# [^}]* 로 끊으면 그 줄들이 통째로 누락되므로 탐욕적 .* 로 마지막 '}' 까지 먹는다.
NUM = r"([0-9.eE+-]+)"


def sh(cmd: str) -> str:
    try:
        return subprocess.run(cmd, shell=True, capture_output=True,
                              text=True, timeout=15).stdout
    except subprocess.TimeoutExpired:
        return ""


def resolve():
    """이 노드에서 도는 backend Pod 의 이름 / IP / cgroup 경로를 찾는다.

    replica 가 2개면 첫 번째 Pod 가 다른 노드에 있을 수 있다. cgroup 은 그 컨테이너가
    실제로 도는 노드에서만 읽히므로, Pod 를 전부 훑어 **이 노드에 cgroup 이 있는 것**을
    고른다. 그래서 이 폴러가 보는 것은 언제나 로컬 노드의 Pod 하나뿐이다 —
    처리량은 전체의 일부지만, 요청당 CPU 와 acquire/usage 평균은 Pod 단위 값이라
    그대로 유효하다.
    """
    rows = sh(
        f"kubectl -n {NS} get pod -l {LABEL_SELECTOR} -o jsonpath="
        "'{range .items[*]}{.metadata.name} {.status.podIP} "
        "{.status.containerStatuses[0].containerID}{\"\\n\"}{end}'"
    ).splitlines()
    for row in rows:
        parts = row.split()
        if len(parts) < 3:
            continue
        name, ip, cid = parts[0], parts[1], parts[2].split("://")[-1]
        path = sh(f"find /sys/fs/cgroup -maxdepth 4 -name '*{cid}*' 2>/dev/null | head -1").strip()
        if ip and path:
            return name, ip, path
    return None


def metric(text: str, name: str):
    m = re.search(r"^" + name + r"\{.*\}\s+" + NUM, text, re.M)
    return m.group(1) if m else ""


def main(out_path: str) -> None:
    target = None
    with open(out_path, "w", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=FIELDS)
        writer.writeheader()
        while True:
            started = time.time()
            if target is None:
                target = resolve()

            row = {k: "" for k in FIELDS}
            row["ts"] = time.strftime("%H:%M:%S", time.gmtime())

            if target:
                name, ip, path = target
                row["pod"] = name
                try:
                    with open(path + "/cpu.stat") as f:
                        stat = dict(l.split() for l in f.read().splitlines() if " " in l)
                    row["cpu_usage_usec"] = stat.get("usage_usec", "")
                    row["throttled_usec"] = stat.get("throttled_usec", "")
                    row["nr_throttled"] = stat.get("nr_throttled", "")
                    row["nr_periods"] = stat.get("nr_periods", "")
                except OSError:
                    target = None

                prom = sh(f"curl -s -m 2 http://{ip}:8080/actuator/prometheus")
                if not prom:
                    target = None
                else:
                    row["acq_sum"] = metric(prom, "hikaricp_connections_acquire_seconds_sum")
                    row["acq_cnt"] = metric(prom, "hikaricp_connections_acquire_seconds_count")
                    row["use_sum"] = metric(prom, "hikaricp_connections_usage_seconds_sum")
                    row["use_cnt"] = metric(prom, "hikaricp_connections_usage_seconds_count")
                    row["hikari_active"] = metric(prom, "hikaricp_connections_active")
                    row["hikari_pending"] = metric(prom, "hikaricp_connections_pending")
                    row["tomcat_busy"] = metric(prom, "tomcat_threads_busy_threads")
                    row["process_cpu"] = metric(prom, "process_cpu_usage")
                    counts = re.findall(r"^http_server_requests_seconds_count\{.*\}\s+" + NUM, prom, re.M)
                    sums = re.findall(r"^http_server_requests_seconds_sum\{.*\}\s+" + NUM, prom, re.M)
                    row["http_cnt"] = str(sum(float(c) for c in counts)) if counts else ""
                    row["http_sum"] = str(sum(float(c) for c in sums)) if sums else ""

            writer.writerow(row)
            fh.flush()
            time.sleep(max(0.0, 1.0 - (time.time() - started)))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("사용법: sudo -E python3 poll-backend.py <출력.csv>")
    try:
        main(sys.argv[1])
    except KeyboardInterrupt:
        pass
