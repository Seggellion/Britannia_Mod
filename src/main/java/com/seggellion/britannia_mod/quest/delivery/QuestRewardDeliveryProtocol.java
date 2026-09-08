package com.seggellion.britannia_mod.quest.delivery;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * The acknowledgement half of contract {@code quest_reward_delivery} version 1 (protocol section
 * 1.7): the body posted to {@code /api/v2/quest_reward_deliveries/:delivery_uuid/result} and the
 * envelope Rails answers with. Frozen at M0; the request encoder is byte-compared against
 * {@code quest_contract/v1/delivery_result_request.json} by its unit test, which is why it writes
 * the fixture's exact layout rather than a compact object.
 */
public final class QuestRewardDeliveryProtocol {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_RESULT_RESPONSE_BYTES = 64 * 1024;
    public static final int MAX_PENDING_RESPONSE_BYTES = 512 * 1024;
    public static final int MAX_PENDING_DELIVERIES = 50;

    /** Error codes Rails answers that no retry can change (section 1.7, section 6). */
    public static final String ERROR_DELIVERY_NOT_FOUND = "delivery_not_found";
    public static final String ERROR_CONFLICTING_RESULT = "conflicting_delivery_result";
    public static final String ERROR_PLAYER_MISMATCH = "player_mismatch";

    private QuestRewardDeliveryProtocol() {}

    /** What the Minecraft server reports it did with the delivery. */
    public enum Outcome {
        APPLIED("applied"),
        QUEUED("queued");

        private final String wireName;

        Outcome(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static Outcome fromWireName(String value) {
            for (Outcome outcome : values()) {
                if (outcome.wireName.equals(value)) return outcome;
            }
            throw new IllegalArgumentException("unknown delivery outcome");
        }
    }

    public record AcknowledgementRequest(UUID deliveryUuid, UUID playerUuid, Outcome outcome,
                                         String recordedAt, UUID requestUuid) {
        public AcknowledgementRequest {
            Objects.requireNonNull(deliveryUuid, "deliveryUuid");
            Objects.requireNonNull(playerUuid, "playerUuid");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(recordedAt, "recordedAt");
            Objects.requireNonNull(requestUuid, "requestUuid");
            if (recordedAt.isBlank() || recordedAt.chars().anyMatch(c -> c == '"' || c == '\\' || Character.isISOControl(c))) {
                throw new IllegalArgumentException("recordedAt must be a plain ISO-8601 timestamp");
            }
        }
    }

    public record AcknowledgementResponse(int protocolVersion, UUID deliveryUuid, String state,
                                          String outcome, boolean duplicate) {}

    /**
     * Encodes the acknowledgement body in the fixture's layout: two-space indentation, one field
     * per line in the frozen order, LF line endings, a trailing newline. Every value is either a
     * canonical UUID string, an enum name, or a validated timestamp, so no escaping is needed and
     * the bytes are fully determined by the arguments.
     */
    public static byte[] encodeResult(AcknowledgementRequest request) {
        Objects.requireNonNull(request, "request");
        String body = "{\n"
            + "  \"protocol_version\": " + PROTOCOL_VERSION + ",\n"
            + "  \"delivery_uuid\": \"" + request.deliveryUuid() + "\",\n"
            + "  \"player_uuid\": \"" + request.playerUuid() + "\",\n"
            + "  \"outcome\": \"" + request.outcome().wireName() + "\",\n"
            + "  \"recorded_at\": \"" + request.recordedAt() + "\",\n"
            + "  \"request_uuid\": \"" + request.requestUuid() + "\"\n"
            + "}\n";
        return body.getBytes(StandardCharsets.UTF_8);
    }

    /** ISO-8601 UTC at whole seconds, the shape every timestamp in the contract uses. */
    public static String formatRecordedAt(long epochMillis) {
        return DateTimeFormatter.ISO_INSTANT.format(
            Instant.ofEpochMilli(Math.max(0L, epochMillis)).truncatedTo(ChronoUnit.SECONDS));
    }

    public static AcknowledgementResponse parseResult(byte[] body) {
        JsonObject root = object(body);
        int version = requireInt(root, "protocol_version");
        if (version != PROTOCOL_VERSION) throw new MalformedProtocolException("unsupported protocol_version");
        UUID deliveryUuid = requireUuid(root, "delivery_uuid");
        String state = requireString(root, "state");
        String outcome = requireString(root, "outcome");
        if (!root.has("duplicate") || !root.get("duplicate").isJsonPrimitive()
            || !root.getAsJsonPrimitive("duplicate").isBoolean()) {
            throw new MalformedProtocolException("duplicate must be a boolean");
        }
        return new AcknowledgementResponse(version, deliveryUuid, state, outcome, root.get("duplicate").getAsBoolean());
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
            JsonElement parsed = JsonParser.parseString(new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new MalformedProtocolException("body must be an object");
            return parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException malformed) {
            throw new MalformedProtocolException("body is not JSON");
        }
    }

    static int requireInt(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive() || !parent.getAsJsonPrimitive(key).isNumber()) {
            throw new MalformedProtocolException(key + " must be an integer");
        }
        String raw = parent.get(key).getAsString();
        if (!raw.matches("-?(0|[1-9][0-9]*)")) throw new MalformedProtocolException(key + " must be an integer");
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException overflow) {
            throw new MalformedProtocolException(key + " is outside its bound");
        }
    }

    static String requireString(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive() || !parent.getAsJsonPrimitive(key).isString()) {
            throw new MalformedProtocolException(key + " must be a string");
        }
        String value = parent.get(key).getAsString();
        if (value.isBlank() || value.length() > 255) throw new MalformedProtocolException(key + " is outside its size bound");
        return value;
    }

    static UUID requireUuid(JsonObject parent, String key) {
        String raw = requireString(parent, key);
        UUID parsed = parseCanonicalUuid(raw);
        if (parsed == null) throw new MalformedProtocolException(key + " must be a UUID");
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

    public static final class MalformedProtocolException extends RuntimeException {
        public MalformedProtocolException(String message) {
            super(message);
        }
    }
}
