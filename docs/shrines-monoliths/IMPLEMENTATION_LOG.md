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
