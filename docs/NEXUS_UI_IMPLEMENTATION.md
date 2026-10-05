# Nexus Input UI implementation and design acceptance

The editable design reference is https://www.figma.com/design/0nup9fG0xfbIa2VTuXKvMh. The account's Figma connector returned its Starter tool-call limit. Individual screen-node links were never captured; none are invented here. Pixel alignment, typography, spacing and visual acceptance against that file remain **pending**. The screen inventory below describes code, not a claim of matching the inaccessible design.

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

For design acceptance, open the supplied file when account access is restored, capture the actual screen node URLs, compare portrait/landscape screenshots on a device, and record each screen's result. Test permission denial, disconnected controllers, empty libraries, invalid imports, backend failure, larger font sizes, rotation, cutouts and three-button/gesture navigation. The current checklist does not treat CI launch tests as visual approval.
