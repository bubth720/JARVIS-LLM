"""Bytecode and negative compilation checks against the exact compiled postimages; never native execution."""
import argparse,importlib.util,json,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser();p.add_argument('--jdk',type=Path,required=True);p.add_argument('--cache',type=Path,required=True);a=p.parse_args()
    spec=importlib.util.spec_from_file_location('review',ROOT/'labadmission/run_review.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    output=ROOT/'build/labadmission/reports';output.mkdir(parents=True,exist_ok=True)
    binaries={k:ROOT/f'build/labadmission/kotlin-{k}/classes' for k in ('baseline','candidate')}
    def disassemble(variant,name):
        return subprocess.check_output([str(a.jdk/'bin/javap'),'-p','-s','-classpath',str(binaries[variant]/'review'),name],text=True)
    # Full NativeBridge surface and callback methods remain byte-for-byte equal in javap output.
    checks={}
    for name in ('fr.jarvis.llm.engine.NativeBridge','fr.jarvis.llm.engine.NativeApi'):
        old=disassemble('baseline',name);new=disassemble('candidate',name);assert old==new,name;checks[name]='PASS_IDENTICAL_DESCRIPTORS'
    callback='fr.jarvis.llm.engine.OwnedStreamCallback'
    def signatures(s):
        lines=s.splitlines();return {lines[i].strip():lines[i+1].strip() for i in range(len(lines)-1) if (' onNativePrepared(' in lines[i] or ' onNativeEvent(' in lines[i])}
    assert signatures(disassemble('baseline',callback))==signatures(disassemble('candidate',callback));checks['native_callback_methods']='PASS_IDENTICAL_DESCRIPTORS'
    runtime=[m.jar(a.cache,c) for c in m.RUNTIME_COORDS];compiler=[m.jar(a.cache,c) for c in m.COMPILER_COORDS]
    cp=os.pathsep.join(map(str,[binaries['candidate']/'domain',binaries['candidate']/'review']+runtime))
    cases={
      'lab_authority_cannot_be_core':'fun invalid(a: LabCloudAuthority): StreamOwnerAuthority = a',
      'lab_permit_cannot_be_core_grant':'fun invalid(a: LabSignedPermit): StreamGrant = a',
      'lab_binding_has_no_core_mandate':'fun invalid(a: LabStreamBinding) = a.coreMandateSha256',
      'broker_observation_constructor_private':'fun invalid(b: fr.jarvis.domain.resources.ResourceCoordinator,h: fr.jarvis.domain.resources.ResourceLease,m: fr.jarvis.domain.resources.ResourceLease) = fr.jarvis.domain.resources.ResourceObservation(b,h,m,"x",1L,1L,2L)',
      'trust_anchor_constructor_private':'fun invalid(k: java.security.PublicKey) = LabOperatorTrust(k)',
      'external_scope_extension_forbidden':'abstract class InvalidScope : StreamBinding',
    }
    failures={}
    for name,code in cases.items():
        path=output/(name+'.kt.txt');path.write_text('package fr.jarvis.llm.engine\n'+code+'\n')
        source=ROOT/'build/labadmission'/('negative-'+name+'.kt');source.write_text(path.read_text())
        args=[str(a.jdk/'bin/java'),'-Xmx1g','-cp',os.pathsep.join(map(str,compiler)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',cp,
              '-Xfriend-paths='+','.join(map(str,[binaries['candidate']/'review',binaries['candidate']/'domain'])),'-d',str(ROOT/'build/labadmission/negative-classes'),str(source)]
        run=subprocess.run(args,capture_output=True,text=True,timeout=90);log=run.stdout+run.stderr
        (output/(name+'.log')).write_text(log);assert run.returncode!=0,name
        # Exclude infrastructure errors: compiler must reject the intended contract.
        expected={'lab_authority_cannot_be_core':'Return type mismatch','lab_permit_cannot_be_core_grant':'Return type mismatch',
                  'lab_binding_has_no_core_mandate':'Unresolved reference','broker_observation_constructor_private':'Cannot access',
                  'trust_anchor_constructor_private':'fun invalid(k: java.security.PublicKey) = LabOperatorTrust(k)',
      'trust_anchor_constructor_private':'Cannot access',
                  'external_scope_extension_forbidden':'extending sealed classes or interfaces from a different module is prohibited'}[name]
        assert expected.lower() in log.lower(),log
        failures[name]='PASS_EXPECTED_COMPILER_REJECTION'
    result={'descriptors':checks,'negative_compilation':failures,'patch_applied':False,'jni_loaded':False}
    (output/'contracts.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
if __name__=='__main__':main()
