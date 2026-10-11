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
- Keep a bounded routing trace after leaving the game. An unmatched key or HAT must say that the saved profile lacks a target. A captured key blocked by focus/backend readiness must say why. A matched request is not proof of game-visible touch acceptance.

## What still needs the phone report

The code above does not by itself explain every Start/Select/Guide failure or a held, correctly bound HAT direction. The user's phone/backend, exact saved bindings and in-game service state are still required. The shipped Delta Force preset contains face buttons, triggers and sticks; it does not invent targets for all center/D-pad controls. Preserve the user's layout and check their actual profile rather than silently adding arbitrary touch coordinates.

Requested evidence: launch through the profile, press the failed controls, then collect/copy System → Diagnostics. Read the selected bindings, backend/error and retained routes. Neither Moto phone is connected to this workspace; simulated transport checks cannot establish hardware acceptance.

The follow-up source backup is `backups/nexus-input-before-stadia-followup-2026-10-11.tar.gz` at baseline `ad75bf3`; its digest is in `backups/SHA256SUMS`.
