package com.seggellion.britannia_mod.service.spawn;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnClientResult.Disposition;

public final class ServiceNpcSpawnResponseParser {
    private static final Duration MAX_RETRY_AFTER = Duration.ofMinutes(5);
    private ServiceNpcSpawnResponseParser() {}

    public static ServiceNpcSpawnClientResult parse(
        int status, byte[] body, Map<String, List<String>> headers, ServiceNpcSpawnOperationRequest submitted
    ) {
        if (status >= 300 && status < 400) {
            return new ServiceNpcSpawnClientResult.HttpFailure("redirect_rejected", Disposition.PROTOCOL_INCOMPATIBLE, null);
        }
        if (status == 404) {
            return new ServiceNpcSpawnClientResult.HttpFailure("endpoint_unavailable", Disposition.RETRYABLE, null);
        }
        if (status == 429) {
            return new ServiceNpcSpawnClientResult.HttpFailure(
                "rate_limited", Disposition.RETRYABLE, parseRetryAfter(firstHeader(headers, "Retry-After"))
            );
        }

        final JsonObject root;
        try {
            root = JsonParser.parseString(decodeUtf8(body)).getAsJsonObject();
        } catch (RuntimeException | CharacterCodingException malformed) {
            if (status == 401 || status == 403) {
                return new ServiceNpcSpawnClientResult.HttpFailure("authentication_rejected", Disposition.AUTHENTICATION_BLOCKED, null);
            }
            if (status >= 500) return genericServerFailure(status);
            if (status >= 400 && status < 500 && !isProtocolEnvelopeStatus(status)) {
                return new ServiceNpcSpawnClientResult.HttpFailure("unexpected_client_response", Disposition.PERMANENT, null);
            }
            return protocolFailure("malformed_protocol_response");
        }

        try {
            return parseProtocol(status, root, submitted);
        } catch (ProtocolException incompatible) {
            if (status == 401 || status == 403) {
                return new ServiceNpcSpawnClientResult.HttpFailure("authentication_rejected", Disposition.AUTHENTICATION_BLOCKED, null);
            }
            if (status >= 500) return genericServerFailure(status);
            if (status >= 400 && status < 500 && !isProtocolEnvelopeStatus(status)) {
                return new ServiceNpcSpawnClientResult.HttpFailure("unexpected_client_response", Disposition.PERMANENT, null);
            }
            return protocolFailure(incompatible.safeCode);
        }
    }

    private static ServiceNpcSpawnClientResult parseProtocol(
        int status, JsonObject root, ServiceNpcSpawnOperationRequest submitted
    ) throws ProtocolException {
        int version = requiredInt(root, "protocol_version");
        if (version != ServiceNpcSpawnOperationRequest.PROTOCOL_VERSION) fail("unsupported_response_protocol");
        boolean success = requiredBoolean(root, "success");
        boolean retryable = requiredBoolean(root, "retryable");
        ServiceNpcSpawnOutcome outcome = ServiceNpcSpawnOutcome.parse(requiredString(root, "outcome"));
        if (outcome == null) fail("unknown_response_outcome");
        validateStatusAndFlags(status, outcome, success, retryable);

        UUID operationId = optionalUuid(root, "operation_id");
        UUID spawnUuid = optionalUuid(root, "spawn_uuid");
        Long submittedRevision = optionalLong(root, "submitted_revision");
        boolean correlationRequired = status == 200 || status == 409 || status == 422;
        if (correlationRequired && (operationId == null || spawnUuid == null)) fail("missing_response_correlation");
        if (operationId != null && !operationId.equals(submitted.operationId())) fail("operation_id_mismatch");
        if (spawnUuid != null && !spawnUuid.equals(submitted.spawnUuid())) fail("spawn_uuid_mismatch");
        if (submittedRevision != null && submittedRevision != submitted.sourceRevision()) fail("submitted_revision_mismatch");

        Long acknowledgedRevision = optionalLong(root, "acknowledged_revision");
        Long currentRevision = optionalLong(root, "current_revision");
        ServiceNpcSpawnProtocolResponse.RegistrationState state =
            optionalEnum(root, "registration_state", ServiceNpcSpawnProtocolResponse.RegistrationState.class);
        Instant acknowledgedAt = optionalInstant(root, "acknowledged_at");
        Boolean created = optionalBoolean(root, "created");
        String field = optionalString(root, "field");
        ServiceNpcSpawnProtocolResponse.CollisionKind collisionKind =
            optionalEnum(root, "collision_kind", ServiceNpcSpawnProtocolResponse.CollisionKind.class);
        Boolean replacementRequired = optionalBoolean(root, "replacement_uuid_required");
        ServiceNpcSpawnCanonicalLocation canonical = optionalCanonical(root);
        UUID occupyingSpawnUuid = optionalUuid(root, "occupying_spawn_uuid");

        if (success) {
            if (acknowledgedRevision == null || state == null || acknowledgedAt == null) fail("incomplete_success_response");
            if (submitted.operation() == ServiceNpcSpawnOperation.UPSERT) {
                if (acknowledgedRevision != submitted.sourceRevision()
                    || state != ServiceNpcSpawnProtocolResponse.RegistrationState.LIVE) fail("inconsistent_success_response");
            } else if (acknowledgedRevision < submitted.sourceRevision()
                || state != ServiceNpcSpawnProtocolResponse.RegistrationState.REMOVED) {
                fail("inconsistent_success_response");
            }
        }
        if (outcome == ServiceNpcSpawnOutcome.STALE_REVISION
            && (submittedRevision == null || currentRevision == null)) fail("incomplete_stale_response");
        if (outcome == ServiceNpcSpawnOutcome.REVISION_CONFLICT && currentRevision == null) {
            fail("incomplete_revision_conflict");
        }
        if (outcome == ServiceNpcSpawnOutcome.UUID_COLLISION) {
            if (collisionKind == null || !Boolean.TRUE.equals(replacementRequired)) fail("incomplete_uuid_collision");
            if (collisionKind == ServiceNpcSpawnProtocolResponse.CollisionKind.LIVE && canonical == null) {
                fail("missing_canonical_location");
            }
            if (collisionKind != ServiceNpcSpawnProtocolResponse.CollisionKind.LIVE && canonical != null) {
                fail("unsafe_collision_metadata");
            }
        }
        if (outcome == ServiceNpcSpawnOutcome.LOCATION_OCCUPIED
            && (occupyingSpawnUuid == null || canonical == null)) fail("incomplete_location_occupied");

        ServiceNpcSpawnProtocolResponse response = new ServiceNpcSpawnProtocolResponse(
            version, success, outcome, retryable, operationId, spawnUuid, submittedRevision,
            acknowledgedRevision, currentRevision, state, acknowledgedAt, created, field,
            collisionKind, replacementRequired, canonical, occupyingSpawnUuid
        );
        return new ServiceNpcSpawnClientResult.Protocol(response, disposition(outcome));
    }

    private static void validateStatusAndFlags(
        int status, ServiceNpcSpawnOutcome outcome, boolean success, boolean retryable
    ) throws ProtocolException {
        int expected = switch (outcome) {
            case APPLIED, ALREADY_APPLIED -> 200;
            case UNAUTHORIZED -> 401;
            case SERVER_NOT_AUTHORIZED -> 403;
            case PAYLOAD_TOO_LARGE -> 413;
            case INVALID_CITY, INVALID_SERVICE_NPC_TYPE, SERVICE_NPC_TYPE_INACTIVE,
                 SERVICE_NPC_TYPE_NOT_SPAWNABLE -> 422;
            case STALE_REVISION, REVISION_CONFLICT, UUID_COLLISION, LOCATION_OCCUPIED -> 409;
            case SERVICE_UNAVAILABLE -> 503;
            default -> 400;
        };
        boolean expectedSuccess = expected == 200;
        boolean expectedRetryable = outcome == ServiceNpcSpawnOutcome.SERVICE_UNAVAILABLE;
        if (status != expected || success != expectedSuccess || retryable != expectedRetryable) {
            fail("inconsistent_protocol_envelope");
        }
    }

    private static Disposition disposition(ServiceNpcSpawnOutcome outcome) {
        return switch (outcome) {
            case APPLIED, ALREADY_APPLIED -> Disposition.SUCCESS;
            case UNAUTHORIZED, SERVER_NOT_AUTHORIZED -> Disposition.AUTHENTICATION_BLOCKED;
            case SERVICE_UNAVAILABLE -> Disposition.RETRYABLE;
            case UNSUPPORTED_PROTOCOL -> Disposition.PROTOCOL_INCOMPATIBLE;
            case UUID_COLLISION -> Disposition.UUID_COLLISION;
            case LOCATION_OCCUPIED -> Disposition.LOCATION_OCCUPIED;
            default -> Disposition.PERMANENT;
        };
    }

    private static ServiceNpcSpawnClientResult genericServerFailure(int status) {
        return new ServiceNpcSpawnClientResult.HttpFailure(
            "server_failure_" + status, Disposition.RETRYABLE, null
        );
    }

    private static ServiceNpcSpawnClientResult protocolFailure(String code) {
        return new ServiceNpcSpawnClientResult.HttpFailure(code, Disposition.PROTOCOL_INCOMPATIBLE, null);
    }

    private static boolean isProtocolEnvelopeStatus(int status) {
        return status == 400 || status == 409 || status == 413 || status == 422;
    }

    private static ServiceNpcSpawnCanonicalLocation optionalCanonical(JsonObject root) throws ProtocolException {
        JsonElement value = root.get("canonical_location");
        if (value == null) return null;
        if (!value.isJsonObject()) fail("invalid_canonical_location");
        JsonObject object = value.getAsJsonObject();
        UUID serverKey = requiredUuid(object, "minecraft_server_key");
        String worldName = requiredString(object, "world_name");
        ResourceLocation dimension = ResourceLocation.tryParse(requiredString(object, "dimension"));
        if (dimension == null) fail("invalid_canonical_dimension");
        try {
            return new ServiceNpcSpawnCanonicalLocation(
                serverKey, worldName, dimension, requiredInt(object, "x"),
                requiredInt(object, "y"), requiredInt(object, "z")
            );
        } catch (IllegalArgumentException invalid) {
            fail(invalid.getMessage());
            return null;
        }
    }

    private static String decodeUtf8(byte[] body) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(body == null ? new byte[0] : body)).toString();
    }

    private static String requiredString(JsonObject root, String name) throws ProtocolException {
        String value = optionalString(root, name);
        if (value == null) fail("missing_" + name);
        return value;
    }

    private static String optionalString(JsonObject root, String name) throws ProtocolException {
        JsonElement value = root.get(name);
        if (value == null) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) fail("invalid_" + name);
        return value.getAsString();
    }

    private static boolean requiredBoolean(JsonObject root, String name) throws ProtocolException {
        Boolean value = optionalBoolean(root, name);
        if (value == null) fail("missing_" + name);
        return value;
    }

    private static Boolean optionalBoolean(JsonObject root, String name) throws ProtocolException {
        JsonElement value = root.get(name);
        if (value == null) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) fail("invalid_" + name);
        return value.getAsBoolean();
    }

    private static int requiredInt(JsonObject root, String name) throws ProtocolException {
        long value = requiredLong(root, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) fail("invalid_" + name);
        return (int) value;
    }

    private static long requiredLong(JsonObject root, String name) throws ProtocolException {
        Long value = optionalLong(root, name);
        if (value == null) fail("missing_" + name);
        return value;
    }

    private static Long optionalLong(JsonObject root, String name) throws ProtocolException {
        JsonElement value = root.get(name);
        if (value == null) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) fail("invalid_" + name);
        try {
            long parsed = new BigDecimal(value.getAsString()).longValueExact();
            if (parsed < 0) fail("invalid_" + name);
            return parsed;
        } catch (ArithmeticException | NumberFormatException invalid) {
            fail("invalid_" + name);
            return null;
        }
    }

    private static UUID requiredUuid(JsonObject root, String name) throws ProtocolException {
        UUID value = optionalUuid(root, name);
        if (value == null) fail("missing_" + name);
        return value;
    }

    private static UUID optionalUuid(JsonObject root, String name) throws ProtocolException {
        String value = optionalString(root, name);
        if (value == null) return null;
        try {
            UUID parsed = UUID.fromString(value);
            if (!ServiceNpcSpawnOperationRequest.isProtocolUuid(parsed)) fail("invalid_" + name);
            return parsed;
        } catch (IllegalArgumentException invalid) {
            fail("invalid_" + name);
            return null;
        }
    }

    private static Instant optionalInstant(JsonObject root, String name) throws ProtocolException {
        String value = optionalString(root, name);
        if (value == null) return null;
        try { return Instant.parse(value); } catch (DateTimeParseException invalid) {
            fail("invalid_" + name);
            return null;
        }
    }

    private static <E extends Enum<E>> E optionalEnum(JsonObject root, String name, Class<E> type)
        throws ProtocolException {
        String value = optionalString(root, name);
        if (value == null) return null;
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException invalid) {
            fail("invalid_" + name);
            return null;
        }
    }

    private static String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null) return null;
        for (var entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)
                && entry.getValue() != null && !entry.getValue().isEmpty()) return entry.getValue().getFirst();
        }
        return null;
    }

    private static Duration parseRetryAfter(String value) {
        if (value == null) return null;
        try {
            long seconds = Long.parseLong(value.trim());
            if (seconds < 0) return null;
            return Duration.ofSeconds(Math.min(seconds, MAX_RETRY_AFTER.toSeconds()));
        } catch (NumberFormatException ignored) {
            try {
                Duration delay = Duration.between(Instant.now(), ZonedDateTime.parse(
                    value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME
                ).toInstant());
                if (delay.isNegative()) return Duration.ZERO;
                return delay.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : delay;
            } catch (DateTimeParseException invalid) {
                return null;
            }
        }
    }

    private static void fail(String code) throws ProtocolException { throw new ProtocolException(code); }
    private static final class ProtocolException extends Exception {
        private final String safeCode;
        private ProtocolException(String safeCode) { this.safeCode = safeCode; }
    }
}
