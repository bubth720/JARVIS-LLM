import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('perf03b_guard', Path(__file__).with_name('check.py'))
guard = importlib.util.module_from_spec(spec); spec.loader.exec_module(guard)


class ScopeTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup); self.root = Path(self.tmp.name)
        self.expected = {'app/src/main/cpp/bridge.cpp': ('100644', b'approved after'),
                         'app/src/main/cpp/stream_support.h': ('100644', b'approved new'),
                         'domain/SafetyPolicy.kt': ('100644', b'unchanged'),
                         'tools/check-preservation-003e.py': ('100644', b'unchanged'),
                         '.github/workflows/android.yml': ('100644', b'unchanged')}
        self.actual = set(self.expected) | {'perf03b/REPORT.md', guard.WORKFLOW}
        self.modes = {n:'100644' for n in self.actual}
        for n in self.actual:
            p = self.root/n; p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(self.expected[n][1] if n in self.expected else b'new qualification')

    def verify(self): guard.verify_files(self.root, self.expected, self.actual, self.modes)

    def test_exact_postimages_and_additions_pass(self): self.verify()

    def test_even_one_byte_of_approved_runtime_cannot_change(self):
        (self.root/'app/src/main/cpp/bridge.cpp').write_bytes(b'approved after!')
        with self.assertRaises(ValueError): self.verify()

    def test_unchanged_safety_guard_and_old_workflow_cannot_change(self):
        for name in ('domain/SafetyPolicy.kt', 'tools/check-preservation-003e.py', '.github/workflows/android.yml'):
            (self.root/name).write_bytes(b'drift')
            with self.assertRaises(ValueError): self.verify()
            (self.root/name).write_bytes(b'unchanged')

    def test_deletion_and_new_runtime_file_are_rejected(self):
        self.actual.remove('app/src/main/cpp/stream_support.h')
        with self.assertRaises(ValueError): self.verify()
        self.actual.add('app/src/main/cpp/stream_support.h')
        self.actual.add('app/src/main/cpp/second.cpp')
        with self.assertRaises(ValueError): self.verify()

    def test_no_other_workflow_model_or_parent_path_additions(self):
        for name in ('.github/workflows/silent.yml', 'model.gguf', '../escape', 'perf03b/../escape'):
            self.actual.add(name)
            with self.assertRaises(ValueError): self.verify()
            self.actual.remove(name)

    def test_modes_and_unindexed_sources_are_rejected(self):
        for name in ('perf03b/REPORT.md', 'app/src/main/cpp/bridge.cpp'):
            self.modes[name] = '100755'
            with self.assertRaises(ValueError): self.verify()
            self.modes[name] = '100644'
        del self.modes['perf03b/REPORT.md']
        with self.assertRaises(ValueError): self.verify()

    def test_symlink_and_symlinked_parent_are_rejected(self):
        path = self.root/'perf03b/REPORT.md'; path.unlink(); path.symlink_to(self.root/'domain/SafetyPolicy.kt')
        with self.assertRaises(ValueError): self.verify()
        path.unlink(); (self.root/'perf03b').rmdir(); (self.root/'perf03b').symlink_to(self.root/'domain', target_is_directory=True)
        with self.assertRaises(ValueError): self.verify()

    def test_only_dedicated_same_repo_routes(self):
        guard.route('push', '', '', guard.BRANCH, False)
        guard.route('pull_request', guard.TARGET, guard.BRANCH, '12/merge', True)
        for args in [('push','','','main',True), ('pull_request','main',guard.BRANCH,'',True),
                     ('pull_request',guard.TARGET,guard.BRANCH,'',False),
                     ('pull_request',guard.TARGET,'other','',True), ('workflow_dispatch','','',guard.BRANCH,True)]:
            with self.assertRaises(ValueError): guard.route(*args)


class ApplicationTests(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads((guard.ROOT/'perf03/PREIMAGES.json').read_text())
        self.proof = json.loads((guard.ROOT/'perf03b/APPLICATION.json').read_text())

    def test_exact_root_preapplication_receipt(self): guard.verify_application(self.proof, self.manifest)

    def test_no_alternate_patch_base_branch_or_applied_failure(self):
        for key,value in [('patch_sha256','0'*64), ('authorized_base_commit','0'*40),
                          ('authorized_base_tree','0'*40), ('branch','main'), ('dry_run_exit',1), ('apply_exit',1),
                          ('preapply_worktree_clean',False), ('postimages_verified',False)]:
            proof = copy.deepcopy(self.proof); proof[key] = value
            with self.assertRaises(ValueError): guard.verify_application(proof, self.manifest)

    def test_no_implicit_f3b_model_download_or_productive_activation(self):
        for key in ('historical_f3b_applied','production_activation','model_downloaded'):
            proof = copy.deepcopy(self.proof); proof[key] = True
            with self.assertRaises(ValueError): guard.verify_application(proof,self.manifest)

    def test_all_preimages_and_their_verified_bits_are_required(self):
        for mutate in ('missing','hash','mode','verified'):
            proof = copy.deepcopy(self.proof)
            if mutate == 'missing': proof['preimages'].pop()
            elif mutate == 'hash': proof['preimages'][0]['preimage_sha256'] = 'f'*64
            elif mutate == 'mode': proof['preimages'][0]['mode'] = '100755'
            else: proof['preimages'][0]['preimage_verified'] = False
            with self.assertRaises(ValueError): guard.verify_application(proof,self.manifest)

    def test_git_preimages_and_frozen_postimages_match_the_authorization(self):
        images = guard.approved_images(guard.ROOT,self.manifest)
        self.assertEqual(set(images),guard.TARGETS)
        for entry in self.manifest['files']:
            self.assertEqual(guard.digest(images[entry['path']][1]),entry['after_sha256'])

    def test_any_changed_postimage_or_extra_target_is_rejected(self):
        manifest = copy.deepcopy(self.manifest); manifest['files'][0]['after_sha256'] = 'f'*64
        with self.assertRaises(ValueError): guard.approved_images(guard.ROOT,manifest)
        manifest = copy.deepcopy(self.manifest); manifest['files'][0]['path'] = 'app/src/main/AndroidManifest.xml'
        with self.assertRaises(ValueError): guard.approved_images(guard.ROOT,manifest)


if __name__ == '__main__': unittest.main()
