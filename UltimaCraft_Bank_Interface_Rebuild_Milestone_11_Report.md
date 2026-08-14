# Milestone 11 — Player inventory and bank grids

**Status:** Complete, awaiting owner review at the acceptance gate
**Baseline:** NeoForge `banking` @ `f5932bd`

> **The Bank Box shows real state.** The vault grid draws the account's actual items through
> Milestone 10's contract; the player grids draw the live inventory with eligibility shading; a
> stored item is selected with **one click, and there is no timer anywhere** — the interaction
> that retires the legacy 400 ms double-click exists as of this milestone. Both grids remain
> read-only: nothing moves until Milestones 13 and 15.

---

## 1. What was built

| File | Role |
| --- | --- |
| `client/screen/bank/BankBoxSelection.java` *(new)* | The click contract, plain and tested |
| `client/screen/bank/BankDepositHint.java` *(new)* | The eligibility shading disjunction |
| `BankBoxScreen` | Contents, hover, tooltips, selection ring, click routing |
| `BankBoxSelectionTest` *(new)* | 9 tests |
| `BankDepositHintGameTests` *(new)* | 3 tests |

**The click semantics, stated once and tested:** an occupied cell selects (by public id); an empty
cell clears; re-clicking the selected item keeps it — a toggle was considered and rejected,
because players trained by the old interface *will* double-click, and a toggle reads that as
select-then-deselect; the grid locks while a mutation is pending; outside the grid the event
passes through untouched.

**Selection is by public id through the scrolled view.** A test pins the composed case: twenty
items scrolled one row down, click visible cell 0, and the selection lands on item 9 — the item
the player is looking at, never the raw cell index. A second test pins that a refresh removing the
selected item leaves the grid showing no selection, so the Withdraw button wired at Milestone 15
can never act on something stale.

---

## 2. The eligibility hint mirrors the router, in one place

`BankDepositHint.isDepositable` is the same three-way disjunction
`BankingTransferPacketService.handleDeposit` routes by: bare coin stack, cheque, or
`BankItemEligibility`. The legacy screen carried it privately and dies at Milestone 19; Milestone
13's drag engine needs the same answer; so it lives once, in the bank package.

The GameTests pin the disjunction rather than re-proving the rules (those have their own suites):
all three depositable categories read depositable, a shulker box *holding* coins reads ineligible
— the carve-out that stops coins being smuggled into item banking — and empty is nothing.

**UX only, never a boundary.** An ineligible stack draws dimmed with a tooltip line — *"The bank
will not take this."* — brightness plus text, not hue alone (design §16). A modified client that
ignores the shading is rejected by the server's own tested path.

---

## 3. Rendering choices worth naming

- **Selection is a gold ring plus fill** — shape carries the state for anyone who cannot rely on
  colour, and it survives whatever icon is drawn inside it.
- **Bank tooltips are built, not taken from the stack.** The icon stack is render data from a
  registry id; its own tooltip would show a *plain* diamond sword's lines for what might be an
  enchanted one, which is a lie of detail. The tooltip shows what the contract actually knows:
  the stored `displayName` (or the translatable fallback), and the weight.
- **Player tooltips are the stack's own**, plus the cannot-bank line when it applies — that grid
  shows live local stacks, so vanilla's full tooltip is the truth there.
- **The count numeral comes from the decoration pass**, same as every vanilla inventory, by
  carrying the summary's count onto the icon stack.
- **Slot mapping is vanilla's:** hotbar cells are slots 0–8, the three main rows 9–35.

---

## 4. Tests

```
BankBoxSelectionTest          9 tests   (new)
BankDepositHintGameTests      3 tests   (new)
Full JUnit suite            550 pass, 0 fail   (was 541)
All GameTests               299 run, 298 pass  (was 296)
```

Playbook Milestone 11's list, mapped: empty bank (`clickingEmptySpaceWithNothingSelected…`, and
the layout suite's empty-vault structure test), partially filled grid (`itemIndexFor`'s past-end
`-1` is what makes cells past the content inert, tested in both suites), overflow (scroll clamping
in `BankBoxLayoutTest`), selection (five tests), content refresh (`aRefreshThatRemovesTheSelected…`
plus the session suite), missing item fallback (Milestone 10's resolver suite), GUI scaling (the
layout sweep).

The one GameTest failure remains the pre-existing world-state bootstrap test.

**Not covered, by decision (Architecture Decision 0):** the drawing itself — icons over velvet,
ring visibility, tooltip bounds. That is this gate and Milestone 18's matrix.

---

## 5. Milestone report

**Decisions made.** No toggle on re-click; empty-cell click clears (the established inventory
idiom — design §9.3 left the choice to approved UX); grid locked while pending; custom-built bank
tooltips (§3).

**Tests run.** `./gradlew test` — 550 pass. `./gradlew runGameTestServer` — 299 run, 298 pass.

**Unresolved blockers.** None.

**Scope explicitly not performed.**
- No mutation of any kind: inventory clicks do nothing (Milestone 13's drag), Withdraw stays
  disabled (Milestone 15), currency controls stay inert (Milestone 16).
- No armor or offhand cells (design §9.4 excludes them).
- Weight-per-item in the bank tooltip is shown; weight *variation* display beyond that (§9.3's
  "weight where approved") awaits owner word.

---

## 6. Acceptance gate

> *Both grids display real state correctly without mutations.*

They do, over tested contracts at every layer: geometry (exhaustive sweep), scroll mapping,
key→icon resolution (six failure modes), selection semantics (nine cases), and the eligibility
disjunction against the real router categories. What remains untestable is appearance, which is
yours.

**Worth your eye in-game:** whether the gold selection ring reads clearly against the red velvet;
whether dimmed-ineligible is distinct enough from unlit empty cells; and whether the bank
tooltip's two lines (name, weight) feel sufficient or want the count echoed in text.

**Stopping here for owner review.** Next is Milestone 12 — the drag interaction engine, flagged
since Milestone 0 as the epic's highest-risk work and the one the playbook says to spike first.
