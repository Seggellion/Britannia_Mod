# Farming Skill Progression Test Matrix

Milestone: 15 - Server-Authoritative Cultivation Gate
Status: Validated, awaiting owner approval

| Area | Required evidence | Automated coverage | Current result |
|---|---|---|---|
| Catalog coverage | 74 catalog species | Proposal/catalog/runtime reconciliation parser | PASS - focused suite |
| Proposal coverage | 74 approved rows; no missing or extra species | Proposal parser plus centralized roster validator | PASS - focused suite |
| Definition coverage | 74 explicit values: 67 crops and 7 flowers | Runtime definition reconciliation | PASS - focused suite |
| Planting mappings | 74 unique proposal/catalog item IDs; runtime common-setup resolution | Reconciliation test plus bootstrap validator | PASS - reconciliation and isolated server bootstrap |
| Exact values | Every runtime value equals its approved proposal row | Proposal parsed during test; no second Java value table | PASS - focused suite |
| Missing/extra species | Fail with identified sets | Central roster validator fixtures | PASS - focused suite |
| Duplicate/conflicting species | Duplicate ID and conflicting value fail with species/value/source | Central validator fixtures | PASS - focused suite |
| Duplicate/ambiguous item | Shared planting item fails with item and both species IDs | Central validator fixture | PASS - focused suite |
| Range | Negative and above 100 fail | Central value validator fixtures | PASS - focused suite |
| Non-finite float | NaN and positive/negative infinity fail | Central value validator fixtures | PASS - focused suite |
| Missing requirement/item | Missing metadata fails; zero remains valid | Central registration fixtures and explicit constructors | PASS - focused suite |
| Sentinels | Carrot/Lettuce/Green Onion/Wheat 0; seven flowers; Nightshade 85; Mandrake 90; Orfluer 95 | Proposal and runtime sentinel assertions | PASS - focused suite |
| Flowers | All seven definitions implement the shared field | Definition count and reconciliation | PASS - focused suite |
| Poppy separation | Ordinary 20; stage-7 mastery 100 | Independent constant/definition assertions and startup validation | PASS - focused suite |
| Fruit trees | Nine overlays resolve to crop-owned requirements without duplication | Architecture test and startup item check | PASS - focused suite |
| Inclusive threshold | Requirement minus 1 and 0.01 deny; exact and plus 0.01/1 permit for every applicable definition | `FarmingCultivationGateTest` iterates all 74 definitions | PASS - focused suite |
| Skill load states | Missing/loading/failure block; available zero remains distinct | Central policy fixtures plus `SkillManager` lifecycle inspection | PASS - focused suite and live outage transition |
| Creative/operator | Creative and real permission-level-2+ players bypass the threshold | Central policy fixtures | PASS - focused suite and live server policy; downstream live checks pending |
| Automation | Fake/non-player automation denied without a trusted owner UUID contract | Central policy fixtures and production actor classification | PASS - focused suite and live server policy |
| Ordinary crop routes | Shared farming-block transaction gates direct, trellis, tall, berry, root/tuber/bulb, orchard, and special crop paths before mutation | Source-order integration assertions | PASS - focused suite |
| Flowers | All seven below/exact thresholds; denial before snapshot, colour RNG, mutation, consumption, and feedback | Gate iteration plus `FlowerLifecycleTest` selector/mutation counters | PASS - focused suite and live server matrix |
| Vanilla boundary | No native planting interception/gating | Source boundary assertion and scoped diff audit | PASS - focused suite and real vanilla farmland packet |
| Grape boundary | Crop metadata 80; existing grape planting/NBT/names untouched and active gate returns `NOT_APPLICABLE` | Exact reconciliation and source boundary assertion | PASS - focused suite and live existing farming-block transaction |
| Seed names | No name, tooltip, narration, or `a brown seed` implementation | Source boundary assertion and scoped diff audit | PASS - focused suite |
| Failure side effects | No seed loss, target mutation, crop/flower initialization, colour roll, Farming award, success sound, or fallback | Flower transaction counters plus source-order audit | PASS - focused suite |
| Feedback | Localized action-bar failure; generic unidentified material and current/required values; no species leak | Translation JSON parsing and key/category assertions | PASS - focused suite |
| UI/sync | Denial resync only; no player-specific item naming, tooltip, narration, hover text, or new skill packet | Scoped diff audit | PASS - focused suite |
| One-item stack | Denial evaluation and production branch contain no shrink/durability mutation | One-item policy assertion plus source-order audit | PASS - automated and real one-item success/repeat packets |
| Both hands/repeated clicks | Denial consumes the interaction without mutation; success reuses existing occupancy/atomic transaction guards | Source-order audit plus flower contention transaction | PASS - real main/off combinations and rapid repeat packets |
| Multiplayer differing skills | Identical definition is evaluated independently from each current server skill; flower contention has one winner | Independent-subject policy test plus two-planter transaction test | PASS - real two-client separate targets and same-target orderings |
| Skill gain/loss | Every attempt reads a fresh server snapshot; the same definition changes eligibility without stack metadata | Repeated evaluation at 0/84/85/100 plus load-state lifecycle inspection | PASS - focused suite |
| Save/reconnect | No eligibility is persisted to items/plants; login resets state to loading and logout clears state | Source inspection and existing save/load regressions | PASS - automated plus disposable-world restart/reconnect |
| Saves | No requirement field in NBT/components or migration | Scoped diff audit and compatibility analysis | PASS - inspection |
| Network | No packet or synchronization change | Scoped diff audit | PASS - inspection |
| Farming/flower regressions | Existing compile/resource/test suite | Full Gradle validation | PASS - clean full suite |
| Placeholder integrity | Corrective Milestone 11 generator check remains non-writing | `generate_flower_placeholders.py --check` | PASS - 182 files plus ledger |
| Whitespace | Repository diff check | `git diff --check` | PASS |

## Milestone 15 focused test command

~~~text
.\gradlew.bat test --tests "com.seggellion.britannia_mod.farming.FarmingCultivationGateTest" --tests "com.seggellion.britannia_mod.farming.FlowerLifecycleTest" --tests "com.seggellion.britannia_mod.farming.FlowerMultiplayerTransactionTest" --tests "com.seggellion.britannia_mod.farming.FarmingSkillRequirementTest" --console=plain --no-configuration-cache --rerun-tasks
~~~

The focused Milestone 15 selection passed after adding the compatibility-material regression. The complete farming/flower and repository test run passed 13 suites and 97 tests with zero failures, errors, or skips. The non-writing placeholder check verified 182 generated files plus the hash ledger, and `git diff --check` found no whitespace errors. The pre-existing `TitleScreenBackgroundMixin` dedicated-dist warning and missing optional development config warning remain unchanged. No owner world was opened.

## Live validation pass - 2026-08-01

Environment: an ignored isolated repository copy ran one NeoForge 21.1.72 / Minecraft 1.21.1 dedicated server, two separate clients, an isolated `m15-world`, and real use-item packets. The clients used distinct offline identities `M15Low` and `M15Exact`. Both logged in at `LOADING`, encountered the real configured Rails HTTP 401 response, and reached `UNAVAILABLE`; server-only disposable hooks then assigned Farming 19 and 20. All hooks, drivers, counters, and run configurations were removed after capture.

| Live area | Evidence | Result |
|---|---|---|
| Catalog and approved values | Server matrix evaluated all 74 species below and exactly at the approved value; startup confirmed 67 crops, 7 flowers, and 74 mappings | PASS - 187/187 matrix overall |
| Ordinary representative success | Wheat, Mandrake, and Apple planted at exact threshold; each consumed one item and produced planted state | PASS - server transaction |
| All seven flowers | Each denied below threshold with unchanged count 2 and selector count 0; each planted at exact threshold with count 1 and selector count 1 | PASS - server transaction |
| Two-client differing skill | Same Poppy seed and separate farming blocks: Farming 19 denied; Farming 20 allowed | PASS - real clients and server packets |
| Both hands and repeated click | Low client main, repeated main, and off-hand attempts retained main 8/off 8 and unchanged soil | PASS - real client packets |
| Prediction and reconciliation | Denial showed no immediate ghost mutation and remained unchanged after 20 ticks; success reconciled main 8 to 7 and farming block to flower block | PASS - real client snapshots |
| Localized privacy | Denial action bar used `Unidentified Flower Seeds`, required 20, and current 19; no Poppy identity appeared | PASS |
| Skill load/outage | Both identities observed `LOADING`; actual HTTP 401 moved both to `UNAVAILABLE`; NOT_LOADED/LOADING/UNAVAILABLE all failed closed with zero selector calls | PASS - live lifecycle plus server matrix |
| Creative/operator threshold policy | Creative and permission-level-2 real-player evaluations bypassed threshold and unavailable state; fake and null actors denied | PASS - live server policy; downstream structure cases pending |
| Grape policy | Gate returned `NOT_APPLICABLE` while skill state was unavailable | PASS - live server policy; live grape placement pending |
| Poppy separation | Plant requirement 20 and stage-7 mastery constant 100 remained independent | PASS - live server matrix |
| Feedback material category | Initial run reproduced Potato and Mushroom as crop seeds; corrected run classified Potato, Brown Mushroom, and Red Mushroom as planting material | PASS after correction and regression test |

The final authoritative files report `summary passed=187 failed=0` and `packet_summary low_denials_unchanged=true exact_allowed_once=true`. The real exact-threshold planted block retained its flower block entity and server-authoritative planted state; a prior diagnostic snapshot of the same route recorded Poppy species, stage 1, player planter/provenance, quality/tint, private farming-block soil origin, and nutrient fields.

Rows not executed in that initial live pass were native vanilla farmland and live grape placement; trellis, tall, berry/regrowing, orchard, bulb/tuber/root, and other specialized structural routes below/exact/above; ordinary-crop real-packet denial; same-target low-first/high-first contention; success from both hands and repeated success; one-item real-packet stack; Creative/operator invalid-target, support, protection, environment, and Adventure cases; loaded-skill recovery, skill gain/loss, and pre-existing planted-crop behavior after loss; dispenser, command/system, world-generation, save/load/reconnect; award/statistic/advancement/effect delta capture; and owner-world visual/animation review. The continuation below closes many, but not all, of those rows.

## Final live continuation - 2026-08-01

Environment: a new ignored isolated runtime used a disposable saved world, one dedicated server, two separate real clients (`M15Final` and `M15Peer`), real use-item packets, and a production-compatible localhost skill response. The world was restarted and the clients reconnected; no owner world was opened. Authoritative accounting is preserved in `build/m15-live-runtime/m15-final-second-server-accounting.txt`, with completed server/client logs in `m15-final-*-rerun.stdout.log` and focused real LOADING-packet evidence in `m15-final-*-focus.stdout.log`. Temporary hooks and compiled harness code were removed after capture.

| Final live area | Evidence | Result |
|---|---|---|
| Real load-state packet | Poppy packet while `LOADING`; stack 2, target, selector, sound, award, and statistic remained unchanged; compatible load separately reached `AVAILABLE` | PASS - fail closed while non-authoritative |
| Annual boundary | Potato 4.99 denied, 5.00 and 5.01 planted; Wheat exact planted | PASS |
| Tall/berry/orchard | Corn 29.99/30, Raspberry 24.99/25, Apple 54.99/55 | PASS - below/exact packets |
| Root/specialized/trellis/fibre | Ginseng 69.99/70, Mandrake 89.99/90, Hops 59.99/60, Cotton 39.99/40 | PASS - below/exact packets |
| Flower boundary | Poppy 19 denied and 20 planted; selector delta 0/1 and sound delta 0/1 | PASS |
| One-item and rapid repeats | One Wheat item became one initialized crop with one award and one sound; repeats caused no duplicate or underflow | PASS |
| Both hands | Same items, different items, eligible-main/ineligible-off, and ineligible-main/eligible-off | PASS - exactly one or zero authoritative transactions as appropriate |
| Crop contention | Two real clients targeted one farming block in low-first and high-first schedules | PASS across completed runs - one mutation/consumption/award/sound |
| Flower contention | Two real clients targeted one farming block in both schedules | PASS - one mutation/consumption/selector/sound; both reconciled |
| Creative and operators | Creative, Survival op 4, and Adventure op 4 below threshold; occupied/unsupported downstream failures | PASS - threshold bypass only; downstream rules retained |
| Vanilla/grape/command boundaries | Vanilla farmland planted while skill unavailable; grape existing route planted while unavailable; direct `setblock` recorded zero gate calls | PASS |
| Dispenser/world generation | No dispenser planting behavior exists; world generation does not use the player transaction | Dispenser `NOT PRESENT`; world generation `NOT APPLICABLE` |
| Accounting | Crop success: one consume/init/award/sound. Flower success: one consume/init/selector/sound and no Farming award. Denial: all zero. | PASS |
| Save/restart/reconnect | Existing Poppy persistent state and Potato identity/stage/progress survived restart; clients reconnected | PASS |
| Milestone 16 boundary | No player-specific seed names, brown-seed presentation, tooltip, narration, hover, or new UI packet | PASS - scoped source/diff audit |

Those critical rows are closed by the final pass below. The remaining packet-per-species, adjacent-target held-click, complete environment matrix, formal-video, and owner-world rows are optional. Milestone 16 remains unstarted.

## Final critical runtime closure - 2026-08-01

Environment: the ignored isolated runtime used a disposable Temperate fixture at `-64,100,64`, a compatible delayed skill endpoint, one dedicated server, and real client identity `M15Closure`. Combined multiplayer evidence uses the earlier `M15Final`/`M15Peer` two-client continuation. Final server accounting: `build/m15-live-runtime/run/server/m15-critical-evidence.txt` (`28 passed, 0 failed`). Client packet/animation evidence: `build/m15-live-runtime/run/m15-critical-client-evidence.txt` and `build/m15-live-runtime/run/screenshots/m15-critical-*`. All temporary runtime code/configuration and the disposable world were removed; evidence remains ignored and untracked.

| Critical closure area | Evidence | Result |
|---|---|---|
| Uninterrupted load transition | Real login loader: `LOADING`/0 -> delayed endpoint -> `AVAILABLE`/5; first and second packets used one connection, one stack instance, and target `-64,100,64` | PASS - first attempt count 2/no crop; immediate retry count 1/Potato |
| No stale or item-carried state | No reconnect, restart, replacement stack, dimension change, item metadata, denial cache, or parallel skill system | PASS |
| Annual after skill loss | Potato planted while eligible, then Farming 0: water, bone-meal nutrient care, natural maturity, root-shovel harvest | PASS - no re-gate and crop cleared normally |
| Perennial after skill loss | Hops planted at 60, then Farming 0: supported growth, scissor harvest, identity retained, stage-4 reset, renewed progress | PASS |
| Flower after skill loss | Poppy planted at 20, then Farming 0: water, Temperate natural growth to stage 6, stage-7 shear/reset, regrowth, Adventure cutback | PASS - species/tint preserved |
| Existing-plant decision | DECISION-010: "Gate only new planting; existing crops continue growing, receiving care, and being harvested. Existing protection rules continue independently." | PASS - crop and flower lifecycles conform |
| Ordinary Poppy boundary | Earlier real packets at Farming 19 and 20; definition/startup validation remains independent of mastery | PASS - 19 denied, 20 planted |
| Poppy mastery 99 | Real packet, stage 6, tagged skinning knife, Farming 99 | PASS - stage 6, tint unchanged, damage 0 |
| Poppy mastery wrong tool | Real packet, stage 6, stick, Farming 100 | PASS - no advance or durability charge |
| Poppy mastery 100 | Real packet, stage 6, tagged skinning knife, Farming 100 | PASS - exactly stage 7 and exactly one durability |
| Poppy reset/requalification | Shear stage 7 to stage 1; regrow naturally to 6; retry at 99 | PASS - no permanent mastery flag and no colour reroll |
| Creative downstream | Occupied target and invalid material after threshold bypass | PASS - downstream denial; no consume/second initialization |
| Survival operator downstream | Unsupported Hops below requirement | PASS - threshold bypassed, support denied, no partial structure |
| Adventure operator downstream | Orchard seed with blocked clearance below requirement | PASS - threshold bypassed, clearance denied, no partial tree |
| Environment boundary | Creative planted altitude-invalid Potato; production growth evaluation blocked it | PASS - environment is growth-time here, not a manufactured planting failure |
| Protection boundary | Creative-origin Poppy became protected; ordinary Survival actor care denied after deauthorization | PASS - origin/permission policy independent of Farming |
| Client hand/prediction | Screenshots/logs for LOADING denial, exact retry, 99 denial, wrong tool, and 100 success; normal hand swing recorded | PASS - no critical ghost or inventory drift |
| Feedback/sound/duplicates | Current reconciliation plus earlier focused/two-client localized action-bar and accounting logs | PASS - generic feedback, one success sound, no duplicate transaction |
| Two-client agreement | Earlier `M15Final`/`M15Peer` same-target low/high ordering and reconnect evidence | PASS |
| Persistence | Earlier disposable-world restart/reconnect plus final post-lifecycle server save | PASS - identity/state retained; eligibility not persisted |
| Production defects | No closure-pass gameplay defect reproduced | PASS - no production correction required |
| Harness defects | Same-state BE retention, unsuitable climate fixture, stale pre-teleport target, and 10-tick contention timing | CORRECTED only in ignored instrumentation; final 28/28 |
| Cleanup | Endpoint, source hooks, identity exemptions, run arguments, server properties, compiled helpers, and world | PASS - removed; evidence untracked |

The complete automated suite remains 13 suites / 97 tests / 0 failures / 0 errors / 0 skipped. The placeholder generator check remains 182 files plus the ledger, and `git diff --check` remains clean apart from line-ending/inaccessible-global-ignore warnings. No new regression test was added because no production defect was found.

Milestone 15 is **Validated, awaiting owner approval**.
