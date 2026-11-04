import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('perf02_guard', Path(__file__).with_name('check.py'))
guard = importlib.util.module_from_spec(spec); spec.loader.exec_module(guard)


class PreservationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup); self.root = Path(self.temp.name)
        self.frozen = {name: ('100644', b'unchanged') for name in ('app/src/main/cpp/bridge.cpp', 'app/src/main/AndroidManifest.xml',
            'domain/src/main/kotlin/fr/jarvis/domain/SafetyPolicy.kt', 'fixtures/p2-contract/CONTRACT.txt', guard.WORKFLOW)}
        self.actual = set(self.frozen) | guard.ADDITIONS
        self.modes = {n: '100644' for n in self.actual}
        for n in self.actual:
            p = self.root/n; p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(b'unchanged' if n in self.frozen else b'new')

    def check(self): guard.verify_files(self.root, self.frozen, self.actual, self.modes)
    def test_exact_scope_passes(self): self.check()
    def test_runtime_jni_policy_contract_manifest_and_workflow_frozen(self):
        for name in self.frozen:
            p = self.root/name; p.write_bytes(b'changed')
            with self.assertRaises(ValueError): self.check()
            p.write_bytes(b'unchanged')
    def test_unreviewed_native_file_rejected(self):
        n = 'app/src/main/cpp/stream.cpp'; self.actual.add(n)
        with self.assertRaises(ValueError): self.check()
    def test_model_or_download_script_addition_rejected(self):
        for name in ('model.gguf', 'perf02/download-model.sh'):
            self.actual.add(name)
            with self.assertRaises(ValueError): self.check()
            self.actual.remove(name)
    def test_required_test_or_report_missing_rejected(self):
        for name in ('perf02/REPORT.md', 'perf02/test_benchmark.py'):
            self.actual.remove(name)
            with self.assertRaises(ValueError): self.check()
            self.actual.add(name)
    def test_deleted_baseline_rejected(self):
        self.actual.remove('app/src/main/cpp/bridge.cpp')
        with self.assertRaises(ValueError): self.check()
    def test_symlink_and_mode_change_rejected(self):
        n = 'perf02/README.md'; p = self.root/n; p.unlink(); p.symlink_to(self.root/guard.WORKFLOW)
        with self.assertRaises(ValueError): self.check()
        p.unlink(); p.write_bytes(b'new'); self.modes[n] = '100755'
        with self.assertRaises(ValueError): self.check()
    def test_only_reviewed_branch_routes_allowed(self):
        guard.route('push', '', '', guard.BRANCH, True)
        guard.route('pull_request', guard.MISSION, guard.BRANCH, '10/merge', True)
        for args in [('push', '', '', 'main', True), ('pull_request', 'main', guard.BRANCH, '', True),
                     ('pull_request', guard.MISSION, guard.BRANCH, '', False), ('workflow_dispatch', '', '', guard.BRANCH, True)]:
            with self.assertRaises(ValueError): guard.route(*args)


if __name__ == '__main__': unittest.main()
