# Milestone 8b — Cheque issuance integration (client)

**Status:** Complete, awaiting owner review at the acceptance gate
**Rails contract:** `ultimacraft-website` @ `dd8600b` (Milestone 8a), `docs/banking_bank_cheque_issuance.md`
**Baseline:** NeoForge `banking` @ `e7ca8f1`

> **Cheque issuance is reachable again.** It has been unreachable since Milestone 7 removed the
> old gold-only submit. This closes Milestone 8 and the epic's second — and last — cross-repository
> contract.

---

## 1. The contract detail that shapes everything

From 8a's doc, which flags it as the easiest thing to get backwards:

> **`amount` is always copper. `currency_key` names the balance, not the unit.**

| Request | Means | Stored | Debits |
| --- | --- | --- | --- |
| `{ 5000000, "gold" }` | 500 gold | 5,000,000 | 500 from `gold_balance` |
| `{ 50000, "silver" }` | 500 silver | 50,000 | 500 from `silver_balance` |
| `{ 500, "copper" }` | 500 copper | 500 | 500 from `copper_balance` |

The key never rescales the amount. `BankChequeForm.Validation` was already producing exactly this
pair in Milestone 7 — a copper value plus the selected denomination — so the client half needed no
rework, only wiring.

---

## 2. What was built

| Change | Where |
| --- | --- |
| `currency_key` on the wire and in the request record | `BankChequeIssuanceRequestC2SPayload`, `BankingChequeIssuancePrepareRequest`, `BankingChequeIssuanceClient` |
| Funding denomination threaded through the sequence | `BankingChequeIssuanceProxyService` |
| Per-denomination local validation | same, `prepareAndConfirmInternal` |
| Shared copper-unit lookup | `CurrencyItemRegistry.copperUnitFor` |
| Confirm wired to send | `BankChequeIssuanceScreen.writeCheque` |
| 6 GameTests | `BankingChequeIssuanceProxyServiceGameTests` |

**`CurrencyItemRegistry.copperUnitFor`** mirrors Rails' `ChequePayloadValidator::COPPER_UNITS`,
and both derive from the same canonical ratios — so the unit this validates an amount against is
provably the unit Rails later divides by.

**The local pre-check now applies Rails' three rules in Rails' order**: storable range, whole
multiple of the funding denomination's unit, and at least 500 coins of it. Checking the *coin
count* rather than the copper value is the substance of the revised floor — 500 copper and 500
gold are both legal, and 10 gold is not.

`MIN_AMOUNT_COPPER` drops from 5,000,000 to 500, matching 8a's lowered `BankCheque::MIN_AMOUNT`.
It is now only a sanity bound; `MIN_COIN_COUNT` is the real rule.

---

## 3. Three decisions from 8a, honoured

**The response does not echo `currency_key`** (8a decision 3), so the screen holds the selected
denomination from its own capture and never reads it back. `writeCheque` passes it straight from
the selection; there is nothing to trust or mistrust.

**`currency_key` is optional on Rails but always sent here.** Rails treats absence as gold so
clients predating 8a keep working forever (§1.1 rule 3). Relying on that default from a client
that *does* know about denominations would make "gold" indistinguishable from "the field got
dropped" — so it is always on the wire, including for gold.

**8a generalised six call sites, not four** — `Cancel` and `Expire` held the same hard-coding.
Nothing for this side to do, but worth recording: without it, cancelling a silver-funded cheque
would have stranded the silver reservation and driven `reserved_gold_balance` negative.

---

## 4. Ordering

§1.1 rule 2 is satisfied: 8a is **committed** on Rails at `dd8600b`, so Rails accepts
`currency_key` before this client sends it. The strict envelope would reject it as
`UNEXPECTED_FIELD` otherwise.

Worth stating plainly: **committed is not deployed.** Nothing here is pushed, and the client and
Rails halves must reach any given shard in that order.

---

## 5. Tests

```
BankingChequeIssuanceProxyServiceGameTests   +6 tests
All GameTests                               290 run, 289 pass   (was 284)
Full JUnit suite                            500 pass, 0 fail
```

New coverage: a silver-funded cheque sends the copper value **unscaled** with its own key; 500
copper is accepted (the case the old absolute floor rejected outright); 499 of gold and of copper
are both refused locally; 10 gold — sensible-looking, nowhere near 500 coins — never reaches
Rails; an amount that is not whole coins of its denomination is refused; an unsupported key is
refused before it can be validated against the wrong unit.

The eleven existing cheque tests were updated to pass the gold key explicitly rather than relying
on a default, so they keep testing what they were written to test.

The one GameTest failure remains the pre-existing world-state bootstrap test.

---

## 6. A finding from 8a that needs a decision

8a's report flagged this, and it is not theirs or mine to close:

> `BankAccounts::ReconcileCurrency` sums true reserved balances from `currency_withdrawal`
> operations only, so a prepared `cheque_issuance` reserves real coins that reconciliation reports
> as drift — verified empirically at 500 gold on a prepared issuance.

**It is pre-existing and unchanged by either milestone** — equally true of the gold-only path. What
changed is exposure: it now surfaces in three denominations instead of one, and the lower floors
mean more issuances in flight at any moment. Someone reading the reconciliation report will see
drift that is not drift.

Out of scope here. Flagged as its own task.

---

## 7. Milestone report

**Decisions made.** Always send `currency_key` (§3); reject an unsupported key locally rather than
letting Rails validate an amount against the wrong unit; put the unit table on
`CurrencyItemRegistry` rather than duplicating it client- and server-side.

**Tests run.** `./gradlew test` — 500 pass. `./gradlew runGameTestServer` — 290 run, 289 pass.

**Unresolved blockers.** None.

**Scope explicitly not performed.**
- **No end-to-end smoke test against the live shard.** Every path is exercised against a fake
  client; nobody has yet written a silver cheque and watched a real balance move. Milestone 18's
  matter, and I would not call this proven until it has happened.
- Bank Box remains a placeholder — item deposit, item withdrawal and currency withdrawal are still
  unreachable (Milestone 4 §3, unchanged).
- The inventory-full `Kind` is still assigned to Milestone 15.
- `docs/ultimacraft_banking_service_npc_compatibility_map.md` carries **ADR-026** in this repo,
  written by the 8a run. Included in this commit as the epic's ADR registry.

---

## 8. Acceptance gate

> *Cheque creation reaches feature parity with the existing service through the new screen.*

Parity and past it: the old screen issued gold-only cheques with a single range check at submit.
The rebuilt one issues from all three denominations, validates six ways per keystroke, enforces
Rails' own three amount rules locally before any round trip, and holds a session-level pending lock
so a double-click writes one cheque.

**Worth your eye:** a silver or copper cheque's amount when it lands in the inventory. The item's
display amount is a copper value, so a 500-silver cheque reads as 50,000 — correct, and possibly
confusing. If that wants changing it is a `BankChequeData` presentation question rather than a
contract one.

**Stopping here for owner review.** Milestone 9 (Bank Box screen shell) is next, and begins the
longest remaining stretch — Milestones 9 through 16 are what make item and currency banking
reachable again.
