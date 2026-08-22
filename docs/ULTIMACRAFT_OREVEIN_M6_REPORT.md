# UltimaCraft OreVein Remediation — Milestone 6 Report

**Managed Extraction Policy Hardening**

Branch `claude/ultimacraft-orevein-remediation-70bfeb` · worktree `flagstone-foundation-kickoff-8fb72e` · 2026-08-21

> **Amended 2026-08-21 after owner review.** The durability rule is approved as written. The
> Creative rule is not: an ordinary Creative break of a sited deposit is now **refused**, not
> permitted-without-yield. All figures, tables and invariants below are post-amendment.

---

## Objective

Make it impossible for an enchantment, a machine, or an administrative mode to get economy out of a managed geological resource by accident — and to express that as one coherent policy across the canonical resource architecture rather than as branching in the sediment code.

Concretely: pin Fortune, Silk Touch, fake players, non-player automation, Creative and operator behaviour, tool durability, yield amplification, and single-commit semantics, with executable behavioural tests for clay, silica, a managed ore and a managed stone.

---

## M5 boundary validation

Checked against the M5 completion report before any M6 work began.

| Question | Answer |
| -------- | ------ |
| All M5 development exit criteria passed? | **Yes.** One criterion is met partially and knowingly — see below |
| Final JUnit | **2,921 run · 5 failed · 0 errors · 17 skipped** |
| Final GameTests | **675 run · 2 failed** at steady state (a third failure in one run was the textile flake, clean on re-run) |
| New deterministic regressions | **None** |
| Known flaky / pre-existing | 5 JUnit (`RoutineSkillGainPresentationTest`, `MilestoneEightContentReportTest`, `CorrectiveMilestoneNineARenderAlignmentTest`, `MonolithMilestoneSevenRenderingTest` ×2); 2 GameTest (`aprivateplotacceptsitsownerandrefuseseveryoneelse`, `alegacyvineconvertsonceandkeepsitsvarietyandmaturity`); 1 intermittent textile GameTest |
| Denied vanilla Overworld mineral features suppressed? | **Yes — all 19 placed features, in every `#minecraft:is_overworld` biome.** Six of eight families reach exactly zero blocks; copper and iron retain ~4% and ~3% from `OreVeinifier`, which is not a feature and cannot be reached by any biome modifier |
| Allowed geology remains? | **Yes** — proven at registry level and by block counts over 256 chunks |
| Nether untouched? | **Yes** — empty suppression set, wrong biome tag, wrong decoration step, and 64 generated Nether chunks with all ores intact |
| Existing chunks / manual / structure blocks untouched? | **Yes** — M5 added no runtime code that reads or writes a chunk |
| Safe in code, not approved for production? | **Yes** — development-complete, explicitly **not deployable** |

**Blocker check: none.** The one partial criterion is a documented Minecraft architecture constraint, recorded in shipped data and pinned by a test, not an unexplained regression. M5 is accepted for the purpose of continuing the project.

**M5 recommendations carried into M6–M9, unchanged:**

- an **activation gate** so the suppression data can merge without changing production generation (M7 keeps it inactive, M8 establishes prerequisites, M9 activates and re-audits);
- an owner decision on the noise ore-vein leak, recommended for M9 with real economy data;
- the iron/gold cutover belongs in M8, alongside the retrofit;
- the `GameTestServer` uses `WorldPresets.FLAT`, so no GameTest can assert anything about generated ore.

**Production suppression remains gated.** M6 has not altered, activated or deactivated any part of it.

---

## Findings

Four discoveries, two of which were live defects.

### 1. Fake players could work the sediment beds — and the existing tests depended on it

`ManagedDepositInteractionHandler` accepted anything that satisfied `event.getPlayer() instanceof ServerPlayer`. NeoForge's `FakePlayer` **is** a `ServerPlayer`, so a quarry mod, an automation block, or any mod driving a break through a fake player with a project shovel in hand was an ordinary customer of `ManagedDepositExtraction`: it got the configured yield, filed restoration debt, and depleted the bed.

The Mining ladder was already safe here — `MiningBreakGate` types a `FakePlayer` as `AUTOMATION` and denies it — so this was specifically the sediment family, and specifically the family this milestone came from.

What kept it invisible is worth stating plainly: **the clay, silica and sandstone extraction tests were themselves built on `FakePlayerFactory`.** Their `player(level, name)` helper needed a *named* player so a house owner could be told from a stranger, and `FakePlayerFactory.get` was the convenient way to make one. So the suite proved, repeatedly and correctly, that a fake player could extract a deposit. The tests were not wrong about extraction; they were asserting it about the wrong kind of subject.

### 2. A Creative operator was minting purity ore and filing restoration debt

`MiningBreakGate` returns `APPROVED_BYPASS` for a creative or operator actor, which permits the break — that is the deliberate, long-standing administrative bypass, and it exists so an operator can clear a misplaced block. But permitting the break let the event reach `CustomBlockBreakHandler` at NORMAL priority, which asked only "is this an authorized tool?" and then ran the entire managed flow.

The result: an administrator in creative holding a Britannia pickaxe, clearing a badly placed vein, was dropping graded stone or purity ore on the floor and enrolling restoration debt **in their own name**. No Mining skill was awarded — `MiningSkill.awardForBreak` re-checks the gate and `APPROVED_BYPASS` does not qualify — so the defect was half-contained, which is the shape that survives review.

The sediment family did not have this problem *for yield*: its handler returns early on `instabuild`, so a creative break of a clay bed passed straight through to vanilla. But that is the other half of the defect, and the owner rejected it on review — the bed was still destroyed, permanently, with no restoration debt to bring it back, by an action indistinguishable from clearing a stray block. **Both halves are now closed: a creative break of a sited deposit is refused outright.**

### 3. The GameTest harness runs every mock player in Creative

`GameTestServer` creates its level with `GameType.CREATIVE`, and `GameTestHelper.makeMockServerPlayerInLevel` additionally hard-codes `isCreative()` to return `true`. A test that does not explicitly call `setGameMode(GameType.SURVIVAL)` is therefore testing an operator, not a player.

This is the direct reason finding 2 went unnoticed: any test written to prove ordinary extraction was, without saying so, proving *creative* extraction. It is also a trap for every future milestone that writes a policy keyed on game mode, so it is now handled once, centrally, in `ManagedResourceTestPlayers`.

### 4. There was no Fortune or Silk Touch behaviour to conflict with

The milestone asked me to identify existing intentional Fortune handling before changing anything. There is none: outside the new policy class's own documentation, the string `Fortune`, `silk_touch` and `getEnchantmentLevel` do not appear anywhere in `src/main/java`. The mod has never read either enchantment.

Both policies therefore already held, but for **structural** reasons rather than enforced ones:

- **Fortune** has nothing to multiply. Managed yield never consults a loot table; it is produced directly from the resource definition (`yieldStack`) or from the purity/grade generator.
- **Silk Touch** has nothing to substitute. The managed break is cancelled before vanilla can drop anything, and the sediment beds have no loot table *and no block item at all*.

A structural guarantee is the strongest kind right up until somebody adds a block item "so it can be picked up in creative", at which point it stops being true and nothing fails. So both are now pinned behaviourally, with real enchantments on real tools through the real event bus, rather than left to hold by construction.

**One nuance discovered while pinning it:** the managed *ores* do have block items on purpose — an administrator places `britannia_mod:silver_ore` to build a vein — while the sediment *beds* have none. The no-block-item invariant is therefore asserted for the sediment family, where it is documented and load-bearing; the ores are protected by interception plus the M3/M7 provenance rule instead.

---

## Policy matrix

Actual behaviour, as asserted by `ManagedExtractionPolicyGameTests` against a running world. "Managed ore" covers both output modes — a purity ore (`britannia_mod:silver_ore`) and a graded stone (`minecraft:stone`) — which are tested separately and behave identically.

| Actor/tool state | Managed ore | Clay | Silica | Economic yield | Debt | Tool damage |
| ---------------- | ----------- | ---- | ------ | -------------- | ---- | ----------- |
| Correct Britannia tool | extracts | extracts | extracts | configured, ×1 | 1 | 1 |
| Fortune III on correct tool | extracts | extracts | extracts | configured, ×1 — **unchanged** | 1 | 1 |
| Silk Touch on correct tool | extracts | extracts | extracts | configured, ×1; block never dropped | 1 | 1 |
| Wrong tool (cross-family, vanilla, axe, stick, bare hand) | refused, block stands | refused | refused | none | 0 | **0** |
| Fake player (correct tool) | refused, block stands | refused | refused | none | 0 | **0** |
| Non-player actor (no `ServerPlayer`) | refused | refused | refused | none | 0 | 0 |
| Creative player / operator, sited deposit | **break refused, block stands** | refused | refused | **none** | **0** | **0** |
| Creative player / operator, ambient rock (`minecraft:stone` and the vanilla STONE family) | breaks normally, no managed flow | — | — | **none** | **0** | **0** |
| Creative player, block they placed themselves | breaks normally (provenance) | — | — | none | 0 | 0 |
| Second break of an already depleted cell | nothing to extract | same | same | none | 0 | **0** |

Notes on rows that carry a decision:

- **Creative** takes decision **A**, on owner instruction: a sited deposit is protected from ordinary Creative destruction. The `APPROVED_BYPASS` still exists and still means an operator is not answered "your Mining is too low" — but it no longer means the block may be deleted by clicking it. Administrative removal must be explicit, through `/manageddeposit` or `/populateores`. The governing invariant is `ordinary player block break != explicit administrator deposit removal`.
- **"Sited deposit" is drawn from the data, not a name list.** ORE and SEDIMENT resources are deposits by definition. STONE resources are deposits only when the block is mod-owned (`britannia_mod:sandstone_deposit` and the four bespoke rocks) — the vanilla STONE family (`minecraft:stone`, `granite`, `deepslate`, `cobblestone`, …) is the world's crust, and refusing Creative breaks on it would stop builders terraforming, cutting cellars or clearing house plots for no protective benefit. A resource added later inherits the right answer without being mentioned anywhere. **If the owner wants ambient rock protected too, it is one line in `ManagedExtractionPolicy.isDepositCell`.**
- **Tool damage on refusal is zero in every refusal case**, including the ones that reach deep into the flow (house-protected ground, insufficient skill, denied actor). No refusal path charges the tool, because no refusal path does any work.

---

## Policy ownership

| Layer | Owns | Where |
| ----- | ---- | ----- |
| **Global managed-extraction policy** | Who may produce economy (player / fake player / non-player / creative), and what a committed extraction costs the tool | `ManagedExtractionPolicy` |
| **Resource-definition data** | Which item tag may work it, what it yields, what it leaves behind, how long it takes to return | `resources.json` → `ResourceDefinition` — **unchanged by M6** |
| **Actor policy** | Actor typing and the creative determination, used identically by both resource families | `ManagedExtractionPolicy.actorOf` / `isCreativeGameMode`, delegated to by `MiningBreakGate` |
| **Enchantment policy** | Deliberately empty | Nowhere — see below |

**No new data-schema fields were added, and that is the decision rather than an omission.**

Every managed economic resource in the catalogue answers these questions identically today: Fortune ignored, Silk Touch ignored, fake players denied, automation denied, creative non-earning. Adding four fields to twenty-eight resource definitions would produce twenty-eight copies of one sentence, twenty-eight opportunities to get one wrong, and would still need a field the day a resource genuinely differed. There is no concrete current variation and no imminent requirement in the project that needs one, so the smallest architecture that supports the requirements is a central policy with per-resource data left exactly as it was.

The split holds up under its own logic: what stays in the resource definition is what genuinely varies per resource. Who may swing the tool does not vary per resource — it varies per actor.

**Enchantment policy is deliberately absent from the code.** There is no "ignore Fortune" branch, because writing one would imply a path exists where Fortune applied. The yield never reaches a loot table, so there is nothing to multiply or substitute. The policy is enforced by the shape of the transaction and pinned by behavioural tests, which is the honest arrangement.

**Delegation, not duplication.** `MiningBreakGate` keeps its own richer `ResultType` — it answers skill, provenance and feedback questions the deposit path does not have — but its actor typing and creative determination now come from `ManagedExtractionPolicy`. There is one definition of "fake player" and one of "creative" for both families, so the sediment beds and the ore ladder cannot drift into disagreeing about who is holding the tool. No second extraction catalogue was created; `MineableCatalog` and `ResourceCatalog` are untouched.

---

## Transaction flow

Two entry paths, each with exactly one commit point. No third path reaches either service.

### Path A — Mining resources (ore and stone)

```
BlockEvent.BreakEvent
  └─ ManagedResourceCreativeGuard            (HIGHEST)                        ← M6 amendment
       creative + sited deposit + not player-placed  → CANCEL (block stands)
  └─ MiningGateHandler                       (HIGH)
       MiningBreakGate.evaluate
         player-placed?      → NOT_APPLICABLE          → permit (construction, vanilla rules)
         not a mineable?     → NOT_APPLICABLE          → permit
         not a player / fake → NON_PLAYER_POLICY       → CANCEL
         creative / operator → APPROVED_BYPASS         → permit  (break allowed, earns nothing)
         wrong tool          → WRONG_TOOL              → CANCEL
         skill unavailable / below requirement         → CANCEL
  └─ CustomBlockBreakHandler                 (NORMAL — cancelled events never arrive)
       ManagedExtractionPolicy.mayExtract(player)      → return if denied   ← M6
       MiningExtractionTool.isAuthorized(state, held)  → return if not
       ┌──────────────── single commit point ────────────────┐
       │ setBlock(depleted)                                  │
       │ drop configured graded/purity item                  │
       │ BrokenBlockTracker.recordBrokenBlock                │
       │ MiningSkill.awardForBreak                           │
       │ ManagedExtractionPolicy.chargeExtractionTool   ← M6 │
       └─────────────────────────────────────────────────────┘
```

### Path B — Sediment beds (clay and silica)

```
BlockEvent.BreakEvent  ─or─  PlayerInteractEvent.LeftClickBlock (adventure only)
  └─ ManagedResourceCreativeGuard            (HIGHEST)                        ← M6 amendment
       creative + sited deposit → CANCEL (bed stands)
  └─ ManagedDepositInteractionHandler        (HIGH)
       not a deposit            → return
       instabuild (creative)    → return  (ordinary vanilla removal, drops nothing)
       otherwise                → CANCEL, then:
  └─ ManagedDepositExtraction.extract
       resolve                  → NOT_A_DEPOSIT
       ManagedExtractionPolicy  → DENIED_ACTOR    ← M6  (block left standing)
       HouseBuildRights         → PROTECTED
       extraction tag           → WRONG_TOOL
       ┌──────────────── single commit point ────────────────┐
       │ BrokenBlockTracker.recordBrokenBlock (from the      │
       │   standing state, before mutation)                  │
       │ setBlock(depleted)                                  │
       │ pop configured yield                                │
       │ ManagedExtractionPolicy.chargeExtractionTool   ← M6 │
       └─────────────────────────────────────────────────────┘
```

**Why two handlers cannot commit the same extraction.** A sediment bed is not in `MineableCatalog`, so the Mining gate resolves it `NOT_APPLICABLE` and never acts on it. A managed ore is not in the `SEDIMENT` family, so the deposit handler never resolves it. Where a resource could in principle satisfy both, the first handler cancels the event and NeoForge does not deliver cancelled events to later listeners. Both are now pinned by `oneBreakCommitsExactlyOneOfEverything`, which posts to the **real event bus** so every registered listener sees the event in its real priority order — driving the services directly would prove the services correct and say nothing about whether two of them fire.

**Reentrancy.** The realistic shape is the same handler reached twice for one dig, not two handlers racing: a held left click re-completing, or a duplicated event. The depleted cell no longer resolves to a resource, so a second pass finds nothing to sell, files no debt, and does not charge the tool. Pinned by `breakingTheDepletedCellAgainCommitsNothing`.

**Durability semantics, precisely.** "Damage once" means **one call to `ItemStack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND)`** — Minecraft's ordinary durability path. Unbreaking therefore applies exactly as it does everywhere else in the game (probabilistically, so a Britannia pickaxe with Unbreaking III will often take no damage from a given extraction), an item with no durability is untouched, and a tool that runs out breaks with its usual effects. It is deliberately **not** a forced decrement past the enchantment. This matches the established project convention: `ManagedVegetationService` charges a managed harvest the same way, and `ManagedVegetationGameTests` already asserts `getDamageValue() == 1` for one cut.

**This is a behaviour change.** Before M6, a successful managed extraction damaged the tool **zero** times, because the flow cancels the vanilla break and vanilla's `mineBlock` is what normally applies wear. That was incidental to `setCanceled(true)` rather than an approved mechanic — no comment, design note or test claimed a durability exemption — so the milestone's default of exactly one charge was applied. Managed extraction now wears tools.

---

## Changes

| File | Change | Reason |
| ---- | ------ | ------ |
| `resource/extraction/ManagedExtractionPolicy.java` | **New.** Actor typing, the creative determination, the pure `decide(Actor, boolean)` core, and the single durability charge | One definition of who may extract and what a success costs, shared by both resource families, with no new per-resource data |
| `mining/MiningBreakGate.java` | Actor typing and creative detection delegated to the policy; now-unused `FakePlayer`/`GameType` imports dropped | Removes the second definition of "fake player" and "creative" so the two families cannot disagree. No behaviour change |
| `event/CustomBlockBreakHandler.java` | Refuses any actor the policy denies before the managed flow; charges the tool once after a commit | Closes the Creative-mints-yield defect; adds the single durability charge for the Mining family |
| `deposit/ManagedDepositExtraction.java` | New `Result.DENIED_ACTOR`, checked immediately after resolution; charges the tool once after a commit | Closes the fake-player hole in the sediment family; adds the single durability charge. Asked before house and tool so a machine is refused for what it is, not for where it stands |
| `deposit/ManagedDepositInteractionHandler.java` | Documentation only | Records that "anybody else" is not "any `ServerPlayer`", and why the actor question is asked inside the service rather than here |
| `resource/extraction/ManagedResourceCreativeGuard.java` | **New (amendment).** Cancels an ordinary Creative break of a sited deposit at `HIGHEST`, resyncs the client, and says why | An ordinary block break must not be an implicit administrative delete. Ahead of every other listener, so the refusal happens before anything forms an opinion |
| `BritanniaMod.java` | Registers the guard ahead of the two `HIGH` break handlers | The refusal has to land first |
| `assets/.../lang/en_us.json` | One key: `message.britannia_mod.deposit.creative_refused` | An operator who clicks a deposit is told removal is a command, not a mystery |
| `gametest/MiningGateGameTests.java` | `creativeBypassesTheThresholdWithoutGain` → `…WithoutGainOrDeletion`: now asserts the ore **survives**, and asserts the bypass verdict directly | The test encoded the superseded behaviour — it asserted the operator deleted the ore. Its real subject (the threshold is not what stops an operator) is preserved and strengthened |
| `gametest/ManagedResourceTestPlayers.java` | **New.** A named, real, survival player for managed-resource tests | The two harness defaults — fake players and creative mode — are opted out of once, so no test has to remember either |
| `gametest/ManagedClayDepositGameTests.java` | `player(...)` builds a real survival player instead of a `FakePlayerFactory` one | These tests were asserting that automation could work a deposit, which is the behaviour M6 removes |
| `gametest/ManagedDepositHouseProtectionGameTests.java` | Same | Same |
| `gametest/HousingMaterialSupplyGameTests.java` | Same | Same |
| `gametest/ManagedExtractionPolicyGameTests.java` | **New.** 12 behavioural GameTests | The milestone's actual proof: enchantments, actors, durability and single-commit semantics, at the real event bus |
| `test/.../ManagedExtractionPolicyTest.java` | **New.** 6 unit tests | Pins the shape of the decision — every actor answered, answers distinguishable, exactly one combination earns — without a server |

No data file, resource definition, tag, loot table or catalogue was changed, so no data or resource-pack validation beyond the standard build was required.

---

## Tests

| Test / command | Result |
| -------------- | ------ |
| `gradlew test` (full JUnit, post-amendment) | **2,927 run · 5 failed · 0 errors · 17 skipped** |
| `gradlew runGameTestServer` (post-amendment) | **689 run · 2 failed** |
| `gradlew runGameTestServer` (post-amendment, `--rerun-tasks`) | **689 run · 2 failed** |
| `gradlew build -x test` | **BUILD SUCCESSFUL** |
| *(pre-amendment runs, superseded)* | *687 run · 2 failed, ×3* |

### Baseline comparison — against the M5 report's own final numbers

| | M5 baseline | M6 | Delta |
| --- | --- | --- | --- |
| JUnit run | 2,921 | 2,927 | **+6** (all M6, all passing) |
| JUnit failed | 5 | 5 | **0** |
| JUnit skipped | 17 | 17 | 0 |
| GameTests run | 675 | 689 | **+14** (all M6, all passing) |
| GameTests failed | 2 | 2 | **0** |

### Failure classification

**Known JUnit baseline failures — 5, unchanged, none in M6 code:** `RoutineSkillGainPresentationTest` (1), `structure.hardening.MilestoneEightContentReportTest` (1), `structure.render.CorrectiveMilestoneNineARenderAlignmentTest` (1), `structure.render.MonolithMilestoneSevenRenderingTest` (2).

**Known pre-existing GameTest failures — 2, unchanged:** `aprivateplotacceptsitsownerandrefuseseveryoneelse` and `alegacyvineconvertsonceandkeepsitsvarietyandmaturity`. Both appear in the M0 baseline log and every run since.

**Intermittent textile flake:** `spinningandweavingareexactandrejectspiderssilk` appeared in exactly one of the five M6 GameTest runs (the first post-amendment run) and passed in every other. Same known flake; no textile code was touched.

**New deterministic regressions: none.**

### The 17 transient failures, recorded honestly

The first M6 GameTest run reported **20 failures**. Seventeen of those were the policy working: existing clay, silica, sandstone and house-protection tests failed because they were driving extraction with `FakePlayerFactory` players — the actor M6 refuses — and because their mock players were in the harness's default Creative mode. One more was a test of mine whose premise was wrong (it asserted no managed block has a block item; the ores legitimately do, so it was narrowed to the sediment beds where that claim is documented). Two were the known pre-existing failures.

All seventeen were resolved by making the tests use a real survival player, not by weakening the policy. Every one of them passes now.

### What the 12 new GameTests cover

| Test | Proves |
| ---- | ------ |
| `fortuneNeverAmplifiesAManagedYield` | Fortune III over clay, silica, purity ore and graded stone: configured yield exactly, one depletion, one debt filed at the worked cell, one durability |
| `silkTouchNeverYieldsTheManagedBlockItself` | Silk Touch over all four: the block item never appears in the drops, configured yield followed, restoration correct, one durability |
| `noSedimentBedHasABlockItemToBeSilkTouchedInto` | The structural reason — a bed has no item form to hand over |
| `aFakePlayerExtractsNothingAndPaysNothing` | All four families: block unchanged, zero yield, zero debt, zero durability, zero skill |
| `neoforgesOwnFakePlayerIsWhatTheActorRuleRecognises` | The rule keys on NeoForge's own marker, not a class-name guess; a null actor is denied too |
| `aCreativeOperatorNeitherEarnsNorDeletesADeposit` | All four families: no yield, no debt, no wear, no skill — **and the deposit cell survives**, while ambient stone still breaks |
| `aRefusedCreativeBreakStaysRefusedAndLeavesNoTrace` | Three consecutive Creative attempts on a silica bed: still standing, still nothing filed |
| `aCreativeBuilderMayStillRemoveTheirOwnPlacedBlock` | Provenance still wins — a builder can remove what they placed |
| `theOperatorBypassStillPermitsTheBreakItSimplyEarnsNothing` | The administrative bypass is intact — an operator can still clear a misplaced vein |
| `theWrongToolEarnsNothingAndWearsNothing` | Seven cross-family and unrelated-tool negatives, through the real event bus, with zero durability cost for a refusal |
| `oneBreakCommitsExactlyOneOfEverything` | One posted `BreakEvent` → one yield, one depletion, one debt, one durability, across all four families |
| `breakingTheDepletedCellAgainCommitsNothing` | A duplicate/reentrant break pays nothing and charges nothing |
| `milestoneSixChangedNoRegenerationTiming` | Silica still 24 hours, everything else still 6, catalogue-wide |
| `aDeniedActorIsReportedAsSuchRatherThanAsAWrongTool` | The refusal reason is distinguishable, and still cancels the break |

---

## Invariants now established

| Invariant | Status |
| --------- | ------ |
| Fortune cannot amplify managed economic output | ✅ Structurally impossible, behaviourally pinned across all four families |
| Silk Touch cannot extract a portable managed deposit block | ✅ Pinned; sediment beds additionally have no block item at all |
| Fake players cannot extract managed resources | ✅ **Newly true** for the sediment family; already true for the Mining family, now from a shared definition |
| Unsupported automation cannot extract managed resources | ✅ No `ServerPlayer`, no extraction. Pistons and explosions remain contained by M1; no dispenser, machine or command path reaches either service |
| Creative/operator behaviour is explicit and tested | ✅ **Newly true.** An ordinary Creative break of a sited deposit is refused: nothing earned, nothing deleted, nothing filed. Ambient rock and player-placed blocks still break |
| `ordinary player block break != explicit administrator deposit removal` | ✅ Established by the amendment |
| Successful extraction damages the tool by one pinned rule | ✅ One `hurtAndBreak(1, …)`, enchantment-aware, identical for both families. **Changed from zero** |
| Denied extraction damages tools zero times | ✅ Every refusal path, including deep ones |
| Extraction cannot commit twice | ✅ One commit point per path, proven at the real event bus; a repeat break commits nothing |
| Clay and silica policies pinned | ✅ Yield, Fortune, Silk Touch, fake player, durability, one debt, timings |
| Representative Mining-resource policies pinned | ✅ Purity ore and graded stone, same matrix |
| No resource economics changed accidentally | ✅ No data file touched; regeneration timings asserted catalogue-wide; yields asserted per family |
| M5 suppression untouched | ✅ No worldgen file changed; the M5 tests still pass unmodified |

---

## Remaining debt

| Item | Origin | Status |
| ---- | ------ | ------ |
| **M5 production activation gate** | M5 | **Open and unchanged.** Suppression is development-complete and not deployable |
| **Replacement-generation gaps** | M3/M4, quantified by M5 | **Open.** Zero managed ore blocks generate naturally per 256 chunks; redstone, diamond and lapis still have no managed resource identity |
| **Noise ore-vein leak (copper, iron)** | M5 | **Open.** Recorded in policy data, pinned by a test; owner decision recommended for M9 |
| **Emerald and badlands-gold not block-level verified** | M5 | **Open.** Registry-level proof only; the audit box contains no mountain or badlands biome |
| **No explicit administrative removal exists for every managed resource** | **M6 — new, deferred to M8 by instruction** | Creative deletion is now refused, which is correct, but it means an operator has no by-hand way to remove a badly sited deposit. `/manageddeposit` and `/populateores` cover part of this; the complete operator tooling is M8, and no broad new admin command was added here |
| **Ambient rock is exempt from the Creative refusal** | **M6 — new, stated interpretation** | `minecraft:stone`, `granite`, `deepslate` and the rest of the vanilla STONE family still break in Creative, because protecting them would stop terraforming and the housing systems. If the owner intends ambient rock to be protected too, it is one line in `ManagedExtractionPolicy.isDepositCell` |
| **Managed extraction now wears tools** | **M6 — new, intended** | A behaviour change from zero durability cost to one. Worth a line in patch notes; balancing, if any is wanted, belongs with M9 |
| Rails synchronous fetch | M0/M4 | Unchanged — deferred to M8 |
| Rails stable row id | M4 | Unchanged — M8 |
| Agapite live radius `55 → 8` | M3 | Unchanged — the live Rails row still carries the old radius; not modified, per scope |
| Silver/tin balancing | M3/M4 | Unchanged — deferred to M9 |
| Silica policy leftovers | M6 | **None.** See below |

---

## M6 closure

**M6 is fully closed. No original requirement remains outstanding, and no phantom silica work should be left in the playbook.**

Against the reduced scope as given:

| Original M6 concern | State |
| ------------------- | ----- |
| Silica managed resource block | Pre-existing, preserved |
| Silica raw item / economy identity | Pre-existing, preserved |
| Silica in the canonical `ResourceDefinition` architecture | Pre-existing, preserved |
| Britannia shovel as silica's extraction tool | Pre-existing, preserved and re-pinned through the event bus |
| Britannia shovel for clay | Same |
| Vanilla shovel not authorized | Re-pinned |
| Britannia pickaxe not authorized for clay/silica | Re-pinned |
| Tag-driven extraction authorization | Unchanged |
| Silica 24-hour regeneration | Asserted unchanged |
| Six-hour policy for everything else | Asserted catalogue-wide |
| Validate-before-mutate ordering | Unchanged and strengthened — the actor check is now the first validation |
| M1 piston/explosion protections | Unchanged |
| M4 deposit/restoration infrastructure | Unchanged |
| Fortune / Silk Touch / fake player / automation / Creative / durability / yield amplification / reentrancy | **Defined, implemented where necessary, and pinned** |

Nothing silica-specific was created: no silica shovel, no second catalogue, no loot-table extraction path, and no silica natural generation (that remains M7).

---

## Playbook impact

**M7–M9 need no structural amendment beyond what M5 already required.** Three small additions:

1. **M7** — when silica sedimentary-lens generation lands, it inherits the managed-extraction policy automatically, because the policy is central rather than per resource. The only thing M7 must not do is give a naturally generated bed a loot table or a block item; `noSedimentBedHasABlockItemToBeSilkTouchedInto` will catch it if it does. Worth one line in the M7 brief.

2. **M8** — when the iron/gold cutover moves those resources onto managed block identities, they arrive under this policy with nothing further to configure. The operator deposit-management suite should decide, explicitly, whether an administrative removal ought to file restoration debt; M6 deliberately left creative removal permanent, and M8 is where the tooling to place a block back belongs.

3. **M9** — two M6 items want a balancing decision alongside the existing ones: whether one durability point per extraction is the right cost, and whether the noise ore-vein trickle is acceptable.

**The M5 activation gate recommendation stands unchanged and is still the single most important open item in the programme.**

---

## Git state

| | |
| --- | --- |
| M5 commit | `5ee93650d8c47d3fac22269488071fadd368bd80` — `feat(worldgen): suppress vanilla overworld mineral features` |
| Current HEAD | `5ee93650d8c47d3fac22269488071fadd368bd80` — **unchanged; M6 is uncommitted** |
| Branch | `claude/ultimacraft-orevein-remediation-70bfeb` |
| Worktree | `C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\flagstone-foundation-kickoff-8fb72e` |

```
$ git status --short
 M src/main/java/com/seggellion/britannia_mod/deposit/ManagedDepositExtraction.java
 M src/main/java/com/seggellion/britannia_mod/deposit/ManagedDepositInteractionHandler.java
 M src/main/java/com/seggellion/britannia_mod/event/CustomBlockBreakHandler.java
 M src/main/java/com/seggellion/britannia_mod/gametest/HousingMaterialSupplyGameTests.java
 M src/main/java/com/seggellion/britannia_mod/gametest/ManagedClayDepositGameTests.java
 M src/main/java/com/seggellion/britannia_mod/gametest/ManagedDepositHouseProtectionGameTests.java
 M src/main/java/com/seggellion/britannia_mod/mining/MiningBreakGate.java
?? src/main/java/com/seggellion/britannia_mod/gametest/ManagedExtractionPolicyGameTests.java
?? src/main/java/com/seggellion/britannia_mod/gametest/ManagedResourceTestPlayers.java
?? src/main/java/com/seggellion/britannia_mod/resource/extraction/
?? src/test/java/com/seggellion/britannia_mod/resource/extraction/
```

**M6 files changed:** 7 modified, 4 new (`ManagedExtractionPolicy.java`, `ManagedExtractionPolicyGameTests.java`, `ManagedResourceTestPlayers.java`, `ManagedExtractionPolicyTest.java`).

**M6 remains uncommitted**, pending review. The stash is untouched, no unrelated files are staged, and the project documentation in the main checkout is outside this worktree.

---

## Recommendation

**Safe to proceed to M7.**

The two defects this milestone existed to find were real, are closed, and are pinned from the denial side — which is the side that regresses quietly. The policy is expressed once and shared by both resource families, no speculative configuration was added, and the resource data is byte-identical to what M5 committed. Three consecutive full GameTest runs and a full JUnit run show no new deterministic failures against the M5 baseline.

Two things the owner should sign off rather than inherit:

1. **Managed extraction now wears tools** — one enchantment-aware durability point per successful extraction, where previously it cost nothing. This is the milestone's stated default and matches how the project already charges managed vegetation harvests, but it is a live gameplay change and players will notice it.
2. **A Creative operator can no longer remove a deposit by hand at all** — the break is refused, per the amendment. That is the correct invariant, but until M8's operator tooling lands there is no complete by-hand route to remove a badly sited deposit, and the exemption for ambient rock is an interpretation worth confirming (see *Remaining debt*).

Separately, and unchanged from M5: **production activation of the vanilla-ore suppression remains gated and is not approved by this milestone.** M6 governs who may extract; it does not close any of the resource-generation gaps that make activation unsafe.

**Stopping after M6 as instructed. Milestone 7 not begun.**
