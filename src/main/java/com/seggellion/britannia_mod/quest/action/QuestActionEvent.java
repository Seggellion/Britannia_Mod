package com.seggellion.britannia_mod.quest.action;

import java.util.Objects;
import java.util.UUID;

/**
 * One server-minted action event (protocol section 2.3), everything except the per-attempt
 * {@code request_uuid}: that is a trace id, not an identity, and a retry of the same event mints a
 * new one while {@link #eventUuid()} stays the same. Rails' idempotency key is the event uuid.
 *
 * <p>Every field here is observed by the server at the authoritative success point. Nothing in it
 * comes from a client packet.
 */
public record QuestActionEvent(
    UUID eventUuid,
    UUID playerUuid,
    QuestAction action,
    String occurredAt,
    String dimensionKey,
    int x,
    int y,
    int z,
    QuestActionSubject subject,
    Target target
) {
    public QuestActionEvent {
        Objects.requireNonNull(eventUuid, "eventUuid");
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(subject, "subject");
        occurredAt = QuestActionEventProtocol.requirePlainTimestamp(occurredAt);
        dimensionKey = QuestActionEventProtocol.requirePlainText(dimensionKey, "dimension_key");
        if (!subject.satisfies(action)) {
            throw new IllegalArgumentException(
                "subject is missing a field " + action.wireName() + " requires: " + action.requiredSubjectFields());
        }
    }

    /**
     * What the mod matched locally (section 2.3). Rails uses it only to answer {@code stale}.
     *
     * <p>{@code nodeId} is optional and is {@code 0} whenever the server does not know it. The
     * journal entry the watcher subscribes from carries no node id, so in production this is the
     * normal case: naming a node the mod is not sure of would invite a {@code stale} answer that
     * discards a legitimate event, while omitting it makes Rails evaluate against its own current
     * node -- strictly the safer half of the same contract.
     */
    public record Target(String questStateId, long nodeId, String triggerKey) {
        public Target {
            questStateId = QuestActionEventProtocol.requirePlainText(questStateId, "quest_state_id");
            triggerKey = QuestActionEventProtocol.requirePlainText(triggerKey, "trigger_key");
            if (nodeId < 0L) throw new IllegalArgumentException("node_id must not be negative");
        }

        public static Target of(String questStateId, String triggerKey) {
            return new Target(questStateId, 0L, triggerKey);
        }

        public boolean hasNodeId() {
            return nodeId > 0L;
        }
    }
}
