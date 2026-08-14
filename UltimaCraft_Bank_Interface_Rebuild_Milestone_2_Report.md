# Milestone 2 — Banking session and refresh contract

**Status:** **Complete and approved by the owner, 2026-08-03.**
**Architecture:** `UltimaCraft_Bank_Interface_Rebuild_Milestone_1_Architecture.md` (approved 2026-08-03)
**Baseline:** NeoForge `banking` @ `f4daef9`

---

## 1. What was built

One new class, one new test class, and four small wiring edits. No screen was rebuilt, no packet
changed, and no server code was touched.

### `ClientBankingSession` — `client/screen/bank/`

The shared client state all four screens will read: the latest account snapshot, the selected
stored item, the pending-mutation lock, and the last non-success result.

Three design points worth stating, because they are the reason the class exists:

**It holds the whole `BankAccountOpenedS2CPayload`, not a copy of its fields.** There is then no
second representation to drift, and `item_key` reaching the payload at Milestone 10 will arrive at
the grid without touching this class.

**Selection is a public UUID, never a grid index.** A refresh that reorders rows leaves the
selection correct; a refresh that removes the item clears it (`reconcileSelection`). Playbook
Milestone 15's "stale selection clears after refresh when the item is gone" is therefore already
satisfied before the grid exists.

**The pending lock is one field, typed with the server's own
`BankTransferResultS2CPayload.Operation`.** No parallel client enum to keep in sync. The lock is
global rather than per-screen because design §10.9's "no second banking mutation may begin" spans
screens — a player must not be able to start a cheque while a deposit is in flight by navigating
away from the Bank Box. The enum is coarser than the UI (a currency deposit and an item deposit are
both `DEPOSIT` — Milestone 0 §3.3), which costs nothing here since the lock blocks everything
regardless.

### Wiring

| File | Change |
| --- | --- |
| `ClientNetworkHandler.handleBankAccountOpened` | Feeds the session before the existing `setScreen` call |
| `ClientNetworkHandler.handleBankTransferResult` | Releases the lock and records the outcome before the existing screen dispatch |
| `BankScreen.onClose` | `ClientBankingSession.close()` |
| `BankChequeIssuanceScreen.onClose` | `ClientBankingSession.close()` |

---

## 2. Deliberately behaviour-neutral

**`handleBankAccountOpened` still calls `setScreen(new BankScreen(payload))`.**

Architecture D9 replaces that with "update the session, leave the current screen mounted", and
D14 puts the cutover at **Milestone 4** — because the screen it must point at, `BankMainScreen`,
does not exist yet. Making the change now would mean pointing it at the legacy screen it is
supposed to replace, which is worse than leaving it alone for two milestones.

So this milestone populates the session and proves it, and Milestone 4 flips one call. Nothing a
player can observe changed today. Playbook §1 rule 3 (preserve existing behaviour unless the
milestone explicitly changes it) is met.

The same applies to pending state: `BankScreen` keeps its own `depositPending` /
`withdrawalPending` booleans and is otherwise untouched. Two sources of truth exist for two
milestones; the legacy pair is removed when `BankScreen` is (Milestone 19). Nothing reads the
session's lock yet, so they cannot disagree in any observable way.

---

## 3. The re-scoped tests

Milestone 2's acceptance gate names "applying a newer refresh" and "rejecting or ignoring an older
refresh". **Both were re-scoped by architecture D12, approved by the owner on 2026-08-03**, because
no revision or sequence discriminator exists to order two refreshes by and none is being added:

- pushes travel `PacketDistributor.sendToPlayer` on one ordered connection, so arrival order is
  send order — there is no reordering to detect;
- every push is a whole `bank.open` snapshot rather than a delta, so applying the latest wholesale
  is correct by construction. There is no partial merge that could be corrupted by ordering.

The two properties that actually need guarding are tested instead:

- `refreshReplacesStateWholesale` — every value comes from the newest snapshot; nothing is merged
  from the previous one.
- `resultArrivingAfterCloseIsDroppedRatherThanApplied` — a late result cannot resurrect a closed
  session, satisfying design §5.3 in both directions at once (closing does not cancel an in-flight
  request, and the client does not invent a result after closing).

If Milestone 18's multiplayer matrix demonstrates a real stale-state failure, a discriminator
becomes its own change with a stated reason.

---

## 4. Playbook requirements

| Required content | Status |
| --- | --- |
| banker entity ID | `tellerEntityId()` |
| portrait / teller identity | `tellerName()`, `tellerGender()` |
| city display name | `cityDisplayName()`, null in global mode |
| balances | `goldBalance()`, `silverBalance()`, `copperBalance()` |
| current weight, weight limit | `currentWeight()`, `weightLimit()` |
| bank-item summaries | `bankItems()` |
| pending operation | `pendingOperation()`, `isMutationPending()` |
| relevant status | `lastResult()` |
| revision / stale-state discriminator **where available** | **None available.** D12; see §3 |

| Required behaviour | Status |
| --- | --- |
| navigation reuses the active session | Same instance survives a same-teller refresh; a different teller starts fresh |
| each screen renders the latest session state | Session is the single source; screens land Milestones 4+ |
| successful mutations update or refresh affected values | Refresh push applied wholesale and releases the lock |
| a stale response cannot overwrite a newer session state | Re-scoped per §3 |
| closing banking releases client-only state | `close()`, wired to both screens' `onClose` |
| the context never becomes authority | Holds only what the server sent; computes no banking fact |

---

## 5. Tests

**24 new JUnit tests**, `src/test/java/.../client/screen/bank/ClientBankingSessionTest.java`,
covering all six categories the playbook names: state creation, navigation identity, refresh
application, refresh rejection (re-scoped), pending transitions, and session close.

```
ClientBankingSessionTest    tests=24  failures=0  errors=0
Full JUnit suite            tests=412 failures=0  errors=0   (60 classes)
```

The suite was 388 before this milestone.

**GameTests — `./gradlew runGameTestServer --no-configuration-cache`:**

```
268 GAME TESTS COMPLETE IN 11.98 s
1 required test failed:
  - WorldStateFullBootstrapFallbackGameTests
      .aFailedFetchLeavesTheCacheUntouchedAndASuccessfulCommitWritesSnapshotAndVersionTogether
```

**Every banking GameTest passed.** The one failure is in world-state bootstrap fallback —
`ServiceNpcAssignmentsCache` snapshot/version file IO — and touches nothing this milestone went
near. The log alongside it shows `World bootstrap did not complete code=fetch_failed`, which is
the expected shape for a local run with no Rails instance reachable.

**Verified pre-existing.** `f4daef9` — the Milestone 0 commit, before any of this milestone's
work — was checked out into a detached worktree at `C:/bmbase` and run there, leaving this working
tree untouched. It produces the identical result:

```
baseline f4daef9 :  268 GAME TESTS COMPLETE IN 7.129 s
                    1 required tests failed :(
                      - afailedfetchleavesthecacheuntouchedandasuccessfulcommit...

milestone 2      :  268 GAME TESTS COMPLETE IN 11.98 s
                    1 required tests failed :(
                      - afailedfetchleavesthecacheuntouchedandasuccessfulcommit...
```

Same count, same test, same outcome. **Milestone 2 did not cause it**, and this epic is not the
right place to fix it — it belongs to whoever owns world-state bootstrap. The worktree was removed
afterwards.

Two build notes worth recording for later milestones:

- The task is **`runGameTestServer`**, not `gameTestServer`.
- It fails to configure under Gradle's configuration cache
  (`ProviderBackedFileCollectionSpec ... null array`) and needs `--no-configuration-cache`. This
  is unrelated to any test outcome.
- **Do not run `./gradlew clean` on this project casually.** It discards the NeoForm
  decompile/patch pipeline; recovery here came from the Gradle build cache and cost a rebuild.

**Not covered, by decision:** anything requiring a live `Screen`. Architecture Decision 0 —
`gameTestServer` is a dedicated server with no `Minecraft` instance, `src/test` has no client
bootstrap, and neither can instantiate a screen. That is precisely why the session is a plain
class with no client types: all of its behaviour is reachable from JUnit.

---

## 6. Milestone report

**Files inspected.** `ClientNetworkHandler`, `BankScreen`, `BankChequeIssuanceScreen`,
`BankAccountOpenedS2CPayload`, `BankTransferResultS2CPayload`, `BankItemSummary`,
`BankingTransferPacketService`, `BankAccountOpenedS2CPayloadTest` (to confirm what `src/test` can
construct), `build.gradle`.

**Files added.**
- `src/main/java/com/seggellion/britannia_mod/client/screen/bank/ClientBankingSession.java`
- `src/test/java/com/seggellion/britannia_mod/client/screen/bank/ClientBankingSessionTest.java`

**Files changed.** `ClientNetworkHandler.java`, `client/gui/BankScreen.java`,
`client/screen/BankChequeIssuanceScreen.java` — plus the three epic documents, recording the four
owner decisions of 2026-08-03 (cheque bounds, Milestone 1 approval, package correction).

**Decisions made.** Only ones already locked by Milestone 1. Two judgment calls inside them:
`BankTransferResultS2CPayload.Operation` reused as the pending discriminator rather than a new
enum, and the whole payload retained rather than exploded into fields. Both are described in §1.

**Tests run.** `./gradlew test` — 412 pass, 0 fail. `./gradlew runGameTestServer
--no-configuration-cache` — 268 run, 1 fail, all banking tests passing; see §5.

**Unresolved blockers.** None. The `WorldStateFullBootstrapFallbackGameTests` failure was verified
pre-existing against `f4daef9` and is out of scope for this epic (§5).

**Scope explicitly not performed.**
- The D9 refresh cutover — Milestone 4, deliberately (§2).
- No screen was rebuilt; `BankScreen` and `BankChequeIssuanceScreen` keep their layouts, their
  pending booleans and their `Component.literal` strings.
- No `BankMainScreen`, `BankBalanceScreen` or `BankBoxScreen`.
- No dragging, no grid, no `item_key`, no localization keys.
- One pre-existing design divergence found and **not** fixed: `BankChequeIssuanceScreen`'s Escape
  routes to `onCancelPressed` (Back), which design §5.2 forbids. It belongs to Milestone 7 and is
  noted in that method's javadoc.

---

## 7. Acceptance gate

> *A testable shared session exists and can support all four screens.*

`ClientBankingSession` holds every field §4 requires, is populated by the real refresh push, and
is covered by 24 tests with no client bootstrap. It supports four screens by construction: it is
static, outlives any screen, and is keyed by teller rather than by which screen is mounted.

The epic's automated safety net is 412 JUnit tests and 268 GameTests. All 412 pass; 267 of 268
GameTests pass, and the one failure is **proven pre-existing** against `f4daef9` (§5) and belongs
to world-state bootstrap, not to banking.

**Approved by the owner, 2026-08-03.** Milestone 3 (shared dialogue framework, including the
`DialoguePresentation.text(Component)` overload that Milestones 4, 5, 7, 9 and 11 all depend on)
is next.
