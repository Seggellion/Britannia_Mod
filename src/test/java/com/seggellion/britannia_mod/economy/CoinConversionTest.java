package com.seggellion.britannia_mod.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinConversionTest {

    @Test
    void canonicalRatioMatchesTheRealGameEconomy() {
        // Cross-checked directly against MerchantEconomyService's pre-existing countCoins/
        // giveChange literals (10000 for gold, 100 for silver) -- not assumed from memory.
        assertEquals(100, CoinConversion.COPPER_PER_SILVER);
        assertEquals(100, CoinConversion.SILVER_PER_GOLD);
        assertEquals(10_000, CoinConversion.COPPER_PER_GOLD);
    }

    @Test
    void zeroConvertsToNoCoins() {
        CoinConversion.CoinCounts coins = CoinConversion.toCoins(0);
        assertEquals(0, coins.gold());
        assertEquals(0, coins.silver());
        assertEquals(0, coins.copper());
    }

    @Test
    void exactlyOneCopper() {
        CoinConversion.CoinCounts coins = CoinConversion.toCoins(1);
        assertEquals(0, coins.gold());
        assertEquals(0, coins.silver());
        assertEquals(1, coins.copper());
    }

    @Test
    void exactlyOneSilversWorth() {
        CoinConversion.CoinCounts coins = CoinConversion.toCoins(100);
        assertEquals(0, coins.gold());
        assertEquals(1, coins.silver());
        assertEquals(0, coins.copper());
    }

    @Test
    void exactlyOneGoldsWorth() {
        CoinConversion.CoinCounts coins = CoinConversion.toCoins(10_000);
        assertEquals(1, coins.gold());
        assertEquals(0, coins.silver());
        assertEquals(0, coins.copper());
    }

    @Test
    void oneLessThanASilverIsAllCopper() {
        CoinConversion.CoinCounts coins = CoinConversion.toCoins(99);
        assertEquals(0, coins.gold());
        assertEquals(0, coins.silver());
        assertEquals(99, coins.copper());
    }

    @Test
    void oneLessThanAGoldIsMaximalSilverAndCopper() {
        CoinConversion.CoinCounts coins = CoinConversion.toCoins(9_999);
        assertEquals(0, coins.gold());
        assertEquals(99, coins.silver());
        assertEquals(99, coins.copper());
    }

    @Test
    void aLargeRealisticAmountSplitsGreedily() {
        // 123,456,789 copper = 12,345 gold, 67 silver, 89 copper.
        CoinConversion.CoinCounts coins = CoinConversion.toCoins(123_456_789);
        assertEquals(12_345, coins.gold());
        assertEquals(67, coins.silver());
        assertEquals(89, coins.copper());
    }

    @Test
    void negativeTotalCopperIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CoinConversion.toCoins(-1));
    }

    @Test
    void roundTripRecoversTheOriginalValueForARangeOfRealisticAmounts() {
        int[] amounts = {
            0, 1, 5, 50, 99, 100, 101, 999, 1_000, 9_999, 10_000, 10_001,
            55_555, 250_050, 1_000_000, 123_456_789, Integer.MAX_VALUE
        };
        for (int amount : amounts) {
            CoinConversion.CoinCounts coins = CoinConversion.toCoins(amount);
            assertEquals(amount, CoinConversion.toCopper(coins), "round trip failed for amount " + amount);
            assertEquals(amount, CoinConversion.toCopper(coins.gold(), coins.silver(), coins.copper()),
                "round trip via the 3-arg overload failed for amount " + amount);
        }
    }

    @Test
    void toCoinsNeverProducesANegativeOrOutOfRangeComponentForAnyValidNonNegativeInput() {
        int[] amounts = {0, 1, 100, 10_000, 999_999, Integer.MAX_VALUE};
        for (int amount : amounts) {
            CoinConversion.CoinCounts coins = CoinConversion.toCoins(amount);
            assertTrue(coins.gold() >= 0, "gold must never be negative for amount " + amount);
            assertTrue(coins.silver() >= 0, "silver must never be negative for amount " + amount);
            assertTrue(coins.copper() >= 0, "copper must never be negative for amount " + amount);
            assertTrue(coins.silver() < CoinConversion.SILVER_PER_GOLD,
                "silver must always be a genuine remainder, not a rolled-up multiple of gold, for amount " + amount);
            assertTrue(coins.copper() < CoinConversion.COPPER_PER_SILVER,
                "copper must always be a genuine remainder, not a rolled-up multiple of silver, for amount " + amount);
        }
    }

    @Test
    void negativeComponentsAreRejectedByToCopper() {
        assertThrows(IllegalArgumentException.class, () -> CoinConversion.toCopper(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> CoinConversion.toCopper(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> CoinConversion.toCopper(0, 0, -1));
    }

    @Test
    void toCopperOverflowThrowsRatherThanSilentlyWrapping() {
        assertThrows(ArithmeticException.class, () -> CoinConversion.toCopper(Integer.MAX_VALUE, 0, 0));
    }

    @Test
    void coinCountsRejectsNegativeComponentsDirectly() {
        assertThrows(IllegalArgumentException.class, () -> new CoinConversion.CoinCounts(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CoinConversion.CoinCounts(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CoinConversion.CoinCounts(0, 0, -1));
    }
}
