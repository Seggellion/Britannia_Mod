package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.block.entity.OrangeTreeRootBlockEntity;
import com.seggellion.britannia_mod.quest.action.QuestActionEvents;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Claims one live empty root, verifies placement, and only then pays and acknowledges it. */
public final class FarmingPlantingTransaction {
    private record Root(ServerLevel level, BlockPos pos) {}
    private static final ThreadLocal<Set<Root>> ACTIVE = ThreadLocal.withInitial(HashSet::new);
    private FarmingPlantingTransaction() {}

    public static <T> T locked(ServerLevel level, BlockPos pos, Supplier<T> action, T refused) {
        var key = new Root(level, pos.immutable());
        if (!ACTIVE.get().add(key)) return refused;
        try { return action.get(); }
        finally { ACTIVE.get().remove(key); if (ACTIVE.get().isEmpty()) ACTIVE.remove(); }
    }

    public static boolean plant(ServerLevel level, BlockPos pos, BlockState expected, FarmingBlockEntity soil,
                                ServerPlayer player, ItemStack seeds, CropDefinition crop,
                                String variety, FruitTreeDefinition tree) {
        return locked(level, pos, () -> commit(level, pos, expected, soil, player, seeds, crop, variety, tree), false);
    }

    private static boolean commit(ServerLevel level, BlockPos pos, BlockState expected, FarmingBlockEntity soil,
                                  ServerPlayer player, ItemStack seeds, CropDefinition crop,
                                  String variety, FruitTreeDefinition tree) {
        if (seeds.isEmpty() || player.isSpectator() || !held(player, seeds) || !level.hasChunkAt(pos)
                || !level.mayInteract(player, pos) || !level.getBlockState(pos).equals(expected)
                || level.getBlockEntity(pos) != soil || expected.getValue(FarmingBlock.HAS_SEEDS)
                || soil.hasCrop() || !soil.getStoredSeed().isBlank() || !FarmingBlock.mayPlantHere(level, soil, player)) return false;
        if (CropRegistry.bySeed(seeds.getItem()).orElse(null) != crop
                || crop.treeCrop() != (tree != null) || tree != null && !tree.id().equals(crop.id())
                || expected.getBlock() instanceof com.seggellion.britannia_mod.block.HouseFarmPlotBlock
                    && !com.seggellion.britannia_mod.block.HouseFarmPlotBlock.mayManagePlot(level, pos, player)) return false;
        var eligibility = FarmingCultivationGate.evaluate(player, seeds.getItem());
        if (!eligibility.permitsPlanting()) return false;
        BlockPos rootPos = pos.above();
        BlockState oldAbove = level.getBlockState(rootPos);
        if (tree != null && (!level.hasChunkAt(rootPos) || !oldAbove.canBeReplaced() || level.getBlockEntity(rootPos) != null)) return false;
        var original = soil.saveWithoutMetadata(level.registryAccess());
        var seedSnapshot = seeds.copy();
        BlockState plantedState = expected.setValue(FarmingBlock.HAS_SEEDS, true);
        BlockEntity placedRoot = null;
        boolean committed = false;
        try {
            // Claim occupancy before any neighbor callbacks or plant synchronization can reenter.
            if (!level.setBlock(pos, plantedState, 3) || level.getBlockEntity(pos) != soil) return false;
            soil.plant(crop, variety);
            soil.attributeCurrentCycleTo(player.getUUID());
            if (tree != null) {
                if (!level.getBlockState(rootPos).equals(oldAbove)
                        || !level.setBlock(rootPos, tree.rootBlock().get().defaultBlockState(), 3)) return false;
                placedRoot = level.getBlockEntity(rootPos);
                if (!(placedRoot instanceof OrangeTreeRootBlockEntity root)) return false;
                root.initializeFromFarm(soil, level.getRandom(), tree.id());
            }
            if (level.getBlockEntity(pos) != soil || !level.getBlockState(pos).equals(plantedState)
                    || !crop.id().equals(soil.getPlantedCropId()) || !held(player, seeds)
                    || !ItemStack.matches(seedSnapshot, seeds) || !level.mayInteract(player, pos)
                    || !soil.mayPlant(player)
                    || expected.getBlock() instanceof com.seggellion.britannia_mod.block.HouseFarmPlotBlock
                        && !com.seggellion.britannia_mod.block.HouseFarmPlotBlock.mayManagePlot(level, pos, player)
                    || tree != null && (level.getBlockEntity(rootPos) != placedRoot
                        || !level.getBlockState(rootPos).is(tree.rootBlock().get()))) return false;
            if (!player.getAbilities().instabuild) seeds.shrink(1);
            committed = true;
        } catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().warn("[planting] Placement failed at {}", pos, failure);
            return false;
        } finally {
            if (!committed) {
                if (placedRoot != null && level.getBlockEntity(rootPos) == placedRoot) level.setBlock(rootPos, oldAbove, 3);
                // Never overwrite an unrelated replacement introduced by a callback.
                if (level.getBlockEntity(pos) == soil && (!soil.hasCrop() || crop.id().equals(soil.getPlantedCropId()))
                        && (level.getBlockState(pos).equals(plantedState)
                        || level.getBlockState(pos).equals(expected))) {
                    soil.clearCrop();
                    level.setBlock(pos, expected, 3);
                    soil.loadWithComponents(original, level.registryAccess());
                    soil.setChanged();
                    level.sendBlockUpdated(pos, expected, expected, 3);
                }
            }
        }
        FarmingPlantingFeedback.planted(level, pos, player, crop.id(), crop.tier(), crop.farmingSkillModifier(),
                eligibility.type() == FarmingCultivationGate.ResultType.APPROVED_BYPASS);
        QuestActionEvents.cropPlant(player, level, pos, crop.id(), soil.cropCycleAtCurrentPlot(),
                player.getUUID(), soil.isCommunityPlot());
        return true;
    }

    private static boolean held(ServerPlayer player, ItemStack stack) {
        return player.getMainHandItem() == stack || player.getOffhandItem() == stack;
    }
}
