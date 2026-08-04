package com.seggellion.britannia_mod.structure.hardening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Family;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Variant;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ClientResource;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ResourceId;
import com.seggellion.britannia_mod.structure.render.ShrineRimMaterialSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MilestoneEightContentReportTest {
    private static final Path REPORT = Path.of("docs/shrines-monoliths/CONTENT_REPORT.json");
    private static final Path ASSETS = Path.of("src/main/resources/assets/britannia_mod");
    private static final String GENERATED_FROM = "0aa523ec86e483e230fc1c4d04e145d394ebf990";

    @Test
    void committedReportExactlyMatchesDeterministicCatalogueAndResourceReconstruction() throws Exception {
        JsonElement committed = JsonParser.parseString(Files.readString(REPORT));
        JsonObject expected = expectedReport();
        assertEquals(expected, committed);
        assertEquals(1, expected.get("report_schema_version").getAsInt());
        assertEquals(GENERATED_FROM, expected.get("generated_from_commit").getAsString());
    }

    @Test
    void catalogueAndReportContainExactlyTwoFamiliesNineShrinesAndTwoMonoliths() throws Exception {
        var catalogue = ShrineMonolithDefinitions.catalogue();
        assertEquals(2, catalogue.families().size());
        assertEquals(9, catalogue.family(ShrineMonolithDefinitions.SHRINE).orElseThrow().variants().size());
        assertEquals(2, catalogue.family(ShrineMonolithDefinitions.MONOLITH).orElseThrow().variants().size());
        assertEquals("honesty,compassion,valor,justice,sacrifice,honor,spirituality,humility,chaos",
                catalogue.family(ShrineMonolithDefinitions.SHRINE).orElseThrow().variants().stream()
                        .map(variant -> variant.id().value()).collect(java.util.stream.Collectors.joining(",")));
        assertEquals("diagnostic_missing_content,diagnostic_alternate",
                catalogue.family(ShrineMonolithDefinitions.MONOLITH).orElseThrow().variants().stream()
                        .map(variant -> variant.id().value()).collect(java.util.stream.Collectors.joining(",")));
    }

    @Test
    void idsCyclePositionsResourcesLocalizationAndMappingsAreUniqueAndComplete() throws Exception {
        JsonObject language = JsonParser.parseString(Files.readString(
                ASSETS.resolve("lang/en_us.json"))).getAsJsonObject();
        for (Family family : ShrineMonolithDefinitions.catalogue().families()) {
            Set<String> ids = new HashSet<>();
            Set<Integer> positions = new HashSet<>();
            Set<String> mappings = new HashSet<>();
            assertTrue(family.defaultVariant().isPresent());
            Variant defaultVariant = family.variants().stream()
                    .filter(variant -> variant.id().equals(family.defaultVariant().orElseThrow()))
                    .findFirst().orElseThrow();
            assertTrue(defaultVariant.enabled());
            assertEquals(family.dimensions().cellCount(), family.footprint().size());
            assertEquals(1, family.footprint().stream()
                    .filter(com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset.ANCHOR::equals)
                    .count());
            for (Variant variant : family.variants()) {
                assertTrue(ids.add(variant.id().value()));
                assertTrue(positions.add(variant.cyclePosition()));
                assertEquals(family.footprint(), variant.footprint());
                assertTrue(variant.enabled());
                assertTrue(language.has(variant.displayName().translationKey().orElseThrow()));
                assertResource(variant.model());
                assertResource(variant.texture());
                assertTrue(mappings.add(resourcePath(variant.model()) + "|" + resourcePath(variant.texture())));
            }
            if (family.id().equals(ShrineMonolithDefinitions.SHRINE)) {
                assertEquals(1, family.variants().stream().map(variant -> resourcePath(variant.model())).distinct().count());
                assertEquals(9, family.variants().stream().map(variant -> resourcePath(variant.texture())).distinct().count());
            } else {
                assertEquals(2, family.variants().stream().map(variant -> resourcePath(variant.model())).distinct().count());
                family.variants().forEach(variant -> assertEquals(16, variant.renderOffsetVoxels().y()));
            }
        }
    }

    static JsonObject expectedReport() throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("report_schema_version", 1);
        root.addProperty("generated_from_commit", GENERATED_FROM);
        JsonArray families = new JsonArray();
        for (Family family : ShrineMonolithDefinitions.catalogue().families()) {
            JsonObject familyJson = new JsonObject();
            familyJson.addProperty("family_id", family.id().value());
            familyJson.addProperty("family_status", family.contentStatus().name());
            familyJson.add("dimensions", dimensions(family.dimensions().width(),
                    family.dimensions().height(), family.dimensions().depth()));
            familyJson.add("ordered_footprint", footprint(family));
            familyJson.addProperty("occupied_cell_count", family.footprint().size());
            familyJson.addProperty("placement_mode", family.placementMode().name());
            familyJson.addProperty("collision_profile", family.collisionProfile().name());
            familyJson.addProperty("render_origin", family.renderOrigin().name());
            familyJson.add("render_offset_voxels", offset(family.renderOffsetVoxels().x(),
                    family.renderOffsetVoxels().y(), family.renderOffsetVoxels().z()));
            familyJson.addProperty("geometry_mode", family.geometryMode().name());
            JsonArray variants = new JsonArray();
            for (Variant variant : family.variants()) variants.add(variant(family, variant));
            familyJson.add("variants", variants);
            families.add(familyJson);
        }
        root.add("families", families);
        return root;
    }

    private static JsonObject variant(Family family, Variant variant) throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("variant_id", variant.id().value());
        json.addProperty("variant_status", variant.contentStatus().name());
        json.addProperty("asset_status", "OWNER_APPROVED_PROVISIONAL");
        json.addProperty("display_localization_key", variant.displayName().translationKey().orElseThrow());
        json.addProperty("cycle_position", variant.cyclePosition());
        json.addProperty("enabled", variant.enabled());
        json.addProperty("default", family.defaultVariant().orElseThrow().equals(variant.id()));
        json.add("dimensions", dimensions(variant.dimensions().width(),
                variant.dimensions().height(), variant.dimensions().depth()));
        json.add("ordered_footprint", footprint(family));
        json.addProperty("occupied_cell_count", variant.footprint().size());
        json.addProperty("placement_mode", variant.placementMode().name());
        json.addProperty("collision_profile", variant.collisionProfile().name());
        json.addProperty("render_origin", variant.renderOrigin().name());
        json.add("render_offset_voxels", offset(variant.renderOffsetVoxels().x(),
                variant.renderOffsetVoxels().y(), variant.renderOffsetVoxels().z()));
        String animation = family.id().equals(ShrineMonolithDefinitions.SHRINE)
                ? "animations/shrine.animation.json" : "animations/monolith.animation.json";
        json.addProperty("geometry_resource", resourcePath(variant.model()));
        json.addProperty("texture_resource", resourcePath(variant.texture()));
        ResourceId rimTexture = null;
        if (family.id().equals(ShrineMonolithDefinitions.SHRINE)) {
            rimTexture = ShrineRimMaterialSelection.textureFor(family.id(), variant.id());
            json.addProperty("rim_texture_resource", resourcePath(rimTexture));
        }
        json.addProperty("animation_resource", animation);
        json.addProperty("resource_availability", "AVAILABLE");
        JsonObject hashes = new JsonObject();
        hashes.addProperty("geometry", sha256(ASSETS.resolve(resourcePath(variant.model()))));
        hashes.addProperty("texture", sha256(ASSETS.resolve(resourcePath(variant.texture()))));
        if (rimTexture != null) {
            hashes.addProperty("rim_texture", sha256(ASSETS.resolve(resourcePath(rimTexture))));
        }
        hashes.addProperty("animation", sha256(ASSETS.resolve(animation)));
        json.add("resource_sha256", hashes);
        json.addProperty("validation_status", "VALID");
        json.add("validation_diagnostics", new JsonArray());
        return json;
    }

    private static JsonObject dimensions(int width, int height, int depth) {
        JsonObject json = new JsonObject();
        json.addProperty("width", width);
        json.addProperty("height", height);
        json.addProperty("depth", depth);
        return json;
    }

    private static JsonObject offset(int x, int y, int z) {
        JsonObject json = new JsonObject();
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("z", z);
        return json;
    }

    private static JsonArray footprint(Family family) {
        JsonArray array = new JsonArray();
        family.footprint().forEach(offset -> array.add(offset(offset.x(), offset.y(), offset.z())));
        return array;
    }

    private static void assertResource(ClientResource resource) throws Exception {
        assertEquals("AVAILABLE", resource.availability().name());
        Path path = ASSETS.resolve(resourcePath(resource));
        assertTrue(Files.isRegularFile(path));
        assertFalse(sha256(path).isBlank());
    }

    private static String resourcePath(ClientResource resource) {
        var id = resource.location().orElseThrow();
        assertEquals("britannia_mod", id.namespace());
        return id.path();
    }

    private static String resourcePath(ResourceId resource) {
        assertEquals("britannia_mod", resource.namespace());
        return resource.path();
    }

    private static String sha256(Path path) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return java.util.HexFormat.of().withUpperCase().formatHex(hash);
    }
}
