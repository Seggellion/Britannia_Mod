# M11 regression evidence

## Current failed-QA follow-up

2026-09-07 failed-QA follow-up: the display-case empty-packet fix and moongate entity-material correction are implemented. The clean release passed **3,498 JUnit tests reported: 3,481 passed, 17 inherited skips, zero failures/errors; all 1,156 required GameTests passed**. Candidate `britannia_mod-0.1.8a-all-6414f54c.jar` embeds clean source `6414f54cb6c31094f61314276c2bb2590f0574a9`. **No new visual gate was personally completed**: Windows was locked during the controlled client attempt. Display-case visual synchronization and all three moongate configurations remain **PENDING_MANUAL_RETEST**; full-can art remains **PENDING_USER_ASSET**. See [current three-issue report](QA_FINAL_REPORT.md), [root-cause evidence](QA_FOLLOWUP_2026-09-07.md), and [release identity](M11_RELEASE_IDENTITY.md). Only the isolated mod worktree was used; Rails was not modified or rerun. No merge, push, deployment or production operation occurred.

The material below is the preserved historical M0–M11 checkpoint. In particular, earlier statements that the moongate renderer was unchanged describe the old candidate and no longer describe this correction. The original failed manual checks are retained in the current evidence.

Test operator: Codex, 2026-09-07. All commands ran in the two isolated worktrees recorded in the handoff. Earlier discovery and failed-run logs remain preserved. No skipped test is counted as a pass.

## Final automated gates

Mod clean source: `4ee0f5d90d70b2429c7d5cb8eacf100b838d0d67`. With Java 21 selected, `gradlew.bat clean build artifactIdentity --no-configuration-cache --console=plain` completed successfully in 6m38s. It included normal resources and the full JUnit suite: **3,492 tests, 3,475 passes, 17 skips, 0 failures/errors, 454 suites**. Fresh XML totals were counted independently. The normal clean target was verified as this isolated worktree’s build directory. Log: `tmp/gameplay-bugfixes/m11-clean-release.log`.

The final registered server command was `gradlew.bat runGameTestServer -x processResources --no-configuration-cache --console=plain` after ordinary resources had been processed and were unchanged. **All 1,154 required GameTests passed in 2.244 minutes**, `m11-gametest-final.log`. No diagnostic source-set/namespace was enabled. The subsequent clean release rebuilt normal resources and repeated the full JUnit gate. Gradle selected Microsoft JDK 21.0.8 for the server; the native packaged client used Eclipse Adoptium 21.0.9.

Rails validated source: `4e67f4c41c4d6f531c673e11fd6b2dbd71b59e18`. Ruby 3.2.2 through `rbenv exec`, PostgreSQL 18 local socket, explicit `RAILS_ENV=test`, `PARALLEL_WORKERS=1`, exact-name guard and `TEST_DATABASE_URL=postgresql:///ultimacraft_test-N?username=ultimacraft_codex_test`. Generic database/password environment variables were unset. Each new database was verified absent before creation and schema loading; no existing database was reset or dropped.

The full normal Rails gate is partitioned into eight disjoint groups with at most four processes active. Each command is `rbenv exec ruby bin/rails test <exact file list> --seed 21079 --verbose`. The verified installed Rails 8.0.1 runner’s normal file selection is preserved: **435 files, each exactly once**. Its two usual browser-system exclusions are `test/system/roadmap_responsive_test.rb` and `test/system/shard_platform_responsive_test.rb`; no new file or acceptance exclusion was introduced. Exact groups, per-file SHA-256, source HEAD, guard databases, PIDs, logs, exits and totals: `tmp/gameplay-bugfixes/m11-rails-final-partitions.json` in the mod worktree. Logs live under the Rails worktree’s `tmp/gameplay-bugfixes/m11-rails-final-partition-0.log` through `-7.log`. Databases are `ultimacraft_test-1815` through `ultimacraft_test-1822`.

**3,190 runs, 51,848 assertions, 0 failures, 0 errors, 2 inherited conditional skips; all 435 normal test files covered.**

| Partition | Files | Guarded database | Runs / assertions / failures / errors / skips | Seconds |
|---|---|---|---|---|
| 0 | 53 | ultimacraft_test-1815 | 393 / 14713 / 0 / 0 / 1 | 117.709375 |
| 1 | 54 | ultimacraft_test-1816 | 385 / 2976 / 0 / 0 / 0 | 532.255988 |
| 2 | 55 | ultimacraft_test-1817 | 427 / 1871 / 0 / 0 / 0 | 148.720471 |
| 3 | 55 | ultimacraft_test-1818 | 417 / 22336 / 0 / 0 / 0 | 137.216862 |
| 4 | 54 | ultimacraft_test-1819 | 373 / 1898 / 0 / 0 / 0 | 158.480073 |
| 5 | 55 | ultimacraft_test-1820 | 408 / 2735 / 0 / 0 / 0 | 169.011079 |
| 6 | 55 | ultimacraft_test-1821 | 351 / 2075 / 0 / 0 / 0 | 122.247435 |
| 7 | 54 | ultimacraft_test-1822 | 436 / 3244 / 0 / 0 / 1 | 137.455672 |

Existing conditional skips (not passes):

- TraderPolicyCrossRepositoryParityTest#test_writes_the_mirrored_fixture_the_mod_replays [test/services/economy/trader_policy_cross_repository_parity_test.rb:210]: set WRITE_PARITY_FIXTURE=1 to regenerate

- ServiceNpcSpawnPointsEconomicPostsTest#test_service_posts_are_unaffected_regression_check [test/services/service_npc_spawn_points_economic_posts_test.rb:168]: no seeded service type in this database

Partitioning avoids repeatedly scanning the entire catalog accumulated by unrelated nontransactional/concurrency fixtures in one database. It changes test order and database isolation, not the normal suite’s membership. The final run uses one clean committed source throughout; later closing commits are documentation only.

## Focused milestone gates

These are separate executions, not additive unique-test totals. Their complete commands and earlier fixture corrections are in the scratchpad.

| Milestone | Focused JUnit passes / skips | Registered required GameTests passed | Final log |
|---|---|---|---|
| M0 smoke | 166 / 6 | historical baseline 1,102, not a new M0 run | `m0-smoke.log` |
| M1 | 169 / 6 | 1,111 | `m1-final.log` |
| M2 | 178 / 6 | 1,115 | `m2-final.log` |
| M3 | 9 / 0 | 1,118 | `m3-final.log` |
| M4 | 180 / 6 | 1,127 | `m4-release-review.log` |
| M5 | 166 / 6 | 1,131 | `m5-final.log` (exact invocation in scratchpad) |
| M6 | 176 / 6 | 1,134 | `m6-final.log` (exact invocation in scratchpad) |
| M7a real Rails | 111 / 0 | 1,137 including opt-in real HTTP | `m7-final-2.log` |
| M7b recovery | 27 / 0 | 1,142 including opt-in real HTTP | `m7b-live-2.log` |
| M8 | 5 / 0 | 1,147 | `m8-3.log` |
| M9 | 7 / 0 | 1,154 | `m9-2.log` |

M10’s normal clean packaged baseline build passed in 1m45s with `build artifactIdentity -x test`; its graphical limits and SHA are in M10_SHADER_ACCEPTANCE.md. Existing renderer/model and registered moongate tests also ran in the full final suites; they do not prove visible Photon compatibility.

M7’s final relevant Rails economy command selected `test/services/commodity_seeder_test.rb`, `test/services/gameplay_produce_rollout_test.rb`, `test/services/economy`, `test/controllers/api/economic_buyback_catalogs_controller_test.rb`, `test/controllers/api/gameplay_produce_buyback_test.rb` and `test/controllers/api/trader_transactions_controller_test.rb`. Result: **208 runs, 1,974 assertions, 0 failures/errors, 1 inherited opt-in parity-writer skip**, 67.887533s (`m7b-final-rails.log`). The five new recovery GameTests and real after-commit response-loss test are described in M7_SALE_RECOVERY.md. The default final 1,154-test gate does not enable external Rails integration; historical commands used the then-current environment selector, while future opt-in runs use JVM property `britannia.m7.integrationConfig`.

M11 focused corrections: **13 JUnit passes** in `m11-corrected-contracts.log`; **18 Rails runs / 81 assertions** in `m11-rails-corrected-fresh.log`; **55 Rails runs / 337 assertions** in `m11-rails-corrections.log`. All have zero failures/errors/skips. The last Rails selection covers query budgets, application image helpers, SEO rendering, authentication halting, session return/join behavior and redeem refusal behavior.

## Earlier failures and their resolution

The first full mod JUnit run executed 3,492 tests with three failures and 17 inherited skips. Two historical source contracts still required the replaced mainhand-only dye adapter. They now check MAIN-phase ownership with either ingredient role, mutation on the server before effects, existing banner preview and nonmatch fallback. A credential-source guard found a direct environment read in the opt-in GameTest: the guard stayed intact, and only test configuration selection moved to a JVM property. Its class is excluded from release artifacts.

The corrected combined mod run passed all JUnit tests but failed one of 1,154 GameTests when Windows denied a synchronous atomic journal replacement. The lock owner was not identified; version-matched SavedData/DimensionDataStorage source is synchronous. Production recovery correctly retained a pending receipt. The fixture now permits that durable pending state and retries boundedly until exact components/count and journal resolution are verified, asserting no partial delivery, loss or duplicate on replay. Production settlement code was unchanged. The complete final server gate subsequently passed.

The initial sequential Rails full attempt was stopped after **2,119 runs, 24,650 assertions, 5 failures, 0 errors, 1 skip**, 2,792.190546s. It is an interrupted run, not a full pass. Four refusal tests wrongly required an empty global immutable audit table; one bank-reconciliation assertion searched an entire history page for a phrase that should be absent only from the tested operation’s row. Corrections explicitly create prior unrelated history, assert no new audit around a refusal, and scope row text to the created operation. Controllers’ refusal/materialization behavior and authorization assertions were preserved. A corrected sequential attempt was superseded after **1,115 runs, 5,574 assertions, 0 failures/errors/skips**, 613.261320s. Only each verified obsolete test child was interrupted; logs and databases remain.

The first four-group full run completed **3,190 runs, 51,842 assertions, 4 failures, 0 errors, 2 skips**. Three additional redeem refusal assertions had the same empty-history assumption and received operation-scoped checks with prior history. The remaining query-budget failure reproduced alone on fresh test database 1814: community 41 versus budget 40, city 22 versus budget 20. The test and public-page code were unchanged from the project’s base before this correction. A SQL trace found unnecessary anonymous `id IS NULL` user lookup and two separate social-image setting fetches. The correction avoids the null lookup and fetches both settings together while preserving priority/fallback behavior. Existing query budgets were not increased; focused rendering/authentication/helper tests pass. Some first-pass processes overlapped those edits, so only the final clean-source eight-group run is the release regression gate.

Earlier milestone compile/fixture failures are retained in the scratchpad: Creative-overriding Minecraft mock players, fixture geometry/collision, source-water dispatch, immediate bonemeal fruit, crate override preservation and false-default Creative origin. Final server behavior was not weakened to accommodate those fixtures.

## Inherited JUnit skips

Eleven concern pre-existing banner/content-owner evidence; six concern farming/catalog/artifact-owner evidence. None was newly skipped by this project. Exact methods:

- `com.seggellion.britannia_mod.bannerdyeing.ExtraSmallGateECloseoutTest.productOwnerEvidenceIsCompleteTrackedAndHasNoUnresolvedCurrentItem()`
- `com.seggellion.britannia_mod.bannerdyeing.FinalContentIntakeDocumentationTest.readmeAndTemplatesStateTheApproved128PixelStandard()`
- `com.seggellion.britannia_mod.bannerdyeing.FinalContentIntakeDocumentationTest.productionCatalogueKeepsAllApprovedFamiliesComplete()`
- `com.seggellion.britannia_mod.bannerdyeing.FinalContentIntakeDocumentationTest.guideDefinesTheSingleTwoFileSelectiveRecolourContract()`
- `com.seggellion.britannia_mod.bannerdyeing.FinalContentIntakeDocumentationTest.checklistCoversTwoFilesAutomatedBoundariesAndManualMatrices()`
- `com.seggellion.britannia_mod.bannerdyeing.FinalContentIntakeDocumentationTest.intakeGuideChecklistReadmeAndTemplatesExist()`
- `com.seggellion.britannia_mod.bannerdyeing.MediumGateECloseoutTest.productOwnerEvidenceMatchesApprovedIntakesAndHasNoUnresolvedResult()`
- `com.seggellion.britannia_mod.bannerdyeing.ParallelLargeGateECloseoutTest.productOwnerEvidenceBindsEveryPassToTheExactReviewedCommitAndHashes()`
- `com.seggellion.britannia_mod.bannerdyeing.ParallelMediumGateECloseoutTest.productOwnerEvidenceBindsTheReviewedCommitAssetsAndEveryPassResult()`
- `com.seggellion.britannia_mod.bannerdyeing.RoadGuardIntegrationTest.roadGuardPreservesHistoricalEvidenceAndRecordsCurrentGateEApproval()`
- `com.seggellion.britannia_mod.bannerdyeing.SmallGateECloseoutTest.productOwnerEvidenceMatchesApprovedIntakesAndHasNoUnresolvedResult()`
- `com.seggellion.britannia_mod.farming.FarmingSkillProgressionCloseoutTest.catalogProposalRuntimeCultivationAndPresentationPoliciesReconcile()`
- `com.seggellion.britannia_mod.farming.FarmingSkillRequirementTest.approvedProposalCatalogDefinitionsAndPlantingMappingsReconcileExactly()`
- `com.seggellion.britannia_mod.farming.FarmingSkillRequirementTest.missingExtraAndProposalMismatchAreRejectedWithoutASecondValueTable()`
- `com.seggellion.britannia_mod.farming.FarmingSkillRequirementTest.everyRequiredSentinelMatchesTheCommittedProposal()`
- `com.seggellion.britannia_mod.farming.FlowerAssetContractTest.allNinetyEightInWorldPngsHonorTheAlignedBaseMaskContract()`
- `com.seggellion.britannia_mod.farming.FlowerAssetContractTest.manifestAndHistoricalHashLedgerRespectIllustratorOwnedRuntimeTextures()`

## Manual and external limits

Native packaged client startup and a fresh normal-world load were observed with the exact clean M10 baseline and fresh GPU/driver logs. Concurrent desktop input prevented reliable controlled actions; an unanswered coordination question remains and automated native input was paused. Physical HUD/hand/fence/synchronization/world distribution, actual client reconnect/server restart, and the required three-configuration moongate comparison have not been certified. The prepared scene pack was not installed or executed. The full watering-can art was not supplied or invented. The final handoff names each pending gate and the smallest next action.

Production evidence is read-only pre-release Jhelom/Britannia policy/catalog state, not deployed support or a live sale. Dedicated server player-file and journal tests do not claim arbitrary power-loss durability or the integrated host’s separate player snapshot. Uncertain trader receipts deliberately remain pending for reconciliation. No publication or production action occurred.
