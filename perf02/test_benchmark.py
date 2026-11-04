import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import uuid

spec = importlib.util.spec_from_file_location('perf02_benchmark', Path(__file__).with_name('benchmark.py'))
bench = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bench)


def row(cap=8):
    return {'schema': bench.SCHEMA, 'origin': 'SYNTHETIC', 'clock': 'SYNTHETIC', 'clock_scope': str(uuid.uuid4()),
            'residency': 'WARM_VERIFIED', 'state': 'COMPLETED', 'native_first_token_status': 'OBSERVED',
            'core_accept_status': 'OBSERVED', 'native_drain_status': 'UNKNOWN', 'native_unload_status': 'UNKNOWN', 'failure': None,
            'key': {'request': str(uuid.uuid4()), 'session': str(uuid.uuid4()), 'epoch': str(uuid.uuid4()),
                    'generation': 1, 'lease': str(uuid.uuid4()), 'model_sha256': 'a'*64},
            'case': {'environment_sha256': 'b'*64, 'config_sha256': 'c'*64, 'corpus_sha256': 'd'*64,
                     'profile': 'MOBILE_ECO', 'output_cap': cap, 'code_commit': 'e'*40},
            'events': {'UI_SEND': 1, 'CORE_ACCEPT': 2, 'ENGINE_ENTER': 3, 'NATIVE_ENTER': 4,
                       'NATIVE_FIRST_TOKEN': 5, 'NATIVE_RETURN': 6, 'BACKEND_RETURN': 7,
                       'UI_TEXT_SET': 8, 'UI_FIRST_RENDER': 9, 'COMPLETED': 10},
            'input_tokens': None, 'output_tokens': None, 'tokenizer_status': 'UNKNOWN',
            'sensors': {'rss_bytes': None, 'pss_bytes': None, 'thermal_status': None, 'battery_percent': None, 'energy_uj': None}}


class BenchmarkTests(unittest.TestCase):
    def test_twenty_samples_two_caps_never_claim_model_or_android(self):
        rows = [bench.validate(row(cap)) for cap in (8, 24) for _ in range(20)]
        report = bench.compare(rows, rows)
        self.assertTrue(report['two_response_caps_present'])
        for group in report['groups']:
            self.assertEqual(group['candidate']['ttft_native']['status'], 'NOT_RUN')
            self.assertIsNone(group['candidate']['ttft_native']['p95_ms'])
        self.assertEqual(report['sla_lt_3s'], 'NON_QUALIFIE')

    def test_real_legacy_cannot_label_ack_or_full_string_a_token(self):
        r = row(); r['origin'] = 'REAL_MODEL'
        with self.assertRaisesRegex(ValueError, 'UNSUPPORTED_REAL_CAPABILITY'): bench.validate(r)

    def test_real_buffered_result_keeps_ttft_not_run(self):
        r = row(); r['origin'] = 'REAL_MODEL'; r['clock'] = 'ANDROID_ELAPSED_REALTIME_NANOS'
        r['native_first_token_status'] = r['core_accept_status'] = 'UNSUPPORTED'
        del r['events']['NATIVE_FIRST_TOKEN']; del r['events']['CORE_ACCEPT']
        bench.validate(r)
        report = bench.compare([r], [r]); self.assertEqual('NOT_RUN', report['groups'][0]['candidate']['ttft_native']['status'])

    def test_unknown_sensor_is_not_zero(self):
        r = row(); r['sensors']['pss_bytes'] = 0
        with self.assertRaises(ValueError): bench.validate(r)

    def test_token_count_must_not_be_a_utf8_estimate(self):
        r = row(); r['input_tokens'] = 10
        with self.assertRaises(ValueError): bench.validate(r)

    def test_full_power_not_selectable(self):
        r = row(); r['case']['profile'] = 'FULL_POWER'
        with self.assertRaises(ValueError): bench.validate(r)

    def test_text_or_path_cannot_enter_metadata(self):
        for field in ('text', 'path', 'prompt'):
            r = row(); r[field] = 'SECRET'
            with self.assertRaises(ValueError): bench.validate(r)

    def test_clock_regression_rejected(self):
        r = row(); r['events']['NATIVE_RETURN'] = 0
        with self.assertRaises(ValueError): bench.validate(r)

    def test_cross_origin_timestamp_overflow_rejected(self):
        r = row(); r['events']['UI_SEND'] = -(2**63)
        with self.assertRaises(ValueError): bench.validate(r)

    def test_false_or_float_is_not_a_timestamp(self):
        for v in (False, 1.0, '1', None):
            r = row(); r['events']['UI_SEND'] = v
            with self.assertRaises(ValueError): bench.validate(r)

    def test_stop_cannot_be_followed_by_content(self):
        r = row(); r['events']['STOP_REQUESTED'] = 7
        with self.assertRaises(ValueError): bench.validate(r)

    def test_before_after_are_not_pooled_across_models_hardware_or_config(self):
        for field in ('environment_sha256', 'config_sha256', 'corpus_sha256'):
            a, b = row(), row(); b['case'][field] = 'f'*64
            self.assertTrue(all(not g['comparable'] for g in bench.compare([a], [b])['groups']))

    def test_cold_and_warm_are_separate(self):
        a, b = row(), row(); b['residency'] = 'COLD_PROCESS'
        self.assertTrue(all(not g['comparable'] for g in bench.compare([a], [b])['groups']))

    def test_mixed_commit_within_lane_is_rejected(self):
        a, b = row(), row(); b['case']['code_commit'] = 'f'*40
        with self.assertRaises(ValueError): bench.compare([a, b], [])

    def test_p95_minimum_and_quantile(self):
        self.assertIsNone(bench.nearest_rank(list(range(19)), 95))
        self.assertEqual(19, bench.nearest_rank(list(range(1, 21)), 95))
        self.assertEqual(10, bench.nearest_rank(list(range(1, 21)), 50))

    def test_empty_is_not_zero(self):
        self.assertIsNone(bench.nearest_rank([], 50)); self.assertEqual([], bench.compare([], [])['groups'])

    def test_duplicate_request_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)/'traces.jsonl'; data = json.dumps(row()); p.write_text(data+'\n'+data+'\n')
            with self.assertRaises(ValueError): bench.load(p)

    def test_duplicate_json_field_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)/'traces.jsonl'; p.write_text('{"schema":1,"schema":2}\n')
            with self.assertRaises(ValueError): bench.load(p)

    def test_nonfinite_json_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)/'traces.jsonl'; p.write_text('{"value":NaN}\n')
            with self.assertRaises(ValueError): bench.load(p)

    def test_file_bound_and_symlink(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)/'traces.jsonl'; p.write_bytes(b' '*(bench.MAX_FILE+1))
            with self.assertRaises(ValueError): bench.load(p)
            link = Path(d)/'link'; link.symlink_to(p)
            with self.assertRaises(ValueError): bench.load(link)

    def test_errors_and_cancellation_are_not_silently_excluded(self):
        a = row(); a['state'] = 'UNKNOWN'; a['failure'] = 'MEMORY_LIMIT'
        report = bench.group_report([a]); self.assertEqual(1, report['failures_or_stops']); self.assertIsNone(report['ttft_native']['p95_ms'])

    def test_report_cannot_overwrite_source_traces(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)/'traces.jsonl'; original = json.dumps(row())+'\n'; p.write_text(original)
            with patch('sys.argv', ['benchmark.py', '--baseline', str(p), '--candidate', str(p), '--output', str(p)]):
                with self.assertRaises(ValueError): bench.main()
            self.assertEqual(original, p.read_text())


if __name__ == '__main__': unittest.main()
