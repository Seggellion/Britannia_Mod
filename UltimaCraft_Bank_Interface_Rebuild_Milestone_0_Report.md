# Milestone 0 — Repository and banking reconnaissance

**Status:** Complete, awaiting owner review at the acceptance gate
**Playbook:** `UltimaCraft_Bank_Interface_Rebuild_Codex_Playbook.md` (revised 2026-08-03)
**Design:** `UltimaCraft_Bank_Interface_Rebuild_Design.md` (revised 2026-08-03)
**Baselines inspected:** NeoForge `Britannia_Mod` @ `banking` / `f94b42e`; Rails `ultimacraft-website` @ `banking` / `1267195` (both confirmed with `git branch --show-current` per Playbook §1.1 rule 4)
**Scope:** Documentation only. No production code, no packet changes, no UI refactor, no deletions.

Two decisions were treated as closed throughout and are not reopened anywhere below:
stored-item icons render from `item_key` alone (design §9.5.3), and post-transaction refresh
adopts the existing server push (design §6.2).

---

## 1. Confirmation of the 2026-08-03 recorded findings

Each was re-read against `f94b42e` / `1267195` and confirmed, not re-derived.

| Recorded finding | Verdict | Evidence |
| --- | --- | --- |
| Baseline screen is `client.gui.BankScreen`, not `client.screen.BankScreen` | **Confirmed with a correction — see §2** | `src/main/java/com/seggellion/britannia_mod/client/gui/BankScreen.java` |
| Double-click gesture real and present, `DOUBLE_CLICK_MS = 400` | Confirmed | `BankScreen.java:73`, deposit at `:554`, withdrawal at `:597` |
| `display_name` / `count` reach the client; `item_key` sent by Rails, dropped by the parser | Confirmed | `bank_item_json` at `banking_controller.rb:651-653` sends all three; `BankingOpenResponseParser` has `lenientName` (`:118`) and `lenientCount` (`:127`) and **no `item_key` reader** |
| Post-transaction refresh already exists as server push via `refreshAccount` | Confirmed, and **broader than recorded** — see §4.1 | `BankingTransferPacketService.java:333` |
| Teller portrait resolves correctly via `tellerGender` | Confirmed | `BankAccountOpenedS2CPayload.java:37`, allow-list at `:57` |
| Deposit All Coins exists nowhere in either repository | Confirmed | `deposit_all` / `all_coins` / `depositAll` return nothing in `src/` (NeoForge) or `app lib config test` (Rails); `config/routes.rb:260-268` lists exactly nine banking routes, none bulk |

---

## 2. Correction requested: the baseline package (owner approval needed)

Design §"Note on the baseline path" says `BankScreen` lives in package `client.gui` and
`BankChequeIssuanceScreen` in `client.screen`, and warns the split is genuine. **The file paths
are split; the packages are not.**

`src/main/java/com/seggellion/britannia_mod/client/gui/BankScreen.java:1` declares:

```java
package com.seggellion.britannia_mod.client.screen;
```

Both classes are in package `com.seggellion.britannia_mod.client.screen`. `BankScreen`'s *file*
merely sits in the `client/gui/` directory. `ClientNetworkHandler.java:46` imports it as
`com.seggellion.britannia_mod.client.screen.BankScreen`, which is the compiling proof.

This is not isolated. Of 21 files under `client/gui/`, **13 declare `client.screen`**, 6 declare
`client.gui`, one declares `client.gui.screen` (`ArchitectScreen`), and `RenameHouseScreen.java`
declares no package on its first line. `DialoguePresentation.java` — which every dialogue screen
depends on — is another `client/gui/` file in package `client.screen`, which is why `BankScreen`
uses it with no import.

**Practical consequence for this epic:** the design's advice to "verify the package before
importing either" is right, but the rule to apply is *read the `package` line, never the
directory*. New screen classes should be placed so that path and package agree, and the milestone
that creates them should state which package the four screens live in. Directory/package
divergence is pre-existing across the whole client tree and is **not** in this epic's scope to
normalise.

Proposed edit to design §"Note on the baseline path" is held pending approval; no document was
modified.

---

## 3. The six items left open by the 2026-08-03 pass

### 3.1 Draggable-widget and inventory-grid precedent, repository-wide

Searched the whole of `src/main/java` for `AbstractContainerMenu`, `Slot`, `addSlot`,
`mouseDragged`, `mouseReleased`, `renderItem`, `renderItemDecorations`, `SimpleContainer`,
`MenuType`, `ContainerScreen`.

**There is no drag-and-drop precedent of any kind, and no custom slot-grid precedent.**

| Candidate | What it actually is | Usable as precedent? |
| --- | --- | --- |
| `ServiceNpcSpawnMenu` + `ServiceNpcSpawnScreen` | A real `AbstractContainerMenu` / `AbstractContainerScreen<>` pair with **zero slots**. `quickMoveStack` returns `ItemStack.EMPTY` (`ServiceNpcSpawnMenu.java:72`); `inventoryLabelY = 10_000` pushes the vanilla label off-screen (`ServiceNpcSpawnScreen.java:39`). The menu exists for its server-side `stillValid` re-validation, not to move items. | Precedent for *server-validated screen opening*. **Not** for slots or grids. |
| `BritanniaChestBlockEntity`, `ArmoireBlockEntity`, `TrashBarrelBlockEntity` | Vanilla `ChestMenu.threeRows(...)` over a local `Container`. Real slot grids and real vanilla drag — all of it supplied by vanilla, none of it written here. | **No.** Requires a local `Container` whose contents are authority. The bank is an external store addressed by public UUID (design §13.3). |
| `NpcCatalogScreen` | The repo's only `mouseDragged` (`:255`) and `mouseReleased` (`:270`) — a **scrollbar-thumb drag**, not an item drag. Source of `BankScreen`'s row-list pattern and its GUI-scale-corrected `enableScissor` workaround (`BankScreen.java:498`). | Precedent for scrolling, scissoring, and manual row hit-testing. **Not** for item drag. |
| `ArchitectScreen` | Plain `Screen`; `renderItem` + `renderItemDecorations` at `:192-193` — a single item drawn per list row. | Precedent for drawing a stack. **Not** for a grid. |

Hard facts:

- **`addSlot(` appears zero times in the repository.** No `extends Slot`, no `new Slot(`.
- The only three `graphics.renderItem` sites (`BankScreen`, `NpcCatalogScreen`, `ArchitectScreen`)
  all render one stack per **list row**. Nothing anywhere lays out an *n×m* stack grid.
- Nothing renders a ghost/carried stack, implements a movement threshold, or does GUI-scale-aware
  hit testing beyond the one scissor workaround.

**Consequence.** Design §13.3's "custom drag-capable screen" choice stands unchallenged — the
clause allowing a milestone to overturn it "when reconnaissance proves a real menu/container
architecture now exists" is not triggered. But the corollary is the more important finding:
**Milestone 12 builds on nothing.** Hit testing, ghost rendering, the drag threshold, and
GUI-scale correctness are all first-of-their-kind in this repository. The Playbook's instruction
to time-box a spike before Milestones 13–15 depend on it is supported by the evidence, and should
be treated as binding rather than advisory.

### 3.2 Current banking test coverage, both repositories

**NeoForge — 219 GameTests across 15 banking files, plus 83 JUnit tests across 11.**

| GameTest file | Tests | | JUnit file | Tests |
| --- | ---: | --- | --- | ---: |
| `BankItemCodecGameTests` | 31 | | `BankTransferReceiptStoreTest` | 19 |
| `BankingProxyServiceGameTests` | 19 | | `BankingOpenResponseParserTest` | 25 |
| `BankingCurrencyWithdrawalProxyServiceGameTests` | 17 | | `CoinConversionTest` | 14 |
| `BankItemWeightGameTests` | 16 | | `BankAccountOpenedS2CPayloadTest` | 9 |
| `BankItemEligibilityGameTests` | 15 | | `BankingOpenResponseParserIdentityTest` | 7 |
| `BankingChequeRedemptionProxyServiceGameTests` | 15 | | `BankingCapabilityTest` | 6 |
| `BankItemEnvelopeGameTests` | 14 | | `BankingOpenClientTest` | 5 |
| `BankingDepositProxyServiceGameTests` | 14 | | `BankDepositRequestC2SPayloadTest` | 4 |
| `BankingCurrencyDepositProxyServiceGameTests` | 13 | | `BankWithdrawalRequestC2SPayloadTest` | 2 |
| `BankingWithdrawalProxyServiceGameTests` | 13 | | `BankTransferResultS2CPayloadTest` | 1 |
| `BankTransferReconciliationServiceGameTests` | 12 | | `BankTransferReconciliationServiceTest` | 1 |
| `BankingChequeIssuanceProxyServiceGameTests` | 12 | | | |
| `BankingTransferPacketServiceGameTests` | 12 | | | |
| `BankTransferPlayerDurabilityGameTests` | 8 | | | |
| `BankTransferReceiptGameTests` | 8 | | | |

**Rails — roughly 530 banking assertions across 47 test files**, covering controllers (`banking_controller_test` 26, `banking_deposit_prepare_test` 22, `banking_cheque_issuance_prepare_test` 15, …), models (`bank_transfer_operation_test` 36, `bank_cheque_test` 28, `bank_item_test` 22, …), services (`payload_validator_test` 35, `create_test` 23, `confirm_test` 20, …), and a dedicated concurrency file per protocol. Three shared support modules exist: `test/support/banking_test_support.rb`, `bank_account_test_support.rb`, `bank_transfer_operation_test_support.rb`.

**The shape of that coverage is the finding, not the volume.**

> **Not a single test in either repository touches a client screen class.**
> `grep -rlE 'client\.(gui|screen)'` over `src/test/` and `src/main/java/.../gametest/` returns
> nothing. There is no screen test, no layout test, no input test, no GUI-scale test, and no
> harness capable of instantiating a `Screen` headlessly.

Coverage is deep on exactly the half of the system this epic is *not* rebuilding (server
protocol, persistence, reconciliation, validation) and **zero** on exactly the half it *is*
(presentation, navigation, input, drag).

Every milestone from 2 onward carries Playbook §1 rule 6 ("add or update tests within the
milestone that introduces the behavior"), and design §19.2 lists fourteen client-interaction test
categories. **All of them currently have nowhere to run.** Establishing a client test harness is
unowned work that no milestone claims, and it blocks Milestones 2, 3, 4, 5, 7, 9, 11 and 12
simultaneously. This is raised for Milestone 1 to assign explicitly — see §5.

Note the existing seam pattern worth reusing: `BankingTransferPacketService.ResultSender`
(`:49-61`) and `BankingProxyService.AccountScreenSender` are test-only injection points that let
a GameTest observe an outgoing packet without a client connection. The same technique is the
cheapest route to testing the session context's *inputs* even if rendering stays untested.

### 3.3 Currency-deposit and cheque-redemption routing, at the packet level

**There is no currency-deposit packet and no cheque-redemption packet.** Both are the ordinary
deposit packet, routed server-side. Six banking payloads are registered in `NetworkHandler`
(`:558-606`) and that is the complete set:

| Payload | Direction | Carries |
| --- | --- | --- |
| `BankAccountOpenedS2CPayload` | S2C | teller name, teller gender, entity id, city display name, weight limit, current weight, gold/silver/copper, `List<BankItemSummary>` |
| `BankTransferResultS2CPayload` | S2C | `(Operation, Kind)` — a closed enum pair, no strings |
| `BankDepositRequestC2SPayload` | C2S | entity id, **live slot index** |
| `BankWithdrawalRequestC2SPayload` | C2S | entity id, bank item public UUID |
| `BankCurrencyWithdrawalRequestC2SPayload` | C2S | entity id, currency key, amount |
| `BankChequeIssuanceRequestC2SPayload` | C2S | entity id, amount |

Routing happens in `BankingTransferPacketService.handleDeposit` (`:81-93`), reading the **live
server-side slot**, in this order:

1. `CurrencyItemRegistry.isCurrencyStack(liveSlot)` → `handleCurrencyDeposit` → `BankingCurrencyDepositProxyService`
2. `liveSlot.getItem() == ItemRegistry.BANK_CHEQUE.get()` → `handleChequeRedemption` → `BankingChequeRedemptionProxyService`
3. otherwise → `BankingDepositProxyService`

The client never names the protocol. `BankScreen.isDepositable` (`:267`) mirrors the same order
purely to grey out rows, and its own docs say so.

**Consequence for Milestones 13 and 14.** Design §10.8's three routing outcomes are already a
server-side property of the deposit packet. Drag-to-deposit sends the same
`BankDepositRequestC2SPayload` with the same slot index and inherits all three routes for free.
Milestone 14 is therefore a *verification* milestone against existing behaviour, not new routing
work — provided the drag gesture changes nothing but which slot index is sent.

Result-channel asymmetry worth recording now, because it shapes Milestone 17: currency deposit
reports on `Operation.DEPOSIT` (`:134`, `:142`, `:146`), while cheque redemption gets its own
`Operation.CHEQUE_REDEMPTION` with four dedicated `Kind`s (`:188-200`). A screen cannot tell an
item deposit from a currency deposit by the result alone.

### 3.4 Withdrawal delivery when the player's inventory is full

Fully handled server-side, and unusually well documented in
`BankingWithdrawalProxyService`'s class docs (`:65-119`).

The sequence: `hasSufficientCapacity(inventory, reconstructed)` (`:503`) runs as **step 3**,
*before* any receipt is written and *before* `Inventory#add` is ever called (`:361`). On failure,
`abortAfterPrepare(..., BankingWithdrawalAbortReason.INSUFFICIENT_CAPACITY, ...)` (`:362`) calls
Rails' Cancel, releasing the reservation. **No item is created, none is lost, and nothing is
dropped on the ground.**

That last point is a deliberate, documented divergence from the `giveCoin` precedent elsewhere in
the mod, which drops a remainder in the world. The reasoning recorded at `:65-72`: a world drop is
as far from "confirmed, in the player's inventory" as never inserting at all, so withdrawal
refuses instead. `hasSufficientCapacity` replicates `Inventory#add`'s own slot-selection rules,
excludes the offhand as a landing target (`:490`), and takes no `Player` argument specifically so
a creative/instabuild player cannot silently destroy the item via `add()`'s discard branch
(`:100-119`).

**The gap is entirely in presentation.** `BankingWithdrawalResult.Aborted` is not matched by
`handleWithdrawal`'s switch (`:217-227`), so it falls to `default ->` and is sent as
`Kind.CLEAN_REJECTION`. The player sees:

> "The teller checks the ledger and shakes their head: 'I'm afraid I can't complete that right now.'"

— identical to a stale item, a dead teller, a fingerprint mismatch, or a transport failure. Design
§15 requires "player inventory full" as its own category, and Playbook Milestone 15 requires
"inventory-full failure is readable."

**This needs a new `BankTransferResultS2CPayload.Kind`.** Both ends of that packet are NeoForge,
so **no Rails contract is involved** and §1.1's cross-repository ordering does not apply. Adding
an enum constant is additive on the wire; an older client reading an unknown ordinal via
`buffer.readEnum` is the compatibility case to check when it is implemented. The natural owner is
Milestone 15 (stored-item withdrawal) or Milestone 17 (result framework) — Milestone 1 should
assign it rather than leave it to whichever arrives first.

### 3.5 Current reconciliation presentation

`BankScreen.acceptTransferResult` (`:327-355`) maps `Kind` → a `Component` constant. Seven `Kind`s
map to six distinct messages:

| `Kind` | Message |
| --- | --- |
| `CLEAN_REJECTION` | "The teller checks the ledger and shakes their head: …" |
| `RECONCILIATION_REQUIRED` | "Something has gone wrong with this transaction that requires staff attention. Please contact a server admin -- do not attempt this again until it is resolved." |
| `PENDING_DELIVERY` | **falls back to the clean-rejection message** (`:341`) |
| `CHEQUE_NOT_FOUND` / `_ALREADY_REDEEMED` / `_CANCELLED` / `_VOIDED` | four distinct diegetic lines |

The reconciliation message is correct in content: it is distinct, it advises staff contact, and it
tells the player not to retry — design §15.1 satisfied on wording.

**Everything about how it is *presented* fails the rebuild's requirements:**

- **No visual distinction.** `renderStatusMessage` (`:387`) draws every message, reconciliation
  included, in the same `0xFFAA4444` at the same position. Design §15.1 requires reconciliation to
  be "visually distinct from an ordinary rejection"; today only the sentence differs. Design §16's
  "color must not be the only state indicator" is moot — colour is not an indicator at all here.
- **No wrapping and no bounds.** A single `graphics.drawString` at `x = CONTENT_MARGIN`. The
  reconciliation string is ~150 characters and will run off the right edge at most widths and GUI
  scales. This is the single most visible defect found in the current screen.
- **No status lifetime.** Design §15.2 requires clearing on new operation, on authoritative
  refresh, and on navigation. Cleared on new operation only (`:297`, `:305`, `:321`). Refresh
  clears it by accident (§3.6). Navigation to `BankChequeIssuanceScreen` (`:286`) leaves it behind
  entirely.
- **`PENDING_DELIVERY` is mislabelled.** Deliberately, per the comment at `:339-341` — it is
  unreachable for `DEPOSIT`/`WITHDRAWAL`. But design §15 requires it as a real category, and once
  the Balance and Cheque screens share a status presenter, a "nothing was rejected" outcome
  rendering as a rejection becomes a live defect.
- **Not translatable.** All six are `Component.literal`. See §4.

### 3.6 Can `CurrencyItemRegistry` classify a bare coin stack for Milestone 6?

**Yes, completely, and with exactly the polarity Milestone 6 requires. This is not new work.**

`CurrencyItemRegistry.isCurrencyStack(ItemStack)` (`:70`) delegates to `currencyKeyOf` (`:57`),
which classifies **by top-level item identity only** — `stack.getItem()` against the three
`ItemRegistry` coin holders, resolved at call time to avoid racing registry init (`:62-67`).

Checked against Playbook Milestone 6's required server behaviour:

| Milestone 6 requirement | Covered? |
| --- | --- |
| identify supported bare coin stacks | **Yes** — `isCurrencyStack` |
| exclude ordinary items | **Yes** — returns empty |
| exclude cheques | **Yes** — the cheque is not one of the three coin items |
| exclude containers that merely contain coins | **Yes, explicitly.** Class docs `:27-36`: "A shulker box containing coins is NOT a coin stack." Deliberately the opposite polarity from `BankItemEligibility.isCurrency`, which *does* search nested contents, for the different purpose of blocking the generic item path. |
| total Gold, Silver, and Copper | **No** — genuinely new |

The complete coin set is confirmed twice in the class docs against the real registry: gold, silver,
copper, plain `Item`s, `stacksTo(99)`, no data components, no fourth denomination. `itemForKey`
(`:80`) already provides the reverse mapping used by currency withdrawal.

**What Milestone 6 actually costs.** The classification predicate is free and already
battle-tested (`BankingCurrencyDepositProxyServiceGameTests`, 13 tests). What is new is: the
inventory sweep, per-denomination totalling, **the Rails contract** (design §11.5 — the epic's
only one), the single atomic persistent credit, disposition of accepted stacks, and reconciliation
of a half-completed disposition. Design §11.5's warning against sizing this as "a button on the
Balance Screen" is confirmed — but the warning should be aimed at the *transaction*, since the
classification half is already done.

---

## 4. Cross-cutting findings

### 4.1 The refresh push replaces the screen

This does **not** reopen design §6.2 — the server push is the right mechanism and is adopted. It
establishes what "wire the session context to consume that push" has to mean in practice, which
Milestone 2 must decide before it writes anything.

`BankingTransferPacketService.refreshAccount` (`:333`) calls `BankingProxyService.handle(player,
teller)`, re-running `bank.open` and pushing a fresh `BankAccountOpenedS2CPayload`. The client
handler is one line:

```java
// ClientNetworkHandler.java:365-367
public static void handleBankAccountOpened(BankAccountOpenedS2CPayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(() -> Minecraft.getInstance().setScreen(new BankScreen(payload)));
}
```

**Every successful mutation destroys the screen and constructs a new one.** There is no in-place
update path. `BankTransferResultS2CPayload` deliberately has no `CONFIRMED` case (its class docs,
`:20-25`) — the replacement *is* the success signal.

Two corrections to the recorded findings, both in the direction of *more* coverage:

- Design §6.2 credits `refreshAccount` on "item deposit, currency deposit, cheque redemption, and
  withdrawal." It is **six** confirm paths, not four: `handleDeposit` (`:107`),
  `handleCurrencyDeposit` (`:140`), `handleChequeRedemption` (`:176`), `handleWithdrawal` (`:218`),
  `handleCurrencyWithdrawal` (`:257`), `handleChequeIssuance` (`:295`). Currency withdrawal and
  cheque issuance also refresh. Every mutation in the system is covered.
- Design §14.2's row "Success responses that trigger updated account state — **Exists**" is
  accurate, with the caveat that the mechanism is screen *replacement*, not state update.

**What Milestone 2 inherits.** Because the push is unconditional and screen-blind, adopting it
as-is means every success:

- discards the bank-grid selection, both scroll offsets, and the currency amount `EditBox`
  contents (they live on the destroyed instance);
- clears pending flags *by construction* — the new screen simply has `depositPending = false`.
  There is no explicit clear anywhere, so pending state is coupled to object lifetime;
- **yanks the player to whichever screen the handler constructs, from wherever they were.**
  Today this is observable: confirm a cheque on `BankChequeIssuanceScreen` and you are bounced to
  `BankScreen`. In the four-screen design the same push would eject a player from the Bank Box
  mid-session on a successful drag-deposit, and from the Balance Screen on a successful Deposit
  All Coins.

The last item is the one that matters. Adopting the push (§6.2, closed) is correct; keeping
`setScreen` as the way the client consumes it is not compatible with a four-screen flow. Milestone
2 needs the handler to route the payload into the active session and leave the current screen
mounted, which in turn means pending state and selection need an owner that outlives a refresh.
Milestone 1's "pending-state ownership" and "banking-session/view-model ownership" decisions
should be made with this in front of them.

Two related absences, both confirmed and both matching design §14.2:

- **No revision or stale-state discriminator exists.** `BankAccountOpenedS2CPayload` carries no
  revision, timestamp, or sequence number. Design §6's "monotonically increasing revision …
  **when supported**" is currently *not* supported, and Playbook Milestone 2's required tests
  "applying a newer refresh" and "rejecting or ignoring an older refresh" have nothing to order
  by. Either the tests are re-scoped to arrival order, or a discriminator is added with a stated
  reason (§14.2 requires justification). Milestone 1 must choose; it cannot be deferred, because
  Milestone 2's acceptance gate names those tests.
- **No client-initiated refresh request exists.** Confirmed — only the four C2S mutation payloads
  in §3.3. Design §6.2's guidance not to add one speculatively stands unopposed.

### 4.2 Design §12.3's three cheque denominations contradict the existing contract

> **RESOLVED 2026-08-03 — owner override. Cheques fund from all three denominations.**
>
> This section's recommendation was to drop §12.3 and keep cheques gold-only. **The owner
> overrode that and confirmed all three denominations.** Design §12.3.1 now records the decision;
> the playbook gains Milestones 8a (Rails) and 8b (NeoForge).
>
> **The recommendation below overstated the cost.** Deeper reading of the Rails side after the
> decision established three things this section did not know, all of which make the change
> materially smaller than it warned:
>
> - **ADR-012 does not need reversing.** `bank_cheques.amount` is a single copper integer with no
>   `currency_key` column. A cheque stays currency-agnostic *at rest*; only the funding
>   instruction gains a denomination.
> - **There is no schema change.** `reserved_gold_balance`, `reserved_silver_balance` and
>   `reserved_copper_balance` all already exist, with database check constraints, and are already
>   driven generically by `currency_withdrawal`. Cheque issuance merely hard-codes the gold column
>   at four call sites (`create.rb:207`, `:216`; `confirm.rb:158`, `:162`).
> - **Redemption needs no change at all.** `BankCheque#coin_mix` (`bank_cheque.rb:87-97`) already
>   converts a stored copper value into a gold/silver/copper mix. A silver-funded cheque already
>   redeems correctly today.
>
> The claim that remains true is the one that actually mattered: this is a **second new Rails
> contract**, and both documents said the epic had exactly one. `ChequePayloadValidator`'s
> allow-list is strict, so §1.1 rule 2 binds and Rails must accept before NeoForge sends.
>
> One genuine open question surfaced by the decision, recorded as Playbook §8a.1: **`MIN_AMOUNT`
> is 5 000 000 copper (500 gold)**, which would make copper and silver cheques unusable if applied
> unchanged. That is a product policy call and is not presumed here.
>
> The analysis below is left unedited as the record of what was found before the decision.

**Design §12.3 requires the Create Cheque Screen to let the player select Gold, Silver, or
Copper. Every layer beneath that screen is deliberately gold-only, by recorded architecture
decision.**

| Layer | State | Evidence |
| --- | --- | --- |
| Current screen | Gold only. One `EditBox` labelled `"Amount (gold)"`; `parseGoldAmount`; bounds `MIN_GOLD`/`MAX_GOLD`; converts to copper via `CoinConversion.COPPER_PER_GOLD` before sending | `BankChequeIssuanceScreen.java:66`, `:94`, `:105-118` |
| Packet | `BankChequeIssuanceRequestC2SPayload(entityId, amount)` — a single copper amount, **no denomination field** | §3.3 |
| Rails request shape | `ALLOWED_FIELDS = REQUIRED_FIELDS = %w[amount]`, a strict allow-list; any additional key is rejected as unknown | `cheque_payload_validator.rb:27-28`, `:61` |
| Rails amount rule | Must be an exact multiple of `GOLD_UNIT` (`COPPER_PER_GOLD`); a non-multiple is `INVALID_CHEQUE_AMOUNT` | `cheque_payload_validator.rb:70-75` |
| Rails domain | "a cheque is currency-agnostic, not gold/silver/copper-keyed" — **ADR-012** | `cheque_payload_validator.rb:7-8`; `docs/banking_bank_cheque_issuance.md:87`; `docs/banking_bank_cheque_redemption.md:33` |
| Persistence | Issuance debits `BankAccount#gold_balance`, "an integer column of whole gold coins — there is no way to reserve or debit a fractional gold coin" | `cheque_payload_validator.rb:16-25` |

The gold-multiple rule is not a rounding nicety. Its comment documents the bug it prevents:
5,000,005 copper would debit exactly 500 gold while claiming to have issued a 5,000,005-copper
cheque — silent value loss.

**Why this matters for the milestone map.** Design §11.5 and §14.2 both state that Deposit All
Coins is the *only* item in this epic requiring a new Rails contract, and Playbook §3 asserts
"every milestone except 6a is NeoForge-only." Implementing §12.3 literally would falsify that. It
would require a new field in `cheque_attributes` (which the strict allow-list rejects today, so
§1.1's Rails-accepts-before-NeoForge-sends ordering binds), a change to
`BankChequeIssuanceRequestC2SPayload`, relaxation of the gold-multiple rule, a debit path against
silver and copper balances, and an explicit reversal of ADR-012 — i.e. a second 6a-shaped
cross-repository milestone that the map does not contain.

**Recommendation: drop the three-denomination requirement and keep cheques gold-only.**
§12.3's own justification is consistency — "Three explicit denomination buttons are preferred for
consistency with currency withdrawal" — which is a UI-symmetry argument, not a product
requirement. It reads as having been written by analogy to §9.7's currency withdrawal without
checking the cheque contract underneath. That is thin grounds for reversing an ADR, changing a
schema, and adding a cross-repo milestone. Under this reading Milestones 7 and 8 stay
presentation-only over the existing service, exactly as §12.5 intends.

If the owner does want multi-denomination cheques, it should become its own numbered
cross-repository milestone with its own design, sequenced like 6a — not absorbed into Milestone 7.

**Related, smaller.** `INSUFFICIENT_BALANCE_MESSAGE` (`:158`) is shown by a **client-side
pre-check** against `account.goldBalance()` before any packet is sent (`:107-110`). No
`Kind.INSUFFICIENT_BALANCE` exists on the wire. So design §15's "insufficient balance" category is
today a client prediction against a possibly-stale snapshot, not a server outcome — a real
insufficient balance at the server arrives as `CLEAN_REJECTION`. Milestone 17 should decide
whether that stays a prediction or becomes a result kind (NeoForge-only either way, like §3.4).

---

## 5. Gaps between current code and the design document

Everything below is evidenced above. Nothing here is a proposal; ownership belongs to Milestone 1.

**Structural**

1. No client test harness exists, and no milestone owns creating one (§3.2). Blocks Milestones 2,
   3, 4, 5, 7, 9, 11, 12.
2. No drag, ghost-render, or grid-layout precedent exists anywhere (§3.1). Milestone 12's spike is
   evidence-supported and should be binding.
3. The refresh push replaces the screen; the four-screen flow cannot adopt that literally (§4.1).
4. No stale-state discriminator exists, but Milestone 2's gate names tests that require one (§4.1).

**Contract**

5. `item_key` is dropped by `BankingOpenResponseParser` — the one remaining piece of the render
   contract (design §9.5.2). Confirmed; no Rails change needed.
6. Deposit All Coins needs a new Rails contract; classification is already done, the transaction
   is not (§3.6, design §11.5).
7. Inventory-full withdrawal is indistinguishable from every other rejection and needs a new
   `Kind` — NeoForge-only, no Rails involvement (§3.4).
8. Currency deposit and item deposit share `Operation.DEPOSIT`; a screen cannot tell them apart
   from the result (§3.3).
9. ~~Design §12.3's three cheque denominations contradict the gold-only issuance contract.~~
   **Resolved by owner override 2026-08-03: all three denominations confirmed.** Now owned by
   Milestones 8a (Rails) and 8b (NeoForge); recorded in design §12.3.1. One product decision
   remains open under it — the `MIN_AMOUNT` bounds policy, Playbook §8a.1 (§4.2).
10. "Insufficient balance" is a client-side prediction, not a server result kind (§4.2).

**Presentation**

11. Status rendering does not wrap, is unbounded, and will overflow at most sizes (§3.5).
12. Reconciliation is not visually distinct from ordinary rejection (§3.5, design §15.1).
13. Status lifetime is not managed across navigation or refresh (§3.5, design §15.2).
14. `PENDING_DELIVERY` renders as a rejection (§3.5).
15. **Localization is effectively absent from banking, and the dialogue framework is the
    obstacle.** `en_us.json` holds 609 keys, of which one matches bank/teller/cheque (the cheque
    *item* name). `Component.translatable` appears in exactly two client files — `SignOptionsScreen`
    and `SignIconButton` — neither of them banking. Every string in `BankScreen`, and every string
    the rebuilt screens will need, is `Component.literal`. The blocker is structural, not
    clerical: `DialoguePresentation.text(String)` (`:37`) takes a `String` and wraps it in a
    literal carrying the UO font `Style`. Making the dialogue family translatable means changing
    or overloading that method, which is **Milestone 3's** work and a prerequisite for Playbook
    §3.1's per-milestone translatability gates on Milestones 4, 5, 7, 9 and 11. Note also design
    §17.1's exception: `BankItemSummary.FALLBACK_NAME` ("Stored item", `BankItemSummary.java:30`)
    is a data-layer fallback belonging to the §9.5 render contract, not to any screen.

**Retirement (Milestone 19 targets, all confirmed present)**

16. `DOUBLE_CLICK_MS = 400` (`:73`) with `lastClickedDepositSlot` / `lastDepositClickTimeMs` /
    `lastClickedWithdrawalItem` / `lastWithdrawalClickTimeMs` (`:116-120`).
17. Two scrolling row lists, `renderDepositPanel` (`:395`) and `renderWithdrawPanel` (`:449`).
18. The Deposit / Withdraw / Checks bottom row (`:170-184`).
19. Hard-coded English: `"The vault is empty."` (`:460`), `"Withdraw from vault:"` (`:450`),
    `"Deposit from inventory:"` (`:396`), `"Bank Account"` (`:149`), `"(ineligible)"` (`:418`),
    the six status constants (`:128-146`).

One pre-existing minor defect, noted and **not** fixed (no production changes in this milestone):
`mouseScrolled` (`:609`) tests the withdraw side with `withinPanel` (the full panel rect) while
`mouseClicked` (`:527`) correctly uses `withinWithdrawList` (below the currency controls). Scrolling
over the currency amount box therefore scrolls the vault list. Harmless today; it disappears with
the screen at Milestone 19.

---

## 6. Milestone report

**Files inspected (NeoForge).** `client/gui/BankScreen.java`; `client/screen/BankChequeIssuanceScreen.java`;
`client/gui/DialoguePresentation.java`; `client/gui/NpcCatalogScreen.java`; `client/gui/ArchitectScreen.java`;
`client/screen/ServiceNpcSpawnScreen.java`; `menu/ServiceNpcSpawnMenu.java`; `registry/MenuRegistry.java`;
`block/entity/{BritanniaChest,Armoire,TrashBarrel}BlockEntity.java`; `dialogue/DialogueLayout.java`;
`dialogue/DialogueViewModel.java`; `network/ClientNetworkHandler.java`; `network/NetworkHandler.java`;
all six `network/payload/Bank*Payload.java`; `service/banking/BankingTransferPacketService.java`;
`service/banking/BankingWithdrawalProxyService.java`; `service/banking/BankingWithdrawalAbortReason.java`;
`service/banking/BankingOpenResponseParser.java`; `service/banking/BankItemSummary.java`;
`bank/currency/CurrencyItemRegistry.java`; `bank/item/BankItemEligibility.java`;
all 15 banking GameTest files and all 11 banking JUnit files (enumerated, sized, counted);
`assets/britannia_mod/lang/en_us.json`. Repository-wide searches as listed in §3.1.

**Files inspected (Rails).** `config/routes.rb`; `app/controllers/api/banking_controller.rb`;
the `app/services/bank_*` and `app/services/banking/` trees; the 47-file banking test inventory.

**Files changed.** One created: this report.

Then, **on explicit owner authorization dated 2026-08-03** (the cheque-denomination override),
two existing documents were edited — documentation only, no production code, no packet, no UI, no
test:

- `UltimaCraft_Bank_Interface_Rebuild_Design.md` — new §12.3.1 recording the decision and its
  verified cost; §11.5, §12.5 and §14.2 corrected from "one new Rails contract" to two; revision
  note added at the head. §14.2 also gained the inventory-full row from §3.4.
- `UltimaCraft_Bank_Interface_Rebuild_Codex_Playbook.md` — §1.1 and §3 corrected to name two Rails
  contracts; milestone map updated; Milestone 8 split into 8a (Rails) / 8b (NeoForge) with the
  bounds decision recorded as §8a.1; Milestone 7 noted as unblocked by 8a; Milestone 19's final
  validation and §4's gate count updated.

The §2 package correction was **not** applied — it is a separate proposal still awaiting approval.
No Rails-repository file was touched: `docs/banking_bank_cheque_issuance.md` and the ADR record
describe behaviour that does not exist yet, and belong to Milestone 8a's own acceptance gate.

**Decisions made.** None by me. One was made *by the owner* during the milestone and applied to
the documents: cheques fund from all three denominations (§4.2, design §12.3.1). The two
pre-closed decisions
(§9.5.3 icons from `item_key`; §6.2 adopt the server push) were treated as settled and are not
reopened; §4.1 describes how to *consume* the adopted push, not whether to adopt it.

**Tests run.** None. No behaviour changed, and the milestone forbids production changes. Existing
suites were inventoried, not executed.

**Unresolved blockers.** None. §4.2 was the one blocker and the owner resolved it on 2026-08-03 —
all three cheque denominations, making this a two-Rails-contract epic. §5 items 1, 3, 4 and 15
remain for Milestone 1 to assign, one product decision remains open under Playbook §8a.1 (cheque
amount bounds), and §2 is a documentation correction still awaiting owner approval.

**Scope explicitly not performed.**

- The §2 package correction was not applied. It is proposed only — Playbook Milestone 0 restricts
  documentation edits to owner approval, and that approval covered the cheque decision alone.
- Milestone 8a's own work was not started. The design and playbook now specify it; no Rails code,
  migration, ADR or doc was written.
- The `mouseScrolled` defect in §5 was left in place.
- Client/dedicated-server smoke tests were not run; nothing was built or launched.
- The wider directory/package divergence across `client/gui/` was surveyed but is not this epic's
  to normalise.
- No commit was made. Playbook §1 rule 9.

---

## 7. Acceptance gate

> *Milestone 0 passes when the report is evidence-based and identifies every dependency needed for
> the four-screen rebuild.*

Every claim above cites a file and line at `f94b42e` / `1267195`, or a search whose command and
result are stated. The six items the 2026-08-03 pass left open are closed in §3. Dependencies for
the rebuild are enumerated in §5 and, where they are unowned, named for Milestone 1.

**Stopping here for owner review.** Of the three decisions originally requested, one is closed:

1. ~~**§4.2 — cheque denominations.**~~ **Closed 2026-08-03: owner confirmed all three
   denominations.** Design §12.3.1 and Playbook Milestones 8a/8b were written to match. A
   follow-on product decision is now open under Playbook §8a.1 — the cheque amount bounds policy
   — and must be answered before 8a is built.
2. **§2 — the baseline package correction** to the design document's note on `BankScreen`'s
   location. Still open; deliberately not applied.
3. **§5 items 1, 3, 4 and 15** — confirmation that the client test harness, the
   refresh-consumption strategy, the stale-state discriminator, and the localization prerequisite
   in the dialogue framework are Milestone 1's to assign rather than any later milestone's.
   Still open.
