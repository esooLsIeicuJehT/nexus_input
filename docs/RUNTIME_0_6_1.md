# NEXUS INPUT 0.6.1 safe-launch runtime

This revision hardens mapped-game launch behavior.

- Mapped games receive a 3.5 second quiet startup grace period.
- No NEXUS bubble and no injection backend is created during that grace period.
- Runtime auto mode prefers Shizuku/Sui framework injection before KernelSU uinput to avoid hot-adding a virtual input device during game launch.
- KernelSU remains available as the native fallback and for explicit profile selection later.
- The quick bubble appears only after the backend is ready and the mapped package is still foreground.
- Window changes inside the same game no longer force injector recreation. Geometry changes are handled through Android configuration changes.
- Launching a game from Profiles no longer redraws the Profiles activity immediately after startActivity.

This change is based on an observed regression where 0.6.0 activation coincided with a mapped game black-screening and returning to NEXUS. The exact game-side reason is not claimed without a process log; the revision removes the two intrusive actions introduced at foreground transition: immediate overlay creation and immediate uinput creation.
