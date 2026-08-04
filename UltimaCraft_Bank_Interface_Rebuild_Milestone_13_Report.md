# Milestone 13 — Drag-to-deposit integration

**Status:** Complete, awaiting owner review at the acceptance gate
**Baseline:** NeoForge `banking` @ `8dcde2a`

> **A player can now drag a stack into the Bank Box and deposit it. No double-click exists
> anywhere in the flow.** This is the epic's central promise, live — and it took one new method
> and one screen function, because everything it stands on was already built and tested:
> Milestone 0 found the server routes deposits from the live slot, Milestone 2 built the pending
> lock, Milestone 12 built the gesture. This milestone is the join.

---

## 1. What was built

| Change | Where |
| --- | --- |
| `handoffSourceUnchanged` — the release-time re-check | `BankDragController` (+2 tests) |
| `sendDragDeposit` — handoff → packet, or safe drop | `BankBoxScreen` |

The whole of the client-side integration is the ordered sequence design §10.6 prescribes:

1. **Release-time re-check.** The per-frame watchdog covered the drag; this covers the final
   frame between the last tick and the mouse-up. The live stack must still be exactly what was
   pressed — same registry id, same count — and still depositable. Any mismatch drops the handoff
   and sends nothing.
2. **The session lock is claimed before the packet goes out**, and a failed claim sends nothing.
   Duplicate protection is now three layers deep: the machine's single-handoff rule, the session
   lock, and the server's own guards.
3. **The packet is the same `BankDepositRequestC2SPayload` the legacy screen sends** — teller
   entity id and live slot index, nothing else.

**No local removal, no local balance change, no new packet, no server change.** The stack stays
visibly in the player's inventory until the refresh push says otherwise, which is the
no-optimistic-mutation rule (design §2) doing exactly what it was written for.

---

## 2. Why this milestone is small, and why that is the system working

Milestone 0 §3.3 established that there is no item-deposit packet, no currency-deposit packet and
no cheque-redemption packet — there is *one* deposit packet, and `BankingTransferPacketService`
routes it from the **live server-side slot**: coin stack → currency protocol, cheque → redemption,
everything else → item deposit. The client never names the protocol.

So the drag inherits, unchanged and for free:

- all three routing categories (Milestone 14 verifies them one by one, as designed);
- every server validation — eligibility, weight, capacity, fingerprint, ownership, the teller
  re-resolution — already covered by the existing GameTest suites;
- the refresh push on success, which the session consumes and the mounted screen re-reads
  (Milestone 4's cutover is why a successful drag does **not** eject the player from the Bank
  Box);
- result reporting through `BankStatusPresenter` on the Box's own status line, including the
  reconciliation-required severity treatment.

A large diff here would have meant the earlier milestones had failed at their jobs.

---

## 3. The playbook's test list, mapped honestly

New coverage: the two `handoffSourceUnchanged` tests (intact / shrunk / emptied at the final
frame, and refusal outside `HANDOFF`, where a meaningless answer must be the one that cannot
send).

The rest of the list was already pinned, and re-testing it through a fake client would only have
tested the fake:

| Playbook case | Where it is proven |
| --- | --- |
| eligible ordinary item | `BankingDepositProxyServiceGameTests` happy path |
| overweight item | `BankingDepositProxyServiceGameTests` capacity rejection |
| ineligible item from modified client | `BankingDepositProxyServiceGameTests` local-rejection path — the shading is UX; the server is the boundary |
| source changed during drag | `theSourceChangingUnderTheGestureCancelsIt` (watchdog) + the new release-time check |
| source emptied during drag | `theSourceEmptyingCancelsIt` + the new check's null case |
| duplicate release | `aDuplicateReleaseAfterTheHandoffIsInertNoise` + the session lock + server dedup |
| latency | the pending lock holds until a result or refresh arrives; `ClientBankingSessionTest` pending transitions |
| close during pending | `resultArrivingAfterCloseIsDroppedRatherThanApplied` |
| success refresh | the refresh push, adopted at D9; `refreshClearsPendingBecauseItIsTheSuccessSignal` |
| stale refresh ordering | re-scoped by owner-approved D12 — one ordered connection, whole snapshots |

```
Full JUnit suite    575 pass, 0 fail   (was 573)
All GameTests       299 run, 298 pass  (unchanged; the one failure is the known pre-existing bootstrap test)
```

---

## 4. Milestone report

**Decisions made.** Only ordering: re-check, then lock, then send, each refusal dropping the
handoff silently — a dropped handoff leaves the player holding their items with nothing pretending
otherwise, which needs no message.

**Tests run.** `./gradlew test` — 575 pass. `./gradlew runGameTestServer` — 299 run, 298 pass.

**Unresolved blockers.** None.

**Scope explicitly not performed.**
- Milestone 14's category-by-category routing verification (deliberately its own milestone).
- Withdrawal (15) and currency withdrawal (16) — the Box's remaining dead controls.
- **End-to-end against the live shard**: a real drag against real Rails remains Milestone 18's
  smoke matrix, as with every operation before it.

---

## 5. Acceptance gate

> *A player can drag a whole eligible stack into the Bank Box without double-clicking.*

They can. Press, cross four pixels, carry the ghost to the velvet, release: the request goes out
with the slot index, the button-equivalents lock, and the vault refreshes with the item in it —
or the status line says exactly why not.

**Worth trying in-game:** an ordinary stack end to end; a coin stack (it should credit the
balance, not appear in the vault — the routing Milestone 14 formalises); dropping while the
teller has walked away; and mashing release at the moment of drop, which should produce exactly
one deposit.

**Stopping here for owner review.** Milestone 14 — special deposit routing and cheque redemption
— proves each of the seven drop categories lands on the right protocol, and is verification
rather than construction.
