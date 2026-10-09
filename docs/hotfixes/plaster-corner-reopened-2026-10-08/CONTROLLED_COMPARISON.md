# One short paired comparison — prepared, not visually accepted

No desktop automation. No owner building/world edits, mod replacements, settings changes or owner client/server restarts were performed. The disposable discovery server is **stopped**. Its world/fixtures and verified candidate are preserved at:

```text
C:\projects\britannia\mod\Britannia_Mod\tmp\plaster-corner-0.1.8d\reopened-discovery-20261008
```

The mod in that run is the unchanged36,142,668-byte f02 candidate from clean 986ed746. Actual startup identity is recorded in [RUNTIME_SUMMARY.json](evidence/RUNTIME_SUMMARY.json). A compact comparison needs an independently verified **candidate client**, because the selected CurseForge client currently contains the earlier44359121 JAR. A dedicated server loading f02 does not replace the client’s art/code. Launcher/install/boot preparation is separate from the approximately 2–5-minute observations; no time estimate assumes a client is already loaded. Later operator-run isolated setup is permitted; this document does not authorize a production deployment.

## Prepared scene and actions

Operator headless restart command, from the verified repository root (no UI/focus/input automation):

```powershell
& tools/plaster-corner/run-isolated.ps1 -GradleTasks @(
  '-p','tmp/combined-0.1.8d/candidate-source','runServer',
  '-I','tools/plaster-corner/packaged-client.gradle',
  '-x','compileJava','-x','processResources','-x','jar','-x','jarJar',
  '-x','neoFormJoined1.21.1-20240808.144430DownloadAssets'
) -RunDirectory 'tmp/plaster-corner-0.1.8d/reopened-discovery-20261008'
```

This reuses the pinned SDK and skips application compilation/packaging. Earlier `--offline` attempts failed in SDK cache resolution; known successful launch uses public dependency resolution. Do not call a setup error an application failure. Before any operator launch, verify this run contains only the expected JAR and no credential-bearing Britannia server properties. Bind is 127.0.0.1:25579. Console commands do not take control of a desktop. No other server should be stopped.

Operator console: `function reopened_plaster:setup` restores **blank arms** and the requested states; `function reopened_plaster:capture` emits exact/normalized state markers. `function reopened_plaster:normal_plots` prepares empty-center genuine placement plots if later required; these were not used for player-placement evidence. Grant disposable permission only to the local tester if needed, then join using a candidate client under the owner’s control. Run `grabby env` independently in that candidate client/server and retain source/hash identity; do not rely on displayed0.1.8d version alone. The latest saved world currently contains support-arm replacements and lone-half probes; setup returns the paired comparison to blank-arm controls.

| View | Center / exact intended saved pair | Edge-run junction | Expected observation to report |
| --- | --- | --- | --- |
| A | `(8,80,8)` lower and`(8,81,8)` upper; south,corner,branch=false,mirror=false | `(9,*,9)` | Timber at exterior SE joint through both heights; do not mistake northern free end for joint. |
| B | `(20,80,8)` lower and`(20,81,8)` upper; south,corner,branch=true,mirror=false | `(20,*,9)` | Timber at exterior SW joint through both heights; separate eastern free-end post. |

**Single short checklist (once setup/client identity is ready):**

1. View A with `/function reopened_plaster:view_00`, then B with `/function reopened_plaster:view_01`. These are exterior oblique viewpoints. Record whether junction timber is visible continuously below/aboveY81, with no plaster gap, wrong side or neighbor-owned substitute. One full A/B image pair or observations suffices; the entity displays at Y84 are labeled **forced art/no wall collision**, and are not normal-placement proof.
2. Look from the **inside** of each L toward the same junction: A camera approximately`(7.2,80.8,7.2)`, yaw−45/pitch0; B approximately`(22.8,80.8,7.2)`, yaw45/pitch0. Compare the expected architectural support with the exterior result. Report whether the intended design requires timber on this interior view; the analytical post-center ray is covered by plaster there. Inspect both lower/upper visible sections. Do not declare this correct/incorrect from the presence of a post in JSON.
3. If blank-arm A/B is acceptable, operator switches `function reopened_plaster:support_arms`, captures resulting states, and the owner looks once more from the same two sides. Report whether neighboring timber hides a missing target post, causes duplicate timber/seams or changes the accepted appearance. Identify the original owner crop as **A defective**, **B defective** or another issue. No movement/medallion repeats.

This is observation only; no lengthy placement/decorator campaign is needed to label the new failure. If the candidate still fails, retain the exact view, actual states and identity and use the proposal’s targeted M1–M3 gates. If launch/setup cannot be prepared, a read-only full view of the existing owner A/B and its good/bad label can still narrow the cause, but cannot certify the candidate.

## Prepared distinctions and further discriminators

The24-case blueprint, captures and view commands are stored in [fixture/](fixture/), with exact requested states, physical edges, arm coordinates and cameras in [FIXTURE_MANIFEST.json](evidence/FIXTURE_MANIFEST.json). Full cases 00/01 are the exact south A/B pair; remaining full cases cover branch/mirror/facing combinations;16–23 are the legal half selectors. Both visible heights are supplied by each lower full model; upper block has air art. Displays at Y84 bypass updateShape and have no collision; connected builds at Y80 use ordinary setblock notifications. Neither is a player BlockItem action.

Four E/W half cases normalize in the two-arm scene; [SAVED_BLANK.json](evidence/SAVED_BLANK.json) records actual states. Separate `lone_half` command creates eight straight-seed/perpendicular-full-wall scenes at Z100/108 and ordinary base notifications; all eight corner selectors passed, including E/W. These establish a normal derivation path from a straight seed, not actual mouse placement. Source upper/lower source pins and current loaded log identity accompany all state evidence.

If true player reachability needs a targeted check later: prepare empty plotsA`(8,80,84)` with west-south/north-east blank arms andB`(20,80,84)` with east-south/north-west blank arms. Look **north** when placing the full BlockItem so its initial facing is south. Inspect both halves after placing, then repeat each arm order in separate disposable plots, recording every state notification. Mirrored cases require appropriate decorator/structure construction and physical-arm XOR, not an invented mirrored half property. Exact forced states may normalize legitimately.

If actual owner state/ownership remains necessary, capture **read-only** both owner center blocks and their north/east/south/west neighbors at Y72/Y73 plus below/above; include complete properties, block IDs, connecting/terminal tag definitions and a full exterior/interior view. Use the existing target/state overlay or authorized server-side read-only inspection; do not set/replace/update blocks in that building. Obtain server `grabby env`/loaded artifact metadata separately if accessible. Earlier build date or placement age alone does not identify cause.

If a saved candidate pair fails visually after a repair, a single targeted reconnect/resource reload observation belongs in M3. A reload checks new resource baking; replacing mod code requires a process restart. Do not request another long client campaign or repeat previously accepted owner sustained movement/medallion UI.
