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

## Milestone 2 owner-review items (updated 2026-08-10 OQ cleanup pass)

Findings and shrunken queues from the cleanup pass; full detail in RUNUO_VENDOR_MATRIX.md.

OQ-3 (**tables ready, matrix §4a**): five proposed trader types with buyback provenance —
reagent_trader (81), provision_trader (59), textile_trader (51), glass_trader (21),
scribe_trader (12). Owner options: approve all five, or consolidate glass+scribe into
reagent_trader (three new types). Not created yet.

OQ-4 (**resolved to a policy + 34 exceptions, matrix §4b**): class-based tiers applied as
data — raw → Copper (83 rows), processed/provisions/tools/textiles/finished equipment →
Silver (787), jewelry/gems and ≥500 GP → Gold (11). Only 34 `OWNER_EXCEPTION` rows remain,
all stock of the OQ-8 vendors (Thief/VarietyDealer/RealEstateBroker/Dryad) — they inherit the
OQ-8 outcomes. Owner action: ratify the tier policy.

OQ-5 (**gap table ready, matrix §5**): reagent ITEMS mostly exist (7/8; black_pearl missing)
— the gap is commodity families: reagents, textiles, glass, scribe; metal-ingot commodity
mappings missing for 7 of 9 tiers; plus the decided form/permission columns. Owner action:
confirm which families become Rails commodities.

OQ-6 (**auto-classified, matrix §3**): 1,015 retail rows now bucketed — 473 MISSING_ITEM,
226 VARIABLE_MATERIAL_PRODUCT (151 with item matches via the 202 blacksmithing craftables),
102 LIKELY_MATCH, 66 DIRECT_MATCH, 29 SERVICE_OR_MOBILE, 26 UNSUPPORTED, 93 OWNER_REVIEW.
The 93 reduce to 3 decisions: (a) magic/alchemy consumable items yes/no, (b) deed-economy
mapping, (c) Hammer disambiguation.

OQ-7 (**proposal written, matrix §7b**): AnimalBuyInfo as a Rails-authoritative MOBILE
fulfillment kind — same quote/denomination/treasury path, entity-spawning fulfillment with
receipt-guarded idempotency. Owner sign-off needed before animal-trainer work.

OQ-8 (**recommendations table ready, matrix §8**): 6 merges (GolemCrafter→tinker,
GypsyMaiden/Thief/VarietyDealer→provisioner, Vagabond→jeweler, EvilHealer→healer),
2 service-only (PricedHealer, RealEstateBroker), 2 guildmaster-territory (Hamato, Ryuichi).
Owner action: approve/adjust per row.

OQ-9 (**carrier gaps identified, matrix §7; Fine policy NOT reopened**): melee/metal-armor/
shield carrier exists (BlacksmithEquipmentItem, 202 outputs; needs the decided 1|2→4-tier
bridge); jewelry carrier exists. Genuine gaps: ranged/bowyer family and leather/cloth
tailor family have neither items nor carriers — recorded as implementation gaps for their
Milestone 17 families.

## Owner approvals recorded (2026-08-10, "I approve" on the cleanup-pass summary)

- **OQ-1**: Option A repair approved and EXECUTED (record below).
- **OQ-3**: the five proposed trader types approved as proposed (reagent, provision, textile,
  glass, scribe). The glass+scribe→reagent consolidation option remains available until the
  first new-trader implementation begins — flag then if preferred.
- **OQ-4**: class-based payout denomination policy ratified (raw→Copper,
  processed/provisions/tools/textiles/finished→Silver, jewelry/gems and ≥500 GP→Gold).
- **OQ-7**: mobile-fulfillment concept approved (Rails-authoritative offer/denomination/
  treasury; entity fulfillment with receipt-guarded idempotency).
- **OQ-8**: vendor merge table approved as recommended (matrix §8).
- Milestone 3 authorized and completed (Rails commit `15bf968`).

**Still genuinely open (small):** (a) magic/alchemy consumable items yes/no; (b) deed-economy
mapping; (c) `Hammer` disambiguation (3 rows).
→ **ALL RESOLVED by the 2026-08-11 owner decision pass — see the section at the end of this
file.**

**2026-08-10 second approval ("i approve" on the Milestone 3 report):** the seeded nine-metal
rank ladder (iron 1 … valorite 9) is confirmed as the working default (remains admin-editable
data). Milestone 4 authorized and completed (Rails `37277cc`): economic NPC types, shard
policies, and eligibility previews are now fully Shard-Admin-manageable with audit history.

## OQ-1 RESOLVED (2026-08-10) — repair record per checklist

```text
Databases: ultimacraft_test plus ultimacraft_test-0 … ultimacraft_test-15 (17 total)
Previous owner: ultimacraft (superuser)   New owner: ultimacraft_codex_test
Test role: ultimacraft_codex_test (peer auth via Unix socket, per docs/local_test_database.md)
Repair commands (approved Option A):
  DROP DATABASE IF EXISTS "<each test db>";   -- as ultimacraft, TCP
  bash bin/setup_test_database                -- recreated ultimacraft_test owned by codex role
  (parallel worker DBs recreated automatically from db/schema.rb on the next suite run;
   one extra step was needed after the Milestone 3 migrations: bin/rails db:schema:dump in the
   test env, because config/environments/test.rb sets dump_schema_after_migration = false)
bin/codex_test result (baseline, pre-Milestone-3): 1340 runs, 6338 assertions, 1 failure —
  Economy::CityFoodSupplyRecalculatorTest (expected 100.0, actual 99.7), deterministic,
  pre-existing at banking @ 35653f5.
bin/codex_test result (post-Milestone-3): 1369 runs, 6435 assertions, 0 errors, 1 failure —
  the same pre-existing recalculator failure. One order-dependent flake observed once
  (Admin::BankChequesControllerTest in-limbo aggregate, leaked 500-copper cheque from another
  test); passes in isolation and on reruns — known non-transactional-leak class.
Development database untouched throughout.
```

## OQ-1 status note (2026-08-10, diagnosis complete — awaiting repair approval)

Full diagnosis performed (read-only; no ownership, grants, or data changed):

- Failure: `bin/codex_test` → `db:prepare` → `PG::InsufficientPrivilege: permission denied for
  table schema_migrations`, connecting as `ultimacraft_codex_test` (peer auth, Unix socket).
- Root cause: all 18 `ultimacraft_*` databases (dev + test + 16 parallel workers) and **all 91
  tables inside `ultimacraft_test`** are owned by role `ultimacraft`; the codex role has schema
  USAGE but zero table privileges (`has_table_privilege(...'SELECT') = false`) and there are no
  default ACLs to inherit. The test DBs were evidently recreated by a Rails run executing as
  `ultimacraft`, locking the canonical test role out entirely.
- Roles: `ultimacraft` is a **superuser** (rolsuper=t, createdb, createrole) reachable via TCP
  password auth; `ultimacraft_codex_test` is the intended limited role (createdb only).
- This is purely a local WSL PostgreSQL ownership problem, not a Rails code defect.

Proposed repair (NOT performed — needs explicit owner approval; pick one):

```text
Option A (clean, matches docs/local_test_database.md):  as ultimacraft:
  DROP DATABASE "ultimacraft_test"; DROP DATABASE "ultimacraft_test-0"; … "-15";
  then as the normal user: bin/setup_test_database   (codex role recreates and owns)

Option B (no drop): for each of the 17 test databases, as ultimacraft:
  ALTER DATABASE "<db>" OWNER TO ultimacraft_codex_test;
  and inside each: REASSIGN OWNED BY ultimacraft TO ultimacraft_codex_test;
  then bin/setup_test_database to verify migrations.
```

Both touch ONLY `ultimacraft_test*`; `ultimacraft_development` is untouched either way.

**Live-data finding unlocked by the diagnosis (read-only SELECT):** the development database's
`currencies` rows are `copper=1, silver=10, gold=100` — the obsolete ladder is CONFIRMED LIVE
in dev (not just a migration default), and `ultimacraft_test.currencies` is empty. The
Milestone 3 correction must therefore include a data migration to the canonical
1/100/10,000 values for existing rows, not only new-row defaults.
## Owner decision pass — 2026-08-11 (pre-Milestone-15): ALL remaining decisions resolved

Recorded verbatim from the owner's answers; applied to `runuo_ultimacraft_mapping.json`
(validator + `RunuoBuybackCoverageTest` both green after the edit).

1. **Magic/alchemy consumables: NO — deferred explicitly.** The 60 retail rows (57 explicit
   + 3 programmatic spell-scroll loops) are `UNSUPPORTED_PENDING_MAGIC`. Vendors ship
   without magic stock; rows stay auditable and revisitable when a consumable/magic system
   is designed. Reagent ingredients keep trading as already approved (OQ-3).
2. **Deed economy: existing housing system.** The 30 deed rows are
   `SERVICE_EXISTING_SYSTEM` — deed retail stays owned by the working house-deed flow,
   consistent with RealEstateBroker's SERVICE classification (OQ-8).
3. **`Hammer` rows: each crafting profession gets its own hammer item.** Only
   `blacksmith_hammer` exists today. The 3 rows are `MISSING_ITEM` with planned ids
   `britannia_mod:carpenter_hammer` (SBCarpenter), `britannia_mod:stonecrafter_hammer`
   (SBStoneCrafter), `britannia_mod:tinker_hammer` (SBTinker) — item creation lands with
   their Milestone 17 profession families.
4. **New trader types: keep all five** (reagent, provision, textile, glass, scribe) as
   approved and seeded in Milestone 11. The glass/scribe→reagent consolidation option is
   CLOSED.

**Derived buyback routing (the 53 `REQUIRES_OWNER_MAPPING` rows → 0):** 49 rows became
`OWNER_MAPPED` by copying the already-approved routing of the SAME RunUO item type
elsewhere in the mapping (twin-consistent: one item type, one buying trader) — VarietyDealer
30, Dryad 7, Thief 7 (incl. Lockpick → provision_trader; `lockpick_tools` exists),
RealEstateBroker 2, HairStylist 1, FortuneTeller 1, Veterinarian 1. 4 rows became
`OWNER_UNSUPPORTED`: Architect's InteriorDecorator/HousePlacementTool (housing service) and
HairStylist's two special dyes (no appearance system).

**Deliberately NOT decided here:** ECONOMY_RULES §2a rounding/adjustment calibration stays
in Milestone 19, where real pricing data informs it.

With this pass, no owner decisions remain open ahead of Milestones 15–19.

## Owner architecture directive — canonical supply units (2026-08-11)

Binding, recorded from the owner's statement on the flour weight-vs-quantity finding:

- One canonical `supply` amount per commodity; never authoritative weight AND
  quantity in parallel. `city_commodities.stock_unit` ("weight" | "count")
  names the authoritative column; `unit_weight` derives the mirror.
- Bulk/fungible commodities (flour, grain, ore, ingots, lumber, fish, meat,
  wool, coal, stone and similar) are weight-canonical. Discrete finished
  goods (weapons, armor, tools, furniture, potions, animals) are
  count-canonical.
- Minecraft ItemStack quantities derive from canonical supply and unit
  weight (Rails `available_units` already implements this division).
- Pricing operates against normalized supply vs target supply, unit-agnostic
  (`inventory_level` / `max_supply_cap` — verified already compliant).
- Production recipes consume/produce canonical units (grain → flour by
  weight; iron → longsword consumes weight, produces count).

Applied 2026-08-11 (see ECONOMY_RULES §9 and the implementation log): flour
and the whole grain family migrated to weight; the plural `metal|ingots` rows
the old singular-`ingot` hardcoded check missed are healed; both hardcoded
classification lists (CityCommodity, SaleTransactionProcessor) retired in
favor of per-row data. Follow-ups recorded in ECONOMY_RULES §9: unit_weight
calibration for count rows feeding weight-summed supply columns (wine), and
the reagent family's unit decision at Milestone 17.

## Owner roadmap pass — 2026-08-11 (post-Milestone-17): open items dispositioned

1. **TownPerson regional population (decision #12): dedicated Milestone 20**, after
   Milestone 19. The Milestone 16 migration already preserved everything it needs
   (townPersonAmount in receipts, entities in-world).
2. **The 548 pending-item retail rows: dedicated Milestone 21**, after Milestone 20.
   Owner direction verbatim: the LLM creates the models, textures, and food
   mechanics; many of the produce items already exist; the weapons and armor are
   already craftable and should be partially added. Each created item makes its row
   seedable by re-running the rollout generator.
3. **OQ-5 commodity families: glass + reagents created NOW** (this pass): weight-
   canonical bulk commodities (glass|raw|sand; reagents|raw|<the eight UO reagents>,
   feeding the existing cities.reagents_supply column). Reagent retail rows now seed
   (7 products across mage/holy_mage/alchemist/herbalist/glassblower); the glass
   family stands ready for Milestone 21's bottle items (SBGlassblower's implementable
   stock turned out to be mostly reagent overlap — its true glass items pend M21).
   Textile + scribe families land with their items (Milestone 21).
4. **Wine unit_weight calibration: deferred to Milestone 19 §2a** with real data;
   until then alcohol_supply reads as bottle count.

Roadmap after this pass: M18 observability → M19 validation/calibration → M20
TownPerson regional population → M21 item content (models/textures/mechanics).

## Final status — Milestone 19 (2026-08-11)

Per the playbook's closing requirement, this section enumerates EVERY item that
is not fully implemented, so none is silently treated as done.

**No open owner decisions remain.** Everything below is scheduled work or a
recorded limit, not a pending question.

### Scheduled by owner decision

| Item | Disposition |
|---|---|
| TownPerson regional population (decision #12) | **Milestone 20.** Legacy TownPersons are preserved meanwhile; `townPersonAmount` survives in migration receipts for that system to consume. |
| 548 retail rows pending item creation | **Milestone 21** (models/textures/food mechanics; produce partly exists; weapons/armor already craftable and partially added). Each created item makes its row seedable by re-running the rollout generator. |
| Textile + scribe commodity families | **Milestone 21**, with their items. Glass and reagents shipped in the 2026-08-11 roadmap pass. |
| `black_pearl` retail row | Milestone 21 (its commodity exists; the ITEM does not). |
| 8 registered-but-inactive vendor types | Activate as data when their items/families land — no deploy required. |

### Recorded limits (implemented behavior, deliberately bounded)

- **Magic/alchemy consumable retail (60 rows)** — deferred by owner decision;
  rows are explicitly `UNSUPPORTED_PENDING_MAGIC`, revisitable.
- **Deed retail (30 rows)** — owned by the existing house-deed service.
- **Mobile fulfillment / AnimalBuyInfo (29 rows)** — the OQ-7 concept is
  approved but unimplemented; rows are explicitly excluded, not silently
  dropped.
- **Trader sale item reservation is in-memory** — `ServerEconomyService`
  removes items before the Rails call and refunds on every failure path, but a
  server crash in that window loses the reservation (banking's durable local
  receipts have no equivalent here). Bounded by the sale window; no idempotency
  or double-spend risk, only a crash-window item-loss risk. **Not fixed in this
  program** — recorded as the top hardening candidate for follow-up work.
- **`EconomicStaffingSweepJob` serializes city reconciles** in one run;
  acceptable at current city counts, revisit past hundreds of dirty cities per
  window.
- **Legacy merchant/trader heartbeat** keeps running for any block that has not
  yet converted; it retires with the last legacy block.
- **Per-profession presentation** — ~30 professions share the generic vendor
  entity, and the three profession hammers share the blacksmith hammer model.
  Cosmetic; swappable per type as admin data.
