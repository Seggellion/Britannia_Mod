package com.seggellion.britannia_mod.service.banking;

/**
 * Why a currency withdrawal was aborted after Rails had already prepared/reserved the amount,
 * but before any coins were given to the player -- Cancel is called in every case, releasing
 * Rails' reservation. The currency-withdrawal counterpart to {@link BankingWithdrawalAbortReason},
 * with no decode/fingerprint concepts (currency has no serialized payload to reconstruct or
 * verify -- the amount is already known locally before prepare is ever called).
 */
public enum BankingCurrencyWithdrawalAbortReason {
    /**
     * The player's inventory no longer has room for the requested amount by the time this
     * second, post-prepare check runs -- unlike the pre-prepare local rejection ({@link
     * BankingCurrencyWithdrawalLocalRejectionReason#INSUFFICIENT_CAPACITY}), a real yield point
     * (the network round trip to Rails) sits between the first check and this one, so the
     * player's inventory could genuinely have changed in between. See {@link
     * BankingCurrencyWithdrawalProxyService}'s own docs for why this second check, run
     * immediately before insertion with no yield point after it, is what actually makes
     * insertion failure structurally unreachable.
     */
    INSUFFICIENT_CAPACITY,
    /**
     * The real insertion attempt did not fully consume the coin stack despite both capacity
     * checks having passed. Expected to be structurally unreachable in real operation (mirroring
     * {@link BankingWithdrawalAbortReason#INSERTION_FAILED}'s own reasoning) -- kept as a real,
     * tested defensive path, not a speculative one.
     */
    INSERTION_FAILED
}
