#!/usr/bin/env python3
from pathlib import Path
import subprocess
import zipfile
import argparse
parser=argparse.ArgumentParser()
parser.add_argument("--signed",action="store_true")
args=parser.parse_args()
from package_release import properties, ROOT

version=properties(ROOT/'version.properties')
expected={'arm64-v8a','armeabi-v7a','x86','x86_64'}
for path in [ROOT/'app/build/outputs/apk/debug/app-debug.apk',ROOT/('app/build/outputs/apk/release/app-release.apk' if args.signed else 'app/build/outputs/apk/release/app-release-unsigned.apk')]:
    with zipfile.ZipFile(path) as archive:
        abis={name.split('/')[1] for name in archive.namelist() if name.startswith('lib/') and name.endswith('/libuinput_jni.so')}
        assert abis==expected, f'{path.name}: missing raw uinput ABI libraries: {expected-abis}'
    import os
    sdk=Path(os.environ['ANDROID_HOME'])
    aapt=sorted(sdk.glob('build-tools/*/aapt'),key=lambda p:[int(i) for i in p.parent.name.split('.') if i.isdigit()])[-1]
    badging=subprocess.check_output([str(aapt),'dump','badging',str(path)],text=True).splitlines()[0]
    for token in ["name='com.inputmapper.platform'",f"versionCode='{version['versionCode']}'",f"versionName='{version['versionName']}'"]:
        assert token in badging, f'Unexpected APK identity: {badging}'
    print(f'{path.name}: existing application ID, shared v1 version and all 4 JNI ABIs verified')
with zipfile.ZipFile(ROOT/'app/build/outputs/bundle/release/app-release.aab') as archive:
    abis={name.split('/')[2] for name in archive.namelist() if name.startswith('base/lib/') and name.endswith('/libuinput_jni.so')}
    assert abis==expected, 'AAB is missing native ABIs'
print('AAB native packaging verified. Hardware verification is not implied; signatures are checked separately.')
