# Vendor/Trader Economy — API Contract

The authoritative Minecraft↔Rails surface for the economic NPC system, as
implemented. Rails is the price/production/eligibility authority; Minecraft
materializes decisions and owns physical inventory. Every endpoint below uses
signed shard-server authentication (`Api::ShardServerAuthentication`) — never
the legacy static bearer token — and is rate-limited per endpoint.

Transport conventions: JSON request/response; responses bounded (client caps
reads at 1 MB, refuses request bodies over 256 KB); connect/read/overall
timeouts 5 s / 10 s / 15 s (`BoundedHttp`).

---

## GET `/api/economic_catalog`

What a Vendor offers in one city, right now. Display data — **purchase-time
validation is authoritative** (see the purchase endpoint).

**Query** — `economic_npc_type_key`, `city_public_id` (both required; the city
is resolved by stable public id only, never by name).

**Response**

```jsonc
{
  "economic_npc_type_key": "weaponsmith_vendor",
  "city_public_id": "…uuid…",
  "city_name": "Britain",
  "catalog_revision": "…",              // cache/invalidation key
  "rows": [
    {
      "item_id": "britannia_mod:broadsword",
      "display_name": "Broadsword",
      "available": true,
      "reasons": [],                     // machine-readable when unavailable
      "unit_price": 33.0,
      "denomination": "gold",            // copper | silver | gold
      "available_units": 12,
      "pricing_strategy": "iron_baseline_material_delta",
      "requirements": [ { "key": "metal|ingots|iron", "quantity": 2.0 } ],
      "selected_material": "iron",       // Milestone 13 construction data
      "quality": "fine"
    }
  ]
}
```

Failure: `404 {"error": "economic_npc_type_not_found" | "city_not_found"}`.

Client behavior: unavailable rows are simply not offered (fail closed, no
fallback catalog). `selected_material`/`quality` are written into the item's
custom data at construction time.

---

## POST `/api/economic_purchase`

The authoritative Vendor→Player retail commit (Milestone 14).

**Request**

```jsonc
{
  "idempotency_key": "economic:<player>:<npc>:<tick>:<uuid>",
  "world_npc_public_id": "…uuid…",       // identity resolves from the assignment
  "player_uuid": "…", "player_name": "…",
  "items": [
    { "item_id": "britannia_mod:broadsword", "quantity": 1,
      "expected_unit_price": 33.0, "expected_denomination": "gold" }
  ]
}
```

**Rails guarantees** (all inside one transaction; any failure rolls back whole):

1. The NPC must resolve to an **active assignment** on a registered post whose
   economic type is a **vendor**.
2. Every line is re-validated through `ProductAvailability` — the same
   authority the catalog used. Price/denomination drift ⇒ `409 quote_changed`;
   the client never wins a price race.
3. Exactly one denomination per purchase (`mixed_payout_denominations`).
4. Production inputs are consumed under row locks in the commodity's
   **canonical supply unit**; never below zero (reject, never oversell).
5. The city treasury is **credited in the paid denomination**; the Currency and
   TreasuryBalance rows are created on demand.
6. One idempotent ledger row; a replay returns the recorded outcome with no
   second production and no second credit.

**Response** — the transaction JSON plus:

```jsonc
{
  "purchase_items": [
    { "item_id": "…", "quantity": 1, "unit_price": 33.0, "denomination": "gold",
      "selected_material": "iron", "quality": "fine",
      "origin_city_public_id": "…uuid…", "origin_city_name": "Britain" }
  ],
  "payment": { "denomination": "gold", "amount": 33 },
  "idempotent_replay": false
}
```

Errors (`error.code`): `missing_idempotency_key`, `vendor_not_found`,
`vendor_not_assigned`, `missing_items`, `invalid_quantity`,
`product_not_offered`, `product_unavailable`, `insufficient_stock`,
`quote_changed`, `mixed_payout_denominations`, `missing_required_commodity`,
`insufficient_commodity_supply`.

Client behavior: reserve **exactly** the quoted denomination before posting;
refund in kind on any rejection or outage; never auto-retry (idempotency keys
make a manual retry safe).

---

## POST `/api/trader_transactions`

Player→Trader buyback. Routed to `Economy::EconomicTraderSale` when
`world_npc_public_id` is present (Milestone 10); legacy payloads without it
keep the legacy processor.

**Request** — `idempotency_key`, `world_npc_public_id`, player identity, and
`items[]` of `{commodity_key, quantity, weight}`.

**Rails guarantees**: identity from the active assignment (must be a
**trader**); the commodity must exist with `npc_buy_enabled`; one payout tier
per basket (raw→copper, processed/finished→silver, ≥500 GP→gold — a basket
spanning tiers is rejected whole, never converted); the payout **debits
`coins_outstanding`** in that denomination, atomically, never below zero
(`treasury_insufficient` rejects the whole sale — no partial purchase, no
reserve fallback); city supply grows by the canonical amount; idempotent.

**Response** — transaction JSON; the payout is `currency_grant`
(`{denomination: amount}`), the single authoritative payout representation
(physical coins). Errors: `trader_not_assigned`, `commodity_not_found`,
`commodity_not_buyable`, `commodity_stock_cap_exceeded`,
`mixed_payout_denominations`, `treasury_insufficient`.

---

## Spawn posts and world state (shared with Service NPCs)

- `POST /api/service_npc_spawn_operations` — durable outbox delivery of post
  UPSERT/REMOVE. The envelope carries **exactly one** of
  `service_npc_type_key` / `economic_npc_type_key` (DB-enforced
  `num_nonnulls(...) = 1`); Minecraft transports economic keys through the
  shared slot with an `economic:` prefix, decoded once at serialization.
- `GET /api/world_state_changes/:shard?from_version=` — incremental change
  cursor (assignments, posts, registries). Never a full snapshot poll.
- `GET /api/world_bootstrap/:shard` — includes `economic_npc_registry`
  (types + revisions, shard-scoped enablement). The `minecraft_server`
  profile omits city commodities; the response is capped at 4 MB.

## Invariants that hold across every endpoint

- **Vendors never buy from players**; all RunUO SellInfo behavior is Trader-only.
- **Exact-denomination settlement**: no automatic conversion, no mixed baskets;
  fractional remainders never spill into another denomination.
- **Rails `Product.requirements` is the only recipe authority** — never
  Minecraft crafting recipes, MerchantRecipes, or CraftableRegistry.
- **Persistent World NPC identity is never deleted** by post removal or
  ineligibility; assignments close instead.
- Prices are never client-supplied: the client echoes a quote, Rails re-derives.
