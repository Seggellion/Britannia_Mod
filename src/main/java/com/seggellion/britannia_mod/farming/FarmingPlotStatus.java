package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.block.*;
import com.seggellion.britannia_mod.block.entity.*;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Read-only presentation of synchronized root state. No optimistic occupancy or retained crop name. */
public record FarmingPlotStatus(String species, Stage stage) {
    public enum Stage { LOADING, EMPTY, GERMINATING, GROWING, READY }

    public Component text() {
        var state = Component.translatable("hud.britannia_mod.plot." + stage.name().toLowerCase(java.util.Locale.ROOT));
        return species.isEmpty() ? state : Component.translatable("hud.britannia_mod.plot.crop", FarmingPlantingFeedback.speciesName(species), state);
    }

    public static Optional<FarmingPlotStatus> at(Level level, BlockPos aimed) {
        return at(level, aimed, level.isClientSide);
    }

    /** Also used by packet round-trip tests without constructing a graphical ClientLevel. */
    public static Optional<FarmingPlotStatus> at(Level level, BlockPos aimed, boolean requireSynchronizedState) {
        if (!level.hasChunkAt(aimed)) return Optional.empty();
        var block = level.getBlockState(aimed).getBlock();
        BlockPos soilPos = aimed;
        if (block instanceof TrellisBlock) soilPos = aimed.below();
        else if (block instanceof CornStalkBlock || block instanceof GrapeArborBlock) {
            soilPos = TallCropSupport.findAnchor(level, aimed);
            if (soilPos == null) return Optional.of(loading());
        } else if (level.getBlockEntity(aimed) instanceof OrangeTreeRootBlockEntity
                || OrangeTreeUtils.isOrangeTreeBody(level.getBlockState(aimed))) {
            var root = OrangeTreeUtils.findRoot(level, aimed).orElse(null);
            if (root == null) return Optional.of(loading());
            soilPos = root.getBlockPos().below();
        } else if (!(block instanceof FarmingBlock) && !(block instanceof FlowerBlock)) return Optional.empty();
        if (!level.hasChunkAt(soilPos) || !(level.getBlockEntity(soilPos) instanceof FarmingBlockEntity soil)) return Optional.of(loading());
        if (requireSynchronizedState && !soil.hasSynchronizedPlotStatus()) return Optional.of(loading());
        if (soil instanceof FlowerBlockEntity flower && flower.isInitialized()) {
            var state = flower.flowerState().orElseThrow();
            var definition = FlowerRegistry.initial().byId(state.speciesId()).orElse(null);
            if (definition == null) return Optional.of(loading());
            return Optional.of(new FarmingPlotStatus(state.speciesId().getPath(),
                    state.growthStage() >= definition.naturalMaximumStage() ? Stage.READY
                            : state.growthStage() <= 1 ? Stage.GERMINATING : Stage.GROWING));
        }
        if (soil instanceof FlowerBlockEntity && !(soil instanceof HouseFarmPlotBlockEntity)) return Optional.of(loading());
        var crop = CropRegistry.byId(soil.getPlantedCropId()).orElse(null);
        if (crop == null) {
            boolean occupied = soil.hasCrop() || !soil.getStoredSeed().isBlank()
                    || level.getBlockState(soilPos).getValue(FarmingBlock.HAS_SEEDS)
                    || soil instanceof HouseFarmPlotBlockEntity house && !house.isCleared();
            return Optional.of(occupied ? loading() : new FarmingPlotStatus("", Stage.EMPTY));
        }
        if (crop.treeCrop()) {
            if (!level.hasChunkAt(soilPos.above()) || !(level.getBlockEntity(soilPos.above()) instanceof OrangeTreeRootBlockEntity root)
                    || !root.getTreeTypeId().equals(crop.id()) || requireSynchronizedState && !root.hasSynchronizedPlotStatus()) return Optional.of(loading());
            // Ripe fruit, rather than maximum wood growth, determines whether a pick is available.
            var plan = OrangeTreeStructurePlanner.plan(root.definition(), root.getTreeSeed(), root.definition().maxGrowthStep(), root.getBlockPos());
            boolean ready = false;
            for (var fruitPos : plan.fruit()) {
                if (!level.hasChunkAt(fruitPos)) return Optional.of(loading());
                var fruit = level.getBlockState(fruitPos);
                if (fruit.is(root.definition().fruitBlock().get()) && fruit.getValue(OrangeFruitBlock.RIPE)) ready = true;
            }
            return Optional.of(new FarmingPlotStatus(crop.id(), ready ? Stage.READY : root.getGrowthStep() <= 1 ? Stage.GERMINATING : Stage.GROWING));
        }
        return Optional.of(new FarmingPlotStatus(crop.id(), soil.isMature() ? Stage.READY
                : soil.getGrowthStage() == 0 ? Stage.GERMINATING : Stage.GROWING));
    }

    private static FarmingPlotStatus loading() { return new FarmingPlotStatus("", Stage.LOADING); }
}
