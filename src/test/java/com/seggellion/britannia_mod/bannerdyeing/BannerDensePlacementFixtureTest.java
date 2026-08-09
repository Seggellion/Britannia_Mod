package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.structure.BannerFootprint;
import com.seggellion.britannia_mod.banner.structure.BannerPlacedStructure;
import com.seggellion.britannia_mod.banner.structure.BannerStructureTransform;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class BannerDensePlacementFixtureTest {
    @Test
    void fourCopiesOfEveryReleasedDefinitionSurviveDenseTransformAndCodecCycles() throws Exception {
        runFixture(4);
    }

    @Test
    void optionalEightCopyStressFixtureRemainsDeterministicAndBounded() throws Exception {
        long started = System.nanoTime();
        int cells = runFixture(8);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        assertTrue(cells >= 280);
        System.out.println("Gate F dense 280-banner structural fixture milliseconds=" + elapsedMillis
                + ", occupied_cells=" + cells);
    }

    private static int runFixture(int copies) throws Exception {
        var definitions = DyeResolverFixtures.productionSnapshot().banners().activeDefinitions();
        assertEquals(35, definitions.size());
        Set<BlockPos> occupiedWorldCells = new HashSet<>();
        int bannerIndex = 0;
        int expectedCells = 0;
        for (int copy = 0; copy < copies; copy++) {
            for (var definition : definitions) {
                BannerOrientation orientation = definition.supportedOrientations()
                        .get((copy + definition.id().hashCode() & Integer.MAX_VALUE)
                                % definition.supportedOrientations().size());
                Direction facing = Direction.Plane.HORIZONTAL.stream().toList()
                        .get((copy + bannerIndex) % 4);
                BannerFootprint footprint = BannerFootprint.fromDimensions(definition.dimensions()).footprint();
                BannerPlacedStructure structure =
                        BannerPlacedStructure.fromFootprint(orientation, footprint);
                var encoded = BannerPlacedStructure.CODEC.encodeStart(JsonOps.INSTANCE, structure).getOrThrow();
                assertEquals(structure,
                        BannerPlacedStructure.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());

                BlockPos anchor = new BlockPos((bannerIndex % 20) * 8, 100,
                        (bannerIndex / 20) * 8);
                structure.occupiedOffsets().forEach(offset -> {
                    BlockPos world = BannerStructureTransform.worldPosition(
                            anchor, facing, orientation, offset);
                    assertTrue(occupiedWorldCells.add(world), "fixture overlap at " + world);
                    assertEquals(anchor, BannerStructureTransform.anchorPosition(
                            world, facing, orientation, offset));
                    assertEquals(offset, BannerStructureTransform.localOffset(
                            anchor, facing, orientation, world).orElseThrow());
                });
                expectedCells += structure.occupiedOffsets().size();
                bannerIndex++;
            }
        }
        assertEquals(35 * copies, bannerIndex);
        assertEquals(expectedCells, occupiedWorldCells.size());

        var wide = definitions.stream().filter(definition -> definition.dimensions().widthBlocks() > 1)
                .findFirst().orElseThrow();
        BannerPlacedStructure crossChunk = BannerPlacedStructure.fromFootprint(
                wide.supportedOrientations().getFirst(),
                BannerFootprint.fromDimensions(wide.dimensions()).footprint());
        Set<Integer> chunks = new HashSet<>();
        crossChunk.occupiedOffsets().forEach(offset -> chunks.add(
                BannerStructureTransform.worldPosition(
                        new BlockPos(15, 80, 0), Direction.SOUTH,
                        BannerOrientation.WALL_PARALLEL, offset).getX() >> 4));
        assertTrue(chunks.size() >= 2);
        return expectedCells;
    }
}
