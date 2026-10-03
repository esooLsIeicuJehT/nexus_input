# Hardware & Privilege Verification Matrix

This document is a mandatory tracking structure for all backend controller modules, root-based input injection methods, and system-wide overlay features. Every module entry **must** explicitly record its verification status (`Tested` vs `Untested`), the verification environment, and known limitations.

---

## 1. Root & Non-Root Input Injection Modules

| Module Name | Backend Class | Verification Status | Verification Environment | Tested / Verified Features | Untested / Requires Hardware | Notes & Known Limitations |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Shizuku Bridge** | `ShizukuInjector.kt` | **[UNTESTED]** | Containerized Emulator (No live Shizuku binder daemon) | Binder ping check, permission state query | Live `rish` shell command execution, remote process IPC binding | Requires an active Shizuku service and wireless debugging pairing on a physical Android 11+ device. |
| **Magisk Root** | `MagiskInjector.kt` | **[UNTESTED]** | Containerized Emulator (No `su` binary / Magisk root) | Binary path checks (`/system/bin/su`, etc.) | `su -c` execution, event node event dispatch (`/dev/input/eventX`) | Requires a Magisk-rooted device with Superuser permission granted. |
| **KernelSU Root** | `KernelSUInjector.kt` | **[UNTESTED]** | Containerized Emulator (No KernelSU daemon) | Daemon path checks (`/data/adb/ksud`) | KernelSU userspace socket communication, `ksu -v` invocation | Requires a KernelSU-supported custom kernel and manager app. |
| **APatch Root** | `APatchInjector.kt` | **[UNTESTED]** | Containerized Emulator (No APatch daemon) | Daemon path checks (`/data/adb/apd`) | APatch kernel patch execution via `su` | Requires an APatch-patched boot image. |
| **Accessibility Fallback** | `AccessibilityInjector.kt` | **[TESTED]** | Android Emulator & Local JVM Tests | Gesture dispatch framework, coordinate scaling | Live touch injection on third-party protected games | Fully functional on standard Android runtime with user-granted Accessibility service permission. |

---

## 2. Crosshair & System Overlay Modules

| Module Name | Component / Service | Verification Status | Verification Environment | Tested / Verified Features | Untested / Requires Hardware | Notes & Known Limitations |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **WindowManager Overlay** | `CrosshairOverlayManager.kt` | **[TESTED]** | Android Emulator & JVM Unit Tests | WindowManager layout params, ComposeView lifecycle attachment, Canvas reticle rendering | Inter-app overlay visibility over secure/protected fullscreen game surfaces | Requires `SYSTEM_ALERT_WINDOW` ("Display over other apps") permission. |
| **Reticle Customization** | `CrosshairStudioScreen.kt` | **[TESTED]** | Local App UI & Compose Previews | Shape rendering (Dot, Cross, Circle, T, Chevron, Tri-Line), sliders, color palettes, preview canvas | Live rendering during heavy gameplay frame rates | Fully functional within local Jetpack Compose runtime. |
| **Touch Draggability** | `CrosshairOverlayManager.kt` | **[TESTED]** | Android Emulator | Pointer input drag gesture detection, window layout updates | High-velocity drag handling during rapid multi-touch gaming sessions | Touch events are processed in real-time by WindowManager layout updates. |

---

## 3. Compliance & Ground Rules
1. **No Silent Stubbing**: Modules marked **[UNTESTED]** must throw or log explicit diagnostic state if invoked without hardware support.
2. **Mandatory Updates**: Any developer or AI agent modifying injection or overlay code must update this matrix with the corresponding verification status before pull requests or builds.
