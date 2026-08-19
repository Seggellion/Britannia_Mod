package com.seggellion.britannia_mod.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The menu theme's resources, guarded the way {@code BankSoundAssetsTest} guards the bank's.
 * {@link MenuMusicBranding} hands Minecraft a sound event and nothing checks that the event
 * resolves to a file: a typo'd {@code sounds.json} key or an absent {@code .ogg} leaves the
 * title screen silent rather than throwing, and silence is indistinguishable from a music
 * slider at zero.
 */
class MenuMusicAssetsTest {

    private static final Path PROJECT_DIR =
            Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path SOUNDS_JSON = PROJECT_DIR.resolve(
            Path.of("src", "main", "resources", "assets", "britannia_mod", "sounds.json"));
    private static final Path SOUNDS_DIR = PROJECT_DIR.resolve(
            Path.of("src", "main", "resources", "assets", "britannia_mod", "sounds"));

    /** The event {@link MenuMusicBranding} plays, as registered in {@code ModSounds}. */
    private static final String EVENT = "menu_music";

    /** The track the event resolves to. */
    private static final String FILE = "stones2";

    @Test
    void menuMusicEventResolvesToTheTrackOnDisk() throws IOException {
        assertTrue(Files.exists(SOUNDS_JSON), "sounds.json not found at " + SOUNDS_JSON.toAbsolutePath());
        JsonObject sounds = JsonParser
                .parseString(Files.readString(SOUNDS_JSON, StandardCharsets.UTF_8))
                .getAsJsonObject();

        assertTrue(sounds.has(EVENT), "sounds.json has no \"" + EVENT + "\" event");
        JsonElement entry = sounds.getAsJsonObject(EVENT).getAsJsonArray("sounds").get(0);
        String reference = entry.isJsonObject()
                ? entry.getAsJsonObject().get("name").getAsString()
                : entry.getAsString();

        assertEquals("britannia_mod:" + FILE, reference,
                "\"" + EVENT + "\" references " + reference);
        assertTrue(Files.exists(SOUNDS_DIR.resolve(FILE + ".ogg")),
                FILE + ".ogg is missing from " + SOUNDS_DIR.toAbsolutePath());
    }

    @Test
    void menuMusicIsStreamedRatherThanBuffered() throws IOException {
        JsonObject sounds = JsonParser
                .parseString(Files.readString(SOUNDS_JSON, StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonElement entry = sounds.getAsJsonObject(EVENT).getAsJsonArray("sounds").get(0);

        assertTrue(entry.isJsonObject() && entry.getAsJsonObject().has("stream")
                        && entry.getAsJsonObject().get("stream").getAsBoolean(),
                "a full-length music track must set \"stream\": true so it is decoded as it plays "
                        + "instead of being held in memory as one buffer");
    }
}
