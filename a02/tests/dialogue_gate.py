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
# A copied instruction template is not an answer (study 0aa7232: "Enchante, <prenom>", "[prenom]").
PLACEHOLDER = re.compile(r"<\s*pre?nom\s*>|\[\s*pre?nom\s*\]|votre prenom|\bmarie\b|<x>|\bx\b")
NAME_TALK = re.compile(r"appelez|enchante|thomas|julien|prenom")
def answers_name(name):
    # "tu m'appelles Thomas" (you call ME Thomas) is a misreading; "tu t'appelles" or "vous vous appelez" is right.
    """The name must be given AS the user's name, not merely greeted ("Bonjour Thomas !" is no answer)."""
    return re.compile(r"(vous appelez|t'appelles|vous etes|tu es|nom est|nom,? c'est|c'est)\W+(\w+\W+){0,2}" + name)
EXPECT = {
    "identite-prenom": [(0, re.compile("jarvis")), (2, answers_name("thomas"))],
    "correction-prenom": [(2, answers_name("julien"))],
    "questions-successives": [(0, re.compile("paris")), (1, re.compile("rome")), (2, re.compile("madrid"))],
    "changement-de-sujet": [(1, re.compile("dessert|gateau|cake|fruit|tarte|creme|mousse|yaourt|compote")), (2, answers_name("thomas"))],
}
# (turn, regex) that must NOT match: no name talk where nobody gave a name, no stale name after a correction.
FORBID = {
    "questions-successives": [(0, NAME_TALK), (1, NAME_TALK), (2, NAME_TALK)],
    "correction-prenom": [(2, re.compile(r"thomas"))],
    "changement-de-sujet": [(1, re.compile(r"appelez|enchante"))],
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
        for index, pattern in expectations:
            if index >= len(answers) or not pattern.search(norm(answers[index])):
                problems.append(f"turn {index + 1} should match '{pattern.pattern}'")
        for index, pattern in FORBID.get(name, []):
            if index < len(answers) and pattern.search(norm(answers[index])):
                problems.append(f"turn {index + 1} off-topic ({pattern.pattern})")
        for i, a in enumerate(answers):
            if not a.strip(): problems.append(f"turn {i + 1} empty")
            if PLACEHOLDER.search(norm(a)): problems.append(f"turn {i + 1}: copied template placeholder")
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
