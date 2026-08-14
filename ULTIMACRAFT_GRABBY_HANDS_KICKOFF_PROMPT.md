# Claude Code Kickoff Prompt — UltimaCraft Grabby Hands

You are beginning a new UltimaCraft Minecraft mod epic called **Grabby Hands**.

Your first responsibility is to understand the existing repository deeply enough that this system extends current behavior instead of replacing or duplicating it.

Read these project-root documents first if present:

- `ULTIMACRAFT_GRABBY_HANDS_DESIGN.md`
- `ULTIMACRAFT_GRABBY_HANDS_PLAYBOOK.md`

Treat them as the owner-approved product/design intent, but verify all implementation assumptions against the live source tree.

For this invocation, perform **Milestone 0 only**. Do not begin feature implementation until the repository reconnaissance is complete and reported.

---

## 1. Product intent

UltimaCraft intentionally uses Adventure mode as a protection boundary. We need a narrow Ultima Online-style object manipulation system, not generic Minecraft building permission.

Players should eventually be able to:

- place approved furniture in Adventure mode;
- pick approved furniture back up into normal inventory;
- place and pick up approved containers without losing contents/state;
- place stateful wine bottles and other practical items into the world as physical/decorative objects;
- keep using those objects normally after placement;
- let other players use placed objects normally as well;
- sit on existing furniture after it has been moved;
- preserve existing supported furniture stacking;
- destroy eligible furniture, containers, and wine bottles with any recognized axe;
- hear clear audio feedback for pickup/stowing and axe destruction;
- leave practical objects in the world in an Ultima-like “littering” sense;
- keep permanent map/admin/world decoration protected unless explicitly enrolled as a movable instance.

The core architectural rule is:

> **Generic transport, specialized behavior.**

Grabby Hands should own safe world <-> inventory movement. It must not replace what the object actually does.

A chair remains a chair. A container remains a container. A wine bottle remains the exact logical wine bottle it was before movement.

---

## 2. Newly clarified hard requirements

These are explicit owner requirements and must influence the architecture from the beginning.

### A. Placed objects remain usable by all players

Grabby Hands must not create an owner-only use permission merely because one player placed or moved an object.

Examples:

- Player A places a chair; Player B must still be able to sit on it.
- Player A places an ordinary usable practical object; Player B must still be able to use it according to its existing behavior.
- A container continues to use its existing lock/security/house access behavior if it has one, but Grabby Hands must not invent an additional “placer-only” normal interaction restriction.

Mobility/provenance and normal object usability are separate concerns.

### B. Existing furniture sitting must survive

Find the actual furniture seating implementation and preserve it.

Do not create a Grabby Hands-specific seating system.

A chair moved through:

`world -> inventory -> world`

must still use the same seat logic, facing, hitbox, and interaction behavior as before.

### C. Existing supported furniture stacking must survive

The owner explicitly expects similar/compatible furniture blocks to remain stackable where the existing project already supports that behavior.

Do not impose a generic “ground only” placement rule that breaks existing stacking.

You must inspect the current collision/support/placement implementation to determine what “stackable” actually means in this project.

### D. Pickup has two sound effects

Successful pickup into inventory needs two distinct audio moments:

1. **Grab/lift:** the physical act of taking the object from the world.
2. **Stow/inventory:** confirmation that the object has successfully entered inventory.

The stow sound must not play if inventory insertion fails.

Inspect existing vanilla/project sound events before creating new sound resources.

### E. Axe destruction has explicit sound feedback

Destroying an eligible object with an axe needs an explicit destruction sound.

Prefer material-appropriate existing sounds where possible. A wooden chair and glass wine bottle should not necessarily sound identical.

### F. Wine bottle is the mandatory complex-state proof case

The current UltimaCraft wine bottle is believed to contain significant gameplay data and is a major reason this feature is useful.

Do not treat it as a simple cosmetic block.

You must fully analyze the existing wine system during Milestone 0 before recommending the portable-state format.

The eventual invariant is:

`world wine bottle -> pickup -> inventory ItemStack -> place -> world wine bottle`

All meaningful state must survive exactly, including repeated round trips and persistence.

Do not regenerate a default replacement bottle from registry ID if that would discard data.

---

## 3. Critical safety invariants

These are non-negotiable throughout the epic:

1. Do not switch the player's game mode to bypass Adventure restrictions.
2. Do not globally enable `mayBuild` or equivalent broad building permissions.
3. Do not globally uncancel Adventure-mode placement or breaking.
4. Only explicitly enrolled object types participate.
5. Type eligibility and exact world-instance mobility are separate concepts.
6. Existing static/admin/world decoration is immovable by default.
7. Placement provenance must not automatically become an interaction ACL.
8. Existing object behavior remains authoritative for sitting, opening, drinking/using, animation, etc.
9. Existing supported furniture stacking must not be broken by Grabby Hands placement validation.
10. All mutations must be server-authoritative.
11. Pickup must be atomic and duplication-safe.
12. Failed inventory insertion must leave the world object intact.
13. Successful pickup must have separate grab/lift and stow/inventory sound stages.
14. Container pickup must not also trigger a normal self-dropping loot path.
15. Axe destruction consumes the eligible placed object rather than returning an intact duplicate.
16. Destroyed container contents must spill into the world exactly once.
17. Any UltimaCraft-recognized axe must work; do not hard-code vanilla axes only.
18. Multi-block furniture must resolve one canonical root and mutate atomically.
19. Do not create a second container system, seating system, housing authority, or wine system.
20. Do not add continuously ticking scans of all placed litter.
21. Do not silently despawn persistent placed objects as the initial performance solution.
22. Do not create a Rails endpoint merely because this is new state. First determine the project's real authority boundary.
23. Do not overwrite unrelated local changes.
24. Do not push, merge, tag, release, or deploy unless explicitly authorized.

---

## 4. RunUO research baseline

Use RunUO for design context and original-UO behavior inspiration.

Repository:

`https://github.com/runuo/runuo`

Use the pinned reference commit where possible:

`71b2794f12eb6f948b1c5598ae8b350401a22d4d`

Read at minimum:

- `Server/Item.cs`
- `Server/Mobile.cs`
- `Server/Items/Container.cs`
- `Scripts/Items/Containers/FurnitureContainer.cs`
- `Scripts/Items/Misc/InteriorDecorator.cs`
- `Scripts/Engines/Harvest/Core/HarvestSystem.cs`
- `Scripts/Engines/Harvest/Core/HarvestTarget.cs`
- `Scripts/Items/Food/Beverage.cs`
- `Data/items.cfg`
- `Data/containers.cfg`
- relevant `Data/Decoration/*` entries as contextual evidence

Verify the following concepts directly rather than accepting this summary blindly:

- RunUO items have an explicit movable/non-movable concept.
- Lifting validates whether an item can be moved.
- Dropping into the world is a first-class item transition.
- Furniture is metadata/classification around normal item/container behavior.
- Furniture can have orientation/flip behavior.
- Interior decoration permissions are separate from the object's normal function.
- RunUO lumberjacking/axe targeting includes a furniture-destruction path.
- Immovable world furniture is protected from that destruction path.
- Container destruction moves/spills contained items into the world.
- Beverage bottles are stateful items, not merely decorative world props.

We want the architectural spirit of those systems, not a literal C# port.

---

## 5. Expected architecture to investigate, not blindly implement

The final solution will probably need responsibilities equivalent to:

- one server-authoritative Grabby Hands transaction service;
- data/tag-driven eligibility;
- exact-instance mobility/provenance metadata;
- a permission policy that delegates to existing house/region systems;
- a canonical-root resolver for multi-block furniture;
- native furniture adapter(s);
- native container adapter(s);
- a specialized-state practical-item adapter;
- a generic placed-item host only if the current project needs one;
- a shared axe classifier;
- pickup/destruction sound-role resolution;
- versioned portable ItemStack/block-entity state using existing project patterns.

Do not create these exact class names unless they fit the repository's existing conventions.

If the live project already has an abstraction that solves one of these responsibilities, reuse it.

---

## 6. Your task for this invocation: execute Milestone 0 only

Do not implement Grabby Hands behavior yet.

Perform a thorough repository analysis and produce a concrete reconnaissance report.

### A. Establish Git/worktree facts

Run and report the equivalent of:

- `git status --short --branch`
- current branch
- current HEAD SHA
- remotes
- local branches
- `git worktree list`
- divergence from the likely integration/base branch

Do not assume the base branch name.

If there is already a safe dedicated `grabby-hands` worktree/branch, inspect and use it.

If one does not exist and project conventions make creating one clearly safe, create a dedicated `grabby-hands` branch/worktree from the actual integration base.

If the Git state is ambiguous or contains unrelated work that makes mutation unsafe, continue analysis read-only and report the blocker. Never discard work.

### B. Confirm dependency/runtime facts

Read the actual build files and report:

- Minecraft version;
- NeoForge version;
- Java version;
- GeckoLib version if present;
- mappings/version conventions;
- mod ID/namespace;
- main package roots;
- baseline compile/test/GameTest commands;
- client/server run tasks.

Run the baseline automated validation that is safe and appropriate before feature changes.

### C. Map exact project architecture

Find exact file paths, classes, and important methods for:

- item registration;
- block registration;
- Creative Tab population;
- block/item tags and datagen;
- block entities;
- client renderer registration;
- custom item/block renderers;
- player interaction event handling;
- Adventure-mode-specific restrictions/overrides;
- custom tool classification;
- container/menu infrastructure;
- persistent world/per-position state;
- house/HouseLot ownership and permission checks;
- region/city protection;
- multi-block/root-part placement patterns;
- sitting/seating behavior;
- networking/payloads;
- sounds;
- logging/telemetry;
- Rails calls affecting blocks/entities/items;
- GameTests/unit/integration tests;
- multiplayer validation infrastructure.

For each important finding, provide source path plus class/method names. Avoid vague prose when source locations exist.

---

## 7. Build the furniture/practical-item candidate matrix

Search registrations and resources, not only filenames.

At minimum search for:

`chair`
`stool`
`bench`
`throne`
`table`
`desk`
`cabinet`
`armoire`
`drawer`
`bookcase`
`shelf`
`crate`
`chest`
`barrel`
`box`
`container`
`bottle`
`wine`
`ale`
`mug`
`cup`
`plate`
`candle`
`lantern`
`basket`
`furniture`

For each relevant candidate record:

- registry ID;
- Java item class/base class;
- block class/base class;
- BlockItem?;
- BlockEntity?;
- container?;
- sittable?;
- how sitting works;
- directional?;
- multi-block?;
- current collision/voxel shape;
- known supported stacking behavior;
- model(s);
- texture(s);
- renderer if custom;
- loot behavior;
- existing tags;
- Creative Tab status;
- evidence it is used as static/admin/world decoration;
- likely adapter: native furniture / native container / multi-block / generic item-only / specialized practical item / excluded;
- initial recommendation: enroll now / later / do not enroll.

Pay attention to assets added in recent UltimaCraft work. Do not create placeholders for something that already exists under another name.

---

## 8. Mandatory sitting audit

Find the exact implementation that lets players sit on furniture today.

Answer:

- Which furniture classes use it?
- What event/method triggers sitting?
- Is the seat represented by an entity, attachment, passenger relationship, block state, or another mechanism?
- How is seat position calculated?
- How does block facing affect seat position?
- Does the system allow multiple player interactions?
- Which part of a multi-block chair/bench owns the seat?
- What would break if Grabby Hands directly used `setBlock` instead of the object's native placement path?
- What tests already cover sitting?

Do not propose a second seating implementation unless the existing code is genuinely unusable, and if so, explain why with source evidence.

---

## 9. Mandatory furniture stacking audit

The owner expects existing similar furniture blocks to remain stackable where currently supported.

Find concrete evidence of how this works.

Answer:

- Which furniture types can currently be placed on top of which other furniture types?
- Is this governed by vanilla support checks, custom placement methods, voxel shape, sturdy-face logic, or another mechanism?
- Are there existing tests/examples?
- Does stacking work only in Creative/Survival today because Adventure blocks normal placement?
- Which placement helper/path must Grabby Hands preserve to retain stacking?
- Are there collision edge cases or multi-block constraints?

Do not generalize unsupported stacking. The requirement is to preserve the existing valid capability.

---

## 10. Mandatory wine-bottle deep audit

This is one of the most important outputs of Milestone 0.

Find every relevant part of the current wine system, including item models, data, recipes/economy/vendor integration, persistence, and any Rails authority.

Answer explicitly:

### Identity/registration

- What is/are the wine bottle registry ID(s)?
- What Java class represents them?
- Is wine a single item with data variants or multiple registered items?
- Is it a BlockItem?
- Is there already an associated block or block entity?

### Rich state

List every meaningful property actually discovered in code, including where it is stored.

Examples only, not assumptions:

- beverage/content type;
- quantity;
- vintage;
- age;
- quality;
- origin;
- maker;
- label;
- custom name;
- ownership/provenance;
- economy value;
- data components;
- custom NBT;
- capabilities/attachments;
- Rails IDs/state.

Do not report a field unless source evidence supports it.

### Behavior

- What happens on use/right-click?
- Can another player use it?
- Is it consumable/refillable?
- Does its state mutate through gameplay?
- Is it stackable? Under what equality rules?
- How is it copied/serialized?

### Rendering

- Which item model and texture are used?
- Is there dynamic/custom rendering?
- Is the existing model suitable for a small upright world bottle?
- Could a generic placed-item renderer render the actual ItemStack directly?
- If not, what minimal adapter/asset work is needed?

### Persistence risk

- Would recreating the item from registry ID lose data?
- Which exact data must be transported opaquely?
- Is an ItemStack itself already the safest canonical portable representation?
- Is any state only stored elsewhere and therefore needs special handling?

### Recommendation

Based on actual source, recommend the safest eventual adapter strategy for wine.

Do not implement it in Milestone 0.

---

## 11. Mandatory container deep audit

Identify the exact container stack used by existing crates/chests/furniture containers.

Answer:

- base block class;
- base block entity;
- menu/provider classes;
- item handler/capability;
- save/load methods;
- custom-name handling;
- orientation/state properties;
- loot/drop behavior;
- how contents are released on ordinary break;
- whether the container can already serialize contents into an ItemStack;
- whether a portable/shulker-like component already exists;
- current viewer synchronization;
- what happens if a block is removed while open;
- anti-nesting/capacity/weight rules;
- lock/security/house restrictions;
- whether any container is furniture/sittable/multi-block.

Identify the exact duplication hazards Grabby Hands would face if it naïvely removed the block and then gave the player a serialized container item.

---

## 12. Mandatory axe audit

Find all ways the project recognizes an axe.

Answer:

- What vanilla tag/class/interface is available under the project's current mappings?
- What custom axe base classes exist?
- Does `TwoHandedAxeItem` participate in a shared axe tag?
- Are there other custom axe families?
- How is durability consumed?
- Are chopping/break sounds already centralized?
- Is there an existing “axe target” interaction path that Grabby Hands should reuse?

The final implementation must not accidentally support vanilla axes while excluding UltimaCraft axes.

---

## 13. Mandatory sound audit

Find the project's sound registration and current usable events.

Create a small sound-role recommendation table for:

- pickup grab/lift;
- pickup inventory stow;
- normal placement;
- wooden furniture axe destruction;
- glass/wine-bottle axe destruction;
- other likely material categories if already supported.

Prefer existing vanilla/project sounds when they fit.

Identify whether any new `.ogg` assets or sound registrations are truly necessary.

Do not add sound assets in Milestone 0.

---

## 14. Adventure-mode interaction audit

Determine exactly why these objects cannot currently be placed/picked up in Adventure mode.

Trace the relevant interaction path through current NeoForge/Minecraft code and project event handlers.

Answer:

- Which existing project code intervenes in right-click/use/place/break?
- What native Adventure restriction blocks placement?
- Is there already a pattern for allowing a narrow special action in Adventure mode?
- What is the smallest server-authoritative hook that could permit only enrolled Grabby Hands placement?
- How can normal right-click remain reserved for use while a deliberate gesture performs pickup?
- How can axe destruction be implemented without globally granting block breaking?

Do not implement the bypass in Milestone 0.

---

## 15. Static-world decoration audit

The same asset type may appear as both permanent map decoration and player-placeable furniture.

Find how world decoration is currently introduced:

- structure generation;
- map import;
- admin tools;
- commands;
- decorator blocks/items;
- saved structures;
- other systems.

Recommend how a placed instance can safely be classified as:

- static/immovable;
- player/dynamic movable;
- existing explicitly protected/secure state.

Do not rely on block type alone.

---

## 16. Multi-block audit

Find every existing multi-block furniture pattern likely to participate.

Answer:

- where root state lives;
- how secondary parts point to root;
- how placement validates footprint;
- how breaking one part affects others;
- how items drop;
- how sitting/use resolves;
- whether pickup from a secondary part can safely redirect to root;
- what duplication/orphan risks exist.

---

## 17. Rails authority audit

Determine whether any relevant object state is currently Rails-authoritative.

Inspect patterns for:

- houses;
- containers/bank systems if relevant;
- wine/economy if relevant;
- item ownership if any;
- block placement if any.

The default design preference is local Minecraft persistence for placed practical objects, but existing authority wins.

Do not create new API endpoints in Milestone 0.

---

## 18. Baseline validation

Run the repository's appropriate pre-change validation after discovering the correct commands.

At minimum report:

- compile/build result;
- unit/integration test result;
- relevant GameTest result;
- known pre-existing failures if any;
- whether the working tree changed as a side effect.

Do not “fix” unrelated baseline failures during this milestone.

---

## 19. Required Milestone 0 report format

Return a structured report with these sections:

1. **Milestone verdict** — `PASS`, `PASS WITH DOCUMENTED RISKS`, or `BLOCKED`.
2. **Git/worktree facts** — branch, HEAD, cleanliness, base, divergence.
3. **Runtime/build facts** — exact dependency versions and commands.
4. **Architecture map** — file paths/classes/methods.
5. **Furniture candidate matrix**.
6. **Sitting architecture findings**.
7. **Furniture stacking findings**.
8. **Wine bottle deep audit** — include actual discovered data fields.
9. **Container deep audit**.
10. **Axe classification findings**.
11. **Sound inventory/recommendations**.
12. **Adventure-mode restriction findings**.
13. **Static-vs-movable instance strategy recommendation**.
14. **Multi-block findings**.
15. **Rails authority findings**.
16. **Recommended adapter architecture** — corrected to fit actual code.
17. **Primary duplication/state-loss risks**.
18. **Baseline validation results**.
19. **Proposed Milestone 1 scope** — no implementation yet.
20. **Open blockers only** — do not manufacture questions when source already answers them.

Be specific. Include source paths and method/class names wherever possible.

---

## 20. Milestone 0 completion gate

Do not call Milestone 0 complete unless all of these are known from source/evidence:

- how Adventure-mode placement is currently restricted;
- how sitting works;
- how existing supported furniture stacking works;
- how furniture is registered/rendered;
- how exact-instance static vs movable state can be represented using project conventions;
- how containers save/load and drop contents;
- how custom and vanilla axes can be classified together;
- which sounds can satisfy grab, stow, wood-destroy, and bottle-destroy roles;
- every meaningful wine-bottle data property relevant to a world/inventory round trip;
- whether wine can safely use an ItemStack as the canonical portable payload;
- how multi-block furniture resolves roots;
- what Rails authority, if any, applies;
- baseline tests/build status.

The highest-risk finding is the wine bottle. Do not finalize the generic portable-state design before understanding its actual data model.

---

## 21. Important implementation guidance for later milestones

Do not execute these yet, but keep them in mind during M0 analysis.

### Pickup audio lifecycle

The eventual interaction must distinguish:

`object successfully grabbed` -> grab/lift sound

from:

`portable item successfully committed to inventory` -> stow/inventory sound

A failed storage attempt must not lie to the player with a success sound or delete the world object.

### Public usability

A placer/owner value used for mobility policy must never automatically gate normal use. Validate this explicitly for chairs, wine, and containers.

### Furniture stacking

Preserve the native placement/support path wherever possible. Do not write a simplistic special placement routine that places every item at `clickedPos.above()` and thereby breaks furniture-on-furniture behavior.

### Wine

Prefer opaque preservation of the specialized item's canonical state over manually copying a central list of wine fields. The more Grabby Hands understands wine internals, the easier it is for future wine features to become data-loss bugs.

### Destruction

Treat axe destruction as a deliberate gameplay transaction, not ordinary unrestricted Minecraft block breaking.

---

## 22. Final instruction for this invocation

Perform the analysis now.

Do not begin Milestone 1 implementation.

Do not make speculative changes just to prove a concept.

Your objective is to return enough concrete repository evidence that the owner can approve the architecture with confidence, especially around:

- Adventure-mode interaction;
- sitting;
- furniture stacking;
- public usability;
- two-stage pickup sounds;
- axe destruction sounds;
- wine-bottle state preservation;
- containers;
- static decoration protection;
- multiplayer/anti-duplication boundaries.
