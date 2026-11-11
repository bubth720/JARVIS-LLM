#!/usr/bin/env python3
"""A02 Linux sealed-memory prototype tests. No model weights, no JNI admission."""
import errno,fcntl,hashlib,os,tempfile,unittest
from pathlib import Path

REQUIRED=fcntl.F_SEAL_WRITE|fcntl.F_SEAL_GROW|fcntl.F_SEAL_SHRINK|fcntl.F_SEAL_SEAL

def sealed_snapshot(source: Path, expected_sha: str, expected_size: int):
    # Linux-only proof of sealing primitives. Native consumer must eventually
    # receive this exact FD, never reopen the original pathname.
    fd=os.memfd_create("jarvis-a02-snapshot",os.MFD_CLOEXEC|os.MFD_ALLOW_SEALING)
    try:
        digest=hashlib.sha256()
        with source.open("rb") as stream:
            count=0
            while True:
                data=stream.read(65536)
                if not data: break
                count+=len(data)
                if count>expected_size: raise ValueError("A02_SIZE_REFUSED")
                offset=0
                while offset<len(data):
                    written=os.write(fd,data[offset:])
                    if written<=0: raise OSError("A02_SHORT_WRITE")
                    offset+=written
                digest.update(data)
        if count!=expected_size or digest.hexdigest()!=expected_sha:
            raise ValueError("A02_DIGEST_REFUSED")
        if os.fstat(fd).st_size!=expected_size:
            raise ValueError("A02_SNAPSHOT_SIZE_REFUSED")
        fcntl.fcntl(fd,fcntl.F_ADD_SEALS,REQUIRED)
        if fcntl.fcntl(fd,fcntl.F_GET_SEALS)&REQUIRED!=REQUIRED:
            raise ValueError("A02_SEALS_REFUSED")
        # Rehash the exact sealed bytes the native consumer will see.
        sealed_digest=hashlib.sha256()
        pos=0
        while pos<expected_size:
            block=os.pread(fd,min(65536,expected_size-pos),pos)
            if not block: raise ValueError("A02_SEALED_READ_REFUSED")
            sealed_digest.update(block)
            pos+=len(block)
        if sealed_digest.hexdigest()!=expected_sha:
            raise ValueError("A02_SEALED_DIGEST_REFUSED")
        os.lseek(fd,0,os.SEEK_SET)
        return fd
    except BaseException:
        os.close(fd)
        raise

class A02Adversarial(unittest.TestCase):
    def test_sealed_bytes_survive_original_replacement(self):
        with tempfile.TemporaryDirectory() as root:
            source=Path(root)/"model.fixture"
            source.write_bytes(b"original-fixture")
            fd=sealed_snapshot(source,hashlib.sha256(source.read_bytes()).hexdigest(),len(source.read_bytes()))
            try:
                source.unlink()
                source.write_bytes(b"ATTACKER-FIXTURE")
                self.assertEqual(os.pread(fd,16,0),b"original-fixture")
                with self.assertRaises(OSError): os.pwrite(fd,b"X",0)
                with self.assertRaises(OSError): os.ftruncate(fd,0)
            finally: os.close(fd)
    def test_wrong_digest_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            p=Path(root)/"fixture";p.write_bytes(b"1234")
            with self.assertRaisesRegex(ValueError,"A02_DIGEST_REFUSED"):
                sealed_snapshot(p,"0"*64,4)
    def test_size_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            p=Path(root)/"fixture";p.write_bytes(b"1234")
            with self.assertRaisesRegex(ValueError,"A02_SIZE_REFUSED"):
                sealed_snapshot(p,hashlib.sha256(b"1234").hexdigest(),3)
    def test_no_sealing_flag_is_not_accepted(self):
        fd=os.memfd_create("unsealed",os.MFD_CLOEXEC)
        try:
            with self.assertRaises(OSError): fcntl.fcntl(fd,fcntl.F_ADD_SEALS,REQUIRED)
        finally: os.close(fd)

if __name__=="__main__":
    unittest.main(verbosity=2)
