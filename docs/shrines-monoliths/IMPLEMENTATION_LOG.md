# Shrine and Monolith Implementation Log

## 2026-08-02 - Milestone 0: Repository Discovery and Safe Project Initialization

### Commit and branch state

- Starting branch: `shrines-monoliths`.
- Starting commit: `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
- Integration branch: `patch-18` at `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
- Starting merge base: `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
- Starting ahead/behind (`patch-18...shrines-monoliths`, left/right): `0 0`.
- Ending commit: the Milestone 0 commit containing this log; its full immutable hash is recorded in the Milestone 0 final report. A commit cannot embed its own final hash without changing that hash.
- Branch action: preserved the existing feature branch; no create, reset, rebase, merge, stash, or push was performed.

### Files inspected

- Authoritative specifications: `UltimaCraft_Shrine_and_Monolith_System_Design.md`, `UltimaCraft_Shrine_and_Monolith_Codex_Playbook.md` (both read in full).
- Build/runtime: `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/gradle-daemon-jvm.properties`, `README.md`, resource/source/test/CI inventories.
- Bootstrap/registration: `BritanniaMod.java`; `registry/BlockRegistry.java`; `ItemRegistry.java`; `BlockEntityRegistry.java`; `DataComponentRegistry.java`.
- Persistence/network: `components/WineData.java`; `item/WeightedFishItem.java`; representative block entities (`WineBottleBlockEntity`, `NudgeableBlockEntity`, `AdaptiveRoofBlockEntity`, `DoubleBedBlockEntity`); `network/NetworkHandler.java` and payload implementations.
- Rendering: `ClientModSetup.java`; `client/ClientModelHandler.java`; GeckoLib model/renderer classes; nudgeable client handler; asset roots and render-bound overrides.
- Current multiblocks: carpet teleporter/parts, double bed, triple metal door, tall thin block, dungeon moongate, `Window2x3`, extended chandelier, and oversized decorative blocks.
- Banner feature (read-only, other branch): banner placement planner/executor, mutation plan, footprint/transform, anchor block entity, part block, lifecycle, integrity handler, tests, and docs from `banners-dyetub` via `git show`/`git ls-tree` without switching branches.
- Decorator: `item/InteriorDecoratorToolItem.java`, its registration/localization/model, and every repository call site for the tool.
- Protection: `StructureProtectionHandler`, `SurvivalZoneHandler`, `StructureRegionManager`, `StructurePlacer`, permission/replaceability/build-height/chunk/world-border searches.
- Content: all current source, assets, models, textures, localization, docs, content intake, IDs, and saved-state references matching shrine/monolith/approved identity terms.

### Files created

- `docs/shrines-monoliths/PROJECT_FACTS.md`
- `docs/shrines-monoliths/OPEN_QUESTIONS.md`
- `docs/shrines-monoliths/IMPLEMENTATION_LOG.md`

The two untracked authoritative root specifications were also selected as project-owned commit inputs. No gameplay file was created or modified.

### Repository facts discovered

- Minecraft `1.21.1`, NeoForge `21.1.72`, Gradle `8.9`, NeoGradle UserDev `7.0.165`, Java 21 toolchain, GeckoLib `4.6.6`, mod ID `britannia_mod`.
- Registration uses NeoForge deferred registers; modern instance state has persistent/network-synchronized data components with `Codec` and `StreamCodec`; older custom data uses `CustomData` NBT.
- Block entities use standard save/load, update tag/packet, `setChanged`, and server `sendBlockUpdated` patterns. Networking uses versioned payload registrars and enqueued server work.
- Client renderer registration and GeckoLib are established, including dynamic entity model/texture selection, but no generic safe missing-resource fallback for block models was found.
- Current-branch multiblock implementations are feature-specific and lack a complete reusable atomic placement/lifecycle/integrity contract.
- The unintegrated banner branch contains a much safer planner/executor, persisted footprint, deterministic part resolution, rollback, centralized lifecycle, reentrancy guard, chunk deferral, orphan/repair handling, bounded diagnostics, and focused tests.
- Interior Decorator is server-authoritative for its direct mutations but has no administrator gate and uses mostly literal/log feedback.
- Break protection exists for recorded structures; no general per-cell placement permission service or world-border convention was found.
- No shrine/monolith implementation or asset exists on the active branch. The nine shrine names and monolith `+16` render requirement exist only in the specifications.
- Current branch has no authored Java tests/GameTests/client tests/server-start test/multiblock test/decorator test/JAR-inspection convention.

### Exact commands run and results

The commands below were run from the repository root. Read-only inventories used `rg`, `rg --files`, `Get-Content`, `git grep`, `git show`, and `git ls-tree` with targeted paths and symbols.

```text
git branch --show-current
```

Result: exit `0`; `shrines-monoliths`.

```text
git status --short --branch
```

Result: exit `0`; branch `shrines-monoliths`; pre-existing modified `.gitignore`; pre-existing untracked `.claude/`, two cybersecurity files, two unrelated feature specifications, and the two shrine/monolith root specifications.

```text
git rev-parse HEAD
git rev-parse patch-18
git merge-base shrines-monoliths patch-18
git rev-list --left-right --count patch-18...shrines-monoliths
```

Result: exit `0`; all three commit queries returned `62df1dc97c5113a86f9c0f258cb90538f31efe89`; counts `0 0`.

```text
git branch --list patch-18 shrines-monoliths
git log --oneline --decorate -15
```

Result: exit `0`; both branches exist; current tip `62df1dc` is `Add refill fishing rod barrel at 5213 66 8912 (#400)` and is shared with `patch-18` and the known remote tips shown by the log.

```text
git diff --name-status
git ls-files --others --exclude-standard
```

Result: `.gitignore` was the only tracked modification. Untracked inventory confirmed the unrelated files and the two authoritative specifications.

```text
git ls-files -- build src/generated/resources run runs
git check-ignore -v build build/classes src/generated/resources run .gradle
.\gradlew.bat clean --dry-run --no-daemon --stacktrace
```

Result: no tracked build/generated/run files; `build`, `run`, and `.gradle` are ignored; clean dry-run exit `0`, `:clean SKIPPED`, `BUILD SUCCESSFUL`. `src/generated/resources` does not exist and had no ignore match. The clean was therefore safe for repository/user files.

```text
java -version
.\gradlew.bat --version --no-daemon
```

Result: ambient Java is Oracle `1.8.0_491`; Gradle launcher/daemon uses Microsoft Java `21.0.8`; Gradle `8.9`. Initial sandboxed wrapper calls could not download Gradle due network denial; the approved external call downloaded the pinned distribution successfully.

```text
.\gradlew.bat test --no-daemon --stacktrace
```

Result: exit `0`; `BUILD SUCCESSFUL in 1m 28s`; `30 actionable tasks: 7 executed, 2 from cache, 21 up-to-date`; `processTestResources NO-SOURCE`. Repository source inventory found zero authored test classes, so test total is `0`, failures `0`, errors `0`, skips `0`. Compilation emitted two warnings: missing Javadoc on a Mixin `@Overwrite`, and use of deprecated-for-removal `Item.initializeClient`; general deprecation/unchecked notes were also emitted.

```text
.\gradlew.bat clean build --no-daemon --stacktrace
```

Result: exit `1`; `:clean` ran, then `:neoFormPatch` failed before project compilation with `Patch directory not found`; `BUILD FAILED in 2m 25s`.

```text
.\gradlew.bat build --no-daemon --stacktrace
```

Result: exit `1`; NeoForm reused/reconstructed outputs, then `:compileJava` failed because Minecraft/client classes were absent from the generated compile classpath (examples: `BlockEntityRenderer`, `EntityRenderer`, `BlockPos`, and many `net.minecraft` packages); `BUILD FAILED in 45s`.

```text
.\gradlew.bat build --no-daemon --no-configuration-cache --no-build-cache --stacktrace
```

Result: exit `1`; bounded diagnostic retry reproduced the missing Minecraft/client classpath at `:compileJava`; `BUILD FAILED in 26s`. No build script or gameplay source was changed before any baseline run. The earlier `test` command had compiled the same source successfully, supporting classification as a pre-existing NeoGradle generated-state/environment failure triggered by clean, not a shrine/monolith regression.

```text
git diff --check
```

Result: exit `0` before staging. Because untracked files are outside a normal worktree diff, the staged verification was also run. `git diff --cached --check` returned exit `2` for ten intentional two-space Markdown hard-breaks in the two authoritative root specifications (lines 3-6 of the playbook and 3-8 of the design). Those source specifications were preserved verbatim. `git diff --cached --check -- docs/shrines-monoliths` returned exit `0` for all newly authored documentation.

### Baseline validation summary

- Tests: passed on the unmodified baseline; zero repository-authored tests discovered; 0 failures, 0 errors, 0 skips.
- Clean build: failed on the unmodified baseline in NeoGradle generated state, then reproduced as an incomplete Minecraft classpath. No unrelated fix was attempted.
- Production artifact: none produced after the safe clean; JAR inspection was therefore unavailable.
- Git whitespace check: worktree check passed; the newly authored documentation passed its staged check; the verbatim authoritative specifications retain ten intentional Markdown hard-break warnings.

### Warnings and pre-existing failures

- Git repeatedly warned that `C:\Users\dusti\.config\git\ignore` was inaccessible. Repository commands otherwise succeeded.
- The staged whitespace check reports ten trailing-space warnings in the two root specifications. They are intentional Markdown line breaks in metadata and were not rewritten.
- The ambient Java 8 differs from the repository's working Gradle Java 21 launcher; the wrapper correctly selected Java 21.
- The two compiler warnings and generic deprecation/unchecked notes predate this documentation-only milestone.
- The clean-build failure is pre-existing: it occurred before any Milestone 0 file edit, while `HEAD`, build scripts, and gameplay sources were unchanged; the same sources compiled in the immediately preceding test run.

### Manual checks

Performed:

- Read both authoritative root documents completely.
- Verified branch identity, tips, ancestry, merge base, ahead/behind, tracked diff, and untracked inventory.
- Verified clean scope before execution.
- Inspected all requested source/resource/system categories and the banner branch without switching.
- Verified that no shrine/monolith gameplay or asset exists and that this milestone changes documentation only.

Unperformed:

- Client launch, dedicated-server launch, multiplayer, GameTest, and manual gameplay validation.
- Visual model/texture review, because no shrine/monolith assets exist.
- Production-JAR inspection, because the baseline clean build did not produce a JAR.
- Integration, merge, push, rebase, reset, stash, or cleanup of unrelated files.

### Preserved unrelated work

The following pre-existing paths were not edited or staged: `.gitignore`, `.claude/`, `CyberSecurity_Hobbit.pdf`, `CyberSecurity_Phishing_IAM.zip`, `UltimaCraft_Farming_Skill_Progression_Codex_Milestones_12_17.md`, and `UltimaCraft_Flower_System_Corrective_Milestone_11_Single_Model_Two_Textures.md`.

### Known limitations

- Clean production build is not green due the documented baseline NeoGradle/generated-classpath failure.
- The completed banner architecture is not on the active/integration branch and was only inspected through Git objects.
- No supplied shrine/monolith asset can be measured or visually validated.
- Administrator authorization and a general placement-permission contract are not settled by current code.

### Deviations from the design

None. Milestone 0 added no gameplay implementation. Baseline build repair was intentionally not attempted because it is unrelated and explicitly outside this milestone.

### Next permitted milestone

Milestone 1 only. Do not begin it.

## 2026-08-02 - Milestone 1: Definition Catalogue, Footprints, and Transform Foundation

### Commit and branch state

- Starting branch: `shrines-monoliths`.
- Starting commit: `b10efd3382f74bf8e1970588bcd0b57869c8771a`.
- Integration branch: `patch-18` at `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
- Starting merge base: `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
- Starting ahead/behind (`patch-18...shrines-monoliths`, left/right): `0 1` (zero behind, one ahead).
- Ending commit: the single Milestone 1 commit `feat(structures): add shrine and monolith definitions`; its full immutable hash is recorded in the final report because a commit cannot contain its own final hash.
- Branch action: no switch, reset, recreate, merge, rebase, stash, clean, or push was performed.

### Files changed

- `build.gradle`: adds only JUnit Jupiter 5.10.2, the matching platform launcher, and `useJUnitPlatform()` for the previously empty conventional test source set.
- `src/main/java/com/seggellion/britannia_mod/structure/definition/StructureIdentity.java`: stable family/variant/display/resource identities, availability, and approved/provisional metadata.
- `StructureGeometry.java`: dimensions, local offsets, deterministic rectangular footprints, placement/collision/render metadata, and voxel-to-block conversion.
- `StructureTransform.java`: the shared pure four-facing forward and reverse transform.
- `StructureDefinition.java`: immutable family and variant records.
- `DefinitionDiagnostic.java`, `DefinitionValidation.java`, `StructureDefinitionValidator.java`: typed structured validation and initial-family compatibility rules.
- `StructureCatalogue.java`: immutable validated family catalogue with family-scoped variant lookup.
- `StructureVariantCycler.java`: deterministic enabled/compatible cycle selection, wrap-around, disabled skipping, and missing-ID refusal.
- `ShrineMonolithDefinitions.java`: the production logical shrine and monolith catalogue without concrete client paths.
- `src/test/java/com/seggellion/britannia_mod/structure/definition/DefinitionFixtures.java`: test-only valid and invalid definition builders, including a two-model monolith fixture.
- `StructureFootprintTransformTest.java`, `ShrineMonolithCatalogueTest.java`, `StructureDefinitionValidationTest.java`, `MilestoneOneBoundaryTest.java`: 48 focused tests in four classes.
- `docs/shrines-monoliths/PROJECT_FACTS.md`: records the proven generated-artifact cause/recovery and current definition/test facts.
- `docs/shrines-monoliths/OPEN_QUESTIONS.md`: removes the resolved NeoGradle build blocker.
- `docs/shrines-monoliths/IMPLEMENTATION_LOG.md`: records this milestone.

### Definition architecture

- Definitions are immutable Java records in common-side code; no reloadable data-pack framework was introduced.
- Family identity, variant identity, display identity, and logical client-resource identity are independent values.
- A client resource contains a stable logical identity plus an optional concrete namespaced path and explicit `AVAILABLE`/`UNAVAILABLE` state. Missing resources therefore do not change or substitute family/variant identity.
- Families own dimensions, ordered footprint, anchor, placement/collision/render contracts, geometry mode, default variant, and variants. Variants repeat the placement contract so catalogue validation can reject incompatible cycle candidates before future gameplay uses them.
- Diagnostics are `DefinitionDiagnostic` records with a typed `Code`, location, and message; validation is non-mutating.

### Production catalogue and test-only fixtures

- Production family `shrine`: approved `2 x 1 x 2`, four cells, shared logical geometry, zero render correction, and nine approved logical variants in explicit order: `honesty`, `compassion`, `valor`, `justice`, `sacrifice`, `honor`, `spirituality`, `humility`, `chaos`.
- All shrine display metadata is unresolved and every logical model/texture resource is explicitly unavailable. No model, texture, localization, or registry path is claimed.
- Production family `monolith`: approved `3 x 3 x 2`, eighteen cells, per-variant geometry, and `[0, 16, 0]` voxel render offset.
- Because no supplied monolith content exists and the validated family shape requires a default, production contains exactly one `diagnostic_missing_content` variant. It is explicitly provisional, enabled only as the structural default, non-player-facing, unresolved, and missing-resource-aware; it is not presented as supplied or final content.
- `DefinitionFixtures` creates a test-only second monolith variant (`test_second_model` with logical model `test_second_monolith_model`) to prove per-variant models, plus bounded mutations for every rejection/cycle case. No test fixture is registered or packaged as player content.

### Facing and footprint conventions

- `FACING` points outward from the front. The anchor is the lower front-left cell. Local X is viewer-right, local Y is up, and local Z is away from the viewer into the structure.
- Basis: `right = FACING.counterClockwise()`, `away = FACING.opposite()`.
- Transform: `world = anchor + right * localX + UP * localY + away * localZ`.
- With anchor `[10, 64, 20]` and local `[1, 2, 1]`, explicit expected results are North `[9, 66, 21]`, East `[9, 66, 19]`, South `[11, 66, 19]`, and West `[11, 66, 21]`.
- Footprint ordering is nested `Y`, then `Z`, then `X`: layers bottom-to-top; rows front-to-back; cells left-to-right. `[0, 0, 0]` is therefore first and occurs exactly once.

### Validation diagnostics

Typed codes cover duplicate family/variant IDs and cycle positions; invalid cycle position; missing/disabled default; invalid/oversized dimensions; empty/duplicate/out-of-bounds/out-of-encoding footprints; invalid/missing/duplicate anchor; invalid resource namespace/path and availability mismatch; missing content status; missing shared geometry; shrine/monolith family contract mismatch; shrine geometry change; cross-family membership; variant dimensions/footprint/placement/collision/render-origin/render-offset mismatch; and incompatible enabled cycle candidates.

### Exact commands and results

All commands ran from the repository root.

```text
git branch --show-current
git status --short --branch
git rev-parse HEAD
git merge-base shrines-monoliths patch-18
git rev-list --left-right --count patch-18...shrines-monoliths
git log --oneline --decorate -15
git show --stat --oneline --decorate b10efd3382f74bf8e1970588bcd0b57869c8771a
git diff-tree --no-commit-id --name-status -r b10efd3382f74bf8e1970588bcd0b57869c8771a
```

Result: branch, HEAD, merge base, and divergence exactly matched the authorization. Milestone 0 contains only the two root specifications and three project documents. Unrelated modified/untracked paths were recorded and preserved.

```text
.\gradlew.bat test --no-daemon --stacktrace --no-configuration-cache
```

Pre-implementation result: exit `1` at `:compileJava`; first material failures were missing `BlockEntityRenderer` and `net.minecraft.core.BlockPos`. NeoForm tasks claimed up-to-date.

Generated-artifact inspection found the supplied NeoForge JAR was 9,265,437 bytes with exactly 4,096 class entries and lacked both `BlockPos` and the client block-entity renderer. The earlier rename-stage JAR contained both, proving generated recompile/pack state was incomplete.

The pinned NeoGradle task inventory exposed its normal NeoForm supply pipeline. A documented binary-mode property was tried as a bounded diagnostic but this plugin version continued to select the decompile/recompile pipeline. The successful recovery was:

```text
.\gradlew.bat supplyRawJarForneoFormJoined1.21.1-20240808.144430 selectRawArtifactNg_dummy_ng.net.neoforged_neoforge_21.1.72 --rerun-tasks '-Pneogradle.subsystems.recompiler.maxMemory=4g' --no-daemon --no-configuration-cache --stacktrace
```

Result: exit `0`; `BUILD SUCCESSFUL in 3m 4s`; the rebuilt supplied artifact was 21,955,724 bytes with 9,729 class entries and contained both missing classes. Only ignored generated/cache outputs changed.

```text
.\gradlew.bat test --no-daemon --stacktrace --no-configuration-cache
```

Repaired unmodified baseline result: exit `0`; `BUILD SUCCESSFUL in 41s`; `test NO-SOURCE`, zero authored baseline tests; the two known pre-existing compiler warnings remained.

```text
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.structure.definition.StructureFootprintTransformTest' --no-daemon --no-configuration-cache --stacktrace
```

Final focused class result: exit `0`; 1 class, 12 tests, 0 failures, 0 errors, 0 skipped.

```text
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.structure.definition.*' --no-daemon --no-configuration-cache --stacktrace
```

Final complete feature-package result: exit `0`; `BUILD SUCCESSFUL in 38s`; 4 classes, 48 tests, 0 failures, 0 errors, 0 skipped.

```text
.\gradlew.bat test --no-daemon --no-configuration-cache --stacktrace
```

Full repository test result: exit `0`; `BUILD SUCCESSFUL in 18s`; 4 classes, 48 tests, 0 failures, 0 errors, 0 skipped.

```text
git diff --check -- build.gradle src/main/java/com/seggellion/britannia_mod/structure/definition src/test/java/com/seggellion/britannia_mod/structure/definition docs/shrines-monoliths
```

Result before the final documentation edit: exit `0`. It is rerun during the final audit.

```text
git ls-files -- build .gradle run runs src/generated/resources
.\gradlew.bat clean --dry-run --no-daemon --no-configuration-cache
.\gradlew.bat clean build --no-daemon --no-configuration-cache --stacktrace
```

Result: no generated path is tracked; dry-run exit `0` with `:clean SKIPPED`; actual clean build exit `0`, `BUILD SUCCESSFUL in 2m 1s`, 36 actionable tasks (6 executed, 23 from cache, 7 up-to-date). The normal clean-build command required no source/configuration workaround.

### Manual checks

Performed:

- Read both root specifications and all three project documents completely.
- Verified branch identity, ancestry, Milestone 0 scope, worktree preservation, and clean scope.
- Inspected Gradle configuration, task inventory, generated NeoForm stages, resolved dependencies, and generated JAR contents.
- Confirmed the production `-all.jar` contains every definition class and no test fixture class.
- Searched the production definition package for client imports, registration APIs, block entities, world mutation, actual voxel shapes, Interior Decorator code, and block/item registry references; none were found.
- Confirmed no concrete client resource path, model, texture, localization, blockstate, or asset was introduced.

Not performed:

- Client launch, dedicated-server launch, multiplayer, GameTest, or in-world checks; Milestone 1 has no registered world content.
- Visual asset or horizon review; no shrine/monolith assets exist.
- Placement, lifecycle, persistence, rendering, collision-shape, drops, decorator, or Interior Decorator checks; all belong to later milestones.

### Build-state outcome and limitations

- The NeoGradle baseline issue is resolved for this workspace: the generated artifact was repaired through NeoGradle's own pipeline and the subsequent normal clean production build passed.
- Existing compiler warnings remain: missing Javadoc on a Mixin `@Overwrite` and deprecated-for-removal `Item.initializeClient` usage. Neither is related to this feature.
- Logical shrine identities are production definitions, but all client resource paths and translation keys remain unresolved.
- Monolith production content remains unavailable; only the explicit non-player-facing diagnostic default represents the validated family contract.
- No manual gameplay validation is possible or required at this pure-definition milestone.

### Deviations

- No design deviation. The extra `playerFacing` flag is the smallest explicit safeguard required to ensure the structurally necessary monolith diagnostic default cannot be mistaken for approved player content.
- No tracked recompiler-memory setting was added: the 4 GB value was used only for bounded generated-state recovery, and the normal clean build then passed unchanged.

### Preserved unrelated work

The pre-existing modified `.gitignore` and untracked `.claude/`, `CyberSecurity_Hobbit.pdf`, `CyberSecurity_Phishing_IAM.zip`, `UltimaCraft_Farming_Skill_Progression_Codex_Milestones_12_17.md`, and `UltimaCraft_Flower_System_Corrective_Milestone_11_Single_Model_Two_Textures.md` were not edited or staged.

### Next permitted milestone

Milestone 2 only. Do not begin it.

## 2026-08-03 - Milestone 2: Anchor, Parts, and Atomic Diagnostic Shrine Placement

### Isolation, commit, and branch state

- Shared repository (read-only): `C:\projects\britannia\mod\Britannia_Mod`.
- Independent clone: `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`.
- Clone creation: `git clone --local --no-hardlinks C:\projects\britannia\mod\Britannia_Mod C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`.
- The clone has its own `.git` directory and common directory at the clone root; it is not a linked worktree.
- Starting branch: `shrines-monoliths`, tracking the local-clone `origin/shrines-monoliths`.
- Starting commit: `bf42b16b818b80a3303c78e21c5f9ec75602fd25`.
- Integration branch: clone-local `origin/patch-18` at `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
- Starting merge base: `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
- Starting divergence (`origin/patch-18...shrines-monoliths`, left/right): `0 2` (zero behind, two ahead).
- Starting isolated working tree: clean (`## shrines-monoliths...origin/shrines-monoliths`).
- Ending commit: the single Milestone 2 commit `feat(structures): add atomic multiblock shrine placement`; its full immutable hash is recorded in the final report.
- No switch, merge, rebase, reset, stash, clean, network fetch, push, or origin change was performed against the shared repository.

### Files changed

- `BritanniaMod.java`: registers the isolated large-structure deferred registers.
- `registry/LargeStructureRegistry.java`: registers exactly one anchor, one part, one anchor block entity, and one shrine item; it registers no anchor/part `BlockItem` and no monolith item.
- `structure/item/ShrineItem.java`: family placement item configured to the approved Honesty diagnostic variant and routed to server placement.
- `structure/multiblock/`: cell role/cell records, immutable in-memory placed state, invisible cell-bounded anchor and part blocks, full future part-offset encoding, deterministic reverse anchor lookup, and the anchor-only block entity.
- `structure/placement/`: typed failures, immutable plan/result, read-only world boundary, mutation boundary, planner, executor, and live server adapter.
- `assets/britannia_mod/blockstates/large_structure_*.json`: invisible diagnostic air-model blockstates for anchor and parts.
- `assets/britannia_mod/models/item/shrine.json`: diagnostic vanilla-stone item presentation; no shrine artwork was fabricated.
- `assets/britannia_mod/lang/en_us.json`: diagnostic item name and concise placement failure feedback.
- `src/test/java/com/seggellion/britannia_mod/structure/{placement,multiblock,milestone,testsupport}`: 18 Milestone 2 tests in four test classes plus the repository-pattern test registration helper.
- `docs/shrines-monoliths/PROJECT_FACTS.md` and this log: verified current architecture and evidence.

### Architecture and behavior

- The existing Milestone 1 facing convention remains authoritative. The live floor placement derives an outward facing opposite the player's horizontal direction, then delegates every world position to `StructureTransform`.
- Planning is mutation-free. It validates the actual shrine item; family and variant catalogue entries; top-face floor placement; all four world-border/build-height positions; all required already-loaded chunks; unrelated structure occupancy; replaceability; `mayInteract`/`mayUseItemAt`; anchor block-entity compatibility; and every encoded part state.
- The immutable plan owns one anchor plus three parts in the catalogue's deterministic `y/z/x` order and captures exact original/expected block states for rollback.
- Execution places the anchor, initializes its in-memory authoritative family/variant/facing/footprint state, places three parts, verifies every block and reverse part-to-anchor relation, synchronizes the anchor, emits success effects, and only then consumes one survival item. Creative consumes zero.
- Any failed mutation restores cells in reverse order with `UPDATE_SUPPRESS_DROPS`. Rollback does not overwrite an unexpected unrelated replacement detected during the transaction.
- Part state supports the complete future ranges `x=0..2`, `y=0..2`, `z=0..1`, but this milestone plans only the four shrine offsets. Parts have no block entity and no family/variant state.
- Anchor and part selection/collision shapes are exactly one local cell (`0..16` voxels on every axis). Adjacent perimeter positions are not reserved or inspected as occupied cells.
- Presentation is intentionally diagnostic: structure blocks render invisibly and the item reuses vanilla stone. No final shrine texture/model, block-entity renderer, monolith placement, Interior Decorator integration, persistence codec, configured drops, or lifecycle repair was added.

### Exact commands and results

Shared-repository read-only validation before cloning:

```text
git -C C:\projects\britannia\mod\Britannia_Mod rev-parse --show-toplevel
git -C C:\projects\britannia\mod\Britannia_Mod branch --show-current
git -C C:\projects\britannia\mod\Britannia_Mod rev-parse HEAD
git -C C:\projects\britannia\mod\Britannia_Mod status --short --branch
git -C C:\projects\britannia\mod\Britannia_Mod rev-parse shrines-monoliths
git -C C:\projects\britannia\mod\Britannia_Mod rev-parse patch-18
git -C C:\projects\britannia\mod\Britannia_Mod merge-base shrines-monoliths patch-18
git -C C:\projects\britannia\mod\Britannia_Mod rev-list --left-right --count patch-18...shrines-monoliths
```

Result: the required branch tips, merge base, and `0 2` divergence matched. The shared tree had a pre-existing modified `ModConfig.java` plus untracked `.claude/` and unrelated root documents; none was copied or modified.

```text
.\gradlew.bat compileJava --no-daemon --no-configuration-cache --stacktrace
```

Result: exit `0`; `BUILD SUCCESSFUL in 2m 8s`; 26 actionable tasks. Only the two known pre-existing compiler warnings remained.

The first focused run failed at `compileTestJava` because a test used an impossible direct `instanceof EntityBlock` check on a final class. The assertion was corrected to reflection without weakening behavior. Two subsequent focused attempts exposed the repository's plain-JUnit bootstrap requirements (`Not bootstrapped`, then `Registry is already frozen`). The test helper was aligned with the proven banner sequence: version detection, vanilla bootstrap, NeoForge `GameData.unfreezeData()`, and explicit registration of test-only blocks/item. Production code was unchanged by these harness corrections.

```text
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.structure.placement.*' --tests 'com.seggellion.britannia_mod.structure.multiblock.*' --tests 'com.seggellion.britannia_mod.structure.milestone.*' --no-daemon --no-configuration-cache --stacktrace
```

Final focused result: exit `0`; `BUILD SUCCESSFUL in 28s`; 4 classes, 18 tests, 0 failures, 0 errors, 0 skipped.

```text
.\gradlew.bat test --no-daemon --no-configuration-cache --stacktrace
```

Result: exit `0`; `BUILD SUCCESSFUL in 33s`; 8 classes, 66 tests, 0 failures, 0 errors, 0 skipped.

```text
.\gradlew.bat clean build --no-daemon --no-configuration-cache --stacktrace
```

Result: exit `0`; `BUILD SUCCESSFUL in 2m 13s`; 36 actionable tasks (8 executed, 19 from cache, 9 up-to-date). Production normal and `-all` JARs were created.

```text
jar tf build\libs\Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar
jar tf build\libs\Britannia_Mod_shrines_m2_codex-0.1.7k.jar
```

Result: both JARs contain the new registry, item, multiblock and placement classes plus both diagnostic blockstates and the shrine item model. No milestone-specific renderer or anchor/part item model was added.

### Automated coverage

- Exact four-cell shrine plans for every horizontal facing and reverse part-to-anchor resolution.
- Wrong item, monolith family, missing variant, invalid clicked face/facing, world bounds, unloaded chunk, unrelated structure, occupied target, protection denial, block-entity incompatibility, and part-encoding failures remain non-mutating and non-consuming.
- Survival and creative consumption behavior.
- Failure injection at every anchor/part write, every final verification cell, missing block entity, rejected/mismatched/throwing state assignment, synchronization, and rollback failure.
- Exactly four successful cell mutations, zero transaction drops, and success effects only after complete verification.
- Full future part-offset encoding for four facings (68 non-anchor state/facing combinations), invalid offset rejection, cell-bounded selection/collision shapes, immovable blocks, and no part block entity/authoritative state.
- Registration counts, no anchor/part `BlockItem`, server authority, no chunk force-load path, drop-suppressed rollback, diagnostic resources, and explicit later-milestone exclusions.

### Manual checks

Performed:

- Read both authoritative root specifications and all three project documents completely.
- Inspected the approved Milestone 1 commit, current registration/block-entity/item/protection conventions, and the unintegrated banner planner/executor patterns without switching branches.
- Verified isolated clone branch, ancestry, clean start, independent Git metadata, and production JAR contents.

Not performed:

- In-game shrine placement, stair ring, client launch, dedicated-server launch, multiplayer, reload, break/drop, or visual review.
- The diagnostic item is obtainable through the normal registry (`/give`) but no interactive client was launched during this automated milestone run.
- Persistence, whole-structure break/lifecycle, pick block, explosions, orphan/repair, final rendering, and decorator cycling belong to later milestones and were not tested or claimed.

### Known limitations and deviations

- The placed family/variant/facing/footprint state is intentionally in-memory only. Save/load and item reconstruction begin in Milestone 3.
- Breaking cells does not yet perform whole-structure teardown or configured drops; full lifecycle behavior is explicitly excluded from Milestone 2.
- The diagnostic structure is invisible and no shrine artwork or final asset path exists in the repository.
- No manual game validation was performed, so live stair adjacency and interaction remain unverified despite pure shape/plan coverage.
- No design deviation was introduced.

### Next permitted milestone

Milestone 3 only. Do not begin it.

## 2026-08-03 - Corrective Milestone 2 Evidence Audit

- Kept `82a4730ac250045bc24bcf3677a2f3958c785f45` immutable and added one narrow
  corrective commit; no squash, rebase, merge, push, or shared-repository mutation occurred.
- Made required chunks, per-cell authorization, and anchor-initialization preflight evidence
  explicit and immutable in `ShrinePlacementPlan`.
- Required protection checks for all four cells even when one is denied, and added typed
  anchor-initialization rejection before original-state capture or mutation.
- Made rollback ownership explicit: transaction-owned cells restore, already-original cells
  remain untouched, and unrelated replacements are preserved while returning the typed
  incomplete-rollback failure.
- Added exact cross-chunk loaded/unloaded, all-cell protection, plan-immutability, reverse
  resolution, rollback-ownership, full collision-profile, and registered-stair adjacency tests.
- Final automated inventory: 10 test classes, 74 tests, 0 failures, 0 errors, 0 skipped.
- Focused structure suite, full repository suite, and clean build all pass. In-game placement
  remains unperformed because this Codex environment has no reliable gameplay input mechanism.
- Milestone 3 was not started.

## 2026-08-03 - Milestone 3: Shrine Persistence, Whole-Structure Lifecycle, and Integrity

### Isolation, commit, and branch state

- Shared repository (read-only): `C:\projects\britannia\mod\Britannia_Mod`.
- Independent clone: `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`.
- Starting branch: `shrines-monoliths`.
- Starting commit: `e963de2fbf641cb7b686c3676ad45d605c6c9eee`.
- Clone-local integration reference: `origin/patch-18` at `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
- Starting merge base: `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
- Starting divergence (`origin/patch-18...shrines-monoliths`, left/right): `0 4`.
- Starting isolated worktree: clean.
- Shared-repository starting state: branch `shrines-monoliths`, HEAD `bf42b16b818b80a3303c78e21c5f9ec75602fd25`, modified `ModConfig.java`, and untracked `.claude/` plus unrelated root documents. It was inspected read-only and never used as a build/edit target.
- At the final read-only check, the shared repository had independently moved to branch `banking`, HEAD `f94b42ebe9ec38443e51ce7ca4325e810b3c4253`, with the same modified `ModConfig.java`, `.claude/`, and unrelated root documents plus two banking specifications. This concurrent shared state was neither caused nor altered by Milestone 3.
- Ending commit: the single Milestone 3 commit `feat(structures): persist and protect multiblock shrines`; its immutable full hash is recorded in the final report because a commit cannot contain its own hash.
- No push, transfer, merge, rebase, reset, stash, branch switch, network fetch, or shared-repository mutation occurred.

### Files changed

- `BritanniaMod.java`, `DataComponentRegistry.java`, `LargeStructureRegistry.java`: register the shrine instance component and bounded integrity events without changing the one-anchor/one-part/one-anchor-entity/one-item content topology.
- `structure/item/ShrineItem.java`, `ShrineItemState.java`, `ShrineItemStateAccess.java`, `ShrineItemTransfer.java`: versioned configured item state, validation, raw-stack defaulting, and exact placed-state recovery.
- `structure/multiblock/PlacedStructureState.java`, `PlacedStructureStatus.java`, `LargeStructureAnchorBlockEntity.java`: versioned persistence, typed load status, synchronization, and authoritative placed footprint.
- `LargeStructureAnchorBlock.java`, `LargeStructurePartBlock.java`: centralized break/explosion/external-replacement/pick behavior, empty vanilla loot path, immovable piston reaction, and dry non-waterloggable cells.
- `ShrinePlacementPlanner.java`, `ShrinePlacementService.java`: configured-item planning and placement reentrancy guard.
- `structure/lifecycle/ShrineRemovalCause.java`, `ShrineLifecycleService.java`, `ShrineIntegrityService.java`, `ShrineIntegrityHandler.java`: whole-structure lifecycle, exact configured drop, loaded-chunk integrity repair/orphan handling, bounded scheduling, and bounded diagnostics.
- Six focused test classes plus `ShrineLifecycleTestWorld` and the existing registration helper: persistence, component, placement, lifecycle, integrity, and milestone-scope coverage.
- `PROJECT_FACTS.md` and this log: only newly proven Milestone 3 facts and evidence.

### Persistence and item schemas

- Schema version is `1` for both placed state and item state.
- Anchor NBT has root key `shrine_state` containing `schema_version` (int), `family_id` (string), `variant_id` (string), `facing` (lower-case direction string), and `placed_footprint` (ordered list). Each footprint entry is a compound with integer keys `x`, `y`, and `z`.
- The saved ordered footprint is authoritative for the placed instance. Integrity, teardown, and repair use it; later catalogue changes cannot add, move, or resize existing cells.
- Block-state `FACING` is authoritative. Persisted `facing` is validation-only; a mismatch is structurally invalid and fails closed.
- Missing family and variant IDs are preserved in decoded state with typed `MISSING_FAMILY_DEFINITION` or `MISSING_VARIANT_DEFINITION` status. They are not silently substituted.
- Malformed state and unsupported future schema produce typed failure status, no configured item, no fabricated default identity, and conservative cleanup behavior.
- Item component ID is `britannia_mod:shrine_instance_state`. Its schema fields are `schema_version`, `family_id`, and `variant_id`; facing and footprint remain placed-anchor concerns.
- `ShrineItemState.CODEC` uses `RecordCodecBuilder`; `STREAM_CODEC` explicitly writes VarInt schema and UTF family/variant IDs. `DataComponentType` registers both persistent and network codecs.
- A raw `/give @s britannia_mod:shrine` stack has no component and intentionally resolves to `shrine`/`honesty`. A configured stack retains exact family/variant IDs. Placement rejects unknown or non-shrine catalogue content safely.
- Actual NBT disk save/load, update-tag creation/application, packet application, component codec, component stream codec, and full `ItemStack` persistent codec are tested. A full `ItemStack` network stream is not claimed because the plain-JUnit built-in item registry is not a synchronized connection registry.

### Lifecycle and integrity policy

- `ShrineLifecycleService` is the sole cleanup/drop/pick authority. Both blocks return empty vanilla loot lists, preventing a second loot path.
- Survival `onDestroyedByPlayer` resolves anchor membership, removes matching persisted cells with suppressed drops, and calls `Block.popResource` exactly once with the configured shrine stack. `playerDestroy` is a no-op.
- Creative uses the same complete teardown path and produces zero items. Invalid fragments remove only the selected shrine cell with zero drops.
- Explosion policy is complete matching-cell cleanup with zero drops. Every affected callback converges on the same guarded lifecycle service.
- External replacement policy preserves the newly installed unrelated block, removes only other still-matching persisted cells, and produces no drop.
- Pick block resolves either anchor or part to the already-loaded synchronized anchor state and returns the same configured family/variant item without mutation or force-loading. Invalid membership returns empty.
- Reentrancy keys use level object identity (`==`, identity hash) plus anchor `BlockPos`. Placement and removal guards are distinct sets and always clear in `finally`; recursive removal and duplicate drops are suppressed.
- `ShrineIntegrityHandler` queues each loaded chunk and its immediate chunk neighbors in insertion order so a newly loaded empty footprint chunk rechecks an adjacent anchor. It processes at most 64 candidate chunks globally per server tick, caps each level at 4,096 pending chunks, removes level queues on unload, obtains only `getChunkNow`, and scans only already-loaded cells. No ticket or force-load API is used.
- A part whose candidate anchor chunk is unavailable is deferred. A part is removed as a definitive orphan only after the candidate anchor chunk is loaded and valid membership is absent.
- Missing parts repair only when every persisted target chunk is loaded and the cell is replaceable with no block entity. Repair recreates the expected part from the persisted offset and synchronizes the anchor.
- An obstruction is never overwritten. It is preserved while the remaining still-matching shrine cells are removed with zero drop. Failed/racing repair releases the placement guard before cleanup.
- Anchor and part piston reaction is `BLOCK`. Both cells are non-waterloggable, expose empty fluid state, cannot be fluid-replaced, and cannot silently split through fluid placement.
- Placement/repair flags are `Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS`. Lifecycle removal flags are `Block.UPDATE_ALL_IMMEDIATE | Block.UPDATE_SUPPRESS_DROPS`. Anchor synchronization uses `Block.UPDATE_CLIENTS`.

### Exact commands and results

All build commands ran from `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`.

```text
.\gradlew.bat compileJava --no-daemon --no-configuration-cache --stacktrace
```

Result: exit `0`; 42.2 seconds; 26 tasks (1 executed, 25 up-to-date). The two known warnings remained: missing Javadoc on a Mixin `@Overwrite` and deprecated-for-removal `Item.initializeClient`.

```text
.\gradlew.bat test --tests <six exact Milestone 3 classes> --no-daemon --no-configuration-cache --stacktrace
```

Final result: exit `0`; 53.3 seconds; 6 classes, 47 tests, 0 failures, 0 errors, 0 skipped; 30 tasks (3 executed, 27 up-to-date). The exact classes were `LargeStructurePersistenceTest`, `ShrineItemStateTest`, `ShrineLifecycleServiceTest`, `ShrineIntegrityServiceTest`, `ShrineConfiguredPlacementTest`, and `MilestoneThreePolicyAndScopeTest`. It emitted only the two known production warnings plus a test deprecation note.

```text
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.structure.*' --no-daemon --no-configuration-cache --stacktrace
```

Final result: exit `0`; 30.7 seconds; 16 classes, 121 tests, 0 failures, 0 errors, 0 skipped; 30 tasks (1 executed, 29 up-to-date).

```text
.\gradlew.bat test --no-daemon --no-configuration-cache --stacktrace
```

Final result: exit `0`; 26.5 seconds; 16 classes, 121 tests, 0 failures, 0 errors, 0 skipped; 30 tasks (1 executed, 29 up-to-date).

```text
git ls-files -- build .gradle run runs logs src/generated/resources
.\gradlew.bat clean build --no-daemon --no-configuration-cache --stacktrace
```

Result: no build/cache/run/log/generated path was tracked before clean. Final clean build exit `0`; 1 minute 32.4 seconds; 16 classes, 121 tests from cache, 0 failures, 0 errors, 0 skipped; 36 tasks (6 executed, 20 from cache, 10 up-to-date). Both production JARs were produced.

```text
jar tf build\libs\Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar
jar tf build\libs\Britannia_Mod_shrines_m2_codex-0.1.7k.jar
```

Result: both JARs contain the persistence, item-component, lifecycle, integrity, anchor/part, placement classes and diagnostic blockstate/item-model resources. Both contain zero test/MilestoneThree classes, zero shrine/monolith renderer classes, zero final shrine/monolith texture/geo/animation assets, and zero monolith placement classes. `InteriorDecoratorToolItem` is present only as unchanged pre-existing production content.

### Automated, simulated, and manual coverage

- Actual registered blocks, item, block-entity type, and data-component type are exercised in plain JUnit after repository-pattern registry bootstrap.
- Actual `CompoundTag`, holder lookup, block-entity save/load/update-tag/packet-application, persistent codec, stream codec, block states, reverse transforms, `ItemStack`, piston reaction, shapes, and fluid APIs are exercised.
- Lifecycle and integrity world mutation use the production services through a narrow deterministic `WorldAccess` adapter. It simulates loaded/unloaded chunks, replacement, failure, recursion, drops, and synchronization; it is not a real `ServerLevel` or GameTest.
- Reflection is limited to constructing the actual clientbound block-entity packet for a detached block entity; application uses the inherited production `onDataPacket` path. Static scope tests inspect source/registration boundaries where a live runtime is unnecessary.
- No `@GameTest` implementation exists and Milestone 3 did not manufacture a new framework. No client, dedicated server, multiplayer, data-generation, or live-world task was introduced.
- Manual checks performed: complete source/API/JAR/scope inspection and exact Git isolation/ancestry/status checks.
- Manual interaction checks not performed: all 19 requested in-game steps (obtain/place/save/reload/break/drop/re-place/creative/pick/repair/obstruct/chunk boundary/piston/fluid), because reliable client control was unavailable. Startup logs were not treated as gameplay evidence.

### Known limitations and Milestone 4 deferrals

- Live `ServerLevel`, client-server synchronization, save-file reload, player break, explosion, piston, fluid, and cross-chunk behavior remain unverified in-world despite API-level and adapter coverage.
- Full `ItemStack` network encoding through a synchronized registry connection remains unverified; the registered component stream codec itself round-trips.
- No final shrine block-entity renderer, geometry, texture, or variant rendering was added. No Interior Decorator integration/cycling, monolith item/placement/renderer/cycling, recipe, command, NPC, or cross-family conversion was added.
- No design deviation was introduced.

### Next permitted milestone

Milestone 4 only. Do not begin it.

## 2026-08-03 - Corrective Milestone 4 Review: Unapproved Rendering Reversal

### Decision and Git policy

- Starting HEAD and rejected commit: `e1fcae20ae2f2af796b7b44ad9ca9d65352cffff` (`feat(shrines): render approved texture variants`). Approved Milestone 3 remains `b863472940d073434490516df457c7e9ad7a6404`.
- The commit was rejected because it bypassed the mandatory approved-asset inventory prerequisite and violated the explicit prohibition against new shrine art. Generated placeholders are not approved repository assets, regardless of a prior request to create temporary assets.
- It was reversed transparently with `git revert --no-commit e1fcae20ae2f2af796b7b44ad9ca9d65352cffff`. The command applied without conflicts. No reset, rebase, amend, or other history rewriting occurred.
- Corrective commit: the single commit containing this entry, with subject `revert(shrines): remove unapproved placeholder rendering`. Its exact full hash is recorded in the corrective report after commit creation. A Git commit cannot truthfully embed its own final hash in its tracked contents without changing that hash or amending history.
- Milestone 5 remains unauthorized and was not begun.

### Exact rejected generated assets

- Generated shared/static geometry: `src/main/resources/assets/britannia_mod/geo/shrine.geo.json` (718 bytes; Git blob `5eedc1025f41a0257cc1d2f85ea1b02ccc210fdb`).
- Generated diagnostic geometry: `src/main/resources/assets/britannia_mod/geo/shrine_missing.geo.json` (573 bytes; Git blob `1526f21315639b7d4b16663901bd25bff871c3aa`).
- Generated empty animation manifest: `src/main/resources/assets/britannia_mod/animations/shrine.animation.json` (84 bytes; Git blob `d5221b8fd85994074a5584358668f88e6bcf58f0`).
- Generated PNGs: `textures/block/shrine/chaos.png` (40,569 bytes; blob `6f0a7c5a6545eb5a6d9fda3eae76ca1f85a065cf`), `compassion.png` (43,798; `409a4f6b62de4cf74c5fccbbf2f7d1298701ee6b`), `honesty.png` (43,834; `89f12346f0fbff48d8959e8d40a8eaa051bf3792`), `honor.png` (42,632; `15bf9d0786c715c1d513e1505a589c8737732802`), `humility.png` (33,789; `396d9b97f24b53b7751420f980c095b4901b2f4c`), `justice.png` (43,083; `450cd91610407f251e484f47f0d27f711043f1a7`), `sacrifice.png` (40,475; `3ed611fa945f76195c719a8d9179cbf4bda24b58`), `spirituality.png` (39,279; `a1cf5b31d2ab3f24a53ae054b742352a91d66db6`), and `valor.png` (37,300; `c94fbb556b3463c82af31212898768a3f777f533`). Their rejected `128 x 128` dimensions are not an approved production requirement.
- Temporary Honesty family icon: the rejected version of `src/main/resources/assets/britannia_mod/models/item/shrine.json` pointed at `britannia_mod:block/shrine/honesty`. It is restored to the Milestone 2 diagnostic `minecraft:block/stone` presentation.
- Rejected asset claims: `docs/shrines-monoliths/PLACEHOLDER_ASSETS.md` and the former Milestone 4 completion section of this log.

### Files removed and restored

- Removed renderer/model-selection production classes: `ShrineGeoModel.java`, `ShrineRenderer.java`, `ShrineRenderTransform.java`, and `ShrineRenderSelection.java`.
- Removed all generated geometry, animation, nine shrine PNGs, and `PLACEHOLDER_ASSETS.md` listed above.
- Removed the rendering-only test `ShrineRenderingMilestoneTest.java`.
- Restored `ClientModSetup.java` without shrine renderer registration; `ShrineMonolithDefinitions.java` with shared geometry and all nine textures explicitly unavailable; `StructureIdentity.java` without rendering-only resource resolution; and `LargeStructureAnchorBlockEntity.java` without GeckoLib/render-bound behavior.
- Restored `en_us.json` without rejected visual localization, `models/item/shrine.json` to vanilla stone, and `ShrineMonolithCatalogueTest.java` to the approved unavailable-resource assertions.
- Restored `PROJECT_FACTS.md` by removing claims that placeholder resources, a shrine renderer, or 127 rendering-era tests were approved repository facts.
- Retained all nine stable shrine IDs, Milestone 1 definitions/transforms, Milestone 2 placement and diagnostic presentation, Milestone 3 persistence/item components/lifecycle/integrity behavior, and all existing Milestone 1-3 tests.

### Fresh asset search and current blocker

- A read-only search covered the cleaned working tree, every local head and remote-tracking ref, committed resource directories, both authoritative root documents, and historical committed paths. No branch was switched and no candidate was copied.
- The only shrine-named shared geometry or nine-texture set in any local ref is the rejected generated payload at `e1fcae20ae2f2af796b7b44ad9ca9d65352cffff`; it has explicit rejection evidence and is insufficient.
- The committed `pillar`, `statue`, and `ankh` geometry/textures are unrelated assets with no shrine identity or approval evidence. Filenames were not treated as approval.
- `OPEN_QUESTIONS.md` now records the exact evidence required for owner-supplied or owner-approved shared geometry, each of the nine texture mappings, and the shrine-family icon decision.
- Current blocker: approved shared shrine geometry and approved, UV-compatible textures for `honesty`, `compassion`, `valor`, `justice`, `sacrifice`, `honor`, `spirituality`, `humility`, and `chaos` are unavailable. Resource availability remains explicit, with no variant substitution or fabricated path.

### Exact validation commands and results

All commands ran from `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`.

```text
.\gradlew.bat test --tests "com.seggellion.britannia_mod.structure.*" --no-daemon --no-configuration-cache --stacktrace
```

Exit `0`; 68.250 seconds; 16 test classes, 121 cases, 0 failures, 0 errors, 0 skipped. Gradle: 30 actionable tasks (2 executed, 2 from cache, 26 up-to-date). Warnings were limited to Gradle's incubating daemon-JVM-discovery notice.

```text
.\gradlew.bat test --no-daemon --no-configuration-cache --stacktrace
```

Exit `0`; 23.459 seconds; 16 test classes, 121 cases, 0 failures, 0 errors, 0 skipped. Gradle: 30 actionable tasks (1 executed, 29 up-to-date). The same daemon-JVM-discovery notice appeared.

```text
.\gradlew.bat clean build --no-daemon --no-configuration-cache --stacktrace
```

Before clean, no build/cache/run/log/generated path was tracked and no user-owned untracked work was present. Exit `0`; 81.598 seconds; 16 test classes and 121 cases restored from cache, with 0 failures, 0 errors, and 0 skipped. Gradle: 36 actionable tasks (6 executed, 20 from cache, 10 up-to-date). Four untracked logs generated by the validation runs were explicitly removed afterward; they are reproducible test output.

```text
git diff --check
```

Exit `0`; no whitespace errors.

### Corrected production JARs

- `Britannia_Mod_shrines_m2_codex-0.1.7k.jar`: 21,840,741 bytes; 4,652 entries; SHA-256 `3D4A0E50DCA75F631093A1333BC277A7EC7A4B779595FB36D6588BF7C949392F`.
- `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar`: 22,412,379 bytes; 4,656 entries; SHA-256 `DDA97FE7A4FA13A7BEBDE8206CF5789DED5E2446660300BADA80443357CAFBFB`.
- Each contains 96 shrine/structure production class entries: 32 definition, 17 placement, 12 lifecycle, 9 multiblock/persistence, and 6 item classes plus their nested/support entries. Each retains the configured `ShrineItem`, invisible anchor/part blockstates, and vanilla-stone diagnostic shrine item model.
- Each contains zero test classes, zero rejected shrine renderer/helper classes, zero generated shrine geometry/animation/textures, zero temporary Honesty icon reference, and zero monolith runtime rendering/placement classes. `InteriorDecoratorToolItem` remains unchanged pre-existing content and has no shrine behavior.

### Corrective status

- Compared with approved Milestone 3, only this corrective log entry and the expanded asset blocker in `OPEN_QUESTIONS.md` differ.
- No fabricated shrine art, renderer, client renderer registration, rendering test, monolith placement/rendering, Interior Decorator shrine behavior, or Milestone 5 behavior remains.
- Milestone 4 is blocked pending approved assets. Milestone 5 remains unauthorized.

## 2026-08-03 - Milestone 4 Resumed: Owner-Approved Placeholder Rendering

### Subsequent approval and chronology

- Starting commit: corrective commit `69514a90c8376dc224cd2530f2f73e58928b1dfd` on `shrines-monoliths`. The working tree began clean; merge base with `origin/patch-18` remained `62df1dc97c5113a86f9c0f258cb90538f31efe89`; divergence was `0` behind and `7` ahead.
- After the corrective review, the project owner explicitly resolved the asset blocker with: `I like the placeholder assets, use them.` This is approval of the exact historical assets from `e1fcae20ae2f2af796b7b44ad9ca9d65352cffff`, not authorization to generate different replacements.
- The rejected commit and corrective commit remain intact in history. No reset, rebase, amend, merge, fetch, push, or history rewriting was used. The exact reviewed files were restored forward from the historical commit and documented with their new approval status.
- These files are approved replaceable development assets for repository and Milestone 4 use. They are not a permanent final-art commitment. Their paths, identities, dimensions, mapping, geometry orientation, bounds, and hashes remain those inventoried in the corrective entry and `PLACEHOLDER_ASSETS.md`.
- The prior corrective entry remains an accurate historical record of the state before this later approval. Its asset blocker is resolved by this entry.
- Milestone 5 behavior was not introduced.

### Restored Milestone 4 scope

- Restored the shared static GeckoLib shrine geometry, bounded diagnostic geometry, static animation manifest, nine distinct 128 by 128 texture files, and Honesty-based family item presentation.
- Restored `ShrineRenderer` and `ShrineGeoModel`, anchor-only client renderer registration, `ShrineRenderSelection`, `ShrineRenderTransform`, synchronized resource resolution, anchor render bounds, nine localization keys, and exact rendering/catalogue tests.
- All nine stable IDs retain their exact mapping: `honesty`, `compassion`, `valor`, `justice`, `sacrifice`, `honor`, `spirituality`, `humility`, and `chaos`. No ID, component schema, saved-data schema, footprint, placement rule, lifecycle rule, or integrity rule changed.
- No Interior Decorator cycling, monolith placement/rendering, cross-family conversion, recipe, command, NPC, or Milestone 5 implementation was added.

### Exact resumed validation

All commands ran from `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`.

- `compileJava --no-daemon --no-configuration-cache --stacktrace`: exit `0`; 32.898 seconds; 26 actionable tasks (1 from cache, 25 up-to-date).
- Focused `ShrineMonolithCatalogueTest` and `ShrineRenderingMilestoneTest`: exit `0`; 64.248 seconds; 2 classes, 20 cases, 0 failures, 0 errors, 0 skipped; 30 actionable tasks (2 executed, 1 from cache, 27 up-to-date).
- `test --tests "com.seggellion.britannia_mod.structure.*" --no-daemon --no-configuration-cache --stacktrace`: exit `0`; 29.722 seconds; 17 classes, 127 cases, 0 failures, 0 errors, 0 skipped; 30 actionable tasks (1 executed, 29 up-to-date).
- Full `test --no-daemon --no-configuration-cache --stacktrace`: exit `0`; 31.371 seconds; 17 classes, 127 cases, 0 failures, 0 errors, 0 skipped; 30 actionable tasks (1 executed, 29 up-to-date).
- Before clean, no build/cache/run/log/generated path was tracked and no user-owned untracked generated work was present. `clean build --no-daemon --no-configuration-cache --stacktrace`: exit `0`; 96.179 seconds; 17 classes and 127 cases from cache, 0 failures, 0 errors, 0 skipped; 36 actionable tasks (6 executed, 20 from cache, 10 up-to-date).
- `git diff --check`: exit `0`; no whitespace errors. Gradle warnings were limited to the incubating daemon-JVM-discovery notice.

### Resumed production JAR inventory

- `Britannia_Mod_shrines_m2_codex-0.1.7k.jar`: 22,218,818 bytes; 4,672 entries; SHA-256 `7DE216EA445C72DC196B428B30652A893673BFA1A95CCA5C8F3A448799F141AF`.
- `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar`: 22,790,456 bytes; 4,676 entries; SHA-256 `E20F7164619AB5C6D18ECFCCC536362ACA02C5155E423742EB585B9BDA673709`.
- Both contain `ShrineRenderer`, `ShrineGeoModel`, `ShrineRenderSelection`, `ShrineRenderTransform`, the shared and diagnostic geometry, animation manifest, all nine textures, shrine item model, localization, and invisible anchor/part blockstates.
- Both contain zero test classes and zero monolith runtime rendering/placement entries.

### Resumed status

- Milestone 4 is implemented using the exact owner-approved replaceable placeholder package and is ready for owner review.
- Milestone 5 was not started.
