# Shrine and Monolith Merge Readiness

## Readiness status

`CONDITIONAL MERGE READINESS`

Code review and an explicitly owner-authorized merge may proceed with the documented Milestone 9 limitations.

Live promotion and production release remain blocked pending the deferred two-authenticated-client validation, unless the project owner later issues a separate explicit release-risk waiver.

This package does not describe the feature as fully validated, unconditionally merge-ready, production-ready, release-ready, multiplayer validated, or live-gameplay validated.

## Audited tips

| Role | Branch/commit |
| --- | --- |
| Source branch | `shrines-monoliths` |
| Audited production/evidence tip | `c69057dc1b2a961f73b7c8b87ae7bc30d7a41c43` |
| Target | `origin/patch-18` at `62df1dc97c5113a86f9c0f258cb90538f31efe89` |
| Shared local target | `patch-18` at `62df1dc97c5113a86f9c0f258cb90538f31efe89` |
| Merge base | `62df1dc97c5113a86f9c0f258cb90538f31efe89` |

The target equals the merge base. No target-side commit exists after the branch point. The Milestone 10 documentation commit cannot record its own hash without amendment; its full hash is in the final Milestone 10 report.

## Complete feature history

| # | Milestone | Commit | Subject | Files |
| ---: | --- | --- | --- | ---: |
| 1 | 0 | `b10efd3382f74bf8e1970588bcd0b57869c8771a` | `docs(structures): initialize shrine and monolith project` | 5 |
| 2 | 1 | `bf42b16b818b80a3303c78e21c5f9ec75602fd25` | `feat(structures): add shrine and monolith definitions` | 19 |
| 3 | 2 | `82a4730ac250045bc24bcf3677a2f3958c785f45` | `feat(structures): add atomic multiblock shrine placement` | 28 |
| 4 | corrective 2 | `e963de2fbf641cb7b686c3676ad45d605c6c9eee` | `fix(structures): complete atomic shrine placement validation` | 14 |
| 5 | 3 | `b863472940d073434490516df457c7e9ad7a6404` | `feat(structures): persist and protect multiblock shrines` | 29 |
| 6 | original 4 | `e1fcae20ae2f2af796b7b44ad9ca9d65352cffff` | `feat(shrines): render approved texture variants` | 27 |
| 7 | corrective 4 | `69514a90c8376dc224cd2530f2f73e58928b1dfd` | `revert(shrines): remove unapproved placeholder rendering` | 28 |
| 8 | owner-approved restoration 4 | `06677e3235e05ec13678d474294e5f2152433ce0` | `feat(shrines): restore approved placeholder rendering` | 28 |
| 9 | 5 | `4900136b1a82f477fc02eac8d963e19d8b94e5e6` | `feat(shrines): cycle variants with interior decorator` | 14 |
| 10 | 6 | `1c5bff67a961da93dea245ed9d05653f2f7bfeca` | `feat(monoliths): add offset multiblock placement` | 39 |
| 11 | 7 | `0aa523ec86e483e230fc1c4d04e145d394ebf990` | `feat(monoliths): cycle model variants with decorator` | 21 |
| 12 | 8 | `a3b81edda57e441d1abdc95f7b2713567df05130` | `test(structures): harden collision and adjacency behavior` | 12 |
| 13 | 9 | `c69057dc1b2a961f73b7c8b87ae7bc30d7a41c43` | `docs(structures): record live validation evidence` | 3 |

All thirteen commits are reachable from the audited source tip. There is no merge commit, duplicate rebase implementation, banking change, direct `patch-18` project commit, or evidence of destructive history rewriting. The transparent Milestone 4 implementation/revert/restoration chronology remains intact. Both authoritative root specifications remain committed.

## Complete changed-path audit

The merge-base-to-audited-tip diff contains 112 paths, 15,390 insertions and 3 deletions: 106 added, 6 modified, 0 deleted, and 0 renamed. It includes 47 production Java paths, 35 test Java paths, 22 main-resource paths, 5 project documents, 2 root specifications, and 1 build file. No unrelated path was found.

Every path is classified by the following exhaustive, non-overlapping path set:

| Classification | Count | Exact paths or exhaustive path rule |
| --- | ---: | --- |
| authoritative root specification | 2 | `UltimaCraft_Shrine_and_Monolith_Codex_Playbook.md`; `UltimaCraft_Shrine_and_Monolith_System_Design.md` |
| project documentation | 5 | every pre-M10 file under `docs/shrines-monoliths/`: `CONTENT_REPORT.json`, `IMPLEMENTATION_LOG.md`, `OPEN_QUESTIONS.md`, `PLACEHOLDER_ASSETS.md`, `PROJECT_FACTS.md` |
| build configuration | 1 | `build.gradle` |
| registration | 4 | `BritanniaMod.java`, `ClientModSetup.java`, `registry/DataComponentRegistry.java`, `registry/LargeStructureRegistry.java` |
| definition/catalogue | 10 | every changed Java file under `structure/definition/` |
| placement | 9 | every changed Java file under `structure/placement/` |
| persistence | 11 | every changed Java file under `structure/item/`, plus `LargeStructureAnchorBlockEntity.java`, `PlacedStructureState.java`, `PlacedStructureStatus.java`, `StructureCell.java`, and `StructureCellRole.java` |
| lifecycle | 4 | `LargeStructureAnchorBlock.java`, `LargeStructurePartBlock.java`, `ShrineLifecycleService.java`, `ShrineRemovalCause.java` |
| integrity | 2 | `ShrineIntegrityHandler.java`, `ShrineIntegrityService.java` |
| interaction | 2 | `InteriorDecoratorToolItem.java`, `ShrineVariantCycleService.java` |
| authorization | 1 | `DecoratorAuthorization.java` |
| rendering | 4 | `client/renderer/shrine/ShrineGeoModel.java`, `ShrineRenderer.java`, `multiblock/ShrineRenderTransform.java`, `render/ShrineRenderSelection.java` |
| resource | 21 | both structure animations, both structure blockstates, four structure geometry files, both structure item models, and eleven structure PNG textures |
| localization | 1 | `assets/britannia_mod/lang/en_us.json` |
| test | 35 | every changed Java file under `src/test/java/com/seggellion/britannia_mod/structure/` |
| unrelated | 0 | none |

Build/run output is ignored and untracked. The external Milestone 9 and 10 validation directories, logs, worlds, configurations, EULA, JAR copies, and evidence are outside Git. The range `git diff --check` reports only intentional Markdown hard-break trailing spaces already committed in the two authoritative root specifications; the Milestone 10 worktree/staged diff is whitespace-clean.

## Architecture and ownership

- One immutable catalogue and transform contract defines a 2 x 1 x 2 shrine (one anchor plus three parts) and a 3 x 3 x 2 monolith (one anchor plus seventeen parts).
- One shared planner validates the complete ordered footprint before mutation. Required chunks must already be loaded; no chunk force-loading exists. Protection is checked for every target cell.
- One shared transaction captures all original/expected states, mutates in order, consumes one survival item only after full success, consumes zero in creative, and rolls back in reverse order with ownership checks. Unrelated callback replacements are preserved and failed placement emits no configured drop.
- Only the anchor owns a block entity. Parts store only facing and local X/Y/Z offset; they do not store family, variant, full footprint, independently drop configured items, or render complete geometry.
- The persisted placed footprint is authoritative. Current definition changes cannot resize an existing saved structure.
- Anchor or any valid part resolves one logical lifecycle. Survival teardown emits exactly one configured family item; creative emits zero. Pick block reconstructs exact configured state. Explosion follows the centralized removal-cause policy. External replacements and repair obstructions are preserved.
- Integrity uses persisted offsets, defers when the anchor chunk is unavailable, repairs only loaded replaceable cells, uses a per-level/anchor reentrancy guard, processes at most 64 chunks per tick and 4,096 pending chunks per level, and performs no global per-tick structure scan.

## Collision and rendering

- Both families use `SOLID_CELL`; collision, interaction, and support shapes remain within each cell's local 0..16 bounds. Render geometry and render bounds do not determine shape.
- Shrine texture cycling, monolith model cycling, and the monolith visual `[0,16,0]`-voxel translation cannot change occupancy or shape.
- Anchor and parts reject fluid replacement, are non-waterloggable, and use piston reaction `BLOCK`. Adjacent cells remain outside structure occupancy.
- The complete visual renders from the anchor only. Parts have no renderer. Shrines share `geo/shrine.geo.json` and select one of nine textures. Monoliths select distinct model and texture pairs.
- Both monolith variants use exactly `[0,16,0]` voxels, applied visually once. Render selection performs no world mutation.
- Missing resource fallback is bounded and diagnostic; it retains the stable identity and never substitutes another variant.
- Common definition, placement, lifecycle, and interaction source imports no client-only renderer implementation.

## Registered IDs

| Kind | Count | ID |
| --- | ---: | --- |
| large-structure anchor block | 1 | `britannia_mod:large_structure_anchor` |
| large-structure part block | 1 | `britannia_mod:large_structure_part` |
| anchor block entity type | 1 | `britannia_mod:large_structure` |
| part block entity type | 0 | none |
| shrine family item | 1 | `britannia_mod:shrine` |
| monolith family item | 1 | `britannia_mod:monolith` |
| ordinary anchor BlockItem | 0 | none |
| ordinary part BlockItem | 0 | none |
| Interior Decorator tool | 1 | reused `britannia_mod:interior_decorator_tool` |
| anchor renderer registration | 1 | `britannia_mod:large_structure` to `ShrineRenderer` |
| part renderer registration | 0 | none |

## Saved-state and compatibility audit

| State | Version/codec | Exact representation |
| --- | --- | --- |
| shrine item | schema 1; `ShrineItemState.CODEC`; `ShrineItemState.STREAM_CODEC` | component `britannia_mod:shrine_instance_state`; keys `schema_version`, `family_id`, `variant_id`; stream uses VarInt plus two UTF-8 strings |
| monolith item | schema 1; same codec classes | component `britannia_mod:monolith_instance_state`; same keys and stream representation |
| placed anchor | schema 1; `PlacedStructureState.CODEC` | root key `shrine_state`; keys `schema_version`, `family_id`, `variant_id`, `facing`, `placed_footprint`; each ordered offset has `x`, `y`, `z` |

The update tag is `saveWithoutMetadata`; the update packet is `ClientboundBlockEntityDataPacket.create(this)`; client application follows vanilla block-entity packet/update-tag loading through `loadAdditional`. Anchor block-state facing is authoritative and persisted facing is validation-only. Footprints are immutable ordered unique lists of exactly 4 or 18 cells, beginning with the anchor.

Unknown family and variant IDs remain represented and classify as `MISSING_FAMILY_DEFINITION` or `MISSING_VARIANT_DEFINITION`; they are not silently substituted. Malformed, structurally invalid, and unsupported-future-schema state fails closed with bounded diagnostics. Valid pick and lifecycle reconstruction selects the registered family item from the saved family and writes the exact schema/family/variant component; invalid ownership/state yields an empty pick. Definition changes do not replace the saved footprint.

No shrine or monolith block, item, block-entity, or data-component ID existed at the merge base. The Interior Decorator ID did exist and is reused. Therefore no alias or migration is required for a prior shrine/monolith feature implementation, and no alias was invented.

## Interior Decorator and security review

- Existing `britannia_mod:interior_decorator_tool` is reused.
- Structure cycling authorization runs on the logical server and requires server-confirmed creative mode or permission level 2.
- The server resolves the clicked anchor/part, catalogue family, current state, deterministic next variant, and resource selection. The client submits no desired family, variant, model, texture, footprint, offset, or cycle position.
- Shrine cycles only inside `shrine`; monolith cycles only inside `monolith`; no cross-family conversion exists. Only `variant_id` changes.
- Assignment or synchronization failure restores prior state; feedback occurs only after authoritative success.
- Placement protection covers every target cell. Creative status changes item consumption but does not bypass the placement-protection predicate.
- Centralized placement rollback, break/explosion/replacement teardown, integrity repair, and reentrancy guards prevent configured-item duplication in tested paths.
- Raw anchor and part items are not registered through the ordinary feature path. Missing/malformed state fails closed.
- Block-entity synchronization is display-only on the client.
- A path-only, value-suppressed scan of every feature commit and current changed tree found no secret assignment, authentication secret, RCON password, launcher token, credential, world, raw runtime log, server property file, EULA, backup, or JAR in feature history. The only path names matching the word `Authorization` are the intended decorator policy source and test.

## Content inventory

The deterministic owner-approved provisional catalogue contains exactly nine shrine variants in cycle order: `honesty`, `compassion`, `valor`, `justice`, `sacrifice`, `honor`, `spirituality`, `humility`, and `chaos`; and exactly two monolith variants: `diagnostic_missing_content` and `diagnostic_alternate`. Each stable ID appears once. All variants are enabled and cycle deterministically by explicit position.

Shrine geometry is shared and the nine textures are distinct. The two monoliths use distinct geometry and texture pairs; both remain explicitly provisional diagnostic content. No placeholder is described as final artwork. Every catalogue model, texture, animation, item model, localization key, and blockstate exists. Part blockstates draw no duplicate complete geometry. `CONTENT_REPORT.json` is deterministic and accurate.

The protected inventory is 19 files, not 20: 13 shrine files and 6 monolith files. Blockstates and localization are validated resources but were not part of the protected asset-hash inventory established in Milestones 4, 6, and 7.

| Protected asset | SHA-256 |
| --- | --- |
| `animations/shrine.animation.json` | `F20A6AFD94D1FCF6463B8FDA98853781FC8548293293514199C4AC1E4C5A981E` |
| `geo/shrine.geo.json` | `C31915013A7D50D1732225764D4F94FEB1AD141515BAC0F3CADC81E5C8B3BCA0` |
| `geo/shrine_missing.geo.json` | `460F9FDDE68EC578C3FF1B4E26B457AFCF3467485325B4189C14E02820CC4781` |
| `models/item/shrine.json` | `829C55BB91B761F7529607B3BFD439B73D6A171F01556C12D5F50D5991636985` |
| `textures/block/shrine/honesty.png` | `D35747568A37960736C20F8359F55043B19739FC7D1FAB34144F8F971C36BCCC` |
| `textures/block/shrine/compassion.png` | `79160E8AF141E4395A64D64439F7FBF0C2170D78073A136E6CBEF8A130FC87BC` |
| `textures/block/shrine/valor.png` | `A748C870317998F911AC328960685F4B41AE8330635DC839F35D27EC6ACA4A1F` |
| `textures/block/shrine/justice.png` | `5F01D14F16870EBA66C6A4C3E2F19C919E403A5DCDDC12037904285C825B1B8B` |
| `textures/block/shrine/sacrifice.png` | `E4C56B44DC44C749DB10E2D34824DCF0079774FAAF9C2368330CC57036B4EEC4` |
| `textures/block/shrine/honor.png` | `ADC2A67CFA7476D4C1D18A7C49E9F1937D552099FCFE9F1D3C821B832135D381` |
| `textures/block/shrine/spirituality.png` | `D50CF726BD459C85313A9173E708C49C3ADC72559D0EABC9458FDFED8FF05CF1` |
| `textures/block/shrine/humility.png` | `E201645E5D9058E28022B22904114E09823E736DDE8021D2E4DA2CE9E558AC8A` |
| `textures/block/shrine/chaos.png` | `D8B2FDEB4158BBF86A053CDD383E532569F4DDDAEFF178D2472F5ABC2507EDC3` |
| `animations/monolith.animation.json` | `D63D4CBCEF6A3E410EE94F38F5684F7B3E9F94BCC69B4D80C925FBBB61FC1530` |
| `geo/monolith_diagnostic.geo.json` | `0D58B1ED73A8811E10B276DB7E55B29F7248DD047074C0FE786882DF0757E53C` |
| `geo/monolith_diagnostic_alternate.geo.json` | `B2FCBE6841C53313A754A9722E5633957A9AB0334FB96FF05FBCFC42BA7512DC` |
| `models/item/monolith.json` | `E48339859F7AA66BBC08246B8BC65AC1827E28241509518F9884A854739A2BED` |
| `textures/block/monolith/diagnostic_stone.png` | `FE07CE0672EE51D76F2833D1044264C7B65B2ADEB076873D1B07C953509944F4` |
| `textures/block/monolith/diagnostic_alternate_stone.png` | `E320B72F2D0B9429D07DD4E62D264535760797AE6D1F9DD047C082B6736A2C4A` |

All 19 source hashes match their packaged `-all.jar` entries byte-for-byte. They match the pre-M10 inventory and no Milestone 9 or 10 asset changed.

## Automated and startup evidence

| Evidence class | Status | Scope |
| --- | --- | --- |
| Automated evidence | available / PASS | 32 structure test classes, 200 JUnit methods, 275,936 hardening logical cases; no failures/errors/skips |
| Previous dedicated-server preflight | available | earlier artifact only; not the final Milestone 9 rebuilt hash |
| Final exact-JAR dedicated-server evidence | PASS in Milestone 10 | final clean-build JAR reached `Done`, normal `stop`, exit 0, all dimensions saved |
| Development-client startup | PASS for smoke scope | mod resources, OpenAL, block and GUI atlases; no structure resource error; process intentionally terminated after marker |
| Authenticated-client evidence | none / UNVERIFIED | no authenticated connection |
| Two-client evidence | none / UNVERIFIED | no concurrent distinct authenticated clients |
| Live gameplay evidence | none / UNVERIFIED | no live placement/cycling/lifecycle matrix |
| Visual evidence | none / UNVERIFIED | no live collision, horizon, or duplicate-render review |
| Performance evidence | none / UNVERIFIED | no dense-scene smoke |

Final validation commands from the isolated clone:

- Focused hardening/milestone/item audit: exit 0 in 46.1s; 9 classes/39 methods; 0 failures/errors/skips; test executed, 29 tasks up-to-date.
- Required structure suite: exit 0 in 33.7s (`BUILD SUCCESSFUL in 32s`); 32 classes/200 methods; 0 failures/errors/skips; test restored from cache.
- Full repository suite: exit 0 in 29.8s (`BUILD SUCCESSFUL in 29s`); 32 classes/200 methods; 0 failures/errors/skips; test restored from cache.
- Clean build: exit 0 in 178.4s (`BUILD SUCCESSFUL in 2m57s`); 36 tasks: 6 executed, 16 from cache, 14 up-to-date; clean compilation, resources, JAR/JarJar, tests and build passed.
- The relevant warning is Gradle's incubating daemon-JVM discovery notice. Established unrelated repository runtime warnings are recorded below.

The deployable `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar` is 22,817,250 bytes, has 4,692 entries, manifest `Manifest-Version: 1.0`, and SHA-256 `5F1619DBBD50FDD35875ACC8D5CCC610F05DCBB6ECEF4F136A8471392C42B04A`. It contains 107 structure-class entries, nine shrine textures, two monolith geometries, two monolith textures, the structure animations/item models/localization, one anchor renderer, and no test/fixture entry or part renderer. Source, external artifact, and fresh server copies match exactly.

The fresh external server at `C:\projects\britannia\validation\shrines-monoliths-m10` ran NeoForge 21.1.72 with `online-mode=true`, found exactly one Britannia JAR, loaded Britannia 0.1.7k and GeckoLib 4.6.6, reached `Done (9.226s)`, received normal console `stop`, exited 0 after 42.4s, and saved overworld, End, Nether, and all dimensions. It logged zero shrine/monolith/large-structure or registry error. The pre-existing `TitleScreen` invalid-dedicated-dist mixin diagnostic, missing optional `britannia_mod.properties`, first-run FML correction, union-schema warnings, and barrel warning remain unrelated baseline diagnostics.

## Owner-Accepted Milestone 9 Limitations

Decision: proceed to Milestone 10 without the prescribed two-authenticated-client Milestone 9 session. Status: approved. Date: 2026-08-04. Scope: final audit and conditional merge-readiness preparation only. The owner accepts the missing evidence as an explicit limitation; all unperformed live checks remain `UNVERIFIED`, the package remains conditional, and no multiplayer or live-gameplay claim is made. Follow-up is a controlled two-distinct-authenticated-client session using the exact release-candidate JAR before production promotion unless separately waived.

Each remaining criterion is individually `UNVERIFIED`:

- Any authenticated Minecraft client connection.
- Two distinct authenticated clients connected concurrently.
- Client A observing Client B.
- Client B observing Client A.
- Live shrine placement.
- Live monolith placement.
- Live shrine cycling.
- Live monolith cycling.
- Unauthorized-player interaction.
- Late tracking.
- Disconnect and reconnect.
- Save-world reload.
- Server restart with client observation.
- Client resource reload.
- Live survival and creative lifecycle.
- Live configured drop counts.
- Live pick block.
- Live explosion behavior.
- Live external replacement.
- Live missing-part repair.
- Live obstructed repair.
- Live piston behavior.
- Live fluid behavior.
- Live cross-chunk behavior.
- Live stair, slab, wall, fence, and attachment adjacency.
- Live monolith horizon alignment.
- Live duplicate-render review.
- Two-client log review.
- Dense-scene performance smoke.

The earlier successful Milestone 9 server startup used a preflight artifact. The final rebuilt Milestone 9 artifact was redistributed but not started. Milestone 10 closes only the final exact-JAR server-startup gap with a new clean rebuild; it does not change any client, multiplayer, gameplay, visual, or performance item above.

## Risk register and code-review hotspots

| Risk | Status/mitigation |
| --- | --- |
| No authenticated/two-client/live evidence | accepted for conditional review/merge only; blocks promotion through `POST_MERGE_VALIDATION.md` |
| Provisional shrine and monolith art | owner-approved provisional; final artwork/migration remains a non-critical content follow-up |
| Saved worlds/inventories depend on new registry IDs | follow `ROLLBACK_PLAN.md`; never blindly remove registrations from a populated world |
| Existing `TitleScreen` dedicated-dist mixin diagnostic | baseline issue, not introduced by this feature; server still reaches `Done`; review separately |
| Future integration branch advancement | re-run merge-base, merge-tree, tests, build, hashes and runtime smoke against the new authorized target |
| Intentional root-spec Markdown trailing spaces | range `diff --check` diagnostic only; do not alter authoritative specs in this milestone |

Code-review hotspots are `build.gradle` (JUnit/JarJar integration), `BritanniaMod.java` (common registration/integrity hook), `ClientModSetup.java` (single anchor renderer), `DataComponentRegistry.java` (persistent/network state), `LargeStructureRegistry.java` (exact IDs/no raw BlockItems), `InteriorDecoratorToolItem.java` and `structure/interaction/` (server authorization/transaction), `structure/placement/` (atomicity/protection/rollback), `structure/lifecycle/` (drop/integrity/reentrancy), `LargeStructureAnchorBlockEntity.java` and `PlacedStructureState.java` (schema/sync), and `en_us.json` (localization merge review).

## Merge-conflict simulation

Git 2.50.1 three-tree `git merge-tree` analyzed source `c69057dc1b2a961f73b7c8b87ae7bc30d7a41c43` into target `62df1dc97c5113a86f9c0f258cb90538f31efe89` with that same merge base. Exit was 0; no conflict marker, `both modified` path, or textual conflict was predicted. The output describes the feature additions because the target has exactly zero post-base changed paths.

Semantic review is still required for `build.gradle`, the four registration/integration Java files, `InteriorDecoratorToolItem.java`, client renderer registration, and `en_us.json`. No target-side banner or banking path overlaps because the exact target has no post-branch changes. The shared repository's dirty `banking` work is uncommitted, unrelated, and was neither read into nor synchronized with this branch.

## Recommended procedure and release gate

The branch is suitable for code review and may be merged only through a separately authorized repository-standard pull request or non-destructive merge procedure.

The missing two-authenticated-client live validation remains an accepted owner limitation, not a passing test result.

Production promotion remains blocked pending `POST_MERGE_VALIDATION.md` or a separate explicit release-risk waiver.

Before any merge, re-confirm the target tip, repeat the non-working-tree merge simulation, review the semantic hotspots, build/test the exact merge result, and retain the transparent thirteen-commit chronology unless repository policy explicitly chooses a different non-destructive PR merge method. After merge, run the complete exact-JAR two-client checklist.

No merge was performed while preparing this package. No release, deployment, tag, promotion, push, or commit transfer was performed.
