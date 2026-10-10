# Source publication / Patch 19 synchronization — 2026-10-10

Task: publish current sanitized Patch 18 source to main through its permitted workflow, then synchronize local/remote Patch 19 onto **full development Patch 18**, preserving unique work and version. No deployment, installed JAR changes, live-world edits, server restarts, desktop inputs, release tags or GitHub Releases.

The owner reports the cumulative 0.1.8d fixes are live. This is trusted owner context, not a new deployment/acceptance audit. Earlier recovery and manual records are retained with their historical scope; no repeat is requested.

## Starting identities and recovery

Fetched starting refs:

| Ref | Starting SHA | Local recovery ref |
| --- | --- | --- |
| local/remote patch-18 | `69935aedbbb3ca918c30fba3b03e9c65110deead` | `refs/backup/2026-10-10-publication/local-patch-18` and `remote-patch-18` under the same prefix |
| local patch-19 | `27bce5d38236994299199f7d0f43a2f867fb1304` | `refs/backup/2026-10-10-publication/local-patch-19` |
| remote patch-19 | `339cc70bb78cb1b99de114e57ca0f8e75f8e8a80` | `refs/backup/2026-10-10-publication/remote-patch-19` |
| remote/local main | `f039c4b259d925872618bfe64ee574b20776856c` | `refs/backup/2026-10-10-publication/remote-main` and `local-main` under the same prefix |
| old public review branch | `fd90bff209d47addf6667c4576bd2053890127f2` | `refs/backup/2026-10-10-publication/remote-codex/patch18-0.1.8d-source` |

Local Patch 19 is clean in `tmp/medallion-patch19-port`, with 31 commits ahead of remote and none unique to remote. Shared base with Patch 18 is `a4ed88cf5a03435c01eed049810898ea98b18ebf`. An isolated `codex/patch19-sync-20261010` worktree preserves the original until rebase validation completes. Five grape/vegetation/medallion/doc patches are already integrated in Patch 18 (negative `git cherry` entries); do not duplicate them. Unique quest-platform fixtures, documentation and permanent reward proof/reconciliation changes must survive. Preserve relevant merge structure and inspect range-diff/content equivalence. Existing Patch 19 version is **0.1.8c**, inherited from its previous base; retain that recorded version rather than inventing a new release. Patch 18 stays **0.1.8d**.

## Public CI repair

Existing PR #501 failed three RunUO test-class initializers in a standalone public checkout. They walked ancestor directories looking for excluded internal `docs/vendor-trader-economy` JSON. Earlier nested local public validation could accidentally read canonical internal docs, concealing the portability failure.

The two complete committed JSON contracts are copied unchanged into `src/test/resources/release-contracts/vendor-trader-economy`. The three consumers load those classpath resources, fail if missing and retain **all original test bodies/assertions**. When internal canonical docs exist at the explicit Gradle project root, the shared loader also fails on semantic fixture drift; it never searches ancestors. Test resources do not enter the production JAR. No tests are suppressed, assumptions broadened or assertions weakened.

Recorded fixture hashes:

- `runuo_ultimacraft_mapping.json`: `623005866d8b389181251b62e4bb2de9836821750b0d18ae2a41dda0a2061caa` (1,028,690 bytes).
- `economic_vendor_rollout.json`: `764d089eb663bd2555178cd5664579ecb70faf27e12189f52b1ef0403a972ba2` (90,652 bytes).

Affected development RunUO tests pass. Final public validation runs from a clean worktree **outside canonical repository ancestors**, ensuring excluded documents cannot satisfy lookup. Fresh build/JUnit/artifact checks and required public GameTests are recorded in task logs/receipt; no passing outcome is presumed by this preparation record.

## Sanitization and main workflow

Use the established [policy](../hotfixes/combined-0.1.8d/SANITIZATION.md) and [prior exclusion manifest](../hotfixes/combined-0.1.8d/PUBLIC_SNAPSHOT.json), applied to the **current** committed Patch 18 tree. Every case-insensitive Markdown file, internal docs/evidence, local configuration/cache/runtime/temporary artifact and established private collection tool is excluded. Required source/assets/test fixtures/build files/runtime dependencies/license inputs remain. Required machine-readable economy fixture copies are test inputs, not exported internal-document trees. No retained-file exceptions are planned.

`prepare_snapshot.py` builds a temporary Git index from exact source objects/modes/types, verifies every retained entry, preserves freshly fetched main as parent and refuses conflicting independent main changes. The selected source, exact retained/excluded manifest, tree and commit go into the receipt directory. A prepared snapshot or open review is not updated main. Existing PR #501 may be superseded by a fresh main-child review so its stale source/failed validation is not represented as current.

Fresh main ruleset **17373102** requires verified signatures, linear history and restricted updates. Its only exception is administrator-role bypass, explicitly prohibited by the owner. No configured Git signer was found; SSH agent is unavailable, and installed GPG cannot open its key database because its configured keybox daemon is unavailable. No signing identity/key or protection change is invented. Requested existing authorized signing/non-bypass publication instructions remain a dependency. Continue public checks and independent Patch 19 synchronization; do not attempt a merge using administrator bypass.

## Actual completion receipt and rollback

Final actual refs, snapshot/build/test/range-diff results, review URL and merged/unmerged state are recorded under:

```text
C:\projects\britannia\mod\Britannia_Mod\tmp\source-publication-20261010
```

`FINAL_RECEIPT.json` is written after actual publication/rebase results. It is outside tracked commits to avoid self-referential ref hashes. No final publication pass is claimed by this pre-operation handoff.

Recovery refs remain local and protected from overwrite. To inspect original history without disturbing a worktree, create a new recovery branch from the desired `refs/backup/...` ref. To roll Patch 19 back deliberately: first verify its worktree is clean and save subsequent work, restore the local branch to the recorded old-local recovery ref, then use an **explicit lease against the currently verified remote SHA** if remote rollback is authorized. Do not use plain force, overwrite newer remote work, or force main/Patch 18. Main recovery here is informational; no main rollback/history rewrite is authorized.
