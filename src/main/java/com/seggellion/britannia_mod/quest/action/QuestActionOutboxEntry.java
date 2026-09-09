package com.seggellion.britannia_mod.quest.action;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;
import java.util.UUID;

/**
 * One durable outbox row (protocol section 2.2): the whole event, plus when it may next be
 * attempted and what the last attempt said.
 *
 * <p>The event is stored structurally rather than as an encoded body because the body carries a
 * per-attempt {@code request_uuid} (section 0): re-encoding per attempt is what keeps the trace id
 * honest while the {@code event_uuid} -- Rails' idempotency key -- stays fixed for the life of the
 * row.
 *
 * <p>Timestamps are epoch milliseconds; {@code nextAttemptAtEpochMillis} of zero means "due now".
 */
public record QuestActionOutboxEntry(
    QuestActionEvent event,
    int attempts,
    long createdAtEpochMillis,
    long nextAttemptAtEpochMillis,
    String lastError
) {
    private static final String KEY_EVENT_UUID = "EventUuid";
    private static final String KEY_PLAYER_UUID = "PlayerUuid";
    private static final String KEY_ACTION = "Action";
    private static final String KEY_OCCURRED_AT = "OccurredAt";
    private static final String KEY_DIMENSION = "Dimension";
    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";
    private static final String KEY_Z = "Z";
    private static final String KEY_SUBJECT = "Subject";
    private static final String KEY_SUBJECT_NAME = "K";
    private static final String KEY_SUBJECT_TYPE = "T";
    private static final String KEY_SUBJECT_VALUE = "V";
    private static final String KEY_TARGET_STATE = "TargetQuestStateId";
    private static final String KEY_TARGET_NODE = "TargetNodeId";
    private static final String KEY_TARGET_TRIGGER = "TargetTriggerKey";
    private static final String KEY_ATTEMPTS = "Attempts";
    private static final String KEY_CREATED_AT = "CreatedAt";
    private static final String KEY_NEXT_ATTEMPT_AT = "NextAttemptAt";
    private static final String KEY_LAST_ERROR = "LastError";

    private static final String TYPE_TEXT = "s";
    private static final String TYPE_FLAG = "b";
    private static final String TYPE_NUMBER = "i";

    public QuestActionOutboxEntry {
        Objects.requireNonNull(event, "event");
        lastError = lastError == null ? "" : lastError;
        if (attempts < 0) throw new IllegalArgumentException("attempts must not be negative");
        if (createdAtEpochMillis < 0L || nextAttemptAtEpochMillis < 0L) {
            throw new IllegalArgumentException("timestamps must not be negative");
        }
    }

    /** A fresh row, due immediately: the first attempt happens as soon as it is flushed. */
    public static QuestActionOutboxEntry queued(QuestActionEvent event, long nowEpochMillis) {
        return new QuestActionOutboxEntry(event, 0, nowEpochMillis, 0L, "");
    }

    public UUID eventUuid() {
        return event.eventUuid();
    }

    public UUID playerUuid() {
        return event.playerUuid();
    }

    public String questStateId() {
        return event.target() == null ? "" : event.target().questStateId();
    }

    public String triggerKey() {
        return event.target() == null ? "" : event.target().triggerKey();
    }

    public boolean dueAt(long nowEpochMillis) {
        return nextAttemptAtEpochMillis <= nowEpochMillis;
    }

    /** The row after a failed attempt, backed off by {@link QuestActionBackoff}. */
    public QuestActionOutboxEntry withFailure(String error, long nowEpochMillis) {
        int nextAttempts = attempts + 1;
        return new QuestActionOutboxEntry(event, nextAttempts, createdAtEpochMillis,
            nowEpochMillis + QuestActionBackoff.delayMillis(nextAttempts),
            error == null ? "" : error);
    }

    /** The row rescheduled to be attempted at once: login and server start do this. */
    public QuestActionOutboxEntry dueNow() {
        return nextAttemptAtEpochMillis == 0L
            ? this
            : new QuestActionOutboxEntry(event, attempts, createdAtEpochMillis, 0L, lastError);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(KEY_EVENT_UUID, event.eventUuid());
        tag.putUUID(KEY_PLAYER_UUID, event.playerUuid());
        tag.putString(KEY_ACTION, event.action().wireName());
        tag.putString(KEY_OCCURRED_AT, event.occurredAt());
        tag.putString(KEY_DIMENSION, event.dimensionKey());
        tag.putInt(KEY_X, event.x());
        tag.putInt(KEY_Y, event.y());
        tag.putInt(KEY_Z, event.z());

        ListTag subject = new ListTag();
        event.subject().fields().forEach((name, value) -> {
            CompoundTag field = new CompoundTag();
            field.putString(KEY_SUBJECT_NAME, name);
            if (value instanceof QuestActionSubject.Text text) {
                field.putString(KEY_SUBJECT_TYPE, TYPE_TEXT);
                field.putString(KEY_SUBJECT_VALUE, text.value());
            } else if (value instanceof QuestActionSubject.Flag flag) {
                field.putString(KEY_SUBJECT_TYPE, TYPE_FLAG);
                field.putBoolean(KEY_SUBJECT_VALUE, flag.value());
            } else if (value instanceof QuestActionSubject.Number number) {
                field.putString(KEY_SUBJECT_TYPE, TYPE_NUMBER);
                field.putLong(KEY_SUBJECT_VALUE, number.value());
            } else {
                throw new IllegalStateException("unstorable subject value");
            }
            subject.add(field);
        });
        tag.put(KEY_SUBJECT, subject);

        if (event.target() != null) {
            tag.putString(KEY_TARGET_STATE, event.target().questStateId());
            tag.putLong(KEY_TARGET_NODE, event.target().nodeId());
            tag.putString(KEY_TARGET_TRIGGER, event.target().triggerKey());
        }
        tag.putInt(KEY_ATTEMPTS, attempts);
        tag.putLong(KEY_CREATED_AT, createdAtEpochMillis);
        tag.putLong(KEY_NEXT_ATTEMPT_AT, nextAttemptAtEpochMillis);
        if (!lastError.isEmpty()) tag.putString(KEY_LAST_ERROR, lastError);
        return tag;
    }

    /** Strict: anything this cannot read is quarantined by the store, never guessed at. */
    public static QuestActionOutboxEntry fromNbt(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (!tag.hasUUID(KEY_EVENT_UUID)) throw new IllegalArgumentException("missing EventUuid");
        if (!tag.hasUUID(KEY_PLAYER_UUID)) throw new IllegalArgumentException("missing PlayerUuid");
        QuestAction action = QuestAction.fromWireName(tag.getString(KEY_ACTION));
        if (action == null) throw new IllegalArgumentException("unknown Action");

        Tag rawSubject = tag.get(KEY_SUBJECT);
        if (!(rawSubject instanceof ListTag subjectList)
            || (!subjectList.isEmpty() && subjectList.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("malformed Subject");
        }
        QuestActionSubject.Builder subject = QuestActionSubject.builder();
        for (int index = 0; index < subjectList.size(); index++) {
            CompoundTag field = subjectList.getCompound(index);
            String name = field.getString(KEY_SUBJECT_NAME);
            switch (field.getString(KEY_SUBJECT_TYPE)) {
                case TYPE_TEXT -> subject.text(name, field.getString(KEY_SUBJECT_VALUE));
                case TYPE_FLAG -> subject.flag(name, field.getBoolean(KEY_SUBJECT_VALUE));
                case TYPE_NUMBER -> subject.number(name, field.getLong(KEY_SUBJECT_VALUE));
                default -> throw new IllegalArgumentException("unknown subject value type");
            }
        }

        QuestActionEvent.Target target = null;
        if (tag.contains(KEY_TARGET_STATE, Tag.TAG_STRING) && tag.contains(KEY_TARGET_TRIGGER, Tag.TAG_STRING)) {
            target = new QuestActionEvent.Target(tag.getString(KEY_TARGET_STATE),
                Math.max(0L, tag.getLong(KEY_TARGET_NODE)), tag.getString(KEY_TARGET_TRIGGER));
        }

        QuestActionEvent event = new QuestActionEvent(
            tag.getUUID(KEY_EVENT_UUID), tag.getUUID(KEY_PLAYER_UUID), action,
            tag.getString(KEY_OCCURRED_AT), tag.getString(KEY_DIMENSION),
            tag.getInt(KEY_X), tag.getInt(KEY_Y), tag.getInt(KEY_Z), subject.build(), target);

        return new QuestActionOutboxEntry(event, tag.getInt(KEY_ATTEMPTS), tag.getLong(KEY_CREATED_AT),
            tag.getLong(KEY_NEXT_ATTEMPT_AT), tag.getString(KEY_LAST_ERROR));
    }
}
