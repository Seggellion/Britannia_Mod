package com.seggellion.britannia_mod.client.screen.bank;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Kind;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Operation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 3: proves the epic's user-visible strings are actually translatable, rather than
 * merely routed through {@code Component.translatable}.
 *
 * <p>A missing key does not crash -- Minecraft renders the raw key, so
 * {@code screen.britannia_mod.bank.status.cheque_voided} appears on the parchment where a sentence
 * should be. That is a defect no compiler and no other test in this project would catch, and it is
 * the specific failure mode Playbook §3.1's per-milestone translatability gate exists to prevent.
 */
class BankTranslationKeysTest {

    private static final Path LANG =
            Path.of("src", "main", "resources", "assets", "britannia_mod", "lang", "en_us.json");

    private static JsonObject language() throws IOException {
        assertTrue(Files.exists(LANG), "language file not found at " + LANG.toAbsolutePath());
        String json = Files.readString(LANG, StandardCharsets.UTF_8);
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /** Every key this milestone can put on screen. */
    private static List<String> allBankStatusKeys() {
        TreeSet<String> keys = new TreeSet<>();
        for (Operation operation : Operation.values()) {
            for (Kind kind : Kind.values()) {
                keys.add(BankStatusPresenter.forResult(operation, kind).translationKey());
            }
        }
        for (BankStatusPresenter.Status status : new BankStatusPresenter.Status[]{
                BankStatusPresenter.EMPTY_AMOUNT,
                BankStatusPresenter.INVALID_AMOUNT,
                BankStatusPresenter.AMOUNT_TOO_LARGE,
                BankStatusPresenter.NO_DENOMINATION_SELECTED,
                BankStatusPresenter.INSUFFICIENT_BALANCE,
                BankStatusPresenter.NOTHING_SELECTED,
                BankStatusPresenter.EMPTY_VAULT
        }) {
            keys.add(status.translationKey());
        }
        return new ArrayList<>(keys);
    }

    @Test
    void everyStatusKeyHasAnEnglishTranslation() throws IOException {
        JsonObject language = language();
        List<String> missing = new ArrayList<>();
        for (String key : allBankStatusKeys()) {
            if (!language.has(key)) missing.add(key);
        }
        assertTrue(missing.isEmpty(), "no English string for: " + missing);
    }

    @Test
    void noStatusTranslationIsBlank() throws IOException {
        JsonObject language = language();
        List<String> blank = new ArrayList<>();
        for (String key : allBankStatusKeys()) {
            if (language.has(key) && language.get(key).getAsString().isBlank()) blank.add(key);
        }
        assertTrue(blank.isEmpty(), "blank translation for: " + blank);
    }

    @Test
    void theLanguageFileIsStillValidJson() throws IOException {
        // Cheap, but this file is hand-edited and a trailing comma would break every string in
        // the mod, not just banking's.
        assertFalse(language().entrySet().isEmpty());
    }

    @Test
    void theLanguageFileCarriesNoDuplicateBankKeys() throws IOException {
        // Gson silently keeps the last value for a repeated key, so a duplicate would not show up
        // as a parse error -- it would show up as an edit that mysteriously does nothing.
        String json = Files.readString(LANG, StandardCharsets.UTF_8);
        for (String key : allBankStatusKeys()) {
            int occurrences = json.split("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:", -1).length - 1;
            assertEquals(1, occurrences, key + " appears " + occurrences + " times");
        }
    }
}
