# Milestone 1 — Architecture decision and implementation plan

**Status:** **APPROVED by the owner, 2026-08-03.** All three flagged decisions (Decision 0, D12,
D9) approved as proposed; the inventory-full `Kind` is assigned to Milestone 15. Implementation
proceeds from Milestone 2.
**Predecessor:** `UltimaCraft_Bank_Interface_Rebuild_Milestone_0_Report.md` (committed `f4daef9`)
**Design:** `UltimaCraft_Bank_Interface_Rebuild_Design.md`
**Playbook:** `UltimaCraft_Bank_Interface_Rebuild_Codex_Playbook.md`
**Baselines:** NeoForge `banking` @ `f4daef9`; Rails `banking` @ `1267195`
**Scope:** Documentation only. No production code. Playbook Milestone 1 forbids implementing
screens, silently adding partial-stack behaviour, and treating a client grid cell as a persistent
bank slot — none of which appear below.

Playbook §1 rule 8 applies: this stops for owner approval before any production implementation
begins.

---

## 1. The constraint that shapes everything else

Milestone 0 found that no test in either repository touches a client screen, and asked who owns
building a harness. Investigating that first changed several later decisions, so it goes first.

`build.gradle` defines three run configurations (`:49-75`). `gameTestServer` (`:68`) is a
**dedicated server** — `workingDirectory = run/gametest`, the same `--nogui` shape as `server`.
There is no `gameTestClient`. `src/test` is plain JUnit 5 (`junit-bom:5.11.4`, `:37-38`) with no
Minecraft bootstrap at all.

**A dedicated server has no `Minecraft` instance, no `Font`, and no `GuiGraphics`. Neither harness
can instantiate a `Screen`.** Design §19.2's fourteen client-interaction test categories have
nowhere to run, and building somewhere would mean standing up a headless client bootstrap — a
large, fragile piece of infrastructure that this epic has no other use for.

### Decision 0 — do not build a screen-test harness; make the logic not need one

**Every behaviour design §19.2 asks us to test is extracted into a plain class that holds no
client type, and is covered by JUnit on `src/test`. `Screen` subclasses become thin: construct
widgets, forward input, draw.**

| Extracted, JUnit-tested | Left in the `Screen`, untested |
| --- | --- |
| session state, refresh application, pending transitions | widget construction |
| drag state machine (threshold, transitions, cancellation) | `render` calls |
| grid geometry (cell hit-testing, index↔position, scroll clamping) | input forwarding |
| amount validation and parsing | tooltip drawing |
| result→message mapping | |
| deposit eligibility/routing preview | |

This is not a workaround; it is the only structure the build supports, and it happens to be the
one that makes the drag engine testable at all. The residue — does it *look* right, does it feel
right at GUI scale 4 — is exactly what design §19.4's owner visual gate and Milestone 18's smoke
tests already exist to cover. **We are not leaving that untested; we are testing it the way this
project already tests visuals, by looking at it.**

Consequence for every later milestone: if a behaviour cannot be tested, that is a signal it is in
the wrong class. Playbook §1 rule 6 stays satisfiable throughout.

---

## 2. The fourteen required decisions

### D1 — Screen class boundaries

Four separate classes, no shared base class:

| Class | Replaces | Milestone |
| --- | --- | --- |
| `BankMainScreen` | the hub half of `BankScreen` | 4 |
| `BankBalanceScreen` | new | 5 |
| `BankChequeIssuanceScreen` | rebuilt in place | 7 / 8b |
| `BankBoxScreen` | the grid/list half of `BankScreen` | 9–16 |

**`BankChequeIssuanceScreen` keeps its name.** Design §13.1 permits a rename; renaming buys
nothing, churns `ClientNetworkHandler`'s dispatch, and risks the service integration §12.5 says to
preserve.

**Package: `com.seggellion.britannia_mod.client.screen`, with files under `client/screen/`.**
Milestone 0 §2 found 13 of 21 files in `client/gui/` declare `client.screen`. New files will not
extend that divergence. The pending §2 documentation correction does not block this — the package
name is determined by what compiles today, not by what the design note says.

Supporting classes go in a new `client/screen/bank/` package so the epic's surface is one
directory: `BankDialogueFrame`, `BankStoredItemGrid`, `BankInventoryGrid`, `BankDragController`,
`BankStatusPresenter`, `BankGridGeometry`.

### D2 — Shared dialogue composition strategy

**Composition. No screen inherits from another.**

`DialoguePresentation` (`client/gui/DialoguePresentation.java`, package `client.screen`) already
renders the left and centre of the dialogue family: portrait via
`PortraitDownloader.getPortrait(name, gender)`, centred name, optional profession, word-wrapped
body (`:59-129`). `optionButton` (`:41-57`) already builds the right-hand vertical button stack at
`layout.buttonStartX()` with `20 + BUTTON_GAP` spacing. **Roughly two thirds of design §7's
reusable frame already exists and is proven by the quest and service dialogue screens.**

`BankDialogueFrame` composes over it and adds what is missing: an action-shaped button column, the
status region, and optional centre form controls.

Two additive, non-breaking changes to `DialoguePresentation`:

- **`text(Component)` overload.** `text(String)` (`:37`) wraps a `String` in
  `Component.literal(...).withStyle(UO_STYLE)` — the single chokepoint that makes the banking UI
  untranslatable. The overload applies `UO_STYLE` to an already-built `Component`, so
  `Component.translatable(...)` can flow through unchanged.
- **A `renderDialogue` overload taking `Component` name and body**, alongside the existing
  `DialogueViewModel` one.

**`DialogueViewModel` is not modified.** It is all-`String` and shared with the quest and service
dialogue systems; changing it to carry `Component` would ripple into unrelated features and
violate Playbook §1 rule 5.

One layout fact the Bank Box must respect: `renderPaperBackground` (`:131-148`) paints parchment
only for `TOP_SECTION_HEIGHT` (134 px) and dims the rest with `0xCC000000`. **The parchment is a
top banner, not a full-screen frame.** The bank and inventory grids sit on the dimmed region
below it.

### D3 — Banking-session / view-model ownership

**`ClientBankingSession` — a client-side singleton that outlives every screen.**

The refresh push arrives asynchronously and cannot know which screen is open (Milestone 0 §4.1).
Passing a snapshot screen-to-screen would mean the network handler has to find and mutate whatever
screen happens to be mounted — which is exactly the coupling that produces today's
`setScreen`-on-every-success behaviour. A singleton inverts it: the handler updates one object,
screens read from it.

Holds: teller entity id, teller name, teller gender, city display name, gold/silver/copper, current
weight, weight limit, ordered `List<BankItemSummary>`, the selected stored-item public id, and the
pending operation. Plain Java, no client types, JUnit-testable per Decision 0.

**It is presentation state, never authority** (design §6.1). It is populated only from
`BankAccountOpenedS2CPayload` and never computes a balance, weight, or eligibility result of its
own.

### D4 — Back and Escape

- **Back** → `Minecraft.setScreen(new BankMainScreen())`, reading state from the session. The
  session is untouched; the banking interaction continues.
- **Escape** → closes banking entirely: `onClose()`, then `ClientBankingSession.clear()`.
- **Escape is never Back**, on any screen, including the Main screen (where it closes).

All four screens are `isPauseScreen() == false`, matching `BankScreen:645`.

Closing during a pending operation does **not** cancel the request (design §5.3). The session is
cleared, so a late result finds no session and is dropped — the client fabricates nothing. A
subsequent reopen gets fresh authoritative state from `bank.open`.

### D5 — Custom drag-capable screen versus real menu/container

**Custom drag-capable screen. Design §13.3's default stands.**

§13.3 allows a milestone to overturn this only if reconnaissance proves a real menu/container
architecture exists that preserves the same external transaction guarantees. Milestone 0 §3.1
proved the opposite: `addSlot(` appears **zero times** in the repository; `ServiceNpcSpawnMenu` is
a real `AbstractContainerMenu` with no slots and `quickMoveStack` returning `EMPTY`; the three
`ChestMenu` block entities are vanilla menus over a **local `Container`**, which is precisely the
"fake local container whose state is mistaken for authority" §13.3 forbids. The overturn condition
is not met.

### D6 — Whole-stack deposit scope

**Whole live stack, always.** No quantity picker, no partial stack, no right-click split, no
shift-click. Unchanged from design §10.2 and §3's non-goals.

The request carries the **slot index only**. The server reads the live stack from that slot; the
client never states a count. This means "whole stack" needs no enforcement — there is no field in
which a partial count could be expressed.

### D7 — Bank grid dimensions and overflow

**Nine columns × five visible rows, 18 px cells, vertical scrolling.**

- Nine columns matches design §9.3 and Minecraft convention.
- Five rows over the dimmed region below the 134 px parchment band leaves room for the 3×9 + 1×9
  player inventory beneath it.
- **Scrolling, not paging** — `BankScreen` and `NpcCatalogScreen` both scroll, `mouseScrolled` is
  already the established idiom, and paging has no precedent here.
- Empty cells always render, to communicate structure (§9.3).

**The grid is a view over an ordered list, never a persistent slot map.** Cell *n* shows
`bankItems.get(n + scrollOffset)`. A cell index is never sent anywhere, never persisted, and never
survives a refresh. Selection is held as a **public UUID** in the session, so it stays correct when
a refresh reorders or removes rows — and clears itself when the selected id is absent from the new
list (design §9.6, Playbook Milestone 15).

`BankGridGeometry` owns the arithmetic — cell hit-testing, index↔position, scroll clamping — as a
plain class per Decision 0.

### D8 — Stored-item render summary format

**Constrained by design §9.5.3: icons render from `item_key` alone. This decision covers plumbing
only.**

1. `BankItemSummary` gains `@Nullable String itemKey`, alongside the existing `displayName` and
   `count`.
2. `BankingOpenResponseParser` gains `lenientItemKey`, mirroring `lenientName` (`:118-125`)
   exactly: wrong JSON type, absent, or over-long degrades to `null` rather than failing the
   account view.
3. `BankAccountOpenedS2CPayload`'s hand-written codec carries it using the same nullable-string
   pattern already used for `displayName`, under its own byte bound.
4. The grid resolves it via `ResourceLocation.tryParse` → `BuiltInRegistries.ITEM.getOptional`.
   **Any failure at any step degrades that one cell to the unknown icon with a readable fallback
   name, and never fails the grid.**

No render-snapshot field. No `payload` to the client. Not reopened.

### D9 — Post-transaction refresh strategy

**Constrained by design §6.2: the server push is adopted. What this decision settles is how the
client consumes it.**

Milestone 0 §4.1 established that `refreshAccount` fires on all **six** confirm paths, and that
`handleBankAccountOpened` consumes it as `setScreen(new BankScreen(payload))` — destroying and
rebuilding the screen on every success.

**Change the handler to update the session, not the screen:**

```
on BankAccountOpenedS2CPayload:
    session.apply(payload)
    if no banking screen is currently open:   -> setScreen(new BankMainScreen())   // first open
    else                                       -> leave the current screen mounted
```

Screens read the session every frame, so a refresh is visible immediately without reconstruction.
This keeps grid selection, scroll position, and the amount field intact across a successful
transaction, and stops a successful drag-deposit from ejecting the player out of the Bank Box.

**No client-initiated refresh request is added.** Design §6.2 forbids adding one speculatively;
Milestone 18's multiplayer cases (§19.3) are where the need would be demonstrated, and it would
then be its own contract change with a stated reason.

### D10 — Deposit All Coins server transaction shape

Owned by Milestones 6a (Rails) and 6b (NeoForge). This decision fixes only the **client** shape so
6b cannot drift:

- one new C2S payload, `BankDepositAllCoinsRequestC2SPayload(entityId)` — **entity id only**;
- the client sends no totals, no denominations, no slot list, and no count;
- the server sweeps the live inventory, classifying with `CurrencyItemRegistry.isCurrencyStack`,
  which Milestone 0 §3.6 confirmed already has exactly the right polarity (containers excluded);
- the result reports through the existing `BankTransferResultS2CPayload` channel, and success
  refreshes via the same `refreshAccount` push as every other mutation.

§11.4's atomicity requirement is 6a's substance. **No client packet loop**, under any
circumstances.

### D11 — Pending-state ownership

**The session owns exactly one nullable `PendingOperation`. Screens own no pending booleans.**

Today `BankScreen` has `depositPending` and `withdrawalPending`, and they are cleared only by
object destruction (Milestone 0 §4.1). With the screen no longer destroyed on success, that
mechanism disappears and pending must be cleared explicitly.

One global lock across all four screens satisfies design §10.9's "no second banking mutation may
begin". It is cleared on any `BankTransferResultS2CPayload` **and** on any
`BankAccountOpenedS2CPayload` — because a refresh push *is* the success signal, and is the only
signal a confirmed operation produces.

Every control that can start a mutation renders disabled while pending, on every screen.

### D12 — Stale-state and duplicate-request strategy

**No revision or sequence field is added.** Design §14.2 requires justification before adding one;
here is the justification for not.

- **Refreshes cannot arrive out of order.** Every push goes through
  `PacketDistributor.sendToPlayer` on one ordered connection. Arrival order is send order.
- **Every push is a complete authoritative snapshot** re-fetched by `bank.open`, not a delta.
  Applying the latest wholesale is correct by construction; there is no partial merge that could
  be corrupted by ordering.
- **Duplicate requests are prevented client-side by D11's pending lock**, and independently
  server-side — `BankingWithdrawalProxyService` already carries an `IN_FLIGHT` guard keyed by bank
  item id (`:166`, `:199`, `:244`).

**Consequence for Milestone 2's acceptance gate.** Its required tests name "applying a newer
refresh" and "rejecting or ignoring an older refresh". With no discriminator these are re-scoped
to: *a refresh replaces session state wholesale*, and *a result arriving after the session is
cleared is dropped rather than applied*. **This re-scoping needs owner acknowledgement**, because
it changes a gate the playbook already wrote.

If Milestone 18 demonstrates a real stale-state failure, a discriminator becomes its own change
with a stated reason — not a speculative addition now.

### D13 — Localization strategy

- **Key namespace:** `gui.britannia_mod.bank.*` — e.g. `gui.britannia_mod.bank.main.title`,
  `gui.britannia_mod.bank.action.open_box`, `gui.britannia_mod.bank.status.reconciliation_required`.
- **Prerequisite: `DialoguePresentation.text(Component)` (D2), delivered in Milestone 3.** Without
  it every string funnels through a `String`→`literal` call and Playbook §3.1's per-milestone
  translatability gates cannot be met by Milestones 4, 5, 7, 9 or 11.
- Every new user-visible string is `Component.translatable` from the milestone that introduces it.
  No "localize it later" pass.
- `BankStatusPresenter` maps `(Operation, Kind)` → translation key, replacing the six
  `Component.literal` constants in `BankScreen` and the four in `BankChequeIssuanceScreen`.
- `BankItemSummary.FALLBACK_NAME` ("Stored item") gets a key but stays in the render contract, not
  screen copy — design §17.1's stated exception.
- Only `en_us.json` is populated. Adding locales is not this epic's work.

### D14 — Migration path from the old `BankScreen`

**Cutover at Milestone 4, deletion at Milestone 19.**

Milestone 4 repoints `handleBankAccountOpened` from `new BankScreen(payload)` to D9's session
update plus `BankMainScreen`. From that moment `BankScreen` is unreachable and the new hub is
live, with clearly-marked placeholder destinations until Milestones 5, 7 and 9 land.

**Both interfaces are never live at once.** Design §3's non-goals forbid keeping the old combined
screen as an alternate interface, and two reachable banking UIs would double the surface every
subsequent milestone has to keep working.

`BankScreen.java` is deleted at Milestone 19 along with the double-click constants and fields, the
two scrolling row lists, and the Deposit/Withdraw/Checks arrangement. The proven server services,
packet contracts, eligibility rules, reconciliation behaviour, cheque data and currency conversion
underneath it are all preserved — none of them live in that file.

---

## 3. Rejected alternatives

| Rejected | Why |
| --- | --- |
| **Real `AbstractContainerMenu` / `Slot` Bank Box** | The bank is an external store addressed by public UUID, not a `Container`. A vanilla menu needs a local container whose contents are authority — design §13.3 forbids exactly that, and Milestone 0 found no precedent to build on. |
| **Headless client test harness** | The build has no client test run; standing one up is large, fragile infrastructure for one epic. Decision 0 extracts the logic instead, which is testable *and* better factored. |
| **Screen-to-screen snapshot passing** | Makes the network handler hunt for the mounted screen — the coupling that causes today's replace-on-success behaviour. |
| **Keeping `setScreen` on refresh** | Destroys selection, scroll and field contents on every success, and ejects the player from sub-screens (Milestone 0 §4.1). |
| **Adding a revision/sequence field now** | Ordering is guaranteed by the transport and pushes are whole snapshots. §14.2 requires justification; there is none yet (D12). |
| **Per-screen pending flags** | Cannot express design §10.9's cross-screen mutation lock, and today's version is cleared only by object destruction — which D9 removes. |
| **Paging the bank grid** | No precedent; scrolling is the established idiom in both `BankScreen` and `NpcCatalogScreen`. |
| **Renaming `BankChequeIssuanceScreen`** | Churn with no benefit; risks the service integration §12.5 preserves. |
| **Modifying `DialogueViewModel` to carry `Component`** | Shared with quest and service dialogue; unrelated blast radius (rule 5). An overload achieves the same thing additively. |
| **Deleting `BankScreen` before Milestone 19** | Its behaviour is the parity reference for Milestones 13–16. |

---

## 4. Security implications

The trust boundary is **unchanged**, and that is the point: no decision above moves authority
client-ward.

- Deposits keep sending a **live slot index**; withdrawals keep sending a **public item UUID**;
  currency keeps sending **key + amount**. The server re-derives every fact (design §4.3, §18).
- **`item_key` travels client-ward only** and never returns as an authority claim (design §9.5.4).
  Withdrawal authority remains the public id.
- **The drag gesture changes no packet.** A release sends the same `BankDepositRequestC2SPayload`
  the double-click sent, so all three server-side routes (item / currency / cheque) and every
  existing validation apply unchanged. Milestone 14 verifies this rather than building it.
- **Client eligibility remains cosmetic.** `BankItemEligibility` greys cells out; a modified client
  sending an ineligible slot is rejected by the same already-tested server path.
- **The grid cell index is never transmitted.** Selection is a UUID, so a modified client cannot
  assert a persistent bank position — the risk design §10.5 exists to prevent.
- **`ClientBankingSession` holds no secret and grants no capability.** It is a cache of what the
  server already sent this player.
- New hostile-input surfaces introduced by this epic: exactly one, `item_key`, handled leniently
  per D8. Milestone 6a adds a second on the Rails side, owned there.

---

## 5. Packet implications

| Payload | Change | Milestone |
| --- | --- | --- |
| `BankAccountOpenedS2CPayload` | **Add `itemKey` to each item** (nullable string, bounded) | 10 |
| `BankTransferResultS2CPayload` | **Add an inventory-full `Kind`** (Milestone 0 §3.4) | 15 or 17 — assign below |
| `BankDepositAllCoinsRequestC2SPayload` | **New**, entity id only | 6b |
| `BankChequeIssuanceRequestC2SPayload` | **Add denomination** (owner decision, design §12.3.1) | 8b |
| `BankDepositRequestC2SPayload` | **None** — drag reuses it verbatim | — |
| `BankWithdrawalRequestC2SPayload` | **None** | — |
| `BankCurrencyWithdrawalRequestC2SPayload` | **None** | — |

**Assignment requested: the inventory-full `Kind` belongs to Milestone 15**, which is where
"inventory-full failure is readable" is already an acceptance criterion. Milestone 17 then
consumes it rather than inventing it.

Both `Kind` and `Operation` are transmitted via `buffer.writeEnum`/`readEnum`, i.e. by **ordinal**.
New constants must therefore be **appended**, never inserted, and a client receiving an unknown
ordinal must be checked for graceful failure — this is a real mixed-version concern under §1.1
rule 3 even though both ends are NeoForge.

---

## 6. Data-contract implications

| Contract | Status |
| --- | --- |
| `item_key` on `bank_items` | **Already sent by Rails.** Client-side plumbing only; no Rails change. |
| Deposit All Coins | **New Rails contract** — Milestone 6a. |
| Multi-denomination cheque issuance | **New Rails contract** — Milestone 8a, incl. the §8a.1 bounds decision. |
| Client-initiated account refresh | **Not added** (D9). |
| Revision / stale-state discriminator | **Not added** (D12). |
| `bank_cheques` schema | **Unchanged** — design §12.3.1. |
| Cheque redemption | **Unchanged** — `coin_mix` already pays a mix. |

Two Rails contracts, both already scheduled, both bound by §1.1 rule 2 (Rails accepts first). No
third appears anywhere in this architecture.

---

## 7. Test plan

Per Decision 0: JUnit on `src/test` for extracted logic, GameTest for anything touching a live
server, owner visual gate for appearance.

**JUnit — new (`src/test/java/.../client/bank/`)**

- `ClientBankingSessionTest` — creation from payload; refresh replaces wholesale; pending set and
  cleared by result; pending cleared by refresh; selection survives a refresh that keeps the item;
  selection clears when the item is gone; clear-on-close; late result after clear is dropped.
- `BankDragControllerTest` — every state transition; threshold not met → click not drag; cancel on
  release outside, on Escape, on close, on re-init, on source emptied; duplicate release sends once.
- `BankGridGeometryTest` — hit-testing at each GUI scale factor; index↔position; scroll clamping at
  both ends; empty grid; partial last row; overflow.
- `BankStatusPresenterTest` — every `(Operation, Kind)` pair maps to a distinct key; no pair falls
  through to a generic message unintentionally.
- `BankAmountValidationTest` — empty, zero, negative, malformed, overflow, whitespace, leading
  zeros, denomination unit multiples.
- Extend `BankingOpenResponseParserIdentityTest` — `item_key` present, absent, wrong type,
  over-long, malformed; and that each degrades only its own cell.
- Extend `BankAccountOpenedS2CPayloadTest` — round-trip with and without `itemKey`.

**GameTest — extend existing files**

- `BankingTransferPacketServiceGameTests` — drag-originated deposit routes identically for all
  seven Milestone 14 categories; inventory-full withdrawal reports the new `Kind`.
- New Deposit All Coins tests per Playbook Milestone 6's list.
- New multi-denomination cheque tests per Playbook Milestone 8b's list.

**Not automated, by decision:** rendering, layout at GUI scales, tooltips, focus order, drag feel.
Covered by Milestone 18's smoke matrix and design §19.4's owner gate.

---

## 8. File-level implementation map

**New — `client/screen/`**

| File | Milestone |
| --- | --- |
| `BankMainScreen.java` | 4 |
| `BankBalanceScreen.java` | 5 |
| `BankBoxScreen.java` | 9 |

**New — `client/screen/bank/`**

| File | Responsibility | Milestone |
| --- | --- | --- |
| `ClientBankingSession.java` | session singleton, pending, selection | 2 |
| `BankDialogueFrame.java` | parchment + portrait + body + action column + status | 3 |
| `BankStatusPresenter.java` | `(Operation, Kind)` → translation key | 3 |
| `BankGridGeometry.java` | cell arithmetic, hit-testing, scroll clamping | 9 |
| `BankStoredItemGrid.java` | bank grid widget, single-click selection | 11 |
| `BankInventoryGrid.java` | 27 + 9 player slots, eligibility shading | 11 |
| `BankDragController.java` | drag state machine | 12 |

**Modified**

| File | Change | Milestone |
| --- | --- | --- |
| `client/gui/DialoguePresentation.java` | `text(Component)` + `renderDialogue` overloads | 3 |
| `network/ClientNetworkHandler.java` | `handleBankAccountOpened` → session (D9); route results to session | 2 / 4 |
| `service/banking/BankItemSummary.java` | `+ @Nullable String itemKey` | 10 |
| `service/banking/BankingOpenResponseParser.java` | `+ lenientItemKey` | 10 |
| `network/payload/BankAccountOpenedS2CPayload.java` | carry `itemKey` | 10 |
| `network/payload/BankTransferResultS2CPayload.java` | `+` inventory-full `Kind` | 15 |
| `network/payload/BankChequeIssuanceRequestC2SPayload.java` | `+` denomination | 8b |
| `client/screen/BankChequeIssuanceScreen.java` | rebuild on the frame; denominations | 7 / 8b |
| `network/NetworkHandler.java` | register the Deposit All Coins payload | 6b |
| `assets/britannia_mod/lang/en_us.json` | `gui.britannia_mod.bank.*` keys | 3–16, incrementally |

**New — server**

| File | Milestone |
| --- | --- |
| `network/payload/BankDepositAllCoinsRequestC2SPayload.java` | 6b |
| `service/banking/BankingDepositAllCoinsProxyService.java` | 6b |

**Deleted — Milestone 19 only**

`client/gui/BankScreen.java`.

---

## 9. Open items carried into implementation

**All four were closed by the owner on 2026-08-03.**

| # | Item | Outcome |
| --- | --- | --- |
| 1 | Cheque amount bounds (Playbook §8a.1) | **Absolute bounds retained, unchanged.** Bounds are value-denominated, so the minimum costs 500 gold / 50 000 silver / 5 000 000 copper. Gold stays the ordinary denomination. No constant changes in 8a. |
| 2 | Baseline package correction (Milestone 0 §2) | **Applied** to the design document. |
| 3 | Milestone 2 gate re-scoping under D12 | **Approved.** The two stale-refresh tests become "a refresh replaces state wholesale" and "a result arriving after the session is cleared is dropped". |
| 4 | Inventory-full `Kind` → Milestone 15 | **Confirmed.** |

Nothing remains open. Milestone 2 may begin.

---

## 10. Acceptance gate

> *The owner approves the architecture before production implementation begins.*

All fourteen required decisions are locked in §2, with the two pre-settled ones (§9.5.3 icons,
§6.2 refresh) treated as constraints rather than reopened. Rejected alternatives, security,
packet, and data-contract implications, the test plan, and the file-level map are in §3–§8.

**Three things in this document differ from what the playbook currently assumes, and need explicit
approval rather than silent acceptance:**

1. **Decision 0** — no screen-test harness will be built; logic is extracted to be JUnit-testable
   instead, and appearance stays on the owner visual gate. This is the largest structural call
   here.
2. **D12** — no stale-state discriminator, which re-scopes two named tests in Milestone 2's
   acceptance gate.
3. **D9** — the client stops replacing the screen on refresh, which changes observable behaviour
   today (a confirmed cheque will no longer bounce the player back to the bank screen).

**Stopping here for owner approval. No production code will be written until this gate passes.**
