from pathlib import Path
import hashlib
import subprocess
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[2]
LIB=ROOT/'kernelsu-module/update-lib.sh'
class UpdaterTest(unittest.TestCase):
    def call(self, function, *args):
        # Actual updater functions run locally; no installer or fake download success is substituted.
        return subprocess.run(['sh','-c','. "$1"; shift; find_busybox() { return 1; }; '+function+' "$@"','test',str(LIB),*args],text=True,capture_output=True)
    def test_manifest_requires_valid_digest_version_and_repository_asset(self):
        url='https://github.com/esooLsIeicuJehT/nexus_input/releases/download/v1.0.0/NEXUS_INPUT.zip'
        self.assertEqual(0,self.call('validate_update','1.0.0','1000',url,'a'*64).returncode)
        for version,code,badurl,digest in [('1.0','1000',url,''),('1.0','0',url,'a'*64),('bad version','1000',url,'a'*64),('1','1000','http://github.com/x.zip','a'*64),('1','1000','https://evil.example/module.zip','a'*64)]:
            self.assertNotEqual(0,self.call('validate_update',version,code,badurl,digest).returncode)
    def test_actual_sha256_checks_match_mismatch_and_missing_digest(self):
        with tempfile.TemporaryDirectory() as directory:
            file=Path(directory)/'module.zip';file.write_bytes(b'fixture archive bytes')
            digest=hashlib.sha256(file.read_bytes()).hexdigest()
            self.assertEqual(0,self.call('verify_sha256',digest,str(file)).returncode)
            self.assertNotEqual(0,self.call('verify_sha256','a'*64,str(file)).returncode)
            self.assertNotEqual(0,self.call('verify_sha256','',str(file)).returncode)
