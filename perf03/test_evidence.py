import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('perf03_evidence', Path(__file__).with_name('evidence.py'))
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.xml = self.root/'TEST-suite.xml'
        self.valid = '<testsuite tests="1"><testcase classname="Original" name="preserved"/></testsuite>'
        self.xml.write_text(self.valid)

    def test_actual_case_identity_and_digest(self):
        result = evidence.passing_cases(self.root, 0)
        self.assertEqual(result['ids'], [['Original', 'preserved']])
        self.assertEqual(result['tests'], 1)
        self.assertEqual(len(result['xml_sha256']['TEST-suite.xml']), 64)

    def test_missing_and_stale_rejected(self):
        with self.assertRaises(ValueError): evidence.passing_cases(self.root, self.xml.stat().st_mtime_ns+1)
        self.xml.unlink()
        with self.assertRaises(ValueError): evidence.passing_cases(self.root, 0)

    def test_failure_error_and_skip_never_ignored(self):
        for tag in ('failure', 'error', 'skipped'):
            self.xml.write_text('<testsuite tests="1"><testcase classname="Original" name="x"><'+tag+'/></testcase></testsuite>')
            with self.assertRaises(ValueError): evidence.passing_cases(self.root, 0)

    def test_declared_count_and_empty_suite_rejected(self):
        for data in ('<testsuite tests="0"/>', self.valid.replace('tests="1"', 'tests="2"')):
            self.xml.write_text(data)
            with self.assertRaises(ValueError): evidence.passing_cases(self.root, 0)

    def test_duplicate_identity_across_files_rejected(self):
        (self.root/'TEST-duplicate.xml').write_text(self.valid)
        with self.assertRaises(ValueError): evidence.passing_cases(self.root, 0)

    def test_external_entities_and_symlink_rejected(self):
        self.xml.write_text('<!DOCTYPE testsuite [<!ENTITY x SYSTEM "file:///etc/passwd">]>'+self.valid)
        with self.assertRaises(ValueError): evidence.passing_cases(self.root, 0)
        self.xml.unlink()
        outside = self.root/'real.xml'
        outside.write_text(self.valid)
        self.xml.symlink_to(outside)
        with self.assertRaises(ValueError): evidence.passing_cases(self.root, 0)

    def regression(self, candidate_ids):
        evidence.OUTPUT = self.root
        self.addCleanup(setattr, evidence, 'OUTPUT', evidence.ROOT/'build/perf03')
        baseline = {'run': {'source_commit': evidence.BASE}, 'suites': {'domain': {'ids': [['Original', 'preserved']]}}}
        candidate = {'run': {'source_commit': 'new-commit', 'source_tree': 'new-tree'}, 'suites': {'domain': {'ids': candidate_ids}}}
        (self.root/'baseline-results.json').write_text(json.dumps(baseline))
        (self.root/'candidate-results.json').write_text(json.dumps(candidate))

    def test_regression_keeps_ids_not_only_counts(self):
        self.regression([['Replacement', 'same-count']])
        with self.assertRaises(ValueError): evidence.compare()

    def test_regression_allows_additions_without_performance_claim(self):
        self.regression([['Original', 'preserved'], ['New', 'candidate']])
        evidence.compare()
        result = json.loads((self.root/'regression.json').read_text())
        self.assertTrue(result['all_historical_ids_preserved'])
        self.assertEqual(result['suites']['domain']['new'], 1)
        self.assertEqual(result['performance_before_after'], 'NOT_RUN')
        self.assertEqual(result['sla_lt_3s'], 'NOT_QUALIFIED')


if __name__ == '__main__': unittest.main()
