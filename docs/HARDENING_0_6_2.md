# 0.6.2 hardening notes

## Shizuku / Sui

The permission flow follows the official Shizuku API contract:

1. Binder must be alive before Shizuku calls.
2. `checkSelfPermission()` granted -> ready.
3. `shouldShowRequestPermissionRationale()` true while denied -> do not loop `requestPermission()`; direct the user to Shizuku's authorization UI.
4. Otherwise `requestPermission()` can be issued.

Sui may provide the same binder without the Shizuku manager package, so binder state is authoritative when alive.

## Runtime backend failover

Auto/Compatibility mode attempts only backends reported AVAILABLE and implemented by this build. Connection failures are accumulated and surfaced. If the first live backend later returns a real injection failure, Auto mode may attempt another available backend once. Explicit/forced backend profiles never silently change backend.

## Self Check

Self Check is intentionally read-only. It does not create uinput devices, bind UserServices for injection, or send input. It reports live state so debugging does not itself change the condition being diagnosed.
