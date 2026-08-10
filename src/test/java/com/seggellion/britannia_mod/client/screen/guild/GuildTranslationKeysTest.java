package com.seggellion.britannia_mod.client.screen.guild;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.network.payload.GuildTrainingOpenS2CPayload.Offer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the Guildmaster screen's user-visible strings are actually translatable, rather than
 * merely routed through {@code Component.translatable}.
 *
 * <p>Mirrors {@code BankTranslationKeysTest} and exists for the same failure mode: a missing key
 * does not crash. Minecraft renders the raw key, so
 * {@code screen.britannia_mod.guild.offer.buy} appears on the button where a sentence should be —
 * a defect no compiler and no other test in this project would catch.
 */
class GuildTranslationKeysTest {
    // Resolved through the same gradle-supplied property BankTranslationKeysTest uses: the test
    // working directory is not the project root, so a relative path silently finds nothing.
    private static final Path LANG =
            Path.of(System.getProperty("britannia.projectDir", "."))
                    .resolve(Path.of("src", "main", "resources", "assets", "britannia_mod", "lang", "en_us.json"));

    private static JsonObject language() throws IOException {
        assertTrue(Files.exists(LANG), "language file not found at " + LANG.toAbsolutePath());
        // The file carries a UTF-8 BOM; strip it or the first key parses with it attached.
        String raw = Files.readString(LANG, StandardCharsets.UTF_8);
        if (!raw.isEmpty() && raw.charAt(0) == '﻿') raw = raw.substring(1);
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    @Test
    void everyGuildScreenKeyExists() throws IOException {
        JsonObject language = language();

        for (String key : List.of(
                GuildTrainingOfferLabel.KEY_AT_CAP,
                GuildTrainingOfferLabel.KEY_NO_GOLD,
                GuildTrainingOfferLabel.KEY_BUY,
                GuildmasterTrainingScreen.KEY_CLOSE
        )) {
            assertTrue(language.has(key), "missing translation key: " + key);
            assertTrue(language.get(key).getAsString().isBlank() == false,
                    "empty translation for: " + key);
        }
    }

    @Test
    void everyGuildKeyTakesTheArgumentsItsCallerSupplies() throws IOException {
        JsonObject language = language();

        // A key with fewer placeholders than arguments silently drops information - a price that
        // never appears on the button is worse than a raw key, because it looks fine.
        assertEquals(2, placeholders(language, GuildTrainingOfferLabel.KEY_AT_CAP));
        assertEquals(2, placeholders(language, GuildTrainingOfferLabel.KEY_NO_GOLD));
        assertEquals(4, placeholders(language, GuildTrainingOfferLabel.KEY_BUY));
        assertEquals(0, placeholders(language, GuildmasterTrainingScreen.KEY_CLOSE));
    }

    @Test
    void eachOfferStateSelectsItsOwnKey() {
        // "Taught in full" and "not enough gold" are different facts. Showing the first when the
        // second is true tells a player never to come back.
        assertEquals(GuildTrainingOfferLabel.KEY_AT_CAP,
                key(new Offer("swordsmanship", "Swordsmanship", 400, 400, 0)));
        assertEquals(GuildTrainingOfferLabel.KEY_NO_GOLD,
                key(new Offer("swordsmanship", "Swordsmanship", 0, 400, 0)));
        assertEquals(GuildTrainingOfferLabel.KEY_BUY,
                key(new Offer("swordsmanship", "Swordsmanship", 127, 400, 273)));
    }

    @Test
    void tenthsRenderFixedPoint() {
        assertEquals("12.7", GuildTrainingOfferLabel.tenths(127));
        assertEquals("40.0", GuildTrainingOfferLabel.tenths(400));
        assertEquals("0.0", GuildTrainingOfferLabel.tenths(0));
    }

    private static String key(Offer offer) {
        return ((net.minecraft.network.chat.contents.TranslatableContents)
                GuildTrainingOfferLabel.of(offer).getContents()).getKey();
    }

    private static int placeholders(JsonObject language, String key) {
        String value = language.get(key).getAsString();
        int count = 0;
        for (int index = 0; index < value.length() - 1; index++) {
            if (value.charAt(index) == '%' && value.charAt(index + 1) == 's') count++;
        }
        return count;
    }
}
