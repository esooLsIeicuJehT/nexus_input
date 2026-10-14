# NEXUS INPUT UI reference device checklist

**UNKNOWN — device acceptance:** No case in this document was run on Android hardware in this workspace session. Every case starts **NOT_RUN**. CI, rendered Robolectric screenshots, a controller event, backend authorization, a bridge self-test, and a `matched` routing record do not prove a game received a touch. Record actual game response separately.

**VERIFIED — source contract:** This checklist uses the current APK, the supplied 15-image UI reference pack, and [scripts/device_test_v1.py](../scripts/device_test_v1.py). The current KernelSU mapping adapter uses libsu RootService with privileged Android InputManager. The native uinput primitives remain separate; the active mapping adapter has no uinput or shell fallback. Shizuku uses its UserService/AIDL bridge and `WAIT_FOR_RESULT(1)`. APatch remains UNVERIFIED and fail-closed. These are source observations, not hardware results.

## Evidence and prerequisites

Record the candidate commit, APK SHA-256, version name/code, installed signing certificate, device/OEM build/API/ABI, screen size/density/insets, controller name/descriptor/VID/PID, and actual transport used. Record KernelSU/module versions for the rooted run and Shizuku version plus observed service UID for the non-root run. Do not copy the reference images' profile names, controller type, battery, FPS, temperatures, ratings, or backend state into the report as measurements.

Use a real controller and an owned test surface or game with unmistakable touch controls. Keep existing game layouts and bindings. Export local profile JSON and the supported full-backup ZIP before changing a profile or testing an upgrade. The APK supports validated JSON import; do not assume a bulk ZIP restore action exists. Run the common matrix separately on KernelSU and UID-2000 Shizuku, keeping separate evidence and status for each backend.

Each result needs: case/backend, timestamp, exact action, expected and actual result, video/screenshots, diagnostics JSON, raw logs, and artifact identity. Replace NOT_RUN with PASS or FAIL only after execution. If a case cannot be exercised, retain NOT_RUN and state the missing device capability or environment. Keep the original failure evidence when retesting.

### Read-only collection and raw log capture

These are instructions to run on the device host, not commands executed against a device in this session. Replace the serial and artifact paths with actual values. The collector requires an explicit serial and never chooses a device automatically.

```sh
adb devices -l
export NEXUS_SERIAL='REPLACE_WITH_ACTUAL_ADB_SERIAL'
mkdir -p device-evidence
python3 scripts/device_test_v1.py --serial "$NEXUS_SERIAL" --output device-evidence/before.json
adb -s "$NEXUS_SERIAL" shell logcat -d -t 5000 -v threadtime > device-evidence/before-logcat.txt
```

For the rooted suite only, after authorizing the fixed read-only root probe:

```sh
python3 scripts/device_test_v1.py --serial "$NEXUS_SERIAL" --root-readiness --output device-evidence/root-readiness.json
```

The optional probe runs `su -c 'id && ls -lZ /dev/uinput && cat /data/adb/modules/gamepad.pro.root/module.prop'`. Its exit/stdout/stderr are observations. Missing uinput/module metadata can make this collection return nonzero; this probe neither creates a touchscreen nor proves that the current InputManager mapping path works. Do not convert it into an injection PASS or require a synthetic touchscreen as evidence for the current mapping adapter.

After reproducing a case, open **System → Collect diagnostics → Copy report**, then save the full clipboard JSON to a host file such as `device-evidence/diagnostics.json`. **Copy input report** is a separate compact report useful immediately after failed inputs; retain it alongside the full report. A collection/copy error is a failure to export evidence, not successful diagnostics. The UI does not currently provide a dedicated diagnostics-file export button.

```sh
python3 scripts/device_test_v1.py --serial "$NEXUS_SERIAL" --diagnostics device-evidence/diagnostics.json --output device-evidence/after.json
adb -s "$NEXUS_SERIAL" shell logcat -d -t 5000 -v threadtime > device-evidence/after-logcat.txt
```

The collector imports only JSON identifying `com.inputmapper.platform`, records command exit codes/timeouts/truncation, and returns `1` for incomplete collection. Its manual cases stay NOT_RUN even if collection exits `0`; `hardware_verification` remains `NOT_ESTABLISHED_BY_THIS_COLLECTOR`. Its filtered log snapshot omits some newer tags, so keep the additional unfiltered logcat file for root-service, screenshot, calibration, routing, and Android dispatcher failures. Preserve observed `SecurityException`, rejected dispatch, timeout, service death, and release errors verbatim. This checklist adds UI-specific cases; it does not change the collector's existing manual-case list.

### Upgrade and signing gate

**UNKNOWN — upgrade compatibility:** The original installed APK certificate, candidate signing identity, and actual GitHub secret values have not been verified against a phone here. The application ID is `com.inputmapper.platform`; retaining that ID alone does not establish upgrade compatibility. Gradle reads `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`, with alias `upload`. The signed workflow references `KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD` and derives `KEYSTORE_PATH`; checked-in references do not prove the secrets exist or match the installed key.

Use Android SDK `apksigner verify --verbose --print-certs` on the actual candidate APK. Obtain the installed APK path with `adb -s "$NEXUS_SERIAL" shell pm path com.inputmapper.platform`, pull the actual returned base APK path, and inspect its certificate with the same tool. Retain the command output. Install an upgrade with `adb -s "$NEXUS_SERIAL" install -r ACTUAL_CANDIDATE.apk` only after confirming the compatible signer. A signature mismatch or install error is FAIL/blocked evidence; do not uninstall, clear app data, force a downgrade, or relabel an unsigned artifact as installable to obtain a pass.

| Case | Status | Steps and required evidence |
| --- | --- | --- |
| UIREF-UPGRADE-01 | NOT_RUN | Back up the installed build's actual profiles and settings; install the matching-key candidate in place; retain install exit and certificate output. Existing profile IDs/packages, raw key/scan bindings, slots, macros, trigger/stick settings, crosshair, and backend preference survive. Mapping requires an explicit launch after startup. |
| UIREF-LEGACY-01 | NOT_RUN | On a backed-up device containing genuine legacy `nexus_game_profiles_v1` schema-1 JSON and `nexus_mapper_state` active/enabled state, upgrade in place. Use preserved revision `f8fd74b5686c453af53bd09f125dfa9fe7fbe237` (`recovery/device-0.6.2-hardening`) as the legacy-format reference. Compare every migrated field and controller-profile reference; valid records reach Room, legacy preferences remain, and imported enabled intent does not automatically inject. |
| UIREF-LEGACY-02 | NOT_RUN | Restart twice, edit one imported record, and restart again. On a separate backed-up test installation, exercise malformed legacy JSON, ID/key mismatch, and a colliding saved ID. Retain explicit migration errors and prove valid records survive without destructive database recreation or duplicate re-import. |

## Backend gates

Start the first delivery test with crosshair and FPS overlays disabled. Enable them only after proving baseline delivery; this separates bridge/capture failures from touch-occlusion effects. An authorized backend or green connection indicator is readiness evidence only.

| Case | Status | Steps and required evidence |
| --- | --- | --- |
| UIREF-ROOT-01 | NOT_RUN | On a real KernelSU device, explicitly save KernelSU as the profile's requested backend and wait for save acknowledgement. Keep Shizuku unavailable for this run. Launch the exact saved game package. Retain RootService UID-0/connection and InputManager preparation logs, actual foreground/profile/backend observations, then video of a mapped face button producing the intended game response and releasing. JNI/uinput health fields are observations; they are not this adapter's touch delivery proof. |
| UIREF-ROOT-02 | NOT_RUN | Deny/revoke root or stop the root service on a recoverable test device. The requested KernelSU path must expose preparation/injection/release failure and never silently substitute Shizuku or Accessibility. Restore authorization explicitly and reproduce actual delivery. |
| UIREF-SHIZUKU-01 | NOT_RUN | On a non-root device, start Shizuku through wireless/ADB setup, authorize Nexus, and save Shizuku explicitly. Retain the actual UserService UID **2000**. A UID-0 Sui/root session does not satisfy this case. On API 34+ expect the source-selected `InputManagerGlobal`; API 33 and below select `InputManager`. Record actual SDK, `mode=WAIT_FOR_RESULT(1)`, `probe=BRIDGE_ONLY`, and service implementation version 4, then prove a real mapped DOWN/UP changes the game. These are expected fields, not captured output. |
| UIREF-SHIZUKU-02 | NOT_RUN | Deny permission, test manager absent/not running, restart its binder/UserService, and test setup failure. Preserve the actual errors; no fabricated pairing or self-test success, stale cached readiness, or hidden backend substitution is acceptable. |
| UIREF-SHIZUKU-03 | NOT_RUN | Kill/restart Shizuku while a touch is held and request panic before reconnecting. Release uncertainty must stay visible; a new bridge must not silently assume ownership of an unresolved old contact. A process restart alone is not release proof. |
| UIREF-BACKEND-01 | NOT_RUN | Load a profile forced to Accessibility while Shizuku is available. System must show the profile's actual requested backend; launching a persistent-touch profile must visibly reject the unsupported requirement. Explicitly select/save Shizuku and relaunch. Select Automatic, confirm preference reset/persistence, and inspect its actual selection notice. No fallback may repair a forced preference silently. |
| UIREF-APATCH-01 | NOT_RUN | Where an APatch environment is available, inspect its status. It must remain UNVERIFIED/fail-closed; neither the UI pass nor this checklist authorizes a claim of tested APatch injection. |

## Profiles, mapper and real screenshot handoff

Repeat these cases for each required backend. A fresh profile is an actual local saved profile targeting a selected installed game, not reference-image data. Test an existing/imported profile as well as a fresh one.

| Case | Status | Steps and required evidence |
| --- | --- | --- |
| UIREF-PROFILE-01 | NOT_RUN | Add a selected installed game, create/name a fresh profile, add real targets, clone it, select each clone/original, and delete only a disposable test profile while mapping is stopped. Check actual IDs/package/default/active status and persistence after process restart. Profile switching/deletion while contacts or cleanup are unresolved must be blocked visibly. |
| UIREF-PROFILE-02 | NOT_RUN | Import the actual backup JSON and compare full settings. Attempt wrong-package/ID, duplicate physical alias/raw identity, overlapping stick axes, duplicate slot, invalid geometry/trigger interval, and malformed macro on a test copy. Each rejection explains the cause and preserves the saved original. |
| UIREF-MAPPER-01 | NOT_RUN | With no game selected and no Bitmap, Add/Save/Capture are unavailable and HUD scan is disabled. Import may open the actual document picker. No default game/controller or packaged game screenshot appears as detected data. |
| UIREF-MAPPER-02 | NOT_RUN | Import a real PNG/JPEG; place and drag targets at corners/edges and with grid on/off in portrait/landscape. Resize using every selected corner handle and size controls; rebind, copy to a distinct physical input, and delete. A copy has a distinct ID and resolves to an independent slot. Invalid duplicate input/slot choices are rejected. Save, reload and restart; actual normalized positions/radii/raw bindings persist and touch the corresponding game location. |
| UIREF-MAPPER-03 | NOT_RUN | Change LS node sensitivity/deadzones, walk/run radii/threshold and movement inversion; change RS sensitivity/deadzones, horizontal/vertical sensitivity, smoothing/boost and camera inversion. Test a profile containing a legacy per-node camera invert flag, disable inversion, then reload. Observe actual motion changes, valid intervals, independent LS/RS settings, and preserved raw identities/slots. A slider changing appearance alone is insufficient. |
| UIREF-CAPTURE-01 | NOT_RUN | Launch the target game and choose the floating **Screenshot mapper** action. While contacts are held, confirm acknowledged release before capture; the mapper, bubble/panel, crosshair and FPS windows are removed before the captured frame. The foreground mapping session and its Android notification are stopped before handoff. The mapper displays the actual current game image, opens the exact captured profile/package, and keeps mapping stopped. No prior import/capture is silently substituted. Save the edited profile and explicitly relaunch to verify its game coordinates. |
| UIREF-CAPTURE-02 | NOT_RUN | Capture protected content, disable the capture service, lose target focus, and exercise a capture timeout/failure where reproducible. Retain the actual Android error or timeout and visible UI/log cause; no success image or previous Bitmap is presented as a new capture. Confirm no stale callback opens a mapper after controls/service are closed. |
| UIREF-CAPTURE-03 | NOT_RUN | Capture from both orientations, including a display cutout/system bars; confirm aspect and actual coordinates. Import corrupt, empty, oversized and featureless images. HUD candidates must derive from the supplied Bitmap and remain reviewable image regions; featureless/failure cases never fabricate named game controls. On API below 30, screenshot capture is explicitly unavailable while actual document import remains usable. |
| UIREF-HANDOFF-01 | NOT_RUN | Switch to another saved same-game profile in the floating Profile menu, then capture. Return to the app and compare active profile, default state, mapper bindings, and diagnostics' selected/bindings profile IDs. No stale previous-profile UI or cross-game handoff may overwrite the selected layout. |
| UIREF-EDIT-01 | NOT_RUN | Enter floating Mapper while contacts are held: release acknowledgement precedes edit. Test Add/Bind/Learn/Copy/Delete/all resize handles/Grid/Hide nodes/Reset and compact inspector. Cancel restores the original; Save writes the real draft and rearms only that saved config. Copy requires a distinct input; macro/turbo/stick targets do not expose an ignored TAP/HOLD behavior action. Save/release failures remain visible and preserve the draft/original error. |

## Controller tester, calibration and delivery

Android 14/API 34+ provides the global motion capture path used for in-game sticks/HAT. On older Android, retain the actual capability warning and do not report full in-game stick support merely because the foreground tester observes input. Test only the actual connected controller's reported axes and capabilities.

| Case | Status | Steps and required evidence |
| --- | --- | --- |
| UIREF-DEVICE-01 | NOT_RUN | Open Devices/Test before connecting hardware, connect Bluetooth/USB as available, then disconnect/reconnect and attach a second controller. Actual name/ID/VID/PID/ranges/event device are shown; unavailable axes and unobserved transport/battery remain unavailable. No physical controller is reported from a virtual SDK input device alone. Disconnect clears stale live device/event state. |
| UIREF-TESTER-01 | NOT_RUN | Press/release A/B/X/Y, shoulders, LT/RT, Start/Select, L3/R3 and every D-pad direction/diagonal; move both sticks through their ranges. Match each highlight/value and raw event against the action, including key/scan/source/device ID and normalized axes. Before any event, a zero/unlit visual is not labeled a measured released controller state. Rumble controls must not claim game-feedback relay or a motor pulse absent actual implementation/capability. |
| UIREF-LEARN-01 | NOT_RUN | Learn an existing target from a real button and a single HAT direction; save/reload. HAT learning stores its direction without invented key/scan values. For a delivered unknown/vendor button, preserve its actual key/positive scan identity. If Android reserves a button or delivers no event, learning waits/fails honestly. Do not duplicate an already correctly bound target to hide a routing failure. |
| UIREF-CALIBRATION-01 | NOT_RUN | Run actual rest/extension stick sampling with the supported axes and one intended controller connected; save measured calibration and compare mapper settings. Retry with no events, insufficient travel, competing calibration, and disconnect during sampling. No-input/invalid sampling exposes failure and does not claim measured deadzones. Retain real sample counts/ranges where reported. |
| UIREF-CALIBRATION-02 | NOT_RUN | Sample LT and RT rest/full pull separately, save, and verify actual press/release thresholds with release below press. Retry unsupported/no-event/disconnected trigger input. If using backend-call timing, mapping must be stopped; record its real result labeled backend call duration, never physical or device-to-photon latency. This timing action performs an actual small tap and requires a suitable test surface. |
| UIREF-INPUT-01 | NOT_RUN | Test face-button TAP/HOLD and LT/RT HOLD near press/release hysteresis. Where the controller emits both analog and key ownership for one trigger, release one source while the other remains held. Observe one contact continuing until its final owner releases, without duplicate DOWN or early UP. |
| UIREF-INPUT-02 | NOT_RUN | Test four HAT/D-pad directions, diagonals, neutral, quick taps/opposites, historical/batched press-then-neutral samples, and key/HAT overlap where the device supplies them. Observe the intended contacts and complete release. Keep Start/Select/L3/R3 tests distinct from LS/RS motion. |
| UIREF-INPUT-03 | NOT_RUN | Move LS and RS independently, together, and while holding a face button and trigger. Use different explicit/automatic logical slots and a high slot such as 31 on a disposable profile. Retain resolved-slot evidence plus real simultaneous game response; no slot collision, wrong pointer release, out-of-bounds motion or stuck camera contact is acceptable. Exceeding an actual capacity must fail visibly rather than overwrite another contact. |
| UIREF-INPUT-04 | NOT_RUN | Execute real TAP/HOLD/RELEASE/SWIPE macros and turbo at saved rates. Stop, focus-change, disconnect or panic during delays, held macro, swipe and repeat. Observe ordered contacts/destination and cancellation; an animation or configured rate is not injection/timing proof. |
| UIREF-FOCUS-01 | NOT_RUN | Leave/re-enter the exact game, open unrelated apps, notification shade and floating controls, and rotate while buttons/sticks are active. Foreground gating/release must prevent touch leakage. Re-entry uses the saved profile and actual geometry; record readiness/notice/errors instead of assuming the overlay itself establishes focus. |
| UIREF-DISCONNECT-01 | NOT_RUN | Disconnect/reconnect the controller while LS/RS/trigger/HOLD/macro contacts are active. Mapping stops/releases or explicitly reports uncertainty; stale axes/buttons must not continue injection. Reconnect requires actual device events and a deliberate valid mapping session. |
| UIREF-PANIC-01 | NOT_RUN | Trigger app panic, notification stop and bubble long-press during simultaneous sticks/trigger/HOLD/turbo/delayed macro. Capture the backend release acknowledgement, armed=false, observed release of every contact and absence of later queued actions. A removed overlay or a zero counter is not contact-release proof. |
| UIREF-PANIC-02 | NOT_RUN | Stop the privilege service with held contacts, request panic during queued/failed cleanup, then retry. Unconfirmed release stays explicit in System/logs and blocks unsafe rearm. Retain the backend failure rather than treating a cleared active field or process death as confirmed cleanup. |

## Overlay, WebUI and visual comparison

Compare all 15 supplied files. Use actual-device screenshots in portrait/landscape and the same approximate layout dimensions; reference game art and sample telemetry are composition guides only. The APK must contain mapping/settings/status features and one Open KernelSU WebUI entry, with kernel/performance tuning exclusively in the actual root module WebUI.

| Case | Status | Steps and required evidence |
| --- | --- | --- |
| UIREF-OVERLAY-01 | NOT_RUN | Drag the collapsed bubble to every edge; expand/close actions; inspect actual runtime error, notice, profile/backend and foreground. Camera selects an existing RS target; no synthetic camera node is inserted. Large diagnostics scroll inside their panel. Controls remain reachable after rotation, cutout and font-scale changes and avoid covering most of the game. |
| UIREF-OVERLAY-02 | NOT_RUN | After baseline delivery, enable crosshair and then frame overlay separately/together. Test normal finger touches and mapped contacts beneath/near each window; preserve Android touch-occlusion failures. Confirm actual frame layer/intervals or an explicit unavailable/error state, never an invented stable FPS. Inspect gaming responsiveness and retained input behavior; record measured evidence before making a performance claim. |
| UIREF-WEBUI-01 | NOT_RUN | Open the real module WebUI from APK on KernelSU; test absent manager/module behavior separately. Verify actual module/capability/status/log/update results. Opening a page must not apply tuning. Any deliberate supported root change belongs only in WebUI and needs before/request/read-back evidence; unavailable capabilities stay disabled. Non-root Shizuku does not gain root controls. |
| UIREF-VISUAL-01 | NOT_RUN | Compare `01_home_dashboard.jpg`: actual controller/profile/backend/mapping/permission cards, cyan accent, readable glass, working actions and navigation. |
| UIREF-VISUAL-02 | NOT_RUN | Compare `02_app_icon_variants.jpg`: installed launcher/adaptive icon at small size and masked variants; no blurry reference collage presented as the app icon. |
| UIREF-VISUAL-03 | NOT_RUN | Compare `03_splash_screen.jpg`: real cold/warm launch, crisp branding, no fabricated loading/device progress. |
| UIREF-VISUAL-04 | NOT_RUN | Compare `04_navigation_color_concept.jpg`: route accent transitions on nav/borders/buttons/haze; black/navy background and readable content remain consistent. |
| UIREF-VISUAL-05 | NOT_RUN | Compare `05_component_design_system.jpg`: glass cards, thin neon borders, buttons/toggles/sliders/status/error states at normal and enlarged font scales. |
| UIREF-VISUAL-06 | NOT_RUN | Compare `06_kernelsu_webui.jpg`: actual root WebUI desktop/phone layout and crimson/cyan accents; no sample status/update success. |
| UIREF-VISUAL-07 | NOT_RUN | Compare `07_ingame_mapper_blue.jpg`: cyan/violet mapping nodes, actual selected resize handles, compact inspector and real game visibility. |
| UIREF-VISUAL-08 | NOT_RUN | Compare `08_ingame_quick_menu.jpg`: compact readable quick actions over a real game, observed status/errors and safe close/panic access. |
| UIREF-VISUAL-09 | NOT_RUN | Compare `09_floating_radial_overlay.jpg`: crisp collapsed neon control and expandable actions; drag/reachability and actual action execution. |
| UIREF-VISUAL-10 | NOT_RUN | Compare `10_devices_screen.jpg`: green/lime-cyan device cards with only actual hardware/capability observations and honest disconnected state. |
| UIREF-VISUAL-11 | NOT_RUN | Compare `11_mapper_rpg_variant.jpg`: alternate real imported/captured game aspect, usable landscape layout and genuine binding/tuning controls. |
| UIREF-VISUAL-12 | NOT_RUN | Compare `12_system_settings.jpg`: amber settings/status/diagnostics, actual requested backend, visible panic failure and single WebUI launcher; no APK CPU/GPU/thermal/ZRAM/VIP/community controls. |
| UIREF-VISUAL-13 | NOT_RUN | Compare `13_mapper_magenta.jpg`: magenta mapper, real image/nodes, compact inspector and save/rebind/copy/delete/grid controls. Preserve valid trigger hysteresis; do not copy invalid sample thresholds from art. |
| UIREF-VISUAL-14 | NOT_RUN | Compare `14_profiles_screen.jpg`: violet real game icons/titles/packages/profiles/default/active states and supported create/import/clone/select/delete actions. No reference sample counts/ratings are treated as user data. |
| UIREF-VISUAL-15 | NOT_RUN | Compare `15_controller_test.jpg`: green controller visualization, live buttons/sticks/triggers/HAT, actual identifiers/raw events and available calibration; absent events remain unobserved. |

## Result handoff

Keep rooted and UID-2000 Shizuku outcomes separate, including failures that occur only with overlays or a specific OEM/API/controller. Attach full/compact diagnostics, unfiltered logcat, game-response recordings, actual profile JSON, certificate/install evidence, and the read-only collector report. Report CI/build results separately from this matrix. Any outstanding NOT_RUN case remains unverified in release notes; a successful build or attractive screenshot does not complete it.
