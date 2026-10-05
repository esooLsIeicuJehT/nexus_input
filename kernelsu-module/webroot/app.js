const log = document.getElementById('log');

const APK_PACKAGE = 'com.inputmapper.platform';
const APK_COMPONENT = `${APK_PACKAGE}/com.example.MainActivity`;
const MODULE_ID = 'gamepad.pro.root';
const MODULE_DIR = `/data/adb/modules/${MODULE_ID}`;
let githubUpdateAvailable = false;

function execRoot(command) {
  return new Promise((resolve, reject) => {
    if (!window.ksu || typeof window.ksu.exec !== 'function') {
      reject(new Error('KernelSU WebUI API is unavailable'));
      return;
    }
    const callback = `nx_exec_${Date.now()}_${Math.random().toString(16).slice(2)}`;
    const timer = setTimeout(() => {
      delete window[callback];
      reject(new Error('KernelSU shell request timed out after 180 seconds'));
    }, 180000);
    window[callback] = (errno, stdout, stderr) => {
      clearTimeout(timer);
      delete window[callback];
      const code = Number(errno);
      if (!Number.isInteger(code)) { reject(new Error('KernelSU returned an invalid exit status')); return; }
      resolve({ errno: code, stdout: String(stdout || ''), stderr: String(stderr || '') });
    };
    try {
      // KernelSU's WebUI bridge requires command, serialized options, and callback name.
      window.ksu.exec(command, '{}', callback);
    } catch (error) {
      clearTimeout(timer);
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

function setBusy(button, busy, busyLabel) {
  if (!button) return;
  if (!button.dataset.label) button.dataset.label = button.textContent;
  button.disabled = busy;
  button.textContent = busy ? busyLabel : button.dataset.label;
}

async function refresh() {
  const refreshButton = document.getElementById('refresh');
  setBusy(refreshButton, true, 'Refreshing…');
  log.textContent = 'Reading live root state…';

  const cmd = String.raw`
PACKAGE=${APK_PACKAGE}
if pm path "$PACKAGE" 2>/dev/null | grep -q '^package:/'; then echo "APK=installed"; else echo "APK=missing"; fi
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
    if (result.errno !== 0) {
      ['apk','uinput','uhid','selinux','processes'].forEach(id => state(id, 'diagnostic failed', 'bad'));
      ['runtimeStatus','devices','moduleConfig','bootStatus'].forEach(id => state(id, 'Unavailable: diagnostic failed', 'bad'));
      return;
    }

    const lines = result.stdout.split('\n');
    const value = key => (lines.find(l => l.startsWith(`${key}=`)) || '').slice(key.length + 1);
    const apk = value('APK');
    const uinput = value('UINPUT');
    const uhid = value('UHID');
    const processes = value('PROCESSES');

    state('apk', apk === 'installed' ? 'Installed' : 'Missing', apk === 'installed' ? 'ok' : 'bad');
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
    ['apk','uinput','uhid','selinux','processes','runtimeStatus','devices','moduleConfig','bootStatus'].forEach(id => state(id, 'Unavailable: WebUI API error', 'bad'));
  } finally {
    setBusy(refreshButton, false, 'Refreshing…');
  }
}

async function launch() {
  const launchButton = document.getElementById('launch');
  setBusy(launchButton, true, 'Launching…');
  try {
    const r = await execRoot(`am start -n ${APK_COMPONENT}`);
    log.textContent = `exit=${r.errno}\n${r.stdout}\n${r.stderr}`;
    if (r.errno !== 0) {
      throw new Error(r.stderr || r.stdout || `Android activity launch failed with exit ${r.errno}`);
    }
  } catch (error) {
    log.textContent = String(error);
  } finally {
    setBusy(launchButton, false, 'Launching…');
  }
}

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
    if (r.errno !== 0 || !r.stdout.trim()) throw new Error(r.stderr || `Module version read failed (exit ${r.errno})`);
    document.getElementById('installedVersion').textContent = r.stdout.trim();
  } catch (error) {
    document.getElementById('installedVersion').textContent = `Unavailable: ${error.message}`;
    log.textContent = String(error);
  }
}

async function checkGithubUpdate() {
  const status = document.getElementById('updateStatus');
  const check = document.getElementById('checkUpdate');
  const install = document.getElementById('installUpdate');
  status.textContent = 'Checking GitHub…';
  install.disabled = true;
  githubUpdateAvailable = false;
  setBusy(check, true, 'Checking…');

  try {
    const r = await execRoot(`${MODULE_DIR}/update.sh check`);
    const kv = parseKv(r.stdout);
    document.getElementById('remoteVersion').textContent = kv.REMOTE_VERSION || 'unavailable';
    if (r.errno === 10 && kv.STATE === 'AVAILABLE') {
      githubUpdateAvailable = true;
      install.disabled = false;
      status.textContent = `Update ${kv.REMOTE_VERSION} is available. The ZIP will be SHA-256 verified before KernelSU stages it.`;
    } else if (r.errno === 0 && kv.STATE === 'UP_TO_DATE') {
      status.textContent = 'NEXUS INPUT KernelSU Companion is up to date.';
    } else {
      status.textContent = kv.MESSAGE || r.stderr || r.stdout || `Update check failed (exit ${r.errno})`;
    }
  } catch (error) {
    status.textContent = String(error);
  } finally {
    setBusy(check, false, 'Checking…');
  }
}

async function installGithubUpdate() {
  if (!githubUpdateAvailable) return;
  if (!window.confirm('Download the verified NEXUS INPUT KernelSU update from GitHub and stage it with KernelSU? A reboot will be required.')) return;

  const status = document.getElementById('updateStatus');
  const install = document.getElementById('installUpdate');
  setBusy(install, true, 'Installing…');
  status.textContent = 'Downloading, verifying and staging update…';

  try {
    const r = await execRoot(`${MODULE_DIR}/update.sh install`);
    const kv = parseKv(r.stdout);
    if (r.errno === 0 && kv.STATE === 'INSTALLED_PENDING_REBOOT') {
      githubUpdateAvailable = false;
      status.textContent = kv.MESSAGE || 'Update staged. Reboot to activate it.';
    } else {
      status.textContent = kv.MESSAGE || r.stderr || r.stdout || `Update install failed (exit ${r.errno})`;
    }
    log.textContent = `exit=${r.errno}\n${r.stdout}\n${r.stderr}`;
  } catch (error) {
    status.textContent = String(error);
  } finally {
    install.disabled = !githubUpdateAvailable;
    install.textContent = install.dataset.label || 'Install update';
  }
}

document.getElementById('refresh').addEventListener('click', refresh);
document.getElementById('launch').addEventListener('click', launch);
document.getElementById('checkUpdate').addEventListener('click', checkGithubUpdate);
document.getElementById('installUpdate').addEventListener('click', installGithubUpdate);

readInstalledVersion();
refresh();

let cpuPolicies = [];
let devfreqDevices = [];
let capabilitiesReady = false;
const controlButtons = ['applyCpuGovernor','applyCpuFrequencies','applyDevfreqGovernor','applySwappiness'];
function shellQuote(value) { return "'" + String(value).replace(/'/g, "'\\''") + "'"; }
function parseCapabilityGroups(text, key) {
  const groups = [];
  let current;
  String(text).split(/\r?\n/).forEach(line => {
    const split = line.indexOf('=');
    if (split < 1) return;
    const name = line.slice(0, split), value = line.slice(split + 1);
    if (name === key) { current = { id: value }; groups.push(current); }
    else if (current) current[name] = value;
  });
  return groups;
}
function setOptions(id, values, selected) {
  const element = document.getElementById(id);
  element.replaceChildren();
  values.forEach(value => {
    const option = document.createElement('option');
    option.value = value; option.textContent = value; element.appendChild(option);
  });
  element.disabled = values.length === 0;
  if (values.includes(selected)) element.value = selected;
}
function exposedWords(text, pattern) { return String(text || '').trim().split(/\s+/).filter(word => pattern.test(word)); }
function selectCpuPolicy() {
  const policy = cpuPolicies.find(value => value.id === document.getElementById('cpuPolicy').value);
  const governors = exposedWords(policy?.scaling_available_governors, /^[a-zA-Z0-9_-]+$/);
  const frequencies = exposedWords(policy?.scaling_available_frequencies, /^[0-9]+$/);
  setOptions('cpuGovernor', governors, policy?.scaling_governor);
  setOptions('cpuMinimum', frequencies, policy?.scaling_min_freq);
  setOptions('cpuMaximum', frequencies, policy?.scaling_max_freq);
  document.getElementById('applyCpuGovernor').disabled = !capabilitiesReady || governors.length === 0;
  document.getElementById('applyCpuFrequencies').disabled = !capabilitiesReady || frequencies.length === 0;
}
function selectDevfreqDevice() {
  const device = devfreqDevices.find(value => value.id === document.getElementById('devfreqDevice').value);
  const governors = exposedWords(device?.available_governors, /^[a-zA-Z0-9_-]+$/);
  setOptions('devfreqGovernor', governors, device?.governor);
  document.getElementById('applyDevfreqGovernor').disabled = !capabilitiesReady || governors.length === 0;
}
async function refreshCapabilities() {
  const button = document.getElementById('refreshCapabilities');
  capabilitiesReady = false; controlButtons.forEach(id => document.getElementById(id).disabled = true);
  setBusy(button, true, 'Reading…');
  try {
    const result = await execRoot(`${MODULE_DIR}/control.sh capabilities`);
    if (result.errno !== 0) throw new Error(result.stderr || result.stdout || `Capability query failed: exit ${result.errno}`);
    const lines = result.stdout.split('\n');
    ['CPU','DEVFREQ','THERMAL','MEMORY','BATTERY','PRESETS'].forEach(name => {
      if (!lines.includes(`${name}_BEGIN`) || !lines.includes(`${name}_END`)) throw new Error(`Missing capability section ${name}`);
    });
    const cpu = section(lines,'CPU_BEGIN','CPU_END'), devfreq = section(lines,'DEVFREQ_BEGIN','DEVFREQ_END');
    state('cpuCapabilities',cpu); state('devfreqCapabilities',devfreq);
    state('thermalCapabilities',section(lines,'THERMAL_BEGIN','THERMAL_END'));
    const memory = section(lines,'MEMORY_BEGIN','MEMORY_END'); state('memoryCapabilities',memory);
    state('batteryCapabilities',section(lines,'BATTERY_BEGIN','BATTERY_END'));
    state('presetCapabilities',section(lines,'PRESETS_BEGIN','PRESETS_END'));
    cpuPolicies = parseCapabilityGroups(cpu,'POLICY').filter(value => /^[0-9]+$/.test(value.id));
    devfreqDevices = parseCapabilityGroups(devfreq,'DEVFREQ').filter(value => /^[a-zA-Z0-9_.:-]+$/.test(value.id));
    capabilitiesReady = true;
    setOptions('cpuPolicy',cpuPolicies.map(value => value.id));selectCpuPolicy();
    setOptions('devfreqDevice',devfreqDevices.map(value => value.id));selectDevfreqDevice();
    const swappiness = parseKv(memory).swappiness;
    document.getElementById('swappiness').value = /^[0-9]+$/.test(swappiness || '') ? swappiness : '';
    document.getElementById('swappiness').disabled = !/^[0-9]+$/.test(swappiness || '');
    document.getElementById('applySwappiness').disabled = document.getElementById('swappiness').disabled;
  } catch (error) {
    cpuPolicies = [];devfreqDevices = [];
    ['cpuPolicy','cpuGovernor','cpuMinimum','cpuMaximum','devfreqDevice','devfreqGovernor'].forEach(id => setOptions(id,[]));
    document.getElementById('swappiness').disabled = true;
    ['cpuCapabilities','devfreqCapabilities','thermalCapabilities','memoryCapabilities','batteryCapabilities','presetCapabilities'].forEach(id => state(id,`Unavailable: ${error.message}`,'bad'));
    state('controlStatus',`Capability query failed: ${error.message}`,'bad');
  } finally { setBusy(button,false,'Reading…'); }
}
async function applyRootControl(operation, argumentsList) {
  if (!capabilitiesReady) { state('controlStatus','Read actual device capabilities before requesting a change.','bad');return; }
  if (argumentsList.some(value => !String(value).trim())) { state('controlStatus','Select exposed device values first.','bad');return; }
  if (!window.confirm(`Apply ${operation}: ${argumentsList.join(' / ')}? The module checks battery safeguards and verifies actual read-back.`)) return;
  controlButtons.forEach(id => document.getElementById(id).disabled = true);
  try {
    const result = await execRoot(`${MODULE_DIR}/control.sh ${shellQuote(operation)} ${argumentsList.map(shellQuote).join(' ')}`);
    state('controlStatus',`exit=${result.errno}\n${result.stdout}\n${result.stderr}`,result.errno === 0 ? 'ok' : 'bad');
    if (result.errno === 0 && (!result.stdout.includes('READ_BACK=') || !result.stdout.includes('CONTROL_EXIT=0'))) {
      state('controlStatus','Change did not provide verified read-back. Read current device values and root logs.','bad');
    }
  } catch (error) { state('controlStatus',`Root change failed: ${error.message}`,'bad'); }
  finally { await refreshCapabilities(); }
}
async function readControlLog() {
  try {
    const result = await execRoot(`${MODULE_DIR}/control.sh logs`);
    state('controlStatus',`exit=${result.errno}\n${result.stdout}\n${result.stderr}`,result.errno === 0 ? '' : 'bad');
  } catch(error) { state('controlStatus',`Root log unavailable: ${error.message}`,'bad'); }
}
document.getElementById('refreshCapabilities').addEventListener('click',refreshCapabilities);
document.getElementById('cpuPolicy').addEventListener('change',selectCpuPolicy);
document.getElementById('devfreqDevice').addEventListener('change',selectDevfreqDevice);
document.getElementById('applyCpuGovernor').addEventListener('click',() => applyRootControl('cpu-governor',['cpuPolicy','cpuGovernor'].map(id => document.getElementById(id).value)));
document.getElementById('applyCpuFrequencies').addEventListener('click',() => applyRootControl('cpu-frequencies',['cpuPolicy','cpuMinimum','cpuMaximum'].map(id => document.getElementById(id).value)));
document.getElementById('applyDevfreqGovernor').addEventListener('click',() => applyRootControl('devfreq-governor',['devfreqDevice','devfreqGovernor'].map(id => document.getElementById(id).value)));
document.getElementById('applySwappiness').addEventListener('click',() => applyRootControl('swappiness',[document.getElementById('swappiness').value]));
document.getElementById('readControlLog').addEventListener('click',readControlLog);
refreshCapabilities();
