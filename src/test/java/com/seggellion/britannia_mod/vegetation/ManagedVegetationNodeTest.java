package com.seggellion.britannia_mod.vegetation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManagedVegetationNodeTest {
    private static final ResourceLocation ENTRY = ResourceLocation.fromNamespaceAndPath("britannia_mod", "grass_family");
    private static final ResourceLocation FLOWER = ResourceLocation.fromNamespaceAndPath("britannia_mod", "poppy");

    @Test
    void lifecycleRoundTripsThroughNbt() {
        ManagedVegetationNode original = ManagedVegetationNode.regrowing(new BlockPos(4, 70, -9), 120L)
                .flower(ENTRY, FLOWER, 4, 500L);

        assertEquals(original, ManagedVegetationNode.fromTag(original.toTag()));
    }

    @Test
    void occupiedNodesRequireEntryAndFlowerStateIsStrictlyOneThroughSeven() {
        assertThrows(IllegalArgumentException.class, () -> new ManagedVegetationNode(
                BlockPos.ZERO, ManagedVegetationLifecycle.SHORT_GRASS,
                java.util.Optional.empty(), java.util.Optional.empty(), 0, 1L
        ));
        assertThrows(IllegalArgumentException.class, () ->
                ManagedVegetationNode.regrowing(BlockPos.ZERO, 1L).flower(ENTRY, FLOWER, 8, 2L));
    }

    @Test
    void beginRegrowthClearsThePreviousVegetationIdentity() {
        ManagedVegetationNode regrowing = ManagedVegetationNode.regrowing(BlockPos.ZERO, 1L)
                .shortGrass(ENTRY, 20L)
                .beginRegrowth(40L);

        assertEquals(ManagedVegetationLifecycle.REGROWING, regrowing.lifecycle());
        assertEquals(java.util.Optional.empty(), regrowing.vegetationEntryId());
        assertEquals(40L, regrowing.nextTransitionGameTime());
    }
}
