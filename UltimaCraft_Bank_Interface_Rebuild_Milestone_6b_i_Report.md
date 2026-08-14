# Milestone 6b-i — Deposit All Coins, NeoForge server half

**Status:** Complete, awaiting owner review at the acceptance gate
**Rails contract:** `ultimacraft-website` @ `1aab037`, `docs/banking_bulk_currency_deposit.md`
**Baseline:** NeoForge `banking` @ `c76857c`

> **Milestone 6b was split in two at the owner's direction.** This is 6b-i: the sweep, the
> contract classes, the proxy service, packet routing and GameTests. **6b-ii** is the Balance
> Screen wiring and status vocabulary. Deposit All Coins is therefore fully implemented and tested
> server-side, and still **unreachable by a player** — the button remains disabled.

---

## 1. What was built

| File | Role |
| --- | --- |
| `bank/currency/CoinSweep.java` | Counts bare coin stacks; records slot, key and count |
| `service/banking/BankingDepositAllCoinsProxyService.java` | Sweep → prepare → dispose → confirm |
| `service/banking/BankingDepositAllCoinsClient.java` + `ClientPort` | `currency/deposit/all/prepare`; confirm/cancel delegated |
| `…PrepareRequest`, `…PrepareResult`, `…Result`, `…LocalRejectionReason` | The contract |
| `network/payload/BankDepositAllCoinsRequestC2SPayload.java` | Entity id, and nothing else |
| `gametest/BankingDepositAllCoinsProxyServiceGameTests.java` | 14 tests |

Modified: `BankingTransferOutcome` (+2), `BankTransferOperationType` (+1),
`RailsApiUrlResolver` (+1 endpoint), `BankingTransferResponseParser` (+1 parser),
`BankingTransferPacketService` (+handler), `NetworkHandler` (+registration),
`BankTransferReconciliationService` (+resume branch).

---

## 2. Two decisions worth your attention

### The receipt needed no schema bump, and that is not a small thing

A `BULK_CURRENCY_DEPOSIT` receipt is currency-shaped — `currencyAmount` carries the sweep's total
copper value — so it satisfies `BankTransferReceipt`'s existing exactly-one-of invariant
unchanged. What distinguishes it from a single-denomination deposit is the new
`BankTransferOperationType` value, not field presence.

**`BankTransferReceiptStore.SCHEMA_VERSION` is deliberately fail-closed:** bumping it makes every
receipt written under the previous version surface as `UnreadableEntry`. A shard carrying pending
reconciliations at upgrade time would lose the ability to resume them — real value, silently
stranded. Adding an enum value costs nothing and keeps them readable.

The stored total is diagnostic only. A resume re-sends `confirm` with the operation id and nothing
else; Rails already holds the three real amounts, so the per-denomination breakdown never needed
persisting.

### Disposal is all-or-nothing by construction, not by luck

The sweep is prepared as one amount triple, so Rails credits all of it or none. Removing only some
counted stacks would pay the player for coins they still hold.

So every counted slot is verified **before** any removal begins — one pass to check, then one pass
to remove, both inside a single main-thread tick with no `await` between them. A partial sweep is
therefore unrepresentable rather than merely unlikely.

**One deliberate divergence from the single-slot deposit:** revalidation requires *at least* the
counted quantity, not exactly. A sweep counts the whole inventory, so a player who *gains* coins
during the round trip has invalidated nothing — the surplus simply is not part of this operation.
Requiring an exact match would abort the deposit because somebody picked up a copper coin
mid-flight, for no safety gain. Tested both ways.

---

## 3. What the sweep will and will not take

`CurrencyItemRegistry.currencyKeyOf` and nothing else — top-level item identity only:

| | Swept? |
| --- | --- |
| Bare gold / silver / copper stack | **Yes** |
| Ordinary items | No |
| Bank cheque | No — never quietly redeemed |
| Shulker box or chest holding coins | **No** — the container is not a coin stack |
| Armor and offhand | No — slots `0..35` only |

The container case is the one worth stating plainly: Deposit All Coins must never empty a
container a player is using for storage, and it cannot, because the classifier never looks inside
one. Milestone 0 §3.6 confirmed that polarity against the code rather than assuming it.

Totals accumulate as `long` and clamp to `Integer.MAX_VALUE` — the exact ceiling Rails validates
against. Thirty-six slots of a stack-99 coin cannot approach it, but a clamped total is rejected
honestly by Rails where a wrapped negative would be neither.

---

## 4. Tests

```
BankingDepositAllCoinsProxyServiceGameTests    14 tests   (new)
All GameTests                                 282 run, 281 pass   (was 268)
Full JUnit suite                              470 pass, 0 fail
```

The single GameTest failure is `WorldStateFullBootstrapFallbackGameTests`, **proven pre-existing**
against `f4daef9` in Milestone 2 §5 and unrelated to banking. It already has its own task.

Covered here: one denomination, multiple stacks across slots, all three together, ordinary items,
containers, cheques, offhand exclusion, empty purse refused without contacting Rails, happy path
with non-coin items untouched, a counted stack shrinking mid-flight, coins gained mid-flight,
duplicate request, prepare rejection, and reconciliation-required at confirm.

**Not covered here, on purpose:** persistence rejection, the balance ceiling, duplicate-confirm
idempotency and expiry. Those are Rails' behaviour and 6a's suite already covers them — asserting
them through a fake client would only test the fake.

One test bug found and fixed during the run: `helper.succeedWhen` registers a condition polled on
later ticks, so a `finally` block releasing a parked future runs first. The duplicate-request test
now asserts synchronously, which is what it was actually testing.

---

## 5. Milestone report

**Decisions made.** The two in §2, plus: `IN_FLIGHT` is keyed by player alone (there is no slot;
the subject is the whole inventory), and the result reports on the existing `Operation.DEPOSIT`
channel rather than a new one.

**Tests run.** `./gradlew test` — 470 pass. `./gradlew runGameTestServer --no-configuration-cache`
— 282 run, 281 pass.

**Unresolved blockers.** None.

**Scope explicitly not performed — this is 6b-ii.**
- The Balance Screen button is **still disabled**, and `BankBalanceCopy.DEPOSIT_ALL_UNAVAILABLE`
  and its translation key are still in place. Nothing a player can press reaches any of this.
- `NO_COINS` currently reports to the client as a generic `CLEAN_REJECTION`. It needs its own
  `BankTransferResultS2CPayload.Kind` and message — "your purse was empty" and "the teller refused
  you" must not read alike, and design §15 lists "nothing to deposit" as its own category.
- No pending-lock wiring through `ClientBankingSession`.

**Also still open from earlier milestones:** the inventory-full `Kind` assigned to Milestone 15,
and the Milestone 4 §3 rollout position (Bank Box remains a placeholder).

---

## 6. Acceptance gate

> *Deposit All Coins works as one authoritative transaction and does not use a client packet loop.*

It is one operation: one sweep, one prepare carrying three totals, one disposal, one confirm
crediting all three denominations in a single Rails update. There is no loop anywhere — the packet
carries an entity id and no amounts at all, which is what makes a client-driven loop structurally
impossible rather than merely avoided.

The half of that gate a player can observe belongs to 6b-ii.

**Stopping here for owner review.**
