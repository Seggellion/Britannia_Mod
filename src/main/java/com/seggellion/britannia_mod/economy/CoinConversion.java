package com.seggellion.britannia_mod.economy;

/**
 * The single authoritative gold/silver/copper conversion source for this mod's coin economy.
 *
 * <p>Before this class existed, the canonical 1 gold = 100 silver = 10,000 copper ratio was
 * reimplemented inline wherever coin math was needed. Confirmed during Milestone 11 recon:
 * {@link MerchantEconomyService}'s {@code countCoins}/{@code giveChange} hardcoded {@code 10000}
 * and {@code 100} directly, and {@link ServerEconomyService} was separately re-checked and found
 * to carry no independent ratio math of its own to migrate -- it only ever applies whatever
 * discrete gold/silver/copper amounts Rails' own payout response already specifies. ADR-012
 * (see {@code docs/ultimacraft_banking_service_npc_compatibility_map.md}) requires exactly one
 * such centralized source before cheque redemption (Milestone 11) can safely convert a single
 * authoritative value into a coin mix; this class is that source.
 *
 * <p>This class does not change the ratio's real value. Its constants were cross-checked
 * directly against {@link MerchantEconomyService}'s pre-existing {@code countCoins}/{@code
 * giveChange} literals (the real {@code 10000}/{@code 100} values already in the shipped code)
 * before this class existed, not assumed from memory of this program's own prior descriptions
 * of the ratio.
 *
 * <h2>Copper is the canonical smallest unit</h2>
 * Every value this class accepts or returns as a single {@code int} total is expressed in
 * copper -- the same convention {@code MerchantEconomyService#reserveCoins}/{@code countCoins}
 * already used before this class existed. Gold and silver are always whole multiples of copper,
 * so a value expressed in copper can never have a fractional remainder left over after
 * conversion -- see {@link #toCoins} for the precise, explicit policy this class follows
 * regardless.
 *
 * <h2>Rounding/remainder policy</h2>
 * {@link #toCoins} converts using a greedy, largest-denomination-first split: as many gold
 * coins as fit, then as many silver coins as fit in what remains, then whatever is left over
 * expressed as copper coins. Because gold and silver are whole multiples of copper, this split
 * is always exact and lossless -- there is no remainder that cannot be expressed as whole
 * copper coins, and this class never rounds, truncates, or drops value. This is stated
 * explicitly, per this slice's own requirement, even though the canonical-copper-unit
 * convention above makes an inexact remainder structurally impossible for any valid
 * non-negative input.
 */
public final class CoinConversion {
    /** Copper coins in one silver coin -- 1 silver = 100 copper. */
    public static final int COPPER_PER_SILVER = 100;

    /** Silver coins in one gold coin -- 1 gold = 100 silver. */
    public static final int SILVER_PER_GOLD = 100;

    /**
     * Copper coins in one gold coin -- 1 gold = 10,000 copper. Cross-checked directly against
     * {@code MerchantEconomyService}'s pre-existing {@code countCoins}/{@code giveChange}
     * literals ({@code 10000} for gold, {@code 100} for silver) before this class existed.
     */
    public static final int COPPER_PER_GOLD = COPPER_PER_SILVER * SILVER_PER_GOLD;

    private CoinConversion() {
    }

    /**
     * An immutable, non-negative gold/silver/copper coin count, as produced by {@link #toCoins}
     * or accepted by {@link #toCopper}.
     */
    public record CoinCounts(int gold, int silver, int copper) {
        public CoinCounts {
            if (gold < 0 || silver < 0 || copper < 0) {
                throw new IllegalArgumentException(
                    "coin counts must not be negative: gold=" + gold + " silver=" + silver + " copper=" + copper);
            }
        }
    }

    /**
     * Converts {@code totalCopper} (a value already expressed in the canonical smallest unit)
     * into the coin counts that represent it, using the greedy largest-denomination-first split
     * documented on the class itself. Exact and lossless for every valid input:
     * {@code toCopper(toCoins(totalCopper))} always equals {@code totalCopper}.
     *
     * @throws IllegalArgumentException if {@code totalCopper} is negative
     */
    public static CoinCounts toCoins(int totalCopper) {
        if (totalCopper < 0) {
            throw new IllegalArgumentException("totalCopper must not be negative: " + totalCopper);
        }
        int remaining = totalCopper;
        int gold = remaining / COPPER_PER_GOLD;
        remaining %= COPPER_PER_GOLD;
        int silver = remaining / COPPER_PER_SILVER;
        remaining %= COPPER_PER_SILVER;
        return new CoinCounts(gold, silver, remaining);
    }

    /**
     * Converts discrete gold/silver/copper coin counts back into a single total value expressed
     * in copper -- the reverse of {@link #toCoins}, provided for symmetry and future reuse (for
     * example, summing a player's real coin-item stacks back into one comparable value).
     *
     * @throws IllegalArgumentException if any component is negative
     * @throws ArithmeticException      if the combined total overflows a 32-bit int; this class
     *                                   never silently wraps a value
     */
    public static int toCopper(int gold, int silver, int copper) {
        if (gold < 0 || silver < 0 || copper < 0) {
            throw new IllegalArgumentException(
                "coin counts must not be negative: gold=" + gold + " silver=" + silver + " copper=" + copper);
        }
        int total = Math.multiplyExact(gold, COPPER_PER_GOLD);
        total = Math.addExact(total, Math.multiplyExact(silver, COPPER_PER_SILVER));
        return Math.addExact(total, copper);
    }

    /** Convenience overload accepting an already-constructed {@link CoinCounts}. */
    public static int toCopper(CoinCounts coins) {
        return toCopper(coins.gold(), coins.silver(), coins.copper());
    }
}
