# Banner and Dyeing Implementation Log

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
