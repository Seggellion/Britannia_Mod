# One combined fence/plaster recovery — 2026-10-09

**COMBINED_CANDIDATE_VERIFIED; INSTALLED_FILE_MATCH; CLIENT_LOAD_AND_VISUAL_ACCEPTANCE_PENDING.** Retain exact `patch-18` and `0.1.8d`. No new repair, discovery campaign, build, test-suite repeat, desktop input, installation or server launch occurred during this coordination.

## Completion dependency and existing installation

The requested fence completion report is available: **Trace UltimaCraft fence facing bug**, thread `01a1129f-fb0e-7460-b721-7949e8768533`, latest turn `01a12071-b4bb-7210-bd9d-7bab31f198e0` is completed/idle. Its report and [final fence handoff](../../projects/patch18-edge-fence-hotfix/RELEASE_HANDOFF.md) establish that the owner separately authorized replacement after closing Minecraft. The guarded operation already installed the selected combined candidate and preserved the old backup, other mods and production.

This request performs **no second replacement**. Fresh read-only verification confirms exactly one load-eligible Britannia JAR matching the selected candidate, the outside-mods old backup, no observed Minecraft client process, and the existing matching localhost server. No force-quit is performed. New loaded client identity remains pending the owner's launch. The earlier plaster-only isolated launch instructions are superseded by the [single combined session](MANUAL_SESSION.md); do not launch another test client/server or install the historical f02 artifact.

## Selected existing combined artifact

```text
C:\projects\britannia\mod\Britannia_Mod\tmp\fence-runtime-recovery-2026-10-09\release\britannia_mod-0.1.8d-all.jar
```

| Field | Verified value |
| --- | --- |
| Filename | `britannia_mod-0.1.8d-all.jar` (preserved) |
| Clean embedded source | `cf49b1700d1a8992f39396f2ae531f0cb26502d7`, dirty=false |
| Size | **36,142,686 bytes** |
| SHA-256 | **e64fe63c6ed0836f520e3d9b66ac7336b3e323677b63a433d65d48852c30fd70** |
| Build timestamp | `2026-10-08T22:31:37.806305200Z` |
| Runtime/version | 0.1.8d; MC 1.21.1; NeoForge 21.1.72; Java 21; bundled GeckoLib 4.6.6/nanohttpd retained |
| Installed file | `C:\Users\dusti\curseforge\minecraft\Instances\UltimaCraft - Britannia\mods\britannia_mod-0.1.8d-all.jar` |
| Installed hash/source | Exact selected values above; FILE_MATCH, not loaded-JVM proof |
| Existing disposable server | **127.0.0.1:25583**, PID40936 at verification, bind localhost only; actual session logs identify selected hash/source |

The old f02/986ed746 candidate remains **historical comparison evidence**, not the installation target. The selected clean build's embedded task branch is legitimate because its source is integrated in exact Patch 18; no different branch is imported now.

## Both fixes and other hotfixes verified

Current entry HEAD is **8707bb54708e2ef8dc97c54bfd9e8d29ae323f79**. Verified ancestry includes:

- Fence saved layout/endpoint implementation **0fe906d7**, required test closeout **07359f98**, runtime identity test **cf49b170**; no implementation blocker is reported in its final handoff.
- Plaster mesh/scoped-half repair **ac6b2e80**, paired-wall mirror/collision/transform repair **5a6734ba**, portable combined fixtures **986ed746**.
- Plaster follow-up **b440196d**, fence recovery preparation **d808d3cb** and actual local replacement closeout **8707bb54** are integrated documentation/tool/fixture descendants. They are not falsely described as new compiled fixes.

Fresh current Java/resources/build/version diff against selected source is empty, including working production paths. The selected archive and historical tested combined archive have **10,698 identical entry names**; **10,697 entries are byte-identical** and only `britannia_mod_build.properties` differs. This includes **2,706 class entries**, **7,542 asset entries**, dependencies and all other cumulative network/medallion/alligator/fence/plaster payload. All 17 support block model/blockstate resources semantically and newline-normalized match the selected committed source/current source. No runtime test helper leaks into the selected JAR.

This proves reuse is appropriate. No clean candidate rebuild or passing-suite rerun is required merely for integration/handoff descendants. Historical 4,129 JUnit/zero failures/errors/23 inherited skips, 24 focused fence GameTests and selected 112-state restart results remain applicable. The prior payload's complete 1,366-required-GameTest gates and plaster render/state/physical evidence are reused with their original artifact/provenance scope, not claimed newly executed. See [fence source gates](../../projects/patch18-edge-fence-hotfix/evidence/rediscovery-2026-10-08/SOURCE_GATES_RESULTS.json) and [plaster follow-up](../plaster-corner-followup-2026-10-09/FOLLOWUP_HANDOFF.md).

The initial read-only collector exposed two tooling assumptions, not game failures: packaged CRLF versus Git LF and an overbroad 17-model filename filter including item models. The collector now checks semantic/normalized resource parity in the established block model/blockstate scope; selected bytes and source were not altered.

## Backup and custody

Outside-mods old backup:

```text
C:\Users\dusti\curseforge\minecraft\Instances\UltimaCraft - Britannia\.britannia-recovery-backups\2026-10-09-e64fe63c\britannia_mod-0.1.8d.jar
```

SHA-256 **c04f3092fa577e4c98c4c4ee16febd5008403ccc98a0ebc064d860e3ac8981c6**; the retired original at `retired-from-mods.jar` in the same directory matches. The fence installation receipt verifies all **10 other mod files unchanged** during replacement. This coordination captures current non-Britannia file hashes without changing them, settings, saves or active mods. No duplicate installation session or rollback is performed. A later deliberate rollback must close Minecraft first and retain exactly one eligible Britannia JAR; old production compatibility/acceptance is not established here.

## Acceptance and publication remain separate

| Component | Status |
| --- | --- |
| Fence implementation/install | COMPLETE effective implementation; installed file verified. New client mouse/corner/decorator/reconnect/appearance pending. |
| Plaster/shared walls | Effective mesh/pair repair verified in chosen candidate. Both A/B exterior supports and edited-pair post-reconnect appearance pending. Exterior required; interior preserved. |
| Medallion/movement | Owner approvals COMPLETE and reused; no repeat. |
| Alligator | Existing functional/preflight evidence retained; visual playback remains an independent combined-release hold. Available in the same server, not a prerequisite for this local installation. |
| Production acceptance | PENDING required observations; no production deployment, server change, live-world edit, tag or GitHub Release. |
| PR #501 / main | Separate existing failed public RunUO fixture tests and signing/restricted-update workflow hold; not repaired, waived or bypassed by local recovery. |

Fresh verification and evidence: [COMBINED_RECOVERY_VERIFICATION.json](evidence/COMBINED_RECOVERY_VERIFICATION.json), [actual existing server identity](evidence/EXISTING_SERVER_IDENTITY.txt), [prior guarded local install receipt](../../projects/patch18-edge-fence-hotfix/evidence/runtime-recovery-2026-10-09/LOCAL_RECOVERY_RECEIPT.json). Applicable successful state/physics evidence does not prove mouse interaction, GPU render, texture/outline appearance or newly loaded client identity.

The owner launches their selected CurseForge profile and uses **one** [consolidated manual session](MANUAL_SESSION.md). No idle desktop, desktop automation or active/production world edits are requested.
