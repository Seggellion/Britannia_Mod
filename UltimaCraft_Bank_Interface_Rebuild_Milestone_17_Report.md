# Milestone 17 — Refresh, error, and reconciliation framework

**Status:** Complete, awaiting owner review at the acceptance gate
**Baseline:** NeoForge `banking` @ `8fe19c9`

> **No banking operation leaves the UI indefinitely pending or displaying fabricated state.**
> Every one of design §15's sixteen result categories now has a concrete, tested answer; silence
> itself gets named after ten seconds; a status message dies the moment it stops being true;
> and the oldest recorded wart in the epic — the interface re-opening uninvited after Escape —
> is closed with the one bit of information that was always missing.

---

## 1. The sixteen categories, audited

The milestone opened with an audit of design §15's list against what the wire could actually
say. Twelve categories were already carried (built incrementally, Milestones 3–16). The four
gaps, now closed:

| Category | What existed | What exists now |
| --- | --- | --- |
| Ineligible item | Local eligibility refusals (nested currency, quest-bound, foreign origin, over-deep nesting, oversized payload) all flattened to the generic rejection | `INELIGIBLE_ITEM`, its own diegetic line. `EMPTY_SLOT` deliberately stays generic — a stale view is not a verdict about an item |
| Insufficient bank capacity | Rails' `CAPACITY_EXCEEDED` (the vault's *weight* ceiling) fell through to generic | `BANK_CAPACITY_EXCEEDED` — the deposit-side sibling of `INVENTORY_FULL`, distinct from `BALANCE_CAPACITY_EXCEEDED` (the coin-count ceiling). Three ceilings, three sentences, tested distinct |
| Stored item no longer available | Rails' `ITEM_NOT_FOUND`/`ITEM_NOT_AVAILABLE` fell through to generic — the Milestone 18 concurrency case reading as "the teller refused you" | `STORED_ITEM_UNAVAILABLE`, presented as **informational** (like `NOTHING_TO_DEPOSIT`): nobody refused the player, nothing is theirs to fix, the refreshed grid is the answer |
| Connection/timeout uncertainty | Nothing — a request the server never answered stayed mute-pending forever | `BankStatusPresenter.UNCERTAIN`, client-side by nature (it describes the *absence* of an answer, so no wire kind can carry it) — see §2 |

All three new kinds were **appended** (both enums travel by ordinal). The presenter's switch is
exhaustive with no default, so the compiler itself demanded the new mappings, and the
presenter/translation loop tests enforced the English lines the moment the constants existed.

## 2. Pending always resolves — or the silence is named

`ClientBankingSession` now records when the lock was claimed. Past ten seconds (comfortably
beyond the server's own Rails transport timeouts) with no answer, `statusFor` shows the
UNCERTAIN status: *"The teller is taking longer than usual. Thy request may yet complete — thy
balance will show the truth of it."* The lock is **not** released — nothing client-side can
cancel an in-flight request (design §5.3) — only the silence is named; Escape remains available
throughout, and a result or refresh ends the uncertainty however late it comes. The clock is an
injectable seam, so all five timing cases are plain JUnit.

`BankStatusPresenter.statusFor(session)` is the milestone's centralization: one question for
the whole status line (result wins; then overdue silence; then nothing), asked identically by
all four screens.

## 3. The D9 wart, closed: no reopening after Escape

Since Milestone 4, `BankNavigation`'s docs have carried a named wart: a request still in flight
when the player pressed Escape produced a refresh that found no banking screen and **re-opened
Main** — the interface reappearing after being dismissed. The docs also named the missing
piece: the payload could not distinguish a first open from a late refresh.

It now can. `BankAccountOpenedS2CPayload` carries one appended bit, `refresh`, stamped by the
server — the only party that knows which flow built the payload (`OpenPurpose.OPEN` from the
teller interact; `REFRESH` from every post-mutation re-fetch). The route rule gains a third
outcome: banking on screen → keep current (both purposes); closed + genuine open → open Main;
closed + refresh → **discard entirely** — not even applied to a session, because that would
resurrect an interaction the player ended. The ordered connection makes this safe against
close-then-immediately-reopen: the late refresh (flagged) is dropped, the new open (unflagged)
opens.

The audit found exactly one production caller of each purpose, and two GameTests pin the flag
from both flows through the real packet paths.

## 4. Status lifetime — §15.2 complete

Three clearing rules; two already existed (`beginPending`, `applyAccountOpened`). The third —
*"navigates to a screen where the message is no longer relevant"* — is now
`BankNavigation.beginNavigation`, called by Main's three destinations and every sub-screen's
Back. A message describes the outcome of something done on the screen the player is leaving;
carrying it forward shows it beside controls it never referred to.

## 5. Logging without account data

`banking/open`'s result log printed the full `Success` record — balances and the complete item
list — on every open and every refresh (it is all over the owner's client log from the
Milestone 16 test session). It now logs outcome, item count, and purpose only. Failures carry
no account data and stay logged in full.

---

## 6. Tests

```
New JUnit tests             16   (5 watchdog, 3 statusFor, 3 new-kind presentation,
                                  4 navigation route/clearing, 1 refresh-flag codec round-trip)
Full JUnit suite           603 pass, 0 fail   (was 587)
New GameTests                5
All GameTests              320 run (was 315)
```

The five GameTests, each through the real packet handlers: a coin hidden in a shulker box
routes to the item path and reports `INELIGIBLE_ITEM` with **prepare provably never called**;
a Rails `CAPACITY_EXCEEDED` prepare rejection reports `BANK_CAPACITY_EXCEEDED` with the item
still in the slot; a Rails `ITEM_NOT_FOUND` reports `STORED_ITEM_UNAVAILABLE`; a confirmed
deposit's account push arrives flagged `refresh=true`; a player-initiated open arrives flagged
`refresh=false`.

The `AccountScreenSender` seam gained the flag via a defaulted five-argument method, so the
seventeen existing test lambdas compiled unchanged; only the production sender and the one test
that observes the flag implement the full form.

**Three existing GameTests carried the old flattening as an expectation** — the quest-bound
security test, the unknown-public-id withdrawal test, and the drag-routing suite's
container-of-coins and quest-bound cases asserted `CLEAN_REJECTION` where the mapping now sends
the named kind. Each test's actual claim (server-side gating, no Rails call, item untouched) is
unchanged; only the expected kind moved. This is the enforcement chain doing its job — the
suite refused to let the mapping change pass silently. One incidental find while fixing them:
a failed `check` inside the drag-routing rig's sequence tick crashes the whole GameTest server
rather than failing one test (vanilla `GameTestSequence` behaviour), which is why the first
Milestone 17 suite run died without a summary line.

---

## 7. Milestone report

**Decisions made.** Severity assignments (§1: ineligible and weight-capacity are REJECTION;
already-gone is INFORMATIONAL, by the NOTHING_TO_DEPOSIT precedent); `EMPTY_SLOT` stays
generic; the UNCERTAIN status holds the lock rather than releasing it (§2); a discarded late
refresh is not applied to any session (§3); navigation clears unconditionally rather than
per-operation-relevance (§4) — the simpler rule, and the honest one, since a result arriving
*while* the player is elsewhere still shows where they are.

**Tests run.** `./gradlew test` — 603 pass, 0 fail. `./gradlew runGameTestServer` — 320 run,
319 pass; the sole failure is the pre-existing order-dependent bootstrap flake (its task
stands; it fails consistently under the shifted batch ordering, as it has since Milestone 16).

**Unresolved blockers.** None.

**Scope explicitly not performed.** No revision/sequence discriminator (D12 stands — the
ordered connection plus whole-snapshot pushes make last-write-wins correct; Milestone 18's
multiplayer matrix is where that assumption gets its live test). The refresh-fetch-failure
chat message ("the bank is unavailable") after a *successful* mutation is unchanged — the
watchdog now covers the player-facing gap, and rewording it is cosmetic. Milestones 18–19
remain: the validation matrices, and retirement.

---

## 8. Acceptance gate

> *No banking operation leaves the UI indefinitely pending or displaying fabricated state.*

Every request now ends in exactly one of: a refresh (success), a named result, a named
uncertainty, or — if the player closed banking first — a deliberate, tested discard. No path
leaves the lock mute forever, and no path invents state the server did not send.

**Worth trying in-game:** deposit a shulker box with coins inside it (the teller now refuses it
by name); press Escape while a deposit is in flight and watch the interface *stay closed* when
the confirm lands (the wart this milestone closes); trigger a rejection on the Box, press Back,
and watch the message not follow you to Main.

**Stopping here for owner review.** Milestone 18 — scaling, multiplayer, and security
validation — is next; it is matrix work (visual, interaction, latency, adversarial) with the
first live-shard end-to-end smokes.

---

## 9. Gate corrective: cheques store; double-click cashes

**The defect (owner, at the gate):** depositing a cheque auto-cashed it. ADR-016 had made
redemption deposit-shaped — any cheque reaching the deposit packet was redeemed — which made
storing a cheque in the Bank Box impossible. The owner overrode ADR-016: players must be able
to keep cheques in bank boxes, and cashing must be deliberate.

**The split.** Intent can no longer be inferred server-side from the slot's contents, so it
travels in the packet:

- **Drag a cheque to the vault → it is STORED**, an ordinary item deposit. The deposit router
  lost its cheque branch; a cheque passes eligibility like anything else (no carve-out
  existed), serializes with its `BankChequeData`, and comes back intact on withdrawal — the
  redemption proxy suite already proves a restored cheque keeps its id.
- **Double-click a cheque in the pack grid → it is CASHED**, via the new
  `BankChequeRedemptionRequestC2SPayload` — same trust model as the deposit packet word for
  word (selection references only; the proxy re-reads the live slot and cleanly rejects a
  non-cheque, so a modified client aiming the packet at a diamond disposes nothing — pinned by
  a new GameTest).

The double-click is the epic's one deliberate reintroduction of the gesture it removed: not as
selection (design §2's complaint), but as an explicit action, in the legacy screen's own 400ms
window. `BankChequeDoubleClick` is plain and tested — same-slot-within-window fires, a drag
past the 4px threshold resets it (so store-then-press cannot cash a cheque the player meant to
keep), and a triple-click is one cashing, not two. The completing press is consumed before the
drag arms; a fired redemption claims the session lock before the packet like every mutation.
The pack tooltip on a cheque now names both gestures.

**Rails: no change.** Storing a cheque is the existing item-deposit contract; cashing is the
existing redemption contract. Nothing new crosses the wire to Rails.

**Boundary worth naming.** A cheque stored in the vault is withdrawn like any item (select,
Withdraw) and then cashed from the pack. There is no direct cash-from-vault gesture — that
would need an atomic withdraw-and-redeem flow on Rails that does not exist. If wanted, it is
its own cross-repo milestone (Rails first, per §1.1 rule 2).

**Tests.** JUnit 608 pass (+5, the double-click timing matrix). The routing suite's cheque
test now asserts the exact opposite of what it did — `aDepositedChequeIsStoredNeverAutoCashed`
— and two GameTests were added (double-click's packet cashes with the real cheque id; the
packet aimed at a non-cheque rejects cleanly). The four cheque terminal states and the
reconciliation case ride the new packet unchanged. GameTests: 322 run, 321 pass; the sole
failure remains the pre-existing bootstrap flake.
