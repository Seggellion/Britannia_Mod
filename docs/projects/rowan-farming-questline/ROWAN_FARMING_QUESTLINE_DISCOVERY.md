# Rowan the Farmer — "From Soil to Supper" questline discovery

Inspected 2026-09-06. Exploration and planning preparation only: no gameplay, quest content,
migrations, reward grants, UI changes, commits, pushes, branch switches, seeds, or live-database
mutations were made. The companion documents are
[ROWAN_FARMING_QUESTLINE_PLANNING_INPUTS.md](ROWAN_FARMING_QUESTLINE_PLANNING_INPUTS.md) and
[ROWAN_FARMING_QUESTLINE_ACCEPTANCE_DRAFT.md](ROWAN_FARMING_QUESTLINE_ACCEPTANCE_DRAFT.md).

This document records what the two inspected checkouts actually do. Every statement about
behaviour is a code reading of the recorded HEADs unless a row says "observed" (a test run) or
"unverified" (needs a live shard). Line numbers refer to the recorded HEADs.

## 1. Verdict in one paragraph

The building blocks exist and work in isolation: a Rails quest engine with server-authoritative
objective detection, idempotent completion, a quest-giver spawn block that already lets an
administrator place a named NPC anywhere, and a complete dung → dirt → bowls → fertilizer → public
plot → plant → harvest chain in the mod. Nothing links the two halves for farming yet: the engine
only knows three objective kinds (walk into a volume, pick up an item, destroy an item in a
volume), and the farming code fires exactly one server event (dung harvest). Two reward-durability
defects would undermine the questline as designed (rewarded items are deleted at the next login
once their quest ends; equipment granted on acceptance is not replay-protected), one
administrative payload is unauthenticated, and the harvest end-point needs planter attribution
that no plot records today. The project is feasible on the existing architecture; it does not
depend on a quest-engine rewrite, a quest importer, or a diagram editor. It does depend on the
extensions and fixes catalogued in §5 and §7 and on operator-provided public infrastructure
(exposed dirt, a Water Well, Community Farm Blocks near each placed Rowan).

## 2. Repository snapshots

### 2.1 NeoForge mod (primary repository)

| Field | Observed value |
| --- | --- |
| Inspected root | `C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\rowan-farmer-exploration-97e04a` (git worktree of `C:\projects\britannia\mod\Britannia_Mod`) |
| Branch / HEAD | `claude/rowan-farmer-exploration-97e04a` @ `0cd566dd811bb717c6bda498ac3bed38be64b95e` |
| Relationship to patch-18 | Identical: the main checkout's `patch-18` is also `0cd566dd`. Nothing on this lineage moved since the earlier farming checklist, which inspected the same HEAD. |
| Published lineage | `origin/patch-18` = `origin/main` = `public/release` = `5c0b9172` (parallel lineage, merge-base `bcde513c`). `git diff 0cd566dd 5c0b9172 -- src` differs by 2 files / 275 insertions (weapon integration); the rest of the delta removes 20 internal documents. Neither commit is an ancestor of the other. |
| Working tree | Clean at inspection start and at inspection end (`git status --porcelain` empty; `build/` is ignored). The session-start snapshot listed staged deletions, which were the worktree materialising; they were gone by the first status check. |
| Runtime | Minecraft 1.21.1, NeoForge 21.1.72 (`gradle.properties` `neo_version=21.1.72`), mod `0.1.8a`, mod id `britannia_mod`, Java 21.0.9 (Eclipse Adoptium), Gradle 8.9 (installed distribution, not the wrapper) |
| Instructions | No `AGENTS.md` or `CLAUDE.md` anywhere in the tree (searched all depths excluding `.git`, `build`, `.gradle`). `.claude/settings.local.json` holds tool permissions only. |
| Documentation convention used | `docs/projects/<project>/` (existing peers: `docs/projects/`, `docs/vendor-trader-economy/`, `docs/new-assets/`, `docs/shrines-monoliths/`). The two earlier farming reports sit untracked at the canonical root; they are cited, not moved. |
| Local server config | `run/config/britannia_mod-server.properties` and `run/server/config/britannia_mod-server.properties` exist only in the canonical checkout (not in this worktree); key names `shard_name`, `shard_secret`, `api_base_url`, `minecraft_server_key`, `allow_integrated_server`, `rails_update_listener_enabled`. Values not printed. |

### 2.2 Rails website and quest backend

| Field | Observed value |
| --- | --- |
| Inspected root | `/home/dusti/ultimacraft-website` inside WSL Ubuntu (`\\wsl.localhost\Ubuntu\home\dusti\ultimacraft-website`); accessed only through `wsl.exe --exec bash -lc` |
| Branch / HEAD | `release/public` @ `5b4a22d7f5344c77584983c8c15872ff47e033d3` |
| Working tree | Dirty with the owner's own work, left untouched: `app/themes/Avatar/views/pages/article.html.erb` (modified), `db/seeds/grapes.rb` (modified: three `grape_color` values), plus three untracked playbooks and their `Zone.Identifier` files |
| Runtime | Ruby 3.2.2 via rbenv (system Ruby is 3.2.3, so `bin/rails` must be run as `rbenv exec ruby bin/rails`), Rails 8.0.1, PostgreSQL local (`pg_isready` accepting on `/var/run/postgresql:5432`), schema version `2026_09_02_160000` |
| Instructions | No `AGENTS.md` or `CLAUDE.md` in the repo. `README.md` documents `bin/codex_test` and the per-file `db:seed:*` task convention; `db/seeds/README.md` states there is deliberately no aggregate `db/seeds.rb`. |
| Environment | No `.env` on this machine (`.env.example` only). `DATABASE_URL`/`TEST_DATABASE_URL` unset in the login shell. |

### 2.3 Not inspected

The Fabric/Atrevion repository was not opened. Nothing below uses Fabric behaviour as evidence.

### 2.4 What changed since the earlier farming checklist

Nothing on this lineage. `FARMING_GAMELOOP_DISCOVERY.md` and
`FARMING_EMPTY_INVENTORY_TEST_CHECKLIST.md` (untracked, canonical root, dated 2026-09-06) inspected
the same `0cd566dd`. Their findings were treated as search leads and re-read against source; the
differences and confirmations are called out inline below. Their JUnit log
(`build/farming-discovery-junit.log`, 230 passed / 6 skipped) was reproduced in this worktree with a
wider selection (§8).

## 3. Source index

Repository-relative paths. Mod paths are under
`src/main/java/com/seggellion/britannia_mod/` unless they start with `src/main/resources`.

### 3.1 Mod: quest engine client/server

| Area | Files (line ranges that matter) |
| --- | --- |
| Spawn block and its config | `block/QuestGiverSpawnBlock.java` (placement 60-64, admin-only screen 66-80, removal 97-113); `block/entity/QuestGiverSpawnBlockEntity.java` (tick 56-90, spawn/restore 92-184, leash 186-218, `applyConfig` 229-242, NBT 324-348); `client/gui/QuestGiverSpawnScreen.java` (archetype list 28, widgets 44-87, save 89-106); `network/QuestGiverSpawnConfigC2SPayload.java`; `network/QuestGiverSpawnScreenS2CPayload.java`; server handler `network/NetworkHandler.java:303-316` |
| Quest giver entity and look | `entity/QuestGiverEntity.java` (identity field 31-33, `resolveQuestGiverApiId` 76-91, role title 100-102, interaction 118-158, nameplate 161-178); `entity/CitizenEntity.java` (gender/personal name/clothing data 110-135, clothing indices 208-241, `finalizeSpawn` 246-262); `client/model/QuestGiverGeoModel.java`; `client/renderer/entity/QuestGiverEntityRenderer.java`; `client/renderer/CitizenClothingLayer.java` (outfit table 49-64, slot fallback 110-118); `client/renderer/PortraitDownloader.java` (fallback/cache 42-57); `client/renderer/PortraitFetch.java` (URL 25, 61-63) |
| Server proxy to Rails | `quest/QuestProxyService.java` (`handle` 46-98, client TRIGGER refused 63-66, journal gate 376-381, dispatch and single retry 133-186, `callRails` 240-287, `applyAuthoritativeResult` 289-337, NPC resolution 340-359, shape 361-374); `quest/network/QuestServerAPI.java` (trigger/kill/quit 30-56); `quest/QuestJournalRefresh.java`; `quest/ServerQuestTable.java` (JournalState 30-37); `server/http/RailsApiUrlResolver.java:184-194` (quest endpoints); `server/http/BoundedHttp.java:9-11`; `server/http/ServerHttpExecutor.java:18,55-56` |
| Rewards and cleanup | `quest/QuestRewardService.java` (apply 29-65, id resolution 73-77, stamping 79-103); `quest/QuestCleanupService.java` (quit cleanup 32-43, stale cleanup 45-56, item predicates 135-141 and 229-236, `ActiveQuestIds.matches` 322-331) |
| Objective detection | `quest/QuestObjectiveWatcher.java` (tick/pickup/destroy/direct 54-101, in-flight and cooldown 109-135, success 144-163); `quest/QuestObjectiveTriggers.java` (parsers 53-70); `quest/events/QuestEventHandlers.java` (tick 70-79, pickup 85-91, expiry return 93-132, lava destroy 134-220, advancement 380-404); `event/QuestEventHandler.java` (kill reporting 84-100); `block/entity/QuestDestinationBlockEntity.java` (escort arrival 34-106) |
| Journal transport | `quest/ClientQuestEntry.java`; `network/payload/QuestEntryCodecs.java:10-32`; `network/payload/ClientboundSyncQuestsPayload.java`; `quest/QuestEntryParser.java:41-104`; `event/WorldBootstrapHandler.java` (login/logout 66-82, journal apply and stale cleanup 142-147, failure path 240-256) |
| Client screens | `client/gui/QuestDecisionScreen.java` (init 54-119, choice 121-132, reject heuristics 169-180, ESC 205-212, close/abandon 221-229); `client/gui/QuestJournalScreen.java` (geometry 21-32, rows and Quit 59-88, text 103-145, scroll 147-157); `client/gui/DialoguePresentation.java` (constants 19-34, render 79-149, paper 151-168); `dialogue/DialogueLayout.java:12-48`; `dialogue/QuestDialogueAdapter.java:13-33`; `client/gui/MenuScreen.java:31-34`; `client/Keybinds.java:21-26`; `network/ClientNetworkHandler.java` (open screen 97-116, trigger result 268-316, client actions 318-355, escort presentation 415-429) |
| Screen art | `src/main/resources/assets/britannia_mod/textures/screens/dialogue_screen.png` (parchment band), `textures/screens/quest-journal.png` (scroll), `textures/screens/portraits/{generic_peasant,Guardian,zorathiel}.png` |
| Tests | `src/test/java/.../quest/QuestProxySecurityTest.java`, `quest/QuestEntryParserCompatibilityTest.java`, `dialogue/QuestDialogueAdapterTest.java`, `event/BootstrapRequestTrackerTest.java`; GameTests `gametest/Quest{Lifecycle,ObjectiveWatcher,GiverIdentity,JournalRefresh,ActionTelemetry,EscortAssignment}GameTests.java` |

### 3.2 Mod: farming chain

| Area | Files |
| --- | --- |
| Dung | `wildresource/WildResourceEntries.java` (tuning 38-45, entry 103-131, node inspection 148-166); `wildresource/WildResourceInteractionHandler.java` (28-43, 76-86); `wildresource/WildResourceHarvestService.java` (21-49, 85-99); `wildresource/WildResourceHarvestPolicy.java` (16-50); `event/WildResourceHarvestEvent.java`; `wildresource/WildResourcePlacementRules.java:62-65`; `wildresource/WildResourceReconciliation.java:26-31`; `block/DungBlock.java`; `mixin/client/ClientAdventureBreakGateMixin.java:60-84`; `docs/wild-resources.md` |
| Dirt | `dirtgathering/DirtGatheringInteractionHandler.java:29-48`; `dirtgathering/DirtGatheringService.java:18-61`; `registry/ToolRegistry.java:27-30` |
| Water and bucket | `util/WaterSourceInteraction.java:26-74`; `util/WaterSourceAccessPolicy.java:13-21`; `block/WaterWellBlock.java:28-41`; `block/WaterBarrelBlock.java:53-62`; `block/WaterTroughBlock.java:88-100`; generated vanilla sources under the canonical checkout's `build/neoform/neoFormJoined1.21.1-20240808.144430/steps/unzipSources/unpacked/`: `net/minecraft/world/item/BucketItem.java:40-72`, `net/minecraft/world/entity/player/Player.java:1764-1770`, `net/minecraft/world/item/ItemStack.java:373-374,1021-1024` |
| Bowls and mixing | `bowlpreparation/BowlPreparationItem.java:27-75`; `bowlpreparation/FertileDirtMixingItem.java:22-54`; `bowlpreparation/BowlWaterFillingService.java`; `bowlpreparation/BowlPreparationService.java`; `bowlpreparation/FertileDirtMixingService.java`; `bowlpreparation/BowlPreparationOutput.java` |
| Public soil | `block/CommunityFarmBlock.java:66-81`; `block/CommunityHoedFarmBlock.java:28-67`; `block/entity/CommunityFarmBlockEntity.java:15-16,36-56`; `item/FarmingHoeItem.java:32-57,94-117`; `item/FertilizedDirtItem.java:29-74` |
| Planting, growth, harvest | `block/FarmingBlock.java` (use 110-211, care 213-227, bucket watering 229-250, random tick 344-401, ownership 436-445, plant 447-540, harvest 542-657, tool rule 680-688, annual reset 746-761, exhaustion 763-775, empty-hand harvest 777-787, owner on placement 820-829); `block/entity/FarmingBlockEntity.java` (constants 38-41, plant 151-171, community window 316-321, owner/`mayPlant` 341-369, reclaim 385-391, growth context 508-546, `tickGrowth` 548-597, NBT 652-712); `farming/CropRegistry.java` (definitions 98-165, forbidden climates 291-310, altitude 312-358, harvest tools 453-488); `farming/CropQualityCalculator.java` (hydration fit 30-41, nutrient fit 53-68, growth multiplier 105-113); `farming/CropDefinition.java`; `farming/GrainHarvestTools.java`; `farming/RootCropShovelTools.java`; `src/main/resources/data/britannia_mod/tags/item/{root_crop_shovels,grain_harvest_blades}.json` |
| Skill gate | `farming/FarmingCultivationGate.java` (results 29-37, permits 92-96, evaluation 131-162, subject 179-195, feedback 197-210); `farming/FarmingSkill.java:14-42`; `skill/SkillManager.java` (states 61-65, login load 233-278, unavailable 311-316, snapshot 633-636) |
| Watering can | `item/WateringCanItem.java` (charges 39-40, `useOn` 46-73, `use` refill 75-84, watering 86-128, charge storage 186-198) |
| Coins | `registry/ItemRegistry.java:170-175`; `economy/CoinConversion.java:42-53`; `bank/currency/CurrencyItemRegistry.java:46-48` |
| Identities and names | `registry/ItemRegistry.java` (dung/bowls 337-346, fertilized dirt/watering can/hoe 990-997, scissors 1130, spawn block item 2119-2121); `registry/BlockRegistry.java` (community farm 248, farming block 268, dung 334, quest giver spawn 591); `src/main/resources/assets/britannia_mod/lang/en_us.json` (114-115, 159, 276, 287, 420-421, 907, 992-995, 1019-1024, 1176, 1223, 1310, 1440, 1668, 1683-1694) |
| Fixture-style integration tests | `gametest/Patch18FullLoopGameTests.java:67-130`; `gametest/FertileDirtFinalMixGameTests.java`; `gametest/FertileDirtLifecycleGameTests.java`; `gametest/FarmerMerchantSpawnGameTests.java` |

### 3.3 Rails

| Area | Files |
| --- | --- |
| Models | `app/models/quest.rb`, `app/models/node.rb`, `app/models/edge.rb` (unused by the API), `app/models/player_quest_state.rb`, `app/models/achievement.rb`, `app/models/user.rb:26-45`, `app/models/skill.rb`, `app/services/uo_skill_roster.rb:14-15,53` |
| API | `app/controllers/api/quest_states_controller.rb` (start 19-72, journal 88-110, interact 114-223, clear_all 226-241, record_kill 244-274, abandon 277-296, trigger_node 298-415, transition 422-455, player_state 464-483, quit 486-514, replay 566-579, correlation id 584-585, formatting 588-632, identity 694-724); `app/controllers/api/world_bootstrap_controller.rb` (journal 41,74; provisional users 211-249; journal scope 252-261); `app/controllers/api/skills_controller.rb` (player_skills 62-88, gain 90-115) |
| Engine | `app/services/quest_engine/processor.rb` (call 24-46, replay 58-79, advance 81-148, refusals 150-170, response 238-256); `app/services/quest_engine/condition_evaluator.rb:22-72`; `app/services/quest_engine/effect_applier.rb` (call 19-63, rewards no-ops 84-95, give_item 97-115, escort 117-125, stats 138-163); `app/services/achievements/award_quest_achievement.rb`; `app/services/achievements/public_policy.rb:37-57`; `app/serializers/quest_journal_entry_serializer.rb` |
| Auth and limits | `app/controllers/concerns/api/shard_server_authentication.rb` (36, 40-64, 98-133); `app/services/api/server_authenticator.rb`; `app/services/api/request_signature_verifier.rb:22-34`; `app/controllers/concerns/api/endpoint_rate_limit.rb` |
| Authoring | `app/controllers/admin/quests_controller.rb:55-63`; `app/controllers/admin/application_controller.rb`; `app/views/admin/quests/{_form,_node_fields,_choice_fields,_conditions,_effects,index,show,edit,new}.html.erb`; `app/javascript/controllers/json_array_builder_controller.js:74-116`; `app/javascript/controllers/nested_form_controller.js` |
| Routes and schema | `config/routes.rb:193-195` (dead public `quests`/`nodes` resources), `:444-453` (API), `:568` (public achievements page); `db/schema.rb` tables `quests`, `nodes`, `edges`, `player_quest_states`, `achievements`, `skills`, `shards`; migrations `20260228233235_quest_engine_core.rb`, `20260608000000_add_journal_lifecycle_to_player_quest_states.rb`, `20260812050147_normalize_player_quest_state_player_uuids.rb`, `20260812060414_add_completion_idempotency_to_player_quest_states.rb`, `20260827150000_add_quest_to_achievements.rb` |
| Content examples | `db/seeds/escorts_britannia.rb` (the working quest followed end to end), `lib/tasks/seed_escorts_britannia.rake`, `db/seeds/farmer_vendor.rb` (farming equipment prices), `db/seeds/uo_skills.rb`, `db/seeds/README.md`, `docs/shard_public_achievements.md` |
| Tests | `test/controllers/api/quest_states_controller_test.rb`, `quest_completion_idempotency_test.rb`, `quest_completion_identity_test.rb`, `quest_journal_endpoint_test.rb`, `quest_multiplayer_isolation_test.rb`, `quest_observability_test.rb`, `quest_achievement_attribution_test.rb`; `test/services/quest_engine/completion_concurrency_test.rb`; `test/services/achievements/award_quest_achievement_test.rb`; `test/integration/farmer_vendor_seed_test.rb` |

## 4. Existing quest architecture (how a working quest actually runs)

The escort quests (`db/seeds/escorts_britannia.rb`) were followed from authoring to reward; they
are the only shipped content, so they define "working" here.

### 4.1 Representation in Rails

* **Quest** (`quests`): `title`, `description`, `origin_npc` (the join key to an NPC), `priority`,
  `active`, `shard_id`, `start_conditions` (jsonb; recognised keys are `repeatable` and
  `prerequisite_quest_id`, read at `quest_states_controller.rb:30-35,157-166`). There is no version,
  slug, key, or questline column; `QuestJournalEntrySerializer#quest_key` falls back to
  `"quest_<id>"` (`quest_journal_entry_serializer.rb:76-84`).
* **Node** (`nodes`): `title` (the routing key: choices point at `destination_node_title`), `body`,
  `node_type` enum `decision:0 / ending:1 / info:2`, `metadata` jsonb. Everything else lives in
  `metadata`: `choices` (a hash keyed by an arbitrary string; each choice has `text`,
  `destination_node_title`, `conditions`, `effects`), and the three observer blocks
  `location_trigger`, `pickup_trigger`, `destroy_trigger`. The `edges` table and `Edge` model exist
  but no API code reads them (`node.rb:6-12` aliases `choices` to edges, `Processor#advance` reads
  `metadata["choices"]` instead).
* **Conditions** (`condition_evaluator.rb:36-63`): `attribute` (`>`, `>=`, `<`, `<=`, `==`/`eq`
  against `state_variables.attributes`), `flag`, `skill_check` (seeded d20), plus `ALL`/`ANY`
  nesting. A choice carrying `trigger_key` is hidden from dialogue and waits for a server trigger
  (`quest_states_controller.rb:617-620`, `processor.rb:227-231`).
* **Effects** (`effect_applier.rb:19-63`): `increment`, `flags`, `give_item` (hash `{id: count}`,
  array, or string; becomes `granted_items` for the mod), `spawn_escort`, `give_karma`/`give_fame`
  (writes `ShardUser`, needs a persisted shard), `grant_achievement` (creates the website
  Achievement row and queues a client toast). `rewards` of type `xp`/`item` are no-ops on `User`
  (`user.rb:36-45` prints or writes a `global_inventory` hash the mod never reads). There is **no**
  random reward, take-item, consume-item, or per-player item-count effect.
* **PlayerQuestState** (`player_quest_states`): one row per `(player_uuid, quest_id)` (unique
  index), `current_node_id`, `state_variables {attributes, flags}`, `status`
  accepted/active/abandoned/completed, `completed`, `accepted_at`, `abandoned_at`,
  `completion_request_uuid`, `completion_result`. Restart reuses the same row and wipes `flags`
  while keeping `attributes` (`player_quest_state.rb:33-42`).
* **Achievement** (`achievements`): unique per `(user_id, shard_id, name)`; quest awards are named
  `"Quest: <Slug Titleized>"`, type `QuestAchievement`, 50 points, linked to the quest
  (`award_quest_achievement.rb:18-52`); the public shard page shows quest awards only when the
  linked quest still exists on the same shard (`public_policy.rb:37-57`,
  `docs/shard_public_achievements.md`).

### 4.2 NPC assignment

Quest givers are **not** part of Rails' NPC registry (`WorldNpc`, `ServiceNpcSpawnPoint`,
`NpcSpawnAssignment` govern service and economic NPCs only; the quest-giver spawner never migrates
and never registers). The only link is the string `quests.origin_npc`, matched exactly and
case-sensitively against the NPC's `questGiverApiId` (`quest_states_controller.rb:135,143`).
Rails does not know where a quest giver stands, how many exist, or what they look like.

### 4.3 Acceptance and dialogue

1. Right-click on a `QuestGiverEntity` (`QuestGiverEntity.java:118-158`) sends
   `QuestActionC2SPayload(INTERACT)` with the entity id and UUID.
2. `QuestProxyService.handle` validates shape, resolves the entity (must be alive and within 8
   blocks, `QuestProxyService.java:353-359`), and POSTs `quests/interact` with `player_uuid`,
   `request_uuid`, `npc_name = questGiverApiId` (`callRails` 240-287). Authentication: `Shard-Name`
   + `Shard-Secret` headers, optional HMAC signature (`shard_server_authentication.rb:40-64`;
   signatures are not yet mandatory, line 36).
3. Rails `interact` (`quest_states_controller.rb:114-223`): if the player has an active state for
   any quest of this NPC it is returned; otherwise the NPC's active quests are scanned by
   `priority DESC`, `prerequisite_quest_id` is enforced (the prerequisite must be `completed:
   true`, 157-166), and the first eligible quest gets a **state created immediately at its first
   node** (191-218). Acceptance in Rails terms happens on the first click, before the player has
   read the offer. A completed, non-repeatable quest is skipped; an abandoned or
   rejected/failed one restarts.
4. The response (`format_active_state` 588-606: node id/title/text/type/**full metadata**,
   choices with `is_locked`, `player_state`, `accepted_quest` journal entry) is forwarded to the
   client verbatim (`QuestProxyService.java:167`) and opens `QuestDecisionScreen`.
5. Clicking a choice sends `CHOOSE`; Rails `transition` runs `QuestEngine::Processor`
   (`processor.rb:24-148`): row lock, condition check, effects and node advance in one
   transaction, `granted_items` returned; when the destination is an `ending`, the exact response
   is stored under the request's `request_uuid` so a retry replays it verbatim (138-145). A
   different request id against a finished quest is refused (`163-170`).
6. Closing the screen without choosing sends `ABANDON`; Rails abandons only while the state is on
   the first node (`277-296`); otherwise the quest stays active.

### 4.4 Objective detection (Milestone 6 design)

The server, not the client, decides objectives. `QuestJournalEntrySerializer#triggers`
(`quest_journal_entry_serializer.rb:41-74`) publishes the current node's `location`, `pickup`,
and `destroy` observers with the journal; `ServerQuestTable` holds them per player;
`QuestObjectiveWatcher` fires on player tick (location), `ItemEntityPickupEvent.Post` (pickup),
and lava destruction (destroy), POSTs `quests/:id/trigger_node`, and rewrites the quest's
triggers from the response so a met objective cannot fire twice (`QuestObjectiveWatcher.java:54-163`).
Client-asserted triggers are refused outright (`QuestProxyService.java:63-66`). An escort's
arrival is a server-decided trigger sent through `fireDirect` (`QuestDestinationBlockEntity.java:97-99`).
Kills are counted through `quests/record_kill`, which increments `attributes.kills_<mob>` on
**every** active quest (`quest_states_controller.rb:244-274`).

### 4.5 Rewards on the mod

`QuestRewardService.apply` (`QuestRewardService.java:29-65`) resolves each `granted_items` id
(bare ids get the `britannia_mod:` namespace; explicit namespaces such as `minecraft:bucket` pass
through, 73-77), splits by max stack size, inserts into the inventory or **drops at the player's
feet when full** (line 60), and **stamps every stack** with `quest_item`, `quest_owner_uuid`,
`quest_owner_name`, `quest_id`, `quest_state_id` in `CUSTOM_DATA` (79-103). Stamped stacks do not
merge with unstamped ones. Coin identity is item-only, so stamped coins still spend and bank
(`CurrencyItemRegistry.java:39-43`).

### 4.6 Journal and login

The server journal is RAM only, filled by the login bootstrap (`WorldBootstrapHandler.java:142-147`)
or an on-demand refresh after a miss (`QuestJournalRefresh.java`), forgotten at logout (66-82).
The client mirror receives only the eight `QuestEntryCodecs` fields (state id, quest id, key,
giver name, name, brief description, accepted-at, status) — no objectives, no counters, no stage
number. Quest definitions themselves are never cached: every interaction fetches the current node
from Rails, so a content edit is visible on the next click or the next journal load.

## 5. Capability matrix

Classification: **exists and suitable**, **exists but needs extension**, **missing**,
**unverified**. "Proposed" items are described in the planning inputs; nothing here is
implemented.

| # | Capability required by the questline | Classification | Evidence |
| --- | --- | --- | --- |
| C1 | Author five sequential, visible quests linked by prerequisites | exists and suitable | `start_conditions.prerequisite_quest_id` at `quest_states_controller.rb:157-166`; escort seed shows quest+node upsert by `origin_npc`/title |
| C2 | Assign quests to a placed Rowan through the QuestGiverSpawn Block | exists but needs extension | Block, entity, config flow all work; the archetype list is hardcoded (`QuestGiverSpawnScreen.java:28`); a plain giver's name *is* its Rails key (`QuestGiverSpawnBlockEntity.java:171-178`). Adding "Rowan" to the list is the whole assignment change. |
| C3 | Multiple Rowan instances sharing one player's progress | exists and suitable | Journal is per player in Rails; `CHOOSE`/`ABANDON` do not bind to an entity (`QuestProxyService.java:340-351`); any instance with the same key serves the same state |
| C4 | Grant equipment on acceptance | exists but needs extension | `give_item` on the Offer node's accept choice grants at the first `CHOOSE`; but non-ending transitions are not replay-protected (`processor.rb:138-145`) and stamped rewards are deleted later (§7 D1) |
| C5 | Award coins on completion | exists but needs extension | `give_item {"gold_coin": n}` works and resolves to `britannia_mod:gold_coin`; same durability defects; silver/copper have no display names (§7 D5) |
| C6 | Award a watering can, bucket, bowls, hoe, shovel, seed | exists but needs extension | All are registered items resolvable by id (`ItemRegistry.java:339, 990-997`, `ToolRegistry.java:27-30`, vanilla `minecraft:bucket`); durability defects apply; a rewarded watering can reads as full because a missing charge tag means 12 charges (`WateringCanItem.java:186-191`) |
| C7 | Random seed selection persisted per player | missing | No random effect in `EffectApplier`; triggers are static per node, so a later stage cannot vary by the player's roll without an extension |
| C8 | Detect dung harvest | exists but needs extension | `WildResourceHarvestEvent` is posted on every authoritative harvest (`WildResourceHarvestService.java:85-99`); no quest listener consumes it. The existing `pickup_trigger` on `britannia_mod:dung` would credit any dung item pickup, including someone else's drop — unsuitable alone |
| C9 | Detect dirt gathering | missing (hook point exists) | `DirtGatheringService.attempt` returns `GATHERED` with no event (`DirtGatheringService.java:49-60`) |
| C10 | Detect bucket filling | missing (hook point exists) | `WaterSourceInteraction.fillFromSource` fills a bucket with no event (`WaterSourceInteraction.java:56-58`); vanilla `FILLED_BUCKET` advancement criterion never fires in Adventure (§6.3) |
| C11 | Detect fertilizer mixing | missing (hook point exists) | `FertileDirtMixingService.apply` returns `APPLIED` with no event |
| C12 | Detect hoe, fertilize, plant, water, harvest | missing (hook points exist) | `FarmingHoeItem.prepareCommunityPlot`, `CommunityHoedFarmBlock.fertilizeCommunityPlot`, `FarmingBlock.tryPlantSeed` (line 538 debug log only), `WateringCanItem.waterFarmingBlock`, `FarmingBlock.tryHarvestCrop` (line 640 debug log only) |
| C13 | Attribute the harvested crop to the planter | missing | `FarmingBlockEntity` records an owner only for private plots (`FarmingBlockEntity.java:341-369`); `plant()` records no planter (151-171); community crops are harvestable by anyone (`tryHarvestCrop` has no ownership check for community plots) |
| C14 | Show stage number, sub-progress, next action in the journal | missing | `ClientQuestEntry`/`QuestEntryCodecs` carry no such fields; journal rows show name, giver, one-line description, date |
| C15 | Achievement on the website | exists and suitable | `grant_achievement` → `AwardQuestAchievement` → public feed (`public_policy.rb`, `shard_achievements_controller.rb`) |
| C16 | Achievement in game (toast) | exists and suitable | `client_actions` type `achievement` → `SystemToast` + challenge sound (`ClientNetworkHandler.java:326-337`) |
| C17 | Achievement in game (persistent advancement) | exists but needs extension | Advancement grant exists only in the lava-destroy path (`QuestEventHandlers.java:380-399`, data `advancement/quest/ring_destroyed.json`); a completion-time grant and a `quest/first_harvest.json` are needed |
| C18 | Idempotent completion across a lost response | exists and suitable (endings only) | `completion_request_uuid`/`completion_result` replay (`processor.rb:58-79`, `quest_states_controller.rb:566-579`); mod retries a `CHOOSE` once on 503/transport failure with the same id (`QuestProxyService.java:181-186`) |
| C19 | Progress counters (e.g. 3 dung) | exists but needs extension | `record_kill` is the only counter endpoint and it is mob-specific; `attribute` conditions can lock a turn-in choice until a counter is met |
| C20 | Sub-step flags | exists and suitable | `quests/:id/player_state` merges flags server-to-server (`quest_states_controller.rb:464-483`); nothing publishes flags to the client |
| C21 | Repeatable reissue of lost supplies with a cap | exists and suitable | `increment` effects plus `attribute` conditions (`<` supported) gate a dialogue choice; attributes survive restart |
| C22 | Rowan portrait and profession label | exists but needs extension | Portrait is fetched from `https://storage.googleapis.com/ultimacraft/portraits/<gender>/<Name>.png` else generic peasant (`PortraitFetch.java:25,61-63`, `PortraitDownloader.java:42-57`); `QuestDialogueAdapter` passes an empty profession label (`QuestDialogueAdapter.java:26`); quest givers have role title "Wanderer" (`QuestGiverEntity.java:100-102`) |
| C23 | Rowan outfit | exists but needs extension | `CitizenClothingLayer.OUTFIT_TEXTURES` knows `wood_trader`, `fish_trader`, `salvage_trader` only (49-64); apron/boot textures exist for both genders; quest givers never receive an outfit key |
| C24 | Directions to infrastructure | missing | No location fields beyond `cityName` and the block position; location triggers are absolute coordinates per node, not per instance |
| C25 | Content installation that preserves progress and prevents duplicates | exists but needs extension | Seed pattern `Quest.find_or_initialize_by(origin_npc:, shard_id:)` + `Node.find_or_initialize_by(quest_id:, title:)` is idempotent; the escort seed regenerates choice keys with `SecureRandom.uuid` on every run (`escorts_britannia.rb:88,94`), which invalidates any dialogue open at that moment; the admin form cannot set `description` or `active` (`admin/quests_controller.rb:55-63`) |
| C26 | Skill readiness for planting | exists and suitable (fail-closed) | `FarmingCultivationGate` requires `SkillDataState.AVAILABLE`; the state is loaded once at login and never retried (`SkillManager.java:233-278,311-316`); the Rails roster does contain "Farming" (`uo_skill_roster.rb:53`, slug `farming` by friendly_id) |
| C27 | Local automated coverage of the integration boundary | exists but needs extension | Rails quest tests drive HTTP with real auth headers; mod GameTests drive `QuestProxyService.handle` with journal fixtures and a swappable journal fetcher (`QuestJournalRefresh.installFetcher`); no test drives farming actions through the objective watcher because no such trigger exists yet |

## 6. Spawn, assignment, and interaction path (Rowan)

### 6.1 Administrator placement

`QuestGiverSpawnBlock` can be placed only by a Creative or permission-level-2 player
(`getStateForPlacement`, `QuestGiverSpawnBlock.java:60-64`); it is invisible and non-colliding to
everyone else (37-56). Right-clicking it as an administrator sends the current config to the
client (66-80), which opens `QuestGiverSpawnScreen`: an **NPC archetype** cycle button over the
hardcoded list `Zorathiel, Lord British, Iolo, Dupre, Shamino, Generic Escort, Generic Combat`
(`QuestGiverSpawnScreen.java:28`), a city text box, a gender toggle (hidden for the two generic
archetypes), a wander radius, and an internal API id box shown only for "Generic Combat".
Save sends `QuestGiverSpawnConfigC2SPayload`; the server handler applies it to the block entity
**without checking the sender's permission** (`NetworkHandler.java:303-316`; see D3).

### 6.2 Persisted configuration and spawning

`QuestGiverSpawnBlockEntity` stores `NpcName`, `CityName`, `Gender`, `SpawnRadius`,
`SpawnedNpcId`, and a full NBT snapshot of the spawned NPC (`saveAdditional` 324-335). Every 200
ticks after a 40-tick grace it checks the tracked entity, restores it from the snapshot or spawns
a fresh one (56-90, 92-184), and leashes it to the radius with soft steering and a hard teleport
(186-218). A plain archetype gets `personalName = npcName`, `questGiverApiId = npcName.trim()`,
`cityName`, and the configured gender (171-178). `applyConfig` discards the current NPC and
respawns (229-242). Because the NPC is recreated from the block, respawns keep the same identity;
the entity UUID may change, which nothing in the questline depends on.

For Rowan this means: adding an archetype entry whose `npcName`/api id is the string the Rails
seed uses as `origin_npc` is sufficient for the existing workflow. No Rails record describes the
NPC; no bootstrap payload, cache, or reload step is involved beyond placing the block and saving
the screen. Reload behaviour: the block entity ticks after chunk load and restores the snapshot;
the entity's `questGiverApiId` is synched and saved (`QuestGiverEntity.java:31-59`), and legacy
`"Display:api_id"` names still resolve (76-91).

### 6.3 Appearance

Model and base texture follow gender (`QuestGiverGeoModel.java:11-33`). Clothing comes from
`CitizenClothingLayer`; with no outfit key every slot renders its default texture at index 1 (the
spawner adds the entity with `addFreshEntity`, so `finalizeSpawn`'s randomisation
(`CitizenEntity.java:246-262`) does not run). The dialogue portrait is downloaded by personal name
and gender from the GCS bucket; a 404 pins the generic peasant for the session
(`PortraitDownloader.java:35-39,57`). Rowan therefore needs a `Rowan.png` under the chosen gender
folder in that bucket, which is an asset task outside both repositories.

### 6.4 Multiple instances and resume

Any Rowan instance shares the same `origin_npc`, so interacting with any of them returns the
player's active state or the next eligible stage. The dialogue screen passes the entity UUID only
for display and escort activation; `CHOOSE` does not validate the NPC (`QuestProxyService.java:340-351`).
Progress lives in Rails and in the server journal, never in an entity. After a reconnect the
journal is rebuilt from the bootstrap (or refetched on the first miss), so the player can resume at
whichever Rowan is nearest. The only per-instance state is the block's leash and snapshot.

### 6.5 Directions

Nothing in the current design expresses "where is the well" per instance. The city name on the
block is used by escorts for routing only. Node bodies are global text. §7 and the planning inputs
recommend a dynamic nearest-infrastructure lookup on the server with an optional per-block hint
string as the smallest configuration.

## 7. Farming findings through first harvest

### 7.1 Stage 1: dung with the shovel in hand

* Dung is a scheduled, tracked terrain node (support must be exact `minecraft:dirt` or
  `minecraft:coarse_dirt`, `WildResourcePlacementRules.java:62-65`), attempted 6–12 minutes after a
  chunk becomes due, cap 2 per chunk, 4 probes, 8-block spacing, respawn 20–40 minutes after a
  harvest (`WildResourceEntries.java:38-45`, `docs/wild-resources.md`). Animals play no part.
* In Adventure a left-click on a tracked node is owned by `WildResourceInteractionHandler.onLeftClick`
  (28-43) and executed by `WildResourceHarvestService.harvestOne` (21-49): reach, `mayInteract`,
  house rights, not Creative, not a fake player (`WildResourceHarvestPolicy.java:16-50`); the block
  and ledger entry are removed, a respawn is scheduled, exactly one `britannia_mod:dung` is dropped
  at the node, and `WildResourceHarvestEvent(player, resourceId, position, dimension, result,
  toolCategory)` is posted (85-99). The client mixin permits the swing for any main-hand item
  because `DungBlock.allowsAdventureHarvest` returns true unconditionally
  (`DungBlock.java:26-29`, `ClientAdventureBreakGateMixin.java:66-71`). **Shovel-in-hand dung
  collection therefore already works exactly like the empty-hand path**; the tool is neither
  required nor consumed.
* Support-validation gap (reported earlier, confirmed): `standingTrackedEntry` excludes only
  `MISSING_OR_REPLACED` (76-86); `classifyDungNode` returns `OWNED_INVALID` when the support is
  gone (148-166); `harvestOne` never rechecks support, so an unsupported but still-standing pile
  yields one dung until reconciliation removes it (`WildResourceReconciliation.java:26-31`).
  Reachability: an ordinary Adventure player cannot remove protected dirt, so only administrative
  or physics changes create the state; the harvest is still a single tracked node with a scheduled
  respawn, so there is no duplication path. Impact on the quest: none beyond crediting a legitimate
  harvest.
* Failed swings (denied policy) return without loot and without an event; command-placed
  `/setblock` piles have no ledger entry and are ignored by the Adventure handler. An event-based
  objective therefore credits only tracked, authorised harvests.

### 7.2 Stage 2: custom Dirt without terrain change

`DirtGatheringInteractionHandler.onRightClickBlock` (29-48): main hand only, exact
`britannia_mod:britannia_shovel` (`ToolRegistry.SHOVEL`), target dirt or coarse dirt.
`DirtGatheringService.attempt` (18-61): mode/reach/world policy with player-facing messages
(`en_us.json:1690-1694`), a persistent per-player 1,200-tick cooldown claimed **before** the item
is created, one `britannia_mod:dirt` added or dropped, one durability point charged, success
message "You gather a handful of dirt." The terrain is untouched and the event is cancelled with
`SUCCESS` so the vanilla shovel path cannot flatten the block. No event or hook exists for a quest
listener; `Result.GATHERED` is the authoritative success point.

### 7.3 Stage 3: the bucket

* There is no Britannia bucket item; the "metal bucket" is vanilla `minecraft:bucket`
  (display "Bucket"; filled form `minecraft:water_bucket`, "Water Bucket").
* Vanilla filling from natural water does **not** work for an ordinary Adventure player:
  `BucketItem.use` requires `player.mayUseItemAt(...)` (`BucketItem.java:53-54`), which returns
  `abilities.mayBuild || stack.canPlaceOnBlockInAdventureMode(...)` (`Player.java:1764-1770`); a
  plain bucket has no `can_place_on` component, so the interaction fails before any pickup. The
  vanilla `FILLED_BUCKET` advancement criterion consequently never fires.
* The supported Adventure path is a block callback: `WaterWellBlock.useItemOn`
  (`WaterWellBlock.java:28-41`) → `WaterSourceInteraction.fillFromSource` (`33-74`), which accepts
  a watering can, a pitcher, an empty bucket, or an empty custom bowl, checks only
  `level.mayInteract` (`WaterSourceAccessPolicy.java:13-21`), replaces the bucket with a water
  bucket via `createFilledResult` (56-58), and plays the fill sound. The well is stateless and
  unlimited. Water barrels and troughs fill only pitchers (`WaterBarrelBlock.java:53-62`,
  `WaterTroughBlock.java:88-100`). **Stage 3 therefore requires a public Water Well**, which is
  consistent with the infrastructure decision; the observable server success point is the
  `setItemInHand` at line 57-58, which posts no event today.
* The empty/filled state is item identity, not NBT. Nothing later needs the bucket; the code does
  offer bucket watering of a farm plot (`FarmingBlock.java:229-250`) but the design keeps that out
  of the questline.

### 7.4 Stage 4: mixing (hand dispatch)

Confirmed against `BowlPreparationItem.use` (27-75) and `FertileDirtMixingItem.use` (22-54):

| Action | Main hand | Off hand | Result |
| --- | --- | --- | --- |
| Right-click air | Empty Bowl | custom Dirt | Bowl of Dirt (both consumed) |
| Right-click air | Bowl of Dirt | Dung | Bowl of Fertile Dirt (both consumed) |
| Right-click source water or the well | Empty Bowl | (empty recommended) | Bowl of Water; the source wins over the dry recipe (lines 34-45) |
| Right-click air | Bowl of Fertile Dirt | Bowl of Water | Fertilized Dirt in main hand + 2 Empty Bowls returned (`BowlPreparationOutput.giveOrDrop`) |

Only the main hand drives the dry recipes (line 47 and `FertileDirtMixingItem.java:27-29`);
reversed hands return `PASS` silently. Plans are revalidated at commit so a stale packet mutates
nothing; freed hands are refilled first, otherwise the output goes to the inventory or drops.
Stacked inputs consume exactly one of each. The authoritative success point is
`FertileDirtMixingService.ApplyResult.APPLIED`; no event exists.

Bowl appearance: `empty_bowl.json` uses `textures/item/patch18/empty_bowl.png`, a brown wooden
bowl sprite (viewed); display name "Empty Bowl" (`en_us.json:1686`), with "Bowl of Dirt",
"Bowl of Fertile Dirt", "Bowl of Water" at 1687-1689.

### 7.5 Stage 5: preparation, planting, growth, harvest

* **Hoe**: `CommunityFarmBlock.useItemOn` routes the exact `farming_hoe` to
  `FarmingHoeItem.prepareCommunityPlot` (94-117): becomes `community_hoed_farm_block`, 1
  durability, message "Public plot hoed. Apply fertilized dirt within 3 minutes.", Farming TOOL
  gain. Expiry is a deterministic block-entity ticker at 3,600 ticks
  (`CommunityFarmBlockEntity.java:15-16,36-56`). Runs in Adventure because it is a block callback.
* **Fertilize**: `CommunityHoedFarmBlock.fertilizeCommunityPlot` (44-67): `farming_block` with
  hydration 1, community seed window 1,200 ticks, 5 fertile harvests, 1 fertilizer consumed,
  message "Public plot fertilized. Plant seeds within 60 seconds." Reclaim of an empty expired
  plot happens on a **random tick** (`FarmingBlock.java:352-355`, `FarmingBlockEntity.java:385-391`);
  `tryPlantSeed` never checks the deadline, so at low random-tick rates a late planting still
  succeeds and no fertilizer is refunded on reclaim. Confirmed as the earlier report described.
* **Plant**: `tryPlantSeed` (447-540): ownership (`mayPlant`, community plots open to all),
  `FarmingCultivationGate` (skill data must be `AVAILABLE`, value ≥ crop minimum, Creative or
  permission ≥ 2 bypasses, 131-162), support, not already planted; consumes one seed, sets
  `PlantedCropId`, awards PLANT gain. No planter is recorded.
* **Growth**: only while the chunk is loaded and random-ticked. `randomTick` (344-401): 10 %
  chance per random tick to lose one hydration point; outdoor rain raises hydration to at least 2;
  growth runs only when hydration > 0. `tickGrowth` (548-597) adds
  `growthMultiplier / baseGrowthTicks` per random tick. `growthMultiplier`
  (`CropQualityCalculator.java:105-113`) is 0 when climate or altitude forbid the crop, otherwise
  `lerp(0.25,1.75,nutrientFit) × lerp(0.25,1.5,hydrationFit) × lerp(0.5,1.5,climateFit)`; both
  fits fall to 0 outside tolerance (30-41, 53-68). Growth never stalls to zero from hydration or
  nutrients alone; it slows to the 0.25 floors.
* **Harvest**: `tryHarvestCrop` (542-657): mature only; tool rule per crop (680-688): `HAND` any
  or empty hand, `BARE_HAND` empty only, `SCISSORS` exact scissors, `GRAIN_BLADE` the
  `grain_harvest_blades` tag (swords, dagger, katana, rapier, halberd, viking sword),
  `ROOT_SHOVEL` the `root_crop_shovels` tag, which contains **only** `britannia_mod:britannia_shovel`.
  Produce is `popResource`d at the plot (not inserted), seed return by chance, a fertile harvest
  is consumed (5 → 4), and an annual community crop resets to a fresh 60-second seed window while
  fertility remains (746-761). Anyone may harvest a community crop. Empty-hand harvest goes
  through `useWithoutItem` (777-787). No event exists; the debug log at 640-653 is the only trace.

### 7.6 Beginner crop eligibility (evidence-backed)

Farming skill of a fresh player is assumed 0.0 with `AVAILABLE` state (the Rails
`player_skills` endpoint creates zero rows for every skill, `skills_controller.rb:78-80`). Kit
assumed: Britannia Shovel, Farming Hoe, Watering Can, no scissors, no blade.

| Seed id → crop id | Display | Min skill | Support / space | Climate (ideal; forbidden) | Altitude | Harvest tool | Harvest output | Base growth ticks | Suitable with this kit? |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `britannia_mod:carrot_seeds` → `carrot` | Carrot Seeds / Carrots | 0.0 | none | TEMPERATE, ICE; FIRE forbidden | 45–130 | HAND (anything or empty) | exactly 3 Carrots, 25 % seed return | 5 | **Yes** (`CropRegistry.java:99`) |
| `minecraft:wheat_seeds` → `wheat` | Wheat Seeds / Wheat | 0.0 | none | TEMPERATE; FIRE forbidden | 50–120 | GRAIN_BLADE | 6 wheat + straw, 35 % seed | 5 | No: needs a blade the kit lacks (`109`, tag) |
| `britannia_mod:lettuce_seeds` → `lettuce` | Lettuce Seeds / Lettuce | 0.0 | none | TEMPERATE, ICE; FIRE | 40–120 | SCISSORS | 1–3, 30 % seed | 4 | No without scissors (`102`, `456-458`) |
| `britannia_mod:green_onion_seeds` → `green_onion` | Green Onion Seeds / Green Onion | 0.0 | none | TEMPERATE, ICE; FIRE | 45–120 | SCISSORS | 1–3, 30 % seed | 4 | No without scissors (`104`) |
| `britannia_mod:potato_seed` → `potato` | Potato Seed / Potato | 5.0 | none | TEMPERATE, ICE; FIRE | 45–130 | ROOT_SHOVEL (the Britannia Shovel) | exactly 4, 25 % seed | 5 | Tool yes, skill no at 0.0 (`106`) |
| `britannia_mod:pea_seeds` → `peas` | Pea Seeds / Peas | 5.0 | none | TEMPERATE | 45–140 | HAND | 2–4 | 6 | Skill no at 0.0 (`140`) |
| `britannia_mod:radish_seeds` → `radish` | Radish Seeds / Radish | 5.0 | none | TEMPERATE, ICE; FIRE | 45–120 | SCISSORS | 1–3 | 4 | No (skill and tool) (`160`) |
| `britannia_mod:turnip_seeds` → `turnips` | Turnip Seeds / Turnips | 5.0 | none | TEMPERATE, ICE; FIRE | 45–120 | SCISSORS | 1–3 | 5 | No (`141`) |

**Only carrot is eligible for a fresh Farming-0 player with the awarded kit.** The Farmer vendor
seed (`db/seeds/farmer_vendor.rb:29-32`) calls carrot, lettuce, green onion and potato
"bare-handed" starters; that comment is wrong for lettuce and green onion (scissors) and potato
(shovel, 5.0). Successful wheat planting is not proof of a tool-free harvest: wheat needs a blade.

### 7.7 Care, watering, and timing for carrot

Hydration is stored as 0–5 and evaluated as fifths. Carrot: `hydrationIdeal 0.55`, under-tolerance
0.30, over-tolerance 0.225, `minHydrationToGrow 0.25`, severe penalty above 0.85
(`CropRegistry.java:490-522`, `CropDefinition` derived fields). Nutrients start at 0 on a freshly
converted plot (`FertilizedDirtItem`/`fertilizeCommunityPlot` set none); no nutrient item is part of
the kit.

| Hydration after conversion / watering | hydrationFit | Multiplier (0 nutrients, ideal climate) | Progress per random tick (÷5) | Expected random ticks to mature | Expected wait at `randomTickSpeed=3` (≈ 68 s per random tick) |
| --- | --- | --- | --- | --- | --- |
| 1 (as converted) | 0.00 → factor 0.25 | 0.43 × 0.25 × 1.5 ≈ 0.16 | 0.032 | ≈ 31 | ≈ 35 min |
| 2 (one can use) | 0.50 → 0.875 | ≈ 0.56 | 0.113 | ≈ 9 | ≈ 10 min |
| 3 (two can uses) | 0.78 → 1.22 | ≈ 0.79 | 0.157 | ≈ 7 | ≈ 8 min |
| 4 | 0.00 → 0.25 | ≈ 0.16 | 0.032 | ≈ 31 | ≈ 35 min |
| 5 (fully watered) | 0.00 → 0.25 | ≈ 0.16 | 0.032 | ≈ 31 | ≈ 35 min |

Nutrient fit at zero nutrients is ≈ 0.12 (bone meal and rotten flesh outside tolerance, turquoise
and ash at 0.29 each, weighted), giving the 0.43 factor above. Random-tick expectation is
4096/3 ≈ 1,365 game ticks per block at the default `randomTickSpeed=3`; the local sandbox save
runs at 300 and grows in seconds. Decay: at hydration 3 the expected time to the first decay is 10
random ticks, so a plot watered to 3 typically finishes before it drops below 2. **Watering is
required in practice** (the unwatered wait is roughly four times longer), and **over-watering to 5
is as slow as not watering**. Rain outdoors sets hydration to at least 2. Watering can: 12
charges, +1 hydration per use via the block callback (Adventure-safe), refilled by right-clicking
a water source (`use`, 75-84) or the well (block callback); the `useOn` refill path is blocked in
Adventure by the same item gate as the bucket, but `use` covers it.

Climate is resolved by `FarmingClimateResolver` from the region/biome; this document assumes
TEMPERATE for the placement area. An ICE region is also ideal for carrot; a FIRE region forbids
growth outright (multiplier 0, `growthBlocked`).

### 7.8 Persistence, unloading, loss, and competition

* Plot and crop state are block-entity NBT (`FarmingBlockEntity.java:652-712`); growth resumes
  after reload but no catch-up happens while unloaded.
* The community seed window (60 s) and hoe window (3 min) are wall-clock in game ticks; leaving
  the area does not pause them (the hoe ticker pauses if the chunk unloads; the seed-window reclaim
  needs a random tick, so it also pauses).
* Crop loss: breaking a community `farming_block` clears the crop and reverts the block
  (`FarmingBlock.java:792-819`); anyone can harvest a mature community crop; a second player can
  plant on an empty fertilized plot (`mayPlant` true).
* Skill unavailability blocks planting without consuming the seed and shows "Farming skill data
  is unavailable; planting is blocked." (`en_us.json:1022`); the state never recovers without a
  relog (`SkillManager` has no retry after `markSkillDataUnavailable`).

### 7.9 Player time through first harvest (estimate, repository defaults)

Assumes infrastructure within ~50 blocks of Rowan, Farming 0 available, carrot awarded, default
random tick speed, temperate region.

| Stage | Active time | Waiting | Notes |
| --- | --- | --- | --- |
| 1 Dung | ≈ 1–2 min walking and one click | 0 if a pile already stands; otherwise 6–12 min to the next attempt per chunk, with no guarantee the attempt places dung (weighted among due resources, 4 probes) | Dominant variance; see the planning inputs for the scheduled-attempt proposal |
| 2 Dirt | ≈ 30 s | none (60 s cooldown is irrelevant for one gather) | |
| 3 Bucket | ≈ 1 min | none | needs the well |
| 4 Mixing | ≈ 2–3 min (inventory handling, four clicks, one well or water visit) | none | |
| 5 Prepare, plant, water | ≈ 1–2 min | ≈ 8–12 min growth if watered to 3; ≈ 35 min if left at 1 | the 60-second seed window is the tightest step |
| 5 Harvest | ≈ 10 s | | |

Typical total: about 6–9 minutes of actions and 10–25 minutes of waiting, of which the first dung
pile is the least predictable. The tutorial is practical only if a dung pile is likely to exist
when the player arrives; the planning inputs recommend scheduling an immediate attempt near Rowan
on acceptance.

## 8. Current UX audit (as shipped)

Observed from source and the shipped textures; no client session was launched. All descriptions
of layout are code readings, not screenshots.

* **Dialogue screen** (`QuestDecisionScreen`, `DialoguePresentation`, `DialogueLayout`): a
  parchment band 134 px tall across the top (`dialogue_screen.png`, viewed: torn-edge parchment),
  the rest of the screen dimmed. Portrait 88 × 88 visible at x = 30, name centred under it,
  optional profession label (empty for quests). Body text word-wrapped in the middle column,
  buttons 140 px wide on the right, 20 px tall with 4 px gaps. The text column width is
  `screenWidth − 160 − 163 − 20`: at a scaled width of 480 (GUI scale 4 on 1080p) that is 137 px,
  at 427 (scale 3 on 720p-class windows) 84 px. There is no clipping or scrolling, so long bodies
  overflow the band. Info nodes with one choice show "Next ->"; locked choices render disabled with
  no explanation. ESC or clicking away abandons an unaccepted quest (221-229). All strings are
  literal English (no localization keys). Colour: near-black text on parchment; disabled buttons
  are the only status cue.
* **Journal** (`QuestJournalScreen`): a 370 × 260 scroll texture (`quest-journal.png`, viewed:
  golden scroll with "Quest Journal" header), rows of 62 px showing name, "Given by: <name>", one
  truncated description line, and "Accepted: <date>". A **Quit** button per row sends the quit
  immediately with no confirmation; quitting removes stamped items of that quest from the
  inventory (`QuestCleanupService.java:32-43`). Mouse-wheel scrolling only; no keyboard focus
  handling; no progress, objectives, or stage numbers. Opened from `MenuScreen` ("Skills",
  "Quests"), which is bound to `O` by default (`Keybinds.java:21-26`); the key's translation
  `key.britannia_mod.open_skills` has no `en_us.json` entry, so the controls screen shows the raw
  key. No direct journal keybinding exists.
* **Trigger results**: a server-fired objective opens the dialogue screen with the NPC name
  "The Guardian" and no gender unless an escort presentation was queued
  (`ClientNetworkHandler.java:304-311`).
* **Feedback in the world**: action-bar messages in yellow/green for dirt gathering, hoeing,
  fertilizing, watering, planting refusals (`FarmingBlock`, `FarmingHoeItem`, `WateringCanItem`,
  `FarmingCultivationGate`); sounds for each success; no message at all for a wrong-hand bowl
  recipe or a stale plan.
* **Rewards**: no icons; the client sees `granted_items` only as inventory changes; the
  achievement toast says "Achievement Unlocked!" plus the slug titleized.
* **Localization**: `en_us.json` is the only language file; silver and copper coins have no
  display names (models and textures exist) and show as raw keys.

## 9. Verification evidence

### 9.1 Mod JUnit (observed)

Invocation (worktree, installed Gradle 8.9, JDK 21.0.9, `--gradle-user-home C:/Users/dusti/.gradle`):

```
gradle.bat test --no-configuration-cache --console=plain \
  --tests com.seggellion.britannia_mod.quest.* \
  --tests com.seggellion.britannia_mod.dialogue.* \
  --tests com.seggellion.britannia_mod.service.ServiceDialogueControllerTest \
  --tests com.seggellion.britannia_mod.event.BootstrapRequestTrackerTest \
  --tests com.seggellion.britannia_mod.bowlpreparation.* \
  --tests com.seggellion.britannia_mod.dirtgathering.* \
  --tests com.seggellion.britannia_mod.wildresource.* \
  --tests com.seggellion.britannia_mod.farming.* \
  --tests com.seggellion.britannia_mod.patch18.* \
  --tests com.seggellion.britannia_mod.merchant.MerchantTypesFarmerTest \
  --tests com.seggellion.britannia_mod.network.payload.MerchantSpawnConfigPayloadFarmerTest \
  --tests com.seggellion.britannia_mod.util.WaterSourceAccessPolicyTest
```

Result counted from `build/test-results/test/*.xml`: **54 suites, 264 tests, 0 failures, 0 errors,
6 skipped**, `BUILD SUCCESSFUL in 3m 31s`.

| Package | Tests | Skipped |
| --- | ---: | ---: |
| `farming.*` | 155 | 6 |
| `wildresource.*` | 46 | 0 |
| `dirtgathering.*` | 15 | 0 |
| `bowlpreparation.*` | 13 | 0 |
| `quest.*` | 8 | 0 |
| `patch18.*` | 7 | 0 |
| `merchant`, `service` | 6 + 6 | 0 |
| `dialogue`, `event`, `network.payload`, `util` | 2 each | 0 |

The six skips are the same assumption skips the earlier checklist reported
(`FarmingSkillRequirementTest` ×3, `FarmingSkillProgressionCloseoutTest` ×1,
`FlowerAssetContractTest` ×2): they need branch-excluded proposal documents. What this proves:
bowl plan/commit, dirt policy and cooldown, dung scheduling and harvest policy, crop registry
contracts, quest payload shape/security and journal parsing. What it does not prove: real packet
dispatch, item acquisition, skill readiness, Rails round trips, or any client interaction.

### 9.2 Rails (observed, isolated)

A disposable database `ultimacraft_test-99` was created as role `ultimacraft_codex_test`
(the only role with a trusted local login), the schema loaded, the selection run single-process,
and the database dropped afterwards. No development or production database was touched.

```
createdb -U ultimacraft_codex_test -O ultimacraft_codex_test "ultimacraft_test-99"
DB_USERNAME=ultimacraft_codex_test PARALLEL_WORKERS=1 SKIP_PERSISTED_SETTINGS=1 RAILS_ENV=test \
  TEST_DATABASE_URL="postgresql:///ultimacraft_test-99?username=ultimacraft_codex_test" \
  rbenv exec ruby bin/rails db:schema:load
rbenv exec ruby bin/rails test test/controllers/api/quest_states_controller_test.rb \
  test/controllers/api/quest_completion_idempotency_test.rb test/controllers/api/quest_completion_identity_test.rb \
  test/controllers/api/quest_journal_endpoint_test.rb test/controllers/api/quest_multiplayer_isolation_test.rb \
  test/controllers/api/quest_observability_test.rb test/controllers/api/quest_achievement_attribution_test.rb \
  test/services/quest_engine/completion_concurrency_test.rb test/services/achievements/award_quest_achievement_test.rb \
  test/integration/farmer_vendor_seed_test.rb
dropdb -U ultimacraft_codex_test "ultimacraft_test-99"
```

Result: **58 runs, 405 assertions, 0 failures, 0 errors, 0 skips** (10.1 s). Per file: 7, 5, 8,
6, 6, 5, 3, 2, 7, 9. These exercise real HTTP with shard headers, replay, identity spellings,
multiplayer isolation, achievement attribution, concurrency, and the farmer vendor seed. They do
not exercise the NeoForge side or any farming objective.

### 9.3 Not executed

GameTests (`runGameTestServer`) were inspected, not run. No client or dedicated server was
launched; no live shard, production catalogue, portrait bucket, or player skill state was queried.

### 9.4 Where existing fixtures bypass the real path

* `Patch18FullLoopGameTests` (67-130) harvests dung with `gameMode.destroyBlock` in Survival,
  gives tools directly, calls the fertilizer's `useOn` without stack dispatch, and creates mature
  crops through `plantMigratedCrop`; it never plants a seed through the block callback.
* `QuestLifecycleGameTests`/`QuestJournalRefreshGameTests` seed `ServerQuestTable` directly and
  swap the journal fetcher; no Rails is involved.
* Rails tests build quests with inline metadata and post with the shard secret; none seeds
  through a rake task or drives the admin form.
* GameTest players cannot be operators without writing the op-list entry manually (see the
  repository's GameTest notes), so permission bypasses in `FarmingCultivationGate` are
  unrepresentable by the normal API.

## 10. Classified findings

### 10.1 Confirmed defects (code reading; none reproduced live)

| Id | Defect | Where | Impact on the questline |
| --- | --- | --- | --- |
| D1 | Every item granted by `QuestRewardService` is stamped with the granting quest's ids and is deleted from the player's inventory, armour, and off hand at the next successful login bootstrap once that quest is no longer active (completed or quit) | `QuestRewardService.java:79-103` stamps; `WorldBootstrapHandler.java:143` → `QuestCleanupService.cleanupStaleLocalQuestState` (45-56) → `removeStaleQuestItems` (139-141) with `hasReliableQuestItemMetadata` (234-236) and `ActiveQuestIds.matches` (322-331). Nothing strips the stamp (searched). | The shovel, bowls, bucket, watering can, seed, hoe, and every coin reward would vanish on the first relog after each stage completes. Also affects escort silver today. Blocking. |
| D2 | Items granted on a non-ending transition are not replay-protected: the stored result exists only for endings, so a lost response followed by the mod's single retry gets "Invalid choice." and the items are gone | `processor.rb:138-145`, `quest_states_controller.rb:388-394`; mod retry `QuestProxyService.java:181-186` | The on-acceptance shovel (stage 1) and any mid-stage grant can be lost on a timeout. Blocking for "durable delivery once". |
| D3 | The spawner configuration payload is applied server-side without any permission check | `NetworkHandler.java:303-316` | Any client can reconfigure or relocate quest givers; must be closed before Rowan ships. |
| D4 | Server-fired objective results present the NPC as "The Guardian" with a generic portrait | `ClientNetworkHandler.java:304-311` | Every farming objective popup would be misattributed. |
| D5 | `silver_coin` and `copper_coin` have no display names | `en_us.json` (only `gold_coin` at 287) | Stage 2 and 3 rewards show raw registry keys. |
| D6 | Fertilized empty community plot expiry depends on a random tick and planting ignores the deadline; no refund on reclaim | `FarmingBlock.java:352-355`, `FarmingBlockEntity.java:385-391`, `tryPlantSeed` | Inconsistent enforcement and silent fertilizer loss. |
| D7 | Dung harvest does not recheck support | `WildResourceInteractionHandler.java:76-86`, `WildResourceHarvestService.java:21-49` | Negligible for the quest; documented. |
| D8 | `key.britannia_mod.open_skills` has no translation | `Keybinds.java:21-26`, `en_us.json` | Controls screen and any "press <key>" instruction show a raw key name. |
| D9 | The admin quest form cannot set `description` or `active` | `admin/quests_controller.rb:55-63`, `_form.html.erb` | Journal brief text and publishing need seeds or console; "Live Stats" page is a "Hello world." stub. |
| D10 | Public `resources :quests`/`nodes` routes have no controllers | `config/routes.rb:193-195` | Dead routes; unrelated to play but confusing for authors. |
| D11 | Skill data state is never re-fetched after a failed login load | `SkillManager.java:233-278,311-316` | A player whose login raced a Rails hiccup cannot plant until relog. |
| D12 | Full node metadata (including observer volumes) is forwarded to the client in action results | `QuestProxyService.java:167`, `format_active_state` | Contradicts the journal codec's intent; informational for this project. |

### 10.2 Missing capabilities

M1 farming action triggers in the engine (serializer, `QuestObjectiveTriggers`, watcher entry
point) and the five mod hook events (dirt gather, bucket fill, mixing, plant, harvest; dung
already has an event); M2 planter attribution on plots; M3 stage/progress/next-action fields in
the journal transport and screen; M4 random reward selection persisted per player (only needed if
the seed pool exceeds one crop); M5 Rowan archetype, outfit key, role label; M6
completion-time advancement grant plus `advancement/quest/first_harvest.json`; M7 per-instance
directions; M8 a generic progress counter endpoint (only if any stage needs a count above one).

### 10.3 Content and configuration work

Rails seed and rake task for five quests keyed `origin_npc = "Rowan"` with deterministic choice
keys; portrait PNG in the GCS bucket; lang entries (coins, keybinding, new UI strings); an
advancement JSON; operator-placed infrastructure (dung-eligible exposed dirt, a Water Well,
Community Farm Blocks) near every Rowan; `db:seed:uo_skills` having been run on the shard.

### 10.4 Deliberate design choices (not defects)

The bucket is incidental and stays with the player after stage 3 (no take-item effect exists and
none is proposed); public infrastructure is operator-provided; five separate quests rather than
one; carrot is the only currently eligible seed; the questline is one-time per player per shard;
no Survival switch; no confiscation of dung, dirt, seed, or fertilizer at any turn-in.

### 10.5 Unverified runtime behaviour

Live shard configuration (random tick speed, spawn protection, region climate), whether
`db:seed:uo_skills` and `db:seed:farmer_vendor` have run in production, the portrait bucket
contents, actual dung placement in the chosen area, the real Rails timeout behaviour under load,
and the entire client walkthrough. The earlier reports' verdicts about acquisition blockers remain
true for a player without Rowan and are resolved by design here (the questline issues the kit).
