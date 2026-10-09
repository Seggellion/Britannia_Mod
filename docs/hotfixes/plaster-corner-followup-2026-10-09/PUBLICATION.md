# Source publication receipt — 2026-10-09

Development integration/publication is authorized independently of pending exterior/manual acceptance. This task changes excluded internal records, analysis and disposable-fixture preparation only. Production implementation/resources and candidate bytes are unchanged. No artificial sanitized snapshot/build is prepared for these internal changes.

Entry exact patch-18: `5a8cab9a38fac52253dea20431dea60cd15812c0`. Remote refs were refreshed; normal non-force task publication is recorded below after execution. Owner untracked kickoffs and concurrent fence discovery records remain untouched. No reset, force push, local main creation, production deployment, tag or GitHub Release occurs.

## Main and existing public review

Fresh inspection: [PR #501](https://github.com/Seggellion/Britannia_Mod/pull/501) remains **OPEN/BLOCKED**, head `fd90bff209d47addf6667c4576bd2053890127f2`, base/main `f039c4b259d925872618bfe64ee574b20776856c`. The commit is unsigned (`verified=false, reason=unsigned`). Active main ruleset **17373102** restricts updates, requires signatures/linear history and permits only an administrator-role bypass. The owner prohibits bypass; no permitted signing/update workflow has been established. No merge or ruleset change was attempted. **An open PR is not updated main.** [Actual API/ref evidence](evidence/REMOTE_STATE.json).

Remote GameTest jobs passed, but both build jobs failed. Run [37687457615](https://github.com/Seggellion/Britannia_Mod/actions/runs/37687457615) reports **4,124 tests completed, three failures, 31 skipped**: initialization failures in `RunuoAccountingDriftTest`, `RunuoBuybackCoverageTest`, `RunuoRetailRolloutCoverageTest`. Their fixture lookup uses excluded `docs/vendor-trader-economy/runuo_ultimacraft_mapping.json` / `economic_vendor_rollout.json`. The local sanitized checkout's ancestor lookup can find canonical internal docs, while the standalone GitHub checkout cannot. The historical local public pass therefore did **not** establish self-contained remote CI portability. [Failure excerpts](evidence/EXISTING_PR_CI_FAILURES.txt). A portable fixture repair is a separate public CI follow-up, not a plaster failure or an excuse to claim main updated.

The older public snapshot matches the candidate's production implementation, but not the entire current source tree: later concurrent `FenceRuntimeIdentityGameTests.java` is exported source although excluded from release JARs. [Scope audit](evidence/PUBLIC_SCOPE_AUDIT.json). This docs-only plaster task does not manufacture a public snapshot to hide or import that difference.

## Development publication

Pending execution at initial record creation. A subsequent receipt will identify the verified task integration commit and remote result; final advertised refs are also captured outside the tracked receipt to avoid self-referential hashes. Pending manual observations and main holds do not imply production acceptance.
