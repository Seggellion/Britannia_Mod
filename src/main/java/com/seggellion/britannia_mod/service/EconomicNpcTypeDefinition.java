package com.seggellion.britannia_mod.service;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * One row of the Rails-owned Economic NPC type registry (Vendor/Trader
 * Milestone 5): the Economic specialization of the generalized NPC type
 * foundation. Mirrors {@link ServiceNpcTypeDefinition}'s immutable-definition
 * role without the dialogue/skill members that are Service-specific.
 *
 * @param acceptedCommodities which commodities this type buys, or {@code null} when the registry
 *                            entry carried no {@code accepted_commodities} member at all. That
 *                            absence means ONE thing — a Rails predating Trader Commodity
 *                            Authority M4 — and since M7 it fails CLOSED: the trader offers
 *                            nothing and reports a configuration fault, rather than having its
 *                            trade guessed from its name. An EMPTY policy is a different case
 *                            with the same outcome: a real, deliberate "accepts nothing", emitted
 *                            for every vendor and for the parked traders. The two are kept
 *                            distinct because they call for different repairs.
 */
public record EconomicNpcTypeDefinition(
        String key,
        String displayName,
        String kind,
        String professionKey,
        @Nullable String minecraftEntityTypeKey,
        boolean active,
        boolean spawnable,
        long definitionRevision,
        @Nullable AcceptedCommodityPolicy acceptedCommodities
) {
    public EconomicNpcTypeDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(professionKey, "professionKey");
        if (!kind.equals("vendor") && !kind.equals("trader")) {
            throw new IllegalArgumentException("kind must be vendor or trader");
        }
        if (definitionRevision < 1L) {
            throw new IllegalArgumentException("definitionRevision must be >= 1");
        }
    }

    /**
     * A definition whose accepted-commodity policy is ABSENT — the pre-M4 Rails shape. Kept so
     * callers that predate the policy, and tests with no interest in it, state that absence
     * explicitly rather than by passing a bare {@code null} whose meaning is easy to misread.
     */
    public EconomicNpcTypeDefinition(String key, String displayName, String kind, String professionKey,
                                     @Nullable String minecraftEntityTypeKey, boolean active,
                                     boolean spawnable, long definitionRevision) {
        this(key, displayName, kind, professionKey, minecraftEntityTypeKey, active, spawnable,
                definitionRevision, null);
    }
}
