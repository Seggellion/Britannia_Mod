# Fence hotfix milestone evidence

## M0 — baseline and isolation

2026-10-06: current `patch-18` c8044e4565281514253e290167c555413c7a5293,
unchanged from discovery. Tracked root clean. Owner's five kickoff/playbook
files and discovery directory preserved. Isolated task branch
`task/patch18-0.1.8d-edge-fence`, checkout `tmp/edge-fence-hotfix`.
No applicable AGENTS.md found in the repository or its ancestors/relevant
source/tool/docs paths. README packaging gate and M8/M11 fence/release
evidence read. Release remains held for medallion visuals.

Live `git ls-remote origin refs/heads/patch-18` returned
c1f2b5b819834ab971a2fc65b86fcd484d31e7c9 (local baseline ahead eight).
No fetch/reset/rewrite; `main`, Patch 19 and other worktrees untouched.
Version remains 0.1.8d. Explicit Temurin 21.0.9+10 for Gradle; installed
Gradle 8.9. Normal NeoGradle application compilation, no probe source set,
init script, manual javac or compile exclusion. Task runner removes service
credential environment variables without logging their values; task worlds
are restricted to `tmp/edge-fence/` within the isolated checkout.

Legacy resource mappings and seven authored mesh hashes captured before
edits. Discovery trace copied unchanged; SHA-256
fb97fb4fb4b5f8c102fe755196267f5f4adea8199974cf184692706326d4cd31.

## M1 — regressions

Five registered GameTests added: four facing tests, each exercising six
constructed BlockItem scenarios (three surfaces × two orders), and one
saved-corner first-update regression. They use ordinary `BlockItem.place`
and scheduled ticks; they do not claim client raycast/packet evidence.
Normal red run log: `tmp/edge-fence/red.log`.
Old convergence/corner/run tests are being rewritten around equivalent
fixed neighborhoods and fixed intent; collision, path and player controls
remain acceptance contracts.

Red run (normal compilation, 5m10s): five registered tests; north/west pass,
south/east fail with reversed facing, and saved corner fails because its
second strip disappears. Three required failures, exact intended reasons.
Initial green attempt had a test-edit brace error, corrected before runtime.
`green-2.log`: full JUnit 4,121 tests, 4,097 passes, 24 skips, zero errors/
failures; 12/13 fence tests pass, one old topology assertion corrected to
allow a real perpendicular terminal contact without creating another panel.
`green-3.log`: same full JUnit result, 19/20 fence tests pass. Patio reader
incorrectly assumed gzip; both existing fixtures are uncompressed NBT.
The reader now recognizes either format; the resources were not rewritten.

## M2–M4 — fixed layout, connections, placement

Reference schema implemented. Normal client/server dependencies unchanged.
`python tools/scaffolding/gen_wooden_fence.py --reflections --check` verifies
idempotent mappings/reflections. `python tools/edge-fence/verify-resources.py`
passes: 64 frozen old resource mappings before/after materialization, all
2,112 states uniquely covered, 1,536 geometric vertex+UV transform checks,
seven authored hashes (LF normalized for Git checkout endings).

`focused-lifecycle-write.log`: normal compile/run succeeds in 3m17s; all
22 registered required tests pass (20 fence + two lifecycle), in 7.831s of
GameTest execution. Includes 24 constructed BlockItem straight scenarios,
four rotated sequential 4×4 enclosures and corner replacement/removal,
support-intent conflicts/replaceable targets, independent old shape oracle,
actual missing-property BlockState codec, serialization, 20 fixed-point
rounds, 128-layout transforms/contact symmetry, dirt path/vanilla controls,
Survival/Adventure collision, both real patio resources and decorator use.
No registered tests are counted from their internal scenario totals.

## M5 — lifecycle

Write process saves 64 layouts to actual Anvil chunks. Separate read process
is running against the same disposable world. Boundary test already passed
actual cache eviction followed by first-owner-only load and second-owner
load at X 121615/121616 and Z 123215/123216, both orders. It proves querying
the partially loaded owner does not acquire the other full chunk; geometry
remains pinned and contacts return when both owners become available.
This is actual chunk eviction, not a synthetic ChunkEvent or inspect-only
call. Forced/player tickets were not added for these remote fixture chunks.

Read process `focused-lifecycle-read.log` succeeds in 3m33s: all 23 registered
required tests pass in 7.779s (21 fence + two lifecycle). All 64 saved physical
layouts survive the separate JVM restart. All four boundary eviction/load
cases pass again. New bounded-access proxy also proves exactly four
availability checks, zero reads with unavailable chunks, four loaded reads,
no world writes or neighbor materialization. Explicit selector and mutable-
flag independence assertions were subsequently strengthened for final gates.

## M6 — complete gates and cost/client evidence

Implementation commit: 0fe906d7699a5e227467faa343d5438948969baa.
`full-gametest.log` normally compiled the application, completed all 1,362
required GameTests in 3.021min, and failed two shared-scheduler ore fixtures:
`anUnloadedChunkIsNeverLoadedToRestoreIntoIt` and
`curatedCoalIsPlacedMinedAndComesBack`. These are the same two fixtures
documented in the prior Alligator full-run interference. No fence failure.
Fence tests now use a dedicated `edge_fence` batch; lifecycle uses
`edge_fence_lifecycle`. All assertions/counts and ore sources remain intact.
Full clean-source rerun follows this harness isolation change.

Unchanged c804 baseline full JUnit (`unit-baseline.log`) passes in 3m35s:
4,120 total, 4,096 passes, 24 skips, zero errors/failures. Hotfix adds one
JUnit contract (authored hash protection) and no skips. Baseline client
was normally built/launched, observed at main menu, and quit through UI.
Three post-GC heap histograms have identical model/state/cache counts;
timing and hotfix comparison will be recorded in the cost ledger.
