package com.seggellion.britannia_mod.service.guild;

import com.seggellion.britannia_mod.economy.CoinConversion;

/**
 * The result of a completed (or refused) Guildmaster training purchase.
 *
 * <p>Guildmaster milestone 5. Carries what actually happened rather than what was quoted: on
 * success {@code chargedGold} is the amount really taken and {@code finalTenths} the value really
 * written, so the message shown to a player can never describe a transaction that did not occur.
 */
public record GuildTrainingOutcome(
        Status status,
        String skillSlug,
        int purchasedTenths,
        int chargedGold,
        int finalTenths,
        String detail
) {
    public enum Status {
        /** Rails committed the new value and the coins were taken. */
        TRAINED,
        /** Refused before anything moved: not taught, at cap, or not enough gold. */
        REFUSED,
        /** Rails refused or never answered. No coins were taken. */
        SERVICE_UNAVAILABLE,
        /**
         * The player's gold changed between quote and charge, so the charge was abandoned.
         *
         * <p>Not merely defensive. The quote is computed, then an HTTP round trip happens, and only
         * then are coins taken — during which the player can spend, drop or bank the gold the
         * quote was priced against. Re-counting immediately before the deduction is what stops a
         * player being charged for coins they no longer have.
         */
        FUNDS_CHANGED
    }

    public boolean trained() {
        return status == Status.TRAINED;
    }

    public int chargedCopper() {
        return chargedGold * CoinConversion.COPPER_PER_GOLD;
    }

    public static GuildTrainingOutcome refused(String skillSlug, String detail) {
        return new GuildTrainingOutcome(Status.REFUSED, skillSlug, 0, 0, 0, detail);
    }

    public static GuildTrainingOutcome unavailable(String skillSlug, String detail) {
        return new GuildTrainingOutcome(Status.SERVICE_UNAVAILABLE, skillSlug, 0, 0, 0, detail);
    }

    public static GuildTrainingOutcome fundsChanged(String skillSlug) {
        return new GuildTrainingOutcome(
                Status.FUNDS_CHANGED, skillSlug, 0, 0, 0, "funds changed before the charge");
    }

    public static GuildTrainingOutcome trained(String skillSlug, int purchasedTenths, int chargedGold, int finalTenths) {
        return new GuildTrainingOutcome(
                Status.TRAINED, skillSlug, purchasedTenths, chargedGold, finalTenths, null);
    }

    /** Diegetic, never a raw status name or HTTP code. */
    public String playerMessage() {
        return switch (status) {
            case TRAINED -> "The guildmaster nods. \"That will be " + chargedGold
                    + " gold.\" Thy skill is now " + GuildmasterProxyService.formatTenths(finalTenths) + ".";
            case REFUSED -> detail;
            case SERVICE_UNAVAILABLE ->
                    "The guildmaster is distracted and cannot teach thee just now. Thy gold is untouched.";
            case FUNDS_CHANGED ->
                    "The guildmaster counts thy purse again and frowns. Nothing was taken.";
        };
    }
}
