# NEXUS INPUT Root Companion 0.5.0

This KernelSU module is the NEXUS INPUT control/diagnostics companion. It does not route high-rate controller events through JavaScript or shell commands.

The APK/root engine handles the data plane. The module provides:

- boot-time `/dev/uinput` and `/dev/uhid` probes;
- input-device inventory;
- KernelSU WebUI diagnostics;
- APK launcher;
- live APK runtime state published by the verified libsu RootService to `/data/adb/gamepad-pro/runtime-status.txt`.

The runtime status file is control-plane telemetry only. It is not used for per-frame input transport.
