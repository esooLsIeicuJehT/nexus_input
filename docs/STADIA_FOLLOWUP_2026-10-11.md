# Stadia follow-up — 2026-10-11

The user tested the released 1.1.0-dev APK and reported that the D-pad and five center buttons still do not trigger game touches. Screenshots show Stadia VID `18d1`, PID `9400`, joystick HAT_X/HAT_Y, and these center events:

| Control | Android key | Linux scan |
| --- | --- | --- |
| Select | BUTTON_SELECT (109) | 314 |
| Start | BUTTON_START (108) | 315 |
| Stadia/Guide | BUTTON_MODE (110) | 316 |
| Assistant | UNKNOWN (0) | 704 |
| Capture | UNKNOWN (0) | 705 |

The Assistant/Capture labels are supported by [AOSP's Stadia bridge](https://android.googlesource.com/platform/frameworks/base/+/3b92b9682d9dbf8d6420432394218ef703c022d3%5E%21/): BTN_TRIGGER_HAPPY1 is Assistant and BTN_TRIGGER_HAPPY2 is Share. [Linux input-event-codes.h](https://github.com/torvalds/linux/blob/master/include/uapi/linux/input-event-codes.h) defines those as 0x2c0/0x2c1. [Chromium's Stadia mapping](https://chromium.googlesource.com/chromium/src/+/HEAD/device/gamepad/android/java/src/org/chromium/device/gamepad/GamepadMappings.java) maps BUTTON_1/2 to Assistant/Capture. This is now measured scan evidence plus a primary-source mapping, rather than a guessed firmware table. Other vendors' extra buttons retain their existing identity.

## Pass 1: confirmed defects

- UNKNOWN 704/705 have no logical aliases in 1.1.0-dev. Ordinary Assistant/Capture targets therefore miss unless individually learned. Add the measured Stadia aliases, including its alternative BUTTON_1/2 translation.
- Both current learning dialogs wait only for KeyEvents. The user's D-pad emits real HAT MotionEvents, so learning it never completes. Retain actual single-direction HAT edges separately from key presses; save their logical D-pad direction without inventing a scan or physical KeyEvent.
- The runtime reads only a MotionEvent's final sample. A fixture with HAT_LEFT in history and neutral as the current sample reproduced an empty touch-call list instead of down/up. Process historical D-pad edges in order, then the current sample. Do not replay stick/camera history.

## Pass 2: compatibility and failure visibility

- After adding aliases for UNKNOWN scans, a learned BUTTON_1/2 could miss its saved scan because the old fallback required an empty alias set. Allow the explicit saved scan for UNKNOWN translations; keep known conflicting keycodes distinct.
- Capture callbacks previously copied monitor state outside a common lock. Serialize key/motion updates so a motion copy cannot overwrite a retained learning press.
- Keep logical bindings, raw scan bindings, macros, turbo, HOLD ownership, slots, profile identity, launch/focus grace, trigger hysteresis, current stick samples and the 8 ms camera loop intact.
- Keep a bounded routing trace after leaving the game. An unmatched key or HAT must say that the saved profile lacks a target. A captured key or changed HAT edge blocked by focus/backend readiness must say why, including disarmed capture and a batched HAT press. Keep neutral frames and key repeats out of this trace. A matched request is not proof of game-visible touch acceptance.

## User diagnostics and remaining device evidence

The failed test is on the rooted Moto G 2026, Android 16/API 36. KernelSU is detected with UID 0, Accessibility capture is connected, global joystick capture is available, and overlays are permitted. The observed Stadia device advertises real HAT_X/HAT_Y ranges of -1..1. Its vibrator is not reported by Android; a game rumble relay remains unimplemented.

The supplied `builtin_delta_force_nexus_v1` profile contains 17 targets: A/B/X/Y, LT/RT, LS/RS, R3, LB/RB, all four D-pad directions, START and SELECT. The six reported Start/Select/D-pad targets are logical bindings with null key/scan/explicit-slot fields and valid normalized coordinates. They are present: do not tell the user to recreate them or overwrite their layout. GUIDE, ASSISTANT and CAPTURE do not appear in the supplied profile and need user-placed targets before those controls can produce mapped touches.

The runtime was disarmed with no backend/config and no error **when the report was collected**. This does not establish its state during the earlier game test. Neither the current Activity lifecycle nor opening Diagnostics contains an automatic mapper stop. Game launch saves and loads the exact profile, then arms it through MappingForegroundService; focus changes gate injection and release contacts but do not silently erase the armed profile. No unproven launch/focus change is justified from this report.

An additional regression replays the supplied 17-target order and coordinates with model-default tap behavior and automatic slots through the real PrivilegedInputManagerEngine, replacing only its OS Binder boundary. Check all four HAT directions and Start/Select, including pointer ID 16, Android action indices, normalized-to-pixel coordinates and tap releases. This is event-composition validation, not a connected Moto or game acceptance test; the supplied report does not include all profile settings or button behaviors.

Diagnostics now label the selected editor profile separately from the profile whose bindings are reported, prefer an armed profile's actual targets, and include effective touch slots and button behavior. **Copy input report** keeps runtime/capture/backend state, saved bindings and the most recent 24 routes without the lengthy raw motion log. Use it after pressing the failed inputs. A matched route is not proof that Android or the game accepted the resulting touch.

Still required: whether other mapped controls worked and whether the floating panel reported mapping ready during the failed test, plus routing evidence from the follow-up APK. The code changes explain missing extra-button aliases, HAT learning and a reproduced batched D-pad tap loss. They do not yet establish the cause of every Start/Select failure or a held, correctly bound HAT direction. Neither Moto phone is connected to this workspace.

The follow-up source backup is `backups/nexus-input-before-stadia-followup-2026-10-11.tar.gz` at baseline `ad75bf3`; its digest is in `backups/SHA256SUMS`.

## Completed validation and publication

Both signed build variants pass 145 Android tests, including the reported 17-target root event-composition replay and blocked Accessibility capture. APK/AAB packaging, all four native ABIs and lint pass; lint reports 0 errors and 62 existing warnings. The 17 host/module tests, 13 WebUI tests, architecture boundaries, shell syntax and both backup digests pass. [GitHub validation for the released source](https://github.com/esooLsIeicuJehT/nexus_input/actions/runs/38127903524) passes too.

[v1.1.1-dev](https://github.com/esooLsIeicuJehT/nexus_input/releases/tag/v1.1.1-dev) publishes the normal release APK, debug APK, matching KernelSU companion, checksums and public signing notes from source commit `6f306f4`. Both APKs retain the v1.1.0-dev development certificate. Publicly downloaded assets match the local signed files byte for byte; the companion's module identity, version, CRC and SHA-256 are verified before advancing the public updater manifest.

These completed checks establish the reproduced fixes and artifact integrity. Remaining Start/Select and held-D-pad in-game acceptance still require the follow-up phone report; no full hardware success or controller rumble fix is claimed.
