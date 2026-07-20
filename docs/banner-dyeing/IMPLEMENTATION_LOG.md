# Banner and Dyeing Implementation Log

## 2026-07-20 - Milestone 3: Data Registries and Validation Pipeline

### Files changed

- Added immutable registry primitives and the aggregate `RegistrySnapshot` under `bannerdyeing/registry`.
- Added resource discovery, structural decoding, cross-reference validation, fixed-point production disabling,
  atomic publication, read-only global access, and the common/server reload listener.
- Added structured validation severity, stage, issue, summary, report, and policy types under
  `bannerdyeing/validation`.
- Registered the reload listener explicitly from `BritanniaMod` on the NeoForge game event bus.
- Added `RegistryDatasetFixtures` and `RegistryDataLoaderTest` under test sources. The fixtures are generated in
  test code from the Milestone 2 codecs and never enter packaged resources.
- Updated `OPEN_QUESTIONS.md` with the unresolved physical-asset mapping boundary.

### Commands and results

1. Required Git preflight:
   - `git branch --show-current` - `banners-dyetub`.
   - `git status --short --branch` - only the preserved modified `ModConfig.java` and preserved untracked root
     specifications, `.claude/`, `logs/`, and `tmp/` were present.
   - `git merge-base banners-dyetub patch-18` - `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
   - `git rev-list --left-right --count patch-18...banners-dyetub` - `0 3`.
   - Milestone 2 full hash - `31e46ddba426136e904a7d59cfddb32322ca3a8b`.
2. Initial restricted `.\gradlew.bat compileJava --no-daemon --stacktrace` - Gradle distribution access was denied
   by the sandbox. The approved retry passed in 1 minute 2 seconds and confirmed the two existing warnings: missing
   `@Overwrite` Javadoc on `PlayerSleepMixin`, and the deprecated-for-removal `Item.initializeClient` override in
   `OrderShieldItem`.
3. First `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.RegistryDataLoaderTest"
   --no-daemon --stacktrace` - 25 tests ran; one report-order test assertion failed because it compared the report's
   explicit enum/domain ordering to unrelated ordinary string ordering. The redundant string-sort assertion was
   removed; validation ordering and production code were not weakened.
4. Corrected narrow registry test command - 25 tests passed, 0 failures, 0 errors, 0 skipped.
5. Completed narrow registry test command after the remaining structural-attribution cases were added - 29 tests
   passed, 0 failures, 0 errors, 0 skipped.
6. Final `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` -
   passed in 32 seconds. XML results: 159 tests, 0 failures, 0 errors, 0 skipped.
7. Final `.\gradlew.bat clean --no-daemon` - passed in 12 seconds.
8. Final `.\gradlew.bat test --no-daemon --stacktrace` - passed in 41 seconds. XML results: 159 tests,
   0 failures, 0 errors, 0 skipped.
9. Final `.\gradlew.bat build --no-daemon` - passed in 22 seconds; `jar`, `jarJar`, `assemble`, and `build`
   completed.
10. Common-source scan - no `net.minecraft.client`, Blaze3D, gameplay objects, item stacks, block entities, screens,
    packets, or `DeferredRegister` usage in the registry/validation implementation.
11. `git diff --check`, staged-diff checks, and final scope checks are recorded in the milestone handoff report.

### Registry and resource-loading decisions

- The six registries are typed views inside one immutable aggregate snapshot. Active lookup maps, deterministic
  definition lists, source-bearing entries, and disabled diagnostic lists are all defensively copied and read-only.
- `AtomicReference<RegistrySnapshot>` is the sole publication mechanism. Candidate work occurs off to the side and
  one reference write exposes the complete new snapshot to concurrent readers.
- `AddReloadListenerEvent` is the repository-compatible NeoForge 21.1 server-data hook. Effective resources are read
  and structurally decoded during `SimplePreparableReloadListener.prepare`; policy validation and publication occur
  during `apply`.
- Folders are exactly `banner_definitions`, `fabric_materials`, `pigments`, `material_palettes`, `banner_mounts`, and
  `placement_profiles` below each data namespace.
- `ResourceManager.listResources` supplies only the effective resource at a path, so ordinary higher-priority pack
  replacement is not a duplicate. Duplicate checks operate on embedded IDs across distinct effective resources.
- Embedded stable IDs are authoritative. Filenames and namespaces are retained as exact diagnostics but are not
  required to match an embedded ID. This permits pack organization without inventing a filename identity contract.

### Validation and policy decisions

- Stage 1 parses every effective JSON resource and reuses the Milestone 2 codecs for required fields, schema,
  dimensions, colours, OKLab values, identifiers, and local collection constraints. It never publishes partial data.
- Stage 2 validates palette owners, material/palette and natural-colour agreement, override pigments, default
  materials, default/supported mounts, placement-profile existence, and dimension containment. Profile width and
  height must be at least the banner's declared width and height; rotations and occupied cells remain deferred.
- Development/fail-fast rejects any candidate with an error, retains the prior snapshot, exposes/logs the complete
  report, and lets the reload framework surface failure without terminating the JVM from low-level code.
- Production/disable-invalid removes the owner of each invalid decoded definition, validates again, and repeats to a
  stable fixed point. A bad palette can therefore disable its material and then banners using that material. Authored
  immutable definitions are never mutated and unrelated substitutes are never selected.
- Every issue carries stage, severity, domain, optional definition ID, exact source resource, stable issue code,
  message, and optional related ID. Reports are deduplicated and deterministically ordered before logging once.

### Test coverage and integration boundary

- Test-only generated datasets cover valid data, every applicable missing/mismatch case, malformed and unknown-schema
  resources, duplicate IDs, simultaneous errors, dependency cascades, replacement and preservation, reference-safe
  production subsets, deterministic ordering, immutable exposure, empty datasets, and authoritative embedded IDs.
- Atomic publication has a concurrent-reader test, and common registry classes are scanned for client references.
- The listener is compiled and wired to `AddReloadListenerEvent`, but no automated Minecraft bootstrap/GameTest was
  added. Runtime resource-manager override behavior and listener invocation therefore have isolated core coverage plus
  API compilation, not a live-server integration test. `ResourceManager.listResources` itself owns pack priority.

### Known limitations and deviations

- Logical asset IDs are syntax-validated. Physical geometry/model/texture existence is not checked until the project
  chooses a reliable mapping across vanilla, GeckoLib, texture, and custom-loader resource types; this is recorded in
  `OPEN_QUESTIONS.md`.
- Structurally undecodable resources remain visible in the report but cannot appear in a disabled typed-definition
  list because no valid immutable definition exists to retain.
- No resource-pack-stack integration test was added because constructing the actual Minecraft pack/bootstrap layer is
  unsuitable for the ordinary unit harness. Same-path replacement uses the platform's effective-resource map.
- No production definitions, catalogue manifest, assets, gameplay registrations, components, items, blocks, block
  entities, screens, packets, rendering, recipes, commands, placement mechanics, or Milestone 4 work were added.

### Next milestone

Stop after the Milestone 3 commit and owner review. The next permitted work is Milestone 4 - Scaffold All 33 Banner
Placeholders - only; do not begin it as part of this milestone.

## 2026-07-20 - Milestone 2: Core Data Records and Codecs

### Files changed

- Added banner data contracts under `banner/data`: `BannerDefinition`, `BannerDimensions`, `BannerAssets`,
  `BannerSourceReference`, `BannerContentStatus`, `MountDefinition`, and `PlacementProfile`.
- Added common item-compatible banner state under `banner/state`: `BannerInstanceState`.
- Added dye definitions under `dye/data`: `FabricMaterialDefinition` and `PigmentDefinition`.
- Added palette contracts under `dye/palette`: `MaterialPalette` and `MaterialPaletteEntry`.
- Added dye state and result contracts: `DyeTubState`, `DyeResult`, and `MatchType`.
- Added `DataCodecs` for the shared schema, canonical sRGB, finite-number, OKLab, tag, and non-blank-string
  structural codecs.
- Added `CoreDataFixtures`, `CoreDataCodecTest`, and `CoreDataValidationTest`.
- Updated `OPEN_QUESTIONS.md` with the deliberately deferred occupied-cell/anchor semantics for placement profiles.

### Commands and results

1. Required Git preflight:
   - `git branch --show-current` - `banners-dyetub`.
   - `git status --short --branch` - only the preserved modified `ModConfig.java` and preserved untracked root
     specifications, `.claude/`, `logs/`, and `tmp/` were present.
   - `git merge-base banners-dyetub patch-18` - `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
   - `git rev-list --left-right --count patch-18...banners-dyetub` - `0 2`.
   - `git log --oneline --decorate -5` - expected Milestone 1 `129ed2d` and Milestone 0 `e43bbc5` were the first
     two feature commits.
2. Initial restricted `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon
   --stacktrace` - could not access/download the Gradle 8.9 distribution because sandbox network access was denied.
3. The first approved retry was given an accidentally short command timeout. Its orphaned worker temporarily held
   `build/test-results/test/binary/output.bin`; the next retry reported that output-lock error. `.\gradlew.bat --stop`
   stopped the one orphaned daemon. No source or test assertion failed in either attempt.
4. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` after the
   complete fixture coverage was added - passed in 11 seconds. XML results: 130 tests, 0 failures, 0 errors,
   0 skipped.
5. `.\gradlew.bat clean --no-daemon` - passed in 9 seconds.
6. `.\gradlew.bat test --no-daemon --stacktrace` - passed in 39 seconds. XML results: 130 tests, 0 failures,
   0 errors, 0 skipped.
7. `.\gradlew.bat build --no-daemon` - passed in 14 seconds; `jar`, `jarJar`, `assemble`, and `build` completed.
8. `git diff --check` - passed before documentation/staging review; repeated during final review.
9. Common-source client/API scan - no `net.minecraft.client`, Blaze3D, gameplay objects, registration APIs,
   reload listeners, payloads, screens, renderers, items, blocks, or block entities in the Milestone 2 data packages.

No initially failing JUnit test required correction. The only initial failures were Gradle environment access and an
orphaned-worker file lock, as described above.

### Structural validation decisions

- Reused all six Milestone 1 stable ID wrappers. Asset, model, texture, and palette identities use namespaced
  `ResourceLocation` values rather than a parallel identifier system.
- All stored top-level contracts use `BannerDyeingConstants.CURRENT_SCHEMA_VERSION`; unknown versions fail with an
  explicit codec error. Nested value records and the transient `DyeResult` do not repeat a schema field.
- Persistent codecs require every non-optional field and do not provide invalid-value defaults. Network codecs exist
  only for `BannerInstanceState` and `DyeTubState`, the two contracts selected for future typed synchronized item
  components by `PROJECT_FACTS.md`.
- Banner width is limited to one through three blocks. Height is limited to one through 16 blocks; 16 is a documented
  conservative occupancy bound that exceeds the current catalogue while preventing unbounded structural input.
- Banner orientations and mounts must be non-empty, and the default mount must be in the supported mount collection.
- Canonical sRGB is uppercase six-digit `#RRGGBB`. OKLab components and dye-result distances must be finite; distances
  must also be non-negative. Numeric fixture values remain illustrative authored data, not scientifically verified
  production colours.
- Palettes reject duplicate entry IDs, require their natural colour to be one of their own entries, and require local
  pigment overrides to target one of those entries. Overrides are copied into lexical pigment-ID order for stable
  output.
- Unlimited dye-tub uses have one representation: an absent `remaining_uses`. Finite counts may be zero or positive;
  a use count without a loaded pigment is structurally invalid. No consumption or replacement policy is encoded.
- Lists and maps are defensively copied and exposed as immutable collections. Source-sheet labels are preserved only
  as provenance and are never promoted to display names.

### Validation deferred to Milestone 3

- Registry membership and existence of referenced definitions, materials, pigments, colours, mounts, palettes,
  placement profiles, models, and textures.
- Agreement between a banner definition and its referenced placement profile or material palette.
- Cross-resource uniqueness, missing resources, disabled-entry policy, and complete registry-set validation.
- Whether an absent source pigment corresponds to the material's natural colour; that requires loaded material and
  palette data. Decoding intentionally remains valid without live registries.

### Known limitations and deviations

- `PlacementProfile` deliberately contains only schema version, stable ID, declared dimensions, and a wall-support
  flag. Occupied offsets, rotations, anchor choice, support-cell rules, and placed-state persistence remain deferred
  to the placement milestones and are recorded in `OPEN_QUESTIONS.md`.
- No data loading, registries, catalogue data, palette resolution, colour conversion, gameplay objects, components,
  packets, rendering, placement behavior, commands, recipes, or assets were added.
- `MaterialPaletteEntry` is nested in a versioned `MaterialPalette`; it does not repeat `schema_version`.
- `DyeResult` is a small immutable resolver result and is not a stored top-level state object, so it does not include a
  schema field or a network codec.

### Next milestone

Stop after the Milestone 2 commit and owner review. The next permitted work is Milestone 3 - Data Registries and
Validation Pipeline - only; do not begin it as part of this milestone.

## 2026-07-20 — Milestone 1: Feature Skeleton, IDs, and Test Harness

### Files changed

- Added common feature entry points: `BannerFeature`, `DyeFeature`, and `BannerDyeingBootstrap`.
- Added the shared `StableResourceId` contract and immutable `ResourceLocation` wrappers for banner definitions, fabric materials, pigments, resolved colours, mounts, and placement profiles.
- Added the stable `BannerOrientation` enum for wall-parallel and wall-perpendicular orientations.
- Added `BannerDyeingConstants` with schema version 1 and the initial cotton, brass, and iron IDs.
- Added JUnit Jupiter test dependencies and enabled the JUnit Platform in `build.gradle`.
- Added reusable ID fixtures and tests covering construction, parsing, equality, string form, persistent codecs, stream codecs, invalid identifiers, null rejection, orientation serialization, bootstrap constants, logger categories, and common-code client-reference safety.

### Commands and results

1. `git branch --show-current` — `banners-dyetub`.
2. `git merge-base patch-18 HEAD` — `62df1dc97c5113a86f9c0f258cb90538f31efe89`; `git rev-list --left-right --count patch-18...banners-dyetub` returned `0 1` before this milestone commit.
3. Initial restricted-sandbox narrow Gradle invocation — could not download/access the Gradle distribution because network access was denied; rerun with approved dependency access.
4. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` — first approved run reached project compilation and found one test-only `JsonOps` input type error. The test was corrected to use `JsonPrimitive`; production code compiled.
5. The same narrow test command after correction — passed in 13 seconds. XML results: 53 tests, 0 failures, 0 errors, 0 skipped.
6. `.\gradlew.bat clean --no-daemon` — passed in 17 seconds.
7. `.\gradlew.bat test --no-daemon --stacktrace` — passed in 59 seconds. XML results: 53 tests, 0 failures, 0 errors, 0 skipped.
8. `.\gradlew.bat build --no-daemon` — passed in 22 seconds; `jar`, `jarJar`, `assemble`, and `build` completed.
9. Common-source client-reference scan — passed with no `net.minecraft.client` or `com.mojang.blaze3d` references.
10. Out-of-scope API scan — passed with no registrations, block entities, menus, screens, renderers, recipes, or custom payload APIs in the Milestone 1 packages.

### Decisions

- Model stable domain identifiers as small immutable record wrappers around `ResourceLocation`, with a shared read-only contract while retaining distinct compile-time types.
- Put both persistent `Codec` and network `StreamCodec` definitions on each identifier type so later milestones share one canonical serialization boundary.
- Reject malformed identifiers through the repository's Minecraft 1.21.1 `ResourceLocation` validation rather than adding a second validation grammar.
- Keep the feature bootstraps side-effect-free in this milestone. Registration and gameplay wiring belong to later milestones.
- Use structured logger categories dedicated to banner and dye content validation.
- Add the smallest conventional JUnit 5 harness because the repository had no test framework or test sources.

### Known limitations and deviations

- No registries, content catalogue, JSON loading, blocks, items, block entities, screens, renderers, packets, recipes, or gameplay behavior are implemented; these are intentionally outside Milestone 1.
- The bootstrap classes establish common boundaries but are not invoked by the main mod initializer until a later registration milestone has real work to wire.
- This milestone has unit-level common-code safety checks only. No manual in-game check is meaningful for a registration-free skeleton.
- The first approved narrow run exposed and led to correction of a test-only compile error before the successful validation runs; it was not a production-code failure.

### Next milestone

Stop after the Milestone 1 commit and owner review. The next permitted work is Milestone 2 only; do not begin it as part of this milestone.

## 2026-07-20 — Milestone 0: Repository Discovery and Implementation Facts

### Branch-creation evidence

- Remote used: `origin` (`https://github.com/Seggellion/Britannia_Mod.git`).
- Remote refresh: `git fetch --prune origin` succeeded.
- Starting branch: `patch-18`, tracking `origin/patch-18`.
- `patch-18` tip after fetch: `62df1dc97c5113a86f9c0f258cb90538f31efe89` (`Add refill fishing rod barrel at 5213 66 8912 (#400)`).
- Local/remote divergence: `git rev-list --left-right --count patch-18...origin/patch-18` returned `0 0`.
- Existing feature branch check: `banners-dyetub` did not exist locally.
- Creation command: `git switch -c banners-dyetub patch-18`.
- Creation point / merge base: `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
- Initial feature/base divergence: `git rev-list --left-right --count patch-18...banners-dyetub` returned `0 0`.
- Current branch: `banners-dyetub`.
- No reset, deletion, merge, rebase, stash, or checkout-discard operation was used.

### Pre-existing working tree

The branch was intentionally created with the user's existing work present and preserved:

- Modified: `src/main/java/com/seggellion/britannia_mod/config/ModConfig.java` (local API base URL selection).
- Untracked: `.claude/`, `UltimaCraft_Banner_Dyeing_LLM_Build_Spec.md`, `UltimaCraft_Banner_and_Dyeing_System_Design.md`, `logs/`, and `tmp/`.

The two root specification files were read in full before branch creation. None of the pre-existing paths is part of the Milestone 0 commit.

### Files added

- `docs/banner-dyeing/PROJECT_FACTS.md`
- `docs/banner-dyeing/OPEN_QUESTIONS.md`
- `docs/banner-dyeing/IMPLEMENTATION_LOG.md`

No gameplay code, registrations, renderers, packets, screens, recipes, block entities, catalogue entries, or assets were added.

### Repository facts recorded

- Gradle 8.9 / NeoGradle UserDev 7.0.165.
- Minecraft 1.21.1 / NeoForge 21.1.72 / Java 21 / active official mappings.
- `britannia_mod` and `com.seggellion.britannia_mod` token mappings.
- NeoForge deferred registration conventions.
- Typed persistent/networked data components as the selected item-state architecture.
- NBT plus update-tag/update-packet block-entity conventions.
- Custom payload networking and direct client `Screen` flow.
- Client-only event gating and rendering registration.
- Static/manual recipe state and absent data generator.
- Empty unit/GameTest source state.
- Metal-specific blacksmithing material flow and the absence of a reusable general material ID.
- Branch, CI, and release integration conventions.
- The exact 33-banner catalogue constraint, including unnamed placeholder handling.

### Baseline commands and results

1. `git fetch --prune origin` — passed; added remote refs and confirmed `patch-18` remained current.
2. `.\gradlew.bat clean build --no-daemon` in the restricted sandbox — could not start because Gradle 8.9 download network access was denied (`java.net.SocketException: Permission denied: getsockopt`). This was an environment restriction, not a repository failure.
3. `.\gradlew.bat clean build --no-daemon` with approved dependency/network access — failed in `:neoFormPatch` before project compilation. A second identical clean-build invocation reproduced the failure.
4. `.\gradlew.bat test --no-daemon --stacktrace` with approved access — passed in 1m 10s. `compileTestJava` and `test` were `NO-SOURCE`. Project compilation emitted two existing warnings: missing Javadoc on `PlayerSleepMixin`'s `@Overwrite`, and the deprecated-for-removal `Item.initializeClient(IClientItemExtensions)` override in `OrderShieldItem`.
5. `.\gradlew.bat neoFormPatch --rerun-tasks --no-daemon --stacktrace` — passed/up-to-date in 20s, showing the NeoForm patch stage works outside the combined clean-build invocation.
6. `.\gradlew.bat clean --no-daemon` — passed in 12s.
7. `.\gradlew.bat build --no-daemon` immediately after the separate clean — passed in 42s; compiled, packaged `jar`/`jarJar`, and reported `test` / `testJunit` as `NO-SOURCE`.
8. `.\gradlew.bat tasks --all --no-daemon` — passed and confirmed `build`, `check`, `test`, `runData`, and `runGameTestServer` tasks.

Assessment: the combined `clean build` failure is pre-existing build/toolchain behavior, likely an ordering/cache interaction between parallel Gradle clean and NeoForm output. The inference is supported by the repeatable combined failure and successful isolated patch task plus separate clean/build. It does not currently block later work, provided verification uses separate `clean` and `build` invocations. No unrelated build fix was attempted.

### Documentation and post-change checks

1. Required-file/content validation — passed. All three documents exist; repository tokens, open-question categories, blacksmithing facts, and the exact-33/`Name Required` rule are present.
2. Trailing-whitespace scan across `docs/banner-dyeing/*.md` — passed with no matches.
3. `.\gradlew.bat build --no-daemon` — passed in 19s after the documentation changes; `test` and `testJunit` remained `NO-SOURCE`, and compilation/package tasks were up-to-date.
4. Final staged `git diff --check` and scope inspection are required immediately before commit.

### Decisions

- Use typed custom data components for banner and dye-tub item state.
- Use namespaced `ResourceLocation` IDs under `britannia_mod`.
- Do not reuse metal-only `UOMetalToolMaterial` or jewelry's separate metal enum for fabrics.
- Reuse blacksmithing's server-authoritative material-from-input interaction principles.
- Use the existing payload-opened client `Screen` convention for a slotless dye preview and a server-revalidated C2S confirmation.
- Keep renderers/screens/models in client packages and common state/codecs free of client imports.
- Register placed-banner rendering through the client render event; verify the non-deprecated item-render API in the rendering milestone.
- Preserve exactly 33 banner entries in release. Unnamed entries remain included with stable provisional IDs and `Name Required` status; placeholder names/dimensions are not final content.

### Known limitations and deviations

- The root specification files remain untracked and were not duplicated into `docs/`. The initiating prompt identifies the root copies as authoritative and limits the requested Milestone 0 commit to the three integration documents. This is a documented deviation from the playbook's general instruction to copy untracked specs into the documentation area.
- The repository has no implemented data-generation provider, reloadable JSON registry, unit tests, or GameTests. Later milestones must establish these incrementally.
- The repository's existing custom item renderer hook is deprecated for removal; its supported replacement must be verified before banner rendering work.
- No manual in-game check was required or performed for this documentation-only milestone.

### Commit

- Message: `docs(banners): record repository integration facts`
- Hash: this log is part of that commit; record the resulting hash in the Milestone 0 report.

### Next milestone

Stop at Gate A for owner review. Recommend Milestone 1 — Feature Skeleton, IDs, and Test Harness — only after the repository facts and integration choices are approved.
