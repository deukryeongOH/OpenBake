#!/usr/bin/env python3
"""api-gateway 의 누적 카운터를 1초 해상도로 기록한다.

backend 쪽은 poll-backend.py 가 본다. 이 스크립트는 그 앞단인 게이트웨이를 본다.
게이트웨이는 두 종류의 타이머를 노출한다.

    http_server_requests   게이트웨이가 요청 하나를 받아 응답할 때까지의 전체 시간
    http_client_requests   그중 업스트림(backend·member-service)을 호출한 시간

    필터 체인 몫 = 전체 - 업스트림
                 = JWT 검증 + Redis 블랙리스트 확인 + 라우팅

이 차이를 구간별로 뽑아야 "지연이 게이트웨이 안에서 생기는가, 그 앞(Traefik·TLS·
네트워크)에서 생기는가"를 가를 수 있다. 누적값을 한 번만 읽으면 여러 실행이
뒤섞이므로 1초 해상도로 받아 구간 차이를 쓴다.

cgroup 은 게이트웨이 Pod 가 도는 노드에서만 읽히므로 이 스크립트도 그 노드에서
돌려야 하고, 그래서 sudo 가 필요하다.

사용법
    sudo -E python3 poll-gateway.py <출력.csv>        # Ctrl-C 로 종료
"""
import csv
import re
import subprocess
import sys
import time

NS = "openbake"
LABEL_SELECTOR = "app.kubernetes.io/name=api-gateway"

FIELDS = [
    "ts", "pod",
    "cpu_usage_usec", "throttled_usec", "nr_throttled", "nr_periods", "psi_some_usec", "psi_full_usec",
    "srv_cnt", "srv_sum",          # 게이트웨이 전체 처리 (POST 성공분)
    "cli_core_cnt", "cli_core_sum",  # backend 업스트림 호출
    "srv_503_cnt",                 # 블랙리스트 타임아웃으로 잘린 요청
    "process_cpu",
]

NUM = r"([0-9.eE+-]+)"


def read_psi(path: str) -> tuple[str, str]:
    """cgroup 의 cpu.pressure 에서 some/full 누적 정체 시간(마이크로초)을 읽는다.

    cpu.stat 의 throttled_usec 은 **자기 쿼터를 넘겨 강제로 멈춘 시간**만 센다.
    쿼터를 안 넘겼는데 노드 경쟁에서 밀려 스케줄을 못 받은 시간은 거기 안 잡힌다.
    2026-08-26 실측에서 게이트웨이가 스로틀 0회인데도 1.28 CPU-초짜리 일을 9초에
    걸쳐 처리했다 — 그 사각지대가 여기 잡힌다.

        some  하나 이상의 스레드가 CPU 를 기다리며 멈춰 있던 시간
        full  모든 스레드가 동시에 멈춰 있던 시간(완전 정지)

    total 은 누적값이므로 구간 차이를 구간 길이로 나누면 그 구간의 정체 비율이 된다.
    """
    some = full = ""
    try:
        with open(path + "/cpu.pressure") as f:
            for line in f:
                m = re.search(r"total=(\d+)", line)
                if not m:
                    continue
                if line.startswith("some"):
                    some = m.group(1)
                elif line.startswith("full"):
                    full = m.group(1)
    except OSError:
        pass
    return some, full


def sh(cmd: str) -> str:
    try:
        return subprocess.run(cmd, shell=True, capture_output=True,
                              text=True, timeout=15).stdout
    except subprocess.TimeoutExpired:
        return ""


def resolve():
    """이 노드에서 도는 게이트웨이 Pod 의 이름 / IP / cgroup 경로."""
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


def sum_matching(text: str, metric: str, *must_contain) -> float:
    """라벨에 must_contain 이 모두 들어간 줄만 합산한다."""
    total = 0.0
    for line in text.splitlines():
        if not line.startswith(metric + "{"):
            continue
        if not all(token in line for token in must_contain):
            continue
        m = re.search(r"\}\s+" + NUM + r"\s*$", line)
        if m:
            total += float(m.group(1))
    return total


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
                    row["psi_some_usec"], row["psi_full_usec"] = read_psi(path)
                except OSError:
                    target = None

                prom = sh(f"curl -s -m 2 http://{ip}:8080/actuator/prometheus")
                if not prom:
                    target = None
                else:
                    # 드롭 부하는 전부 POST 다. 액추에이터 GET 이 섞이면 평균이 희석된다.
                    row["srv_cnt"] = sum_matching(
                        prom, "http_server_requests_seconds_count", 'method="POST"', 'status="200"')
                    row["srv_sum"] = sum_matching(
                        prom, "http_server_requests_seconds_sum", 'method="POST"', 'status="200"')
                    row["srv_503_cnt"] = sum_matching(
                        prom, "http_server_requests_seconds_count", 'method="POST"', 'status="503"')
                    row["cli_core_cnt"] = sum_matching(
                        prom, "http_client_requests_seconds_count", 'method="POST"', 'route_id="core-api"')
                    row["cli_core_sum"] = sum_matching(
                        prom, "http_client_requests_seconds_sum", 'method="POST"', 'route_id="core-api"')
                    m = re.search(r"^process_cpu_usage\{.*\}\s+" + NUM, prom, re.M)
                    row["process_cpu"] = m.group(1) if m else ""

            writer.writerow(row)
            fh.flush()
            time.sleep(max(0.0, 1.0 - (time.time() - started)))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("사용법: sudo -E python3 poll-gateway.py <출력.csv>")
    try:
        main(sys.argv[1])
    except KeyboardInterrupt:
        pass
