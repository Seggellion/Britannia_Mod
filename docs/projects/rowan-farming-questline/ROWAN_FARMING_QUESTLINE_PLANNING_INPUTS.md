# Rowan the Farmer — "From Soil to Supper" planning inputs

Companion to [ROWAN_FARMING_QUESTLINE_DISCOVERY.md](ROWAN_FARMING_QUESTLINE_DISCOVERY.md) (evidence,
line references, capability matrix) and
[ROWAN_FARMING_QUESTLINE_ACCEPTANCE_DRAFT.md](ROWAN_FARMING_QUESTLINE_ACCEPTANCE_DRAFT.md).
Everything marked **proposed** is a recommendation for a later implementation playbook; nothing has
been implemented, seeded, or committed. Product decisions listed in the brief are treated as fixed
and are not reopened here.

Inspected checkouts: mod worktree `claude/rowan-farmer-exploration-97e04a` @ `0cd566dd`
(= `patch-18`), Rails `release/public` @ `5b4a22d`.

## 1. Recommended representation: five linked quests

**Recommendation: five `Quest` rows keyed `origin_npc = "Rowan"`, chained with the existing
`start_conditions.prerequisite_quest_id`, each stage ending in its own `ending` node.** Smallest
compatible shape, for these reasons:

1. Rewards on an `ending` transition are the only replay-protected path today
   (`processor.rb:138-145`, `quest_states_controller.rb:388-394`). Five endings give five
   durable reward hand-offs without touching the engine's transaction model.
2. `interact` already selects the next eligible stage by priority and prerequisite
   (`quest_states_controller.rb:143-188`); no new sequencing code is needed.
3. Each stage is a separate journal entry, so "Quest 2 of 5" can be shown with a title
   convention immediately and with a real field later (§5.3).
4. A player can quit one stage without losing the earlier stages' completions (a single
   five-node quest can only be abandoned on its first node, `quest_states_controller.rb:287-291`;
   quitting it from the journal restarts everything).
5. Content edits to one stage cannot strand players in another.

Costs accepted: five seed blocks instead of one; the awarded-seed choice must cross a quest
boundary if the seed pool ever exceeds one crop (§4.3); `interact` returns "I have nothing else for
you right now." between stages only if a prerequisite is unmet, which cannot happen in a linear
chain.

Rejected alternative: one quest with five stage nodes. It keeps per-player flags in one row, but
mid-quest rewards would sit on non-ending transitions (unprotected until D2 is fixed), quitting
loses everything, and the journal cannot distinguish stages without the same extension anyway.

### 1.1 Seed and priority contract (proposed)

| Stage | `title` (journal name) | `priority` | `start_conditions` | `description` (journal brief, 160 chars max) |
| --- | --- | --- | --- | --- |
| 1 | From Soil to Supper (1 of 5): Shovel Dung | 50 | `{}` | Rowan wants one pile of dung. Find a dung pile on bare dirt and strike it while holding your Britannia Shovel. |
| 2 | From Soil to Supper (2 of 5): Shovel Dirt | 40 | `{"prerequisite_quest_id": <stage 1 id>}` | Gather one handful of Dirt: right-click bare dirt with your Britannia Shovel. The ground stays put. |
| 3 | From Soil to Supper (3 of 5): Fill the Water Bucket | 30 | `{"prerequisite_quest_id": <stage 2 id>}` | Fill Rowan's bucket at the public Water Well. |
| 4 | From Soil to Supper (4 of 5): Mix Fertilized Dirt | 20 | `{"prerequisite_quest_id": <stage 3 id>}` | Bowl of dirt, add dung, bowl of water, mix. Keep the Fertilized Dirt. |
| 5 | From Soil to Supper (5 of 5): Plant and Harvest | 10 | `{"prerequisite_quest_id": <stage 4 id>}` | Hoe a public plot, fertilize it, plant your seed, water it, harvest your crop. |

Priorities descend so that, when several rows are inspected, the earliest incomplete stage wins;
prerequisites make the order strict regardless. The seed resolves `prerequisite_quest_id` from
the rows it just upserted, so ids never need to be hand-copied. All five stay non-repeatable.

## 2. Objective and delivery contracts

Symbols marked **proposed** do not exist yet. "Authoritative success point" is the existing
server-side line where success is already decided today; the proposed event is posted exactly
there.

### 2.1 Stage 1 — Shovel Dung

| Field | Contract |
| --- | --- |
| Prerequisites | Player accepted stage 1 (state on node `Working`). Shovel already granted on acceptance (§3). |
| Accepted interaction | Left-click a **tracked** dung node in Adventure with any main-hand item; the Britannia Shovel is the instructed tool but is not enforced (any item works today, `DungBlock.java:26-29`). Proposal: do not enforce the shovel either; enforcing it would refuse an empty-hand harvest for no player benefit. |
| Authoritative success point | `WildResourceHarvestService.harvestOne` after `setBlock` and `removeNode` succeed and the drop is popped (`WildResourceHarvestService.java:40-48`); the existing `WildResourceHarvestEvent` at 85-99 is the hook. |
| Actor / target | The harvesting `ServerPlayer`; `resourceId == britannia_mod:dung`. |
| Quantity | 1 (proposed default; see U4). |
| Time / state | Node must exist and be tracked; failed policy swings post no event; command-placed piles post no event. |
| Retained / consumed | 1 Dung dropped at the node; player must pick it up (pickup is not the objective). Nothing consumed. |
| Repeat handling | A second harvest after the objective fired is ignored because the node advanced and the watcher rewrites triggers (`QuestObjectiveWatcher.java:148-155`). |
| Failure reasons the player can hit | No pile nearby; pile inside a foreign house or spawn protection (policy message); pile out of reach. |
| Proposed integration | `action_trigger {"trigger_key":"dung_collected","action":"wild_resource_harvest","resource_id":"britannia_mod:dung"}` on node `Working`; mod `FarmingQuestEventBridge` listens to `WildResourceHarvestEvent` and calls `QuestObjectiveWatcher.onAction(player, ActionObjective.wildResourceHarvest(resourceId))` (proposed). |

### 2.2 Stage 2 — Shovel Dirt

| Field | Contract |
| --- | --- |
| Prerequisites | Stage 1 completed; stage 2 accepted. Player holds the Britannia Shovel (reissue path in §7 if lost). |
| Accepted interaction | Main-hand right-click on exact `minecraft:dirt` or `minecraft:coarse_dirt` with `britannia_mod:britannia_shovel` (`DirtGatheringInteractionHandler.java:29-48`). |
| Authoritative success point | `DirtGatheringService.attempt` returning `Result.GATHERED` (`DirtGatheringService.java:49-60`) — after the cooldown claim, item grant, and durability charge. |
| Actor / target | The gathering player; output item `britannia_mod:dirt`. |
| Quantity | 1. |
| Time / state | 1,200-tick cooldown per player after a success; irrelevant for a single gather unless the player gathered elsewhere first (message tells the remaining seconds). |
| Retained / consumed | +1 Dirt retained; shovel −1 durability; terrain unchanged. |
| Repeat handling | Later gathers ignored once advanced. |
| Failure reasons | Wrong target (grass, path); denied world/house; cooldown; off-hand shovel (not a gather gesture). |
| Proposed integration | `action_trigger {"trigger_key":"dirt_gathered","action":"dirt_gather"}`; post `DirtGatheredEvent(player, pos)` (proposed) at `Result.GATHERED`. |

### 2.3 Stage 3 — Fill the Water Bucket

| Field | Contract |
| --- | --- |
| Prerequisites | Stage 2 completed; stage 3 accepted; bucket granted at the end of stage 2 (§3). |
| Accepted interaction | Right-click a public `britannia_mod:water_well` with `minecraft:bucket` in either hand (`WaterWellBlock.java:28-41`). Natural water is **not** an accepted source for an ordinary Adventure player (`BucketItem.java:53-54`, `Player.java:1764-1770`), so instructions must name the well. |
| Authoritative success point | `WaterSourceInteraction.fillFromSource` branch `stack.is(Items.BUCKET)` after `setItemInHand(... WATER_BUCKET)` (`WaterSourceInteraction.java:56-58`). |
| Actor / target | The player; resulting item `minecraft:water_bucket`. |
| Quantity | 1. |
| Time / state | Well must be reachable and `mayInteract` must allow (spawn protection can deny silently: the fill returns `sidedSuccess(false)` with no message, `WaterSourceInteraction.java:41-44`). |
| Retained / consumed | Bucket becomes Water Bucket (retained; identity swap). Nothing else consumed. The bucket has no required use afterwards; the player keeps it (U3). |
| Repeat handling | Subsequent fills ignored. |
| Failure reasons | No well; well inside spawn protection; wrong item. |
| Proposed integration | `action_trigger {"trigger_key":"bucket_filled","action":"water_container_fill","item_id":"minecraft:water_bucket"}`; post `WaterContainerFilledEvent(player, source pos, resulting item)` (proposed) at line 58. Also add a player-facing denial message for the `DENIED_WORLD` branch (today silent). |

### 2.4 Stage 4 — Mix Fertilized Dirt

| Field | Contract |
| --- | --- |
| Prerequisites | Stage 3 completed; stage 4 accepted; player holds 1 Dung (stage 1), 1 Dirt (stage 2), 2 Empty Bowls (stage 2 reward), access to the well or source water. |
| Accepted interaction | The four main-hand gestures in the discovery §7.4; all are `Item.use` (right-click air or targeted water), which is not gated in Adventure. |
| Authoritative success point | `FertileDirtMixingService.apply` returning `ApplyResult.APPLIED` (after both live stacks are revalidated and shrunk, the Fertilized Dirt placed, and both Empty Bowls returned). |
| Actor / target | The player; output `britannia_mod:fertilized_dirt` ×1 plus `britannia_mod:empty_bowl` ×2 returned. |
| Quantity | 1 mix. |
| Time / state | None. Intermediates (Bowl of Dirt, Bowl of Fertile Dirt, Bowl of Water) are informational sub-steps only. |
| Retained / consumed | Consumed: 1 Dung, 1 Dirt, one bowl's water. Retained: 1 Fertilized Dirt, 2 Empty Bowls, all tools. |
| Repeat handling | Additional mixes ignored. |
| Failure reasons | Reversed hands (silent `PASS` today); wrong bowl identity; aiming at water while trying the dry recipe (fills instead); full inventory (output drops at feet, not lost). |
| Proposed integration | `action_trigger {"trigger_key":"fertilizer_mixed","action":"fertile_dirt_mix"}`; post `FertileDirtMixedEvent(player)` (proposed) at `APPLIED`. Optional informational flags via the existing `quests/:id/player_state` endpoint: `bowl_of_dirt`, `bowl_of_fertile_dirt`, `bowl_of_water` (posted by the bridge from `BowlPreparationService`/`BowlWaterFillingService` success), so the journal can show the three intermediate ticks. |

### 2.5 Stage 5 — Plant and Harvest

| Field | Contract |
| --- | --- |
| Prerequisites | Stage 4 completed; stage 5 accepted; player holds Farming Hoe (stage 4 reward), 1 Fertilized Dirt, the awarded seed (stage 3 reward), Watering Can (stage 3 reward); Farming skill data `AVAILABLE` with value ≥ 0; an unused base `britannia_mod:community_farm_block` reachable. |
| Accepted interactions (sub-steps) | (a) hoe: right-click base plot with `farming_hoe` (`CommunityFarmBlock.java:76-77`); (b) fertilize: right-click hoed plot with `fertilized_dirt` (`CommunityHoedFarmBlock.java:30-32`); (c) plant: right-click soil top with the seed (`FarmingBlock.java:200-202`, `tryPlantSeed`); (d) water: right-click soil with the Watering Can, target hydration 3 (`FarmingBlock.java:218-220`); (e) harvest: right-click the mature crop with an empty hand or any item for carrot (`tryHarvestCrop`, `canHarvestWith` HAND). |
| Authoritative success point | Sub-steps: `prepareCommunityPlot` after `setBlock` (`FarmingHoeItem.java:100-103`); `fertilizeCommunityPlot` after `setBlock` and `initializeFertileHarvests` (`CommunityHoedFarmBlock.java:46-53`); `tryPlantSeed` at `farmBe.plant(crop)` + `stack.shrink` (`FarmingBlock.java:526-533`); `waterFarmingBlock` after `farmBlockEntity.water(1)` (`WateringCanItem.java:112-117`). **Objective**: `tryHarvestCrop` after `popResource(harvest)` (`FarmingBlock.java:603`). |
| Actor / target | The harvesting player must equal the planter of that plot (M2); crop id must equal the awarded crop (carrot by default). |
| Quantity | 1 harvest (3 carrots dropped). |
| Time / state | Hoe→fertilize window 3 min (deterministic); fertilize→plant window 60 s (random-tick reclaim); growth 8–12 min watered to 3, ~35 min unwatered (discovery §7.7); crop must reach `mature`. |
| Retained / consumed | Hoe −1 durability; 1 Fertilized Dirt consumed; 1 seed consumed; 2 watering-can charges; produce dropped at the plot for pickup; plot keeps 4 fertile harvests and re-opens a seed window for anyone. |
| Repeat handling | A second harvest of another plot after completion is ignored; a harvest of a plot the player did not plant never counts. |
| Failure reasons | "Use a farming hoe on this public plot first." (fertilizer on unhoed plot); "This public plot has already been hoed."; plot expired back to base (silent revert); "A crop is already planted here."; "Farming skill data is unavailable; planting is blocked."; "You do not know how to grow this crop yet…" (cannot happen for carrot at 0 unless data says less than 0); "The soil is already fully watered."; "The watering can is empty."; crop harvested by someone else. |
| Proposed integration | `action_trigger {"trigger_key":"crop_harvested","action":"crop_harvest","crop_id":"carrot","require_planter":true}`; `CropHarvestedEvent(player, pos, cropId, planterUuid, communityPlot)` (proposed) posted at line 603; `FarmingBlockEntity` gains a persisted `PlanterUUID` set in `tryPlantSeed` and cleared with the crop (M2). Informational flags posted through `player_state`: `plot_hoed`, `plot_fertilized`, `crop_planted`, `crop_watered` (with hydration), so the journal shows sub-progress. |

### 2.6 Cross-cutting delivery contract (proposed)

* Objective results are always server-decided; the client is never asked. The watcher's
  existing in-flight set and 10-second failure cooldown stay (`QuestObjectiveWatcher.java:109-135`).
* **Pending-objective retry (extension)**: single farming events do not recur, so a Rails outage
  at the moment of a harvest would lose the objective forever. Keep an in-memory
  `PendingObjective(playerUuid, questStateId, triggerKey, firstAttemptAt)` per player, retry with
  backoff (10 s, 30 s, 60 s, then every 2 min) until Rails answers success, answers "Nothing
  happens here yet." (node already advanced: drop it), or the player logs out. Persist nothing to
  disk: on logout the player-facing recovery is the reissue/replant path, and the journal refresh
  reconciles the node.
* Rewards are granted only from an authenticated Rails response (`QuestRewardService.apply`);
  keep that. Fix D1 so ordinary rewards are not stamped (stamp only when the id matches the
  node's `pickup_trigger`/`destroy_trigger` item tag, which is what the stamp exists for) and fix
  D2 so any transition whose response carries `granted_items` is stored for replay.
* Full-inventory delivery: today the surplus drops at the player's feet
  (`QuestRewardService.java:60`) and a stamped drop that despawns is returned to the player
  (`QuestEventHandlers.java:93-132`). With D1 fixed, unstamped drops would no longer be returned;
  therefore also add a **pre-check on the claim choice**: the client disables "Claim reward" and
  says "Make room in your pack (need N free slots)" when `rewards_preview` needs more free slots
  than exist, and the server keeps the drop fallback for the race.
* Identity scope: one completion per player per shard (`player_quest_states` unique on
  `(player_uuid, quest_id)`, quests are per shard). Multiple Rowan instances share it. This matches
  how skills and achievements are scoped (`ShardUser`, `achievements` unique per user/shard/name).

## 3. Content and reward contracts per stage (proposed node graphs)

Common node set for every stage: `Offer` (decision) → `Working` (decision) ↔ `Help` (decision)
→ `Done` (decision) → `Complete` (ending). Choice keys are deterministic strings so re-seeding
never invalidates an open dialogue (the escort seed's `SecureRandom.uuid` keys are the pattern to
avoid, `escorts_britannia.rb:88,94`). A choice with id `close` is intercepted client-side and never
sent (`QuestDecisionScreen.java:98-102`), which leaves the state parked on `Offer` and already
listed in the journal (Rails creates the row at the first click); pressing ESC or clicking away
instead sends `ABANDON`, which Rails honours only on the first node
(`quest_states_controller.rb:287-291`). Both paths re-offer the stage on the next click. Proposed
small client change: "Not now" on an `Offer` node should send `ABANDON` like ESC does, so a
declined stage does not sit in the journal as accepted.

| Stage | On acceptance (`Offer.accept` effects) | Objective node | Completion rewards (`Done.claim` effects → `Complete`) | Player keeps for later |
| --- | --- | --- | --- | --- |
| 1 | `{"give_item": {"britannia_shovel": 1}}` | `Working` with `dung_collected` | `{"give_item": {"gold_coin": G1}}` | 1 Dung, the shovel |
| 2 | — | `dirt_gathered` | `{"give_item": {"copper_coin": C2, "empty_bowl": 2, "minecraft:bucket": 1}}` | 1 Dirt, 2 Empty Bowls, Bucket, Dung |
| 3 | — | `bucket_filled` | `{"give_item": {"silver_coin": S3, "watering_can": 1, "carrot_seeds": 1}}` (random pool: U2) | Seed, Watering Can (full), Water Bucket (incidental) |
| 4 | — | `fertilizer_mixed` | `{"give_item": {"gold_coin": G4, "farming_hoe": 1}}` | 1 Fertilized Dirt, 2 Empty Bowls, hoe |
| 5 | — | `crop_harvested` | `{"give_item": {"gold_coin": G5}, "grant_achievement": "first_harvest"}` | Carrots, all tools |

`QuestRewardService.resolveItem` accepts bare ids with the mod namespace and explicit
`minecraft:` ids (`QuestRewardService.java:73-77`). Quantities G1, C2, S3, G4, G5 are unresolved
(§4.1). The website achievement will be named "Quest: First Harvest" by
`AwardQuestAchievement` (slug titleized); the in-game toast shows "First Harvest".

### 3.1 Dialogue copy (proposed, short enough for the narrow text column)

Bodies are kept under ~170 characters because the dialogue column can be 84–137 px wide at high
GUI scales (discovery §8). Directions placeholders (`%{well}`, `%{plots}`, `%{dirt}`) are
resolved by the server (§5.4) or replaced by "Ask an administrator where the public farm is."

* **Stage 1 Offer**: "Fancy learning the land? First job: bring me one pile of dung. Take this
  shovel — you'll keep it." Choices: "I'll do it" / "Not now".
* **Stage 1 Working**: "Dung shows up on bare dirt now and then. Hold the shovel and strike the
  pile. Bring one back." Choices: "How do I find dung?" / "Farewell".
* **Stage 1 Help**: "Look for a low dark pile on plain dirt, not grass. %{dirt} Strike it once;
  pick up what drops." Choice: "Back".
* **Stage 1 Done**: "That'll do nicely. Keep the dung — you'll need it for the mix." Choice:
  "Claim reward".
* **Stage 2 Working**: "Right-click bare dirt with your shovel. It takes a handful and leaves the
  ground as it was." Help: "Only plain dirt or coarse dirt. Once a minute at most."
* **Stage 3 Working**: "Fill this bucket at the well. Rivers won't do — use the well. %{well}"
  Help: "Right-click the well with the bucket in your hand."
* **Stage 4 Working**: "Empty bowl in your main hand, dirt in your off hand, right-click the air.
  Then dung. Fill the other bowl at the well. Mix the two." Help opens the mixing guide (§5.5).
* **Stage 5 Working**: "Hoe a public plot, put the fertilized dirt on it, plant your seed, water
  it twice. Harvest when it's grown. %{plots}"
* **Stage 5 Done**: "Your first harvest! You've got the makings of a farmer." Choice: "Claim
  reward" → toast "First Harvest".

Failure and status copy is in §5.6.

## 4. Economy assumptions

### 4.1 Coins and quantities

Facts: three coin items, stack size 99 (`ItemRegistry.java:170-175`); 1 gold = 100 silver =
10,000 copper (`CoinConversion.java:42-53`, mirrored by Rails' cheque validator); `give_item`
counts above 99 split into extra stacks (`QuestRewardService.java:50-62`, cap 1,024 per line, 32
lines). Rails `give_item` keys `gold_coin`/`silver_coin`/`copper_coin` resolve directly.

Price evidence available in the repositories:

| Source | Evidence |
| --- | --- |
| Farmer vendor seed (`db/seeds/farmer_vendor.rb:60-77`) | Farming Hoe 12 gold, Britannia Shovel 12 gold, Watering Can 9 gold, Empty Bowl 2 gold, seeds 3 gold each (products `published: false`; live catalogue unverified). The issued kit is worth about 40 gold at those prices. |
| Escort quests (`escorts_britannia.rb:41-45`) | 3–7 silver plus fame/karma per completed escort. |
| Mob loot (earlier discovery §"Money") | 1–3 gold per Wisp or Earth Elemental kill. |
| New-player income baseline | None found in either repository. |

Because the vendor and escort scales disagree by two orders of magnitude and no income baseline
exists, **the five quantities are left unresolved (U1)**. Proposed default for the owner to
accept or replace, anchored to "less than one tool at vendor prices": G1 = 2 gold, C2 = 50 copper,
S3 = 20 silver, G4 = 3 gold, G5 = 5 gold (≈ 10.2 gold total). Evidence needed to settle it: the
live catalogue price of a hoe and a seed packet, and what a first-hour player can otherwise earn.

### 4.2 Reward display

Silver and copper coins have no display names (D5); add `item.britannia_mod.silver_coin` and
`item.britannia_mod.copper_coin` before any stage pays them.

## 5. Crop eligibility and the seed decision

### 4.3 Seed pool (U2)

With the kit as specified (shovel, hoe, watering can) and Farming 0, **carrot is the only crop that
can be planted and harvested** (discovery §7.6). Default: the stage 3 seed reward is
`carrot_seeds` ×1 and "randomly selected" degenerates to a pool of one; no persistence extension
is needed because stage 5's trigger names `carrot` statically.

Alternative the owner may prefer: add `scissors` ×1 to the stage 3 reward. The pool becomes
carrot, lettuce, green onion (all 0.0, all 4–5 base growth ticks). That requires the random
effect and cross-stage persistence below:

* Rails `EffectApplier` gains `give_random_item: {"pool": [{"carrot_seeds":1},{"lettuce_seeds":1},{"green_onion_seeds":1}], "flag": "awarded_seed"}`
  (proposed): rolls once inside the transaction, appends the chosen entry to `granted_items`, and
  records `flags.awarded_seed`. Because the ending response is stored verbatim, a replay grants
  the same roll.
* `interact` copies listed flags from the prerequisite's state when it creates the next stage
  (`start_conditions.inherit_flags: ["awarded_seed"]`, proposed), and the serializer resolves
  `"crop_id": "%{flag:awarded_seed_crop}"` placeholders when publishing triggers. Reopening
  dialogue or reconnecting never rerolls because the roll lives in `state_variables`.

Wheat and potato are excluded: wheat needs a grain blade, potato needs Farming 5.0.

## 5. UX states and copy (proposed; source-derived, not screenshots)

Reuse: `DialoguePresentation` (UO font, parchment band, portrait, 140-px option buttons),
`QuestJournalScreen` (scroll texture), action-bar messages for world feedback, `SystemToast` for
the achievement. Keybindings referenced through `KeyMapping.getTranslatedKeyMessage()` for
`key.use`, `key.attack`, `key.swapOffhand`, `key.inventory`, and `Keybinds.OPEN_SKILL_SCREEN`, so
rebound keys are shown, never assumed.

### 5.1 Rowan in dialogue

* Portrait: `Rowan.png` in the GCS `portraits/<gender>/` folder (content); until uploaded the
  generic peasant shows. Name under the portrait: "Rowan". Profession label: "Farmer" — requires
  `QuestDialogueAdapter` to pass a label (it passes `""`, `QuestDialogueAdapter.java:26`); the
  label can come from a new node/quest metadata key `profession` or from the archetype table
  (M5).
* Outfit: new `CitizenClothingLayer.OUTFIT_TEXTURES` entry `farmer` → boots + half apron
  (textures exist for both genders); the spawner sets `outfitKey` for the Rowan archetype.
* Nameplate: existing UO-style nameplate without background (`NpcNameplate`), unchanged.
* Body text ≤ 170 characters; three visible choices maximum on `Working` nodes so buttons never
  exceed the parchment band (3 × 24 px + margins < 134 px).

### 5.2 Dialogue sections (new client rendering, driven by node metadata)

Rendered under the body text inside the parchment band, each a one-line list of item icons
with counts (icons via `ItemStack` from registry ids, tooltips on hover):

* **"You receive now:"** — `metadata.rewards_preview.on_accept` on `Offer` nodes.
* **"Reward when done:"** — `metadata.rewards_preview.on_complete` on `Offer`/`Working`/`Done`.
* **"Keep for later:"** — `metadata.keep_items`, e.g. Dung on stage 1.

Distinguishing colour is not the only cue: each list has a text label and an icon column.

### 5.3 Journal rows (extension of `ClientQuestEntry` + `QuestEntryCodecs`)

Row layout (62 px today; propose 74 px and one fewer visible row):

```
From Soil to Supper (2 of 5): Shovel Dirt        [Quit]
Rowan · Farmer · Quest 2 of 5
Next: right-click bare dirt with your shovel
Progress: ✓ Accepted   ○ Dirt gathered   ○ Report to Rowan
```

Fields: `stage_label` ("2 of 5", from quest metadata), `objective` (current node
`metadata.journal_objective`), `progress` (ordered `{label, done}` from node metadata plus
`state_variables.flags`), all published by `QuestJournalEntrySerializer`. Locked-stage reason: a
quest not yet available is not in the journal; instead the last completed stage's `Complete`
node says "Talk to me again for the next job."

### 5.4 Persistent "What do I do next?"

* The journal `Next:` line is the persistent help.
* Directions: the server resolves `%{well}`, `%{plots}`, `%{dirt}` in node bodies at forward
  time by scanning a 64-block radius around the Rowan the player is talking to for
  `water_well`, `community_farm_block`, and surface dirt/coarse dirt, producing "The well is 18
  blocks north-east." Optional per-block override: one `HintText` string on
  `QuestGiverSpawnBlockEntity` (shown in the spawn screen) that replaces all placeholders. If
  nothing is found and no hint is set: "Ask an administrator where the public farm is."
* A quiet action-bar reminder when the player is within 8 blocks of Rowan with an unclaimed
  `Done` node: "Rowan has your reward. Right-click to claim."

### 5.5 Mixing guide (Help node for stage 4, client-rendered from `metadata.guide`)

Two-column table: main hand, off hand, gesture, result, returned. Four rows in the order of
discovery §7.4, plus one line: "Aim at the sky for the dry steps; aim at the well only for water."
The bucket is not mentioned in the guide.

### 5.6 Failure and status messages (action bar, with a text prefix so colour is not the only cue)

| Situation | Today | Proposed player text |
| --- | --- | --- |
| Reversed bowl hands | silent `PASS` | "Swap hands: the bowl goes in your main hand, the ingredient in your off hand." |
| Wrong bowl or vanilla dirt | silent | "That's not the right bowl. Use the wooden Empty Bowl from Rowan." / "Use the Dirt you gathered with the shovel." |
| Empty bowl aimed at flowing water | silent | "That water is moving. Fill from still water or the well." |
| Bucket at a river | vanilla fail, silent | "Fill the bucket at the well." (client-side hint when holding a bucket and looking at water) |
| Well inside spawn protection | silent | "You can't use this well here." |
| Occupied soil | "A crop is already planted here." | keep |
| Fertilizer on unhoed plot | "Use a farming hoe on this public plot first." | keep |
| Hoed plot about to expire | none | countdown at 60/30/10 s: "Fertilize this plot within 30 s." |
| Fertilized plot about to lose its seed window | none | countdown: "Plant within 30 s or the plot resets." |
| Skill data unavailable | "Farming skill data is unavailable; planting is blocked." | "Your skills haven't loaded yet. Wait a moment and try again." plus automatic retry (D11) |
| Pending backend confirmation | none | "Rowan is checking your work…" while a pending objective retries; "Confirmed." on success |
| Full inventory on claim | items drop | "Make room in your pack: N free slots needed." and the claim button disabled |
| Over-watering | "The soil is already fully watered." only at 5 | "Water twice, then wait. Carrots dislike soggy soil." (once, at hydration 3) |
| Someone else harvested the crop | none | "Someone harvested that plot. Plant again in a plot you prepared." |

### 5.7 Reopen, resume, interruptions, completion moment

* Reopening dialogue always shows the current node (Rails is authoritative); `Done` stays until
  claimed; the popup for a server-fired objective should show Rowan's name (fix D4 by reading the
  quest giver name from `ClientQuestTable`) and, for farming objectives, **not** auto-open at all:
  a toast "Objective complete — return to Rowan" is less disruptive mid-field (U6).
* Full inventory: claim disabled with the slot count; fallback drop remains.
* Interrupted delivery: replay (D2 fix) returns the same items once; the retry is invisible to the
  player except for "Rowan is checking your work…".
* Lost supplies: "I lost my…" choice on `Working`, gated to one reissue per stage (§7).
* Completed stages: `Complete` node closes with "Farewell"; the journal entry disappears
  (completed quests leave the active journal), so the next stage's entry must appear on the next
  click at Rowan.
* Achievement moment: toast "Achievement Unlocked! First Harvest" plus challenge sound (existing),
  a persistent advancement `britannia_mod:quest/first_harvest` (M6), and the website entry.

### 5.8 GUI scale, small windows, long text, focus, contrast, localization

* Cap body text length in content and add clipping plus a mouse-wheel scroll to
  `DialoguePresentation.renderDialogue` when the wrapped text exceeds the band.
* Journal panel: scale to `min(370, width − 20)` and clamp visible rows to the height; add
  keyboard focus order (Tab between rows, Enter on the focused Quit) and a Quit confirmation.
* All new strings through `Component.translatable` keys under `screen.britannia_mod.quest.*` and
  `message.britannia_mod.quest.*`; add the missing coin and keybinding entries.
* Contrast: keep `TEXT_COLOR 0xFF111111` on parchment; status cues carry a text prefix ("Done:",
  "Blocked:", "Waiting:").

### 5.9 Rails authoring interface (only what this questline needs)

* Add `description` and `active` to the permitted params and the form (D9) so the journal brief
  and publishing are authorable.
* Add "Action" to the observer section of `_node_fields` (trigger key, action, resource/crop id,
  require planter) and "Rewards preview / keep items / journal objective / progress steps"
  fields as JSON text areas with placeholders. No general editor.
* Replace the "Live Stats" stub with a count of active/completed states per quest (optional).

## 6. Recovery decisions

| Event | Current behaviour | Proposed policy |
| --- | --- | --- |
| Reconnect | Journal rebuilt from bootstrap or refetched on first miss; triggers restored from the current node | No change; verify in acceptance |
| NPC respawn / other Rowan | Same key, same state | No change |
| Death | Vanilla drop rules (server `keepInventory` unverified) | Renewable inputs are regathered; one reissue per stage of shovel (1, 2), bucket (3), bowls (4), hoe and seed (5) through gated dialogue choices; coins never reissued |
| Cancel/quit a stage | Quit → abandoned; restart from `Offer` keeping attributes | Journal Quit gets a confirmation; reissue counters survive because attributes survive restart |
| Lost fertilizer or expired plot | No refund | Remix (dung and dirt are renewable); stage 4 stays complete |
| Crop lost or harvested by another player | Plot re-opens a 60-s window then reverts | Replant; if the plot reverted, hoe and fertilize again (remix); seed reissue once |
| Backend unavailable after a successful action | Objective lost after cooldown for single events | Pending-objective retry with backoff (§2.6); no client assertion |
| Content edit mid-flight | Node titles are routing keys; observer edits apply on next journal load | Never rename node titles of a published stage; add nodes instead; deterministic choice keys |
| Chunk unload / long absence | Growth pauses; windows keep counting when loaded | Longer windows (§8) and the countdowns |

Limits stated plainly: nothing can find items in chests, banks, or other players' inventories;
the policy reissues a bounded set and otherwise asks the player to regather.

## 7. File and component map

### 7.1 Rails

| Change | Files | Kind |
| --- | --- | --- |
| Store replay for any transition with granted items (D2) | `app/services/quest_engine/processor.rb:138-145`, `app/controllers/api/quest_states_controller.rb:388-394`; tests `test/controllers/api/quest_completion_idempotency_test.rb` | fix |
| `action_trigger` publication | `app/serializers/quest_journal_entry_serializer.rb:41-50` (+ `action_trigger` reader), tests `test/controllers/api/quest_journal_endpoint_test.rb` | extension |
| Journal fields `stage_label`, `objective`, `progress`, `profession` | same serializer; quest/node metadata conventions | extension |
| `give_random_item`, `inherit_flags` (only for U2 alternative) | `app/services/quest_engine/effect_applier.rb`, `quest_states_controller.rb:191-218` | extension |
| Generic counter endpoint (only if U4 > 1) | `quest_states_controller.rb:244-274` generalised to `record_progress` with a counter key | extension |
| Admin form: description, active, action observer, preview fields | `app/controllers/admin/quests_controller.rb:55-63`, `app/views/admin/quests/_form.html.erb`, `_node_fields.html.erb`, `app/javascript/controllers/json_array_builder_controller.js` | extension |
| Seed and task | `db/seeds/rowan_farming_questline.rb`, `lib/tasks/seed_rowan_farming_questline.rake`, `test/integration/rowan_farming_questline_seed_test.rb` | content |
| Skill roster | `db:seed:uo_skills` must have run (`uo_skill_roster.rb:53`) | config |

### 7.2 NeoForge

| Change | Files | Kind |
| --- | --- | --- |
| Stop stamping ordinary rewards / stop deleting them (D1) | `quest/QuestRewardService.java:79-103`, `quest/QuestCleanupService.java:139-141,234-236`; GameTest for "reward survives relog" | fix |
| Permission check on spawner config (D3) | `network/NetworkHandler.java:303-316` | fix |
| Trigger popup naming (D4) and no auto-popup for action objectives (U6) | `network/ClientNetworkHandler.java:268-316` | fix |
| Skill data retry (D11) | `skill/SkillManager.java:233-316` | fix |
| Farming quest events | new `event/farming/{DirtGatheredEvent,WaterContainerFilledEvent,FertileDirtMixedEvent,CropPlantedEvent,CropHarvestedEvent}.java`; posts in `dirtgathering/DirtGatheringService.java:49-60`, `util/WaterSourceInteraction.java:56-58`, `bowlpreparation/FertileDirtMixingService.java` (APPLIED), `block/FarmingBlock.java:526-538,603` | extension |
| Planter attribution | `block/entity/FarmingBlockEntity.java` (`PlanterUUID` NBT, set in `plant`, cleared in `clearCrop`); `block/FarmingBlock.java:447-540` | extension |
| Action triggers and bridge | `quest/QuestObjectiveTriggers.java` (Action record + parsers), `quest/QuestObjectiveWatcher.java` (`onAction`, pending retry), new `quest/events/FarmingQuestEventBridge.java` | extension |
| Flags client | `quest/network/QuestServerAPI.java` (`updatePlayerState` → `Endpoint.QUEST_PLAYER_STATE`, new in `server/http/RailsApiUrlResolver.java:184-194`) | extension |
| Journal transport | `quest/ClientQuestEntry.java`, `network/payload/QuestEntryCodecs.java`, `quest/QuestEntryParser.java`, `client/gui/QuestJournalScreen.java` | extension |
| Dialogue sections, guide, countdowns, messages | `client/gui/QuestDecisionScreen.java`, `client/gui/DialoguePresentation.java`, `dialogue/QuestDialogueAdapter.java`, farming blocks/items for the countdown and new messages | extension |
| Rowan archetype, outfit, profession | `client/gui/QuestGiverSpawnScreen.java:28`, `block/entity/QuestGiverSpawnBlockEntity.java:171-178`, `client/renderer/CitizenClothingLayer.java:49-64`, `entity/QuestGiverEntity.java:100-102` (role title from archetype) | extension |
| Directions | `quest/QuestProxyService.java:289-337` (placeholder resolution before forwarding), `block/entity/QuestGiverSpawnBlockEntity.java` (`HintText`), spawn screen field | extension |
| Timing | `block/entity/CommunityFarmBlockEntity.java:15` (window), `block/entity/FarmingBlockEntity.java:41` and `FarmingBlock.java:352-355` (deterministic reclaim), `wildresource/WildResourceSavedData.scheduleAttempt` call on stage-1 acceptance | extension |
| Advancement grant on completion | `quest/QuestProxyService.java` or `quest/QuestRewardService.java` (award `quest/<slug>` when a `client_actions` achievement arrives) | extension |

### 7.3 Content and configuration

`data/britannia_mod/advancement/quest/first_harvest.json`; `assets/britannia_mod/lang/en_us.json`
(coins, keybinding, `screen.britannia_mod.quest.*`, `message.britannia_mod.quest.*`); Rails seed
data (five quests, node bodies, metadata); operator placement of Rowan blocks and infrastructure.

### 7.4 Assets

`portraits/<gender>/Rowan.png` in the GCS bucket (108 × 108 with the 10-px crop margin the
screen expects, `DialoguePresentation.java:29-32`); optional new farmer outfit textures if the
existing half apron and boots are judged insufficient; reward icons need no new art (item sprites).

## 8. Gaps: smallest change, dependency, risk, acceptance criterion

| Gap | Smallest change | Depends on | Risk | Acceptance criterion |
| --- | --- | --- | --- | --- |
| D1 stamped rewards deleted | Stamp only ids matching the node's pickup/destroy tag; keep cleanup logic | none | Escort silver behaviour changes (improves) | GameTest: grant an unstamped shovel, complete quest, run `cleanupStaleLocalQuestState` with an empty journal, shovel remains; stamped quest item still removed |
| D2 non-ending grants unprotected | Store request id and result whenever `granted_items` is non-empty | none | Column names say "completion" | Rails test: accept choice with `give_item`, replay same request id → same items; different id → "Invalid choice." with no second grant |
| D3 unauthenticated spawner config | `hasPermissions(2) || isCreative()` check before `applyConfig` | none | none | Unit/GameTest: permission-0 player payload ignored and logged |
| D4/U6 popup | Read giver name from client table; toast instead of popup for `action` triggers | none | none | Manual: harvest → toast, no popup; talk to Rowan → `Done` |
| D5/D8 lang | Add entries | none | none | Tooltip and controls show names |
| D6 seed window | Block-entity ticker reclaim + longer windows | none | Changes existing farming timing | GameTest at `randomTickSpeed 0`: reclaim still happens at the deadline |
| D11 skill retry | Retry `fetchPlayerSkillsAsync` on a 60-s backoff while UNAVAILABLE | none | none | GameTest with a failing then succeeding fetcher seam |
| M1 action triggers | Serializer + parser + watcher entry + 5 events | D2 | Protocol drift between repos | Rails test publishes `triggers.action`; GameTest fires each event and observes one `sendTrigger` |
| M2 planter | `PlanterUUID` NBT | none | Migration of old plots (null planter = nobody) | GameTest: plant as A, harvest as B → no objective; as A → objective |
| M3 journal fields | Serializer + codec + screen | M1 | Client/server codec must ship together | Screen shows stage, next, progress at GUI scale 1–4 |
| M4 random seed | Only if U2 chooses the wider pool | D2 | Cross-stage flag plumbing | Replay grants the same seed; stage 5 trigger names it |
| M5 archetype | List entry + outfit + role title | none | none | Place block, choose Rowan, NPC named Rowan with apron; portrait loads if uploaded |
| M6 advancement | JSON + grant hook | none | none | Advancement appears after stage 5 |
| M7 directions | Placeholder resolution + hint field | none | Scan cost (bounded radius, on interact only) | Two Rowans in different places give different directions |
| Pending retry | Watcher queue with backoff | M1 | Duplicate sends are idempotent per node | GameTest: fetcher fails twice then succeeds → one applied result |
| Dung availability | Schedule an immediate attempt in the 3×3 chunks around Rowan on stage-1 acceptance | M1 (acceptance hook) | Tracked semantics unchanged; cap and spacing still apply | GameTest: after acceptance a scheduled attempt exists for Rowan's chunk with `nextAttempt <= now` |

## 9. Proposed milestone order and gates

| Milestone | Scope | Local gate | Live gate |
| --- | --- | --- | --- |
| M0 Durability and safety fixes | D1, D2, D3, D4, D5, D8, D11 | Mod unit + GameTests green (baseline 264/0/6 for the selection in discovery §9.1; full suites per repo norms); Rails quest tests green (58/0) plus new replay test | Complete an escort quest, relog, silver still present |
| M1 Rails contracts and seed | `action_trigger`, journal fields, seed + rake task, admin form fields | Seed test: idempotent twice, five quests, prerequisites resolved, deterministic choice keys; journal endpoint test shows the new fields | Seed on staging; admin page shows the five quests |
| M2 Mod objective bridge | Events, planter attribution, watcher `onAction`, flags client, pending retry, Rowan archetype/outfit | GameTests per stage boundary through the fetcher seam; `QuestProxySecurityTest` still refuses client triggers | Two Rowans placed; one player runs stages 1–5 with a fixture kit (labelled diagnostic) |
| M3 UX | Journal rows, dialogue sections, guide, messages, countdowns, Quit confirmation, scaling | Screenshots at GUI scale 1–4 and a 640 × 360 window; lang completeness check | Ordinary player reads only in-game text and completes without help |
| M4 Timing and forgiveness | Windows, deterministic reclaim, dung scheduling on accept, over-water hint | GameTests at `randomTickSpeed 0` and 3 | Timed walkthrough: waiting ≤ 25 min end to end |
| M5 Release readiness | Portrait upload, advancement, infrastructure guide for operators, acceptance run | Acceptance draft executed by an ordinary player | Multi-instance, reconnect, backend-interruption, repeat-claim rows all pass |

Dependencies: M1 and M2 can proceed in parallel after M0's D2; M3 needs M1's fields; M4 is
independent of M3; M5 needs everything.

## 10. Unresolved decisions (materially affect scope or experience)

| Id | Decision | Recommended default | Evidence needed to settle |
| --- | --- | --- | --- |
| U1 | Coin quantities G1, C2, S3, G4, G5 | 2 gold / 50 copper / 20 silver / 3 gold / 5 gold | Live vendor prices; first-hour income baseline |
| U2 | Seed pool | Carrot only (no random machinery) | Whether adding scissors to stage 3 is acceptable; if yes, M4 random effect is needed |
| U3 | Bucket after stage 3 | Keep it (no take-item effect exists; hand-in would need one) | Owner preference only |
| U4 | Dung quantity | 1 | Whether a count > 1 is wanted; needs the counter endpoint |
| U5 | Rowan gender and portrait source | Owner choice; male default only because `male_half_apron_1.png` and `male_boots_1.png` exist alongside the female set | Portrait art availability |
| U6 | Objective completion presentation | Toast plus claim at Rowan (no auto-popup) | Owner preference; escort behaviour unchanged |
| U7 | Reissue caps | One per item per stage | Abuse tolerance |
| U8 | Windows | Hoe 10 min, seed 5 min, both deterministic | Whether other farming content relies on 3 min / 60 s |

## 11. Feasibility verdict

Feasible on the existing quest engine, dialogue system, reward path, and spawn block. **Not
implementation-ready yet**: it becomes ready when M0's two durability fixes (D1, D2) are agreed as
the first milestone and the `action_trigger` contract of §2 is accepted, because every stage's
detection and every reward's survival depends on them. The acquisition sequence (kit issued by the
quests), the first-harvest path (carrot at Farming 0, watered to 3, harvested by hand), and the
reward-persistence questions are answered or bounded above. Main blockers: D1, D2, D3, the five
missing farming events with planter attribution, and operator infrastructure near each Rowan.
Must be verified live: dung placement rate near the chosen sites, region climate, `randomTickSpeed`,
the seeded Farming skill and vendor rows on the shard, the portrait bucket, and the whole
ordinary-player walkthrough in the acceptance draft.
