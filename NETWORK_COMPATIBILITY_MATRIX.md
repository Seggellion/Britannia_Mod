# Network Compatibility Matrix

## Purpose

Use this document to track every feature branch's migration to the canonical Patch 18 network system introduced by `banking`.

Do not mark a branch compatible based only on compilation. Compatibility requires registration, encoding, handling, state ownership, persistence, dedicated-server, runtime, and multiplayer evidence.

## Status legend

- `Not audited`
- `Audit in progress`
- `Audited, not merged`
- `Migration required`
- `Merged, validation incomplete`
- `Validated`
- `Blocked`

## Canonical network contract (Milestone 2, evidence-based)

The post-merge canonical architecture is the `banking` branch's network system. Verified facts:

1. **Registrar**: exactly one play-phase registrar — `network/NetworkHandler.java`, subscribed to `RegisterPayloadHandlersEvent`, `event.registrar("1")` (protocol version string unchanged from patch-18). Explicit `playToServer`/`playToClient` per payload; no bidirectional registrations. 55 registrations on banking; every payload registered exactly once.
2. **Identifiers**: identifier-based (`CustomPacketPayload.Type` over `ResourceLocation`). New payloads declare `TYPE_ID = ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "snake_case_name")` plus a `TYPE` constant. 60 IDs on the branch, all distinct (verified). Namespace is `britannia_mod` except pre-existing `britannia:skill_sync`.
3. **Codecs**: `StreamCodec` per payload. New payloads hand-write encode/decode with **decode-time validation** of malformed values (e.g. `BankDepositRequestC2SPayload` rejects negative slot indices at decode, documented as the `SellItemsC2SPayload` precedent). Encoder/decoder field order verified identical in the hand-written pairs.
4. **Serverbound trust model**: the client sends only *selection references* (entity ids, slot indices, container ids, revisions) — never facts. Handlers are thin registrar lambdas: `enqueueWork` → `instanceof ServerPlayer` → delegate to a server-side service (`BankingTransferPacketService`, `QuestProxyService.handle`, `ServiceNpcSpawnPayloadHandler`). Services re-resolve every target fresh from the live world (teller liveness/distance/capability via `BankingProxyService.resolve`; live slot re-read; protocol routing decided server-side) and re-validate at prepare AND confirm stages.
5. **Menu-request validation**: `ServiceNpcSpawnMenuRequestValidator.validate(Facts)` is the canonical serverbound menu-mutation gate — 14 ordered checks: authenticated, authorized, correctMenu, correctOwner, containerMatches, dimensionMatches, withinDistance, positionMatches, blockPresent, correctBlock, correctBlockEntity, uuidMatches, menuStillValid, revisionMatches → typed `ServiceNpcSpawnValidationError`. Optimistic-concurrency via `expectedConfigurationRevision` (stale revision → `STALE_REVISION`, resync payload path exists).
6. **Clientbound handling**: dist-gated at registration (`FMLLoader.getDist().isClient() ? ClientNetworkHandler::handleX : (p, c) -> {}`); client handlers live in `ClientNetworkHandler` only.
7. **Responses**: one typed result payload per domain (`BankTransferResultS2CPayload` with Operation × Kind enums; `QuestActionResultS2CPayload`; `ServiceNpcSpawnStateS2CPayload`), sent via a static `send` helper with a test seam (`useResultSenderForTesting`).
8. **External-service auth (replaces client token sync)**: `server/auth/` — `ServerAuthRegistry` (initialized from server dir at startup, cleared on stop), `ServerCredentials` (shard secret package-private, only a fingerprint exposed), `RequestSignature` (HMAC-SHA256 over canonical request), `RailsRequestAuthenticator` (headers applied server-side). **Secrets never reach clients.** The patch-18/Farming pattern of sending apiToken+shardSecret to players via `ClientboundSyncCityTokenPayload` is deleted, not ported.
9. **Persistence & crash-safety**: authoritative bank ledger lives in the external Rails backend. Local `SavedData` (`bank/transfer/BankTransferReceiptStore` + `BankTransferReceipts`) records in-flight transfer receipts; `BankTransferReconciliationService.runStartupReconciliation` (ServerStartedEvent) resumes pending confirms through the same live-path code (idempotent on the Rails side). `BankChequeData` is a display-only data component (persistent + networkSynchronized), documented non-authoritative.
10. **Server lifecycle subsystems** (registered in `BritanniaMod`): `WorldStateSyncPoller` (start/tick/stop with the server; polls Rails, applies world state via `WorldStateSyncApply`/`Validator`, outage-tolerant per gametests), `ServiceNpcSpawnDeliveryProcessor` (start/tick/stop), `MenuRegistry.register(modEventBus)`.
11. **Startup without a backend is tolerated**: `WorldBootstrapAPI` logs "Skipping world bootstrap because server authentication is unavailable" and continues; 401/403 mapped to `authentication_rejected`.
12. **Deleted (do not reintroduce)**: payloads `ClaimQuestRewardC2SPayload`, `GrantCoinsC2SPayload`, `ClientboundSyncCityTokenPayload`, `SpawnEscortC2SPayload`, `ServerboundQuestAcceptedPayload` (superseded by `QuestActionC2SPayload`/`QuestActionResultS2CPayload`); HTTP clients `RailsApi`, `RailsCatalog`, `WineryNetworkClient` (superseded by `server/http/RailsApiUrlResolver` + `server/auth/*`); dead `.old` files in `network/` and `event/`.

## Branch naming note (Milestone 0)

The playbook and project prompt refer to `shrines-monolith`. The actual Git branch is **`shrines-monoliths`** (plural, local and `origin/shrines-monoliths`). No singular variant exists. All entries below use the verified name. The release branch is exactly **`patch-18`** (lowercase, hyphenated, tracks `origin/patch-18`).

## Branch summary

All merge bases verified on 2026-08-08. Every feature branch is based on the current Patch 18 HEAD (`62df1dc97c5113a86f9c0f258cb90538f31efe89`); patch-18 is 0 commits ahead of each branch, and all five branches are pairwise independent (every pairwise merge base is also `62df1dc9`).

| Branch | Initial risk | Current status | Planned merge order | Merge-base commit | Branch HEAD | Integration commit | Primary owner of network state | Notes |
|---|---:|---|---:|---|---|---|---|---|
| `banking` | Critical | Validated (see completion note) | 1 | `62df1dc9` | `aa39914a2ef3caf5e1f137a657169264ae2c31cf` | `9f7e800c` (M3, 2026-08-08) | Canonical network baseline (merged) | MERGED at M3 with 44 owner-excluded .md docs. Validated: build, 658 unit tests, 333 gametests, dedicated-server boot, single-client join (zero payload errors), graceful disconnect, absent-Rails tolerance. Deferred to M10: reconnect, restart persistence, two-player. |
| `Farming` | High | Validated (see completion note) | 2 | `62df1dc9` | `1753f7cdffdc919601aaad0334ea3e3a7781c0eb` | `3cae58ed` (M5, 2026-08-08) | Server (versioned skill snapshots S2C; server-only Rails auth) | MERGED at M5. Client token sync deleted (NET-010 resolved); versioned `skill_sync` live with hasChannel guard; bootstrap = banking auth + Farming tolerant parsing + climate. Validated: build, 766 unit (6 policy skips), 333 gametests, server + client join. Deferred to M10: reconnect/restart/two-player + gameplay scenarios. |
| `blacksmithing` | High | Validated (see completion note) | 3 | `62df1dc9` | `9acfb7e253580f1fafc9892f107a270d2d748653` | `882cc0e3` (M6, 2026-08-08) | Server (session-validated crafting) | MERGED at M6. Extended craft payload schema live with server-owned session validation; registrar auto-merge reviewed intact; 265 lang keys merged key-level. Excluded: 2 docs + 2 tracked log files. Deferred to M10: craft/repair/smelt gameplay scenarios, expired-session and duplicate-submit runtime checks. |
| `shrines-monoliths` | Medium | Validated (see completion note) | 4 | `62df1dc9` | `49a44506ef44b44f88a99b5cee630859a3b73926` | `9b564af9` (M7, 2026-08-08) | Server (vanilla block-entity sync only) | MERGED at M7, network-neutral as audited. Five mechanical union conflicts resolved. Pre-existing line-ending pin defect repaired (NET-012): per-file .gitattributes eol pins + 2 test pins + 9 report entries canonicalized to LF. Deferred to M10: multiblock placement/cycling/persistence gameplay. |
| `banners-dyetub` | Medium | Validated (see completion note) | 5 | `62df1dc9` | `5debcc11e5ecdd58a8a6302885197f84ac7f6ee4` | `e4049712` (M8, 2026-08-08) | Server (dye sessions; banner render data S2C, hasChannel-guarded) | MERGED at M8 (branch now on origin). 6 registrations re-applied (61 total); 60 payload IDs unique; banners' mixins.json fix RESOLVES PRE-003. 65 docs excluded; 45 intake assets eol-pinned (NET-012 pattern); 2 guard counts updated to integrated truth. Deferred to M10: dye/banner gameplay + two-player visibility. |

## Network touchpoint inventory

File-level inventory from the Milestone 0 audit (diff of each branch against merge base `62df1dc9`). Symbol-level enumeration is Milestone 2 (banking) and Milestone 4 (others) scope.

| Branch | File | Symbol | Direction | Payload or sync purpose | Existing API/pattern | Canonical replacement | Server validation required | Persistence interaction | Runtime test | Status | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `banking` | `src/main/java/com/seggellion/britannia_mod/network/NetworkHandler.java` | `register` via `RegisterPayloadHandlersEvent`, `event.registrar("1")` | Both | Sole play-phase registrar; restructured; 55 registrations (patch-18 has 48) | NeoForge `PayloadRegistrar` | Canonical baseline | Yes | Yes | Client + dedicated server | Audit in progress | M0 diff |
| `banking` | `src/main/java/com/seggellion/britannia_mod/network/ClientNetworkHandler.java` | client handlers | S2C | Client-side payload handling, dist-gated | NeoForge `IPayloadContext` | Canonical baseline | N/A | No | Client | Audit in progress | M0 diff |
| `banking` | `src/main/java/com/seggellion/britannia_mod/network/payload/Bank*.java` (9 files) | `BankDepositRequestC2SPayload`, `BankWithdrawalRequestC2SPayload`, `BankCurrencyWithdrawalRequestC2SPayload`, `BankDepositAllCoinsRequestC2SPayload`, `BankChequeIssuanceRequestC2SPayload`, `BankChequeRedemptionRequestC2SPayload`, `BankStoredChequeRedemptionRequestC2SPayload`, `BankAccountOpenedS2CPayload`, `BankTransferResultS2CPayload` | C2S + S2C | Bank deposit/withdraw/cheque flows and results | New canonical pattern | Canonical baseline | Yes | Yes (bank/world state) | Bank scenarios (M3) | Audit in progress | M0 diff |
| `banking` | `src/main/java/com/seggellion/britannia_mod/network/payload/QuestActionC2SPayload.java`, `QuestActionResultS2CPayload.java` | quest action pair | C2S + S2C | Replaces deleted `ServerboundQuestAcceptedPayload`, `ClaimQuestRewardC2SPayload`, `GrantCoinsC2SPayload` | New canonical pattern | Canonical baseline | Yes | Yes | Quest scenarios (M3) | Audit in progress | M0 diff |
| `banking` | `src/main/java/com/seggellion/britannia_mod/network/payload/ServiceNpcSpawn*.java` (5 files) + `menu/ServiceNpcSpawnMenu.java` + `registry/MenuRegistry.java` + `service/spawn/ServiceNpcSpawnMenuRequestValidator.java` | service NPC spawn payload family | C2S + S2C | Menu-driven NPC spawn configuration with dedicated codec, handler, and request validator | New canonical pattern | Canonical baseline | Yes (validator present) | Yes | Menu scenarios (M3) | Audit in progress | M0 diff |
| `banking` | `src/main/java/com/seggellion/britannia_mod/bank/item/BankItemCodec.java` | `BankItemCodec` | N/A (serialization) | Versioned bank item envelope; default compiled version 1; dev runs force 3 via `britannia.bank.item_envelope_version` (see build.gradle comment); depends on external Rails backend milestone | New codec | Canonical baseline | N/A | Yes (external Rails) | Codec gametests exist | Audit in progress | M0 diff, build.gradle |
| `banking` | `src/main/java/com/seggellion/britannia_mod/worldstate/WorldStateSync{Poller,Apply,Validator,Outcome}.java` | world-state sync subsystem | HTTP side-channel | Polls external Rails backend and applies world state; outage/reconciliation gametests exist | New subsystem | Canonical baseline | Yes | Yes | Gametests exist | Audit in progress | M0 diff |
| `banking` | Deleted: `network/ClaimQuestRewardC2SPayload.java`, `GrantCoinsC2SPayload.java`, `ClientboundSyncCityTokenPayload.java`, `SpawnEscortC2SPayload.java`, `RailsApi.java`, `RailsCatalog.java`, `WineryNetworkClient.java`, `payload/ServerboundQuestAcceptedPayload.java`, `.old` files | removed legacy payloads/clients | — | Legacy network surface removed by banking; no branch may reintroduce these post-merge | Legacy | Removed | — | — | — | Audit in progress | M0 diff |
| `banking` | Modified send/sync paths: `skill/SkillManager.java`, `quest/QuestEventHandlers.java`, `quest/QuestProxyService.java`, `network/WorldBootstrapAPI.java` (+273/−107), `network/SendTransactionToAPI.java`, `network/RailsUpdateServer.java`, `util/BlessedItemSyncAPI.java`, `util/BlessedItemSyncHandler.java`, `city/CityDataSync.java` | various | Mixed | Send helpers and sync paths adapted to new architecture | Mixed | Canonical baseline | Yes | Yes | M3 scenarios | Audit in progress | M0 diff |
| `Farming` | `src/main/java/com/seggellion/britannia_mod/skill/SkillSyncPayload.java` | `SkillSyncPayload` (id `britannia:skill_sync`; file declares package `...network`) | S2C | **Wire-format change of an existing payload**: adds `wireVersion` (=1), `SkillManager.SkillDataState`, `revision`, `identificationBypass`; bounds skills at 512 | `CustomPacketPayload` + `StreamCodec.of` | Must be reconciled with banking's `SkillManager`/skill sync during M5 | N/A (S2C UI snapshot) | Yes (skill data) | Farming multiplayer scenarios | Migration required | M0 diff |
| `Farming` | `src/main/java/com/seggellion/britannia_mod/skill/SkillManager.java` | skill send path | S2C | Sends skill snapshots; **also modified by `banking`** — three-way merge risk | `PacketDistributor` | Banking-merged baseline | Yes | Yes | M5 | Migration required | M0 diff, overlap check |
| `Farming` | `src/main/java/com/seggellion/britannia_mod/network/WorldBootstrapAPI.java` | bootstrap API | HTTP side-channel | +487/−187 rewrite; **also rewritten by `banking`** (+273/−107) — guaranteed heavy conflict | Rails HTTP | Banking-merged baseline | Yes | Yes | M5 | Migration required | M0 diff stats |
| `Farming` | `src/main/java/com/seggellion/britannia_mod/event/WorldBootstrapHandler.java`, `config/ModConfig.java` | bootstrap/config | — | Both also modified by `banking` | — | Banking-merged baseline | — | Yes | M5 | Migration required | M0 overlap check |
| `blacksmithing` | `src/main/java/com/seggellion/britannia_mod/network/CraftBlacksmithItemC2SPayload.java` | `CraftBlacksmithItemC2SPayload` | C2S | **Wire-schema change of an existing payload**: adds `action` (CRAFT/REPAIR/SMELT), `sessionToken`, `targetToken` | `CustomPacketPayload` | Keep schema; re-apply handler onto banking's restructured `NetworkHandler` in M6 | Yes (session validation added) | Yes (inventory) | Blacksmithing scenarios | Migration required | M0 diff |
| `blacksmithing` | `src/main/java/com/seggellion/britannia_mod/network/NetworkHandler.java` | `handleCraftBlacksmithItem` | C2S handler | Adds `BlacksmithSessionManager.validate(player, sessionToken)` gate and action switch; registration count unchanged (48) | `PayloadRegistrar` handler body | Banking-merged `NetworkHandler` | Yes | Yes | M6 | Migration required | M0 diff |
| `blacksmithing` | `src/main/java/com/seggellion/britannia_mod/network/OpenBlacksmithGuiS2CPayload.java`, `ClientNetworkHandler.java`, `event/BlacksmithInteractionEvent.java` | GUI open + send path | S2C / send | Modified GUI payload and interaction send path | Existing pattern | Banking-merged baseline | Yes | No | M6 | Migration required | M0 diff |
| `shrines-monoliths` | `src/main/java/com/seggellion/britannia_mod/structure/multiblock/LargeStructureAnchorBlockEntity.java` | `getUpdateTag`, `getUpdatePacket`, `setChanged` | Vanilla BE sync | Multiblock anchor state sync to clients via vanilla mechanisms; no custom payloads anywhere in branch | Vanilla `BlockEntity` sync | None needed (verify at M4/M7) | Yes (interactions are server-side) | Yes (`saveAdditional`/`loadAdditional`) | Multiblock scenarios | Audit in progress | M0 grep of branch tree |
| `shrines-monoliths` | `src/main/java/com/seggellion/britannia_mod/registry/DataComponentRegistry.java` | data components | Item/BE data | New structure data components; file also modified by `banking` and `banners-dyetub` (3-way) | Data components | Merge union | — | Yes | M7 | Audit in progress | M0 overlap check |
| `banners-dyetub` | `src/main/java/com/seggellion/britannia_mod/network/payload/dye/*.java` (4 files) | `C2SConfirmDyeApplicationPayload`, `C2SCancelDyePreviewPayload`, `S2COpenDyePreviewPayload`, `S2CDyeApplicationResultPayload` | C2S + S2C | Dye preview session open/confirm/cancel/result; server handlers route to `DyePreviewRuntime` by `sessionId` | `PayloadRegistrar` (same pattern as banking) | Registrations must be re-appended to banking-merged `NetworkHandler` in M8 | Yes (session validation — verify at M4) | Yes (colour state) | Dye multiplayer scenarios | Audit in progress | M0 diff |
| `banners-dyetub` | `src/main/java/com/seggellion/britannia_mod/network/payload/banner/*.java` (2 files) | `S2CBannerRenderDataPayload`, `S2CBannerPlacementOrientationPayload` | S2C | Banner render data and placement orientation sync | `PayloadRegistrar` | Same as above | N/A | Yes | Banner scenarios | Audit in progress | M0 diff |
| `banners-dyetub` | `src/main/java/com/seggellion/britannia_mod/network/NetworkHandler.java`, `ClientNetworkHandler.java` | +6 registrations appended (54 total) | Both | Registrar additions; textual conflict with banking's restructured file, semantically additive | `PayloadRegistrar` | Banking-merged `NetworkHandler` | Yes | — | M8 | Audit in progress | M0 diff |
| `banners-dyetub` | Send/sync paths: `banner/placement/BannerPlacementService.java`, `banner/renderdata/BannerRenderDataSync.java`, `dye/preview/DyePreviewRuntime.java`; codecs: `bannerdyeing/api/DataCodecs.java` | send helpers + codecs | S2C sends | Server-side send paths and data codecs | `PacketDistributor` | Banking-merged baseline | Yes | Yes | M8 | Audit in progress | M0 diff |

## Milestone 4: measured conflict predictions and touchpoint classification

`git merge-tree --write-tree` dry runs of each remaining branch against the post-banking integration HEAD `9f7e800c` (2026-08-08). These are measured, not estimated — git auto-merges most files the M0 overlap analysis flagged.

### Predicted conflicts per branch

| Branch | Conflicted files (exact) | Assessment |
|---|---|---|
| `Farming` | `config/ModConfig.java`, `event/WorldBootstrapHandler.java`, `network/WorldBootstrapAPI.java`, `assets/.../lang/en_us.json` | Bootstrap/config trio is the real migration site (banking rewrote all three for server-only auth). Resolution must adopt banking's auth model and delete Farming's client token sync (NET-010). `SkillManager.java` auto-merges but is SEMANTICALLY UNVERIFIED — banking's version constructs `SkillSyncPayload`, whose record shape Farming changes; compile + review required at M5 |
| `blacksmithing` | `assets/.../lang/en_us.json` only | Minimal. `NetworkHandler.java` auto-merges (banking's restructure left the `handleCraftBlacksmithItem` region intact); semantic review of the auto-merged registrar still required at M6 |
| `shrines-monoliths` | `build.gradle`, `registry/DataComponentRegistry.java`, `assets/.../lang/en_us.json` | All mechanical (test-infra block, component registrations union, lang union) |
| `banners-dyetub` | `BritanniaMod.java`, `network/ClientNetworkHandler.java`, `network/NetworkHandler.java`, `registry/DataComponentRegistry.java`, `assets/.../lang/en_us.json`, `britannia_mod.mixins.json` | Registrar pair conflicts are additive re-application (6 new registrations appended vs banking's restructure); no API migration needed |

### Touchpoint classification (playbook categories)

| Branch | Touchpoint | Classification | Evidence / required action |
|---|---|---|---|
| `Farming` | `skill/SkillSyncPayload.java` — versioned wire format for `britannia:skill_sync` | **Requiring runtime verification** (auto-merges; consumer `SkillManager` merged from both branches) | M5: compile, review merged `SkillManager` send path, runtime skill-sync check |
| `Farming` | `network/WorldBootstrapAPI.java` — client token sync via deleted `ClientboundSyncCityTokenPayload` | **Obsolete** (payload deleted by banking; secrets must not reach clients) | M5: resolve conflict by adopting banking's server-only auth; DELETE the client send (NET-010) |
| `Farming` | `event/WorldBootstrapHandler.java`, `config/ModConfig.java` | **Conflicting** | M5: manual three-way resolution preserving Farming features on banking's structure |
| `Farming` | `block/entity/{FarmingBlockEntity, FlowerBlockEntity, OrangeTreeRootBlockEntity}` — `getUpdateTag`/`getUpdatePacket` | **Compatible** (vanilla BE sync, canonical per contract point 8/block-entity decision) | M5: runtime spot-check crop/flower visual sync |
| `Farming` | `block/entity/{CommunityFarmBlockEntity, WeightedWoodBlockEntity}` — no update packets (server-only state) | **Compatible** | None |
| `Farming` | `mixin/client/CreativeModeInventoryScreenAccessor` + mixins.json entry | **Compatible** (client accessor; mixins.json auto-merges for Farming) | M5: confirm it registers under a client-only section or is dist-guarded (PRE-003 class of hazard) |
| `blacksmithing` | `CraftBlacksmithItemC2SPayload` — record(Action, craftableId, targetToken≤128, sessionToken≤64), bounded `writeUtf` | **Compatible** (schema change matches canonical bounded-encoding + validation contract) | M6: verify handler after registrar auto-merge |
| `blacksmithing` | `NetworkHandler.handleCraftBlacksmithItem` — adds `BlacksmithSessionManager.validate` gate + action switch | **Requiring runtime verification** (auto-merged into banking's restructured file) | M6: compile + craft/repair/smelt runtime checks incl. expired-session rejection |
| `blacksmithing` | `OpenBlacksmithGuiS2CPayload` — adds `learnedRecipes` list | **Compatible** (S2C GUI payload, dist-gated handling) | M6: GUI open runtime check |
| `blacksmithing` | `BlacksmithSessionManager` (new, server-side sessions) | **Compatible** | M6: session expiry path |
| `shrines-monoliths` | `LargeStructureAnchorBlockEntity` — vanilla `getUpdateTag`/`getUpdatePacket`; server-authoritative interactions | **Compatible** (confirmed: zero custom payloads, zero packet sends in branch) | M7: placement/variant-cycle runtime check |
| `shrines-monoliths` | `DataComponentRegistry` additions | **Conflicting** (mechanical union with banking's `BANK_CHEQUE_DATA` + banners' additions) | M7: registry union resolution |
| `banners-dyetub` | 6 payloads (`payload/dye/*`, `payload/banner/*`) + registrations | **Conflicting** (textual only — re-append onto banking's registrar; pattern already canonical: dist-gated S2C, enqueueWork+ServerPlayer C2S, session-scoped) | M8: re-apply registrations during conflict resolution |
| `banners-dyetub` | `DyePreviewRuntime` S2C sends via raw `player.connection.send(new ClientboundCustomPayloadPacket(...))` | **Compatible with minor idiom divergence** (canonical code uses `PacketDistributor`/static send helpers) | M8 note; optional normalization at M9 (only if zero-risk) |
| `banners-dyetub` | `BannerBlockEntity` — vanilla update tag+packet | **Compatible** | M8: banner render-data runtime check |
| `banners-dyetub` | `bannerdyeing/api/DataCodecs`, `banner/renderdata/BannerRenderDataSync` | **Compatible** (codec + send helper, canonical shape) | M8: colour persistence check |
| `banners-dyetub` | `britannia_mod.mixins.json` | **Conflicting** (union with banking's PlayerList mixins) | M8: verify no client-only mixin lands in the common section |

### Branch-specific validation scenarios (defined for M5–M8)

- **Farming (M5)**: skill gain → `skill_sync` payload received and UI updates; plant/tend/harvest/uproot each crop archetype (single-block, tall corn, trellis grape, orange tree multi-part); flower species+colour persists across chunk unload/reload and reconnect; bootstrap runs against absent Rails without client token errors; protected-interaction denial for non-owner.
- **blacksmithing (M6)**: open GUI at forge (learnedRecipes list arrives); CRAFT with/without ingredients; REPAIR and SMELT via targetToken; expired/invalid sessionToken rejected with message and no state change; rapid duplicate submits produce no duplication.
- **shrines-monoliths (M7)**: place/remove shrine + monolith (multiblock integrity, anchor BE sync on chunk reload); decorator variant cycling server-authoritative; collision/footprint intact; dedicated-server load.
- **banners-dyetub (M8)**: open dye preview (session), confirm/cancel; colour application consumes inputs exactly once; banner placement orientation + render data sync to a second observer position; state survives reconnect and restart.

## Shared-file conflict hotspots (Milestone 0)

Files modified by two or more feature branches relative to the common merge base; these are where merge conflicts will concentrate:

| File | Modified by | Notes |
|---|---|---|
| `BritanniaMod.java`, `ClientModSetup.java`, `assets/britannia_mod/lang/en_us.json`, `build.gradle` | all 5 branches | build.gradle changes are mostly test infrastructure; banking adds `gameTestServer` run + gametest structure generation |
| `registry/CreativeTabRegistry.java` | banking, Farming, shrines-monoliths, banners-dyetub | |
| `network/NetworkHandler.java`, `network/ClientNetworkHandler.java` | banking, blacksmithing, banners-dyetub | The canonical registrar pair; banking restructures, others edit/append |
| `registry/DataComponentRegistry.java` | banking, shrines-monoliths, banners-dyetub | |
| `britannia_mod.mixins.json` | banking, Farming, banners-dyetub | |
| `skill/SkillManager.java`, `network/WorldBootstrapAPI.java`, `event/WorldBootstrapHandler.java`, `config/ModConfig.java` | banking, Farming | Core of Farming's migration burden |
| `registry/ItemRegistry.java`, `BlockRegistry.java`, `BlockEntityRegistry.java`, `registry/CommandRegistry.java`, `.gitignore` | banking, Farming | |
| `gradle.properties` | Farming only | Farming bumps `mod_version` 0.1.7k → 0.1.8; final version needs an owner decision |

## Registration audit

Banking rows completed in Milestone 2. Remaining-branch rows complete in Milestone 4.

| Payload identifier | Registering file and symbol | Direction | Codec | Handler | Registered count | Dedicated-server safe | Branch source | Final status |
|---|---|---|---|---|---:|---|---|---|
| All play payloads (48 on patch-18; 55 banking; 48 blacksmithing; 54 banners-dyetub) | `network/NetworkHandler.java`, `RegisterPayloadHandlersEvent`, `event.registrar("1")` | Explicit `playToServer`/`playToClient` per payload | `StreamCodec` per payload | Inline lambdas + `ClientNetworkHandler` (dist-gated with `FMLLoader.getDist().isClient()`) | 1 registrar | Dist-gating pattern present | patch-18 baseline | Audited, not merged |
| `britannia:skill_sync` (`SkillSyncPayload`) | `NetworkHandler` registration; class at `skill/SkillSyncPayload.java` (declares package `...network`) | S2C | `StreamCodec.of` — **Farming changes field set** | Client skill table update | 1 | Yes | patch-18 baseline; Farming rewrites | Migration required |
| `britannia_mod:bank_deposit_request` | `NetworkHandler` (banking, line ~567) | C2S | Hand-written `STREAM_CODEC`, rejects negative slot at decode | → `BankingTransferPacketService.handleDeposit` (server routes coin vs item protocol from live slot) | 1 | Yes | banking | Audited, not merged |
| `britannia_mod:bank_withdrawal_request` | `NetworkHandler` (banking) | C2S | Hand-written | → `BankingTransferPacketService.handleWithdrawal` | 1 | Yes | banking | Audited, not merged |
| `britannia_mod:bank_currency_withdrawal_request` | `NetworkHandler` (banking) | C2S | Hand-written | → `handleCurrencyWithdrawal` | 1 | Yes | banking | Audited, not merged |
| `britannia_mod:bank_deposit_all_coins_request` | `NetworkHandler` (banking) | C2S | Hand-written | → `handleDepositAllCoins` | 1 | Yes | banking | Audited, not merged |
| `britannia_mod:bank_cheque_issuance_request` | `NetworkHandler` (banking) | C2S | Hand-written | → `handleChequeIssuance` | 1 | Yes | banking | Audited, not merged |
| `britannia_mod:bank_cheque_redemption_request` | `NetworkHandler` (banking) | C2S | Hand-written | → `handleChequeRedemption` | 1 | Yes | banking | Audited, not merged |
| `britannia_mod:bank_stored_cheque_redemption_request` | `NetworkHandler` (banking) | C2S | Hand-written | → `handleStoredChequeRedemption` | 1 | Yes | banking | Audited, not merged |
| `britannia_mod:bank_account_opened` | `NetworkHandler` (banking) | S2C | Hand-written | dist-gated → `ClientNetworkHandler.handleBankAccountOpened` | 1 | Yes (dist-gated) | banking | Audited, not merged |
| `britannia_mod:bank_transfer_result` | `NetworkHandler` (banking) | S2C | Operation × Kind enums | dist-gated → `ClientNetworkHandler.handleBankTransferResult` | 1 | Yes (dist-gated) | banking | Audited, not merged |
| `britannia_mod:quest_action_request` | `NetworkHandler` (banking) | C2S | Hand-written | → `QuestProxyService.handle` | 1 | Yes | banking (replaces `ServerboundQuestAcceptedPayload`, `ClaimQuestRewardC2SPayload`, `GrantCoinsC2SPayload`) | Audited, not merged |
| `britannia_mod:quest_action_result` | `NetworkHandler` (banking) | S2C | Hand-written | dist-gated client handler | 1 | Yes (dist-gated) | banking | Audited, not merged |
| `britannia_mod:service_npc_spawn_configure` | `NetworkHandler` (banking) | C2S | `ServiceNpcSpawnPayloadCodec` | → `ServiceNpcSpawnPayloadHandler.handleConfigure` (14-fact validator + revision check) | 1 | Yes | banking | Audited, not merged |
| `britannia_mod:service_npc_spawn_resync` | `NetworkHandler` (banking) | C2S | `ServiceNpcSpawnPayloadCodec` | → `ServiceNpcSpawnPayloadHandler.handleResync` | 1 | Yes | banking | Audited, not merged |
| `britannia_mod:service_npc_spawn_state` | `NetworkHandler` (banking) | S2C | `ServiceNpcSpawnPayloadCodec` | dist-gated → `ClientNetworkHandler.handleServiceNpcSpawnState` | 1 | Yes (dist-gated) | banking | Audited, not merged |
| REMOVED: `ClaimQuestRewardC2SPayload`, `GrantCoinsC2SPayload`, `ClientboundSyncCityTokenPayload`, `SpawnEscortC2SPayload`, `ServerboundQuestAcceptedPayload` | Deleted from `NetworkHandler` and classes deleted | — | — | — | 0 | — | banking removes from patch-18 baseline | Audited, not merged |

## Protocol and migration decisions

| Topic | Patch 18 baseline | Banking branch | Final decision | Evidence | Status |
|---|---|---|---|---|---|
| Protocol version | `event.registrar("1")` | `event.registrar("1")` (unchanged) | Keep "1" — banking does not bump it; payload set differences make pre/post builds incompatible in practice regardless | M2 audit | Decided (M2), owner may override |
| Version acceptance rule | NeoForge default for registrar version "1" | Same | NeoForge default (same-version pairing) | M2 audit | Decided (M2) |
| Payload identifier strategy | Identifier-based (`ResourceLocation`); `CustomPacketPayload.Type` | Same; new payloads use `BritanniaMod.MODID` + snake_case, `TYPE_ID`/`TYPE` constants | Identifier-based; 60 IDs on banking verified distinct | M2 uniqueness check | Decided (M2) |
| Mixed old/new client support | Not defined in repo | Not defined | Not supported: one Patch 18 client+server protocol (playbook default; no acceptance predicate exists to support mixing) | Playbook + M2 | Decided (M2) |
| Existing-world data migration | City token sync to clients; no bank receipts | Deletes city-token client sync (server-only auth); adds `BankTransferReceiptStore` SavedData (new, no migration needed); `BankChequeData` component display-only | No world-data migration required by banking itself; re-verify per feature branch in M4 | M2 audit | Decided for banking (M2) |
| Existing-player data migration | — | Bank ledger lives in external Rails (account state not in player NBT) | No player-NBT migration from banking; skill data reconciliation deferred to M5 (Farming) | M2 audit | Open (Farming) |
| Menu synchronization migration | Menu payloads in `NetworkHandler` | Adds `MenuRegistry` (mod bus) + `ServiceNpcSpawnMenu` with containerId+revision-validated mutations | Banking pattern is canonical for new menus; existing menus unchanged | M2 audit | Decided (M2) |
| Block entity synchronization migration | Vanilla BE sync | Unchanged by banking; shrines-monoliths adds vanilla-sync BE | Vanilla BE sync remains canonical | M2 audit | Decided (M2) |
| Player data synchronization migration | `SkillSyncPayload` snapshot | Banking modifies `SkillManager` send path but not the payload wire format | Farming's versioned payload must be reconciled onto banking's `SkillManager` in M5 | M0/M2 diffs | Open (M5) |
| External Rails backend contract | `RailsApi`/`RailsCatalog`/`WineryNetworkClient` + client-distributed tokens | Server-only auth (`ServerAuthRegistry`, HMAC request signing), `server/http/RailsApiUrlResolver`, `worldstate` polling sync; startup tolerates missing backend; bank item envelope compiled default v1, dev-only override to v3 | Production Patch 18 ships envelope v1 unless owner states the production Rails has "Milestone 16"; server must keep tolerating an absent backend | M2 audit (NET-008 stays open for the owner call) | Open (owner) |

## Validation evidence by branch

Patch 18 baseline (Milestone 1, 2026-08-08): `gradlew build` SUCCESS; `test` NO-SOURCE (no test source set on baseline); dedicated-server smoke PASSED in the dedicated worktree — Britannia 0.1.7k + NeoForge 21.1.72, fresh world, "Done (4.024s)!", port 25565, RailsUpdateServer on 8081, clean shutdown. Pre-existing issues PRE-001..PRE-004 recorded in the integration log (notably: run tasks need `--no-configuration-cache`; clean builds need one retry past a `:neoFormPatch` flake; NET-009 below).

Feature-branch validation has not yet been run (test infrastructure arrives with the feature branches).

| Branch | Static audit | Clean build | Automated tests | Dedicated server | Client join | Reconnect | Server restart | Two-player test | Persistence | Final status |
|---|---|---|---|---|---|---|---|---|---|---|
| `banking` | M2 full + M3 staged-diff | PASS (M3) | PASS: 658 unit, 333 gametest (M3) | PASS: Done 2.911s (M3) | PASS: quickplay join, stable (M3) | PASS (M10 system-level) | PASS (M10 system-level) | Owner-pending (needs 2 players) | PASS (M10: fresh world + restart + rejoin at same position) | Validated (see completion note) |
| `Farming` | M4 full + M5 resolution review | PASS (M5) | PASS: 766 unit, 6 policy skips; 333 gametest (M5) | PASS: Done 1.119s (M5) | PASS: quickplay join, zero payload errors (M5) | PASS (M10 system-level) | PASS (M10 system-level) | Owner-pending (needs 2 players) | PASS (M10: fresh world + restart + rejoin at same position) | Validated (see completion note) |
| `blacksmithing` | M4 full + M6 registrar review | PASS (M6) | PASS: 771 unit, 333 gametest (M6) | PASS: Done 0.991s (M6) | PASS: quickplay join, zero payload errors (M6) | PASS (M10 system-level) | PASS (M10 system-level) | Owner-pending (needs 2 players) | PASS (M10: fresh world + restart + rejoin at same position) | Validated (see completion note) |
| `shrines-monoliths` | M4 full + M7 post-merge re-grep | PASS (M7) | PASS: 984 unit, 333 gametest (M7) | PASS: Done 1.200s (M7) | PASS: quickplay join, zero payload errors (M7) | PASS (M10 system-level) | PASS (M10 system-level) | Owner-pending (needs 2 players) | PASS (M10: fresh world + restart + rejoin at same position) | Validated (see completion note) |
| `banners-dyetub` | M4 full + M8 post-merge ID re-verify | PASS (M8) | PASS: 1,679 unit, 333 gametest (M8) | PASS: Done 2.936s, banner data 62/62 (M8) | PASS: quickplay join, render data received (M8) | PASS (M10 system-level) | PASS (M10 system-level) | Owner-pending (needs 2 players) | PASS (M10: fresh world + restart + rejoin at same position) | Validated (see completion note) |

## Open compatibility findings

| Finding ID | Severity | Branch | Description | Evidence | Proposed resolution | Milestone | Status |
|---|---|---|---|---|---|---|---|
| NET-001 | Critical | `banking` | ~~Unaudited~~ → M2 audit COMPLETE: canonical contract documented (registrar, IDs, codecs, trust model, validator, auth, persistence, lifecycle). All 60 payload IDs distinct; every payload registered once; serverbound handlers delegate to validating services; clientbound dist-gated. Merge is textually conflict-free (merge base = integration HEAD). Residual risk moves to M3 validation: build, 98 unit tests, gametests, dedicated-server + client smoke | M2 audit | Merge in M3 with full validation tiers | 3 | Resolved for audit; M3 validation pending |
| NET-002 | High | `Farming` | RESOLVED at M5: versioned `skill_sync` merged (banking HTTP + Farming payload, hasChannel-guarded); bootstrap trio manually resolved onto banking's server-only auth with Farming's tolerant parsing and diagnostics ported | M5 merge `3cae58ed`; 766 unit + 333 gametests + join smoke | Runtime gameplay scenarios at M10 | 4 and 5 | Resolved (M10 runtime pending) |
| NET-010 | High | `Farming` | RESOLVED at M5: client token/secret sync DELETED (not ported); no reference to `ClientboundSyncCityTokenPayload` or `CityAPITokenData` anywhere in src/main; server-only auth is the sole Rails path | M5 grep + merge diff | None — verify nothing regresses it at M9 | 5 | Resolved |
| NET-011 | Medium | integration | Six Farming doc-reconciliation tests skip via JUnit assumptions because their subject `.md` documents are owner-excluded from this branch; they run fully on the Farming feature branch. Listed so M9/M11 reviewers know the 6 skips are intentional | M5 test run (766/0/6) | Accept as policy consequence; revisit only if owner changes the exclusion policy | 5 | Open (accepted) |
| NET-003 | High | `blacksmithing` | RESOLVED at M6: schema + session-validated handler live on banking's registrar (auto-merge manually reviewed); bounded encodings confirmed; no new registrations | M6 merge `882cc0e3`; 771 unit + 333 gametests + join smoke | Gameplay scenarios at M10 | 4 and 6 | Resolved (M10 runtime pending) |
| NET-004 | Medium | `shrines-monoliths` | RESOLVED at M7: merged network-neutral as audited (vanilla BE sync + data components; registrar untouched at 55); registry conflicts resolved as unions | M7 merge `9b564af9`; 984 unit + 333 gametests + join smoke | Multiblock gameplay scenarios at M10 | 4 and 7 | Resolved (M10 runtime pending) |
| NET-012 | Medium | `shrines-monoliths` | Pre-existing branch defect repaired at M7: SHA-256 content pins and CONTENT_REPORT.json hashes were recorded over per-file dev-tree line endings (internally inconsistent LF/CRLF); no fresh checkout could pass the full suite. Repaired via per-file `.gitattributes` eol pins, 2 updated test pin constants, 9 updated report entries (LF-canonical). Asset content byte-identical to the branch's blobs | M7 evidence chain (staged-blob hash == pin proof) | M9 reviewers: treat .gitattributes + pin updates as the durable contract; if the owner regenerates CONTENT_REPORT.json on the feature branch, regenerate from an LF checkout | 7 | Resolved |
| NET-005 | Medium | `banners-dyetub` | RESOLVED at M8: 6 payloads re-applied onto the canonical registrar (61 registrations; 60 unique IDs verified post-merge); render-data send hasChannel-guarded; real-client receipt verified at runtime | M8 merge `e4049712`; 1,679 unit + 333 gametests + join smoke | Gameplay scenarios at M10 | 4 and 8 | Resolved (M10 runtime pending) |
| NET-006 | High | `banners-dyetub` | RESOLVED 2026-08-08: owner authorized the push; `origin/banners-dyetub` created at `5debcc11` (verified identical to local HEAD) | `git push -u origin banners-dyetub` | None | 0 follow-up | Resolved |
| NET-007 | Low | `Farming` | `mod_version` conflict: Farming sets 0.1.8; all other branches and patch-18 remain 0.1.7k | gradle.properties diff | Owner decision on final Patch 18 version string | 5 | Open |
| NET-008 | Medium | `banking` | Bank item envelope default is compiled version 1; dev runs force version 3 which "must not be assumed" for production per build.gradle comment; external Rails backend milestone dependency | banking build.gradle comment | Record the Rails backend contract decision in M2 | 2 | Open |
| NET-009 | Low | patch-18 baseline | CLOSED at M9: banners' mixins.json fix adopted at M8; the RuntimeDistCleaner/TitleScreen ERROR is verifiably absent from both M8 server logs (smoke + gametest server, 0 occurrences) and the owner approved closing without further M10 verification | M8 mixins.json resolution + M9 log grep + owner approval 2026-08-08 | None | 8 and 9 | Closed |

## Milestone 9: repository-wide consolidation audit results (2026-08-08, HEAD `e4049712`)

The integrated tree contains ONE coherent network architecture. Every check below ran against the final merged tree:

1. **Registrar sites (3 total, all accounted for)**: `network/NetworkHandler.java` — the canonical play registrar, 61 registrations (33 `playToServer` + 28 `playToClient`); `network/ClientModWhitelist.java` — baseline configuration-phase pair (`client_mod_audit_request`/`_response`, byte-identical to patch-18); `event/ClientEventHandler.registerClientPackets` — a baseline EMPTY vestige (obtains a registrar, registers nothing; pre-existing, harmless, report-only). No feature branch added an independent registrar.
2. **Exactly-once registration**: 61 play + 2 configuration registrations, zero duplicates (per-block extraction).
3. **Payload ID uniqueness**: zero collisions across all 64 payload classes; 66 distinct declared IDs (includes the config pair and two dead classes below).
4. **No legacy channel APIs**: zero hits for SimpleChannel/NetworkRegistry/EventNetworkChannel/ChannelBuilder.
5. **No obsolete symbols**: zero references to any banking-deleted class (quest/coins/city-token/escort payloads, RailsApi, RailsCatalog, WineryNetworkClient, CityAPITokenData) in main or test sources.
6. **Single protocol**: every registrar site uses version "1"; no mixed protocol.
7. **Serverbound thread/validation**: all 33 play C2S handlers enqueue to the main thread and validate — inline (enqueueWork + ServerPlayer + context checks) or via verified method refs (ServiceNpcSpawnPayloadHandler 14-fact validator [M2], handleCraftBlacksmithItem session gate [M6], ServerPayloadHandler.handleBottling distance check).
8. **Clientbound side safety**: 27 of 28 S2C registrations dist-gated with `FMLLoader.getDist().isClient()`; `ItemBurnedS2CPayload` uses a `context.flow().isClientbound()` guard instead — a valid alternative idiom, runtime-verified server-safe across every dedicated-server run this project executed.
9. **hasChannel guards**: exactly 2 (skill sync M5, banner render data M8) — consistent with the no-mixed-clients protocol decision: mod payloads are required, so vanilla clients cannot join; the guards exist for gametest mock players.
10. **Pre-existing dead network code (report-only, all present on patch-18 in identical states; left in place per the no-unrelated-changes rule)**: `CraftBlacksmithItemPayload` (`britannia_mod:craft_bs_item`, never registered, self-referential only), `OpenQuestScreenS2CPayload` (`britannia_mod:open_quest_screen`, import-only, never registered or sent), the empty `registerClientPackets` method, and the repo's `.old` files. Candidates for post-integration cleanup.
11. **Persistence/migration decisions**: all documented in the protocol table above — no world or player NBT migration required by any branch; new SavedData (bank transfer receipts) and data components (6) are additive; `skill_sync` carries its own wire version.
12. **Final green run at audit close**: clean worktree; build SUCCESS; 1,679 unit tests 0 failures (20 accepted policy skips, NET-011); all 333 gametests pass.

## Completion note (2026-08-08, merged into patch-18 as `632570fe`)

All five branches are marked `Validated` on the strength of: static and symbol-level audits (M2/M4/M9), clean builds, 1,679 unit tests with 0 failures (17 documented skips), 333 gametests, the M9 12-point consolidation audit, and M10 automated runtime (zero-error clean boot, join, reconnect, graceful restart, position-level persistence).

Two categories remain unexercised in this environment and are the honest limits of that label:
- **Interactive feature gameplay** — bank flows, farming interactions, blacksmith craft/repair/smelt (incl. expired-session rejection), shrine/monolith placement and cycling, dye tub sessions and banner placement, repeated menu cycling, rapid-action sequences, dimension changes.
- **Two-player scenarios** — per-player state scoping, cross-player leakage, tracking-range visibility, latency behaviour.

Several of these also require a reachable Rails backend. Owner-accepted at Milestone 10.

## Completion rule

This matrix is complete only when every row is evidence-based, every open critical or high-severity finding is resolved, every active payload is registered exactly once, and every branch has passed its required runtime and multiplayer validation.
