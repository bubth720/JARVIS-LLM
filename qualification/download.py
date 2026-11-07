"""Download exactly the authorized weights to an ephemeral PRIVATE directory. No load."""
import argparse,hashlib,json,os,socket,time,urllib.request,urllib.parse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
REV='8e42d41f70cb6c571f58c3f31bd9287b372d97cc'
SIZE=396704416
SHA='b0638f08417a2d3c8652760462eb5407c6e30173cf9608ad0820757a281eea0e'
NAME='Qwen3-0.6B-Q4_K_M.gguf'
URL=f'https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/{REV}/{NAME}'
class VerificationError(Exception):pass
def allowed_url(url):
    p=urllib.parse.urlsplit(url);host=p.hostname or ''
    return p.scheme=='https' and (host=='huggingface.co' or host.endswith('.huggingface.co') or host=='hf.co' or host.endswith('.hf.co'))
class Redirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        if not allowed_url(newurl):raise VerificationError('UNAPPROVED_REDIRECT_HOST')
        return super().redirect_request(req,fp,code,msg,headers,newurl)
def open_url(url):
    if not allowed_url(url):raise VerificationError('UNAPPROVED_SOURCE')
    return urllib.request.build_opener(Redirect()).open(urllib.request.Request(url,headers={'User-Agent':'JARVIS-GATEB-Authorized-Laboratory','Accept-Encoding':'identity'}),timeout=60)
def text_url(url,maximum=131072):
    with open_url(url) as r:
        data=r.read(maximum+1)
        if len(data)>maximum:raise VerificationError('METADATA_TOO_LARGE')
        return data.decode('utf-8')
def copy_verified(stream,path,size,sha):
    total=0;digest=hashlib.sha256();start=time.monotonic()
    with path.open('xb') as f:
        while True:
            chunk=stream.read(min(1024*1024,size-total+1))
            if not chunk:break
            total+=len(chunk)
            if total>size:raise VerificationError('AUTHORIZED_SIZE_EXCEEDED')
            f.write(chunk);digest.update(chunk)
            if time.monotonic()-start>900:raise VerificationError('DOWNLOAD_DEADLINE')
    if total!=size:raise VerificationError('AUTHORIZED_SIZE_MISMATCH')
    observed=digest.hexdigest()
    if observed!=sha:raise VerificationError('AUTHORIZED_SHA256_MISMATCH')
    # Independent post-write read: do not admit merely on a transfer checksum.
    check=hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''):check.update(chunk)
    if check.hexdigest()!=sha:raise VerificationError('POSTWRITE_SHA256_MISMATCH')
    return {'actual_size_bytes':total,'actual_sha256':observed,'postwrite_sha256':check.hexdigest(),'download_elapsed_ms':(time.monotonic()-start)*1000}
def main():
    p=argparse.ArgumentParser();p.add_argument('--private-dir',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    report={'status':'NOT_RUN','model_repository':'Qwen/Qwen3-0.6B-GGUF','revision':REV,'file':NAME,'expected_size_bytes':SIZE,'expected_sha256':SHA,'model_loaded':False,'generation':'NOT_RUN','automatic_retry':False,'weight_publication':False}
    a.output.mkdir(parents=True,exist_ok=True)
    try:
        private=a.private_dir.resolve();public=a.output.resolve()
        if private.is_relative_to(ROOT) or private.is_relative_to(public) or public.is_relative_to(private):raise VerificationError('PRIVATE_WEIGHTS_MUST_BE_OUTSIDE_WORKSPACE_AND_REPORTS')
        a.private_dir.mkdir(parents=True,exist_ok=False)
        auth=json.loads((ROOT/'qualification/authorization.json').read_text())
        if auth['revision']!=REV or auth['sha256']!=SHA or auth['size_bytes']!=SIZE or auth['file']!=NAME or auth['scope']!='EXPLICIT_USER_CLOUD_LAB_ONLY':raise VerificationError('AUTHORIZATION_MISMATCH')
        card=text_url(f'https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/raw/{REV}/README.md')
        if 'apache-2.0' not in card.lower():raise VerificationError('PINNED_LICENSE_DECLARATION_MISMATCH')
        (a.output/'MODEL-CARD.txt').write_text(card)
        meta=json.loads(text_url('https://huggingface.co/api/models/Qwen/Qwen3-0.6B',1048576))
        base_revision=meta['sha']
        if len(base_revision)!=40 or any(c not in '0123456789abcdef' for c in base_revision):raise VerificationError('INVALID_LICENSE_REVISION')
        license_url=f'https://huggingface.co/Qwen/Qwen3-0.6B/raw/{base_revision}/LICENSE'
        license=text_url(license_url)
        if 'Apache License' not in license or 'Version 2.0' not in license:raise VerificationError('LICENSE_TEXT_MISMATCH')
        (a.output/'LICENSE.txt').write_text(license)
        notices=[s['rfilename'] for s in meta.get('siblings',[]) if s.get('rfilename') in ('NOTICE','NOTICE.txt')]
        for name in notices:(a.output/(name+'.txt')).write_text(text_url(f'https://huggingface.co/Qwen/Qwen3-0.6B/raw/{base_revision}/{name}'))
        report.update(license='Apache-2.0',license_source=license_url,license_sha256=hashlib.sha256(license.encode()).hexdigest(),license_base_revision=base_revision,notice_files=notices,license_verified_before_transfer=True)
        pointer=text_url(f'https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/raw/{REV}/{NAME}',4096)
        if f'oid sha256:{SHA}' not in pointer or f'size {SIZE}' not in pointer:raise VerificationError('OFFICIAL_LFS_POINTER_MISMATCH')
        (a.output/'LFS-POINTER.txt').write_text(pointer)
        with open_url(URL) as r:
            final_host=urllib.parse.urlsplit(r.geturl()).hostname
            declared=r.headers.get('Content-Length')
            if declared is not None and int(declared)!=SIZE:raise VerificationError('CONTENT_LENGTH_MISMATCH')
            measured=copy_verified(r,a.private_dir/NAME,SIZE,SHA)
        report.update(status='PASS',source_url=URL,final_delivery_host=final_host,**measured)
    except Exception as e:
        report.update(status='FAIL',error_code=str(e) if isinstance(e,VerificationError) else type(e).__name__,loading_prohibited=True)
    (a.output/'download.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
    return 0 if report['status']=='PASS' else 1
if __name__=='__main__':raise SystemExit(main())
