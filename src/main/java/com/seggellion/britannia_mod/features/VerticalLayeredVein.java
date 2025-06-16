package com.seggellion.britannia_mod.features;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public class VerticalLayeredVein {
    public static int generate(ServerLevel level, BlockPos center, int radius, Block oreBlock, String rotation, Map<BlockPos, BlockState> originalBlocks) {
        RandomSource random = level.getRandom();
        int placed = 0;

        int coreClusterCount = (radius * 3);
        int peripheralClusterCount = (radius * 2);
        int scatterCount = radius * 6;

        // **CORE CLUSTERS**
        for (int c = 0; c < coreClusterCount; c++) {
            BlockPos clusterCenter = offsetPosition(center, random, radius, rotation, false);
            int clusterSize = 8 + random.nextInt(7);

            for (int i = 0; i < clusterSize; i++) {
                BlockPos target = offsetPosition(clusterCenter, random, 1, rotation, false);
                if (level.getBlockState(target).isAir()) {
                    BlockState currentState = level.getBlockState(target);

                // Store the original block before replacing (only if not already stored)
                if (!originalBlocks.containsKey(target)) {
                    originalBlocks.put(target, currentState);
                }
                    level.setBlock(target, oreBlock.defaultBlockState(), 2);
                    placed++;
                }
            }
        }

        // **PERIPHERAL CLUSTERS**
        for (int c = 0; c < peripheralClusterCount; c++) {
            BlockPos clusterCenter = offsetPosition(center, random, radius, rotation, true);
            int clusterSize = 6 + random.nextInt(6);

            for (int i = 0; i < clusterSize; i++) {
                BlockPos target = offsetPosition(clusterCenter, random, 1, rotation, true);
                if (level.getBlockState(target).isAir()) {

                BlockState currentState = level.getBlockState(target);

                // Store the original block before replacing (only if not already stored)
                if (!originalBlocks.containsKey(target)) {
                    originalBlocks.put(target, currentState);
                }

                    level.setBlock(target, oreBlock.defaultBlockState(), 2);
                    placed++;
                }
            }
        }

        // **SCATTERED ORES**
        for (int i = 0; i < scatterCount; i++) {
            BlockPos target = offsetPosition(center, random, radius, rotation, true);
            if (level.getBlockState(target).isAir()) {

                BlockState currentState = level.getBlockState(target);

                // Store the original block before replacing (only if not already stored)
                if (!originalBlocks.containsKey(target)) {
                    originalBlocks.put(target, currentState);
                }

                level.setBlock(target, oreBlock.defaultBlockState(), 2);
                placed++;
            }
        }

        return placed;
    }

    /**
     * Adjusts the ore placement based on rotation.
     * Handles North-South (XZ), East-West (ZW), Sideways (YZ), and Flat (XY).
     */
    private static BlockPos offsetPosition(BlockPos center, RandomSource random, int radius, String rotation, boolean allowYVariation) {
        int x = 0, y = 0, z = 0;

        switch (rotation) {
            case "XZ": // Default (North-South)
                x = random.nextInt(radius * 2) - radius;
                y = random.nextInt(radius * 2) - radius;
                z = random.nextInt(3) - 1; // Keeps it tight on Z-axis
                break;
            case "YZ": // Sideways (Vertical Wall)
                x = random.nextInt(3) - 1; // Keeps it tight on X-axis
                y = random.nextInt(radius * 2) - radius;
                z = random.nextInt(radius * 2) - radius;
                break;
            case "XY": // Flat
                x = random.nextInt(radius * 2) - radius;
                y = random.nextInt(3) - 1; // Keeps it tight on Y-axis
                z = random.nextInt(radius * 2) - radius;
                break;
            case "ZW": // ✅ East-West Rotation (Corrected)
                x = random.nextInt(3) - 1;  // Keeps it narrow in X-axis (like how "XZ" kept it narrow in Z)
                y = random.nextInt(radius * 2) - radius; // Allows full height variation
                z = random.nextInt(radius * 2) - radius; // Expands along Z (east-west vein)
                break;
        }

        if (allowYVariation) {
            y += random.nextInt(3) - 1; // Small Y variation for natural look
        }

        return center.offset(x, y, z);
    }
}
