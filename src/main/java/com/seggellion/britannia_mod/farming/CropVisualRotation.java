package com.seggellion.britannia_mod.farming;

import net.minecraft.core.BlockPos;

public final class CropVisualRotation {
    private static final float[] CROP_YAW_VARIANTS = {
            -22.5F,
            -15.0F,
            -8.0F,
            -3.5F,
            5.5F,
            11.0F,
            17.5F,
            24.0F
    };

    private CropVisualRotation() {
    }

    public static boolean isEnabledFor(CropDefinition crop) {
        return crop != null
                && !crop.treeCrop();
    }

    public static float yawFor(BlockPos pos, CropDefinition crop) {
        if (crop == null) {
            return 0.0F;
        }
        if ("grapes".equals(crop.id()) || crop.supportRequirement().name().contains("TRELLIS")) {
            return 0.0F;
        }
        return yawFor(pos, crop.id());
    }

    public static float yawFor(BlockPos pos, String cropId) {
        int hash = 0x7f4a7c15;
        hash = 31 * hash + pos.getX();
        hash = 31 * hash + pos.getY();
        hash = 31 * hash + pos.getZ();
        hash = 31 * hash + (cropId == null ? 0 : cropId.hashCode());
        return CROP_YAW_VARIANTS[Math.floorMod(hash, CROP_YAW_VARIANTS.length)];
    }
}
