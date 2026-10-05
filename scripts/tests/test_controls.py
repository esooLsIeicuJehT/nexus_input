from pathlib import Path
import subprocess
import tempfile
import unittest

LIB=Path(__file__).resolve().parents[2]/'kernelsu-module/control-lib.sh'

def run(function,*arguments):
    return subprocess.run(['sh','-c','. "$1"; shift; "$@"','nexus-test',str(LIB),function,*map(str,arguments)],text=True,capture_output=True)

class ControlTest(unittest.TestCase):
    def test_write_is_successful_only_after_actual_file_readback(self):
        with tempfile.TemporaryDirectory() as directory:
            node=Path(directory)/'node'
            node.write_text('old\n')
            result=run('nexus_write_checked',node,'new')
            self.assertEqual(0,result.returncode,result.stdout+result.stderr)
            self.assertEqual('new\n',node.read_text())
            self.assertIn('BEFORE=old',result.stdout)
            self.assertIn('READ_BACK=new',result.stdout)
            failure=run('nexus_write_checked',Path(directory)/'absent','new')
            self.assertNotEqual(0,failure.returncode)
            self.assertIn('ERROR',failure.stdout)
    def test_readback_mismatch_cannot_be_reported_as_applied(self):
        result=run('nexus_write_checked','/dev/null','requested')
        self.assertNotEqual(0,result.returncode)
        self.assertIn('read-back differs',result.stdout)
        self.assertNotIn('APPLIED=',result.stdout)
    def test_control_inputs_cannot_escape_fixed_sysfs_paths(self):
        for value in ['','../0','0; touch /tmp/injected','-1']:
            self.assertNotEqual(0,run('nexus_number',value).returncode)
        for value in ['','performance; reboot','../performance']:
            self.assertNotEqual(0,run('nexus_governor',value).returncode)
        self.assertEqual(0,run('nexus_member','schedutil','performance schedutil powersave').returncode)
        self.assertNotEqual(0,run('nexus_member','invented','performance schedutil').returncode)
        self.assertNotEqual(0,run('nexus_swappiness','101').returncode)
        self.assertNotEqual(0,run('nexus_gpu_governor','../../proc/sys','performance').returncode)
