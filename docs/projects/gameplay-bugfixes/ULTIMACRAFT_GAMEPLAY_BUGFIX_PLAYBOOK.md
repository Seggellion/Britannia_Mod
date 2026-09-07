# UltimaCraft gameplay bug-fix project playbook

## 1. Purpose and completion state

This playbook implements and verifies the sixteen functional defects tracked by the September 2026 gameplay discovery. It covers the Britannia NeoForge mod and the bounded Rails economy work required for produce buyback.

The project is complete when every in-scope issue has an implemented, tested outcome; the full automated gates pass from clean ordinary build inputs; required manual/client checks are recorded; each repository has reviewable local commits; and the handoff identifies any external acceptance still owed. Completion does not authorize pushing, merging, deploying, running production seeds, or changing a production world.

The source discoveries were made against:

| Repository | Discovery branch and revision |
|---|---|
| `C:/projects/britannia/mod/Britannia_Mod` | `patch-18` at `421e27853dde4099d1d794568e33e6709507a53b` |
| `/home/dusti/ultimacraft-website` | `release/public` at `a9425ca41afa9b4ba7539426044e7b50e70a3966` |

These revisions are inputs, not assumptions about the checkout when execution begins. M0 records the actual bases and reconciles intervening changes.

## 2. Authoritative inputs

Read these documents before changing code:

- `ULTIMACRAFT_GAMEPLAY_BUGFIX_PLAYBOOK.md`
- `ULTIMACRAFT_BUGFIX_DISCOVERY.md`
- `ULTIMACRAFT_BUGFIX_SUPPLEMENTAL_DISCOVERY.md`
- `ULTIMACRAFT_BUGFIX_PLANNING_INPUTS.md`
- `ULTIMACRAFT_BUGFIX_ACCEPTANCE_DRAFT.md`

The discovery documents preserve evidence and history. The decisions below are authoritative when an older recommendation conflicts with them.

## 3. Adopted product and gameplay decisions

### Soil and hydration

1. Fertilized soil has a 1,200 simulation-tick planting window while it is empty. At ordinary 20 TPS this is 60 seconds.
2. The timer advances only while the server is running. It may expire while the chunk is unloaded if the server continued running; reconcile it when the plot loads.
3. Planting a crop cancels the empty-soil deadline before the crop transaction commits.
4. Expired soil returns to the exact prior hoed-soil stage: `community_hoed_farm_block` for community soil and the captured `minecraft:farmland` state for house soil.
5. The fertilizer application and all unused fertile-harvest entitlements are lost on empty expiry. No invisible entitlement remains for later reuse.
6. The remaining community hoe-preparation budget pauses while the plot is fertilized and resumes when empty expiry restores hoed soil. Repeated fertilizing cannot refresh that preparation budget.
7. Fertilizer may be applied only to an eligible prepared community plot or empty `minecraft:farmland` inside the acting player's owned house. Ordinary dirt, coarse dirt, base community soil, farmland outside the owned house, and farmland in another player's house are invalid.
8. House eligibility uses authoritative dimension, block position, full house bounds, and owner UUID. Town membership, nearest lot, client claims, and permissive plot-manager fallbacks are not ownership.
9. Buckets, watering cans, bowls of water, and rain hydrate crops. Preserve existing fertilizer moisture of 1, watering-can increment of 1, bucket-full behavior, and rain behavior. A bowl adds 1 hydration and returns one custom empty bowl. Invalid or already-full targets consume nothing.

### Farming skill and harvesting

10. Planting and harvesting use the same species requirement. Grapes lose both planting bypasses. Cultivated flowers use their existing species requirements for planting and harvesting.
11. Native crops grown outside UltimaCraft's custom cultivation system retain vanilla behavior. Native-compatible seeds planted in an UltimaCraft `farming_block` use the custom rules.
12. A player below the requirement is refused before RNG, tool damage, fertilizer-use consumption, mutation, skill progress, drops, or quest progress.
13. An eligible harvest succeeds with:

   `min(95%, 75% + (Farming skill - required Farming skill) × 1%)`

   Exact-threshold skill therefore succeeds 75% of the time. Inject the random source for deterministic tests and roll once per resolved crop/root harvest attempt.
14. An eligible failed harvest destroys all usable produce and harvest byproducts, including seeds and straw.
15. Annual crops complete their normal harvested lifecycle on failure. Carrots and green onions are therefore removed. Perennials, trellises, and fruit harvested without destroying the plant return to their normal regrowth state. An axe action that normally removes a fruit tree still removes it, but produces no fruit when the harvest outcome fails.
16. Eligible failures consume the normal tool durability and one fertile-soil use, and run the existing harvest skill-practice opportunity. They never award produce, successful-harvest quest progress, or economy rewards.
17. Creative/admin testing bypasses the minimum gate, failure roll, tool durability, fertilizer-use cost, and harvest skill rewards. It produces the deterministic success outcome so administrators can test crops.

### World generation, economy, and item interactions

18. Remove the three random landscape features `minecraft:patch_pumpkin`, `minecraft:patch_melon`, and `minecraft:patch_melon_sparse` from new world generation. Preserve player-grown native stems, custom crops, existing world blocks, and structure-authored pumpkin/melon content. Villages are not used by this project, so structure rewriting has no value here.
19. Immediate two-item pseudo-crafting recipes work with their logical ingredient roles in either hand. One activation commits at most one recipe and charges the actual role-bearing slots.
20. Compatible crafted outputs merge into compatible inventory stacks before using an emptied hand or empty slot. Meaningful components are never stripped to force stacking.
21. The produce trader should buy every fruit and vegetable grown through the supported UltimaCraft cultivation system. Continue excluding seeds, flowers, reagents, textiles, grains, tobacco, and processed foods unless another established merchant policy already supports them.
22. The reported produce-vendor case is the Jhelom produce trader on the Britannia shard. Use that identity for read-only effective-policy and live acceptance evidence.
23. Using the interior decorator from the offhand on an occupied display case ejects the exact stored stack into the world and leaves the case intact.
24. Using the decorator from the main hand rotates the whole two-cell display case atomically. It must never rotate one cell, dismantle the case, delete contents, or create duplicate drops. If the complete rotation cannot commit safely, leave the case unchanged.
25. Placing the custom fence on an existing vanilla-shovel-created dirt path preserves the path. The project does not add a separate ability to create a path beneath an already placed fence.

### Asset boundary

BUG-04, BUG-06, and BUG-07 are excluded because the user owns the lettuce and green-onion asset polishing. Codex must not recolor, redraw, smooth, replace, or generate those assets. The user also supplies the full watering-can artwork. Codex owns the charge-based state selection and integration. Functional moongate shader compatibility, fence state/model selection, and display synchronization remain code work.

## 4. Issue ledger

| Issue | Required outcome | Milestone |
|---|---|---|
| BUG-01 | Placed moongate is visible with Iris 1.8.0 and Photon v1.1 while remaining functional | M10 |
| BUG-02 | Server-confirmed planting message and aimed occupied-plot status | M5 |
| BUG-03 | Deterministic online empty-soil expiry to prior hoed state | M1 |
| BUG-04 | Excluded lettuce artwork | Excluded |
| BUG-05 | Correct hydration gates plus working bowl hydration | M1 |
| BUG-06 | Excluded green-onion artwork | Excluded |
| BUG-07 | Excluded green-onion artwork | Excluded |
| BUG-08 | Matching planting/harvest gates and lifecycle-aware destructive failures | M4 |
| BUG-09 | Fertilizer target whitelist for community and owned-house farmland | M1 |
| BUG-10 | Creative decoration placement without gameplay substrate restrictions | M9 |
| BUG-11 | Deterministic L-shaped and other fence connections | M8 |
| BUG-12 | Vanilla-height fence collision | M8 |
| BUG-13 | Existing dirt path survives custom fence placement | M8 |
| BUG-14 | Watering-can full state follows actual stored charges | M5 |
| BUG-15 | Random landscape pumpkins/melons suppressed | M6 |
| BUG-16 | Pseudo-crafting hands are interchangeable | M2 |
| BUG-17 | Final crafted output merges normally | M2 |
| BUG-18 | Complete supported produce buyback, including broccoli and oranges | M7 |
| BUG-19 | Offhand content ejection and atomic mainhand case rotation | M3 |

There are sixteen functional issues in scope and three excluded artwork issues.

## 5. Repository and change-management rules

1. Read every applicable `AGENTS.md` before work in each repository.
2. Preserve the user's existing tracked and untracked work. Never clean, reset, checkout over, or delete unrelated files.
3. Create an isolated NeoForge worktree from the current `patch-18` head using a dedicated local branch such as `claude/patch18-gameplay-bugfixes`. If the branch or worktree already exists, inspect it and reuse it only when its ancestry and contents match this project.
4. Copy the discovery, planning, acceptance, playbook, and kickoff documents into `docs/projects/gameplay-bugfixes/` in the project worktree. Do not move or delete the originals.
5. Create an isolated Rails worktree and local branch only when M7 begins. Base it on the then-current intended `release/public` line after recording its head and divergence from the discovered revision.
6. Keep NeoForge and Rails commits separate. Record paired M7 commit SHAs in both scratchpad and handoff.
7. Do not touch the Fabric/Atrevion repository.
8. Do not push, merge, deploy, run production seeds/migrations, alter production data, or change production server/world settings.
9. Use one or more focused local commits per milestone. Do not mix unrelated milestones merely because they touch the same dispatcher or registry.
10. Before each commit, inspect the complete diff and confirm that generated files, diagnostic worlds, credentials, logs, IDE files, and unrelated user changes are absent.
11. Maintain `docs/projects/gameplay-bugfixes/ULTIMACRAFT_GAMEPLAY_BUGFIX_SCRATCHPAD.md`. Record current milestone, starting/ending SHAs, decisions used, files changed, commands and results, manual evidence, risks, and next action.
12. Maintain `docs/projects/gameplay-bugfixes/ULTIMACRAFT_GAMEPLAY_BUGFIX_HANDOFF.md` as milestones complete. Check acceptance boxes only when the required evidence actually exists.

## 6. Shared engineering invariants

- The server owns permission, skill, RNG, inventory, soil, crop, case, and economy mutations. Client state may present outcomes but never authorize them.
- Re-read live block entities, hands, inventory stacks, ownership, and roots immediately before commit. Stale previews and callbacks cannot overwrite newer state.
- One player gesture produces at most one committed mutation, sound/reward set, drop set, skill attempt, or transaction.
- Item counts and full data components are conserved across input consumption, output insertion, case storage/ejection, drops, rejection, retry, disconnect, and full inventories.
- Below-threshold refusals and invalid targets are free. Eligible failed harvests follow the adopted cost contract.
- Multiblock operations resolve one authoritative root. Never mutate one visual part independently of the root transaction.
- Existing-world compatibility must cover legacy crop age/stage, fertility `-1`, flower snapshots/origins, perennial roots, fence facing, house ownership, and watering cans whose missing `WaterCharges` means 12.
- A source file, JSON contract, or headless test cannot prove shader rendering, HUD behavior, physical jumping, client hand dispatch, world generation distribution, or display synchronization. Use the appropriate manual/client evidence.
- Temporary diagnostic namespaces and generated build outputs are not release inputs. Rebuild normally before final artifact verification.

## 7. Milestone execution protocol

For every milestone:

1. Update the scratchpad with the intended scope and current repository SHAs.
2. Reproduce the relevant failure or preserve the discovery's exact reproducible evidence.
3. Add or repair the smallest authoritative implementation boundary.
4. Add focused tests that prove the requested behavior and important conservation/negative cases.
5. Run the focused tests. Run broader suites only where shared code creates a concrete regression risk.
6. Perform required manual/client checks when the environment supports them. Record pending external checks candidly.
7. Inspect the diff, update the planning/acceptance/handoff documents, and commit the milestone locally.
8. Continue to the next milestone without waiting for reconfirmation of adopted decisions.

Stop only for a true blocker that cannot be resolved from the repositories, this playbook, or safe local testing. Document the exact blocker and continue independent milestones before returning to the user.

## 8. M0 — Isolated workspaces, baseline, and contract freeze

### Objective

Create safe workspaces and establish the evidence baseline without changing production behavior.

### Work

- Record current branch, full head, status, worktrees, remotes, build/runtime metadata, and relevant divergence in the NeoForge repository.
- Create/reuse the isolated mod worktree and project branch. Bring the five authoritative documents into its project-doc directory.
- Confirm all sixteen functional issues and three exclusions in the scratchpad.
- Locate the historical test logs from discovery. If the mod base is still `421e278...` and logs are intact, preserve the historical baseline of 3,462 executed JUnit passes, 17 skips, and 1,102 existing GameTests without pretending it is a new run. Run focused smoke tests to prove the isolated worktree is healthy. If the base differs or evidence is missing, run a new ordinary baseline.
- Remove diagnostic inputs from the active build configuration and prove ordinary source sets are in effect. Do not delete preserved discovery evidence.
- Record Iris, Sodium, Photon, GPU/driver, client settings, and current JAR identity available for M10. Build identity at M0 is diagnostic context, not a release candidate.

### Exit gate

- Isolated branch/worktree established with user work preserved.
- Scratchpad and handoff created.
- Healthy focused baseline, or a fully recorded pre-existing failure classified before feature changes.
- No production behavior changes.

## 9. M1 — Soil eligibility, empty expiry, and bowl hydration

### Objective

Implement BUG-03, BUG-05, and BUG-09 as one coherent soil-state lifecycle.

### Work

- Replace the ordinary-dirt fertilizer branch with an explicit eligibility resolver for prepared community soil or empty vanilla farmland inside the acting player's owned house.
- Check ownership, dimension, full-box containment, block identity, occupancy, and community access on the server and revalidate them at commit.
- Persist enough origin data to restore the correct prior hoed state. Capture vanilla farmland moisture and the remaining community preparation budget. Default legacy data conservatively without initializing every existing plot to five uses.
- Start a 1,200-game-tick empty deadline on successful fertilization. Evaluate loaded plots deterministically and reconcile overdue plots on chunk/block-entity load. Dimension game time provides online-only timing.
- On empty expiry, discard the fertilizer application and remaining uses, restore prior hoed soil, and resume the saved community preparation budget relative to current game time.
- Cancel the empty deadline before successful crop or flower occupancy commits. Flower snapshots must not resurrect expired deadlines or reset partly consumed fertilizer uses.
- Add bowl-of-water care through the existing soil/flower-care abstraction for either actual hand. A successful bowl use adds one hydration and returns one custom empty bowl. Preserve source-water filling and fertilizer-mixing precedence.
- Preserve watering can, bucket, rain, initial moisture, dry growth gating, ownership, house data, and paid plot behavior.

### Required automated evidence

- Eligibility matrix: community prepared/base/fertilized/occupied; ordinary/coarse dirt; owned-house farmland; boundary, basement, other dimension, foreign owner, outside house, missing rehydration; Survival/Adventure/Creative as applicable.
- No conversion or consumption on invalid targets or stale ownership.
- Expiry at 1,199/1,200/1,201 ticks; `randomTickSpeed=0`; chunk unload/load while server runs; server stop/start with no offline advancement; low-TPS semantics; planting immediately before expiry.
- Exact restoration of community and farmland states; saved preparation time resumes; repeated fertilizing cannot refresh it; fertilizer entitlement is gone after expiry.
- Bowl hydration in both hands on crops and cultivated flowers; correct remainder and full-inventory behavior; full/protected/wrong targets do not consume.
- Existing rain/can/bucket and zero-effective-moisture growth controls remain correct.

### Exit gate

Focused soil, house-rights, flower-snapshot, hydration, serialization, and GameTests pass. Normal farming from preparation through planting remains functional. Commit and record the SHA.

## 10. M2 — Interchangeable pseudo-crafting hands and output delivery

### Objective

Implement BUG-16 and BUG-17 without rewriting unrelated crafting or block interaction systems.

### Work

- Create a small role-aware resolver for the three bowl recipes and seven dye-tub pigment variants identified in discovery.
- Match logical driver/input roles independently of hand arrangement and return the actual slots plus immutable stack snapshots.
- Preserve each service's existing transaction/revalidation boundary, costs, Creative semantics, dye state, sounds, and remainders.
- Ensure an accepted interaction owns the gesture and cannot commit again during the other-hand callback. Nonmatches return control to unrelated interactions.
- Preserve source-water bowl filling, protected farm interactions, food use, planting, dye preview, and blacksmith GUI behavior.
- Build every output once. Insert into compatible stacks first, then a suitable empty inventory slot/hand, then one world drop if necessary. Apply the same conservation discipline to returned bowls.
- Do not normalize names, quality, ownership, origin, damage, or custom components to force a merge.

### Required automated and client evidence

- Every bowl recipe and pigment in both hand arrangements through actual dispatch.
- Counts 1, 2, 3, and 64; final craft; compatible partial stacks; intentionally incompatible stacks; nearly full/full inventories; exact remainder totals.
- Main-then-off and reversed callback probes, rapid separate clicks, stale hand swaps, same-pigment no-op, unavailable pigments, ambiguous/nonmatching inputs.
- Exactly one input/output/remainder/sound/reward set per activation.
- Actual client check that hand swapping works and the final output appears in the merged stack after reconnect.

### Exit gate

Both arrangements are behaviorally identical for each recipe, final outputs stack correctly, all components/counts are conserved, and unrelated interactions retain their established precedence. Commit and record the SHA.

## 11. M3 — Transactional display-case decorator behavior

### Objective

Implement BUG-19 and eliminate single-cell display-case dismantling.

### Work

- Add case-specific decorator recognition at the display-case/root interaction boundary early enough to own the correct gesture before ordinary case transfer, item placement, or unsafe generic rotation.
- Offhand decorator on an occupied case resolves either cell to the root, checks live permissions, copies the exact stored stack, spawns one reachable world item entity, confirms insertion, then clears/synchronizes the still-matching stored stack.
- Empty cases, denied access, malformed roots, rejected spawns, exceptions, and stale replacements leave storage and structure unchanged.
- Mainhand decorator rotates both cells as one atomic root transaction, preserves contents, recalculates neighboring case connections, and rolls back fully if the complete rotation cannot commit.
- If both hands hold decorators, the mainhand action has normal Minecraft precedence and performs one whole-case rotation. An offhand-only decorator performs content ejection.
- Generic decorator logic must exclude individual display-case parts after the case-specific path is installed.

### Required automated and client evidence

- Root/upper cells; independent/end/straight/corner/T/cross neighbors; normal/sneaking; empty/stick/food/placeable/decorator mainhand; decorator offhand; both hands; rapid clicks and two players.
- Exact preservation of count and all components for named, damaged, quality, owner/origin, and legacy multi-count stacks.
- Full player inventory still ejects into the world. Permission denial and entity-insertion failure retain the exact stored stack.
- Offhand success leaves both case cells, facing, ownership, and connections intact. Mainhand rotation changes both cell facings together and never dismantles the case.
- Success/failure survives save/reload. Two-client evidence shows one ejection, no ghost display, and no duplicate.

### Exit gate

Inventory/case/world conservation passes for every gesture class, and no supported decorator interaction can rotate a single case cell. Commit and record the SHA.

## 12. M4 — Farming skill equality and harvest outcomes

### Objective

Implement BUG-08 across every economic crop route.

### Work

- Use one authoritative species-requirement resolver for planting and harvesting. Preserve the 67 `CropRegistry` definitions and seven cultivated-flower definitions.
- Remove the grape `NOT_APPLICABLE` exception and direct `GrapeSeedsItem` bypass. Both planting paths and every trellis/arbor/root harvest path require Farming 80.
- Add cultivated-flower harvest gates using poppy 20, snowdrop 40, lily 50, foxglove 65, campion 10, hyacinth 30, and orfluer 95. Preserve the separate mature-poppy knife/Farming-100 rule.
- Resolve live root/species, rights, maturity, required tool, and server skill readiness before the single outcome roll.
- Implement the adopted success formula and lifecycle-aware failure contract at a shared transaction boundary.
- Cover standard soil interaction, trellises, corn/banana/grape parts, fruit scissors, two-handed axe fruit/tree paths, Adventure forwarding, and cultivated flowers. Environmental/support destruction is not a player harvest.
- Emit yield, seed/straw, successful-harvest quest signals, and success rewards only after a successful commit. Eligible failure runs the existing practice opportunity but no success signal.
- Preserve full-inventory drop fallback without rerolls. Two players and repeated actions cannot both harvest the same root.

### Required automated evidence

- All 67 crops and seven flowers resolve one matching planting/harvest threshold.
- Below/exactly/above threshold; unavailable/loading skill; invalid tool; immature crop; denied rights; stale root; deterministic RNG boundaries.
- Exact 75% threshold probability contract, +1% per point, 95% cap, and one roll per attempt.
- Annual removal, perennial/trellis regrowth, fruit picking, axe tree removal, fertilizer-use and durability costs, skill-practice attempt, and no byproducts on failure.
- Creative/admin deterministic bypass with no durability, fertilizer-use, or skill-reward changes.
- Multiplayer, multiblock parts, reload, full inventory, fake-player/automation policy, and future Rowan event placement.

### Exit gate

No player-accessible custom harvest route bypasses the gate/outcome transaction. Planting and harvesting requirements agree for grapes, flowers, custom crops, and native-compatible crops inside custom plots. Commit and record the SHA.

## 13. M5 — Planting feedback and watering-can state

### Objective

Implement BUG-02 and the functional portion of BUG-14.

### Work

- Send one localized actionbar message only after a successful server planting commit: `You skillfully planted the <crop name> seed.` Resolve the canonical planted species and use translatable names.
- Add a small aimed-at-plot HUD indicator based on synchronized authoritative occupancy. It should identify the crop and a plain state such as germinating, growing, or ready. Resolve tall/trellis parts to the root.
- Never display success for refusal, failed transaction, stale client state, duplicate input, or overwritten occupancy. Clear/update the HUD after harvest, removal, chunk load, reconnect, and unknown/loading state.
- Add charge-derived watering-can item state selection: 12 is full; 0 through 11 use the existing base/partial art unless a supplied partial asset exists. Missing legacy charge data remains full at 12.
- Integrate the user's supplied full texture/model without altering it. If it is not present, complete and test the state-selection code and mark only the final visual gate `PENDING_USER_ASSET`; do not fabricate or polish artwork and do not commit a broken resource reference.

### Required automated and client evidence

- One successful planting produces one seed consumption, one gain attempt, one sound, and one message. Duplicate/two-hand/two-player attempts do not duplicate any effect.
- HUD accuracy for empty, planted invisible phase, growing, mature, removed, tall/trellis, reconnect, and chunk reload.
- Watering-can states at 0, 1, 11, and 12; clamping; preservation of unrelated custom data; legacy missing data; fill and dispense updates.
- Inventory, mainhand, offhand, and dropped item display agree after movement, pickup, reconnect, and resource reload.

### Exit gate

Feedback reflects committed server state. Charge-state logic is complete. Final full-can visual acceptance is recorded when the user-supplied asset exists. Commit and record the SHA.

## 14. M6 — Suppress random landscape pumpkins and melons

### Objective

Implement BUG-15 with a narrow world-generation policy.

### Work

- Use the project's existing NeoForge biome-modifier/policy validation pattern to remove exactly `minecraft:patch_pumpkin`, `minecraft:patch_melon`, and `minecraft:patch_melon_sparse` from affected biome generation.
- Preserve village, mansion, and outpost templates/features; player-planted native stems; bonemeal stem behavior; custom pumpkin/watermelon cultivation; native-compatible seeds in custom soil; items and recipes.
- Do not delete existing blocks, scan old chunks, globally cancel stem random ticks, or change `randomTickSpeed`.

### Required evidence

- Runtime registry/biome settings after modifier application omit the three landscape features while unrelated vegetation remains.
- Known-seed new-chunk checks in affected biomes and control biomes show the intended absence.
- Existing native fruit/stems survive load/restart and intentional native/custom cultivation still produces fruit.
- Configuration/datapack reload behavior follows the established policy contract and cannot silently restore the suppressed features without an explicit override.

### Exit gate

Random landscape patches are absent from new generation, intentional cultivation and existing worlds are preserved, and structure rewriting was not introduced. Commit and record the SHA.

## 15. M7 — Complete produce-trader buyback

### Objective

Implement BUG-18 across the NeoForge classification layer and Rails authoritative catalog for the Jhelom produce trader on the Britannia shard.

### Supported produce contract

Keep existing mapped produce and add the missing supported fruit/vegetable outputs identified by discovery:

`yellow_onion`, `green_onion`, `watermelon`, `beans`, `vanilla_melon`, `pineapple`, `strawberry`, `blueberry`, `raspberry`, `cranberry`, `blackberry`, `huckleberry`, `mulberry`, `elderberry`, `cherries`, `snow_peas`, `peas`, `turnips`, `lemon`, `lime`, `orange`, `olive`, `plum`, `bell_peppers`, `cucumbers`, `honeydew`, `cantaloupe`, `broccoli`, `cauliflower`, `rhubarb`, `celery`, `radish`, `parsnip`, `yam`, `rutabaga`, and `grapes`.

Use canonical harvested item IDs, even when a definition ID, item path, or Rails commodity name differs. Preserve exclusions for grains, seeds, flowers, reagents, textiles, tobacco, and processed food. Do not turn the produce trader into a universal food buyer.

### Work in the NeoForge repository

- Generate or maintain one explicit commodity mapping manifest for supported produce, including canonical category, subcategory, commodity key/name, and exact item ID.
- Add mappings for every approved output and keep known carrot/apple controls.
- Ensure ordinary harvest quality/provenance components do not prevent category classification. Preserve meaningful components through reservation and settlement.
- Add completeness tests comparing the approved produce set with `CropRegistry` harvest identities so future crops cannot silently miss their intended policy.

### Work in the Rails repository

- Create the isolated Rails worktree and record its clean base before changes.
- Verify the `produce_trader` policy, Jhelom WorldNpc assignment/spawn point, Britannia shard override, and effective accepted categories using safe read-only evidence when available.
- Add or backfill canonical commodity rows through the existing idempotent economy rollout mechanism. Never reset existing production prices as part of this project.
- Derive base prices from established commodity pricing conventions and the nearest comparable crop in the same farming/lifecycle tier. Use conservative existing comparables when several values are defensible and document the full mapping/price table in the handoff. Do not create a new global pricing formula.
- Preserve `npc_buy_enabled`, stock caps, treasury checks, denomination minimums, dynamic pricing, shard/type policy, quote/sale parity, and durable receipt/idempotency behavior.
- Ensure catalog cache/revision invalidation or refresh follows the established shared-cache mechanism so newly supported commodities appear without stale policy.

### Required evidence

- Completeness test for every approved produce output and negative tests for excluded categories.
- Broccoli, orange, carrot, and apple controls from real harvested/default/admin-style stacks classify identically where policy intends.
- Local Rails catalog/valuation tests cover valid Jhelom/Britannia produce trader; wrong vendor type, shard, city, NPC, disabled buy flag, missing row, stock cap, insufficient treasury, below-minimum value, and stale revision.
- Local mod-to-Rails integration covers quote, reserved exact stack, sale, currency, city inventory/treasury, failure refund, retry, replay, disconnect, and receipt accounting exactly once.
- If authorized read-only production evidence is available, record the effective Jhelom/Britannia policy and current rows before any future deployment. No production sale or write is required.

### Exit gate

All approved produce appears in the correct local produce-trader catalog with authoritative valuations; broccoli and orange are no longer filtered before Rails; both repositories pass focused economy tests; separate commits and SHAs are recorded. Any live-data/deployment acceptance remains explicitly pending. Do not deploy or seed production.

## 16. M8 — Fence connection, collision, and dirt-path behavior

### Objective

Implement BUG-11, BUG-12, and BUG-13 as one consistent fence-state contract.

### Work

- Replace history-dependent facing inheritance with deterministic final-neighborhood resolution. Preserve intentional isolated orientation while ensuring identical neighborhoods converge to identical connected states.
- Reuse the existing end, mirrored end, straight, corner, T, and cross models. Do not create decorative geometry.
- Separate outline and collision shapes. Match vanilla fence collision height of 1.5 blocks across every occupied arm/post while retaining appropriate selection bounds.
- Add the narrow path-survival integration needed so placing this custom fence on an existing `minecraft:dirt_path` does not schedule conversion to dirt. Do not change vanilla oak-fence behavior or vanilla-shovel gesture behavior globally.
- Reconcile connections and collision after neighbor changes, removal/readdition, chunk load, and restart without update loops.

### Required automated and manual evidence

- All 16 NESW neighborhoods across relevant initial facings, rotations, mirrors, placement orders, and removal/readdition converge.
- Direct L and L→T→L are visually/state equivalent in all rotations, including the reproduced south-arm case.
- Straight/end/corner/T/cross collision is 1.5 blocks high with no arm gaps or unintended full-cube obstruction.
- Survival/Adventure normal walk/run/jump tests beside a vanilla oak-fence control.
- Create a dirt path with a vanilla shovel, place the custom fence, and verify the path persists across updates, chunk load, and restart. Vanilla control behavior remains unchanged.

### Exit gate

Final fence state depends only on the final neighborhood and intentional isolated facing; ordinary players cannot jump it on level ground; existing dirt paths persist. Commit and record the SHA.

## 17. M9 — Creative decoration placement

### Objective

Implement BUG-10 consistently across custom gameplay substrate restrictions while preserving technical structure validity.

### Work

- Centralize the Creative placement policy where practical: Creative players bypass gameplay substrate/support restrictions for decorative blocks.
- Preserve world border/height, loaded-space, replaceability, occupied block entity, collision, complete multiblock footprint, and structural-part integrity checks.
- Ensure Creative-placed decorations remain after neighbor updates and reload. For blocks that re-run substrate survival without a player, add the smallest persisted origin/state required; use no persistent flag where the block never rechecks substrate.
- Cover the scarecrow and the bounded discovery audit: shared multiblock decorations, fern, hedge, pool of blood, stalactite, wine bottles, and triple metal door. Apply per-family behavior where physical attachment is an essential structural relationship.
- Preserve Survival/Adventure substrate rules, community access, house protection, and no-consumption Creative semantics.

### Required evidence

- Scarecrow placement on grass, stone, path, slabs/stairs, farm soil, and thin surfaces with valid space; complete four-cell structure and reload survival.
- Each audited family receives Creative, Survival, Adventure, support-removal, neighbor, chunk-load, and restart checks appropriate to its structure.
- Invalid footprints, occupied cells, world limits, malformed parts, and protected actions fail without partial placement or item loss.

### Exit gate

Creative placement is free of gameplay substrate restrictions and structurally stable, while other modes and technical constraints retain their intended rules. Commit and record the SHA.

## 18. M10 — Moongate shader compatibility

### Objective

Implement BUG-01 against the actual placed block rendering path.

### Work

- Build a clean current mod JAR and record its SHA-256. Confirm the reported block is `britannia_mod:moongate_block` before editing.
- Reproduce the placed/inventory/mainhand comparison with no Iris/Sodium, Iris/Sodium with shaders disabled, and Iris 1.8.0 + Sodium 0.6.0 + Photon v1.1 using identical scene/settings.
- Capture logs/screenshots and distinguish a missing baked model from render-pipeline/material/depth behavior.
- Apply the smallest evidence-supported renderer/model-registration change. A block/chunk `RenderType.translucent()` submitted from a BER is a leading hypothesis; use the correct entity/BER render type only if the controlled evidence confirms that boundary.
- Preserve animation, lighting intent, bounds/culling, resource reload, teleport dispatch, collision, mounts, and other moongate variants.
- Do not redesign portal artwork.

### Required evidence

- Placed, inventory, mainhand, front/back, distance, animation, chunk edge, and resource-reload comparisons in all three configurations.
- Teleport behavior for player and mount remains functional independently of rendering.
- Focused renderer/model tests and manual screenshots/logs from the exact tested JAR, shader pack, GPU/driver, and settings.

### Exit gate

The placed moongate is visibly correct with Photon and without shaders, the held/inventory forms remain correct, and teleport behavior is unchanged. If the execution environment cannot observe client visuals, finish the bounded code/tests and mark this milestone pending a named manual shader check rather than claiming acceptance. Commit and record the SHA.

## 19. M11 — Integrated regression, clean artifacts, and handoff

### Objective

Prove the complete project from clean ordinary inputs and produce reviewable local handoff artifacts.

### Work and gates

1. Remove all temporary diagnostic source-set injections from active build configuration. Preserve ignored logs only as evidence.
2. Run focused suites for every milestone.
3. Run the full NeoForge JUnit suite and registered GameTest suite. Confirm tests were discovered and executed, not merely compiled.
4. Run the relevant Rails economy suites and a reasonable full Rails regression appropriate to the repository's established gates.
5. Execute the ordinary-player acceptance journey: prepare/fertilize/water/plant/inspect/grow/harvest; swapped pseudo-crafting/final stack; display-case rotate/eject; produce sale locally; path/fence placement and movement; Creative decoration placement; moongate rendering/teleport.
6. Run the important multiplayer/reload cases: simultaneous planting/harvesting/ejection, inventory conservation, chunk unload/load, server restart, reconnect, and stale client state.
7. Build the mod through the normal release build path. Record filename, size, SHA-256, embedded git head/dirty flag, mod ID/version, and dependencies. The candidate must be clean and must not contain temporary diagnostic namespaces.
8. Inspect both repository diffs/logs/status. Update acceptance evidence and write final handoff with every milestone commit, Rails/mod pairing, test totals, manual evidence, existing-world compatibility, asset dependency, and pending production/deployment work.

### Exit classifications

- `PASS`: all automated and required available manual gates pass.
- `PASS WITH USER-ASSET ACCEPTANCE PENDING`: only the user-supplied watering-can art/final visual remains.
- `PASS WITH EXTERNAL RUNTIME ACCEPTANCE PENDING`: local implementation is complete but a named client/live read-only check could not run.
- `FAIL`: a required functional or regression gate fails. Record exact evidence; do not hide it behind unrelated passing totals.

No exit classification authorizes pushing, merging, deployment, production seeding, or replacing a server JAR.

## 20. Final handoff format

Return:

1. Final verdict and remaining external gates.
2. Mod and Rails worktree paths, branches, starting heads, ending heads, and complete local commit list by milestone.
3. A 19-row issue table showing sixteen implemented outcomes and three asset exclusions.
4. Decisions implemented, including fertilizer loss, harvest formula/costs, produce policy, Jhelom/Britannia context, world-feature boundary, and whole-case rotation.
5. Exact automated test totals and required manual evidence, with failures/skips/limits.
6. Clean candidate JAR identity and proof that diagnostic inputs are absent.
7. Existing-world and rollback notes.
8. Complete changed-file inventory for each repository and final `git status`.
9. Paths to playbook, scratchpad, updated acceptance draft, and final handoff.
10. Explicit confirmation that nothing was pushed, merged, deployed, seeded in production, or changed in Fabric/Atrevion.

## 21. Expected rollback boundaries

- Soil changes should be revertible as one state-lifecycle milestone without changing crop definitions.
- Pseudo-crafting resolver/output changes should be revertible independently of display-case dispatch.
- Harvest gates/outcomes should be revertible without undoing soil persistence.
- Produce buyback uses paired NeoForge/Rails commits; revert or deploy them together so the mod never advertises unsupported catalog rows.
- Worldgen suppression affects new generation only; reverting restores future feature placement and does not recreate removed landscape patches.
- Fence, Creative-placement, and moongate changes remain separate commits for isolated regression diagnosis.

Implementation must preserve unrelated Patch 18 work and stop before any publication or production operation.
