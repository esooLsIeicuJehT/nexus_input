# NEXUS INPUT 0.6.2 build status

## Hardware-proven before 0.6.2

- KernelSU/libsu root service reaches UID 0.
- `/dev/uinput` and `/dev/uhid` are present on the target Android 16 device.
- KernelSU JNI/uinput touch injection hit a real Android target.
- Shizuku/Sui binder and root UserService touch injection hit a real Android target.
- Accessibility global controller capture works.
- Google Stadia Controller rev. A (VID:PID 18d1:9400) detection, buttons, axes and calibration work.
- HAT axes are visible and usable for D-pad synthesis.
- KernelSU WebUI diagnostics and GitHub updater UI work.

## 0.6.2 source-verified

- Central Shizuku/Sui permission state and repair path.
- Blocked authorization detection without request loops.
- Per-profile backend preference.
- Auto backend failover with explicit failure reporting.
- Game profile validation before mapper arming.
- Read-only full system self-check and copyable report.
- Safe-launch grace retained.
- Runtime geometry refresh contract.
- KernelSU updater requires SHA-256.

## Not yet hardware-verified for 0.6.2

- Exact 0.6.2 APK compilation on the target Termux toolchain.
- 0.6.2 Shizuku blocked-permission repair UX on the target device.
- Runtime backend failover during a real mapped game session.
- Full in-game mapping behavior after the safe-launch/runtime hardening changes.

No item in the final section should be treated as passing until verified on the actual device.
