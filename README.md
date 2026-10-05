[![Android v1 debug and release validation](https://github.com/esooLsIeicuJehT/nexus_input/actions/workflows/android-debug.yml/badge.svg)](https://github.com/esooLsIeicuJehT/nexus_input/actions/workflows/android-debug.yml)



# NEXUS INPUT 1.0.0 candidate

Android gamepad-to-touch mapping with per-game profiles, real controller sampling, screenshot mapping, an in-game editor, crosshair and presented-frame overlays. This branch is a release candidate; CI compilation does not establish hardware verification or production acceptance.

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

[Android validation workflow](.github/workflows/android-debug.yml) uploads a debug APK, explicitly **unsigned** release APK/AAB, KernelSU ZIP, test/lint reports and Room schemas. [Branch CI runs](https://github.com/esooLsIeicuJehT/nexus_input/actions?query=branch%3Afeature%2Fnexus-v1-finish) are the build evidence. Unit tests use host/Robolectric transport fixtures; they do not certify Android injection, SELinux or hardware.

Global gamepad motion capture requires Android 14+. Android 7–13 supports Activity controller testing and key capture, but global stick profiles are rejected rather than silently losing motion. Accessibility screenshot capture requires Android 11+; image import remains available on older supported versions. Accessibility injection has explicit gesture and simultaneous-contact limitations.

## Upgrade and release gates

The package remains `com.inputmapper.platform`, the existing Room filename remains `controlyst_database`, and the module ID remains `gamepad.pro.root`. App and module share `version.properties`: version 1.0.0, code 1000. Legacy profiles from the exact preserved 0.6.2 contract migrate transactionally into Room without clearing the original preferences. Imported enabled intent never auto-arms mapping. See [source lineage](docs/SOURCE_LINEAGE.md).

Release signing uses the **existing** keystore and alias `upload`, with `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD`. No replacement key is generated. The manual signed workflow requires secure GitHub environment secrets; no signed APK/AAB can be claimed until it passes. See [release gates](docs/RELEASE_V1.md).

The [editable Figma file](https://www.figma.com/design/0nup9fG0xfbIa2VTuXKvMh) is the design reference. Its connector is rate-limited and trustworthy per-screen node links are unavailable. Current screens are implemented, but exact Figma visual acceptance remains pending.

Follow the [device test checklist](docs/DEVICE_TEST_CHECKLIST_V1.md) before production publication. `scripts/device_test_v1.py` collects real read-only ADB observations and initializes every manual case as `NOT_RUN`; it never manufactures a hardware pass. Device-specific thermal writes, active ZRAM resizing, compaction and performance presets remain unsupported until a verified device adapter/recovery path exists.
