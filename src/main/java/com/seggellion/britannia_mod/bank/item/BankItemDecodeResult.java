package com.seggellion.britannia_mod.bank.item;

import net.minecraft.world.item.ItemStack;

/**
 * Typed outcome of {@link BankItemCodec#deserialize}. Deliberately not an exception:
 * an unsupported schema version or a corrupt/truncated payload is an expected, recoverable
 * condition a caller must branch on, not a programming error.
 */
public sealed interface BankItemDecodeResult {
    record Success(ItemStack stack) implements BankItemDecodeResult {
    }

    record UnsupportedSchemaVersion(int foundVersion) implements BankItemDecodeResult {
    }

    record Corrupt(String reason) implements BankItemDecodeResult {
    }
}
