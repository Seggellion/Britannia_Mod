# UltimaCraft / Britannia NeoForge — Patch 18 Closeout Project Playbook

**Repository:** `C:\projects\britannia\mod\Britannia_Mod`  
**Primary development branch:** `patch-18`  
**Target production branch:** `main`  
**Next development branch:** `patch-19`  
**Remote:** `origin` → `https://github.com/Seggellion/Britannia_Mod.git`  
**Execution model:** Autonomous, evidence-driven, sub-agent assisted  
**Project rule:** Patch branches retain development documentation. Production `main` contains zero Markdown files.

---

## 1. Mission

Close Patch 18 safely and permanently, publish the complete accepted NeoForge release, clean obsolete local development state without losing recoverable work, and open Patch 19 from the finalized Patch 18 development lineage.

The finished repository must satisfy all of the following:

1. `patch-18` is finalized, clean, fully tested, and pushed to `origin/patch-18`.
2. `main` is a sanitized public snapshot whose tree is exactly final Patch 18 minus every tracked Markdown path, case-insensitively.
3. `main` does **not** gain the private Patch 18 development ancestry during publication.
4. Every non-Markdown path on `main` has the same Git object, type, and mode as final Patch 18.
5. The exact sanitized `main` candidate passes the same release acceptance gate as final Patch 18.
6. `patch-19` is created from the finalized Patch 18 commit, not from sanitized `main`, and is pushed to `origin/patch-19`.
7. No Patch 19 feature work is performed during this project.
8. Obsolete Claude/Codex worktrees and local branches are removed only after preservation is proven.
9. Valuable worlds, diagnostics, recovery refs, archives, artwork sources, configuration, screenshots, stashes, and recovery tags are not destroyed by cleanup.
10. The primary checkout finishes on a clean `patch-19`.

This is a release/closeout project, not a feature project.

---

## 2. Discovery anchors

The following values came from the 2026-09-14 discovery and are **comparison anchors only**. They are mutable facts and must be revalidated before action.

| Symbol | Discovery meaning | Discovery value |
|---|---|---|
| `P0` | Local `patch-18` HEAD at discovery | `8efcce7abfe292a58682d2b424f33a5c88d17dc8` |
| `R18` | `origin/patch-18` at discovery | `1213551dac8a28dea0a0ef15bcae9e020529e530` |
| `B0` | `origin/main` at discovery | `5c0b917226848192e40b376cae719057e70cd662` |
| `P` | Final accepted Patch 18 commit | Unknown until finalized |
| `B` | Revalidated public-main parent | Unknown until publication gate |
| `M` | Sanitized production snapshot | Unknown until constructed |
| `P19` | Initial Patch 19 commit | Must equal `P` |

At discovery time:

- local `patch-18` was 24 commits ahead of `origin/patch-18`;
- no local `main` existed;
- neither local nor remote `patch-19` existed;
- the primary checkout was dirty;
- all active Claude/Codex production branch heads except the housing documentation branch were already ancestors of local `patch-18`;
- a unique housing smoke-test documentation commit existed at `a6e0c8d32459c021256efccbb7472b954dd381ea`;
- the current acceptance record contained one watering-can-related JUnit failure;
- six test classes contained unconditional Markdown dependencies;
- five secondary worktrees existed and none was proven safe for unconditional deletion;
- recorded live acceptance still contained open items.

Never assume those facts are still current.

---

## 3. Non-negotiable release architecture

### 3.1 Patch branch policy

`patch-18` is the permanent development/archive lineage for Patch 18.

It may contain:

- playbooks;
- discovery reports;
- implementation reports;
- acceptance evidence;
- handoffs;
- historical notes;
- project Markdown documentation.

`patch-18` must not be deleted after release.

### 3.2 Production main policy

`main` is a sanitized public/production lineage.

The final `M` tree must equal:

> every tree entry from `P` whose path does not end in `.md`, case-insensitively

For retained paths, equality means the same:

- path;
- object ID;
- Git mode;
- Git object type.

No historical "sanitation manifest" may be reused to remove additional files. The zero-Markdown rule is the **only** tree exclusion for this release.

### 3.3 Public ancestry policy

Do not merge `patch-18` into `main`.

Construct `M` so that:

- its sole parent is the revalidated current `origin/main` commit `B`;
- its tree is exactly `P` minus Markdown;
- no force-push or history rewrite is required.

This intentionally preserves public history without introducing the full private development lineage as new `main` ancestry.

### 3.4 Patch 19 policy

Create `patch-19` from exact final `P`.

Do **not** create Patch 19 from `M`.

This preserves documentation and development history from Patch 18 while keeping production sanitation independent.

Initially:

```text
local patch-19 == origin/patch-19 == P
```

No Patch 19 feature commit is authorized by this playbook.

---

## 4. Autonomous operating model

The project should run without repeated owner prompts.

The orchestrator may make technical decisions when repository evidence and this playbook provide a deterministic answer. It must not ask for confirmation merely because a task is large, tedious, or multi-step.

However, autonomy does **not** authorize fabricated evidence, destructive guessing, or silently weakening release requirements.

### 4.1 Primary operator

One primary operator owns:

- branch mutation;
- integration into `patch-18`;
- final commit creation;
- final release gates;
- `M` construction;
- local `main`;
- all remote pushes;
- worktree removal;
- branch deletion;
- final checkout.

Only the primary operator may publish remote refs.

### 4.2 Sub-agent model

Use sub-agents aggressively for independent analysis, verification, focused remediation, and evidence review.

Sub-agents should be given narrow scopes and explicit deliverables.

Recommended sub-agents:

| Agent | Scope | Mutation permission |
|---|---|---|
| A — Topology Auditor | Revalidate refs, ancestry, branches, worktrees, recovery refs, remote drift | Read-only |
| B — Preservation Auditor | Inventory ignored/untracked files, worlds, diagnostics, archives, duplicate material, recovery paths | Read-only |
| C — Asset/Watering-Can Agent | Resolve the eight dirty/new asset files and watering-can resource contract | Isolated worktree only if implementation needed |
| D — Markdown Compatibility Agent | Audit all test/tool Markdown dependencies and implement branch-policy-compatible tests | Isolated worktree only |
| E — Acceptance Evidence Agent | Reconcile historical/manual acceptance evidence and identify truly open live gates | Read-only |
| F — Housing/Documentation Agent | Integrate/preserve unique housing and untracked documentation; prove duplicates | Isolated worktree only if needed |
| G — Release Verification Agent | Independently verify `P`, `M`, parity, test results, provenance, and remote readiness | Read-only |
| H — Cleanup Auditor | Re-prove per-worktree/per-branch cleanup eligibility after publication | Read-only |

Additional sub-agents may be created for a focused defect, but do not create agents whose scopes overlap enough to cause competing edits.

### 4.3 Concurrency rules

The following may run in parallel after the initial state freeze:

- preservation analysis;
- asset analysis;
- Markdown/test dependency analysis;
- housing/document analysis;
- historical acceptance reconciliation.

Do not run these concurrently:

- two agents modifying the same branch;
- final `P` integration and another production mutation;
- sanitized `main` construction and modification of `P`;
- remote publication and cleanup;
- worktree removal while any agent may still be using it.

### 4.4 Shared-state safety

Sub-agents must not casually modify the primary checkout.

If a sub-agent needs to implement code:

1. create or use an isolated, named temporary worktree/branch;
2. make the smallest coherent commit;
3. run focused tests;
4. return commit SHA, changed files, test result, and reasoning;
5. let the primary operator integrate it;
6. do not push it unless the primary operator explicitly delegates that exact ref.

The primary operator remains the sole authority for release topology.

---

## 5. Hard stop conditions

The operator should continue autonomously through ordinary failures by diagnosing and fixing them.

Stop **before the first remote push** and produce a blocker report only if one of these conditions remains after reasonable remediation:

1. `origin/main` moved and its new commits cannot be reconciled safely with the proposed public snapshot.
2. `origin/patch-18` moved in a way that is not an ancestor of the intended `P`.
3. genuinely unique, irreplaceable work cannot be preserved with confidence.
4. the final Patch 18 build/GameTest gate cannot be made green without changing intended game behavior beyond closeout scope.
5. `M` cannot be proven byte/object-equivalent to `P` for all non-Markdown entries.
6. a genuinely required live/manual acceptance gate remains unresolved and cannot be satisfied from available executable tests or trustworthy recorded evidence.
7. credentials/secrets are discovered in material that would be published.
8. cleanup classification cannot determine whether a file is unique versus reproducible.

Do not stop cleanup merely because an artifact might be useful someday. If it is reproducible, superseded, duplicated, generated, cached, or already represented in Git/release evidence, it should be deleted.

Do not ask for approval merely because a known fix is required.

Do not fabricate a pass for a manual/visual gate. If such a gate is the only remaining blocker and no trusted evidence resolves it, stop publication and emit the smallest exact human QA checklist needed to unblock it.

---

## 6. Evidence and scratchpad policy

Create a new **ignored/untracked** execution evidence directory outside the release tree, preferably:

```text
C:\projects\britannia\patch18-closeout\<YYYYMMDD-HHMMSS>\
```

Use it for:

- before-state inventory;
- logs;
- branch/worktree manifests;
- file hashes;
- preservation manifests;
- focused test output;
- full acceptance output;
- parity output;
- push receipts;
- cleanup ledger;
- final audit.

Do not place transient execution evidence into `main`.

The root playbook itself is intended to be committed on the patch lineage and therefore naturally excluded from `main` by the zero-Markdown snapshot.

Use a scratchpad such as:

```text
<closeout-dir>\SCRATCHPAD.txt
```

Update it throughout execution with:

- current milestone;
- immutable SHAs;
- completed agent assignments;
- unresolved blockers;
- decisions and evidence locations.

Do not use a Markdown scratchpad if the file could accidentally become part of the release tree.

---

# M0 — Revalidate and freeze the execution state

## Goal

Prove what exists **now** before changing anything.

## Primary operator tasks

Run a non-pruning fetch:

```powershell
git fetch origin
```

Record:

```powershell
git status --short --branch
git rev-parse --show-toplevel
git rev-parse HEAD
git remote -v
git branch -vv
git branch -a
git worktree list --porcelain
git stash list
git tag --list
```

Query remote heads directly:

```powershell
git ls-remote origin refs/heads/main refs/heads/patch-18 refs/heads/patch-19
```

Inventory preservation refs:

```powershell
git for-each-ref refs/patch18-closeout/ --format="%(refname) %(objectname)"
```

Confirm:

- no merge/rebase/cherry-pick/revert is in progress;
- no unmerged index entries exist;
- branch topology is understood;
- remote `main` and `patch-18` relationships are understood;
- all registered worktrees are known.

## Sub-agent A — Topology Auditor

Independently report:

- exact current `patch-18`, `origin/patch-18`, `origin/main`, and `origin/patch-19` SHAs;
- ahead/behind counts;
- all local branches not reachable from `patch-18`;
- all secondary worktree HEADs;
- all unique commits;
- any drift from the discovery anchors.

## Acceptance

M0 passes when all mutable discovery facts are refreshed and no unexplained branch/worktree state exists.

If remote drift is ordinary and reconcilable, update the scratchpad and continue. If it changes the publication ancestry assumption materially, invoke the hard-stop rule.

---

# M1 — Preserve all valuable local material before mutation

## Goal

Make loss impossible before integration, cleanup, or worktree removal.

## Sub-agent B — Preservation Auditor

Audit:

- primary dirty files;
- all untracked files;
- ignored files in every worktree;
- `run/` worlds, screenshots, mods, shaderpacks, config, server lists;
- Rowan "New World";
- `tmp/gameplay-bugfixes`;
- `tmp/gameplay-closeout-20260909`;
- prior closeout bundles;
- recovery refs;
- stash;
- `pre-rebase-m7-backup`;
- historical closeout checkpoint/manifests;
- locally valuable Java/Python diagnostics.

Produce a preservation manifest containing:

```text
path
type
size
hash where practical
tracked/untracked/ignored
unique/duplicate
preservation location
safe-to-delete-original? yes/no
reason
```

### Required preservation defaults

Retain unless individually superseded and proven safe:

- primary saved worlds;
- screenshots;
- mods;
- shaderpacks;
- configuration;
- server lists;
- Rowan client world;
- unique diagnostic sources;
- historical closeout manifests/checkpoints;
- recovery refs;
- stash;
- `pre-rebase-m7-backup`;
- historical remote branches.

Do not blanket-delete:

```text
run/
tmp/
.claude/
.codex/
.gradle/
```

## Empty residues

The discovery identified these as empty/unregistered candidates:

```text
C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\patch18-gameplay-bugfixes
C:\projects\britannia\mod\Britannia_Mod\.codex\worktrees\public-release-adaptive-roof
```

Recheck them. They may be removed later only if still empty and unregistered.

## Acceptance

M1 passes when every valuable or uncertain item has a verified retained location and recovery route.

No worktree is removed in M1.

---

# M2 — Resolve Patch 18 assets and documentation

M2 may run in parallel with M3 and M4 after M1 preservation is complete.

## 2A. Dirty/new asset set

The discovery observed these asset paths:

```text
src/main/resources/assets/britannia_mod/models/item/broccoli.json
src/main/resources/assets/britannia_mod/models/item/watering_can.json
src/main/resources/assets/britannia_mod/models/item/watering_can_full.json
src/main/resources/assets/britannia_mod/textures/item/crops/barley/barley.png
src/main/resources/assets/britannia_mod/models/item/watering_can_empty.json
src/main/resources/assets/britannia_mod/textures/item/crops/broccoli.png
src/main/resources/assets/britannia_mod/textures/item/water_32.png
src/main/resources/assets/britannia_mod/textures/item/watering_can_metal_64.png
```

Revalidate the list first.

## Sub-agent C — Asset/Watering-Can Agent

Determine for each file:

- whether it is referenced;
- whether the reference resolves;
- image dimensions and format;
- whether dimensions match project conventions and intended use;
- whether it is a source artifact or runtime asset;
- whether it supersedes a committed placeholder;
- whether model texture-slot semantics are valid;
- whether focused tests cover the intended behavior.

The discovery recorded a failure:

```text
PlantingPresentationTest.fullCanOverrideResolvesToAReplaceableResourceWithoutRecursion
```

with an assertion involving full-model texture slot `"2"`.

Resolve the **actual resource contract**, not merely the assertion.

A valid fix may update implementation, model JSON, or an over-specific test, but the resulting test must still verify meaningful resource resolution and prevent recursive/broken model fallback.

### Artwork decision rule

Integrate an asset when all of the following are true:

- it is clearly part of the intended Patch 18 state;
- its references are valid;
- its dimensions/format are valid for the project;
- it passes applicable focused tests;
- no higher-confidence committed asset intentionally supersedes it.

If an asset is clearly experimental or malformed and no safe intended transformation is established by repository conventions, preserve it outside the candidate and do not silently "fix" artwork by lossy transformation.

### Barley caution

The discovery recorded a large byte-size increase for `barley.png`. Verify actual pixel dimensions and runtime suitability rather than assuming the file is correct because it is a PNG.

## 2B. Documentation reconciliation

The discovery found untracked project documents and one unique housing documentation commit.

## Sub-agent F — Housing/Documentation Agent

Revalidate:

- `internal/patch-18-housing-smoke-test`;
- unique commit `a6e0c8d32459c021256efccbb7472b954dd381ea`;
- current untracked Markdown;
- exact duplicate documents;
- Rowan worktree documents.

### Default documentation policy

1. Legitimate unique Patch 18 documentation belongs on the Patch 18 lineage.
2. Exact duplicates should not be kept twice merely because both exist.
3. If a unique document has an obvious canonical project documentation directory, prefer the established project location.
4. Do not invent extensive reorganizations during closeout.
5. A documentation-only housing commit that remains legitimate should normally be incorporated into `patch-18` before final `P`, unless its content is already present identically.
6. The root duplicate of the gameplay bugfix playbook may be removed once exact normalized duplication is re-proven.
7. Rowan duplicated worktree documents do not need a second copy if exact Git blob/content identity is proven.
8. The root `PATCH_18_CLOSEOUT_PROJECT_PLAYBOOK.md` is retained on `patch-18` and inherited by `patch-19`.

## Integration

Sub-agents return isolated commits.

The primary operator integrates approved asset/document changes into `patch-18`.

After integration:

```powershell
git status --short
```

must contain no unexplained owner work.

## Acceptance

M2 passes when every dirty/untracked asset/document has a recorded disposition:

```text
INTEGRATED
PRESERVED OUTSIDE CANDIDATE
DUPLICATE REMOVED
INTENTIONALLY RETAINED UNTRACKED
BLOCKED
```

No item may disappear without evidence.

---

# M3 — Make the project compatible with zero-Markdown `main`

M3 may run in parallel with M2.

## Goal

Make the **same non-Markdown code** work on both documentation-rich `patch-18` and zero-Markdown `main`.

Do not create a special main-only code patch.

## Known direct dependencies

Revalidate these discovery findings:

```text
src/test/java/com/seggellion/britannia_mod/packaging/DeployableArtifactPackagingTest.java
src/test/java/com/seggellion/britannia_mod/NewAssetsCrossSystemAuditTest.java
src/test/java/com/seggellion/britannia_mod/wildresource/WildResourceDocumentationTest.java
src/test/java/com/seggellion/britannia_mod/bannerdyeing/GeneratedBannerCatalogueTest.java
src/test/java/com/seggellion/britannia_mod/bannerdyeing/Milestone14RRemovalAndPreservationTest.java
src/test/java/com/seggellion/britannia_mod/bannerdyeing/ParallelLargeGateECloseoutTest.java
```

Known Markdown inputs included:

```text
README.md
docs/new-assets/ASSET_IMPORT_MANIFEST.md
docs/wild-resources.md
content/banner_catalogue_status.md
```

Also inspect `BannerScaffoldTool --check`.

## Sub-agent D — Markdown Compatibility Agent

Perform a complete search for:

- `.md` literals in Java, Gradle, scripts, and tests;
- file reads that resolve to Markdown indirectly;
- packaging assertions that confuse documentation presence with production resource validity;
- generators/checkers that would recreate Markdown in `main`.

### Required design principle

Separate:

- documentation-presence assertions

from:

- runtime resource assertions;
- packaging assertions;
- generated-data integrity assertions;
- source/resource coverage.

A zero-Markdown production tree may legitimately skip or conditionalize **documentation-only** checks.

It must not bypass substantive runtime/package/resource verification.

Use existing repository patterns for optional documentation where appropriate.

Do not:

- disable entire suites;
- catch-and-ignore missing files broadly;
- make `main` tests weaker for non-document production behavior;
- create different Java/test blobs only on `main`.

Implement fixes on Patch 18, return a focused commit, and run focused tests.

## Acceptance

M3 passes when:

- the known six classes no longer require Markdown for substantive production checks;
- no other unconditional Markdown dependency blocks the complete release gate on a zero-Markdown tree;
- focused tests pass both with docs present and in a controlled no-doc simulation where practical.

---

# M4 — Reconcile live/manual acceptance

M4 may begin in parallel as a read-only evidence task.

## Sub-agent E — Acceptance Evidence Agent

Search the current Patch 18 history and retained evidence for the latest authoritative result for:

- display-case two-client/reload/restart rendering;
- moongate Photon rendering;
- no-shader comparison;
- actual moongate travel;
- watering-can empty/full appearance;
- Rowan live acceptance;
- weapon views;
- crate lifecycle;
- fertile-dirt survival loop;
- blessed items / Starfarer's Medallion;
- market-stall visuals;
- any later closeout handoff that supersedes these.

For each gate classify:

```text
PASS — exact current behavior is supported by trustworthy evidence
SUPERSEDED — prior blocker no longer applies and why
NOT REQUIRED FOR THIS RELEASE — repository policy explicitly supports this
OPEN — current acceptance evidence is insufficient
```

Historical acceptance on a different commit is not automatically acceptance of final `P`.

Where the behavior is deterministic and testable in automation, add or use an automated regression instead of relying only on prose.

Where the requirement is inherently visual/manual, do not fabricate an automated pass.

## Release rule

Before publication, every release-required live gate must be:

- passed on sufficiently equivalent final behavior; or
- explicitly supported as an accepted limitation by existing owner/project evidence.

If a truly required visual/manual gate remains open and cannot be executed by the available environment, invoke the hard-stop policy before remote publication and output a minimal exact QA checklist.

## Acceptance

M4 passes when there is no silently open release-required acceptance item.

---

# M5 — Finalize immutable Patch 18 commit `P`

## Preconditions

M1–M4 have passed.

## Primary operator

Integrate all approved isolated commits onto `patch-18`.

Resolve conflicts deliberately.

Do not squash away useful Patch 18 documentation merely to make `main` cleaner.

Confirm:

```powershell
git status --short --branch
```

is clean.

Record:

```powershell
$P = git rev-parse HEAD
git show --no-patch --format=fuller $P
git rev-list --parents -n 1 $P
git ls-tree -r --full-tree $P
```

`P` is now frozen.

Any change to source, tests, resources, assets, or tracked docs after freezing creates a **new** `P` and invalidates downstream acceptance evidence.

---

# M6 — Run the complete established acceptance gate on `P`

Use JDK 21.

From a clean checkout at exact `P`:

```powershell
.\gradlew.bat clean build artifactIdentity runGameTestServer --no-configuration-cache --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Patch 18 release acceptance failed' }
```

Do not use:

```text
-PallowDirty
-Pdev
```

for release acceptance.

## Required evidence

Record:

- JUnit total;
- JUnit failures;
- JUnit errors;
- skipped tests and reasons;
- required GameTest count;
- GameTest passes/failures;
- generated build identity;
- `git.head`;
- `git.dirty`;
- branch;
- version;
- deployable JAR identity;
- SHA-256 of accepted deployable artifact;
- full command log.

Require:

```text
git.head == P
git.dirty == false
JUnit failures == 0
JUnit errors == 0
all required GameTests executed and passed
```

If the gate changes the working tree unexpectedly, diagnose that before continuing.

## Independent review

Sub-agent G independently reviews the output and confirms the exact candidate identity.

---

# M7 — Construct sanitized production snapshot `M`

## Preconditions

`P` passed M6.

Refresh remote state again:

```powershell
git fetch origin
git ls-remote origin refs/heads/main refs/heads/patch-18
```

Set:

```text
B = exact current origin/main
```

If `B` differs from the M0 baseline, inspect the new public commits and confirm `B` remains an ancestor/public baseline compatible with the release.

Do not silently adopt unexplained drift.

## Construction requirements

`M` must:

- have exactly one parent: `B`;
- have a tree equal to `P` minus case-insensitive `.md`;
- preserve every retained Git object ID and mode;
- contain zero Markdown;
- be created without text-copy normalization.

### Preferred Git-native method

Use a temporary index, not filesystem copy/paste.

Conceptually:

1. load `P`'s tree into a temporary index;
2. enumerate tree paths ending in `.md`, case-insensitively;
3. remove only those paths from the temporary index;
4. write the resulting tree;
5. create commit `M` using `git commit-tree` with sole parent `B`;
6. establish/update local `main` at `M` only after validation of the constructed commit.

The exact implementation may vary, but it must preserve Git objects/modes and must not rewrite `P`.

Do not reuse prior exclusion manifests.

---

# M8 — Prove exact `P` → `M` parity

Run an authoritative tree comparison.

Use the following logic, with exact full SHAs:

```powershell
@'
import subprocess, sys

p, m, b = sys.argv[1:]

def git(*args):
    return subprocess.check_output(["git", *args])

def tree(ref):
    result = {}
    for record in git("ls-tree", "-r", "-z", "--full-tree", ref).split(b"\0"):
        if record:
            metadata, path = record.split(b"\t", 1)
            result[path] = metadata
    return result

pt, mt = tree(p), tree(m)

expected = {
    path: metadata
    for path, metadata in pt.items()
    if not path.lower().endswith(b".md")
}

assert not any(path.lower().endswith(b".md") for path in mt), \
    "Markdown remains on main"

assert mt == expected, \
    "Non-Markdown path, object, type, or mode differs"

parents = git("rev-list", "--parents", "-n", "1", m).decode().split()
assert parents == [m, b], \
    "Main candidate does not have the approved sole public parent"

subprocess.run(["git", "merge-base", "--is-ancestor", b, p], check=True)

print("PASS: exact non-Markdown parity, zero Markdown, approved lineage")
'@ | python - $P $M $B

if ($LASTEXITCODE -ne 0) {
    throw 'Production parity verification failed'
}
```

Also inspect:

```powershell
git diff --no-ext-diff --no-textconv --no-renames --name-status $P $M
```

Every reported difference must be a deletion of a Markdown path.

The Python tree-map equality is authoritative.

Do not substitute JAR hashes for source-tree parity.

---

# M9 — Run complete acceptance on exact `M`

Create/use a clean worktree checked out at local `main` pointing to `M`.

Run the same command:

```powershell
.\gradlew.bat clean build artifactIdentity runGameTestServer --no-configuration-cache --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Sanitized main acceptance failed' }
```

Require:

```text
git.head == M
git.dirty == false
JUnit failures == 0
JUnit errors == 0
all required production GameTests passed
```

Documentation-only skips may differ only where explicitly designed by M3.

Unexpected loss of substantive coverage is a failure.

If `M` requires a code/test fix:

1. do **not** patch `main` directly;
2. apply the fix to `patch-18`;
3. create a new `P`;
4. rerun M6;
5. reconstruct `M`;
6. rerun M8 and M9.

After testing, rerun the parity proof and zero-Markdown check.

Sub-agent G independently reviews readiness.

---

# M10 — Publication gate

Before the first push, re-fetch and query exact remote refs.

Require all of the following:

```text
P acceptance = PASS
M parity = PASS
M zero Markdown = PASS
M acceptance = PASS
live acceptance = resolved
origin/patch-18 expected ancestry = PASS
origin/main == approved B
no unexpected remote patch-19
primary preservation = PASS
```

Set command-level push behavior to avoid incidental tag publication:

```powershell
git -c push.followTags=false ...
```

Never use:

```text
--all
--mirror
--force
--force-with-lease
```

for this closeout.

---

# M11 — Publish Patch 18

Push only exact `P`:

```powershell
git -c push.followTags=false push --porcelain origin "$P`:refs/heads/patch-18"
```

If PowerShell quoting/environment handling differs, use an equivalent explicit refspec.

Then verify independently:

```powershell
git ls-remote origin refs/heads/patch-18
```

The advertised remote SHA must equal `P`.

If not, stop dependent publication.

---

# M12 — Publish sanitized `main`

Ensure local `main` points to exact accepted `M`.

Push only exact `M`:

```powershell
git -c push.followTags=false push --porcelain origin "$M`:refs/heads/main"
```

Verify:

```powershell
git ls-remote origin refs/heads/main
```

The advertised SHA must equal `M`.

Re-inspect the remote-tracking tree after fetch:

```powershell
git fetch origin
git rev-parse origin/main
git ls-tree -r --name-only origin/main
```

Verify again:

- zero Markdown;
- parent = approved `B`;
- non-Markdown tree parity against `P`.

Do not consider main publication complete until the remote state itself is verified.

---

# M13 — Create and publish Patch 19

Create local `patch-19` at exact `P`.

If no local branch exists:

```powershell
git branch patch-19 $P
```

If a branch unexpectedly exists, do not overwrite it blindly. Reconcile why.

Push and set upstream:

```powershell
git -c push.followTags=false push -u origin patch-19:refs/heads/patch-19
```

Verify:

```powershell
git rev-parse patch-19
git rev-parse origin/patch-19
```

Both must equal `P`.

Do not add any Patch 19 feature commit.

---

# M14 — Aggressive worktree, branch, and disk-space cleanup

Cleanup begins only after M11–M13 succeed.

The goal of M14 and M15 is not merely to leave Git tidy. The goal is to reclaim as much local disk space as practical while preserving only material that is actually unique, irreplaceable, or necessary for recovery.

## Cleanup philosophy

Default disposition after successful publication:

```text
DELETE
```

An item survives cleanup only if at least one of these is true:

1. it contains unique production work not represented by retained Git history;
2. it contains an irreplaceable owner-created source asset;
3. it is the explicitly current QA/world state that the owner still needs;
4. it is required to recover from the just-completed publication;
5. it is a small, intentional recovery ref/tag/stash whose disk cost is negligible relative to its safety value;
6. deletion would remove information that cannot reasonably be regenerated or reconstructed.

"Could be useful later" is not sufficient retention justification.

"Contains ignored files" is not sufficient retention justification.

"Was created by Claude/Codex" is not sufficient retention justification.

Generated, cached, duplicated, superseded, or reproducible material should be deleted.

## Sub-agent H — Cleanup Auditor

Re-audit every secondary worktree and major disk consumer after publication.

For each item report:

```text
path
size
type
unique content?
represented elsewhere?
reproducible?
required for recovery?
action: DELETE / KEEP / EXTRACT-THEN-DELETE
expected reclaimed bytes
```

Sort cleanup candidates by reclaimable size.

### Worktrees

A secondary worktree should normally be removed when:

- its commits are already represented by `P`, `M`, or retained refs;
- it has no unique source change that must still be integrated;
- any genuinely unique artifact has been extracted first.

Ignored GameTest worlds, generated run directories, copied configs, build output, or test reports do **not** justify retaining an entire worktree.

If a worktree contains one valuable item and gigabytes of disposable content:

1. copy/archive only the unique item to the closeout preservation directory;
2. hash/verify it;
3. remove the entire worktree.

### Previously observed secondary worktrees

Revalidate and aggressively retire where possible:

```text
.claude/worktrees/neoforge-quest-item-handin-f8695f
.claude/worktrees/rowan-farmer-exploration-97e04a
C:\projects\britannia\patch18-closeout\...\candidate\patch18-m2
C:\projects\britannia\patch18-closeout\...\publication\public-release
C:\projects\britannia\release-builds\starfarer-m11
```

The Rowan worktree previously contained a client world. Determine whether that world is still uniquely useful. If it is required, extract/compress only that world and then remove the worktree. If it is obsolete test state, delete it with the worktree.

Use normal Git removal:

```powershell
git worktree remove <path>
```

Use `--force` only after the cleanup audit proves remaining differences are generated/duplicated/disposable.

After removals:

```powershell
git worktree prune
git worktree list --porcelain
```

### Local branch cleanup

Delete obsolete local implementation/integration branches once their content is represented.

Expected deletion candidates include previously discovered Claude/Codex/integration branches whose heads are ancestors of final `P`.

Retain only the branches needed for current development/recovery, principally:

```text
patch-18
main
patch-19
```

The housing documentation branch may be deleted once its unique document is incorporated or otherwise preserved.

Use:

```powershell
git branch -d <branch>
```

Use:

```powershell
git branch -D <branch>
```

when the branch is intentionally non-merged but independent content comparison proves it is superseded or preserved.

Do not delete unrelated historical remote branches as part of local disk cleanup unless they have local worktrees/copies consuming disk. Remote refs themselves consume negligible space.

### Recovery refs and Git objects

Keep small safety references such as:

```text
pre-rebase-m7-backup
stash
refs/patch18-closeout/*
```

through the immediate publication audit.

After publication and cleanup evidence is complete, determine whether old preservation refs are keeping large unreachable object graphs alive.

Run size diagnostics such as:

```powershell
git count-objects -vH
git rev-list --objects --all
```

If old `refs/patch18-closeout/*`, obsolete stash entries, or backup tags are the only references retaining very large superseded histories, the cleanup auditor should identify the reclaim potential.

Do not remove the just-completed release recovery anchor during the same operation.

Older redundant closeout refs may be retired if their contents are already represented by permanent branches/tags and the final recovery evidence is retained.

After deliberate ref retirement, expire only genuinely obsolete reflog/object history and run Git maintenance if doing so materially reclaims disk:

```powershell
git reflog expire --expire=now --all
git gc --prune=now
```

This is allowed only after all retained refs and recovery requirements have been explicitly verified.

---

# M15 — Aggressive generated-artifact and local-data cleanup

## Goal

Recover substantial disk space after successful publication.

The discovery previously observed approximately:

```text
run/      ~2.12 GB
.gradle/  ~709 MB
build/    ~411 MB
tmp/      ~111 MB
```

Re-measure current sizes and prioritize the largest disposable categories.

## Delete by default after release evidence is retained

Delete:

- project `build/`;
- project-local `.gradle/` caches and transient Gradle state that can be regenerated;
- generated GameTest worlds;
- generated test reports already captured in the closeout evidence directory;
- old `run/logs`;
- crash reports no longer needed;
- stale debug logs;
- root build weapon/debug logs;
- superseded JARs;
- copied/development-only mods that can be restored from dependencies/builds;
- generated shader/cache files;
- Python `__pycache__`;
- temporary extraction directories;
- obsolete `tmp/` experiments;
- duplicate Markdown copies;
- old patch/reject files;
- stale Claude/Codex scratch output;
- old publication/candidate worktree directories after Git deregistration;
- redundant closeout bundles that are superseded by the final closeout evidence;
- obsolete generated screenshots used only as transient test evidence once final evidence is retained;
- generated server/client test configs that are reproducible;
- any empty residue directories.

## `run/` policy

Do not preserve the entire `run/` directory by default.

Classify its contents.

Retain only:

- a specifically identified current owner world that is still needed;
- irreplaceable screenshots/source evidence;
- irreplaceable local config not reconstructible from repository/environment documentation.

Delete:

- generated GameTest worlds;
- obsolete development worlds;
- old client/server logs;
- crash reports;
- cached assets;
- copied mods;
- transient shader caches;
- server lists/configs that are already reproducible and not intentionally retained;
- other generated runtime state.

If one current world must be kept, move it into a clearly named retained location outside `run/`, optionally compress it, verify the archive, and then delete the rest of `run/`.

Do not keep hundreds of megabytes of stale worlds merely because they once existed.

## `.gradle/` policy

The project-local `.gradle/` directory is regenerable.

After all builds/tests/publication are complete, delete project-local Gradle caches unless a small file is specifically required by repository tooling.

Do not delete the user's global Gradle installation/cache outside the project unless explicitly within the repository cleanup scope.

## `build/` policy

Delete the full project `build/` tree after:

- final P/M logs are copied to the evidence directory;
- the accepted deployable JAR is copied to the intended retained release location;
- its SHA-256 is recorded.

The build tree itself is not a release archive.

## `tmp/` policy

Treat `tmp/` as disposable by default.

For each unique source/script/evidence item:

1. determine whether it is still useful;
2. if yes, copy only that file to the final evidence/archive directory;
3. hash it;
4. delete the original temp tree.

Delete historical temporary closeout material that is redundant with the final report and permanent Git history.

## Previous closeout bundles

Old `C:\projects\britannia\patch18-closeout\...` directories can consume substantial disk.

Keep:

- the final current closeout package;
- only older evidence that contains unique information not present in Git or the final package.

Delete older candidate/publication directories, generated reports, copied JARs, old test worlds, and duplicate manifests after uniqueness verification.

## Release builds

Inspect `C:\projects\britannia\release-builds`.

Retain:

- the final accepted Patch 18 deployable artifact if this directory is the chosen canonical local release archive;
- any genuinely unique historical release that the project intentionally preserves.

Delete superseded intermediary Patch 18 candidate builds and stale worktree copies.

## Cleanup size target

The cleanup auditor must report:

```text
bytes before cleanup
bytes deleted
bytes retained
bytes after cleanup
top retained disk consumers
```

There is no requirement to retain a large file merely to make cleanup safer.

If a large retained item is not demonstrably unique or currently required, delete it.

## Final cleanup safety rule

Never delete:

- unpushed unique source code;
- the final accepted release artifact unless another verified copy exists;
- the only copy of an owner-created source asset;
- credentials needed for operation without a documented recovery path.

Everything else is eligible for deletion when it is reproducible, duplicated, superseded, or obsolete.

# M16 — Final checkout and final audit

Switch the primary checkout to `patch-19`.

Require:

```powershell
git status --short --branch
```

to be clean, unless an intentionally preserved local-only item is documented and cannot safely be relocated. Prefer relocating preserved local-only evidence outside the repository rather than hiding it.

Record exact final identities:

```powershell
git rev-parse patch-18
git rev-parse origin/patch-18
git rev-parse main
git rev-parse origin/main
git rev-parse patch-19
git rev-parse origin/patch-19
git worktree list --porcelain
```

Verify:

```text
local patch-18 == origin/patch-18 == P
local main == origin/main == M
local patch-19 == origin/patch-19 == P
```

Verify final `main`:

- zero `.md`, case-insensitive;
- sole parent is `B`;
- exact non-Markdown parity with `P`.

Verify final patch branches:

- `patch-18` retained;
- `patch-19` starts from `P`;
- documentation remains available on development lineage.

Verify cleanup:

- every removed worktree has a preservation proof;
- no unique branch work was lost;
- recovery safety nets remain;
- no remote branch was accidentally removed.

---

## 7. Final expected topology

Conceptually:

```text
development lineage:

... -- Patch 18 history -- P
                         | \
                         |  \__ patch-19
                         |
                         \_____ patch-18

public lineage:

... -- B -- M
           |
           \__ main
```

Where:

```text
tree(M) = tree(P) - all case-insensitive *.md paths
parent(M) = B only
patch-19 = P initially
```

`P` is not a parent of `M`.

---

## 8. Required final report

At project completion produce:

### Release identities

```text
P:
B:
M:
P19:
Patch 18 version:
Accepted JAR:
Accepted JAR SHA-256:
```

### Validation

```text
P full gate:
P JUnit:
P GameTests:
P artifact identity:

M parity:
M Markdown count:
M full gate:
M JUnit:
M GameTests:
M artifact identity:
```

### Remote publication

```text
origin/patch-18:
origin/main:
origin/patch-19:
```

### Preservation

Summarize:

- integrated dirty work;
- preserved external work;
- housing documentation disposition;
- Rowan world disposition;
- diagnostic/recovery material;
- retained recovery refs/tags/stash.

### Cleanup

List:

- removed worktrees;
- retained worktrees and reason;
- deleted local branches;
- retained local branches and reason;
- removed generated artifacts;
- intentionally retained local data.

### Final state

```text
Primary checkout:
Working tree clean?:
patch-18 == origin/patch-18?:
main == origin/main?:
patch-19 == origin/patch-19?:
main Markdown count:
main parity proof:
Patch 19 feature commits created during closeout?:
```

The last field must be:

```text
No
```

---

## 9. Completion definition

This project is complete only when all of these are true:

- final `P` exists and is clean;
- final `P` passes the full release gate;
- final `M` exists with sole parent `B`;
- `M` has zero Markdown;
- `M` exactly matches all non-Markdown Git entries from `P`;
- final `M` passes the full release gate;
- `origin/patch-18 == P`;
- `origin/main == M`;
- local and remote `patch-19 == P`;
- Patch 19 contains no new feature work;
- unique/irreplaceable local state is preserved;
- obsolete/reproducible worktrees, caches, build output, generated worlds, logs, and redundant evidence were aggressively removed;
- cleanup reports the amount of disk space reclaimed;
- primary checkout is clean on `patch-19`;
- a final evidence report records every release identity, test result, preservation decision, and cleanup action.

If any requirement is unresolved, report the exact blocker rather than declaring the closeout complete.
