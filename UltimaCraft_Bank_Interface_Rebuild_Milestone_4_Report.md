# Milestone 4 — Bank Main Screen

**Status:** Complete, awaiting owner review at the acceptance gate
**Architecture:** `UltimaCraft_Bank_Interface_Rebuild_Milestone_1_Architecture.md` (approved 2026-08-03)
**Baseline:** NeoForge `banking` @ `59f8127`

> **This is the cutover milestone.** A banker interaction now opens `BankMainScreen`, and the
> legacy `BankScreen` is no longer constructed anywhere. §3 below is the rollout consequence the
> acceptance gate asks you to approve, and it is the part worth reading first.

---

## 1. What was built

| File | Role |
| --- | --- |
| `client/screen/BankMainScreen.java` | The hub: portrait, greeting, three destinations |
| `client/screen/BankPlaceholderScreen.java` | Clearly-marked stand-in for Bank Box and Balance |
| `client/screen/bank/BankNavigation.java` | Routing rules, kept testable |
| `client/screen/bank/BankingScreen.java` | Marker: "this screen is part of banking" |
| `client/screen/bank/BankNavigationTest.java` | 12 tests |

Changed: `ClientNetworkHandler` (the cutover), `BankChequeIssuanceScreen` (Cancel now returns to
Main, not to the legacy screen), `en_us.json` (12 keys), `BankTranslationKeysTest` (+1 test).

**The Main screen owns no account state.** Every value it draws is read from
`ClientBankingSession` inside `render`, so a refresh push that lands while it is open just changes
what the next frame shows. That is Milestone 2 paying off: the screen never has to be rebuilt to
display fresh data, and never has to be told the data changed.

---

## 2. The cutover

```java
// before — every refresh rebuilt the screen
Minecraft.getInstance().setScreen(new BankScreen(payload));

// after
ClientBankingSession.applyAccountOpened(payload);
if (BankNavigation.refreshRoute(minecraft.screen instanceof BankingScreen) == OPEN_MAIN) {
    minecraft.setScreen(new BankMainScreen());
}
```

The rule lives in `BankNavigation.refreshRoute` rather than in the handler because
`ClientNetworkHandler` reaches for `Minecraft.getInstance()` and cannot run in JUnit. It is the
most consequential rule in the epic; it should not be the one thing verified only by playing.

**`BankScreen` is now unreachable.** `grep -rn 'new BankScreen('` across `src/` returns only
comments. One dead branch remains in `handleBankTransferResult` — an `instanceof BankScreen` that
can no longer match — kept and commented so Milestone 19's retirement is one removal rather than
two.

**One behaviour deliberately preserved rather than improved.** If a request is in flight when the
player presses Escape, the refresh that follows finds no banking screen and re-opens Main — the
interface reappears after they dismissed it. That is exactly what the legacy handler did (it
called `setScreen` unconditionally), so Playbook §1 rule 3 says preserve it. Fixing it properly
requires telling a first `bank.open` apart from a late refresh, which this payload does not allow;
design §5.3 and Milestone 17 own that problem.

---

## 3. Rollout consequence — the decision this gate asks for

> *Acceptance gate: the banker interaction opens the Main Screen as the new hub, **subject to
> owner-approved rollout strategy**.*

That clause matters here, because the cutover changes what a player on this branch can do:

| Destination | State after this milestone | Restored at |
| --- | --- | --- |
| Create Cheque | **Works.** Routed to the real `BankChequeIssuanceScreen` | — |
| Balance | Placeholder | Milestone 5 |
| Open Bank Box | Placeholder | Milestone 9 (usable through 16) |

**Item deposit, item withdrawal and currency withdrawal are unreachable from the UI until
Milestones 13–16.** The server services are untouched and fully tested; there is simply no screen
that calls them.

That gap is inherent to the playbook's own sequence — Milestone 4 builds the hub, Milestones 9–16
build the Bank Box — and Milestone 4 explicitly permits placeholders "until their milestones are
implemented". Design §3's non-goals also forbid keeping the old combined screen as an alternate
interface, so running both was not on the table.

I routed Create Cheque to the real screen specifically to avoid widening the gap: cheque issuance
already worked end to end, and pointing it at a placeholder would have taken a working feature
away from players for three milestones to no purpose.

**If a playable branch matters more than sequence purity**, the alternative is to point the Bank
Box placeholder at the legacy `BankScreen` temporarily. I did not do this: it would keep two
banking interfaces live at once, which Milestone 1's D14 explicitly rejected, and it makes
Milestone 19's "no legacy route remains" harder to verify. **Say so if you would rather have the
branch fully playable throughout, and I will wire it that way** — it is a small change and the
decision is yours, not mine.

---

## 4. Playbook requirements

| Required | Status |
| --- | --- |
| banker portrait and name on the left | `BankDialogueFrame.renderHeader` |
| greeting in the centre | Translatable, names the city when the account has one |
| Open Bank Box, Balance, Create Cheque on the right | Three `BankActionButton`s |
| each opens its destination | §3 |
| destination screens receive the active banking session | They read the singleton; nothing is passed |
| Escape closes banking | `onClose` → `ClientBankingSession.close()` + `setScreen(null)` |
| no legacy ledger or transaction controls remain | No balances, weight, item lists or amount fields |

| Required test | Where |
| --- | --- |
| each button routes correctly | `BankNavigationTest` destination coverage |
| banker/account context preserved | `navigationPreservesBankerAndAccountContext` |
| Escape closes | Owner visual gate — `onClose` is three lines and unreachable from JUnit |
| repeated clicks do not stack duplicate screens | Structurally impossible: `setScreen` replaces |
| old combined controls absent | `grep` for `new BankScreen(` returns nothing |

Two of those are answered by construction rather than by a test, and both are stated plainly
rather than papered over. `setScreen` replaces the mounted screen, so a double-click cannot stack
duplicates; and "old controls absent" is a property of a file that no longer exists in the flow,
which a test cannot assert more convincingly than the grep does.

---

## 5. Tests

```
BankNavigationTest          12 tests   (new)
BankTranslationKeysTest      5 tests   (+1)
Full JUnit suite           457 tests, 0 failures, 0 errors   (was 444)
```

`BankTranslationKeysTest` now covers Milestone 4's keys too, including a check that the two
greetings carry the right number of `%s` arguments — a mismatch there throws inside
`Component.translatable` at render time, not at compile time.

GameTests not re-run: client-only change, and the suite's single failure is already proven
pre-existing (Milestone 2 §5).

**Not covered, by decision:** the screens themselves. Architecture Decision 0 — neither harness can
instantiate a `Screen`. Everything they delegate is tested; what remains is appearance and feel,
which is design §19.4's owner gate.

---

## 6. Milestone report

**Files added.** `BankMainScreen.java`, `BankPlaceholderScreen.java`, `BankNavigation.java`,
`BankingScreen.java`, `BankNavigationTest.java`.

**Files changed.** `ClientNetworkHandler.java`, `BankChequeIssuanceScreen.java`, `en_us.json`,
`BankTranslationKeysTest.java`.

**Decisions made, all within Milestone 1's architecture.**
- Create Cheque routes to the real screen, not a placeholder (§3).
- `BankChequeIssuanceScreen` does **not** implement `BankingScreen` yet. It still renders balances
  from the payload captured in its constructor, so leaving it mounted across a refresh would show
  stale numbers immediately after the player changed them. Being replaced by a freshly-read Main
  screen is more honest, and matches today's behaviour. It joins at Milestone 7.
- Navigation stays available while a mutation is pending. Navigating is not mutating; the session
  carries the lock across screens, and blocking it would strand the player until the server
  answered.

**Tests run.** `./gradlew test` — 457 pass, 0 fail.

**Unresolved blockers.** None. One decision requested: §3's rollout strategy.

**Scope explicitly not performed.**
- No Balance screen (Milestone 5), no Bank Box (Milestone 9), no cheque rebuild (Milestone 7).
- `BankScreen.java` still exists on disk, unreachable. Deleting it is Milestone 19's.
- The dead `instanceof BankScreen` branch in `handleBankTransferResult` was commented, not removed
  — same reason.
- `BankChequeIssuanceScreen`'s Escape still acts as Back, which design §5.2 forbids. Pre-existing;
  Milestone 7 owns it. Only its Cancel *routing* was changed here, to close the legacy route.

---

## 7. Acceptance gate

> *The banker interaction opens the Main Screen as the new hub, subject to owner-approved rollout
> strategy.*

It does: `handleBankAccountOpened` constructs `BankMainScreen`, and nothing constructs
`BankScreen`. The hub carries the required layout and behaviour, and the routing rules are tested.

Playbook §3.1: **all user-visible strings introduced by this milestone are translatable**, enforced
by `BankTranslationKeysTest`.

**Two things for the owner:**

1. **§3's rollout strategy** — accept the temporary gap in Bank Box and Balance, or have me point
   the Bank Box placeholder at the legacy screen to keep the branch fully playable.
2. **First visual confirmation of the Milestone 3 dialogue frame.** This is the first milestone
   with something to look at. If the parchment, portrait placement or button column is wrong, the
   fix belongs in `BankDialogueFrame`/`BankDialogueLayout` rather than in this screen.

**Stopping here for owner review.**
