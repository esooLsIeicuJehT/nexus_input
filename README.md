# NEXUS INPUT 0.6.0-dev-runtime

NEXUS INPUT is an Android controller-to-touch / mouse / keyboard mapper with KernelSU-first injection, Shizuku/Sui fallback, persistent per-game profiles, live in-game overlay editing, controller calibration and a KernelSU companion WebUI.

## 0.6.0 runtime fixes and additions

- Fix in-game runtime activation after closing the live overlay editor.
- Recreate the root virtual touchscreen when game display geometry changes, including portrait-to-landscape transitions.
- Add HAT-axis D-pad synthesis so D-pad directions can be mapped.
- Add draggable NEXUS quick bubble with mapper/settings shortcuts.
- Add screenshot-based mapper inside the APK using Android's document picker.
- Surface runtime injection failures instead of silently dropping them.
- Keep KernelSU companion self-update support through GitHub `update.json` and verified SHA-256 module ZIPs.

## KernelSU companion updates

The KernelSU companion uses KernelSU's official `updateJson` mechanism and also exposes a WebUI **Check GitHub / Install update** control.

Update manifest: `update.json`

Module source: `kernelsu-module/`

Published module ZIPs: `releases/`

The WebUI updater downloads the ZIP, verifies SHA-256, then stages it using:

```sh
ksud module install <zip>
```

A reboot is required after KernelSU stages an update.

## Build on Termux

```bash
./scripts/verify-source.sh
./scripts/build-termux.sh
```

## Verified on the real Android 16 / KernelSU device in earlier revisions

- KernelSU root touch injection.
- Shizuku/Sui root touch injection.
- `/dev/uinput` and `/dev/uhid` availability.
- Accessibility global controller capture.
- Stadia controller discovery, button capture, axis telemetry and calibration.
- KernelSU companion WebUI.

The 0.6.0 runtime-specific paths still require device verification after compilation; they are not presented as already tested.
