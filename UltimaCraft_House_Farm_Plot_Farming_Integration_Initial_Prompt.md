# Initial Agent Prompt
## UltimaCraft House Farm Plot Farming Integration

You are implementing the House Farm Plot Farming Integration feature in the UltimaCraft Minecraft mod.

Before changing code, read these two project-root documents completely:

1. `UltimaCraft_House_Farm_Plot_Farming_Integration_Design.md`
2. `UltimaCraft_House_Farm_Plot_Farming_Integration_Codex_Playbook.md`

Treat those documents as the feature contract and implementation workflow.

# Repository and Git Context

The primary local UltimaCraft repository folder is:

```text
britannia_mod
```

The current integration/source-of-truth branch is:

```text
patch-18
```

Everything has recently been consolidated onto `patch-18`.

The primary `britannia_mod` folder must remain available for other agents.

**Do not implement this feature directly inside the main `britannia_mod` working directory.**

Before doing any implementation work, you must create:

1. a dedicated feature branch based on `patch-18`; and
2. a separate Git worktree for that feature branch.

Recommended feature branch:

```text
feature/house-farm-plot-farming
```

Recommended sibling worktree folder:

```text
britannia_mod-house-farm-plot
```

Use the repository's established naming convention if it clearly differs, but preserve the isolation requirement.

# Required Git Workflow

Start by inspecting the primary checkout:

```bash
cd britannia_mod
git status --short
git branch --show-current
git log -5 --oneline
git worktree list
git rev-parse patch-18
git log -1 --oneline patch-18
```

Do not discard, reset, stash, clean, or overwrite unrelated changes in `britannia_mod`. They may belong to another agent.

Do not switch the primary checkout merely for this feature if another agent is using it.

Create the feature worktree from the current local `patch-18` commit.

Conceptually:

```bash
git -C britannia_mod worktree add \
  -b feature/house-farm-plot-farming \
  britannia_mod-house-farm-plot \
  patch-18
```

Adjust paths to the actual filesystem.

If the branch already exists, inspect it and attach the worktree without overwriting the branch.

After creation, enter the new worktree and verify:

```bash
git status --short
git branch --show-current
git log -1 --oneline
git worktree list
```

Record:

```text
Base branch: patch-18
Base commit: <hash>
Feature branch: <branch>
Feature worktree: <path>
```

From that point onward:

- perform all feature edits inside the dedicated worktree;
- run all feature builds/tests from the dedicated worktree;
- commit all feature work to the feature branch;
- leave the primary `britannia_mod` folder available for other agents;
- do not merge the feature branch into `patch-18`;
- do not push unless explicitly instructed;
- do not remove the feature worktree when finished.

# Core Feature Contract

The existing `HouseFarmPlotBlock` must become compatible with UltimaCraft's existing crop farming and flower systems.

Do not create a second farming system.

Reuse the real existing project implementations for:

- farming/planting;
- crop definitions;
- crop growth;
- crop models;
- flower definitions and `FlowerBlock`;
- flower models;
- harvesting;
- drops;
- farming skill/mastery hooks;
- multiplayer synchronization;
- ownership/protection;
- the existing `FarmingHoeItem`.

The house plot has unique geometry, so existing plant models must be positioned correctly on its actual planting surface without creating duplicated per-house-plot crop models.

# Special House-Plot Behavior

A house farm plot has a persistent plant assignment.

A newly initialized plot must be assigned to the existing red poppy by default.

Once a crop or flower is assigned:

```text
grow -> mature -> harvest -> same plant remains assigned -> regrow
```

It must continue growing that same plant indefinitely through repeated harvest cycles.

Harvesting must never clear the persistent assignment.

The assignment may be intentionally cleared only with the existing `FarmingHoeItem`.

Using `FarmingHoeItem` must clear the crop/flower assignment while leaving `HouseFarmPlotBlock` itself intact.

After a hoe reset, the plot is intentionally empty and must remain empty across chunk reloads and server restarts. The default red poppy must not automatically return.

The next valid crop or flower planted on the cleared plot becomes the new persistent assignment.

A player must not be able to silently replace an assigned plant with another seed/flower without first clearing the plot with `FarmingHoeItem`.

# Important State Distinction

The implementation must distinguish:

```text
UNINITIALIZED
ASSIGNED(<canonical plant ID>)
CLEARED
```

Legacy house farm plots that do not yet contain the new data should be treated as `UNINITIALIZED` and initialized once with the existing red poppy.

A plot that was intentionally cleared by the player must remain `CLEARED`.

# Work Method

Start with M0 from the playbook.

M0 includes both worktree verification and repository discovery.

Do not write feature code until you have confirmed:

```text
current workspace = dedicated feature worktree
feature branch = based on patch-18
primary britannia_mod checkout = left available
```

Then inspect and document the actual current architecture for:

- `HouseFarmPlotBlock`;
- `FarmingHoeItem`;
- the ordinary farming block/surface;
- planting;
- crop identity;
- crop growth stages;
- `FlowerBlock`;
- red poppy registry/data;
- harvesting;
- farming skill hooks;
- crop/flower rendering;
- tall crops;
- house/lot protections;
- persistence conventions.

Do not guess class names, registry IDs, model paths, or APIs.

Use the repository as the source of truth.

# Architecture Constraints

Prefer the smallest shared abstraction/refactor that lets `HouseFarmPlotBlock` participate in the current farming pipeline.

Do not maintain a second hardcoded list of supported crops if the normal farming system already owns that decision.

Do not copy harvest logic.

Do not copy growth logic.

Do not copy flower logic.

Do not duplicate every crop model to compensate for the house plot's geometry. Find a shared render/model anchor or transform seam if technically possible.

The persistent assignment must belong to durable house-plot state, not solely to a transient crop block that may disappear during growth or harvest transitions.

# Regression Requirement

Ordinary farming must behave exactly as before.

Any shared farming refactor requires regression validation.

# Milestones

Implement in the playbook order:

```text
M0 worktree verification + repository discovery
M1 persistent assignment state
M2 default red poppy initialization
M3 shared planting compatibility
M4 geometry/model anchoring
M5 harvest persistence and same-crop regrowth
M6 FarmingHoeItem reset and reassignment
M7 tall crops / edge cases
M8 persistence, multiplayer, protection, regressions
M9 documentation and final evidence
```

Compile/test after every meaningful milestone.

Create focused commits on the feature branch as milestones are completed.

Never push unless explicitly instructed.

Never merge back into `patch-18` unless explicitly instructed in a later request.

Do not modify unrelated user or agent changes.

# Final Git Verification

Before reporting completion, run appropriate equivalents of:

```bash
git status --short
git branch --show-current
git log --oneline patch-18..HEAD
git diff --stat patch-18...HEAD
git worktree list
```

Confirm the implementation remains isolated on the feature branch/worktree.

# Final Response

When implementation is complete, report:

1. base branch;
2. base commit;
3. feature branch;
4. feature worktree path;
5. what changed;
6. the architecture chosen and why;
7. all files changed;
8. tests/build commands run and their results;
9. manual gameplay validation performed;
10. any unverified or remaining limitations;
11. commit hashes created;
12. confirmation that the feature was not merged into `patch-18`;
13. confirmation that the primary `britannia_mod` checkout was left available for other agents.

Do not claim a test passed unless you actually ran it.

Begin by creating/verifying the isolated feature worktree, then complete M0.
