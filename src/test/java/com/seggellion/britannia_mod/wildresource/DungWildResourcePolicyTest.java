package com.seggellion.britannia_mod.wildresource;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungWildResourcePolicyTest {
    private static final BlockPos TARGET = new BlockPos(8, 65, 8);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void supportSetIsClosedToDirtAndCoarseDirt() {
        FakeView view = new FakeView();
        for (BlockState accepted : new BlockState[] {
                Blocks.DIRT.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState()
        }) {
            view.states.put(TARGET.below(), accepted);
            assertTrue(WildResourcePlacementRules.isDungSupport(TARGET, view), accepted.toString());
        }

        for (BlockState rejected : new BlockState[] {
                Blocks.AIR.defaultBlockState(),
                Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.STONE.defaultBlockState(),
                Blocks.SAND.defaultBlockState(),
                Blocks.FARMLAND.defaultBlockState(),
                Blocks.MUD.defaultBlockState(),
                Blocks.PODZOL.defaultBlockState()
        }) {
            view.states.put(TARGET.below(), rejected);
            assertFalse(WildResourcePlacementRules.isDungSupport(TARGET, view), rejected.toString());
        }
    }

    @Test
    void targetMustAlsoBeDryReplaceableAndWithoutBlockEntity() {
        FakeView view = new FakeView();
        view.states.put(TARGET.below(), Blocks.DIRT.defaultBlockState());
        assertTrue(WildResourcePlacementRules.isDungSupport(TARGET, view));
        assertTrue(WildResourcePlacementRules.isSafeTarget(TARGET, view));

        view.states.put(TARGET, Blocks.WATER.defaultBlockState());
        assertFalse(WildResourcePlacementRules.isSafeTarget(TARGET, view));
        view.states.put(TARGET, Blocks.AIR.defaultBlockState());
        view.hasBlockEntity = true;
        assertFalse(WildResourcePlacementRules.isSafeTarget(TARGET, view));
    }

    @Test
    void entryUsesAshCadenceAndClassifiesPersistedNodes() {
        WildResourceEntry entry = WildResourceEntries.createDungEntry();
        WildResourceTuning tuning = entry.tuning();

        assertEquals(WildResourceEntries.DUNG, entry.id());
        assertEquals(1, entry.spawnWeight());
        assertEquals(2, entry.maxNodesPerChunk());
        assertEquals(20 * 60 * 3, tuning.attemptIntervalMinTicks());
        assertEquals(20 * 60 * 6, tuning.attemptIntervalMaxTicks());
        assertEquals(20 * 60 * 20, tuning.respawnCooldownMinTicks());
        assertEquals(20 * 60 * 40, tuning.respawnCooldownMaxTicks());
        assertEquals(4, tuning.maxRandomProbes());
        assertEquals(8, tuning.minimumSameTypeSpacing());

        assertEquals(WildResourceEntry.ExistingNodeState.MISSING_OR_REPLACED,
                WildResourceEntries.classifyDungNode(false, true));
        assertEquals(WildResourceEntry.ExistingNodeState.OWNED_INVALID,
                WildResourceEntries.classifyDungNode(true, false));
        assertEquals(WildResourceEntry.ExistingNodeState.VALID,
                WildResourceEntries.classifyDungNode(true, true));
    }

    @Test
    void invalidTrackedDungIsRemovedFromLedgerAndPutOnCooldown() {
        WildResourceSavedData data = new WildResourceSavedData();
        data.registerNode(new WildResourceNode(WildResourceEntries.DUNG, TARGET, 10L));
        WildResourceNode node = data.nodeAt(TARGET).orElseThrow();

        assertEquals(WildResourceReconciliation.Outcome.REMOVE_OWNED_BLOCK,
                WildResourceReconciliation.reconcile(
                        data,
                        node,
                        WildResourceEntries.createDungEntry(),
                        WildResourceEntries.classifyDungNode(true, false),
                        500L
                ));
        assertTrue(data.nodeAt(TARGET).isEmpty());
        assertEquals(500L, data.nextAttempt(new ChunkPos(TARGET), WildResourceEntries.DUNG));
    }

    @Test
    void schedulerRejectsAirborneAndWrongSupportBeforePlacement() {
        FakeView view = new FakeView();
        FakeAttemptContext context = new FakeAttemptContext();
        WildResourceEntry entry = schedulerEntry(view, context);

        for (BlockState rejected : new BlockState[] {
                Blocks.AIR.defaultBlockState(),
                Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.STONE.defaultBlockState(),
                Blocks.SAND.defaultBlockState()
        }) {
            view.states.put(TARGET.below(), rejected);
            context.reset();
            assertEquals(WildResourceSpawnScheduler.AttemptResult.NO_MATCH,
                    WildResourceSpawnScheduler.attempt(
                            entry, new ChunkPos(TARGET), 100L, context
                    ), rejected.toString());
            assertFalse(context.placed, rejected.toString());
        }

        view.states.put(TARGET.below(), Blocks.DIRT.defaultBlockState());
        context.reset();
        assertEquals(WildResourceSpawnScheduler.AttemptResult.PLACED,
                WildResourceSpawnScheduler.attempt(entry, new ChunkPos(TARGET), 100L, context));
        assertTrue(context.placed);
    }

    @Test
    void dungScheduleAndNodeIdentitySurviveSavedDataRoundTrip() {
        WildResourceSavedData original = new WildResourceSavedData();
        ChunkPos chunk = new ChunkPos(TARGET);
        original.scheduleAttempt(chunk, WildResourceEntries.DUNG, 321L);
        original.registerNode(new WildResourceNode(WildResourceEntries.DUNG, TARGET, 123L));

        CompoundTag encoded = original.save(new CompoundTag(), null);
        WildResourceSavedData decoded = WildResourceSavedData.load(encoded, null);
        assertEquals(321L, decoded.nextAttempt(chunk, WildResourceEntries.DUNG));
        assertEquals(WildResourceEntries.DUNG, decoded.nodeAt(TARGET).orElseThrow().resourceId());
    }

    private static WildResourceEntry schedulerEntry(FakeView view, FakeAttemptContext context) {
        return new WildResourceEntry(
                WildResourceEntries.DUNG,
                1,
                2,
                WildResourceEntries.DUNG_TUNING,
                (level, chunk, random) -> TARGET,
                (level, position) -> WildResourcePlacementRules.isSafeTarget(position, view),
                (level, position) -> WildResourcePlacementRules.isDungSupport(position, view),
                WildResourceEntry.PlacementRule.ALLOW,
                WildResourceEntry.PlacementRule.ALLOW,
                (level, position) -> {
                    context.placed = true;
                    return true;
                },
                WildResourceEntry.HarvestStrategy.DISABLED,
                WildResourceEntry.LootStrategy.NONE
        );
    }

    private static final class FakeView implements WildResourcePlacementRules.PlacementView {
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private boolean hasBlockEntity;

        @Override public BlockState stateAt(BlockPos position) {
            return states.getOrDefault(position, Blocks.AIR.defaultBlockState());
        }
        @Override public boolean hasBlockEntity(BlockPos position) { return hasBlockEntity; }
        @Override public FluidState getFluidState(BlockPos position) { return stateAt(position).getFluidState(); }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public BlockEntity getBlockEntity(BlockPos position) { return null; }
    }

    private static final class FakeAttemptContext implements WildResourceSpawnScheduler.AttemptContext {
        private final RandomSource random = RandomSource.create(18L);
        private boolean placed;

        void reset() {
            placed = false;
        }

        @Override public ServerLevel level() { return null; }
        @Override public RandomSource random() { return random; }
        @Override public boolean isChunkLoaded(ChunkPos chunk) { return true; }
        @Override public boolean isPositionLoaded(BlockPos position) { return true; }
        @Override public long nextAttempt(ChunkPos chunk, WildResourceEntry entry) { return 0L; }
        @Override public int countInChunk(ChunkPos chunk, WildResourceEntry entry) { return 0; }
        @Override public boolean spacingAllows(WildResourceEntry entry, BlockPos position) { return true; }
        @Override public boolean recordPlacement(WildResourceEntry entry, BlockPos position, long gameTime) {
            return true;
        }
        @Override public void schedule(ChunkPos chunk, WildResourceEntry entry, long nextAttempt) {
        }
    }
}
