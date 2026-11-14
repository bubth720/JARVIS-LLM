#!/usr/bin/env python3
"""Successor of dialogue_gate.py (unchanged, imported): the SAME evaluation, split in two (AUDIT-GLOBAL-01 A-002,
owner approval 14/11/2026).
  BLOCKING  : identity and safety — JARVIS takes the user's name, copied template, empty answer, off-topic name talk,
              stale name after a correction, wrong or missing name in the name scenarios, missing scenario.
  MEASURED  : factual knowledge of the model (Paris/Rome/Madrid, dessert topic) — printed, never blocking.
The historical gate keeps failing on factual answers of the 0.6B ("Italie : Lucca"); that is a model-quality
measurement, not an engine-integrity defect."""
import json, sys
sys.path.insert(0, __import__('os').path.dirname(__file__))
import dialogue_gate as g

FACTUAL = {("questions-successives", 0), ("questions-successives", 1), ("questions-successives", 2), ("changement-de-sujet", 1)}

def split(dialogues):
    _, results = g.evaluate(dialogues)
    blocking, measured = [], []
    for r in results:
        if r["status"] == "NOT_RUN": blocking.append(f"{r['scenario']}: NOT_RUN"); continue
        for p in r.get("problems", []):
            turn = int(p.split()[1].rstrip(':')) - 1 if p.startswith("turn ") else -1
            factual = (r["scenario"], turn) in FACTUAL and "should match" in p
            (measured if factual else blocking).append(f"{r['scenario']}: {p}")
    return blocking, measured, results

if __name__ == "__main__":
    ev = json.load(open(sys.argv[1]))
    blocking, measured, results = split(ev[sys.argv[2] if len(sys.argv) > 2 else "v1bDialogues"])
    for r in results:
        for a in r.get("answers", []): print("  >", a.replace("\n", " ")[:160])
    for m in measured: print("MEASURED (non-blocking, model knowledge):", m)
    for b in blocking: print("BLOCKING:", b)
    print(f"RESULT: {'PASS' if not blocking else 'FAIL'} — {len(blocking)} blocking, {len(measured)} measured")
    sys.exit(1 if blocking else 0)
