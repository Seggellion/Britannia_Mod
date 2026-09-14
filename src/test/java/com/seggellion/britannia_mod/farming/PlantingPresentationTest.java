package com.seggellion.britannia_mod.farming;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.item.WateringCanItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlantingPresentationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void everyCanonicalSpeciesHasATranslatableNameAndExactConfirmation() throws Exception {
        var lang = JsonParser.parseString(Files.readString(Path.of(System.getProperty("britannia.projectDir", ".")).resolve("src/main/resources/assets/britannia_mod/lang/en_us.json")).replace("\uFEFF", "")).getAsJsonObject();
        assertEquals("You skillfully planted the %s seed.", lang.get("message.britannia_mod.seed_planted").getAsString());
        for (var crop : CropRegistry.all()) assertEquals(crop.displayName(), lang.get("crop.britannia_mod." + crop.id()).getAsString());
        for (var flower : FlowerRegistry.initial().definitions().values())
            assertEquals(lang.get("item.britannia_mod." + flower.id().getPath()).getAsString(), lang.get("crop.britannia_mod." + flower.id().getPath()).getAsString());
        for (var state : FarmingPlotStatus.Stage.values()) assertTrue(lang.has("hud.britannia_mod.plot." + state.name().toLowerCase(java.util.Locale.ROOT)));
    }

    @Test void fullPredicateClampsChargesPreservesComponentsAndDefaultsLegacyToFull() {
        var stack = new ItemStack(Items.BUCKET);
        var tag = new CompoundTag(); tag.putString("CustomOwner", "unchanged");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        assertEquals(12, WateringCanItem.getWaterCharges(stack));
        assertEquals(1f, WateringCanItem.fullModelState(stack));
        for (int charges : new int[]{Integer.MIN_VALUE, -1, 0, 1, 11, 12, 13, Integer.MAX_VALUE}) {
            WateringCanItem.setWaterCharges(stack, charges);
            int clamped = Math.max(0, Math.min(12, charges));
            assertEquals(clamped, WateringCanItem.getWaterCharges(stack));
            assertEquals(clamped == 12 ? 1f : 0f, WateringCanItem.fullModelState(stack));
            assertEquals("unchanged", stack.get(DataComponents.CUSTOM_DATA).copyTag().getString("CustomOwner"));
            assertEquals(WateringCanItem.fullModelState(stack), WateringCanItem.fullModelState(stack.copy()));
        }
        for (int malformed : new int[]{-99, 99}) {
            tag.putInt("WaterCharges", malformed); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            assertEquals(malformed < 0 ? 0 : 12, WateringCanItem.getWaterCharges(stack));
        }
    }

    @Test void fullCanOverrideResolvesToAReplaceableResourceWithoutRecursion() throws Exception {
        var assets = Path.of(System.getProperty("britannia.projectDir", "."))
                .resolve("src/main/resources/assets/britannia_mod");
        var base = JsonParser.parseString(Files.readString(assets.resolve("models/item/watering_can.json"))).getAsJsonObject();
        var overrides = base.getAsJsonArray("overrides");
        assertNotNull(overrides, "full-state property needs an item model override");
        var fullOverride = overrides.get(overrides.size() - 1).getAsJsonObject();
        assertEquals(1f, fullOverride.getAsJsonObject("predicate").get("britannia_mod:full").getAsFloat());
        assertEquals("britannia_mod:item/watering_can_full", fullOverride.get("model").getAsString());
        var full = JsonParser.parseString(Files.readString(assets.resolve("models/item/watering_can_full.json"))).getAsJsonObject();
        assertFalse(full.has("overrides"), "full model must not override itself");
        var emptyTextures = assertRenderableModel(assets, "britannia_mod:item/watering_can", false);
        var fullTextures = assertRenderableModel(assets, "britannia_mod:item/watering_can_full", true);
        assertTrue(fullTextures.containsAll(emptyTextures), "full can retains its metal body");
        assertTrue(fullTextures.stream().anyMatch(texture -> !emptyTextures.contains(texture)),
                "full can must render an additional fill texture, not just reuse empty geometry");
        var water = full.getAsJsonArray("elements").asList().stream()
                .map(element -> element.getAsJsonObject())
                .filter(element -> element.has("name") && "water_surface_full".equals(element.get("name").getAsString()))
                .findFirst().orElseThrow(() -> new AssertionError("full can needs visible water geometry"));
        var waterFace = water.getAsJsonObject("faces").getAsJsonObject("up");
        assertNotNull(waterFace, "the open fill cavity must have a visible water surface");
        var waterTexture = resolveTexture(waterFace.get("texture").getAsString(),
                textureBindings(full.getAsJsonObject("textures")));
        assertFalse(emptyTextures.contains(waterTexture), "water surface must use the full-only fill texture");
    }

    private static Set<String> assertRenderableModel(Path assets, String modelId, boolean forbidOverrides) throws Exception {
        var chain = new ArrayList<JsonObject>();
        var visitedModels = new HashSet<String>();
        var current = modelId;
        while (current.startsWith("britannia_mod:")) {
            assertTrue(visitedModels.add(current), "recursive model parent: " + current);
            var modelPath = assets.resolve("models/" + current.substring("britannia_mod:".length()) + ".json");
            assertTrue(Files.isRegularFile(modelPath), "missing model: " + current);
            var model = JsonParser.parseString(Files.readString(modelPath)).getAsJsonObject();
            if (forbidOverrides) assertFalse(model.has("overrides"), "full model ancestry must not inherit item overrides");
            chain.add(model);
            if (!model.has("parent")) break;
            current = model.get("parent").getAsString();
        }
        var textures = new HashMap<String, String>();
        JsonArray elements = null;
        for (int index = chain.size() - 1; index >= 0; index--) {
            var model = chain.get(index);
            if (model.has("textures")) textures.putAll(textureBindings(model.getAsJsonObject("textures")));
            if (model.has("elements")) elements = model.getAsJsonArray("elements");
        }
        assertNotNull(elements, "watering can must resolve renderable geometry");
        assertFalse(elements.isEmpty(), "watering can geometry must not be empty");
        var used = new HashSet<String>();
        for (var element : elements) {
            var faces = element.getAsJsonObject().getAsJsonObject("faces");
            assertNotNull(faces, "each can element needs textured faces");
            for (var face : faces.entrySet()) {
                var texture = resolveTexture(face.getValue().getAsJsonObject().get("texture").getAsString(), textures);
                assertTrue(texture.startsWith("britannia_mod:"), "can texture must resolve to a replaceable mod resource");
                var path = assets.resolve("textures/" + texture.substring("britannia_mod:".length()) + ".png");
                assertTrue(Files.isRegularFile(path), "missing texture: " + texture);
                if (used.add(texture)) assertNotNull(javax.imageio.ImageIO.read(path.toFile()),
                        "texture must be a readable image: " + texture);
            }
        }
        assertFalse(used.isEmpty(), "can geometry must have textured faces");
        return used;
    }

    private static Map<String, String> textureBindings(JsonObject textures) {
        var bindings = new HashMap<String, String>();
        textures.entrySet().forEach(entry -> bindings.put(entry.getKey(), entry.getValue().getAsString()));
        return bindings;
    }

    private static String resolveTexture(String texture, Map<String, String> bindings) {
        var visitedSlots = new HashSet<String>();
        while (texture.startsWith("#")) {
            var slot = texture.substring(1);
            assertTrue(visitedSlots.add(slot), "recursive texture alias: " + slot);
            texture = bindings.get(slot);
            assertNotNull(texture, "unresolved texture slot: " + slot);
        }
        return texture;
    }
}
