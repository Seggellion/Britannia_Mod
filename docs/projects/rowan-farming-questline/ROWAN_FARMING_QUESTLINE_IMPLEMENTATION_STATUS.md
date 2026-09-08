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
| M5 NeoForge farming events, outbox, crop attribution | **PASSED** | `473917c0` | — | 2026-09-07 |
| M6 Rowan archetype | **PASSED** | `6b783003` | — | 2026-09-07 |
| M7 Rails content, seed, admin | **PASSED** | — | `abea5ff` | 2026-09-07 |
| M8 Dialogue and journal UX | **PASSED** | `75bdb1c5` | — | 2026-09-07 |
| M9 Timing, skill, recovery | **PASSED** | `6fe3f1e9` | `39d14b3` | 2026-09-08 |
| M10 Achievement and advancement | **PASSED** | `994d9b7c` | `0aed9dd` | 2026-09-08 |
| M11 Hardening and automated acceptance | **PASSED** | `8d2dbcfa` | `7602ede` | 2026-09-08 |
| M12 Live acceptance and release handoff | **HANDOFF PREPARED** (live acceptance not started) | (this commit) | — | 2026-09-08 |

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

### Next action (at M5 close)

M6 — Rowan archetype and quest-giver spawn integration (mod only), then M7 — the Rails questline
content. Note for M6: `QuestGiverSpawnBlockEntity.SUPPORTED_ARCHETYPES` (added in M1 as the server
authority) and the client screen's list are kept identical by a unit test, so Rowan must be added
to both.
## M6 — Rowan archetype and quest-giver spawn integration (PASSED 2026-09-07, NeoForge only)

Delegated to a NeoForge sub-agent; reviewed hunk by hunk and gated by the integrator.

### Changes (`src/main`, `src/test`; no new art, no lang, no Rails change)

* `block/entity/QuestGiverSpawnBlockEntity.java` — `Rowan` added to the server-authoritative
  `SUPPORTED_ARCHETYPES`, an archetype-to-outfit map applied only on a plain archetype's fresh
  spawn (so no existing archetype's appearance changes), and the per-spawner `directions` hint with
  its NBT key, a 128-character bound and a sanitiser.
* `client/gui/QuestGiverSpawnScreen.java` — `Rowan` in the pick list and a Local Directions box.
* `entity/QuestGiverEntity.java` — a synched, saved `localDirections`, and a role title derived
  from the resolved quest API identity (`Rowan` → Farmer, everything else → Wanderer) so the
  profession cannot drift from the quest and needs no extra persistence.
* `client/renderer/CitizenClothingLayer.java` — one outfit entry, `farmer` = boots + half apron,
  reusing the two textures the wood trader already wears, present for both genders. **No new
  texture asset was added**; a test asserts both files exist and that the mapping reuses them.
* `network/QuestGiverSpawnConfigC2SPayload.java` — the seventh field and its validation;
  `network/NetworkHandler.java` — one line passing it through, with M1's four gates byte-identical.
* Tests: 5 unit tests (`entity/RowanFarmerArchetypeTest`), 7 GameTests
  (`gametest/RowanQuestGiverSpawnGameTests`), and M1's spawn-config GameTests updated because they
  used `Rowan` as their canonical *unsupported* archetype, which it no longer is.

### Wire format change and its compatibility

`directions` is appended after the six existing fields and encoded **only when non-empty**; the
decoder reads it only if bytes remain. So a pre-M6 client produces a byte-for-byte pre-M6 packet,
which decodes to an empty hint, and an empty hint means "leave the stored hint alone" — an old
client can still configure a spawner without destroying its hint. Server-side validation refuses a
hint longer than 128 characters or carrying control characters, whatever the client believed it
sent; the sanitiser additionally guards the NBT-load path.

### Identity

Every Rowan resolves the Rails `origin_npc` identity `Rowan` exactly: the first configured spawn,
one rebuilt from NBT after a chunk reload, one respawned from the block after the entity was lost,
and two spawners differing in city, gender, radius and hint. A hint shaped like the legacy
`Name:api_id` form reaches neither the identity nor the nameplate, and the archetype is accepted
under exactly one spelling (`rowan`, `ROWAN`, `Rowan `, `Rowan the Farmer` are all refused).

### Tests and gates

| Run | Result |
| --- | --- |
| Full JUnit (integrator, final tree) | 3681 tests, 0 failures, 0 errors, 23 skipped (469 suites; baseline 3671) |
| GameTests (integrator-verified log) | 1146 required tests passed, 0 failed (baseline 1139, +7) |
| M1's gates | verbatim unchanged: permission, reach, block entity, payload shape |
| `git diff --check` | clean |

### Known limitation, carried into M11

The spawn screen cannot be told a spawner's **current** hint, because the screen-opening payload
and its sender are outside this milestone's file boundary. The box therefore opens empty and blank
means "keep the stored hint", which is labelled in the UI but means a hint can be replaced and not
cleared from the screen. The fix is one field on the screen payload and its sender; **M11 owns it.**

### External asset the owner must supply (M12)

`Rowan.png`, uploaded to the portrait bucket under whichever gender is placed. Nothing was
uploaded. Until it exists the generic peasant portrait renders. Note the pre-existing client
behaviour that a single missing-portrait response pins the fallback for the rest of that client
session.

### Defect found in passing (pre-existing, NOT fixed here, outside this project)

`QuestGiverSpawnBlockEntity.customApiId` is never written to or read from NBT, so a Generic Combat
spawner's Rails api id does not survive a chunk reload. The standing NPC is unaffected because it
respawns from the saved snapshot, but a respawn after a reload would lose the id. Recorded for the
owner rather than fixed, because it belongs to no milestone here and changing it would alter an
existing archetype's behaviour.

### Next action (at M6 close)

M7 — the Rails questline content: five linked non-repeatable quests keyed to `origin_npc = "Rowan"`
with an idempotent seed, authored against the M4 action-objective and journal contract.
## M7 — Rails Rowan content, admin support and rewards (PASSED 2026-09-07, Rails only)

Delegated to a Rails sub-agent; reviewed hunk by hunk and gated by the integrator, who also found
and fixed the engine defect below.

### Changes

* `app/services/quest_content/` (new) — the questline as data (`rowan_farming_questline.rb`), the
  seed pool and its eligibility rule (`crop_seed_pool.rb`), seed-time validation
  (`definition_validator.rb`), the metadata shape checks shared with the admin form
  (`authoring.rb`), and the idempotent two-pass upsert (`installer.rb`).
* `db/seeds/rowan_farming_questline.rb` + `lib/tasks/seed_rowan_farming_questline.rake` — a narrow
  `db:seed:rowan_farming_questline` task in the repository's existing seed-task shape, resolving
  the shard from an environment variable and validating before installing.
* `app/models/quest.rb` — `#authoring_errors`, deliberately not an ActiveRecord validation because
  existing tests author deliberately-loose metadata that a validation would make unsavable.
* `app/controllers/admin/quests_controller.rb` and three `app/views/admin/quests/` partials — the
  smallest admin support the milestone asks for: description, active flag, quest key, journal
  metadata, the node objective and observer fields, reward previews, keep-for-later items, and a
  choice's `presentation` selector. The controller parses the JSON textareas into objects and
  refuses a save that will not parse or that fails authoring validation. Two defects this closes:
  a string assigned to a jsonb column is stored as a JSON string that every reader treats as
  absent, so editing a Rowan quest through the form would have silently stripped its objectives;
  and without the `presentation` selector a save turned the Help button into a destination-less
  choice that answers an error.

### The five quests

Keys `rowan_farming_1..5`, `origin_npc "Rowan"`, non-repeatable, priorities 50 down to 10 so the
interact path reaches the earliest open stage first. Each has the same five node titles in the same
creation order, with Offer first because interact starts a player on the lowest-id node. Choice
keys are fixed per stage (`accept`, `decline`, `help`, the stage's trigger choice, `later`,
`claim`). Quest 5's working node matches the frozen authoring fixture exactly, including its
ordered steps with bindings and deadlines and the harvest trigger with `require_planter`.

Rewards are the playbook's provisional constants, centralised in one table: 2 gold, 50 copper,
20 silver, 3 gold, 5 gold. **No newer economy authority for quest coin rewards exists in the
repository** (the currency table fixes only the comparative ladder; the escort seeds derive silver
from distance). Two of the five are independently pinned by the M0-frozen fixtures, which agree.

### The seed pool

Rails holds no crop-definition table, so eligibility is resolved against the Farmer vendor's
starter seed rack, with a test that parses that seed file so the transcription cannot drift.
Three clauses, each able to reject alone: beginner skill requirement, growable on Rowan's community
plot, and harvestable with equipment the questline has already issued. Carrot is the only crop that
passes; lettuce and green onion need scissors, potato needs a higher skill. The selection is a real
random roll over the resolved pool so widening it is a data edit rather than a code change, and the
validator re-checks every authored entry at seed time.

### The "Not now" and Escape problem

The interact path creates the journal row on the first click, before acceptance, and the serializer
has no notion of "offered" — changing that is controller work outside this milestone. Solved by
authoring instead: both refusal paths route to an ending node carrying a `rejected` flag, which
removes the row from the active journal immediately and lets the quest be offered again, and
neither grants anything. While the offer is open the entry reads as an offer rather than as
progress: no observers, no progress entries, and a preview naming what accepting would grant.

### Integrator fix: an ungated quest start (found in review)

`POST /api/quests/:id/start` created a journal row **without ever consulting
`start_conditions.prerequisite_quest_id`**, which the interact path has always enforced. That
endpoint is reachable: the Minecraft server forwards a client's START action for an arbitrary quest
id, and only TRIGGER is refused as non-authoritative. A modified client could therefore have opened
a later stage of a chained questline directly and claimed its reward — for this questline, quests 2
through 4's coins, bowls, bucket, watering can, seed and hoe, none of whose objectives depend on an
inherited flag. Quest 5 was already protected in depth, because its harvest matches against a crop
flag that only quest 3's roll sets.

The fix applies the same check the interact path uses, at the point the completed and repeatable
rules are already evaluated: refused with a conflict, no journal row, one warning line. This is an
**engine** rule, not a questline rule, so it is tested with a plain two-quest chain in
`test/controllers/api/quest_start_prerequisite_test.rb` (4 tests). Negative control: with the fix
reverted, two of those four fail; with it, all pass.

One M4 test changed as a consequence. It asserted that starting a quest with an unfinished
prerequisite succeeds and inherits nothing; that start is now refused outright, which is strictly
stronger, so the assertion was updated to expect the refusal and the absence of a row rather than
the endpoint being loosened to keep the old expectation.

### Tests and gates

| Run | Result |
| --- | --- |
| The agent's new tests | 103 runs, 1181 assertions, 0 failures |
| Quest selection (integrator, fresh database) | 773 runs, 4337 assertions, 0 failures, 0 errors |
| Full suite (integrator, fresh database) | 3472 runs (3365 baseline + 103 + the 4 fix tests), 53984 assertions, 10 failures, 0 errors, 1 skip |
| Admin family on a fresh database (control) | 51 runs, 0 failures |
| Idempotency (through the real rake task) | seeded twice; row snapshots including microsecond timestamps byte-identical; unchanged content does not even touch `updated_at`; a player's current node and choice keys survive a re-seed; a hand-edited quest is rewritten without changing ids |
| `git diff --check` | clean |

The ten failures are the permitted family: eight admin tests asserting an unscoped audit-row count
plus the stable food-supply failure and the query-budget test, which fails *worse* in isolation on
a fresh database and mentions no quest code. No quest, questline, delivery or action-event test
fails.

### Line endings

Five modified files were uniformly CRLF at HEAD and were left that way, because normalising them
would have surfaced fourteen pre-existing trailing-whitespace lines as newly added and failed the
whitespace gate. All thirteen new files are LF, and no added line in the two known mixed-ending
files carries a carriage return.

### Deviations and open items

* No deviation from the frozen protocol; no fixture, digest or manifest touched.
* One reconciliation: the frozen stage-1 response example shows an empty keep-for-later list while
  the product contract says quest 1 retains the shovel and the dung. The product contract wins; the
  fixtures are shape examples and their digests are unchanged.
* The watering can is granted as the single registry item the mod ships, since "full" is a state
  rather than a separate item.
* An admin-form save is equivalent rather than byte-identical to the seed's output (it adds two
  inert empty observer blocks the node form has always posted). Asserted as equivalence.

### Next action (at M7 close)

M8 — dialogue and quest-journal UX in the mod, which is the first milestone a player would notice,
followed by M9 timing and recovery, M10 the achievement, and M11 hardening.
## M8 — Dialogue and quest-journal UX (PASSED 2026-09-07, NeoForge only)

Three sub-agents: an implementer, an **independent UX auditor** who did not write the code, and a
remediation pass. The auditor found two blocking defects that green unit tests and the integrator's
own inspection of the renderings had both missed, which is the case for auditing interface work
separately rather than trusting a milestone's own report.

### Architecture

Built on this repository's existing precedent (`client/screen/bank/BankDialogueLayout`): all layout
arithmetic lives in classes with no `Font`, no `GuiGraphics` and no client, because a `Screen`
cannot be instantiated by either test harness here, and the drawing classes only draw at the
computed numbers. New pure classes: `ScreenRect`, `QuestDialogueLayout`, `QuestJournalLayout`,
`QuestMixingGuideLayout`, `QuestScreenText` (the translation-key registry), `QuestKeyPrompt`,
`QuestNodePresentation`, `QuestTriggerResultPresentation`; drawing in `QuestScreenDraw`;
`QuestDecisionScreen` and `QuestJournalScreen` rewritten against them.

### The acceptance matrix, derived rather than assumed

Minecraft's own scale rule was extracted from the decompiled client. Two findings:

* **The playbook's "1024×768 at GUI scale 4" does not exist.** The scale loop refuses to go past
  the 320×240 floor and clamps to scale 3, giving 342×256 units.
* **But the vanilla Force Unicode Font option bumps an odd scale up *after* that check**, walking
  through the floor. So 1024×768 really can reach 256×192, and the true worst case is **160×120**
  (a 320×240 window with Force Unicode), which is the arithmetic floor.

Eighteen rows result, from 160×120 to 1920×1080. **Eight of the eighteen give the legacy
`screenWidth - 343` a non-positive wrap width** (−183 at 160, −87 at 256, −23 at 320, −1 at 342),
so that defect is live rather than theoretical. The quest path now computes its own geometry.
`dialogue/DialogueLayout.java` is untouched and **`ServiceDialogueScreen` remains exposed** — it
both measures and draws at a negative width below 343 units. Out of this milestone's boundary;
**carried to M11**.

### Evidence

32 layout renderings in the M8 evidence directory — computed rectangles and real authored strings
drawn offline, **labelled as layout renderings, not screenshots**, since nothing here launches a
client. One per acceptance-matrix row plus the offer, working, done, help/mixing-guide and journal
states, each at normal and worst-case sizes. The integrator opened them; two of the three concerns
they raised became audit findings.

### What the audit found (all fixed)

* **F1, blocking — the help and mixing-guide view was painted over by its own background.** It drew
  its content and then called the parent render, which begins by re-entering this screen's
  background override and filling the screen with an 80%-opaque black quad. Only the Back button,
  drawn later as a widget, survived. A player opening "How do I farm a public plot?" would have
  seen a near-black screen. The same shape affected the journal panel and quit confirmation.
  Fixed by painting surfaces in the background pass and content after the widgets, matching the
  dialogue path that was already correct, and pinned by `QuestScreenDrawOrderTest`.
* **F2, blocking — the quiet notice and the journal read state that never refreshed.** Replacing
  the objective popup was correct, but nothing updated the client journal when an objective
  completed, so the same line would print after hoeing, fertilizing, planting and watering, and
  "Return to Rowan to claim your reward" would never fire at the one moment it exists for. Fixed on
  both sides: the server can now update journal detail from the authoritative response, and the
  client refreshes from the trigger result **before** the notice is composed.
* **F4 — hidden choices were unreachable.** Below about 192 units only one choice button was added,
  with no affordance and no keyboard route at all. Now has a scroll hint and page-key routing,
  matching what the journal already did.
* **F5 — the mixing guide drew zero of four steps at the smallest size, silently**, while claiming
  to be scrollable. It now sheds introduction lines until a row fits, and says so when it genuinely
  cannot. **Its guard test could not have failed**: the helper hardcoded a three-line body, which
  fits by seven units, while the screen computes four, which does not. The test is now parameterised
  from the real body.
* **F6 — two water messages invented a reason.** The access policy is a single world-interaction
  check with no notion of buckets or wells, but the message told a bucket holder to use the well
  (false: a well inside the same protection also refuses) and told a player standing on a well to
  use the well. Both now carry the protection message. The Adventure bucket rule is enforced by
  vanilla with no mod hook, so that message was **retired rather than relocated**.
* **F7 — the fertile-dirt mixer refused every right-click with the wrong advice**, telling a player
  holding a filled bowl that their bowl was not ready, and never naming the actual requirement.
  Now silent on an empty off hand and names the Bowl of Water otherwise.
* **F3 — two javadocs asserted something false.** The codec is clean, but three pre-existing paths
  ship the raw Rails body or node metadata to the client, including resolved bound values. The
  prose is corrected; **the payloads are deliberately unchanged** and carried to M11 with their own
  gate. A test now fails if someone hardens the payloads and leaves the prose stale.
* **F8 — the renderer overstated the screen.** It painted a background behind the reward panel that
  the screen did not paint, concealing roughly 1.6:1 contrast; drew item names that appear only in
  tooltips; and printed journal text at the origin where the screen draws nothing. The screen gained
  a real reward-panel ground; the renderer gained the screen's own empty-rect guard, honest
  captions, and a **"synthetic" label** on the one sample that is not fixture data.
* **F10** — a key prompt described a route it did not take; reworded, with a test that fails if the
  sentence and the route drift apart.

### Deferred to M9–M11 (recorded, not silently dropped)

Reward panel is fixed-height rather than fitted, so dead space grows with the screen (F9); journal
rows drop their progress and their hidden-step count at the smallest size (F11); result and returned
icons in the guide are distinguished only by position (F13); a stale flowing-water warning can
persist after a successful preparation (F14); `ServiceDialogueScreen`'s negative wrap width; the
raw-payload trigger-data exposure (F3). A pre-existing package/path mismatch on
`QuestDecisionScreen` was noted and left alone.

### Tests and gates

| Run | Result |
| --- | --- |
| Full JUnit (integrator, final tree) | 3804 tests, 0 failures, 0 errors, 23 skipped (479 suites; baseline 3681) |
| GameTests (integrator, final tree) | 1146 required tests passed, 0 failed, no "Failed to start" |
| `git diff --check` | clean |

### Not evidenced, stated plainly

Actual in-game rendering of parchment art, portraits and item sprites; real glyph metrics; mouse and
keyboard navigation as behaviour rather than as geometry and widget order; the shipped stage-4
mixing-guide content, which lives in Rails and is only transcribed here; and a long-localized-string
render, which is the one visual-acceptance row with no artifact. All need a running client or the
Rails content, and all belong to M12's live acceptance.

### Next action (at M8 close)

M9 — timing, skill recovery and player recovery.
## M9 — Timing, skill recovery and player recovery (PASSED 2026-09-07, both repositories)

### OWNER-VISIBLE GAMEPLAY CHANGE

The public-plot windows were retuned to the playbook's values, and this affects **every player who
uses a public farm, not only players on the questline**:

| Window | Was | Now |
| --- | --- | --- |
| Hoed public plot reverts if nobody fertilizes | 180 s | **600 s** |
| Fertilized public plot reverts if nothing is planted | 60 s | **300 s** |
| Replant window after a harvest on a public plot | 60 s | **300 s** |

The integrator verified that every code path reading these constants is gated on the plot being a
community plot, so private and house plots are untouched. Both changes make public farming more
forgiving, and the playbook specified these values directly. Flagged here because it is a live
behaviour change beyond the questline.

### Changes

**NeoForge.** `farming/CommunityPlotWindow` is now the single source of truth for both windows, in
seconds first and ticks second so it can be read against Rails without arithmetic. Expiry became
**deterministic**: the plot books a scheduled tick rather than waiting on a random tick, with the
random-tick reclaim kept only as a backstop for worlds saved before this change — that closes
discovery defect D6, where expiry depended on `randomTickSpeed`. Planting now reclaims an expired
plot **before** anything is consumed, and hoeing refuses an expired preparation before the
fertilized dirt is taken. Countdown warnings are announced to players in range.

`skill/SkillDataBackoff` and `SkillManager` add the bounded retry (10 s, 30 s, 60 s, 2 min, 5 min,
capped, six attempts, with a floor between player-triggered attempts), cleared on logout and on any
successful load. `SkillSnapshot` can now distinguish a **known zero** from an **unknown** value, and
`FarmingCultivationGate` asks for a retry only when the data is unavailable, never when the answer
was an authoritative refusal — that closes D11, where a player whose skill data had not arrived
simply could not plant.

`WildResourceQuestScheduling` and `RowanQuestlineHooks` bring the *next scheduled attempt* for dung
forward in a bounded area near the interacting Rowan when quest 1 is accepted. It never places a
block and never delays an attempt already due, so caps, spacing, substrate rules and ordinary
randomness all still apply — the playbook's "do not command-place an untracked pile".

`WateringCanItem` now teaches moisture from the **actual crop and the plot's current state** through
the existing care-state calculation, instead of a hardcoded instruction to water twice, so rain or
another player's watering cannot make the advice false.

**Rails.** `quest_equipment_reissues` records replacements for lost tutorial equipment: one row per
replacement, bounded at two per player per quest per item, enforced by the database twice (a unique
index including the ordinal, and a check constraint on the ordinal) with the application only
reading the count. Coins are excluded twice — absent from the allow-list, and refused by a check
constraint even if the model is bypassed. The item is granted as an ordinary reward delivery through
the existing M2/M3 ledger, inside the same transaction and row lock; there is no second reward path.

### How the two deadlines agree

Rails expires a step on the event timestamps the shard mints, the game on its own scheduled tick,
and the numbers are the same on both sides, asserted in seconds on one side and in ticks on the
other so a change has to be made twice and deliberately. They can drift in exactly one direction: a
server running below full tick rate makes the game's window *longer* in wall time, never stricter
than the server. When they disagree Rails wins, answering the planting `window_expired` with the
step to reset to, and the player sees the step un-tick at the next journal refresh.

**Known limitation carried to M11:** the mod logs that rejection but does not yet surface
`window_expired` in the player's own words, because the reset instruction is not parsed.

### The reissue model

Recorded as a durable row, not a session counter, so a reconnect is irrelevant. A quest restart was
the case that had to be designed for, and the agent's first premise was wrong and was corrected by
its own test: a restart does **not** mint a new journal row, it re-accepts the same one and wipes
its state variables. Anything counted in those variables would reset on every restart, so the
allowance lives outside them and is keyed on the quest's stable key rather than its row id, so
re-seeding the content does not hand out a fresh allowance either. Carried inventory is checked on
the game side because Rails cannot see a Minecraft inventory, and every non-grant answer tells the
player plainly that items in a chest or the bank cannot be seen.

### Tests and gates

| Run | Result |
| --- | --- |
| Mod full JUnit (integrator) | 3841 tests, 0 failures, 0 errors, 23 skipped (483 suites; baseline 3804) |
| Mod GameTests (integrator) | 1154 required tests passed, 0 failed, no "Failed to start" (baseline 1146, +8) |
| Rails quest selection (integrator, fresh database) | 803 runs, 4490 assertions, 0 failures |
| Rails full suite | 3502 runs (3472 baseline + exactly the 30 new), 54011 assertions, 12 failures, 1 error, 1 skip — 11 audit-count admin tests, the stable food-supply one, the query-budget one, and one newly-appearing name proven unrelated below |
| Rails admin control (fresh database) | all four admin classes together: 51 runs, 208 assertions, 0 failures |
| Migration ⇔ schema proof | up, down, up on a disposable database; the only differing dumper lines are the PostgreSQL 18 check-constraint rendering artifact that already affects the two earlier tables |
| `git diff --check` | clean in both repositories |

### Deviations, stated rather than buried

* **Recovery is offered when the player talks to Rowan, not as a dialogue choice.** The questline's
  content and its contract fixtures are frozen under a digest from M0; adding a choice would have
  rewritten a pinned fixture. Same reasoning kept the dung scheduling on the game side rather than
  as a server-driven effect. The contract between the halves is the quest key, which Rails never
  re-mints.
* **Item 8 was largely verification rather than new code**: dung, dirt, well water and the returned
  bowls were already renewable. What was added is the dung expedite and a test proving a stage-5
  failure cannot disturb the completed stages 1–4.
* **Countdown warnings are not persisted**, so a plot reloaded mid-window may re-announce its
  current mark to a returning player. Deliberate and documented.
* `ShardPlatformQueryBudgetTest` now fails **deterministically on a fresh database** in isolation on
  this branch, rather than intermittently. It is unrelated to anything this milestone touched, and
  M11 investigates the whole pre-existing failure family properly rather than restating the label.
* **The Rails suite cannot run in parallel on this machine**: the database's host-based
  authentication trusts specific database names, so forked workers fail to authenticate. Serial runs
  are required and take 12–25 minutes. Worth recording for the release handoff.

### Only a live run can prove

End-to-end skill recovery against a real Rails outage; that the expedited dung schedule produces a
findable pile in populated terrain, since caps and substrate rules may still refuse every probe; the
reissue round trip over signed HTTP with real credentials; and whether 600 and 300 seconds are the
right *feel* rather than merely the right *numbers*.

### Next action (at M9 close)

M10 — the First Harvest achievement, its in-game toast and the persistent advancement, all
idempotent under replay and repeated claims.

### The newly appearing failure, investigated rather than labelled

`SeoPublicationRouteSafetyTest#test_legacy_Update_Center_Pages_redirect_one_hop_to_the_owned_category_route`
appeared for the first time in this milestone's full run, as an **error** rather than a failure.
The gate rule says a new name must be proven, not assumed, so it was:

* On this branch, **in isolation on a fresh database, it passes** (5 runs, 0 failures).
* The test file references nothing this milestone touched — zero mentions of quests or reissues.
* Its failure mode is `at_css('link[rel="canonical"]')` returning nil, i.e. a page that rendered
  without a canonical link. That is a content-state symptom, the same shape as the admin family's
  unscoped audit-row counts.
* The one change M9 made to routing adds a single v2 API endpoint inside the shard-authenticated
  block, which no SEO redirect can reach.

Conclusion: order- and state-dependent, pre-existing in kind, not caused by M9. A comparison run at
the base commit was attempted in a detached worktree and produced an unrelated missing-asset error,
so that probe proved nothing and is not counted as evidence either way. M11 investigates the whole
family properly.
## M10 — Achievement and completion experience (PASSED 2026-09-08, both repositories)

### Two real defects closed, not just new work

* **The website achievement was granted outside the effects transaction.** It ran after
  `EffectApplier`'s own transaction had closed, so a caller without an outer transaction could pay a
  player their items and record no achievement. It now runs inside the same transaction as the
  items, the flags, the node advance and the reward delivery, under the journal-row lock: all of it
  commits together or none of it does.
* **The final claim's announcement had nothing rendering it.** A turn-in response returns on a
  different payload than an objective result, so quest 5's achievement announcement reached the
  client and was dropped. Now presented through the same shared renderer as every other client
  action.

### Changes

**NeoForge.** `data/britannia_mod/advancement/quest/first_harvest.json` in the established shape of
the existing challenge advancement, with its title and description as translation keys rather than
literals. `quest/achievement/QuestAchievementAward` reads the achievement client actions out of an
authoritative Rails body, grants `britannia_mod:quest/<key>`, and returns the body to forward with
already-earned announcements removed — so a replayed response cannot toast twice. It is called at
the same server-side boundary that already applies the reward delivery, on all three authoritative
paths (turn-in, action event, and the legacy observer fallback), and **never on a client signal**.
`client/quest/QuestClientActions` becomes the single rendering of every client-action type.

**Rails.** The achievement grant moved inside the effects transaction and gained a stable `key`
alongside its display name, additively, so the mod resolves the advancement by key rather than by
parsing a title. Quest 5's closing dialogue was rewritten to acknowledge the harvest and settle what
the player keeps.

### Idempotency

The per-player advancement record is the token that makes the toast idempotent, because Rails
legitimately replays a stored response including its client actions, so nothing on the Rails side
could suppress the repeat. All four cases are proven, each against all three results:

| | Rails achievement | client toast | advancement |
| --- | --- | --- | --- |
| Same request replayed | stored replay returns before the grant runs | announcement filtered out | already earned |
| Fresh request, transition already made | refused | no client actions at all | nothing granted |
| Repeated claim click | as above | as above | as above |
| Duplicate action event | duplicate answer returns before any effect | announcement filtered out | already earned |

### Attribution and policy

Wrong player, wrong shard and unresolved identity all fail before anything is awarded, on both the
turn-in and action-event paths. A harvest that fails the planter check is rejected with no
achievement for anybody, no delivery and no node advance. The advancement is per player, so one
player's completion neither grants nor silences another's. `require_planter` and the crop-cycle
checks are untouched.

### Tests and gates

| Run | Result |
| --- | --- |
| Mod full JUnit (integrator) | 3863 tests, 0 failures, 0 errors, 23 skipped (485 suites; baseline 3841) |
| Mod GameTests (integrator) | 1160 required tests passed, 0 failed, no "Failed to start" (baseline 1154, +6) |
| Rails quest selection (integrator, fresh database) | 838 runs, 4661 assertions, 0 failures |
| Rails full suite | 3517 runs (3502 baseline + exactly the 15 new), 54113 assertions, 13 failures, 1 error, 1 skip — every name in the known order-dependent set |
| Rails admin control (fresh database) | all four admin classes together: 51 runs, 208 assertions, 0 failures |
| Frozen fixtures | both manifests verify 12 of 12 unchanged in the mod and its Rails mirror |
| `git diff --check` | clean in both repositories |

The closing dialogue was safe to rewrite because the frozen fixtures pin the *working* and *done*
nodes of quest 5, not its ending, and the transition fixture pins an empty client-action list, so
nothing constrained the achievement action's shape either. That was checked before authoring.

### One regression the agent caused and caught

Adding the stable `key` broke an M9 test that compared the achievement client action by exact hash.
Fixed by naming the new field in that assertion, and the name did not recur. Recorded because it
appeared as a new failure name in a full run and was **not** waved through as pre-existing.

### Owner decisions, recorded rather than made

* **Completing the questline announces shard-wide.** That matches the existing challenge
  achievement's behaviour, but it is a policy choice worth confirming before launch.
* **Players who finish quest 5 before this ships** will hold the website achievement without the
  in-game advancement, and there is no authoritative response left to grant one retroactively. A
  backfill would be a separate deliberate task.
* An achievement with no advancement resource keeps its announcement rather than silently losing
  it, since there is no token to decide with. Logged by name.

### Deliberate deviations

The advancement suppresses its own vanilla toast, because the challenge frame plays the toast and
its sound itself and leaving both on would show two toasts and play the sound twice in one tick.
Chat announcement is kept.

### Not covered by tests

The turn-in boundary needs a live Rails and is not driven end to end; it is covered by tests on the
grant-and-filter function itself plus a source-level assertion that all three server paths call it
and no client path does. The action-event boundary **is** driven end to end. The toast and its sound
have no automated coverage and never had — only a live client shows them.

### Next action (at M10 close)

M11 — cross-repository hardening and automated acceptance, including a proper investigation of the
pre-existing Rails failure family rather than a restatement of its label.
## M11 — Cross-repository hardening and automated acceptance (PASSED 2026-09-08, both repositories)

Two parallel sub-agents — one investigating the pre-existing Rails failures, one hardening the mod —
plus a live cross-repository integration run by the integrator.

### The defect that mattered most

**The client was discarding every farming objective answer.** An action-event response carries
`result`, not `success`, and none of the five frozen response fixtures has a `success` field — but
`ClientNetworkHandler.handleQuestTriggerResult` returns early on any body whose `success` is not
true, before the journal refresh. So M8's objective refresh and M10's achievement toast were both
**dead on the action-event path**, which is the path that carries every farming objective. Their
tests passed because they exercised those functions directly rather than through the gate that drops
the message. The forwarded envelope is now stamped `success: true` on the applied and duplicate
paths only, matching what the server already sets on the parsed object, and two tests pin both
directions.

This is the second time in this project that a green suite described behaviour the real path did not
have. Both times an independent pass found it.

### The client no longer receives the objective answer key

`QuestClientPayload` deep-copies and strips six keys at every depth from every sending site: the
journal entry's published objective machinery and all five node observer definitions. That is
exactly the set the server's own parsers read, and the proof runs both of those parsers over the
sanitized body and shows they find nothing. Bound plot keys and crop-cycle identities no longer
travel. Kept deliberately: every journal field, the client actions, and the presentation metadata,
each proven byte-identical afterwards, so the journal refresh and the achievement filtering still
work. A source-wide test now fails if a fourth sending site appears.

### GameTest coverage audit

Of 1160 registered GameTests, 96 were quest-related — and **not one drove a real block or item
interaction**. Every one entered production one layer below the player's gesture, though the harness
supports real dispatch and other parts of the repository use it. Added real right-click coverage for
each of the five farming boundaries, for a stranger harvesting an attributed crop, for the expired
window being explained, and for the flowing-water warning. Three of the nine required behaviours
have no in-world gesture to drive at all — relog, restart and acceptance replay are lifecycle or
wire events — and that is stated rather than covered with a theatrical test.

**Harness limitation found and documented**: a GameTest player joins over a connection that never
negotiates the mod's payload channels, so clientbound custom payloads are dropped silently. Chat
does arrive. This is why no test in the repository asserts on such a payload, and the sanitization
test therefore asserts the payload's contents through its own codec instead.

### The seven deferred defects, all fixed

The shared dialogue layout's negative wrap width (live on eight of eighteen configurations); the
raw payload exposure; the expired-window rejection now explained to the player in their own
language; the reward panel fitted to its content instead of a fixed height; journal rows keeping
their progress and their hidden-step count; guide result and returned markers distinguished by
symbol and tooltip rather than position; and the stale flowing-water warning, which now stays
silent after a successful preparation and warns at most once per click.

**Two corrections to the integrator's brief**, both verified: another screen also consumes the
shared dialogue layout and was fixed with it, and two of the three screens flagged for the same
width defect do not have it, for reasons given.

### Validation

| Check | Result |
| --- | --- |
| Frozen contract fixtures | 12 of 12 verify against the manifest, no byte changed; protocol versions the code sends and accepts now pinned to the fixtures by test |
| Resources | the one advancement this project added parses and resolves; the validation now walks the whole directory rather than one file |
| Localization | every key referenced exists exactly once and is non-blank; the raw-registry-id rule extended from screens to every message site |
| `git diff --check` | clean in both repositories |

**Two pre-existing defects found in another questline's advancement** and recorded rather than
fixed: its title and description are literals, so it cannot be translated, and its icon item has no
localization entry, so players see a raw identifier. Guarded by a test that fails once either is
fixed, so the exemption cannot outlive the defect.

### Tests and gates

| Run | Result |
| --- | --- |
| Mod full JUnit (integrator) | 3902 tests, 0 failures, 0 errors, 23 skipped (487 suites; baseline 3863) |
| Mod GameTests (integrator) | 1167 required tests passed, 0 failed, no "Failed to start" (baseline 1160, +7) |
| Rails suites | unchanged by this milestone; the investigation added documentation only |

### Live cross-repository integration

Run by the integrator against an isolated Rails at the committed head, on a disposable database,
with **the real questline installed by its own rake task** — five chained quests, twenty-five nodes,
the awarded seed and crop inherited into the final stage. The mod's own client, through its
production signing and transport, then drove:

* the pending reward listing, an applied acknowledgement, its duplicate replay, a refused
  contradicting report, the delivery leaving the listing, and an unknown delivery answering
  not-found;
* a harvest by someone who did not plant the crop, refused as not the planter;
* a harvest naming a different crop cycle, refused as the wrong cycle;
* the planter's own harvest of the bound cycle, **applied**, advancing the authored quest from its
  working node to its completion node;
* the retry an outbox would send, answered duplicate with no second advance.

Server-side afterwards: the journal row had advanced, three posted identities had produced exactly
three stored rows and the retry none, and the reward delivery was acknowledged and applied. All six
live tests passed with none skipped. Everything was torn down: server stopped, secret shredded,
worktree removed, database dropped.

### Not covered, stated plainly

The acceptance replay is proven at the delivery-ledger level only, because no in-world gesture
re-triggers acceptance. Clientbound payload contents cannot be observed in the GameTest harness.
The toast and its sound, the parchment art, portraits and real glyph metrics need a running client.
The Rails half of the fixture mirror was verified by the Rails-side agent, not the mod-side one.

### Next action (at M11 close)

M12 — the release handoff: SHAs, diffs, test totals, jar identity, migrations and seeds, deployment
order, rollback, operator setup and the remaining live gates, prepared but not executed.

## M12 — Release handoff (PREPARED 2026-09-08; live acceptance NOT started)

The handoff is `ROWAN_FARMING_QUESTLINE_RELEASE_HANDOFF.md` beside this document: milestone SHAs,
test totals, the candidate build's identity, the migration and seed list, deployment order and
rollback, operator setup, unresolved risks and owner decisions, what automated testing cannot
establish, and the live acceptance prerequisites.

**Nothing was pushed, merged, deployed, seeded to a live database, or uploaded.** Those five actions
are the owner's, each prepared as a single reviewable step.

All 58 items in `ROWAN_FARMING_QUESTLINE_ACCEPTANCE_DRAFT.md` remain unchecked, because no
behaviour has been observed in a live game. The playbook's rule that an acceptance box is checked
only when observed has been kept throughout.

### Next action

The owner's: deploy Rails, seed the questline, deploy the jar, place Rowan with the infrastructure
listed in the handoff, then walk the acceptance draft.
