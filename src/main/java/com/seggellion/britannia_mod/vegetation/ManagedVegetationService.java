package com.seggellion.britannia_mod.vegetation;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

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
        if (node == null || !data.remove(position)) {
            return false;
        }
        BlockState current = level.getBlockState(position);
        if (current.is(BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get())) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        }
        return true;
    }
}
