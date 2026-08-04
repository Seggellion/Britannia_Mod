package com.seggellion.britannia_mod.structure.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ContentStatus;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ResourceAvailability;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import com.seggellion.britannia_mod.structure.multiblock.ShrineRenderTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MonolithMilestoneSixRenderingTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/britannia_mod");
    private static final Path MODEL = ASSETS.resolve("geo/monolith_diagnostic.geo.json");
    private static final Path TEXTURE = ASSETS.resolve("textures/block/monolith/diagnostic_stone.png");

    @Test
    void existingEnabledProvisionalVariantStillResolvesItsUnchangedResources() throws Exception {
        var family = ShrineMonolithDefinitions.catalogue()
                .family(ShrineMonolithDefinitions.MONOLITH).orElseThrow();
        assertEquals(2, family.variants().size());
        var variant = family.variants().getFirst();
        assertEquals("diagnostic_missing_content", variant.id().value());
        assertEquals(ContentStatus.PROVISIONAL, variant.contentStatus());
        assertTrue(variant.enabled());
        assertTrue(variant.playerFacing());
        assertEquals(18, variant.footprint().size());
        assertEquals(ResourceAvailability.AVAILABLE, variant.model().availability());
        assertEquals(ResourceAvailability.AVAILABLE, variant.texture().availability());
        assertEquals("geo/monolith_diagnostic.geo.json",
                variant.model().location().orElseThrow().path());
        assertEquals("textures/block/monolith/diagnostic_stone.png",
                variant.texture().location().orElseThrow().path());
        assertTrue(Files.isRegularFile(MODEL));
        assertTrue(Files.isRegularFile(TEXTURE));
        assertTrue(Files.isRegularFile(ASSETS.resolve("animations/monolith.animation.json")));
        assertTrue(Files.isRegularFile(ASSETS.resolve("models/item/monolith.json")));

        ShrineRenderSelection selection = ShrineRenderSelection.resolve(
                ShrineMonolithDefinitions.MONOLITH, variant.id());
        assertEquals(ShrineRenderSelection.Status.READY, selection.status());
        assertEquals(variant.model(), selection.geometry().orElseThrow());
        assertEquals(variant.texture(), selection.texture().orElseThrow());
    }

    @Test
    void geometryHasExactAuthoredBoundsPivotForwardMarkerAndNoShrineIdentity() throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(MODEL)).getAsJsonObject();
        JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        assertEquals("geometry.britannia_mod.monolith_diagnostic",
                description.get("identifier").getAsString());
        assertEquals(32, description.get("texture_width").getAsInt());
        assertEquals(32, description.get("texture_height").getAsInt());
        JsonObject bone = geometry.getAsJsonArray("bones").get(0).getAsJsonObject();
        assertEquals("[0,-16,0]", compact(bone.getAsJsonArray("pivot")));
        JsonArray cubes = bone.getAsJsonArray("cubes");
        assertEquals(4, cubes.size());
        double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (var element : cubes) {
            JsonObject cube = element.getAsJsonObject();
            JsonArray origin = cube.getAsJsonArray("origin");
            JsonArray size = cube.getAsJsonArray("size");
            for (int axis = 0; axis < 3; axis++) {
                double start = origin.get(axis).getAsDouble();
                min[axis] = Math.min(min[axis], start);
                max[axis] = Math.max(max[axis], start + size.get(axis).getAsDouble());
            }
        }
        assertEquals(-8.0, min[0]);
        assertEquals(40.0, max[0]);
        assertEquals(-16.0, min[1]);
        assertEquals(32.0, max[1]);
        assertEquals(-8.0, min[2]);
        assertEquals(24.0, max[2]);
        JsonObject frontMarker = cubes.get(3).getAsJsonObject();
        assertEquals("[12,-4,-6]", compact(frontMarker.getAsJsonArray("origin")));
        assertEquals("[8,24,2]", compact(frontMarker.getAsJsonArray("size")));
        String source = Files.readString(MODEL).toLowerCase();
        assertFalse(source.contains("shrine"));
        assertFalse(source.contains("ankh"));
        assertFalse(source.contains("virtue"));
    }

    @Test
    void textureIsOneOpaqueThirtyTwoPixelDiagnosticAssetWithPinnedHash() throws Exception {
        BufferedImage image = ImageIO.read(TEXTURE.toFile());
        assertEquals(32, image.getWidth());
        assertEquals(32, image.getHeight());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                assertEquals(255, (image.getRGB(x, y) >>> 24) & 0xFF);
            }
        }
        assertEquals("FE07CE0672EE51D76F2833D1044264C7B65B2ADEB076873D1B07C953509944F4",
                sha256(TEXTURE));
        try (var paths = Files.list(TEXTURE.getParent())) {
            assertEquals(2, paths.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void positiveOffsetIsOneBlockRenderOnlyAndBoundsCoverAllFacingsAndLayers() throws Exception {
        assertEquals(1.0, ShrineRenderTransform.renderOffsetBlocks(ShrineMonolithDefinitions.MONOLITH));
        assertEquals(0.0, ShrineRenderTransform.renderOffsetBlocks(ShrineMonolithDefinitions.SHRINE));
        assertEquals(16, ShrineMonolithDefinitions.MONOLITH_RENDER_OFFSET.y());
        var state = new PlacedStructureState(
                ShrineMonolithDefinitions.MONOLITH, new VariantId("diagnostic_missing_content"),
                Direction.NORTH, ShrineMonolithDefinitions.catalogue()
                        .family(ShrineMonolithDefinitions.MONOLITH).orElseThrow().footprint());
        var bounds = ShrineRenderTransform.worldBounds(BlockPos.ZERO, Optional.of(state));
        assertTrue(Double.isFinite(bounds.minX) && Double.isFinite(bounds.maxY));
        assertEquals(-2.0 - ShrineRenderTransform.TOLERANCE, bounds.minX);
        assertEquals(-2.0 - ShrineRenderTransform.TOLERANCE, bounds.minZ);
        assertEquals(3.0 + ShrineRenderTransform.TOLERANCE, bounds.maxX);
        assertEquals(3.0 + ShrineRenderTransform.TOLERANCE, bounds.maxY);
        assertEquals(3.0 + ShrineRenderTransform.TOLERANCE, bounds.maxZ);

        String renderer = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/client/renderer/shrine/ShrineRenderer.java"));
        assertEquals(1, occurrences(renderer, "poseStack.translate(0.0, ShrineRenderTransform.renderOffsetBlocks(anchor), 0.0)"));
        assertTrue(renderer.contains("if (!isReRender)"));
        String planner = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/structure/placement/ShrinePlacementPlanner.java"));
        assertFalse(planner.contains("MONOLITH_RENDER_OFFSET"));
        assertFalse(planner.contains("renderOffsetBlocks"));
    }

    @Test
    void sharedRegistrationHasOneRendererNoPartRendererAndNoSecondStructureBlocks() throws Exception {
        String registry = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/registry/LargeStructureRegistry.java"));
        assertEquals(1, occurrences(registry, "BLOCKS.register(\"large_structure_anchor\""));
        assertEquals(1, occurrences(registry, "BLOCKS.register(\"large_structure_part\""));
        assertEquals(1, occurrences(registry, "BLOCK_ENTITIES.register(\"large_structure\""));
        assertEquals(1, occurrences(registry, "ITEMS.register(\n            \"monolith\""));
        assertFalse(registry.contains("monolith_anchor"));
        assertFalse(registry.contains("monolith_part"));
        assertFalse(registry.contains("MonolithBlockEntity"));
        assertFalse(registry.contains("BlockItem"));

        String setup = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/ClientModSetup.java"));
        assertEquals(1, occurrences(setup,
                "registerBlockEntityRenderer(LargeStructureRegistry.LARGE_STRUCTURE.get(), ShrineRenderer::new)"));
        assertFalse(setup.contains("LARGE_STRUCTURE_PART.get(), ShrineRenderer"));
    }

    private static String compact(JsonArray array) {
        return array.toString().replace(" ", "");
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static int occurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }
}
