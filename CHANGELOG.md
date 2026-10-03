# Changelog

## 0.5.1-dev

- Add KernelSU official `updateJson` metadata pointing at this GitHub repository.
- Add **Check GitHub** and **Install update** controls to the KernelSU WebUI.
- Download module updates from GitHub and verify the ZIP SHA-256 before installation.
- Stage updates with KernelSU's real `ksud module install <zip>` CLI contract.
- Surface downloader, hash verification, `ksud`, and installation failures instead of silently falling back.
- Require a reboot after KernelSU stages an update.

## 0.5.0-dev

- NEXUS INPUT KernelSU companion control plane.
- APK runtime state, `/dev/uinput`, `/dev/uhid`, SELinux, process, controller and boot diagnostics.
