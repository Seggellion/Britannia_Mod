package com.seggellion.britannia_mod.bank.transfer;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;
import java.util.UUID;

/**
 * A durable local record that Minecraft is about to perform (or has just performed) the risky
 * physical inventory/currency mutation for a bank transfer -- Section A.6's withdrawal step 2
 * ("Minecraft persists a local transfer receipt before ownership is exposed to the player") and
 * the analogous deposit risk window between removal and confirm.
 *
 * {@code operationId} is the opaque UUID this store is given -- it will match Rails'
 * {@code BankTransferOperation} public UUID once Milestone 9 actually wires a live caller, but
 * this class does not know or care where the UUID came from.
 *
 * Exactly one of {@code itemPayload} or {@code currencyAmount} is present, never both, never
 * neither -- mirroring the same nullable-forward-compat shape Rails' own {@code BankTransaction}
 * already uses for {@code bank_item_id}. Only the item path is exercised by this slice (no
 * currency-transfer protocol exists yet on the Rails side, confirmed in Milestone 8 Rails
 * Slice 2); the currency field exists because Section A.6 states currency uses "the same
 * ownership protocol", so the schema should not have to change shape when that path is built.
 * {@code itemPayload} is treated as fully opaque here -- already-serialized {@link
 * com.seggellion.britannia_mod.bank.item.BankItemCodec} bytes, never re-decoded by this class.
 */
public record BankTransferReceipt(
    UUID operationId,
    BankTransferOperationType operationType,
    byte[] itemPayload,
    Long currencyAmount,
    BankTransferReceiptStatus status,
    long createdAtEpochMillis
) {
    private static final String KEY_OPERATION_ID = "OperationId";
    private static final String KEY_OPERATION_TYPE = "OperationType";
    private static final String KEY_ITEM_PAYLOAD = "ItemPayload";
    private static final String KEY_CURRENCY_AMOUNT = "CurrencyAmount";
    private static final String KEY_STATUS = "Status";
    private static final String KEY_CREATED_AT = "CreatedAtEpochMillis";

    public BankTransferReceipt {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(operationType, "operationType");
        Objects.requireNonNull(status, "status");
        if (createdAtEpochMillis < 0L) {
            throw new IllegalArgumentException("createdAtEpochMillis must not be negative");
        }
        boolean hasItem = itemPayload != null && itemPayload.length > 0;
        boolean hasCurrency = currencyAmount != null;
        if (hasItem == hasCurrency) {
            throw new IllegalArgumentException(
                "exactly one of itemPayload or currencyAmount is required, not " + (hasItem ? "both" : "neither"));
        }
        if (hasCurrency && currencyAmount < 0L) {
            throw new IllegalArgumentException("currencyAmount must not be negative");
        }
        // Defensive copy: the caller's array must never be mutated out from under a stored
        // receipt after construction.
        itemPayload = hasItem ? itemPayload.clone() : null;
    }

    /** Defensive copy on the way out, matching the constructor's own copy on the way in. */
    @Override
    public byte[] itemPayload() {
        return itemPayload == null ? null : itemPayload.clone();
    }

    public boolean isItemTransfer() {
        return itemPayload != null;
    }

    CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(KEY_OPERATION_ID, operationId);
        tag.putString(KEY_OPERATION_TYPE, operationType.name());
        if (itemPayload != null) {
            tag.put(KEY_ITEM_PAYLOAD, new ByteArrayTag(itemPayload));
        } else {
            tag.putLong(KEY_CURRENCY_AMOUNT, currencyAmount);
        }
        tag.putString(KEY_STATUS, status.name());
        tag.putLong(KEY_CREATED_AT, createdAtEpochMillis);
        return tag;
    }

    static BankTransferReceipt fromNbt(CompoundTag tag) {
        if (!tag.hasUUID(KEY_OPERATION_ID)
                || !tag.contains(KEY_OPERATION_TYPE, Tag.TAG_STRING)
                || !tag.contains(KEY_STATUS, Tag.TAG_STRING)
                || !tag.contains(KEY_CREATED_AT, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("missing bank transfer receipt identity or metadata");
        }
        boolean hasItem = tag.contains(KEY_ITEM_PAYLOAD, Tag.TAG_BYTE_ARRAY);
        boolean hasCurrency = tag.contains(KEY_CURRENCY_AMOUNT, Tag.TAG_LONG);
        if (hasItem == hasCurrency) {
            throw new IllegalArgumentException("receipt must carry exactly one of ItemPayload or CurrencyAmount");
        }
        return new BankTransferReceipt(
            tag.getUUID(KEY_OPERATION_ID),
            BankTransferOperationType.valueOf(tag.getString(KEY_OPERATION_TYPE)),
            hasItem ? tag.getByteArray(KEY_ITEM_PAYLOAD) : null,
            hasCurrency ? tag.getLong(KEY_CURRENCY_AMOUNT) : null,
            BankTransferReceiptStatus.valueOf(tag.getString(KEY_STATUS)),
            tag.getLong(KEY_CREATED_AT)
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BankTransferReceipt that)) return false;
        return createdAtEpochMillis == that.createdAtEpochMillis
            && operationId.equals(that.operationId)
            && operationType == that.operationType
            && java.util.Arrays.equals(itemPayload, that.itemPayload)
            && Objects.equals(currencyAmount, that.currencyAmount)
            && status == that.status;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(operationId, operationType, currencyAmount, status, createdAtEpochMillis);
        return 31 * result + java.util.Arrays.hashCode(itemPayload);
    }
}
