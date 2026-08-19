package com.seggellion.britannia_mod.vegetation;

import com.seggellion.britannia_mod.block.entity.ManagedFlowerBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;

import com.seggellion.britannia_mod.event.ManagedVegetationCutEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Server-only registration and removal boundary for managed vegetation nodes. */
public final class ManagedVegetationService {
    private ManagedVegetationService() {
    }

    public static boolean registerNode(ServerLevel level, BlockPos position) {
        return registerNode(
                level,
                position,
                level.getGameTime() + ManagedVegetationConfig.cutRegrowDelay(level.random)
        );
    }

    /** Called only from vanilla grass random ticks; never replaces the substrate or occupied space. */
    public static boolean tryRegisterNaturalNode(ServerLevel level, BlockPos position, RandomSource random) {
        if (!ManagedVegetationConfig.shouldNaturallyGrow(random)) {
            return false;
        }
        return registerNode(level, position, level.getGameTime());
    }

    private static boolean registerNode(ServerLevel level, BlockPos position, long firstTransitionTime) {
        if (!ManagedVegetationPlacementRules.canRegister(level::getBlockState, position)) {
            return false;
        }
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        ManagedVegetationNode node = ManagedVegetationNode.regrowing(position, firstTransitionTime);
        if (!data.register(node)) {
            return false;
        }
        if (!level.setBlock(position, BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get().defaultBlockState(), 3)) {
            data.remove(position);
            return false;
        }
        return true;
    }

    public static boolean registerPlacedController(ServerLevel level, BlockPos position) {
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        if (data.nodeAt(position).isPresent()) {
            return true;
        }
        boolean valid = ManagedVegetationPlacementRules.canRegister(
                level::getBlockState,
                position,
                state -> state.isAir() || state.is(BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get())
        );
        return valid && data.register(ManagedVegetationNode.regrowing(
                position, level.getGameTime() + ManagedVegetationConfig.cutRegrowDelay(level.random)
        ));
    }

    public static boolean removeNode(ServerLevel level, BlockPos position) {
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        ManagedVegetationNode node = data.nodeAt(position).orElse(null);
        if (node == null) {
            return false;
        }
        if (!data.remove(position)) {
            return false;
        }
        removeOwnedPlant(level, node);
        BlockState current = level.getBlockState(position);
        if (current.is(BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get())) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        }
        return true;
    }

    /** Relinquishes ownership only; blocks placed by players or other entities are never removed. */
    public static int retireNodesClaimedByPlacement(ServerLevel level, Collection<BlockPos> placedPositions) {
        if (placedPositions == null || placedPositions.isEmpty()) {
            return 0;
        }
        return retireNodesClaimedByPlacement(ManagedVegetationSavedData.get(level), placedPositions);
    }

    static int retireNodesClaimedByPlacement(
            ManagedVegetationSavedData data,
            Collection<BlockPos> placedPositions
    ) {
        if (data == null || placedPositions == null || placedPositions.isEmpty()) {
            return 0;
        }
        LinkedHashSet<BlockPos> bases = new LinkedHashSet<>();
        for (BlockPos placedPosition : placedPositions) {
            nodesClaimingPosition(data, placedPosition).forEach(node -> bases.add(node.position()));
        }
        int removed = 0;
        for (BlockPos base : bases) {
            if (data.remove(base)) {
                removed++;
            }
        }
        return removed;
    }

    static List<ManagedVegetationNode> nodesClaimingPosition(
            ManagedVegetationSavedData data,
            BlockPos position
    ) {
        List<ManagedVegetationNode> claimed = new ArrayList<>(ManagedVegetationPlacementRules.REQUIRED_VERTICAL_SPACE);
        for (int offset = 0; offset < ManagedVegetationPlacementRules.REQUIRED_VERTICAL_SPACE; offset++) {
            data.nodeAt(position.below(offset)).ifPresent(claimed::add);
        }
        return List.copyOf(claimed);
    }

    public static void scheduleReconciliation(ServerLevel level, BlockPos affectedPosition) {
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        resolveNode(data, affectedPosition).ifPresent(node -> {
            if (node.lifecycle().occupied()) {
                data.update(node.schedule(level.getGameTime() + ManagedVegetationConfig.retryTicks()));
            }
        });
    }

    public static boolean forceTransition(ServerLevel level, BlockPos position) {
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        ManagedVegetationNode node = data.nodeAt(position).orElse(null);
        if (node == null) {
            return false;
        }
        data.update(node.schedule(level.getGameTime()));
        return true;
    }

    public static boolean rerollNode(ServerLevel level, BlockPos position) {
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        ManagedVegetationNode node = data.nodeAt(position).orElse(null);
        if (node == null) {
            return false;
        }
        ManagedVegetationNode regrowing = node.beginRegrowth(level.getGameTime());
        data.update(regrowing);
        if (node.lifecycle().occupied()) {
            removeOwnedPlant(level, node);
        }
        BlockState current = level.getBlockState(position);
        if (current.isAir() || current.is(BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get())) {
            level.setBlock(
                    position, BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get().defaultBlockState(), 3
            );
        }
        return true;
    }

    public static String debugReport(ServerLevel level, BlockPos position) {
        ManagedVegetationNode node = ManagedVegetationSavedData.get(level).nodeAt(position).orElse(null);
        BlockState substrate = level.getBlockState(position.below());
        StringBuilder report = new StringBuilder("Managed vegetation debug ")
                .append(level.dimension().location()).append(' ')
                .append(position.toShortString())
                .append("\nregistered=").append(node != null)
                .append(" substrate=").append(blockName(substrate))
                .append(" substrate_valid=").append(substrate.is(Blocks.GRASS_BLOCK));
        for (int offset = 0; offset < ManagedVegetationPlacementRules.REQUIRED_VERTICAL_SPACE; offset++) {
            BlockPos checked = position.above(offset);
            BlockState state = level.getBlockState(checked);
            report.append("\nspace[").append(offset).append("]=")
                    .append(blockName(state))
                    .append(" fluid=").append(!state.getFluidState().isEmpty());
        }
        if (node == null) {
            boolean eligible = ManagedVegetationPlacementRules.canRegister(level::getBlockState, position);
            report.append("\nstatus=UNMANAGED natural_eligible=").append(eligible);
            if (eligible) {
                report.append("; waiting for the grass block below to pass its random growth roll");
            } else {
                report.append("; substrate or three-block air/fluids check prevents natural growth");
            }
        } else {
            long dueIn = node.nextTransitionGameTime() == ManagedVegetationNode.NO_TRANSITION
                    ? ManagedVegetationNode.NO_TRANSITION
                    : Math.max(0L, node.nextTransitionGameTime() - level.getGameTime());
            report.append("\nlifecycle=").append(node.lifecycle())
                    .append(" entry=").append(node.vegetationEntryId().map(Object::toString).orElse("<none>"))
                    .append(" flower=").append(node.flowerSpeciesId().map(Object::toString).orElse("<none>"))
                    .append(" stage=").append(node.flowerStage())
                    .append(" due_in_ticks=").append(dueIn == ManagedVegetationNode.NO_TRANSITION ? "none" : dueIn)
                    .append(" due_in_seconds=").append(
                            dueIn == ManagedVegetationNode.NO_TRANSITION
                                    ? "none"
                                    : String.format(Locale.ROOT, "%.1f", dueIn / 20.0D)
                    );
        }
        report.append("\nweights=")
                .append(ManagedVegetationConfig.grassWeight()).append('/')
                .append(ManagedVegetationConfig.fernWeight()).append('/')
                .append(ManagedVegetationConfig.flowerWeight())
                .append(" natural_growth=").append(ManagedVegetationConfig.naturalGrowthEnabled())
                .append(" natural_roll=1/")
                .append(ManagedVegetationConfig.effectiveNaturalGrowthChanceDenominator())
                .append(" discovery=random-grass-ticks lifecycle=normal-server-ticks");
        return report.toString();
    }

    private static String blockName(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /** Resolves a canonical node from its base or the upper half of managed tall grass. */
    public static Optional<ManagedVegetationNode> resolveNode(ServerLevel level, BlockPos clickedPosition) {
        return resolveNode(ManagedVegetationSavedData.get(level), clickedPosition);
    }

    /** Resolves only a node whose complete live representation still agrees with persisted ownership. */
    public static Optional<ManagedVegetationNode> resolveOwnedCutNode(
            ServerLevel level,
            BlockPos clickedPosition
    ) {
        return resolveNode(level, clickedPosition)
                .filter(node -> ownsCutPosition(level, node, clickedPosition));
    }

    static Optional<ManagedVegetationNode> resolveNode(
            ManagedVegetationSavedData data,
            BlockPos clickedPosition
    ) {
        Optional<ManagedVegetationNode> direct = data.nodeAt(clickedPosition);
        if (direct.isPresent()) {
            return direct;
        }
        return data.nodeAt(clickedPosition.below())
                .filter(node -> node.lifecycle() == ManagedVegetationLifecycle.TALL_GRASS);
    }

    /** Performs one atomic, no-drop managed cut. Callers must cancel the vanilla break path. */
    public static boolean cutNode(ServerPlayer player, ServerLevel level, BlockPos clickedPosition) {
        if (!ManagedVegetationCutTools.canCut(player)) {
            return false;
        }
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        ManagedVegetationNode node = resolveOwnedCutNode(level, clickedPosition).orElse(null);
        if (node == null) {
            return false;
        }

        BlockPos base = node.position();
        ManagedVegetationNode latest = data.nodeAt(base).orElse(null);
        if (latest == null || latest.lifecycle() != node.lifecycle()
                || !ownsCutPosition(level, latest, clickedPosition)) {
            return false;
        }

        long nextTransition = level.getGameTime() + ManagedVegetationConfig.cutRegrowDelay(level.random);
        data.update(latest.beginRegrowth(nextTransition));
        removeOwnedPlant(level, latest);
        level.setBlock(base, BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get().defaultBlockState(), 3);

        if (ManagedVegetationCutTools.shouldDamageHeldItem(player)) {
            player.getMainHandItem().hurtAndBreak(
                    1, player, net.minecraft.world.entity.player.Player.getSlotForHand(InteractionHand.MAIN_HAND)
            );
        }
        level.playSound(null, base, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
        NeoForge.EVENT_BUS.post(new ManagedVegetationCutEvent(
                player,
                level.dimension(),
                base,
                latest.vegetationEntryId().orElseThrow(),
                latest.flowerSpeciesId(),
                latest.flowerStage()
        ));
        return true;
    }

    private static void removeOwnedPlant(ServerLevel level, ManagedVegetationNode node) {
        BlockPos base = node.position();
        if (node.lifecycle() == ManagedVegetationLifecycle.TALL_GRASS
                && level.getBlockState(base.above()).is(Blocks.TALL_GRASS)) {
            level.setBlock(base.above(), Blocks.AIR.defaultBlockState(), 3);
        }
        BlockState state = level.getBlockState(base);
        if (isOwnedState(node.lifecycle(), state)) {
            level.setBlock(base, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    static boolean isOwnedState(ManagedVegetationLifecycle lifecycle, BlockState state) {
        return switch (lifecycle) {
            case SHORT_GRASS -> state.is(Blocks.SHORT_GRASS);
            case TALL_GRASS -> state.is(Blocks.TALL_GRASS);
            case FERN -> state.is(BlockRegistry.FERN.get());
            case BLOOD_MOSS -> state.is(BlockRegistry.BLOOD_MOSS.get());
            case FLOWER -> state.is(BlockRegistry.MANAGED_FLOWER.get());
            case REGROWING -> state.is(BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get());
        };
    }

    static boolean hasLiveRepresentation(ServerLevel level, ManagedVegetationNode node) {
        BlockPos base = node.position();
        return switch (node.lifecycle()) {
            case REGROWING -> level.getBlockState(base).is(BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get());
            case SHORT_GRASS -> level.getBlockState(base).is(Blocks.SHORT_GRASS);
            case TALL_GRASS -> isTallGrassHalf(level.getBlockState(base), DoubleBlockHalf.LOWER)
                    && isTallGrassHalf(level.getBlockState(base.above()), DoubleBlockHalf.UPPER);
            case FERN -> level.getBlockState(base).is(BlockRegistry.FERN.get());
            case BLOOD_MOSS -> level.getBlockState(base).is(BlockRegistry.BLOOD_MOSS.get());
            case FLOWER -> level.getBlockState(base).is(BlockRegistry.MANAGED_FLOWER.get())
                    && level.getBlockEntity(base) instanceof ManagedFlowerBlockEntity flower
                    && node.flowerSpeciesId().filter(species -> flower.matches(species, node.flowerStage())).isPresent();
        };
    }

    private static boolean ownsCutPosition(
            ServerLevel level,
            ManagedVegetationNode node,
            BlockPos clickedPosition
    ) {
        if (!node.lifecycle().occupied() || !hasLiveRepresentation(level, node)) {
            return false;
        }
        return clickedPosition.equals(node.position())
                || (node.lifecycle() == ManagedVegetationLifecycle.TALL_GRASS
                && clickedPosition.equals(node.position().above()));
    }

    private static boolean isTallGrassHalf(BlockState state, DoubleBlockHalf expectedHalf) {
        return state.is(Blocks.TALL_GRASS)
                && state.hasProperty(DoublePlantBlock.HALF)
                && state.getValue(DoublePlantBlock.HALF) == expectedHalf;
    }
}
