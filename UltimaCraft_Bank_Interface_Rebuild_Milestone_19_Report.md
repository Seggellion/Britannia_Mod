# Milestone 19 — Legacy retirement and final acceptance gate

**Status:** Retirement complete; the epic's final gate needs the owner's visual approval
**Baseline:** NeoForge `banking` @ `d32dba2`

> **The old screen is gone.** 679 lines deleted, one dead route removed, and nineteen stale
> references to it corrected. The epic now has exactly one banking interface.

---

## 1. What was removed

| Item (playbook's list) | Where it lived | Status |
| --- | --- | --- |
| Legacy combined Bank Screen layout | `client/gui/BankScreen.java`, 679 lines | **deleted** |
| Manual scrolling deposit row list | inside it | deleted with it |
| Manual scrolling withdrawal row list | inside it | deleted with it |
| Old Deposit/Withdraw/Checks button arrangement | inside it | deleted with it |
| Obsolete selection handlers | inside it | deleted with it |
| Obsolete rendering code | inside it | deleted with it |
| Legacy 400 ms double-click constants and fields | inside it | deleted with it — **see §2** |
| Unreachable screen routes | `ClientNetworkHandler.handleBankTransferResult`'s `instanceof` branch | **removed** |
| Superseded tests | — | none existed; no test ever targeted the legacy screen |

The result-handler is now three lines: record the outcome on the session and stop. It no longer
looks at what is on screen at all, because every rebuilt screen reads the outcome off the session
while rendering, and a result arriving after the player closed banking is dropped by the session
itself (design §5.3) rather than by a screen check.

**One oddity resolved for free:** the deleted file lived at `client/gui/` while declaring
`package com.seggellion.britannia_mod.client.screen`. That mismatch is gone with it.

## 2. The one item on the retirement list I did **not** remove

The playbook says to retire *"400 ms double-click constants and tracking fields"*, and design §2
forbids double-click selection. Both are satisfied: the legacy selection double-click is gone,
and single-click selection is what the Bank Box uses.

But `BankChequeDoubleClick` — a 400 ms double-click — **stays**, because the owner introduced it
deliberately at the Milestone 17 gate for a different job: cashing a cheque, in the pack and in
the vault. It is not selection; it is an explicit, destructive-ish action that wants a
deliberate gesture. Removing it because a retirement list mentions "400 ms" would delete a
feature that was requested three milestones after that list was written.

Recording this explicitly so a future reader does not "finish the job" by deleting it.

## 3. Stale references cleaned

Deleting a class leaves its name behind in comments, and those comments then lie. Nineteen
references remained; each was judged rather than blanket-replaced:

- **Corrected to past tense (12)** — anything claiming the class still does something: *"BankScreen
  renders this as a rejection today"*, *"does the label half of this today"*, *"real deposit
  triggers from BankScreen"*, *"Tells BankScreen how…"*, and the two test comments describing what
  it collapsed.
- **Left as history (5)** — the *"before this class existed, the client did X"* narratives in
  `ClientBankingSession`, `BankNavigation` and `ClientNetworkHandler`. Those explain why the
  current design exists and are worth more than the name costs.
- **Dangling doc pointers (2)** — *"see BankScreen's own class doc"* now points at nothing, so the
  claim was restated in place instead.

Also checked and clear: no orphaned helper classes (every import the deleted file used is still
used elsewhere), no orphaned textures (it referenced none — it drew from the shared dialogue
assets), and no orphaned translation keys (it used hard-coded `Component.literal` throughout,
which is precisely what `BankStatusPresenter` was built to replace).

---

## 4. Final validation

The playbook's confirmation list, each against automated coverage that already exists:

| Confirm | Evidence |
| --- | --- |
| Banker opens Main | `BankNavigationTest` — `OPEN_MAIN` on a genuine open |
| All three destinations open | `BankNavigationTest` — every `Destination` with a live session |
| Back returns to Main | `BankNavigationTest` + the three screens' `returnToMain` |
| Escape closes | session `close()` tests; Escape is never Back (design §5.2) |
| Bank Box grid works | `BankBoxLayoutTest`, `BankBoxSelectionTest`, grid geometry sweep |
| Inventory grid works | same sweep; `BankGridGeometry` exclusive-edge tests |
| Drag-to-deposit works | `BankDragControllerTest` + `BankDragDepositRoutingGameTests` |
| No double-click required | single-click selection (`BankBoxSelection`); cheque cashing is opt-in, §2 |
| Items, coins, cheques route correctly | `BankDragDepositRoutingGameTests` — six categories, exclusivity asserted |
| Stored item withdrawal works | `BankingTransferPacketServiceGameTests` full-path test |
| Currency withdrawal works | per-denomination tests, Milestone 16 |
| Deposit All Coins works | `BankingDepositAllCoinsProxyServiceGameTests` |
| Cheque issuance, all three denominations | `BankingChequeIssuanceProxyServiceGameTests` |
| Refreshed state is correct | refresh-flag tests, Milestone 17 |
| **No legacy route remains** | **this milestone — the class and its one route are gone** |
| Tests and smokes pass | tests below; smokes are the owner's (Milestone 18 matrix) |

---

## 5. Tests

```
Full JUnit suite           631 pass, 0 fail   (unchanged — nothing tested the deleted class)
All GameTests              333 run, 332 pass  (unchanged)
```

The unchanged counts are the point: **removing 679 lines changed no test outcome**, which is the
cleanest possible evidence that the class was genuinely unreachable. Had anything still depended
on it, the suites would have said so.

The single GameTest failure remains the pre-existing order-dependent world-state bootstrap flake,
unrelated to banking, with its task chip standing.

---

## 6. Milestone report

**Decisions made.** Keep `BankChequeDoubleClick` despite the retirement list's wording, and record
why (§2); correct stale comments rather than blanket-replace, keeping the historical narratives
that explain the design (§3); restate dangling doc pointers in place rather than delete the
sentence around them.

**Tests run.** `./gradlew test` — 631 pass, 0 fail. `./gradlew runGameTestServer` — 333 run, 332
pass (pre-existing flake).

**Unresolved blockers.** None.

**Scope not performed.** `src/main/java/.../registry/ClientOnlyItemRegistry.old` is a stray
non-banking file that predates this epic; out of scope, left alone. Drag-to-withdraw remains a
design §3 non-goal and a possible follow-on epic, with the target-slot question noted.

---

## 7. Final acceptance gate — what is left

The epic's code is complete. Two gates remain, both owner-side and neither closable from here:

1. **Milestone 18's matrices** — visual, interaction, multiplayer/latency, with screenshots and
   smoke notes. Document already prepared.
2. **Milestone 19's owner visual gate** — explicit approval of the Main, Balance, Create Cheque
   and Bank Box screens as final.

**Reminder for both:** the shard needs `-Dbritannia.bank.item_envelope_version=3`, or stored
cheques deposit unlinked and the vault offers no cashing gesture.

When those two are signed off, the epic closes.
