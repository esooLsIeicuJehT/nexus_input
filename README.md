# NEXUS INPUT

NEXUS INPUT is an Android controller-to-touch / mouse / keyboard mapper project with KernelSU and Shizuku/Sui backends.

## KernelSU companion updates

The KernelSU companion uses KernelSU's official `updateJson` mechanism and also exposes a WebUI **Check GitHub / Install update** control.

Update manifest: `update.json`

Module source: `kernelsu-module/`

Published module ZIPs used by the updater: `releases/`

The WebUI updater downloads the ZIP, verifies its SHA-256, then stages it using the real KernelSU CLI contract:

```sh
ksud module install <zip>
```

A reboot is required after KernelSU stages an update.
