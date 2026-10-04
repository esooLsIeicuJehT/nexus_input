# NEXUS INPUT 0.6.2-dev-hardening

NEXUS INPUT is an Android controller-to-touch mapper with a KernelSU/libsu `/dev/uinput` backend, a Shizuku/Sui UserService backend, Accessibility-based global controller capture, persistent per-game profiles, live overlay editing, screenshot mapping, and a KernelSU companion WebUI.

## 0.6.2 hardening changes

- Shizuku/Sui permission handling now follows the documented `checkSelfPermission()` + `shouldShowRequestPermissionRationale()` state machine instead of repeatedly requesting a blocked permission.
- Shizuku authorization can be repaired by opening the official Shizuku manager. A denied optional Shizuku permission no longer traps first-run setup; KernelSU can continue independently.
- Sui-only binder availability is no longer rejected just because the Shizuku manager APK is absent.
- Runtime Auto/Compatibility mode now tries multiple real available backends in order and falls back from a failed Shizuku UserService connection to KernelSU rather than leaving the mapper unavailable.
- A real runtime injection failure in Auto mode can recover once onto another available backend. Forced backend profiles never silently switch.
- Foreground-package state can be restored from Accessibility `rootInActiveWindow` after service recreation.
- D-pad synthetic state is cleared when a mapper profile stops/deactivates.
- Per-profile backend selection UI: Auto/Compatibility, KernelSU Native, Shizuku/Sui, or limited Accessibility fallback.
- Profile validation catches duplicate touch slots, duplicate button bindings, invalid coordinates/radii/deadzones/sensitivity before runtime activation.
- New read-only full Self Check audits permissions, backends, controllers, calibration, game-profile integrity, target-package installation and mapper runtime without injecting input.
- Self Check reports can be copied to clipboard for debugging.
- Setup and Diagnostics now use the inset-safe NEXUS UI shell.

## Build on Termux

```bash
./scripts/verify-source.sh
./scripts/build-termux.sh
```

## Already hardware-verified in earlier revisions

- KernelSU RootService uid 0 and `/dev/uinput` touch injection.
- Shizuku/Sui root UserService uid 0 and `InputManager` touch injection.
- `/dev/uinput` and `/dev/uhid` presence on the target device.
- Accessibility global controller capture.
- Stadia controller discovery, button/axis telemetry and calibration.
- KernelSU companion WebUI.
- Android/Termux debug APK build pipeline.

## Not yet hardware-verified in this exact 0.6.2 revision

- Blocked-Shizuku repair UX on the target phone.
- Auto backend connection fallback after a real Shizuku UserService failure.
- Runtime backend recovery after a live injection failure.
- Read-only Self Check output on the target phone.
- Full in-game behavior after the 0.6.1 safe-launch + 0.6.2 hardening changes.

Unverified paths are not represented as completed hardware validation.
