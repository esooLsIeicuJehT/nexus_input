# NEXUS INPUT 0.6.2 hardening

This revision tightens runtime and permission handling after the 0.6.x on-device tests.

## Shizuku / Sui

- Centralizes binder and permission state in `ShizukuAccess`.
- Accepts both root-backed UID 0 and ADB-backed UID 2000.
- Distinguishes READY, PERMISSION NEEDED, AUTHORIZATION BLOCKED and BINDER NOT AVAILABLE.
- Uses `Shizuku.shouldShowRequestPermissionRationale()` so a blocked authorization is not requested in a loop.
- Opens the Shizuku manager for manual repair when authorization is blocked.
- Does not require the Shizuku manager APK when a Sui binder is already alive.

## Runtime hardening

- Validates saved game profiles before arming the mapper.
- Restores foreground-package state from the accessibility root window when possible.
- Keeps the safe-launch grace period from 0.6.1.
- Auto/Compatibility backend order is Shizuku/Sui, then KernelSU, then Magisk, then limited Accessibility fallback.
- A failed Auto backend can move to the next real available backend and reports the failure.
- A forced per-profile backend never silently switches to another backend.
- Persistent multitouch mappings do not fall back to Accessibility because that backend cannot satisfy that contract.
- Geometry refresh is honored instead of reusing stale virtual-touchscreen geometry.

## Debugging

`SystemSelfCheck` is read-only. It reports app/device state, permissions, Shizuku state, backend availability, controller/calibration state, profile validation, package presence and runtime state. It does not inject input or modify settings.

## KernelSU companion

- Module updater requires a SHA-256 value and rejects a missing or mismatched checksum.
- Updates continue to use KernelSU's real `ksud module install <zip>` contract.

## Verification status

Source verification, shell syntax, WebUI JavaScript syntax, XML parsing, native-boundary checks and lightweight source sanity checks passed in the build workspace.

The exact 0.6.2 Android APK has not been compiled or run in the assistant environment because the Gradle wrapper cannot reach `services.gradle.org` there. The user's rooted Termux environment is the known working Android build environment and remains the required on-device verification target.
