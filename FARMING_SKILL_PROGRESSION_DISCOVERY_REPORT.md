# Farming Skill Progression — Milestone 12 Discovery Report

Status: **Blocked on owner**
Repository: `C:/projects/britannia/mod/Britannia_Mod`
Branch: `Farming`
HEAD: `a2c2e73e0a7500c8c5c2628f51292fae55f67428`

Milestone 12 discovery is complete. The active catalog is **74 species and 74 distinct planting-item registry IDs**: 67 crops and seven persistent flowers. No progression thresholds were proposed, no production or test code was changed, and Milestone 13 has not started.

## 1. Repository and branch baseline

| Fact | Result |
|---|---|
| Repository root | `C:/projects/britannia/mod/Britannia_Mod` |
| Required branch | `Farming`, confirmed directly by the owner |
| HEAD before/after discovery | `a2c2e73e0a7500c8c5c2628f51292fae55f67428` |
| Tracking | `origin/Farming`; 0 behind, 10 ahead |
| Minecraft / NeoForge | Minecraft 1.21.1 / NeoForge 21.1.72 |
| Build runtime | Java 21 toolchain; Gradle 8.9 |
| Working tree before | Dirty with pre-existing, uncommitted Corrective Milestone 11 flower model/renderer/test/document changes, plus untracked supplied briefs and `.claude/settings.local.json` |
| Discovery changes | Only the two Milestone 12 Markdown deliverables |
| Commit/push | Not performed; not authorized |

The existing dirty state was preserved. No pre-existing file was reverted, staged, committed, renamed, or otherwise adopted as part of this work.

### Baseline validation

The authoritative baseline command, run with Java 21 and the existing user Gradle cache, passed:

```text
gradle.bat compileJava processResources test --console=plain --no-configuration-cache --no-daemon
BUILD SUCCESSFUL in 31s
30 actionable tasks: 30 up-to-date
```

An earlier restricted-sandbox/offline attempt failed during settings evaluation because the isolated cache did not contain `org.gradle.toolchains.foojay-resolver-convention:0.8.0`. That was an environment/cache failure, not a source or test failure. The approved user-cache run resolved the distinction and passed.

## 2. Guidance and project documents reviewed

The following repository documents were reviewed and applied in authority order:

- `UltimaCraft_Farming_Skill_Progression_Codex_Milestones_12_17.md` — controlling Milestone 12 scope, deliverables, hard stop, acceptance criteria, and closeout format.
- `UltimaCraft_Persistent_Flower_System_Design.docx` — authoritative flower species, environmental profiles, persistence, protection, regrowth, and Poppy special-stage design.
- `UltimaCraft_Flower_System_Codex_Milestone_Playbook.md` — historical implementation boundaries and approval discipline.
- `UltimaCraft_Flower_System_Corrective_Milestone_11_Single_Model_Two_Textures.md` — current canonical one-model/two-texture correction.
- `FLOWER_SYSTEM_DISCOVERY_REPORT.md`, `FLOWER_SYSTEM_DECISIONS.MD`, `FLOWER_SYSTEM_IMPLEMENTATION_STATUS.md`, `FLOWER_ASSET_PLACEHOLDER_MANIFEST.md`, and `FLOWER_SYSTEM_TEST_MATRIX.md` — implemented behavior, approved decisions, validation evidence, assets, and deferred work.
- `README.md` — contains only the project title and no additional guidance.

No `AGENTS.md` or `CLAUDE.md` exists in the repository. `.claude/settings.local.json` is tool permission/configuration state, not project guidance. The decision log is present as uppercase-extension `FLOWER_SYSTEM_DECISIONS.MD`; no separate lowercase duplicate was expected or used.

The Word document was structurally read in full: 224 paragraphs, 43 tables, 209 table rows, and one section. Visual render verification was attempted with the document workflow but could not run because LibreOffice/`soffice` is not installed. This limitation affects page-layout verification only; all text and table content was accessible.

## 3. Content registration map

### Authoritative registries

| Layer | Authority | Count / role |
|---|---|---|
| Crop definitions | `CropRegistry` | 67 active species; seed mapping, current growth/environment/yield/lifecycle/tool contracts |
| Persistent flowers | `FlowerRegistry` | 7 active species; one-to-one seed and harvest mappings, current environmental profiles and palettes |
| Orchard structures | `FruitTreeRegistry` | 9 structure overlays for already-counted crop species |
| Items | `ItemRegistry` | 60 `CropSeedItem`s, 1 `GrapeSeedsItem`, 7 ordinary flower seed items; six additional mappings use vanilla items |
| Blocks/entities | `FarmingBlock` + `FarmingBlockEntity` | Shared crop substrate and persisted crop state |
| Trees | `OrangeTreeRootBlock`/`OrangeTreeRootBlockEntity` and fruit blocks | Generic orchard implementation despite historical “Orange” class names |
| Tall crops | `TallCropSupport` | Corn and banana multi-block growth after planting |
| Flowers | `FlowerBlock`, `FlowerBlockEntity`, planting/growth/interaction services | Generic persistent flower implementation |
| Grapes | `GrapeSeedsItem`, `GrapesItem`, `GrapeVineBlock`, variety manager | Current FarmingBlock species plus an active legacy direct-placement fallback |

Registration evidence is fully reconciled in `FARMING_CONTENT_MASTER_CATALOG.md`. Every applicable species appears once; aliases and structural overlays do not inflate the total.

### Totals by category

| Category | Count | Species |
|---|---:|---|
| Grains and pulses | 8 | Corn, Wheat, Rye, Barley, Oats, Mustard, Beans, Rice |
| Roots and alliums | 11 | Carrot, Potato, Vanilla Potato, Yellow Onion, Green Onion, Garlic, Turnips, Radish, Parsnip, Yam, Rutabaga |
| Annual gourds and melons | 5 | Squash, Pumpkin, Watermelon, Vanilla Pumpkin, Vanilla Melon |
| Leaf, stalk, and brassica crops | 8 | Cabbage, Lettuce, Snow Peas, Peas, Broccoli, Cauliflower, Rhubarb, Celery |
| Annual fruit crop | 1 | Pineapple |
| Trellis and vine perennials | 7 | Tomato, Hops, Bell Peppers, Cucumbers, Honeydew, Cantaloupe, Grapes |
| Other perennial | 1 | Banana |
| Orchard trees | 9 | Cherries, Apple, Pear, Peach, Lemon, Lime, Orange, Olive, Plum |
| Berries | 8 | Strawberry, Blueberry, Raspberry, Cranberry, Blackberry, Huckleberry, Mulberry, Elderberry |
| Fibres | 3 | Cotton, Flax, Hemp |
| Medicinal or magical | 3 | Ginseng, Mandrake, Nightshade |
| Mushrooms | 2 | Brown Mushroom, Red Mushroom |
| Special leaf crop | 1 | Tobacco |
| Persistent flowers | 7 | Poppy, Snowdrop, Lily, Foxglove, Campion, Hyacinth, Orfluer |
| **Total** | **74** | **67 crops + 7 flowers** |

The category taxonomy is an audit aid inferred from registered mechanics/names because the source has no general category field. It is not a progression judgment.

### Complete planting-item count

- 60 distinct custom `CropSeedItem` registry IDs.
- 1 `GrapeSeedsItem` registry ID; stack custom data distinguishes varieties but all map to `grapes`.
- 6 vanilla compatibility item IDs: Potato, Wheat Seeds, Brown Mushroom, Red Mushroom, Pumpkin Seeds, and Melon Seeds.
- 7 ordinary flower seed item IDs.
- **Total: 74 item IDs mapping to 74 species. No registry ID is shared between species.**

No active planting item class represents bulbs, tubers, saplings, cuttings, starts, spores, rhizomes, pits, kernels, grafts, slips, or transplants. Potato/Yam use repository seed items; mushroom compatibility uses the whole mushroom item. Fruit trees are planted from seed items rather than saplings.

## 4. Special and non-obvious paths

1. **Grape varieties:** one item ID carries Rails/bootstrap-provided variety custom data. Creative entries additionally receive a persistent `CUSTOM_NAME`, which leaks identity independently of future viewer skill.
2. **Grape legacy placement:** `GrapeSeedsItem` extends `ItemNameBlockItem` and falls back to `super.useOn`; `GrapeVineBlock.mayPlaceOn` accepts vanilla farmland and `FarmingBlock`. This can bypass the central crop transaction and may not retain variety consistently.
3. **Vanilla compatibility:** all six vanilla planting materials enter the custom FarmingBlock mapping, but their ordinary vanilla behaviors outside it remain available. A FarmingBlock-only gate would therefore not be a universal cultivation gate.
4. **Trellises:** six definitions (`tomato`, `bell_peppers`, `hops`, `cucumbers`, `honeydew`, `cantaloupe`) can enter through `TrellisBlock`, which delegates to `FarmingBlock.tryPlantSeed` with a trellis-only requirement.
5. **Orchard trees:** nine seed definitions use the shared crop transaction, then create a root/structure state and later multi-block tree structure. They are not separate sapling registrations.
6. **Tall crops:** corn and banana use the ordinary shared planting transaction; `TallCropSupport` adds upper blocks only during later growth.
7. **Flowers:** all seven are detected before grapes/ordinary crops in `FarmingBlock.useItemOn` and delegate to a separate atomic `FlowerPlantingService` transaction.
8. **Aliases:** `carrots`, `sweet_potato`, `mandrake_root`, `strawberries`, and `blueberries` are lookup/save aliases only.
9. **Automation/NPC/world generation:** no custom dispenser behavior, NPC planting, GUI planting, crop world-generation placement, or crop planting command was found. Fake players can reach player-interaction paths. Commands or other mods can still set blocks/state directly, outside item planting contracts.

## 5. Farming-skill architecture

### Server authority and lifecycle

- Canonical key: `FarmingSkill.SKILL_ID = "farming"`.
- Values are floats. Missing player/skill entries read as `0.0`.
- The established operational range is 0–100: fallback skill definitions use maximum 100 and `FarmingSkill` stops awarding at 100. Administrative/API storage is not intrinsically clamped by every setter, so values outside the range are technically possible.
- `SkillManager` maintains the live server cache keyed by player UUID. Login asynchronously loads skill configuration and player values from the Rails API, then sends a `SkillSyncPayload`; logout removes the server cache entry.
- If configuration loading fails, the fallback definition is maximum 100/difficulty 1. If player-value loading fails, the session starts with an empty map (effective zero) while gains can still update locally/post asynchronously.
- `SkillSyncPayload` (`britannia:skill_sync`) transmits the complete map to the owning client. `ClientSkillTable.overwrite` replaces the client cache; its `get` default is zero and `clear` is intended for disconnect.

### Existing farming use

The skill currently modifies crop/tree growth/quality and is awarded after successful planting, harvest, tending, and tool preparation. Flowers award tend/harvest actions but deliberately do not award on planting. Poppy’s existing stage-7 transition separately requires server-side Farming at least 100 and a tagged skinning knife; that is not a planting requirement and remains unchanged.

No existing per-species cultivation requirement table or general Farming `SkillRequirement` is wired into planting. The similarly named crafting requirement record is used by blacksmith crafting and is not an established farming gate.

### Synchronization risks

- Server checks must use `SkillManager`, never the client cache.
- Client names may temporarily be generic during login/resync, which is safe but can be visually stale until the packet arrives.
- Rails failures can make a known player appear skill zero for a session. A future failure policy needs to be explicit rather than silently allowing cultivation.
- Two players viewing the same shared stack require local presentation, not mutation of the stack’s synchronized/persisted components.

## 6. Player-specific naming and identification options

Current item-name methods do not accept a viewing player. `Item#getName(ItemStack)` and the current hover-text signature are stack/context oriented; `GrapeSeedsItem` already derives variety text from stack data, not viewer identity. The owning client does have synchronized local skill through `ClientSkillTable`.

### Option A — viewer-local presentation resolver plus client surface hooks (recommended)

Create a common policy/data resolver and a client-only bridge that reads `ClientSkillTable`. Hook the relevant vanilla presentation surfaces (tooltip/title line, hotbar/container rendering, Creative/search indexing where feasible, narration, and recipient-specific server messages) and return either the generic material name or translated real name without changing the `ItemStack`.

- **Two-player correctness:** each client renders the same stack using its own synchronized skill.
- **Authority:** presentation is advisory; planting remains server-authoritative.
- **Stale risk:** generic until sync is safer than premature disclosure; refresh/re-render follows cache update.
- **Localization:** both generic and species names remain translation components.
- **Compatibility:** central resolver is reusable, but UI hooks/mixins require careful NeoForge/version testing.
- **Maintainability/performance:** one lookup per rendered name/tooltip is cheap; broad surface coverage is the main maintenance cost.

### Option B — dynamic planting-item subclasses with a client-safe skill bridge

Override `getName` for custom seed/flower material classes and consult a distribution-safe bridge to the local skill table on the physical client; use recipient-aware server components for messages.

- **Two-player correctness:** normally correct because each client calls locally.
- **Coverage:** good for inventory/hotbar/narration surfaces that call `getName`, but incomplete for cached Creative/search keys, external recipe viewers, vanilla item classes, and flower seeds unless those registrations change class.
- **Safety:** common item classes must not directly load client-only classes on a dedicated server.
- **Maintainability:** simpler interception surface, but class replacement/migration and vanilla compatibility handling broaden implementation.

### Option C — per-viewer packet/slot rewriting (not recommended)

Send wrapper or rewritten stacks per viewer. This can cover remote containers but risks breaking stack equality, recipe matching, components, caches, and other mods. It creates much more networking and compatibility complexity than a presentation-only rule.

### Rejected identity mechanism

Mutating `CUSTOM_NAME` or another persisted component based on a viewer is not viable: the result is shared, saved, transferable, and visible to every player. Current Creative grape stacks already set `CUSTOM_NAME`; that pre-existing leak needs an explicit migration/policy decision in a later milestone.

### Unavoidable or high-risk disclosures

Registry IDs in advanced/debug views, commands such as `/give`, data/component inspection, recipe/data pack JSON, logs, third-party recipe viewers, chat links, and other-mod UIs may disclose identity even if ordinary vanilla inventory presentation is generic. The feature should define supported surfaces rather than promise secrecy against data inspection.

## 7. Planting call chains and earliest safe gate points

### Ordinary custom and compatibility crops

```text
CropSeedItem.useOn OR FarmingBlock.useItemOn OR TrellisBlock.useItemOn
  -> CropRegistry.byId/bySeed
  -> FarmingBlock.tryPlantSeed
  -> validate target/support/occupancy
  -> mutate FarmingBlockEntity and block state
  -> create tree root when applicable
  -> sound, consume item, award Farming
```

The earliest shared server-authoritative gate is inside `FarmingBlock.tryPlantSeed`, after item-to-definition resolution and target/support validation but before `farmBe.plant`, `setBlock`, tree-root creation, sound, consumption, statistics, or skill award. A client precheck may improve feedback but cannot authorize.

### Flowers

```text
FarmingBlock.useItemOn
  -> FlowerPlantingService.tryPlant/execute
  -> resolve and validate definition/target/occupancy
  -> capture soil and planter metadata
  -> select colour once on server
  -> replace block, initialize/sync state
  -> consume and sound (rollback on failure)
```

The gate belongs in `FlowerPlantingService.execute` after definition/target validation and before the soil snapshot, colour selection, state construction, replacement, consumption, or sound. This preserves the existing exactly-once colour and rollback contracts.

### Grapes

There are two duplicated FarmingBlock handlers and a direct-placement fallback. A species check is required before mutation in both FarmingBlock and `GrapeSeedsItem.useOn`, or the flow should later be centralized. The fallback to `super.useOn` must be gated or deliberately prohibited; otherwise it is a bypass. Variety does not change the species mapping unless the owner explicitly chooses variety-specific progression later.

### Vanilla routes

The six compatibility items can plant outside the custom FarmingBlock through vanilla behavior. No shared custom gate currently intercepts all of those routes. If the policy is “all planting anywhere,” later implementation will need targeted placement events/hooks/mixins for vanilla crop, stem, potato, and mushroom routes, with exact side-effect tests.

### Automation and non-player callers

`tryPlantSeed` accepts a nullable player and currently consumes the item for a null caller. No custom dispenser planting behavior was found, so normal dispensers generally drop the item rather than plant. Fake players may be `ServerPlayer`s but normally have no loaded Farming value. A future gate must define whether skill comes from the fake player, an attributed owner, an automation upgrade, or an explicit denial. Direct block/state mutation by commands/worldgen is not an item planting transaction and should remain a separate policy domain.

## 8. Localization, tooltip, Creative, and accessibility architecture

- Custom item display strings live in `assets/britannia_mod/lang/en_us.json`; vanilla compatibility names come from Minecraft translations.
- Most custom seeds rely on ordinary translation keys. `GrapeSeedsItem#getName` returns a literal variety-derived string and its tooltip adds literal `Variety: …`; Creative grape variants also use literal persistent custom names.
- `CreativeTabRegistry.FARMING_SEEDS` contains the 60 custom crop seeds and seven flower seeds. Grape variety stacks are added separately. Only Wheat Seeds/Wheat are explicitly added for vanilla compatibility; Potato, mushrooms, Pumpkin Seeds, and Melon Seeds are not explicitly added by the mod tab.
- Creative search, recipe/book/viewer caches, narrator text, dropped-item labels, container slots, chat components, hover events, and pickup messages can each obtain names through different paths. Ordinary `getName` coverage alone must not be assumed complete.
- No JEI, REI, or EMI integration/dependency was found in the build or source. Future compatibility should be best-effort and tested only for installed integrations.

Recommended architecture is translated generic components by material category, one central identification resolver, viewer-local client rendering, and server-recipient-aware failure/messages. Literal strings should not be introduced for the feature.

## 9. Creative/admin and automation behavior

Current crop/flower planting consumes no item from players with `instabuild`; ordinary successful crop planting still follows server actions and awards skill. Flower protection defines administrator as Creative or server operator permission level 2, but Poppy mastery itself has no skill bypass. No general cultivation bypass exists because no cultivation gate exists.

For later implementation, bypass decisions must separate:

- Creative inventory visibility from Creative planting authorization;
- Creative `instabuild` from operator permission;
- operator debugging from normal progression;
- fake players/automation from human players;
- direct admin `/setblock`/world-edit mutation from item planting.

Repository consistency favors the existing flower administrator definition (Creative or op level 2) if the owner wants a bypass, but this requires explicit approval.

## 10. Compatibility integrations

- **Vanilla crops/items:** six active mappings, with native behavior remaining outside FarmingBlock.
- **Existing saves:** aliases preserve five historical crop IDs; `FarmingBlockEntity` also contains legacy load logic. Requirements should be checked only on new planting, not retrospectively written into existing planted state unless separately approved.
- **Rails API:** authoritative persistence/config source for player skills and grape variety bootstrap; asynchronous failure behavior is material to gating and names.
- **Community farming plots:** converge on the same FarmingBlock/flower transactions; flower state records/restores community substrate.
- **Other mods:** direct state mutation cannot be universally intercepted. Item names/recipe viewers may bypass ordinary UI presentation.
- **Dedicated server:** client presentation code must remain distribution-isolated; existing flower rendering already follows a client-only registration boundary.

No custom dispenser, merchant, NPC, recipe-viewer, worldgen, or crop-acquisition integration was found for this feature.

## 11. Testing architecture and gaps

Current farming tests are eleven flower-focused suites covering registry/domain contracts, lifecycle/rollback, growth, interactions/protection, rendering/assets, save compatibility, species content, regression, and multiplayer transaction behavior. `FlowerRegressionTest` includes representative source-order assertions for existing crop paths. There is no complete 67-crop catalog test or planting-skill-gate suite.

Later milestones will need, at minimum:

- a pure catalog/requirement resolver test covering all 74 one-to-one mappings and aliases;
- server transaction tests proving denied planting has no block/entity mutation, colour roll, item consumption, durability, sound, statistic, advancement, or skill award;
- route tests for direct FarmingBlock, `CropSeedItem`, trellis, orchard, tall crop, grape duplicate/fallback, all seven flowers, and all six vanilla compatibility paths under the approved scope;
- two-player client tests where the same stack has different names for different Farming values, including resync and skill loss;
- localization/tooltip/hotbar/container/Creative/search/narration tests and screenshots/manual QA;
- fake-player/automation and Creative/operator policy tests;
- existing-save tests confirming already-planted crops remain stable and aliases continue loading;
- dedicated-server classloading and multiplayer synchronization regression gates.

No tests were added or modified during discovery.

## 12. Likely files affected by Milestones 14–17

This is a forecast, not authorization to edit:

- Farming progression contracts/registry/resolver classes under `farming/` (new) and possibly a decision/status document defined by the controlling milestones.
- `CropRegistry`, `FlowerRegistry`, or a separate data table only after owner-approved requirements exist.
- `FarmingBlock`, `FlowerPlantingService`, `GrapeSeedsItem`, and possibly targeted vanilla placement interception/event code for server gates.
- `ItemRegistry`, `CropSeedItem`, flower seed item type(s), and client presentation hooks/bridge for viewer-local names.
- `ClientSkillTable`, `SkillSyncPayload`, or login/disconnect handling only if synchronization behavior needs strengthening.
- `CreativeTabRegistry` and `en_us.json` for generic names/tooltips and grape custom-name cleanup.
- New/updated tests under `src/test/java/.../farming`, plus any GameTest/client test fixtures supported by the repository.
- Final status, proposal, and test-matrix Markdown documents prescribed by later milestones.

Production changes must not begin until the Milestone 13 proposal and required owner decisions are approved.

## 13. Risks and unknowns

1. A FarmingBlock-only gate leaves vanilla compatibility and grape fallback bypasses.
2. Grape Creative `CUSTOM_NAME` persists identity across players and saves.
3. UI methods lack viewer context; complete presentation requires more than one override.
4. Rails load failure currently collapses to effective zero and can create false denials/generic names.
5. Client sync arrival and disconnect clearing can momentarily show stale/generic state.
6. “Hidden identity” cannot be guaranteed against registry/debug/data/third-party inspection.
7. Fake-player ownership and automation skill attribution do not exist.
8. Survival acquisition, recipes, loot, merchant/economy, and world generation are incomplete or absent for much of the catalog, so future progression value cannot rely on proven availability/economic importance.
9. `peas` has a blank registry note; intended design role is unknown.
10. Category labels are inferred rather than stored and must not silently become balance weights.
11. Current grape planting paths disagree on whether planting awards Farming skill.
12. Broad client hooks/mixins and vanilla placement interception carry NeoForge/version/other-mod compatibility risk.

## 14. Consolidated owner questions

These are all material questions discovered in Milestone 12. Recommended answers are provided for decision, not applied.

### DECISION-001 — Unified identification and cultivation threshold

Should a species use one minimum Farming value for both revealing its planting-material identity and permitting planting?

**Recommendation:** Yes. One threshold prevents “known but unusable” ambiguity and keeps the server/client policy explainable.
**Implementation consequence:** one approved species table feeds both the name resolver and server gate.

### DECISION-002 — Generic unidentified names by material category

What generic names should be used before identification?

**Recommendation:** translated category names: `Unidentified Seeds`, `Unidentified Flower Seeds`, and `Unidentified Planting Material` for produce/mushroom compatibility. Do not expose orchard/trellis/species clues unless intentionally approved.
**Implementation consequence:** add translation keys and a material-category field/resolver; no per-viewer stack mutation.

### DECISION-003 — Requirement visibility before identification

May an unidentified stack disclose its numeric Farming requirement?

**Recommendation:** Yes, show `Requires Farming <value> to identify and plant`; progression remains actionable without revealing the species.
**Implementation consequence:** tooltip policy can expose a requirement while the title stays generic.

### DECISION-004 — Insufficient-skill feedback

What message should a denied planting action show?

**Recommendation:** an action-bar translated message naming the generic material when still unidentified and stating current/required Farming; no sound or other success feedback.
**Implementation consequence:** denial must occur before every mutation/effect and build a recipient-specific server component.

### DECISION-005 — Creative bypass

Should Creative players bypass identification and planting requirements?

**Recommendation:** Yes for planting and ordinary inventory presentation, consistent with administrative testing and existing flower protection origin.
**Implementation consequence:** central policy checks `instabuild`/Creative before the requirement; tests cover no-consumption and full naming.

### DECISION-006 — Operator/admin bypass

Should non-Creative operators bypass requirements, and at what permission level?

**Recommendation:** Yes at permission level 2, matching `FlowerProtectionService`; keep direct system/worldgen mutation separate.
**Implementation consequence:** reuse one administrator predicate or an explicitly shared policy to avoid divergent rules.

### DECISION-007 — Automation and dispensers

How should fake players or future planting automation be authorized?

**Recommendation:** deny unattributed automation; use the attributed owner’s server skill when a trustworthy owner UUID exists. Do not silently treat null/fake players as skill 100.
**Implementation consequence:** planting context needs an optional trusted actor/owner and deterministic denial; current dispensers continue not planting.

### DECISION-008 — Creative search and recipe-viewer identity

Must identity be hidden in Creative search and third-party recipe viewers?

**Recommendation:** support viewer-correct vanilla inventory/Creative/search surfaces; define external recipe viewers and registry/debug inspection as best-effort/unavoidable disclosure unless a concrete installed integration is added.
**Implementation consequence:** document the privacy boundary and avoid an untestable promise of universal secrecy.

### DECISION-009 — Skill loss or respec

If a player falls below a species threshold, should the real name hide again and planting become blocked?

**Recommendation:** Yes; derive both dynamically from current skill, with no permanent discovery flag.
**Implementation consequence:** names re-render after sync and server planting rechecks every attempt.

### DECISION-010 — Existing planted crops after skill loss

Should existing plants continue growing and remain harvestable/tendable after the planter or viewer loses skill?

**Recommendation:** Yes. Gate only new planting; do not strand worlds or retroactively invalidate state.
**Implementation consequence:** no growth/harvest requirement and no save migration for already-planted content.

### DECISION-011 — Shared and variant planting items

How should grape varieties and future shared planting items be handled?

**Recommendation:** requirement is species-level; every grape variety uses the `grapes` requirement. Reject future ambiguous item-to-multiple-species mappings until they provide a deterministic stack-data resolver.
**Implementation consequence:** grape variety data remains presentation/produce metadata and cannot bypass the species gate.

### DECISION-012 — Vanilla compatibility scope

Must the requirement govern the six vanilla materials when planted outside the custom FarmingBlock?

**Recommendation:** Yes, if the feature promise is cultivation eligibility across the implemented roster; otherwise those items are obvious bypasses. Preserve non-plant uses such as eating/crafting.
**Implementation consequence:** targeted server-side interception is required for vanilla crop/stem/potato/mushroom placement, with version-sensitive tests.

### DECISION-013 — Grape persistent custom names

May later implementation remove/migrate the Creative grape `CUSTOM_NAME` component so identification can be viewer-specific?

**Recommendation:** Yes; compute variety text locally after identification and retain only variety ID in stack custom data. Existing custom-named stacks need a bounded compatibility rule.
**Implementation consequence:** data-component cleanup/migration and regression tests are required; otherwise grapes permanently leak identity.

### DECISION-014 — Skill-service outage policy

When Rails player-skill loading fails, should planting fail closed at effective zero, use a last-known value, or fail open?

**Recommendation:** fail closed for new planting but show a distinct “skill data unavailable” message when load state is known; never fail open. A future last-known cache may be considered separately.
**Implementation consequence:** `SkillManager` needs observable load state, not just a numeric zero, before precise outage feedback is possible.

### DECISION-015 — Incomplete acquisition/economy evidence

Should Milestone 13 balance every active registered species even when initial survival acquisition is absent or unclear?

**Recommendation:** Yes, keep all 74 in the progression proposal but mark acquisition/economic confidence separately and do not infer value from missing content.
**Implementation consequence:** no species disappears from coverage; low-confidence entries receive alternatives and explicit owner review.

`DECISION-016` / the controlling brief’s final progression-table approval cannot be asked meaningfully until Milestone 13 produces a proposal. It remains deferred, not assumed.

## 15. Milestone 12 closeout

```text
Milestone: 12 — Repository Archaeology and Complete Farming Content Catalog
Branch and HEAD before work: Farming / a2c2e73e0a7500c8c5c2628f51292fae55f67428
Branch and HEAD after work: Farming / a2c2e73e0a7500c8c5c2628f51292fae55f67428
Working tree before: Dirty pre-existing Corrective Milestone 11 work; supplied briefs and .claude untracked
Working tree after: Same pre-existing state plus the two Milestone 12 Markdown deliverables

Documents reviewed: Controlling Milestones 12–17 brief; authoritative flower DOCX; flower playbook; corrective M11 brief; discovery, decisions, implementation status, placeholder manifest, and test matrix; README
Files inspected: Farming/crop/flower/skill/item/block/block-entity/network/client/registry/command/test sources; Gradle config; localization; data resources; Git history/status
Files added: FARMING_SKILL_PROGRESSION_DISCOVERY_REPORT.md; FARMING_CONTENT_MASTER_CATALOG.md
Files modified: None
Files removed: None

Confirmed findings: 74 unique active species; 74 distinct planting-item IDs; 67 crop definitions; 7 flowers; 6 vanilla compatibility mappings; one variant-bearing grape item; no shared item IDs
Implementation summary: Documentation-only discovery; no production implementation
Catalog coverage: 74/74 species exactly once; 74/74 planting item mappings reconciled
Progression coverage: 0 proposed thresholds; intentionally deferred to Milestone 13 after owner decisions
Tests added or updated: None
Commands run: Git preflight/history/status; repository searches/source reads; DOCX structural extraction/render attempt; Java/Gradle version checks; compileJava processResources test
Results: Java 21 Gradle baseline passed; restricted offline cache attempt failed before project configuration due missing Foojay plugin; DOCX structural read passed; DOCX visual render unavailable without soffice
Manual QA: Catalog row/count/item reconciliation; call-chain inspection; no gameplay manual QA claimed
Known limitations: No visual DOCX page render; external recipe/debug identity cannot be universally hidden; acquisition/economy incomplete; peas notes blank; Rails outage behavior ambiguous
Risks: Vanilla/grape bypasses; per-viewer UI coverage; persistent grape custom names; fake-player attribution; asynchronous skill state; version-sensitive hooks
Deferred work: All progression values, owner approval, schema, production gating/naming, test implementation, QA, commit/push
Owner decisions required: DECISION-001 through DECISION-015 above; final progression-table approval deferred until a proposal exists
Suggested commit message: docs(farming): inventory plantable content and skill integration points
```

## Hard stop

**Catalog total:** 74 species / 74 planting-item registry IDs.
**Unknown or ambiguous entries:** Peas’ intended role; grape variety/persistent-name behavior; six vanilla external planting routes; fake-player/automation attribution; Rails outage state; incomplete survival acquisition/economy; inferred category taxonomy.
**Changes made:** the two required Markdown files only. No balance values, production code, tests, commit, push, or implementation changes.
**Status:** **Blocked on owner. Do not proceed automatically to Milestone 13.**
