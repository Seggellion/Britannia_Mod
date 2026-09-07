# Gameplay bugfix handoff (in progress)

Verdict: IN PROGRESS. Base and workspace identity, frozen decisions, milestone commands/results and commit ledger are maintained in ULTIMACRAFT_GAMEPLAY_BUGFIX_SCRATCHPAD.md. This file will be finalized at M11; M1 implementation has server evidence below; manual acceptance is recorded separately.

| Issue | Status | Milestone |
|---|---|---|
| BUG-01 | Pending shader reproduction | M10 |
| BUG-02 | Pending planting feedback/HUD | M5 |
| BUG-03 | Implemented; server proof; physical timing/reload pending | M1 |
| BUG-04 | EXCLUDED user lettuce artwork | — |
| BUG-05 | Implemented; both-hand server/conservation proof; client pending | M1 |
| BUG-06 | EXCLUDED user green-onion artwork | — |
| BUG-07 | EXCLUDED user green-onion artwork | — |
| BUG-08 | Pending harvest gates/outcomes | M4 |
| BUG-09 | Implemented; owner/target server matrix; client pending | M1 |
| BUG-10 | Pending Creative substrates | M9 |
| BUG-11 | Pending fence convergence | M8 |
| BUG-12 | Pending fence collision | M8 |
| BUG-13 | Pending path retention | M8 |
| BUG-14 | Pending charge state; user asset required | M5 |
| BUG-15 | Pending landscape suppression | M6 |
| BUG-16 | Implemented; server hand/pigment matrices; client pending | M2 |
| BUG-17 | Implemented; count/component/final merge server proof; client pending | M2 |
| BUG-18 | Pending full produce mapping/catalog; Jhelom/Britannia live evidence | M7 |
| BUG-19 | Pending case transactions | M3 |

Final delivery will include milestone SHAs in both repositories, exact automated/manual evidence, clean normal JAR SHA/size/embedded HEAD/dirty flag/dependencies, changed files/status, rollback and existing-world notes. No push, merge, deployment, production seed/migration/data/world change, server JAR replacement or Fabric work authorized or performed.


## M1 evidence

Soil fertilizer now accepts only prepared community plots or empty farmland within the actor's registered house. One saved online game-time deadline restores the exact previous hoed state and forfeits unused fertilizer; remaining community preparation time resumes without a fresh budget. Flower conversion cancels the empty deadline and preserves soil ownership/uses through rollback, care and uproot. Bowl water adds one hydration, consumes the actual hand once and returns one custom bowl; full/protected targets do not spend it.

Focused JUnit:169 passed/6skipped (175 total). Final server run:1111 required GameTests passed; BUILD SUCCESSFUL1m55s. Final focused JUnit169passes/6skips. Commands and earlier fixture corrections are recorded in scratchpad. Existing five-harvest lifecycle/full loop remain passing with authorized owned-farmland fixtures. Full inventory drops exactly one bowl remainder. Build outputs here are diagnostic, not the M11 deliverable.

Existing-world notes: old private soil without timer/fertility tags remains untimed/untracked(-1); old timed community soil with no origin restores hoed soil with no invented preparation budget. Missing prepared time captures one tick, never3600 free ticks. Occupied crops/flowers and paid house plots do not expire. No chunk scan/migration/cleanup is required. Private flower owner metadata is optional for legacy saves and excluded from client tags. Reverting the code would ignore new origin/owner fields and restore the former incorrect timer/eligibility behavior; preserve world backups before any separately authorized release.

Pending actual clients: standard/low-TPS sixty-second timing, chunk unload/reload, server stop/start, ordinary farming and hydration, two-client remainder/synchronization. Server fixtures are not those observations.


## M2 evidence

Three bowl recipes and seven tub pigments now use actual ingredient roles in either hand. The main callback owns a matched gesture; a redundant offhand callback cannot craft twice, while separate clicks in one tick still work. Compatible outputs/remainders merge first, preserve meaningful components, and use available capacity before one excess drop. Creative bowl costs and dye pigment exemption remain unchanged. Invalid nonmatches pass to established item behavior; source-water and block-target precedence remain.

Focused JUnit178passes/6skips (184total); final registered GameTests1115passed, BUILD SUCCESSFUL2m, including the both-hand source-water regression. The24bowl series execute420crafts with both hand orders, counts1/2/3/64, compatible-headroom and named-incompatible controls. Seven pigments in both hands and both modes preserve tub metadata, costs and same-pigment no-op. Existing full/near-full capacity cases remain passing. Final command/result and M2 commit are in scratchpad.

No item NBT migration is needed. Reverting M2 restores restricted drivers and final-output hand placement; outputs already made are ordinary unchanged registry/component stacks. Physical input/reconnect, inventory display, source targeting and dye-preview/anvil/case client controls remain pending; server tests do not certify those observations.
