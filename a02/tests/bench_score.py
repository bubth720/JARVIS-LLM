#!/usr/bin/env python3
"""Scores M55-B evidence files against fr-bench-30.json (published keyword checks) and reports
per-category PASS counts and p50/p95 latencies. Prints a markdown table; never edits answers."""
import json, pathlib, re, statistics, sys, unicodedata
root = pathlib.Path(__file__).resolve().parents[2]
spec = {i["id"]: i for f in ("fr-bench-30.json", "fr-conv-01.json") for i in json.loads((root / "a02/tests/bench" / f).read_text())["items"]}
norm = lambda s: unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower()
def pct(values, q):
    v = sorted(values); return v[min(len(v) - 1, max(0, round(q * (len(v) - 1))))] if v else None
rows = []
for f in sys.argv[1:]:
    ev = json.load(open(f)); res = {}; cats = {}
    for item in ev["bench"]:
        s = spec[item["id"]]; last = norm(item["turns"][-1]["answer"])
        ok = (bool(re.search(s["expect"], last)) and not (s.get("forbid") and re.search(s["forbid"], last))
              and len(item["turns"][-1]["answer"].strip()) <= s.get("maxChars", 10**9))
        res[item["id"]] = ok; c = cats.setdefault(s["category"], [0, 0]); c[0] += ok; c[1] += 1
    turns = [t for i in ev["bench"] for t in i["turns"] if t["firstTokenMs"] >= 0]
    ttft = [t["firstTokenMs"] for t in turns]; tot = [t["totalMs"] for t in turns]; tps = [t["decodeTokensPerSecond"] for t in turns if t["decodeTokensPerSecond"] > 0]
    m = ev.get("memory") or {}
    rows.append((ev["label"], sum(res.values()), len(res), cats, pct(ttft, .5), pct(ttft, .95), pct(tot, .5), pct(tot, .95),
                 statistics.median(tps) if tps else 0, m))
    print(f"## {ev['label']} — {sum(res.values())}/{len(res)} PASS")
    for iid, ok in res.items():
        a = next(i for i in ev["bench"] if i["id"] == iid)["turns"][-1]["answer"].replace("\n", " ")
        print(f"- {'PASS' if ok else 'FAIL'} {iid} ({spec[iid]['category']}): {a[:150]}")
print("\n| Config | Bench | " + " | ".join(sorted({c for r in rows for c in r[3]})) + " | 1er jeton p50/p95 (ms) | Total p50/p95 (ms) | Jetons/s méd. | RSS chargé / VmHWM (Mio) | 20 sessions : shmem après (Mio) |")
print("|---" * (8 + len({c for r in rows for c in r[3]})) + "|")
for label, ok, n, cats, t50, t95, o50, o95, tps, m in rows:
    cs = " | ".join(f"{cats.get(c, [0, 0])[0]}/{cats.get(c, [0, 0])[1]}" for c in sorted({c for r in rows for c in r[3]}))
    print(f"| {label} | {ok}/{n} | {cs} | {t50} / {t95} | {o50} / {o95} | {tps} | {m.get('rssLoadedKb', 0) // 1024} / {m.get('vmHwmKb', 0) // 1024} | {m.get('shmemAfter20SessionsKb', 0) / 1024:.1f} |")
