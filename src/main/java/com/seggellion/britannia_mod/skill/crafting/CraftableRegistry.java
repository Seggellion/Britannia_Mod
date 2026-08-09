package com.seggellion.britannia_mod.skill.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Canonical, data-driven Blacksmithing catalogue loaded before item registration. */
public final class CraftableRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE = "/data/britannia_mod/blacksmithing/craftables.json";
    private static final Map<String, CraftableDef> CRAFTABLES = new LinkedHashMap<>();
    private static final Map<String, String> LEGACY_ALIASES = Map.ofEntries(
            Map.entry("heavy_cannonball", "cannon_ball"),
            Map.entry("light_cannonball", "cannon_ball"),
            Map.entry("heavy_grapeshot", "grapeshot"),
            Map.entry("light_grapeshot", "grapeshot"),
            Map.entry("heavy_ship_cannon", "carronade"),
            Map.entry("light_ship_cannon", "culverin")
    );

    private CraftableRegistry() {}

    public static synchronized void init() {
        if (!CRAFTABLES.isEmpty()) return;
        try (InputStream stream = CraftableRegistry.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing catalogue resource " + RESOURCE);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalStateException("Unsupported catalogue schema");
            for (JsonElement element : root.getAsJsonArray("recipes")) register(parse(element.getAsJsonObject()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load Blacksmithing catalogue", exception);
        }
        validate();
        LOGGER.info("Loaded {} authoritative Blacksmithing recipes", CRAFTABLES.size());
    }

    private static CraftableDef parse(JsonObject json) {
        List<IngredientRequirement> ingredients = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("ingredients")) {
            JsonObject ingredient = element.getAsJsonObject();
            ingredients.add(new IngredientRequirement(ingredient.get("key").getAsString(), ingredient.get("count").getAsInt()));
        }
        List<SkillRequirement> skills = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("skills")) {
            JsonObject skill = element.getAsJsonObject();
            skills.add(new SkillRequirement(skill.get("key").getAsString(), skill.get("minimum").getAsFloat()));
        }
        return CraftableDef.catalogue(
                json.get("id").getAsString(), json.get("category").getAsString(), json.get("display_name").getAsString(),
                ResourceLocation.parse(json.get("output").getAsString()), ingredients, skills,
                json.get("output_count").getAsInt(), json.get("retains_color").getAsBoolean(),
                json.get("makers_mark").getAsBoolean(), nullableString(json, "learned_recipe"),
                nullableString(json, "race"), nullableString(json, "gender"),
                json.get("batch").getAsBoolean(), json.get("recyclable").getAsBoolean());
    }

    private static String nullableString(JsonObject json, String key) {
        return !json.has(key) || json.get(key).isJsonNull() ? null : json.get(key).getAsString();
    }

    private static void register(CraftableDef definition) {
        CraftableDef previous = CRAFTABLES.putIfAbsent(definition.id(), definition);
        if (previous != null) throw new IllegalStateException("Duplicate Blacksmithing recipe " + definition.id());
    }

    public static void validate() {
        if (CRAFTABLES.size() != 202) throw new IllegalStateException("Expected 202 recipes, found " + CRAFTABLES.size());
        Map<ResourceLocation, String> outputs = new java.util.HashMap<>();
        for (CraftableDef def : CRAFTABLES.values()) {
            String duplicate = outputs.putIfAbsent(def.resultItem(), def.id());
            if (duplicate != null) throw new IllegalStateException("Duplicate output " + def.resultItem());
            if (def.ingredients().isEmpty() || def.ingredients().stream().anyMatch(i -> i.amount() <= 0))
                throw new IllegalStateException("Invalid ingredients for " + def.id());
            if (def.skillRequirements().stream().noneMatch(s -> s.skillKey().equals("blacksmithy")))
                throw new IllegalStateException("Missing Blacksmithy requirement for " + def.id());
            if (def.armorProfileId() != null && ArmorProfileRegistry.get(def.armorProfileId()) == null)
                throw new IllegalStateException("Missing armor profile " + def.armorProfileId() + " for " + def.id());
            if (def.shieldProfileId() != null && ShieldProfileRegistry.get(def.shieldProfileId()) == null)
                throw new IllegalStateException("Missing shield profile " + def.shieldProfileId() + " for " + def.id());
            if (def.requiresLearnedRecipe() && def.learnedRecipeKey().isBlank())
                throw new IllegalStateException("Blank learned recipe key for " + def.id());
        }
        long weapons = CRAFTABLES.values().stream().filter(def -> CraftableDef.isWeaponCategory(def.category())).count();
        if (weapons != 120) throw new IllegalStateException("Expected 120 weapons, found " + weapons);
    }

    public static CraftableDef get(String id) {
        init();
        return CRAFTABLES.get(LEGACY_ALIASES.getOrDefault(id, id));
    }

    public static List<CraftableDef> getAll() {
        init();
        return CRAFTABLES.values().stream().sorted(Comparator.comparing(CraftableDef::category)
                .thenComparing(CraftableDef::displayName)).toList();
    }

    public static List<CraftableDef> getByCategory(String category) {
        return getAll().stream().filter(def -> def.category().equalsIgnoreCase(category)).toList();
    }

    public static List<String> getCategories() {
        return getAll().stream().map(CraftableDef::category).distinct().collect(Collectors.toList());
    }
}
