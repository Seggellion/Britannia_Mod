package com.seggellion.britannia_mod.features;

// Required imports

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public class AltitudeScaledFeature extends Feature<OreConfiguration> {

    public AltitudeScaledFeature() {
        super(OreConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<OreConfiguration> context) {
        BlockPos origin = context.origin();
        int yLevel = origin.getY();
        LevelAccessor level = context.level();
        RandomSource random = context.random();

        // Example: scale cluster size based on altitude
        int baseCluster = 5;
        if (yLevel > 190) {
            baseCluster = 12; // Bigger veins above Y=190
        } else if (yLevel < 60) {
            baseCluster = 3;  // Smaller veins near bedrock
        }

        int placed = 0;
        for (int i = 0; i < baseCluster; i++) {
            BlockPos target = origin.offset(
                random.nextInt(3) - 1, // Small random offset in X
                0, // Keep it mostly at the same Y-level
                random.nextInt(3) - 1  // Small random offset in Z
            );

        if (canPlaceOre(level, target, context.config(), random)) {
                level.setBlock(target, context.config().targetStates.get(0).state, 2);
                placed++;
            }
        }

        return placed > 0;
    }

    private boolean canPlaceOre(LevelAccessor level, BlockPos pos, OreConfiguration config, RandomSource random) {
        return config.targetStates.stream().anyMatch(target -> 
            target.target.test(level.getBlockState(pos), random)
        );
    }
}
