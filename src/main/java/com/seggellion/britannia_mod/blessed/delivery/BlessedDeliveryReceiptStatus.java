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
 * <p>{@link #DESTROYED} is the terminal state, added in Starfarer M7 under the same discipline
 * M6 stated for it: leave the third status out until something needs it, then add it when
 * something does. It means this side positively established that the physical item is gone --
 * not that it could not be found, which is a different and much weaker claim that this enum
 * deliberately has no value for. See {@link #DESTROYED}'s own note for why the difference is the
 * whole point.
 *
 * <h2>Persisted as the enum NAME</h2>
 * {@code BlessedDeliveryReceipt#toNbt} writes {@code status.name()}, never {@code ordinal()},
 * which is what makes adding a value here a non-breaking change to the on-disk format: every
 * pre-existing row still spells the same name it always did, and its meaning does not shift
 * because a constant was appended. Declaration order therefore carries no persisted meaning, and
 * nothing may ever be re-ordered into a scheme where it does.
 */
public enum BlessedDeliveryReceiptStatus {
    PENDING_PHYSICAL_DELIVERY,
    DELIVERED,

    /**
     * The materialization is positively, terminally gone, and must never be physically
     * re-delivered under this {@code instanceUuid}. Restoration, if it is ever warranted, is
     * Rails' decision and arrives as a brand-new materialization with a fresh {@code
     * instanceUuid} -- exactly the path {@code BlessedItemInventorySync} already follows for a
     * Rails-side {@code destroyed} row.
     *
     * <p>The bar for writing this is deliberately high: an item that merely cannot be found is
     * NOT destroyed. A blessed medallion is equally at home in a chest, a bank, a display case or
     * an ender chest, none of which any scan inspects, so "absent from the player's inventory"
     * has never been evidence of anything. Only a positively-observed destruction (or an
     * operator's confirmed ruling) may write this value, because the cost of writing it wrongly
     * is a permanent, one-off entitlement that the protocol will now refuse to hand back.
     */
    DESTROYED
}
