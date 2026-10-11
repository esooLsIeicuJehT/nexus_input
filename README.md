![Nexus Input — Play your way](docs/assets/nexus-input-banner.png)

# Nexus Input

Your controller. Your game. Your layout.

Nexus Input lets you turn your gamepad buttons and sticks into touch controls for Android games. Pick a game, place your controls over a screenshot, and adjust the layout while you play. Each game keeps its own profile, so you can get things feeling right without starting over every time.

The new look is dark glass, cyan light and violet glow. There’s one bottom navigation bar, a cleaner mapping workspace, and small scrollable windows for long reports. Your game stays the focus.

[Download Nexus Input](https://github.com/esooLsIeicuJehT/nexus_input/releases/tag/v1.1.0-dev) · [See the changes](CHANGELOG.md) · [Phone testing guide](docs/DEVICE_TEST_NEON_UPGRADE.md)

## Downloads

**1.1.0-dev is a development candidate.** The Android builds and automated checks pass; the new controller fixes still need an in-game check on both Moto phones.

| Download | What it’s for |
| --- | --- |
| [Nexus Input-debug.apk](https://github.com/esooLsIeicuJehT/nexus_input/releases/download/v1.1.0-dev/Nexus.Input-debug.apk) | An installable debug build for testing the new UI and mapping fixes. |
| [Nexus Input.apk](https://github.com/esooLsIeicuJehT/nexus_input/releases/download/v1.1.0-dev/Nexus.Input.apk) | The installable release build, signed with the same development key as the debug download. |
| [KernelSU companion ZIP](https://github.com/esooLsIeicuJehT/nexus_input/releases/download/v1.1.0-dev/NEXUS_INPUT-KernelSU-Companion-v1.1.0-dev.zip) | The module for rooted KernelSU setups, including the matching WebUI and GitHub updater. |

Already have Nexus installed? Keep your profiles. Export them using **System → System tools → Local profile files → Backup ZIP** before trying a test build. An APK update needs the same signing key as your installed copy. Older GitHub Actions debug APKs used temporary keys, so these downloads may not update them directly. If Android reports a signature mismatch, keep the installed app until your profile backup is safely exported and you have a restore path.

Both release-page APKs share one development signing key; the release build is not debuggable. These are installable development builds, not APKs signed with a recovered older GitHub key. Use the release-page downloads together rather than mixing APKs from separate Actions runs.

## Get started

1. **Set up your backend.** On the rooted Moto G 2026, install the KernelSU companion and reboot. On the non-root Moto G 4G 2025, start Shizuku with wireless debugging and allow Nexus to use it.
2. **Enable input capture and overlays.** Follow the setup guide in the app. Accessibility captures controller input; “Display over other apps” lets the floating mapper appear over your game.
3. **Choose a game and a profile.** Import a screenshot or capture the game, then place each touch target where the game expects it. Tune your sticks and triggers to your liking.
4. **Launch and test.** Use the profile’s Launch action. The floating Nexus button lets you edit, resize and save your controls in-game. Panic remains available if you need to stop mapping and release touches.

For buttons with unusual Android names, select the target and use **Rebind → Learn physical button**. Nexus saves the button event your controller actually sends. This is especially useful for Stadia’s center controls.

## What changed

- Glass panels and neon edges across the app, mapper, floating controls and KernelSU WebUI.
- One bottom navigation, including landscape. Settings fold into sections; diagnostics and logs scroll inside their own windows.
- Better D-pad key/HAT matching and Start, Select, Guide/Home, Capture and Assistant bindings. Both editors can learn physical buttons, including quick taps.
- A real downloadable module update, with checksum and version checks. The updater no longer advertises the stale 0.6 candidate.
- A root CPU frequency fix that restores the previous interval if final verification fails. Opening a WebUI page does not apply a performance preset.

The existing stick timing, camera movement, trigger thresholds, macros, turbo, profile storage and touch cleanup remain in place. Vibration is being investigated separately; controller rumble is not currently implemented by the mapper.

## If a control still won’t work

First check **Devices**. If the button shows an event there, learn it against the intended touch target and try it in the game. If it still fails, collect and copy **System → Diagnostics** after pressing it. The report includes the selected profile, button identities and backend state, so we can follow the event to its touch target.

[The focused checklist](docs/DEVICE_TEST_NEON_UPGRADE.md) covers both Moto setups, center buttons, D-pad diagonals, the floating editor and the updater. Android 14 or newer is needed for global stick capture. On older supported Android versions, activity input testing and key capture remain available.

## Backups and notes

A labeled [pre-upgrade source backup](backups/README.md) is kept in this repo. It restores the tested source from before this work; your phone’s profiles need their own export.

The [two-pass audit](docs/AUDIT_NEON_UPGRADE_2026-10-10.md) records what was found, what changed and which tested behaviors were preserved. The latest complete [Android validation](https://github.com/esooLsIeicuJehT/nexus_input/actions/workflows/android-debug.yml) covers debug and release tests, APK/AAB builds, lint, native packaging and rendered portrait/landscape layouts. Host and WebUI tests cover the module scripts and controls. These checks help catch regressions; they do not replace playing on the two phones.

<details>
<summary>Build and technical notes</summary>

Use Java 17, Gradle 9.3.1, Android SDK 36/36.1, NDK 27.0.12077973 and CMake 3.22.1.

```sh
python3 scripts/check_native_boundary.py
python3 scripts/check_architecture_boundary.py
python3 -m unittest discover -s scripts/tests -v
node --test scripts/tests/webui.test.js
gradle --no-daemon -PunsignedRelease=true :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:lintDebug :app:lintRelease
python3 scripts/verify_android_artifacts.py
python3 scripts/package_release.py
```

The release APK directly produced by `-PunsignedRelease=true` is a build-validation artifact and cannot be installed. The APKs on this candidate's release page are subsequently signed with the same retained development key and verified with `apksigner`. The release APK remains non-debuggable; development signing does not establish hardware acceptance. Signing material stays outside the public repo and release assets.

For your own production signing, use the existing keystore and alias `upload` with `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD`, or configure `KEYSTORE_BASE64`, `STORE_PASSWORD` and `KEY_PASSWORD` for the [signed GitHub workflow](.github/workflows/android-signed-release.yml). The GitHub signing secrets are currently unconfigured. Keep those secrets out of the repo and chat.

App identity remains `com.inputmapper.platform`, the Room database remains `controlyst_database`, and the module ID remains `gamepad.pro.root`. Version information comes from `version.properties`. Existing profiles and migrations are retained. See [source lineage](docs/SOURCE_LINEAGE.md) and [release notes for builders](docs/RELEASE_V1.md).

Root performance controls live in the KernelSU WebUI. Thermal protections stay intact; active ZRAM resizing and unverified device-specific presets remain unavailable. APatch remains unverified.

</details>
