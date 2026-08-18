package com.seggellion.britannia_mod.economy;

import java.util.Locale;
import java.util.Set;

/**
 * LEGACY FALLBACK — which commodity categories a Trader will quote a buyback for, guessed from
 * its name.
 *
 * <p>Superseded by the Rails-owned {@code accepted_commodities} policy (Trader Commodity
 * Authority, Milestone 5). This table is consulted from exactly one place,
 * {@code TraderCommodityFilter.resolve}, and only when a type's registry entry carries NO
 * {@code accepted_commodities} member at all — which means one thing: a Rails predating that
 * project's Milestone 4. A policy that is present but EMPTY is a real "accepts nothing" and must
 * never reach here.
 *
 * <p>REMOVAL CONDITION: delete this class, its test, and the {@code LEGACY_FALLBACK} branch once
 * no supported Rails predates Trader Commodity Authority M4, at which point the member is always
 * present and the branch is dead. That deletion is Milestone 7, and nothing else should be left
 * depending on this by then.
 *
 * <p>Why it exists at all: Rails prices a sell row from the city's commodity table, and
 * {@code npc_buy_enabled} is a per-commodity-per-city flag rather than a per-NPC one — so an
 * older Rails would happily quote a Fish Trader for a stack of logs if the mod offered them. The
 * legacy role handlers drew that line client-of-Rails side (see
 * {@code TraderRoleHandler#collectSellableInventory}), and this mirrors their selection exactly.
 * Guessing a policy from a name is precisely the arrangement the Authority project replaces:
 * substring matching cannot express a subcategory or a commodity allow-list, and it cannot be
 * corrected without a mod release.
 *
 * <p>Matching is by substring, on the Rails type key first ({@code fish_trader}) and the role
 * title second ("Fish Trader"), because those are the two spellings of the same identity and both
 * carry the discriminating word.
 */
final class TraderBuybackCategories {
    /** Sentinel for the generic Trader: no category restriction, matching {@code collectGenericInventory}. */
    static final Set<String> ANY = Set.of();

    private TraderBuybackCategories() {
    }

    static Set<String> forTrader(String economicTypeKey, String role) {
        Set<String> byKey = match(economicTypeKey);
        if (byKey != null) return byKey;
        Set<String> byRole = match(role);
        return byRole != null ? byRole : ANY;
    }

    private static Set<String> match(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.toLowerCase(Locale.ROOT);

        // Ordered as TraderRoleHandlers.create dispatches: the two specialized handlers first,
        // so "salvage" and "alcohol" can never be swallowed by a generic substring below.
        if (value.contains("salvage")) return Set.of("metal");
        if (value.contains("alcohol") || value.contains("wine") || value.contains("vintner")) return Set.of("alcohol");

        if (value.contains("wood") || value.contains("lumber")) return Set.of("wood");
        if (value.contains("fish")) return Set.of("fish");
        if (value.contains("ore") || value.contains("metal") || value.contains("miner")) return Set.of("ore");
        if (value.contains("stone")) return Set.of("stone");
        if (value.contains("grain")) return Set.of("grain");
        if (value.contains("produce") || value.contains("costermonger")) return Set.of("produce");
        if (value.contains("fur") || value.contains("leather")) return Set.of("fur", "leather");
        if (value.contains("hunter") || value.contains("butcher") || value.contains("meat")) return Set.of("meat");
        return null;
    }

    static boolean accepts(Set<String> categories, String category) {
        if (category == null || category.isBlank()) return false;
        return categories.isEmpty() || categories.contains(category);
    }
}
