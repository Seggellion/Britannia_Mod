# UltimaCraft Bank Interface Rebuild Design

**Document status:** Proposed authoritative design  
**Feature:** Four-screen bank interaction rebuild  
**Target:** UltimaCraft / Britannia Mod  
**Runtime context:** Minecraft 1.21.1, NeoForge 21.1.72, Java 21  
**Primary baseline:** `com.seggellion.britannia_mod.client.gui.BankScreen`  
**Revised:** 2026-08-03, against NeoForge `banking` @ `f94b42e` and Rails `banking` @ `1267195`

> **Note on the baseline path (corrected 2026-08-03).** The screen being replaced is the one whose
> **file** is `src/main/java/com/seggellion/britannia_mod/client/gui/BankScreen.java`. Its
> **package** is `com.seggellion.britannia_mod.client.screen` — declared on line 1 of that file,
> and imported that way by `ClientNetworkHandler`.
>
> **Path and package diverge here, and the package is what matters.** An earlier draft of this
> document named `client.screen.BankScreen` and a later one "corrected" it to `client.gui`; the
> first was right about the package and wrong about where to find the file. Both `BankScreen` and
> `BankChequeIssuanceScreen` are in package `client.screen`.
>
> This is not isolated. Of the 21 files under `client/gui/`, 13 declare `client.screen`, 6 declare
> `client.gui`, one declares `client.gui.screen`, and one declares no package on its first line.
> `DialoguePresentation` — which every dialogue screen depends on — is another `client/gui/` file
> in package `client.screen`, which is why `BankScreen` uses it with no import.
>
> **The rule: read the `package` line, never the directory.** New screens in this epic are placed
> so the two agree (`client/screen/`, package `client.screen`). Normalizing the pre-existing
> divergence across the rest of the client tree is explicitly out of scope.

> **Note on this revision.** Sections 4.7, 6.2, 9.5, 11.5, 14.2 and 17.1 were corrected after the
> item-identity program (Milestones 15–19) shipped. That program changed facts this document was
> originally written against. Where a requirement here is already satisfied, it now says so
> explicitly rather than inviting a reimplementation.

> **Note on the Milestone 0 revision (2026-08-03, later same day).** Milestone 0's reconnaissance
> is recorded in `UltimaCraft_Bank_Interface_Rebuild_Milestone_0_Report.md`. One change to this
> document follows from it:
>
> **§12.3.1 is new.** Milestone 0 found that cheque issuance is gold-only end to end and asked
> whether §12.3's three denominations should be dropped. **The owner overrode that and confirmed
> all three.** This adds the epic's second new Rails contract; §11.5 and §14.2 are corrected
> accordingly, and the playbook gains Milestones 8a/8b.
>
> The report's other proposed correction — the baseline **package** named in the note below — was
> approved by the owner on 2026-08-03 and **has been applied**.
>
> The cheque amount bounds question raised by §12.3.1 was also settled that day: **absolute bounds
> retained, unchanged.** See §12.3.1 and Playbook §8a.1.

---

## 1. Purpose

The existing bank interface combines dialogue, account balances, item deposits, item withdrawals, currency withdrawals, and cheque access into one screen. The result is visually crowded, difficult to understand, and dependent on a double-click row-selection interaction that does not resemble normal Minecraft inventory handling.

This rebuild separates banking into four focused screens:

1. **Bank Main Screen**
2. **Bank Box Screen**
3. **Bank Balance Screen**
4. **Create Cheque Screen**

The new design must preserve the existing server-authoritative banking rules while replacing the legacy combined screen and its double-click interaction.

---

## 2. Design goals

The rebuild must:

- present one clear banking task per screen;
- preserve the Ultima-style parchment and banker portrait presentation;
- make the Main, Balance, and Create Cheque screens feel like one dialogue family;
- give the Bank Box a dedicated inventory-oriented presentation;
- show stored bank items in a multi-row, multi-column grid;
- show the player's main inventory and hotbar on the Bank Box screen;
- allow a player to click and drag an eligible item from their inventory into the Bank Box;
- remove the requirement to double-click an item before depositing it;
- keep all deposits, withdrawals, currency transfers, and cheque operations server-authoritative;
- prevent optimistic client-side inventory mutation;
- preserve existing item eligibility, weight, ownership, reconciliation, and cheque-validation rules;
- refresh visible account state after successful mutations;
- work at supported GUI scales and common screen sizes;
- remain safe under latency, duplicate input, stale state, and multiplayer concurrency.

---

## 3. Non-goals

The initial epic does not require:

- dragging stored bank items back into the player's inventory;
- partial-stack deposits;
- right-click stack splitting;
- shift-click transfers;
- armor-slot or offhand deposits;
- rearranging the order of items inside the persistent bank box;
- optimistic local mutation before server confirmation;
- redesigning the Rails banking domain unless a required client contract is unavailable;
- replacing the underlying cheque, currency, weight, or item-eligibility rules;
- keeping the old combined screen as an alternate interface.

A later epic may add partial-stack transfers or drag-to-withdraw, but those behaviors must not be improvised during this rebuild.

---

## 4. Existing implementation baseline

The current `BankScreen` establishes several important facts that the rebuild must preserve.

### 4.1 Current screen model

The existing screen is a plain Minecraft `Screen`. It is not backed by `AbstractContainerMenu` or vanilla `Slot` objects.

It manually renders:

- a dialogue header;
- account weight and currency balances;
- a scrolling player-inventory list;
- a scrolling bank-item list;
- an amount input for currency withdrawal;
- Gold, Silver, and Copper withdrawal buttons;
- Deposit, Withdraw, and Checks buttons;
- transaction status messages.

### 4.2 Current item selection

The legacy screen uses a two-click gesture:

- the same inventory row must be clicked twice within 400 milliseconds to select it for deposit;
- the same stored bank row must be clicked twice within 400 milliseconds to select it for withdrawal;
- a separate Deposit or Withdraw button completes the operation.

This interaction is retired by this design.

### 4.3 Current transaction references

The existing client sends identifiers rather than trusting locally reconstructed state:

- deposits send the live player inventory slot index;
- stored-item withdrawals send the bank item's public UUID;
- currency withdrawals send currency key plus positive amount.

The server independently re-derives and validates the authoritative facts.

### 4.4 Current eligibility routing

The current deposit picker accepts:

- ordinary bank-eligible items;
- bare supported currency stacks;
- bank cheques.

Those categories do not all produce the same server operation:

- ordinary items enter bank storage;
- loose coin stacks enter the currency-deposit path;
- cheques enter cheque redemption.

Containers that merely contain coins are not treated as loose currency stacks.

### 4.5 Current pending and error behavior

The current screen prevents overlapping deposit and withdrawal requests. It also distinguishes:

- clean rejection;
- reconciliation required;
- cheque not found;
- cheque already redeemed;
- cheque cancelled;
- cheque voided.

These distinctions must remain available in the rebuilt screens.

### 4.6 Existing cheque and currency features (continued below)

The current implementation already has:

- a separate `BankChequeIssuanceScreen`;
- currency-withdrawal packets and controls;
- cheque-specific presentation messages;
- an account-open payload containing teller identity, balances, weight, limit, and bank-item summaries.

The rebuild should adapt these proven services instead of duplicating them.

### 4.7 State delivered by Milestones 15–19 (verified 2026-08-03)

The item-identity program landed after this document was first drafted. These are verified facts,
read from the code, not assumptions. They materially reduce the scope of §9.5 and Playbook
Milestone 10.

**Item identity now crosses the Rails boundary.** `bank_items` carries nullable `display_name`,
`item_key`, and `count`. `Api::BankingController#bank_item_json` returns each one, **omitting the
key entirely** when the item has no such value, so a v1 item's JSON is byte-identical to what it
was before the program existed.

**The client carries name and count, but drops `item_key`.**

```java
public record BankItemSummary(UUID publicId, double weight,
                              @Nullable String displayName, @Nullable Integer count)
```

`BankingOpenResponseParser.parseBankItems` reads `display_name` and `count` leniently — a
malformed value degrades to absent rather than failing the account view. It does **not** read
`item_key`. The value is sent by Rails and discarded client-side. Carrying it through is the
principal remaining work for an icon grid.

**The vault list already shows real names.** `BankItemSummary.describe()` renders
`displayName` with an `x<count>` suffix when count exceeds one, falling back to the literal
`"Stored item"`. The row label defect this design cites in §4.2 as motivation is already fixed;
what remains is that the presentation is a text row rather than an icon cell.

**Post-transaction refresh already exists.** See §6.2.

**The teller portrait is already correct.** `BankAccountOpenedS2CPayload` carries `tellerGender`
alongside `tellerName`, resolved server-side and allow-listed to `male`/`female`. The rebuilt
dialogue family should consume this, not reintroduce a hardcoded gender.

---

## 5. Authoritative screen model

The bank interaction is a four-screen navigation flow.

```text
Interact with banker
        |
        v
Bank Main Screen
   |          |             |
   v          v             v
Bank Box   Balance     Create Cheque
   |          |             |
   +----------+-------------+
              |
              v
        Bank Main Screen
```

### 5.1 Back behavior

The Back button on every sub-screen returns to the Bank Main Screen for the same active banker and account context.

### 5.2 Escape behavior

Escape closes the banking interaction entirely and returns to gameplay.

Escape does not act as Back.

### 5.3 Closing during a transaction

Closing a screen does not cancel a request that has already reached the server.

The client must not invent a result after closing. A later account refresh must reconcile the true state.

---

## 6. Shared banking session context

The four screens should not independently invent or retain unrelated account snapshots.

Introduce a reusable banking-session context or view model containing the display state needed by the client:

- banker entity ID;
- teller name;
- teller gender or portrait key;
- city display name, when present;
- gold balance;
- silver balance;
- copper balance;
- current stored weight;
- weight limit;
- ordered bank-item summaries;
- account/session identity required by existing routing;
- last status message, where appropriate;
- current request or pending state;
- monotonically increasing revision, timestamp, or equivalent stale-state discriminator when supported.

The exact class name is implementation-defined. Suitable examples include:

- `BankingSessionViewModel`
- `BankScreenContext`
- `ClientBankingSession`

### 6.1 State ownership

The context is client presentation state, not authority.

The server and persistent banking service remain authoritative for:

- player identity;
- account ownership;
- live player inventory;
- item fingerprint;
- item eligibility;
- item weight;
- available capacity;
- stored item availability;
- currency balances;
- cheque state;
- transaction outcome.

### 6.2 Refresh requirement

After any successful mutation, the client must receive or request refreshed authoritative state for every affected value.

At minimum:

- item deposit refreshes inventory, bank items, and weight;
- item withdrawal refreshes inventory, bank items, and weight;
- currency deposit refreshes inventory and currency balances;
- currency withdrawal refreshes inventory and currency balances;
- cheque creation refreshes currency balances and player inventory;
- cheque redemption refreshes currency balances and player inventory.

A screen must not continue displaying an old snapshot after a successful transaction.

**This is already implemented as server push, and must be adopted rather than replaced.**
`BankingTransferPacketService` calls `refreshAccount(player, teller)` on every `Confirmed`
result — item deposit, currency deposit, cheque redemption, and withdrawal — which re-runs
`bank.open`'s real fetch and pushes a fresh `BankAccountOpenedS2CPayload`. The authoritative
refresh requirement above is therefore satisfied today for all four mutations.

The distinction that matters for the rebuild:

| Mechanism | Status |
| --- | --- |
| Server push on `Confirmed` | **Exists.** Adopt it. |
| Client-initiated "refresh my account now" request | **Does not exist.** |

A client-initiated refresh is only needed if a screen must resynchronize without having caused a
mutation — for example after a reconnect, or when a second client changed the account while this
screen sat open. Do not add one speculatively. If §19.3's multiplayer cases prove it necessary,
add it as its own contract change with a stated reason, not as a side effect of building the
session context.

---

## 7. Shared dialogue-screen presentation

The following screens belong to the dialogue family:

- Bank Main Screen;
- Bank Balance Screen;
- Create Cheque Screen.

They should share reusable composition rather than copy-pasted layout calculations.

A reusable dialogue frame should support:

- parchment background;
- banker portrait;
- banker name;
- body dialogue area;
- right-side vertical action buttons;
- optional form controls;
- status and validation text;
- responsive dimensions;
- consistent type scale and text wrapping;
- consistent button spacing;
- consistent hover, focus, pressed, inactive, and pending states.

Composition is preferred over a deep screen inheritance hierarchy. A reusable renderer/layout object may be safer than requiring all screens to inherit from one complex base screen.

---

## 8. Screen 1: Bank Main Screen

### 8.1 Purpose

The Bank Main Screen is the entry hub for all banking functions.

It replaces the current combined account screen.

### 8.2 Required layout

Left:

- banker portrait;
- banker name.

Center:

- greeting or introductory dialogue.

Right:

1. Open Bank Box
2. Balance
3. Create Cheque

### 8.3 Required behavior

- Open Bank Box opens the dedicated Bank Box Screen.
- Balance opens the Bank Balance Screen.
- Create Cheque opens the Create Cheque Screen.
- Escape closes banking.
- No item list, bank grid, currency input, weight ledger, or transaction controls appear here.
- Buttons are disabled only when the destination cannot safely open.
- Opening a destination must preserve banker/account context.

### 8.4 Suggested dialogue

The precise localized copy is owner-controlled. The design requires only that it be a short banker greeting and not an account-data dump.

---

## 9. Screen 2: Bank Box Screen

### 9.1 Purpose

The Bank Box Screen is the player's storage-management interface.

It is the only screen in this epic that presents inventory grids.

### 9.2 Required regions

The screen must contain:

- bank-box title and banker/account context;
- current bank weight and weight limit;
- multi-row, multi-column bank-item grid;
- player main inventory grid;
- player hotbar;
- Back button;
- stored-item Withdraw button;
- currency withdrawal amount input;
- Gold, Silver, and Copper currency-withdrawal actions;
- status or error area.

### 9.3 Bank grid

The bank box must be presented as a real grid rather than a textual row list.

Recommended default:

- nine columns, matching Minecraft inventory conventions;
- four or more visible rows when space permits;
- scrolling or paging when the stored item count exceeds visible capacity;
- empty cells remain visible to communicate available grid structure;
- each occupied cell displays an item icon and stack count when count is meaningful;
- hover shows a tooltip;
- selected bank item receives a clear selection border or background;
- pending items cannot be selected again.

The visible row count may adapt to GUI scale, but the grid must remain multi-row and multi-column.

### 9.4 Player inventory

The player section must show:

- three rows of nine main-inventory slots;
- one row of nine hotbar slots;
- empty slots;
- stack counts;
- normal hover tooltips;
- eligible, ineligible, pressed, dragged, and pending visual states.

Armor and offhand slots are excluded from the initial epic.

### 9.5 Stored-item rendering contract

**Revised 2026-08-03.** Milestones 15–19 delivered most of this. The remaining work is narrower
than the original draft assumed, and one decision that draft left implicit is now settled.

### 9.5.1 Already delivered

| Field | Rails | Client |
| --- | --- | --- |
| public bank-item UUID | yes | yes — `BankItemSummary.publicId` |
| stored weight | yes | yes — `BankItemSummary.weight` |
| display name | yes — `display_name` | yes — `BankItemSummary.displayName` |
| stack count | yes — `count` | yes — `BankItemSummary.count` |
| item registry identifier | yes — `item_key` | **no — parsed nowhere** |
| visual variant / components | **no** | **no** |

### 9.5.2 Remaining work

Carry `item_key` from `BankingOpenResponseParser` through `BankItemSummary` and
`BankAccountOpenedS2CPayload` to the grid. Rails already sends it; nothing on the server side
needs to change. Apply the same lenient treatment the existing identity fields use — a malformed
or absent `item_key` degrades that cell to the unknown-icon fallback and must never fail the whole
account view.

### 9.5.3 Decision: icons render from `item_key` alone

**Decided. Do not reopen during implementation.**

The original draft asked for "visual variant/components required for the correct icon" as though
it cost the same as a registry id. It does not. Component data (enchantment glint, damage bar,
dyed colour, custom name styling) exists only inside `payload` — the gzipped NBT blob Rails
deliberately keeps opaque and omits from `bank_item_json` unless `include_payload: true`.

Three options were considered:

1. **Icon from `item_key` alone.** No contract change. A damaged, enchanted, renamed sword renders
   as a plain sword of the right type, with the correct name and count beside it.
2. **Ship `payload` on open.** True icons, but reverses the opacity decision that keeps Rails
   independent of Minecraft's serialization format, and ships up to 256 KB per item for every item
   in the account on every open.
3. **A bounded render-snapshot field.** A third contract, a third hostile-input surface, and a new
   round of cross-repo sequencing.

**Option 1 is chosen for this epic.** It is the only one that adds no contract, no payload
inflation, and no new untrusted field. The limitation is real and accepted: visual variants will
not render. `display_name` already communicates a renamed or notable item in text, which is the
information a player actually needs to find something in their vault.

Option 2 or 3 may be revisited in a later epic with its own design. Neither is to be improvised
inside this one.

### 9.5.4 Trust boundary, unchanged

The withdrawal request must continue to reference the public stored-item ID. The server must
ignore any client-supplied registry id, component payload, count, or weight. `item_key` is render
data travelling client-ward only; it never returns as an authority claim.

### 9.6 Stored-item selection and withdrawal

Initial withdrawal behavior:

1. Single-click a stored bank cell.
2. The cell becomes selected.
3. Press Withdraw.
4. The client sends the stored item's public ID.
5. The server validates and performs the withdrawal.
6. The client refreshes authoritative state.

Double-click selection is forbidden.

Dragging stored items into the player inventory is not part of the initial epic.

### 9.7 Currency withdrawal

The Bank Box Screen retains currency withdrawal.

It must provide:

- positive whole-number amount input;
- Gold button;
- Silver button;
- Copper button;
- disabled states for malformed or non-positive input;
- pending lock while a withdrawal is in flight;
- insufficient-funds feedback;
- inventory-capacity feedback;
- refreshed balances and inventory after success.

Currency withdrawal is separate from bank-grid selection.

---

## 10. Dragging items into the Bank Box

### 10.1 Required user interaction

The player must be able to deposit an item by dragging it from their inventory into the bank-box drop region.

Expected sequence:

```text
Idle
  -> left mouse press on an eligible player inventory slot
  -> pointer moves beyond drag threshold
  -> carried/ghost stack appears under cursor
  -> pointer enters valid or invalid bank drop region
  -> left mouse release
  -> cancelled or deposit request pending
  -> authoritative result
  -> refreshed state
```

### 10.2 Whole-stack scope

The initial drag deposits the entire live stack in the source slot.

No quantity picker is used for item deposits.

No partial stack is removed locally.

Partial-stack support requires a separate approved design because it changes:

- packet contract;
- server validation;
- persistent transaction;
- rollback;
- inventory reconstruction;
- concurrency tests.

### 10.3 Drag start

A drag may begin only when:

- the left mouse button is pressed;
- the source is a player main-inventory or hotbar slot;
- the slot contains a non-empty stack;
- no banking mutation is pending;
- the stack is client-display eligible.

A small movement threshold must distinguish an intended drag from a click. The threshold should be based on GUI pixels after scale handling.

### 10.4 Drag visuals

While dragging:

- the source slot remains visible but dimmed or marked;
- a ghost or carried copy of the stack follows the cursor;
- the stack count remains visible;
- the bank drop region highlights as valid or invalid;
- a brief reason may appear for ineligible items;
- the real player inventory is unchanged.

### 10.5 Valid drop target

The initial valid target is the Bank Box storage region, not a particular empty persistent slot.

The persistent service owns storage ordering. The player is requesting "deposit this source stack into my bank box," not placing it into a trusted client-selected database position.

This prevents the client from asserting a persistent bank slot that may no longer be free.

### 10.6 Release handling

On release over a valid target:

- re-check that the source slot still contains a compatible live stack;
- enter pending state;
- send the existing slot-index deposit request or an approved compatible replacement;
- do not remove or reduce the client stack optimistically;
- block another deposit/withdrawal until a result or timeout policy resolves;
- clear the local drag visual.

On release outside the target:

- cancel the drag;
- send no packet;
- leave all state unchanged.

### 10.7 Ineligible item handling

Client eligibility is only guidance.

The client may show an ineligible state using the existing item-eligibility rules, but the server must still independently reject:

- empty or changed source slots;
- ineligible items;
- overweight deposits;
- ownership violations;
- stale inventory references;
- unsupported item data;
- altered-client requests.

### 10.8 Special deposit routing

Dropping different item categories must preserve existing routing:

- ordinary eligible item -> item storage deposit;
- loose currency stack -> currency deposit;
- bank cheque -> cheque redemption.

A cheque must not be stored as an ordinary bank item.

A loose coin stack may still be dragged individually even though the Balance Screen also offers Deposit All Coins.

### 10.9 Pending and concurrency behavior

While a drag deposit is pending:

- the source slot is locked in the bank UI;
- no second banking mutation may begin;
- closing the screen is allowed but does not fabricate cancellation;
- a duplicate mouse release must not send a duplicate request;
- a changed live slot must cause server rejection or reconciliation;
- success requires authoritative refresh;
- failure clears pending state and shows the correct message.

---

## 11. Screen 3: Bank Balance Screen

### 11.1 Purpose

The Bank Balance Screen presents currency balances and offers one bulk coin-deposit action.

### 11.2 Required layout

Left:

- banker portrait;
- banker name.

Center:

- dialogue stating the account's Gold, Silver, and Copper balances.

Right:

1. Back
2. Deposit All Coins

### 11.3 Required behavior

- Back returns to the Bank Main Screen.
- Deposit All Coins deposits all supported loose coin stacks from the player's eligible inventory scope.
- The action does not deposit ordinary items.
- The action does not redeem cheques.
- The action does not inspect or empty containers holding coins unless the existing banking domain explicitly defines that behavior.
- The button enters a pending state and prevents duplicate requests.
- A successful result refreshes balances and player inventory.
- A no-coins result is clean, understandable, and non-destructive.

### 11.4 Atomicity

Deposit All Coins must be one server-authoritative banking operation from the player's perspective.

It must not be implemented as a client loop sending one request per detected stack.

The server must:

- inspect the live inventory;
- identify supported loose coin stacks;
- compute denomination totals;
- apply the persistent credit;
- remove accepted coin stacks;
- return success only when the transaction reaches its defined completion point;
- provide reconciliation handling when persistence and inventory disposition cannot be completed cleanly.

### 11.5 Deposit All Coins is new server work, not a screen (added 2026-08-03)

Verified: **nothing of this exists yet, on either side.** Rails' `banking/*` routes are
`open`, `deposit/prepare`, `withdrawal/prepare`, `currency/deposit/prepare`,
`currency/withdrawal/prepare`, `cheque/issue/prepare`, `cheque/redeem`, `confirm`, `cancel` —
there is no bulk path. A repository-wide search for `deposit_all` / `all_coins` returns nothing in
either repository.

This makes Deposit All Coins **one of exactly two items in this epic requiring a new Rails
contract** — the other is multi-denomination cheque issuance, added by owner decision on
2026-08-03; see §12.3.1. (This section originally said "the only item," which was true when it was
written and is no longer.) Everything else is client presentation over contracts that already
exist. That has two consequences the milestone sequence must respect:

1. **It inherits the cross-repo ordering rule.** Rails validates a strict envelope and rejects
   unknown fields, so Rails must accept before NeoForge sends — exactly as Milestone 16 preceded
   Milestone 17 in the item-identity program. See
   `UltimaCraft_Banking_Item_Identity_Codex_Milestones_15_19.md` §3.4 and §4.
2. **Its Rails half has no dependency on any screen** and should start early, in parallel with the
   dialogue-family work, rather than waiting for its position in the client sequence.

The §11.4 atomicity requirement is the substance here, not the button. Inspecting a live
Minecraft inventory, computing denomination totals, applying one persistent credit, disposing of
accepted stacks, and reconciling a half-completed disposition is a full transaction design. Sizing
it as "a button on the Balance Screen" will underestimate it.

---

## 12. Screen 4: Create Cheque Screen

### 12.1 Purpose

The Create Cheque Screen issues a bank cheque against the player's deposited currency.

### 12.2 Required layout

Left:

- banker portrait;
- banker name.

Center:

- cheque-creation prompt;
- currency selection;
- positive amount input;
- contextual balance or selected-denomination balance;
- validation/status text.

Right or lower action area:

- Back;
- Confirm/Create Cheque;
- Cancel when needed by the chosen form arrangement.

### 12.3 Currency selection

The screen must allow the player to select:

- Gold;
- Silver;
- Copper.

The control style should match the project's established UI language. Three explicit denomination buttons are preferred for consistency with currency withdrawal.

### 12.3.1 Multi-denomination cheques require a Rails contract change (owner-decided 2026-08-03)

**Decided by the owner. Do not reopen during implementation.**

Milestone 0 found that every layer beneath this screen is gold-only, and asked whether §12.3
should be relaxed to match. **The owner overrode that and confirmed all three denominations.**
This section records what that costs, because it is not free and the milestone map now has to
carry it.

What is gold-only today, verified against Rails `1267195`:

| Layer | State |
| --- | --- |
| `ChequePayloadValidator` | `REQUIRED_FIELDS = ALLOWED_FIELDS = %w[amount]`, a strict allow-list — an extra key is rejected as unknown |
| Amount rule | Must be an exact multiple of `GOLD_UNIT` (`COPPER_PER_GOLD`, 10 000); a non-multiple is `INVALID_CHEQUE_AMOUNT` |
| Reservation | `BankTransferOperations::Create` reserves against `reserved_gold_balance` specifically (`create.rb:207`, `:216`) |
| Debit | `BankTransferOperations::Confirm` debits `gold_balance` / releases `reserved_gold_balance` specifically (`confirm.rb:158`, `:162`) |
| Packet | `BankChequeIssuanceRequestC2SPayload(entityId, amount)` — one copper amount, no denomination |

**The change is smaller than the gold-only surface suggests, for three reasons.**

1. **ADR-012 stays intact and is not reversed.** `bank_cheques.amount` is a single copper integer
   with no `currency_key` column. A cheque remains currency-agnostic *at rest*. What gains a
   denomination is only the **funding instruction** — which balance the issuance debits — not the
   cheque itself.
2. **Redemption needs no change at all.** `BankCheque#coin_mix` already converts the stored copper
   value into a gold/silver/copper mix by `divmod` against the same canonical ratios NeoForge uses
   (`COPPER_PER_GOLD` 10 000, `COPPER_PER_SILVER` 100). A cheque funded from silver already
   redeems correctly today.
3. **The reservation machinery for all three denominations already exists.**
   `reserved_gold_balance`, `reserved_silver_balance` and `reserved_copper_balance` are all real
   columns, carry database check constraints against overdraft and negativity, and are already
   driven generically by `currency_withdrawal`. Cheque issuance simply hard-codes the gold column
   at four call sites.

**Therefore the required work is:** add `currency_key` to the cheque request contract; generalize
those four call sites from the gold column to the keyed column, reusing the mechanism
`currency_withdrawal` already proves; replace the `% GOLD_UNIT` rule with "a whole multiple of the
selected denomination's copper unit"; and carry a denomination on the issuance packet. No
`bank_cheques` schema change. No change to redemption.

**Amount bounds: the floor is a coin count, the ceiling is a value (owner-decided, revised).**

> **Revision history, because this changed once.** An earlier decision on 2026-08-03 kept the
> pre-existing absolute floor, making the minimum cost 500 gold / 50 000 silver / 5 000 000
> copper. The owner revised that after seeing it in the built interface: *"it should be 500 coins,
> and not a value of 500 gold."* This section records the revised rule. The original absolute
> floor was never a decision of this epic — it is `BankCheque::MIN_AMOUNT` from ADR-018/ADR-019,
> written when cheques were gold-only and it simply meant "500 gold".

**The smallest cheque is 500 coins of whichever denomination funds it.** 500 gold, 500 silver, or
500 copper. One sentence a player can hold in their head.

**Gold is unaffected.** 500 gold has always been the floor, because 500 gold *is* 5 000 000
copper. Only silver and copper gain reachable floors, and neither denomination existed before this
epic — so nothing that has ever shipped changes behaviour.

**The ceiling stays value-denominated, and that is structural rather than policy.** A cheque's
amount is stored and debited as an int32 copper column, so `MAX_AMOUNT` (1 000 000 000 copper)
is a capacity limit. The maximum therefore differs per denomination — 100 000 gold, 10 000 000
silver, 1 000 000 000 copper — because that is what fits.

Consequences for Milestone 8a:

- **`ChequePayloadValidator` must enforce the floor per denomination**, against the coin count
  rather than the copper value: `amount / unit(currency_key) >= 500`.
- **`BankCheque::MIN_AMOUNT` must drop** from 5 000 000 to 500 — the smallest legal cheque, which
  is now a 500-copper one. It remains a model-level sanity floor; the denomination-aware rule
  lives in the validator.
- `MAX_AMOUNT` is unchanged.
- The unit-multiple rule is still enforced per denomination, because it guards the debit
  arithmetic rather than the floor.
- Record the revised rule as an ADR superseding the bounds half of ADR-018/ADR-019.

**Consequence for the epic:** this is the second new Rails contract, not the first. See §11.5 and
§14.2, both corrected, and the new Milestones 8a/8b in the playbook.

### 12.4 Input validation

Client validation must reject or disable confirmation for:

- empty input;
- zero;
- negative value;
- malformed value;
- overflow;
- unsupported denomination.

Server validation remains authoritative for:

- account ownership;
- sufficient funds;
- transaction state;
- cheque issuance;
- inventory delivery;
- duplicate request;
- reconciliation.

### 12.5 Existing service reuse

The current cheque issuance flow should be adapted rather than reimplemented.

**Revised 2026-08-03.** With §12.3.1 decided, "adapted" now includes one genuine contract
extension on both sides — the denomination. Everything listed below is still reuse, not rewrite:
the issuance protocol, the reservation/confirm shape, the pending-delivery path, and redemption
are all unchanged. Only the funding denomination is new.

The rebuilt screen must preserve:

- issuance result handling;
- pending delivery behavior;
- inventory-full handling;
- balance debit rules;
- cheque data components;
- duplicate submission prevention.

---

## 13. Recommended client architecture

### 13.1 Screen classes

Recommended responsibilities:

- `BankMainScreen`
- `BankBoxScreen`
- `BankBalanceScreen`
- `BankChequeIssuanceScreen` or a renamed replacement that preserves existing service integration

### 13.2 Reusable presentation components

Recommended reusable components:

- `BankDialogueFrame`
- `BankerPortraitRenderer`
- `BankActionButtonColumn`
- `BankStatusPresenter`
- `BankInventoryGrid`
- `BankStoredItemGrid`
- `BankDragController`

Names are illustrative. Responsibilities are authoritative.

### 13.3 Recommended Bank Box implementation strategy

The preferred initial strategy is a custom drag-capable screen that preserves the existing server packet trust model.

Reasons:

- the bank is an external persistent store, not a normal Minecraft `Container`;
- stored entries are addressed by public UUID;
- deposit requests already use a live player slot index;
- withdrawal requests already use a stored-item public ID;
- the server already revalidates every authoritative fact;
- the required interaction is a deposit gesture, not a client-controlled persistent slot move.

The implementation may reuse vanilla rendering and input concepts, but it must not create a fake local container whose state is mistaken for authority.

A milestone may overturn this choice only when repository reconnaissance proves that a real menu/container architecture now exists and can preserve the same external transaction guarantees without duplicating state.

---

## 14. Server and network contracts

### 14.1 Existing contracts to preserve where practical

- account-open snapshot;
- item deposit by player inventory slot;
- stored-item withdrawal by public item ID;
- currency withdrawal by denomination and amount;
- cheque issuance;
- transfer result with operation and result kind.

### 14.2 New or extended contracts likely required

Revised 2026-08-03 against what actually exists:

| Contract | Status |
| --- | --- |
| Success responses that trigger updated account state | **Exists.** `refreshAccount` on every `Confirmed` — see §6.2. |
| Richer stored-item render summaries | **Mostly exists.** Name and count delivered; only `item_key` plumbing remains — see §9.5.2. |
| Deposit All Coins request/result | **Genuinely new, both repositories** — see §11.5. |
| Multi-denomination cheque issuance | **Genuinely new, both repositories** — added by owner decision 2026-08-03; see §12.3.1. |
| Account refresh request/response (client-initiated) | Does not exist. Add only if §19.3 proves it necessary — see §6.2. |
| Explicit revision data to reject stale UI operations | Does not exist. Justify before adding. |
| Inventory-full withdrawal as a distinct result kind | Does not exist; renders as a generic clean rejection today. **NeoForge-only** — both ends of `BankTransferResultS2CPayload` are NeoForge, so no Rails contract is involved. See §15. |

Two rows of that table are unambiguously new cross-repository work.

### 14.3 Request idempotency and duplicate prevention

Every mutation should have a stable request identity or equivalent duplicate-protection mechanism when the existing service architecture supports it.

At minimum:

- a single user gesture sends one request;
- pending UI blocks repeated submission;
- server handling is safe when duplicate packets are received;
- the result identifies the operation being completed.

### 14.4 No client authority

The client must never authoritatively supply:

- item weight;
- item eligibility;
- persistent bank position;
- currency balance;
- stored item count;
- cheque validity;
- account owner;
- transaction success.

---

## 15. Error and status presentation

The rebuilt screens must present meaningful outcomes without exposing internal implementation detail.

Required categories:

- success;
- nothing to deposit;
- invalid amount;
- insufficient balance;
- ineligible item;
- insufficient bank capacity;
- player inventory full;
- stored item no longer available;
- clean rejection;
- reconciliation required;
- cheque not found;
- cheque already redeemed;
- cheque cancelled;
- cheque voided;
- pending cheque delivery;
- connection or timeout uncertainty.

### 15.1 Reconciliation message

A reconciliation-required result must remain visually distinct from an ordinary rejection and must advise the player not to repeat the operation until staff review when that remains the established domain rule.

### 15.2 Status lifetime

A status message should persist long enough to be read, but it must be replaced or cleared when:

- the user begins a new operation;
- the account is authoritatively refreshed;
- the user navigates to a screen where the message is no longer relevant.

---

## 16. Scaling and accessibility

The screens must be usable across supported GUI scales.

Requirements:

- no control may render beneath the parchment frame or outside the visible screen;
- body text must wrap without overlapping action buttons;
- the Bank Box grid must retain usable cell sizes;
- scrolling or pagination must appear when bank contents exceed available space;
- focus order must be deterministic for text fields and buttons;
- disabled controls must remain readable;
- selected, hovered, dragged, valid-drop, invalid-drop, and pending states must be visually distinct;
- color must not be the only state indicator;
- tooltips must stay within the screen bounds.

---

## 17. Data and localization

All visible labels and messages should use translatable components.

Do not retain hard-coded English literals in final production UI where the project supports localization.

Required localization keys should cover:

- screen titles;
- buttons;
- balance sentence;
- weight sentence;
- empty bank;
- no coins;
- validation failures;
- transaction results;
- cheque statuses;
- drag eligibility explanations.

Registry identifiers such as `item.britannia_mod...` must never be displayed as the normal item name when a translated hover name is available.

### 17.1 Localization needs an explicit owner in every milestone (added 2026-08-03)

The current screen is full of hard-coded English: `"Stored item"`, `"The vault is empty."`,
`"Withdraw from vault:"`, `"Deposit from inventory:"`, and the status strings. This section
forbids carrying that pattern into the final UI, but the milestone map assigns the work to no one
in particular — it is spread implicitly across every screen milestone, which is precisely how it
ends up deferred to a cleanup that never happens.

Each screen milestone's acceptance gate must therefore state explicitly that its user-visible
strings are translatable, or the epic must carry one dedicated localization milestone before final
acceptance. Either is acceptable. Leaving it implicit is not.

Note one deliberate exception: `BankItemSummary.FALLBACK_NAME` (`"Stored item"`) is a data-layer
fallback for an item with no stored name, not screen copy. It still needs a translation key, but
it belongs to the render contract in §9.5, not to any one screen.

---

## 18. Security and integrity requirements

The rebuilt UI must preserve the current server-authoritative trust boundary.

Server-side validation must cover:

- active player and banker relationship;
- account ownership;
- live player slot;
- live stack identity;
- item eligibility;
- item fingerprint/components;
- weight and capacity;
- stored item ownership and availability;
- currency balance;
- cheque status;
- inventory delivery capacity;
- duplicate requests;
- stale client state.

The client is a renderer and input source. It is not a banking authority.

---

## 19. Testing requirements

### 19.1 Unit and service tests

Cover:

- Deposit All Coins denomination totals;
- no-coins behavior;
- whole-stack item deposit;
- ineligible item rejection;
- overweight deposit;
- stale source slot;
- cheque routing;
- loose currency routing;
- container-with-coins non-routing;
- item withdrawal by public ID;
- stale stored item;
- currency withdrawal;
- cheque issuance;
- inventory-full delivery;
- duplicate request handling;
- reconciliation outcomes.

### 19.2 Client interaction tests

Cover:

- opening each screen;
- all Back routes;
- Escape from every screen;
- drag threshold;
- valid drag/drop;
- invalid drop cancellation;
- ineligible source;
- source stack changes during drag;
- duplicate release;
- pending lock;
- grid selection;
- no double-click requirement;
- amount validation;
- GUI scaling;
- scrolling/pagination;
- tooltips.

### 19.3 Multiplayer and latency tests

Cover:

- two clients interacting with the same logical account state when possible;
- a stored item withdrawn before another client's request;
- balance changes while a cheque screen is open;
- latency during drag deposit;
- closing during a pending request;
- reconnect and authoritative refresh;
- modified-client packet attempts.

### 19.4 Visual owner gate

The epic is not complete until the owner confirms:

- parchment dialogue layout;
- portrait placement;
- button placement;
- bank-box grid layout;
- player inventory layout;
- drag feedback;
- readable balances;
- cheque form layout;
- supported GUI scales.

---

## 20. Migration and retirement

When the replacement reaches parity:

- route banker interaction to the Bank Main Screen;
- remove the legacy combined layout;
- remove the 400 ms double-click tracking fields and handlers;
- remove row-list deposit and withdrawal presentation;
- remove obsolete Deposit/Withdraw/Checks button arrangement;
- retain reusable transaction messages and services;
- ensure no route can reopen the retired combined screen;
- update tests and documentation.

The old `BankScreen` is a source of proven transaction behavior, not the final container for four modes.

---

## 21. Completion criteria

The epic is complete only when:

- four distinct screens exist and follow the required navigation;
- Main, Balance, and Create Cheque use a coherent dialogue-family layout;
- Bank Box uses a multi-row, multi-column stored-item grid;
- player inventory and hotbar are visible on Bank Box;
- eligible items can be dragged from player inventory into the Bank Box;
- double-click is not required for deposits or stored-item selection;
- deposits remain whole-stack and server-authoritative;
- item, currency, and cheque routing remains correct;
- stored-item withdrawal works by public ID;
- Deposit All Coins works atomically;
- currency withdrawal works;
- cheque creation works;
- state refreshes after successful mutations;
- failures and reconciliation are presented correctly;
- tests pass;
- dedicated-server and client smoke tests pass;
- the owner approves the final visual and interaction result.
