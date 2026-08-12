package com.seggellion.britannia_mod.wildresource;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.ArrayList;

/**
 * Server-only round-robin driver for naturally loaded chunks. It performs a fixed amount of work,
 * samples bounded candidates, and verifies availability exclusively through {@code getChunkNow}.
 */
public final class WildResourceManager {
    public static final int MAX_CHUNKS_PER_TICK = 16;
    public static final int MAX_RESOURCE_ATTEMPTS_PER_TICK = 32;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WildResourceManager INSTANCE = new WildResourceManager();
    private static final Map<ServerLevel, LoadedChunkQueue> LOADED_CHUNKS = new IdentityHashMap<>();
    private static boolean registered;

    private WildResourceManager() {
    }

    public static synchronized void register() {
        if (!registered) {
            NeoForge.EVENT_BUS.register(INSTANCE);
            registered = true;
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LOADED_CHUNKS.computeIfAbsent(level, ignored -> new LoadedChunkQueue()).add(event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LoadedChunkQueue queue = LOADED_CHUNKS.get(level);
            if (queue != null) {
                queue.remove(event.getChunk().getPos());
            }
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LOADED_CHUNKS.remove(level);
        }
    }

    @SubscribeEvent
    public void afterServerTick(ServerTickEvent.Post event) {
        int remainingChunks = MAX_CHUNKS_PER_TICK;
        int remainingAttempts = MAX_RESOURCE_ATTEMPTS_PER_TICK;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LoadedChunkQueue queue = LOADED_CHUNKS.get(level);
            if (queue == null) {
                continue;
            }
            while (remainingChunks > 0 && remainingAttempts > 0) {
                ChunkPos chunk = queue.rotate();
                if (chunk == null) {
                    break;
                }
                remainingChunks--;
                remainingAttempts -= processChunk(level, chunk, remainingAttempts);
            }
            if (remainingChunks == 0 || remainingAttempts == 0) {
                break;
            }
        }
    }

    private static int processChunk(ServerLevel level, ChunkPos chunk, int attemptBudget) {
        if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
            LoadedChunkQueue queue = LOADED_CHUNKS.get(level);
            if (queue != null) {
                queue.remove(chunk);
            }
            return 0;
        }
        WildResourceSavedData data = WildResourceSavedData.get(level);
        LevelAttemptContext context = new LevelAttemptContext(level, data);
        long now = level.getGameTime();
        ArrayList<WildResourceEntry> dueEntries = new ArrayList<>();
        for (WildResourceEntry entry : WildResources.registry().entries()) {
            long nextAttempt = data.nextAttempt(chunk, entry.id());
            if (nextAttempt == WildResourceSavedData.UNSCHEDULED) {
                data.scheduleAttempt(chunk, entry.id(), now + entry.tuning().nextAttemptDelay(level.random));
                continue;
            }
            if (nextAttempt <= now) {
                dueEntries.add(entry);
            }
        }
        if (attemptBudget <= 0) {
            return 0;
        }
        WildResourceEntry selected = WildResources.registry().select(level.random, dueEntries).orElse(null);
        if (selected == null) {
            return 0;
        }
        try {
            WildResourceSpawnScheduler.attempt(selected, chunk, now, context);
        } catch (RuntimeException exception) {
            LOGGER.warn("Wild resource attempt {} in {} failed: {}", selected.id(), chunk, exception.getMessage());
            data.scheduleAttempt(chunk, selected.id(), now + selected.tuning().nextAttemptDelay(level.random));
        }
        return 1;
    }

    private record LevelAttemptContext(ServerLevel level, WildResourceSavedData data)
            implements WildResourceSpawnScheduler.AttemptContext {
        @Override
        public RandomSource random() {
            return level.random;
        }

        @Override
        public boolean isChunkLoaded(ChunkPos chunk) {
            return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null;
        }

        @Override
        public boolean isPositionLoaded(BlockPos position) {
            ChunkPos chunk = new ChunkPos(position);
            return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null;
        }

        @Override
        public long nextAttempt(ChunkPos chunk, WildResourceEntry entry) {
            return data.nextAttempt(chunk, entry.id());
        }

        @Override
        public int countInChunk(ChunkPos chunk, WildResourceEntry entry) {
            return data.countInChunk(chunk, entry.id());
        }

        @Override
        public boolean spacingAllows(WildResourceEntry entry, BlockPos position) {
            return data.nodeAt(position).isEmpty()
                    && !data.hasSameTypeWithin(entry.id(), position, entry.tuning().minimumSameTypeSpacing());
        }

        @Override
        public boolean recordPlacement(WildResourceEntry entry, BlockPos position, long gameTime) {
            return data.registerNode(new WildResourceNode(entry.id(), position, gameTime));
        }

        @Override
        public void schedule(ChunkPos chunk, WildResourceEntry entry, long nextAttempt) {
            data.scheduleAttempt(chunk, entry.id(), nextAttempt);
        }
    }

    private static final class LoadedChunkQueue {
        private final ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
        private final LinkedHashSet<Long> membership = new LinkedHashSet<>();

        void add(ChunkPos chunk) {
            if (membership.add(chunk.toLong())) {
                queue.addLast(chunk);
            }
        }

        void remove(ChunkPos chunk) {
            if (membership.remove(chunk.toLong())) {
                queue.remove(chunk);
            }
        }

        ChunkPos rotate() {
            ChunkPos chunk = queue.pollFirst();
            if (chunk != null) {
                queue.addLast(chunk);
            }
            return chunk;
        }
    }
}
