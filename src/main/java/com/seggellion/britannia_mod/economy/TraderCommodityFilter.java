package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload.Notice;
import com.seggellion.britannia_mod.service.AcceptedCommodityPolicy;
import com.seggellion.britannia_mod.service.EconomicNpcTypeDefinition;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Which of a player's items one Trader may be offered — Trader Commodity Authority.
 *
 * <p>Rails owns the answer, and since Milestone 7 it is the ONLY source of it. This resolves one
 * of four named states per trader, once, before any item is looked at, so the reason an offer came
 * out narrow is always a named condition rather than an accident of control flow:
 *
 * <ul>
 *   <li>{@link Source#POLICY} — a synced {@code accepted_commodities} policy drives the filter.
 *   <li>{@link Source#EMPTY_POLICY} — a synced policy that accepts nothing. A real, deliberate
 *       value, and for a trader a player can walk up to it is a configuration FAULT: the contract
 *       requires an active or spawnable trader to carry a valid non-empty policy.
 *   <li>{@link Source#MISSING} — no registry entry at all. Rails DROPS a type whose stored policy
 *       is malformed rather than publishing it broken, so this is a fault too.
 *   <li>{@link Source#ABSENT} — a registry entry carrying no policy member, which means one thing:
 *       a Rails predating M4.
 * </ul>
 *
 * <p>Three of the four fail closed and say so. That is the whole of M7: until it, ABSENT reached a
 * table that GUESSED a profession's trade from substrings in its name, and M6 measured what that
 * guess was worth — for five of fifteen professions the table recognised nothing and returned its
 * ANY sentinel, offering everything classifiable. A fallback that is wrong in a third of cases is
 * not a safety net; it is the quote-disagrees-with-settlement bug this project exists to remove,
 * wearing the costume of caution. Nothing here infers policy from a name any more.
 *
 * <p>ABSENT is kept distinct from MISSING rather than folded into it because the two call for
 * different repairs — one is a Rails too old to deploy against, the other a type Rails refused to
 * publish — and a log line that cannot tell them apart sends the reader to the wrong repository.
 */
final class TraderCommodityFilter {
    private static final Logger LOGGER = LogUtils.getLogger();

    enum Source { POLICY, EMPTY_POLICY, MISSING, ABSENT }

    private final Source source;
    @Nullable private final AcceptedCommodityPolicy policy;

    private TraderCommodityFilter(Source source, @Nullable AcceptedCommodityPolicy policy) {
        this.source = source;
        this.policy = policy;
    }

    static TraderCommodityFilter resolve(@Nullable EconomicNpcTypeDefinition definition,
                                         @Nullable String economicTypeKey) {
        if (definition == null) {
            LOGGER.warn("Economic type {} has no registry entry; offering nothing rather than"
                    + " guessing a policy Rails would refuse at settlement", economicTypeKey);
            return new TraderCommodityFilter(Source.MISSING, null);
        }
        AcceptedCommodityPolicy synced = definition.acceptedCommodities();
        if (synced == null) {
            LOGGER.warn("Economic type {} carries no accepted_commodities member; offering nothing."
                    + " Rails has emitted it for every type since Trader Commodity Authority M4, so"
                    + " this shard is running an older Rails and needs upgrading.", economicTypeKey);
            return new TraderCommodityFilter(Source.ABSENT, null);
        }
        if (synced.isEmpty()) {
            LOGGER.warn("Economic type {} accepts no commodities; an active or spawnable trader is"
                    + " required to carry a non-empty policy", economicTypeKey);
            return new TraderCommodityFilter(Source.EMPTY_POLICY, synced);
        }
        return new TraderCommodityFilter(Source.POLICY, synced);
    }

    Source source() {
        return source;
    }

    /**
     * The refusal to speak instead of quoting, or {@code null} to go on and quote.
     *
     * <p>All three fault states report {@code trader_policy_missing} rather than staying silent.
     * That code exists so a configuration failure is never mistaken for a trader who simply is not
     * interested — the exact confusion of the original bug.
     */
    @Nullable
    String refusalCode() {
        return source == Source.POLICY ? null : Notice.TRADER_POLICY_MISSING;
    }

    /**
     * Whether a described item may be put on the quote.
     *
     * <p>{@code category} is read in every case; the policy may additionally read
     * {@code subcategory} and {@code item_name}. {@code item_name} rather than the mod's own
     * {@code commodity_key} because {@code commodity_keys} is specified against
     * {@code city_commodities.item_name}, the field Rails resolves the row on — so the mod narrows
     * on exactly what Rails narrows on, and cannot be stricter than the settlement it previews.
     */
    boolean accepts(JsonObject describedItem) {
        if (source != Source.POLICY) return false;
        return policy.accepts(member(describedItem, "category"),
                member(describedItem, "subcategory"), member(describedItem, "item_name"));
    }

    @Nullable
    private static String member(JsonObject item, String name) {
        return item.has(name) && item.get(name).isJsonPrimitive() ? item.get(name).getAsString() : null;
    }
}
