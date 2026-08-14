# Milestone 9 — Bank Box screen shell

**Status:** Complete, awaiting owner review at the acceptance gate
**Baseline:** NeoForge `banking` @ `aa419df`

> **All four screens now exist.** This one is structure only — it moves nothing, sends nothing,
> and shows no items. It begins the epic's longest stretch: Milestones 9–16 are what make item and
> currency banking reachable again.

---

## 1. What was built

| File | Role |
| --- | --- |
| `client/screen/bank/BankGridGeometry.java` | One grid of cells: where each is, which one the cursor is over |
| `client/screen/bank/BankGridScroll.java` | Whole-row scroll position, clamped to the content |
| `client/screen/bank/BankBoxLayout.java` | Every region on the screen, responsive |
| `client/screen/BankBoxScreen.java` | The shell |
| `client/screen/bank/BankBoxLayoutTest.java` | 20 tests |

**`BankPlaceholderScreen` is deleted.** Both its factories are gone, its translation keys are gone,
and a test asserts the strings no longer exist. Every destination on the Main screen now reaches a
real screen.

Changed: `BankMainScreen` (routes to the real Bank Box), `BankDialogueFrame` (a `renderStatus`
overload taking coordinates), `en_us.json` (+9 keys, −2 retired), `BankTranslationKeysTest` (+1).

---

## 2. Two things adapt, and both are required rather than decorative

**The bank grid's visible row count.** Design §9.3 permits this explicitly — "the visible row count
may adapt to GUI scale, but the grid must remain multi-row and multi-column" — and it has to,
because the fixed content is roughly 200 scaled pixels before a single stored item is shown, while
the smallest scaled height a player can select is around 240. The floor is two rows and the ceiling
is five, and it also stops growing once the grid could show everything the account holds: five
empty rows for three items is wasted space crowding everything below it.

**Where the controls sit.** Wide enough and they form a column beside the grids; narrow and they
wrap into two rows underneath. A fixed side column pushes the panel past the right edge at GUI
scale 4; a fixed bottom stack wastes width that exists at scale 2.

Nine columns never adapt. That is the one dimension design §9.3 fixes, and it is what makes the
grid read as an inventory rather than as a list.

---

## 3. Two decisions in the geometry worth naming

**Scrolling is by whole rows, not pixels.** A pixel offset means partially-visible cells at the top
and bottom edge — which look like rendering errors, and make hit testing ambiguous. A half-visible
stored item is either clickable or it is not, and neither answer is defensible. The legacy screen
scrolled by pixels and needed a scissor rectangle to hide the overflow; whole rows need neither.

**Grid edges are exclusive on the right and bottom.** The Bank Box stacks three grids, so an
inclusive edge would let one pixel of mouse travel start a drag in one grid and finish it in
another. There is a test for exactly that, because Milestone 12's drag engine is built entirely on
"which cell is the cursor over" — if that answer is wrong, the drag is wrong in ways that look like
a rendering bug.

---

## 4. What this milestone deliberately does not do

Playbook Milestone 9's restrictions, each observed: **no deposit packet, no withdrawal packet, no
local drag mutation, and no fake bank items.** The grids draw their cells and nothing in them.

The controls are built, positioned and disabled rather than omitted — so their placement, focus
order and wording are reviewable now, and the milestones that make them live change behaviour
rather than layout. Weight is the one real value shown, because it is account state rather than
item state and §9.2 lists it as its own region.

Contents arrive at Milestones 10 and 11, dragging at 12 and 13, withdrawal at 15 and 16.

---

## 5. Tests

```
BankBoxLayoutTest        20 tests   (new)
BankChequeTintTest        6 tests   (new, §5a)
BankChequeFormTest       +4 tests   (§5a)
Full JUnit suite        530 pass, 0 fail   (was 500)
All GameTests           290 run, 289 pass  (unchanged)
```

Playbook Milestone 9 asks for layout verification at supported resolutions and GUI scales. Rather
than sample a handful, the structural invariants — nothing overlaps, nothing leaves the panel — are
asserted across **every size from 180×200 to 1200×700**, because the compact/wide switch and the
row-count clamp both have edges no hand-picked list would land on.

Also covered: nine columns at every width; the player's three rows and hotbar always present; never
fewer than two bank rows even at absurd heights; the grid not growing past what the content needs;
an empty vault still showing structure; controls not overlapping in either arrangement; cell
round-tripping; the exclusive-edge property between stacked grids; and scroll clamping at both
ends, including re-clamping when a refresh shrinks the content underneath the view.

The one GameTest failure is still the pre-existing world-state bootstrap test.

**Not covered, by decision:** how it looks. Architecture Decision 0 — no harness here can
instantiate a `Screen`. The geometry beneath it is tested exhaustively; the appearance is this
gate.

---

## 5a. Two owner-directed changes folded in

Both arrived while this milestone was open and are included here rather than deferred.

### Cheque bounds are now both coin counts

**500 to 5 000 000 coins, the same two numbers for all three denominations.** The previous pass
made only the floor a coin count and left the ceiling value-denominated, which is an inconsistent
rule — a coin count at the bottom and a copper figure at the top.

**Gold cannot reach the ceiling**: five million gold is 50 000 000 000 copper and the amount
column holds a fiftieth of that, so gold caps at 100 000 coins. That is a storage limit rather
than a policy one, it is stated in the code and in design §12.3.1, and a player who types
5 000 000 gold gets an honest "too large" before anything is sent. Lifting it needs a bigint
column on the Rails side — recorded as a follow-up, not attempted here.

Enforced on both sides of the client boundary: `BankChequeForm` for the form,
`BankingChequeIssuanceProxyService` for anything that reaches the server.

### The cheque item has artwork

It uses the existing `deed_item` texture — a written instrument already drawn like one — tinted by
the balance that funded it: warm yellow, grey, orange-brown. `BankChequeData` gained a
`currencyKey` for it, optional on read so cheques already in a world keep loading and default to
gold, which is the true answer for anything issued before Milestone 8b rather than a guess.

**This also fixes two pre-existing defects nobody had filed.** There was no `bank_cheque.json`
model at all, so the item rendered as the missing-model placeholder; and there was no
`item.britannia_mod.bank_cheque` translation, so it displayed its own registry id — which design
§17 forbids by name.

**One honest limitation:** a cheque delivered through the crash-resume path renders with the gold
tint regardless of how it was funded. The local receipt does not record the denomination, and
recording it would mean a receipt schema bump — which strands every pending receipt on upgrade
(Milestone 6b-i §2) for the sake of a colour. Identity, value and redemption are unaffected.

### The Bank Box is drawn as the owner's strongbox artwork

Visual-gate feedback, applied. The screen now renders the supplied open-chest illustration as its
background: **the stored-item grid sits inside the red velvet interior**, the title and weight are
written on the open lid, and the pack and controls sit on a dark panel beneath the box — which
also fixes the readability problem the owner's screenshot showed, where dark text floated over the
raw world.

**The art is fixed-proportion; the grid is not** (2–5 rows). One stretched image would squash the
chest at two rows, so it draws as three vertical slices: the lid at up to its natural proportion
(compressing when height is tight, never below the two text rows on it), the velvet stretched to
exactly the grid's height, and the base fixed — velvet tolerates vertical stretch; a lid and a
lock do not. Below ~228 scaled pixels of width the chest cannot fit at all and the screen falls
back to the plain layout, the same yield rule the dialogue portrait follows.

Two consequences handled rather than left to be discovered:

- **A dark-ground status palette.** The parchment severity colours were chosen against a light
  ground — `INFORMATIONAL` is near-black and would vanish on the panel. Each severity now carries
  a `colorOnDark`; marker width and bold are unchanged, so severity still never depends on colour
  alone. Tested for brightness and distinctness.
- **The control column widened 84 → 108.** The owner's screenshot showed "Silver"/"Copper"
  truncated to "Silve"/"pper".

**One manual step remains: the texture itself.** Save the supplied artwork as
`src/main/resources/assets/britannia_mod/textures/screens/bank_box_chest.png`. The blits sample it
fractionally, so any resolution of the same picture renders identically; until the file exists the
chest region shows the missing-texture checker. The slice cuts (42% / 88%) and wall insets (10%)
are named constants calibrated by eye to the artwork — if the grid sits a few pixels off the
velvet in-game, those four numbers are the whole adjustment.

---

## 6. Milestone report

**Decisions made.** Whole-row scrolling (§3); exclusive grid edges (§3); adaptive rows bounded at
2–5 and capped by content; controls reflow rather than a fixed arrangement; a coordinate-taking
`renderStatus` overload so the Bank Box reports outcomes identically to the dialogue screens
without fabricating a layout record around three fields.

**Tests run.** `./gradlew test` — 520 pass. `./gradlew runGameTestServer` — 290 run, 289 pass.

**Unresolved blockers.** None.

**Scope explicitly not performed.**
- No item rendering, no selection, no drag, no packets (§4).
- Item deposit, item withdrawal and currency withdrawal remain unreachable from the UI. The Bank
  Box now *exists* rather than being a placeholder, but nothing in it works yet — Milestones 13–16
  close that.
- Still open from earlier: the inventory-full `Kind` at Milestone 15, and the two flagged
  out-of-scope defects (world-state bootstrap, cheque reservations reported as currency drift).

---

## 7. Acceptance gate

> *The owner approves the Bank Box structure before data binding.*

Yours to judge by looking, and this is the milestone where that matters most — everything from 10
to 16 is built inside these rectangles, so a structural problem is far cheaper to fix now than
after five milestones sit on top of it.

Playbook §3.1: **all user-visible strings introduced by this milestone are translatable**, enforced
by `BankTranslationKeysTest`.

**Worth your eye specifically:**

- **Whether five rows of bank grid plus your full inventory feels cramped** at the GUI scale you
  play at. The row count is one constant.
- **Whether the compact arrangement is reachable in practice** on your setup, or whether it only
  appears at scales nobody uses — if the latter, it is still worth keeping for safety but not worth
  polishing.
- **Whether "Thy pack" reads right** as the label over the player's own inventory, or whether
  plainer wording suits better.

**Stopping here for owner review.** Milestone 10 (stored-item render contract) is next: carrying
`item_key` from the parser through to the grid, which is the last piece before these cells can show
anything.
