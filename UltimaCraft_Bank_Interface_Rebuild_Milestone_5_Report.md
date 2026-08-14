# Milestone 5 — Bank Balance Screen presentation

**Status:** Complete, awaiting owner review at the acceptance gate
**Architecture:** `UltimaCraft_Bank_Interface_Rebuild_Milestone_1_Architecture.md` (approved 2026-08-03)
**Baseline:** NeoForge `banking` @ `8030ef6`

---

## 1. What was built

| File | Role |
| --- | --- |
| `client/screen/BankBalanceScreen.java` | Portrait, balances, Back, Deposit All Coins |
| `client/screen/bank/BankBalanceCopy.java` | Plural selection, sentence choice, number formatting |
| `client/screen/bank/BankBalanceCopyTest.java` | 11 tests |

Changed: `BankMainScreen` (Balance routes to the real screen), `BankPlaceholderScreen` (the Balance
factory removed), `en_us.json` (+13 keys, −2 retired), `BankTranslationKeysTest` (+2 tests).

**The Balance placeholder is fully retired**, not left lying around: the factory method, both
translation keys, and the route are all gone, and a test asserts the strings no longer exist so
the next person editing `en_us.json` does not mistake them for a live feature. `BankPlaceholderScreen`
now has one factory left and is deleted outright at Milestone 9.

---

## 2. The extractable part is the wording

Almost nothing on this screen is logic — it reads three integers and says them. What *is* worth
testing is the copy, and it is where read-only screens usually go wrong:

- **Plural selection.** Minecraft has no plural rule; the caller picks the key. `amountKey` uses
  the singular for exactly one, which means **zero takes the plural** — correct English ("0 gold
  coins") and the case a naive `n < 2` check gets wrong.
- **Which sentence.** An account holding nothing gets its own line rather than "0 gold coins, 0
  silver coins, and 0 copper coins", which is accurate and reads like a malfunction.
- **Number formatting.** `formatAmount` groups digits, so a seven-figure copper balance is
  readable rather than a run of digits. Pinned to `Locale.ROOT` with a test that survives the JVM
  default being changed — `en_us.json`'s strings are written against that grouping, and a client
  running under a comma-decimal locale must not silently render `1.234.567`.

---

## 3. A correctness gap found and closed during the milestone

The layout is computed in `init` from the wrapped height of the balance sentence. But balances
change on refresh, and a sentence that grows from two wrapped lines to three would then be drawn
against centring calculated for two.

`render` now recomputes the layout when the sentence actually changes — not every frame, so
`font.split` is not run sixty times a second to get the same answer. Button positions are
unaffected either way: `BankDialogueLayout` derives them from the screen width and button count
alone, never from the body's line count.

This is the first screen where "text updates when refreshed session state changes" has anything to
update, so it is the first place the gap could appear. `BankMainScreen`'s greeting does not vary
within a session and needs no equivalent.

---

## 4. Playbook requirements

| Required layout | Status |
| --- | --- |
| portrait and name | `BankDialogueFrame.renderHeader` |
| dialogue showing Gold, Silver, Copper | One diegetic sentence, highest denomination first |
| Back | Button 0 |
| Deposit All Coins | Button 1, disabled |

| Required behaviour | Status |
| --- | --- |
| Back returns to Bank Main | `returnToMain()` |
| Escape closes banking | `onClose()` → session closed, screen dismissed |
| balances come from the shared session | Read in `render`; the screen caches no numbers |
| Deposit All Coins may remain disabled until Milestone 6 | Disabled, **with the reason stated** |
| text updates when refreshed session state changes | §3 |

| Required test | Where |
| --- | --- |
| all three balances render correctly | `BankBalanceCopyTest` + `BankTranslationKeysTest` argument counts |
| singular/plural wording | `usesTheSingularOnlyForExactlyOne`, `usesThePluralForZero`, `usesThePluralForEverythingAboveOne` |
| Back and Escape differ correctly | **Owner visual gate** — see below |
| large values do not overlap buttons | By construction — see below |
| disabled placeholder is clear before integration | `marksTheDepositAllNoticeAsInformationalNotAFailure` + the status line |

**Two of those are answered by construction rather than by a test, and I would rather say so than
imply coverage that does not exist.**

*Back versus Escape* is four lines in two methods on a `Screen`, which neither harness can
instantiate (Architecture Decision 0). `Back` calls `setScreen(new BankMainScreen())` and leaves
the session alone; `onClose` calls `ClientBankingSession.close()` first. The session half of that
distinction **is** tested — `ClientBankingSessionTest` covers close, and that reopening builds a
fresh session rather than resurrecting the old one — but that the Escape key reaches `onClose` is
for the visual gate.

*Large values not overlapping buttons* cannot be tested more strongly than it already is without a
`Font`: measuring the rendered sentence needs one. What holds instead is stronger than a
spot-check — `BankDialogueLayout` guarantees `bodyRight() <= buttonX()` at **every** width from 120
to 2000, `drawWordWrap` wraps at exactly that width, and `formatAmount` keeps even
`Integer.MAX_VALUE` to thirteen characters. Horizontal overlap is not reachable; vertical overflow
is covered by `keepsAVeryLongBodyOnScreenRatherThanPushingItOffTheTop`.

---

## 5. Deposit All Coins

Present, disabled, and it says why: *"The teller cannot yet take a purse all at once. Deposit
coins one stack at a time for now."*

Playbook Milestone 5 permits the button to stay inert until Milestone 6 and asks that the
placeholder be clear. A greyed button on its own tells a player "not now" without telling them
whether that is permanent, a bug, or something about their account — so the notice fills the
status line whenever there is no real outcome to show. A genuine result always wins the slot.

It is rendered `INFORMATIONAL`, not as a rejection: nothing failed, and the teller has not refused
the player. `BankBalanceCopy.DEPOSIT_ALL_UNAVAILABLE` and its key are both marked **delete at
Milestone 6**.

The pending label (`"Depositing..."`) already exists so Milestone 6 wires behaviour to a button
whose wording and states the owner has already approved.

---

## 6. Milestone report

**Files added.** `BankBalanceScreen.java`, `BankBalanceCopy.java`, `BankBalanceCopyTest.java`.

**Files changed.** `BankMainScreen.java`, `BankPlaceholderScreen.java`, `en_us.json`,
`BankTranslationKeysTest.java`.

**Decisions made.**
- One diegetic sentence rather than three stacked lines, matching the dialogue family the Main
  screen established.
- A dedicated empty-account sentence (§2).
- `Locale.ROOT` grouping, tested against a changed JVM default (§2).
- Layout recomputed on balance change rather than per frame (§3).

**Tests run.** `./gradlew test` — 470 pass, 0 fail (was 457).

**Unresolved blockers.** None.

**Scope explicitly not performed.**
- No Deposit All Coins request, packet, or server work. Milestones 6a and 6b.
- No Bank Box. `BankPlaceholderScreen.bankBox()` remains, and item deposit, item withdrawal and
  currency withdrawal are still unreachable from the UI — the Milestone 4 §3 rollout position,
  unchanged and still open if you want it done differently.
- `BankChequeIssuanceScreen` untouched; Milestone 7.

---

## 7. Acceptance gate

> *The owner approves the Balance Screen layout.*

This gate is explicitly yours to judge by looking. What I can report is that the required elements
are present, the copy is translatable and plural-correct, the numbers follow the session rather
than a snapshot, and the disabled control explains itself.

Playbook §3.1: **all user-visible strings introduced by this milestone are translatable**, enforced
by `BankTranslationKeysTest`, including both plural forms of all three denominations — a missing
`.one` would otherwise surface only on an account holding exactly one coin, which is exactly the
account nobody thinks to check by hand.

Worth a look specifically: the balance sentence at a large value, and whether the Deposit All Coins
notice reads as helpful rather than as noise sitting under every visit.

**Stopping here for owner review.** Milestone 6a (Rails — Deposit All Coins) is the natural next
step and is the epic's long pole; Milestone 7 (Create Cheque presentation) is the alternative if
you would rather keep client momentum.
