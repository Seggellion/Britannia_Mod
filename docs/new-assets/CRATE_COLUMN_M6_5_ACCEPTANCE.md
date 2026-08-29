# Crate Column M6.5 — Live Destruction Acceptance

## 1. Overall verdict

```text
FAIL — crate destruction still has a production blocker
```

The blocker is **the acceptance itself, not a defect found in the code.**

M6.5 is defined by evidence that can only come from a running Minecraft client: what the screen
shows in the frames between a break completing, the prediction acknowledgement arriving, and the
next-tick layout update landing; what a held mouse button does; whether Iris + Photon shades a
repacked column correctly. I have no client and cannot observe any of it. Every acceptance criterion
in Parts 4–7 and 10–20 is therefore **unobserved**, and the gate cannot be marked PASS on reasoning
alone — that is exactly the soft green the brief forbids.

Everything reachable without a client was done, and is reported below. The live checklist in §24 is
the remaining work; it is a session at a client, not an engineering task.

## 2. M6 commit identity

```text
9582cd3475cdaf578292fe8f10b08caf8791e3a8
feat(crates): break one crate out of a compact column
2026-08-28T21:54:21-07:00
```

M6 was already committed with a clean tree. Nothing had been left behind, so no reconstruction
commit was needed. Part 1 is resolved.

## 3. Test environment

| | |
|---|---|
| Client/server arrangement | **None.** No Minecraft client is available in this environment. |
| Automated environment | Dedicated `GameTestServer` (headless) and JUnit, both on the mod's own dedicated-server code path |
| Game mode | Creative and Survival, both exercised in automated tests |
| Allowed build area | Not applicable — no live session |
| Shader state | Not applicable — no live session |
| Latency | Not applicable — no live session |

## 4. Bottom break

**Not observed live.** Automated coverage: a swing arriving as a real `START_DESTROY_BLOCK` packet at
the bottom crate removes exactly that crate, leaves the two above it, repacks them down to the floor,
and preserves their contents.

## 5. Middle break

**Not observed live.** Automated coverage: a swing aimed at 10.00 voxels — inside the middle crate of
three smalls, which spans 7.15–14.30 — destroys the middle crate and only the middle crate, and the
top crate keeps its items.

## 6. Top / continuation break

**Not observed live.** Automated coverage: a swing aimed at 18.00 voxels, whose clicked cell is the
continuation cell above the root, destroys the top crate, and the continuation cell is released back
to air by the same transaction.

## 7. Root-survival prediction

**Not observed live.** This is the case the brief calls the most important, and it is the one I am
least able to substitute for: it is a question about frames, not about state. The end state is
covered — the root stays `crate_stack` with the survivors repacked — but *what the player sees while
it settles* is unmeasured.

## 8. Prediction/update ordering

`notifyClientsNextTick` is **retained** — Option A.

What changed in M6.5 is the standing of the claim underneath it. M6 justified the one-tick delay by
protocol reasoning; it is now traced through `MinecraftServer` source, so it is a checked fact about
this NeoForge version rather than an inference:

```text
tickServer()
  this.tickCount++                       // tick N begins
  tickChildren(...)
    ... getConnection().tick()           // MinecraftServer:1051 — flushes the outgoing queue,
                                         //   including ClientboundBlockChangedAckPacket
  ...
waitUntilNextTick()                      // MinecraftServer:712 — polls pending TickTasks
```

A `TickTask` scheduled for tick N+1 is polled by `waitUntilNextTick`, which runs *after*
`tickChildren` has already flushed that tick's packets. So the acknowledgement that ends the client's
prediction is written to the connection strictly before the layout update the task sends. The
ordering the design depends on is a property of the tick loop, not a race.

This is a stronger justification than M6 had, and it is still not the live observation Part 7 asks
for. Source ordering says the packets leave the server in the right order; it does not prove the
client's own snapshot-restore path leaves nothing visible behind between them.

## 9. Held-left-click behavior

**Not observed live.**

```text
guard = 4 ticks retained
```

Retained, but note honestly *why*: the brief asks for the smallest measured safe value, and I could
not measure. Four ticks is unchanged from M6's protocol reasoning. M6.5 did add end-to-end automated
proof that the guard is actually reached by a real packet — a second `START` arriving immediately
after a completed break captures no target, so the destruction that follows it does nothing. That
proves the mechanism works; it does not prove `4` is the right number for a human holding a mouse.

## 10. Final crate

**Not observed live.** Automated coverage from M6: breaking the last logical crate removes the root
block and every continuation cell, drops the crate item and its contents, and never yields a raw
`crate_stack` item.

## 11. Inventory/drop integrity

**Not observed live.** Automated coverage: the targeted crate's contents drop exactly once, at that
crate's packed centre rather than the column root; survivor inventories are asserted item-by-item
after the repack.

## 12. Persistence

**Not observed live.** Automated coverage: round-trip NBT tests over a mutated column.

## 13. Protection

**Not observed live.** Indirect evidence exists and is worth stating precisely, because it came from
a failure rather than a test: M6's break tests failed until the fixture player was moved to Creative,
because `StructureProtectionHandler` refused the bare-handed Survival break outside a house, and
`WoodChopEventHandler` cancelled every break made with a `two_handed_axe`. The crate path did not
bypass either. That is real evidence that the deny half works; the allow half — inside build rights —
is untested.

## 14. Creative behavior

**Not observed live.** Automated coverage is strongest here: the packet-path tests run Creative, where
destruction completes inside the same `START` action, which is the sharpest available test of whether
the target is captured early enough to still be there when destruction runs.

## 15. Multiplayer/menu concurrency

```text
AUTOMATED ONLY
```

## 16. Iris + Photon

```text
NOT AVAILABLE
```

Nothing was inspected. No client, no shader pack.

## 17. Mixed/tall stack rendering after repack

**Not observed live.** Geometry after repack is asserted numerically (exact contact, no gaps,
including the medium crate's authored `minY` correction), but nothing rendered it.

## 18. Code changes from live testing

No live testing occurred, so nothing here was driven by an observed defect. Three changes were made,
each with its own reason:

| File | Change | Why |
|---|---|---|
| `event/CrateStackBreakHandler.java` | Server aim raycast now uses partial tick `1.0F` instead of `0.0F` | At `0.0F`, `Entity.pick` interpolates from the *previous* tick's rotation, so the agreement check judged the swing by where the player was looking a tick earlier. A swing should be judged by the rotation the player last reported. |
| `gametest/CrateStackBreakPacketPathGameTests.java` | New file — five tests driving `handleBlockBreakAction` | Closes a real coverage gap: M6's break tests hand the target to the block directly, so `LeftClickBlock` capture, the server raycast, the agreement check and the cascade guard had no end-to-end coverage. |
| `event/CrateStackBreakHandler.java` | Removed temporary `M65DIAG` logging | Part 8 diagnostics, gated to the crate path, removed before the candidate as required. |

## 19. Automated regression

```text
Unit:      3331 tests, 0 failures, 0 errors, 17 skipped   (430 classes)
GameTests: 983 required, all passed
```

Group totals, all zero-failure:

| Group | |
|---|---|
| Crate stack domain | `CrateStackPackingTest` 16, `CrateStackPersistenceTest` 10, `CrateStackGeometryTest` 11, `CrateStackTargetResolverTest` 9, `LogicalCrateContainerTest` 6 |
| Crate blocks/art | `CrateArtCollisionTest` 8, `CrateStackingInteractionTest` 6, `CrateBlockEntityTest` 4 |
| Destruction, menus, world cells | `CrateStackPromotionGameTests`, `CrateStackWorldCellGameTests`, `CrateStackInteractionGameTests`, `CrateStackBreakGameTests`, `CrateStackBreakPacketPathGameTests` — all inside the 983 |
| Grabby | 19 classes, 218 tests |
| Protection | `GrabbyProtectionAuditTest` 17, `GrabbyAuthorizationRegressionTest` 8, `FlowerProtectionBypassTest` 3 |

The new regression was verified to actually catch its defect rather than merely coexist with the
fix: with the raycast reverted to partial tick `0.0F`, `aSwingIsJudgedByTheRotationTheClientJustSent`
fails with *"a swing was judged by last tick's aim and destroyed nothing"* and the suite reports 1
required failure; with `1.0F` restored, 983/983 pass.

## 20. Commits

```text
M6    9582cd3475cdaf578292fe8f10b08caf8791e3a8
      feat(crates): break one crate out of a compact column

M6.5  25683aabef9a7c162f00ac0091afaf5210bcfcb5
      fix(crates): judge a crate swing by the aim the client just sent
```

This document is committed separately as the acceptance record, so the candidate below corresponds
exactly to `25683aab`.

## 21. Final candidate

```text
branch   claude/stackable-wooden-crates-e60016
HEAD     25683aabef9a7c162f00ac0091afaf5210bcfcb5
tree     clean
version  0.1.8
git.dirty false
jar      britannia_mod-0.1.8-all.jar
size     34336487 bytes
sha256   c2ab256689151b4301cea8cdaaf75bf1359310948cb40cc96748f2308a6c28f3
```

Plain jar, for a server that already has GeckoLib installed separately:

```text
jar      britannia_mod-0.1.8.jar
size     33764849 bytes
sha256   3f8e21bc3e5aa75ac5ce986e03df0e559a866563726545252f627b743549b294
```

The jar bakes a build timestamp, so it is not byte-reproducible across builds; it is however built
from an exactly identified clean commit, and `git.dirty=false` is recorded inside the artifact
itself.

## 22. Remaining risks

1. **The entire live acceptance is unperformed.** Sections 4–7 and 10–17 are unobserved. This is the
   blocker.
2. **The cascade guard's value is unmeasured.** The mechanism is proven; `4` is still a guess.
3. **Client-side snapshot restore is unexamined from the client.** The server's send order is a
   checked fact; what NeoForge's prediction restore does to a restored `CrateStackBlockEntity` in the
   frames before the layout update arrives is not.
4. **No shader evidence at all** for a renderer that was newly written in M4.

## 23. M7 gate

```text
M7 BLOCKED
```

The blocker is unperformed live acceptance, not a known defect. M7 becomes available the moment the
checklist below is run and passes; nothing in the code is known to be waiting on a fix.

## 24. Live checklist to run

Run against the candidate jar in §21. Each line is one observation. The milestone's own acceptance
criteria are the pass conditions; this is the order that makes them cheapest to collect.

**Setup.** Join a Survival world inside your own house or another area where you legitimately hold
build rights. Keep `/cratestack info` handy — it is the only way to compare what you see against the
logical IDs.

1. **Bottom break.** Stack small A (apples) / B (iron) / C (diamonds). Break A. Watch for: whole
   column vanishing, ghost air, persistent flicker, stale geometry, invisible collision at the old
   heights. Confirm B settles to the floor, C onto B, IDs unchanged, only apples dropped, and both
   menus still open.
2. **Middle break.** Rebuild. Break B. Watch specifically for C rendered at its old height, C drawn
   twice, C briefly gone, or collision left where C was.
3. **Continuation break.** Three smalls = 21.45 voxels = `PART=0` + `PART=1`. Break the top crate
   through `PART=1`. Then immediately walk through the space `PART=1` occupied.
4. **Root-survival prediction.** With crates remaining, break the bottom one through `PART=0`. This
   is the one to watch frame by frame: what appears immediately, what appears after the ack, what
   appears a tick later.
5. **Held click.** Four smalls, hold the button through one completion. Count how many crates one
   continuous hold can take, and whether destroy progress restarts on its own.
6. **Final crate.** Reduce to one and break it. Confirm no ghost, no invisible collision, no
   `crate_stack` item, and that reconnecting does not resurrect it.
7. **Reconnect.** After several removals, disconnect, rejoin, open every survivor.
8. **Protection.** Repeat one break outside your build rights — it must be refused with the column
   unchanged.
9. **Creative.** Bottom, middle, top and final, in Creative.
10. **Iris + Photon.** Normal column, column after a repack, a crate crossing a cell boundary, mixed
    small/medium, and a full-height stack. Black geometry, missing faces, flickering duplicates or a
    stale mesh after a break are M6.5 blockers.
11. **Feedback position.** Break a crate high in a column — sound, particles and drops should come
    from that crate, not the floor.
12. **Mixed and tall.** small/medium/small and medium/small/medium, breaking each position; then a
    64-voxel four-cell column, breaking in several places.

If two clients are available, add: player 1 opens crate C, player 2 breaks B beneath it — C moves,
player 1's menu stays on C; then player 2 breaks C — player 1's menu closes cleanly with no
duplication and no server exception.
