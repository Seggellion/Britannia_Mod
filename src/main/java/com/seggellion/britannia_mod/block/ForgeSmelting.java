package com.seggellion.britannia_mod.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a forge owes its user, given the ore purity it is currently holding.
 *
 * <p>Deliberately free of Minecraft types so the rule — including the Bronze alloy — is unit
 * testable without a world, and shared by both the small and large forge instead of being written
 * twice and drifting.
 *
 * <p><b>Bronze.</b> Tin and Copper worked in the same forge at the same time alloy into Bronze
 * rather than yielding their own ingots. It is the one metal that is never mined: there is no
 * Bronze ore, which is exactly the model the project already had — Rails prices a bronze ingot but
 * has never priced bronze ore. The alloy is settled before the single-metal payout, so mixing is
 * what produces Bronze and working one metal alone still produces that metal.
 *
 * <p>Throughput is deliberately identical on both paths: six purity always becomes two ingots,
 * whether that is six of one metal or three Tin plus three Copper. Alloying costs a miner nothing
 * and gains them nothing but the alloy itself.
 */
public final class ForgeSmelting {

    /** Purity that must accumulate before a single metal yields ingots. */
    public static final int PURITY_PER_BATCH = 6;

    /** Ingots produced per completed batch, on either path. */
    public static final int INGOTS_PER_BATCH = 2;

    /** Purity of each constituent consumed per Bronze batch: three Tin plus three Copper. */
    public static final int ALLOY_PURITY_EACH = 3;

    /** Keys are the lowercase ore names the mined item carries, e.g. {@code "tin ore"}. */
    public static final String TIN_ORE = "tin ore";
    public static final String COPPER_ORE = "copper ore";
    public static final String BRONZE = "bronze";

    private ForgeSmelting() {
    }

    /** One thing the forge should eject: a metal name and how many ingots of it. */
    public record Payout(String metal, int ingots) {
    }

    /**
     * Settles everything the forge currently owes, <em>mutating</em> the supplied purity map to
     * leave only the remainder that has not yet become ingots.
     */
    public static List<Payout> settle(Map<String, Integer> purityByOreType) {
        List<Payout> payouts = new ArrayList<>();

        int alloyBatches = Math.min(
                purityByOreType.getOrDefault(TIN_ORE, 0) / ALLOY_PURITY_EACH,
                purityByOreType.getOrDefault(COPPER_ORE, 0) / ALLOY_PURITY_EACH);
        if (alloyBatches > 0) {
            int consumed = alloyBatches * ALLOY_PURITY_EACH;
            purityByOreType.merge(TIN_ORE, -consumed, Integer::sum);
            purityByOreType.merge(COPPER_ORE, -consumed, Integer::sum);
            payouts.add(new Payout(BRONZE, alloyBatches * INGOTS_PER_BATCH));
        }

        for (Map.Entry<String, Integer> entry : purityByOreType.entrySet()) {
            int batches = entry.getValue() / PURITY_PER_BATCH;
            if (batches > 0) {
                entry.setValue(entry.getValue() % PURITY_PER_BATCH);
                payouts.add(new Payout(metalName(entry.getKey()), batches * INGOTS_PER_BATCH));
            }
        }
        return payouts;
    }

    /** {@code "shadow iron ore"} to {@code "shadow iron"}, the name the metal registry uses. */
    public static String metalName(String oreType) {
        return oreType == null ? "" : oreType.replace(" ore", "");
    }
}
