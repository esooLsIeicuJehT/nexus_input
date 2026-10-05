#!/usr/bin/env python3
"""Collect read-only Android observations; never infer mapping or hardware PASS."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess

APPLICATION_ID = 'com.inputmapper.platform'
MANUAL_CASES = (
    'UPGRADE-01', 'STORAGE-01', 'STORAGE-02', 'UI-01', 'SCREENSHOT-01',
    'SCREENSHOT-02', 'CONTROLLER-01', 'CALIBRATION-01', 'CALIBRATION-02',
    'ROOT-01', 'ROOT-02', 'SHIZUKU-01', 'SHIZUKU-02', 'SHIZUKU-03',
    'INPUT-01', 'INPUT-02', 'INPUT-03', 'INPUT-04', 'INPUT-05',
    'PANIC-01', 'PANIC-02', 'OVERLAY-01', 'OVERLAY-02', 'FPS-01',
    'ACCESSIBILITY-01', 'APATCH-01', 'WEBUI-01', 'WEBUI-02',
    'WEBUI-03', 'UPDATE-01', 'UPDATE-02', 'DIAGNOSTICS-01',
)


def decoded(value):
    if value is None:
        return ''
    return value.decode('utf-8', errors='replace') if isinstance(value, bytes) else value


def observe(argv, timeout=30, limit=1_048_576):
    """Record the real exit code/output or an explicit launch/timeout failure."""
    record = dict(argv=list(argv), exit_code=None, timed_out=False, error=None)
    try:
        completed = subprocess.run(argv, capture_output=True, text=True, timeout=timeout, check=False)
        record.update(exit_code=completed.returncode, stdout=completed.stdout, stderr=completed.stderr)
    except subprocess.TimeoutExpired as failure:
        record.update(timed_out=True, error='Command exceeded its timeout',
                      stdout=decoded(failure.stdout), stderr=decoded(failure.stderr))
    except (OSError, ValueError) as failure:
        record.update(error=f'{type(failure).__name__}: {failure}', stdout='', stderr='')
    record['output_truncated'] = any(len(record[field]) > limit for field in ('stdout', 'stderr'))
    for field in ('stdout', 'stderr'):
        record[field] = record[field][:limit]
    return record


def observed_without_error(record):
    return (record['exit_code'] == 0 and not record['timed_out']
            and not record['output_truncated'] and record['error'] is None)


def commands(adb, serial, root_readiness=False):
    prefix = [adb, '-s', serial]
    result = [[adb, 'version'], prefix + ['get-state']]
    for prop in ('ro.product.manufacturer', 'ro.product.model', 'ro.product.device',
                 'ro.build.version.sdk', 'ro.build.version.release', 'ro.product.cpu.abilist'):
        result.append(prefix + ['shell', 'getprop', prop])
    result.extend(prefix + ['shell'] + arguments for arguments in (
        ['wm', 'size'], ['wm', 'density'], ['dumpsys', 'input'],
        ['dumpsys', 'package', APPLICATION_ID],
        ['dumpsys', 'package', 'moe.shizuku.privileged.api'],
        ['settings', 'get', 'secure', 'enabled_accessibility_services'],
        ['logcat', '-d', '-t', '2000', '-v', 'threadtime',
         'NexusAccessibility:V', 'NexusMappingService:V', 'NexusPanic:V',
         'NexusModule:V', 'NexusFrames:V', 'NexusInput:V', 'NexusOverlay:V',
         'NexusCrosshair:V', 'NexusKernelSU:V', 'NexusShizuku:V',
         'NexusAPatch:V', 'NexusMagisk:V', 'NexusPairing:V', '*:S'],
    ))
    if root_readiness:
        # These inspect readiness only. No device creation, injection or sysfs writes.
        result.append(prefix + ['shell', 'su', '-c',
            'id && ls -lZ /dev/uinput && cat /data/adb/modules/gamepad.pro.root/module.prop'])
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True, help='Exact adb device serial; never auto-select a device')
    parser.add_argument('--adb', default='adb', help='adb executable path')
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--timeout', type=int, default=30, choices=range(1, 61), metavar='1..60')
    parser.add_argument('--root-readiness', action='store_true', help='Also perform the fixed read-only su readiness probe')
    parser.add_argument('--diagnostics', type=Path, help='Optional JSON exported from APK System > release diagnostics')
    arguments = parser.parse_args(argv)
    observations = [observe(command, arguments.timeout)
                    for command in commands(arguments.adb, arguments.serial, arguments.root_readiness)]
    report = dict(application_id=APPLICATION_ID, serial=arguments.serial,
                  collected_at_utc=datetime.now(timezone.utc).isoformat(), observations=observations,
                  hardware_verification='NOT_ESTABLISHED_BY_THIS_COLLECTOR',
                  manual_cases={case: dict(status='NOT_RUN', evidence=None) for case in MANUAL_CASES})
    complete = all(observed_without_error(observation) for observation in observations)
    if arguments.diagnostics:
        try:
            diagnostics = json.loads(arguments.diagnostics.read_text())
            if not isinstance(diagnostics, dict) or diagnostics.get('app') != APPLICATION_ID:
                raise ValueError('Diagnostic JSON must identify the Nexus application package')
            report['apk_diagnostics'] = diagnostics
        except (OSError, ValueError) as failure:
            report['diagnostics_import_error'] = f'{type(failure).__name__}: {failure}'
            complete = False
    report['collection_complete_without_command_errors'] = complete
    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    arguments.output.write_text(json.dumps(report, indent=2) + '\n')
    print(f'Observation report: {arguments.output}; manual hardware cases remain NOT_RUN')
    return 0 if complete else 1


if __name__ == '__main__':
    raise SystemExit(main())
