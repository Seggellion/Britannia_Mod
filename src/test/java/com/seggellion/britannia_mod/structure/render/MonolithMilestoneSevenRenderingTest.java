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
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class MonolithMilestoneSevenRenderingTest {
    private static final Path ASSETS = Path.of(System.getProperty("britannia.projectDir", "."), "src/main/resources/assets/britannia_mod");
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
        assertEquals(16, description.get("texture_height").getAsInt());
        JsonObject bone = geometry.getAsJsonArray("bones").get(0).getAsJsonObject();
        assertEquals("monolith", bone.get("name").getAsString());
        assertEquals("[0,-16,0]", compact(bone.getAsJsonArray("pivot")));
        JsonArray cubes = bone.getAsJsonArray("cubes");
        assertEquals(6, cubes.size());
        assertEquals(List.of(-8.0, -16.0, -8.0, 40.0, 32.0, 24.0), extents(cubes));
        assertNotEquals(Files.readString(EXISTING_MODEL), Files.readString(ALTERNATE_MODEL));
        String source = Files.readString(ALTERNATE_MODEL).toLowerCase();
        assertFalse(source.contains("shrine"));
        assertFalse(source.contains("virtue"));
        assertFalse(source.contains("ankh"));
    }

    @Test
    void alternateTextureIsOpaqueDistinctTwoMaterialAtlasWithPinnedHash() throws Exception {
        BufferedImage alternate = ImageIO.read(ALTERNATE_TEXTURE.toFile());
        BufferedImage existing = ImageIO.read(EXISTING_TEXTURE.toFile());
        assertEquals(256, alternate.getWidth());
        assertEquals(128, alternate.getHeight());
        for (int y = 0; y < alternate.getHeight(); y++) {
            for (int x = 0; x < alternate.getWidth(); x++) {
                assertEquals(255, (alternate.getRGB(x, y) >>> 24) & 0xFF);
            }
        }
        assertEquals("37206E78E694552627786A5EC750124C15792C1346023D52755392FCFEB65CEF",
                sha256(ALTERNATE_TEXTURE));
        assertEquals("359C2D29B0B16EDD58EB2765812F6E80140D5F215D634DE98D1110A96991572D",
                sha256(ALTERNATE_MODEL));
        assertEquals("11988200CE334883AADC39B1BE48AB337B21D62690222538F5A1946D48B5766B",
                sha256(EXISTING_TEXTURE));
        assertEquals("C2F22F3B6D35AB4D29D8E559EC55507877925E2981C26E3EC163B7D905596192",
                sha256(EXISTING_MODEL));
        assertNotEquals(sha256(EXISTING_TEXTURE), sha256(ALTERNATE_TEXTURE));
        // The first atlas half is intentionally shared sarsen_stone_1; the accent halves differ.
        assertNotEquals(existing.getRGB(192, 64), alternate.getRGB(192, 64));
    }

    @Test
    void crystallineGeometryHasPinnedExtentsPivotFrontDirectionAndDistinctSilhouette() throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(CRYSTALLINE_MODEL)).getAsJsonObject();
        JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        assertEquals("geometry.britannia_mod.monolith_diagnostic_crystalline",
                description.get("identifier").getAsString());
        assertEquals(32, description.get("texture_width").getAsInt());
        assertEquals(16, description.get("texture_height").getAsInt());
        JsonObject bone = geometry.getAsJsonArray("bones").get(0).getAsJsonObject();
        assertEquals("monolith", bone.get("name").getAsString());
        assertEquals("[0,-16,0]", compact(bone.getAsJsonArray("pivot")));
        JsonArray cubes = bone.getAsJsonArray("cubes");
        assertEquals(7, cubes.size());
        assertEquals(List.of(-8.0, -16.0, -8.0, 40.0, 32.0, 24.0), extents(cubes));
        assertNotEquals(Files.readString(EXISTING_MODEL), Files.readString(CRYSTALLINE_MODEL));
        assertNotEquals(Files.readString(ALTERNATE_MODEL), Files.readString(CRYSTALLINE_MODEL));
        String source = Files.readString(CRYSTALLINE_MODEL).toLowerCase();
        assertFalse(source.contains("shrine"));
        assertFalse(source.contains("virtue"));
        assertFalse(source.contains("ankh"));
    }

    @Test
    void crystallineTextureIsOpaqueDistinctTwoMaterialAtlasWithPinnedHash() throws Exception {
        BufferedImage crystalline = ImageIO.read(CRYSTALLINE_TEXTURE.toFile());
        assertEquals(256, crystalline.getWidth());
        assertEquals(128, crystalline.getHeight());
        for (int y = 0; y < crystalline.getHeight(); y++) {
            for (int x = 0; x < crystalline.getWidth(); x++) {
                assertEquals(255, (crystalline.getRGB(x, y) >>> 24) & 0xFF);
            }
        }
        assertEquals("77FEDD7BFF29F867B47B63B337BAD7443CF1595FE972A0D7B0A1B68DD1E9D724",
                sha256(CRYSTALLINE_TEXTURE));
        assertEquals("6921FBF2F68B341517730524C407F101F39F4758ACEC95BA4865208361C9338A",
                sha256(CRYSTALLINE_MODEL));
        assertNotEquals(sha256(EXISTING_TEXTURE), sha256(CRYSTALLINE_TEXTURE));
        assertNotEquals(sha256(ALTERNATE_TEXTURE), sha256(CRYSTALLINE_TEXTURE));
    }

    @Test
    void everyImportedModelEnvelopeExactlyMatchesCollisionForEveryFacing() throws Exception {
        var footprint = ShrineMonolithDefinitions.catalogue()
                .family(ShrineMonolithDefinitions.MONOLITH).orElseThrow().footprint();
        for (Path model : List.of(EXISTING_MODEL, ALTERNATE_MODEL, CRYSTALLINE_MODEL)) {
            AABB modelBounds = modelBounds(model);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                AABB occupied = ShrineRenderTransform.occupiedMonolithFootprintEnvelope(
                        BlockPos.ZERO, facing);
                AABB rendered = ShrineRenderTransform.renderedStructureEnvelope(
                        BlockPos.ZERO, facing, modelBounds, ShrineMonolithDefinitions.MONOLITH);
                assertAabbEquals(occupied, rendered);

                var state = new PlacedStructureState(ShrineMonolithDefinitions.MONOLITH,
                        new VariantId("diagnostic_missing_content"), facing, footprint);
                var bounds = ShrineRenderTransform.worldBounds(BlockPos.ZERO, Optional.of(state));
                assertTrue(bounds.minX <= rendered.minX && bounds.minY <= rendered.minY
                        && bounds.minZ <= rendered.minZ && bounds.maxX >= rendered.maxX
                        && bounds.maxY >= rendered.maxY && bounds.maxZ >= rendered.maxZ);
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

    private static AABB modelBounds(Path model) throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(model)).getAsJsonObject();
        JsonArray cubes = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones").get(0).getAsJsonObject().getAsJsonArray("cubes");
        List<Double> bounds = extents(cubes);
        return new AABB(bounds.get(0), bounds.get(1), bounds.get(2),
                bounds.get(3), bounds.get(4), bounds.get(5));
    }

    private static void assertAabbEquals(AABB expected, AABB actual) {
        assertEquals(expected.minX, actual.minX, 1.0E-8);
        assertEquals(expected.minY, actual.minY, 1.0E-8);
        assertEquals(expected.minZ, actual.minZ, 1.0E-8);
        assertEquals(expected.maxX, actual.maxX, 1.0E-8);
        assertEquals(expected.maxY, actual.maxY, 1.0E-8);
        assertEquals(expected.maxZ, actual.maxZ, 1.0E-8);
    }

    private static String compact(JsonArray array) {
        return array.toString().replace(" ", "");
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
