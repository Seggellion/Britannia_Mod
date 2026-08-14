package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.*;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.structure.BannerStructureTransform;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone13RenderFixtures;
import com.seggellion.britannia_mod.client.banner.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerPlacedGeometryAndBoundsTest {
    private static final List<FamilyCase> FOOTPRINTS = List.of(
            new FamilyCase("small", 1, 1),
            new FamilyCase("medium", 1, 2),
            new FamilyCase("medium_wall", 1, 2),
            new FamilyCase("large", 2, 2));

    @BeforeAll
    static void setup() {
        Milestone13RenderFixtures.ensureLoaded();
    }

    @Test
    void everyFootprintOrientationAndFacingUsesPersistedRowMajorWorldGeometry() {
        BlockPos anchor = new BlockPos(-17, 70, -33);
        for (FamilyCase family : FOOTPRINTS) {
            var definition = Milestone13RenderFixtures.definitionForGeometry(family.name);
            var material = Milestone13RenderFixtures.material("cotton");
            var instance = Milestone13RenderFixtures.state(definition, material,
                    Milestone13RenderFixtures.natural(material), Milestone13RenderFixtures.mount("brass"));
            for (BannerOrientation orientation : Milestone13RenderFixtures.renderData().banners().get(definition).supportedOrientations()) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    var entity = Milestone13RenderFixtures.entity(anchor, facing, orientation,
                            family.width, family.height, instance);
                    BannerPlacedRenderState state = Milestone13RenderFixtures.placed(entity, 3, 4);
                    assertFalse(state.fallback(), family.name + "/" + orientation + "/" + facing);
                    assertEquals(family.width, state.persistedWidth());
                    assertEquals(family.height, state.persistedHeight());
                    assertEquals(family.width * family.height, state.occupiedOffsets().size());
                    assertEquals(BannerStructureTransform.spanAxis(facing, orientation), state.spanAxis());
                    assertEquals(Direction.DOWN, state.verticalAxis());
                    assertEquals(BannerAnchorConvention.TOP_LEFT_OR_TOP_INNER, state.anchorConvention());
                    assertEquals(anchor, state.lightingSamplePositions().getFirst());
                    assertEquals(state.occupiedOffsets().stream().map(offset ->
                                    BannerStructureTransform.worldPosition(anchor, facing, orientation, offset)).toList(),
                            state.lightingSamplePositions());
                }
            }
        }
    }

    @Test
    void renderBoundsExactlyCoverEveryPersistedCellPlusFiniteMountMargin() {
        for (BlockPos anchor : List.of(
                new BlockPos(-1, -20, -1), new BlockPos(15, 64, 15), new BlockPos(16, 64, 16))) {
            for (FamilyCase family : FOOTPRINTS) {
                var definition = Milestone13RenderFixtures.definitionForGeometry(family.name);
                var material = Milestone13RenderFixtures.material("linen");
                var instance = Milestone13RenderFixtures.state(definition, material,
                        Milestone13RenderFixtures.natural(material), Milestone13RenderFixtures.mount("iron"));
                for (BannerOrientation orientation : Milestone13RenderFixtures.renderData().banners().get(definition).supportedOrientations()) {
                    for (Direction facing : Direction.Plane.HORIZONTAL) {
                        var entity = Milestone13RenderFixtures.entity(
                                anchor, facing, orientation, family.width, family.height, instance);
                        AABB actual = BannerPlacedRenderBounds.from(entity);
                        AABB expected = expectedBounds(entity.getBlockPos(), facing,
                                orientation, family.width, family.height);
                        assertEquals(expected, actual,
                                anchor + "/" + family.name + "/" + orientation + "/" + facing);
                        assertTrue(Double.isFinite(actual.minX) && Double.isFinite(actual.maxX));
                        if (family.width > 1 || family.height > 1) {
                            assertTrue(actual.getXsize() > 1.0 || actual.getYsize() > 1.0
                                    || actual.getZsize() > 1.0);
                        }
                    }
                }
            }
        }
    }

    @Test
    void geometryStartsAtAnchorBoundaryAndGrowsOnlyAlongPersistedSpan() {
        for (BannerPlacedGeometryFamily family : BannerPlacedGeometryFamily.values()) {
            for (BannerOrientation orientation : BannerOrientation.values()) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    BannerPlacedGeometryPlan plan = BannerPlacedGeometryPlan.create(
                            orientation, facing, family.width(), family.height(), family, false);
                    Direction span = BannerStructureTransform.spanAxis(facing, orientation);
                    Vec3 delta = plan.topRight().subtract(plan.topLeft());
                    assertEquals(0.0, delta.y, 1.0e-12);
                    assertEquals(family.width() - 2.0 * family.horizontalInset(),
                            delta.x * span.getStepX() + delta.z * span.getStepZ(), 1.0e-12);
                    assertEquals(0.0,
                            delta.x * span.getStepZ() - delta.z * span.getStepX(), 1.0e-12);
                    // Cloth height is the cloth WIDTH, not the footprint height less its inset:
                    // the quad must stay square because the whole square texture is mapped onto
                    // it. verticalInset positions the top edge only.
                    assertEquals(family.width() - 2.0 * family.horizontalInset(),
                            plan.topLeft().y - plan.bottomLeft().y, 1.0e-12);
                    assertEquals(1.0 - family.verticalInset(), plan.topLeft().y, 1.0e-12);
                    Vec3 mountDelta = plan.mountTopRight().subtract(plan.mountTopLeft());
                    assertEquals(family.width() + 0.25,
                            mountDelta.x * span.getStepX() + mountDelta.z * span.getStepZ(), 1.0e-12);
                }
            }
        }
    }

    @Test
    void parallelAndPerpendicularPlanesPreserveMilestoneTwelveAxes() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var parallel = BannerPlacedGeometryPlan.create(BannerOrientation.WALL_PARALLEL,
                    facing, 2, 2, BannerPlacedGeometryFamily.LARGE, false);
            var perpendicular = BannerPlacedGeometryPlan.create(BannerOrientation.WALL_PERPENDICULAR,
                    facing, 2, 2, BannerPlacedGeometryFamily.LARGE, false);
            assertAxis(parallel.topRight().subtract(parallel.topLeft()), facing.getCounterClockWise());
            assertAxis(perpendicular.topRight().subtract(perpendicular.topLeft()), facing);
            assertEquals(facing, parallel.frontNormal());
            assertEquals(facing.getClockWise(), perpendicular.frontNormal());
        }
    }

    @Test
    void changedDefinitionDimensionsCannotResizePersistedOccupancy() {
        var definition = Milestone13RenderFixtures.definitionForGeometry("large");
        var material = Milestone13RenderFixtures.material("silk");
        var instance = Milestone13RenderFixtures.state(definition, material,
                Milestone13RenderFixtures.natural(material), Milestone13RenderFixtures.mount("brass"));
        BlockPos anchor = new BlockPos(15, 80, 15);
        var entity = Milestone13RenderFixtures.entity(anchor, Direction.SOUTH,
                BannerOrientation.WALL_PARALLEL, 1, 1, instance);
        BannerPlacedRenderState state = Milestone13RenderFixtures.placed(entity, 1, 1);
        assertEquals(1, state.persistedWidth());
        assertEquals(1, state.persistedHeight());
        assertEquals(BannerPlacedRenderFailure.GEOMETRY_FOOTPRINT_MISMATCH, state.failure());
        assertEquals(expectedBounds(anchor, Direction.SOUTH,
                        BannerOrientation.WALL_PARALLEL, 1, 1),
                state.renderBounds());
        assertEquals(instance, entity.bannerState().orElseThrow());
        assertEquals(1, entity.placedStructure().orElseThrow().width());
    }

    @Test
    void missingStructureUsesOneCellSafeBoundsWithoutInventingOccupancy() {
        var definition = Milestone13RenderFixtures.definitionForGeometry("small");
        var material = Milestone13RenderFixtures.material("cotton");
        var instance = Milestone13RenderFixtures.state(definition, material,
                Milestone13RenderFixtures.natural(material), Milestone13RenderFixtures.mount("brass"));
        var entity = Milestone13RenderFixtures.entity(BlockPos.ZERO, Direction.NORTH,
                BannerOrientation.WALL_PARALLEL, 1, 1, instance);
        net.minecraft.nbt.CompoundTag malformed = entity.saveWithoutMetadata(
                net.minecraft.core.RegistryAccess.EMPTY);
        malformed.getCompound(BannerBlockEntityTag.PLACEMENT).putInt("schema_version", 999);
        entity.loadWithComponents(malformed, net.minecraft.core.RegistryAccess.EMPTY);
        BannerPlacedRenderState state = Milestone13RenderFixtures.placed(entity, 2, 3);
        assertTrue(state.fallback());
        assertEquals(new AABB(BlockPos.ZERO).inflate(
                BannerPlacedRenderBounds.MOUNT_AND_CLOTH_MARGIN), state.renderBounds());
    }

    private static AABB expectedBounds(
            BlockPos anchor, Direction facing, BannerOrientation orientation, int width, int height) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int vertical = 0; vertical < height; vertical++) {
            for (int horizontal = 0; horizontal < width; horizontal++) {
                var pos = BannerStructureTransform.worldPosition(anchor, facing, orientation,
                        new com.seggellion.britannia_mod.banner.structure.BannerLocalOffset(
                                horizontal, vertical));
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX() + 1);
                maxY = Math.max(maxY, pos.getY() + 1);
                maxZ = Math.max(maxZ, pos.getZ() + 1);
            }
        }
        double margin = BannerPlacedRenderBounds.MOUNT_AND_CLOTH_MARGIN;
        return new AABB(minX - margin, minY - margin, minZ - margin,
                maxX + margin, maxY + margin, maxZ + margin);
    }

    private static void assertAxis(Vec3 delta, Direction expected) {
        assertTrue(delta.dot(new Vec3(expected.getStepX(), 0, expected.getStepZ())) > 0);
        assertEquals(0.0, delta.x * expected.getStepZ() - delta.z * expected.getStepX(), 1.0e-12);
    }

    private record FamilyCase(String name, int width, int height) {
    }

    /** Avoids importing common state into production solely for a test-only malformed-tag construction. */
    private static final class BannerBlockEntityTag {
        private static final String PLACEMENT =
                com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity.PLACEMENT_TAG;
    }
}
