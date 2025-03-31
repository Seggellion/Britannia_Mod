package com.seggellion.britannia_mod.features;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Map;

public class VerticalVein {
    public static int generate(ServerLevel level, BlockPos center, int radius, Block oreBlock, String rotation, Map<BlockPos, BlockState> originalBlocks) {
        RandomSource random = level.getRandom();
        int placed = 0;

        BlockPos currentPos = center;

        for (int i = 0; i < radius; i++) { // Ensures vein grows exactly `radius` blocks tall
            // Always grow upwards
            currentPos = currentPos.above();

            // Randomize X/Z shifts slightly to make it more organic
            int xOffset = random.nextInt(3) - 1; // -1, 0, or +1
            int zOffset = random.nextInt(3) - 1; // -1, 0, or +1
            currentPos = currentPos.offset(xOffset, 0, zOffset);

            // Prevent bedrock overwriting
            if (level.getBlockState(currentPos).is(Blocks.BEDROCK)) {
                continue; // Skip this iteration if bedrock is found
            }

            // Vary width dynamically (2-6 blocks thick)
            int width = 2 + random.nextInt(5); 

            for (int w = 0; w < width; w++) {
                BlockPos spreadPos = currentPos.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                if (!level.getBlockState(spreadPos).is(Blocks.BEDROCK)) {
                BlockState currentState = level.getBlockState(spreadPos);

                    // Store the original block before replacing (only if not already stored)
                    if (!originalBlocks.containsKey(spreadPos)) {
                        originalBlocks.put(spreadPos, currentState);
                    }
                    level.setBlock(spreadPos, oreBlock.defaultBlockState(), 2);
                    placed++;
                }
            }
        }

        return placed;
    }
}
