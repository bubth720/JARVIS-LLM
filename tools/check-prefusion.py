"""PRE-FUSION lot 1: frozen LLM source, historical FUSION-02 checked separately.

No Android qualification, dependency replacement or receipt rewriting occurs here.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
MISSION = '12fc58a81ceee70eedbba2d0b187b1294bae6283'
HISTORICAL = 'f147236820e0633b0c88f6f1725e828f71167b27'
WORKFLOW = '.github/workflows/android.yml'
ADDITIONS = {
    'tools/check-prefusion.py',
    'tests/prefusion/test_preservation.py',
    'docs/prefusion/ASSESSMENT.md',
    '.github/workflows/prefusion.yml',
}


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def verify_files(root, frozen, actual, additions, replacements):
    """Reject deletion, symlinks, changed baseline bytes and undeclared additions."""
    extra = set(actual) - set(frozen) - additions
    if extra:
        raise ValueError('Undeclared files: ' + ', '.join(sorted(extra)))
    for name in set(actual) | set(frozen):
        path = root / name
        if path.is_symlink() or not path.is_file():
            raise ValueError('Missing/non-regular file: ' + name)
        if name in frozen and path.read_bytes() != replacements.get(name, frozen[name]):
            raise ValueError('Frozen source changed: ' + name)


def main():
    subprocess.run(['git', 'merge-base', '--is-ancestor', MISSION, 'HEAD'], cwd=ROOT, check=True)
    # Respect the repository's reviewed checkout attributes (notably .bat CRLF).
    frozen = {p: git('cat-file', '--filters', MISSION + ':' + p) for p in
              git('ls-tree', '-r', '--name-only', MISSION).decode().splitlines()}
    original = frozen[WORKFLOW].decode()
    replacement = original.replace("branches-ignore: ['feat/fusion-01-llm-integration', 'feat/fusion-02-llm-prequalification']",
        "branches-ignore: ['feat/fusion-01-llm-integration', 'feat/fusion-02-llm-prequalification', 'mission/prefusion-gates-20261026', 'codex/prefusion-20261026-lot1']")
    replacement = replacement.replace(" && github.ref_name != 'feat/fusion-02-llm-prequalification'",
        " && github.ref_name != 'feat/fusion-02-llm-prequalification' && github.base_ref != 'mission/prefusion-gates-20261026' && github.head_ref != 'codex/prefusion-20261026-lot1' && github.ref_name != 'mission/prefusion-gates-20261026' && github.ref_name != 'codex/prefusion-20261026-lot1'")
    actual = git('ls-files', '--cached', '--others', '--exclude-standard').decode().splitlines()
    verify_files(ROOT, frozen, actual, ADDITIONS, {WORKFLOW: replacement.encode()})
    # The old guards keep their original scope and HEAD semantics. Check it at its
    # own exact SHA in a disposable local clone, never against a rewritten receipt.
    with tempfile.TemporaryDirectory(prefix='llm-preservation-') as temp:
        subprocess.run(['git', 'clone', '--quiet', '--shared', '--no-checkout', str(ROOT), temp], check=True)
        subprocess.run(['git', 'checkout', '--quiet', '--detach', HISTORICAL], cwd=temp, check=True)
        subprocess.run(['python3', 'tools/check-fusion-02.py'], cwd=temp, check=True)
    print('PASS PRE-FUSION source freeze at mission ' + MISSION)
    print('PASS historical FUSION-02 only at ' + HISTORICAL + '; native admission remains CLOSED')


if __name__ == '__main__':
    main()
