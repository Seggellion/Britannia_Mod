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
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Server-only round-robin driver for naturally loaded chunks. It performs a fixed amount of work,
 * samples bounded candidates, and verifies availability exclusively through {@code getChunkNow}.
 */
public final class WildResourceManager {
    public static final int MAX_CHUNKS_PER_TICK = 16;
    public static final int MAX_RESOURCE_ATTEMPTS_PER_TICK = 32;
    public static final int MAX_RECONCILIATIONS_PER_TICK = 16;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WildResourceManager INSTANCE = new WildResourceManager();
    private static final Map<ServerLevel, LoadedChunkQueue> LOADED_CHUNKS = new IdentityHashMap<>();
    private static boolean registered;
    private int dimensionCursor;

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
            LoadedChunkQueue queue = LOADED_CHUNKS.computeIfAbsent(level, ignored -> new LoadedChunkQueue());
            ChunkPos loaded = event.getChunk().getPos();
            queue.add(loaded);
            // A resource radius is 20 blocks, so a newly available chunk can unblock checks in
            // loaded chunks up to two chunk coordinates away. This is a fixed 5x5 candidate set.
            for (int offsetX = -2; offsetX <= 2; offsetX++) {
                for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
                    ChunkPos neighbor = new ChunkPos(loaded.x + offsetX, loaded.z + offsetZ);
                    if (level.getChunkSource().getChunkNow(neighbor.x, neighbor.z) != null) {
                        queue.requestReconciliation(neighbor);
                    }
                }
            }
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
        int remainingReconciliations = MAX_RECONCILIATIONS_PER_TICK;
        List<LevelQueue> activeLevels = new ArrayList<>();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LoadedChunkQueue queue = LOADED_CHUNKS.get(level);
            if (queue != null) {
                activeLevels.add(new LevelQueue(level, queue));
            }
        }
        if (activeLevels.isEmpty()) {
            dimensionCursor = 0;
            return;
        }

        int start = Math.floorMod(dimensionCursor, activeLevels.size());
        while (remainingReconciliations > 0) {
            boolean progressed = false;
            for (int offset = 0; offset < activeLevels.size() && remainingReconciliations > 0; offset++) {
                LevelQueue current = activeLevels.get((start + offset) % activeLevels.size());
                ChunkPos chunk = current.queue().pollReconciliation();
                if (chunk != null) {
                    reconcileLoadedChunk(current.level(), chunk);
                    remainingReconciliations--;
                    progressed = true;
                }
            }
            if (!progressed) {
                break;
            }
        }

        while (remainingChunks > 0 && remainingAttempts > 0) {
            boolean progressed = false;
            for (int offset = 0; offset < activeLevels.size()
                    && remainingChunks > 0 && remainingAttempts > 0; offset++) {
                LevelQueue current = activeLevels.get((start + offset) % activeLevels.size());
                ChunkPos chunk = current.queue().rotate();
                if (chunk != null) {
                    remainingChunks--;
                    remainingAttempts -= processChunk(current.level(), chunk, remainingAttempts);
                    progressed = true;
                }
            }
            if (!progressed) {
                break;
            }
        }
        dimensionCursor = (start + 1) % activeLevels.size();
    }

    static void reconcileLoadedChunk(ServerLevel level, ChunkPos chunk) {
        if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
            return;
        }
        WildResourceSavedData data = WildResourceSavedData.get(level);
        for (WildResourceNode node : data.nodesInChunk(chunk)) {
            WildResourceEntry entry = WildResources.registry().find(node.resourceId()).orElse(null);
            WildResourceEntry.ExistingNodeState state = entry == null
                    ? WildResourceEntry.ExistingNodeState.MISSING_OR_REPLACED
                    : entry.existingNodeValidator().inspect(level, node.position());
            long nextAttempt = entry == null
                    ? level.getGameTime()
                    : level.getGameTime() + entry.tuning().nextRespawnDelay(level.random);
            WildResourceReconciliation.Outcome outcome = WildResourceReconciliation.reconcile(
                    data, node, entry, state, nextAttempt
            );
            if (outcome == WildResourceReconciliation.Outcome.REMOVE_OWNED_BLOCK) {
                level.setBlock(node.position(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
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

    private record LevelQueue(ServerLevel level, LoadedChunkQueue queue) {
    }

    private static final class LoadedChunkQueue {
        private final ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
        private final LinkedHashSet<Long> membership = new LinkedHashSet<>();
        private final ArrayDeque<ChunkPos> reconciliationQueue = new ArrayDeque<>();
        private final LinkedHashSet<Long> reconciliationMembership = new LinkedHashSet<>();

        void add(ChunkPos chunk) {
            if (membership.add(chunk.toLong())) {
                queue.addLast(chunk);
            }
            requestReconciliation(chunk);
        }

        void remove(ChunkPos chunk) {
            if (membership.remove(chunk.toLong())) {
                queue.remove(chunk);
            }
            if (reconciliationMembership.remove(chunk.toLong())) {
                reconciliationQueue.remove(chunk);
            }
        }

        void requestReconciliation(ChunkPos chunk) {
            if (membership.contains(chunk.toLong()) && reconciliationMembership.add(chunk.toLong())) {
                reconciliationQueue.addLast(chunk);
            }
        }

        ChunkPos pollReconciliation() {
            ChunkPos chunk = reconciliationQueue.pollFirst();
            if (chunk != null) {
                reconciliationMembership.remove(chunk.toLong());
            }
            return chunk;
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
