# Initial Claude Prompt — UltimaCraft Guildmaster Service NPC

You are implementing a new UltimaCraft feature: the **Guildmaster Service NPC**.

Before doing any implementation work, read these two project-root documents in full:

```text
UltimaCraft_Guildmaster_Service_NPC_Design.md
UltimaCraft_Guildmaster_Service_NPC_Playbook.md
```

Treat them as the feature specification and execution contract.

## Feature summary

A Guildmaster is a specialization of the existing `ServiceNPCEntity`.

Each Guildmaster teaches one canonical UltimaCraft player skill and presents itself as:

```text
{Skill Display Name} Guildmaster
```

The NPC also receives a random personal name through the same naming system used by other Service NPCs.

Players interact with the Guildmaster through a service screen using the same visual/interface family as the existing Bank Teller NPC, but the Guildmaster screen is for **training the configured skill in exchange for money**.

The supplied Ultima Online rule is:

```text
1 gold per 0.1 skill
maximum NPC-trained skill = 40.0
0.0 -> 40.0 therefore costs 400 gold
```

Do not assume UltimaCraft uses fractional skill values. First prove how the current skill system represents values and adapt this rule to that canonical representation.

The `ServiceNPCSpawnBlock` must allow an admin to select Guildmaster and then select which Guildmaster to spawn using the deterministic index/order of the complete canonical skill list.

Guildmasters must be represented on the Rails server using the existing Service NPC lifecycle/persistence architecture.

Guildmaster spawning must also be subject to existing or extended **economic/population policy levers**, so a configured spawn block does not automatically guarantee that the NPC may spawn.

This is intended to be the foundation for many future Service NPC types. Reuse and extend the current Service NPC framework; do not build a parallel Guildmaster-only NPC system.

## Git/worktree requirements

The integrated baseline is expected to be:

```text
branch: patch-18
main folder: britannia_mod
```

Other agents may be using the main working folder.

First verify the local repository state.

Create a dedicated feature branch and independent worktree from the current local `patch-18` HEAD.

Recommended:

```text
feature/guildmaster-service-npc
../britannia_mod_guildmaster
```

If either name conflicts with an existing branch/worktree, choose a clear equivalent and report it.

Do not disturb the owner's primary `britannia_mod` working tree.

Do not push, merge, tag, release, or deploy.

## Your task now: Milestone 0 only

Execute **Milestone 0 — Repository Reconnaissance and Baseline** from the playbook.

Do not begin functional Guildmaster implementation yet.

Inspect both the Minecraft/NeoForge code and Rails code and produce an evidence-backed implementation map.

You must locate and report, with exact file paths and important classes/methods:

### Minecraft

- `ServiceNPCEntity` architecture;
- current Service NPC type/service discriminator;
- Bank Teller entity/service;
- Bank Teller interaction flow;
- Bank Teller screen/menu/network messages;
- reusable Bank Teller visual/screen components;
- random NPC naming;
- `ServiceNPCSpawnBlock`;
- spawn-block persistence and admin selection flow;
- canonical skill registry/list;
- exact skill storage precision;
- skill mutation APIs;
- per-skill and total skill-cap enforcement;
- currency/wallet/coin abstraction;
- authoritative money deduction path;
- Service NPC -> Rails registration/update/remove;
- world bootstrap/config cache;
- city/region/shard resolution;
- existing service-NPC population/economic spawn restrictions;
- relevant automated tests.

### Rails

- Service NPC model/schema;
- Service NPC type representation;
- create/update/upsert API path;
- stable identity/uniqueness rules;
- city/region/shard fields;
- economic/population configuration;
- `/api/world_bootstrap` or equivalent;
- relevant specs/tests;
- service-NPC seed/config data.

## Required questions you must answer before proposing implementation

### Skill representation

Explicitly prove:

```text
How is a player skill stored?
What exact internal value represents:
- 0
- 0.1
- 1.0
- 40.0
- 100.0

Are fractions supported?
Where are per-skill and total skill caps enforced?
```

If 0.1 precision does not exist, state exactly how the UO pricing rule should map into the existing representation without introducing a second skill scale.

### Transaction authority

Explicitly prove:

```text
Where is player money authoritative?
Where are player skills authoritative?
Is there an existing transaction, ledger, or audit-event service?
What API must a Guildmaster purchase use so payment + skill gain can be atomic?
```

### Bank UI reuse

Identify which Bank Teller UI components can be reused as a neutral Service NPC shell and which components are banking-specific and should not be inherited/copied.

### Rails integration

Identify the smallest backward-compatible way to represent:

```text
service type: Guildmaster
taught skill: stable canonical skill identifier
```

Prefer extending the current Service NPC payload/model over creating a Guildmaster-only API.

### Spawn economics

Identify the existing mechanism that should decide whether a Guildmaster may spawn.

Determine whether policy should come from cached Rails bootstrap/config data, local config, city economy state, population/service capacity, or a combination already established in the project.

Do not design a synchronous Rails request on every spawn-block tick.

## Milestone 0 output

Return a structured report with:

```text
1. Branch/worktree setup
2. Starting commit / repository status
3. Minecraft Service NPC architecture
4. Bank Teller UI/network architecture
5. ServiceNPCSpawnBlock architecture
6. Canonical skill architecture and precision
7. Economy/payment architecture
8. Rails Service NPC architecture
9. Existing spawn/economic policy architecture
10. Recommended implementation shape
11. Exact proposed files likely to change
12. Risks/compatibility concerns
13. Test plan mapped to existing test infrastructure
14. Any contradictions between the design docs and the real repository
```

Use concrete paths, class names, method names, schema fields, API routes, and test names.

Do not rely on guesses when the repository can answer the question.

## Stop condition

After completing the Milestone 0 report:

- stop;
- do not implement Milestone 1;
- do not make functional Guildmaster changes;
- do not commit unrelated files;
- do not push or merge.

The owner will review the reconnaissance before authorizing the implementation milestones.
