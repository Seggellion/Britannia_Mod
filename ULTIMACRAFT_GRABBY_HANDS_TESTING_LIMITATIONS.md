# Grabby Hands — Testing Limitations and Outstanding Live Validation

**Branch:** `grabby-hands`
**Covers:** Milestones 0–13
**Status at time of writing:** 1919 JUnit tests and 364 GameTests, 0 failures, 0 errors

This document exists so that "the tests are green" is never mistaken for "the feature is proven". It records exactly what the automated suite does and does not establish, why, and what a human at a client needs to check.

---

## 1. The environment constraint that used to shape everything — now resolved

**The GameTest server did not boot for milestones 0–11.** `./gradlew runGameTestServer` failed during mixin preparation with `ClassNotFoundException` on `LivingEntityBedMixin`, even though the class compiled correctly. That is why no GameTests were written before M12: shipping test code that cannot be executed is worse than shipping none.

**Fixed in M12.** The `gameTestServer` run configuration in `build.gradle` declared `modSource sourceSets.main`, which the `client` and `server` configurations do not. Removing that one line lets the server boot; the 337 pre-existing GameTests then run and pass. The change affects dev runs only and has no effect on a built jar.

Everything below marked as closed was closed by that unblock. What remains is genuinely client-side: rendering, screens, and human judgement about how things look and sound.

---

## 2. What the automated suite does establish

The suite is stronger than "it compiles", and it is worth being precise about how.

| Technique | What it proves | Where |
|---|---|---|
| **Pure unit tests** | Transaction ordering, policy decisions, provenance encoding, state preservation logic, session validity | the seven `Grabby*TransactionTest` / `*StateTest` / `*RoundTripTest` classes |
| **Fake world and actor seams** | "Exactly once" invariants, race outcomes, and failure paths that are hard to reach in a live game — full inventory, failed removal, failed provenance stamp, object-refused transport | `testsupport/FakeGrabbyWorld`, `testsupport/FakeGrabbyActor` |
| **Real registries under `Bootstrap`** | Tag membership, item classification, `ItemStack` component round trips, NBT persistence, sound-type resolution | `GrabbyAxesTest`, `GrabbyEnrollmentTagTest`, wine / host / container tests |
| **Stream-codec round trips** | Both network payloads encode and decode symmetrically and consume exactly what they wrote | `GrabbyPayloadCodecTest` |
| **Source-scan contract tests** | Architectural rules that cannot be expressed as behaviour: what the code must never reference | `GrabbyAuthorizationRegressionTest`, `GrabbyPreservationContractTest`, `GrabbyWineContractTest`, `GrabbyEnrollmentPreconditionTest` |
| **GameTests against a real level** | Real blocks, real block entities, real players, real NBT save/load, real item drops. This is what closed the container spill, live seating, `setPlacedBy`, and the concurrency guards | `GrabbyPersistenceGameTests`, `GrabbyMultiplayerGameTests` |
| **Mutation testing** | That the guards above actually fail when the thing they protect is broken | performed at each milestone; see §5 |

The source-scan tests deserve a note. Several load-bearing properties of this design are *absences* — Grabby Hands never calls `setBlock`, never touches `BreakEvent`, never reads `placerUuid` for a use decision, never names a wine field. An absence cannot be unit-tested behaviourally, so it is enforced by scanning the sources with comments stripped. Each such test carries a comment naming the real defect it prevents.

**Two source-scan guards were found to be worthless and fixed** (§5a). That is the strongest argument for the mutation discipline: a green source-scan test proves nothing until you have watched it go red.

---

## 3. What is NOT proven — by milestone

### M2 — two-stage pickup audio

| Claim | Automated status | Live check needed |
|---|---|---|
| Grab cue fires when the object leaves the world | Ordering and flags asserted against a fake | The sound is actually audible and reads as physical |
| Stow cue fires only on successful inventory insertion | Asserted, including the withheld case | Audible, and distinct from the grab cue to the ear |
| Cues are distinguishable | Asserted as different sound event ids | **Whether they *sound* different in play** — a judgement no test can make |

### M3 — furniture behaviour

| Claim | Automated status | Live check needed |
|---|---|---|
| Chair places in Adventure mode | **Confirmed live by the owner** | — |
| Placer can sit on a moved chair | **Closed** — `sittingStillWorksAfterAChairHasBeenMoved` picks a chair up, replaces it, and confirms a player still becomes a passenger | — |
| A second player can sit on it | **Closed** — `aSecondPlayerCanSitOnAChairSomebodyElsePlaced` uses a different player from the placer | — |
| Chair-on-chair stacking works | **Closed** — `aChairStacksOnAChair` places one on the other in a real level and confirms both survive | Still worth an eyeball for interpenetration |
| Invalid intersecting placement is rejected | **Closed** — `anObstructedPlacementIsRejectedAndConsumesNothing` asserts the placement fails *and* the item stays in hand | — |
| Facing survives a place/pickup cycle | **Closed** — `facingSurvivesAReload` against a real block state | Still worth an eyeball for orientation-vs-model |
| Worldgen furniture stays immovable | **Closed** — `sceneryStaysImmovableForEveryone` refuses both pickup and axe on a real unmarked chair | — |

### M4 — expanded enrollment

| Claim | Automated status | Live check needed |
|---|---|---|
| Lighting family places and picks up | Enrollment preconditions asserted from the registries | Place and retrieve a sconce, a brazier, a lamp post |
| Wall- and floor-attached variants behave | Not exercised | Whether a free-standing wall sconce looks wrong |
| **Light sources are now player-placeable** | Not a test question | **Gameplay review: this affects mob spawning.** Reversal is one line in `grabby_movable.json` |

### M5 — generic placed-item host

The least validated milestone, because it is the only one that introduced new rendering.

| Claim | Automated status | Live check needed |
|---|---|---|
| Payload survives save/reload exactly | **Closed** — `theHostKeepsItsPayloadAcrossAReload` drives the real block entity's save and load | — |
| Pickup cannot double-drop | Asserted via the detach step, mutation-tested | — |
| **The host renders the stored item** | **Not exercised at all** | Does the item appear? At a sensible size? Lying flat? |
| **The invisible block produces no missing-model warnings** | Not exercised | Watch the client log on first placement |
| **The empty-elements model behaves** | Not exercised | Check for render artefacts and particle behaviour |
| Compact collision feels right | Not exercised | Walk into a placed cheese |

### M6 — wine bottle

| Claim | Automated status | Live check needed |
|---|---|---|
| All six `WineData` fields survive round trips | Asserted repeatedly against the real preservation helper | — |
| Custom names and arbitrary components survive | Asserted; mutation-tested | Name a bottle, place it, pick it up, confirm the name |
| Worldgen bottles still rebuild correctly | Asserted | — |
| The in-world state transfer | **Closed** — `aWineBottlePlacedThroughItsOwnPathRecordsWhatItWasPlacedFrom` runs the real `setPlacedBy` and `getCloneItemStack` against a live level, and `aWineBottleKeepsAllSixFieldsAndItsOriginStackAcrossAReload` survives a real reload | The full five-step cycle driven by a human is still worth doing once |
| `LABEL` blockstate maps from `labelColor` | Not exercised | Place a bottle of each label colour and compare models |
| Label text renderer shows the right data | Not exercised | Look at a placed bottle |
| Another player can inspect a placed bottle | Enforced as an absence: no ownership check in the wine path | Two clients, one bottle |

### M7 — portable containers

| Claim | Automated status | Live check needed |
|---|---|---|
| Contents survive transport exactly, slots and item data included | Asserted against the real `ContainerHelper` format, repeated cycles | — |
| Pickup cannot double-drop the inventory | **Closed** — `pickingUpAFilledContainerLeavesNothingOnTheFloor` counts real item entities after a real pickup | — |
| A refused pickup never empties a container | Asserted | Fill your inventory, then try to pick up a full chest |
| Failed removal restores the contents | Asserted against a fake | Unreachable in practice; no live check meaningful |
| Lock id, locked flag, difficulty and `ChestKeySeeded` all survive | **Closed for the writer** — `aContainerCarriesItsWholeStateIntoAPortableItem` runs the real `writePortableState` on a real locked chest and checks all four fields plus the absence of carried provenance | **The restore half still needs the live key check: lock, note key, pick up, replace, right-click — expect no second key** |
| **`updateCustomBlockEntityTag` actually restores contents inside a live `BlockItem.place`** | **Still not exercised.** The writer is now run for real (`aContainerCarriesItsWholeStateIntoAPortableItem`), but vanilla's restore during placement is not | Place a filled chest and open it |
| **The lockable chest's constructor seeds a key that is then overwritten by the load** | **Not exercised** — reasoned from source | Same check as the key test above |
| Viewer refusal uses the real `ContainerOpenersCounter` | Only the refusal *plumbing* is asserted; the opener count is not | Have a second player open a chest, then try to pick it up |
| Nesting refusal | Plumbing asserted; the `BLOCK_ENTITY_DATA` inspection is not exercised on a real nested chest | Put a filled chest inside a chest, then try to pick up the outer one |
| A placed container still opens its normal menu | Not exercised | Place a chest and open it |

### M8 — axe destruction and the R-2.11 confirmation

The largest untested surface in the epic, because it introduced networking and a screen.

| Claim | Automated status | Live check needed |
|---|---|---|
| Destroys exactly once; second swing does nothing | Asserted | — |
| Only a recognised axe reaches the path | Asserted for vanilla axes and non-axes; `TwoHandedAxeItem` covered structurally | Swing the two-handed axe at a placed chair |
| Scenery with no provenance is refused | Asserted | Swing at a Britannia scenery chair — expect nothing |
| Policy, reach and object refusal apply | Asserted | — |
| Container contents spill exactly once and the container does not also drop | **Closed** — `destroyingAFilledContainerSpillsItsContentsExactlyOnce` destroys a real filled chest and counts the real item entities: five ingots, two bread, zero chests | — |
| A host's payload is consumed rather than handed back | Asserted; mutation-tested | Destroy a placed cheese — expect no cheese on the ground |
| Destruction feedback fires once, after commit | Asserted through the seam | Hear and see it |
| Wood, glass and metal sound different | Asserted at `SoundType` level | Chop a chair, a bottle and a sconce; listen |
| Axe loses one durability; Creative exempt | Asserted | — |
| Session: replay, wrong player, expiry, changed block, swapped axe all rejected | Asserted directly | — |
| One outstanding question per player | Asserted | — |
| Payload codecs round-trip | Asserted | — |
| **The prompt screen renders** | **Never run** | Does it appear? Is it readable? Does the paper background suit it? |
| **`renderDialogue` handles the blank-NPC case** | **Never run** — read from source as skipping the portrait | Look for a missing-portrait artefact |
| **Esc / closing without answering** | Not exercised | Press Esc; expect nothing destroyed |
| **The lang keys read well** | Never seen | Check both the plain and the with-contents wording |
| **Payload registration and dispatch** | Codecs tested; the registrar wiring and `PacketDistributor` send are not | Any successful prompt proves this |
| **Axe mode suppresses sitting** | Handler ordering asserted by source scan | Hold an axe, right-click a chair — expect the prompt, not a seat |

### M13 — the Adventure gate itself

`GrabbyAdventureGameTests` closed the parts of the live script a machine can decide.

| Claim | Automated status | Live check needed |
|---|---|---|
| The four enrollment tags actually load and bind at runtime | **Closed** — `theEnrollmentTagsActuallyLoadAndBind`, `theLooseItemTagActuallyLoadsAndBinds`. Previously the tag JSON was only read as data; now it is proven to reach `BlockState.is` | — |
| An ordinary block still cannot be placed in Adventure mode | **Closed** — `anOrdinaryBlockStillCannotBePlacedInAdventureMode` | — |
| Enrollment does **not** grant ordinary building rights | **Closed** — `anEnrolledChairIsAlsoStillRefusedByTheOrdinaryPlacementPath` puts an *enrolled* chair through the vanilla path and confirms it is still refused | — |
| An enrolled chair places in Adventure mode, is stamped `PLAYER` with the placer UUID, and consumes exactly one item | **Closed** — `anEnrolledChairCanBePlacedInAdventureModeThroughGrabbyHands` | — |
| An unenrolled block is refused by Grabby Hands too | **Closed** — `anUnenrolledBlockIsRefusedByGrabbyHandsToo` | — |
| Support rules still apply to placement | **Closed** — `aWineBottleNeedsSomethingSturdyUnderneath` | — |
| **Audio quality, screen rendering, orientation-vs-model, and two-client behaviour** | Unchanged from above — **still human-only** | The 12 steps in the M13 acceptance script |

One test-authoring note worth recording, because it hid a real assertion: the mock player initially
stood *in* the destination block, so three tests failed with `REFUSED_BY_BLOCK` for the wrong reason —
and the obstruction test had been passing for the wrong reason too. Standing the player beside the
target fixed both. A second mock-player artefact: `makeMockServerPlayerInLevel` grants `instabuild`,
and `ItemStack.consume` skips shrinking for a player with infinite materials, so the
item-consumption invariant was silently unverifiable until `instabuild` was cleared as well as
`mayBuild`. Both are the same lesson as §5a — a green test proves nothing until you have watched it
go red for the right reason.

---

## 4. Cross-cutting gaps

- **Two-player behaviour is now exercised with real server players**, but they are GameTest mock players in one process, not two connected clients. Latency, packet ordering and client prediction are still untested.
- **Persistence is exercised through the real block entity save/load pair**, which is what a chunk unload and reload does. An actual region-file write and a server restart are still untested.
- **No client-side rendering has ever run.** The placed-item host renderer, the wine label renderer, the destruction confirmation screen, blockstate/model resolution, destruction particles, and particle behaviour for the invisible host block.
- **No network round trip has run end to end.** Codecs are tested in isolation; registration, dispatch and client handling are not.
- **`GrabbyActor.hasRoomFor`, reach validation and `damageTool` now run** against real players inside the GameTests, since every transaction there goes through the live `GrabbyActor`. None is asserted directly, so they are exercised rather than verified.

---

## 5. Mutation testing performed

Guards were deliberately broken and confirmed to fail, then reverted. This is what distinguishes an enforced rule from a decorative one.

| Milestone | Mutation | Test that caught it |
|---|---|---|
| M1 | Added a class calling `setGameMode(GameType.SURVIVAL)` inside `grabbyhands` | `noGrabbyHandsClassTouchesTheBreakPipelineOrGameModeArbitration` |
| M2 | Removed the concurrency guard claim | `aCompetingTransactionArrivingMidFlightLosesAndChangesNothing` |
| M2 | Moved the inventory room check to after block removal | `aFullInventoryLeavesTheWorldObjectIntactAndPlaysNothing` |
| M3 | Removed the enrollment check from placement | `aNonEnrolledItemIsLeftEntirelyAloneSoAdventureModeStillRefusesIt` |
| M3 | Made a failed provenance stamp report success | `aBlockThatCannotCarryProvenanceIsLeftProtectedRatherThanSilentlyMovable` |
| M4 | Enrolled a repositioning item and a block with no provenance-capable entity | four precondition tests |
| M5 | Removed the payload detach step | `pickingUpAHostDetachesThePayloadBeforeRemovalSoItCannotAlsoDrop` |
| M6 | Made pickup ignore the recorded origin stack | `aCustomNameNoLongerDisappearsAcrossACycle`, `componentsNobodyHasThoughtOfSurviveToo` |
| M7 | Container detach no longer empties the container | `theContainerSpillHazardIsStillPresentAndStillAnswered` *(after §5a)* |
| M7 | Ignored the object's own transport refusal | both refusal tests |
| M7 | Dropped lock state from transport, carrying only contents | `theContainerSpillHazardIsStillPresentAndStillAnswered` |
| M8 | Host payload no longer consumed on destruction | `aPlacedItemHostsPayloadIsConsumedRatherThanHandedBack` |
| M8 | Removed the axe check | `GrabbyDestructionTransactionTest` |
| M8 | Made the confirmation session reusable | `GrabbyDestructionConfirmationTest` |
| M10 | Allowed a locked chest to be chopped open | `aLockedContainerCannotBeChoppedOpen` |
| M11 | Enrolled a deed-placed fixture; dropped an eligible block from enrollment | the precondition and completeness guards |

### 5a. Two guards that were worthless until mutation testing exposed them

Both were green, both proved nothing, and neither would have been noticed without deliberately breaking the code they claimed to protect.

1. **M7 — `clearContent()` scan.** The assertion checked whether the *file* contained `clearContent()`. It did, because `Container` declares that method a few lines away. Removing the actual call from `detachForTransport` — reintroducing the exact inventory-duplication bug — left the suite green. Fixed by adding a brace-matching `methodBody()` helper so the assertion scans the specific method.

2. **M8 — `outstandingSessions()` helper.** A probe written to count live sessions always returned `0`, so two assertions passed regardless of behaviour. Caught while writing it; fixed by moving the test into the `destruction` package where it can call the real package-private counter.

**Lesson worth carrying forward:** a substring check against a whole file can pass for the wrong reason, and a test helper can be quietly inert. Every source-scan guard added from here should be watched going red at least once.

---

## 6. Suggested first live session

Most of the behavioural list is now covered by GameTests. What is left is what only a human at a client can judge: whether things **look** and **sound** right.

In rough order of value:

1. **Place a cheese** — the host's first ever render. Check size, orientation, whether it lies flat, and the client log for missing-model warnings.
2. **Place a lute** — the item most likely to look wrong, since instruments are long and thin and the host lays items horizontally at half scale.
3. **Hold an axe and right-click a placed chair** — the confirmation screen's first ever render. Is it readable? Does the paper background suit it? Does the blank-NPC layout leave a gap where a portrait would be?
4. Press Esc on that prompt, then confirm one — check the wording of both the plain and the with-contents body text.
5. Pick a chair up and listen: are the grab and stow cues **audibly** different, and does the grab read as physical rather than as a UI click?
6. Chop a chair, a wine bottle and a metal sconce — do wood, glass and metal sound distinct?
7. Place a wine bottle of each label colour and confirm the models differ correctly.
8. Look at a placed wine bottle and check the floating label text.
9. Place a filled chest, then **open it** — this is the one behavioural gap GameTests did not close, because vanilla's restore runs inside `BlockItem.place`.
10. **Lock a chest, note the key, pick it up, replace it, right-click** — expect no second key and the original still fitting.
11. Walk into a placed cheese and a placed chair: does the collision feel right?
12. Place a wall sconce free-standing — does it look wrong enough to matter?

Items 1, 2, 3 and 9 are the highest value: the first three have never rendered, and 9 is the last uncovered behavioural path.

**This list is now folded into the formal M13 script.** `ULTIMACRAFT_GRABBY_HANDS_MILESTONE_13_ACCEPTANCE.md`
walks all 23 playbook steps in order, marks the 11 that automation closed, and leaves blanks for the
12 that need a human. Use that document for the acceptance pass; this section remains as the
rationale for why those particular items are worth the most attention.

---

## 7. Honest summary

As of M13 the suite covers transaction atomicity, ordering invariants, policy decisions, state preservation, persistence through the real block entity save/load pair, session validity, payload codecs, architectural rules expressed as absences — and now real-level behaviour: container spill, live seating including by a second player, the wine bottle's own `setPlacedBy`, concurrency guards, provenance surviving a reload, and — as of M13 — the Adventure gate itself: that the enrollment tags really bind at runtime, that an enrolled block is *still* refused by the ordinary placement path, and that the Grabby path places one and consumes exactly one.

It has been mutation-tested rather than merely written, and that process caught two guards that were doing nothing.

What it still does **not** cover:

- **any rendering at all** — the placed-item host, the wine label, the destruction screen, block models, particles;
- **two genuinely connected clients** — the multiplayer tests use mock players in one process, so latency, packet ordering and client prediction are untested;
- **a real region-file write or server restart**;
- **vanilla's `updateCustomBlockEntityTag` restoring container contents during placement**, which remains the last uncovered behavioural path;
- **human judgement** — whether the cues sound different, whether the prompt reads well, whether a lute lying flat looks like decoration or like a bug.

The remaining risk is concentrated in the client, which is exactly where it should be by this point.
