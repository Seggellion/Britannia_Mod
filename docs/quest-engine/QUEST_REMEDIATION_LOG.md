# QuestEngine Remediation — Implementation Log

One entry per milestone. Facts only: what changed, what was verified, what was observed but
deliberately not fixed. Plan lives in `QUESTENGINE_REMEDIATION_PLAYBOOK.md`; the diagnosis lives
in `QUESTENGINE_HEALTH_CHECK.md`.

---

## Milestone 0 — Branch, baseline, land the investigation (2026-08-11)

### Worktrees created

| | Path | Branch | Base | Base commit |
| --- | --- | --- | --- | --- |
| Minecraft | `C:\projects\britannia\mod\Britannia_Mod-questengine` | `feature/questengine-remediation` | `patch-18` | `5c9f4f2f` |
| Rails (WSL) | `~/ultimacraft-website-questengine` | `feature/questengine-remediation` | `banking` | `35653f5` |

No existing worktree was modified. `patch-18` remains checked out in the owner's main checkout
(`C:\projects\britannia\mod\Britannia_Mod`) and was not touched; the remediation branch was
created *from* it, not *in* it.

### Source parity verified (the health check's line references are valid here)

- **Minecraft.** `git diff feature/vendor-trader-economy` over `quest/`, `QuestGiverEntity`,
  `EscortPlayerGoal`, `QuestPayloadHandler` and `WorldBootstrapHandler` is empty except for one
  line in `WorldBootstrapHandler.onServerStopping` — the economy branch adds
  `EconomicNpcRegistryCache.clear()` at line 71. It is unrelated to quests, but it means **every
  health-check line number for `WorldBootstrapHandler` after line 68 is one lower on this
  branch** (`complete()`'s failure branch is at :220, not :221). All other files match exactly.
- **Rails.** `git diff banking` over `quest_states_controller.rb`, `app/services/quest_engine/**`,
  `player_quest_state.rb`, `quest.rb`, `node.rb`, `quest_journal_entry_serializer.rb`, `user.rb`
  and the quest controller test is empty. Only `config/routes.rb` differs, and the differences are
  the economy routes; the eleven `quest_states#` route lines are identical, **including the
  duplicated `get 'status/:player_uuid' => quest_states#show` pair (Q-17)**.
  `processor.rb`'s defective lookup is present on `banking` verbatim.

Conclusion: the defects are on the integration line, not artifacts of the economy branch.

### Artifacts landed

- `docs/quest-engine/QUESTENGINE_HEALTH_CHECK.md` (investigation, findings Q-01…Q-17)
- `docs/quest-engine/QUESTENGINE_REMEDIATION_PLAYBOOK.md` (M0–M8 execution plan)
- `docs/quest-engine/QUEST_REMEDIATION_LOG.md` (this file)
- Rails `test/controllers/api/quest_completion_identity_repro_test.rb` (reproduction harness)

### Baselines

**Rails full suite** (`bash bin/codex_test`):
```
1344 runs, 6355 assertions, 1 failures, 0 errors, 0 skips
```
The single failure is the known deterministic one, unrelated to quests:
`Economy::CityFoodSupplyRecalculatorTest#test_city_food_consumption_deducts_from_commodities_and_rebuilds_summary`
— `Expected: 100.0 / Actual: 99.7`.

**Reproduction harness** (`bash bin/codex_test test/controllers/api/quest_completion_identity_repro_test.rb`):
```
4 runs, 12 assertions, 0 failures, 0 errors, 0 skips
```
It passes because it asserts *today's broken behaviour*. The load-bearing case is
`REPRO: turn-in is refused when the journal row carries a legacy compact uuid` — a 403
`"Quest not active."` — alongside `ASYMMETRY: the same legacy row IS found by trigger_node`.
**M3 flips these; the diff of this file is the proof that M3 worked.**

**Minecraft unit suite** (`./gradlew test --no-configuration-cache`):
```
1788 tests, 21 failures, 0 errors   (BUILD FAILED)
```
All 21 are pre-existing banner-dyeing failures, none quest-related:

| Class | tests | failures |
| --- | --- | --- |
| `BannerScaffoldToolTest` | 20 | 12 |
| `ParallelLargeIntegrationTest` | 6 | 2 |
| `ParallelMediumIntegrationTest` | 7 | 2 |
| `SmallBannerFamilyIntegrationTest` | 6 | 2 |
| `ExtraSmallBannerFamilyIntegrationTest` | 8 | 1 |
| `ParallelLargeGateECloseoutTest` | 4 | 1 |
| `PerpendicularMediumIntegrationTest` | 4 | 1 |

**This is 21 failures across 7 classes, not the 13 across 3 recorded on the economy branch**, and
the cause is identifiable: `patch-18`'s own tip commit `5c9f4f2f "Clean placeholder model
metadata"` rewrote `content/banner-final-intake/submissions/*/source/geometry.json`, invalidating
the approved geometry hashes those tests assert. The economy branch is based on `2accdbd1`, one
commit earlier, and therefore sees fewer. Every failure message is
`Approved geometry hash mismatch for <banner>`.

**Take this as the M0 baseline for this branch: 21 known-red banner tests.** Any 22nd failure, or
any failure outside `com.seggellion.britannia_mod.bannerdyeing`, belongs to the milestone that
introduced it.

**Minecraft GameTest server** (`./gradlew runGameTestServer --rerun-tasks --no-configuration-cache`):
```
All 349 required tests passed :)
```
Fresh-log verified (`run/gametest/logs/latest.log`, 20:48, this run). This branch does **not**
carry the vendor/trader economy gametests, so the count is 349, not the economy branch's 377.
**The playbook's M1 expectation is corrected from "377 + 4" to "349 + 4 = 353"** in the copy
committed here.

### Environment notes (fresh-worktree traps — added to the playbook's §0.4 knowledge)

1. **The Gradle wrapper jar is Git-LFS tracked.** A new worktree checks out the 133-byte LFS
   *pointer*, and `./gradlew` dies with
   `Could not find or load main class org.gradle.wrapper.GradleWrapperMain`. Fix: copy the real
   43,504-byte jar in from an existing worktree. It then shows as modified forever — **never stage
   it**, consistent with the standing rule.
2. **Rails `bin/` exec bits do not survive the worktree checkout.** `bin/codex_test` and
   `bin/setup_test_database` arrive mode 644 (they are 644 in the tree; the existing worktrees were
   chmod'd locally), so `bin/codex_test` fails with `Permission denied` on
   `bin/setup_test_database`. Fix: `chmod +x bin/codex_test bin/setup_test_database bin/jobs` in
   the new worktree. Local-only; not committed.

### Observed, not fixed

- Q-17 (the duplicated dead `status/:player_uuid` route with no `#show` action) is confirmed
  present on `banking`; it is M5's to fix.
- **`patch-18`'s tip commit leaves the branch's own unit suite red.** `5c9f4f2f "Clean placeholder
  model metadata"` edited the banner submission geometry sources without re-approving the hashes
  the banner tests assert, taking that suite from 13 failures to 21. This is outside the
  QuestEngine programme's scope and is **not** being fixed here, but the owner should know the
  integration branch is red for a reason that is one commit wide and has nothing to do with
  quests.

### Scope discipline

No production code was changed in this milestone, in either repository.
