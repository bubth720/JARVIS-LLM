"""Negative evidence checks, synthetic XML only; never native/device tests."""
import importlib.util
import os
from pathlib import Path
import sys
import tempfile
import time
import unittest

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('evidence', Path(__file__).resolve().parents[2]/'fusion02_evidence.py')
evidence = importlib.util.module_from_spec(spec); spec.loader.exec_module(evidence)

class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name);self.started = time.time_ns()-1_000_000_000
    def xml(self, name='TEST-one.xml', case='synthetic', extra='', count=1, failure=0, skipped=0):
        p=self.root/name
        p.write_text(f'<testsuite tests="{count}" failures="{failure}" errors="0" skipped="{skipped}"><testcase classname="Fixture" name="{case}">{extra}</testcase></testsuite>')
        return p
    def inspect(self, baseline=(('Fixture','synthetic'),)):
        return evidence.inspect_suite(self.root,baseline,self.started)
    def test_valid_evidence_counts_real_cases_and_hashes(self):
        self.xml();r=self.inspect();self.assertEqual(1,r['tests']);self.assertEqual(1,len(r['xml_sha256']))
    def test_stale_result_cannot_be_relabelled_fresh(self):
        p=self.xml();os.utime(p,ns=(1,1))
        with self.assertRaisesRegex(ValueError,'STALE'):self.inspect()
    def test_duplicate_case_in_different_files_is_not_two_tests(self):
        self.xml();self.xml('TEST-two.xml')
        with self.assertRaisesRegex(ValueError,'DUPLICATE_CASE_ACROSS'):self.inspect()
    def test_inflated_summary_cannot_hide_missing_cases(self):
        self.xml(count=285)
        with self.assertRaisesRegex(ValueError,'COUNT_MISMATCH'):self.inspect()
    def test_hidden_failure_is_not_a_success(self):
        self.xml(extra='<failure message="synthetic failure"/>')
        with self.assertRaisesRegex(ValueError,'COUNT_MISMATCH'):self.inspect()
    def test_declared_failure_is_refused(self):
        self.xml(extra='<failure/>',failure=1)
        with self.assertRaisesRegex(ValueError,'NON_PASSING'):self.inspect()
    def test_missing_baseline_cannot_be_replaced_by_extra_new_tests(self):
        self.xml(case='different')
        with self.assertRaisesRegex(ValueError,'BASELINE_CASE_MISSING'):self.inspect()
    def test_skipped_test_does_not_satisfy_a_required_pass(self):
        self.xml(extra='<skipped/>',skipped=1)
        with self.assertRaisesRegex(ValueError,'NON_PASSING'):self.inspect()
    def test_doctype_and_external_entity_are_refused(self):
        with self.assertRaisesRegex(ValueError,'DECLARATION_FORBIDDEN'):evidence.parse_xml(b'<!DOCTYPE x [<!ENTITY y SYSTEM "synthetic">]><testsuite/>')
    def test_empty_suite_is_not_a_success(self):
        with self.assertRaisesRegex(ValueError,'MISSING_OR_EXCESSIVE'):self.inspect()
    def test_symlink_results_are_refused(self):
        target=self.root/'source.xml';target.write_text('<testsuite/>');(self.root/'TEST-linked.xml').symlink_to(target)
        with self.assertRaisesRegex(ValueError,'SYMLINK'):self.inspect()

if __name__ == '__main__':unittest.main()
