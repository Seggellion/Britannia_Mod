# Patch 18 Critical Hotfix — Trader Sale Receipt Settlement

**Repository:** `C:\projects\britannia\mod\Britannia_Mod`  
**Required target branch:** `patch-18`  
**Suggested temporary branch:** `codex/patch-18-trader-sale-receipt-hotfix`  
**Severity:** Critical — Rails commits sales and removes city funds, but Minecraft can strand the player's payout  
**Publication:** Merge locally into `patch-18`; do not push, deploy, or mutate production unless the invoking request separately authorizes it  
**Cleanup:** Remove every worktree and temporary branch created for this hotfix after proving its commits are contained in `patch-18`

---

## 1. Codex prompt

Use this entire document as the execution prompt.

You are implementing a critical Patch 18 hotfix in the Britannia NeoForge repository. Work autonomously and evidence-first. Preserve unrelated user changes. Do not implement the fix only on `patch-19`, `main`, or an unmerged temporary branch. The final accepted source commit must be contained in local `patch-18`.

Use an isolated worktree created from the freshly revalidated `patch-18` branch. Do not modify the user's current checkout if it is on another branch. Run focused tests first, then the full proportionate release gates below. After the hotfix is committed and merged into `patch-18`, rerun the acceptance gate against the exact resulting `patch-18` commit. Remove only the worktrees and temporary branches that this hotfix created, and only after proving they are clean and merged. Never remove a pre-existing worktree or branch merely because it looks stale.

Do not push, deploy, edit production data, manually pay players, or delete pending settlement evidence unless the invoking request separately grants that authority.

---

## 2. Confirmed production incident

At `2026-09-15T21:16:56Z`, Rails successfully committed a produce-trader sale:

- player UUID: `4af2cc45-dc5a-4880-9cc3-5509286cff12`;
- Rails transaction ID: `1474`;
- item: one carrot;
- price: `2.67` copper, rounded to a grant of `3` copper;
- city commodity quantity advanced to `101`;
- response reported `success: true` and `idempotent_replay: false`.

The server parsed the grant correctly:

```text
Rails currency_grant parsed raw={"copper":3} payout=0g 0s 3c
```

It then repeatedly logged:

```text
Trader sale <key> awaits authoritative receipt resolution; no refund or payout guessed
```

The full incident idempotency key is:

```text
sale:4af2cc45-dc5a-4880-9cc3-5509286cff12:6d70f4b8-5906-4b51-8070-8833d1fda7e6:1194695950:4e10a730:86a1b5e2-70be-4f0d-9617-228eb4dff2cc
```

This is not a treasury-insufficiency event. Rails accepted and persisted the sale. The player is owed the confirmed payout; the local durable receipt should remain recoverable until delivery.

---

## 3. Confirmed root cause

The successful production response has identity fields nested inside `transaction`:

```json
{
  "success": true,
  "transaction": {
    "transaction_id": 1474,
    "idempotency_key": "sale:...",
    "player_uuid": "4af2cc45-dc5a-4880-9cc3-5509286cff12",
    "transaction_type": "sell",
    "currency_grant": { "copper": 3 }
  },
  "transaction_id": 1474,
  "idempotency_key": "sale:...",
  "currency_grant": { "copper": 3 }
}
```

`ServerEconomyService.resultFromReceipt(...)` already understands the nested currency grant, so it parses the three-copper payout. However, `TraderSaleSettlementService.matchesReceipt(...)` reads `player_uuid` and `transaction_type` only from the response root. Those fields are absent there. Its caught runtime failure returns `false`, causing a valid HTTP 200 settlement to enter `RECONCILING` forever.

The same validation must work for both:

1. the immediate successful `POST /api/trader_transactions` response; and
2. the later authoritative `GET /api/trader_sale_receipt` response used after timeout, restart, disconnect, or response loss.

Do not weaken receipt ownership checks to make the fixture pass. Normalize and validate the actual response contract.

---

## 4. Required behavior

The completed hotfix must satisfy all of these invariants:

1. A confirmed successful response in the exact production shape transitions to `PAYOUT_PENDING`, delivers the exact three-copper grant to the currently connected player, saves that delivery durably, and resolves the journal entry.
2. A previously stranded `RECONCILING` receipt automatically recovers through authoritative lookup after deployment. No manual item refund or new sale is required.
3. Existing supported flat/legacy receipt shapes continue to work if they are still part of the contract.
4. Receipt identity is accepted only when all required facts agree:
   - idempotency key;
   - normalized player UUID;
   - transaction type `sell`;
   - successful confirmed transaction/receipt identity;
   - non-negative, non-zero payout.
5. If the same identity field appears at more than one level, conflicting values fail closed and remain pending for investigation.
6. Missing, malformed, wrong-player, wrong-key, wrong-type, zero-payout, or negative-payout receipts never deliver currency or refund sold goods speculatively.
7. A retry or stale callback cannot deliver twice.
8. Definitive initial 4xx rejections continue to return the exact reserved items through the existing durable refund path.
9. Network errors and genuinely ambiguous outcomes remain pending; the hotfix must not convert uncertainty into duplicated goods or currency.
10. Full inventory behavior remains atomic and pending, with no partial payout and no world drop.
11. The fix must preserve the original idempotency key and must prefer authoritative receipt lookup over reposting whenever a committed receipt is found.
12. Logs must make malformed or mismatched receipt identity diagnosable without logging secrets or full player inventory payloads.

Literal cross-process atomicity between Minecraft player data and Rails is not possible. The required guarantee is durable eventual settlement: exactly one confirmed payout or one exact refund, never silent loss and never both.

---

## 5. Bounded adjacent audit

Before editing, read the complete relevant methods and their tests, including:

- `ServerEconomyService.resultFromReceipt`;
- `ServerEconomyService.postSale` and `postSaleToEndpoint`;
- `TraderSaleSettlementService.send`, `lookup`, `confirmed`, `matchesReceipt`, `deliver`, and `resolveDelivered`;
- `TraderSaleReservationReceipt` and `TraderSaleReservationStore`;
- `GameplayTraderRecoveryGameTests`;
- `GameplayProduceRailsIntegrationGameTests`;
- the local Rails trader-sale response serializer/controller and receipt-lookup response contract, read-only unless a Rails change is proven necessary.

Audit all currently intended receipt shapes, not just the one JSON sample. Determine whether the lookup endpoint returns:

- a flat receipt;
- a nested `transaction`;
- a nested `receipt` containing a transaction;
- or more than one supported form.

Prefer one small, explicit parser/value object that extracts and validates authoritative receipt identity and payout source consistently. Avoid scattered fallback expressions that silently choose one conflicting value.

If the audit finds an adjacent defect that directly prevents safe settlement of this incident class, fix and test it in this hotfix. Record unrelated findings separately; do not expand this into a general economy rewrite.

Changing Rails to flatten future responses is not sufficient by itself: already-stranded Minecraft journals must be able to consume the response shapes that production has already emitted and that the receipt endpoint currently returns.

---

## 6. Required regression tests

Add focused tests that freeze the wire contract. Use the exact structural shape observed in production, with synthetic UUIDs/keys where practical.

At minimum, prove:

1. **Immediate nested success:** root `success`, root transaction ID/key/grant, nested `transaction.player_uuid`, nested `transaction.transaction_type`, and nested transaction grant are accepted and paid once.
2. **Nested authoritative lookup:** a journal in `RECONCILING` consumes the lookup response, pays the current same-UUID player, and resolves without reposting the sale.
3. **Existing flat compatibility:** an intended flat receipt still validates and settles.
4. **Conflicting duplicate fields:** root and nested key/player/type disagreement fails closed.
5. **Wrong ownership:** wrong player UUID fails closed.
6. **Wrong operation:** a `purchase` receipt cannot settle a `sell` reservation.
7. **Malformed/missing identity:** no payout and no speculative refund.
8. **Payout validation:** zero, negative, fractional-invalid, or oversized denomination values remain rejected/pending according to existing safety policy.
9. **Exactly once:** repeated callback, repeated login recovery, and repeated tick recovery do not duplicate currency.
10. **Definitive refusal:** an initial Rails 4xx still returns the exact item stack and never pays currency.
11. **Full inventory:** confirmed payout remains pending without partial insertion.
12. **Production incident fixture:** a response structurally equivalent to transaction `1474` produces three copper and no carrots, then clears the local receipt.

Tests should exercise the public settlement flow where practical, not merely a helper in isolation. A small unit contract test is welcome, but retain at least one GameTest or HTTP-bound test that proves status transition, inventory delivery, durability marker, and journal resolution together.

If the opt-in real-Rails integration cannot run in the execution environment, do not fake that gate. Use a deterministic local HTTP fixture for the exact production JSON and clearly report the real-Rails gate as not executed. Existing historical real-Rails evidence is not evidence for the new parser until rerun.

---

## 7. Production recovery requirements

The source fix must be sufficient for existing modern journal entries to recover naturally. Do not write a one-off mutation keyed only to transaction `1474`.

Document the expected post-deployment sequence for this incident:

1. player is online, or logs in;
2. bounded recovery finds the existing `RECONCILING` receipt;
3. receipt lookup finds Rails transaction `1474` under the original idempotency key;
4. the normalized identity validator accepts the nested production shape;
5. status becomes `PAYOUT_PENDING`;
6. three copper are inserted and saved with the delivery marker;
7. journal resolution is flushed;
8. the marker is cleared only after successful journal resolution;
9. later retries produce no additional coins.

Do not manually grant the three copper while leaving the journal pending. That would allow the fixed automatic recovery to pay the player again. If an operator performs manual compensation before deployment, it must be handled as an explicit, separately authorized reconciliation that also safely resolves the matching journal receipt with durable audit evidence.

---

## 8. Branch and worktree procedure

### 8.1 Freeze and revalidate

Before mutation:

1. Read any applicable `AGENTS.md` instructions completely.
2. Record `git status --short`, current branch, `patch-18`, `origin/patch-18`, and `git worktree list --porcelain`.
3. Preserve and do not overwrite unrelated dirty/untracked files.
4. Fetch `origin` if network permission is available.
5. Reconcile remote movement safely. Never force-reset `patch-18` or discard unique local commits.
6. Confirm the settlement implementation being fixed is contained in the chosen `patch-18` base.

At playbook creation, local `patch-18` and `patch-19` both pointed to `339cc70bb78cb1b99de114e57ca0f8e75f8e8a80`. This is an observation, not an immutable instruction; revalidate it.

### 8.2 Create isolated hotfix worktree

Create a uniquely named worktree outside the primary checkout from the revalidated `patch-18` commit. Use the `codex/` branch prefix. Do not reuse or remove the pre-existing Claude worktree.

Example shape only—choose a collision-free absolute path:

```powershell
git worktree add -b codex/patch-18-trader-sale-receipt-hotfix `
  C:\projects\britannia\worktrees\patch18-trader-sale-receipt-hotfix patch-18
```

All implementation commits must be made in that isolated hotfix worktree.

### 8.3 Integrate into `patch-18`

After focused and full gates pass in the hotfix worktree:

1. commit the smallest coherent hotfix;
2. create or use a clean integration worktree checking out `patch-18`;
3. revalidate that `patch-18` has not moved unexpectedly;
4. integrate the hotfix, using `--ff-only` when ancestry permits;
5. prove the hotfix commit is an ancestor of `patch-18`;
6. rerun the final acceptance gate on the exact resulting `patch-18` HEAD;
7. do not merge the hotfix into `patch-19` or `main` unless separately requested.

Do not leave the only copy of the fix on the temporary branch.

### 8.4 Cleanup

After successful integration and verification:

1. ensure each hotfix-created worktree is clean;
2. verify every hotfix-created commit is reachable from `patch-18`;
3. remove only the worktrees created for this task;
4. delete the temporary hotfix branch only after the reachability proof;
5. run `git worktree prune` only if safe;
6. record the final `git worktree list --porcelain` and branch containment output.

Do not remove, modify, or clean the pre-existing worktree at `.claude/worktrees/ultimacraft-harness-discovery-dfc252` as part of this hotfix.

---

## 9. Verification gates

Use the repository's configured JDK and Gradle conventions. Capture complete command output in an ignored temporary evidence directory, not as tracked release material.

Run, at minimum:

1. the new focused receipt-contract unit tests;
2. all economy unit tests;
3. the focused trader recovery GameTests if the harness supports selection;
4. the complete required GameTest server gate;
5. the normal clean release build and artifact identity gate.

Expected command shapes on Windows:

```powershell
.\gradlew.bat test --tests "com.seggellion.britannia_mod.economy.*" `
  --no-configuration-cache --console=plain

.\gradlew.bat runGameTestServer -x processResources `
  --no-configuration-cache --console=plain

.\gradlew.bat clean build artifactIdentity `
  --no-configuration-cache --console=plain
```

Adapt only when repository tooling requires it. Do not omit a failing test, weaken an assertion, or label a skipped external integration as passed. Investigate failures and distinguish hotfix regressions from unrelated pre-existing failures with concrete evidence.

After integration, at least rerun the focused receipt/economy tests and the clean build against exact `patch-18` HEAD. For this critical inventory/currency defect, prefer rerunning the complete GameTest gate on final `patch-18` unless an identical artifact/commit proof makes the earlier run literally the same commit.

---

## 10. Completion criteria

The hotfix is complete only when all of the following are true:

- the production nested response is accepted without weakening identity validation;
- direct success and authoritative lookup use compatible validation;
- transaction `1474`'s structural fixture settles to exactly three copper in tests;
- previously pending modern receipts can recover automatically;
- wrong, conflicting, or malformed receipts fail closed;
- retry/disconnect/restart/full-inventory behavior remains lossless and exactly once;
- focused tests, economy tests, required GameTests, and release build gates are reported accurately;
- the accepted commit is contained in local `patch-18`;
- no hotfix worktree remains;
- no temporary hotfix branch remains unless cleanup is blocked and explicitly reported;
- no pre-existing worktree or unrelated user change was removed;
- no remote push, deployment, or production mutation occurred without separate authority.

---

## 11. Final report format

Return a concise evidence-backed report containing:

1. root cause and the exact response-contract mismatch;
2. implementation summary and changed files;
3. hotfix commit SHA and final `patch-18` HEAD;
4. tests executed with pass/fail/skip counts;
5. whether real Rails integration was executed;
6. proof that the hotfix commit is contained in `patch-18`;
7. worktrees/temporary branches created and confirmation they were removed;
8. remaining risks or unexecuted gates;
9. the safe production recovery expectation for already-pending receipts;
10. an explicit statement that no manual compensation, production write, push, or deployment occurred unless separately authorized.

