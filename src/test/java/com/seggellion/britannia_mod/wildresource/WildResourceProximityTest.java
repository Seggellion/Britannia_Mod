package com.seggellion.britannia_mod.wildresource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildResourceProximityTest {
    private static final BlockPos CENTER = new BlockPos(32, 64, 32);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void squaredEuclideanRadiusIsInclusiveAtTwenty() {
        FakeAccess access = new FakeAccess();
        access.put(CENTER.offset(20, 0, 0), Blocks.LAVA.defaultBlockState());

        assertTrue(WildResourceProximity.hasMatchingWithin(
                CENTER, 20, access, WildResourceProximity::isLava
        ));
        assertFalse(WildResourceProximity.hasMatchingWithin(
                CENTER, 19, access, WildResourceProximity::isLava
        ));
    }

    @Test
    void sourceBeyondTwentyIsRejected() {
        FakeAccess access = new FakeAccess();
        access.put(CENTER.offset(20, 1, 0), Blocks.LAVA.defaultBlockState());

        assertFalse(WildResourceProximity.hasMatchingWithin(
                CENTER, 20, access, WildResourceProximity::isLava
        ));
    }

    @Test
    void unloadedNeighborFailsWithoutReadingBlocks() {
        FakeAccess access = new FakeAccess();
        access.loaded = false;

        assertFalse(WildResourceProximity.hasMatchingWithin(
                CENTER, 20, access, WildResourceProximity::isWater
        ));
        assertEquals(0, access.stateReads);
        assertEquals(
                WildResourceProximity.QueryResult.INCOMPLETE,
                WildResourceProximity.findMatchingWithin(
                        CENTER, 20, access, WildResourceProximity::isWater
                )
        );
    }

    @Test
    void waterAndLavaFluidStatesAreRecognized() {
        assertTrue(WildResourceProximity.isWater(Blocks.WATER.defaultBlockState()));
        assertTrue(WildResourceProximity.isWater(
                Blocks.OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)
        ));
        assertTrue(WildResourceProximity.isLava(Blocks.LAVA.defaultBlockState()));
        assertFalse(WildResourceProximity.isWater(Blocks.LAVA.defaultBlockState()));
        assertFalse(WildResourceProximity.isLava(Blocks.WATER.defaultBlockState()));
    }

    private static final class FakeAccess implements WildResourceProximity.SearchAccess {
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private boolean loaded = true;
        private int stateReads;

        void put(BlockPos position, BlockState state) {
            states.put(position.immutable(), state);
        }

        @Override public int minimumY() { return -64; }
        @Override public int maximumYInclusive() { return 319; }
        @Override public boolean isChunkLoaded(int chunkX, int chunkZ) { return loaded; }

        @Override
        public boolean sectionMayContain(
                int chunkX,
                int sectionY,
                int chunkZ,
                Predicate<BlockState> matcher
        ) {
            return states.entrySet().stream().anyMatch(entry ->
                    SectionPos.blockToSectionCoord(entry.getKey().getX()) == chunkX
                            && SectionPos.blockToSectionCoord(entry.getKey().getY()) == sectionY
                            && SectionPos.blockToSectionCoord(entry.getKey().getZ()) == chunkZ
                            && matcher.test(entry.getValue())
            );
        }

        @Override
        public BlockState stateAt(int x, int y, int z) {
            stateReads++;
            return states.getOrDefault(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }
}
