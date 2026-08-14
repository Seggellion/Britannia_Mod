# Milestone 14 — Special deposit routing and cheque redemption

**Status:** Complete, awaiting owner review at the acceptance gate
**Baseline:** NeoForge `banking` @ `3590ec9`

> **Verification, as designed — and it caught something.** The milestone existed to prove the drag
> changed only the UI interaction, never the routing. Twelve new GameTests prove exactly that,
> and one of them found a real defect on the way: a cheque redemption ending in
> reconciliation-required was being softened to an ordinary clean rejection. Fixed, one line.

---

## 1. What was built

One new GameTest file — `BankDragDepositRoutingGameTests`, 12 tests — and a one-line fix in
`BankingTransferPacketService.chequeRedemptionKindFor`.

Every test sends **the exact packet a drag release sends** — `BankDepositRequestC2SPayload(teller,
slot)` through the real `handleDeposit` — with **all three protocol clients faked at once**. That
simultaneity is the point: each category asserts *exclusivity*, not just that the right protocol
was engaged but that the other two were never touched. Exclusivity is what a routing bug actually
looks like, and it is invisible from inside any one protocol's own suite.

---

## 2. The seven categories

| # | Category | Proven |
| --- | --- | --- |
| 1 | Ordinary bankable item | Item protocol engaged once; currency and redemption silent; confirm refreshes; no result payload — the refresh is the signal |
| 2 | Loose gold stack | Currency protocol, key `gold`, exact live count; item and redemption silent |
| 3 | Loose silver stack | Same, key `silver` |
| 4 | Loose copper stack | Same, key `copper` |
| 5 | Bank cheque | Redemption engaged once with the **real cheque id** from the component; never stored, never currency; confirm refreshes |
| 6 | Container holding coins | **No protocol's wire touched at all** — top-level identity routes it to the item path, whose CURRENCY carve-out rejects it locally; one clean rejection; the shulker stays with the player |
| 7 | Ineligible (quest-bound) item | Same shape — rejected before any Rails call, nothing else touched, item stays |

Category 6 is design §10.8's rule in three assertions: a container never becomes a balance merely
because of its contents. Category 7's fixture self-checks — if the quest-stamp key ever changes,
the test fails with "fixture error" rather than passing vacuously against an eligible diamond.

---

## 3. The cheque terminal states — and the defect

Six more tests drive a cheque drop into each terminal state Rails can answer:

| Rails outcome | Reaches the client as |
| --- | --- |
| `CHEQUE_NOT_FOUND` | its own kind ✓ |
| `CHEQUE_ALREADY_REDEEMED` | its own kind ✓ |
| `CHEQUE_CANCELLED` | its own kind ✓ |
| `CHEQUE_VOIDED` | its own kind ✓ |
| successful redemption | refresh push, no result payload ✓ |
| `RECONCILIATION_REQUIRED` | **was `CLEAN_REJECTION` — now its own kind** |

The defect: redemption is single-shot (no separate confirm), so Rails' reconciliation outcome
arrives inside an ordinary `Rejected`, and the kind mapping's `default` arm swallowed it into
`CLEAN_REJECTION`. By that point the physical cheque is already disposed — so a player whose
redemption genuinely needs staff attention would have read *"I'm afraid I can't complete that
right now"*, shrugged, and walked away from the one outcome that must not be shrugged at. The fix
maps it to `RECONCILIATION_REQUIRED`, which the Box's status line already renders at full
severity — bold, wide marker, distinct colour.

This is precisely what a verification milestone is for. The mapping predates the epic (Milestone
11 Slice 2 of the identity program); the drag did not cause it, but the drag made cheque-dropping
a primary gesture, so it would have been hit.

---

## 4. Tests

```
BankDragDepositRoutingGameTests   12 tests   (new)
All GameTests                    311 run, 311 pass
Full JUnit suite                 575 pass, 0 fail   (unchanged)
```

**A note on the 311/311:** the world-state bootstrap test — failing consistently since Milestone
2, proven pre-existing against `f4daef9` — *passed* this run. The new tests changed batch
ordering, which points at order-dependence rather than a genuine fix. The separate task for it
stands; I would not call it healed on one green run.

One rig subtlety worth recording: `refreshAccount` re-runs `bank.open`'s real fetch, so the rig
fakes the open client too — the first run stalled on a network call that could never answer, and
"refreshed" never fired. The existing packet-service suite does the same; the rig now matches it.

---

## 5. Milestone report

**Decisions made.** None of substance — this milestone verifies decisions made elsewhere. The one
code change closes a gap between the design's stated requirement ("cheque-specific failures remain
distinct", reconciliation among the terminal states) and the code.

**Tests run.** `./gradlew runGameTestServer` — 311 run, 311 pass. `./gradlew test` — 575 pass.

**Unresolved blockers.** None.

**Scope explicitly not performed.** No UI change, no packet change, no new behaviour beyond the
mapping fix. Withdrawal (15) and currency withdrawal (16) remain the Box's dead controls.

---

## 6. Acceptance gate

> *The new drag gesture changes only the UI interaction, not the established banking-domain
> routing.*

Proven category by category, with exclusivity, through the real handler, against the real routing
code — plus one place where the established behaviour itself fell short of the design, found and
corrected under this milestone's own mandate.

**Stopping here for owner review.** Milestone 15 — stored-item withdrawal — is next: the selection
the grid already holds becomes a live Withdraw button, and it carries the inventory-full `Kind`
assigned to it back at Milestone 1.
