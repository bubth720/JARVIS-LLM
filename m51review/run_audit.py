"""Independent review: compile exact postimages via inherited harness, then separate friend-module audit tests."""
import argparse,hashlib,importlib.util,json,os,re,shutil,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def load_harness():
    spec=importlib.util.spec_from_file_location('frozen_review',ROOT/'labadmission/run_review.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module
def run(args,path,timeout):
    p=subprocess.run(args,cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=timeout)
    path.write_text(p.stdout);print(p.stdout);return p

def main():
    p=argparse.ArgumentParser();p.add_argument('--jdk',type=Path,required=True);p.add_argument('--cache',type=Path,required=True);a=p.parse_args()
    out=ROOT/'build/m51review';out.mkdir(parents=True,exist_ok=True)
    inherited=run([sys.executable,'labadmission/run_review.py','--jdk',str(a.jdk),'--cache',str(a.cache),'--historical'],out/'inherited.log',420)
    if inherited.returncode:raise SystemExit(inherited.returncode)
    m=load_harness();compiler=[m.jar(a.cache,c) for c in m.COMPILER_COORDS];runtime=[m.jar(a.cache,c) for c in m.RUNTIME_COORDS]
    binroot=ROOT/'build/labadmission/kotlin-candidate/classes';target=out/'audit-classes'
    if target.exists():shutil.rmtree(target)
    target.mkdir()
    tests=sorted((ROOT/'m51review/tests').glob('*.kt'));sources={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in tests}
    cp=os.pathsep.join(map(str,[binroot/'domain',binroot/'review']+runtime))
    command=[str(a.jdk/'bin/java'),'-Xmx1g','-cp',os.pathsep.join(map(str,compiler)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17',
             '-module-name','m51_independent_audit','-classpath',cp,'-Xfriend-paths='+','.join(map(str,[binroot/'domain',binroot/'review'])),'-d',str(target)]+[str(p) for p in tests]
    compiled=run(command,out/'audit-compile.log',180)
    report={'schema':'jarvis.m51.authority-audit/1','base_commit':'69400fe26d61688262b94cc8a2d5efe20d1da4be',
            'commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'tree':subprocess.check_output(['git','rev-parse','HEAD^{tree}'],cwd=ROOT,text=True).strip(),
            'worktree_dirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT)),'audit_sources_sha256':sources,
            'patch_sha256':hashlib.sha256((ROOT/'labadmission/LAB-ADMISSION.patch').read_bytes()).hexdigest(),
            'inherited_tests':247,'audit_tests':None,'compile':'PASS' if compiled.returncode==0 else 'FAIL','junit':'NOT_RUN',
            'audit_verdict':'NO_GO','audit_pass_means':'CONTROL_BEHAVIOR_AND_FAILURE_MODES_REPRODUCED_NOT_SAFETY_QUALIFIED',
            'patch_applied':False,'jni_loaded':False,'weights_acquired':False,'real_generation':'NOT_RUN','android':'NOT_RUN','gate_a':'CLOSED','gate_root':'CLOSED','gate_b':'NOT_VALIDATED'}
    if compiled.returncode==0:
        result=run([str(a.jdk/'bin/java'),'-cp',str(target)+os.pathsep+cp,'org.junit.runner.JUnitCore','fr.jarvis.llm.engine.AuthorityAuditTest'],out/'audit-junit.log',45)
        match=re.search(r'(?m)^OK \((\d+) tests?\)$',result.stdout);report['junit']='PASS' if result.returncode==0 and match else 'FAIL'
        report['audit_tests']=int(match[1]) if match else None
        status=0 if report['junit']=='PASS' else 1
    else:status=compiled.returncode
    current={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in tests}
    report['sources_stable']=current==sources
    if not report['sources_stable']:status=1
    (out/'results.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report));raise SystemExit(status)
if __name__=='__main__':main()
