#!/usr/bin/env python3
"""poll-backend.py 가 남긴 누적 카운터 CSV 에서 부하 구간을 찾아 지표를 낸다.

게이지의 피크가 아니라 **구간 차이**로 계산한다.

    요청당 CPU   = usage_usec 차이 / 요청 수 차이
    스로틀 비율  = nr_throttled 차이 / nr_periods 차이
    acquire 평균 = acquire_seconds_sum 차이 / count 차이   (커넥션 대기)
    usage 평균   = usage_seconds_sum   차이 / count 차이   (커넥션 점유)

부하 구간은 초당 요청 증가가 임계(기본 20건/초)를 넘는 연속 구간으로 잡고,
앞뒤로 1초씩 넓혀 버스트의 꼬리를 담는다. Pod 가 바뀐 경계는 끊는다.

사용법
    python3 poll-report.py poll.csv [--min-rps 20]
"""
import csv
import sys


def num(row, key):
    try:
        return float(row[key])
    except (KeyError, TypeError, ValueError):
        return None


def load(path):
    rows = []
    for r in csv.DictReader(open(path)):
        if r.get("cpu_usage_usec") and r.get("http_cnt") and r.get("pod"):
            rows.append(r)
    return rows


def find_windows(rows, min_rps):
    windows, start = [], None
    for i in range(1, len(rows)):
        prev, cur = rows[i - 1], rows[i]
        same_pod = prev["pod"] == cur["pod"]
        delta = (num(cur, "http_cnt") - num(prev, "http_cnt")) if same_pod else 0
        hot = same_pod and delta is not None and delta >= min_rps
        if hot and start is None:
            start = i - 1
        elif not hot and start is not None:
            windows.append((max(0, start - 1), min(len(rows) - 1, i)))
            start = None
    if start is not None:
        windows.append((max(0, start - 1), len(rows) - 1))
    # 같은 Pod 안에서만 유효하다.
    return [(a, b) for a, b in windows if rows[a]["pod"] == rows[b]["pod"]]


def report(rows, a, b):
    first, last = rows[a], rows[b]
    d = lambda k: (num(last, k) or 0) - (num(first, k) or 0)
    reqs = d("http_cnt")
    if reqs <= 0:
        return
    secs = d("http_sum")
    cpu = d("cpu_usage_usec") / 1e6
    periods, throttled = d("nr_periods"), d("nr_throttled")
    acq_n = d("acq_cnt")
    span = b - a

    peak = lambda k: max((num(r, k) or 0) for r in rows[a:b + 1])

    print(f"[{first['ts']} ~ {last['ts']}]  {span}s  {first['pod']}")
    print(f"  요청        {reqs:.0f}건  ({reqs / span:.1f}/s)"
          + (f"  서버 처리 평균 {secs / reqs * 1000:.0f}ms" if secs else ""))
    print(f"  요청당 CPU  {cpu / reqs * 1000:.1f}ms   (구간 CPU {cpu:.2f}s)")
    if periods:
        print(f"  스로틀      {d('throttled_usec') / 1000:.0f}ms / {throttled:.0f}회"
              f"  ({throttled / periods * 100:.1f}% of {periods:.0f} periods)")
    if acq_n:
        print(f"  커넥션 대기 acquire 평균 {d('acq_sum') / acq_n * 1000:.0f}ms   "
              f"점유 usage 평균 {d('use_sum') / acq_n * 1000:.0f}ms   (n={acq_n:.0f})")
    print(f"  피크        Hikari active {peak('hikari_active'):.0f} / "
          f"pending {peak('hikari_pending'):.0f} / "
          f"Tomcat busy {peak('tomcat_busy'):.0f}")
    print()


def main():
    args = sys.argv[1:]
    if not args:
        sys.exit("사용법: python3 poll-report.py poll.csv [--min-rps 20]")
    path = args[0]
    min_rps = 20.0
    if "--min-rps" in args:
        min_rps = float(args[args.index("--min-rps") + 1])

    rows = load(path)
    if not rows:
        sys.exit(f"{path} 에 쓸 만한 행이 없습니다.")

    windows = find_windows(rows, min_rps)
    if not windows:
        sys.exit(f"초당 {min_rps:.0f}건 이상인 구간이 없습니다. --min-rps 를 낮춰보세요.")

    print(f"\n{path} — 부하 구간 {len(windows)}개 (초당 {min_rps:.0f}건 이상)\n")
    for a, b in windows:
        report(rows, a, b)


if __name__ == "__main__":
    main()
