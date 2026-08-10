# UltimaCraft Guildmaster Service NPC — Design Document

## 1. Purpose

Implement a reusable **Guildmaster Service NPC** system for UltimaCraft.

A Guildmaster is a `ServiceNPCEntity` that teaches exactly one configured player skill in exchange for money. Its visible role name is derived from that skill:

```text
{Skill Display Name} Guildmaster
```

Examples:

```text
Mining Guildmaster
Blacksmithing Guildmaster
Fishing Guildmaster
Magery Guildmaster
```

This must be implemented as a reusable service-NPC capability because UltimaCraft will have many service NPC variants over time. The Guildmaster must reuse existing Service NPC conventions instead of becoming a parallel NPC framework.

The system must integrate with:

- the existing `ServiceNPCEntity` hierarchy and behavior;
- the existing random NPC naming system;
- the existing bank teller-style NPC interface/screen shell;
- the canonical UltimaCraft skill registry/list;
- `ServiceNPCSpawnBlock`;
- the existing money/economy abstraction;
- the Rails server's Service NPC/world state representation;
- existing administrative spawn restrictions and economic controls;
- existing multiplayer/network security patterns.

---

## 2. Owner Requirements

The feature is considered complete only when all of the following are true.

1. A Guildmaster is a Service NPC specialization.
2. Every Guildmaster is bound to one canonical UltimaCraft skill.
3. The NPC's role/title is `{skillName} Guildmaster`.
4. The NPC receives a random personal name using the same system as other Service NPCs.
5. The admin can use `ServiceNPCSpawnBlock` to choose the Guildmaster's taught skill using the canonical index/order of all skills.
6. Interacting with the NPC opens a bank-teller-style service UI adapted for skill training.
7. A player can exchange money for skill advancement in the configured skill.
8. Training is server-authoritative.
9. The Guildmaster is represented/persisted on the Rails server in the same architectural family as other Service NPCs.
10. Rails/server-side economic policy can allow or deny Guildmaster spawning.
11. The implementation is extensible to future Service NPC service types.
12. Existing Service NPC capabilities must remain intact.

---

## 3. Ultima Online Training Rule

The supplied UO reference defines the core economy:

> 1 GP per 1/10 of skill, with the highest amount of skill costing 400 gold for 40 skill points.

For UltimaCraft, model this as an exact integer pricing rule rather than floating-point currency math.

### 3.1 Canonical rule

At the domain level:

```text
1 gold = 0.1 trained skill
10 gold = 1.0 trained skill
maximum NPC-trained level = 40.0
maximum purchase from 0.0 to 40.0 = 400 gold
```

Preferred internal representation for the transaction:

```text
skillTenths = skillValue * 10
costGold = purchasedTenths
```

Example:

```text
Current skill: 12.7
Requested target: 20.0
Purchased tenths: 73
Cost: 73 gold
```

### 3.2 Important implementation gate

Do **not** create a new skill scale merely to match this document.

Milestone 0 must determine how UltimaCraft currently stores skill values. The implementation must adapt the UO rule to the existing canonical skill representation.

If UltimaCraft stores whole skill points only, the equivalent is:

```text
10 gold per whole skill point
maximum Guildmaster-trained value = 40
```

If UltimaCraft already stores tenths or another fixed precision, use that precision without introducing floating-point drift.

### 3.3 Training ceiling

For this feature, a Guildmaster cannot train the configured skill beyond the UO-style NPC training ceiling of:

```text
40.0
```

The transaction must also respect any **existing UltimaCraft player skill caps, profession restrictions, progression locks, or global caps**. The effective target is the minimum of all applicable caps.

Do not bypass an existing skill progression system just because the Guildmaster ceiling is 40.0.

---

## 4. Core Domain Model

### 4.1 Guildmaster identity

A Guildmaster must have two distinct names:

```text
personalName = existing randomly generated NPC name
roleName     = "{Skill Display Name} Guildmaster"
```

The UI may present these as, for example:

```text
Aldric
Blacksmithing Guildmaster
```

or, if existing Service NPC naming conventions combine them:

```text
Aldric the Blacksmithing Guildmaster
```

Use the established Service NPC presentation convention found during reconnaissance.

### 4.2 Taught skill

Every Guildmaster instance has one authoritative skill binding.

Conceptually:

```java
GuildmasterServiceData {
    SkillId taughtSkill;
}
```

The actual implementation may use an existing registry ID, enum, resource location, integer ID, or other canonical skill key.

Requirements:

- persist the skill binding;
- synchronize it to clients when required for rendering/UI;
- restore it after chunk/world reload;
- include it in Rails representation;
- never use the display label as the authoritative identifier.

### 4.3 Canonical skill order

`ServiceNPCSpawnBlock` must expose the **canonical complete skill list** through a deterministic index.

Conceptually:

```text
0 -> first canonical skill
1 -> second canonical skill
...
N -> final canonical skill
```

The index must be derived from the existing skill registry/order rather than duplicated in a separate hard-coded Guildmaster list.

If the current skill architecture does not guarantee a stable order, Milestone 1 must introduce a stable mapping in the canonical skill domain and document the compatibility implications.

---

## 5. Service NPC Architecture

### 5.1 Reuse, do not fork

Guildmasters should reuse the capabilities already provided by `ServiceNPCEntity`, including whichever of the following are currently present:

- random names;
- persistence;
- interaction routing;
- movement/AI policy;
- invulnerability/protection;
- city/region awareness;
- Rails/world synchronization;
- spawn-block ownership or provenance;
- admin tooling;
- service dialogue;
- network opening of service screens;
- despawn restrictions;
- visual model/renderer.

Reconnaissance decides the exact inheritance/composition path.

### 5.2 Preferred extensibility

Avoid architecture like:

```text
if banker ...
else if guildmaster ...
else if futureService ...
```

spread across many classes.

Prefer an explicit service discriminator/capability that can be extended later, for example:

```text
ServiceNpcKind.BANKER
ServiceNpcKind.GUILDMASTER
```

plus service-specific payload:

```text
Guildmaster -> taughtSkill
```

If an equivalent service-type abstraction already exists, extend it instead of inventing another.

### 5.3 Single-skill v1, multi-service future

A v1 Guildmaster teaches exactly one skill.

Do not prematurely implement multi-skill Guildmasters, but structure the service payload and UI routing so future Service NPC service types do not require another architecture rewrite.

---

## 6. ServiceNPCSpawnBlock Administration

The spawn block must support creation of a Guildmaster and selection of its taught skill.

### 6.1 Required admin flow

The existing admin interaction pattern should be preserved.

When the spawn-block service type is Guildmaster, the block must expose/cycle/select:

```text
Guildmaster Skill Index
Guildmaster Skill Name
```

The visible admin feedback should make both clear, for example:

```text
Service: Guildmaster
Skill [17/NN]: Blacksmithing
```

The exact UI/chat/overlay mechanism should match the existing spawn-block admin tooling.

### 6.2 Source of truth

Never maintain a second manually curated array of Guildmaster skills.

The spawn block must query the canonical skill registry/list.

### 6.3 Persistence

The selected Guildmaster skill must survive:

- chunk unload/reload;
- world restart;
- block entity save/load;
- server restart.

If the spawn block already persists service configuration, add the skill binding to that format.

### 6.4 Invalid/missing skill

If a saved skill no longer exists:

- do not silently bind another skill;
- mark the configuration invalid or fall back to a clearly defined safe state;
- prevent a misconfigured Guildmaster from spawning;
- produce useful admin diagnostics.

---

## 7. Spawn Permission and Economic Levers

A Guildmaster must not spawn solely because a block requests one.

The authoritative server must ask the existing economy/world policy whether the spawn is permitted.

### 7.1 Required policy seam

Create or extend a reusable service such as:

```text
ServiceNpcSpawnPolicy
```

or the existing equivalent.

Conceptually:

```java
SpawnDecision canSpawn(
    ServiceNpcKind serviceKind,
    Optional<SkillId> skill,
    RegionId region,
    CityId city,
    BlockPos spawnPos
);
```

Do not force this exact signature if a suitable policy abstraction already exists.

### 7.2 Economic levers

The design must support server/Rails-controlled economic levers for Guildmasters. The exact existing configuration source must be discovered first.

At minimum, the policy should be capable of expressing:

- Guildmasters globally enabled/disabled;
- Guildmasters enabled/disabled for a city or region;
- allowed/denied Guildmaster skills;
- total Guildmaster population cap;
- per-city/per-region Guildmaster cap;
- per-skill Guildmaster population cap;
- service-NPC economic/population capacity rules already used by the mod.

If the current economy has richer concepts such as settlement wealth, employment slots, service demand, payroll, provisioning, or population capacity, integrate with those existing levers rather than adding an isolated boolean system.

### 7.3 Denied spawn behavior

If policy denies spawning:

- do not create the entity and immediately delete it;
- keep the spawn block intact unless existing conventions say otherwise;
- provide an admin-readable reason;
- avoid repeated high-frequency Rails requests or log spam.

### 7.4 Caching

If Rails is consulted for spawn permission, use the project's existing world-bootstrap/config cache strategy where available. A spawn block must not perform a synchronous remote request every tick.

---

## 8. Rails Integration

The Guildmaster must populate the Rails server as part of the existing world/Service NPC model.

### 8.1 Rails record requirements

The Rails representation must be able to identify at least:

- Minecraft/world NPC identity;
- service NPC kind = Guildmaster;
- taught skill canonical identifier;
- display/service role where appropriate;
- personal NPC name if other Service NPCs persist names;
- city/region association if existing NPCs carry it;
- position/world/shard fields used by current Service NPC records;
- active/inactive lifecycle fields already used by the project.

### 8.2 Extend the current API

Milestone 0 must locate the current Service NPC create/update/remove path.

Prefer extending the existing payload, for example:

```json
{
  "service_type": "guildmaster",
  "service_data": {
    "skill_id": "blacksmithing"
  }
}
```

or the nearest equivalent in the current Rails schema.

Do not create a Guildmaster-only endpoint unless the current API architecture genuinely requires it.

### 8.3 Idempotent registration

World reloads must not create duplicate Rails records.

Use the same stable identity/upsert strategy as existing Service NPCs.

### 8.4 Spawn economics source of truth

The Rails side should expose the economic/configuration data needed by the Minecraft server through the project's existing bootstrap/config endpoint where possible.

If `/api/world_bootstrap` already carries region/city/economic policy, prefer extending that payload rather than adding per-spawn HTTP calls.

### 8.5 Training transactions and Rails

Do not automatically add a new Rails request for every training click unless the existing economy architecture requires Rails to be transaction-authoritative.

Milestone 0 must determine whether player money and skills are authoritative in Minecraft, Rails, or split across both.

Whichever authority owns the state must own the atomic transaction.

If an existing economic ledger/audit/event pipeline exists, emit the appropriate transaction event through it.

---

## 9. Guildmaster Training Transaction

The actual purchase must be server-authoritative and atomic.

### 9.1 Quote

Given:

```text
configured skill
current player skill
requested target/amount
effective training cap
available money
```

the server calculates:

```text
trainable delta
final skill value
exact cost
```

The client may display a preview, but the server recalculates before commit.

### 9.2 Validation

Before completing a training purchase, validate all applicable conditions:

- player is still connected;
- Guildmaster entity still exists;
- player is close enough to interact;
- entity is actually a Guildmaster;
- Guildmaster's configured skill matches the request;
- player is eligible to train that skill;
- current skill is below the effective Guildmaster cap;
- requested delta is positive;
- requested target does not exceed the effective cap;
- player has sufficient funds;
- player has not exceeded any existing total skill cap;
- the request is not stale/replayed if the networking layer needs request identity protection.

### 9.3 Atomic commit

The operation must behave as one transaction:

```text
validate -> determine final price -> deduct money -> award skill -> synchronize
```

A failure must not leave the player with:

- lost money and no skill;
- gained skill and no charge;
- partial duplicated training.

Use existing economy/skill transaction primitives where available.

### 9.4 Currency

The UO rule is denominated in gold.

UltimaCraft already has gold/silver/copper concepts. Reconnaissance must identify the canonical payment API.

Preferred behavior:

- express the training price as a gold value;
- allow the existing wallet/coin abstraction to make change or convert denominations if it already supports this;
- do not hand-roll inventory coin scanning if an economy service already exists.

If the current economy intentionally requires physical gold only for NPC services, preserve that rule and document it.

### 9.5 Skill precision

All price and skill calculations should use fixed-point/integer math compatible with the existing skill precision.

Avoid direct binary floating-point equality for transaction values.

---

## 10. User Interface

The Guildmaster should reuse the **same visual/interface family as the Bank Teller NPC**, while replacing bank actions with training actions.

This means visual and interaction reuse, not copying banker business logic into the Guildmaster.

### 10.1 Screen structure

Recommended v1 layout:

```text
+---------------------------------------------------+
| NPC portrait | Personal Name                      |
|              | {Skill} Guildmaster                |
|              | Greeting / training explanation    |
|                                                   |
| Current {Skill}: XX.X                             |
| Maximum NPC training: 40.0                        |
|                                                   |
| Train to: [ - ] [ XX.X ] [ + ]                   |
| Cost: XXX gold                                    |
|                                                   |
|                         [ Train Skill ] [ Close ]  |
+---------------------------------------------------+
```

If the Bank Teller interface has a different established composition, preserve its visual shell and adapt the content.

### 10.2 Interaction model

The player should be able to choose a valid training amount/target without repeatedly buying microscopic increments.

Preferred controls may include:

- numeric stepper;
- slider;
- `+1`, `+5`, `Max` shortcuts;
- target value input if the existing UI framework supports it safely.

The screen must always show:

- taught skill;
- current player skill;
- effective maximum trainable value;
- selected gain or target;
- quoted cost;
- affordability;
- disabled-state reason when training is unavailable.

### 10.3 Feedback

On success:

```text
Training complete
Skill increased by X
Paid Y gold
New skill: Z
```

On failure, show a specific reason such as:

```text
Not enough gold
Already trained to the Guildmaster maximum
Training would exceed your total skill cap
Guildmaster is no longer available
```

Do not silently close the screen on a rejected request.

### 10.4 Reusing Bank Teller UI

Milestone 0 must identify reusable banker screen components.

Preferred result:

- shared Service NPC screen frame;
- shared portrait/header/dialogue components;
- service-specific screen/controller content.

Avoid subclassing a banking screen if that couples Guildmaster behavior to bank data.

---

## 11. Networking and Security

The client must never be allowed to send an authoritative price or skill result.

A request should contain only intent, for example:

```text
Guildmaster entity/network id
requested target or requested delta
```

The server determines:

```text
skill being taught
current skill
training cap
cost
funds
final skill
```

Do not trust the client's supplied:

- skill ID;
- price;
- current balance;
- current skill;
- maximum skill;
- final awarded value.

Rate-limit or naturally gate requests through the screen/network handler to prevent spam.

---

## 12. Persistence and Synchronization

Guildmaster instance data must survive and remain consistent across:

- save/load;
- chunk unload/reload;
- dedicated server;
- client join/rejoin;
- Rails synchronization;
- entity tracking.

Persist the canonical taught-skill identifier.

If an integer index is used by the spawn block for admin selection, prefer persisting the canonical skill ID on the entity itself so registry reorderings do not silently retarget already-spawned Guildmasters.

---

## 13. Compatibility Requirements

The feature must not regress:

- bankers;
- other existing Service NPCs;
- Service NPC random naming;
- existing `ServiceNPCSpawnBlock` modes;
- Rails world bootstrap;
- city/region population logic;
- economy/wallet/coin handling;
- existing player skill progression;
- dedicated server startup;
- client/server networking.

Any migration of a shared Service NPC payload must remain backward-compatible with existing service NPC records unless the project explicitly performs a coordinated Rails + mod migration.

---

## 14. Testing Strategy

### 14.1 Unit/domain tests

Cover:

- canonical skill index mapping;
- skill ID round-trip persistence;
- title formatting;
- price calculations;
- training cap calculations;
- insufficient funds;
- no-op/zero delta;
- total skill cap restrictions;
- fixed-point conversion;
- spawn policy decisions.

Minimum pricing examples:

```text
0.0 -> 40.0 = 400 gold
0.0 -> 1.0  = 10 gold
12.7 -> 20.0 = 73 gold
39.9 -> 40.0 = 1 gold
40.0 -> any  = not trainable
```

Adjust examples to the canonical UltimaCraft skill precision discovered in Milestone 0.

### 14.2 Persistence tests

Verify:

- spawn block selected skill survives save/load;
- Guildmaster taught skill survives save/load;
- invalid skill ID is handled safely;
- Rails registration does not duplicate after reload.

### 14.3 Networking tests

Verify the server rejects:

- forged price;
- forged skill;
- out-of-range target;
- request from too far away;
- replay/stale interaction where relevant;
- insufficient funds after the screen opened.

### 14.4 Integration tests

Test:

- banker still opens and works;
- Guildmaster opens correct training UI;
- two Guildmasters can teach different skills;
- admin skill index cycles the full canonical skill list;
- Rails receives correct service kind + skill;
- spawn policy can deny a Guildmaster;
- allowed spawn creates exactly one NPC;
- random NPC name is retained.

### 14.5 Live in-game validation

At minimum:

1. Place/configure a Service NPC spawn block.
2. Select Guildmaster.
3. Select a skill by index.
4. Spawn Guildmaster.
5. Confirm random personal name.
6. Confirm `{skill} Guildmaster` role.
7. Open UI.
8. Train a low skill.
9. Confirm money deduction.
10. Confirm skill increase.
11. Confirm 40.0 ceiling.
12. Confirm insufficient-funds handling.
13. Restart world/server.
14. Confirm same taught skill after reload.
15. Confirm Rails record.
16. Deny spawning with an economic lever and verify the block does not create the NPC.
17. Re-enable and verify spawn succeeds.
18. Verify banker regression.

---

## 15. Observability

Useful structured logs should identify:

- Guildmaster entity UUID/id;
- taught skill;
- city/region;
- spawn approval/denial reason;
- Rails upsert success/failure;
- player training attempt outcome;
- cost and trained delta where appropriate.

Do not log high-frequency UI quote changes at INFO level.

Avoid exposing sensitive player/account data in logs.

---

## 16. Failure Modes to Avoid

Do not:

- duplicate the skill registry in Guildmaster code;
- identify skills only by localized/display names;
- trust a client-provided cost;
- use floating-point currency math where fixed-point is possible;
- charge the player before final validation;
- create a Rails request every spawn-block tick;
- duplicate Service NPC records after server restart;
- copy/paste the entire Banker UI implementation;
- hard-code Guildmaster behavior into every Service NPC class;
- silently change a saved skill if registry ordering changes;
- ignore existing total skill caps;
- let denied economic policy spawn an entity briefly before deletion.

---

## 17. Recommended Architecture Shape

The final codebase should trend toward the following separation, adapted to existing project conventions:

```text
Service NPC domain
├── ServiceNPCEntity
├── ServiceNpcKind / existing service type abstraction
├── Guildmaster service payload
│   └── taughtSkill
├── ServiceNpcSpawnPolicy
└── ServiceNPCSpawnBlock configuration

Skill domain
├── canonical Skill registry/list
├── player skill read/write service
└── skill cap rules

Economy domain
├── player funds abstraction
└── GuildmasterTrainingService
    ├── quote
    └── purchase

UI/network
├── shared Service NPC screen shell
├── Guildmaster training screen
└── server-bound training request

Rails
├── Service NPC persistence/upsert
├── Guildmaster service_data
└── economic spawn-policy/bootstrap data
```

This is a target shape, not permission to replace good existing abstractions.

---

## 18. Definition of Done

The feature is done when:

- Guildmaster is a first-class Service NPC service type;
- a Guildmaster is configured from a canonical skill index;
- skill binding persists by stable skill identity;
- Guildmaster naming and role display are correct;
- training UI reuses the bank-service visual system;
- training uses the supplied UO pricing rule adapted to UltimaCraft's existing skill precision;
- server owns validation, price, payment, and skill award;
- 40.0 NPC training ceiling works;
- existing skill caps are respected;
- Rails stores Guildmaster service information without duplication;
- economic policy can permit or deny spawning;
- all automated tests pass;
- dedicated-server build/startup passes;
- owner performs successful live in-game validation;
- banker and existing Service NPC behavior remain unchanged.
