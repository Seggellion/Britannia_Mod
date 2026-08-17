package com.seggellion.britannia_mod.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Well-formedness contract for the animated fountain water.
 *
 * <p>The published model expressed the water as three ill-formed elements: one cuboid inverted on
 * all three axes (an exact duplicate of its neighbour, so their side faces were coincident and
 * coplanar with opposite winding), one zero-height cuboid, and a no-op {@code "rotation"} of angle
 * zero on each. That last part is what made the others fatal rather than merely untidy:
 * {@code FaceBakery#bakeQuad} only calls {@code recalculateWinding} when an element declares no
 * rotation, so a zero-angle rotation baked the un-normalised winding straight into the quads.
 *
 * <p>Vanilla tolerated it - terrain shaders there ignore the vertex normal, and back-face culling
 * happened to hide one of each coincident pair. Iris reconstructs normals and tangents from that
 * winding and sorts translucent geometry, so the water was culled away and disappeared while the
 * stone still drew. Every malformed element in the model used the water texture, and the water was
 * exactly what went missing.
 *
 * <p>These assertions are static because no harness here can execute a shader pipeline; they lock
 * the geometry contract so the defect cannot silently return through a model re-export.
 */
class FountainWaterGeometryTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = PROJECT.resolve("src/main/resources/assets/britannia_mod");
    private static final Path MODEL = ASSETS.resolve("models/block/new_assets/fountain.json");
    private static final String WATER_TEXTURE = "britannia_mod:block/new_assets/fountain_water";

    private static JsonObject model() throws Exception {
        return JsonParser.parseString(Files.readString(MODEL)).getAsJsonObject();
    }

    @Test
    void noElementIsInvertedOrZeroVolume() throws Exception {
        JsonArray elements = model().getAsJsonArray("elements");
        for (int i = 0; i < elements.size(); i++) {
            JsonObject element = elements.get(i).getAsJsonObject();
            JsonArray from = element.getAsJsonArray("from");
            JsonArray to = element.getAsJsonArray("to");
            for (int axis = 0; axis < 3; axis++) {
                double lo = from.get(axis).getAsDouble();
                double hi = to.get(axis).getAsDouble();
                assertTrue(lo <= hi, "element " + i + " is inverted on axis " + axis
                        + " (from " + lo + " > to " + hi + "); vanilla bakes this with reversed"
                        + " winding and shader pipelines discard it");
                assertTrue(hi - lo >= 0.001D, "element " + i + " has zero thickness on axis " + axis
                        + "; give it a sliver of depth rather than a degenerate volume");
            }
        }
    }

    @Test
    void noWaterElementCarriesANoOpRotationThatWouldSuppressWindingNormalisation() throws Exception {
        JsonArray elements = model().getAsJsonArray("elements");
        for (int i = 0; i < elements.size(); i++) {
            JsonObject element = elements.get(i).getAsJsonObject();
            if (!usesWater(element) || !element.has("rotation")) {
                continue;
            }
            double angle = element.getAsJsonObject("rotation").get("angle").getAsDouble();
            assertFalse(angle == 0.0D, "element " + i + " declares a zero-angle rotation, which makes"
                    + " FaceBakery skip recalculateWinding and bakes un-normalised winding into the"
                    + " water quads");
        }
    }

    @Test
    void waterSideFacesAreNotDrawnTwiceByCoincidentElements() throws Exception {
        JsonArray elements = model().getAsJsonArray("elements");
        java.util.Set<String> extents = new java.util.HashSet<>();
        for (int i = 0; i < elements.size(); i++) {
            JsonObject element = elements.get(i).getAsJsonObject();
            if (!usesWater(element)) {
                continue;
            }
            String extent = element.getAsJsonArray("from") + "->" + element.getAsJsonArray("to");
            assertTrue(extents.add(extent),
                    "two water elements occupy the identical volume " + extent
                            + "; coincident coplanar translucent faces z-fight and are the shape the"
                            + " inverted duplicate took");
        }
    }

    @Test
    void theWaterIsStillPresentAndStillUsesTheAnimatedSprite() throws Exception {
        JsonObject model = model();
        assertEquals(WATER_TEXTURE, model.getAsJsonObject("textures").get("1").getAsString(),
                "water texture reference changed");

        long waterFaces = 0;
        for (JsonElement raw : model.getAsJsonArray("elements")) {
            JsonObject faces = raw.getAsJsonObject().getAsJsonObject("faces");
            for (String side : faces.keySet()) {
                if ("#1".equals(faces.getAsJsonObject(side).get("texture").getAsString())) {
                    waterFaces++;
                }
            }
        }
        assertTrue(waterFaces >= 6, "the fountain lost its water surfaces: " + waterFaces);
    }

    @Test
    void theAnimationMetadataStillDescribesAMultiFrameSprite() throws Exception {
        Path texture = ASSETS.resolve("textures/block/new_assets/fountain_water.png");
        Path meta = ASSETS.resolve("textures/block/new_assets/fountain_water.png.mcmeta");
        assertTrue(Files.exists(texture), "animated water texture missing");
        assertTrue(Files.exists(meta), "water animation metadata missing; the fountain would go static");

        JsonObject animation = JsonParser.parseString(Files.readString(meta))
                .getAsJsonObject().getAsJsonObject("animation");
        assertTrue(animation.has("frametime"), "animation frametime missing");
        assertTrue(animation.get("frametime").getAsInt() > 0, "animation frametime must be positive");
    }

    @Test
    void theFountainStillRendersOnTheTranslucentLayer() throws Exception {
        String client = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/ClientModSetup.java"));
        assertTrue(client.contains(
                        "setRenderLayer(BlockRegistry.FOUNTAIN.get(), RenderType.translucent())"),
                "the water is partially transparent on every pixel, so it needs the translucent layer");
    }

    private static boolean usesWater(JsonObject element) {
        JsonObject faces = element.getAsJsonObject("faces");
        for (String side : faces.keySet()) {
            if ("#1".equals(faces.getAsJsonObject(side).get("texture").getAsString())) {
                return true;
            }
        }
        return false;
    }
}
