package com.seggellion.britannia_mod.wildresource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Conservative target/substrate policies shared by resource entry definitions. */
public final class WildResourcePlacementRules {
    private WildResourcePlacementRules() {
    }

    public static BlockPos surfaceCandidate(ServerLevel level, ChunkPos chunk, RandomSource random) {
        var loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
        if (loaded == null) {
            return null;
        }
        int localX = random.nextInt(16);
        int localZ = random.nextInt(16);
        int x = chunk.getMinBlockX() + localX;
        int z = chunk.getMinBlockZ() + localZ;
        int supportY = loaded.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, localX, localZ);
        return new BlockPos(x, supportY + 1, z);
    }

    public static boolean isSafeTarget(ServerLevel level, BlockPos position) {
        return isSafeTarget(position, new LevelPlacementView(level));
    }

    public static boolean isAshSupport(ServerLevel level, BlockPos position) {
        return isAshSupport(position, new LevelPlacementView(level));
    }

    static boolean isSafeTarget(BlockPos position, PlacementView view) {
        BlockState target = view.stateAt(position);
        return target.canBeReplaced() && target.getFluidState().isEmpty() && !view.hasBlockEntity(position);
    }

    static boolean isAshSupport(BlockPos position, PlacementView view) {
        BlockState support = view.stateAt(position.below());
        return support.isFaceSturdy(view, position.below(), Direction.UP) && isNaturalAshSupport(support);
    }

    static boolean isNaturalAshSupport(BlockState state) {
        return state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.NETHERRACK)
                || state.is(Blocks.BASALT)
                || state.is(Blocks.BLACKSTONE)
                || state.is(Blocks.MAGMA_BLOCK);
    }

    interface PlacementView extends net.minecraft.world.level.BlockGetter {
        BlockState stateAt(BlockPos position);

        boolean hasBlockEntity(BlockPos position);

        @Override
        default BlockState getBlockState(BlockPos position) {
            return stateAt(position);
        }
    }

    private record LevelPlacementView(ServerLevel level) implements PlacementView {
        @Override
        public BlockState stateAt(BlockPos position) {
            return level.getBlockState(position);
        }

        @Override
        public boolean hasBlockEntity(BlockPos position) {
            return level.getBlockEntity(position) != null;
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos position) {
            return level.getBlockEntity(position);
        }

        @Override
        public net.minecraft.world.level.material.FluidState getFluidState(BlockPos position) {
            return level.getFluidState(position);
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }
    }
}
