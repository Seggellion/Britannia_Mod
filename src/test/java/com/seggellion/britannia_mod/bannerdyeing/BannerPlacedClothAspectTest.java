package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.client.banner.BannerPlacedGeometryFamily;
import com.seggellion.britannia_mod.client.banner.BannerPlacedGeometryPlan;
import com.seggellion.britannia_mod.client.banner.BannerPlacedRenderBounds;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Guards the placed-banner cloth against the aspect-distortion defect: every banner texture is
 * square (128x128) and {@code BannerBlockEntityRenderer} maps the WHOLE sprite onto the cloth
 * quad, so a non-square quad stretches every banner in that family. The medium family shipped
 * with a 0.5 x 1.9375 cloth -- a 74% horizontal squeeze -- which is what these tests would have
 * caught.
 */
class BannerPlacedClothAspectTest {

    // Resolved through the classpath (the test runtime sees main resources as an exploded
    // directory) so these do not depend on the Gradle worker's working directory.
    private static final Path DEFINITIONS = classpathDirectory("data/britannia_mod/banner_definitions");
    private static final Path MODELS = classpathDirectory("assets/britannia_mod/models");

    /**
     * Families deliberately not held to the authored-size check, recorded here rather than by
     * loosening the tolerance for everyone so the deviation stays visible.
     *
     * <p>X_SMALL renders at 0.56x the size its six definitions' geometry.json files imply. That
     * is a real deviation with the same origin as the medium distortion this class guards
     * against -- the family constant was tuned to match {@code banner/placeholder/x_small}
     * (0.375 blocks, exactly) rather than the artwork that later shipped (~0.665 blocks) -- but
     * unlike the medium defect it is UNIFORM, so those banners are correctly proportioned and
     * merely smaller than their Blockbench source. Whether an "extra small" banner should be
     * 0.375 or 0.665 blocks is a visual decision for the owner, not a correctness one, so it is
     * left alone; {@link #everyFamilyProducesASquareCloth} still holds X_SMALL to the
     * no-distortion invariant.
     */
    private static final java.util.Set<BannerPlacedGeometryFamily> SIZE_EXEMPT =
            java.util.EnumSet.of(BannerPlacedGeometryFamily.X_SMALL);

    private static Path classpathDirectory(String resource) {
        java.net.URL url = BannerPlacedClothAspectTest.class.getClassLoader().getResource(resource);
        if (url == null) {
            throw new IllegalStateException("resource directory not on test classpath: " + resource);
        }
        try {
            return Path.of(url.toURI());
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void everyFamilyProducesItsApprovedClothAspect() {
        for (BannerPlacedGeometryFamily family : BannerPlacedGeometryFamily.values()) {
            BannerPlacedGeometryPlan plan = BannerPlacedGeometryPlan.create(
                    BannerOrientation.WALL_PARALLEL, Direction.NORTH,
                    family.width(), family.height(), family, false);
            double width = plan.topRight().subtract(plan.topLeft()).length();
            double height = plan.topLeft().y - plan.bottomLeft().y;
            // The cloth was square until the owner's 2026-08-25 review asked medium and large
            // for +20% width and +30% height. The whole square texture still maps onto the
            // quad, so that aspect change IS a deliberate 8.3% vertical stretch of the artwork;
            // what must never happen is an accidental one, so the aspect is pinned to exactly
            // the approved factors rather than merely "square".
            double approvedAspect = family.heightScale() / family.widthScale();
            assertEquals(approvedAspect, height / width, 1.0e-9,
                    family + " cloth aspect drifted from its approved factors (" + width + " x "
                            + height + "); the whole square texture maps onto it, so any "
                            + "unapproved aspect distorts every banner in the family");
        }
    }

    @Test
    void fallbackClothIsAlsoSquareForEveryFootprint() {
        for (BannerPlacedGeometryFamily family : BannerPlacedGeometryFamily.values()) {
            BannerPlacedGeometryPlan plan = BannerPlacedGeometryPlan.create(
                    BannerOrientation.WALL_PARALLEL, Direction.NORTH,
                    family.width(), family.height(), family, true);
            double width = plan.topRight().subtract(plan.topLeft()).length();
            assertEquals(width, plan.topLeft().y - plan.bottomLeft().y, 1.0e-9,
                    "fallback cloth for footprint " + family.width() + "x" + family.height()
                            + " is not square");
        }
    }

    /**
     * Ties the rendered size back to the authored art. Each geometry.json states the artwork's
     * true size in blocks (its element bounds) and how much of the square texture that artwork
     * occupies (its UV fraction); dividing gives the full-texture quad the author implied. The
     * placed cloth should reproduce that, and a family constant is one value shared by a whole
     * catalogue group, so this allows a modest per-definition spread rather than exactness.
     */
    @Test
    void everyDefinitionRendersNearItsAuthoredSize() {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Path file : definitionFiles()) {
            JsonObject definition = readJson(file);
            JsonObject dimensions = definition.getAsJsonObject("dimensions");
            int width = dimensions.get("width_blocks").getAsInt();
            int height = dimensions.get("height_blocks").getAsInt();
            ResourceLocation geometry = ResourceLocation.parse(
                    definition.getAsJsonObject("assets").get("geometry").getAsString());
            Optional<BannerPlacedGeometryFamily> family = BannerPlacedGeometryFamily.from(
                    geometry, new BannerDimensions(width, height, false));
            if (family.isEmpty()) {
                problems.add(file.getFileName() + ": no placed geometry family");
                continue;
            }
            Optional<Double> authored = authoredQuadSize(geometry);
            if (authored.isEmpty()) {
                problems.add(file.getFileName() + ": could not read authored quad from " + geometry);
                continue;
            }
            // Compare the family's BASELINE against the artwork, not the cloth it now draws:
            // the owner's approved scale factors are a deliberate presentation choice layered
            // on top, pinned exactly in BannerFamilyDimensionsTest. What this test protects is
            // the tie underneath -- that each family is still tuned to the art it serves.
            double rendered = family.orElseThrow().clothBaseline();
            double ratio = rendered / authored.orElseThrow();
            if (SIZE_EXEMPT.contains(family.orElseThrow())) {
                checked++;
                continue;
            }
            if (ratio < 0.75 || ratio > 1.25) {
                problems.add("%s: renders at %.2fx its authored size (%.3f vs %.3f blocks, family %s)"
                        .formatted(file.getFileName(), ratio, rendered, authored.orElseThrow(),
                                family.orElseThrow()));
            }
            checked++;
        }
        assertTrue(checked >= 35, "expected the full catalogue, checked " + checked);
        if (!problems.isEmpty()) {
            fail("placed banners diverge from their authored size (" + problems.size() + "): " + problems);
        }
    }

    @Test
    void renderBoundsMarginCoversTheWidestClothOverhang() {
        double worstOverhang = 0.0;
        for (BannerPlacedGeometryFamily family : BannerPlacedGeometryFamily.values()) {
            BannerPlacedGeometryPlan plan = BannerPlacedGeometryPlan.create(
                    BannerOrientation.WALL_PARALLEL, Direction.NORTH,
                    family.width(), family.height(), family, false);
            double clothWidth = plan.topRight().subtract(plan.topLeft()).length();
            Vec3 mountDelta = plan.mountTopRight().subtract(plan.mountTopLeft());
            worstOverhang = Math.max(worstOverhang, Math.max(
                    (clothWidth - family.width()) / 2.0,
                    (mountDelta.length() - family.width()) / 2.0));
        }
        assertTrue(BannerPlacedRenderBounds.MOUNT_AND_CLOTH_MARGIN >= worstOverhang,
                "render bounds margin " + BannerPlacedRenderBounds.MOUNT_AND_CLOTH_MARGIN
                        + " is smaller than the widest overhang " + worstOverhang
                        + ", so a banner can be culled while still on screen");
    }

    /** Authored full-texture quad size in blocks: element size divided by its UV fraction. */
    private static Optional<Double> authoredQuadSize(ResourceLocation geometry) {
        Path model = MODELS.resolve(geometry.getPath() + ".json");
        if (!Files.exists(model)) {
            return Optional.empty();
        }
        JsonObject root = readJson(model);
        if (!root.has("elements")) {
            return Optional.empty();
        }
        for (var element : root.getAsJsonArray("elements")) {
            JsonObject object = element.getAsJsonObject();
            JsonObject faces = object.getAsJsonObject("faces");
            for (var entry : faces.entrySet()) {
                JsonObject face = entry.getValue().getAsJsonObject();
                if (!face.has("uv") || !"#base_texture".equals(
                        face.has("texture") ? face.get("texture").getAsString() : null)) {
                    continue;
                }
                JsonArray from = object.getAsJsonArray("from");
                JsonArray to = object.getAsJsonArray("to");
                JsonArray uv = face.getAsJsonArray("uv");
                double elementWidth = (to.get(0).getAsDouble() - from.get(0).getAsDouble()) / 16.0;
                double uvFraction =
                        Math.abs(uv.get(2).getAsDouble() - uv.get(0).getAsDouble()) / 16.0;
                if (uvFraction <= 0.0) {
                    continue;
                }
                return Optional.of(elementWidth / uvFraction);
            }
        }
        return Optional.empty();
    }

    private static List<Path> definitionFiles() {
        assertTrue(Files.isDirectory(DEFINITIONS), "missing " + DEFINITIONS);
        try (Stream<Path> files = Files.list(DEFINITIONS)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static JsonObject readJson(Path path) {
        try (var reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
