# Gameplay bugfix handoff (in progress)

Verdict: IN PROGRESS. Base and workspace identity, frozen decisions, milestone commands/results and commit ledger are maintained in ULTIMACRAFT_GAMEPLAY_BUGFIX_SCRATCHPAD.md. This file will be finalized at M11; M1 implementation has server evidence below; manual acceptance is recorded separately.

| Issue | Status | Milestone |
|---|---|---|
| BUG-01 | Pending shader reproduction | M10 |
| BUG-02 | Implemented; packet/commit/status server proof; graphical HUD pending | M5 |
| BUG-03 | Implemented; server proof; physical timing/reload pending | M1 |
| BUG-04 | EXCLUDED user lettuce artwork | — |
| BUG-05 | Implemented; both-hand server/conservation proof; client pending | M1 |
| BUG-06 | EXCLUDED user green-onion artwork | — |
| BUG-07 | EXCLUDED user green-onion artwork | — |
| BUG-08 | Implemented;74-species policy/server routes; client pending | M4 |
| BUG-09 | Implemented; owner/target server matrix; client pending | M1 |
| BUG-10 | Pending Creative substrates | M9 |
| BUG-11 | Pending fence convergence | M8 |
| BUG-12 | Pending fence collision | M8 |
| BUG-13 | Pending path retention | M8 |
| BUG-14 | State selection implemented/tested; PENDING_USER_ASSET for full artwork/display | M5 |
| BUG-15 | Implemented; exhaustive loaded-biome/native/structure server proof; normal-terrain/restart checks pending | M6 |
| BUG-16 | Implemented; server hand/pigment matrices; client pending | M2 |
| BUG-17 | Implemented; count/component/final merge server proof; client pending | M2 |
| BUG-18 | All36 additions/catalog/HTTP routing implemented; real local quote/sale/refund/replay proof; response-loss recovery acceptance OPEN; production pending | M7 |
| BUG-19 | Implemented; server transaction/gesture matrix; two-client pending | M3 |

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

## M3 evidence

Case-specific MAIN-phase dispatch now owns decorator use on either cell. Offhand-only ejects the exact stored stack to a collision-free world position after confirmed insertion; mainhand/both tools rotate the complete pair with rollback. Root locking and live identity/content checks prevent reentrant duplication and stale clearing. Existing world permissions and case-neighbor policy remain in force. No NBT or artwork change.

Final focused JUnit9passes; all1118 registered required GameTests passed, BUILD SUCCESSFUL1m56s. New320gesture matrix includes all16neighborhood patterns, both cells, sneaking, air/stick/food/placeable/decorator main items, full inventory and exact legacy count/components. Rejected spawn with second-player reentry, two-player repeat, malformed/denied cases, rejected/throwing upper rotation and success/failure disk serialization pass. An old connection-teardown regression was caught and corrected before the final suite. Detailed command/log evidence and commit ledger are in scratchpad. Physical hand input and two-client ghost-display/synchronization acceptance remain pending.

## M4 evidence

All67crop and7flower definitions now use the same planting/harvest requirement, including Farming80 for both grape entrypoints. One server outcome draw after live root/maturity/tool/rights/readiness checks implements75% at threshold, +1% per extra point, capped95%. Failed eligible attempts destroy yield/byproducts, pay one normal use/tool cost, run existing practice, and follow annual/perennial/fruit/tree lifecycle. Creative/operator-level2 has free deterministic success and no outcome draw/practice. Native crops outside custom soil are unchanged. Poppy's separate knife/Farming100 advancement remains.

Fruit/tree and flower harvests now share finite-use accounting. Economic tree felling requires ripe fruit and still removes the tree on eligible failure with no byproducts. Environmental/support cleanup remains separate. Flower quality is bounded to its persisted1..100 domain. Success is exposed through one post-commit FarmingHarvestCommittedEvent, with administrative provenance; no quest listener, reward or backend seed was introduced. Ordinary and Creative inventory delivery uses the existing compatible insertion helper and retains excess as a world drop.

Final command in scratchpad:180focused JUnit passes/6skips (186total), all1127registered required GameTests passed, BUILD SUCCESSFUL2m15s. New tests cover74species probability/refusal/bypass policy,58soil species success/failure,7flowers,9fruit species×3routes×2outcomes, below/unripe rejection, full inventories, actual grape entrypoints, ordinary tall/trellis two-player dispatch, Adventure tree BreakEvent, stale/reentrant root, finite-use reload and last-flower-use/Creative conservation. Earlier assertion/fixture failures and the quality boundary fix are recorded explicitly in scratchpad.

No world migration or backend change is needed; existing finite-use/legacy-1 fields are reused. Manual ordinary farming/physical inputs, two-client sync, reconnect/readiness and actual save/restart remain pending. Revert restores previous grape/harvest behavior; generated harvest items require no conversion.


## M5 evidence

Verified planting now precedes a shared localized actionbar confirmation, one sound and the existing paid practice opportunity. Flower, grape, ordinary and tree planting use canonical species names. Failed placement, stale/replaced soil, duplicate hand input and a second player cannot produce an extra success set. HUD state resolves live soil and supported parts, distinguishes packet readiness from default empty data, and discards removed/unknown occupancy. Tree ready means ripe fruit exists. No optimistic or persistent client crop-name cache is used.

The can predicate is britannia_mod:full, derived solely from charges:12full,0..11base,missing legacy data12. State and refill/dispense tests preserve unrelated components. Full artwork is absent from the supplied/repository locations; no invented artwork or broken override was added. PENDING_USER_ASSET applies to the final full-can visual gate. No item/save migration is needed; PlotStatusVersion is packet-only and does not change disk crop data.

Final focused JUnit172total/166passes/6skips/0failures/errors and all1131registered required GameTests passed, BUILD SUCCESSFUL2m12s. Commands and earlier corrections are recorded in the scratchpad. Actual GUI placement, client hands/inventory/dropped-item appearance, reconnect/resource reload and physical aiming remain separate M11 acceptance checks. Reverting M5 removes the message/HUD/predicate and restores the former planting boundary; it does not require changing already planted crop or watering-can save data.


## M6 evidence

The biome modifier removes only minecraft:patch_pumpkin, minecraft:patch_melon and minecraft:patch_melon_sparse from Overworld vegetal decoration. The loaded audit sees53Overworld biomes, removes49original references (46/2/1) and preserves every other vegetation feature exactly. All13fruit/stem structure templates remain. Native seed planting, bonemeal and vanilla growth still create pumpkin and melon fruit; custom/native-compatible gourd routes remain passing in the full farming suite.

Final JUnit182total/176passes/6skips/0failures/errors; all1134registered required GameTests passed, BUILD SUCCESSFUL2m14s. The corrected fixture and exact commands are in the scratchpad. GameTestServer uses FLAT; these results do not claim a normal-terrain seed survey or physical restart. Those remain M11 checks.

Existing blocks and chunks require no migration and are never scanned or deleted. Removing the new modifier later restores future landscape feature generation after the worldgen registries reload; it does not create/remove fruit in already generated chunks. Structure files, native stems, recipes, seed items, randomTickSpeed and the existing ore policy are unchanged.

## M7 mapping, pricing and transaction evidence

All36missing outputs now map through an explicit55-item/51-key manifest. It accounts for every67CropRegistry definition (50supported,17excluded), retains legacy produce/native controls, and refuses unrelated namespace aliases. Rails uses the byte-identical manifest for insert-only rollout and new-city seeding; existing prices/stock/flags/caps/curve parameters survive reruns. The complete table and comparator rationale are in [M7_PRODUCE_MAPPING_AND_PRICES.md](M7_PRODUCE_MAPPING_AND_PRICES.md). Actual prices continue to use the existing live Rails scarcity/form/denomination rules.

The real HTTP test exposed and fixed Rails strong params dropping world_npc_public_id before the existing economic router. Both top-level and wrapped requests now reach authoritative assignment/policy/treasury settlement. No new sale formula, NPC activation or shard-policy widening was added. Catalog opens already fetch live rows and use the existing row-sensitive revision.

Validation:111mod economy JUnit passes;1137registered GameTests pass including opt-in real Rails integration;205Rails economy/controller tests,1957assertions,0failures/errors,1existing skip. The final manifest's lifecycle/pricing refinement has a further normally processed111-test mod gate and the final205-test Rails gate. Earlier setup/fixture failures and their corrections are in scratchpad.

Two independently created local Jhelom fixtures proved actual mod prepare/fetch, durable reservation of exact quality components/count, sale/copper delivery, same-key Rails replay, real stock-cap failure/exact refund and preflight disconnect. Read-only DB verification:1transaction/1line,broccoli stock3,treasury500→491,copper grant9,market price aftersale2.4. A test proxy paused forwarding only to inspect the reservation; all economic responses were real Rails. No production evidence is inferred.

OPEN acceptance: existing post-dispatch response-loss/crash recovery knowingly refunds goods even if Rails may have committed; the callback also targets a captured player. This is a concrete pre-existing code limitation, not an unavailable external check. Preflight disconnect/replay tests do not prove that window safe. Further recovery work remains before an unqualified exactly-once verdict. Production Jhelom/Britannia assignment/override/catalog acceptance is separately pending.


M7a paired local source commits: NeoForge5c195339a33f8e79fcdd1d4589ceec45c23a4ae4; Rails6cc948f9a814add4f4a7b8a4f122514d9ed45bf8. Both isolated worktrees were clean after their commits. Final manifest LF blob SHA2569bd07850fce0bf25906daba76ac95e25bf389cd1fb7f0fff49c36dd44934e5bc. The one Rails skip is an opt-in parity fixture writer, not an acceptance scenario.

Production read-only observation subsequently became available through WSL Heroku auth and verified remote app ultimacraft. At2026-09-07T08:28:11.16914Z, transaction_read_only=on: produce_trader active/spawnable revision2, categoryproduce, noBritanniaoverride; Jhelom registered/enabledpost assigned to activeZorah. Exactly15existingproduce rows; all36approvednewkeys absent, includingbroccoli/orange. Controls applebase2/current3 andcarrotsbase1.8/current2.7 enabled. Full IDs/prices/missinglist in M7_PRODUCTION_READONLY_EVIDENCE.md. The first SELECT failed only for a nonexistent guessed FK; corrected using verified source, no writes in either transaction. This supersedes earlier statements that production read-only evidence was unavailable. Deployed/new-row acceptance remains pending; no production changes performed.


M7b update: dedicated-server response-loss recovery is now implemented and verified against real local Rails. All1,142GameTests +27focusedJUnit passed; two local receipts grant9/5copper,stock5,treasury486. Full details, persistence boundaries and operator-pending cases: M7_SALE_RECOVERY.md and scratchpad. M8–M11 remain; no final release verdict yet.


M7b paired local commits: NeoForge934790ec7ec45b193cdff299a876dceb2b611603; Railsa506d67d1b478789c2a68094b5d081a4e9afb924. Final Rails gate208runs/1,974assertions/0failures/0errors/1existing parity-writer skip,67.887533s. Both worktrees clean after commits. Mod commit has a harmless extra EOF blank line in the new settlement service; no functional issue.

M8 implemented and verified: all1,147GameTests +5JUnit passed; see M8_FENCE_EVIDENCE.md. M9–M11 remain; no final release verdict yet.
