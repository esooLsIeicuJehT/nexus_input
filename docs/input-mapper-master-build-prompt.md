# Master Build Prompt — Universal Root/Non-Root Game Input Mapper

Paste this whole document into your coding AI as the project brief. It is written to be handed over phase-by-phase (each phase is self-contained) or all at once as full project context.

---

## 0. Non-Negotiable Ground Rules (read this before writing anything)

These rules apply to every phase below. Restate them to yourself before every file you generate:

1. **No placeholder logic disguised as working code.** Don't write a function that returns a hardcoded value, a `TODO`, or a fake success path and present it as done. If something genuinely can't be finished in one pass (e.g. it needs a real device, a real API key, a real root shell to test against), say so explicitly in a comment AND in your response text — never silently stub it and move on like it works.
2. **No mocked data pretending to be real output.** If you don't have a real API response, real sensor data, or a real device capability to test against, either (a) write the integration against the documented/actual API contract and clearly flag "untested against a live [X]," or (b) ask me for a real sample first. Never invent plausible-looking JSON and act like it came from a real call.
3. **No silent fallbacks that mask failure.** If root detection fails, if a permission is denied, if Shizuku isn't running, if an ad SDK callback times out — the code must surface that state (return an error type, throw, log, update UI state) rather than quietly falling through to a default that looks like success.
4. **Every root-method backend must be written against that method's actual real API/shell contract**, not a generic guess. If you (the coding AI) aren't certain of Shizuku's actual API surface, KernelSU's actual daemon socket protocol, or Magisk's actual su invocation pattern, say so and ask before guessing — don't fabricate method signatures that look plausible but don't exist.
5. **State explicitly what is untested.** At the end of each phase, give me a short list: "This was tested/verified against X. This was NOT tested and needs verification on a real device: Y, Z."
6. **No renamed duplicate functions to dodge a bug** — if something's broken, fix the actual function, don't paper over it with a second copy.
7. **Ask before assuming a library, SDK version, or Android API level** if it materially changes how a feature works. A wrong guess here compounds across every later phase.

If you (the coding AI reading this) cannot comply with one of these rules for a specific piece — for example, you genuinely cannot verify Shizuku's binder API without documentation — stop and say exactly that, rather than producing confident-looking code that silently violates rule 4.

---

## 1. Project Summary

Build a universal Android input-mapping platform that converts touch-only games into full gamepad / mouse+keyboard experiences. It must work across root and non-root devices, support screenshot-based visual mapping, and support a shareable JSON config ecosystem. Target: functionally surpass existing tools (Octopus, Panda Gamepad Pro, GameSir X2 companion app) in flexibility, transparency, and cross-method compatibility.

No app name is finalized yet — build without hardcoding a brand name into user-facing strings; use a string resource placeholder (`app_name`) so naming can be swapped later.

---

## 2. Phase 0 — Root/Privilege Abstraction Layer (build this first, everything depends on it)

Build an Android input-injection abstraction layer in Kotlin.

- Define an `InputInjector` interface with: `injectTap(x, y)`, `injectDrag(path, duration)`, `injectKeyEvent(keyCode, action)`, `cleanup()`.
- Implement four backends:
  - `ShizukuInjector` — using Shizuku's actual ADB-shell bridge API. Verify the real Shizuku API surface before writing calls against it; if uncertain, flag it rather than guessing method names.
  - `MagiskInjector` — root shell via `su`, writing to `/dev/input/eventX`. Must handle `su` invocation failure explicitly, not silently no-op.
  - `KernelSUInjector` — detect via KernelSU's actual userspace daemon socket/API; do not assume it behaves identically to Magisk without verifying.
  - `AccessibilityInjector` — non-root fallback using `AccessibilityService.dispatchGesture`.
- Build a `PrivilegeDetector` that probes for each method at startup in priority order: Shizuku → Magisk → KernelSU → APatch → Accessibility. It must return a real detection result (found/not found/version), never a guessed default.
- Provide a factory that returns the correct injector based on detection, with a manual override path for advanced users to force a specific method.
- Make all four backends unit-testable via mockable interfaces, since three of the four cannot be tested without a rooted device — call this out explicitly in your response as untested-without-hardware.

## 3. Phase 1 — Onboarding + Permissions

Build a multi-step onboarding flow in Jetpack Compose with an `OnboardingViewModel`.

Steps:
1. Welcome screen explaining what the app does in plain language.
2. Root-detection results screen, showing which method (if any) was found, with a plain-language explanation of what that method unlocks.
3. Sequential permission requests — Accessibility, `SYSTEM_ALERT_WINDOW` (overlay), `PACKAGE_USAGE_STATS`, storage — each preceded by a rationale dialog explaining why, before the actual system prompt fires.
4. Input-device detection: poll `InputManager.getDeviceIds()` for connected gamepads; fall back to mouse/keyboard mode if none found. This must reflect actual connected hardware, not a static "gamepad detected" placeholder.
5. Entry point into calibration (Phase 2).
6. A short sample-mapping tutorial using a bundled example screenshot.

Every step must be skippable and re-enterable later from Settings — don't build onboarding as a one-time-only flow.

## 4. Phase 2 — Screenshot Mapper + Calibration

**Visual mapper:**
- Compose Canvas-based editor. Input: a screenshot `Bitmap`. Output: `MappingNode(id, xNorm, yNorm, radiusNorm, type, boundKey)` with coordinates normalized 0–1 (resolution-independent).
- Support drag-to-move, pinch-to-resize, tap-to-place-new-node, and a type picker (button / joystick-zone / camera-drag-zone).
- Optional grid-snap overlay.

**Calibration system (`CalibrationManager`):**
- Stick deadzone: sample rest position over ~2s, then max-extension over ~2s, compute real inner/outer deadzone from actual sampled values — not a fixed default presented as "calibrated."
- Trigger range: rest→full-pull sampling, same rule.
- Touch-latency (non-root mode only): measure actual injection-to-render round-trip, report the real measured number.
- Screen-resolution/aspect-ratio calibration so imported configs auto-scale to the current device.
- Recalibration must be accessible any time from Settings, not just onboarding.

## 5. Phase 3 — Game Library + Auto-Launch

- Room database schema: `Game(packageName, displayName, iconUri)`, `ConfigProfile(id, gameId, name, jsonBlob, isDefault)`.
- Query installed launchable apps via `PackageManager`; let the user tag entries as games.
- Tapping a game tile: `startActivity` to launch it, then start a foreground `MappingService` that loads the linked `ConfigProfile`, applies the correct injector backend from Phase 0, and shows the overlay (Phase 7).
- Detect game exit (via `UsageStatsManager` or lifecycle callbacks) and call `injector.cleanup()` + stop the service — this cleanup must actually run, not just be logged as having run.
- Support multiple configs per game with a quick-switch selector.

## 6. Phase 4 — JSON Config Schema + Import/Export/Share

- Define a versioned schema: `schemaVersion`, `controller`, `joystick`, `camera`, `buttons[]`, `settings` — matching the structure already in use for existing per-game configs (controller type/layout, joystick center/radius/deadzone, camera sensitivity/curve, button array with gamepad_button/x/y/radius/mode/role).
- Kotlinx.serialization for serialize/deserialize, with real migration functions for old `schemaVersion` values — not a no-op migration that just bumps the version number.
- Export via share-sheet or file save; import via file picker or paste-JSON.
- Define a real REST API contract for the community share repository: `POST /configs`, `GET /configs?game=&controller=`, rating/upload endpoints. Stub the client networking layer against this exact contract so backend and client stay in sync — don't invent an ad hoc client API that doesn't match the documented server contract.

## 7. Phase 5 — Accessibility Overlay + Crosshair

- Persistent floating overlay via `WindowManager` + `TYPE_ACCESSIBILITY_OVERLAY` (root/Accessibility path) or `TYPE_APPLICATION_OVERLAY` (non-root). Draggable, edge-snapping, optional auto-hide.
- Tap opens a quick-menu: switch config, open live editor, toggle crosshair, open calibration.
- Live editor reuses the Phase 2 node editor but renders over the live game — dragging a node must actually update and persist the active config, not just move a visual element with no save.
- Overlay's touch-interceptable area must be strictly limited to its own icon/menu bounds — verify gameplay touches pass through everywhere else; this is a correctness requirement, not a nice-to-have.
- Separate always-on-top `CrosshairOverlay`: shape library (dot/cross/circle/T/custom bitmap import), size, thickness, color, opacity, outline, x/y center offset, all live-adjustable and saved per-config.

## 8. Phase 6 — VIP, Referrals, Ad-Coin Economy

**This entire phase must be server-authoritative. No client-side trust for anything that affects money or entitlements.**

- Backend tracks per user: `invitedByUserId`, `invitedUserCumulativeHours` (aggregated from real server-received heartbeat pings, never from local storage the client could reset), `successfulReferralCount`, `coinBalance`, `vipExpiryTimestamp`.
- Referral reward: 1 week free VIP once an invited user hits 48 cumulative hours; after the referrer's 5th successful referral, the threshold for subsequent referrals drops to 12 cumulative hours. This calculation must run server-side on real aggregated data.
- Ad-coin system: integrate a rewarded-ad SDK (e.g. AdMob) using **server-side reward verification (SSV)** — not a client-side "ad finished" timer, which is exactly the exploit vector you flagged (multiple ad units firing per click). Award 5–10 coins per SSV-verified view, scaled to actual ad duration. 250 coins = 1 week VIP.
- $4.99/month direct VIP purchase via Play Billing, verified server-side (don't trust a client-reported "purchase successful" flag).
- Explicitly log/flag any case where you cannot verify SSV callback behavior without a live AdMob account — don't fabricate a working SSV integration if it hasn't actually been tested against Google's real callback.

## 9. Phase 7 — Additional Differentiators (build after core phases are stable)

- **Auto-detect HUD elements from screenshot**: basic edge/icon detection to suggest button placements, with user confirm/adjust — must be a real image-analysis pass, not a hardcoded "detected 3 buttons" placeholder.
- **Config diffing**: side-by-side old-config vs new-screenshot comparison so users can drag-correct only the nodes that moved after a game update.
- **Multi-resolution profiles**: device-class presets (phone/tablet/foldable) within one config, using the existing 0–1 normalization.
- **Verified/Official config badges** in the community repo, plus **comments + version history** on shared configs (not just star ratings).
- **Conditional/contextual bindings**: behavior that changes based on an actual in-game state proxy (e.g. pixel-region check), not a fake condition that never evaluates differently.
- **Profile chaining**: config A inherits unset values from config B, resolved at load time — verify the inheritance actually resolves correctly with real nested test configs, not just the trivial case.
- **Macro recorder** with visual timeline scrubbing before save.
- **Turbo/rapid-fire** per button, adjustable Hz.
- **Anti-recoil curve editor**, per-weapon where the game structurally allows pattern compensation — do not claim this works generically across all games; anti-recoil effectiveness is game-specific and must be labeled as such.
- **Per-game anti-cheat safety warning system**, curated or crowd-sourced, shown before applying a config to a flagged game.
- **Panic kill-switch** (tap or shake) that instantly hides overlay and stops injection — verify this actually calls `cleanup()` on the active injector, not just hides the UI.
- **Genre-based onboarding templates** (FPS/MOBA/racing starting layouts).
- **Local unencrypted backup export** separate from cloud, for users who don't want cloud lock-in — this must be a real full-profile export, not partial.

## 10. Naming (unresolved — do not hardcode)

Candidates under consideration: Vectr, AxisForge, NodeMap, InputForge, Calibra, RootShift, Overclick, Crossmap, Ghostbind, Bindwave, Reticle, Controlyst, Bindstack, Synapse Input. Leading candidates: **Controlyst**, **Bindstack**. Do not commit to a name in code — use `app_name` string resource only.

## 11. Reporting Format for Every Phase

When you (the coding AI) finish a phase, structure your response as:

1. **What was built** — real, concrete summary.
2. **What was verified** — how (unit test, logic trace, actual documented API match).
3. **What was NOT verified / needs hardware or account testing** — explicit list.
4. **Any assumption you made that I should confirm** — explicit list, not buried in code comments.

If any of this can't be honestly filled in because something was faked, stubbed, or guessed — say that instead of filling the section in anyway.
