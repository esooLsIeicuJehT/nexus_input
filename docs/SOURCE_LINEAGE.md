# NEXUS INPUT source lineage

The Android application source under `app/`, plus the root Gradle project files,
was imported byte-for-byte from `esooLsIeicuJehT/Controlyst` at commit
`616d8cc4eacfb2efac7e6f1578933026d401739f`.

NEXUS INPUT-specific KernelSU companion files remain under `kernelsu-module/`,
release packages remain under `releases/`, and NEXUS release/hardening notes
remain under `docs/`.

The imported Android tree is the verified recoverable source lineage available in
GitHub. Later NEXUS INPUT source ZIPs created outside GitHub are not represented as
byte-identical files here unless separately committed from those original archives.
Do not treat this lineage note as proof that every 0.6.x runtime change documented
elsewhere in this repository is already present in the imported Android tree.

## v1 candidate baseline and preserved migration contract

Work on `feature/nexus-v1-finish` started from Nexus `main` commit
`c988ce7698ebc369e74074bf4beeb4bcdd8e87a1`. Files were recovered through the
GitHub connector and checked against that commit's Git tree; the shell transport
could not perform `git pull` under the managed network proxy. Remote feature
commits are published through the GitHub connector without rewriting main.

The authoritative legacy profile store is
[GameProfileStore.kt at f8fd74b5686c453af53bd09f125dfa9fe7fbe237](https://github.com/esooLsIeicuJehT/nexus_input/blob/f8fd74b5686c453af53bd09f125dfa9fe7fbe237/app/src/main/java/com/inputmapper/platform/game/GameProfileStore.kt),
preserved on `recovery/device-0.6.2-hardening`. That exact file was read for the
migration implementation, rather than inferring its schema from newer models.

Preferences `nexus_game_profiles_v1` use profile IDs as keys and schema-1 JSON
strings as values. The importer preserves touch mappings, explicit scan fallback,
stick actions/axes/settings/slots, controller reference, preferred backend,
package/name and saved timestamp. `nexus_mapper_state` supplies `active_profile`
and `mapping_enabled`. Migration journals are transactional and idempotent;
original preferences remain untouched. Stored enabled intent is not permission
to start injecting after an upgrade.

Package `com.inputmapper.platform`, Room filename `controlyst_database`, module ID
`gamepad.pro.root` and root state path `/data/adb/gamepad-pro` remain compatible.
Version-code compatibility alone does not prove signing compatibility: the
original application's signing certificate must match the candidate before an
in-place upgrade can be verified. Historical device-verification statements in
0.6.x documents are historical evidence only, not verification of this branch.
