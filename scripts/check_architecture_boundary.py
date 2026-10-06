#!/usr/bin/env python3
"""Guard the APK/root-WebUI ownership boundary in checked-in production sources."""
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
FORBIDDEN=re.compile(r'cpufreq|devfreq|scaling_governor|scaling_(?:min|max)_freq|/sys/class/thermal|/sys/block/zram|/proc/sys/|memory_compact|inheritPerformanceProfile|ksud\s+module\s+(?:install|config|uninstall)|Performance-Tuning|"(?:community|vip)"',re.I)
LEGACY_BRANDING=re.compile(r'(?:\bText\(\s*|\b(?:text|title|label)\s*=\s*)"[^"\n]*controlyst',re.I)
UNUSED_CLOUD=re.compile(r'libs\.(?:plugins\.secrets|plugins\.google\.services|firebase|googleid|androidx\.credentials)|com\.google\.firebase|assets\..*kernelsu-module')

def violations(root):
    found=[]
    for source in ([root] if root.is_file() else sorted(root.rglob('*'))):
        if source.is_file() and source.suffix in {'.kt','.kts','.cpp','.h','.aidl','.xml'}:
            for number,line in enumerate(source.read_text().splitlines(),1):
                if FORBIDDEN.search(line) or LEGACY_BRANDING.search(line) or UNUSED_CLOUD.search(line):
                    found.append(f'{source.relative_to(root.parent if root.is_file() else root)}:{number}: {line.strip()}')
    return found

def kernelsu_inputmanager_violations():
    found=[]
    platform=ROOT/'app/src/main/java/com/inputmapper/platform/root/KernelSUInjector.kt'
    client=ROOT/'app/src/main/java/com/inputmapper/platform/root/RootInputManagerInjector.kt'
    adapter=ROOT/'app/src/main/java/com/example/injector/KernelSUInjector.kt'
    platform_text=platform.read_text()
    client_text=client.read_text()
    adapter_text=adapter.read_text()

    if 'RootInputManagerInjector' not in platform_text or 'RootUinputInjector' in platform_text:
        found.append('KernelSU platform backend must delegate exclusively to RootInputManagerInjector')
    for token in ('.touchDown(', '.touchMove(', '.touchUp(', '.create('):
        if token in client_text:
            found.append(f'KernelSU InputManager client must not call legacy uinput RPC {token}')
    for token in ('DisplayMetrics', 'WindowManager', 'virtual touchscreen', 'KernelSU/uinput'):
        if token in adapter_text:
            found.append(f'KernelSU app adapter retains stale uinput/geometry dependency: {token}')
    return found

if __name__=='__main__':
    failures=violations(ROOT/'app/src/main')+violations(ROOT/'app/build.gradle.kts')+kernelsu_inputmanager_violations()
    if failures: raise SystemExit('APK architecture boundary violations:\n'+'\n'.join(failures))
    print('OK: APK sources contain no root tuning/module ownership leaks; KernelSU touch remains isolated on the InputManager client.')
