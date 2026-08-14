# UltimaCraft — Grabby Hands System Design

**Status:** Proposed implementation design, revised with interaction/audio/state-preservation requirements  
**Target:** UltimaCraft Minecraft mod, Minecraft 1.21.1 / NeoForge 21.1.x / Java 21  
**Primary player mode:** Adventure  
**Project codename:** `grabby-hands`

---

## 1. Executive summary

UltimaCraft needs a controlled Ultima Online-style world-object system that lets Adventure-mode players physically rearrange practical objects without weakening Adventure mode globally.

Players must be able to pick up approved furniture, containers, wine bottles, and other practical objects; carry them as normal inventory items; place them back into the world; continue using their original gameplay behavior; and destroy appropriate placed objects with any recognized axe.

The defining architectural rule is:

> **Generic transport, specialized behavior.**

Grabby Hands owns the safe transition between **world state** and **inventory state**. It must not replace the behavior of the object being moved.

A chair remains a chair and remains sittable. A container remains a real container. A stateful wine bottle remains the exact logical wine bottle that was picked up. A practical item keeps its normal interaction. Existing furniture stacking must continue to work. The transport system must never turn rich gameplay objects into generic inert decorations.

The system must remain narrow and server-authoritative. It must not grant generic Adventure-mode building or breaking permissions, and it must not make permanent world decoration collectible merely because the same block type is also used by a player-placeable object.

---

## 2. Hard product requirements

The following requirements are non-negotiable and must be treated as acceptance criteria, not optional polish.

### 2.1 Adventure-mode placement

Approved objects can be placed by players in Adventure mode without changing game mode and without globally bypassing Adventure-mode restrictions.

### 2.2 Pickup into normal inventory

Approved movable world objects can be picked up and stored in normal player inventory. Pickup must be duplication-safe and must preserve the complete portable state of the object.

The intended physical interaction is a deliberate pickup gesture such as **sneak + right-click with an empty main hand**, unless repository analysis identifies an already-established UltimaCraft interaction convention that is materially better. Normal right-click must remain available for the object's normal use.

### 2.3 Objects remain usable by all players

Grabby Hands must not introduce an owner-only use restriction.

A player placing an object does **not** make its normal interaction private. Other players must still be able to use the placed object according to its existing gameplay rules.

Examples:

- a placed chair can still be sat on by other players;
- an ordinary public-use object can still be activated by other players;
- a container continues to use whatever access/security behavior its existing container implementation already provides;
- Grabby Hands ownership/mobility metadata must not silently become an interaction ACL.

Existing independent systems such as a lock, secure container, house restriction, quest restriction, or other established authorization rule may continue to govern use where they already do so. Grabby Hands must not invent a second use-permission system.

### 2.4 Existing furniture behavior survives movement

Existing furniture must retain its real behavior after pickup and replacement.

This specifically includes:

- sitting/seating behavior;
- orientation/facing behavior;
- interaction hitboxes;
- collision behavior;
- any animation/state behavior already present;
- existing supported stacking behavior.

If UltimaCraft currently allows compatible furniture blocks to be stacked, Grabby Hands must not prevent that merely because the placement is happening in Adventure mode. Placement validation must understand valid support/collision rather than imposing a blanket “must sit directly on terrain” rule.

### 2.5 Two pickup/inventory sound effects

A successful world-to-inventory pickup must have **two distinct audio moments**:

1. a physical **grab/lift** sound when the object is successfully claimed for pickup;
2. a **stow/inventory** sound when the resulting item is successfully committed to the player's inventory.

These sounds may use existing suitable UltimaCraft or Minecraft sound events when appropriate. Claude Code must inspect existing sound registration and conventions before adding new assets.

If inventory insertion fails, the world object must remain intact and the successful stow sound must not play.

### 2.6 Axe destruction sound

Destroying an eligible Grabby Hands object with an axe must have an explicit destruction sound.

Where practical, the sound should respect material/object type. For example, wooden furniture should not sound identical to a glass bottle. Reuse existing sound types/events when possible rather than adding redundant assets.

### 2.7 Wine bottles are a mandatory high-complexity proof case

Wine bottles are not merely decorative placeholders. The current UltimaCraft wine-bottle implementation is expected to carry meaningful gameplay data.

Claude Code must inspect the real implementation before deciding how Grabby Hands serializes or renders a placed bottle.

For a wine bottle, the following round trip must preserve all meaningful state exactly:

`world object -> pickup -> ItemStack in inventory -> placement -> world object`

Repeated round trips must also preserve the state.

Grabby Hands may not reconstruct a bottle from a registry ID and defaults if doing so would lose data. The exact portable state must be carried forward.

### 2.8 Any recognized axe destroys eligible objects

Any item recognized by UltimaCraft as an axe must be able to destroy eligible placed furniture, containers, and wine bottles, subject to existing protection rules.

Do not hard-code only vanilla axes if the project already has custom axe classes/tags.

### 2.9 Container destruction spills contents exactly once

When a movable container is destroyed with an axe, its contained items must be released into the world exactly once before the container itself is consumed.

The intact container must not also drop as an item unless a later explicit design changes that rule.

### 2.10 Static decoration remains protected by default

Eligibility of a block/item type and movability of a particular world instance are separate concepts.

A chair type can support Grabby Hands while a chair instance placed as permanent Britannia scenery remains immovable.

---

## 3. Product goals

The system should make UltimaCraft's world feel physically manipulable in the spirit of Ultima Online.

A player should be able to:

- buy, obtain, or carry furniture and then place it in Adventure mode;
- sit on a chair after it has been moved;
- allow another player to sit on that same chair;
- stack compatible furniture where the existing geometry/system supports it;
- carry a crate without losing its state;
- place a crate and use it as a real container;
- put a stateful wine bottle on the floor, table, counter, shelf, or another valid support surface;
- pick that exact bottle up later and keep its data;
- leave approved practical clutter around the world;
- destroy an eligible furniture/container/bottle object with an axe and hear an appropriate destruction sound;
- receive immediate audio feedback when physically picking something up and when it successfully enters inventory.

This is intentionally “littering” in the Ultima sense: practical items can have a persistent physical presence in the world.

---

## 4. Non-goals

This epic does not:

- turn Adventure mode into Survival mode;
- globally set `mayBuild` or equivalent player capability;
- globally uncancel block placement or block breaking;
- make all Minecraft blocks freely placeable;
- make every existing decorative map object collectible;
- replace the existing furniture system;
- replace the existing sitting system;
- replace the existing container/menu system;
- replace house/region authorization;
- introduce a complete theft/criminality system;
- introduce full rigid-body physics;
- silently despawn player-placed clutter as a performance shortcut;
- require every item in UltimaCraft to become placeable in the first release;
- introduce Rails persistence for local world objects unless existing project authority already requires it.

---

## 5. Core design principles

### 5.1 Generic transport, specialized behavior

The Grabby Hands transport layer should know how to answer questions such as:

- Is this type eligible to be physically placed?
- Is this exact world instance movable?
- Can this player move it here?
- How is its complete portable state captured?
- How is that state restored?
- What audio should accompany the transition?
- Is it a canonical root of a multi-block object?

It should **not** own questions such as:

- How does a chair seat a player?
- How does a wine bottle calculate its gameplay properties?
- How does a crate expose inventory slots?
- How does a lamp turn on?
- How does a specialized object animate?

Those behaviors stay in their existing systems.

### 5.2 Capability is not instance mobility

There must be two independent concepts:

1. **Type capability:** this registered item/block can participate in Grabby Hands.
2. **Instance mobility:** this exact placed world instance is allowed to be picked up/destroyed/moved.

This is essential for protecting permanent decoration.

### 5.3 Usability is not movability

A placed object may be usable even when it is not movable.

Likewise, being movable by one policy does not imply only that mover may use it.

Grabby Hands must never derive interaction access from pickup ownership unless an existing independent system explicitly requires it.

### 5.4 State-preserving inverse operations

For a movable object, placement and pickup should be designed as inverse operations:

`portable ItemStack/state <-> placed world object/state`

The round trip must be deterministic and duplication-safe.

### 5.5 Server authority

All placement, pickup, state transfer, destruction, permission, inventory insertion, and content spilling must be decided on the logical server.

Client effects can predict or animate only after the project’s established networking model says it is safe. The client must never be the source of truth for removing or creating the object.

---

## 6. RunUO research and design lessons

Reference repository: `https://github.com/runuo/runuo`  
Research baseline used for the original design: commit `71b2794f12eb6f948b1c5598ae8b350401a22d4d`.

### 6.1 Mobility is explicit

RunUO's item movement pipeline treats item movability as an explicit property and validates lifting before allowing the transition.

Relevant references:

- `Server/Item.cs`
- `Server/Mobile.cs`

**UltimaCraft lesson:** do not infer movability solely from block type. Permanent Britannia decoration must be able to use the same asset as movable player furniture without becoming lootable.

### 6.2 Dropping into the world is a first-class transition

RunUO validates drop-to-world behavior before moving an item to a world position.

**UltimaCraft lesson:** a world object is not conceptually “mined and re-crafted.” It is the physical representation of an item with portable state.

### 6.3 Furniture is metadata around normal object behavior

RunUO furniture includes real containers such as cabinets and armoires, tagged as furniture while retaining their container behavior and orientation variants.

Relevant reference:

- `Scripts/Items/Containers/FurnitureContainer.cs`

**UltimaCraft lesson:** furniture classification must wrap existing furniture behavior, not replace it.

### 6.4 Axes destroying furniture is directly supported by RunUO precedent

RunUO's lumberjacking targeting detects furniture and has a dedicated furniture-destruction path.

Relevant references:

- `Scripts/Engines/Harvest/Core/HarvestTarget.cs`
- `Scripts/Engines/Harvest/Core/HarvestSystem.cs`

The path rejects unsuitable immovable world furniture and destroys eligible furniture.

**UltimaCraft lesson:** the requested axe behavior is not an arbitrary Minecraft convention; it closely matches Ultima-style behavior.

### 6.5 Destroyed containers release contents

RunUO container destruction moves contained items into the world before deleting the container.

Relevant reference:

- `Server/Items/Container.cs`

**UltimaCraft lesson:** container contents must not vanish and must not be duplicated. Destruction is a transaction.

### 6.6 Interior decoration separates movement from use

RunUO has specific house decoration rules for turning/raising/lowering objects and checks house/security state separately from the object's underlying functionality.

Relevant reference:

- `Scripts/Items/Misc/InteriorDecorator.cs`

**UltimaCraft lesson:** Grabby Hands should delegate to existing house/region permission systems instead of inventing its own broad ACL.

### 6.7 Beverage bottles are stateful objects

RunUO models wine as a beverage bottle with content/quantity state rather than a purely cosmetic prop.

Relevant reference:

- `Scripts/Items/Food/Beverage.cs`

**UltimaCraft lesson:** UltimaCraft's wine bottle must be treated as a stateful item first and a decoration second. Its current local implementation, not assumptions from RunUO, defines the actual preservation contract.

### 6.8 The RunUO `Data` directory is supporting context

Useful contextual sources include:

- `Data/items.cfg`
- `Data/containers.cfg`
- `Data/Decoration/*`

However, movement, containers, furniture, houses, and beverage behavior primarily live in server/script code.

**UltimaCraft lesson:** Claude Code must inspect assets/data and behavior code together.

---

## 7. UltimaCraft repository analysis requirements

Before implementation, Claude Code must inspect the live project and identify the existing source of truth for each relevant behavior.

At minimum, inspect:

- furniture blocks/items and their base classes;
- the sitting/seating implementation;
- furniture collision and placement rules;
- any furniture stacking behavior or examples already present;
- multi-block furniture/root-part conventions;
- all container block/block-entity/menu classes;
- portable container serialization if it exists;
- the wine bottle item/block/model/data implementation;
- Adventure-mode interaction hooks;
- custom axe classes and tags;
- block/item tags;
- sound registration and existing suitable sounds;
- house/HouseLot ownership and region protection;
- persistent per-position/per-block-entity metadata patterns;
- Creative Tab patterns;
- networking/payload conventions;
- Rails boundaries;
- automated/GameTest infrastructure;
- multiplayer testing conventions.

Do not create parallel systems where the project already has one.

---

## 8. Player interaction contract

### 8.1 Normal use

Normal right-click continues to invoke the object's normal gameplay behavior.

Examples:

- right-click chair -> existing sitting behavior;
- right-click container -> existing open-container behavior;
- right-click a usable practical object -> its existing action;
- right-click wine bottle -> whatever the current wine system defines.

Grabby Hands must not consume or replace normal interaction callbacks unless it is specifically handling a pickup gesture.

### 8.2 Pickup gesture

Preferred default:

**Sneak + right-click with empty main hand** on an eligible movable world object.

This creates a clear distinction between “use this” and “pick this up.”

Claude Code may recommend a different gesture only if the existing UltimaCraft project already has a stronger established convention or a concrete conflict makes this one unsafe.

### 8.3 Pickup transaction

A successful pickup should proceed conceptually as follows:

1. Resolve the canonical movable object/root.
2. Validate reach and current player state.
3. Validate exact-instance mobility.
4. Delegate region/house/protection checks.
5. Capture the complete portable state without mutating the world.
6. Verify that the resulting inventory item can actually be inserted.
7. Reserve/commit the pickup server-side.
8. Play the **grab/lift** sound.
9. Insert the exact portable item into inventory.
10. Play the **stow/inventory** sound only after successful inventory commitment.
11. Remove the world representation atomically.
12. Emit existing project logging/telemetry if applicable.

The actual ordering may need to use a rollback-safe transaction mechanism depending on the codebase, but the observable invariant is strict:

- no item duplication;
- no state loss;
- no world deletion when inventory storage fails;
- no success stow sound on failure.

### 8.4 Placement

Using an enrolled portable item against a valid target may place its world representation even in Adventure mode.

Placement must validate:

- the item is eligible;
- the player is allowed to place at the destination;
- collision/shape is valid;
- the destination can support the object according to that object's rules;
- orientation is valid;
- multi-block footprint is clear where applicable;
- furniture stacking is allowed when the existing object geometry/logic supports it;
- the source stack is consumed only after successful server placement.

The placement system must use the object's native placement path when possible so existing behavior survives.

### 8.5 Public usability after placement

After successful placement:

- all players can invoke the object's normal use behavior unless an existing independent rule says otherwise;
- the placer is not granted an implicit exclusive-use lock;
- the mobility owner, if tracked at all, is separate from use access;
- seating and other interactions must work identically to equivalent existing furniture.

### 8.6 Furniture stacking

Claude Code must determine how current furniture stacking works in the live project.

Grabby Hands placement must preserve it. This can require checking the actual collision/support surface of the target rather than assuming only full vanilla blocks are valid supports.

Tests must cover at least:

- compatible furniture placed on normal ground;
- compatible furniture placed on an existing supported furniture object;
- invalid intersecting placement rejected;
- stacked furniture remains usable where the existing furniture behavior says it should be.

Do not invent generalized unsafe stacking if the current furniture does not support it. Preserve the existing capability.

---

## 9. Object adapters / participation modes

One universal block implementation is unlikely to be correct for every asset. Use a small set of adapters around existing behavior.

### 9.1 Native furniture adapter

Use when the object already exists as a real block/block item with specialized behavior.

Must preserve:

- sit behavior;
- facing;
- collision;
- model/rendering;
- block-state properties;
- animations;
- stack/support behavior;
- any existing block-entity state.

### 9.2 Native container adapter

Use for existing crates/chests/cabinets/other containers.

Must preserve:

- contents;
- custom name;
- orientation;
- lock/security state if portable by design;
- any custom metadata/components;
- menu behavior;
- block-entity state required for gameplay.

Container pickup must not also execute normal self-dropping loot behavior.

### 9.3 Stateful practical-item adapter

Use for complex items that have meaningful item data and need a physical world representation.

Wine bottles are the mandatory proof case.

The safest conceptual pattern is a stationary placed-item host that stores the **complete source ItemStack** or equivalent canonical portable representation, then renders/uses the original item through an adapter.

However, Claude Code must first inspect the wine implementation. If the wine system already has a real block/block entity or another safer representation, reuse it.

### 9.4 Simple practical-item adapter

Later practical items with no specialized behavior may use the generic placed-item host with minimal policy/configuration.

### 9.5 Multi-block furniture adapter

For large furniture occupying more than one block position:

- one canonical root owns portable state;
- secondary parts reference the root;
- pickup from any part resolves the root;
- placement validates the whole footprint before consuming inventory;
- destruction from any eligible part resolves one atomic destruction transaction;
- no secondary part may independently drop a duplicate item.

---

## 10. Wine bottle preservation contract

Wine is the system's highest-value state-preservation test.

### 10.1 Claude must inspect before designing

Before implementing a generic state schema, Claude Code must answer from the live project:

- What class represents a wine bottle?
- Is it an Item, BlockItem, block, block entity, or combination?
- What data components/NBT/custom components/capabilities does it carry?
- Does it have custom name/label/vintage/quality/origin/maker/content/quantity/age or other metadata?
- Does data change through gameplay?
- Does the item render model vary based on state?
- Is any state Rails-authoritative?
- How is it saved, copied, stacked, compared, consumed, or traded?

The exact field list must come from the repository. Do not hard-code assumptions from this document.

### 10.2 Complete-state requirement

Whatever fields exist, every meaningful portable property must survive:

1. inventory -> world placement;
2. world -> inventory pickup;
3. repeated placement/pickup cycles;
4. save/reload while placed;
5. chunk unload/reload;
6. server restart;
7. another player interacting with the placed bottle;
8. multiplayer observation.

### 10.3 No “same-looking replacement”

A pickup may not discard the original data and generate a new default wine bottle that merely uses the same model.

The result must be semantically identical to the source bottle.

Tests should compare the portable data before and after the round trip, not just registry ID or display name.

### 10.4 Decorative but still gameplay-real

A placed bottle is decorative in the sense that it can visibly sit in the world, but it remains a real gameplay object.

If the existing wine item has use behavior, display information, trading value, spoilage/aging, quantity, provenance, or other systems, placement must not sever those systems.

---

## 11. Furniture behavior contract

### 11.1 Sitting

If existing furniture implements sitting, Grabby Hands must invoke/reuse that exact seating mechanism after placement.

Do not introduce a second seating implementation just for movable furniture.

Acceptance tests must include:

- placer can sit after placement;
- second player can sit after placement;
- pickup/replacement does not break seat position/orientation;
- static equivalent furniture still behaves as before.

### 11.2 Stacking

Existing supported furniture stacking must remain supported.

The implementation must inspect:

- voxel/collision shapes;
- support face rules;
- block-state placement rules;
- any custom placement helper;
- multi-block interactions.

Do not flatten all furniture placement to `target.above()` without respecting the actual target/support geometry.

### 11.3 Orientation

Preserve native facing/rotation conventions. Where a picked-up object has an orientation that is meant to be portable, decide explicitly whether placement restores previous orientation or faces relative to the player. Follow existing project convention where one exists.

### 11.4 Interaction ownership

The placer must not become the sole user of a chair or other ordinary furniture merely because mobility metadata records who placed it.

---

## 12. Container contract

Containers are stateful and require transactional handling.

### 12.1 Pickup

Before moving a container:

- close/resolve any active viewers according to existing menu rules;
- capture contents and all portable state;
- ensure the item can be stored;
- prevent vanilla/custom loot-drop duplication;
- remove the world block only as part of the successful transaction.

### 12.2 Placement

Placement restores the complete container state into the real native container implementation.

### 12.3 Use

Placed containers remain usable through the existing container UI/menu implementation. Grabby Hands does not create a generic fake inventory UI.

### 12.4 Axe destruction

When an eligible container is destroyed with an axe:

1. resolve permission and canonical root;
2. snapshot contents;
3. remove/consume the container exactly once;
4. release each contained item into the world exactly once using existing safe drop helpers;
5. play destruction audio;
6. prevent the intact container from dropping as a second item;
7. preserve existing trap/security consequences if the project already defines them and they are appropriate.

---

## 13. Mobility, ownership, and protection

### 13.1 World-generated/admin decoration

Default: **immovable**.

A type being eligible for player placement must not retroactively enroll all existing instances.

### 13.2 Player-placed instances

Default: movable if the type policy allows it and no existing region/house rule forbids movement.

The project may record placement provenance/owner if needed for movement authorization or audit, but this must not become an implicit “only owner may use” rule.

### 13.3 Housing

Reuse existing HouseLot/house ownership/co-owner/public/security semantics.

Grabby Hands should ask the existing system questions such as “may this player move/place/destroy here?” rather than duplicating ownership data.

### 13.4 Regions

Reuse existing region/protection checks for cities, protected structures, quest areas, or other restricted spaces.

### 13.5 Admin override

If the project already has an admin/decorator override convention, integrate with it. Do not silently invent a new unrestricted bypass.

---

## 14. Sound design

Audio is part of the interaction contract.

### 14.1 Pickup sound A: grab/lift

Purpose: communicate that the player's hand has successfully taken hold of the object.

Properties:

- short;
- physical/tactile;
- distinct from inventory UI clicks;
- emitted only after the server has accepted the pickup operation enough that rollback is safe.

### 14.2 Pickup sound B: inventory stow

Purpose: communicate that the item successfully entered inventory.

Properties:

- distinct from the grab/lift sound;
- may reuse an existing inventory/item pickup sound if it fits UltimaCraft;
- must not play when insertion fails.

### 14.3 Placement sound

Use the object's existing placement sound/sound type where available. Do not require a new sound if native placement already provides correct feedback.

### 14.4 Axe destruction sound

Must be explicit and should prefer material-aware existing sounds.

At minimum validate wooden furniture and wine-bottle destruction as perceptibly appropriate cases.

### 14.5 Registration policy

Claude Code must inventory existing project and vanilla sound events before adding custom sound assets. New assets should be added only when existing sounds do not meet the gameplay requirement.

---

## 15. Adventure-mode integration strategy

Do not weaken Adventure mode globally.

The intended strategy is a narrowly scoped interaction/placement service that recognizes only enrolled objects and performs the permitted mutation server-side after all policy checks.

Potential integration points depend on the live NeoForge mappings and current project architecture. Claude Code must inspect the project's actual interaction-event code and 1.21.1 APIs before naming the final hooks.

Hard constraints:

- no temporary game-mode switching;
- no broad build permission;
- no “uncancel every placement in Adventure” handler;
- no broad axe-breaking permission;
- no client-only world mutation.

---

## 16. Portable state representation

Prefer the repository's existing portable-state mechanism if one exists.

If none exists, Minecraft 1.21.1 ItemStack data components/custom data are the likely portable representation, but Claude must confirm the project's established patterns.

### 16.1 Requirements

Portable state must be:

- complete;
- versionable;
- copy-safe;
- server-authoritative;
- resilient to save/reload;
- resistant to accidental duplication;
- able to preserve specialized object state without the transport layer understanding every field.

### 16.2 Opaque payload principle

Where safe, Grabby Hands should transport specialized state as an opaque payload owned by the specialized object adapter rather than manually enumerating every field in a central service.

This reduces the risk that adding a future wine property silently breaks pickup preservation.

---

## 17. Transaction and anti-duplication model

Every mutating operation must have one authoritative commit point.

### 17.1 Pickup invariants

- never both leave the world object and grant the inventory item;
- never delete the world object if inventory transfer cannot complete;
- never trigger the normal block loot path in addition to portable-state creation;
- reject simultaneous pickups of the same canonical root after the first transaction wins.

### 17.2 Placement invariants

- never consume the source item before full placement validation;
- never place a partial multi-block object;
- never create the world object twice from duplicate client interaction packets;
- restore specialized state before exposing the object for use.

### 17.3 Destruction invariants

- eligible object consumed exactly once;
- container contents spilled exactly once;
- no intact self-drop unless explicitly designed later;
- simultaneous multiplayer axe/pickup attempts resolve to one winner.

---

## 18. Persistence and networking

Placed objects must survive:

- chunk unload/reload;
- save/reload;
- server restart;
- player reconnect;
- multiplayer observation.

Use native block entities/saved data/data attachments/components according to the repository's existing architecture.

Do not create a global per-tick manager that scans all placed clutter.

For a generic placed-item host, persist enough data to reconstruct the exact portable ItemStack and any instance mobility/provenance required by policy.

Client sync should use the project's existing block-entity/data/network mechanisms. Avoid custom packets where standard synced state already solves the problem.

---

## 19. Rendering

### 19.1 Native blocks

Existing furniture/containers continue using their current block models/Geo renderers.

### 19.2 Generic practical items

If a generic placed-item host is required, its renderer should render the stored item's actual model/state where possible rather than maintaining a second manually synchronized art registry.

Wine-bottle rendering must be chosen only after inspecting the current bottle assets and data model.

### 19.3 Collision

Small decorative practical items may use a compact collision/selection shape appropriate to their physical size. Furniture continues using its native collision.

Avoid invisible full-block collision for a bottle unless the existing asset is intentionally full-block sized.

---

## 20. Performance and abuse controls

The system should not rely on per-tick scanning.

Mutations are event-driven:

- pickup interaction;
- placement interaction;
- use interaction delegated to existing object;
- axe destruction;
- normal persistence lifecycle.

Potential clutter limits may be considered later only if the live project demonstrates a real performance/abuse requirement. Do not silently despawn persistent player objects as the initial solution.

Any future limits should be explicit and user-visible.

---

## 21. Required automated testing

The test strategy must validate behavior, not only registration.

### 21.1 Eligibility / static protection

- enrolled type can be player-placed;
- player-placed instance can be moved when allowed;
- pre-existing/static instance of same type remains immovable by default;
- non-enrolled block is unaffected.

### 21.2 Pickup transaction

- successful pickup creates exactly one inventory item and removes exactly one world object;
- full inventory leaves world object intact;
- two rapid pickup attempts cannot duplicate;
- pickup state survives exact-data comparison;
- grab sound and stow sound fire at their correct success stages.

### 21.3 Furniture

- placed chair remains sittable;
- second player can sit on it;
- pickup/replacement preserves seating orientation/behavior;
- supported furniture stacking still works;
- invalid collision stacking fails safely.

### 21.4 Wine bottle

Mandatory test matrix:

- create a non-default bottle with representative meaningful data discovered from repository analysis;
- place it;
- save/reload if test harness supports it;
- pick it up;
- compare complete meaningful portable state;
- place it again;
- compare world state;
- repeat a second cycle;
- verify another player can use/inspect it according to existing wine behavior;
- axe destruction removes it exactly once and produces destruction audio.

### 21.5 Containers

- filled container pickup preserves all contents;
- place -> open -> contents correct;
- custom state/name preserved;
- axe destruction spills contents exactly once;
- intact container does not additionally drop;
- simultaneous viewer/pickup cases are safe.

### 21.6 Multi-block furniture

- pickup from every part resolves one root;
- placement validates full footprint;
- failed placement consumes nothing;
- axe destruction from any part resolves once;
- no orphan secondary blocks.

### 21.7 Multiplayer

At minimum two-player validation:

- A places chair; B can sit;
- A places usable item; B can use it unless an existing independent restriction applies;
- A/B race to pick up same item; exactly one succeeds;
- A/B race pickup vs axe destruction; exactly one authoritative outcome;
- state visible consistently to both clients.

---

## 22. Required live in-game validation

Automated tests are not enough for interaction-heavy behavior.

A live Adventure-mode validation pass must demonstrate:

1. pick up an eligible object;
2. hear grab/lift sound;
3. hear stow sound when inventory receives it;
4. place it without leaving Adventure mode;
5. use it normally;
6. have a second player use it;
7. sit on moved furniture;
8. stack supported furniture;
9. place a stateful wine bottle;
10. pick it up and verify its data remains intact;
11. repeat wine placement/pickup;
12. axe-destroy wooden furniture and hear destruction feedback;
13. axe-destroy wine bottle with appropriate destruction feedback;
14. axe-destroy a filled container and observe contents spill exactly once;
15. verify permanent/static decoration is not collectible or axe-destroyable through Grabby Hands.

Record evidence according to the project's existing implementation-log conventions.

---

## 23. Acceptance criteria

The epic is complete only when all of the following are true:

- Adventure mode remains globally restrictive.
- Approved furniture can be placed and picked up.
- Approved containers can be placed and picked up without state loss.
- Wine bottles can exist decoratively in the world without losing their rich gameplay data.
- Wine state survives repeated world/inventory round trips exactly.
- Placed objects retain their existing specialized behavior.
- Grabby Hands does not introduce owner-only use.
- Existing chairs remain sittable after movement.
- Other players can sit/use placed objects according to existing rules.
- Existing supported furniture stacking remains possible.
- Pickup has a distinct grab/lift sound and a distinct successful inventory-stow sound.
- Failed inventory insertion does not delete the object or play a false success stow cue.
- Any recognized axe can destroy eligible movable furniture/container/wine objects.
- Axe destruction has explicit, appropriate audio feedback.
- Destroyed containers spill contents exactly once.
- Static/admin/world decoration remains immovable by default even if its type is eligible.
- Multi-block objects are atomic and duplication-safe.
- Save/reload and multiplayer preserve state.
- No broad Adventure-mode bypass is introduced.
- No parallel furniture, seating, container, housing, or wine gameplay system is invented unnecessarily.

---

## 24. Architectural decision summary

The recommended architecture is:

- **Data-driven eligibility** for which types can participate.
- **Per-instance mobility/provenance** for whether a particular world object can move.
- **One server-authoritative Grabby Hands transaction service** for place/pickup/destroy.
- **Native adapters** for furniture, containers, and multi-block systems.
- **A generic placed-item host only where needed** for practical item-only objects.
- **Wine bottle as the mandatory complex-state reference implementation.**
- **Existing interaction systems remain authoritative** for sitting, using, opening, drinking, etc.
- **Public usability by default; mobility policy is separate.**
- **Two-stage pickup audio and material-appropriate axe destruction audio.**
- **No global weakening of Adventure mode.**

The core rule should remain visible throughout implementation:

> **Move the object without changing what the object is.**
