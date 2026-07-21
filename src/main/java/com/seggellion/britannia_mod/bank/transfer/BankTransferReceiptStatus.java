package com.seggellion.britannia_mod.bank.transfer;

/**
 * A local status distinct from Rails' {@code BankTransferOperation} states
 * (prepared/confirmed/cancelled/expired/reconciliation_required) but informed by them: this
 * tracks whether Minecraft has locally committed to the risky physical action, not whether
 * Rails has confirmed anything.
 *
 * <p>{@link #PENDING_LOCAL_ACTION} is written by {@code BankTransferReceipts#record} and
 * cleared by {@code BankTransferReceipts#resolve} (which removes the receipt entirely rather
 * than storing a terminal "resolved" value -- see {@link BankTransferReceiptStore#resolve}).
 * Milestone 8 implemented exactly the two lifecycle calls its own spec described (record,
 * resolve) and deliberately left a third status out "until something needs it" -- Milestone 9's
 * deposit path is that real caller: {@link #RECONCILIATION_REQUIRED} is written by {@code
 * BankTransferReceipts#escalateToReconciliationRequired} when Rails reports its own {@code
 * RECONCILIATION_REQUIRED} confirm outcome (Section A.6's "crash after removal, confirm
 * ambiguous" case). Unlike {@link #PENDING_LOCAL_ACTION}, a receipt in this status is never
 * expected to transition further -- Rails itself has already declared the operation terminal
 * and unresolvable automatically, so nothing on this side ever calls {@code resolve} on it
 * either. It exists so a startup scan (and any future admin-facing surface) can distinguish
 * "still an ordinary in-flight retry candidate" from "already known to need human review",
 * rather than reporting both the same way.
 */
public enum BankTransferReceiptStatus {
    PENDING_LOCAL_ACTION,
    RECONCILIATION_REQUIRED
}
