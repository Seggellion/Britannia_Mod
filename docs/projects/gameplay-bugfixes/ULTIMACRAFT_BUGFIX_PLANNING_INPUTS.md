# UltimaCraft bug-fix planning inputs

Updated2026-09-06 after authorized supplemental discovery. These are candidate outcomes/dependencies for a later implementation playbook, not authorization to implement, rebalance, migrate, deploy, create branches or assign a release label. Mod: `C:/projects/britannia/mod/Britannia_Mod`, patch-18, `421e27853dde4099d1d794568e33e6709507a53b`. Rails: Ubuntu WSL `/home/dusti/ultimacraft-website`, release/public, `a9425ca41afa9b4ba7539426044e7b50e70a3966`. Minecraft1.21.1/NeoForge21.1.72/Java21. Source maps/runtime evidence are in [original discovery](C:/projects/britannia/mod/Britannia_Mod/docs/projects/gameplay-bugfixes/ULTIMACRAFT_BUGFIX_DISCOVERY.md) and [supplemental discovery](C:/projects/britannia/mod/Britannia_Mod/docs/projects/gameplay-bugfixes/ULTIMACRAFT_BUGFIX_SUPPLEMENTAL_DISCOVERY.md); all acceptance cases remain unchecked.

## Scope and adopted decisions

**19 tracked reports;16 functional issues in scope;3 exclusions.** BUG-04/06/07 artwork belongs to the user and receives no investigation task, milestone or aesthetic gate. Historical evidence is retained without restoring scope. User supplies new full-can art. Functional shader compatibility, fence model selection and charge-state synchronization remain scope; no decorative geometry polishing or Fabric/Atrevion work.

| Decision | Settled contract |
|---|---|
| D1 |60second expiry only while empty; restore previous hoed soil, not dirt/unprepared community base; server downtime excluded; preserve planted crops/ownership/lifecycle accounting |
| D2 | Prepared community plots plus empty minecraft:farmland inside the acting player's owned house; reject ordinary/coarse dirt and farmland elsewhere; preserve community access; do not broaden owner to managers |
| D3 | Buckets/cans/bowls/rain hydrate; rain needs no manual-watering supplement; retain initial fertilizer moisture; bowl route is now confirmed missing |
| D4 | Eligible harvest failure destroys produce; annual carrots/green onions consumed; perennial/tree effects follow lifecycle, not universal uprooting; refusal distinct from failed roll |
| D5 | Grapes obey both planting and harvest gates; remove explicit gate exemption and direct item bypass. Cultivated flowers use existing species requirements. Native crops outside custom system remain separate |
| D6 | Placing custom fence on existing vanilla-shovel-made dirt_path preserves path. L-shaped join defect and vanilla-height collision remain; creating path beneath existing fence is not an additional requirement |

D4 odds/costs/gains/Creative and residual evidence/accounting questions below are not settled by these six decisions.

## Issue-to-work mapping

S/M/L describe relative engineering scope, not elapsed time. Confirmed means current defect/path, not completed fix.

| Issue / evidence | Files or systems | Proposed bounded outcome / dependency | Size / critical validation |
|---|---|---|---|
| BUG-01 source path confirmed, shader cause unverified | MoongateBlockEntityRenderer, ClientModSetup, moongate model references | Smallest evidenced render correction after actual artifact/Iris/Photon comparison | M; baseline/shaders-off/Photon, all surfaces, teleport independently |
| BUG-02 feedback/occupancy missing | FarmingBlock/BE, GrapeSeedsItem, FlowerPlantingService, translations/UI layer | Commit-based actionbar plus aimed occupied-plot identity; align with M1/M3 planting authority | M; one success/seed/gain, rejection silent as success,2hands/players/reload |
| BUG-03 timer/reversion mismatch | CommunityFarm BE, FarmingBlock/BE, FertilizedDirtItem, flower snapshot | Deterministic online empty expiry; prior-hoed restoration and paused preparation budget; private provenance; Q5 accounting | M–L;1199/1200/1201, randomTickSpeed0, chunk load/downtime/legacy uses |
| BUG-05 moisture gate works; bowl hydration absent | Soil/flower care, bowl item/filling/output, FarmingBlock | Add bowl route; retain rain, bucket/can, initial moisture and dry outer dispatcher | M; actual both-hand use, source/remainder/capacity, rain vs dry controls |
| BUG-08 harvest gate/roll absent; grape bypasses | CropRegistry/gate/resolver, FarmingBlock, tall/tree/fruit/axe/flower routes, SkillManager | One resolved requirement and root harvest decision/commit; lifecycle-aware destructive failure; Q3 economics | L;67crop+7flower definitions, unavailable skill, alternate yield paths, deterministic RNG/accounting |
| BUG-09 ordinary dirt accepted, owned farmland absent | FertilizedDirtItem, CommunityHoedFarmBlock, HouseBuildRights/HouseUtil | Explicit prepared community or owned-house farmland predicate; exact target dimension/fullBox/owner | M; missing rehydration/foreign/boundary/other dimension/modes/no consume |
| BUG-10 Creative support defect reproduced | DecorativeMultiblockItem, AdventureScarecrowItem, bounded survival-sensitive decoration families | Shared Creative substrate exemption where technically valid, persist when later survival checks need it | M; parts/neighbor/reload, S/A protection, occupied cells/border/height |
| BUG-11 adjacent-arm L→T→L state history reproduced | WoodenFenceBlock and existing blockstate/model selection | Final-neighborhood convergence independent of placement/removal history | M;16neighborhoods/rotations/mirrors/order, no update loops |
| BUG-12 collision1.0 vs vanilla1.5 | WoodenFenceBlock shape/state | Vanilla-height intended collision strips, appropriate separate outline | S–M; corners/gaps/ordinary jump controls, M7 path effects |
| BUG-13 custom/vanilla path reversion reproduced | Fence/path survival/solidity integration | Narrow preserve-existing-dirt_path exception for intended fence; no shovel-gesture rewrite | M; placement/neighbor/reload and taller collision |
| BUG-14 full-state selection absent | WateringCanItem, item predicate registration/model selection | Exact-full charge state at12, existing partial/empty art; integrate user-supplied full art | S;0/1/11/12, missing legacy tag12, all item views/client sync |
| BUG-15 native sources confirmed | NeoForge biome modifier/feature policy; conditional structure processors/pools | Remove3 landscape placed features; preserve native stems/custom crops; Q2 only for structure additions | S–M; loaded registry + actual new chunks, existing plants/reload/intentional cultivation |
| BUG-16 hand restriction reproduced | Bowl item/services; DyeTubItem/LoadingService; narrow use hook | Role/slot resolver and single gesture ownership,3bowl recipes+7pigment variants | M; actual both-hand dispatch, contexts/nonmatches/Creative/remainders |
| BUG-17 final output compatible, separate hand | BowlPreparationService, FertileDirtMixingService, BowlPreparationOutput | One canonical construction and compatible-stack-first insertion policy | S–M;1/2/3/64 and reversed, partial/full capacity, components/aliasing/remainders |
| BUG-18 pre-Rails mapping omission confirmed | CommodityMappings, server buyback/settlement contracts; Rails catalog/seed/valuation | Intended broccoli/orange mapping and authoritative catalog alignment with legitimate prices; Q1 context | M across repos; known controls, policy/price/caps/funds/revisions/receipt accounting |
| BUG-19 case dispatch/dismantling reproduced | DisplayCaseBlock/BE, InteriorDecoratorToolItem, narrow RightClickBlock recognition | Offhand content-only world ejection owns earlier callback; insertion-confirmed root mutation; preserve case | M;16gesture baseline, components/counts,2players/denial/rejected spawn/reload/visual sync |

The36 unmapped edible definitions found by the audit are follow-up candidates, not36 additional vendor acceptance commitments. No automatic seeds/flowers/reagents/processed-item expansion. Excluded crop-art IDs above are absent from this work mapping by design.

## Candidate milestone outcomes and dependencies

These are reviewable outcome groupings, not an executable playbook or authorization to delegate. Split diffs when a shared group has independent behavior. Already decided contracts do not need reconfirmation.

| Candidate | Scope / reviewable result | Dependencies / exit evidence |
|---|---|---|
| M0 — Reproduction identity and remaining contracts | Record deployed client/server artifact identity; retain source/fixture baselines; resolve only questions blocking selected work | Source-confirmed independent work need not wait for every environment question. No clean-release claim from diagnostic outputs |
| M1 — Allowed soil, empty expiry and hydration | BUG-03/05/09: owner-contained farmland, prior-hoed restoration, separate timers, bowls, existing moisture semantics | D1–D3 adopted; Q5 final entitlement accounting. Boundary/time/random-tick-independent tests; true chunk/restart and normal farming run |
| M2 — Two-hand transactions and case contents | BUG-16/17/19, separate reviewable recipe/insertion/ejection diffs | Explicit hand/target precedence; build shared resolver only to supported immediate handlers. Real two-hand input, capacities/components, exactly-one commit and case/entity conservation |
| M3 — Skill equality and harvest outcomes | BUG-08, both grape bypasses, cultivated flowers, all root/yield routes | Existing definitions and M1 lifecycle; Q3 formula/costs/gains/Creative. Native outside-system extension waits only on Q4. Deterministic outcome and economic-route tests |
| M4 — Planting feedback and can state | BUG-02/14 | M1/M3 authoritative occupancy and M2 hand semantics where shared; user-supplied full-can art for final visual check. One localized commit message/aim label, charges across item views |
| M5 — Intended produce buyback | BUG-18 mod mapping plus bounded Rails catalog work if needed | Q1 report/loaded catalog evidence and legitimate price source; not harvest RNG. Source fixture pair carrot/apple; local quote/settlement, policy/revision/refund/receipt tests, then real authorized acceptance |
| M6 — Spontaneous world sources | BUG-15 | Landscape3feature removal can stand alone. Q2 only gates structure content. Preserve intentional stems/custom/compatible crops and existing saves; actual new-chunk evidence |
| M7 — Fence joins, collision and path | BUG-11/12/13 | D6 adopted; converge joins before height/path integration.16neighborhoods/orders/mirrors, existing state/reload, normal jump and vanilla fence/gate controls |
| M8 — Creative decoration substrate rules | BUG-10 | Preserve technical structure integrity and S/A permissions; bounded family audit from original discovery; neighbor/reload and mode controls |
| M9 — Functional moongate rendering | BUG-01 | Actual deployed ID/artifact and controlled shader evidence. Small verified renderer fix, independent visibility/teleport gates; no decorative-art task |
| M10 — Integrated acceptance/handoff | All16functional issues | Relevant checks plus one appropriate final baseline, genuine2client/reload/manual cases, clean source build without temp namespaces; separate release authorization |

M1→M3→M4 is the main soil/harvest/feedback chain. M2 coordinates bowl watering in M1 and case precedence but does not require rewriting crop or blacksmith systems. M5 has an independent Rails boundary; crop quality classification does not depend on BUG-17. M6 and M7 can progress independently of harvest odds; M6 native stem preservation does not decide native skill policy. M8 and M9 remain separate compatibility outcomes. No artwork exclusion is an exit gate.

## Genuine remaining questions

| Question | Why evidence does not decide it | Recommendation / actual block |
|---|---|---|
| Q1 vendor identity/context | Local source/logs do not identify the report's NPC/shard/city or live catalog; source seeds are not deployed data | Read-only context/catalog evidence; blocks M5 report certification and exact catalog repair, not confirming mapping omission |
| Q2 structure plants/decorations | Runtime resources show village/mansion/outpost stems/fruit beyond landscape features; current stems retain no origin | Landscape-only removal first; structure expansion pending answer in M6; no existing-world deletion |
| Q3 harvest economics | No current harvest-failure formula exists; yield/seed/gain randomness cannot supply requested odds | One post-eligibility roll, no usable produce/success progress on failure, lifecycle-aware regrowth. Explicit formula/tool/fertility/byproduct/gain/Creative choices before M3 outcomes |
| Q4 native outside custom | Native stems/crops use separate growth/harvest paths | Preserve separate native policy unless expanded; blocks only that extension, not native compatibility inside custom plots |
| Q5 empty-expiry entitlement | Prior-hoed state cannot represent remaining fertilizer uses, especially exact vanilla farmland with no BE | Preserve recorded uses without implicit reset/erase; decide resume/forfeit behavior and bounded persistence before M1 final accounting |

Ordinary engineering choices need no separate permission:1200 online simulation ticks and load reconciliation; prior-preparation remaining-budget snapshot; translatable actionbar plus aimed occupancy; existing partial-can art for0..11; narrow role adapters; copied full-stack ejection with insertion check; injected harvest RNG; existing feature modifier mechanism. These remain reviewable proposals rather than silently claimed user choices.

## Compatibility and backend boundaries

Keep legacy untracked fertility(-1), stage/seed migration, owner/house data and unavailable rehydration, flower soil/origin, perennial root age, saved fence facing and missing WaterCharges=12 fixtures. Never initialize all legacy plots to5, resurrect an overdue flower deadline or accidentally extend ownership through permissive management helpers. Farmer-vendor versus produce-trader direction is authoritative; accepted policy/flags/stock/treasury/prices remain Rails-owned. No prices, seeds or catalog rows changed by discovery.

Local crop/flower thresholds remain mod data; player skill readiness comes from server-loaded Rails state. Rowan successful-harvest integration is a future commit signal only if separately included, never on refused/destroyed harvest or before outcome commit; preserve pickup-quest/direct-insertion distinctions. No quest seed or broader merchant redesign is inferred.

## Validation evidence and stop point

Historical unchanged-HEAD baseline:3462 executed JUnit passes/17skips,1102 existing GameTests,5 original temporary probes. Supplemental existing tests:83passes/0failures/0skips. New6-test probe:5passes,1failed assumption exposing case dismantling. Follow-up initially1pass/1fixture failure; corrected active-area follow-up2passes including16case gestures and native stems/resources. See supplemental ledger for exact commands/limits; no Rails or client acceptance claimed. Do not recycle historical totals as new tests.

Stop at completed discovery. Next task is a playbook, with implementation/release work still separate. No production fix, clean release build or deployment exists from this task.
