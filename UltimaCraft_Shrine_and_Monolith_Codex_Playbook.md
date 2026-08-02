# UltimaCraft Shrine and Monolith Codex Playbook

**Purpose:** Milestone-by-milestone implementation instructions for Codex  
**Authoritative design:** `UltimaCraft_Shrine_and_Monolith_System_Design.md`  
**Recommended feature branch:** `shrines-monoliths`  
**Known integration branch:** `patch-18`  
**Project scope:** Britannia Minecraft mod only

---

## 1. How Codex Must Use This Playbook

Codex must implement exactly one milestone at a time.

At the beginning of every milestone:

1. Read this playbook in full.
2. Read `UltimaCraft_Shrine_and_Monolith_System_Design.md` in full.
3. Read:
   - `docs/shrines-monoliths/PROJECT_FACTS.md`
   - `docs/shrines-monoliths/OPEN_QUESTIONS.md`
   - `docs/shrines-monoliths/IMPLEMENTATION_LOG.md`
4. Inspect the current branch and working tree.
5. Inspect the latest completed milestone report and commit.
6. Confirm the active milestone and its explicit exclusions.
7. Inspect repository code and the actual NeoForge classpath before choosing APIs.

Codex must not begin the next milestone automatically.

A milestone is complete only after:

- Its implementation is present.
- Its required tests actually ran.
- Exact commands and results are recorded.
- Its acceptance criteria pass.
- The implementation log is updated.
- A coherent commit is created when the repository is safe to commit.
- The final report distinguishes automated, manual, and unperformed validation.

---

## 2. Global Safety Rules

### Git

Before changing files, run repository-appropriate equivalents of:

```text
git branch --show-current
git status --short --branch
git rev-parse HEAD
git merge-base shrines-monoliths patch-18
git rev-list --left-right --count patch-18...shrines-monoliths
git log --oneline --decorate -15
```

Rules:

- Work only on `shrines-monoliths`.
- Create it from the current approved `patch-18` tip only during Milestone 0.
- If the branch already exists, inspect it. Do not recreate, reset, or overwrite it.
- Do not commit directly to `patch-18`.
- Do not merge into `patch-18`.
- Do not push unless explicitly instructed.
- Do not rebase shared branches.
- Do not force-push.
- Do not hard-reset.
- Do not delete worktrees or branches.
- Do not clean untracked files.
- Do not stash or discard user work without explicit approval.
- Do not use `git add .`.
- Stage only explicit milestone paths.
- Preserve `.claude/`, logs, temporary validation directories, root specifications, and unrelated modified files unless the active milestone explicitly owns them.
- If branch ancestry or preserved user work is unsafe or ambiguous, stop and report the exact state.

### Scope

- Implement only the active milestone.
- Do not perform unrelated refactors.
- Do not rename unrelated classes or resources.
- Do not introduce Rails or website changes.
- Do not add recipes, NPC shops, progression, or economy behavior.
- Do not fabricate art, final names, lore, or missing monolith definitions.
- Do not modify approved shrine textures as part of implementation.
- Do not create a second Interior Decorator tool.
- Do not create normal direct-use block items for anchor or part blocks.
- Do not use rendered model bounds for collision.
- Do not force-load chunks.
- Do not weaken tests to obtain a pass.
- Do not describe unperformed manual testing as passed.

### Repository-first implementation

The known project baseline is Minecraft 1.21.1, NeoForge 21.1.72, Java 21, and the `britannia_mod` namespace. Codex must verify these facts from the repository.

Do not invent API names from memory. Inspect:

- Build files
- Existing registrations
- Existing block entities
- Existing data components or item-state codecs
- Existing block renderers
- Existing Blockbench or GeckoLib models
- Existing multiblock implementations
- Existing banner anchor-and-part work
- Existing Interior Decorator behavior
- Existing protection hooks
- Existing test infrastructure
- The actual resolved classpath

Repository facts override generic examples in these documents while preserving the specified behavior.

---

## 3. Required Project Documentation

Milestone 0 creates:

```text
docs/shrines-monoliths/PROJECT_FACTS.md
docs/shrines-monoliths/OPEN_QUESTIONS.md
docs/shrines-monoliths/IMPLEMENTATION_LOG.md
```

Every later milestone updates `IMPLEMENTATION_LOG.md`.

Update `PROJECT_FACTS.md` only with verified repository facts.

Update `OPEN_QUESTIONS.md` only for genuinely unresolved product or architecture decisions. Remove or resolve entries when evidence answers them.

Each implementation-log entry must include:

- Date
- Milestone
- Starting commit
- Ending commit
- Files changed
- Architecture decisions
- Exact commands
- Exact results
- Test counts
- Manual checks
- Unperformed checks
- Known limitations
- Deviations from the design
- Next milestone only

---

## 4. Standard Validation

Use the repository's native commands. On Windows, commands may resemble:

```text
.\gradlew.bat test --no-daemon --stacktrace
.\gradlew.bat clean --no-daemon
.\gradlew.bat build --no-daemon
```

On WSL or Linux, commands may resemble:

```text
./gradlew test --no-daemon --stacktrace
./gradlew clean --no-daemon
./gradlew build --no-daemon
```

The repository may document client launch as:

```text
./gradlew runClient -Pdev --no-configuration-cache
```

Codex must adapt to the actual environment and record the exact command used.

At minimum, each implementation milestone runs:

1. Narrow focused tests.
2. Relevant feature package tests.
3. `git diff --check`.
4. The full unit test suite when practical.
5. A clean build before final acceptance.

When a clean command would delete preserved generated or local user work, inspect first and use the repository's safe validation approach.

Do not claim a GameTest was run when no GameTest framework exists.

---

## 5. Standard Final Report

Every milestone response must use:

```markdown
# Milestone N Report

## Current state
- Active branch:
- Starting commit:
- Ending commit:
- Working tree before:
- Working tree after:
- Branch ancestry:

## Scope implemented
- ...

## Repository facts used
- File paths and conventions inspected

## Files changed
- ...

## Architecture and behavior
- ...

## Tests run
- Exact command
- Exact result
- Test totals
- Failures, errors, skips

## Manual checks
- Performed:
- Not performed:

## Acceptance criteria
- [PASS/FAIL] ...

## Known limitations
- ...

## Preserved work
- Unrelated paths left untouched and unstaged

## Commit
- Full hash:
- Message:

## Next action
- Milestone N+1 only. Do not begin it.
```

Unsupported claims must be marked unverified.

---

# Milestone 0 — Repository Discovery and Safe Project Initialization

## Objective

Create or safely enter the feature branch, inspect the repository, establish the baseline, and create project facts. Do not implement gameplay.

## Prerequisites

- The two root documents are present:
  - `UltimaCraft_Shrine_and_Monolith_System_Design.md`
  - `UltimaCraft_Shrine_and_Monolith_Codex_Playbook.md`
- The approved integration branch is available.
- Existing uncommitted work is identified and preserved.

## Required work

### Branch setup

1. Inspect local and remote branch state.
2. Confirm the approved current integration branch. The known target is `patch-18`.
3. If `shrines-monoliths` does not exist:
   - Safely update the approved integration branch using the repository's normal fast-forward workflow.
   - Create `shrines-monoliths` directly from it.
4. If it exists:
   - Inspect ancestry and divergence.
   - Do not reset or recreate it.
5. Record the exact base commit and merge base.

### Repository discovery

Inspect and record paths for:

- Minecraft version
- NeoForge version
- Java version
- Gradle and NeoGradle versions
- Mod ID
- Main Java source root
- Resource roots
- Generated resource roots
- Test roots
- Block registration
- Item registration
- Block-entity registration
- Data-component or item-state conventions
- Codec and NBT conventions
- Networking
- Client registration
- Block-entity rendering
- Blockbench or GeckoLib model loading
- Existing multiblock architecture
- Completed banner anchor-and-parts architecture, if present
- Interior Decorator item and interaction handling
- Administrator authorization predicate
- Region/protection hooks
- Drop and pick-block conventions
- Piston behavior conventions
- Chunk-load event handling
- Test framework and GameTest availability
- Current shrine blocks, items, models, textures, and localization
- Current monolith models, textures, names, and localization
- Current release and branch conventions

### Documentation

Create:

```text
docs/shrines-monoliths/PROJECT_FACTS.md
docs/shrines-monoliths/OPEN_QUESTIONS.md
docs/shrines-monoliths/IMPLEMENTATION_LOG.md
```

`PROJECT_FACTS.md` must explicitly record:

- Feature branch
- Base branch
- Base commit
- Integration path
- Mod-only scope
- Shrine footprint `2 × 1 × 2`
- Monolith footprint `3 × 3 × 2`
- Required monolith Y render offset `+16 voxels`
- Shrine variants share geometry and change texture only
- Monolith variants may change geometry
- Interior Decorator cycles same-family variants
- No cross-family conversion
- Collision must be cell-bounded and independent of model geometry

`OPEN_QUESTIONS.md` must not repeat questions already answered by the design.

### Baseline validation

Run the unmodified repository's normal test and build commands.

Record pre-existing warnings and failures without fixing unrelated problems.

## Explicit exclusions

Do not add:

- Blocks
- Items
- Block entities
- Definitions
- Placement code
- Renderer code
- Interior Decorator changes
- Assets
- Localization
- Gameplay tests for the new feature

## Acceptance criteria

- [ ] Active branch is `shrines-monoliths`.
- [ ] Branch base and merge base are recorded.
- [ ] Unrelated work is preserved.
- [ ] Both root documents were read.
- [ ] Existing multiblock and Interior Decorator systems were inspected.
- [ ] Existing shrine and monolith assets were inventoried.
- [ ] Project documentation exists.
- [ ] Baseline test/build results are recorded.
- [ ] No gameplay code was added.

## Suggested commit

```text
docs(structures): initialize shrine and monolith project
```

---

# Milestone 1 — Definition Catalogue, Footprints, and Transform Foundation

## Objective

Implement validated family and variant definitions plus pure placement mathematics. Do not register world blocks yet.

## Prerequisites

- Milestone 0 passed.
- Repository facts identify the appropriate content and validation patterns.
- Approved shrine and available monolith resources are inventoried.

## Required work

### Definitions

Implement the smallest repository-appropriate definition model for:

- Structure family
- Structure variant
- Dimensions
- Ordered footprint
- Cycle order
- Shared or per-variant model
- Texture
- Placement mode
- Collision profile
- Render origin
- Render offset in voxels
- Enabled/provisional status

Do not introduce a reloadable data-pack architecture unless the repository already uses one or the current feature clearly requires it.

### Initial families

Add validated family definitions:

```text
shrine:
  dimensions = 2 × 1 × 2
  occupied cells = 4
  shared geometry
  texture variants
```

```text
monolith:
  dimensions = 3 × 3 × 2
  occupied cells = 18
  model variants
  render offset = [0, +16, 0] voxels
```

Use approved stable IDs discovered in Milestone 0.

Unnamed monoliths use stable provisional IDs and provisional display labels.

### Transform service

Implement pure logic for:

- Local-to-world transform
- World-to-local or part-to-anchor reversal
- Four horizontal facings
- Ordered rectangular footprint generation
- Footprint validation
- Render-offset conversion from voxels to block units
- Cycle-order selection and wrap-around
- Family/variant compatibility

Adopt one documented facing convention for all future milestones.

### Validation

Reject:

- Duplicate IDs
- Duplicate cycle order
- Missing default variant
- Invalid dimensions
- Duplicate offsets
- Missing anchor offset
- Dimensions beyond planned part encoding
- Shrine variant with different geometry
- Monolith variant with incompatible footprint or render-offset contract
- Invalid resource namespace
- Missing approved/provisional status where required

## Tests

Add focused tests proving:

- Shrine produces exactly four unique offsets.
- Monolith produces exactly eighteen unique offsets.
- Four-facing transforms are correct.
- Reverse transforms recover the anchor.
- Shrine variants use one geometry.
- Shrine variants differ through textures.
- Monolith Y offset converts to exactly one block.
- Cycle order is deterministic and wraps.
- Invalid definitions fail with typed diagnostics.
- Group or display name does not determine footprint.
- Missing resources can be represented without corrupting definition identity.

## Explicit exclusions

Do not add:

- Registered blocks
- Block entities
- Placement items
- World mutation
- Renderer registration
- Interior Decorator behavior
- Drops
- Integrity repair

## Acceptance criteria

- [ ] Definitions follow repository conventions.
- [ ] Shrine and monolith contracts are represented.
- [ ] Approved IDs are preserved.
- [ ] Provisional monoliths remain explicit.
- [ ] One transform is used for all four facings.
- [ ] Footprint counts are exact.
- [ ] Positive sixteen-voxel Y offset is tested.
- [ ] No gameplay registration exists.
- [ ] Focused and regression tests pass.

## Suggested commit

```text
feat(structures): add shrine and monolith definitions
```

---

# Milestone 2 — Anchor, Parts, and Atomic Diagnostic Shrine Placement

## Objective

Prove the multi-block framework through one diagnostic shrine placement using the exact `2 × 1 × 2` footprint. Do not add final shrine rendering or variant cycling.

## Prerequisites

- Milestone 1 passed.
- Definition and transform tests are stable.
- Registration, item-state, protection, and block-entity conventions are documented.

## Required registered content

Register the minimum architecture:

- One large-structure anchor block
- One large-structure part block
- One anchor block-entity type
- One shrine family placement item or the repository-approved existing shrine item

Do not register normal direct-use block items for anchor or part blocks.

Do not register a monolith item yet unless registration separation would cause unnecessary later migration. If registered early, it must remain non-placeable and have tests proving that limitation.

## Part encoding

Represent:

- Horizontal facing
- Local X offset
- Local Y offset
- Local Z offset

Support the full future required ranges, but use only shrine offsets during this milestone.

Parts must have no block entity and no authoritative variant state unless repository evidence proves block-state encoding is unsafe.

## Diagnostic placement

Implement server-authoritative placement for one valid shrine definition:

```text
family item
-> validate
-> immutable four-cell plan
-> anchor
-> initialized block entity
-> three parts
-> final verification
-> synchronization
-> survival consumption
```

Use an intentionally diagnostic or invisible presentation. Do not implement final shrine model rendering.

### Validation

Before mutation, validate:

- Correct item
- Valid configured family/variant
- Horizontal floor placement
- Complete loaded footprint
- World border and height
- Replaceability
- Unrelated structure occupancy
- Project protection rules
- Block-entity creation
- Part offset encoding
- Rollback states

### Transaction

On failure:

- Restore exact prior states in reverse order.
- Suppress drops.
- Consume no item.
- Leave no block entity.
- Leave no parts.
- Emit no success effects.

### Collision foundation

Use a provisional collision profile whose shapes remain inside every occupied cell.

No shape may use model dimensions or coordinates outside `0..16`.

## Tests

- Registration counts
- No ordinary anchor/part block item
- Valid four-cell shrine plan
- Placement in four facings
- Survival consumes exactly one
- Creative consumes zero
- Every planned failure path mutates nothing
- Failure during each mutation step rolls back
- Only four cells change
- Correct anchor and part offsets
- Part reverse transform resolves anchor
- Shapes remain within local bounds
- Adjacent target cells remain unreserved
- No monolith placement yet
- No renderer or decorator cycling exists

## Manual check

When a safe item acquisition path exists:

- Place the diagnostic shrine.
- Confirm four cells are occupied.
- Place stairs immediately around it.
- Confirm no collision extends outside the four cells.

Do not add a command solely for this check.

## Explicit exclusions

Do not add:

- Final shrine textures
- Block-entity renderer
- Monolith placement
- Interior Decorator integration
- Cross-family conversion
- Crafting recipes
- Full lifecycle repair beyond placement rollback

## Acceptance criteria

- [ ] One anchor and three parts form one shrine.
- [ ] Placement is atomic.
- [ ] All shapes are cell-bounded.
- [ ] Adjacent stair placement is not blocked by model bounds.
- [ ] No part owns full state.
- [ ] No duplicate block item path exists.
- [ ] No future milestone rendering or cycling was added.
- [ ] Tests and clean build pass.

## Suggested commit

```text
feat(structures): add atomic multiblock shrine placement
```

---

# Milestone 3 — Shrine Persistence, Whole-Structure Lifecycle, and Integrity

## Objective

Make the diagnostic shrine safe across break, pick block, save/reload, chunk boundaries, external replacement, and integrity repair.

## Prerequisites

- Milestone 2 passed.
- Shrine placement is stable.
- Authoritative drop and chunk-event patterns are documented.

## Required work

### Anchor state

Persist:

- Schema version
- Family ID
- Variant ID
- Exact ordered placed footprint
- Facing validation as required by repository conventions

Implement:

- Disk save/load
- Update tag
- Update packet
- Client application
- Missing definition preservation
- Structurally invalid state handling

### Central lifecycle

One service owns:

- Anchor break
- Part break
- Survival drop
- Creative removal
- Explosion cleanup
- External replacement
- Placement rollback cleanup
- Orphan cleanup
- Missing-part repair
- Obstructed-repair failure

Add a reentrancy guard keyed by level and anchor.

### Drop behavior

Survival anchor or part break:

- Removes all four cells.
- Drops exactly one configured shrine item.
- Preserves variant ID.
- Produces no anchor or part block item.

Creative removal:

- Removes all four cells.
- Drops none.

Select and document one authoritative drop path.

### Integrity

- Never force-load chunks.
- Defer part orphan classification when the anchor chunk is unavailable.
- Use persisted offsets, not current definition dimensions.
- Repair missing parts only in loaded, replaceable cells.
- Never overwrite an obstruction.
- Preserve an unrelated replacement block.
- Remove definitive orphans without drops.

### Piston and fluid behavior

- Anchor and parts use blocked piston reaction.
- Prevent partial movement.
- Confirm fluid replacement cannot silently split the structure.

## Tests

- Save/load exact equality
- Update-tag equality
- Update-packet equality
- Missing family and variant IDs preserved
- Definition footprint changes do not resize placed shrine
- Survival anchor break gives one item
- Survival part break gives one item
- Creative breaks give none
- No duplicate loot path
- Pick anchor and part return equal configured item
- Explosion policy
- External replacement preserved
- Piston blocked
- Orphan cleanup
- Deferred unloaded-anchor classification
- Missing-part repair
- Obstruction is never overwritten
- Reentrancy prevents recursion and duplicate drops
- Cross-chunk reload ordering

## Explicit exclusions

Do not add:

- Final visual renderer
- Shrine texture cycling
- Interior Decorator changes
- Monolith content
- Recipes

## Acceptance criteria

- [ ] Shrine state survives save/reload.
- [ ] Complete footprint remains authoritative.
- [ ] Any cell controls the whole lifecycle.
- [ ] Survival produces exactly one configured item.
- [ ] Creative produces none.
- [ ] Missing content does not erase IDs.
- [ ] Chunk loading does not cause false orphan deletion.
- [ ] Obstructions are preserved.
- [ ] Pistons cannot split the structure.
- [ ] No final rendering or cycling exists.
- [ ] Tests and clean build pass.

## Suggested commit

```text
feat(structures): persist and protect multiblock shrines
```

---

# Milestone 4 — Shrine Rendering and Texture Variants

## Objective

Render the complete shrine once from the anchor using one shared geometry and approved texture variants.

## Prerequisites

- Milestone 3 passed.
- Approved shrine geometry and texture paths are inventoried.
- Existing renderer conventions are documented.
- Dedicated-server client isolation is understood.

## Required work

### Renderer

Implement one anchor block-entity renderer or repository-equivalent dynamic renderer.

Requirements:

- Anchor-only render.
- Parts render no geometry.
- Four horizontal facings.
- Shared shrine geometry.
- Texture selected from the shrine variant.
- Render bounds include the complete two-by-two footprint.
- No collision derived from rendered model.
- Safe missing-model and missing-texture fallback.
- No common-side client imports.

### Content

Register or manifest every approved shrine variant.

The intended content family includes:

- Honesty
- Compassion
- Valor
- Justice
- Sacrifice
- Honor
- Spirituality
- Humility
- Chaos

Use repository-approved IDs and files. Do not rename or redraw them.

### Packaging

Verify:

- Model resources package.
- Every approved texture packages.
- Localization packages.
- Part block has no visible duplicate model.
- Production JAR contains renderer classes only on the appropriate side.

## Tests

- All shrine variants resolve.
- All shrine variants reference the same geometry.
- Each variant resolves its own texture.
- Four facing rotations are correct.
- Render bounds cover the footprint.
- Missing model uses fallback without state mutation.
- Missing texture uses fallback without state mutation.
- Only anchor renders.
- Common code has no client imports.
- Dedicated-server startup passes.
- Existing structure persistence tests remain passing.

## Manual check

Review every shrine texture in game:

- Correct virtue/Chaos identity
- Correct facing
- No duplicate render
- No z-fighting
- No missing texture
- No clipping at the anchor cell
- No unexpected disappearance at normal camera angles
- Stairs still place around the perimeter

## Explicit exclusions

Do not add:

- Interior Decorator cycling
- Monolith placement or renderer
- New shrine art
- Cross-family conversion
- Recipes

## Acceptance criteria

- [ ] Shrine renders once from anchor.
- [ ] Geometry is shared.
- [ ] Variants change texture only.
- [ ] Complete render bounds are correct.
- [ ] Parts are invisible.
- [ ] Collision remains cell-bounded.
- [ ] Dedicated-server startup passes.
- [ ] All approved shrine assets package.
- [ ] Tests and clean build pass.

## Suggested commit

```text
feat(shrines): render approved texture variants
```

---

# Milestone 5 — Interior Decorator Shrine Cycling

## Objective

Allow an authorized administrator to right-click an anchor or part with the existing Interior Decorator tool and cycle shrine texture variants.

## Prerequisites

- Milestone 4 passed.
- Existing Interior Decorator interaction and administrator authorization are documented.
- Shrine cycle order is approved and deterministic.

## Required work

### Interaction

Extend the existing Interior Decorator tool or its established interaction service.

On server-authorized right-click:

1. Confirm held tool.
2. Confirm administrator permission.
3. Resolve anchor from anchor or part.
4. Confirm family is `shrine`.
5. Resolve the next enabled shrine variant.
6. Change only `variant_id`.
7. Mark changed and synchronize.
8. Play existing decorator feedback.
9. Show localized selected-variant feedback.

### Required invariants

Cycling must not change:

- Geometry
- Footprint
- Anchor
- Parts
- Facing
- Collision
- Render offset
- Item consumption

Cycling must not call the placement transaction.

### Failure cases

- Unauthorized player
- Wrong held item
- Invalid part
- Anchor chunk unavailable
- Missing current variant
- No alternate enabled variant
- Invalid cycle definition
- Synchronization failure

Every failure must leave state unchanged.

## Tests

- Authorized anchor target
- Authorized part target
- Unauthorized interaction
- Wrong tool
- Stable cycle order
- Last wraps to first
- Variant persists through save/reload
- Observer receives synchronized variant
- Geometry ID remains unchanged
- Footprint and part states remain unchanged
- Missing variant fails safely
- Existing Interior Decorator interactions for unrelated blocks still pass
- Client cannot authoritatively select an arbitrary variant

## Manual check

Cycle through every approved shrine variant from:

- Anchor
- Each of the three part cells
- At least two facings
- A two-client session when practical

Confirm one interaction advances exactly one variant.

## Explicit exclusions

Do not add:

- Reverse cycling
- Rotation
- Shrine-to-monolith conversion
- Monolith cycling
- New tool
- Recipes

## Acceptance criteria

- [ ] Existing Interior Decorator is reused.
- [ ] Administrator authorization is server-side.
- [ ] Any valid shrine cell resolves the anchor.
- [ ] Only texture variant state changes.
- [ ] Cycle order is stable.
- [ ] State persists and synchronizes.
- [ ] Unrelated decorator behavior remains intact.
- [ ] Tests and clean build pass.

## Suggested commit

```text
feat(shrines): cycle variants with interior decorator
```

---

# Milestone 6 — Atomic Monolith Placement and Positive-Y Render Offset

## Objective

Extend the proven framework to one diagnostic monolith occupying exactly `3 × 3 × 2`, and prove the positive sixteen-voxel Y visual correction.

## Prerequisites

- Milestone 5 passed.
- One approved or provisional diagnostic monolith model is available.
- Part encoding supports the complete offset range.
- Renderer can select the monolith family safely.

## Required work

### Item and placement

Add or activate the approved monolith family item.

Implement placement using the existing transaction:

```text
one anchor
+ seventeen parts
= eighteen occupied cells
```

Do not create a separate monolith-specific lifecycle engine.

### Footprint

Required dimensions:

```text
width  = 3
height = 3
depth  = 2
cells  = 18
```

Prove all offsets, facings, reverse transforms, cross-chunk validation, rollback, and lifecycle behavior at this size.

### Rendering

Render one diagnostic monolith model from the anchor.

Apply:

```text
render_offset_voxels = [0, 16, 0]
```

The correction must be visual only.

Confirm:

- Occupied cells are not shifted.
- Collision is not shifted.
- Selection is not shifted.
- Anchor remains at the planned world position.
- The visual base sits at the intended world horizon.
- Render bounds include the corrected model.

### Collision

Use the approved monolith collision profile, defaulting to cell-bounded solid cells unless repository/model evidence supports a reviewed custom profile.

No shape may extend outside its own cell.

## Tests

- Exactly eighteen offsets
- One anchor and seventeen parts
- Four facings
- Positive sixteen-voxel Y render offset
- Placement and rollback at each mutation boundary
- Survival and creative consumption
- Save/load
- Break any layer or part
- One configured monolith item
- Pick block from anchor and parts
- Cross-chunk placement requires loaded chunks
- Piston blocked
- Shapes remain local
- Adjacent stair cells remain available
- Render bounds include all three vertical layers and offset

## Manual check

- Place monolith in all four facings.
- Confirm it is not one block below the horizon.
- Confirm it is not one block above the horizon.
- Place stairs and full blocks against every perimeter side.
- Break cells on bottom, middle, and top layers.
- Save/reload.
- Test near a chunk boundary.

## Explicit exclusions

Do not add:

- Multiple monolith model variants
- Interior Decorator monolith cycling
- Cross-family conversion
- New monolith art
- Recipes

## Acceptance criteria

- [ ] Monolith occupies exactly eighteen cells.
- [ ] Existing framework is reused.
- [ ] Positive Y offset is exactly sixteen voxels.
- [ ] Offset affects rendering only.
- [ ] Horizon alignment is manually checked or marked unperformed.
- [ ] Collision remains cell-bounded.
- [ ] Whole-structure lifecycle works from every layer.
- [ ] Tests and clean build pass.

## Suggested commit

```text
feat(monoliths): add offset multiblock placement
```

---

# Milestone 7 — Monolith Model Variants and Interior Decorator Cycling

## Objective

Add all approved monolith model variants and cycle them with the Interior Decorator without replacing the eighteen structure cells.

## Prerequisites

- Milestone 6 passed.
- Monolith asset inventory is complete enough to identify approved and provisional variants.
- Every cycle candidate shares the placement contract.

## Required work

### Variant catalogue

For each supplied monolith:

- Stable ID
- Approved or provisional display label
- Model resource
- Texture resource
- Cycle order
- Enabled/provisional status
- `3 × 3 × 2` footprint compatibility
- Positive sixteen-voxel Y offset compatibility

Do not invent final names.

### Renderer

Select monolith geometry and texture from the current variant.

Variant mutation must not:

- Re-place anchor
- Re-place parts
- Change facing
- Change footprint
- Change collision
- Change render-offset contract

### Interior Decorator

Extend same-family cycling to `monolith`.

Right-clicking any valid anchor or part resolves the anchor and advances to the next enabled monolith variant.

The server selects the next variant from the validated ordered catalogue.

### Failure handling

Reject or exclude a variant when:

- Footprint differs
- Required render-offset contract differs
- Resource identity is invalid
- Variant is disabled
- Model is absent and the repository policy treats absence as disabled

A missing client resource for an otherwise valid saved variant uses the diagnostic fallback and preserves state.

## Tests

- Every approved/provisional monolith appears once.
- Cycle order is deterministic.
- Last wraps to first.
- Model and texture selection follow variant.
- Footprint remains identical.
- Parts remain unchanged.
- Render offset remains positive sixteen voxels.
- Save/reload preserves selected model.
- Observer synchronizes.
- Unauthorized interaction fails.
- Invalid client variant is rejected.
- Missing resource uses fallback without state mutation.
- Shrine cycling remains texture-only and unchanged.

## Manual check

Cycle through every monolith model from:

- Anchor
- Bottom part
- Middle part
- Top part
- At least two facings
- Two clients when practical

Inspect alignment, texture, culling, and duplicate rendering for every variant.

## Explicit exclusions

Do not add:

- Shrine-to-monolith conversion
- Variable footprints
- Runtime resizing
- Reverse cycle
- Rotation
- Asset redesign
- Recipes

## Acceptance criteria

- [ ] All supplied models are inventoried.
- [ ] Unknown names remain provisional.
- [ ] Every cycle candidate shares the footprint contract.
- [ ] Model changes happen through anchor state.
- [ ] No block replacement occurs.
- [ ] Render offset remains compatible.
- [ ] State persists and synchronizes.
- [ ] Shrine behavior remains unchanged.
- [ ] Tests and clean build pass.

## Suggested commit

```text
feat(monoliths): cycle model variants with decorator
```

---

# Milestone 8 — Collision, Adjacency, Content, and Regression Hardening

## Objective

Complete the exhaustive collision and neighbor-placement matrix, finalize approved family content, and harden regressions before live multiplayer validation.

## Prerequisites

- Milestone 7 passed.
- Final approved collision profile is known for shrine and monolith.
- Approved content inventory is available.

## Required work

### Shape audit

For anchor and every part offset:

- Selection shape
- Collision shape
- Occlusion shape
- Support/sturdy-face behavior
- Pathfinding behavior
- Replacement behavior

Prove every shape remains inside its own `0..16` local bounds.

No model, renderer, or render AABB may be consulted by placement collision.

### Adjacency matrix

Automate where practical and manually validate:

- Full perimeter blocks
- Stairs toward and away
- Inner corners
- Outer corners
- Top slabs
- Bottom slabs
- Walls
- Fences
- Face attachments where intended
- All four structure facings
- Shrine and monolith
- Neighbor placement before and after variant cycling
- Neighbor placement after save/reload

### Content validation

Produce a machine-readable or documented content report containing:

- Shrine variants
- Monolith variants
- Stable IDs
- Provisional status
- Model and texture paths
- Cycle order
- Footprint
- Render offset
- Validation result

Do not add final names or assets to eliminate warnings without approval.

### Regression

Re-run and inspect:

- Existing Interior Decorator tests
- Existing banner multiblock tests
- Existing block registration tests
- Dedicated-server startup
- Client startup
- Existing unrelated feature suites
- JAR content

### Diagnostics

Ensure logs are bounded and useful for:

- Missing definition
- Missing model
- Missing texture
- Invalid footprint
- Orphan part
- Repair obstruction
- Duplicate lifecycle callback
- Failed rollback
- Unauthorized decorator use where logging is appropriate

## Tests

- Complete shape-bound tests
- Complete adjacency tests possible in the test framework
- Content catalogue validation
- No duplicate IDs or cycle order
- No ordinary part/anchor items
- No per-part renderer
- No per-part block entity unless explicitly approved
- No client imports in common classes
- Full feature and regression test suites
- Production JAR inspection

## Manual check

Perform the full stair-ring matrix for representative and edge variants.

A visual model extending across cells is allowed. A collision shape extending across cells is not.

## Explicit exclusions

Do not add:

- New gameplay systems
- Recipes
- NPCs
- Rails
- Cross-family conversion
- Performance rewrites without evidence
- Art changes

## Acceptance criteria

- [ ] Every shape is cell-bounded.
- [ ] Complete stair adjacency matrix passes.
- [ ] Neighbor blocks never mutate structure state.
- [ ] Content report is complete and honest.
- [ ] Missing resources remain non-destructive.
- [ ] Existing systems remain passing.
- [ ] Dedicated server and production JAR checks pass.
- [ ] Tests and clean build pass.

## Suggested commit

```text
test(structures): harden collision and adjacency behavior
```

---

# Milestone 9 — Live Multiplayer, Reload, Chunk, and Performance Validation

## Objective

Validate the built feature in an exact-JAR dedicated-server environment with two authenticated clients. This milestone should prefer validation and narrowly scoped fixes over new architecture.

## Prerequisites

- Milestone 8 passed.
- A clean production JAR is available.
- Exact runtime versions and hashes are recorded.
- A disposable server and client topology can be created safely.

## Required environment

Use:

- One clean NeoForge dedicated server
- Two authenticated NeoForge clients
- The exact same built Britannia JAR
- Separate game directories
- No duplicate Britannia JARs
- Online mode for the authoritative run unless the project's established test procedure specifies otherwise

Record:

- Minecraft version
- NeoForge version
- Java version
- Mod version
- JAR file name
- SHA-256 hash copied to server and clients
- Server directory
- Client directories
- World seed or disposable-world identity
- Relevant log paths

Do not commit server worlds or logs.

## Validation matrix

### Placement and observation

For each family:

- Client A places while Client B observes.
- Verify identical facing, variant, geometry, texture, footprint, and offset.
- Reverse roles.
- Disconnect and reconnect the observer.
- Place and cycle additional structures while the observer is offline.
- Confirm correct late-tracking state on reconnect.

### Interior Decorator

- Authorized cycling from anchor and parts.
- Unauthorized attempt.
- Shrine texture cycle.
- Monolith model cycle.
- Wrap-around.
- Save/reload.
- Server restart.
- Client resource reload where safe.

### Lifecycle

- Break anchor in survival.
- Break part in survival.
- Break top monolith part.
- Creative removal.
- Pick block.
- Re-place recovered item.
- Explosion policy.
- External replacement if safely reproducible.
- Piston attempt.
- Missing part recovery.
- Obstruction during recovery.
- Chunk unload and reload.
- Cross-chunk placement.

### Collision

- Full stair ring.
- Inner and outer corners.
- Slabs, walls, fences, and full blocks.
- Perform before and after cycling.
- Perform after server reload.

### Horizon alignment

For every monolith model:

- Bottom visually rests on intended world horizon.
- No one-block vertical error.
- No clipping below ground.
- No unexpected floating.
- All four facings.
- Chunk-boundary camera movement.
- Normal and distant camera views.

### Logs

Search server and both client logs for relevant terms such as:

```text
shrine
monolith
large_structure
anchor
part
orphan
repair
rollback
duplicate
renderer
model
texture
offset
decorator
sync
payload
exception
disconnect
missing
invalid
```

Report only relevant errors, warnings, bounded expected diagnostics, repeated diagnostics, stack traces, and disconnect reasons.

### Performance smoke

Create a dense but reasonable scene containing multiple shrines and monoliths.

Record:

- Server responsiveness
- Placement latency
- Variant-cycle latency
- Client frame behavior
- Chunk reload behavior
- Log volume
- Obvious render duplication
- Obvious memory or tick degradation

Do not claim formal benchmarking unless measurements were actually collected.

## Fix boundary

If validation exposes a defect:

- Reproduce it.
- Identify whether it is feature-introduced, pre-existing, environmental, content-related, render-related, lifecycle-related, or network-related.
- Make the smallest fix.
- Add regression coverage.
- Re-run the relevant focused and full checks.
- Record the correction in the implementation log.

Do not redesign the system during this milestone without explicit approval.

## Acceptance criteria

- [ ] Exact JAR identity is proven.
- [ ] Dedicated server starts cleanly.
- [ ] Two clients observe identical structures.
- [ ] Late tracking and reconnect work.
- [ ] Save/reload and server restart work.
- [ ] Shrine cycling synchronizes.
- [ ] Monolith cycling synchronizes.
- [ ] Stair and neighbor matrix passes live.
- [ ] Whole-structure break and recovery work.
- [ ] No duplicate drops.
- [ ] Cross-chunk behavior is safe.
- [ ] Every monolith's horizon alignment is reviewed.
- [ ] Relevant logs are reviewed.
- [ ] Performance smoke shows no material regression or limitations are documented.
- [ ] Unperformed checks are clearly marked.
- [ ] Any correction has automated regression coverage.
- [ ] Clean build still passes.

## Suggested commit

When validation requires no source change, create no empty commit.

When narrowly scoped corrections are required:

```text
fix(structures): resolve live validation defects
```

---

# Milestone 10 — Final Audit and Merge-Readiness Package

## Objective

Perform a documentation, branch, saved-state, security, packaging, and scope audit. Prepare the feature for review into `patch-18`. Do not merge it.

## Prerequisites

- Milestone 9 passed or has an explicitly approved limitation list.
- Working tree and commit history are available.
- All milestone reports are present.

## Required audit

### Git and scope

- Active branch is `shrines-monoliths`.
- Clean or explicitly understood working tree.
- Correct merge base with approved `patch-18`.
- Complete commit list.
- No unrelated files.
- No root specifications accidentally omitted.
- No destructive history operations.
- No direct work on integration/live branches.

### Architecture

Confirm:

- One anchor block
- One part block
- One anchor block entity
- No ordinary anchor/part block items
- One logical object per placement
- Parts carry no complete family/variant state
- Anchor-only rendering
- Cell-bounded collision
- Persisted authoritative footprint
- Positive sixteen-voxel monolith Y render offset
- Same-family variant cycling
- No cross-family conversion
- Server authority
- No chunk force-loading
- Piston blocking
- Central lifecycle and reentrancy guard

### Saved-state review

Review:

- Schema version
- Family and variant IDs
- Footprint persistence
- Facing
- Missing definitions
- Definition changes
- Malformed data
- Item reconstruction
- Pick block
- Update tag and packet
- Migration or alias behavior for any existing shrine IDs

### Security

Confirm:

- Administrator check is server-side.
- Client cannot choose arbitrary resources or footprint.
- Client cannot bypass variant registry.
- Client sync is display-only.
- No duplication through break, explosion, replacement, or rollback.
- No protected-region bypass.

### Assets and packaging

Confirm:

- Approved shrine textures
- Shared shrine geometry
- Every monolith model
- Every texture
- Localization
- Blockstate/model resources
- Renderer classes
- No visible part model duplication
- No missing resources in production JAR
- Dedicated-server client isolation

### Test evidence

Require exact evidence for:

- Focused tests
- Full tests
- Clean build
- JAR inspection
- Dedicated server
- Two-client run
- Reload
- Chunk boundary
- Stair adjacency
- Horizon alignment
- Variant cycling
- Duplicate-drop prevention
- Performance smoke

### Documentation

Finalize:

```text
docs/shrines-monoliths/PROJECT_FACTS.md
docs/shrines-monoliths/OPEN_QUESTIONS.md
docs/shrines-monoliths/IMPLEMENTATION_LOG.md
docs/shrines-monoliths/MERGE_READINESS.md
docs/shrines-monoliths/ROLLBACK_PLAN.md
docs/shrines-monoliths/POST_MERGE_VALIDATION.md
```

`OPEN_QUESTIONS.md` must contain no unresolved critical item.

### Rollback plan

The rollback plan must identify:

- Feature commits
- Registered IDs
- Saved-state implications
- Whether reverting code would leave placed structures in worlds
- Safe operational sequence
- Backup expectations
- How to restore the prior JAR
- How to validate world integrity after rollback
- Why world deletion is not an acceptable default rollback

### Merge readiness

Prepare, but do not execute:

```text
shrines-monoliths -> patch-18
```

State the recommended merge or pull-request method according to repository conventions.

Do not update or merge into `patch-18` without explicit user approval.

## Final acceptance checklist

- [ ] All design acceptance criteria are mapped to evidence.
- [ ] Branch ancestry is correct.
- [ ] Working tree is safe.
- [ ] Full commit list is reviewed.
- [ ] Saved-state behavior is reviewed.
- [ ] Administrator authority is reviewed.
- [ ] Collision and stair compatibility are proven.
- [ ] Monolith offset is proven.
- [ ] Content inventory is complete.
- [ ] Production JAR is inspected.
- [ ] Dedicated server and multiplayer evidence exists.
- [ ] No critical open question remains.
- [ ] Rollback plan exists.
- [ ] Post-merge validation exists.
- [ ] No merge was performed.
- [ ] No live promotion was performed.

## Suggested commit

```text
docs(structures): complete merge-readiness audit
```

---

## 6. Review Checklist for Every Codex Output

When reviewing a milestone response, reject or correct it when any of these are true:

- Work occurred on the wrong branch.
- Codex guessed APIs without repository evidence.
- A shape extends outside `0..16` in a local block cell.
- The Blockbench model is used as collision.
- A shrine uses one oversized collision block instead of four occupied cells.
- A monolith uses one oversized collision block instead of eighteen occupied cells.
- The positive Y offset moves placement or collision.
- Parts contain full family or variant state.
- Parts render duplicate models.
- Parts can independently drop items.
- Placement consumes an item before complete success.
- Rollback creates drops or leaves parts.
- Unloaded chunks are force-loaded.
- Part cleanup runs before anchor availability is known.
- Current definitions resize saved structures.
- Shrine cycling replaces geometry.
- Monolith cycling replaces world blocks.
- Interior Decorator permission is client-authoritative.
- The client submits arbitrary resource paths or variants.
- A shrine is converted to a monolith.
- Cross-family cycling is added.
- Monoliths with different footprints appear in one cycle group.
- Missing content silently substitutes another virtue or model.
- Approved art is modified without instruction.
- Provisional monolith names are presented as final.
- Existing Interior Decorator behavior regresses.
- Tests are claimed without commands or results.
- Manual validation is claimed but not documented.
- Unrelated files are refactored.
- The feature branch is merged without explicit approval.

---

## 7. Completion Definition

The project is complete when:

```text
2 × 1 × 2 shrine
    + texture-only shrine variants
    + 3 × 3 × 2 monolith
    + model-based monolith variants
    + +16-voxel visual Y correction
    + cell-bounded collision
    + full stair adjacency
    + administrator Interior Decorator cycling
    + persistence
    + whole-structure lifecycle
    + reload/chunk safety
    + two-client validation
    + merge-readiness documentation
```

are all supported by evidence.

Codex must stop after the active milestone and return its report. The user or reviewer decides whether the next milestone begins.
