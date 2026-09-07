package com.seggellion.britannia_mod.quest.delivery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of the durable delivery ledger (protocol section 1.8): the delivery's identity, the
 * player it belongs to, the items still owed or already granted, and the local state machine's
 * position with its timestamps. Two fields beyond the protocol's list are recorded because the
 * apply sequence needs them: {@code acknowledgedOutcome} (what Rails last accepted, so a
 * {@code queued} entry is not acknowledged twice and an {@code applied} upgrade is sent once) and
 * {@code acknowledgementError} (the terminal Rails answer that closed the entry without a normal
 * acknowledgement, so it is not retried forever).
 *
 * <p>Timestamps are epoch milliseconds; zero means "not yet". Items are kept verbatim for as long
 * as the entry lives, which is what lets a {@code queued} delivery be granted after a restart
 * without asking Rails again.
 */
public record QuestRewardDeliveryLedgerEntry(
    UUID deliveryUuid,
    UUID playerUuid,
    long questId,
    String questStateId,
    String transitionKey,
    List<QuestRewardDeliveryItem> items,
    QuestRewardDeliveryLocalState localState,
    int attempts,
    long recordedAtEpochMillis,
    long appliedAtEpochMillis,
    long acknowledgedAtEpochMillis,
    String acknowledgedOutcome,
    String acknowledgementError
) {
    private static final String KEY_DELIVERY_UUID = "DeliveryUuid";
    private static final String KEY_PLAYER_UUID = "PlayerUuid";
    private static final String KEY_QUEST_ID = "QuestId";
    private static final String KEY_QUEST_STATE_ID = "QuestStateId";
    private static final String KEY_TRANSITION_KEY = "TransitionKey";
    private static final String KEY_ITEMS = "Items";
    private static final String KEY_LOCAL_STATE = "LocalState";
    private static final String KEY_ATTEMPTS = "Attempts";
    private static final String KEY_RECORDED_AT = "RecordedAt";
    private static final String KEY_APPLIED_AT = "AppliedAt";
    private static final String KEY_ACKNOWLEDGED_AT = "AcknowledgedAt";
    private static final String KEY_ACK_OUTCOME = "AckOutcome";
    private static final String KEY_ACK_ERROR = "AckError";

    public QuestRewardDeliveryLedgerEntry {
        Objects.requireNonNull(deliveryUuid, "deliveryUuid");
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(localState, "localState");
        questStateId = questStateId == null ? "" : questStateId;
        transitionKey = transitionKey == null ? "" : transitionKey;
        acknowledgedOutcome = acknowledgedOutcome == null ? "" : acknowledgedOutcome;
        acknowledgementError = acknowledgementError == null ? "" : acknowledgementError;
        items = List.copyOf(items);
        if (items.isEmpty() || items.size() > QuestRewardDelivery.MAX_ITEMS) {
            throw new IllegalArgumentException("a ledger entry carries 1.." + QuestRewardDelivery.MAX_ITEMS + " items");
        }
        if (attempts < 0) throw new IllegalArgumentException("attempts must not be negative");
        if (recordedAtEpochMillis < 0L || appliedAtEpochMillis < 0L || acknowledgedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("timestamps must not be negative");
        }
    }

    /** A fresh {@code pending_local} row for a delivery about to be applied. */
    public static QuestRewardDeliveryLedgerEntry pendingLocal(QuestRewardDelivery delivery, UUID playerUuid,
                                                              long nowEpochMillis) {
        return new QuestRewardDeliveryLedgerEntry(delivery.deliveryUuid(), playerUuid, delivery.questId(),
            delivery.questStateId(), delivery.transitionKey(), delivery.items(),
            QuestRewardDeliveryLocalState.PENDING_LOCAL, 0, nowEpochMillis, 0L, 0L, "", "");
    }

    /** The items are owed and not yet in the inventory. */
    public boolean itemsOwed() {
        return localState == QuestRewardDeliveryLocalState.PENDING_LOCAL
            || localState == QuestRewardDeliveryLocalState.QUEUED;
    }

    /** The items are in the inventory, whether or not Rails has been told. */
    public boolean itemsGranted() {
        return localState == QuestRewardDeliveryLocalState.APPLIED
            || localState == QuestRewardDeliveryLocalState.ACKNOWLEDGED;
    }

    /**
     * Rails still needs to hear about this entry: an {@code applied} state it has not recorded, or
     * a {@code queued} one it has not recorded as queued. A terminal error closes the question.
     */
    public boolean acknowledgementOutstanding() {
        if (!acknowledgementError.isEmpty()) return false;
        return switch (localState) {
            case APPLIED -> true;
            case QUEUED -> acknowledgedOutcome.isEmpty();
            case PENDING_LOCAL, ACKNOWLEDGED -> false;
        };
    }

    /** Anything the reconciler may still have to do for this entry. */
    public boolean open() {
        return itemsOwed() || acknowledgementOutstanding();
    }

    public QuestRewardDeliveryLedgerEntry withQueued(long nowEpochMillis) {
        return new QuestRewardDeliveryLedgerEntry(deliveryUuid, playerUuid, questId, questStateId, transitionKey, items,
            QuestRewardDeliveryLocalState.QUEUED, attempts + 1, recordedAtEpochMillis, appliedAtEpochMillis,
            acknowledgedAtEpochMillis, acknowledgedOutcome, acknowledgementError);
    }

    public QuestRewardDeliveryLedgerEntry withApplied(long nowEpochMillis) {
        return new QuestRewardDeliveryLedgerEntry(deliveryUuid, playerUuid, questId, questStateId, transitionKey, items,
            QuestRewardDeliveryLocalState.APPLIED, attempts + 1, recordedAtEpochMillis, nowEpochMillis,
            acknowledgedAtEpochMillis, acknowledgedOutcome, acknowledgementError);
    }

    public QuestRewardDeliveryLedgerEntry withAttempt() {
        return new QuestRewardDeliveryLedgerEntry(deliveryUuid, playerUuid, questId, questStateId, transitionKey, items,
            localState, attempts + 1, recordedAtEpochMillis, appliedAtEpochMillis,
            acknowledgedAtEpochMillis, acknowledgedOutcome, acknowledgementError);
    }

    /** Rails accepted the report: {@code applied} closes the entry, {@code queued} leaves it owed. */
    public QuestRewardDeliveryLedgerEntry withAcknowledged(QuestRewardDeliveryProtocol.Outcome outcome, long nowEpochMillis) {
        QuestRewardDeliveryLocalState next = outcome == QuestRewardDeliveryProtocol.Outcome.APPLIED
            ? QuestRewardDeliveryLocalState.ACKNOWLEDGED : localState;
        return new QuestRewardDeliveryLedgerEntry(deliveryUuid, playerUuid, questId, questStateId, transitionKey, items,
            next, attempts, recordedAtEpochMillis, appliedAtEpochMillis, nowEpochMillis, outcome.wireName(),
            acknowledgementError);
    }

    /** Rails answered with something no retry can change; the entry closes carrying the reason. */
    public QuestRewardDeliveryLedgerEntry withTerminalError(String errorCode, long nowEpochMillis) {
        String code = errorCode == null || errorCode.isBlank() ? "terminal_rejection" : errorCode;
        return new QuestRewardDeliveryLedgerEntry(deliveryUuid, playerUuid, questId, questStateId, transitionKey, items,
            QuestRewardDeliveryLocalState.ACKNOWLEDGED, attempts, recordedAtEpochMillis, appliedAtEpochMillis,
            nowEpochMillis, acknowledgedOutcome, code);
    }

    CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(KEY_DELIVERY_UUID, deliveryUuid);
        tag.putUUID(KEY_PLAYER_UUID, playerUuid);
        tag.putLong(KEY_QUEST_ID, questId);
        tag.putString(KEY_QUEST_STATE_ID, questStateId);
        tag.putString(KEY_TRANSITION_KEY, transitionKey);
        ListTag list = new ListTag();
        items.forEach(item -> list.add(item.toNbt()));
        tag.put(KEY_ITEMS, list);
        tag.putString(KEY_LOCAL_STATE, localState.name());
        tag.putInt(KEY_ATTEMPTS, attempts);
        tag.putLong(KEY_RECORDED_AT, recordedAtEpochMillis);
        tag.putLong(KEY_APPLIED_AT, appliedAtEpochMillis);
        tag.putLong(KEY_ACKNOWLEDGED_AT, acknowledgedAtEpochMillis);
        tag.putString(KEY_ACK_OUTCOME, acknowledgedOutcome);
        tag.putString(KEY_ACK_ERROR, acknowledgementError);
        return tag;
    }

    static QuestRewardDeliveryLedgerEntry fromNbt(CompoundTag tag) {
        if (!tag.hasUUID(KEY_DELIVERY_UUID) || !tag.hasUUID(KEY_PLAYER_UUID)
            || !tag.contains(KEY_LOCAL_STATE, Tag.TAG_STRING)
            || !tag.contains(KEY_ITEMS, Tag.TAG_LIST)
            || !tag.contains(KEY_RECORDED_AT, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("missing delivery ledger identity or state");
        }
        ListTag list = tag.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        List<QuestRewardDeliveryItem> items = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            items.add(QuestRewardDeliveryItem.fromNbt(list.getCompound(index)));
        }
        return new QuestRewardDeliveryLedgerEntry(
            tag.getUUID(KEY_DELIVERY_UUID),
            tag.getUUID(KEY_PLAYER_UUID),
            tag.getLong(KEY_QUEST_ID),
            tag.getString(KEY_QUEST_STATE_ID),
            tag.getString(KEY_TRANSITION_KEY),
            items,
            QuestRewardDeliveryLocalState.valueOf(tag.getString(KEY_LOCAL_STATE)),
            tag.getInt(KEY_ATTEMPTS),
            tag.getLong(KEY_RECORDED_AT),
            tag.getLong(KEY_APPLIED_AT),
            tag.getLong(KEY_ACKNOWLEDGED_AT),
            tag.getString(KEY_ACK_OUTCOME),
            tag.getString(KEY_ACK_ERROR));
    }
}
