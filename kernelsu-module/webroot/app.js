const log = document.getElementById('log');

function execRoot(command) {
  return new Promise((resolve, reject) => {
    if (!window.ksu || typeof window.ksu.exec !== 'function') {
      reject(new Error('KernelSU WebUI API is unavailable'));
      return;
    }
    const callback = `gp_exec_${Date.now()}_${Math.random().toString(16).slice(2)}`;
    window[callback] = (errno, stdout, stderr) => {
      delete window[callback];
      resolve({ errno, stdout, stderr });
    };
    try {
      // This two-argument bridge form is retained because it is verified working on the user's
      // KernelSU 3.3.0 WebUI. Do not replace it with an unverified wrapper signature here.
      window.ksu.exec(command, callback);
    } catch (error) {
      delete window[callback];
      reject(error);
    }
  });
}

function state(id, text, kind) {
  const el = document.getElementById(id);
  el.textContent = text;
  el.className = kind || '';
}

function section(lines, beginKey, endKey) {
  const start = lines.indexOf(beginKey);
  const end = lines.indexOf(endKey);
  if (start < 0 || end <= start) return 'unavailable';
  return lines.slice(start + 1, end).join('\n').trim() || 'none';
}

async function refresh() {
  log.textContent = 'Reading live root state…';
  const cmd = String.raw`
PACKAGE=com.inputmapper.platform
if pm path "$PACKAGE" >/dev/null 2>&1; then echo "APK=installed"; else echo "APK=missing"; fi
if [ -e /dev/uinput ]; then echo "UINPUT=$(ls -lZ /dev/uinput 2>&1)"; else echo "UINPUT=missing"; fi
if [ -e /dev/uhid ]; then echo "UHID=$(ls -lZ /dev/uhid 2>&1)"; else echo "UHID=missing"; fi
echo "SELINUX=$(getenforce 2>/dev/null || echo unknown)"
echo "PROCESSES=$(ps -AZ 2>/dev/null | grep -E 'com\.inputmapper\.platform|nexus\.input|gamepad\.pro' | tr '\n' ';' || true)"
echo 'RUNTIME_STATUS_BEGIN'
if [ -r /data/adb/gamepad-pro/runtime-status.txt ]; then
  cat /data/adb/gamepad-pro/runtime-status.txt
else
  echo 'No APK runtime state has been published yet'
fi
echo 'RUNTIME_STATUS_END'
echo 'DEVICES_BEGIN'
for event in /sys/class/input/event*; do
  [ -d "$event" ] || continue
  n=$(cat "$event/device/name" 2>/dev/null)
  v=$(cat "$event/device/id/vendor" 2>/dev/null)
  p=$(cat "$event/device/id/product" 2>/dev/null)
  echo "$(basename "$event")  $v:$p  $n"
done
echo 'DEVICES_END'
echo 'MODULE_CONFIG_BEGIN'
if command -v ksud >/dev/null 2>&1; then
  KSU_MODULE=gamepad.pro.root ksud module config list 2>&1 || echo 'ksud module config list failed'
else
  echo 'ksud command not found in WebUI shell PATH'
fi
echo 'MODULE_CONFIG_END'
echo 'BOOT_STATUS_BEGIN'
if [ -r /data/adb/gamepad-pro/boot-status.txt ]; then
  cat /data/adb/gamepad-pro/boot-status.txt
else
  echo 'boot status file is not readable yet'
fi
echo 'BOOT_STATUS_END'
`;
  try {
    const result = await execRoot(cmd);
    log.textContent = `exit=${result.errno}\n${result.stderr || result.stdout}`;
    if (result.errno !== 0) return;
    const lines = result.stdout.split('\n');
    const value = key => (lines.find(l => l.startsWith(`${key}=`)) || '').slice(key.length + 1);
    const apk = value('APK');
    const uinput = value('UINPUT');
    const uhid = value('UHID');
    const processes = value('PROCESSES');
    state('apk', apk || 'unknown', apk === 'installed' ? 'ok' : 'bad');
    state('uinput', uinput || 'unknown', uinput && uinput !== 'missing' ? 'ok' : 'bad');
    state('uhid', uhid || 'unknown', uhid && uhid !== 'missing' ? 'ok' : 'warn');
    state('selinux', value('SELINUX') || 'unknown', '');
    state('processes', processes || 'none', processes ? 'ok' : 'warn');
    document.getElementById('runtimeStatus').textContent = section(lines, 'RUNTIME_STATUS_BEGIN', 'RUNTIME_STATUS_END');
    document.getElementById('devices').textContent = section(lines, 'DEVICES_BEGIN', 'DEVICES_END');
    document.getElementById('moduleConfig').textContent = section(lines, 'MODULE_CONFIG_BEGIN', 'MODULE_CONFIG_END');
    document.getElementById('bootStatus').textContent = section(lines, 'BOOT_STATUS_BEGIN', 'BOOT_STATUS_END');
  } catch (error) {
    log.textContent = String(error);
  }
}

async function launch() {
  try {
    const r = await execRoot('am start -n com.inputmapper.platform/com.inputmapper.platform.ui.SplashActivity');
    log.textContent = `exit=${r.errno}\n${r.stdout}\n${r.stderr}`;
  } catch (error) {
    log.textContent = String(error);
  }
}

document.getElementById('refresh').addEventListener('click', refresh);
document.getElementById('launch').addEventListener('click', launch);
refresh();

const MODULE_ID = 'gamepad.pro.root';
const MODULE_DIR = `/data/adb/modules/${MODULE_ID}`;
let githubUpdateAvailable = false;

function parseKv(text) {
  const out = {};
  String(text || '').split(/\r?\n/).forEach(line => {
    const i = line.indexOf('=');
    if (i > 0) out[line.slice(0, i)] = line.slice(i + 1);
  });
  return out;
}

async function readInstalledVersion() {
  try {
    const r = await execRoot(`sed -n 's/^version=//p' ${MODULE_DIR}/module.prop | head -n 1`);
    document.getElementById('installedVersion').textContent = r.stdout.trim() || 'unknown';
  } catch (error) {
    document.getElementById('installedVersion').textContent = 'unavailable';
  }
}

async function checkGithubUpdate() {
  const status = document.getElementById('updateStatus');
  const install = document.getElementById('installUpdate');
  status.textContent = 'Checking GitHub…';
  install.disabled = true;
  githubUpdateAvailable = false;
  try {
    const r = await execRoot(`${MODULE_DIR}/update.sh check`);
    const kv = parseKv(r.stdout);
    document.getElementById('remoteVersion').textContent = kv.REMOTE_VERSION || 'unavailable';
    if (kv.STATE === 'AVAILABLE') {
      githubUpdateAvailable = true;
      install.disabled = false;
      status.textContent = `Update ${kv.REMOTE_VERSION} is available. The ZIP will be SHA-256 verified before KernelSU stages it.`;
    } else if (kv.STATE === 'UP_TO_DATE') {
      status.textContent = 'NEXUS INPUT KernelSU Companion is up to date.';
    } else {
      status.textContent = kv.MESSAGE || r.stderr || r.stdout || `Update check failed (exit ${r.errno})`;
    }
  } catch (error) {
    status.textContent = String(error);
  }
}

async function installGithubUpdate() {
  if (!githubUpdateAvailable) return;
  if (!window.confirm('Download the verified NEXUS INPUT KernelSU update from GitHub and stage it with KernelSU? A reboot will be required.')) return;

  const status = document.getElementById('updateStatus');
  const install = document.getElementById('installUpdate');
  install.disabled = true;
  status.textContent = 'Downloading, verifying and staging update…';
  try {
    const r = await execRoot(`${MODULE_DIR}/update.sh install`);
    const kv = parseKv(r.stdout);
    if (kv.STATE === 'INSTALLED_PENDING_REBOOT') {
      githubUpdateAvailable = false;
      status.textContent = kv.MESSAGE || 'Update staged. Reboot to activate it.';
    } else {
      install.disabled = false;
      status.textContent = kv.MESSAGE || r.stderr || r.stdout || `Update install failed (exit ${r.errno})`;
    }
    log.textContent = `exit=${r.errno}\n${r.stdout}\n${r.stderr}`;
  } catch (error) {
    install.disabled = false;
    status.textContent = String(error);
  }
}

document.getElementById('checkUpdate').addEventListener('click', checkGithubUpdate);
document.getElementById('installUpdate').addEventListener('click', installGithubUpdate);
readInstalledVersion();
