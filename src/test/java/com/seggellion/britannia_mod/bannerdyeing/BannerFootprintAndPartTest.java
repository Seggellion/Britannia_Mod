package com.seggellion.britannia_mod.bannerdyeing;

import com.seggellion.britannia_mod.bannerdyeing.registry.ProductionBannerCatalogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.block.BannerPartBlock;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.banner.structure.BannerFootprint;
import com.seggellion.britannia_mod.banner.structure.BannerLocalOffset;
import com.seggellion.britannia_mod.banner.structure.BannerPlacedStructure;
import com.seggellion.britannia_mod.banner.structure.BannerStructureTransform;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone6RegisteredTestContent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerFootprintAndPartTest {
    private static RegistrySnapshot production;

    @BeforeAll
    static void setup() throws Exception {
        Milestone6RegisteredTestContent.ensureRegistered();
        production = DyeResolverFixtures.productionSnapshot();
    }

    @Test
    void canonicalCatalogueDerivesFifteenFourteenAndSixRectanglesFromDimensions() {
        Map<String, Long> counts = production.banners().activeDefinitions().stream().collect(Collectors.groupingBy(
                definition -> definition.dimensions().widthBlocks() + "x" + definition.dimensions().heightBlocks(),
                Collectors.counting()));
        assertEquals(Map.of("1x1", 15L, "1x2", 14L, "3x2", 6L), counts);
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT,
                production.banners().activeDefinitions().size());
        for (var definition : production.banners().activeDefinitions()) {
            BannerFootprint.Result result = BannerFootprint.fromDimensions(definition.dimensions());
            assertTrue(result.successful(), definition.id().toString());
            assertEquals(definition.dimensions().widthBlocks() * definition.dimensions().heightBlocks(),
                    result.footprint().offsets().size());
            assertEquals(BannerLocalOffset.ANCHOR, result.footprint().offsets().getFirst());
        }
    }

    @Test
    void supportedRectanglesAreRowMajorAndUnsupportedHeightIsTyped() {
        assertEquals(1, footprint(1, 1).offsets().size());
        assertEquals(2, footprint(1, 2).offsets().size());
        assertEquals(4, footprint(2, 2).offsets().size());
        assertEquals(6, footprint(3, 2).offsets().size());
        assertEquals(java.util.List.of(
                new BannerLocalOffset(0, 0), new BannerLocalOffset(1, 0), new BannerLocalOffset(2, 0),
                new BannerLocalOffset(0, 1), new BannerLocalOffset(1, 1), new BannerLocalOffset(2, 1)),
                footprint(3, 2).offsets());
        assertEquals(BannerFootprint.Failure.UNSUPPORTED_HEIGHT,
                BannerFootprint.fromDimensions(new BannerDimensions(1, 3, true)).failure());
    }

    @Test
    void allFourFacingTransformsUseViewerRightAndInvertExactly() {
        Map<Direction, Direction> expectedRight = Map.of(
                Direction.NORTH, Direction.WEST,
                Direction.SOUTH, Direction.EAST,
                Direction.EAST, Direction.NORTH,
                Direction.WEST, Direction.SOUTH);
        BlockPos anchor = new BlockPos(-16, 80, 15);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertEquals(expectedRight.get(facing), BannerStructureTransform.viewerRight(facing));
            for (BannerLocalOffset offset : footprint(3, 2).offsets()) {
                for (BannerOrientation orientation : BannerOrientation.values()) {
                    BlockPos world = BannerStructureTransform.worldPosition(anchor, facing, orientation, offset);
                    assertEquals(anchor,
                            BannerStructureTransform.anchorPosition(world, facing, orientation, offset));
                    assertEquals(offset, BannerStructureTransform.localOffset(
                            anchor, facing, orientation, world).orElseThrow());
                }
            }
        }
    }

    @Test
    void genericPartEncodesEveryCurrentChildOffsetWithNoEntityAndBothBlocksAreImmovable() throws Exception {
        BannerPartBlock part = new BannerPartBlock(BlockBehaviour.Properties.of().pushReaction(PushReaction.BLOCK));
        for (BannerLocalOffset offset : footprint(3, 2).offsets()) {
            if (offset.isAnchor()) continue;
            var state = part.stateFor(Direction.WEST, offset);
            assertEquals(Direction.WEST, state.getValue(BannerPartBlock.FACING));
            assertEquals(offset, BannerPartBlock.localOffset(state));
            assertEquals(PushReaction.BLOCK, state.getPistonPushReaction());
        }
        BannerBlock anchor = new BannerBlock(BlockBehaviour.Properties.of().pushReaction(PushReaction.BLOCK));
        assertEquals(PushReaction.BLOCK, anchor.defaultBlockState().getPistonPushReaction());
        String partSource = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/banner/block/BannerPartBlock.java"));
        assertFalse(partSource.contains("newBlockEntity"));
        assertFalse(partSource.contains("BannerInstanceState"));
    }

    @Test
    void placedStructureCodecRoundTripsEveryFootprintAndRejectsUnknownSchema() {
        for (BannerDimensions dimensions : java.util.List.of(
                new BannerDimensions(1, 1, true), new BannerDimensions(1, 2, true),
                new BannerDimensions(2, 2, true), new BannerDimensions(3, 2, true))) {
            for (BannerOrientation orientation : BannerOrientation.values()) {
                BannerPlacedStructure structure = BannerPlacedStructure.fromFootprint(
                        orientation, BannerFootprint.fromDimensions(dimensions).footprint());
                var encoded = BannerPlacedStructure.CODEC.encodeStart(JsonOps.INSTANCE, structure)
                        .result().orElseThrow();
                assertEquals(structure,
                        BannerPlacedStructure.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
                encoded.getAsJsonObject().addProperty("schema_version", 999);
                assertTrue(BannerPlacedStructure.CODEC.parse(JsonOps.INSTANCE, encoded).error().isPresent());
            }
        }
    }

    @Test
    void footprintImplementationContainsNoStableIdOrCatalogueGroupSwitch() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/banner/structure/BannerFootprint.java"));
        assertFalse(source.contains("BannerDefinitionId"));
        assertFalse(source.contains("catalogueGroup"));
        assertFalse(source.contains("large_01"));
    }

    private static BannerFootprint footprint(int width, int height) {
        return BannerFootprint.fromDimensions(new BannerDimensions(width, height, true)).footprint();
    }
}
