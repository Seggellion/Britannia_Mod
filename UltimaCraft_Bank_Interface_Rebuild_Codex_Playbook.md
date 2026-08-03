# UltimaCraft Bank Interface Rebuild Codex Playbook

**Document status:** Proposed authoritative implementation playbook  
**Feature:** Four-screen bank interaction rebuild  
**Companion design:** `UltimaCraft_Bank_Interface_Rebuild_Design.md`  
**Revised:** 2026-08-03, against NeoForge `banking` @ `f94b42e` and Rails `banking` @ `1267195`

> **Read the companion design's §4.7 first.** The item-identity program (Milestones 15–19)
> shipped after both documents were drafted and satisfies several requirements they describe as
> outstanding. Milestones 0, 2, 6, 10 and 12 below were revised accordingly. Do not re-derive
> those findings.

> **Then read `UltimaCraft_Bank_Interface_Rebuild_Milestone_0_Report.md`.** Milestone 0 is
> complete. Its findings closed the six items the earlier reconnaissance left open, and one owner
> decision followed from it: **cheques fund from all three denominations**, which split Milestone
> 8 into 8a (Rails) and 8b (NeoForge) and made this a two-Rails-contract epic. See design §12.3.1.

---

## 1. Operating rules for Codex

This playbook must be executed one milestone at a time.

For every milestone:

1. Read the complete design document and this playbook.
2. Inspect the current repository before proposing changes.
3. Preserve existing server-authoritative banking behavior unless the milestone explicitly changes it.
4. Do not begin later milestones early.
5. Do not perform unrelated cleanup.
6. Add or update tests within the milestone that introduces the behavior.
7. Report:
   - files inspected;
   - files changed;
   - decisions made;
   - tests run;
   - results;
   - unresolved blockers;
   - exact scope not performed.
8. Stop for owner review before continuing.
9. Commit only when the owner explicitly authorizes a commit.
10. Do not push, merge, tag, release, or deploy unless separately authorized.

If the repository uses an isolated clone or branch workflow, follow the active project-management instructions exactly.

### 1.1 Cross-repository rules (added 2026-08-03)

This epic is almost entirely NeoForge work, with exactly two exceptions: **Milestones 6a and 8a**
each require a new Rails contract. (This section originally named only Milestone 6; 8a was added
on 2026-08-03 by owner decision — see design §12.3.1.) When a milestone touches both repositories,
the rules the item-identity program established apply unchanged — see
`UltimaCraft_Banking_Item_Identity_Codex_Milestones_15_19.md` §3.3, §3.4 and §4.

1. **Name the owning repository before writing code.** Ambiguous ownership, not tooling, is the
   main failure mode of cross-repository work.
2. **Rails accepts before NeoForge sends.** Rails validates a strict envelope and rejects unknown
   fields, so a client that emits early breaks the operation on every shard. The Rails half ships
   first and ships behaviour-neutral.
3. **Mixed client versions are permanent.** Third-party shard operators upgrade on their own
   schedule. Every contract change is additive and leaves older clients working.
4. **Working repositories.** NeoForge: `Britannia_Mod`, branch `banking`. Rails:
   `ultimacraft-website` (WSL, `/home/dusti/ultimacraft-website`), branch `banking`. Confirm with
   `git branch --show-current` rather than assuming — this has been wrong before.

---

## 2. Authoritative requirements summary

The final feature contains four screens:

1. **Bank Main Screen**
   - banker portrait and name;
   - greeting;
   - Open Bank Box;
   - Balance;
   - Create Cheque.

2. **Bank Box Screen**
   - stored bank items in a multi-row, multi-column grid;
   - player main inventory and hotbar;
   - drag whole eligible stacks from player inventory into the bank box;
   - no double-click deposit;
   - single-click stored-item selection;
   - Withdraw action;
   - currency withdrawal controls;
   - weight and status display;
   - Back.

3. **Bank Balance Screen**
   - banker portrait and name;
   - Gold, Silver, and Copper balances;
   - Back;
   - Deposit All Coins.

4. **Create Cheque Screen**
   - banker portrait and name;
   - denomination selection;
   - amount input;
   - confirmation;
   - Back.

Back returns to Bank Main. Escape closes banking.

All mutations remain server-authoritative.

---

## 3. Milestone map

| Milestone | Title | Repo | Revised 2026-08-03 |
|---|---|---|---|
| 0 | Repository and banking reconnaissance | — | Partly done; see design §4.7 |
| 1 | Architecture decision and implementation plan | — | Two decisions pre-settled |
| 2 | Banking session and refresh contract | NeoForge | Adopt existing `refreshAccount` |
| 3 | Shared banker dialogue framework | NeoForge | |
| 4 | Bank Main Screen | NeoForge | |
| 5 | Bank Balance Screen presentation | NeoForge | |
| 6a | Deposit All Coins — Rails transaction | **Rails** | **New contract; start early, in parallel** |
| 6b | Deposit All Coins — client integration | NeoForge | Requires 6a deployed |
| 7 | Create Cheque Screen presentation | NeoForge | Presents three denominations; sends nothing |
| 8a | Multi-denomination cheque issuance — Rails transaction | **Rails** | **New contract; start early, in parallel** |
| 8b | Cheque issuance integration — client | NeoForge | Requires 8a deployed |
| 9 | Bank Box screen shell | NeoForge | |
| 10 | Stored-item render contract | NeoForge | Reduced to `item_key` plumbing |
| 11 | Player inventory and bank grids | NeoForge | |
| 12 | Drag interaction engine | NeoForge | **Highest risk; spike first** |
| 13 | Drag-to-deposit integration | NeoForge | |
| 14 | Special deposit routing and cheque redemption | NeoForge | |
| 15 | Stored-item withdrawal | NeoForge | |
| 16 | Currency withdrawal migration | NeoForge | |
| 17 | Refresh, error, and reconciliation framework | NeoForge | |
| 18 | Scaling, multiplayer, and security validation | Both | |
| 19 | Legacy retirement and final acceptance gate | NeoForge | |

**Revised 2026-08-03 (owner decision).** Every milestone except **6a and 8a** is NeoForge-only.
Those two are the epic's only Rails contracts and the only places the §1.1 cross-repository rules
bind. 8a was added after Milestone 0 found that cheque issuance is gold-only end to end and the
owner confirmed design §12.3's three denominations rather than relaxing them — see design §12.3.1.

Both Rails halves are screen-independent and should run in parallel with the client sequence
rather than waiting for their numeric position. Under §1.1 rule 2 each must be accepted by Rails
before any client emits the new field.

### 3.1 Localization ownership (added 2026-08-03)

Design §17.1 forbids shipping the hard-coded English the current screen is full of, but this map
assigns that work to nobody, which is how it gets deferred indefinitely. **Every screen milestone
(4, 5, 7, 9, 11) must include "all user-visible strings introduced by this milestone are
translatable" in its acceptance gate.** If the owner would rather batch it, add one dedicated
localization milestone before 19 and say so here. Do not leave it implicit.

---

# Milestone 0: Repository and banking reconnaissance

> **Partly completed 2026-08-03.** A reconnaissance pass against `f94b42e` / `1267195` already
> answered several of the required findings, and they are recorded in the companion design's
> §4.7, §6.2, §9.5 and §11.5. **Read those before inspecting anything.** Confirm them rather than
> re-deriving them, and spend this milestone on what they do not cover.
>
> Already established, with evidence:
> - the baseline screen is `client.gui.BankScreen`, not `client.screen.BankScreen`;
> - the double-click gesture is real and still present (`DOUBLE_CLICK_MS = 400`);
> - item identity (`display_name`, `count`) reaches the client; `item_key` is sent by Rails and
>   dropped by `BankingOpenResponseParser`;
> - post-transaction refresh already exists as server push via `refreshAccount`;
> - the teller portrait already resolves correctly via `tellerGender`;
> - Deposit All Coins exists nowhere in either repository.

## Objective

Produce a verified map of the existing banking implementation before changing production code,
building on the recorded findings above rather than repeating them.

## Required inspection

At minimum inspect:

- `BankScreen`;
- `BankChequeIssuanceScreen`;
- banker interaction and screen-opening entry points;
- `DialogueLayout`;
- `DialogueViewModel`;
- `DialoguePresentation`;
- `BankAccountOpenedS2CPayload`;
- `BankDepositRequestC2SPayload`;
- `BankWithdrawalRequestC2SPayload`;
- `BankCurrencyWithdrawalRequestC2SPayload`;
- cheque issuance and redemption payloads;
- `BankTransferResultS2CPayload`;
- `ClientNetworkHandler`;
- `BankItemSummary`;
- `BankItemEligibility`;
- `CurrencyItemRegistry`;
- cheque data components;
- server packet handlers;
- deposit, withdrawal, currency, and cheque proxy/services;
- Rails endpoints and transaction services;
- all banking tests;
- any newer `AbstractContainerMenu`, `Slot`, draggable widget, or inventory-grid precedent elsewhere in the repository.

## Required findings

Document:

- the complete current banker-to-screen flow;
- how account snapshots are obtained;
- how success is currently communicated;
- whether success triggers a refreshed account snapshot;
- exact item deposit routing;
- exact cheque redemption routing;
- exact currency deposit routing;
- exact withdrawal delivery behavior;
- item summary fields available to the client;
- whether stored item icons can currently be reconstructed safely;
- current test coverage;
- current reconciliation behavior;
- whether a slot/menu architecture now exists elsewhere in the codebase;
- all gaps between current code and the design document.

### Still genuinely open after the 2026-08-03 pass

These are the findings that pass did **not** establish, and they are where this milestone's effort
belongs:

- whether any draggable-widget or inventory-grid precedent exists elsewhere in the repository
  (checked for `AbstractContainerMenu` in banking only — the wider codebase was not searched);
- current banking test coverage and its shape, in both repositories;
- exact currency-deposit and cheque-redemption routing at the packet level;
- withdrawal delivery behaviour when the player's inventory is full;
- current reconciliation presentation in the existing screen;
- whether `CurrencyItemRegistry` can already classify a "bare coin stack" well enough for
  Milestone 6, or whether that classification is itself new work.

## Restrictions

- No production implementation.
- No packet changes.
- No UI refactor.
- No speculative deletion.
- Small documentation-only corrections are allowed only with owner approval.

## Acceptance gate

Milestone 0 passes when the report is evidence-based and identifies every dependency needed for the four-screen rebuild.

---

# Milestone 1: Architecture decision and implementation plan

## Objective

Convert Milestone 0 findings into the final technical architecture for this epic.

## Required decisions

Lock:

- screen class boundaries;
- shared dialogue composition strategy;
- banking-session/view-model ownership;
- Back and Escape behavior;
- custom drag-capable screen versus real menu/container architecture;
- whole-stack deposit scope;
- bank-grid dimensions and overflow strategy;
- stored-item render-summary format — **constrained: the icon source is decided in design §9.5.3
  (`item_key` only). This milestone decides the plumbing, not whether to ship component data;**
- post-transaction refresh strategy — **constrained: server push already exists (design §6.2).
  Decide whether anything further is needed, not how to build it from scratch;**
- Deposit All Coins server transaction shape — **note this is the epic's only new Rails contract
  (design §11.5) and carries the §1.1 cross-repository rules;**
- pending-state ownership;
- stale-state and duplicate-request strategy;
- localization strategy;
- exact migration path from the old `BankScreen`.

## Default architecture

Unless reconnaissance proves a better existing repository precedent, use:

- separate screen classes;
- reusable dialogue layout composition;
- a custom Bank Box screen;
- explicit inventory slot widgets;
- explicit bank-item grid widgets;
- a custom drag controller;
- existing authoritative slot-index deposit requests;
- existing public-UUID stored-item withdrawals.

## Deliverable

Create an architecture decision record or milestone report containing:

- chosen approach;
- rejected alternatives;
- security implications;
- packet implications;
- data-contract implications;
- test plan;
- file-level implementation map.

## Restrictions

- Do not implement full screens.
- Do not silently add partial-stack behavior.
- Do not treat a client bank-grid cell as a persistent bank slot.

## Acceptance gate

The owner approves the architecture before production implementation begins.

---

# Milestone 2: Banking session and refresh contract

## Objective

Create the shared client presentation state and authoritative refresh mechanism used by all four screens.

## Required work

Implement or adapt a banking-session context containing:

- banker entity ID;
- portrait/teller identity;
- city display name;
- balances;
- current weight;
- weight limit;
- bank-item summaries;
- pending operation;
- relevant status;
- revision or stale-state discriminator where available.

Adopt the refresh mechanism that already exists so screens do not retain stale account snapshots
after successful mutations.

> **Revised 2026-08-03.** `BankingTransferPacketService` already calls `refreshAccount(player,
> teller)` on every `Confirmed` result — deposit, currency deposit, cheque redemption, withdrawal
> — re-running `bank.open` and pushing a fresh `BankAccountOpenedS2CPayload`. **Wire the session
> context to consume that push. Do not build a parallel refresh path.** A client-initiated refresh
> request does not exist and must not be added in this milestone; see design §6.2 for the
> conditions under which it would be justified.

## Required behavior

- screen navigation reuses the active banking session;
- each screen renders the latest session state;
- successful mutations update or refresh affected values;
- a stale response cannot overwrite a newer session state;
- closing banking safely releases client-only screen state;
- the context never becomes authority for banking facts.

## Tests

Add tests for:

- state creation from account-open payload;
- navigation preserving banker/account identity;
- applying a newer refresh;
- rejecting or ignoring an older refresh;
- pending-state transitions;
- session close.

## Restrictions

- Do not implement Bank Box dragging.
- Do not redesign cheque or currency domain logic.
- Do not mutate player inventory locally.

## Acceptance gate

A testable shared session exists and can support all four screens.

---

# Milestone 3: Shared banker dialogue framework

## Objective

Build the reusable presentation foundation for Main, Balance, and Create Cheque.

## Required work

Create reusable composition for:

- parchment background;
- portrait;
- banker name;
- body text;
- right-side button stack;
- optional center form controls;
- validation/status text;
- responsive layout;
- focus order;
- translated labels.

## Required visual states

Buttons must visibly support:

- normal;
- hover;
- keyboard focus;
- pressed;
- disabled;
- pending.

Status rendering must support:

- informational;
- validation;
- success;
- ordinary rejection;
- reconciliation-required warning.

## Tests

Add layout or client tests for:

- narrow screen;
- wide screen;
- multiple GUI scales;
- wrapped dialogue;
- three right-side buttons;
- two right-side buttons;
- form controls without overlap.

## Restrictions

- Do not implement banking mutations.
- Do not copy the entire layout separately into each screen.
- Do not hard-code final English strings when localization exists.

## Acceptance gate

A reusable dialogue frame can render the three required screen shapes.

---

# Milestone 4: Bank Main Screen

## Objective

Implement the new banking hub.

## Required layout

- banker portrait and name on the left;
- greeting in the center;
- Open Bank Box, Balance, and Create Cheque on the right.

## Required behavior

- Open Bank Box opens the Bank Box destination;
- Balance opens the Balance destination;
- Create Cheque opens the cheque destination;
- destination screens receive the active banking session;
- Escape closes banking;
- no legacy account ledger or transaction controls remain on this screen.

Temporary destination placeholders are allowed only when clearly marked and only until their milestones are implemented.

## Tests

Verify:

- each button routes correctly;
- banker/account context is preserved;
- Escape closes;
- repeated clicks do not open stacked duplicate screens;
- old combined controls are absent.

## Acceptance gate

The banker interaction opens the Main Screen as the new hub, subject to owner-approved rollout strategy.

---

# Milestone 5: Bank Balance Screen presentation

## Objective

Implement the read-only Balance Screen and navigation.

## Required layout

- portrait and name;
- dialogue showing Gold, Silver, and Copper;
- Back;
- Deposit All Coins.

## Required behavior

- Back returns to Bank Main;
- Escape closes banking;
- balances come from the shared banking session;
- Deposit All Coins is present but may remain disabled until Milestone 6;
- text updates when refreshed session state changes.

## Tests

Verify:

- all three balances render correctly;
- singular/plural or compact wording follows approved localization;
- Back and Escape differ correctly;
- large values do not overlap buttons;
- disabled pending placeholder is clear before transaction integration.

## Acceptance gate

The owner approves the Balance Screen layout.

---

# Milestone 6: Deposit All Coins transaction

> **This is one of the epic's two new Rails contracts** — the other is Milestone 8a. Verified
> 2026-08-03: no bulk route exists in Rails' `banking/*` namespace, and `deposit_all` /
> `all_coins` appear nowhere in either repository. See design §11.5.
>
> **Split it in two, and start the Rails half early.** It has no dependency on any screen, and
> under §1.1 rule 2 Rails must accept before NeoForge sends. Running it in parallel with
> Milestones 2–5 removes it from the critical path; leaving it at position 6 in a client sequence
> will stall that sequence.
>
> - **6a — Rails.** New endpoint, transaction, denomination totalling, disposition, reconciliation
>   path, tests. Behaviour-neutral until a client calls it. Owns `ultimacraft-website`.
> - **6b — NeoForge.** Client request, pending lock, result handling, Balance Screen wiring.
>   Requires 6a deployed. Owns `Britannia_Mod`.
>
> Do not size this as "a button on the Balance Screen." The §11.4 atomicity requirement is the
> substance.

## Objective

Implement one atomic server-authoritative operation that deposits all supported loose coins from the player's inventory.

## Required server behavior

The operation must:

- inspect the live authoritative inventory;
- identify supported bare coin stacks;
- exclude ordinary items;
- exclude cheques;
- exclude containers that merely contain coins unless existing domain rules explicitly allow them;
- total Gold, Silver, and Copper;
- persist the balance credit;
- dispose of accepted physical coin stacks;
- return or trigger refreshed balances and inventory;
- distinguish clean rejection from reconciliation required;
- be safe against duplicate requests.

## Required client behavior

- one button press sends one request;
- button becomes pending;
- duplicate clicks are blocked;
- no local stack removal;
- no-coins result is non-destructive and readable;
- success refreshes the Balance Screen;
- closing during pending does not fabricate cancellation.

## Tests

Cover:

- no coins;
- one denomination;
- mixed denominations;
- multiple stacks;
- maximum supported values;
- container holding coins;
- cheque present;
- concurrent inventory mutation;
- persistence rejection;
- inventory-disposition failure;
- duplicate packet;
- successful refresh.

## Acceptance gate

Deposit All Coins works as one authoritative transaction and does not use a client packet loop.

---

# Milestone 7: Create Cheque Screen presentation

> **Note added 2026-08-03.** This milestone sends no packet, so it is unblocked by Milestone 8a
> and may be built while 8a is in flight. The three denomination controls it presents are
> genuinely required (design §12.3, confirmed by owner decision — see §12.3.1), not placeholders.
> Their contextual balance comes from the shared banking session, which already carries all three
> balances.

## Objective

Rebuild the cheque screen using the shared dialogue framework.

## Required layout

- portrait and name;
- prompt;
- Gold, Silver, Copper selection;
- positive amount input;
- selected balance context;
- Back;
- Confirm/Create;
- Cancel if retained by approved layout;
- status area.

## Required client validation

- empty;
- malformed;
- zero;
- negative;
- overflow;
- no denomination selected.

## Required behavior

- Back returns to Main;
- Escape closes banking;
- changing denomination updates contextual balance;
- pending state disables form submission;
- no issuance packet is required in this milestone unless owner approves combined delivery.

## Tests

Cover layout, focus, input validation, denomination changes, Back, Escape, and GUI scaling.

## Acceptance gate

The owner approves the new cheque form before transaction integration.

---

# Milestone 8: Cheque issuance integration

> **Split in two on 2026-08-03 by owner decision.** Milestone 0 found that cheque issuance is
> gold-only through every layer — request validator, reservation, debit, and packet. The owner
> confirmed design §12.3's three denominations rather than relaxing the requirement, which makes
> this the epic's **second** cross-repository milestone. See design §12.3.1 for the full verified
> baseline and the reasoning.
>
> **Start the Rails half early**, in parallel with Milestones 2–7, exactly as with 6a. It depends
> on no screen, and under §1.1 rule 2 Rails must accept the denomination before any client sends
> it.
>
> - **8a — Rails.** Denomination on the cheque contract, keyed reservation and debit, amount-unit
>   rule, bounds policy, tests. Behaviour-neutral until a client sends the field. Owns
>   `ultimacraft-website`.
> - **8b — NeoForge.** Packet field, screen wiring, result handling. Requires 8a deployed. Owns
>   `Britannia_Mod`.
>
> **Do not size 8a as "add a field."** The bounds question in §8a.1 is a product decision that
> must be answered before the code is written.

---

# Milestone 8a: Multi-denomination cheque issuance — Rails transaction

**Repository:** `ultimacraft-website`, branch `banking`. Confirm with `git branch --show-current`.

## Objective

Extend cheque issuance to fund a cheque from any of the three currency balances, without changing
what a cheque *is* or how it redeems.

## What must not change

Verified 2026-08-03 and deliberately out of scope:

- **`bank_cheques` schema.** `amount` stays a single copper integer. There is no `currency_key`
  column and none is to be added.
- **ADR-012.** A cheque remains currency-agnostic *at rest*. Only the funding instruction gains a
  denomination. **This milestone does not reverse ADR-012** — it narrows its scope to the cheque
  itself, and that narrowing must be recorded rather than left implicit.
- **Redemption.** `BankCheque#coin_mix` already converts a stored copper value into a
  gold/silver/copper mix against the canonical ratios. A cheque funded from silver already redeems
  correctly. Do not touch the redemption path.

## Required work

1. **Contract.** Add `currency_key` to `ChequePayloadValidator`'s `REQUIRED_FIELDS` /
   `ALLOWED_FIELDS`. The allow-list is strict and currently rejects unknown keys, which is exactly
   why §1.1 rule 2 binds here. Accept only `gold` / `silver` / `copper`, matching
   `CurrencyPayloadValidator`'s existing key vocabulary.
2. **Amount unit.** Replace the `% GOLD_UNIT` check with "a whole multiple of the selected
   denomination's copper unit" — gold 10 000, silver 100, copper 1. The rule exists to prevent
   silent truncation when debiting a whole-coin integer column, and that hazard is per-denomination,
   not gold-specific. Preserve the existing failure outcome (`INVALID_CHEQUE_AMOUNT`).
3. **Reservation.** Generalize `BankTransferOperations::Create`'s cheque path from
   `reserved_gold_balance` to the keyed reserved column. `reserved_gold_balance`,
   `reserved_silver_balance` and `reserved_copper_balance` all already exist and carry database
   check constraints against overdraft and negativity. **Reuse the mechanism `currency_withdrawal`
   already drives generically; do not write a second one.**
4. **Debit.** Generalize `BankTransferOperations::Confirm`'s cheque path from
   `gold_balance` / `reserved_gold_balance` to the keyed pair, same row-locking shape.
5. **Compatibility.** A request omitting `currency_key` must keep behaving exactly as it does
   today — gold. Mixed client versions are permanent (§1.1 rule 3), and older clients will keep
   sending the current shape indefinitely.

## 8a.1 Required product decision: amount bounds

`BankCheque::MIN_AMOUNT` is 5 000 000 copper — 500 gold — and `MAX_AMOUNT` is 1 000 000 000
(ADR-018/ADR-019). Applied unchanged to a copper cheque, the minimum is five million copper coins,
which makes silver and copper cheques unusable in practice.

**Obtain an explicit owner decision before writing code.** Options:

- **per-denomination bounds** — each denomination gets its own min/max, so a copper cheque has a
  copper-scaled floor;
- **absolute bounds retained** — the floor stays 5 000 000 copper of value regardless of funding
  denomination, and the low denominations exist only nominally;
- **revised absolute bounds** — one floor, lowered enough to make all three usable.

Record the choice as a new ADR alongside ADR-018/ADR-019. Do not infer it from the code.

## Tests

Cover, per denomination:

- valid issuance from each of gold, silver, copper;
- amount not a whole multiple of the denomination unit;
- amount below minimum and above maximum, under the bounds policy chosen in §8a.1;
- insufficient balance in the selected denomination while another denomination holds enough;
- reservation released on cancel;
- reservation released on expire;
- concurrent issuance against the same denomination;
- concurrent issuance against two different denominations on one account;
- unknown or malformed `currency_key`;
- unknown extra field still rejected;
- **request with no `currency_key` behaves exactly as today (gold)**;
- redemption of a silver-funded and a copper-funded cheque yields the correct coin mix.

## Restrictions

- No `bank_cheques` schema change.
- No change to the redemption path.
- No client change in this milestone.
- Behaviour must stay neutral until a client sends the field.

## Acceptance gate

Rails accepts and correctly funds a cheque from any denomination, older clients are unaffected,
the bounds policy is recorded as an ADR, and `docs/banking_bank_cheque_issuance.md` is updated to
match.

---

# Milestone 8b: Cheque issuance integration — client

**Repository:** `Britannia_Mod`, branch `banking`. **Requires 8a deployed.**

## Objective

Connect the rebuilt cheque presentation to the authoritative issuance workflow, now carrying a
denomination.

## Required work

Reuse or safely adapt:

- existing cheque issuance packet, **extended with the selected denomination**;
- server-side balance validation;
- cheque persistence;
- cheque item construction;
- data components;
- player inventory delivery;
- pending-delivery handling;
- transfer result routing.

## Required behavior

- only one request per confirmation;
- the selected denomination is sent with the amount;
- server revalidates denomination and amount — the client's selection is a request, never a fact;
- insufficient balance in the selected denomination is a clean failure;
- inventory-full or uncertain delivery follows existing pending/reconciliation rules;
- success refreshes balances;
- the delivered cheque is not fabricated by the client;
- closing during pending is safe.

## Tests

Cover:

- each denomination end to end;
- exact balance;
- insufficient balance in the selected denomination while another holds enough;
- malformed modified-client request, including an unsupported denomination key;
- amount not a whole multiple of the selected denomination's unit;
- inventory full;
- duplicate request;
- persistence failure;
- pending delivery;
- successful refresh.

## Acceptance gate

Cheque creation works from all three denominations through the new screen, and reaches feature
parity with the existing service for gold.

---

# Milestone 9: Bank Box screen shell

## Objective

Create the dedicated Bank Box screen structure without moving items yet.

## Required layout

- bank-box heading;
- weight usage and limit;
- bank-grid region;
- player-inventory region;
- hotbar region;
- Back;
- Withdraw;
- currency amount input;
- Gold, Silver, Copper withdrawal buttons;
- status area.

## Required behavior

- Back returns to Main;
- Escape closes banking;
- screen remains non-pausing;
- pending controls are represented;
- grid regions respond to approved GUI scaling;
- no legacy scrolling text lists appear.

## Restrictions

- No item deposit packet.
- No item withdrawal packet.
- No local drag mutation.
- No fake bank items.

## Tests

Verify layout at supported resolutions and GUI scales.

## Acceptance gate

The owner approves the Bank Box structure before data binding.

---

# Milestone 10: Stored-item render contract

## Objective

Provide enough safe client data to render each stored bank item as a grid icon.

> **Substantially reduced 2026-08-03.** Milestones 15–19 already delivered most of this. See
> design §9.5 for the verified field-by-field status.

## Required investigation

Already answered — confirm, do not re-derive:

| Field | Status |
| --- | --- |
| public ID, weight, display name, count | delivered end to end |
| registry identity (`item_key`) | sent by Rails, **dropped by `BankingOpenResponseParser`** |
| visual components | not available, and deliberately out of scope |

## Required work

Carry `item_key` from `BankingOpenResponseParser` through `BankItemSummary` and
`BankAccountOpenedS2CPayload` to the grid. **No Rails change is required** — it is already in the
response.

Match the lenient treatment the existing identity fields use: a malformed or absent `item_key`
degrades that one cell to the unknown-icon fallback and must never fail the whole account view.

Icons render from `item_key` alone. Component-accurate icons are decided against in design
§9.5.3 — **do not reopen that decision here**, and do not add a render-snapshot field or ship
`payload` to the client.

The client may receive visual data, but withdrawal authority must remain the public bank-item ID.

## Security rules

The server must ignore client claims about:

- registry ID;
- components;
- count;
- weight;
- owner;
- persistent position.

## Compatibility behavior

When an item cannot be rendered:

- show a safe missing/unknown icon;
- show a readable fallback name;
- preserve the public ID for authoritative withdrawal;
- log enough diagnostic detail without exposing sensitive data;
- do not crash the entire grid.

## Tests

Cover:

- vanilla item;
- modded item;
- stack count;
- custom name;
- component-based visual variant;
- missing registry entry;
- malformed stored snapshot;
- fallback tooltip.

## Acceptance gate

Real bank contents can be rendered reliably in a grid.

---

# Milestone 11: Player inventory and bank grids

## Objective

Implement the final read-only grid interaction surfaces.

## Player inventory requirements

Render:

- 27 main-inventory slots;
- 9 hotbar slots;
- empty slots;
- stack icons;
- counts;
- tooltips;
- eligibility indication.

## Bank grid requirements

Render:

- nine columns unless architecture decision approved another count;
- multiple visible rows;
- empty cells;
- stored item icons;
- counts;
- tooltips;
- weight where approved;
- scrolling or paging;
- single-click selection;
- clear selected state.

## Required behavior

- stored item selection uses one click;
- no double-click timer exists;
- selecting an empty bank cell clears or leaves selection according to approved UX;
- player inventory click does not deposit yet;
- scroll does not alter unrelated widgets;
- selection remains bound to public item ID, not visual cell index.

## Tests

Cover empty bank, partially filled grid, overflow, selection, content refresh, missing item fallback, and GUI scaling.

## Acceptance gate

Both grids display real state correctly without mutations.

---

# Milestone 12: Drag interaction engine

> **Highest-risk milestone in the epic. Time-box a spike before committing to the rest of the
> Bank Box sequence.** Because the Bank Box is a plain `Screen` with no vanilla `Slot` or
> `Container` (design §4.1, §13.3 — a deliberate and correct choice), hit testing, ghost
> rendering, the movement threshold, and GUI-scale correctness are all hand-rolled with no
> framework support.
>
> Prove the state machine and ghost rendering at GUI scales 1 through 4 before building
> Milestones 13–15 on top of it. If the spike shows the interaction cannot be made reliable at
> small scales, that is a finding worth surfacing before four further milestones depend on it.

## Objective

Implement a reusable local drag state machine without sending deposit requests yet.

## Required state model

At minimum:

- Idle;
- PressedOnSource;
- Dragging;
- HoveringValidTarget;
- HoveringInvalidTarget;
- Cancelled;
- Pending handoff state used by the next milestone.

## Drag start requirements

- left mouse button;
- non-empty player inventory or hotbar slot;
- no banking mutation pending;
- movement exceeds approved drag threshold;
- source stack snapshot recorded for comparison;
- ineligible items may either refuse drag or drag with invalid feedback according to the approved UX.

## Visual requirements

- source slot marked;
- ghost stack follows cursor;
- stack count visible;
- valid bank region highlighted;
- invalid region visibly rejected;
- tooltip does not obscure the carried item;
- color is not the only state indicator.

## Cancellation requirements

Cancel safely on:

- release outside valid target;
- Escape;
- screen close;
- resize/re-init;
- source slot becoming empty before request handoff;
- loss of mouse capture where detectable.

## Restrictions

- No deposit packet.
- No local inventory removal.
- No partial-stack behavior.
- No right-click split.

## Tests

Cover threshold, source identification, valid hover, invalid hover, release, cancellation, resize, and duplicate mouse events.

## Acceptance gate

The drag experience is visually approved before it can mutate banking state.

---

# Milestone 13: Drag-to-deposit integration

## Objective

Turn a valid drag release into the authoritative whole-stack deposit operation.

## Required request behavior

On valid release:

- verify the source slot still contains a compatible live stack;
- clear the carried visual;
- enter pending state;
- send exactly one deposit request referencing the live slot;
- do not remove or reduce the local stack;
- lock further banking mutations.

## Required server behavior

Revalidate:

- player;
- banker/account context;
- live slot;
- live stack;
- item fingerprint/components;
- eligibility;
- weight;
- available capacity;
- routing category;
- duplicate request;
- concurrent inventory changes.

## Required success behavior

- authoritative inventory update;
- refreshed bank items;
- refreshed weight;
- refreshed balances when the dropped stack is currency or cheque;
- clear pending state;
- readable success feedback.

## Required failure behavior

- no fabricated local success;
- clear pending state;
- preserve or authoritatively reconcile inventory;
- show specific rejection where available;
- distinguish reconciliation required.

## Tests

Cover:

- eligible ordinary item;
- overweight item;
- ineligible item from modified client;
- source changed during drag;
- source emptied during drag;
- duplicate release;
- latency;
- close during pending;
- success refresh;
- stale refresh ordering.

## Acceptance gate

A player can drag a whole eligible stack into the Bank Box without double-clicking.

---

# Milestone 14: Special deposit routing and cheque redemption

## Objective

Prove that drag-to-deposit preserves all existing special routing.

## Required categories

1. Ordinary bankable item
2. Loose Gold stack
3. Loose Silver stack
4. Loose Copper stack
5. Bank cheque
6. Container that contains coins but is not itself a currency stack
7. Ineligible item

## Required behavior

- ordinary item is stored;
- loose coin stack credits the correct currency balance;
- cheque enters redemption, not item storage;
- container-with-coins follows ordinary eligibility or rejection, never loose-currency routing merely because of contents;
- cheque-specific failures remain distinct;
- account and inventory refresh correctly for each route.

## Tests

Cover every category and all cheque terminal states:

- not found;
- already redeemed;
- cancelled;
- voided;
- successful redemption;
- reconciliation required.

## Acceptance gate

The new drag gesture changes only the UI interaction, not the established banking-domain routing.

---

# Milestone 15: Stored-item withdrawal

## Objective

Implement withdrawal of a selected bank-grid item.

## Required interaction

1. Single-click stored item.
2. Selected state appears.
3. Withdraw becomes active.
4. Press Withdraw.
5. Send stored item's public ID.
6. Enter pending.
7. Refresh state after result.

## Required server validation

- account ownership;
- item still exists;
- item belongs to account;
- current stored representation;
- player inventory delivery capacity;
- duplicate request;
- persistence mutation;
- reconciliation.

## Required client behavior

- no double-click;
- no withdrawal by visual index;
- no local item fabrication;
- stale selection clears after refresh when the item is gone;
- inventory-full failure is readable;
- another mutation cannot start while pending.

## Tests

Cover:

- successful withdrawal;
- empty inventory;
- partially full inventory;
- full inventory;
- stale public ID;
- item already withdrawn;
- duplicate click;
- concurrent client withdrawal;
- close during pending;
- successful weight refresh.

## Acceptance gate

Stored items withdraw correctly from the new grid.

---

# Milestone 16: Currency withdrawal migration

## Objective

Move existing currency withdrawal into the final Bank Box screen.

## Required behavior

- amount input accepts positive whole numbers only;
- Gold, Silver, and Copper buttons send the selected denomination;
- buttons disable for invalid input;
- pending blocks duplicates and other mutations;
- server revalidates balance and delivery capacity;
- success refreshes balance and inventory;
- insufficient balance and inventory-full outcomes are readable.

## Required migration

- remove currency-withdraw controls from the legacy combined screen;
- reuse existing packet/service logic where correct;
- adapt result routing to the shared banking session;
- ensure currency withdrawal does not depend on bank-grid selection.

## Tests

Cover each denomination, zero, negative, malformed, overflow, insufficient funds, exact funds, inventory full, duplicate request, and refresh.

## Acceptance gate

Currency withdrawal reaches feature parity inside the new Bank Box.

---

# Milestone 17: Refresh, error, and reconciliation framework

## Objective

Standardize completion and failure handling across all four screens.

## Required result categories

- success;
- nothing to deposit;
- invalid amount;
- insufficient balance;
- ineligible item;
- insufficient bank capacity;
- inventory full;
- stored item unavailable;
- clean rejection;
- reconciliation required;
- cheque not found;
- cheque already redeemed;
- cheque cancelled;
- cheque voided;
- pending delivery;
- connection/timeout uncertainty.

## Required work

- centralize result-to-presentation mapping where practical;
- preserve diegetic banker wording where approved;
- prevent stale result messages from appearing on unrelated screens;
- ensure pending state always resolves or transitions to an explicit uncertain state;
- define refresh after every successful operation;
- define safe behavior when a screen is closed before result arrival;
- log diagnostic context without leaking private account data.

## Tests

Cover every operation/result combination and stale result ordering.

## Acceptance gate

No banking operation leaves the UI indefinitely pending or displaying fabricated state.

---

# Milestone 18: Scaling, multiplayer, and security validation

## Objective

Validate the complete epic under realistic and adversarial conditions.

## Visual matrix

Test at approved combinations of:

- window sizes;
- GUI scales;
- fullscreen/windowed;
- empty and full bank grids;
- large currency values;
- long translated names;
- long status messages.

## Interaction matrix

Test:

- Back from each sub-screen;
- Escape from every screen;
- rapid navigation;
- drag precision;
- invalid drop;
- selection during scrolling;
- amount-field focus;
- tooltip bounds;
- pending controls;
- close during request.

## Multiplayer and latency matrix

Test:

- latency during deposit;
- latency during withdrawal;
- two clients viewing stale bank state;
- concurrent withdrawal of the same stored item;
- balance change while cheque screen is open;
- duplicate packets;
- reconnect after uncertain result;
- authoritative refresh after screen reopen.

## Security matrix

Attempt modified-client requests for:

- ineligible slot;
- empty slot;
- altered item after drag;
- false stored UUID;
- false amount;
- unsupported currency key;
- insufficient funds;
- overweight item;
- duplicate request;
- stale banker/account entity.

## Required evidence

Provide:

- automated test results;
- client smoke-test notes;
- dedicated-server smoke-test notes;
- screenshots at agreed scales;
- unresolved defects;
- performance observations.

## Acceptance gate

All material security, concurrency, scaling, and navigation checks pass.

---

# Milestone 19: Legacy retirement and final acceptance gate

## Objective

Remove the old combined interface and close the epic cleanly.

## Required retirement

Remove or retire:

- legacy combined Bank Screen layout;
- manual scrolling deposit row list;
- manual scrolling withdrawal row list;
- 400 ms double-click constants and tracking fields;
- old Deposit/Withdraw/Checks bottom-button arrangement;
- obsolete selection handlers;
- obsolete rendering code;
- unreachable screen routes;
- superseded tests.

Preserve:

- proven server services;
- required packet contracts;
- eligibility rules;
- result messages;
- reconciliation behavior;
- cheque data;
- currency conversion;
- security validation.

## Final validation

Confirm:

- banker opens Main;
- all three destinations open;
- Back returns to Main;
- Escape closes;
- Bank Box grid works;
- inventory grid works;
- drag-to-deposit works;
- no double-click is required;
- ordinary items, coins, and cheques route correctly;
- stored item withdrawal works;
- currency withdrawal works;
- Deposit All Coins works;
- cheque issuance works from all three denominations;
- refreshed state is correct;
- no legacy route remains;
- tests and smokes pass.

## Owner visual gate

Obtain explicit owner approval for:

- Main Screen;
- Balance Screen;
- Create Cheque Screen;
- Bank Box layout;
- drag behavior;
- final navigation;
- final wording.

## Commit rule

After owner authorization, create exactly the approved isolated local commit for the completed epic. Do not push, merge, tag, release, or deploy without separate authorization.

---

## 4. Final definition of done

The playbook is complete only when every milestone has passed its gate and the final
implementation satisfies the companion design document without retaining the legacy double-click
interface.

That is **twenty-one gates**: Milestones 0–19, with Milestone 6 split into 6a/6b and Milestone 8
split into 8a/8b. Two of them — 6a and 8a — own the Rails repository; the rest own NeoForge.
