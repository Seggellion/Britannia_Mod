package com.seggellion.britannia_mod.economy;

import com.seggellion.britannia_mod.bank.item.BankItemCodec;
import com.seggellion.britannia_mod.bank.item.BankItemDecodeResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vendor/Trader Milestone 19.5: the durable local record of items a player has
 * handed to a Trader, held from before they leave the inventory until the sale
 * is known to have settled.
 *
 * <p>Item payloads reuse {@link BankItemCodec} — the same versioned, corruption-
 * aware encoding banking already trusts for items sitting on disk — rather than
 * a second serialization path invented here.
 *
 * <h2>Status is the anti-duplication rule</h2>
 * {@link Status#RESERVED} means the receipt was written but the removal had not
 * yet been made durable, so the player's saved inventory still holds the items:
 * recovery must NOT refund such a receipt. Only {@link Status#ITEMS_REMOVED} and
 * {@link Status#DISPATCHED} prove the items durably left the player, and only
 * those are refundable. Loss is bounded and visible; duplication is unbounded
 * inflation, so the ambiguous case always resolves toward "do not mint".
 */
public record TraderSaleReservationReceipt(
        String idempotencyKey,
        UUID playerUuid,
        List<byte[]> itemPayloads,
        Status status,
        long createdAtEpochMillis
) {
    public enum Status {
        /** Written; the items may or may not have durably left the inventory. */
        RESERVED,
        /** The removal was force-saved: the items are provably gone from the player. */
        ITEMS_REMOVED,
        /** Sent to Rails; the outcome is unknown to this process. */
        DISPATCHED
    }

    public TraderSaleReservationReceipt {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        if (playerUuid == null) throw new IllegalArgumentException("playerUuid is required");
        if (itemPayloads == null || itemPayloads.isEmpty()) {
            throw new IllegalArgumentException("a reservation must carry at least one item payload");
        }
        if (status == null) throw new IllegalArgumentException("status is required");
        List<byte[]> copied = new ArrayList<>(itemPayloads.size());
        for (byte[] payload : itemPayloads) copied.add(payload.clone());
        itemPayloads = List.copyOf(copied);
    }

    public TraderSaleReservationReceipt withStatus(Status next) {
        return new TraderSaleReservationReceipt(
                idempotencyKey, playerUuid, itemPayloads, next, createdAtEpochMillis);
    }

    /** Only a receipt whose items provably left the player may be refunded. */
    public boolean refundable() {
        return status == Status.ITEMS_REMOVED || status == Status.DISPATCHED;
    }

    /**
     * Decodes the reserved stacks. Corrupt or future-schema payloads are skipped
     * rather than throwing — a single unreadable entry must never block the
     * refund of the readable ones (banking's own decode-result convention).
     */
    public List<ItemStack> decodeItems(HolderLookup.Provider registries) {
        List<ItemStack> stacks = new ArrayList<>();
        for (byte[] payload : itemPayloads) {
            if (BankItemCodec.deserialize(payload, registries) instanceof BankItemDecodeResult.Success success) {
                stacks.add(success.stack());
            }
        }
        return stacks;
    }

    /** True when any payload could not be decoded (operator-visible signal). */
    public boolean hasUnreadableItems(HolderLookup.Provider registries) {
        return decodeItems(registries).size() != itemPayloads.size();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("IdempotencyKey", idempotencyKey);
        tag.putUUID("PlayerUuid", playerUuid);
        tag.putString("Status", status.name());
        tag.putLong("CreatedAtEpochMillis", createdAtEpochMillis);
        ListTag items = new ListTag();
        for (byte[] payload : itemPayloads) {
            CompoundTag entry = new CompoundTag();
            entry.putByteArray("Payload", payload);
            items.add(entry);
        }
        tag.put("Items", items);
        return tag;
    }

    /** Returns null for an unreadable entry rather than throwing. */
    public static TraderSaleReservationReceipt fromTag(CompoundTag tag) {
        try {
            List<byte[]> payloads = new ArrayList<>();
            ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
            for (int index = 0; index < items.size(); index++) {
                payloads.add(items.getCompound(index).getByteArray("Payload"));
            }
            if (payloads.isEmpty()) return null;
            return new TraderSaleReservationReceipt(
                    tag.getString("IdempotencyKey"),
                    tag.getUUID("PlayerUuid"),
                    payloads,
                    Status.valueOf(tag.getString("Status")),
                    tag.getLong("CreatedAtEpochMillis")
            );
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
