# Patch 18 Integration Log

## Project

Patch 18 feature integration and banking-network compatibility migration.

## Protected release target

Exact Git reference: `patch-18` (local branch, tracks `origin/patch-18`; verified in sync on 2026-08-08)

Initial Patch 18 HEAD: `62df1dc97c5113a86f9c0f258cb90538f31efe89` ("Add refill fishing rod barrel at 5213 66 8912 (#400)", 2026-06-09)

## Integration branch

Recommended branch: `patch-18-network-integration`

Created from: `62df1dc97c5113a86f9c0f258cb90538f31efe89` (verified Patch 18 HEAD) on 2026-08-08 (Milestone 1)

Working directory or worktree: Dedicated Git worktree at `C:\projects\britannia\mod\Britannia_Mod-integration` (created via `git worktree add`; clean checkout, no untracked contamination). The primary working copy `C:\projects\britannia\mod\Britannia_Mod` remains on `patch-18` and holds the tracking documents. All merge work happens in the worktree only.

## Branch naming corrections

- The branch referred to in the project prompt as `shrines-monolith` is actually **`shrines-monoliths`** (plural). Verified: only `shrines-monoliths` and `remotes/origin/shrines-monoliths` exist.
- `Farming` capitalization confirmed (uppercase F, local and remote).
- `banners-dyetub` exists **locally only** — there is no `origin/banners-dyetub` (verified after `git fetch origin` on 2026-08-08).

## Source branches

All HEADs recorded 2026-08-08 after `git fetch origin`. Every branch's merge base with `patch-18` is the current Patch 18 HEAD itself (`62df1dc9`), i.e. all branches are strictly ahead of patch-18 and patch-18 is 0 commits ahead of each. All pairwise merge bases between feature branches are also `62df1dc9` — the branches are mutually independent.

| Branch | HEAD at project start | Merge base with Patch 18 | Read-only source confirmed |
|---|---|---|---|
| `banking` | `aa39914a2ef3caf5e1f137a657169264ae2c31cf` (in sync with origin) | `62df1dc97c5113a86f9c0f258cb90538f31efe89` | Confirmed (no writes performed) |
| `Farming` | `1753f7cdffdc919601aaad0334ea3e3a7781c0eb` (in sync with origin) | `62df1dc97c5113a86f9c0f258cb90538f31efe89` | Confirmed (no writes performed) |
| `shrines-monoliths` | `49a44506ef44b44f88a99b5cee630859a3b73926` (in sync with origin) | `62df1dc97c5113a86f9c0f258cb90538f31efe89` | Confirmed (no writes performed) |
| `blacksmithing` | `9acfb7e253580f1fafc9892f107a270d2d748653` (in sync with origin) | `62df1dc97c5113a86f9c0f258cb90538f31efe89` | Confirmed (no writes performed) |
| `banners-dyetub` | `5debcc11e5ecdd58a8a6302885197f84ac7f6ee4` (**local only; no origin ref**) | `62df1dc97c5113a86f9c0f258cb90538f31efe89` | Confirmed (no writes performed) |

Ahead counts vs `patch-18`: banking +77 commits, Farming +21, shrines-monoliths +20, blacksmithing +1 (single squashed commit), banners-dyetub +43.

## Milestone status

| Milestone | Name | Status | Starting HEAD | Ending HEAD | Commit | Validation summary |
|---:|---|---|---|---|---|---|
| 0 | Repository and branch audit | **Complete** | `62df1dc9` (patch-18, read-only) | `62df1dc9` (unchanged) | None (docs not committed) | Static audit only; no builds run |
| 1 | Patch 18 baseline validation and integration branch creation | **Complete** | `62df1dc9` | `62df1dc9` (no commits) | None (docs not committed) | Build SUCCESS; tests NO-SOURCE (none exist on baseline); dedicated-server smoke SUCCESS |
| 2 | Banking network architecture audit | **Complete** | `62df1dc9` (read-only) | `62df1dc9` (unchanged) | None (docs not committed) | Static audit only; canonical contract documented; no merge |
| 3 | Merge banking and establish canonical network baseline | **Complete** | `62df1dc9` | `9f7e800c` | `9f7e800c` (merge, parents `62df1dc9` + `aa39914a`) | Build SUCCESS; 658 unit tests pass; 333 gametests pass; dedicated server + client join smoke PASSED |
| 4 | Post-banking repository-wide compatibility inventory | **Complete** | `9f7e800c` (read-only) | `9f7e800c` (unchanged) | None (docs not committed) | merge-tree dry runs + symbol classification; no merge |
| 5 | Merge Farming and migrate its network usage | **Complete** | `9f7e800c` | `3cae58ed` | `3cae58ed` (merge, parents `9f7e800c` + `1753f7cd`) | Build SUCCESS; 766 unit tests 0 failures (6 policy skips); 333 gametests pass; server + client join smoke PASSED |
| 6 | Merge blacksmithing and migrate its network usage | **Complete** | `3cae58ed` | `882cc0e3` | `882cc0e3` (merge, parents `3cae58ed` + `9acfb7e2`) | Build SUCCESS; 771 unit tests 0 failures (6 policy skips); 333 gametests pass; server + client join smoke PASSED |
| 7 | Merge shrines-monoliths and verify compatibility | **Complete** | `882cc0e3` | `9b564af9` | `9b564af9` (merge, parents `882cc0e3` + `49a44506`) | Build SUCCESS; 984 unit tests 0 failures (6 policy skips); 333 gametests pass; server + client join smoke PASSED |
| 8 | Merge banners-dyetub and verify compatibility | **Complete** | `9b564af9` | `e4049712` | `e4049712` (merge, parents `9b564af9` + `5debcc11`) | Build SUCCESS; 1,679 unit tests 0 failures (20 policy skips); 333 gametests pass; server + client join smoke PASSED |
| 9 | Repository-wide network consolidation audit | **Complete** | `e4049712` (read-only) | `e4049712` (unchanged; no source changes needed) | None | 12-point audit passed; final green run: build SUCCESS, 1,679 unit 0 failures, 333 gametests pass |
| 10 | Cross-feature runtime and multiplayer regression | **Complete (automated scope)** | `e4049712` | `e4049712` (unchanged) | None | Clean-runtime boot (0 errors), join, reconnect, graceful restart, persistence all PASS; interactive/two-player scenarios documented as owner-pending |
| 11 | Final merge-readiness review | **Complete** | `e4049712` | `8ddf9734` (one corrective commit: removed a policy-escaped .MD doc) | `8ddf9734` | Evidence package complete; build + 1,679 tests green re-verified; NO merge into patch-18 performed |

## Milestone 0: Repository and branch audit

```text
Date: 2026-08-08
Status: Complete
Working directory: C:\projects\britannia\mod\Britannia_Mod (primary working copy, NOT a dedicated clone/worktree; audit was read-only)
Active branch: patch-18
Patch 18 reference: patch-18 (tracks origin/patch-18, in sync)
Starting HEAD: 62df1dc97c5113a86f9c0f258cb90538f31efe89
Ending HEAD: 62df1dc97c5113a86f9c0f258cb90538f31efe89 (unchanged)
Feature branch HEADs consulted:
  banking            aa39914a2ef3caf5e1f137a657169264ae2c31cf (origin in sync)
  Farming            1753f7cdffdc919601aaad0334ea3e3a7781c0eb (origin in sync)
  shrines-monoliths  49a44506ef44b44f88a99b5cee630859a3b73926 (origin in sync)
  blacksmithing      9acfb7e253580f1fafc9892f107a270d2d748653 (origin in sync)
  banners-dyetub     5debcc11e5ecdd58a8a6302885197f84ac7f6ee4 (LOCAL ONLY)
Commit created: No
Commit hash: Not applicable
Commit parents: Not applicable
Files changed: NETWORK_COMPATIBILITY_MATRIX.md, PATCH_18_INTEGRATION_LOG.md (both untracked tracking documents; not committed — no Milestone 0 documentation commit has been authorized)
Network symbols added: None (audit only)
Network symbols removed: None (audit only)
Network symbols migrated: None (audit only)
Build commands: Identified, not run. Authoritative entry points (Gradle 8.9 wrapper, NeoGradle userdev 7.0.165):
  .\gradlew.bat build          (compile + jar + processResources)
  .\gradlew.bat compileJava
  .\gradlew.bat runClient      (dev client, run/)
  .\gradlew.bat runServer      (dev dedicated server, run/server, --nogui)
  .\gradlew.bat test           (JUnit 5 — only meaningful on branches that add test infra; patch-18 has no src/test and no test dependencies)
  .\gradlew.bat runGameTestServer (banking branch only — adds gameTestServer run config)
Test results: Not run (Milestone 0 is static audit only; patch-18 baseline has no test source set)
Runtime validation: Not run (not required for Milestone 0)
Compatibility matrix updates: Fully populated initial matrix — branch summary with verified HEADs/merge bases, file-level network touchpoint inventory for all 5 branches, shared-file conflict hotspots, registration facts (single PayloadRegistrar, event.registrar("1"), 48 registrations on patch-18), protocol decision table, findings NET-001..NET-008
Pre-existing failures: None observed (no build or test executed; nothing to report yet — baseline build health is Milestone 1 scope)
New failures: None
Unresolved risks:
  1. banners-dyetub exists only locally (43 commits, no origin ref) — single point of failure until pushed (NET-006)
  2. Milestone 0 ran in the primary working copy; a dedicated clone/worktree is required before Milestone 1 merge work
  3. Working tree contains untracked files: the 4 project documents plus logs/ (runtime logs; Farming has a commit "stop tracking runtime logs") — none interfere with tracked state; do not clean without authorization
  4. banking's bank item envelope v3 depends on an external Rails backend milestone; dev-only override in build.gradle (NET-008)
  5. mod_version divergence: Farming 0.1.8 vs 0.1.7k everywhere else (NET-007)
Assumptions verified:
  - Exact release branch name is patch-18 (not Patch-18/patch_18)
  - Actual branch name is shrines-monoliths (plural), not shrines-monolith as in the prompt
  - Farming capitalization (uppercase F) confirmed
  - Minecraft 1.21.1, NeoForge 21.1.72, Java 21 toolchain, GeckoLib 4.6.6, Gradle 8.9, Parchment 2024.07.28 — all confirmed from gradle.properties / build.gradle / gradle-wrapper.properties on patch-18
  - All five feature branches exist and share merge base 62df1dc9 (current patch-18 HEAD); pairwise independent
  - Patch 18 network baseline is NeoForge PayloadRegistrar (RegisterPayloadHandlersEvent, registrar version "1") in network/NetworkHandler.java + network/ClientNetworkHandler.java, 48 play registrations
  - banking restructures the registrar (55 registrations), deletes 8 legacy payload/client classes, adds bank/quest-action/service-NPC payload families, worldstate Rails sync, versioned BankItemCodec
  - Farming and blacksmithing each change the wire format of an existing payload (skill_sync; craft_blacksmith_item) — the owner-stated migration requirement is confirmed by repository evidence
  - shrines-monoliths has no custom payloads or packet sends (vanilla BE sync only)
  - banners-dyetub adds 6 custom payloads and edits the registrar — it is NOT network-neutral
Assumptions still open:
  - Whether banking's canonical contract requires changes to payloads it does not itself touch (Milestone 2)
  - Whether any payload identifier collides across branches (Milestone 2/4 symbol-level audit)
  - Baseline build/test health of patch-18 (Milestone 1)
  - Final mod_version for the integrated Patch 18 line (owner decision)
  - Rails backend version the production Patch 18 build may assume (owner decision, Milestone 2)
Prohibited actions confirmed not performed: No merge, no rebase, no push, no force-push, no tag, no release, no deploy, no commit, no checkout/branch switch, no reset/clean/stash, no gameplay source modification, no feature-branch modification. Only the two tracking documents were edited; git fetch origin (read-only ref update) was the sole remote interaction.
Recommended next milestone: Milestone 1 — Patch 18 baseline validation and integration branch creation (prerequisites: owner decision on where the dedicated clone/worktree lives; strongly recommend pushing banners-dyetub to origin first)
Owner decision: Pending
```

## Milestone 1: Patch 18 baseline validation and integration branch creation

```text
Date: 2026-08-08
Status: Complete
Working directory: C:\projects\britannia\mod\Britannia_Mod-integration (dedicated Git worktree)
Active branch: patch-18-network-integration
Patch 18 reference: patch-18
Starting HEAD: 62df1dc97c5113a86f9c0f258cb90538f31efe89
Ending HEAD: 62df1dc97c5113a86f9c0f258cb90538f31efe89 (no commits created)
Feature branch HEADs consulted: None required this milestone (no merges); all five HEADs unchanged from Milestone 0
Commit created: No
Commit hash: Not applicable
Commit parents: Not applicable
Files changed: PATCH_18_INTEGRATION_LOG.md, NETWORK_COMPATIBILITY_MATRIX.md (tracking documents in primary copy, not committed).
  In the worktree, only untracked runtime files were created: run/server/eula.txt + server.properties (copied from the
  owner's existing dev-server config in the primary copy, propagating the owner's previously recorded EULA acceptance),
  build outputs, and a freshly generated smoke-test world at run/saves/sandbox. No tracked file modified.
Network symbols added: None
Network symbols removed: None
Network symbols migrated: None
Build commands:
  1. .\gradlew.bat build                      -> BUILD SUCCESSFUL in 2m 59s (33 tasks; :compileJava FROM-CACHE from shared local build cache; :test NO-SOURCE)
  2. .\gradlew.bat clean build                -> BUILD FAILED in 52s at :neoFormPatch ("Failed to execute stage" in NeoGradle neoForm pipeline)
  3. .\gradlew.bat build (retry)              -> BUILD SUCCESSFUL in 21s, exit code 0 verified. The :neoFormPatch failure is a transient
                                                 NeoGradle post-clean cache flake that self-recovers on re-run; it is toolchain behaviour,
                                                 not a mod source failure. Recorded under pre-existing failures.
  Artifacts: build/libs/Britannia_Mod-integration-0.1.7k.jar and -all.jar. Note: the artifact name follows the worktree
  directory name because settings.gradle sets no rootProject.name (builds from the primary copy produce Britannia_Mod-*.jar).
  Cosmetic in dev; recorded in case any packaging script matches on the jar name.
Test results: :test NO-SOURCE — the patch-18 baseline has no test source set or test dependencies. Nothing to run; not a failure.
Runtime validation: Dedicated-server startup smoke test PASSED.
  - .\gradlew.bat runServer                          -> BUILD FAILED in 1s: Gradle configuration-cache deserialization error on the
                                                       :runServer JavaExec classpath ("null array"). Pre-existing incompatibility between
                                                       NeoGradle run tasks and org.gradle.configuration-cache=true (set in gradle.properties).
  - .\gradlew.bat runServer --no-configuration-cache -> Server started: Britannia 0.1.7k + NeoForge 21.1.72 on MC 1.21.1, fresh world
                                                       generated at run/saves/sandbox (inside worktree), "Done (4.024s)!", listening on
                                                       127.0.0.1:25565, gametest namespace britannia_mod enabled, mod's RailsUpdateServer
                                                       HTTP service started on port 8081.
  - Log profile: 1 ERROR, 404 WARN lines. The ERROR and notable mod warnings are recorded under pre-existing failures below.
  - Shutdown: server process (PID verified to belong to the worktree by command line) stopped; port 25565 released. No crash, no
    registration mismatch, no payload error during startup.
Compatibility matrix updates: Added baseline-validation note and pre-existing finding NET-009 (client-only mixin probed during
  dedicated-server load).
Pre-existing failures (baseline, NOT caused by integration):
  1. :neoFormPatch fails on the first build after `gradlew clean` (NeoGradle cache flake); succeeds on immediate retry.
  2. `gradlew runServer` (and likely runClient) fails under the configuration cache enabled in gradle.properties;
     requires --no-configuration-cache. 
  3. RuntimeDistCleaner ERROR at server boot: TitleScreenBackgroundMixin (britannia_mod.mixins.json) targets client-only
     net.minecraft.client.gui.screens.TitleScreen and is probed during DEDICATED_SERVER load; mixin apply is skipped and the
     server continues. Pre-existing dedicated-server class-loading hazard of exactly the kind the network contract audits for;
     must not be made worse by integration (three branches modify britannia_mod.mixins.json).
  4. Fresh-runtime warnings: ModConfig "Could not load configuration file, using defaults" + FileNotFoundException for
     config\britannia_mod.properties; ChestHandler "No barrel found at BlockPos{x=5213, y=66, z=8912}" (the #400 refill barrel
     does not exist in a freshly generated world); vanilla command-ambiguity and refmap warnings.
New failures: None.
Unresolved risks: banners-dyetub still local-only (NET-006); mod_version divergence (NET-007); Rails backend contract (NET-008).
Assumptions verified: Worktree isolation (fresh world generated inside worktree, primary sandbox world untouched);
  baseline compiles and boots a dedicated server; test entry point exists but has no sources on baseline;
  EULA already accepted by owner in primary dev config (run/server/eula.txt, eula=true) — reused, not newly accepted.
Assumptions still open: runClient smoke (not required for this milestone); baseline behaviour with a Rails backend reachable
  (WorldStateSync/Rails interactions were not exercised — no backend contacted during smoke).
Prohibited actions confirmed not performed: No merge, no rebase, no push, no tag, no release, no deploy, no commit, no change
  to any tracked file, no feature-branch modification, no primary-working-copy modification (beyond the two tracking documents),
  no discard/reset/clean of unrelated work. The only state-changing operations were: worktree + branch creation (required by this
  milestone), builds, the smoke-test runtime files listed above, and stopping the smoke-test server process I started.
Recommended next milestone: Milestone 2 — Banking network architecture audit (read-only; no merge).
Owner decision: Pending
```

## Milestone 2: Banking network architecture audit

```text
Date: 2026-08-08
Status: Complete
Working directory: C:\projects\britannia\mod\Britannia_Mod (primary copy, read-only git inspection; worktree untouched)
Active branch: patch-18 (primary) / patch-18-network-integration (worktree, unchanged at 62df1dc9)
Patch 18 reference: patch-18
Starting HEAD: 62df1dc97c5113a86f9c0f258cb90538f31efe89
Ending HEAD: 62df1dc97c5113a86f9c0f258cb90538f31efe89 (unchanged; audit only)
Feature branch HEADs consulted: banking aa39914a (primary subject); Farming 1753f7cd (deleted-symbol cross-reference)
Commit created: No
Commit hash: Not applicable
Commit parents: Not applicable
Files changed: NETWORK_COMPATIBILITY_MATRIX.md, PATCH_18_INTEGRATION_LOG.md (tracking docs only, not committed)
Network symbols added: None (audit only)
Network symbols removed: None (audit only)
Network symbols migrated: None (audit only)
Build commands: None run (static analysis milestone)
Test results: Not run (banking's 98 unit-test files + 28 gametest files execute at M3)
Runtime validation: Not run (M3 scope)
Compatibility matrix updates: New "Canonical network contract" section (12 verified contract points); registration audit table
  completed for all 14 new + 5 removed payloads; protocol/migration decision table resolved for banking (5 decided, 2 open:
  Rails envelope version = owner call NET-008, skill sync = M5); banking status -> "Audited, not merged"; NET-001 resolved
  for audit; new finding NET-010 (Farming ships shard secrets to clients; banking's model deletes that -- M5 must delete, not port).
Pre-existing failures: None newly observed (no builds run)
New failures: None
Unresolved risks: See merge-risk report in the M2 milestone report (chat) and matrix findings NET-006..NET-010
Assumptions verified:
  - banking vs merge base: 487 files, +75,415/−3,297; areas: service/ 136, client/ 34, network/ 36, bank/ 18, worldstate/ 10,
    server/ 10 (new server/auth + server/http), gametest/ 28, tests 82, resources 11
  - Registration delta: 5 removed, 14 added, 46->55 TYPE registrations (extraction-based); no duplicate registrations
  - All 60 payload IDs on banking are distinct; namespace britannia_mod (+1 legacy britannia:skill_sync)
  - Serverbound trust model and 14-fact menu validator verified from source (not assumed from docs)
  - Bank ledger authority = external Rails; local SavedData receipts + startup reconciliation for crash safety
  - Client token/secret distribution deleted in favour of server-only HMAC auth (server/auth/*)
  - Startup tolerates an unreachable Rails backend (verified in WorldBootstrapAPI source + outage gametests exist)
  - BritanniaMod lifecycle additions: WorldStateSyncPoller, ServiceNpcSpawnDeliveryProcessor, BankTransferReconciliationService,
    ServerAuthRegistry, MenuRegistry (mod bus), BlessedItemSyncHandler.init, WorldBootstrapHandler.init
  - mixins.json: adds PlayerListInvokerMixin + PlayerListAccessorMixin (common-safe targets; no new client-only mixin)
  - .gitignore: banking also ignores logs/ + *.log (same as Farming -- trivial union at M5)
  - banking adds two large design docs at repo root (ultimacraft_codex_milestone_playbook.md,
    ultimacraft_cohesive_banking_service_npc_spawn_design_v2.md) -- will land in root on merge; flagged to owner
Assumptions still open:
  - Whether production Rails backend has the "Milestone 16" envelope-v3 contract (NET-008; owner decision; compiled default v1 is safe)
  - Runtime behaviour under real client join (M3 Tier 4)
  - ServiceNpcSpawnPayloadCodec/menu flow runtime correctness (covered by unit tests + gametests at M3)
Prohibited actions confirmed not performed: No merge, no rebase, no push, no tag, no release, no deploy, no commit, no checkout,
  no source modification, no feature-branch modification, no reset/clean/stash. Read-only git commands plus tracking-document edits.
Recommended next milestone: Milestone 3 — Merge banking and establish the canonical network baseline (in the worktree,
  git merge --no-ff --no-commit banking; full validation before the single merge commit)
Owner decision: Pending
```

## Owner-directed merge exclusions (standing policy, applies to every milestone)

Directed by the project owner on 2026-08-08 (Milestone 3 approval):

1. Project-related `.md` files are NOT merged into the integration branch; they stay on their respective feature branches. Applied per merge by enumerating added/modified `.md` files in the branch diff and `git rm`-ing the project-documentation ones from the staged merge before committing.
2. No log files and no `logs/` folder may be merged, ever.

Application record:

| Milestone | Branch | Excluded |
|---|---|---|
| 3 | `banking` | 44 added `.md` files (29 root `UltimaCraft_*`, 2 root `ultimacraft_*`, 11 `docs/*`, 2 farming/flower design docs riding on the branch). No tracked log files existed. banking's `.gitignore` additions (`logs/`, `*.log`) were KEPT — they enforce the policy going forward. |
| 5 | `Farming` | 12 added `.md` files (FARMING_*, FLOWER_*, UltimaCraft_Flower_System_Codex_Milestone_Playbook.md). No tracked log files existed; Farming's `/logs/` .gitignore line KEPT (unions with banking's). Six doc-reconciliation tests were given assumption-skips (with the policy cited in-code) because their subject documents are excluded: FarmingSkillProgressionCloseoutTest (1), FarmingSkillRequirementTest (3 via its two doc-reading helpers), FlowerAssetContractTest (2). They still run fully on the Farming feature branch where the documents exist. |
| 6 | `blacksmithing` | 2 added `.md` files (docs/blacksmithing-audit.md, docs/blacksmithing-implementation.md) and **2 tracked runtime log files** (logs/debug.log, logs/latest.log — the first branch found actually tracking logs; excluded per policy). |
| 7 | `shrines-monoliths` | 9 added `.md` files (2 root UltimaCraft_Shrine_and_Monolith_*, 7 docs/shrines-monoliths/*). `docs/shrines-monoliths/CONTENT_REPORT.json` KEPT — it is a data file consumed by MilestoneEightContentReportTest, not a project .md. No tracked log files existed. |
| 8 | `banners-dyetub` | 65 added `.md` files (gate-E review tree under content/banner-final-intake/, docs/banner-dyeing/*, content/banner_catalogue_status.md). Data files KEPT: content/banner_catalogue.yml, submission textures/geometry/yml, tools/scaffold sources. NOTE for owner: `content/banner_catalogue_status.md` is tool-generated data in .md form; its two consumer tests now skip under the policy — say the word if it should return. 14 new policy skips added (total 20). No tracked log files existed. |

## Milestone 3: Merge banking and establish canonical network baseline

```text
Date: 2026-08-08
Status: Complete
Working directory: C:\projects\britannia\mod\Britannia_Mod-integration (dedicated worktree)
Active branch: patch-18-network-integration
Patch 18 reference: patch-18 (untouched)
Starting HEAD: 62df1dc97c5113a86f9c0f258cb90538f31efe89
Ending HEAD: 9f7e800c97db56c2b1073babdddf0e612314c51a
Feature branch HEADs consulted: banking aa39914a2ef3caf5e1f137a657169264ae2c31cf (merge source, unmodified)
Commit created: Yes — one validated merge commit
Commit hash: 9f7e800c97db56c2b1073babdddf0e612314c51a
Commit parents: 62df1dc97c5113a86f9c0f258cb90538f31efe89, aa39914a2ef3caf5e1f137a657169264ae2c31cf
Files changed: 443 (banking's 487-file diff minus the 44 owner-excluded .md documents; staged set verified byte-identical
  to expected set before commit)
Network symbols added: 14 payload registrations (9 bank_*, quest_action_request/result, 3 service_npc_spawn_*), MenuRegistry,
  ServiceNpcSpawnMenu, ServiceNpcSpawnPayloadCodec/Handler + validators, BankingTransferPacketService + service layer,
  server/auth/* (ServerAuthRegistry, ServerCredentials, RequestSignature, RailsRequestAuthenticator),
  server/http/RailsApiUrlResolver, worldstate/WorldStateSync{Poller,Apply,Validator,Outcome}, bank/item/BankItemCodec,
  bank/transfer receipt SavedData, BankChequeData component
Network symbols removed: ClaimQuestRewardC2SPayload, GrantCoinsC2SPayload, ClientboundSyncCityTokenPayload,
  SpawnEscortC2SPayload, ServerboundQuestAcceptedPayload, RailsApi, RailsCatalog, WineryNetworkClient, network/.old files
Network symbols migrated: None this milestone (banking IS the canonical target; other branches migrate in M5+)
Build commands: gradlew build -> BUILD SUCCESSFUL 1m45s (compileTestJava executed; one deprecation note in banking's own
  BoundedHttpTest); gradlew runGameTestServer --no-configuration-cache -> BUILD SUCCESSFUL 28s
Test results: Unit: 658 tests, 0 failures, 0 errors, 0 skipped (80 JUnit classes). Gametests: "All 333 required tests passed".
Runtime validation: Dedicated server (merged tree): Done (2.911s), loaded existing M1 world. Client: launched with
  session-only init-script quickplay (--quickPlayMultiplayer 127.0.0.1:25565; no repo file touched), joined successfully
  ("Dev joined the game", entity id 5), connection stable, ZERO payload/registration/decode/channel errors on either side.
  Absent Rails backend tolerated: blessed-item/skill fetch errors logged, no crash, no disconnect. Client kill ->
  server logged graceful "lost connection/left the game". Server stopped cleanly; working tree clean after runtime tests.
Compatibility matrix updates: banking row -> "Merged, validation incomplete" (single-client runtime validated; reconnect,
  restart-persistence, and two-player validation intentionally deferred to M10); validation evidence row updated;
  NET-001 closed for merge.
Pre-existing failures: Reconfirmed PRE-003 (TitleScreen mixin ERROR at server boot) and PRE-004 (fresh-runtime config
  noise) on the merged tree — unchanged by the merge. New observations, all pre-existing baseline asset issues seen on
  the client: invalid pack paths ("candlefire - Copy.png", models/block/world/Old/*), GeckoLib "Unable to parse animation:
  animation.model.attack/attack2" (broken molang; source of the gson stack trace). Absent-backend fetch errors
  (BlessedItemSyncAPI, SkillManager skill config/skills) are expected without Rails and handled.
New failures: None.
Unresolved risks: NET-006 (banners-dyetub local-only), NET-007 (mod_version), NET-008 (Rails envelope version, owner),
  NET-010 (Farming client-secret sync — M5 deletion), PRE-002 (run tasks need --no-configuration-cache).
Assumptions verified: Merge was conflict-free as predicted (merge base = starting HEAD); staged set = banking diff minus
  exclusions, verified identical; no duplicate registrations; no obsolete class references anywhere in src/main.
Assumptions still open: Reconnect/restart persistence and multiplayer behaviour (M10); production Rails contract (NET-008).
Prohibited actions confirmed not performed: No push, no rebase, no tag, no release, no deploy, no commit to patch-18 or any
  feature branch, no modification of feature branches, no discard of unrelated work. Exactly one commit was created, on
  patch-18-network-integration only, after all validation gates passed.
Recommended next milestone: Milestone 4 — post-banking repository-wide compatibility inventory (read-only; no merge)
Owner decision: Pending
```

## Milestone 4: Post-banking repository-wide compatibility inventory

```text
Date: 2026-08-08
Status: Complete
Working directory: C:\projects\britannia\mod\Britannia_Mod-integration (read-only git analysis; no file changes in worktree)
Active branch: patch-18-network-integration (unchanged at 9f7e800c)
Patch 18 reference: patch-18
Starting HEAD: 9f7e800c97db56c2b1073babdddf0e612314c51a
Ending HEAD: 9f7e800c97db56c2b1073babdddf0e612314c51a (unchanged; audit only)
Feature branch HEADs consulted: Farming 1753f7cd, blacksmithing 9acfb7e2, shrines-monoliths 49a44506, banners-dyetub 5debcc11
  (all unchanged since M0; all read-only)
Commit created: No
Commit hash: Not applicable
Commit parents: Not applicable
Files changed: NETWORK_COMPATIBILITY_MATRIX.md, PATCH_18_INTEGRATION_LOG.md (tracking docs only, not committed)
Network symbols added/removed/migrated: None (audit only)
Build commands: None run (git merge-tree dry runs only — no working-tree mutation)
Test results: Not applicable
Runtime validation: Not applicable
Compatibility matrix updates: New M4 section with measured merge-tree conflict predictions per branch, full touchpoint
  classification (compatible / obsolete / conflicting / requiring-runtime-verification per playbook categories), and
  branch-specific validation scenarios for M5-M8; shrines-monoliths and banners-dyetub rows -> "Audited, not merged".
Key findings:
  1. Measured conflicts are far smaller than M0 file-overlap estimates: Farming 4 files, blacksmithing 1 (en_us.json only!),
     shrines-monoliths 3, banners-dyetub 6.
  2. Farming's SkillManager and blacksmithing's NetworkHandler AUTO-MERGE — flagged "requiring runtime verification":
     auto-merged is not semantically verified; compile + review at M5/M6 respectively.
  3. Farming's obsolete touchpoint confirmed: client token sync (apiToken+shardSecret to clients) must be deleted at M5,
     resolved inside the conflicted WorldBootstrapAPI/WorldBootstrapHandler/ModConfig trio (NET-010).
  4. blacksmithing's payload schema matches the canonical bounded-encoding contract (writeUtf 128/64 caps, enum action).
  5. shrines-monoliths re-confirmed network-neutral at symbol level.
  6. banners-dyetub needs no API migration — only conflict re-application; minor idiom divergence (raw connection.send
     in DyePreviewRuntime vs canonical PacketDistributor) noted for optional M9 normalization.
  7. Farming BE sync surface: FarmingBlockEntity, FlowerBlockEntity, OrangeTreeRootBlockEntity use vanilla update
     tag/packet; CommunityFarmBlockEntity and WeightedWoodBlockEntity are server-only. No new menus in Farming.
Pre-existing failures: None newly observed (no builds run)
New failures: None
Unresolved risks: NET-006 (banners-dyetub STILL local-only — third reminder), NET-007, NET-008, NET-010, PRE-002
Assumptions verified: All four branch HEADs unchanged since M0; merge-tree dry runs used real 3-way merge machinery
  (same engine as git merge), so conflict predictions are exact for the current HEADs
Assumptions still open: Semantic correctness of the two auto-merged files (M5/M6 compile+review); runtime scenarios per branch
Prohibited actions confirmed not performed: No merge, no commit, no push, no checkout, no source change, no feature-branch
  modification. merge-tree writes only loose tree objects into the object database (standard, side-effect-free for refs
  and working tree).
Recommended next milestone: Milestone 5 — Merge Farming and migrate its network usage
Owner decision: Pending
```

## Milestone 5: Merge Farming and migrate its network usage

```text
Date: 2026-08-08
Status: Complete
Working directory: C:\projects\britannia\mod\Britannia_Mod-integration (dedicated worktree)
Active branch: patch-18-network-integration
Patch 18 reference: patch-18 (untouched)
Starting HEAD: 9f7e800c97db56c2b1073babdddf0e612314c51a
Ending HEAD: 3cae58ed5ab6454eef8e5e17e498291f9db0fc4b
Feature branch HEADs consulted: Farming 1753f7cdffdc919601aaad0334ea3e3a7781c0eb (merge source, unmodified)
Commit created: Yes — one validated merge commit
Commit hash: 3cae58ed5ab6454eef8e5e17e498291f9db0fc4b
Commit parents: 9f7e800c97db56c2b1073babdddf0e612314c51a, 1753f7cdffdc919601aaad0334ea3e3a7781c0eb
Files changed: 2,579 staged (Farming's 2,590-file diff minus 12 excluded .md docs, one rename fold, plus merge-resolution
  edits). The four predicted conflicts were the only conflicts.
Network symbols added: SkillSyncPayload versioned wire format (wireVersion=1, SkillDataState, revision,
  identificationBypass, MAX_SKILLS=512 bound) — same payload id britannia:skill_sync, registered once (clientbound), now
  carrying Farming's versioned snapshot; WorldBootstrapData record extended with shard/httpStatus/status diagnostics.
Network symbols removed: Farming's client token sync call (ClientboundSyncCityTokenPayload.send with apiToken+shardSecret)
  — DELETED per NET-010; the payload class itself was already deleted by banking and stays deleted. Farming's legacy
  bearer-token HTTP path (ModConfig.API_BASE_URL + CityAPITokenData) not ported; banking's server-only auth is the sole path.
Network symbols migrated: World bootstrap fetch: Farming's tolerant fish/regions(+climate)/grapes parsing now runs inside
  banking's authenticated bounded-HTTP pipeline; HTTP 304 -> not_modified cache retention; RegionCache diagnostics
  (retainExisting on failure, 4-arg update on success) wired into banking's Coordinator; SkillManager send path now guards
  with connection.hasChannel(SkillSyncPayload.TYPE).
Build commands: gradlew build (x3 during iteration; final SUCCESS 19s, exit 0 verified);
  gradlew runGameTestServer --no-configuration-cache (x2; final: all pass)
Test results: 766 unit tests, 0 failures, 0 errors, 6 skipped (all six are doc-reconciliation tests skipping under the
  owner exclusion policy, each with an in-code justification). Gametests: "All 333 required tests passed".
  Iteration history recorded honestly: first run 16 unit failures (bank asset/lang tests broken by Farming's test
  workingDir relocation; doc tests reading excluded .md files); second run 5 failures (remaining doc readers);
  first gametest run 179 failures (skill_sync sends to channel-less mock players) -> hasChannel guard -> all pass.
Runtime validation: Dedicated server Done (1.119s) on existing world, only the known pre-existing ERROR (PRE-003).
  Client quickplay-joined, stable, ZERO payload/decode/channel errors, ZERO skill-sync send refusals (real client
  negotiates the channel and receives the versioned payload). Both processes stopped cleanly; worktree clean.
Compatibility matrix updates: Farming -> "Merged, validation incomplete"; NET-002 and NET-010 resolved; skill_sync
  registration row updated.
Pre-existing failures: PRE-003 reconfirmed. Expected absent-Rails fetch errors (blessed items, skill config) — handled.
New failures: None remaining. The three integration-introduced regressions found during validation (bank test paths,
  doc-test file reads, mock-player skill sync) were all fixed within this milestone and are part of the merge commit.
Unresolved risks: NET-006 (banners-dyetub STILL local-only), NET-007 (mod_version now 0.1.8 on the integration branch —
  owner may override at M11), NET-008 (Rails envelope), PRE-002.
Assumptions verified: The four predicted conflicts were exactly the conflicts; SkillManager auto-merge semantically
  correct (banking HTTP + Farming payload, verified by compile, review, and runtime); farming BE sync surface unchanged
  (vanilla); no duplicate registrations; no obsolete network references anywhere in src/main.
Assumptions still open: Farming gameplay scenarios (plant/harvest/flower colour, per-crop archetypes) need in-game and
  Rails-backed validation — deferred to M10 cross-feature regression per plan.
Prohibited actions confirmed not performed: No push, rebase, tag, release, deploy; no commits to patch-18 or feature
  branches; feature branches unmodified; nothing discarded. One merge commit, created only after all gates passed.
Recommended next milestone: Milestone 6 — Merge blacksmithing and migrate its network usage
Owner decision: Pending
```

## Milestone 6: Merge blacksmithing and migrate its network usage

```text
Date: 2026-08-08
Status: Complete
Working directory: C:\projects\britannia\mod\Britannia_Mod-integration (dedicated worktree)
Active branch: patch-18-network-integration
Patch 18 reference: patch-18 (untouched)
Starting HEAD: 3cae58ed5ab6454eef8e5e17e498291f9db0fc4b
Ending HEAD: 882cc0e3031dfa490bbcadc670597756590cc71a
Feature branch HEADs consulted: blacksmithing 9acfb7e253580f1fafc9892f107a270d2d748653 (merge source, unmodified)
Commit created: Yes — one validated merge commit
Commit hash: 882cc0e3031dfa490bbcadc670597756590cc71a
Commit parents: 3cae58ed5ab6454eef8e5e17e498291f9db0fc4b, 9acfb7e253580f1fafc9892f107a270d2d748653
Files changed: blacksmithing's 269-file diff minus 4 exclusions (2 docs, 2 tracked log files). Only conflict: en_us.json,
  resolved by key-level 3-way merge (857 + 265 new keys = 1,122, zero key conflicts, JSON validated, BOM preserved).
Network symbols added: None (no new payload types; registrar count stays 55)
Network symbols removed: None
Network symbols migrated: CraftBlacksmithItemC2SPayload schema extended (action/targetToken<=128/sessionToken<=64,
  bounded writeUtf) with server-side BlacksmithSessionManager gate (per-player UUID token, 60s expiry, one-shot
  invalidation); OpenBlacksmithGuiS2CPayload carries learnedRecipes; registrar auto-merge onto banking's restructured
  NetworkHandler manually reviewed — handler body intact at the merged location; ClientNetworkHandler auto-merge compiled
  and exercised via GUI-open path in smoke test.
Build commands: gradlew build -> SUCCESS 2m43s exit 0; gradlew runGameTestServer --no-configuration-cache -> all pass
Test results: 771 unit tests, 0 failures, 0 errors, 6 policy skips. Gametests: "All 333 required tests passed".
Runtime validation: Dedicated server Done (0.991s); client quickplay-joined (~29s), zero payload/decode/channel errors,
  zero unexpected server errors, stable connection; clean shutdown; worktree clean.
Compatibility matrix updates: blacksmithing -> "Merged, validation incomplete"; NET-003 resolved; validation row filled.
Pre-existing failures: PRE-003 reconfirmed; absent-Rails fetch errors expected and handled.
New failures: None.
Unresolved risks: NET-007 (mod_version 0.1.8), NET-008 (Rails envelope), NET-011 (accepted skips), PRE-002.
  NET-006 resolved this session (owner authorized push; origin/banners-dyetub created at 5debcc11).
Assumptions verified: Only predicted conflict occurred; auto-merged registrar semantically correct (reviewed + compiled +
  runtime); session validation is server-owned and expiring; no new registrations.
Assumptions still open: In-game craft/repair/smelt scenarios incl. expired-session rejection and rapid-duplicate
  submission — deferred to M10 (needs interactive gameplay/Rails).
Prohibited actions confirmed not performed: No push (except the owner-directed banners-dyetub publish, recorded under
  Owner approvals), no rebase/tag/release/deploy, no commits to patch-18 or feature branches, feature branches
  unmodified. One merge commit after all gates passed.
Recommended next milestone: Milestone 7 — Merge shrines-monoliths and verify compatibility
Owner decision: Pending
```

## Milestone 7: Merge shrines-monoliths and verify compatibility

```text
Date: 2026-08-08
Status: Complete
Working directory: C:\projects\britannia\mod\Britannia_Mod-integration (dedicated worktree)
Active branch: patch-18-network-integration
Patch 18 reference: patch-18 (untouched)
Starting HEAD: 882cc0e3031dfa490bbcadc670597756590cc71a
Ending HEAD: 9b564af9414f1339818c216d2c4bd492b0c7d34c
Feature branch HEADs consulted: shrines-monoliths 49a44506ef44b44f88a99b5cee630859a3b73926 (merge source, unmodified)
Commit created: Yes — one validated merge commit
Commit hash: 9b564af9414f1339818c216d2c4bd492b0c7d34c
Commit parents: 882cc0e3031dfa490bbcadc670597756590cc71a, 49a44506ef44b44f88a99b5cee630859a3b73926
Files changed: shrines' 124-file diff minus 9 excluded .md docs, plus resolution/repair edits and a new .gitattributes.
Network symbols added: None (no payloads; registrar stays 55). Vanilla BE sync via LargeStructureAnchorBlockEntity
  (getUpdateTag/getUpdatePacket) as audited. New data components SHRINE/MONOLITH_INSTANCE_STATE (persistent +
  networkSynchronized codecs).
Network symbols removed: None
Network symbols migrated: None required (branch confirmed network-neutral at M0/M4; re-confirmed post-merge)
Build commands: gradlew build (final SUCCESS 25s); gradlew runGameTestServer --no-configuration-cache (all pass)
Test results: 984 unit tests, 0 failures, 0 errors, 6 policy skips. Gametests: "All 333 required tests passed".
  Iteration history: first run 44 failures (shrines tests resolve src/... relative to the test workingDir that the
  integration's build config relocates to build/test-run) -> 22 -> 9 via britannia.projectDir path rooting (11 files,
  then multi-line Path.of sites, then a read() helper); final 3 were the line-ending contract defect below.
Runtime validation: Dedicated server Done (1.200s); client quickplay-joined (~15s later), zero payload/decode/channel
  errors, zero unexpected server errors, stable; clean shutdown; worktree clean.
Line-ending contract repair (pre-existing shrines-monoliths defect, surfaced by any fresh checkout with autocrlf):
  The branch pins SHA-256 hashes over text assets, recorded from the dev working tree per file — internally inconsistent:
  geo pins match LF bytes; animation pins match CRLF bytes; models/item split; committed CONTENT_REPORT.json records CRLF
  animation hashes that its own reconstruction (from LF-checked-out files) can never reproduce. Verified evidence:
  staged/branch blobs are byte-identical; only checkout representation varies. Repair: new .gitattributes pinning each
  hashed asset's eol per its recorded pin (geo/* + monolith model + the two animation files = lf; shrine_missing.geo.json
  + models/item/shrine.json = crlf per their pins); 2 test pin constants and 9 CONTENT_REPORT.json entries updated from
  the CRLF hash to the git-canonical LF hash (asset content unchanged); working files normalized. Documented for M9/M11.
Compatibility matrix updates: shrines-monoliths -> "Merged, validation incomplete"; NET-004 resolved; NET-012 added
  (line-ending pin repair record).
Pre-existing failures: PRE-003 reconfirmed. New pre-existing finding: the shrines hash-pin/report line-ending
  inconsistency (repaired this milestone, recorded as NET-012).
New failures: None remaining; all integration-found issues fixed within the milestone before the commit.
Unresolved risks: NET-007, NET-008, NET-011, PRE-002.
Assumptions verified: Network neutrality (post-merge grep: no new payloads/sends); conflicts were mechanical unions
  (one brace restored at a union seam, caught by compile); all hash pins now reproducible from a fresh checkout.
Assumptions still open: Shrine/monolith placement, variant cycling, collision, persistence scenarios in-game (M10).
Prohibited actions confirmed not performed: No push, rebase, tag, release, deploy; no commits to patch-18 or feature
  branches; feature branches unmodified. One merge commit after all gates passed.
Recommended next milestone: Milestone 8 — Merge banners-dyetub and verify compatibility
Owner decision: Pending
```

## Milestone 8: Merge banners-dyetub and verify compatibility

```text
Date: 2026-08-08
Status: Complete
Working directory: C:\projects\britannia\mod\Britannia_Mod-integration (dedicated worktree)
Active branch: patch-18-network-integration
Patch 18 reference: patch-18 (untouched)
Starting HEAD: 9b564af9414f1339818c216d2c4bd492b0c7d34c
Ending HEAD: e4049712a43a41aaf0e2531d548281aec4d2e7c2
Feature branch HEADs consulted: banners-dyetub 5debcc11e5ecdd58a8a6302885197f84ac7f6ee4 (merge source, unmodified; now on origin)
Commit created: Yes — one validated merge commit
Commit hash: e4049712a43a41aaf0e2531d548281aec4d2e7c2
Commit parents: 9b564af9414f1339818c216d2c4bd492b0c7d34c, 5debcc11e5ecdd58a8a6302885197f84ac7f6ee4
Files changed: banners' 922-file diff minus 65 excluded .md docs, plus resolutions/repairs. Nine conflicts (registrar pair,
  BritanniaMod, ClientModSetup, CommandRegistry, DataComponentRegistry, mixins.json, en_us.json, build.gradle) — all unions;
  two union seams repaired (lost ');' in NetworkHandler, lost '.build(); }' in DataComponentRegistry), caught by compile.
Network symbols added: 6 registrations re-applied onto the canonical registrar (dye C2SConfirmDyeApplication,
  C2SCancelDyePreview; S2COpenDyePreview, S2CDyeApplicationResult; banner S2CBannerRenderData,
  S2CBannerPlacementOrientation) -> 61 total; ClientNetworkHandler gains the four dist-gated client handlers;
  DYE_TUB_STATE + BANNER_INSTANCE_STATE data components (6 total). All 60 payload IDs verified unique on the merged tree.
Network symbols removed: None
Network symbols migrated: BannerRenderDataSync send now guarded with connection.hasChannel (display-only OnDatapackSync
  snapshot; gametest mock players negotiate no channels — was 180 gametest failures; real client receipt verified at runtime).
Build commands: gradlew build -> SUCCESS; gradlew runGameTestServer --no-configuration-cache -> all pass
Test results: 1,679 unit tests, 0 failures, 0 errors, 20 skips (6 prior + 14 new owner-policy skips for tests reading the
  65 excluded intake/review documents). Gametests: "All 333 required tests passed".
  Iteration history (full disclosure): 297 -> 46 -> 40 -> 28 -> 18 -> 2 -> 0 unit failures across six fix rounds:
  ~200 test path sites rooted at britannia.projectDir (the recurring workingDir class); line-ending pin repair extended to
  45 banner intake assets (.gitattributes per recorded hash representation, NET-012 pattern); two branch-local guard counts
  updated to integrated-tree truth (data components 3->6, repository items 550->693); 14 policy skips; validator roots fixed.
  Gametests: 180 failures (banner_render_data to mock players) -> hasChannel guard -> all 333 pass.
Runtime validation: Dedicated server Done (2.936s), banner content fully loaded (62/62 decoded, 35 active definitions,
  0 errors). First client attempt raced the server (chained build delayed runServer; quickplay retried via unroutable IPv6)
  — relaunched cleanly: "Dev joined the game", banner render data received by the real client, ZERO payload/decode/channel
  errors, stable; clean shutdown; worktree clean.
Compatibility matrix updates: banners-dyetub -> "Merged, validation incomplete"; NET-005 resolved; PRE-003 RESOLVED
  (banners' mixins.json fix adopted: TitleScreenBackgroundMixin now client-gated).
Pre-existing failures: PRE-003 now FIXED by this merge (verify absence of the RuntimeDistCleaner ERROR at M10).
  Banner intake assets carried the same line-ending pin fragility as shrines (NET-012 pattern) — repaired identically.
New failures: None remaining; all integration-found issues fixed within the milestone before the commit.
Unresolved risks: NET-007 (mod_version 0.1.8), NET-008 (Rails envelope, owner), NET-011 (now 20 accepted skips), PRE-002.
Assumptions verified: 61 registrations = 55 + 6 exactly; payload ID uniqueness re-verified post-merge; banners' payload
  code needed no API migration (M4 assessment held); the two guard-count tests were the only branch-local-truth collisions.
Assumptions still open: Dye preview/application and banner placement gameplay scenarios (M10); two-player banner render
  visibility (M10).
Prohibited actions confirmed not performed: No push, rebase, tag, release, deploy; no commits to patch-18 or feature
  branches; feature branches unmodified. One merge commit after all gates passed.
Recommended next milestone: Milestone 9 — Repository-wide network consolidation audit
Owner decision: Pending
```

## Milestone 9: Repository-wide network consolidation audit

```text
Date: 2026-08-08
Status: Complete
Working directory: C:\projects\britannia\mod\Britannia_Mod-integration (read-only audit; zero source changes)
Active branch: patch-18-network-integration
Patch 18 reference: patch-18 (untouched)
Starting HEAD: e4049712a43a41aaf0e2531d548281aec4d2e7c2
Ending HEAD: e4049712a43a41aaf0e2531d548281aec4d2e7c2 (unchanged)
Feature branch HEADs consulted: None required (all five merged); patch-18 consulted for baseline-status verification of findings
Commit created: No — the audit required no source changes; nothing to commit
Commit hash / parents: Not applicable
Files changed: NETWORK_COMPATIBILITY_MATRIX.md, PATCH_18_INTEGRATION_LOG.md (tracking docs only, not committed)
Network symbols added/removed/migrated: None (audit only)
Build commands: gradlew build -> SUCCESS; gradlew runGameTestServer --no-configuration-cache -> all pass (final green run)
Test results: 1,679 unit tests, 0 failures, 0 errors, 20 accepted policy skips; all 333 gametests pass
Runtime validation: Not required this milestone (M8 smoke stands; M10 covers cross-feature runtime)
Compatibility matrix updates: Full 12-point M9 audit section added; NET-009 CLOSED (owner-approved + evidence: the
  TitleScreen dist ERROR is absent from both M8 server logs, 0 occurrences)
Key audit findings (full detail in the matrix M9 section):
  - One coherent architecture confirmed: canonical play registrar (61 registrations, exactly-once) + baseline
    configuration-phase pair; zero duplicate IDs across 64 payload classes; zero legacy channel APIs; zero obsolete
    symbols; single protocol version "1" everywhere.
  - AUDIT SURPRISE, resolved benign: two additional RegisterPayloadHandlersEvent subscribers exist beyond NetworkHandler —
    ClientModWhitelist (baseline configuration-phase client-mod-audit pair, byte-identical to patch-18) and an EMPTY
    baseline vestige in ClientEventHandler.registerClientPackets. All prior audits had only counted NetworkHandler.
  - All 33 serverbound handlers enqueue to the main thread and validate (inline or via the verified method-ref handlers);
    27/28 clientbound registrations dist-gated, 1 flow-guarded (valid idiom, runtime-verified).
  - Pre-existing dead network code inventoried (report-only, identical on patch-18): CraftBlacksmithItemPayload (never
    registered), OpenQuestScreenS2CPayload (import-only), the empty vestige method, .old files. Post-integration cleanup
    candidates; left untouched per the no-unrelated-changes rule.
Pre-existing failures: None new; PRE-002 (config-cache run-task workaround) remains the only active pre-existing toolchain issue
New failures: None
Unresolved risks: NET-007 (mod_version 0.1.8 — owner decision at M11), NET-008 (Rails envelope version — owner decision),
  NET-011 (20 accepted policy skips, documented)
Assumptions verified: Every M9 acceptance gate: one canonical architecture; no duplicate IDs/registrations; no known
  client-only dedicated-server load hazard (NET-009 closed with log evidence); no unresolved high-risk finding; full suite green
Assumptions still open: Cross-feature runtime and multiplayer behaviour (M10 scope)
Prohibited actions confirmed not performed: No merge, commit, push, rebase, tag, release, deploy, checkout, or source
  modification; read-only analysis plus tracking-document edits
Recommended next milestone: Milestone 10 — Cross-feature runtime and multiplayer regression. NOTE for planning: M10's
  minimum scenarios require interactive gameplay (bank use, farming, smithing, shrines, dye tubs) and a second
  simultaneous client — I can drive server lifecycle, single-client joins, reconnects, restarts, and log inspection
  automatically, but feature interactions and the two-player matrix need the owner at the keyboard (or an owner decision
  to accept a reduced automated scope).
Owner decision: Pending
```

## Milestone 10: Cross-feature runtime and multiplayer regression (automated scope)

```text
Date: 2026-08-08
Status: Complete (automated scope); interactive and two-player scenarios documented as owner-pending below
Working directory: C:\projects\britannia\mod\Britannia_Mod-integration
Active branch: patch-18-network-integration (unchanged at e4049712; zero source changes)
Patch 18 reference: patch-18 (untouched)
Starting HEAD / Ending HEAD: e4049712a43a41aaf0e2531d548281aec4d2e7c2 (unchanged)
Commit created: No (runtime validation only)
Files changed: Tracking docs only. Worktree runtime (gitignored): RCON temporarily enabled for graceful server control,
  disabled again afterwards; M1 smoke world preserved as run/saves/sandbox_m1_smoke_backup; fresh M10 world at
  run/saves/sandbox.
Build commands: None (M9's green build stands)
Test results: Not applicable (unit/gametest suites green at M9)
Runtime validation (all on the final integrated tree, absent Rails backend):
  1. CLEAN-RUNTIME DEDICATED SERVER START: fresh world generated; Done (4.511s); ZERO ERROR lines in the entire boot log
     (baseline M1 had one - the TitleScreen dist error, now verifiably eliminated); banner content 62/62; RCON up.
  2. CLIENT JOIN: quickplay join succeeded; login synchronization completed; the only post-join errors are the expected
     absent-Rails fetch trio (blessed items, skill config, player skills), all handled without crash or disconnect.
  3. RECONNECT: client force-killed; server logged clean "lost connection/left the game"; relaunched client rejoined at
     the SAME coordinates (player data persisted in-session); the only additional error was vanilla netty
     "Exception caught in connection" from the force-killed socket (standard, not mod-related).
  4. GRACEFUL RESTART + PERSISTENCE: RCON save-all ("Saved the game"); RCON stop -> textbook shutdown sequence including
     banking's WorldStateSyncPoller lifecycle stop, "Saving players", "All dimensions are saved"; process exited cleanly.
     Server restarted on the same world (~20s, no regeneration); client rejoined at the EXACT same position
     (23.5, 64.0, 11.5) proving player NBT persistence across restart; world save structure intact (level.dat,
     playerdata/<uuid>.dat + backup, data/neoforge_data_attachments.dat).
  5. LOG FORENSICS across all runs: zero payload/decode/registration/channel errors; zero class-loading failures;
     zero invalid-player or stale-menu errors; NET-009 signature absent everywhere.
Acceptance gates:
  PASS - no disconnect caused by payload mismatch (three separate joins, zero payload errors)
  PASS - no state loss after reconnect or restart (position-level persistence proven)
  PASS - no dedicated-server class-loading failure (zero, including the formerly-failing TitleScreen probe)
  PASS - no observed regression in unrelated functionality within the automated scope
  PASS - all observed issues recorded (the vanilla netty force-kill exception is the only note)
  OWNER-PENDING - cross-player state leakage, per-player scoping, tracking-range/dimension sync (needs 2 players)
  OWNER-PENDING - feature gameplay: bank flows (also needs a Rails backend), farming interactions, blacksmith
    craft/repair/smelt incl. expired-session rejection, shrine/monolith placement and cycling, dye tub sessions and
    banner placement; menu open/close repetition; rapid-action sequences; dimension change
Pre-existing failures: PRE-002 only (run tasks need --no-configuration-cache)
New failures: None
Unresolved risks: NET-007, NET-008, NET-011 (unchanged; owner decisions at M11)
Assumptions verified: System-level runtime health of the integrated whole: lifecycle hooks (worldstate poller,
  bootstrap coordinator) start/stop cleanly with the server; persistence layers write and reload correctly.
Assumptions still open: The owner-pending interactive/multiplayer scenarios above.
Prohibited actions confirmed not performed: No source change, no commit, no push; runtime-only operations in the
  gitignored run directory (RCON toggled on then off; M1 world preserved, not deleted).
Recommended next milestone: Milestone 11 - Final merge-readiness review. The owner may run the interactive/two-player
  scenarios first, or accept the automated M10 scope and proceed.
Owner decision: Pending
```

## Milestone 11: Final merge-readiness review

```text
Date: 2026-08-08
Status: Complete — evidence package below; NO merge into patch-18 performed
Working directory: C:\projects\britannia\mod\Britannia_Mod-integration (clean)
Active branch: patch-18-network-integration
Patch 18 reference: patch-18 (verified still at 62df1dc9, untouched all project)
Starting HEAD: e4049712a43a41aaf0e2531d548281aec4d2e7c2
Ending HEAD: 8ddf9734fceb335f717f12c7863b7ddad541ed0e
Commit created: Yes — ONE corrective commit 8ddf9734: removed FLOWER_SYSTEM_DECISIONS.MD, a Farming project doc whose
  UPPERCASE .MD extension escaped the case-sensitive '*.md' exclusion pathspec at M5. Post-removal sweep is
  case-insensitive: exactly the 2 baseline docs remain (README.md, docs/quest_destroy_manual_test_checklist.md).
  Green re-verified after the commit (build SUCCESS; 1,679 unit tests, 0 failures, 20 policy skips).

COMMIT TRAIL (complete, linear, from baseline 62df1dc9):
  9f7e800c  M3  Merge banking            (parents 62df1dc9 + aa39914a)
  3cae58ed  M5  Merge Farming            (parents 9f7e800c + 1753f7cd)
  882cc0e3  M6  Merge blacksmithing      (parents 3cae58ed + 9acfb7e2)
  9b564af9  M7  Merge shrines-monoliths  (parents 882cc0e3 + 49a44506)
  e4049712  M8  Merge banners-dyetub     (parents 9b564af9 + 5debcc11)
  8ddf9734  M11 Corrective doc removal
  Every feature branch HEAD verified unmodified; patch-18 verified unmoved; the integration branch has never been pushed
  (no origin/patch-18-network-integration).

FINAL DIFF vs 62df1dc9: 4,222 files, +255,162 / −5,960. Composition: resources 2,885; content data 351; tests 231;
  java by feature (service 136, client 86, banner 63, farming 62, dye 55, network 44, bannerdyeing 44, structure 41,
  gametest 28, bank 18, ...); tools 15. Root-level: .gitattributes (hash-pin eol contract), .gitignore (log exclusions),
  build.gradle (test infra union), gradle.properties (mod_version 0.1.8), UltimaCraft_Persistent_Flower_System_Design.docx
  (see decision point 3).

NETWORK MIGRATIONS BY FEATURE (summary):
  banking — canonical architecture established: 14 payloads added, 5 legacy payloads + 3 Rails clients deleted,
    server-only HMAC auth, worldstate sync, SavedData transfer receipts with startup reconciliation.
  Farming — client token/secret sync DELETED (NET-010); versioned skill_sync live with hasChannel guard; bootstrap =
    banking auth + Farming tolerant parsing (incl. climate) + cache-fallback diagnostics.
  blacksmithing — extended craft payload schema (action/targetToken/sessionToken, bounded) with server-owned expiring
    session validation, re-applied onto the canonical registrar.
  shrines-monoliths — network-neutral (vanilla BE sync + data components); zero registrar impact.
  banners-dyetub — 6 payloads re-applied (61 play registrations total); render-data send hasChannel-guarded; mixins.json
    fix eliminated the baseline TitleScreen dedicated-server class-load error (NET-009 closed).

VALIDATION SUMMARY: per-milestone builds all green; final suite 1,679 unit tests 0 failures (20 documented policy skips,
  NET-011); all 333 gametests pass; M9 12-point consolidation audit passed; M10 automated runtime: clean-runtime boot
  with ZERO errors, join, reconnect, graceful RCON restart, position-level persistence across restart, zero
  payload/decode/class-loading errors anywhere.

PRE-EXISTING (not integration regressions): PRE-001 neoFormPatch post-clean flake (retry succeeds); PRE-002 run tasks
  need --no-configuration-cache; PRE-004 fresh-runtime config-default noise; baseline asset noise (invalid pack paths,
  GeckoLib animation parse); pre-existing dead network code inventoried at M9 (report-only). PRE-003 was FIXED by the
  integration itself.

INTEGRATION REGRESSIONS INTRODUCED AND FIXED (none outstanding): bank-test path breakage (M5), doc-test reads (M5/M8),
  mock-player payload sends (M5: skill_sync, M8: banner_render_data), shrines/banners line-ending pin defects
  (NET-012, .gitattributes contract), two branch-local guard counts updated to integrated truth (M8), union-seam
  syntax breaks caught by compile (M7, M8).

UNRESOLVED LOW-RISK LIMITATIONS: NET-011 (20 policy skips — the skipped tests run on their feature branches);
  owner-pending M10 interactive/two-player scenarios; Rails-backed flows unexercised in this environment.

OWNER DECISION POINTS AT FINAL APPROVAL:
  1. NET-007: integration branch carries mod_version=0.1.8 (from Farming). Confirm or direct a different version string.
  2. NET-008: bank item envelope compiles at v1; dev runs force v3. Confirm production Rails contract before release.
  3. UltimaCraft_Persistent_Flower_System_Design.docx (repo root, from Farming): a design document not covered by the
     letter of the .md exclusion policy — say the word and it gets a one-line removal commit.
  4. content/banner_catalogue_status.md (excluded): tool-generated data in .md form; two consumer tests skip. Restore?
Documentation current: Yes — matrix and log fully updated through M11.
Prohibited actions confirmed not performed: NO push, NO release, NO deployment, NO tag, NO merge into patch-18, no
  feature-branch modification — verified by ref inspection this milestone.

RECOMMENDED FINAL MERGE COMMAND (not executed; requires explicit owner authorization):
  From the primary working copy (C:\projects\britannia\mod\Britannia_Mod, on branch patch-18):
      git merge --no-ff patch-18-network-integration
  patch-18 has not moved since the integration began (62df1dc9 is the merge base of everything), so this merge is
  conflict-free by construction. Optional, recommended first (also needs authorization):
      git push -u origin patch-18-network-integration
  to back up and enable review of the integration branch before the final merge.
Recommended next milestone: None — project complete pending owner review and final merge authorization.
Owner decision: Pending
```

## Post-project addendum: villa integration (2026-08-09)

```text
Date: 2026-08-09
Status: Complete
Authorization: Owner — "Please integrate villa branch in the same way as the other branches" (after discovering
  villa blocks missing in-world; villa was never part of the original five-branch scope)
Working directory: C:\projects\britannia\mod\Britannia_Mod-integration (worktree, established pattern)
Starting HEAD: 6295d544 (patch-18 with the pike fix)  ->  Ending HEAD: 50061f07 (patch-18 fast-forwarded, tree-identical)
Commits created:
  084af544 — Merge branch 'villa' (parents 6295d544 + 3ac93d56): 39 blocks + 41 items, sixth creative tab,
             architectural tag/wall systems; 3 docs excluded (2 UltimaCraft design docs, tools/README.md);
             5 conflicts resolved (.gitignore union, ModConfig whitespace, CreativeTabRegistry integrated-helpers-win,
             thatch_roof.json taken from villa's re-export, en_us.json +39 keys); 2 guard counts updated
             (items 693->735, tabs 5->6)
  50061f07 — Fix server tick crash (independent of villa, found during its validation): CityDataSync NPC lifecycle
             sync methods crashed the ticking server via credentials().orElseThrow() when no Rails credentials are
             configured; upsertLiveNpc / heartbeatLiveNpc / markLiveNpcInactive now degrade to a warning + false,
             matching the class's established absent-backend contract. Triggered by world state (missing/dead
             tracked trader).
Audit: zero network surface; zero id collisions (scan + registry-integrity gametests); merge base = original baseline.
Validation: build SUCCESS; 1,679 unit tests, 0 failures (17 skips); all 337 gametests; world-copy client run:
  12/12 previously-missing villa items resolve, creative tabs rebuild successfully, trader-tick crash path degrades
  to warnings. Owner's world backed up to run/saves/sandbox_backup_pre_villa BEFORE any of this session's runs.
World-data caveat (stated to owner): chunks saved on villa-less builds lost their villa blocks permanently;
  unvisited chunks restore correctly now. Recovery for lost chunks = world backup, not code.
Villa branch: unmodified at 3ac93d56; STILL LOCAL-ONLY (no origin/villa) — push requires owner authorization.
Prohibited actions: no push, no tag, no release; feature branches untouched.
```

## Merge-order changes

No changes. Milestone 0 evidence confirms the default order (banking → Farming → blacksmithing → shrines-monoliths → banners-dyetub):

- All five branches are pairwise independent (every pairwise merge base equals the patch-18 HEAD), so no topological dependency forces a different order.
- banking must land first: it restructures `NetworkHandler`/`ClientNetworkHandler` and deletes legacy payloads that other branches' baselines still contain.
- Farming and blacksmithing carry wire-format changes to existing payloads and (Farming) heavy overlap with banking in `SkillManager`/`WorldBootstrapAPI`/`WorldBootstrapHandler`/`ModConfig` — merging them immediately after banking localizes the migration work.
- shrines-monoliths has the smallest network surface (vanilla BE sync only) and banners-dyetub is additive to the registrar; both can safely follow.

## Pre-existing failures

Recorded during Milestone 1 baseline validation (none are integration-caused):

| ID | Area | Description | Impact | Evidence |
|---|---|---|---|---|
| PRE-001 | Build toolchain | `:neoFormPatch` fails on the first `build` after `gradlew clean` (NeoGradle neoForm pipeline flake); immediate retry succeeds | Clean builds need one retry | M1: clean build FAILED in 52s; retry SUCCESS in 21s |
| PRE-002 | Build toolchain | `gradlew runServer` fails under the configuration cache (`org.gradle.configuration-cache=true` in gradle.properties): "Could not load the value of field `provider` … null array" on the :runServer JavaExec | Run tasks require `--no-configuration-cache` | M1 smoke test |
| PRE-003 | Dedicated-server class loading | RuntimeDistCleaner ERROR: `TitleScreenBackgroundMixin` targets client-only `TitleScreen`, probed during DEDICATED_SERVER load; mixin skipped, server continues | Cosmetic today, but a real client-class-on-server hazard; 3 branches modify britannia_mod.mixins.json — must not worsen | M1 server log (1 ERROR) |
| PRE-004 | Fresh-runtime defaults | Missing `config\britannia_mod.properties` (defaults used, stack trace logged); `ChestHandler` warns no barrel at 5213/66/8912 in a freshly generated world | Expected on any fresh runtime | M1 server log |

## Integration defects

None recorded yet.

## Owner approvals

| Date | Approval |
|---|---|
| 2026-08-08 | Milestone 0 report approved; "Continue Milestone 1" |
| 2026-08-08 | Milestone 1 report approved; "Continue to Milestone 2" |
| 2026-08-08 | Milestone 2 report approved; "Continue to Milestone 3" WITH standing directive: never merge project-related .md files (they stay on feature branches); never merge log files or the logs/ folder; applies to all further milestones |
| 2026-08-08 | Milestone 3 and 4 reports approved in sequence |
| 2026-08-08 | Milestone 5 report approved; owner directed correction of NET-006 first: authorized push of banners-dyetub to origin (executed: origin/banners-dyetub at 5debcc11), then "Continue to Milestone 6" |

## Final merge authorization

**GRANTED by the project owner on 2026-08-08** ("Approve of merge"), together with four decisions:

1. `mod_version=0.1.8` confirmed correct — no change.
2. NET-008 delegated to recommendation. Resolution applied: **no code change**. The compiled default of envelope version 1 is already the safe production value; evidence re-verified at M11 that the v3 override exists only inside gradle's dev run configurations (`build.gradle` `runs { configureEach { systemProperty ... } }`) and appears in no packaged resource, so a built jar ships v1. Recorded as a **release-gate item**: bump deliberately only once the production Rails instance is confirmed to carry the Milestone 16 contract.
3. `UltimaCraft_Persistent_Flower_System_Design.docx` deleted in this merge (commit `03e28c8d`).
4. `content/banner_catalogue_status.md` restored as tool-generated data (commit `03e28c8d`); its three assumption guards removed, so the status-report tests now execute — skips fell 20 → 17.

### Merge executed

```text
Date: 2026-08-08
Target: patch-18 (primary working copy C:\projects\britannia\mod\Britannia_Mod)
Command: git merge --no-ff patch-18-network-integration
Merge commit: 632570feb4463c6923554d35c3b745c0a12f5917
Parents: 62df1dc97c5113a86f9c0f258cb90538f31efe89 (previous patch-18 HEAD)
         03e28c8d9623a9acf97bd74ec75dd3f89528bbb7 (integration HEAD)
Result: conflict-free (patch-18 never moved during the project, as predicted)
Verification: patch-18 tree is byte-identical to patch-18-network-integration (empty diff);
              patch-18 is 170 commits ahead of origin/patch-18
Pre-merge validation on the integration branch: build SUCCESS; 1,679 unit tests, 0 failures, 17 skips;
              all 333 gametests pass
NOT performed (never authorized): push. origin/patch-18 remains at 62df1dc9; the integration branch has
              never been pushed. Publishing the merged patch-18 is a separate owner decision.
```

### Post-merge state

- `patch-18` (local): `632570fe` — integrated, unpushed.
- `patch-18-network-integration`: `03e28c8d` — retained as the audit trail; the worktree at `C:\projects\britannia\mod\Britannia_Mod-integration` can be removed with `git worktree remove` whenever the owner is satisfied.
- All five feature branches: unmodified at their project-start HEADs, with their project documents intact.
