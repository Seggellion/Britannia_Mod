package com.seggellion.britannia_mod.service.banking;

/**
 * Why a currency deposit attempt was rejected locally, before any network call left this
 * process -- the currency counterpart to {@link BankingDepositLocalRejectionReason}, and a much
 * shorter list because a coin stack has none of the item flow's serialization concerns (no
 * payload to oversize, no nesting to bound, no eligibility carve-outs to trip: a bare coin
 * stack IS the eligible input here).
 */
public enum BankingCurrencyDepositLocalRejectionReason {
    /** The source slot has nothing in it. */
    EMPTY_SLOT,
    /**
     * The source slot does not hold a bare coin stack ({@link
     * com.seggellion.britannia_mod.bank.currency.CurrencyItemRegistry} produced no key for it).
     * Unreachable through the real packet router, which only dispatches genuine coin stacks
     * here -- kept as the service's own self-contained guard so a direct call can never
     * misroute a non-coin item into the balance protocol.
     */
    NOT_CURRENCY
}
