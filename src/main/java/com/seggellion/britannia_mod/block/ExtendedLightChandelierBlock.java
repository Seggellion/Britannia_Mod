package com.seggellion.britannia_mod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.seggellion.britannia_mod.registry.BlockRegistry; 

public class ExtendedLightChandelierBlock extends CandelabraBlock {
    // Note: If you want to serialize the radius for data generation, you will need a RecordCodecBuilder. 
    // For standard gameplay, passing it via the constructor is perfectly fine.
    private final int extensionRadius;

    public ExtendedLightChandelierBlock(Properties props, int extensionRadius) {
        super(props);
        this.extensionRadius = extensionRadius;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        
        // Only run on the server side and when the block is newly placed
        if (!level.isClientSide && !oldState.is(state.getBlock())) {
            placeGhostLights(level, pos, this.extensionRadius);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // Clean up ghost lights before the main block is completely removed
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            removeGhostLights(level, pos, this.extensionRadius);
        }
        
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private void placeGhostLights(Level level, BlockPos center, int radius) {
        // Extends outward from all 6 faces by the defined value
        for (Direction dir : Direction.values()) {
            BlockPos targetPos = center.relative(dir, radius);
            
            // Only place if the target block is replaceable (like air, tall grass, water)
            if (level.getBlockState(targetPos).canBeReplaced()) {
                level.setBlock(targetPos, BlockRegistry.GHOST_LIGHT.get().defaultBlockState(), 3);
            }
        }
    }

    private void removeGhostLights(Level level, BlockPos center, int radius) {
        for (Direction dir : Direction.values()) {
            BlockPos targetPos = center.relative(dir, radius);
            
            // Only destroy the block if it is OUR ghost light
            if (level.getBlockState(targetPos).is(BlockRegistry.GHOST_LIGHT.get())) {
                level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}