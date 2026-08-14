# UltimaCraft Guildmaster Service NPC — Milestone Playbook

## Working Agreement

This playbook is for implementing the Guildmaster Service NPC system described in:

```text
UltimaCraft_Guildmaster_Service_NPC_Design.md
```

The feature must be developed in an isolated Git worktree so other agents can continue using the main `britannia_mod` folder.

### Baseline

Use the project owner's current integrated baseline:

```text
branch: patch-18
main working folder: britannia_mod
```

Before creating the worktree, verify these assumptions against the local repository. If `patch-18` has moved, branch from its current local HEAD.

Recommended feature branch:

```text
feature/guildmaster-service-npc
```

Recommended worktree folder:

```text
../britannia_mod_guildmaster
```

Do not reuse a worktree owned by another feature.

### Change discipline

- Inspect before editing.
- Preserve existing project conventions.
- Do not push, merge, tag, release, or deploy unless the owner explicitly authorizes it.
- Do not modify unrelated work.
- Keep generated/build output out of commits.
- Prefer one isolated local commit per approved milestone.
- Record important architectural findings and validation evidence in the project's established implementation log/facts documents if those documents exist.
- If the real architecture contradicts this playbook, stop the milestone and report the contradiction with evidence before redesigning shared systems.

---

# Milestone 0 — Repository Reconnaissance and Baseline

## Goal

Establish the actual current architecture and produce an evidence-backed implementation map before changing behavior.

## Required reconnaissance

Locate and document:

### Minecraft / NeoForge

- `ServiceNPCEntity` and subclasses/implementations;
- Service NPC type/service discriminator;
- banker/bank teller entity;
- bank teller interaction handler;
- bank teller screen/menu/container/networking;
- shared NPC portrait/dialogue UI components;
- random NPC name generation;
- `ServiceNPCSpawnBlock`;
- its block entity/state/config persistence;
- admin interaction used to select service types/options;
- current canonical skill registry/list/enum;
- skill value representation and precision;
- player skill storage and mutation APIs;
- total skill cap/progression rules;
- money/currency abstraction;
- gold/silver/copper conversion behavior;
- player balance mutation APIs;
- Rails HTTP client/service;
- Service NPC Rails registration/upsert/removal;
- `/api/world_bootstrap` or equivalent configuration fetch;
- city/region/shard lookup;
- current Service NPC spawn/population/economic policies;
- tests covering any of the above.

### Rails

Locate and document:

- Service NPC model/table/schema;
- service type representation;
- create/update/upsert endpoint;
- uniqueness/stable identity rules;
- city/region/shard associations;
- economic configuration/models;
- world bootstrap payload;
- any service-NPC population limits;
- request specs/model specs/service tests;
- seed data that defines Service NPC/service types.

## Skill-scale decision

Explicitly answer:

```text
How is one player skill stored?
What values represent 0, 1.0, 0.1, 40.0 and 100.0?
Is fractional precision supported?
Where are skill caps enforced?
```

Do not implement Guildmaster pricing until this is proven.

## Rails authority decision

Explicitly answer:

```text
Is player money authoritative in Minecraft or Rails?
Are player skills authoritative in Minecraft or Rails?
Is there an existing transaction/event/ledger pipeline?
```

## UI reuse decision

Identify the lowest-level bank teller components that can be safely reused without coupling Guildmaster behavior to banking logic.

## Deliverable

Produce a Milestone 0 report containing:

- relevant file paths/classes;
- architecture diagram/flow;
- canonical skill representation;
- existing economy transaction path;
- Rails Service NPC lifecycle;
- economic-policy path;
- reusable banker UI pieces;
- risks/open contradictions;
- exact proposed files to change in Milestones 1–8.

## Gate

No functional implementation in Milestone 0 unless a tiny compile-only probe is absolutely necessary.

Stop after reporting Milestone 0.

---

# Milestone 1 — Canonical Guildmaster Service Type and Skill Binding

## Goal

Add Guildmaster as a first-class Service NPC service type with stable skill identity.

## Work

1. Extend the existing Service NPC service-type abstraction with Guildmaster.
2. Add a service-specific Guildmaster payload containing the canonical taught skill ID.
3. Reuse the canonical skill registry.
4. Define deterministic skill index <-> canonical skill mapping for admin selection.
5. Add serialization/deserialization needed for Guildmaster data.
6. Add role/title generation:

```text
{Skill Display Name} Guildmaster
```

7. Preserve random personal naming through the existing Service NPC mechanism.

## Persistence rule

The admin spawn block may select by integer index, but the spawned Guildmaster should persist a stable canonical skill identity wherever feasible.

Do not make registry position the only persisted entity identity if reordering would retarget an existing NPC.

## Tests

Add focused tests for:

- Guildmaster service type round-trip;
- canonical skill index mapping;
- invalid index behavior;
- stable skill ID persistence;
- title formatting;
- random personal name integration if testable.

## Gate

Build and focused tests pass. Existing Service NPC type tests remain green.

Stop and report files changed, behavior, tests, and any schema implications.

---

# Milestone 2 — ServiceNPCSpawnBlock Guildmaster Configuration

## Goal

Allow admins to configure which Guildmaster skill a spawn block produces.

## Work

1. Extend the existing spawn-block service selection to include Guildmaster.
2. When Guildmaster is selected, expose the full canonical skill list by deterministic index.
3. Preserve the existing admin interaction style.
4. Show clear admin feedback including index and skill name.
5. Persist the Guildmaster configuration in the spawn block/block entity.
6. Validate configuration before spawn.
7. Safely handle a removed/unknown saved skill.

## Required behavior

Example admin feedback:

```text
Service: Guildmaster
Skill [12/NN]: Mining
```

The exact format can follow existing project conventions.

## Tests

Verify:

- cycling from first to last skill;
- wrap/clamp behavior matches existing selection conventions;
- skill survives save/load;
- non-Guildmaster service selection is unaffected;
- invalid skill cannot silently spawn a different Guildmaster.

## Gate

Focused spawn-block tests and build pass.

---

# Milestone 3 — Guildmaster Entity Behavior and Interaction Routing

## Goal

Spawn and interact with a functional Guildmaster entity before implementing the payment transaction.

## Work

1. Create or extend the appropriate Service NPC entity/service implementation.
2. Bind the configured canonical skill.
3. Reuse random personal naming.
4. Reuse existing Service NPC lifecycle/AI/protection/city awareness.
5. Route player interaction to the Guildmaster service screen/network flow.
6. Ensure two Guildmasters can coexist with different skills.
7. Synchronize taught skill to the client only as required.

## Constraints

Do not copy Banker business logic.

Do not create a second NPC base class.

## Tests

Verify:

- configured skill reaches spawned entity;
- two skills produce two correct role names;
- skill survives entity save/load;
- Guildmaster interaction is routed separately from Banker;
- Banker interaction remains unchanged.

## Gate

A Guildmaster can be spawned and opened in-game with placeholder/read-only training content if necessary.

---

# Milestone 4 — Rails Service NPC Persistence and Economic Spawn Policy

## Goal

Represent Guildmasters correctly in Rails and make spawning subject to server economic policy.

## Rails work

1. Extend existing Service NPC persistence with Guildmaster service information.
2. Store the stable canonical skill ID.
3. Preserve current NPC stable identity/upsert rules.
4. Add backward-compatible serialization/API behavior.
5. Extend world bootstrap/configuration if that is the existing policy-distribution mechanism.

## Economic policy work

Integrate Guildmaster spawn permission into the existing population/economic framework.

The policy must be capable of representing at least:

```text
global Guildmaster enabled/disabled
city/region Guildmaster enabled/disabled
allowed/denied skill
global population cap
city/region population cap
per-skill population cap
existing service-NPC economy/population constraints
```

Do not invent redundant controls if equivalent existing levers already exist.

## Minecraft behavior

Before creating the NPC:

```text
spawn request
-> local cached policy evaluation
-> allow: spawn/register
-> deny: no entity created, admin-readable reason
```

Avoid synchronous Rails requests in block ticks.

## Tests

Rails:

- Guildmaster payload validation;
- upsert is idempotent;
- taught skill stored correctly;
- existing Banker/Service NPC payloads remain valid;
- bootstrap policy serialization.

Minecraft:

- allow decision spawns one NPC;
- deny decision spawns none;
- reason is observable;
- policy cache/path works after reload.

## Gate

Rails and mod tests pass. Demonstrate no duplicate Rails row on simulated/repeated registration.

---

# Milestone 5 — Server-Authoritative Training Quote and Purchase Service

## Goal

Implement training economics independent of the UI.

## Work

Create or extend a domain service responsible for:

```text
quote(player, guildmaster, requestedTarget)
purchase(player, guildmaster, requestedTarget)
```

Names should follow project conventions.

## Pricing

Implement the UO-derived rule using the existing skill precision:

```text
1 gold per 0.1 skill
maximum NPC-trained skill = 40.0
```

Equivalent whole-point rule if the project only supports whole skill units:

```text
10 gold per skill point
maximum = 40
```

Never use a second hidden skill scale.

## Effective cap

The transaction must respect:

```text
Guildmaster cap
existing per-skill cap
existing total player skill cap
existing progression/profession restrictions
```

## Payment

Use the existing authoritative money API.

Do not duplicate wallet/coin deduction logic.

## Atomicity

The operation must not allow:

- charge without skill gain;
- skill gain without charge;
- duplicated replay purchase.

## Tests

At minimum:

```text
0.0 -> 40.0 = 400 gold
0.0 -> 1.0 = 10 gold
12.7 -> 20.0 = 73 gold
39.9 -> 40.0 = 1 gold
40.0 -> any = rejected
```

Adapt values to actual project precision.

Also test:

- insufficient funds;
- total skill cap;
- invalid/negative target;
- target above 40;
- wrong Guildmaster skill;
- no-op target;
- exact-funds purchase;
- change/denomination behavior through the existing economy service.

## Gate

Transaction service is fully tested before client purchase controls are enabled.

---

# Milestone 6 — Guildmaster Training UI and Networking

## Goal

Deliver the bank-teller-style Guildmaster screen and secure client/server transaction flow.

## UI

Reuse the Bank Teller visual shell/components where practical.

Show:

- NPC portrait;
- random personal name;
- `{Skill} Guildmaster`;
- greeting/help text;
- current skill;
- effective Guildmaster maximum;
- selected target or gain;
- price in gold;
- affordability;
- Train button;
- Close/back navigation consistent with the existing NPC UI.

## Networking

Client request contains intent only.

Example:

```text
entity id
requested target
```

Server resolves/revalidates everything else.

The client must not authoritatively supply:

```text
skill ID
cost
current skill
balance
final awarded skill
```

## UX

The interface should make meaningful purchases convenient. Prefer a stepper/slider/shortcut pattern over forcing 0.1 purchases one click at a time.

Disable or explain the Train button when:

- insufficient funds;
- already at cap;
- total skill cap prevents training;
- selected target is invalid.

After purchase, refresh the authoritative balance and skill state.

## Tests

Verify:

- quote preview follows selected target;
- server rejects forged price/skill data;
- insufficient funds changed after screen-open is rejected;
- player too far from NPC is rejected if existing interactions enforce range;
- duplicate/replay behavior is safe;
- Banker screen still works.

## Gate

Dedicated-server-compatible build and client compile pass.

---

# Milestone 7 — Cross-System Integration and Data Compatibility

## Goal

Harden the feature across reloads, multiplayer, Rails synchronization, and existing Service NPC behavior.

## Work

1. Validate entity + block persistence across restart.
2. Validate Guildmaster Rails upsert across restart.
3. Validate world bootstrap/economic policy refresh.
4. Confirm service UI works on dedicated server.
5. Confirm multiple clients cannot corrupt a player's transaction state.
6. Confirm multiple Guildmasters with different skills function simultaneously.
7. Confirm no change to Banker behavior.
8. Confirm no unintended changes to other Service NPCs.
9. Confirm registry/skill localization labels are used correctly.
10. Add migration/backward-compat handling if a shared payload/schema changed.

## Gate

All focused integration tests pass and no known compatibility blocker remains.

---

# Milestone 8 — Full Regression Suite and In-Game Validation Package

## Goal

Prepare a release-quality candidate for owner validation.

## Automated validation

Run the project's appropriate equivalents of:

- focused Guildmaster tests;
- Service NPC tests;
- skill-system tests;
- economy tests;
- networking tests;
- Rails model/request/service tests;
- full Minecraft mod test suite;
- full Rails test suite if practical;
- clean Minecraft build;
- dedicated server startup/smoke test;
- formatting/lint/static checks used by the project.

Record exact commands and results.

## Owner in-game test script

Provide the owner a concise validation script:

1. Configure spawn block as Guildmaster.
2. Cycle to a known skill.
3. Spawn NPC.
4. Confirm random name and correct Guildmaster role.
5. Open training UI.
6. Confirm current skill and price.
7. Buy training.
8. Verify money decreases correctly.
9. Verify only configured skill increases.
10. Verify 40.0 NPC cap.
11. Verify insufficient-funds message.
12. Restart the server/world.
13. Confirm Guildmaster skill binding persists.
14. Confirm Rails has exactly one matching Service NPC record.
15. Disable/deny Guildmaster spawn with an economic lever.
16. Confirm a configured block does not spawn one.
17. Re-enable the policy.
18. Confirm spawn succeeds.
19. Open a Banker and verify normal banking behavior.

## Gate

Stop for owner live-validation evidence.

Do not declare the feature complete based only on automated tests.

---

# Milestone 9 — Final Evidence, Documentation, and Isolated Completion Commit

## Goal

Close the feature only after owner validation.

## Work

1. Record owner in-game validation results.
2. Update design/implementation notes with any final deviations.
3. Confirm Rails migration/schema state is documented.
4. Confirm no unrelated files are included.
5. Re-run material tests after any final fix.
6. Produce final diff summary.
7. Create the final isolated local milestone commit if owner workflow authorizes it.

## Final report

Include:

```text
Feature:
Branch:
Worktree:
Starting baseline commit:
Final commit(s):
Minecraft files changed:
Rails files changed:
Database migrations:
Tests:
Build:
Dedicated server validation:
Owner live validation:
Rails persistence result:
Spawn-policy result:
Known limitations:
Uncommitted/untracked files:
Push/merge status:
```

## Completion criteria

Do not mark complete until every item in the Design Document's Definition of Done has evidence.
