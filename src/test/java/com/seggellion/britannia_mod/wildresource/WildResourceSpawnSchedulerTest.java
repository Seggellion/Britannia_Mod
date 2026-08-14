package com.seggellion.britannia_mod.wildresource;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildResourceSpawnSchedulerTest {
    private static final ChunkPos CHUNK = new ChunkPos(2, 3);
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("britannia_mod", "test");

    @Test
    void unloadedChunkIsNotProcessedOrRescheduled() {
        FakeContext context = new FakeContext();
        context.loaded = false;

        assertEquals(WildResourceSpawnScheduler.AttemptResult.UNLOADED,
                WildResourceSpawnScheduler.attempt(entry(4), CHUNK, 100, context));
        assertEquals(0, context.probes);
        assertEquals(0, context.schedules);
    }

    @Test
    void cooldownAndChunkCapAreRespected() {
        FakeContext context = new FakeContext();
        context.nextAttempt = 101;
        assertEquals(WildResourceSpawnScheduler.AttemptResult.COOLDOWN,
                WildResourceSpawnScheduler.attempt(entry(4), CHUNK, 100, context));

        context.nextAttempt = 100;
        context.count = 2;
        assertEquals(WildResourceSpawnScheduler.AttemptResult.CHUNK_CAP,
                WildResourceSpawnScheduler.attempt(entry(4), CHUNK, 100, context));
        assertEquals(1, context.schedules);
    }

    @Test
    void probesAreBoundedAndSuccessfulPlacementIsRecorded() {
        FakeContext failed = new FakeContext();
        failed.valid = false;
        assertEquals(WildResourceSpawnScheduler.AttemptResult.NO_MATCH,
                WildResourceSpawnScheduler.attempt(entry(4), CHUNK, 100, failed));
        assertEquals(4, failed.probes);

        FakeContext placed = new FakeContext();
        assertEquals(WildResourceSpawnScheduler.AttemptResult.PLACED,
                WildResourceSpawnScheduler.attempt(entry(4), CHUNK, 100, placed));
        assertEquals(1, placed.probes);
        assertTrue(placed.recorded);
        assertEquals(120, placed.nextAttempt);
    }

    private static WildResourceEntry entry(int probes) {
        return new WildResourceEntry(
                ID, 1, 2, new WildResourceTuning(20, 20, 40, 40, probes, 0),
                (level, chunk, random) -> {
                    FakeContext.active.probes++;
                    return chunk.getBlockAt(1, 64, 1);
                },
                (level, position) -> FakeContext.active.valid,
                WildResourceEntry.PlacementRule.ALLOW,
                WildResourceEntry.PlacementRule.ALLOW,
                WildResourceEntry.PlacementRule.ALLOW,
                (level, position) -> true,
                WildResourceEntry.HarvestStrategy.DISABLED,
                WildResourceEntry.LootStrategy.NONE
        );
    }

    private static final class FakeContext implements WildResourceSpawnScheduler.AttemptContext {
        private static FakeContext active;
        private final RandomSource random = RandomSource.create(4L);
        private boolean loaded = true;
        private boolean valid = true;
        private boolean recorded;
        private int probes;
        private int count;
        private int schedules;
        private long nextAttempt;

        private FakeContext() {
            active = this;
        }

        @Override public ServerLevel level() { return null; }
        @Override public RandomSource random() { return random; }
        @Override public boolean isChunkLoaded(ChunkPos chunk) { return loaded; }
        @Override public boolean isPositionLoaded(BlockPos position) { return loaded; }
        @Override public long nextAttempt(ChunkPos chunk, WildResourceEntry entry) { return nextAttempt; }
        @Override public int countInChunk(ChunkPos chunk, WildResourceEntry entry) { return count; }
        @Override public boolean spacingAllows(WildResourceEntry entry, BlockPos position) { return true; }
        @Override public boolean recordPlacement(WildResourceEntry entry, BlockPos position, long gameTime) {
            recorded = true;
            return true;
        }
        @Override public void schedule(ChunkPos chunk, WildResourceEntry entry, long nextAttempt) {
            schedules++;
            this.nextAttempt = nextAttempt;
        }
    }
}
