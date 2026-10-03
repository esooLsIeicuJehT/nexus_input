# NEXUS INPUT 0.6.2-dev-hardening

This source revision focuses on repairing the Shizuku/Sui authorization experience and making the mapper runtime fail more transparently.

Highlights:

- Shizuku/Sui binder and permission state are separated.
- Blocked Shizuku authorization is surfaced and links back to the manager rather than looping permission requests.
- KernelSU remains usable without Shizuku authorization.
- Read-only system self-check can be copied for debugging.
- Saved game profiles are validated before mapper startup.
- Profiles can force a backend or use Auto/Compatibility mode.
- Auto mode may fail over to another real available backend; forced modes do not silently fall back.
- KernelSU module updater requires and verifies SHA-256.

The current full source archive is distributed alongside this revision while the repository is being expanded from the initial KernelSU/update skeleton into the canonical browsable Android source tree.
