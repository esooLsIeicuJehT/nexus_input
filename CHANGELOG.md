# Changelog

## 0.6.0-dev

- Introduce the NEXUS INPUT five-tab application shell: Home, Profiles, Mapper, Devices, and System.
- Add Nexus dashboard, profile library/detail, controller device, and backend/system screens using live ViewModel state.
- Rename visible APK branding from Controlyst to NEXUS INPUT while retaining compatibility aliases for legacy code.
- Remove the phantom Xbox fallback: controller state now records whether Android actually exposes an input device plus its name, VID, and PID when available.
- Stop presenting legacy calibration/latency simulation as hardware-verified telemetry in the Nexus UI.
- Add the editable Figma design and implementation workflow to `docs/NEXUS_UI_IMPLEMENTATION.md`.
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
