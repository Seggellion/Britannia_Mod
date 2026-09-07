package com.seggellion.britannia_mod.worldgen;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LandscapeCropFeaturePolicyTest {
    private static final Path DATA = Path.of(System.getProperty("britannia.projectDir", ".")).resolve("src/main/resources/data/britannia_mod");

    @Test void modifierMatchesTheCompleteThreeFeaturePolicyAndPreservesItsScope() throws Exception {
        var modifier = JsonParser.parseString(Files.readString(DATA.resolve("neoforge/biome_modifier/suppress_landscape_pumpkin_melon.json"))).getAsJsonObject();
        var policy = JsonParser.parseString(Files.readString(DATA.resolve("worldgen/landscape_crop_feature_policy.json"))).getAsJsonObject();
        var expected = Set.of("minecraft:patch_pumpkin", "minecraft:patch_melon", "minecraft:patch_melon_sparse");
        Set<String> removed = new LinkedHashSet<>(), classified = new LinkedHashSet<>();
        for (var feature : modifier.getAsJsonArray("features")) assertTrue(removed.add(feature.getAsString()));
        for (var feature : policy.getAsJsonArray("suppressed")) assertTrue(classified.add(feature.getAsJsonObject().get("feature").getAsString()));
        assertEquals(expected, removed); assertEquals(expected, classified);
        assertEquals("neoforge:remove_features", modifier.get("type").getAsString());
        assertEquals("#minecraft:is_overworld", modifier.get("biomes").getAsString());
        assertEquals("vegetal_decoration", modifier.get("steps").getAsString());
        assertEquals(modifier.get("biomes"), policy.get("biomes"));
        assertEquals(modifier.get("steps"), policy.get("step"));
        assertEquals(1, policy.get("schema").getAsInt());
        assertEquals(6, policy.getAsJsonArray("preserved").size());
    }
}
