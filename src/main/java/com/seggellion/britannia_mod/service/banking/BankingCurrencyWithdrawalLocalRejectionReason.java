package com.seggellion.britannia_mod.service.banking;

/**
 * Why a currency withdrawal attempt was rejected locally, before any network call left this
 * process -- the currency-withdrawal counterpart to {@link BankingCurrencyDepositLocalRejectionReason}.
 */
public enum BankingCurrencyWithdrawalLocalRejectionReason {
    /**
     * The requested amount's resulting coin stack(s) would not fit in the player's inventory,
     * determined via {@link BankingWithdrawalProxyService#hasSufficientCapacity} before any
     * Rails call or reservation is made -- there is no reason to spend a round trip, or let
     * Rails reserve funds, for a withdrawal that could not possibly be delivered. See {@link
     * BankingCurrencyWithdrawalProxyService}'s own docs for why a second, later check
     * immediately before insertion is still required for the actual safety guarantee.
     */
    INSUFFICIENT_CAPACITY
}
