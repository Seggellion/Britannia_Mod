package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.bank.currency.CurrencyItemRegistry;
import com.seggellion.britannia_mod.component.BankChequeData;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BankChequeTintTest {

    private static BankChequeData cheque(String currencyKey) {
        return new BankChequeData(UUID.randomUUID(), 5_000_000L, "Britannia Bank", currencyKey);
    }

    @Test
    void givesEachDenominationItsOwnColour() {
        Set<Integer> colours = new HashSet<>();
        colours.add(BankChequeTint.forCurrencyKey(CurrencyItemRegistry.GOLD_KEY));
        colours.add(BankChequeTint.forCurrencyKey(CurrencyItemRegistry.SILVER_KEY));
        colours.add(BankChequeTint.forCurrencyKey(CurrencyItemRegistry.COPPER_KEY));
        assertEquals(3, colours.size(), "two denominations share a tint");
    }

    @Test
    void tintsAreDistinguishableRatherThanNearlyIdentical() {
        // Three colours that differ only in the last few bits would pass the test above and be
        // useless in an inventory. Each channel pair must differ by a visible margin.
        assertChannelsDiffer(BankChequeTint.GOLD, BankChequeTint.SILVER);
        assertChannelsDiffer(BankChequeTint.GOLD, BankChequeTint.COPPER);
        assertChannelsDiffer(BankChequeTint.SILVER, BankChequeTint.COPPER);
    }

    private static void assertChannelsDiffer(int a, int b) {
        int distance = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF))
                + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
                + Math.abs((a & 0xFF) - (b & 0xFF));
        org.junit.jupiter.api.Assertions.assertTrue(distance >= 60,
                "tints too close to tell apart: " + Integer.toHexString(a) + " vs " + Integer.toHexString(b));
    }

    @Test
    void readsTheTintFromTheChequesOwnData() {
        assertEquals(BankChequeTint.GOLD, BankChequeTint.forData(cheque(CurrencyItemRegistry.GOLD_KEY)));
        assertEquals(BankChequeTint.SILVER, BankChequeTint.forData(cheque(CurrencyItemRegistry.SILVER_KEY)));
        assertEquals(BankChequeTint.COPPER, BankChequeTint.forData(cheque(CurrencyItemRegistry.COPPER_KEY)));
    }

    @Test
    void aChequeIssuedBeforeDenominationsExistedReadsAsGold() {
        // The component defaults an absent or blank key to gold, which is the true answer for
        // every cheque issued before Milestone 8b rather than a guess.
        assertEquals(BankChequeTint.GOLD, BankChequeTint.forData(cheque(null)));
        assertEquals(BankChequeTint.GOLD, BankChequeTint.forData(cheque("")));
        assertEquals(BankChequeTint.GOLD, BankChequeTint.forData(null));
    }

    @Test
    void anUnknownKeyLeavesTheArtworkUntintedRatherThanBlack() {
        // White multiplies to leave the texture as drawn. A zero would render the cheque black,
        // which looks like a rendering fault rather than an unrecognised denomination.
        assertEquals(0xFFFFFF, BankChequeTint.forCurrencyKey("platinum"));
        assertNotEquals(0, BankChequeTint.forCurrencyKey("platinum"));
    }

    @Test
    void everyTintIsOpaqueRgbWithNoStrayAlpha() {
        // An ItemColor's return value is read as 0xRRGGBB; a stray high byte would multiply the
        // texture by an unintended alpha.
        for (int tint : new int[]{BankChequeTint.GOLD, BankChequeTint.SILVER, BankChequeTint.COPPER, BankChequeTint.UNTINTED}) {
            assertEquals(0, tint >>> 24, "unexpected high byte in " + Integer.toHexString(tint));
        }
    }
}
