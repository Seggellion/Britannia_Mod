package com.seggellion.britannia_mod.features;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public class ClusterVein {
    public static int generate(ServerLevel level, BlockPos center, int radius, Block oreBlock, String rotation, Map<BlockPos, BlockState> originalBlocks) {
        RandomSource random = level.getRandom();
        int placed = 0;

        // Squash factor: Adjusts how "flat" the cluster is (higher values = flatter)
        double ySquashFactor = 1.8; // Adjust to control how much the Y-axis is compressed

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos target = center.offset(x, y, z);
                    BlockState currentState = level.getBlockState(target);

                    // Squished distance formula: Reduce Y-axis influence
                    double distance = Math.sqrt(
                        (x * x) + (z * z) + ((y / ySquashFactor) * (y / ySquashFactor)) // Y-axis is compressed
                    );

                    if (distance > radius) continue;

                    // Gradual density drop (core is denser, edges sparser)
                    double densityChance = 1.0 - (distance / radius);
                    if (random.nextFloat() > densityChance) continue;

                    // Air pockets (10% chance to remove an ore block)
                    if (random.nextFloat() < 0.10) continue;

                    // Slight chance to extend some "veins" outward
                    if (distance > radius * 0.8 && random.nextFloat() < 0.3) continue;

                    if (!originalBlocks.containsKey(target)) { 
                        originalBlocks.put(target, currentState);  // Save the original block state
                    }

                    // Place ore
                    level.setBlock(target, oreBlock.defaultBlockState(), 2);
                    placed++;
                }
            }
        }

        return placed;
    }
}