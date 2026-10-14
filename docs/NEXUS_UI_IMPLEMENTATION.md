# Nexus Input UI implementation and design acceptance

The implementation source of truth is the user-supplied `Nexus_Input_UI_Reference_Pack.zip`, its `CODEX_IMPLEMENTATION_PROMPT.md`, and all fifteen images. ZIP SHA-256: `daba2f67bc32e6e4055a04728fcc58635b909983869fbe496c7b895e8fea74f7`. The editable Figma reference remains https://www.figma.com/design/0nup9fG0xfbIa2VTuXKvMh; individual node links were never captured and none are invented here. The reference pack makes implementation possible without the rate-limited Figma account. Visual acceptance still requires rendered comparisons and device checks.

This pass starts from current main `b7a5872800086b402e29de8cd62e5f4821798bc4`, preserving application ID, profile schema, privilege backends and the newer `1.1.1-dev` / `1101` version. It does not downgrade the application to the earlier requested 1.0 identity. The local snapshot's tree was verified against GitHub. Two large archival binary blobs were retained unchanged by their existing Git object references; their bytes were unavailable through the connector, and they were not replaced with empty files.

| Canonical image | Implementation in this pass |
| --- | --- |
| 01_home_dashboard.jpg | Live controller/profile/backend/permission cards and six quick actions in `NexusScreens.kt` |
| 02_app_icon_variants.jpg | Crisp N/gamepad vector, adaptive, legacy round and monochrome launcher resources |
| 03_splash_screen.jpg | Static brand panel and responsive portrait/short-landscape splash; no invented initialization progress |
| 04_navigation_color_concept.jpg | Finite 300 ms page-accent transition in `NexusPageTheme.kt`, with fixed navy foundation |
| 05_component_design_system.jpg | Shared glass cards, neon buttons, status chips and accent-aware controls in `NexusComponents.kt` |
| 06_kernelsu_webui.jpg | Crimson/cyan module shell, responsive actual status/actions/logs; existing root bridge and read-back logic preserved |
| 07_ingame_mapper_blue.jpg | Compact actual overlay inspector, screenshot capture, resize and editing controls |
| 08_ingame_quick_menu.jpg | Actual mapping/profile/controller status and compact existing-action menu |
| 09_floating_radial_overlay.jpg | Collapsed N bubble and expandable cyan/violet vertical controls |
| 10_devices_screen.jpg | Physical Android input inventory, vendor/product IDs and explicit unavailable radio/battery observations |
| 11_mapper_rpg_variant.jpg | Real nodes, selection handles, grid, screenshot import/capture and binding inspector |
| 12_system_settings.jpg | Amber permissions, backend, overlay, diagnostics, panic and supported app preferences; root tuning remains WebUI-only |
| 13_mapper_magenta.jpg | Magenta screenshot mapper with validated editing, LS/RS tuning, thresholds and persistence |
| 14_profiles_screen.jpg | Actual Room library, package icons, search/filter/edit/launch, acknowledged add/import/clone/delete |
| 15_controller_test.jpg | Actual observed buttons/HAT/axes, generic controller diagram, raw event log and calibration tools |

The screenshots contain illustrative games, controllers, battery levels, backend success and process IDs. These are design examples, not seed telemetry. Missing observations stay visible. Unsupported rumble/default/forget actions and root-side restart/stop/panic/backup actions are not presented as functional controls. The module safety section directs input release to the APK runtime, which owns active contacts.

The glass treatment uses translucent fills, small edge highlights and bounded accent gradients. It intentionally avoids full-screen blur and continuously animated bloom while gaming. The launcher logo is a native vector interpretation; it is not the reference image pasted into an icon.

## Flow and verification boundary

Actual Android inventory and events → device-isolated controller observations → tester/calibration and mapping diagnostics. Virtual framework devices are excluded from calibration inventory. A controller switch clears the previous device's unobserved buttons/axes while retaining the raw log with device IDs.

Actual saved Room row → exact profile validation → atomic clone/delete/select transaction → acknowledged UI update. Room's committed mapper selection also feeds the screen state after floating-menu profile changes. Draft and generation guards prevent overwriting unsaved edits. Active or unconfirmed contact ownership blocks unsafe profile changes.

Acknowledged emergency contact release → checked removal of every service-owned overlay → two real frame callbacks → accessibility screenshot callback → actual Bitmap → stopped mapping/Android foreground notification → unique PNG in the existing FileProvider cache → explicit MainActivity handoff → exact saved profile validation → existing screenshot import/detection/mapper. Capture failure, timeout, malformed handoff, missing profile and launch failure remain observable. HUD candidates still require real screenshot pixels and manual semantic binding.

MappingConfig / actual gamepad events → existing GamepadMappingRuntime → existing privilege backend → actual delivery result and diagnostics. This UI pass does not replace JNI, RootService, InputManager clients, Shizuku AIDL, touch-slot ownership or emergency-release machinery. APatch remains unverified and fail-closed.

Local checks observed during this pass: Python `Ran 17 tests` / `OK`; WebUI `tests 16`, `pass 16`, `fail 0`; raw-native and APK/module architecture checks passed; module shell syntax and `git diff --check` passed. These are host/source checks, not proof that Android compiled or that a touch reached a game. Chromium CI run `38175311958` passed the actual no-bridge states at 320, 390 and 1280 pixels, with one visible route, no horizontal overflow and no page errors; its artifact digest was verified locally. Android runs `38175103283` and `38175311847` failed on Kotlin collection conversion and a test import respectively; those failures produced no APK. Follow-up Android results must be recorded separately.

Android CI is configured to run debug and release regression tests, assemble debug and unsigned release APKs, bundle unsigned release AAB, lint both variants, verify package/native artifacts and package the module. Fifteen native Robolectric layout captures are required as evidence, not approved pixel goldens. The release-signing key was not supplied; no original-key signed release artifact or upgrade compatibility of the CI debug certificate is claimed. See `UI_REFERENCE_DEVICE_CHECKLIST.md` for the remaining device gates.

| Screen | Current implementation | Functional data |
| --- | --- | --- |
| Splash | `ui/nexus/NexusSplashScreen.kt`, Android cold-start theme | Startup state |
| Home | `ui/nexus/NexusScreens.kt` | Saved profiles, installed games, observed devices, backend/runtime state |
| Profiles | `ui/nexus/NexusScreens.kt`, installed-app picker | Room profiles; actual package metadata |
| Profile detail, portrait/landscape | `ui/nexus/NexusScreens.kt`, `NexusRouteEnhancements.kt` | Selected per-game configuration; saved bindings |
| Screenshot mapper, portrait/landscape | `ui/mapper/ScreenshotMapperScreen.kt` | Imported/captured real Bitmap, normalized coordinates, actual saved bindings |
| Devices and calibration | `ui/nexus/NexusRouteEnhancements.kt`, calibration screens | Android InputDevice and MotionEvent samples; explicit missing-data states |
| System | `ui/nexus/NexusScreens.kt` and diagnostics/settings routes | Permissions, privilege probes, runtime errors, local app preferences |
| In-game mapper and bubble | `service/InGameMapperOverlay.kt`, its `EditorCanvas` | Actual WindowManager overlay; validated profile edit session |
| Crosshair | `service/CrosshairOverlayManager.kt`, crosshair editor | Saved style; spread derived from observed stick displacement |
| FPS/frame time | `service/FrameTimeOverlay.kt`, `ui/frames/FrameOverlayScreen.kt` | Privileged SurfaceFlinger presentation timestamps, explicit stale/unavailable states |
| KernelSU WebUI | `kernelsu-module/webroot/` | Actual KernelSU JS bridge, root observations and verified read-back |

Primary APK navigation is Home, Profiles, Mapper, Devices and System. Secondary routes provide calibration, crosshair, FPS, macros, local profile files, runtime diagnostics and the WebUI launcher. No production Community/VIP/account route remains. Local profile import/export is an on-device file feature.

The APK does not contain root tuning controls. Its module card performs a read-only installation probe and opens the actual KernelSU manager, with instructions to select the module's WebUI. No undocumented module deep link is presented as supported.

Screens never substitute sample game ratings, download counts, controller names, HUD controls or FPS for missing observations. HUD region candidates are derived from screenshot pixels; button meaning requires manual binding. Calibration consumes actual controller events. Latency calibration measures callback/backend request timing, not physical controller-to-photon latency.

For design acceptance, compare actual rendered portrait/landscape screens against every reference image and record differences. When Figma access returns, capture actual node URLs before claiming agreement with the editable file. Test permission denial, disconnected controllers, empty libraries, invalid imports, backend failure, larger font sizes, rotation, cutouts and three-button/gesture navigation. CI launch tests and host layout captures do not imply physical input or visual acceptance on Android hardware.
