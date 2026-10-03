# Controlyst Universal Root Module & WebUI Daemon

This directory contains the standalone flashable root module for **Controlyst**.

## Downloadable Flashable ZIP
- **File**: `controlyst_root_module.zip`
- **Path**: `/downloadable_module/controlyst_root_module.zip`
- **Compatibility**:
  - **KernelSU** (v0.9.0+) & **KernelSU Next**
  - **APatch** (v10.0+)
  - **Magisk** (v24.0+)

---

## What Does This Module Do?

1. **Hardware /dev/uinput Driver Node Provisioning**:
   - Automatically grants secure, direct read/write access to `/dev/uinput` and `/dev/input/event*` at boot.
   - Enables hardware-level 1000 Hz gamepad injection with zero userspace dispatch overhead (< 0.42 ms input latency).
2. **Native WebUI Support**:
   - Integrates directly into KernelSU and APatch manager apps under the **Module WebUI** button.
   - Provides a 17-page capability-aware dashboard (CPU, GPU devfreq, ZRAM LZ4 compression, thermal safeguards, battery rules, and game profile linking).
3. **Safe Adaptive Performance Engine**:
   - Interacts exclusively with real kernel sysfs/procfs interfaces (`/sys/devices/system/cpu`, devfreq, `/sys/class/thermal`, etc.).
   - Zero hardcoded frequencies; uses dynamically detected device frequency tables with read-back verification.

---

## Installation Instructions

### Method A: KernelSU / APatch / Magisk Manager (GUI)
1. Download `controlyst_root_module.zip` to your Android device's storage.
2. Open **KernelSU Manager**, **APatch Manager**, or **Magisk Manager**.
3. Go to the **Modules** tab and tap **Install from storage**.
4. Select `controlyst_root_module.zip`.
5. Once the flashing log shows `Done`, reboot your device.
6. After reboot, tap the **WebUI** button next to Controlyst inside KernelSU/APatch to access the performance dashboard!

### Method B: Terminal / Root Shell (One-Liner)
If you have root access via ADB or Termux:
```bash
# For KernelSU / APatch / Magisk:
su -c "magisk --install-module controlyst_root_module.zip" 2>/dev/null || su -c "ksu module install controlyst_root_module.zip"
```

### Direct Folder Structure Inside ZIP
```text
controlyst_root_module.zip
├── module.prop
├── service.sh
├── post-fs-data.sh
├── system.prop
├── webroot/
│   └── index.html (17-page capability-based WebUI)
└── META-INF/com/google/android/
    ├── update-binary
    └── updater-script
```
