# Mining Skill Test Matrix

Date: 2026-08-15 · Branch `patch-18`

Final automated run: **2,296 unit tests** and **491 GameTests**, all passing.

Note on the GameTest harness: `run/gametest/world` persists between runs, and one unrelated
textile test counts item entities near the *shared* mock-player spawn, so stale drops accumulate
there and can fail it intermittently. Deleting that directory resets it. Filed separately; it is
not a Mining defect.

Every row names the test that actually enforces it. "Unit" = JUnit (`./gradlew.bat test`),
"GameTest" = in-world (`./gradlew.bat runGameTestServer`). Rows marked **owner-run** cannot be
executed from this environment and are listed in §7.

---

## 1. Skill gates — the approved ladder

Boundary convention: `requirement − 0.1` denied · `requirement` allowed (inclusive, design §10.2)
· `requirement + 0.1` allowed.

| Tier | Req | Unit (policy) | GameTest (in-world) |
|---|---:|---|---|
| Stone | 0.0 | `MiningBreakGateTest.everyActiveTierEnforcesTheInclusiveBoundaryExactly` | `MiningLadderGameTests.stoneRemainsTrainableAtEverySkillLevel` (0 / 50 / 99.9) |
| Iron | 0.0 | same | `MiningLadderGameTests.everyTierDeniesJustBelowAndMinesAtItsRequirement` |
| Silver | 55.0 | `metalLadderBoundariesMatchTheApprovedValues` | ladder sweep + `SilverMiningGameTests` (54.9 / 55.0) |
| Tin | 65.0 | same | ladder sweep |
| Shadow Iron | 70.0 | same | ladder sweep |
| Copper | 75.0 | same | ladder sweep |
| Gold | 85.0 | same | ladder sweep |
| Agapite | 90.0 | same | ladder sweep |
| Verite | 95.0 | same | ladder sweep |
| Valorite | 99.0 | same | ladder sweep |
| Dripstone | 35.0 | `MiningBreakGateTest.retiredResourcesNeverReachTheGate` (34.9 / 35.0) | — |
| Every ACTIVE definition | all | `everyActiveTierEnforcesTheInclusiveBoundaryExactly` iterates the whole catalogue | — |

## 2. Denied-break invariants (design §10.3)

| Invariant | Test |
|---|---|
| Block and state unchanged | `MiningGateGameTests.underSkilledBreakIsCompletelyInert` |
| Zero drops | same |
| Zero durability delta | same |
| Zero Mining award | same |
| Zero restoration records | same + `MiningRestorationGameTests.deniedBreakAddsNoRestorationRecord` |
| Denial for any tool (not only the Britannia pickaxe) | `MiningGateGameTests.vanillaToolCanNoLongerDestroyGatedOreDroplessly` |
| Denial feedback never in chat | `MiningFeedbackPolicyTest.denialFeedbackUsesTheActionBar` |

## 3. Successful-break invariants

| Invariant | Test |
|---|---|
| One break, one drop | `MiningGateGameTests.twoPlayersWithDifferentSkillsSeeDifferentOutcomes` |
| Correct drop identity and purity range | `SilverMiningGameTests.silverMinesAtTheThresholdIntoItsExistingIdentity` |
| At most one Mining activation | `MiningSkillTest` (dedup) + the two-player GameTest |
| At most one restoration record per position | `MiningRestorationGameTests.oneRecordPerPositionSurvivesSaveAndLoad` |
| Restoration remembers the original node | same |
| Award only on ELIGIBLE (no bypass/automation gain) | `MiningSkillTest`, `MiningGateGameTests.creativeBypassesTheThresholdWithoutGain` |

## 4. Persistence and lifecycle

| Scenario | Test |
|---|---|
| Restart with a pending restoration | `MiningRestorationGameTests.oneRecordPerPositionSurvivesSaveAndLoad`, `SilverMiningGameTests.pendingSilverRestorationSurvivesSaveAndLoad` |
| Provenance survives restart | `MiningLadderGameTests.playerPlacedProvenanceIsPersisted` |
| Chunk unload/reload | `MiningRestorationPolicyTest.restorationKeepsASingleSchedulerAndNeverForceLoads` (unloaded cells retried, never force-loaded) |
| Skill reload / reconnect / dimension change | Existing `SkillManager` lifecycle (login fetch, logout clear, respawn/dimension resync) — unchanged by Mining; threshold immediacy proven by `MiningGateGameTests.crossingTheThresholdTakesEffectImmediately` |
| Old saves keep working | `MiningRestorationPolicyTest.provenanceDefaultsToNatural` (absence = natural, no migration) |

## 5. Actors, modes and multiplayer

| Scenario | Test |
|---|---|
| Two players, different skills, same session | `MiningGateGameTests.twoPlayersWithDifferentSkillsSeeDifferentOutcomes` |
| Two players, neighbouring nodes, independent records | `MiningRestorationGameTests.neighbouringNodesAndTwoPlayersStayIndependent` |
| Creative bypass, no gain | `MiningGateGameTests.creativeBypassesTheThresholdWithoutGain` |
| Operator (permission 2) bypass | `MiningBreakGateTest.creativeAndOperatorBypassTheThresholdOnly` |
| Fake player / automation denied | `MiningGateGameTests.fakePlayersAreDeniedByDefault`, `MiningBreakGateTest.automationIsDeniedEvenWithCreativeOrSkill` |
| Skill data unavailable → denied | `MiningGateGameTests.unavailableSkillDataDeniesEvenWithTheRightTool` |

## 6. Content, economy and assets

| Scenario | Test |
|---|---|
| Catalogue covers exactly the managed set | `MineableCatalogContractTest.activeCoverageEqualsTheDiscoveredManagedSetExactly` |
| Drop names unchanged by the M6 refactor | `dropNamesMatchTheHistoricalBlockBreakUtilsChain` |
| Catalogue validation (duplicates, ranges, malformed refs) | `MineableCatalogValidationTest` (12 tests) |
| Retired resources unreachable | `retiredResourcesAreAbsentFromTheCatalogue`, `retiredResourcesNeverReachTheGate` |
| Stone posts its seeded commodity family | `MiningEconomyIdentityTest.everyStoneCommodityIsPostedUnderItsSeededFamily` |
| Blackrock → seeded blackstone commodity | `blackrockResolvesToTheSeededBlackstoneCommodity` |
| No invented or duplicated commodities | `noMineableClaimsACommodityRailsDoesNotSeed`, `noTwoResourcesShareACommodityUnintentionally` |
| All nine metals refine into their own ingot | `MiningEconomyGameTests.everyMinedMetalRefinesIntoItsOwnIngot` |
| Blacksmith accepts every refined ingot | `everyRefinedIngotIsAcceptedByTheBlacksmith` |
| Every mod-owned block ships assets and real textures | `SilverVerticalSliceTest.everyCatalogueBlockOwnedByThisModShipsItsAssets` |
| Vein generator actually places ore | `SilverMiningGameTests.silverVeinGeneratorPlacesSilverOre` |
| Progression lands on 18,000 ±5% | `MiningCalibrationTest` (fails the build outside the window) |

## 7. Owner-run acceptance (cannot be automated here)

These need real clients and a live Rails instance, neither of which this environment can drive.
The runbook is `MINING_SKILL_IMPLEMENTATION_STATUS.md` §"Live acceptance runbook".

| Scenario | Why it is manual |
|---|---|
| Two live clients, LowMiner/HighMiner on a dedicated server | Requires running game clients |
| Skill raised through **Rails** (not `applyConfirmedValue`) then retry without reconnect | Requires a reachable Rails instance; its DB was unavailable all session |
| `/populateores <metal>` after seeding veins, then mine what appears | Requires an operator and a live shard |
| Selling mined ore and stone to a live trader | Requires live commodity rows |
| Visual check of ore/ingot art in-game | Requires a client |
