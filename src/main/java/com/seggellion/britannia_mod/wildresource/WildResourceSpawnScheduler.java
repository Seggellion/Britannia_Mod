package com.seggellion.britannia_mod.wildresource;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;

/** One bounded, deterministic scheduling attempt; world/event wiring lives in the manager. */
public final class WildResourceSpawnScheduler {
    private WildResourceSpawnScheduler() {
    }

    public static AttemptResult attempt(
            WildResourceEntry entry,
            ChunkPos chunk,
            long gameTime,
            AttemptContext context
    ) {
        if (!context.isChunkLoaded(chunk)) {
            return AttemptResult.UNLOADED;
        }
        if (context.nextAttempt(chunk, entry) > gameTime) {
            return AttemptResult.COOLDOWN;
        }
        if (context.countInChunk(chunk, entry) >= entry.maxNodesPerChunk()) {
            scheduleNext(entry, chunk, gameTime, context);
            return AttemptResult.CHUNK_CAP;
        }

        for (int probe = 0; probe < entry.tuning().maxRandomProbes(); probe++) {
            BlockPos candidate = entry.candidateGenerator().sample(context.level(), chunk, context.random());
            if (candidate == null || !context.isPositionLoaded(candidate)
                    || !context.spacingAllows(entry, candidate)
                    || !entry.isValidPlacement(context.level(), candidate)) {
                continue;
            }
            if (entry.placementStrategy().place(context.level(), candidate)
                    && context.recordPlacement(entry, candidate, gameTime)) {
                scheduleNext(entry, chunk, gameTime, context);
                return AttemptResult.PLACED;
            }
        }
        scheduleNext(entry, chunk, gameTime, context);
        return AttemptResult.NO_MATCH;
    }

    private static void scheduleNext(
            WildResourceEntry entry,
            ChunkPos chunk,
            long gameTime,
            AttemptContext context
    ) {
        context.schedule(chunk, entry, gameTime + entry.tuning().nextAttemptDelay(context.random()));
    }

    public enum AttemptResult {
        PLACED,
        NO_MATCH,
        CHUNK_CAP,
        COOLDOWN,
        UNLOADED
    }

    public interface AttemptContext {
        ServerLevel level();

        RandomSource random();

        boolean isChunkLoaded(ChunkPos chunk);

        boolean isPositionLoaded(BlockPos position);

        long nextAttempt(ChunkPos chunk, WildResourceEntry entry);

        int countInChunk(ChunkPos chunk, WildResourceEntry entry);

        boolean spacingAllows(WildResourceEntry entry, BlockPos position);

        boolean recordPlacement(WildResourceEntry entry, BlockPos position, long gameTime);

        void schedule(ChunkPos chunk, WildResourceEntry entry, long nextAttempt);
    }
}
