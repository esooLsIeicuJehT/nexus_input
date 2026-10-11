# Pre-upgrade backup — 2026-10-10

`nexus_input-before-ui-upgrade-2026-10-10.tar.gz` is a complete Git-tracked source snapshot taken **before implementation changes**, from main commit `583a573`. It includes the APK, JNI, module, release ZIPs, device notes and tests. Git history remains available separately.

Verify with `sha256sum -c backups/SHA256SUMS` from the repository root. Restore into a separate directory with `tar -xzf backups/nexus_input-before-ui-upgrade-2026-10-10.tar.gz`; the archive uses its own top-level folder. This is a repository backup; it does not contain live phone profiles or a signing keystore.

`nexus-input-before-stadia-followup-2026-10-11.tar.gz` preserves the released 1.1.0-dev source plus installation-guide updates at commit `ad75bf3`, before the follow-up controller edits. This archive has files at its root: create an empty restore directory and extract with `tar -xzf ARCHIVE -C RESTORE_DIRECTORY`. It contains tracked repository files, including the earlier backup, and no private signing key or phone data.
