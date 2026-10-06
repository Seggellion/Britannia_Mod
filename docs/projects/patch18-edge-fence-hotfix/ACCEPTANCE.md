# Wooden fence acceptance

Source implementation and release acceptance are separate. This ledger is
updated from executed results; pending checks are not passes. Status:
**IMPLEMENTATION_COMPLETE_PENDING_ACCEPTANCE** and
**CANDIDATE_PREPARED_RELEASE_HELD**.

| Contract | Result and evidence |
| --- | --- |
| Four isolated edges; support convention | PASS — four registered anchor tests, 24 internal constructed BlockItem scenarios |
| Straight forward/reverse, immediately/ticks/removal | PASS — old owner facing, outline/collision and new straight anchor asserted in the same scenarios |
| Saved physical layout independent of current flags | PASS — all 64 old states through actual BlockState codec, old independent shape oracle, first correction and serialized round trip |
| Sentinel resource mapping and every registered state | PASS — frozen 64-resource oracle, exactly one mapping for each of 2,112 states |
| Authored artwork/item/textures retained | PASS — original seven models unchanged in Git; normalized SHA-256 contract, item parent/texture contracts |
| Reflected geometry and UV correspondence | PASS — 1,536 independent transformed vertex+UV comparisons; client observation pending |
| Local symmetric contacts; absent/gapped arms | PASS — all 128 concrete layouts pairwise, reverse owner order and rotation; loaded/unloaded query bound test added |
| No global run scans or neighbor materialization | PASS — explicit four-query/no-write proxy in the 23-test read run |
| Fixed points, neighbor removal, pinned elbows | PASS — at least 20 update rounds; temporary neighbor and enclosure arm removal |
| Sequential enclosure and corner re-placement | PASS — ordinary BlockItem 4×4 perimeter in all rotations; pre-existing single remains single until deliberately removed |
| Support conflicts and actual replacement target | PASS — support anchor retained; deterministic repeated context; replaceable clicked cell uses resolved target |
| Rotation/mirror/tool | PASS — 128 layouts, four rotations, both mirrors, identities/round trips; real decorator use with tick/load inspection |
| Both patio copies | PASS corrected reader in 22-test write run — actual old palette and StructureTemplate loading, transformed physical-state geometry |
| Actual chunk eviction/reload X/Z 15/16, both orders | PASS write run — four actual cache eviction/load cases; absent neighbor stays absent after derivation |
| Actual process restart and all 64 saved layouts | PASS — separate write/read JVMs, 64 actual saved Anvil states, orderly saves/shutdowns |
| Collision height/interior, path and oak controls | PASS — 1.5 collision, 1.0 outline, open L interior, Survival/Adventure collision and real shovel/queued path |
| Other fence families | Unchanged source/resources; oak control passes. Dedicated interactive mixed scene pending |
| Complete JUnit | PASS final clean run — 4,121 total, 4,098 passed, 23 skips, zero errors/failures; all skips also exist on baseline. Baseline has one extra packaging skip because no candidate existed |
| Complete configured GameTests/build | PASS — 1,362 required GameTests, zero failures, normal compilation and clean build/verifyDeployableJar/artifactIdentity |
| Dedicated matching client/server packets/render | PENDING — baseline and matching hotfix clients reached main menu, but Windows PIN/locked desktop blocked world connection and mouse/keyboard acceptance; no packet-placement pass claimed |
| State/model/cache cost | Measured three heap samples per client, stable counts; +2,048 states, +28 SimpleBakedModels, +1,248 BakedQuads, +8,719,176 median Java heap bytes. See COST_MEASUREMENTS.md. Repeated startup variance/isolated model-bake/frame timing pending |
| Release | HELD — medallion visuals and Alligator client visuals remain; no deployment, publication or production-world changes |

The server placement hits are constructed contexts. Mouse raycasts/packets,
visible art/UVs and keyboard movement must be recorded independently.
Remaining client scene must cover four-facing/support/endpoint/ground packet
placement, displayed authoritative state, original/reflected art and UVs,
hit outlines, sequential corners, walk/jump barriers, decorator edits and
reconnect/reload. Actual automated save/restart already passes; visual
agreement after reload is still pending. No world scene was reached before
the desktop lock, so no screenshot is offered as scene acceptance evidence.
Existing noncanonical T/end states retain their separate old render and
collision geometries, with conservative contacts; they are not repaired
into new artwork. Synthetic-player backend fetch warnings are not backend
parity evidence. Lifecycle fixture chunk requests are explicit test setup;
the production derivation itself never acquires an unloaded neighbor.
