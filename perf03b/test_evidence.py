import copy
import importlib.util
from pathlib import Path
import tempfile
import time
import unittest

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


if __name__ == '__main__': unittest.main()
