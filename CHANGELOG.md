# 1.1.0-dev — neon glass and controller routing candidate

- Verified source backup and two-pass audit in `backups/` and `docs/AUDIT_NEON_UPGRADE_2026-10-10.md`.
- Translucent cyan/violet panels, static glow, one persistent bottom dock, compact mapper toolbar and bounded diagnostics/input/tuning windows.
- Expandable System/backend details and WebUI device controls; WebUI uses five separate workspaces instead of one long page. Native floating bubble, editor and binding dialogs share the theme.
- Physical button learning in screenshot and in-game editors; Guide, Capture and Assistant choices. Compatible Android-prefixed labels, equivalent center-key translations, unknown-key scan fallback and scan-bound HAT/trigger matching. Existing contact ownership, stick driver, launch grace and cleanup retained.
- Root HOME key conversion corrected; Capture/Assistant key conversion added. CPU final-pair read-back failures attempt verified restoration.
- Real checked-in `1.1.0-dev` module ZIP and SHA-256 manifest replace the stale `0.6.0-dev` channel. Update staging checks ZIP module ID/version, serializes processes and removes failed downloads. This public development channel is a test candidate, not hardware acceptance or a production-signed APK release.

# Changelog

## 1.0.0 candidate — not yet production-published

- Keep Android mapping and module root tuning as separate products; remove APK performance controls, Community/VIP navigation and unused cloud authentication.
- Replace simulated pairing, HUD controls and controller calibration with real process results, Bitmap input and InputDevice/MotionEvent samples.
- Add exact profile validation, duplicate physical-input checks, transactional legacy migration and explicit Room 1→2→3 migrations without destructive fallback.
- Complete real screenshot import/capture, profile persistence, in-game editing, controller testing, crosshair and timestamp-based presented-frame overlays.
- Harden backend selection, actual Shizuku UserService/AIDL, touch slots, HAT/D-pad, triggers, independent sticks, swipe macros, turbo and observable panic cleanup. APatch remains unverified/fail-closed.
- Add guarded module CPU/devfreq/swappiness controls with actual capability observations and read-back; unsupported device-specific thermal/ZRAM/preset operations remain explicit.
- Share app/module version 1.0.0/code 1000. Add debug/release tests, lint, native/architecture guards, APK/AAB identity checks, deterministic module packaging and fail-closed signing/draft-release workflows.
- Add release gates and the rooted/non-root device checklist. CI is not hardware verification; exact Figma comparison and signed upgrade testing remain pending.

Earlier entries below describe historical revisions and must not be read as hardware acceptance of the v1 candidate.

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
