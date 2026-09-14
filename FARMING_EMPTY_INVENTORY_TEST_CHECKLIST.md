# Farming from empty inventory: manual test checklist

Version: 2026-09-06, `patch-18`, HEAD `0cd566dd811bb717c6bda498ac3bed38be64b95e`, Minecraft 1.21.1 / NeoForge 21.1.72 / mod 0.1.8a. **Manual checklist NOT EXECUTED.** All gameplay results below are expected from code inspection, not observations.

**Current verdict: blocked.** The first confirmed missing acquisition step is the exact Britannia Shovel, followed independently by two custom Empty Bowls. Public planting also requires an unproven Farming Hoe source, a first seed, accessible authored land and loaded skill data. Do not turn these gaps into supplied starting materials. The [discovery report](FARMING_GAMELOOP_DISCOVERY.md) explains the evidence, runtime overrides and limitations. `S1`–`S20` below refer to its [source reference index](FARMING_GAMELOOP_DISCOVERY.md#source-reference-index).

## Starting contract and recording

Use a new ordinary **non-operator** account, empty main inventory/hotbar, equipment/armor and offhand, zero supplied coins, no bank balance or restored assets, no house/farm ownership and no borrowed tools, stocked containers or another player's produce. Use only the normal new-player skill allocation. Do not change game mode, use Creative, receive gifts, use commands or consume fixtures to mark an ordinary step passed.

Normal mode is **Adventure**, enforced by the mod. A `gamemode=survival` server property does not override that behavior. Record server/build/world, player role, observed mode, enabled packs, relevant game rules, backend skill readiness and available public infrastructure. Ask the world operator to supply those configuration facts through the normal test record if the ordinary player cannot see them; do not grant the tester extra permissions. Repository defaults are not proof of the live shard configuration. [S1, S8, S12]

Use default controls or their rebound equivalents: `E` opens inventory; select a hotbar slot for main hand; drag an item into the offhand slot in inventory (or `F` to swap hands); left mouse is Attack/Destroy; right mouse is Use; Shift is Sneak. Split stacks to **one** input at a time with inventory controls. Keep offhand empty unless a step explicitly needs an ingredient. For item-only mixing, aim into clear dry air away from water, blocks and entities; do not sneak. For land interactions, aim at the top face and do not sneak so the block's Adventure-compatible callback runs.

Record each step as PASS / FAIL / BLOCKED / NOT RUN, actual inventory deltas, coordinates, timing, messages and whether reconnect/reload preserved the result. Leave unchecked boxes unpassed. A known blocked earlier step means every later dependent step is **CONDITIONAL**, even if an administrator can demonstrate it independently.

### Exact inventory identities

| Label used below | Exact ID | Required quantity / disposition |
| --- | --- | --- |
| Dung | `britannia_mod:dung` | 1 consumed in mixing; same ID also names its natural block |
| Dirt | `britannia_mod:dirt` | 1 consumed in mixing; vanilla dirt **item** is invalid |
| Shovel | `britannia_mod:britannia_shovel` | 1 retained; at least 2 durability remaining before gather if it is to survive |
| Empty Bowl | `britannia_mod:empty_bowl` | 2 reusable; both returned by final mix |
| Bowl of Dirt | `britannia_mod:bowl_of_dirt` | 1 intermediate |
| Bowl of Fertile Dirt | `britannia_mod:bowl_of_fertile_dirt` | 1 intermediate |
| Bowl of Water | `britannia_mod:bowl_of_water` | 1 intermediate |
| Fertilized Dirt | `britannia_mod:fertilized_dirt` | 1 made, then consumed on soil |
| Hoe | `britannia_mod:farming_hoe` | 1 retained for public route; at least 2 of its 128 durability remaining |
| Wheat Seeds | `minecraft:wheat_seeds` | 1 consumed at the endpoint |

No processing station, crafting grid, feeding item, bucket, bottle, watering can, nutrient additive, trellis or extra planting tool is required by the conditional wheat transactions. There are no fixed coin costs in those transactions; **acquisition costs remain unknown**, not free. A well is optional when natural source water is reachable. [S4–S10, S14]

## Ordinary-player sequence

1. [ ] **Establish the empty start and world access.**

   **Start:** empty inventory/equipment/offhand, no assets/property, ordinary new-player progression. **Action:** log in normally, open inventory (`E`), confirm nothing has been supplied/restored, then walk to publicly accessible open terrain. Record skill UI/readiness and the world's configured mode; do not assume a displayed Farming 0 means the backend loaded. Identify reachable exposed dirt/coarse dirt, source water or a public well, and an unused base Community Farm Block if one exists. Animals need not be present.

   **Expected:** ordinary gameplay is Adventure; exploration is possible without a tool. Terrain/infrastructure presence is a test observation, not guaranteed by the code. **Accounting:** no items consumed/returned; inventory remains empty. **Conditions/failure:** absent accessible terrain, water or plots is a world/setup blocker. Skill-unavailable is a later planting blocker; continue independent dung exploration. Do not dig through protected grass/stone or enter another player's supplies to create this infrastructure. **Evidence:** S1–S3, S8, S12–S13.

2. [ ] **Find and collect one naturally scheduled Dung.**

   **Start:** empty inventory and main/offhand; reachable loaded area with exposed exact `minecraft:dirt` or `minecraft:coarse_dirt`, dry surface above. **Action:** keep the area loaded by remaining nearby and search the exposed surfaces for a low dung pile. Aim at the pile and left-click once with empty main hand; walk over the dropped item to pick it up.

   **Expected:** in Adventure, server-authorized removal replaces the pile with air and drops exactly 1 Dung. It enters inventory when collected. No animal feeding or interaction is involved. **Accounting:** terrain beneath retained; no tool/container used; inventory now **1 Dung**. **Conditions/failure:** initial spawn attempts are scheduled 6–12 minutes apart, with 4 probes, cap 2/chunk and 8-block spacing; an unsuccessful terrain probe means a later attempt, not a guaranteed spawn. Removal schedules a 20–40-minute delay to another attempt. Unloaded chunks, ineligible support, ledger absence, reach, house/spawn protection or disabled tile drops can prevent the expected result. Record absence and location; do not declare the producer missing just because a timer passed. **Evidence:** S2–S3, S13, S15.

3. [ ] **FIRST CONFIRMED BLOCKER — obtain one exact Britannia Shovel from this start.**

   **Start:** at most the legitimately collected **1 Dung**, no coins/tools/bank assets. **Action:** inspect the inventory crafting/recipe UI for the exact custom tool. If a publicly accessible merchant exists, approach it, select an empty main-hand slot, right-click the merchant and inspect its actual catalog without purchasing an unrelated shovel. Record the item ID, stock, price and denomination if an offer exists, and the actual ordinary earnings/actions that could fund it from this inventory.

   **Expected under repository-defined acquisition:** **BLOCKED — no recipe, loot, fixed catalog or starter connection produces `britannia_mod:britannia_shovel`.** A live catalog may differ and must be evidenced before passing. **Accounting:** inspection spends nothing; inventory stays **1 Dung**. Do not substitute a wooden/iron/diamond vanilla shovel or conjure a configured tool from a helper/Creative tab. **Conditions/failure:** an empty/unreachable catalog is external unavailability, not proof of absent production stock. A paid offer does not close the step until the actual income route, price, payment and correct tool delivery are recorded. No numeric budget is supportable from the inspected data. **Evidence:** S4–S5, S12, S17.

   **STOP the empty-inventory acceptance route here. Steps 4–15 are conditional descriptions, not a playable continuation under established repository acquisition.** If a live ordinary acquisition route is later demonstrated, retain its record and continue without resetting or supplementing the inventory.

4. [ ] **CONDITIONAL / independent acquisition blocker — obtain two custom Empty Bowls.**

   **Start if step 3 is genuinely resolved:** 1 Dung + 1 Shovel, plus only recorded earned currency/change. **Action:** inspect actual crafting/merchant offers as in step 3 for **`britannia_mod:empty_bowl`**, quantity **2**, and record a complete ordinary payment/source route before obtaining them.

   **Expected today:** no repository-defined acquisition source; mark BLOCKED. If an external route is proven, expect exactly 2 Empty Bowls delivered. **Accounting on a proved purchase:** subtract only its recorded earned-price payment and add 2 Empty Bowls; retain Dung/Shovel and record any earned change separately. No fixed price is known. **Conditions/failure:** `minecraft:bowl`, `empty_pewter_bowl` and food bowls fail the later tuple. Returning 2 bowls from a final mix that already needs 2 bowls is circular and cannot pass this step. A crafting table and planks do not craft the required custom identity. **Evidence:** S5–S7, S12, S14.

5. [ ] **CONDITIONAL / public-route acquisition blocker — obtain one Farming Hoe.**

   **Start:** 1 Dung + Shovel + 2 Empty Bowls and any recorded earned change. **Action:** locate and use only a proved ordinary source of **`britannia_mod:farming_hoe`**, recording payment or recipe inputs and its source. Confirm the tooltip identifies Farming Hoe and enough durability remains for a retained tool.

   **Expected today:** no local recipe/loot/fixed sale was found; mark BLOCKED unless live acquisition is proven. **Accounting if resolved:** add **1 Hoe**, retain all prior materials, subtract only documented earned cost. **Conditions/failure:** vanilla hoes do not satisfy the public-plot exact-item test. Do not skip this by selecting a plot another player already hoed/fertilized or borrowing their tools. **Evidence:** S5, S9, S12, S14.

6. [ ] **CONDITIONAL / seed bootstrap check — obtain exactly one Wheat Seed.**

   **Start:** 1 Dung + Shovel + Hoe + 2 Empty Bowls, no seed and no owned/established crop. **Action:** with empty main/offhand, try a left-click on reachable ordinary short grass outside protection, recording whether it is actually breakable. Do not confuse breaking the grass plant with its grass-block support. If no legitimate grass-loot path exists, inspect an accessible vendor's exact `minecraft:wheat_seeds` listing and its price/income route before attempting a purchase. Do not harvest another player's crop/container.

   **Expected in normal landless Adventure:** ordinary bare-hand grass breaking is restricted; **no reliable seed acquisition is established**. If a permitted **unmanaged vanilla** grass/fern breaking route is independently proved, each non-shears/no-Fortune break has a 12.5% chance of 1 seed; continue only until collecting 1, with no finite guaranteed number of breaks. A managed grass cut deliberately gives zero seeds even with an authorized blade. **Accounting:** failed restricted swings consume nothing; conditional success adds **1 Wheat Seeds**, retaining the kit. A proved vendor purchase spends only documented earned currency. **Conditions/failure:** an existing crop's seed return is circular; a registered Farmer or Creative seed is not a starter source. Log BLOCKED if no source is established. **Evidence:** S8, S10, S12, S15–S16.

7. [ ] **CONDITIONAL — gather one custom Dirt without breaking terrain.**

   **Start:** 1 Dung + 2 Empty Bowls + Shovel + Hoe + 1 Wheat Seeds; one reachable exact vanilla dirt/coarse-dirt block outside foreign-house/spawn restrictions. **Action:** hold the Shovel in **main hand**, offhand empty, aim at the terrain block and right-click once. Pick up overflow if it drops, although this inventory has room.

   **Expected:** **“You gather a handful of dirt.”** Inventory gains exactly 1 **`britannia_mod:dirt`**. The world block stays dirt/coarse dirt; it does not become a path or disappear. **Accounting:** +1 Dirt; Shovel loses **1 durability** and is retained; Dung, bowls, Hoe and seed unchanged. **Conditions/failure:** exact custom shovel, reachable permitted target, real ordinary player and a ready per-player cooldown. One successful gather imposes 1,200 ticks (60 seconds at 20 TPS); failure/cooldown must not grant dirt or charge durability. No Mining/Farming minimum or Mining gain is involved. **Evidence:** S4, S13–S14.

8. [ ] **CONDITIONAL — make a Bowl of Dirt.**

   **Start:** preceding kit + 1 custom Dirt. **Action:** in `E`, split bowls so **1 Empty Bowl is main hand**, **1 custom Dirt is offhand**, and the second Empty Bowl remains in inventory. Close inventory, aim into clear dry air and right-click once without sneaking.

   **Expected:** composter-fill sound and **1 Bowl of Dirt in main hand**; offhand empties. **Accounting:** consume 1 Empty Bowl + 1 custom Dirt; retain the other Empty Bowl, 1 Dung, Shovel, Hoe and seed. **Conditions/failure:** custom identity and exact hand order; pointing at source water fills instead, while an interactive block/entity may handle use first. Wrong tuples ordinarily give no custom error and leave inputs unchanged. No crafting grid, station or waiting time. **Evidence:** S6–S7, S14.

9. [ ] **CONDITIONAL — add Dung to the dry dirt bowl.**

   **Start:** main 1 Bowl of Dirt; 1 Dung, 1 Empty Bowl, retained tools and seed in inventory. **Action:** move **1 Dung to offhand**, keep Bowl of Dirt in main; right-click dry air once.

   **Expected:** composter-fill sound, **1 Bowl of Fertile Dirt in main**, offhand empty. **Accounting:** consume 1 Bowl of Dirt + 1 Dung; retain 1 Empty Bowl + Shovel + Hoe + seed. **Conditions/failure:** order matters; Empty Bowl + Dung is not a recipe. This output is still an intermediate bowl, not the soil treatment. **Evidence:** S6, S14.

10. [ ] **CONDITIONAL — fill the other bowl with water.**

    **Start:** 1 Bowl of Fertile Dirt, 1 Empty Bowl, retained tools and seed; reachable source water **or** public Water Well. **Action:** put the fertile bowl in inventory, hold the **single Empty Bowl in main hand**, clear offhand, aim directly at a **water source** and right-click. Alternatively right-click an accessible part of the existing Water Well; do not build/provision one for this ordinary route.

    **Expected:** fill sound; **1 Bowl of Water replaces the Empty Bowl**. Water source remains, or the well remains intact without depletion. **Accounting:** exchange 1 Empty Bowl for 1 Bowl of Water; fertile bowl/tools/seed retained; no bucket/bottle consumed. **Conditions/failure:** flowing water is invalid. A protected world target may consume the click animation but must not exchange the bowl. Rain, a cauldron or a water bucket in offhand is not the implemented filling method. Water filling can support either hand, but this main-hand/empty-offhand setup avoids ambiguous gestures. **Evidence:** S6–S7.

11. [ ] **CONDITIONAL — finish Fertilized Dirt and recover both containers.**

    **Start:** 1 Bowl of Fertile Dirt + 1 Bowl of Water + Shovel + Hoe + 1 Wheat Seeds. **Action:** main hand **Bowl of Fertile Dirt**, offhand **Bowl of Water**; right-click clear dry air once.

    **Expected:** composter-fill sound; **1 Fertilized Dirt in main hand and exactly 2 Empty Bowls in offhand**. **Accounting:** both filled bowls consumed; 1 `britannia_mod:fertilized_dirt` and 2 `britannia_mod:empty_bowl` produced; tools/seed retained. No loose dirt/dung or filled intermediate bowl remains. **Conditions/failure:** no extra dirt/water/skill/processing time. An arm swing/SUCCESS with unchanged stacks can be a stale/denied call; verify item deltas. A result named `fertile_dirt`, one returned bowl, extra bowls or duplicate fertilizer fails. **Evidence:** S6, S14.

12. [ ] **CONDITIONAL — verify seed/skills, then hoe an unused public base plot.**

    **Start:** 1 Fertilized Dirt + 2 Empty Bowls + Shovel + Hoe + 1 Wheat Seeds. **World:** accessible unused **`britannia_mod:community_farm_block`**, no prior preparation or crop; Farming data available with value >= 0. **Action:** resolve skill readiness first; store the fertilizer/bowls safely in your own inventory, hold the Hoe in **main hand**, offhand empty, and right-click the plot's top without sneaking.

    **Expected:** till sound, **Hoed Community Farm Block** and **“Public plot hoed. Apply fertilized dirt within 3 minutes.”** **Accounting:** 1 Hoe durability spent, tool retained; all six inventory types remain at the same counts. **Conditions/failure:** use the exact base block/custom hoe. Bare natural dirt is not hoed by this tool. No property ownership or skill minimum is tested by this public preparation callback. An unavailable plot/hoe is a blocker, not permission to use someone else's prepared farm. Do not use the ordinary-dirt alternative to bypass Adventure restrictions. **Evidence:** S8–S9, S13–S14.

13. [ ] **CONDITIONAL — apply Fertilized Dirt to the hoed public plot.**

    **Start:** hoed public plot prepared less than **3,600 server ticks** ago; 1 Fertilized Dirt, 2 Empty Bowls, retained Shovel/Hoe, 1 Wheat Seeds. **Action:** hold Fertilized Dirt in **main hand**, offhand empty; right-click the hoed plot's top without sneaking.

    **Expected:** gravel placement sound, soil changes to **`britannia_mod:farming_block`**, message **“Public plot fertilized. Plant seeds within 60 seconds.”** Initial hydration is 1 in blockstate and BE; `has_seeds=false`; tracked fertility is 5. **Accounting:** consume exactly **1 Fertilized Dirt**; remaining inventory **2 Empty Bowls + Shovel + Hoe + 1 Wheat Seeds**. No empty bowl is consumed on land.

    **Conditions/failure:** fertilizer on an unhoed Community Farm Block only displays **“Use a farming hoe on this public plot first.”** and must not consume it. An expired hoed plot may already have reverted. The fertilized empty plot is reclaimed on a random tick after its 1,200-tick deadline, without refund; do not rely on a delayed/random-tick-disabled expiry to plant late. **Evidence:** S9–S10.

14. [ ] **CONDITIONAL — plant the first Wheat Seed.**

    **Start:** empty prepared Farming Block inside its advertised 60-second seed window; loaded authoritative Farming >= 0; 2 Empty Bowls + Shovel + Hoe + **1 Wheat Seeds**. **Action:** select the seed in **main hand**, offhand empty, aim at the **top of the soil**, do not sneak, and right-click once.

    **Expected:** crop planting sound; **one seed consumed**, soil keeps its Farming Block identity and becomes `has_seeds=true`; crop presentation begins at wheat age 0. Server BE is `PlantedCropId="wheat"`, `GrowthStage=0`, `GrowthProgress=0`, `Mature=false`; community deadline clears. **Accounting:** seed -1; remaining **2 Empty Bowls + retained Shovel + retained Hoe** (plus only separately recorded earned change). Fertility remains **5**. No nutrients, nearby water, light/season/climate threshold, trellis, scissors or grain blade are needed to accept this planting.

    **Conditions/failure:** reject occupied/foreign-owned plots or unavailable skill data without seed loss. The explicit backend failure text is **“Farming skill data is unavailable; planting is blocked.”** No zero-skill bypass exists. There need not be a separate vanilla wheat block above the soil: the custom BE renders the crop. Client visuals or an accepted gesture alone do not pass the step. **Evidence:** S8, S10, S14.

15. [ ] **CONDITIONAL — establish that the server kept the planting.**

    **Start:** alleged success from step 14, no remaining wheat seed. **Action:** wait for ordinary inventory/block synchronization, reopen inventory, then disconnect/reconnect normally or leave and return after the chunk has genuinely unloaded. Revisit the recorded position. If another ordinary observer is available, have them observe the crop without interacting with it. Stop at planting; do not wait for a harvest just to declare success.

    **Expected:** seed stays consumed and the planted wheat remains after a fresh server sync; age may advance if time passed. Soil does not revert as an empty expired community plot. **Accounting:** no additional consumption; 2 Empty Bowls and both tools retained, each with exactly the one durability charge specified. **Conditions/failure:** a seed restored on resync, disappearing crop or empty soil after reconnect is a failure/uncertain server result; record timing rather than accepting the earlier animation. For strict endpoint evidence, an independent operator can read only the target BE/log as described below while the tested player remains non-op. **Evidence:** S8, S10, S18.

## Inventory accounting for one conditional public-plot attempt

Acquisition rows remain blocked/unpriced. This table begins **only after** tools/bowls/seed have been legitimately acquired and Dung collected; it cannot serve as a supplied starting kit for an empty-inventory PASS. No animal feed, crafting material or coins are silently added. Record any real acquisition earnings/spending in a separate ledger.

| After step | Dung | Loose Dirt | Empty Bowls | Bowl of Dirt | Fertile bowl | Water bowl | Fertilized Dirt | Wheat Seeds | Retained tools |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 6: acquired conditional inputs | 1 | 0 | 2 | 0 | 0 | 0 | 0 | 1 | Shovel + Hoe |
| 7: gather | 1 | 1 | 2 | 0 | 0 | 0 | 0 | 1 | Shovel -1 durability; Hoe unchanged |
| 8: dirt bowl | 1 | 0 | 1 | 1 | 0 | 0 | 0 | 1 | unchanged |
| 9: dung added | 0 | 0 | 1 | 0 | 1 | 0 | 0 | 1 | unchanged |
| 10: water | 0 | 0 | 0 | 0 | 1 | 1 | 0 | 1 | unchanged |
| 11: final mix | 0 | 0 | 2 | 0 | 0 | 0 | 1 | 1 | unchanged |
| 12: public hoe | 0 | 0 | 2 | 0 | 0 | 0 | 1 | 1 | Hoe -1 durability |
| 13: soil conversion | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 1 | unchanged |
| 14–15: planted and resynced | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | Shovel + Hoe retained |

Minimum physical consumption: **1 Dung + 1 custom Dirt + one bowl's water + 1 wheat seed**; one mixed Fertilized Dirt is produced and then consumed. Two custom containers are conserved. World dirt used for gathering and the water source/well are retained. One public base plot is converted. Tool durability is a cost, not a consumed whole tool with sufficient remaining durability.

## Alternate soil and fertilizer paths — do not count as the primary route

* **Ordinary exact vanilla dirt:** with 1 Fertilized Dirt in main hand, offhand empty, right-click its top. A normal landless Adventure player is expected to get **no conversion and no item loss** because the item lacks the required Adventure placement predicate. A valid owner standing in their own house can have `mayBuild`; with permitted exact dirt inside that house the real item path may convert it without a hoe. This requires proving house acquisition, access and terrain availability; it is not an empty-start shortcut. Conversion initializes hydration 1/fertility 5 and consumes one fertilizer, but does **not** assign a plot owner. No separate dirt item is added. [S1, S9–S11, S13]
* **House Farm Plot:** a different owned/persistently assigned system, initially assigned poppy. It does not accept Fertilized Dirt as a new soil conversion. Ownership and exact Hoe clearing are required before reassignment. Do not switch to this block to hide public-tool/land gaps. [S13]
* **Earth Elemental:** a separate combat loot roll can yield 1–2 of the same Fertilized Dirt at 35%. Record the actually reached mob and combat/loot; do not supply a kill or assume equipment. This bypasses dung, dirt gathering and both bowls and cannot pass steps 3–11. Seeds, usable land and available Farming still matter. [S19]

## Separate diagnostics / fixtures

**These are setup for component testing only, not ordinary acquisition.** Commands below were verified against the item/block registrations, local 1.21.1 command definitions and the registered custom commands; **they were not executed here**. Use a disposable development world and a separate administrative operator/console. Keep the actual test player non-op in Adventure. Choose fresh, loaded, reachable coordinates outside spawn/house protection; replace `Tester` and `X Y Z` with the real name and a chosen plot position.

### Component fixture A: inputs, gather and bowl chain

```mcfunction
/give Tester britannia_mod:britannia_shovel 1
/give Tester britannia_mod:empty_bowl 2
/give Tester britannia_mod:farming_hoe 1
/give Tester minecraft:wheat_seeds 1
/give Tester britannia_mod:dung 1
```

This bypasses every missing equipment/seed source and natural dung spawning/collection. Use existing eligible dirt/coarse dirt and source water for steps 7–11, or have the operator create a single appropriate target if testing in an empty fixture world and record that world bypass too. If isolating only mixing, `/give Tester britannia_mod:dirt 1` also bypasses shovel gathering; do not then report step 7 as tested. Do not set a bare dung block and call it a natural node: `/setblock ... britannia_mod:dung` does not establish the WildResource saved-data ledger used by Adventure harvest. Use the existing dung GameTest fixtures to test tracked nodes independently. [S3–S6, S14, S20]

### Component fixture B: public soil and first planting

An operator may create a base plot at a documented location:

```mcfunction
/setblock X Y Z britannia_mod:community_farm_block
```

Then the **non-op tester** performs steps 12–15 with their fixture Hoe, mixed fertilizer and fixture seed. This bypasses public-land placement and all fixture-input acquisitions but still exercises normal Adventure block-use for hoeing, fertilizing and planting. Setting `farming_block` directly bypasses fertilizer application and does **not** initialize its five-use provenance automatically; do not use that substitution to test correct initialization. To isolate only soil/planting, the operator can give 1 `britannia_mod:fertilized_dirt`, explicitly bypassing the entire dung process.

Skill readiness must still be addressed. Prefer a disposable test backend that loads an available Farming value of 0. The registered diagnostic command is:

```mcfunction
/skill Tester farming 0
```

It marks the runtime skill available, bypassing normal skill loading, **and attempts to persist an admin skill update to Rails**. Use it only with a disposable identity/backend or intentionally disconnected disposable setup; do not run it against a real player's account to test an empty start. Its local success does not prove login/readiness or skill persistence. An operator testing planting while personally in Creative/with admin bypass would also conceal the ordinary player's skill gate. [S8, S18]

### Server-side verification without changing the tested player's permissions

An independent operator can query the target immediately after planting:

```mcfunction
/data get block X Y Z PlantedCropId
/data get block X Y Z GrowthStage
/data get block X Y Z RemainingFertileHarvests
```

Expect `"wheat"`, `0` immediately (later growth can advance it), and `5`. Read `Hydration`, `CommunityPlot` and `SeedableUntilGameTime` if investigating soil/window behavior; expect hydration 1 immediately after conversion, community true and deadline 0 after successful planting. An operator looking at the plot can run **`/farming debug`** for server crop/state information. The server planting log should say `crop_id=wheat`, `planted=true`, `reason=planted`. These are read-only observations; the operator must not perform the planting on behalf of the non-op tester. [S10, S18]

### Existing automated fixture coverage

The existing focused JUnit run passed **230/236**, with **6 assumption skips**, no failures/errors; see the discovery report for invocation and XML totals. Existing GameTests were inspected, not run this session. `Patch18FullLoopGameTests` forces a scheduled dung candidate and supplies bowls/tools; its fertilizer call bypasses normal stack dispatch and its crop helper creates mature potatoes. Passing that fixture cannot establish any missing acquisition or the first normally planted seed. No new test suite is part of this task. [S20]

## Focused negative checks

Run these only after documenting the source of the inputs, or mark them **DIAGNOSTIC** if supplied. Use a separate attempt/plot so a destructive or consuming negative check cannot quietly spend the primary route's only input set. Each row specifies its own starting setup; unrelated retained tools/containers stay unchanged unless stated.

| Check | Starting state and action | Expected result / exact accounting | Failure meaning and evidence |
| --- | --- | --- | --- |
| Wrong preparation identity | Main 1 vanilla bowl or Empty Pewter Bowl, offhand 1 custom Dirt; right-click dry air | No custom bowl output; both counts unchanged | Wrong item unexpectedly accepted = identity mismatch; S6, S14 |
| Wrong dirt/order | Main 1 Empty Bowl + offhand vanilla dirt item, or Empty Bowl + Dung; use dry air | Both retained, no output | Unsupported recipe/ingredient loss; S6 |
| Reversed final mix | Main 1 Bowl of Water, offhand 1 Bowl of Fertile Dirt; use dry air | No fertilizer; both bowls retained | Offhand callback must not become a valid final mix; S6 |
| Water priority | Main 1 Empty Bowl, offhand 1 custom Dirt; target actual source water and use | 1 Bowl of Water; Dirt unchanged; source unchanged; no Bowl of Dirt | Water branch should win; S6–S7 |
| Invalid water | Main Empty Bowl, offhand empty; try flowing water, water bucket held elsewhere, rain or cauldron | No Bowl of Water; Empty Bowl retained | False fill or consumption bug. With Dirt offhand a dry-recipe fallback is possible, so keep offhand empty; S6–S7 |
| Protected water | Main Empty Bowl; attempt source/well where effective `mayInteract` denies | Bowl retained, no water output/source mutation; animation alone is inconclusive | World gate bypass or phantom client success; public/private house ownership alone is not the well's gate; S7 |
| Repeated mix / stacked inputs | One valid paid set, then immediately repeat; separately test stacks of 2 valid pairs | First yields 1 fertilizer + 2 bowls; repeat without another valid pair yields nothing; two paid pairs can legitimately yield 2 fertilizer + 4 bowls | Distinguish legitimate stack processing from duplicate packet rewards; S6 |
| Full/partial inventory delivery | Diagnostic inventory full, with main/off input stacks >1 so hands stay occupied; mix once | Exactly one fertilizer + two Empty Bowls delivered to capacity or dropped, both input stacks -1 | Count nearby drops plus inventory; loss/duplication or force-clear bug; S6 |
| Dirt cooldown/tool | One successful gather, then repeat immediately, change shovel/hands, or reconnect within 1,200 ticks | No additional Dirt; no additional durability charge; terrain unchanged | Cooldown bypass. Offhand shovel is not a gather gesture; S4 |
| Invalid/foreign gather | Shovel main; right-click grass/path/farmland or denied foreign-house exact dirt | No loose Dirt from this feature; denied feature-owned exact terrain must not lose durability or flatten | Other vanilla use on unsupported targets is separate; exact-target denied mutation is a bug; S4, S13 |
| Repeated/foreign dung | Naturally tracked node; double left-click or two players race; separately try denied foreign-house node | At most one economic drop for one accepted node; denied removal/drop zero | Ledger/world/permission failure. Keep natural and command-placed nodes distinct; S3 |
| Unsupported tracked dung | Diagnostic tracked node, remove its support before chunk reconciliation, then Adventure-harvest | Current code may still yield 1 before reconciliation; later reconciliation removes invalid nodes without granting loot | Known validation gap; do not assert immediate rejection based on old reports; S3 |
| Invalid soil and coarse dirt | 1 fertilizer main; use on coarse dirt, grass, vanilla farmland, already-fertile soil, or House Farm Plot | No conversion and no fertilizer loss (house interactions may give their own refusal) | Gatherability is not convertibility; S9, S13 |
| Missing hoe / repeated hoe | Fertilizer on base public plot; then Hoe on already-hoed plot | First gives hoe-first message and retains fertilizer; second says already hoed and retains durability | Consumption on no-op is a failure; S9 |
| Ordinary Adventure fertilizer | Non-op landless Adventure, plain fertilizer main, exact vanilla dirt | Expected current blocker: no conversion/item consumption | If a fixture/direct helper passes, it may have bypassed the NeoForge stack gate; inspect effective components/build rights; S1, S9, S11 |
| Public preparation timeout | Hoe fresh base plot, wait >=3,600 ticks without fertilizing | Server BE ticker restores base plot; Hoe durability remains spent; held fertilizer not spent | Time server ticks, not just wall clock; S9 |
| Public seed timeout | Fertilize empty public plot, leave seed in inventory past 1,200 ticks and observe subsequent random ticks | Reclamation to base on a later random tick, no fertilizer refund, seed retained. At random ticks 0 the plot may persist; planting itself lacks a deadline check | Known timer/dispatch distinction; do not interpret precisely 60 seconds as deterministic cleanup; S9–S10 |
| Skill unavailable | Empty Farming Block + 1 wheat seed; genuine non-op with unavailable/loading skill state; use top | Skill-unavailable message; seed retained; no crop; soil/fertility unchanged | A displayed 0 must not bypass availability. Repeat with genuinely available 0: planting should succeed; S8, S10 |
| Invalid target / occupied plot | 1 wheat seed main; right-click stone; separately click a plot already growing a crop | Stone: no planting/no seed loss. Occupied soil: already-planted refusal, original crop unchanged, seed retained | Invalid target consuming item or overwriting crop; S10 |
| Foreign private plot | Another owner's actual owned Farming Block or House Farm Plot, 1 seed main; click top | Denial, seed retained, crop/state unchanged | Ordinary converted dirt has **no owner assigned** and therefore is not a valid fixture for this denial; use real owned state; S10, S13 |
| Zero hydration/nutrients / roof | Diagnostic empty wheat-compatible soil, hydration 0, nutrients 0 and low light/roof; available Farming 0; use 1 seed | Planting still consumes 1 seed and records wheat; later growth is separate | Do not add nonexistent planting prerequisites. Use separate plot because successful negative-control planting consumes its seed; S8, S10 |
| Two simultaneous planting attempts | Two non-op eligible players, each 1 seed, same empty public soil; click once each | One server crop; winner seed -1, loser seed unchanged; fertility still 5 | Duplicate consumption/overwrite if both spend; S10 |

## Acceptance record

| Evidence category | Result for this investigation |
| --- | --- |
| Current source/registrations/config/resources/history | Inspected; detailed findings in discovery report |
| Existing focused JUnit | 236 discovered; 230 passed; 6 skipped; 0 failed/errors |
| GameTests | Source/fixtures inspected; not executed in this session |
| Ordinary empty-inventory playthrough | **NOT EXECUTED; first confirmed acquisition blocker at step 3** |
| Conditional component manual tests | NOT EXECUTED |
| Production configuration, catalog and player skill readiness | UNVERIFIED |

A full PASS requires ordinary provenance for every input and payment, accessible land/water/dung, a ready ordinary skill state, exact inventory accounting and a persistent server-accepted planted wheat crop. Success after giving tools/bowls/seeds, pre-preparing soil, setting skill readiness or supplying Earth Elemental fertilizer is only a separately labeled component result.
