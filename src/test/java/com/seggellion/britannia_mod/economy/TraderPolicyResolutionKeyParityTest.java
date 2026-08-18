package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.service.AcceptedCommodityPolicy;
import com.seggellion.britannia_mod.service.AcceptedCommodityPolicy.Entry;
import com.seggellion.britannia_mod.service.EconomicNpcTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trader Commodity Authority Milestone 6, the second half of the parity claim.
 *
 * <p>{@code TraderPolicyWireParityTest} proves a (category, subcategory, item_name) triple means
 * the same thing on both sides. It cannot prove the triple the mod DERIVES from an ItemStack is
 * the one Rails derives from the resolved {@code city_commodities} row — and that is the half the
 * known ingot defect lives in, so it is pinned separately rather than assumed.
 *
 * <p>THE ASYMMETRY. Rails matches {@code commodity_keys} against the RESOLVED ROW's
 * {@code item_name}, having resolved that row by {@code commodity_key.presence || item_name} (a
 * piped {@code commodity_key} is split into category|subcategory|name first). The mod matches
 * against the DESCRIBED ITEM's {@code item_name}. The two agree only while every item's
 * {@code commodity_key} is absent, equal to its {@code item_name}, or the piped form whose name
 * segment IS its {@code item_name}. Nothing in either codebase enforces that.
 *
 * <p>COVERAGE LIMIT, stated rather than glossed. The shapes below are TRANSCRIBED from
 * {@code ServerEconomyService.describeSaleItem}, not produced by calling it: that method reads
 * {@code DataComponentRegistry.WINE_DATA} unconditionally, so invoking it needs live mod
 * registries and cannot run in a unit test. This test therefore pins the RULE and its
 * consequences, and will NOT catch a future edit to {@code describeSaleItem} that changes a shape.
 * Closing that gap needs a GameTest, which would require widening that method's visibility; the
 * playbook records it as a prerequisite of the Salvage/ingot work rather than something M6 forced.
 */
class TraderPolicyResolutionKeyParityTest {

    /**
     * Every branch of {@code describeSaleItem}, as of this commit: (branch, commodity_key or null,
     * item_name).
     */
    private static final List<String[]> DESCRIBED_SHAPES = List.of(
            new String[] { "WeightedWoodItem", "oak", "oak" },
            new String[] { "WeightedFishItem", "cod", "cod" },
            new String[] { "PurityOreItem", "silver", "silver" },
            new String[] { "GradeStoneItem", "stone|igneous|granite", "granite" },
            new String[] { "classifyMappedCommodity", "wheat", "wheat" },
            new String[] { "ingot branch", null, "copper_ingots" },
            new String[] { "jewelry/sword salvage", null, "gold_ring" },
            new String[] { "unclassified", null, "diamond" });

    /**
     * The invariant the mod's {@code commodity_keys} matching silently depends on: whatever Rails
     * would resolve the row by is the same string the mod narrows on. Break it and a trader offers
     * goods the settlement then refuses, or hides goods it would have been paid for.
     */
    @Test
    void everyDescribedShapeResolvesUnderTheNameTheModNarrowsOn() {
        for (String[] shape : DESCRIBED_SHAPES) {
            JsonObject described = describedAs(shape[1], shape[2]);

            assertEquals(shape[2], railsResolutionName(described),
                    shape[0] + " sends commodity_key=" + shape[1] + " and item_name=" + shape[2]
                            + "; Rails would resolve the row by a different string, so the mod is"
                            + " narrowing on something Rails never matches against");
        }
    }

    /**
     * Rails' {@code find_commodity} name selection, transcribed. Executable prose, so the
     * dependency on the other repository is visible in this one rather than only in a reviewer's
     * memory of it.
     */
    private static String railsResolutionName(JsonObject described) {
        String commodityKey = string(described, "commodity_key");
        if (commodityKey == null || commodityKey.isEmpty()) return string(described, "item_name");
        int first = commodityKey.indexOf('|');
        if (first < 0) return commodityKey;
        int second = commodityKey.indexOf('|', first + 1);
        return second < 0 ? commodityKey : commodityKey.substring(second + 1);
    }

    /**
     * The known blocker, pinned as it behaves rather than as it is hoped to behave.
     *
     * <p>Contract section 6 records that ingot resolution misses: the payload sends the item path
     * where Rails seeds the bare metal name. Both sides refuse a copper ingot at the Salvage
     * Trader — Rails with {@code commodity_not_found} before the policy is consulted, the mod by
     * filtering it out of the offer. The OUTCOMES agree, which is why parity passes; but they
     * agree by both failing, and the allow-list is never exercised.
     */
    @Test
    void theIngotAllowListIsCorrectlyExpressedAndStillNotExercisable() {
        JsonObject copperIngot = ingot("copper_ingots", null);

        assertFalse(salvageFilter().accepts(copperIngot),
                "'copper_ingots' is not in the allow-list, so the mod refuses it");
        assertEquals("copper_ingots", railsResolutionName(copperIngot),
                "and Rails looks for a row named 'copper_ingots', not the seeded 'copper'");

        // The allow-list itself is sound: it is the identifier that is wrong, not the policy.
        assertTrue(salvageFilter().accepts(ingot("copper", null)),
                "given the seeded name the same policy accepts, so the defect is resolution --"
                        + " do not weaken the policy to make Salvage look functional");
    }

    /**
     * The trap that would split the two sides the moment the ingot defect is fixed carelessly.
     * Adding {@code commodity_key: "copper"} while leaving {@code item_name} as the path makes
     * Rails resolve and ACCEPT while the mod, still narrowing on item_name, keeps REFUSING — a
     * quote disagreeing with its own settlement, which is the failure this project exists to end.
     */
    @Test
    void fixingIngotsByAddingOnlyACommodityKeyWouldSplitTheTwoSides() {
        JsonObject halfFixed = ingot("copper_ingots", "copper");

        assertEquals("copper", railsResolutionName(halfFixed),
                "Rails would now resolve the seeded row and accept");
        assertFalse(salvageFilter().accepts(halfFixed),
                "the mod would still refuse -- the fix must set item_name too, or teach the filter"
                        + " Rails' commodity_key-then-item_name rule");
    }

    /** An item nothing could classify carries no category, so no trader may be offered it. */
    @Test
    void anUnclassifiableItemIsOfferedToNobody() {
        assertFalse(salvageFilter().accepts(describedAs(null, "diamond")));
    }

    private static JsonObject ingot(String itemName, String commodityKey) {
        JsonObject described = new JsonObject();
        described.addProperty("category", "metal");
        described.addProperty("subcategory", "ingots");
        described.addProperty("material", "copper");
        described.addProperty("item_name", itemName);
        if (commodityKey != null) described.addProperty("commodity_key", commodityKey);
        return described;
    }

    private static JsonObject describedAs(String commodityKey, String itemName) {
        JsonObject described = new JsonObject();
        described.addProperty("item_name", itemName);
        if (commodityKey != null) described.addProperty("commodity_key", commodityKey);
        return described;
    }

    private static TraderCommodityFilter salvageFilter() {
        AcceptedCommodityPolicy policy = new AcceptedCommodityPolicy(List.of(
                new Entry("metal", List.of("salvage"), null),
                new Entry("metal", List.of("ingots"), List.of("copper", "silver", "gold"))));
        return TraderCommodityFilter.resolve(
                new EconomicNpcTypeDefinition("salvage_trader", "Salvage Trader", "trader", "smith",
                        "britannia_mod:salvage_trader", true, true, 1L, policy),
                "salvage_trader");
    }

    private static String string(JsonObject holder, String member) {
        return holder.has(member) && !holder.get(member).isJsonNull()
                ? holder.get(member).getAsString() : null;
    }
}
