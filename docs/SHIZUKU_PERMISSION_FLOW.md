# Shizuku / Sui permission flow

NEXUS INPUT follows the Shizuku API permission contract instead of assuming that binder availability means authorization.

1. Wait for an alive binder.
2. If `Shizuku.checkSelfPermission()` is granted, the backend is ready.
3. If `Shizuku.shouldShowRequestPermissionRationale()` is true, authorization is treated as blocked and NEXUS directs the user to Shizuku's Authorized applications screen instead of repeatedly requesting permission.
4. Otherwise NEXUS calls `Shizuku.requestPermission(requestCode)` and listens for the result.
5. Root-backed Shizuku/Sui UID 0 and ADB-backed Shizuku UID 2000 are both accepted.
6. An alive Sui binder does not require the standalone Shizuku manager APK to be installed.

The normal mapper can operate through KernelSU without Shizuku. Shizuku/Sui remains an independent compatibility backend.
