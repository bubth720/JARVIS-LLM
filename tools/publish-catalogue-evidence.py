#!/usr/bin/env python3
"""Publish actual test-generated metrics, not estimates or device claims."""
import json
from pathlib import Path
import subprocess
report_file = Path('app/build/reports/catalogue-scale.json')
report = json.loads(report_file.read_text())
if report.get('synthetic_records', 0) <= 1000 or report.get('llama_loaded') is not False:
    raise RuntimeError('Required catalogue scale/no-LLM evidence is absent')
report['source_commit'] = subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
report_file.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
payload = json.dumps(report,ensure_ascii=False).replace('%','%25').replace('\r','%0D').replace('\n','%0A')
print('::notice title=Catalogue scale evidence::'+payload)
