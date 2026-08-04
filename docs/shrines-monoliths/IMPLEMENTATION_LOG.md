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
