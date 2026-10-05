# NEXUS INPUT v1 device acceptance checklist

**UNKNOWN — hardware status:** This session did not execute these cases on rooted, Shizuku or Accessibility devices. All cases begin **NOT_RUN**. Record PASS/FAIL only after performing the steps and attaching evidence. CI/Robolectric is not device verification.

## Setup and read-only collection

Use a real controller, a game/test surface where injected touches can be observed, and the exact candidate commit/artifact checksum. Record device model, Android build/API, controller VID/PID/descriptor, connection transport, root manager/module version, Shizuku version/UID, screen geometry and app certificate. Use an original-key signed APK for upgrade testing; a debug APK is a separate development test and must not replace the installed release by clearing data.

```sh
adb devices -l
python3 scripts/device_test_v1.py --serial YOUR_ACTUAL_SERIAL --output device-evidence-before.json
# Optional fixed read-only root readiness probe, with root authorization on your test device:
python3 scripts/device_test_v1.py --serial YOUR_ACTUAL_SERIAL --root-readiness --output rooted-evidence-before.json
# After exporting the real APK diagnostics JSON through System:
python3 scripts/device_test_v1.py --serial YOUR_ACTUAL_SERIAL --diagnostics exported-diagnostics.json --output device-evidence-after.json
```

**VERIFIED — collector host tests:** Its tests execute real host commands for exit/stdout/stderr, timeout, missing executable and truncation. The collector preserves actual errors, returns nonzero when collection is incomplete and initializes manual results to `NOT_RUN`. **UNKNOWN:** It has not been executed against Android here. It makes no changes to mapping, app data, kernel tuning or module installation. Root readiness inspects UID, `/dev/uinput` metadata and the actual module file; it does not prove that a virtual touchscreen can be created.

For each case, add status, timestamp, exact steps, expected/actual result, relevant UI screenshot/video, exported diagnostic JSON and log lines. Mark genuinely unavailable cases NOT_APPLICABLE with a reason; do not substitute a pass. Preserve failures and reproduce them after a fix before replacing their status.

## Common APK, storage and controller cases

| ID | Steps | Required proof |
| --- | --- | --- |
| UPGRADE-01 | On the preserved 0.6.2 build, save TAP/HOLD, scan-fallback and both stick mappings with nondefault slots/settings, multiple game profiles, preferred backend and active/enabled state. Export a backup. Install the original-certificate signed v1 with `adb -s SERIAL install -r candidate.apk`, without uninstalling or clearing data. | Actual install exit, matching certificate, preserved profile/package/controller reference/timestamp/coordinates/slots, Room version 3, original preferences retained, active profile preserved and mapper **disarmed** despite imported enabled intent. |
| STORAGE-01 | Restart twice after migration; edit one imported profile; restart again. Try malformed legacy JSON, profile-ID/key mismatch and a collision on a backed-up test installation. | No duplicated/import-overwritten profiles; explicit per-profile migration error, valid profiles retained, no destructive database recreation. |
| STORAGE-02 | Export/import local profile JSON and full backup; compare all bindings, macros, triggers, crosshair/settings and metadata. Import wrong package/ID, duplicate A/B physical alias, duplicate key/scan fallback, overlapping stick axes, duplicate slot, NaN/out-of-range coordinate and malformed macro. | Exact round-trip where valid; rejected invalid imports with visible reason; original saved profile survives rejection. |
| UI-01 | Open splash, Home, Profiles, detail, Mapper, Devices, System and WebUI launcher in portrait/landscape; change font scale/navigation mode and rotate. | Actual screenshots and Figma-node comparison when accessible; no Community/VIP/account/performance controls in APK; one module launcher with honest readiness. |
| SCREENSHOT-01 | Import an actual game PNG/JPEG; import empty/corrupt/oversized/transparent or featureless input. Drag/add/delete and bind buttons, LS/RS, triggers and turbo; save and reload. | Real Bitmap displayed at correct aspect, coordinates survive letterboxing/rotation, pixel-derived region candidates only; empty/invalid input never creates named detected controls. |
| SCREENSHOT-02 | Enable capture service on Android 11+ and capture a real screen, then a protected screen. Test import on an older supported API. | Actual captured image; protected content/capture API failure visible; no old screenshot substituted as a new capture. |
| CONTROLLER-01 | Connect Bluetooth then USB controllers as available. Exercise every physical button, HAT, axis and trigger; disconnect/reconnect during testing and mapping. Connect a second controller. | Android-observed name/VID/PID/ranges, actual event values, undeclared axes shown unavailable, no phantom Xbox fallback, stale device state cleared and active mapping stopped on disconnect. |
| CALIBRATION-01 | Select exact device/axes; collect rest samples, then full stick travel; test both axes and deadzone changes. Retry without input, insufficient travel and disconnect mid-sampling. | Actual sample counts/min/max/noise, measured deadzones and persisted settings; incomplete/no-input calibration fails explicitly. |
| CALIBRATION-02 | Collect LT/RT rest and pull samples separately; inspect saved release/press hysteresis. Run callback/backend timing with mapping disarmed, then attempt while armed. | Real normalized observed trigger ranges and thresholds; no fabricated polling or physical latency; timing labeled as callback/backend request time; busy/insufficient samples and cleanup failure observable. |

## B — Real rooted Android / KernelSU

Force KernelSU for this suite. Keep Shizuku unavailable so another backend cannot hide a root failure. Retain root authorization, RootService, SELinux/InputReader and mapper logs. Record the root manager, kernel, API and ABI for every result.

| ID | Steps | Required proof |
| --- | --- | --- |
| ROOT-01 | Install canonical module ZIP through KernelSU, reboot if required, authorize the APK and activate a valid profile. Inspect actual `/dev/uinput` and created input device while active. Repeat root denial, absent module/uinput and inaccessible device node on a recoverable test device. | RootService actual UID/connection, JNI library loading on device ABI, real virtual touchscreen recognized by Android and observed touch coordinates; each failure disarms with UI/log reason. Directory existence alone is insufficient. |
| ROOT-02 | Rotate/change game geometry during active LS/RS and a held button; exit/re-enter the target game, then disconnect service/root authorization. | Old contacts released before geometry/session replacement, new coordinates use actual bounds, no touch leakage to another foreground package and no silent backend substitution. |
| INPUT-01 | Map button TAP and HOLD, LT/RT HOLD with analog and key events, L3/R3 clicks. Pull/release at hysteresis boundaries while holding a second source of the same trigger. | One contact per mapping; HOLD survives until its final owner releases; no duplicate down, early release or stale trigger. |
| INPUT-02 | Map all four HAT/D-pad directions. Exercise diagonals, neutral, rapid opposite directions and controllers that emit both keys and HAT. | Correct simultaneous diagonal directions and release at neutral, no duplicated or stranded direction contacts. |
| INPUT-03 | Map LS and RS to different slots; move independently/together with sensitivity, curve, deadzone and invert-Y changes. Hold a face button during both stick motions. | Actual independent joystick/camera trajectories, camera contact renewed at boundary, correct curve/direction, finite in-bounds positions and no slot collision. |
| INPUT-04 | Use explicit logical slot 31 and simultaneous contacts with differing slot order. Inspect pointer IDs/index actions. Exercise the 16-contact boundary on a real compatible test surface. | Stable slot/pointer identity, correct first/additional down/up actions, rejection beyond actual concurrent capacity with visible error; no native buffer overrun or silent overwrite. |
| INPUT-05 | Execute TAP/HOLD/RELEASE and SWIPE macro sequences, then hold turbo at several configured rates. Stop/disconnect/focus-change during delays, held macro and swipe. | Real ordered contacts and swipe destination, observed turbo rate/cancellation, no queued activity after stop; never use animation playback as injection evidence. |
| PANIC-01 | Panic from app bar, notification and bubble long-press while both sticks, trigger HOLD, turbo and delayed/swipe macro are active. | All contact releases/backend cleanup acknowledged before success, armed=false, overlays/service stop, no later queued contact; count fields are not fabricated. |
| PANIC-02 | Stop/kill the privilege service while contacts are held; panic while an earlier teardown is queued or has failed; retry panic. | Release failure stays explicit/unconfirmed; failed backend retained for cleanup retry; mapping cannot rearm into an unresolved cleanup; no success solely because the active field was cleared. |

## C — Real non-root Shizuku

Use a non-root device with wireless/ADB-started Shizuku. Confirm **actual UID 2000** in the backend/service evidence; a rooted Sui session does not validate this suite. Force Shizuku and retain its UserService/AIDL plus Android logs. Repeat INPUT-01 through INPUT-05, PANIC-01/02 and overlays on this backend.

| ID | Steps | Required proof |
| --- | --- | --- |
| SHIZUKU-01 | Start Shizuku, authorize Nexus, prepare a profile and observe actual input. Deny authorization; test already-denied/rationale state, absent binder and manager restart. | Permission follows actual Shizuku contract; UserService binds and executes at UID 2000; actual MotionEvent injection succeeds or explicit permission failure. Binder availability is not authorization. |
| SHIZUKU-02 | Exercise multi-touch through the real AIDL contract including slot 31, reordered pointer indices, simultaneous LS/RS/trigger, binder death and cleanup failure. | Real game/touch-test surface response, stable pointer IDs/action indices/downTime, no interleaved stale session, explicit failure and unconfirmed release after transport loss. |
| SHIZUKU-03 | Submit invalid and expired pairing codes and wrong ports; test normal APK environment without an `adb` executable. If a real executable is intentionally available, test real success. Then start Shizuku and authorize separately. | No fake pairing success, visible actual process exit/error or missing-executable instruction; pairing success requires actual confirmation and never claims Shizuku permission. Verify notification denial/cancel and ensure pairing secret is not logged. |

## Overlays, Accessibility, APatch and diagnostics

| ID | Steps | Required proof |
| --- | --- | --- |
| OVERLAY-01 | Grant overlay permission, open game and drag bubble to all edges. Enter in-game editor while held inputs are active; add/bind/delete/save, then cancel another edit. Rotate during an edit and test denied/revoked permission. | Contacts stop before editing, draft/cancel do not overwrite saved data, saved edits persist, geometry/job/window failures visible, bubble remains reachable in landscape and touch delivery is not blocked. |
| OVERLAY-02 | Edit crosshair shape/color/outline/gap/size/offset/opacity/dynamic spread; enter/leave target game and panic. | Actual reticle changes and observed stick-driven spread, no overlay outside mapping/target state, small pass-through window works under Android untrusted-touch rules; invalid color/settings reject explicitly. |
| FPS-01 | On each supported rooted and UID-2000 Shizuku backend, enable overlay, inspect actual matching SurfaceFlinger layers and select the game layer. Compare exported timestamps with the displayed interval-derived statistics. Freeze/background/stop the game; test absent/ambiguous layer, stale timestamps, denied dumpsys and service death. | Actual presented FPS/mean/p95 frame intervals; never display refresh-rate-derived estimates. Stale/no-new frames and errors clear live statistics. Accessibility/unsupported backends show unavailable. These are presentation intervals, not CPU/GPU game-render time. |
| ACCESSIBILITY-01 | Without root/Shizuku, test supported button TAP and SWIPE gesture profiles. Attempt unsupported HOLD/multi-touch/stick profiles. Cancel an in-flight gesture and panic during a long swipe. Test older Android global-motion limitation. | Real dispatch callback/completion/cancellation, unsupported profiles rejected, pending gesture count actual, release remains unconfirmed while Android completion is pending; no root-equivalent capability claim. |
| APATCH-01 | On actual APatch hardware, select APatch and attempt profile activation. | Explicit UNVERIFIED/refused injection, no generic su fallback. This test only validates fail-closed behavior; it does **not** certify APatch transport. Enabling transport requires separate implementation and hardware evidence. |
| DIAGNOSTICS-01 | Export System self-check after success and after root denial, Shizuku death, failed screenshot/import, calibration failure and panic failure. | Actual app/version/API/ABI/devices/storage/runtime/backend/overlay/frame state, clear errors and hardware-unverified statement; no invented diagnostic counters or success. |

## Root module WebUI only

Use the actual KernelSU WebUI host. Before writing, record original observed values and the restoration path. Choose only exposed device-supported values; preserve thermal protections. If the device has no safe restoration path, leave the write case NOT_RUN and document why.

| ID | Steps | Required proof |
| --- | --- | --- |
| WEBUI-01 | Open module WebUI, inspect CPU policy topology/related CPUs/governors/frequency table, all exposed devfreq nodes, raw thermal readings, ZRAM/memory, battery, module and uinput status/logs. Try unavailable/denied bridge and missing nodes. | Actual device values or explicit unavailable fields; no hardcoded hardware, fabricated performance preset or stale green status after an error. APK contains no duplicate controls. |
| WEBUI-02 | On a recoverable device, change CPU governor and a supported frequency interval, devfreq governor and swappiness individually. Restore original values immediately. Attempt invalid governors/paths, inverted/unsupported intervals and unwritable nodes. | Battery gate observed, every write followed by exact read-back, failure/mismatch shown with real exit and rollback result; writes remain within supported min/max, original values restored. Never infer GPU identity solely from an arbitrary devfreq node name. |
| WEBUI-03 | Test battery-low/high-temperature/unavailable-safeguard rejection through observed device conditions or a dedicated safely instrumented hardware test; concurrent changes and interruption. Try thermal/ZRAM/preset operations absent from controls. | Rejection visible, no thermal bypass, lock/interruption requires inspection/recovery, unsupported operations remain unavailable. Do not heat/drain hardware merely to force a test. |
| UPDATE-01 | Check actual GitHub manifest/version. Use a controlled test source to exercise missing/invalid SHA, wrong digest, failed download, current version, bridge timeout and missing callback. | Install enabled only after actual newer valid manifest/exit; digest failure never stages a ZIP; current/no-update and timeout are explicit. Public manifest must refer to a real published asset. |
| UPDATE-02 | Once a signed/hardware-reviewed release exists, stage its actual canonical module update using WebUI, then reboot as instructed. Verify actual module version and unchanged mapping/backend readiness. | Actual `ksud` exit, staged-not-active distinction, matching SHA/version after reboot, retained state/module identity and root logs. If only a draft exists, public-update acceptance remains NOT_RUN. |

Production release requires no unresolved critical FAIL, original-key signed upgrade proof, both root and UID-2000 Shizuku suites, design acceptance, and a documented recovery/restoration result for every root write tested. Attach results to the candidate PR; do not edit this checklist to imply it ran automatically.
