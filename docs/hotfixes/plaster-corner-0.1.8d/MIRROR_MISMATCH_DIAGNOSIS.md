# Packaged wall-pair mismatch diagnosis

Disposition: **PRE_EXISTING_BLOCKING_REQUIRED_CORNER_BEHAVIOR**. This is a player-visible physical defect, not harmless stored-state divergence. The plaster mesh repair passes its bounded render matrix, but plaster acceptance cannot be cleared while decoration can leave the two heights with different collision. Mirrored neighboring joins also fail with matching halves. No shared Java repair or waiver is included in this asset hotfix.

Evidence was collected on exact `patch-18` checkpoint f29548d77c03163e16321f120e28904685fdbdab, using the clean ac6b2e80 candidate, SHA-256 `de563ecfcb8f9067248ec09ae0578f7776ea5c1beefa4f1c182b9ac255a5df2e`. See [runtime evidence](implementation/acceptance-resume/), [candidate recheck](implementation/acceptance-resume/CANDIDATE_RECHECK.json), [actual client log](implementation/acceptance-resume/CLIENT_LATEST.log), and [saved states](implementation/acceptance-resume/CLIENT_SAVED_STATES.json). All worlds are disposable copies under `tmp/plaster-corner-0.1.8d`; original worlds and candidates remain preserved.

## Minimal actual-click reproduction

The isolated sample is a support corner at (48,80,84), upper at Y81, with blank straight arms west at (47,84),(46,84), facing south, and north at (48,83),(48,82), facing east, both heights. These arms give a normal south/corner/branch_right=false placement. A [saved 7x7 neighborhood dump](implementation/acceptance-resume/MINIMAL_SAMPLE_NEIGHBORHOOD.json) verifies these ten occupied blocks and no other connecting neighbor. Actual BlockItem placement was previously recorded with matching unmirrored halves on this same packaged candidate.

The resumed run first retained the prior actual lower-only mirror. An actual offhand decorator click on the lower at camera (48.5,80,86.2), yaw180,pitch43.6 returned BOTH mirrors to false. An actual offhand click on the upper, pitch18, then produced:

| Property | Lower | Upper |
| --- | --- | --- |
| ID | plaster_wall_and_support_blank | same |
| half | lower | upper |
| facing / shape / branch_right | south / corner / false | south / corner / false |
| mirrored | false | true |

Both runtime execute-if-block assertions, F3 target states and the later saved NBT agree. [Lower click](implementation/acceptance-resume/decorator-lower-mirror.png) and [upper click](implementation/acceptance-resume/decorator-upper-mirror.png) show real gestures and selected heights. The upper click leaves visible art unchanged. A lower click moves the visible art through both heights. This follows the selectors: the lower model renders Y0..32; the upper model is air. The upper block is nevertheless a real collidable/selectable block.

Main-hand rotation was also clicked on each half. Lower rotation left lower west/corner/false/unmirrored while upper stayed south/corner/false/mirrored; this divergence persisted through the scheduled 10-second check. Upper rotation subsequently normalized both facings to south but left the mirror mismatch. A dirt/stone floor neighbor notification did not synchronize MIRRORED. The [rotation screenshots](implementation/acceptance-resume/decorator-lower-rotation.png) and [upper rotation](implementation/acceptance-resume/decorator-upper-rotation.png) plus PAIR markers retain these observations.

## Physical effect measured in the engine

Distinct NoGravity item entities approached each height from OUTSIDE the branch, z84.35, with x motion +/-0.12 for 20 ticks. The item half-width is 0.125. Two unobstructed parallel controls traveled approximately 1.913 blocks, excluding a stationary probe artifact. The initially attempted probes starting embedded in the block are excluded; accepted results use external starting positions x49.5 and x47.5.

| Pair mirrors lower/upper | East lower final x | East upper final x | West lower final x | West upper final x |
| --- | ---: | ---: | ---: | ---: |
| true / false | 48.5625 | 49.125 | 47.875 | 48.4375 |
| false / true | 49.125 | 48.5625 | 48.4375 | 47.875 |

Lower probe Y80.2 and upper Y81.2 remain constant. The common south arm stops both heights at z85.125. Exact methods, starting positions and results are in [lower-only mirror physics](implementation/acceptance-resume/PHYSICS_RESULTS.json), [upper-only mirror physics](implementation/acceptance-resume/PHYSICS_UPPER_ONLY_MIRROR.json), [saved command storage](implementation/acceptance-resume/COMMAND_STORAGE.json), and the retained `physics*.mcfunction` files.

The branch collider shifts **9 pixels / 0.5625 blocks** between heights. With only the upper mirrored, the lower model still draws an east-edge branch through BOTH sections, while the upper block collides on the west edge. Thus part of the visible upper arm lacks its expected collider and the opposite side has an invisible upper barrier. This conclusion uses actual entity motion, visible geometry, exact states and source behavior together; it is not inferred from differing property strings alone. Item physics does not certify the still-missing sustained player walking/jumping checklist.

## Cause and pre-existing evidence

`InteriorDecoratorToolItem.useOn` calls `setBlock` at the clicked position only for offhand MIRRORED and generic main-hand FACING rotation. `DoubleWallBlock.updateShape` checks counterpart ID and opposite HALF, then derives connection properties from the lower neighborhood. It does not copy MIRRORED or synchronize an explicit rotation. `MirrorableWallBlock.getShape` flips the branch for mirrored non-straights; collision delegates to that shape separately for each stored half. The lower-only visual selector therefore cannot make differing upper collision harmless.

`DoubleWallBlock.runOf` and the full-wall path in `PlasterWallHalfBlock.runOf` read raw FACING/BRANCH_RIGHT, ignoring MIRRORED. Neighbor derivation therefore describes logical rather than rendered/colliding branch edges. `MirrorableWallBlock.mirror` additionally toggles MIRRORED after the superclass flips BRANCH_RIGHT, requiring careful physical-edge treatment in a follow-up.

[PRE_EXISTING_BYTECODE.json](implementation/acceptance-resume/PRE_EXISTING_BYTECODE.json) compares the candidate with the retained PRE-plaster fence jar (source 07359f98, SHA25689c3f936…486cb): **InteriorDecoratorToolItem, DoubleWallBlock, MirrorableWallBlock, WallConnection and PlasterWallHalfBlock are byte-identical**. Git also has no differences in these Java files from the pre-plaster integration base. The unchanged old and new corner branch slabs and mirror convention establish that the newly moved post did not introduce the pair-collision or neighbor-topology problem. The prior jar was inspected, not installed; an old-client runtime replay is not claimed.

## Normalization, joins and transforms

Forced legal E/W states were inserted in a CLOSED disposable copied world and verified independently for lower/upper at runtime. Their renders pass; they are not normal-placement evidence. A subsequent ordinary neighbor notification changes an E/W corner to a derived N/S state. This is recorded explicitly rather than counting the normalized render as an E/W render.

All twelve vanilla structure placements (four rotations x NONE/LEFT_RIGHT/FRONT_BACK) were executed and both resulting halves logged and dumped. Four unmirrored rotations keep physical joins, but 90/270-degree placements normalize to true-branch N/S models and consequently use the pre-existing two-post art layout. This is not proof that a one-post asset remains a literal one-post rigid rotation after normalization. Eight mirrored structures have matching MIRRORED in both halves yet show a detached/misplaced branch. The mirrored T control does too. See [transform states](implementation/acceptance-resume/CLIENT_SAVED_STATES.json), [frame map](implementation/acceptance-resume/JOIN_FRAMES.json), and scenes 16–23 /30. Pair synchronization alone cannot fix this second behavior.

Support joins and neighbor mutations work for the unmirrored fixture. Door and valid two-half tagged wooden_gate fixtures normalize to north/straight. Unrelated solids/posts and full-with-half remain straight; half-with-full and half-with-half derive a corner. These are observed existing connection rules, not a newly introduced bidirectional full/half-join guarantee.

Save/restart persistence is reported in [acceptance](CLIENT_ACCEPTANCE.md). Persisting the mismatch proves a durable defect; it does not pass the corner physical acceptance gate. A [bounded follow-up proposal](WALL_PAIR_FOLLOWUP_PROPOSAL.md) identifies affected consumers, required tests and model-layout review without expanding this mesh hotfix.
