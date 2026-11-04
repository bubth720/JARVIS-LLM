import importlib.util
import os
from pathlib import Path
import tempfile
import time
import unittest

spec = importlib.util.spec_from_file_location('perf02_evidence', Path(__file__).with_name('evidence.py'))
evidence = importlib.util.module_from_spec(spec); spec.loader.exec_module(evidence)


class EvidenceTests(unittest.TestCase):
    def test_changed_sources_or_commit_cannot_reuse_results(self):
        marker = {'source_commit':'a'*40,'source_sha256':{'instrument.kt':'b'*64}}
        evidence.validate_binding(marker, 'a'*40, {'instrument.kt':'b'*64})
        for commit, inputs in [('c'*40, marker['source_sha256']), ('a'*40, {'instrument.kt':'d'*64})]:
            with self.assertRaises(ValueError): evidence.validate_binding(marker, commit, inputs)

    def test_stale_junit_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)/'TEST-Suite.xml'; p.write_text('<testsuite tests="1"><testcase classname="Suite" name="one"/></testsuite>')
            started = time.time_ns()+1_000_000
            with self.assertRaisesRegex(ValueError, 'STALE_JUNIT'): evidence.cases(d, started)

    def test_failed_or_skipped_case_not_counted_as_pass(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)/'TEST-Suite.xml'
            for kind in ('failure','error','skipped'):
                p.write_text('<testsuite tests="1"><testcase classname="Suite" name="one"><'+kind+'/></testcase></testsuite>')
                with self.assertRaises(ValueError): evidence.cases(d)

    def test_forged_count_duplicate_and_xml_entities_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)/'TEST-Suite.xml'
            for data in ['<testsuite tests="2"><testcase classname="Suite" name="one"/></testsuite>',
                         '<testsuite tests="2"><testcase classname="Suite" name="one"/><testcase classname="Suite" name="one"/></testsuite>',
                         '<!DOCTYPE x><testsuite tests="0"/>']:
                p.write_text(data)
                with self.assertRaises(ValueError): evidence.cases(d)


if __name__ == '__main__': unittest.main()
