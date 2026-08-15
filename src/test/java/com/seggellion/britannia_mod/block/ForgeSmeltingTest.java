package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The forge's payout rule, including the Bronze alloy, driven without a world. */
class ForgeSmeltingTest {

    private static Map<String, Integer> purity(Object... pairs) {
        Map<String, Integer> map = new HashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            map.put((String) pairs[index], (Integer) pairs[index + 1]);
        }
        return map;
    }

    private static int ingotsOf(List<ForgeSmelting.Payout> payouts, String metal) {
        return payouts.stream()
                .filter(payout -> payout.metal().equals(metal))
                .mapToInt(ForgeSmelting.Payout::ingots)
                .sum();
    }

    @Test
    void oneMetalAloneStillYieldsThatMetal() {
        Map<String, Integer> held = purity("tin ore", 6);
        List<ForgeSmelting.Payout> payouts = ForgeSmelting.settle(held);

        assertEquals(2, ingotsOf(payouts, "tin"));
        assertEquals(0, ingotsOf(payouts, ForgeSmelting.BRONZE), "no copper present, so no alloy");
        assertEquals(0, held.get("tin ore"), "the consumed purity is gone");
    }

    /** The owner's rule: tin and copper worked together become bronze. */
    @Test
    void tinAndCopperTogetherAlloyIntoBronze() {
        Map<String, Integer> held = purity("tin ore", 3, "copper ore", 3);
        List<ForgeSmelting.Payout> payouts = ForgeSmelting.settle(held);

        assertEquals(2, ingotsOf(payouts, ForgeSmelting.BRONZE));
        assertEquals(0, ingotsOf(payouts, "tin"), "tin was consumed by the alloy");
        assertEquals(0, ingotsOf(payouts, "copper"), "copper was consumed by the alloy");
        assertEquals(0, held.get("tin ore"));
        assertEquals(0, held.get("copper ore"));
    }

    /** Six purity is two ingots on either path, so alloying is neither a tax nor a duplication. */
    @Test
    void throughputIsIdenticalOnBothPaths() {
        assertEquals(2, ingotsOf(ForgeSmelting.settle(purity("copper ore", 6)), "copper"));
        assertEquals(2, ingotsOf(
                ForgeSmelting.settle(purity("tin ore", 3, "copper ore", 3)), ForgeSmelting.BRONZE));
    }

    @Test
    void theAlloyIsSettledBeforeSingleMetalPayouts() {
        // Six of each would otherwise be two tin plus two copper; together they are bronze.
        Map<String, Integer> held = purity("tin ore", 6, "copper ore", 6);
        List<ForgeSmelting.Payout> payouts = ForgeSmelting.settle(held);

        assertEquals(4, ingotsOf(payouts, ForgeSmelting.BRONZE), "two batches of bronze");
        assertEquals(0, ingotsOf(payouts, "tin"));
        assertEquals(0, ingotsOf(payouts, "copper"));
    }

    @Test
    void leftoverPurityStaysInTheForge() {
        Map<String, Integer> held = purity("tin ore", 5, "copper ore", 4);
        List<ForgeSmelting.Payout> payouts = ForgeSmelting.settle(held);

        assertEquals(2, ingotsOf(payouts, ForgeSmelting.BRONZE), "one batch: three of each");
        assertEquals(2, held.get("tin ore"), "the odd tin waits for more copper");
        assertEquals(1, held.get("copper ore"));
    }

    /** A metal alloying must never consume a bystander metal sharing the forge. */
    @Test
    void otherMetalsAreUntouchedByTheAlloy() {
        Map<String, Integer> held = purity("tin ore", 3, "copper ore", 3, "silver ore", 4);
        List<ForgeSmelting.Payout> payouts = ForgeSmelting.settle(held);

        assertEquals(2, ingotsOf(payouts, ForgeSmelting.BRONZE));
        assertEquals(4, held.get("silver ore"), "silver was neither consumed nor paid out");
        assertEquals(0, ingotsOf(payouts, "silver"));
    }

    @Test
    void nothingIsOwedBelowAFullBatch() {
        Map<String, Integer> held = purity("tin ore", 2, "copper ore", 2, "verite ore", 5);
        assertTrue(ForgeSmelting.settle(held).isEmpty());
        assertEquals(2, held.get("tin ore"));
        assertEquals(5, held.get("verite ore"));
    }

    @Test
    void oreNamesReduceToTheMetalTheRegistryKnows() {
        assertEquals("shadow iron", ForgeSmelting.metalName("shadow iron ore"));
        assertEquals("tin", ForgeSmelting.metalName("tin ore"));
        assertEquals("", ForgeSmelting.metalName(null));
    }
}
