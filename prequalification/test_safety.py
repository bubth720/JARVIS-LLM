import json,unittest
from pathlib import Path
from android import validate_guest
class Safety(unittest.TestCase):
    def test_cloud_arm64_29(self):validate_guest(29,'arm64-v8a,armeabi-v7a','1',29)
    def test_cloud_arm64_35(self):validate_guest(35,'arm64-v8a','1',35)
    def test_real_device_refused(self):
        with self.assertRaises(ValueError):validate_guest(35,'arm64-v8a','0',35)
    def test_x86_not_substituted(self):
        with self.assertRaises(ValueError):validate_guest(35,'x86_64','1',35)
    def test_wrong_api_refused(self):
        with self.assertRaises(ValueError):validate_guest(34,'arm64-v8a','1',35)
    def test_similar_abi_refused(self):
        with self.assertRaises(ValueError):validate_guest(35,'fake-arm64-v8a','1',35)
    def test_model_not_authorized_or_downloaded(self):
        d=json.loads((Path(__file__).parent/'model.json').read_text())
        self.assertEqual(d['status'],'PROPOSED_AWAITING_USER_APPROVAL')
        self.assertFalse(d['weights_downloaded']);self.assertFalse(d['binary_sha256_recomputed'])
if __name__=='__main__':unittest.main()
