# Build / verification status — NEXUS INPUT 0.6.2

## Verified on target hardware in prior revisions

- Termux ARM64 CMake/Clang JNI build: PASS
- Android debug APK assembly: PASS
- KernelSU RootService uid 0: PASS
- `/dev/uinput` virtual touchscreen creation: PASS
- KernelSU injected real Android target tap: PASS
- Shizuku/Sui root UserService uid 0: PASS
- Shizuku injected real Android target tap: PASS
- Stadia controller discovery + foreground button/axis telemetry: PASS
- AccessibilityService global joystick capture: PASS
- Controller calibration capture: PASS
- KernelSU companion WebUI diagnostics: PASS

## Implemented, needs target-device verification in 0.6.2

- Shizuku blocked/requestable/granted state handling.
- Sui binder detection independent of manager APK presence.
- Backend connection cascade: Shizuku -> KernelSU -> Magisk -> Accessibility in Auto mode.
- Runtime alternate-backend recovery after a real injection failure.
- Accessibility foreground-state restore after service recreation.
- Game-profile validation and preflight rejection of unsafe duplicate slots/bindings.
- Per-profile backend selector.
- Read-only full Self Check and clipboard report export.
- Inset-safe Setup + Diagnostics UI.

## Explicitly not claimed finished yet

- Native evdev capture daemon.
- `/dev/uhid` virtual mouse/keyboard/gamepad output.
- Persistent KernelSU native mapper daemon + dedicated IPC protocol.
- Layers/phases/macros/turbo engine.
- Modern LSPosed module entry/hooks/Remote Preferences integration.

Those remain future phases rather than placeholders pretending to work.
