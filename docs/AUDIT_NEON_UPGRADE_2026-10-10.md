# Two-pass audit before the neon/glass upgrade

Baseline: main `583a573`, backed up in `backups/` before any implementation edit. SHA-256 verified and archive inspected (287 entries). Preserve package `com.inputmapper.platform`, Room filename/migrations, module ID `gamepad.pro.root`, original signing identity, normalized geometry, per-game profiles and all mapper actions.

## Pass 1 — baseline code and existing verification

Both architecture checks, all 15 Python tests and all 12 WebUI tests pass. These verify host logic; they are not measurements on either Moto phone.

* Public `update.json` still publishes 0.6.0-dev/code 600 although source/module is 1.0.0/code 1000. There are no published GitHub releases. Newer source does not imply a downloadable release. Supply a real canonical ZIP and matching SHA manifest, and label the installed/public versions accurately without downgrading.
* System renders the entire diagnostics JSON as an unbounded Text inside the page scroll; collecting a report makes the page enormous.
* Home repeats primary routes as a second Quick access navigation row. Landscape Mapper hides the sole bottom nav, and the mapper stacks two profile/header strips above its canvas.
* Device live input lists standard axes plus duplicate AXIS_N values vertically; its unbounded strip can consume the screen after a controller moves.
* Selected mapper inspector has numerous sliders without a bounded viewport, potentially leaving no canvas visible.
* Center input labels expose Start/Select/Guide but no Capture/Assistant. Stadia scan fallback only covers the four previously measured keys.

## Pass 2 — input, updater and root-control boundaries

* HAT matching excludes every scan-bound node. A D-pad learned/saved with only a scan code can work as a key but never match Stadia HAT motion. Use a consistent canonical logical match for D-pad/trigger nodes with raw scan fallbacks while preserving explicit supported keycode identity.
* Standard aliases do not recognize controller-origin MENU/BACK/HOME/ASSIST/SYSRQ equivalents. Accept such aliases only after controller device/source classification; ordinary phone/keyboard keys must pass through. Do not invent an unknown firmware scan code.
* Existing key/HAT ownership handling correctly prevents duplicate down and early HOLD release; preserve it. Preserve LS one-event position updates, RS dt-scaled 8ms camera loop and trigger hysteresis. Keep tested root launch/focus grace and cleanup retry behavior untouched.
* Activity tester only checks event sources, while service/runtime check device sources too. Keyboard-source controller center keys can activate Android UI while testing. Apply the same classifier in the Activity.
* Root frequency interval can fail final pair read-back after both writes and skip rollback; attempt and verify restoration in that path too. Thermal controls and active ZRAM resizing remain read-only/unsupported because no device-specific recovery adapter is verified.
* Update requests share fixed temp paths without a cross-process lock. Serialize checks/staging, remove partial download leftovers and check the ZIP's actual module ID/version before staging, in addition to SHA-256.

## Research and evidence

Read the user's K2er 0.3.880, Mantis Pro 3.4.8 and Panda 9.2 static reports from Drive. K2er/Mantis use privileged event readers plus framework/uinput injection, confirming that capture and touch injection are separate problems. Their recovered code does not certify Moto permissions, and their permissive socket/file patterns should not replace Nexus's existing Binder architecture.

Primary references: [Android controller actions](https://developer.android.com/games/sdk/game-controller/controller-input), [AOSP key layouts](https://source.android.com/docs/core/interaction/input/key-layout-files), [AOSP Generic.kl](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/data/keyboards/Generic.kl), [Android KeyEvent](https://developer.android.com/reference/android/view/KeyEvent). Android recommends supporting both HAT axes and D-pad keys. Generic.kl defines BUTTON_MODE (Linux 316), SYSRQ (99), ASSIST (583), VOICE_ASSIST (582). KEYCODE_HOME can be consumed by system policy; a label/alias cannot make an undelivered event available. Unknown center-button scan values must be learned from actual device input rather than guessed.

## Combined implementation and acceptance

Use static translucent panel gradients and edge glows for the APK, real backdrop-filter frosted glass for WebUI, and matching native overlay drawables. Avoid per-frame blur/animation in gameplay. Keep one bottom navigation, section accordions and independent bounded report/inspector/log scroll areas. Preserve every feature by placing its controls in expandable groups.

Fix input identity matching and add live physical key learning for nonstandard delivered events. Explain reserved/undelivered Home/Assistant behavior in the tester. Validate host and Android regression tests, compile/lint, inspect responsive WebUI screenshots, package a real update artifact, then document the exact remaining physical-device checks. Neither rooted Moto G 2026 nor non-root Moto G 4G 2025 is connected to this workspace; do not claim in-game acceptance.

## User clarification and follow-up

The user confirmed during this work that **all the affected buttons appear in the Devices event area** on their setup. Therefore, focus this repair on logical/raw binding equivalence and touch routing; do not characterize their controller as failing capture. Added compatibility for Android-prefixed saved labels, equivalent MENU/START and BACK/SELECT translations, and an existing saved scan when a firmware reports an unknown/untranslated key. Added actual routing logs for unmatched center/D-pad targets and matched backend requests. No physical-device success is inferred from these fixes.

## Post-change sweep and validation

The first full [Android validation run](https://github.com/esooLsIeicuJehT/nexus_input/actions/runs/38044270878) passed all 132 tests in each of debug and release, APK/AAB assembly, lint (zero errors/fatals), identity and four-ABI packaging verification. The [follow-up run](https://github.com/esooLsIeicuJehT/nexus_input/actions/runs/38044701462) also passed after retaining physical presses through release and adding selected-profile routing observations. A quick press/release can be coalesced before Compose renders; learning now keeps the last non-repeated down event, and unusable unknown-key/zero-scan events cannot be learned. A Room round-trip regression verifies learned vendor scan identity, touch slot and label survive saving, and an explicit logical rebind clears the old physical identity.

Lint exposed corrupted legacy launcher WebP resources: several report impossible dimensions, and none can be decoded by an independent image decoder. Replaced them with references to the existing vector artwork, retaining adaptive icons and app identity. Added rendered portrait/landscape shell fixtures checking each bottom route appears exactly once and stays visible on Home, System and Mapper. Their review captures are uploaded with CI test reports; they are synthetic Android layouts, not Moto screenshots.

The module ZIP digest, shared version and embedded module ID were checked. The WebUI was inspected at 412×915, 915×412 and 1280×800 on all five routes: one visible page, one navigation, no horizontal overflow or JavaScript errors. Full logs and diagnostics retain their contents in bounded windows.

The [existing signing workflow](https://github.com/esooLsIeicuJehT/nexus_input/actions/runs/38044706958) stopped because `KEYSTORE_BASE64` is not configured (the signing password variables are also empty). A production upgrade APK cannot be signed until the original signing material is supplied through the existing GitHub secrets or local build. Debug APKs are test artifacts; never uninstall the installed app to work around a signature mismatch without separately preserving its profiles. Follow [the focused phone checklist](DEVICE_TEST_NEON_UPGRADE.md) for physical acceptance. Source changes do not establish in-game delivery or measured lag.
