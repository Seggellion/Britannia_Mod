package com.seggellion.britannia_mod.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

public final class CropEnvironmentRules {
    private static final int DARK_SKY_LIGHT_MAX = 3;
    private static final int DARK_BLOCK_LIGHT_MAX = 7;

    private CropEnvironmentRules() {
    }

    public static boolean requiresDarknessOrUnderground(CropDefinition crop) {
        return crop != null && "nightshade".equals(crop.id());
    }

    public static NightshadeEnvironment nightshadeEnvironment(Level level, BlockPos pos) {
        BlockPos samplePos = pos.above();
        int skyLight = level.getBrightness(LightLayer.SKY, samplePos);
        int blockLight = level.getBrightness(LightLayer.BLOCK, samplePos);
        boolean underground = FarmingClimateResolver.resolve(level, pos) == FarmingClimate.UNDERGROUND
                || pos.getY() < level.getSeaLevel() - 12;
        boolean darkEnough = skyLight <= DARK_SKY_LIGHT_MAX && blockLight <= DARK_BLOCK_LIGHT_MAX;
        return new NightshadeEnvironment(underground || darkEnough, darkEnough, underground, skyLight, blockLight);
    }

    public record NightshadeEnvironment(
            boolean allowed,
            boolean darkEnough,
            boolean underground,
            int skyLight,
            int blockLight
    ) {
    }
}
