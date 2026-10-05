import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from package_release import package, FILES, ROOT, properties

class PackagingTest(unittest.TestCase):
    def test_reproducible_zip_exact_identity_and_required_permissions(self):
        with tempfile.TemporaryDirectory() as directory:
            out=Path(directory)
            archive=package(out)
            first=archive.read_bytes()
            self.assertEqual(first,package(out).read_bytes())
            manifest=json.loads((out/'update.json').read_text())
            self.assertEqual(hashlib.sha256(first).hexdigest(),manifest['sha256'])
            self.assertEqual(properties(ROOT/'version.properties')['versionName'],manifest['version'])
            with zipfile.ZipFile(archive) as zipin:
                self.assertEqual(set(FILES),set(zipin.namelist()))
                self.assertIn(b'id=gamepad.pro.root',zipin.read('module.prop'))
                for path in FILES:
                    self.assertEqual(0o755 if path.endswith('.sh') else 0o644,zipin.getinfo(path).external_attr>>16 & 0o777)
