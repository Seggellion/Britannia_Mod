# Rowan the Farmer — “From Soil to Supper”
## Multi-Milestone Cross-Repository Project Playbook

**Project:** UltimaCraft farming onboarding and quest-interface polish  
**Primary runtime:** Minecraft 1.21.1, NeoForge 21.1.72, Java 21  
**Minecraft repository:** `C:\projects\britannia\mod\Britannia_Mod`  
**Rails repository:** `\\wsl.localhost\Ubuntu\home\dusti\ultimacraft-website` (`/home/dusti/ultimacraft-website` inside Ubuntu WSL)  
**Quest giver:** Rowan the Farmer  
**Questline:** From Soil to Supper  
**Status:** Implementation playbook  

---

## 1. Project outcome

Add a polished five-quest farming apprenticeship that takes a new ordinary Adventure player from an empty inventory through the first successful harvest.

The project must use the existing Rails quest engine, server-authoritative Minecraft quest bridge, quest dialogue, rewards, achievements, and `QuestGiverSpawnBlock`. It may extend those systems where the discovery proved a required capability is missing. It must not depend on the general QuestSystemV2 rewrite, quest importer, or diagram editor.

The complete player route is:

1. Accept work from Rowan and receive a Britannia Shovel.
2. Collect dung and earn gold.
3. Gather custom Dirt and receive copper, two custom wooden bowls, and a metal bucket.
4. Fill the bucket at a public Water Well and receive silver, a watering can, and a random entry-level crop seed.
5. Mix Fertilized Dirt and receive gold and a Farming Hoe.
6. Prepare a public plot, fertilize it, plant the awarded seed, care for it, harvest it, earn gold, and unlock the First Harvest achievement.

The sequence is delivered as five linked quests because the current prerequisite mechanism already sequences separate quests and each quest ending provides a clear reward-claim boundary.

---

## 2. Fixed product decisions

These decisions are approved project requirements and must not be reopened during implementation:

1. The NPC is **Rowan the Farmer**.
2. The questline is **From Soil to Supper**.
3. Rowan must be an archetype available through the current `QuestGiverSpawnBlock` configuration screen and spawn lifecycle. Administrators may place multiple Rowans anywhere. Do not create a dedicated Farmer spawn block or a fixed Rowan location.
4. Multiple placed Rowans share the same Rails-backed progress for a player. A player may accept at one Rowan and continue or claim at another.
5. Public farming infrastructure will be supplied by operators. World-building, well construction, plot placement, and general world generation are outside implementation scope.
6. The metal bucket is intentionally incidental. It is awarded after quest 2, filled during quest 3, and has no required farming use afterward. Do not add bucket-to-bowl or bucket-to-watering-can mechanics.
7. Fertilizer mixing continues to use two custom wooden bowls. One bowl is filled directly from a supported water source or public Water Well.
8. The journey begins with an ordinary non-operator player in the normal enforced game mode, with an empty inventory, no currency or bank assets, no property, and ordinary beginner skill data.
9. The final endpoint is a successful first harvest and achievement, including persistence after reconnect.
10. UX polish is required functionality. Clear instructions, progress, controls, feedback, recovery, responsive layout, and accessible status cues are acceptance requirements.
11. Quest objectives remain server-authoritative. The client must never be allowed to assert that a farming objective succeeded.
12. Work applies to the NeoForge Britannia mod. Do not modify the Fabric/Atrevion repository.

---

## 3. Authoritative discovery inputs

Before implementation, read these documents completely:

- `docs/projects/rowan-farming-questline/ROWAN_FARMING_QUESTLINE_DISCOVERY.md`
- `docs/projects/rowan-farming-questline/ROWAN_FARMING_QUESTLINE_PLANNING_INPUTS.md`
- `docs/projects/rowan-farming-questline/ROWAN_FARMING_QUESTLINE_ACCEPTANCE_DRAFT.md`
- `FARMING_GAMELOOP_DISCOVERY.md`, if present
- `FARMING_EMPTY_INVENTORY_TEST_CHECKLIST.md`, if present
- Applicable repository guidance and testing documentation in both repositories

Use current source and configuration as the implementation authority. The discovery recorded NeoForge `0cd566dd811bb717c6bda498ac3bed38be64b95e` and Rails `5b4a22d7f5344c77584983c8c15872ff47e033d3`; verify current HEADs because these are historical snapshots.

The discovery confirmed three blocking shared defects:

- All quest-granted items are stamped as temporary quest items and later deleted when their quest leaves the journal.
- Non-ending item grants are not replay-protected, so the acceptance shovel can be lost after a committed Rails transition and lost response.
- The quest-giver spawn configuration payload lacks a server-side permission check.

It also confirmed that farming actions other than dung harvesting do not publish quest events, public plots do not record the planter, the current journal cannot display stage progress, several interactions fail silently, silver/copper localization is missing, and the skill loader does not recover from a transient login failure.

---

## 4. Repository and change-control rules

### 4.1 Baseline discipline

At the start of every milestone:

1. Record repository root, branch, full HEAD, upstream relationship, worktree status, and relevant runtime versions for both repositories.
2. Read applicable `AGENTS.md` and repository guidance files.
3. Preserve all owner changes. Never reset, clean, stash, overwrite, or absorb unrelated work.
4. Use isolated worktrees for both repositories when the canonical checkout is dirty or active elsewhere.
5. Confirm the NeoForge starting point contains all accepted Patch 18 work, including any work on a divergent published lineage. Do not silently omit newer accepted weapon or farming changes.
6. Use a disposable Rails test database. Never run the Rowan seed, migrations, or tests against development or production data unless the relevant milestone explicitly authorizes a controlled deployment step.
7. Do not print shard secrets, database credentials, or player data.

Recommended local branch names:

```text
claude/rowan-farming-questline-mod
claude/rowan-farming-questline-rails
```

If an existing authorized project worktree already uses different names, retain it and record the exact state.

### 4.2 Commit discipline

- Commit each completed milestone separately in the repository or repositories it changes.
- Use narrow commits with tests and documentation included.
- Record both repository SHAs in the milestone closeout and project scratchpad.
- Do not push, merge, deploy, seed staging/production, or upload external portrait assets unless separately authorized.
- A milestone that changes the Rails/NeoForge contract is incomplete until both implementations and the contract tests agree.

### 4.3 Project scratchpad

Maintain `docs/projects/rowan-farming-questline/ROWAN_FARMING_QUESTLINE_IMPLEMENTATION_STATUS.md` with:

- current milestone and status;
- starting and ending SHAs in both repositories;
- decisions and contract versions;
- files changed;
- test commands and exact results;
- defects discovered;
- deferred live evidence;
- next safe action.

---

## 5. Required quest and item flow

| Quest | Acceptance and objective | Completion rewards | Items retained for later |
| --- | --- | --- | --- |
| **1. Shovel Dung** | “I’ll do it” grants exactly one `britannia_mod:britannia_shovel`. The player completes one authoritative tracked-dung harvest. The UI teaches holding the shovel. | Gold coins | Shovel and collected Dung |
| **2. Shovel Dirt** | Gather one `britannia_mod:dirt` from valid terrain with the Britannia Shovel; terrain remains unchanged. | Copper coins, two `britannia_mod:empty_bowl`, one `minecraft:bucket` | Dung, Dirt, bowls, bucket, shovel |
| **3. Fill the Water Bucket** | Fill the issued bucket at a supported public Water Well. Natural-water failure in Adventure receives useful guidance. | Silver coins, one full `britannia_mod:watering_can`, one seed selected once from the configured eligible entry-level pool | Water Bucket, watering can, awarded seed, all earlier materials |
| **4. Mix Fertilized Dirt** | Complete the custom bowl sequence and produce `britannia_mod:fertilized_dirt`. | Gold coins, one `britannia_mod:farming_hoe` | Fertilized Dirt, two recovered bowls, tools, watering can, seed |
| **5. Plant Seeds and Harvest** | On one qualifying public plot: hoe, fertilize, plant the awarded crop, reach its intended moisture state, then harvest the same attributed crop cycle. | Gold coins, website achievement, in-game toast, persistent advancement | Produce, reusable equipment, incidental Water Bucket |

No claim may confiscate Dung, Dirt, a seed, Fertilized Dirt, bowls, or equipment needed later. Coin rewards are permanent currency.

The configured seed pool must be resolved from the current authoritative crop definitions. Every entry must be plantable at the beginner skill requirement, compatible with intended Rowan placement conditions, and harvestable using the equipment the questline has already issued. Implement durable random selection even when the initial validated pool has one entry. Reopening dialogue, retrying a response, reconnecting, or moving to another Rowan must never reroll the seed.

Use the following reward quantities as provisional seed constants only when no newer approved economy values exist:

```text
Quest 1: 2 gold
Quest 2: 50 copper
Quest 3: 20 silver
Quest 4: 3 gold
Quest 5: 5 gold
```

Keep them centralized and easy to revise before deployment. Record the actual values used and the evidence supporting them.

---

## 6. Cross-repository architecture requirements

### 6.1 Permanent rewards versus temporary quest items

Permanent equipment, seeds, bowls, produce, and currency must not use cleanup metadata intended for temporary objective items.

Introduce an explicit temporary-item contract. Only a grant intentionally marked temporary may receive cleanup metadata. Preserve cleanup for real pickup/destroy objective items. Include a conservative migration for legacy stamped items so an upgrade does not delete existing ordinary rewards. Test existing escort rewards as part of this repair.

### 6.2 Durable reward delivery

Rails transition replay and Minecraft inventory delivery form one workflow. A committed transition must produce a stable delivery identity and a durable pending delivery that remains discoverable until acknowledged.

Required properties:

- A Rails transaction that grants items creates or updates a durable delivery record in the same transaction as the quest transition.
- A retry using the same request UUID returns the same delivery identity and payload.
- Grant-bearing non-ending transitions receive the same replay protection as endings.
- NeoForge keeps a persistent per-player record of applied delivery identities and a persistent pending queue when immediate insertion is impossible.
- Login and journal/bootstrap recovery fetch unacknowledged deliveries.
- Applying the same delivery more than once never duplicates items.
- Rails acknowledges delivery only after NeoForge records durable local application or a durable pending inventory delivery.
- Full inventory never turns a required reward into a despawning ground item. The UI may preflight space, but the server owns the decision and durable fallback.
- Disconnect, response timeout, reconnect, server restart, and repeated claim clicks result in exactly one delivery.

Do not claim mathematically perfect exactly-once semantics without proving the actual save boundaries. Document the concrete failure model and make the tested behavior loss-free and duplicate-resistant across supported restarts.

### 6.3 Durable server-authoritative objectives

Add a backward-compatible action-objective contract to the journal. Preserve existing location, pickup, destroy, direct, kill, and legacy payload behavior.

Each farming action event must carry a stable server-created event UUID, player/shard scope, action type, target/resource/crop identity, relevant tool/container identity, dimension and position where applicable, and a minimal context needed to validate the objective. Client-authored objective messages remain rejected.

NeoForge must persist an objective outbox before or with the authoritative event submission. Failed submissions retry across logout and server restart. Rails processes each event idempotently and returns an explicit applied, duplicate, irrelevant, stale, or rejected result. A legitimate harvest during a Rails outage must not require the player to grow another crop merely because the HTTP request failed.

### 6.4 Help and dialogue state

Opening “What do I do next?” or the mixing guide must not move the player away from the node that owns the active observer. Implement help as presentation-only navigation or otherwise prove that the active action trigger remains subscribed. Closing help, pressing Escape, or reconnecting must not disable objective tracking.

### 6.5 Farming attribution

Persist at least:

- planter UUID;
- crop ID;
- a unique planting/crop-cycle identity;
- plot dimension and position;
- community/private provenance already needed by farming rules.

Quest 5 must associate its ordered progress with one qualifying plot and crop cycle. Produce picked up from another player, a crop planted by another player, or a different crop cycle must not complete the apprenticeship. Normal public-farm harvesting rules may remain unchanged; quest credit is stricter than general permission.

Clear or rotate attribution at the same transaction boundary that clears or replants the crop. Preserve it through chunk unload, save/restart, and growth.

---

## 7. Milestone plan

## M0 — Baselines, worktrees, and executable contracts

### Objective

Establish safe cross-repository implementation branches and freeze the tested contracts before feature code begins.

### Work

1. Verify current NeoForge and Rails baselines, including divergent branch ancestry and accepted Patch 18 changes.
2. Create or select isolated worktrees without disturbing dirty canonical checkouts.
3. Read all discovery inputs and verify every cited implementation seam still exists.
4. Run the focused baseline suites from the discovery and the repository-standard full gates where practical.
5. Copy the acceptance draft into the project directory if it is not already there; keep every manual checkbox unchecked.
6. Add an explicit protocol document describing reward deliveries, action events, result codes, and compatibility behavior.
7. Create the implementation-status scratchpad.

### Gates

- No unrelated diff enters either project worktree.
- Baseline test totals and pre-existing failures/skips are recorded exactly.
- The intended NeoForge base includes all accepted Patch 18 work.
- Contract fields, ownership, retry identities, and save/recovery behavior are written before migrations or payload code.

### Suggested commits

```text
docs(quests): define Rowan farming integration contracts
```

---

## M1 — Quest security and permanent-item safety

### Objective

Repair the mod defects that could delete rewards or let an ordinary client reconfigure quest givers.

### NeoForge work

1. Replace blanket quest-item stamping with explicit temporary-item semantics.
2. Add conservative compatibility handling for legacy stamped permanent rewards.
3. Retain cleanup for real temporary pickup/destroy objective items.
4. Add server-side permission, reach, block-entity, and payload validation before applying a quest-giver spawn configuration.
5. Reject and log unauthorized configuration attempts without changing the block or entity.
6. Add missing localized display names for copper and silver coins and the existing quest/menu keybinding.

### Required tests

- Permanent shovel, bowls, bucket, watering can, seed, hoe, and coins survive quest completion, quit where applicable, journal refresh, and relog cleanup.
- Explicit temporary quest items still clean up at the intended lifecycle boundary.
- Existing escort silver survives relog.
- Permission-level-0 clients cannot apply a spawn configuration, including crafted packets.
- Authorized configuration continues to work.

### Gate

No Rowan reward work begins until permanent items survive the complete existing cleanup lifecycle.

### Suggested commit

```text
fix(quests): protect permanent rewards and quest-giver configuration
```

---

## M2 — Rails reward-delivery ledger and replay

### Objective

Make every grant-bearing quest transition recoverable and replay-safe in Rails.

### Rails work

1. Add the minimum durable delivery model/schema needed by the M0 contract, with stable UUIDs and unique constraints for shard, player, quest state, and transition/request identity.
2. Create delivery rows inside the locked quest transition transaction.
3. Persist and replay the same result for every response containing granted items, including the stage-1 acceptance shovel.
4. Add authenticated endpoints or bootstrap fields to list pending deliveries and acknowledge them.
5. Validate item IDs, positive bounded counts, delivery scope, and acknowledgement ownership.
6. Keep old clients compatible during the rollout window.

### Required tests

- Lost acceptance response followed by the same request UUID returns the same shovel delivery.
- Concurrent and repeated claims create one delivery.
- A different request cannot claim an already-completed transition twice.
- A pending delivery remains available across a new request/session until acknowledged.
- Acknowledgement is idempotent and cannot cross player or shard scope.
- Existing ending-completion and achievement tests remain green.

### Gate

Rails can demonstrate a durable unacknowledged delivery independent of a live Minecraft client.

### Suggested commit

```text
feat(quests): persist replayable reward deliveries
```

---

## M3 — NeoForge reward reconciliation

### Objective

Complete the durable delivery workflow in the Minecraft server.

### NeoForge work

1. Parse stable delivery identities without breaking legacy responses.
2. Maintain a bounded persistent per-player applied-delivery ledger.
3. Maintain a persistent pending queue for rewards that cannot immediately fit.
4. Reconcile pending Rails deliveries on login, journal refresh, and an appropriate bounded retry schedule.
5. Apply each delivery once on the server thread, persist the applied/pending decision, then acknowledge Rails.
6. Present clear claim and inventory-space states. Avoid ground-drop delivery for mandatory questline rewards.
7. Add structured, non-secret observability for created, replayed, applied, queued, acknowledged, and rejected deliveries.

### Required tests

- Response timeout, repeated response, logout, relog, and server restart produce exactly one local grant.
- Full inventory retains a durable pending reward and delivers after space is available.
- A second Rowan cannot duplicate the same delivery.
- Legacy reward responses still work during compatibility.
- Malformed or out-of-scope deliveries are rejected without item mutation.

### Cross-repository gate

Run a local Rails-backed test in a disposable database and isolated Minecraft server. Commit both sides only after the delivery ID and acknowledgement flow agree.

### Suggested commit

```text
feat(quests): reconcile durable reward deliveries
```

---

## M4 — Rails action-objective and progress contract

### Objective

Add a generic, backward-compatible Rails contract for authoritative farming actions and ordered progress.

### Rails work

1. Publish `action_trigger` metadata alongside existing trigger types.
2. Add an authenticated action-event endpoint or extend the existing trigger endpoint according to the M0 contract.
3. Store processed event identities so retries return a deterministic duplicate/applied result.
4. Support ordered progress steps and compact state attributes without adding a general QuestSystemV2 dependency.
5. Support correlation fields needed for quest 5: plot key, crop ID, planter, crop-cycle ID, and hydration/care state.
6. Publish journal fields for stage label, next action, ordered progress, profession, reward previews, and keep-for-later items.
7. Filter server-only observer geometry and validation metadata from client-facing responses.
8. Add deterministic random-item selection whose result is stored with the transition and can be inherited by the later linked quest.

### Required tests

- Old journal consumers still receive valid existing fields.
- Action events are authenticated, server-scoped, schema-validated, and idempotent.
- Duplicate event UUIDs do not advance twice.
- Events from the wrong player, shard, action, item, crop, plot, or stage do not advance.
- The selected seed is stable through response replay, reconnect, and stage inheritance.
- Client-facing payloads omit server-only validation data.

### Suggested commit

```text
feat(quests): add durable action objectives and stage progress
```

---

## M5 — NeoForge farming events, outbox, and crop attribution

### Objective

Connect real farming successes to the Rails action contract without trusting client claims.

### NeoForge work

1. Extend the existing dung harvest event bridge.
2. Publish typed server events at the authoritative success points for:
   - tracked dung harvest;
   - custom Dirt gathering;
   - Water Well bucket filling;
   - Bowl of Dirt preparation;
   - Bowl of Fertile Dirt preparation;
   - Bowl of Water filling;
   - final Fertilized Dirt mixing;
   - public plot hoeing;
   - public plot fertilizing;
   - crop planting;
   - qualifying watering/care;
   - mature crop harvesting.
3. Add planter and crop-cycle attribution to the farming block entity and save data.
4. Add a persistent server-owned objective outbox with stable event UUIDs and bounded retry/backoff across logout and restart.
5. Extend the quest watcher to match action triggers and publish progress without accepting client assertions.
6. Keep Help and mixing-guide navigation presentation-only so active observers remain installed.
7. Add clear result handling for applied, duplicate, irrelevant, stale, temporarily unavailable, and rejected events.
8. Revalidate tracked dung support during harvest or document and test the chosen compatible correction.

### Required tests

- Every event fires once only after the real mutation succeeds.
- Failed, denied, cooled-down, stale, wrong-hand, or wrong-item interactions post no success event.
- Outage at harvest persists the event; reconnect/restart retries it and advances once.
- Plant as player A and harvest as player B gives B no quest credit; A’s qualifying crop cycle remains distinguishable.
- Another crop, another plot, dropped produce, or inventory pickup cannot complete quest 5.
- Attribution persists through chunk unload and server restart and clears/rotates when the crop cycle ends.
- Existing location, pickup, destroy, escort, and kill objectives remain green.

### Suggested commit

```text
feat(farming): publish durable quest actions and planter attribution
```

---

## M6 — Rowan archetype and QuestGiverSpawn integration

### Objective

Make Rowan placeable anywhere through the current administrative workflow.

### NeoForge work

1. Add Rowan to the existing QuestGiverSpawn archetype list with exact quest API identity `Rowan`.
2. Assign the Farmer profession label and an appropriate existing apron/boots outfit for the chosen gender configuration.
3. Preserve current block placement restrictions, saved configuration, respawn snapshot, wander radius, and invisibility rules.
4. Add an optional per-spawner direction/help string if the existing configuration cannot give useful location guidance. Validate length and sanitize control characters server-side.
5. Ensure a placed Rowan, respawned Rowan, and second Rowan all resolve the same Rails `origin_npc` while retaining instance-specific directions.
6. Use the current portrait loading convention and generic fallback. Record the external `Rowan.png` requirement without blocking code validation.

### Required tests

- Authorized administrator places and configures Rowan using the existing block.
- Rowan survives chunk reload and respawns with the same name, profession, outfit, gender, radius, and hint.
- Two Rowans share player progression but can show different local directions.
- Ordinary players cannot see/configure the spawn block or forge its configuration payload.
- Existing quest-giver archetypes remain unchanged.

### Suggested commit

```text
feat(quests): add Rowan to quest-giver spawn blocks
```

---

## M7 — Rails Rowan content, admin support, and rewards

### Objective

Install the five linked quests as durable, idempotent Rails content.

### Rails work

1. Add an idempotent `rowan_farming_questline` seed and a narrow rake task following repository conventions.
2. Upsert five non-repeatable quests keyed to `origin_npc = "Rowan"`, with descending priority and resolved prerequisite IDs.
3. Use stable node titles and deterministic choice keys. Never regenerate identifiers that can strand active players.
4. Author Offer, Working, Help presentation, Done, and Complete states without moving active observers when Help opens.
5. Grant the stage-1 shovel only when the player chooses “I’ll do it.” “Not now” and Escape grant nothing and do not leave a misleading accepted journal entry.
6. Configure permanent rewards and keep-for-later metadata exactly as specified in section 5.
7. Select and persist the random entry-level seed once. Carry its item/crop identity into quest 5.
8. Add the smallest admin form support needed for description, active status, action observers, stage/progress fields, reward previews, keep-for-later items, and validation. Do not build the general diagram editor in this project.
9. Validate quest definitions at seed time: item IDs, counts, prerequisite chain, trigger keys, supported progress actions, reward previews matching effects, and pool eligibility.

### Required tests

- Running the seed twice produces the same five quests, nodes, choices, and prerequisite chain.
- A player can never receive stage 2 before completing stage 1.
- “Not now,” Escape, acceptance, working, completion, claim, abandon, and resume behave deliberately.
- Each reward payload matches its preview and keep-for-later copy.
- Seed selection and quest-5 crop matching survive replay and reconnect.
- Multiple Rowan instances do not create duplicate states.

### Suggested commit

```text
feat(quests): add From Soil to Supper questline
```

---

## M8 — Dialogue and quest-journal UX

### Objective

Give the apprenticeship a clear, polished, resilient player interface.

### NeoForge work

1. Show Rowan’s portrait, name, Farmer profession, concise body copy, and selectable actions using existing visual language.
2. Add labeled icon sections for:
   - **You receive now**;
   - **Reward when done**;
   - **Keep for later**.
3. Display quest number, next action, ordered progress, and return-to-Rowan state in the journal.
4. Replace farming objective auto-popups with a quiet toast/action-bar notice and claim at Rowan. Correct the hardcoded “The Guardian” attribution for all relevant quest results.
5. Implement “What do I do next?” as non-mutating presentation. Include instance-specific directions when configured.
6. Add a visual mixing guide with main hand, off hand, gesture, ingredients, outputs, and both returned bowls. Do not mention the bucket in the mixing guide.
7. Use actual rebound key names in instructions.
8. Add useful messages for wrong hand order, incorrect bowl/Dirt, flowing water, natural-water bucket failure, protected well, unavailable skills, pending backend confirmation, occupied plots, expiry countdowns, inventory space, and crop loss.
9. Make dialogue text scroll/clamp safely; make the journal responsive; add keyboard focus and a Quit confirmation.
10. Put all new player-facing strings in localization keys. Use text labels and symbols as well as color.

### Visual acceptance

- GUI scales 1, 2, 3, and 4.
- A 640 × 360 window.
- Long localized strings and three visible choices.
- Mouse and keyboard navigation.
- Rebound controls.
- No text outside the parchment/journal bounds.
- Reward icons and tooltips resolve actual item names.
- No raw registry keys or missing translation keys.

### Suggested commit

```text
feat(quests): polish Rowan dialogue and journal progress
```

---

## M9 — Timing, skill recovery, and player recovery

### Objective

Remove avoidable failure traps while keeping the farming mechanics meaningful.

### Work

1. Make public preparation expiry deterministic and enforce the same deadline at planting.
2. Use the recommended starting windows unless newer approved values exist:
   - 10 minutes from hoeing to fertilizing;
   - 5 minutes from fertilizing to planting.
3. Show countdown warnings at useful thresholds and do not consume fertilizer or seed after an already-expired state.
4. Retry unavailable Farming skill data with bounded backoff; distinguish a legitimate Farming value of zero from unavailable data.
5. Schedule a normal tracked dung placement attempt in a bounded area near the interacting Rowan when quest 1 is accepted. Preserve caps, spacing, support rules, and ordinary randomness; do not command-place an untracked pile.
6. Teach moisture state based on the actual crop and current plot state. Do not hardcode “water twice” when rain or another interaction changed hydration.
7. Add bounded, Rails-tracked recovery choices for mandatory noncurrency tutorial equipment. Check the player’s carried inventory before reissue, state the limitations around external storage, and never reissue coin rewards.
8. Let renewable ingredients be regathered/remixed. Preserve completed earlier quests when a later farming attempt fails.
9. Provide recovery when another player harvests the attributed public crop, without awarding credit for the other player’s action.

### Required tests

- Expiry behaves at `randomTickSpeed` 0 and normal speed.
- A failed skill fetch followed by recovery allows planting without relog.
- No valuable input is consumed after an expired or unavailable prerequisite.
- Dung scheduling creates a tracked eligible attempt and respects caps.
- Rain/other watering does not make instructions false.
- Reissue limits persist through reconnect and quest restart and cannot duplicate currency.
- Losing fertilizer or a crop does not reset completed stages 1–4.

### Suggested commits

```text
fix(farming): make tutorial timing and skill recovery predictable
feat(quests): add bounded apprenticeship recovery
```

---

## M10 — Achievement and completion experience

### Objective

Complete the apprenticeship coherently across Rails and Minecraft.

### Work

1. Grant the website quest achievement through the existing Rails achievement service.
2. Show the existing in-game challenge-style achievement toast and sound.
3. Add and grant a persistent `britannia_mod:quest/first_harvest` advancement at the same authoritative completion boundary.
4. Make all three results idempotent under response replay and repeated claims.
5. Show a final Rowan dialogue that acknowledges the harvest and closes the questline gracefully.
6. Ensure the public achievement policy and player/shard attribution are correct.

### Required tests

- One qualifying completion creates one Rails achievement, one client action, and one advancement.
- Replay and repeated claim do not duplicate any achievement or reward.
- Wrong player, wrong shard, provisional/unresolved identity, and nonqualifying harvest cases follow existing policy safely.

### Suggested commits

```text
feat(quests): award the First Harvest achievement
```

---

## M11 — Cross-repository hardening and automated acceptance

### Objective

Prove the five quests and the repaired shared engine work together before live testing.

### Automated gates

1. Run all repository-standard Rails tests in a disposable database, plus new migrations, seeds, delivery, action-event, security, and achievement tests.
2. Run NeoForge compilation, focused unit tests, the complete unit suite, and registered GameTests.
3. Add meaningful GameTests covering real block/item dispatch rather than calling helpers directly:
   - permanent reward relog;
   - unauthorized spawn config;
   - acceptance shovel replay;
   - each farming event boundary;
   - objective outbox retry across simulated restart;
   - full-inventory pending delivery;
   - planter/crop-cycle attribution;
   - deterministic preparation expiry;
   - multiple Rowan instances.
4. Add contract fixtures shared or mirrored between repositories and verify schema/version compatibility.
5. Run `git diff --check` and resource/localization validation.

### Diagnostic integration

Run a local Rails-backed server with a disposable database and test identity. Use fixtures only to isolate component boundaries, and label them as diagnostics. Do not mark the empty-inventory route passed from a fixture kit.

### Gate

All new automated tests and established regression suites pass. Any pre-existing skip or failure is documented and shown unrelated. Both repositories have clean project diffs and milestone commits.

### Suggested commits

```text
test(quests): validate Rowan farming integration
docs(quests): prepare Rowan live acceptance
```

---

## M12 — Live acceptance and release readiness

### Objective

Execute the full ordinary-player journey and prepare a reviewable release candidate.

### Prerequisites

- All M0–M11 gates pass.
- A Rowan portrait is available or the approved generic fallback is accepted for the test.
- Operators have placed Rowan through the existing spawn blocks and supplied eligible dirt, a public Water Well, and Community Farm Blocks.
- Farming skills and Rowan quest content are seeded in the target environment through approved procedures.
- Region/climate and `randomTickSpeed` are recorded.

### Manual acceptance

Execute every applicable row in `ROWAN_FARMING_QUESTLINE_ACCEPTANCE_DRAFT.md`, including:

- empty-inventory start;
- declining and accepting quest 1;
- reconnect after acceptance;
- every acquisition, objective, and reward;
- correct bowl mixing and wrong-hand feedback;
- the incidental bucket path;
- one qualifying plant/care/harvest cycle;
- claim and achievement;
- two placed Rowans;
- another-player attribution checks;
- backend interruption at objective and claim;
- full inventory;
- repeated clicks and responses;
- lost-item recovery;
- GUI scales, small window, keyboard, localization, and rebound keys;
- relog after every permanent reward.

Record observed inventory accounting, coordinates, timings, UI screenshots, logs, and persistence results. Expected source behavior is not live evidence.

### Release outputs

- Final NeoForge and Rails SHAs.
- Clean worktree status or an exact explanation of preserved unrelated changes.
- Test totals.
- Candidate JAR name, size, SHA-256, mod version, and `git.dirty` state.
- Rails migration and seed list.
- Deployment order and rollback plan.
- Operator setup guide for Rowan blocks and required public infrastructure.
- Completed acceptance checklist and remaining external gates.

Do not push, merge, deploy, run production seeds, or upload the external portrait without authorization. Prepare every artifact first so those actions are the final reviewable steps.

---

## 8. Required failure and recovery behavior

| Situation | Required result |
| --- | --- |
| Rails response lost after accepting quest 1 | The same shovel delivery is recovered once. |
| Player reconnects after completing a stage | Permanent items and coins remain; next quest is offered. |
| Player inventory is full during claim | Reward remains durably pending and is delivered after space is available. |
| Rails is unavailable when an objective succeeds | Server persists the action and retries; the player does not repeat the successful action solely due to HTTP failure. |
| Player opens Help or mixing guide | Active objective remains subscribed and completable. |
| Wrong bowl/hand order | No mutation; concise corrective feedback. |
| Bucket used on natural water in Adventure | No mutation; guidance points to the public Water Well. |
| Skill data is temporarily unavailable | Valuable inputs remain; automatic retry and useful status. |
| Preparation window expires | Deterministic reset; no late planting; clear recovery instructions. |
| Another player harvests the crop | No false quest credit; original player receives a recovery route. |
| Same delivery/event is replayed | One reward/objective application. |
| Unauthorized spawn-config packet | Rejected and logged; Rowan configuration unchanged. |

---

## 9. Definition of done

The project is complete only when:

1. A new non-operator player begins empty and completes all five quests using only normal gameplay and operator-provided public infrastructure.
2. Rowan is placed and configured through the existing QuestGiverSpawn Block and works from multiple locations.
3. The bucket is used only for its intended quest and is absent from later fertilizer instructions.
4. Every permanent reward survives completion, reconnect, journal cleanup, and server restart.
5. Acceptance and completion grants are recoverable, replay-safe, and duplicate-resistant.
6. All farming objectives originate from server-confirmed actions and survive Rails interruption.
7. Quest 5 credits only the correct player, awarded crop, plot, and crop cycle.
8. Dialogue and journal surfaces clearly show the current stage, next action, progress, immediate equipment, completion rewards, and retained materials.
9. Help, dismissal, reconnect, full inventory, unavailable skills, expired plots, and lost supplies have tested behavior.
10. First Harvest appears through the Rails achievement, in-game toast, and persistent advancement exactly once.
11. Rails and NeoForge automated suites pass, GameTests are registered and executed, and the ordinary-player acceptance checklist is completed with observed evidence.
12. Both repositories have reviewable milestone commits, accurate handoff documentation, and no unrelated work changed.

---

## 10. Final handoff format

At the end of every milestone and the project, report:

```text
Milestone:
Verdict:

NeoForge branch / starting HEAD / ending HEAD:
Rails branch / starting HEAD / ending HEAD:
Working-tree state in both repositories:
Commits created:

Problem solved:
Implemented behavior:
Protocol/schema changes:
Compatibility behavior:
Files changed by repository:

Tests run:
Exact totals:
GameTests registered and executed:
Manual evidence:

Security result:
Reward durability result:
Objective durability result:
Attribution result:
UX/accessibility result:

Known limitations:
Live gates remaining:
Next safe action:
```

Do not report a source-inspected, fixture-driven, or expected result as live acceptance.

