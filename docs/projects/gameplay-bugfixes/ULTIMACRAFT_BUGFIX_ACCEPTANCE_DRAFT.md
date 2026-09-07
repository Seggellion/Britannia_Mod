# UltimaCraft bug-fix acceptance draft

The original checklist remains a set of proposed acceptance cases. Evidence-backed implementation results appear in the milestone addenda below; discovery tests alone do not prove fixes. Target: Minecraft1.21.1, NeoForge21.1.72, source baseline `421e27853dde4099d1d794568e33e6709507a53b`, checkout `C:/projects/britannia/mod/Britannia_Mod`. See [discovery](C:/projects/britannia/mod/Britannia_Mod/docs/projects/gameplay-bugfixes/ULTIMACRAFT_BUGFIX_DISCOVERY.md) for actual executed evidence and [planning inputs](C:/projects/britannia/mod/Britannia_Mod/docs/projects/gameplay-bugfixes/ULTIMACRAFT_BUGFIX_PLANNING_INPUTS.md) for adopted D1–D6 and residual Q1–Q5. Supplemental discovery records the new evidence.

**Scope:**19 tracked reports,16 in-scope functional issues. BUG-04/06/07 artwork is excluded; historical inspection does not create an acceptance gate. Full watering-can art is user-supplied; state selection/sync remains code acceptance. Mixed automated/manual cases remain unchecked until both forms of evidence exist.

**A** = automated candidate (pure logic, GameTest or server integration). **M** = manual client/player/visual acceptance. S/A/C = Survival/Adventure/Creative. Adventure tests use ordinarily authorized farms/areas and normal item gestures; mode permissions must not be bypassed by a diagnostic command during the actual action. Diagnostics may set initial skill/weather, prepare a disposable fixture or observe NBT, but players perform the planting, watering, cutting, placing, jumping and filling themselves.

## Shared setup and evidence

- [ ] **M — identity:** record candidate source SHA and built client/server artifact hashes, mod/MC/NeoForge/Java/Iris/Sodium versions, Photon archive hash, GPU/driver, resource packs and relevant settings. Match actual reported block/item IDs, not names alone.
- [ ] **M — isolation:** use disposable local worlds or copies; never change production worlds/settings. Preserve original inventory/world fixture and record coordinates/time/weather/light/mode.
- [ ] **A — old state:** load fixtures with untracked fertility, legacy crop stage/seed, owner/house data, flower soil snapshot/admin origin, perennial roots, old fence states and missing WaterCharges.
- [ ] **M — persistence/multiplayer:** real second client, reconnect, actual chunk unload/reload and server save/restart; distinguish BE serialize/recreate tests from those operations.
- [ ] **A — harness:** tests discovered by intended namespace, skipped tests reported separately, no diagnostic namespace/artifact included in deliverable JAR. Run relevant focused checks and one baseline after final changes.
- [ ] **M — decisions:** expected results below incorporate approved D1–D6. Record resolved Q1–Q5 where applicable before marking dependent accounting/outcome/catalog cases passed; do not reopen adopted D1–D6.

## Issue matrix

| Issue | Automated candidates | Manual/player acceptance |
|---|---|---|
| BUG-01 | Model registration/resource references; BE bounds; root/top cleanup and collision; verify no gameplay dependency on renderer | Three-row shader matrix below, every item surface; walk player/mount through portal; reload and view all sides |
| BUG-02 | One committed success/seed/gain, no duplicate/failed success; root resolution and sync state | Plant normally and read named actionbar; aim at pre-sprout plot; removal/reconnect/two hands/two clients |
| BUG-03 | Deadline boundaries, deterministic dispatch, no unwanted renewal, planted/reversion contract, old fields | Time normal and low-TPS expiry, unload/reload/restart, preserve crops/soil as agreed |
| BUG-05 | Dry dispatcher, unchanged initial moisture/rain/decay, bowl hydration, carrot control | Natural timed dry versus irrigated plots with weather/nearby water/initial moisture recorded |
| BUG-08 | Threshold/readiness + deterministic success/destruction across every route and economics | Normal harvest with required tools, visible outcome, two players, full inventory, perennials and mode policy |
| BUG-09 | Closed valid target set, no invalid consume/convert, current state revalidation | Fertilize prepared soil normally; ordinary/coarse denial without ghost block/count loss |
| BUG-10 | Shared Creative policy, multiblock atomicity, survival-aware persisted exemption | Varied supports and neighbors/reload in C; explicit S/A controls |
| BUG-11 | All16 neighborhoods, order permutations/rotations/mirrors and convergence | Direct L versus L→T→L, all blockstates/images, actual reload; inspect existing models |
| BUG-12 | Collision≥vanilla-height barrier on intended strips, separate outline, no join gaps | Normal S/A jump/walk around ends/corners beside vanilla control |
| BUG-13 | Fence preserves existing dirt_path, vanilla/custom tool distinction, neighbor survival | Existing-path placement/reload/collision; vanilla fence/gate controls; no new under-fence path creation gate |
| BUG-14 | Charges0/1/11/12 and legacy default12 map correctly; no capacity/usage drift | Inventory/held/offhand/dropped full state after fill/dispense/move/pickup/reconnect/reload |
| BUG-15 | Exact loaded feature routes and conditional structure sources; native stem/custom cultivation controls | New affected-biome chunks, existing wild/planted saves, actual block IDs and datapacks |
| BUG-16 | Role/slot resolver for3bowl recipes and7pigments; deterministic single commit both arrangements | Actual use/target precedence, both-hand callbacks, source water, dye preview/anvil/case controls |
| BUG-17 | Counts1/2/3/64, component equality/incompatibility, compatible-first delivery and remainder accounting | Penultimate/final destination, actual manual merge and reconnect if needed, full storage |
| BUG-18 | Canonical mappings, policy/quote/settlement and backend accounting | Exact vendor/shard/city, real harvest vs admin controls and fresh catalog evidence |
| BUG-19 | Root resolution, exact component/count world ejection, case preserved, insertion failure/denial | Empty/usable main hands,2clients, reachable item and visual clear/reload |

## Detailed unchecked cases

### BUG-01 — Portal rendering and independent function

- [ ] **M:** same disposable scene and artifact: NeoForge baseline without Iris/Sodium shaders; Iris1.8.0+Sodium0.6.0 with shaders disabled; same with Photonv1.1 high. Capture placed block, inventory and mainhand in each row. Record packs, shader options, AO/mipmap/light and GPU.
- [ ] **M:** placed portal visible front/back, cardinal/oblique views, near/far and chunk boundary; animation/translucency correct, no flicker or clipping, before/after resource reload and reconnect.
- [ ] **M:** normal placement creates expected single root; collision unchanged; walk through and verify correct teleport for player and supported mount, sound/cooldown behavior. Verify shader visibility independently from teleport.
- [ ] **A:** no missing standalone model or texture; expanded render bounds encompass geometry; obsolete top cleanup does not remove valid portal. JSON/test pass alone does not close visual acceptance.

### BUG-02 — Confirmation and discoverable occupancy

- [ ] **M S/A:** plant lettuce or carrot by right-clicking an authorized prepared plot; one seed consumed, one accurate localized success actionbar; aim immediately at still-invisible stage0 and read crop identity/status.
- [ ] **M C:** successful planting confirms once and does not consume seed; indicator remains accurate without relying on crop texture.
- [ ] **A/M:** immature/already occupied/wrong soil/unsupported/unauthorized/below-skill/unavailable-skill attempts show no success, no extra consume/overwrite/gain; occupancy denial identifies existing crop.
- [ ] **A/M:** mainhand+offhand seeds, seed+care, fast repeated click, two players and deliberately stale client state produce at most one planting commit and one success. Re-read authoritative state, not cached method arguments.
- [ ] **M:** remove/harvest/replant, walk away/back, reconnect, reload chunk/server: label refreshes or clears. Trellis/tall/tree/flower targets resolve actual planted state; unknown data does not claim empty.

### BUG-03 — Expiry boundary and survival

- [ ] **A:** D1 empty timer starts at fertilizer commit, is distinct from hoe3600-tick timer, checks at1199/1200/1201, works when randomTickSpeed=0. Invalid/repeated fertilizer and ordinary care do not accidentally renew it.
- [ ] **A/M:** empty, planted, community, owned-house vanilla farmland use the empty-only expiry contract; planted crops and cultivated flowers never expire; restore community hoed state or exact prior minecraft:farmland, including MOISTURE. Preserve crop/soil/NPK/moisture/ownership and paid-use entitlement unless explicitly specified otherwise.
- [ ] **M:** apply fertilizer normally and time it at normal TPS; repeat with low TPS, showing simulation versus wall-time contract clearly. No debug command performs the actual fertilizer use.
- [ ] **A/M:** unload before deadline, advance elsewhere, return; save/restart before/after deadline; server downtime does not count; loaded-chunk absence counts online simulation time under proposed1200-tick mechanism. Old missing deadline/use tags receive explicit safe defaults.
- [ ] **A:** five-use annual/perennial/fruit lifecycle stays independent; resolve Q5 entitlement before asserting a timeout consumes or preserves remaining uses; community reuse deadline and flower-restored soil do not erase or duplicate uses.

### BUG-05 — Effective moisture versus manual watering

- [ ] **A:** normal randomTick dispatch blocks zero-effective-moisture germination and all relevant transitions. Tests must not bypass the outer guard by directly calling tickGrowth and then call it normal gameplay.
- [ ] **A:** unchanged initial fertilizer moisture, rain, bucket, can, bowl, decay and persisted moisture are authoritative; state/BE disagreement reconciles. No unapproved per-stage water spending or growth-rate rebalance.
- [ ] **M:** naturally plant lettuce and carrot controls in cleared dry plots under roof, clear weather, no nearby water; record initial moisture/nutrients/climate/y, randomTickSpeed, gameTime/wall time and every stage. Compare irrigated plots until control maturity.
- [ ] **M:** repeat no-can but fertilizer/rain-moisture cases: rain qualifies and manual watering is not mandatory when moisture is available. Dry fields cannot be labeled “watered” solely from a past action; wet initialization cannot be labeled “zero water.”
- [ ] **A/M:** reconnect/chunk/server reload does not manufacture moisture or unintended catch-up growth. Tree/flower separate growth families get bounded regression checks if shared care dispatch changes.

### BUG-08 — Eligibility, roll and all economic routes

- [ ] **A:** all67 CropRegistry rows and7 cultivated-flower definitions (native crops outside the custom system remain pending Q4) use one requirement source. For each threshold check below, exactly and above; grapes obey D5 consistently in both planting and harvesting.
- [ ] **A:** NOT_LOADED/LOADING/UNAVAILABLE refuse without RNG/mutation/reward, including threshold0; AVAILABLE uses server value. Specify behavior for backend refresh and reject nonfinite/invalid state as applicable; no client authorization.
- [ ] **A:** deterministic injected RNG covers every approved success/failure/destruction outcome and boundary. Wrong tool, immature crop, denied rights and stale root never roll. Eligible failure destroys produce. Carrots/green onions are consumed; perennial/tree plants follow lifecycle regrowth rather than universal uproot. Minimum refusal is distinct and costs nothing under proposed refusal contract.
- [ ] **A/M:** verify D4 effects separately: produce, seed return, plant survival, annual soil, perennial regrowth/root age, fruit-tree canopy/root, fertile uses, tool durability, XP/skill gain and feedback. No double yield/roll/gain on repeat or failed outcome.
- [ ] **A/M:** normal soil item/empty-hand, trellis, every corn/banana/grape part, ripe fruit scissors, two-handed axe fruit/whole-tree and Adventure forwarding are all accounted. Required tools include HAND versus BARE_HAND, root shovel and grain tag distinctions.
- [ ] **A/M:** two players attempt same root, fast repeated actions, main/offhand, saved state/reload and full inventory; successful inventory overflow drops remainder once, never rerolls.
- [ ] **A/M:** Creative/op behavior follows D4; testing bypass is explicit for minimum gate, RNG, durability, uses, rewards. Survival/Adventure cannot inherit an admin exception accidentally.
- [ ] **A:** fake-player/automation route explicit; environmental destruction/support loss is not misclassified as player harvest. No hopper/dispenser/loot bypass introduced.
- [ ] **A/server integration:** future Rowan successful-harvest signal occurs once after successful commit with agreed planter/actor attribution; destructive failure no success progress/reward. Existing pickup quests and direct inventory insertions tested separately; no guessed backend seed change.

### BUG-09 — Prepared-only fertilizer

- [ ] **A/M S/A:** correct canonical fertilizer on hoed community and empty minecraft:farmland inside the acting player's owned house succeeds once, correct target restored later; ordinary dirt/coarse dirt denied with zero convert/consume.
- [ ] **A/M:** base community, farmland outside owned house or in another owner's house/dimension, occupied plots, separate house-plot blocks, wrong item and both-hand retries follow exact whitelist; no broad dirt tag admits coarse/ordinary accidentally.
- [ ] **M C:** invalid targets remain invalid for fertilizer unless a separately stated requirement changes that rule; valid targets use Creative consumption semantics. Decorative Creative exemption is not a fertilizer whitelist exemption.
- [ ] **A/M:** server/client agree, no ghost conversion or inventory loss; hoe's allowable terrain remains a separate explicit rule.

### BUG-10 — Creative decoration support

- [ ] **M C:** place scarecrow normally on grass, stone, path, slabs/stairs, farm soil and chosen thin surfaces with technically valid space; correct four-part assembly and rotation, no item consumption.
- [ ] **A/M:** test all affected families from discovery audit: common multiblock decorations, fern, hedge, blood pool, stalactite, bottles and triple door; support exemption persists where needed after neighbor changes/reload/restart.
- [ ] **A/M S/A:** retain current substrate/protection and Adventure community rules; no partial assembly/item loss, no unauthorized replacement, preserve paid/protected blocks.
- [ ] **A:** world height/border/unloaded/nonreplaceable/BE-occupied cells and incomplete multiblock geometry still reject safely. Removing a mandatory structure part maintains coherent cleanup; substrate exemption is not permission for malformed pieces.

### BUG-11 — Deterministic joins

- [ ] **A:** all16 NESW neighborhoods × facing/rotation/mirror, placement order and removal/readdition converge to agreed final connected state. Isolated orientation remains intentional; updates terminate without oscillation.
- [ ] **M:** direct L and L→T→L, capture center AND both arm states/images at every step. Include reproduced S/W L with temporary N arm; final south end must not retain history-dependent W/E state difference.
- [ ] **M:** repeat reversed order and four rotations/mirrors; actual chunk unload/reload/server restart preserve correct final join and no stale visuals. Compare existing end/end_mirrored/corner/T models before adding geometry.
- [ ] **A/M:** straights, ends, isolated, T/cross and longer runs remain coherent; same connection interpretation feeds visual and collision shapes.

### BUG-12 — Ordinary movement control

- [ ] **A:** intended posts/arms/ends/corners/T/cross provide vanilla-height collision barrier; outline stays appropriate, no short strip/gap or unintended full-cube obstruction.
- [ ] **M S/A:** on flat ground with normal player attributes, walk/run/jump against straight, end and inside/outside corner beside vanilla oak fence control; cannot clear where vanilla blocks ordinary jumping.
- [ ] **M:** record Creative flight/boosted jump/raised terrain separately, not failures of the level-ground contract. Recheck collision after BUG-11 state changes and reload.

### BUG-13 — Agreed path interaction

- [ ] **M:** record exact path/fence IDs; create dirt_path with vanilla shovel, then place the custom fence: existing path must remain. Test walking/visual join; creating path beneath an already placed fence is not a required feature.
- [ ] **A/M:** vanilla oak fence/path reversion and oak gate/path retention controls retained as baseline evidence; custom intended departure is explicit, no accidental global vanilla change.
- [ ] **A/M:** distinguish vanilla shovel flatten from Britannia dirt/coarse gathering. Check both modes where each action is ordinarily authorized; no unapproved tool-gesture change.
- [ ] **A/M:** neighbors/chunk/server reload and updated collision do not unexpectedly change accepted path state or fence connections.

### BUG-14 — Can appearance from real charges

- [ ] **A:** getWaterCharges0/1/11/12 chooses base/partial/full contract, full only at12, using user-supplied full artwork; missing legacy tag still full12; invalid values clamp identically. Preserve unrelated custom data/capacity.
- [ ] **M S/A:** fill normally from supported water/well/trough; visible full state. Dispense once on a dry valid target: count11 and leave full art immediately. Full target/invalid action does not spend water or falsely alter art.
- [ ] **M C:** charge/consumption behavior unchanged; empty/partial/full render from actual stored state, no forced full appearance just because mode is Creative.
- [ ] **M:** inventory, mainhand, offhand and dropped entity agree at each state after container movement/drop/pickup, reconnect and resource reload; partial may share base art but numeric tooltip remains accurate.

## Corrected cross-system cases

- [ ] **A/M BUG-03:** capture previous hoed blockstate and remaining preparation time before fertilizer replaces its BE; expire to hoed state without immediately reverting through an old overdue PreparedExpiresAt. Check near-expiry preparation and legacy missing tags; no repeated free preparation renewal.
- [ ] **A/M BUG-03:** cultivated flower conversion cancels/suspends empty timer in its persisted soil snapshot; after uproot never resurrect an overdue planting deadline. Verify remaining fertilizer uses and owner under agreed Q5 accounting, without resetting partially spent uses to5.
- [ ] **A/M BUG-09:** exact target center within registered house fullBox (including existing basement boundary), matching dimension and owner UUID; another owner, town resident, delegated manager without ownership, outside boundary, missing/not-yet-rehydrated house all deny. Recheck live authority on commit and after ownership/reload changes; do not use permissive mayManagePlot/no-house fallback as ownership.
- [ ] **A/M BUG-05:** actual bowl_of_water use on dry farm and cultivated flower in each hand hydrates once and returns custom empty_bowl once; full/wrong/protected target does not consume; source filling and fertile-dirt mixing retain their context precedence. Full inventory/remainders conserve counts; approved moisture increment is documented without altering fertilizer's initial moisture or requiring manual watering after rain.
- [ ] **A/M BUG-08:** grapes below80 cannot plant through either general gate or GrapeSeedsItem direct path and cannot harvest through any grape part/root. Cultivated flower planting/harvest reuse poppy20, snowdrop40, lily50, foxglove65, campion10, hyacinth30, orfluer95; preserve separate poppy stage7 knife rule and protected/admin-origin policy.


## New issue acceptance

### BUG-15 — Stop spontaneous sources, retain cultivation

- [ ] **A:** runtime registry after actual datapack/modifier application lacks landscape placed features minecraft:patch_pumpkin, minecraft:patch_melon, minecraft:patch_melon_sparse in intended biomes; unrelated features remain. Configuration overrides/reload cannot silently restore them.
- [ ] **A/M:** new noise-generated chunk fixtures with known seed and affected biomes demonstrate intended absence versus baseline; distinguish registry proof from actual natural generation. Record exact native/custom IDs, positions and loaded packs.
- [ ] **A/M:** Q2 structure scope explicit: village pile features and listed farm/street/mansion/outpost templates either preserved as exclusions or narrowly suppressed in new structures. If included, both direct fruit and generated stems handled; carved-pumpkin decorations distinguished.
- [ ] **A/M:** existing world fruit/stems and saved intentional plots survive load/restart; no cleanup/deletion or global randomTickSpeed change. Native planted pumpkin/melon stems still grow/fruit after fruit removal, including bonemeal route as applicable.
- [ ] **A/M:** custom pumpkin/watermelon and native-seed compatibility inside FarmingBlock still plant/harvest with approved skill/tool/lifecycle and item/recipe availability. Native outside-system skill policy follows Q4 without accidental new bypass or restriction.
- [ ] **A:** managed vegetation/wild resources and unrelated native crops retain their registrations/rates; no broad event cancellation masquerades as source removal.

### BUG-16 — Logical recipes in interchangeable hands

- [ ] **A/M:** empty_bowl+dirt→bowl_of_dirt; bowl_of_dirt+dung→bowl_of_fertile_dirt; bowl_of_fertile_dirt+bowl_of_water→fertilized_dirt+2empty_bowl all activate with either arrangement through actual item-use dispatch.
- [ ] **A/M:** dye_tub with each madder_red/woad_blue/verdigris/weld_gold/soot_black/chalk_white/ice_blue in either hand loads same pigment; retains tub/components, consumes actual pigment1 outside Creative. Same pigment, disabled/missing definition, unavailable registry and nonpigment follow no-mutation/fallback contract.
- [ ] **A:** role resolution returns original input slots/snapshots; one/two/64 inputs, stale hand swaps and component changes revalidated before mutation. Same-item pairs and truly ambiguous recipe matches resolve deterministically or reject without consuming.
- [ ] **A/M:** both-hand callbacks, reversed callback attempts, fast physical repeated activation and client/server prediction commit at most once per gesture; distinct allowed clicks still craft. No duplicate sound/reward/consume/remainder and no follow-on craft from returned bowls.
- [ ] **A/M:** accepted match owns gesture; nonmatch preserves food use, source-water bowl filling, planting/watering/protected targets, display storage/ejection, dye preview and blacksmith anvil GUI. No unrelated skill/permissions bypass.
- [ ] **A:** Creative cost semantics unchanged for existing recipes unless separately approved. No durable tool exists in identified immediate recipes; if later added, test actual-slot damage/exhaustion/break event, never damage the other hand by assumption.
- [ ] **A/M:** output/remainder placement with partial/full storage and stale client inventory preserves total counts/components and one final outcome; no second craft or reroll on insertion failure.

### BUG-17 — Final output insertion and components

- [ ] **A/M:** all3bowl recipes, both arrangements, counts1/2/3/64; preload compatible output below cap and leave free slots. For count64 ensure compatible partial capacity remains at last craft (e.g. two initial output stacks). Log every before/after hand, destination and relevant callback/event.
- [ ] **A:** penultimate versus exhausting craft produces identical intended registry ID/full components; compare custom data/quality/damage/name/owner/origin, absent vs empty values, full map and patch. No read from already-consumed input or shared mutable output reference.
- [ ] **A/M:** compatible last output joins existing stack before empty-hand fallback. Test normal inventory insertion and actual client manual merge/reconnect when visual discrepancy remains; server totals and displayed stacks agree.
- [ ] **A:** intentionally different quality/name/ownership/origin components remain incompatible; no stripping/normalization used to hide metadata differences.
- [ ] **A/M:** nearly full/full inventory, damaged/unstackable controls, exhausted input and final-mix2bowl remainder all conserve inputs/outputs. No overwrite, loss, duplicate, refund/reroll of completed outcome or consume of returned remainder during same activation.

### BUG-18 — Intended produce sale with authoritative accounting

- [ ] **M/read-only:** identify NPC/world public ID/type/kind, shard/city, deployed artifact and exact error/missing row. Distinguish produce_trader from farmer seed vendor. Capture fresh effective policy/catalog revision and loaded rows/prices/flags/stock/treasury; seed source alone is not proof.
- [ ] **A/M:** canonical britannia_mod:broccoli and britannia_mod:orange classify to intended exact commodity names/category; spelling/namespace/singular-plural invalid IDs do not silently become another commodity. Same-vendor carrots/apple positive controls under comparable conditions.
- [ ] **A/M:** genuine harvested quality/provenance, named and default/admin stacks; quote offer and reserved stack components recorded. Intended acceptance independent of incidental components; meaningful metadata retained; no unapproved quality/price redesign.
- [ ] **A/server integration:** server quote filtering→Rails valuation→client products→sell request→live inventory reservation→Rails settlement→currency/receipt all agree. Exact city/type resolved from authoritative assignment; no client price/category/identity claim trusted.
- [ ] **A:** correct refusals for missing/empty policy, wrong profession/shard/city/NPC, missing commodity, npc_buy_enabled=false, stock cap, insufficient treasury, below-minimum value, unavailable/stale catalog. npc_sell_enabled tested separately as opposite direction.
- [ ] **A:** legitimate existing price/config source used; no invented prices or seed-run test pretending to prove deployed data. Registry/catalog refresh/revision and opening new screen reflect corrected data without stale acceptance.
- [ ] **A/server integration:** success removes requested real stacks once, moves city stock/treasury once, grants returned currency once; refusal/refund/retry/idempotent replay/disconnect/crash use existing durable receipt policy with no loss/duplication. No production sale needed for automated coverage.
- [ ] **A:** related67crop mapping audit updated, but no automatic buy-all-food/seed/flower/reagent/processed policy. Any further additions have explicit intended merchant/category/catalog evidence.

### BUG-19 — Content-only world ejection

- [ ] **A/M:** occupied lower/upper case, independent/end/straight/corner/T/cross; offhand decorator wins the earlier callback with empty, stick, food, placeable, bowl or other usable mainhand. Case cells/facing/connections remain unchanged; stored contents eject into world, not player inventory.
- [ ] **A/M:** normal/sneaking, decorator main-only/off-only/both, empty case, missing/malformed/removed root, adjacent independent cases; preserve meaningful mainhand behavior and unrelated decorator operations, never generic single-cell rotation/dismantling of case.
- [ ] **A:** named/damaged/quality/custom-data/owner/origin/absent-empty component stacks; normal count1 and legacy multi-count retain exact full components/count through BE persistence and ejection. Do not reconstruct only type.
- [ ] **A:** authoritative root permission, protected/foreign house where applicable, player/world denial and stale state all leave case/contents unchanged; denied matched gesture cannot fall through into inventory transfer/rotation/storage.
- [ ] **A:** item-entity insertion rejection/exception where reproducible preserves exact stored stack and leaves no provisional duplicate. Validate world-drop settings/snapshot conditions intentionally rather than relying on void popResource after clearing.
- [ ] **A/M:** duplicate hand events/activation and2players yield one ejection; reentry guard/live-state check prevents callbacks from clearing a replacement item or spawning twice.
- [ ] **A/M:** full player inventory still ejects to safe reachable world position; case+player+world counts/components conserved. Neither case-item drop nor case destruction substitutes for content ejection.
- [ ] **A/M:** success reload remains empty; failure reload retains contents; clients receive one clear/update, displayed item disappears without ghost after reconnect/chunk/server reload. Actual2client evidence, not BE serialization alone.

## Completion record to fill later

- [ ] Each issue has named tester, artifact identity, test date, exact command or player steps, result and evidence location.
- [ ] All automated required checks pass with skipped tests explained; visual/manual cells have actual client evidence, not inferred passes.
- [ ] Existing-world, multiplayer and approved economics decisions verified; scope exclusions explicit.
- [ ] No production deployment implied by this checklist. Final implementation handoff separates accepted fixes, remaining limitations and release authorization.

## Material changes from original draft

D1–D6 are adopted, not pending reconfirmation. Expiry now restores prior hoed soil; eligibility is owner-contained vanilla farmland plus prepared community; bowl hydration and unchanged initial moisture/rain are explicit; grape/flower gates and lifecycle-aware produce destruction replace exemptions/universal uproot; existing path preservation replaces ambiguous shovel outcomes. Excluded artwork rows and aesthetic checklist were removed. All other original functional coverage remains, with new BUG-15–19 cases and residual Q1–Q5 identified. No box was checked by discovery.


## Implementation evidence addendum — M1

The playbook and kickoff close the historical Q1–Q5 recommendations. Original mixed A/M boxes remain unchecked where physical client evidence is still owed. M1 checks below describe server evidence precisely; final run identity lives in scratchpad/handoff.

- [x] **A:**1199/1200/1201 boundaries across all8prior farmland moisture states; exact restoration removes fertilizer BE and residual entitlement.
- [x] **A:**saved community preparation budget pauses/resumes and re-fertilization cannot grant fresh preparation time.
- [x] **A:**registered loaded BE ticker reconciles an overdue saved deadline independently of randomTick dispatch. Actual chunk unload/restart is still pending.
- [x] **A:**crop planting cancels private timer; flower forward snapshot clears it; rollback restores original timer/origin/uses; uproot never revives it. Private owner survives snapshot/care/NBT/rollback/uproot.
- [x] **A:**target/mode whitelist and registered owner/dimension/full-box/basement/missing authority checks; invalid fertilizer leaves count/target unchanged.
- [x] **A:**MAIN phase uses actual MAIN/OFF bowl, redundant OFF callback cannot spend again; full/foreign soil and protected flowers refuse free; one full-inventory remainder world drop.
- [x] **A:**legacy untimed private soil remains untracked(-1) with no invented deadline.
- [ ] **M:**physical input, normal/low-TPS timing, two clients, actual chunk unload and server restart, ordinary watering and rain/dry progression observations.


## Implementation evidence addendum — M2

- [x] **A:**three bowl recipes ×both hand orientations ×counts1/2/3/64 through registered server use dispatch;420craft matrix, repeated/reversed OFF callbacks never add a commit.
- [x] **A:**compatible final output merges into pre-existing partial stacks; named incompatible output stays separate; exact2empty-bowl remainders per final mix, full/near-full inventory conservation and Creative costs.
- [x] **A:**all7pigments ×both hands ×Survival/Creative load the intended pigment and preserve tub name/count; same pigment does not charge again. Existing unavailable/disabled/missing registry tests remain passing.
- [x] **A:**immutable actual-role snapshots; stale component/count checks; alias/ambiguous recipe rejection; deterministic equal-item role selection; canonical outputs do not inherit input names.
- [x] **A:**separate legitimate clicks can craft in one tick; no blanket cooldown. Original protected-farm/flower server regressions remain passing.
- [x] **A:**aimed water-source filling takes precedence over dry crafting in either hand; one bowl filled, dirt/source preserved, redundant callback free.
- [ ] **M:**physical input and menu synchronization/reconnect, final-output display and water-source targeting, dye preview/anvil/case fallback interaction checks.
