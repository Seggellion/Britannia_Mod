# UltimaCraft Persistent Flower System
## Codex Multi-Milestone Integration Playbook

**Target branch:** `farming`  
**Primary design authority:** `UltimaCraft_Persistent_Flower_System_Design.docx`  
**Execution model:** Discovery first, owner questions second, implementation only after answers  
**Initial species:** Poppy, Snowdrop, Lily, Foxglove, Campion, Hyacinth, and Orfluer

---

## 1. Purpose

This playbook guides Codex through integrating the Persistent Flower System into the existing UltimaCraft farming implementation. It is deliberately milestone-based so the existing farming architecture is understood before any flower code is introduced.

Codex must work from the existing `farming` branch and integrate with the current crop system rather than building a parallel farming engine. The design document and this playbook should remain in the project folder throughout the work and must be reread at the start of every milestone.

The playbook has three hard gates:

1. **Repository and farming-system discovery must finish before implementation begins.**
2. **Codex must present all material questions after discovery and stop for owner answers.**
3. **Each implementation milestone must pass its own tests and closeout review before the next milestone starts.**

Do not treat this document as permission to work ahead. Execute only the milestone explicitly assigned by the owner.

---

## 2. Authoritative Inputs and Precedence

Codex must use the following order of authority:

1. Direct owner instructions given during the current milestone.
2. `UltimaCraft_Persistent_Flower_System_Design.docx`.
3. This milestone playbook.
4. Existing established farming-system architecture and repository conventions.
5. Reasonable implementation choices documented by Codex.

When the design document and existing code appear incompatible, Codex must not silently choose one. It must document the conflict and ask the owner during the discovery question gate.

The existing code is authoritative for how UltimaCraft currently represents hydration, nutrients, regions, altitude, farming skill, tools, registration, networking, persistence, models, data generation, and tests. The design document is authoritative for flower behaviour.

---

## 3. Non-Negotiable Feature Invariants

Every milestone must preserve these rules.

### 3.1 FarmingBlock conversion

A `FarmingBlock` does **not** become a `FlowerBlock` when soil is tilled, prepared, watered, fertilized, loaded, or inspected.

The only normal conversion event is:

```text
Existing FarmingBlock + valid flower seed use
    -> FlowerBlock initialized at growth stage 1
```

Invalid seed use must leave the `FarmingBlock` and held item unchanged.

### 3.2 One colour roll per planting

The flower colour is selected server-side exactly once during successful seed planting. The selected colour is stored persistently on the flower instance.

The colour must not change during:

- natural growth;
- harvest or cutback;
- reset to growth stage 1;
- Adventure-mode sword damage;
- Poppy stage 7 advancement;
- chunk unload or reload;
- server restart;
- client reconnect;
- resource reload; or
- later changes to soil nutrients.

A new colour may be selected only after the existing flower is genuinely removed and a new seed is planted.

### 3.3 Persistent regrowth

Flowers are persistent plantings. Approved harvesting or cutting returns the same flower instance to growth stage 1 and restarts growth. Species, colour, planting origin, protection state, and applicable farming data remain unchanged.

### 3.4 Admin and Creative protection

Protection is determined when the flower seed is planted.

A flower planted by a Creative-mode player or an authorized flower administrator is admin-protected. Ordinary players must not alter, cut back, harvest, uproot, replace, or destroy it with a hoe, shovel, sword, normal break attempt, or other unapproved mutation path.

Only Creative mode or the project’s authorized admin permission path may permanently modify or destroy an admin-protected flower.

### 3.5 Adventure sword cutback

When an Adventure-mode player attacks an eligible, unprotected flower with an approved sword, the flower is not removed. It resets to growth stage 1 and regrows with the same species and colour.

### 3.6 Poppy stage 7

Poppies grow naturally only through stage 6. Automatic growth must never produce stage 7.

A stage-6 Poppy may be advanced manually to stage 7 only when the interacting player:

- has Farming skill 100 or greater; and
- uses the registered skinning knife or approved skinning-knife tag.

After the Poppy returns to stage 1, this special advancement must be earned again after it regrows to stage 6.

### 3.7 Existing farming simulation reuse

Flowers must use the existing farming system’s hydration, nutrient, regional, altitude, quality, and scheduling mechanisms wherever practical. Do not create a separate flower-only soil or climate engine.

Each flower species supplies its own ideal and tolerated growth attributes to the shared evaluator.

### 3.8 Exactly two textures per in-world flower model

Every unique in-world flower growth model uses exactly two texture files:

```text
<model_name>_base_texture.png
<model_name>_dye_mask.png
```

The `base_texture` contains fixed, untinted artwork. The `dye_mask` contains the grayscale artwork and alpha selection for the pixels that receive the stored flower colour tint.

There is no third grayscale texture, no binary mask plus grayscale texture pair, and no per-colour texture collection. A model may alias its particle texture to the base texture, but it must not introduce a third texture file.

The two files must have identical dimensions, UV layout, transparency padding, and pixel alignment.

### 3.9 Multi-plane flower models

In-world flowers use a multi-plane texture model rather than a full cube. Codex must follow the repository’s existing crop or plant geometry conventions when available. If the repository has no suitable convention, Codex must propose the exact multi-plane and two-pass rendering strategy during discovery and obtain owner approval before implementation.

### 3.10 Server authority

Species, colour, growth stage, planting origin, protection, growth decisions, and interactions are server-authoritative. The client renders synchronized state and must never reroll or infer colour.

---

## 4. Codex Operating Contract

### 4.1 Branch discipline

All work occurs on the existing `farming` branch.

At the beginning of every milestone, Codex must run and report:

```bash
git rev-parse --show-toplevel
git branch --show-current
git status --short --branch
git log -5 --oneline
```

The current branch must be exactly `farming`. If it is not, Codex must stop and report the mismatch. It must not silently create, switch, reset, rebase, merge, or delete branches.

Codex must not:

- create a feature branch unless the owner explicitly changes this playbook;
- merge `farming` into another branch;
- merge another branch into `farming`;
- rebase or rewrite history;
- force push;
- push to a remote; or
- modify unrelated untracked files.

Codex may commit a completed milestone only when the owner’s milestone prompt explicitly authorizes a commit. Otherwise, leave the changes staged or unstaged for review and report the exact state.

### 4.2 Scope discipline

Codex must execute only the assigned milestone. It may make a small prerequisite correction discovered during the milestone only when all of the following are true:

- the correction is necessary for the assigned milestone;
- it does not change unrelated behaviour;
- it is documented in the milestone report; and
- it is covered by validation.

Otherwise, add it to a deferred-work section and do not implement it.

### 4.3 No destructive cleanup

Codex must preserve unrelated working-tree changes. Never use broad cleanup commands such as:

```bash
git reset --hard
git clean -fd
git checkout -- .
```

Do not reformat broad areas of the codebase merely because a formatter is available. Restrict formatting to files touched by the milestone unless the repository’s build tooling necessarily applies a wider change.

### 4.4 Evidence over assumptions

Codex must identify actual classes, registries, tags, data files, build tasks, and tests before proposing integration. It must cite repository-relative paths and relevant symbols in discovery and milestone reports.

Do not invent class names and later force the repository to match them. Names in this playbook such as `FlowerBlock`, `FlowerBlockEntity`, and `FlowerSpeciesDefinition` describe roles. Codex should use repository-consistent names unless an owner-approved architecture decision says otherwise.

### 4.5 Build and test discipline

Codex must first discover the project’s real build and validation commands. It must not assume Gradle task names. Once discovered, each implementation milestone must run the narrowest relevant checks plus the normal compile/resource validation required by the repository.

If a test cannot run because of environment limitations, Codex must state:

- the exact command attempted;
- the full relevant failure;
- why the failure appears environmental rather than functional; and
- what remains unverified.

### 4.6 Milestone closeout report

Every milestone ends with a report containing:

```text
Milestone:
Branch and HEAD before work:
Branch and HEAD after work:
Working tree before:
Working tree after:
Files inspected:
Files added:
Files modified:
Architecture decisions:
Behaviour implemented:
Tests and commands run:
Results:
Known limitations:
Deferred work:
Owner decisions still required:
Suggested commit message:
```

Do not claim completion when acceptance criteria have not been demonstrated.

---

## 5. Recommended Project Documentation

Codex should maintain these repository documents as the work progresses, using the project’s documentation location if one already exists:

```text
UltimaCraft_Persistent_Flower_System_Design.docx
UltimaCraft_Flower_System_Codex_Milestone_Playbook.md
FLOWER_SYSTEM_DISCOVERY_REPORT.md
FLOWER_SYSTEM_DECISIONS.md
FLOWER_SYSTEM_IMPLEMENTATION_STATUS.md
FLOWER_ASSET_PLACEHOLDER_MANIFEST.md
FLOWER_SYSTEM_TEST_MATRIX.md
```

The design document and playbook are owner-provided guidance. Codex-generated reports should be updated rather than duplicated under new names at every milestone.

---

# Milestone 0 - Repository Preflight and Document Intake

## Objective

Confirm the correct repository, branch, working-tree state, guidance documents, build system, and baseline health without changing implementation code.

## Codex instructions

1. Verify the repository root and confirm the active branch is `farming`.
2. Record the current HEAD and working-tree state.
3. Locate and read the complete design document and this playbook.
4. Locate repository guidance files such as `AGENTS.md`, `CLAUDE.md`, `README`, contribution guidance, architecture notes, or package-specific instructions.
5. Identify the Minecraft, NeoForge, Java, Gradle, GeckoLib, and relevant library versions from repository files rather than memory.
6. Identify the standard build, test, data-generation, and run-client/run-server commands.
7. Run the least invasive baseline validation appropriate to the repository. Do not repair pre-existing failures in this milestone.
8. Create or begin `FLOWER_SYSTEM_DISCOVERY_REPORT.md` with the preflight evidence.

## Prohibited work

- No flower classes.
- No registrations.
- No asset files.
- No refactoring.
- No dependency updates.
- No formatting sweeps.

## Acceptance criteria

- Branch is confirmed as `farming`.
- Initial repository status and HEAD are recorded.
- Both guidance documents are found and read.
- Build and validation entry points are identified.
- Baseline result is recorded, including pre-existing failures.
- No implementation code is changed.

## Suggested closeout

Codex should proceed directly into Milestone 1 only when the owner assigned Discovery as a combined Milestone 0-1 task. Otherwise, stop after the preflight report.

---

# Milestone 1 - Farming-System Discovery and Integration Assessment

## Objective

Fully understand the existing farming system and produce an evidence-based flower integration proposal. This milestone is read-only with respect to implementation code.

## Discovery areas

Codex must trace the existing system end to end.

### 1. Soil preparation and FarmingBlock lifecycle

Find and document:

- how natural soil is converted into the prepared farming block;
- the role of `britannia_hoe`;
- prepared-soil expiration or reset behaviour;
- watering and fertilizing interactions;
- blockstate properties and block entity data;
- how FarmingBlock is restored after crop removal or harvest;
- how ordinary crops are planted on FarmingBlock; and
- every code path that replaces or destroys FarmingBlock.

Explicitly identify where flower-seed conversion can be inserted without allowing hoe use alone to create a FlowerBlock.

### 2. Crop definitions and registration

Find and document:

- crop item and seed registration conventions;
- crop species definitions or hard-coded mappings;
- growth-stage configuration;
- annual, perennial, trellis, tall-crop, berry, and fruit-tree abstractions, if present;
- item tags and tool tags;
- language-key conventions;
- creative-tab registration;
- loot-table and recipe conventions; and
- data generation versus handwritten JSON conventions.

### 3. Growth evaluation

Trace the exact path for:

- hydration state and thresholds;
- nutrient state and thresholds;
- climate or region lookup;
- altitude lookup;
- quality calculation;
- stage advancement;
- growth timers and random ticks;
- paused or failed growth;
- harvest reset; and
- save/load persistence.

Document which code can be reused directly, which requires an interface or adapter, and which must not be duplicated.

### 4. Skills and tools

Find and document:

- Farming skill storage and lookup;
- how skill level 100 is represented and compared;
- the skinning knife item or tag;
- sword detection conventions;
- Adventure-mode interaction handling;
- `britannia_hoe` and shovel handling;
- tool durability behaviour; and
- server/client interaction boundaries.

### 5. Permissions and protected world content

Find and document:

- existing admin or permission services;
- Creative-mode checks;
- protected block, city, structure, or spawn-block precedents;
- break-event cancellation;
- explosion, piston, fluid, and indirect block mutation handling;
- operator or command permission conventions; and
- how protected state is synchronized and persisted.

### 6. Rendering and models

Find and document:

- existing crop model geometry;
- whether crops use crossed planes, multiple planes, custom baked models, block-entity renderers, GeckoLib, tint indices, render types, or another mechanism;
- stage-to-model resolution;
- block entity client synchronization;
- texture sizes and naming conventions;
- cutout and transparency conventions;
- item rendering conventions; and
- resource/data generation support.

Codex must evaluate at least two technically viable approaches for rendering the fixed base pass and tinted grayscale `dye_mask` pass on a multi-plane model. The recommendation must account for Z-fighting, mip bleeding, render ordering, performance, resource reloads, multiplayer state, and the exact two-texture-file invariant.

### 7. Testing and diagnostics

Find and document:

- unit-test conventions;
- GameTest or integration-test support;
- test fixtures or test worlds;
- resource validation;
- dedicated-server validation;
- logging conventions; and
- any existing debug commands useful for crop growth.

### 8. Save compatibility

Find and document:

- NBT or codec conventions;
- data version fields and migration patterns;
- block-entity update tags/packets;
- missing-registry fallback behaviour; and
- safeguards against state creation during deserialization.

## Required discovery methods

Use repository search rather than guessing. Appropriate commands may include:

```bash
rg -n "FarmingBlock|CropBlock|hydration|nutrient|altitude|region|quality" .
rg -n "britannia_hoe|skinning|SwordItem|ShovelItem|Adventure|isCreative" src
rg -n "BlockEntity|saveAdditional|loadAdditional|getUpdateTag|getUpdatePacket" src
rg -n "tintindex|BlockColor|ItemColor|BakedModel|BlockEntityRenderer|RenderType" src
find . -iname 'AGENTS.md' -o -iname 'CLAUDE.md' -o -iname '*farming*' -o -iname '*crop*'
```

Adapt paths and search terms to the actual repository.

## Required discovery report

`FLOWER_SYSTEM_DISCOVERY_REPORT.md` must contain:

1. Repository and branch baseline.
2. Farming system component map.
3. Current planting sequence.
4. Current growth-evaluation sequence.
5. Current persistence and synchronization sequence.
6. Existing model and rendering pipeline.
7. Tool, skill, and permission integration points.
8. Recommended flower architecture with repository-consistent class and file names.
9. Files likely to be added or modified, grouped by milestone.
10. Reuse plan showing which farming code remains authoritative.
11. Risks, incompatibilities, and migration concerns.
12. Two-pass multi-plane rendering options and a recommendation.
13. Test strategy.
14. A consolidated owner-question section.

## Mandatory owner question gate

After the report is complete, Codex must ask all material architecture, integration, and behaviour questions in one consolidated message and then stop.

Questions must be based on actual discoveries. Do not ask questions the code or design document already answers. Questions may include, when unresolved:

- the preferred rendering approach when more than one integrates cleanly;
- final registry ID naming where current crop conventions conflict with the design IDs;
- whether every sword or only an approved sword tag performs Adventure cutback;
- whether ordinary shovels uproot player flowers or merely must be blocked from protected flowers;
- the exact admin permission source in addition to Creative mode;
- the expected harvested flower item and seed-drop behaviour;
- whether harvesting and sword cutback share drops or have different outcomes;
- whether planter UUID auditing is required;
- region-tag mappings for each species;
- texture resolution when current crop assets use more than one size;
- whether placeholder assets should be generated or handwritten; and
- any save-compatibility decision exposed by the existing block architecture.

Codex must clearly label each question as **blocking**, **architecture**, **gameplay**, **asset**, or **non-blocking**.

## Hard stop

Do not write implementation code, create registrations, or add placeholders until the owner answers the discovery questions and explicitly authorizes the next milestone.

## Acceptance criteria

- The full existing farming pipeline is mapped with file and symbol evidence.
- The recommended integration reuses the existing crop engine.
- Seed-only FarmingBlock conversion has a specific integration point.
- Rendering options are compared and one is recommended.
- Risks and tests are documented.
- Owner questions are asked.
- Codex stops before implementation.

---

# Owner Decision Gate - Required Before Building

After the owner answers, Codex must:

1. Add the answers to `FLOWER_SYSTEM_DECISIONS.md`.
2. Update any affected recommendations in `FLOWER_SYSTEM_DISCOVERY_REPORT.md`.
3. List each resolved question with the chosen decision and implementation consequence.
4. Identify any remaining blockers.
5. Present the final milestone order and expected file areas.
6. Stop again if any blocking question remains.

Implementation begins only after the owner explicitly says to proceed.

---

# Milestone 2 - Architecture Foundation and Data Contracts

## Objective

Add the minimum compilable flower-domain foundation and data contracts without yet converting FarmingBlock, growing flowers, or rendering final models.

## Required work

Using the owner-approved discovery architecture, implement repository-consistent equivalents of:

- flower planting origin (`PLAYER`, `ADMIN`, with reserved future values only if the project convention supports them);
- reusable flower colour definition;
- species-specific palette entry with weight and rarity label;
- flower species definition;
- flower growth profile that delegates to existing farming values;
- flower colour selector interface;
- Version 1 weighted random selector;
- validation for species IDs, seed mappings, stage ranges, weights, colours, growth ranges, and special rules;
- serialization codecs or loaders that match current repository conventions; and
- shared constants/tags only where required.

### Forward-compatible colour selection

The selector should accept planting context, including the existing soil snapshot or equivalent, even though Version 1 uses base species weights only. This allows future nutrient-biased weighting without changing saved flower identity.

The selector must not be callable from load/deserialization paths.

### Initial data

Add the seven species IDs and shared colour definitions from the design document, but do not yet claim gameplay completeness. Use data-driven files if the current crop system supports data-driven definitions. Otherwise, use the smallest repository-consistent registration layer and document why.

Species IDs:

```text
britannia_mod:poppy
britannia_mod:snowdrop
britannia_mod:lily
britannia_mod:foxglove
britannia_mod:campion
britannia_mod:hyacinth
britannia_mod:orfluer
```

Poppy must define natural maximum stage 6 and absolute maximum stage 7. All other initial species use natural stages 1-7 unless the owner decision log changes this.

## Tests

At minimum, verify:

- all species definitions load;
- all referenced colours exist;
- all palette weights are positive;
- duplicate seed-to-species mappings fail clearly;
- stage bounds are valid;
- Poppy natural and absolute maxima remain distinct;
- invalid data fails with a useful error; and
- a large weighted-selection sample produces only allowed colours and roughly follows configured weights.

## Prohibited work

- No FarmingBlock replacement yet.
- No world growth yet.
- No renderer yet.
- No final art.
- No colour selection on load.

## Acceptance criteria

- The project compiles.
- Definitions and validation follow existing architecture.
- Seven species and their palettes are represented.
- The colour selector is server-oriented and future-compatible.
- Tests demonstrate palette safety and data validation.

## Suggested commit message

```text
feat(flowers): add species and colour data contracts
```

---

# Milestone 3 - Placeholder Items, Seeds, Models, and Textures

## Objective

Register all initial flower and seed content and add complete placeholder assets so every new registry entry and model resolves without missing-texture errors. These are scaffolding assets for later owner replacement, not final artwork.

## Required item placeholders

Follow the existing crop item conventions discovered in Milestone 1. Unless the owner decision log specifies different IDs, provide a harvested flower item and seed item for each species:

```text
poppy
poppy_seeds
snowdrop
snowdrop_seeds
lily
lily_seeds
foxglove
foxglove_seeds
campion
campion_seeds
hyacinth
hyacinth_seeds
orfluer
orfluer_seeds
```

Add all required:

- item registrations;
- language keys;
- creative-tab entries;
- item models;
- placeholder inventory textures or renderer references;
- tags;
- minimal loot/recipe placeholders only when required for valid loading; and
- seed-to-species mappings.

Do not invent acquisition recipes or economy behaviour unless approved. If recipes are not part of the current milestone, the items may remain Creative/admin accessible and be clearly documented as placeholders.

## Required in-world model placeholders

Flowers are multi-plane texture models. Add a resolvable placeholder model for every stage required by every species:

- Poppy stages 1-7, with stage 7 reserved for manual advancement.
- Snowdrop stages 1-7.
- Lily stages 1-7.
- Foxglove stages 1-7.
- Campion stages 1-7.
- Hyacinth stages 1-7.
- Orfluer stages 1-7.

That is 49 stage-model entries unless the approved architecture uses a generated model family. Shared parent geometry is encouraged, but every species/stage lookup must resolve to an intentional placeholder.

The placeholder geometry must:

- use multiple intersecting or oriented planes consistent with existing crop assets;
- render from all expected player viewpoints;
- use cutout transparency;
- avoid cube occlusion behaviour inappropriate for plants;
- follow the approved two-pass strategy; and
- avoid visible Z-fighting between base and dye-mask passes.

## Exact two-texture requirement

Each unique in-world stage model must resolve exactly these two texture files:

```text
assets/britannia_mod/textures/block/flowers/<species>/stage_<n>_base_texture.png
assets/britannia_mod/textures/block/flowers/<species>/stage_<n>_dye_mask.png
```

Suggested model path:

```text
assets/britannia_mod/models/block/flowers/<species>/stage_<n>.json
```

Adapt the paths only when the repository’s asset architecture requires it, and record the final mapping in the placeholder manifest.

The `base_texture` placeholder must contain only fixed untinted plant artwork, such as a simple stem and leaf indicator. The `dye_mask` placeholder must:

- contain neutral grayscale only in RGB channels;
- use alpha to select the tintable bloom region;
- be transparent outside the bloom region;
- preserve simple grayscale highlights and shadows;
- match the base texture’s dimensions and UV layout exactly; and
- contain no final species colour.

No third in-world texture file is permitted for a stage model. A particle reference may alias the base texture.

### Placeholder generation

Prefer the project’s existing data or asset generator. If no suitable PNG generator exists, Codex may add a small deterministic development script under the repository’s tooling convention. It should generate valid pixel-art PNGs without adding a runtime dependency. The script and generated files must be documented.

Do not use inaccessible external image links. Do not leave invalid empty files or deliberately trigger Minecraft’s missing-texture pattern.

## Placeholder manifest

Create `FLOWER_ASSET_PLACEHOLDER_MANIFEST.md` listing every placeholder:

- registry ID;
- item model;
- item texture or renderer source;
- block model path;
- base texture path;
- dye-mask path;
- dimensions;
- stage;
- final-art replacement status; and
- whether the asset is generated or handwritten.

Clearly state that replacing artwork must preserve filenames, dimensions, UV layout, transparency, and the exact two-texture contract unless code and models are intentionally revised together.

## Tests

Validate:

- all item and model JSON parses;
- all referenced textures exist;
- each in-world stage model resolves exactly two texture files;
- base and dye-mask dimensions match;
- mask RGB is grayscale wherever alpha is non-zero;
- pixels outside the intended mask have zero alpha;
- all seven species and required stages appear in the manifest; and
- client resource loading produces no missing model or missing texture errors for this content.

## Acceptance criteria

- All fourteen logical item/seed entries are registered or represented according to approved conventions.
- All 49 stage lookups resolve.
- Every in-world stage model is multi-plane and has exactly one base texture and one dye-mask texture.
- Placeholder assets are visibly placeholders but technically valid.
- A complete replacement manifest exists.

## Suggested commit message

```text
content(flowers): add placeholder items seeds models and masks
```

---

# Milestone 4 - FlowerBlock Lifecycle, Planting, Persistence, and Synchronization

## Objective

Implement the generic in-world flower state and the atomic seed-only conversion from existing FarmingBlock.

## Required work

### Generic flower block

Implement one generic flower block architecture for all species unless discovery proved a different shared architecture is necessary. Do not register one block per species or colour merely to simplify rendering.

The flower state must provide or persist the approved equivalents of:

- species ID;
- colour ID;
- growth stage;
- planting origin;
- admin-protected state;
- existing farming soil/growth data or a reference to the shared farming component;
- growth timing;
- data version;
- optional planter UUID only if approved; and
- any minimal special-stage state required by the architecture.

### Seed-only planting transaction

The planting path must:

1. Verify the target is the existing valid `FarmingBlock`.
2. Resolve the held seed to exactly one flower species.
3. Validate all initialization data before replacing the block.
4. Snapshot and preserve required hydration, nutrients, quality, region/climate context, timers, and other existing farming state.
5. Determine planting origin and protection from the planter at planting time.
6. Roll one allowed colour on the logical server.
7. Replace FarmingBlock with the generic FlowerBlock at stage 1.
8. Initialize persistent data atomically.
9. Consume the seed only after successful initialization and only when normal item consumption applies.
10. Synchronize species, colour, stage, and protection to tracking clients.

A failed lookup, failed validation, or failed initialization must not consume the seed or replace FarmingBlock.

### Protection origin

Creative mode or the approved admin permission produces protected admin origin. Tilling by an admin does not mark future flowers as protected. The planter at the moment of seed use determines the flower’s origin.

### Persistence

Save and load must preserve the colour exactly. Loading must never call the colour selector.

Unknown IDs must follow the approved safe fallback and logging policy from discovery. Do not silently replace an unknown saved colour with a newly randomized colour.

## Tests

Verify:

- hoe preparation alone leaves a FarmingBlock;
- watering and fertilizing alone leave a FarmingBlock;
- valid flower seed use converts FarmingBlock to FlowerBlock stage 1;
- invalid seed use is atomic and consumes nothing;
- soil state survives conversion;
- ordinary planting records player/unprotected origin;
- Creative/admin planting records admin/protected origin;
- admin-prepared soil planted by an ordinary player creates an ordinary unprotected flower;
- colour is from the species palette;
- colour persists through save/load and server restart simulation;
- client synchronization agrees across two observers; and
- deserialization does not invoke colour selection.

## Acceptance criteria

- FarmingBlock conversion happens only through valid flower seed use.
- Flower identity and protection persist.
- Colour is rolled once and saved immediately.
- Multiplayer clients receive identical state.
- No growth or interaction behaviour is claimed beyond what this milestone implements.

## Suggested commit message

```text
feat(flowers): add atomic planting and persistent flower state
```

---

# Milestone 5 - Shared Growth Evaluation and Perennial Regrowth

## Objective

Connect flower species profiles to the existing crop growth engine and implement stage progression and persistent reset behaviour.

## Required work

### Shared evaluator integration

Use the existing farming implementation for:

- hydration scoring;
- nutrient scoring;
- regional or climate scoring;
- altitude scoring;
- quality effects;
- growth timing;
- paused or failed growth; and
- relevant visual or diagnostic feedback.

Add an adapter or shared interface only when necessary. Do not copy the crop formula into a flower-specific class.

### Species profiles

Implement the design document’s starting profiles for:

- Poppy;
- Snowdrop;
- Lily;
- Foxglove;
- Campion;
- Hyacinth;
- Orfluer.

Map conceptual regional profiles to the actual region or climate tags found during discovery. Record all mappings in `FLOWER_SYSTEM_DECISIONS.md` or the status document.

### Stage progression

- Poppy automatic growth stops at stage 6.
- Other initial species grow naturally through stage 7.
- The growth scheduler must respect existing crop timing conventions.
- Stage progression must never change the saved colour.

### Perennial reset

Create one authoritative reset operation, such as an approved equivalent of:

```text
resetToStageOne(reason)
```

It must reset only the growth stage and applicable timer state. It must retain:

- species;
- colour;
- planting origin;
- protection;
- underlying farming state; and
- persistent block identity.

Use explicit reset reasons when helpful for tests and later balancing, such as `HARVEST`, `SWORD_CUTBACK`, or `PRUNING`.

## Tests

Verify:

- each species responds to its own hydration, nutrient, regional, and altitude profile;
- flower growth uses the same scoring semantics as crops;
- unsuitable conditions follow existing crop pause/failure behaviour;
- Poppy never advances automatically beyond stage 6;
- every other initial species reaches stage 7 naturally under ideal conditions;
- stage reset returns to 1;
- colour remains unchanged through every stage and reset;
- save/load during intermediate and mature stages preserves state; and
- growth continues correctly after chunk unload/reload.

## Acceptance criteria

- No parallel farming simulation exists.
- All seven species have usable growth profiles.
- Perennial regrowth is implemented through an authoritative stage-1 reset.
- Colour is invariant across growth and regrowth.

## Suggested commit message

```text
feat(flowers): integrate perennial growth with farming simulation
```

---

# Milestone 6 - Interactions, Protection, Uprooting, and Poppy Stage 7

## Objective

Implement all player interactions and enforce one centralized protection policy across direct and indirect mutation paths.

## Required work

### Central authorization

Create or reuse one authoritative check for whether a player may mutate or remove a flower. All handlers must call it rather than duplicating partial checks.

Admin-protected flowers must reject ordinary-player:

- sword cutback;
- harvest;
- `britannia_hoe` uprooting;
- shovel mutation;
- normal block breaking;
- replacement placement;
- Poppy stage advancement;
- explosion or piston removal where project policy requires protection; and
- any other discovered bypass path.

Denied interactions must not alter state, create drops, consume items, or damage tools unless owner-approved behaviour says otherwise.

### Adventure-mode sword cutback

Implement the owner-approved sword rule. At minimum:

- actor is in Adventure mode;
- held item matches the approved sword class or tag;
- flower is not admin-protected;
- action is server-authoritative; and
- successful action resets the flower to stage 1 while preserving colour and species.

Use the existing attack/event architecture rather than relying on a client-only animation.

### Uprooting

For player-planted flowers, the approved `britannia_hoe` path permanently removes the flower and restores FarmingBlock with the preserved soil state where the architecture supports restoration.

Apply the owner’s decision for ordinary shovels. Regardless of that decision, ordinary hoes and shovels must have no effect on admin-protected flowers.

A true uproot ends the flower instance. The next planted seed performs a new colour roll.

### Harvesting and drops

Implement only the owner-approved drop behaviour. Harvest or cutting that is intended to preserve the planting must reset the same flower to stage 1 and retain its colour.

Do not conflate harvest reset with uprooting.

### Poppy stage 7

Implement a server-side manual interaction that advances a stage-6 Poppy to stage 7 only when:

- the species is Poppy;
- current stage is exactly 6;
- Farming skill is at least 100;
- the held item is the approved skinning knife or member of the approved tag; and
- the actor is authorized to mutate that flower.

The interaction retains species, colour, soil state, origin, and protection. Automatic growth remains capped at 6. After any approved reset to stage 1, the interaction is required again.

Apply owner-approved durability, sound, particles, advancement, and feedback behaviour without inventing additional rewards.

## Tests

Create a behaviour matrix covering at least:

| Flower origin | Actor/action | Required result |
|---|---|---|
| Player | Adventure sword | Reset to stage 1; same colour; no uproot |
| Admin | Ordinary Adventure sword | No effect |
| Player | `britannia_hoe` | Uproot and end instance |
| Admin | Ordinary `britannia_hoe` | No effect |
| Admin | Ordinary shovel | No effect |
| Admin | Ordinary break attempt | No effect |
| Admin | Creative/admin removal | Allowed |
| Poppy stage 6 | Farming < 100 + skinning knife | No advancement |
| Poppy stage 6 | Farming 100 + wrong tool | No advancement |
| Poppy stage 6 | Farming 100 + skinning knife | Advance to stage 7 |
| Protected Poppy stage 6 | Unauthorized qualifying player | No advancement |
| Poppy stage 7 after reset | Any | Returns to stage 1 and must earn stage 7 again |

Also test indirect mutation paths identified during discovery.

## Acceptance criteria

- Protection cannot be bypassed through any covered direct interaction.
- Adventure sword cutback regrows rather than destroys.
- Uprooting and harvest reset are distinct.
- Poppy stage 7 is manual, gated, repeatable after regrowth, and never automatic.
- Denied actions have no unintended drops, consumption, or tool damage.

## Suggested commit message

```text
feat(flowers): add protected interactions and poppy mastery stage
```

---

# Milestone 7 - Multi-Plane Rendering and Dye-Mask Tinting

## Objective

Complete the client rendering pipeline so each flower species and stage displays a fixed base texture plus a tinted grayscale dye-mask pass using the saved server colour.

## Required work

Implement the rendering approach approved after discovery.

### Render contract

For each flower instance:

```text
model = modelFor(speciesId, growthStage)
render fixed multi-plane base using base_texture with no flower tint
render matching multi-plane dye mask using stored colour tint
```

The client must obtain the colour from synchronized persistent state. It must not select a colour from the species definition.

### Two-texture enforcement

Each unique in-world model must use only:

- one `base_texture` file; and
- one `dye_mask` file.

The dye mask is both the alpha selection layer and grayscale luminance artwork. Do not add a third bloom texture.

### Visual requirements

- Multi-plane geometry is visible from expected viewing angles.
- Fixed stems, leaves, markings, and outlines remain untinted.
- Tint affects only non-transparent dye-mask pixels.
- Grayscale highlights and shadows remain visible after tinting.
- Base and mask remain pixel-aligned.
- There is no visible Z-fighting or flicker.
- Cutout edges do not show coloured halos.
- Rendering remains stable across chunk reload, resource reload, and multiplayer observation.
- Performance is reasonable for garden-scale concentrations of flowers.

### Fixed details

Species details that should not change colour, such as green Snowdrop markings or fixed stamens, belong in the base texture. They must not require a third texture.

### Item presentation

Use the owner-approved item approach from discovery. Seed and inventory item icons may follow normal item texture conventions. When a harvested flower item must preserve its planted colour, use the approved saved-component and item-tint path; otherwise, do not invent colour-bearing item state.

The exact two-texture invariant is mandatory for in-world flower growth models.

## Tests and visual QA

Validate:

- all species/stages resolve the correct model;
- all models resolve exactly two in-world texture files;
- tint is read from synchronized state;
- different flower instances of the same species can display different saved colours;
- the same flower displays the same colour to multiple clients;
- colour survives reload and restart;
- no tint reaches fixed base regions;
- pure or near-white tints retain mask detail;
- dark tints retain readable shading;
- Poppy stage 7 selects the correct model;
- missing species/colour IDs use the approved safe visual fallback without mutating saved data; and
- placeholder assets render without missing-texture errors.

Capture a development review image or test-world evidence showing several species, stages, and colours. Do not claim final artwork approval.

## Acceptance criteria

- The two-pass multi-plane renderer is functional.
- The exact two-texture contract is enforced.
- Saved colours render consistently in multiplayer.
- Placeholder models are replaceable without code changes when dimensions and UVs remain compatible.

## Suggested commit message

```text
feat(flowers): render multi-plane flowers with dye masks
```

---

# Milestone 8 - Initial Species Content, Balance Wiring, and Player-Facing Data

## Objective

Finish the initial seven-species data set using the design document’s growth profiles and realistic colour libraries, then integrate player-facing names and approved acquisition or drop behaviour.

## Required work

### Species content

Confirm all seven species have:

- a seed item mapping;
- a harvested flower item or approved equivalent;
- stage bounds;
- hydration profile;
- nutrient profile;
- altitude profile;
- regional profile;
- weighted colour palette;
- default/fallback colour;
- placeholder model references for every stage;
- localization; and
- any approved drops, recipes, tags, or creative-tab placement.

### Colour libraries

Use the design document as the baseline for realistic species palettes and rarity weights. The global tint definition stores RGB; each species stores its own weights and rarity labels.

Do not permit a colour for a species merely because the global colour exists. Species palettes are explicit allowlists.

### Growth profiles

Use the design document’s normalized values as starting points but adapt them to the existing crop system’s actual scale. Document every conversion rather than creating a second normalized runtime scale.

### Orfluer

Orfluer is fictional. Preserve the owner-approved spelling and registry ID. Its colour library and highland or magic-touched growth profile come from the design document unless later owner guidance changes them.

### Balance status

Mark values as initial tuning. Do not claim final balance until tested against actual Britannia regions, water availability, altitude distribution, and crop timing.

## Tests

Verify:

- all seven species load and can be planted;
- each species can roll only its explicit palette;
- all weights sum or normalize correctly;
- each species responds differently to at least one meaningful growth condition;
- all region tags resolve;
- no species is accidentally blocked across all practical farmland;
- Poppy remains capped at 6 automatically;
- all player-facing strings resolve; and
- no placeholder content is mistaken for final approved art.

## Acceptance criteria

- Seven complete species definitions are active.
- The growth values are mapped to real farming-system units.
- Realistic colour allowlists and rarity weights are functional.
- Player-facing registrations resolve without missing localization.

## Suggested commit message

```text
content(flowers): wire initial species palettes and growth profiles
```

---

# Milestone 9 - Regression Testing, Save Safety, and Multiplayer QA

## Objective

Prove that the flower feature works as a farming-system extension without regressing existing crops or corrupting saved state.

## Required test matrix

Update `FLOWER_SYSTEM_TEST_MATRIX.md` with automated and manual evidence for the following.

### Existing farming regression

- Existing annual crops still plant, grow, harvest, and reset as before.
- Existing tall crops, trellis crops, berries, fruit trees, or special cases remain unaffected.
- `britannia_hoe`, watering, fertilizer, hydration, nutrient, region, altitude, and quality behaviour remain unchanged for crops.
- FarmingBlock preparation and expiry remain unchanged before flower planting.

### Conversion and identity

- Hoe use never directly creates FlowerBlock.
- Only valid flower seed use converts FarmingBlock.
- Failed planting is atomic.
- Species and colour are saved and synchronized.
- Colour never rerolls while the same flower exists.
- Uproot followed by replant can produce a new colour.

### Growth

- Every species grows under ideal conditions.
- Tolerated and unsuitable conditions follow crop-system semantics.
- Poppy natural growth stops at 6.
- Other initial species reach 7 naturally.
- Reset to stage 1 preserves colour and soil state.

### Protection

- Ordinary players cannot alter admin-protected flowers through every discovered direct and indirect path.
- Creative/admin actors can remove protected flowers.
- Changing the original planter’s game mode later does not change stored protection.
- Admin-prepared soil does not protect a flower planted later by an ordinary player.

### Poppy mastery

- Farming 99 fails.
- Farming 100 succeeds only with the approved skinning knife.
- Stage 5 fails.
- Stage 6 succeeds.
- Stage 7 does not advance further.
- Reset requires the stage-7 action again.

### Rendering and assets

- Every model has exactly two in-world texture files.
- Every base and mask pair has matching dimensions and UV alignment.
- Mask RGB is grayscale where visible.
- Multiple colours render correctly.
- Multi-plane models are visible from all intended directions.
- No missing models, textures, or language keys occur.

### Persistence and compatibility

- Chunk unload/reload preserves all state.
- Server restart preserves all state.
- Client reconnect preserves visual state.
- Resource reload does not mutate world state.
- Unknown species and colour IDs follow safe fallback policy.
- Out-of-range stages are handled according to the approved migration policy.
- Legacy or missing origin/protection fields follow the approved default.

### Multiplayer

- Two clients see the same stage, species, colour, and protection.
- Simultaneous planting cannot consume multiple seeds or initialize twice.
- Simultaneous interactions cannot duplicate drops.
- Unauthorized client actions are rejected by the server and resynchronized.

### Performance

- Growth ticks do not perform unnecessary registry parsing or asset work.
- Colour definitions are resolved efficiently.
- A representative garden concentration does not create unreasonable renderer or networking overhead.

## Required runtime checks

Run the repository’s approved equivalents of:

- compile/build;
- unit tests;
- GameTests or integration tests;
- data generation consistency;
- resource loading;
- dedicated-server startup; and
- client test-world validation.

Record commands and results exactly.

## Acceptance criteria

- All critical test rows pass or have an owner-approved documented exception.
- Existing crops show no functional regression.
- Save/load and multiplayer state are stable.
- No protection bypass remains in tested paths.
- No missing resource or registry errors remain.

## Suggested commit message

```text
test(flowers): complete regression and multiplayer coverage
```

---

# Milestone 10 - Placeholder Handoff, Documentation, and Feature Closeout

## Objective

Prepare the feature for owner artwork replacement and final review without misrepresenting placeholder content as finished.

## Required work

### Documentation finalization

Update:

- `FLOWER_SYSTEM_IMPLEMENTATION_STATUS.md`;
- `FLOWER_ASSET_PLACEHOLDER_MANIFEST.md`;
- `FLOWER_SYSTEM_TEST_MATRIX.md`;
- `FLOWER_SYSTEM_DECISIONS.md`; and
- any player/admin documentation required by repository convention.

### Placeholder replacement instructions

For every model, document:

- species;
- growth stage;
- model path;
- base texture path;
- dye-mask path;
- dimensions;
- UV assumptions;
- transparent padding requirements;
- fixed details that belong in base;
- tintable regions that belong in dye mask; and
- validation command or test used after replacement.

State clearly:

```text
Do not add a third in-world texture.
Do not bake a final flower colour into dye_mask.
Do not change base/mask dimensions independently.
Do not change UV layout in only one file.
Do not recolour saved flower instances during asset replacement.
```

### Final repository audit

Confirm:

- branch remains `farming`;
- no unrelated files were changed;
- no debug-only code remains unless intentionally retained;
- no generated temporary files are untracked unexpectedly;
- placeholder assets are all listed;
- final-art replacement does not require registry or world-save changes;
- design invariants remain represented in tests; and
- all build and test commands pass.

### Final closeout report

The closeout must include:

```text
Branch:
Starting HEAD:
Final HEAD:
Milestone commits, if any:
Working tree status:
Implementation summary:
Architecture summary:
Seven species status:
Placeholder asset count:
Final-art replacements still required:
Automated test summary:
Manual test summary:
Known limitations:
Deferred future work:
Design invariant audit:
Recommended next step:
```

Future nutrient-driven colour selection must be listed as deferred. It may bias weights only at planting time and must never recolour an existing flower.

## Acceptance criteria

- The feature is fully documented.
- Placeholder replacement is safe and explicit.
- The branch is ready for owner review.
- No claim is made that placeholder artwork is final.
- No merge or push occurs without separate owner instruction.

## Suggested commit message

```text
docs(flowers): close implementation and asset handoff
```

---

## 6. Codex Milestone Invocation Template

Use this template when asking Codex to perform a milestone:

```text
Work only within the existing `farming` branch.

Read these files in full before acting:
- UltimaCraft_Persistent_Flower_System_Design.docx
- UltimaCraft_Flower_System_Codex_Milestone_Playbook.md
- FLOWER_SYSTEM_DISCOVERY_REPORT.md, if it exists
- FLOWER_SYSTEM_DECISIONS.md, if it exists
- FLOWER_SYSTEM_IMPLEMENTATION_STATUS.md, if it exists

Execute only Milestone <NUMBER>: <NAME> from the playbook.

Preserve all unrelated working-tree changes. Do not switch branches, create branches, merge, rebase, push, reset, or clean the repository. Do not work ahead into later milestones.

Before changing files, report the repository root, active branch, HEAD, and working-tree state. The active branch must be exactly `farming`.

Follow the milestone acceptance criteria and run the repository’s actual relevant build and test commands. Update the persistent flower status documents rather than creating duplicate milestone reports.

At the end, provide the complete milestone closeout report required by the playbook. Do not commit unless I explicitly authorize a commit in this prompt.
```

For the discovery phase, add:

```text
This is a discovery-only milestone. Do not implement flower code or assets. After completing the discovery report, ask all material architecture and integration questions in one consolidated list, then stop for my answers.
```

---

## 7. Status Tracking Template

Codex should maintain a compact table in `FLOWER_SYSTEM_IMPLEMENTATION_STATUS.md`:

| Milestone | Status | HEAD/commit | Tests | Owner approval | Notes |
|---|---|---|---|---|---|
| 0. Preflight | Not started |  |  |  |  |
| 1. Discovery | Not started |  |  |  |  |
| Owner decision gate | Not started |  | N/A |  |  |
| 2. Data contracts | Not started |  |  |  |  |
| 3. Placeholders | Not started |  |  |  |  |
| 4. Lifecycle | Not started |  |  |  |  |
| 5. Growth | Not started |  |  |  |  |
| 6. Interactions | Not started |  |  |  |  |
| 7. Rendering | Not started |  |  |  |  |
| 8. Species content | Not started |  |  |  |  |
| 9. QA | Not started |  |  |  |  |
| 10. Closeout | Not started |  |  |  |  |

Allowed statuses:

```text
Not started
In discovery
Blocked on owner
In progress
Implemented, awaiting validation
Validated, awaiting owner approval
Approved
Deferred
```

---

## 8. Final Definition of Done

The Persistent Flower System is complete only when all of the following are true:

- Work was performed and validated on `farming`.
- FarmingBlock remains unchanged until a valid flower seed is used.
- Seven initial species are registered and plantable.
- Each species uses its own hydration, nutrient, region, altitude, and colour data.
- Colour is selected once on planting and remains fixed for the flower instance.
- Persistent harvest/cutback returns the flower to stage 1 and permits regrowth.
- Adventure-mode sword cutback works for eligible unprotected flowers.
- Admin/Creative-planted flowers cannot be altered by ordinary hoe, shovel, sword, break, or covered bypass paths.
- Poppies stop naturally at stage 6 and reach stage 7 only through Farming 100 plus skinning knife.
- All in-world growth models are multi-plane.
- Every in-world model has exactly two texture files: `base_texture` and grayscale `dye_mask`.
- All placeholder items, seeds, models, and textures exist and are documented for replacement.
- Existing crop behaviour has not regressed.
- Save/load, restart, resource reload, and multiplayer behaviour are stable.
- Automated and manual test evidence is recorded.
- Placeholder artwork is clearly distinguished from final approved artwork.
- No merge or push has occurred without separate owner direction.

---

## 9. Deferred Expansion Hooks

These features are intentionally outside the initial implementation but should remain possible without redesigning saved flower identity:

- nutrient-biased colour weights at planting time;
- flower breeding or genetics;
- cross-pollination;
- quality-based yields or value;
- biome or season influence;
- admin colour-selection tools;
- rare-colour discoveries or achievements;
- flower arranging or decorative crafting; and
- additional species.

The most important future constraint is unchanged: nutrient logic may influence the colour roll when a new seed is planted, but it must never recolour an existing flower.
