package com.seggellion.britannia_mod.blessed.delivery;

/**
 * The local, Minecraft-side status of a blessed-item materialization -- deliberately NOT a
 * mirror of Rails' own {@code BlessedItem} state. This tracks only what this side knows about
 * the physical world: whether the item actually exists in Minecraft yet.
 *
 * <p>{@link #PENDING_PHYSICAL_DELIVERY} is written by {@code
 * BlessedDeliveryReceipts#recordPendingDelivery} *before* the risky physical act, so a crash at
 * any point during materialization leaves a durable trace that this instance was authorised and
 * may or may not have landed. {@link #DELIVERED} is written by {@code
 * BlessedDeliveryReceipts#markDelivered} once the physical item demonstrably exists.
 *
 * <p>Note what {@link #DELIVERED} does and does not claim: it claims the item exists in the
 * world, and nothing at all about whether Rails has been told. The Rails acknowledgement is a
 * separate, independently-retryable concern -- a receipt can sit in {@link #DELIVERED} with the
 * acknowledgement still outstanding, and that is a normal, expected state, not an anomaly. The
 * asymmetry is deliberate: losing the acknowledgement costs a retry, whereas losing the record
 * that the item was physically created costs a duplicate item.
 *
 * <p>There is deliberately no destroyed/cancelled/revoked value here. Destruction lifecycle is a
 * later milestone with its own real requirements (and, most likely, its own schema change); this
 * milestone implements exactly the two states its own delivery path needs, following the same
 * "leave the third status out until something needs it" discipline the banking precedent
 * ({@code BankTransferReceiptStatus}) established and then, one milestone later, actually
 * exercised.
 */
public enum BlessedDeliveryReceiptStatus {
    PENDING_PHYSICAL_DELIVERY,
    DELIVERED
}
