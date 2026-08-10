# Patch 18 Network Integration Playbook

## Document status

This document is the authoritative integration plan for merging the existing feature branches into the Patch 18 release line while preserving compatibility with the network architecture introduced by the `banking` branch.

The project should be executed one milestone at a time. Each milestone must be validated and reported before the next milestone begins.

## Primary objective

Merge the following branches into the Patch 18 release line without breaking client-server communication, dedicated-server compatibility, saved data, menus, block entities, entities, or feature-specific synchronization:

- `banking`
- `Farming`
- `shrines-monolith`
- `blacksmithing`
- `banners-dyetub`

The `banking` branch contains the new canonical network system. Any feature branch using the previous network implementation must be adapted to the banking network contract during integration.

## Recommended branch strategy

Treat the current Patch 18 branch as the protected release source of truth.

Resolve the exact Git reference for Patch 18 from the repository before making changes. This document refers to it as `patch-18`, but the existing repository spelling and capitalization must be preserved.

Create a dedicated integration branch from the current Patch 18 HEAD:

```text
patch-18-network-integration
```

Perform all feature merges and compatibility work on this integration branch. Do not perform the integration directly on `patch-18`.

The feature branches remain read-only merge sources during this project. Do not rewrite, rebase, force-update, or opportunistically repair them.

Recommended topology:

```text
patch-18
└── patch-18-network-integration
    ├── merge banking
    ├── merge Farming and patch networking
    ├── merge blacksmithing and patch networking
    ├── merge shrines-monolith
    └── merge banners-dyetub
```

Use a dedicated clone or Git worktree for this integration so unrelated working-tree changes in other project copies cannot contaminate the merge.

## Recommended merge order

The default merge order is:

1. `banking`
2. `Farming`
3. `blacksmithing`
4. `shrines-monolith`
5. `banners-dyetub`

This order establishes the banking network implementation first, then migrates the two branches already known to require network patches. The remaining branches are merged only after the highest-risk network consumers are stable.

The order may be changed only when repository evidence demonstrates a dependency that requires it. Any change must be recorded in `PATCH_18_INTEGRATION_LOG.md` before the affected merge begins.

## Canonical project assumptions to verify

The project is expected to use:

- Minecraft 1.21.1
- NeoForge 21.1.72
- Java 21
- GeckoLib 4.6.6 where applicable

Claude must verify these values from the repository. Repository configuration is authoritative if any value differs.

## Non-negotiable integration rules

1. Work only on `patch-18-network-integration` after it has been created from the verified Patch 18 HEAD.
2. Never commit directly to any feature source branch.
3. Never push, force-push, rebase, tag, release, deploy, or merge the integration branch into Patch 18 without explicit project-owner authorization.
4. Never discard, clean, reset, or stash unrelated work without explicit authorization.
5. Do not perform unrelated refactors, formatting sweeps, dependency upgrades, asset replacements, or feature redesigns.
6. Preserve feature behaviour unless a change is necessary for Patch 18 or network compatibility.
7. Use the network implementation introduced by `banking` as the canonical post-merge architecture unless repository evidence proves that a later Patch 18 implementation supersedes it.
8. Do not retain two parallel network systems as a temporary shortcut.
9. Avoid intermediate commits that leave the integration branch uncompilable.
10. Complete only one milestone at a time and stop after reporting its result.
11. Update the compatibility matrix and integration log during every milestone.
12. Record all assumptions as assumptions until verified from code or runtime evidence.

## Merge commit policy

For a feature merge milestone, use a no-fast-forward, no-commit merge so conflicts and required compatibility patches can be completed before one validated merge commit is created:

```bash
git merge --no-ff --no-commit <feature-branch>
```

After the merge is staged but before committing:

- resolve conflicts deliberately;
- replace obsolete network calls with the canonical banking network API;
- compile the client and dedicated-server source sets;
- run the required tests;
- inspect the final staged diff;
- update the project log and compatibility matrix;
- create one merge commit only after all milestone gates pass.

Do not commit a known-broken merge merely to preserve an intermediate state.

If a milestone cannot be completed safely, abort the merge and report the blocker instead of committing partial work.

## Network compatibility contract

The post-banking codebase must have one coherent network architecture. Every network-using feature must be audited against the following areas.

### Registration

Verify:

- packet or payload types are registered exactly once;
- registration occurs in the correct mod lifecycle phase;
- clientbound and serverbound directions are explicit and correct;
- payload identifiers are globally unique;
- registration order is deterministic where order matters;
- no branch-local initializer silently replaces or bypasses the canonical registrar;
- no obsolete channel or packet registry remains reachable.

### Encoding and decoding

Verify:

- every payload uses the canonical codec or stream-codec pattern;
- encoder and decoder field order is identical;
- nullable and optional values are represented consistently;
- registry-aware values use the correct registry context;
- identifiers, block positions, item stacks, components, enums, and collections use safe bounded encoding;
- malformed or oversized payloads fail safely;
- no payload depends on client-only classes during common or server loading.

### Handling and thread safety

Verify:

- handlers execute work on the correct game thread;
- serverbound messages validate the sending player and current context;
- clientbound handlers do not access unavailable worlds, menus, entities, or block entities;
- handlers tolerate a closed screen, unloaded chunk, removed entity, or stale block entity;
- duplicate or delayed messages do not corrupt state;
- client-only logic is isolated from dedicated-server class loading.

### State ownership and synchronization

Verify:

- the server remains authoritative for gameplay state;
- clients request actions rather than directly asserting trusted results;
- login, respawn, dimension change, menu opening, tracking start, chunk loading, and data mutation trigger synchronization where required;
- persistent data is not accidentally replaced by transient client state;
- menu/container data and custom payload data do not race or overwrite each other;
- block entity, entity, capability, attachment, saved-data, and player data synchronization follows one documented source of truth.

### Security and validation

Verify every serverbound request checks, as applicable:

- sender exists;
- sender is alive and in the correct dimension;
- target position or entity is loaded and valid;
- distance is reasonable;
- menu or interaction context is correct;
- item, tool, permission, ownership, and skill requirements are satisfied;
- quantity and numeric values are bounded;
- identifiers are allow-listed or registry-valid;
- the requested mutation is legal on the server.

### Protocol compatibility

Record:

- protocol version or acceptance predicate;
- whether mixed old/new clients are intentionally supported;
- how incompatible versions fail;
- whether payload identifiers changed;
- whether packet IDs are positional or identifier-based;
- whether saved data or serialized menu state changed;
- whether a migration is necessary for existing worlds or players.

Unless the repository already defines a supported compatibility policy, the default objective is one Patch 18 client and server protocol, not indefinite compatibility with pre-integration builds.

## Validation tiers

Each milestone must run the highest applicable tier.

### Tier 1: Static validation

- inspect the full staged diff;
- search for obsolete network classes, methods, payload IDs, registrars, and imports;
- search for duplicate registrations;
- search for client-only references in common/server code;
- confirm no accidental unrelated files are included.

### Tier 2: Build validation

Run the repository's authoritative commands for:

- Java compilation;
- resource processing;
- client source set;
- dedicated-server source set;
- tests;
- data generation or validation when the changed feature requires it.

Do not invent commands when project scripts already define them.

### Tier 3: Automated feature validation

Run existing tests for:

- banking;
- farming;
- blacksmithing;
- shrines and monoliths;
- banners and dye tubs;
- network encoding and handling;
- persistence and reload behaviour.

Add narrowly scoped tests when a compatibility change would otherwise be unprotected.

### Tier 4: Runtime smoke validation

At minimum, validate:

- a client can join a dedicated server;
- no registration mismatch or payload decode error appears;
- login and initial synchronization complete;
- each integrated feature can perform its primary networked interaction;
- disconnect and reconnect do not corrupt state;
- server restart preserves expected persistent state;
- opening and closing each relevant menu repeatedly does not desynchronize;
- invalid or stale requests fail safely.

### Tier 5: Multiplayer regression validation

Validate with at least two players where practical:

- state is scoped to the correct player;
- nearby players receive only intended updates;
- one player's menu or action does not overwrite another player's state;
- tracking-range and dimension changes synchronize correctly;
- feature actions remain authoritative under latency or rapid repeated input.

## Milestone plan

## Milestone 0: Repository and branch audit

### Goal

Establish a trustworthy starting state without modifying gameplay code.

### Tasks

- locate the repository root;
- confirm the exact Patch 18 branch name;
- confirm all five feature branches exist;
- record each branch HEAD;
- inspect ahead/behind relationships and merge bases;
- confirm the working tree is clean in the dedicated integration environment;
- verify the project toolchain and build commands;
- identify network-related packages, registrars, payloads, handlers, menu sync code, and persistence systems in Patch 18 and each feature branch;
- create or update the initial entries in the compatibility matrix and integration log;
- identify any branch dependency that could alter the proposed merge order.

### Acceptance gates

- no gameplay source changes;
- no merge performed;
- branch names and HEADs recorded;
- build commands identified;
- initial network inventory recorded;
- blockers and uncertainties reported.

### Completion behaviour

Stop and report. Do not begin Milestone 1 automatically.

## Milestone 1: Patch 18 baseline validation and integration branch creation

### Goal

Prove the unmodified release baseline is healthy before feature integration.

### Tasks

- check out the verified Patch 18 branch;
- create `patch-18-network-integration` from its current HEAD;
- run the authoritative clean build and test suite;
- run a dedicated-server startup smoke test when supported;
- record pre-existing warnings and failures separately from new failures;
- commit only documentation changes if the project owner has explicitly authorized that commit.

### Acceptance gates

- integration branch points to the verified Patch 18 baseline;
- baseline build result recorded;
- baseline tests recorded;
- pre-existing failures clearly distinguished;
- no feature branch merged.

### Completion behaviour

Stop and report. Do not begin Milestone 2 automatically.

## Milestone 2: Banking network architecture audit

### Goal

Understand the banking branch's network changes before merging them.

### Tasks

- compare `banking` against its merge base with Patch 18;
- identify all added, removed, renamed, and modified network infrastructure;
- trace banking messages from registration through encoding, handling, state mutation, and response synchronization;
- identify repository-wide APIs that other branches will need to adopt;
- identify protocol, saved-data, menu, block-entity, player-data, or login-sync changes;
- identify any non-network banking changes that may conflict with Patch 18;
- document the intended canonical network contract in the compatibility matrix;
- prepare a precise merge-risk report.

### Acceptance gates

- no banking merge yet;
- network architecture and migration points documented;
- likely conflicts and obsolete APIs identified;
- no unsupported claim that a branch is compatible merely because it compiles.

### Completion behaviour

Stop and report. Do not begin Milestone 3 automatically.

## Milestone 3: Merge banking and establish the canonical network baseline

### Goal

Integrate the complete banking feature and its network architecture into the integration branch.

### Tasks

- start from the validated integration HEAD;
- merge `banking` with `--no-ff --no-commit`;
- resolve Patch 18 conflicts without discarding valid Patch 18 changes;
- ensure the banking network registrar becomes the sole canonical network path;
- remove or adapt obsolete Patch 18 network code made redundant by banking;
- validate bank menus, balance synchronization, bank-box actions, check creation, persistence, reconnect, and dedicated-server loading;
- inspect for client-only references and unsafe serverbound trust;
- update the compatibility matrix and integration log;
- create one validated merge commit.

### Acceptance gates

- clean build;
- dedicated server starts;
- client joins successfully;
- banking interactions synchronize correctly;
- no duplicate packet registration;
- no obsolete parallel network system remains active;
- no unrelated feature change;
- one merge commit created only after validation.

### Completion behaviour

Stop and report. Do not begin Milestone 4 automatically.

## Milestone 4: Post-banking repository-wide compatibility inventory

### Goal

Determine exactly how each remaining branch must be adapted to the new network baseline.

### Tasks

For `Farming`, `blacksmithing`, `shrines-monolith`, and `banners-dyetub`:

- compare each branch with its merge base;
- list every network registration, payload, codec, send call, handler, menu sync path, block-entity update path, player-data sync path, and persistence dependency;
- classify each touchpoint as compatible, obsolete, conflicting, or requiring runtime verification;
- identify shared files that will conflict with the merged banking baseline;
- define branch-specific validation scenarios;
- update the compatibility matrix with exact file and symbol references.

### Acceptance gates

- every remaining branch has an evidence-based compatibility assessment;
- known migration work for Farming and blacksmithing is explicit;
- supposedly unaffected branches still receive a documented network audit;
- no feature branch merged during this milestone.

### Completion behaviour

Stop and report. Do not begin Milestone 5 automatically.

## Milestone 5: Merge Farming and migrate its network usage

### Goal

Integrate `Farming` while preserving all farming behaviour under the canonical banking network system.

### Required audit areas

- crop and fruit-tree state synchronization;
- planting, tending, harvesting, uprooting, and protected-interaction requests;
- flower species and colour persistence;
- tall-crop state;
- farming skill and mastery checks;
- menu or screen interactions;
- block entity or saved-data updates;
- multiplayer visibility and per-player state;
- login, chunk-load, and reconnect synchronization.

### Tasks

- merge `Farming` with `--no-ff --no-commit`;
- resolve conflicts against the banking network baseline;
- replace obsolete Farming network registration and send/handle calls;
- preserve server authority and all existing farming validation rules;
- add or update narrowly scoped tests for migrated network paths;
- run farming, common network, dedicated-server, persistence, and multiplayer checks;
- update the compatibility matrix and integration log;
- create one validated merge commit.

### Acceptance gates

- all Farming payloads use the canonical network architecture;
- no duplicate or legacy Farming registrar remains;
- existing worlds retain farming data where required;
- colour, species, growth, protection, skill, and regrowth state synchronize correctly;
- no cross-player state leakage;
- clean build and runtime smoke validation;
- one validated merge commit.

### Completion behaviour

Stop and report. Do not begin Milestone 6 automatically.

## Milestone 6: Merge blacksmithing and migrate its network usage

### Goal

Integrate `blacksmithing` while preserving all server-authoritative crafting, interaction, and state synchronization.

### Required audit areas

- forge, anvil, station, or workstation menus;
- recipe selection and execution requests;
- heat, progress, durability, material, quality, or skill state;
- block entity synchronization;
- inventory and item-stack mutation;
- player skill or profession data;
- sound, animation, particle, and visual update messages;
- reconnect and chunk reload behaviour.

### Tasks

- merge `blacksmithing` with `--no-ff --no-commit`;
- resolve conflicts against the canonical network baseline;
- replace obsolete blacksmithing network code;
- ensure serverbound crafting actions validate context, distance, ownership, ingredients, quantities, and permissions;
- add or update tests for migrated paths;
- run blacksmithing, common network, dedicated-server, persistence, and multiplayer checks;
- update the compatibility matrix and integration log;
- create one validated merge commit.

### Acceptance gates

- all blacksmithing messages use the canonical network architecture;
- no client-authoritative item or skill mutation;
- no duplication or loss caused by repeated or stale requests;
- clean build and runtime smoke validation;
- one validated merge commit.

### Completion behaviour

Stop and report. Do not begin Milestone 7 automatically.

## Milestone 7: Merge shrines-monolith and verify compatibility

### Goal

Integrate `shrines-monolith` without disturbing the canonical network system or the feature's multiblock and administrative behaviour.

### Required audit areas

- multiblock placement and removal;
- variant or model cycling with the interior decorator tool;
- block entity or persistent state;
- client rendering updates;
- interaction permissions;
- chunk boundaries and neighbour updates;
- any administrative action packets.

### Tasks

- merge `shrines-monolith` with `--no-ff --no-commit`;
- resolve conflicts without reintroducing an obsolete network path;
- migrate any network calls discovered during the audit;
- validate shrine and monolith placement, persistence, model changes, collision behaviour, and dedicated-server loading;
- update the compatibility matrix and integration log;
- create one validated merge commit.

### Acceptance gates

- no duplicate network registration;
- all administrative mutations are server-authoritative;
- shrine and monolith state persists and synchronizes;
- existing geometry and collision fixes remain intact;
- clean build and runtime smoke validation;
- one validated merge commit.

### Completion behaviour

Stop and report. Do not begin Milestone 8 automatically.

## Milestone 8: Merge banners-dyetub and verify compatibility

### Goal

Integrate `banners-dyetub` while preserving item, colour, block, menu, and persistence behaviour.

### Required audit areas

- dye-tub interaction requests;
- colour selection and application;
- inventory consumption and output;
- banner or item state synchronization;
- block entity or menu state;
- multiplayer visibility;
- reconnect and chunk reload behaviour.

### Tasks

- merge `banners-dyetub` with `--no-ff --no-commit`;
- resolve conflicts without restoring obsolete network infrastructure;
- migrate any affected payloads or handlers;
- validate server authority, inventory safety, colour persistence, and multiplayer synchronization;
- update the compatibility matrix and integration log;
- create one validated merge commit.

### Acceptance gates

- canonical network system used exclusively;
- no item duplication, lost inputs, or client-trusted colour mutation;
- state persists across reconnect and server restart;
- clean build and runtime smoke validation;
- one validated merge commit.

### Completion behaviour

Stop and report. Do not begin Milestone 9 automatically.

## Milestone 9: Repository-wide network consolidation audit

### Goal

Prove that the integrated branch contains one coherent network implementation.

### Tasks

- search the entire repository for obsolete packet classes, channel builders, registrars, protocol constants, send helpers, handlers, and imports;
- inspect every payload identifier for uniqueness;
- inspect every serverbound handler for validation;
- inspect every clientbound handler for side safety and stale-context handling;
- confirm registrations occur exactly once;
- confirm no feature initializes networking independently;
- confirm no accidental mixed protocol remains;
- confirm all changed persistent data has a documented migration decision;
- remove only proven dead compatibility code;
- add regression tests for any gap uncovered;
- update the compatibility matrix and integration log.

### Acceptance gates

- one canonical network architecture;
- no duplicate payload IDs or registration;
- no known client-only dedicated-server load hazard;
- no unresolved high-risk compatibility finding;
- full clean build and test suite passes, except explicitly documented pre-existing failures.

### Completion behaviour

Stop and report. Do not begin Milestone 10 automatically.

## Milestone 10: Cross-feature runtime and multiplayer regression

### Goal

Validate the integrated feature set as a system rather than as isolated branches.

### Minimum scenarios

- start a dedicated server from a clean runtime;
- join with one client and then a second client;
- test login synchronization and reconnect;
- use the bank and verify balances, bank-box contents, checks, and persistence;
- perform representative Farming interactions and verify state for both players;
- perform representative blacksmithing interactions and verify inventory and state authority;
- place and modify shrines and monoliths;
- use banners and dye tubs;
- change dimensions and return;
- unload and reload relevant chunks;
- restart the server and re-check persistent state;
- repeat menu open/close and rapid action sequences;
- inspect logs for registration, decode, thread, class-loading, invalid-player, stale-menu, or missing-target errors.

### Acceptance gates

- no disconnect caused by payload mismatch;
- no cross-player state leakage;
- no state loss after reconnect or restart;
- no duplicate item or currency mutation;
- no dedicated-server class-loading failure;
- no regression in unrelated Patch 18 functionality;
- all observed issues recorded with reproduction steps.

### Completion behaviour

Stop and report. Do not begin Milestone 11 automatically.

## Milestone 11: Final merge-readiness review

### Goal

Prepare a complete evidence package for project-owner approval without merging into Patch 18.

### Tasks

- verify the integration working tree is clean;
- list all milestone commits and merge parents;
- compare the final branch against the original Patch 18 HEAD;
- summarize all network migrations by feature;
- summarize tests and runtime validation;
- list pre-existing failures separately from integration regressions;
- list unresolved low-risk limitations;
- confirm documentation is current;
- confirm no push, release, deployment, or Patch 18 merge has occurred;
- provide the exact recommended final merge command, but do not execute it.

### Acceptance gates

- complete audit trail;
- clean integration branch;
- no unresolved critical or high-risk issue;
- final diff contains only intended integrations and compatibility work;
- owner can approve or reject the final merge from the evidence provided.

### Completion behaviour

Stop. Await explicit project-owner authorization for any merge into Patch 18.

## Post-integration fixes

After the integrated work has been approved and merged into Patch 18, newly discovered integration defects should be fixed from the current Patch 18 source of truth using short-lived hotfix branches.

Do not repair a released integration defect only on an old feature branch. Backport a hotfix to a still-active feature branch only when that branch will continue to receive development and would otherwise reintroduce the defect later.

## Required milestone report format

Every milestone report must include:

```text
Milestone:
Status:
Working directory:
Active branch:
Patch 18 reference:
Starting HEAD:
Ending HEAD:
Feature branch HEADs consulted:
Commit created:
Commit hash:
Commit parents:
Files changed:
Network symbols added:
Network symbols removed:
Network symbols migrated:
Build commands:
Test results:
Runtime validation:
Compatibility matrix updates:
Pre-existing failures:
New failures:
Unresolved risks:
Assumptions verified:
Assumptions still open:
Prohibited actions confirmed not performed:
Recommended next milestone:
```

Use `Not applicable` rather than omitting a field.

## Definition of project completion

The project is complete only when:

- all five feature branches have been integrated;
- `banking` is the canonical network architecture;
- Farming and blacksmithing have been migrated to that architecture;
- shrines-monolith and banners-dyetub have been verified and patched where necessary;
- builds, tests, dedicated-server startup, client connection, persistence, and multiplayer regression pass;
- the compatibility matrix and integration log are complete;
- the integration branch is clean;
- the project owner has reviewed the final evidence and explicitly authorized the final merge.
