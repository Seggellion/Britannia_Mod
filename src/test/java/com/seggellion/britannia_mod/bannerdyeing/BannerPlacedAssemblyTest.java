package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.math.Axis;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.structure.BannerStructureTransform;
import com.seggellion.britannia_mod.client.banner.BannerAssemblyAssets;
import com.seggellion.britannia_mod.client.banner.BannerAssetAvailability;
import com.seggellion.britannia_mod.client.banner.BannerPlacedAssembly;
import com.seggellion.britannia_mod.client.banner.BannerPlacedGeometryFamily;
import com.seggellion.britannia_mod.client.banner.BannerPlacedGeometryPlan;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The placed-banner assembly: pole, brackets, and the rotation that carries the one authored
 * canonical frame onto all four facings and both orientations.
 *
 * <p>The rotation is the part with no visual fallback -- a sign error puts the pole through the
 * wall and still compiles -- so it is checked here by applying the renderer's own quaternion to
 * the canonical basis vectors and comparing against the span and normal the rest of the banner
 * system already derives independently in {@link BannerStructureTransform}.
 */
class BannerPlacedAssemblyTest {

    private static final double EPSILON = 1.0e-6;

    // Resolved through the classpath (main resources are an exploded directory at test runtime)
    // so these do not depend on the Gradle worker's working directory.
    private static final Path MODELS = classpathDirectory("assets/britannia_mod/models");
    private static final Path TEXTURES = classpathDirectory("assets/britannia_mod/textures");

    private static Path classpathDirectory(String resource) {
        java.net.URL url = BannerPlacedAssemblyTest.class.getClassLoader().getResource(resource);
        if (url == null) {
            throw new IllegalStateException("resource directory not on test classpath: " + resource);
        }
        try {
            return Path.of(url.toURI());
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static BannerPlacedGeometryPlan plan(BannerOrientation orientation, Direction facing) {
        BannerPlacedGeometryFamily family = BannerPlacedGeometryFamily.MEDIUM;
        return BannerPlacedGeometryPlan.create(
                orientation, facing, family.width(), family.height(), family, false);
    }

    private static Vec3 rotate(float degrees, Vec3 vector) {
        Vector3f rotated = new Vector3f((float) vector.x, (float) vector.y, (float) vector.z)
                .rotate(Axis.YP.rotationDegrees(degrees));
        return new Vec3(rotated.x(), rotated.y(), rotated.z());
    }

    private static Vec3 of(Direction direction) {
        return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static void assertVectorEquals(Vec3 expected, Vec3 actual, String message) {
        assertEquals(expected.x, actual.x, EPSILON, message + " x");
        assertEquals(expected.y, actual.y, EPSILON, message + " y");
        assertEquals(expected.z, actual.z, EPSILON, message + " z");
    }

    @Test
    void canonicalFrameRotatesOntoTheSpanAxisAndFrontNormal() {
        for (BannerOrientation orientation : BannerOrientation.values()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BannerPlacedGeometryPlan geometry = plan(orientation, facing);
                BannerPlacedAssembly assembly = BannerPlacedAssembly.from(geometry, orientation);
                float yaw = assembly.yRotationDegrees();
                String context = orientation + "/" + facing;

                // +X is authored along the pole, so it must land on the banner's span axis.
                assertVectorEquals(of(BannerStructureTransform.spanAxis(facing, orientation)),
                        rotate(yaw, new Vec3(1, 0, 0)), context + " canonical +X -> span");
                // +Z is authored pointing away from the wall, i.e. the cloth's front normal.
                assertVectorEquals(of(geometry.frontNormal()),
                        rotate(yaw, new Vec3(0, 0, 1)), context + " canonical +Z -> front normal");
            }
        }
    }

    @Test
    void bracketPlateFacesTheWallForBothOrientations() {
        for (BannerOrientation orientation : BannerOrientation.values()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BannerPlacedGeometryPlan geometry = plan(orientation, facing);
                BannerPlacedAssembly assembly = BannerPlacedAssembly.from(geometry, orientation);
                // The bracket's wall plate is authored at canonical -Z.
                Vec3 plateNormal = rotate(
                        assembly.yRotationDegrees() + assembly.bracketExtraYRotationDegrees(),
                        new Vec3(0, 0, -1));
                Direction wall = orientation == BannerOrientation.WALL_PARALLEL
                        ? facing.getOpposite()                       // wall behind the cloth
                        : BannerStructureTransform.spanAxis(facing, orientation).getOpposite();
                assertVectorEquals(of(wall), plateNormal,
                        orientation + "/" + facing + " bracket plate must face the wall");
            }
        }
    }

    @Test
    void poleHangsOnTheClothTopEdgeAtTheRightHeightAndDepth() {
        for (BannerOrientation orientation : BannerOrientation.values()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BannerPlacedGeometryPlan geometry = plan(orientation, facing);
                BannerPlacedAssembly assembly = BannerPlacedAssembly.from(geometry, orientation);
                String context = orientation + "/" + facing;

                // The pole carries the cloth, so it must lie on the cloth's top edge line: same
                // height, and no offset off that line once the span component is removed.
                assertEquals(geometry.topLeft().y, assembly.poleCenter().y, EPSILON,
                        context + " pole height");
                Vec3 spanUnit = geometry.topRight().subtract(geometry.topLeft()).normalize();
                Vec3 offLine = assembly.poleCenter().subtract(geometry.topLeft());
                Vec3 perpendicularPart = offLine.subtract(spanUnit.scale(offLine.dot(spanUnit)));
                assertEquals(0.0, perpendicularPart.length(), EPSILON,
                        context + " pole must lie in the cloth's own plane");
            }
        }
    }

    /**
     * The bracket exists to bolt the pole to the wall, so its plate has to finish flush on the
     * wall face -- not buried in the wall block, and not floating short of it. This is exactly
     * what a cloth-derived pole gets wrong for a perpendicular banner, whose span axis points
     * out of the wall and whose cloth start therefore moves with the family's horizontal inset.
     */
    @Test
    void everyBracketPlateLandsFlushOnTheWallFace() {
        for (BannerPlacedGeometryFamily family : BannerPlacedGeometryFamily.values()) {
            for (BannerOrientation orientation : BannerOrientation.values()) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    BannerPlacedGeometryPlan geometry = BannerPlacedGeometryPlan.create(
                            orientation, facing, family.width(), family.height(), family, false);
                    BannerPlacedAssembly assembly = BannerPlacedAssembly.from(geometry, orientation);
                    Direction wall = orientation == BannerOrientation.WALL_PARALLEL
                            ? facing.getOpposite()
                            : BannerStructureTransform.spanAxis(facing, orientation).getOpposite();
                    // The plate sits POLE_STANDOFF back from the anchor, along the direction the
                    // bracket's own -Z was rotated to, which is the wall direction.
                    for (Vec3 anchor : assembly.bracketAnchors()) {
                        Vec3 plate = anchor.add(of(wall).scale(BannerPlacedAssembly.POLE_STANDOFF));
                        double onWallAxis = plate.x * wall.getStepX() + plate.z * wall.getStepZ();
                        // The anchor block spans 0..1, so its face on the wall side is at +0.5
                        // measured from the centre along the wall direction.
                        assertEquals(0.5, onWallAxis - 0.5 * (wall.getStepX() + wall.getStepZ()),
                                EPSILON,
                                family + "/" + orientation + "/" + facing
                                        + " bracket plate must finish on the wall face");
                    }
                }
            }
        }
    }

    @Test
    void parallelGetsABracketAtEachEndAndPerpendicularOnlyAtTheWall() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BannerPlacedAssembly parallel = BannerPlacedAssembly.from(
                    plan(BannerOrientation.WALL_PARALLEL, facing), BannerOrientation.WALL_PARALLEL);
            assertEquals(2, parallel.bracketAnchors().size(), "parallel bracket count");

            BannerPlacedGeometryPlan perpendicularPlan =
                    plan(BannerOrientation.WALL_PERPENDICULAR, facing);
            BannerPlacedAssembly perpendicular = BannerPlacedAssembly.from(
                    perpendicularPlan, BannerOrientation.WALL_PERPENDICULAR);
            assertEquals(1, perpendicular.bracketAnchors().size(), "perpendicular bracket count");

            // Its one bracket belongs at the wall end, which for a perpendicular banner is the
            // end of the pole furthest back along the outward span axis.
            Direction span = BannerStructureTransform.spanAxis(
                    facing, BannerOrientation.WALL_PERPENDICULAR);
            Vec3 anchor = perpendicular.bracketAnchors().getFirst();
            Vec3 centre = perpendicular.poleCenter();
            double along = (anchor.x - centre.x) * span.getStepX()
                    + (anchor.z - centre.z) * span.getStepZ();
            assertTrue(along < 0.0,
                    facing + " perpendicular bracket must sit at the wall end, got offset " + along);
        }
    }

    @Test
    void poleVariantIsStablePerPositionAndUsesEveryStrip() {
        BlockPos sample = new BlockPos(120, 64, -37);
        assertEquals(BannerAssemblyAssets.poleModelFor(sample),
                BannerAssemblyAssets.poleModelFor(sample),
                "the same post must always draw the same pole");

        Set<ResourceLocation> seen = new HashSet<>();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                seen.add(BannerAssemblyAssets.poleModelFor(new BlockPos(x, 70, z)));
            }
        }
        assertEquals(BannerAssemblyAssets.POLE_VARIANTS, seen.size(),
                "every authored pole variant should appear across a field of posts");
    }

    @Test
    void everyAssemblyModelAndTextureExistsAndIsRegisteredForBaking() {
        for (ResourceLocation id : BannerAssemblyAssets.MODELS) {
            assertTrue(Files.isRegularFile(MODELS.resolve(id.getPath() + ".json")),
                    "missing model file for " + id);
            assertTrue(BannerAssetAvailability.EXPECTED_MODELS.contains(id),
                    id + " must be registered for baking");
        }
        for (ResourceLocation id : BannerAssemblyAssets.TEXTURES) {
            assertTrue(Files.isRegularFile(TEXTURES.resolve(id.getPath() + ".png")),
                    "missing texture file for " + id);
            assertTrue(BannerAssetAvailability.EXPECTED_TEXTURES.contains(id),
                    id + " must be registered for stitching");
        }
    }

    /**
     * Each pole model must map its strip of the shared sheet, and only its strip. A copy-paste
     * slip here shows two posts wearing the same pole, or a pole sliced across two strips.
     */
    @Test
    void everyPoleModelMapsItsOwnStripOfTheSharedSheet() {
        int[][] strips = {{8, 15}, {27, 35}, {46, 57}, {66, 78}, {86, 96}, {104, 116}};
        Set<String> ranges = new HashSet<>();
        for (int index = 0; index < BannerAssemblyAssets.POLE_VARIANTS; index++) {
            ResourceLocation id = BannerAssemblyAssets.POLE_MODELS.get(index);
            JsonObject model = read(MODELS.resolve(id.getPath() + ".json"));
            assertEquals("britannia_mod:banner/mount/poles",
                    model.getAsJsonObject("textures").get("pole").getAsString(), id + " texture");

            double expectedV0 = strips[index][0] / 8.0;
            double expectedV1 = (strips[index][1] + 1) / 8.0;
            for (var element : model.getAsJsonArray("elements")) {
                JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
                for (var entry : faces.entrySet()) {
                    var uv = entry.getValue().getAsJsonObject().getAsJsonArray("uv");
                    assertEquals(expectedV0, uv.get(1).getAsDouble(), EPSILON,
                            id + " " + entry.getKey() + " v0");
                    assertEquals(expectedV1, uv.get(3).getAsDouble(), EPSILON,
                            id + " " + entry.getKey() + " v1");
                }
            }
            assertTrue(ranges.add(expectedV0 + ":" + expectedV1), "duplicate strip for " + id);
        }
        assertEquals(BannerAssemblyAssets.POLE_VARIANTS, ranges.size());
    }

    private static JsonObject read(Path path) {
        assertTrue(Files.isRegularFile(path), "missing " + path);
        try (var reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            JsonObject parsed = JsonParser.parseReader(reader).getAsJsonObject();
            assertNotNull(parsed);
            return parsed;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
