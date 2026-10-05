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

if __name__=='__main__':
    failures=violations(ROOT/'app/src/main')+violations(ROOT/'app/build.gradle.kts')
    if failures: raise SystemExit('APK architecture boundary violations:\n'+'\n'.join(failures))
    print('OK: APK sources contain no root tuning, module mutations, Community/VIP routes or performance inheritance.')
