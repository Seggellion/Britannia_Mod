# Vendor/Trader Economy — ECONOMY_RULES

Owner-approved economic rules for the Vendor/Trader economy, consolidated for implementation.
Status classifications follow COMPATIBILITY_MAP.md (OWNER DECISION unless marked
IMPLEMENTATION DETAIL / OPEN CALIBRATION). Nothing here re-opens decided points.

## 1. Currency and denominations (OWNER DECISION)

```text
Canonical comparative values:  Copper = 1    Silver = 100    Gold = 10,000
100 Copper = 1 Silver;  100 Silver = 1 Gold;  10,000 Copper = 1 Gold
```

- Comparative value establishes relative worth ONLY. It is never settlement conversion.
- Every Vendor/Trader quote is `amount + denomination`, denomination ∈ {copper, silver, gold}.
- Settlement is exact-denomination: a 25-Gold product needs 25 physical Gold coins; equivalent
  copper does not qualify; no mixed baskets; no NPC-side conversion in either direction.
- Rails owns amount, denomination, affordability, and any substitution decision (there is none).
- Minter/denomination exchange is a future Service NPC concept, out of scope; nothing in the
  Vendor/Trader economy may depend on it or emulate it.
- The obsolete Rails 1/10/100 `base_value` ladder is a scheduled data/code correction
  (Milestone 3; PROJECT_FACTS §4b). After correction, `Currency.base_value` is an
  analytics/comparison input only.

## 2. Vendor retail pricing (OWNER DECISION; calibration OPEN)

RunUO GP calibration: **1 RunUO GP = 1 UltimaCraft Gold**. RunUO-imported vendor products
default to gold denomination; RunUO price = the Iron-baseline finished-product anchor (it
already embeds craftsmanship/labor/margin/game-balance value).

```text
baseline_material_cost = product_material_quantity × current_iron_commodity_price
selected_material_cost = product_material_quantity × current_selected_material_price
material_delta         = selected_material_cost − baseline_material_cost

final_vendor_price     = runuo_iron_baseline
                       + material_delta
                       + approved_production_adjustment      # see §2a
```

- The result stays in the Product's configured denomination (no mixed-denomination results).
- Prohibited as target rules: legacy `input_cost × 1.70`, raw-material-only pricing, fixed Java
  prices, client-computed prices.
- Vendor retail is intentionally expensive: a luxury/convenience path and player-wealth drain,
  not a competitor to player gathering/crafting/trade. Do not discount because
  copper-equivalents look large.
- Vendor -> Player payments credit the city treasury in the paid denomination.

### 2a. OPEN CALIBRATION items (to be fixed during the pricing milestone)

```text
approved_production_adjustment   formula/curve not yet calibrated (must be tested against
                                 expected RunUO price ranges)
material_delta unit bridging     material_delta is computed in commodity-price units
                                 (copper-comparative); the conversion of that delta into the
                                 Product's denomination (e.g. gold) uses the canonical
                                 comparative values — exact rounding below
denomination rounding policy     floor / nearest / ceiling / tick-size within the Product's
                                 denomination; must be explicit, documented, and tested;
                                 fractional remainders are never converted into another
                                 denomination
```

## 3. Trader payout valuation (OWNER DECISION; strategy details IMPLEMENTATION)

Closed Rails-owned valuation strategy registry (names follow project conventions at
implementation time):

```text
CURRENT_COMMODITY_VALUE           authoritative city commodity market value
RAW_COMMODITY_VALUE               raw form; shard-configurable below-market multiplier
PROCESSED_COMMODITY_VALUE         processed form; full current market value permitted
RECIPE_DERIVED_VALUE              Rails Product/material-derived valuation
WINE_QUALITY_VALUE                absorbs CityCommodity#calculate_wine_value semantics
SALVAGE_MATERIAL_QUALITY_VALUE    absorbs legacy salvage material/quality valuation
```

- Strategy selection comes from Rails-owned mapping data, never from client requests or Java
  class names.
- Trader quotes are `amount + denomination`; per-economic-class default denominations are
  mapping data pending owner review (matrix §4: raw→copper, processed/finished→silver,
  high-value→gold are PROPOSALS).
- Player -> Trader payouts debit the city treasury in the payout denomination, atomically,
  never below zero; unfundable transactions are rejected whole (no partial purchase, no
  substitution). Denomination-specific city liquidity is intentional.
- One authoritative player payout per transaction (physical coins / banking) — no
  `shard_user.currency` double-write.
- Raw vs processed vs finished is explicit Rails form data (backfilled from conventions,
  owner decision #9); shard multiplier policy binds to that data.

## 4. Product availability (OWNER DECISION)

A Rails Product is listed only when ALL hold:

```text
product active/configured
AND economic NPC active and assigned to this city/post
AND shard policy allows it
AND every Product.requirements entry resolves to a real city commodity
AND required commodities are production-enabled
AND sufficient city supply exists for the production policy
AND the material variant is eligible (rank + supply)
AND treasury/economy policy permits the sale
```

Fail closed: no fallback inventory, no fallback prices, no vanilla-ingredient substitution, no
client-side synthesis. Catalog snapshots are display-only; purchase-time Rails validation is
authoritative (stale quotes are rejected/requoted).

## 5. Material variants (OWNER DECISION)

- Material progression/rank is explicit Rails data (key, family, economic rank, commodity
  mapping, enabled). Iron is the baseline. Enum order/durability/alphabetical are prohibited
  rank sources.
- Per base product slot, catalogs display the highest-value currently producible variant,
  falling back down the rank (…→ Iron) as city supply changes.
- Material gap: rank data does not exist yet, and ingot commodities beyond copper/silver/gold
  are unmapped (matrix §6) — Milestone 3 schema/seed work.

## 6. Quality (OWNER DECISION)

NPC-manufactured equipment ladder: Crude / Basic / **Fine** (NPC output) / Exceptional (max).
Blacksmithing's 1|2 representation is bridged safely during implementation; crop 0–100 quality
is a separate system. Quality carriers are missing for armor and ranged families (matrix §7).

## 7. Arbitrage invariant (OWNER DECISION)

No deterministic same-city loop may profit:

```text
buy product from Vendor for X [denomination]
sell to any Trader
→ received value must be < X in comparative terms
```

Valuation strategies and retail rules are tested for same-city arbitrage before rollout.
Cross-city price differences may be intentional gameplay; same-city loops are not.

## 8. Treasury flow summary (OWNER DECISION)

```text
Player -> Trader:  treasury[denom] -= amount;  city commodity/supply += goods;
                   player += amount[denom]     (reject whole if unfundable)
Vendor -> Player:  player -= amount[denom];    treasury[denom] += amount;
                   product requirements consumed per production policy;
                   player += product (material/quality/provenance applied server-side)
```

Implemented via locked, idempotent Rails services (GuildTraining::Purchase treasury pattern;
existing transaction idempotency; banking-style local receipts for the Minecraft item leg).
Vendor revenue moves currency (player → city); it does not destroy it — shard-level sinks are
future city-expense systems, not Vendor price manipulation.

## 9. Canonical supply units (OWNER DIRECTIVE, 2026-08-11)

One authoritative supply representation per commodity, named by
`city_commodities.stock_unit`:

```text
stock_unit = "weight"  ->  weight is canonical  (bulk/fungible goods)
stock_unit = "count"   ->  quantity is canonical (discrete goods)
```

- The mirror column is DERIVED through `unit_weight` on every save
  (`quantity = floor(weight / unit_weight)` or `weight = quantity x
  unit_weight`) and can never act as a second source of truth; a direct write
  to the mirror is overwritten from the canonical column.
- Bulk families (weight): fish, wood, stone, ore, meat, grain, metal,
  textile, and every food-supply category (food, produce, animal_product,
  cooking_ingredient, dairy, eggs, groceries, bone, fat). Flour is the
  canonical example: `grain|milled|flour` is weight-based supply.
- Discrete goods (count): wine/alcohol bottles today; weapons, armor, tools,
  furniture, potions, animals as their commodity families arrive.
- Pricing and availability read `inventory_level` (aliased `supply`)
  normalized against `max_supply_cap` -- they are unit-agnostic by
  construction and unchanged by this directive.
- Production recipes consume and produce canonical units: grain -> flour
  moves weight; iron -> longsword consumes ingot weight and produces a
  counted product (`ProductAvailability` divides canonical supply by the
  per-unit requirement, which is already the derived-count rule Minecraft
  ItemStacks need).
- The old hardcoded `weight_based_inventory?` category list (and
  `SaleTransactionProcessor`'s second copy of it) is retired; classification
  is per-row data with a category-based default for new rows.

Calibration follow-ups (OPEN, non-blocking): real `unit_weight` values for
count-canonical commodities whose derived weight feeds city supply columns
(wine bottles), and the reagent family's unit decision when Milestone 17
implements it.
