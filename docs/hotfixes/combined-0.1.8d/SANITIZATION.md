## Active public README/licensing policy — 2026-10-10

PR #502 merged the sanitized release source but continued an earlier omission of the public README. The actual exporter is `docs/source-publication-2026-10-10/prepare_snapshot.py`; its historical exclusion input is `PUBLIC_SNAPSHOT.json` beside this record. The historic exclusion map and counts are preserved as evidence, with an explicit active-policy amendment.

The exporter now retains exact root `README.md` and root LICENSE, LICENCE, COPYING, NOTICE and TEMPLATE_LICENSE notices (no extension, .txt, .md or .rst) **before** applying either case-insensitive Markdown deletion or the historical exclusion map. Nested README files, other Markdown, the complete internal docs/evidence tree and all other established exclusions remain excluded. Public README content must be reviewed without internal handoff links, acceptance records, local paths or private operations.

Before any temporary index/tree/commit is created, preparation fails if `README.md` is absent or any existing root public licensing file from the freshly fetched main is missing from retained blob entries. No licensing file is silently recovered from a different branch. Independent main changes still require investigation; the original retained source/object/mode/type and main-parent guards remain.

The focused exporter regression checks exercise real rule order, historical README/license exclusions, missing README, accidental removal of a currently public license, and exclusion of internal Markdown. Run `python docs/source-publication-2026-10-10/test_prepare_snapshot.py`; no runtime build or GameTests are required for this policy/documentation change. The correction is carried into Patch 19 by a scoped commit integration, with no rebase or version change.

The records below describe earlier preparation checkpoints, not the active exception policy or current repository protections.

---

## Current closeout — 2026-10-07

Source publication is independently authorized before remaining manual observations. Snapshot fd90bff209d47addf6667c4576bd2053890127f2 retains exact source object/type/mode parity and passes clean build/full JUnit. Additional closeout changes are excluded documents/evidence. Main restricted updates/signatures remain enforced without administrator exception; a PR is not updated main. Actual outcome is recorded in PUBLICATION_RECEIPT.json and RELEASE_HANDOFF.md.

---

# Public snapshot preparation

The existing 2026-09-14 workflow produced a separate main child of the prior public main and retained exact non-Markdown tree parity with Patch 18. Current owner instructions additionally exclude all internal records, dumps and screenshots. Canonical records remain intact; exclusions are applied only in the detached public-source worktree.

Required fence oracle (`legacy-blockstate.json`, `AUTHORED_MODEL_SHA256.txt`) and weapon/roof/shrine asset-contract manifests previously lived under `docs`. Copies now live in `src/test/resources/release-contracts`; the four JUnit consumer classes and fence resource verifier read them there. Original internal copies are preserved. Weapon `source_root` becomes portable `weapons`. Both roof test copies now name `roof.ai` instead of a machine-specific absolute path, and the roof importer emits the basename going forward. Asset bytes, content/hash maps, game code and test assertions are unchanged. Focused fixture consumers pass; the final clean candidate and sanitized check will verify this integration too.

Except for the public root `README.md` and root licensing notices, the public export excludes every case-insensitive Markdown path, the complete internal `docs` tree, local editor settings, cache/build/runtime/temporary/configuration/credential files and internal plaster runtime collection/preparation/release-audit tools. Required source assets, tests and their fixture resources, generators/checkers, CI/build files and legal/license files remain. Editable artwork and asset-import manifests are source/build inputs, not runtime screenshots. The exact excluded path list and retained Git object/mode/type comparison will be recorded before publication; no blanket parity pass is claimed yet.

Fresh fetch remains `origin/main=f039c4b259d925872618bfe64ee574b20776856c`, `origin/patch-18=7ca44a78d2438ae2204c4f64271ef84bb0672740`. A detached `tmp/combined-0.1.8d/public-source` worktree was created from remote main; no local main branch exists as a result.

GitHub main is protected by active ruleset **17373102**, name **Main**, applying to the default branch. Rules include restricted creation/update/deletion, non-fast-forward rejection, linear history and required signatures. Its only bypass actor is RepositoryRole 5 in `always` mode. Legacy branch-protection endpoint returns 404, which does not mean main is unprotected. Patch 18 reports protected=false. Under the owner's no-bypass instruction, direct main publication must not use this administrator exception. Prepare the normal review path once component acceptance passes; do not mark main updated while its verified remote ref remains unchanged. Recheck current rules and refs before any publication action.

No source publication, PR, tag, GitHub Release, launcher update, live-server restart or live-world edit has occurred at this preparation checkpoint.
