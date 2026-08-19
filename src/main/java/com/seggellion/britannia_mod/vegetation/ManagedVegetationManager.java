package com.seggellion.britannia_mod.vegetation;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.block.entity.ManagedFlowerBlockEntity;
import com.seggellion.britannia_mod.farming.FlowerDefinition;
import com.seggellion.britannia_mod.wildresource.SwampBiomeRules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
            ManagedVegetationNode current = data.nodeAt(node.position()).orElse(null);
            if (current == null) {
                continue;
            }
            ManagedVegetationReconciliationResult reconciliation = reconcileVisibleState(level, current);
            if (reconciliation == ManagedVegetationReconciliationResult.OBSTRUCTED) {
                data.remove(current.position());
                continue;
            }
            if (reconciliation == ManagedVegetationReconciliationResult.REPAIRABLE
                    && current.nextTransitionGameTime() == ManagedVegetationNode.NO_TRANSITION) {
                data.update(current.schedule(now + ManagedVegetationConfig.retryTicks()));
                current = data.nodeAt(current.position()).orElseThrow();
            }
            if (current.nextTransitionGameTime() != ManagedVegetationNode.NO_TRANSITION
                    && current.nextTransitionGameTime() <= now) {
                transition(level, current, now);
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
            ManagedVegetationReconciliationResult reconciliation = reconcileVisibleState(level, current);
            if (reconciliation == ManagedVegetationReconciliationResult.OBSTRUCTED) {
                data.remove(current.position());
                return;
            }
            if (reconciliation == ManagedVegetationReconciliationResult.REPAIRABLE) {
                retry(data, current, now);
                return;
            }
            switch (current.lifecycle()) {
                case REGROWING -> spawnFromProfile(level, data, current, now);
                case SHORT_GRASS -> growTallGrass(level, data, current, now);
                case FLOWER -> growFlower(level, data, current, now);
                case TALL_GRASS, FERN, BLOOD_MOSS -> reconcileMaturePlant(level, data, current, now);
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
        ManagedVegetationEntry entry = profileFor(level, position).select(level.random);
        spawnEntry(level, data, node, entry, now);
    }

    private static void spawnEntry(
            ServerLevel level,
            ManagedVegetationSavedData data,
            ManagedVegetationNode node,
            ManagedVegetationEntry entry,
            long now
    ) {
        BlockPos position = node.position();
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
                if (!level.setBlock(position, BlockRegistry.FERN.get().defaultBlockState(), 3)) {
                    data.update(node.schedule(now + ManagedVegetationConfig.retryTicks()));
                }
            }
            case STATIC_BLOOD_MOSS -> {
                data.update(node.bloodMoss(entry.id()));
                if (!level.setBlock(position, BlockRegistry.BLOOD_MOSS.get().defaultBlockState(), 3)) {
                    data.update(node.schedule(now + ManagedVegetationConfig.retryTicks()));
                }
            }
            case FLOWER_STAGES -> spawnFlower(level, data, node, entry, now);
        }
    }

    /** Immediate deterministic admin path; normal gameplay always uses the weighted scheduler. */
    public static boolean debugSpawn(
            ServerLevel level,
            BlockPos position,
            ResourceLocation entryId
    ) {
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        ManagedVegetationEntry entry = profileFor(level, position).byId(entryId).orElse(null);
        if (entry == null || data.nodeAt(position).isEmpty()
                || !ManagedVegetationService.rerollNode(level, position)) {
            return false;
        }
        ManagedVegetationNode regrowing = data.nodeAt(position).orElse(null);
        if (regrowing == null || regrowing.lifecycle() != ManagedVegetationLifecycle.REGROWING
                || !canSpawnAt(level, position)) {
            return false;
        }
        spawnEntry(level, data, regrowing, entry, level.getGameTime());
        ManagedVegetationNode result = data.nodeAt(position).orElse(null);
        return result != null && result.lifecycle().occupied()
                && result.vegetationEntryId().filter(entryId::equals).isPresent();
    }

    private static void spawnFlower(
            ServerLevel level,
            ManagedVegetationSavedData data,
            ManagedVegetationNode node,
            ManagedVegetationEntry entry,
            long now
    ) {
        var species = ManagedFlowerSpecies.select(level.random);
        FlowerDefinition definition = ManagedFlowerSpecies.definition(species).orElse(null);
        if (definition == null) {
            retry(data, node, now);
            return;
        }
        long transitionTime = now + flowerStageDelay(
                definition, ManagedVegetationConfig.flowerStageTickMultiplier()
        );
        ManagedVegetationNode flowerNode = node.flower(entry.id(), species, 1, transitionTime);
        data.update(flowerNode);
        if (!level.setBlock(node.position(), BlockRegistry.MANAGED_FLOWER.get().defaultBlockState(), 3)
                || !(level.getBlockEntity(node.position()) instanceof ManagedFlowerBlockEntity flower)
                || !flower.initialize(species, 1)) {
            if (level.getBlockState(node.position()).is(BlockRegistry.MANAGED_FLOWER.get())) {
                level.setBlock(node.position(), Blocks.AIR.defaultBlockState(), 3);
            }
            data.update(node.schedule(now + ManagedVegetationConfig.retryTicks()));
            level.setBlock(
                    node.position(), BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get().defaultBlockState(), 3
            );
        }
    }

    private static void growFlower(
            ServerLevel level,
            ManagedVegetationSavedData data,
            ManagedVegetationNode node,
            long now
    ) {
        var species = node.flowerSpeciesId().orElse(null);
        FlowerDefinition definition = species == null
                ? null : ManagedFlowerSpecies.definition(species).orElse(null);
        if (definition == null) {
            resetInvalidFlower(level, data, node, now);
            return;
        }
        if (!ManagedVegetationPlacementRules.hasValidSubstrate(level::getBlockState, node.position())
                || !level.getBlockState(node.position()).is(BlockRegistry.MANAGED_FLOWER.get())
                || !(level.getBlockEntity(node.position()) instanceof ManagedFlowerBlockEntity flower)
                || !flower.matches(species, node.flowerStage())
                || !isClear(level, node.position().above())
                || !isClear(level, node.position().above(2))) {
            retry(data, node, now);
            return;
        }
        if (node.flowerStage() >= 7) {
            data.update(node.schedule(ManagedVegetationNode.NO_TRANSITION));
            return;
        }

        int nextStage = node.flowerStage() + 1;
        long nextTransition = nextStage == 7
                ? ManagedVegetationNode.NO_TRANSITION
                : now + flowerStageDelay(definition, ManagedVegetationConfig.flowerStageTickMultiplier());
        ManagedVegetationNode advanced = node.flower(
                node.vegetationEntryId().orElseThrow(), species, nextStage, nextTransition
        );
        data.update(advanced);
        if (!flower.setGrowthStage(species, nextStage)) {
            data.update(node.schedule(now + ManagedVegetationConfig.retryTicks()));
        }
    }

    private static void resetInvalidFlower(
            ServerLevel level,
            ManagedVegetationSavedData data,
            ManagedVegetationNode node,
            long now
    ) {
        if (level.getBlockState(node.position()).is(BlockRegistry.MANAGED_FLOWER.get())) {
            level.setBlock(node.position(), Blocks.AIR.defaultBlockState(), 3);
        }
        ManagedVegetationNode regrowing = node.beginRegrowth(now + ManagedVegetationConfig.retryTicks());
        data.update(regrowing);
        if (canSpawnAt(level, node.position())) {
            level.setBlock(
                    node.position(), BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get().defaultBlockState(), 3
            );
        }
    }

    private static void reconcileMaturePlant(
            ServerLevel level,
            ManagedVegetationSavedData data,
            ManagedVegetationNode node,
            long now
    ) {
        ManagedVegetationReconciliationResult reconciliation = reconcileVisibleState(level, node);
        if (reconciliation == ManagedVegetationReconciliationResult.VALID) {
            data.update(node.schedule(ManagedVegetationNode.NO_TRANSITION));
        } else if (reconciliation == ManagedVegetationReconciliationResult.OBSTRUCTED) {
            data.remove(node.position());
        } else {
            retry(data, node, now);
        }
    }

    /** Repairs only empty space or this node's own partial representation. */
    static ManagedVegetationReconciliationResult reconcileVisibleState(
            ServerLevel level,
            ManagedVegetationNode node
    ) {
        ManagedVegetationReconciliationResult classification = classifyVisibleState(level, node);
        if (classification != ManagedVegetationReconciliationResult.REPAIRABLE) {
            return classification;
        }
        return repairVisibleState(level, node)
                ? ManagedVegetationReconciliationResult.VALID
                : ManagedVegetationReconciliationResult.REPAIRABLE;
    }

    private static ManagedVegetationReconciliationResult classifyVisibleState(
            ServerLevel level,
            ManagedVegetationNode node
    ) {
        BlockPos position = node.position();
        if (!ManagedVegetationPlacementRules.hasValidSubstrate(level::getBlockState, position)) {
            return ManagedVegetationReconciliationResult.OBSTRUCTED;
        }
        return switch (node.lifecycle()) {
            case REGROWING -> classifySingleBlock(
                    level, position,
                    state -> state.is(BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get())
            );
            case SHORT_GRASS -> classifySingleBlock(
                    level, position, state -> state.is(Blocks.SHORT_GRASS)
            );
            case FERN -> classifySingleBlock(
                    level, position, state -> state.is(BlockRegistry.FERN.get())
            );
            case BLOOD_MOSS -> classifySingleBlock(
                    level,
                    position,
                    state -> state.is(BlockRegistry.BLOOD_MOSS.get())
            );
            case TALL_GRASS -> classifyTallGrass(level, position);
            case FLOWER -> classifyFlower(level, node);
        };
    }

    private static ManagedVegetationReconciliationResult classifySingleBlock(
            ServerLevel level,
            BlockPos position,
            java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> expected
    ) {
        if (!isClear(level, position.above()) || !isClear(level, position.above(2))) {
            return ManagedVegetationReconciliationResult.OBSTRUCTED;
        }
        var current = level.getBlockState(position);
        if (expected.test(current)) {
            return ManagedVegetationReconciliationResult.VALID;
        }
        return current.isAir() && current.getFluidState().isEmpty()
                ? ManagedVegetationReconciliationResult.REPAIRABLE
                : ManagedVegetationReconciliationResult.OBSTRUCTED;
    }

    private static ManagedVegetationReconciliationResult classifyTallGrass(
            ServerLevel level,
            BlockPos position
    ) {
        var lower = level.getBlockState(position);
        var upper = level.getBlockState(position.above());
        if (ManagedVegetationService.hasLiveRepresentation(
                level,
                ManagedVegetationNode.regrowing(position, 0L).tallGrass(ManagedVegetationProfile.GRASS_FAMILY_ID)
        )
                && isClear(level, position.above(2))) {
            return ManagedVegetationReconciliationResult.VALID;
        }
        boolean safeLower = lower.isAir() || lower.is(Blocks.TALL_GRASS);
        boolean safeUpper = upper.isAir() || upper.is(Blocks.TALL_GRASS);
        if (!safeLower || !safeUpper || !isClear(level, position.above(2))) {
            return ManagedVegetationReconciliationResult.OBSTRUCTED;
        }
        return ManagedVegetationReconciliationResult.REPAIRABLE;
    }

    private static ManagedVegetationReconciliationResult classifyFlower(
            ServerLevel level,
            ManagedVegetationNode node
    ) {
        BlockPos position = node.position();
        var species = node.flowerSpeciesId().orElse(null);
        if (species == null || ManagedFlowerSpecies.definition(species).isEmpty()
                || !isClear(level, position.above()) || !isClear(level, position.above(2))) {
            return ManagedVegetationReconciliationResult.OBSTRUCTED;
        }
        var current = level.getBlockState(position);
        if (current.is(BlockRegistry.MANAGED_FLOWER.get())
                && level.getBlockEntity(position) instanceof ManagedFlowerBlockEntity flower
                && flower.matches(species, node.flowerStage())) {
            return ManagedVegetationReconciliationResult.VALID;
        }
        if (!(current.isAir() || current.is(BlockRegistry.MANAGED_FLOWER.get()))) {
            return ManagedVegetationReconciliationResult.OBSTRUCTED;
        }
        return ManagedVegetationReconciliationResult.REPAIRABLE;
    }

    private static boolean repairVisibleState(ServerLevel level, ManagedVegetationNode node) {
        return switch (node.lifecycle()) {
            case REGROWING -> level.setBlock(
                    node.position(), BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get().defaultBlockState(), 3
            );
            case SHORT_GRASS -> level.setBlock(node.position(), Blocks.SHORT_GRASS.defaultBlockState(), 3);
            case FERN -> level.setBlock(node.position(), BlockRegistry.FERN.get().defaultBlockState(), 3);
            case BLOOD_MOSS -> level.setBlock(
                    node.position(), BlockRegistry.BLOOD_MOSS.get().defaultBlockState(), 3
            );
            case TALL_GRASS -> repairTallGrass(level, node.position());
            case FLOWER -> repairFlower(level, node);
        };
    }

    private static boolean repairTallGrass(ServerLevel level, BlockPos position) {
        removeOwnedTallGrass(level, position);
        DoublePlantBlock.placeAt(level, Blocks.TALL_GRASS.defaultBlockState(), position, 3);
        return ManagedVegetationService.hasLiveRepresentation(
                level,
                ManagedVegetationNode.regrowing(position, 0L).tallGrass(ManagedVegetationProfile.GRASS_FAMILY_ID)
        );
    }

    private static boolean repairFlower(ServerLevel level, ManagedVegetationNode node) {
        BlockPos position = node.position();
        var species = node.flowerSpeciesId().orElseThrow();
        var current = level.getBlockState(position);
        if (current.is(BlockRegistry.MANAGED_FLOWER.get())) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        }
        if (!level.setBlock(position, BlockRegistry.MANAGED_FLOWER.get().defaultBlockState(), 3)) {
            return false;
        }
        return level.getBlockEntity(position) instanceof ManagedFlowerBlockEntity flower
                && flower.initialize(species, node.flowerStage());
    }

    static long flowerStageDelay(FlowerDefinition definition, int multiplier) {
        if (definition == null || multiplier <= 0) {
            throw new IllegalArgumentException("Flower definition and a positive timing multiplier are required");
        }
        return Math.multiplyExact((long) definition.growthProfile().baseGrowthTicks(), multiplier);
    }

    static ManagedVegetationProfile profileFor(ServerLevel level, BlockPos position) {
        return ManagedVegetationProfile.configured(SwampBiomeRules.isSwamp(level, position));
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
