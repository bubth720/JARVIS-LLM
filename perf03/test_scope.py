import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('perf03_guard', Path(__file__).with_name('check.py'))
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class ScopeTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        names = ['app/src/main/cpp/bridge.cpp', 'app/src/main/AndroidManifest.xml',
                 'domain/src/main/kotlin/fr/jarvis/domain/SafetyPolicy.kt', '.github/workflows/android.yml',
                 'perf02/check.py', 'fixtures/p2-contract/CONTRACT.txt']
        self.frozen = {n: ('100644', b'original') for n in names}
        self.actual = set(names) | {'perf03/REPORT.md', guard.WORKFLOW}
        self.modes = {n: '100644' for n in self.actual}
        for n in self.actual:
            p = self.root/n
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(b'original' if n in self.frozen else b'review')

    def check(self):
        guard.verify_files(self.root, self.frozen, self.actual, self.modes)

    def test_additive_review_passes(self): self.check()

    def test_every_runtime_contract_policy_and_old_guard_frozen(self):
        for n in self.frozen:
            (self.root/n).write_bytes(b'mutated')
            with self.assertRaises(ValueError): self.check()
            (self.root/n).write_bytes(b'original')

    def test_historical_deletion_rejected(self):
        self.actual.remove('app/src/main/cpp/bridge.cpp')
        with self.assertRaises(ValueError): self.check()

    def test_unreviewed_additions_rejected(self):
        for n in ('app/src/main/cpp/new.cpp', '.github/workflows/silent.yml', 'model.gguf', '../outside'):
            self.actual.add(n)
            with self.assertRaises(ValueError): self.check()
            self.actual.remove(n)

    def test_mode_change_rejected(self):
        for n in ('perf03/REPORT.md', 'app/src/main/cpp/bridge.cpp'):
            self.modes[n] = '100755'
            with self.assertRaises(ValueError): self.check()
            self.modes[n] = '100644'

    def test_unindexed_file_rejected(self):
        del self.modes['perf03/REPORT.md']
        with self.assertRaises(ValueError): self.check()

    def test_symlink_file_and_parent_rejected(self):
        p = self.root/'perf03/REPORT.md'
        p.unlink()
        p.symlink_to(self.root/'perf02/check.py')
        with self.assertRaises(ValueError): self.check()
        p.unlink()
        (self.root/'perf03').rmdir()
        (self.root/'perf03').symlink_to(self.root/'perf02', target_is_directory=True)
        with self.assertRaises(ValueError): self.check()

    def test_exact_ci_routes(self):
        guard.route('push', '', '', guard.BRANCH, False)
        guard.route('pull_request', guard.TARGET, guard.BRANCH, '11/merge', True)

    def test_other_or_fork_routes_rejected(self):
        for args in [('push', '', '', 'main', True), ('pull_request', 'main', guard.BRANCH, '', True),
                     ('pull_request', guard.TARGET, guard.BRANCH, '', False),
                     ('workflow_dispatch', '', '', guard.BRANCH, True),
                     ('pull_request', guard.TARGET, 'attacker', '', True)]:
            with self.assertRaises(ValueError): guard.route(*args)

    def patch(self):
        return '\n'.join('diff --git a/'+n+' b/'+n+'\n--- a/'+n+'\n+++ b/'+n+'\n@@ -1 +1 @@\n-a\n+b' for n in sorted(guard.PATCH_TARGETS)).encode()

    def test_exact_patch_targets(self):
        self.assertEqual(set(guard.patch_entries(self.patch())), guard.PATCH_TARGETS)

    def test_incomplete_or_extra_patch_rejected(self):
        for data in (b'', b'diff --git a/app/src/main/AndroidManifest.xml b/app/src/main/AndroidManifest.xml\n',
                     self.patch()+b'\ndiff --git a/app/src/main/cpp/bridge.cpp b/app/src/main/cpp/bridge.cpp\n'):
            with self.assertRaises(ValueError): guard.patch_entries(data)

    def test_patch_modes_rename_deletion_and_binary_rejected(self):
        for bad in (b'new file mode 100755', b'old mode 100644', b'new mode 100755',
                    b'rename from old', b'deleted file mode 100644', b'GIT binary patch'):
            with self.assertRaises(ValueError): guard.patch_entries(self.patch()+b'\n'+bad+b'\n')


if __name__ == '__main__': unittest.main()
