package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TopOnlySlabBlockTest {
    private static final BlockPos POS = BlockPos.ZERO;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
    }

    @Test
    void placementAlwaysUsesTopGeometryAndReflectsTheTargetFluid() {
        TopOnlySlabBlock block = block();

        BlockState waterlogged = block.placementStateForFluid(Fluids.WATER.getSource(false));
        BlockState dry = block.placementStateForFluid(Fluids.EMPTY.defaultFluidState());
        BlockState inLava = block.placementStateForFluid(Fluids.LAVA.getSource(false));

        assertEquals(SlabType.TOP, waterlogged.getValue(TopOnlySlabBlock.TYPE));
        assertTrue(waterlogged.getValue(TopOnlySlabBlock.WATERLOGGED));
        assertFalse(waterlogged.getValue(TopOnlySlabBlock.SUPPORTS_LANTERN));

        assertEquals(SlabType.TOP, dry.getValue(TopOnlySlabBlock.TYPE));
        assertFalse(dry.getValue(TopOnlySlabBlock.WATERLOGGED));

        assertEquals(SlabType.TOP, inLava.getValue(TopOnlySlabBlock.TYPE));
        assertFalse(inLava.getValue(TopOnlySlabBlock.WATERLOGGED));
    }

    @Test
    void plainRoofKeepsAConsistentTopHalfContract() {
        BlockState state = block().defaultBlockState();

        assertTopHalf(state.getShape(EmptyBlockGetter.INSTANCE, POS));
        assertTopHalf(state.getCollisionShape(EmptyBlockGetter.INSTANCE, POS));
        assertTopHalf(state.getBlockSupportShape(EmptyBlockGetter.INSTANCE, POS));
        assertTopHalf(state.getOcclusionShape(EmptyBlockGetter.INSTANCE, POS));
    }

    @Test
    void acquiredBottomStateMakesEveryGeometryContractFullWithoutBlockEntityQueries() {
        BlockState acquired = block().defaultBlockState()
                .setValue(TopOnlySlabBlock.SUPPORTS_LANTERN, true);
        BlockGetter level = new NoBlockEntityLookups();

        assertFullBlock(acquired.getShape(level, POS));
        assertFullBlock(acquired.getCollisionShape(level, POS));
        assertFullBlock(acquired.getBlockSupportShape(level, POS));
        assertFullBlock(acquired.getOcclusionShape(level, POS));
    }

    private static TopOnlySlabBlock block() {
        return new TopOnlySlabBlock(BlockBehaviour.Properties.of());
    }

    private static void assertTopHalf(VoxelShape shape) {
        assertBounds(shape.bounds(), 0.5D, 1.0D);
    }

    private static void assertFullBlock(VoxelShape shape) {
        assertBounds(shape.bounds(), 0.0D, 1.0D);
    }

    private static void assertBounds(AABB bounds, double minY, double maxY) {
        assertEquals(0.0D, bounds.minX);
        assertEquals(minY, bounds.minY);
        assertEquals(0.0D, bounds.minZ);
        assertEquals(1.0D, bounds.maxX);
        assertEquals(maxY, bounds.maxY);
        assertEquals(1.0D, bounds.maxZ);
    }

    private static final class NoBlockEntityLookups implements BlockGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            throw new AssertionError("shape logic must use synchronized block state");
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }
}
