#!/usr/bin/env python3
"""Bounded real-model cloud comparison. No Android admission policy is changed."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/model-memory'
OUT.mkdir(parents=True, exist_ok=True)

def get_json(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return json.load(response)

stage = "initialization"
def report_exception(kind, error, traceback):
    detail = f"{stage}: {kind.__name__}: {error}"
    if isinstance(error, subprocess.CalledProcessError):
        detail += " " + (error.stderr or "")[-2000:]
    escaped = detail.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print("::error title=GGUF comparison failure::" + escaped, flush=True)
    sys.__excepthook__(kind, error, traceback)
sys.excepthook = report_exception

results = []
for repo, quant in [('Qwen/Qwen3-0.6B-GGUF', 'Q4_K_M'),
                    ('Qwen/Qwen3-0.6B-GGUF', 'Q3_K_M'),
                    ('Qwen/Qwen3-0.6B-GGUF', 'Q2_K'),
                    ('Qwen/Qwen2.5-0.5B-Instruct-GGUF', 'Q4_K_M')]:
    stage = f'catalog {repo} {quant}'
    info = get_json(f'https://huggingface.co/api/models/{repo}?blobs=true')
    requested_repo = repo
    def matching_files(catalog):
        return [f for f in catalog['siblings'] if f['rfilename'].lower().endswith(f'-{quant.lower()}.gguf')]
    files = matching_files(info)
    if not files and repo == 'Qwen/Qwen3-0.6B-GGUF':
        repo = 'unsloth/Qwen3-0.6B-GGUF'
        stage = f'fallback catalog {repo} {quant}'
        info = get_json(f'https://huggingface.co/api/models/{repo}?blobs=true')
        files = matching_files(info)
    revision = info['sha']
    assert re.fullmatch('[0-9a-f]{40}', revision), 'immutable revision missing'
    if not files:
        results.append(dict(repo=repo, requested_repo=requested_repo, revision=revision, quantization=quant, status='not_published_in_checked_repositories', available_files=[f['rfilename'] for f in info['siblings']]))
        continue
    assert len(files) == 1, 'ambiguous or sharded artifact'
    entry = files[0]
    lfs = entry['lfs']
    expected = lfs['sha256']
    assert re.fullmatch('[0-9a-f]{64}', expected)
    size = lfs['size']
    assert 0 < size <= 800 * 1024 * 1024, 'download size exceeds probe limit'
    target = OUT / (expected + '.gguf')
    url = f'https://huggingface.co/{repo}/resolve/{revision}/' + urllib.parse.quote(entry['rfilename'])
    digest = hashlib.sha256()
    stage = f'download {repo} {quant}'
    with urllib.request.urlopen(url, timeout=120) as response, target.open('wb') as output:
        received = 0
        while chunk := response.read(1024 * 1024):
            received += len(chunk)
            if received > size:
                raise ValueError('download exceeds declared length')
            digest.update(chunk)
            output.write(chunk)
    assert received == size and digest.hexdigest() == expected, 'GGUF checksum mismatch'
    record = dict(repo=repo, requested_repo=requested_repo, revision=revision, file=entry['rfilename'], sha256=expected,
                  file_bytes=size, declared_license=info.get('cardData', {}).get('license'), quantization=quant, status='measured_linux_x86_64', runs=[])
    for context in (768, 512):
        stage = f'inference {repo} {quant} context={context}'
        run = subprocess.run([str(ROOT / 'build/native-tests/model_memory_probe'), str(target), str(context)],
                             capture_output=True, text=True, timeout=90, check=True)
        measured = json.loads(run.stdout)
        measured['response'] = bytes.fromhex(measured.pop('response_utf8_hex')).decode('utf-8', errors='replace')
        record['runs'].append(measured)
    if requested_repo == 'Qwen/Qwen3-0.6B-GGUF' and quant == 'Q4_K_M':
        jni = subprocess.run(['./tools/test-jni-p2.sh', str(target)], capture_output=True, text=True, timeout=180, check=True)
        (OUT / 'p2-jni.json').write_text(json.dumps(dict(source_commit=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(), model_sha256=expected, platform='Linux x86_64 real JNI, NOT Android qualification', passed=True, result=jni.stdout),indent=2)+'\n')
        print(jni.stdout, flush=True)
    results.append(record)
    # Models are not included in the diagnostic artifact or Android APK.
    target.unlink()
assert (OUT / 'p2-jni.json').exists(), 'required P2 JNI evidence missing'
report = dict(platform='Linux x86_64 GitHub-hosted runner; NOT Android PSS',
              source_commit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              conditions='one fresh process per model/context; CPU one thread; 24 output tokens maximum; 60 s native deadline; one French prompt; no quality benchmark',
              admission_budget_changed=False, results=results)
(OUT / 'comparison.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
lines = ['| Model | Quant | Context | Peak RSS MiB | Native context MiB | Compute MiB |',
         '|---|---|---:|---:|---:|---:|']
for result in results:
    for run in result.get('runs', []):
        lines.append(f"| {result['repo']} | {result['quantization']} | {run['context']} | {run['linux_peak_rss_bytes']/2**20:.1f} | {run['context_buffer_bytes']/2**20:.1f} | {run['compute_buffer_bytes']/2**20:.1f} |")
summary = '\n'.join(lines) + '\n\nLinux RSS, not Android PSS. No mobile safety threshold was reduced.\n'
(OUT / 'SUMMARY.md').write_text(summary)
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as output:
        output.write(summary)
print(summary)
# Synthetic prompts and public model provenance only; no device/user data.
print('::notice title=Cloud GGUF evidence::' + json.dumps(report, ensure_ascii=False).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'), flush=True)
# Public downstream job title exposes only these nonsensitive cloud measurements.
q4 = results[0]
assert q4.get('runs'), 'mandatory Qwen3 Q4 probe not executed'
metrics = '; '.join(result['repo'].split('/')[-1].replace('-GGUF', '') + ' ' + result['quantization'] + ' ' + '/'.join(f"{r['context']}:{r['linux_peak_rss_bytes']/2**20:.1f}MiB(ctx{r['context_buffer_bytes']/2**20:.1f},calc{r['compute_buffer_bytes']/2**20:.1f})" for r in result['runs']) for result in results if result.get('runs'))
if os.environ.get('GITHUB_OUTPUT'):
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write('metrics=' + metrics + '\n')
