# UltimaCraft Vendor/Trader Economy
# Compatibility Map Review and Required Refactors

**Purpose:** Review the current `COMPATIBILITY_MAP.md` and identify the changes required to align it with the owner-approved Vendor/Trader economy architecture.

**Status:** Architecture review companion document

**Important:** The current compatibility map is strong as a forensic description of the existing Trader, Merchant, and Service NPC systems. Most of the current-state analysis should remain intact. The primary changes are to the target-architecture conclusions, which were written before several owner decisions were finalized.

---

# 1. Overall Assessment

The current compatibility map makes sense and is valuable.

Its strongest sections are the source-backed descriptions of:

- legacy Trader spawning and identity;
- legacy Merchant spawning and identity;
- `sourceId` coupling;
- Rails live-NPC heartbeat behavior;
- Trader transaction flow;
- Merchant transaction flow;
- current commodity price authority;
- the lack of treasury coupling;
- Merchant-side client-submitted pricing;
- Service NPC post identity;
- durable registration/outbox behavior;
- Rails-owned `WorldNpc`;
- `NpcSpawnAssignment`;
- bootstrap/state-change synchronization;
- deterministic entity reconciliation;
- menu-backed Service NPC configuration security.

Those findings should remain as repository facts unless subsequent source inspection proves the implementation changed.

The main issue is that several target-design conclusions are now stale because they predate the owner-approved decisions documented in the regenerated `OPEN_QUESTIONS.md`.

The compatibility map should clearly distinguish:

```text
FACT
Verified from repository source.

OWNER DECISION
Required target architecture.

IMPLEMENTATION DETAIL
Exact class/table/API shape to be determined during the relevant milestone.

OPEN QUESTION
Actually unresolved.
```

This separation will make the compatibility map much more durable.

---

# 2. Preserve the Current-State Forensic Sections

Sections describing the existing systems should generally remain.

In particular, preserve the findings that:

## Legacy Trader / Merchant identity

Legacy Trader and Merchant spawn blocks currently combine multiple identity/lifecycle concepts around `sourceId`, generated NPC UUIDs, source tags, live-NPC synchronization, and saved NBT snapshots.

This is important evidence for why the target architecture must use the Service NPC identity model.

## Legacy transaction behavior

Preserve documentation showing that:

- Trader sales are Rails-priced using city commodity market data;
- Minecraft reserves/removes actual inventory items;
- Rails provides idempotency at the transaction-record level;
- the Minecraft-side reservation leg is not fully crash/idempotency safe;
- legacy Trader payouts do not debit a city treasury;
- legacy payout representation is duplicated between the Rails `shard_user.currency` ledger and physical coin grants;
- Merchant prices are calculated in Java;
- Merchant recipes are hard-coded in Java;
- Rails currently accepts submitted Merchant line prices;
- Merchant purchases do not credit the city treasury.

## Service NPC architecture

Preserve the Service NPC findings around:

- server-generated stable spawn-post UUID;
- clone collision repair;
- NBT identity sanitization;
- durable pending operation outbox;
- Rails spawn-point registration;
- source revision handling;
- `WorldNpc`;
- `NpcSpawnAssignment`;
- versioned bootstrap/world-state change synchronization;
- cache-driven entity materialization;
- deterministic duplicate removal;
- secure menu-backed configuration;
- Rails-owned economic eligibility/staffing.

These are the correct architectural precedents for economic NPCs.

---

# 3. Update the Economic NPC Type Decision

The compatibility map currently treats the relationship between `ServiceNpcType` and economic NPC types as unresolved.

That is no longer an owner-level open question.

## Required target direction

Use a generalized NPC role/type foundation with specialized Service and Economic definitions.

Conceptually:

```text
general NPC type / role
    |
    +-- Service specialization
    |     dialogue
    |     service actions
    |     guildmaster/skill configuration
    |
    +-- Economic specialization
          kind: vendor | trader
          product/catalog policy
          valuation strategy
          economic eligibility
```

The following concepts remain shared:

```text
WorldNpc
persistent spawn post
NpcSpawnAssignment
City
Shard
persistent NPC identity
```

Do not create a second identity system for Vendors/Traders.

Do not force Vendors/Traders to satisfy Service-specific dialogue or Guildmaster validation simply because the existing table is named `ServiceNpcType`.

## Compatibility-map change

Conclusions that currently say:

```text
add kind to ServiceNpcType
OR
create sibling economic type
```

should be rewritten as:

```text
OWNER DECISION:
Use a generalized shared NPC type foundation with Service/Economic specialization.

IMPLEMENTATION DETAIL:
Milestone 3 determines the exact Rails table/class migration shape.
```

---

# 4. Replace the Recipe-Authority Conclusion

This is the largest required correction.

The current compatibility map concludes that Minecraft is the existing recipe authority and suggests exporting Minecraft recipe compositions to Rails or submitting recipe compositions for quotes.

That is not the target architecture.

## Owner-approved rule

Rails `Product` is the authoritative NPC product and NPC production-requirement model.

The existing Rails `products` table already contains:

```text
item_id
requirements JSONB
price
```

That model should be expanded to represent NPC economic product requirements.

## Hard prohibition

Do not use any of the following as the authoritative recipe source for NPC Vendor/Trader production or valuation:

```text
vanilla Minecraft recipes
legacy Minecraft recipes
MerchantRecipes
CraftableRegistry
datapack crafting recipes
other player-crafting recipe definitions
```

Player crafting and NPC economic production are separate systems.

## Target model

```text
Rails Product
    |
    +-- item_id
    +-- requirements JSONB
    +-- RunUO/default baseline price
    +-- economic product policy
    +-- material-family policy
    +-- quality policy
    +-- Vendor associations
    |
    v
city economy availability + dynamic price
```

`Product.requirements` should evolve beyond:

```json
{
  "wood": 0,
  "metal": 0,
  "stone": 0
}
```

to support real commodity requirements and quantities.

Conceptually:

```json
{
  "commodities": [
    {
      "key": "wood|boards|oak",
      "quantity": 2
    },
    {
      "material_family": "metal",
      "quantity": 8
    }
  ]
}
```

The exact JSON schema is an implementation detail.

## Compatibility-map replacement

Replace the existing "How can product recipes be valued without a second recipe authority?" conclusion with:

> Rails `Product.requirements` is authoritative for NPC economic production. Minecraft crafting recipes are not used for NPC Vendor/Trader production or valuation. Default Rails Product definitions are seeded/imported from project-owned economic data derived from the RunUO mapping and UltimaCraft design. Shards can override NPC product requirements through Rails without Java changes.

---

# 5. Clarify the RunUO-to-Rails Data Flow

The current map correctly says RunUO Vendor definitions should live in Rails, but it should be more explicit about how the source data maps to Rails.

## Required flow

```text
Pinned RunUO source
        |
        v
Milestone 2 normalized project mapping
        |
        +--> Economic NPC / Vendor definition
        |
        +--> Rails Product definitions
        |
        +--> Vendor/Product associations
        |
        +--> Trader buyback mappings
        v
Rails seeds/default configuration
        |
        v
Shard overrides
        |
        v
City runtime economic evaluation
```

The pinned RunUO tree is the completeness authority.

The existing `runuo_vendor_catalog.json` is an input/reference artifact, not proof of complete coverage and not the runtime source of truth.

Once seeded/imported, Rails becomes authoritative for UltimaCraft.

---

# 6. Update NPC Product Quality

The current compatibility map says the quality choice remains ambiguous.

That is no longer true.

## Owner-approved quality rule

NPC-manufactured equipment should use:

```text
Crude
Basic
Fine          <- NPC Vendor product
Exceptional   <- maximum
```

NPC-produced equipment uses `Fine`.

The existing blacksmithing `Normal|Exceptional` representation is a compatibility/migration problem, not the target economic-product quality ladder.

Crop quality remains separate.

## Compatibility-map change

Replace the unresolved quality conclusion with:

> OWNER DECISION: NPC-manufactured equipment uses `Fine`, one tier below maximum `Exceptional`. Milestone implementation must bridge/normalize the existing quality representations without corrupting existing item data.

---

# 7. Add Material Variant Architecture

The current map does not sufficiently describe the owner-approved material selection and fallback behavior.

This should be added because it affects Rails Products, commodity availability, Vendor catalogs, UI behavior, and pricing.

## Explicit material progression

Material progression must be explicit Rails/economic data.

Do not derive economic ranking from:

```text
Java enum ordinal
durability
alphabetical ordering
implicit class ordering
```

Conceptually:

```text
material key
material family
economic rank
commodity mapping
enabled
```

## Product material behavior

For a Product with a variable material requirement:

```text
Base Product
    |
    | material_family = metal
    v
city material commodities
    |
    +-- Iron
    +-- higher material 1
    +-- higher material 2
    +-- ...
    +-- Valorite
```

All producible variants may be valid in the economy.

The interface does not need to show every material variant at once.

## UI catalog rule

For each base product slot:

> Display the highest-value currently producible material variant.

Example:

```text
Valorite available
    -> show Valorite product

Valorite insufficient
    -> show next-highest producible material

...

only Iron remains
    -> show Iron product
```

This allows prosperous cities to visibly sell higher-value goods without flooding the catalog.

---

# 8. Refactor the Target Pricing Description

Preserve the current-state finding that today's Merchant implementation uses:

```text
ceil(input cost * 1.70)
```

That is a forensic fact.

It is not the target pricing formula.

## RunUO price role

The RunUO item price is the baseline retail price anchor for the default/Iron version of the product.

It should not be treated as:

- a fixed price for every material variant; or
- something replaced entirely by raw material cost.

RunUO prices already represent non-material finished-product value such as craftsmanship, labor,
shop margin, and game-balance value.

## Target conceptual pricing

For a material-variable Product:

```text
baseline_material_cost =
    Product material quantity
    * reference/current Iron commodity value

selected_material_cost =
    Product material quantity
    * selected material current city commodity value

material_delta =
    selected_material_cost - baseline_material_cost

final_retail_price =
    RunUO Iron baseline
    + material_delta
    + approved UltimaCraft economic/production adjustment
```

The exact calibrated formula belongs in `ECONOMY_RULES.md`.

## Important

Do not define target pricing as:

```text
input cost * 1.70
```

Do not define it as:

```text
raw material cost only
```

Do not use fixed Java retail prices.

Rails calculates/authorizes the final retail price.

---

# 9. Add the Vendor Gold-Coin Rule

The compatibility map should explicitly document:

> City Vendor finished products are priced and sold in Gold coins.

This is intentionally expensive and is part of UltimaCraft's economic balance.

Conceptually distinguish:

```text
Player -> Trader
commodity/resource acquisition transaction

Vendor -> Player
finished/specialty NPC product retail transaction priced in Gold
```

Vendor sales credit the city treasury.

The product price reflects:

- RunUO baseline;
- current commodity/material costs;
- material variant;
- approved production/economic adjustment.

---

# 10. Refine Trader Price Authority

The compatibility map currently lists "Rails-authoritative sale pricing from `CityCommodity.current_price`" as a safe legacy behavior.

This should be made slightly more general.

## Preserve this

```text
Rails owns the valuation.
CityCommodity.current_price is an authoritative market input.
```

## Do not preserve this as a universal rule

```text
Trader payout == CityCommodity.current_price
```

The target system needs multiple valuation strategies.

Conceptually:

```text
CURRENT_COMMODITY_VALUE
RAW_COMMODITY_VALUE
PROCESSED_COMMODITY_VALUE
RECIPE_DERIVED_VALUE
WINE_QUALITY_VALUE
SALVAGE_MATERIAL_QUALITY_VALUE
```

Examples:

```text
raw commodity
    -> may use a shard-configured below-market multiplier

processed commodity
    -> may use full current market value

recipe-derived goods
    -> Rails Product/material valuation

wine
    -> quality-aware valuation

salvage
    -> material/quality-aware valuation
```

Therefore rewrite the safe legacy behavior as:

> Preserve Rails-authoritative valuation using `CityCommodity.current_price` and other Rails-owned economic data as valuation-strategy inputs.

---

# 11. Add Explicit Commodity Economic Policy

The target Rails boundary should contain more than commodity quantity and price.

Commodity policy should explicitly represent whether a commodity may participate in each economic direction.

At minimum, represent the equivalent of:

```text
npc_buy_enabled
npc_sell_enabled
production_enabled
```

The exact table/column shape can be determined by Milestone 3.

Do not derive these permissions permanently from category/subcategory naming.

A commodity may validly be:

```text
accepted from players = true
sold by NPC = false
usable for NPC production = false
```

---

# 12. Add Explicit Raw/Processed Commodity Classification

The target should not rely permanently on existing string conventions such as:

```text
raw
logs
whole
milled
ingot
```

Add explicit form/classification data.

Conceptually:

```text
raw
processed
finished
other
```

The exact enum terms may follow Rails project conventions.

This explicit field becomes the basis for shard policies such as:

```text
raw Trader payout multiplier
processed Trader payout multiplier
```

Existing commodities can be backfilled using current category/subcategory conventions and reviewed.

---

# 13. Update the Shard-Specific Requirement Conclusion

The compatibility map currently describes shard scoping as an unresolved open question.

It is now decided.

## Required pattern

```text
global NPC type default policy
        |
        v
shard-specific NPC type policy override
        |
        v
city eligibility evaluation
```

This applies to:

```text
Service NPC types
Economic NPC types
```

Do not create one global-only Service staffing system and one shard-aware Vendor system.

Shard Admin manages the override policy.

The exact Rails schema remains an implementation detail.

---

# 14. Update Economic Reconciliation

Preserve the current-state finding that `CityStaffing::Reconcile` is currently admin-triggered and that legacy Traders/Merchants do not react to the economy.

Add the owner-approved target.

## Target reconciliation

```text
commodity/treasury economic change
        |
        v
enqueue or mark reconciliation required
        |
        +-------------------+
        |                   |
        v                   v
event-driven job      periodic safety job
        |                   |
        +---------+---------+
                  |
                  v
      Rails economic eligibility
                  |
           hysteresis/stability
                  |
                  v
        staffing/assignment changes
                  |
                  v
      world-state synchronization
                  |
                  v
        Minecraft reconciliation
```

Do not execute the entire population/staffing engine directly in model `after_commit` callbacks.

Use hysteresis and/or minimum active/inactive durations so highly visible Vendors do not flicker around threshold values.

Minecraft does not independently decide whether an economic NPC should spawn.

---

# 15. Add the TownPerson Population Target

The current compatibility map documents that legacy Merchant/Trader blocks maintain `townPersonAmount`, but it does not yet document the target replacement.

This should be explicitly added.

## TownPersons remain economic

TownPersons are:

- an economic sink;
- an expression of prosperity;
- a visible indicator of food, alcohol, and city wealth.

They should not be removed from the economic model.

They should be removed from individual Trader/Merchant spawn-block ownership.

## Target architecture

```text
Rails city economy
    |
    | food
    | alcohol
    | wealth / treasury
    | future population factors
    v
desired TownPerson population
    |
    v
Minecraft city-region population manager
    |
    v
valid random locations throughout the city Region
```

## Population curve

Population should grow/decline nonlinearly with prosperity.

The behavior may feel exponential through useful ranges, but the function must be bounded.

Rails should support policy inputs equivalent to:

```text
minimum TownPerson population
maximum TownPerson population
food influence
alcohol influence
wealth influence
growth curve parameters
spawn rate limit
despawn rate limit
```

Do not use an unbounded exponential function.

## Minecraft regional spawning

Spawn throughout the authoritative city Region.

Validate candidate locations for:

```text
correct region
correct dimension
loaded chunk
valid ground
headroom
no invalid liquid/solid placement
reasonable player distance
local population density
population cap
```

Do not force-load chunks merely to satisfy a population target.

Minecraft should gradually converge toward the Rails desired population rather than instantly spawning or deleting a large number of citizens.

---

# 16. Add a TownPerson Row to the Comparison Table

Add a row similar to:

| Concern | Legacy Trader | Legacy Merchant | Service NPC | Target Economic NPC |
|---|---|---|---|---|
| Ambient TownPersons | Spawned by each block from `townPersonAmount`, coupled to legacy `sourceId` | Same | None | Separate Rails-driven city regional population subsystem |

This makes the migration boundary explicit.

---

# 17. Update the Legacy Spawn-Block Migration Plan

The existing migration plan for legacy Trader/Merchant posts is generally sound:

```text
read legacy NBT
resolve city name to stable city public ID
generate a NEW stable spawn-post UUID
register through the durable Service-NPC-style operation path
receive authoritative assignment
disable legacy maintenance only after acknowledgement
```

Keep that.

Add TownPerson migration requirements.

## TownPerson migration rule

Do not migrate `townPersonAmount` into the new economic NPC assignment.

Before disabling legacy TownPerson side-spawning:

1. the regional TownPerson population system must be operational;
2. existing loaded TownPersons must be accounted for;
3. Rails must have a desired city population;
4. the regional reconciler must safely adopt/reconcile current citizens;
5. legacy side-spawning can then be disabled.

The economic NPC post migration and ambient city-population migration are separate responsibilities.

---

# 18. Make the Player Payout Authority Explicit

The current map identifies the existing duplicate payout representation.

The target section should explicitly close it.

## New Trader payout rule

```text
city treasury debit
        |
        v
one authoritative player payout
```

Do not also credit `shard_user.currency`.

Physical coin items and/or the established banking/account representation are authoritative for new economic transactions.

The legacy JSON currency field may remain temporarily readable during migration, but new flows must not write the same payout into two economic representations.

---

# 19. Move Wine and Salvage into the Common Valuation Architecture

The current-state map correctly documents that wine/alcohol/salvage bypass the generic sale processor.

The target should explicitly state that this is temporary legacy architecture.

## Target

```text
common Player -> Trader transaction
        |
        +-- commodity market valuation
        +-- raw commodity valuation
        +-- processed commodity valuation
        +-- wine quality valuation
        +-- salvage material/quality valuation
        +-- recipe/product valuation
```

Legacy wine/salvage controller branches may delegate into the new strategies during migration.

After differential tests prove parity, retire the separate transaction branches.

---

# 20. Improve Transaction Direction Terminology

Avoid ambiguous phrases such as:

```text
buy transaction
sell transaction
```

because "buy" can mean either the player or NPC is buying.

Prefer:

```text
Player -> Trader
Vendor -> Player
Trader -> Player
```

or:

```text
NPC acquisition
Vendor retail
Trader commodity retail
```

Recommended invariant descriptions:

```text
Player -> Trader
    Trader acquires item/resource
    city treasury decreases
    city commodity/economic supply increases
    player receives payout

Vendor -> Player
    player pays Gold
    city treasury increases
    Product requirements/commodity supply decrease
    player receives NPC product
```

Use one convention consistently throughout future documents and APIs.

---

# 21. Clarify Catalog Snapshot vs Transaction Authority

The target map currently recommends cached catalogs refreshed by revision.

That is good, but the transaction boundary must remain authoritative.

A catalog snapshot may become stale while the player has a screen open.

Example:

```text
Catalog displays:
Valorite Sword

Another transaction consumes the remaining Valorite.

Player clicks Buy.

Rails revalidates:
Valorite requirements no longer satisfied.

Result:
reject/requote purchase
refresh catalog
next eligible material variant becomes visible
```

Do not require instantaneous UI synchronization to provide economic correctness.

The guarantees should be:

```text
catalog snapshot
    useful for display

purchase-time Rails validation
    authoritative for money, availability, material, stock, price, and treasury
```

This distinction should be explicit in the target boundary.

---

# 22. Recommended Compatibility Map Structure

The current map mixes repository facts, recommendations, open questions, and owner decisions in one "conclusions" section.

Refactor it to:

```text
1. Verified Current-State Architecture
   1.1 Legacy Trader
   1.2 Legacy Merchant
   1.3 Service NPC
   1.4 Current Rails Economy

2. Compatibility Comparison Table

3. Verified Current-State Conclusions
   facts supported directly by repository source

4. Owner-Approved Target Architecture
   decisions from the project specification / OPEN_QUESTIONS

5. Implementation Details to Resolve During Milestones
   exact class/table/API migration choices

6. Remaining Operational Open Questions
   only genuinely unresolved items
```

Every major statement should be internally classifiable as one of:

```text
FACT
OWNER DECISION
IMPLEMENTATION DETAIL
OPEN QUESTION
```

This prevents a later owner decision from making the forensic source analysis appear incorrect.

---

# 23. Specific Current Conclusions to Rewrite

## Current conclusion #4 / #5

### Current state

The map presents `ServiceNpcType` extension versus sibling economic types as unresolved.

### Replace with

```text
OWNER DECISION:
Use a generalized shared NPC type/role foundation with Service and Economic specializations.

IMPLEMENTATION DETAIL:
Milestone 3 determines the safest database/class migration while preserving WorldNpc,
spawn-post identity, and NpcSpawnAssignment.
```

---

## Current conclusion #11

### Current state

RunUO Vendor definitions are described primarily as economic type/catalog records.

### Replace/expand with

```text
RunUO Vendor class
    -> economic NPC/Vendor definition

RunUO player-buy catalog row
    -> Rails Product/default product definition
    -> Vendor/Product association

RunUO player-sell/buyback rule
    -> Trader buyback mapping

Pinned RunUO source
    -> completeness authority

Rails
    -> runtime/configuration authority
```

---

## Current conclusion #13

### Current state

Minecraft recipe authority/export remains proposed.

### Replace entirely

```text
Rails Product.requirements is authoritative for NPC production.

No vanilla/legacy Minecraft recipe is used for Vendor/Trader product production or valuation.

Minecraft/player recipes are a separate player-crafting system.

Default NPC Product requirements are seeded/imported into Rails and are shard-configurable.
```

---

## Current conclusion #15

### Current state

Quality is unresolved.

### Replace with

```text
NPC manufactured equipment uses Fine quality.

Exceptional remains the highest quality.

Existing blacksmithing quality representation must be bridged/migrated safely.
```

---

## Current conclusion #16

### Current state

Shard scoping is described as unresolved.

### Replace with

```text
Shard-specific policy overrides are required for both Service and Economic NPC types.

Global type policy may provide defaults.

Shard Admin owns the override configuration.
```

---

## Current conclusion #17

### Current state

Correctly documents admin-triggered reconciliation.

### Expand target

```text
Current:
admin-triggered only

Target:
economic changes enqueue reconciliation
+
periodic reconciliation safety job
+
hysteresis/stability policy
```

---

## Current conclusion #18

### Current state

Legacy spawn-post migration is good.

### Add

```text
TownPerson side-spawning is not migrated into the economic NPC post.

TownPersons move to the separate economy-driven city regional population subsystem.
```

---

# 24. Add These Target Architecture Invariants

The revised compatibility map should contain a concise invariant list.

```text
Rails Product.requirements is authoritative for NPC production.

Vanilla/legacy Minecraft recipes are never authoritative for NPC production.

RunUO prices anchor default/Iron finished-product retail value.

Current city material costs dynamically alter higher-material product prices.

Rails determines material eligibility and ranking.

The UI shows the highest-value currently producible material variant per base product slot.

NPC equipment is Fine quality, not Exceptional.

Vendor finished products are priced in Gold.

Rails owns all final economic prices.

CityCommodity.current_price is a market input, not necessarily the final payout/retail formula.

Every Player -> Trader payout debits the city treasury.

Every Vendor -> Player purchase credits the city treasury.

New transactions do not double-credit shard_user.currency and physical currency.

Commodity buy/sell/production permissions are explicit Rails policy.

Commodity raw/processed form is explicit Rails data.

Shard overrides apply to Service and Economic NPC policy.

Economic NPC reconciliation is asynchronous, Rails-authoritative, and stabilized.

TownPerson population is Rails economy-driven and Minecraft region-spawned.

Spawn-post UUID != WorldNpc UUID != assignment UUID != loaded entity UUID.

Catalog snapshots may be stale; purchase-time Rails validation is authoritative.
```

---

# 25. Final Recommendation

Do not replace the existing compatibility map wholesale.

The source-backed forensic analysis is useful and should be preserved.

Refactor only the target-design portions so that the map accurately distinguishes:

1. what the repository currently does;
2. what the owner has now decided;
3. what Codex may choose as an implementation detail;
4. what remains genuinely unresolved.

After these changes, `COMPATIBILITY_MAP.md`, `OPEN_QUESTIONS.md`, the milestone playbook, and the eventual `ECONOMY_RULES.md` will all describe the same architecture.
