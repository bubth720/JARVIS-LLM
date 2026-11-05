#!/usr/bin/env python3
"""Offline direct Kotlin/JUnit review; no Gradle daemon, model, JNI load or patch application."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
COMPILER_COORDS = [
    ('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.1.21'),
    ('org.jetbrains.kotlin', 'kotlin-stdlib', '2.1.21'),
    ('org.jetbrains.kotlin', 'kotlin-script-runtime', '2.1.21'),
    ('org.jetbrains.kotlin', 'kotlin-reflect', '1.6.10'),
    ('org.jetbrains.kotlin', 'kotlin-daemon-embeddable', '2.1.21'),
    ('org.jetbrains.intellij.deps', 'trove4j', '1.0.20200330'),
    ('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0'),
    ('org.jetbrains', 'annotations', '13.0'),
]
RUNTIME_COORDS = [
    ('org.jetbrains.kotlin', 'kotlin-stdlib', '2.1.21'),
    ('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.10.2'),
    ('org.jetbrains.kotlinx', 'kotlinx-coroutines-test-jvm', '1.10.2'),
    ('org.jetbrains', 'annotations', '13.0'),
    ('junit', 'junit', '4.13.2'), ('org.hamcrest', 'hamcrest-core', '1.3'),
]


def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def jar(cache, coordinate):
    group, artifact, version = coordinate
    matches = list((cache/group/artifact/version).glob('*/'+artifact+'-'+version+'.jar'))
    if len(matches) != 1 or matches[0].is_symlink():
        raise ValueError('MISSING_OR_AMBIGUOUS_CACHED_JAR:'+':'.join(coordinate))
    return matches[0].resolve()


def classes(files):
    result = []
    for p in files:
        source = p.read_text()
        if not re.search(r'@Test\b', source): continue
        package = re.search(r'(?m)^package\s+([\w.]+)', source)
        names = re.findall(r'(?m)^(?:(?:public|internal)\s+)?class\s+(\w+)', source)
        if package is None or len(names) != 1: raise ValueError('AMBIGUOUS_TEST_CLASS:'+str(p))
        result.append(package[1]+'.'+names[0])
    if not result or len(result) != len(set(result)): raise ValueError('MISSING_OR_DUPLICATE_TEST_CLASSES')
    return result


def command(args, output, timeout):
    run = subprocess.run(args, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
    output.write_text(run.stdout)
    sys.stdout.write(run.stdout)
    return run


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jdk', type=Path, required=True, help='Already provisioned JDK21; never downloaded by this script.')
    parser.add_argument('--cache', type=Path, required=True, help='Already provisioned Gradle files-2.1 directory.')
    parser.add_argument('--historical', action='store_true', help='Also execute original domain and six injected backend cases.')
    args = parser.parse_args()
    output = ROOT/'build/perf03/kotlin-direct'
    output.mkdir(parents=True, exist_ok=True)
    compiler_jars = [jar(args.cache, c) for c in COMPILER_COORDS]
    runtime_jars = [jar(args.cache, c) for c in RUNTIME_COORDS]
    java = args.jdk.resolve()/'bin/java'
    version = command([str(java), '-version'], output/'java-version.log', 15)
    if version.returncode or 'version "21.' not in version.stdout: raise ValueError('JDK21_REQUIRED')
    domain_sources = sorted((ROOT/'domain/src/main/kotlin').rglob('*.kt'))
    review_sources = [ROOT/'app/src/main/java/fr/jarvis/llm/ProcessResources.kt']
    review_sources += sorted((ROOT/'perf03/candidate/engine').glob('*.kt'))
    review_tests = sorted((ROOT/'perf03/kotlin-review/src/test/kotlin').rglob('*.kt'))
    domain_tests = []
    if args.historical:
        domain_tests = sorted((ROOT/'domain/src/test/kotlin').rglob('*.kt'))
        review_tests += [ROOT/'app/src/test/java/fr/jarvis/llm/NativeBackendLifecycleTest.kt',
                         ROOT/'app/src/test/java/fr/jarvis/llm/NativeFixtureBudgetTest.kt']
    tests = review_tests + domain_tests
    test_classes = classes(tests)
    source_files = domain_sources + review_sources + tests + [Path(__file__).resolve()]
    inputs = {p.relative_to(ROOT).as_posix(): sha(p) for p in source_files}
    binary = output/'classes'
    if binary.exists(): shutil.rmtree(binary)
    binary.mkdir()
    domain_binary, review_binary, domain_test_binary = [binary/name for name in ('domain', 'review', 'domain-tests')]
    for p in (domain_binary, review_binary, domain_test_binary): p.mkdir()
    runtime_cp = os.pathsep.join(map(str, runtime_jars))
    compiler_base = [str(java), '-Xmx2g', '-cp', os.pathsep.join(map(str, compiler_jars)),
                     'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17']
    stages = [('domain', domain_sources, domain_binary, runtime_cp, []),
              ('review', review_sources+review_tests, review_binary, str(domain_binary)+os.pathsep+runtime_cp, [])]
    if domain_tests:
        stages.append(('domain-tests', domain_tests, domain_test_binary, str(domain_binary)+os.pathsep+runtime_cp,
                       ['-Xfriend-paths='+str(domain_binary)]))
    compile_results = {}
    logs = []
    exit_code = 0
    for name, files, target, classpath, extra in stages:
        compile_args = compiler_base+['-module-name', 'perf03_'+name.replace('-', '_'), '-classpath', classpath,
                                     '-d', str(target)]+extra+[str(p) for p in files]
        compiled = command(compile_args, output/('compile-'+name+'.log'), 240)
        compile_results[name] = 'PASS' if compiled.returncode == 0 else 'FAIL'
        logs.append(name+'\n'+compiled.stdout)
        if compiled.returncode:
            exit_code = compiled.returncode
            break
    (output/'compile.log').write_text('\n'.join(logs))
    result = {'schema': 'jarvis.perf03a.direct-kotlin-review/1',
        'source_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'source_tree': subprocess.check_output(['git', 'rev-parse', 'HEAD^{tree}'], cwd=ROOT, text=True).strip(),
        'worktree_dirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT)),
        'source_sha256': inputs, 'compiler': 'Kotlin 2.1.21 direct K2JVMCompiler, JVM17/JDK21',
        'jars_sha256': {p.name: sha(p) for p in compiler_jars+runtime_jars},
        'historical_requested': args.historical, 'test_classes': test_classes,
        'compile': 'PASS' if exit_code == 0 else 'FAIL', 'compile_stages': compile_results,
        'module_boundaries': 'DOMAIN_AND_REVIEW_SEPARATE', 'domain_tests_friend_paths': bool(domain_tests),
        'junit': 'NOT_RUN', 'tests': None,
        'jni_loaded': False, 'patch_applied': False, 'real_generation': 'NOT_RUN', 'physical_android': 'NOT_RUN',
        'ttft_status': 'NOT_RUN', 'ttft_p95_ms': None, 'sla_lt_3s': 'NOT_QUALIFIED'}
    if exit_code == 0:
        test_cp = os.pathsep.join(map(str, (domain_binary, review_binary, domain_test_binary)))+os.pathsep+runtime_cp
        run = command([str(java), '-cp', test_cp, 'org.junit.runner.JUnitCore']+test_classes,
                      output/'junit.log', 180)
        match = re.search(r'(?m)^OK \((\d+) tests?\)$', run.stdout)
        result['junit'] = 'PASS' if run.returncode == 0 and match else 'FAIL'
        result['tests'] = int(match[1]) if match else None
        exit_code = run.returncode if run.returncode else (0 if match else 1)
    if inputs != {p.relative_to(ROOT).as_posix(): sha(p) for p in source_files}:
        result['sources_stable_during_run'] = False
        exit_code = 1
    else: result['sources_stable_during_run'] = True
    (output/'results.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps({k: result[k] for k in ('compile', 'junit', 'tests', 'patch_applied', 'real_generation')}))
    raise SystemExit(exit_code)


if __name__ == '__main__': main()
