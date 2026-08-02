# Farming Skill Progression Implementation Status

Milestone: 17 - Progression Validation, Gameplay QA, and Closeout
Branch: `Farming`
Milestone 12 status: Approved
Milestone 12 commit: `ca696c602ea9fe47b7a8ec30069ba17bc180d5cf` (`docs(farming): inventory plantable content and skill integration points`)
Milestone 13 status: Approved
Milestone 13 commit: `922cc766c34628dd4cd86d49cec01eaeeb91eb4e` (`docs(farming): approve crop skill progression`)
Milestone 14 status: Approved
Milestone 14 commit: `ddc488aa6a423a125510bea1a6556117e9ca6d0e` (`feat(farming): add approved crop skill requirements`)
Milestone 15 status: Approved
Milestone 15 commit: `7db9d1d549640d1389f11aa8416e5e3d2fe325d2` (`feat(farming): gate planting by farming skill`)
Milestone 16 status: Approved
Milestone 16 commit: `cd11434b1ec76f3f36f7e83b8b00b382fcf0c728` (`feat(farming): add player-specific seed identification`)
Milestone 17 status: Approved
Milestone 17 commit: See final progression closeout commit in branch history.

## Final owner approval

Farming Skill Progression implementation: **Approved on the `Farming` branch.**

All 74 approved progression values, server-authoritative cultivation gating, and player-specific planting-item identification are complete. Ordinary identification and cultivation use the same inclusive threshold. The approved grape planting exemption and native compatibility exclusions remain unchanged, and viewer-specific presentation does not mutate stack identity or grant client cultivation authority. All required closeout validation passed.

Deferred acquisition, economy, recipe, natural-generation, and compatibility-expansion work remains unimplemented. No merge, push, release, or deployment was performed or included in this approval.

## Coverage

| Contract | Count |
|---|---:|
| Accepted catalog species | 74 |
| Approved proposal rows | 74 |
| Implemented definitions | 74 |
| Crop definitions | 67 |
| Flower definitions | 7 |
| Unique planting-item mappings | 74 |

The authoritative contract is `FarmingSkillRequirement.minimumFarmingSkill()`. `CropDefinition` and `FlowerDefinition` each store an explicit `float minimumFarmingSkill`; their constructors reject non-finite values and values outside inclusive `0..100`. A primitive field with no compatibility constructor makes every active definition supply a value explicitly. Zero remains a valid approved threshold and is not a missing-value fallback.

The float type matches the existing Farming skill representation and future comparison boundary `current Farming >= minimumFarmingSkill`. Approved values remain whole numbers, so the representation introduces no decimal band policy.

## Definition architecture

- All ordinary annual, root, grain, berry, fibre, trellis, lifecycle-special, compatibility, banana, orchard, and grape species are authoritative `CropDefinition` entries.
- The nine `FruitTreeDefinition` records are structure overlays for already-counted crop IDs. They do not own or duplicate requirements; startup validation confirms each overlay resolves to its authoritative crop and planting item.
- The seven persistent flowers are authoritative `FlowerDefinition` entries.
- Crop lookup/save aliases remain lookup-only and do not create progression species or additional values.
- Grapes remains the existing `CropDefinition` species at Farming 80. No grape registry ID, variety data, component/NBT, planting transaction, rendering, harvest, or viewer presentation changed.
- Six compatibility definitions retain their approved metadata because they are part of the accepted catalog. Native vanilla planting routes are not intercepted or gated.

`FarmingSkillRequirementResolver` is the single read-only path from planting item to existing crop/flower species mapping to authoritative requirement. Milestone 14 committed it without a player skill comparison or planting consumer. Milestone 15 consumes its resolved definition through the central cultivation policy and does not add a second requirement table.

## Validation and bootstrap

`FarmingSkillRequirementValidator` validates definition values, duplicate/conflicting species, duplicate planting items, missing definitions or mappings, exact 67/7/74 coverage, central resolver agreement, fruit-tree overlays, ordinary Poppy Farming 20, and independent Poppy stage-7 Farming 100. Common setup enqueues this validation once after deferred registration is available, so client and dedicated-server common initialization use the same fail-fast path.

The test reconciliation parses `FARMING_CONTENT_MASTER_CATALOG.md` and `FARMING_SKILL_PROGRESSION_PROPOSAL.md` and compares their 74 IDs, planting-item IDs, and approved values to runtime definitions. The complete approved table is not duplicated in test Java.

## Save and network compatibility

The requirement remains immutable definition metadata and is not written to crop, flower, tree, seed-item, or planted-state NBT. Existing worlds already persist species identity and therefore resolve the current definition automatically; no save migration or legacy requirement fallback is needed. Milestone 16 extends the existing server-to-client skill snapshot with a wire version, load state, monotonic per-session revision, and Creative/operator identification-bypass bit. The client stores each snapshot atomically, rejects stale revisions, and clears the connection epoch on login/logout. Identity remains viewer-local derived presentation, never item data.

## Special boundaries

- Ordinary Poppy identification/cultivation metadata is Farming 20. The existing stage-7 mastery constant remains Farming 100 plus stage 6, an approved skinning knife, and mutation authorization. Neither value derives from the other.
- Native vanilla planting behavior remains untouched.
- The existing grape variety, NBT/component, planting, rendering, and harvesting system remains untouched. Milestone 16 projects the localized under-skilled name `a brown seed` without mutating the stack.
- Acquisition, recipes, merchants, natural generation, loot, Rails/economy, and pricing remain deferred.
- Milestone 17 reconciliation, final regression review, and combined runtime closeout are complete and owner-approved.

## Milestone 15 cultivation gate

`FarmingCultivationGate` is the single policy service for new player planting. It returns explicit `ELIGIBLE`, `INSUFFICIENT_SKILL`, `NOT_APPLICABLE`, `UNRESOLVED_SPECIES`, `APPROVED_BYPASS`, `NON_PLAYER_POLICY`, and `SKILL_DATA_UNAVAILABLE` results. Its inclusive boundary is `current Farming >= required Farming`.

`SkillManager` now exposes `NOT_LOADED`, `LOADING`, `AVAILABLE`, and `UNAVAILABLE` states alongside the server-owned value. Login marks data loading before the asynchronous Rails request; success publishes the loaded map before `AVAILABLE`; failure marks `UNAVAILABLE`; logout clears both. Missing, loading, and failed data block cultivation instead of being treated as an authoritative zero. Creative players and real server players with operator permission level 2 or higher use the shared approved bypass. Fake/non-player automation remains denied because the repository has no trustworthy owner-UUID skill contract.

The ordinary-crop gate is in `FarmingBlock.tryPlantSeed` immediately after the held item resolves to an authoritative non-grape crop and before species-specific support/clearance feedback, `FarmingBlockEntity.plant`, block placement, structure creation, sound, item consumption, or Farming awards. This prevents an under-skilled attempt from learning identity through a structure error while eligible and bypassed players still pass through every existing occupancy, support, soil, and orchard-space rule. The shared transaction covers ordinary annuals, grains, fibres, berries, trellis crops, tall crops, roots/tubers/bulbs, lifecycle-special crops, the nine orchard overlays, and compatibility definitions when planted through the Britannia farming block. Native vanilla planting code is not intercepted.

The flower gate is in `FlowerPlantingService.execute` after registered seed/definition and target validation and before the soil snapshot and the only random colour-selection call. A denial therefore cannot replace the block, create or initialize the flower block entity, roll a colour, consume a seed, play the success sound, or write persistent state. Existing successful flower rollback and contention behavior remains intact.

Grape planting remains outside both active gates. Grapes retains Farming 80 metadata, and the established farming-block, grape-item, vine, variety, component/NBT, rendering, and harvesting behavior is unchanged. Milestone 16 changes only the viewer-local projected name below Farming 80 to `a brown seed`.

Denials are server-originated localized action-bar messages. Insufficient-skill messages use only `Unidentified Seeds`, `Unidentified Flower Seeds`, or `Unidentified Planting Material` plus current and required Farming values. They do not expose a species identity. The server consumes the interaction and explicitly rebroadcasts inventory/menu and target-block state on denial, preventing fallback placement and client prediction drift.

## Milestone 16 player-specific identification

`FarmingPlantingItemPresentation` is the single pure identity policy. It resolves the current authoritative species requirement on demand, compares the current synchronized viewer Farming value inclusively, applies the approved Creative/operator bypass, and returns either the existing exact name or the approved localized generic presentation. It does not cache learned identity and never writes to an `ItemStack`.

The approved categories are `Unidentified Seeds` for ordinary custom crop seeds (including seed-named Potato and Yam), `Unidentified Flower Seeds` for the seven flower seeds, and exact localized `a brown seed` for every grape variety below Farming 80. `Unidentified Planting Material` is the fail-closed category for a future unresolved/ambiguous planting material. The six cataloged native vanilla compatibility rows remain explicit `NATIVE_VANILLA_OUTSIDE_SCOPE` mappings under DECISION-012; no vanilla item identity, planting, or recipe behavior is added.

A client-only `ItemStack` mixin projects the viewer-correct name through the shared hover/display-name path used by inventory, hotbar, off-hand, containers, pickup/chat hover, narration, Creative inventory, and Creative search. Unidentified tooltips are reduced to the generic name; advanced mode retains only the approved registry/component diagnostics boundary. A client name-tag event applies the same policy to custom dropped-item labels. Creative search trees and an open Creative search result are refreshed on each accepted snapshot revision. Recipe-viewer integration remains best effort because the repository has no applicable farming recipes.

`SkillSyncPayload` is versioned and carries `NOT_LOADED`/`LOADING`/`AVAILABLE`/`UNAVAILABLE`, a monotonic revision, an explicit identification-bypass bit, and the authoritative skill map only when available. Login publishes `LOADING` before the asynchronous fetch; load success, failure, skill gain/admin set, respawn, dimension change, and game-mode change publish fresh snapshots. The client applies each snapshot atomically, rejects duplicate/stale revisions, resets on connection boundaries, and fails closed until authoritative data arrives. This presentation pipeline is client-only; dedicated-server common classloading does not reference Minecraft client classes.

Identification is evaluated per viewer and per render/query. Two players can see different names for the same logical stack; a skill gain reveals the exact name without reconnecting or rewriting components. The implementation does not alter cultivation eligibility, flower lifecycle/protection/color persistence, Poppy stage-7 mastery, grape variety data, existing plant behavior, saves, acquisition, recipes, merchants, or economy.

### Milestone 16 live validation - 2026-08-01

An ignored disposable copy under `build/m16-live-runtime` ran one NeoForge 21.1.72 / Minecraft 1.21.1 dedicated server, two separate real clients (`M16Low` and `M16High`), and a disposable `m16-world`. No owner world was opened or copied. The server assigned explicit ephemeral Farming snapshots; clients consumed the production `SkillSyncPayload`, `ClientSkillTable`, ItemStack mixin, tooltip, narration, Creative-search refresh, and name-tag event paths. The same server stacks, counts, components, grape variety data, and lack of custom stack names were captured before and after viewer presentation.

- At Farming 0 versus 100, equivalent Carrot, Potato, Corn, Rice, Nightshade, Apple, all seven flower, and two grape-variety stacks produced different approved names per viewer. The low client showed generic names except the approved Farming-0 Carrot identity; the high client showed existing exact names. Wild Grape and Cabernet Sauvignon both rendered `a brown seed` for the low client while retaining their distinct `GrapeVariety` custom data.
- Both clients opened the same chest simultaneously. Corn, Poppy, Wild Grape, and Cabernet Sauvignon were generic for the low viewer and exact for the high viewer. Farming 0 -> 20 -> 100 -> 19 revisions updated that still-open `ContainerScreen` without stack replacement; names and narration followed the current value, and server chest signatures were unchanged before and after.
- Live `LOADING`, API-failure `UNAVAILABLE`, explicit `AVAILABLE`, connection-boundary `NOT_LOADED`, and a deliberately old revision were exercised. All non-authoritative states failed closed. The client retained accepted revision 12 when stale revision 11 arrived. Dimension transitions generated revisions 19/20, respawn generated revision 21, and an actual stopped/restarted server caused both still-running clients to reset to `NOT_LOADED` revision `-1` before accepting the new epoch's LOADING/AVAILABLE revisions.
- `reloadResourcePacks()` completed on both clients with `failure=null`; the synchronized snapshots and viewer-correct inventory, chest, grape, tooltip, and narration components remained correct with no raw localization key or Farming presentation exception.
- Creative at Farming 0 synchronized `bypass=true`; the actual Creative Search tab found `Orfluer Seeds` by `orfluer`. Returning to Survival restored generic names. Operator level 2+ outside Creative synchronized the same approved identity bypass, and de-op restored ordinary policy.
- The live rendered-component audit evaluated all 68 maskable custom planting items and all seven flowers in fail-closed and available states. It reported zero ordinary species leaks in name/normal-tooltip components. Narration was invoked with the actual projected Poppy component at each revision. Advanced registry/component diagnostics remain the approved debug-only disclosure.
- The first run reproduced one production defect: `onClientLogin`, `onClientLogout`, and `onRenderNameTag` existed but were not registered by `ClientModSetup`, so a low-skill custom dropped Poppy label remained exact. The narrow correction registers those three existing handlers. A deterministic integration-boundary assertion was added to `FarmingSkillIdentificationTest`. The corrected two-client run rendered the same custom-named entity as `Unidentified Flower Seeds` for `M16Low` and `Poppy Seeds` for `M16High`; the source stack remained unnamed and component-identical. It also masked ordinary dropped crop, flower, and both grape-variety labels.
- Vanilla has no textual pickup message for this ordinary pickup path; both equivalent Poppy entities were collected and no text/overlay was emitted. This surface is `NOT PRESENT`, not silently counted as viewer-specific text. A normal shared ItemStack chat link serializes the server-composed visible text `[Poppy Seeds]`; the hover stack remains component-preserving and uses the client ItemStack tooltip policy, but the already-serialized visible text is an `UNAVOIDABLE DISCLOSURE` under the approved no-broad-packet-rewrite fallback.
- Vanilla recipe-book farming entries, farming recipe ingredient/results, applicable farming merchant trades, guidebooks, custom farming menus, and JEI/EMI/REI were absent. Those surfaces are `NOT PRESENT` or `NOT APPLICABLE`; no third-party integration was added. The shared chest is the representative supported container UI.
- A forced-identified low-skill presentation could not affect authority: Poppy and Corn evaluated `INSUFFICIENT_SKILL`. A forced-generic qualified presentation could not remove authority: both evaluated `ELIGIBLE`. Grapes remained `NOT_APPLICABLE` to the gate. Existing Milestone 15 live/automated evidence continues to cover successful grape variety planting, native vanilla planting, and existing planted crop/flower behavior after skill loss.
- Inventory/hotbar/off-hand/container movement, repeated revision rendering, Creative search, resource reload, dimension transitions, respawn, and reconnect showed no stack component/name/count mutation, viewer cross-contamination, search rebuild loop, packet-per-tooltip behavior, tooltip/narrator exception, or visible presentation stall. Skill packets occurred only at lifecycle/authoritative update triggers.

Runtime evidence remains ignored and untracked in `build/m16-live-runtime/run/server/m16-evidence-pass1`, `build/m16-live-runtime/run/server/m16-evidence`, `build/m16-live-runtime/run/client-low/m16-client-evidence-pass1.txt`, `build/m16-live-runtime/run/client-low/m16-client-evidence.txt`, and the equivalent `client-high` files. Screenshots are in the two ignored client `screenshots` directories. Disposable skill hooks, drivers, identity exemptions, and custom run configurations were removed after capture; none entered production source. The only production correction is the three missing existing-handler registrations plus its regression assertion.

## Milestone 17 final reconciliation

`FarmingSkillProgressionCloseoutTest` parses the accepted catalog and approved proposal rather than declaring a second 74-value table. In one cross-system loop it reconciles 74 catalog species, 74 distinct planting IDs, 74 `APPROVED` proposal rows, 74 runtime definitions, 74 cultivation decisions, and 74 presentation policies. Results are 67 crops, seven flowers, 68 maskable custom items, six native exclusions, no missing/extra species, no duplicate item, no conflicting value, and every value finite and inside inclusive `0..100`.

For every ordinary applicable species, identification and cultivation use `current Farming >= minimumFarmingSkill` at `requirement - 1`, `requirement - 0.01`, exact requirement, `requirement + 0.01`, and `requirement + 1`. Loading/unavailable state fails closed and Creative/operator level 2+ uses the shared approved administrative bypass. Grapes is the documented exception: viewer identity changes at Farming 80 while established planting remains ungated. The six native compatibility items retain their catalog/reference requirements while native vanilla routes remain unmasked and ungated.

Poppy ordinary identification/planting remains Farming 20. Stage-7 mastery remains an independent stage-6 transaction requiring Farming 100, an item in the skinning-knife tag, and mutation authorization; failed mastery has no durability cost and success costs one. Gate-order assertions keep crop denial before `farmBe.plant`, and flower denial before soil snapshot, colour selection, block/entity state, or item consumption.

### Final progression distribution

| Farming | New | Cumulative | Species unlocked | Category diversity |
|---:|---:|---:|---|---|
| 0 | 4 | 4 | Carrot, Lettuce, Green Onion, Wheat | Root, leaf, allium, grain |
| 5 | 5 | 9 | Potato, Vanilla Potato, Peas, Turnips, Radish | Roots/tubers, compatibility food, pulse |
| 10 | 5 | 14 | Squash, Cabbage, Yellow Onion, Rye, Campion | Gourd, brassica, allium, grain, flower |
| 15 | 4 | 18 | Barley, Oats, Strawberry, Parsnip | Grain, berry, root |
| 20 | 5 | 23 | Pumpkin, Garlic, Flax, Snow Peas, Poppy | Gourd, root/allium, fibre, pulse, flower |
| 25 | 4 | 27 | Beans, Vanilla Pumpkin, Raspberry, Broccoli | Pulse, compatibility gourd, berry, brassica |
| 30 | 5 | 32 | Corn, Mustard, Blackberry, Cauliflower, Hyacinth | Grain, seed crop, berry, brassica, flower |
| 35 | 4 | 36 | Blueberry, Cucumbers, Yam, Rutabaga | Berry, trellis perennial, roots/tubers |
| 40 | 5 | 41 | Tomato, Huckleberry, Cotton, Celery, Snowdrop | Trellis, berry, fibre, stalk, flower |
| 45 | 4 | 45 | Vanilla Melon, Mulberry, Bell Peppers, Rhubarb | Compatibility melon, berry, trellis, stalk |
| 50 | 4 | 49 | Watermelon, Elderberry, Hemp, Lily | Melon, berry, fibre, flower |
| 55 | 4 | 53 | Brown Mushroom, Apple, Peach, Cantaloupe | Mushroom, orchard, trellis |
| 60 | 5 | 58 | Red Mushroom, Cherries, Hops, Pear, Honeydew | Mushroom, orchard, trellis |
| 65 | 4 | 62 | Pineapple, Plum, Tobacco, Foxglove | Fruit, orchard, processing candidate, flower |
| 70 | 4 | 66 | Ginseng, Cranberry, Lemon, Orange | Medicinal, berry, orchard |
| 75 | 3 | 69 | Rice, Lime, Olive | Grain/wetland, orchard |
| 80 | 2 | 71 | Banana, Grapes | Tall perennial, vine perennial |
| 85 | 1 | 72 | Nightshade | Magical/medicinal |
| 90 | 1 | 73 | Mandrake | Magical/medicinal |
| 95 | 1 | 74 | Orfluer | Magical decorative flower |

Phase totals are starter 4, early game 19 (5-20), lower midgame 18 (25-40), upper midgame 17 (45-60), late game 13 (65-80), and endgame 3 (85-95). Food is available in every phase. Fruit starts with Strawberry at 15, expands through berries and melons, opens orchards at 55, and reaches Banana/Grapes at 80. Fibre appears at 20/40/50. Medicinal/magical content is at 70/85/90. Utility/economic/processing candidates span Flax, Cotton, Hemp, Hops, Tobacco, orchards, and Grapes; their actual recipes and prices remain unknown. Decorative flowers are deliberately spaced at 10/20/30/40/50/65/95.

Perennial/recurring choices begin with Campion at 10, Cucumbers at 35, Tomato at 40, orchards at 55, and Banana/Grapes at 80. Trellis species are Cucumbers 35, Tomato 40, Bell Peppers 45, Cantaloupe 55, Hops 60, and Honeydew 60. Orchard species are Apple/Peach 55, Cherries/Pear 60, Plum 65, Lemon/Orange 70, and Lime/Olive 75. Corn 30 and Banana 80 need tall clearance; Grapes 80 uses its established vine route. Special post-planting/environment mechanics include Celery, mushrooms, Cranberry, Rice, Nightshade, Mandrake, all persistent flowers, and Poppy mastery.

The longest gap between ordinary unlock bands is five Farming points. The largest cluster is five species at bands 5, 10, 20, 30, 40, and 60. No band exceeds five and 71/74 species unlock by 80. Review observations, not value changes: 23 species unlock by 20; endgame bands 85/90/95 are intentionally sparse; medicinal/magical content is late; initial survival acquisition is unproven for much of the roster; processing-chain and economy sequencing cannot be validated until recipes, tools, structures, prices, and acquisition exist. Approved low-confidence placements remain Rice 75, Ginseng 70, Nightshade 85, Mandrake 90, Tobacco 65, Grapes 80, and Orfluer 95.

### Milestone 17 scope and deferred register

No production Java/resource behavior changed in Milestone 17. The work is limited to reconciliation tests and final progression documentation. Deferred: survival acquisition, seed recipes, skinning-knife acquisition, natural generation, merchants, economy/Rails pricing, balance revision, processing expansion, formal packet/performance profiling, unsupported third-party recipe viewers, broad chat-component rewriting, arbitrary other-mod planting compatibility, additional species, and nutrient-biased flower-colour weights. A future colour-weight change may only select a colour when a new seed is successfully planted and may never recolour an existing flower.

### Milestone 17 validation execution - 2026-08-02

Reexecuted in Milestone 17 after commit `cd11434b1ec76f3f36f7e83b8b00b382fcf0c728`:

- `FarmingSkillProgressionCloseoutTest`: PASS, three tests after one test-assertion wording correction; no production defect.
- Full clean compile/resource/test gate: PASS, 15 suites and 108 tests with zero failures, errors, or skips.
- Corrective Milestone 11 non-writing placeholder audit: PASS, 182 generated files plus hash ledger.
- `git diff --check`: PASS with only existing LF-to-CRLF warnings.
- Dedicated common bootstrap: PASS through 74/67/7/74 validation with no M16 client-class load; established unrelated `TitleScreenBackgroundMixin`, missing development config, and empty GameTest warnings remain.
- Isolated disposable client startup: PASS through mod discovery, M16 mixin application, resource/model loading, OpenAL, sound engine, and all render-thread atlas creation. No Farming presentation/accessor exception occurred. Existing unrelated crop/other-model errors and missing-texture warnings remain.

Referenced from previously approved evidence because Milestone 17 made no production change:

- Milestone 15 two-client/server planting matrix for crop/flower denial and success, `-1`/exact/`+1` boundaries, decimal boundaries, one-item/off-hand/contention, downstream restrictions, Poppy mastery, protected flowers, persistence, and farming/flower regressions.
- Milestone 16 corrected two-client UI matrix for per-viewer inventory/shared-container/drop-label presentation, open-UI gain/loss, Creative/operator bypass, narration, grapes, stale revisions, reconnect/respawn/dimension/server restart/resource reload, 68-item leakage, and stack equality.

Fresh combined validation was subsequently executed after the Milestone 16 commit and before the Milestone 17 closeout commit in one ignored disposable NeoForge runtime rooted at `build/m16-live-runtime`, using dedicated server world `run/m17-server/m17-combined-world` on localhost port 25617 and real clients `M17Low` and `M17High`. The run passed the representative crop/flower transactions, all-seven flower loop, Poppy mastery, save/restart, reconnect, shared-container UI, dynamic skill gain/loss, administrative bypasses, fail-closed states, session/resource transitions, protected-flower denial, stack immutability, and M11 resource contract. The owner has reviewed and approved Milestone 17 and authorized its isolated closeout commit.

### Fresh combined two-client validation - 2026-08-02

| Area | Fresh runtime evidence/result |
|---|---|
| Runtime and identities | Dedicated server plus two graphical clients; disposable world `m17-combined-world`; ordinary identities `M17Low` and `M17High`. Login, reconnect, server stop/restart, and second login are present in the server/client logs. |
| Skill authority and sync | The ignored harness changed the real `SkillManager` authoritative state and emitted production `SkillSyncPayload` revisions. Clients observed only `ClientSkillTable`. Connection epochs began at `NOT_LOADED`, moved through `LOADING`, and returned to `AVAILABLE`; an intentionally stale real payload was rejected. |
| Initial two-viewer comparison | In the same open chest, Farming 19 rendered Corn, Poppy, Orfluer, Hyacinth, Snowdrop, Lily, and Foxglove generically and both grape varieties as `a brown seed`; Farming 100 rendered exact names, including `Concord grape seeds` and `Wild grape seeds`. Carrot and native Brown Mushroom correctly remained exact at both viewers under their applicable policies. |
| Viewer surfaces | Inventory/hotbar stack projection, off-hand-equivalent stack policy, one shared chest, basic and advanced tooltips, narration result, dropped-label policy, Creative/admin presentation, and Creative-search policy were exercised. Advanced diagnostics retained the approved registry/component boundary. Server-visible chat-link text remains the documented unavoidable disclosure; viewer-local hover remains governed by the same stack policy. No recipe-book or external viewer surface exists for this roster. |
| Stack integrity | Client and server records retain registry ID, count, component map, grape `GrapeVariety`, empty custom-name state, and unchanged shared-container stacks. Presentation wrote no item data. The chest stack component hash stayed unchanged through open-UI gain. |
| Crop denial/success | Farming 19 Corn denial: item same, count 1, FarmingBlock and block entity unchanged, award delta 0, statistic delta 0, zero success effects. Farming 100 Corn success: planted once, count 0, one award invocation, one established sound path, no particle path. |
| Flower denial/success | Farming 19 Hyacinth denial: item same, count 1, soil unchanged, award 0, colour-selection delta 0, zero success effects. Farming 100 success: planted once, count 0, one persistent state, exactly one colour selection. |
| Unified threshold | The all-seven live loop set each flower to requirement-1 and exact requirement. Every below case returned `REJECTED` with count 1 and colour delta 0; every exact case returned `PLANTED` with count 0 and colour delta 1. The closeout suite separately covers all ordinary applicable policies at -1, -0.01, exact, +0.01, and +1. |
| Open-UI skill gain | `M17Low` remained in `ContainerScreen`; revision changed and Poppy changed from `Unidentified Flower Seeds` at 19 to `Poppy Seeds` at exact 20 without stack replacement or component change. New Poppy planting then succeeded. |
| Open-UI skill loss | `M17High` remained in `ContainerScreen`; Corn/Poppy/grapes and higher flowers changed from exact identity at 100 to approved generic identity at 19. New Corn planting denied with count 1. Existing Corn and Hyacinth remained present; growth, care, harvest, colour, and protection remain independent of new-plant eligibility and no eligibility field was saved. |
| Shared-container isolation | Both clients inspected the same chest stacks simultaneously. Low/high names differed exactly by viewer skill, both grape varieties were masked only for the low viewer, and the server stacks/components were unchanged. Reopening after restart reproduced current viewer-local output. |
| Grapes | Concord and Wild Grape components/names were compared. Low rendered `a brown seed`; high rendered each exact variety. Low Farming 0 planting returned the established item result, consumed one, stored `concord`, and the cultivation policy returned `NOT_APPLICABLE`. The variety survived restart. |
| Flowers | Campion 10, Poppy 20, Hyacinth 30, Snowdrop 40, Lily 50, Foxglove 65, and Orfluer 95 all passed below/exact identity and cultivation accounting in the fresh session. |
| Poppy split | Ordinary planting passed at 20. A real stage-6 Poppy stayed stage 6 at Farming 99 with a tagged knife and at Farming 100 with the wrong tool; both failures cost zero durability. Farming 100 plus the tagged knife advanced to stage 7, cost one durability, produced no item, and preserved colour. Reset/recreated stage 6 again rejected Farming 99. |
| Creative and operator | Creative at effective Farming 0 showed exact identities, planted without consumption, and remained subject to occupied/downstream rules. Operator level 2 outside Creative showed exact identities and bypassed the threshold while downstream rules stayed active. De-op returned the ordinary policy. |
| Fail closed | `NOT_LOADED`, `LOADING`, and `UNAVAILABLE` each denied Corn with count 1 and no mutation despite a supplied value of 100. `AVAILABLE` at exact 30 updated identity/eligibility immediately and planted. No maximum-skill assumption occurred. |
| Session transitions | Dimension transfer to Nether and back generated authoritative resync; death caused a real client respawn request and a new revision; resource reload completed successfully at the same revision and retained the generic Poppy name; server restart reloaded current states and both clients reconnected through a fresh epoch. No raw localization key appeared. |
| Save/network authority | Saved crop and Poppy block entities loaded without rechecking planting eligibility. Poppy colour/stage and grape variety persisted. Requirements/presentation results were not written to world or item data. Client display never supplied server skill authority; low denial/high success and stale-payload rejection prove the authority split. |
| Protection | A Creative-planted Poppy saved `protected=true`; an ordinary scissors attempt left state and colour identical, damaged no item, and produced no output. |
| Corrective M11 | 49 canonical stage models, 49 base textures, 49 dye-mask textures, zero separate pass models, and zero third textures. The renderer passes one resolved model twice. Dedicated startup and live client resource reload remained safe apart from the established unrelated TitleScreen mixin warning. |
| Defects/corrections | No production M17 defect. No production correction or regression test was required. Disposable instrumentation and run definitions stayed ignored and were removed after capture. |
| Evidence | `build/m16-live-runtime/run/m17-server/m17-evidence/server.txt`; `run/m17-client-low/m17-evidence/client-low.txt`; `run/m17-client-high/m17-evidence/client-high.txt`; the three runtimes' `logs/latest.log`; and `m17-*-process*.log` in the ignored runtime root. |

Structured performance/network review found cached requirement resolution, immutable client snapshots, event/revision-triggered packets, one Creative search refresh per accepted revision, no Markdown parsing at runtime, and no per-tooltip packet or catalog rebuild. No formal profiler or packet benchmark was run; those remain deferred unless a practical defect appears.

## Working-tree boundary

Milestone 14 is owner-approved and isolated in `ddc488aa6a423a125510bea1a6556117e9ca6d0e`. Milestone 15 is isolated in `7db9d1d549640d1389f11aa8416e5e3d2fe325d2`. Milestone 16 is owner-approved and isolated in `cd11434b1ec76f3f36f7e83b8b00b382fcf0c728`. Pre-existing Corrective Milestone 11 changes remain present, separate, and unstaged. No `.claude/` file was modified.

## Validation result

- Milestone 16 focused identification/requirement/packet selection: PASS, 15 tests.
- Complete repository suite with Milestone 16: PASS, 14 suites and 105 tests with zero failures, errors, or skips.
- Milestone 16 dedicated-server/common bootstrap: PASS. It validated 74 species, 67 crops, seven flowers, and 74 planting items without loading the client-only presentation mixins; the pre-existing `TitleScreenBackgroundMixin` dedicated-dist warning, missing optional development config warning, and empty GameTest-harness `No test functions were given!` message remain unchanged, and Gradle completed successfully.
- Milestone 16 client startup smoke: PASS to completed resource/model loading, OpenAL startup, and render-thread atlas creation with no farming-presentation mixin or accessor error. The process was stopped before entering a world. Existing unrelated asset/model warnings remain.
- Corrective Milestone 11 non-writing placeholder check after Milestone 16: PASS, 182 generated files plus hash ledger.
- Milestone 16 two-client visual/runtime matrix: PASS after one corrected handler-registration defect. Equivalent-stack viewer isolation, shared chest, open-UI gain/loss, all four load states, stale revision, dimension/respawn/reconnect/server restart, resource reload, narration component, Creative/operator bypass and search, two grape varieties, all seven flowers, 68-item leakage audit, stack equality, dropped labels, pickup-path accounting, chat-link fallback, and server-authority regression were executed in ignored runtimes.
- Milestone 14 focused proposal/catalog/runtime reconciliation: PASS.
- Milestone 15 focused gate/flower transaction suite: PASS, 27 tests across four suites.
- Complete farming and flower suite after the validation correction: PASS, 13 suites and 97 tests with zero failures, errors, or skips.
- `cleanTest compileJava processResources test`: PASS, 97 tests.
- Corrective Milestone 11 non-writing placeholder check: PASS, 182 generated files plus hash ledger.
- `git diff --check`: PASS; line-ending conversion warnings only.
- Isolated `runGameTestServer` common bootstrap: PASS. It logged `74 species, 67 crops, 7 flowers, 74 planting items` before the repository's empty GameTest harness reported that no test functions were registered; Gradle completed successfully. No owner world was opened. The pre-existing `TitleScreenBackgroundMixin` dedicated-dist warning and missing optional development config warning remain unchanged.
- An isolated NeoForge 21.1.72 / Minecraft 1.21.1 dedicated server and two separate client runtimes (`M15Low` and `M15Exact`) completed a live server-authoritative pass without opening an owner world. Both real identities logged in with Farming `LOADING`; the configured Rails request failed with HTTP 401 and both transitioned to `UNAVAILABLE`, demonstrating fail-closed outage behavior before disposable server-only fixture values were assigned.
- The live server matrix passed 187/187 checks. It covered all 74 species immediately below and exactly at their approved thresholds, all seven flowers below/exact with selector counts 0/1, the 67/7/74 roster, localized material categories, unavailable states, Creative/operator threshold bypass, fake/null actor denial, per-player snapshots, grapes `NOT_APPLICABLE`, ordinary wheat/mandrake/apple planting, and Poppy 20 versus stage-7 mastery 100.
- Real use-item packets demonstrated that Farming 19 Poppy attempts from the main hand, repeated main hand, and off hand left both eight-seed stacks and the farming block unchanged. Client prediction was unchanged immediately and after reconciliation; the action bar reported `Unidentified Flower Seeds require Farming 20 to plant (current: 19).` Farming 20 consumed exactly one main-hand seed and reconciled to a persistent `FlowerBlock` while leaving the off-hand stack unchanged. The final server snapshot confirmed independent player values and `low_denials_unchanged=true exact_allowed_once=true`.
- Live validation reproduced a feedback defect: potato and mushroom compatibility items used the crop-seed category. `FarmingCultivationGate` now classifies potato, brown mushroom, and red mushroom as unidentified planting material, and a regression test covers all three. The corrected live matrix and full automated suite pass.
- Temporary skill-state and selector-count hooks, the client/server drivers, and isolated run configurations were removed after evidence capture. Evidence remains under the ignored `build/m15-live-runtime/run/*/m15-evidence` and client working directories.
- That initial live pass did not execute every required owner-facing route: native vanilla farmland, live grape placement, direct trellis/tall/berry/orchard/root/specialized structural interactions, same-target ordering, Creative/operator downstream structural failures, loaded-skill recovery/gain/loss, existing-crop behavior after loss, dispenser/command/world-generation/save-load, Adventure operator, or one-item packet cases. The continuation below closes many, but not all, of those rows.

### Final live continuation - 2026-08-01

A second ignored runtime used a fresh disposable dedicated-server world, two separate real client processes (`M15Final` and `M15Peer`), a production-compatible localhost skill endpoint, and real use-item packets. It did not open or copy an owner world. The server restarted the same disposable saved world and both clients reconnected. Authoritative accounting is preserved in `build/m15-live-runtime/m15-final-second-server-accounting.txt`; focused load-state evidence is in `m15-final-server-focus.stdout.log` and `m15-final-client-focus.stdout.log`, with the completed two-client run in the corresponding `*-rerun.stdout.log` files.

Newly executed live coverage:

- Production skill loading reached `AVAILABLE` from the compatible HTTP response. A focused real Poppy packet while the same identity was still `LOADING` left the two-item stack and farming block unchanged, with no selector call, award, statistic, or planted sound. The completed run then exercised available values repeatedly on the same server identities. The separately observed `UNAVAILABLE` outage behavior remains fail closed.
- Real packet boundaries passed for Potato 4.99/5.00/5.01, Corn 29.99/30, Raspberry 24.99/25, Apple 54.99/55, Ginseng 69.99/70, Mandrake 89.99/90, Hops 59.99/60, Cotton 39.99/40, Wheat exact threshold, and Poppy 19/20. Denials were side-effect free; successes used the existing annual, tall, berry, orchard, root/specialized, trellis, fibre, and persistent-flower transactions.
- A one-item real-packet Wheat attempt consumed exactly one item, created one initialized crop, awarded Farming once, and played one planted sound. Rapid repeated packets produced no duplicate placement, duplicate award, duplicate sound, or stack underflow.
- Main/off-hand combinations passed for identical items, different items, eligible-main/ineligible-off, and ineligible-main/eligible-off. The first consumed interaction prevented an unintended fallback hand transaction.
- Same-target two-client crop and flower contention was exercised in low-first and high-first orders across the completed runs. Server accounting recorded one winning mutation, one consumption, one success sound, and (for flowers) one colour selection; both clients reconciled to the authoritative planted block.
- Creative, Survival operator level 4, and Adventure operator level 4 planted below threshold. Creative occupied-target and unsupported Hops attempts still obeyed downstream rules and produced no mutation or success sound. An ordinary Survival player below threshold remained denied.
- Native vanilla wheat planting on vanilla farmland remained outside the gate while Farming was unavailable and preserved vanilla consumption/statistic behavior. Grapes likewise remained outside the active gate and used the existing farming-block transaction without a Farming award. A direct command/system `setblock` path made zero cultivation-gate calls. Dispenser planting behavior is `NOT PRESENT`; world generation does not invoke the player planting transaction and its progression gate is `NOT APPLICABLE`.
- Successful Britannia crop planting consumed once, initialized once, awarded Farming once, and played one planted sound; successful flower planting consumed once, initialized once, selected colour once, played one planted sound, and awarded no Farming. Failed Britannia attempts changed none of those counters. No Britannia planting statistic, advancement criterion, or success-particle hook exists in the production planting sources; the live vanilla control incremented only its vanilla item-used statistic.
- Restart/reconnect preserved the previously planted Poppy persistent state and Potato crop identity/stage/progress, while eligibility continued to come from the current server skill snapshot rather than saved plant or item data.

No additional Milestone 15 production defect was reproduced in this continuation, so no production Java/resource correction was made. Temporary hooks, drivers, endpoint, custom run definitions, and compiled harness classes were removed after log capture.

Those rows were the inputs to the final critical closure pass below. That historical Milestone 15 statement predates the uncommitted Milestone 16 implementation described above.

### Final critical runtime closure pass - 2026-08-01

The closure pass used the ignored `build/m15-live-runtime` copy, a disposable NeoForge dedicated-server world, one real client identity (`M15Closure`), and a production-compatible localhost HTTP response fixture. The fixture delayed the real `player_skills` response while the client issued its first packet, then returned authoritative Farming 5. No owner world was opened. Final accounting is `SUMMARY passed=28 failed=0` in `build/m15-live-runtime/run/server/m15-critical-evidence.txt`; client packet, hand-swing, prediction, and screenshot accounting is in `build/m15-live-runtime/run/m15-critical-client-evidence.txt` and `build/m15-live-runtime/run/screenshots/m15-critical-*`. Earlier two-client agreement remains recorded in `build/m15-live-runtime/m15-final-second-server-accounting.txt` and the paired `m15-final-*-rerun.stdout.log` files.

The uninterrupted load sequence passed on one server player, one network connection, one FarmingBlock target, and the same two-item Potato-seed `ItemStack` instance. Login recorded `LOADING`, Farming 0, connection identity `1858907760`, and stack identity `518749812`. The first real use-item packet failed closed with unchanged count 2 and an unchanged empty FarmingBlockEntity. The delayed production loader then published `AVAILABLE`, Farming 5. The immediately following packet on the same connection, stack, and target consumed one item and planted Potato. There was no stale denial cache, reconnect, item-carried skill value, replacement stack, dimension change, client restart, or second skill system. The earlier live matrix supplies the practical below-loaded-value repeat: Potato 4.99 and Poppy 19 denied, while exact Potato 5 and Poppy 20 succeeded.

Existing-plant behavior after authoritative reduction to Farming 0 matched DECISION-010. An annual Potato accepted watering and fertilizer, grew naturally to maturity, and harvested through the existing root-shovel transaction without another cultivation check. A Hops perennial retained its identity, grew with existing support, harvested with scissors to stage 4, and resumed regrowth. No saved requirement was added. The earlier restart/reconnect pass plus the final post-lifecycle `saveEverything` call cover persistence and confirm that eligibility continues to come from the current server snapshot.

An ordinary Poppy planted at Farming 20, then remained planted after reduction to Farming 0. Its production growth evaluation at the Temperate fixture reported block reason `NONE` and multiplier `0.17897728`; it grew naturally to the stage-6 cap. Watering remained active. Shearing stage 7 reset the same species and stored colour to stage 1; natural regrowth returned to stage 6; an Adventure cutback with a tagged Britannia dagger reset it to stage 1 after the existing ten-tick contention window. Protection continued to derive from planting origin: an administrator-origin protected Poppy denied care after the actor became an ordinary Survival player. No requirement field was persisted and no colour was rerolled.

The real client mastery triplet passed on the same Poppy and preserved its tint: Farming 99 plus the tagged skinning knife stayed at stage 6 with damage 0; Farming 100 plus a stick stayed at stage 6; Farming 100 plus the tagged skinning knife advanced exactly once to stage 7 and charged exactly one durability. No item or seed was produced. Harvest reset stage 7 to stage 1, later natural regrowth stopped at stage 6, and Farming 99 again failed to advance it. Ordinary Poppy planting remains independently 19 denied / 20 permitted.

Bypass was confirmed as threshold-only. Creative on an occupied target and with invalid material, Survival operator level 2+ without Hops support, and Adventure operator level 2+ with blocked orchard clearance all bypassed the Farming threshold but were denied by the original downstream rules without consumption or partial placement. A Creative altitude-invalid Potato could be planted because environment is not a planting-time check, then its production growth evaluation set `growthBlocked=true`; no planting-time climate/altitude failure was manufactured. An administrator-origin protected flower still denied an ordinary actor. Creative/operator status is itself the approved flower-administration authority, so a simultaneous protected-flower denial for that same authorized actor is not an applicable independent policy case.

Client evidence records normal main-hand swing state for the LOADING denial, exact-threshold success, Farming-99 mastery denial, wrong-tool pass-through, and Farming-100 mastery success. Immediate snapshots showed no denial-side item loss or ghost planting; server snapshots reconciled the exact success to one consumed item and authoritative planted state. The earlier two-client pass supplies low/high same-target reconciliation, localized action-bar text, duplicate-feedback, success-sound, and observer agreement. No materially misleading interaction remained. The recurring GeckoLib malformed legacy animation warning is pre-existing and unrelated.

No Milestone 15 production defect was reproduced, so no production Java, resource, or deterministic regression-test change was made. Iteration failures were confined to disposable harness orchestration (retained same-state block entities, an unsuitable Tropical fixture, stale pre-teleport target discovery, and attempting cutback inside the ten-tick contention window). The endpoint, harness sources, identity exemptions, custom username, compiled helpers, temporary server properties, and disposable world were removed after capture. Ignored evidence logs and screenshots remain untracked.

Optional rows not executed are every remaining species through individual real packets, held-click movement across adjacent targets, a full environment matrix, formal video capture, and owner-world testing. They are not critical because the 74-species policy matrix, representative packets, lifecycle closure, mastery triplet, downstream separation, full automated suite, and two-client agreement pass.

Milestone 15 is **Approved**. Milestone 16 is **Approved**.
