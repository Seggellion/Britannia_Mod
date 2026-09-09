# Rowan the Farmer — "From Soil to Supper" acceptance draft

Status: **DRAFT, UNEXECUTED.** Nothing here has been run. Boxes are unchecked and must stay
unchecked until an ordinary player performs the step on a build that contains the work described
in [ROWAN_FARMING_QUESTLINE_PLANNING_INPUTS.md](ROWAN_FARMING_QUESTLINE_PLANNING_INPUTS.md). Expected
results below are derived from the code readings in
[ROWAN_FARMING_QUESTLINE_DISCOVERY.md](ROWAN_FARMING_QUESTLINE_DISCOVERY.md) at mod `0cd566dd` and
Rails `5b4a22d`, plus the proposed changes; where a proposal is required for a step to pass, the
row says so. Repository IDs are exact.

Record each row as PASS / FAIL / BLOCKED / NOT RUN with inventory deltas, coordinates, timings,
messages, and whether a reconnect preserved the result. A blocked earlier row makes every dependent
row CONDITIONAL.

## 0. Starting contract

* A **new, non-operator** account: empty inventory, armour, off hand; no coins, bank balance,
  blessed restores, property, or crops; normal new-player skill allocation (Farming 0.0, loaded).
* Game mode is whatever the mod enforces (Adventure for ordinary players); do not switch modes,
  use Creative, or accept gifts. Administrator fixtures are listed separately in §9 and never
  satisfy an ordinary-play row.
* Controls: write down the actual bindings for Use, Attack, Swap Hands, Inventory, and the
  Britannia menu key (default `O`); instructions in game must show the same names.
* Record the shard/build identity, server `randomTickSpeed`, region climate at the farm, and
  whether `db:seed:uo_skills` and the questline seed have been run.

### Identities

| Label | Exact id | Source |
| --- | --- | --- |
| Britannia Shovel | `britannia_mod:britannia_shovel` | stage 1 acceptance |
| Dung | `britannia_mod:dung` | stage 1 harvest |
| Dirt | `britannia_mod:dirt` | stage 2 gather |
| Empty Bowl ×2 | `britannia_mod:empty_bowl` | stage 2 reward (wooden bowl sprite, name "Empty Bowl") |
| Bucket | `minecraft:bucket` → `minecraft:water_bucket` when filled | stage 2 reward, filled in stage 3 |
| Watering Can | `britannia_mod:watering_can` | stage 3 reward (reads 12/12) |
| Seed | `britannia_mod:carrot_seeds` (default pool) | stage 3 reward |
| Bowl of Dirt / Bowl of Fertile Dirt / Bowl of Water | `britannia_mod:bowl_of_dirt` / `bowl_of_fertile_dirt` / `bowl_of_water` | stage 4 intermediates |
| Fertilized Dirt | `britannia_mod:fertilized_dirt` | stage 4 output |
| Farming Hoe | `britannia_mod:farming_hoe` (durability 128) | stage 4 reward |
| Coins | `britannia_mod:gold_coin`, `silver_coin`, `copper_coin` | stage rewards |
| Public plot | `britannia_mod:community_farm_block` → `community_hoed_farm_block` → `farming_block` | operator-placed |
| Well | `britannia_mod:water_well` | operator-placed |
| Achievement | website "Quest: First Harvest"; toast "First Harvest"; advancement `britannia_mod:quest/first_harvest` (proposed) | stage 5 |

## 1. Administrator setup through existing blocks (setup rows, not ordinary play)

1. [ ] **Place two Rowan spawners** in different places (for example the town square and beside
   the farm) with `britannia_mod:quest_giver_spawn_block`, open each, choose the "Rowan" archetype
   (proposed list entry), set the city, gender, radius 5, optional hint text, Save.
   Expected: within about 12 seconds a named "Rowan" with a farmer outfit stands near each block;
   the blocks are invisible to ordinary players. Record both positions.
2. [ ] **Verify Rails content**: admin quest list shows five quests titled "From Soil to
   Supper (n of 5): …", origin NPC `Rowan`, priorities 50/40/30/20/10, each stage after the first
   carrying `prerequisite_quest_id` of the previous stage; re-running the seed task changes nothing
   (row counts and choice keys identical).
3. [ ] **Infrastructure present near each Rowan**: exposed `minecraft:dirt`/`coarse_dirt` surface
   (dung-eligible, not grass) within roughly 20 blocks, a Water Well, at least four base Community
   Farm Blocks outside spawn protection. Record coordinates.
4. [ ] **Portrait**: confirm `portraits/<gender>/Rowan.png` exists in the bucket; otherwise note
   that the generic peasant will show (not a failure of the questline logic).
5. [ ] **Skill roster**: confirm the shard has a Skill named "Farming" (slug `farming`).

## 2. Ordinary-player walkthrough

### Stage 1 — Shovel Dung

6. [ ] **Talk to Rowan** (Use on the NPC). Expected: dialogue with Rowan's portrait, name "Rowan",
   profession "Farmer", the stage 1 offer text, a "You receive now: Britannia Shovel" line, a
   "Reward when done" line with the coin icon, choices "I'll do it" / "Not now". Inventory
   unchanged so far.
7. [ ] **Choose "Not now"**, then talk again. Expected: the same offer returns and no items are
   granted. On the current build the stage already appears in the journal as accepted after the
   first click; with the proposed change "Not now" abandons it and it does not appear until
   accepted. Also press ESC on the offer once: the offer must return on the next click.
8. [ ] **Choose "I'll do it"**. Expected: exactly one Britannia Shovel appears in the inventory;
   dialogue moves to "Working" with "How do I find dung?" / "Farewell"; the journal (menu key →
   Quests) shows "From Soil to Supper (1 of 5): Shovel Dung", "Rowan · Farmer · Quest 1 of 5",
   Next line, and progress ticks. Accounting: +1 shovel.
9. [ ] **Reconnect now.** Expected: shovel still present (D1 fix), journal entry still present
   with the same Next line, dialogue reopens at "Working".
10. [ ] **Find a dung pile** on bare dirt near the hint. If none stands, note the wait until one
    appears (the proposed acceptance hook schedules an attempt immediately; without it expect
    6–12 minutes and no guarantee). Hold the shovel, Attack the pile once. Expected: the pile
    vanishes, one Dung drops; walk over it. Accounting: +1 Dung; shovel unchanged.
11. [ ] **Objective feedback.** Expected: a toast "Objective complete — return to Rowan" (or, if
    the popup design is kept, a dialogue titled with Rowan's name, never "The Guardian"); journal
    progress shows "Dung collected ✓". The Dung stays in the inventory.
12. [ ] **Claim**: talk to Rowan (either instance), choose "Claim reward". Expected: G1 gold coins
    (named "Gold Coin"), "Complete" text mentioning keeping the dung, "Farewell" closes. The stage
    leaves the journal. Accounting: +G1 gold; Dung retained; shovel retained.
13. [ ] **Repeat-claim check**: talk to Rowan again immediately. Expected: stage 2's offer, not a
    second stage-1 reward; coin count unchanged.

### Stage 2 — Shovel Dirt

14. [ ] **Accept stage 2.** Expected: journal "(2 of 5)", Next: right-click bare dirt with the
    shovel.
15. [ ] **Gather**: shovel in main hand, off hand empty, Use on exposed dirt. Expected: "You
    gather a handful of dirt.", +1 Dirt, terrain unchanged, shovel −1 durability; objective toast.
    Try a second gather at once: "You can gather dirt again in N seconds." and no extra dirt.
16. [ ] **Claim.** Expected: C2 copper coins named "Copper Coin", 2 Empty Bowls (wooden bowl
    icon), 1 Bucket. Accounting: Dung 1, Dirt 1, Empty Bowl 2, Bucket 1, shovel, coins.

### Stage 3 — Fill the Water Bucket

17. [ ] **Accept stage 3.** Expected: Next line names the well and gives directions or the
    administrator fallback text.
18. [ ] **Negative**: Use the bucket on a river or lake. Expected: nothing happens (vanilla
    Adventure refusal) and the proposed hint "Fill the bucket at the well." appears; bucket
    unchanged.
19. [ ] **Fill at the well**: Use on the Water Well with the bucket. Expected: fill sound, Water
    Bucket in hand, objective toast, well unchanged.
20. [ ] **Claim.** Expected: S3 silver coins named "Silver Coin", Watering Can showing
    "Water: 12 / 12", one Carrot Seeds (or the awarded pool seed). The Water Bucket stays and is
    never asked for again. Accounting: +S3 silver, +1 can, +1 seed.
21. [ ] **Seed persistence**: reconnect and reopen the stage 5 preview later; the crop named in
    stage 5 must be the crop awarded here (only meaningful if the pool exceeds one).

### Stage 4 — Mix Fertilized Dirt

22. [ ] **Accept stage 4**; open "How do I mix it?" Expected: the two-column mixing guide with
    hands, ingredients, results, and returned bowls; no mention of the bucket.
23. [ ] **Bowl of Dirt**: Empty Bowl main hand, Dirt off hand, aim at the sky, Use. Expected:
    Bowl of Dirt in main hand; journal tick. Negative first: reversed hands → "Swap hands…" and no
    change.
24. [ ] **Bowl of Fertile Dirt**: Bowl of Dirt main, Dung off hand, Use in air. Expected: Bowl of
    Fertile Dirt; Dung consumed.
25. [ ] **Bowl of Water**: the other Empty Bowl main, off hand empty, Use on the well (or still
    water). Expected: Bowl of Water. Negative: flowing water → the "still water" message.
26. [ ] **Final mix**: Bowl of Fertile Dirt main, Bowl of Water off hand, Use in air. Expected:
    1 Fertilized Dirt in main hand and exactly 2 Empty Bowls in the off hand; objective toast.
    Accounting: Dung 0, Dirt 0, Empty Bowl 2, Fertilized Dirt 1.
27. [ ] **Claim.** Expected: G4 gold coins and one Farming Hoe; "Keep the fertilizer" text.

### Stage 5 — Plant and Harvest

28. [ ] **Accept stage 5.** Expected: Next line lists the five sub-steps with directions to the
    plots; progress shows five unchecked ticks.
29. [ ] **Skill readiness**: the skills screen shows Farming with a loaded value; if it shows
    unavailable, wait for the proposed automatic retry (or reconnect on the current build) before
    spending fertilizer.
30. [ ] **Hoe** an unused base Community Farm Block with the hoe. Expected: "Public plot hoed…",
    the countdown, hoe −1 durability, tick "Soil prepared".
31. [ ] **Fertilize** the hoed plot within the window. Expected: gravel sound, "Public plot
    fertilized…", Fertilized Dirt consumed, tick "Fertilized", the seed-window countdown.
32. [ ] **Plant** the seed on the soil top, off hand empty, not sneaking, within the window.
    Expected: planting sound, seed consumed, sprout visible, tick "Planted". Negative on a second
    plot: nothing is consumed if the skill data is unavailable (message shown).
33. [ ] **Water** with the can: Use on the soil twice. Expected: two dispense sounds, can shows
    10/12, tick "Watered", the proposed "Water twice, then wait" hint once; a third use is allowed
    but the hint discourages it.
34. [ ] **Wait** near the plot. Expected: visible growth stages; at `randomTickSpeed 3` and
    hydration 3 expect roughly 8–12 minutes to maturity; record the actual time. Leaving the chunk
    pauses growth; note if used.
35. [ ] **Harvest** the mature carrot with an empty hand. Expected: three Carrots drop, the plot
    re-opens for planting, objective toast, tick "Harvested".
36. [ ] **Claim.** Expected: G5 gold coins, toast "Achievement Unlocked! First Harvest" with the
    challenge sound, the advancement in the Advancements screen, and the questline farewell.
    Talking to Rowan again: "I have nothing else for you right now." or the proposed graceful
    farewell. Accounting: all tools retained, 3 Carrots, coins from five stages.
37. [ ] **Website**: the shard achievements page lists "Quest: First Harvest" for this player
    (requires a verified, non-provisional account with a public username).

## 3. Inventory and reward accounting (expected)

| After | Dung | Dirt | Empty Bowl | Bucket | Water Bucket | Watering Can | Seed | Fertilized Dirt | Tools | Coins |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| 8 accept stage 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | Shovel | — |
| 12 claim stage 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | Shovel | G1 gold |
| 16 claim stage 2 | 1 | 1 | 2 | 1 | 0 | 0 | 0 | 0 | Shovel (−1) | + C2 copper |
| 20 claim stage 3 | 1 | 1 | 2 | 0 | 1 | 1 (12/12) | 1 | 0 | Shovel | + S3 silver |
| 26 final mix | 0 | 0 | 2 | 0 | 1 | 1 | 1 | 1 | Shovel | — |
| 27 claim stage 4 | 0 | 0 | 2 | 0 | 1 | 1 | 1 | 1 | Shovel, Hoe | + G4 gold |
| 33 watered | 0 | 0 | 2 | 0 | 1 | 1 (10/12) | 0 | 0 | Shovel, Hoe (−1) | — |
| 36 claim stage 5 | 0 | 0 | 2 | 0 | 1 | 1 | 0 | 0 | Shovel, Hoe, 3 Carrots | + G5 gold |

No item is confiscated at any claim. Coin quantities are unresolved (planning inputs U1).

## 4. Multiple placed Rowans

38. [ ] Accept stage 2 at Rowan A, gather dirt, claim at Rowan B. Expected: identical to claiming
    at A; both Rowans then offer stage 3.
39. [ ] Break and re-place Rowan A's spawner as an administrator while the player is mid-stage.
    Expected: the respawned Rowan resumes the same stage; no duplicate offer, no lost progress.
40. [ ] Two ordinary players on stage 5 at the same time. Expected: each harvests only their own
    planted plot for credit; harvesting the other's plot never completes the objective and the
    victim gets "Someone harvested that plot…" guidance.

## 5. GUI checks

41. [ ] Dialogue at GUI scale 1, 2, 3, 4 and in a 640 × 360 window: no text overflows the
    parchment band; long bodies scroll; three buttons fit; icons and labels legible.
42. [ ] Journal: rows fit the panel at every scale; keyboard Tab/Enter works; Quit asks for
    confirmation; the Next line updates without closing the journal after an objective completes.
43. [ ] Colour independence: every status line carries a text prefix, not only a colour; locked
    choices show a reason on hover.
44. [ ] Rebound keys: rebind Use and the menu key, reopen the help text; it names the new keys.
45. [ ] Tooltips: all coins, bowls, and tools show real names, not registry keys.

## 6. Reconnect, resume, interruption

46. [ ] Reconnect during each stage (before and after the objective fires). Expected: same node,
    same progress ticks, no rerolled seed, no duplicate items.
47. [ ] Disconnect between claiming and receiving (pull the plug right after "Claim reward").
    Expected on return: the items are present exactly once (server-side grant) or the claim is
    still offered; never zero and never double.
48. [ ] Backend interruption: with Rails stopped, harvest the crop. Expected: "Rowan is checking
    your work…", no reward, no loss; when Rails returns within the retry window the objective
    completes without the player redoing the harvest. If the player logs out first, the journal
    shows the objective still pending and the recovery path applies.
49. [ ] Backend interruption on claim: Rails stopped, click "Claim reward". Expected: "Rowan is
    checking your work…", button re-enabled after the failure; when Rails returns the claim
    succeeds once; a second click after success says the stage is complete.
50. [ ] Repeated reward claims: spam-click "Claim reward". Expected: one grant.

## 7. Recovery

51. [ ] Lose the shovel (drop it into lava as an experiment) during stage 2. Expected: "I lost my
    shovel" appears once on Rowan's Working node, grants one shovel, then shows locked with a
    reason.
52. [ ] Lose the seed after stage 3. Expected: one reissue on stage 5's node; a second request is
    refused with a clear message.
53. [ ] Let the hoed plot expire. Expected: it reverts; hoe again; nothing else lost.
54. [ ] Let the fertilized plot expire. Expected: the plot reverts and the fertilizer is gone;
    the journal says to mix again; stage 4 stays complete.
55. [ ] Quit stage 3 from the journal (confirm). Expected: stage 3 restarts from its offer;
    stages 1–2 remain complete; the bucket and bowls remain.

## 8. Crop attribution

56. [ ] Player B plants on a plot Player A hoed and fertilized. Expected: allowed (public), but
    A's harvest of B's crop gives A no credit, and vice versa.
57. [ ] Player A picks up carrots Player B dropped. Expected: no credit.
58. [ ] Player A plants two plots and harvests both. Expected: exactly one objective completion.

## 9. Administrator fixtures and diagnostics (labelled; never ordinary-play evidence)

* `/give <player> britannia_mod:britannia_shovel 1`, `... empty_bowl 2`, `... minecraft:bucket 1`,
  `... watering_can 1`, `... carrot_seeds 1`, `... farming_hoe 1` — component testing of stages
  in isolation; bypasses acquisition and every reward path.
* `/setblock X Y Z britannia_mod:community_farm_block` and a well — bypasses infrastructure
  placement, not gameplay.
* `/skill <player> farming 0` — marks skill data available locally and writes to Rails; use only
  on a disposable identity.
* `/data get block X Y Z PlantedCropId`, `GrowthStage`, `Hydration`, `RemainingFertileHarvests`,
  and the proposed `PlanterUUID` — read-only verification by an operator.
* Server log lines to capture: `event=quest_objective_detected`, `event=quest_objective_applied`,
  `event=quest_action_result`, `[farming planting] … planted=true`, `[farming harvest] …
  harvested=true`; Rails `event=quest_turn_in_result`, `event=quest_trigger_replayed`,
  `event=quest_achievement_awarded`.
* Existing automated evidence at the inspected HEAD: mod selection 264 tests / 0 failures / 6
  assumption skips; Rails selection 58 runs / 0 failures (disposable database). Neither drives a
  farming objective; the new GameTests listed in the planning inputs are the smallest meaningful
  integration tests to add.

## 10. Acceptance record

| Evidence category | Result |
| --- | --- |
| Source, registrations, configuration, history | Inspected (discovery document) |
| Mod JUnit selection | 264 / 0 / 6 skipped at `0cd566dd` |
| Rails quest selection | 58 runs / 0 failures at `5b4a22d` (isolated DB) |
| GameTests | Inspected, not run |
| Ordinary-player walkthrough | NOT RUN — requires the planned work |
| Multi-instance, reconnect, interruption, attribution rows | NOT RUN |
| Live configuration, seeds, portrait bucket | UNVERIFIED |
