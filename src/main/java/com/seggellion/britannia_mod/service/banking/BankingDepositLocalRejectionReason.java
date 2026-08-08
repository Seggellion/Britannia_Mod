package com.seggellion.britannia_mod.service.banking;

/**
 * Why a deposit attempt was rejected locally, before any network call left this process
 * (protocol step 2, docs/banking_item_transfer.md's local-rejection philosophy: fail fast on
 * something already known-invalid rather than waste a round trip). The first three mirror
 * {@link com.seggellion.britannia_mod.bank.item.BankItemEligibility.IneligibilityReason}
 * exactly; the remaining two are this slice's own guards, not eligibility's concern.
 */
public enum BankingDepositLocalRejectionReason {
    /** The source slot has nothing in it -- not an eligibility question, but never a valid deposit. */
    EMPTY_SLOT,
    /** {@link com.seggellion.britannia_mod.bank.item.BankItemEligibility.IneligibilityReason#CURRENCY_MUST_USE_BALANCE_PROTOCOL}. */
    CURRENCY,
    /** {@link com.seggellion.britannia_mod.bank.item.BankItemEligibility.IneligibilityReason#QUEST_BOUND}. */
    QUEST_BOUND,
    /** {@link com.seggellion.britannia_mod.bank.item.BankItemEligibility.IneligibilityReason#UNSUPPORTED_ORIGIN}. */
    UNSUPPORTED_ORIGIN,
    /** Nested deeper than {@link com.seggellion.britannia_mod.bank.item.BankItemNesting#MAX_DEPTH}. */
    NESTING_TOO_DEEP,
    /** Serialized payload exceeds Rails' known 256 KiB limit (mirrors {@code PayloadValidator::MAX_PAYLOAD_BYTES}). */
    PAYLOAD_TOO_LARGE
}
