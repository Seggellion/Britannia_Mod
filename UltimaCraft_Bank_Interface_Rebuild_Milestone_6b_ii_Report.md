# Milestone 6b-ii — Deposit All Coins, client integration

**Status:** Complete, awaiting owner review at the acceptance gate
**Predecessor:** Milestone 6b-i, committed `bbe070f`
**Baseline:** NeoForge `banking` @ `bbe070f`

> **Deposit All Coins is now reachable by a player.** The Balance Screen button is live, and this
> closes Milestone 6 and with it the epic's first cross-repository contract.

---

## 1. What was built

| Change | Where |
| --- | --- |
| Two new result kinds: `NOTHING_TO_DEPOSIT`, `BALANCE_CAPACITY_EXCEEDED` | `BankTransferResultS2CPayload` |
| Outcome → kind mapping | `BankingTransferPacketService.depositAllCoinsKindFor` |
| Kind → message + severity | `BankStatusPresenter` |
| Button wired: press → session lock → one packet | `BankBalanceScreen` |
| The "not built yet" notice retired | `BankBalanceCopy`, `en_us.json` |
| 2 GameTests, 2 JUnit tests | |

---

## 2. "Nothing to deposit" is not a refusal

6b-i flagged this as the gap, and it is the substance of this milestone. A sweep that finds no
coins previously reached the client as a generic `CLEAN_REJECTION`, whose wording is *"The teller
checks the ledger and shakes their head: 'I'm afraid I can't complete that right now.'"*

That sentence is false. Nothing failed, and the teller did not refuse anyone — the player's purse
was empty. Design §15 lists "nothing to deposit" as its own outcome category for exactly this
reason, and it now renders as **informational**, not a rejection:

> *The teller glances at thy purse and smiles. "Thou hast no coin about thee to deposit."*

The same argument applies to the balance ceiling, so it also got its own kind. It **is** a
refusal, but an actionable one — withdraw or spend, then retry — and a generic rejection would not
tell the player that. It stays `REJECTION` severity, with its own wording.

Both are reported from two places, deliberately: an empty purse is caught locally before any
network call, and Rails' own `NO_COINS` covers a client that skipped that check. They map to the
same kind, so the player sees one consistent message either way.

---

## 3. One press, one request

```java
if (!session.beginPending(Operation.DEPOSIT)) return;
ClientNetworkHandler.sendToServer(new BankDepositAllCoinsRequestC2SPayload(session.tellerEntityId()));
```

The lock is claimed **before** the packet goes out and a failed claim sends nothing, which is what
makes a double-click one deposit instead of two. It is the session's lock rather than a screen
flag, so navigating away mid-request cannot shake it off (design §10.9) — and because the session
outlives the screen, the button comes back correctly pending if the player returns to Balance
while the request is still in flight.

Nothing is computed locally: no totals, no denominations, no local inventory change. The screen
learns what happened only from the refresh push or the result.

`refreshButtonStates` runs from `render` as well as `init`, because the lock is released by a
packet arriving rather than by anything this screen does — it can land on any tick.

---

## 4. Wire compatibility

Both `Kind` and `Operation` are encoded **by ordinal**. The two new constants are **appended**,
never inserted, so existing values keep their positions. I added that constraint to
`BankTransferResultS2CPayload`'s class docs rather than leaving it as folklore — an insertion here
would silently remap every outcome on a mixed-version client, which is the §1.1 rule 3 hazard even
for a packet whose two ends are both NeoForge.

Two exhaustive switches in the legacy screens needed the new cases to keep compiling. Both are
unreachable — `BankScreen` since the Milestone 4 cutover, and the cheque screen's handler returns
early on any operation but `CHEQUE_ISSUANCE` — so both map to the generic message with a comment
saying why. They disappear at Milestones 7 and 19.

---

## 5. Tests

```
BankingDepositAllCoinsProxyServiceGameTests    16 tests   (+2)
All GameTests                                 284 run, 283 pass   (was 282)
Full JUnit suite                              471 pass, 0 fail
```

The two new GameTests go through the **real packet handler**, not the proxy service, because the
mapping being tested lives in the handler: an empty purse reports `NOTHING_TO_DEPOSIT`, and a
Rails balance-ceiling refusal keeps its own kind and destroys nothing.

Two existing tests earned their keep without modification: `BankStatusPresenterTest` loops every
`(Operation, Kind)` pair and asserts each maps to a distinct key, and `BankTranslationKeysTest`
loops every kind and asserts an English string exists. Both new kinds were covered the moment they
were added — a missing translation would have failed the build rather than shipping a raw key onto
the parchment.

The one GameTest failure remains `WorldStateFullBootstrapFallbackGameTests`, proven pre-existing
in Milestone 2 §5 and already tracked separately.

---

## 6. Milestone report

**Decisions made.** `BALANCE_CAPACITY_EXCEEDED` was given its own kind rather than folded into
`CLEAN_REJECTION` (§2) — a small widening of 6b-i's stated scope, taken because design §15 already
lists "insufficient bank capacity" as its own category and the message is actionable.

**Tests run.** `./gradlew test` — 471 pass. `./gradlew runGameTestServer --no-configuration-cache`
— 284 run, 283 pass.

**Unresolved blockers.** None.

**Scope explicitly not performed.**
- No end-to-end smoke test against the live shard. The Rails half is deployed and the contract is
  exercised against a fake client throughout, but nobody has yet pressed the button with real
  coins and watched a real balance move. That belongs to Milestone 18's matrix, and I would not
  call this feature proven until it has happened.
- Bank Box remains a placeholder; item deposit, item withdrawal and currency withdrawal are still
  unreachable (the Milestone 4 §3 rollout position, unchanged).
- The inventory-full `Kind` is still assigned to Milestone 15.

---

## 7. Acceptance gate

> *Deposit All Coins works as one authoritative transaction and does not use a client packet loop.*

One press sends one packet carrying a teller entity id and no amounts. The server sweeps, prepares
once with three totals, disposes once, and confirms once; Rails credits all three denominations in
a single update. A client-driven loop is structurally impossible because the packet has nowhere to
put a quantity.

The client half now meets the playbook's own list: one press sends one request, the button becomes
pending, duplicate clicks are blocked, no local stack is removed, a no-coins result is
non-destructive and readable, success refreshes the Balance Screen through the existing push, and
closing during pending fabricates nothing.

Playbook §3.1: **all user-visible strings introduced by this milestone are translatable**, enforced
by `BankTranslationKeysTest`.

**Worth your eye:** whether the no-coins line reads as friendly rather than as an error, and
whether the button's pending state is legible at the GUI scale you play at.

**Stopping here for owner review.** Milestone 7 (Create Cheque Screen presentation) is next, and
carries the three-denomination controls your 8a decision requires.
