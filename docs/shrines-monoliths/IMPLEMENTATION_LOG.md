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
