# NEXUS INPUT v1 release gates

## Evidence labels

**VERIFIED** means the named observation was made in this implementation session, with its scope stated. **INFERRED** means source reasoning or intended behavior, not a device result. **UNKNOWN** means a required environment/account/device check has not run. Instructions and acceptance criteria below are procedures, not claims that they passed.

## Build evidence and candidate status

**VERIFIED — CI:** [run 37273471652](https://github.com/esooLsIeicuJehT/nexus_input/actions/runs/37273471652), commit `2c5d14943d8c1a5a796a2e32970f025026331ae4`, completed successfully. The job's verbatim output includes:

```text
Ran 8 tests in 0.530s
OK
# tests 9
BUILD SUCCESSFUL in 3m 52s
app-debug.apk: existing application ID, shared v1 version and all 4 JNI ABIs verified
app-release-unsigned.apk: existing application ID, shared v1 version and all 4 JNI ABIs verified
```

The completed job ran debug/release unit tests, `assembleDebug`, explicitly unsigned `assembleRelease`, `bundleRelease`, `lintDebug`, `lintRelease`, native/APK architecture guards, module tests, WebUI contract tests, identity checks and canonical ZIP packaging. This is evidence for that exact commit. Follow the [branch Actions runs](https://github.com/esooLsIeicuJehT/nexus_input/actions?query=branch%3Afeature%2Fnexus-v1-finish) for subsequent commits; do not reuse a previous result as proof of a newer revision.

**VERIFIED — report artifact:** XML in `nexus-test-reports` (artifact `11329762076`) records `testDebugUnitTest: tests=82 failures=0 errors=0 skipped=0` and `testReleaseUnitTest: tests=82 failures=0 errors=0 skipped=0`. Both lint XML reports record `Error=0 Fatal=0 Warning=81`. Passing lint does not mean there were no warnings. Notably, Shizuku's privileged injection uses non-SDK reflection and requires Android-version-specific hardware verification. Subsequent source commits replace fixed-rate scheduling, remove the unused second crosshair service and guard notification settings on older Android; their CI result must be checked separately.

**UNKNOWN — release acceptance:** No rooted or non-root Android execution, production signing or exact Figma comparison was performed in the container. A production-ready claim requires those gates. APatch remains unverified and refuses injection. Historical 0.6.x hardware statements are not this candidate's evidence.

## Implemented flow and limitations

**VERIFIED — source and CI:** The APK owns profile validation/storage, controller events/calibration, mapping scheduling, screenshots, the in-game editor, crosshair, presented-frame overlay and backend diagnostics. Root injection remains libsu RootService/AIDL plus raw JNI uinput. Shizuku remains real UserService/AIDL; tests exercise the contract with explicit test transports, not a privileged Android process.

**VERIFIED — source guard:** APK CPU/GPU/thermal/ZRAM/sysctl controls, Community/VIP routes, cloud account placeholders and performance-profile inheritance have been removed. The retained module action opens an installed KernelSU manager and explains selecting the module WebUI. Its installation probe only reads module readiness.

**INFERRED — device behavior to verify:** The module WebUI uses actual KernelSU bridge callbacks and root scripts. Exposed controls are CPU governor/frequency interval, devfreq governor and `vm.swappiness`; each write requires battery safeguards and exact read-back. CPU topology, thermal raw readings, memory/ZRAM and battery are observations. Thermal configuration, active ZRAM resizing, memory compaction, scheduler configuration and device-specific performance presets are **unsupported**. They must not be advertised as finished controls. A verified adapter, safe recovery and hardware tests are required to add them.

**VERIFIED — enforced capability limits:** Global controller motion capture requires Android 14+. Screenshot capture requires Android 11+; real image import supports older APK targets. Logical touch slots are 0–31 with a maximum of 16 simultaneous Android contacts. Accessibility cannot provide root-equivalent held multi-touch; unsupported profiles fail explicitly. Stored sprint-lock, anti-recoil and mouse DPI behavior are preserved on import but rejected for runtime use, with a visible option to disable unsupported values. FPS is derived from actual SurfaceFlinger presentation timestamps, never guessed from display refresh rate; unavailable, stale or permission-denied readings show errors.

## Signing and compatible upgrade

**UNKNOWN:** The original signing key and GitHub signing environment are unavailable in this session. No signed release APK/AAB was produced. A debug APK cannot prove upgrade compatibility with an existing release installation; an unsigned APK is not installable as that upgrade.

1. Configure the GitHub `android-release` environment securely with `KEYSTORE_BASE64`, `STORE_PASSWORD` and `KEY_PASSWORD` using the existing keystore. Do not post private key material or passwords in chat or commit them. Alias must remain `upload`. The workflow writes `KEYSTORE_PATH` to its temporary decoded keystore.
2. Confirm the keystore certificate is compatible with the installed application. Compare the original and candidate APK certificates with `apksigner verify --print-certs`; if Google Play App Signing is used, distinguish the upload certificate from the app-signing certificate used on devices.
3. Run [android-signed-release.yml](../.github/workflows/android-signed-release.yml) on the accepted commit. Missing/incomplete signing fails; it never generates a replacement key. Retain the actual APK signature, AAB verification, checksums, lint and test reports.
4. Complete `UPGRADE-01` from the device checklist using the original installed APK, its existing profiles and the signed candidate. Do not uninstall or clear data to make an upgrade appear successful.
5. Run [publish-kernelsu.yml](../.github/workflows/publish-kernelsu.yml) with the successful signed run ID for the **same commit**. It stages a draft containing APK, AAB, module ZIP, checksums and its staged updater manifest. It does not publish or rewrite main's public manifest.
6. Publish only after hardware, Figma, upgrade and device recovery evidence is reviewed. Then make the public `update.json` refer to the actual published asset and observed SHA-256. A staged URL is not evidence that an asset is publicly downloadable.

## Design gate

**UNKNOWN — exact visual match:** The supplied editable file is https://www.figma.com/design/0nup9fG0xfbIa2VTuXKvMh. Figma returned the account's tool-call limit. No trustworthy individual node URLs were supplied or captured. The current screen implementation inventory is in [NEXUS_UI_IMPLEMENTATION.md](NEXUS_UI_IMPLEMENTATION.md); it does not certify a Figma match.

Restore account access, capture actual node URLs and compare every supplied portrait/landscape screen on Android plus the root WebUI in its real host. Record font scale, density, navigation insets, cutouts and permission/error states.

## Hardware gate

**UNKNOWN:** Every functional hardware case starts `NOT_RUN`. Use [DEVICE_TEST_CHECKLIST_V1.md](DEVICE_TEST_CHECKLIST_V1.md) and the read-only [collector](../scripts/device_test_v1.py). Command exit 0 is an observation, not proof that touch mapping works. Any failed cleanup, binder death, unavailable API, invalid screenshot, denied permission or read-back mismatch must have UI/log evidence and must never be recorded as a successful hardware result.
