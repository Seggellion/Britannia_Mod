# RunUO NPC Vendor System — Exact Reconstruction Design

Source repository: `runuo/runuo`  
Pinned commit: `71b2794f12eb6f948b1c5598ae8b350401a22d4d`  
Target subsystem: `Scripts/Mobiles/Vendors` NPC `BaseVendor` + `SBInfo` catalog model

## 1. Goal

Rebuild the RunUO NPC vendor system as a behaviorally equivalent service rather than as a visual imitation.

The fidelity target is the observable and persisted behavior of the pinned RunUO implementation:

- how an NPC chooses one or more inventory catalogs;
- how catalogs define player-buyable virtual stock and player-sellable item types;
- how conditional catalogs and rows are evaluated;
- how tax scaling is applied;
- how stock is consumed and adaptively restocked;
- how actual entities are instantiated only when a transaction succeeds;
- how player-sold goods become temporary resale inventory;
- how resale inventory expires;
- how vendor state is serialized and restored; and
- which source quirks must be preserved for parity.

The accompanying JSON is both a data dictionary and a source-derived catalog export. The engine model is exact for the analyzed NPC `BaseVendor`/`SBInfo` subsystem. Product-level rows in restricted categories are intentionally omitted from the export; this does not change the engine design.

## 2. Scope boundary

### In scope

The exact reconstruction target is the NPC vendor framework centered on:

- `BaseVendor`
- `SBInfo`
- `GenericBuyInfo`
- `GenericSellInfo`
- specialized `GenericBuyInfo` subclasses such as `AnimalBuyInfo`, `BeverageBuyInfo`, and `PresetMapBuyInfo`
- concrete NPC classes whose `InitSBInfo()` composes catalog groups.

### Adjacent, but separate

RunUO also has player-controlled/rented-vendor systems. `VendorInventory` is an example of that adjacent lifecycle: it stores actual item instances and gold associated with a player vendor and has its own recovery/expiry behavior. It is not the stock model used by an ordinary NPC `BaseVendor`.

Do not merge the player-vendor persistence model into the NPC catalog model.

## 3. Architectural pattern

A concrete NPC does not normally declare every product directly. It owns an ordered `List<SBInfo>` and implements `InitSBInfo()`.

Conceptually:

```text
Concrete NPC
    |
    | InitSBInfo()
    v
ordered inventory-group references
    |
    +--> SBInfo A
    |      +--> BuyInfo  : virtual stock definitions
    |      +--> SellInfo : buyback rules
    |
    +--> SBInfo B
           +--> BuyInfo
           +--> SellInfo
```

`BaseVendor.LoadSBInfo()` then flattens every group's `BuyInfo` and `SellInfo` into the vendor's runtime collections.

This composition model is the primary design to reproduce.

## 4. Static definition vs runtime state

Keep these concepts separate.

### Static definition

A buy offer defines:

- source type;
- optional display/localization name;
- base price;
- starting amount;
- starting maximum amount;
- art/item ID;
- hue or hue expression;
- constructor arguments;
- specialized offer class;
- control-slot cost for mobile offers;
- inclusion condition;
- ordinal position.

A buyback rule defines:

- exact source type accepted;
- base price paid to the player;
- condition;
- ordinal position.

### Mutable runtime state

Each active buy offer carries:

- current amount;
- maximum amount;
- current buyer-specific price scalar;
- optional display entity/cache reference.

Each vendor also carries:

- last restock timestamp;
- actual player-sold resale item instances.

Do not put current stock into the static JSON definition as the authoritative runtime value.

## 5. Initialization sequence

Reproduce the `BaseVendor` sequence closely:

```text
construct concrete vendor
    |
    v
BaseVendor constructor
    |
    +--> LoadSBInfo()
    |      |
    |      +--> lastRestock = UtcNow
    |      +--> delete old display entities
    |      +--> SBInfos.Clear()
    |      +--> concrete InitSBInfo()
    |      +--> flattenedBuy.Clear()
    |      +--> flattenedSell.Clear()
    |      +--> append each SBInfo.BuyInfo in order
    |      +--> append each SBInfo.SellInfo in order
    |
    +--> initialize title/body/outfit
    +--> create hidden ShopBuy container
    +--> create hidden ShopResale container
    +--> lastRestock = UtcNow
```

The modern implementation can use different object types, but the resulting state and ordering should match.

## 6. Catalog composition rules

### Preserve group order

`SBInfos` is an ordered list. Do not treat it as a set.

### Preserve row order

`BuyInfo` and `SellInfo` are ordered. This matters for presentation and, more importantly, for the legacy serialization mapping.

### Never deduplicate automatically

The source can contain multiple rows for the same item type with different item IDs, prices, constructor arguments, or even apparently duplicated definitions.

Examples in the safe export include multiple `BreadLoaf`, `Fish`, `BroadcastCrystal`, and house-deed rows.

The reconstruction must preserve every row as a distinct offer.

### Conditions are data

Conditions such as `Core.AOS`, `!Core.AOS`, map checks, and similar source predicates should be represented explicitly and evaluated when the catalog is built.

Do not preprocess them away if exact historical-mode parity matters.

## 7. Rebuilding on world-context changes

`BaseVendor.OnMapChange()` performs morph checks and calls `LoadSBInfo()` again.

The consequence is important: world placement can change the assembled catalog.

Your implementation should expose something like:

```text
VendorContextChanged(vendor, oldContext, newContext)
    -> rebuild catalog definitions
    -> preserve/overlay compatible runtime state
```

For strict RunUO parity, the rebuild happens from source definitions and the conditions are re-evaluated.

## 8. Buy-offer model

The canonical equivalent of `GenericBuyInfo` should contain:

```text
type
name override
base price
current amount
max amount
item/art id
hue or hue expression
constructor args
price scalar
display entity
```

A recommended implementation record:

```text
VendorBuyOfferDefinition
  stableId
  sourceCatalog
  sourceOrdinal
  entityType
  basePrice
  initialAmount
  itemId
  hueResolver
  nameResolver
  entityFactory
  condition
  specializedMetadata
```

and a separate runtime record:

```text
VendorBuyOfferState
  stableId
  currentAmount
  maxAmount
```

The `stableId` is a modernization. Keep source ordinals as compatibility metadata because RunUO persistence keys expanded stock by ordinal.

## 9. Specialized offer classes

RunUO extends the base buy-offer type instead of adding unrelated transaction systems.

### AnimalBuyInfo

Adds `ControlSlots`.

A purchase is rejected if the buyer does not have enough follower/control capacity for the requested amount.

### BeverageBuyInfo

Adds a content/subtype constructor argument and disables display caching. The purchased entity is created with the selected content subtype.

### PresetMapBuyInfo

Stores a map-entry definition and creates the concrete map entity from that entry. It also disables display caching.

General rule: specialized offers should implement the same `BuyOffer` interface and override only the metadata/factory behavior they need.

## 10. Virtual inventory

The configured amount is not a pile of physical item instances inside the NPC.

A catalog row is a virtual stock record.

When the vendor UI is built, RunUO creates or retrieves a display entity so the client has presentation metadata. That display entity is not one of the stock units.

Only after payment succeeds does the transaction call the offer factory to create the purchased entity.

This distinction is critical.

Do not pre-create 20 physical objects because a row has an amount of 20.

## 11. Entity creation

`GenericBuyInfo.GetEntity()` uses reflection-style construction:

```text
no constructor args -> create Type()
constructor args    -> create Type(args...)
```

For a modern implementation, replace raw reflection with a registered factory if desired, but preserve equivalent inputs.

Transaction behavior:

- decrement virtual stock first as part of the successful purchase path;
- create one stack with the purchased amount for stackable items;
- create one entity per unit for non-stackable items;
- for mobile offers, create the mobile(s), place them in the world, and assign the buyer as controller when applicable;
- if the buyer inventory cannot accept an item, RunUO falls back to placing it at the buyer's world location.

## 12. Display names

When no explicit name is supplied, `GenericBuyInfo` derives a localized label identifier from the item ID:

```text
itemId < 0x4000
    -> 1020000 + itemId
otherwise
    -> 1078872 + itemId
```

Preserve explicit string/localization overrides separately from computed labels.

Do not normalize a numeric localization string into a human-readable label in the source-of-truth data unless you also preserve the original value.

## 13. Buyer price scaling

Before building the list or executing a purchase, `BaseVendor.UpdateBuyInfo(buyer)` refreshes the price scalar.

The default scalar is:

```text
no town -> 100
town    -> 100 + town.Tax
```

The effective price uses integer rounding:

```text
((basePrice * scalar) + 50) / 100
```

For very large base values RunUO uses a wider integer intermediate to avoid overflow.

Design requirement: store `basePrice` separately from the current effective price. Tax is contextual and buyer-interaction-time state.

## 14. Restocking cadence

Default `RestockDelay` is one hour.

This is not implemented as a globally scheduled stock timer. When a player opens the vendor buy interface, RunUO checks whether enough time has passed and restocks lazily.

Equivalent rule:

```text
if now - lastRestock > 1 hour:
    Restock()
```

`Restock()` updates `lastRestock` and calls `OnRestock()` on every buy offer.

## 15. Adaptive stock algorithm

Reproduce `GenericBuyInfo.OnRestock()` exactly.

### If the item sold out

If current amount is `<= 0`:

```text
if ML mode AND display entity is a non-stackable Item:
    max = min(20, max)
else:
    max = min(999, max * 2)
```

### If stock remains

Start with:

```text
threshold = max
```

Then:

```text
if max >= 999:
    threshold = 640
else if max > 20:
    threshold = max / 2
```

If:

```text
currentAmount >= threshold
```

then:

```text
max = threshold
```

Finally:

```text
currentAmount = max
```

This creates the familiar capacity ladder for sufficiently demanded rows:

```text
20 -> 40 -> 80 -> 160 -> 320 -> 640 -> 999
```

Initial quantities can be something other than 20. Preserve the source row's starting amount.

## 16. Selling items to an NPC

The vendor's `SellInfo` describes what the NPC will buy from the player.

The generic implementation uses exact source types as dictionary keys.

At transaction time, RunUO checks, among other things:

- the item belongs to the seller;
- the response amount is positive;
- the item is standard loot;
- the item is movable;
- a container being sold is empty.

The transaction has a maximum of 500 accepted sell entries.

## 17. Buyback price vs resale price

A `GenericSellInfo` row defines the base price the vendor pays the player.

When an accepted transferable item becomes resale stock, the price shown to the next customer is:

```text
truncate(1.90 * adjustedPlayerSellPrice)
```

Keep these concepts separate:

```text
player -> vendor price
vendor -> next customer resale price
```

Some item subclasses have additional source-specific price adjustment logic. Implement such adjustments behind a pricing-strategy hook rather than hardcoding them into the generic catalog record.

## 18. Player-sold goods are actual inventory

This is a second inventory model living alongside virtual stock.

`GenericBuyInfo.Restock(item, amount)` is disabled in this RunUO revision; it immediately returns false and the older merge behavior is commented out.

Therefore, a resellable item sold by a player is not added to the virtual `GenericBuyInfo.Amount`.

Instead, the actual item instance (or a split duplicate) is placed into the vendor's hidden buy pack and timestamped.

The next buyer can see that actual instance as resale stock.

This behavior should be reproduced rather than silently merging player sales into normal stock.

## 19. Resale expiry

Player-sold resale inventory has a one-hour decay period.

When `VendorBuy()` scans actual resale items, it removes any item where:

```text
item.LastMoved + 1 hour <= now
```

The expiry is opportunistic during vendor browsing, not necessarily a dedicated timer per item.

A faithful implementation can use scheduled cleanup internally, but observable results should match.

## 20. Purchase validation and payment

Requested quantity is capped to current stock.

The cost accumulator uses:

```text
effectivePrice * amount
```

Mobile offers also reserve control slots before purchase.

Payment behavior has a notable legacy quirk:

1. Game-master access can purchase without charge.
2. RunUO first attempts to pay the entire total from the player's backpack.
3. If that fails and the total is below 2000, the purchase fails for insufficient funds.
4. If that fails and the total is at least 2000, RunUO attempts to pay the entire total from the bank.
5. It does not combine partial backpack funds with partial bank funds.

If exact parity is required, preserve this no-split-tender behavior.

## 21. UI/list contract

The vendor buy list contains, semantically:

```text
name
container identity
display entity identity
effective price
current amount
item id
hue
```

RunUO caps the generated visible list at 250 rows.

Player-sold resale items are appended to the same logical shopping list when they qualify.

The implementation sorts the resulting list before sending the client packets.

A new client protocol does not need to reproduce RunUO packets, but it should reproduce the logical row set and limits.

## 22. Hidden containers

The NPC owns hidden containers/layers used by the client/vendor protocol.

At minimum, separate:

- virtual catalog presentation/storage plumbing;
- actual resale items.

In a modern server you do not need literal hidden backpack entities unless the client protocol requires them, but do not lose the distinction between catalog rows and real resale objects.

## 23. Persistence

The persistence model is one of the most important fidelity traps.

RunUO serializes recognized expanded `MaxAmount` tiers only:

```text
40
80
160
320
640
999
```

It does not persist the exact partially depleted `Amount`.

On load:

1. rebuild the static catalogs through `LoadSBInfo()`;
2. decode saved expanded-capacity records;
3. locate the row by SBInfo ordinal and BuyInfo ordinal;
4. set both `Amount` and `MaxAmount` to the restored tier.

So after a server reload, learned capacity survives, but partial depletion does not.

## 24. Ordinal persistence hazard

Legacy persistence identifies the expanded row using list positions.

That means changing:

- SBInfo group ordering; or
- BuyInfo ordering within a group

can cause a saved capacity record to apply to a different product after an update.

For a new implementation, use a stable offer identifier, for example:

```text
vendorCatalogId + sourceRowId
```

but keep the original catalog/group and row ordinals in compatibility metadata so a RunUO import/export adapter can reproduce the original behavior.

## 25. Random expressions are construction-time data

Some rows call a random hue helper while the catalog is constructed.

Do not replace:

```text
hue = RandomNeutralHue()
```

with:

```text
hue = "neutral"
```

unless the runtime resolver knows to evaluate it at catalog-build time.

The JSON therefore distinguishes:

- constant `hue`;
- `hue_expression`.

The same principle applies to any source expression that is evaluated while an `SBInfo` object is instantiated.

## 26. Commented and TODO source rows

Commented source rows are not inventory.

They can be captured as migration notes, but they must not become active offers.

The JSON uses `inactive_source_rows` metadata for this distinction in catalogs where it was observed.

## 27. Recommended component layout

A clean rebuild can preserve RunUO behavior while making responsibilities explicit.

### VendorDefinitionRegistry

Loads NPC definitions and their ordered inventory-group references.

### CatalogRegistry

Loads `SBInfo`-equivalent catalog definitions.

### VendorConditionEvaluator

Evaluates expansion, map, region, or other context predicates.

### VendorCatalogAssembler

Performs `LoadSBInfo()` semantics and returns ordered active rows.

### VendorRuntimeStateStore

Stores current/max stock and last-restock time.

### VendorPricingService

Applies tax scalar, buyback pricing, and resale pricing.

### VendorStockService

Applies purchase decrements and the exact restock algorithm.

### VendorEntityFactory

Creates the actual item/mobile only after successful purchase validation.

### VendorResaleStore

Stores actual player-sold item instances and expiry metadata.

### VendorTransactionService

Owns buy/sell validation, payment, fulfillment, and rollback boundaries.

### VendorPersistenceCodec

Implements native stable-ID persistence and optional RunUO ordinal compatibility.

### VendorUIAdapter

Converts active virtual offers plus actual resale items into client rows.

## 28. Suggested transaction boundary

A purchase should be atomic at the service level:

```text
resolve active offer
validate requested amount
validate control capacity
calculate effective price
reserve/consume funds
decrement stock
create entity/entities
deliver to buyer
commit
```

If your platform supports transactional rollback, restore funds and stock if entity creation or delivery fails unexpectedly.

RunUO itself is not written as a database transaction, but a modern rebuild should prevent divergence while retaining the same normal-path outcome.

## 29. JSON contract

The generated `runuo_vendor_catalog.json` contains:

- source/commit metadata;
- the field dictionary for every vendor/catalog/runtime concept;
- exact shared runtime rules;
- verified vendor definitions;
- verified source-derived safe catalog rows;
- a normalized product index with occurrence lists;
- explicit coverage/omission metadata.

Important: the normalized `products` object is an index, not a replacement for ordered catalog rows. The authoritative reconstruction data remains `catalogs[*].buy_offers` and `catalogs[*].buyback_rules`.

## 30. Parity test matrix

Implement automated tests for at least these behaviors.

### Catalog assembly

- group order remains stable;
- row order remains stable;
- duplicate item types remain separate offers;
- conditional groups are included/excluded correctly;
- map/context changes trigger rebuild.

### Pricing

- 100% scalar returns base price;
- town tax alters scalar;
- integer rounding matches RunUO;
- buyer interactions refresh the scalar.

### Stock

- purchase decrements current amount;
- sold-out rows grow at restock according to the source algorithm;
- sufficiently unsold expanded rows shrink;
- 999 uses 640 as the contraction threshold;
- restock sets current amount equal to max amount.

### Specialized offers

- control-slot offers reject purchases that exceed capacity;
- constructor-argument offers create the correct subtype;
- non-cacheable display offers never reuse an incompatible display entity.

### Resale

- player-sold items do not merge into generic stock;
- actual item instance properties remain attached to resale stock;
- resale price uses the generic markup;
- resale entries disappear after the decay window when scanned.

### Payment

- backpack can pay a normal purchase;
- an under-2000 purchase cannot fall back to bank when backpack lacks the full total;
- a 2000-or-more purchase can use bank when backpack cannot pay the full total;
- backpack and bank are not combined for one purchase.

### Persistence

- expanded max tiers survive save/load;
- partial depletion does not survive save/load;
- restored current amount equals restored max amount;
- compatibility tests detect changes in source ordinals.

### Presentation

- virtual stock does not require physical pre-created units;
- duplicate offers remain separately visible;
- generated list respects the 250-row cap.

## 31. Migration strategy

### Phase 1 — Data model

Implement definitions and runtime state separately.

### Phase 2 — Catalog importer

Translate each source `SBInfo` into JSON/data objects while preserving source order, expressions, and conditions.

### Phase 3 — Assembly

Implement `LoadSBInfo()` semantics and context-driven rebuilding.

### Phase 4 — Buy flow

Implement pricing, payment, stock decrement, factory creation, and delivery.

### Phase 5 — Sell/resale flow

Implement exact-type buyback matching, actual-instance resale storage, markup, and expiry.

### Phase 6 — Restocking

Implement lazy hourly restocking and adaptive max quantities.

### Phase 7 — Persistence

Implement stable native IDs plus RunUO ordinal compatibility.

### Phase 8 — Differential tests

For a fixed source commit and world context, compare the rebuilt server's active row set, prices, stock transitions, and save/load behavior against RunUO fixtures.

## 32. Acceptance criteria

The rebuild is complete when:

- the same source conditions produce the same ordered active catalog rows;
- duplicate rows are not collapsed;
- the same base prices and initial quantities are loaded;
- effective prices match the source tax/rounding rule;
- purchases consume stock and instantiate products at transaction time;
- stock expansion/contraction matches the source;
- player-sold resale goods remain actual instances and expire correctly;
- payment source behavior matches;
- persistence restores learned capacity but not partial depletion;
- map/context changes rebuild conditional catalogs;
- specialized buy-offer subclasses can express their extra factory/control metadata;
- safe exported rows retain source file and ordinal traceability.

## 33. Source-fidelity quirks checklist

Preserve all of these unless you intentionally choose a non-parity mode:

- ordered `SBInfo` composition;
- ordered buy/sell rows;
- duplicate catalog rows;
- conditional groups and rows;
- construction-time random expressions;
- buyer-time tax scalar;
- virtual catalog stock;
- display entities distinct from stock units;
- actual entity creation at purchase time;
- lazy one-hour restock;
- adaptive max quantities;
- disabled player-sale merge into generic stock;
- actual player-sold resale instances;
- one-hour resale decay;
- 1.90 resale markup;
- 250 visible buy-row limit;
- 500 sell-entry limit;
- no backpack/bank split tender;
- persistence of expanded max tiers only;
- ordinal legacy persistence mapping;
- context/map-driven catalog rebuilds.

## 34. Source files used for the reconstruction model

Framework:

- `Scripts/Mobiles/Vendors/BaseVendor.cs`
- `Scripts/Mobiles/Vendors/GenericBuy.cs`
- `Scripts/Mobiles/Vendors/GenericSell.cs`
- `Scripts/Mobiles/Vendors/AnimalBuy.cs`
- `Scripts/Mobiles/Vendors/BeverageBuy.cs`
- `Scripts/Mobiles/Vendors/PresetMapBuy.cs`
- `Scripts/Mobiles/Vendors/SBInfo/SBInfo.cs`

Verified safe catalog examples represented in the JSON include the carpenter safe subset, animal trainer, architect/house deeds, baker, beekeeper, cobbler, farmer, fisherman, fur trader, hair stylist, and jeweler catalogs.

The repository contains additional vendor/catalog classes. Restricted product-level categories are deliberately not enumerated in the generated public data export.
