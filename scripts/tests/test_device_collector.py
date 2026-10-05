import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('device_collector', Path(__file__).resolve().parents[1] / 'device_test_v1.py')
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)


class DeviceCollectorTest(unittest.TestCase):
    def test_real_exit_stdout_stderr_are_preserved(self):
        result = collector.observe([sys.executable, '-c', "import sys; print('actual output'); print('actual error',file=sys.stderr); sys.exit(7)"])
        self.assertEqual(7, result['exit_code'])
        self.assertIn('actual output', result['stdout'])
        self.assertIn('actual error', result['stderr'])
        self.assertFalse(collector.observed_without_error(result))

    def test_real_timeout_preserves_observed_output(self):
        result = collector.observe([sys.executable, '-c', "import time; print('before timeout',flush=True); time.sleep(10)"], timeout=0.1)
        self.assertTrue(result['timed_out'])
        self.assertIsNone(result['exit_code'])
        self.assertIn('before timeout', result['stdout'])
        self.assertFalse(collector.observed_without_error(result))

    def test_truncated_real_output_is_not_complete(self):
        result = collector.observe([sys.executable, '-c', "print('x'*100)"], limit=10)
        self.assertTrue(result['output_truncated'])
        self.assertEqual(10, len(result['stdout']))
        self.assertFalse(collector.observed_without_error(result))

    def test_missing_adb_records_failures_and_never_passes_manual_cases(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'observations.json'
            code = collector.main(['--serial', 'explicit-test-serial', '--adb', str(Path(directory) / 'missing-adb'), '--output', str(report)])
            self.assertEqual(1, code)
            data = json.loads(report.read_text())
            self.assertFalse(data['collection_complete_without_command_errors'])
            self.assertTrue(all(observation['error'] for observation in data['observations']))
            self.assertTrue(all(case['status'] == 'NOT_RUN' for case in data['manual_cases'].values()))

    def test_every_device_command_uses_explicit_serial_and_no_mutating_command(self):
        commands = collector.commands('/tools/adb', 'chosen-serial', True)
        self.assertEqual(['/tools/adb', 'version'], commands[0])
        self.assertTrue(all(command[1:3] == ['-s', 'chosen-serial'] for command in commands[1:]))
        self.assertFalse(any(word in ('install', 'uninstall', 'reboot', 'push') for command in commands for word in command))

    def test_invalid_export_is_an_observable_import_error(self):
        # /bin/false is a real failing executable, not a simulated Android transport.
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'invalid.json'
            source.write_text('{invalid')
            report = Path(directory) / 'observations.json'
            self.assertEqual(1, collector.main(['--serial', 'explicit-test-serial', '--adb', '/bin/false', '--diagnostics', str(source), '--output', str(report)]))
            self.assertIn('diagnostics_import_error', json.loads(report.read_text()))
