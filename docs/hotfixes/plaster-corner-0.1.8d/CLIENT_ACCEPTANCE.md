## Current combined closeout — 2026-10-07

This update supersedes the historical status below. The bounded shared-wall repair is integrated in `5a6734ba`; clean final candidate source is `986ed746`, SHA-256 `f02d2876d60e10a539829950b173b61055a72e6e42dd36643bec97ab62f482bd`, 36,142,668 bytes. Full 4,129-case JUnit and 1,366-required-GameTest gates pass; candidate and prior evidence are preserved. Actual packaged both-half edits now match, joins/transforms render correctly, engine legacy collision follows the visible lower at both heights, and all 54 copied client fixture rows/neighbors survive a real dedicated load/save with 108 loaded state assertions. This is not a claim of post-load human visual acceptance.

Plaster status: **AUTOMATED_AND_CURRENT_RENDER_ACCEPTANCE_PASS; FINAL_LOADED_APPEARANCE_PENDING**. The prior mirror divergence was pre-existing and player-visible/blocking (0.5625-block upper branch displacement); it was repaired, not waived. Current manual remainder is one upper edit followed by reconnect/resource refresh and appearance/outline comparison. Sustained wall/fence movement is already owner-approved and must not be repeated.

Combined production acceptance remains pending fence corner/decorator/reconnect appearance and alligator playback, plus the short plaster loaded appearance check. Medallion UI is **OWNER_APPROVED_UI_ACCEPTANCE_COMPLETE**. Timing repetitions are follow-up confidence measurements, with measured memory cost retained. Source publication is independently authorized; `main` must follow its ruleset without administrator bypass. See the [current combined handoff](../combined-0.1.8d/RELEASE_HANDOFF.md), [gate reassessment](../combined-0.1.8d/GATE_REASSESSMENT.md) and [single manual session](../combined-0.1.8d/MANUAL_SESSION.md). No live deployment, world edits, launcher update, tag or GitHub Release is authorized/performed.

---

# Plaster client acceptance ledger

Status: **BLOCKED_PRE_EXISTING_WALL_PAIR_AND_MIRROR_TOPOLOGY**. The scoped repaired-mesh render matrix is complete. Plaster acceptance is **not complete**: actual decoration creates player-visible upper/lower collision divergence, and mirrored structure/neighbor joins fail even with matching halves. See [diagnosis](MIRROR_MISMATCH_DIAGNOSIS.md) and the [bounded follow-up proposal](WALL_PAIR_FOLLOWUP_PROPOSAL.md). No shared Java rewrite or acceptance waiver was performed.

Resumed exact `patch-18` at **f29548d77c03163e16321f120e28904685fdbdab** without resetting. Candidate remains the clean **ac6b2e80e1dac33d48dfd0b48b52543deab5cb2d** bundled jar: **36,140,726 bytes**, SHA256 **de563ecfcb8f9067248ec09ae0578f7776ea5c1beefa4f1c182b9ac255a5df2e**. Candidate identity and all 17 support resource JSONs were rechecked. Application source/resources/tests and version have no resumed changes; build and prior automated evidence remain applicable. Verification tooling and documentation do not require a replacement candidate.

Actual packaged checks use the pinned MC 1.21.1 / NeoForge 21.1.72 / Java 21 / GeckoLib 4.6.6 SDK with project mod roots excluded, separate bundled dependencies removed, empty MOD_CLASSES, and only this verified jar in isolated mods. Runtime logs discover that jar and bundled GeckoLib/nanohttpd. Default resource packs are empty; no shader mods are loaded. Missing isolated backend credentials are not service acceptance.

[OUTSTANDING_ROWS.json](implementation/acceptance-resume/OUTSTANDING_ROWS.json) preserves all 27 rows extracted at resume from this ledger, milestone records, handoff and playbook. The matching [ACCEPTANCE_RESULTS.json](implementation/acceptance-resume/ACCEPTANCE_RESULTS.json) supplies row IDs, actual results and individual evidence references. “Completed observation” records a behavior, not an unconditional acceptance pass.

| Row | Result | Actual observation / limit |
| --- | --- | --- |
| 1 | PASS RENDER ONLY | All four facings x two mirrors rendered with exact LOWER/UPPER assertions; cases 0,1,2,5,6,9,12,13. Forced legal states do not certify normal placement. |
| 2 | PASS RENDER CONTROL | Eight true-branch full controls rendered; existing two-post layout retained. |
| 3 | PASS MESH | Both visible sections and top inspected; lower renders Y0..32, upper air, no duplicate upper post in repaired false branch. |
| 4 | PASS RENDER ONLY | All four false-branch half facings rendered with exact assertions; visible cap, one post, no invented mirror/half. |
| 5 | PASS RENDER CONTROL | All four true-branch half controls rendered with exact assertions. |
| 6 | COMPLETED WITH PRE EXISTING FAILURE | Valid straight/T states rendered, both branches represented and full mirror controls. Mirrored T detached branch matches mirror-ignorant neighbor derivation; art unchanged. |
| 7 | PASS ACTUAL CLICK | Actual corner BlockItem placed before command-created blank arms; both heights derive south/corner/false/unmirrored. |
| 8 | PASS ACTUAL CLICK | North then west blank arms prepared; actual corner BlockItem click gives matching south/corner/false/unmirrored halves. |
| 9 | PASS OBSERVED RULE | Unmirrored full support arms join to matching south/corner/false/unmirrored center. |
| 10 | PASS OBSERVED RULE | Valid two-half oak doors normalize center to north/straight; rendered and dumped. |
| 11 | PASS OBSERVED RULE | Corrected valid two-half tagged wooden_gate neighbors asserted at all four cells; center north/straight both halves. Initial lower-only fixture excluded. |
| 12 | PASS OBSERVED RULE | Stone and wooden_post are non-peers; center remains south/straight both halves. |
| 13 | COMPLETED OBSERVATION | Full center ignores half neighbors (straight); half center joins full blank/half peers (corner). This is observed existing asymmetry, not a symmetric join pass. |
| 14 | PASS OBSERVED RULE | Remove north arm: matching south/straight; add back: matching south/corner. E/W forced corner separately normalizes N/S on neighbor notification; MIRRORED mismatch survives update. |
| 15 | COMPLETED FAIL PRE EXISTING | Actual offhand lower click changes only lower mirror, with visible art following lower through both heights. Prior true/false divergence restored to false/false by repeated click. |
| 16 | COMPLETED FAIL PRE EXISTING | Actual offhand upper click produces false/true; visible art unchanged while upper branch collider moves 0.5625 blocks. |
| 17 | COMPLETED FAIL PRE EXISTING | Actual main-hand lower rotation leaves lower west while upper remains south; scheduled ten-second pair assertions confirm divergence. |
| 18 | COMPLETED FAIL PRE EXISTING | Actual upper rotation normalizes facings to south but leaves false/true mirrors; not paired-edit acceptance. |
| 19 | COMPLETED OBSERVATION | All four unmirrored vanilla rotations executed. Physical joins retained; 90/270 normalize to N/S true branch with pre-existing two-post layout. Literal one-post rigid transform not passed. |
| 20 | COMPLETED FAIL PRE EXISTING | All eight mirrored vanilla structure transforms executed; both halves match, but rendered branch detached/misplaced after normalization. |
| 21 | NOT COMPLETED UI HOLD | W/Space taps did not sustain movement in per-tick player coordinates. Engine item collision probes complete but do not pass player walking/jumping. Manual reproduction prepared. |
| 22 | COMPLETED WITH PRE EXISTING FAILURE | Actual selection/targeting on both stored heights observed; upper is selectable despite air model. Per-half shape divergence blocks outline/geometry agreement. |
| 23 | PASS RESOURCE RELOAD | Actual F3+T resource refresh completed and settled structure/target state reobserved; no jar change. |
| 24 | NOT SUPPLIED | Default no selected packs/shader mods verified. Owner overrides not provided; unknown configuration cannot be certified. |
| 25 | PASS PERSISTENCE | Dedicated copied world: 90 chunks loaded, save/stop/restart/save/stop both exit0. All 54 fixture rows and recorded neighbors unchanged; divergent mirrors persist. |
| 26 | COMPLETED REPRODUCED | Minimal isolated corner with four two-height blank-arm cells; actual upper click from matching false/false creates false/true. |
| 27 | COMPLETED BLOCKING DISPOSITION | Byte-identical pre-plaster shared classes; actual rendered/physical branch divergence, mirror-ignorant joins and durable persistence. Pre-existing blocking required corner behavior. |

The full matrix contains16 exact full corners (four facings x branch x mirror), eight exact half corners (four facings x branch), and legal straight/T representatives. Cases0,1,2,5,6,9,12,13 cover the repaired false branch. The native F2 screenshots were visually reviewed; [RENDER_FRAMES.json](implementation/acceptance-resume/RENDER_FRAMES.json) maps them to runtime case markers. [FORCED_STATES.json](implementation/acceptance-resume/FORCED_STATES.json) records the closed-world legal fixture before rendering; [FINAL_GRID_STATES.json](implementation/acceptance-resume/FINAL_GRID_STATES.json) records subsequent saved normalization separately. A first occluded E/W fixture is retained but excluded. Case0 and the north half received settled oblique retakes for the top and cap. No additional in-scope mesh defect was exposed.

[JOIN_FRAMES.json](implementation/acceptance-resume/JOIN_FRAMES.json) maps all 31 architectural scenes to the actual log marker preceding each native screenshot. Repeated or early frames are retained; use settled retakes for scenes 0,1,5,12–23. A timer request index alone is not a scene identity. The first lower-only tagged wooden_gate fixture was invalid; its later two-half correction is independently asserted and dumped. Door/gate/full-half observations report actual existing rules without inventing a symmetric joining requirement.

All 12 vanilla structure transforms were executed and exact post-normalization halves recorded. Four unmirrored transforms retain physical joins, though quarter-turn normalization selects existing true-branch two-post models. All eight mirrored transforms show detached/misplaced branches despite matching half mirrors. This fails transformed corner behavior and cannot be cured by merely syncing the pair.

Actual decorator clicks on lower and upper, both offhand mirror and main-hand rotation, are retained with F3 target states and runtime assertions. The lower model renders both visible sections and the upper model is air, but both stored blocks supply independent collision/selection shapes. Engine item movement proves a 9-pixel branch collider offset between heights in divergent pairs; common-main-arm and free-space controls pass. [Lower-only physics](implementation/acceptance-resume/PHYSICS_RESULTS.json) and [upper-only physics](implementation/acceptance-resume/PHYSICS_UPPER_ONLY_MIRROR.json) establish practical effects. Pre-plaster candidate byte comparison of all five responsible classes establishes pre-existing cause. The issue is **blocking**, not harmless stored-state divergence.

[Persistence results](implementation/acceptance-resume/PERSISTENCE_RESULTS.json): a CLOSED client-world copy was loaded with the same jar in a localhost dedicated server;90 fixture chunks were force loaded. Normal save/stop and restart/save/stop both exited0. The26 grid,12 structures,9 neighborhoods,4 controls and3 actual-click samples (54 rows), with recorded lower/upper/neighbors, compare exactly from client save to first dedicated save to restarted save. [First server log](implementation/acceptance-resume/SERVER_FIRST.log) and [restart log](implementation/acceptance-resume/SERVER_RESTART.log) identify actual loaded artifact/source and pair states. The durable mismatch is a preserved defect, not a physical gate pass. No network-client or production restart is claimed.

Successful earlier same-candidate evidence is retained: owner junction, old free-end finished plaster, both visible heights/top, actual placement, original26 saved-grid equality, chunk eviction/load and integrated save/reopen. These checks are not discarded or presented as newly rerun. Actual F3+T resource refresh is now additionally recorded.

Outstanding: bounded shared wall-pair/mirror-topology repair and affected packaged revalidation; sustained player walking/jumping against both arms/heights (supported key taps did not hold movement, per-tick coordinates unchanged); exact owner pack/shader overrides if any exist (not supplied). Precise [runtime reproduction instructions](RUNTIME_REPRODUCTION.md) and retained command pack support these checks. Automated entity physics does not waive the player interaction row.

Separate combined0.1.8d release readiness remains **HELD** for fence mouse/state/art/UV/outline/walk-jump/decorator/reload acceptance and outstanding timing measures, alligator real gait/pose/transitions/attack visuals, and medallion sustained motion/swim/crawl/cape (optional multiplayer) visuals. Their current records remain authoritative; plaster results clear none of those holds.
