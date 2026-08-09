package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BannerReleaseContractTest {
    private static final Path CONTRACT = Path.of(System.getProperty("britannia.projectDir", "."),"content/banner_release_contract.json");
    private static final Path CATALOGUE = Path.of(System.getProperty("britannia.projectDir", "."),"content/banner_catalogue.yml");
    private static final Path DATA = Path.of(System.getProperty("britannia.projectDir", "."),"src/main/resources/data/britannia_mod");

    @Test
    void releasedDefinitionIdsAndIndexesMatchTheAuthoritativeCatalogue() throws Exception {
        JsonObject contract = json(CONTRACT);
        JsonObject catalogue = json(CATALOGUE);
        JsonArray released = contract.getAsJsonArray("banner_definitions");
        JsonArray banners = catalogue.getAsJsonArray("banners");
        assertEquals(35, released.size());
        assertEquals(35, banners.size());

        Set<String> ids = new HashSet<>();
        Set<Integer> indexes = new HashSet<>();
        for (int index = 0; index < banners.size(); index++) {
            JsonObject actual = banners.get(index).getAsJsonObject();
            JsonObject locked = released.get(index).getAsJsonObject();
            assertEquals(actual.get("index").getAsInt(), locked.get("index").getAsInt());
            assertEquals("britannia_mod:" + actual.get("id").getAsString(), locked.get("id").getAsString());
            assertTrue(ids.add(locked.get("id").getAsString()));
            assertTrue(indexes.add(locked.get("index").getAsInt()));
            assertEquals("complete", actual.get("content_status").getAsString());
            assertFalse(actual.get("base_texture").getAsString().contains("placeholder"));
            assertFalse(actual.get("dye_mask").getAsString().contains("placeholder"));
        }
        assertEquals(Set.copyOf(java.util.stream.IntStream.rangeClosed(1, 35).boxed().toList()), indexes);
    }

    @Test
    void releasedMaterialsPigmentsMountsAndPaletteValuesMatchRuntimeData() throws Exception {
        JsonObject contract = json(CONTRACT);
        Map<String, JsonObject> materials = objectsById(contract.getAsJsonArray("materials"));
        Map<String, JsonObject> palettes = objectsById(contract.getAsJsonArray("palettes"));
        assertEquals(4, materials.size());
        assertEquals(4, palettes.size());
        assertEquals(7, contract.getAsJsonArray("pigments").size());
        assertEquals(2, contract.getAsJsonArray("mounts").size());

        for (String material : materials.keySet()) {
            JsonObject runtime = json(DATA.resolve("fabric_materials/"
                    + material.substring(material.indexOf(':') + 1) + ".json"));
            JsonObject locked = materials.get(material);
            assertEquals(runtime.get("id"), locked.get("id"));
            assertEquals(runtime.get("natural_colour_id"), locked.get("natural_colour_id"));
            assertEquals(runtime.get("palette_id"), locked.get("palette_id"));
        }
        for (String palette : palettes.keySet()) {
            JsonObject runtime = json(DATA.resolve("material_palettes/"
                    + palette.substring(palette.indexOf(':') + 1) + ".json"));
            JsonObject locked = palettes.get(palette);
            assertEquals(runtime.get("material_id"), locked.get("material_id"));
            assertEquals(colourValues(runtime.getAsJsonArray("entries")),
                    colourValues(locked.getAsJsonArray("colours")));
            assertEquals(runtime.getAsJsonObject("pigment_overrides"),
                    locked.getAsJsonObject("pigment_overrides"));
        }
    }

    @Test
    void activeReleaseHasNoProvisionalIdsRecipesOrPatternContent() throws Exception {
        String catalogue = Files.readString(CATALOGUE);
        for (String removed : java.util.List.of(
                "x_small_unnamed_01", "end_01", "end_02",
                "medium_wall_01", "medium_wall_02", "medium_wall_03", "medium_wall_04", "medium_wall_05",
                "large_01", "large_02", "large_03", "large_04", "large_05", "large_06")) {
            assertFalse(catalogue.matches("(?s).*\"id\"\\s*:\\s*\"" + removed + "\".*"), removed);
        }
        try (var paths = Files.walk(Path.of(System.getProperty("britannia.projectDir", "."),"src/main/resources"))) {
            assertTrue(paths.filter(Files::isRegularFile)
                    .noneMatch(path -> path.toString().replace('\\', '/')
                            .matches("(?i).*(banner.*recipe|recipe.*banner|banner.*pattern|pattern.*banner).*")));
        }
        var production = DyeResolverFixtures.productionSnapshot();
        assertEquals(35, production.banners().activeCount());
        assertEquals(35, production.banners().activeDefinitions().stream()
                .filter(definition -> definition.contentStatus() == BannerContentStatus.COMPLETE).count());
        assertEquals(0, production.banners().disabledCount());
    }

    @Test
    void releaseSchemaContractRemainsAtVersionOneWithoutInventedRgbSnapshots() throws Exception {
        JsonObject schemas = json(CONTRACT).getAsJsonObject("schemas");
        for (String versioned : java.util.List.of(
                "banner_definition", "banner_instance_state", "dye_tub_state",
                "placed_structure", "client_asset_index")) {
            assertEquals(1, schemas.get(versioned).getAsInt(), versioned);
        }
        assertFalse(Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/java/com/seggellion/britannia_mod/banner/state/BannerInstanceState.java"))
                .matches("(?is).*(stored_rgb|resolved_rgb|display_srgb).*"));
    }

    private static JsonObject json(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), path.toString());
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static Map<String, JsonObject> objectsById(JsonArray values) {
        LinkedHashMap<String, JsonObject> result = new LinkedHashMap<>();
        values.forEach(value -> {
            JsonObject object = value.getAsJsonObject();
            assertNotNull(object.get("id"));
            assertEquals(null, result.put(object.get("id").getAsString(), object));
        });
        return result;
    }

    private static Map<String, String> colourValues(JsonArray entries) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        entries.forEach(value -> {
            JsonObject entry = value.getAsJsonObject();
            result.put(entry.get("id").getAsString(), entry.get("display_srgb").getAsString());
        });
        return result;
    }
}
