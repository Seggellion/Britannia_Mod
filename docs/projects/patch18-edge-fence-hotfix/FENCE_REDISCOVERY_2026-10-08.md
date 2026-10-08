# Fence runtime re-discovery — 2026-10-08

Result: **RUNTIME_ARTIFACT_MISMATCH_CONFIRMED (active client)**.
The authoritative remote server identity and new real-click/render acceptance
remain unverified. No application-behavior correction is justified by the
current evidence. The client observed during Stage A loaded the original normalization
code, despite sharing version 0.1.8d with the corrected candidates.

## Owner observations and limits

The owner reports an expected isolated edge and the opposite connected edge.
Only the supplied transcription is available in this session; the attachment
folder contains the request text, not the three images. No nonexistent chat
scratch/image path was used as a Windows path.

| Screenshot | Transcribed evidence |
| --- | --- |
| image(20261008-213951).png | Isolated panel and adjacent connected run appear at different edge alignments; no identity/details visible |
| image(20261008-214031).png | Owner 5251,72,4166; britannia_mod:wooden_fence; east=true, facing=north, north=false, south=false, west=false; minecraft:fences and minecraft:mineable/axe tags |
| image(20261008-214048).png | Owner 5249,72,4166; same ID/tags; east=false, facing=south, north=false, south=false, west=false |

Neither details crop shows layout_code. The samples have different owners;
they are not independently a before/after trace of one block. Those production
coordinates were not edited or used as disposable fixtures.

## Stage A — startup-identified artifacts

Canonical Patch 18 and live remote initially agreed at
71de2312a552ebf4dad3ecf147efd1de4bd815db. Tracked root was clean; owner's seven
untracked paths and unrelated worktrees remain preserved. Current application,
resources, build.gradle and gradle.properties have no diff from the clean
combined source 986ed746866dc944b7b496ab9428b670f65e15c0. Historical fence-only
07359f98 is an ancestor, not this investigation's baseline. Scoped task branch
codex/fence-runtime-rediscovery-2026-10-08 starts at current Patch 18.

Active process: javaw.exe PID 19556, start 2026-10-08 14:29:54.879863 PDT.
Actual --gameDir is C:/Users/dusti/curseforge/minecraft/Instances/UltimaCraft -
Britannia. Its 14:30:01 mod-discovery record names britannia_mod-0.1.8d.jar
from that instance's mods directory. Its 14:30:43 connection record names
sosaria.apexmc.co:25565; this is a remote world, not an integrated server.
The jar predates this process. Selected startup lines and exact binary evidence
are preserved in evidence/rediscovery-2026-10-08/ARTIFACT_COMPARISON.json and
OWNER_STARTUP_SELECTED.txt. Authentication launch arguments are not retained
in those diagnostic files; all subsequent inventory extracts specific fields.

| Artifact | Embedded source | Bytes | Full SHA-256 |
| --- | --- | ---: | --- |
| Startup-identified active client mods/britannia_mod-0.1.8d.jar | 4435912197fba84630d6e5a0544f29fc3a736450 | 36,107,339 | c04f3092fa577e4c98c4c4ee16febd5008403ccc98a0ebc064d860e3ac8981c6 |
| Existing clean combined -all candidate | 986ed746866dc944b7b496ab9428b670f65e15c0 | 36,142,668 | f02d2876d60e10a539829950b173b61055a72e6e42dd36643bec97ab62f482bd |
| Historical fence-only -all reference | 07359f98277417b8ffdafd9d57b55b828804ab5b | 36,140,545 | 89c3f936d2f942a0967ddc500542a8b0f587f5987a0902b2833596c099b486cb |

All report version 0.1.8d, dirty=false. Active build timestamp is
2026-10-06T00:15:17.175199Z; combined is 2026-10-07T17:43:56.051245600Z.
A filename or version match concealed the source difference. Mod-discovery
origin plus process start, unchanged old file timestamp, full hash and compiled
contents identify the client file; this is not a search for the newest jar.

There is exactly one active .jar containing BritanniaMod.class in that mods
folder. Four prior Britannia .old files are disabled and left intact. Resource
pack options are empty. Iris/Sodium/DistantHorizons/freecam/WorldEdit appear
in the owner's mod inventory; this session does not certify that modpack's
rendering. No evidence establishes a launcher restoring/syncing a newer jar
back to this one, so no launcher/infrastructure patch is proposed.

The existing read-only operator command is /grabby env, permission level 2,
registered by GrabbyDiagnosticsCommand. It reports artifact SHA/bytes/build
and server kind. No such output from the remote server is accessible here;
only artifact/build/server lines have been requested. Server binary/schema,
full authoritative live states and exact screenshot-time binary remain
unverified. The active client mismatch does not by itself identify that server.

## Actual schema and compiled correspondence

The old file's compiled WoodenFenceBlock registers facing and four connection
booleans (64 states), has resolvedRunEdge and no persistent layout selector or
WoodenFenceGeometry helper. Its compiled getStateForPlacement, updateShape and
tick call deriveConnections; the straight-run return writes FACING from
resolvedRunEdge. The fallback is NORTH for X runs, WEST for Z runs. The Git
44359121 source and actual javap disassembly agree on this assignment.
This accounts for the missing selector and can produce the reported opposite
edge when extending a south/east isolated owner.

Current source registers facing, layout_code=0..32 and four booleans (2,112
states), default layout_code=32. Plain item placement chooses concrete 0 or an
L selector; materialization captures legacy flags before changing them.
Ordinary derive/update/tick/load paths do not write facing or fixed layout
except sentinel materialization. Intentional rotation/mirror and the wooden
decorator transform the complete physical state. Other families are unchanged.

Combined and historical fence-only WoodenFenceBlock.class SHA-256 are both
5ac7749603856252ba8ea6af2bbbbb98a1fe1933e6e80f0d21e557e7257c9c45; both wooden
blockstate mappings hash to bc90ea245b16432510d3c096d8e8dced195b4f723e9de224c2b2415a82fbc17d.
Both have layout_code and omit resolvedRunEdge. This is compiled/resource
correspondence, not an inference from a source push. No remaining normalizer,
component/tool path or flag-driven model selector was found in current ordinary
placement/update paths. No current source defect has yet been demonstrated.

## Same-coordinate reproduction and acceptance boundary

The packaged-artifact comparison below uses a normally compiled, excluded
helper and actual BlockItem.place with resolved
contexts. Each case retains one owner A throughout isolated placement, neighbor
B immediately, scheduled tick, settling, B removal and settling. Four facings,
three click surfaces, both tangent extension directions and cardinal/20-degree
oblique looks give 48 constructed scenarios. Each record includes both owner
coordinates, full states, yaw/pitch, face/hit, target resolution, item components,
neighbors and A's outline/collision. A separate missing-property codec series
covers all 64 legacy samples; those are not fresh placement. Fixtures are saved
for another process to read at the same coordinates.

Computer use could not initialize: after kernel reset, node_repl reported
"windows sandbox failed: helper_unknown_error: setup refresh had errors".
No window/input operation was issued. This is a current helper failure, not
an obsolete Windows PIN hold or a new medallion/timing hold. Fresh client state,
mouse packets, actual rails/UVs/target outline and reconnect/F3+T visuals cannot
be claimed from these constructed server contexts. Existing 2026-10-07 matching
mouse/F2 records remain historical independent evidence, not this new run.

## Operator delivery steps — no deployment performed

First obtain /grabby env on the affected server to identify its loaded artifact.
For an eventual separately authorized deployment, retain a pre-upgrade world,
configuration and installed-artifact backup. Fully stop the server and exit the
actual CurseForge client process. Preserve the existing jar outside the active
mods scan; install exactly one matching bundled -all candidate on both sides,
using the full hash, not version or filename. The historical fence-only jar must
not replace the newer combined work. Check launcher's actual instance/profile
path and startup mod origin after restart, then /grabby env's server full hash,
source, dirty status and registered layout_code on a freshly placed disposable
panel. Do not treat copying bytes into a mods folder while the JVM still runs
as loading them. Already displaced saved panels retain their currently stored
layout; repair them only by deliberate rotation/replacement. Downgrade is not
certified as schema rollback.

Production acceptance retains the combined fence corner/decorator/art/reload,
paired-wall loaded appearance/outline and Alligator playback observations.
Medallion UI and sustained owner movement are accepted. Repeated startup/bake/
frame timing remains follow-up, not a new publication/acceptance barrier. This
reported owner-facing fence issue remains open until authoritative runtime
identity and the reported interaction on matched builds are confirmed. No live
server/client installation, production world, main branch/ruleset, release tag
or download publication was changed.

## Executed artifact comparison — new 2026-10-08 evidence

Normal Java 21 / Gradle 8.9 compilation produced the runtime helper; no manual
javac or compile exclusion was used. The test-only lowcode mod contains only
FenceRuntimeIdentityGameTests, ManagedResourceTestPlayers and the test template.
A task-local init script removes compiled application roots and separate
GeckoLib/nanohttpd entries from both SDK classpaths and clears MOD_CLASSES.
Thus the production registry/class/resources/build provenance come from the
specified mods jar, not the newly compiled source. Loaded-origin/hash/schema
records verify this for each process. The helper is never a release artifact.

- Copied active jar c04f3092: one registered required test fails after 48
  constructed scenarios. Exactly the 24 east/south cases reverse A; 24
  north/west controls retain it. Five failed transition checks per reversing
  case give 120 assertions recorded, not 120 registered tests. All records
  are preserved in OLD_ARTIFACT_TRACE.jsonl and OLD_ARTIFACT_RESULTS.json.
- Exact combined jar f02d2876: one registered required test passes; 48 fresh
  scenarios, zero divergence. Actual registered schema is 2,112 states,
  layout_code=0..32/default 32, expected implementation class and full loaded
  artifact provenance. All 64 missing-property codec states preserve facing,
  outline/collision through the first actual neighbor update.
- Independent resource lookup verifies the same A owner/facing/concrete 0,
  model asset/rotation, outline and collision in six transitions per fresh
  case (288 comparisons). All 64 sentinel-to-concrete legacy model selections
  are identical. These are model-selection/resource checks, not rendered UV
  screenshots. COMBINED_ARTIFACT_WRITE_RESULTS.json records the boundaries.
- Another normally launched JVM with the exact combined jar reads the saved
  48 fresh owners and 64 legacy fixtures: all 112 actual saved states match.
  One registered required restart test passes. This is actual process restart
  and engine state/shape evidence; no client reconnect/F3+T visual pass.
- Fresh current resource verifier passes 64 legacy mappings, unique coverage
  for 2,112 states, 1,536 UV/vertex transforms and seven authored mesh hashes;
  generator check passes 192 mappings without rewriting artwork/item.
- Normally compiled WoodenFenceBlock, WoodenFenceGeometry and
  WoodenFenceLoadHandler bytes are identical to the clean combined jar.

Concrete first divergence: case 12 owner A=20032,80,22008 is east while isolated
then west immediately after endpoint B is placed at 20032,80,22009. Case 24
owner A=20000,80,22024 is south while isolated, then north immediately after
endpoint B at 20001,80,22024. The same A remains displaced after B is removed.
The outline/collision moves from x or z [0.75,1] to [0,0.25], preserving 1.5
collision height but shifting the barrier. Neither example substitutes B or
another owning coordinate for A. Both extension directions and oblique looks
are included in the 24 failures. The combined jar keeps A and B on the original
edge in these identical contexts and retains A after removal/restart.

The old call chain is BlockItem.place -> getStateForPlacement(B) ->
deriveConnections(B) -> resolvedRunEdge -> setValue(FACING,NORTH/WEST), followed
by updateShape(A) -> the same normalizer and scheduled tick reconciliation.
Captured inputs and actual jar disassembly establish the wrong straight-run
assignment; a private-method live debugger was not used. No equivalent
ordinary-update assignment exists in the compiled combined implementation.

Prior tests did cover the old normalization defect. The original fence task
could not finish client acceptance, and later matching candidate checks did
not prove deployment to this CurseForge profile or the remote production
server. Version/filename stayed 0.1.8d. Passing current-source tests and pushing
source could therefore coexist with the old loaded client. This investigation
adds the artifact-origin/schema check and persistent same-owner traces; it
does not weaken the successful layout design or propose a speculative fix.

Setup limits: the first launch had an installed-Gradle path typo, corrected
before compilation. Synthetic-player missing-config/backend warnings are not
backend parity. The intentional old-jar red result is not a current-source
failure. Current application/resources/dependencies/version remain unchanged;
only the excluded regression and dated evidence are added. Final clean packaging
and relevant source checks are recorded below.


## Final clean candidate and new source gates

The source/evidence commit cf49b1700d1a8992f39396f2ae531f0cb26502d7 was
fast-forwarded from the unchanged canonical/remote 71de2312 baseline into
patch-18 and backed up by a normal non-force push. A fresh live remote query
confirmed cf49b1700d1a8992f39396f2ae531f0cb26502d7. The final documentation
closeout is a descendant; its verified final local/remote identities are saved
in canonical tmp/fence-runtime-rediscovery-2026-10-08/CLOSEOUT.json. No main,
ruleset/history, release/download publication or production installation changed.

Final deployable-format candidate, held for matched runtime/human acceptance:
`tmp/fence-runtime-rediscovery-2026-10-08/release/britannia_mod-0.1.8d-all.jar`.
The thin jar is not deployable.

- Bytes: **36,142,686**.
- SHA-256: **e64fe63c6ed0836f520e3d9b66ac7336b3e323677b63a433d65d48852c30fd70**.
- Version: **0.1.8d**; embedded source
  **cf49b1700d1a8992f39396f2ae531f0cb26502d7**;
  branch **codex/fence-runtime-rediscovery-2026-10-08**; **git.dirty=false**.
- Build timestamp: **2026-10-08T22:31:37.806305200Z**.
- All **10,698 ZIP entries** have the same names and bytes as the preserved
  October 7 combined candidate, except britannia_mod_build.properties.
  Alligator, medallion, plaster/wall, fence resources and bundled dependencies
  are preserved byte-for-byte. No GameTest class/helper/template is packaged.
- Initial ordinary compileJava executed in the clean task checkout, including
  this regression. `build artifactIdentity runGameTestServer` then executed
  the full JUnit suite: **4,129 total, 4,106 passed, 23 existing skips, zero
  failures/errors**, and **24 required focused fence/lifecycle GameTests passed**.
  Existing placement/support/corner/decorator/structure/transform and chunk
  boundary/load-order checks are included; this is not a fresh 1,366-test run.
- An additional explicit `clean build artifactIdentity` passed normal bundled
  jar/dependency/provenance gates. Gradle restored its normally generated
  compilation/test outputs FROM-CACHE in that clean build. No manual javac,
  manual application packaging or `-x compileJava` was used. The earlier actual
  compilation/test execution and exact entry comparison are retained separately.
- A separate JVM loads the exact final e64fe63c candidate, not project build
  classes: actual origin/hash/source/default/schema are captured again.
  **One required packaged restart GameTest passes all 112 saved fixtures**;
  independent trace comparison also matches all 112 full states, outlines and
  collision shapes against the earlier exact combined restart. The combined
  48 placement/64 decoded-legacy results apply to byte-identical application
  and resource entries; they are paired evidence, not a claim that this final
  hash separately reran the 48 placement sequences.

Full machine-readable candidate/gate/final runtime records are in
`evidence/rediscovery-2026-10-08/FRESH_CANDIDATE_IDENTITY.json`,
`SOURCE_GATES_RESULTS.json`, `FRESH_CANDIDATE_READ_TRACE.jsonl` and
`FRESH_CANDIDATE_READ_RESULTS.json`. The earlier regular-build intermediate
039726d12cffe791f7d41b1b95e3ec703f2ce0dfb7b7844b7c95231b37ce4968 is retained
under the task release/preclean-intermediate directory with its own identity
and matching 112-fixture trace. It does not overwrite the historical comparison.

## Retention and remaining action

At the 15:33 PDT recheck, the previously observed client PID 19556 was absent
and no javaw client was listed. Its CurseForge installed jar still hashed to
c04f3092fa577e4c98c4c4ee16febd5008403ccc98a0ebc064d860e3ac8981c6. The task did
not stop/change that process or replace the jar. This result identifies the
observed startup/session, not a promise about any later independently launched
session or the inaccessible remote server.

The task branch and checkout at
`tmp/fence-runtime-rediscovery-2026-10-08/source` remain available, with the
old/combined/final disposable worlds, fixed-coordinate fixture manifest and
packaged helper recipe. They are needed for the remaining reported-interaction
acceptance and are deliberately retained. Candidate/identity, compressed raw
build/runtime logs, disassembly and helper recipe are also preserved outside
that checkout under the task release/evidence directories. Test servers exited
normally; unrelated worktrees and owner untracked files were not cleaned.

The next missing input is only the affected server's existing read-only
`/grabby env` artifact/build/server lines. Then use the final clean candidate
above (or the preserved byte-identical combined application), deliberately
match both sides by full hash and startup origin, and record fresh same-owner
client/server placement/removal/reconnect observations. The operator steps
above are preparation; deployment/restart still requires separate authorization.
Already displaced legacy blocks cannot be guessed backward. Remaining combined
human gates and this owner-facing fence complaint stay open.
