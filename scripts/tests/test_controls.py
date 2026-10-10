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
        for value in ['../../proc/sys','.','..']:
            self.assertNotEqual(0,run('nexus_gpu_governor',value,'performance').returncode)

    def test_final_interval_mismatch_restores_original_pair(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            policy=root/'policy0';policy.mkdir()
            for name,value in {'scaling_min_freq':'100','scaling_max_freq':'1000',
                'scaling_available_frequencies':'100 500 1000 1500'}.items():
                (policy/name).write_text(value+'\n')
            # Redirect only the fixed sysfs prefix to fixture nodes. A mock driver
            # changes max after accepting min, exercising the actual final-pair check.
            lib=root/'control-lib.sh'
            lib.write_text(LIB.read_text().replace('/sys/devices/system/cpu/cpufreq/',str(root)+'/'))
            command=''' . "$1"
            nexus_write_checked() {
                printf '%s\n' "$2" > "$1"
                case "$1" in */scaling_min_freq) printf '999\n' > "${1%/*}/scaling_max_freq";; esac
                return 0
            }
            nexus_cpu_frequencies 0 500 1500
            '''
            result=subprocess.run(['sh','-c',command,'test',str(lib)],text=True,capture_output=True)
            self.assertNotEqual(0,result.returncode,result.stdout)
            self.assertIn('ROLLBACK_VERIFIED=CPU_INTERVAL',result.stdout)
            self.assertEqual('100', (policy/'scaling_min_freq').read_text().strip())
            self.assertEqual('1000', (policy/'scaling_max_freq').read_text().strip())
