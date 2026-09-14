# Farming game-loop discovery

Inspected 2026-09-06. Scope: an ordinary player starting with nothing, through one server-accepted planted seed. This is source discovery and testing documentation; no gameplay fixes were made. The companion [empty-inventory checklist](FARMING_EMPTY_INVENTORY_TEST_CHECKLIST.md) has **not been executed in a client**.

## Verdict

**The dung-to-first-seed route is not established as playable from empty inventory in this checkout.** Dung gathering and the custom bowl transactions are implemented and wired. The first confirmed acquisition blockers are **one `britannia_mod:britannia_shovel` and two `britannia_mod:empty_bowl`**: there is no repository-defined recipe, loot, starter grant, or fixed merchant source for them. A live backend could sell these registered items, but no live catalog was available to verify that possibility.

There are additional independent obstacles: ordinary dirt conversion does not pass the normal Adventure item-use gate; a public community plot avoids that gate but needs an unproven Farming Hoe source and authored land; the first wheat seed is not bootstrapped by managed grass; and wheat planting fails closed until authoritative skill data loads. Thus the absence of a connected backend/world cannot be treated as just a cosmetic limitation.

## Inspected implementation and evidence limits

| Field | Observed value |
| --- | --- |
| Repository | `https://github.com/Seggellion/Britannia_Mod.git` (`origin`) |
| Current worktree | `C:/projects/britannia/mod/Britannia_Mod` |
| Branch / HEAD | `patch-18` / `0cd566dd811bb717c6bda498ac3bed38be64b95e` |
| Initial tracked/untracked status | `git status --short` empty; Git emitted a warning about an inaccessible user-level ignore file |
| Minecraft / loader | Minecraft **1.21.1**, **NeoForge 21.1.72**, Java 21 |
| Mod version / namespace | `0.1.8a` / `britannia_mod` |
| Documentation convention | Root Markdown investigation/checklist reports alongside the existing `PATCH18_FERTILE_DIRT_*` reports |
| Instructions | Checked `AGENTS.md` at the drive/project ancestors and recursively in this checkout, including hidden files; none found |
| Change boundary | Stayed in this checkout; no branch switch, other-loader edits, commit, push, deployment, gameplay edits, or new test suites |

[gradle.properties](gradle.properties), [build.gradle](build.gradle), and [the NeoForge metadata template](src/main/templates/META-INF/neoforge.mods.toml) establish the versions and loader. README's `0.1.8` is less precise than the current property. The Minecraft metadata range property is `[1.21,1.21.1)`, which excludes the declared 1.21.1 target; record this packaging inconsistency separately from the farming logic. The test run below does not prove external jar installation compatibility.

Source and active packaged data take precedence over prose. Generated Minecraft/NeoForge sources in `build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/` were used to verify the actual 1.21.1 interaction dispatch and vanilla loot. These are local build evidence, not checked-in mod sources.

### Repository defaults versus local and live configuration

* **Repository:** `SurvivalZoneHandler` forces non-Creative/non-Spectator players into Adventure every server tick; there is no dung enable flag in its registration/scheduler. Dung timing is compiled into `WildResourceEntries`. The normal skill state must come from Rails. The legacy API default is loopback port 3000; `ServerCredentialSource` controls the effective authenticated endpoint, including environment precedence. [S1, S2, S8, S12]
* **Local configuration files:** `run/config/britannia_mod-server.properties` and `run/server/config/britannia_mod-server.properties` point to a loopback API on port 3000. Integrated-server permission is respectively `true` / `false`; the Rails update listener is `false` in both. Credential values were not printed. Environment/JVM overrides, credential validity, and backend connectivity were not established.
* **Local dedicated run:** `run/server/server.properties` says `gamemode=survival`, `force-gamemode=false`, `spawn-protection=16`, `spawn-animals=true`, `spawn-monsters=true`, `difficulty=easy`, and `level-name=../saves/sandbox`. That relative world resolves to **`run/saves/sandbox`**, not `run/server/world`. The mod still forces ordinary players into Adventure.
* **Live shard:** no production catalog, staffing, world survey, effective server properties, active packs, permissions, or new-player skill response was available. Nothing in this report is a verified production configuration.

Read-only `level.dat` inspection found:

| Local save | Enabled packs | Relevant persisted gamerules |
| --- | --- | --- |
| `run/saves/sandbox`, `probe`, `sandbox_backup_pre_villa` | `vanilla`, `mod_data` | `doTileDrops=true`, `doMobLoot=true`, `doMobSpawning=false`, `randomTickSpeed=300` |
| `run/server/world` | `vanilla`, `mod_data` | drops/loot/spawning true; random ticks 3 |
| `run/gametest/world` and its pre-M3 backup | `bundle`, `trade_rebalance`, `vanilla`, `mod_data` | drops/loot true; mob spawning false; random ticks 0 |
| `run/server/m9-roof-acceptance` | `vanilla`, `mod_data`, `file/m9_roof_acceptance` | drops/loot true; mob spawning false; random ticks 3 |

The corresponding datapack directories were empty except for the roof fixture pack. That pack contains roof setup/view functions; inspection found no farming recipes, seed grants, or dung/bowl overrides. `mod_data` aggregates installed mods, so these pack names alone are not a complete runtime recipe audit. `initial-enabled-packs=vanilla` in server properties is a creation default, not the saved world's enabled-pack list. No local save was loaded or changed for gameplay testing. The high random tick speed in the sandbox and zero in GameTests materially change soil decay/reclamation observations. Dung uses its own server-tick scheduler, independent of random ticks and vanilla mob spawning.

## Starting conditions and infrastructure

Use a genuinely new, non-operator player with empty inventory, armor/equipment and offhand; zero supplied currency, no bank assets, restored blessed items, borrowed supplies, owned property, or established crops. Login systems restore remote player metadata and blessed items; restoration is not evidence of a free farming starter kit. No local farming starter grant was found in the login paths. `PlayerData.syncFromShardUser` stores inventory JSON as metadata; it does not establish these missing item grants. [S12, S17]

The initial local skill map returns 0 for absent skills while its state is **LOADING**. A successful backend fetch makes it **AVAILABLE**; a failed fetch leaves it **UNAVAILABLE**. These are different from an available Farming skill of 0. The exact production new-player allocation remains external. No paid training is needed for wheat's numerical minimum of 0, but loading the skill state is compulsory. [S8]

| Needed world feature | Why / current evidence |
| --- | --- |
| Reachable exposed dirt/coarse dirt surfaces in loaded chunks | Natural dung can appear here; grass-covered ground is ineligible. No animal pen, feed, or animal entity is needed. Actual sites and access were not surveyed. |
| One reachable dirt or coarse-dirt block | Custom-shovel gathering target, protected by world/reach/house checks; unchanged after gathering. Players cannot assume they may dig through scenery to expose it. |
| Reachable source water **or** an existing Water Well | Fills the second custom bowl. No constructed well, bucket, crafting table, furnace, or placed processing station is required if natural source water is accessible. |
| Public base Community Farm Block | Best conditional planting route under normal Adventure rules. No ownership required by this block. Registry/BlockItem existence is not evidence that a public plot has been placed in the world. No automatic community-plot placement source was found in inspected structure/worldgen code/data. |
| Reachable and stocked vendors/traders, if used | City association, Rails responses, world NPC assignments, product IDs, prices, stock, and purchase settlement all need verification. A Farmer type is registered; its legacy food-supply gate is 20.0. That does not prove seeds or tools are sold. |
| Valid new-player skill service | Authenticated skill response available before planting. A client displaying 0 is insufficient evidence of readiness. |

Housing is an alternative, not a starting entitlement. An owner standing inside their own registered house is lent `mayBuild` while remaining in Adventure. Foreign houses, house infrastructure and perimeter foundations have protection rules; ordinary Survival breaking outside houses is also restricted. `StructureProtectionHandler` protects break/place events, not every arbitrary block-use callback. Do not generalize it into a blanket city interaction ban or blanket authorization. Server spawn protection, world border, reach, another mod, and actual city/world layout can still deny an interaction. [S1, S13]

## Actual dependency map and identities

```text
loaded eligible exposed terrain -> tracked dung node -> bare-hand left-click -> 1 dung
MISSING local shovel acquisition -> Britannia Shovel -> right-click dirt/coarse dirt -> 1 custom dirt
MISSING local empty-bowl acquisition -> 2 custom Empty Bowls

main Empty Bowl + offhand custom Dirt --use--> Bowl of Dirt
main Bowl of Dirt + offhand Dung --use--> Bowl of Fertile Dirt
other Empty Bowl + targeted source water / well --use--> Bowl of Water
main Bowl of Fertile Dirt + offhand Bowl of Water --use-->
    1 Fertilized Dirt + 2 returned Empty Bowls

MISSING/unverified Farming Hoe supply + authored public base plot --hoe-->
    Hoed Community Farm Block --Fertilized Dirt--> Farming Block
UNVERIFIED first wheat-seed supply + AVAILABLE Farming >= 0 --use on Farming Block-->
    server BE crop "wheat", age 0, has_seeds=true; seed consumed

alternative soil: exact vanilla dirt --Fertilized Dirt Item.useOn--> Farming Block
    BLOCKED at normal Adventure dispatch without mayBuild / suitable CAN_PLACE_ON
alternative fertilizer: Earth Elemental loot -> same Fertilized Dirt item
    bypasses dung processing; does not repair its acquisition blockers
```

All mod IDs below use the exact `britannia_mod:` prefix. Names are the English localization unless noted. [S2, S14]

| Exact ID | Display name | Type / quantity for one attempt |
| --- | --- | --- |
| `britannia_mod:dung` | Dung | Separate block and plain item; 1 node yields 1 item; item is not placeable |
| `britannia_mod:dirt` | Dirt | Plain loose-dirt item, 1 consumed; **not** `minecraft:dirt` |
| `britannia_mod:britannia_shovel` | Britannia Shovel | Exact retained gathering tool; 1 durability spent; quality/material do not gate this gather |
| `britannia_mod:empty_bowl` | Empty Bowl | Custom item, 2 reusable containers needed simultaneously |
| `britannia_mod:bowl_of_dirt` | Bowl of Dirt | Intermediate item, 1 |
| `britannia_mod:bowl_of_fertile_dirt` | Bowl of Fertile Dirt | Intermediate item, 1; not yet usable soil |
| `britannia_mod:bowl_of_water` | Bowl of Water | Filled item, 1; plain item identity rather than a stored water amount |
| `britannia_mod:fertilized_dirt` | Fertilized Dirt | Canonical soil-conversion item, 1 consumed; no separate `fertile_dirt` ID |
| `britannia_mod:farming_hoe` | Farming Hoe | Retained custom item, durability 128; public preparation spends 1 |
| `britannia_mod:community_farm_block` | Community Farm Block | Block and BlockItem; authored public base soil |
| `britannia_mod:community_hoed_farm_block` | Hoed Community Farm Block | Prepared block; no matching BlockItem registered in the inspected item registry |
| `britannia_mod:farming_block` | Farming Block | Soil block and separate BlockItem; BE type `britannia_mod:farming_block_be` |
| `britannia_mod:house_farm_plot` | House Farm Plot | Separate owned persistent plot; BE `britannia_mod:house_farm_plot_be` |
| `britannia_mod:water_well` | Water Well | Existing two-high multiblock water source; optional infrastructure |
| `minecraft:wheat_seeds` | Wheat Seeds (vanilla name) | 1 consumed; internal crop definition ID is `wheat`, not a new block ID |

The custom dirt, dung, and four bowl identities use ordinary stack sizes of 64. `minecraft:bowl`, `britannia_mod:empty_pewter_bowl`, and food bowls are not accepted replacements. Vanilla planks-to-bowls crafting would produce the wrong registry item; no amount of ordinary crafting facilities fixes that mismatch. Vanilla shovel/hoe recipes likewise do not produce the exact custom tools.

## Reconstructed interactions

### 1. Dung acquisition

`BritanniaMod` bootstraps WildResource entries and registers the manager and interaction handler. Dung is an environmental node, not animal excrement emitted by entity AI. Its entry has unrestricted biome/nearby rules, weight **1**, cap **2 tracked nodes per chunk**, **4** candidate probes per attempt, minimum same-type spacing **8 blocks** (exact radius is permitted), initial/retry attempt delay **6–12 minutes**, and post-removal attempt delay **20–40 minutes**, measured at 20 ticks/second. The weighted choice is among due resource types, not a fixed percentage chance to get dung. Suitable terrain/probe success is stochastic; 12 minutes is not a guaranteed appearance deadline. [S2, S3]

The manager processes loaded chunks across server levels; it does not require nearby animals, feeding, a full moon, a particular skill, rain, or a bucket. Candidate selection uses the surface heightmap. The replaceable target must be dry and have no block entity; its support must be exact `minecraft:dirt` or `minecraft:coarse_dirt`. Grass block, farmland, sand, water and stone fail. It is scheduled on chunk load and server ticks, not an entity drop timer or crafting recipe.

In Adventure, target the low dung pile and **left-click with an empty main hand**. The client mixin permits this `AdventureHarvestableBlock` gesture; the server HIGH-priority handler cancels ordinary breaking and calls the tracked entry's harvest strategy. With valid ledger/block, real-player identity, reach, `level.mayInteract` and house rights, it removes the node, schedules another attempt and drops **one** dung item. Walk over it to collect it. `doTileDrops=false` can suppress the economic drop. No collecting vessel or tool is required. In Survival the normal break/loot path applies, but broader outside-house restrictions can intervene; bare-hand Survival must not be assumed equivalent to the explicit Adventure path. [S3, S13]

One discrepancy with old prose: harvesting does **not** recheck valid support when a tracked dung block is still standing. `standingTrackedEntry` excludes `MISSING_OR_REPLACED`, not `OWNED_INVALID`; `harvestOne` checks identity/ledger but not the support rule. Reconciliation later removes unsupported owned nodes. Test the interval between support removal and reconciliation rather than claiming immediate invalid-support denial. This is not the empty-inventory blocker. [S3]

### 2. Tools, bowls and renewable loose dirt

The custom shovel is registered as `QualityShovelItem`; the handler checks its exact item holder, not a generic shovel tag. Main-hand **right-click exact vanilla dirt or coarse dirt**. A permitted server transaction grants one `britannia_mod:dirt`, charges one durability, leaves the terrain unchanged, and sends **“You gather a handful of dirt.”** It does not award Mining or enter the mining/deposit pipeline. No Farming/Mining minimum, animal input, or station is tested. A persistent, per-player **1,200-tick** cooldown is claimed before granting value; feedback is throttled to 40 ticks. Swapping tools, hands, or reconnecting does not supply a second immediate gather. [S4]

The acquisition absence was checked through several independent connections:

* Parsed all **88** JSON files under active recipe/loot/blacksmithing paths: the targeted acquisition identities appear only as dung block loot and Earth Elemental fertilizer loot. There are **3 mod recipes + 9 vanilla overrides**, none producing these custom bowls/tools.
* Inspected the canonical **204-output Blacksmithing catalog** and its actual loader, `CraftableRegistry`: no custom shovel, bowl or Farming Hoe output. A method named `createShovel` manufactures configured stacks for callers; it is not a player crafting registration.
* Cross-referenced exact registry IDs and Java holders across production source, commands, events, item use, economy and login code, separately from Creative exposure and GameTests. The empty bowl's production output is the **return from the final mix**, which itself requires two bowls. That is a genuine bootstrap cycle without another source.
* Inspected merchant recipe routing and catalog parsers. Local food recipes cover baker/tavernkeeper/costermonger products, not farming equipment. Generic/economic catalogs accept backend-provided registered items, so absence here does **not** prove that a live Rails database has no listing. Documentation/vendor mappings for the pewter bowl are neither an Empty Bowl mapping nor active stock evidence.
* Inspected local datapacks and searched worldgen/structure paths rather than relying on one keyword miss. Generated source resources are declared in Gradle but `src/generated/resources` is absent; generated GameTest structure data is a test fixture, not a source of player equipment. [S5, S12, S17]

Thus no ingredient quantities, crafting station, merchant price, or skill progression can honestly be specified for acquiring the required custom shovel/two bowls under repository defaults. The same absence applies to the Farming Hoe needed by the public route.

### 3. Bowl preparation, water and final output

These are **item-use transactions**, not crafting-grid recipes, placed bowls, or a timed composting block. For deterministic manual gestures, stand clear of interactive blocks/entities, hold one item per input, leave the unused bowl in inventory, and right-click air without sneaking. Each successful server callback consumes exactly one of each input even in Creative. Client calls only predict success. [S6]

| Action | Main hand | Offhand | Result and cost |
| --- | --- | --- | --- |
| Right-click air | 1 Empty Bowl | 1 custom Dirt | 1 Bowl of Dirt in vacated main hand; both inputs consumed |
| Right-click air | 1 Bowl of Dirt | 1 Dung | 1 Bowl of Fertile Dirt in main hand; both inputs consumed |
| Right-click targeted source water or well | Other Empty Bowl | Empty recommended | 1 Bowl of Water replaces held bowl; source/well unchanged |
| Right-click air | 1 Bowl of Fertile Dirt | 1 Bowl of Water | 1 Fertilized Dirt in main hand + 2 Empty Bowls in offhand |

Dry preparation and final mixing require **MAIN-hand callbacks and this hand order**. Water filling supports either hand because its branch runs before the preparation item's main-hand guard; using main hand with empty offhand avoids ambiguity. A water-tagged **source** fluid is accepted by the source-only raycast; flowing water is not. Waterlogged source fluid can meet that predicate if reachable. The existing Water Well resolves its root/child interaction to an anchor and delegates to the same fill transaction. It is public/stateless/unlimited in this implementation and checks world interaction permission, not house ownership. [S7]

Source-water targeting takes precedence over offhand custom dirt. Aim into dry air when preparing Bowl of Dirt. Clicking an interactive block/entity may consume the use before the item's generic `use` runs. The fill exchanges item identities; it does not store water NBT in an Empty Bowl. No bowl-from-water-bucket/bottle, water-cauldron, rain-catching or bowl-placement handler/recipe is wired. The well supports buckets, watering cans and pitchers independently, but those are not substitutes for the required Bowl of Water in final mixing. No additional hydration tool is needed for the first planting.

Successful dry/mix callbacks play the composter-fill sound; filling plays a fill sound. Wrong tuples/reversed hands ordinarily return PASS without mutation or a custom error message. Both live stacks are revalidated before shrinking. A stale accepted plan owns the callback but mutates nothing. Freed hands are used first; otherwise output goes to inventory or is dropped without duplication. With the one-at-a-time checklist there is ample space and both returned bowls stay in offhand. [S6, S7]

### 4. Soil preparation and permissions

**Fertilized Dirt is an item which converts a target block.** It is not the `farming_block` BlockItem and not a nutrient additive for an already-established plot. The canonical item can convert **exact vanilla dirt**, or fertilize the distinct **Hoed Community Farm Block**. Coarse dirt is gatherable and supports dung but is **not convertible** by this item. Grass, vanilla farmland, already-fertile Farming Block, and House Farm Plot are not new-soil targets. [S9]

**Ordinary dirt branch:** `FertilizedDirtItem.useOn` directly sets a Farming Block with hydration **1**, synchronizes BE hydration **1**, initializes **5 remaining fertile harvests**, plays gravel placement, consumes **1** item outside Creative and attempts a Farming TOOL skill gain. Fresh N/P/K/organic matter and the `fertilizer` blockstate value remain **0**. No extra dirt item, tilling hoe, nearby water, wait, or minimum skill is needed inside this method. It does not call `setOwner`; `setBlock` does not invoke `setPlacedBy`. Consequently a converted ordinary plot is unowned and `mayPlant` accepts other players too. This differs from placing the Farming Block BlockItem, which sets the placer as owner. [S9, S10]

**Adventure dispatch blocker:** `ServerPlayerGameMode.useItemOn` tries block interaction before invoking `ItemStack.useOn`. Server `ItemStack.useOn` delegates to NeoForge `CommonHooks.onPlaceItemIntoWorld`; that method returns PASS before `Item.useOn` when `mayBuild=false` and there is no matching `CAN_PLACE_ON`. The fertilizer is registered with plain properties. Neither its item class, global component grants (saplings/ladders only), nor an event/mixin supplies an Adventure override for it. Ordinary vanilla dirt has no fertilizer block callback. Therefore a normal landless Adventure player cannot reach the ordinary-dirt conversion through a normal click. This is a code-confirmed disconnect, **not a live reproduced result**. [S1, S9, S11]

**Public community branch:** right-click a base Community Farm Block with the exact Farming Hoe. Its **block callback** converts it to Hoed Community Farm Block, charges 1 hoe durability and sets a **3,600-tick / 3-minute** expiry. Apply Fertilized Dirt before that expires. The prepared block's callback runs before the Adventure item gate, creates the Farming Block with the same hydration/5-use state, records a public community plot with no owner and a **1,200-tick / 60-second** seed window, and consumes 1 fertilizer. No property purchase is needed for that block's callback. These methods have no additional Farming threshold or house-owner check; surrounding live protections still need testing. [S9]

The 3-minute hoed expiry is checked by a BE server ticker. The empty fertilized community plot's 60-second expiry is reclaimed on a **random block tick**, not a hard planting-time guard: `tryPlantSeed` never checks the deadline. At `randomTickSpeed=0` an expired but unreclaimed plot can remain seedable. Plant promptly within the advertised window for the main test; test late reclamation separately. Once a crop is committed, `plant` clears its community seed deadline. No fertilizer refund is produced by expiry. [S9, S10]

**House Farm Plot:** still exists, extends the farming substrate, and has its own owner checks and persistent assignment. It initializes an existing poppy assignment; only the exact Farming Hoe clears it, and another plant cannot be assigned until clear. Fertilized Dirt has no house-plot conversion branch. It is therefore an independent housing subsystem, not a missing step in dung mixing. A route through it would require acquiring a deed/house and the hoe, placing/obtaining a plot, and proving ownership/access; none is a free starting grant. The public route avoids that entire property dependency. [S13]

### 5. First seed and successful planting

**Selected crop: wheat (`minecraft:wheat_seeds`, crop definition `wheat`).** It has a known vanilla seed-loot source, numerical Farming requirement **0**, no trellis, and no tree-space requirements. This is the smallest planting dependency set, not a claim of proven seed access in normal Adventure.

The inherited vanilla short-grass/fern loot has a **12.5%** seed chance per normal non-shears, no-Fortune break. But empty-hand Adventure cannot perform that break. The client bypass only accepts specially harvestable blocks, a house build lease, or an authorized managed-vegetation cutter. **Managed** grass cutting requires a sword/grain blade and intentionally removes the node with **no drops**; buying a blade does not turn that managed path into wheat-seed harvesting. Unmanaged vanilla grass loot is thus real but unavailable to the stated empty-handed, landless player through ordinary breaking. Survival is also not a blanket workaround because of the mode enforcer and outside-house break policy. [S1, S13, S15]

No fixed wheat-seed merchant listing, local seed recipe, farming starter grant, or automatically accessible ripe farm was found. Existing Farming Block wheat harvest can return one seed with chance **0.35**, but needs an already-mature wheat crop and a grain harvesting blade; it cannot create the first crop from nothing. Naturally generated vanilla crop/chest loot or environmental destruction could provide seeds in a particular world, but no such accessible source was established, and an assumed stocked farm/container is excluded from this test.

Other crop names do not solve this automatically. Some produce really supports extraction: `SeedExtractableHealingCropItem` (for example custom Corn), `GrapesItem`, and harvested flowers delegate shift-use/offhand use to `CropSeedExtractor`, consuming one produce for one seed in ordinary play. That is not a generic recipe for every vegetable. Carrots and lettuce are registered as `WeightedCookedFoodItem`, not that seed-extraction class. Buying food cannot be assumed to yield its seeds. Corn additionally requires Farming 30; grapes and flowers have different world/support/acquisition requirements. No alternate seed bootstrap is promoted to verified here. [S16]

With **one legitimately acquired wheat seed**, an empty Farming Block and available Farming >= 0, right-click the **soil top**, main hand seed, offhand empty, **not sneaking**. The block callback resolves the vanilla seed via `CropRegistry.bySeed`; it does not place vanilla wheat above vanilla farmland. It checks plot ownership, authoritative cultivation eligibility, no existing crop/`has_seeds`, and crop support. For wheat, support is `NONE`. No planting-time hydration, nutrient, light, altitude, season/climate, nearby-water or overhead-space threshold is checked. Use visible clear land for observation; those growth concerns are not extra planting ingredients. [S8, S10]

Server acceptance sets BE `PlantedCropId="wheat"`, `GrowthStage=0`, `GrowthProgress=0`, `Mature=false`, clears the community deadline, updates the soil's `has_seeds=true`, consumes **one seed**, plays planting sound and attempts the skill award. Fertility remains **5**; planting is not a harvest. The renderer displays the crop from the BE; there need not be a separate `minecraft:wheat` block. Relog/chunk reload and persistent seed consumption provide a practical ordinary-player check; an independent non-operator observer helps. For an authoritative diagnostic, inspect BE NBT and the server `[farming planting] ... crop_id=wheat ... planted=true reason=planted` log. A sound, arm swing, SUCCESS return or brief client image alone is not proof: denials deliberately consume the interaction without consuming the seed. [S8, S10, S18]

## Money, external catalogs and the alternative fertilizer

The physical bowl/gather/plant transactions charge no coins. Missing equipment/seed prices are **unknown**, not zero. The server's economic catalog obtains product availability, item IDs, denomination and price from Rails and requotes purchases; it reserves the exact denomination, settles remotely, grants on success and refunds on failed settlement. Legacy generic catalogs are also remote; an entity named Farmer does not guarantee a seed offer. Farmer registration/legacy food floor 20 is active code; current staffing may instead be controlled by Rails NPC assignments. [S12]

There are local coin-generating loot paths, so “there is no money anywhere” would be false: Wisp and Earth Elemental loot each supplies **1–3 `britannia_mod:gold_coin`**. Wisp has a dark-forest spawn modifier (weight 5, group 1). Reaching and defeating such mobs from an empty inventory, and trading/splitting the resulting denomination at the required prices, have not been demonstrated. The configured sandbox disables vanilla mob spawning. Trader buyback is another possible link, but requires an accessible trader, accepted commodity policy, actual stock/demand and settlement; dung is not automatically a sellable commodity merely because it is an item. Without those facts there is no concrete initial-income-to-two-bowls/shovel/hoe/seed budget. [S12, S19]

Earth Elemental loot separately supplies **1–2 canonical Fertilized Dirt with 35% probability per loot roll**. `britannia_mod:earth_elemental` is registered with attributes; `ShameDungeonSpawner` is registered and attempts spawning on a 200-tick cadence in its configured dungeon floors. Floor 1 produces Earth Elementals in X 2906–3156, Z 3496–3893, Y 88–108, with a 40-monster floor cap and ground/space checks. The code path exists; an accessible dungeon, viable unarmed combat (72 HP elemental), and actual drops are not verified world facts. This is a combat/loot alternative that bypasses **all dung processing inputs** and still faces soil, seed and skill constraints. It must never be used to pass the dung-chain acquisition step. [S19]

## Transition classification and follow-up scope

“Reachable” below means the code has a legitimate interaction from the listed inputs, not observed gameplay in this investigation.

| Transition | Classification | Evidence / remaining requirement |
| --- | --- | --- |
| Empty hands -> natural dung | Implemented and reachable **if eligible world sites exist**; world accessibility unverified | Registered scheduler, client gesture and authoritative harvest all connected; no animal/tool requirement |
| Empty inventory -> custom shovel / 2 Empty Bowls | **Missing repository acquisition connection**; external offers unverified | Recipe/loot/catalog-output enumeration + production call sites + login scan; final-mix bowl return is circular |
| Custom shovel + terrain -> loose Dirt | Implemented, conditional on tool acquisition | Exact two substrates; event wired; protected server transaction |
| Dirt + bowls + dung + water -> Fertilized Dirt | Implemented and wired, conditional on inputs | Exact item classes, live-plan commit, returned containers |
| Fertilizer -> ordinary dirt soil in normal Adventure | **Partially implemented/disconnected at dispatch** | `Item.useOn` works directly; NeoForge stack-use gate blocks entry without build rights/predicate |
| Empty inventory -> Farming Hoe / public plot | Tool acquisition missing locally; world plot availability unverified | Neither registration nor BlockItem establishes ordinary world access |
| Public plot + hoe + fertilizer -> usable soil | Implemented in Adventure block callbacks, conditional | 3-minute preparation and nominal 60-second seed window |
| Empty hands -> wheat seed by grass breaking | Vanilla loot implemented but **unavailable under ordinary Adventure restriction** | Managed grass additionally has deliberate no-drop cutting; backend/world alternative unverified |
| First seed from crop seed return | Implemented but **circular as bootstrap** | Requires prior crop and harvest tool |
| Skill login -> planting permission | Implemented external dependency; unavailable if load fails | Fail-closed state check applies even to 0-minimum wheat |
| Valid wheat seed + valid soil + available skill -> first crop | Implemented in server block callback; live behavior unverified | BE mutation, stack shrink, state update and sync present |

Smallest recommended follow-up changes, **not implemented here**:

1. Assign and ship an ordinary source for **two exact Empty Bowls and one exact Britannia Shovel**, with a complete no-money/no-tool bootstrap or a documented achievable earning route. Container returns alone cannot bootstrap bowls.
2. Choose the normal Adventure land path. For ordinary dirt, supply a narrow, protected server interaction/predicate and exercise real `ItemStack`/game-mode dispatch; for public plots, ship/prove the exact Farming Hoe source and public plot placement/access. Do not ask players to switch to Survival.
3. Establish one first-seed source that works in Adventure without existing crops; if a merchant is chosen, verify actual products, stock, income route and cost. Managed-grass no-drop behavior means vanilla seed assumptions are insufficient.
4. Ensure a new ordinary player's skill service successfully loads (or explicitly design offline planting behavior). A 0-point threshold is not an offline bypass.
5. Add future end-to-end acceptance coverage using actual acquisition and Adventure dispatch, without hidden supplies or mature-crop helpers. Secondary issues worth targeted follow-up are unowned ordinary converted soil, unsupported dung before reconciliation, and random-tick-dependent community expiry. Define desired behavior before changing these semantics.

To resolve external uncertainty, obtain the inspected shard/build identity; effective world path, game rules and enabled packs; reachable dung/source-water/public-plot coordinates; exact catalog rows and purchase results for bowl/shovel/hoe/wheat seed (including quantities, prices and denominations); fresh-player earning route/bank baseline; Farmer/NPC assignments and food supply; and the fresh-player skill response/readiness. Credentials are not needed in a public report. Continue the conditional component checklist even when those facts are unavailable, but keep its result separate.

## Automated verification versus gameplay

**Executed:** the existing focused JUnit suite, Java 21.0.9, Gradle 8.9. **236 tests discovered: 230 passed, 0 failures, 0 errors, 6 skipped; 46 suites.**

| Package selection | Tests | Passed | Skipped |
| --- | ---: | ---: | ---: |
| `bowlpreparation.*` | 13 | 13 | 0 |
| `dirtgathering.*` | 15 | 15 | 0 |
| `wildresource.*` | 46 | 46 | 0 |
| `farming.*` | 155 | 149 | 6 |
| `patch18.*` | 7 | 7 | 0 |

Reproduction with a working JDK/Gradle installation:

```powershell
.\gradlew.bat test --no-configuration-cache --console=plain `
  --tests 'com.seggellion.britannia_mod.bowlpreparation.*' `
  --tests 'com.seggellion.britannia_mod.dirtgathering.*' `
  --tests 'com.seggellion.britannia_mod.wildresource.*' `
  --tests 'com.seggellion.britannia_mod.farming.*' `
  --tests 'com.seggellion.britannia_mod.patch18.*'
```

The first sandboxed wrapper attempt could not download/access its Gradle distribution. Running the installed Gradle 8.9 directly with the user's existing Gradle home initially failed offline at `cacheVersionExecutableServer1.21.1`, before tests. The subsequent normal dependency-resolution run succeeded in **3m 34s**. Actual launcher: `C:/Users/dusti/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle.bat`, `--gradle-user-home C:/Users/dusti/.gradle`, `JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-21.0.9.10-hotspot`, with the same selections above. Results were counted from XML, not inferred from BUILD SUCCESSFUL. Local artifacts: `build/farming-discovery-junit.log`, `build/test-results/test/`, `build/reports/tests/test/index.html` (generated/ignored).

The six assumptions skipped four proposal-dependent skill checks (`FarmingSkillRequirementTest` x3 and `FarmingSkillProgressionCloseoutTest` x1) and two `FlowerAssetContractTest` checks. They require branch-excluded `FARMING_SKILL_PROGRESSION_PROPOSAL.md` / `FLOWER_ASSET_PLACEHOLDER_MANIFEST.md`; they are not six passing gameplay tests.

Coverage is mixed: bowl tests exercise plan/commit using test item identities; dirt/wild tests exercise policies, schedulers and persistence with controlled contexts; farming tests include domain checks and source/resource contracts. These establish useful invariants, not real player controls, default-world acquisition, network dispatch, live permissions or Rails integration.

**Existing GameTests inspected, not run this session:** `Patch18MilestoneOneGameTests`, `Patch18MilestoneTwoGameTests`, `Patch18MilestoneFourGameTests`, `FertileDirtFinalMixGameTests`, `FertileDirtLifecycleGameTests`, and `Patch18FullLoopGameTests`, plus their test-player/services seams. In particular, `renewableIngredientsCompleteTheFiveHarvestLoopWithoutDuplication` supplies a pickaxe/shovel/two bowls, forces a scheduled dung position/time, creates water and terrain, calls `item.getItem().useOn(...)` directly for soil, and uses `plantMigratedCrop` to create mature potatoes. It does not obtain or normally plant the first seed. Its title and older pass report cannot close this task. [S20]

**Manual client / live server verification:** not executed. No suitable connected ordinary-player test session was established; existing local saves are not equivalent to such a session. No production service was queried or modified. No new tests or gameplay implementation were added. Historical results in `PATCH18_FERTILE_DIRT_FINAL_REPORT.md` remain historical, not results of this investigation.

### History that explains discrepancies

`a3419da2` introduced the renewable finite fertilizer chain; `4287ae6f` subsequently added coarse dirt to loose-dirt gathering. Therefore the older final report's exact-dirt-only gather description is superseded. `7db9d1d5` introduced the cultivation skill gate. The older report correctly flagged missing bowl/shovel acquisition, but its broad full-loop/Adventure claims must be read against today's dispatch and the actual provisioned test seams. No older branch or other-loader checkout was used as the implementation under test.

## Source reference index

References identify the checked-in source at the recorded HEAD; numbers are source line locations. Directory scans described above additionally cover absence claims.

* **S1 — mode/wiring:** [BritanniaMod.java](src/main/java/com/seggellion/britannia_mod/BritanniaMod.java), 154–157, 195–218, 276–289; [SurvivalZoneHandler.java](src/main/java/com/seggellion/britannia_mod/structure/SurvivalZoneHandler.java), 87–137.
* **S2 — dung definition:** [WildResourceEntries.java](src/main/java/com/seggellion/britannia_mod/wildresource/WildResourceEntries.java), 35–45, 93–120; [WildResourcePlacementRules.java](src/main/java/com/seggellion/britannia_mod/wildresource/WildResourcePlacementRules.java), 17–48, 61–65; [DungBlock.java](src/main/java/com/seggellion/britannia_mod/block/DungBlock.java), 12–29.
* **S3 — dung runtime:** [WildResourceManager.java](src/main/java/com/seggellion/britannia_mod/wildresource/WildResourceManager.java), 39–63, 83–193; [WildResourceSpawnScheduler.java](src/main/java/com/seggellion/britannia_mod/wildresource/WildResourceSpawnScheduler.java); [WildResourceInteractionHandler.java](src/main/java/com/seggellion/britannia_mod/wildresource/WildResourceInteractionHandler.java), 28–85; [WildResourceHarvestService.java](src/main/java/com/seggellion/britannia_mod/wildresource/WildResourceHarvestService.java), 21–48, 70–111; [WildResourceHarvestPolicy.java](src/main/java/com/seggellion/britannia_mod/wildresource/WildResourceHarvestPolicy.java), 16–49; [dung loot](src/main/resources/data/britannia_mod/loot_table/blocks/dung.json).
* **S4 — dirt gather:** [DirtGatheringInteractionHandler.java](src/main/java/com/seggellion/britannia_mod/dirtgathering/DirtGatheringInteractionHandler.java), 22–52; [DirtGatheringTarget.java](src/main/java/com/seggellion/britannia_mod/dirtgathering/DirtGatheringTarget.java), 11–12; [DirtGatheringService.java](src/main/java/com/seggellion/britannia_mod/dirtgathering/DirtGatheringService.java), 18–60; [DirtGatheringPolicy.java](src/main/java/com/seggellion/britannia_mod/dirtgathering/DirtGatheringPolicy.java), 17–59; [DirtGatheringCooldown.java](src/main/java/com/seggellion/britannia_mod/dirtgathering/DirtGatheringCooldown.java); [QualityShovelItem.java](src/main/java/com/seggellion/britannia_mod/item/QualityShovelItem.java), 88 onward.
* **S5 — acquisition audit:** [ToolRegistry.java](src/main/java/com/seggellion/britannia_mod/registry/ToolRegistry.java), 27–50; [mod recipes](src/main/resources/data/britannia_mod/recipe); [vanilla overrides](src/main/resources/data/minecraft/recipe); [loot tables](src/main/resources/data/britannia_mod/loot_table); [craftables.json](src/main/resources/data/britannia_mod/blacksmithing/craftables.json); [CraftableRegistry.java](src/main/java/com/seggellion/britannia_mod/skill/crafting/CraftableRegistry.java), 25–49; S12, S14 and S17.
* **S6 — bowl transactions:** [BowlPreparationItem.java](src/main/java/com/seggellion/britannia_mod/bowlpreparation/BowlPreparationItem.java), 27–75; [BowlPreparationService.java](src/main/java/com/seggellion/britannia_mod/bowlpreparation/BowlPreparationService.java), 16–93; [FertileDirtMixingItem.java](src/main/java/com/seggellion/britannia_mod/bowlpreparation/FertileDirtMixingItem.java), 22–54; [FertileDirtMixingService.java](src/main/java/com/seggellion/britannia_mod/bowlpreparation/FertileDirtMixingService.java), 13–94; [BowlPreparationOutput.java](src/main/java/com/seggellion/britannia_mod/bowlpreparation/BowlPreparationOutput.java), 12–60.
* **S7 — water:** [BowlWaterFillingService.java](src/main/java/com/seggellion/britannia_mod/bowlpreparation/BowlWaterFillingService.java), 23–79; [WaterSourceInteraction.java](src/main/java/com/seggellion/britannia_mod/util/WaterSourceInteraction.java), 26–78; [WaterSourceAccessPolicy.java](src/main/java/com/seggellion/britannia_mod/util/WaterSourceAccessPolicy.java), 13–21; [WaterWellBlock.java](src/main/java/com/seggellion/britannia_mod/block/WaterWellBlock.java), 26–40; S6 source raycast.
* **S8 — crop and skills:** [CropRegistry.java](src/main/java/com/seggellion/britannia_mod/farming/CropRegistry.java), 98–109, 400 onward; [FarmingCultivationGate.java](src/main/java/com/seggellion/britannia_mod/farming/FarmingCultivationGate.java), 109–161, 179–218; [SkillManager.java](src/main/java/com/seggellion/britannia_mod/skill/SkillManager.java), 233–278, 658–663; [FarmingSkillRequirementResolver.java](src/main/java/com/seggellion/britannia_mod/farming/FarmingSkillRequirementResolver.java), 35–59.
* **S9 — soil/public preparation:** [FertilizedDirtItem.java](src/main/java/com/seggellion/britannia_mod/item/FertilizedDirtItem.java), 30–74; [CommunityFarmBlock.java](src/main/java/com/seggellion/britannia_mod/block/CommunityFarmBlock.java), 55–79; [FarmingHoeItem.java](src/main/java/com/seggellion/britannia_mod/item/FarmingHoeItem.java), 94–116; [CommunityHoedFarmBlock.java](src/main/java/com/seggellion/britannia_mod/block/CommunityHoedFarmBlock.java), 29–66; [CommunityFarmBlockEntity.java](src/main/java/com/seggellion/britannia_mod/block/entity/CommunityFarmBlockEntity.java), 15–54.
* **S10 — planting/state:** [FarmingBlock.java](src/main/java/com/seggellion/britannia_mod/block/FarmingBlock.java), 111–210, 350–355, 436–539, 607–608, 690–695, 821–828; [FarmingBlockEntity.java](src/main/java/com/seggellion/britannia_mod/block/entity/FarmingBlockEntity.java), 44–60, 151–170, 279–320, 341–390, 653–712, 808 onward; [BlockEntityRegistry.java](src/main/java/com/seggellion/britannia_mod/registry/BlockEntityRegistry.java), 167–201.
* **S11 — dispatch/predicates:** [GlobalEventHandler.java](src/main/java/com/seggellion/britannia_mod/event/GlobalEventHandler.java), 58–115; [mixins manifest](src/main/resources/britannia_mod.mixins.json). Generated 1.21.1 sources: `net/minecraft/server/level/ServerPlayerGameMode.java` 337 onward, `net/minecraft/world/item/ItemStack.java` (`useOn`), `net/neoforged/neoforge/common/CommonHooks.java` 593–614, under the build source directory recorded above.
* **S12 — economy/config:** [ServerCatalogService.java](src/main/java/com/seggellion/britannia_mod/economy/ServerCatalogService.java), 44–86, 112–156, 172 onward; [EconomicVendorPurchaseService.java](src/main/java/com/seggellion/britannia_mod/economy/EconomicVendorPurchaseService.java), 78–141; [MerchantRecipes.java](src/main/java/com/seggellion/britannia_mod/economy/MerchantRecipes.java), 19–73; [MerchantTypes.java](src/main/java/com/seggellion/britannia_mod/merchant/MerchantTypes.java), 20–49; [ServerCredentialSource.java](src/main/java/com/seggellion/britannia_mod/server/auth/ServerCredentialSource.java), 38–99; [ModConfig.java](src/main/java/com/seggellion/britannia_mod/config/ModConfig.java), 16.
* **S13 — house/world permissions:** [HouseBuildRights.java](src/main/java/com/seggellion/britannia_mod/structure/HouseBuildRights.java), 100–112, 147–170; [StructureProtectionHandler.java](src/main/java/com/seggellion/britannia_mod/structure/StructureProtectionHandler.java), 75–165; [HouseFarmPlotBlock.java](src/main/java/com/seggellion/britannia_mod/block/HouseFarmPlotBlock.java), 138–193, 240–257; [HouseFarmPlotBlockEntity.java](src/main/java/com/seggellion/britannia_mod/block/entity/HouseFarmPlotBlockEntity.java), 82–119; [HouseActionHandler.java](src/main/java/com/seggellion/britannia_mod/structure/HouseActionHandler.java).
* **S14 — IDs/names/assets:** [ItemRegistry.java](src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java), 331–346, 947–951, 990–997, 1024 onward; [BlockRegistry.java](src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java), 247–274, 333, 2509, 2735; [en_us.json](src/main/resources/assets/britannia_mod/lang/en_us.json), 114–159, 276, 992–995, 1668, 1683–1690; [FertileDirtRegistryAssetContractTest.java](src/test/java/com/seggellion/britannia_mod/patch18/FertileDirtRegistryAssetContractTest.java).
* **S15 — grass seed distinction:** [ClientAdventureBreakGateMixin.java](src/main/java/com/seggellion/britannia_mod/mixin/client/ClientAdventureBreakGateMixin.java), 60–84; [ManagedVegetationCutTools.java](src/main/java/com/seggellion/britannia_mod/vegetation/ManagedVegetationCutTools.java); [ManagedVegetationService.java](src/main/java/com/seggellion/britannia_mod/vegetation/ManagedVegetationService.java), 255–289; [ManagedVegetationInteractionHandler.java](src/main/java/com/seggellion/britannia_mod/event/ManagedVegetationInteractionHandler.java). Generated vanilla sources: `net/minecraft/data/loot/BlockLootSubProvider.java` 398–408; `net/minecraft/data/loot/packs/VanillaBlockLoot.java` 1200–1201.
* **S16 — seed extraction scope:** [CropSeedExtractor.java](src/main/java/com/seggellion/britannia_mod/item/CropSeedExtractor.java), 17–44; [SeedExtractableHealingCropItem.java](src/main/java/com/seggellion/britannia_mod/item/SeedExtractableHealingCropItem.java), 23–28; [GrapesItem.java](src/main/java/com/seggellion/britannia_mod/item/GrapesItem.java), 46 onward; [HarvestedFlowerItem.java](src/main/java/com/seggellion/britannia_mod/item/HarvestedFlowerItem.java), 29 onward; ItemRegistry 772–789.
* **S17 — login grant audit:** [WorldBootstrapHandler.java](src/main/java/com/seggellion/britannia_mod/event/WorldBootstrapHandler.java), 66 onward, 117–137; [PlayerData.java](src/main/java/com/seggellion/britannia_mod/player/PlayerData.java), 44–72; [BlessedItemSyncHandler.java](src/main/java/com/seggellion/britannia_mod/util/BlessedItemSyncHandler.java); [BlessedItemInventorySync.java](src/main/java/com/seggellion/britannia_mod/util/BlessedItemInventorySync.java); SkillManager in S8.
* **S18 — diagnostics:** [CommandRegistry.java](src/main/java/com/seggellion/britannia_mod/registry/CommandRegistry.java), 31, 43; [FarmingDebugCommand.java](src/main/java/com/seggellion/britannia_mod/commands/FarmingDebugCommand.java), 51–70, 125 onward; [SetSkillCommand.java](src/main/java/com/seggellion/britannia_mod/commands/SetSkillCommand.java), 17–40; SkillManager 361–416 (admin set can write Rails).
* **S19 — alternate loot/currency:** [Earth Elemental loot](src/main/resources/data/britannia_mod/loot_table/entities/earth_elemental.json); [Wisp loot](src/main/resources/data/britannia_mod/loot_table/entities/wisp.json); [Wisp spawn modifier](src/main/resources/data/britannia_mod/neoforge/biome_modifier/wisp_spawn.json); [EntityRegistry.java](src/main/java/com/seggellion/britannia_mod/registry/EntityRegistry.java), 666–705; [ShameDungeonSpawner.java](src/main/java/com/seggellion/britannia_mod/spawner/ShameDungeonSpawner.java), 33–52, 73–84, 143–199; [EarthElementalEntity.java](src/main/java/com/seggellion/britannia_mod/entity/EarthElementalEntity.java), 22–26.
* **S20 — provisioned integration coverage:** [Patch18FullLoopGameTests.java](src/main/java/com/seggellion/britannia_mod/gametest/Patch18FullLoopGameTests.java), 67–178, 182–211, 255–264; [FertileDirtFinalMixGameTests.java](src/main/java/com/seggellion/britannia_mod/gametest/FertileDirtFinalMixGameTests.java); [FertileDirtLifecycleGameTests.java](src/main/java/com/seggellion/britannia_mod/gametest/FertileDirtLifecycleGameTests.java).
