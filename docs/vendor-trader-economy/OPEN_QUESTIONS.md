Vendor/Trader Economy — OPEN_QUESTIONS

This document records the owner-approved architecture decisions that resolve the questions raisedduring Milestones 0–1, followed by the small number of operational questions that remain genuinelyopen.

Resolved decisions are not implementation suggestions. Codex must treat them as projectrequirements unless later owner direction explicitly supersedes them.

Resolved Architecture Decisions

1. Economic NPC type architecture

Decision: use a generalized NPC type foundation with Service and Economic specializations.

Do not keep expanding ServiceNpcType until it becomes a catch-all model for unrelated NPC roles.

The target model should preserve one shared persistent NPC/spawn architecture while allowingrole-specific configuration:

general NPC type / role definition
    |
    +-- Service NPC specialization
    |     dialogue
    |     service actions
    |     guildmaster/skill configuration
    |
    +-- Economic NPC specialization
          kind: vendor | trader
          product/catalog policy
          valuation strategy
          economic eligibility

WorldNpc, persistent spawn-post identity, and NpcSpawnAssignment remain shared concepts.

The current ServiceNpcSpawnPoint foreign-key shape may therefore need to be generalized orreplaced by a common NPC spawn-post/type relationship. Do not create a second persistent NPCidentity system for Vendors or Traders.

Milestone 3 must choose the exact Rails table/class shape by extending current project conventions,but the architectural direction above is decided.

2. Shard scoping of requirements

Decision: shard-specific NPC policy overrides apply to both Service NPCs and Economic NPCs.

Global NPC-type requirements may remain defaults, but individual shards must be able to overrideactivation/staffing requirements.

The target pattern is conceptually:

global NPC type defaults
        |
        v
shard-specific NPC type policy
        |
        v
city eligibility evaluation

This applies to Service NPCs as well as Vendors/Traders so the project does not end up with twoincompatible staffing-rule systems.

Shard Admin must manage these overrides.

3. Treasury coupling direction

Decision: Trader payouts debit the city's circulating treasury balance represented bycoins_outstanding.

This is the inverse of economic flows that credit coins_outstanding, including Guildmastertraining revenue.

Required behavior:

Trader buys from player
    city treasury coins_outstanding decreases
    city economic/commodity supply increases
    player receives payment

Treasury debit must be atomic and must never reduce the balance below zero.

If the city cannot afford the entire transaction, reject the sale.

Do not partially purchase a player's submitted transaction by default.

Do not silently fall back to reserve.

Implement the debit through a locked/idempotent Rails transaction service rather than directunprotected arithmetic.

4. NPC product recipe authority

Decision: Rails Product is the authoritative source for NPC products and NPC productionrequirements.

The existing Rails products table already contains:

item_id
requirements JSONB
price

That model should be evolved into the authoritative NPC economic product definition instead ofcreating a second recipe authority.

Critical rule

Do not use vanilla Minecraft recipes, legacy Minecraft recipes, MerchantRecipes,CraftableRegistry, datapack crafting recipes, or any other Minecraft crafting recipe as therecipe authority for Vendor/Trader production or valuation.

Minecraft/player crafting and NPC economic production are separate systems.

For NPC products:

Rails Product
    |
    +-- item_id
    +-- requirements
    +-- baseline/default price
    +-- Vendor associations/policy
    +-- material-family policy where applicable
    +-- quality policy
    |
    v
city-economy availability + pricing

Product.requirements should be expanded beyond the current generic:

{
  "wood": 0,
  "metal": 0,
  "stone": 0
}

so that it can express real Rails commodity requirements.

The exact JSON schema should follow project conventions and be versionable, but conceptually itmust support:

fixed commodity requirements;

quantities;

variable material-family requirements such as metal;

future non-material economic requirements if needed.

Default NPC products/requirements must be generated from a project-owned Rails seed/import source.

RunUO provides historical/default Vendor data. Rails becomes the actual UltimaCraft game-designauthority.

Shard customization

Every shard must ultimately be able to override NPC production requirements without modifying Java.

Prefer global/default Product definitions plus shard-scoped overrides rather than blindlyduplicating the entire Product table for every shard.

The exact Rails representation is a Milestone 3 implementation detail, not an open owner decision.

5. Player payout representation

Decision: physical currency and the banking/account system are the authoritative player-valuerepresentations for new economic transactions.

The current Trader path must not continue double-representing one payout by both:

crediting shard_user.currency, and

granting physical coin items.

For the new Vendor/Trader economy:

stop crediting shard_user.currency for new Trader-sale flows;

pay through the established physical-coin/banking economic path;

retain legacy reads only as long as required for compatibility;

migrate/deprecate the old JSON ledger once no supported flow depends on it.

One transaction must create value in exactly one authoritative player representation.

6. NPC product quality

Decision: NPC-manufactured equipment uses the richer four-tier quality ladder and should beFine, not maximum Exceptional.

Canonical intent:

Crude
Basic
Fine          <- NPC-produced high-quality goods
Exceptional   <- maximum

The current blacksmithing 1|2 Normal|Exceptional representation must not force NPC goods to useNormal.

Milestone implementation must normalize or bridge the existing quality representations safelywithout corrupting existing item data.

Crop quality remains a separate system and does not define equipment quality.

7. Material eligibility, ordering, and fallback

Decision: material progression must be explicit data, not Java enum order, durability, or animplicit string sort.

Rails must know the intended UltimaCraft/UO economic progression for material families such asmetal.

For each base Product, Rails determines which material variants are currently producible from thecity's available commodities.

Iron is the normal baseline material.

Higher-value material variants become eligible when that material exists in sufficient city supplyfor the Product's Rails requirements.

Catalog behavior

All economically supported material variants may exist conceptually in the product/economy model,but the current interface does not need to show every material variation simultaneously.

For each base product slot, display the highest-value currently producible material variant.

Example:

Valorite product is producible
    -> display Valorite variant

Valorite becomes unavailable
    -> fall back to next-highest producible material

...

only Iron remains
    -> display Iron variant

This gives prosperous cities visibly better merchandise without flooding the interface with everymaterial variation.

Do not use UOMetalToolMaterial enum ordinal as the economic rank.

Create/import an explicit material rank/policy in Rails.

8. Commodity economic eligibility

Decision: commodity economic permissions must be explicit Rails data.

Do not permanently derive saleability from category/subcategory strings.

A single ambiguous saleable boolean is not sufficient because different directions may needdifferent policy.

The Rails design should support at least the equivalent of:

npc_buy_enabled
npc_sell_enabled
production_enabled

or an associated policy object that represents the same independent permissions.

This must remain compatible with shard-specific overrides where required.

9. Raw vs processed classification

Decision: commodities need explicit form/classification data.

Do not make long-term pricing policy depend on conventions such as:

raw
logs
whole
milled
ingot

Existing commodity records should be backfilled from current conventions and reviewed.

The target model should support an explicit classification such as:

raw
processed
finished
other

The final enum names may follow existing project terminology.

Shard pricing policies can then bind to explicit data, for example:

raw purchase multiplier
processed purchase multiplier

instead of inspecting commodity-name strings.

10. Economic NPC reconciliation

Decision: economic NPC eligibility is Rails-authoritative and reconciles asynchronously.

Do not run the complete staffing/population engine directly inside commodity or treasuryafter_commit callbacks.

Economic changes should enqueue or mark reconciliation work, while a periodic reconciliation jobprovides eventual-consistency recovery.

Vendor/Trader activation must also use hysteresis and/or minimum active/inactive durations so visibleNPCs do not oscillate around a threshold.

Conceptually:

activation threshold != immediate deactivation threshold

or an equivalent bounded stability policy.

Shard Admin should ultimately control the relevant thresholds/policies.

Minecraft receives the resulting authoritative assignment/activation state. It does not independentlycalculate whether a Vendor should exist.

11. Wine, alcohol, and salvage legacy branches

Decision: the new Trader valuation-strategy architecture absorbs the existing specializedvaluation behavior.

Do not permanently retain separate transaction systems for wine and salvage.

Preserve their existing valuation semantics by implementing dedicated closed strategies, forexample conceptually:

WineQualityValue
SalvageMaterialQualityValue

During migration, legacy controller paths may delegate into the new strategies so behavior remainscompatible.

After differential/parity tests prove the generic transaction path produces the same intendedresults, route those Trader transactions through the common processor and retire the legacybranches.

12. TownPerson economic population

Decision: TownPersons remain an important economic sink and visible expression of city wealth,but they no longer belong to individual Trader/Merchant spawn blocks.

Legacy townPersonAmount / shared-sourceId side-spawning should be migrated away.

The target architecture is:

Rails city economy
    |
    | food supply
    | alcohol supply
    | treasury/wealth
    | future economic inputs
    v
desired TownPerson population
    |
    v
Minecraft regional population manager
    |
    v
valid random spawn/despawn locations within the city region

Population behavior

TownPerson population should grow and decline nonlinearly with economic prosperity.

The desired population function should feel exponential during normal prosperity growth, but mustbe bounded by configured limits.

Do not use an unbounded exponential formula.

Use a capped nonlinear curve or equivalent model that provides:

very low population for poor cities;

gradual initial growth;

visibly faster growth for prosperous cities;

a hard maximum population cap.

Rails should own the desired population calculation and shard/city configuration.

At minimum the eventual policy should be able to represent:

minimum population
maximum population
food influence
alcohol influence
wealth/gold influence
growth-curve parameters
spawn/despawn rate limits

Minecraft spawning

TownPersons should spawn throughout the existing authoritative city Region rather than around aMerchantSpawnBlock or TraderSpawnBlock.

Candidate positions must be validated against current world conditions, including:

correct city region;

correct dimension;

loaded chunks only;

valid ground;

sufficient headroom;

no solid/liquid invalid placement;

reasonable player distance;

local TownPerson density/cap.

Do not force-load chunks merely to satisfy desired population.

Gradual reconciliation

Do not instantly spawn or despawn a large population difference.

Minecraft should gradually converge toward the Rails desired count, producing the visual effect ofpopulation growth, decline, and migration rather than mass appearance/disappearance.

Existing legacy TownPersons must be preserved during migration until the new regional populationsystem has parity.

13. RunUO catalog completeness

Decision: the pinned RunUO source is the completeness authority.

runuo_vendor_catalog.json is a normalized project input and may contain verified mappings, but itmust not be treated as proof that the entire RunUO Vendor/catalog set has been imported.

Milestone 2 must audit the pinned RunUO source at the approved commit and produce a project-ownedcomplete Vendor/buyback mapping.

RunUO data is used to create/default Rails Product and economic-NPC definitions.

Once imported/seeded, Rails is authoritative for UltimaCraft configuration.

The only remaining operational question for this item is the exact local location of the pinnedRunUO checkout, documented below.

14. Merchant price authority and migration

Decision: Rails is authoritative for all new Merchant/Vendor prices.

Do not trust submitted_line_price.

A compatibility window may preserve the old request field temporarily, but Rails must ignore thesubmitted value and calculate/return the authoritative price itself.

Once supported Minecraft deployments no longer require the deprecated field, remove it.

A private shard does not justify retaining client-authoritative economic pricing.

Product Pricing Clarification

The RunUO item price is a baseline price anchor for the Iron/default version of a product.

It is not the final fixed price for all material variants.

It is also not replaced by a simple raw-material-only calculation.

RunUO pricing already represents non-material economic value such as craftsmanship, labor, shopmargin, and game-balance value.

For a variable-material product, the target pricing concept is:

RunUO Iron baseline price
    +
difference between current selected-material cost
and the reference/current Iron material cost
    +
approved UltimaCraft economic/production adjustment

Conceptually:

baseline_material_cost =
    Product material quantity
    * reference/current Iron commodity value

selected_material_cost =
    Product material quantity
    * current selected-material city commodity value

material_delta =
    selected_material_cost - baseline_material_cost

final_retail_price =
    RunUO Iron baseline
    + material_delta
    + approved production/economy adjustment

The exact calibrated formula belongs in ECONOMY_RULES.md and must be tested against expected RunUOprice ranges.

Do not use a straight material-cost comparison as the entire retail price.

Do not use fixed Java price constants.

Vendor Currency Rule

Decision: city Vendors sell NPC products in Gold coins.

This is intentionally expensive and is part of the UltimaCraft economic design.

Product prices must reflect current city commodity/material costs and the RunUO baseline.

Player purchases from Vendors credit the authoritative city treasury.

Vendor retail is intended to be economically meaningful rather than a cheap substitute for playergathering and crafting.

Product Availability Clarification

A Rails Product is not automatically for sale merely because it exists.

The runtime catalog must be derived from:

Product active/configured
AND economic NPC active
AND shard policy allows it
AND all Product.requirements resolve to real city commodities
AND required commodities are production-enabled
AND sufficient city supply exists
AND material variant is eligible
AND treasury/economy policy permits the sale

If any required commodity does not exist in the city economy, the Product must not be listed.

Do not substitute a vanilla Minecraft ingredient.

Do not substitute a legacy Minecraft recipe.

Do not synthesize missing commodities client-side.

Remaining Operational Open Questions

Only the following items remain genuinely unresolved.

OQ-1. Rails test database ownership repair

All discovered ultimacraft_test* databases are currently owned by role ultimacraft, while thecanonical Codex test workflow expects the project's dedicated test role/environment.

The test environment must be repaired before Milestone 3+ Rails implementation work proceeds.

Required local/admin action is one of:

safely recreate the affected test databases under the expected test role; or

safely transfer ownership/privileges according to the project's established test setup.

Do not alter production database ownership.

Do not weaken AGENTS.md testing requirements to bypass this issue.

Once repaired, record:

database names
owner
test role
exact repair command
bin/codex_test result

in PROJECT_FACTS.md / IMPLEMENTATION_LOG.md.

This is an environment prerequisite, not an architecture question.

OQ-2. Local pinned RunUO source location

Milestone 2 needs the pinned RunUO source checkout used as the completeness authority.

Required source:

repository: runuo/runuo
commit: 71b2794f12eb6f948b1c5598ae8b350401a22d4d

Codex must determine whether that exact commit already exists locally.

If present:

record the absolute local path;

verify the repository remote;

verify git rev-parse HEAD or the inspected tree is exactly the pinned commit;

do not modify the checkout.

If absent:

obtain or create a read-only/reference checkout of exactly the pinned commit using the project'sapproved workflow;

keep it separate from the UltimaCraft implementation worktrees;

record its location and commit in PROJECT_FACTS.md.

Do not substitute the current RunUO default branch or another commit.

**RESOLVED 2026-08-10:** no local checkout existed; a dedicated read-only reference checkout was
created at `C:\projects\runuo-reference` (remote `https://github.com/runuo/runuo.git`), HEAD
verified as exactly `71b2794f12eb6f948b1c5598ae8b350401a22d4d`. Recorded in PROJECT_FACTS §4c.
The checkout is never modified and is kept separate from the implementation worktrees.

---

## Milestone 2 owner-review items (added 2026-08-10)

These are data-mapping proposals awaiting owner confirmation, produced by the RunUO audit
(`runuo_ultimacraft_mapping.json`, summarized in RUNUO_VENDOR_MATRIX.md). They are review
queues, not blockers.

OQ-3. Five proposed NEW trader types (textile_trader 51 rows, reagent_trader 81,
provision_trader 59, glass_trader 21, scribe_trader 12) — confirm, rename, merge, or reject.

OQ-4. Payout denomination proposals for 915 buyback rows (copper 75 / silver 370 / gold 11 /
unresolved 459) — the raw→copper, processed/finished→silver, ≥500GP→gold heuristic needs
owner ratification, and the 459 unresolved rows need classification (mostly tied to OQ-5
form data).

OQ-5. New commodity families required by the mapping: reagents, textile inputs
(cloth/thread/wool/cotton/flax), glass, scribe (paper/scrolls), plus metal-ingot commodity
mappings for shadow_iron..valorite. Confirm which become Rails commodities vs
UNSUPPORTED_BY_DESIGN.

OQ-6. 694 retail rows with no confident UltimaCraft item match (REQUIRES_OWNER_MAPPING) —
need triage into PROPOSED_DIRECT_ITEM / REQUIRES_NEW_ITEM / UNSUPPORTED_BY_DESIGN, likely in
profession-family batches during Milestone 17 planning.

OQ-7. 21 AnimalBuyInfo rows (creatures with control slots) — owner path needed (animal
trainer vendor selling mobs is a different transaction class than ItemStack products).

OQ-8. 10 REQUIRES_OWNER_MAPPING vendors (GolemCrafter, GypsyMaiden, RealEstateBroker, Thief,
Vagabond, VarietyDealer, EvilHealer, PricedHealer, Hamato, Ryuichi).

OQ-9. Quality carriers missing for armor and ranged-weapon product families (matrix §7) —
needed before those vendor families roll out.

## OQ-1 status note (2026-08-10)

Re-verified during the Milestone 1 documentation correction: `ultimacraft_test` is still owned by
role `ultimacraft` and `SELECT` still fails for `ultimacraft_codex_test`. The repair remains an
outstanding local-admin action; the `Currency.base_value` data correction (canonical
1/100/10,000) is prepared as a Milestone 3 item behind it.