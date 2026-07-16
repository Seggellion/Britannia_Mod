package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
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
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnClaim;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnClaimData;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnConfigurationValidator;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingData;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingOperation;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingRecord;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ServiceNpcSpawnGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

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
