# UltimaCraft QuestEngine Remediation — Claude Milestone Playbook

Companion to `QUESTENGINE_HEALTH_CHECK.md` (the investigation; finding ids `Q-01`…`Q-17` below
refer to its §7 register). This document is the **execution** plan.

**Read this section, then jump straight to your milestone.** Every milestone below is written to
be executed from a cold context: it restates the facts it depends on, names exact files and
anchors, and lists its own commands and exit criteria. You should not need to re-read the health
check or re-derive the codebase to execute one.

---

# 0. Operating rules

## 0.1 The milestone contract

1. Execute exactly one milestone.
2. Run that milestone's verification commands. Paste real output; never summarise a test run you
   did not watch finish.
3. Commit **only** the files that milestone owns (they are listed). Never `git add .`.
4. Append an entry to `docs/quest-engine/QUEST_REMEDIATION_LOG.md`.
5. Write a completion report ending with `STOPPED: YES`.
6. **Stop and wait for owner approval.** "I approve" of a decision is not permission to start the
   next milestone; only an explicit "start Milestone N" is.

## 0.2 Non-negotiable rules

- **Do not widen scope.** Each milestone fixes its listed findings and nothing else. If you spot
  something new, record it in the log's "Observed, not fixed" section and keep going.
- **Never destroy persisted state because an in-memory cache said "no".** This is the class of bug
  that produced Q-16; do not reintroduce it anywhere.
- **Never trust the client for objective completion, reward content, or quest ownership.**
- **Never make quest completion non-idempotent.** After M4 every completion path must tolerate a
  replay without double-granting and without a bare failure.
- **Do not change quest balance, quest text, rewards, or unrelated NPC systems.**
- Do not touch the pinned RunUO checkout at `C:\projects\runuo-reference` (@ `71b2794f`).
- Never touch the `ultimacraft_development` database.
- Never stage `gradle/wrapper/gradle-wrapper.jar` (it shows as modified in every worktree; leave
  it alone).

## 0.3 Repositories and branches

| | Path | Branch |
| --- | --- | --- |
| Minecraft | `C:\projects\britannia\mod\Britannia_Mod-questengine` (create in M0) | `feature/questengine-remediation` off `patch-18` |
| Rails (WSL) | `~/ultimacraft-website-questengine` (create in M0) | `feature/questengine-remediation` off the approved Rails integration branch |

Verified fact: the quest sources on `patch-18` and `feature/vendor-trader-economy` are
**byte-identical** (`git diff patch-18 -- .../quest` is empty), so every line reference in the
health check applies to a branch cut from `patch-18`. Confirm this again in M0 before relying on it.

The investigation artifacts currently live **uncommitted** in the
`Britannia_Mod-vendor-trader-economy` worktree (`docs/quest-engine/`) and in
`~/ultimacraft-website-vendor-trader-economy` (`test/controllers/api/quest_completion_identity_repro_test.rb`).
M0 moves them onto the remediation branches.

## 0.4 Environment facts (these have cost hours before — do not rediscover them)

**Rails, in WSL only.** Windows tools cannot see the Rails worktree.

- Write files to the scratchpad first, then copy in and strip CRLF:
  ```bash
  wsl -e bash -lc 'tr -d "\r" < "/mnt/c/<scratchpad>/file.rb" > ~/ultimacraft-website-questengine/path/file.rb'
  ```
- Complex edits: write a Python script to the scratchpad and run it in WSL. Inline heredocs with
  quotes break; this has been proven repeatedly.
- Tests: `bash bin/codex_test [files…]` from the repo root.
- **After any migration**, in this order:
  ```bash
  SKIP_PERSISTED_SETTINGS=1 RAILS_ENV=test rbenv exec ruby bin/rails db:schema:dump
  ```
  `RAILS_ENV=test` is **critical**. Without it the development schema is dumped into `schema.rb`,
  `maintain_test_schema!` then purges the main test database while `schema_migrations` still
  records the version, and you get ~112 phantom errors. Then reload the 16 parallel worker
  databases with `/tmp/reload_worker_schemas.rb` (`dropdb` is permission-blocked).
- Long commit messages: write to a file and `git commit -F file` (WSL quoting mangles inline `-m`).

**Minecraft, from Windows.**

- `./gradlew compileJava --no-configuration-cache`
- `./gradlew test --no-configuration-cache` — **21 pre-existing failures across 7
  `bannerdyeing` classes** on this branch (stale approved geometry hashes; `patch-18`'s tip commit
  `5c9f4f2f` rewrote the banner geometry sources). They are not yours. Any 22nd failure, or any
  failure outside `bannerdyeing`, is.
- `./gradlew runGameTestServer --rerun-tasks --no-configuration-cache` — baseline **349 required
  tests**, all passing on this branch (the economy branch's 377 includes 28 economy tests that do
  not exist here). Before quoting a tally, check `run/gametest/logs/latest.log` **mtime** is from
  this run; grepping a stale log has produced two false "all green" reports.
- **A fresh worktree needs two fixes before either command works** — see the M0 log: copy the real
  `gradle/wrapper/gradle-wrapper.jar` in (LFS pointer) and `chmod +x bin/codex_test
  bin/setup_test_database` on the Rails side.

**Commit trailer** (use the model actually doing the work):
```
Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## 0.5 Milestone map

| # | Title | Fixes | Repos | Risk |
| --- | --- | --- | --- | --- |
| M0 | Branch, baseline, land investigation artifacts | — | both | none |
| M1 | Stop the destruction: escort assignments | Q-16 | MC | very low |
| M2 | Observability and correlation id | S-1…S-12 | both | low (no behaviour change) |
| M3 | **Rails identity unification + journal uuid migration** | Q-01, Q-07 | Rails | medium |
| M4 | Idempotent completion transaction | Q-04, Q-10, Q-12 | both | medium |
| M5 | Durable journal and fail-safe gate | Q-03, Q-09, Q-17 | both | medium |
| M6 | Server-authoritative objectives | Q-02, Q-05, Q-06 | MC | high |
| M7 | QuestGiver identity and escort lifecycle | Q-08, Q-11, Q-13, Q-14 | MC | medium |
| M8 | Regression suite and failure-injection validation | Q-15 | both | none |

Ordering rationale: M1 first because it is actively destroying persisted state on every restart
and the fix is a guard. M2 next so every later milestone is verifiable in production. M3 is the
blocker the owner is waiting on and must precede M4 (idempotency is meaningless if the row cannot
be found). M5 depends on M2's reason codes. M6 depends on M5's durable journal. M7 is
independent and deliberately last among the fixes. **M3 alone makes the reported symptom stop.**

---

# Milestone 0 — Branch, baseline, land the investigation

### Objective
Create isolated worktrees, capture a truthful "before" baseline, and commit the investigation
artifacts so every later milestone has them on-branch.

### Steps

1. Inspect state in both repos before touching anything:
   `git branch --show-current`, `git status --short --branch`, `git rev-parse HEAD`,
   `git log --oneline -10`, `git worktree list`.
2. Confirm the approved integration branches from repository state and docs. Confirm
   `git diff patch-18 -- src/main/java/com/seggellion/britannia_mod/quest` is empty.
3. Create both worktrees/branches from §0.3.
4. Copy in from the vendor-trader-economy worktrees:
   - `docs/quest-engine/QUESTENGINE_HEALTH_CHECK.md`
   - `docs/quest-engine/QUESTENGINE_REMEDIATION_PLAYBOOK.md` (this file)
   - Rails `test/controllers/api/quest_completion_identity_repro_test.rb`
5. Create `docs/quest-engine/QUEST_REMEDIATION_LOG.md` with a Milestone 0 entry.
6. Record the baseline: Rails full suite counts, `./gradlew test` failure list, GameTest tally.

### Acceptance
- Both worktrees exist; no other worktree was modified.
- The repro test runs on the new Rails branch: **4 runs, 12 assertions, 0 failures** (it asserts
  today's broken behaviour and must pass *before* M3).
- Baselines recorded verbatim in the log.

### Commit scope
MC: `docs/quest-engine/**`. Rails: `test/controllers/api/quest_completion_identity_repro_test.rb`.

### Stop
Report and stop.

---

# Milestone 1 — Stop the destruction: escort assignments (Q-16)

### The defect, restated
`EscortPlayerGoal.hasActiveQuestAssignment` (`src/main/java/com/seggellion/britannia_mod/entity/ai/EscortPlayerGoal.java:100-116`)
asks `ServerQuestTable.hasActiveQuestState(player.getUUID(), questStateId)` — a **static
in-memory map** that is empty from server start until each player's login bootstrap lands, and
stays empty all session if that bootstrap failed. On a `false` answer it calls
`clearInvalidEscortAssignment`, which **permanently deletes** the entity's persisted tags
(`escort_active`, `quest_escort_*`, `quest_state_id_*`, `quest_id_*`, `quest_key_*`). The goal
ticks as soon as the chunk loads, so an escort standing near its owner can lose its assignment in
the first ticks after every restart, irrecoverably.

The same logic is duplicated in `QuestPayloadHandler.FollowPlayerGoal.hasActiveQuestAssignment`
(`network/QuestPayloadHandler.java:200-214`).

### Change
Introduce a three-state answer in `ServerQuestTable`:

```java
public enum JournalState { ACTIVE, INACTIVE, UNKNOWN }
public static JournalState questStateStatus(UUID playerUuid, String questStateId)
```

- no map entry for the player at all → `UNKNOWN` (journal never loaded this session)
- entry present, key present → `ACTIVE`
- entry present, key absent → `INACTIVE`

Both goals then:
- `ACTIVE` → follow;
- `UNKNOWN` → **idle without clearing anything**, and log once per entity at `debug`;
- `INACTIVE` → clear, as today.

Keep `hasActiveQuestState` for other callers; implement it in terms of the new method
(`== ACTIVE`). Do not change `QuestDestinationBlockEntity` or `MoongateTeleportationHandler`
behaviour in this milestone.

### Files
- `entity/ai/EscortPlayerGoal.java`
- `network/QuestPayloadHandler.java` (inner `FollowPlayerGoal`)
- `quest/ServerQuestTable.java`

### Tests (new GameTests, `gametest/QuestEscortAssignmentGameTests.java`)
1. Journal never loaded (no map entry) + escort with valid tags → **tags survive**, mob idles.
2. Journal loaded, quest present → follows.
3. Journal loaded, quest absent → tags cleared (existing behaviour preserved).
4. Journal never loaded → tags survive, then journal arrives with the quest → follows without
   re-activation.

### Verify
`./gradlew compileJava --no-configuration-cache`; `./gradlew test --no-configuration-cache`
(only the 21 known banner failures — see the M0 log); `./gradlew runGameTestServer --rerun-tasks
--no-configuration-cache` → **353 required tests** (the measured 349 baseline + 4), fresh-log
verified.

### Acceptance
No code path deletes escort tags on an `UNKNOWN` journal. Existing clearing behaviour on a real
`INACTIVE` answer is unchanged.

### Commit scope
The three source files + the new gametest + log entry.

---

# Milestone 2 — Observability and correlation id (S-1…S-12)

### Objective
Make every quest action explainable end to end, with **no behaviour change**. After this
milestone the question "what happened to player X's turn-in at 21:04" is answerable from logs
alone. This is what makes M3–M7 verifiable in production.

### Change — Minecraft

1. `QuestActionC2SPayload`: add a `String requestUuid` field (client-generated
   `UUID.randomUUID().toString()`), bump the payload version/codec accordingly. Validate in
   `QuestProxyService.isValidShape` (parseable UUID, else reject).
2. `QuestClient.send`: generate it, keep it in `PendingRequest`, log at `debug`.
3. `QuestProxyService`: forward it to Rails as `request_uuid` in the JSON body, and log
   structured events:
   ```
   event=quest_action_requested request_uuid=… action=… player_uuid=… quest_id=…
     quest_giver_uuid=… quest_giver_api_id=…
   event=quest_action_rejected  request_uuid=… stage=shape|journal_gate|npc_resolve
     reason=<machine_readable> journal_size=<n>
   event=quest_action_result    request_uuid=… rails_status=… success=… granted_item_count=…
   ```
   **S-1 is the single highest-value line in this milestone**: the 422 at
   `QuestProxyService.handle:45-49` currently logs nothing at all, and it is where a cold journal
   silently kills every quest action. It must emit a distinct `reason` for each of: bad shape,
   not-in-journal, npc-not-resolved.
4. `QuestProxyService.handle:57` (player reconnected mid-flight) and
   `applyAuthoritativeResult:118,146` — log at `warn` with the request uuid instead of returning
   silently.
5. `QuestServerAPI` callbacks that currently discard results
   (`QuestEventHandler.onEntityDeath:61,77`) — log failures.

### Change — Rails

6. Accept `request_uuid` in `QuestStatesController`; put it in every log line for the quest
   actions, matching the existing `[QuestTrigger]` style.
7. **`Processor#fail_fast("Quest not active.")` must log first** — this is the message affected
   players actually hit and it currently explains nothing:
   ```
   event=quest_turn_in_rejected request_uuid=… reason=state_not_active
     normalized_uuid=… user_id=… user_minecraft_uuid=… candidates=[…] matching_rows=<n>
   ```
   Include `matching_rows` = count of `player_quest_states` for this quest across the candidate
   list. **That number proves or disproves the Q-01 diagnosis in production.**
8. Never log the shard client secret, signature material, or tokens.

### Files
MC: `network/payload/QuestActionC2SPayload.java`, `quest/QuestClient.java`,
`quest/QuestProxyService.java`, `event/QuestEventHandler.java`, `quest/QuestServerAPI.java`.
Rails: `app/controllers/api/quest_states_controller.rb`, `app/services/quest_engine/processor.rb`.

### Tests
- MC unit: `QuestProxySecurityTest` extended — a payload with a malformed `requestUuid` is
  rejected; each rejection reason is distinct.
- MC gametest: a CHOOSE against an empty journal produces a `quest_action_rejected` with
  `reason=not_in_server_journal` (assert via a test-visible last-rejection hook, not by scraping
  logs).
- Rails: turn-in rejection logs `matching_rows`; assert with `assert_logged` or a log capture.

### Acceptance
Every rejection path in §8 of the health check emits exactly one structured line carrying the
request uuid. Behaviour is otherwise byte-identical — **the repro test still passes unchanged.**

### Commit scope
The listed files, their tests, log entry. Two commits (MC, Rails).

---

# Milestone 3 — Rails identity unification + journal uuid migration (Q-01, Q-07) ⟵ the blocker

### The defect, restated
`app/services/quest_engine/processor.rb:12` resolves the player's quest row by
`@player.minecraft_uuid` — byte-for-byte. `trigger_node`, `quit` and the world bootstrap all use
`minecraft_uuid_candidates` = `[normalized, compact, raw]`. Migration
`20260602001000_enforce_unique_minecraft_uuid_on_users` normalized `users.minecraft_uuid` and
fourteen other tables but **not `player_quest_states.player_uuid`**, so pre-migration journal rows
still carry compact/upper-case uuids. Those players see the quest, can progress it and can quit
it — and get `403 "Quest not active."` forever when they try to turn it in.

Reproduced in `test/controllers/api/quest_completion_identity_repro_test.rb` (4 tests, passing
against today's code).

### Change

1. **One resolver.** Add to `Api::QuestStatesController`:
   ```ruby
   def resolve_player!   # => [user, uuid_candidates]
   ```
   built from the existing `find_user_by_minecraft_uuid!` + `minecraft_uuid_candidates`
   (prefer `User.active_identity`, fall back, raise `RecordNotFound` with the candidates logged).
   Use it in **every** action: `start`, `interact`, `abandon`, `record_kill`, `clear_all`,
   `update_player_state`, `transition`, `trigger_node`, `quit`. Delete the three ad-hoc lookups.
2. **Processor takes candidates, not a string.** Change `QuestEngine::Processor#initialize` to
   accept `player_uuid_candidates:` and use
   `PlayerQuestState.active_journal.where(player_uuid: candidates, quest_id: @quest_id).first`.
   `transition` passes what `resolve_player!` returned. Do not leave a default that falls back to
   `@player.minecraft_uuid`.
3. **Writes normalize.** `PlayerQuestState` gains
   `before_validation { self.player_uuid = User.normalize_minecraft_uuid(player_uuid) if player_uuid.present? }`
   so no new row can ever be written in a legacy shape. Check the fixtures/tests that create rows
   directly still pass.
4. **Data migration.** New migration `normalize_player_quest_state_player_uuids`:
   - update every row whose `player_uuid` differs from its normalized form;
   - **guard the unique index** `index_player_quest_states_on_player_uuid_and_quest_id`: if
     normalizing would collide with an existing row for the same `quest_id`, keep the row that is
     `active_journal` (or, if both or neither are, the most recently `updated_at`) and mark the
     other `abandoned` rather than deleting it — never destroy player history;
   - log a per-row summary of what it did;
   - implement `down` as a no-op with a comment (normalization is not reversible and reversing it
     would recreate the bug).
5. Keep the candidate list in the resolver even after the migration — belt and braces for any row
   written by a path we have not seen.

### Files
`app/controllers/api/quest_states_controller.rb`, `app/services/quest_engine/processor.rb`,
`app/models/player_quest_state.rb`, `db/migrate/<ts>_normalize_player_quest_state_player_uuids.rb`,
`db/schema.rb` (regenerated).

### Tests
- **Flip the repro test**: `REPRO: turn-in is refused…` becomes `turn-in succeeds for a legacy
  compact uuid`; keep `BASELINE` and `ASYMMETRY`; rename the file to
  `quest_completion_identity_test.rb`. Its diff is the proof this milestone worked.
- New: every action resolves the same row for all three uuid shapes (table-driven).
- New: migration test — legacy row is normalized; a colliding pair keeps the active row and
  abandons the other; no row is deleted.
- New: `transition` for a user with a merged duplicate resolves the `active_identity` row.

### Verify
```bash
bash bin/codex_test test/controllers/api/quest_completion_identity_test.rb test/controllers/api/quest_states_controller_test.rb
SKIP_PERSISTED_SETTINGS=1 RAILS_ENV=test rbenv exec ruby bin/rails db:schema:dump   # RAILS_ENV=test!
ruby /tmp/reload_worker_schemas.rb
bash bin/codex_test        # full suite
```
Expect the full suite at its M0 baseline count + the new tests, with only the known deterministic
`CityFoodSupplyRecalculatorTest` failure (99.7 ≠ 100.0) and the known spawn-point concurrency flake.

### Acceptance
- No quest code path resolves a player by a bare `minecraft_uuid` equality any more
  (`grep -rn "minecraft_uuid: " app/controllers/api/quest_states_controller.rb app/services/quest_engine/` is clean).
- A player with a legacy journal row can complete a quest.
- The migration is idempotent (running it twice changes nothing).

### Commit scope
The listed Rails files + tests + log entry. **Rails only** — no Minecraft change belongs here.

### Owner note
This is the milestone that makes the reported bug stop. After it lands, ask the owner whether to
deploy M3 before continuing.

---

# Milestone 4 — Idempotent completion transaction (Q-04, Q-10, Q-12)

### The defect, restated
`Processor#call` applies effects inside `EffectApplier`'s transaction but advances the state
**outside** it, takes no row lock, and returns `granted_items` **only once**: the items exist
solely in that HTTP response and are materialised by `QuestRewardService.apply` on the Minecraft
side. If the response is lost (timeout, disconnect, `handle:57` player-reconnect drop), Rails has
committed the completion and the player has nothing — and the retry returns
`403 "Quest not active."`. Confirmed by the repro test's fourth case.

### Change

1. `player_quest_states` gains `completion_request_uuid` (string, indexed, nullable) and
   `completion_result` (jsonb, nullable) — the exact payload returned when the quest was
   completed.
2. `Processor#call`:
   ```ruby
   ActiveRecord::Base.transaction do
     state = scope.lock!                      # row lock, Q-12
     if state.completed? && state.completion_request_uuid == @request_uuid
       return state.completion_result         # idempotent replay, Q-04
     end
     ...effects + state advance in ONE transaction...   # Q-10
   end
   ```
   Replay returns the stored result **including `granted_items`**, so a Minecraft retry grants the
   reward exactly once overall.
3. A completion request with a *different* `request_uuid` against an already-completed state keeps
   returning "already completed" (not a replay) — with a distinct machine reason.
4. Minecraft: `QuestProxyService` retries a CHOOSE **once** on 503/timeout with the *same*
   `requestUuid`, then surfaces a clear message. Do not add blind retry loops anywhere else.
5. `QuestRewardService.apply` must remain the only item granter and must stay bounded.

### Files
Rails: `app/services/quest_engine/processor.rb`, `app/controllers/api/quest_states_controller.rb`
(trigger_node's completion branch gets the same treatment),
`db/migrate/<ts>_add_completion_idempotency_to_player_quest_states.rb`, `db/schema.rb`.
MC: `quest/QuestProxyService.java`.

### Tests
- Same request uuid twice → identical body, one reward, one completion.
- Different request uuid after completion → explicit already-completed, no reward.
- Two concurrent completions (threads or `lock!` assertion) → one advance, one rejection.
- `EffectApplier` raising mid-way → no state advance, no partial effects.
- MC gametest: 503 then success on the same requestUuid grants items once.

### Verify
Migration → `RAILS_ENV=test` schema dump → worker reload → focused then full Rails suite;
MC compile + gametests.

### Acceptance
No sequence of retries produces two rewards or a completed-but-unrewarded quest.

### Commit scope
Two commits (Rails, MC) + log entry.

---

# Milestone 5 — Durable journal and fail-safe gate (Q-03, Q-09, Q-17)

### The defect, restated
`ServerQuestTable` is a static in-memory map that authorizes every CHOOSE/TRIGGER/ABANDON
(`QuestProxyService.authorizedForCurrentJournal`). It is filled **only** by
`WorldBootstrapHandler.apply`, and `WorldBootstrapHandler.complete:221` returns early — leaving it
empty — on `overall_timeout`, `request_error`, any `data.failureCode()`, `queue_rejected`,
`stale_generation`, `cancelled_player_session`, and when server auth is unavailable. Such a player
gets 422 for the whole session. The map is also never cleared on logout, so a stale journal is
reused when a later bootstrap fails.

There is currently **no working per-player journal endpoint** to refresh from: `routes.rb:291-292`
declares `get 'status/:player_uuid' => quest_states#show` **twice** and `#show` does not exist
(Q-17); `Endpoint.QUEST_JOURNAL` is declared in `RailsApiUrlResolver` and never called.

### Change

1. **Rails**: implement `QuestStatesController#show` — shard-scoped, candidate-aware, returning
   `{ accepted_quests: [QuestJournalEntrySerializer…] }` exactly as the world bootstrap does
   (reuse `accepted_quest_states_for`'s query shape). Delete the duplicated route line.
2. **Minecraft**: `ServerQuestTable` gains a load-state per player
   (`UNKNOWN` / `LOADED`) — reuse the M1 enum.
3. `QuestProxyService.authorizedForCurrentJournal`: on a miss where the journal is `UNKNOWN`,
   **do not reject**. Trigger a one-shot journal fetch via `Endpoint.QUEST_JOURNAL`, then
   re-evaluate. Only reject when the journal is `LOADED` and the quest is genuinely absent — and
   log that with the M2 reason code and `journal_size`.
4. Rate-limit the refetch (one in flight per player, short cooldown) so a hostile or looping
   client cannot turn a miss into a Rails flood.
5. Clear the player's entry on `PlayerLoggedOutEvent` (Q-09) — safe only now that a miss
   re-fetches rather than rejects.
6. `WorldBootstrapHandler.complete` failure branch: log explicitly that the quest journal is
   unloaded for this player and that quest actions will refetch.

### Files
Rails: `app/controllers/api/quest_states_controller.rb`, `config/routes.rb`.
MC: `quest/ServerQuestTable.java`, `quest/QuestProxyService.java`,
`event/WorldBootstrapHandler.java`, possibly a small `quest/QuestJournalRefresh.java`.

### Tests
- Rails: `#show` returns the journal for all three uuid shapes; shard-scoped; requires auth.
- MC gametest: CHOOSE with an unloaded journal → refetch → authorized (not 422).
- MC gametest: CHOOSE with a loaded journal that genuinely lacks the quest → rejected with the
  reason code, and **no** refetch storm on repeated attempts.
- MC gametest: logout clears the entry; the next action refetches.

### Acceptance
A failed login bootstrap no longer disables quests for the session. No unbounded refetching.

### Commit scope
Two commits (Rails, MC) + log entry.

---

# Milestone 6 — Server-authoritative objectives (Q-02, Q-05, Q-06)

### The defect, restated
Location, pickup and lava-destroy objectives are detected **on the client** and asserted to the
server: `QuestEventHandlers.onPlayerTick:68` (client branch), `onItemPickup:96`
(`if (!isClientSide) return;`), `QuestPayloadHandler.ClientProxy.evaluateLavaQuest`. They all read
one client static, `QuestManager.currentQuestState`, whose only writers are
`QuestClient.processResponse:99` and `ClientNetworkHandler:255` — **neither runs at login**. So:
after a relog no environmental trigger fires at all, with two active quests only the most recently
touched one can progress, and a modified client can fire any `trigger_key` it likes. The location
check also re-fires every 20 ticks with no debounce.

The lava path already has a correct server-side implementation to copy:
`QuestEventHandlers.onItemEntityTick` (server-side, validates the stamped destroy volume, the item
match and the owner, then calls `QuestServerAPI.sendTrigger`).

### Change

1. Move location and pickup detection to the **server** tick, iterating that player's
   `ServerQuestTable` journal (all active quests, not one "current" quest).
   - Trigger metadata must be available server-side. It arrives today only inside the Rails
     response the client keeps. Cache the active node metadata per (player, questStateId) in
     `ServerQuestTable` when `applyAuthoritativeResult` runs, and refresh it via the M5 journal
     fetch. If the metadata is unknown, do nothing — **never** ask the client for it.
2. Per (questStateId, triggerKey) fire-once guard plus an in-flight guard (Q-06).
3. Pickup: detect on `ItemEntityPickupEvent` **server-side** and verify the stack actually entered
   that player's inventory.
4. Delete the client-side trigger senders. `QuestManager` stays only as the dialogue-screen view
   model; nothing gameplay-authoritative may read it.
5. `QuestClient.sendTrigger` may remain for genuinely client-initiated UI actions only — if
   nothing needs it, remove it and its `TRIGGER` action path.

### Files
`quest/QuestEventHandlers.java`, `network/QuestPayloadHandler.java` (`ClientProxy`),
`quest/ServerQuestTable.java`, `quest/QuestClient.java`, `quest/QuestManager.java`.

### Tests (GameTests)
- Player relogs mid-quest, walks into the zone → trigger fires (today it does not).
- Two active quests, both with location triggers → both progress.
- Standing in a zone for 10 seconds → exactly one trigger.
- A forged client TRIGGER for an objective the player has not met → rejected and logged.
- Pickup trigger fires only when the item really reached the inventory.

### Acceptance
No gameplay-authoritative decision is made client-side. Relog no longer breaks progression.

### Regression risk
Highest in the plan — it changes which quests can progress. Test with two players.

### Commit scope
MC only + log entry.

---

# Milestone 7 — QuestGiver identity and escort lifecycle (Q-08, Q-11, Q-13, Q-14)

### The defect, restated
The Rails join key for a quest giver is the substring after `:` in `personalName`
(`"Display:api_id"`, parsed by `QuestProxyService.internalApiId` and
`QuestPayloadHandler.internalApiId`). Any write to `personalName` without that encoding silently
repoints the NPC at a different quest or none. `QuestGiverEntity extends CitizenEntity` and so
already has `worldNpcPublicId`, which the quest spawner never sets.

`activateEscort` (`QuestPayloadHandler.java:111-128`) **discards** the quest giver and creates a
new entity with a new UUID, dangling any open dialogue's `quest_giver_uuid`, and carries the
assignment in string tags.

`Endpoint.QUEST_QUIT` names its path parameter `quest_state_id` while the route is `:id`, and
`find_quittable_state` accepts **either** a state id or a quest id, guessing with a warning log.

Several quest files sit in `quest/` while declaring `quest.network` / `quest.events` packages.

### Change

1. Add a synched + saved `questGiverApiId` field on `QuestGiverEntity` (or `CitizenEntity` if
   cleaner). `QuestGiverSpawnBlockEntity` sets it explicitly alongside `personalName`.
   `internalApiId` reads the field and falls back to the legacy `:` parse **only** when the field
   is blank, logging when it does — so existing world entities keep working and you can see how
   many are still legacy.
2. `activateEscort`: attach the escort behaviour to the **existing** entity instead of
   discard-and-recreate. If replacement is genuinely unavoidable, return the new UUID to the
   client and update the open screen's context.
3. Make the quit contract single-meaning: `quest_state_id` end to end. Keep the quest-id fallback
   for one release, still warning, then note its removal in the log.
4. Move the mis-located files into directories matching their packages (pure move, no content
   change, separate commit so the diff is reviewable).

### Files
`entity/QuestGiverEntity.java`, `entity/CitizenEntity.java` (if the field lands there),
`block/entity/QuestGiverSpawnBlockEntity.java`, `quest/QuestProxyService.java`,
`network/QuestPayloadHandler.java`, `server/http/RailsApiUrlResolver.java`; Rails
`quest_states_controller.rb` (`find_quittable_state`), `config/routes.rb`.

### Tests
- A giver with only a legacy `"Name:api_id"` still resolves (and logs the fallback).
- A giver with the field set resolves from the field even if `personalName` is rewritten.
- Escort activation keeps the entity UUID; an open dialogue's CHOOSE still resolves the giver.
- Quit by `quest_state_id` works; quit by quest id works and warns.

### Acceptance
Quest giver identity survives a rename, a reload and an escort activation.

### Commit scope
Two or three commits (feature, file moves, Rails) + log entry.

---

# Milestone 8 — Regression suite and failure-injection validation (Q-15)

### Objective
Turn §12 of the health check into a permanent suite and prove the system under multiplayer and
failure conditions. No production behaviour changes.

### Required tests

**Rails**
| Scenario | Assertion |
| --- | --- |
| Happy-path turn-in | completes once, rewards once |
| Two players, same quest | A completing leaves B active; B completes later |
| Duplicate completion (same request uuid) | identical body, one reward |
| Concurrent completion | one advance, one explicit rejection |
| Legacy uuid shapes | every action resolves the same row |
| Missing Rails user | deliberate, observable 404 |
| Merged duplicate user | resolves the active identity |
| `#show` journal | all uuid shapes, shard-scoped, authenticated |

**GameTest**
| Scenario | Assertion |
| --- | --- |
| Reconnect before turn-in | journal refetched, CHOOSE authorized |
| Bootstrap failure | actions refetch instead of 422 |
| Server restart | first quest action after restart succeeds |
| Escort present at restart | assignment tags survive a cold journal |
| Death / respawn | quest data and escort assignment survive |
| QuestGiver reload | turn-in works with a new runtime entity id |
| Wrong QuestGiver | explicit, logged rejection |
| Stale client | explicit failure with a recoverable message |
| Forged pickup trigger | rejected |
| Rails 503 during turn-in | one retry, single reward |

### Verify
Full Rails suite; `./gradlew test`; full GameTest server run with a fresh-log tally. State the
new required-test count explicitly.

### Acceptance
Every scenario above is automated and green. Re-answer the health check's final question with
evidence.

### Commit scope
Tests + a `QUESTENGINE_HEALTH_CHECK.md` addendum recording the new verdict + log entry.

---

# 7. Definition of done for the whole programme

The QuestEngine is healthy when:

- no player can hold a quest they are unable to turn in (M3);
- a lost response never costs a reward and never double-grants one (M4);
- no quest action is refused because of a transient in-memory condition, and every refusal is
  logged with a machine-readable reason and a correlation id (M2, M5);
- no persisted state is destroyed by a cold cache (M1);
- objective progress is decided by the server (M6);
- quest giver identity survives rename, reload and escort activation (M7);
- all of it is covered by automated tests that run in CI (M8).
