"""Provision only reviewed compiler/JUnit dependencies, never a model or SDK; SHA256 required."""
import argparse,hashlib,json,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser();p.add_argument('--cache',type=Path,required=True);a=p.parse_args()
    for item in json.loads((ROOT/'labadmission/JARS.json').read_text()):
        g,n,v,h=[item[k] for k in ('group','artifact','version','sha256')]
        path=a.cache/g/n/v/h/(n+'-'+v+'.jar');path.parent.mkdir(parents=True,exist_ok=True)
        if not path.exists():
            url='https://repo.maven.apache.org/maven2/'+g.replace('.','/')+'/'+n+'/'+v+'/'+path.name
            with urllib.request.urlopen(url,timeout=60) as r:path.write_bytes(r.read(80*1024*1024+1))
        assert path.stat().st_size<=80*1024*1024 and hashlib.sha256(path.read_bytes()).hexdigest()==h,n
        print('VERIFIED',path.name,h)
if __name__=='__main__':main()
