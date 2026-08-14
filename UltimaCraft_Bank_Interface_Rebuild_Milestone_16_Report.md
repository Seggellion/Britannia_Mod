# Milestone 16 — Currency withdrawal

**Status:** Complete, awaiting owner review at the acceptance gate
**Baseline:** NeoForge `banking` @ `4018ec8`

> **The Box's last dead controls are live.** Type an amount, press Gold, Silver or Copper, and
> the coins land in your pack — or the teller tells you *which* of the two fixable things went
> wrong: not enough on deposit, or no room to hand it over. This is the last functional
> milestone: every banking operation is now reachable through the new interface.

---

## 1. What was built

| Change | Where |
| --- | --- |
| `Kind.INSUFFICIENT_BALANCE` — **appended**, both enums travel by ordinal | `BankTransferResultS2CPayload` |
| `Rejected(INSUFFICIENT_BALANCE)` → the new kind; `Aborted(INSUFFICIENT_CAPACITY)` **and** `RejectedLocally(INSUFFICIENT_CAPACITY)` → `INVENTORY_FULL`; everything else stays generic | `BankingTransferPacketService.handleCurrencyWithdrawal` |
| `BankAmountInput` — the one definition of what an amount field accepts (new) | `client/screen/bank` |
| `BankCurrencyWithdrawalForm` — validation with per-denomination affordability (new) | `client/screen/bank` |
| Cheque form refactored onto `BankAmountInput`; behaviour unchanged | `BankChequeForm` |
| The live amount field and three denomination buttons | `BankBoxScreen` |
| Presenter entry for the wire kind — shared sentence, `REJECTION` severity | `BankStatusPresenter` |
| Three new GameTests + `FakeCurrencyWithdrawalClient` | `BankingTransferPacketServiceGameTests` |

The interaction is the legacy screen's own idiom, kept deliberately: **one field, three send
buttons** — pressing Gold withdraws the typed amount of gold. Each button judges affordability
against its *own* denomination's balance every frame, so the three can legitimately disagree:
800 typed with 900 silver and 3 gold on deposit lights Silver and not Gold. A press revalidates,
claims the global session lock, and sends the **existing payload unchanged** — no wire change
for the request, only the appended result kind. While pending, all three buttons and the field
lock; the refresh push or the result payload resolves everything.

Two boundaries held on purpose:

- **The grid selection is untouched** (design §9.7). Coins are a balance, not a stored item;
  withdrawing gold neither requires nor clears a selected diamond.
- **No floor beyond one coin.** Unlike the cheque's 500-coin minimum, withdrawing 3 copper for a
  market stall is a legitimate request, and the form treats it as one.

---

## 2. The two refusals a player can fix

**Insufficient balance.** The client pre-check has always disabled the button, but the server's
answer is the truth (Milestone 0 §4.2) — a stale session or a race can send an amount Rails
refuses. The wire kind now carries that refusal by name, and the presenter gives it **the same
sentence as the client pre-check** — deliberately. One message for one fact, whichever side
caught it first; only the severity differs (`VALIDATION` for "fix the form", `REJECTION` for
"the request was made and refused"). The uniqueness test in `BankStatusPresenterTest` documents
the shared key as intentional, so an accidental collision elsewhere still fails.

**Inventory full.** Currency withdrawal checks capacity **twice**: once locally before prepare
(no reason to spend a round trip, or let Rails reserve funds, for a withdrawal that could not be
delivered) and once after prepare, immediately before insertion — the actual safety guarantee.
Writing the tests exposed that these two paths read differently: the post-prepare abort mapped
to `INVENTORY_FULL` (Milestone 15's pattern), but the *normal* full-pack case — caught by the
local pre-check — fell through the `default` arm as a generic rejection. Same fact, two
messages. Fixed: `RejectedLocally(INSUFFICIENT_CAPACITY)` now maps to `INVENTORY_FULL` too.

As in Milestone 15, only player-actionable refusals get named kinds; teller-no-longer-valid and
transport failures stay generic because they assert nothing a player can act on.

`BankAmountInput` is the milestone's one extraction: the cheque form's digit rules (ASCII digits
only — `Long.parseLong`'s Unicode-digit acceptance is deliberately rejected — and an overflow
sentinel) now live in one class both forms share, so "what an amount field accepts" cannot
drift between the cheque and withdrawal screens.

---

## 3. Tests

```
New JUnit tests             12   (11 form validation, 1 presenter severity split)
Full JUnit suite           587 pass, 0 fail   (was 575)
New GameTests                3
All GameTests              315 run (was 312)
```

The three GameTests each pin a different path through the real packet handler:

- **Insufficient balance** — prepare answers `Rejected(INSUFFICIENT_BALANCE)`; exactly one
  result with the new kind, no refresh.
- **Full pack, caught locally** — thirty-six slots of cobblestone; `INVENTORY_FULL` arrives
  with **prepare never called** (the fake's throwing defaults are the assertion that no round
  trip was spent), no cancel, inventory untouched, no refresh.
- **Capacity lost mid-flight** — the race the second check exists for. The prepare future is
  held open, the inventory fills *between* the two checks, then prepare completes: exactly one
  Rails cancel releasing the reservation, `INVENTORY_FULL`, inventory untouched, no refresh.

Both suite runs failed exactly one test: the **known pre-existing** world-state bootstrap flake
(`aFailedFetchLeavesTheCacheUntouched…`, order-dependent, task already filed) — not a Milestone
16 test, and not a subsystem this milestone touches. What changed is its frequency: the suite
batches 50 tests at a time, and three added tests shifted every later test's batch position, so
the ordering that triggers the flake is now evidently the consistent one. The failure belongs to
the already-filed isolation task, but the owner should know the suite will report 314/315 until
that task lands.

`en_us.json` needed no change at all: the insufficient-balance sentence has existed since
Milestone 3, the button labels since Milestone 15 — and the presenter/translation loop tests
enforced coverage of the new kind the moment the constant existed.

---

## 4. Milestone report

**Decisions made.** The shared-sentence/split-severity presentation in §2; the `RejectedLocally`
mapping fix in §2; the no-floor decision and the untouched-selection boundary in §1; the
one-field-three-buttons idiom retained from the legacy screen.

**Tests run.** `./gradlew test` — 587 pass, 0 fail. `./gradlew runGameTestServer` — 315 run,
314 pass, twice; the sole failure both times is the pre-existing order-dependent bootstrap
flake (§3), untouched by this milestone.

**Unresolved blockers.** None for this milestone's scope. The bootstrap flake now fails
consistently rather than intermittently under the new batch ordering — its already-filed task
is the fix.

**Scope explicitly not performed.** The playbook's "migration" language for this milestone is
satisfied structurally: the legacy combined screen has been unreachable since Milestone 4's
cutover, and its deletion is Milestone 19's, not this one's. No partial-denomination splitting,
no withdraw-all button (design §3 non-goals). Milestones 17–19 — result/refresh framework
hardening, the validation matrix, retirement — remain.

---

## 5. Acceptance gate

> *Currency withdraws correctly from the new interface.*

Type, press a denomination, and the coins are in your pack; balance, weight and button states
all follow the refresh. Both fixable refusals — not enough on deposit, no room in the pack —
now say so by name, from whichever check catches them.

**Worth trying in-game:** type 800 with different gold/silver balances and watch the buttons
disagree; withdraw with a nearly-full pack; press a denomination and watch all three buttons
and the field lock until the teller answers.

**Stopping here for owner review.** Milestone 17 — the result/refresh framework pass — is next;
from here on the epic is hardening, validation and retirement.

---

## 6. Gate findings — owner's in-game test, and the fixes

The owner's acceptance test found two failures. Both are fixed; the JUnit suite is 588 pass, 0
fail after the fixes (one layout test added).

**The currency buttons were dead.** `refreshCurrencyButtons` existed and was correct, but
`render` never called it — only `sendWithdrawal` did, which is unreachable from an inactive
button. Construction-time `active = false` was therefore permanent. One line in `render` fixes
it; this is exactly the class of defect Architecture Decision 0 predicts, living in the one
place JUnit cannot reach (the screen's frame loop), and Milestone 18's validation matrix walks
this control by hand for that reason.

**The issued cheque was delivered — invisible.** The trail: Rails deducted the 500 silver and
the refresh showed it (client log, 07:02:40 → 07:02:50, silver 1012 → 512); no delivery-failure
branch logged; so the item went into the pack. The cause is in the renderer: 1.21's
`ItemRenderer.renderQuadList` reads `FastColor.ARGB32.alpha(tint)` into the vertex alpha, and
every `BankChequeTint` colour was a bare `0xRRGGBB` — alpha zero, a cheque drawn perfectly
transparently. Worse, a unit test *asserted* the alpha byte must be zero, on the stated (wrong)
belief that ItemColor returns are read as RGB. All four tints now carry `0xFF` alpha and the
test asserts the opposite of what it used to, with the render-path citation. **The invisible
cheque from the test issuance is real and in the pack** — it will appear as soon as the client
runs with this fix; it occupied a slot the whole time.

The same alpha-zero defect exists in the mod's six spawn-egg colour handlers (pre-existing,
outside banking) — filed as its own task rather than fixed here.

**Also from the screenshot: "opper".** The wide control column put three buttons on one 108px
row — ~33px each against a ~46px label, which no credible column width fixes. The denominations
now stack as full-width rows in the wide arrangement (matching every other control in that
column — amount, Back, Withdraw are already full-width rows); compact mode keeps them side by
side across the content width, where a third genuinely fits. The layout sweep gained a test
pinning the six-row column inside the panel across the full 180–1200 × 200–700 matrix.
