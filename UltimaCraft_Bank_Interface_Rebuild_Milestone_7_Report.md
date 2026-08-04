# Milestone 7 — Create Cheque Screen presentation

**Status:** Complete, awaiting owner review at the acceptance gate
**Baseline:** NeoForge `banking` @ `bbe070f` (Milestone 6b-ii is in the tree, uncommitted)

---

## 1. What was built

| File | Role |
| --- | --- |
| `client/screen/bank/BankChequeForm.java` | Validation, bounds, denomination conversion |
| `client/screen/BankChequeIssuanceScreen.java` | Rebuilt on the dialogue frame |
| `client/screen/bank/BankChequeFormTest.java` | 22 tests |

Changed: `BankStatusPresenter` (+`AMOUNT_BELOW_MINIMUM`), `BankMainScreen` and `BankScreen`
(constructor no longer takes a payload), `ClientNetworkHandler` (result dispatch removed),
`en_us.json` (+10 keys), `BankTranslationKeysTest` (+1 test).

Four things changed beyond the frame:

- **Three denominations**, replacing a box literally labelled "Amount (gold)".
- **Escape closes banking.** It used to call Cancel and return to the previous screen, making it a
  second Back — which design §5.2 forbids by name. Back is now the only route to the hub.
- **It reads the session.** Balances come from `ClientBankingSession` every frame instead of a
  payload captured in the constructor, which is what finally lets it implement `BankingScreen` and
  stay mounted across a refresh. It could not before: staying mounted would have shown numbers
  that were already stale, which is exactly why Milestone 4 deliberately left it out.
- **Validation is real and immediate**, rather than a single gold-range check at submit time.

---

## 2. The bounds rule, revised after seeing it built

Building this form is what exposed the problem, and the owner revised the rule on seeing it.

**The floor is now 500 coins of whichever denomination funds the cheque** — 500 gold, 500 silver,
or 500 copper. Previously it was an absolute 5,000,000-copper *value*, which cost 500 gold but
50,000 silver or 5,000,000 copper for the same instrument. A player selecting Gold and typing `10`
was about to be refused, and there was no way to infer "5,000,000 copper" from "500 gold".

Worth recording, because the question came up: **the absolute floor was never this epic's
decision.** It is `BankCheque::MIN_AMOUNT` from ADR-018/ADR-019, written when cheques were
gold-only and it simply meant "500 gold". This epic only chose whether to keep it absolute or make
it per-denomination when the owner opened cheques to all three; the revision reverses that
sub-decision.

**Gold is unaffected** — 500 gold *is* 5,000,000 copper — so nothing that has ever shipped changes.
Only silver and copper gain reachable floors, and neither existed before this epic.

**The ceiling stays value-denominated**, and that is structural rather than policy: the amount is
an int32 copper column, so a flat coin count would let a copper cheque overflow what it is stored
in. Maximums are therefore 100,000 gold / 10,000,000 silver / 1,000,000,000 copper.

The screen still does two things that survive the revision:

- **`AMOUNT_BELOW_MINIMUM` is its own status**, not folded into "invalid amount". The number is not
  invalid; it is too small, and those are different sentences.
- **The contextual line names the minimum**, updating as the player switches denomination.

Both are client-side courtesies. Rails' validator remains the authority — and **Milestone 8a must
now implement the revised rule**: enforce the floor per denomination against the coin count, and
lower `BankCheque::MIN_AMOUNT` to 500. Nothing is sent until 8b, so the two halves cannot disagree
in flight.

---

## 3. A finding from the tests

`Long.parseLong` accepts **any Unicode decimal digit** — it routes through `Character.digit` — so
Arabic-Indic `٥٠٠` parses happily as 500, as does fullwidth `５００`, as would a string mixing
scripts. My test asserted these should be rejected and failed, which is how I noticed.

The parsed value would even have been *right*. But a field whose accepted input depends on which
digit systems the JDK recognises is not a contract anyone can reason about, and it silently
differed from the plain-ASCII amounts every other banking field takes. `BankChequeForm` now checks
for ASCII digits explicitly before parsing.

A useful side effect: a leading `-` now fails the shape check rather than parsing to a negative, so
the zero-or-negative branch below it only ever has to catch a literal zero. And a run of digits too
long for a `long` reports as **too large** rather than malformed — it is still a number the player
typed, and that is the honest thing to say.

---

## 4. This milestone sends nothing

Confirm enables and disables correctly and **issues no cheque**. Playbook Milestone 7 requires no
issuance packet, and 8b cannot send one until 8a has taught Rails to accept a denomination —
emitting the field early would be rejected as `UNEXPECTED_FIELD`, which is precisely the ordering
§1.1 rule 2 exists to prevent.

The pending label and states exist now so that when 8b wires the request, the wording and visuals
are already ones you have approved.

---

## 5. Playbook requirements

| Required | Status |
| --- | --- |
| portrait and name, prompt, status area | `BankDialogueFrame` |
| Gold / Silver / Copper selection | Three buttons; the selected one renders inactive |
| positive amount input | `EditBox`, ASCII digits, max length 10 |
| selected balance context | §2 — balance **and** that denomination's minimum |
| Back / Confirm | Buttons 0 and 1 |
| Cancel | **Not retained.** Back is Cancel; a second button doing the same thing on a form that submits nothing would be noise |
| empty, malformed, zero, negative, overflow, no denomination | All six tested (§6) |
| Back returns to Main; Escape closes banking | Both, and now genuinely different |
| changing denomination updates contextual balance | `selectDenomination` → `revalidate` |
| pending disables form submission | Confirm, denomination buttons and the text field all lock |

---

## 6. Tests

```
BankChequeFormTest       26 tests   (new)
Full JUnit suite        500 pass, 0 fail   (was 471)
All GameTests           284 run, 283 pass  (unchanged)
```

All six required validations are covered, plus both bounds at their exact edges in all three
denominations, the conversion out of each denomination into copper, affordability against the
selected denomination only, and the ordering rule that shape and range problems are reported
before affordability — a player who typed `abc` should be told it is not a number, not that they
are poor.

The one GameTest failure is still the pre-existing world-state bootstrap test.

**Not covered, by decision:** focus order, GUI scaling and the visual layout of the form, which
Playbook Milestone 7 also lists. Architecture Decision 0 — no harness here can instantiate a
`Screen`. The geometry those rest on is covered by `BankDialogueLayoutTest`, including form rows
that overlap nothing at every width; what remains is appearance, and that is this gate.

---

## 7. Milestone report

**Decisions made.**
- Cancel not retained (§5).
- ASCII-digit input, deliberately narrower than `parseLong` (§3).
- Gold preselected — it is what every cheque was before this milestone.
- The typed amount survives a denomination change. Switching gold→silver usually means the same
  number, and clearing it would punish exploring.

**Tests run.** `./gradlew test` — 496 pass. `./gradlew runGameTestServer` — 284 run, 283 pass.

**Unresolved blockers.** None.

**Scope explicitly not performed.**
- No issuance packet, no denomination on the wire. Milestones 8a and 8b.
- **Cheque issuance is currently unreachable end to end.** The old screen's gold-only submit was
  removed with it; nothing replaces it until 8b. This is a real functional gap on the branch, and
  it is the same shape as the Milestone 4 §3 rollout position — worth knowing rather than
  discovering.
- Bank Box remains a placeholder.

---

## 8. Acceptance gate

> *The owner approves the new cheque form before transaction integration.*

That is explicitly yours to judge by looking. What I can report: the required elements are present,
all six required validations are tested, the copy is translatable, balances follow the session, and
the value-denominated minimum is stated rather than left for a player to discover by rejection.

**Worth your eye specifically:** whether three buttons with the current one greyed reads as a
selection rather than as three broken buttons. If it is wrong, the fix is in this screen or in
`BankDialogueLayout`, not in the validation.

**Stopping here for owner review.** Milestone 8a (Rails — multi-denomination cheques) is the
natural next step; it is what makes this form able to do anything, and it now carries the revised
floor rule in §2.
