# Addendum — Drag-to-withdraw

**Status:** Complete, awaiting owner review
**Baseline:** NeoForge `banking` @ `159d7ad` (post-epic)
**Authority:** Owner decision, 2026-08-04 — supersedes design §3's "dragging stored bank items
back into the player's inventory" non-goal, with the target-slot question answered: *"Just find a
place. Same functionality of the withdraw button."*

> **Drag a stored item from the vault to your pack and it withdraws.** The gesture ends in the
> exact `BankWithdrawalRequestC2SPayload` the Withdraw button sends, so the server chooses where
> the item lands and every guard that path has — inventory-full, item-already-gone, duplicate
> protection — is inherited rather than re-implemented. **Zero server change. Zero Rails change.
> Zero new packets.**

---

## 1. The owner's answer made this small

The one design question this feature ever had was "what happens when you drop onto an occupied
pack slot?" — because honoring "put it *here*" means either a new target-slot packet field or
silently putting the item somewhere else while the player pointed at a cell. The owner's answer
dissolves the question: the drop is a **gesture, not an aim**. The whole pack is one drop region,
mirroring how the deposit drag treats the whole vault as one (design §10.5's own rule, run
backwards), and the server finds room exactly as the button does.

That answer is why the diff is client-only and why no security surface moved: a modified client
gains nothing it didn't already have, because the wire is unchanged.

## 2. One machine, two directions

`BankDragController` — Milestone 12's tested state machine — never cared which way items flow.
Its two direction-specific spots were the hardwired drop grid and an outcome named
`DROPPED_ON_BANK`. The grid became a `DropRegion` functional seam (with the original
single-grid constructor kept so Milestone 12's sites read unchanged), the outcome became
`DROPPED_ON_TARGET`, and the Bank Box now runs **two instances of one machine**:

| | Deposit (M12) | Withdraw (this addendum) |
| --- | --- | --- |
| Source | pack slot | vault cell |
| Drop region | the vault grid | the pack's two grids, as one region |
| Identity | slot index | **the row's public id** (§9.6 — never a cell) |
| Watchdog token | registry id + count of the live stack | the public id, while the session still holds it |
| Handoff packet | `BankDepositRequestC2SPayload` | `BankWithdrawalRequestC2SPayload` — the button's own |

Threshold, one-handoff rule, Escape handling, release-frame re-check: shared by construction.

**Identity over position, everywhere.** The gesture captures the row's public id at press. A
refresh or scroll can reorder the vault mid-drag; the ghost keeps carrying the right item, the
source-cell dimming recomputes (or draws nothing if scrolled out of view), and if the row
vanishes — withdrawn or cashed from another client — the watchdog kills the gesture that frame.
The release-frame re-check covers the last instant, same as deposit.

## 3. Interaction layering

The vault cell press now does three things in a fixed order: the cheque double-click check
(consumes only a completing second click), then **selection** (unchanged — the press still
selects, so a sub-threshold release is a plain click and the selection is the whole story), then
arming the drag. Crossing the 4px threshold resets the vault cheque tracker, so
drag-then-return-and-press cannot cash a cheque the player meant to move. The pending lock
blocks arming, the amount field and buttons stay locked during the request, and tooltips are
suppressed while the ghost rides — all the deposit drag's rules, inherited.

Visuals mirror deposit's four non-color-only signals (design §16): dimmed+outlined source cell,
bright border on **both** pack grids while the drop is valid, the row's icon following the
cursor, a cross on invalid hover. One new tooltip line on non-cheque vault items — *"Drag to thy
pack to withdraw it."* (§6.2: the button it originally also named is retired) — cashable cheques
keep their cashing hint instead, because two hints would bury the one that moves money.

## 4. Tests

```
New JUnit tests              9   (BankWithdrawDragTest)
Full JUnit suite           640 pass, 0 fail   (was 631)
All GameTests              333 run, 332 pass  (unchanged -- see below)
```

The new tests prove the **configuration**, not the machine again: dropping on the main grid
withdraws, dropping on the hotbar withdraws, the seam *between* the two grids is invalid rather
than snapping to a neighbour, dropping back on the vault cancels, the identity watchdog kills a
vanished row mid-drag and at the release frame, the pending lock blocks arming, and a
sub-threshold release stays a click.

No new GameTests, deliberately: the server path is untouched and already pinned end-to-end
(full withdrawal, INVENTORY_FULL with reservation cancel, STORED_ITEM_UNAVAILABLE, dedup,
out-of-range teller). Milestone 14's claim — the drag changes the gesture, never the routing —
now holds in both directions by construction, because both gestures end in packets those tests
already drive.

## 5. Report

**Decisions made.** Generalize the existing machine rather than duplicate it; identity (public
id) as both the gesture's authority and its watchdog token, making mid-drag scroll and refresh
harmless; the pack's two grids as one drop region with the seam between them invalid; selection
preserved on sub-threshold release; the hint line only on non-cheque rows.

**Tests run.** `./gradlew test` — 640 pass, 0 fail. `./gradlew runGameTestServer` — 333 run,
332 pass; the sole failure remains the pre-existing order-dependent bootstrap flake.

**Unresolved blockers.** None.

**Not performed.** Partial-stack withdrawal (design §3 keeps it a non-goal; the row is
whole-stack by construction). No design-doc rewrite — §3's non-goal stays historically true of
the epic; this addendum records the owner decision that superseded it.

**Worth trying in-game:** drag an item from the vault to your pack (either grid); drag and
release in the gap between pack and hotbar (cancels); start a drag and scroll the vault
(gesture survives, ghost keeps the right item); drag with a full pack (the teller says so, by
name); Escape mid-drag (cancels the drag, not the screen).

---

## 6. Field test findings (owner, 2026-08-04)

### 6.1 "Dragging gives the error" — the drag was innocent

The rejection traced to Rails' cancel log: `reason: "payload_decode_failed"`, on vault row
`67048520…` ("Gilded Arrow x64", **row id 5** — one of the oldest in this dev database). The
gesture worked end to end: prepare succeeded, Rails reserved the row, and then **this client
refused to decode the stored payload** and correctly cancelled the reservation. Nothing was
lost; the row stays in the vault. The Withdraw button would have failed identically on it —
same packet, same flow.

The row is a pre-codec relic: the current codec's round trip is pinned by 31 GameTests plus the
full deposit-withdraw path, and items deposited through today's client withdraw fine (worth
confirming with the Sulphurous Ash deposited after the schema fix). Rows like it are permanently
unwithdrawable by design — the client must never conjure an item it cannot faithfully decode.
On a dev database they can be deleted or ignored; on production none can exist, because every
production payload was written by the real codec.

**What was genuinely wrong, and is fixed:** the decode-abort path logged *nothing*. Diagnosis
required Rails' logs, while the side that actually knew the reason stayed silent. Both abort
branches now log one WARN naming the row and the reason (corrupt payload, or unsupported inner
schema version). The player-facing kind deliberately stays the generic rejection — Milestone
15's scoping stands, a decode failure asserts nothing a player can act on — but the operator's
log line is where the truth now lives.

### 6.2 The Withdraw button is retired

Owner decision: the drag is the withdrawal, so the button goes. Removed: the button, its
pending/active mirroring, and its two translation keys; the layout reflows so nothing
advertises the absence — Back takes the full row in both arrangements, and the wide control
column shrinks to five rows. The vault tooltip hint now reads simply *"Drag to thy pack to
withdraw it."*

**Selection stays.** The press that starts a drag still selects, the ring still follows the
item, and the cheque double-click still needs the grid's hit-testing. The presenter's
`NOTHING_SELECTED`/`EMPTY_VAULT` statuses remain defined (nothing renders them today; removing
them is churn without benefit).

Tests after both fixes: **640 JUnit pass, 0 fail** (two layout tests rewritten for the
five-row column and the full-width Back); **333 GameTests, 332 pass** (the pre-existing
bootstrap flake).
