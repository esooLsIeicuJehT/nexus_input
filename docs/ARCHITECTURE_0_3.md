# Gamepad Pro 0.3 architecture notes

## Verified foundation on the target Android 16 / KernelSU device

- KernelSU/libsu RootService runs with uid 0.
- JNI can create `/dev/uinput` devices.
- Android InputReader sees the virtual touchscreen and injected taps reach a real UI target.
- Shizuku/Sui UserService works in root mode (uid 0) and framework InputManager injection reaches a real UI target.
- The Google Stadia Controller rev. A is exposed as GAMEPAD + JOYSTICK + KEYBOARD with VID:PID `18d1:9400` and real axis/button events.
- `/dev/uhid` exists on the target device, but Gamepad Pro has not yet opened/validated a UHID device from its root process.

## Data plane vs control plane

High-rate input must not travel through a WebView or shell command per event.

Data plane:

```
controller -> Accessibility/evdev capture -> mapping engine -> uinput/UHID/InputManager
```

Control plane:

```
APK UI -> Binder RootService
KernelSU WebUI -> KernelSU shell/config/diagnostics
optional LSPosed module app <-> LSPosed service/remote preferences <-> hooked process
```

The current 0.3 build adds real global Android 14+ joystick capture for calibration through `AccessibilityServiceInfo.setMotionEventSources(SOURCE_JOYSTICK)`. Android documents that requested motion-event sources are not delivered to the rest of the system, so capture is enabled only during explicit calibration and is disabled when that activity pauses.

Reference: https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#setMotionEventSources(int)
Reference: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onMotionEvent(android.view.MotionEvent)

## KernelSU module/WebUI contract

KernelSU officially supports:

- `service.sh`, `boot-completed.sh`, `sepolicy.rule`, and `initrc/` in module roots.
- `webroot/index.html` WebUIs with a root-capable JS bridge.
- persistent and temporary module configuration through `ksud module config` in module scripts.

References:
- https://kernelsu.org/guide/module.html
- https://kernelsu.org/guide/module-webui.html
- https://kernelsu.org/guide/module-config.html

For Gamepad Pro, WebUI remains control/diagnostics only. It must not call shell commands for every stick or mouse movement. A future persistent native daemon may expose a low-latency IPC channel, but no daemon/socket is shipped until its Android/SELinux contract is implemented and verified on the target device.

## APK <-> KernelSU communication

Current verified path:

```
APK -> libsu RootService -> Binder/AIDL -> JNI uinput
```

This is already reliable and replaces the old long-lived `su` stdin/stdout protocol.

KernelSU module configuration is suitable for low-frequency persistent settings such as autostart/backend preference after the daemon exists. Direct APK access to `ksud module config` from the RootService environment has NOT yet been verified, so this build does not pretend that bridge is complete.

## LSPosed plan

LSPosed is optional, not required for basic mapping.

The current modern Xposed API uses:

- `META-INF/xposed/java_init.list`
- `META-INF/xposed/native_init.list` when needed
- `META-INF/xposed/scope.list`
- `META-INF/xposed/module.prop`
- an entry implementing the modern `XposedModule` API
- framework service communication / remote preferences for module-app <-> hooked-process settings

Reference: https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API

Gamepad Pro does not ship fake Xposed metadata yet. Before adding the module we need to verify the exact LSPosed/libxposed API version installed on the Android 16 target. The Xposed layer should be used only for features that genuinely require in-process hooks; root uinput and Shizuku remain independent.

## 0.3 implementation

- Product home screen.
- Explicit global controller capture through the enabled AccessibilityService.
- Real controller calibration recorder.
- Persistent app-private controller profiles with observed key codes, scan codes, and axis center/min/max samples.
- KernelSU WebUI now distinguishes Gamepad Pro process diagnostics, shows boot probe data, and attempts to display official KernelSU module-config output without claiming it is available when `ksud` is missing from the WebUI shell PATH.
