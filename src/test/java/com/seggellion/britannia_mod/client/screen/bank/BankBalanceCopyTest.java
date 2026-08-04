package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.client.screen.bank.BankBalanceCopy.Denomination;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankBalanceCopyTest {

    // ---------- Singular and plural ----------

    @Test
    void usesTheSingularOnlyForExactlyOne() {
        for (Denomination denomination : Denomination.values()) {
            assertTrue(BankBalanceCopy.amountKey(denomination, 1).endsWith(".one"), denomination.name());
        }
    }

    @Test
    void usesThePluralForZero() {
        // "0 gold coins" is correct English, and the case a naive n < 2 test gets wrong.
        for (Denomination denomination : Denomination.values()) {
            assertTrue(BankBalanceCopy.amountKey(denomination, 0).endsWith(".many"), denomination.name());
        }
    }

    @Test
    void usesThePluralForEverythingAboveOne() {
        for (Denomination denomination : Denomination.values()) {
            for (int amount : new int[]{2, 3, 47, 99, 1_000, Integer.MAX_VALUE}) {
                assertTrue(
                        BankBalanceCopy.amountKey(denomination, amount).endsWith(".many"),
                        denomination + " @ " + amount
                );
            }
        }
    }

    @Test
    void givesEachDenominationItsOwnKey() {
        Set<String> singular = new HashSet<>();
        Set<String> plural = new HashSet<>();
        for (Denomination denomination : Denomination.values()) {
            singular.add(BankBalanceCopy.amountKey(denomination, 1));
            plural.add(BankBalanceCopy.amountKey(denomination, 2));
        }
        assertEquals(3, singular.size());
        assertEquals(3, plural.size());
        assertNotEquals(singular, plural);
    }

    @Test
    void speaksTheDenominationsHighestValueFirst() {
        // The order the balance sentence and the legacy ledger line both use.
        assertEquals(Denomination.GOLD, Denomination.values()[0]);
        assertEquals(Denomination.SILVER, Denomination.values()[1]);
        assertEquals(Denomination.COPPER, Denomination.values()[2]);
    }

    // ---------- Which sentence ----------

    @Test
    void usesTheEmptySentenceOnlyWhenAllThreeAreZero() {
        assertTrue(BankBalanceCopy.bodyKey(0, 0, 0).endsWith("body_empty"));
    }

    @Test
    void usesTheOrdinarySentenceWhenAnyDenominationIsHeld() {
        // Any one non-zero denomination means the account holds something, so the three-part
        // sentence is right even though two of its numbers are zero.
        assertEquals(BankBalanceCopy.bodyKey(1, 0, 0), BankBalanceCopy.bodyKey(0, 0, 1));
        for (int[] balances : new int[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {3, 47, 92}}) {
            String key = BankBalanceCopy.bodyKey(balances[0], balances[1], balances[2]);
            assertTrue(key.endsWith(".body"), "expected the ordinary sentence for " + key);
        }
    }

    // ---------- Formatting ----------

    @Test
    void groupsLargeAmountsSoTheyCanBeRead() {
        assertEquals("1,000", BankBalanceCopy.formatAmount(1_000));
        assertEquals("1,234,567", BankBalanceCopy.formatAmount(1_234_567));
        assertEquals("2,147,483,647", BankBalanceCopy.formatAmount(Integer.MAX_VALUE));
    }

    @Test
    void leavesSmallAmountsAlone() {
        assertEquals("0", BankBalanceCopy.formatAmount(0));
        assertEquals("1", BankBalanceCopy.formatAmount(1));
        assertEquals("999", BankBalanceCopy.formatAmount(999));
    }

    @Test
    void formatsIndependentlyOfTheClientSystemLocale() {
        // Locale.ROOT is deliberate: en_us.json's strings are written against this grouping, and a
        // client running under a comma-decimal locale must not silently render "1.234.567".
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("1,234,567", BankBalanceCopy.formatAmount(1_234_567));
        } finally {
            Locale.setDefault(previous);
        }
    }

}
