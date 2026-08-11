# Economy Performance Verification (Milestone 18)

Each playbook bullet, verified against the code that enforces it (pointers are
the enforcement sites; nothing here is aspirational).

## No Rails request per tick

- Catalog fetches happen only on player interaction (`ServerCatalogService.fetchAndSend`,
  invoked from the NPC screen-open path), never from a ticker.
- Legacy spawn-block Rails sync ran on 200/600-tick cooldowns and is being
  retired by the Milestone 16 migration; authoritative posts tick locally and
  speak to Rails only through the durable outbox delivery processor.
- World-state sync polls on a ~5-minute cadence with jitter
  (`WorldStateSyncPoller.BASE_CADENCE_TICKS = 6000`, `MAX_JITTER_TICKS = 1200`).
- Rails-side event coupling is the Milestone 15 dirty-mark (an `update_all`
  on commit) drained by a 5-minute Solid Queue sweep — never inline work.

## No product-by-product chatter

- One catalog snapshot request returns every row for a vendor
  (`GET economic_catalog` → `EconomicCatalog::ForVendor` rows;
  `fetchEconomicCatalog` parses them in one pass).
- Purchases are one POST per basket (`economic_purchase` with an items array),
  re-quoted from ONE catalog fetch (`EconomicVendorPurchaseService.fetchQuotes`).

## Bounded payloads

- Every HTTP read is capped (`BoundedHttp.readUtf8` with 1 MB caps at all
  catalog/purchase call sites; requests over 256 KB are refused client-side in
  `ServerCatalogService.request`).
- Catalog rows are capped at `ClientboundOpenNpcScreenPayload.MAX_PRODUCTS`;
  purchase baskets at `BuyMerchantItemsC2SPayload.MAX_ITEMS = 64` /
  `MAX_QUANTITY = 1024` (codec-enforced on decode).
- The world bootstrap is capped at 4 MB with a compact `minecraft_server`
  profile that omits city commodities (`Api::WorldBootstrapProfileTest`).
- HTTP work is throttled by a bounded executor
  (`ServerHttpExecutor`: 1–2 threads, queue capacity 32, rejects on overflow
  rather than queueing unboundedly).

## Pagination where appropriate

- World-state changes are consumed incrementally by version cursor
  (`from_version`), never as full snapshots.
- Admin surfaces paginate with Pagy (economic transactions: 50/page).
- The retail catalog is deliberately snapshot-per-vendor (bounded by
  MAX_PRODUCTS), not paginated — a vendor's catalog is small by design.

## Cache invalidation by revision

- Economic/Service registries carry revisions; the reconciler re-materializes
  from snapshot revisions (`EconomicNpcRegistrySnapshot.revision`,
  assignment/spawn-point `source_revision`).
- Catalog responses carry `catalog_revision` (stamped into item custom data);
  purchase-time validation is authoritative regardless (`quote_changed`
  rejects any drift), so a stale catalog can never transact at stale prices.
- Post configuration is revision-gated end to end
  (`ServiceNpcSpawnStateMachine.nextRevision`, stale-revision rejection).

## Safe retry/backoff

- All Rails calls run with connect/read/overall timeouts
  (`BoundedHttp`: 5 s / 10 s / 15 s).
- Post registration retries through the DURABLE pending outbox (redelivery on
  later ticks; idempotent by operation id) rather than tight loops.
- The world-state poller re-arms with jitter after every poll including
  failures; the Milestone 15 sweep's conditional mark-clear never loses work.
- Purchase failures refund locally and never auto-retry (operator-visible,
  idempotency keys prevent double-commit on manual retry).

## Verified gaps (recorded, not hidden)

- The legacy merchant/trader path (pre-migration blocks) still heartbeats on
  its own cadence until Milestone 16 conversion reaches it; it retires with
  the blocks.
- `EconomicStaffingSweepJob` serializes city reconciles in one job run;
  acceptable at current city counts, revisit if a shard exceeds hundreds of
  dirty cities per window (noted for Milestone 19).
