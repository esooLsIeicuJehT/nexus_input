# Changelog

## 0.6.0-dev

- Fix in-game runtime activation after closing the live overlay editor.
- Recreate the root virtual touchscreen when game display geometry changes, including portrait-to-landscape transitions.
- Add HAT-axis D-pad synthesis so D-pad directions can be mapped.
- Add draggable NEXUS quick bubble with mapper/settings shortcuts.
- Add screenshot-based mapper inside the APK using Android's document picker.
- Surface runtime injection failures instead of silently dropping them.
- Keep KernelSU companion self-update support through GitHub `update.json` and verified SHA-256 module ZIPs.

## 0.5.1-dev

- Added KernelSU WebUI GitHub check/install update controls.
- Added KernelSU `updateJson` metadata.
