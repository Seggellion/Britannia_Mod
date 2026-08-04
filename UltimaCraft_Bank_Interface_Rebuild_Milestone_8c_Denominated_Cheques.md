# Milestone 8c — Denominated cheques (corrective, cross-repository)

**Status:** **Both halves complete.** Rails `9c0aebf`; NeoForge implemented and tested (§5 below).
**Owner decision:** 2026-08-03 — a cheque never converts. 500 copper in, 500 copper out.
**Repositories:** Rails `ultimacraft-website` **first**, then NeoForge `Britannia_Mod` (§1.1 rule 2)
**Supersedes:** ADR-012 (properly reversed, not narrowed), and the bounds half of ADR-018/ADR-019

---

## 1. The defect

A cheque stores **one integer of copper** and has no denomination. `bank_cheques` has an `amount`
column and nothing else of value. Redemption converts that copper into a coin mix:

```ruby
gold, remainder = copper_amount.divmod(COPPER_PER_GOLD)
silver, copper  = remainder.divmod(COPPER_PER_SILVER)
```

`BankCheques::Redeem` then credits all three balances from that mix. So:

| Written | Debits | Stored | **Redeems as** |
| --- | --- | --- | --- |
| 500 gold | 500 gold | 5 000 000 | 500 gold ✓ |
| 500 silver | 500 silver | 50 000 | **5 gold** |
| 500 copper | 500 copper | 500 | **5 silver** |

Gold round-trips because it is the identity case, which is why this was never noticed: cheques
were gold-only, by ADR-012's design, and the conversion had nothing to convert.

**Milestones 8a and 8b made silver and copper funding reachable, which turns a dormant property
into a live one.** The conversion predates this epic; the exposure does not.

> **Milestone 8b must not be deployed until this lands.** A player writing a copper cheque under
> the current code loses their copper.

---

## 2. The rule

**A cheque is a fixed number of coins of one named denomination. It never converts, and it never
carries a mix.**

- Bounds: **500 to 5 000 000 coins**, identically for gold, silver and copper.
- Redemption credits exactly `amount` coins to the `currency_key` balance. Nothing else.
- Issuance debits exactly `amount` coins from the `currency_key` balance. Nothing else.

**This dissolves the int32 ceiling.** The old ceiling existed only because `amount` held copper
value, so five million gold needed 50 000 000 000. As a coin count, five million fits an int32
column in every denomination — the same fix removes both problems.

---

## 3. Rails changes

### 3.1 Schema

Add `currency_key` to `bank_cheques` (string, not null after backfill), constrained to
`gold`/`silver`/`copper`.

**`amount` changes meaning from copper value to a coin count in `currency_key`.** The column type
is unchanged; only its interpretation moves.

### 3.2 Migration

Every cheque issued before Milestone 8a was gold-only *and* validated with
`amount % COPPER_PER_GOLD == 0`, so the conversion is exact:

```
currency_key = 'gold'
amount       = amount / 10_000
```

No rounding, no loss.

> **Precondition confirmed by the owner, 2026-08-03: Milestone 8a was never deployed.** No silver
> or copper cheque has ever been issued, so every existing row is a pre-8a gold cheque with an
> amount that is a whole multiple of 10 000, and the conversion above is exact for all of them.
>
> **Assert it in the migration anyway** and fail loudly on a violating row. The cost is one
> `SELECT`; the alternative is silently dividing a value that was never gold and misstating what a
> player holds.

### 3.3 Bounds

`BankCheque::MIN_AMOUNT` → **500**, `MAX_AMOUNT` → **5 000 000**, both now coin counts.
`ChequePayloadValidator` drops the copper-unit multiple rule entirely — there is no unit to be a
multiple of once the amount is a coin count.

### 3.4 Redemption

`BankCheques::Redeem` credits one balance:

```ruby
account.update!("#{cheque.currency_key}_balance" => balance + cheque.amount)
```

`coin_mix` / `coin_mix_for` are **no longer used for redemption**. Keep or remove per §3.6.

### 3.5 Issuance

`Create` and `Confirm` already reserve and debit against the keyed balance
(`reserved_#{key}_balance`, `#{key}_balance`) from Milestone 8a. They currently divide the copper
amount by the denomination's unit to get a coin count; with `amount` already a coin count, **that
division is removed**. `Cancel` and `Expire` likewise.

### 3.6 Admin reporting — a real knock-on

`Admin::BankChequesController` computes `@in_limbo_copper = BankCheque.issued.sum(:amount)` and
renders it as a coin mix. **That sum becomes meaningless**: 500 gold and 500 copper are both
`amount = 500`, and adding them produces a number that is not a quantity of anything.

Replace with a per-denomination total — three figures, grouped by `currency_key`. Do not
re-derive a single copper figure; that would reintroduce exactly the conversion this milestone
removes.

### 3.7 Contract

`docs/banking_bank_cheque_issuance.md` currently states "**`amount` is always copper.
`currency_key` names the balance, not the unit**" and calls it the easiest thing to get backwards.
**That sentence inverts.** The doc is the contract's one home and must be rewritten, not patched.

Request shape is unchanged in *structure* — `{ amount, currency_key }` — but `amount` now means
coins. A client sending the old copper meaning would issue a cheque 10 000× too large, so this is
a **breaking contract change**, not an additive one.

---

## 4. Ordering, and why this one is genuinely breaking

Every prior cross-repo change in this epic was additive: Rails accepted a new optional field and
older clients kept working (§1.1 rule 3). **This one is not.** The same request shape means
something different afterwards.

Mitigation, in order of preference:

1. **8b is committed but not deployed.** If no shard is running it, no client is sending
   `currency_key` yet, and there is nothing to break. Confirm this first — it is the difference
   between a clean change and a coordinated one.
2. If a shard *is* running 8b, Rails must reject the ambiguity rather than guess: a cheque request
   whose amount is a whole multiple of 10 000 is indistinguishable between the two meanings.

---

## 5. NeoForge changes — **done**

Delivered as specified, plus one thing the spec did not anticipate: **the copper concept is gone
from the cheque path entirely**, rather than merely reinterpreted.

- `BankChequeForm.Validation` no longer carries both a copper value and the typed number. It
  carries one `amount`, the coin count. Two fields where the screen could pick the wrong one is
  exactly the class of mistake this milestone exists to remove, so the second field went with the
  conversion.
- `copperPerUnit` and `MAX_COPPER` are deleted; `maximumIn` returns 5 000 000 for all three.
- `BankingChequeIssuanceProxyService`: `MIN_AMOUNT_COPPER`/`MAX_AMOUNT_COPPER` replaced by
  `MIN_COIN_COUNT`/`MAX_COIN_COUNT`; the unit-multiple check removed; `amountCopper` renamed
  throughout to `amountCoins`.
- `BankChequeData.displayAmount` is a coin count, and the tooltip reads "500 silver" — it
  previously divided by `COPPER_PER_GOLD`, so a 500-silver cheque displayed as "5 gold".
- `CoinConversion` is no longer imported anywhere in the cheque path. That is the check worth
  keeping: if it comes back, a conversion came back with it.

Tests: 529 JUnit, 290 GameTests (289 pass — the one failure is the pre-existing world-state
bootstrap test). The cheque GameTests now use a 500-coin sample rather than a 5 000 000-copper
one, and the "not a whole multiple of the unit" test was **deleted rather than adapted**: the rule
it covered no longer exists.

### Original specification (retained for the record)

Small, because the client already carries the denomination:

- `BankChequeForm`: `MAX_UNITS` applies to all three; `maximumIn` loses the storage cap and returns
  5 000 000 flat; the copper conversion disappears — the request carries the typed coin count
  directly.
- `BankingChequeIssuanceProxyService`: `MIN_AMOUNT_COPPER`/`MAX_AMOUNT_COPPER` become
  `MIN_COIN_COUNT`/`MAX_COIN_COUNT` and the unit-multiple check is removed.
- `BankChequeData.displayAmount` becomes a coin count; the tooltip reads "500 silver" rather than
  a converted figure. The tint already reads `currencyKey` and needs no change.
- `BankChequeIssuanceScreen` sends `validation.enteredAmount()` rather than
  `validation.copperAmount()`.

`CoinConversion` stays where it is used for actual coin handling; it simply stops being involved
in cheques.

---

## 6. Tests

**Rails:** issuance, redemption and cancellation in each denomination; a 500-copper cheque
redeeming as exactly 500 copper and touching no other balance; the floor and ceiling at their
exact edges in all three; the migration against a pre-8a gold cheque; the guard that refuses to
migrate a non-whole-gold row; per-denomination admin totals.

**NeoForge:** the form accepting 5 000 000 in every denomination; the request carrying the coin
count unconverted; the tooltip naming the denomination.

**The one test that matters most:** write a cheque in each denomination, redeem it, and assert the
balance returns to exactly what it was. That is the whole milestone in one assertion.

---

## 7. What this fixes

- A cheque returns what was put into it, in the coin it was written in.
- The int32 ceiling is gone; 5 000 000 is reachable in all three denominations.
- `bank_cheques` stops being a value instrument pretending to be a denominated one.
- ADR-012 is reversed honestly rather than narrowed twice.
