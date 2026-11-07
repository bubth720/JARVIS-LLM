"""Cloud ARM64 emulator only. No model, no native or APK modification, no retry."""
import argparse,hashlib,json,os,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
PACKAGE='fr.jarvis.llm.perf03b.lab'
APK_SHA='b1dfa7a93850db92019121d4c1a0000bd4a465709cc8e581d4f2a8daa7fa8411'
class Blocked(Exception):pass
def validate_guest(api,abi,qemu,expected):
    if qemu!='1' or api!=expected or 'arm64-v8a' not in abi.split(','):
        raise ValueError('REFUSE_NONMATCHING_CLOUD_EMULATOR_BEFORE_INSTALL')
def main():
    p=argparse.ArgumentParser();p.add_argument('--api',type=int,choices=[29,35],required=True);p.add_argument('--input',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    out=a.output;out.mkdir(parents=True,exist_ok=True);serial='emulator-5580';emu=None;logfile=None
    report={'api_requested':a.api,'status':'NOT_RUN','cases':[],'model':'NOT_RUN','ttft':'NOT_RUN','personal_installation':False,'apk_source_commit':'059cf9c3040d768e1074f62ec5d174c1a27a2590','apk_sha256':APK_SHA,'serial':serial}
    def run(cmd,label,timeout=60,check=True):
        r=subprocess.run(list(map(str,cmd)),capture_output=True,text=True,timeout=timeout)
        (out/(label+'.log')).write_text(r.stdout+r.stderr)
        if check and r.returncode:raise RuntimeError(label+' exit '+str(r.returncode))
        return r
    def adb(*args,**kw):return run([str(sdk/'platform-tools/adb'),'-s',serial,*args],**kw)
    def ui(label):
        adb('shell','uiautomator','dump','/sdcard/jarvis-ui.xml',label=label+'-dump')
        r=adb('shell','cat','/sdcard/jarvis-ui.xml',label=label+'-xml');return ET.fromstring(r.stdout[r.stdout.index('<?xml'):])
    def text(tree):return '\n'.join(n.get('text','') for n in tree.iter('node'))
    def case(name):report['cases'].append({'name':name,'status':'PASS'})
    try:
        host=run(['uname','-m'],'host').stdout.strip();report['host_arch']=host
        if host not in ('arm64','aarch64'):raise Blocked('BLOCKED_HOST_ABI')
        sdk=Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','/missing')))
        emulator=sdk/'emulator/emulator';manager=sdk/'cmdline-tools/latest/bin/sdkmanager';avd=sdk/'cmdline-tools/latest/bin/avdmanager'
        if not all(q.is_file() for q in [emulator,manager,avd,sdk/'platform-tools/adb']):raise Blocked('BLOCKED_SDK_TOOLS')
        report['runner_image']=os.environ.get('ImageVersion','UNKNOWN')
        run([emulator,'-version'],'emulator-version',check=False)
        acc=run([emulator,'-accel-check'],'acceleration',check=False)
        if acc.returncode:raise Blocked('BLOCKED_HYPERVISOR')
        image=f'system-images;android-{a.api};google_apis;arm64-v8a';report['system_image']=image
        listing=run([manager,'--list','--channel=0'],'sdk-list',timeout=180,check=False)
        if listing.returncode:raise Blocked('BLOCKED_SDK_REPOSITORY')
        if image not in listing.stdout:raise Blocked('BLOCKED_ARM64_IMAGE_UNAVAILABLE')
        apk=list(a.input.rglob('qualified-apk/*.apk'));assert len(apk)==1
        assert hashlib.sha256(apk[0].read_bytes()).hexdigest()==APK_SHA
        # Accept SDK licenses only in the ephemeral cloud runner; no model license/download action.
        r=subprocess.run([str(manager),'--licenses'],input='y\n'*100,text=True,capture_output=True,timeout=90)
        (out/'sdk-licenses.log').write_text(r.stdout+r.stderr)
        installed=run([manager,image],'image-install',timeout=600,check=False)
        if installed.returncode:raise Blocked('BLOCKED_IMAGE_INSTALL')
        env=dict(os.environ,ANDROID_AVD_HOME=str(Path(os.environ['RUNNER_TEMP'])/('jarvis-gateb-avd-'+str(a.api))));Path(env['ANDROID_AVD_HOME']).mkdir()
        r=subprocess.run([str(avd),'create','avd','--name','jarvis-gateb','--package',image],input='no\n',env=env,text=True,capture_output=True,timeout=90)
        (out/'avd-create.log').write_text(r.stdout+r.stderr)
        if r.returncode:raise Blocked('BLOCKED_AVD_CREATE')
        logfile=(out/'emulator.log').open('w');emu=subprocess.Popen([str(emulator),'-avd','jarvis-gateb','-port','5580','-no-window','-no-audio','-no-snapshot','-gpu','swiftshader_indirect'],env=env,stdout=logfile,stderr=subprocess.STDOUT)
        deadline=time.monotonic()+240
        while time.monotonic()<deadline:
            if emu.poll() is not None:raise Blocked('BLOCKED_EMULATOR_EXIT')
            r=adb('shell','getprop','sys.boot_completed',label='boot',check=False,timeout=15)
            if r.returncode==0 and r.stdout.strip()=='1':break
            time.sleep(2)
        else:raise Blocked('BLOCKED_BOOT_TIMEOUT')
        qemu=adb('shell','getprop','ro.kernel.qemu',label='qemu').stdout.strip()
        report['guest_api']=int(adb('shell','getprop','ro.build.version.sdk',label='guest-api').stdout.strip());assert report['guest_api']==a.api
        report['guest_abi']=adb('shell','getprop','ro.product.cpu.abilist',label='guest-abi').stdout.strip()
        validate_guest(report['guest_api'],report['guest_abi'],qemu,a.api);case('cloud_arm64_identity')
        adb('install','-t',apk[0],label='install',timeout=90);case('install_exact_testonly_apk')
        def launch(label):adb('shell','am','start','-W','-n',PACKAGE+'/'+PACKAGE+'.LabActivity',label=label)
        adb('shell','input','keyevent','82',label='unlock');launch('launch');tree=ui('initial');assert 'NOT_RUN' in text(tree) and 'Prêt' in text(tree);case('launch_without_model')
        button=next(n for n in tree.iter('node') if n.get('text')=='Tester le cycle de vie natif sans modèle')
        x1,y1,x2,y2=map(int,re.findall(r'\d+',button.get('bounds')));adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2),label='exercise')
        deadline=time.monotonic()+20
        while time.monotonic()<deadline:
            t=text(ui('lifecycle'))
            if 'PASS —' in t:break
            if 'CLEANUP_FAILED' in t or 'FAIL —' in t:raise RuntimeError('NATIVE_LIFECYCLE_FAILED')
            time.sleep(1)
        else:raise RuntimeError('NATIVE_LIFECYCLE_TIMEOUT')
        case('actual_android_jni_lifecycle_without_model')
        screenshot=subprocess.run([str(sdk/'platform-tools/adb'),'-s',serial,'exec-out','screencap','-p'],capture_output=True,timeout=30)
        if screenshot.returncode==0:(out/'lifecycle.png').write_bytes(screenshot.stdout)
        adb('shell','settings','put','system','accelerometer_rotation','0',label='rotation-auto');adb('shell','settings','put','system','user_rotation','1',label='rotate')
        assert 'NOT_RUN' in text(ui('rotated'));case('rotation_screen_survives')
        adb('shell','input','keyevent','3',label='home');launch('foreground');assert 'NOT_RUN' in text(ui('resumed'));case('background_foreground')
        adb('shell','cmd','connectivity','airplane-mode','enable',label='airplane');assert adb('shell','settings','get','global','airplane_mode_on',label='airplane-check').stdout.strip()=='1'
        adb('shell','am','force-stop',PACKAGE,label='force-stop');launch('restart');assert 'Prêt' in text(ui('restarted'));case('force_stop_restart_airplane')
        report['status']='PASS'
        report['not_run']=['generation','token_stream','prefill_decode_stop','ui_stop_race','thermal_memory_refusal_simulation','physical_performance']
    except Blocked as e:report.update(status='BLOCKED',reason=str(e))
    except Exception as e:report.update(status='FAIL',reason=type(e).__name__+': '+str(e))
    finally:
        if emu is not None:
            try:adb('logcat','-d','-v','threadtime',label='logcat',timeout=30,check=False)
            except Exception:pass
            emu.terminate()
            try:emu.wait(timeout=20)
            except subprocess.TimeoutExpired:emu.kill()
        if logfile:logfile.close()
        report['source_commit']=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
        (out/'results.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
    return 1 if report['status']=='FAIL' else 0
if __name__=='__main__':raise SystemExit(main())
