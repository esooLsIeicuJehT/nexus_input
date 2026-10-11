# Pre-upgrade backup — 2026-10-10

`nexus_input-before-ui-upgrade-2026-10-10.tar.gz` is a complete Git-tracked source snapshot taken **before implementation changes**, from main commit `583a573`. It includes the APK, JNI, module, release ZIPs, device notes and tests. Git history remains available separately.

Verify with `sha256sum -c backups/SHA256SUMS` from the repository root. Restore into a separate directory with `tar -xzf backups/nexus_input-before-ui-upgrade-2026-10-10.tar.gz`; the archive uses its own top-level folder. This is a repository backup; it does not contain live phone profiles or a signing keystore.
