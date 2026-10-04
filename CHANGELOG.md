# Changelog

## 0.6.0-dev

- Introduce the NEXUS INPUT five-tab application shell: Home, Profiles, Mapper, Devices, and System.
- Add Nexus dashboard, profile library/detail, controller device, and backend/system screens using live ViewModel state.
- Rename visible APK branding from Controlyst to NEXUS INPUT while retaining compatibility aliases for legacy code.
- Remove the phantom Xbox fallback: controller state now records whether Android actually exposes an input device plus its name, VID, and PID when available.
- Stop presenting legacy calibration/latency simulation as hardware-verified telemetry in the Nexus UI.
- Add the editable Figma design and implementation workflow to `docs/NEXUS_UI_IMPLEMENTATION.md`.
- Add a real Nexus loading splash and dark cold-start window treatment.
- Add landscape-immersive mapper/profile routing so the app chrome no longer consumes critical vertical space during sideways mapping.
- Add a landscape profile side panel with real profile/binding/controller data and launch/mapper controls.
- Add a live Android controller event monitor for physical button presses, sticks, triggers, and HAT/D-pad axes delivered to the Activity.
- Surface live controller events in the Devices route without fabricating unsupported axes.
- Rebuild the in-app floating Nexus bubble so drag/snap bounds use the actual current screen size instead of hard-coded dimensions.
- Rebrand and restructure the floating quick mapper menu around the active profile, controller state, backend, mapper, crosshair, calibration, KernelSU WebUI, and panic kill.
- Redesign the KernelSU WebUI as a responsive Nexus control center while preserving the existing diagnostic/update command contracts.
- Fix the KernelSU launch action and WebUI APK probes to target the current Android package/component.
- Remove the repo-level missing debug-keystore override so `assembleDebug` can use Android's standard debug signing behavior.
- Add GitHub Actions debug APK compilation and artifact upload for PR/main verification.
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
