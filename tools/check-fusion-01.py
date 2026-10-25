#!/usr/bin/env python3
"""Exact additive freeze of the chosen runtime base. No safety/native/wire exceptions."""
import hashlib
import json
import pathlib
import subprocess

BASE = '2efb3f7aa6205062ab71f570212019fd1d9885b6'
ROOT = pathlib.Path(__file__).resolve().parent.parent

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)

def main():
    paths = git('ls-tree', '-r', '--name-only', BASE, '--', 'app', 'domain', 'fixtures',
                'docs/mission-003f-f3b', 'settings.gradle.kts', 'build.gradle.kts',
                'gradle', 'tools').decode().splitlines()
    # New files allowed, but every byte in the historical implementation AND tests is frozen.
    for name in paths:
        assert (ROOT / name).read_bytes() == git('show', BASE + ':' + name), name
    subprocess.run(['python3', 'tools/check-intensive-llm.py'], cwd=ROOT, check=True)
    source = list((ROOT/'domain/src/main/kotlin/fr/jarvis/domain/client').glob('*.kt'))
    source += list((ROOT/'app/src/main/java/fr/jarvis/llm/client').glob('*.kt'))
    for p in source:
        text = p.read_text()
        for forbidden in ['System.loadLibrary', 'NativeBridge', 'LlamaBackend(', 'ResourceCoordinator(',
                          'requireLease(', 'requireHeavy(', 'SQLiteOpenHelper', 'https://', 'http://']:
            assert forbidden not in text, (str(p), forbidden)
    patch = json.loads((ROOT/'docs/mission-003f-f3b/PATCH.json').read_text())
    assert patch['applied'] is False and patch['nativeAdmissionEnabled'] is False
    contract = ROOT/'docs/fusion-01/CLIENT-CONTRACT.json'
    actual = hashlib.sha256(contract.read_bytes()).hexdigest()
    expected = (ROOT/'docs/fusion-01/CLIENT-CONTRACT.sha256').read_text().split()[0]
    assert actual == expected, 'contract digest'
    # Both success and refusal examples are local DTO descriptors, never native attestations.
    manifest = json.loads((ROOT/'docs/fusion-01/PROVENANCE.json').read_text())
    assert manifest['base'] == BASE and manifest['native_enabled'] is False
    print(json.dumps({'base': BASE, 'frozen_files': len(paths), 'native_changed': False,
                      'contract_sha256': actual, 'physical_qualification': False}))

if __name__ == '__main__':
    main()
