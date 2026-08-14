# patch-18 Branch and Worktree Integration Milestone Playbook

## Project Objective

Safely consolidate all relevant work from the current local branches and linked Git worktrees into `patch-18`, while proving which branches are already represented in `patch-18` and avoiding duplicate merges, regressions, lost commits, lost untracked files, or accidental deletion of active work.

After the integration is complete and validated, remove obsolete linked worktrees and old local working folders to recover disk space. Cleanup must not begin until every candidate branch has been audited and all valuable work is either:

1. already present in `patch-18`,
2. intentionally merged into `patch-18`,
3. intentionally excluded with a documented reason, or
4. preserved in a backup artifact for later recovery.

The controlling principle for this project is:

> Never infer that a branch is safe to delete merely because Git reports it as merged. Prove both commit reachability and effective content coverage first.

---

## Current Branch Inventory

The starting local branch list is:

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

Interpretation of the markers:

- `*` is the branch checked out in the current worktree.
- `+` indicates a branch checked out in another linked Git worktree.
- An unmarked branch is a local branch that is not currently checked out in any worktree.

Do not assume that branches without `+` have no associated old folder. Some may have ordinary cloned/copy directories rather than registered Git worktrees.

---

# Milestone 0: Freeze, Safety Snapshot, and Recovery Point

## Goal

Create a recoverable state before any merge, rebase, branch deletion, worktree removal, or filesystem cleanup occurs.

## Required Actions

1. Confirm the repository root and active branch.
2. Record:
   - `git status`
   - `git branch -vv`
   - `git worktree list --porcelain`
   - `git remote -v`
   - `git log --oneline --decorate --graph --all --date-order`
3. Fetch all remotes and prune stale remote-tracking refs without deleting local branches:
   ```bash
   git fetch --all --prune
   ```
4. Verify `patch-18` exists locally and determine its relationship to the remote branch, if a remote counterpart exists.
5. Do not hard-reset `patch-18` unless the user explicitly authorizes it.
6. Inspect every registered worktree for:
   - staged changes,
   - unstaged changes,
   - untracked files,
   - ignored files that appear project-specific or valuable.
7. Record any dirty worktree before proceeding.
8. Create a safety backup before destructive cleanup. Preferred options:
   - a Git bundle containing all refs, and/or
   - timestamped backup tags or refs for candidate branch tips.
9. Save the pre-integration inventory in a project report.

## Suggested Backup

A full local bundle is strongly preferred before deleting any branches or worktrees:

```bash
git bundle create ../britannia-pre-patch18-consolidation.bundle --all
git bundle verify ../britannia-pre-patch18-consolidation.bundle
```

If the bundle cannot be created or verified, cleanup must not proceed.

## Milestone Exit Criteria

- Repository state is documented.
- Every linked worktree is known.
- Dirty worktrees are identified.
- A verified recovery artifact exists.
- No destructive operations have occurred.

---

# Milestone 1: Build the Branch Audit Matrix

## Goal

Determine the true status of every candidate branch relative to `patch-18`.

## Candidate Branches

Audit all of these individually:

```text
0.1.8
Farming
banking
banners-dyetub
blacksmithing
client-branding
codex/wild-reagents
feature/guildmaster-service-npc
feature/house-farm-plot-farming
feature/questengine-remediation
feature/vendor-trader-economy
grabby-hands
main
managed-vegetation
new-assets
patch-18-network-integration
patch-18-troubleshooting
shrines-monoliths
villa
```

Do not merge `patch-18` into itself.

## Required Audit Fields

Create a table/report with at least:

| Branch | Tip SHA | Worktree path | Clean? | Ancestor of patch-18? | Unique commits | Patch-equivalent commits | Effective content delta | Dependency notes | Decision |
|---|---|---|---|---|---:|---:|---|---|---|

Allowed decisions:

- `ALREADY_CONTAINED`
- `MERGE_REQUIRED`
- `PARTIALLY_CONTAINED`
- `SUPERSEDED`
- `INTENTIONALLY_EXCLUDED`
- `BLOCKED_DIRTY_WORKTREE`
- `NEEDS_MANUAL_REVIEW`

## Required Git Checks

For each branch, perform multiple checks. No single command is authoritative enough by itself.

### A. Exact commit ancestry

```bash
git merge-base --is-ancestor <branch> patch-18
```

If this succeeds, all commits reachable from `<branch>` are reachable from `patch-18`.

This is strong evidence that the branch has already been merged through normal Git history.

### B. Common base

```bash
git merge-base patch-18 <branch>
```

Record the merge base to understand divergence.

### C. Unique commits by topology

```bash
git log --oneline --decorate patch-18..<branch>
```

This shows commits reachable from the branch but not from `patch-18`.

### D. Patch-equivalence / cherry detection

Use:

```bash
git cherry patch-18 <branch>
```

and/or:

```bash
git log --cherry-pick --right-only --no-merges --oneline patch-18...<branch>
```

This helps detect commits that were squash-merged, cherry-picked, or rebased and therefore may not be ancestors even though their patch content is already present.

### E. File-level delta

Inspect both:

```bash
git diff --stat patch-18...<branch>
git diff --name-status patch-18...<branch>
```

Then inspect relevant patches when the branch appears partially integrated.

### F. Direct tip comparison where useful

```bash
git diff --stat patch-18 <branch>
git diff --name-status patch-18 <branch>
```

Use this carefully. A direct tip diff can include unrelated later changes on either side and is not a substitute for merge-base analysis.

### G. Merge history and prior integration evidence

Search the history for branch names, known feature names, merge commits, and major commits:

```bash
git log patch-18 --merges --oneline --decorate
git log patch-18 --all --grep="<feature or branch term>" --oneline
```

Do not rely only on merge commit messages because squash merges may not preserve branch names.

## Special Rule: Squash/Rebase/Cherry-Pick Detection

A branch is not automatically `MERGE_REQUIRED` merely because:

```bash
git merge-base --is-ancestor <branch> patch-18
```

returns false.

If equivalent changes were incorporated through squash, cherry-pick, reimplementation, or rebasing, mark the branch `ALREADY_CONTAINED`, `SUPERSEDED`, or `PARTIALLY_CONTAINED` only after comparing the actual unique patches and resulting files.

## Milestone Exit Criteria

- Every candidate branch has a documented status.
- No branch remains classified solely from its name or age.
- Already-integrated branches are distinguished from genuinely unmerged branches.
- Dirty/untracked work is identified before merge operations begin.

---

# Milestone 2: Dependency and Merge-Order Analysis

## Goal

Establish an integration order that minimizes conflicts and prevents older branches from overwriting newer shared systems.

## Important Context

Several branches represent broad systems or later remediation work. Their names suggest likely overlap, but Claude must inspect the actual commit graph and changed files rather than assuming dependency relationships.

Pay special attention to:

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

## Merge-Order Principles

1. Prefer branches that are direct descendants of `patch-18` or contain cross-cutting infrastructure before dependent feature branches.
2. Integrate network/core/system refactors before feature branches that depend on them, unless history proves the feature branch already contains the newer infrastructure.
3. For an old feature branch and a newer successor branch, avoid merging the old branch wholesale if its useful changes are already present in the successor.
4. Prefer preserving the newest correct implementation when two branches contain competing versions of the same subsystem.
5. If a branch contains a mix of already-integrated and unique commits, consider selective cherry-picking only when that is safer than a full merge and does not destroy useful history.
6. Do not rewrite published history unless explicitly necessary.
7. Do not rebase branches merely to make the graph look cleaner.
8. Record the reasoning for the selected integration order before performing the first merge.

## Required Output

Produce an ordered merge plan such as:

```text
1. <branch> - reason
2. <branch> - reason
3. <branch> - reason
...
```

The actual order must come from repository inspection.

## Milestone Exit Criteria

- All `MERGE_REQUIRED` and `PARTIALLY_CONTAINED` branches have an integration strategy.
- Dependency-sensitive order is documented.
- Superseded branches will not be merged simply for completeness.

---

# Milestone 3: Prepare `patch-18` as the Integration Target

## Goal

Create a clean, validated target before adding branch work.

## Required Actions

1. Switch to the `patch-18` worktree. If no dedicated worktree exists, create or use one safely.
2. Confirm there are no local changes in that target worktree.
3. Confirm the target SHA and record it.
4. Bring `patch-18` up to the intended remote state only if doing so is appropriate and safe.
5. Run the current project test/build baseline before merging anything.
6. Record any pre-existing failures separately. Do not blame existing failures on later merges.

## Milestone Exit Criteria

- `patch-18` is clean.
- Baseline build/test results are documented.
- Starting SHA is recorded.

---

# Milestone 4: Controlled Integration

## Goal

Integrate one logical branch at a time with checkpoints.

## Rules

For every branch being integrated:

1. Reconfirm its audit decision immediately before merge.
2. Merge only from a clean `patch-18` worktree.
3. Prefer a normal merge when preserving the branch history is useful and safe.
4. Do not use `-X theirs`, `-X ours`, blanket checkout strategies, or automated conflict acceptance for project source files.
5. Resolve conflicts semantically by understanding:
   - which branch contains the newer architecture,
   - whether both changes are required,
   - whether one side supersedes the other,
   - whether generated files should be regenerated rather than hand-merged.
6. After resolving conflicts:
   - inspect the complete staged diff,
   - search for conflict markers,
   - compile,
   - run relevant focused tests,
   - run broader tests when shared infrastructure changed.
7. Commit the merge.
8. Record the resulting `patch-18` SHA.
9. Repeat for the next branch only after the current checkpoint passes.

## Required Conflict Checks

Search for unresolved markers:

```bash
git grep -nE '^(<<<<<<<|=======|>>>>>>>)' -- .
```

Also inspect:

```bash
git status
git diff --check
```

## Failure Rule

If a merge causes uncertainty about data loss or architecture, abort that merge and reassess:

```bash
git merge --abort
```

Do not stack additional merges on top of an unresolved or questionable integration.

## Milestone Exit Criteria

- All branches marked `MERGE_REQUIRED` are integrated or explicitly reclassified.
- Partial branches have had their unique work preserved without duplicating superseded content.
- Every merge has an auditable checkpoint.

---

# Milestone 5: Full Project Validation

## Goal

Prove that consolidated `patch-18` is healthy before any cleanup.

## Required Validation

Run all validation available in the repository, including as applicable:

- full build,
- full automated test suite,
- focused tests for merged systems,
- resource/data generation validation,
- lint/static analysis,
- client/server compilation,
- asset/resource checks,
- runtime smoke tests,
- any repository-specific verification scripts.

For the Minecraft mod, pay special attention to systems represented by these branches, including:

- network integration,
- troubleshooting/remediation,
- farming,
- house farm plot farming,
- managed vegetation,
- banking,
- blacksmithing,
- banners/dye tub,
- guildmaster service NPC,
- vendor/trader economy,
- QuestEngine remediation,
- new assets,
- shrines and monoliths,
- client branding,
- wild reagents,
- grabby-hands,
- villa-related content.

This list is a coverage reminder, not proof of what each branch contains.

## Regression Review

Compare key files against branch versions where conflicts occurred. Confirm that no conflict resolution accidentally reverted newer implementations.

Review deleted or renamed files carefully.

## Milestone Exit Criteria

- Full build passes, or any remaining failures are proven pre-existing and documented.
- Relevant tests pass.
- No unresolved merge markers remain.
- No expected feature appears to have disappeared.
- `patch-18` is clean after validation.

---

# Milestone 6: Post-Merge Branch Coverage Audit

## Goal

Re-run the branch audit against the final `patch-18` so that branch/worktree deletion is based on the final state, not earlier assumptions.

## Required Actions

For every candidate branch, re-run:

```bash
git merge-base --is-ancestor <branch> patch-18
git log --cherry-pick --right-only --no-merges --oneline patch-18...<branch>
git diff --stat patch-18...<branch>
```

Inspect remaining deltas.

Every branch must now be assigned one of:

- `SAFE_TO_DELETE_REACHABLE`
- `SAFE_TO_DELETE_PATCH_EQUIVALENT`
- `KEEP_INTENTIONALLY`
- `DO_NOT_DELETE_UNIQUE_WORK_REMAINS`

## Critical Cleanup Rule

Do not delete a branch with unique commits or unique effective content unless that work is intentionally excluded and the exclusion is documented.

If a branch is patch-equivalent but not topologically merged, preserve the pre-cleanup bundle and record why deletion is still safe.

## Milestone Exit Criteria

- All deletion candidates are proven safe.
- Any retained branch has a documented reason.

---

# Milestone 7: Worktree and Old Folder Cleanup

## Goal

Recover disk space without deleting uncommitted, untracked, ignored, or otherwise unrecoverable work.

## Registered Git Worktrees

Start with:

```bash
git worktree list --porcelain
```

For each worktree being removed:

1. Confirm its branch is safe to delete or intentionally being kept elsewhere.
2. Run `git status --short`.
3. Inspect untracked and ignored project files where necessary.
4. Do not use force removal to bypass unexplained local changes.
5. Remove registered worktrees using:
   ```bash
   git worktree remove <path>
   ```
6. After all legitimate removals:
   ```bash
   git worktree prune
   ```
7. Re-run:
   ```bash
   git worktree list --porcelain
   ```

## Ordinary Old Folders

Some old local project folders may not be registered worktrees.

Before deleting any such folder:

1. Prove what repository/branch it contains.
2. Confirm whether its `.git` is a normal repository, gitfile, or stale copied folder.
3. Check for uncommitted changes.
4. Check for untracked and ignored project content.
5. Compare any unique files with the consolidated repository.
6. Preserve anything not recoverable from Git.
7. Only then remove the obsolete directory.

Never delete folders based solely on matching names.

## Local Branch Cleanup

After worktrees using those branches are removed, delete only branches approved by Milestone 6.

Prefer:

```bash
git branch -d <branch>
```

Use:

```bash
git branch -D <branch>
```

only when the branch is proven patch-equivalent/superseded but Git cannot recognize it as topologically merged, and only after the recovery bundle has been verified.

Do not delete:

- `patch-18`
- any branch intentionally retained,
- any branch with unresolved unique work,
- any branch still checked out by a worktree.

## Milestone Exit Criteria

- Obsolete worktrees are removed.
- Stale worktree metadata is pruned.
- Approved old branches are deleted.
- No uncommitted or unique files were lost.
- Disk-space cleanup is documented.

---

# Milestone 8: Final Verification and Report

## Goal

Leave the repository in a clean, understandable, recoverable state.

## Final Checks

Run and record:

```bash
git status
git branch -vv
git worktree list --porcelain
git log --oneline --decorate --graph --all --date-order
```

Run the final build/test suite again after cleanup.

Verify:

- `patch-18` contains all intended work.
- `patch-18` has no accidental uncommitted changes.
- no deleted worktree still appears in Git metadata,
- no branch intended for preservation was deleted,
- the safety bundle still exists and verifies,
- all intentionally excluded branches/features are documented.

## Required Final Report

Create a report containing:

### Starting State
- original `patch-18` SHA,
- original branches,
- original worktrees,
- dirty worktrees or special files found.

### Branch Audit
For every branch:
- whether it was already contained,
- patch-equivalent,
- partially contained,
- merged,
- superseded,
- intentionally excluded,
- retained.

### Integration
- branches actually merged,
- cherry-picked commits if any,
- conflicts and how they were resolved,
- resulting merge SHAs.

### Validation
- commands run,
- build result,
- test result,
- known pre-existing failures,
- manual verification performed.

### Cleanup
- worktrees removed,
- folders removed,
- local branches deleted,
- branches retained,
- recovery bundle location.

### Final State
- final `patch-18` SHA,
- final branch list,
- final worktree list,
- confirmation that no known unique work was lost.

---

# Non-Negotiable Safety Constraints

Claude must obey all of the following throughout the project:

1. Do not delete a branch because of its name, age, or apparent obsolescence.
2. Do not assume `git branch --merged patch-18` detects squash/cherry-pick/rebase integrations.
3. Do not assume a branch is unmerged merely because it is not an ancestor of `patch-18`.
4. Do not use destructive cleanup before a verified backup exists.
5. Do not delete dirty worktrees.
6. Do not discard untracked or ignored files without examining them.
7. Do not use `git reset --hard` or `git clean -fdx` as routine cleanup.
8. Do not automatically choose "ours" or "theirs" for merge conflicts.
9. Do not silently remove code to make tests pass.
10. Do not merge superseded branches if doing so would reintroduce old implementations.
11. Do not delete `patch-18`.
12. Do not delete any branch or folder until final coverage auditing proves the work is represented or intentionally excluded.
13. If certainty is not possible, preserve the branch/worktree and document the unresolved risk instead of deleting it.

---

# Definition of Done

This consolidation is complete only when all of the following are true:

- every listed branch has been audited against `patch-18`,
- already-merged work has been identified without duplicate merging,
- all desired unique work has been integrated,
- dependency-sensitive conflicts have been resolved semantically,
- the consolidated project builds and passes its applicable tests,
- a final branch coverage audit shows no unintended unique work remains outside `patch-18`,
- a verified recovery bundle exists,
- obsolete worktrees and folders have been safely removed,
- obsolete local branches have been safely removed,
- `patch-18` is the authoritative integrated branch,
- a final written report documents exactly what happened.
