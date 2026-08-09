package com.seggellion.britannia_mod.service.banking;

/**
 * Why a cheque redemption attempt was rejected locally, before any network call left this
 * process -- mirrors {@link BankingCurrencyDepositLocalRejectionReason}'s own shape (the
 * structural precedent this flow's ordering follows) rather than {@link
 * BankingChequeIssuanceLocalRejectionReason}'s (a prepare-time amount/capacity check that has
 * no redemption equivalent -- redemption reserves nothing and constructs nothing).
 */
public enum BankingChequeRedemptionLocalRejectionReason {
    /** The selected slot is empty by the time the trigger actually ran. */
    EMPTY_SLOT,

    /**
     * The selected slot's live item is not a bank cheque -- unreachable through the real packet
     * router (which only dispatches a genuine {@code BankChequeItem} stack here, mirroring how
     * {@link BankingCurrencyDepositProxyService} is only ever dispatched a genuine coin stack),
     * kept as this service's own self-contained guard against a future misrouted caller.
     */
    NOT_A_CHEQUE
}
