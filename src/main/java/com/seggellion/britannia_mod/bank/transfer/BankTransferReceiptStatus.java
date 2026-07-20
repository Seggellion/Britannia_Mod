package com.seggellion.britannia_mod.bank.transfer;

/**
 * A local status distinct from Rails' {@code BankTransferOperation} states
 * (prepared/confirmed/cancelled/expired/reconciliation_required) but informed by them: this
 * tracks whether Minecraft has locally committed to the risky physical action, not whether
 * Rails has confirmed anything.
 *
 * Only one value exists today: {@link #PENDING_LOCAL_ACTION}, written by
 * {@code BankTransferReceipts#record} and cleared by {@code BankTransferReceipts#resolve}
 * (which removes the receipt entirely rather than storing a terminal "resolved" value -- see
 * {@link BankTransferReceiptStore#resolve}). This slice implements exactly the two lifecycle
 * calls its own spec describes (record, resolve); it deliberately does not add a third
 * "local action definitely taken" transition that no current caller needs. A future
 * Milestone 9 wiring that wants finer-grained diagnostics between "about to act" and
 * "definitely acted" before Rails confirms can extend this enum then, once a real caller
 * exists to drive that extra transition -- this is not a speculative placeholder, it is
 * deliberately left out until something needs it.
 */
public enum BankTransferReceiptStatus {
    PENDING_LOCAL_ACTION
}
