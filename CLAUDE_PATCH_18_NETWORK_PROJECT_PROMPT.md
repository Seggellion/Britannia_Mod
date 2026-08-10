# Claude Project Start Prompt: Patch 18 Network Integration

You are beginning a controlled, milestone-based integration project in the UltimaCraft repository.

Your task is to merge five existing feature branches into the Patch 18 release line while preserving compatibility with the network architecture introduced by the `banking` branch.

## Branches in scope

- `banking`
- `Farming`
- `shrines-monolith`
- `blacksmithing`
- `banners-dyetub`

The `banking` branch contains a major network-system change.

`Farming` and `blacksmithing` are already known to require compatibility patches for the new network system.

`shrines-monolith` and `banners-dyetub` must still receive a complete network-impact audit. Do not assume they are unaffected merely because no patch has yet been identified.

## Target and working branch

The release target is referred to as `patch-18`. First resolve and record the exact existing Git branch name, including its real capitalization and spelling.

The recommended working branch is:

```text
patch-18-network-integration
```

It must be created from the verified current Patch 18 HEAD in a clean, dedicated clone or worktree.

Do not integrate directly on Patch 18.

## Authoritative project documents

Read these files from the repository root before taking any action:

1. `PATCH_18_NETWORK_INTEGRATION_PLAYBOOK.md`
2. `NETWORK_COMPATIBILITY_MATRIX.md`
3. `PATCH_18_INTEGRATION_LOG.md`

Treat `PATCH_18_NETWORK_INTEGRATION_PLAYBOOK.md` as the authoritative scope, milestone order, validation contract, and stop condition.

Update the compatibility matrix and integration log as required by each milestone.

## Operating constraints

- Execute exactly one milestone at a time.
- Begin with Milestone 0 only.
- Do not begin Milestone 1 in the same run.
- Do not change gameplay source code during Milestone 0.
- Do not merge any branch during Milestone 0.
- Do not push, force-push, rebase, tag, release, deploy, or merge into Patch 18.
- Do not alter the feature source branches.
- Do not discard, reset, clean, or stash unrelated work.
- Do not perform unrelated refactors or formatting changes.
- Do not invent repository facts. Verify them.
- Preserve exact branch capitalization, especially `Farming`.
- Use repository build scripts and configuration as the source of truth.
- Report pre-existing failures separately from failures introduced by the integration.
- Stop immediately when the current milestone is complete or blocked.

## Integration policy for later milestones

The default merge order is:

1. `banking`
2. `Farming`
3. `blacksmithing`
4. `shrines-monolith`
5. `banners-dyetub`

For feature merge milestones, the intended method is:

```bash
git merge --no-ff --no-commit <feature-branch>
```

Resolve conflicts and complete required network compatibility patches before creating one validated merge commit. Never commit a knowingly broken intermediate merge.

The banking network implementation becomes the canonical post-merge network system unless repository evidence establishes that Patch 18 already contains a newer authoritative replacement.

Do not retain parallel old and new network systems as a shortcut.

## Milestone 0 assignment

Perform only Milestone 0: Repository and branch audit.

You must:

1. Locate and confirm the repository root.
2. Confirm the current working directory and whether it is a dedicated integration clone or worktree.
3. Inspect the working tree without modifying or cleaning it.
4. Resolve the exact Patch 18 Git reference.
5. Confirm that all five feature branches exist locally or as retrievable remote references.
6. Record the HEAD commit for Patch 18 and every feature branch.
7. Determine merge bases and ahead/behind relationships.
8. Identify whether any branch dependency requires changing the proposed merge order.
9. Verify the project versions and toolchain from repository files, including Minecraft, NeoForge, Java, and GeckoLib where applicable.
10. Identify the authoritative build and test commands.
11. Inventory the network architecture in Patch 18 and each feature branch at a high level.
12. Identify likely network registrars, payload classes, codecs, handlers, send helpers, menu synchronization, block-entity synchronization, player-data synchronization, and persistence touchpoints.
13. Populate the initial `NETWORK_COMPATIBILITY_MATRIX.md`.
14. Append a Milestone 0 entry to `PATCH_18_INTEGRATION_LOG.md`.
15. Report blockers, ambiguities, dirty-tree risks, missing branches, or unexpected divergence.

During this milestone, documentation edits are allowed only in the two tracking documents named above. Do not commit those edits unless the project owner has separately authorized a Milestone 0 documentation commit.

## Milestone 0 success criteria

Milestone 0 passes only when:

- the exact Patch 18 reference is known;
- all branch HEADs are recorded;
- the working environment is safe;
- the build and test entry points are known;
- the initial network touchpoints are documented;
- the proposed merge order has been confirmed or an evidence-based replacement has been proposed;
- no source code or branch history has been changed.

## Required final response

Return a structured report using every field below:

```text
Milestone:
Status:
Working directory:
Repository root:
Dedicated clone/worktree status:
Active branch:
Working tree status:
Exact Patch 18 reference:
Patch 18 HEAD:
banking HEAD:
Farming HEAD:
shrines-monolith HEAD:
blacksmithing HEAD:
banners-dyetub HEAD:
Merge bases:
Ahead/behind summary:
Verified toolchain:
Authoritative build commands:
Authoritative test commands:
Patch 18 network inventory:
banking network inventory:
Farming network inventory:
shrines-monolith network inventory:
blacksmithing network inventory:
banners-dyetub network inventory:
Proposed merge order:
Merge-order evidence:
Compatibility matrix updated:
Integration log updated:
Files changed:
Commit created:
Blockers:
Risks:
Assumptions verified:
Assumptions still open:
Prohibited actions confirmed not performed:
Recommended next milestone:
```

Use `Not applicable`, `None found`, or `Not yet verified` where appropriate. Do not omit fields.

After reporting Milestone 0, stop and await project-owner approval.
