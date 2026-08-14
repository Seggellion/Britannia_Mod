# UltimaCraft Vendor/Trader Economy
# Currency Denomination and NPC Settlement Context

**Purpose:** Establish the owner-approved currency-denomination rules for the Vendor/Trader economy without modifying or replacing `COMPATIBILITY_MAP.md` or `OPEN_QUESTIONS.md`.

**Status:** Corrective architecture context

**Scope:** Vendor pricing, Trader payouts, Rails Product pricing, treasury settlement, RunUO GP calibration, and denomination handling.

**Explicitly out of scope:** Implementation of Minter Service NPCs or any denomination-exchange Service NPC in the current Vendor/Trader milestones.

---

# 1. Why This Document Exists

The Vendor/Trader design previously risked treating Copper as a universal atomic settlement currency and converting Gold-denominated NPC prices into very large Copper values.

That is not the intended UltimaCraft economy.

Copper, Silver, and Gold are not merely display formats for one interchangeable checkout balance.

They are distinct physical coin denominations used for settlement.

NPC transactions must specify which denomination they require or pay.

A transaction settles only in that denomination.

---

# 2. Currency Denominations

UltimaCraft currently uses the following comparative denomination relationship:

```text
100 Copper = 1 Silver
100 Silver = 1 Gold

Therefore:

10,000 Copper = 1 Gold
```

These ratios establish comparative monetary value.

They do **not** mean that every NPC automatically converts one denomination into another during a transaction.

The existence of an equivalent value does not make denominations interchangeable at checkout.

---

# 3. Core Settlement Rule

Every Vendor or Trader transaction has:

```text
amount
denomination
```

Examples:

```text
20 Gold
75 Silver
340 Copper
```

The denomination is part of the price contract.

An NPC only accepts or pays the denomination specified by that transaction.

For example:

```text
Vendor price:
20 Gold

Player inventory:
10 Gold
50,000 Copper

Result:
Purchase fails because the player does not possess 20 Gold coins.
```

The Vendor does not automatically convert the player's Copper into Gold.

Likewise:

```text
Trader payout:
8 Silver

City has:
large Copper balance
0 Silver available for Trader settlement

Result:
The transaction cannot be funded in the required denomination.
```

Do not silently substitute another denomination.

---

# 4. No Automatic Currency Conversion at Vendors or Traders

Vendor and Trader code must never perform implicit denomination conversion.

Do not implement:

```text
Gold price
-> inspect Silver/Copper
-> calculate equivalent value
-> accept mixed denominations
```

Do not implement:

```text
Silver Trader payout
-> city lacks Silver
-> automatically pay equivalent Copper or Gold
```

Do not accept mixed denomination baskets unless a future owner-approved feature explicitly introduces such behavior.

The settlement denomination is exact.

---

# 5. Minter Service NPC Is Future Context Only

The intended future economy includes a Minter-style Service NPC that may allow players to exchange coin denominations.

For example, conceptually:

```text
player gives Copper
city receives Copper

city gives Gold
player receives Gold
```

subject to city availability and future Minter rules.

However:

> **Do not implement Minter NPCs as part of the current Vendor/Trader economy project.**

The current Vendor/Trader milestones must not depend on the Minter existing.

Do not:

- create a Minter Service NPC type;
- add Minter UI;
- add denomination-exchange APIs;
- add Minter spawn requirements;
- add automatic denomination exchange as a temporary substitute;
- add hidden conversion behavior inside Vendor or Trader transactions.

The Vendor/Trader architecture should merely remain compatible with a future explicit denomination-exchange service.

---

# 6. RunUO GP Calibration

For default Vendor retail calibration:

> **1 RunUO GP is treated as 1 UltimaCraft Gold coin.**

RunUO Vendor prices therefore become Gold-denominated baseline prices in UltimaCraft.

Example:

```text
RunUO price:
25 GP

UltimaCraft default Vendor baseline:
25 Gold
```

Do not convert the Vendor-facing price into:

```text
250,000 Copper
```

for display or settlement.

The comparative Copper value may be mathematically derivable, but the actual Vendor contract remains:

```text
25 Gold
```

This is intentional.

NPC Vendors are intended to be expensive.

Many Vendor products should be unaffordable to ordinary players for substantial portions of progression.

Vendor retail is intended to function as a major wealth sink / luxury convenience path rather than the ordinary acquisition method for most products.

---

# 7. Vendor Products Are Intentionally Expensive

High Vendor prices are a deliberate game-design choice.

NPC Vendors should not compete with player gathering, crafting, farming, trade, or production on price.

The intended relationship is:

```text
player production / player trade
    generally economically preferable

NPC Vendor
    immediate convenience
    guaranteed availability when the city economy supports production
    very high monetary cost
```

This creates pressure for players to participate in the broader economy rather than treating NPC shops as the default source for finished goods.

---

# 8. Rails Product Must Store a Denomination

Rails `Product` is authoritative for NPC product definitions.

The Product pricing model must therefore represent both:

```text
price amount
price denomination
```

The existing `products.price` field alone is not sufficient to express the complete settlement contract.

The exact schema is a Milestone implementation detail, but conceptually the Product must support a closed denomination set such as:

```text
copper
silver
gold
```

Example:

```json
{
  "price": 25,
  "denomination": "gold"
}
```

Future UltimaCraft Products may intentionally use other denominations:

```json
{
  "price": 80,
  "denomination": "silver"
}
```

or:

```json
{
  "price": 350,
  "denomination": "copper"
}
```

Do not infer denomination from the numeric magnitude.

Do not assume all future Products are Gold-denominated.

---

# 9. RunUO-Imported Vendor Products Default to Gold

Products seeded/imported from RunUO Vendor prices should default to:

```text
denomination = gold
```

unless a later project-owned mapping explicitly overrides the denomination.

Therefore:

```text
RunUO Vendor price:
43800 GP

UltimaCraft baseline:
43800 Gold
```

This may represent enormous comparative Copper value.

That is intentional.

Do not reduce the Gold price merely because the Copper-equivalent number appears large.

---

# 10. Material-Adjusted Vendor Pricing Still Uses the Product Denomination

Material-based dynamic price adjustments do not change the Product's settlement denomination.

Example:

```text
Base Product:
Iron item
RunUO baseline = 10 Gold

Higher material variant:
dynamic calculation produces a higher economic value
```

The final Vendor quote remains Gold-denominated if the Product is Gold-denominated.

Conceptually:

```text
RunUO Iron baseline
+
material-cost difference
+
approved production/economic adjustment
=
Gold-denominated retail price
```

Do not convert the adjustment into Silver/Copper checkout components.

Do not produce mixed denomination prices such as:

```text
27 Gold + 43 Silver
```

unless a future owner-approved pricing system explicitly supports mixed-denomination settlement.

---

# 11. Dynamic Price Rounding Must Respect the Denomination

Dynamic calculations may produce fractional values relative to the selected denomination.

For example:

```text
calculated economic value:
27.43 Gold
```

The final transaction must resolve to an allowed quantity of Gold coins.

The exact rounding rule is an implementation/calibration decision and should be documented in `ECONOMY_RULES.md`.

Possible policies include:

```text
floor to whole denomination
nearest whole denomination
ceiling to whole denomination
configured denomination tick size
```

Do not silently convert the fractional remainder into another denomination.

The same rule applies to Silver- and Copper-denominated Products.

---

# 12. Trader Payouts Are Also Denomination-Aware

Trader purchase policies must also produce:

```text
amount
denomination
```

rather than one universal numeric payout.

Examples may eventually include:

```text
ordinary raw resource
-> Copper-denominated payout

processed material
-> Silver-denominated payout

high-value finished/rare item
-> Gold-denominated payout
```

The exact denomination policies are Rails-owned economic configuration.

Do not hard-code denomination selection in Java based on item class names.

---

# 13. City Treasury Must Preserve Denomination

Treasury accounting for NPC transactions must preserve the actual denomination involved.

Conceptually:

```text
Vendor -> Player transaction

player Gold decreases
city Gold coins_outstanding increases
```

For a Silver-denominated Product:

```text
player Silver decreases
city Silver coins_outstanding increases
```

For Trader purchases:

```text
Player -> Trader

required payout denomination treasury decreases
player receives that denomination
```

Do not automatically net the transaction through another denomination.

The Rails treasury remains authoritative for whether the transaction can be funded.

---

# 14. Trader Funding Must Be Denomination-Specific

A city having sufficient total comparative monetary value does not automatically mean that it can fund a Trader payout.

Example:

```text
Trader payout:
4 Gold

City treasury:
1 Gold
50,000 Copper
```

Unless a future explicit denomination exchange/minting process has converted city holdings:

```text
Result:
Insufficient Gold liquidity.
```

The Trader must not automatically spend the city's Copper as Gold.

This intentionally introduces denomination liquidity as part of the city economy.

---

# 15. Vendor Purchases Do Not Require Destination-Denomination Supply

A Vendor receiving money from a player is different from a Trader paying a player.

If a Product costs:

```text
20 Gold
```

and the player possesses 20 Gold:

```text
player pays 20 Gold
city treasury receives 20 Gold
```

The city does not need pre-existing Gold supply to accept the payment.

The transaction increases city Gold liquidity.

This is one mechanism by which Gold enters the city's treasury.

---

# 16. Player Payout Representation Remains Singular

The prior decision remains unchanged:

New Trader payouts must not simultaneously:

```text
credit shard_user.currency
AND
grant physical coins
```

One economic transaction produces one authoritative player payout.

Physical denomination-specific coins and the established banking/account systems are the intended authoritative monetary representations.

The legacy `shard_user.currency` write path should be retired for new Vendor/Trader flows.

---

# 17. Rails Is the Price and Denomination Authority

Minecraft must never decide:

- the final price;
- the settlement denomination;
- a denomination substitution;
- a conversion rate exception;
- treasury affordability;
- whether another denomination can be substituted.

Rails returns or authorizes the quote.

Conceptually:

```json
{
  "product_key": "example_product",
  "amount": 25,
  "denomination": "gold",
  "catalog_revision": 42
}
```

Minecraft:

- displays the quote;
- verifies/interacts with physical inventory;
- removes the exact required coin denomination after authoritative transaction preparation;
- reconstructs/delivers outputs;
- follows the established receipt/reconciliation model.

---

# 18. Catalog Display Rule

NPC catalog screens must display the actual settlement denomination.

Display:

```text
25 Gold
```

not:

```text
250,000 Copper
```

Display:

```text
40 Silver
```

not:

```text
4,000 Copper
```

Do not display a "base Copper equivalent" as the primary Vendor/Trader price.

If comparative-value information is ever added for debugging/admin purposes, it must remain separate from the actual transaction denomination.

---

# 19. Price Comparison and Economic Calculations

Rails may need a normalized comparative-value calculation for:

- material-cost comparisons;
- economic analytics;
- arbitrage tests;
- pricing calibration;
- valuation strategies.

A normalized value may mathematically use denomination ratios.

That normalized value is an internal calculation tool.

It is **not** the transaction settlement unit.

Maintain a strict distinction:

```text
comparative economic value
!=
required settlement denomination
```

---

# 20. Same-City Arbitrage Must Still Be Prevented

Denomination-specific pricing does not remove the need for arbitrage protection.

The completed economy must not permit a deterministic loop such as:

```text
buy Product from Vendor for X Gold
immediately sell same Product to Trader
receive more than X Gold-equivalent value
repeat
```

Rails valuation strategies and Product retail rules should be tested for same-city NPC arbitrage.

Cross-city price differences may eventually be intentional gameplay, but same-city infinite-money loops are not.

---

# 21. Gold Sink Clarification

Vendor spending currently has two different meanings:

## Player-level sink

When a player pays a Vendor:

```text
Gold leaves the player's possession.
```

This is immediately a strong player wealth sink.

## Shard-wide monetary sink

If that Gold is credited to the city treasury:

```text
player Gold decreases
city Gold increases
```

the currency has moved rather than been destroyed.

Therefore, if the long-term goal includes reducing total shard currency supply, separate city expenses will eventually need to remove currency from circulation.

Possible future city expenditures may include:

```text
TownPerson population upkeep
NPC staffing
city services
guards
maintenance
other owner-approved economic sinks
```

Those systems do not need to be implemented in the current Vendor/Trader milestone unless explicitly scheduled.

Do not arbitrarily destroy Vendor revenue merely to call the Vendor a "sink."

---

# 22. TownPerson Economy Remains Compatible

The separate owner-approved TownPerson system remains compatible with denomination-aware treasuries.

TownPerson population is driven by city prosperity, including:

```text
food
alcohol
wealth / treasury
```

Future population expenses may provide a recurring treasury drain.

The exact denomination treatment for TownPerson upkeep is not defined by this document.

Do not add TownPerson currency-consumption behavior merely because this corrective note mentions the future possibility.

---

# 23. Minter Dependency Rule

Vendor/Trader implementation must be complete and valid even if no Minter NPC exists anywhere in the shard.

This means:

```text
no Minter available
+
player lacks required Gold
=
Vendor purchase is unavailable to that player
```

That is acceptable and intentional.

Do not compensate by weakening exact-denomination settlement.

The future Minter system will provide an additional economic path, not a prerequisite required to make Vendor/Trader code function.

---

# 24. Future Minter Compatibility Requirements

Although the Minter is out of scope, do not design Vendor/Trader accounting in a way that prevents a future Minter from operating.

The future system will likely need:

```text
denomination-aware treasury balances
exact denomination quantities
atomic denomination exchange
city supply checks
transaction audit/idempotency
physical coin inventory mutation
```

The current project should preserve these concepts without implementing the service.

---

# 25. Required Corrections to Vendor/Trader Implementation Documents

Where existing planning documents imply that all prices should be normalized and settled in Copper, correct them.

Replace concepts such as:

```text
Copper is the canonical settlement unit.
Every price is converted to Copper.
NPCs accept equivalent mixed denominations.
```

with:

```text
Prices are amount + denomination.
NPCs settle only in the stated denomination.
Comparative denomination conversion is not automatic settlement conversion.
```

Where existing documents discuss RunUO calibration, specify:

```text
1 RunUO GP
=
1 UltimaCraft Gold
for default imported Vendor retail prices.
```

Where existing documents discuss future currency conversion, mark Minter functionality as:

```text
future Service NPC work
not part of the current Vendor/Trader milestones
```

---

# 26. Required Transaction Invariants

The Vendor/Trader implementation must maintain these invariants:

```text
1. Every NPC price/payout contains an explicit denomination.

2. Gold, Silver, and Copper are distinct settlement denominations.

3. A Vendor accepts only the denomination in its authoritative quote.

4. A Trader pays only the denomination in its authoritative quote.

5. No Vendor or Trader performs automatic denomination conversion.

6. No mixed-denomination checkout occurs unless explicitly added by future design.

7. Rails owns the final amount and denomination.

8. Rails owns treasury affordability.

9. Player inventory mutation uses the exact physical coin denomination.

10. Trader treasury debits cannot go negative.

11. Vendor payments credit the same denomination to the city treasury.

12. New transactions do not double-credit shard_user.currency.

13. RunUO GP defaults to UltimaCraft Gold for imported Vendor prices.

14. Material-price adjustments remain in the Product's settlement denomination.

15. Catalogs display the actual settlement denomination.

16. A future Minter may exchange denominations, but Minter implementation is out of scope now.

17. Vendor/Trader functionality must not depend on a Minter being present.
```

---

# 27. Suggested Rails Product Direction

The exact schema should follow project migration conventions, but the Product domain must eventually express the equivalent of:

```text
Product
    item_id
    requirements
    baseline price
    price denomination
    RunUO/default price metadata
    material policy
    quality policy
```

Conceptually:

```json
{
  "item_id": "britannia_mod:example",
  "price": 25,
  "price_denomination": "gold",
  "requirements": {
    "commodities": []
  }
}
```

Do not treat this JSON example as the required database schema.

Use proper typed Rails columns/associations where appropriate.

---

# 28. Suggested Trader Valuation Direction

Trader valuation strategies should return a quote object equivalent to:

```text
amount
denomination
valuation strategy
market inputs
revision
```

Conceptually:

```json
{
  "amount": 12,
  "denomination": "silver",
  "strategy": "processed_commodity_value",
  "quote_revision": 17
}
```

Rails remains authoritative.

The player cannot request a preferred payout denomination as part of a normal Trader sale unless a future design explicitly allows that behavior.

---

# 29. Scope Boundary for Current Milestones

The current Vendor/Trader economy project may implement:

```text
denomination-aware Product pricing
denomination-aware catalog display
denomination-aware physical payment
denomination-aware treasury credit/debit
denomination-aware Trader payouts
exact-denomination transaction validation
Rails-authoritative quote denomination
```

It must **not** implement:

```text
Minter NPC
Minter ServiceNpcType
Minter spawn block configuration
coin exchange screen
coin exchange API
automatic denomination exchange
city minting
currency issuance
precious-metal-backed minting
mixed-denomination checkout
```

Those belong to a future project/milestone unless explicitly added later by the owner.

---

# 30. Owner-Approved Context Summary

The following is the intended economic context:

```text
RunUO GP prices become Gold-denominated UltimaCraft Vendor baselines.

NPC Vendors are intentionally expensive.

A 10,000-Copper comparative value for 1 Gold is not a reason to display or settle Gold prices in Copper.

A 25-Gold Product costs 25 physical Gold coins.

A player with sufficient Copper-equivalent wealth but insufficient Gold cannot purchase it.

Vendors do not exchange denominations.

Traders do not exchange denominations.

Future Minters may provide explicit denomination exchange if the city has the required supply.

Minters are not part of the current implementation.

Prices in future may intentionally be Gold, Silver, or Copper.

The denomination is part of the authoritative Rails transaction contract.

City treasuries preserve denomination-specific liquidity.

Rails remains authoritative for economic pricing and settlement.
```

This document should be treated as corrective currency context for all subsequent Vendor/Trader economy milestones.
