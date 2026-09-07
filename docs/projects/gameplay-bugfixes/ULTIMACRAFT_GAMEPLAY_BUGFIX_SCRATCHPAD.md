# Gameplay bugfix implementation scratchpad

Current: M2 complete; next M3. Authorized execution: M0–M11, local commits, no push/merge/deploy/production writes or Fabric work. Playbook and kickoff supersede historical discovery recommendations. All five authoritative documents read in full before code edits.

## Workspace and frozen contract

Original mod: C:/projects/britannia/mod/Britannia_Mod, patch-18 at 421e27853dde4099d1d794568e33e6709507a53b. No divergence (0/0). Tracked files clean. Existing untracked farming/Rowan/playbook/discovery documents preserved. No applicable AGENTS.md on ancestor chain, root, .claude or .claude/worktrees. Remote origin https://github.com/Seggellion/Britannia_Mod.git. No network git operation performed.

Implementation: C:/projects/britannia/mod/Britannia_Mod/.claude/worktrees/patch18-gameplay-bugfixes, codex/patch18-gameplay-bugfixes, same initial SHA. Other worktrees preserved: rowan-farmer-exploration-97e04a (0cd566dd811bb717c6bda498ac3bed38be64b95e), rowan-farming-questline-mod (45ad043458491a134c46028c43115362fcf83bf5), patch18-m2 candidate (625269d3bb5abb560a901f7e9198abe910eb482a), public-release (5c0b917226848192e40b376cae719057e70cd662), starfarer-m11 (7a4d2fc768c38969eec7e7be9412974c3ab2ff2f). Rails inspection/worktree deferred until M7.

Decisions: 1200 online game ticks, exact hoed restoration, unused fertilizer forfeited, paused preparation budget; prepared community or actor-owned-house empty farmland only; bowls +1 and custom bowl remainder; planting/harvest species equality including grapes/flowers; harvest min(.95,.75+.01*surplus), destructive failure/cost/practice and lifecycle, free deterministic admin success; native cultivation outside custom unchanged; only three landscape features removed; all 36 approved produce gaps; role-based two-hand recipes and compatible-first outputs; offhand exact case ejection/mainhand whole-case rotation; deterministic fence, 1.5 collision and narrow dirt-path exception; Creative substrate bypass with technical validity. BUG-04/06/07 excluded. Full-can artwork user supplied; no generated assets. Reported vendor Jhelom/Britannia. Old Q1–Q5 are closed by playbook; live evidence still must be observed.

## Milestones

| Milestone | Status | Start / end |
|---|---|---|
| M0 workspace, baseline, contract | PASS | 421e27853dde4099d1d794568e33e6709507a53b / a95538d862a6c9316d7f45077fbc620e3241d097 |
| M1 soil, expiry, bowls | PASS (client checks pending) | a95538d862a6c9316d7f45077fbc620e3241d097 / f31550eefb215e198afc60a35dec355661a5f173 |
| M2 hand recipes/output | PASS (client checks pending) | f31550eefb215e198afc60a35dec355661a5f173 / pending |
| M3 display-case transactions | PENDING | |
| M4 harvest gates/outcomes | PENDING | |
| M5 feedback/can state | PENDING | |
| M6 landscape features | PENDING | |
| M7 produce mod + Rails | PENDING | paired SHAs required |
| M8 fence joins/collision/path | PENDING | |
| M9 Creative decoration | PENDING | |
| M10 shader reproduction/correction | PENDING | |
| M11 final gates/build/handoff | PENDING | |

## M0 evidence

Normal source sets from unmodified build.gradle; no init script/diagnostic source input enabled. Runtime Minecraft1.21.1, NeoForge21.1.72, GeckoLib4.6.6, Gradle8.9/userdev7.0.165, mod0.1.8a. JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-21.0.9.10-hotspot (PATH Java8 unsuitable). Existing minecraft_version_range=[1.21,1.21.1) discrepancy is inherited; no unrelated metadata change.

Historical logs intact at original checkout tmp/gameplay-bugfixes: baseline-summary.json 3479 tests/17 skips/0 failures/0 errors (3462 executed), baseline.log 1102 GameTests passed; focused-summary.json 172 tests/6 skips (166 executed); supplemental logs and both failed-fixture explanations preserved. These are historical, not new runs. Copied all four discovery/planning/acceptance docs, playbook and kickoff into this worktree.

New smoke command: JAVA_HOME above; .\gradlew.bat test --tests 'com.seggellion.britannia_mod.farming.*' --tests 'com.seggellion.britannia_mod.patch18.*' --tests 'com.seggellion.britannia_mod.woodenfence.*' --tests 'com.seggellion.britannia_mod.moongate.*' --no-configuration-cache --console=plain. Log tmp/gameplay-bugfixes/m0-smoke.log. BUILD SUCCESSFUL in 4m 2s; 172 tests, 166 executed passes, 6 skipped, 0 failures/errors, 27 suites (fresh XML totals verified).

M10 available local files freshly hashed: Iris1.8.0+mc1.21.1 SHA256 0e8ae2864f2ba144cc59fdb56a8ac00b88c0a96afcd986c54f3fb2b7dbcc81f5; Sodium0.6.0+mc1.21.1 fb178004f4a942735029c57ef5556bc3c83ce6c1d8344b6ecdfcc651148535f6; Photonv1.1 1228172bfb0ee49de3d2b6268390bac52c9a57dd0aa92c8903e57cf14583203a. Original config shaders=true/Photon, SRGB, shadow32, AOtrue, graphics1, mipmap4, view/simulation12, no resource packs. Historical Aug30 log Intel UHD/driver32.0.101.5972 on Windows11/i7-13620H; not a new rendered observation. Original JAR metadata at discovery was older a2f6391cdcc61fe8563545169af69145fd7d94f3/dirty=true; no M0 release artifact.

No client, physical input, multiplayer, natural world distribution or live Rails acceptance claimed. Next: finish focused baseline, commit M0 docs, implement M1 and meaningful registered server tests. Keep manual pending entries specific throughout.

## M1 work in progress

Preserved evidence: original discovery BUG-03/05/09 and supplemental deadline/ownership/bowl dispatch probes in original ignored tmp/gameplay-bugfixes. Implementing shared fertilizer eligibility/commit, saved prior state + paused budget, deterministic server tick/load reconciliation, canceled flower deadlines and actual-hand bowl care. New legacy policy: no invented deadline for old private/untracked soil; old timed community soil restores hoed with zero saved budget (normal subsequent preparation expiry), never receives five uses. Missing prepared metadata captures one tick, not a refreshed3600. Source changes and focused tests reviewed; results below.

### M1 review and evidence

Files: fertilizer item/community block delegate to FertilizedSoilService; FarmingBlock/BE add deterministic loaded ticks, live expiry checks and persisted PreviousHoedState/PausedPreparationTicks; CommunityFarm BE resumes saved budget; BowlWateringService + NORMAL interaction listener (after existing protection listeners) own the main phase and use the actual bowl-bearing hand; flower care recognizes bowls. Flower soil snapshots cancel forward deadlines, preserve rollback deadlines/origin, private owner and remaining uses. Owner data stays server-side; owner checks preserve environmental mutation rules. Paid house plots are excluded from expiry. Existing lifecycle/full-loop fixtures now register owned farmland temporarily instead of using prohibited ordinary dirt. No assets/resources/recipes/build source sets changed.

New registered GameplaySoilGameTests (9): all eight farmland moisture states ×1199/1200/1201, exact restoration/no BE entitlement; paused preparation budget and repeated fertilizer; registered loaded ticker with overdue saved state; flower forward/rollback/uproot and owner NBT; whitelist/modes/dimension/full-box/basement/missing authority; both-hand bowl event dispatch including redundant OFF callback/full/foreign soil; both-hand flower bowls/protected admin flower; full-inventory remainder world conservation; legacy private -1 fertility/no invented clock.

Commands from this worktree, JAVA_HOME=JDK21 as M0:
- `./gradlew.bat test --tests 'com.seggellion.britannia_mod.farming.*' --tests 'com.seggellion.britannia_mod.patch18.*' --tests 'com.seggellion.britannia_mod.bowlpreparation.*' --no-configuration-cache --console=plain`: m1-junit.log, passed in1m56s before final review.
- First combined invocation put `--tests` after runGameTestServer: rejected unsupported option before tests; m1-server.log preserved.
- Retry compile found FlowerColorDefinition versus FlowerColor in new fixture; corrected `.color()` accessor. m1-server-retry.log preserved.
- Combined correct command `test <same three filters> runGameTestServer --no-configuration-cache --console=plain`: m1-server-2.log, 1109/1110 server passes; one new negative failed because stock Minecraft mock overrides isCreative=true irrespective of selected mode. Replaced new fixtures with existing ManagedResourceTestPlayers.survival; no gameplay permission weakened.
- Same command m1-server-3.log: BUILD SUCCESSFUL2m40s, all1111 required GameTests pass; focused JUnit175 total,169 executed passes,6 skips,0failures/errors,28suites.
- Final review additionally rejects corrupt/uninitialized non-farm bowl targets and hoed-state PREPARED=false; private flower ownership does not change existing explosion/fluid rules. Final rerun m1-final.log uses same command with `-x processResources` (resources unchanged and already built normally; all Java recompiled, normal namespace only). Final result: BUILD SUCCESSFUL1m55s;1111 required GameTests pass and focused JUnit175total/169passes/6skips/0failures/errors.

Historical mock-login missing-auth/shard exceptions remain expected harness noise, not paid/live economy proof. No production credentials/config copied. BE serialization/recreation and an overdue saved deadline are not actual chunk unload/server restart. Simulation clock source cannot advance while server is stopped; no wall-clock arithmetic exists. Real normal/low-TPS timing, ordinary player preparation→planting/hydration, two-client inventory sync, actual chunk unload/reload and save/restart remain named M11 client checks. No client visual pass claimed. Review includes complete tracked diff and all four new Java files; diff --check clean.


Next: commit M1 locally, record its SHA in M2 entry, then implement three bowl recipes + seven pigments in both hands and compatible-first output. No client or live Rails evidence has been fabricated.

## M2 in progress

Preserved exact BUG-16/17 discovery count/hand/pigment traces at original tmp/gameplay-bugfixes/supplemental. Implementing role snapshots, MAIN phase gesture ownership with no per-tick cooldown, existing adapter commit boundaries, source-water precedence and component-compatible output merge before empty hand/inventory/drop. Implementation reviewed and validated below. M1 local commit f31550eefb215e198afc60a35dec355661a5f173.


### M2 review/evidence

Implementation: HandRecipeRoles stores actual driver/input hands and immutable full-stack snapshots; both bowl plan types expose driver hand and preserve live-main/live-off commit snapshots. Ambiguous recipe definitions reject; aliased physical input stacks reject. HandRecipeInteraction owns MAIN-phase matching in a NORMAL RightClickItem listener on both logical sides; OFF callbacks cannot commit, separate MAIN clicks remain allowed in the same tick. Existing item adapters delegate; matching source-water fill precedes dry crafting in either orientation. Block-target/protection dispatch is untouched. DyeTubItem loads the actual tub/pigment slots through existing plan/apply, preserves banner preview, and nonmatches pass. Server committed hand results broadcast to the open inventory menu.

BowlPreparationOutput now copies each output once, tops up component-compatible main/off stacks first, then uses a preferred empty hand (MAIN output, OFF returned bowls), empty normal inventory slots, and one remaining world drop. It never strips components or uses Creative Inventory.add force-clear. Partial capacity is used before dropping excess; legacy capacity helper remains for existing contracts. Existing source-fill output delivery inherits compatible insertion for non-exhausting fills; empty input replacement still returns the filled bowl as before.

New HandRecipeRolesTest:4 tests for actual role slots/immutable snapshots, equal-item deterministic roles/alias refusal, ambiguous recipe refusal, reversed final mix/stale components/canonical outputs. Updated old JUnit reversed-tuple expectations intentionally. Updated two legacy server suites to exercise ServerPlayerGameMode.useItem and expect reversed success while keeping invalid items free.

GameplayHandRecipeGameTests:24 recipe/hand/count series (3recipes×2hands×1/2/3/64 =420 crafts), OFF→MAIN→OFF callbacks for each craft, two compatible control stacks with final headroom plus an incompatible named control, exact final-mix2bowls per craft;7pigments×2hands×Survival/Creative with same-pigment no-op and preserved tub name; separate same-tick Creative clicks preserve paid bowl costs; source-water precedence in both hands.

Command (JAVA_HOME JDK21): `./gradlew.bat test --tests 'com.seggellion.britannia_mod.bowlpreparation.*' --tests 'com.seggellion.britannia_mod.bannerdyeing.DyeTubLoadingServiceTest' --tests 'com.seggellion.britannia_mod.farming.*' runGameTestServer -x processResources --no-configuration-cache --console=plain`. Ordinary source sets/namespace, unchanged already-generated resources reused; no diagnostic init script or namespace. m2-junit.log initial focused run BUILD SUCCESSFUL41s. m2-server.log BUILD SUCCESSFUL1m59s/all1114 required tests passed. Final review adds menu synchronization and source-water regression; m2-final.log BUILD SUCCESSFUL2m/all1115 required GameTests passed; source-water precedence verified both ways. Focused XML184total/178executed passes/6skips/0failures/errors/28suites.

Review: complete source/test diffs and all five new files inspected, no assets/resources/prices/build-source-set changes, diff --check clean. Prior full/near-full inventory/drop/Creative tests remain in registered suite. Sound/particle emission remains one call per successful adapter result by source proof; no physical audio or client packet-count capture asserted. Physical swapped-hand input, final inventory merge/reconnect, screen/preview/anvil/case controls remain named manual M11 checks. No live service or production action performed. Next: record final result, commit M2 locally, begin M3 case-specific ejection/atomic rotation.
