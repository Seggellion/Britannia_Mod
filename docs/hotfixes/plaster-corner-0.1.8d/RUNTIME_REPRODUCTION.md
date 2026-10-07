# Remaining checks and isolated reproduction

Use Minecraft 1.21.1 / NeoForge 21.1.72 / Java 21 and the preserved bundled candidate with SHA256 `de563ecfcb8f9067248ec09ae0578f7776ea5c1beefa4f1c182b9ac255a5df2e`. Never use live worlds. The existing disposable `client-resume` world and `server-resume/world` are retained. Close the relevant world/server before copying or reading its saved regions. The region-writing forced-state helper must ONLY run on a closed copy under its guarded client-resume path; retain its before-forced backups. Do not rerun setup over the retained evidence world.

The client SDK launch is `tools/plaster-corner/run-isolated.ps1 -GradleTasks @('runClient','-Pdev','--init-script','tools/plaster-corner/packaged-client.gradle') -RunDirectory tmp/plaster-corner-0.1.8d/client-resume`, using the pinned installed Gradle 8.9 and Java 21. This SDK smoke strips project classes/resources, MOD_CLASSES and separate bundled dependencies, loading only the candidate in that run directory's mods. `-Pdev` is a launch setting; it is not a deployable build bypass.

The retained [command pack](implementation/acceptance-resume/commands/) contains exact fixtures, camera commands, per-half assertions and the NBT structure template. To reproduce on a NEW isolated copy, install that pack, `/reload`, and use the individual functions below. Preserve the reviewed world before replacing any sample.

## Actual decorator reproduction

1. `/function plaster_probe:minimal_reset` clears the sample at (48,80/81,84), creates four two-height blank-arm cells and puts the support BlockItem in the main hand. At the supplied camera, right-click the floor once to PLACE the corner. `/function plaster_probe:pair` must log south/corner/branch_false/mirror_false for lower and upper. This step requires an actual BlockItem click.
2. `/function plaster_probe:mirror_upper` sets empty main hand, decorator OFFHAND and targets upper Y81. Right-click once. Wait for scheduled AFTER marker or run `/function plaster_probe:pair`. Lower stays mirrored=false; upper becomes=true. F3 reports the targeted upper state. F2 records actual geometry, which still follows the lower state through both heights.
3. `/function plaster_probe:physics`; wait at least20ticks, then `/data get storage plaster_probe:acceptance physics`. Compare with PHYSICS_UPPER_ONLY_MIRROR.json. Actual engine probes approach externally; do not use the earlier embedded-start attempt as evidence.
4. `/function plaster_probe:normalize_pair` sends a normal neighbor update. The mirrors remain different. Repeat with mirror_lower and rotate_lower/rotate_upper, with one actual click each. Pair markers report the state, not a visual/physical pass.

## Renders, placement and transforms

`view_00`..`view_25` observe the retained legal forced grid. Full cases have exact LOWER AND UPPER assertions; half cases have no invented mirror/half properties. `acceptance_view_00`..`acceptance_view_30` observe architectural joins without writing blocks. Use an individual camera, wait for its scene marker and settled render, hide HUD and F2. Match the native screenshot with the preceding log SCENE marker; timer-request indices alone are unreliable. The first E/W occluded fixture and first lower-only gate fixture are invalid and explicitly excluded.

`normalize_east` applies a floor neighbor notification to forced case 05. Its E/W state normalizes to N/S; do not call that an E/W placement pass. `place_corner_first` needs an actual support BlockItem click BEFORE `check_corner_first` adds the arms. `place_reverse_arms` prepares north arms then west arms BEFORE an actual corner click; `check_reverse_arms` asserts the result. `structures` performs all twelve ordinary vanilla template transforms; `structure_states` logs both halves after normalization. `neighbors` includes removal/re-addition of the north arm in its mutations fixture; `fix_gate` builds valid two-half wooden_gate neighbors and asserts the resulting north/straight center. These helpers change only their isolated fixtures.

## Missing sustained player interaction

The supported UI key API sends a key chord with no hold duration. Actual W/Space attempts did not move the player in the per-tick coordinate recording, so sustained walking/jumping is **NOT PASSED**. The engine item probes establish the collision defect, but do not replace this player checklist.

On a fresh copied world, use `walk_normal_south`, `walk_normal_east`, `walk_phantom_east` and the retained recording functions after checking their camera/target coordinates. Hold W for at least one second and capture video/F3 coordinates; repeat Space+W jumping and crouch approaches. Test both physical arms on the normal corner at (60,80,12), then the divergent pair at (48,80,84) from east and west, with lower-only and upper-only mirror. Record exact per-half state and candidate identity with each run. The upper visible branch and opposite invisible collider should be included, not just a frontal lower hit. Repeat affected facings/branches after a future repair before clearing acceptance.

Owner-specific resource packs/shaders were not supplied. Current disposable options contain `resourcePacks:[]` and no shader mod is loaded. If owner overrides exist, record their names/versions/settings and repeat junction, free-end, cap, both heights and mirrored/half checks with those exact overrides. Default-pack results cannot certify an unknown override.

## Dedicated persistence reproduction (completed)

Run the same isolated runner with tasks `@('runServer','-Pdev','--init-script','tools/plaster-corner/packaged-client.gradle')`, run directory `tmp/plaster-corner-0.1.8d/server-resume`. The retained test server is bound to127.0.0.1:25579, offline, with a COPY of the closed client world and only the verified candidate in mods. No private service credential configuration is present; the runner strips live credential prefixes.

After Done, `forceload add -8 -8 124 140` loads all 90 fixture chunks; run `function plaster_probe:pair`, `function plaster_probe:structure_states`, `save-all flush`, `stop`. Read saved states only after normal exit. Restart this SAME world; `forceload query` confirms90 retained chunks, repeat assertions/save/stop, compare dumps. Both executed runs exit0 and all 54 fixture rows (including their eight neighbors where applicable) compare exactly. Logs and PERSISTENCE_RESULTS.json retain actual identities and commands. No network-client/dedicated multiplayer interaction is claimed.
