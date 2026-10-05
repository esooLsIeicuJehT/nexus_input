import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('boundary',Path(__file__).resolve().parents[1]/'check_architecture_boundary.py')
boundary=importlib.util.module_from_spec(spec);spec.loader.exec_module(boundary)

class ArchitectureTest(unittest.TestCase):
    def test_rejects_root_tuning_but_allows_mapping_and_readiness(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            path=root/'screen.kt'
            for bad in ['scaling_governor','/sys/class/thermal','/proc/sys/vm','inheritPerformanceProfile','ksud module install','"community"','"vip"']:
                path.write_text('val value="'+bad+'"')
                self.assertTrue(boundary.violations(root),bad)
            path.write_text('val status="KernelSU available; /dev/uinput ready"\nval name="NEXUS INPUT gamepad mapper"')
            self.assertEqual([],boundary.violations(root))
            path.write_text('text = "CONTROLYST"')
            self.assertTrue(boundary.violations(root))
            path.write_text('Text("Controlyst setup")')
            self.assertTrue(boundary.violations(root))
            path.write_text('val databaseName="controlyst_database"')
            self.assertEqual([],boundary.violations(root))
    def test_current_apk_obeys_boundary(self):
        self.assertEqual([],boundary.violations(boundary.ROOT/'app/src/main'))
