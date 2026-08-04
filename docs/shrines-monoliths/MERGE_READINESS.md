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
| Audited production/evidence tip | `b26165350227e6d46cfde3af91ff738a42e48c11` |
| Target | `origin/patch-18` at `62df1dc97c5113a86f9c0f258cb90538f31efe89` |
| Shared local target | `patch-18` at `62df1dc97c5113a86f9c0f258cb90538f31efe89` |
| Merge base | `62df1dc97c5113a86f9c0f258cb90538f31efe89` |

The target equals the merge base. No target-side commit exists after the branch point. This refreshed
Milestone 10 documentation commit cannot record its own hash without amendment; its full hash is in the
final refreshed audit report.

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
| 14 | original 10 | `51c83c82d50c8467f06606bbec60eefcb82c758b` | `docs(structures): complete merge-readiness audit` | 6 |
| 15 | corrective 9A | `9008f8c18e7e2c98dabcb207a464f94de5298f3d` | `fix(shrines): align geometry and expose creative item` | 10 |
| 16 | corrective 9A | `257155c1dbf494074e9881992b8e50eb3ed85826` | `fix(shrines): restore flat model and creative entries` | 21 |
| 17 | corrective 9A | `b26165350227e6d46cfde3af91ff738a42e48c11` | `fix(shrines): align texture and raise profile` | 7 |

All seventeen commits are reachable from the audited source tip. There is no merge commit, duplicate
rebase implementation, banking change, direct `patch-18` project commit, or evidence of destructive
history rewriting. The transparent Milestone 4 implementation/revert/restoration and the later
audit/corrective chronology remain intact. Both authoritative root specifications remain committed.

## Complete changed-path audit

The merge-base-to-audited-tip diff contains 119 paths, 16,811 insertions and 3 deletions: 112 added,
7 modified, 0 deleted, and 0 renamed. It includes 49 production Java paths, 36 test Java paths,
23 main-resource paths, 8 project documents, 2 root specifications, and 1 build file. No unrelated
path was found.

Every path is classified by the following exhaustive, non-overlapping path set:

| Classification | Count | Exact paths or exhaustive path rule |
| --- | ---: | --- |
| authoritative root specification | 2 | `UltimaCraft_Shrine_and_Monolith_Codex_Playbook.md`; `UltimaCraft_Shrine_and_Monolith_System_Design.md` |
| project documentation | 8 | every file under `docs/shrines-monoliths/`: `CONTENT_REPORT.json`, `IMPLEMENTATION_LOG.md`, `MERGE_READINESS.md`, `OPEN_QUESTIONS.md`, `PLACEHOLDER_ASSETS.md`, `POST_MERGE_VALIDATION.md`, `PROJECT_FACTS.md`, `ROLLBACK_PLAN.md` |
| build configuration | 1 | `build.gradle` |
| registration | 5 | `BritanniaMod.java`, `ClientModSetup.java`, `registry/CreativeTabRegistry.java`, `registry/DataComponentRegistry.java`, `registry/LargeStructureRegistry.java` |
| definition/catalogue | 10 | every changed Java file under `structure/definition/` |
| placement | 9 | every changed Java file under `structure/placement/` |
| persistence | 11 | every changed Java file under `structure/item/`, plus `LargeStructureAnchorBlockEntity.java`, `PlacedStructureState.java`, `PlacedStructureStatus.java`, `StructureCell.java`, and `StructureCellRole.java` |
| lifecycle | 4 | `LargeStructureAnchorBlock.java`, `LargeStructurePartBlock.java`, `ShrineLifecycleService.java`, `ShrineRemovalCause.java` |
| integrity | 2 | `ShrineIntegrityHandler.java`, `ShrineIntegrityService.java` |
| interaction | 2 | `InteriorDecoratorToolItem.java`, `ShrineVariantCycleService.java` |
| authorization | 1 | `DecoratorAuthorization.java` |
| rendering | 5 | `client/renderer/shrine/ShrineGeoModel.java`, `ShrineGraniteRenderLayer.java`, `ShrineRenderer.java`, `multiblock/ShrineRenderTransform.java`, `render/ShrineRenderSelection.java` |
| resource | 22 | both structure animations, both structure blockstates, four structure geometry files, both structure item models, and twelve structure PNG textures |
| localization | 1 | `assets/britannia_mod/lang/en_us.json` |
| test | 36 | every changed Java file under `src/test/java/com/seggellion/britannia_mod/structure/` |
| unrelated | 0 | none |

Build/run output is ignored and untracked. The owner-created `textures/block/shrine/old/` backup is
untracked and explicitly excluded from resource processing. External Milestone 9 and 10 validation
directories, logs, worlds, configurations, EULA, JAR copies, and evidence are outside Git. The range
`git diff --check` reports exactly ten intentional Markdown hard-break trailing-space diagnostics
already committed in the two authoritative root specifications; the refreshed documentation diff is
whitespace-clean.

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
- The complete visual renders from the anchor only. Parts have no renderer. Shrines share
  `geo/shrine.geo.json`, select one of nine owner-supplied virtue textures, and use one bounded
  `ShrineGraniteRenderLayer` for only the `granite_rim` bone. Monoliths select distinct model and
  texture pairs.
- Corrected shrine geometry is centered within its four-cell union for every facing: four
  `13 x 14 x 13` surface cubes, four 15-voxel-high granite rim pieces, raw X/Z `[-7,23]`, raw Y
  `[0,15]`, and a one-model-voxel horizontal perimeter inset. Exact 64 by 64 UV quadrants reconstruct
  each 128 by 128 virtue symbol with the world-row correction enforced by tests.
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

The existing `britannia_mod:britannia_decor_tab` exposes exactly one explicitly configured shrine
stack (`shrine/honesty`) and one explicitly configured monolith stack
(`monolith/diagnostic_missing_content`). It exposes neither raw anchor nor part item.

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

The deterministic catalogue contains exactly nine shrine variants in cycle order: `honesty`,
`compassion`, `valor`, `justice`, `sacrifice`, `honor`, `spirituality`, `humility`, and `chaos`; and
exactly two owner-approved provisional monolith variants: `diagnostic_missing_content` and
`diagnostic_alternate`. Each stable ID appears once. All variants are enabled and cycle
deterministically by explicit position.

Current shrine geometry, nine virtue textures, and granite material are owner-supplied and approved for
this implementation. The two monoliths use distinct geometry and texture pairs; both remain explicitly
provisional diagnostic content. No provisional monolith is described as final artwork. Every catalogue
model, texture, animation, item model, localization key, and blockstate exists. Part blockstates draw no
duplicate complete geometry. `CONTENT_REPORT.json` is deterministic and accurate.

The protected inventory is now 20 files: 14 shrine files including the owner-supplied granite material,
and 6 monolith files. Blockstates and localization are validated resources but are outside this
protected asset-hash inventory.

| Protected asset | SHA-256 |
| --- | --- |
| `animations/shrine.animation.json` | `F20A6AFD94D1FCF6463B8FDA98853781FC8548293293514199C4AC1E4C5A981E` |
| `geo/shrine.geo.json` | `374E8455075B8EFFCDFE4E36432FB9254A112F777141D319F90B5545EA92AB51` |
| `geo/shrine_missing.geo.json` | `460F9FDDE68EC578C3FF1B4E26B457AFCF3467485325B4189C14E02820CC4781` |
| `models/item/shrine.json` | `829C55BB91B761F7529607B3BFD439B73D6A171F01556C12D5F50D5991636985` |
| `textures/block/shrine/honesty.png` | `BF7C657E85198EB58A82E6466DFCBAFD74E45B3201B9F24000BD553B84744855` |
| `textures/block/shrine/compassion.png` | `F4A03F885EBF68B0C060450F72EDCFE3D2D48825E030C3A31951F826FBA627CD` |
| `textures/block/shrine/valor.png` | `2482D99690D718C3D0B06E919118408BE965179C0A878F2924F396EBBA186CF9` |
| `textures/block/shrine/justice.png` | `50531DACCE0ECB3A513714EC3036C44C96092A787F93854375EDC47904A2BFC3` |
| `textures/block/shrine/sacrifice.png` | `E9D3FA485A24BC1237B20CE2287F3E7BD93E8792C4BF44C8DA742C25992427DC` |
| `textures/block/shrine/honor.png` | `723898577D80867D40B4BBF9DADCDCC759446740722F4F496264E88D1826AF36` |
| `textures/block/shrine/spirituality.png` | `C4394FF25C3EF14DAE11EEDB5D2DF3BD0787608E82602712314AF32565A3CA4A` |
| `textures/block/shrine/humility.png` | `B8136CE1C9637D1B1A5F6E0738B9698F34854C88F796BD6FC63DB2F955C1688C` |
| `textures/block/shrine/chaos.png` | `9365AB11463B1442FEE89D131AA0197A38E7F5FE513102E44182D2B1595F4702` |
| `textures/block/shrine/granite.png` | `A1D3C1A881B6DC6990EB56932B702CDA78AE0BBF10355FDA90B8A3133B4CCA77` |
| `animations/monolith.animation.json` | `D63D4CBCEF6A3E410EE94F38F5684F7B3E9F94BCC69B4D80C925FBBB61FC1530` |
| `geo/monolith_diagnostic.geo.json` | `0D58B1ED73A8811E10B276DB7E55B29F7248DD047074C0FE786882DF0757E53C` |
| `geo/monolith_diagnostic_alternate.geo.json` | `B2FCBE6841C53313A754A9722E5633957A9AB0334FB96FF05FBCFC42BA7512DC` |
| `models/item/monolith.json` | `E48339859F7AA66BBC08246B8BC65AC1827E28241509518F9884A854739A2BED` |
| `textures/block/monolith/diagnostic_stone.png` | `FE07CE0672EE51D76F2833D1044264C7B65B2ADEB076873D1B07C953509944F4` |
| `textures/block/monolith/diagnostic_alternate_stone.png` | `E320B72F2D0B9429D07DD4E62D264535760797AE6D1F9DD047C082B6736A2C4A` |

All 20 source hashes match both production JARs byte-for-byte. The owner-created untracked `old/`
backup is excluded from resource processing and neither JAR contains an `old/` entry.

## Automated and startup evidence

| Evidence class | Status | Scope |
| --- | --- | --- |
| Automated evidence | available / PASS | 33 structure test classes, 208 JUnit methods, 275,936 hardening logical cases plus corrective geometry/Creative assertions; no failures/errors/skips |
| Refreshed exact-JAR dedicated-server evidence | PASS for server-startup scope | corrected clean-build JAR reached `Done (10.900s)`, normal `stop`, exit 0, all dimensions saved |
| Prior development-client startup | PASS for earlier corrected-resource smoke scope | owner-supplied model/material resources reached atlas startup at `257155c`; the final UV/height-only JSON edit was parsed and package-validated but no new client startup claim is made |
| Authenticated-client evidence | none / UNVERIFIED | no authenticated connection |
| Two-client evidence | none / UNVERIFIED | no concurrent distinct authenticated clients |
| Live gameplay evidence | none / UNVERIFIED | no live placement/cycling/lifecycle matrix |
| Owner visual disposition | limited / accepted correction only | owner accepted the corrected UV/height result and authorized this audit; complete four-facing, representative-variant, seam, Creative, collision, and save/reload evidence remains UNVERIFIED |
| Performance evidence | none / UNVERIFIED | no dense-scene smoke |

Refreshed validation commands from the isolated clone:

- Focused hardening/milestone/item/corrective-render/Creative audit: exit 0 in 31.2s
  (`BUILD SUCCESSFUL in 30s`); 11 classes / 51 methods; 0 failures/errors/skips; 30 tasks:
  1 executed, 29 up-to-date.
- Required structure suite: exit 0 in 35.0s (`BUILD SUCCESSFUL in 34s`); 33 classes / 208
  methods; 0 failures/errors/skips; 30 tasks: 1 executed, 29 up-to-date.
- Full repository suite: exit 0 in 21.5s (`BUILD SUCCESSFUL in 20s`); 33 classes / 208
  methods; 0 failures/errors/skips; 30 tasks: 1 from cache, 29 up-to-date.
- Clean build: exit 0 in 122.3s (`BUILD SUCCESSFUL in 2m 1s`); 36 tasks: 6 executed,
  20 from cache, 10 up-to-date; clean compilation, resources, JAR/JarJar, tests and build passed.
- The relevant warning is Gradle's incubating daemon-JVM discovery notice. Established unrelated repository runtime warnings are recorded below.

The thin `Britannia_Mod_shrines_m2_codex-0.1.7k.jar` is 21,988,615 bytes, has 4,690
entries, manifest `Manifest-Version: 1.0`, and SHA-256
`1AEF96104D311EAF463BC60E1845B7A4165EE87B381FE173192980049E806C11`. The deployable
`Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar` is 22,560,253 bytes, has 4,694 entries,
the same manifest version, and SHA-256
`23DC141268A46E9E7BF1389A49B25405199AE3287624716F301F7AB824512C8A`. Both contain
107 structure-class entries, ten shrine textures, two shrine geometries, two monolith geometries,
two monolith textures, one `ShrineRenderer`, one `ShrineGraniteRenderLayer`, one Creative-tab class,
zero part renderer, zero test/fixture/documentation entry, and all 20 protected assets with zero
missing or mismatched byte. The deployable JAR embeds GeckoLib 4.6.6 and NanoHTTPD 2.2.0 as JarJar
dependencies.

The fresh external server at
`C:\projects\britannia\validation\shrines-monoliths-m10-refresh-b261653` ran Temurin Java 21.0.9
and NeoForge 21.1.72 with `online-mode=true`, found exactly one Britannia JAR, loaded Britannia
0.1.7k and GeckoLib 4.6.6, reached `Done (10.900s)`, received normal console `stop`, exited 0 in
53.5s, and saved overworld, End, Nether, and all dimensions. Its before/after JAR hash remained the
exact deployable hash and it logged zero shrine/monolith/large-structure error. The first invocation
using the shell's legacy Java 8 failed before NeoForge or the JAR loaded (`@user_jvm_args.txt` treated
as a class); the configured Java 21 rerun is the valid result. The pre-existing `TitleScreen`
invalid-dedicated-dist mixin diagnostic, missing optional `britannia_mod.properties`, first-run FML
correction, union-schema warnings, and barrel warning remain unrelated baseline diagnostics.

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
- Complete four-facing shrine symbol orientation, 14/15-voxel profile, granite seam, and stair-boundary review.
- Live Decorative & Graveyard tab contents and configured default-state review.
- Live stair, slab, wall, fence, and attachment adjacency.
- Live monolith horizon alignment.
- Live duplicate-render review.
- Two-client log review.
- Dense-scene performance smoke.

The refreshed Milestone 10 server smoke closes only the corrected exact-JAR server-startup gap. It
does not change any client, multiplayer, gameplay, broader visual, or performance item above. The
owner's `Excellent` acceptance closes the corrective implementation decision but is not recorded as a
complete live matrix.

## Risk register and code-review hotspots

| Risk | Status/mitigation |
| --- | --- |
| No authenticated/two-client/live evidence | accepted for conditional review/merge only; blocks promotion through `POST_MERGE_VALIDATION.md` |
| Owner-approved shrine art and provisional monolith art | shrine package is the current owner-supplied implementation; final monolith artwork/migration remains a non-critical content follow-up |
| Saved worlds/inventories depend on new registry IDs | follow `ROLLBACK_PLAN.md`; never blindly remove registrations from a populated world |
| Existing `TitleScreen` dedicated-dist mixin diagnostic | baseline issue, not introduced by this feature; server still reaches `Done`; review separately |
| Future integration branch advancement | re-run merge-base, merge-tree, tests, build, hashes and runtime smoke against the new authorized target |
| Intentional root-spec Markdown trailing spaces | range `diff --check` diagnostic only; do not alter authoritative specs in this milestone |

Code-review hotspots are `build.gradle` (JUnit/JarJar and `old/` resource exclusion),
`BritanniaMod.java` (common registration/integrity hook), `ClientModSetup.java` (single anchor
renderer), `CreativeTabRegistry.java` (two explicitly configured stacks), `DataComponentRegistry.java`
(persistent/network state), `LargeStructureRegistry.java` (exact IDs/no raw BlockItems),
`InteriorDecoratorToolItem.java` and `structure/interaction/` (server authorization/transaction),
`structure/placement/` (atomicity/protection/rollback), `structure/lifecycle/`
(drop/integrity/reentrancy), `LargeStructureAnchorBlockEntity.java` and `PlacedStructureState.java`
(schema/sync), `ShrineRenderer.java` / `ShrineGraniteRenderLayer.java` / `shrine.geo.json` (bounded
two-material presentation), and `en_us.json` (localization merge review).

## Merge-conflict simulation

Git 2.50.1 three-tree `git merge-tree` analyzed source
`b26165350227e6d46cfde3af91ff738a42e48c11` into target
`62df1dc97c5113a86f9c0f258cb90538f31efe89` with that same merge base. Exit was 0; its
17,532-line simulation contained zero conflict marker and zero `both modified` path. The output
describes the feature additions because the target has exactly zero post-base changed paths.

Semantic review is still required for `build.gradle`, the five registration/integration Java files,
`InteriorDecoratorToolItem.java`, client renderer/material-layer registration, current shrine resources,
and `en_us.json`. No target-side banner or banking path overlaps because the exact target has no
post-branch changes. The shared repository's dirty `banking` work is unrelated and was not
synchronized with this branch.

## Final acceptance disposition

| Playbook criterion | Status | Evidence/disposition |
| --- | --- | --- |
| Design acceptance criteria mapped | PASS for implemented/headless scope | architecture, lifecycle, rendering, content, security, package, and limitation sections in this document |
| Branch ancestry correct | PASS | source/base/target and 17-commit audit above |
| Working tree safe | PASS | only seven audit documents changed; preserved untracked `logs/` and excluded owner `old/` backup |
| Full commit list reviewed | PASS | complete feature-history table above |
| Saved-state behavior reviewed | PASS | schema and compatibility audit above |
| Administrator authority reviewed | PASS | server-owned authorization and security audit above |
| Collision and stair compatibility proven | PASS for deterministic geometry/state scope; live GPU boundary review UNVERIFIED | hardening matrix plus corrective envelope tests; post-merge live gate |
| Monolith offset proven | PASS for deterministic render-transform scope; live horizon review UNVERIFIED | exact `[0,16,0]` tests; post-merge live gate |
| Content inventory complete | PASS | 20 source/package hashes and deterministic catalogue |
| Production JAR inspected | PASS | both artifacts and exact package counts/hashes above |
| Dedicated-server and multiplayer evidence | server startup PASS; multiplayer UNVERIFIED by owner-approved limitation | exact corrected JAR server smoke; Milestone 9 limitation list |
| No critical open question remains | PASS | settled decisions and explicit release-validation items in `OPEN_QUESTIONS.md` |
| Rollback plan exists | PASS | `ROLLBACK_PLAN.md` refreshed through corrected tip |
| Post-merge validation exists | PASS | `POST_MERGE_VALIDATION.md` includes corrected shrine checks and full live matrix |
| No merge or live promotion performed | PASS | isolated branch/documentation audit only |

The unresolved live rows prevent unconditional or production readiness; they do not contradict the
owner-authorized `CONDITIONAL MERGE READINESS` status.

## Recommended procedure and release gate

The branch is suitable for code review and may be merged only through a separately authorized repository-standard pull request or non-destructive merge procedure.

The missing two-authenticated-client live validation remains an accepted owner limitation, not a passing test result.

Production promotion remains blocked pending `POST_MERGE_VALIDATION.md` or a separate explicit release-risk waiver.

Before any merge, re-confirm the target tip, repeat the non-working-tree merge simulation, review the
semantic hotspots, build/test the exact merge result, and retain the transparent eighteen-commit
chronology including both audit commits unless repository policy explicitly chooses a different
non-destructive PR merge method. After merge, run the complete exact-JAR two-client checklist.

No merge was performed while preparing this package. No release, deployment, tag, promotion, push, or commit transfer was performed.
