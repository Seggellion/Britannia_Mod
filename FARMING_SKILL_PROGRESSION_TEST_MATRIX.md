# Farming Skill Progression Test Matrix

Milestone: 17 - Progression Validation, Gameplay QA, and Closeout
Status: Approved

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
| Seed names | Viewer-local generic names below requirement and exact names at/above it | All 74 mappings plus category and sentinel assertions | PASS - Milestone 16 focused suite |
| Failure side effects | No seed loss, target mutation, crop/flower initialization, colour roll, Farming award, success sound, or fallback | Flower transaction counters plus source-order audit | PASS - focused suite |
| Feedback | Localized action-bar failure; generic unidentified material and current/required values; no species leak | Translation JSON parsing and key/category assertions | PASS - focused suite |
| UI/sync | Viewer-specific name/tooltip/narration/container/Creative/search/drop-label projection from authoritative state | Pure surface matrix, client mixin/source boundary, revision refresh | PASS automated and corrected two-client live UI |
| One-item stack | Denial evaluation and production branch contain no shrink/durability mutation | One-item policy assertion plus source-order audit | PASS - automated and real one-item success/repeat packets |
| Both hands/repeated clicks | Denial consumes the interaction without mutation; success reuses existing occupancy/atomic transaction guards | Source-order audit plus flower contention transaction | PASS - real main/off combinations and rapid repeat packets |
| Multiplayer differing skills | Identical definition is evaluated independently from each current server skill; flower contention has one winner | Independent-subject policy test plus two-planter transaction test | PASS - real two-client separate targets and same-target orderings |
| Skill gain/loss | Every attempt reads a fresh server snapshot; the same definition changes eligibility without stack metadata | Repeated evaluation at 0/84/85/100 plus load-state lifecycle inspection | PASS - focused suite |
| Save/reconnect | No eligibility is persisted to items/plants; login resets state to loading and logout clears state | Source inspection and existing save/load regressions | PASS - automated plus disposable-world restart/reconnect |
| Saves | No requirement field in NBT/components or migration | Scoped diff audit and compatibility analysis | PASS - inspection |
| Network | Versioned skill state, revision ordering, bypass, lifecycle resync, reconnect reset | Client projection tests and source inspection | PASS automated plus live gain/loss, stale revision, dimension, respawn, and restart/reconnect |
| Farming/flower regressions | Existing compile/resource/test suite | Full Gradle validation | PASS - clean full suite |
| Placeholder integrity | Corrective Milestone 11 generator check remains non-writing | `generate_flower_placeholders.py --check` | PASS - 182 files plus ledger |
| Whitespace | Repository diff check | `git diff --check` | PASS |

## Milestone 17 cross-system closeout

| Area | Evidence | Result |
|---|---|---|
| Catalog/proposal/runtime | One parser-driven loop over 74 catalog rows, 74 approved proposal rows, and 74 runtime definitions | PASS - `FarmingSkillProgressionCloseoutTest` |
| Planting mappings | 74 distinct catalog and proposal planting IDs; runtime bootstrap 74 | PASS automated and dedicated bootstrap |
| Policy totals | 74 cultivation decisions; 74 presentation decisions; 68 maskable custom items; six native exclusions | PASS automated |
| Unified threshold | `-1`, `-0.01`, exact, `+0.01`, and `+1` identification/cultivation comparison | PASS for all ordinary applicable policies |
| Approved exceptions | Grapes identity at 80 with ungated planting; six native routes untouched | PASS automated/source boundary |
| Load/bypass | Unavailable fails closed; Creative/operator level 2+ bypasses the threshold | PASS automated |
| Stack integrity | One-item stacks retain item, count, and components through all policy evaluations | PASS automated |
| Poppy | Ordinary 20; stage 7 remains stage 6 + Farming 100 + tagged knife + authorization | PASS automated/source boundary |
| Denial ordering | Crop before plant mutation; flower before snapshot, colour RNG, mutation, and shrink | PASS deterministic source-order assertion plus existing transaction counters |
| Production scope | No M17 production Java/resource change and no deferred feature | PASS scoped diff audit |

Focused command:

~~~text
.\gradlew.bat test --tests com.seggellion.britannia_mod.farming.FarmingSkillProgressionCloseoutTest --console=plain --no-configuration-cache
~~~

Current focused result: PASS, three tests. Final full-suite and runtime rows are recorded below after execution.

## Milestone 17 final validation accounting - 2026-08-02

| Row | Classification | Evidence/result |
|---|---|---|
| Closeout reconciliation | PASSED - reexecuted in M17 | 3/3 cross-system tests |
| Complete automation | PASSED - reexecuted in M17 | 15 suites, 108 tests, zero failure/error/skip |
| Placeholder/model contract | PASSED - reexecuted in M17 | 182 generated outputs plus ledger; existing Corrective M11 suites pass |
| Dedicated server/common | PASSED - reexecuted in M17 | 74 species, 67 crops, seven flowers, 74 planting items; no M16 client-class load |
| Client startup | PASSED - reexecuted in M17 | M16 mixins, resources, OpenAL, sound engine, and render atlases completed |
| Crop/flower live boundaries | PASSED - referenced approved M15 evidence | No M17 production change; not reexecuted post-commit |
| Two-client identification/UI | PASSED - referenced approved M16 evidence | No M17 production change; not reexecuted post-commit |
| Final combined live matrix | PASSED - reexecuted after M16 and before the M17 closeout commit in a fresh ignored runtime | Dedicated server, `M17Low`, `M17High`, disposable `m17-combined-world`; identification/cultivation, open-UI gain/loss, shared chest, grapes, all flowers, Poppy, bypass/fail-closed, transitions, restart, and protection passed |
| Recipe book | NOT PRESENT / NOT APPLICABLE | No applicable farming recipes |
| Installed recipe viewers | NOT PRESENT | No JEI/EMI/REI integration installed |
| Merchant/trade/custom compatibility UI | NOT PRESENT / NOT APPLICABLE | No applicable farming surface |
| External viewers | BEST EFFORT | Shared `ItemStack` policy where used; no broad compatibility added |
| Chat-link visible text | UNAVOIDABLE DISCLOSURE | Server-serialized visible component; hover remains viewer policy; no broad rewrite approved |
| Pickup text | NOT PRESENT | Ordinary vanilla pickup path emits no identity text |
| Advanced tooltip registry/components | PASSED approved debug boundary | Diagnostics may reveal registry/component identity only in advanced mode |
| Formal profiling/packet benchmark | NOT EXECUTED / deferred | Structured practical and source review only |

Rows reexecuted in M17 are not conflated with inherited evidence. The fresh combined matrix satisfies the remaining material acceptance row, and the owner has approved the Milestone 17 closeout.

## Fresh combined runtime rows - 2026-08-02

| Row | Classification | Fresh result/evidence |
|---|---|---|
| Two real viewers / one chest | PASSED | `M17Low` Farming 19 and `M17High` Farming 100 simultaneously rendered generic versus exact names from the same server stacks; no cross-view contamination |
| Representative crop | PASSED | Corn denied below 30 with zero mutation/consumption/award/stat/success effects; high planted once with one award invocation and established success sound |
| Representative flower | PASSED | Hyacinth denied below 30 with zero colour selection; high planted once with exactly one colour selection |
| Open-UI gain | PASSED | Poppy generic at 19, exact at 20 on new revision without screen close/stack replacement, then planted |
| Open-UI loss | PASSED | Exact at 100, generic at 19, new Corn denied; existing crop/flower and flower colour remained |
| Grapes | PASSED | Concord and Wild Grape masked as `a brown seed` only for low; components unchanged; low Farming 0 planted Concord; `NOT_APPLICABLE` gate result; variety persisted |
| Campion 10 | PASSED | 9 rejected, 10 planted, colour delta 0/1 |
| Poppy 20 | PASSED | 19 rejected, 20 planted, colour delta 0/1 |
| Hyacinth 30 | PASSED | 29 rejected, 30 planted, colour delta 0/1 |
| Snowdrop 40 | PASSED | 39 rejected, 40 planted, colour delta 0/1 |
| Lily 50 | PASSED | 49 rejected, 50 planted, colour delta 0/1 |
| Foxglove 65 | PASSED | 64 rejected, 65 planted, colour delta 0/1 |
| Orfluer 95 | PASSED | 94 rejected, 95 planted, colour delta 0/1 |
| Poppy mastery | PASSED | 99+knife remained 6/no damage; 100+wrong tool remained 6/no damage; 100+knife reached 7/one damage/no output; colour retained; requalification required |
| Creative | PASSED | Exact identity at effective 0, threshold bypass, no consumption, occupied/downstream rule retained, real-name search policy refreshed |
| Operator 2 outside Creative | PASSED | Exact identity and threshold bypass; downstream rules retained; ordinary behavior returned after de-op |
| `NOT_LOADED` | PASSED | Generic client policy; live planting denied, count 1, no mutation |
| `LOADING` | PASSED | Generic client policy; live planting denied, count 1, no mutation |
| `UNAVAILABLE` | PASSED | Generic client policy; live planting denied, count 1, no mutation |
| `AVAILABLE` | PASSED | Exact 30 update took effect immediately and Corn planted |
| Stale revision | PASSED | Intentionally stale real `SkillSyncPayload` did not replace revision 38; next accepted respawn sync advanced to 39 |
| Reconnect epoch | PASSED | Both clients reset to revision -1/`NOT_LOADED`, observed `LOADING`, then accepted new epoch revisions |
| Respawn | PASSED | Real client respawn request followed by authoritative revision 39 |
| Dimension change | PASSED | Nether/Overworld transfer completed with authoritative current state |
| Server restart | PASSED | Both clients reconnected; saved crop, Poppy stage/colour, chest, and Concord variety loaded |
| Resource reload | PASSED | Low client reload completed successfully at unchanged revision 21; generic Poppy presentation retained |
| UI surfaces | PASSED / documented boundary | Inventory/hotbar stack, shared container, normal/advanced tooltip, narration, dropped labels, Creative/admin/search policy; chat visible text remains unavoidable while viewer-local hover stays masked; absent recipe/viewer surfaces classified above |
| Stack/save/network audit | PASSED | IDs, counts, component maps, grape custom data, custom-name absence, and chest component hash retained; no saved eligibility/presentation; no client skill authority |
| Protected flower | PASSED | Ordinary attempt left protected Creative Poppy state/colour unchanged, no damage/output |
| M11 model/resources | PASSED | 49 canonical models, 49 base, 49 masks, 0 separate pass models, 0 third textures; dedicated load and client reload safe |

Fresh ignored evidence paths: `build/m16-live-runtime/run/m17-server/m17-evidence/server.txt`, `build/m16-live-runtime/run/m17-client-low/m17-evidence/client-low.txt`, `build/m16-live-runtime/run/m17-client-high/m17-evidence/client-high.txt`, the associated `logs/latest.log`, and `build/m16-live-runtime/m17-*-process*.log`. Runtime evidence is ignored and untracked.

## Milestone 16 focused coverage

| Area | Required evidence | Current result |
|---|---|---|
| Complete policy coverage | All 74 approved species have an explicit mapping; 68 custom planting items are masked and six native compatibility rows are explicitly outside scope | PASS automated |
| Inclusive identification | Below requirement generic; exact and above requirement identified | PASS for all 74 definitions, with excluded native rows asserted separately |
| Material categories | Custom crop seeds, flower seeds, grape varieties, fail-closed planting material, and native boundary use approved policy | PASS automated |
| Grapes | Every variety remains one Grapes requirement at 80; below uses `a brown seed`; exact identifies; stack data unchanged | PASS automated plus live Wild Grape/Cabernet Sauvignon two-viewer UI |
| Poppy | Ordinary identity threshold 20 remains independent of stage-7 mastery 100 | PASS automated |
| Per-viewer behavior | Same logical stack can be generic for one viewer and exact for another | PASS pure test and live two-client inventory/shared-chest/drop-label UI |
| Dynamic refresh | Current skill is evaluated per snapshot; gain can reveal without reconnect; stale revisions are ignored | PASS automated and live 0 -> 20 -> 100 -> 19 open-container sequence |
| Load failure | NOT_LOADED, LOADING, and UNAVAILABLE hide identity even when the carried numeric value is high | PASS automated |
| Creative/operator | Server-supplied identification bypass reveals exact identity in every state | PASS automated and live Creative plus op2 outside Creative |
| Passive privacy | Generic name, tooltip, and narration carry no exact requirement or current Farming value | PASS automated policy assertions |
| UI surfaces | Inventory, hotbar, off-hand, containers, dropped label, pickup/chat hover, Creative inventory/search, recipe/compatibility surfaces share one policy | PASS supported live surfaces; pickup text NOT PRESENT; serialized chat-link text UNAVOIDABLE DISCLOSURE |
| Advanced/debug boundary | Unidentified normal tooltip is generic-only; advanced retains registry/component diagnostics | PASS source inspection; live advanced-tooltip review pending |
| Creative search refresh | Accepted revision rebuilds search trees and refreshes an open Creative search | PASS live actual Search tab query for `orfluer` returned `Orfluer Seeds` |
| Item integrity | Presentation does not write components, NBT, name, count, variety, or requirement to the stack | PASS component-equivalence test and source audit |
| Connection lifecycle | Atomic immutable snapshots; stale packet rejection; new-session revision epoch reset | PASS automated |
| Dedicated-server boundary | Client mixins/accessor/event bridge remain in client-only packages/configuration | PASS compile/source audit and isolated dedicated bootstrap |
| Vanilla compatibility | Six native routes receive no identity, gate, or recipe behavior under DECISION-012 | PASS automated/source audit |
| Recipes/viewers | No farming recipe/viewer behavior is added because no applicable farming recipes exist | PASS scoped audit; external viewers documented best effort |
| Existing gameplay | Cultivation gate, grape placement, flower lifecycle/color/protection, save data, acquisition, and economy remain unchanged | PASS source/focused regressions and complete 105-test suite |

## Milestone 16 focused test command

~~~text
.\gradlew.bat test --tests com.seggellion.britannia_mod.farming.FarmingSkillIdentificationTest --tests com.seggellion.britannia_mod.farming.FarmingSkillRequirementTest --console=plain --no-configuration-cache
~~~

The focused Milestone 16 selection passes 15 tests. The material live acceptance rows are closed by the two-client pass below.

## Milestone 16 live UI validation - 2026-08-01

Environment: ignored `build/m16-live-runtime`, one dedicated NeoForge server, two real clients (`M16Low`/`M16High`), one disposable world, server-only ephemeral skill assignments, and production client sync/presentation code. No owner world or tracked runtime evidence was used. First-pass evidence is retained with `pass1` names; corrected-pass and restart evidence is in the current ignored server/client evidence files.

| Live area | Evidence | Result |
|---|---|---|
| Equivalent inventories | Carrot, Potato, Corn, Rice, Nightshade, Apple, seven flowers, two grape varieties, identical counts/components/off-hand | PASS - low generic by threshold; high exact; server signatures equal |
| Shared chest | Same Corn, Poppy, Wild Grape, Cabernet Sauvignon slots open on both clients | PASS - viewer-isolated names; server slots unchanged before/after |
| Open-UI gain/loss | Farming 0 -> 20 -> 100 -> 19 while `ContainerScreen` remained open | PASS - immediate names/tooltips/narration; no replacement or mutation |
| Load states | Connection `NOT_LOADED`; live `LOADING`; real API failure `UNAVAILABLE`; harness `AVAILABLE` | PASS - generic fail closed; available uses current value |
| Stale packet | Accepted revision 12 followed by deliberate revision 11/value 100 | PASS - client remained revision 12/value 19 |
| Dimension/respawn | Overworld -> Nether -> Overworld; real death and client respawn packet | PASS - authoritative revisions 19/20/21 and correct presentation |
| Server restart/reconnect | Stop/restart while both clients remained open | PASS - each reset to NOT_LOADED/-1, then accepted new epoch LOADING/AVAILABLE |
| Resource reload | Actual `reloadResourcePacks()` on both clients | PASS - `failure=null`; snapshot and localized identities retained |
| Narration | `GameNarrator.sayNow` invoked with actual projected Poppy component on revisions | PASS - generic/exact/generic follows visual state; no raw key |
| Creative/search | Farming 0 Creative bypass; actual Search tab query `orfluer`; return to Survival | PASS - `Orfluer Seeds` found; Survival generic restored |
| Operator | Permission level 2+ outside Creative, then de-op | PASS - exact while bypass true; ordinary policy restored |
| Grapes | Wild Grape and Cabernet Sauvignon custom-data stacks | PASS - low `a brown seed`; high exact name/variety tooltip; data unchanged |
| All flowers | Campion, Poppy, Hyacinth, Snowdrop, Lily, Foxglove, Orfluer below and above live; exact thresholds automated | PASS - low generic, high/exact real; zero tooltip/narration leak |
| 68-item rendered audit | Real client hover-name and normal-tooltip components at fail-closed/available revisions | PASS - 68 maskable, seven flowers, zero ordinary species leaks |
| Dropped labels | Intentionally visible Poppy entity name; source stack had no custom name | PASS after correction - low generic, high exact; crop/flower/grapes likewise masked |
| Pickup | Equivalent Poppy entities collected | NOT PRESENT - vanilla ordinary pickup emitted no textual message/overlay |
| Chat link | Shared `ItemStack.getDisplayName()` link | UNAVOIDABLE DISCLOSURE - visible component serialized as `[Poppy Seeds]`; hover stack remains client-policy/component preserving; no broad rewrite approved |
| Recipes/viewers | Vanilla farming recipes, guidebook, custom menu, merchant farming trade, JEI/EMI/REI | NOT PRESENT / NOT APPLICABLE; global ItemStack path covered by chest/Creative |
| Stack equality | Registry ID, class, count, components/custom data, grape variety, custom-name state before/after | PASS - no presentation mutation or viewer cross-contamination |
| Cultivation security | Locally forced identity concept at low; forced generic concept at high | PASS - Corn/Poppy low denied, high eligible; Grapes NOT_APPLICABLE |
| Client/cache/performance | Rapid revisions, container, hotbar/off-hand, search, reload, transitions, many hover resolves | PASS structured observation - no stale stack name, frame packet, rebuild loop, exception, or visible stall |
| Dedicated isolation | Disposable dedicated-server boot and 74/67/7/74 bootstrap | PASS - no M16 client classloading failure; only established unrelated warnings |

Defect/correction: the first run proved that the newly implemented login, logout, and dropped-label methods were absent from `ClientModSetup` event registration. The minimal production correction registers those existing handlers. `FarmingSkillIdentificationTest` now asserts all three registrations. The focused suite, full 14-suite/105-test run, placeholder check, and corrected two-client scenario pass. Temporary skill hooks, drivers, runtime identity exemption, and custom run configurations were removed after evidence capture.

Known architecture outcomes: ordinary unnamed item entities have no vanilla visible label; the deliberately visible entity path now masks per viewer. Ordinary pickup has no textual identity surface. Server-composed chat-link visible text is fixed before receipt and is documented as an approved `UNAVOIDABLE DISCLOSURE`; broad shared-component packet rewriting was not added. No applicable recipe/merchant/guidebook/custom farming menu or external recipe viewer is installed.

Milestone 16 is **Approved**.

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

Those critical rows are closed by the final pass below. The remaining packet-per-species, adjacent-target held-click, complete environment matrix, formal-video, and owner-world rows are optional. At the time of this historical Milestone 15 continuation, Milestone 16 remained unstarted; it was subsequently completed and approved.

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

This historical pass left Milestone 15 validated and awaiting approval; Milestone 15 was subsequently approved and committed as recorded at the top-level feature status.
