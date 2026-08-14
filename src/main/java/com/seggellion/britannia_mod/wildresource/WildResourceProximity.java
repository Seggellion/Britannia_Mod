package com.seggellion.britannia_mod.wildresource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.Fluids;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Loaded-only environmental queries shared by all wild-resource validators. */
public final class WildResourceProximity {
    public static final int ENVIRONMENT_RADIUS = 20;

    private WildResourceProximity() {
    }

    public static boolean hasLavaWithin(ServerLevel level, BlockPos center) {
        return lavaStatus(level, center) == QueryResult.FOUND;
    }

    public static boolean hasWaterWithin(ServerLevel level, BlockPos center) {
        return waterStatus(level, center) == QueryResult.FOUND;
    }

    public static QueryResult lavaStatus(ServerLevel level, BlockPos center) {
        return findMatchingWithin(center, ENVIRONMENT_RADIUS, new LoadedSectionAccess(level),
                WildResourceProximity::isLava);
    }

    public static QueryResult waterStatus(ServerLevel level, BlockPos center) {
        return findMatchingWithin(center, ENVIRONMENT_RADIUS, new LoadedSectionAccess(level),
                WildResourceProximity::isWater);
    }

    static boolean hasMatchingWithin(
            BlockPos center,
            int radius,
            SearchAccess access,
            Predicate<BlockState> matcher
    ) {
        return findMatchingWithin(center, radius, access, matcher) == QueryResult.FOUND;
    }

    static QueryResult findMatchingWithin(
            BlockPos center,
            int radius,
            SearchAccess access,
            Predicate<BlockState> matcher
    ) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius cannot be negative");
        }
        int minimumX = center.getX() - radius;
        int maximumX = center.getX() + radius;
        int minimumY = Math.max(access.minimumY(), center.getY() - radius);
        int maximumY = Math.min(access.maximumYInclusive(), center.getY() + radius);
        int minimumZ = center.getZ() - radius;
        int maximumZ = center.getZ() + radius;
        if (minimumY > maximumY) {
            return QueryResult.NOT_FOUND;
        }

        int minimumChunkX = SectionPos.blockToSectionCoord(minimumX);
        int maximumChunkX = SectionPos.blockToSectionCoord(maximumX);
        int minimumChunkZ = SectionPos.blockToSectionCoord(minimumZ);
        int maximumChunkZ = SectionPos.blockToSectionCoord(maximumZ);

        // An incomplete radius is not authoritative. Fail the attempt instead of loading a chunk.
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (!access.isChunkLoaded(chunkX, chunkZ)) {
                    return QueryResult.INCOMPLETE;
                }
            }
        }

        long radiusSquared = (long) radius * radius;
        int minimumSectionY = SectionPos.blockToSectionCoord(minimumY);
        int maximumSectionY = SectionPos.blockToSectionCoord(maximumY);
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            int startX = Math.max(minimumX, SectionPos.sectionToBlockCoord(chunkX));
            int endX = Math.min(maximumX, SectionPos.sectionToBlockCoord(chunkX) + 15);
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                int startZ = Math.max(minimumZ, SectionPos.sectionToBlockCoord(chunkZ));
                int endZ = Math.min(maximumZ, SectionPos.sectionToBlockCoord(chunkZ) + 15);
                for (int sectionY = minimumSectionY; sectionY <= maximumSectionY; sectionY++) {
                    if (!access.sectionMayContain(chunkX, sectionY, chunkZ, matcher)) {
                        continue;
                    }
                    int startY = Math.max(minimumY, SectionPos.sectionToBlockCoord(sectionY));
                    int endY = Math.min(maximumY, SectionPos.sectionToBlockCoord(sectionY) + 15);
                    for (int y = startY; y <= endY; y++) {
                        long deltaY = y - center.getY();
                        for (int z = startZ; z <= endZ; z++) {
                            long deltaZ = z - center.getZ();
                            for (int x = startX; x <= endX; x++) {
                                long deltaX = x - center.getX();
                                if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= radiusSquared
                                        && matcher.test(access.stateAt(x, y, z))) {
                                    return QueryResult.FOUND;
                                }
                            }
                        }
                    }
                }
            }
        }
        return QueryResult.NOT_FOUND;
    }

    public enum QueryResult {
        FOUND,
        NOT_FOUND,
        INCOMPLETE
    }

    static boolean isLava(BlockState state) {
        return state.is(Blocks.LAVA)
                || state.getFluidState().is(Fluids.LAVA)
                || state.getFluidState().is(Fluids.FLOWING_LAVA)
                || state.getFluidState().is(FluidTags.LAVA);
    }

    static boolean isWater(BlockState state) {
        return state.is(Blocks.WATER)
                || state.getFluidState().is(Fluids.WATER)
                || state.getFluidState().is(Fluids.FLOWING_WATER)
                || state.getFluidState().is(FluidTags.WATER);
    }

    interface SearchAccess {
        int minimumY();

        int maximumYInclusive();

        boolean isChunkLoaded(int chunkX, int chunkZ);

        boolean sectionMayContain(int chunkX, int sectionY, int chunkZ, Predicate<BlockState> matcher);

        BlockState stateAt(int x, int y, int z);
    }

    private static final class LoadedSectionAccess implements SearchAccess {
        private final ServerLevel level;
        private final Map<Long, LevelChunk> loadedChunks = new HashMap<>();

        private LoadedSectionAccess(ServerLevel level) {
            this.level = level;
        }

        @Override
        public int minimumY() {
            return level.getMinBuildHeight();
        }

        @Override
        public int maximumYInclusive() {
            return level.getMaxBuildHeight() - 1;
        }

        @Override
        public boolean isChunkLoaded(int chunkX, int chunkZ) {
            return loadedChunk(chunkX, chunkZ) != null;
        }

        @Override
        public boolean sectionMayContain(
                int chunkX,
                int sectionY,
                int chunkZ,
                Predicate<BlockState> matcher
        ) {
            LevelChunk chunk = loadedChunk(chunkX, chunkZ);
            if (chunk == null) {
                return false;
            }
            int sectionIndex = chunk.getSectionIndexFromSectionY(sectionY);
            LevelChunkSection[] sections = chunk.getSections();
            return sectionIndex >= 0 && sectionIndex < sections.length && sections[sectionIndex].maybeHas(matcher);
        }

        @Override
        public BlockState stateAt(int x, int y, int z) {
            LevelChunk chunk = loadedChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
            if (chunk == null) {
                return Blocks.AIR.defaultBlockState();
            }
            int sectionIndex = chunk.getSectionIndex(y);
            LevelChunkSection[] sections = chunk.getSections();
            if (sectionIndex < 0 || sectionIndex >= sections.length) {
                return Blocks.AIR.defaultBlockState();
            }
            return sections[sectionIndex].getBlockState(x & 15, y & 15, z & 15);
        }

        private LevelChunk loadedChunk(int chunkX, int chunkZ) {
            long key = ChunkPos.asLong(chunkX, chunkZ);
            if (loadedChunks.containsKey(key)) {
                return loadedChunks.get(key);
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            loadedChunks.put(key, chunk);
            return chunk;
        }
    }
}
