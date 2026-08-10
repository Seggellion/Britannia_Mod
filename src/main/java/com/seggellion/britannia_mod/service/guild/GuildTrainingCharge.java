package com.seggellion.britannia_mod.service.guild;

import com.seggellion.britannia_mod.economy.CoinConversion;

/**
 * Guildmaster milestone 5: the decision of what to charge, once Rails has committed.
 *
 * <p>Pure arithmetic, separated from the orchestration so the two rules that protect a player's
 * purse are testable without a server, a player or a socket. The orchestration around it is
 * threading and I/O; this is the part that can be wrong in a way that costs someone money.
 */
public final class GuildTrainingCharge {
    private GuildTrainingCharge() {
    }

    public enum Decision {
        /** Charge exactly {@link Plan#copper()}. */
        CHARGE,
        /**
         * Do not charge. The player's coins changed between the quote and the commit, so the
         * amount Rails was told about is no longer what they are carrying.
         */
        ABANDON
    }

    public record Plan(Decision decision, int gold, int copper) {
        public boolean charge() {
            return decision == Decision.CHARGE;
        }
    }

    private static final Plan ABANDON = new Plan(Decision.ABANDON, 0, 0);

    /**
     * @param goldCharged     what Rails says it granted, in gold — never what was requested. Rails
     *                        clamps to the real headroom, so a player whose skill moved between
     *                        quote and commit is charged for what they actually received.
     * @param availableCopper the player's coins re-counted <em>now</em>, not at quote time
     */
    public static Plan decide(int goldCharged, int availableCopper) {
        if (goldCharged <= 0) return ABANDON;

        int copper = goldCharged * CoinConversion.COPPER_PER_GOLD;
        // Re-counted immediately before the deduction, because an HTTP round trip happened since
        // the quote and the player can have spent, dropped or banked the gold it was priced
        // against. Charging a purse that no longer holds it would take whatever is left instead.
        if (availableCopper < copper) return ABANDON;

        return new Plan(Decision.CHARGE, goldCharged, copper);
    }
}
