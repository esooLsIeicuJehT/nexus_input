#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

python3 scripts/check_native_boundary.py
python3 - <<'PY'
import xml.etree.ElementTree as ET
for path in [
    'app/src/main/AndroidManifest.xml',
    'app/src/main/res/values/strings.xml',
    'app/src/main/res/xml/accessibility_service_config.xml',
]:
    ET.parse(path)
print('OK: Android XML parses')
PY

python3 - <<'PY'
from pathlib import Path
m = Path('app/src/main/AndroidManifest.xml').read_text()
required = [
    'rikka.shizuku.ShizukuProvider',
    '${applicationId}.shizuku',
    'android.permission.POST_NOTIFICATIONS',
    '.setup.SetupActivity',
    '.ui.MainActivity',
    '.ui.ControllerCalibrationActivity',
]
missing = [x for x in required if x not in m]
if missing:
    raise SystemExit('ERROR: onboarding/Shizuku manifest contract missing: ' + ', '.join(missing))
print('OK: onboarding + Shizuku provider manifest contract')
PY

for f in kernelsu-module/customize.sh kernelsu-module/service.sh kernelsu-module/action.sh kernelsu-module/update.sh; do
  sh -n "$f"
done
echo "OK: KernelSU shell syntax"

if command -v node >/dev/null 2>&1; then
  node --check kernelsu-module/webroot/app.js
  echo "OK: KernelSU WebUI JavaScript syntax"
fi

if grep -R "RootUinputMain\|RootProcessFactory\|SuRootProcessFactory" -n app/src; then
  echo "ERROR: obsolete root text-stream transport still referenced" >&2
  exit 1
fi
echo "OK: obsolete root text-stream transport removed"

python3 - <<'PYSHIZUKU'
from pathlib import Path
text = Path('app/src/main/java/com/inputmapper/platform/shizuku/ShizukuInjector.kt').read_text()
if 'uid == 0 || uid == 2000' not in text:
    raise SystemExit('ERROR: Shizuku UserService must accept both root UID 0 and ADB shell UID 2000')
manifest = Path('app/src/main/AndroidManifest.xml').read_text()
if '.ui.ControllerLiveActivity' not in manifest:
    raise SystemExit('ERROR: live controller input activity missing from manifest')
print('OK: Shizuku root/shell UID contract + live controller activity')
PYSHIZUKU

python3 - <<'PYLIFECYCLE'
from pathlib import Path
for path in [
    Path("app/src/main/java/com/inputmapper/platform/ui/Phase0DiagnosticsActivity.kt"),
    Path("app/src/main/java/com/inputmapper/platform/setup/SetupActivity.kt"),
]:
    text = path.read_text()
    ui_ready = text.find("setContentView(")
    sticky = text.find("Shizuku.addBinderReceivedListenerSticky")
    if ui_ready < 0 or sticky < 0:
        raise SystemExit(f"ERROR: lifecycle guard could not inspect {path}")
    if sticky < ui_ready:
        raise SystemExit(f"ERROR: sticky Shizuku listener registered before UI init in {path}")
print("OK: sticky Shizuku listeners register after UI initialization")
PYLIFECYCLE


python3 - <<'PYMAPPER'
from pathlib import Path
service = Path('app/src/main/java/com/inputmapper/platform/accessibility/MapperAccessibilityService.kt').read_text()
config = Path('app/src/main/res/xml/accessibility_service_config.xml').read_text()
manifest = Path('app/src/main/AndroidManifest.xml').read_text()
profile = Path('app/src/main/java/com/inputmapper/platform/profile/ControllerProfileStore.kt').read_text()
cal = Path('app/src/main/java/com/inputmapper/platform/ui/ControllerCalibrationActivity.kt').read_text()
checks = {
    'AccessibilityService joystick motion capture': 'motionEventSources' in service and 'SOURCE_JOYSTICK' in service,
    'filter-key capability': 'canRequestFilterKeyEvents="true"' in config and 'flagRequestFilterKeyEvents' in config,
    'calibration activity manifest': '.ui.ControllerCalibrationActivity' in manifest,
    'persistent profile store': 'gamepad_pro_controller_profiles' in profile,
    'explicit calibration lifecycle': 'setControllerCaptureEnabled(true' in cal and 'setControllerCaptureEnabled(false' in cal,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('ERROR: mapper-core contract missing: ' + ', '.join(failed))
print('OK: mapper-core global capture + calibration/profile contract')
PYMAPPER

if find app/src/main/jniLibs -type f -name '*.so' -print -quit 2>/dev/null | grep -q .; then
  echo "ERROR: source tree contains a prebuilt JNI library. Rebuild through scripts/build-termux.sh or normal NDK." >&2
  exit 1
fi
echo "OK: no stale prebuilt JNI binary is shipped in source"

python3 - <<'PY'
from pathlib import Path
p = Path('kernelsu-module/module.prop')
b = p.read_bytes()
if b'\r\n' in b:
    raise SystemExit('ERROR: module.prop contains CRLF')
required = {'id','name','version','versionCode','author','description'}
keys = {line.split('=',1)[0] for line in p.read_text().splitlines() if '=' in line}
missing = sorted(required - keys)
if missing:
    raise SystemExit('ERROR: module.prop missing: ' + ', '.join(missing))
print('OK: KernelSU module metadata')
PY

python3 - <<'PYNEXUS'
from pathlib import Path
manifest = Path('app/src/main/AndroidManifest.xml').read_text()
main = Path('app/src/main/java/com/inputmapper/platform/ui/MainActivity.kt').read_text()
service = Path('app/src/main/java/com/inputmapper/platform/accessibility/MapperAccessibilityService.kt').read_text()
selector = Path('app/src/main/java/com/inputmapper/platform/core/BackendSelector.kt').read_text()
input_injector = Path('app/src/main/java/com/inputmapper/platform/core/InputInjector.kt').read_text()
shizuku = Path('app/src/main/java/com/inputmapper/platform/shizuku/ShizukuInjector.kt').read_text()
game_store = Path('app/src/main/java/com/inputmapper/platform/game/GameProfileStore.kt').read_text()
profiles = Path('app/src/main/java/com/inputmapper/platform/ui/ProfilesActivity.kt').read_text()
ui = Path('app/src/main/java/com/inputmapper/platform/ui/NexusUi.kt').read_text()
splash = Path('app/src/main/java/com/inputmapper/platform/ui/SplashActivity.kt').read_text()
checks = {
    'NEXUS splash launcher': '.ui.SplashActivity' in manifest and 'FIT_CENTER' in splash,
    'system bar inset handling': 'WindowInsets.Type.systemBars()' in ui,
    'persistent game profile store': 'nexus_game_profiles_v1' in game_store and 'touchMappings' in game_store and 'stickMappings' in game_store,
    'installed app profile picker': 'queryIntentActivities' in profiles and 'Edit Layout over Game' in profiles,
    'accessibility overlay editor': 'TYPE_ACCESSIBILITY_OVERLAY' in service and 'captureButton' in service,
    'persistent runtime mapping': 'enableMapping' in service and 'findByPackage' in service and 'onAccessibilityEvent' in service,
    'button TAP/HOLD runtime': 'TouchActionType.TAP' in service and 'TouchActionType.HOLD' in service,
    'stick runtime': 'VIRTUAL_JOYSTICK' in service and 'CAMERA_DRAG' in service and 'ControllerNormalizer' in service,
    'continuous touch injector contract': 'beginTouch' in input_injector and 'moveTouch' in input_injector and 'endTouch' in input_injector,
    'Shizuku multi-pointer state': 'ACTION_POINTER_DOWN' in shizuku and 'ACTION_POINTER_UP' in shizuku and 'activeTouches' in shizuku,
    'KernelSU-first backend order': selector.find('BackendKind.KERNEL_SU') < selector.find('BackendKind.SHIZUKU'),
    'main dashboard uses profiles': 'ProfilesActivity::class.java' in main and 'MapperLabActivity::class.java' not in main,
    'least privilege overlay model': 'android.permission.SYSTEM_ALERT_WINDOW' not in manifest and 'android.permission.PACKAGE_USAGE_STATS' not in manifest,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('ERROR: NEXUS 0.5 MVP contract missing: ' + ', '.join(failed))
print('OK: NEXUS 0.5 persistent profiles + overlay + multitouch mapper contract')
PYNEXUS


python3 - <<'PYRUNTIME06'
from pathlib import Path
service = Path('app/src/main/java/com/inputmapper/platform/accessibility/MapperAccessibilityService.kt').read_text()
manifest = Path('app/src/main/AndroidManifest.xml').read_text()
profiles = Path('app/src/main/java/com/inputmapper/platform/ui/ProfilesActivity.kt').read_text()
screenshot = Path('app/src/main/java/com/inputmapper/platform/ui/ScreenshotMapperActivity.kt').read_text()
checks = {
    'D-pad HAT synthesis': 'AXIS_HAT_X' in service and 'KEYCODE_DPAD_LEFT' in service and 'synthesizeDpadFromHat' in service,
    'runtime activation fix': 'assumeTargetForeground' in service and 'activateProfile(profile' in service and 'lastForegroundPackage' in service,
    'geometry-aware injector recreation': 'injectorWidth' in service and 'injectorHeight' in service and 'onConfigurationChanged' in service,
    'quick overlay bubble': 'class QuickBubble' in service and 'TYPE_ACCESSIBILITY_OVERLAY' in service and 'Profiles / Screenshot Mapper' in service,
    'runtime failure surfacing': 'reportRuntimeFailure' in service and 'Mapper error' in service,
    'screenshot mapper manifest': '.ui.ScreenshotMapperActivity' in manifest,
    'screenshot mapper profile entry': 'Screenshot Mapper' in profiles,
    'screenshot mapper persistent profile writes': 'saveProfile()' in screenshot and 'ACTION_OPEN_DOCUMENT' in screenshot and 'takePersistableUriPermission' in screenshot,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('ERROR: NEXUS 0.6 runtime contract missing: ' + ', '.join(failed))
print('OK: NEXUS 0.6 D-pad + runtime activation + bubble + screenshot mapper contract')
PYRUNTIME06

python3 - <<'PYSAFE061'
from pathlib import Path
service = Path('app/src/main/java/com/inputmapper/platform/accessibility/MapperAccessibilityService.kt').read_text()
profiles = Path('app/src/main/java/com/inputmapper/platform/ui/ProfilesActivity.kt').read_text()
checks = {
    'startup grace': 'GAME_STARTUP_GRACE_MS = 3500L' in service and 'scheduleStableActivation' in service,
    'no immediate game bubble': 'Waiting for ${profile.displayName} startup • mapper not injecting yet' in service,
    'compatibility-first runtime backend': 'listOf(BackendKind.SHIZUKU, BackendKind.KERNEL_SU' in service,
    'bubble only after ready': 'BUBBLE_DELAY_MS' in service and 'runtimeReady && runtimeProfile?.profileId' in service,
    'game launch avoids forced new task': 'startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))' not in profiles,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('ERROR: NEXUS 0.6.1 safe-launch contract missing: ' + ', '.join(failed))
print('OK: NEXUS 0.6.1 safe-launch grace + compatibility runtime contract')
PYSAFE061


python3 - <<'PYHARDEN062'
from pathlib import Path
setup = Path('app/src/main/java/com/inputmapper/platform/setup/SetupActivity.kt').read_text()
access = Path('app/src/main/java/com/inputmapper/platform/shizuku/ShizukuAccess.kt').read_text()
priv = Path('app/src/main/java/com/inputmapper/platform/privilege/PrivilegeDetector.kt').read_text()
service = Path('app/src/main/java/com/inputmapper/platform/accessibility/MapperAccessibilityService.kt').read_text()
profiles = Path('app/src/main/java/com/inputmapper/platform/ui/ProfilesActivity.kt').read_text()
diag = Path('app/src/main/java/com/inputmapper/platform/ui/Phase0DiagnosticsActivity.kt').read_text()
selfcheck = Path('app/src/main/java/com/inputmapper/platform/debug/SystemSelfCheck.kt').read_text()
validator = Path('app/src/main/java/com/inputmapper/platform/debug/GameProfileValidator.kt').read_text()
checks = {
    'documented Shizuku rationale handling': 'shouldShowRequestPermissionRationale' in access and 'permissionBlocked' in setup,
    'Shizuku manager repair path': 'ShizukuAccess.openManager' in setup and 'Open Shizuku manager' in diag,
    'Sui binder does not require manager APK': 'if (!state.binderAlive)' in priv and 'managerInstalled' in priv,
    'auto backend connection cascade': 'for (candidate in candidates)' in service and 'BackendKind.SHIZUKU, BackendKind.KERNEL_SU' in service,
    'runtime alternate-backend recovery': 'Recovering from $failedKind failure' in service and 'excludedKinds = setOf(failedKind)' in service,
    'foreground restore after service recreation': 'refreshForegroundPackageFromRootWindow' in service and 'service restore' in service,
    'profile validation before runtime': 'GameProfileValidator.validate(profile)' in service and 'duplicate button bindings' not in validator,
    'per-profile backend selector': 'Auto / Compatibility' in profiles and 'BackendKind.KERNEL_SU.name' in profiles,
    'read-only full self check': 'class SystemSelfCheck' in selfcheck and 'Run full read-only self-check' in diag,
    'self-check clipboard export': 'setPrimaryClip' in diag,
    'inset-safe setup shell': 'NexusUi.page(this)' in setup,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('ERROR: NEXUS 0.6.2 hardening contract missing: ' + ', '.join(failed))
print('OK: NEXUS 0.6.2 Shizuku repair + backend failover + self-check hardening contract')
PYHARDEN062

python3 - <<'PYSUSPICIOUS'
from pathlib import Path
import re
roots = [Path('app/src/main/java'), Path('app/src/main/cpp')]
patterns = [
    re.compile(r'\bTODO\b', re.I),
    re.compile(r'\bFIXME\b', re.I),
    re.compile(r'dummy\s+success', re.I),
    re.compile(r'fake\s+success', re.I),
    re.compile(r'placeholder\s+success', re.I),
]
hits = []
for root in roots:
    for path in root.rglob('*'):
        if not path.is_file():
            continue
        text = path.read_text(errors='ignore')
        for pattern in patterns:
            if pattern.search(text):
                hits.append(f'{path}:{pattern.pattern}')
if hits:
    raise SystemExit('ERROR: suspicious unfinished markers found: ' + ', '.join(hits))
print('OK: no TODO/FIXME/fake-success markers in runtime source')
PYSUSPICIOUS
