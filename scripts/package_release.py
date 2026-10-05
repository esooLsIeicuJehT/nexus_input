#!/usr/bin/env python3
"""Package the canonical KernelSU companion and verify shared release identity."""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FILES = (
    'module.prop','customize.sh','service.sh','action.sh','update.sh','update-lib.sh','control.sh','control-lib.sh','skip_mount',
    'webroot/index.html','webroot/bridge-guard.js','webroot/app.js','webroot/style.css'
)

def properties(path):
    return dict(line.split('=',1) for line in path.read_text().splitlines() if '=' in line and not line.startswith('#'))

def verify_identity(version, module, metadata):
    assert module['id'] == 'gamepad.pro.root', 'Existing module identity must be preserved'
    assert module['version'] == version['versionName'] and module['versionCode'] == version['versionCode'], 'Android and module versions differ'
    assert metadata['name'] == 'NEXUS INPUT', 'Inherited project branding remains'
    assert metadata['versionName'] == version['versionName'] and metadata['versionCode'] == int(version['versionCode']), 'Project metadata and release versions differ'
    assert not metadata['majorCapabilities'], 'No server capability is implemented in this mapping-only product'
    assert int(version['versionCode']) > 600, 'Upgrade versionCode must exceed preserved 0.6.2 lineage'

def package(output):
    version = properties(ROOT/'version.properties')
    module = properties(ROOT/'kernelsu-module/module.prop')
    verify_identity(version,module,json.loads((ROOT/'metadata.json').read_text()))
    output.mkdir(parents=True,exist_ok=True)
    archive = output/f"NEXUS_INPUT-KernelSU-Companion-v{version['versionName']}.zip"
    with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as zipout:
        for path in sorted(FILES):
            info=zipfile.ZipInfo(path,date_time=(2026,1,1,0,0,0))
            info.create_system=3
            info.external_attr=(0o100755 if path.endswith('.sh') else 0o100644)<<16
            zipout.writestr(info,(ROOT/'kernelsu-module'/path).read_bytes(),compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
    digest=hashlib.sha256(archive.read_bytes()).hexdigest()
    (output/'SHA256SUMS').write_text(f'{digest}  {archive.name}\n')
    # This manifest is staged with the artifact. The public main manifest changes only after a real release is published.
    (output/'update.json').write_text(json.dumps(dict(version=version['versionName'],versionCode=int(version['versionCode']),
        zipUrl=f"https://github.com/esooLsIeicuJehT/nexus_input/releases/download/v{version['versionName']}/{archive.name}",
        sha256=digest,changelog='https://raw.githubusercontent.com/esooLsIeicuJehT/nexus_input/main/CHANGELOG.md'),indent=2)+'\n')
    return archive

if __name__=='__main__':
    print(package(ROOT/'dist'))
