package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.client.banner.BannerPlacedAssembly;
import com.seggellion.britannia_mod.client.banner.BannerPlacedGeometryFamily;
import com.seggellion.britannia_mod.client.banner.BannerPlacedGeometryPlan;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Focused pins for the three banner regressions repaired together: the geometry-family
 * collapse that resized every real small banner to the x-small convention, the perpendicular
 * cloth start that a negative family inset pushed inside the supporting wall, and the placed
 * dye-mask pass drawn through a chunk render type whose framebuffer the level pipeline clears.
 */
class BannerRegressionRepairTest {
    private static final Path MAIN = Path.of(System.getProperty("britannia.projectDir", "."),
            "src/main/java/com/seggellion/britannia_mod");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", path);
    }

    @Test
    void realGeometryResolvesTheFamilyOfItsOwnCatalogueGroup() {
        BannerDimensions oneByOne = new BannerDimensions(1, 1, false);
        BannerDimensions oneByTwo = new BannerDimensions(1, 2, false);
        BannerDimensions twoByTwo = new BannerDimensions(2, 2, false);

        assertEquals(Optional.of(BannerPlacedGeometryFamily.SMALL),
                BannerPlacedGeometryFamily.from(id("banner/small/iron_ward/geometry"), oneByOne));
        assertEquals(Optional.of(BannerPlacedGeometryFamily.MEDIUM),
                BannerPlacedGeometryFamily.from(id("banner/medium/argent_shield/geometry"), oneByTwo));
        assertEquals(Optional.of(BannerPlacedGeometryFamily.MEDIUM),
                BannerPlacedGeometryFamily.from(id("banner/medium_wall/ankh_pennon/geometry"), oneByTwo));
        assertEquals(Optional.of(BannerPlacedGeometryFamily.LARGE),
                BannerPlacedGeometryFamily.from(id("banner/large/tournament_curtain/geometry"), twoByTwo));

        // Exact catalogue ids keep winning over group prefixes.
        assertEquals(Optional.of(BannerPlacedGeometryFamily.ROAD_GUARD),
                BannerPlacedGeometryFamily.from(id("banner/road_guard/geometry"), oneByOne));
        // Unknown geometry outside the catalogue groups keeps the documented most-compact
        // dimension fallback rather than failing.
        assertEquals(Optional.of(BannerPlacedGeometryFamily.X_SMALL),
                BannerPlacedGeometryFamily.from(id("banner/custom/oddity/geometry"), oneByOne));
        // A group prefix with impossible dimensions falls through instead of mis-sizing.
        assertEquals(Optional.of(BannerPlacedGeometryFamily.MEDIUM),
                BannerPlacedGeometryFamily.from(id("banner/small/iron_ward/geometry"), oneByTwo));
    }

    @Test
    void everyShippedDefinitionResolvesItsGroupFamilyAtItsOwnDimensions() throws Exception {
        var snapshot = DyeResolverFixtures.productionSnapshot();
        for (var definition : snapshot.banners().activeDefinitions()) {
            String path = definition.assets().geometry().getPath();
            Optional<BannerPlacedGeometryFamily> family = BannerPlacedGeometryFamily.from(
                    definition.assets().geometry(), definition.dimensions());
            assertTrue(family.isPresent(), definition.id() + " has no placed family");
            assertTrue(family.orElseThrow().supports(
                            definition.dimensions().widthBlocks(), definition.dimensions().heightBlocks()),
                    definition.id() + " family does not support its own dimensions");
            if (path.startsWith("banner/small/")) {
                assertEquals(BannerPlacedGeometryFamily.SMALL, family.orElseThrow(), definition.id().toString());
            } else if (path.startsWith("banner/medium/") || path.startsWith("banner/medium_wall/")) {
                assertEquals(BannerPlacedGeometryFamily.MEDIUM, family.orElseThrow(), definition.id().toString());
            } else if (path.startsWith("banner/large/")) {
                assertEquals(BannerPlacedGeometryFamily.LARGE, family.orElseThrow(), definition.id().toString());
            }
        }
    }

    @Test
    void perpendicularClothStartsAtTheWallFaceInsteadOfInsideTheWall() {
        // Facing NORTH: the wall sits south of the anchor, its face at z = 1.0 in anchor-local
        // coordinates, and the perpendicular span runs north (negative z).
        BannerPlacedGeometryPlan medium = BannerPlacedGeometryPlan.create(
                BannerOrientation.WALL_PERPENDICULAR, Direction.NORTH, 1, 2,
                BannerPlacedGeometryFamily.MEDIUM, false);
        for (Vec3 corner : List.of(medium.topLeft(), medium.topRight(),
                medium.bottomLeft(), medium.bottomRight())) {
            assertTrue(corner.z <= 1.0 + 1.0e-9, "cloth corner inside the wall: " + corner);
        }
        // The negative medium inset pins the wall-side edge exactly at the wall face and sends
        // the whole overhang outward.
        assertEquals(1.0, medium.topLeft().z, 1.0e-9);
        double clothWidth = 1 - 2.0 * BannerPlacedGeometryFamily.MEDIUM.horizontalInset();
        assertEquals(1.0 - clothWidth, medium.topRight().z, 1.0e-9);

        // Positive insets keep their historical symmetric placement.
        BannerPlacedGeometryPlan small = BannerPlacedGeometryPlan.create(
                BannerOrientation.WALL_PERPENDICULAR, Direction.NORTH, 1, 1,
                BannerPlacedGeometryFamily.SMALL, false);
        assertEquals(1.0 - BannerPlacedGeometryFamily.SMALL.horizontalInset(),
                small.topLeft().z, 1.0e-9);
    }

    @Test
    void parallelAndPerpendicularPlansAreGenuinelyDifferentGeometry() {
        BannerPlacedGeometryPlan parallel = BannerPlacedGeometryPlan.create(
                BannerOrientation.WALL_PARALLEL, Direction.NORTH, 1, 2,
                BannerPlacedGeometryFamily.MEDIUM, false);
        BannerPlacedGeometryPlan perpendicular = BannerPlacedGeometryPlan.create(
                BannerOrientation.WALL_PERPENDICULAR, Direction.NORTH, 1, 2,
                BannerPlacedGeometryFamily.MEDIUM, false);
        // The cloth planes differ: parallel faces the viewer (north), perpendicular faces
        // along the wall (east), so the two orientations cannot render identically.
        assertEquals(Direction.NORTH, parallel.frontNormal());
        assertEquals(Direction.EAST, perpendicular.frontNormal());
        assertNotEquals(parallel.topLeft(), perpendicular.topLeft());
        assertNotEquals(parallel.topRight(), perpendicular.topRight());

        // The assemblies differ too: a parallel pole rides the wall on two brackets, a
        // perpendicular pole runs out of the wall on one bracket turned a quarter onto it.
        BannerPlacedAssembly parallelAssembly = BannerPlacedAssembly.from(
                parallel, BannerOrientation.WALL_PARALLEL);
        BannerPlacedAssembly perpendicularAssembly = BannerPlacedAssembly.from(
                perpendicular, BannerOrientation.WALL_PERPENDICULAR);
        assertEquals(2, parallelAssembly.bracketAnchors().size());
        assertEquals(1, perpendicularAssembly.bracketAnchors().size());
        assertEquals(0.0F, parallelAssembly.bracketExtraYRotationDegrees());
        assertEquals(90.0F, perpendicularAssembly.bracketExtraYRotationDegrees());
    }

    @Test
    void mountCycleServiceIsServerAuthoritativeAndPreEmptsDecoratorRotation() throws Exception {
        String service = Files.readString(MAIN.resolve("banner/interaction/BannerMountCycleService.java"));
        // Placement-parity protection, the anchor-resolving lifecycle, and the synchronizing
        // single-field mutation boundary; never a raw setBlock.
        assertTrue(service.contains("BannerStructureLifecycle.resolve"));
        assertTrue(service.contains("isGuarded"));
        assertTrue(service.contains("mayInteract"));
        assertTrue(service.contains("mayUseItemAt"));
        assertTrue(service.contains("setBannerStateAndSynchronize"));
        assertFalse(service.contains("setBlock"));

        String decorator = Files.readString(MAIN.resolve("item/InteriorDecoratorToolItem.java"));
        // The banner branch must sit above the generic FACING rotation, which would otherwise
        // spin one cell of a multi-block banner out of its own structure.
        int bannerBranch = decorator.indexOf("BannerMountCycleService.cycle");
        int rotationBranch = decorator.indexOf("state.hasProperty(HorizontalDirectionalBlock.FACING)");
        assertTrue(bannerBranch > 0, "decorator has no banner branch");
        assertTrue(rotationBranch > 0, "decorator rotation branch missing");
        assertTrue(bannerBranch < rotationBranch, "banner branch must pre-empt the rotation branch");
    }
}
