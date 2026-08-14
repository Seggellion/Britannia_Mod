# UltimaCraft — Cashing a Cheque That Is Stored in the Bank Box

**Status:** Design, awaiting Rails implementation
**Owner decision (2026-08-04):** a cheque must be **stored in the vault first**, and cashed by
**double-clicking the stored cheque** in the Bank Box grid.
**Repos:** Rails (`ultimacraft-website`, branch `banking`) implements first; NeoForge
(`Britannia_Mod`, branch `banking`) follows.

---

## 1. Why this needs Rails at all

The NeoForge side already does what it can alone:

- **Depositing a cheque stores it** as an ordinary bank item (Milestone 17 gate corrective —
  this reversed ADR-016, which used to auto-cash any deposited cheque).
- **Double-clicking a cheque in the player's pack cashes it**, via
  `POST /api/banking/cheque/redeem` with the cheque's public id read from the item's own NBT.

The owner's requirement is the third case, and it is the one the mod cannot do by itself:
**cash a cheque that is sitting in the vault.** The obstruction is precise:

> A stored bank item's payload is an **opaque, Base64-encoded NBT blob** to Rails. The cheque's
> `public_id` lives *inside* that blob. Rails therefore has no way, today, to know that
> bank item `X` is cheque `Y` — and the mod cannot tell it at redemption time either, because
> reading the payload requires withdrawing the item first.

Everything below follows from closing that one gap.

### Why not do it client-side with the existing endpoints?

The mod could withdraw the item, decode it, call `cheque/redeem`, then confirm the withdrawal.
That sequence is **not atomic across two independent Rails operations**, and its failure modes
are exactly the ones this codebase refuses to ship:

- redeem succeeds, withdrawal confirm fails → the balance is credited *and* a now-worthless
  cheque stays in the vault;
- withdrawal confirms, redeem fails → **the item is destroyed and no value is credited.**

The second is real value loss. The fix belongs server-side, in one transaction.

### Why this is simpler than every other transfer

Every existing banking mutation is two-phase (prepare → confirm/cancel) for one reason: an item
or coins must **materialise in the player's Minecraft inventory**, which can fail (a full pack,
a crash mid-flight) after Rails has already committed. Prepare/confirm exists to make that
survivable.

**Stored-cheque redemption materialises nothing.** The vault row disappears and the balance
rises — both entirely inside Rails. There is no inventory step that can fail, so there is
nothing to reserve, nothing to confirm, and nothing to cancel. It is a **single-shot endpoint**,
exactly like the existing `cheque/redeem`, and it should be one database transaction.

---

## 2. What changes

Three changes, all **additive** (cross-repo rule 3: mixed client versions are permanent).

### 2.1 Deposit carries the cheque link (required first)

`POST /api/banking/deposit/prepare` currently sends, inside its `item` object:

```json
{
  "player_uuid": "…", "world_npc_public_id": "…", "idempotency_key": "…",
  "item": {
    "schema_version": 1, "payload": "<base64 NBT>", "fingerprint": "…", "weight": 1.0,
    "display_name": "Bank Cheque", "item_key": "britannia_mod:bank_cheque", "count": 1
  }
}
```

**Add one optional key** to `item`:

```json
"cheque_public_id": "e550b58d-34fa-49db-baa7-e245e5b41251"
```

Sent by the mod only when the deposited stack is a bank cheque. Rails persists it on the bank
item row (new nullable column, e.g. `bank_items.cheque_public_id`, indexed).

> ⚠️ **Ordering is not optional here.** The NeoForge client's own tests record that *"Rails
> refuses an envelope carrying an unknown key outright."* If the mod sent this key before Rails
> accepted it, **every cheque deposit would break**. This is precisely why cross-repo rule 2
> exists (Rails accepts before NeoForge sends), and why Rails ships first.

**Validation stance (decision for the Rails owner):** the recommendation is to store the link
*without* asserting the cheque's state or ownership at deposit time — storing a
already-redeemed cheque is just storing a worthless piece of paper, and the state that matters
is the state at *cash* time, checked in §2.2. Reject only a malformed UUID.

**On ownership:** do **not** add an owner check here. A cheque item is a bearer instrument —
players can hand one to another player — and **possession is the fact that the item is in that
account's vault.** The account that holds the bank item is the bearer, and is who gets credited.

### 2.2 New endpoint: `POST /api/banking/cheque/redeem_stored`

**Request**

```json
{
  "player_uuid": "…",
  "world_npc_public_id": "…",
  "bank_item_public_id": "…"
}
```

Note what is **absent**: no `cheque_public_id`. Rails derives the cheque from the bank item row
it stored in §2.1 and never from a client claim — the same "the client sends selection
references, never facts" rule the whole banking surface follows.

**Behaviour — one transaction, all-or-nothing**

1. Authenticate (`Minecraft-Server-Key` header + the standard request signature), resolve the
   player and the teller exactly as `cheque/redeem` does (shard/city/banking-mode checks
   included).
2. Load the bank item by public id **scoped to that player's account**. Missing or belonging to
   another account → `ITEM_NOT_FOUND`.
3. If the row has no `cheque_public_id` → `CHEQUE_NOT_FOUND` (see §4, legacy cheques).
4. Load the cheque. Apply the same state checks `cheque/redeem` already applies:
   already redeemed → `CHEQUE_ALREADY_REDEEMED`; cancelled → `CHEQUE_CANCELLED`; voided →
   `CHEQUE_VOIDED`; missing → `CHEQUE_NOT_FOUND`.
5. Credit the cheque's amount to the account **in the cheque's own denomination — no
   conversion, ever** (the owner's standing rule; cheques are denominated and cash to the equal
   amount). Exceeding the per-denomination ceiling → `BALANCE_CAPACITY_EXCEEDED`.
6. Mark the cheque redeemed, **delete the bank item row**, and recompute the account's
   `current_weight`.
7. Commit. Any failure rolls back the whole thing.

**Response** — the standard envelope, mirroring `cheque/redeem`'s:

```json
{
  "protocol_version": 1,
  "success": true,
  "retryable": false,
  "outcome": "cheque_redeemed",
  "bank_cheque": { "public_id": "…", "currency_key": "gold", "amount_coins": 500 },
  "bank_item": { "public_id": "…" }
}
```

`bank_cheque.public_id` is the only field the mod's parser will require (matching
`parseChequeRedeem`); the rest is useful for logging and a future "you cashed 500 gold" line.
Extra keys in the *response* are safe — the mod reads only what it needs.

**Envelope rules are strict and already enforced client-side.** The mod validates all of:
`protocol_version == 1`; HTTP status equals the outcome's expected status (`cheque_redeemed`
→ **200**; every rejection listed above → **422**; auth → 401/403; malformed → 400; unavailable
→ 503); `success == true` only for `cheque_redeemed`; `retryable == true` only for
`service_unavailable`. A mismatch is discarded as `inconsistent_protocol_envelope`, so a
well-meaning but off-spec envelope reads to the player as a transport failure.

**No new outcome values are needed.** Every outcome above already exists in the shared
vocabulary (`BankingTransferOutcome`), so this endpoint needs no NeoForge enum change.

**Replay/duplicate safety** comes free from state, as it does for `cheque/redeem`: a second
call finds the bank item gone (`ITEM_NOT_FOUND`) or the cheque already redeemed. No
`idempotency_key` is required (the existing single-shot `cheque/redeem` does not take one
either).

### 2.3 `banking/open` tells the client which stored items are cashable

The account snapshot's `bank_items[]` entries already carry `public_id`, `weight`,
`display_name`, `count`, `item_key`. **Add one optional boolean**, e.g.:

```json
"cheque_redeemable": true
```

True when the row has a `cheque_public_id` and the cheque is still redeemable; absent or false
otherwise. This lets the Bank Box enable the double-click **honestly** — offering the gesture
on a cheque that cannot be cashed, and failing after the click, is the kind of dishonest
affordance this epic has removed everywhere else.

A boolean rather than the id itself: the client does not need the id (the server derives it),
and not shipping it keeps a bearer instrument's identifier off the wire.

*(If checking redeemability per row proves expensive on large vaults, sending it purely from
column presence — link exists → true — is an acceptable simplification; the endpoint still
returns the honest outcome at cash time.)*

---

## 3. What NeoForge will do afterwards (not this work)

Recorded so the contract's other half is visible; **do not implement this in the Rails repo.**

1. Send `cheque_public_id` on deposit when the stack is a cheque.
2. Read `cheque_redeemable` in the account payload parser.
3. New endpoint entry + client + response parser for `cheque/redeem_stored`.
4. Move the double-click gesture onto the **bank grid** for redeemable stored cheques (the
   existing pack-side double-click stays — it is the only way to cash a cheque you are holding).
5. GameTests: stored cheque cashes and vanishes from the vault; a non-cheque bank item rejects;
   a legacy linkless cheque rejects cleanly; balance-ceiling refusal reads as its own message.

---

## 4. Migration: cheques already in vaults

Cheques stored **before** §2.1 ships have no `cheque_public_id`, and **cannot be backfilled** —
the payload is opaque to Rails, and only the mod could decode it (which requires withdrawing).

This is a clean, no-data-loss story rather than a problem:

> A legacy stored cheque simply reports `cheque_redeemable: false`. The player **withdraws it to
> their pack and double-clicks it there** — the Milestone 17 path, which keeps working forever
> and is also the only route for a cheque a player is carrying.

No migration script, no reconciliation, nothing to clean up. Worth one line in the endpoint's
own docs so the state is not mistaken for a bug later.

---

## 5. Tests Rails should carry

- happy path: stored cheque cashes — balance credited in the **cheque's own denomination**,
  cheque marked redeemed, bank item row gone, `current_weight` recomputed, `200` +
  `cheque_redeemed`;
- each refusal, each with its exact outcome **and** `422`: already redeemed, cancelled, voided,
  unknown/foreign `bank_item_public_id`, bank item with no cheque link, balance ceiling;
- **atomicity**: force a failure at step 5 or 6 and assert *nothing* moved — no credit, no
  redeemed flag, item still stored;
- replay: the same request twice credits exactly once;
- auth: missing/incorrect server key and signature → 401/403 with the standard envelope;
- deposit accepts `cheque_public_id` and persists it; deposit **without** it still succeeds
  unchanged (old clients);
- `banking/open` reports `cheque_redeemable` correctly for a linked, an unlinked, and an
  already-redeemed stored cheque.

---

## 6. Open questions for the Rails owner

1. **Deposit-time validation** — store the link unconditionally (recommended, §2.1), or reject
   a deposit whose cheque id does not resolve?
2. **`cheque_redeemable` cost** — real redeemability per row, or link-presence only (§2.3)?
3. **Does an existing model already associate cheques with bank items** in some way this design
   has not accounted for? The contract facts here were derived from the NeoForge client's
   serializers and parsers, not from reading the Rails code — **verify before building, and
   flag any discrepancy** rather than following this document off a cliff.
