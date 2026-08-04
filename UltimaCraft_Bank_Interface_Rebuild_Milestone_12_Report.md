# Milestone 12 — Drag interaction engine

**Status:** Complete, awaiting owner review at the acceptance gate
**Baseline:** NeoForge `banking` @ `f5932bd` (Milestone 11 in the tree, uncommitted)

> **The highest-risk milestone, and where the risk actually went.** The playbook ordered a spike
> because hit testing, ghost rendering, the threshold and GUI-scale correctness were all
> hand-rolled with no framework. Two of those four were retired in advance: hit testing and scale
> live in `BankGridGeometry`, exhaustively tested since Milestone 9 — coordinates arrive
> pre-scaled, and the exclusive edges are what stop a gesture straddling two grids. What remained
> genuinely new was the state machine, and it is a plain class with 23 tests and no client type
> anywhere in it. The spike's question — *can this be made reliable at small scales?* — is
> answered by arithmetic that is identical at every scale, which is a stronger answer than a
> spike's.

---

## 1. What was built

| File | Role |
| --- | --- |
| `client/screen/bank/BankDragController.java` *(new)* | Every decision in the gesture; none of the drawing |
| `BankDragControllerTest` *(new)* | 23 tests — all playbook categories |
| `BankBoxScreen` | Press/move/release routing, Escape ordering, watchdog, visuals |

**No packet, no local removal, no partial stack, no right-click split** — Milestone 12's own
restrictions, all observed. A valid release arms the `HANDOFF` state and the screen immediately
completes it; Milestone 13 replaces that one line with the deposit request and pending flow. The
state exists now so 13 changes behaviour, not shape.

---

## 2. The state machine

```
IDLE → PRESSED_ON_SOURCE → DRAGGING_OVER_INVALID ⇄ DRAGGING_OVER_VALID → HANDOFF → IDLE
```

Every cancellation collapses to `IDLE`. *Cancelled* is an outcome, not a resting state — nothing
observes "cancelled" a frame later; they observe idle.

Decisions encoded and tested:

- **Ineligible stacks refuse the drag.** Design §10.3 lists eligibility as a start condition; the
  playbook offered latitude. Refusing matches the shading — a dimmed stack that will not lift is
  one message; a stack that lifts and then bounces is two.
- **The valid target is the whole bank-grid region**, never a cell (§10.5) — the player requests
  "deposit this", not "place it at slot 14".
- **4 scaled pixels, Euclidean, turns a press into a drag.** Under it, release is a click — and
  inventory clicks still do nothing, consistent with Milestone 11.
- **Exactly one handoff per gesture.** After `DROPPED_ON_BANK`, every further release returns
  `NONE` — the machine half of "a duplicate release must not send a duplicate request", pinned by
  a test that releases three times.
- **Escape belongs to the drag while one is live.** It cancels the gesture and is consumed; the
  screen does not close. With no gesture, §5.2 applies unchanged. And Escape deliberately does
  *not* cancel an armed handoff — by then the gesture is over, and cancelling could race
  Milestone 13's send.

**The source watchdog.** The controller holds an opaque snapshot token (registry id + count,
built by the screen — which is what keeps the controller Minecraft-free). Every frame it compares
the live token; any change — shrink, swap, empty — kills the gesture on the spot. That covers the
playbook's "source slot becoming empty" and every quieter mutation: a hopper, a refresh, a pickup.

**Resize cancels by construction.** The controller is rebuilt with the layout on every `init`,
so there is no resize *case* — a fresh controller is idle because it was just born.

---

## 3. The visuals — four signals, none colour-only

Per design §16: the **source cell** is dimmed and outlined (shape); the **bank grid** gains a
bright border and wash while it is the valid target (shape); the **ghost stack** — the whole live
stack, per §10.2 — rides the cursor with its count numeral (motion); an **invalid hover marks the
ghost with a ✕ glyph** (shape again, not hue).

"The tooltip does not obscure the carried item" is satisfied by absence, not dodging: tooltips are
suppressed entirely while dragging. There is nothing a tooltip could tell the player mid-gesture
that the ghost does not already say.

---

## 4. Tests

```
BankDragControllerTest      23 tests   (new)
Full JUnit suite           573 pass, 0 fail   (was 550)
All GameTests              299 run, 298 pass  (unchanged)
```

Playbook's list, mapped: threshold (under / exactly-at / release-under-is-click), source
identification (armed slot, survives duplicate press, known through handoff), valid hover, invalid
hover, hover following the cursor both ways, the exclusive-edge boundary, release (all four
outcomes), cancellation (Escape consumed / Escape-with-no-gesture passes through / handoff
immune / source changed / source emptied / unchanged ticks quietly), resize (fresh controller),
duplicate mouse events (press during gesture, triple release, stray release).

The one GameTest failure remains the pre-existing world-state bootstrap test.

**Not covered, by decision (Architecture Decision 0):** how the gesture *feels* — ghost offset,
border brightness, whether 4px is right for your mouse. That is this gate.

---

## 5. Milestone report

**Decisions made.** Refuse-don't-bounce for ineligible stacks; whole-region target; 4px threshold;
Escape ordering; handoff immune to Escape; tooltips absent while dragging; snapshot token as a
string so the controller stays Minecraft-free.

**Tests run.** `./gradlew test` — 573 pass. `./gradlew runGameTestServer` — 299 run, 298 pass.

**Unresolved blockers.** None.

**Scope explicitly not performed.** The deposit itself — release over the vault does nothing yet,
by this milestone's own restrictions. Milestone 13 turns the armed handoff into the existing
`BankDepositRequestC2SPayload` and the session's pending flow.

---

## 6. Acceptance gate

> *The drag experience is visually approved before it can mutate banking state.*

That is precisely the current state: the full gesture — press, threshold, ghost, hover feedback,
drop, every cancellation — is live in-game and **cannot move anything**. Drag a stack around,
try to break it, and judge the feel.

**Worth trying deliberately:** dragging over the controls and releasing (cancels); Escape
mid-drag (drag dies, screen stays); picking up a stack and having something else consume it
mid-gesture if you can arrange it (watchdog); and whether the ✕ on invalid hover reads at your
GUI scale.

**Stopping here for owner review.** Milestone 13 — drag-to-deposit — turns the handoff into the
real request, and Milestone 0 §3.3 already established it inherits all three server routes for
free because the packet carries only a slot index.
