package com.seggellion.britannia_mod.service.banking;

import javax.annotation.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * One row of {@code banking/open}'s {@code bank_items} envelope -- every {@code available} item
 * the account currently holds, list-view only.
 *
 * <p>Milestone 18 adds {@code displayName} and {@code count}. Before them every row rendered as
 * the literal "Stored item", so a vault holding five different objects showed five
 * indistinguishable lines -- which is what a player reported as "my items disappeared". They had
 * not; they were simply unrecognisable.
 *
 * <p>Both are optional and independently so, because Rails sends them only when it has them: a
 * row deposited before item identity existed carries neither and never will, and a client that
 * predates Milestone 17 keeps creating more of them. {@link #describe()} is the single place that
 * decides what such a row looks like.
 *
 * <p>Still deliberately minimal beyond that. The list endpoint never includes the item's
 * {@code payload} (only {@code banking/withdrawal/prepare} does, per
 * docs/banking_item_transfer.md), so there is no stack to render an icon from -- that would be a
 * separate decision about the contract, not a drive-by here.
 */
public record BankItemSummary(UUID publicId, double weight, @Nullable String displayName, @Nullable Integer count) {

    /** What a row with no stored name has always read as. Unchanged, so nothing regresses. */
    public static final String FALLBACK_NAME = "Stored item";

    public BankItemSummary {
        Objects.requireNonNull(publicId, "publicId");
        // Absence is null, never a blank string: the two must not be distinguishable downstream,
        // and a blank name would render as an empty row rather than falling back.
        if (displayName != null && displayName.isBlank()) displayName = null;
        if (count != null && count < 1) count = null;
    }

    /** The pre-Milestone-18 shape, for callers that have no identity to carry. */
    public static BankItemSummary withoutIdentity(UUID publicId, double weight) {
        return new BankItemSummary(publicId, weight, null, null);
    }

    /**
     * What this item should be called in a list. A count above one is shown because a stack and a
     * single item must not render identically -- that is the same failure this milestone exists
     * to fix, just one level down. A count of exactly one adds nothing and is left off.
     */
    public String describe() {
        String name = displayName != null ? displayName : FALLBACK_NAME;
        return count != null && count > 1 ? name + " x" + count : name;
    }
}
