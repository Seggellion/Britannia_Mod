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

    /**
     * Every key the epic can put on screen so far. Extend this as milestones add screens -- it is
     * the list Playbook §3.1's per-milestone translatability gate is checked against.
     */
    private static List<String> allBankStatusKeys() {
        TreeSet<String> keys = new TreeSet<>();

        // Milestone 3: server outcomes.
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

        // Milestone 4: the hub, its destinations, and the placeholders.
        for (BankNavigation.Destination destination : BankNavigation.Destination.values()) {
            keys.add(destination.labelKey());
        }
        keys.add(BankNavigation.greetingKey(null));
        keys.add(BankNavigation.greetingKey("Britain"));
        keys.add("screen.britannia_mod.bank.main.title");
        keys.add("screen.britannia_mod.bank.action.back");
        keys.add("screen.britannia_mod.bank.placeholder.bank_box.title");
        keys.add("screen.britannia_mod.bank.placeholder.bank_box.body");

        // Milestone 5: the Balance screen. Both plural forms of all three denominations, because
        // a missing ".one" only shows up on an account holding exactly one coin -- which is
        // exactly the account nobody thinks to test by hand.
        for (BankBalanceCopy.Denomination denomination : BankBalanceCopy.Denomination.values()) {
            keys.add(BankBalanceCopy.amountKey(denomination, 1));
            keys.add(BankBalanceCopy.amountKey(denomination, 2));
        }
        keys.add(BankBalanceCopy.bodyKey(3, 47, 92));
        keys.add(BankBalanceCopy.bodyKey(0, 0, 0));
        keys.add(BankBalanceCopy.DEPOSIT_ALL_UNAVAILABLE.translationKey());
        keys.add("screen.britannia_mod.bank.balance.title");
        keys.add("screen.britannia_mod.bank.action.deposit_all_coins");
        keys.add("screen.britannia_mod.bank.action.deposit_all_coins.pending");

        return new ArrayList<>(keys);
    }

    @Test
    void theBalanceSentencesCarryTheArgumentsTheScreenPasses() throws IOException {
        JsonObject language = language();
        // Three amounts in the ordinary sentence, none in the empty one, and exactly one number
        // in each denomination phrase.
        assertEquals(3, countPlaceholders(language.get(BankBalanceCopy.bodyKey(3, 47, 92)).getAsString()));
        assertEquals(0, countPlaceholders(language.get(BankBalanceCopy.bodyKey(0, 0, 0)).getAsString()));
        for (BankBalanceCopy.Denomination denomination : BankBalanceCopy.Denomination.values()) {
            for (int amount : new int[]{1, 2}) {
                String key = BankBalanceCopy.amountKey(denomination, amount);
                assertEquals(1, countPlaceholders(language.get(key).getAsString()), key);
            }
        }
    }

    @Test
    void theRetiredBalancePlaceholderStringsAreGone() throws IOException {
        // Milestone 5 replaced the placeholder with the real screen. Leaving its copy behind would
        // be dead weight that reads as a live feature to the next person editing this file.
        JsonObject language = language();
        assertFalse(language.has("screen.britannia_mod.bank.placeholder.balance.title"));
        assertFalse(language.has("screen.britannia_mod.bank.placeholder.balance.body"));
    }

    @Test
    void theGreetingsCarryTheFormatArgumentsTheScreenPasses() throws IOException {
        JsonObject language = language();
        // The city-less greeting takes the teller's name; the city one takes name then city.
        // A mismatch here throws at render time inside Component.translatable, not at compile time.
        assertEquals(1, countPlaceholders(language.get(BankNavigation.greetingKey(null)).getAsString()));
        assertEquals(2, countPlaceholders(language.get(BankNavigation.greetingKey("Britain")).getAsString()));
    }

    private static int countPlaceholders(String value) {
        return value.split("%s", -1).length - 1;
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
