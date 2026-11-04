#!/usr/bin/env python3
"""Offline trace validation/comparison, never a model runner or an Android qualification.

No model download, no trace synthesis, no tokenizer estimate and no generation loop.
Current observer/1 cannot expose native first token or real CORE acceptance: honest NOT_RUN.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import sys
import uuid

SCHEMA = 'jarvis.perf02.trace/1'
MAX_FILE = 4 * 1024 * 1024
MAX_RECORDS = 512
STAGES = {'UI_SEND', 'CORE_ACCEPT', 'ENGINE_ENTER', 'LOAD_REQUEST', 'LOAD_RETURN', 'MODEL_LOAD_ENTER',
          'MODEL_LOAD_RETURN', 'NATIVE_ENTER', 'NATIVE_FIRST_TOKEN', 'NATIVE_RETURN', 'BACKEND_RETURN',
          'UI_TEXT_SET', 'UI_FIRST_RENDER', 'COMPLETED', 'STOP_REQUESTED', 'DRAIN_RETURN'}
STATUSES = {'OBSERVED', 'UNSUPPORTED', 'UNKNOWN', 'NOT_RUN'}
FIELDS = {'schema', 'origin', 'clock', 'clock_scope', 'residency', 'state', 'native_first_token_status',
          'core_accept_status', 'native_drain_status', 'native_unload_status', 'failure', 'key', 'case',
          'events', 'input_tokens', 'output_tokens', 'tokenizer_status', 'sensors'}


def require(ok, code):
    if not ok:
        raise ValueError(code)


def exact_keys(value, expected):
    require(isinstance(value, dict) and set(value) == expected, 'UNKNOWN_OR_MISSING_FIELD')


def digest(value, width=64):
    require(isinstance(value, str) and re.fullmatch('[a-f0-9]{' + str(width) + '}', value), 'INVALID_DIGEST')


def integer(value):
    require(type(value) is int and -(2**63) <= value < 2**63, 'INVALID_MONOTONE_INTEGER')


def canonical_uuid(value):
    require(isinstance(value, str) and str(uuid.UUID(value)) == value, 'INVALID_CORRELATION')


def nearest_rank(values, percentile):
    require(type(percentile) is int and 1 <= percentile <= 100, 'INVALID_PERCENTILE')
    require(all(type(x) is int and x >= 0 for x in values), 'INVALID_DURATION')
    if not values or percentile == 95 and len(values) < 20:
        return None
    return sorted(values)[math.ceil(len(values) * percentile / 100) - 1]


def validate(row):
    exact_keys(row, FIELDS)
    require(row['schema'] == SCHEMA, 'UNSUPPORTED_SCHEMA')
    require(row['origin'] in {'REAL_MODEL', 'SYNTHETIC', 'DETERMINISTIC_LOCAL', 'UNAVAILABLE'}, 'INVALID_ORIGIN')
    require(row['clock'] in {'HOST_NANO_TIME', 'ANDROID_ELAPSED_REALTIME_NANOS', 'SYNTHETIC'}, 'INVALID_CLOCK')
    canonical_uuid(row['clock_scope'])
    require(row['residency'] in {'COLD_PROCESS', 'COLD_MODEL', 'WARM_VERIFIED', 'UNKNOWN'}, 'INVALID_RESIDENCY')
    require(row['state'] in {'OPEN', 'STOP_REQUESTED', 'COMPLETED', 'UNKNOWN'}, 'INVALID_STATE')
    require(row['failure'] in {None, 'CANCELLED', 'NATIVE_ERROR', 'BACKEND_ERROR', 'MEMORY_LIMIT', 'CLOCK_ERROR', 'INVALID_ORDER', 'CLOSED'}, 'INVALID_FAILURE')
    exact_keys(row['key'], {'request', 'session', 'epoch', 'generation', 'lease', 'model_sha256'})
    for k in ('request', 'session', 'epoch', 'lease'):
        canonical_uuid(row['key'][k])
    integer(row['key']['generation']); require(row['key']['generation'] > 0, 'INVALID_GENERATION')
    digest(row['key']['model_sha256'])
    exact_keys(row['case'], {'environment_sha256', 'config_sha256', 'corpus_sha256', 'profile', 'output_cap', 'code_commit'})
    for k in ('environment_sha256', 'config_sha256', 'corpus_sha256'):
        digest(row['case'][k])
    digest(row['case']['code_commit'], 40)
    require(row['case']['profile'] in {'MOBILE_ECO', 'TABLET_ECO', 'EQUILIBRE'}, 'PROFILE_NOT_AUTHORIZED')
    require(type(row['case']['output_cap']) is int and row['case']['output_cap'] in (8, 24), 'INVALID_RESPONSE_CASE')
    for field in ('native_first_token_status', 'core_accept_status', 'native_drain_status', 'native_unload_status'):
        require(row[field] in STATUSES, 'INVALID_STATUS')
    # This observer has no real JNI-first-token, CORE transport or drain/unload attestation.
    if row['origin'] != 'SYNTHETIC':
        require(row['native_first_token_status'] == 'UNSUPPORTED' and row['core_accept_status'] == 'UNSUPPORTED', 'UNSUPPORTED_REAL_CAPABILITY')
        require('NATIVE_FIRST_TOKEN' not in row['events'] and 'CORE_ACCEPT' not in row['events'], 'FABRICATED_REAL_TOKEN_OR_ACK')
        require(row['native_drain_status'] == 'UNKNOWN' and row['native_unload_status'] == 'UNKNOWN', 'FABRICATED_CLEANUP')
    require(row['tokenizer_status'] == 'UNKNOWN' and row['input_tokens'] is None and row['output_tokens'] is None, 'UNQUALIFIED_TOKEN_COUNT')
    exact_keys(row['sensors'], {'rss_bytes', 'pss_bytes', 'thermal_status', 'battery_percent', 'energy_uj'})
    require(all(x is None for x in row['sensors'].values()), 'SENSOR_COLLECTOR_NOT_CONNECTED')
    events = row['events']; require(isinstance(events, dict) and set(events) <= STAGES and len(events) <= len(STAGES), 'INVALID_STAGES')
    for value in events.values():
        integer(value)
    parents = {'CORE_ACCEPT': ('UI_SEND',), 'ENGINE_ENTER': ('UI_SEND',), 'LOAD_REQUEST': ('UI_SEND',),
               'LOAD_RETURN': ('LOAD_REQUEST',), 'MODEL_LOAD_ENTER': ('LOAD_REQUEST',), 'MODEL_LOAD_RETURN': ('MODEL_LOAD_ENTER',),
               'NATIVE_ENTER': ('ENGINE_ENTER',), 'NATIVE_FIRST_TOKEN': ('NATIVE_ENTER',), 'NATIVE_RETURN': ('NATIVE_ENTER',),
               'BACKEND_RETURN': ('ENGINE_ENTER',), 'UI_TEXT_SET': ('NATIVE_FIRST_TOKEN' if 'NATIVE_FIRST_TOKEN' in events else 'NATIVE_RETURN',),
               'UI_FIRST_RENDER': ('UI_TEXT_SET',), 'COMPLETED': ('NATIVE_RETURN', 'BACKEND_RETURN', 'UI_FIRST_RENDER'),
               'STOP_REQUESTED': ('UI_SEND',), 'DRAIN_RETURN': ('STOP_REQUESTED', 'NATIVE_RETURN')}
    if row['state'] != 'UNKNOWN':
        for stage, requirements in parents.items():
            if stage in events:
                for parent in requirements:
                    require(parent in events and 0 <= events[stage] - events[parent] < 2**63, 'OUT_OF_ORDER_OR_OVERFLOW')
        if 'STOP_REQUESTED' in events:
            require(not any(s in events and events[s] > events['STOP_REQUESTED'] for s in ('UI_FIRST_RENDER', 'UI_TEXT_SET', 'COMPLETED')), 'LATE_CONTENT_AFTER_STOP')
        require((row['state'] == 'COMPLETED') == ('COMPLETED' in events), 'INCOMPLETE_TERMINAL')
    return row


def load(path):
    path = Path(path)
    require(not path.is_symlink() and path.is_file() and path.stat().st_size <= MAX_FILE, 'TRACE_FILE_LIMIT')
    with path.open('rb') as stream:
        data = stream.read(MAX_FILE + 1)
    require(len(data) <= MAX_FILE, 'TRACE_FILE_LIMIT')
    records, seen = [], set()
    def pairs(items):
        d = {}
        for k, v in items:
            require(k not in d, 'DUPLICATE_JSON_FIELD'); d[k] = v
        return d
    for line in data.splitlines():
        if not line.strip():
            continue
        require(len(records) < MAX_RECORDS and len(line) <= 16384, 'TRACE_COUNT_OR_LINE_LIMIT')
        row = validate(json.loads(line, object_pairs_hook=pairs, parse_constant=lambda _: (_ for _ in ()).throw(ValueError('NON_FINITE_JSON'))))
        identity = tuple(row['key'][k] for k in ('request', 'session', 'epoch', 'generation', 'lease'))
        require(identity not in seen, 'DUPLICATE_REQUEST'); seen.add(identity); records.append(row)
    return records, hashlib.sha256(data).hexdigest()


def group_key(row):
    c = row['case']
    return (c['environment_sha256'], row['key']['model_sha256'], c['config_sha256'], c['corpus_sha256'],
            c['profile'], c['output_cap'], row['residency'], row['clock'])


def group_report(rows):
    reasons = set()
    if len(rows) < 20:
        reasons.add('FEWER_THAN_20_COMPARABLE_REQUESTS')
    if any(r['origin'] != 'REAL_MODEL' for r in rows):
        reasons.add('SYNTHETIC_OR_NON_MODEL_SOURCE')
    if any(r['clock'] != 'ANDROID_ELAPSED_REALTIME_NANOS' for r in rows):
        reasons.add('NOT_ANDROID_MONOTONE_CAPTURE')
    if any(r['residency'] == 'UNKNOWN' or r['state'] != 'COMPLETED' or r['failure'] is not None for r in rows):
        reasons.add('UNKNOWN_FAILED_STOPPED_OR_INCOMPLETE_REQUEST')
    if any(r['native_first_token_status'] != 'OBSERVED' or r['core_accept_status'] != 'OBSERVED' for r in rows):
        reasons.add('MISSING_REAL_NATIVE_TOKEN_OR_CORE_TIMESTAMP')
    # A declared source label is not model execution, hardware qualification or native authority.
    reasons.add('NATIVE_STREAMING_NOT_APPROVED_OR_CONNECTED')
    empty = {'status': 'NOT_RUN', 'p50_ms': None, 'p95_ms': None, 'max_ms': None}
    return {'attempts': len(rows), 'failures_or_stops': sum(r['state'] != 'COMPLETED' or r['failure'] is not None for r in rows),
            'ttft_native': dict(empty), 'first_model_render': dict(empty), 'complete_response': dict(empty),
            'reasons': sorted(reasons), 'physical_android': 'NOT_RUN', 'sla_lt_3s': 'NON_QUALIFIE'}


def compare(baseline, candidate):
    left, right = {}, {}
    for source, target in ((baseline, left), (candidate, right)):
        for row in source:
            target.setdefault(group_key(row), []).append(row)
    groups = []
    for key in sorted(set(left) | set(right)):
        a, b = left.get(key, []), right.get(key, [])
        # Never mix differing commits within one lane or subtract different clocks/processes.
        require(all(len({r['case']['code_commit'] for r in lane}) <= 1 for lane in (a, b)), 'MIXED_CODE_LANE')
        groups.append({'binding': dict(zip(('environment_sha256', 'model_sha256', 'config_sha256', 'corpus_sha256', 'profile',
            'output_cap', 'residency', 'clock'), key)), 'comparable': bool(a and b),
            'baseline': group_report(a), 'candidate': group_report(b), 'delta_ttft_p95_ms': None})
    return {'schema': 'jarvis.perf02.comparison/1', 'scope': 'METADATA_VALIDATION_ONLY', 'groups': groups,
            'two_response_caps_present': {r['case']['output_cap'] for r in baseline + candidate} == {8, 24},
            'physical_android': 'NOT_RUN', 'native_generation': 'NOT_RUN', 'sla_lt_3s': 'NON_QUALIFIE'}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--baseline', required=True); p.add_argument('--candidate', required=True); p.add_argument('--output', required=True)
    args = p.parse_args()
    target = Path(args.output)
    require(not target.exists() and not target.is_symlink() and target.resolve() not in
            {Path(args.baseline).resolve(), Path(args.candidate).resolve()}, 'OUTPUT_MUST_NOT_OVERWRITE_INPUT_OR_EXISTING_REPORT')
    a, ah = load(args.baseline); b, bh = load(args.candidate)
    result = compare(a, b); result['input_sha256'] = {'baseline': ah, 'candidate': bh}
    with target.open('x') as stream:
        stream.write(json.dumps(result, indent=2, allow_nan=False) + '\n')
    print('Metadata comparison saved; native generation and physical Android NOT_RUN; SLA NON_QUALIFIE.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, RecursionError, TypeError, KeyError):
        print('INVALID_OR_UNAVAILABLE_TRACE_INPUT', file=sys.stderr); sys.exit(2)
