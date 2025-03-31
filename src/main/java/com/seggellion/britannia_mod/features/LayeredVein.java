package com.seggellion.britannia_mod.features;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Map;

public class LayeredVein {
    public static int generate(ServerLevel level, BlockPos center, int radius, Block oreBlock, String rotation, Map<BlockPos, BlockState> originalBlocks) {
        RandomSource random = level.getRandom();
        int placed = 0;

        int coreClusterCount = (radius * 3); // 3x more core clusters
        int peripheralClusterCount = (radius * 2); // More peripheral clusters
        int scatterCount = radius * 6; // More individual scattered ores

        // **CORE CLUSTERS (Very Dense, No Y-Variance)**
        for (int c = 0; c < coreClusterCount; c++) {
            BlockPos clusterCenter = center.offset(
                random.nextInt(radius * 2) - radius,
                0, // No Y variation
                random.nextInt(radius * 2) - radius
            );

            int clusterSize = 8 + random.nextInt(7); // Larger clusters (8-14 ores)
            for (int i = 0; i < clusterSize; i++) {
                BlockPos target = clusterCenter.offset(
                    random.nextInt(3) - 1,
                    0, // No Y variation
                    random.nextInt(3) - 1
                );

                BlockState currentState = level.getBlockState(target);
                if (currentState.isAir()) {
                    if (!originalBlocks.containsKey(target)) { 
                        originalBlocks.put(target, currentState);  // Save the original block
                    }
                    level.setBlock(target, oreBlock.defaultBlockState(), 2);
                    placed++;
                }
            }
        }

        // **PERIPHERAL CLUSTERS (Increased Density, Slight Y-Variance)**
        for (int c = 0; c < peripheralClusterCount; c++) {
            BlockPos clusterCenter = center.offset(
                random.nextInt(radius * 2) - radius,
                0,
                random.nextInt(radius * 2) - radius
            );

            int clusterSize = 6 + random.nextInt(6); // Medium-sized clusters
            for (int i = 0; i < clusterSize; i++) {
                BlockPos target = clusterCenter.offset(
                    random.nextInt(3) - 1,
                    random.nextInt(3) - 1, // Allows ±1 Y variation
                    random.nextInt(3) - 1
                );

                BlockState currentState = level.getBlockState(target);
                if (currentState.isAir()) {
                    if (!originalBlocks.containsKey(target)) { 
                        originalBlocks.put(target, currentState);  // Save the original block
                    }
                    level.setBlock(target, oreBlock.defaultBlockState(), 2);
                    placed++;
                }
            }
        }

        // **SCATTERED ORES (More Filler Ores, Same Y-Level)**
        for (int i = 0; i < scatterCount; i++) {
            BlockPos target = center.offset(
                random.nextInt(radius * 2) - radius,
                0, // Stays on the same Y-level
                random.nextInt(radius * 2) - radius
            );

            BlockState currentState = level.getBlockState(target);
            if (!currentState.isAir()) {
                if (!originalBlocks.containsKey(target)) { 
                    originalBlocks.put(target, currentState);  // Save the original block
                }
                level.setBlock(target, oreBlock.defaultBlockState(), 2);
                placed++;
            }
        }

        return placed;
    }
}
