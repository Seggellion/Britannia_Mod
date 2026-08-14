package com.seggellion.britannia_mod.service;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * One row of the Rails-owned Economic NPC type registry (Vendor/Trader
 * Milestone 5): the Economic specialization of the generalized NPC type
 * foundation. Mirrors {@link ServiceNpcTypeDefinition}'s immutable-definition
 * role without the dialogue/skill members that are Service-specific.
 */
public record EconomicNpcTypeDefinition(
        String key,
        String displayName,
        String kind,
        String professionKey,
        @Nullable String minecraftEntityTypeKey,
        boolean active,
        boolean spawnable,
        long definitionRevision
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
}
