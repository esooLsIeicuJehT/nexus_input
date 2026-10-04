# Current Validation Report

## Implemented in this revision

- Replaced the failing persistent `su` text-stream root transport with libsu 6.0.0 `RootService` Binder IPC.
- Added `IRootInputService.aidl` and `RootInputService`.
- RootService loads the existing JNI uinput library only in the uid-0 process.
- KernelSU and Magisk injectors now share the Binder-backed root uinput implementation.
- Removed the old `RootUinputMain` / `RootProcessFactory` transport instead of keeping a duplicate fallback.
- Root status now reports real uid, JNI-load state, `/dev/uinput`, `/dev/uhid`, SELinux context, and uinput creation state.
- Added Android-visible input-device inventory with VID/PID and axis min/max/flat/fuzz/resolution data.
- Reworked the hardware dashboard to show live backend state, connected input devices, root-engine health, and explicit operation results.
- Added a KernelSU companion module with WebUI, boot diagnostics, input-device inventory, and APK launcher.
- Added a one-command Termux build script that compiles/validates the ARM64 JNI library and verifies APK packaging.
- Preserved the automated native-boundary guard: C++ remains raw uinput only, with no KernelSU/Magisk/Shizuku/backend policy.

## Locally verified in this workspace

- KernelSU module shell scripts pass `sh -n` syntax checks.
- KernelSU WebUI JavaScript passes Node syntax checking.
- Android XML files parse successfully.
- `scripts/check_native_boundary.py` passes against the current `uinput_jni.cpp`.
- No old `RootUinputMain` or `RootProcessFactory` references remain in `app/src`.
- KernelSU module ZIP packages with the documented module root layout.

## NOT verified here

This workspace does not contain a complete Android SDK/Gradle dependency cache and has no attached Android device. Therefore the following are **not claimed as tested**:

- full Android Gradle compile after adding libsu 6.0.0,
- APK install/start on the Motorola,
- KernelSU root grant and RootService binding,
- JNI load in the actual root service process,
- `/dev/uinput` create/inject/destroy from the phone's live KernelSU SELinux context,
- module WebUI rendering in the installed KernelSU Manager version,
- `/dev/uhid` behavior,
- real controller capture/mapping/gameplay.

The next real-device acceptance point is a single APK build/install followed by the dashboard's root-engine health and center-tap checks. If that passes, Phase 0 privilege/output transport is considered accepted and work moves to the controller capture + mapping engine.
