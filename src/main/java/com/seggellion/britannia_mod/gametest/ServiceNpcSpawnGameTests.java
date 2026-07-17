package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.city.BootstrapCityRegistryCache;
import com.seggellion.britannia_mod.city.BootstrapCityRegistrySnapshot;
import com.seggellion.britannia_mod.menu.ServiceNpcSpawnMenu;
import com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnStateS2CPayload;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnAcknowledgementReceipt;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnCanonicalLocation;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnClaim;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnClaimData;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnClientResult;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnCollisionEvidence;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnCollisionRepairCoordinator;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnConfigurationValidator;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnDeliveryProcessor;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnLocation;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnOperation;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnOperationRequest;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnOutcome;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingData;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingDisposition;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingOperation;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingRecord;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingRequestAdapter;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnProtocolResponse;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnReceiptReconciler;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnRegistrationState;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnValidationError;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ServiceNpcSpawnGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String CONTROLLED_SHARD_ONE = "gametest_delivery_one";
    private static final String CONTROLLED_SHARD_TWO = "gametest_delivery_two";

    private ServiceNpcSpawnGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void newPlacementGetsIdentityWithoutPendingWork(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        UUID id = requireId(post);

        ServiceNpcSpawnClaim claim = ServiceNpcSpawnClaimData.get(helper.getLevel()).find(id);
        check(claim != null, "new placement did not create an identity claim");
        check(claim.location().pos().equals(helper.absolutePos(relative)), "identity claim used the wrong position");
        check(!ServiceNpcSpawnPendingData.get(helper.getLevel()).snapshot().containsKey(id),
                "unconfigured placement created pending Rails work");
        check(post.getConfigurationRevision() == 0L, "new placement did not start at revision zero");
        check(post.getRegistrationState() == ServiceNpcSpawnRegistrationState.UNCONFIGURED,
                "new placement did not start unconfigured");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void separatePlacementsGetDistinctIdentities(GameTestHelper helper) {
        UUID first = requireId(placePost(helper, new BlockPos(1, 1, 1)));
        UUID second = requireId(placePost(helper, new BlockPos(3, 1, 1)));
        check(!first.equals(second), "separate placements shared a spawn-point UUID");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void normalPlacementRejectsPreloadedIdentityAndConfiguration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get());
        ServiceNpcSpawnBlockEntity post = requirePost(level, absolute);
        UUID suppliedId = UUID.randomUUID();
        CompoundTag suppliedData = new CompoundTag();
        suppliedData.putUUID("SpawnPointId", suppliedId);
        suppliedData.putUUID("CityPublicId", UUID.randomUUID());
        suppliedData.putString("ServiceNpcTypeKey", "bank_teller");
        suppliedData.putBoolean("Enabled", false);
        suppliedData.putLong("ConfigurationRevision", 9L);
        suppliedData.putString("RegistrationState", ServiceNpcSpawnRegistrationState.REGISTERED.name());
        post.loadCustomOnly(suppliedData, level.registryAccess());

        BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get().setPlacedBy(
                level,
                absolute,
                level.getBlockState(absolute),
                null,
                ItemStack.EMPTY
        );

        UUID authoritativeId = requireId(post);
        check(!suppliedId.equals(authoritativeId), "normal placement trusted a preloaded UUID");
        check(post.getCityPublicId() == null && post.getServiceNpcTypeKey() == null,
                "normal placement trusted preloaded configuration");
        check(post.isEnabled() && post.getConfigurationRevision() == 0L,
                "normal placement did not restore new-post defaults");
        check(post.getRegistrationState() == ServiceNpcSpawnRegistrationState.UNCONFIGURED,
                "normal placement retained a preloaded registration state");
        ServiceNpcSpawnClaim claim = ServiceNpcSpawnClaimData.get(level).find(authoritativeId);
        check(claim != null && claim.location().pos().equals(absolute),
                "normal placement did not claim its server-generated UUID");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void cachedConfigurationCreatesReloadablePendingUpsertAndSyncedState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        UUID id = requireId(post);
        UUID cityId = UUID.randomUUID();
        BootstrapCityRegistrySnapshot cities = BootstrapCityRegistrySnapshot.available(List.of(
                new BootstrapCityDefinition(cityId, "Britain")
        ));
        ServiceNpcTypeDefinition type = new ServiceNpcTypeDefinition(
                "bank_teller",
                "Bank Teller",
                "banker",
                "minecraft:villager",
                "bank_default",
                List.of("open_bank"),
                true,
                true,
                1L
        );
        ServiceNpcRegistrySnapshot serviceTypes = new ServiceNpcRegistrySnapshot(
                1,
                1L,
                Map.of(),
                Map.of(type.key(), type),
                Map.of()
        );

        BootstrapCityRegistryCache.replace(cities);
        ServiceNpcRegistryCache.replace(serviceTypes);
        try {
            check(ServiceNpcSpawnConfigurationValidator.validate(
                            BootstrapCityRegistryCache.snapshot(),
                            ServiceNpcRegistryCache.snapshot(),
                            cityId,
                            type.key()
                    ) == ServiceNpcSpawnValidationError.NONE,
                    "valid cached registry selection was rejected");
            check(post.applyConfiguration(level, cityId, type.key(), false, 0L)
                            == ServiceNpcSpawnValidationError.NONE,
                    "valid cached configuration was not applied");
            check(cityId.equals(post.getCityPublicId()) && type.key().equals(post.getServiceNpcTypeKey()),
                    "accepted configuration did not update the post");
            check(!post.isEnabled() && post.getConfigurationRevision() == 1L,
                    "enabled state or configuration revision did not synchronize");
            check(post.getRegistrationState() == ServiceNpcSpawnRegistrationState.PENDING_REGISTRATION,
                    "first configuration did not enter pending registration");

            ServiceNpcSpawnPendingData pendingData = ServiceNpcSpawnPendingData.get(level);
            ServiceNpcSpawnPendingRecord pending = pendingData.snapshot().get(id);
            check(pending != null && pending.operation() == ServiceNpcSpawnPendingOperation.UPSERT,
                    "accepted configuration did not create pending UPSERT work");
            check(cityId.equals(pending.cityPublicId()) && type.key().equals(pending.serviceNpcTypeKey())
                            && !pending.enabled() && pending.configurationRevision() == 1L,
                    "pending UPSERT did not preserve the accepted snapshot");

            CompoundTag savedPending = pendingData.save(new CompoundTag(), level.registryAccess());
            ServiceNpcSpawnPendingRecord reloaded = ServiceNpcSpawnPendingData
                    .load(savedPending, level.registryAccess())
                    .snapshot()
                    .get(id);
            check(pending.equals(reloaded), "pending UPSERT did not survive a save/load boundary");

            ServerPlayer player = FakePlayerFactory.get(
                    level,
                    new GameProfile(UUID.randomUUID(), "service-npc-spawn-state-gametest")
            );
            ServiceNpcSpawnMenu menu = new ServiceNpcSpawnMenu(
                    7,
                    player.getInventory(),
                    level.dimension(),
                    absolute,
                    id
            );
            ServiceNpcSpawnStateS2CPayload state = ServiceNpcSpawnStateS2CPayload.create(
                    player,
                    menu,
                    ServiceNpcSpawnValidationError.NONE
            );
            check(state.blockPresent() && state.cityRegistryAvailable() && state.serviceTypeRegistryAvailable(),
                    "authoritative state did not expose the cached registries");
            check(cityId.equals(state.cityPublicId()) && type.key().equals(state.serviceNpcTypeKey()),
                    "authoritative state did not expose the accepted configuration");
            check(!state.enabled() && state.configurationRevision() == 1L
                            && state.registrationState() == ServiceNpcSpawnRegistrationState.PENDING_REGISTRATION,
                    "authoritative state did not expose enabled/revision/pending status");
        } finally {
            BootstrapCityRegistryCache.clear();
            ServiceNpcRegistryCache.clear();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void saveAndReloadPreservesCanonicalIdentity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        ServiceNpcSpawnBlockEntity original = placePost(helper, relative);
        UUID id = requireId(original);
        CompoundTag saved = original.saveCustomOnly(level.registryAccess());

        level.removeBlockEntity(absolute);
        ServiceNpcSpawnBlockEntity reloaded = new ServiceNpcSpawnBlockEntity(
                absolute,
                BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get().defaultBlockState()
        );
        reloaded.loadCustomOnly(saved, level.registryAccess());
        level.setBlockEntity(reloaded);
        reloaded.serverTick();

        check(id.equals(reloaded.getSpawnPointId()), "save/reload changed the canonical spawn-point UUID");
        check(reloaded.currentLocation(level).equals(reloaded.getIdentityOrigin()),
                "save/reload did not retain the canonical identity origin");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void survivalCreativeBreakAndReplacementCreateTerminalRemoveWork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos survivalPos = new BlockPos(1, 1, 1);
        BlockPos creativePos = new BlockPos(3, 1, 1);
        BlockPos replacedPos = new BlockPos(5, 1, 1);
        UUID survivalId = requireId(placePost(helper, survivalPos));
        UUID creativeId = requireId(placePost(helper, creativePos));
        UUID replacedId = requireId(placePost(helper, replacedPos));

        ServerPlayer player = FakePlayerFactory.get(
                level,
                new GameProfile(UUID.randomUUID(), "service-npc-spawn-gametest")
        );
        BlockPos survivalAbsolute = helper.absolutePos(survivalPos);
        BlockPos creativeAbsolute = helper.absolutePos(creativePos);
        player.setPos(survivalAbsolute.getX() + 0.5D, survivalAbsolute.getY(), survivalAbsolute.getZ() + 0.5D);
        AABB ownedBounds = new AABB(survivalAbsolute).minmax(new AABB(helper.absolutePos(replacedPos))).inflate(1.0D);
        StructureRecord ownedRegion = new StructureRecord(
                player.getUUID(),
                ownedBounds,
                ownedBounds,
                UUID.randomUUID(),
                "gametest"
        );
        StructureRegionManager.registerStructure(ownedRegion);
        try {
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            check(player.gameMode.destroyBlock(survivalAbsolute),
                    "survival player could not break the spawn post inside an owned structure");
            player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            check(player.gameMode.destroyBlock(creativeAbsolute),
                    "creative player could not break the spawn post");
            helper.setBlock(replacedPos, Blocks.STONE);
        } finally {
            StructureRegionManager.unregisterStructure(ownedRegion);
        }

        assertRemove(level, survivalId);
        assertRemove(level, creativeId);
        assertRemove(level, replacedId);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void explosionCreatesTerminalRemoveWork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(3, 1, 3);
        BlockPos absolute = helper.absolutePos(relative);
        UUID id = requireId(placePost(helper, relative));

        level.explode(null, absolute.getX() + 0.5D, absolute.getY() + 0.5D, absolute.getZ() + 0.5D,
                4.0F, Level.ExplosionInteraction.BLOCK);
        helper.runAfterDelay(2, () -> {
            check(!level.getBlockState(absolute).is(BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get()),
                    "explosion did not destroy the spawn post");
            assertRemove(level, id);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void chunkUnloadRemovalDoesNotCreateRemoveWork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServiceNpcSpawnBlockEntity post = placePost(helper, new BlockPos(1, 1, 1));
        UUID id = requireId(post);

        // Chunk unload removes block entities from the live level without replacing their blocks.
        post.setRemoved();
        check(!ServiceNpcSpawnPendingData.get(level).snapshot().containsKey(id),
                "block-entity unload was mistaken for true block destruction");
        check(ServiceNpcSpawnClaimData.get(level).find(id) != null,
                "block-entity unload released the durable identity claim");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void normalAndStickyPistonsCannotMovePosts(GameTestHelper helper) {
        BlockPos normalPost = new BlockPos(2, 1, 1);
        BlockPos stickyPullPost = new BlockPos(3, 1, 4);
        placePost(helper, normalPost);

        check(BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get().defaultBlockState().getPistonPushReaction()
                        == PushReaction.BLOCK,
                "spawn post did not advertise an immovable piston reaction");
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.PISTON.defaultBlockState()
                .setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK);

        helper.setBlock(new BlockPos(1, 1, 4), Blocks.STICKY_PISTON.defaultBlockState()
                .setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(0, 1, 4), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(3, () -> {
            placePost(helper, stickyPullPost);
            helper.setBlock(new BlockPos(0, 1, 4), Blocks.AIR);
        });
        helper.runAfterDelay(8, () -> {
            helper.assertBlockPresent(BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get(), normalPost);
            helper.assertBlockPresent(BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get(), stickyPullPost);
            helper.assertBlockNotPresent(BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get(), new BlockPos(4, 1, 4));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void cloneCommandRekeysAndResetsCopiedState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourceRelative = new BlockPos(1, 1, 1);
        BlockPos targetRelative = new BlockPos(4, 1, 1);
        BlockPos source = helper.absolutePos(sourceRelative);
        BlockPos target = helper.absolutePos(targetRelative);
        ServiceNpcSpawnBlockEntity original = placeConfiguredPost(helper, sourceRelative);
        UUID originalId = requireId(original);

        String command = "clone " + coordinates(source) + " " + coordinates(source) + " "
                + coordinates(target) + " replace force";
        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withLevel(level).withPermission(4),
                command
        );

        ServiceNpcSpawnBlockEntity copy = requirePost(level, target);
        copy.serverTick();
        assertFreshCopy(originalId, original, copy);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void structureTemplateCopyRekeysAndResetsCopiedState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourceRelative = new BlockPos(1, 1, 1);
        BlockPos targetRelative = new BlockPos(4, 1, 1);
        BlockPos source = helper.absolutePos(sourceRelative);
        BlockPos target = helper.absolutePos(targetRelative);
        ServiceNpcSpawnBlockEntity original = placeConfiguredPost(helper, sourceRelative);
        UUID originalId = requireId(original);

        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, source, new BlockPos(1, 1, 1), true, Blocks.STRUCTURE_VOID);
        boolean placed = template.placeInWorld(
                level,
                target,
                target,
                new StructurePlaceSettings(),
                level.getRandom(),
                3
        );
        check(placed, "structure-template placement failed");

        ServiceNpcSpawnBlockEntity copy = requirePost(level, target);
        copy.serverTick();
        assertFreshCopy(originalId, original, copy);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void dropsAndBlockEntityItemSerializationContainNoIdentity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(3, 1, 3);
        BlockPos absolute = helper.absolutePos(relative);
        ServiceNpcSpawnBlockEntity post = placeConfiguredPost(helper, relative);
        ItemStack serialized = new ItemStack(ItemRegistry.SERVICE_NPC_SPAWN_BLOCK_ITEM.get());
        post.saveToItem(serialized, level.registryAccess());
        check(serialized.get(DataComponents.BLOCK_ENTITY_DATA) == null,
                "block-entity item serialization retained identity/configuration data");

        check(level.destroyBlock(absolute, true), "spawn post could not be destroyed with drops enabled");
        helper.runAfterDelay(2, () -> {
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0D));
            ItemEntity spawnPostDrop = drops.stream()
                    .filter(entity -> entity.getItem().is(ItemRegistry.SERVICE_NPC_SPAWN_BLOCK_ITEM.get()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("spawn post did not produce its clean item drop"));
            check(spawnPostDrop.getItem().get(DataComponents.BLOCK_ENTITY_DATA) == null,
                    "spawn-post drop retained identity/configuration data");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void automaticDeliveryRegistersExactPost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtomicLong clock = new AtomicLong(1_000_000L);
        PendingFixture fixture = pendingPost(
            helper, new BlockPos(1, 1, 1), 0L, CONTROLLED_SHARD_ONE);
        List<ServiceNpcSpawnOperationRequest> submitted = new ArrayList<>();
        ServiceNpcSpawnDeliveryProcessor processor =
            ServiceNpcSpawnDeliveryProcessor.replaceForGameTest(
                level.getServer(),
                (server, request) -> {
                    submitted.add(request);
                    return completedSubmission(success(request, ServiceNpcSpawnOutcome.APPLIED, clock.get()));
                },
                clock::get,
                CONTROLLED_SHARD_ONE
            );
        processor.processNowForGameTest();

        helper.runAfterDelay(3, () -> {
            check(submitted.size() == 1, "automatic processor did not submit exactly one operation");
            check(submitted.getFirst().operationId().equals(fixture.pending.operationId()),
                "automatic submission changed the stable operation ID");
            check(submitted.getFirst().spawnUuid().equals(fixture.pending.spawnPointId()),
                "automatic submission changed the spawn UUID");
            check(fixture.post.getRegistrationState() == ServiceNpcSpawnRegistrationState.REGISTERED,
                "valid Rails success did not register the exact loaded post");
            check(!ServiceNpcSpawnPendingData.get(level).snapshot().containsKey(fixture.pending.spawnPointId()),
                "successful UPSERT remained pending");
            verifyProcessorBoundsShutdownAndReplacementThenDestroy(helper);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void receiptApplicationIsReplaySafeAndTwoPass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PendingFixture fixture = pendingPost(helper, new BlockPos(1, 1, 1), 1L);
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(level);
        check(data.acknowledgeSuccess(
                fixture.pending.token(),
                successResponse(fixture.pending, ServiceNpcSpawnOutcome.ALREADY_APPLIED, 2_000L)),
            "could not create durable UPSERT receipt");
        ServiceNpcSpawnAcknowledgementReceipt receipt =
            data.findAcknowledgement(fixture.pending.spawnPointId());
        check(receipt != null, "UPSERT acknowledgement did not create a receipt");

        ServiceNpcSpawnReceiptReconciler.reconcileLoadedBlock(level, fixture.post);
        check(fixture.post.getRegistrationState() == ServiceNpcSpawnRegistrationState.REGISTERED,
            "first receipt pass did not register the post");
        check(fixture.post.hasAcknowledgementMarker(receipt),
            "first receipt pass did not write the exact acknowledgement marker");
        check(data.findAcknowledgement(fixture.pending.spawnPointId()) != null,
            "first receipt pass consumed the receipt too early");

        ServiceNpcSpawnReceiptReconciler.reconcileLoadedBlock(level, fixture.post);
        check(data.findAcknowledgement(fixture.pending.spawnPointId()) == null,
            "later matching pass did not consume the receipt");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void receiptMarkerSurvivesBlockSaveReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        PendingFixture fixture = pendingPost(helper, relative, 2L);
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(level);
        check(data.acknowledgeSuccess(
                fixture.pending.token(),
                successResponse(fixture.pending, ServiceNpcSpawnOutcome.APPLIED, 3_000L)),
            "could not create save/reload receipt");
        ServiceNpcSpawnReceiptReconciler.reconcileLoadedBlock(level, fixture.post);
        CompoundTag saved = fixture.post.saveCustomOnly(level.registryAccess());

        level.removeBlockEntity(absolute);
        ServiceNpcSpawnBlockEntity reloaded = new ServiceNpcSpawnBlockEntity(
            absolute, BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get().defaultBlockState()
        );
        reloaded.loadCustomOnly(saved, level.registryAccess());
        level.setBlockEntity(reloaded);
        reloaded.serverTick();

        check(reloaded.getRegistrationState() == ServiceNpcSpawnRegistrationState.REGISTERED,
            "save/reload lost the acknowledged REGISTERED state");
        check(data.findAcknowledgement(fixture.pending.spawnPointId()) == null,
            "reloaded exact marker did not consume the replayed receipt");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unavailableBlockEntityReplaysReceiptAfterReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        PendingFixture fixture = pendingPost(helper, relative, 3L);
        CompoundTag savedBeforeAcknowledgement =
            fixture.post.saveCustomOnly(level.registryAccess());
        level.removeBlockEntity(absolute);

        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(level);
        check(data.acknowledgeSuccess(
                fixture.pending.token(),
                successResponse(fixture.pending, ServiceNpcSpawnOutcome.APPLIED, 3_500L)),
            "could not acknowledge unavailable block fixture");
        ServiceNpcSpawnAcknowledgementReceipt receipt =
            data.findAcknowledgement(fixture.pending.spawnPointId());
        check(receipt != null, "unavailable block acknowledgement was not durable");
        ServiceNpcSpawnReceiptReconciler.reconcileLocation(level.getServer(), receipt);
        check(data.findAcknowledgement(fixture.pending.spawnPointId()) != null,
            "receipt disappeared while its block entity was unavailable");

        ServiceNpcSpawnBlockEntity reloaded = new ServiceNpcSpawnBlockEntity(
            absolute, BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get().defaultBlockState()
        );
        reloaded.loadCustomOnly(savedBeforeAcknowledgement, level.registryAccess());
        level.setBlockEntity(reloaded);
        reloaded.serverTick();
        check(reloaded.getRegistrationState() == ServiceNpcSpawnRegistrationState.REGISTERED,
            "reloaded matching block did not apply its durable receipt");
        check(data.findAcknowledgement(fixture.pending.spawnPointId()) != null,
            "first reload application consumed the receipt");
        reloaded.serverTick();
        check(data.findAcknowledgement(fixture.pending.spawnPointId()) == null,
            "later reload pass did not consume the applied receipt");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unloadedReceiptDoesNotForceLoadChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = new BlockPos(20_000_000, 64, 20_000_000);
        ServiceNpcSpawnPendingRecord pending = new ServiceNpcSpawnPendingRecord(
            ServiceNpcSpawnPendingOperation.UPSERT,
            UUID.randomUUID(),
            ModConfig.SHARD_NAME,
            new ServiceNpcSpawnLocation(
                level.getServer().getWorldData().getLevelName(),
                level.dimension().location(),
                far
            ),
            UUID.randomUUID(),
            "bank_teller",
            true,
            1L,
            3L
        );
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(level);
        check(data.put(pending) == ServiceNpcSpawnPendingData.MutationResult.ACCEPTED,
            "could not queue unloaded receipt fixture");
        ServiceNpcSpawnPendingRecord stored = data.snapshot().get(pending.spawnPointId());
        check(data.acknowledgeSuccess(
                stored.token(), successResponse(stored, ServiceNpcSpawnOutcome.APPLIED, 4_000L)),
            "could not acknowledge unloaded receipt fixture");
        ServiceNpcSpawnAcknowledgementReceipt receipt =
            data.findAcknowledgement(stored.spawnPointId());
        check(receipt != null, "unloaded success did not remain durable");
        check(!level.hasChunk(far.getX() >> 4, far.getZ() >> 4),
            "far fixture chunk unexpectedly started loaded");

        ServiceNpcSpawnReceiptReconciler.reconcileLocation(level.getServer(), receipt);

        check(!level.hasChunk(far.getX() >> 4, far.getZ() >> 4),
            "receipt reconciliation force-loaded an unavailable chunk");
        check(data.findAcknowledgement(stored.spawnPointId()) != null,
            "unavailable-chunk receipt was discarded");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void staleReceiptCannotRegisterNewerRevision(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PendingFixture fixture = pendingPost(helper, new BlockPos(1, 1, 1), 4L);
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(level);
        check(data.acknowledgeSuccess(
                fixture.pending.token(),
                successResponse(fixture.pending, ServiceNpcSpawnOutcome.APPLIED, 5_000L)),
            "could not create stale receipt fixture");

        CompoundTag newer = fixture.post.saveCustomOnly(level.registryAccess());
        newer.putLong("ConfigurationRevision", fixture.pending.configurationRevision() + 1L);
        newer.putString("RegistrationState", ServiceNpcSpawnRegistrationState.PENDING_UPDATE.name());
        newer.remove("LastAcknowledgedOperationId");
        newer.remove("LastAcknowledgedRecordedAtEpochMillis");
        fixture.post.loadCustomOnly(newer, level.registryAccess());
        ServiceNpcSpawnReceiptReconciler.reconcileLoadedBlock(level, fixture.post);

        check(fixture.post.getRegistrationState() == ServiceNpcSpawnRegistrationState.PENDING_UPDATE,
            "stale receipt registered a newer block revision");
        check(data.findAcknowledgement(fixture.pending.spawnPointId()) != null,
            "stale receipt was consumed against a newer revision");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void permanentFailureAndRedactedCollisionRepairConvergeSafely(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PendingFixture permanent = pendingPost(helper, new BlockPos(1, 1, 1), 5L);
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(level);
        check(data.markPermanentFailure(permanent.pending.token(), "invalid_city"),
            "could not persist permanent failure");
        permanent.post.serverTick();
        check(permanent.post.getRegistrationState() == ServiceNpcSpawnRegistrationState.ERROR
                && "invalid_city".equals(permanent.post.getLastErrorCode()),
            "permanent failure was not visible on the exact block");

        BlockPos repairRelative = new BlockPos(3, 1, 1);
        PendingFixture repair = pendingPost(helper, repairRelative, 6L);
        UUID oldId = repair.pending.spawnPointId();
        UUID oldOperationId = repair.pending.operationId();
        UUID city = repair.pending.cityPublicId();
        CompoundTag cached = repair.post.saveCustomOnly(level.registryAccess());
        cached.putUUID("AssignedNpcPublicId", UUID.randomUUID());
        cached.putString("AssignedNpcDisplayName", "Stale Assignment");
        cached.putLong("AssignmentRevision", 9L);
        cached.putLong("LastSuccessfulSyncEpochMillis", 8_000L);
        cached.putUUID("LastAcknowledgedOperationId", UUID.randomUUID());
        cached.putLong("LastAcknowledgedRecordedAtEpochMillis", 7_000L);
        repair.post.loadCustomOnly(cached, level.registryAccess());

        ServiceNpcSpawnClaimData claims = ServiceNpcSpawnClaimData.get(level);
        ServiceNpcSpawnLocation repairLocation = repair.pending.location();
        ServiceNpcSpawnLocation canonicalElsewhere = new ServiceNpcSpawnLocation(
            repairLocation.worldName(), repairLocation.dimension(),
            repairLocation.pos().offset(4, 0, 0)
        );
        check(claims.releaseIfMatches(oldId, repairLocation),
            "could not release duplicate-location A claim fixture");
        check(claims.claim(oldId, canonicalElsewhere) == ServiceNpcSpawnClaimData.ClaimResult.CLAIMED,
            "could not establish canonical A claim elsewhere");

        ServiceNpcSpawnCollisionEvidence evidence = new ServiceNpcSpawnCollisionEvidence(
            ServiceNpcSpawnCollisionEvidence.CollisionKind.REDACTED, true, null, 6_000L
        );
        check(data.markCollisionRepair(
                repair.pending.token(), evidence, "uuid_collision_pending_repair"),
            "could not persist collision-repair disposition");
        repair.post.serverTick();

        UUID replacementId = repair.post.getSpawnPointId();
        check(replacementId != null && !replacementId.equals(oldId),
            "redacted collision did not generate a replacement UUID");
        ServiceNpcSpawnPendingRecord replacement = data.findPending(replacementId);
        check(replacement != null
                && replacement.disposition() == ServiceNpcSpawnPendingDisposition.READY,
            "confirmed local replacement did not become READY");
        check(replacement.configurationRevision() == 1L
                && repair.post.getConfigurationRevision() == 1L,
            "replacement revision did not reset to one");
        check(replacement.operationId() != oldOperationId,
            "replacement reused the colliding operation ID");
        check(oldId.equals(replacement.supersedesSpawnPointId())
                && replacement.collisionRepairCount() == 1,
            "replacement lineage or repair count was not preserved");
        check(city.equals(repair.post.getCityPublicId())
                && repair.pending.serviceNpcTypeKey().equals(repair.post.getServiceNpcTypeKey())
                && repair.pending.enabled() == repair.post.isEnabled(),
            "collision repair did not preserve current configuration");
        check(repair.post.getRegistrationState() == ServiceNpcSpawnRegistrationState.PENDING_REGISTRATION
                && repair.post.getLastErrorCode() == null,
            "replacement did not enter clean PENDING_REGISTRATION");
        check(repair.post.getAssignedNpcPublicId() == null
                && repair.post.getAssignedNpcDisplayName() == null
                && repair.post.getAssignmentRevision() == 0L
                && repair.post.getLastSuccessfulSyncEpochMillis() == null
                && repair.post.getLastAcknowledgedOperationId() == null
                && repair.post.getLastAcknowledgedRecordedAtEpochMillis() == null,
            "replacement retained assignment or acknowledgement cache");
        check(claims.claimMatches(oldId, canonicalElsewhere),
            "repair altered canonical A claim at another location");
        check(claims.claimMatches(replacementId, repairLocation),
            "replacement B claim was not created at the repaired location");
        check(data.findPending(oldId) == null,
            "repair retained or created pending work for canonical A");
        check(data.snapshot().values().stream()
                .noneMatch(record -> record.spawnPointId().equals(oldId)
                    && record.operation() == ServiceNpcSpawnPendingOperation.REMOVE),
            "repair created a REMOVE for canonical A");

        check(data.acknowledgeSuccess(
                replacement.token(),
                successResponse(replacement, ServiceNpcSpawnOutcome.APPLIED, 9_000L)),
            "replacement UPSERT could not use normal exact acknowledgement");
        ServiceNpcSpawnReceiptReconciler.reconcileLoadedBlock(level, repair.post);
        ServiceNpcSpawnReceiptReconciler.reconcileLoadedBlock(level, repair.post);
        check(repair.post.getRegistrationState() == ServiceNpcSpawnRegistrationState.REGISTERED,
            "replacement did not reach REGISTERED through normal receipt application");

        helper.setBlock(repairRelative, Blocks.AIR);
        ServiceNpcSpawnPendingRecord remove = data.findPending(replacementId);
        check(remove != null && remove.operation() == ServiceNpcSpawnPendingOperation.REMOVE,
            "destruction after repair did not create REMOVE for B");
        check(data.findPending(oldId) == null,
            "destruction after repair created work for canonical A");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void stagedReplacementReplaysAcrossBlockEntityReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        PendingFixture fixture = pendingPost(helper, relative, 10L);
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(level);
        ServiceNpcSpawnCollisionEvidence evidence = new ServiceNpcSpawnCollisionEvidence(
            ServiceNpcSpawnCollisionEvidence.CollisionKind.TOMBSTONED, true, null, 10_000L
        );
        check(data.markCollisionRepair(
                fixture.pending.token(), evidence, "uuid_collision_pending_repair"),
            "could not create TOMBSTONED collision fixture");
        ServiceNpcSpawnPendingRecord source = data.findPending(fixture.pending.spawnPointId());
        UUID replacementId = UUID.randomUUID();
        UUID replacementOperationId = UUID.randomUUID();
        ServiceNpcSpawnPendingData.CollisionReplacementStage stage =
            data.stageCollisionReplacement(
                source.token(), evidence, replacementId, replacementOperationId,
                source.location(), source.cityPublicId(), source.serviceNpcTypeKey(),
                source.enabled(), 10_001L
            );
        ServiceNpcSpawnPendingRecord staged = stage.replacement();
        check(stage.result() == ServiceNpcSpawnPendingData.MutationResult.ACCEPTED && staged != null,
            "could not stage Phase-B replay fixture");

        CompoundTag savedA = fixture.post.saveCustomOnly(level.registryAccess());
        level.removeBlockEntity(absolute);
        ServiceNpcSpawnBlockEntity reloaded = new ServiceNpcSpawnBlockEntity(
            absolute, BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get().defaultBlockState()
        );
        reloaded.loadCustomOnly(savedA, level.registryAccess());
        level.setBlockEntity(reloaded);
        reloaded.serverTick();

        check(replacementId.equals(reloaded.getSpawnPointId()),
            "staged A-to-B replay generated or selected a different UUID");
        ServiceNpcSpawnPendingRecord ready = data.findPending(replacementId);
        check(ready != null
                && ready.operationId().equals(replacementOperationId)
                && ready.disposition() == ServiceNpcSpawnPendingDisposition.READY,
            "staged replacement did not retain its operation ID and become READY");
        check(ServiceNpcSpawnClaimData.get(level).claimMatches(replacementId, staged.location()),
            "replay did not reconcile the B claim");
        check(!ServiceNpcSpawnClaimData.get(level)
                .claimMatches(fixture.pending.spawnPointId(), staged.location()),
            "replay did not release exact local A claim");
        reloaded.serverTick();
        check(replacementId.equals(reloaded.getSpawnPointId())
                && replacementOperationId.equals(data.findPending(replacementId).operationId()),
            "repeated reconciliation was not idempotent");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fourthCollisionFailsWithoutGeneratingAnotherUuid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PendingFixture fixture = pendingPost(helper, new BlockPos(1, 1, 1), 20L);
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(level);
        UUID previousId = fixture.pending.spawnPointId();

        for (int expectedCount = 1; expectedCount <= 3; expectedCount++) {
            ServiceNpcSpawnPendingRecord current = data.findPending(previousId);
            ServiceNpcSpawnCollisionEvidence evidence = new ServiceNpcSpawnCollisionEvidence(
                ServiceNpcSpawnCollisionEvidence.CollisionKind.REDACTED, true, null,
                20_000L + expectedCount
            );
            check(data.markCollisionRepair(
                    current.token(), evidence, "uuid_collision_pending_repair"),
                "could not persist repeated collision " + expectedCount);
            fixture.post.serverTick();
            UUID nextId = fixture.post.getSpawnPointId();
            check(nextId != null && !nextId.equals(previousId),
                "collision " + expectedCount + " did not generate a replacement");
            ServiceNpcSpawnPendingRecord replacement = data.findPending(nextId);
            check(replacement != null
                    && replacement.collisionRepairCount() == expectedCount
                    && replacement.disposition() == ServiceNpcSpawnPendingDisposition.READY,
                "collision " + expectedCount + " did not converge to bounded READY state");
            previousId = nextId;
        }

        ServiceNpcSpawnPendingRecord third = data.findPending(previousId);
        ServiceNpcSpawnCollisionEvidence fourthEvidence = new ServiceNpcSpawnCollisionEvidence(
            ServiceNpcSpawnCollisionEvidence.CollisionKind.REDACTED, true, null, 20_100L
        );
        check(data.markCollisionRepair(
                third.token(), fourthEvidence, "uuid_collision_pending_repair"),
            "could not persist fourth collision fixture");
        UUID exhaustedId = previousId;
        fixture.post.serverTick();
        ServiceNpcSpawnPendingRecord exhausted = data.findPending(exhaustedId);
        check(exhaustedId.equals(fixture.post.getSpawnPointId()),
            "fourth collision generated a forbidden replacement UUID");
        check(exhausted != null
                && exhausted.disposition() == ServiceNpcSpawnPendingDisposition.PERMANENT_FAILURE
                && "uuid_collision_repair_exhausted".equals(exhausted.lastFailureCode()),
            "fourth collision did not become permanent repair exhaustion");
        check(fixture.post.getRegistrationState() == ServiceNpcSpawnRegistrationState.ERROR
                && "uuid_collision_repair_exhausted".equals(fixture.post.getLastErrorCode()),
            "repair exhaustion was not visible on the exact block");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void sameLocationLiveCollisionFailsWithoutRekey(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PendingFixture fixture = pendingPost(helper, new BlockPos(1, 1, 1), 30L);
        UUID serverKey = UUID.randomUUID();
        ServiceNpcSpawnLocation location = fixture.pending.location();
        ServiceNpcSpawnCollisionEvidence evidence = new ServiceNpcSpawnCollisionEvidence(
            ServiceNpcSpawnCollisionEvidence.CollisionKind.LIVE,
            true,
            new ServiceNpcSpawnCanonicalLocation(
                serverKey, "Different Diagnostic Name", location.dimension(),
                location.pos().getX(), location.pos().getY(), location.pos().getZ()
            ),
            30_000L
        );
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(level);
        check(data.markCollisionRepair(
                fixture.pending.token(), evidence, "uuid_collision_pending_repair"),
            "could not persist same-location LIVE collision");
        ServiceNpcSpawnCollisionRepairCoordinator.processExactForGameTest(
            level.getServer(), fixture.pending.spawnPointId(), serverKey
        );
        ServiceNpcSpawnPendingRecord failed = data.findPending(fixture.pending.spawnPointId());
        check(fixture.pending.spawnPointId().equals(fixture.post.getSpawnPointId()),
            "same-location LIVE collision changed the UUID");
        check(failed != null
                && failed.disposition() == ServiceNpcSpawnPendingDisposition.PERMANENT_FAILURE
                && "uuid_collision_same_location".equals(failed.lastFailureCode()),
            "same-location LIVE collision did not fail safely");
        check(ServiceNpcSpawnClaimData.get(level)
                .claimMatches(fixture.pending.spawnPointId(), fixture.pending.location()),
            "same-location LIVE failure altered the exact claim");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unloadedCollisionRepairDefersWithoutForceLoading(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = new BlockPos(19_000_000, 64, 19_000_000);
        ServiceNpcSpawnPendingRecord pending = new ServiceNpcSpawnPendingRecord(
            ServiceNpcSpawnPendingOperation.UPSERT,
            UUID.randomUUID(),
            ModConfig.SHARD_NAME,
            new ServiceNpcSpawnLocation(
                level.getServer().getWorldData().getLevelName(),
                level.dimension().location(),
                far
            ),
            UUID.randomUUID(),
            "bank_teller",
            true,
            1L,
            40_000L
        );
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(level);
        check(data.put(pending) == ServiceNpcSpawnPendingData.MutationResult.ACCEPTED,
            "could not create unloaded collision-repair fixture");
        ServiceNpcSpawnPendingRecord stored = data.findPending(pending.spawnPointId());
        ServiceNpcSpawnCollisionEvidence evidence = new ServiceNpcSpawnCollisionEvidence(
            ServiceNpcSpawnCollisionEvidence.CollisionKind.REDACTED, true, null, 40_001L
        );
        check(data.markCollisionRepair(
                stored.token(), evidence, "uuid_collision_pending_repair"),
            "could not persist unloaded collision evidence");
        ServiceNpcSpawnPendingRecord before = data.findPending(stored.spawnPointId());
        check(!level.hasChunk(far.getX() >> 4, far.getZ() >> 4),
            "far collision fixture chunk unexpectedly started loaded");

        ServiceNpcSpawnCollisionRepairCoordinator.processExactForGameTest(
            level.getServer(), stored.spawnPointId(), UUID.randomUUID()
        );

        check(!level.hasChunk(far.getX() >> 4, far.getZ() >> 4),
            "collision repair force-loaded an unavailable chunk");
        check(before.equals(data.findPending(stored.spawnPointId())),
            "unloaded collision repair mutated its durable Phase-A record");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void stagedReplacementNeverStealsConflictingClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PendingFixture fixture = pendingPost(helper, new BlockPos(1, 1, 1), 50_000L);
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(level);
        ServiceNpcSpawnCollisionEvidence evidence = new ServiceNpcSpawnCollisionEvidence(
            ServiceNpcSpawnCollisionEvidence.CollisionKind.TOMBSTONED, true, null, 50_001L
        );
        check(data.markCollisionRepair(
                fixture.pending.token(), evidence, "uuid_collision_pending_repair"),
            "could not create claim-conflict collision fixture");
        ServiceNpcSpawnPendingRecord source = data.findPending(fixture.pending.spawnPointId());
        UUID replacementId = UUID.randomUUID();
        ServiceNpcSpawnPendingData.CollisionReplacementStage stage =
            data.stageCollisionReplacement(
                source.token(), evidence, replacementId, UUID.randomUUID(),
                source.location(), source.cityPublicId(), source.serviceNpcTypeKey(),
                source.enabled(), 50_002L
            );
        ServiceNpcSpawnPendingRecord staged = stage.replacement();
        check(stage.result() == ServiceNpcSpawnPendingData.MutationResult.ACCEPTED && staged != null,
            "could not stage claim-conflict replacement");
        ServiceNpcSpawnLocation elsewhere = new ServiceNpcSpawnLocation(
            staged.location().worldName(), staged.location().dimension(),
            staged.location().pos().offset(5, 0, 0)
        );
        ServiceNpcSpawnClaimData claims = ServiceNpcSpawnClaimData.get(level);
        check(claims.claim(replacementId, elsewhere) == ServiceNpcSpawnClaimData.ClaimResult.CLAIMED,
            "could not reserve replacement UUID elsewhere");

        fixture.post.serverTick();

        ServiceNpcSpawnPendingRecord failed = data.findPending(replacementId);
        check(failed != null
                && failed.disposition() == ServiceNpcSpawnPendingDisposition.PERMANENT_FAILURE
                && "collision_repair_claim_conflict".equals(failed.lastFailureCode()),
            "conflicting B claim did not permanently block staged delivery");
        check(claims.claimMatches(replacementId, elsewhere),
            "repair stole the replacement UUID claim");
        check(fixture.pending.spawnPointId().equals(fixture.post.getSpawnPointId()),
            "claim-conflicted repair changed the block UUID");
        check(claims.claimMatches(fixture.pending.spawnPointId(), fixture.pending.location()),
            "claim-conflicted repair released A prematurely");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void destructionBeforeCollisionStagingLeavesRemoveAuthoritative(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        PendingFixture fixture = pendingPost(helper, relative, 60_000L);
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(helper.getLevel());
        ServiceNpcSpawnCollisionEvidence evidence = new ServiceNpcSpawnCollisionEvidence(
            ServiceNpcSpawnCollisionEvidence.CollisionKind.REDACTED, true, null, 60_001L
        );
        check(data.markCollisionRepair(
                fixture.pending.token(), evidence, "uuid_collision_pending_repair"),
            "could not create pre-destruction collision fixture");
        helper.setBlock(relative, Blocks.AIR);
        ServiceNpcSpawnPendingRecord remove = data.findPending(fixture.pending.spawnPointId());
        check(remove != null && remove.operation() == ServiceNpcSpawnPendingOperation.REMOVE,
            "destruction before staging did not supersede collision with REMOVE A");
        check(data.snapshot().values().stream()
                .noneMatch(record -> fixture.pending.spawnPointId().equals(record.supersedesSpawnPointId())),
            "destruction before staging generated a replacement UUID");
        helper.succeed();
    }

    private static void verifyProcessorBoundsShutdownAndReplacementThenDestroy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        String shard = "gametest-lifecycle-bounds";
        List<PendingFixture> fixtures = List.of(
            pendingPost(helper, new BlockPos(3, 1, 3), 100L, shard),
            pendingPost(helper, new BlockPos(4, 1, 3), 101L, shard),
            pendingPost(helper, new BlockPos(5, 1, 3), 102L, shard)
        );
        List<ControlledSubmission> handles = new ArrayList<>();
        List<ServiceNpcSpawnOperationRequest> submitted = new ArrayList<>();
        ServiceNpcSpawnDeliveryProcessor processor =
            ServiceNpcSpawnDeliveryProcessor.replaceForGameTest(
                server,
                (activeServer, request) -> {
                    submitted.add(request);
                    ControlledSubmission handle = new ControlledSubmission();
                    handles.add(handle);
                    return handle;
                },
                () -> 8_000L,
                shard
            );
        check(ServiceNpcSpawnDeliveryProcessor.start(server) == processor,
            "starting an active server created a second processor");
        check(ServiceNpcSpawnDeliveryProcessor.activeProcessorCount() == 1,
            "server did not retain exactly one active processor");
        for (int tick = 0; tick < ServiceNpcSpawnDeliveryProcessor.TICK_CADENCE - 1; tick++) {
            ServiceNpcSpawnDeliveryProcessor.tick(server);
        }
        check(submitted.isEmpty(), "processor selected work before the 20-tick cadence elapsed");
        ServiceNpcSpawnDeliveryProcessor.tick(server);
        check(submitted.size() == ServiceNpcSpawnDeliveryProcessor.MAX_IN_FLIGHT,
            "processor did not enforce the two-request in-flight limit");
        check(processor.inFlightCount() == ServiceNpcSpawnDeliveryProcessor.MAX_IN_FLIGHT,
            "runtime in-flight tracking exceeded its configured bound");
        check(ServiceNpcSpawnPendingData.get(level).snapshot()
                .get(fixtures.get(2).pending.spawnPointId()).attemptCount() == 0,
            "processor submitted beyond available in-flight capacity");

        ServiceNpcSpawnDeliveryProcessor.stop(server);
        check(processor.inFlightCount() == 0, "processor retained entries after shutdown cancellation");
        check(handles.stream().allMatch(handle -> handle.future.isCancelled()),
            "shutdown did not request cancellation for every active handle");
        processor.processNowForGameTest();
        ServiceNpcSpawnDeliveryProcessor.tick(server);
        check(submitted.size() == ServiceNpcSpawnDeliveryProcessor.MAX_IN_FLIGHT,
            "stopped processor continued selecting work");

        String replacementShard = "gametest-block-replacement";
        BlockPos replacementRelative = new BlockPos(6, 1, 3);
        PendingFixture replacement = pendingPost(
            helper, replacementRelative, 200L, replacementShard);
        ControlledSubmission replacementHandle = new ControlledSubmission();
        List<ServiceNpcSpawnOperationRequest> replacementRequests = new ArrayList<>();
        ServiceNpcSpawnDeliveryProcessor replacementProcessor =
            ServiceNpcSpawnDeliveryProcessor.replaceForGameTest(
                server,
                (activeServer, request) -> {
                    replacementRequests.add(request);
                    return replacementHandle;
                },
                () -> 9_000L,
                replacementShard
            );
        replacementProcessor.processNowForGameTest();
        check(replacementRequests.size() == 1, "replacement fixture did not enter flight");
        helper.setBlock(replacementRelative, Blocks.STONE);
        replacementHandle.future.complete(success(
            replacementRequests.getFirst(), ServiceNpcSpawnOutcome.APPLIED, 9_000L
        ));
        helper.runAfterDelay(3, () -> {
            check(level.getBlockState(helper.absolutePos(replacementRelative)).is(Blocks.STONE),
                "obsolete UPSERT completion mutated the replacement block");
            ServiceNpcSpawnPendingRecord current = ServiceNpcSpawnPendingData.get(level)
                .snapshot().get(replacement.pending.spawnPointId());
            check(current != null && current.operation() == ServiceNpcSpawnPendingOperation.REMOVE,
                "block replacement did not leave REMOVE authoritative");
            check(ServiceNpcSpawnPendingData.get(level)
                    .findAcknowledgement(replacement.pending.spawnPointId()) == null,
                "obsolete replacement completion created a receipt");
            destroyDuringInflightUpsertLeavesRemoveAuthoritative(helper);
        });
    }

    private static void destroyDuringInflightUpsertLeavesRemoveAuthoritative(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtomicLong clock = new AtomicLong(7_000L);
        PendingFixture fixture = pendingPost(
            helper, new BlockPos(3, 1, 1), 0L, CONTROLLED_SHARD_TWO);
        ControlledSubmission controlled = new ControlledSubmission();
        List<ServiceNpcSpawnOperationRequest> submitted = new ArrayList<>();
        ServiceNpcSpawnDeliveryProcessor processor =
            ServiceNpcSpawnDeliveryProcessor.replaceForGameTest(
                level.getServer(),
                (server, request) -> {
                    submitted.add(request);
                    return controlled;
                },
                clock::get,
                CONTROLLED_SHARD_TWO
            );
        processor.processNowForGameTest();
        check(submitted.size() == 1 && processor.inFlightCount() == 1,
            "fixture UPSERT did not enter in-flight tracking");

        helper.setBlock(new BlockPos(3, 1, 1), Blocks.AIR);
        ServiceNpcSpawnPendingRecord remove =
            ServiceNpcSpawnPendingData.get(level).snapshot().get(fixture.pending.spawnPointId());
        check(remove != null && remove.operation() == ServiceNpcSpawnPendingOperation.REMOVE,
            "destruction did not replace in-flight UPSERT with terminal REMOVE");
        controlled.future.complete(success(
            submitted.getFirst(), ServiceNpcSpawnOutcome.APPLIED, clock.get()
        ));

        helper.runAfterDelay(3, () -> {
            ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(level);
            ServiceNpcSpawnPendingRecord current = data.snapshot().get(fixture.pending.spawnPointId());
            check(current != null && current.operation() == ServiceNpcSpawnPendingOperation.REMOVE,
                "obsolete UPSERT completion removed the authoritative REMOVE");
            check(data.findAcknowledgement(fixture.pending.spawnPointId()) == null,
                "obsolete UPSERT completion created a receipt");
            resetProcessor(level);
            helper.succeed();
        });
    }

    private static PendingFixture pendingPost(
            GameTestHelper helper, BlockPos relative, long recordedAt
    ) {
        return pendingPost(helper, relative, recordedAt, ModConfig.SHARD_NAME);
    }

    private static PendingFixture pendingPost(
            GameTestHelper helper, BlockPos relative, long recordedAt, String shardName
    ) {
        ServerLevel level = helper.getLevel();
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        UUID id = requireId(post);
        UUID city = UUID.randomUUID();
        CompoundTag configured = post.saveCustomOnly(level.registryAccess());
        configured.putUUID("CityPublicId", city);
        configured.putString("ServiceNpcTypeKey", "bank_teller");
        configured.putBoolean("Enabled", true);
        configured.putLong("ConfigurationRevision", 1L);
        configured.putString("RegistrationState", ServiceNpcSpawnRegistrationState.PENDING_REGISTRATION.name());
        post.loadCustomOnly(configured, level.registryAccess());
        post.serverTick();
        ServiceNpcSpawnPendingRecord incoming = new ServiceNpcSpawnPendingRecord(
            ServiceNpcSpawnPendingOperation.UPSERT,
            id,
            shardName,
            post.currentLocation(level),
            city,
            "bank_teller",
            true,
            1L,
            recordedAt
        );
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(level);
        ServiceNpcSpawnPendingData.MutationResult result = data.put(incoming);
        check(result == ServiceNpcSpawnPendingData.MutationResult.ACCEPTED,
            "could not create pending GameTest fixture: " + result);
        return new PendingFixture(post, data.snapshot().get(id));
    }

    private static ServiceNpcSpawnClientResult.Protocol success(
            ServiceNpcSpawnOperationRequest request,
            ServiceNpcSpawnOutcome outcome,
            long acknowledgedAt
    ) {
        return new ServiceNpcSpawnClientResult.Protocol(
            new ServiceNpcSpawnProtocolResponse(
                1, true, outcome, false, request.operationId(), request.spawnUuid(),
                request.sourceRevision(), request.sourceRevision(), request.sourceRevision(),
                request.operation() == ServiceNpcSpawnOperation.UPSERT
                    ? ServiceNpcSpawnProtocolResponse.RegistrationState.LIVE
                    : ServiceNpcSpawnProtocolResponse.RegistrationState.REMOVED,
                Instant.ofEpochMilli(acknowledgedAt), true, null, null, null, null, null
            ),
            ServiceNpcSpawnClientResult.Disposition.SUCCESS
        );
    }

    private static ServiceNpcSpawnProtocolResponse successResponse(
            ServiceNpcSpawnPendingRecord pending,
            ServiceNpcSpawnOutcome outcome,
            long acknowledgedAt
    ) {
        return success(ServiceNpcSpawnPendingRequestAdapter.adapt(pending), outcome, acknowledgedAt).response();
    }

    private static ServiceNpcSpawnDeliveryProcessor.Submission completedSubmission(
            ServiceNpcSpawnClientResult result
    ) {
        return new ServiceNpcSpawnDeliveryProcessor.Submission() {
            private final CompletableFuture<ServiceNpcSpawnClientResult> future =
                CompletableFuture.completedFuture(result);
            @Override public CompletableFuture<ServiceNpcSpawnClientResult> future() { return future; }
            @Override public boolean cancel() { return false; }
        };
    }

    private static void resetProcessor(ServerLevel level) {
        ServiceNpcSpawnDeliveryProcessor.stop(level.getServer());
        ServiceNpcSpawnDeliveryProcessor.start(level.getServer());
    }

    private record PendingFixture(
        ServiceNpcSpawnBlockEntity post, ServiceNpcSpawnPendingRecord pending
    ) {}

    private static final class ControlledSubmission implements ServiceNpcSpawnDeliveryProcessor.Submission {
        private final CompletableFuture<ServiceNpcSpawnClientResult> future = new CompletableFuture<>();
        @Override public CompletableFuture<ServiceNpcSpawnClientResult> future() { return future; }
        @Override public boolean cancel() { return future.cancel(true); }
    }

    private static ServiceNpcSpawnBlockEntity placePost(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get());
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(relative);
        ServiceNpcSpawnBlockEntity post = requirePost(level, absolute);
        BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get().setPlacedBy(
                level,
                absolute,
                level.getBlockState(absolute),
                null,
                ItemStack.EMPTY
        );
        return post;
    }

    private static ServiceNpcSpawnBlockEntity placeConfiguredPost(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        CompoundTag configured = post.saveCustomOnly(level.registryAccess());
        configured.putUUID("CityPublicId", UUID.randomUUID());
        configured.putString("ServiceNpcTypeKey", "banker");
        configured.putBoolean("Enabled", false);
        configured.putLong("ConfigurationRevision", 12L);
        configured.putString("RegistrationState", ServiceNpcSpawnRegistrationState.REGISTERED.name());
        configured.putUUID("AssignedNpcPublicId", UUID.randomUUID());
        configured.putString("AssignedNpcDisplayName", "Copied Banker");
        configured.putLong("AssignmentRevision", 7L);
        configured.putLong("LastSuccessfulSyncEpochMillis", 123456789L);
        post.loadCustomOnly(configured, level.registryAccess());
        post.serverTick();
        return post;
    }

    private static void assertFreshCopy(
            UUID originalId,
            ServiceNpcSpawnBlockEntity original,
            ServiceNpcSpawnBlockEntity copy
    ) {
        UUID copiedId = requireId(copy);
        check(originalId.equals(original.getSpawnPointId()), "copy operation changed the canonical post UUID");
        check(!originalId.equals(copiedId), "copied post retained the canonical post UUID");
        check(copy.getCityPublicId() == null, "copied post retained its city configuration");
        check(copy.getServiceNpcTypeKey() == null, "copied post retained its Service NPC type");
        check(copy.isEnabled(), "copied post did not reset enabled to the new-post default");
        check(copy.getConfigurationRevision() == 0L, "copied post retained its configuration revision");
        check(copy.getRegistrationState() == ServiceNpcSpawnRegistrationState.UNCONFIGURED,
                "copied post retained its registration state");
        check(copy.getAssignedNpcPublicId() == null && copy.getAssignedNpcDisplayName() == null,
                "copied post retained its assignment");
        check(copy.getAssignmentRevision() == 0L && copy.getLastSuccessfulSyncEpochMillis() == null,
                "copied post retained assignment/sync revisions");
    }

    private static void assertRemove(ServerLevel level, UUID id) {
        ServiceNpcSpawnPendingRecord pending = ServiceNpcSpawnPendingData.get(level).snapshot().get(id);
        check(pending != null, "true destruction did not create pending work for " + id);
        check(pending.operation() == ServiceNpcSpawnPendingOperation.REMOVE,
                "true destruction did not create terminal REMOVE work for " + id);
        check(ServiceNpcSpawnClaimData.get(level).find(id) == null,
                "true destruction did not release the identity claim for " + id);
    }

    private static ServiceNpcSpawnBlockEntity requirePost(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity post) return post;
        throw new IllegalStateException("missing Service NPC spawn block entity at " + pos);
    }

    private static UUID requireId(ServiceNpcSpawnBlockEntity post) {
        UUID id = post.getSpawnPointId();
        if (id != null) return id;
        throw new IllegalStateException("Service NPC spawn post has no UUID");
    }

    private static String coordinates(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
