package com.seggellion.britannia_mod.service.banking;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * The {@code account} object from a successful {@code OPENED} response, field-for-field
 * matching docs/banking_open.md. {@code weightLimit} stays an int and {@code currentWeight}
 * a double to match Rails' own wire representation decision (native JSON integer/number,
 * not string-encoded) for the exact same reason Rails chose it: consistency with the
 * actual column types, nothing invented on this side.
 */
public record BankingOpenAccount(
        UUID publicId,
        String bankingMode,
        @Nullable UUID cityPublicId,
        int weightLimit,
        double currentWeight,
        int goldBalance,
        int silverBalance,
        int copperBalance,
        long revision
) {
    public BankingOpenAccount {
        Objects.requireNonNull(publicId, "publicId");
        Objects.requireNonNull(bankingMode, "bankingMode");
    }
}
