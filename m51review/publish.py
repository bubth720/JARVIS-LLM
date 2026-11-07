"""Text evidence only. No binaries, ephemeral fixtures, credentials or model bytes are selected."""
import hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    out=ROOT/'build/m51review-public';out.mkdir(exist_ok=True);assert not any(out.iterdir())
    for folder in ('build/m51review','build/labadmission/kotlin-candidate'):
        for p in sorted((ROOT/folder).glob('*')):
            if not p.is_file():continue
            assert not p.is_symlink() and p.suffix in ('.json','.log','.txt') and p.stat().st_size<=2*1024**2
            data=p.read_bytes();assert not data.startswith(b'GGUF');data.decode('utf-8')
            assert (b'-----BEGIN '+b'PRIVATE KEY-----') not in data
            name=Path(folder).name+'/'+p.name;dest=out/name;dest.parent.mkdir(exist_ok=True);dest.write_bytes(data)
    files=[p for p in sorted(out.rglob('*')) if p.is_file()]
    (out/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(out).as_posix()+'\n' for p in files))
    print('TEXT_ONLY',len(files))
if __name__=='__main__':main()
