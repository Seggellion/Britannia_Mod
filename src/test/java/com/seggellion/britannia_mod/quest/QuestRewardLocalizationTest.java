package com.seggellion.britannia_mod.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M1 (discovery D5, D8): the coins the questline pays out and the key that
 * opens the journal must have display names, or the player sees raw translation keys.
 */
class QuestRewardLocalizationTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path LANG = PROJECT.resolve("src/main/resources/assets/britannia_mod/lang/en_us.json");
    private static final Path ITEM_REGISTRY = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java");
    private static final Path KEYBINDS = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/client/Keybinds.java");

    private static final List<String> REQUIRED_KEYS = List.of(
        "item.britannia_mod.copper_coin",
        "item.britannia_mod.silver_coin",
        "item.britannia_mod.gold_coin",
        "key.britannia_mod.open_skills");

    @Test
    void theCoinsAndTheJournalKeyHaveDisplayNames() throws IOException {
        String raw = Files.readString(LANG, StandardCharsets.UTF_8);
        JsonObject lang = JsonParser.parseString(raw).getAsJsonObject();

        for (String key : REQUIRED_KEYS) {
            assertTrue(lang.has(key), key + " is missing from en_us.json");
            assertTrue(lang.get(key).isJsonPrimitive() && lang.get(key).getAsJsonPrimitive().isString(),
                key + " must be a string");
            assertFalse(lang.get(key).getAsString().isBlank(), key + " must not be blank");
            assertEquals(1, occurrences(raw, key), key + " must be defined exactly once (a later duplicate silently wins)");
        }
        assertEquals("Copper Coin", lang.get("item.britannia_mod.copper_coin").getAsString());
        assertEquals("Silver Coin", lang.get("item.britannia_mod.silver_coin").getAsString());
    }

    @Test
    void theCoinKeysNameTheRegisteredItems() throws IOException {
        String registry = Files.readString(ITEM_REGISTRY, StandardCharsets.UTF_8);
        for (String id : List.of("copper_coin", "silver_coin", "gold_coin")) {
            assertTrue(registry.contains("register(\"" + id + "\""),
                "ItemRegistry must register '" + id + "' for item.britannia_mod." + id + " to apply");
        }
    }

    @Test
    void theKeybindingAndItsCategoryAreTranslated() throws IOException {
        String keybinds = Files.readString(KEYBINDS, StandardCharsets.UTF_8);
        Matcher mapping = Pattern.compile(
                "new KeyMapping\\(\\s*\"([^\"]+)\"\\s*,\\s*[^,]+,\\s*[^,]+,\\s*\"([^\"]+)\"", Pattern.DOTALL)
            .matcher(keybinds);
        assertTrue(mapping.find(), "Keybinds.java must declare the skills/quests KeyMapping");
        String keyId = mapping.group(1);
        String category = mapping.group(2);
        assertEquals("key.britannia_mod.open_skills", keyId);

        JsonObject lang = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(lang.has(keyId) && !lang.get(keyId).getAsString().isBlank(), keyId + " must be translated");

        if (lang.has(category)) {
            assertFalse(lang.get(category).getAsString().isBlank(), category + " must not be blank");
            return;
        }
        // Not ours: the category must then be one vanilla ships ("key.categories.ui" is vanilla's
        // Interface category). When the vanilla language file is on the classpath, prove it.
        assertTrue(category.startsWith("key.categories."),
            category + " is neither translated by this mod nor a vanilla keybinding category");
        try (InputStream vanilla = QuestRewardLocalizationTest.class.getClassLoader()
                .getResourceAsStream("assets/minecraft/lang/en_us.json")) {
            if (vanilla == null) return;
            JsonObject vanillaLang = JsonParser.parseString(new String(vanilla.readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject();
            assertTrue(vanillaLang.has(category), category + " is not a vanilla keybinding category");
        }
    }

    private static int occurrences(String raw, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:").matcher(raw);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }
}
