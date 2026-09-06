package com.seggellion.britannia_mod.skill.crafting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.client.renderer.block.model.BlockModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Patch18WeaponAssetsTest {
    private static final List<String> ITEMS = List.of("dagger", "viking_sword", "katana", "rapier",
            "halberd", "decorative_shield");

    private JsonObject resource(String path) throws Exception {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void modelsParseInTheActualMinecraftVersionAndAllReferencesResolve() throws Exception {
        for (String id : ITEMS) {
            JsonObject model = resource("/assets/britannia_mod/models/item/" + id + ".json");
            assertDoesNotThrow(() -> BlockModel.fromString(model.toString()), id);
            assertFalse(model.getAsJsonArray("elements").isEmpty(), id);
            assertEquals("minecraft:block/block", model.get("parent").getAsString(), id);
            for (var texture : model.getAsJsonObject("textures").entrySet()) {
                String path = texture.getValue().getAsString();
                assertEquals(path.toLowerCase(java.util.Locale.ROOT), path);
                assertNotNull(getClass().getResource("/assets/" + path.replace(":", "/textures/") + ".png"), path);
            }
            for (String context : List.of("gui", "ground", "fixed", "firstperson_righthand",
                    "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand")) {
                assertTrue(model.getAsJsonObject("display").has(context), id + ": " + context);
            }
            for (var element : model.getAsJsonArray("elements")) {
                for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    JsonObject value = face.getValue().getAsJsonObject();
                    assertEquals("#0", value.get("texture").getAsString(), id);
                    for (var coordinate : value.getAsJsonArray("uv")) {
                        assertTrue(coordinate.getAsDouble() >= 0 && coordinate.getAsDouble() <= 16, id + " UV");
                    }
                }
            }
            assertNotNull(resource("/assets/britannia_mod/lang/en_us.json").get("item.britannia_mod." + id));
        }
        JsonObject blocking = resource("/assets/britannia_mod/models/item/decorative_shield_blocking.json");
        assertDoesNotThrow(() -> BlockModel.fromString(blocking.toString()));
        assertEquals("britannia_mod:item/decorative_shield", blocking.get("parent").getAsString());
    }

    @Test
    void importedRecipesKeepEstablishedCostsAndExplicitlyMarkAnalogueDecisions() {
        int[] ingots = {3, 14, 8, 8, 20, 14};
        float[] skill = {0, 24.3f, 44.1f, 36.7f, 39.1f, 0};
        for (int i = 0; i < ITEMS.size(); i++) {
            CraftableDef def = CraftableRegistry.get(ITEMS.get(i));
            assertEquals("britannia_mod:" + ITEMS.get(i), def.resultItem().toString());
            assertEquals(ingots[i], def.ingredients().getFirst().amount());
            assertEquals(skill[i], def.minimumBlacksmithy());
            assertEquals(1, def.outputCount());
            assertTrue(def.recyclable());
            assertFalse(def.batchCrafting());
            assertFalse(def.requiresLearnedRecipe());
            assertEquals(ITEMS.get(i).equals("rapier") || ITEMS.get(i).equals("decorative_shield"), def.provisional());
        }
    }
}
