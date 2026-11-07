"""Build a fresh text-only evidence archive directory, separate from jars/classes/synthetic file fixtures."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    base=ROOT/'build/labadmission';out=base/'public';out.mkdir(exist_ok=True);assert not any(out.iterdir())
    for directory in ('kotlin-baseline','kotlin-candidate','reports'):
        source=base/directory
        for p in sorted(source.glob('*')):
            if not p.is_file():continue
            assert not p.is_symlink() and p.suffix in ('.json','.log','.txt') and p.stat().st_size<=2*1024**2
            b=p.read_bytes();assert not b.startswith(b'GGUF');b.decode('utf-8')
            dest=out/directory/p.name;dest.parent.mkdir(exist_ok=True);dest.write_bytes(b)
    files=[p for p in sorted(out.rglob('*')) if p.is_file()]
    (out/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(out).as_posix()+'\n' for p in files))
    print('TEXT_ONLY',len(files),'files; no weights, jars, classes or credentials')
if __name__=='__main__':main()
