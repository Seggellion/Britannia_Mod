package com.seggellion.britannia_mod.client.screen.bank;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bank's four sounds (owner, 2026-08-04), guarded the same way {@link
 * BankTranslationKeysTest} guards strings: a typo'd {@code sounds.json} key or a missing
 * {@code .ogg} does not crash -- the game plays silence -- which is exactly the defect no
 * compiler and no other test would catch.
 */
class BankSoundAssetsTest {

    private static final Path PROJECT_DIR =
            Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path SOUNDS_JSON =
            PROJECT_DIR.resolve(Path.of("src", "main", "resources", "assets", "britannia_mod", "sounds.json"));
    private static final Path SOUNDS_DIR =
            PROJECT_DIR.resolve(Path.of("src", "main", "resources", "assets", "britannia_mod", "sounds"));

    /** Event name -> the file its sounds.json entry must reference. */
    private static final Map<String, String> BANK_SOUNDS = Map.of(
            "chest_open", "chest_open",
            "chest_close", "chest_close",
            "bank_deposit", "hit02",
            "bank_withdraw", "leather1",
            // Coins, either direction (owner, 2026-08-04). Pre-existing event, newly used by
            // banking -- pinned here so a later edit elsewhere cannot silently break this use.
            "gold_coin", "gold_coin"
    );

    @Test
    void everyBankSoundEventExistsAndItsFileIsOnDisk() throws IOException {
        assertTrue(Files.exists(SOUNDS_JSON), "sounds.json not found at " + SOUNDS_JSON.toAbsolutePath());
        JsonObject sounds = JsonParser
                .parseString(Files.readString(SOUNDS_JSON, StandardCharsets.UTF_8))
                .getAsJsonObject();

        for (Map.Entry<String, String> entry : BANK_SOUNDS.entrySet()) {
            String event = entry.getKey();
            String file = entry.getValue();

            assertTrue(sounds.has(event), "sounds.json has no \"" + event + "\" event");
            String reference = sounds.getAsJsonObject(event)
                    .getAsJsonArray("sounds").get(0).getAsString();
            assertTrue(reference.equals("britannia_mod:" + file),
                    "\"" + event + "\" references " + reference + ", expected britannia_mod:" + file);
            assertTrue(Files.exists(SOUNDS_DIR.resolve(file + ".ogg")),
                    file + ".ogg is missing from the sounds directory");
        }
    }
}
