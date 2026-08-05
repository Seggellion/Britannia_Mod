# Shrine and Monolith Post-Merge Validation

This checklist must pass before production promotion unless the project owner issues a separate explicit release-risk waiver.

A merge into patch-18 does not itself establish runtime validation.

The refreshed pre-merge production/evidence tip is
`b26165350227e6d46cfde3af91ff738a42e48c11`. The exact merged candidate must also contain the later
documentation-only refreshed audit commit. Do not reuse the original Milestone 10 JAR or any earlier
corrective build as release evidence.

## Exact candidate and topology

- [ ] Build the exact merged production `-all.jar` from the reviewed merge commit.
- [ ] Record its filename, byte size, entry count, manifest, and SHA-256.
- [ ] Prove build, artifact, dedicated-server, Client A, and Client B copies have matching hashes.
- [ ] Confirm exactly one Britannia JAR exists in each runtime `mods` directory.
- [ ] Use a clean NeoForge 21.1.72 dedicated server with `online-mode=true`.
- [ ] Use two distinct authenticated Minecraft accounts on independently controlled clients.
- [ ] Record sanitized server and both-client version/mod/channel/resource agreement.

## Two-client and live gameplay matrix

- [ ] Both clients connect concurrently.
- [ ] Client A observes Client B and Client B observes Client A.
- [ ] Each client places a shrine and monolith in all required facings.
- [ ] Both clients observe one anchor-owned render and the same occupied cells.
- [ ] For every shrine facing, confirm the four 64 by 64 texture quadrants assemble one correctly
      oriented symbol with no row wrap or seam displacement.
- [ ] Confirm the shrine inner surface reaches 14 model voxels, the granite rim reaches 15, and the
      separate granite material pass has no seam or z-fighting artifact.
- [ ] Confirm runtime resource selection maps only `shrine/chaos` to
      `textures/block/shrine/light_granite.png` and maps the other eight variants to
      `textures/block/shrine/granite.png`. The current files are byte-identical, so path/resource
      evidence is required even if no visual difference is observable until distinct light content is supplied.
- [ ] Confirm the 32 by 32 model-voxel shrine presentation remains centered and flush against the
      prescribed eight-stair frame with no gap, penetration, empty side, stripe, overlap, or boundary
      artifact.
- [ ] Confirm the Decorative & Graveyard tab exposes exactly one configured shrine and one configured
      monolith, resolving to `shrine/honesty` and `monolith/diagnostic_missing_content`, with no anchor
      or part item exposed.
- [ ] Authorized shrine cycling covers all nine variants and wraps deterministically.
- [ ] Authorized monolith cycling covers all three variants in order and wraps deterministically.
- [ ] Cycling works when the Interior Decorator targets either anchor or part.
- [ ] Unauthorized interaction fails without state change or success feedback.
- [ ] Late tracking shows the correct family, variant, facing, footprint, and render.
- [ ] Disconnect and reconnect are tested in both client directions.
- [ ] Save-world reload preserves state and render for both clients.
- [ ] A full server restart preserves state and render for both clients.
- [ ] Client resource reload preserves identity and restores correct resources.
- [ ] Survival removal from anchor and representative parts yields exactly one configured family item.
- [ ] Creative cleanup yields zero configured drops.
- [ ] Pick block from anchor and every valid part reconstructs the exact configured item state.
- [ ] Explosion behavior matches the documented centralized policy without duplication.
- [ ] External replacement preserves the replacement and removes only owned cells.
- [ ] Missing-part repair uses persisted offsets; obstructed repair preserves the obstruction.
- [ ] Piston movement is blocked for anchor and parts.
- [ ] Fluid replacement and waterlogging remain unavailable for anchor and parts.
- [ ] Cross-chunk placement, tracking, cycling, restart, lifecycle, repair, and unloaded-required-chunk rejection behave as documented.
- [ ] Live full-block and stair matrix covers straight, inner-corner, outer-corner, top, bottom, and all facings around structure perimeters.
- [ ] Slabs, walls, fences, fence gates, panes, supported attachments, and other required neighbors recompute and remain outside occupancy.
- [ ] All three monolith models are reviewed against the horizon in all four facings; verify the positive 16-voxel Y offset once, alignment, culling, texture distinction, and no duplicate part render.
- [ ] A dense scene of at least 12 shrines and 8 monoliths receives a responsiveness, frame, tick, memory, and bounded-log smoke.
- [ ] Review the server log and both complete client logs across connect, cycle, reload, reconnect, restart, teardown, and dense-scene ranges.

## Evidence and disposition

For every check, record `PASS`, `FAIL`, or `UNVERIFIED`, exact artifact hash, actor/client, world position, time range, and sanitized evidence reference. A failure blocks promotion and requires a separately scoped corrective milestone. `UNVERIFIED` is not a pass.

The owner decision dated 2026-08-04 permits Milestone 10 and a separately authorized code-review/merge procedure to proceed conditionally. It does not waive this pre-promotion checklist and establishes no multiplayer or live-gameplay result.
