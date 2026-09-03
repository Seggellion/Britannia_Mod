package com.seggellion.britannia_mod.blessed.delivery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;
import java.util.UUID;

/**
 * A durable local record that Minecraft is about to perform (or has just performed) the
 * irreversible physical materialization of a blessed item -- the blessed-item analogue of
 * {@code BankTransferReceipt}'s "persist a local receipt before ownership is exposed to the
 * player" discipline. The risky act here is putting a real, keepable item into a real player's
 * inventory: once it exists, an unclean crash that loses the record of it existing produces a
 * duplicate, which for a one-off commemorative item is the exact failure this store exists to
 * make impossible.
 *
 * <h2>{@code instanceUuid} is the key, and only the key</h2>
 * {@code instanceUuid} is Rails' per-shard materialization identity: one blessed entitlement
 * ({@code deedId}) may legitimately be materialized more than once across shards, so the
 * entitlement uuid is NOT a safe primary key for a per-world delivery store -- the instance
 * identity is. Like {@code BankTransferReceipt#operationId}, this class does not know or care
 * where the UUID came from; it only requires that the caller hands it the same one on a replay.
 *
 * <h2>Why {@code deedId} is a String and {@code ownerUuid} is a UUID</h2>
 * {@code deedId} is Rails' {@code BlessedItem.uuid}. It is stored as an opaque {@link String}
 * rather than a {@link UUID} because legacy entitlement values are not guaranteed to be
 * uuid-shaped, and a store whose whole purpose is surviving a crash must never be the thing
 * that rejects a real, already-authorised delivery because a historical identifier failed a
 * parse this store had no business performing. {@code ownerUuid} has no such history: it is a
 * Minecraft player uuid, which is structurally a uuid by definition, so it is typed as one.
 *
 * <h2>The fingerprint</h2>
 * {@link #matches(String, String, UUID)} is the anti-corruption check, not a convenience. A
 * replay of an {@code instanceUuid} is only safely idempotent if it refers to the *same*
 * intended materialization: same entitlement, same item, same player. If local state is
 * corrupted, or a caller is buggy, or a stale receipt file is restored from a backup taken
 * before an unrelated delivery, then an {@code instanceUuid} could otherwise be silently bound
 * to the wrong item or -- far worse -- the wrong player, and the store would report a
 * reassuring "already handled" for a delivery it has never actually seen. Hence the store
 * treats a fingerprint disagreement as {@code FINGERPRINT_MISMATCH} and refuses to touch the
 * stored receipt at all, rather than overwriting it or answering yes.
 *
 * <p>Note deliberately what the fingerprint does NOT include: {@code status} and {@code
 * updatedEpochMillis}. Those are this side's own mutable bookkeeping, not the caller's
 * statement of intent -- a replay that arrives while the stored receipt has already advanced to
 * {@link BlessedDeliveryReceiptStatus#DELIVERED} is still the same intended materialization, and
 * must be recognised as an idempotent replay rather than a mismatch.
 */
public record BlessedDeliveryReceipt(
    UUID instanceUuid,
    String deedId,
    String itemId,
    UUID ownerUuid,
    BlessedDeliveryReceiptStatus status,
    long updatedEpochMillis
) {
    private static final String KEY_INSTANCE_UUID = "InstanceUuid";
    private static final String KEY_DEED_ID = "DeedId";
    private static final String KEY_ITEM_ID = "ItemId";
    private static final String KEY_OWNER_UUID = "OwnerUuid";
    private static final String KEY_STATUS = "Status";
    private static final String KEY_UPDATED_AT = "UpdatedEpochMillis";

    public BlessedDeliveryReceipt {
        Objects.requireNonNull(instanceUuid, "instanceUuid");
        Objects.requireNonNull(deedId, "deedId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(status, "status");
        if (deedId.isBlank()) {
            throw new IllegalArgumentException("deedId must not be blank");
        }
        if (itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        if (updatedEpochMillis < 0L) {
            throw new IllegalArgumentException("updatedEpochMillis must not be negative");
        }
    }

    /**
     * True if a replay carrying these fields refers to the same intended materialization as this
     * receipt. See this record's own docs for why {@code status}/{@code updatedEpochMillis} are
     * deliberately excluded.
     */
    public boolean matches(String otherDeedId, String otherItemId, UUID otherOwnerUuid) {
        return deedId.equals(otherDeedId)
            && itemId.equals(otherItemId)
            && ownerUuid.equals(otherOwnerUuid);
    }

    /** Convenience overload of {@link #matches(String, String, UUID)} for a whole incoming receipt. */
    public boolean matches(BlessedDeliveryReceipt other) {
        Objects.requireNonNull(other, "other");
        return matches(other.deedId(), other.itemId(), other.ownerUuid());
    }

    /** Returns a copy of this receipt in the given status, stamped with the given time. */
    BlessedDeliveryReceipt withStatus(BlessedDeliveryReceiptStatus newStatus, long atEpochMillis) {
        return new BlessedDeliveryReceipt(instanceUuid, deedId, itemId, ownerUuid, newStatus, atEpochMillis);
    }

    CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(KEY_INSTANCE_UUID, instanceUuid);
        tag.putString(KEY_DEED_ID, deedId);
        tag.putString(KEY_ITEM_ID, itemId);
        tag.putUUID(KEY_OWNER_UUID, ownerUuid);
        tag.putString(KEY_STATUS, status.name());
        tag.putLong(KEY_UPDATED_AT, updatedEpochMillis);
        return tag;
    }

    static BlessedDeliveryReceipt fromNbt(CompoundTag tag) {
        if (!tag.hasUUID(KEY_INSTANCE_UUID)
                || !tag.hasUUID(KEY_OWNER_UUID)
                || !tag.contains(KEY_DEED_ID, Tag.TAG_STRING)
                || !tag.contains(KEY_ITEM_ID, Tag.TAG_STRING)
                || !tag.contains(KEY_STATUS, Tag.TAG_STRING)
                || !tag.contains(KEY_UPDATED_AT, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("missing blessed delivery receipt identity or metadata");
        }
        return new BlessedDeliveryReceipt(
            tag.getUUID(KEY_INSTANCE_UUID),
            tag.getString(KEY_DEED_ID),
            tag.getString(KEY_ITEM_ID),
            tag.getUUID(KEY_OWNER_UUID),
            BlessedDeliveryReceiptStatus.valueOf(tag.getString(KEY_STATUS)),
            tag.getLong(KEY_UPDATED_AT)
        );
    }
}
