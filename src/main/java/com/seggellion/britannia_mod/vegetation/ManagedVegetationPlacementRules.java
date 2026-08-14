package com.seggellion.britannia_mod.vegetation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.function.Predicate;

/** Central environment policy for registering and transitioning managed vegetation. */
public final class ManagedVegetationPlacementRules {
    public static final int REQUIRED_VERTICAL_SPACE = 3;

    private ManagedVegetationPlacementRules() {
    }

    @FunctionalInterface
    public interface BlockLookup {
        BlockState getBlockState(BlockPos position);
    }

    public static boolean hasValidSubstrate(BlockLookup world, BlockPos nodePosition) {
        Objects.requireNonNull(world, "Block lookup is required");
        Objects.requireNonNull(nodePosition, "Node position is required");
        return world.getBlockState(nodePosition.below()).is(Blocks.GRASS_BLOCK);
    }

    public static boolean canRegister(BlockLookup world, BlockPos nodePosition) {
        return canRegister(world, nodePosition, BlockState::isAir);
    }

    public static boolean canRegister(
            BlockLookup world,
            BlockPos nodePosition,
            Predicate<BlockState> usableState
    ) {
        return hasValidSubstrate(world, nodePosition)
                && hasVerticalSpace(world, nodePosition, usableState);
    }

    public static boolean hasVerticalSpace(
            BlockLookup world,
            BlockPos nodePosition,
            Predicate<BlockState> usableState
    ) {
        Objects.requireNonNull(world, "Block lookup is required");
        Objects.requireNonNull(nodePosition, "Node position is required");
        Objects.requireNonNull(usableState, "Usable-state predicate is required");
        for (int offset = 0; offset < REQUIRED_VERTICAL_SPACE; offset++) {
            BlockState state = world.getBlockState(nodePosition.above(offset));
            if (!state.getFluidState().isEmpty() || !usableState.test(state)) {
                return false;
            }
        }
        return true;
    }
}
