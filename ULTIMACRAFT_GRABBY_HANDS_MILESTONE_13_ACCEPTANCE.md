# Grabby Hands — Milestone 13 Live Acceptance Script

**Branch:** `grabby-hands`
**Base commit:** `50061f07` (all Grabby Hands work is uncommitted working-tree state on top of it — see §6)
**Prepared:** 2026-08-13
**Gate:** M13 cannot be closed by automation. It needs a human at a client, ideally two.

> **CLOSED — 2026-08-13.** The owner ran the live pass and approved all 23 steps. One finding, not a
> defect: the pickup gesture is **Shift + right-click with both hands empty**, and the owner first
> reached for left-click. Left-click is intentionally inert (Grabby Hands never touches the break
> pipeline), so the mechanic is correct — but the gesture is not self-evident to a new player. See
> §5 item 6 for the cheap mitigation if it recurs on the live server.

---

## 0. What this document is

The playbook lists 23 live steps. Since M12 unblocked the GameTest server, **11 of those 23 are now
closed automatically** and the remaining 12 are the ones that genuinely need eyes, ears, and two
humans. This script gives you, per step:

- what is already proven and by which test, so you don't re-verify machine-checkable things by hand;
- exactly what to do;
- exactly what counts as a pass;
- a blank for the result.

Steps marked **AUTO-CLOSED** still deserve a quick sanity glance, but a failure there is a test bug
as much as a game bug. Steps marked **HUMAN-ONLY** are the real gate.

---

## 1. Evidence pre-filled

### Build and test outputs

| Command | Result |
|---|---|
| `./gradlew build` | BUILD SUCCESSFUL |
| `./gradlew test` | **1919 tests, 0 failures, 0 errors, 17 skipped** across 222 classes (2m 17s) |
| `./gradlew runGameTestServer` | **All 364 required tests passed** |

Of the 1919 JUnit tests, **240 are Grabby Hands tests** across 21 classes. Of the 364 GameTests,
**27 are Grabby Hands tests** (337 were the pre-existing baseline, all still passing).

### Grabby Hands GameTests, by file

`GrabbyPersistenceGameTests` (10)
: `provenanceSurvivesAReload`, `anUnmarkedBlockStillReadsAsProtectedAfterAReload`,
`facingSurvivesAReload`, `aWineBottleKeepsAllSixFieldsAndItsOriginStackAcrossAReload`,
`aWineBottlePlacedThroughItsOwnPathRecordsWhatItWasPlacedFrom`,
`aFilledContainerKeepsItsContentsAcrossAReload`, `aContainerCarriesItsWholeStateIntoAPortableItem`,
`aLockedChestReportsItselfAsSecuredAgainstDestruction`, `theHostKeepsItsPayloadAcrossAReload`,
`theHostsCloneStackIsTheItemItHolds`

`GrabbyMultiplayerGameTests` (8)
: `twoPlayersCannotBothPickUpTheSameObject`, `anObjectAlreadyInATransactionRefusesASecondOne`,
`aPickupAndAnAxeCannotBothConsumeTheSameObject`, `destroyingAFilledContainerSpillsItsContentsExactlyOnce`,
`pickingUpAFilledContainerLeavesNothingOnTheFloor`, `aSecondPlayerCanSitOnAChairSomebodyElsePlaced`,
`sittingStillWorksAfterAChairHasBeenMoved`, `sceneryStaysImmovableForEveryone`

`GrabbyAdventureGameTests` (9)
: `theEnrollmentTagsActuallyLoadAndBind`, `theLooseItemTagActuallyLoadsAndBinds`,
`anOrdinaryBlockStillCannotBePlacedInAdventureMode`,
`anEnrolledChairIsAlsoStillRefusedByTheOrdinaryPlacementPath`,
`anEnrolledChairCanBePlacedInAdventureModeThroughGrabbyHands`,
`anUnenrolledBlockIsRefusedByGrabbyHandsToo`, `aChairStacksOnAChair`,
`anObstructedPlacementIsRejectedAndConsumesNothing`, `aWineBottleNeedsSomethingSturdyUnderneath`

### Exact enrolled objects

**Movable and axe-destroyable (32, identical sets)** — `data/britannia_mod/tags/block/grabby_movable.json`,
`grabby_axe_destroyable.json`:

seating and tables (12): `wooden_chair`, `straw_chair`, `chair_trinsic`, `chair_vesper`, `stool`,
`footstool`, `bench`, `wooden_throne`, `magincia_style_throne`, `yew_table`, `small_table`, `counter`
· wine (4): `wine_bottle_green`, `wine_bottle_brown`, `wine_bottle_blue`, `wine_bottle_clear`
· lighting (8): `candle`, `candelabra_small`, `brazier_small`, `torch_standing`, `wall_sconce`,
`torch_wall`, `lamp_post_regular`, `lamp_post_fancy`
· containers (8): `chest_wooden`, `chest_metal`, `chest_metal_bronze`, `armoire_brown`, `armoire_red`,
`chest_of_drawers_brown`, `chest_of_drawers_red`, `britannia_lockable_chest`

**Deed-placed, permanently excluded (6)** — `grabby_deed_placed.json`: `double_bed` and the five
chandeliers.

**Loose items placeable on the host block (30)** — `grabby_placeable_items.json`.

**Deliberately excluded and worth spot-checking:** trash barrel (owner decision), `candelabra_tall`
and `villa_lamp_post` (both `RaisedBlockItem`, which mis-places silently — see M4 report).

### Exact wine fields verified

`WineData(String wineryName, String grapeType, int year, int quality, String region, String labelColor)`
— all six, plus the whole origin `ItemStack`, asserted across a real save/load in
`aWineBottleKeepsAllSixFieldsAndItsOriginStackAcrossAReload`.

---

## 2. Setup

```bash
./gradlew runServer
```

Join with two clients if you can; the multiplayer steps say where the second is required. Put both
players in **Adventure mode** (`/gamemode adventure`), outside any structure you own unless a step
says otherwise, and give yourself the test objects.

### The gestures — read this before starting

| Intent | Gesture |
|---|---|
| **Place** | Right-click a surface holding the enrolled item. Ordinary Adventure placement rules are untouched; this is a separate path. |
| **Pick up** | **Sneak + right-click** the object with **both hands empty**. Both empty is deliberate — an occupied offhand would make the same gesture mean different things. |
| **Use normally** | Right-click with anything in hand, or without sneaking. Sitting, opening, drinking are unchanged. |
| **Destroy** | Right-click the object holding **any recognized axe**. A confirmation screen appears. This is a right-click, **not** a left-click swing — Grabby Hands never touches the break pipeline. |
| **Stay out of the way** | Holding the Interior Decorator tool in either hand suppresses all Grabby gestures. |

If you are holding the axe, you get the destroy prompt instead of the normal interaction (R-2.11).
The prompt reads **"Destroy the X? This cannot be undone."** with **"Yes, destroy it"** / **"No, leave it"**,
and for containers it names the occupied slot count.

---

## 3. The 23 steps

### Step 1 — Join in Adventure mode
**HUMAN-ONLY.** Confirm `/gamemode adventure` took and the HUD shows no reach-block outline.
Note also that Grabby Hands never changes your game mode; if you observe a mode flip at any point in
this script, that is a defect and invariant #1 is broken.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 2 — Ordinary disallowed placement is still blocked
**AUTO-CLOSED** — `anOrdinaryBlockStillCannotBePlacedInAdventureMode` and
`anEnrolledChairIsAlsoStillRefusedByTheOrdinaryPlacementPath` both assert refusal through the vanilla
path, the second proving that enrollment does **not** grant ordinary building rights.

Sanity glance: try to place a cobblestone block. It must fail.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 3 — Place an enrolled chair
**AUTO-CLOSED** for the mechanism (`anEnrolledChairCanBePlacedInAdventureModeThroughGrabbyHands`
asserts the block appears, provenance is stamped `PLAYER` with the placer UUID, and the source item
is consumed exactly once) and **previously confirmed live by the owner at M3**.

Glance: the chair appears facing you, the item leaves your hand.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 4 — Sit on it
**AUTO-CLOSED** — `sittingStillWorksAfterAChairHasBeenMoved` picks a chair up, replaces it, and
confirms a player still becomes a passenger.

Glance: the sit animation and camera height look right, and dismount works.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 5 — Second player sits on / uses it
**AUTO-CLOSED** — `aSecondPlayerCanSitOnAChairSomebodyElsePlaced` uses a player who is *not* the
placer, proving placement provenance did not become an interaction ACL (invariant #7).

**Do this one live anyway.** It is the single most important social behaviour in the epic.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 6 — Pick it up with the Grabby gesture
**HUMAN-ONLY** for feel. Sneak + right-click, both hands empty.
Pass: chair vanishes, one chair item enters your inventory, nothing drops on the floor.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 7 — Grab / lift sound
**HUMAN-ONLY — this is a judgement no test can make.**
The grab cue is `ITEM_FRAME_REMOVE_ITEM`, played at the block position the instant the object leaves
the world.
Pass: audible, and reads as something being physically lifted.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 8 — Inventory stow sound
**HUMAN-ONLY.** The stow cue is `ITEM_PICKUP`, played to you personally, and **only** on successful
insertion.
Pass: it is clearly a *second*, distinct event — not a single blurred noise with step 7.
Also verify the negative: fill your inventory completely, then pick up a chair. It should drop at
your feet with the grab cue **and no stow cue**.

Result: ☐ pass ☐ fail — notes: ________________________________________
Distinctness of the two cues: ☐ clearly distinct ☐ muddled — notes: __________________

---

### Step 9 — Place it again
**HUMAN-ONLY** for feel; mechanism covered by step 3.
Pass: facing is chosen from where you stand, not preserved blindly from before.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 10 — Supported furniture stacking
**AUTO-CLOSED** — `aChairStacksOnAChair` places a chair on a chair and confirms both survive.

Live: stack a chair on a chair, and a candle on a table. Pass: no visual interpenetration, and the
upper object can still be picked up independently.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 11 — Invalid intersection is rejected
**AUTO-CLOSED** — `anObstructedPlacementIsRejectedAndConsumesNothing` asserts both halves: the
placement fails **and** the item stays in hand.

Live: stand a chair into a wall or into another player. Pass: nothing is placed **and nothing is
consumed**. Losing the item on a failed placement is a defect.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 12 — Place a non-default stateful wine bottle
**HUMAN-ONLY.** Use a bottle with all six fields set to non-default values — write them down here
before you place it:

| field | value before placing |
|---|---|
| wineryName | ______________ |
| grapeType | ______________ |
| year | ______________ |
| quality | ______________ |
| region | ______________ |
| labelColor | ______________ |

Pass: it places, and the label/colour renders as the item did.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 13 — Other player uses / inspects it per existing wine behaviour
**HUMAN-ONLY.** Grabby Hands must not have changed drinking, inspection, or tooltips at all.
Pass: identical to a wine bottle that was never touched by Grabby Hands.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 14 — Pick it up and verify exact state
**AUTO-CLOSED for persistence** (`aWineBottleKeepsAllSixFieldsAndItsOriginStackAcrossAReload`), but
**verify by eye anyway** — this was M0 risk #4, a real state-loss bug that existed and was fixed.
Compare every field against your table in step 12.

Fields matching: ☐ all six ☐ mismatch: ________________________________________

---

### Step 15 — Place and pick up again, re-verify
**HUMAN-ONLY.** The second cycle is the one that catches "state survives once, then degrades".
Pass: identical to step 14. Custom name and any stack components also survive.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 16 — Place a filled container
**HUMAN-ONLY.** Put several distinguishable stacks in a `chest_wooden` first. Note contents:
________________________________________

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 17 — Another player opens it where rules permit
**HUMAN-ONLY.** Pass: existing container rules decide, unchanged. A `britannia_lockable_chest` must
still respect its lock for the other player.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 18 — Pick up and replace the filled container, verify contents
**AUTO-CLOSED** — `aFilledContainerKeepsItsContentsAcrossAReload`,
`aContainerCarriesItsWholeStateIntoAPortableItem`, and `pickingUpAFilledContainerLeavesNothingOnTheFloor`
together cover contents preservation and the double-drop hazard.

Live, check three things: contents identical; **nothing spilled on the floor during pickup**; and
the item in your inventory is not a "fresh" empty chest.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 19 — Axe-destroy wooden furniture, confirm sound
**HUMAN-ONLY.** Right-click a placed chair with any axe → confirm the prompt appears → choose
**"Yes, destroy it"**.
Pass: prompt text is correct and readable; the chair is consumed (**no intact item returned**,
invariant #15); the destruction sound is the block's own break sound (`SoundType.WOOD` — this was
explicitly fixed in M7, so wooden furniture must sound wooden, not stony).

Also test **"No, leave it"**: the object must survive untouched.

Result: ☐ pass ☐ fail — notes: ________________________________________
Sound reads as wood: ☐ yes ☐ no

---

### Step 20 — Axe-destroy a wine bottle, confirm sound
**HUMAN-ONLY.** Pass: appropriate break sound for glass/whatever the block declares, and the bottle
with all its state is destroyed rather than returned.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

### Step 21 — Axe-destroy a filled container, contents spill exactly once
**AUTO-CLOSED** — `destroyingAFilledContainerSpillsItsContentsExactlyOnce` counts the item entities.

Live: the prompt should name the occupied slot count ("Its contents (N occupied slots) will spill…").
Pass: N matches, contents hit the ground **once**, and the container itself is not also returned.

Slot count shown: ______  Actual: ______  Duplicates seen: ☐ none ☐ yes

---

### Step 22 — Permanent static decoration rejects pickup and destruction
**AUTO-CLOSED** — `sceneryStaysImmovableForEveryone` and
`anUnmarkedBlockStillReadsAsProtectedAfterAReload` prove the fail-closed default: no `GrabbyState`
tag decodes to `WORLD`, so **every pre-existing object in every existing save is protected with no
migration**.

Live, this is the most important protection check in the epic. Try, in a town you did not build:

- pick up a chair that was placed by the map, not by you → must refuse;
- axe it → must refuse;
- try the same on a bed or a chandelier you placed **yourself** → must refuse (deed-placed exclusion);
- try to axe a **locked** chest → must refuse *and fall through to lockpicking*, not to destruction.

Result: ☐ all four refuse ☐ something got through: ________________________________________

---

### Step 23 — Save, restart, rejoin, verify persisted objects
**AUTO-CLOSED** — the whole `GrabbyPersistenceGameTests` file exercises real NBT save/load.

Live: leave one of each in the world — chair, wine bottle with known state, filled chest, a loose
item on a host block — then stop the server, restart, and rejoin.
Pass: all four present, wine state intact, chest contents intact, host still rendering its payload,
and all four still pick-up-able by you.

Result: ☐ pass ☐ fail — notes: ________________________________________

---

## 4. Defect log

Record anything that fails above. A defect in any of these categories blocks Gate M13:
state loss, duplication, usability, seating, stacking, audio, Adventure-permission.

| # | Step | Category | What happened | Repro |
|---|---|---|---|---|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |

---

## 5. Known limitations carried into this pass

Full detail in `ULTIMACRAFT_GRABBY_HANDS_TESTING_LIMITATIONS.md`. In brief:

1. **Audio quality is unjudgeable by automation.** Tests assert two *different* sound event ids fire
   in the right order; whether they *sound* different is steps 7–8.
2. **Rendering is unjudgeable by automation.** The host block renderer, the confirmation screen
   layout, and furniture orientation-vs-model all need eyes.
3. **Two source-scan guards were found worthless and fixed** during the epic. Every architectural
   guard has since been mutation-tested, but source-scanning remains a weaker technique than
   behavioural assertion.
4. **Two open questions remain non-blocking:** OQ-3 (container nesting depth) and OQ-5 (city
   container destruction policy).
5. **M11 loose-item pickup-rights defaults were assumed, not confirmed.** Anyone may pick up a loose
   item another player placed on a host block. If step 5's philosophy (placement is not an ACL) is
   right, this is consistent — but it is an assumption, and this pass is the moment to accept or
   reject it.
6. **Gesture discoverability.** The pickup gesture is not self-evident — the owner reached for
   left-click first during this pass. No mechanic change is needed; if it recurs with other players,
   a tooltip line on the 32 enrolled blocks plus a one-shot actionbar hint is the cheap fix.

---

## 6. Repository state — please read

All Grabby Hands work is **uncommitted working-tree state** on top of `50061f07`, spanning 15
modified files and ~50 new ones. During M10 a careless multi-file refactor destroyed an untracked
test file that had no git fallback; it had to be reconstructed from context.

Committing to the local `grabby-hands` branch would remove that whole class of risk. I have not done
so, since committing was never requested. When you want it:

```bash
git add -A ':!gradle/wrapper/gradle-wrapper.jar' && git commit -m "Grabby Hands M0-M13"
```

`gradle/wrapper/gradle-wrapper.jar` shows as modified because this worktree needed the real 43 KB jar
in place of a 133-byte LFS pointer. **It must never be staged.**

No push, merge, tag, or deploy has been performed or is proposed.

---

## 7. Gate M13 sign-off

> Close only when the owner-facing behavior matches the design and there are no unresolved
> state-loss, duplication, usability, seating, stacking, audio, or Adventure-permission defects.

Automated portion: **complete and green** — 1919 JUnit / 364 GameTests / 0 failures.
Live portion: **complete** — all 23 steps run at a client.

Owner: **Seggellion**  Date: **2026-08-13**  ☑ **M13 CLOSED** ☐ defects logged, M13 held open

No state-loss, duplication, usability, seating, stacking, audio, or Adventure-permission defects
were found. Defect log §4 is empty.
