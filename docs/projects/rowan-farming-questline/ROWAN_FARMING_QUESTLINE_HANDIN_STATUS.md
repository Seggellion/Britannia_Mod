# Strict item hand-ins — NeoForge implementation status

Contract `quest_item_handin` version 1, protocol section 1.5. Rails shipped its half at
`release/public` @ `e8a0d58`; this is the mod half.

Branch `claude/neoforge-quest-item-handin-f8695f`, based on `patch-18` @ `a8017b81`, which is a
strict descendant of `integration/rowan-patch18` @ `f9ef224a` and carries four dialogue fixes this
work builds directly on.

---

## The rule, and where it is enforced

> Successful turn-in removes exactly the requested item and quantity from carried inventory, and
> nothing else. A quest without an authored hand-in removes nothing at all.

Hand-in requirements are the sole authority for removal. Nothing else in the mod can cause one:
objectives, quest history, granted-item stamps and dialogue text are all read-only with respect to
the inventory. The only entry point is `QuestItemHandinService.begin`, reached from exactly one
place — a `handin_required` answer to the player's own `CHOOSE`.

| Guarantee | Where |
| --- | --- |
| Only carried inventory is read — 36 main slots and the off-hand | `QuestHandinInventory.carried` |
| Armour, ender chest, containers, houses, dropped entities are never read | same; there is no other accessor |
| All-or-nothing | `QuestHandinInventory.plan` answers `Planned` or `Insufficient`; only `Planned` reaches `removeAll` |
| Exact quantity, remainder preserved | `SlotTake` carries a per-slot count; `shrink` takes that and no more |
| Repeated item ids aggregate their demand but keep separate proof | `plan` consumes against a shared tally, one entry per `requirement_index` |
| A crop resolves to its produce, never its seed | `QuestHandinRequirementResolver` → `CropRegistry.byId(flagValue).harvestItem()` |
| Server thread only | `begin` runs inside the proxy's `server.execute`; every callback hops back the same way |
| Clients cannot name an item | no client packet carries one; the demand comes from Rails |

---

## Lifecycle

```
PREPARED ──▶ REMOVAL_INTENT ──▶ REMOVED_LOCAL ──▶ CONFIRMING ──▶ CONSUMED
    │                │                                   ├──────▶ REFUNDED
    │                └──▶ (marker absent) ──▶ PREPARED    └──────▶ STRANDED
    └──▶ ABANDONED ──▶ (re-claimable)
```

`ABANDONED` and `PREPARED` are the only re-claimable states, and both are states in which nothing
was ever taken. `STRANDED` is preserved forever and never pruned: it is the record that a player
gave something up for an outcome Rails could not prove.

---

## The crash-safety argument

A removal touches two files written at different moments. Only one direction of disagreement is
survivable.

* **Player file ahead of the ledger** — items gone, ledger does not know. Recoverable, because the
  player's own file carries the whole proof.
* **Ledger ahead of the player file** — ledger says removed, player reloads still holding the items.
  Confirming that buys a completed quest, or a refund, for nothing.

So the player save comes **first** and the durable record second — the same conclusion
`BankTransferPlayerDurability` reached for a bank deposit, whose notes name receipt-first as a real,
exploitable duplication.

The shrink and the marker append are two adjacent in-memory changes to the same `ServerPlayer` with
no I/O, wait or yield between them, so no save can observe one without the other. Vanilla writes
that file to a temp path and atomically replaces the real one, so the file on disk holds **both the
removal and its proof, or neither**.

| Crash point | Player file | Ledger | Recovery |
| --- | --- | --- | --- |
| after intent, before mutation | items present, no marker | `REMOVAL_INTENT` | unwound to `PREPARED`; nothing owed |
| inside the mutation | items present, no marker | `REMOVAL_INTENT` | identical — the file cannot hold half of it |
| after player save, before ledger flush | items gone, marker | `REMOVAL_INTENT` | promoted from **the marker's own proof** and confirmed |
| after ledger flush, before HTTP | items gone, marker | `REMOVED_LOCAL` | confirmed with the stored proof, byte-identical |
| Rails consumed, reply lost | items gone, marker | `CONFIRMING` | retried; Rails answers `duplicate`; applied once |
| ledger file lost entirely | items gone, marker | row absent | rebuilt from the marker and confirmed |
| player dies mid-transaction | items gone, marker | any | the marker survives — `PlayerDataCloneHandler` |
| player save silently failed | items present, no marker | `REMOVED_LOCAL` | no marker means no removal: settled `ABANDONED` and claimable again |
| marker present but corrupt | items gone, damaged marker | `REMOVED_LOCAL` | stranded — an entry existing proves the removal, so it is never re-offered |

### What this does not claim

Two limits, both inherited and both already stated by the machinery this follows.

`PlayerDataStorage#save` catches its own `IOException` and only logs, so a forced player save that
*reports* success is not proof the bytes reached disk. If that silent failure happens, the ledger
records `REMOVED_LOCAL` for a player whose file still has the items. That is not left to chance: on
the next start the marker is absent while the ledger says removed, and the marker wins — the shrink
and the marker persist together or not at all, so no marker means no removal. The row is settled
`ABANDONED`, the player keeps the items **and can claim the hand-in again**, and nothing is ever
confirmed. That is the only outcome of that window, and it is the correct direction.

A damaged marker is the opposite case and is told apart from an absent one: an entry that exists but
cannot be parsed is proof the removal *did* happen, so that row strands rather than being re-offered.
Collapsing the two would either trap a player who could simply try again, or charge one twice.

`SavedData#save(File, …)` swallows its own `IOException` too. Every state whose flush could fail is
one recovered from the player file, which is written first for exactly this reason.

Neither covers a power loss defeating `fsync` on the storage device — the same limitation the
blessed-item and banking receipts state plainly for themselves.

---

## Rails results and what each one does here

| Result | Local outcome |
| --- | --- |
| `consumed` | completion applied once through `applyQuestResponse`; row `CONSUMED`; marker dropped and force-saved |
| `duplicate` | the stored completion, applied only if this row had not already applied it |
| `cancelled_refunded` | row `REFUNDED`; **nothing inserted locally** — the refund is an ordinary pending reward delivery |
| `items_missing` | unreachable from here (this server only confirms after a removal); treated as a refusal to settle |
| `cancelled` | items gone with neither ending proved → `STRANDED` |
| `rejected` (404 with the code) | same → `STRANDED` |
| 404 without the code | `EndpointUnsupported` — the route is missing, not the transaction; row stays open |
| `evidence_rejected` (409) | the stored proof is byte-stable, so every retry earns the same refusal → `STRANDED` |
| transport failure / timeout | row keeps its proof; reconciliation retries with the same bytes |

**A refund is never minted locally.** There is no code path in this feature that inserts an item.

---

## Fixture mirror

`src/test/resources/quest_contract/v1/` gained exactly three files, copied byte-for-byte after LF
normalisation from Rails' `test/fixtures/quest_contract/v1/`:

```
d4ca1a526386d37cb10ce65dc4fda24e661edec1b121a23de3a4d768cba1c9d4  handin_result_request.json
7b094f861dfff0820019fbf33da94db19df2c595b006945a554f2157ce0ed7c1  handin_result_response_cancelled_refunded.json
0899be9b6e01319544f4b2eb817841746089ccfb617a75dfceadf1053f527d27  handin_result_response_consumed.json
```

Those digests were already in Rails' manifest. The twelve digests this repository already pinned are
unchanged. `QuestContractFixturesTest` verifies all fifteen.

**One thing the fixture does not settle.** `handin_result_request.json` names `britannia_mod:carrot`
as the produce for crop `carrot`; this mod's `CropRegistry` says `britannia_mod:carrots`. The fixture
is illustrative — Rails deliberately never checks a resolver's concrete item, because not knowing
that mapping is the entire reason the resolver shape exists (§1.5.5). The mod is the authority and
sends `carrots`.

---

## Release order

Unchanged from §1.5.7, and step 2 is the barrier:

1. Deploy the Rails code and migration through `e8a0d58`.
2. **Do not apply the updated Rowan seed yet.**
3. Integrate and deploy this mod.
4. Apply the Rowan seed.
5. Begin live acceptance.

Between 1 and 4 no node carries hand-in metadata, so every quest behaves exactly as before. Before 3,
a mod without this code meets `handin_required`, falls through the ordinary path, applies nothing and
advances nothing — Rails creates no delivery and no completion until a shard confirms a removal, so
there is no free reward to obtain.

---

## Why a valid hand-in cannot become permanently stranded

`STRANDED` is not an ending — no completion, no refund — so the invariant holds only if no ordinary
gameplay can reach it after a durable removal. Three Rails answers lead there. Each is impossible by
contract, and the reasons are different.

### `cancelled` — unreachable by construction

`QuestItemHandins::Confirm#late_confirmation` answers `cancelled` **only** when the report says
`removed: false`; a report that took something takes the `refund` branch and answers
`cancelled_refunded`. This server confirms only after a durable removal and only ever sends
`removed: true`, so the branch cannot be entered. The mod handles it defensively and nothing more.

This is also what makes the ugliest race safe: a player abandoning or restarting the quest while a
confirmation is in flight. Rails cancels the row, the confirmation lands afterwards, and the late
confirmation publishes the refund.

### `rejected` — Rails does not hold the transaction

Answered when the `handin_uuid` is unknown, belongs to another shard, or resolves to another player.
Rails itself minted the row inside the player's own claim and never deletes it — `Quest`'s
`dependent: :destroy` reaches the journal row, not the hand-in, whose `player_quest_state_id` is
`ON DELETE SET NULL` while `user_id` and `shard_id` are `ON DELETE RESTRICT`. So a `rejected` answer
means the shard is talking to a Rails that never prepared this transaction: a mis-pointed
`api_base_url`, a restored-from-backup database, or a credential belonging to a different shard.
Configuration, not gameplay.

### `evidence_rejected` — the mod would have to contradict itself

The only one the mod could cause on its own, and the only one it cannot recover from: Rails checks
evidence **before** the consumed/cancelled branches, so a retry earns the identical refusal, and the
stored proof is byte-stable so the retry is identical.

Both halves are closed structurally, and both are tested:

* **`evidence_mismatch`** — the proof does not describe the requirements. The proof is generated
  from the demand by `QuestHandinRequirementResolver`: exactly one entry per requirement, carrying
  that requirement's own index and count, a literal echoing its own item id and a resolver echoing
  the resolver and the flag value pinned at prepare time. `QuestHandinEvidenceBoundaryTest` checks
  the generated proof against `RailsEvidenceMatcher`, a line-by-line transcription of
  `Confirm#matched_proof` and `#requirement_satisfied?`, and separately shows that oracle refusing
  every malformed shape so the acceptance is not vacuous.
* **`evidence_conflict`** — a proof contradicting one already stored. The proof is built once, at
  `REMOVAL_INTENT`, before the mutation, and is never recomputed: every later attempt re-encodes the
  stored entries, and the recovery paths read the marker's copy, which was written from the same
  plan. `QuestHandinLedgerStoreTest.theReloadedProofEncodesToTheSameBytesTheFirstAttemptSent` pins
  that a reload posts the bytes the lost attempt posted.

A resolver requirement whose flag was never pinned would be unanswerable — but Rails refuses to
prepare one, and `parseDemand` refuses to read one, so the mod never removes for it.

### And a stranded row is still not the end

The read-only reconciliation endpoint re-examines every stranded row about once a minute. If Rails
ever records the transaction as `consumed` or `cancelled_refunded` — including because an operator
resolved it there — the mod settles it automatically and the player gets the completion or the
refund without anyone touching the mod's ledger.

### Operator recovery

A stranded row is logged at every boot with the transaction id, the player, the state, the attempt
count and the strand reason (`event=quest_handin_outstanding`), and the removal itself is logged with
the exact items (`event=quest_handin_stranded ... proof=<item>x<count>`).

| Strand reason | What it means | What to do |
| --- | --- | --- |
| `handin_rejected` | Rails holds no such transaction | Check the shard's `api_base_url` and server key first — this is almost always a mis-pointed shard. If the configuration is right, the transaction is genuinely gone; issue the items back as a Rails reward delivery. |
| `marker_unreadable` | the player's file has an entry for this transaction whose bytes are damaged | The items really did leave the pack — an entry exists, and a marker and its shrink are written together. The exact proof is in the boot log; compensate through Rails. |
| `evidence_*` | Rails refused the proof | Should be unreachable; treat as a defect report. The Rails row is still `pending` with its own stored requirements — compare them with the logged proof. Compensate through Rails. |

A silent player-save failure is deliberately **not** in that table any more. The ledger saying "removed"
while the player's own file has no entry at all means the shrink never reached disk either — the two
are written in one step — so the player is still holding everything. That row is settled `ABANDONED`
and becomes claimable again (`event=quest_handin_removal_did_not_persist`), which is the only outcome
that leaves the player able to finish the quest with the goods they still have. Stranding it, as an
earlier revision did, left them staring at a stage they could never complete while holding exactly
what it asked for.

In every case the compensation is issued **through Rails' own reward-delivery machinery**, which the
mod already applies durably, survives a full pack and cannot be granted twice. The mod never mints a
refund, and an operator should never edit its ledger: a stranded row is the audit record of what
happened, and it is meant to outlive the incident.

---

## Independent audits

Two read-only adversarial reviews were run against the invariant, one after the durability layer and
one before handoff. Between them they found eleven defects; all were fixed and the fixes are covered
by tests, except where noted below.

### Round one — the durability layer

| Finding | Consequence | Resolution |
| --- | --- | --- |
| Player death dropped the persistent-data marker | items gone, quest unfinished, no refund | `PlayerDataCloneHandler` carries the compound across a respawn |
| `ABANDONED` was a permanent dead end | the quest became unfinishable, with the dialogue insisting the transaction was in flight | `ABANDONED` is re-claimable; a transient registry refusal no longer closes the row |
| A completion could be applied to a disconnected player | quest looked finished with none of its effects | the answer is deferred to the next login sweep |
| The marker was forgotten in memory only | a leaked marker replayed a completed transition, and enough of them blocked every future hand-in | force-saved like the write; the sweep reaps settled markers; the guard covers every settled state |
| The live confirm skipped the in-flight reservation | every hand-in over one second was confirmed twice, concurrently | the reservation is shared with the sweep |
| `evidence_rejected` looped forever in `CONFIRMING` | the identical body resent until the player logged out, then again at every login | it strands, which is the same fact as a rejection |

### Round two — before handoff

| Finding | Consequence | Resolution |
| --- | --- | --- |
| An inquiry acted on any row Rails named | one player's completion could run on another, and close a row nobody asked about | answers are filtered to the ids this sweep asked for, and to this player's own rows |
| `cancelled` un-stranded an `evidence_rejected` row | a permanent 60-second cycle, each lap flushing the whole overworld storage twice | an evidence refusal is never requeued |
| `consumed` resolved a `marker_missing` strand | the quest completed for items the player was still holding | a removed row is only resolved while the player's own file vouches for it |
| A settled row could be re-settled into a different ending | a refund landing on an applied completion pays both | the first authoritative ending wins; the second is logged |
| A rebuilt row re-applied a completion on `duplicate` | the finished transition ran again | a duplicate on a marker-rebuilt row is replayed, not re-applied |
| An answer naming a different transaction was accepted | a row settled on somebody else's outcome | the id is checked |
| The resolver and pinned flag could reach a client on a fall-through path | the question this server exists to answer, handed to the player | the sanitizer strips them from any hand-in requirement list |
| Two GameTests asserted less than their names claimed | the "applied exactly once" guard and the no-hand-in path were untested | both rewritten to drive the real path |
| One GameTest leaked the completion applier | later batches reconciled with a no-op | it restores the production applier |

Two residual items were judged acceptable and are recorded rather than fixed:

* A row whose confirmations keep failing is asked about at most once a minute, and a player holding
  more than fifty unresolved transactions is already an operator situation. The rotation is fair —
  asked rows are skipped for the interval, so the remainder is asked on the next sweep.
* The retry schedules are plain maps mutated from completion callbacks. Correct because every path
  is on the server thread; it would break silently if that ever stopped being true.

### Round three — pre-integration

Three parallel audits: cross-repository protocol, durability and races, gameplay and UI.

The protocol audit found the wire contract clean on every axis — routes, auth, envelopes, bounds,
result and status vocabularies, reconciliation scoping, and all fifteen fixture digests — and
independently confirmed that `evidence_rejected`, `cancelled` and `rejected` are unreachable in
ordinary gameplay, `cancelled` structurally so.

| Finding | Consequence | Resolution |
| --- | --- | --- |
| A silently-failed player save stranded the row, blocking any re-claim | the player kept the items and could never finish the stage | no marker means no removal: settled `ABANDONED` and claimable again |
| A corrupt marker was indistinguishable from an absent one | with the fix above, would have offered a second removal for goods already paid | `handinRemovalRecorded` tells "present but unreadable" from "absent"; the former strands |
| A `cancelled` inquiry re-queued a stranded row | confirm → refuse → strand → repeat every 60s, flushing the whole overworld storage twice a lap | only a row whose confirmation has not already been refused is re-queued — keyed on the state, not on a reason string |
| A refund inserted into a player on the death screen | `PlayerList.respawn` discards the corpse's inventory: items acknowledged to Rails and thrown away | queued instead, which the reconciler already retries |
| A shortfall re-armed the abandon-on-close guard | Escape after "you are still short" silently abandoned the quest stage | `choiceMade` stays set; it gates the abandon guard, not clicking |
| Stage 4/5 guidance demanded a Water Bucket the mix never uses | sent every player after an item stage 3 had confiscated | the chain fills a bowl at a water source; the bucket is gone from the list |
| The reissue kit still required that bucket on stages 4 and 5 | every conversation with Rowan spent a bounded replacement, then repeated "limit reached" forever | no bucket past stage three, where it is still reissuable |
| The status block could overlap the body and silently drop its heading | a shortfall list drawn over the dialogue, unlabelled | fitted to the room available; the tail is dropped, never the heading |
| The body's last lines were unscrollable while a status line showed | the end of what Rowan said was unreachable | the scroll range accounts for the status block |
| A response arriving after the screen closed re-opened or closed the wrong screen | a quest dialogue popping over whatever the player was doing | the callback returns unless this screen is still current |
| "Waiting for confirmation" shown while only re-checking the inventory | the two lines exist to be told apart | a re-attempt after a known shortfall says "Checking what you are carrying" |
| Guidance ignored a mix the player already held | told to gather dung for fertilized dirt in their own hands | the finished good short-circuits the list |
| Rails' shortfall list can use the ledger's `id` spelling | a well-formed answer would have been read as malformed and retried forever | read under either spelling, as Rails' own inbound contract does |

Latent only — the mod never sends `removed: false`, so Rails cannot reach `items_missing` — but the
last one is a real divergence one behavioural change away from an infinite retry, and neither side
had coverage.

Two further items were judged acceptable and recorded rather than fixed: a re-application window
between applying a completion and marking the row settled (every sub-effect is individually
idempotent), and two pre-existing callers that hand a screen-space width to `font.split`, which
measures in font units — outside this feature's diff, and the cause of a mild pre-existing
under-count of wrapped lines.

## Open items for the owner

* **Live acceptance is the only remaining gate.** Nothing here has been exercised against a running
  Rails; the confirmer seam is what the tests drive.
* `PlayerDataCloneHandler` also repairs the pre-existing reward-delivery marker, which had the same
  respawn defect in the opposite direction (a lost marker there causes a *second* grant). That is a
  fix to M3 behaviour made necessary by the shared compound, and worth a line in the M3 notes.
* A `STRANDED` row needs an operator. It is logged at every boot with the transaction id and the
  exact proof; there is deliberately no automatic resolution, because every automatic option either
  pays twice or takes twice.
