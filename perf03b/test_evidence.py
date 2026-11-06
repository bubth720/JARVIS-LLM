import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('perf03b_evidence', Path(__file__).with_name('evidence.py'))
evidence = importlib.util.module_from_spec(spec); spec.loader.exec_module(evidence)


class EvidenceTests(unittest.TestCase):
    def report(self, baseline=False):
        return {'run':{'source_commit':evidence.BASE if baseline else 'candidate', 'source_tree':'tree'},
                'suites':{'domain':{'tests':1, 'failures':0, 'errors':0, 'skipped':0,'ids':[['Case','old']]}}}

    def test_case_identity_subset_preserved_without_performance_claim(self):
        before, after = self.report(True),self.report()
        after['suites']['domain']['ids'].append(['Case','new'])
        after['suites']['domain']['tests'] = 2
        result = evidence.compare_reports(before,after)
        self.assertEqual(result['suites']['domain']['new'],1)
        self.assertEqual(result['performance_before_after'],'NOT_RUN')
        self.assertEqual(result['real_generation'],'NOT_RUN')

    def test_missing_baseline_identity_or_wrong_reference_rejected(self):
        before, after = self.report(True),self.report()
        after['suites']['domain']['ids'] = [['Case','different']]
        with self.assertRaises(ValueError): evidence.compare_reports(before,after)
        before['run']['source_commit'] = 'unreviewed'
        with self.assertRaises(ValueError): evidence.compare_reports(before,self.report())

    def test_nonpassing_or_mismatched_suite_comparison_rejected(self):
        for key in ('failures','errors','skipped'):
            after = self.report(); after['suites']['domain'][key] = 1
            with self.assertRaises(ValueError): evidence.compare_reports(self.report(True),after)
        after = self.report(); after['suites']['extra'] = copy.deepcopy(after['suites']['domain'])
        with self.assertRaises(ValueError): evidence.compare_reports(self.report(True),after)

    def test_only_one_reviewed_jni_export_may_be_added(self):
        old = ['legacy'+str(i) for i in range(12)]
        evidence.validate_exports(old,old+[evidence.STREAM_EXPORT])
        for new in (old, old[1:]+[evidence.STREAM_EXPORT], old+[evidence.STREAM_EXPORT,'unapproved']):
            with self.assertRaises(ValueError): evidence.validate_exports(old,new)
        with self.assertRaises(ValueError): evidence.validate_exports(old+[evidence.STREAM_EXPORT],old)

    def test_comparison_rejects_inconsistent_reported_counts(self):
        after = self.report(); after['suites']['domain']['tests'] = 500
        with self.assertRaises(ValueError): evidence.compare_reports(self.report(True),after)

    def test_comparison_rejects_duplicate_case_identities(self):
        after = self.report(); after['suites']['domain']['ids'].append(['Case','old'])
        after['suites']['domain']['tests'] = 2
        with self.assertRaises(ValueError): evidence.compare_reports(self.report(True),after)

    def test_fresh_junit_with_exact_counts_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            started = time.time_ns()
            path = Path(directory)/'TEST-Case.xml'
            path.write_text('<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="Case" name="pass"/></testsuite>')
            self.assertEqual(evidence.passing_cases(directory,started)['tests'],1)

    def test_declared_failure_without_case_evidence_cannot_look_green(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'TEST-Case.xml'
            path.write_text('<testsuite tests="1" failures="1" errors="0" skipped="0"><testcase classname="Case" name="pass"/></testsuite>')
            with self.assertRaises(ValueError): evidence.passing_cases(directory,0)

    def test_stale_skipped_or_duplicate_junit_cannot_look_green(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'TEST-Case.xml'
            for body in ('<testcase classname="Case" name="skip"><skipped/></testcase>',
                         '<testcase classname="Case" name="dup"/><testcase classname="Case" name="dup"/>'):
                count = body.count('<testcase ')
                path.write_text('<testsuite tests="'+str(count)+'">'+body+'</testsuite>')
                with self.assertRaises(ValueError): evidence.passing_cases(directory,0)
            path.write_text('<testsuite tests="1"><testcase classname="Case" name="old"/></testsuite>')
            with self.assertRaises(ValueError): evidence.passing_cases(directory,path.stat().st_mtime_ns+1)

    def binary_fixture(self, directory):
        root = Path(directory); output = root/'build/perf03b'; output.mkdir(parents=True)
        for lane, source in [('plain','build/native-tests/libjarvis_llm.so'),('sanitized','build/perf03b-native-sanitized/libjarvis_llm.so')]:
            p = root/source; p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(b'unit fixture '+lane.encode())
            result = output/('jni-host-'+lane)/'results.json'; result.parent.mkdir()
            result.write_text(json.dumps({'status':'PASS','native_library_sha256':hashlib.sha256(p.read_bytes()).hexdigest()}))
        return root,output,root/'build/perf03b-host-binaries'

    def test_exact_tested_host_binaries_preserved_outside_reports(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(evidence,'provenance',return_value={'source_commit':'commit','source_tree':'tree'}), patch.object(evidence,'git',return_value='commit'):
            root,output,separate = self.binary_fixture(directory)
            report = evidence.preserve_host_binaries(root,output,separate)
            self.assertTrue(all(e['matches_tested_library'] for e in report['libraries'].values()))
            self.assertEqual((separate/'plain/libjarvis_llm.so').read_bytes(),b'unit fixture plain')
            self.assertTrue((separate/'SHA256SUMS').is_file())
            self.assertEqual(json.loads((output/'host-binaries.json').read_text())['source_tree'],'tree')
            self.assertFalse(any(output.rglob('*.so')))

    def test_mismatched_host_binary_is_preserved_but_cannot_be_qualified(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(evidence,'provenance',return_value={'source_commit':'commit'}), patch.object(evidence,'git',return_value='commit'):
            root,output,separate = self.binary_fixture(directory)
            (root/'build/native-tests/libjarvis_llm.so').write_bytes(b'changed binary')
            with self.assertRaises(ValueError): evidence.preserve_host_binaries(root,output,separate)
            self.assertEqual((separate/'plain/libjarvis_llm.so').read_bytes(),b'changed binary')
            self.assertEqual(json.loads((output/'host-binaries.json').read_text())['binary_report_mismatches'],['plain'])

    def test_missing_host_binary_stays_not_built_not_run(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(evidence,'provenance',return_value={'source_commit':'commit'}), patch.object(evidence,'git',return_value='commit'):
            root=Path(directory); output=root/'build/perf03b'; separate=root/'build/perf03b-host-binaries'
            report = evidence.preserve_host_binaries(root,output,separate)
            self.assertTrue(all(e['binary_status']=='NOT_BUILT' and e['qualification_status']=='NOT_RUN' for e in report['libraries'].values()))
            self.assertTrue((separate/'manifest.json').is_file())

    def test_native_build_cannot_leak_into_main_reports_artifact(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); output=root/'build/perf03b'
            with self.assertRaises(ValueError): evidence.preserve_host_binaries(root,output,output/'host-binaries')

    def test_binary_assertion_failure_still_archives_raw_xml_and_checksums(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); output=root/'build/perf03b'; output.mkdir(parents=True)
            xml=root/'domain/build/test-results/test/TEST-Case.xml'; xml.parent.mkdir(parents=True)
            xml.write_text('<testsuite tests="1"><testcase classname="Case" name="raw"/></testsuite>')
            helper=root/'build/perf03/helper-tests/plain.log'; helper.parent.mkdir(parents=True)
            helper.write_text('raw helper diagnostic')
            original=ValueError('HOST_BINARY_NOT_MATCH_TESTED_LIBRARY:plain')
            with patch.object(evidence,'ROOT',root),patch.object(evidence,'OUTPUT',output),patch.object(evidence,'provenance',return_value={'source_commit':'commit'}),patch.object(evidence,'preserve_host_binaries',side_effect=original):
                with self.assertRaises(ValueError) as raised: evidence.package()
            self.assertIs(raised.exception,original)
            self.assertEqual((output/'raw-junit/candidate/domain/TEST-Case.xml').read_bytes(),xml.read_bytes())
            self.assertIn('raw-junit/candidate/domain/TEST-Case.xml',(output/'SHA256SUMS').read_text())
            self.assertEqual((output/'helper-tests/plain.log').read_text(),'raw helper diagnostic')
            self.assertFalse(any((output/'helper-tests').glob('plain')))
            self.assertEqual(json.loads((output/'host-binaries-archive-error.json').read_text())['status'],'FAIL')


if __name__ == '__main__': unittest.main()
