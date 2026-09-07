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
| M1 Quest security and permanent-item safety | **PASSED** | (this commit) | — | 2026-09-07 |
| M2 Rails reward-delivery ledger and replay | **PASSED** | — | (this commit) | 2026-09-07 |
| M3 NeoForge reward reconciliation | not started | | | |
| M4 Rails action-objective and progress contract | not started | | | |
| M5 NeoForge farming events, outbox, crop attribution | not started | | | |
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

### Next action

M3 — NeoForge reward reconciliation (mod only) and M4 — Rails action-objective contract (Rails
only), in parallel; the M3 cross-repository gate runs against the M2 Rails code in a disposable
database.
