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
     * <p>{@code nodeId} is {@code 0} whenever the server does not know it, which in production is
     * the normal case: the journal entry the watcher subscribes from carries no node id.
     *
     * <p>What follows from that is stricter than it once said here. Rails treats {@code target} as
     * optional, but a target that is present must carry {@code node_id} -- it is the key the
     * staleness check compares against -- and a partial one is refused outright with 400
     * {@code invalid_action_event}. So an unknown node id means the serializer omits the target
     * object entirely rather than sending two of its three fields. Rails then evaluates the event
     * against its own current node, which is the safer half of the contract this comment always
     * meant to describe; the earlier wording claimed dropping the field alone achieved that, and
     * it did not -- it rejected every objective the questline raised.
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
