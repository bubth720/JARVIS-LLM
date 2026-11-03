import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('m50', ROOT / 'tools/check-m50-s1.py')
m50 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m50)


class PreservationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.name = 'app/src/main/AndroidManifest.xml'
        p = self.root / self.name
        p.parent.mkdir(parents=True)
        p.write_bytes(b'<manifest/>')
        self.frozen = {self.name: ('100644', b'<manifest/>')}
        self.actual = [self.name]
        self.modes = {self.name: '100644'}
        for name in m50.ADDITIONS:
            self.add(name)

    def check(self):
        m50.verify_files(self.root, self.frozen, self.actual, self.modes)

    def add(self, name, content=b'new', mode='100644'):
        p = self.root / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(content)
        self.actual.append(name)
        self.modes[name] = mode

    def test_unchanged(self):
        self.check()

    def test_exact_additions(self):
        self.check()

    def test_required_workflow_removed(self):
        self.actual.remove('.github/workflows/m50-s1.yml')
        with self.assertRaises(ValueError): self.check()

    def test_required_test_removed(self):
        self.actual.remove('tests/m50/test_preservation.py')
        with self.assertRaises(ValueError): self.check()

    def test_required_report_removed(self):
        self.actual.remove('docs/m50/M50-S1-A2.md')
        with self.assertRaises(ValueError): self.check()

    def test_manifest_permission_change(self):
        (self.root / self.name).write_bytes(b'<uses-permission/>')
        with self.assertRaises(ValueError): self.check()

    def test_baseline_deleted_on_disk(self):
        (self.root / self.name).unlink()
        with self.assertRaises(ValueError): self.check()

    def test_baseline_removed_from_index(self):
        self.actual.clear()
        with self.assertRaises(ValueError): self.check()

    def test_runtime_addition(self):
        self.add('app/src/main/cpp/extra.cpp')
        with self.assertRaises(ValueError): self.check()

    def test_workflow_addition(self):
        self.add('.github/workflows/unreviewed.yml')
        with self.assertRaises(ValueError): self.check()

    def test_baseline_workflow_mutation(self):
        self.add('.github/workflows/android.yml', b'original')
        self.frozen['.github/workflows/android.yml'] = ('100644', b'original')
        (self.root / '.github/workflows/android.yml').write_bytes(b'disabled')
        with self.assertRaises(ValueError): self.check()

    def test_symlink(self):
        p = self.root / self.name
        p.unlink()
        (self.root / 'target').write_bytes(b'<manifest/>')
        p.symlink_to(self.root / 'target')
        with self.assertRaises(ValueError): self.check()

    def test_mode_change(self):
        self.modes[self.name] = '100755'
        with self.assertRaises(ValueError): self.check()

    def test_addition_symlink_mode(self):
        self.modes['tools/check-m50-s1.py'] = '120000'
        with self.assertRaises(ValueError): self.check()

    def test_positive_pr(self):
        m50.verify_route('pull_request', m50.BASE_BRANCH, m50.BRANCH, '8/merge', True)

    def test_fork_with_matching_branch_rejected(self):
        with self.assertRaises(ValueError): m50.verify_route('pull_request', m50.BASE_BRANCH, m50.BRANCH, '8/merge', False)

    def test_positive_push(self):
        m50.verify_route('push', '', '', m50.BRANCH)

    def test_wrong_base(self):
        with self.assertRaises(ValueError): m50.verify_route('pull_request', 'main', m50.BRANCH, '', True)

    def test_wrong_head(self):
        with self.assertRaises(ValueError): m50.verify_route('pull_request', m50.BASE_BRANCH, 'codex/m50-evil', '', True)

    def test_wrong_push(self):
        with self.assertRaises(ValueError): m50.verify_route('push', '', '', 'main')

    def test_dispatch_rejected(self):
        with self.assertRaises(ValueError): m50.verify_route('workflow_dispatch', '', '', m50.BRANCH)


if __name__ == '__main__': unittest.main()
