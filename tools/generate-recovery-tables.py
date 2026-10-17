#!/usr/bin/env python3
"""Derive implementation constants from exact approved normative tables; --check never writes."""
import json,sys
from pathlib import Path
root=Path('fixtures/p2-contract');d=json.loads((root/'TABLES.json').read_text())
def quoted(v):return ','.join(json.dumps(x) for x in v)
s='package fr.jarvis.fixture.p2\n\ninternal object RecoveryTables {\n val fields=mapOf(\n'+',\n'.join('  "'+k+'" to setOf('+quoted(v)+')' for k,v in d['fields'].items())+'\n )\n val prior=setOf('+quoted(d['prior_fields_when_PRESENT'])+')\n val enums=mapOf(\n'+',\n'.join('  "'+k+'" to setOf('+quoted(v)+')' for k,v in d['enums'].items())+'\n )\n val codes=setOf('+quoted(d['codes'])+')\n val journalStates=setOf('+quoted(d['journal_states'])+')\n}\n'
p=root/'src/main/java/fr/jarvis/fixture/p2/RecoveryTables.kt'
if '--check' in sys.argv:assert p.read_text()==s,'Generated recovery tables differ'
else:p.write_text(s)
