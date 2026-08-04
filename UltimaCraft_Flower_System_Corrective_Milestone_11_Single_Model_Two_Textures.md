# UltimaCraft Persistent Flower System
## Corrective Milestone 11 — One Canonical Model, Two Textures

**Target branch:** `farming`  
**Milestone type:** Post-implementation defect correction  
**Scope:** Flower model-resource structure only  
**Primary design authority:** `UltimaCraft_Persistent_Flower_System_Design.docx`  
**Primary implementation playbook:** `UltimaCraft_Flower_System_Codex_Milestone_Playbook.md`  
**Amendment authority:** This document clarifies and supersedes any earlier wording that could be interpreted as requiring separate base-pass and dye-mask model files.

---

## 1. Purpose

Correct one asset and rendering-architecture defect in the completed UltimaCraft flower system.

The implemented project currently appears to use two model resources for a flower stage:

1. a regular or base model; and
2. a separate dye-mask model.

That is not the intended contract.

The intended contract is:

- **one canonical in-world model resource per flower species and growth stage;**
- **one fixed base texture referenced by that model/material definition;**
- **one grayscale `dye_mask` texture referenced by that same model/material definition;**
- the **same multi-plane geometry** rendered for both passes; and
- no separate species/stage model resource whose only purpose is the dye-mask pass.

This milestone must correct that defect without changing the working flower gameplay system.

---

## 2. Corrected Asset Contract

### 2.1 Incorrect interpretation

The following pattern is incorrect when both JSON files duplicate or separately represent the same stage geometry:

```text
models/block/flowers/poppy/stage_1_base.json
models/block/flowers/poppy/stage_1_dye_mask.json

textures/block/flowers/poppy/stage_1_base_texture.png
textures/block/flowers/poppy/stage_1_dye_mask.png
```

Equivalent pass-specific names such as these are also incorrect:

```text
stage_1.json
stage_1_mask.json

stage_1_base_model.json
stage_1_tint_model.json
```

The exact filenames may differ in the current repository. The defect is the existence of two species/stage model resources representing the base and tinted passes separately.

### 2.2 Required interpretation

Each species and growth stage must have one canonical stage-model lookup:

```text
models/block/flowers/poppy/stage_1.json
```

That canonical model/material contract must resolve the two image textures:

```text
textures/block/flowers/poppy/stage_1_base_texture.png
textures/block/flowers/poppy/stage_1_dye_mask.png
```

Conceptually:

```text
Poppy stage 1
└── one canonical multi-plane model
    ├── base_texture     -> rendered without tint
    └── dye_mask        -> rendered with the flower instance's saved tint
```

The two-pass render result remains required. The correction changes the model-resource structure, not the visual design.

### 2.3 Geometry ownership

The canonical stage model is the only species/stage model that owns or selects the flower's multi-plane geometry.

The renderer must reuse that same geometry for both passes:

```text
canonicalStageModel = modelFor(species, growthStage)

render canonicalStageModel geometry with base_texture and no tint
render canonicalStageModel geometry with dye_mask and saved flower tint
```

Do not load a second stage model to perform the dye-mask pass.

A shared generic parent model is permitted only when it is already part of the repository's normal model architecture. A parent must represent shared geometry or loader configuration; it must not reintroduce separate base-pass and mask-pass species/stage models.

### 2.4 Model texture references

Prefer a canonical model definition that exposes both materials using repository-appropriate texture keys, for example:

```json
{
  "parent": "britannia_mod:block/flowers/multi_plane_flower",
  "textures": {
    "base": "britannia_mod:block/flowers/poppy/stage_1_base_texture",
    "dye_mask": "britannia_mod:block/flowers/poppy/stage_1_dye_mask",
    "particle": "britannia_mod:block/flowers/poppy/stage_1_base_texture"
  }
}
```

This JSON is illustrative. Codex must follow the actual loader, renderer, model format, and naming conventions already implemented in the repository.

If the current Minecraft/NeoForge model path cannot expose two materials through ordinary texture keys, the approved custom renderer or model loader may derive the two texture resources from the canonical model identifier. Even in that case:

- there is still only one canonical species/stage model resource;
- geometry is defined or resolved once;
- the two PNG textures remain separate;
- the base and mask passes reuse the same geometry; and
- no pass-specific stage model is permitted.

---

## 3. Existing Feature Invariants That Must Not Change

This milestone must preserve all completed flower-system behaviour.

### 3.1 FarmingBlock conversion

A `FarmingBlock` becomes a flower block only when a valid flower seed is successfully used on it.

Tilling, preparing, watering, fertilizing, loading, or inspecting a `FarmingBlock` must never create a flower block.

### 3.2 Persistent flower colour

The flower colour is selected server-side once when the flower seed is planted.

The saved colour must not change during:

- natural growth;
- harvesting or cutback;
- return to growth stage 1;
- perennial regrowth;
- Adventure-mode sword interaction;
- Poppy stage 7 advancement;
- chunk reload;
- server restart;
- client reconnection; or
- resource reload.

### 3.3 Texture rule

Each unique in-world flower stage retains exactly two image textures:

```text
<stage>_base_texture.png
<stage>_dye_mask.png
```

The base texture contains all fixed, untinted artwork.

The `dye_mask` contains the grayscale tintable artwork and uses alpha to select the pixels affected by the saved flower colour.

Do not merge the two image textures. Do not add a third texture. Do not create per-colour textures.

### 3.4 Multi-plane model

Every in-world flower stage remains a multi-plane plant model. The correction must not turn flowers into cubes, single flat billboards, or inventory-style item models.

### 3.5 Gameplay and protection

Do not alter:

- hydration, nutrient, region, altitude, or quality calculations;
- species palettes or rarity weights;
- perennial regrowth;
- Adventure-mode sword cutback;
- hoe or shovel behaviour;
- admin/Creative planting protection;
- permission handling;
- flower drops;
- seed handling;
- Poppy's natural stage-6 cap;
- Farming 100 plus skinning-knife access to Poppy stage 7;
- persistence or networking formats; or
- existing registry IDs.

---

## 4. Branch and Repository Safety

All work must occur on the existing branch:

```text
farming
```

At the beginning, run and report:

```bash
git rev-parse --show-toplevel
git branch --show-current
git status --short --branch
git log -5 --oneline
```

If the active branch is not exactly `farming`, stop and report the mismatch.

Do not:

- create or switch branches;
- merge or rebase;
- reset or rewrite history;
- push to a remote;
- use destructive cleanup commands;
- remove unrelated untracked files; or
- modify unrelated systems.

Do not commit unless the owner explicitly authorizes a commit in the invocation prompt.

---

## 5. Required Context Review

Before changing files, read:

```text
UltimaCraft_Persistent_Flower_System_Design.docx
UltimaCraft_Flower_System_Codex_Milestone_Playbook.md
FLOWER_SYSTEM_DISCOVERY_REPORT.md
FLOWER_SYSTEM_DECISIONS.md
FLOWER_SYSTEM_IMPLEMENTATION_STATUS.md
FLOWER_ASSET_PLACEHOLDER_MANIFEST.md
FLOWER_SYSTEM_TEST_MATRIX.md
```

Read any applicable repository guidance, including nested `AGENTS.md`, `CLAUDE.md`, `README.md`, or contribution instructions.

If one of the listed flower-project documents does not exist, record that fact. Do not fabricate its contents.

This corrective milestone has authority over the narrow model-file issue. All unrelated decisions remain governed by the original design, playbook, and decision log.

---

## 6. Phase A — Defect Audit

Perform a focused audit before implementation.

### 6.1 Identify current model structure

For every initial species and growth stage, determine the current model-resource paths:

- Poppy stages 1–7;
- Snowdrop stages 1–7;
- Lily stages 1–7;
- Foxglove stages 1–7;
- Campion stages 1–7;
- Hyacinth stages 1–7; and
- Orfluer stages 1–7.

Identify:

- blockstate or stage-to-model mappings;
- canonical model lookup code;
- base-pass model files;
- dye-mask-pass model files;
- shared parent models;
- model-loader registrations;
- render-pass code;
- texture lookup code;
- asset/data generators;
- resource validation tests; and
- manifest entries.

### 6.2 Confirm the defect

Document the exact implemented pattern with repository-relative paths and symbols.

Answer these questions in the milestone report:

1. Are two model JSON files loaded for each species/stage?
2. Do both files contain or inherit the same geometry?
3. Does the blockstate or resolver expose both models?
4. Does the renderer request a separate baked model for the mask pass?
5. Are duplicate model files generated automatically?
6. Which tests or manifest rules currently permit the duplicate structure?
7. Can the redundant model resources be removed without changing saved world data?

### 6.3 Establish the correction plan

Before editing, state:

- the one canonical model path that each stage will use;
- how the canonical model resolves both texture resources;
- how the renderer will reuse one geometry definition for two passes;
- which redundant files will be removed;
- which generators and tests must change; and
- whether any discovered architecture conflict is blocking.

Proceed with the correction unless a genuine architecture conflict makes the required one-model contract impossible without a broader redesign. If blocked, stop and present the conflict, available options, and a recommendation.

Do not ask questions already answered by this document.

---

## 7. Phase B — Implementation

### 7.1 Canonicalize species/stage model lookup

For each of the 49 species/stage combinations, ensure the normal model lookup resolves to one canonical stage model.

Preferred path shape:

```text
assets/britannia_mod/models/block/flowers/<species>/stage_<n>.json
```

Adapt the path only when required by established repository conventions.

The blockstate, model resolver, stage selector, or renderer must reference the canonical stage model only.

### 7.2 Reuse the same geometry for both render passes

Update the rendering implementation so that it:

1. resolves one canonical stage model;
2. obtains or resolves the base texture;
3. renders the canonical multi-plane geometry without tint;
4. obtains or resolves the grayscale `dye_mask`;
5. renders the same canonical geometry with the saved flower tint; and
6. does not resolve a second species/stage model for the dye-mask pass.

Maintain the existing approved solution for avoiding visible Z-fighting, such as the current material/layer strategy, polygon offset, geometry-layer handling, or loader-specific technique. Do not introduce materially different geometry between the passes.

### 7.3 Remove redundant model resources

Remove only model files that exist solely to provide a separate base or dye-mask pass for the same species/stage.

Do not remove the two PNG textures.

Do not remove legitimate shared parent models, loader definitions, item models, or unrelated crop models.

After removal, search for and eliminate all stale references to the deleted pass-specific model resources.

### 7.4 Correct asset generation

If a generator created separate base and mask model JSONs, update it to generate:

- one canonical stage model; and
- two stage texture images.

Regenerate only the flower assets required by this milestone. Avoid unrelated generated-file churn.

### 7.5 Correct documentation and manifests

Update the applicable project documents so they unambiguously distinguish model resources from texture resources.

At minimum, update:

```text
FLOWER_ASSET_PLACEHOLDER_MANIFEST.md
FLOWER_SYSTEM_IMPLEMENTATION_STATUS.md
FLOWER_SYSTEM_TEST_MATRIX.md
FLOWER_SYSTEM_DECISIONS.md
```

The manifest must have one model-path entry per species/stage and two texture-path entries:

```text
model_path
base_texture_path
dye_mask_texture_path
```

Remove any manifest structure that treats the base and dye-mask passes as separate stage models.

Add a decision entry equivalent to:

> Each flower species/growth-stage lookup resolves one canonical multi-plane model. That model's geometry is rendered twice: once with the fixed base texture and once with the grayscale dye-mask texture tinted by the flower's saved colour. Separate base and dye-mask stage-model resources are prohibited.

The original design DOCX does not need to be rewritten during this code correction unless the owner separately requests it. This amendment and the updated repository documentation must make the corrected rule explicit.

---

## 8. Non-Goals

Do not use this milestone to:

- redesign flower gameplay;
- change class or registry IDs;
- rebalance species;
- change palettes or rarity;
- replace placeholder artwork with final art;
- change texture dimensions or UV layouts unless required to repair a proven mismatch;
- add new flower species;
- implement nutrient-driven colour selection;
- alter saved block-entity data;
- add a third render texture;
- combine the base and mask PNGs;
- rewrite the farming engine;
- refactor unrelated rendering systems; or
- perform broad code formatting.

Any unrelated defect discovered must be reported as deferred work.

---

## 9. Required Validation

Use the repository's discovered commands rather than assuming task names.

### 9.1 Structural validation

Validate that:

- all 49 species/stage lookups resolve;
- each lookup has exactly one canonical stage model;
- no pass-specific base or dye-mask species/stage model remains;
- every canonical stage model resolves one base texture and one `dye_mask` texture;
- both texture files exist;
- base and mask dimensions match;
- base and mask UV alignment remains compatible;
- the mask remains grayscale where alpha is non-zero;
- no third in-world texture was introduced;
- all JSON parses;
- all generator outputs are deterministic; and
- no stale references remain.

Add or update automated validation that fails when a future change introduces names or mappings equivalent to separate pass-specific stage models.

The test should evaluate the actual asset graph or model manifest, not only a fragile filename convention.

### 9.2 Build and resource validation

Run:

- the normal compile/build validation;
- resource or data-generation validation;
- client resource loading;
- the narrow flower asset tests; and
- any dedicated-server validation affected by client/server separation.

The dedicated server must not attempt to load client-only model classes or textures.

### 9.3 Visual parity testing

In a development client or existing flower test world, verify:

- every species renders at every available stage;
- the fixed base regions remain untinted;
- the mask regions receive the saved colour;
- highlights and shadows remain visible;
- there are no missing-model or missing-texture patterns;
- the two passes remain aligned;
- no new flicker or Z-fighting appears;
- light and dark colours remain readable;
- Poppy stage 7 uses its canonical stage-7 model;
- different saved colours still render on different instances of the same species; and
- resource reload does not change flower state or colour.

Where the repository's normal process permits it, record screenshots or test-world evidence for review.

### 9.4 Regression validation

Confirm that this correction does not change:

- planting;
- growth;
- hydration or nutrients;
- harvest/reset behaviour;
- admin protection;
- sword cutback;
- Poppy stage 7;
- persistence;
- multiplayer synchronization;
- item models; or
- non-flower crop rendering.

---

## 10. Acceptance Criteria

This milestone is complete only when all of the following are true:

- The active branch remained `farming`.
- Every flower species/stage lookup resolves one canonical in-world model.
- No separate dye-mask species/stage model is loaded or retained.
- The canonical multi-plane geometry is reused for both rendering passes.
- Every canonical stage model resolves exactly two in-world image textures: one base texture and one grayscale `dye_mask`.
- All 49 stage lookups remain valid.
- No registry IDs or saved flower data changed.
- Existing flower gameplay remains unchanged.
- The asset generator, manifest, implementation documentation, and tests reflect the corrected contract.
- Build, resource, and relevant flower tests pass, or environmental limitations are reported precisely.
- No unrelated files were modified.
- No commit, merge, rebase, or push occurred unless separately authorized.

---

## 11. Required Closeout Report

End with:

```text
Milestone:
Branch and HEAD before work:
Branch and HEAD after work:
Working tree before:
Working tree after:

Defect confirmed:
Previous model structure:
Corrected model structure:
Canonical model naming:
Texture resolution method:
Renderer changes:
Redundant model files removed:
Stale references removed:

Files inspected:
Files added:
Files modified:
Files removed:
Generators updated:
Documentation updated:
Tests added or updated:

Commands run:
Results:
Visual QA performed:
Regression status:
Known limitations:
Deferred work:
Owner decisions required:
Suggested commit message:
```

The suggested commit message is:

```text
fix(flowers): use one stage model with base and dye-mask textures
```

Do not create the commit unless the owner explicitly authorizes it.

---

## 12. Codex Execution Instruction

When this document is supplied to Codex, use the following instruction:

> Read the root design document, the original milestone playbook, all completed flower-system project records, and this corrective milestone. Execute only Corrective Milestone 11 on the existing `farming` branch. First audit and report the exact duplicate-model implementation, then correct it so every flower species/stage uses one canonical multi-plane model with two texture resources: `base_texture` and grayscale `dye_mask`. Preserve all gameplay, persistence, registry, and networking behaviour. Run the required validation, update the project records, provide the closeout report, and do not commit or push unless explicitly authorized.
