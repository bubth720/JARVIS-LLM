import hashlib,io,json,tempfile,unittest
from pathlib import Path
from download import copy_verified,VerificationError,allowed_url
from package import stage
from admission import inspect
class Safety(unittest.TestCase):
    def test_exact_bytes_pass_independent_reread(self):
        with tempfile.TemporaryDirectory() as d:
            b=b'unit fixture only';r=copy_verified(io.BytesIO(b),Path(d)/'fixture',len(b),hashlib.sha256(b).hexdigest());self.assertEqual(r['postwrite_sha256'],hashlib.sha256(b).hexdigest())
    def test_size_overshoot_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(VerificationError):copy_verified(io.BytesIO(b'abcd'),Path(d)/'fixture',3,'0'*64)
    def test_truncated_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(VerificationError):copy_verified(io.BytesIO(b'ab'),Path(d)/'fixture',3,'0'*64)
    def test_wrong_hash_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(VerificationError):copy_verified(io.BytesIO(b'abc'),Path(d)/'fixture',3,'0'*64)
    def test_never_overwrite_existing_file(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'fixture';p.write_bytes(b'preserve')
            with self.assertRaises(FileExistsError):copy_verified(io.BytesIO(b'a'),p,1,'0'*64)
            self.assertEqual(p.read_bytes(),b'preserve')
    def test_redirect_https_publisher_only(self):
        self.assertTrue(allowed_url('https://us.aws.cdn.hf.co/file'))
        for u in ['http://huggingface.co/a','https://huggingface.co.evil.invalid/a','https://evil.invalid/a']:self.assertFalse(allowed_url(u))
    def test_weight_never_copied_even_with_text_extension(self):
        with tempfile.TemporaryDirectory() as d:
            s=Path(d)/'raw';s.mkdir();(s/'bad.json').write_bytes(b'GGUFunit fixture');t=Path(d)/'public'
            with self.assertRaises(ValueError):stage(s,t)
            self.assertFalse((t/'bad.json').exists());self.assertTrue((t/'SHA256SUMS').exists())
    def test_binary_extension_refused(self):
        with tempfile.TemporaryDirectory() as d:
            s=Path(d)/'raw';s.mkdir();(s/'bad.gguf').write_bytes(b'unit fixture');t=Path(d)/'public'
            with self.assertRaises(ValueError):stage(s,t)
            self.assertFalse((t/'bad.gguf').exists())
    def test_symlink_refused(self):
        with tempfile.TemporaryDirectory() as d:
            s=Path(d)/'raw';s.mkdir();(Path(d)/'secret').write_text('unit fixture');(s/'bad.log').symlink_to(Path(d)/'secret');t=Path(d)/'public'
            with self.assertRaises(ValueError):stage(s,t)
            self.assertFalse((t/'bad.log').exists())
    def test_only_utf8_reports_staged(self):
        with tempfile.TemporaryDirectory() as d:
            s=Path(d)/'raw';s.mkdir();(s/'result.json').write_text('{"status":"BLOCKED"}');t=Path(d)/'public'
            self.assertEqual(stage(s,t)['status'],'PASS');self.assertTrue((t/'result.json').exists())
    def test_admission_does_not_turn_download_consent_into_grant(self):
        r=inspect(Path(__file__).resolve().parents[1]);self.assertEqual(r['status'],'BLOCKED');self.assertFalse(r['generation_admitted']);self.assertFalse(r['fake_core_mandate_created']);self.assertIsNone(r['performance']['p95_ms'])
if __name__=='__main__':unittest.main()
