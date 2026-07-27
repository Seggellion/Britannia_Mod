package com.seggellion.britannia_mod.bank.transfer;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
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
 * <p>{@code playerUuid} and {@code bankItemPublicId} (Milestone 9 NeoForge Slice 3b) exist so a
 * startup reconciliation can resume a confirm with no live {@code ServerPlayer} on hand (the
 * player may not even be online) and no other context to draw on:
 * <ul>
 *   <li>{@code playerUuid} is required because Rails' own {@code confirm} action genuinely
 *       re-validates it -- traced directly, not assumed: {@code resolve_owned_operation}
 *       resolves a {@code User} from {@code player_uuid} and re-scopes the {@code
 *       BankTransferOperation} lookup to that user's own {@code bank_accounts}.</li>
 *   <li>{@code bankItemPublicId} (present only for an item transfer, mirroring {@code
 *       itemPayload}/{@code currencyAmount}'s own mutual exclusivity) is never sent to Rails by
 *       confirm itself, but is required by {@code BankingDepositResult.Confirmed}/{@code
 *       BankingWithdrawalResult.Confirmed}'s own non-null contract -- the same one every live
 *       confirm already populates from the original prepare response.</li>
 * </ul>
 * Both are a real, deliberate schema change (not an addition made to fit within the existing
 * shape), which is why {@link BankTransferReceiptStore#SCHEMA_VERSION} moves to 2 alongside
 * them -- any receipt written under schema 1 (this program's own pre-Slice-3b testing, never a
 * real shipped server) has neither field to recover, so the whole store correctly falls back to
 * its existing unsupported-schema path (every entry surfaces as {@link
 * BankTransferReceiptStore.UnreadableEntry}) rather than being silently misread.
 *
 * Exactly one of {@code itemPayload} or {@code currencyAmount} is present, never both, never
 * neither -- mirroring the same nullable-forward-compat shape Rails' own {@code BankTransaction}
 * already uses for {@code bank_item_id}. Only the item path is exercised by this slice (no
 * currency-transfer protocol exists yet on the Rails side, confirmed in Milestone 8 Rails
 * Slice 2); the currency field exists because Section A.6 states currency uses "the same
 * ownership protocol", so the schema should not have to change shape when that path is built.
 * {@code itemPayload} is treated as fully opaque here -- already-serialized {@link
 * com.seggellion.britannia_mod.bank.item.BankItemCodec} bytes, never re-decoded by this class.
 *
 * <h2>Milestone 11: cheque issuance needs no schema change here</h2>
 * A {@link BankTransferOperationType#CHEQUE_ISSUANCE} receipt is currency-shaped
 * ({@code currencyAmount} carries the cheque's own requested/authoritative copper value,
 * {@code itemPayload}/{@code bankItemPublicId} both absent) -- deliberately NOT a third slot
 * carrying the cheque's own public UUID, even though that identity is exactly what {@code
 * bankItemPublicId} plays for an item transfer. The two are not analogous: {@code
 * bankItemPublicId} exists because Rails' {@code BankItem} already exists at prepare time, so a
 * resume can trust the receipt's own copy of it without re-asking Rails. A cheque's {@code
 * BankCheque} row does not exist until confirm succeeds (Rails Milestone 11 Slice 1's own
 * deliberate design -- see {@code docs/banking_bank_cheque_issuance.md}), and confirm is
 * idempotent: calling it again on resume always returns the exact same already-created cheque,
 * never a duplicate. A resume therefore never needs the cheque's identity persisted locally --
 * it simply re-asks Rails, safely, every time. This is why cheque issuance fits the existing
 * two-shape schema (item xor currency) rather than needing a genuinely new third shape the way
 * {@code bank_transfer_operations.bank_cheque_id} did on the Rails side: unlike that column
 * (which had to become a real, deferred-presence addition because {@code BankTransferOperation}
 * itself is the durable, permanent record of the operation), this receipt is only ever a
 * transient, pre-resolution crash-recovery aid -- it can always afford to re-derive information
 * from Rails on resume rather than cache it, so it never needed the identity in the first place.
 */
public record BankTransferReceipt(
    UUID operationId,
    UUID playerUuid,
    BankTransferOperationType operationType,
    byte[] itemPayload,
    Long currencyAmount,
    @Nullable UUID bankItemPublicId,
    BankTransferReceiptStatus status,
    long createdAtEpochMillis
) {
    private static final String KEY_OPERATION_ID = "OperationId";
    private static final String KEY_PLAYER_UUID = "PlayerUuid";
    private static final String KEY_OPERATION_TYPE = "OperationType";
    private static final String KEY_ITEM_PAYLOAD = "ItemPayload";
    private static final String KEY_CURRENCY_AMOUNT = "CurrencyAmount";
    private static final String KEY_BANK_ITEM_PUBLIC_ID = "BankItemPublicId";
    private static final String KEY_STATUS = "Status";
    private static final String KEY_CREATED_AT = "CreatedAtEpochMillis";

    public BankTransferReceipt {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(playerUuid, "playerUuid");
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
        if (hasItem == (bankItemPublicId == null)) {
            throw new IllegalArgumentException(
                "bankItemPublicId must be present for an item transfer and absent for a currency transfer");
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
        tag.putUUID(KEY_PLAYER_UUID, playerUuid);
        tag.putString(KEY_OPERATION_TYPE, operationType.name());
        if (itemPayload != null) {
            tag.put(KEY_ITEM_PAYLOAD, new ByteArrayTag(itemPayload));
            tag.putUUID(KEY_BANK_ITEM_PUBLIC_ID, bankItemPublicId);
        } else {
            tag.putLong(KEY_CURRENCY_AMOUNT, currencyAmount);
        }
        tag.putString(KEY_STATUS, status.name());
        tag.putLong(KEY_CREATED_AT, createdAtEpochMillis);
        return tag;
    }

    static BankTransferReceipt fromNbt(CompoundTag tag) {
        if (!tag.hasUUID(KEY_OPERATION_ID)
                || !tag.hasUUID(KEY_PLAYER_UUID)
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
        if (hasItem && !tag.hasUUID(KEY_BANK_ITEM_PUBLIC_ID)) {
            throw new IllegalArgumentException("item-transfer receipt missing BankItemPublicId");
        }
        return new BankTransferReceipt(
            tag.getUUID(KEY_OPERATION_ID),
            tag.getUUID(KEY_PLAYER_UUID),
            BankTransferOperationType.valueOf(tag.getString(KEY_OPERATION_TYPE)),
            hasItem ? tag.getByteArray(KEY_ITEM_PAYLOAD) : null,
            hasCurrency ? tag.getLong(KEY_CURRENCY_AMOUNT) : null,
            hasItem ? tag.getUUID(KEY_BANK_ITEM_PUBLIC_ID) : null,
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
            && playerUuid.equals(that.playerUuid)
            && operationType == that.operationType
            && java.util.Arrays.equals(itemPayload, that.itemPayload)
            && Objects.equals(currencyAmount, that.currencyAmount)
            && Objects.equals(bankItemPublicId, that.bankItemPublicId)
            && status == that.status;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(operationId, playerUuid, operationType, currencyAmount, bankItemPublicId, status, createdAtEpochMillis);
        return 31 * result + java.util.Arrays.hashCode(itemPayload);
    }
}
