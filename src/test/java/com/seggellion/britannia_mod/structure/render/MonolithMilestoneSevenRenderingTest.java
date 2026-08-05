package com.seggellion.britannia_mod.structure.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureDefinitionValidator;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.Dimensions;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.VoxelOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ContentStatus;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import com.seggellion.britannia_mod.structure.multiblock.ShrineRenderTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MonolithMilestoneSevenRenderingTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/britannia_mod");
    private static final Path EXISTING_MODEL = ASSETS.resolve("geo/monolith_diagnostic.geo.json");
    private static final Path ALTERNATE_MODEL = ASSETS.resolve("geo/monolith_diagnostic_alternate.geo.json");
    private static final Path CRYSTALLINE_MODEL = ASSETS.resolve("geo/monolith_diagnostic_crystalline.geo.json");
    private static final Path EXISTING_TEXTURE = ASSETS.resolve(
            "textures/block/monolith/diagnostic_stone.png");
    private static final Path ALTERNATE_TEXTURE = ASSETS.resolve(
            "textures/block/monolith/diagnostic_alternate_stone.png");
    private static final Path CRYSTALLINE_TEXTURE = ASSETS.resolve(
            "textures/block/monolith/diagnostic_crystalline_stone.png");

    @Test
    void catalogueContainsExactlyThreeOrderedEnabledProvisionalCompatibleVariants() throws Exception {
        var family = ShrineMonolithDefinitions.catalogue()
                .family(ShrineMonolithDefinitions.MONOLITH).orElseThrow();
        assertTrue(StructureDefinitionValidator.validate(family).valid());
        assertEquals(List.of("diagnostic_missing_content", "diagnostic_alternate", "diagnostic_crystalline"),
                family.variants().stream().map(variant -> variant.id().value()).toList());
        assertEquals(List.of(0, 1, 2), family.variants().stream().map(variant -> variant.cyclePosition()).toList());
        assertEquals(3, family.variants().stream().filter(variant -> variant.enabled()).count());
        assertTrue(family.variants().stream().allMatch(
                variant -> variant.contentStatus() == ContentStatus.PROVISIONAL));
        assertEquals(3, family.variants().stream().map(variant -> variant.id()).distinct().count());

        for (var variant : family.variants()) {
            assertEquals(new Dimensions(3, 3, 2), variant.dimensions());
            assertEquals(family.footprint(), variant.footprint());
            assertEquals(18, variant.footprint().size());
            assertEquals(new VoxelOffset(0, 16, 0), variant.renderOffsetVoxels());
            assertEquals(family.placementMode(), variant.placementMode());
            assertEquals(family.collisionProfile(), variant.collisionProfile());
            assertEquals(family.renderOrigin(), variant.renderOrigin());
            assertTrue(StructureDefinitionValidator.compatible(family, variant));
            assertTrue(StructureDefinitionValidator.usableClientResource(variant.model()));
            assertTrue(StructureDefinitionValidator.usableClientResource(variant.texture()));
            assertEquals("britannia_mod", variant.model().location().orElseThrow().namespace());
            assertEquals("britannia_mod", variant.texture().location().orElseThrow().namespace());
            assertTrue(Files.isRegularFile(ASSETS.resolve(variant.model().location().orElseThrow().path())));
            assertTrue(Files.isRegularFile(ASSETS.resolve(variant.texture().location().orElseThrow().path())));
        }

        JsonObject lang = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/en_us.json")))
                .getAsJsonObject();
        assertTrue(lang.has("structure.britannia_mod.monolith.diagnostic_missing_content"));
        assertEquals("Diagnostic Monolith Alternate",
                lang.get("structure.britannia_mod.monolith.diagnostic_alternate").getAsString());
        assertEquals("Diagnostic Monolith Crystalline",
                lang.get("structure.britannia_mod.monolith.diagnostic_crystalline").getAsString());
        assertEquals("Selected monolith: %s",
                lang.get("message.britannia_mod.monolith.decorator.selected").getAsString());
    }

    @Test
    void exactVariantResourceMappingIsDistinctAndRendererSelectionIsCatalogueOwned() {
        var existing = ShrineRenderSelection.resolve(
                ShrineMonolithDefinitions.MONOLITH, new VariantId("diagnostic_missing_content"));
        var alternate = ShrineRenderSelection.resolve(
                ShrineMonolithDefinitions.MONOLITH, new VariantId("diagnostic_alternate"));
        var crystalline = ShrineRenderSelection.resolve(
                ShrineMonolithDefinitions.MONOLITH, new VariantId("diagnostic_crystalline"));
        assertEquals(ShrineRenderSelection.Status.READY, existing.status());
        assertEquals(ShrineRenderSelection.Status.READY, alternate.status());
        assertEquals(ShrineRenderSelection.Status.READY, crystalline.status());
        assertEquals("geo/monolith_diagnostic.geo.json",
                existing.geometry().orElseThrow().location().orElseThrow().path());
        assertEquals("textures/block/monolith/diagnostic_stone.png",
                existing.texture().orElseThrow().location().orElseThrow().path());
        assertEquals("geo/monolith_diagnostic_alternate.geo.json",
                alternate.geometry().orElseThrow().location().orElseThrow().path());
        assertEquals("textures/block/monolith/diagnostic_alternate_stone.png",
                alternate.texture().orElseThrow().location().orElseThrow().path());
        assertEquals("geo/monolith_diagnostic_crystalline.geo.json",
                crystalline.geometry().orElseThrow().location().orElseThrow().path());
        assertEquals("textures/block/monolith/diagnostic_crystalline_stone.png",
                crystalline.texture().orElseThrow().location().orElseThrow().path());
        assertNotEquals(existing.geometry(), alternate.geometry());
        assertNotEquals(existing.texture(), alternate.texture());
        assertNotEquals(existing.geometry(), crystalline.geometry());
        assertNotEquals(existing.texture(), crystalline.texture());
        assertNotEquals(alternate.geometry(), crystalline.geometry());
        assertNotEquals(alternate.texture(), crystalline.texture());

        ShrineRenderSelection missing = ShrineRenderSelection.resolve(
                ShrineMonolithDefinitions.MONOLITH, new VariantId("missing_saved_variant"));
        assertEquals(ShrineRenderSelection.Status.UNKNOWN_VARIANT, missing.status());
        assertEquals("missing_saved_variant", missing.variantId().value());
        assertTrue(missing.texture().isEmpty());
        assertFalse(missing.geometry().equals(existing.geometry()));
        assertFalse(missing.geometry().equals(alternate.geometry()));
        assertFalse(missing.geometry().equals(crystalline.geometry()));
    }

    @Test
    void alternateGeometryHasPinnedExtentsPivotFrontDirectionAndDistinctSilhouette() throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(ALTERNATE_MODEL)).getAsJsonObject();
        JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        assertEquals("geometry.britannia_mod.monolith_diagnostic_alternate",
                description.get("identifier").getAsString());
        assertEquals(32, description.get("texture_width").getAsInt());
        assertEquals(32, description.get("texture_height").getAsInt());
        JsonObject bone = geometry.getAsJsonArray("bones").get(0).getAsJsonObject();
        assertEquals("[0,-16,0]", compact(bone.getAsJsonArray("pivot")));
        JsonArray cubes = bone.getAsJsonArray("cubes");
        assertEquals(5, cubes.size());
        assertEquals("[10,-6,-6]",
                compact(cubes.get(4).getAsJsonObject().getAsJsonArray("origin")));
        assertEquals("[12,28,4]",
                compact(cubes.get(4).getAsJsonObject().getAsJsonArray("size")));
        assertEquals(List.of(-8.0, -16.0, -8.0, 40.0, 32.0, 24.0), extents(cubes));
        assertNotEquals(Files.readString(EXISTING_MODEL), Files.readString(ALTERNATE_MODEL));
        String source = Files.readString(ALTERNATE_MODEL).toLowerCase();
        assertFalse(source.contains("shrine"));
        assertFalse(source.contains("virtue"));
        assertFalse(source.contains("ankh"));
    }

    @Test
    void alternateTextureIsOpaqueDistinctThirtyTwoPixelsWithPinnedHash() throws Exception {
        BufferedImage alternate = ImageIO.read(ALTERNATE_TEXTURE.toFile());
        BufferedImage existing = ImageIO.read(EXISTING_TEXTURE.toFile());
        assertEquals(32, alternate.getWidth());
        assertEquals(32, alternate.getHeight());
        for (int y = 0; y < alternate.getHeight(); y++) {
            for (int x = 0; x < alternate.getWidth(); x++) {
                assertEquals(255, (alternate.getRGB(x, y) >>> 24) & 0xFF);
            }
        }
        assertEquals("E320B72F2D0B9429D07DD4E62D264535760797AE6D1F9DD047C082B6736A2C4A",
                sha256(ALTERNATE_TEXTURE));
        assertEquals("B2FCBE6841C53313A754A9722E5633957A9AB0334FB96FF05FBCFC42BA7512DC",
                sha256(ALTERNATE_MODEL));
        assertEquals("FE07CE0672EE51D76F2833D1044264C7B65B2ADEB076873D1B07C953509944F4",
                sha256(EXISTING_TEXTURE));
        assertEquals("0D58B1ED73A8811E10B276DB7E55B29F7248DD047074C0FE786882DF0757E53C",
                sha256(EXISTING_MODEL));
        assertNotEquals(sha256(EXISTING_TEXTURE), sha256(ALTERNATE_TEXTURE));
        assertNotEquals(existing.getRGB(16, 16), alternate.getRGB(16, 16));
    }

    @Test
    void crystallineGeometryHasPinnedExtentsPivotFrontDirectionAndDistinctSilhouette() throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(CRYSTALLINE_MODEL)).getAsJsonObject();
        JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        assertEquals("geometry.britannia_mod.monolith_diagnostic_crystalline",
                description.get("identifier").getAsString());
        assertEquals(32, description.get("texture_width").getAsInt());
        assertEquals(32, description.get("texture_height").getAsInt());
        JsonObject bone = geometry.getAsJsonArray("bones").get(0).getAsJsonObject();
        assertEquals("[0,-16,0]", compact(bone.getAsJsonArray("pivot")));
        JsonArray cubes = bone.getAsJsonArray("cubes");
        assertEquals(7, cubes.size());
        assertEquals("[12,-4,-8]",
                compact(cubes.get(6).getAsJsonObject().getAsJsonArray("origin")));
        assertEquals("[8,28,6]",
                compact(cubes.get(6).getAsJsonObject().getAsJsonArray("size")));
        assertEquals(List.of(-8.0, -16.0, -8.0, 40.0, 32.0, 24.0), extents(cubes));
        assertNotEquals(Files.readString(EXISTING_MODEL), Files.readString(CRYSTALLINE_MODEL));
        assertNotEquals(Files.readString(ALTERNATE_MODEL), Files.readString(CRYSTALLINE_MODEL));
        String source = Files.readString(CRYSTALLINE_MODEL).toLowerCase();
        assertFalse(source.contains("shrine"));
        assertFalse(source.contains("virtue"));
        assertFalse(source.contains("ankh"));
    }

    @Test
    void crystallineTextureIsOpaqueDistinctThirtyTwoPixelsWithPinnedHash() throws Exception {
        BufferedImage crystalline = ImageIO.read(CRYSTALLINE_TEXTURE.toFile());
        assertEquals(32, crystalline.getWidth());
        assertEquals(32, crystalline.getHeight());
        for (int y = 0; y < crystalline.getHeight(); y++) {
            for (int x = 0; x < crystalline.getWidth(); x++) {
                assertEquals(255, (crystalline.getRGB(x, y) >>> 24) & 0xFF);
            }
        }
        assertEquals("E20F4D15EC0511AFDB132E13D047FB830309896538C2C33A1224EF8420A9BAC7",
                sha256(CRYSTALLINE_TEXTURE));
        assertEquals("A4EAD03C5C1ECA1FEF6F7FA6DE22C64E1ADAE964763A5EAF0715E7C72AA74955",
                sha256(CRYSTALLINE_MODEL));
        assertNotEquals(sha256(EXISTING_TEXTURE), sha256(CRYSTALLINE_TEXTURE));
        assertNotEquals(sha256(ALTERNATE_TEXTURE), sha256(CRYSTALLINE_TEXTURE));
    }

    @Test
    void finiteFamilyBoundsContainAllThreeSameExtentsModelsForEveryFacingAndOffsetRemainsOnce() {
        var footprint = ShrineMonolithDefinitions.catalogue()
                .family(ShrineMonolithDefinitions.MONOLITH).orElseThrow().footprint();
        for (String variant : List.of(
                "diagnostic_missing_content", "diagnostic_alternate", "diagnostic_crystalline")) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var state = new PlacedStructureState(ShrineMonolithDefinitions.MONOLITH,
                        new VariantId(variant), facing, footprint);
                var bounds = ShrineRenderTransform.worldBounds(BlockPos.ZERO, Optional.of(state));
                assertTrue(Double.isFinite(bounds.minX) && Double.isFinite(bounds.maxY));
                assertEquals(-2.0 - ShrineRenderTransform.TOLERANCE, bounds.minX);
                assertEquals(-2.0 - ShrineRenderTransform.TOLERANCE, bounds.minZ);
                assertEquals(3.0 + ShrineRenderTransform.TOLERANCE, bounds.maxX);
                assertEquals(3.0 + ShrineRenderTransform.TOLERANCE, bounds.maxY);
                assertEquals(3.0 + ShrineRenderTransform.TOLERANCE, bounds.maxZ);
            }
        }
        assertEquals(1.0, ShrineRenderTransform.renderOffsetBlocks(ShrineMonolithDefinitions.MONOLITH));
        assertEquals(0.0, ShrineRenderTransform.renderOffsetBlocks(ShrineMonolithDefinitions.SHRINE));
    }

    private static List<Double> extents(JsonArray cubes) {
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
        return List.of(min[0], min[1], min[2], max[0], max[1], max[2]);
    }

    private static String compact(JsonArray array) {
        return array.toString().replace(" ", "");
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
