package com.seggellion.britannia_mod.wildresource;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.neoforged.neoforge.common.Tags;

/** Shared definition for Blood Moss and client swamp atmosphere; includes swamp and mangrove swamp. */
public final class SwampBiomeRules {
    private SwampBiomeRules() {
    }

    public static boolean isSwamp(LevelReader level, BlockPos position) {
        return level.getBiome(position).is(Tags.Biomes.IS_SWAMP);
    }
}
