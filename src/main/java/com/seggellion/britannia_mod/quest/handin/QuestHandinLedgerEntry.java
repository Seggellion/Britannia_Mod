package com.seggellion.britannia_mod.quest.handin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One hand-in transaction as this server holds it (protocol section 1.5).
 *
 * <p>Everything needed to finish the transaction is here, because a restart has nothing else: the
 * transaction id Rails knows it by, the concrete proof of what was taken, and the correlation id
 * the first attempt used. The confirmation body is a pure function of those three, so the retry
 * after a restart posts the bytes the first attempt posted -- which is what keeps Rails' own
 * {@code evidence_conflict} check from firing on this server's own recovery.
 *
 * <p>Transitions are copy-on-write {@code withX} methods, so an entry that has been handed out can
 * never be mutated underneath the ledger that stored it.
 */
public record QuestHandinLedgerEntry(UUID handinUuid, UUID playerUuid, String questId,
                                     String questStateId, String choice,
                                     List<QuestHandinRemoval> proof, QuestHandinLocalState localState,
                                     UUID requestUuid, int attempts, long recordedAtMillis,
                                     long removedAtMillis, long settledAtMillis, String lastError) {

    private static final String KEY_HANDIN_UUID = "HandinUuid";
    private static final String KEY_PLAYER_UUID = "PlayerUuid";
    private static final String KEY_QUEST_ID = "QuestId";
    private static final String KEY_QUEST_STATE_ID = "QuestStateId";
    private static final String KEY_CHOICE = "Choice";
    private static final String KEY_PROOF = "Proof";
    private static final String KEY_STATE = "LocalState";
    private static final String KEY_REQUEST_UUID = "RequestUuid";
    private static final String KEY_ATTEMPTS = "Attempts";
    private static final String KEY_RECORDED_AT = "RecordedAt";
    private static final String KEY_REMOVED_AT = "RemovedAt";
    private static final String KEY_SETTLED_AT = "SettledAt";
    private static final String KEY_LAST_ERROR = "LastError";

    /** A safe code is short and machine-readable; anything longer is a message that leaked in. */
    private static final int MAX_ERROR_LENGTH = 64;

    public QuestHandinLedgerEntry {
        Objects.requireNonNull(handinUuid, "handinUuid");
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(localState, "localState");
        Objects.requireNonNull(requestUuid, "requestUuid");
        questId = questId == null ? "" : questId;
        questStateId = questStateId == null ? "" : questStateId;
        choice = choice == null ? "" : choice;
        proof = List.copyOf(proof);
        lastError = lastError == null ? "" : lastError;
        if (lastError.length() > MAX_ERROR_LENGTH) lastError = lastError.substring(0, MAX_ERROR_LENGTH);
        attempts = Math.max(0, attempts);
        // A state that says the items are gone with nothing to show for it is the one shape that
        // could ask Rails to pay for a removal it can never verify.
        if (localState.itemsRemoved() && proof.isEmpty()) {
            throw new IllegalArgumentException("a removed hand-in must carry its removal proof");
        }
    }

    /** A freshly prepared transaction: Rails has minted it, nothing has been taken. */
    public static QuestHandinLedgerEntry prepared(UUID handinUuid, UUID playerUuid, String questId,
                                                  String questStateId, String choice, UUID requestUuid,
                                                  long nowMillis) {
        return new QuestHandinLedgerEntry(handinUuid, playerUuid, questId, questStateId, choice,
                List.of(), QuestHandinLocalState.PREPARED, requestUuid, 0, nowMillis, 0L, 0L, "");
    }

    /**
     * The requirements resolved and the pack covers them; this is what will be taken.
     *
     * <p>The proof is attached <b>here</b>, before the mutation, and is never recomputed
     * afterwards. That is what makes a resolver hand-in refundable: the crop this server decided on
     * is durable before the crop could be taken.
     */
    public QuestHandinLedgerEntry withRemovalIntent(List<QuestHandinRemoval> plan, long nowMillis) {
        return new QuestHandinLedgerEntry(handinUuid, playerUuid, questId, questStateId, choice,
                plan, QuestHandinLocalState.REMOVAL_INTENT, requestUuid, attempts, recordedAtMillis,
                0L, 0L, "");
    }

    /** The player's own file now records the removal. */
    public QuestHandinLedgerEntry withRemovedLocally(long nowMillis) {
        return new QuestHandinLedgerEntry(handinUuid, playerUuid, questId, questStateId, choice,
                proof, QuestHandinLocalState.REMOVED_LOCAL, requestUuid, attempts, recordedAtMillis,
                nowMillis, 0L, "");
    }

    public QuestHandinLedgerEntry withConfirming(long nowMillis) {
        return new QuestHandinLedgerEntry(handinUuid, playerUuid, questId, questStateId, choice,
                proof, QuestHandinLocalState.CONFIRMING, requestUuid, attempts + 1, recordedAtMillis,
                removedAtMillis, 0L, lastError);
    }

    /** A settled ending. {@code state} must be one the caller has an authoritative answer for. */
    public QuestHandinLedgerEntry withSettled(QuestHandinLocalState state, long nowMillis) {
        return new QuestHandinLedgerEntry(handinUuid, playerUuid, questId, questStateId, choice,
                proof, state, requestUuid, attempts, recordedAtMillis, removedAtMillis, nowMillis, "");
    }

    /**
     * Items gone, and Rails proves neither ending. Kept forever with the reason attached.
     */
    public QuestHandinLedgerEntry withStranded(String reason, long nowMillis) {
        return new QuestHandinLedgerEntry(handinUuid, playerUuid, questId, questStateId, choice,
                proof, QuestHandinLocalState.STRANDED, requestUuid, attempts, recordedAtMillis,
                removedAtMillis, nowMillis, reason);
    }

    /** Bookkeeping after a failed attempt. Never changes the state, so it never changes what is owed. */
    public QuestHandinLedgerEntry withAttemptFailure(String safeCode) {
        return new QuestHandinLedgerEntry(handinUuid, playerUuid, questId, questStateId, choice,
                proof, localState, requestUuid, attempts, recordedAtMillis, removedAtMillis,
                settledAtMillis, safeCode);
    }

    /** Back to a state that owes nothing, for a transaction abandoned before any mutation. */
    public QuestHandinLedgerEntry withPrepared() {
        return new QuestHandinLedgerEntry(handinUuid, playerUuid, questId, questStateId, choice,
                List.of(), QuestHandinLocalState.PREPARED, requestUuid, attempts, recordedAtMillis,
                0L, 0L, lastError);
    }

    public boolean open() {
        return !localState.settled();
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_HANDIN_UUID, handinUuid.toString());
        tag.putString(KEY_PLAYER_UUID, playerUuid.toString());
        tag.putString(KEY_QUEST_ID, questId);
        tag.putString(KEY_QUEST_STATE_ID, questStateId);
        tag.putString(KEY_CHOICE, choice);
        tag.put(KEY_PROOF, QuestHandinRemovalNbt.toList(proof));
        // By name, never by ordinal: a state inserted later must not re-read an existing ledger.
        tag.putString(KEY_STATE, localState.name());
        tag.putString(KEY_REQUEST_UUID, requestUuid.toString());
        tag.putInt(KEY_ATTEMPTS, attempts);
        tag.putLong(KEY_RECORDED_AT, recordedAtMillis);
        tag.putLong(KEY_REMOVED_AT, removedAtMillis);
        tag.putLong(KEY_SETTLED_AT, settledAtMillis);
        if (!lastError.isEmpty()) tag.putString(KEY_LAST_ERROR, lastError);
        return tag;
    }

    /**
     * @throws IllegalArgumentException when the tag is not one this build wrote; the store
     *         quarantines it verbatim rather than dropping it, so a row it cannot read is still a
     *         row an operator can see
     */
    public static QuestHandinLedgerEntry fromNbt(CompoundTag tag) {
        UUID handinUuid = uuid(tag, KEY_HANDIN_UUID);
        UUID playerUuid = uuid(tag, KEY_PLAYER_UUID);
        UUID requestUuid = uuid(tag, KEY_REQUEST_UUID);
        if (!tag.contains(KEY_STATE, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("missing LocalState");
        }
        QuestHandinLocalState state;
        try {
            state = QuestHandinLocalState.valueOf(tag.getString(KEY_STATE));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("unknown LocalState " + tag.getString(KEY_STATE));
        }
        List<QuestHandinRemoval> proof = tag.contains(KEY_PROOF, Tag.TAG_LIST)
                ? QuestHandinRemovalNbt.fromList(tag.getList(KEY_PROOF, Tag.TAG_COMPOUND))
                : List.of();
        return new QuestHandinLedgerEntry(handinUuid, playerUuid, tag.getString(KEY_QUEST_ID),
                tag.getString(KEY_QUEST_STATE_ID), tag.getString(KEY_CHOICE), proof, state,
                requestUuid, tag.getInt(KEY_ATTEMPTS), tag.getLong(KEY_RECORDED_AT),
                tag.getLong(KEY_REMOVED_AT), tag.getLong(KEY_SETTLED_AT),
                tag.getString(KEY_LAST_ERROR));
    }

    private static UUID uuid(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) throw new IllegalArgumentException("missing " + key);
        UUID parsed = QuestItemHandinProtocol.parseCanonicalUuid(tag.getString(key));
        if (parsed == null) throw new IllegalArgumentException(key + " is not a canonical UUID");
        return parsed;
    }

    /** The confirmation this entry owes, built from the stored evidence and nothing else. */
    public QuestItemHandinProtocol.ConfirmationRequest confirmation() {
        return new QuestItemHandinProtocol.ConfirmationRequest(handinUuid, playerUuid, true, proof,
                List.of(), requestUuid);
    }
}
