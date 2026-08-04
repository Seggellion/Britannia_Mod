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

## 2026-08-03 - Milestone 5: Interior Decorator Shrine Cycling

### Authorization, isolation, and chronology

- Starting commit: `06677e3235e05ec13678d474294e5f2152433ce0` on branch `shrines-monoliths`; independent clone `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`; working tree clean.
- Merge base with `origin/patch-18`: `62df1dc97c5113a86f9c0f258cb90538f31efe89`. Authorized starting divergence was `0` behind / `8` ahead of `origin/patch-18` and `0` behind / `6` ahead of `origin/shrines-monoliths`.
- Post-Milestone-3 history remains intact: `e1fcae20ae2f2af796b7b44ad9ca9d65352cffff` original Milestone 4 implementation; `69514a90c8376dc224cd2530f2f73e58928b1dfd` transparent corrective revert; `06677e3235e05ec13678d474294e5f2152433ce0` owner-approved restoration.
- The shared repository was read-only throughout. Its initial observed state was branch `banking`, HEAD `bbe070f80c0b7bb0dd8c12a9ed6cf4170787eca0`, with unrelated existing banking and untracked work. No shared file, branch, index, build output, or history was changed.
- The required final read-only observation found the shared repository still on `banking`, now at concurrently changed HEAD `e7ca8f14a72884a69f5c9193edbc9f749df99ccf`, with an existing `ModConfig.java` modification and two unrelated untracked specification files. Those external changes were not synchronized into the isolated clone.
- The project owner's explicit override approves the exact Milestone 4 placeholder package as provisional implementation input. Milestone 5 did not regenerate, edit, rename, resize, recolor, re-UV, or otherwise alter it.
- Ending commit: the single commit containing this entry, subject `feat(shrines): cycle variants with interior decorator`. Its full hash is recorded in the final Milestone 5 report because a commit cannot truthfully contain its own final hash without amendment.

### Implementation

- Existing item and entry point: `britannia_mod:interior_decorator_tool`, registered once in `ItemRegistry` and implemented by `InteriorDecoratorToolItem.useOn`. No second item, recipe, packet, screen, or dispatcher was added.
- Dispatch precedence: registered large-structure anchor/part handling runs immediately after the null-player guard and before offhand nudge and generic horizontal rotation. Other item branches and block-owned tent, bed, furniture, floor, wall, sign, slab, hanging-item, and teleporter interactions remain in their previous order and code.
- Authorization: repository inspection found no canonical reusable administrator predicate. `DecoratorAuthorization` therefore implements the playbook fallback once: logical-server `ServerPlayer.isCreative()` or `ServerPlayer.hasPermissions(2)`. It gates shrine cycling only and trusts no client permission or desired-variant input.
- Server authority: the normal item-use request supplies only clicked cell and held stack. The server verifies the registered tool and authorization, resolves current anchor state, computes one next ID from the authoritative catalogue, and returns a sided interaction result. No custom packet exists.
- Anchor and part resolution: `ShrineLifecycleService.resolve` remains the owner. Anchors resolve directly. Parts use the existing reverse transform and validate encoded offset/facing, already-loaded candidate chunk, anchor block/entity, authoritative facing, exact persisted offset, exact world position, expected part state, shrine family, and structurally valid four-cell footprint. No chunk ticket or force-load is used and cycling does not queue integrity work.
- Cycle owner and order: `StructureVariantCycler` over the validated catalogue owns enabled, compatible, numeric cycle positions. Exact order is `honesty`, `compassion`, `valor`, `justice`, `sacrifice`, `honor`, `spirituality`, `humility`, `chaos`, then wrap to `honesty`. Disabled/incompatible candidates are skipped; missing current IDs, duplicate/negative cycle definitions, and a cycle with no alternate fail closed.
- Transaction: `ShrineVariantCycleService` captures the complete previous `PlacedStructureState`, constructs an immutable replacement differing only in `variantId`, and applies it through `LargeStructureAnchorBlockEntity.replacePlacedState`, a compare-and-set boundary that rejects stale state and any schema/family/facing/footprint change. Assignment and readback precede synchronization; readback is confirmed again afterward.
- Rollback: failed or partial assignment and injected synchronization failure restore the complete previous state, mark it changed through the same compare-and-set helper, and attempt normal synchronization. Rollback failure is explicit. No successful sound/message occurs before the transaction and restored synchronization complete.
- Synchronization: `LargeStructureAnchorBlockEntity.synchronize` calls `setChanged` and server `sendBlockUpdated` with `Block.UPDATE_CLIENTS`; existing `getUpdateTag`, `ClientboundBlockEntityDataPacket`, `handleUpdateTag`, and `onDataPacket` paths carry the selected variant to initial/tracking clients. The renderer continues selecting one shared geometry and the texture identified by synchronized `variant_id`.
- Feedback: no dedicated pre-existing decorator sound was registered. Milestone 5 uses the existing vanilla `SoundEvents.UI_STONECUTTER_SELECT_RECIPE` selection sound once on authoritative success and localized action-bar key `message.britannia_mod.shrine.decorator.selected` with the selected variant's existing translation key. Failure and unauthorized paths emit neither.
- Typed results cover success, wrong tool, client side, unauthorized, non-structure cell, invalid part, unloaded/missing anchor, missing anchor entity, non-shrine family, missing current ID, no alternate, invalid cycle, incompatible candidate, assignment failure, synchronization failure, and rollback failure.

### Preserved state and scope

- The only mutable persisted field is `variant_id`. Schema, family, ordered footprint, anchor position/state, facing, all part states/offsets/world positions, geometry, render origin/offset/bounds, collision/selection/occlusion/support/piston/fluid behavior, held stack count/durability, shrine item component, lifecycle queues, integrity queues, drops, and world cells remain unchanged.
- Placement planning/execution, lifecycle teardown, integrity repair, item reconstruction, drops, block replacement, shrine rotation/movement/nudging, reverse cycling, cross-family conversion, and monolith behavior are not called or added.
- Renderer architecture, renderer registration, part invisibility, fallback policy, animation, geometry, item presentation, and all placeholder art remain unchanged. Milestone 6 placement, monolith renderer/cycling, and positive-Y runtime behavior remain deferred.

### Approved asset hashes before and after

Every before/after pair is identical (SHA-256; byte size):

- `animations/shrine.animation.json`: `F20A6AFD94D1FCF6463B8FDA98853781FC8548293293514199C4AC1E4C5A981E` (89).
- `geo/shrine.geo.json`: `C31915013A7D50D1732225764D4F94FEB1AD141515BAC0F3CADC81E5C8B3BCA0` (744).
- `geo/shrine_missing.geo.json`: `460F9FDDE68EC578C3FF1B4E26B457AFCF3467485325B4189C14E02820CC4781` (597).
- `models/item/shrine.json`: `829C55BB91B761F7529607B3BFD439B73D6A171F01556C12D5F50D5991636985` (121).
- `chaos.png`: `D8B2FDEB4158BBF86A053CDD383E532569F4DDDAEFF178D2472F5ABC2507EDC3` (40,569).
- `compassion.png`: `79160E8AF141E4395A64D64439F7FBF0C2170D78073A136E6CBEF8A130FC87BC` (43,798).
- `honesty.png`: `D35747568A37960736C20F8359F55043B19739FC7D1FAB34144F8F971C36BCCC` (43,834).
- `honor.png`: `ADC2A67CFA7476D4C1D18A7C49E9F1937D552099FCFE9F1D3C821B832135D381` (42,632).
- `humility.png`: `E201645E5D9058E28022B22904114E09823E736DDE8021D2E4DA2CE9E558AC8A` (33,789).
- `justice.png`: `5F01D14F16870EBA66C6A4C3E2F19C919E403A5DCDDC12037904285C825B1B8B` (43,083).
- `sacrifice.png`: `E4C56B44DC44C749DB10E2D34824DCF0079774FAAF9C2368330CC57036B4EEC4` (40,475).
- `spirituality.png`: `D50CF726BD459C85313A9173E708C49C3ADC72559D0EABC9458FDFED8FF05CF1` (39,279).
- `valor.png`: `A748C870317998F911AC328960685F4B41AE8330635DC839F35D27EC6ACA4A1F` (37,300).

### Automated coverage and requirement mapping

- `DecoratorAuthorizationTest` covers logical-server creative, permission level 2, combined authorization, unauthorized state, and rejection of client-side claims.
- `ShrineVariantCycleServiceTest` exercises the actual production transaction through a narrow injected mutation/synchronization adapter: all nine transitions/wrap, exactly-once advancement/feedback, disabled and incompatible skipping, display/localization independence, missing current, no alternate, invalid definition, all entry and target failure types, non-shrine family, immutable-field equality, geometry/texture mapping, clean/partial assignment failure, synchronization rollback, failed rollback, retry, and deterministic repeated catalogue access.
- `ShrineLifecycleServiceTest` exercises the real resolver with registered block states for anchor plus all three parts across all four horizontal facings (16 cases), invalid/wrong membership, orphan/unrelated anchor, and unloaded-anchor deferral without writes or force-load.
- `LargeStructurePersistenceTest` exercises the real registered anchor block entity compare-and-set, disk NBT, update tag, actual clientbound block-entity packet construction/application, and rejection of stale/family/facing structural changes.
- `InteriorDecoratorMilestoneFiveScopeTest` proves one registered tool/constructor, shrine precedence before rotation, continued branch reachability, common-code client-import absence, no custom packet/placement/removal call, no Milestone 6/recipe, and exact immutable hashes for all thirteen approved assets.
- Existing `ShrineRenderingMilestoneTest` proves the selected synchronized ID maps to the expected texture while retaining shared geometry; all earlier 127 Milestone 1-4 tests remain green.
- These 23 Milestone 5-added/extended test methods contain more than 86 logical cases through the nine-transition, 16 membership/facing, typed-failure, persistence-path, and thirteen-asset loops. Requirements 1-86 are covered by the grouped suites above except live-world/manual claims, which remain explicitly unverified rather than inferred.

### Exact validation commands and results

All commands ran from `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`.

- `compileJava --no-daemon --no-configuration-cache`: final exit `0`; 41.7 seconds; 26 tasks (1 executed, 25 up-to-date). Two existing warnings remained: missing Javadoc on a Mixin `@Overwrite` and deprecated-for-removal `Item.initializeClient`. An earlier compile attempt failed on an `Optional<String>` translation-key type mismatch; it was corrected before tests.
- Focused `test --tests "com.seggellion.britannia_mod.structure.interaction.*" --no-daemon --no-configuration-cache --stacktrace`: final exit `0`; 22.5 seconds; 3 classes, 19 methods, 0 failures/errors/skips; 30 tasks (1 executed, 29 up-to-date). An earlier focused run exposed one incorrect test expectation about the established incompatible-candidate filter; the implementation was unchanged and the test corrected.
- Expanded focused interaction/lifecycle/persistence command: exit `0`; 34.0 seconds; 5 classes, 42 methods, 0 failures/errors/skips; 30 tasks (2 executed, 28 up-to-date).
- `test --tests "com.seggellion.britannia_mod.structure.*" --no-daemon --no-configuration-cache --stacktrace`: exit `0`; 54.5 seconds; 20 classes, 150 methods, 0 failures/errors/skips; 30 tasks (2 executed, 28 up-to-date).
- `test --no-daemon --no-configuration-cache --stacktrace`: exit `0`; 34.1 seconds; 20 classes, 150 methods, 0 failures/errors/skips; 30 tasks (1 executed, 29 up-to-date).
- Before clean, `git ls-files` found no tracked build/cache/run/log/generated output. `git clean -ndX` listed only generated `.gradle`, `build`, `run`, and `runs`; no user-owned work was a Gradle-clean target.
- `clean build --no-daemon --no-configuration-cache --stacktrace`: exit `0`; 161.5 seconds; 20 classes / 150 methods restored from cache, 0 failures/errors/skips; 36 tasks (6 executed, 20 from cache, 10 up-to-date).
- `git diff --check`: exit `0`; no whitespace errors. The only repeated Git warning was inability to read the user's global ignore file inside the sandbox; it did not affect repository status or validation.

### Runtime smoke validation

- Dedicated server: `runServer -Pdev --no-daemon --no-configuration-cache`. A first run reached `Done (1.799s)` but the wrapper did not forward redirected stdin, so only that attempt's exact process tree was terminated. The final run reached `Done (1.897s)`, accepted loopback RCON `stop`, logged `Stopping server`, `Saving players`, `Saving worlds`, and `All dimensions are saved`, and Gradle exited successfully in 59 seconds (32 tasks: 1 executed, 31 up-to-date).
- Dedicated-server logs retain a pre-existing unrelated `TitleScreen` dist-cleaner error/warning and missing `britannia_mod.properties` warning. Despite those baseline diagnostics, mod construction, decorator and shrine registration, common shrine-cycle/authorization classes, world startup, and normal save/shutdown succeeded. No shrine client renderer was loaded from common cycle code.
- Development client: `runClient -Pdev --no-daemon --no-configuration-cache` reached user initialization, OpenAL initialization, and block/GUI atlas creation. Existing unrelated missing-model/blockstate warnings remain. The hidden smoke processes were then terminated because no interactive window was exposed. This is startup/resource/class-loading evidence only, not a normal client-exit or gameplay result.
- Manual/in-world checks performed: source/API/Git/JAR inspection plus actual dedicated-server and client startup smokes.
- Manual interaction checks unperformed: obtaining/placing/clicking all cells, cycling nine variants, two-facing visual review, save/reload in-world, unauthorized/wrong-item play, two-client observation, live GPU appearance, and live resource reload. These are `UNVERIFIED`; adapter, packet, startup, and JAR results are not described as live gameplay or multiplayer validation.

### Production JAR inspection

- `Britannia_Mod_shrines_m2_codex-0.1.7k.jar`: 22,231,766 bytes; 4,679 entries; SHA-256 `E5BC4DD455629DCD2CE1E16F5F7C47AC765DD2D1114222EDFB472DE1F8C951D4`.
- `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar`: 22,803,404 bytes; 4,683 entries; SHA-256 `D8E465F046823112DCEADB08DE00C4758FDDC6A14BAE222E6E91874D8FBCD8A2`.
- Each contains `ShrineVariantCycleService` (five top/nested entries), one `DecoratorAuthorization`, one existing `InteriorDecoratorToolItem`, localization, two existing shrine renderer/model entries, nine textures, two geometry files, animation/item resources, and all Milestone 1-4 production classes.
- Each contains zero test/fixture entries, zero second decorator item, zero monolith runtime/renderer/placement entry, zero Milestone 6 class, and no unexpected asset change.

### Known limitations and next milestone

- Mutation behavior is deeply exercised through the actual production service with deterministic adapters and real persisted/packet state, but no live `ServerPlayer`/`ServerLevel`, GameTest, two-client session, in-world click, save-file reload, or GPU visual validation occurred.
- Client startup proves registration, resource parsing, and class loading only. It does not close the known Milestone 4 live-render/resource-reload limitation.
- Milestone 6 remains explicitly deferred. The next milestone is permitted only after separate owner approval.

## 2026-08-03 - Milestone 6: Atomic Monolith Placement and Positive-Y Render Offset

### Asset gate, owner override, and isolation

- Original prerequisite result: repository and historical asset searches found no owner-approved monolith geometry or texture, so implementation initially stopped at the mandatory asset gate. That blocker is retained as project chronology rather than erased.
- Owner override: the project owner accepted the blocker report and explicitly authorized creation of exactly one provisional diagnostic package for family `monolith`, stable variant `diagnostic_missing_content`. The authorization is provisional implementation content only, not final art and not permission for a second variant or Milestone 7.
- Starting commit: `4900136b1a82f477fc02eac8d963e19d8b94e5e6` on `shrines-monoliths`; starting tree clean; merge base with `origin/patch-18` `62df1dc97c5113a86f9c0f258cb90538f31efe89`; divergence `0` behind / `9` ahead of `origin/patch-18` and `0` behind / `7` ahead of `origin/shrines-monoliths`.
- Ending commit: the single commit containing this entry, subject `feat(monoliths): add offset multiblock placement`. Its immutable hash is recorded in the final report because a commit cannot truthfully contain its own hash without amendment.
- All work ran only in `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`. The shared repository was initially inspected read-only on branch `banking`, HEAD `e7ca8f14a72884a69f5c9193edbc9f749df99ccf`, with unrelated existing banking/untracked work. The final read-only observation found the same `banking` branch at concurrently advanced HEAD `aa419df6dbe8f4111b6e71ec05eb09a345a734b8` with expanded banking work. None of those concurrent changes was synchronized. The shared repository was not switched, built, cleaned, edited, staged, committed, reset, stashed, merged, rebased, fetched, or pushed by this milestone.

### Provisional diagnostic package

- `assets/britannia_mod/geo/monolith_diagnostic.geo.json`: static GeckoLib geometry, 1,007 bytes, SHA-256 `0D58B1ED73A8811E10B276DB7E55B29F7248DD047074C0FE786882DF0757E53C`.
- `assets/britannia_mod/textures/block/monolith/diagnostic_stone.png`: ImageGen-origin neutral diagnostic stone, deterministically reduced to one fully opaque 32 by 32 PNG, 2,034 bytes, SHA-256 `FE07CE0672EE51D76F2833D1044264C7B65B2ADEB076873D1B07C953509944F4`. It contains no text, shrine texture, virtue symbol, ankh, person, third-party image, or alternate palette.
- `assets/britannia_mod/animations/monolith.animation.json`: required empty static GeckoLib resource, 84 bytes, SHA-256 `D63D4CBCEF6A3E410EE94F38F5684F7B3E9F94BCC69B4D80C925FBBB61FC1530`.
- `assets/britannia_mod/models/item/monolith.json`: vanilla `minecraft:block/stone` presentation, 40 bytes, SHA-256 `E48339859F7AA66BBC08246B8BC65AC1827E28241509518F9884A854739A2BED`; no separate icon exists.
- Exact authored cube bounds are X `[-8,40]`, Y `[-16,32]`, Z `[-8,24]` model voxels. Origin contract is lower front-left anchor; local X viewer-right, Y up, Z away; pivot `[0,-16,0]`; default forward North with the front rib at negative Z. Texture box UVs originate at `[0,0]` on the 32 by 32 image.
- The negative-sixteen authored base deliberately requires one positive-sixteen-voxel visual correction. `ShrineRenderer.preRender` applies `[0,+1,0]` blocks only when `isReRender` is false; neither geometry, anchor, cells, persisted offsets, collision, nor selection add a second correction. Post-translation Y is `[0,48]` voxels.
- GeckoLib's established anchor rotation remains North `0`, East `-90`, South `180`, West `90` degrees around positive Y. Finite all-facing monolith bounds are X/Z `[-2,3]` and Y `[0,3]` blocks, with the existing `1/128` tolerance.
- `ShrineMonolithDefinitions` honestly activates exactly `monolith/diagnostic_missing_content`, status `PROVISIONAL`, enabled and player-facing, with the exact geometry/texture paths. No second runtime monolith variant exists.

### Shared framework implementation

- `ConfiguredStructureItem` generalizes the existing configured item boundary while `ShrineItem` preserves `shrine/honesty` and new `MonolithItem` owns `monolith/diagnostic_missing_content`. Registry ID `britannia_mod:monolith` and synchronized component `britannia_mod:monolith_instance_state` are each registered exactly once. The existing shrine item/component IDs and schema fields are unchanged.
- The immutable planner now accepts an item's exact family and footprint count. The monolith deterministic footprint order is Y layers bottom-to-top, then local Z front-to-back, then local X left-to-right: one anchor plus seventeen parts. It captures all eighteen original and expected states, exact required chunks, and eighteen placement-authorized positions before mutation.
- Local-to-world mappings at anchor `(ax,ay,az)` remain centralized: North `(ax-x,ay+y,az+z)`, East `(ax-z,ay+y,az-x)`, South `(ax+x,ay+y,az-z)`, West `(ax+z,ay+y,az+x)`. Reverse part-to-anchor resolution uses the same transform and stored facing/offset.
- The existing `ShrinePlacementExecutor`, transaction owner, rollback ownership, placement guard, lifecycle service, integrity scheduler, anchor block, part block, and anchor block entity are reused. No monolith-specific block/entity/transaction/lifecycle/integrity duplicate exists.
- `PlacedStructureState` retains schema version `1`, NBT key `shrine_state`, family/variant/facing fields, and ordered footprint field; it now admits exactly four or eighteen cells and rejects all other sizes, duplicate offsets, missing/duplicate anchor, non-horizontal facing, malformed, and future state.
- Survival consumes one configured monolith only after all eighteen placements, anchor initialization, seventeen part placements, verification, and synchronization succeed. Creative consumes zero. Any injected cell mutation or verification failure restores all owned cells in reverse order with drops suppressed and preserves unrelated replacements.
- The existing lifecycle owns anchor/part resolution, configured pick block, survival one-drop teardown, creative zero-drop teardown, explosion/external replacement, orphan handling, unloaded-anchor deferral, all seventeen missing-part repairs, obstruction preservation, reentrancy, piston blocking, fluid rejection, and cell-local full-block collision/selection. Parts remain invisible, entity-free, and renderer-free.
- The one existing anchor renderer and model selector now resolve either family. Shrine rendering remains at zero correction and unchanged bounds; monolith rendering uses its per-variant geometry/texture and the one positive-Y correction. Missing resources continue through the bounded diagnostic fallback.

### Scope and regression protection

- No monolith Interior Decorator dispatch, variant cycling, cross-family conversion, recipe, command, packet, NPC, rail, website, second geometry, second texture, second animation, part renderer, part block entity, separate anchor/part registration, collision expansion, or selection expansion was added.
- All thirteen existing shrine assets were hashed before and after; every byte count and SHA-256 remained identical: animation `F20A6AFD94D1FCF6463B8FDA98853781FC8548293293514199C4AC1E4C5A981E`; shared geometry `C31915013A7D50D1732225764D4F94FEB1AD141515BAC0F3CADC81E5C8B3BCA0`; diagnostic geometry `460F9FDDE68EC578C3FF1B4E26B457AFCF3467485325B4189C14E02820CC4781`; item model `829C55BB91B761F7529607B3BFD439B73D6A171F01556C12D5F50D5991636985`; Chaos `D8B2FDEB4158BBF86A053CDD383E532569F4DDDAEFF178D2472F5ABC2507EDC3`; Compassion `79160E8AF141E4395A64D64439F7FBF0C2170D78073A136E6CBEF8A130FC87BC`; Honesty `D35747568A37960736C20F8359F55043B19739FC7D1FAB34144F8F971C36BCCC`; Honor `ADC2A67CFA7476D4C1D18A7C49E9F1937D552099FCFE9F1D3C821B832135D381`; Humility `E201645E5D9058E28022B22904114E09823E736DDE8021D2E4DA2CE9E558AC8A`; Justice `5F01D14F16870EBA66C6A4C3E2F19C919E403A5DCDDC12037904285C825B1B8B`; Sacrifice `E4C56B44DC44C749DB10E2D34824DCF0079774FAAF9C2368330CC57036B4EEC4`; Spirituality `D50CF726BD459C85313A9173E708C49C3ADC72559D0EABC9458FDFED8FF05CF1`; Valor `A748C870317998F911AC328960685F4B41AE8330635DC839F35D27EC6ACA4A1F`.
- Existing shrine placement, persistence, lifecycle, cycling order/transaction, localization keys, texture mapping, shared geometry, renderer architecture, item presentation, and four-cell behavior remain covered and green.

### Automated validation

All commands ran from `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`.

- Initial `compileJava`: exit `0`, 56 seconds, 26 tasks (1 executed, 25 up-to-date). Known warnings: missing Javadoc on a Mixin `@Overwrite` and deprecated-for-removal `Item.initializeClient`; general deprecation/unchecked notes remained. The first test compile found one test-only nested-type reference and was corrected; final `compileTestJava` exited `0` in 32.3 seconds (1 executed, 28 up-to-date).
- Focused planner/executor/lifecycle/render/persistence command: final exit `0`, 43.1 seconds, 5 classes / 26 methods, zero failures/errors/skips, 30 tasks (2 executed, 28 up-to-date). One earlier assertion called the explicit-ID planner overload while expecting component decoding; the test was corrected to exercise the production overload without changing implementation.
- Focused `MonolithItemStateTest`: exit `0`, 76.2 seconds, 1 class / 3 methods, zero failures/errors/skips, 30 tasks (3 executed, 27 up-to-date); recompilation emitted only the two known production warnings and one existing test deprecation note.
- `test --tests "com.seggellion.britannia_mod.structure.*" --no-daemon --no-configuration-cache --stacktrace`: final exit `0`, 33.4 seconds, 25 classes / 169 methods, zero failures/errors/skips, 30 tasks (1 executed, 29 up-to-date). An earlier structure run exposed one stale pre-activation `playerFacing=false` assertion; the assertion was updated to the owner-authorized definition and the suite rerun.
- `test --no-daemon --no-configuration-cache --stacktrace`: exit `0`, 34.7 seconds, 25 classes / 169 methods, zero failures/errors/skips, 30 tasks (1 executed, 29 up-to-date).
- Before clean, `git clean -ndX` listed only generated `.gradle`, `build`, `run`, and `runs`. Twelve untracked logs created by this Milestone 6 session were path-verified under the isolated `logs` directory and removed explicitly; no user-owned or unrelated work was a clean target.
- `clean build --no-daemon --no-configuration-cache --stacktrace`: exit `0`, 106.8 seconds, 25 classes / 169 methods from cache, zero failures/errors/skips, 36 tasks (6 executed, 20 from cache, 10 up-to-date).
- Final import-only cleanup verification, `build --no-daemon --no-configuration-cache --stacktrace`: exit `0`, 78.5 seconds, 25 classes / 169 methods, zero failures/errors/skips, 35 tasks (4 executed, 31 up-to-date); it rebuilt both production JARs from the final source and emitted only the two known production warnings.
- Final `git diff --check`: exit `0`; no whitespace errors. Git repeatedly warned that the sandbox could not read the user's global ignore file; repository status and explicit staging were unaffected.

### Runtime smoke validation

- Dedicated server command: `runServer -Pdev --no-daemon --no-configuration-cache --stacktrace`. It loaded Britannia `0.1.7k` and GeckoLib `4.6.6`, reached `Done (4.456s)`, started RCON, accepted loopback `stop`, logged `Stopping server`, `Saving players`, `Saving worlds`, saves for overworld/end/nether, `All dimensions are saved`, stopped RCON, and ended `BUILD SUCCESSFUL in 3m 48s`.
- Dedicated-server diagnostics were the established unrelated client `TitleScreen` dist-cleaner message and missing `britannia_mod.properties` warning; registries and world startup still completed. No new client-only monolith class loaded on the dedicated server.
- Development client command: `runClient -Pdev --no-daemon --no-configuration-cache --stacktrace`. It loaded Britannia, GeckoLib, Minecraft, and NeoForge; initialized Britannia; reloaded `mod/britannia_mod`; created the block and GUI atlases; and logged no missing/parse error naming `monolith_diagnostic` or `diagnostic_stone`. The exact isolated client process tree was then terminated after startup verification.
- The client log retains many pre-existing unrelated invalid/missing models, textures, sounds, animation expressions, and the default missing config warning. The smoke proves startup and resource/class loading, not gameplay appearance.
- In-world placement, two-facing visual inspection, live save/reload, two-client observation, resource reload, and actual visual horizon alignment were not performed. They remain explicitly `UNVERIFIED`.

### Production JAR inspection

- `Britannia_Mod_shrines_m2_codex-0.1.7k.jar`: 22,240,626 bytes; 4,686 entries; SHA-256 `913519964498FC0BE932F37D02CD71508B76A081C8090086EBE6355E8B7CB0E7`.
- `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar`: 22,812,264 bytes; 4,690 entries; SHA-256 `10E236A3FB8BC5665E717CB97A5B3E692EFC65A824CD03585C04A31967B87795`.
- Each contains 47 production classes below shared structure placement/lifecycle/multiblock/item packages, including `ConfiguredStructureItem`, `MonolithItem`, the shared planner/executor, lifecycle/integrity services, one anchor/part/entity, and one shared renderer/model selector. Each contains zero test/fixture entries and zero monolith cycle/decorator entries.
- Each contains exactly one monolith PNG and one monolith geometry plus the single animation and vanilla-backed item model. Hashing each resource from inside each JAR reproduced the four source SHA-256 values above, proving the complete provisional package is in both production artifacts.

### Status

- Milestone 6 is complete subject to owner review. The provisional package remains explicitly replaceable only with later owner approval.
- Milestone 7 was not started and remains blocked on owner-approved additional monolith variants/assets.

## 2026-08-03 - Milestone 7: Monolith Model Variants and Interior Decorator Cycling

### Authorization, isolation, and chronology

- Milestone 6 commit `1c5bff67a961da93dea245ed9d05653f2f7bfeca` was approved. The owner then authorized exactly one additional provisional monolith variant, `diagnostic_alternate`, and the exact cycle `diagnostic_missing_content -> diagnostic_alternate -> diagnostic_missing_content`. Neither technical ID is final lore or artwork, and no third variant was authorized or added.
- Starting clone: `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`, branch `shrines-monoliths`, clean HEAD `1c5bff67a961da93dea245ed9d05653f2f7bfeca`.
- Merge base with clone-local `origin/patch-18`: `62df1dc97c5113a86f9c0f258cb90538f31efe89`. Starting divergence was `0` behind / `10` ahead of `origin/patch-18` and `0` behind / `8` ahead of `origin/shrines-monoliths`.
- Shared repository was read-only. Its starting observed state was branch `banking`, HEAD `aa419df6dbe8f4111b6e71ec05eb09a345a734b8`, with unrelated banking modifications and untracked files. No shared branch, file, index, history, build output, or generated content was changed or synchronized.
- Ending commit is the one commit containing this entry, subject `feat(monoliths): cycle model variants with decorator`. Its immutable full hash is recorded in the final report because a commit cannot contain its own hash without amendment.
- No fetch, push, transfer, merge, rebase, reset, amend, stash, remote change, linked worktree, or history rewrite occurred. Milestone 8 was not begun.

### Provisional alternate asset package

- New geometry `assets/britannia_mod/geo/monolith_diagnostic_alternate.geo.json`: 1,145 bytes, SHA-256 `B2FCBE6841C53313A754A9722E5633957A9AB0334FB96FF05FBCFC42BA7512DC`. It is a distinct five-cube stepped/tapered static monument with exact union X `[-8,40]`, Y `[-16,32]`, Z `[-8,24]` voxels, pivot `[0,-16,0]`, default North forward, and a negative-Z front ridge at origin `[10,-6,-6]`, size `[12,28,4]`.
- New texture `assets/britannia_mod/textures/block/monolith/diagnostic_alternate_stone.png`: 1,900 bytes, SHA-256 `E320B72F2D0B9429D07DD4E62D264535760797AE6D1F9DD047C082B6736A2C4A`. It is a fully opaque 32 by 32 dark/cool layered-stone diagnostic generated from an OpenAI ImageGen raster and reduced deterministically. It contains no text, icon, shrine/virtue/ankh/human identity, copied decoration, or final lore.
- The final ImageGen source was `C:\Users\dusti\.codex\generated_images\019fc9b3-b045-75a0-8d82-8e25a9fe1763\exec-f07925ea-55f7-4db6-93c2-5fb2bbf516ef.png`. The generation prompt requested a square seamless-style flat diffuse game texture of restrained dark cool layered stone with broad sediment/mineral variation and expressly prohibited text, runes, icons, symbols, shrine/virtue/ankh imagery, humans/faces, ornament, carved decoration, lore motifs, lighting, perspective, borders, and transparency.
- No alternate animation or item model was technically required. Both monolith variants reuse unchanged `animations/monolith.animation.json`; the family retains unchanged vanilla-backed `models/item/monolith.json`.
- Both models have the same measured extents, so the finite family render bounds did not expand: all-facing X/Z remain `[-2,3]`, Y `[0,3]` blocks plus `1/128` tolerance. Both require the same renderer-only `[0,+16,0]` voxel correction applied once.

### Catalogue, transaction, and feedback

- The production monolith family now contains exactly two enabled, player-facing, `PROVISIONAL` entries. Positions are numeric and unique: `diagnostic_missing_content` at `0`, `diagnostic_alternate` at `1`. Both own the exact ordered eighteen-cell `3 x 3 x 2` footprint, floor-oriented placement, cell-bounded collision, lower-front-left origin, and `[0,16,0]` render offset.
- Resource selection remains `family_id + variant_id -> validated catalogue variant -> its model and texture`. Existing maps to `geo/monolith_diagnostic.geo.json` plus `textures/block/monolith/diagnostic_stone.png`; alternate maps to the two new paths. Missing IDs/resources retain the selected stable ID and use the established bounded diagnostic behavior; neither valid variant substitutes for the other.
- `InteriorDecoratorToolItem` still owns one large-structure dispatch ahead of generic rotation and calls the existing `ShrineVariantCycleService`. No second tool, packet, dispatcher, anchor renderer, part renderer, block entity, or family-specific interaction path was introduced.
- The shared service now admits only `shrine` and `monolith`, looks up the persisted family, validates cycle positions, resolves the next enabled compatible entry from catalogue order, classifies incompatible footprint/collision/placement/origin/offset or invalid model/texture failures, and changes only `variant_id` through the existing anchor compare-and-set boundary.
- Success order remains immutable replacement, compare-and-set assignment, readback, `setChanged`/block update synchronization, readback, one existing stonecutter-selection sound, and one localized action-bar message. Monolith feedback key is `message.britannia_mod.monolith.decorator.selected` (`Selected monolith: %s`) using localized `structure.britannia_mod.monolith.diagnostic_alternate` or the existing identity.
- Assignment or synchronization failure restores the complete previous placed state, verifies readback, attempts normal synchronization, emits no success feedback, and reports incomplete rollback explicitly. Retry advances exactly once.
- Authorization remains the established logical-server predicate: server `ServerPlayer` creative mode or permission level 2 or higher. The client sends no desired ID, model, texture, cycle position, direction, geometry identity, offset, footprint, or step count.
- Anchor and part resolution remains `ShrineLifecycleService.resolve`. It validates registered anchor/part state, loaded candidate anchor chunk, reverse transformed offset/facing, persisted footprint membership, expected part state, anchor entity, and authoritative state. It neither force-loads nor mutates.

### State and scope invariants

- Cycling preserves schema, family, ordered eighteen-cell footprint, anchor position/state, facing, all seventeen part states, offsets/world positions, placement/collision/origin policies, render offset, shapes/occlusion/support/piston/fluid behavior, held stacks, drops, lifecycle/integrity state, and chunk tickets. It calls no placement planner/executor/rollback, teardown, integrity repair, item reconstruction, or block replacement.
- Shrine behavior remains unchanged: nine ordered texture variants, one shared geometry, existing authorization/resolution/synchronization/rollback/feedback, and byte-identical placeholder resources.
- No reverse cycling, shift behavior, rotation, movement, resizing, variable footprint, cross-family conversion, third monolith, recipe, command, NPC, rails, website, broad rewrite, or Milestone 8 content exists.

### Existing-asset hashes before and after

Every before/after pair is identical:

- Existing monolith geometry `0D58B1ED73A8811E10B276DB7E55B29F7248DD047074C0FE786882DF0757E53C` (1,007); texture `FE07CE0672EE51D76F2833D1044264C7B65B2ADEB076873D1B07C953509944F4` (2,034); animation `D63D4CBCEF6A3E410EE94F38F5684F7B3E9F94BCC69B4D80C925FBBB61FC1530` (84); item model `E48339859F7AA66BBC08246B8BC65AC1827E28241509518F9884A854739A2BED` (40).
- Shrine animation `F20A6AFD94D1FCF6463B8FDA98853781FC8548293293514199C4AC1E4C5A981E` (89); shared geometry `C31915013A7D50D1732225764D4F94FEB1AD141515BAC0F3CADC81E5C8B3BCA0` (744); diagnostic geometry `460F9FDDE68EC578C3FF1B4E26B457AFCF3467485325B4189C14E02820CC4781` (597); item model `829C55BB91B761F7529607B3BFD439B73D6A171F01556C12D5F50D5991636985` (121).
- Shrine textures: Honesty `D35747568A37960736C20F8359F55043B19739FC7D1FAB34144F8F971C36BCCC`; Compassion `79160E8AF141E4395A64D64439F7FBF0C2170D78073A136E6CBEF8A130FC87BC`; Valor `A748C870317998F911AC328960685F4B41AE8330635DC839F35D27EC6ACA4A1F`; Justice `5F01D14F16870EBA66C6A4C3E2F19C919E403A5DCDDC12037904285C825B1B8B`; Sacrifice `E4C56B44DC44C749DB10E2D34824DCF0079774FAAF9C2368330CC57036B4EEC4`; Honor `ADC2A67CFA7476D4C1D18A7C49E9F1937D552099FCFE9F1D3C821B832135D381`; Spirituality `D50CF726BD459C85313A9173E708C49C3ADC72559D0EABC9458FDFED8FF05CF1`; Humility `E201645E5D9058E28022B22904114E09823E736DDE8021D2E4DA2CE9E558AC8A`; Chaos `D8B2FDEB4158BBF86A053CDD383E532569F4DDDAEFF178D2472F5ABC2507EDC3`.

### Automated coverage and requirement mapping

- `MonolithVariantCycleServiceTest` (6 methods) covers requirements 19-27, 40-63, and 91-101 through exact two-way/wrap cycles, deterministic order independent of names/resources, disabled skipping, typed entry/target/compatibility/resource failures, complete immutable-field equality, assignment/synchronization rollback, failed rollback, retry, and feedback counts.
- `MonolithMilestoneSevenRenderingTest` (5 methods) covers requirements 1-18 and 64-80: exact catalogue, resource/localization existence, distinct identities, geometry parsing/extents/pivot/front, opaque image/hash validation, both selections, stable unknown-ID fallback, and finite all-facing bounds with one offset.
- `MonolithLifecycleAndIntegrityTest` (6 methods) exercises registered anchor/part states for all 18 offsets across all four horizontal facings (72 resolution cases), alternate pick and configured-drop recovery, lifecycle, repair, orphan, chunk, and collision/fluid regressions. This covers 28-39, 53-55, 62, and 89-90 where the adapter permits.
- `LargeStructurePersistenceTest` (12 methods) uses the registered anchor entity, actual NBT/update tags, reflective construction of the actual clientbound block-entity packet, and packet application for both monolith variants, covering 81-88. It is packet-path evidence, not live multiplayer.
- `MonolithMilestoneSevenScopeTest` (3 methods), existing `DecoratorAuthorizationTest`, `InteriorDecoratorMilestoneFiveScopeTest`, `ShrineVariantCycleServiceTest`, `ShrineRenderingMilestoneTest`, and all prior tests cover authorization, existing-tool reuse, common/client boundaries, no world-subsystem calls, shrine invariants, immutable assets, registration singularity, and exclusions 102-120.
- Narrow mutation and world adapters exercise production services deterministically. Registered-state tests use actual registered blocks/items/components. Static inspection is limited to boundaries that are not reasonably instantiated in plain JUnit. No adapter or packet test is described as live gameplay or multiplayer.

### Exact validation commands and results

All commands ran from `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`.

- `compileJava compileTestJava --no-daemon --no-configuration-cache --stacktrace`: exit `0`; 97.6 seconds; 29 tasks (3 executed, 26 up-to-date). The two established warnings remained: missing Javadoc on a Mixin `@Overwrite` and deprecated-for-removal `Item.initializeClient`; test deprecation and generic unchecked notes were unchanged.
- First six-class focused run: exit `1`; 50.7 seconds; 45 methods, 2 test failures. It exposed test-only assumptions: an overly broad renderer-registration substring counted eleven repository renderers, and the first PNG conversion retained source alpha. The exact renderer assertion was narrowed and only the new texture was reconverted to fully opaque 24-bit PNG. Production cycling logic did not change.
- Corrected six-class focused command covering monolith transaction/render/lifecycle/persistence/scope plus shrine-cycle regression: exit `0`; 73.2 seconds; 6 classes / 45 methods, zero failures/errors/skips; 30 tasks (3 executed, 27 up-to-date).
- Focused catalogue/definition/authorization/shrine-render/Milestone-6-render/Milestone-7-render command: exit `0`; 23.3 seconds (`BUILD SUCCESSFUL in 22s`); 6 classes / 52 methods, zero failures/errors/skips; 30 tasks (1 executed, 29 up-to-date).
- `test --tests "com.seggellion.britannia_mod.structure.*" --no-daemon --no-configuration-cache --stacktrace`: exit `0`; 31.1 seconds (`BUILD SUCCESSFUL in 30s`); 28 classes / 186 methods, zero failures/errors/skips; 30 tasks (1 executed, 29 up-to-date).
- `test --no-daemon --no-configuration-cache --stacktrace`: exit `0`; 31.2 seconds (`BUILD SUCCESSFUL in 30s`); 28 classes / 186 methods, zero failures/errors/skips; 30 tasks (1 executed, 29 up-to-date).
- Before clean, no build/cache/run/log/generated path was tracked. `git clean -ndX` listed only `.gradle`, `build`, `run`, and `runs`; no user-owned or unrelated file was a Gradle clean target. `clean build --no-daemon --no-configuration-cache --stacktrace`: exit `0`; 91.6 seconds (`BUILD SUCCESSFUL in 1m 30s`); 28 classes / 186 methods from cache, zero failures/errors/skips; 36 tasks (6 executed, 20 from cache, 10 up-to-date).
- Final `git diff --check`: result is recorded after documentation completion. Git's repeated inability to read the sandboxed global ignore file did not affect repository status or explicit staging.

### Runtime validation

- Dedicated server: `runServer -Pdev --no-daemon --no-configuration-cache --stacktrace` loaded Britannia `0.1.7k`, GeckoLib `4.6.6`, Minecraft, and NeoForge; reached `Done`; started RCON; accepted loopback `stop`; logged `Stopping server`, `Saving players`, `Saving worlds`, each dimension save, and `All dimensions are saved`; then Gradle exited `0` with `BUILD SUCCESSFUL in 2m 40s` (32 tasks: 1 executed, 1 from cache, 30 up-to-date). Established unrelated `TitleScreen` dist-cleaner and missing-config warnings remained; no client shrine/monolith renderer loaded as common code.
- Development client: `runClient -Pdev --no-daemon --no-configuration-cache --stacktrace` loaded Britannia/GeckoLib, initialized OpenAL, and built the block and GUI atlases. Neither new nor existing monolith geometry/texture path appeared in missing/parse diagnostics. The exact hidden root process PID `18212` and its child tree were terminated after startup verification. This was not a normal client exit or gameplay session.
- Live placement, decorator clicks from anchor/bottom/middle/top cells, two-facing appearance, horizon/culling/duplicate-render review, save-file reload, unauthorized/wrong-tool play, live pick/re-place, resource reload, and second-client observation were not performed. They remain `UNVERIFIED`; startup, transforms, adapters, and packet tests do not imply those manual results.

### Production JAR inspection

- `Britannia_Mod_shrines_m2_codex-0.1.7k.jar`: 22,245,489 bytes; 4,688 entries; SHA-256 `DA8C2FDDA0AB63B7056ECF7AF6B3B354F948B00508733BEB297F16583A261BA8`.
- `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar`: 22,817,127 bytes; 4,692 entries; SHA-256 `40B0943E8AF5CC6DB61B6D2E668E8F3C3399EAF3E03DB4742FC7474E2FA1E45C`.
- Both contain exactly the two monolith geometry paths and two monolith PNG paths, the one monolith animation, item model and localization, `ShrineVariantCycleService`, existing `InteriorDecoratorToolItem`, `ShrineGeoModel`, `ShrineRenderer`, all shrine resources, and all Milestone 1-6 production content. Source-to-JAR SHA-256 comparison covered all 19 existing/new provisional assets in each artifact with zero missing entries and zero mismatches.
- Both contain zero test/fixture entries, zero third monolith resource, zero second decorator/renderer/block entity, zero reverse-cycle/cross-family implementation, and zero Milestone 8 content.

### Status and next milestone

- Milestone 7 implementation and automated/startup/package validation are complete subject to owner review. Manual live interaction, visual, save-file, and multiplayer checks are explicitly unverified.
- Final monolith names/art/migration remain unresolved. The next permitted milestone is Milestone 8 only after separate owner approval. Milestone 8 was not started.

## 2026-08-03 - Milestone 8: Collision, Adjacency, Content, and Regression Hardening

### Authorization, isolation, and chronology

- Approved Milestone 7/start commit: `0aa523ec86e483e230fc1c4d04e145d394ebf990` (`feat(monoliths): cycle model variants with decorator`). Worktree began clean on `shrines-monoliths` in `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`.
- Merge base with `origin/patch-18`: `62df1dc97c5113a86f9c0f258cb90538f31efe89`; starting divergence: `0` behind / `11` ahead of `origin/patch-18` and `0` behind / `9` ahead of `origin/shrines-monoliths`.
- Shared repository was inspected read-only before work on branch `banking`, HEAD `aa419df6dbe8f4111b6e71ec05eb09a345a734b8`, with unrelated existing banking modifications/untracked files. It was never switched, edited, built, cleaned, staged, committed, synchronized, or used as input.
- Ending commit is the single commit containing this entry, subject `test(structures): harden collision and adjacency behavior`. Its immutable full hash is recorded in the final report because a commit cannot embed its own hash without amendment.
- No fetch, push, transfer, merge, rebase, reset, amend, stash, remote change, linked worktree, or history rewrite occurred. Milestone 9 was not begun.

### Proven production corrections

- The owner-approved final first-release profile is `SOLID_CELL`. Production behavior was already a full local cell, but the enum/catalogue still called it `CELL_BOUNDED`. The smallest semantic correction renames that only enum constant and all five built-in uses; no schema serializes this enum and no structure identity, footprint, asset, or world state changed.
- A focused registered-state test proved `BlockState.canBeReplaced(Fluids.WATER)` returned `true`: the inherited fallback treats `.noOcclusion()` invisible cells as non-solid even though selection/collision are full cells. Anchor and part now explicitly override only fluid replacement to return `false`. They remain dry, non-waterloggable, and otherwise unchanged.
- The first focused run also showed the inherited interaction shape is intentionally empty. The test was corrected to classify inherited empty interaction/face-occlusion shapes rather than changing production shapes.

### Shape-method inventory and exact result

- Resolved NeoForge `21.1.72` / Minecraft `1.21.1` `BlockBehaviour.BlockStateBase`, vanilla neighbor classes, `VoxelShape`, and `AABB` APIs were inspected from the mapped local classpath with Java 21 `javap`; no API was guessed.
- Audited actual registered states: four anchor states (`north`, `east`, `south`, `west`) and all 72 part states (`4 facing x 3 local_x x 3 local_y x 2 local_z`), including the representable origin combination that `stateFor` deliberately rejects for normal part creation.
- Explicit anchor/part overrides: selection `getShape` and collision `getCollisionShape` each return `Shapes.block()`, exactly one AABB `[0,0,0]..[1,1,1]` (`0..16` voxels).
- Inherited results: occlusion, visual, block-support, and all six face-occlusion shapes are the same full local cell; interaction shape alone is empty. Empty shapes are permitted and every non-empty AABB was enumerated.
- Every coordinate is finite, ordered, non-negative, at most `1.0`, and independent of facing/offset. NaN, infinity, reversed, negative, oversized, model-derived, renderer-derived, texture-derived, render-AABB-derived, and render-offset-derived coordinates are rejected.
- All six `isFaceSturdy` results are `true`; land, water, and air `PathComputationType` results are all `false`; ordinary and fluid replaceability are false; piston reaction is `BLOCK`; fluid state is empty; neither class implements `SimpleWaterloggedBlock`.
- Behavioral signatures are identical across every anchor/part state and all eleven catalogue variants. Static dependency inspection additionally proves shape classes import no client renderer/model/selection/bounds/offset code. Shrine extents, monolith extents, missing-resource selection, variant cycling, and `[0,+16,0]` visual correction therefore cannot affect physics.

### Occupancy, perimeter, and adjacency matrix

- Transform-derived shrine result for every facing and all nine variants: four occupied cells and eight unique horizontal perimeter cells.
- Transform-derived monolith result for every facing and both variants: eighteen occupied cells; each Y layer contains six occupied cells and ten unique horizontal perimeter cells; all three layers produce thirty layer-specific perimeter positions.
- Six state phases are covered: initial, one catalogue cycle, actual disk NBT save/load, actual update-tag application, actual reflected `ClientboundBlockEntityDataPacket` application, and missing-resource display fallback without authoritative-state mutation.
- The registered-neighbor catalogue has 87 states: one full cube; all 40 oak stair combinations (`4 facing x 2 half x 5 shape`); three stone slabs; thirteen wall connection controls; six fence controls; eight gate controls; and sixteen wall button/lever/wall-torch/ladder controls.
- Exterior slots are `9 x 4 x 8 = 288` shrine plus `2 x 4 x 30 = 240` monolith slots. `528 x 6 phases x 87 states = 275,616` neighbor install/collision/snapshot/remove cases. Adding 264 phase/occupancy cases, 40 explicit stair state cases, and 16 attachment cases yields 275,936 logical Milestone 8 cases across 14 focused JUnit methods.
- Every neighbor uses a registered vanilla `BlockState`; world-translated neighbor collision never intersects any occupied cell collision. Install and removal modify only the simulated neighbor map; complete anchor state, block-entity `PlacedStructureState`, schema/family/variant/facing/ordered footprint, and every part state remain equal.
- Stair straight/toward/away/parallel interpretations, top/bottom halves, and direct `INNER_LEFT`, `INNER_RIGHT`, `OUTER_LEFT`, `OUTER_RIGHT` registered-state shapes are all local. Corner shapes are evidence level C direct-state tests, not claimed live recomputation or placement.
- Wall/fence toward/away/straight connection controls, fence gates, slabs, full blocks, and attachments are registered-state level C plus coordinate level D. Structure sturdy-face evidence proves the support side; no broad fake/real Level placement harness was introduced. Existing production placement/lifecycle adapters provide level B regression evidence. Level A live `BlockItem.place`/GameTest was unavailable; level E is used only for registration, renderer, logging, and side boundaries.

### Content report and fixed inventory

- `docs/shrines-monoliths/CONTENT_REPORT.json` is deterministic JSON schema version `1`, generated from the approved content commit `0aa523ec86e483e230fc1c4d04e145d394ebf990`; it has no timestamp and no filesystem-enumeration ordering.
- Family order and variant cycle order come from the validated catalogue. Every family/variant entry records logical status separately from `OWNER_APPROVED_PROVISIONAL` asset status, exact dimensions/ordered footprint/count, default/enabled state, `SOLID_CELL`, render origin/offset, exact geometry/texture/animation paths and SHA-256 hashes, availability, validation status, and empty diagnostics.
- Exact content: nine shrine variants (`honesty`, `compassion`, `valor`, `justice`, `sacrifice`, `honor`, `spirituality`, `humility`, `chaos`) and two provisional monolith variants (`diagnostic_missing_content`, `diagnostic_alternate`). No final lore/name, third variant, disablement, reorder, substitution, or content migration was added.
- `MilestoneEightContentReportTest` reconstructs the report from the in-memory catalogue plus exact source resources and compares parsed JSON equality, paths, hashes, localization, uniqueness, mappings, defaults, offsets, and counts.

### Protected assets before and after

All 19 source byte counts and SHA-256 values are identical before/after and match both production JARs:

- Shrine: animation `F20A6AFD94D1FCF6463B8FDA98853781FC8548293293514199C4AC1E4C5A981E` (89); shared geometry `C31915013A7D50D1732225764D4F94FEB1AD141515BAC0F3CADC81E5C8B3BCA0` (744); fallback geometry `460F9FDDE68EC578C3FF1B4E26B457AFCF3467485325B4189C14E02820CC4781` (597); item model `829C55BB91B761F7529607B3BFD439B73D6A171F01556C12D5F50D5991636985` (121).
- Shrine textures: Honesty `D35747568A37960736C20F8359F55043B19739FC7D1FAB34144F8F971C36BCCC`; Compassion `79160E8AF141E4395A64D64439F7FBF0C2170D78073A136E6CBEF8A130FC87BC`; Valor `A748C870317998F911AC328960685F4B41AE8330635DC839F35D27EC6ACA4A1F`; Justice `5F01D14F16870EBA66C6A4C3E2F19C919E403A5DCDDC12037904285C825B1B8B`; Sacrifice `E4C56B44DC44C749DB10E2D34824DCF0079774FAAF9C2368330CC57036B4EEC4`; Honor `ADC2A67CFA7476D4C1D18A7C49E9F1937D552099FCFE9F1D3C821B832135D381`; Spirituality `D50CF726BD459C85313A9173E708C49C3ADC72559D0EABC9458FDFED8FF05CF1`; Humility `E201645E5D9058E28022B22904114E09823E736DDE8021D2E4DA2CE9E558AC8A`; Chaos `D8B2FDEB4158BBF86A053CDD383E532569F4DDDAEFF178D2472F5ABC2507EDC3`.
- Monolith: existing geometry `0D58B1ED73A8811E10B276DB7E55B29F7248DD047074C0FE786882DF0757E53C` (1,007); alternate geometry `B2FCBE6841C53313A754A9722E5633957A9AB0334FB96FF05FBCFC42BA7512DC` (1,145); existing texture `FE07CE0672EE51D76F2833D1044264C7B65B2ADEB076873D1B07C953509944F4` (2,034); alternate texture `E320B72F2D0B9429D07DD4E62D264535760797AE6D1F9DD047C082B6736A2C4A` (1,900); animation `D63D4CBCEF6A3E410EE94F38F5684F7B3E9F94BCC69B4D80C925FBBB61FC1530` (84); item model `E48339859F7AA66BBC08246B8BC65AC1827E28241509518F9884A854739A2BED` (40).

### Diagnostic inventory and bounding policy

| Path | Severity/code | Trigger/context | Bound and coverage |
| --- | --- | --- | --- |
| Definition validation | typed `DefinitionDiagnostic.Code` | duplicate/missing IDs, invalid footprint/resources/cycle; family/variant/location context | finite validation result; exhaustive definition tests |
| Placed-state decode | `PlacedStructureStatus` plus WARN | malformed/future/invalid/missing definitions; anchor/status context | once per key, FIFO cap 1,024; persistence tests and M8 static cap audit |
| Client model/texture fallback | WARN | selected family/variant/status or exact missing resource | once per key, FIFO cap 128, never state mutation; render tests and M8 missing-selection test |
| Integrity orphan/obstruction/repair race | WARN | kind plus anchor/part position | once per kind/position, FIFO cap 1,024; repeated-diagnostic and lifecycle tests |
| Integrity scheduler | intentionally silent | missing anchor chunk/loaded-cell deferral | 64 chunks/tick, 4,096 pending/level, removed on level unload, `getChunkNow` only; integrity tests and M8 scope audit |
| Placement rollback | ERROR | incomplete rollback with complete position list | emitted only on exceptional failure; rollback ownership/failure tests |
| Lifecycle exception | ERROR | removal anchor and cause plus exception | removal guard clears in `finally`; lifecycle failure/reentrancy/duplicate-drop tests |
| Unauthorized decorator/invalid cycle/missing definition | typed result, intentionally silent | server-owned authorization/definition result | no logger and no client authorization details; authorization/cycle tests and M8 scope audit |
| Duplicate lifecycle callback | intentionally silent guard | level identity plus anchor | in-progress set cleared in `finally`; duplicate callbacks produce at most one configured drop |

No new logging was added. Missing-resource paths cannot log per frame for the same key; orphan/obstruction paths cannot log each tick for the same position; caches and queues are finite or lifecycle-cleared; logging does not mutate authoritative state.

### Banner and side/registration boundaries

- No banner multiblock source/test exists on active `shrines-monoliths`. Local remote-tracking ref `origin/banners-dyetub` at `5debcc1` contains the banner suite under `src/test/java/com/seggellion/britannia_mod/bannerdyeing/`; paths were inspected read-only with `git ls-tree`. It was not switched to, copied, imported, or run, so banner runtime regression is unavailable on this active branch.
- Existing scope tests plus M8 checks retain one generic anchor, one generic part, one anchor block-entity type, two configured family items, no anchor/part `BlockItem`, no part block entity, one anchor renderer registration, no part renderer, and one Interior Decorator registration.
- Common definition/placement/lifecycle/interaction code imports no `net.minecraft.client`; the client cannot submit arbitrary family/variant/resource/footprint/cycle-position/offset data. Fallback is display-only and same-family cycling remains server authoritative.

### Exact automated commands and results

All commands ran from `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`.

- Focused hardening: `.\gradlew.bat test --tests "com.seggellion.britannia_mod.structure.hardening.*" --no-daemon --no-configuration-cache --stacktrace`; final exit `0`, 22.4 seconds, 4 classes / 14 methods, 275,936 logical cases, zero failures/errors/skips; 30 tasks (2 executed, 28 up-to-date). Three diagnostic red runs classified the inherited empty interaction shape, exposed inherited fluid-replaceability, and corrected the face-occlusion expectation from empty to the observed full-cell result; the final suite passed with the production correction described above.
- Focused interaction/placement/lifecycle/render/multiblock/hardening command: exit `0`, 25.0 seconds, 23 classes / 127 methods, zero failures/errors/skips; 30 tasks (1 executed, 29 up-to-date).
- Structure suite: `.\gradlew.bat test --tests "com.seggellion.britannia_mod.structure.*" --no-daemon --no-configuration-cache --stacktrace`; exit `0`, 23.6 seconds, 32 classes / 200 methods, zero failures/errors/skips; 30 tasks (1 executed, 29 up-to-date).
- Full suite: `.\gradlew.bat test --no-daemon --no-configuration-cache --stacktrace`; exit `0`, 21.8 seconds, 32 classes / 200 methods, zero failures/errors/skips; 30 tasks (1 executed, 29 up-to-date).
- Before clean, tracked generated paths were empty; dry inventory listed only ignored `.gradle`, `build`, `run`, and `runs`; untracked root `logs` contained only reproducible current-session test/runtime logs and was not a Gradle clean target. `.\gradlew.bat clean build --no-daemon --no-configuration-cache --stacktrace`: exit `0`, 165.7 seconds (`BUILD SUCCESSFUL in 2m 45s`); 32 classes / 200 methods from cache, zero failures/errors/skips; 36 tasks (6 executed, 20 from cache, 10 up-to-date).
- Known compile warnings remain unchanged: missing Javadoc on one Mixin `@Overwrite`, deprecated-for-removal `Item.initializeClient`, test deprecation, and generic deprecation/unchecked notes. Gradle's daemon-JVM-discovery notice remains incubating.

### Runtime startup validation

- Dedicated server: `.\gradlew.bat runServer -Pdev --no-daemon --no-configuration-cache --stacktrace`; loaded Britannia `0.1.7k`, GeckoLib `4.6.6`, Minecraft `1.21.1`, NeoForge `21.1.72`; reached `Done (1.369s)`; loopback RCON authenticated and issued `stop`; players/worlds/overworld/end/nether chunks saved and `All dimensions are saved`; Gradle exit `0`, `BUILD SUCCESSFUL in 1m 13s`, 32 tasks (1 executed, 1 from cache, 30 up-to-date).
- Established unrelated diagnostics remain: development refmaps, `TitleScreen` dedicated-dist Mixin target, missing `britannia_mod.properties`, asset-union schema warnings, and repetitive Fishing Rod/barrel messages. No shrine/monolith missing-resource, catalogue, shape, content-report, or improper client-renderer diagnostic occurred on the server.
- Development client: `.\gradlew.bat runClient -Pdev --no-daemon --no-configuration-cache --stacktrace`; loaded the mod list, Britannia client registration, `mod/britannia_mod` resources, OpenAL, sound engine, 4096 block atlas, GUI atlas, and other atlases. No missing/parse warning named shrine or either monolith resource. The exact hidden process tree was terminated after the startup marker because no interactive client control was exposed; this is startup/resource/class-loading evidence, not a normal client exit or gameplay result.

### Production JAR inspection

- `Britannia_Mod_shrines_m2_codex-0.1.7k.jar`: 22,245,612 bytes; 4,688 entries; SHA-256 `755EBE6448EF5553954D1BCE0625F2DA3FBC9AD265F36A09018EF3E99ACBD316`.
- `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar`: 22,817,250 bytes; 4,692 entries; SHA-256 `D0E883B3B338B52B20943CDA3C85FE0D22DA703B5F4D1A8D8412E1FFC402DFFE`.
- Each contains 107 structure-class entries, exactly one anchor class, one part class, one anchor entity, one shared `ShrineRenderer`, localization, nine shrine textures, two monolith geometries, two monolith textures, both animations/item presentations, and all prior production content. All 19 source-to-JAR resource comparisons have zero missing and zero mismatch.
- Each contains zero test/fixture entries, zero part renderer, zero part entity, zero direct-use anchor/part item model, zero third monolith resource, zero cross-family conversion, and zero Milestone 9 harness/world. `CONTENT_REPORT.json` is intentionally project documentation and is not runtime-packaged.

### Manual validation, limitations, and next milestone

- Performed manually: complete specification/document/API/source/diff/Git/ref/JAR/log inspection; exact registered-state evidence classification; dedicated server and development client startup observation.
- `UNVERIFIED`: live `BlockItem` placement, full representative stair/full-block/slab/wall/fence/attachment rings, actual neighbor recomputation in a real Level, visual collision/overlap, part duplicate-render observation, save-world reload, live cycling with neighbors, two authenticated clients, multiplayer observation, and normal client shutdown.
- These checks are explicitly carried to Milestone 9. Registered-state collision mathematics, deterministic adapters, packet application, and startup smoke are not claimed as live gameplay, visual, save-world, or multiplayer evidence.
- No new gameplay, content, art, recipe, command, NPC, rails, website, cross-family conversion, reverse cycling, rotation, resizing, performance rewrite, renderer, decorator, part entity/item, global scan, or Milestone 9 infrastructure was added.
- Next permitted milestone: Milestone 9 only after separate owner approval. Do not begin it.

## 2026-08-04 - Milestone 9: Limited Live-Validation Evidence

### Authorization, limitation, and isolation

- Approved Milestone 8/start commit: `a3b81edda57e441d1abdc95f7b2713567df05130` (`test(structures): harden collision and adjacency behavior`). The isolated clone began clean on `shrines-monoliths` at `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`.
- Merge base with `origin/patch-18`: `62df1dc97c5113a86f9c0f258cb90538f31efe89`; starting divergence was `0` behind / `12` ahead of `origin/patch-18` and `0` behind / `10` ahead of `origin/shrines-monoliths`.
- The disposable topology is `C:\projects\britannia\validation\shrines-monoliths-m9`, with server, `client-a`, `client-b`, evidence, backups, and world `server\m9-world` outside every Git working tree.
- The two-authenticated-client gate could not be satisfied: no authenticated client joined and no safe independent two-client control was available. On 2026-08-04 the project owner explicitly approved Milestone 9 with that gate and all dependent live-validation checks waived and recorded as `UNVERIFIED`.
- The waiver changes milestone acceptance only. It is not gameplay evidence and does not convert any unperformed check to `PASS`.
- Ending commit is the single documentation evidence commit containing this entry, subject `docs(structures): record live validation evidence`. Its full hash is recorded in the final report because a commit cannot embed its own hash without amendment.
- The shared repository remained read-only on its unrelated dirty `banking` work. No fetch, push, transfer, merge, rebase, reset, amend, stash, remote change, linked worktree, or history rewrite occurred.

### Runtime topology and exact artifact

- Runtime versions: Minecraft `1.21.1`; NeoForge `21.1.72`; Java `21.0.9+10-LTS` by Eclipse Adoptium; Britannia `0.1.7k`; embedded GeckoLib `4.6.6`.
- The repository-correct deployable artifact is `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar`, selected because `build.gradle`'s `jarJar` output embeds `geckolib-neoforge-1.21.1-4.6.6.jar` and `nanohttpd-2.2.0.jar`; the thin JAR does not contain those dependencies.
- Final deployable JAR: 22,817,250 bytes; 4,692 entries; SHA-256 `998CF12AC3BF0E4AA07E7558081EC361762913560A7C513B840D1C785BFD93E2`.
- The source build, validation `artifacts`, server `mods`, Client A `mods`, and Client B `mods` copies independently produced that same hash. Each runtime `mods` directory contains exactly one Britannia JAR.
- Server configuration remains private loopback `127.0.0.1:25585`, `online-mode=true`, `white-list=true`, `enforce-whitelist=true`, and RCON disabled. No router/public exposure or authentication material was used.
- External evidence is preserved under `evidence\manifests\runtime-manifest.json`, `evidence\manifests\runtime-manifest-final-2026-08-04.json`, `evidence\checklists\OWNER_ACTION_SHEET.md`, and `evidence\log-summaries\server-startup-summary.md`. Runtime evidence is not committed.

### Available runtime evidence

- The clean production server previously loaded Minecraft `1.21.1`, NeoForge `21.1.72`, Britannia `0.1.7k`, and GeckoLib `4.6.6` from preflight artifact SHA-256 `DBB6B2E73F10FBF167590BB7C0CAB30837CB025007F95190410EB223B81E8601`, reached `Done (4.680s)`, received a normal console `stop`, exited `0`, and saved players, worlds, chunks, and all dimensions.
- Its mod list contained Minecraft, NeoForge, Britannia, and GeckoLib. It found the exact Britannia `-all.jar` and its embedded GeckoLib/NanoHTTPD dependencies.
- Existing non-structure diagnostics were the established dedicated-dist `TitleScreen` mixin error/warnings, first-run FML configuration correction, missing optional `britannia_mod.properties`, union-schema warnings, and missing barrel warning. No named shrine/monolith missing resource, renderer, synchronization, orphan, repair, or rollback error appeared during server-only startup.
- This is server startup and packaging evidence only. It is not authenticated multiplayer, client rendering, gameplay, reload, adjacency, lifecycle, or performance evidence.
- The final clean rebuild has identical source commit, size, entry inventory, and embedded content but a different archive hash (`998CF12A...93E2`) because it is a newly emitted JAR. That final JAR was copied to all four disposable destinations but was not launched; final-hash runtime startup is therefore also `UNVERIFIED` under the owner's limitation approval.

### Explicitly unverified live phases

- Phase 1: two distinct authenticated concurrent joins, client runtime/channel/resource agreement, and mutual in-world observation — `UNVERIFIED`.
- Phase 2: two-way shrine/monolith placement, all facings, occupied-cell observation, collision/selection agreement, and anchor-only rendering — `UNVERIFIED`.
- Phase 3: authorized and unauthorized anchor/part decorator cycling, all nine shrine variants, both monolith variants, wrap-around, and two-client synchronization — `UNVERIFIED`.
- Phase 4: late tracking and both disconnect/reconnect directions — `UNVERIFIED`.
- Phase 5: live save/reload, same-world server restart, resource reload, neighbor persistence, and live pick-block persistence — `UNVERIFIED`.
- Phases 6-7: survival/creative lifecycle, exact drops, pick block, explosion, external replacement, repair/obstruction, piston, and fluid behavior — `UNVERIFIED`.
- Phase 8: live cross-chunk placement, tracking, restart, lifecycle, repair, and unloaded-required-chunk rejection — `UNVERIFIED`.
- Phase 9: actual-gameplay full-block, stair/corner, slab, wall, fence, and supported-attachment adjacency — `UNVERIFIED`.
- Phase 10: both monolith models in all four facings, horizon/alignment/culling/duplicate-render views before and after cycle/reconnect/restart — `UNVERIFIED`.
- Phase 11: 12-shrine/8-monolith dense-scene responsiveness, frame behavior, tick/memory behavior, and log volume — `UNVERIFIED`.
- Phase 12: Client A and Client B logs, disconnect/resource-reload ranges, and live diagnostic boundedness — `UNVERIFIED`. Only the server-only startup log was available.
- No live test-case ID received a `PASS` or `FAIL`, no world test-zone coordinates were assigned, and no screenshot or authenticated identity evidence was created.

### Defects and scope

- No live production defect could be discovered or reproduced because the live phases did not run. No production, test, resource, asset, schema, renderer, network, lifecycle, placement, collision, or content file changed.
- No automated regression was added because no defect was corrected. All 19 protected owner-approved asset files remain unchanged, and the deterministic report continues to validate the exact nine shrine and two monolith variants.
- No gameplay, content, art, recipe, command, NPC, Rails, website, cross-family conversion, reverse cycling, rotation, resizing, force-loading, renderer, decorator, part entity/item, telemetry, or Milestone 10 work was added.

### Final automated validation

All commands ran from `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex` after the owner limitation approval.

- `.\gradlew.bat test --tests "com.seggellion.britannia_mod.structure.*" --no-daemon --no-configuration-cache --stacktrace`: exit `0`; 39.3 seconds (`BUILD SUCCESSFUL in 38s`); 32 classes / 200 JUnit methods, zero failures/errors/skips; 30 tasks (1 from cache, 29 up-to-date).
- `.\gradlew.bat test --no-daemon --no-configuration-cache --stacktrace`: exit `0`; 21.5 seconds (`BUILD SUCCESSFUL in 21s`); 32 classes / 200 JUnit methods, zero failures/errors/skips; 30 tasks (1 from cache, 29 up-to-date).
- Before clean, no generated/cache/run/log path was tracked and no unrelated user-owned path was a clean target. `.\gradlew.bat clean build --no-daemon --no-configuration-cache --stacktrace`: exit `0`; 139.1 seconds (`BUILD SUCCESSFUL in 2m 18s`); 32 classes / 200 methods from cache, zero failures/errors/skips; 36 tasks (6 executed, 20 from cache, 10 up-to-date).
- `.\gradlew.bat test --tests "com.seggellion.britannia_mod.structure.hardening.MilestoneEightContentReportTest" --no-daemon --no-configuration-cache --stacktrace`: exit `0`; 37.2 seconds (`BUILD SUCCESSFUL in 36s`); focused deterministic catalogue/report/resource path/hash/localization validation passed; 30 tasks (1 executed, 29 up-to-date).
- Final `git diff --check`, source status, staged scope, JAR resource inspection, and five-copy hashes are recorded after documentation completion.
- Known build warning remains Gradle's incubating daemon-JVM discovery notice; no new compile or test failure occurred.

### Final production package inspection

- Thin `Britannia_Mod_shrines_m2_codex-0.1.7k.jar`: 22,245,612 bytes; 4,688 entries; SHA-256 `45F71263DA9988C20CFE5E7A98E58EFEFD949E820FBC041285AB49F5D1672AF7`.
- Deployable `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar`: 22,817,250 bytes; 4,692 entries; SHA-256 `998CF12AC3BF0E4AA07E7558081EC361762913560A7C513B840D1C785BFD93E2`.
- Each contains 107 structure-class entries, nine shrine textures, two monolith geometries, two monolith textures, both animations/item presentations, localization, one anchor/part architecture, one anchor renderer, and all Milestone 1-8 production content.
- Each contains zero tests/fixtures, part renderer, part block entity, direct anchor/part item, third monolith variant, cross-family conversion, or Milestone 10 package. The deployable JAR's five copies match exactly.

### Acceptance and next milestone

- `PASS`: source/shared isolation; correct start state; clean deployable selection; final automated suites/build; deterministic content/resource validation; JAR contents; five matching hashes; private online-mode server configuration; clean production-server startup; no scope expansion; no push/transfer.
- `UNVERIFIED` by explicit owner-approved limitation: both-client runtime/version evidence, concurrent authentication, and every dependent live Phase 1-12 outcome listed above.
- `FAIL`: none. `UNVERIFIED` does not mean `PASS` and must not be cited as gameplay evidence.
- Milestone 9 is documented as accepted with explicit limitations by project-owner override. Milestone 10 requires separate authorization and was not begun.

## 2026-08-04 - Milestone 10: Final Audit and Conditional Merge Readiness

### Authorization and decision record

- Starting/audited source commit: `c69057dc1b2a961f73b7c8b87ae7bc30d7a41c43` (`docs(structures): record live validation evidence`) on clean branch `shrines-monoliths` in `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`.
- Isolated `origin/patch-18` and shared local `patch-18` both resolved to the approved target `62df1dc97c5113a86f9c0f258cb90538f31efe89`, also the merge base. Starting divergence was 0 behind / 13 ahead of `origin/patch-18` and 0 behind / 11 ahead of `origin/shrines-monoliths`.
- The shared repository remained read-only on its unrelated dirty `banking` state. No shared branch switch, modification, build, clean, stage, commit, reset, stash, merge, or rebase occurred.
- Decision: proceed to Milestone 10 without the prescribed two-authenticated-client Milestone 9 session.
- Status: approved.
- Date: 2026-08-04.
- Scope: final audit and conditional merge-readiness preparation only.
- Reason: the owner accepts the missing Milestone 9 live evidence as an explicit limitation and authorizes the final audit to proceed.
- Implementation consequence: every unperformed live check remains `UNVERIFIED`; merge readiness remains conditional; no multiplayer or live-gameplay claim may be made; the complete deferred matrix remains in `POST_MERGE_VALIDATION.md`.
- Documents affected: this log, `OPEN_QUESTIONS.md`, `MERGE_READINESS.md`, and `POST_MERGE_VALIDATION.md`.
- Follow-up validation: a controlled two-distinct-authenticated-client session using the exact release-candidate JAR before production promotion unless separately waived by the owner.
- The single ending documentation commit is `docs(structures): complete merge-readiness audit`; its full hash is recorded in the final report because a commit cannot embed its own hash without amendment.

### History and path audit

- Audited all thirteen commits from merge base through Milestone 9. Full hashes, subjects, milestone association, and per-commit file counts are in `MERGE_READINESS.md`.
- Confirmed no merge commit, duplicate rebase implementation, unrelated banking content, direct `patch-18` project commit, destructive history operation, or root-spec deletion. The original Milestone 4 implementation, transparent revert, and owner-approved restoration remain visible and reachable.
- Full feature diff: 112 paths, 15,390 insertions, 3 deletions; 106 added, 6 modified, 0 deleted, 0 renamed; 47 production Java, 35 test Java, 22 main resources, 5 project documents, 2 authoritative root specifications, and 1 build file.
- Every path is classified in `MERGE_READINESS.md`; unrelated count is zero. `git diff --check` for the historical range identifies only intentional Markdown hard-break trailing spaces in the two authoritative root specifications. Milestone 10 worktree and staged documentation diffs are whitespace-clean.

### Architecture, schema, security, and content audit

- Registrations remain exact: one `britannia_mod:large_structure_anchor`, one `britannia_mod:large_structure_part`, one anchor-only `britannia_mod:large_structure` block entity type, zero part entities, `britannia_mod:shrine`, `britannia_mod:monolith`, zero ordinary anchor/part BlockItems, reused `britannia_mod:interior_decorator_tool`, one anchor renderer, and zero part renderers.
- One logical object owns each four-cell shrine or eighteen-cell monolith. Parts contain only facing/local offset and no complete family, variant, footprint, drop, entity, or complete-render ownership.
- `SOLID_CELL`, cell-bounded shape, non-waterloggable/fluid rejection, piston `BLOCK`, persisted-footprint authority, no force-loading, per-cell protection, atomic reverse-order ownership-aware rollback, centralized lifecycle/drop/pick/explosion, obstruction preservation, bounded integrity, and no global scan all remain supported by production source and tests.
- Shrine rendering remains shared-geometry/texture-selection only; monolith rendering selects distinct model/texture pairs; both use visual-only `[0,16,0]` voxels exactly once; parts do not render; fallback is bounded and identity-preserving.
- Interior Decorator cycling remains logical-server authoritative, creative-or-permission-level-2, catalogue validated, same-family only, `variant_id` only, and transactional. The client cannot submit arbitrary IDs, resources, footprints, or offsets.
- Item and placed schemas remain version 1. Components are `britannia_mod:shrine_instance_state` and `britannia_mod:monolith_instance_state`; placed NBT is under `shrine_state`; exact keys/codec/update paths and closed-failure behavior are documented in `MERGE_READINESS.md`.
- No shrine/monolith registry ID existed at the merge base, so no prior-feature alias or migration is required. The pre-existing Interior Decorator is reused.
- Value-suppressed committed-history and current-tree scans found no secret assignment, RCON password, authentication/launcher token, credential, world, raw log, server configuration, EULA, backup, or JAR in the feature history.
- Catalogue remains exactly nine shrine and two owner-approved provisional monolith variants. `CONTENT_REPORT.json` remains deterministic. The protected inventory is 19 files (13 shrine and 6 monolith), not 20; all 19 source hashes remain identical to the pre-M10 record and match packaged entries.

### Automated validation

All commands ran in the isolated clone.

- Focused command: `.\gradlew.bat test --tests "com.seggellion.britannia_mod.structure.hardening.*" --tests "com.seggellion.britannia_mod.structure.milestone.*" --tests "com.seggellion.britannia_mod.structure.item.*" --no-daemon --no-configuration-cache --stacktrace`; exit 0; 46.1 seconds (`BUILD SUCCESSFUL in 45s`); 9 classes / 39 JUnit methods, zero failures/errors/skips; 30 tasks, test executed and 29 up-to-date. This freshly exercised deterministic content/hash, diagnostics, collision/adjacency, scope/registration, and item codec/stream-codec coverage.
- Required structure suite: `.\gradlew.bat test --tests "com.seggellion.britannia_mod.structure.*" --no-daemon --no-configuration-cache --stacktrace`; exit 0; 33.7 seconds (`BUILD SUCCESSFUL in 32s`); 32 classes / 200 methods, zero failures/errors/skips; test from cache, 29 tasks up-to-date.
- Full suite: `.\gradlew.bat test --no-daemon --no-configuration-cache --stacktrace`; exit 0; 29.8 seconds (`BUILD SUCCESSFUL in 29s`); 32 classes / 200 methods, zero failures/errors/skips; test from cache, 29 tasks up-to-date.
- The test runs created only untracked `logs/debug.log` and `logs/latest.log`; preflight proved they did not exist before the run. Their resolved directory was inside the isolated clone and they were removed before clean. No user-owned generated/untracked work was a clean target.
- Clean build: `.\gradlew.bat clean build --no-daemon --no-configuration-cache --stacktrace`; exit 0; 178.4 seconds (`BUILD SUCCESSFUL in 2m 57s`); 36 tasks: 6 executed, 16 from cache, 14 up-to-date; 32 classes / 200 methods, zero failures/errors/skips.
- Gradle's daemon-JVM-discovery notice remains incubating. No new compile/test failure or warning attributable to this feature appeared.

### Production JAR and exact runtime evidence

- Thin JAR: `Britannia_Mod_shrines_m2_codex-0.1.7k.jar`, 22,245,612 bytes, 4,688 entries, SHA-256 `B5C8B223FA7BF79460CB3541B9C24205B5CF2D05433242E2B90198FE22BBAB51`.
- Deployable JarJar artifact: `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar`, 22,817,250 bytes, 4,692 entries, manifest `Manifest-Version: 1.0`, SHA-256 `5F1619DBBD50FDD35875ACC8D5CCC610F05DCBB6ECEF4F136A8471392C42B04A`.
- The deployable JAR contains 107 structure-class entries, nine shrine textures, two monolith geometries, two monolith textures, both structure animations/item models/localization, one anchor renderer, zero tests/fixtures, and zero part renderer. All 19 protected source/package hashes match.
- Fresh external topology: `C:\projects\britannia\validation\shrines-monoliths-m10` with `artifacts`, `server`, and `evidence`; no Git working tree. Source build, artifact, and server copies match the exact SHA-256, and the server has exactly one Britannia JAR.
- A fresh NeoForge 21.1.72 server with `online-mode=true` loaded Britannia 0.1.7k and GeckoLib 4.6.6, reached `Done (9.226s)`, received normal console `stop`, exited 0 after 42.4 seconds, and saved players, worlds, overworld, End, Nether, and all dimensions. No shrine/monolith/large-structure or registry error appeared.
- The established baseline `TitleScreen` invalid-dedicated-dist mixin diagnostic, first-run FML correction, missing optional `britannia_mod.properties`, union-schema warnings, and barrel warning remain. They are not structure regressions and are disclosed in the risk register.
- Development client `runClient -Pdev --no-daemon --no-configuration-cache --stacktrace` reached `mod/britannia_mod` reload, OpenAL, block atlas, and GUI atlas after 52.6 seconds with zero shrine/monolith resource error. Its exact hidden process tree was then terminated. This is class-loading/resource-registration evidence only, not gameplay or a normal client-exit result.
- Sanitized summaries remain outside Git at `C:\projects\britannia\validation\shrines-monoliths-m10\evidence`. Raw logs, world, configuration, EULA, installer, and JAR copies were not committed.

### Merge analysis, documents, and disposition

- Git 2.50.1 three-tree `git merge-tree` simulated source `c69057dc1b2a961f73b7c8b87ae7bc30d7a41c43` into target/base `62df1dc97c5113a86f9c0f258cb90538f31efe89`; exit 0; no conflict marker, `both modified` path, or predicted textual conflict. Target has zero post-base paths.
- Semantic review remains required for `build.gradle`, common/client registration, components, large-structure registry, Interior Decorator interaction, lifecycle/persistence, and localization. No target-side banner or banking overlap exists at the exact audited target.
- Created `MERGE_READINESS.md`, `ROLLBACK_PLAN.md`, and `POST_MERGE_VALIDATION.md`; updated verified facts, open-question disposition, and this log. No production, test, build, registration, schema, resource, model, texture, localization, asset, or runtime behavior file changed.
- `MERGE_READINESS.md` records `CONDITIONAL MERGE READINESS`; code review and a separately owner-authorized non-destructive repository-standard merge/PR may proceed. Production promotion remains blocked by `POST_MERGE_VALIDATION.md` unless separately waived.
- `UNVERIFIED`: every authenticated-client, two-client, live gameplay, live adjacency, live horizon/duplicate-render, client-log, and dense-scene performance criterion listed individually in `MERGE_READINESS.md` and carried into `POST_MERGE_VALIDATION.md`.
- No merge, push, fetch, transfer, release, deployment, tag, or live promotion was performed.
## 2026-08-04 - Corrective Milestone 9A: Shrine Render-Footprint Alignment and Creative-Tab Exposure

### Authorization, state, and live defect

- Work was performed only in `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex` on
  `shrines-monoliths`, beginning clean at owner-authorized latest milestone commit
  `51c83c82d50c8467f06606bbec60eefcb82c758b`. The prompt's earlier
  `c69057dc1b2a961f73b7c8b87ae7bc30d7a41c43` expectation was explicitly superseded by the owner.
- Starting merge base with `origin/patch-18` was
  `62df1dc97c5113a86f9c0f258cb90538f31efe89`; divergence was `0/14` from
  `origin/patch-18` and `0/12` from `origin/shrines-monoliths` (behind/ahead).
- The shared repository was captured read-only before work on unrelated dirty branch `banking` at
  `4018ec88097f3ab4776a9a16498fe098ae3f02ab`. No shared content was synchronized or mutated.
- Owner screenshot evidence reports correct four logical cells, correct cell-bounded collision, and
  valid adjacent oak stairs, but a shrine presentation shifted toward one side, an unintentionally
  empty portion of the 2 by 2 opening, stair penetration, and thin right-side striping/overlap.
  This entry does not claim the screenshot is visually resolved without the owner recheck.
- Ending commit is the one commit containing this entry with subject
  `fix(shrines): align geometry and expose creative item`; its full hash is reported externally
  because a commit cannot contain its own hash without changing that hash or amending history.

### Root cause and narrow correction

- The old shared model had three cubes: `[-24,0,-8] + [32,4,32]`,
  `[-20,4,-4] + [24,6,24]`, and `[-16,10,0] + [16,6,16]`; union X `[-24,8]`,
  Y `[0,16]`, Z `[-8,24]`, root pivot `[0,0,0]`, no child bone, cube pivot,
  rotation, or inflate, and box UV origins `[0,0]`, `[32,32]`, `[64,64]`.
- GeckoLib 4.6.6 converts Bedrock X using `-(originX + sizeX) / 16 .. -originX / 16`.
  The old raw X center `-8` therefore became renderer X `+0.5` block. GeckoLib then translated
  the model by `(0.5,0,0.5)` to the anchor cell center and rotated around that point. The intended
  lower-front-left anchor contract needs renderer center `(-0.5,+0.5)`, so the old model was
  displaced by exactly one block: North `+X`, East `+Z`, South `-X`, West `-Z`.
- Renderer translation, scale `1`, pivot, North/East/South/West rotations `0/-90/180/90`, shrine
  render offset `0`, and finite all-facing bounds were correct. `ShrineRenderer` was not changed.
- Corrected cubes are `[-7,0,-7] + [30,4,30]`, `[-4,4,-4] + [24,6,24]`, and
  `[0,10,0] + [16,6,16]`; union X/Z `[-7,23]`, Y `[0,16]`. The new raw X center `+8`
  becomes renderer X `-0.5`; Z remains `+0.5`. The 30-voxel base is symmetrically inset one model
  voxel from all four footprint edges, spans `1.875 x 1.875` blocks, and preserves the stepped
  silhouette, texture identities, UV safety, and one-block height.
- Old shrine geometry SHA-256 was
  `C31915013A7D50D1732225764D4F94FEB1AD141515BAC0F3CADC81E5C8B3BCA0`; new is
  `05C52F184101C5AA62EEE515EB3BB285975FAAE2744EAC3381512000F0EEE62E`.
- Striping diagnosis: there was exactly one dynamic anchor renderer and no anchor/part baked
  geometry, part renderer, duplicate cube, zero/negative cube, rotation, or inflate. The proven
  one-block model displacement put dynamic faces inside the neighboring stair cells, allowing
  depth-overlapping stair/model surfaces at the reported right edge. Corrected envelopes have
  one-voxel clearance and do not touch any stair cell. Final GPU confirmation remains owner-only.

### Exact world envelopes and invariants

All values below are blocks relative to the anchor position; vertical range is always `[0,1]`.

| Facing | occupiedFootprintEnvelope X/Z | old rendered X/Z | corrected rendered X/Z |
| --- | --- | --- | --- |
| North | X `[-1,1]`, Z `[0,2]` | X `[0,2]`, Z `[0,2]` | X `[-15/16,15/16]`, Z `[1/16,31/16]` |
| East | X `[-1,1]`, Z `[-1,1]` | X `[-1,1]`, Z `[0,2]` | X `[-15/16,15/16]`, Z `[-15/16,15/16]` |
| South | X `[0,2]`, Z `[-1,1]` | X `[-1,1]`, Z `[-1,1]` | X `[1/16,31/16]`, Z `[-15/16,15/16]` |
| West | X `[0,2]`, Z `[0,2]` | X `[0,2]`, Z `[-1,1]` | X `[1/16,31/16]`, Z `[1/16,31/16]` |

- The production transform helper derives the exact four-cell envelope from the authoritative
  footprint and converts/rotates the parsed Bedrock bounds according to GeckoLib's actual convention.
- For every facing, horizontal centers match exactly, all four exterior clearances are `1/16` block,
  and no corrected AABB intersects any of the eight perimeter block AABBs.
- The stair regression installs eight actual registered oak-stair states around every facing,
  including top/bottom, straight, inner, and outer controls. All nine shrine variants retain the
  same geometry and leave the stair-state snapshot unchanged. Existing disk/update-tag/packet and
  adjacency-phase tests continue to cover save/load and cycling state invariance.
- Anchor/part `SOLID_CELL` collision, selection, occlusion, support, replaceability, piston/fluid
  behavior, four-cell shrine placement, eighteen-cell monolith placement, persistence, lifecycle,
  integrity, drops, pick block, cycling, authorization, synchronization, and schemas were unchanged.

### Creative-tab integration

- Existing tab: `britannia_mod:britannia_decor_tab` (Decorative & Graveyard); no new tab.
- `CreativeTabRegistry` accepts exactly one stack returned by
  `shrineCreativeStack(LargeStructureRegistry.SHRINE.get())` near the existing Ankh/Pentagram entries.
- The helper uses the existing item-state API:
  `configuredStack(ShrineItemStateAccess.defaultState())`. The explicit state is schema `1`,
  family `shrine`, variant `honesty`; it validates directly without server-default fallback and enters
  the existing placement path.
- Registry count is unchanged: one `britannia_mod:shrine` and one monolith item. No shrine-variant,
  anchor, part, or monolith Creative entry; no recipe or survival-acquisition change.

### Protected assets before and after

Only the shared shrine geometry changed. These SHA-256 values were identical before/after and match
both production JARs:

- Shrine fallback geometry `460F9FDDE68EC578C3FF1B4E26B457AFCF3467485325B4189C14E02820CC4781`;
  animation `F20A6AFD94D1FCF6463B8FDA98853781FC8548293293514199C4AC1E4C5A981E`;
  item model `829C55BB91B761F7529607B3BFD439B73D6A171F01556C12D5F50D5991636985`.
- Shrine textures: Honesty `D35747568A37960736C20F8359F55043B19739FC7D1FAB34144F8F971C36BCCC`;
  Compassion `79160E8AF141E4395A64D64439F7FBF0C2170D78073A136E6CBEF8A130FC87BC`;
  Valor `A748C870317998F911AC328960685F4B41AE8330635DC839F35D27EC6ACA4A1F`;
  Justice `5F01D14F16870EBA66C6A4C3E2F19C919E403A5DCDDC12037904285C825B1B8B`;
  Sacrifice `E4C56B44DC44C749DB10E2D34824DCF0079774FAAF9C2368330CC57036B4EEC4`;
  Honor `ADC2A67CFA7476D4C1D18A7C49E9F1937D552099FCFE9F1D3C821B832135D381`;
  Spirituality `D50CF726BD459C85313A9173E708C49C3ADC72559D0EABC9458FDFED8FF05CF1`;
  Humility `E201645E5D9058E28022B22904114E09823E736DDE8021D2E4DA2CE9E558AC8A`;
  Chaos `D8B2FDEB4158BBF86A053CDD383E532569F4DDDAEFF178D2472F5ABC2507EDC3`.
- Monolith existing/alternate geometry
  `0D58B1ED73A8811E10B276DB7E55B29F7248DD047074C0FE786882DF0757E53C` /
  `B2FCBE6841C53313A754A9722E5633957A9AB0334FB96FF05FBCFC42BA7512DC`;
  textures `FE07CE0672EE51D76F2833D1044264C7B65B2ADEB076873D1B07C953509944F4` /
  `E320B72F2D0B9429D07DD4E62D264535760797AE6D1F9DD047C082B6736A2C4A`;
  animation `D63D4CBCEF6A3E410EE94F38F5684F7B3E9F94BCC69B4D80C925FBBB61FC1530`;
  item model `E48339859F7AA66BBC08246B8BC65AC1827E28241509518F9884A854739A2BED`.

### Validation and packaging

All commands ran from `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`.

- First focused compile attempt exceeded the initial tool timeout: exit `124`, 124.0 seconds. The
  immediate completed run exposed one test-only count assumption (`5` tab-register call sites versus
  five holders plus the event-bus registration): exit `1`, 35.1 seconds, 7 methods, one failure,
  zero errors/skips. The assertion was narrowed to the five actual tab holders; production was unchanged.
- Final corrective class:
  `gradlew test --tests "...CorrectiveMilestoneNineARenderAlignmentTest" ...`; exit `0`, 39.8 seconds,
  1 class / 7 methods / at least 109 explicit new loop cases, zero failures/errors/skips; 30 tasks,
  2 executed and 28 up-to-date.
- Focused corrective/content/hash command (corrective class, `MilestoneEightContentReportTest`, and
  `InteriorDecoratorMilestoneFiveScopeTest`): exit `0`, 39.1 seconds, 3 classes / 14 methods,
  zero failures/errors/skips; 30 tasks, 2 executed and 28 up-to-date.
- Structure suite: `gradlew test --tests "com.seggellion.britannia_mod.structure.*" ...`; exit `0`,
  41.4 seconds, 33 classes / 207 methods, zero failures/errors/skips; 30 tasks, 1 executed and
  29 up-to-date. Existing 275,936 Milestone 8 matrix cases plus 109 explicit new cases give at least
  276,045 counted logical cases, excluding other looped tests.
- Full suite: `gradlew test ...`; exit `0`, 39.1 seconds, 33 classes / 207 methods, zero
  failures/errors/skips; 30 tasks, 1 executed and 29 up-to-date.
- Before clean, tracked generated files were absent. Untracked root `logs` and ignored `.gradle`,
  `build`, `run`, and `runs` were reproducible current-session outputs; `clean` targets only `build`.
  `gradlew clean build ...`; exit `0`, 160.6 seconds (`BUILD SUCCESSFUL in 2m 40s`),
  36 tasks: 7 executed, 20 from cache, 9 up-to-date; tests restored from cache, 33 classes / 207 methods.
- `git diff --check`: exit `0`; only Git's existing Windows LF-to-CRLF notices appeared.
- Dedicated server: RCON was disabled for the run; an isolated temporary Gradle input-forwarding
  init script allowed normal console `stop`. `Done (3.205s)`, all dimensions saved, Gradle exit `0`,
  82.6 seconds / `BUILD SUCCESSFUL in 1m 19s`, 32 tasks (1 executed, 31 up-to-date). Established
  dev-only refmap/TitleScreen-dist, missing config, asset-union, offline-mode, HTTP-service, and
  repetitive chest/barrel diagnostics remained; no shrine/monolith resource error appeared.
- Development client: reached Britannia resource reload, OpenAL/sound startup, and the
  4096-block-atlas marker in 73.9 seconds; the exact hidden process tree was then terminated because
  no interactive control was exposed. Existing unrelated missing-model/texture and legacy GeckoLib
  animation-expression diagnostics remained; no shrine/monolith missing/parse diagnostic appeared.
  This is startup/resource/class-loading evidence, not normal exit, gameplay, or visual proof.
- Thin JAR `Britannia_Mod_shrines_m2_codex-0.1.7k.jar`: 22,247,428 bytes, 4,688 entries,
  SHA-256 `2BA6B94641671411244C4FE9B41FACACCDC8955D04B8642735A63EC0AD9C2079`.
- Deployable JAR `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar`: 22,819,066 bytes, 4,692 entries,
  SHA-256 `496E8A41669B5EDEBAAFE5816D16C9361440382076F9A7DBE8001FBF5D5FB98E`.
  Each contains 107 structure class entries, one `ShrineRenderer`, zero part renderer/test entries,
  one corrected shrine geometry, nine shrine textures, two monolith geometries/textures, one Creative
  tab class, and all 19 protected resources with zero missing/mismatch. Packaged geometry hash is the
  corrected `05C52F...E62E`.

### Owner recheck and milestone boundary

`OWNER VISUAL RECHECK REQUIRED`: open the existing Decorative & Graveyard tab; verify exactly one
shrine and no anchor/part entry; place it and verify Honesty; surround all four cells with eight oak
stairs; verify centered fit, no stair penetration, empty side, stripe, overlap, or z-fighting; repeat
North/East/South/West; cycle Honesty, Compassion, and Chaos and verify identical geometry; verify
collision; save/reload and verify alignment persists. Creative exposure and every GPU-dependent visual
claim remain pending until owner evidence. Milestone 10 was not begun or resumed.

## 2026-08-04 — Owner shrine model and Creative-tab defect correction

- Starting HEAD: `9008f8c18e7e2c98dabcb207a464f94de5298f3d` (`fix(shrines): align geometry and expose creative item`).
- Owner reference: `C:\projects\britannia\raw fiels\shrines\Shrine_old.json`, plus its Blockbench source and exact `honesty.png` / `granite.png` materials. The reference establishes a low central surface at 9 model voxels and a granite exterior at 10 voxels, replacing the unintended three-step silhouette.
- Ending commit is the one commit containing this entry with subject `fix(shrines): restore flat model and creative entries`; its full hash is reported externally because a commit cannot contain its own hash without changing that hash or amending history.
- Shared geometry now contains two top-level bones and eight non-overlapping cubes: four equal `13 x 9 x 13` surface quadrants and four 10-voxel-high granite rim pieces. Its raw X/Z union remains `[-7,23]`, preserving the proven one-voxel perimeter clearance; raw Y is now `[0,10]` (`0.625` block) instead of `[0,16]`.
- Per-face UVs divide each 128 x 128 virtue texture into four exact 64 x 64 top quadrants. `ShrineGraniteRenderLayer` hides the surface and re-renders only `granite_rim` with the exact owner-supplied `textures/block/shrine/granite.png`; monolith models have neither bone and skip the layer.
- The nine owner-supplied virtue textures replace the former generated textures. Geometry SHA-256 is `3B98308F509E264220381B6E32CC9A8ACFE6FFE3470B38CA01B10589BB562BAC`; granite SHA-256 is `A1D3C1A881B6DC6990EB56932B702CDA78AE0BBF10355FDA90B8A3133B4CCA77`. Exact texture hashes are recorded in `CONTENT_REPORT.json` and enforced by two resource tests.
- Existing `britannia_mod:britannia_decor_tab` now exposes exactly one explicitly configured shrine stack (`shrine/honesty`) and one explicitly configured monolith stack (`monolith/diagnostic_missing_content`). No new tab, registry item, anchor/part item, recipe, schema, placement, lifecycle, persistence, integrity, or cycling behavior was introduced.
- The owner-created `textures/block/shrine/old/` backup remains untouched and untracked. `build.gradle` excludes it from resource processing so it cannot enter either production JAR; it is not staged or committed.
- First focused run compiled production successfully but one parser assertion still expected one bone: exit `1`, 150.5 seconds, 7 methods, three failures sharing that test-helper cause, zero production compile errors. After correcting the expected bone count, the focused class passed: exit `0`, 50.9 seconds, 7 methods, zero failures/errors/skips, 30 tasks (2 executed, 28 up-to-date).
- Focused geometry/content/hash command (corrective renderer class, Milestone 5 scope/hash class,
  and deterministic content report): exit `0`, 230.5 seconds, 3 classes / 15 methods, zero
  failures/errors/skips; 30 tasks (3 executed, 27 up-to-date).
- Structure suite: `gradlew test --tests "com.seggellion.britannia_mod.structure.*" ...`; exit `0`,
  121.2 seconds, 33 classes / 208 methods, zero failures/errors/skips; 30 tasks (1 executed,
  29 up-to-date).
- Full suite: `gradlew test ...`; exit `0`, 85.2 seconds, 33 classes / 208 methods, zero
  failures/errors/skips; 30 tasks (1 executed, 29 up-to-date).
- Before clean, only ignored reproducible `build`, `.gradle`, `run`, and `runs` paths were eligible
  for Gradle outputs. Untracked `logs/` and the owner `old/` backup were preserved; Gradle `clean`
  targeted only `build/`. `gradlew clean build ...` exited `0` in 249.2 seconds (`BUILD SUCCESSFUL
  in 4m 8s`), 36 tasks (6 executed, 20 from cache, 10 up-to-date); cached test results remain
  33 classes / 208 methods, zero failures/errors/skips.
- Thin JAR `Britannia_Mod_shrines_m2_codex-0.1.7k.jar`: 21,988,612 bytes, 4,690 entries,
  SHA-256 `358D6B436C2D4FD74BFB2F622F0D737FA7680A83055CE91DE6FE048EBD7EA7FA`.
  Deployable `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar`: 22,560,250 bytes, 4,694 entries,
  SHA-256 `58C1F837196B0655EB91915C845E2844C95EA8960691AB57F3E81A78E6C4B6E7`.
  Both contain 107 structure classes, one `ShrineRenderer`, one `ShrineGraniteRenderLayer`, one
  Creative-tab class, one shrine geometry, ten shrine textures including granite, and zero `old/`
  or test entries. Source-to-JAR SHA-256 comparison covered geometry plus all ten production shrine
  textures in both artifacts with zero missing entries and zero mismatches.
- The first bounded hidden client smoke stopped on an imprecise generic legacy model-warning marker;
  it produced no shrine/granite diagnostic. The corrected smoke reached resource reload, OpenAL, and
  the 4096-block-atlas marker in 112.7 seconds, then terminated its exact six-process tree. Exact
  `shrine.geo`, `shrine/granite`, `textures/block/shrine`, and `ShrineGranite` diagnostic count was
  zero. Existing unrelated model/blockstate, invalid-path, and legacy GeckoLib animation warnings
  remain. This is resource/startup evidence, not a visual gameplay pass.
- `git diff --check` passes; only Git's existing Windows LF-to-CRLF notices appear. Live GPU
  appearance remains unverified pending the owner recheck in `OPEN_QUESTIONS.md`. Milestone 10
  remains suspended.

## 2026-08-04 — Owner visual follow-up: UV row and height correction

- Starting HEAD: `257155c1dbf494074e9881992b8e50eb3ed85826` (`fix(shrines): restore flat model and creative entries`).
- Owner screenshot evidence showed the Honesty hand split across the wrong world rows: the source texture's upper 64-pixel half appeared in the lower two cells and the lower half appeared in the upper two cells. This was a top-face UV row-wrap error, not a texture-file defect.
- The surface cubes at raw Z `-5` now select V `64..128`; the cubes at raw Z `8` select V `0..64`. U remains `0..64` / `64..128`, so the correction moves the assembled image upward by one whole 2 x 2 cell row without editing, regenerating, recoloring, or renaming any PNG.
- Per owner direction, the four center cubes change from `13 x 9 x 13` to `13 x 14 x 13`; the four granite rim cubes change from heights `10` to `15`. Raw bounds are now X/Z `[-7,23]`, Y `[0,15]`; horizontal alignment and one-voxel perimeter clearance remain unchanged.
- Geometry SHA-256 changes from `3B98308F509E264220381B6E32CC9A8ACFE6FFE3470B38CA01B10589BB562BAC` to `374E8455075B8EFFCDFE4E36432FB9254A112F777141D319F90B5545EA92AB51`. All ten owner-supplied PNGs remain byte-identical.
- No renderer, material-layer, Creative-tab, placement, collision, persistence, lifecycle, integrity, cycling, schema, monolith, or Milestone 10 behavior is changed. Live GPU confirmation remains owner-only.
- Focused geometry/content/hash command (corrective renderer class, Milestone 5 scope/hash class,
  and deterministic content report): exit `0`, 86.0 seconds (`BUILD SUCCESSFUL in 1m 25s`),
  3 classes / 15 methods, zero failures/errors/skips; 30 tasks (3 executed, 27 up-to-date).
- Full suite: `gradlew test --no-daemon --no-configuration-cache --stacktrace`; exit `0`,
  56.1 seconds (`BUILD SUCCESSFUL in 55s`), 33 classes / 208 methods, zero
  failures/errors/skips; 30 tasks (1 executed, 29 up-to-date).
- Before clean, untracked `logs/` and the owner `textures/block/shrine/old/` backup were preserved;
  Gradle `clean` targeted only reproducible `build/`. `gradlew clean build --no-daemon
  --no-configuration-cache --stacktrace` exited `0` in 134.2 seconds (`BUILD SUCCESSFUL in
  2m 13s`), 36 tasks (6 executed, 20 from cache, 10 up-to-date); cached test results remain
  33 classes / 208 methods, zero failures/errors/skips.
- Thin JAR `Britannia_Mod_shrines_m2_codex-0.1.7k.jar`: 21,988,615 bytes, 4,690 entries,
  SHA-256 `5CCB101B1EB685D673CF18A1D78AEE617205666C62D4E9EDE2CB1D9C4FE0E8C1`.
  Deployable `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar`: 22,560,253 bytes, 4,694 entries,
  SHA-256 `E294FF418A94C407B46C0B020DB953D612B4C88EF5950D31D0807CBFC5C633D1`.
  Both contain 107 structure classes, one `ShrineRenderer`, one `ShrineGraniteRenderLayer`, one
  shrine geometry, ten shrine textures, zero `old/` entries, and zero test entries. The packaged
  geometry SHA-256 exactly matches the corrected source hash `374E8455...AB51` in both artifacts.

## 2026-08-04 - Refreshed Milestone 10: Corrected Merge-Readiness Audit

### Authorization and preflight

- The owner accepted the UV-row and 14/15-voxel correction with `Excellent. Commit. Move to next
  step in this project.` The correction was committed as
  `b26165350227e6d46cfde3af91ff738a42e48c11` (`fix(shrines): align texture and raise profile`).
  That instruction authorizes this refreshed final audit but does not claim that unperformed live
  checks passed.
- Starting production/evidence tip: `b26165350227e6d46cfde3af91ff738a42e48c11` on
  `shrines-monoliths` in
  `C:\projects\britannia\mod\Britannia_Mod_shrines_m2_codex`. The only untracked paths were the
  preserved `logs/` directory and owner-created `textures/block/shrine/old/` backup.
- Merge base and `origin/patch-18` were
  `62df1dc97c5113a86f9c0f258cb90538f31efe89`. Divergence was 0 behind / 17 ahead of
  `origin/patch-18` and 0 behind / 15 ahead of `origin/shrines-monoliths`.
- The shared repository was read-only on unrelated branch `banking`, commit
  `e4875e53850106af16e0176ee78e98f7380d8710`, with its pre-existing modified banking/config files
  and untracked project documents. No shared state was synchronized.
- The one ending documentation commit has subject
  `docs(structures): refresh corrected merge-readiness audit`; its full hash is reported externally
  because a commit cannot contain its own hash without amendment.

### History, scope, architecture, and content audit

- The prior merge-readiness package was stale because it ended at Milestone 9 and predated the
  original audit plus all three corrective Milestone 9A commits. The refreshed audit covers all 17
  non-merge commits from the merge base through `b261653` and preserves the original Milestone 4
  implementation/revert/restoration chronology.
- The corrected feature range has 119 paths, 16,811 insertions and 3 deletions: 112 added, 7
  modified, 0 deleted, 0 renamed. Exhaustive classification is 49 production Java, 36 test Java,
  23 main resources, 8 project documents, 2 authoritative root specifications, and 1 build file;
  unrelated count is zero.
- Architecture, schema, lifecycle, authority, collision, chunk, rollback, and saved-footprint
  conclusions remain unchanged. The refresh additionally audits the existing Decorative & Graveyard
  tab's two configured family stacks, `ShrineGraniteRenderLayer`, owner-supplied granite material,
  corrected four-quadrant shrine geometry, and the excluded untracked `old/` backup.
- The protected asset inventory is now 20 files: 14 shrine resources including granite and 6
  monolith resources. Exact current hashes are in `MERGE_READINESS.md`; all 20 match both production
  JARs byte-for-byte with zero missing/mismatch. `CONTENT_REPORT.json` remains deterministic and its
  current shrine geometry/texture hashes pass reconstruction tests.
- Range `git diff --check` reports exactly ten historical Markdown hard-break diagnostics, all in
  the two authoritative root specifications. No current documentation path is intentionally added to
  that exception.

### Refreshed automated and package validation

- Focused command:
  `.\gradlew.bat test --tests "com.seggellion.britannia_mod.structure.hardening.*" --tests
  "com.seggellion.britannia_mod.structure.milestone.*" --tests
  "com.seggellion.britannia_mod.structure.item.*" --tests
  "com.seggellion.britannia_mod.structure.render.CorrectiveMilestoneNineARenderAlignmentTest"
  --tests
  "com.seggellion.britannia_mod.structure.interaction.InteriorDecoratorMilestoneFiveScopeTest"
  --no-daemon --no-configuration-cache --stacktrace`; exit 0 in 31.2 seconds (`BUILD SUCCESSFUL in
  30s`), 11 classes / 51 methods, zero failures/errors/skips; 30 tasks (1 executed, 29 up-to-date).
- Required structure suite:
  `.\gradlew.bat test --tests "com.seggellion.britannia_mod.structure.*" --no-daemon
  --no-configuration-cache --stacktrace`; exit 0 in 35.0 seconds (`BUILD SUCCESSFUL in 34s`),
  33 classes / 208 methods, zero failures/errors/skips; 30 tasks (1 executed, 29 up-to-date).
- Full suite: `.\gradlew.bat test --no-daemon --no-configuration-cache --stacktrace`; exit 0 in
  21.5 seconds (`BUILD SUCCESSFUL in 20s`), 33 classes / 208 methods, zero
  failures/errors/skips; 30 tasks (1 from cache, 29 up-to-date).
- Before clean, only preserved untracked `logs/` and the owner `old/` backup were present; Gradle
  clean targeted reproducible `build/` only. `.\gradlew.bat clean build --no-daemon
  --no-configuration-cache --stacktrace`; exit 0 in 122.3 seconds (`BUILD SUCCESSFUL in 2m 1s`),
  36 tasks (6 executed, 20 from cache, 10 up-to-date).
- Thin JAR `Britannia_Mod_shrines_m2_codex-0.1.7k.jar`: 21,988,615 bytes, 4,690 entries,
  manifest `Manifest-Version: 1.0`, SHA-256
  `1AEF96104D311EAF463BC60E1845B7A4165EE87B381FE173192980049E806C11`.
- Deployable JAR `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar`: 22,560,253 bytes, 4,694
  entries, manifest `Manifest-Version: 1.0`, SHA-256
  `23DC141268A46E9E7BF1389A49B25405199AE3287624716F301F7AB824512C8A`.
  Both contain 107 structure classes, one anchor renderer, one granite layer, one Creative-tab class,
  ten shrine textures, two shrine geometries, two monolith textures/geometries, all 20 protected
  assets, and zero part renderer, test, fixture, project-documentation, or `old/` entry. The
  deployable JAR embeds GeckoLib 4.6.6 and NanoHTTPD 2.2.0 through JarJar.

### Exact server and merge evidence

- Fresh external validation directory:
  `C:\projects\britannia\validation\shrines-monoliths-m10-refresh-b261653`; it is not a Git working
  tree and contains exactly one Britannia JAR with the deployable hash. `online-mode=true`.
- The first standalone launch selected the shell's legacy Java 8 and failed in 0.2 seconds before
  NeoForge or the JAR loaded (`@user_jvm_args.txt` was treated as a class). The JAR hash was
  unchanged. This failed prerequisite attempt is not counted as server evidence.
- The configured Temurin Java 21.0.9 rerun loaded NeoForge 21.1.72, Britannia 0.1.7k, GeckoLib
  4.6.6, and NanoHTTPD; reached `Done (10.900s)`; received console `stop`; exited 0 in 53.5
  seconds; and saved overworld, End, Nether, and all dimensions. Before/after JAR hashes match and
  the complete log has zero shrine/monolith/large-structure error. Existing `TitleScreen`
  dedicated-dist, optional-config, first-run FML, union-schema, and barrel diagnostics remain the
  disclosed unrelated baseline.
- Git 2.50.1 three-tree `git merge-tree` simulated source `b261653` into unchanged target/base
  `62df1dc97c5113a86f9c0f258cb90538f31efe89`; exit 0, 17,532 output lines, zero conflict marker,
  zero `both modified` path, and zero target post-base path. No merge or index/worktree mutation
  occurred.

### Documentation and disposition

- Refreshed `MERGE_READINESS.md`, `ROLLBACK_PLAN.md`, `POST_MERGE_VALIDATION.md`,
  `PROJECT_FACTS.md`, `OPEN_QUESTIONS.md`, `PLACEHOLDER_ASSETS.md`, and this log. No production,
  test, build, registry, resource, texture, model, localization, or saved-state file changed in this
  refreshed audit.
- `OPEN_QUESTIONS.md` now distinguishes settled owner decisions from release evidence. The owner's
  acceptance authorizes conditional review; it does not establish complete all-facing GPU,
  representative-variant, Creative-tab, collision, reload, authenticated-client, multiplayer, or
  performance results.
- Readiness remains `CONDITIONAL MERGE READINESS`. Every unperformed authenticated-client and
  dependent live validation item remains individually `UNVERIFIED`; production promotion remains
  blocked by `POST_MERGE_VALIDATION.md` unless the owner separately accepts that release risk.
- No fetch, push, transfer, merge, rebase, reset, amend, stash, shared-repository mutation, release,
  deployment, tag, or live promotion occurred.

## 2026-08-04 - Owner visual follow-up: flush shrine footprint envelope

- Starting HEAD: `f4f18b8a19d66f10c6d8d4e2ba3b461eb5ca4eb9`
  (`docs(structures): refresh corrected merge-readiness audit`) on `shrines-monoliths`; only the
  preserved untracked `logs/` and owner `textures/block/shrine/old/` backup were present.
- Owner screenshots showed a uniform one-model-voxel line between the granite exterior and adjacent
  stairs. The defect exactly matched the intentional 30 by 30 model-voxel bounds `[-7,23]`: after
  GeckoLib conversion and anchor translation, every facing stopped `1/16` block inside each edge of
  the logical two-block-square footprint.
- The correction expands the complete model symmetrically around its unchanged raw center at X/Z
  `8`: raw bounds become `[-8,24]`, exactly 32 by 32 model voxels. Four surface quadrants change from
  `13 x 14 x 13` to `14 x 14 x 14`, covering the inner `[-6,22]` square. The four granite pieces
  remain 15 voxels high and 2 voxels thick, with horizontal pieces `32 x 15 x 2` and side pieces
  `2 x 15 x 28`.
- Exact effective horizontal bounds now equal the occupied four-cell envelope for every facing:
  North X `[-1,1]` / Z `[0,2]`; East X/Z `[-1,1]`; South X `[0,2]` / Z `[-1,1]`; West X/Z
  `[0,2]`, relative to the anchor. AABB boundary contact is exact and has zero volume, so no adjacent
  perimeter stair cell is entered.
- UV quadrant origins/sizes, corrected row orientation, 14-voxel surface height, 15-voxel granite
  height, center/pivot, renderer/material architecture, placement, collision, state, lifecycle,
  cycling, Creative exposure, monolith content, and schemas are unchanged. No PNG was edited.
- Geometry SHA-256 changes from
  `374E8455075B8EFFCDFE4E36432FB9254A112F777141D319F90B5545EA92AB51` to
  `DAEF7813658A6EB8C9831D538599EB58892E184174896D4B2179E21C29C099FE`.
- Focused geometry/hash/content command (corrective render alignment, Milestone 5 asset scope, and
  deterministic content report): exit 0 in 82.7 seconds (`BUILD SUCCESSFUL in 1m 22s`), 3 classes /
  15 methods, zero failures/errors/skips; 30 tasks (3 executed, 27 up-to-date).
- After adding explicit pairwise proof that the eight cubes meet only at faces and never overlap,
  the corrective render class was rerun: exit 0 in 28.2 seconds (`BUILD SUCCESSFUL in 27s`),
  1 class / 8 methods, zero failures/errors/skips; 30 tasks (2 executed, 28 up-to-date).
- Full suite: `.\gradlew.bat test --no-daemon --no-configuration-cache --stacktrace`; exit 0 in
  23.5 seconds (`BUILD SUCCESSFUL in 23s`), 33 classes / 208 methods, zero
  failures/errors/skips; 30 tasks (1 executed, 29 up-to-date).
- Before clean, only the preserved untracked `logs/` and owner `old/` backup were present; Gradle
  clean targeted reproducible `build/`. `.\gradlew.bat clean build --no-daemon
  --no-configuration-cache --stacktrace`; exit 0 in 86.8 seconds (`BUILD SUCCESSFUL in 1m 26s`),
  36 tasks (6 executed, 20 from cache, 10 up-to-date).
- Thin JAR `Britannia_Mod_shrines_m2_codex-0.1.7k.jar`: 21,988,614 bytes, 4,690 entries,
  SHA-256 `3C6F4F69F565E99981D583DE1597FFA2FB1033913B33412301515550B91B98E9`.
  Deployable JAR `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar`: 22,560,252 bytes, 4,694
  entries, SHA-256 `CC9A778E658B9C4171FA29382B10C8F482DC6A2E165BD5E76B7D6DF152416489`.
  Both package the exact new geometry hash, all 20 protected assets with zero missing/mismatch, ten
  shrine PNGs, zero `old/` entry, and zero test entry. No tracked PNG differs from HEAD.
- Live confirmation of no gap or visual boundary artifact remains owner-only; no commit was requested
  in this correction turn.

## 2026-08-04 - Chaos shrine light-granite rim selection

- Starting HEAD remains `f4f18b8a19d66f10c6d8d4e2ba3b461eb5ca4eb9` on
  `shrines-monoliths`. The preceding uncommitted flush-envelope correction, preserved `logs/`, and
  owner `textures/block/shrine/old/` backup remain intact.
- Per owner request, only synchronized `shrine/chaos` state selects
  `textures/block/shrine/light_granite.png` for the `granite_rim` material layer. The other eight
  shrine variants and every missing/non-shrine identity fail closed to the existing
  `textures/block/shrine/granite.png` path.
- `ShrineRimMaterialSelection` is a pure display selector over the already synchronized family and
  variant identity. `ShrineGraniteRenderLayer` consumes its result; renderer registration, geometry,
  main virtue texture selection, cycling order, placement, persistence, lifecycle, item components,
  collision, monolith behavior, and serialized schemas are unchanged.
- Owner-supplied `light_granite.png` is a 128 by 128 indexed PNG, 13,635 bytes, fully opaque, SHA-256
  `A1D3C1A881B6DC6990EB56932B702CDA78AE0BBF10355FDA90B8A3133B4CCA77`. It is currently
  byte-identical to `granite.png`; the runtime resource path differs correctly, but no visible color
  difference can occur until distinct approved bytes replace the light file.
- Deterministic content evidence now records `rim_texture_resource` and `rim_texture` hash for all
  nine shrines: exactly one light mapping for Chaos and eight default-granite mappings. Monolith
  report entries remain unchanged.
- Focused command: `.\gradlew.bat test --tests
  "com.seggellion.britannia_mod.structure.render.ShrineRimMaterialSelectionTest" --tests
  "com.seggellion.britannia_mod.structure.render.CorrectiveMilestoneNineARenderAlignmentTest"
  --tests
  "com.seggellion.britannia_mod.structure.interaction.InteriorDecoratorMilestoneFiveScopeTest"
  --tests
  "com.seggellion.britannia_mod.structure.hardening.MilestoneEightContentReportTest" --no-daemon
  --no-configuration-cache --stacktrace`; exit 0 in 68.4 seconds (`BUILD SUCCESSFUL in 1m 8s`),
  4 classes / 18 methods, zero failures/errors/skips; 30 tasks (4 executed, 26 up-to-date).
- Full suite: `.\gradlew.bat test --no-daemon --no-configuration-cache --stacktrace`; exit 0 in
  21.5 seconds (`BUILD SUCCESSFUL in 21s`), 34 classes / 211 methods, zero
  failures/errors/skips; 30 tasks (1 executed, 29 up-to-date).
- Before clean, the preserved untracked `logs/` and owner `old/` backup remained present. Clean
  targeted only reproducible `build/`. `.\gradlew.bat clean build --no-daemon
  --no-configuration-cache --stacktrace`; exit 0 in 80.5 seconds (`BUILD SUCCESSFUL in 1m 20s`),
  36 tasks (6 executed, 20 from cache, 10 up-to-date). Existing compile warnings remain limited to
  missing `@Overwrite` Javadoc, one deprecated removal-marked API, deprecation, and unchecked use.
- Thin JAR `Britannia_Mod_shrines_m2_codex-0.1.7k.jar`: 22,004,118 bytes, 4,692 entries,
  SHA-256 `CCBD232C39CE37EA539021C3D823E5AB567232899DC8F3D35E4711D229F23BCF`.
  Deployable JAR `Britannia_Mod_shrines_m2_codex-0.1.7k-all.jar`: 22,575,756 bytes, 4,696
  entries, SHA-256 `6116E0CB74EF8614FF5E9628C169A62C57262A35CDDC7E78219271B4EB80DF2D`.
  Both contain the selector class, the 13,635-byte light-granite resource, eleven shrine PNGs, and
  all 21 protected assets with zero source/JAR mismatch; both contain zero `old/` entry and zero test
  class.
- No commit, push, transfer, merge, rebase, reset, amend, fetch, or shared-repository mutation was
  performed.
