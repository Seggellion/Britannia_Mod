package com.seggellion.britannia_mod.vegetation;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.seggellion.britannia_mod.farming.GrainHarvestTools;

import java.util.Optional;

/** Server-only registration and removal boundary for managed vegetation nodes. */
public final class ManagedVegetationService {
    private ManagedVegetationService() {
    }

    public static boolean registerNode(ServerLevel level, BlockPos position) {
        if (!ManagedVegetationPlacementRules.canRegister(level::getBlockState, position)) {
            return false;
        }
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        ManagedVegetationNode node = ManagedVegetationNode.regrowing(
                position, level.getGameTime() + ManagedVegetationConfig.cutRegrowDelay(level.random)
        );
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

    public static void scheduleReconciliation(ServerLevel level, BlockPos affectedPosition) {
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        resolveNode(data, affectedPosition).ifPresent(node -> {
            if (node.lifecycle().occupied()) {
                data.update(node.schedule(level.getGameTime() + ManagedVegetationConfig.retryTicks()));
            }
        });
    }

    /** Resolves a canonical node from its base or the upper half of managed tall grass. */
    public static Optional<ManagedVegetationNode> resolveNode(ServerLevel level, BlockPos clickedPosition) {
        return resolveNode(ManagedVegetationSavedData.get(level), clickedPosition);
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
        if (player == null || !GrainHarvestTools.isGrainHarvestBlade(player.getMainHandItem())) {
            return false;
        }
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        ManagedVegetationNode node = resolveNode(data, clickedPosition).orElse(null);
        if (node == null || !node.lifecycle().occupied()) {
            return false;
        }

        BlockPos base = node.position();
        ManagedVegetationNode latest = data.nodeAt(base).orElse(null);
        if (latest == null || latest.lifecycle() != node.lifecycle() || !latest.lifecycle().occupied()) {
            return false;
        }

        long nextTransition = level.getGameTime() + ManagedVegetationConfig.cutRegrowDelay(level.random);
        data.update(latest.beginRegrowth(nextTransition));
        removeOwnedPlant(level, latest);
        level.setBlock(base, BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get().defaultBlockState(), 3);

        if (!player.getAbilities().instabuild) {
            player.getMainHandItem().hurtAndBreak(
                    1, player, net.minecraft.world.entity.player.Player.getSlotForHand(InteractionHand.MAIN_HAND)
            );
        }
        level.playSound(null, base, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
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
            case FERN -> state.is(Blocks.FERN);
            case FLOWER -> state.is(BlockRegistry.MANAGED_FLOWER.get());
            case REGROWING -> state.is(BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get());
        };
    }
}
