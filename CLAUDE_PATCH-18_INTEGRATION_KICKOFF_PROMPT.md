# Claude Kickoff Prompt: Consolidate All Relevant Work into `patch-18`

You are working in our Britannia / UltimaCraft Minecraft mod repository.

I want you to run a cautious, multi-milestone Git consolidation project whose target branch is:

```text
patch-18
```

The project must first determine which branches are already represented in `patch-18`, then integrate only the genuinely missing work, validate the resulting branch thoroughly, and finally remove obsolete local worktrees/folders and old local branches to recover disk space.

I am specifically concerned about losing work or accidentally merging the same feature twice. Safety and proof are more important than speed or a tidy Git graph.

A milestone playbook named something similar to:

```text
PATCH-18_INTEGRATION_MILESTONE_PLAYBOOK.md
```

should exist in the repository root. Read it completely before making changes and treat it as the governing plan for this project.

## Current Local Branches

This is the current branch list I have:

```text
  0.1.8
  Farming
  banking
  banners-dyetub
  blacksmithing
+ client-branding
+ codex/wild-reagents
+ feature/guildmaster-service-npc
+ feature/house-farm-plot-farming
+ feature/questengine-remediation
+ feature/vendor-trader-economy
+ grabby-hands
  main
  managed-vegetation
* new-assets
  patch-18
+ patch-18-network-integration
+ patch-18-troubleshooting
  shrines-monoliths
  villa
```

Remember that in normal `git branch` output:

- `*` is the branch checked out in the current worktree.
- `+` means the branch is checked out in another linked worktree.

Do not assume branches without `+` have no old local folder. Some old folders may not be registered Git worktrees.

## Primary Objective

I want `patch-18` to become the authoritative branch containing all intended work from these development branches.

However, before merging anything, you must answer this first:

> Which of these branches have already been merged into, cherry-picked into, squash-merged into, rebased into, superseded by, or otherwise effectively incorporated into `patch-18`?

Do not rely on branch names or `git branch --merged` alone.

For every branch, use a combination of:

```bash
git merge-base --is-ancestor <branch> patch-18
git merge-base patch-18 <branch>
git log --oneline patch-18..<branch>
git cherry patch-18 <branch>
git log --cherry-pick --right-only --no-merges --oneline patch-18...<branch>
git diff --stat patch-18...<branch>
git diff --name-status patch-18...<branch>
```

Inspect actual patches/files where the result is ambiguous.

A branch that is not a topological ancestor can still be fully represented in `patch-18` because of squash merges, cherry-picks, rebases, or later reimplementations. Conversely, a branch that looks old may still contain unique work.

## Safety Requirements Before Any Merge

Before modifying history or deleting anything:

1. Inspect the repository and all worktrees.
2. Record current SHAs, branch tracking state, worktree paths, and dirty states.
3. Run `git fetch --all --prune`.
4. Inspect staged, unstaged, untracked, and relevant ignored files in every worktree.
5. Create and verify a recovery artifact, preferably:
   ```bash
   git bundle create ../britannia-pre-patch18-consolidation.bundle --all
   git bundle verify ../britannia-pre-patch18-consolidation.bundle
   ```
6. Do not begin destructive cleanup unless that recovery artifact verifies successfully.

Do not run `git reset --hard`, `git clean -fdx`, or any equivalent destructive shortcut as routine housekeeping.

## Milestone Workflow

Execute this project milestone by milestone.

### Milestone 0
Freeze and document the starting state. Create the recovery bundle. Make no destructive changes.

### Milestone 1
Build a complete audit matrix for every branch and classify each as one of:

```text
ALREADY_CONTAINED
MERGE_REQUIRED
PARTIALLY_CONTAINED
SUPERSEDED
INTENTIONALLY_EXCLUDED
BLOCKED_DIRTY_WORKTREE
NEEDS_MANUAL_REVIEW
```

Show me the audit before doing broad integration work.

### Milestone 2
Analyze dependencies and establish the safest merge order.

Pay particular attention to possible overlap among:

```text
patch-18-network-integration
patch-18-troubleshooting
feature/vendor-trader-economy
feature/guildmaster-service-npc
feature/questengine-remediation
feature/house-farm-plot-farming
Farming
managed-vegetation
new-assets
shrines-monoliths
banking
blacksmithing
banners-dyetub
client-branding
codex/wild-reagents
grabby-hands
villa
0.1.8
main
```

These names are hints only. Determine the real relationships from the commit graph and changed files.

Do not merge an older branch wholesale if a newer branch already supersedes most of it and doing so would resurrect stale implementations.

### Milestone 3
Prepare a clean `patch-18` integration worktree and run the current baseline build/tests. Record pre-existing failures separately.

### Milestone 4
Integrate required work one logical branch at a time.

After every merge or selective integration:

- inspect the staged/resulting diff,
- search for unresolved conflict markers,
- run `git diff --check`,
- compile,
- run relevant focused tests,
- run broader tests when shared infrastructure changed,
- record the resulting `patch-18` SHA.

Never resolve project conflicts with blanket "ours" or "theirs". Resolve them semantically based on the current architecture.

If an integration becomes questionable, abort that merge and reassess rather than stacking more changes on top of uncertainty.

### Milestone 5
Run full project validation after all intended work is integrated.

Validate the systems represented by the branches, including network integration, farming, house farm plots, managed vegetation, banking, blacksmithing, banners/dye tub, guildmaster NPCs, vendor/trader economy, QuestEngine remediation, new assets, shrines/monoliths, branding, wild reagents, grabby-hands, villa-related work, and any other functionality discovered during the audit.

### Milestone 6
Re-audit every original branch against the final `patch-18`.

A branch must not be deleted until you can classify it as:

```text
SAFE_TO_DELETE_REACHABLE
SAFE_TO_DELETE_PATCH_EQUIVALENT
KEEP_INTENTIONALLY
DO_NOT_DELETE_UNIQUE_WORK_REMAINS
```

For patch-equivalent branches that are not topologically merged, preserve the verified bundle and document why deletion is safe.

### Milestone 7
Only after successful validation and final coverage auditing, remove obsolete worktrees and local folders.

For registered worktrees:

```bash
git worktree list --porcelain
git worktree remove <path>
git worktree prune
```

Do not force-remove a dirty worktree.

For ordinary old project folders that are not registered worktrees, inspect their Git state and untracked/ignored contents before deleting them. Never delete a folder just because its name matches an old branch.

After the associated worktree is removed, delete only branches proven safe.

Prefer:

```bash
git branch -d <branch>
```

Use `git branch -D <branch>` only for a branch that is conclusively patch-equivalent or superseded but is not topologically merged, and only because the verified recovery bundle exists.

Never delete:

```text
patch-18
```

and never delete a branch that still contains unresolved unique work.

### Milestone 8
Run final build/tests again, verify the final worktree and branch lists, and produce a comprehensive final report.

## Expected Audit Report

For each branch, I want something equivalent to:

| Branch | Tip SHA | Worktree | Clean? | Ancestor of patch-18? | Unique commits | Patch-equivalent? | Effective content delta | Decision | Notes |
|---|---|---|---|---|---:|---|---|---|---|

The important part is not the exact formatting. The important part is that every original branch is accounted for and that the decision is supported by Git evidence.

## Required Final Report

At the end, report:

- original `patch-18` SHA,
- final `patch-18` SHA,
- branches that were already contained,
- branches actually merged,
- commits selectively cherry-picked, if any,
- branches determined to be superseded,
- branches intentionally excluded, if any,
- merge conflicts and how they were resolved,
- test/build commands and results,
- worktrees removed,
- old folders removed,
- branches deleted,
- branches intentionally retained,
- recovery bundle path,
- any remaining risks or unresolved unique work.

## Non-Negotiable Rules

Do not delete first and investigate later.

Do not assume `git branch --merged patch-18` is sufficient.

Do not assume "not an ancestor" means "not integrated".

Do not merge every branch merely because it exists.

Do not overwrite newer code with stale branch versions just to eliminate a diff.

Do not silently drop files or functionality to resolve conflicts or pass tests.

Do not discard uncommitted, untracked, or ignored project data.

Do not clean up worktrees or folders until the final branch coverage audit proves it is safe.

If you cannot reach high confidence that something is safe to delete, preserve it and document why. Saving disk space is secondary to preserving work.

Start now with Milestone 0 and Milestone 1. Read the milestone playbook first, inspect the actual repository and worktrees, create the safety snapshot, then produce the complete branch audit matrix before broad merging.
