#!/usr/bin/env python3
"""V1-B dialogue quality gate over REAL model transcripts (no model call here, only checks).

Usage: dialogue_gate.py EVIDENCE.json [key]   (key: list of dialogues, default "v1bDialogues")
Each scenario has explicit expectations; the gate prints PASS/FAIL per scenario and exits 1 if any
scenario fails or is missing. It never edits or "fixes" an answer.
"""
import json, re, sys, unicodedata

def norm(s):
    return unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower()

SELF_AS_USER = re.compile(r"\b(je suis|je m'appelle|moi,? c'est|mon (pre)?nom est)\s+(thomas|julien)\b")
EXPECT = {
    "identite-prenom": [(2, "thomas")],
    "correction-prenom": [(2, "julien")],
    "questions-successives": [(0, "paris"), (1, "rome"), (2, "madrid")],
    "changement-de-sujet": [(2, "thomas")],
}

def evaluate(dialogues):
    results, ok = [], True
    by_name = {d["scenario"]: d for d in dialogues}
    for name, expectations in EXPECT.items():
        d = by_name.get(name)
        if d is None:
            results.append({"scenario": name, "status": "NOT_RUN"}); ok = False; continue
        problems = []
        answers = [t["answer"] for t in d["turns"]]
        for index, word in expectations:
            if index >= len(answers) or word not in norm(answers[index]):
                problems.append(f"turn {index + 1} should mention '{word}'")
        for i, a in enumerate(answers):
            if not a.strip(): problems.append(f"turn {i + 1} empty")
            if SELF_AS_USER.search(norm(a)): problems.append(f"turn {i + 1}: JARVIS takes the user's name")
        status = "PASS" if not problems else "FAIL"
        ok &= status == "PASS"
        results.append({"scenario": name, "status": status, "problems": problems, "answers": answers})
    return ok, results

def study(entries):
    """Prompt study: score each candidate instruction on the same scenarios (informative only)."""
    variants = {}
    for d in entries: variants.setdefault(d.get("variant", "?"), []).append(d)
    for name, dialogues in variants.items():
        ok, results = evaluate(dialogues)
        passed = sum(r["status"] == "PASS" for r in results)
        print(f"STUDY {name}: {passed}/{len(results)} scenarios PASS")
        for r in results:
            print("  ", r["status"], r["scenario"], "; ".join(r.get("problems", [])))
            for a in r.get("answers", []): print("     >", a.replace("\n", " ")[:160])

if __name__ == "__main__":
    evidence = json.load(open(sys.argv[1]))
    key = sys.argv[2] if len(sys.argv) > 2 else "v1bDialogues"
    if key == "v1bPromptStudy":
        study(evidence.get(key) or []); sys.exit(0)
    ok, results = evaluate(evidence.get(key) or [])
    for r in results:
        print(r["status"], r["scenario"], "; ".join(r.get("problems", [])))
        for a in r.get("answers", []): print("   >", a.replace("\n", " ")[:200])
    sys.exit(0 if ok else 1)
