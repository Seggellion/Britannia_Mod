# UltimaCraft Vendor, Trader, and City Economy Expansion — Claude Milestone Playbook

**Purpose:** Incrementally expand UltimaCraft's existing Merchant/Vendor and Trader systems so they can represent the RunUO vendor economy while using Rails as the authoritative city-economy and NPC control plane.

**Target stack:** Ruby on Rails 8 + PostgreSQL, NeoForge 1.21.1 + Java 21

**Minecraft integration baseline:** the current approved project integration branch, historically `patch-18-troubleshooting`. Claude must verify the actual current approved integration tip before creating worktrees or branches.

**Recommended feature name:** `vendor-trader-economy`

**Primary source inputs:**
- Current UltimaCraft Rails repository
- Current UltimaCraft NeoForge repository
- Existing Service NPC architecture and completed Service NPC milestones
- Existing Trader and Merchant implementations
- Existing city economy, treasury, commodity, recipe, item-quality, and transaction code
- Existing `runuo_vendor_catalog.json`
- Existing `runuo_vendor_reconstruction_design.md`
- Pinned RunUO source used to produce those artifacts

**Critical source warning:** `runuo_vendor_catalog.json` is useful as a normalized schema and verified source sample, but it must not automatically be assumed to contain every RunUO vendor/product row. Milestone 1 must perform an explicit source-coverage audit and create the complete in-project vendor/buyback matrix required by this feature.

**Planning rule:** One milestone at a time. One reviewable commit or small coherent commit series per repository per milestone. Claude must stop after each milestone and wait for review.

---

# 1. Product Vision

UltimaCraft should have two distinct but related economic NPC roles.

## 1.1 Vendors / Merchants

A Vendor or Merchant primarily sells finished or specialty goods to players.

Examples include occupations represented by RunUO vendors such as bakers, carpenters, jewelers, animal trainers, architects, tailors, mages, blacksmith-style merchants, and other specialty shops.

A specialty Vendor must not provide the generic "sell items to this NPC" service.

The Vendor's visible catalog is derived from:
- the NPC/vendor profession;
- the city;
- the shard;
- current Rails city-economy state;
- commodity sale eligibility;
- required recipe/input availability;
- shard-admin economic/spawn rules;
- treasury and other configured requirements.

A product must not appear merely because it exists in a static RunUO list.

## 1.2 Traders

A Trader is the city-facing acquisition and commodity-exchange layer.

Every product/type that an equivalent RunUO vendor would normally purchase from a player must be mapped to an UltimaCraft Trader purchasing service.

This means RunUO `SellInfo` / vendor-buyback behavior is not implemented on specialty Vendors. It is converted into Trader purchase capabilities.

Traders may support categories such as:
- raw resources;
- processed/refined resources;
- crafted goods;
- recipe-valued goods;
- weapons and metal goods;
- salvage;
- food/agricultural goods;
- fish;
- hides/leather;
- stone;
- ore and ingots;
- other economy-supported RunUO buyback categories.

Trader purchasing and selling must be driven by Rails market data and city treasury state rather than fixed client-side prices.

---

# 2. Non-Negotiable Architecture Rules

## 2.1 Rails is authoritative

Rails is the durable control plane for:
- shard identity;
- city identity;
- persistent NPC identity;
- NPC city/shard association;
- spawn-post registration;
- spawn assignment;
- active/inactive economic NPC definitions;
- vendor/trader profession definitions;
- shard-specific spawn requirements;
- commodity definitions;
- commodity quantities;
- commodity sale eligibility;
- commodity market prices;
- recipes or economy-recognized input requirements;
- city treasury balances;
- transaction authorization;
- transaction ledger/audit records;
- economy-side product availability;
- NPC activation decisions;
- bootstrap/synchronization definitions.

Minecraft owns:
- physical spawn blocks;
- loaded entity projections;
- pathfinding and AI;
- interaction range and screen state;
- item inspection;
- inventory mutation after authoritative approval;
- item construction;
- rendering;
- local crash-safe operation receipts where the existing architecture requires them.

Rails must not become a per-tick AI engine.

Minecraft must not become the authoritative market, treasury, NPC assignment, or spawn-rule engine.

## 2.2 Reuse the Service NPC control-plane model

Merchant and Trader spawning must converge on the same architectural principles already established for Service NPCs:

```text
physical spawn post
        |
        | stable post identity
        v
Rails mirrored spawn registration
        |
        | separate assignment
        v
persistent World NPC / economic NPC definition
        |
        | bootstrap / operation sync
        v
loaded Minecraft entity
```

A spawn-block UUID identifies a physical post.

It must not identify:
- the live entity;
- the World NPC;
- the current employee;
- a Rails transaction;
- a product catalog.

Do not preserve the legacy Trader/Merchant `sourceId` conflation as the long-term design.

## 2.3 Preserve existing working systems

Do not:
- break Quest Trader behavior;
- break Service NPCs;
- remove existing traders before parity exists;
- remove existing merchants before parity exists;
- perform unrelated refactors;
- rename unrelated files/classes;
- add duplicate API clients when an existing authenticated client can be extended;
- add per-tick Rails requests;
- trust client-supplied prices;
- trust client-supplied city identity;
- trust client-supplied commodity quantities;
- trust client-supplied treasury balances;
- hard-code shard-specific economy thresholds in Java;
- create a second persistent NPC identity system;
- create a second city/shard registry;
- create a second treasury implementation;
- create a second commodity pricing engine.

---

# 3. Required Economic Behavior

## 3.1 City association

Every economic NPC is associated with exactly one authoritative city while active.

Every product produced/sold by a Vendor or Trader must carry its origin city using the project's canonical custom-item/data-component mechanism.

Conceptually:

```text
originCityPublicId
originCityName
```

At minimum the player-visible data must include the city name.

Claude must inspect current custom-item data conventions before naming or implementing fields.

Origin data is set server-side from the authoritative NPC/city assignment.

A client must never be able to choose or overwrite the provenance used for a transaction.

## 3.2 Product availability

A product is eligible for a Vendor/Trader sale catalog only when every required condition passes.

At minimum:

```text
NPC definition active
AND NPC spawn requirements satisfied
AND NPC assigned to this city/post
AND product enabled for this shard/NPC
AND product/commodity policy allows sale
AND every required city commodity exists
AND every required commodity is eligible for use/sale
AND required quantity/state is sufficient for the product policy
AND treasury/economy requirements pass
```

If a recipe requires a commodity that does not exist in that city's economy, do not list the product.

Do not silently substitute a global/default commodity.

Do not list a product with a fake fallback price.

## 3.3 Commodity availability

The existing economy distinguishes raw and processed commodity forms. Preserve that.

Examples:

```text
ore/raw/iron
metal/ingots/iron
grain/raw/wheat
grain/processed/flour
```

The exact current project keys and fields must be discovered in Milestone 1 and reused.

## 3.4 Pricing authority

Rails calculates or authorizes the economic value used in a transaction.

Minecraft may display a synchronized quoted value but must not independently invent the authoritative price.

For recipe-derived products:

```text
materialMarketValue =
    SUM(requiredCommodityQuantity * currentCityCommodityPrice)
```

Additional existing merchant markup/production rules should be preserved if they are already implemented and still compatible with this design.

The first implementation must separate:
- commodity market value;
- Trader player-purchase price;
- Trader player-sale price;
- Merchant/Vendor retail price;
- any production/markup modifier;
- treasury impact.

## 3.5 Raw vs processed materials

The pricing policy must be explicit and Rails-configurable.

Required baseline intent:

- Raw materials may trade below full processed-market value.
- Processed/refined materials may trade at full current market value.
- Do not infer a processed item price from an unrelated raw commodity when a proper processed commodity exists.
- Shards may override applicable multipliers through Rails configuration.

Claude must inspect current FishTrader, SalvageTrader, OreTrader, and related pricing behavior before defining defaults.

## 3.6 Weapons and metal goods

Weapons and metal goods are valued from their actual recipe/material composition using current city material prices.

Material selection for NPC-produced metal goods should use the highest eligible material supported by the current city/economy rules when that is the intended product policy.

Item quality should be high, but not the highest possible quality tier.

Claude must inspect the existing UltimaCraft quality implementation and select the exact quality tier immediately below the maximum supported tier rather than inventing a new label.

Do not hard-code a quality name until the project enum/data model is known.

## 3.7 RunUO buyback conversion

For every RunUO vendor product/type that can normally be sold by a player to that RunUO NPC:

```text
RunUO vendor buyback capability
            |
            v
normalized UltimaCraft product/commodity mapping
            |
            v
one or more eligible UltimaCraft Trader definitions
            |
            v
Rails-authorized market-value purchase from player
```

Specialty Vendors do not expose that buyback directly.

The mapping must be exhaustive and testable.

## 3.8 Treasury flow

Every economic NPC transaction is tied to one city treasury.

Required ledger direction:

### NPC buys from player

```text
city treasury decreases
city commodity/inventory/economic supply increases
player receives currency/value
```

The transaction must fail atomically if the city cannot fund it.

### NPC sells to player

```text
player pays
city treasury increases
city commodity/input inventory decreases where production/stock policy requires it
player receives item
```

The exact currency item/account mutation path must reuse the existing treasury/currency transaction architecture.

No economic NPC should mint unlimited currency locally.

## 3.9 Shard-specific requirements

Every Vendor/Trader definition can have Rails-managed activation/spawn requirements.

Different shards may use different rules.

Example requirement:

```text
profession: magery_vendor
requirements:
  city alcohol supply > 100
  city treasury gold >= 20
```

The example is illustrative.

Rules must be configurable in Shard Admin, persisted in Rails, evaluated on Rails, and projected to Minecraft through the existing authoritative state/sync system.

Do not hard-code this example in Java.

---

# 4. Required Repository Documentation

Create and maintain a feature documentation folder, using current project conventions if an equivalent already exists:

```text
docs/vendor-trader-economy/
    PROJECT_FACTS.md
    COMPATIBILITY_MAP.md
    RUNUO_VENDOR_MATRIX.md
    ECONOMY_RULES.md
    API_CONTRACT.md
    MIGRATION_PLAN.md
    OPEN_QUESTIONS.md
    IMPLEMENTATION_LOG.md
```

Do not create duplicate docs if an existing project documentation structure already covers one of these responsibilities.

---

# 5. Git and Worktree Rules

Before modifying either repository, Claude must inspect:

```text
git branch --show-current
git status --short --branch
git rev-parse HEAD
git log --oneline --decorate -20
git worktree list
```

Also inspect the repository's `AGENTS.md`, root specifications, and feature documentation.

Rules:
- Determine the current approved integration branch from repository state and project docs.
- Historically the Minecraft integration branch has been `patch-18`, but do not assume that blindly.
- Create dedicated feature branches/worktrees only after the baseline is verified.
- Recommended feature name: `vendor-trader-economy`.
- Keep Rails and Minecraft changes in separate worktrees/repositories.
- Do not work directly in the user's main active checkout when another agent may be using it.
- Do not reset, rebase, force-push, delete branches, delete worktrees, clean untracked files, or discard unrelated user work.
- Do not use `git add .`.
- Stage only the files owned by the active milestone.
- Do not push or merge unless explicitly instructed.
- Stop after each milestone.

---

# 6. Milestone Sequence

---

## Milestone 0 — Safe Worktree and Baseline Establishment

### Objective

Create a safe feature workspace and prove the existing project baseline without implementing Vendor/Trader changes.

### Rails inspection

Identify:
- repository root;
- current branch/HEAD;
- integration branch;
- worktrees;
- dirty files;
- test command;
- database/test setup;
- current Service NPC milestone state.

### Minecraft inspection

Identify:
- repository root;
- current branch/HEAD;
- integration branch;
- worktrees;
- dirty files;
- Java/NeoForge versions;
- current Gradle baseline;
- current Service NPC milestone state.

### Required actions

1. Read project instructions.
2. Locate the RunUO design/catalog artifacts.
3. Create feature worktree(s) only if repository state is safe.
4. Record exact baseline HEADs.
5. Run current focused Service NPC tests.
6. Run current Trader/Merchant tests.
7. Run current full build/test commands appropriate to each repo.
8. Record pre-existing failures separately.

### Deliverables

Update:

```text
PROJECT_FACTS.md
IMPLEMENTATION_LOG.md
```

### No implementation

Do not change production behavior.

### Acceptance criteria

- Baseline is known.
- User work is protected.
- Feature worktrees/branches are safe.
- Existing failures are documented.
- No Vendor/Trader production code changed.

### Stop condition

Commit documentation only if appropriate, report, and stop.

---

## Milestone 1 — Forensic Compatibility Map: Trader vs Merchant vs Service NPC

### Objective

Understand exactly how the current systems work before designing replacements.

This is the most important analysis milestone.

Do not implement the new economy system in this milestone.

### Minecraft systems to inspect

Inspect every compiled implementation related to:

#### Trader hierarchy
- `CitizenEntity`
- `AbstractTraderEntity`
- `EntityWoodMerchant`
- `FishTraderEntity`
- `FurLeatherTraderEntity`
- `GrainTraderEntity`
- `MeatTraderEntity`
- `OreTraderEntity`
- `ProduceTraderEntity`
- `SalvageTraderEntity`
- `StoneTraderEntity`
- any newer Trader classes

#### Merchant hierarchy
- `AbstractEconomyMerchantEntity`
- `BakerEntity`
- `CostermongerEntity`
- `TavernkeeperEntity`
- `ArchitectEntity`
- any newer Merchant/Vendor classes

#### Spawn systems
- `TraderSpawnBlock`
- `TraderSpawnBlockEntity`
- Trader spawn UI
- Trader spawn packets
- Trader spawn reconciliation
- `MerchantSpawnBlock`
- `MerchantSpawnBlockEntity`
- Merchant spawn UI
- Merchant packets
- Merchant reconciliation
- `QuestGiverSpawnBlock`
- `QuestGiverSpawnBlockEntity`
- current Service NPC Spawn Block
- current Service NPC operation/synchronization code
- generic Britannia spawn blocks

#### Identity
Document:
- `sourceId`;
- UUID generation;
- NBT persistence;
- entity UUID;
- World NPC public ID;
- post UUID;
- city identity;
- Rails NPC IDs;
- assignment IDs;
- heartbeat identity.

Identify every place where legacy Merchant/Trader code conflates these concepts.

#### Interaction
Inspect:
- open-screen path;
- C2S/S2C payloads;
- permissions;
- range validation;
- dimension validation;
- city resolution;
- item identification;
- price calculation;
- buy/sell flows;
- inventory mutation;
- duplicate/retry handling.

#### Item metadata
Inspect:
- item quality;
- material identity;
- crafter/provenance;
- custom names;
- custom data components;
- stack compatibility;
- canonical serialization.

#### Recipes
Inspect:
- recipe registries;
- custom recipes;
- dynamic recipes;
- material inputs;
- metal tiers;
- quality creation hooks.

### Rails systems to inspect

Document exact models/services/controllers/jobs for:

- `Shard`
- `City`
- `CityCommodity`
- treasury model/service
- commodity pricing
- commodity quantity mutation
- economy snapshots/jobs
- transaction/ledger/audit models
- idempotency conventions
- authenticated Minecraft-server APIs
- `WorldNpc`
- Service NPC type/registry
- spawn-point records
- assignments
- bootstrap/world synchronization
- admin interfaces
- current NPC/trader persistence
- existing merchant/trader APIs
- recipe/product definitions, if any
- shard settings
- staffing/population rules, if any

### Required comparative output

Produce a table for:

| Concern | Legacy Trader | Legacy Merchant | Service NPC | Target Economic NPC |
|---|---|---|---|---|
| physical post identity | | | | |
| persistent NPC identity | | | | |
| Rails assignment | | | | |
| city identity | | | | |
| client config | | | | |
| server validation | | | | |
| Rails registration | | | | |
| chunk reload | | | | |
| duplicate repair | | | | |
| economic authority | | | | |
| transaction idempotency | | | | |
| admin configuration | | | | |

### Known legacy risks to verify

Historical inspection found that Trader/Merchant `sourceId` was generated in block-entity construction and used for multiple identity responsibilities. Confirm whether that remains true.

Historical inspection also found direct configuration-screen packet flows and weaker validation than Service NPC spawn infrastructure. Confirm current state.

Do not assume those findings remain current.

### Deliverables

Create/update:

```text
COMPATIBILITY_MAP.md
PROJECT_FACTS.md
OPEN_QUESTIONS.md
IMPLEMENTATION_LOG.md
```

### Acceptance criteria

Claude can explain, with exact paths:
- how a Trader spawns;
- how a Merchant spawns;
- how a Service NPC spawns;
- how each is represented in Rails;
- where city identity comes from;
- how prices are computed;
- how treasury value moves;
- how entity persistence works;
- which Service NPC components can be reused;
- which legacy components must be migrated rather than copied.

### Stop condition

No production implementation. Commit the compatibility map and stop.

---

## Milestone 2 — RunUO Coverage Audit and UltimaCraft Vendor/Trader Matrix

### Objective

Produce the exhaustive data model that maps RunUO economic behavior to UltimaCraft before introducing new NPCs.

### Required source audit

Read:
- `runuo_vendor_reconstruction_design.md`;
- `runuo_vendor_catalog.json`;
- the pinned RunUO source used by those artifacts.

Do not assume the current normalized JSON is exhaustive.

Enumerate:
- all RunUO NPC vendor classes;
- every `SBInfo` catalog used by them;
- every player-buy offer;
- every player-to-vendor buyback rule;
- conditions/expansion variants;
- duplicate rows;
- service-only NPC behavior.

### Build the target matrix

For every RunUO vendor profession, record:

```text
runuo_vendor_key
runuo_class
ultima_vendor_key
ultima_merchant_entity_strategy
merchant_spawn_type
city_required
vendor_can_buy_from_player = false
runuo_buy_catalog_mappings
runuo_buyback_types
target_trader_keys
economy_category
recipe/material mapping status
commodity prerequisites
quality policy
material-selection policy
implementation status
```

For every RunUO buyback type, record:

```text
runuo_type
ultima_item_or_tag
target_trader
valuation_strategy
commodity_key(s)
recipe_key
raw_or_processed
city_treasury_required
mapping_status
```

### Mapping rules

Classify every row as one of:

```text
DIRECT_ITEM
ITEM_TAG
COMMODITY
RECIPE_DERIVED
MOBILE_OR_SERVICE
UNIMPLEMENTED_ITEM
UNSUPPORTED_BY_DESIGN
REQUIRES_NEW_ASSET
REQUIRES_ECONOMY_COMMODITY
```

No row may disappear silently.

### Deliverables

Create/update:

```text
RUNUO_VENDOR_MATRIX.md
ECONOMY_RULES.md
OPEN_QUESTIONS.md
```

Also create a machine-readable project-owned mapping file in the format already preferred by the repositories.

### Acceptance criteria

- Every RunUO vendor is accounted for.
- Every RunUO player-sell/buyback type is accounted for.
- Every mapped buyback has a Trader destination or explicit unresolved status.
- Missing UltimaCraft items/commodities are visible.
- No implementation yet depends on guessed item names.

### Stop condition

Data/mapping only. Stop for review.

---

## Milestone 3 — Economic NPC Domain Contract in Rails

### Objective

Create the Rails-side definition model needed to describe Vendors, Traders, catalogs, shard-specific activation requirements, and spawn eligibility.

### First inspect

Prefer extending current:
- Service NPC registry concepts;
- World NPC profession/type concepts;
- spawn-point definitions;
- shard settings;
- economy/staffing rules;
- admin conventions.

Do not create duplicate registries if the current architecture can express an economic NPC subtype safely.

### Required domain capabilities

Rails must be able to answer:

```text
Which economic NPC types exist?
Which are Vendors?
Which are Traders?
Which shard enables each type?
Which city can currently support each type?
What requirements must pass?
What catalog/policy does it use?
Which spawn posts may host it?
```

### Requirement model

Support data-driven requirements such as:
- commodity exists;
- commodity quantity > threshold;
- treasury currency/balance >= threshold;
- commodity is saleable;
- city attribute/status;
- shard feature flag.

Prefer a constrained typed rule model over arbitrary executable Ruby or arbitrary client expressions.

### Example

The system must be capable of representing:

```text
magery vendor
alcohol supply > 100
treasury gold >= 20
```

without Java changes.

### Database requirements

- stable public IDs/keys;
- shard scoping or shard overrides;
- uniqueness constraints;
- active/inactive state;
- revisions;
- auditability;
- no hard-coded Java-only threshold source.

### Deliverables

- migrations;
- models/services;
- unit tests;
- admin-neutral domain API;
- documented schema.

### Acceptance criteria

Rails can evaluate whether a given Vendor/Trader type is economically eligible in a given city/shard using persisted rules.

No Minecraft spawning changes yet.

---

## Milestone 4 — Shard Admin Management for Economic NPC Rules

### Objective

Make Vendor/Trader definitions and requirements manageable through Shard Admin.

### Required admin capabilities

Operators can:
- enable/disable a Vendor/Trader type by shard;
- inspect city eligibility;
- configure requirement thresholds;
- configure treasury requirements;
- configure commodity requirements;
- configure raw/processed pricing multipliers where applicable;
- inspect why an NPC is currently ineligible;
- preview affected cities;
- audit changes.

### Guardrails

- typed fields;
- no arbitrary code evaluation;
- permission checks;
- validation against real shard/city/commodity keys;
- clear revision/audit history;
- safe defaults.

### Acceptance criteria

The example magery condition can be configured entirely from Rails Admin and evaluated by the same service used outside Admin.

---

## Milestone 5 — Merchant and Trader Spawn Posts Converge on Service NPC Identity Rules

### Objective

Refactor the physical Merchant/Trader spawn infrastructure so it follows the proven Service NPC post/assignment lifecycle without yet replacing every entity/catalog.

### Key invariant

```text
post UUID != World NPC UUID != loaded entity UUID
```

### Minecraft work

For Merchant and Trader posts:
- stable server-generated physical-post UUID;
- clone-safe UUID handling;
- no client-side identity generation;
- authoritative city selection by stable Rails city identifier;
- enabled/disabled state;
- configuration revision;
- registration state;
- assignment state;
- error state;
- safe chunk unload;
- safe block break/removal;
- duplicate post repair;
- no per-tick Rails calls.

Reuse Service NPC helpers where structurally appropriate.

### Rails work

Extend the existing spawn registration/assignment protocol if possible so economic posts use the same lifecycle and authentication.

Do not create an unrelated registration protocol unless Milestone 1 proved reuse impossible.

### Migration compatibility

Existing legacy Trader/Merchant spawn blocks must continue to function until a later explicit migration milestone.

### Acceptance criteria

One test Merchant post and one test Trader post:
- register with Rails;
- retain stable identity;
- receive assignment/state;
- survive restart;
- recreate the correct entity without identity duplication.

---

## Milestone 6 — Economic NPC Bootstrap and Assignment Projection

### Objective

Use Rails authoritative state to decide which Vendor/Trader should be materialized at a registered post.

### Rails

Publish, using the existing versioned bootstrap/operation mechanism:
- active economic NPC type definitions;
- eligible posts;
- assignments;
- World NPC identity;
- city public ID/name;
- profession/type key;
- active catalog/policy revision;
- economic eligibility revision;
- enough display metadata for Minecraft.

### Minecraft

- maintain immutable/cache-safe definitions;
- reconcile assignments;
- create exactly one loaded entity for the assignment;
- remove stale/wrong assignment projections;
- preserve permanent name/gender/appearance;
- avoid force-loading chunks.

### Critical rule

Minecraft does not independently decide "city has 101 alcohol, therefore spawn mage."

Rails decides eligibility and assignment.

### Acceptance criteria

Changing a Rails requirement and refreshing authoritative state can deactivate/reactivate the economic NPC without changing Java code.

---

## Milestone 7 — Generic Vendor/Merchant Definition and Sell-to-Player Catalog

### Objective

Generalize the Merchant side so RunUO vendor professions can be represented without creating one hard-coded transaction engine per NPC.

### Required behavior

- Vendor is city-associated.
- Vendor sells to player.
- Vendor cannot buy items from player.
- Vendor title/profession comes from authoritative definition.
- Vendor catalog is Rails/economy driven.
- Product list excludes economically invalid rows.

### Preserve rendering/entity style

Reuse the current Citizen/entity/rendering hierarchy unless Milestone 1 proves a necessary refactor.

Avoid a mass entity-class rewrite in this milestone.

### Catalog definition

A product should resolve to:
- UltimaCraft item type;
- quantity/unit;
- production recipe/economic inputs;
- current retail valuation;
- quality policy;
- material policy;
- city provenance;
- availability reason.

### Acceptance criteria

Implement one vertical-slice Vendor, preferably an already-existing Merchant such as Baker, through the new generic catalog path while preserving its current expected behavior.

---

## Milestone 8 — Commodity and Recipe Eligibility Engine

### Objective

Create one authoritative Rails service that determines if a product may be listed and what resource-limited stock is available.

### Inputs

- city;
- shard;
- product definition;
- recipe;
- commodity rows;
- commodity quantities;
- commodity saleability flags/policy;
- NPC activation policy.

### Mandatory listing rule

If a required commodity row does not exist, the product is not listed.

### Stock rule

Do not create arbitrary infinite stock.

Where product stock represents city production, calculate available units from the limiting required input.

Conceptually:

```text
availableUnits =
    MIN(
        floor(commodityQuantity[i] / recipeQuantity[i])
    )
```

Then apply any current project cap/policy.

### Pricing

Calculate material cost from the current city market price of each recipe input.

Preserve any existing production markup if currently authoritative.

### Acceptance criteria

A product disappears when a required commodity is absent and reappears when the authoritative commodity state supports it.

---

## Milestone 9 — City Provenance on Vendor/Trader Products

### Objective

Attach authoritative origin-city information to every NPC-sold product.

### Required inspection

Before implementation determine:
- current custom DataComponent model;
- stack-equivalence behavior;
- lore/tooltip conventions;
- item serializer;
- bank serialization;
- crafting behavior;
- drop/pickup behavior.

### Requirements

When a Vendor/Trader creates/transfers a product:
- source city comes from the authoritative NPC assignment;
- city name is visible to the player;
- stable city ID is retained if appropriate;
- provenance survives save/load and serialization;
- client cannot forge transaction provenance.

### Stacking decision

Items with different city provenance must not silently merge if merging would destroy the required origin information.

Implement using the project's canonical item-data semantics.

### Acceptance criteria

Same item sold in Britain and Minoc preserves distinct origin data across restart and inventory operations.

---

## Milestone 10 — Trader Player-Sell Transaction Foundation

### Objective

Create a generic Rails-authorized transaction for a player selling an item to a Trader.

### Flow

```text
player selects item
    |
Minecraft inspects canonical server-side item
    |
server resolves trader + city + item mapping
    |
server requests authoritative quote/prepare operation
    |
Rails validates:
    trader assignment
    city
    mapping
    market value
    treasury balance
    commodity policy
    shard rules
    idempotency
    |
Minecraft removes/transfers item using existing safe operation pattern
    |
Rails commits:
    treasury deduction
    commodity/economy increase
    ledger/audit
    |
player receives value
```

Use the existing prepare/confirm/cancel or operation-receipt architecture if already established for cross-system inventory/value transactions.

### Requirements

- no client price;
- no client treasury balance;
- idempotency;
- atomic treasury effect;
- duplicate retry safety;
- authenticated server;
- item fingerprint/contract;
- amount bounds;
- rollback/reconciliation.

### Acceptance criteria

One existing Trader category can purchase one existing commodity item from a player and the city treasury/economy changes exactly once.

---

## Milestone 11 — Full RunUO Buyback-to-Trader Coverage

### Objective

Implement the exhaustive mapping produced in Milestone 2.

For every RunUO vendor buyback rule:
- identify the UltimaCraft item/commodity/tag;
- identify the Trader category;
- define valuation strategy;
- add tests;
- expose unresolved mappings as explicit failures/coverage gaps.

### Coverage test

Add a machine-readable completeness test:

```text
every active RunUO buyback mapping
must have
  target trader
OR
  explicit unsupported/unimplemented status with reason
```

No accidental omissions.

### Specialty Vendor rule

Also add negative tests proving specialty Vendors reject generic player-sell requests.

### Acceptance criteria

The RunUO-to-UltimaCraft matrix reports 100% accounted-for buyback rows.

---

## Milestone 12 — Raw and Processed Material Trader Pricing

### Objective

Implement explicit market-value policy for raw and processed commodities.

### Required behavior

- raw material purchase/sale policy can apply a shard-configured discount/multiplier;
- processed/refined material can use full current market value;
- pricing always uses the city's authoritative commodity row;
- raw and processed commodity identities remain distinct;
- treasury is checked before NPC purchase;
- city inventory/supply is updated after successful purchase.

### Preserve current traders

Use FishTrader, SalvageTrader, OreTrader, StoneTrader, FurLeatherTrader, GrainTrader, ProduceTrader, and MeatTrader behavior as compatibility fixtures.

### Acceptance criteria

A raw input and its processed equivalent can have different policy while sharing the same authoritative city market system.

---

## Milestone 13 — Recipe-Valued Weapons and Metal Goods

### Objective

Implement Trader valuation and NPC production/sale policy for recipe-derived metal goods.

### Valuation

Determine the exact canonical recipe and its material requirements.

Conceptually:

```text
weaponMaterialValue =
    Σ(currentCityCommodityPrice(material) * recipeAmount)
```

Then apply the appropriate Trader or retail policy.

### Material policy

For NPC-produced metal goods:
- resolve the highest eligible material from the current city/economy policy;
- do not invent material tiers;
- fail closed if the required commodity/material is unavailable.

### Quality policy

- inspect the current quality system;
- use the tier immediately below the highest possible tier;
- preserve existing item stats/quality generation rules;
- do not set "maximum/perfect" quality.

### Tests

Cover:
- different city material prices;
- unavailable material;
- recipe changes;
- quality metadata;
- city provenance;
- treasury flow;
- restart serialization.

### Acceptance criteria

Weapon/metal values change when the underlying city material market changes, without Java price constants.

---

## Milestone 14 — Vendor Retail Transactions and City Production Consumption

### Objective

Finish the authoritative sell-to-player path for Vendors/Traders.

### Flow

```text
player selects product
Rails-backed catalog quote
player payment validated
city production inputs reserved/consumed
city treasury credited
Minecraft constructs product with:
    material
    quality
    city provenance
transaction commits
```

Use existing currency/transaction safeguards.

### Requirements

- no product if required commodity is absent;
- no sale if supply cannot satisfy production policy;
- no duplicate production on retry;
- no duplicate treasury credit;
- product receives authoritative city provenance;
- catalog refreshes after meaningful economy changes.

### Acceptance criteria

At least one recipe-derived Vendor product and one Trader-sold commodity complete an end-to-end treasury/economy transaction.

---

## Milestone 15 — Rails-Driven Economic NPC Activation and Population

### Objective

Make Vendor/Trader presence react to city economics and shard-admin requirements.

### Rails evaluator

For each city/type:

```text
evaluate all configured requirements
produce:
  eligible true/false
  machine-readable reasons
  revision
```

### Assignment/population

Reuse the existing persistent NPC staffing/assignment architecture.

Economic changes may cause:
- unfilled post;
- assignment activation;
- reassignment;
- deactivation;
- surplus NPC displacement;

but must not casually delete persistent World NPC identity.

### Example test

Configure one shard so a magery NPC requires:

```text
alcohol > 100
treasury gold >= 20
```

Verify:
- below threshold -> not active;
- crosses threshold -> eligible;
- falls below -> deactivates according to defined staffing policy;
- another shard can use different thresholds.

### Acceptance criteria

No Java deployment is required to change economic spawn requirements.

---

## Milestone 16 — Migrate Legacy MerchantSpawnBlock and TraderSpawnBlock

### Objective

Move existing posts and behavior onto the authoritative architecture without destroying existing worlds.

### Requirements

- detect legacy block entities;
- preserve configured city/type where valid;
- issue stable post UUIDs safely;
- register with Rails;
- create assignments;
- avoid duplicated NPCs;
- preserve old worlds;
- preserve item/block IDs unless a deliberate migration is proven safe;
- maintain rollback documentation.

### Deprecated code

Only remove or bypass old `sourceId`/heartbeat/reconciliation behavior after parity tests prove the new path.

### Acceptance criteria

A world containing old Trader/Merchant spawn posts loads without losing NPC configuration and converges to the new authoritative registration/assignment model.

---

## Milestone 17 — Full RunUO Vendor Rollout

### Objective

Create/enable the full set of required RunUO-derived Vendor professions and their corresponding Trader buyback services.

### Work

For each matrix row:
- implement or map entity presentation;
- register profession;
- map catalog;
- map required commodities/recipes;
- map Trader buyback;
- seed/admin-configure shard rules;
- test city conditions;
- test treasury flows;
- test provenance;
- test quality/material policies.

### Rollout discipline

Do not ship every profession in one unreviewable commit.

Implement in small profession families, for example:
- food/agriculture;
- wood/carpentry;
- textiles/leather;
- mining/metal;
- jewelry;
- magical/specialty;
- service-adjacent vendors;
- remaining RunUO professions.

Each family can be a sub-milestone/commit series under Milestone 17.

### Acceptance criteria

The project matrix reports every required RunUO Vendor as implemented or explicitly excluded by approved design.

---

## Milestone 18 — Observability, Admin Diagnostics, and Hardening

### Objective

Make the economic NPC system operable without direct database edits.

### Rails diagnostics

Expose:
- active/inactive NPC types;
- requirement failures;
- city eligibility;
- post registration;
- assignment;
- catalog revision;
- commodity prerequisite failures;
- missing recipes;
- treasury rejection reason;
- transaction history;
- idempotency keys;
- reconciliation-required operations.

### Minecraft diagnostics

Provide safe operator diagnostics for:
- post UUID;
- assigned World NPC;
- city;
- type;
- registry revision;
- catalog revision;
- last authoritative sync;
- pending operation count;
- duplicate entity detection.

### Performance

Verify:
- no Rails request per tick;
- no product-by-product network chatter when a catalog snapshot can be used;
- bounded payloads;
- pagination where appropriate;
- cache invalidation by revision;
- safe retry/backoff.

### Acceptance criteria

Operators can explain why a specific Vendor is absent, why a product is absent, why a Trader rejected an item, or why a treasury transaction failed without modifying the database manually.

---

## Milestone 19 — Final Differential and Economy Validation

### Objective

Prove the completed system against both the RunUO coverage matrix and UltimaCraft economic invariants.

### Required validation suites

#### RunUO coverage

- all expected Vendor professions accounted for;
- all RunUO buyback rows accounted for;
- every supported buyback maps to a Trader.

#### Vendor behavior

- cannot buy from player;
- city provenance;
- product gating;
- commodity prerequisites;
- quality/material policy;
- retail treasury credit.

#### Trader behavior

- buys eligible items;
- market-value calculation;
- raw/processed distinction;
- recipe-derived valuation;
- treasury funding;
- city economy increase;
- idempotency.

#### Spawn behavior

- stable post identity;
- persistent NPC identity;
- authoritative city;
- assignment;
- chunk unload/reload;
- restart;
- duplicate repair;
- Rails outage/recovery.

#### Shard rules

- different shards can use different requirements;
- Admin changes take effect through authoritative synchronization;
- no Java constants required.

### Final deliverables

Complete:

```text
PROJECT_FACTS.md
COMPATIBILITY_MAP.md
RUNUO_VENDOR_MATRIX.md
ECONOMY_RULES.md
API_CONTRACT.md
MIGRATION_PLAN.md
OPEN_QUESTIONS.md
IMPLEMENTATION_LOG.md
```

`OPEN_QUESTIONS.md` must contain no unresolved blocker silently treated as implemented.

---

# 7. Required Data Model Concepts

Claude must discover the existing project equivalents before creating names, but the completed architecture needs to represent these concepts.

## 7.1 Economic NPC type

```text
stable key/public id
kind: vendor | trader
display label
profession
active
spawnable
definition revision
catalog/policy reference
```

## 7.2 Shard economic NPC policy

```text
shard
economic npc type
enabled
requirements
pricing policy overrides
raw multiplier
processed multiplier
other approved economy parameters
revision
```

## 7.3 Spawn requirement

Typed examples:

```text
commodity_exists
commodity_quantity_gt
commodity_quantity_gte
commodity_saleable
treasury_balance_gt
treasury_balance_gte
city_attribute
feature_enabled
```

## 7.4 Product definition/mapping

```text
product key
UltimaCraft item identity
RunUO source metadata
vendor profession(s)
target trader(s)
recipe
required commodities
sale policy
quality policy
material policy
city provenance required
active
```

## 7.5 Trader buyback mapping

```text
accepted item/type/tag
target trader
commodity output
valuation strategy
raw/processed classification
recipe-derived flag
treasury required
limits
active
```

## 7.6 Transaction

Reuse the existing project transaction/operation model where possible.

The conceptual record must support:

```text
public operation id
idempotency key
shard
city
World NPC
spawn post
player
direction
item contract
quantity
quoted unit value
quoted total
treasury delta
commodity delta
status
prepare/confirm/cancel state
timestamps
audit metadata
```

---

# 8. Pricing Strategy Registry

Avoid giant profession-specific `if` chains.

After Milestone 1 confirms current conventions, use a closed strategy registry or equivalent.

Required conceptual strategies:

```text
CURRENT_COMMODITY_VALUE
RAW_COMMODITY_VALUE
PROCESSED_COMMODITY_VALUE
RECIPE_MATERIAL_VALUE
MERCHANT_RETAIL_RECIPE_VALUE
SALVAGE_VALUE
EXPLICIT_SERVICE_PRICE
UNSUPPORTED
```

The exact names should follow project conventions.

The client/server request must not select an arbitrary strategy. The strategy comes from Rails-owned product/trader configuration.

---

# 9. Product Availability Decision

A single Rails service should be capable of explaining a decision.

Example result:

```json
{
  "available": false,
  "reasons": [
    {
      "code": "missing_required_commodity",
      "commodity_key": "metal|ingots|valorite"
    }
  ]
}
```

or:

```json
{
  "available": true,
  "available_units": 6,
  "unit_price": 145.25,
  "catalog_revision": 42
}
```

Do not require the Minecraft client to reverse-engineer why an item is unavailable.

---

# 10. Spawn Eligibility Decision

Likewise, Rails should be able to explain NPC activation.

Conceptual result:

```json
{
  "eligible": false,
  "reasons": [
    {
      "code": "commodity_threshold_not_met",
      "commodity_key": "alcohol",
      "required": 100,
      "actual": 83
    },
    {
      "code": "treasury_threshold_not_met",
      "currency": "gold",
      "required": 20,
      "actual": 12
    }
  ],
  "revision": 17
}
```

Exact API shape must follow existing conventions.

---

# 11. Claude Completion Report Required After Every Milestone

Claude must report:

```text
Milestone:
Starting Rails branch/HEAD:
Starting Minecraft branch/HEAD:

Files inspected:
Files changed:
Migrations:
API changes:
Minecraft changes:
Admin changes:

Tests actually run:
Exact commands:
Exact results:

Manual validation actually performed:
Manual validation not performed:

Compatibility notes:
Security/idempotency notes:
Economy/treasury notes:
RunUO coverage impact:

Known limitations:
Open questions:

Commit(s):
Suggested commit subject:
Working tree state:

Next milestone:
STOPPED: yes
```

Never report an unperformed manual test as passed.

---

# 12. Initial Claude Prompt

Use this to begin the project.

```text
You are implementing Milestones 0 and 1 of the UltimaCraft Vendor, Trader, and City Economy Expansion.

Do not implement the new Vendor/Trader economy yet.

Your first job is repository discovery, safety setup, baseline verification, and a forensic compatibility map comparing the existing Trader, Merchant, and Service NPC systems.

Read the complete playbook:
UltimaCraft_Vendor_Trader_Economy_Claude_Playbook.md

Also locate and read:
runuo_vendor_reconstruction_design.md
runuo_vendor_catalog.json
the current Service NPC architecture/milestone documentation
all repository AGENTS.md or equivalent instructions

PROJECT INTENT

UltimaCraft must expand its current Vendor/Merchant and Trader systems to represent all required RunUO vendor professions.

Specialty Vendors sell products to players but do not provide generic player-to-NPC item selling.

For every item/product category that a RunUO vendor would normally purchase from a player, UltimaCraft must provide that purchasing capability through an appropriate Trader.

All economic NPCs are city-associated.

Rails is authoritative for shard, city, persistent NPC identity, spawn-post registration, assignment, commodity state, prices, treasury, economic requirements, product eligibility, and transaction authorization.

MerchantSpawnBlocks and TraderSpawnBlocks must ultimately follow the same persistent-post/assignment principles used by Service NPCs.

Every NPC-sold product must carry authoritative city provenance so the player can see which city it came from.

Products must not be listed when required city commodities/materials do not exist or are not eligible for sale/use.

Trader pricing must use current Rails market values. Raw and processed resources must be distinguishable. Processed materials may use full market value while raw-material policy may be lower/configurable.

Recipe-derived weapons and metal goods must be valued from their current city material prices. NPC-produced metal goods use the highest eligible material according to the city/economy policy and use a high quality tier that is one tier below the project's highest possible quality.

All NPC economic value flows through the city treasury. NPC purchases from players must be treasury-funded. Player purchases must credit the city treasury under the authoritative transaction model.

Vendor/Trader spawn requirements are Rails-defined, shard-specific, editable in Shard Admin, and evaluated on Rails. As an example, one shard could require a magery economic NPC to have alcohol supply greater than 100 and treasury gold of at least 20. Do not hard-code this example.

MILESTONE 0

1. Inspect both Rails and Minecraft repositories.
2. Report branch, HEAD, status, worktrees, integration baseline, and repository instructions.
3. Identify unrelated work that must not be touched.
4. Determine whether a vendor-trader-economy branch/worktree already exists.
5. If safe and required, create dedicated worktrees from the current approved integration tips. Do not assume a stale branch.
6. Run baseline focused Service NPC, Merchant, and Trader tests plus the appropriate full builds.
7. Record existing failures separately.
8. Create/update docs/vendor-trader-economy/PROJECT_FACTS.md and IMPLEMENTATION_LOG.md only.

Do not change production behavior.

MILESTONE 1

Perform an exact source-code comparison.

Minecraft:
- CitizenEntity hierarchy.
- AbstractTraderEntity and every Trader subclass.
- AbstractEconomyMerchantEntity and every Merchant subclass.
- ArchitectEntity and any merchant outside that hierarchy.
- TraderSpawnBlock and TraderSpawnBlockEntity.
- MerchantSpawnBlock and MerchantSpawnBlockEntity.
- configuration screens and packets.
- QuestGiverSpawnBlock for comparison.
- current Service NPC Spawn Block, UUID lifecycle, registration, assignment, bootstrap/operation client, and reconciliation.
- identity fields including sourceId, post UUID, World NPC public ID, entity UUID, Rails IDs, city identifiers.
- current API clients and authentication.
- item quality/material/custom-data components.
- recipes.
- currency handling.
- current Trader/Merchant transaction flow.

Rails:
- Shard.
- City.
- CityCommodity.
- commodity pricing and quantity mutation.
- treasury.
- transaction/ledger/audit services.
- idempotency.
- authenticated Minecraft APIs.
- WorldNpc.
- Service NPC registry/types.
- spawn points.
- assignments.
- bootstrap and operation synchronization.
- Shard Admin conventions.
- NPC/trader/merchant persistence.
- recipe/product definitions if any.
- population/staffing/economic-rule systems.

Produce docs/vendor-trader-economy/COMPATIBILITY_MAP.md containing:
- exact file/class/table paths;
- current behavior;
- current weaknesses;
- reusable Service NPC components;
- migration risks;
- a comparison table for legacy Trader vs legacy Merchant vs Service NPC vs target economic NPC;
- a proposed boundary for Rails and Minecraft;
- unanswered questions that require source evidence.

Historical findings to verify, not blindly assume:
- legacy Trader/Merchant sourceId may conflate physical post and live NPC identity;
- legacy Trader/Merchant configuration may use direct screen packets and free-text/hard-coded values;
- existing Service NPC post identity/assignment architecture is stricter and should be preferred;
- FishTrader and SalvageTrader may be useful transaction/pricing reference implementations.

Do not implement Milestone 2.

When the compatibility map is complete:
- run documentation checks;
- commit only milestone-owned files if safe;
- report exact commands/results;
- stop and wait for review.
```

---

# 13. Definition of Done

This program is complete only when:

- every required RunUO Vendor profession is accounted for;
- every RunUO player-to-vendor buyback category is mapped to an UltimaCraft Trader or explicitly approved as unsupported;
- specialty Vendors cannot buy generic items from players;
- all economic NPCs have authoritative city association;
- all NPC-sold items contain authoritative city provenance;
- no product is listed without valid city commodity/recipe prerequisites;
- Rails market prices drive commodity and recipe valuation;
- raw and processed resources have explicit policies;
- weapon/metal valuations derive from recipe materials;
- NPC-produced metal goods follow the approved material/quality policy;
- Trader purchases are funded from city treasury;
- player purchases credit city treasury;
- all economic transactions are idempotent and auditable;
- Merchant and Trader posts use stable physical-post identity separate from persistent NPC identity;
- Rails controls assignments and economic eligibility;
- shard-specific requirements are editable in Shard Admin;
- economic spawn thresholds require no Java changes;
- old worlds migrate safely;
- no per-tick Rails requests exist;
- the RunUO coverage matrix has no silent gaps;
- full Rails and Minecraft suites pass apart from explicitly documented unrelated pre-existing failures.
