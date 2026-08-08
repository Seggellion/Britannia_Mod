package com.seggellion.britannia_mod.service.banking;

/**
 * Why a cheque issuance attempt was rejected locally, before any network call left this process.
 */
public enum BankingChequeIssuanceLocalRejectionReason {
    /**
     * The requested amount is outside the approved 500-100,000 gold-equivalent range (ADR-018/
     * ADR-019), or is not an exact whole multiple of a gold coin. A pure UX/efficiency check --
     * the screen's own numeric input cannot produce a non-whole-gold value, and Rails'
     * {@code ChequePayloadValidator} independently enforces the same bounds authoritatively;
     * this only avoids a wasted round trip for a request Rails would reject anyway.
     */
    INVALID_AMOUNT,

    /**
     * The player's inventory has no room for one more item, determined before any Rails call or
     * gold reservation is made -- there is no reason to spend a round trip, or let Rails reserve
     * real gold, for an issuance that could never be delivered.
     */
    INSUFFICIENT_CAPACITY
}
