# M7 sale recovery

The earlier sale path refunded uncertain HTTP outcomes and idempotent replays, including sales Rails had already committed. It also delivered to a captured player object after disconnect and ignored persistence failures. M7b replaces that path with a saved request and a pending settlement.

## Ordering and outcomes

1. Revalidate every selected slot and component after asynchronous preflight. Repeated partial requests share the slot's remaining count. A changed selection refuses the complete reservation.
2. Save the exact item payloads, original request, service origin and idempotency key before removing goods. The record contains no authentication secret.
3. Remove the goods and save the inventory with a removal marker using a checked NeoForge atomic writer. If that write fails, restore the inventory and do not dispatch. Save the dispatch state before HTTP.
4. A confirmed receipt becomes a pending payout. A definitive rejection of the initial dispatch becomes a pending exact refund. Network errors, timeouts, malformed responses and uncertain earlier dispatches remain pending for receipt lookup and same-key retry.
5. Resolve against the currently connected player with the matching UUID. Simulate complete insertion before changing inventory. A full inventory waits for space; recovery does not create world drops.
6. Save inventory and delivery marker together. Remove and flush the journal entry before clearing that marker. If journal resolution fails, the saved marker permits resolution without another insertion. A stale callback for an absent receipt cannot deliver again.

The authenticated Rails receipt lookup is read-only, scoped to shard, player and idempotency key. An already committed sale remains discoverable after its NPC retires. The sale service rechecks duplicate keys after acquiring the city lock, so a concurrent retry returns the winner's receipt before reevaluating stock caps or treasury.

## Deliberately retained uncertainty

A missing lookup does not prove an earlier request cannot still commit. If a retry after an uncertain dispatch is refused, the record stays pending for operator reconciliation instead of inventing either a refund or payout. Legacy `DISPATCHED` records without a saved request also remain visible. Legacy known-undispatched `ITEMS_REMOVED` records can return exact goods. Corrupt payloads remain intact for investigation.

Automatic recovery is bounded to four attempts per pass with a 600-tick retry delay. A change to server origin or shard does not silently replay old requests against a different backend. Startup logging identifies unresolved keys and owners. No new database migration or production write is required by this implementation.

The checked player-file path is verified on the dedicated NeoForge GameTest server. These tests do not claim an arbitrary hardware power-loss experiment or an integrated singleplayer host's separate `level.dat` player snapshot. The original banking helper remains unchanged; its swallowed-I/O limitation is not used as proof of trader durability.

## Verification

- Focused Rails receipt/retry gate: 23 runs, 314 assertions, zero failures/errors/skips in `m7b-focused.log`.
- Five new GameTests cover duplicate partial selection and changed components; real journal/player-file write failures; full-inventory exact refund; saved delivery marker with a failed journal resolution; and retained unknown/corrupt records plus request NBT round-trip.
- The opt-in real Rails GameTest now loses a response **after Rails commits**, disconnects the current player, reconnects the same UUID from its actual saved file, queries the real receipt, and verifies one payout, no goods refund, no repeated POST, and no payout to the old player object.
- The independent read-only Rails verifier confirms two receipts, sale quantities 3 and 2, currency grants 9 and 5 copper, stock 5, and treasury 486 from 500. The earlier same-key replay, exact cap-refund and preflight-disconnect scenarios remain included.

Final full-suite totals and paired local commits are recorded in the scratchpad and handoff. No production sale, seed, migration, deployment or server-JAR replacement occurred.
