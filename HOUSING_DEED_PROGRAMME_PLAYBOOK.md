# UltimaCraft — Housing Deed & Architect Vendor Programme Playbook

## Project Purpose

This project puts **house deeds into the player-facing economy** on the `patch-18` branch, and repairs the housing defects that currently prevent that system from working at all.

Three outcomes define success:

1. A player can walk up to an Architect in a city, see house deeds in their catalogue at Ultima Online prices, buy one with coin, and place the house.
2. The house they place keeps its identity across a server restart — its doors unlock with their key, and they can dig their basement.
3. Three new houses — **two-story villa**, **large house with patio**, and **small stone keep** — exist as placeable structures, with a repeatable pipeline for the sixteen remaining RunUO deeds.

The owner has stated the intent plainly: there are two kinds of deed. The **blessed** deed is bought with real money through the website and is granted by the existing deed service. The **house deed** is sold by the Architect vendor and **must not exist outside the vendor economy** — it uses every mechanism other economy vendors use, and so do all other houses.

The implementation must be based on the architecture that actually exists in the two repositories. Do not assume how structures are placed, persisted, protected, locked, priced, or spawned until the code has been inspected. Several assumptions that seemed obvious during scoping turned out to be wrong; the appendix records what was verified so it does not have to be re-derived, but it does not replace reading the code.

---

# Operating Principles

## 1. Discovery before implementation

Do not begin by creating a deed item or seeding a product.

Every milestone that changes behaviour begins by reading the code it will change and stating what is actually there. Where this playbook records a finding, confirm it still holds — the appendix was accurate at the time of scoping, not necessarily at the time of building.

The repositories are authoritative. This document is a map, not a substitute.

## 2. Two repositories, one contract

This programme spans both halves of UltimaCraft:

- **Mod** — the `britannia_mod` worktree, branch `patch-18`.
- **Rails** — `\\wsl.localhost\Ubuntu\home\dusti\ultimacraft-website`.

The mod's vocabularies must stay level with Rails. A mod that reads a field Rails does not yet emit, or a Rails that ships a key the mod rejects, is a defect even when both suites are green. Where a milestone touches both, **Rails ships first** and the mod's parser tolerates its absence, matching the convention already established by the Economic NPC registry.

Never invent a Rails endpoint, column, or reason code from the mod side. Read the Rails source.

## 3. Protect `patch-18`

Work only on the intended `patch-18` branch unless the owner explicitly directs otherwise.

Before implementation:

- confirm the current branch
- inspect working-tree status
- do not discard unrelated owner changes
- do not reset, clean, or rewrite history destructively
- do not modify unrelated feature work

Commit at milestone boundaries with a clear message. Do not push unless the owner asks.

## 4. Fail closed, and say why

The economy is built on a principle worth preserving: an unavailable product is not offered, and the reason is machine-readable. Extend that, never bypass it.

Be alert to the specific failure this system is prone to — **silence**. A misconfigured deed does not raise an error; it simply never appears in the catalogue, because the mod drops rows where `available == false`. When something does not show up, the cause is a reason code somewhere, not a mystery. Find it.

## 5. Do not invent requirements

Only the requirements in this playbook are committed scope. Record any defect discovered during analysis; do not fix it opportunistically unless it blocks the milestone in hand. Milestone 9 exists to collect them.

Three decisions are explicitly the owner's and are marked **OWNER DECISION** where they arise. Do not resolve them by choosing a default — stop and ask.

## 6. Build it as a table, not as a special case

The owner has confirmed **all nineteen RunUO deeds follow the same pattern**; only the NBT geometry differs. Anything written for the villa that would have to be written again for the keep is written wrong. A new house should cost one row in a table, one pair of NBT files, and nothing else.

## 7. Evidence at every exit

Each milestone ends with a concise evidence record in Claude's response — commands run, output observed, tests green — rather than new documentation files, unless such files already belong to the repository's normal workflow.

---

# Scope

## Required Feature A — Structure region durability

House regions must survive a server restart. This is the root cause of the reported basement defect and of every door being permanently locked; nothing else in this programme can be tested until it is fixed.

## Required Feature B — The NBT pipeline and three new structures

A repeatable, tested pipeline that takes a structure-block export and produces both shipped copies in the correct formats, plus the villa, patio and keep placed through it.

## Required Feature C — House styles, sizes and deed items

Three new house styles registering three new deed items, driven from one table.

## Required Feature D — Owner build rights inside a house

Replace the current survival-inside-your-house model with the creative-mode model the owner has specified: the owner may break everything inside their house except foundation blocks.

## Required Feature E — Building-material commodities

Plaster, sandstone, finished glass and thatch as real city commodities, so houses can be produced from city supply rather than conjured.

## Required Feature F — The Architect as an economy vendor

A Rails-authoritative `architect_vendor` type with deed products and listings, spawning on economic conditions, settling through the same retail transaction every other vendor uses.

---

# Non-Goals

- The **blessed deed** path stays exactly as it is. `DeedHttpServer` continues to grant website-purchased deeds. This programme does not move it into the vendor economy, and does not change its wire format.
- The remaining sixteen RunUO deeds are **not** authored here. The programme must make adding them trivial; it does not add them.
- No calibration of the wider commodity economy. New commodities are seeded with defensible starting values; tuning is a separate exercise.
- No changes to banking, guilds, quests, skills, or any vendor other than the Architect.
- No redesign of the house management UI.

---

# Milestone 0 — Repository Safety & Baseline

## Goal

Establish a known-good starting point in both repositories, and remove one live server-stability risk before anything else changes.

## Tasks

- Confirm the mod worktree is on `patch-18`; record the HEAD commit.
- Record `git status`; do not modify unrelated owner changes.
- Confirm the Rails checkout is reachable and record its branch and HEAD.
- Run and record the baseline:
  - `./gradlew test`
  - `./gradlew runGameTestServer --no-configuration-cache`
- Note any test already failing **before** any change, so a pre-existing failure is never mistaken for a regression. The textile GameTest is known to be flaky.
- **Remove the per-tick log line.** `SurvivalZoneHandler.onServerTick` calls `LOGGER.info("Is inside structure")` on every server tick, per player, for every player standing inside a house. This is the console-flood shape that previously rate-limited the Apex console, blocked the server thread and timed players out. It is a one-line deletion and it should not wait for the milestone that rewrites that class.

## Exit Criteria

- Branch, HEAD and working-tree state recorded for both repositories.
- Baseline test results recorded, including any pre-existing failure.
- The per-tick log line is gone and the suite is still green.

---

# Milestone 1 — Housing Architecture Reconnaissance

## Goal

Produce an accurate map of the housing system as it exists, so that later milestones change it deliberately rather than by discovery.

No production code changes in this milestone.

## Required Investigation

Trace and record, with file and line references:

**Placement**
- `AbstractHouseDeedItem` → `HousePlacementPayload` → `HousePlacementHandler` → `StructurePlacer.placeStructure`
- How the template is loaded, and which of the two load paths actually executes
- How rotation is chosen, where the placement anchor comes from, and how the ground-validation loop constrains where a house may go
- Where the house lot block is placed and what it records

**Identity and persistence**
- `StructureRecord`, `StructureRegionManager`, `StructureBoxes`
- Every consumer of `getFullBox()`
- What is persisted, where, and by whom — for the record, the lot block entity, and the Rails house row
- What `HouseDataAPI` sends and what Rails stores

**Doors**
- `LockableDoorBlock`, `LockableDoorBlockEntity`, how a door resolves its lock identity
- Which door blocks are bound to the lockable block-entity type
- `HouseKeyItem` and how a key is issued and matched

**Player rights inside a house**
- `SurvivalZoneHandler`, `StructureProtectionHandler`, `HousePrivacyHandler`
- Exactly which game mode a player is in, where, and what grants exceptions

**Vendor routing**
- `ArchitectEntity` interaction → `ServerCatalogService.fetchAndSend` → which of the catalogue branches an Architect takes today
- `MerchantRoleHandler` → `BuyMerchantItemsC2SPayload` → `MerchantEconomyService` → `EconomicVendorPurchaseService`
- Why an Architect purchase fails today

**Rails economy**
- `EconomicCatalog::ForVendor`, `EconomicCatalog::ProductAvailability`, `Economy::EconomicVendorPurchase`
- `EconomicNpcType`, `Product`, `ProductListing`, and how the rollout seeds them
- `EconomicNpcs::Eligibility`, `EconomicStaffing::Reconcile`, `NpcRequirements::Evaluate`

## Architecture Questions Claude Must Answer

1. After a server restart, what exactly does a house still know about itself, and what has it forgotten?
2. Can a door in one chunk of a multi-chunk house resolve the same lock as a door in another?
3. What prevents a player from breaking a block, and does that rule differ inside a house they own?
4. What makes a Rails product available, and what makes one silently unavailable?
5. What must be true of an entity before its catalogue request routes to the economic endpoint?
6. Which of the two structure NBT copies is read by the client, which by the server, and in what format?

## Deliverable

A written map in the response — subsystems, the flow between them, and the specific defects confirmed. Where this playbook's appendix disagrees with the code, **the code wins**; say so explicitly.

## Exit Criteria

- All six questions answered with file and line evidence.
- Any appendix correction recorded.
- No production code changed.

---

# Milestone 2 — Structure Region Persistence

## Goal

House regions survive a server restart. This is the blocking milestone; Feature A.

## Background

`StructureRegionManager` is a static in-memory map. `registerStructure` has one production caller — `StructurePlacer`, at placement time. Nothing saves it, nothing loads it, and `HouseDataAPI` only ever posts create and delete to Rails. On restart every region is lost, and with it:

- the owner's right to break blocks inside their own house, including the basement
- every door's lock identity, which leaves doors permanently locked because `locked` defaults to `true` and persists in block-entity NBT
- house farm plots, Grabby policy, survival zones and structure protection

## Tasks

- **Persist rotation.** It is currently stored nowhere — not in the lot block entity, not in the Rails payload — and the bounding box cannot be reconstructed without it. Decide where it lives and record why.
- Extend the Rails house record so a region can be rebuilt exactly. Rails already has an unused `houses.structure` jsonb column intended for this.
- Add a Rails read endpoint returning a shard's houses, authenticated the same way every other shard-server endpoint is.
- Rehydrate `StructureRegionManager` on server start, off the server thread, failing safe if Rails is unreachable — a shard that cannot reach Rails must not silently strip every player of their house rights.
- Decide and document what happens to houses placed while Rails was unreachable.
- Cover it: a unit test for the record round trip, and a GameTest that places a house, clears the manager, rehydrates, and proves a door resolves its lock and the owner may dig.

## Exit Criteria

- A house placed, server restarted, and the owner can still unlock their door and break blocks beneath their house.
- Rehydration failure is logged once and degrades safely, not per tick.
- Both suites green.

---

# Milestone 3 — The NBT Pipeline

## Goal

A tested, repeatable pipeline from structure-block export to both shipped copies. Feature B, first half.

## Background — verified, and it corrects an earlier note

Minecraft's structure block exports **gzip-compressed** NBT to the world's generated folder. The mod ships two copies of every structure, in **different formats**, and both are load-bearing:

| Copy | Format | Read by |
|---|---|---|
| `src/main/resources/assets/britannia_mod/structures/<name>.nbt` | gzip (`1f 8b`) | client ghost preview, `NbtIo.readCompressed` |
| `src/main/resources/data/britannia_mod/structures/<name>.nbt` | raw NBT (`0a 00`) | server placement, `NbtIo.read` |

This was confirmed by comparing bytes: each generated export is **byte-identical** to its `assets/` copy, and the `data/` copy is that file gunzipped.

So the pipeline is **copy to `assets/`, gunzip to `data/`**. Note that this is the opposite direction from the PowerShell snippet previously in use, which compressed the `data/` copies into `.nbt.gz` files beside themselves. That snippet also targeted `build/resources/main`, where Gradle discards the result on the next build. Neither behaviour is what the mod needs.

Source of exports:

```
C:\Users\dusti\curseforge\minecraft\Instances\UltimaCraft - Britannia\saves\Sandbox\generated\britannia_mod\structures
```

**As of scoping this folder contains only the seven existing structures** — castle, field_stone_house, small_brick_house, stone_and_plaster_house, thatched_roof_cottage, wood_and_plaster_house, wooden_house. The villa, patio and keep have not been authored yet. Build the pipeline against the seven, prove it reproduces the shipped copies byte-for-byte, and make it re-runnable for the three when they arrive.

## Tasks

- Add a checked-in tool under `tools/` that takes an export directory and writes both copies into `src/main/resources`. It must be idempotent and safe to re-run.
- Verify it against the existing seven: running it must reproduce the current `assets/` copies byte-identically and the current `data/` copies byte-identically.
- Add a test asserting, for every registered house style, that both copies exist and carry the correct magic bytes. A missing or wrongly-formatted client copy must fail the build, not surface as an invisible ghost preview in game.
- Document the authoring loop in the tool's own help text: export from a structure block, run the tool, run the test.

## Exit Criteria

- The tool reproduces all fourteen existing files exactly.
- The format test passes and fails correctly when a copy is removed or mis-compressed.
- The loop is documented where the next person will find it.

---

# Milestone 4 — House Styles, Sizes and Deed Items

## Goal

The villa, patio and keep exist as registered styles with deed items. Features B and C.

## The table

| House | Style | Stub | Size (W×H×D) | RunUO deed | Price (gold) |
|---|---|---|---|---|---|
| Two-story villa | `VILLA` | `villa` | 11 × 10 × 11 | `VillaDeed` | 136,500 |
| Large house with patio | `PATIO` | `large_patio` | 18 × 8 × 18 | `LargePatioDeed` | 152,800 |
| Small stone keep | `KEEP` | `keep` | 25 × 12 × 26 | `KeepDeed` | 665,200 |

Note that "villa" and "two-story villa" are the same RunUO deed — `VillaDeed` is literally *"deed to a two story villa"*. There is no separate two-story variant to add.

## Tasks

- Add three `HouseSize` constants. Existing sizes are `SMALL` 9×8×9, `MEDIUM` 9×7×9, `TOWER` 13×7×13, `CASTLE` 34×20×35, so none of the three fit an existing constant. The keep is the first non-square footprint (25 wide, 26 deep) — confirm the rotation and bounding-box maths handle it, and add a test that pins it.
- Add three `HouseStyle` entries. Deed item registration and creative-tab placement already derive from the enum; confirm this still holds rather than assuming it.
- Choose each style's lot offset deliberately rather than copying `-2`. Read what the offset does first.
- Add item models and language strings following the existing deed naming.
- Place the six NBT files through the Milestone 3 tool once the owner has authored them.

## The one geometry constraint

`StructureUtils.getDoorOffset()` hardcodes the placement anchor to `(size.x / 2, 0, 0)` — front face, horizontal centre. Every shipped structure honours it: the small houses put their door at x=4 of 9, the castle at x=17 of 34.

**Each new structure must put its main entrance on that anchor**, or the house will land offset from where the player aimed. Interior doors are unconstrained. If a structure cannot honour it, that is a signal to generalise the anchor — not to fudge the geometry — and it needs a note to the owner.

## Exit Criteria

- Three deeds registered, appearing in the creative tab, with correct names.
- Each places its structure at the aimed position, in all four rotations.
- The keep's non-square footprint rotates correctly.
- Suites green.

---

# Milestone 5 — Owner Build Rights and the Foundation Tag

## Goal

Implement the owner's specified model: **inside their own house, the owner is in creative mode and may break everything except foundation blocks.** Feature D.

## Background

The current model is survival-inside-your-house, adventure everywhere else, with mod tools as an escape hatch. Nothing excavates a basement — the region simply extends ten blocks below the structure so the owner may dig it themselves.

Two pieces of code assume creative means unrestricted, and both must be inverted:

- `StructureProtectionHandler` opens with `if (CREATIVE) return;`. Under the new model that early return would disable the foundation exception entirely. Creative-inside-your-own-house becomes precisely the case that needs the foundation test.
- `SurvivalZoneHandler` skips players already in creative, so it would never restore the mode on exit. It must own the transition in both directions and remember the player's prior mode — including across logout inside a house.

## Tasks

- Add a `britannia_mod:house_foundation` block tag and a `ModTags` key over it. Ten foundation blocks are registered and no tag groups them today.
  - **Watch the directory.** Both `data/britannia_mod/tags/block/` (correct for 1.21) and `data/britannia_mod/tags/blocks/` (vestigial, ignored) exist in the repository. The new tag goes in the singular one.
- Rework the game-mode transition so entering your own house grants creative and leaving restores what you had.
- Rework block-break protection so foundation blocks are protected from the owner too, and non-owners remain excluded entirely.
- Cover it with GameTests: owner breaks a wall (allowed), owner breaks a foundation block (denied), non-owner breaks anything (denied), owner logs out inside and returns to the right mode.

## OWNER DECISION — required before this milestone can be built

**1. How does a player get into their basement?** Every structure lays a solid foundation floor — the small houses a 7×7 slab of 49 `brick_foundation_spruce`, the castle 292 `stone_foundation`. If the whole slab is protected there is no way down. Either one block must be breakable, or every house ships with a hatch or stair well.

**2. What happens to the creative inventory?** Creative mode hands out the full creative inventory, flight, and instant break. A player standing in their own house could spawn any item in the game and walk out with it. Vanilla creative cannot be partially disabled without additional work — gating the creative tabs, item pickup and flight separately.

Do not choose a default for either. Stop and ask.

## Exit Criteria

- Owner may build and dig inside their house; foundation blocks resist them.
- Non-owners are unaffected by the change.
- Game mode is restored correctly on exit and across a logout.
- The decisions above are recorded with the owner's answer.

---

# Milestone 6 — Building-Material Commodities

## Goal

The materials houses are made of exist as city commodities. Feature E, and the milestone that unblocks pricing.

## Background

The owner specified villa materials as plaster, wood, sandstone, glass and thatched roof. `CommoditySeeder` has 51 category/subcategory pairs, all raw agriculture, mining and animal products. Four of the five are absent:

| Material | Nearest seeded commodity | Status |
|---|---|---|
| Wood | `wood\|logs\|oak` (cap 5,000) | exists |
| Glass | `glass\|raw\|sand` (cap 5,000) | raw input only, no finished glass |
| Plaster | `stone\|sedimentary\|limestone` (cap 3,000) | no plaster |
| Sandstone | none in any of seven stone subcategories | missing |
| Thatch | `grain\|whole\|wheat` (cap 5,000) | no thatch |

This also resolves a hard blocker. `EconomicCatalog::ProductAvailability` records a `no_production_requirements` reason when a product has neither commodity nor material requirements, and then sets `available = reasons.empty?`. **A product with a price but no recipe is permanently unavailable**, and its `available_units` stays zero. Real recipes make the problem disappear without touching the service that both the catalogue and settlement depend on. Prefer that.

## Tasks

- Extend `CommoditySeeder` with the missing building materials, following the existing category/subcategory conventions and choosing caps and base prices consistent with comparable goods.
- Decide production policy: which cities produce them, and how supply enters the economy.
- Decide which trader buys them back from players, and extend that type's accepted-commodity policy. A commodity players cannot sell is a commodity that only ever depletes.
- Seed idempotently; existing rows are never overwritten.
- Cover it with a Rails test that a house product with these requirements prices, stocks and settles.

## Exit Criteria

- All five villa materials resolve to real commodities.
- A test proves a deed-shaped product becomes available when supply exists and unavailable when it does not, with the correct reason code.
- Rails suite green.

---

# Milestone 7 — The Architect as an Economy Vendor

## Goal

Deeds are sold by the Architect through the ordinary retail path. Feature F.

## Background

The Architect currently has no catalogue service at all. The project's own mapping records it: `"ArchitectEntity exists in Minecraft but has no catalogue service"`, rollout `excluded_service`. Interaction routes to the legacy catalogue endpoint, which holds no rows for the role, so the vendor reports having nothing. Even with rows, purchase would fail — the settlement path requires an economy merchant entity or a stamped economic citizen, and the Architect is neither.

Stamping the entity with an economic type key flips **both** the catalogue fetch and purchase settlement onto the economic path. That single stamp is the mechanism; it does not require new mod code.

## Tasks

**Rails**

- Create the `architect_vendor` economic NPC type: kind `vendor`, profession `architect`, entity `britannia_mod:architect`, active and spawnable, accepted commodities empty — a vendor buys nothing, and the empty policy is the deliberate expression of that.
- Create one `Product` per deed at the RunUO gold price, with the Milestone 6 recipes, and one `ProductListing` per deed against `architect_vendor`.
- **Fix the denomination gap.** `price_denomination` is absent from `Admin::ProductsController#product_params` and from every admin view. A product created through the admin UI falls back to copper, so a 136,500-gold deed would be quoted at 136,500 *copper*. Add the parameter and the form field.
- Write a standalone idempotent seed rather than regenerating the RunUO rollout artefact. The generator was removed from the mod repository during the patch-18 sanitise commit, and deeds are an owner-decision exception to that matrix in any case.

**Mod**

- Confirm the stamped Architect routes to the economic catalogue and settles through the economic purchase service. Change mod code only if reconnaissance shows it is genuinely required — the expectation is that it is not.
- Verify deeds arrive intact. Deeds are single-stack items; the grant path issues one stack per unit, which is correct.

## Exit Criteria

- A player interacts with a stamped Architect and sees deeds priced in gold.
- Buying one debits the correct coin, credits the city treasury, consumes city commodities, and delivers the deed.
- A deed whose city lacks materials is absent from the catalogue, with the reason visible in the Rails decision.

---

# Milestone 8 — Architect Spawn Gating

## Goal

The Architect spawns from its spawn block on Rails-authoritative economic conditions, not hardcoded mod constants.

## Background

The gate today is mod-side and fixed: `ArchitectSpawnBlockEntity` requires 400 food and 200 wood, permits one architect, and cools down for 1200 ticks. Rails already owns the right machinery — `default_requirements` evaluated by `NpcRequirements::Evaluate`, with rule kinds `supply_gte`, `supply_gt`, `treasury_gte`, `treasury_gt`, `commodity_exists` and `commodity_quantity_gte`, plus hysteresis dwell times that stop a post flapping around a threshold.

So "realistic economic factors" is configuration, not new code.

## Tasks

- Express the Architect's economic conditions as Rails requirement rules. A treasury floor plus standing stock of the materials it builds from is the natural shape; propose concrete values and get them confirmed.
- Create the spawn point with the `architect_vendor` key, and confirm the staffing reconciler fills the post and the assignment reaches the mod.
- **Retire the mod-side food and wood gate** so there is one authority rather than two that can disagree.
- Verify the spawn block still governs *where* the Architect appears while Rails governs *whether*.

## OWNER DECISION

What must a city prove before it earns an Architect? Propose values; do not adopt them unilaterally.

## Exit Criteria

- An eligible city staffs an Architect; an ineligible one does not, with a legible reason.
- The mod-side gate is gone and nothing else depended on it.
- Crossing the threshold repeatedly does not cause the post to flap.

---

# Milestone 9 — Accounting, Defects and Coverage

## Goal

Leave the project's own bookkeeping true, and clear the defects found along the way.

## Tasks

**Matrix accounting** — two coverage tests read the same accounting from opposite repositories and must move together:

- In the mod's `runuo_ultimacraft_mapping.json`: flip the Architect's `vendor_rollout` from `excluded_service` to the implemented form, and each implemented deed row's `product_status` from `excluded_service` to `seeded`, giving every seeded row a non-null `uc_item_id` — the mod's coverage test asserts that.
- In Rails' `db/seeds/data/economic_vendor_rollout.json`: move `row_status_counts` by exactly the same amount.
- The total must stay 1015 and seeded coverage must not regress. Both suites stay green only if the two files move in step. Verify by running both.

**Defects**

- **Blessed re-deed.** `HouseActionHandler` stamps `blessed: true` on every deed returned when a house is re-deeded. A vendor-bought house can be re-deeded into a blessed deed for free, undercutting the paid website product. Return the deed in the state it arrived.
- Any defect recorded during earlier milestones and deferred here.

## Exit Criteria

- Both coverage suites green, in both repositories.
- Re-deeding preserves blessed state; a test pins it.
- The deferred-defect list is empty or explicitly triaged with the owner.

---

# Milestone 10 — End-to-End Validation

## Goal

Prove the three outcomes in the Project Purpose, in a running game.

## Tasks

Walk the whole path and record evidence at each step:

1. A city meets the economic conditions and staffs an Architect.
2. A player interacts and sees villa, patio and keep priced in gold.
3. The player buys a villa deed; coin is debited, treasury credited, city commodities consumed.
4. The player places the villa; it lands where they aimed, in a non-default rotation.
5. The player receives a key; every door in the house — exterior and interior — locks and unlocks with it.
6. The player enters creative inside the house, breaks a wall, and cannot break a foundation block.
7. The player digs a basement.
8. **The server restarts.** The doors still unlock, the basement is still theirs to dig, and the region is intact.
9. A city stripped of building materials no longer offers the deed, and the reason is legible.

## Exit Criteria

- All nine steps demonstrated with evidence.
- Both suites green.
- Any behaviour that surprised the owner is recorded, not quietly normalised.

---

# Appendix — Verified Findings

Accurate at the time of scoping. Confirm before relying on any of it; where the code disagrees, the code wins.

## Structure formats

- Generated exports are gzip; each is byte-identical to its `assets/` copy; the `data/` copy is that file gunzipped.
- Because the `data/` copies are uncompressed, the vanilla `getStructureManager().getOrCreate()` path in `StructurePlacer` always fails and every placement falls through to the manual `NbtIo.read` branch. This works, but it means the vanilla path is dead code that looks live.

## Doors in the shipped structures

| Structure | Size | Door block | Openings | Positions |
|---|---|---|---|---|
| six small houses | 9×8×9 | `lockable_wood_door` | 1 | (4, 1, 1) facing south |
| castle | 34×20×35 | `lockable_metal_door` | 4 double | z = 1, 5, 13, 28 at x = 16/17 |

The lock system already scales to many doors and needs no redesign. A door derives its lock identity from the enclosing structure record at query time rather than storing it, so one key opens every door in a house. Records are indexed into every chunk a structure overlaps, so multi-chunk houses resolve correctly. The castle already runs four double doors, three of them interior.

## Foundation blocks

Ten are registered: `brick_foundation_oak`, `brick_foundation_spruce`, `brick_foundation_sandstone`, `brick_foundation_dark_sandstone`, `brick_foundation_flagstone`, `stone_foundation`, `cobblestone_foundation`, `plaster_stone_foundation`, `plaster_wood_foundation`, `curtain_foundation`. No tag groups them.

In use: the six small houses each lay 49 `brick_foundation_spruce`; `field_stone_house` adds 33 `cobblestone_foundation`; `stone_and_plaster_house` adds 84 `plaster_stone_foundation`; the castle lays 292 `stone_foundation`. Wall-base courses are foundation blocks too, not just floors.

## Economy mechanics

- Available units are `floor(supply / required)`, minimum across inputs, capped at 999. Purchases consume city supply and credit the city treasury in the paid denomination. So an Architect can only sell a house when the city holds the materials — the behaviour the owner asked for, obtained for free.
- Purchase re-validates through the same availability service the catalogue used, so display and settlement cannot disagree.
- The mod drops catalogue rows where `available == false`. A misconfigured product is invisible rather than erroneous.
- No shard policy row is needed for a new type; an active type with no policy resolves as enabled.

## RunUO deed prices, for the remaining sixteen

`StonePlasterHouseDeed` 43,800 · `FieldStoneHouseDeed` 43,800 · `SmallBrickHouseDeed` 43,800 · `WoodHouseDeed` 43,800 · `WoodPlasterHouseDeed` 43,800 · `ThatchedRoofCottageDeed` 43,800 · `StoneWorkshopDeed` 60,600 · `MarbleWorkshopDeed` 63,000 · `SmallTowerDeed` 88,500 · `SandstonePatioDeed` 90,900 · `LogCabinDeed` 97,800 · `VillaDeed` 136,500 · `BrickHouseDeed` 144,500 · `LargePatioDeed` 152,800 · `LargeMarbleDeed` 192,000 · `TwoStoryWoodPlasterHouseDeed` 192,400 · `TowerDeed` 433,200 · `KeepDeed` 665,200 · `CastleDeed` 1,022,800

One RunUO gold piece is one UltimaCraft gold, by owner rule.

## Environment notes

- Rails lives at `\\wsl.localhost\Ubuntu\home\dusti\ultimacraft-website` and is reachable over the UNC path.
- The Rails development server needs a manual start and a `DB_PASSWORD`; the test database has had ownership problems before.
- The textile GameTest is known to be flaky and can redden CI without a real regression.
- Gradle GameTests need `--no-configuration-cache`.
