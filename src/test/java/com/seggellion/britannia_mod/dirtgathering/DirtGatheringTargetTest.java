package com.seggellion.britannia_mod.dirtgathering;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirtGatheringTargetTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exactVanillaDirtAndCoarseDirtAreGatherable() {
        assertTrue(DirtGatheringTarget.isGatherable(Blocks.DIRT.defaultBlockState()));
        assertTrue(DirtGatheringTarget.isGatherable(Blocks.COARSE_DIRT.defaultBlockState()));
    }

    @Test
    void adjacentSoilFamiliesRemainOutsideTheClosedRule() {
        assertFalse(DirtGatheringTarget.isGatherable(Blocks.GRASS_BLOCK.defaultBlockState()));
        assertFalse(DirtGatheringTarget.isGatherable(Blocks.ROOTED_DIRT.defaultBlockState()));
        assertFalse(DirtGatheringTarget.isGatherable(Blocks.PODZOL.defaultBlockState()));
        assertFalse(DirtGatheringTarget.isGatherable(Blocks.FARMLAND.defaultBlockState()));
        assertFalse(DirtGatheringTarget.isGatherable(Blocks.MUD.defaultBlockState()));
        assertFalse(DirtGatheringTarget.isGatherable(Blocks.AIR.defaultBlockState()));
    }
}
