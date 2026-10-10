[![Android v1 debug and release validation](https://github.com/esooLsIeicuJehT/nexus_input/actions/workflows/android-debug.yml/badge.svg)](https://github.com/esooLsIeicuJehT/nexus_input/actions/workflows/android-debug.yml)



# NEXUS INPUT 1.1.0-dev

Android gamepad-to-touch mapping for Android with per-game profiles, screenshot mapping, in-game editing, controller diagnostics, crosshair/presented-frame overlays, Shizuku support and a KernelSU `/dev/uinput` backend. Version 1.0.0 is under active device validation; CI success does not by itself establish production hardware acceptance.

## Neon glass upgrade candidate

The current source includes one persistent bottom navigation, glass/neon styling across the APK, screenshot mapper, floating editor and KernelSU WebUI, compact expandable settings, bounded diagnostic/log windows and controller-binding repairs. See [the two-pass audit](docs/AUDIT_NEON_UPGRADE_2026-10-10.md), [the verified pre-change backup](backups/README.md) and [review previews](docs/previews/phone-overview.png).

The module updater's public development channel now points to the real checked-in [1.1.0-dev ZIP](releases/NEXUS_INPUT-KernelSU-Companion-v1.1.0-dev.zip) with matching `update.json` SHA-256. Source version and published asset are kept distinct; this is a **device-test candidate**. The APK signing certificate and profile database identities are retained. Install an APK upgrade only with the existing certificate; never uninstall/clear profiles merely to install a debug build.

The user reports that all Stadia buttons appear in the Devices event area. The fixes target saved-input matching and touch routing: prefixed Android labels, equivalent center-key translations, untranslated-key scan fallbacks and D-pad HAT mappings with scan-bound nodes. New test cases exercise these through both root and Shizuku interfaces. Actual in-game acceptance on rooted Moto G 2026 and non-root Moto G 4G 2025 remains required; this workspace has no connected phone.

## Current status

CI run **#149** passed the complete Android v1 validation workflow for the latest Nexus Input candidate. It validated commit `d8c9b0a600826f1f908fd0d539291a8be345c01a`, including the controller/runtime fixes from PRs #12 and #13 plus the user-approved Nexus cyan/violet APK and KernelSU WebUI visual pass from PR #14. PR #14 merged into `main` as `738ff407f0a03ece7704717cb14554dc2202afad`.

Real-device diagnostics on the rooted moto g 2026 established that KernelSU, libsu RootService, JNI and `/dev/uinput` can successfully create and register the virtual touchscreen and keyboard. The previously observed failure was in Nexus lifecycle handling: the prepared backend was being released during the game's splash/activity/orientation handoff. The merged fix removes that preparation race, ignores Nexus's own overlay/activity package as a false foreground exit, retains a prepared backend for the armed session through transient launch focus changes, and uses an explicit foreground-exit grace while still gating injection on the target game's foreground state.

Stadia Controller rev. A input is grounded in measured Android events rather than guessed mappings. Observed values include Start key 108/scan 315, Select key 109/scan 314, L3 key 106/scan 317, R3 key 107/scan 318, right-stick axes 11/14, and D-pad HAT axes 15/16. The runtime keeps standard Android keycode handling and adds a device-specific scan fallback only for the measured Stadia vendor/product pair. Controller diagnostics also deduplicate the same physical key event when Android delivers it through both the Activity and Accessibility observation paths.

The mapper exposes visible +/- target resizing, walk/run radius and threshold tuning, right-stick H/V sensitivity, smoothing and fast-turn tuning, trigger press/release hysteresis, and matching in-game resize controls. Right-stick camera motion uses continuous dt-scaled movement with smoothing and safe display-boundary restart behavior instead of resetting whenever the pointer merely reaches the mapping radius. In-game controls respect display cutouts/system bars without shifting the full-screen mapping coordinate system.

The APK and KernelSU WebUI now share the approved Nexus visual language: near-black/navy panels, luminous cyan edges, violet accents and the updated Nexus splash hierarchy. This visual pass did not replace live controller/backend/profile state with mock data.

The Devices tester records real controller motion/key events including all reported axes, HAT X/Y, trigger representations, key code, scan code, source, device ID and a rolling raw event log. Backend selection fails visibly: if no real backend is available, the UI reports that state instead of silently pretending Accessibility was selected.

**Still requiring real-device acceptance:** rooted moto g 2026 + Delta Force backend persistence after the lifecycle fix, final Stadia Start/Select/D-pad/R3 behavior in-game, subjective right-stick tuning, and Shizuku in-game delivery after the combined upgrade. CI success establishes source/build/test validation, not those physical-device results.

## Two products

The Android APK owns input mapping and talks to the privilege backend. KernelSU uses the existing libsu RootService/AIDL and JNI `/dev/uinput` architecture. JNI contains raw uinput primitives. Shizuku uses its real UserService/AIDL contract. APatch remains **UNVERIFIED and fail-closed**.

CPU/devfreq, thermal and memory observations, guarded root tuning, module management and the GitHub updater belong exclusively to `kernelsu-module/webroot/`. The APK exposes backend readiness and an **Open KernelSU WebUI** action. Community, VIP, cloud account and performance-tuning navigation are absent from the production APK.

## Build and tests

Use Java 17, Gradle 9.3.1, Android SDK 36/36.1, NDK 27.0.12077973 and CMake 3.22.1. CI installs the required toolchain and builds JNI for arm64-v8a, armeabi-v7a, x86 and x86_64.

```sh
python3 scripts/check_native_boundary.py
python3 scripts/check_architecture_boundary.py
python3 -m unittest discover -s scripts/tests -v
node --test scripts/tests/webui.test.js
gradle --no-daemon -PunsignedRelease=true :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:lintDebug :app:lintRelease
python3 scripts/verify_android_artifacts.py
python3 scripts/package_release.py
```

[Android validation workflow](.github/workflows/android-debug.yml) uploads a debug APK, explicitly **unsigned** release APK/AAB, KernelSU ZIP, test/lint reports and Room schemas. GitHub Actions on `main` and pull-request branches are the build evidence. CI #149 validated the latest combined candidate: root lifecycle, Stadia input, controller-event deduplication, mapper UX, camera/trigger behavior, backend-state handling, Nexus APK styling and KernelSU WebUI regression coverage. Unit tests use host/Robolectric transport fixtures; they do not certify SELinux behavior, physical-controller delivery or game-visible injection.

Global gamepad motion capture requires Android 14+. Android 7–13 supports Activity controller testing and key capture, but global stick profiles are rejected rather than silently losing motion. Accessibility screenshot capture requires Android 11+; image import remains available on older supported versions. Accessibility injection has explicit gesture and simultaneous-contact limitations.


## Latest test APK

CI **#149** produced the latest installable debug-signed test APK from commit `d8c9b0a600826f1f908fd0d539291a8be345c01a`. The GitHub Actions artifact is named `nexus-input-debug-v1`. This is a **debug/test candidate**, not a production-signed release. The corresponding CI run passed; rooted moto g 2026 + KernelSU + Stadia + Delta Force acceptance is still required before production publication.

## Upgrade and release gates

The package remains `com.inputmapper.platform`, the existing Room filename remains `controlyst_database`, and the module ID remains `gamepad.pro.root`. App and module share `version.properties`: development version 1.1.0-dev, code 1100. Legacy profiles from the exact preserved 0.6.2 contract migrate transactionally into Room without clearing the original preferences. Imported enabled intent never auto-arms mapping. See [source lineage](docs/SOURCE_LINEAGE.md).

Release signing uses the **existing** keystore and alias `upload`, with `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD`. No replacement key is generated. The manual signed workflow requires secure GitHub environment secrets; no signed APK/AAB can be claimed until it passes. See [release gates](docs/RELEASE_V1.md).

The [editable Figma file](https://www.figma.com/design/0nup9fG0xfbIa2VTuXKvMh) is the design reference. Its connector is rate-limited and trustworthy per-screen node links are unavailable. Current screens are implemented, but exact Figma visual acceptance remains pending.

Follow the [device test checklist](docs/DEVICE_TEST_CHECKLIST_V1.md) before production publication. `scripts/device_test_v1.py` collects real read-only ADB observations and initializes every manual case as `NOT_RUN`; it never manufactures a hardware pass. Device-specific thermal writes, active ZRAM resizing, compaction and performance presets remain unsupported until a verified device adapter/recovery path exists.
