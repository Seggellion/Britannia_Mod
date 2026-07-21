package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * One row of {@code banking/open}'s real {@code bank_items} envelope (Milestone 9 Rails Slice
 * 1) -- every {@code available} item the account currently holds, list-view only. Deliberately
 * minimal: the list endpoint never includes the item's {@code payload} (only {@code
 * banking/withdrawal/prepare} does, per docs/banking_item_transfer.md), so there is no item
 * name/icon to show here -- only what Rails actually sends for a row: identity ({@code
 * publicId}) and {@code weight}. {@link BankingWithdrawalPrepareRequest} is what a player's
 * selection here eventually turns into, carrying nothing more than this same {@code publicId}.
 */
public record BankItemSummary(UUID publicId, double weight) {
    public BankItemSummary {
        Objects.requireNonNull(publicId, "publicId");
    }
}
