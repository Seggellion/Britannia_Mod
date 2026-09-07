package com.seggellion.britannia_mod.quest.action;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Contract {@code quest_action_event} version 1 (protocol section 2.3): the body posted to
 * {@code POST /api/v2/quest_action_events} and the envelope Rails answers with.
 *
 * <p>The encoder writes the frozen fixture's exact layout -- two-space indentation, one field per
 * line in the frozen order, a nested {@code position} and {@code target} on one line each, LF line
 * endings and a trailing newline -- because
 * {@code quest_contract/v1/action_event_request_crop_harvest.json} is byte-compared against it.
 * Every value it emits is a canonical UUID, an enum wire name, a validated timestamp, a validated
 * plain string or a whole number, so nothing needs escaping and the bytes are fully determined by
 * the event.
 */
public final class QuestActionEventProtocol {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_RESPONSE_BYTES = 256 * 1024;

    /** Error codes section 2.3 and section 6 name for this endpoint. */
    public static final String ERROR_INVALID_ACTION_EVENT = "invalid_action_event";
    public static final String ERROR_PLAYER_NOT_FOUND = "player_not_found";

    private QuestActionEventProtocol() {}

    /** The five {@code result} values of section 2.3. All five are terminal for the outbox. */
    public enum Result {
        APPLIED("applied"),
        DUPLICATE("duplicate"),
        IRRELEVANT("irrelevant"),
        STALE("stale"),
        REJECTED("rejected");

        private final String wireName;

        Result(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static Result fromWireName(String value) {
            for (Result result : values()) {
                if (result.wireName.equals(value)) return result;
            }
            throw new MalformedActionEventException("unknown action event result");
        }
    }

    /**
     * A parsed success envelope. {@code root} is kept so the dispatcher can hand the very same
     * object to the existing journal and reward application path rather than re-deriving it.
     */
    public record Response(int protocolVersion, UUID eventUuid, Result result, String originalResult,
                           String reason, String questStateId, JsonObject root) {
        public Response {
            Objects.requireNonNull(eventUuid, "eventUuid");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(root, "root");
            originalResult = originalResult == null ? "" : originalResult;
            reason = reason == null ? "" : reason;
            questStateId = questStateId == null ? "" : questStateId;
        }

        /** Whether the response carries a state Rails wants this server to install locally. */
        public boolean carriesState() {
            return result == Result.APPLIED || result == Result.DUPLICATE;
        }
    }

    // --- request ---------------------------------------------------------------------------

    /**
     * Encodes one attempt. {@code requestUuid} is per attempt (section 0); everything else comes
     * from the durable event.
     */
    public static byte[] encode(QuestActionEvent event, UUID requestUuid) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(requestUuid, "requestUuid");

        StringBuilder body = new StringBuilder(512);
        body.append("{\n");
        body.append("  \"protocol_version\": ").append(PROTOCOL_VERSION).append(",\n");
        body.append("  \"event_uuid\": \"").append(event.eventUuid()).append("\",\n");
        body.append("  \"request_uuid\": \"").append(requestUuid).append("\",\n");
        body.append("  \"player_uuid\": \"").append(event.playerUuid()).append("\",\n");
        body.append("  \"action\": \"").append(event.action().wireName()).append("\",\n");
        body.append("  \"occurred_at\": \"").append(event.occurredAt()).append("\",\n");
        body.append("  \"dimension_key\": \"").append(event.dimensionKey()).append("\",\n");
        body.append("  \"position\": {\"x\": ").append(event.x())
            .append(", \"y\": ").append(event.y())
            .append(", \"z\": ").append(event.z()).append("},\n");
        appendSubject(body, event.subject(), event.target() != null);
        if (event.target() != null) {
            body.append("  \"target\": {\"quest_state_id\": \"").append(event.target().questStateId()).append("\"");
            if (event.target().hasNodeId()) {
                body.append(", \"node_id\": ").append(event.target().nodeId());
            }
            body.append(", \"trigger_key\": \"").append(event.target().triggerKey()).append("\"}\n");
        }
        body.append("}\n");
        return body.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendSubject(StringBuilder body, QuestActionSubject subject, boolean targetFollows) {
        String tail = targetFollows ? ",\n" : "\n";
        if (subject.isEmpty()) {
            body.append("  \"subject\": {}").append(tail);
            return;
        }
        body.append("  \"subject\": {\n");
        int remaining = subject.fields().size();
        for (Map.Entry<String, QuestActionSubject.Value> field : subject.fields().entrySet()) {
            body.append("    \"").append(field.getKey()).append("\": ");
            appendValue(body, field.getValue());
            body.append(--remaining > 0 ? ",\n" : "\n");
        }
        body.append("  }").append(tail);
    }

    private static void appendValue(StringBuilder body, QuestActionSubject.Value value) {
        if (value instanceof QuestActionSubject.Text text) {
            body.append('"').append(text.value()).append('"');
        } else if (value instanceof QuestActionSubject.Flag flag) {
            body.append(flag.value());
        } else if (value instanceof QuestActionSubject.Number number) {
            body.append(number.value());
        } else {
            throw new IllegalStateException("unencodable subject value");
        }
    }

    /** ISO-8601 UTC at whole seconds, the shape every timestamp in the contract uses. */
    public static String formatOccurredAt(long epochMillis) {
        return DateTimeFormatter.ISO_INSTANT.format(
            Instant.ofEpochMilli(Math.max(0L, epochMillis)).truncatedTo(ChronoUnit.SECONDS));
    }

    // --- response --------------------------------------------------------------------------

    public static Response parse(byte[] body) {
        JsonObject root = object(body);
        int version = requireInt(root, "protocol_version");
        if (version != PROTOCOL_VERSION) throw new MalformedActionEventException("unsupported protocol_version");
        UUID eventUuid = requireUuid(root, "event_uuid");
        Result result = Result.fromWireName(requireString(root, "result"));

        String originalResult = optionalString(root, "original_result");
        String reason = optionalString(root, "reason");
        String questStateId = optionalString(root, "quest_state_id");
        if (result == Result.REJECTED && reason.isEmpty()) {
            throw new MalformedActionEventException("a rejected action event must name its reason");
        }
        if (result == Result.DUPLICATE && !originalResult.isEmpty()) {
            // Guards against a stored envelope whose original result is itself not a contract value.
            Result.fromWireName(originalResult);
        }
        return new Response(version, eventUuid, result, originalResult, reason, questStateId, root);
    }

    /** The {@code error} code of a section 0 error envelope, or an empty string when there is none. */
    public static String errorCode(byte[] body) {
        if (body == null || body.length == 0) return "";
        try {
            JsonElement parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return "";
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("error") || !root.get("error").isJsonPrimitive()) return "";
            String code = root.get("error").getAsString().trim().toLowerCase(Locale.ROOT);
            return code.length() <= 64 && code.matches("[a-z0-9_]+") ? code : "";
        } catch (JsonParseException | IllegalStateException malformed) {
            return "";
        }
    }

    static JsonObject object(byte[] body) {
        try {
            JsonElement parsed = JsonParser.parseString(
                new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new MalformedActionEventException("body must be an object");
            return parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException malformed) {
            throw new MalformedActionEventException("body is not JSON");
        }
    }

    static int requireInt(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive() || !parent.getAsJsonPrimitive(key).isNumber()) {
            throw new MalformedActionEventException(key + " must be an integer");
        }
        String raw = parent.get(key).getAsString();
        if (!raw.matches("-?(0|[1-9][0-9]*)")) throw new MalformedActionEventException(key + " must be an integer");
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException overflow) {
            throw new MalformedActionEventException(key + " is outside its bound");
        }
    }

    static String requireString(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive() || !parent.getAsJsonPrimitive(key).isString()) {
            throw new MalformedActionEventException(key + " must be a string");
        }
        String value = parent.get(key).getAsString();
        if (value.isBlank() || value.length() > 255) {
            throw new MalformedActionEventException(key + " is outside its size bound");
        }
        return value;
    }

    static String optionalString(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonPrimitive()
            || !parent.getAsJsonPrimitive(key).isString()) {
            return "";
        }
        String value = parent.get(key).getAsString().trim();
        return value.length() > 255 ? "" : value;
    }

    static UUID requireUuid(JsonObject parent, String key) {
        UUID parsed = parseCanonicalUuid(requireString(parent, key));
        if (parsed == null) throw new MalformedActionEventException(key + " must be a UUID");
        return parsed;
    }

    /** A 36-character canonical UUID, or null. Case-insensitive, no other spellings. */
    public static UUID parseCanonicalUuid(String raw) {
        if (raw == null || raw.length() != 36) return null;
        try {
            UUID parsed = UUID.fromString(raw);
            return parsed.toString().equalsIgnoreCase(raw) ? parsed : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** Plain, quote-free, control-free text of at most 255 characters. */
    static String requirePlainText(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 255) {
            throw new IllegalArgumentException(field + " is outside its size bound");
        }
        if (trimmed.chars().anyMatch(c -> c == '"' || c == '\\' || Character.isISOControl(c))) {
            throw new IllegalArgumentException(field + " carries a character the encoder cannot emit");
        }
        return trimmed;
    }

    static String requirePlainTimestamp(String value) {
        String plain = requirePlainText(value, "occurred_at");
        if (!plain.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z")) {
            throw new IllegalArgumentException("occurred_at must be an ISO-8601 UTC timestamp");
        }
        return plain;
    }

    public static final class MalformedActionEventException extends RuntimeException {
        public MalformedActionEventException(String message) {
            super(message);
        }
    }
}
