# UltimaCraft — Flagstone Foundation & Foundation Texture Architecture Playbook

## Project Purpose

This project extends and cleans up UltimaCraft's existing foundation-block system on the `patch-18` branch.

The immediate feature is a new **Flagstone Foundation** family derived from the existing `brick_foundation` group of blocks.

The project must also improve how foundation texture families are managed. The current system contains at least two visually and semantically distinct foundation families:

- the existing `brick_foundation` family, historically using `brick_foundation.png`
- a separate dark-foundation family using `brick_dark_foundation.png`

The ordinary brick foundation texture now has three intended visual variations:

- `brick_foundation_01.png`
- `brick_foundation_02.png`
- `brick_foundation_03.png`

Existing blocks that belong to the normal brick-foundation family should visually select among those three textures without accidentally affecting blocks that belong to the `brick_dark_foundation.png` family.

The implementation must be based on the architecture that actually exists in the repository. Do not assume how these blocks are registered, modelled, rendered, connected, rotated, waterlogged, persisted, or grouped until the code and resources have been inspected.

The owner also stated that additional modifications to foundation blocks are desired, but the supplied sentence ended before those additional defects were enumerated. Do **not** invent missing requirements. Record any related defects discovered during analysis, but treat only the explicit requirements in this playbook as committed scope unless repository evidence makes a correction necessary for the requested feature.

---

# Operating Principles

## 1. Discovery before implementation

Do not begin by copying files or registering a new block.

First determine:

- every block belonging to the existing `brick_foundation` family
- every block belonging to the dark-foundation family
- whether other foundation families already exist
- the Java classes involved
- registration structure
- blockstate architecture
- model inheritance
- texture indirection
- item models
- loot tables
- recipes, if any
- creative-tab registration
- tags
- data generation
- collision and selection shapes
- placement/orientation logic
- connection or multipart logic
- waterlogging or other state
- any block entities or custom renderers
- test coverage
- naming conventions
- asset-generation conventions

The repository is authoritative.

## 2. Preserve behavior

The Flagstone Foundation is intended to be a visual/material sibling of the existing brick foundation group, not an excuse to redesign foundation gameplay.

Unless evidence shows otherwise, preserve:

- geometry
- collision
- placement rules
- connectivity
- orientation
- drops
- hardness/resistance
- mining requirements
- sound type
- state serialization
- waterlogging
- redstone behavior
- server/client behavior
- item representation
- existing world compatibility

## 3. Separate material families explicitly

Do not solve the texture problem with brittle filename substitution or broad search/replace logic.

The architecture should make it difficult for a block in the `brick_dark_foundation` family to accidentally inherit normal brick textures.

Prefer an explicit concept such as:

- foundation material family
- foundation texture set
- model-family identifier
- material-specific model parent
- reusable datagen/model helper

The exact implementation should follow repository conventions.

## 4. Random texture selection must be stable

The three normal brick textures are visual variants of the same material.

The desired result is random-looking distribution across placed blocks, but not animation or flicker.

Unless the existing architecture requires another method, prefer Minecraft's normal model/blockstate variation system so that visual selection is stable for a given block position and does not require unnecessary server-side persistent state.

Do not introduce:

- a ticking block entity merely to choose a texture
- client/server desynchronization
- texture changes every frame
- texture changes every chunk rebuild
- duplicated gameplay blocks solely for three cosmetic variants

If weighted model variants are unsuitable because of the existing blockstate/multipart architecture, identify the reason and choose the simplest stable alternative.

## 5. Avoid unnecessary duplication

The new Flagstone family should reuse the existing foundation implementation where behavior is actually shared.

Do not create a second copy of an entire Java block class simply because the texture differs.

Likewise, do not force unrelated material families through one giant abstraction if that makes resource generation or blockstates harder to understand.

Use the smallest abstraction that gives clear ownership of:

- geometry/behavior
- material family
- texture set
- model generation
- registration

## 6. Protect `patch-18`

Work only on the intended `patch-18` branch unless the owner explicitly directs otherwise.

Before implementation:

- confirm the current branch
- inspect working-tree status
- do not discard unrelated owner changes
- do not reset, clean, or rewrite history destructively
- do not modify unrelated feature work

Every milestone should end with a concise evidence record in Claude's response rather than adding project documentation files unless such files already belong to the repository's normal workflow.

---

# Scope

## Required Feature A — Existing Brick Foundation Texture Variants

For every foundation block that currently belongs to the normal `brick_foundation.png` material family:

1. Stop treating the old single texture as the only surface.
2. Use:
   - `brick_foundation_01.png`
   - `brick_foundation_02.png`
   - `brick_foundation_03.png`
3. Produce natural random visual distribution.
4. Keep the selected visual stable during normal play.
5. Preserve all geometry and gameplay behavior.

Determine whether `brick_foundation.png` should:

- remain as a compatibility/resource alias,
- become unused but retained temporarily,
- or be removed,

based on actual references and repository policy. Do not delete it merely because the three numbered textures exist.

## Required Feature B — Protect Dark Foundation

Blocks using `brick_dark_foundation.png` are a different foundation family.

They must:

- continue to use their dark material
- not randomly pull from the normal brick texture set
- not be renamed or reclassified merely to fit the new feature
- retain their current appearance unless a genuine existing defect must be corrected

The implementation should make this separation obvious in code/resources.

## Required Feature C — Add Flagstone Foundation Family

Create a new **Flagstone Foundation** group based on the existing normal `brick_foundation` group.

Claude must first determine exactly what "group" means in this repository.

If `brick_foundation` consists of multiple companion blocks, the Flagstone family should normally receive equivalent companion blocks unless the architecture or naming conventions clearly indicate otherwise.

The Flagstone family should reuse:

- the same foundation geometry
- the same placement behavior
- the same connection/state behavior
- the same collision behavior
- the same gameplay characteristics

while using the appropriate Flagstone material textures.

Before creating placeholder textures or inventing filenames, search the repository for owner-provided Flagstone assets. If assets are missing, report the expected filenames/paths and implement only what can be completed without fabricating production artwork, unless the project already has an established generated-placeholder convention.

## Required Feature D — Foundation Management Cleanup

Refactor only as much as needed to make the foundation families understandable and maintainable.

The finished architecture should answer these questions clearly:

- Which blocks share foundation behavior?
- Which blocks belong to which material family?
- Which texture set does each family use?
- Which families use multiple cosmetic variants?
- How are model/blockstate resources generated or maintained?
- How would a fourth foundation material be added later?

---

# Non-Goals

Unless repository evidence makes them necessary to complete the requested work, do not:

- redesign foundation geometry
- change the dark foundation art
- change unrelated brick blocks
- change unrelated walls, floors, stairs, roofs, or sandstone systems
- redesign world generation
- add new gameplay mechanics
- add block entities for cosmetic texture selection
- change networking
- alter save format unnecessarily
- migrate existing placed blocks to a new registry ID
- remove old registry IDs
- perform broad asset cleanup outside the foundation subsystem

---

# Milestone 0 — Repository Safety & Baseline

## Goal

Establish a safe baseline before touching the foundation subsystem.

## Tasks

1. Confirm repository root.
2. Confirm current branch is `patch-18`.
3. Record:
   - `git status`
   - current HEAD
   - relevant uncommitted files
4. Do not modify or remove unrelated owner work.
5. Identify build/test commands from the repository rather than guessing.
6. Run the smallest useful existing compile/resource validation baseline.
7. If the baseline already fails, record failures exactly and distinguish them from later project regressions.

## Exit Criteria

Milestone 0 is complete only when Claude can state:

- branch confirmed
- working tree understood
- baseline build/test condition known
- no destructive cleanup performed

---

# Milestone 1 — Foundation Architecture Reconnaissance

## Goal

Understand the existing foundation implementation thoroughly before changing it.

## Required Investigation

Search for all references to at least:

- `brick_foundation`
- `brick_foundation.png`
- `brick_dark_foundation`
- `brick_dark_foundation.png`
- `foundation`
- block registration entries
- model JSON references
- blockstate JSON references
- item model references
- relevant datagen helpers
- creative-tab entries
- tags and loot tables
- tests

Build an explicit inventory of the foundation families.

For every discovered foundation block, identify:

- registry ID
- Java registration location
- implementation class
- material/texture family
- model
- blockstate
- states/properties
- item model
- loot behavior
- creative-tab placement
- associated companion shapes
- whether it is currently normal brick or dark brick

Inspect representative source/resource files in full rather than relying only on grep output.

## Architecture Questions Claude Must Answer

1. Is the foundation family one block or multiple block types?
2. Are corner/end/edge/inside/outside/straight variants separate registry blocks or state/model variants?
3. Are models handwritten or data-generated?
4. Are textures assigned through model parents or repeated directly?
5. Is multipart blockstate logic involved?
6. Is model randomization already used elsewhere in UltimaCraft?
7. What existing project pattern is safest to reuse for three cosmetic texture variants?
8. Can randomization happen entirely through resource/model definitions?
9. What makes the dark foundation family distinct today?
10. What files would be duplicated if Flagstone were implemented naïvely?
11. Is there already a material-family abstraction worth extending?
12. Are there existing tests that can be generalized?

## Deliverable

In Claude's milestone response, provide:

- a foundation inventory
- the current architecture
- the proposed minimal architecture change
- the exact expected file categories to touch
- identified risks

Do not implement the feature during this milestone except for non-functional investigation aids that are removed before proceeding.

## Exit Criteria

No implementation until Claude can explain the complete path from:

`registry -> block implementation -> blockstate -> model -> texture -> item representation`

for both normal brick and dark foundation families.

---

# Milestone 2 — Design the Material/Texture Family Solution

## Goal

Choose the maintainable architecture before adding Flagstone.

## Preferred Design Characteristics

The solution should:

- keep shared foundation behavior shared
- express material family explicitly
- allow one family to have one texture
- allow another family to have three stable cosmetic variants
- allow Flagstone to use its own texture set
- keep dark foundation isolated
- minimize repeated blockstate/model JSON
- follow existing datagen conventions if datagen is already authoritative

## Randomization Analysis

Evaluate the repository and choose among approaches such as:

### A. Weighted model variants

Use multiple equivalent models pointing to different textures and reference them as weighted blockstate variants.

This is preferred when it works naturally with the existing state structure.

### B. Multipart-compatible weighted models

If the foundation uses multipart states, determine whether each applicable part can reference weighted alternatives without breaking connections/orientation.

### C. Existing project-specific model/datagen abstraction

If UltimaCraft already has a stable texture-variation framework, reuse it rather than introducing a competing system.

### D. Persistent property/block-entity selection

Use only if the resource system cannot satisfy the requirement and explain why. Cosmetic variation alone is not sufficient justification for a block entity.

## Design Requirements

The design must explicitly prevent a normal brick variation list from being implicitly applied to `brick_dark_foundation`.

If a helper is created, prefer an API conceptually like:

`foundationFamily(materialKey, textureSet, ...)`

over logic conceptually like:

`if filename contains "foundation" then randomize`

The exact API must fit the actual codebase.

## Testing Plan

Before implementation, identify how to verify:

- all three normal brick variants are reachable
- visual choice is stable
- all normal foundation shapes use the correct variation set
- dark foundation remains dark
- Flagstone uses only Flagstone textures
- block/item registration works
- existing world IDs remain valid
- resource loading produces no missing-model/missing-texture errors

## Exit Criteria

Milestone closes when Claude has a concrete file-level implementation plan and no unresolved architectural ambiguity remains.

---

# Milestone 3 — Implement Normal Brick Texture Variations

## Goal

Upgrade the existing normal brick foundation family to the three numbered textures without changing gameplay.

## Tasks

1. Wire:
   - `brick_foundation_01.png`
   - `brick_foundation_02.png`
   - `brick_foundation_03.png`
2. Apply the variation mechanism to all members of the normal brick foundation family that previously used `brick_foundation.png`.
3. Preserve state/model orientation.
4. Preserve UV/layout expectations.
5. Ensure all three variants represent the same geometry.
6. Confirm there is no per-frame or per-tick visual switching.
7. Confirm inventory/item rendering has a deliberate policy:
   - one canonical texture, or
   - stable/appropriate variant behavior,
   depending on repository conventions.
8. Audit remaining references to `brick_foundation.png`.
9. Do not alter dark foundation references.

## Validation

At minimum:

- resource generation/validation
- compile
- relevant tests
- grep/reference audit
- model/texture existence check

If practical, add an automated resource-level test or datagen assertion that ensures the normal family contains exactly the expected texture set and the dark family is excluded.

## Exit Criteria

- all normal brick foundation shapes can show the three new textures
- no dark foundation block references them
- no missing resources
- no behavior regression found

---

# Milestone 4 — Introduce Explicit Foundation Family Management

## Goal

Refactor the minimum necessary foundation resource/registration logic so material ownership is obvious.

This milestone may be merged with Milestone 3 in implementation if the repository's architecture makes that cleaner, but its acceptance criteria must still be addressed separately.

## Tasks

1. Centralize repeated material-family definitions where appropriate.
2. Separate:
   - normal brick
   - dark brick
   - Flagstone
3. Keep behavior shared independently from texture selection.
4. Remove only duplication that is directly relevant to the foundation subsystem.
5. Preserve registry names and old-world compatibility.
6. Add comments only where architecture would otherwise be non-obvious.
7. Prefer self-explanatory names over comments explaining fragile logic.

## Anti-Patterns to Reject

- material choice based on substring matching
- global "all foundations use these textures" arrays
- duplicated Java classes for texture-only changes
- copy-pasted blockstate files with no abstraction when datagen already exists
- one giant generalized block system affecting unrelated blocks
- hidden behavior changes during cosmetic refactor

## Exit Criteria

A future developer should be able to add another material family without studying every foundation resource file individually.

---

# Milestone 5 — Add Flagstone Foundation

## Goal

Add the new Flagstone Foundation family as a material sibling of the existing normal brick foundation group.

## Tasks

1. Search for existing Flagstone texture assets and naming conventions.
2. Determine the complete set of companion foundation blocks required by parity with `brick_foundation`.
3. Register the Flagstone blocks with consistent registry IDs.
4. Reuse the established foundation behavior.
5. Generate/create matching models and blockstates using the new family architecture.
6. Add item models.
7. Add loot tables if required by project conventions.
8. Add tags if equivalent foundation blocks use them.
9. Add creative-tab entries beside related foundation blocks.
10. Add recipes only if the existing foundation family has recipes and parity is intended.
11. Add localization/display names.
12. Verify all new assets are referenced and all references resolve.

## Naming

Derive naming from the existing repository rather than assuming exact registry IDs.

Use "flagstone foundation" consistently in user-facing localization unless existing naming conventions dictate a more specific pattern.

## Texture Behavior

Do not assume Flagstone should use the three brick textures.

Flagstone must use Flagstone art.

If multiple Flagstone variants already exist, Claude may apply the same texture-set abstraction after verifying that this is intended by the assets. If only one Flagstone texture exists, use one.

## Exit Criteria

Flagstone has functional parity with the existing brick foundation group and does not alter the normal or dark foundation visuals.

---

# Milestone 6 — Automated Regression Coverage

## Goal

Prove the architectural cleanup did not silently damage the existing families.

## Add or Extend Tests Where Practical

Cover as much as the repository's current test infrastructure supports:

- registry presence
- registry uniqueness
- block/item pairing
- creative-tab exposure if testable
- expected block properties
- loot-table presence
- expected tags
- resource/model existence
- texture references
- model parent resolution
- normal brick texture set membership
- dark brick exclusion from normal texture set
- Flagstone texture-family membership
- preservation of important block states
- serialization compatibility if relevant

If visual weighted variants cannot be meaningfully unit tested, test the generated JSON/resource graph that enables them.

## Required Full Checks

Run the relevant:

- compile
- unit tests
- GameTests if present and relevant
- datagen validation
- resource validation
- repository-specific check task

Do not call a milestone green merely because compilation succeeds.

## Exit Criteria

No new unexplained failures.

Pre-existing failures must be identified separately from project-induced failures.

---

# Milestone 7 — In-Game Validation Matrix

## Goal

Validate the result under actual Minecraft rendering and placement behavior.

## Manual Validation

Place a representative sample of every normal brick foundation shape in a sufficiently large arrangement.

Confirm:

- `_01`, `_02`, and `_03` appearances are all observable
- distribution looks naturally mixed
- the same placed block does not visibly flicker or cycle while stationary
- chunk reload does not produce pathological behavior
- neighboring connections still work
- rotations still work
- corner/edge/straight or equivalent shapes remain correct
- collision is unchanged
- breaking/replacing works normally
- inventory items render correctly

Place dark foundation blocks beside normal brick foundation blocks.

Confirm:

- dark blocks remain dark
- no normal-brick texture leaks into them
- connection behavior remains correct

Place the complete Flagstone family.

Confirm:

- geometry matches the intended brick-foundation equivalents
- textures are Flagstone
- all companion shapes exist
- item names are correct
- creative-tab entries are present
- drops are correct
- orientation/connectivity is correct

If shaders/resource packs materially affect these blocks, perform a representative smoke test where practical.

## Evidence

Claude should provide a precise manual-validation checklist for the owner if Claude cannot itself launch and visually inspect the client.

Do not falsely claim visual validation that was not performed.

---

# Milestone 8 — Final Audit & Delivery

## Goal

Finish with a clean, scoped, reviewable patch.

## Final Audit

Review the complete diff.

Confirm:

- no unrelated files were changed
- no owner files were deleted
- no dark-foundation texture behavior changed unintentionally
- old registry IDs remain intact
- no placeholder assets were introduced without disclosure
- no missing texture/model warnings are expected
- no redundant copied implementation was added
- all new blocks are localized
- all intended resources are tracked
- generated files match repository policy
- formatting/style matches the project

Run the final test suite appropriate to the touched area.

## Final Claude Report

Provide:

### 1. What changed

Summarize:

- brick variation implementation
- foundation-family architecture
- dark-foundation protection
- Flagstone additions

### 2. Exact files changed

Group by:

- Java
- blockstates
- models
- textures
- localization
- data/tags/loot
- tests/datagen

### 3. Validation

List commands actually run and their results.

### 4. Manual validation still required

State any steps the owner must perform in Minecraft.

### 5. Risks / follow-up

Only genuine remaining issues.

### 6. Git state

State:

- branch
- HEAD
- whether working tree is clean
- whether commits were made

Do not push unless explicitly instructed.

---

# Acceptance Criteria

The project is complete only when all of the following are true:

- Claude analyzed the complete existing foundation subsystem before modifying it.
- Existing blocks formerly using `brick_foundation.png` now use the intended three-texture set:
  - `brick_foundation_01.png`
  - `brick_foundation_02.png`
  - `brick_foundation_03.png`
- Visual selection is random-looking and stable, not animated/flickering.
- `brick_dark_foundation.png` remains isolated to the correct dark-foundation family.
- No broad filename-based texture hack was introduced.
- The foundation architecture now has a clear material/texture-family concept or an equally maintainable repository-native solution.
- Flagstone Foundation exists as the appropriate equivalent group to `brick_foundation`.
- Flagstone reuses shared foundation behavior instead of copying it unnecessarily.
- Flagstone uses Flagstone textures, not normal brick or dark brick textures.
- Existing registry IDs and placed-world compatibility are preserved.
- All relevant blockstates/models/item models/resources resolve.
- Creative-tab exposure matches project conventions.
- Loot/tags/localization are complete where applicable.
- Automated checks are green or all pre-existing failures are explicitly separated.
- Manual in-game validation instructions are provided for any visual behavior Claude cannot directly prove.
- The final diff remains narrowly scoped to this project.

---

# Guidance for the Incomplete Owner Requirement

The owner's original request ended with:

> "I would also like to modify these foundation blocks because there are"

No additional defect description followed.

Therefore:

1. Do not invent the missing requirement.
2. During Milestone 1, inspect for obvious foundation defects that are directly adjacent to this work.
3. Record discovered issues under a heading such as `Additional Foundation Findings`.
4. Fix only issues that:
   - are objectively broken,
   - are necessary for the requested texture/family architecture,
   - or are clearly regressions caused by the current implementation.
5. Do not silently expand into unrelated foundation redesign.
6. If the owner later supplies the missing requirements, append them to the appropriate milestone and acceptance criteria without discarding completed analysis.

---

# Definition of Done

"Done" means the foundation subsystem is better understood, safer to extend, the normal brick family correctly uses the three new texture variations, the dark family remains protected, and the new Flagstone Foundation family is fully integrated with equivalent functionality.

It does **not** mean merely registering one new block and making the game compile.
