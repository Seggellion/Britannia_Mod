package com.seggellion.britannia_mod.service;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Which commodities one Rails-owned economic NPC type will buy — the mod's read-only mirror of
 * {@code economic_npc_types.accepted_commodities} (Trader Commodity Authority, Milestone 5).
 *
 * <p>Rails owns this policy and enforces it at settlement. This copy exists only so the quote a
 * player is shown matches the sale that follows it: here the policy is DATA, never authority. A
 * mod that reads it wrongly can only offer MORE than Rails will honour, which is precisely the
 * confusion this project exists to remove — a trader appearing to want something that is then
 * refused when the sale is confirmed.
 *
 * <p>An EMPTY {@code entries} list means ACCEPTS NOTHING (Decision 4, fail closed). It never means
 * "unrestricted" and never means "not configured". The policy being absent altogether is a
 * different condition entirely and is carried as a {@code null}
 * {@link EconomicNpcTypeDefinition#acceptedCommodities()} — see that accessor for why the two must
 * not be conflated.
 *
 * <p>Entry ORDER is contractual and is preserved end to end. The Salvage policy's two entries
 * differ only by subcategory, so treating the list as unordered would move a
 * {@code commodityKeys} allow-list onto the wrong scope. Object KEY order is not a contract and is
 * not relied on anywhere: members are read by name.
 */
public record AcceptedCommodityPolicy(List<Entry> entries) {

    public AcceptedCommodityPolicy {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    /**
     * One accepted scope: a whole category, optionally narrowed to some of its subcategories, and
     * optionally narrowed again to named commodities.
     *
     * <p>A {@code null} array means "no restriction at this level" — absent {@code subcategories}
     * is the WHOLE category, absent {@code commodityKeys} is every commodity in this entry's
     * scope. A present-but-empty array is rejected rather than treated as either extreme: the
     * contract says to use absence, and guessing which extreme was meant is how a fail-closed
     * policy silently becomes a fail-open one.
     */
    public record Entry(String category,
                        @Nullable List<String> subcategories,
                        @Nullable List<String> commodityKeys) {
        public Entry {
            Objects.requireNonNull(category, "category");
            if (category.isBlank()) throw new IllegalArgumentException("category must not be blank");
            subcategories = frozen(subcategories, "subcategories");
            commodityKeys = frozen(commodityKeys, "commodityKeys");
        }

        @Nullable
        private static List<String> frozen(@Nullable List<String> values, String member) {
            if (values == null) return null;
            if (values.isEmpty()) throw new IllegalArgumentException(member + " must be absent, not empty");
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException(member + " must not contain a blank value");
                }
            }
            return List.copyOf(values);
        }
    }

    /** Accepts nothing. The fail-closed value, and a legitimate one Rails really does emit. */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Whether an item the player is offering falls inside any accepted scope.
     *
     * @param category    the described item's {@code category}; blank or absent is never accepted,
     *                    because {@code describeSaleItem} leaves it off anything it could not
     *                    classify and Rails would have no way to price such a row
     * @param subcategory the described item's {@code subcategory}
     * @param itemName    the described item's {@code item_name} — deliberately NOT the mod's
     *                    {@code commodity_key}. {@code commodityKeys} is specified against
     *                    {@code city_commodities.item_name}, the same field Rails'
     *                    {@code BuybackValuation.find_commodity} resolves on, so matching the same
     *                    field here keeps the offer and the settlement in agreement instead of
     *                    adding a second resolution path.
     */
    public boolean accepts(@Nullable String category, @Nullable String subcategory, @Nullable String itemName) {
        if (category == null || category.isBlank()) return false;
        for (Entry entry : entries) {
            if (!entry.category().equalsIgnoreCase(category)) continue;
            if (entry.subcategories() != null && !containsIgnoreCase(entry.subcategories(), subcategory)) continue;
            if (entry.commodityKeys() != null && !containsIgnoreCase(entry.commodityKeys(), itemName)) continue;
            return true;
        }
        return false;
    }

    /** Exact, case-insensitive, as the contract specifies the comparison. */
    private static boolean containsIgnoreCase(List<String> values, @Nullable String candidate) {
        if (candidate == null || candidate.isBlank()) return false;
        for (String value : values) {
            if (value.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }
}
