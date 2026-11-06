#!/usr/bin/env python3
"""Execute the approved JNI itself without a model. No network, fake llama, or automatic retry."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
PATCH_SHA = '747598dd77bc53e1a2f8df7f2e60be824ff74f687d355b060e2012b55a665460'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def run(command, logfile, timeout, env=None):
    result = subprocess.run(command, cwd=ROOT, env=env, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, timeout=timeout)
    logfile.write_text(result.stdout)
    sys.stdout.write(result.stdout)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--library', type=Path, required=True)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--lane', choices=['plain', 'sanitized'], default='plain')
    parser.add_argument('--asan-runtime', type=Path)
    parser.add_argument('--cxx-runtime', type=Path)
    args = parser.parse_args()
    if (ROOT/'build/perf03b/CRITICAL-STOP.json').exists():
        raise ValueError('QUALIFICATION_ALREADY_STOPPED_PRESERVE_EVIDENCE_NO_RETRY')
    out = ROOT/'build/perf03b'/('jni-host-'+args.lane)
    out.mkdir(parents=True, exist_ok=True)
    library = args.library.resolve(strict=True)
    java, javac = args.jdk.resolve()/'bin/java', args.jdk.resolve()/'bin/javac'
    source = ROOT/'perf03b/native-tests/java/fr/jarvis/llm/engine/NativeBridge.java'
    manifest = json.loads((ROOT/'perf03/PREIMAGES.json').read_text())
    if sha(ROOT/'perf03/NATIVE-STREAMING.patch') != PATCH_SHA or manifest['patch_sha256'] != PATCH_SHA:
        raise ValueError('APPROVED_PATCH_CHANGED')
    for entry in manifest['files']:
        if sha(ROOT/entry['path']) != entry['after_sha256']:
            raise ValueError('APPROVED_POSTIMAGE_CHANGED:'+entry['path'])
    inputs = {str(p.relative_to(ROOT)): sha(p) for p in [source, Path(__file__).resolve()] +
              [ROOT/e['path'] for e in manifest['files']]}
    report = {
        'schema': 'jarvis.perf03b.jni-host-evidence/1',
        'source_commit': git('rev-parse', 'HEAD'), 'source_tree': git('rev-parse', 'HEAD^{tree}'),
        'worktree_dirty': bool(git('status', '--porcelain')), 'lane': args.lane,
        'approved_patch_sha256': PATCH_SHA, 'source_sha256': inputs,
        'native_library_sha256': sha(library), 'library': str(library),
        'jni': 'REAL_APPROVED_BRIDGE', 'model': 'NOT_RUN', 'real_token_stream': 'NOT_RUN',
        'prefill_decode': 'NOT_RUN', 'loaded_model_cleanup': 'NOT_RUN', 'ttft': 'NOT_RUN',
        'physical_android': 'NOT_RUN', 'native_drain_receipt': 'UNKNOWN',
        'model_unload_receipt': 'UNKNOWN', 'clock_domain': 'HOST_MONOTONIC',
        'model_downloaded': False, 'automatic_retry': False,
        'sanitizers': {'asan': 'ENABLED' if args.lane == 'sanitized' else 'NOT_RUN',
                       'ubsan': 'ENABLED' if args.lane == 'sanitized' else 'NOT_RUN',
                       'lsan_jvm': 'NOT_RUN_JVM_NOT_SUPPORTED'},
    }
    version = run([str(java), '-version'], out/'java-version.log', 20)
    if version.returncode or 'version "21.' not in version.stdout:
        raise ValueError('JDK21_REQUIRED')
    classes = out/'classes'
    classes.mkdir(exist_ok=True)
    compiled = run([str(javac), '-encoding', 'UTF-8', '-d', str(classes), str(source)], out/'javac.log', 60)
    if compiled.returncode:
        report.update(status='FAIL', phase='JAVAC', critical=False, exit_code=compiled.returncode)
        (out/'results.json').write_text(json.dumps(report, indent=2)+'\n')
        return 1
    env = dict(os.environ)
    if args.lane == 'sanitized':
        if args.asan_runtime is None or args.cxx_runtime is None:
            raise ValueError('SANITIZED_LANE_REQUIRES_EXPLICIT_ASAN_AND_CXX_RUNTIMES')
        runtime = args.asan_runtime.resolve(strict=True)
        cxx_runtime = args.cxx_runtime.resolve(strict=True)
        if runtime.read_bytes()[:4] != b'\x7fELF' or cxx_runtime.read_bytes()[:4] != b'\x7fELF':
            raise ValueError('PRELOADED_RUNTIMES_MUST_BE_ELF_SHARED_OBJECTS')
        report['asan_runtime_sha256'] = sha(runtime)
        report['cxx_runtime_sha256'] = sha(cxx_runtime)
        report['asan_runtime_path'] = str(runtime)
        report['cxx_runtime_path'] = str(cxx_runtime)
        report['preload_order'] = ['ASAN_RUNTIME', 'CXX_RUNTIME']
        # Map libstdc++ at JVM startup so ASan resolves __cxa_throw before the JNI's
        # first expected C++ exception. ASan remains first; link-order checking stays enabled.
        env['LD_PRELOAD'] = str(runtime)+':'+str(cxx_runtime) + (':'+env['LD_PRELOAD'] if env.get('LD_PRELOAD') else '')
        # JVM owns SIGSEGV for implicit null checks/stack guards. This is sanitizer interop,
        # not any change to Android resource, thermal, battery, context or model policies.
        env['ASAN_OPTIONS'] = 'detect_leaks=0:halt_on_error=1:handle_segv=0:use_sigaltstack=0:verify_asan_link_order=1'
        env['UBSAN_OPTIONS'] = 'halt_on_error=1:print_stacktrace=1'
        report['asan_options'] = env['ASAN_OPTIONS']
        report['ubsan_options'] = env['UBSAN_OPTIONS']
    started_ns = time.time_ns()
    command = [str(java), '-Xcheck:jni', '-Xms32m', '-Xmx128m', '-XX:+UseSerialGC',
               '-XX:-CreateCoredumpOnCrash', '-XX:ErrorFile='+str(out/'hs_err_pid%p.log'),
               '-Djava.library.path='+str(library.parent), '-cp', str(classes),
               'fr.jarvis.llm.engine.NativeBridge']
    try:
        executed = run(command, out/'stdout.log', 150, env=env)
        check_jni_failure = bool(re.search(r'(?:WARNING|FATAL ERROR)[^\n]*(?:JNI|native method)|JNI DETECTED ERROR',
                                          executed.stdout, re.IGNORECASE))
        sanitizer_failure = 'ERROR: AddressSanitizer' in executed.stdout or 'runtime error:' in executed.stdout
        summaries = [json.loads(line) for line in executed.stdout.splitlines()
                     if line.startswith('{"schema":"jarvis.perf03b.jni-host/1"')]
        summary = summaries[-1] if len(summaries) == 1 else None
        stable = inputs == {name: sha(ROOT/name) for name in inputs}
        passed = (executed.returncode == 0 and not check_jni_failure and not sanitizer_failure and stable and
                  summary is not None and summary.get('passed') == 22 and summary.get('failed') == 0 and
                  len(set(summary.get('cases', []))) == 22)
        report.update(status='PASS' if passed else 'FAIL', exit_code=executed.returncode,
                      critical=not passed, check_jni='PASS' if not check_jni_failure else 'FAIL',
                      sanitizer_diagnostic='FAIL' if sanitizer_failure else 'NONE', sources_stable=stable,
                      summary=summary, invocation_started_ns=started_ns,
                      stdout_sha256=sha(out/'stdout.log'))
    except subprocess.TimeoutExpired as error:
        stdout = error.stdout or b''
        if isinstance(stdout, bytes): stdout = stdout.decode('utf-8', errors='replace')
        (out/'stdout.log').write_text(stdout)
        report.update(status='FAIL', critical=True, phase='JNI_TIMEOUT', exit_code=None,
                      native_return='UNKNOWN', invocation_started_ns=started_ns)
    if args.lane == 'sanitized' and report['status'] == 'PASS':
        report['sanitizers'].update(asan='PASS', ubsan='PASS')
    (out/'results.json').write_text(json.dumps(report, indent=2)+'\n')
    (out/'sources.sha256').write_text(''.join(digest+'  '+name+'\n' for name,digest in sorted(inputs.items())))
    if report['status'] != 'PASS':
        (ROOT/'build/perf03b/CRITICAL-STOP.json').write_text(json.dumps({
            'reason': 'JNI_HOST_QUALIFICATION_FAILED', 'report': str(out/'results.json'),
            'source_commit': report['source_commit'], 'no_retry': True,
            'native_drain': 'UNKNOWN', 'model_unload': 'UNKNOWN'}, indent=2)+'\n')
    print(json.dumps({'status': report['status'], 'lane': args.lane,
                      'tests': (report.get('summary') or {}).get('passed'),
                      'critical': report.get('critical'), 'real_tokens': 'NOT_RUN', 'ttft': 'NOT_RUN'}))
    return 0 if report['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
