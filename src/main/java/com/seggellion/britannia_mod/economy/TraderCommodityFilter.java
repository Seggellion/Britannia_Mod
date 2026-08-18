package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload.Notice;
import com.seggellion.britannia_mod.service.AcceptedCommodityPolicy;
import com.seggellion.britannia_mod.service.EconomicNpcTypeDefinition;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Which of a player's items one Trader may be offered — Trader Commodity Authority, Milestone 5.
 *
 * <p>Rails owns the answer. This resolves ONE of four states for a single trader, once, before
 * any item is looked at, so that the reason an offer is narrow is always a named condition rather
 * than an accident of control flow:
 *
 * <ul>
 *   <li>{@link Source#POLICY} — a synced {@code accepted_commodities} policy drives the filter.
 *   <li>{@link Source#EMPTY_POLICY} — a synced policy that accepts nothing. A real, deliberate
 *       value, and for a trader a player can walk up to it is a configuration FAULT: the contract
 *       requires an active or spawnable trader to carry a valid non-empty policy.
 *   <li>{@link Source#MISSING} — no registry entry at all. Rails DROPS a type whose stored policy
 *       is malformed rather than publishing it broken, so this is a fault too, not an old Rails.
 *   <li>{@link Source#LEGACY_FALLBACK} — the {@code accepted_commodities} MEMBER was absent, which
 *       means one thing only: a Rails predating M4.
 * </ul>
 *
 * <p>The legacy table is consulted in that last case and NOWHERE else. A present-but-empty policy
 * must never reach it: falling back there would hand a player exactly the categories the policy
 * exists to withhold, and Rails would refuse the sale at settlement anyway — a quote disagreeing
 * with its own settlement is the failure this project was opened to remove.
 */
final class TraderCommodityFilter {
    private static final Logger LOGGER = LogUtils.getLogger();

    enum Source { POLICY, EMPTY_POLICY, MISSING, LEGACY_FALLBACK }

    private final Source source;
    @Nullable private final AcceptedCommodityPolicy policy;
    @Nullable private final Set<String> legacyCategories;

    private TraderCommodityFilter(Source source, @Nullable AcceptedCommodityPolicy policy,
                                  @Nullable Set<String> legacyCategories) {
        this.source = source;
        this.policy = policy;
        this.legacyCategories = legacyCategories;
    }

    static TraderCommodityFilter resolve(@Nullable EconomicNpcTypeDefinition definition,
                                         @Nullable String economicTypeKey, @Nullable String role) {
        if (definition == null) {
            LOGGER.warn("Economic type {} has no registry entry; offering nothing rather than guessing"
                    + " a policy Rails would refuse at settlement", economicTypeKey);
            return new TraderCommodityFilter(Source.MISSING, null, null);
        }
        AcceptedCommodityPolicy synced = definition.acceptedCommodities();
        if (synced == null) {
            // The ONLY fallback condition. TraderBuybackCategories is reached from here and from
            // no other branch, which is what makes "policy present, legacy table not consulted"
            // a property of the code rather than of the test that checks it.
            LOGGER.warn("Economic type {} carries no accepted_commodities; falling back to the legacy"
                    + " category table. Rails has emitted the member since Trader Commodity Authority"
                    + " M4, so this shard is running an older Rails.", economicTypeKey);
            return new TraderCommodityFilter(Source.LEGACY_FALLBACK, null,
                    TraderBuybackCategories.forTrader(economicTypeKey, role));
        }
        if (synced.isEmpty()) {
            LOGGER.warn("Economic type {} accepts no commodities; an active or spawnable trader is"
                    + " required to carry a non-empty policy", economicTypeKey);
            return new TraderCommodityFilter(Source.EMPTY_POLICY, synced, null);
        }
        return new TraderCommodityFilter(Source.POLICY, synced, null);
    }

    Source source() {
        return source;
    }

    /**
     * The refusal to speak instead of quoting, or {@code null} to go on and quote.
     *
     * <p>Both fault states report {@code trader_policy_missing} rather than staying silent. That
     * code exists so a configuration failure is never mistaken for a trader who simply is not
     * interested — the exact confusion of the original bug.
     */
    @Nullable
    String refusalCode() {
        return source == Source.EMPTY_POLICY || source == Source.MISSING
                ? Notice.TRADER_POLICY_MISSING : null;
    }

    /**
     * Whether a described item may be put on the quote.
     *
     * <p>{@code category} is read from the item in every case; the policy may additionally read
     * {@code subcategory} and {@code item_name}. {@code item_name} rather than the mod's own
     * {@code commodity_key} because {@code commodity_keys} is specified against
     * {@code city_commodities.item_name}, which is the field Rails resolves the row on — so the
     * mod narrows on exactly what Rails narrows on, and cannot be stricter than the settlement it
     * is previewing.
     */
    boolean accepts(JsonObject describedItem) {
        String category = member(describedItem, "category");
        return switch (source) {
            case POLICY -> policy.accepts(category, member(describedItem, "subcategory"),
                    member(describedItem, "item_name"));
            case LEGACY_FALLBACK -> TraderBuybackCategories.accepts(legacyCategories, category);
            case EMPTY_POLICY, MISSING -> false;
        };
    }

    @Nullable
    private static String member(JsonObject item, String name) {
        return item.has(name) && item.get(name).isJsonPrimitive() ? item.get(name).getAsString() : null;
    }
}
