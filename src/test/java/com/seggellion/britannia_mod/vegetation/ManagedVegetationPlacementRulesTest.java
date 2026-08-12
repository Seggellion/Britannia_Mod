package com.seggellion.britannia_mod.vegetation;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedVegetationPlacementRulesTest {
    private static final BlockPos NODE = new BlockPos(8, 65, 8);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void registrationRequiresGrassAndExactlyThreeClearBlocks() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(NODE.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        ManagedVegetationPlacementRules.BlockLookup world = pos -> blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());

        assertTrue(ManagedVegetationPlacementRules.canRegister(world, NODE));

        blocks.put(NODE.above(2), Blocks.STONE.defaultBlockState());
        assertFalse(ManagedVegetationPlacementRules.canRegister(world, NODE));
    }

    @Test
    void nonGrassSubstrateAndFluidsAreRejected() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(NODE.below(), Blocks.DIRT.defaultBlockState());
        ManagedVegetationPlacementRules.BlockLookup world = pos -> blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        assertFalse(ManagedVegetationPlacementRules.canRegister(world, NODE));

        blocks.put(NODE.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        blocks.put(NODE.above(), Blocks.WATER.defaultBlockState());
        assertFalse(ManagedVegetationPlacementRules.canRegister(world, NODE));
    }
}
