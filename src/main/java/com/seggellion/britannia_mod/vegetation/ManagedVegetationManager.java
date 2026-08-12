package com.seggellion.britannia_mod.vegetation;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Server lifecycle driver backed by the SavedData due-time index. It only visits due nodes and
 * nodes in newly loaded chunks; it never scans the complete registry or loads a chunk.
 */
public final class ManagedVegetationManager {
    public static final int MAX_TRANSITIONS_PER_TICK = 256;
    public static final int MAX_CHUNKS_PER_TICK = 64;
    public static final int MAX_PENDING_CHUNKS_PER_LEVEL = 4_096;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ManagedVegetationManager INSTANCE = new ManagedVegetationManager();
    private static final Map<ServerLevel, LinkedHashSet<ChunkPos>> PENDING_CHUNKS = new LinkedHashMap<>();
    private static boolean registered;

    private ManagedVegetationManager() {
    }

    public static synchronized void register() {
        if (!registered) {
            NeoForge.EVENT_BUS.register(INSTANCE);
            registered = true;
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        synchronized (PENDING_CHUNKS) {
            LinkedHashSet<ChunkPos> pending = PENDING_CHUNKS.computeIfAbsent(
                    level, ignored -> new LinkedHashSet<>()
            );
            pending.add(event.getChunk().getPos());
            while (pending.size() > MAX_PENDING_CHUNKS_PER_LEVEL) {
                pending.remove(pending.getFirst());
            }
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            synchronized (PENDING_CHUNKS) {
                PENDING_CHUNKS.remove(level);
            }
        }
    }

    @SubscribeEvent
    public void afterServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            processDue(level);
        }
        drainChunkLoadQueue();
    }

    static void processDue(ServerLevel level) {
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        long now = level.getGameTime();
        for (ManagedVegetationNode node : data.pollDue(now, MAX_TRANSITIONS_PER_TICK)) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    new ChunkPos(node.position()).x, new ChunkPos(node.position()).z
            );
            if (chunk != null) {
                transition(level, node, now);
            }
            // An unloaded overdue node stays persisted with its original due time. The chunk-load
            // queue will reconcile it when the chunk naturally becomes available.
        }
    }

    static void reconcileLoadedChunk(ServerLevel level, ChunkPos chunkPosition) {
        if (level.getChunkSource().getChunkNow(chunkPosition.x, chunkPosition.z) == null) {
            return;
        }
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        long now = level.getGameTime();
        for (ManagedVegetationNode node : data.nodesInChunk(chunkPosition)) {
            if (node.nextTransitionGameTime() != ManagedVegetationNode.NO_TRANSITION
                    && node.nextTransitionGameTime() <= now) {
                transition(level, node, now);
            }
        }
    }

    private static void drainChunkLoadQueue() {
        List<Map.Entry<ServerLevel, List<ChunkPos>>> work = new ArrayList<>();
        synchronized (PENDING_CHUNKS) {
            int remaining = MAX_CHUNKS_PER_TICK;
            var iterator = PENDING_CHUNKS.entrySet().iterator();
            while (iterator.hasNext() && remaining > 0) {
                var entry = iterator.next();
                List<ChunkPos> selected = entry.getValue().stream().limit(remaining).toList();
                selected.forEach(entry.getValue()::remove);
                work.add(Map.entry(entry.getKey(), selected));
                remaining -= selected.size();
                if (entry.getValue().isEmpty()) {
                    iterator.remove();
                }
            }
        }
        work.forEach(entry -> entry.getValue().forEach(chunk -> reconcileLoadedChunk(entry.getKey(), chunk)));
    }

    private static void transition(ServerLevel level, ManagedVegetationNode scheduled, long now) {
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        ManagedVegetationNode current = data.nodeAt(scheduled.position()).orElse(null);
        if (current == null || current.nextTransitionGameTime() != scheduled.nextTransitionGameTime()
                || current.nextTransitionGameTime() > now) {
            return;
        }
        try {
            switch (current.lifecycle()) {
                case REGROWING -> spawnFromProfile(level, data, current, now);
                case SHORT_GRASS -> growTallGrass(level, data, current, now);
                case TALL_GRASS, FERN, FLOWER -> data.update(
                        current.schedule(ManagedVegetationNode.NO_TRANSITION)
                );
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Managed vegetation transition failed at {}: {}",
                    current.position().toShortString(), exception.getMessage());
            retry(data, current, now);
        }
    }

    private static void spawnFromProfile(
            ServerLevel level,
            ManagedVegetationSavedData data,
            ManagedVegetationNode node,
            long now
    ) {
        BlockPos position = node.position();
        if (!canSpawnAt(level, position)) {
            retry(data, node, now);
            return;
        }
        ManagedVegetationEntry entry = ManagedVegetationProfile.configured().select(level.random);
        switch (entry.growthStrategy()) {
            case GRASS_FAMILY -> {
                ManagedVegetationNode shortGrass = node.shortGrass(
                        entry.id(), now + ManagedVegetationConfig.grassGrowthDelay(level.random)
                );
                data.update(shortGrass);
                if (!level.setBlock(position, Blocks.SHORT_GRASS.defaultBlockState(), 3)) {
                    data.update(node.schedule(now + ManagedVegetationConfig.retryTicks()));
                }
            }
            case STATIC_FERN -> {
                data.update(node.fern(entry.id()));
                if (!level.setBlock(position, Blocks.FERN.defaultBlockState(), 3)) {
                    data.update(node.schedule(now + ManagedVegetationConfig.retryTicks()));
                }
            }
            case FLOWER_STAGES -> retry(data, node, now);
        }
    }

    private static void growTallGrass(
            ServerLevel level,
            ManagedVegetationSavedData data,
            ManagedVegetationNode node,
            long now
    ) {
        BlockPos position = node.position();
        if (!ManagedVegetationPlacementRules.hasValidSubstrate(level::getBlockState, position)
                || !level.getBlockState(position).is(Blocks.SHORT_GRASS)
                || !isClear(level, position.above())
                || !isClear(level, position.above(2))) {
            retry(data, node, now);
            return;
        }

        ManagedVegetationNode tallGrass = node.tallGrass(node.vegetationEntryId().orElseThrow());
        data.update(tallGrass);
        DoublePlantBlock.placeAt(level, Blocks.TALL_GRASS.defaultBlockState(), position, 3);
        if (!level.getBlockState(position).is(Blocks.TALL_GRASS)
                || !level.getBlockState(position.above()).is(Blocks.TALL_GRASS)) {
            removeOwnedTallGrass(level, position);
            level.setBlock(position, Blocks.SHORT_GRASS.defaultBlockState(), 3);
            data.update(node.schedule(now + ManagedVegetationConfig.retryTicks()));
        }
    }

    private static boolean canSpawnAt(ServerLevel level, BlockPos position) {
        if (!ManagedVegetationPlacementRules.hasValidSubstrate(level::getBlockState, position)) {
            return false;
        }
        if (!(level.getBlockState(position).isAir()
                || level.getBlockState(position).is(BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get()))) {
            return false;
        }
        return isClear(level, position.above()) && isClear(level, position.above(2));
    }

    private static boolean isClear(ServerLevel level, BlockPos position) {
        var state = level.getBlockState(position);
        return state.isAir() && state.getFluidState().isEmpty();
    }

    private static void removeOwnedTallGrass(ServerLevel level, BlockPos base) {
        if (level.getBlockState(base).is(Blocks.TALL_GRASS)) {
            level.setBlock(base, Blocks.AIR.defaultBlockState(), 3);
        }
        if (level.getBlockState(base.above()).is(Blocks.TALL_GRASS)) {
            level.setBlock(base.above(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void retry(
            ManagedVegetationSavedData data,
            ManagedVegetationNode node,
            long now
    ) {
        data.nodeAt(node.position())
                .filter(current -> current.lifecycle() == node.lifecycle())
                .ifPresent(current -> data.update(
                        current.schedule(now + ManagedVegetationConfig.retryTicks())
                ));
    }
}
