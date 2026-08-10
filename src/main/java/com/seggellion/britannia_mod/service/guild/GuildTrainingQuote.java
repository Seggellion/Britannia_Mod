package com.seggellion.britannia_mod.service.guild;

import com.seggellion.britannia_mod.economy.CoinConversion;

/**
 * Guildmaster milestone 5: what a training purchase would cost and yield. Pure arithmetic, no
 * player, no entity, no I/O — so the rule that actually takes money off a player is fully testable
 * in JUnit, which neither a {@code Screen} nor an entity would be.
 *
 * <h2>Integer tenths, never floating point</h2>
 * Milestone 0 proved the canonical skill precision is one decimal place: Rails stores
 * {@code shard_user_skills.value} as a float but quantises it with {@code .round(1)} on both write
 * endpoints. Every value here is therefore an {@code int} count of tenths, converted to float only
 * at the Rails boundary. A price computed through {@code float} could differ by a coin from the
 * price a player was shown, and this is real money.
 *
 * <h2>The UO rule</h2>
 * One gold buys one tenth of a skill point, so <b>the number of tenths purchased is also the price
 * in gold</b> — that identity is why the design's headline figures work out: 0.0 to 40.0 is 400
 * tenths and 400 gold.
 *
 * <h2>Pure-UO behaviour, by owner decision</h2>
 * Training "takes what it takes": the player is charged for as much as their gold and the ceiling
 * allow, with no requested amount and no spend cap. Sub-gold change buys nothing, because one gold
 * is the smallest unit that buys anything at all.
 */
public record GuildTrainingQuote(
        String skillSlug,
        int currentTenths,
        int capTenths,
        int purchasedTenths,
        int costGold,
        Rejection rejection
) {
    /** The UO NPC-training ceiling: 40.0 skill. */
    public static final int GUILDMASTER_MAX_TENTHS = 400;

    public enum Rejection {
        /** Nothing wrong; the quote is purchasable. */
        NONE,
        /** Already at or above the effective ceiling. */
        ALREADY_AT_CAP,
        /** Below the ceiling, but the player cannot afford even one tenth. */
        NOT_ENOUGH_GOLD,
        /** This Guildmaster does not teach the requested skill. */
        NOT_TAUGHT
    }

    public boolean purchasable() {
        return rejection == Rejection.NONE && purchasedTenths > 0;
    }

    /** The exact charge, in the canonical smallest unit the coin economy uses. */
    public int costCopper() {
        return costGold * CoinConversion.COPPER_PER_GOLD;
    }

    public int finalTenths() {
        return currentTenths + purchasedTenths;
    }

    /**
     * @param currentTenths   the player's current value, in tenths
     * @param skillMaxTenths  the skill's own configured maximum, in tenths (Rails
     *                        {@code skills.max_value} × 10)
     * @param availableCopper everything the player is carrying, in copper
     */
    public static GuildTrainingQuote of(
            String skillSlug, boolean taught, int currentTenths, int skillMaxTenths, int availableCopper
    ) {
        if (!taught) {
            return rejected(skillSlug, currentTenths, GUILDMASTER_MAX_TENTHS, Rejection.NOT_TAUGHT);
        }

        // The effective ceiling is the stricter of the guild ceiling and the skill's own cap: a
        // Guildmaster may not push past 40.0, and must not push past a skill whose own maximum is
        // lower than that either.
        int capTenths = Math.min(GUILDMASTER_MAX_TENTHS, Math.max(0, skillMaxTenths));
        int headroomTenths = capTenths - Math.max(0, currentTenths);
        if (headroomTenths <= 0) {
            return rejected(skillSlug, currentTenths, capTenths, Rejection.ALREADY_AT_CAP);
        }

        // Integer division floors, which is the whole point: silver and copper cannot buy
        // training, because one gold is the smallest unit that buys anything.
        int availableGold = Math.max(0, availableCopper) / CoinConversion.COPPER_PER_GOLD;
        if (availableGold <= 0) {
            return rejected(skillSlug, currentTenths, capTenths, Rejection.NOT_ENOUGH_GOLD);
        }

        int purchasedTenths = Math.min(headroomTenths, availableGold);
        // One gold per tenth: the count of tenths IS the price.
        return new GuildTrainingQuote(
                skillSlug, currentTenths, capTenths, purchasedTenths, purchasedTenths, Rejection.NONE);
    }

    private static GuildTrainingQuote rejected(
            String skillSlug, int currentTenths, int capTenths, Rejection rejection
    ) {
        return new GuildTrainingQuote(skillSlug, currentTenths, capTenths, 0, 0, rejection);
    }
}
