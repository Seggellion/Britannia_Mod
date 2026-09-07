# Rowan the Farmer — From Soil to Supper: implementation status

Living scratchpad for the cross-repository project defined by
`ROWAN_FARMING_QUESTLINE_PROJECT_PLAYBOOK.md` (owner's copy at the NeoForge repository root) and
the three discovery documents in this folder. Updated at the close of every milestone. Nothing in
this document is pushed, deployed, seeded to a live database, or uploaded; the release handoff
(M12) lists the live gates the owner performs.

| Repository | Branch | Base | Worktree |
| --- | --- | --- | --- |
| NeoForge mod | `claude/rowan-farming-questline-mod` | `patch-18` @ `421e2785` (= private patch-18 tip; superset of the sanitised `origin/patch-18` `5c0b9172`, which additionally lacks the creative-mining fix) | `C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\rowan-farming-questline-mod` |
| Rails site | `claude/rowan-farming-questline-rails` | `release/public` @ `a9425ca` | `/home/dusti/ultimacraft-website/.claude/worktrees/rowan-farming-questline-rails` |

Both worktrees were created from clean bases; the canonical checkouts (mod `patch-18` with the
owner's untracked farming notes, Rails `release/public` with the owner's uncommitted changes)
were not switched, stashed, reset, or modified.

## Milestone ledger

| Milestone | Status | Mod commit | Rails commit | Closed |
| --- | --- | --- | --- | --- |
| M0 Baselines, worktrees, executable contracts | **PASSED** | `45ad0434` | `3400742` | 2026-09-06 |
| M1 Quest security and permanent-item safety | **PASSED** | `85291cb5` | — | 2026-09-07 |
| M2 Rails reward-delivery ledger and replay | **PASSED** | — | `78d640a` | 2026-09-07 |
| M3 NeoForge reward reconciliation | **PASSED** | `71f1713b` | — | 2026-09-07 |
| M4 Rails action-objective and progress contract | **PASSED** | — | `0fff0b3` | 2026-09-07 |
| M5 NeoForge farming events, outbox, crop attribution | **PASSED** | (this commit) | — | 2026-09-07 |
| M6 Rowan archetype | not started | | | |
| M7 Rails content, seed, admin | not started | | | |
| M8 Dialogue and journal UX | not started | | | |
| M9 Timing, skill, recovery | not started | | | |
| M10 Achievement and advancement | not started | | | |
| M11 Hardening and automated acceptance | not started | | | |
| M12 Live acceptance and release handoff | not started | | | |

## M0 — Baselines, worktrees, executable contracts (PASSED 2026-09-06)

### Changes

NeoForge (`docs/`, `src/test/` only; no `src/main/` change):

* `docs/projects/rowan-farming-questline/ROWAN_FARMING_QUESTLINE_{DISCOVERY,PLANNING_INPUTS,ACCEPTANCE_DRAFT}.md`
  — the exploration deliverables, committed unchanged (all 58 acceptance boxes unchecked).
* `docs/projects/rowan-farming-questline/ROWAN_FARMING_QUESTLINE_PROTOCOL.md` — the frozen
  integration contract (reward deliveries v1, action events v1, journal/dialogue additions,
  compatibility matrix, save/recovery model, HTTP status table, fixture index).
* `src/test/resources/quest_contract/v1/*.json` + `MANIFEST.sha256` — twelve mirrored fixtures.
* `src/test/java/.../quest/contract/QuestContractFixturesTest.java` — verifies the manifest is
  exactly the frozen index, every file on disk is listed, every digest matches after LF
  normalisation, and every fixture parses with `protocol_version` 1.
* This status document.

Rails (`docs/`, `test/` only; no `app/`, `db/`, `config/` change):

* `docs/rowan_farming_questline_protocol.md` — byte-identical mirror of the protocol document.
* `test/fixtures/quest_contract/v1/*.json` + `MANIFEST.sha256` — the same twelve fixtures.
* `test/contracts/quest_contract_fixtures_test.rb` — the same four checks.

### Baselines (unmodified bases, before any M0 file existed)

| Suite | Result |
| --- | --- |
| Mod focused JUnit (quest, network, farming, wild-resource, bowl, dirt selection) @ 421e2785 | 264 tests, 0 failures, 0 errors, 6 skipped (assumption skips), 4 m 24 s |
| Mod full JUnit @ 421e2785 | 3479 tests, 0 failures, 0 errors, 17 skipped, 449 classes |
| Mod GameTests (`runGameTestServer`, `run/gametest`) @ 421e2785 | 1102 required tests, all passed, 52.4 s |
| Rails focused quest selection @ a9425ca (`ultimacraft_test-99`, disposable) | 58 runs, 405 assertions, 0 failures |
| Rails full suite @ a9425ca (`ultimacraft_test-99`, disposable, dropped afterwards) | 3178 runs, 51390 assertions, **6 failures** (pre-existing), 0 errors, 1 skip |

Pre-existing Rails failures at the base, none in quest, API or farming code, left untouched and
reported to the owner rather than fixed here:

1. `ShardPlatformQueryBudgetTest#test_public_platform_endpoints_stay_inside_explicit_database_query_budgets`
   (city endpoint measured 22 queries against its budget).
2. `Economy::CityFoodSupplyRecalculatorTest#test_city_food_consumption_deducts_from_commodities_and_rebuilds_summary`
   (expected 100.0, actual 99.7).
3. – 6. `Admin::RedeemShardResetsControllerTest` — four refusal cases expect 0 rows cleared and
   observe 4 (`…without_the_shard_name_typed…`, `…naming_a_different_shard…`,
   `…non-admin_cannot_execute…`, `…without_a_reason…`).

The milestone gate for later Rails full runs is therefore "3178 + new runs, exactly these six
failures and no others".

### New tests

| Test | Result |
| --- | --- |
| Mod `QuestContractFixturesTest` | 26 dynamic/static tests, 0 failures |
| Rails `QuestContractFixturesTest` | 4 runs, 36 assertions, 0 failures |
| Negative control (one fixture tampered with a trailing newline, then restored) | Rails: 1 failure naming the drifted fixture. Mod: 2 failures (drifted digest, plus the unlisted backup file). Both green again after restore; manifests verify 12/12 on both sides. |

### Gates

* No unrelated diff in either worktree (`git status` shows only the files above).
* Whitespace clean (`git diff --cached --check`) in both repositories.
* Base includes all accepted Patch 18 work (private `patch-18` tip).
* Contract written and executable before any implementation code.
* Owner's untracked files in the canonical mod checkout (`FARMING_EMPTY_INVENTORY_TEST_CHECKLIST.md`,
  `FARMING_GAMELOOP_DISCOVERY.md`, `docs/projects/gameplay-bugfixes/`, the playbook) and the
  owner's Rails changes are untouched. The playbook is deliberately **not** copied into the
  branch: it is the owner's root document and carries Markdown hard-break trailing spaces that
  would fail the whitespace gate.

### Decisions frozen in M0 (see the protocol document for the full text)

* Reward deliveries are Rails rows created inside the transition transaction, replayed by
  `request_uuid` for every transition (not only endings), and acknowledged by the Minecraft
  server only after its own fsync'd ledger and player marker exist (`applied`/`queued`).
* Only `temporary: true` items are stamped for cleanup; coins, tools, seeds, bowls, buckets and
  produce are permanent. Legacy stamped stacks are un-stamped, never deleted (M1).
* Objectives are server-created action events with a persistent, bounded, retrying outbox; the
  client `TRIGGER` payload stays refused. Rails answers `applied | duplicate | irrelevant |
  stale | rejected`, always idempotent on `event_uuid`.
* Plot/crop attribution travels as `plot_key`, `crop_cycle_uuid`, `planter_uuid`; ordered
  `action_steps` with `bind`/`require_bound`/`deadline`, and `action_trigger` for the advancing
  step. Restarting from an earlier step is allowed; skipping ahead is rejected.
* Help/mixing-guide choices are `presentation: "help"`, rendered client-side and refused by Rails
  as a transition (`422 presentation_only`).
* Quest-giver spawn configuration requires Creative or permission level 2 within reach (M1).

### Defect register (from discovery; status)

| Id | Defect | Milestone | Status |
| --- | --- | --- | --- |
| D1 | Every stamped reward deleted at next login by stale-quest cleanup | M1 | open |
| D2 | Non-ending grants not replay-protected in Rails | M2 | open |
| D3 | Quest-giver spawn config packet applied without permission/reach check | M1 | open |
| D4 | Trigger popup says "The Guardian" for non-escort triggers | M8 | open |
| D5 | Silver/copper coin lang entries missing | M1 | open |
| D6 | Community plot 60 s seed window and random-tick reclaim | M9 | open |
| D8 | `key.britannia_mod.open_skills` untranslated | M1 | open |
| D11 | Farming cultivation gate has no retry when skill state is not yet available | M9 | open |

### Next action (at M0 close)

M1 — Quest security and permanent-item safety (NeoForge only).

## M1 — Quest security and permanent-item safety (PASSED 2026-09-07, NeoForge only)

Delegated to a NeoForge sub-agent (one attempt was cut off by a usage limit and resumed by a
second agent from the same uncommitted diff); reviewed hunk by hunk and gated by the integrator.

### Changes (`src/main`, `src/test`, one lang file; no Rails change)

* `quest/QuestItemStamp.java` (new) — the stamp vocabulary and the two shapes it can take:
  *temporary* (stamp + `quest_trigger_key`) and *legacy* (the pre-M1 blanket stamp, no trigger
  key). `strip()` removes every stamp key and drops an emptied custom-data component so a
  stripped coin merges with plain coins again.
* `quest/QuestTemporaryItemPolicy.java` (new) — the frozen §1.5 decision: a
  `reward_delivery.items[].temporary` verdict wins when present (malformed flag = no verdict);
  otherwise the destination node's `destroy_trigger`/`pickup_trigger` `item_tag` naming the item
  (bare, namespaced, or the resolved registry id) makes it temporary with that trigger key and,
  for destroy, the volume; everything else is permanent.
* `quest/QuestRewardService.java` — stamps only temporary decisions (`stampTemporary`), never
  writes the player name, logs `event=quest_reward_stamped_temporary`; new overload
  `apply(player, response, rawResponse)`; `quest/QuestProxyService.java` passes the raw body.
* `quest/QuestCleanupService.java` — one `sweepItems` pass over main inventory, armour and off
  hand: temporary + in delete scope → deleted; legacy + in strip scope → stamp stripped, item
  kept; nothing else touched. Login pass deletes only owner-matched temporary stamps whose quest
  left the journal and strips legacy stamps whose quest left the journal or that name no quest;
  quit pass is scoped to the quit quest. Log lines gain `stripped_legacy_stamps=`.
* `network/NetworkHandler.java` — `applyQuestGiverSpawnConfig` gates the payload on Creative or
  permission level 2, then `canInteractWithBlock(pos, 1.0)` reach (checked before the block
  lookup so a far position never loads a chunk), then a real `QuestGiverSpawnBlockEntity`, then
  payload shape; rejection logs `event=quest_giver_spawn_config_rejected reason=… player=<uuid>
  pos=…` and changes nothing. The packet listener now only delegates.
* `network/QuestGiverSpawnConfigC2SPayload.java` — `shapeViolation()`/`isValidShape()`
  (wire format unchanged): archetype must be one the spawner supports, city required, texts ≤ 64
  chars without control characters, Generic Combat needs a Rails api id, gender ∈ {male, female},
  wander radius 0–64. `block/entity/QuestGiverSpawnBlockEntity.java` exposes
  `SUPPORTED_ARCHETYPES` (server authority; a unit test proves it equals the screen's list — M6
  must add Rowan to both).
* `assets/britannia_mod/lang/en_us.json` — `item.britannia_mod.silver_coin`,
  `item.britannia_mod.copper_coin`, `key.britannia_mod.open_skills`.
* Tests: `QuestItemStampTest`, `QuestTemporaryItemPolicyTest`, `QuestCleanupDecisionTableTest`
  (the full decision table over real `NonNullList` slots), `QuestRewardLocalizationTest`,
  `network/payload/QuestGiverSpawnConfigPayloadValidationTest`; GameTests
  `QuestRewardDurabilityGameTests` (5: the kit survives login-active, completion+relog, quit,
  empty-journal login and journal refresh; the ring is stamped and taken back at quest end and
  quit; a `temporary:false` verdict is never stamped; legacy stamps stripped across inventory,
  armour and off hand; escort silver survives relog and quit) and
  `QuestGiverSpawnConfigGameTests` (5: survival crafted packet, out of reach, no spawner,
  malformed payload, authorised save).
* Out-of-boundary but necessary: `gametest/BankItemEligibilityGameTests`,
  `BankingDepositProxyServiceGameTests`, `BankingTransferPacketServiceGameTests` built their
  "quest-bound ring" through `QuestRewardService.apply`; they now supply the destination-node
  `destroy_trigger` so the ring is still stamped, which keeps the bank's refusal of quest items
  under test with the real policy.

### Integrator corrections

* Wander radius lower bound relaxed from 1 to 0: `applyConfig` and the block entity accepted 0
  before M1 (a leash of 0 blocks keeps the NPC at its post), so refusing it would have silently
  rejected an operator's existing habit. `-1` stays refused (it means "no restriction" to the
  mob's leash). Unit and GameTest expectations updated accordingly.

### Decision tables (as implemented and tested)

| Grant input | Stamped? | Trigger key |
| --- | --- | --- |
| `reward_delivery.items[]` says `temporary:false` | no | — |
| `reward_delivery.items[]` says `temporary:true`, objective names it | yes | the objective's |
| `reward_delivery.items[]` says `temporary:true`, nothing names it | yes | `temporary_item` |
| no verdict, destination `destroy_trigger.item_tag` names it | yes (+ volume) | the objective's |
| no verdict, destination `pickup_trigger.item_tag` names it | yes | the objective's |
| no verdict, no objective names it (kit, coins, escort silver) | no | — |

| Stamp on the stack | Quest active | Login pass | Quit pass (that quest) |
| --- | --- | --- | --- |
| temporary (has trigger key), owner matches | no | delete | delete |
| temporary, owner matches | yes | keep, still stamped | keep (other quest) |
| temporary, owner differs | any | keep | keep |
| legacy (no trigger key) | no | strip, keep item | strip, keep item |
| legacy | yes | keep, still stamped | keep (other quest) |
| unstamped | — | untouched | untouched |

A pre-M1 magic ring already carried `quest_trigger_key` (the old code added it when the destroy
objective matched), so it is classified temporary and still taken back; pre-M1 coins, tools and
escort silver carried no trigger key and are stripped, never deleted.

### Tests and gates

| Run | Result |
| --- | --- |
| Full JUnit (agent's gate, before the radius correction) | 3558 tests, 0 failures, 0 errors, 17 skipped (455 suites; +79 over baseline) |
| GameTests (agent's gate, before the radius correction) | 1112 required tests passed (1102 baseline + 10 new), 0 failed |
| Full JUnit (integrator, final tree) | 3558 tests, 0 failures, 0 errors, 17 skipped (455 suites) |
| GameTests (integrator, final tree) | 1112 required tests passed, 0 failed (a first attempt was invalidated by a stray Gradle build left by the terminated agent: it held the JUnit result file and rewrote resources mid-scan; daemons stopped, re-run clean) |
| `git diff --check` | clean |

Behaviour change to note for operators (M12 guide): a spawn configuration with a blank city is
now refused (the city feeds the escort origin and the NPC's home; a blank one produced
`unknown` before).

### Defect register update

| Id | Status |
| --- | --- |
| D1 | **fixed** (M1) — permanent rewards unstamped; legacy stamps stripped |
| D3 | **fixed** (M1) — permission/reach/block-entity/payload gates |
| D5 | **fixed** (M1) — coin lang entries |
| D8 | **fixed** (M1) — keybinding lang entry |

### Next action (at M1 close)

M2 ran in parallel in the Rails worktree; M3 starts after both are committed.

## M2 — Rails reward-delivery ledger and replay (PASSED 2026-09-07, Rails only)

Delegated to a Rails sub-agent (cut off twice by usage limits and resumed from the same
uncommitted diff); reviewed hunk by hunk and gated by the integrator, who also ran the
migration⇔schema proof and the suites.

### Changes (`app/`, `config/routes.rb`, `db/`, `test/`)

* `db/migrate/20260906120000_create_quest_reward_deliveries.rb` — the §1.2 table: uuid default
  `gen_random_uuid()`, FKs `shards`/`users` restrict, `quests`/`player_quest_states`/
  `minecraft_servers` nullify, unique `delivery_uuid`, partial unique
  `(player_quest_state_id, transition_key)`, `(shard, player_uuid, state)` and partial
  `(shard, player_uuid, request_uuid)` indexes, three check constraints (state set, items array,
  state⇔timestamps⇔outcome agreement).
* `db/schema.rb` — hand-edited block in alphabetical position plus five `add_foreign_key` lines,
  version stamp `2026_09_06_120000`. Proof on a second disposable database: schema load →
  `db:migrate:down` → `db:migrate` rebuilds the table; `\d` matches; an in-memory
  `SchemaDumper` rendering of the block is byte-identical to the hand-edited text (36 lines);
  table order and stamp agree. (The dumper also renders the pre-existing
  `blessed_item_materializations` check constraint differently on PostgreSQL 18, which is exactly
  why `schema.rb` is never re-dumped here.)
* `app/models/quest_reward_delivery.rb` — `PROTOCOL_VERSION = 1`, validations mirroring every
  constraint (1–32 items of exactly `{id, count, temporary}`, namespaced ids, count 1–1024),
  `attr_readonly` on identity/items/response, `contract_payload` (§1.3) and
  `pending_listing_payload` (§1.6). Associations: `PlayerQuestState has_many :reward_deliveries,
  dependent: :nullify`; `Shard`/`User has_many … dependent: :restrict_with_exception`.
* `app/services/quest_reward_deliveries/publish.rb` — creates the delivery inside the caller's
  transaction and row lock; adopts an existing row for the same `(state, transition_key)` (lookup,
  then `RecordNotUnique`/uniqueness-validation backstops) so a second claim reuses the same
  `delivery_uuid`; decides `temporary` per §1.5 (`give_temporary_item` ids or the destination
  node's `pickup_trigger`/`destroy_trigger` `item_tag`); namespaces bare ids; raises
  `InvalidGrant` (→ transaction rollback, `422 "Reward could not be delivered."`) for grants the
  contract refuses. `replay.rb` — `by_request` (same request uuid, any transition kind → stored
  response verbatim) and `for_transition` (fresh request uuid for a transition this run already
  made → same delivery, `"replayed": true`, no second advance). `pending.rb` — one scope shared
  by the v2 listing and the bootstrap (pending only, `created_at, id`, limit 50).
  `accept_result.rb` + `result_contract.rb` — §1.7 under `with_lock`: shard ownership, path/body
  uuid agreement, player resolution to the delivery's user; `applied`/`queued` idempotent
  (`duplicate: true`), `queued → applied` upgrade, `applied → queued` conflict; Rails' clock only.
* `app/services/quest_engine/effect_applier.rb` — `give_temporary_item` (same three shapes as
  `give_item`) and `temporary_item_ids` in the result; 39-line semantic change after the
  integrator asked for the whole-file rewrite to be undone.
* `app/services/quest_engine/processor.rb` — delivery replay before the lock; `requires_new`
  savepoint; delivery published after the advance with `transition_key
  "<accepted_at>:<node>:choice:<choice_id>"`; an unknown choice consults the replay before
  answering "Invalid choice."; `reward_delivery: nil` in every success response.
* `app/controllers/api/quest_states_controller.rb` — `trigger_node` refactored to run under
  `state.lock!` in one transaction (`apply_trigger`/`render_trigger_outcome`), with the same
  replay-by-request, `trigger`-keyed delivery, replay-by-transition fallback, and the `picked_up_`
  flag fallback preserved.
* `app/controllers/api/v2/quest_reward_deliveries_controller.rb` + `config/routes.rb` —
  `GET /api/v2/quest_reward_deliveries/pending`, `POST …/:delivery_uuid/result` on the v2 tier
  (`REQUIRE_SIGNATURE = true`, `Minecraft-Server-Key`, 300/min rate limit) with the §6 codes.
* `app/controllers/api/world_bootstrap_controller.rb` — additive `pending_reward_deliveries`
  (same element shape), nil-safe, folded into the ETag.
* Tests (new): `test/models/quest_reward_delivery_test.rb`, `test/services/quest_reward_deliveries/
  {publish,replay_concurrency}_test.rb` (real concurrent claims/retries through the row lock and
  the unique-index backstop), `test/controllers/api/quest_reward_delivery_transitions_test.rb`
  (choice and trigger paths: lost acceptance response replay, fresh-uuid replay, refused grants,
  pending across sessions), `test/controllers/api/v2/quest_reward_deliveries_controller_test.rb`
  (auth tiers, listing, every acknowledgement outcome and error code),
  `test/controllers/api/world_bootstrap_reward_deliveries_test.rb`,
  `test/contracts/quest_reward_delivery_contract_test.rb` (real controllers against the four M0
  fixture shapes). Adjusted: the bootstrap key-set assertion gains `pending_reward_deliveries`;
  `completion_concurrency_test.rb` teardown deletes deliveries before users/shards (RESTRICT FKs).

### Tests and gates

| Run | Result |
| --- | --- |
| Selection (contracts, delivery model/services, quest engine incl. completion concurrency, quest states controller, transitions, v2 deliveries, bootstrap, blessed v2 control) on `ultimacraft_test-97` | 122 runs, 638 assertions, 0 failures, 0 errors |
| Full suite on `ultimacraft_test-97` (seed 63781) | 3263 runs (+85), 51864 assertions, 6 failures, 0 errors, 1 skip — see the note below |

Full-suite failure note. Two of the six are the stable baseline failures (`ShardPlatformQueryBudgetTest`
city budget, `Economy::CityFoodSupplyRecalculatorTest` 100.0 vs 99.7). The other four differ from
the baseline's four (`Admin::RedeemShardResetsControllerTest` ×4 at M0; here
`Admin::BankChequesControllerTest` ×3 and `Admin::BankReconciliationsControllerTest` ×1), so a
control experiment was run: the three admin files in isolation on the M2 tree against a **fresh**
disposable database (`ultimacraft_test-94`) pass twice in a row (31 runs, 0 failures, 0 errors),
while the same files on the reused `ultimacraft_test-97` fail 8 of 31 and on the untouched base
`a9425ca` (temporary detached worktree, fresh `ultimacraft_test-95`) show 1 unrelated error. The
admin failures are therefore database-state pollution between tests (order/seed dependent) that
predates this project and never touches quest or delivery code; the gate for later runs stays
"3263 + new runs, the two stable failures plus admin-state flakes only". The control worktree and
all disposable databases were removed.
| Migration ⇔ schema proof on `ultimacraft_test-96` | passed (see above); both disposable databases dropped afterwards |
| `git diff --check` | clean |

### Decision tables (as implemented and tested)

| Retry | Node already advanced by this run | Answer |
| --- | --- | --- |
| same `request_uuid` | yes | stored response verbatim (same `delivery_uuid`), any transition kind |
| different `request_uuid`, choice/trigger no longer on the current node | yes | same delivery, `"replayed": true`, no advance |
| different `request_uuid`, ending already completed | yes | refused as before (`completion_result` replay only for the winning uuid) |
| any, choice/trigger valid on the current node | no | normal advance; delivery created in the same transaction |

| Delivery state | `applied` report | `queued` report |
| --- | --- | --- |
| pending | → acknowledged/applied, `duplicate: false` | → acknowledged/queued, `duplicate: false` |
| acknowledged/queued | → applied (upgrade), `duplicate: false` | `duplicate: true` |
| acknowledged/applied | `duplicate: true` | `409 conflicting_delivery_result` |

### Defect register update

| Id | Status |
| --- | --- |
| D2 | **fixed** (M2) — every grant-bearing transition is replay-protected and durable |

### Next action (at M2 close)

M3 — NeoForge reward reconciliation (mod only) and M4 — Rails action-objective contract (Rails
only), in parallel; the M3 cross-repository gate runs against the M2 Rails code in a disposable
database.
## M3 — NeoForge reward reconciliation (PASSED 2026-09-07, NeoForge only)

Delegated to a NeoForge sub-agent (cut off once by a usage limit and resumed from the same
uncommitted diff); reviewed hunk by hunk and gated by the integrator, who also ran the
cross-repository live gate below.

### Changes (`src/main`, `src/test`, one lang file; no Rails change)

* `quest/delivery/` (15 new classes) — `QuestRewardDelivery`/`Item` (the wire records; `temporary`
  is a nullable Boolean so "no verdict" is never read as true), `Parser` (transition →
  Present/Absent/Malformed, strict v2 listing, per-entry-tolerant bootstrap array), `Protocol`
  (acknowledgement encoder, response parser, terminal error codes), `Client` (signed v2 transport
  with four test seams; distinguishes a 404 carrying `delivery_not_found` from a 404 meaning the
  route does not exist), `LedgerEntry`/`LedgerStore`/`Ledger` (the fsync'd SavedData),
  `InventoryInsertion` (all-or-nothing planner), `ReconciliationDecision` (the pure restart
  table), `Service` (the apply sequence), `Reconciler` (login, journal refresh, periodic),
  `Backoff`, `Notices`, `LocalState`.
* `quest/QuestRewardService.java` — a response carrying `reward_delivery` is applied through the
  ledger and `granted_items` is ignored; a malformed delivery grants nothing at all (Rails has a
  record of it and the pending listing will hand it back); only a response with no delivery takes
  the legacy immediate path, logged `delivery_mode=legacy`. `buildDeliveryStacks` reuses M1's
  temporary-item policy so a delivery applied at login is stamped exactly as one applied inline.
* `network/WorldBootstrapAPI.java` + its records parse `pending_reward_deliveries`;
  `event/WorldBootstrapHandler.java` applies them at login; `quest/QuestJournalRefresh.java` fires
  a reconciliation; `event/QuestRewardDeliveryReconcilerHandler.java` drives the periodic sweep and
  clears per-server state on logout and shutdown; `server/http/RailsApiUrlResolver.java` gains the
  two v2 endpoints; `quest/network/QuestModels.java` carries `reward_delivery`/`replayed`;
  `quest/QuestProxyService.java` passes its `request_uuid`.
* `player/PlayerDataStore.java` — the bounded (256) `applied_delivery_uuids` marker list, carried
  across a profile save so a login write cannot drop it.
* `assets/britannia_mod/lang/en_us.json` — three delivery notices.
* Tests: 7 unit classes under `quest/delivery/` (parser against the four frozen fixtures, malformed
  cases, byte-compared acknowledgement body, ledger round-trip/schema/quarantine/bound,
  reconciliation table), `sync/WorldBootstrapAPIPendingRewardDeliveriesTest`, and
  `gametest/QuestRewardDeliveryGameTests` (11 GameTests driving real server players).

### Durability model as implemented

Ledger `britannia_quest_reward_deliveries` (overworld SavedData, `SchemaVersion=1`, corrupt rows
quarantined verbatim, 256 newest per player, only `acknowledged` rows pruned). Two fields beyond
the protocol's list, both required by the sequence: `AckOutcome` (so a queued row is not
acknowledged twice and the applied upgrade is sent once) and `AckError` (a terminal Rails answer,
so it is never retried forever).

Order per delivery, on the server thread: ledger `pending_local` → flush → all-or-nothing
insertion → (does not fit: `queued` → flush → acknowledge `queued` → notice → stop) → player
marker → `PlayerList#saveAll()` → ledger `applied` → flush → acknowledge → `acknowledged` → flush.
`PlayerList#saveAll()` is the only public route to the vanilla player-file write (temp file,
`SYNC`, atomic replace), which is what makes "items and marker persist together or not at all"
true; the price is that it saves every online player, acceptable at one call per quest transition.

The insertion planner deliberately does not use `Inventory#add`: that method grants what fits and
drops the rest, and for a creative player (which every GameTest mock player is) it reports success
while discarding. The planner follows vanilla's slot rules over a scratch copy of the 36 main
slots and commits only when everything fits.

### Integrator review findings (corrected by the resumed agent before the gates)

* A `reward_delivery: null` in a legal Rails response threw during Gson binding, because the field
  was typed `JsonObject`. Now `JsonElement`, with JSON null read as "no delivery".
* One trigger posted two acknowledgements: the login path applied the bootstrap deliveries and
  then swept the same rows, both bypassing the backoff. Sweeps now carry the set already visited.
* The 11 GameTests share process-wide single-slot seams and were interfering inside one batch;
  each now has its own batch.

### Tests and gates

| Run | Result |
| --- | --- |
| Focused (`quest.delivery.*`, `sync.WorldBootstrapAPI*`) | 89 tests, 0 failures, 0 errors, 2 skipped (the live tests) |
| Full JUnit (integrator, final tree) | 3634 tests, 0 failures, 0 errors, 20 skipped (462 suites; +76 over M1, of which 3 are the opt-in live tests skipping without credentials) |
| GameTests (integrator-verified log) | 1123 required tests passed, 0 failed (baseline 1112, +11 = exactly the new class) |
| `git diff --check` | clean |

### Cross-repository live gate (playbook M3 requirement)

Run by the integrator against an **isolated** Rails: a temporary detached worktree at the
committed M2 state `78d640a`, a disposable database `ultimacraft_test-91`, a generated shard
secret and Minecraft server key, and one seeded pending delivery. Nothing touched the owner's
checkout, the M4 worktree, or any real data; the server was bound to the WSL host-only interface,
the secret was shredded, and the worktree, database and temporary files were removed afterwards.

The mod's own client (production signing path, production transport) proved end to end:

1. `GET /api/v2/quest_reward_deliveries/pending` on the signed v2 tier lists the seeded delivery
   with its items for the right player, and the mod's parser reads what Rails actually sends.
2. `POST …/:delivery_uuid/result` with `applied` → `state: acknowledged`, `outcome: applied`,
   `duplicate: false`.
3. The same report again → `duplicate: true`, nothing moved.
4. `queued` after `applied` → `409 conflicting_delivery_result`, refused as a contradiction.
5. The acknowledged delivery no longer appears in the pending listing.
6. An unknown `delivery_uuid` → `404 delivery_not_found`.

Server-side confirmation in the isolated database: the row ended `state=acknowledged`,
`acknowledged_outcome=applied`, with `acknowledged_at`, `applied_at` and
`acknowledged_by_minecraft_server_id` all set. The delivery identity and the acknowledgement flow
therefore agree across the two repositories, which is the playbook's condition for committing
either side.

The gate revealed one environment fact worth recording for M12: a Rails server bound to
`127.0.0.1` inside WSL is unreachable from the Windows-side JVM; the live tests need it bound to
an interface the host can route to.

### Failure model (protocol §1.8) and its coverage

| Failure point | Outcome | Covered by |
| --- | --- | --- |
| Crash after recording `pending_local`, before insertion | re-applied on restart, nothing inserted | GameTest restart without marker |
| Crash after insertion, before the player save | file lacks items and marker → applied once | same |
| Crash after the marker, before the ledger says applied | marker present → ledger repaired, no second insert | GameTest restart with marker |
| Crash after applied, before acknowledgement | acknowledgement retried; ledger says applied → ack only | GameTest acknowledgement failure |
| Rails lost the acknowledgement response | second acknowledgement → `duplicate: true` | same, and the live gate |
| Full inventory | `queued`, no ground drop; delivered when space frees | GameTest full inventory |
| Two Rowans, same delivery | ledger refuses the second application | GameTest same delivery from every direction |
| Terminal Rails answer (404/409) | row closed with its reason, never retried | GameTest terminal rejection, and the live gate |
| Malformed delivery | nothing granted, no ledger row, no acknowledgement | GameTest and parser unit tests |
| Legacy response | immediate path, no ledger row | GameTest legacy response |
| Power loss defeating `fsync` | not covered; same limitation as the blessed-item and banking receipts | — |

### Defect register update

No new defects. D1 and D2 remain fixed; the delivery path now supersedes the legacy immediate
grant for every Rails that publishes a delivery.

### Next action (at M3 close)

M4 (Rails action objectives) is running in parallel; M5 (NeoForge farming events, outbox and crop
attribution) starts once M4 is committed, since it consumes the action-event contract.
## M4 — Rails action-objective and progress contract (PASSED 2026-09-07, Rails only)

Delegated to a Rails sub-agent (cut off once by a usage limit and resumed from the same
uncommitted diff); reviewed hunk by hunk and gated by the integrator.

### Changes (`app/`, `config/routes.rb`, `db/`, `test/`)

* `db/migrate/20260907120000_add_journal_fields_to_quests.rb` — `quests.quest_key` (nullable) and
  `quests.journal_metadata` jsonb, with a partial unique index on `(shard_id, quest_key)` so legacy
  rows stay unkeyed.
* `db/migrate/20260907121000_create_quest_action_events.rb` — the idempotency record: unique
  `(shard_id, event_uuid)`, an index on `(shard_id, player_uuid, created_at)`, FKs restrict on
  shard and user and nullify on the journal row, and a check constraint on `result`.
  `duplicate` is never stored; it is the answer for a row that already exists.
* `app/models/quest_action_event.rb` — `PROTOCOL_VERSION`, the eleven actions, the stored results
  and rejection reasons, validations mirroring the constraints, every column `attr_readonly`, and
  `duplicate_response`, which rebuilds the envelope with `result: "duplicate"` and
  `original_result` and replays the stored body verbatim.
* `app/services/quest_action_events/` — `contract` (per-action shape check naming the offending
  field), `matcher` (`match`, `$flag:` resolution, `require_planter`, `require_bound`, returning
  the most specific reason), `progress` (ordered steps then trigger; `done` always read live),
  `presenter` (client-facing node and choices), `transition` (the advance an `action_trigger`
  performs, publishing its delivery under a third transition kind `…:action:<trigger_key>`), and
  `process` (the decision, the transaction, the row lock and the stored answer).
* `app/controllers/api/v2/quest_action_events_controller.rb` + `config/routes.rb` —
  `POST /api/v2/quest_action_events` on the v2 tier with its own rate limit, the §6 status codes,
  and the same player resolution every quest action uses.
* `app/serializers/quest_journal_entry_serializer.rb` — the additive §3.1 fields and
  `triggers.action`/`triggers.steps` with placeholders resolved and `require_bound` published as
  the currently bound values.
* `app/models/quest.rb` — `quest_key` normalisation, `CLIENT_METADATA_SERVER_ONLY_KEYS`,
  `CLIENT_METADATA_FILTER` (default **off** per §3.2, with a runtime override), and
  `client_node_metadata`, the single filter every response builder calls, which also derives
  `progress_steps` from `action_steps` when a node authors none.
* `app/services/quest_engine/effect_applier.rb` — `give_random_item` rolls one pool entry once
  inside the transition and returns the flags; `processor.rb` merges them into the journal row,
  filters node metadata, passes `presentation` through, and refuses a presentation choice with
  `422 presentation_only` before any effect runs; `quest_states_controller.rb` adds
  `inherit_flags` at all four create/restart sites and the same filtering on its own paths.
* Tests (new, 9 files): model constraints, journal fields, the processing decision table, the
  serializer, the client-metadata filter in both states, random-item stability and inheritance,
  the v2 controller (auth tiers, every status code, every result), and a contract test driving the
  real controller against the five frozen response fixtures.

### Integrator review findings (corrected by the resumed agent)

* Two touched files (`app/models/quest.rb`, `app/services/quest_engine/effect_applier.rb`) carry
  **mixed** CRLF/LF in the committed tree. The first agent's blanket LF normaliser rewrote them
  whole, inflating a +54/+37 diff to +99/+299 and making pre-existing trailing whitespace look
  newly added. Restored to a line-accurate merge; the integrator confirmed both files have the
  same CRLF line counts as at `HEAD` and that **no added line carries a CR**.
* Three of the agent's own new tests were wrong rather than the code: an `attr_readonly` update
  raises in this application rather than silently no-opping; a jsonb object cannot preserve
  authored key order; and a fixture state already seeds two flags.
* A `target.quest_state_id` of nineteen nines is accepted by the shape rule, so a test now pins
  that it answers a terminal result rather than a 500 the outbox would retry forever.

### Tests and gates

| Run | Result |
| --- | --- |
| The nine new test files | 102 runs, 869 assertions, 0 failures, 0 errors |
| Quest selection (integrator, fresh `ultimacraft_test-90`) | 666 runs, 3147 assertions, 0 failures, 0 errors |
| Full suite (integrator, fresh `ultimacraft_test-90`) | 3365 runs (3263 + exactly the 102 new), 52665 assertions, 12 failures, 0 errors, 1 skip — 11 audit-count admin tests + the stable food-supply one; none attributable to M4 |
| Admin family on a fresh database (integrator control) | all four admin classes together: 51 runs, 208 assertions, 0 failures — the 11 are order/state pollution |
| Migration ⇔ schema proof on `ultimacraft_test-92` | both migrations down and up; dumper rendering of both `create_table` blocks and both `add_foreign_key` groups byte-identical to `db/schema.rb`; version stamp `2026_09_07_121000` |
| `git diff --check` | clean |

**The pre-existing failure family is wider than M0 recorded.** M0 named four
`Admin::RedeemShardResetsControllerTest` cases; later runs show `Admin::BankChequesControllerTest`,
`Admin::BankReconciliationsControllerTest` and `Admin::RedeemsControllerTest` failing instead or as
well, and `ShardPlatformQueryBudgetTest` coming and going. The mechanism is now identified: those
tests assert an **unscoped** `AdminActionAudit.count == 0`, and other suites commit audit rows
outside their own transactions, so which of them fails depends on the seed and the order. All of
them pass together on a fresh database. `Economy::CityFoodSupplyRecalculatorTest` (100.0 vs 99.7)
fails on a fresh database too and is the one genuinely stable pre-existing failure. Nothing in the
M4 diff mentions `AdminActionAudit`.

The gate for later Rails runs is therefore: **the `Economy::CityFoodSupplyRecalculatorTest`
failure plus any subset of the audit-count admin family, and nothing else** — with a fresh-database
control run whenever a new name joins the set.

### Processing decision table (as implemented and tested)

| Input | Result | State change |
| --- | --- | --- |
| `event_uuid` already answered on this shard | `duplicate` + `original_result` | none; stored answer replayed verbatim |
| two copies race past the lookup | loser answers `duplicate` | loser's work rolled back by the unique index |
| target state on another shard, or another player's | `rejected` `wrong_shard` / `wrong_player` | none |
| target state's current node is not `target.node_id` | `stale` (+ both node ids) | none |
| no active journal row subscribes to this action | `irrelevant` | none |
| listening step is ahead of the expected next one | `rejected` `step_out_of_order` | none |
| a `match`/`require_bound` check fails | `rejected` `not_planter`/`wrong_crop`/`wrong_plot`/`wrong_cycle`/`mismatch` | none |
| step arrived after its `deadline` | `rejected` `window_expired` + `reset_to` | progress and bindings cleared from `reset_to` on |
| the last recorded step again, identical bindings | `applied` | none |
| the expected next step | `applied` | progress, timestamp and bindings recorded; node unchanged |
| an earlier step, or the last one with different bindings | `applied` + `restarted` | cleared from that step on, then recorded |
| `action_trigger` matches | `applied` | effects, flags, node advance, delivery under `…:action:…`, `completion_result` for an ending |
| the advance's grant is refused by the delivery contract | `rejected` `mismatch` | none; the refusal is stored so the outbox stops retrying |

Every outcome writes its `quest_action_events` row in the same transaction and under the same
journal-row lock as the change it describes.

### Assumptions recorded by the agent (each documented in code)

* §2.1's "an already-recorded step returns applied unchanged" and "an earlier step restarts"
  overlap. Implemented as: the last recorded step with identical bindings changes nothing;
  anything earlier, or the last one with different bindings, restarts from there. This is the
  reading that makes the protocol's own reclaimed-plot example behave.
* `triggers.steps[]` publishes no `label` (nor does the frozen fixture); labels travel in
  `progress[]`.
* `inherit_flags` keys on `start_conditions.prerequisite_quest_id`, the key `interact` already uses.

### Defect register update

No new defects. The client-metadata filter ships **off**, as §3.2 requires during the compatibility
window; turning it on is an M8 rollout step and both states are covered by tests.

### Next action (at M4 close)

M5 — NeoForge farming events, outbox and crop attribution, which is the mod half of this contract
and the last piece before Rowan's content can be authored.
## M5 — NeoForge farming events, outbox and crop attribution (PASSED 2026-09-07, NeoForge only)

Delegated to a NeoForge sub-agent; reviewed hunk by hunk and gated by the integrator, who also ran
the cross-repository live gate below.

### Changes (`src/main`, `src/test`; no lang, no resources, no Rails change)

* `quest/action/` (12 new classes) — `QuestAction` (the eleven wire names and the subject fields
  each requires), `QuestActionSubject` (ordered, typed, validated at construction so the encoder
  never needs escaping), `QuestActionEvent`, `QuestActionEventProtocol` (byte-exact encoder for the
  frozen fixture layout, strict parser for the five results), `QuestActionEventClient` (signed v2
  transport mirroring the delivery client, splitting a contract 404 from a missing route),
  `QuestActionOutboxEntry`/`Store`/`QuestActionOutbox` (the fsync'd SavedData, schema 1, quarantine,
  overworld anchor, per-player cap 64 with an overflow log), `QuestActionBackoff`,
  `QuestActionSubscriptions` (the journal filter), `QuestActionDispatcher`, `QuestActionEvents`
  (the façade the farming code calls).
* `quest/QuestObjectiveTriggers.java` and `QuestObjectiveWatcher.java` — the fourth observer,
  matching `triggers.action` and `triggers.steps` from the server journal with the same in-flight
  guard and cooldown discipline as the three legacy observers. `QuestEntryCodecs` is untouched:
  `triggers` still never reaches the client, and no new client payload carries an objective.
* `block/entity/FarmingBlockEntity.java` — the attribution model (below).
* Twelve publish sites, each after the real mutation: the existing dung harvest event, dirt
  gathering, water-container filling, the three bowl preparations, fertile-dirt mixing, plot hoeing,
  plot fertilizing, planting, watering and harvesting. Two `setBlock` return values that were
  previously discarded are now checked, so a failed placement reports nothing. No farming rule or
  balance changed.
* `event/QuestActionOutboxHandler.java` — the dung bridge plus the tick, login and server-start
  schedule; `server/http/RailsApiUrlResolver.java` — one v2 endpoint.
* Tests: 7 unit classes under `quest/action/` and 4 GameTest classes (16 new GameTests).

### Attribution model

`FarmingBlockEntity` persists, beside the existing plot owner and community flag: `PlanterUUID`
(who planted the crop standing here), `CropCycleUUID` (the identity of this cycle) and
`CropCyclePlotKey` (the `plot_key` the cycle was minted at). The planter is deliberately distinct
from the owner: a public plot has no owner and still has a planter, and quest credit turns on the
planter. The cycle uuid is minted on planting, **rotated** when a perennial regrows after a harvest
(without which one plot would satisfy the same bound objective forever), and cleared when the crop
is cleared, replanted or the block removed. Accessors gate on there being a crop, and the
plot-key check refuses a cycle whose stored plot disagrees with where the block entity now is, so a
pasted or moved block entity cannot credit the wrong plot. All three survive chunk unload, save and
restart through the block entity's own NBT.

At harvest the cycle and planter are read **before** the reset or rotation, so the event reports
the cycle that produced the crop and the player who planted it, not whoever is holding the tool.
That is what lets Rails refuse a harvest by anyone but the planter.

### Integrator review findings (found and fixed by the agent during its own gate runs)

* `Inventory#add` empties the stack it consumed, so four publish sites were reading an
  already-empty output; item ids are now read before delivery.
* An `irrelevant` answer was setting a cooldown, which would have suppressed the player's next
  legitimate event; the cooldown now applies to `rejected` only.
* Two architectural guard tests assert on literal source text, so planting keeps its two-statement
  form to preserve the proof that the skill gate precedes the mutation.

### Tests and gates

| Run | Result |
| --- | --- |
| Full JUnit (integrator, final tree) | 3671 tests, 0 failures, 0 errors, 23 skipped (468 suites; baseline 3634/20) |
| GameTests (integrator-verified log) | 1139 required tests passed, 0 failed (baseline 1123, +16) |
| `git diff --check` | clean |

### Cross-repository live gate

Run by the integrator against an isolated Rails: a temporary detached worktree at the committed M4
state `0fff0b3`, a disposable database, generated credentials, and a seeded quest bound to one plot
and one crop cycle with `require_planter`. Torn down afterwards, secret shredded.

The mod's own encoder and transport proved, end to end:

1. A harvest of the bound crop by someone who did not plant it → `rejected` with reason
   `not_planter`; nothing advanced.
2. The planter's harvest of the bound cycle → `applied`, and the journal row moved from the working
   node to the completion node.
3. The same event identity again → `duplicate` naming `applied` as its original result, with no
   second advance.
4. A harvest naming a different cycle → `rejected` `wrong_cycle`.

Server-side: three posted identities produced exactly three stored rows, and the retry produced
none. The objective contract therefore agrees across both repositories, including the
duplicate-resistance the outbox depends on.

### Outbox behaviour

Enqueued only when the mutation succeeded, the subject carries every field its action requires, the
player's server journal actually subscribes to that action with matching `match`, `require_planter`
and bound values, no cooldown is running, and no live row already covers the same subscription.
`applied`, `duplicate`, `irrelevant`, `stale` and `rejected` are all terminal; `400` is terminal
(an encoder or authoring defect must not retry forever); `404 player_not_found`, `429` and `5xx`
retry on the 10 s → 30 s → 60 s → 2 min → 5 min schedule; a 404 without a contract code means old
Rails and stops the outbox for the boot.

### Playbook item 8 — tracked dung support

The chosen correction is **no rule change**, documented in code and tested: an unsupported but
tracked pile is worth exactly one event, an untracked one none. Revalidating support at harvest
would deny a harvest that succeeds today, which is a farming rule change outside this milestone's
scope.

### Defect register update

No new defects. D6 (community plot seed window and random-tick reclaim) and D11 (no retry when
skill data is unavailable) remain open and are M9's work.

### Next action

M6 — Rowan archetype and quest-giver spawn integration (mod only), then M7 — the Rails questline
content. Note for M6: `QuestGiverSpawnBlockEntity.SUPPORTED_ARCHETYPES` (added in M1 as the server
authority) and the client screen's list are kept identical by a unit test, so Rowan must be added
to both.
