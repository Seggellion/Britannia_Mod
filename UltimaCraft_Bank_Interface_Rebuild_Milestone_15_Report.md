# Milestone 15 — Stored-item withdrawal

**Status:** Complete, awaiting owner review at the acceptance gate
**Baseline:** NeoForge `banking` @ `cbe96ff`

> **The vault gives things back.** Single-click a stored item, press Withdraw, and it lands in
> your pack — or the teller tells you *specifically* that your pack is full, which is this
> milestone's second half: the inventory-full `Kind` assigned to it back at Milestone 1, closing
> the epic's oldest recorded defect (Milestone 0 §3.4).

---

## 1. What was built

| Change | Where |
| --- | --- |
| `Kind.INVENTORY_FULL` — **appended**, both enums travel by ordinal | `BankTransferResultS2CPayload` |
| `Aborted(INSUFFICIENT_CAPACITY)` → `INVENTORY_FULL`; other abort reasons stay generic | `BankingTransferPacketService.handleWithdrawal` |
| Presenter entry, `REJECTION` severity, own diegetic line | `BankStatusPresenter`, `en_us.json` |
| The live Withdraw button | `BankBoxScreen` |
| `inventoryFullWithdrawalReportsItsOwnKindAndCancelsTheReservation` | `BankingTransferPacketServiceGameTests` |

The interaction is the playbook's seven steps, most of them already existing: single-click
selection (Milestone 11), the selected ring, **Withdraw active exactly when something is selected
and nothing is pending**, one press claiming the session lock before one packet carrying the
selection's **public id — never a grid position** (§9.6), pending state on the button, and the
refresh push resolving everything.

**The duplicate-press question answers itself structurally.** On success the refresh removes the
item from the account, the session drops the now-stale selection (tested since Milestone 2), and
the button deactivates — by the time the lock releases there is nothing selected to re-send. On
failure the lock releases with the selection intact, and pressing again is a legitimate retry —
which is exactly what a player with a full inventory does after dropping some cobblestone.

---

## 2. The inventory-full kind — the §3.4 finding, closed

Milestone 0 found the server side of this case **impeccable** — capacity pre-checked before any
receipt is written, Rails' reservation cancelled, nothing created, nothing lost, nothing dropped
on the ground — and then reported as the generic *"I'm afraid I can't complete that right now"*,
indistinguishable from a dead teller or a stale item. The most actionable rejection in the whole
system read as a shrug.

Now: *"The teller looks over the counter. 'Thy pack is full — make room, and I shall hand it
over.'"*

Two scoping decisions inside the fix:

- **Only `INSUFFICIENT_CAPACITY` gets the new kind.** The other abort reasons — decode failure,
  fingerprint mismatch, teller-no-longer-valid — stay generic: they assert nothing a player can
  act on, and naming them would leak mechanism without adding action.
- **Appended, never inserted.** Both enums encode by ordinal; the constraint is already written
  into the payload's class docs from Milestone 6b, and this follows it.

The GameTest drives the real path — thirty-six slots of full cobblestone, a real serialized
diamond from prepare, the **real** `hasSufficientCapacity` arithmetic refusing (no stubbed
verdicts) — and asserts all four consequences: the kind, the operation, exactly one Rails cancel
releasing the reservation, and an untouched inventory.

---

## 3. Tests

```
New GameTest                 1   (the full real-path inventory-full case)
All GameTests              312 run, 312 pass
Full JUnit suite           575 pass, 0 fail   (unchanged in count — see below)
```

The unchanged JUnit count is itself the system working: `BankStatusPresenterTest` loops every
`(Operation, Kind)` pair and `BankTranslationKeysTest` loops every kind against `en_us.json`, so
`INVENTORY_FULL` was covered — and its English string *enforced* — the moment the constant
existed. A missing translation would have failed the build.

The playbook's remaining cases were already pinned: successful withdrawal and delivery
(`fullWithdrawalThroughRealPacketPath…`), stale/unknown public id, duplicate dedup through the
real handler, close-during-pending (session), weight refresh (the push), and the full adversarial
inventory battery in `BankingWithdrawalProxyServiceGameTests`. The bootstrap flake passed again
this run; its task stands.

---

## 4. Milestone report

**Decisions made.** The two scopings in §2, and the retry-by-design analysis in §1.

**Tests run.** `./gradlew runGameTestServer` — 312 run, 312 pass. `./gradlew test` — 575 pass.

**Unresolved blockers.** None.

**Scope explicitly not performed.** Currency withdrawal — the Box's last dead controls — is
Milestone 16. No partial-stack withdrawal, no drag-to-withdraw (design §3 non-goals).

---

## 5. Acceptance gate

> *Stored items withdraw correctly from the new grid.*

Click, Withdraw, and it is in your pack; vault, weight and button state all follow the refresh.
With a full pack, the one rejection a player can actually fix tells them how to fix it.

**Worth trying in-game:** withdraw the last item (selection clears, button deactivates); withdraw
with a full pack, drop something, press again; and select-then-scroll — the ring follows the item,
not the cell.

**Stopping here for owner review.** Milestone 16 — currency withdrawal migration — is next and is
the last functional milestone: after it, every banking operation is reachable through the new
interface, and 17–19 are hardening, validation and retirement.
