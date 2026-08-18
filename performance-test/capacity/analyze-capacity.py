#!/usr/bin/env python3
from __future__ import annotations

import csv, json, os, statistics
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
P95_LIMIT_MS = float(os.environ.get("STOCK_P95_LIMIT_MS", "1500"))
P99_LIMIT_MS = float(os.environ.get("STOCK_P99_LIMIT_MS", "3000"))


def parse_metadata(path: Path) -> dict[str, str]:
    out = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if "=" in line:
                k, v = line.split("=", 1); out[k] = v
    return out


def load_json(path: Path) -> dict[str, Any]:
    try: return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError): return {}


def metric(summary, name, key):
    return summary.get("metrics", {}).get(name, {}).get("values", {}).get(key, "")


def count(summary, name):
    return summary.get("metrics", {}).get(name, {}).get("values", {}).get("count", "")


def obs_max(obs, name):
    return obs.get("metrics", {}).get(name, {}).get("stats", {}).get("max", "")


def num(v):
    try: return float(v) if v not in (None, "") else None
    except (TypeError, ValueError): return None


def fmt(v, digits=1):
    n=num(v); return "-" if n is None else f"{n:.{digits}f}"


def diagnose(r):
    hints=[]
    cpu=num(r.get("process_cpu_max_pct")); tomcat=num(r.get("tomcat_busy_max_pct"))
    hp=num(r.get("hikari_pending_max")); ha=num(r.get("hikari_active_max_pct"))
    heap=num(r.get("jvm_heap_max_pct")); gc=num(r.get("gc_pause_max_sec_per_sec"))
    sx=num(r.get("server_5xx_max_rps")); p95=num(r.get("p95_ms")); p99=num(r.get("p99_ms"))
    unexpected=num(r.get("unexpected"))
    if hp is not None and hp > 0: hints.append("DB connection pool 대기 후보")
    elif ha is not None and ha >= 90: hints.append("DB connection pool 포화 근접")
    if cpu is not None and cpu >= 85: hints.append("CPU 포화 후보")
    if tomcat is not None and tomcat >= 80: hints.append("Tomcat thread 포화 후보")
    if heap is not None and heap >= 85 and gc is not None and gc >= .10: hints.append("Heap/GC pressure 후보")
    if sx is not None and sx > 0: hints.append("서버 5xx 발생")
    if unexpected is not None and unexpected > 0: hints.append("예상 외 비즈니스 오류")
    nfr=(p95 is not None and p95 >= P95_LIMIT_MS) or (p99 is not None and p99 >= P99_LIMIT_MS)
    if nfr and not hints: hints.append("DB row contention/쿼리 지연 추가 확인 필요")
    return "; ".join(dict.fromkeys(hints)) if hints else "뚜렷한 포화 신호 없음"


rows=[]
if RUNS.exists():
    for d in sorted(RUNS.iterdir()):
        if not d.is_dir() or "lock-concurrency" not in d.name: continue
        meta=parse_metadata(d/"metadata.env"); s=load_json(d/"summary.json")
        if not s: continue
        o=load_json(d/"observability.json")
        r={
            "run":d.name,"users":meta.get("user_count",""),"drop_id":meta.get("drop_id",""),"result":meta.get("result",""),
            "p95_ms":metric(s,"drop_lock_business_duration","p(95)"),"p99_ms":metric(s,"drop_lock_business_duration","p(99)"),
            "http_p95_ms":metric(s,"http_req_duration","p(95)"),"http_failed_rate":metric(s,"http_req_failed","rate"),
            "success":count(s,"drop_lock_success"),"sold_out":count(s,"drop_lock_sold_out"),"unexpected":count(s,"drop_lock_unexpected"),
            "process_cpu_max_pct":obs_max(o,"process_cpu_percent"),"jvm_heap_max_pct":obs_max(o,"jvm_heap_percent"),
            "gc_pause_max_sec_per_sec":obs_max(o,"gc_pause_seconds_per_second"),"tomcat_busy_max_pct":obs_max(o,"tomcat_busy_percent"),
            "hikari_active_max_pct":obs_max(o,"hikari_active_percent"),"hikari_pending_max":obs_max(o,"hikari_pending"),
            "server_rps_max":obs_max(o,"server_rps"),"server_5xx_max_rps":obs_max(o,"server_5xx_rps"),"server_p95_max_ms":obs_max(o,"server_p95_ms"),
            "observability":"Y" if o else "N",
        }
        r["bottleneck_candidates"]=diagnose(r); rows.append(r)

rows.sort(key=lambda r:(int(r["users"]) if str(r["users"]).isdigit() else 10**9,r["run"]))
fields=["run","users","drop_id","result","p95_ms","p99_ms","http_p95_ms","http_failed_rate","success","sold_out","unexpected",
        "process_cpu_max_pct","jvm_heap_max_pct","gc_pause_max_sec_per_sec","tomcat_busy_max_pct","hikari_active_max_pct","hikari_pending_max",
        "server_rps_max","server_5xx_max_rps","server_p95_max_ms","observability","bottleneck_candidates"]
with OUT_CSV.open("w",newline="",encoding="utf-8") as f:
    w=csv.DictWriter(f,fieldnames=fields); w.writeheader(); w.writerows(rows)
lines=["# OpenBake Stock Reservation Capacity Summary","",f"- NFR: P95 < {P95_LIMIT_MS:.0f}ms, P99 < {P99_LIMIT_MS:.0f}ms","- 병목 항목은 원인 확정이 아닌 후보 신호입니다.","",
       "| Users | Result | P95 | P99 | Hikari pending | CPU max | Tomcat busy | Candidate |","|---:|:---:|---:|---:|---:|---:|---:|---|"]
for r in rows:
    lines.append(f"| {r['users']} | {r['result']} | {fmt(r['p95_ms'])}ms | {fmt(r['p99_ms'])}ms | {fmt(r['hikari_pending_max'])} | {fmt(r['process_cpu_max_pct'])}% | {fmt(r['tomcat_busy_max_pct'])}% | {r['bottleneck_candidates']} |")
OUT_MD.write_text("\n".join(lines)+"\n",encoding="utf-8")

groups=defaultdict(list)
for r in rows:
    if str(r["users"]).isdigit(): groups[int(r["users"])].append(r)
grows=[]
for users,items in sorted(groups.items()):
    p95=[n for n in (num(x["p95_ms"]) for x in items) if n is not None]; p99=[n for n in (num(x["p99_ms"]) for x in items) if n is not None]
    grows.append({"users":users,"runs":len(items),"pass_count":sum(x.get("result")=="PASS" for x in items),
                  "p95_median_ms":statistics.median(p95) if p95 else "","p95_min_ms":min(p95) if p95 else "","p95_max_ms":max(p95) if p95 else "",
                  "p99_median_ms":statistics.median(p99) if p99 else ""})
gfields=["users","runs","pass_count","p95_median_ms","p95_min_ms","p95_max_ms","p99_median_ms"]
with OUT_GROUP_CSV.open("w",newline="",encoding="utf-8") as f:
    w=csv.DictWriter(f,fieldnames=gfields); w.writeheader(); w.writerows(grows)
glines=["# OpenBake Stock Reservation Repeatability Summary","","| Users | Runs | Pass | P95 median | P95 min~max | P99 median |","|---:|---:|---:|---:|---:|---:|"]
for r in grows:
    glines.append(f"| {r['users']} | {r['runs']} | {r['pass_count']}/{r['runs']} | {fmt(r['p95_median_ms'])}ms | {fmt(r['p95_min_ms'])}~{fmt(r['p95_max_ms'])}ms | {fmt(r['p99_median_ms'])}ms |")
OUT_GROUP_MD.write_text("\n".join(glines)+"\n",encoding="utf-8")
print(f"runs={len(rows)}")
print(f"csv={OUT_CSV}")
print(f"markdown={OUT_MD}")
