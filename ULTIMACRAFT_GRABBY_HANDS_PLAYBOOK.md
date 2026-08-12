# UltimaCraft — Grabby Hands Milestone Playbook

**Project:** UltimaCraft Grabby Hands  
**Target:** Minecraft 1.21.1 / NeoForge 21.1.x / Java 21  
**Primary game mode:** Adventure  
**Companion design:** `ULTIMACRAFT_GRABBY_HANDS_DESIGN.md`

---

## 1. Playbook purpose

This playbook is the implementation contract for Claude Code.

The epic adds a narrow Ultima Online-style object movement system that lets Adventure-mode players place, pick up, carry, use, stack where already supported, and axe-destroy approved furniture, containers, wine bottles, and other practical objects.

The governing rule is:

> **Generic transport, specialized behavior.**

Grabby Hands moves objects between world and inventory. Existing object systems continue to define what those objects do.

A chair must remain sittable. A container must remain a real container. A wine bottle must preserve all meaningful data. A player's act of placing an object must not make its normal use exclusive to that player.

---

## 2. Non-negotiable invariants for every milestone

Claude Code must continuously protect these invariants:

1. Do not change a player's game mode to bypass Adventure restrictions.
2. Do not globally enable building/breaking in Adventure mode.
3. Do not broadly uncancel placement/break events.
4. Only explicitly enrolled types participate.
5. Type eligibility and exact-instance mobility are separate.
6. Static/admin/world decoration is immovable by default.
7. Grabby Hands mobility metadata is not a use-access ACL.
8. Placed objects remain normally usable by all players unless an existing independent system already restricts use.
9. Existing sitting behavior must be reused, not reimplemented.
10. Existing supported furniture stacking must survive Grabby Hands placement.
11. Existing container/menu behavior must be reused, not reimplemented.
12. Wine bottles are stateful gameplay items, not generic cosmetic props.
13. All meaningful wine-bottle state must survive repeated world/inventory round trips.
14. Pickup must produce two success-stage audio moments: grab/lift and inventory stow.
15. The stow sound must not play when inventory insertion fails.
16. Any project-recognized axe can destroy eligible Grabby Hands objects.
17. Axe destruction must produce explicit appropriate audio feedback.
18. Container axe destruction spills contents exactly once.
19. The intact container does not also self-drop during destruction.
20. Multi-block objects have one canonical root and one mutation transaction.
21. All mutations are server-authoritative.
22. No continuously ticking global clutter scanner.
23. Do not invent Rails endpoints unless existing authority requires them.
24. Do not overwrite unrelated worktree changes.
25. Do not push, merge, tag, release, or deploy without explicit owner authorization.

---

## 3. Milestone operating rules

### 3.1 Inspect before changing

Each milestone begins by reading the exact affected code and tests. Do not rely on class names or architecture from this playbook when the live repository differs.

### 3.2 Small isolated changes

Avoid opportunistic refactors. Changes should be attributable to the current milestone.

### 3.3 Evidence before completion

A milestone is not complete because code compiles. Its explicit validation gate must pass.

### 3.4 Preserve repository conventions

Reuse existing:

- registration helpers;
- tags/datagen;
- block/entity base classes;
- menu/container helpers;
- sitting/furniture behavior;
- sound registration;
- networking;
- logging;
- region/house permission services;
- portable-state components;
- tests/GameTests.

### 3.5 Stop on architectural contradiction

If repository discovery invalidates a core assumption, record the finding and revise the implementation plan before continuing. Do not force this document's suggested class layout onto a materially different project.

---

# Milestone 0 — Repository reconnaissance and behavior inventory

## Goal

Establish the real architecture before any feature implementation.

## Required discovery

### Git/worktree

Record:

- repository root;
- active worktree;
- branch;
- HEAD SHA;
- working-tree state;
- remotes;
- worktrees;
- likely integration/base branch and evidence;
- divergence.

If appropriate and safe, establish/use a dedicated `grabby-hands` branch/worktree following existing project conventions. Never destroy unrelated local work.

### Runtime/build

Confirm from build files:

- Minecraft version;
- NeoForge version;
- Java version;
- GeckoLib version if relevant;
- mappings;
- Gradle tasks used for compile/test/GameTest;
- client/server run tasks.

### Architecture map

Locate exact files/classes/methods for:

- item registration;
- block registration;
- Creative Tab population;
- tags/datagen;
- block entities;
- renderers;
- interaction event handlers;
- Adventure-mode special handling;
- custom axe classification;
- containers and menus;
- persistent world/per-position data;
- housing/HouseLot authorization;
- region protection;
- multi-block placement/root logic;
- sitting/seating;
- networking;
- logging;
- Rails calls touching relevant state;
- tests/GameTests.

### Furniture inventory

Search Java registrations and resources for at least:

`chair`, `stool`, `bench`, `throne`, `table`, `desk`, `cabinet`, `armoire`, `drawer`, `bookcase`, `shelf`, `crate`, `chest`, `barrel`, `box`, `container`, `furniture`.

For each candidate record:

- registry ID;
- item class;
- block class;
- block entity;
- model/renderer;
- directional properties;
- collision/support behavior;
- multi-block status;
- sitting behavior;
- container behavior;
- current place/break behavior in Adventure mode;
- Creative Tab status;
- evidence of static world use;
- likely Grabby Hands adapter.

### Sitting audit

Find the current sitting mechanism and answer:

- Which furniture uses it?
- How does interaction invoke it?
- Is seat position/facing derived from block state?
- Is there an entity/attachment involved?
- Can multiple furniture types share it?
- What would break if placement bypassed the native block placement path?

### Furniture stacking audit

Find concrete examples/tests of similar furniture being stacked.

Determine:

- whether stacking is native vanilla support, custom support validation, or merely geometry-compatible;
- which blocks are valid supports;
- whether collision/voxel shape permits it;
- whether multi-block furniture complicates it;
- which placement path must be preserved.

Do not assume “stacking” means every furniture type can stack on every other furniture type.

### Wine bottle audit — mandatory

Find the complete current wine bottle implementation.

Record:

- registry IDs;
- item/block/block-entity classes;
- data components/NBT/custom data;
- all meaningful gameplay properties;
- model/renderer/texture paths;
- stackability rules;
- use behavior;
- save/copy behavior;
- trade/economy integration;
- Rails integration if any;
- whether a default item recreation would lose data;
- the safest world representation strategy based on actual code.

This audit is a blocker for the final portable-state architecture.

### Container audit

Find the real base container stack and record:

- container blocks;
- block entities;
- menus/providers;
- item handlers;
- persistence;
- custom names;
- loot/drop behavior;
- portable storage if already implemented;
- current break behavior;
- viewer synchronization;
- anti-nesting/capacity rules.

### Axe audit

Find:

- vanilla axe tag/classification available in current mappings;
- every custom UltimaCraft axe base/type;
- existing shared tool tags;
- any special axe behavior;
- current durability handling;
- sounds used for chopping/breaking.

### Sound audit

Find:

- sound registry files/classes;
- existing item pickup/inventory sounds;
- existing furniture/wood break sounds;
- existing glass/bottle break sounds;
- existing placement sounds;
- whether custom resources are actually required.

## Deliverable

Produce a Milestone 0 reconnaissance report containing concrete source paths and a candidate matrix.

Do not implement feature behavior in this milestone.

## Gate M0

Pass only when:

- baseline build/tests are known and executed where feasible;
- sitting architecture is identified;
- furniture stacking behavior is understood from source/evidence;
- wine bottle data model is fully identified;
- container persistence/drop behavior is identified;
- axe classification is identified;
- sound infrastructure is identified;
- the proposed adapter categories are confirmed or corrected.

---

# Milestone 1 — Architecture contract and enrollment model

## Goal

Implement the narrow capability/policy foundation without yet making broad content placeable.

## Required work

Create/reuse the repository's equivalent of:

- Grabby Hands eligibility classification;
- per-instance mobility/provenance state;
- permission/policy service;
- canonical object/root resolver;
- adapter interface/strategy;
- shared axe classifier if one does not already exist;
- sound-role resolver for grab/stow/destruction if useful.

Prefer tags/data over hard-coded registry-ID lists where the project already uses tags.

## Critical distinction

Do not encode “movable” purely as a block tag.

A block tag can answer “this type supports Grabby Hands,” but a separate world-instance state must answer “this exact chair may be moved.”

## Public use rule

Do not add any rule that says only the placer/owner may right-click/use the object.

If placement provenance is stored, document exactly which movement/protection decisions use it.

## Tests

- enrolled type recognized;
- non-enrolled type rejected;
- static instance of enrolled type remains immovable by default;
- player/dynamic instance can be marked movable;
- use-access is not denied solely by provenance;
- recognized vanilla/custom axes resolve through one classifier.

## Gate M1

Architecture review confirms there is one source of truth for eligibility, instance mobility, policy, and root resolution.

---

# Milestone 2 — Transaction primitive and two-stage pickup audio

## Goal

Implement the safe server-authoritative pickup transaction on one minimal pilot object before expanding content.

## Required work

Implement/reuse a transaction path that can:

1. resolve canonical root;
2. validate reach;
3. validate instance mobility;
4. delegate protection/region/house checks;
5. capture portable state;
6. verify inventory insertion;
7. commit exactly once;
8. play grab/lift sound;
9. commit inventory item;
10. play stow sound only on successful inventory storage;
11. remove world representation without triggering a duplicate loot path.

The exact internal ordering may differ if rollback semantics require it, but observable invariants must hold.

## Sound requirement

Wire two semantically distinct sound roles:

- `GRAB/LIFT`
- `STOW/INVENTORY`

Reuse existing sound events where suitable.

## Tests

- one pickup -> one inventory item;
- world object removed exactly once;
- full inventory -> world object remains;
- full inventory -> no false stow sound;
- successful pickup -> both sounds occur in correct lifecycle stages;
- repeated packet/double interaction cannot duplicate;
- two-player pickup race has one winner.

## Gate M2

The pilot transaction is demonstrably atomic and audio stages are correct.

---

# Milestone 3 — Native furniture placement, pickup, sitting, and stacking

## Goal

Make a representative existing furniture type fully Grabby Hands capable in Adventure mode while preserving all specialized furniture behavior.

Choose the pilot from actual repository discovery, ideally a simple existing chair with sitting behavior.

## Required work

### Placement

Allow the enrolled furniture ItemStack to place its native block through the safest existing placement path in Adventure mode.

Do not globally bypass Adventure placement.

### Pickup

Use the M2 transaction to return the native furniture item/state to inventory.

### Sitting

Verify the exact existing sitting implementation still works without adapter duplication.

### Public use

A second player must be able to sit on the placed chair.

### Stacking

Preserve existing supported stacking behavior.

Placement validation must respect native collision/support rules and must not impose a blanket ground-only restriction.

### Orientation

Preserve native facing/rotation behavior.

## Tests

- chair places in Adventure mode;
- unrelated block still cannot be placed;
- chair pickup works;
- placer can sit;
- second player can sit;
- pickup/place cycle does not break seating;
- supported furniture stack case succeeds;
- invalid collision stack is rejected;
- static equivalent chair remains immovable by default.

## Live validation

Perform in-game Adventure-mode validation of sitting and stacking. Automated registration tests alone do not close this milestone.

## Gate M3

Furniture is movable without becoming inert and without weakening Adventure mode.

---

# Milestone 4 — Native furniture expansion and multi-shape validation

## Goal

Expand enrollment to a representative set of existing furniture categories and prove the adapter is reusable.

## Candidate categories

Based on repository discovery, include representative examples of:

- chair/stool;
- table/desk;
- bench;
- cabinet/bookcase/non-container furniture;
- directional furniture;
- unusual collision furniture.

Do not blindly enroll every asset.

## Required work

For each enrolled type verify:

- native interaction preserved;
- native collision preserved;
- orientation preserved;
- valid support/stack behavior preserved;
- static map instances remain protected.

## Gate M4

The system demonstrates data-driven/native-adapter reuse across materially different furniture without per-object business logic proliferation.

---

# Milestone 5 — Generic placed practical-item host

## Goal

Create the reusable world representation for item-only practical objects **only if M0 proves one is needed**.

## Required work

If required, implement a stationary placed-item host block/block entity/renderer using existing project patterns.

It must:

- store the complete canonical portable ItemStack or adapter-owned payload;
- persist across chunk unload/reload and server restart;
- sync enough data for rendering and interaction;
- use a compact physical shape appropriate for small items;
- render the stored item's real model/state where safe;
- support per-instance mobility metadata;
- delegate object-specific interaction rather than erasing it;
- avoid per-tick scanning.

If the live project has an existing equivalent, adapt it instead of creating a new one.

## Tests

- arbitrary enrolled simple pilot ItemStack round-trips exactly;
- custom name/data survives;
- save/reload survives;
- static/unmovable host can be represented;
- renderer receives correct stored item state;
- no duplicate loot path.

## Gate M5

A generic item-only world representation exists only if justified and preserves opaque item state.

---

# Milestone 6 — Wine bottle high-complexity integration

## Goal

Use the actual UltimaCraft wine bottle as the mandatory proof that Grabby Hands preserves rich gameplay state.

This milestone is not complete with a default bottle.

## Required work

### Build representative non-default test bottle(s)

Use actual meaningful properties discovered in M0. Do not invent fields.

### Placement

Place the bottle in Adventure mode using its safest native/generic adapter.

### Decorative presentation

The bottle should read visually as a bottle placed in the world, with appropriate scale/orientation/collision based on existing assets.

### Specialized behavior

Preserve all existing wine interaction/gameplay behavior that logically applies while placed.

### Public use

Another player must be able to interact with/use/inspect the bottle according to existing wine rules. Grabby Hands placement provenance must not make it private.

### Full-state round trip

Validate:

`inventory -> world -> inventory -> world -> inventory`

Compare all meaningful portable state after each step.

### Persistence

Validate placed bottle state across:

- chunk unload/reload;
- save/reload;
- server restart where feasible.

## Tests

Exact test fields must come from M0 discovery. At minimum compare more than registry ID/display name.

## Gate M6

A complex non-default bottle survives repeated movement with zero meaningful state loss and remains a real gameplay object while placed.

---

# Milestone 7 — Native portable containers

## Goal

Make representative existing containers movable while preserving contents and real container behavior.

## Required work

### Pickup

- resolve active viewers safely;
- capture contents and portable block-entity state;
- insert one portable container ItemStack;
- suppress duplicate normal loot/drop behavior;
- remove world container exactly once.

### Placement

Restore the native container/block entity and complete portable state.

### Use

Normal right-click continues opening the existing menu.

Grabby Hands must not replace it with a generic UI.

### Public use

Placement provenance alone must not block other players from using the container. Existing lock/house/security rules remain authoritative where already applicable.

## Tests

- empty container round-trip;
- filled container round-trip;
- custom name/state round-trip;
- second player can open/use when existing rules allow it;
- full inventory prevents pickup with no loss;
- no duplicate contents/self-drop;
- save/reload preserves placed contents.

## Gate M7

A filled native container can move through inventory and back without loss, duplication, or fake parallel container behavior.

---

# Milestone 8 — Axe destruction and destruction audio

## Goal

Implement the explicit Ultima-style axe destruction path for eligible Grabby Hands objects.

## Required work

Any recognized axe must be able to destroy eligible movable:

- furniture;
- containers;
- wine bottles;
- other enrolled destroyable practical objects.

Respect exact-instance mobility and existing protection rules.

### Audio

Destruction must play explicit appropriate sound feedback.

Prefer existing material-aware sound events:

- wood/furniture case;
- glass/bottle case;
- other materials where metadata already exists.

### Container spill

For containers:

1. snapshot contents;
2. destroy container exactly once;
3. release contents exactly once;
4. do not additionally drop an intact container;
5. use existing safe world-drop helpers.

### Tool durability

Follow the project's existing axe/tool durability convention. Do not invent special durability behavior unless required by existing tool logic.

## Tests

- vanilla axe recognized;
- every custom UltimaCraft axe family recognized;
- non-axe cannot use destruction path;
- movable furniture destroyed once;
- static furniture rejected;
- wine bottle destroyed once;
- wood and bottle cases produce appropriate destruction audio roles;
- filled container contents spill once;
- intact container does not drop;
- concurrent pickup vs axe resolves one winner.

## Gate M8

Axe destruction behaves consistently with RunUO inspiration, project protection rules, and no-duplication guarantees.

---

# Milestone 9 — Multi-block furniture and canonical-root atomicity

## Goal

Support large/multi-block furniture without duplicate items or orphan blocks.

## Required work

Integrate the repository's existing root/secondary-part convention.

Rules:

- one root stores canonical state;
- all parts resolve the root;
- pickup from any part resolves one transaction;
- placement validates the entire footprint first;
- source item consumed only after successful complete placement;
- axe destruction from any part resolves one transaction;
- secondary blocks never independently drop the item;
- sitting/use interactions still resolve correctly.

## Tests

- place multi-block item;
- pickup from root;
- pickup from every secondary part;
- blocked footprint consumes nothing;
- no orphan blocks;
- no duplicate item;
- axe from each part destroys once;
- use behavior survives replacement.

## Gate M9

All multi-block mutations are atomic around one canonical root.

---

# Milestone 10 — Housing, regions, provenance, and public usability audit

## Goal

Integrate movement/destruction with existing world protection while proving that movement permissions do not become use permissions.

## Required work

Delegate placement/pickup/destruction checks to existing:

- HouseLot/house authorization;
- region protection;
- admin/decorator overrides;
- secure/locked container systems.

### Explicit public-use audit

For each representative enrolled object verify:

- placer can use it;
- non-placer can use it when existing object rules allow it;
- provenance does not create an owner-only normal right-click rule;
- an existing lock/security rule still works independently.

## Tests

- allowed house placement/movement;
- disallowed protected region movement;
- static decoration protected;
- ordinary chair usable by non-placer;
- ordinary public object usable by non-placer;
- secure container still follows existing security semantics;
- admin override follows established project behavior.

## Gate M10

Protection is correctly scoped to movement/destruction and does not accidentally privatize normal object use.

---

# Milestone 11 — Broad content enrollment and data-driven policy

## Goal

Enroll the initial production set of practical objects discovered in M0 without turning the codebase into a registry-ID switch statement.

## Required work

Create/extend tags/profiles for appropriate categories such as:

- movable furniture;
- movable containers;
- placed practical items;
- axe-destroyable items;
- optional material/sound role metadata;
- explicitly excluded/static-only content where useful.

Review each candidate against:

- gameplay usefulness;
- static-world theft risk;
- state preservation capability;
- model suitability;
- interaction preservation;
- multi-block safety.

Do not enroll quest infrastructure, shrine roots, service NPC infrastructure, admin-only decorations, or other unrelated systems merely because they are blocks/items.

## Gate M11

Initial production enrollment is explainable, data-driven, and does not broaden Adventure permissions outside the intended object set.

---

# Milestone 12 — Persistence, multiplayer, and race-condition validation

## Goal

Prove the system survives real server lifecycle and concurrent players.

## Required automated validation

### Persistence

- placed furniture survives save/reload;
- seating/facing state survives where applicable;
- stacked furniture arrangement survives;
- wine bottle complete state survives;
- filled container survives;
- mobility/provenance survives;
- generic host state survives if used.

### Multiplayer

- A places chair; B sits;
- A places usable object; B uses it;
- A places wine bottle; B interacts according to wine rules;
- A/B race pickup same object -> one winner;
- A/B race axe same object -> one winner;
- A pickup vs B axe -> one authoritative outcome;
- contents never duplicate;
- both clients observe final state consistently.

### Audio

Validate success/failure audio routing in multiplayer so duplicate packets do not create duplicate logical outcomes. Client-local presentation may occur per observer according to existing sound APIs, but server mutations happen once.

## Gate M12

Persistence and multiplayer race tests are green.

---

# Milestone 13 — Full live Adventure-mode acceptance pass

## Goal

Validate the actual player experience. This milestone cannot be closed purely by compile/tests.

## Required live scenario

Use at least two clients where practical.

1. Join in Adventure mode.
2. Confirm ordinary disallowed block placement is still blocked.
3. Place an enrolled chair.
4. Sit on it.
5. Have the second player sit on/use it.
6. Pick it up using the chosen Grabby Hands gesture.
7. Confirm grab/lift sound.
8. Confirm inventory-stow sound.
9. Place it again.
10. Demonstrate a supported furniture stacking scenario.
11. Demonstrate an invalid intersection being rejected.
12. Place a non-default stateful wine bottle.
13. Have the other player use/inspect it according to existing wine behavior.
14. Pick it up and verify exact state.
15. Place and pick it up again and reverify state.
16. Place a filled container.
17. Have another player open it when existing rules permit.
18. Pick up and replace the filled container and verify contents.
19. Axe-destroy wooden furniture and confirm destruction sound.
20. Axe-destroy a wine bottle and confirm appropriate destruction sound.
21. Axe-destroy a filled container and confirm contents spill exactly once.
22. Attempt to pick up/destroy equivalent permanent static decoration and confirm rejection.
23. Save/restart/rejoin and verify persisted representative objects.

## Evidence

Record:

- branch/HEAD;
- build/test outputs;
- GameTest results;
- exact objects tested;
- exact wine fields verified;
- audio observations;
- multiplayer observations;
- static-protection observations;
- any known limitations.

## Gate M13

Close only when the owner-facing behavior matches the design and there are no unresolved state-loss, duplication, usability, seating, stacking, audio, or Adventure-permission defects.

---

# Milestone 14 — Documentation and completion

## Goal

Finish implementation documentation and leave the branch in a reviewable state.

## Required work

Update existing project documentation conventions with:

- architecture summary;
- enrolled content/policy explanation;
- how to enroll a new practical item;
- how to mark/keep static decoration immovable;
- adapter responsibilities;
- wine state-preservation contract;
- container destruction semantics;
- public usability vs movement permissions;
- sound-role behavior;
- test commands;
- live validation evidence;
- known exclusions.

Do not claim unsupported objects are covered.

## Final completion criteria

The feature is not complete until:

- all required gates passed;
- full relevant automated suite is green;
- live Adventure-mode validation passed;
- two-player usability was validated;
- furniture sitting passed;
- supported furniture stacking passed;
- two-stage pickup audio passed;
- axe destruction audio passed;
- wine bottle repeated exact-state round trip passed;
- filled-container round trip passed;
- container spill-on-destroy passed exactly once;
- static decoration protection passed;
- no broad Adventure-mode bypass exists;
- working tree contains no unexplained changes.

---

# Appendix A — Suggested candidate matrix columns

Claude Code should maintain a table similar to:

| Registry ID | Category | Native Block | Block Entity | Stateful | Sittable | Stack-compatible | Multi-block | Container | Static-world use | Grabby adapter | Enroll now? |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|

Populate this from repository evidence rather than assumptions.

---

# Appendix B — Mandatory wine preservation matrix

The actual property names must come from the live code.

| Property discovered in code | Before place | Placed world state | After pickup | After second cycle | Persistence verified |
|---|---|---|---|---|---|
| Registry/type | | | | | |
| Display/custom name | | | | | |
| Custom component/NBT field 1 | | | | | |
| Custom component/NBT field 2 | | | | | |
| Gameplay quantity/content | | | | | |
| Provenance/quality/etc. | | | | | |

Do not mark M6 complete with only visual equivalence.

---

# Appendix C — Failure rules

If any milestone exposes one of the following, stop progression and correct it before continuing:

- broad Adventure building permission accidentally enabled;
- static decoration becomes lootable;
- placed chair no longer seats players;
- non-placer cannot use ordinary placed furniture solely because they did not place it;
- supported furniture stacking stops working;
- pickup deletes an object when inventory is full;
- stow sound plays despite failed inventory insertion;
- wine state changes or resets through a round trip;
- container contents duplicate or vanish;
- axe destruction returns an intact container plus contents;
- custom UltimaCraft axe is not recognized because code checks only vanilla classes;
- multi-block parts independently drop;
- client becomes authoritative for a mutation;
- new Rails authority is introduced without an existing architectural requirement.
