package com.seggellion.britannia_mod.service.banking;

/**
 * Why a withdrawal was aborted after Rails had already prepared/reserved the item, but before
 * (or without) ever giving it to the player -- Cancel was called in every case, releasing
 * Rails' reservation, and no item was created or lost. Unlike deposit's local-rejection
 * (which happens entirely before any Rails call), these all happen after a successful prepare,
 * which is why this is its own reason enum rather than reusing {@link
 * BankingDepositLocalRejectionReason}.
 */
public enum BankingWithdrawalAbortReason {
    /** {@link com.seggellion.britannia_mod.bank.item.BankItemCodec#deserialize} reported a corrupt or unsupported-schema payload. */
    DECODE_FAILED,
    /** The reconstructed stack decoded cleanly but its own fingerprint did not match what Rails sent -- a data-integrity mismatch. */
    FINGERPRINT_MISMATCH,
    /** Protocol step 3: the pre-check determined the stack could not fit before any receipt was written or insertion attempted. */
    INSUFFICIENT_CAPACITY,
    /**
     * Protocol step 5b: the real insertion attempt did not fully consume the stack despite the
     * pre-check having passed. Expected to be structurally unreachable in real operation (see
     * {@link BankingWithdrawalProxyService}'s own docs) -- kept as a real, tested defensive
     * path, not a speculative one.
     */
    INSERTION_FAILED
}
