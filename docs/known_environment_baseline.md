# Known environment baseline

Status: reference record, not a milestone deliverable

Date verified: 2026-07-26

Scope: NeoForge build/test tooling gotchas and standing cross-repo items

Implementation status: documentation only

This document is the NeoForge-side counterpart to the Rails repo's `docs/known_environment_baseline.md`. It catalogs build/test-tooling behavior that is easy to misread as a real problem (or, worse, as a false-positive "all clear") if you don't already know about it. Every item was re-verified directly against the current merged `banking` tip (`ab14eb95ef94fe7d37f005fb42bc5dc7ae01876e`) on 2026-07-26, not carried forward from memory.

## 1. Gradle tooling gotchas

### 1.1 `:test` reports UP-TO-DATE instead of genuinely re-running

Confirmed by direct reproduction: running `gradlew test` a second time with no source changes prints `Task :test UP-TO-DATE` and finishes in seconds without executing a single test. Gradle's incremental-build model treats the task as satisfied because none of its declared inputs changed — this is correct Gradle behavior, not a bug, but it means a plain `gradlew test` (or `build`) can report success without having actually run anything new. Whenever a *genuinely fresh* result matters — verifying a merge, confirming a fix, or any other case where "it was already green from before" is not an acceptable answer — always pass `--rerun-tasks` (and see 1.3 for why `--no-configuration-cache` usually needs to ride along with it).

### 1.2 Windows `git.exe` vs. WSL-native `git` — CRLF/whitespace corruption risk

This repository has `core.autocrlf=true` set locally (confirmed via `git config --get core.autocrlf`), which makes Windows Git normalize line endings on checkout/commit. The Rails repo this mod integrates with is a native-LF Linux repository accessed through WSL. Editing or generating Rails-repo files through Windows-side tools (including piping heredoc content through a Windows-side shell) risks CRLF injection or whitespace corruption that WSL-native `git` and Ruby will see as real diffs or, worse, invalid syntax in generated source. Prefer `wsl.exe -e bash -lc "cd <rails-repo> && ..."` for anything that reads, writes, or diffs files in the Rails repo, and verify anything written this way by reading it back before trusting it.

### 1.3 `runGameTestServer` needs `--no-configuration-cache` for a genuinely fresh run

`gradle.properties` sets `org.gradle.configuration-cache=true` (confirmed present) — configuration caching is on by default in this project. Combined with a known Gradle configuration-cache staleness issue around the `runGameTestServer` task, a plain rerun can silently reuse a stale cached configuration instead of genuinely re-evaluating and re-running the GameTest server. Always pair `--rerun-tasks --no-configuration-cache` together for `runGameTestServer` (and `compileJava`/`test`/`build`, per 1.1) when the result needs to be trustworthy as a fresh signal — e.g. verifying a merge or a concurrency-sensitive change, where running it twice in a row is also worth doing for stability, not just once.

## 2. Real, standing issues — not fixed, not in scope here

These are genuine, currently-true items, recorded so they aren't lost or rediscovered from scratch. They are not addressed by this document and are not this document's job to fix.

### 2.1 `ServerEconomyService.reserveItems` has no revalidation/idempotency guard

Found during Milestone 9 reconnaissance, re-confirmed still present and unchanged as of the current merged `banking` tip. `reserveItems` (`src/main/java/com/seggellion/britannia_mod/economy/ServerEconomyService.java`, currently around line 323) reads each selected slot's live `ItemStack`, checks only `live.getCount() < item.quantity()`, and shrinks it immediately if that passes — a plain check-then-shrink with no idempotency key, no in-flight/dedup tracking, and no atomic claim comparable to banking's `BankItem` row-flip or `reserved_*_balance` counters. This is a real latent duplication-risk window in the unrelated wood/trader-sale flow. It is not banking's system and not banking's to fix, but it means the trader-sale path does not have the same race-safety guarantees banking's transfer system does — don't assume otherwise.

### 2.2 Four parked Rails branches (Rails repo, recorded here for cross-repo visibility)

Full detail lives in the Rails repo's own `docs/known_environment_baseline.md` §3.2; summarized here so a NeoForge-side session doesn't have to cross-reference to know these exist:

- `feature/service-npc-m2-world-npc-identity` and `feature/service-npc-m4-auth-hardening-banking-base` — harmless stale pointers, both fully ancestors of `banking`, no attached worktree.
- `feature/service-npc-m3-dialogue-registry` and `feature/service-npc-m4-auth-hardening` — genuinely unmerged, each carrying one small real commit never merged forward (a fixture-infrastructure commit, and a dead/superseded auth-hardening implementation respectively). Both have active worktrees. Neither is harmful as-is; neither should be deleted without checking whether its content is still wanted.

### 2.3 Banking transfer flows have no server-side "screen actually open" verification (Milestone 14 priority 2 context-enforcement audit, 2026-08-03)

Confirmed directly by reading every packet handler, not inferred: `BankScreen` (`src/main/java/com/seggellion/britannia_mod/client/screen/BankScreen.java`) is, by its own class doc, "a plain client `Screen`, not an `AbstractContainerMenu`" — there is no server-side record of "this player currently has the Bank Screen open for this teller." `BankingTransferPacketService`'s handlers (deposit/withdrawal/currency deposit/currency withdrawal/cheque issuance/cheque redemption) trust a packet's claimed slot index only as "a selection reference" and re-validate the item/currency *content* at that slot, but never whether the screen itself was legitimately opened first. A crafted or replayed packet with a valid nearby teller and a real matching item in the player's live inventory can trigger the full mutation without the player ever having opened the screen.

This is real but deliberate, not an oversight this milestone's own priority-2 pass fixed: unlike proximity re-validation (which had an existing, portable pattern — `BankingProxyService.resolve()`'s double-check — ported into all five gap flows this same milestone), there is no existing server-side "screen session" mechanism for banking to extend. `ServiceNpcSpawnPayloadHandler.validateSession` (`network/payload/ServiceNpcSpawnPayloadHandler.java`) proves the *pattern* is known and buildable elsewhere in this codebase (real `player.containerMenu instanceof X` + ownership + `stillValid` checks, for spawn-point configuration) — but porting it to banking would mean converting `BankScreen` from a plain client `Screen` into a real server-tracked `AbstractContainerMenu`, a genuine architectural change, not a targeted fix. Recorded here rather than silently fixed or silently ignored, per this milestone's own instruction not to invent a new context-checking mechanism where none already exists.

Practical exposure is narrower than it sounds: the mutation itself still requires a real, live, in-range, capability-matching teller (now re-checked twice per flow, not once) and a real matching item/currency amount already in the player's own inventory — this gap only means the intended UX flow (open screen, select item, click deposit) is not the *only* path to a legitimate-looking mutation, not that an attacker can act on someone else's account or fabricate value from nothing.
