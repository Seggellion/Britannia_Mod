package com.seggellion.britannia_mod.service.guild;

import com.seggellion.britannia_mod.economy.CoinConversion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pricing rule that takes real money off a player. Every figure the design document states by
 * name is pinned here as its own case.
 */
class GuildTrainingQuoteTest {
    private static final int SKILL_MAX_TENTHS = 1000; // a 100.0 skill, the seeded UO default

    private static int gold(int amount) {
        return amount * CoinConversion.COPPER_PER_GOLD;
    }

    private static GuildTrainingQuote quote(int currentTenths, int availableCopper) {
        return GuildTrainingQuote.of("swordsmanship", true, currentTenths, SKILL_MAX_TENTHS, availableCopper);
    }

    // ---------- The design document's headline figures ----------

    @Test
    void zeroToFortyCostsFourHundredGold() {
        GuildTrainingQuote result = quote(0, gold(1000));

        assertTrue(result.purchasable());
        assertEquals(400, result.purchasedTenths());
        assertEquals(400, result.costGold());
        assertEquals(400, result.finalTenths(), "should land exactly on 40.0");
    }

    @Test
    void zeroToOnePointZeroCostsTenGold() {
        GuildTrainingQuote result = quote(0, gold(10));

        assertEquals(10, result.purchasedTenths());
        assertEquals(10, result.costGold());
        assertEquals(10, result.finalTenths());
    }

    @Test
    void twelvePointSevenToTwentyCostsSeventyThreeGold() {
        // 127 -> 200 tenths is 73 tenths, and one gold per tenth makes it 73 gold.
        GuildTrainingQuote result = quote(127, gold(73));

        assertEquals(73, result.purchasedTenths());
        assertEquals(73, result.costGold());
        assertEquals(200, result.finalTenths());
    }

    @Test
    void thirtyNinePointNineToFortyCostsOneGold() {
        GuildTrainingQuote result = quote(399, gold(500));

        assertEquals(1, result.purchasedTenths());
        assertEquals(1, result.costGold());
        assertEquals(400, result.finalTenths());
    }

    @Test
    void atTheCeilingNothingIsTrainable() {
        GuildTrainingQuote result = quote(400, gold(10_000));

        assertFalse(result.purchasable());
        assertEquals(GuildTrainingQuote.Rejection.ALREADY_AT_CAP, result.rejection());
        assertEquals(0, result.costGold());
    }

    @Test
    void aboveTheCeilingIsAlsoRejectedRatherThanRefunded() {
        // Reachable through the admin /setskill command or a powerscroll.
        GuildTrainingQuote result = quote(750, gold(10_000));

        assertEquals(GuildTrainingQuote.Rejection.ALREADY_AT_CAP, result.rejection());
        assertEquals(0, result.purchasedTenths());
    }

    // ---------- Pure-UO "takes what it takes" ----------

    @Test
    void goldIsTheBindingConstraintWhenItIsScarcer() {
        GuildTrainingQuote result = quote(0, gold(30));

        assertEquals(30, result.purchasedTenths(), "30 gold buys 3.0 skill and no more");
        assertEquals(30, result.costGold());
        assertEquals(30, result.finalTenths());
    }

    @Test
    void theCeilingIsTheBindingConstraintWhenGoldIsPlentiful() {
        GuildTrainingQuote result = quote(350, gold(10_000));

        assertEquals(50, result.purchasedTenths(), "only 5.0 of headroom remains");
        assertEquals(50, result.costGold(), "the player is never charged for headroom that is not there");
    }

    @Test
    void exactFundsBuyExactlyTheHeadroom() {
        GuildTrainingQuote result = quote(399, gold(1));

        assertEquals(1, result.purchasedTenths());
        assertEquals(1, result.costGold());
    }

    // ---------- Sub-gold change buys nothing ----------

    @Test
    void silverAndCopperAloneCannotBuyTraining() {
        // One gold is the smallest unit that buys anything, so 99 silver + 99 copper buys nothing.
        int almostOneGold = CoinConversion.COPPER_PER_GOLD - 1;
        GuildTrainingQuote result = quote(0, almostOneGold);

        assertFalse(result.purchasable());
        assertEquals(GuildTrainingQuote.Rejection.NOT_ENOUGH_GOLD, result.rejection());
        assertEquals(0, result.costGold());
    }

    @Test
    void aSubGoldRemainderIsNeverCharged() {
        // 5 gold and change buys exactly 0.5 skill for exactly 5 gold; the remainder stays put.
        GuildTrainingQuote result = quote(0, gold(5) + 4_321);

        assertEquals(5, result.purchasedTenths());
        assertEquals(5, result.costGold());
        assertEquals(gold(5), result.costCopper(), "the charge must be whole gold, never the remainder");
    }

    @Test
    void carryingNothingIsRejected() {
        assertEquals(GuildTrainingQuote.Rejection.NOT_ENOUGH_GOLD, quote(0, 0).rejection());
    }

    // ---------- Caps other than the guild ceiling ----------

    @Test
    void aSkillWhoseOwnMaximumIsBelowFortyBindsFirst() {
        // A Guildmaster must not push a skill past its own configured maximum.
        GuildTrainingQuote result =
                GuildTrainingQuote.of("swordsmanship", true, 0, 250, gold(10_000));

        assertEquals(250, result.capTenths());
        assertEquals(250, result.purchasedTenths());
        assertEquals(250, result.costGold());
    }

    @Test
    void aSkillTheGuildDoesNotTeachIsRejectedBeforeAnyPricing() {
        GuildTrainingQuote result =
                GuildTrainingQuote.of("magery", false, 0, SKILL_MAX_TENTHS, gold(10_000));

        assertFalse(result.purchasable());
        assertEquals(GuildTrainingQuote.Rejection.NOT_TAUGHT, result.rejection());
        assertEquals(0, result.costGold());
    }

    // ---------- The gold/tenths identity ----------

    @Test
    void theCostInGoldAlwaysEqualsTheTenthsPurchased() {
        // The identity the whole UO rule rests on. Checked across the full range rather than at a
        // couple of points, because a single off-by-one in either direction is real money.
        for (int current = 0; current <= 400; current++) {
            GuildTrainingQuote result = quote(current, gold(10_000));
            assertEquals(result.purchasedTenths(), result.costGold(),
                    "one gold must always buy exactly one tenth, at current=" + current);
            assertEquals(400, result.finalTenths(),
                    "unbounded gold should always reach the ceiling, at current=" + current);
        }
    }
}
