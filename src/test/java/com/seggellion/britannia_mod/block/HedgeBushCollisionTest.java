package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HedgeBushCollisionTest {
    private static final BlockPos POS = BlockPos.ZERO;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
    }

    @Test
    void hedgeCollisionUsesItsAuthoredInsetFootprint() {
        HedgeBushBlock hedge = new HedgeBushBlock(
                BlockBehaviour.Properties.of().noOcclusion(),
                Block.box(2, 0, 2, 14, 16, 14));
        BlockState state = hedge.defaultBlockState();

        VoxelShape outline = state.getShape(EmptyBlockGetter.INSTANCE, POS);
        VoxelShape collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, POS);

        assertFalse(collision.isEmpty(), "a hedge must stop normal entities");
        assertEquals(outline.bounds(), collision.bounds(),
                "the collision should use the deliberately authored outline volume");
        AABB bounds = collision.bounds();
        assertEquals(0.125D, bounds.minX);
        assertEquals(0.875D, bounds.maxX);
        assertEquals(0.0D, bounds.minY);
        assertEquals(1.0D, bounds.maxY);
        assertEquals(0.125D, bounds.minZ);
        assertEquals(0.875D, bounds.maxZ);

        double adjacentGap = 1.0D + bounds.minX - bounds.maxX;
        assertEquals(0.25D, adjacentGap);
        assertTrue(adjacentGap < 0.6D,
                "normal-width players must not fit between adjacent hedge collisions");
    }

    @Test
    void sharedDecorativeShapePolicyStillDistinguishesPropsFromPlants() {
        DecorativePropBlock solidProp = new DecorativePropBlock(
                BlockBehaviour.Properties.of().noOcclusion(),
                Block.box(1, 0, 1, 15, 16, 15),
                true);
        DecorativePlantBlock fern = new DecorativePlantBlock(
                BlockBehaviour.Properties.of().noCollission().noOcclusion().replaceable(),
                Block.box(3, 0, 3, 13, 10, 13));

        assertFalse(solidProp.defaultBlockState()
                .getCollisionShape(EmptyBlockGetter.INSTANCE, POS).isEmpty(),
                "existing solid decorative props must remain collidable");
        assertFalse(fern.defaultBlockState().getShape(EmptyBlockGetter.INSTANCE, POS).isEmpty(),
                "the fern still needs its selection outline");
        assertTrue(fern.defaultBlockState()
                .getCollisionShape(EmptyBlockGetter.INSTANCE, POS).isEmpty(),
                "intentional decorative plants must remain non-colliding");
    }
}
