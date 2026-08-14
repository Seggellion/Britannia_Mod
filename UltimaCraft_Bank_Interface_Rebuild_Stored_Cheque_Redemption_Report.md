# Stored cheque redemption — the NeoForge half

**Status:** Complete, awaiting owner review at the acceptance gate
**Baseline:** NeoForge `banking` @ `8fe19c9` (Milestone 17 + its gate corrective still uncommitted)
**Rails:** stored-redemption change live and migrated (`cheque_public_id`, `redeemed` status,
`POST /api/banking/cheque/redeem_stored`, `cheque_redeemable` on `banking/open`)

> **The boundary I flagged at the Milestone 17 gate is closed.** A cheque stored in the vault can
> now be cashed there — double-click it in the Bank Box grid — instead of having to be withdrawn
> to the pack first. This is the NeoForge half of the cross-repo change; Rails shipped first, as
> §1.1 rule 2 requires.

---

## 1. What was built

| Piece | Where |
| --- | --- |
| Envelope **v3** — the `cheque_public_id` link, gated like v2 | `BankItemEnvelopeVersion`, `BankingDepositPrepareRequest`, `BankingDepositClient`, `BankingDepositProxyService` |
| `cheque_redeemable` parsed, carried, and honoured | `BankingOpenResponseParser`, `BankItemSummary`, `BankAccountOpenedS2CPayload` |
| The new single-shot endpoint | `BankingStoredChequeRedemption{Request,ClientPort,Client,ProxyService}`, `RailsApiUrlResolver` |
| The packet and its handler | `BankStoredChequeRedemptionRequestC2SPayload`, `NetworkHandler`, `BankingTransferPacketService` |
| The vault-grid double-click and its tooltip | `BankBoxScreen`, `en_us.json` |

**Three cheque gestures now exist, one per row of the Rails doc's own table:** drag to the vault
stores; double-click in the *pack* cashes a carried cheque (Milestone 17's corrective, unchanged);
double-click in the *vault* cashes a stored one (this work).

## 2. The deposit link is version-gated, and that was not optional

Rails' `PayloadValidator` refuses an envelope carrying an unknown key. A build that sends
`cheque_public_id` at a Rails without it does not degrade — **every cheque deposit on that shard
is refused outright**. So the link rides the existing `BankItemEnvelopeVersion` ladder as
`V3_WITH_CHEQUE_LINK`, with the same compiled-in default of v1 and the same deliberate
per-deployment opt-in the identity fields have:

```
-Dbritannia.bank.item_envelope_version=3
```

Dev runs (`build.gradle`) move from 2 to 3; **a production build still defaults to v1 and must be
told**, exactly as before. This is the one operational action this change needs from the owner.

Worth naming: leaving it ungated would have been *worse* than for identity, not better. The key
is sent only on cheque deposits, so the blast radius is one item type — the kind of failure that
reaches production unnoticed because the smoke test deposits a diamond.

The id is read from the same capture snapshot the payload, fingerprint and weight came from, so
the link cannot name a cheque other than the one being stored. A cheque whose component is
missing or malformed still deposits perfectly; it simply stores as an unlinked row.

## 3. `cheque_redeemable` is three-valued on purpose

`null` (Rails said nothing — every ordinary item, and every cheque stored before the link),
`false` (Rails knows the cheque and it is spent/cancelled/voided/dangling), `true` (cashable now).
Only an explicit `true` offers the gesture. Absence and malformed both read as "no", via
`isChequeRedeemable()`, so a stale or hostile response can never light up an action that would
then fail — the dishonest affordance this epic has removed everywhere else.

The three states survive the wire independently (flagged optional, appended), because collapsing
"no" into "nothing" would lose the only distinction that can ever change.

## 4. The simplest flow in the package

Stored redemption **materialises nothing in any inventory**, so unlike every other banking
mutation it has no prepare/confirm, no capture, no revalidation, no receipt, no cancel — and
unlike even the pack-side redemption it delegates to, no item to dispose of or restore. What
remains is: resolve the teller fresh, dedupe per (player, row), dispatch, refresh on success.

The proxy deliberately pre-checks **nothing** about the row. It cannot: the cheque link lives in
Rails, and the client's `cheque_redeemable` is a possibly-stale snapshot used only to decide
whether to *offer* the gesture. Rails re-derives every fact under its own locks. A modified
client aiming the packet at an ordinary item gets `ITEM_NOT_FOUND`, not a misroute.

The response parser and result type are **reused verbatim** from the pack-side endpoint: same
envelope, same `bank_cheque.public_id`, and — per Rails' own doc — no new outcome string. The two
outcomes this path adds to the mapping (`ITEM_NOT_FOUND`, `BALANCE_CAPACITY_EXCEEDED`) already
had named kinds from Milestones 17 and 6b, so both were a one-line mapping each and the player
reads "that item is no longer here" / "thy account cannot hold that much" rather than a shrug.

## 5. Tests

```
New JUnit tests              6   (4 cheque-link semantics, 1 three-state wire round-trip,
                                  1 envelope-key emission)
Full JUnit suite           613 pass, 0 fail   (was 608)
New GameTests                6
All GameTests              328 run (was 322)
```

The GameTests drive the real packet handler with all four protocol clients faked at once, so each
asserts **exclusivity** — cashing a stored cheque must never touch the pack-side endpoint, the
item path or the currency path. Cases: the happy path (the *row's* id travels, never a grid
position; the account refreshes; no result payload, because the refresh is the signal); the row
already gone (`STORED_ITEM_UNAVAILABLE` — also the replay case, since this endpoint has no
idempotency token); a legacy unlinked row (`CHEQUE_NOT_FOUND`, the correct answer rather than a
bug); and the balance ceiling (`BALANCE_CAPACITY_EXCEEDED`, the guard that exists only on this
endpoint).

Envelope coverage: the link is written verbatim when present, omitted entirely when absent, and
an unlinked v3 envelope carries exactly the original four keys. The emission-default test was
generalised off its hardcoded `"2"` so it follows whatever the ladder's top version is.

Suite result: 328 run, 327 pass. The one failure is the pre-existing order-dependent world-state
bootstrap flake, unrelated to banking and unchanged by this work — the same single failure every
run has reported since Milestone 16 shifted the batch ordering.

---

## 6. Report

**Decisions made.** Version-gate the link as v3 rather than sending it unconditionally (§2); a
separate port and proxy service rather than a second method on the pack-side ones, so the two
flows' fakes stay independent and a test can prove one never touched the other; reuse the
pack-side parser and result type verbatim; three-valued `cheque_redeemable` with only `true`
offering the gesture; a separate double-click tracker per grid, so a pack click and a vault click
cannot complete one another's gesture.

**Tests run.** `./gradlew test` — 613 pass, 0 fail. `./gradlew runGameTestServer` — 328 run, 327
pass; the sole failure is the pre-existing order-dependent bootstrap flake (task already filed).

**Unresolved blockers.** None.

**Not performed / flagged.**
- **The owner must set `-Dbritannia.bank.item_envelope_version=3`** on any shard where stored
  cheques should become cashable. Until then cheques deposit and store correctly but arrive
  unlinked, and the vault offers no gesture on them.
- Rails' own report flags that `cheque/redeem` still answers **503** on an int32 balance overflow
  where `redeem_stored` answers a clean 422. That asymmetry is Rails-side and unchanged here; the
  mod maps whatever it is told, so a 503 reaches the player as a transport failure rather than as
  "thy account is full". Worth closing on the Rails side, the owner's call.
- Legacy stored cheques cannot be backfilled (Rails cannot decode the payload). They report
  `false` and the player withdraws then cashes from the pack — a supported route, not a defect.
- Milestone 18 (scaling/multiplayer/security matrices) and 19 (retirement) remain.

**Still uncommitted:** Milestone 17, its gate corrective, and this change are all in the working
tree awaiting authorization. They are three coherent commits if you want them separated.

---

## 7. Acceptance gate

Store a cheque in the vault, hover it (the tooltip names the gesture), double-click it: the row
leaves the vault and the balance rises in one refresh. Double-click an ordinary stored item and
nothing happens. Cash the same cheque twice quickly and the second attempt says the item is no
longer there rather than crediting twice.

**Worth trying in-game after setting the envelope flag:** deposit a fresh cheque, then cash it
from the vault; try a cheque deposited *before* the flag was set (it should offer no gesture, and
withdrawing then double-clicking it in the pack should still work).
