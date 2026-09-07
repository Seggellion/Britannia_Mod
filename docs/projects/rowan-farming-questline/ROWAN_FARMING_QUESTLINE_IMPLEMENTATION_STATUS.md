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
| M0 Baselines, worktrees, executable contracts | **PASSED** | (this commit) | (this commit) | 2026-09-06 |
| M1 Quest security and permanent-item safety | not started | | | |
| M2 Rails reward-delivery ledger and replay | not started | | | |
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

### Next action

M1 — Quest security and permanent-item safety (NeoForge only): temporary-item contract in
`QuestRewardService`/`QuestCleanupService`, conservative legacy-stamp migration, spawner
permission/reach/block-entity/payload validation in `NetworkHandler`, coin and keybinding lang.
Delegated to a NeoForge sub-agent with the file boundary above; the integrator reviews the diff,
runs the focused + full JUnit suites and the GameTests, and commits
"fix(quests): protect permanent rewards and quest-giver configuration".
