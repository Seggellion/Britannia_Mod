# UltimaCraft Guildmaster Service NPC — Milestone 0 Report

Repository Reconnaissance and Baseline. **No functional changes were made.** Every claim below is
backed by a file path and, where it matters, a line number or quoted symbol.

---

## 1. Branch / worktree setup

| Item | Value |
|---|---|
| Mod repo | `C:\projects\britannia\mod\Britannia_Mod` (owner's primary tree, branch `patch-18`) |
| Feature branch | `feature/guildmaster-service-npc` (new, created from `patch-18` HEAD) |
| Feature worktree | `C:\projects\britannia\mod\britannia_mod_guildmaster` |
| Conflicts | None. No pre-existing branch or worktree matched either name. |

Verification before creating anything (`git worktree list`):

```
C:/projects/britannia/mod/Britannia_Mod                  50061f07 [patch-18]
C:/projects/britannia/mod/Britannia_Mod-house-farm-plot  72c0c8bd [feature/house-farm-plot-farming]
C:/projects/britannia/mod/Britannia_Mod-integration      50061f07 [patch-18-network-integration]
C:/projects/britannia/mod/Britannia_Mod-troubleshooting  50061f07 [patch-18-troubleshooting]
```

The owner's `patch-18` tree was not touched. Three other agent worktrees exist and were not touched.

**Naming note.** The playbook recommended `../britannia_mod_guildmaster`, which I used verbatim.
The local on-disk convention for every other worktree is `Britannia_Mod-<feature>`. If the owner
prefers consistency, renaming to `Britannia_Mod-guildmaster` is a one-command change
(`git worktree move`).

### Rails repository — located, not on the Windows filesystem

There is **no Rails checkout anywhere under `C:\projects`**. Per `docs/known_environment_baseline.md`
§1.2, the Rails repo is a native-LF Linux repository accessed through WSL. Found and verified:

| Item | Value |
|---|---|
| Rails repo | `\\wsl$\...\home\dusti\ultimacraft-website` (access via `wsl.exe -e bash -lc '...'`) |
| Branch | `banking` |
| HEAD | `e584f27e24db60687ca71a68e304debeb45010e1` — *test: stop the non-transactional suites leaking state into later runs* |
| Working tree | **Dirty** — 4 modified + 5 untracked files (admin spawn-point retirement work). Not mine; left alone. |

Rails could not be booted for live DB queries (`bin/rails runner` fails on a bundler/boot error in
this environment), so every Rails claim below comes from source, schema, and tests — not from
querying data.

---

## 2. Starting commit / repository status

**Mod baseline commit:** `50061f07231271e67591b2720ddf21cf4fa54fcc`
— *Fix server tick crash: NPC lifecycle sync without Rails credentials*

Feature worktree `git status --short`:

```
?? UltimaCraft_Guildmaster_Service_NPC_Design.md
?? UltimaCraft_Guildmaster_Service_NPC_Initial_Prompt.md
?? UltimaCraft_Guildmaster_Service_NPC_Playbook.md
```

(The three spec documents, copied in for reference. Nothing staged, nothing committed.)

**Important caveat about `docs/`.** The Service NPC and banking design documents
(`docs/service_npc_registry_bootstrap.md`, `docs/service_npc_spawn_persistence_lifecycle.md`,
`docs/ultimacraft_banking_service_npc_compatibility_map.md`, and the 20+ `UltimaCraft_Bank_*.md`
milestone reports) are **staged but never committed** in the `Britannia_Mod-troubleshooting`
worktree. They are *not* in `patch-18`, so they do not exist in this feature worktree. I read them
in place from the troubleshooting tree as evidence and deliberately did not copy them onto this
branch.

---

## 3. Minecraft Service NPC architecture

### 3.1 The entity

`src/main/java/com/seggellion/britannia_mod/entity/ServiceNpcEntity.java`

```java
public class ServiceNpcEntity extends CitizenEntity      // line 42
```

It is a **sibling** of `QuestGiverEntity`, not a base class, and has **no subclasses**. There is
exactly one registered entity type:

`registry/EntityRegistry.java:131`
```java
public static final DeferredHolder<EntityType<?>, EntityType<ServiceNpcEntity>> SERVICE_NPC =
    ENTITIES.register("service_npc", ... .build("britannia_mod:service_npc"));
```

Persistent state (NBT, `addAdditionalSaveData`/`readAdditionalSaveData`, lines 222–251):

| Field | NBT key | Type |
|---|---|---|
| `spawnPointId` | `SpawnPointId` | UUID |
| `assignmentPublicId` | `AssignmentPublicId` | UUID |
| `serviceNpcTypeKey` | `ServiceNpcTypeKey` | String |
| `definitionRevision` | `DefinitionRevision` | long |
| `assignmentRevision` | `AssignmentRevision` | long |
| home post | `HomePost` / `HomePostRadius` | BlockPos / int |

Inherited from `CitizenEntity` (`entity/CitizenEntity.java`): `worldNpcPublicId` (NBT),
`personalName` / `gender` / `cityName` / clothing indices (all `SynchedEntityData`, lines 98–123).

`isPersistenceRequired()` is overridden to `true` (line 166) — Service NPCs never despawn.

### 3.2 The service discriminator — **it is a Rails-published string, not a mod-side enum**

This is the single most important architectural fact for this feature.

`ServiceNpcEntity.serviceNpcTypeKey` is a plain `String` (e.g. `"bank_teller"`). Its meaning comes
from `service/ServiceNpcTypeDefinition.java`:

```java
public record ServiceNpcTypeDefinition(
        String key, String displayName, String professionKey,
        String minecraftEntityTypeKey, String defaultDialogueKey,
        List<String> allowedServiceKeys, boolean active, boolean spawnable,
        long definitionRevision) {}
```

These records are **not defined in Java**. They are parsed out of the Rails world-bootstrap
response by `service/ServiceNpcRegistryParser.java` and held in
`service/ServiceNpcRegistryCache.java` (a static `AtomicReference<ServiceNpcRegistrySnapshot>`,
server-side only), populated by `event/WorldBootstrapHandler.java:75`:

```java
ServiceNpcRegistryCache.replace(data.serviceNpcRegistry());
```

**There is no `ServiceNpcKind` enum to extend.** Design §5.2's suggested
`ServiceNpcKind.BANKER / GUILDMASTER` does not exist and should not be created — the existing
abstraction is the Rails `service_npc_types` registry plus the `allowed_service_keys` capability
list.

### 3.3 The capability gate — closed allow-lists in **three** places

A Service NPC's behaviour is gated on capability, not on type key. `service/banking/BankingCapability.java`:

```java
static final String BANK_OPEN_SERVICE_KEY = "bank.open";
public static boolean supportsBankOpen(@Nullable String serviceNpcTypeKey) {
    ServiceNpcTypeDefinition definition =
        ServiceNpcRegistryCache.snapshot().serviceNpcTypes().get(serviceNpcTypeKey);
    return definition != null && definition.active()
        && definition.allowedServiceKeys().contains(BANK_OPEN_SERVICE_KEY);
}
```

Adding a `guild.train` service key requires editing a closed set in **all three** of these, or the
registry is rejected wholesale:

1. `service/ServiceNpcRegistryParser.java:21`
   `private static final Set<String> SUPPORTED_SERVICE_KEYS = Set.of("bank.open", "bank.create_check");`
2. `service/ServiceActionDispatcher.java:11`
   `private static final Set<String> SUPPORTED_SERVICES = Set.of("bank.open", "bank.create_check");`
3. Rails `app/models/service_npc/action_registry.rb` — `ACTIONS` hash, same two keys.

Note the failure mode: `parseNpcTypes` throws `invalid("Service NPC type ... allows unavailable
service ...")` for an unknown key, and `parseBootstrapRoot` catches it and returns
`ParseResult.rejected(...)` → the **entire Service NPC registry becomes the empty snapshot**. An
older mod build talking to a Rails server that has published `guild.train` loses bankers too. This
is a hard version-coupling constraint (see §12).

### 3.4 Interaction routing

`ServiceNpcEntity.interactAt` (line 144) — server-authoritative, no client round-trip:

```java
if (hand == InteractionHand.MAIN_HAND && !level().isClientSide
        && player instanceof ServerPlayer serverPlayer
        && BankingCapability.supportsBankOpen(this.getServiceNpcTypeKey())) {
    ServiceActionDispatcher.dispatchBankOpen(serverPlayer, this);
    return InteractionResult.sidedSuccess(false);
}
return super.interactAt(player, hit, hand);
```

This is exactly the seam a Guildmaster hooks into. `ServiceActionDispatcher.dispatch(...)` (the
dialogue-menu path) is a **stub** — every service key returns `SERVICE_NOT_AVAILABLE`. The live
path is `dispatchBankOpen` → `BankingProxyService.handle`.

### 3.5 Random NPC naming — **it happens in Rails, not the mod**

`service/spawn/ServiceNpcAssignmentReconciler.java:185–187`:

```java
entity.setWorldNpcPublicId(worldNpc.publicId());
entity.setPersonalName(worldNpc.name());
entity.setGender(worldNpc.genderKey());
```

`worldNpc` is a `ServiceNpcAssignmentWorldNpcDefinition` from the Rails bootstrap. The name is
picked by `app/services/world_npcs/create.rb`:

```ruby
active_catalog = NpcName.active.where(source_key: NpcName::SOURCE_KEY)
candidates     = gender ? active_catalog.where(gender_key: gender) : active_catalog
selected_name  = candidates.order(:id).offset(@random.rand(candidate_count)).first!
```

The mod *does* have a legacy name generator (`util/NameLoader.java` +
`assets/britannia_mod/uo_names.xml`), used by other NPC families — but **not** by Service NPCs.
A Guildmaster gets its random personal name for free, with zero mod changes, as long as it goes
through the normal World NPC / assignment pipeline.

### 3.6 Role title — currently dead code for Service NPCs

`CitizenEntity.getRoleTitle()` returns `"Citizen"`; `ServiceNpcEntity` overrides it to
`"Service NPC"` (line 69). But `getRoleTitle()` is only *consumed* by:

- `TownPersonEntity.java:91`, `AlcoholTraderEntity.java:100`, `AbstractTraderEntity.java:119` —
  each overrides `updateDisplayName()` to `setCustomName(personalName + " the " + roleTitle)`
- `AbstractEconomyMerchantEntity.java:61`

`ServiceNpcEntity` does **not** override `updateDisplayName()`, so it inherits
`CitizenEntity.updateDisplayName()` (line 292), which sets the custom name to the **personal name
only**. A bank teller today renders as `Aldric`, never `Aldric the Bank Teller`.

Consequence: `{Skill} Guildmaster` display (Design §4.1, Owner Requirement 3) is **new work** — the
pattern exists (`personalName + " the " + roleTitle`) but Service NPCs don't use it yet. Also note
`serviceNpcTypeKey` is **not** `SynchedEntityData`, so the client cannot currently derive a role
title itself; either sync it or compute the name server-side (the latter matches
`updateDisplayName()`, which already writes into synced custom-name state).

---

## 4. Bank Teller UI / network architecture

### 4.1 Server flow

`service/banking/BankingProxyService.java` is the template to copy structurally:

```
ServiceNpcEntity.interactAt
  → ServiceActionDispatcher.dispatchBankOpen
    → BankingProxyService.handle(player, entity)
        resolve()                       // fresh revalidation, returns null to no-op
        IN_FLIGHT.add(playerUuid)       // per-player dedup, ConcurrentHashMap.newKeySet()
        client.submit(server, request)  // ServerHttpExecutor → off-tick HTTP
        .whenComplete(... server.execute(...))   // back onto the main thread
           re-resolve entity by UUID, re-check isAlive + distance
           applyResult(...)
    → BankAccountOpenedS2CPayload.send(player, teller, account, bankItems, refresh)
```

`resolve()` (line 213) is the reusable validation shape:

```java
if (player.level().isClientSide) return null;
if (!entity.isAlive()) return null;
if (player.distanceToSqr(entity) > MAX_INTERACTION_DISTANCE_SQR) return null;   // 64.0 == 8 blocks
if (entity.getWorldNpcPublicId() == null) return null;
if (!BankingCapability.supportsBankOpen(entity.getServiceNpcTypeKey())) return null;
```

Rails endpoints used (`server/http/RailsApiUrlResolver.Endpoint`): `BANKING_OPEN`,
`BANKING_*_PREPARE`, `BANKING_CONFIRM`, `BANKING_CANCEL` — a genuine two-phase commit with
`BankTransferReconciliationService` reconciling stranded operations at startup.

### 4.2 Client screens

`client/screen/` — `BankMainScreen`, `BankBalanceScreen`, `BankBoxScreen`, `BankChequeIssuanceScreen`.
All plain `Screen`s (**not** `AbstractContainerMenu`), marked by the empty interface
`client/screen/bank/BankingScreen.java`.

### 4.3 Reuse verdict — which Bank components are neutral

**Safe to reuse as a Service NPC shell (zero banking coupling):**

| Class | Why |
|---|---|
| `client/screen/bank/BankDialogueLayout.java` | Pure geometry `record`. No banking fields. Already JUnit-tested (`BankDialogueLayoutTest`). Solves the negative-wrap-width bug that `DialogueLayout` still has. |
| `client/screen/bank/BankDialogueFrame.java` | Draws parchment + portrait + name + body from `Component` args. Takes `tellerName` as a `String` — rename or pass a Guildmaster's name; no bank data crosses it. |
| `client/screen/bank/BankActionButton.java` | `Button` + idle/pending label + pending marker. Generic. |
| `client/screen/bank/BankAmountInput.java` | ASCII-digit parser returning `Long`/`TOO_LARGE`/`null`. Generic. |
| `client/screen/bank/BankStatusPresenter.java` | Status-line presentation. |
| `client/screen/DialoguePresentation.java` | Parchment texture, UO font, portrait lookup. Already shared with quest + service dialogue screens. |
| `client/renderer/PortraitDownloader.java` | Portrait by NPC name. |

**Do NOT inherit or copy (banking-specific):**

`BankNavigation` (three fixed bank destinations, refresh-route rules), `ClientBankingSession`
(account/balance/weight state), `BankBoxLayout` / `BankBoxSelection` / `BankGridGeometry` /
`BankGridPager` / `BankGridScroll` / `BankDragController` / `BankItemIcon` (bank-box item grid),
`BankChequeForm` / `BankChequeTint` / `BankChequeDoubleClick`, `BankCurrencyButton` /
`BankCurrencyWithdrawalForm`, `BankBalanceAbbreviation` / `BankBalanceCopy`, `BankDepositHint`,
`BankMutationCue`, and the `BankingScreen` marker interface itself.

Recommended shape: **do not subclass any bank screen.** Write `GuildmasterTrainingScreen extends
Screen`, compose `BankDialogueLayout` + `BankDialogueFrame` + `BankActionButton` +
`BankAmountInput` exactly as `BankMainScreen` does. Consider renaming the four neutral classes into
a `client/screen/service/` package in a later milestone — **not** in this feature, since that
touches 19 passing bank UI tests.

---

## 5. `ServiceNpcSpawnBlock` architecture

**The block does not spawn anything.** This is the second most important finding, and it
contradicts the design document's assumption throughout §6.

`block/ServiceNpcSpawnBlock.java` + `block/entity/ServiceNpcSpawnBlockEntity.java` register a
*spawn point* with Rails. Rails then decides whether to assign an NPC to it. A separate reconciler
materialises the entity from that assignment.

```
Admin right-clicks block (requires hasPermissions(2))
  → ServiceNpcSpawnMenu opened (menu/ServiceNpcSpawnMenu.java, an AbstractContainerMenu)
  → ServiceNpcSpawnStateS2CPayload pushes { cityOptions[], serviceTypeOptions[] } to the client
  → client/screen/ServiceNpcSpawnScreen.java lets the admin pick city + service type + enabled
  → ServiceNpcSpawnConfigureC2SPayload { cityPublicId, serviceNpcTypeKey, enabled, expectedRevision }
  → ServiceNpcSpawnBlockEntity.applyConfiguration(...)   // optimistic-concurrency on revision
  → durable outbox: ServiceNpcSpawnPendingData.put(UPSERT record)
  → ServiceNpcSpawnDeliveryProcessor → POST /api/service_npc_spawn_operations
  → Rails ServiceNpcSpawnPoints::ApplyOperation creates/updates the ServiceNpcSpawnPoint row
  ── async, later ──
  → Rails CityStaffing::Reconcile creates a WorldNpc + NpcSpawnAssignment
  → world bootstrap / world_state_changes delivers the assignment to the mod
  → ServiceNpcAssignmentsCache
  → ServiceNpcSpawnBlockEntity.serverTick() → ServiceNpcAssignmentReconciler.reconcileBlock(...)
  → EntityRegistry.SERVICE_NPC.get().create(level); level.addFreshEntity(entity)
```

Block-entity persisted state (`saveAdditional`, line 380): `SpawnPointId`, `CityPublicId`,
**`ServiceNpcTypeKey`**, `Enabled`, `ConfigurationRevision`, `RegistrationState`, `LastErrorCode`,
`AssignedNpcPublicId`, `AssignedNpcDisplayName`, `AssignmentRevision`, sync timestamps, identity
origin (world/dimension/x/y/z, for copy-paste rekey detection).

Service-type options offered to the admin (`network/payload/ServiceNpcSpawnStateS2CPayload.java:97`):

```java
typeSnapshot.serviceNpcTypes().values().stream()
    .filter(ServiceNpcTypeDefinition::active)
    .filter(ServiceNpcTypeDefinition::spawnable)
    .sorted(Comparator.comparing(ServiceNpcTypeDefinition::displayName)
            .thenComparing(ServiceNpcTypeDefinition::key))
```

Note the list is **sorted deterministically** and bounded by
`ServiceNpcSpawnConfigurationValidator.MAX_TYPE_OPTIONS = 1_024` — ample headroom.

Validation: `service/spawn/ServiceNpcSpawnConfigurationValidator.validate(...)` rejects
`TYPE_UNAVAILABLE` / `TYPE_INACTIVE` / `TYPE_NOT_SPAWNABLE` / `CITY_UNAVAILABLE` /
`REGISTRY_UNAVAILABLE`. Type keys must match `^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$`, ≤ 64 bytes.

**Consequence for the design.** There is no "spawn tick that creates an entity" to gate, and no
place for a mod-side skill index to live. Design §7.3 ("do not create the entity and immediately
delete it") is already structurally impossible.

---

## 6. Canonical skill architecture and precision

### 6.1 Required proof: how is a player skill stored?

**Rails (authoritative), `db/schema.rb:1052`:**

```ruby
create_table "shard_user_skills", id: :uuid, ... do |t|
  t.bigint "shard_user_id", null: false
  t.uuid   "skill_id",      null: false
  t.float  "value",   default: 0.0, null: false
  t.boolean "powerscroll", default: false
  t.index ["shard_user_id", "skill_id"], unique: true
end
```

**Rails skill definition, `db/schema.rb:1113`:**

```ruby
create_table "skills", id: :uuid, ... do |t|
  t.string  "name",  null: false          # unique
  t.string  "slug"                        # unique, friendly_id from name
  t.float   "min_value", default: 0.0
  t.float   "max_value", default: 120.0
  t.float   "skill_difficulty_modifier", default: 1.0
  t.boolean "gain_on_success", default: true
  t.boolean "gain_on_failure", default: false
  t.boolean "published", default: false
  t.bigint  "category_id"
  # + rich-text description, image, meta fields — these are CMS pages, not seed data
end
```

**Precision is pinned to one decimal place by Rails, on both write paths**
(`app/controllers/api/skills_controller.rb`):

```ruby
new_val = permitted[:value].to_f.round(1)     # set_skill
new_val = permitted[:value].to_f.round(1)     # gain
```

**Mod (in-memory cache), `skill/SkillManager.java`:**

```java
private static class PlayerSkills {
    private final Map<String, Float> map = new HashMap<>();   // key = lowercase slug
    float get(String s) { return map.getOrDefault(s, 0f); }
}
```

Natural gain granularity is `0.1f` — `SkillManager.java:91`:
```java
float newValue = Math.min(current + 0.1f, def.max);
```

### 6.2 Answers to the mandated questions

| Question | Answer |
|---|---|
| How is one player skill stored? | `float`, keyed by lowercase skill **slug** string. Rails `shard_user_skills.value` is authoritative; the mod holds a per-login in-memory cache and pushes the client a `Map<String,Float>` snapshot. |
| Value for `0` | `0.0f` / `0.0` |
| Value for `0.1` | `0.1f` / `0.1` |
| Value for `1.0` | `1.0f` / `1.0` |
| Value for `40.0` | `40.0f` / `40.0` |
| Value for `100.0` | `100.0f` / `100.0` |
| Are fractions supported? | **Yes — to exactly one decimal place.** Rails `.round(1)` on both write endpoints is the canonical quantisation. |
| Where are per-skill caps enforced? | Mod: `SkillManager.trySkillGain`:88 and `awardSkillGain`:124, `Math.min(current + amount, def.max)` where `def.max` = Rails `skills.max_value`. Rails: `skills#set_skill` (`max_allowed = powerscroll ? 120.0 : skill_def.max_value`) and `skills#gain` (`new_val > skill_def.max_value`). |
| Where is the **total** skill cap enforced? | **Nowhere. It does not exist.** A repo-wide search of both codebases for a total/aggregate skill cap returns nothing. UO's 700-point total cap is not implemented. |

### 6.3 Mapping the UO rule — no second scale needed

The design's preferred `skillTenths = skillValue * 10` maps **exactly** onto the existing
representation, because Rails already quantises to tenths. Recommendation:

- Do all Guildmaster arithmetic in **integer tenths** (`int`), never `float`.
- Convert to `float` only at the `SkillManager` / Rails JSON boundary:
  `value = tenths / 10.0f`, and read back with `Math.round(current * 10f)`.
- `costGold == purchasedTenths`. `0.0 → 40.0` = 400 tenths = **400 gold**. ✔ matches the UO rule.
- Guildmaster ceiling constant: `GUILDMASTER_MAX_TENTHS = 400`.
- Effective cap = `min(400, round(def.max * 10))`. There is no total cap to intersect with.

The design's §14.1 pricing examples are all directly implementable as written:
`12.7 → 20.0 = 73 gold`, `39.9 → 40.0 = 1 gold`, `40.0 → any = rejected`.

### 6.4 There is **no canonical skill order** anywhere — this is a real blocker for §4.3

Evidence, three independent places:

1. `SkillManager.java:42` — `Map<String, SkillDef> SKILL_DEFS = new ConcurrentHashMap<>()`
   (unordered), populated from `HashMap` in `fetchSkillConfigAsync` (line 401). It is `private`
   with **no accessor** — nothing outside `SkillManager` can even enumerate the skill list today.
2. Rails `SkillsController#skill_config` — `skills = Skill.all` with **no `order`**. Postgres row
   order is arbitrary and changes on update.
3. `client/gui/SkillTableScreen.java:66` — iterates `ClientSkillTable.snapshot().entrySet()`, a
   `Map.copyOf(...)`. The existing in-game skill list is already displayed in unstable order.

There is also **no skill seed file** (`db/seeds/` contains cities, commodities, escorts, fish,
grapes, ore veins, regions, npc_names, service_npc_definitions — no skills). Skills are
**admin-editable database content**, with `published`, `category_id`, rich-text descriptions and
images. An admin can add, rename, or unpublish a skill at any time.

**Conclusion:** an integer index over the skill list cannot be made stable without adding an
explicit ordering column in Rails. The genuinely stable canonical identifier that already exists is
**`skills.slug`** (unique index, friendly_id-generated, and already the key the mod uses in
`PLAYER_SKILLS`, `postGain`, `SKILL_SET`, and `FarmingSkill.SKILL_ID = "farming"`).

**Slug format caution:** friendly_id parameterises the name, so a two-word skill becomes
`animal-taming` (hyphen). That does **not** match the Service NPC `DEFINITION_KEY` pattern
`[a-z][a-z0-9]*(?:_[a-z0-9]+)*`. Any place a slug is embedded in a registry key needs an explicit
`-` → `_` normalisation with a documented, validated round-trip.

### 6.5 Skill mutation APIs

| API | Location | Behaviour |
|---|---|---|
| `SkillManager.trySkillGain(player, skill, success)` | `SkillManager.java:66` | Probabilistic +0.1 use-based gain. Not usable for a purchase. |
| `SkillManager.awardSkillGain(player, skill, amount)` | `SkillManager.java:111` | **Deterministic award.** Clamps to `def.max`, returns the actual delta, messages the player, syncs the client, `postGain(...)` to Rails. Callers: `LockpickingEventHandler:111`, `FarmingSkill:41`. |
| `SkillManager.setSkillAdmin(player, skill, value)` | `SkillManager.java:283` | Absolute set. `postSetSkill(...)` → `SKILL_SET`. Caller: `commands/SetSkillCommand.java:31`. |

**`awardSkillGain` cannot be used as-is for a paid transaction.** Two reasons:

1. It mutates the local map first and then **fires and forgets** the Rails POST —
   `postGain` catches every exception and only logs `LOGGER.warn("Failed to POST skill gain to
   Rails", e)`. If Rails is down, the player is charged, sees the skill go up, and loses it at next
   login.
2. **Rails rejects the request outright for any purchase above 1.0 point.**
   `SkillsController#gain`:
   ```ruby
   bump = new_val - current_val
   return render json: { error: 'invalid gain' }, status: :unprocessable_entity if bump <= 0 || bump > 1
   ```
   A 73-gold `12.7 → 20.0` purchase is a bump of 7.3 → **422 Unprocessable Entity**.

This is the single hardest blocker uncovered in Milestone 0. See §7.3 for the resolution options.

---

## 7. Economy / payment architecture

### 7.1 Where money is authoritative — **it is split**

| Money form | Authority | Evidence |
|---|---|---|
| Coins in the player's inventory | **Minecraft** | `registry/ItemRegistry.GOLD_COIN / SILVER_COIN / COPPER_COIN`, plain `Item`s, `stacksTo(99)`, no data components. Counted and mutated purely in `MerchantEconomyService` / `ServerEconomyService`. |
| Bank account balance | **Rails** | `bank_accounts.gold_balance / silver_balance / copper_balance` (integers) + `reserved_*_balance`, `db/schema.rb:140`. |

`economy/CoinConversion.java` is the one authoritative ratio source:
`1 gold = 100 silver = 10 000 copper`; **copper is the canonical smallest unit**;
`toCoins(int totalCopper)` does a lossless greedy split; `toCopper(...)` uses `Math.multiplyExact`.
400 gold = 4 000 000 copper — comfortably inside `int`.

### 7.2 The authoritative deduction path for inventory coins

`economy/MerchantEconomyService.java`:

| Member | Visibility | Line |
|---|---|---|
| `countCoins(ServerPlayer)` → total copper | **public static** | 380 |
| `giveChange(ServerPlayer, int copper)` | **public static** | 411 |
| `reserveCoins(ServerPlayer, int totalCopper)` | private static | 360 |
| `removeAllCoins(ServerPlayer)` | private static | 391 |

The established charging idiom is `countCoins` → `removeAllCoins` → `giveChange(available - price)`.
Only the first and last are public, so a Guildmaster needs either a small extraction of
`reserveCoins`/`removeAllCoins` into a shared helper, or an equivalent alongside them. **Do not
hand-roll a second inventory coin scanner** (Design §16).

Also relevant: `MerchantEconomyService.MAX_PURCHASE_DISTANCE_SQ = 12.0² = 144` for merchants,
versus `BankingProxyService.MAX_INTERACTION_DISTANCE_SQR = 64` (8 blocks) for Service NPCs. Use the
Service NPC value.

### 7.3 Is there a transaction / ledger / audit pipeline? Yes — three of them

| Pipeline | Where | Fits a Guildmaster purchase? |
|---|---|---|
| `bank_transactions` (`db/schema.rb:235`) + `bank_transfer_operations` prepare/confirm/cancel | Rails, banking | Structurally ideal, but `transaction_type` is a DB **check constraint** listing exactly seven banking types. Not extensible without a migration, and it is bound to a `bank_account_id`. |
| `transactions` (`db/schema.rb:1291`) — `player_uuid`, `npc_id`, `npc_name`, `npc_type`, `city_id`, `shard_id`, `transaction_type`, `total_price`, **`idempotency_key`** (unique per shard), position, `server_tick` | Rails, merchant/trader economy | **Closest existing generic ledger.** But `Api::TransactionsController#create` only accepts `transaction_type == "purchase"` (requires `transaction_items` + city commodities) or `"sell"` (routes to `Economy::SaleTransactionProcessor`). A service fee fits neither branch today. |
| `admin_action_audits` (`db/schema.rb:101`) | Rails, admin actions only | Not a player-transaction ledger. |

### 7.4 What API must a Guildmaster purchase use so payment + skill gain can be atomic?

**There is no existing API that gives true atomicity, because the two halves live on different
authorities:** the coins are Minecraft state, the skill is Rails state.

The project has already solved exactly this problem once, for banking: a Rails
`bank_transfer_operations` row with `prepare` → `confirm` / `cancel`, plus
`service/banking/BankTransferReconciliationService` reconciling stranded operations at server
startup. That is the honest answer for a paid skill purchase, and I recommend mirroring it:

```
POST /api/guild_training/prepare  { player_uuid, shard, world_npc_public_id, skill_slug,
                                    requested_target_tenths, idempotency_key }
  → Rails re-derives current value, effective cap, purchasable tenths, price in gold
  → creates a pending GuildTrainingOperation, returns the authoritative quote
mod: revalidate teller + distance + funds, deduct coins, give change
POST /api/guild_training/confirm  { operation_id }
  → Rails writes shard_user_skills.value and a ledger row in one transaction
  → on any local failure instead: POST /api/guild_training/cancel
startup: reconcile any operation left prepared-but-unconfirmed
```

Two cheaper alternatives, both with named costs — **owner decision required**:

- **(B) Reuse `skills/set`.** `SkillsController#set_skill` has no `bump > 1` restriction and only
  checks the cap. Fastest path, no migration. Costs: it is the admin/override endpoint (`SetSkillCommand`
  is its only current caller), it takes an absolute value from the caller with no server-side
  re-derivation of "what were they before", it writes no ledger row, and it has no idempotency key —
  a retried request could double-apply if the mod's local value were stale.
- **(C) Pay from the bank balance instead of inventory coins.** Both money and skill become Rails
  state, so a single Rails DB transaction is genuinely atomic and the whole prepare/confirm apparatus
  becomes unnecessary. Costs: diverges from UO (which charges the backpack) and from every other NPC
  in this mod (merchants and traders all charge inventory coins), and requires the player to be
  carrying no gold at all.

My recommendation is **(A)**, with **(C)** as a strong second if the owner is willing to accept
bank-funded training — it is by far the smallest correct implementation.

---

## 8. Rails Service NPC architecture

### 8.1 Models

| Model | File | Key fields |
|---|---|---|
| `ServiceNpcType` | `app/models/service_npc_type.rb` | `key` (unique), `display_name`, `profession_key`, `minecraft_entity_type_key`, `default_service_dialogue_set`, `allowed_service_keys` (array), `active`, `spawnable`, `definition_revision` |
| `ServiceNpcSpawnPoint` | `app/models/service_npc_spawn_point.rb` | `public_id` (unique, **the stable identity**), `shard`, `minecraft_server`, `city`, `service_npc_type`, `status` (`registered`/`removed`), `enabled`, `world_name`, `dimension_key`, `x/y/z`, `source_revision`, `last_operation_id`. `attr_readonly :public_id, :shard_id, :minecraft_server_id` |
| `WorldNpc` | `app/models/world_npc.rb` | `public_id`, `shard`, `city`, `service_npc_type` (optional), `name`, `normalized_name`, `gender_key`, `profession_key`, `status` (`active`/`retired`), `definition_revision`. Identity fields are `attr_readonly` and validated immutable on update. |
| `NpcSpawnAssignment` | `app/models/npc_spawn_assignment.rb` | `public_id`, `world_npc`, `spawn_point`, `status` (`active`/`closed`), `revision`, `reason`. Unique partial index: **one active assignment per spawn point**. |
| `ServiceNpc::ActionRegistry` | `app/models/service_npc/action_registry.rb` | Frozen `ACTIONS` hash — `bank.open`, `bank.create_check` |

`NpcSpawnAssignment#assignment_is_compatible` enforces: world NPC active, spawn point registered +
non-removed + enabled, same shard, same city, and
`service_type.profession_key == world_npc.profession_key`.

### 8.2 Service-type representation and the create/update/upsert path

- Spawn points: `POST /api/service_npc_spawn_operations` →
  `Api::ServiceNpcSpawnOperationsController#create` → `ServiceNpcSpawnPoints::RequestEnvelope.parse`
  → `ServiceNpcSpawnPoints::ApplyOperation`. Idempotent by `operation_id` + revision-guarded by
  `source_revision`. Outcomes include `UUID_COLLISION`, which drives the mod's
  `ServiceNpcSpawnCollisionRepairCoordinator`.
- Registry publication: `app/serializers/service_npc_registry_serializer.rb` →
  `world_bootstrap` response member `service_npc_registry`. Types are `order(:key)`, dialogues
  `order(:key)`, and `revision` is a SHA-256 digest of the payload.
- Assignments publication: `app/serializers/service_npc_assignments_serializer.rb` →
  `service_npc_assignments`. World NPCs are **derived strictly from assignments** — an unassigned
  World NPC is never published.

### 8.3 `/api/world_bootstrap` and delta sync

`app/controllers/api/world_bootstrap_controller.rb` — `GET /api/world_bootstrap/:shard`, query
params `player_uuid`, `minecraft_uuid`, `minecraft_username`, `profile`. Dedicated servers request
`profile=minecraft_server`. ETag combines the registry and assignments revisions. Body ≤ 4 MiB;
5 s connect / 10 s read / 15 s overall on the mod side.

Deltas: `GET /api/world_state_changes/:shard?from_version=N` →
`worldstate/WorldStateSyncPoller.java` → `ServiceNpcAssignmentsCache`, with
`WorldStateFullBootstrapFallback` when the delta stream cannot be applied.

**This is the established "cached Rails config, never a per-tick request" mechanism the design asks
for in §7.4 — it already exists and already carries Service NPC data.**

### 8.4 Smallest backward-compatible representation of Guildmaster + taught skill

Recommended (see §10 for the full argument):

1. `ServiceNpc::ActionRegistry::ACTIONS` gains `"guild.train"`.
2. `service_npc_types` gains **one nullable column**: `taught_skill_id` (uuid, FK → `skills`),
   `null: true`. Existing `bank_teller` rows are untouched and stay valid.
3. `ServiceNpcRegistrySerializer#serialize_npc_type` gains one nullable member:
   `taught_skill_slug: npc_type.taught_skill&.slug`.
4. Mod: `ServiceNpcTypeDefinition` gains `@Nullable String taughtSkillSlug`; the parser reads it as
   **optional** (absent → `null`), so an older Rails server still parses cleanly.
5. Validation: a type whose `allowed_service_keys` include `guild.train` **must** have a
   `taught_skill_id`, and vice versa. Enforce on both sides.

No change to `service_npc_spawn_points`, no change to the spawn wire protocol, no change to
`ServiceNpcSpawnConfigureC2SPayload`, no change to the assignments serializer.

---

## 9. Existing spawn / economic policy architecture

**The lever already exists and is entirely Rails-side.** `app/services/city_staffing/`:

| File | Role |
|---|---|
| `city_staffing.rb` | `BANKER_PROFESSION_KEY = "banker"` |
| `desired_staffing.rb` | The formula. `BASELINE = 1`, `RESIDENTS_PER_INCREMENT = 10`, `MAX_CITY_TOTAL = 6`. `target = min(1 + floor(eligible_non_banker_residents / 10), 6, actual_combined_capacity)` |
| `spawn_capacity.rb` | `MAX_ASSIGNMENTS_PER_SPAWN_BLOCK = 1`. Capacity = count of `ServiceNpcSpawnPoint` where `city`, `status: "registered"`, `enabled: true`, joined to a `service_npc_type` with the target `profession_key` |
| `plan.rb` | Pure computation: keep / close / reuse / generate, with deterministic ordering and stability policy |
| `apply_plan.rb` | Applies it: `NpcSpawnAssignments::Close`, `NpcSpawnAssignments::Create`, `WorldNpcs::Create` |
| `reconcile.rb` | `City.transaction { city.lock!; plan = Plan.call(city:); ApplyPlan.call(plan:); AdminActionAudit.record!(...) }` |

Triggered by `Admin::CityStaffingReconciliationsController#create` → `CityStaffingReconciliationJob`.
It is **admin-triggered, not automatic** — a newly configured spawn block does not get an NPC until
a reconciliation runs for that city.

Mapping the design's §7.2 lever list onto what already exists:

| Design lever | Existing mechanism | Gap |
|---|---|---|
| Globally enabled/disabled | `service_npc_types.active` / `.spawnable` | None |
| Per city/region enabled/disabled | Spawn point's `city` + `enabled` | None |
| Allowed/denied skills | Per-skill `ServiceNpcType` row's `active` flag (under Option A) | None |
| Total population cap | `DesiredStaffing::MAX_CITY_TOTAL` | Per-city, not global. Needs a new constant if a global cap is wanted. |
| Per-city/region cap | `DesiredStaffing::MAX_CITY_TOTAL` | Currently one hardcoded number for all cities |
| Per-skill population cap | Falls out for free if `CityStaffing` is parameterised by profession | Requires the refactor below |
| Existing economy constraints | `cities.population`, `is_starving`, `gold_supply` / `silver_supply` / `copper_supply`, `food_supply`, `consumption_rates` — all present but **unused by staffing today** | Optional richer formula |

**The required work is a parameterisation, not a new system:** replace the hardcoded
`BANKER_PROFESSION_KEY` with a per-profession staffing configuration (baseline, residents-per-
increment, max-per-city), iterate professions in `Reconcile`, and scope
`eligible_non_banker_resident_count` accordingly. Everything else — capacity, locking, planning,
auditing — works unchanged.

**The mod side needs no new spawn-policy code at all.** `ServiceNpcAssignmentReconciler` already
reads only from `ServiceNpcAssignmentsCache` and creates exactly one entity iff an active assignment
exists. Design §7.1's proposed `ServiceNpcSpawnPolicy` seam should **not** be built — the policy
decision is made in Rails and arrives as data.

---

## 10. Recommended implementation shape

### Core decision: one `ServiceNpcType` per taught skill

**Recommended — Option A.** Seed one `ServiceNpcType` per guild-trainable skill:

```
key:                       mining_guildmaster
display_name:              Mining Guildmaster
profession_key:            mining_guildmaster
minecraft_entity_type_key: britannia_mod:service_npc
allowed_service_keys:      ["guild.train"]
taught_skill_id:           <skills.id for slug "mining">      # new nullable column
active / spawnable:        true
```

What this buys, all through mechanisms that already work:

- **Admin selection** — the existing spawn-block screen already lists every active + spawnable type,
  sorted by display name then key. Zero changes to the block, block entity, menu, screen, C2S/S2C
  payloads, wire protocol, or Rails spawn-point schema.
- **Persistence** — the taught skill rides on the already-persisted `ServiceNpcTypeKey` NBT tag on
  both the block entity and the entity. Registry reordering cannot retarget an existing Guildmaster,
  because there is no ordering involved.
- **Per-skill population caps, per-city enable/disable, global enable/disable, allowed/denied
  skills** — all fall directly out of `CityStaffing` once it is parameterised by profession, plus the
  `active`/`spawnable` flags.
- **Random personal name** — free, via `WorldNpcs::Create`.
- **`{Skill} Guildmaster` title** — free from `display_name`; only the display wiring (§3.6) is new.

Cost: N registry rows and N dialogue sets (or one parameterised set reused across types — the
`%{profession_name}` interpolation token already exists in the parser).

**Option B** (a single `guildmaster` type plus a taught-skill field on the spawn point) matches the
design document's "skill index" wording more literally but requires: a `service_npc_spawn_points`
migration, a spawn-protocol envelope schema bump, a new `ServiceNpcSpawnConfigureC2SPayload` field,
a new S2C option list + screen control, new assignment compatibility rules, an entity sync field —
and it gives you *less* policy leverage, because `CityStaffing` keys off `profession_key`.

**This is the one decision I need the owner to confirm before Milestone 1**, because it changes
roughly two-thirds of the implementation.

### Proposed layering (assuming Option A)

```
Service NPC domain (mostly unchanged)
├── ServiceNpcEntity                          — + updateDisplayName() override for the role title
├── ServiceNpcTypeDefinition                  — + @Nullable taughtSkillSlug
├── ServiceNpcRegistryParser                  — + "guild.train", + optional taught_skill_slug
└── service/guild/GuildmasterCapability       — new, mirrors BankingCapability exactly

Skill domain
├── skill/SkillManager                        — + a public read of SkillDef (slug, displayName, max)
└── skill/GuildTrainingMath                   — new, pure integer-tenths quote arithmetic, JUnit-tested

Economy domain
├── economy/CoinConversion                    — unchanged, reused
├── economy/MerchantEconomyService            — extract a reusable "charge N copper, give change"
└── service/guild/GuildmasterTrainingService  — new, mirrors BankingProxyService

UI / network
├── client/screen/GuildmasterTrainingScreen   — new Screen, composes the four neutral bank components
├── GuildTrainingOpenS2CPayload               — new
├── GuildTrainingRequestC2SPayload            — new: { entityId, requestedTargetTenths } only
└── GuildTrainingResultS2CPayload             — new

Rails
├── ServiceNpc::ActionRegistry                — + "guild.train"
├── migration                                 — service_npc_types.taught_skill_id (uuid, nullable)
├── ServiceNpcRegistrySerializer              — + taught_skill_slug
├── CityStaffing::*                           — parameterise by profession
├── db/seeds/guildmaster_definitions.rb       — new
└── guild training endpoints + ledger         — per §7.4 option (A)
```

---

## 11. Exact proposed files likely to change

### Minecraft — modified

| File | Change |
|---|---|
| `service/ServiceNpcRegistryParser.java` | add `guild.train` to `SUPPORTED_SERVICE_KEYS`; parse optional `taught_skill_slug` |
| `service/ServiceNpcTypeDefinition.java` | add `@Nullable String taughtSkillSlug` |
| `service/ServiceActionDispatcher.java` | add `guild.train` to `SUPPORTED_SERVICES`; add `dispatchGuildTrain` |
| `entity/ServiceNpcEntity.java` | route `interactAt` to the Guildmaster path; override `updateDisplayName()` |
| `skill/SkillManager.java` | expose a read-only skill definition lookup (slug → display name, max); add a Rails-confirmed award path |
| `economy/MerchantEconomyService.java` | widen `reserveCoins`/`removeAllCoins` into a reusable charge helper |
| `network/NetworkHandler.java` | register three new payloads |
| `network/ClientNetworkHandler.java` | client handlers for the two S2C payloads |
| `server/http/RailsApiUrlResolver.java` | add the guild-training `Endpoint` entries |
| `src/main/resources/assets/britannia_mod/lang/en_us.json` | new translation keys |

### Minecraft — new

`service/guild/GuildmasterCapability.java`, `GuildmasterTrainingService.java`,
`GuildTrainingQuote.java`, `GuildTrainingClient.java` + `ClientPort`, `GuildTrainingResult.java`;
`skill/GuildTrainingMath.java`;
`network/payload/GuildTrainingOpenS2CPayload.java`, `GuildTrainingRequestC2SPayload.java`,
`GuildTrainingResultS2CPayload.java`;
`client/screen/GuildmasterTrainingScreen.java`, `client/screen/guild/ClientGuildTrainingSession.java`.

### Minecraft — tests (new)

`src/test/java/.../skill/GuildTrainingMathTest.java`,
`.../service/guild/GuildmasterCapabilityTest.java`,
`.../service/ServiceNpcRegistryParserGuildTrainTest.java`,
`.../network/payload/GuildTrainingPayloadCodecTest.java`,
`.../client/screen/guild/GuildTrainingLayoutTest.java`;
`src/main/java/.../gametest/GuildmasterTrainingProxyServiceGameTests.java`,
`.../gametest/GuildmasterAssignmentReconcilerGameTests.java`.

### Rails — modified

`app/models/service_npc/action_registry.rb`, `app/models/service_npc_type.rb`,
`app/serializers/service_npc_registry_serializer.rb`,
`app/services/city_staffing.rb` + `city_staffing/{desired_staffing,spawn_capacity,plan,reconcile}.rb`,
`config/routes.rb`, `db/schema.rb`.

### Rails — new

`db/migrate/*_add_taught_skill_to_service_npc_types.rb`,
`db/seeds/guildmaster_definitions.rb`,
`app/controllers/api/guild_training_controller.rb`,
`app/services/guild_training/{quote,prepare,confirm,cancel}.rb`,
`db/migrate/*_create_guild_training_operations.rb`.

### Rails — tests (new, Minitest under `test/`)

`test/models/service_npc_type_guildmaster_test.rb`,
`test/serializers/service_npc_registry_guildmaster_test.rb`,
`test/services/city_staffing/guildmaster_staffing_test.rb`,
`test/controllers/api/guild_training_controller_test.rb`,
`test/integration/guildmaster_definition_seed_test.rb`.

---

## 12. Risks and compatibility concerns

1. **Registry version coupling is all-or-nothing (highest risk).** Publishing `guild.train` from
   Rails to a mod build whose `SUPPORTED_SERVICE_KEYS` lacks it makes
   `ServiceNpcRegistryParser.parseNpcTypes` throw, which makes `parseBootstrapRoot` return
   `REJECTED`, which replaces the whole snapshot with `ServiceNpcRegistrySnapshot.empty()` — **and
   bank tellers stop working**. Mitigation: ship the mod parser change first, or make unknown
   service keys skip that one type instead of rejecting the registry (a deliberate, tested
   behaviour change to a shared parser). Owner decision.

2. **Rails `skills/gain` hard-caps a bump at 1.0.** Any Guildmaster purchase above one point is
   rejected. Must be resolved before Milestone 5 (see §7.4).

3. **`awardSkillGain` is fire-and-forget.** Unacceptable for a paid transaction; the write must be
   Rails-confirmed before the player is charged, or reconciled after a crash.

4. **Split money/skill authority makes true atomicity impossible without new machinery.** Design
   §9.3's guarantee cannot be met by composing existing calls.

5. **No stable skill ordering exists** and skills are admin-editable CMS content. Under Option A
   this is a non-issue (no index is ever persisted). Under Option B it requires a new ordering
   column and a documented migration.

6. **Skill slug format vs. registry key format.** `animal-taming` (hyphen) is not a valid
   `DEFINITION_KEY`. Needs explicit, validated normalisation.

7. **`skills#gain` ignores `powerscroll` while `skills#set_skill` honours it.** Pre-existing Rails
   inconsistency (120 vs `max_value`). Guildmaster training tops out at 40.0 so it never reaches
   either cap, but the effective-cap calculation should read the same source as whichever endpoint
   is used.

8. **No total skill cap exists.** Design §3.3, §9.2 and §14.1 all assume one. Nothing to integrate
   with; the Guildmaster ceiling of 40.0 will be the only constraint besides `max_value`.

9. **`CityStaffing` is admin-triggered, not automatic.** A configured Guildmaster spawn block
   produces no NPC until someone runs a reconciliation for that city. This will look like a bug
   during owner validation if not called out in the test script.

10. **`ServiceNpcType.dependent: :restrict_with_exception`** on both `world_npcs` and
    `service_npc_spawn_points` — a seeded Guildmaster type that has ever been used cannot be
    deleted, only deactivated. Seeds must be `find_or_initialize_by`, matching
    `db/seeds/service_npc_definitions.rb`.

11. **`serviceNpcTypeKey` is not synced to the client.** The training screen must get the skill
    display name from a server payload, not from client-side lookup.

12. **The Rails working tree is dirty on branch `banking`** with someone else's spawn-point
    retirement work. Any Rails changes need coordination or their own branch.

13. **Bank UI components are shared with 19 passing JUnit tests.** Reuse by composition; do not
    move or rename them in this feature.

14. **`ServerEconomyService.reserveItems` has a known check-then-shrink race**
    (`docs/known_environment_baseline.md` §2.1). Not in scope, but do not copy that pattern for
    coin deduction — copy `MerchantEconomyService`'s instead.

---

## 13. Test plan mapped to existing infrastructure

Two harnesses exist on the mod side. **Neither can instantiate a `Screen`** (Architecture Decision 0,
documented in `BankDialogueLayout`'s class comment) — which is exactly why all the layout/parse/math
logic lives in plain classes.

### 13.1 JUnit — `gradlew test`

Existing tests this feature must keep green:
`service/ServiceNpcRegistryParserTest`, `service/ServiceNpcAssignmentsParserTest`,
`service/ServiceNpcAssignmentsSnapshotFilterTest`, `service/ServiceDialogueControllerTest`,
`service/banking/BankingCapabilityTest`, `service/banking/BankingOpenClientTest`,
`service/spawn/ServiceNpcSpawnConfigurationValidatorTest`, `service/spawn/ServiceNpcSpawnMenuValidationTest`,
`network/payload/ServiceNpcSpawnPayloadCodecTest`, `economy/CoinConversionTest`,
and all 19 under `client/screen/bank/`.

New JUnit coverage:

| Test | Asserts |
|---|---|
| `GuildTrainingMathTest` | `0.0→40.0 = 400g`; `0.0→1.0 = 10g`; `12.7→20.0 = 73g`; `39.9→40.0 = 1g`; `40.0→any` rejected; negative/zero delta rejected; target > 40.0 clamped; effective cap = `min(400, max_value×10)`; float↔tenths round-trip has no drift |
| `ServiceNpcRegistryParserGuildTrainTest` | `guild.train` accepted; `taught_skill_slug` optional (absent → null); a `guild.train` type without a taught skill rejected; hyphenated slug handling; existing `bank_teller` fixtures still parse |
| `GuildmasterCapabilityTest` | mirrors `BankingCapabilityTest`: inactive type → false, missing key → false, wrong service list → false |
| `GuildTrainingPayloadCodecTest` | round-trip; oversized/malformed rejected (mirrors `ServiceNpcSpawnPayloadCodecTest`) |
| `GuildTrainingLayoutTest` | mirrors `BankDialogueLayoutTest`: body never overlaps the button column at narrow widths / GUI scale 4 |

### 13.2 GameTest — `gradlew runGameTestServer --rerun-tasks --no-configuration-cache`

(Both flags are mandatory per `docs/known_environment_baseline.md` §1.1 and §1.3, or the run is
silently stale.)

Existing: `ServiceNpcEntityGameTests`, `ServiceNpcAssignmentReconcilerGameTests`,
`ServiceNpcAssignmentsCacheGameTests`, `ServiceNpcSpawnGameTests`, `BankingProxyServiceGameTests`
(**the regression canary for bankers**).

New, modelled directly on `BankingProxyServiceGameTests`' seams
(`useClientForTesting` / `useAccountScreenSenderForTesting`):

- interaction with a Guildmaster type dispatches `guild.train`, not `bank.open`
- interaction with a `bank_teller` still dispatches `bank.open` (regression)
- out-of-range / dead entity / inactive type produce no request
- forged skill slug, forged price, and out-of-range target are all ignored — the server re-derives
- insufficient funds after the screen opened → rejected, no coin loss
- successful purchase deducts exactly the quoted copper and gives correct change
- two Guildmasters with different taught skills coexist and train independently
- taught skill survives entity save/load and block-entity save/load
- a denied/absent assignment creates zero entities

### 13.3 Rails — `bin/rails test` (Minitest)

Existing to keep green: `test/models/service_npc_type_test.rb`,
`test/models/service_npc_registry_isolation_test.rb`, `test/models/world_npc_test.rb`,
`test/serializers/service_npc_registry_serializer_test.rb`,
`test/controllers/api/world_bootstrap_service_npc_registry_test.rb`,
`test/controllers/api/world_bootstrap_service_npc_assignments_test.rb`,
`test/services/city_staffing/*`, `test/services/city_staffing_reconcile_concurrency_test.rb`,
`test/integration/service_npc_definition_seed_test.rb`,
`test/integration/service_npc_spawn_wire_contract_test.rb`.

New: taught-skill validation on `ServiceNpcType`; serializer emits `taught_skill_slug` and omits it
for `bank_teller`; registry `revision` changes when a Guildmaster type is added; per-profession
staffing target and capacity; guild-training quote/prepare/confirm/cancel including idempotent
replay, cap enforcement, and concurrent-purchase safety; seed idempotency.

### 13.4 Cross-repo contract

`src/test/java/.../service/spawn/wirecontract/ServiceNpcSpawnWireContract*` (mod) pairs with
`test/integration/service_npc_spawn_wire_contract_test.rb` (Rails). Under Option A the spawn wire
contract is **unchanged**, which is a significant part of Option A's value — but the pairing should
still be re-run as a regression.

### 13.5 Build / smoke

`gradlew build --rerun-tasks --no-configuration-cache`, then a dedicated-server startup smoke test.

---

## 14. Contradictions between the design docs and the real repository

| # | Design/playbook says | Repository actually has | Impact |
|---|---|---|---|
| 1 | `ServiceNPCEntity` | `ServiceNpcEntity` (lowercase `pc`), and `ServiceNPCSpawnBlock` is `ServiceNpcSpawnBlock` | Cosmetic |
| 2 | §5.2: introduce `ServiceNpcKind.BANKER / GUILDMASTER` | The discriminator is a **Rails-published** `service_npc_types` registry with `allowed_service_keys`. No Java enum exists | **Major.** Adding a Java enum would create the parallel system the design forbids. Extend the Rails registry instead. |
| 3 | §4.3 / §6.1 / Owner Req. 5: admin selects a skill by "canonical index" over the complete skill list | No canonical order exists anywhere (`ConcurrentHashMap`, `Skill.all` with no `order`, unordered `SkillTableScreen`). Skills are admin-editable CMS rows with no seed file. The spawn block has no numeric-cycling admin UI — it is a container-menu screen with server-supplied option lists | **Major.** Under Option A the requirement is met in spirit (select the Guildmaster by name from the existing list) but not literally (no index). **Owner decision required.** |
| 4 | §6 heading: "the spawn block spawns a Guildmaster" | The block registers a *spawn point*; Rails assigns; `ServiceNpcAssignmentReconciler` materialises the entity | **Major**, but favourable — §7.3's "don't spawn then delete" is structurally impossible already. |
| 5 | §4.1 / Owner Req. 4: reuse "the existing random NPC naming system" | Service NPC names come from **Rails** `WorldNpcs::Create` / `NpcName`. The mod's `NameLoader` + `uo_names.xml` serve other NPC families and are not on this path | Favourable — free, but the mechanism is not where the doc implies. |
| 6 | §3.3 / §9.2 / §14.1: respect "existing total skill caps" | **No total skill cap exists** in either codebase | Nothing to integrate. Remove from the acceptance criteria or treat as future work. |
| 7 | §14.1 test: "0.0 → 40.0 = 400 gold" via the existing skill API | Rails `skills#gain` rejects any bump > 1.0 | **Blocker.** Must be resolved before Milestone 5. |
| 8 | §9.3: "validate → price → deduct money → award skill" as one transaction | Coins are Minecraft-authoritative; skills are Rails-authoritative. No single API spans both | **Major.** Needs prepare/confirm + reconciliation, or bank-funded payment. |
| 9 | §7.1: create a `ServiceNpcSpawnPolicy` with `canSpawn(...)` on the Minecraft side | The policy is `CityStaffing::*` in Rails, delivered as assignment data through the bootstrap/delta cache | Building the Java seam would duplicate an existing Rails system. **Do not build it.** |
| 10 | §10.1 / §10.4: reuse the "Bank Teller screen shell" | The reusable pieces exist and are already well factored, but they are named `Bank*` and live in `client/screen/bank/`, shared with 19 tests | Compose, don't rename. Package move is a later, separate change. |
| 11 | Playbook: inspect the Rails repo | No Rails checkout exists on the Windows filesystem; it is at `~/ultimacraft-website` **inside WSL**, on branch `banking`, with a dirty working tree | Reconnaissance succeeded; Milestones 4+ need WSL-native git/ruby per `docs/known_environment_baseline.md` §1.2 |
| 12 | §12: "persist the canonical taught-skill identifier on the entity" | Under Option A this is already satisfied by the existing `ServiceNpcTypeKey` NBT tag | No new persistence needed |

---

## Open questions for the owner (blocking Milestone 1)

1. **Option A (one `ServiceNpcType` per skill) or Option B (one type + a taught-skill field on the
   spawn point)?** A is dramatically smaller and gives more policy leverage; B matches the design
   document's "skill index" wording more literally. *Recommendation: A.*
2. **Payment source: inventory coins (consistent with merchants/traders, needs prepare/confirm for
   atomicity) or bank balance (single atomic Rails transaction, diverges from UO)?**
   *Recommendation: inventory coins + prepare/confirm, but bank-funded is a legitimate simplification.*
3. **How to resolve the `skills#gain` `bump > 1` limit** — new `guild_training` endpoint with a
   ledger (recommended), or reuse the admin `skills/set` endpoint?
4. **Registry version coupling:** ship the mod parser change first, or change
   `ServiceNpcRegistryParser` to skip unknown-service types instead of rejecting the whole registry?
5. **`CityStaffing` is admin-triggered.** Should Guildmaster staffing reconciliation stay manual, or
   is automatic reconciliation in scope for this feature?
6. **Which skills get Guildmasters?** The skill list is live DB content and could not be queried
   here. Owner needs to supply the intended set (or "all published skills").

---

*Milestone 0 complete. Stopping as instructed — no Milestone 1 work, no functional changes, nothing
committed, nothing pushed.*
