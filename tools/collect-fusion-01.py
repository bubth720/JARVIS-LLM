#!/usr/bin/env python3
"""Collect results that actually exist; fail if suites are incomplete or failing."""
import argparse
import datetime
import hashlib
import json
import pathlib
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--output', default='build/fusion-01/results.json')
args = parser.parse_args()
roots = {'domain': ('domain/build/test-results/test',127),
         'android_simulated': ('app/build/test-results/testDebugUnitTest',40),
         'endpoint_simulated': ('fixtures/p2-endpoint/build/test-results/testDebugUnitTest',118)}
suites = {}
for label, (path, minimum) in roots.items():
    files = sorted(pathlib.Path(path).glob('TEST-*.xml'))
    totals = {key: 0 for key in ('tests','failures','errors','skipped')}
    for p in files:
        root = ET.parse(p).getroot()
        for key in totals: totals[key] += int(root.get(key,'0'))
    suites[label] = dict(totals, expected_minimum=minimum,
                         result_files=[{'path': str(p),'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in files])
report = {'format': 'jarvis.llm.fusion-01.validation/1', 'collected_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'source_commit': subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
          'working_tree_dirty': bool(subprocess.check_output(['git','status','--porcelain'])),
          'suites': suites, 'native_ready': False, 'android_instrumented': 'NOT_RUN', 'physical': 'NOT_RUN'}
report['success'] = all(s['tests']>=s['expected_minimum'] and not any(s[k] for k in ('failures','errors','skipped')) for s in suites.values())
out=pathlib.Path(args.output);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k: {a:b for a,b in v.items() if a!='result_files'} for k,v in suites.items()}))
raise SystemExit(0 if report['success'] else 1)
