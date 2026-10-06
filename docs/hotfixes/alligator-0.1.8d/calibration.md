# Calibration and runtime evidence

Pre-fix production source: 4435912197fba84630d6e5a0544f29fc3a736450. AI enabled, registered Alligator, seed 18, inherited JUMP/FloatGoal retained; forced cases remove competing MOVE/TARGET only. Fixture 16×12×16 encloses all tank blocks. Commands use tools/alligator/run-isolated.ps1 and fresh ignored worlds.

| Baseline | Actual result |
| --- | --- |
| Four-block descent, tick 200 | Y=-51.445 vs -57; error 5.555; path unreachable; yya=0 |
| Diagonal, tick 250 | Y error 5.739; path unreachable |
| Depth travel, tick 250 | Y error 4.714; path unreachable |
| Ascent, tick 250 | Eyes surfaced through FloatGoal, lateral error ≈7.14; does not reach requested point |
| Eight-block dry land, corrected exact node request | 40 ticks to physical endpoint tolerance 1 block; actual remaining distance ≈0.955 |
| Twenty entities / 1,200 ticks | Baseline-matrix: median wall tick 11.552 ms, final rolling server average 12.817 ms. Baseline-exact: 8.640 ms / 9.069 ms. Machine/cache/GC variation is material. |

The initial land failures were fixture errors: default path accuracy 1 permits a short endpoint, and width 1.6 adds +1 to node X/Z. The retained comparison requests the node with accuracy 0 and measures the physical eight-block destination; final gate ≤44 ticks and ≤250 absolute. Raw baseline.log, baseline-matrix.log, baseline-land.log, baseline-exact.log retain both failed diagnostics and the corrected measurement.

Focused iteration uses the exact Gradle compile classpath and Java 21 javac for modified classes, then runGameTestServer with compileJava/processResources excluded because outputs were explicitly compiled and unchanged assets retained. Complete final gates must use normal Gradle compilation/resource generation with no such exclusions.

Post-fix measurements and survival constants: pending calibration.
