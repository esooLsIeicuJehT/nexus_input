# Nexus Input UI Implementation

## Source of truth

- Product name: **NEXUS INPUT**
- Tagline: **Play Your Way**
- Editable Figma design: https://www.figma.com/design/0nup9fG0xfbIa2VTuXKvMh
- Android UI stack: Jetpack Compose / Material 3
- KernelSU WebUI stack: HTML + CSS + JavaScript under `kernelsu-module/webroot/`

The Figma Make React project is a visual/reference prototype. The Android APK is implemented natively in Compose rather than embedding the React prototype in a WebView.

## Screen mapping

| Design | Android implementation |
| --- | --- |
| Splash / loading | Android/Compose startup screen (Phase 2) |
| Home / Dashboard | `ui/nexus/NexusHomeScreen` |
| Profiles | `ui/nexus/NexusProfilesScreen` |
| Game Profile Detail | `ui/nexus/NexusProfileDetailScreen` |
| Mapping Editor | Existing `ui/mapper/ScreenshotMapperScreen`, reskin/refactor in Phase 2 |
| In-game mapper | Existing overlay + mapper runtime, landscape redesign in Phase 2 |
| Devices / Controller Tester | `ui/nexus/NexusDevicesScreen` |
| System / Settings | `ui/nexus/NexusSystemScreen` |
| KernelSU WebUI | `kernelsu-module/webroot/`, redesign in Phase 3 |

## Navigation

Primary APK navigation is now:

1. Home
2. Profiles
3. Mapper
4. Devices
5. System

Secondary screens (calibration, overlay studio, KernelSU WebUI, safety, macros, community, VIP) remain reachable from those primary surfaces while they are migrated to the Nexus visual system.

## Data integrity rule

The production UI must not display fabricated telemetry.

- Controller connection, name, VID and PID come from Android `InputDevice` inventory.
- Configured polling rate is labeled as configured, not measured.
- Latency is not presented as a verified hardware measurement until a real measurement path exists.
- Backend cards reflect privilege probe results rather than hard-coded connected states.
- Failed or unavailable states remain visible instead of silently falling back to a success-looking value.

The legacy calibration/latency helper still contains a simulated path. It is explicitly labeled unverified and must be replaced with raw input event sampling before those values are treated as real measurements.

## GitHub sync policy

Every Nexus Input implementation phase follows this workflow:

1. Inspect current repository state.
2. Make changes on a feature/fix branch.
3. Commit all source and documentation changes to GitHub during the same work phase.
4. Open a PR against `main`.
5. Record what was verified and what still requires real-device verification.
6. After merge, the next change starts from current `main`.

No design/code upgrade should exist only in chat or only in Figma once implementation begins.

## Current phase

### Phase 1: Nexus application shell

Implemented:

- Nexus Input brand palette and theme aliases.
- Five-tab application shell.
- Dashboard using repository/view-model state.
- Profiles and profile detail screens.
- Devices screen using Android input-device detection.
- System/backend screen using actual privilege probe state.
- Visible app strings renamed to Nexus Input.
- Phantom Xbox fallback removed from controller detection.

Not yet hardware-verified:

- UI rendering on the target moto g 2026.
- Controller VID/PID and connection reporting across Stadia, Xbox and PlayStation devices.
- Landscape mapper layout.
- True stick/trigger calibration sampling.
- True latency measurement.
- KernelSU WebUI redesign.
