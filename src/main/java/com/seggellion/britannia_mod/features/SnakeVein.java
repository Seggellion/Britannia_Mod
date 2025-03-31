package com.seggellion.britannia_mod.features;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;

import java.util.Map;

public class SnakeVein {
    public static int generate(ServerLevel level, BlockPos center, int radius, Block oreBlock, String rotation, Map<BlockPos, BlockState> originalBlocks) {
        RandomSource random = level.getRandom();
        int totalPlaced = 0;

        // Ensure the primary vein extends from (center.y) up to (center.y + radius)
        totalPlaced += generateVein(level, center, radius, oreBlock, originalBlocks, random);

        // Generate additional tendrils within a 20x20 area
        int tendrilCount = 3; // Number of additional veins
        boolean fullHeightUsed = false; // Ensure at least one tendril has the full height

        for (int i = 0; i < tendrilCount; i++) {
            int offsetX = center.getX() + random.nextInt(21) - 10; // Random offset in 20x20 area
            int offsetZ = center.getZ() + random.nextInt(21) - 10;
            int tendrilHeight = random.nextInt(radius - 9) + 10; // Random height between 10 and radius

            // Ensure at least one tendril is full height
            if (!fullHeightUsed || i == tendrilCount - 1) {
                tendrilHeight = radius;
                fullHeightUsed = true;
            }

            BlockPos tendrilStart = new BlockPos(offsetX, center.getY(), offsetZ);
            totalPlaced += generateVein(level, tendrilStart, tendrilHeight, oreBlock, originalBlocks, random);
        }

        return totalPlaced;
    }

    private static int generateVein(ServerLevel level, BlockPos start, int height, Block oreBlock, Map<BlockPos, BlockState> originalBlocks, RandomSource random) {
        int placed = 0;
        BlockPos current = start;

        for (int i = 0; i < height * 3; i++) { // Extends based on height
            int dx = random.nextInt(3) - 1; // Random X movement (-1 to 1)
            int dy = random.nextInt(2); // Moves upwards (0 to 1) instead of sinking
            int dz = random.nextInt(3) - 1; // Random Z movement (-1 to 1)

            current = current.offset(dx, dy, dz);

            if (current.getY() >= start.getY() + height) break; // Stop if exceeding height limit

            if (random.nextFloat() < 0.8f) { // 80% chance to place ore
                BlockState currentState = level.getBlockState(current);

                // Store original block if not already stored
                if (!originalBlocks.containsKey(current)) {
                    originalBlocks.put(current, currentState);
                }

                level.setBlock(current, oreBlock.defaultBlockState(), 2);
                placed++;
            }
        }

        return placed;
    }
}
