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

---

## Milestone 1 — Stop the destruction: escort assignments (Q-16) (2026-08-11)

### The defect

`EscortPlayerGoal.hasActiveQuestAssignment` asked `ServerQuestTable.hasActiveQuestState(...)` — a
boolean — and on `false` called `clearInvalidEscortAssignment`, which **permanently deletes** the
entity tags that are the only durable record of an escort assignment (`escort_active`,
`quest_escort_<player>`, `quest_state_id_<id>`, `quest_id_<id>`, `quest_key_<key>`).

`ServerQuestTable` is RAM only and is filled solely by the login bootstrap, which is allowed to
fail. Between server start and a player's bootstrap landing — and for the whole session if that
bootstrap failed — the table holds nothing for that player. The goal ticks as soon as the chunk
loads, so **an escort standing near its owner lost its assignment within the first ticks after
every restart, irrecoverably.** A boolean cannot distinguish "this quest ended" from "I have no
record yet", and the code treated the second as the first.

### The change

`ServerQuestTable` gains a three-state answer:

```java
public enum JournalState { ACTIVE, INACTIVE, UNKNOWN }
public static JournalState questStateStatus(UUID playerUuid, String questStateId)
public static boolean journalLoaded(UUID playerUuid)
```

- no map entry for the player → `UNKNOWN` (journal never loaded this run)
- entry present, key present → `ACTIVE`
- entry present, key absent (or a blank id) → `INACTIVE`

Both escort goals — `EscortPlayerGoal` and the duplicate implementation inside
`QuestPayloadHandler.FollowPlayerGoal` — now branch on it: `ACTIVE` follows, `INACTIVE` clears
exactly as before, and **`UNKNOWN` idles without touching a single tag**, logging once per goal
instance at `debug`. Both `hasActiveQuestState` overloads are reimplemented as
`questStateStatus(...) == ACTIVE`, so every other caller keeps byte-identical behaviour and there
is now one definition of the question.

Deliberately unchanged in this milestone: `QuestDestinationBlockEntity` and
`MoongateTeleportationHandler`, which also consult the journal but do not destroy state on a
negative answer.

### Verification

`./gradlew compileJava` BUILD SUCCESSFUL.

**GameTest: all 353 required tests passed** (fresh-log verified, `run/gametest/logs/latest.log`
21:17) — the M0 baseline of 349 plus the four new
`QuestEscortAssignmentGameTests`:

1. `unloadedJournalPreservesTheAssignment` — no journal entry (a fresh mock player never
   bootstraps, which is exactly the post-restart state): the escort idles and every tag survives.
2. `loadedJournalWithTheQuestFollows` — journal loaded and listing the quest: follows, tags intact.
3. `loadedJournalWithoutTheQuestClearsTheAssignment` — journal loaded, quest absent: still clears,
   pinning the pre-existing behaviour that stops abandoned escorts following forever.
4. `assignmentRecoversWhenTheJournalArrives` — idles through the unloaded window, then resumes on
   its own once the journal lands, with no re-activation and no player action.

**Mutation-tested, because a green test proves nothing until it can fail.** The `UNKNOWN` branch
was temporarily disabled so the goal treated it as `INACTIVE` again (the pre-M1 behaviour) and the
full GameTest server re-run:

```
353 GAME TESTS COMPLETE
2 required tests failed :(
   - assignmentrecoverswhenthejournalarrives
   - unloadedjournalpreservestheassignment
```

Exactly the two tests that assert the fix failed; the two that pin preserved behaviour stayed
green. The guard was then restored and the suite re-run clean (the 21:17 log above).

**Unit suite**: 1788 tests, 21 failures — identical to the M0 baseline, same seven
`bannerdyeing` classes. No new failure.

### Observed, not fixed

- `QuestPayloadHandler.FollowPlayerGoal` is a second, near-duplicate implementation of
  `EscortPlayerGoal` that `activateEscort` adds imperatively on top of the goal every
  `QuestGiverEntity` already registers. Both were fixed here because both could destroy state, but
  the duplication itself is **M7's** to remove (Q-11).
- `ServerQuestTable` entries are still never cleared on logout (Q-09). Clearing them today would
  manufacture the `UNKNOWN` state on every relog; it is safe only once M5 makes a miss re-fetch
  rather than reject.

### Scope discipline

Minecraft only. Three production files and one new test class. No Rails change, no behaviour
change to any non-escort caller of the journal.

---

## Milestone 2 — Observability and correlation id (2026-08-11)

### The problem

Twelve paths could swallow a quest action (health check §8). The worst was
`QuestProxyService.handle`: a bare `422 invalid_quest_action` that logged **nothing at all**, and
into which several unrelated causes collapsed — a malformed payload, an unresolvable quest giver,
a quest genuinely absent from the server journal, and a journal that was never fetched because the
login bootstrap failed (Q-03). The last of those disables quests for a whole session, and it was
indistinguishable from a client sending nonsense. On the Rails side, `Processor`'s
`fail_fast("Quest not active.")` — the message affected players actually hit — explained nothing,
which is precisely why a deterministic, permanent, player-specific defect read as flaky networking.

### The change — Minecraft

- `QuestActionC2SPayload` carries a **`requestUuid`**: a client-generated correlation id, bounded
  to 36 chars at the codec, validated as a real UUID in `isValidShape`, and forwarded to Rails as
  `request_uuid`. It authorizes nothing; it is a trace token.
- New `QuestActionTelemetry` emits three structured events — `quest_action_requested`,
  `quest_action_rejected`, `quest_action_result` — and retains the **last rejection per player**
  (bounded to 256 entries, ids and reason codes only) so tests and, later, an operator command can
  read the reason without scraping logs.
- `handle` is restructured so each refusal names itself: `stage` ∈ {SHAPE, NPC_RESOLVE,
  JOURNAL_GATE, DISPATCH, RESPONSE} plus a machine-readable `reason`. The wire response stays the
  deliberately vague `invalid_quest_action` — the detail belongs in the server log, not in a reply
  to a client that may be probing.
- The two journal misses are now **different facts**: `server_journal_not_loaded` (Q-03; journal
  never fetched, `journal_size=-1`) versus `quest_not_in_server_journal` (loaded, quest absent).
  M5 is what makes the first re-fetch instead of reject; M2 just makes it visible.
- Previously silent paths that now log: the player-session-changed response drop (S-2), both
  `applyAuthoritativeResult` early returns and its catch (S-3, S-4), the unmatched-response drop
  in `QuestClient` (S-9), and the two discarded `QuestServerAPI` callbacks — escort death and kill
  recording (S-7, S-8).

### The change — Rails

- `request_uuid` is accepted, stripped to `[0-9a-fA-F-]` and bounded to 36 chars before it is
  written anywhere — a correlation id that lands in a log line is a log-injection vector.
- `transition` (the turn-in) logs `quest_turn_in_requested` and `quest_turn_in_result`.
- `Processor` logs **`quest_turn_in_rejected`** before failing, carrying `user_id`,
  `user_minecraft_uuid`, the candidate uuid list, and — the load-bearing number —
  **`matching_rows` / `active_matching_rows`**: how many journal rows exist for this quest under
  *any* spelling of the player's uuid. A refusal printed next to a non-zero `matching_rows` is
  finding Q-01 caught in the act: the row is there, and only this code path cannot see it.
- `Processor` now accepts `player_uuid_candidates:` **for logging only**. The lookup still uses
  `@player.minecraft_uuid`; M3 is what changes the resolution. This milestone is behaviour-neutral
  by construction.

### Verification

- `compileJava` BUILD SUCCESSFUL.
- **GameTest: all 357 required tests passed** — the 353 baseline plus four new
  `QuestActionTelemetryGameTests` (unloaded-journal reason, loaded-journal reason, malformed
  correlation id, correlation id carried through). Fresh-log verified: the previous `latest.log`
  was **deleted** before the run and the new one is stamped 21:51.
- Unit suite: 1790 tests, 21 failures — the same seven `bannerdyeing` classes as the M0 baseline,
  +2 tests from the extended `QuestProxySecurityTest` (malformed correlation ids, wire round-trip).
- **Rails full suite: 1347 runs, 6377 assertions, 1 failure** — the known deterministic
  `CityFoodSupplyRecalculator` 99.7 ≠ 100.0. Baseline was 1344/1; the delta is exactly the three
  new observability tests.
- The M0 reproduction harness still passes **unchanged**, which is the proof that M2 altered no
  behaviour.

Why these tests cannot pass degenerately: the two journal tests assert *different* exact reason
strings from the *same* call site, differing only in whether the journal was loaded — no single
constant satisfies both.

### Honest note on the test scaffolding

The first GameTest run failed all four new tests with
`Payload britannia_mod:quest_action_result may not be sent to the client!`. Production code was
never implicated: the telemetry lines were emitted correctly (they are in that run's log), and the
throw came afterwards from a `makeMockServerPlayerInLevel` connection refusing a play-phase custom
payload. The helper now tolerates **exactly** that message and rethrows anything else, so a real
failure in the same call still fails the test.

A second trap, previously documented and hit again: a `--rerun-tasks` GameTest run exceeded the
10-minute tool timeout and left a **stale** `latest.log` reporting the earlier failing run. The
fix used here is the reliable one — delete `latest.log`, run, and confirm the new file's mtime.

### Observed, not fixed

- `Endpoint.QUEST_JOURNAL` is still declared and never called, and its Rails route still points at
  a `#show` action that does not exist (Q-17). M5 needs it and will implement it.
- `QuestServerAPI`'s server-originated triggers still carry no correlation id; they are not part
  of the reported failure and were left alone rather than widening this milestone.

### Scope discipline

Both repositories, instrumentation only. No control flow was changed other than splitting one
unlogged rejection into named ones, and no response body, status code, or stored state differs.

---

## Milestone 3 — Rails identity unification + journal uuid migration (Q-01, Q-07) (2026-08-12)

**The blocker is closed.**

### The defect

`QuestEngine::Processor` resolved the journal row by `@player.minecraft_uuid`, byte-for-byte,
while `trigger_node`, `quit` and the world bootstrap resolved it through a candidate list. Journal
rows written before migration `20260602001000` still carry compact or upper-case uuids — that
migration normalized `users.minecraft_uuid` and fourteen other tables, but not
`player_quest_states.player_uuid`. An affected player could see the quest, progress it through
triggers and quit it, and got `403 "Quest not active."` forever when they tried to turn it in.

### The change

**One resolver.** `resolve_player!` / `resolve_player` returns `[user, uuid_candidates]`, prefers
`User.active_identity`, falls back, memoizes per request, and logs `quest_player_resolved` /
`quest_player_unresolved`. **Every** action now uses it — `start`, `interact`, `clear_all`,
`record_kill`, `abandon`, `trigger_node`, `transition`, `update_player_state`, `quit`. The three
ad-hoc user lookups and every single-spelling state lookup are gone; `grep` for
`minecraft_uuid: player_uuid` and `player_uuid: player_uuid` outside the two `create!` calls
returns nothing.

**Processor resolves by candidates**, with `player_uuid_candidates:` now required — it raises
rather than defaulting, because a silent fallback to the old lookup is exactly how this defect
would return.

**Writes normalize.** `PlayerQuestState#normalize_player_uuid` (before_validation) means no new
row can be written in a legacy spelling. A malformed uuid is preserved rather than blanked:
losing a player's journal row is worse than storing an odd one.

**Candidate spellings widened.** A test failure caught a real gap: the candidate list covered
spellings of the *input*, not of the *stored column*, so an upper-case stored row still missed.
The list now enumerates lower/upper × hyphenated/compact plus the raw input — enumerated rather
than matched with `lower()` so the `(player_uuid, quest_id)` index stays usable.

**Migration `20260812050147_normalize_player_quest_state_player_uuids`** — the table the
2026-06-02 migration missed. Written to be safe rather than tidy: no row is ever deleted, no
completed row is ever altered, a malformed uuid is left exactly as it is and reported, and the
unique `(player_uuid, quest_id)` index is respected by never renaming a row into a collision —
where two rows would land on the same normalized uuid for one quest, the most recently touched
active row is kept and the others are abandoned in place. `down` is a deliberate no-op.

### Verification

- **The M0 reproduction harness is flipped and green.** `REPRO: turn-in is refused…` is now
  `FIXED: turn-in completes for a journal row carrying a legacy compact uuid`, joined by an
  upper-case case and an `ASYMMETRY IS GONE` case that drives `trigger_node`,
  `update_player_state` and `abandon` across all three spellings. Renamed
  `quest_completion_identity_repro_test.rb` → `quest_completion_identity_test.rb`; **its diff is
  the evidence.** 8 runs, 28 assertions.
- Migration test: 7 runs — normalizes compact and upper-case, keeps the newest active row and
  **abandons rather than deletes** the duplicate, never touches a completed row, leaves malformed
  uuids alone, is idempotent, and treats quests independently.
- **Mutation-tested.** Reverting Processor's lookup to `@player.minecraft_uuid` fails exactly the
  two `FIXED:` tests and nothing else; restored and re-run clean.
- **Rails full suite: 1359 runs, 6416 assertions, 1 failure** — the known deterministic
  `CityFoodSupplyRecalculator` 99.7 ≠ 100.0. (A separate run also showed the known intermittent
  `ServiceNpcSpawnPointsConcurrencyTest` race; it passes in isolation and predates this work.)
- Guards that must still hold, and do: a second turn-in is still refused (no double reward — M4
  turns that into an idempotent replay), and another player's quest is still never resolved.

### Two judgement calls worth recording

**The schema dump was not committed as generated.** `db:schema:dump` in this environment also
dropped `enable_extension "pg_stat_statements"` and rewrote every `check_constraint` string —
unrelated environment and Postgres-rendering noise. The migration is data-only and adds no DDL,
so `schema.rb` was restored and **only the version line bumped**. Committing the generated dump
would have silently removed an extension other environments declare.

**One M2 test asserted the defect.** `QuestObservabilityTest`'s refusal case drove a legacy
compact row, which M3 legitimately makes succeed. It was moved to a genuinely finished quest, so
it still pins `matching_rows` — and gained a companion asserting that an *active* legacy row
completes and logs no rejection. That pairing is what makes the diagnostic meaningful: rows exist
but none active = the quest really ended; an active row exists and we still refused = the bug is
back.

### Observed, not fixed

- `Api::WorldBootstrapController` keeps its own private `minecraft_uuid_candidates` (without the
  upper-case spellings). It already worked and is outside this milestone's file set; consolidating
  the two copies belongs with M5, which touches that controller anyway.
- Quest turn-in is still not idempotent: a lost response after a Rails commit still costs the
  reward, and the retry is refused. That is M4, unchanged by this work.

### Scope discipline

Rails only. Controller, processor, model, one migration, `schema.rb` version line, three test
files. No Minecraft change.
