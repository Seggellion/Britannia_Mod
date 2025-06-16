package com.seggellion.britannia_mod.features;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public class GeodeVein {
    public static int generate(ServerLevel level, BlockPos center, int radius, Block oreBlock, String rotation, Map<BlockPos, BlockState> originalBlocks) {
        RandomSource random = level.getRandom();
        int placed = 0;

        for (int i = 0; i < radius * 2; i++) { // Fewer but larger deposits

            BlockPos target = center.offset(
                random.nextInt(radius) - radius / 2,
                random.nextInt(radius / 2) - radius / 4,
                random.nextInt(radius) - radius / 2
            );
            BlockState currentState = level.getBlockState(target);

            // Store the original block before replacing (only if not already stored)
            if (!originalBlocks.containsKey(target)) {
                originalBlocks.put(target, currentState);
            }

            level.setBlock(target, oreBlock.defaultBlockState(), 2);
            placed++;
        }

        return placed;
    }
}
