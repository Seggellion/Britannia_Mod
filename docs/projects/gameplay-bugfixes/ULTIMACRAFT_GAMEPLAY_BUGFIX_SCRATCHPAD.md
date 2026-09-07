# Gameplay bugfix implementation scratchpad

Current: M0 complete; next M1. Authorized execution: M0–M11, local commits, no push/merge/deploy/production writes or Fabric work. Playbook and kickoff supersede historical discovery recommendations. All five authoritative documents read in full before code edits.

## Workspace and frozen contract

Original mod: C:/projects/britannia/mod/Britannia_Mod, patch-18 at 421e27853dde4099d1d794568e33e6709507a53b. No divergence (0/0). Tracked files clean. Existing untracked farming/Rowan/playbook/discovery documents preserved. No applicable AGENTS.md on ancestor chain, root, .claude or .claude/worktrees. Remote origin https://github.com/Seggellion/Britannia_Mod.git. No network git operation performed.

Implementation: C:/projects/britannia/mod/Britannia_Mod/.claude/worktrees/patch18-gameplay-bugfixes, codex/patch18-gameplay-bugfixes, same initial SHA. Other worktrees preserved: rowan-farmer-exploration-97e04a (0cd566dd811bb717c6bda498ac3bed38be64b95e), rowan-farming-questline-mod (45ad043458491a134c46028c43115362fcf83bf5), patch18-m2 candidate (625269d3bb5abb560a901f7e9198abe910eb482a), public-release (5c0b917226848192e40b376cae719057e70cd662), starfarer-m11 (7a4d2fc768c38969eec7e7be9412974c3ab2ff2f). Rails inspection/worktree deferred until M7.

Decisions: 1200 online game ticks, exact hoed restoration, unused fertilizer forfeited, paused preparation budget; prepared community or actor-owned-house empty farmland only; bowls +1 and custom bowl remainder; planting/harvest species equality including grapes/flowers; harvest min(.95,.75+.01*surplus), destructive failure/cost/practice and lifecycle, free deterministic admin success; native cultivation outside custom unchanged; only three landscape features removed; all 36 approved produce gaps; role-based two-hand recipes and compatible-first outputs; offhand exact case ejection/mainhand whole-case rotation; deterministic fence, 1.5 collision and narrow dirt-path exception; Creative substrate bypass with technical validity. BUG-04/06/07 excluded. Full-can artwork user supplied; no generated assets. Reported vendor Jhelom/Britannia. Old Q1–Q5 are closed by playbook; live evidence still must be observed.

## Milestones

| Milestone | Status | Start / end |
|---|---|---|
| M0 workspace, baseline, contract | PASS | 421e27853dde4099d1d794568e33e6709507a53b / pending |
| M1 soil, expiry, bowls | PENDING | |
| M2 hand recipes/output | PENDING | |
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

