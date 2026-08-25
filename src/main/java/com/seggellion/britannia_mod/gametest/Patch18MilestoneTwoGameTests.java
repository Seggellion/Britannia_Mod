package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import com.seggellion.britannia_mod.wildresource.WildResourceEntries;
import com.seggellion.britannia_mod.wildresource.WildResourceEntry;
import com.seggellion.britannia_mod.wildresource.WildResourceManager;
import com.seggellion.britannia_mod.wildresource.WildResourceNode;
import com.seggellion.britannia_mod.wildresource.WildResourceSavedData;
import com.seggellion.britannia_mod.wildresource.WildResourceSpawnScheduler;
import com.seggellion.britannia_mod.wildresource.WildResources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class Patch18MilestoneTwoGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos SUPPORT = new BlockPos(1, 1, 1);
    private static final BlockPos TARGET = SUPPORT.above();

    private Patch18MilestoneTwoGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void schedulerPlacesAndRecordsDungOnDirt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(TARGET);
        WildResourceSavedData data = cleanNodeAndDrops(level, target);
        helper.setBlock(SUPPORT, Blocks.DIRT);
        helper.setBlock(TARGET, Blocks.AIR);

        WildResourceEntry configured = dungEntry();
        WildResourceEntry fixedCandidate = new WildResourceEntry(
                configured.id(),
                configured.spawnWeight(),
                configured.maxNodesPerChunk(),
                configured.tuning(),
                (ignoredLevel, ignoredChunk, ignoredRandom) -> target,
                configured.environmentRule(),
                configured.substrateRule(),
                configured.biomeRule(),
                configured.nearbyRule(),
                configured.existingNodeValidator(),
                configured.placementStrategy(),
                configured.harvestStrategy(),
                configured.lootStrategy()
        );
        ChunkPos chunk = new ChunkPos(target);
        data.scheduleAttempt(chunk, configured.id(), level.getGameTime());

        WildResourceSpawnScheduler.AttemptResult result = WildResourceSpawnScheduler.attempt(
                fixedCandidate,
                chunk,
                level.getGameTime(),
                new RuntimeAttemptContext(level, data)
        );
        check(result == WildResourceSpawnScheduler.AttemptResult.PLACED,
                "dung scheduler did not place on exposed dirt: " + result);
        helper.assertBlockPresent(BlockRegistry.DUNG.get(), TARGET);
        check(data.nodeAt(target).map(WildResourceNode::resourceId)
                        .filter(WildResourceEntries.DUNG::equals).isPresent(),
                "placed dung was not recorded in the WildResource ledger");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void adventureHarvestIsSingleWinnerAndStartsCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(TARGET);
        WildResourceSavedData data = placeTrackedDung(helper, Blocks.DIRT);
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, "patch18-dung-adventure");
        player.setGameMode(GameType.ADVENTURE);
        player.setPos(target.getX() + 0.5D, target.getY(), target.getZ() + 2.5D);

        PlayerInteractEvent.LeftClickBlock first = leftClick(player, target);
        NeoForge.EVENT_BUS.post(first);
        check(first.isCanceled(), "tracked dung did not own the Adventure left-click");
        check(level.getBlockState(target).isAir(), "Adventure harvest left dung in the world");
        check(data.nodeAt(target).isEmpty(), "Adventure harvest left the dung ledger node behind");
        check(data.nextAttempt(new ChunkPos(target), WildResourceEntries.DUNG)
                        != WildResourceSavedData.UNSCHEDULED,
                "Adventure harvest did not schedule dung respawn");

        PlayerInteractEvent.LeftClickBlock second = leftClick(player, target);
        NeoForge.EVENT_BUS.post(second);
        check(totalDung(level, player, target) == 1,
                "two Adventure attempts produced more than one dung item");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void survivalAndCreativeOrdinaryBreaksPreserveEconomyRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos survivalTarget = helper.absolutePos(TARGET);
        WildResourceSavedData data = placeTrackedDung(helper, Blocks.COARSE_DIRT);
        ServerPlayer survivor = ManagedResourceTestPlayers.survival(level, "patch18-dung-survival");
        survivor.setPos(survivalTarget.getX() + 0.5D, survivalTarget.getY(), survivalTarget.getZ() + 2.5D);
        survivor.setItemInHand(
                InteractionHand.MAIN_HAND,
                ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3)
        );

        check(survivor.gameMode.destroyBlock(survivalTarget), "Survival could not break tracked dung");
        check(data.nodeAt(survivalTarget).isEmpty(), "Survival break left the dung ledger node behind");
        check(totalDung(level, survivor, survivalTarget) == 1,
                "Survival tracked break did not yield exactly one dung");

        BlockPos creativeSupport = new BlockPos(4, 1, 1);
        BlockPos creativeRelative = creativeSupport.above();
        BlockPos creativeTarget = helper.absolutePos(creativeRelative);
        cleanNodeAndDrops(level, creativeTarget);
        helper.setBlock(creativeSupport, Blocks.DIRT);
        helper.setBlock(creativeRelative, BlockRegistry.DUNG.get());
        data.registerNode(new WildResourceNode(
                WildResourceEntries.DUNG, creativeTarget, level.getGameTime()
        ));
        ServerPlayer creative = ManagedResourceTestPlayers.survival(level, "patch18-dung-creative");
        creative.setGameMode(GameType.CREATIVE);
        creative.setPos(creativeTarget.getX() + 0.5D, creativeTarget.getY(), creativeTarget.getZ() + 2.5D);

        check(creative.gameMode.destroyBlock(creativeTarget), "Creative could not administratively remove dung");
        check(data.nodeAt(creativeTarget).isEmpty(), "Creative removal left the dung ledger node behind");
        check(totalDung(level, creative, creativeTarget) == 0,
                "Creative administrative removal created an economic dung drop");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void invalidSupportReconciliationRemovesDungWithoutLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(TARGET);
        WildResourceSavedData data = placeTrackedDung(helper, Blocks.DIRT);

        WildResourceManager.reconcileLoadedChunk(level, new ChunkPos(target));
        check(data.nodeAt(target).isPresent(), "valid dirt-supported dung was removed during reconciliation");
        helper.setBlock(SUPPORT, Blocks.STONE);
        WildResourceManager.reconcileLoadedChunk(level, new ChunkPos(target));

        check(level.getBlockState(target).isAir(), "unsupported tracked dung persisted after reconciliation");
        check(data.nodeAt(target).isEmpty(), "unsupported dung remained in the WildResource ledger");
        check(data.nextAttempt(new ChunkPos(target), WildResourceEntries.DUNG)
                        != WildResourceSavedData.UNSCHEDULED,
                "unsupported dung removal did not enter cooldown");
        check(totalDung(level, null, target) == 0,
                "support invalidation dropped dung instead of removing it administratively");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void fakePlayerAndForeignHouseCannotHarvestTrackedDung(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(TARGET);
        WildResourceSavedData data = placeTrackedDung(helper, Blocks.DIRT);
        FakePlayer machine = FakePlayerFactory.getMinecraft(level);
        machine.setPos(target.getX() + 0.5D, target.getY(), target.getZ() + 2.5D);
        machine.setItemInHand(
                InteractionHand.MAIN_HAND,
                ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3)
        );

        check(!machine.gameMode.destroyBlock(target), "fake player broke tracked dung");
        check(level.getBlockState(target).is(BlockRegistry.DUNG.get()) && data.nodeAt(target).isPresent(),
                "fake player harvested tracked dung");

        ServerPlayer stranger = ManagedResourceTestPlayers.survival(level, "patch18-dung-stranger");
        stranger.setGameMode(GameType.ADVENTURE);
        stranger.setPos(target.getX() + 0.5D, target.getY(), target.getZ() + 2.5D);
        AABB box = new AABB(helper.absolutePos(BlockPos.ZERO))
                .minmax(new AABB(helper.absolutePos(new BlockPos(6, 5, 6))));
        StructureRecord house = new StructureRecord(
                UUID.randomUUID(), box, box, UUID.randomUUID(),
                "small", "SMALL_BRICK", null, 0, level.dimension()
        );
        StructureRegionManager.registerStructure(house);
        try {
            PlayerInteractEvent.LeftClickBlock strangerClick = leftClick(stranger, target);
            NeoForge.EVENT_BUS.post(strangerClick);
            check(strangerClick.isCanceled(), "foreign-house tracked dung click fell through");
            check(level.getBlockState(target).is(BlockRegistry.DUNG.get()) && data.nodeAt(target).isPresent(),
                    "stranger harvested dung inside another player's house");
            check(totalDung(level, stranger, target) == 0,
                    "a denied tracked dung click produced an item");
        } finally {
            StructureRegionManager.unregisterStructure(house);
        }
        helper.succeed();
    }

    private static WildResourceEntry dungEntry() {
        return WildResources.registry().find(WildResourceEntries.DUNG)
                .orElseThrow(() -> new GameTestAssertException("dung WildResource entry is not registered"));
    }

    private static WildResourceSavedData placeTrackedDung(
            GameTestHelper helper,
            net.minecraft.world.level.block.Block support
    ) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(TARGET);
        WildResourceSavedData data = cleanNodeAndDrops(level, target);
        helper.setBlock(SUPPORT, support);
        helper.setBlock(TARGET, BlockRegistry.DUNG.get());
        check(data.registerNode(new WildResourceNode(
                        WildResourceEntries.DUNG, target, level.getGameTime())),
                "could not register tracked dung test node");
        return data;
    }

    private static WildResourceSavedData cleanNodeAndDrops(ServerLevel level, BlockPos target) {
        WildResourceSavedData data = WildResourceSavedData.get(level);
        data.removeNode(target);
        level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(3.0D))
                .forEach(ItemEntity::discard);
        return data;
    }

    private static PlayerInteractEvent.LeftClickBlock leftClick(ServerPlayer player, BlockPos target) {
        return new PlayerInteractEvent.LeftClickBlock(
                player, target, Direction.UP, PlayerInteractEvent.LeftClickBlock.Action.START
        );
    }

    private static int totalDung(ServerLevel level, ServerPlayer player, BlockPos target) {
        int inventory = player == null ? 0 : player.getInventory().countItem(ItemRegistry.DUNG.get());
        int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(3.0D)).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(ItemRegistry.DUNG.get()))
                .mapToInt(ItemStack::getCount)
                .sum();
        return inventory + dropped;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private record RuntimeAttemptContext(
            ServerLevel level,
            WildResourceSavedData data
    ) implements WildResourceSpawnScheduler.AttemptContext {
        @Override public RandomSource random() { return level.random; }
        @Override public boolean isChunkLoaded(ChunkPos chunk) {
            return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null;
        }
        @Override public boolean isPositionLoaded(BlockPos position) {
            ChunkPos chunk = new ChunkPos(position);
            return isChunkLoaded(chunk);
        }
        @Override public long nextAttempt(ChunkPos chunk, WildResourceEntry entry) {
            return data.nextAttempt(chunk, entry.id());
        }
        @Override public int countInChunk(ChunkPos chunk, WildResourceEntry entry) {
            return data.countInChunk(chunk, entry.id());
        }
        @Override public boolean spacingAllows(WildResourceEntry entry, BlockPos position) {
            return data.nodeAt(position).isEmpty()
                    && !data.hasSameTypeWithin(entry.id(), position, entry.tuning().minimumSameTypeSpacing());
        }
        @Override public boolean recordPlacement(WildResourceEntry entry, BlockPos position, long gameTime) {
            return data.registerNode(new WildResourceNode(entry.id(), position, gameTime));
        }
        @Override public void schedule(ChunkPos chunk, WildResourceEntry entry, long nextAttempt) {
            data.scheduleAttempt(chunk, entry.id(), nextAttempt);
        }
    }
}
