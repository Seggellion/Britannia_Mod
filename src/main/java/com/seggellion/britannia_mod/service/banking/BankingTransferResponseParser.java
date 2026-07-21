package com.seggellion.britannia_mod.service.banking;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Parses raw {@code banking/deposit/prepare}, {@code banking/confirm}, and {@code
 * banking/cancel} HTTP responses, mirroring {@link BankingOpenResponseParser}: cross-validates
 * status/success/retryable against what Rails itself defines for that outcome
 * (docs/banking_item_transfer.md), and fails hard (never guesses) on anything unexpected.
 *
 * <p>Deliberately does not deep-parse every field of the nested {@code operation}/{@code
 * bank_item} objects -- only what {@link BankingDepositProxyService} actually needs downstream
 * (the two identifiers on a successful prepare; nothing beyond the outcome itself for
 * confirm/cancel, since Rails already encodes "confirmed" vs. "reconciliation_required" as
 * distinct top-level outcomes, not a state field a caller must separately inspect).
 *
 * <p>Every {@code parse*} method here runs on whichever {@code ServerHttpExecutor} pool thread
 * happened to pick up that request -- concurrent deposit attempts for different players/slots
 * can genuinely call into this class from different threads at once (see
 * {@link BankingDepositProxyService}'s own concurrency handling), so nothing here may hold
 * mutable state across a call. A failure's safe code is threaded back purely through a thrown
 * {@link EnvelopeFailure}, never a shared field.
 */
public final class BankingTransferResponseParser {
    private static final int PROTOCOL_VERSION = 1;

    private BankingTransferResponseParser() {
    }

    public static BankingDepositPrepareResult parsePrepare(int status, byte[] body) {
        final Envelope envelope;
        try {
            envelope = parseEnvelope(status, body);
        } catch (EnvelopeFailure failure) {
            return new BankingDepositPrepareResult.TransportFailure(failure.safeCode());
        }
        if (envelope.outcome != BankingTransferOutcome.PREPARED) {
            return new BankingDepositPrepareResult.Rejected(envelope.outcome, envelope.retryable);
        }

        try {
            UUID operationPublicId = requiredUuid(requiredObject(envelope.root, "operation"), "public_id");
            UUID bankItemPublicId = requiredUuid(requiredObject(envelope.root, "bank_item"), "public_id");
            return new BankingDepositPrepareResult.Success(operationPublicId, bankItemPublicId);
        } catch (ProtocolException malformed) {
            return new BankingDepositPrepareResult.TransportFailure("malformed_protocol_response");
        }
    }

    public static BankingConfirmResult parseConfirm(int status, byte[] body) {
        final Envelope envelope;
        try {
            envelope = parseEnvelope(status, body);
        } catch (EnvelopeFailure failure) {
            return new BankingConfirmResult.TransportFailure(failure.safeCode());
        }
        return switch (envelope.outcome) {
            case CONFIRMED -> new BankingConfirmResult.Confirmed();
            case RECONCILIATION_REQUIRED -> new BankingConfirmResult.ReconciliationRequired();
            default -> new BankingConfirmResult.Rejected(envelope.outcome, envelope.retryable);
        };
    }

    public static BankingCancelResult parseCancel(int status, byte[] body) {
        final Envelope envelope;
        try {
            envelope = parseEnvelope(status, body);
        } catch (EnvelopeFailure failure) {
            return new BankingCancelResult.TransportFailure(failure.safeCode());
        }
        if (envelope.outcome == BankingTransferOutcome.CANCELLED) {
            return new BankingCancelResult.Cancelled();
        }
        return new BankingCancelResult.Rejected(envelope.outcome, envelope.retryable);
    }

    private static Envelope parseEnvelope(int status, byte[] body) throws EnvelopeFailure {
        if (status == 404) {
            throw new EnvelopeFailure("endpoint_unavailable");
        }

        final JsonObject root;
        try {
            root = JsonParser.parseString(decodeUtf8(body)).getAsJsonObject();
        } catch (RuntimeException | CharacterCodingException malformed) {
            throw new EnvelopeFailure(genericFailureCodeForStatus(status));
        }

        try {
            int version = requiredInt(root, "protocol_version");
            if (version != PROTOCOL_VERSION) fail("unsupported_response_protocol");
            boolean success = requiredBoolean(root, "success");
            boolean retryable = requiredBoolean(root, "retryable");
            BankingTransferOutcome outcome = BankingTransferOutcome.parse(requiredString(root, "outcome"));
            if (outcome == null) fail("unknown_response_outcome");

            if (status != outcome.expectedHttpStatus()) fail("inconsistent_protocol_envelope");
            boolean expectedSuccess = outcome == BankingTransferOutcome.PREPARED
                    || outcome == BankingTransferOutcome.CONFIRMED
                    || outcome == BankingTransferOutcome.CANCELLED;
            if (success != expectedSuccess) fail("inconsistent_protocol_envelope");
            if (retryable != outcome.expectedRetryable()) fail("inconsistent_protocol_envelope");

            return new Envelope(root, outcome, retryable);
        } catch (ProtocolException incompatible) {
            throw new EnvelopeFailure(genericFailureCodeForStatus(status));
        }
    }

    private static String genericFailureCodeForStatus(int status) {
        if (status == 401 || status == 403) return "authentication_rejected";
        if (status >= 500) return "server_failure_" + status;
        return "malformed_protocol_response";
    }

    private record Envelope(JsonObject root, BankingTransferOutcome outcome, boolean retryable) {
    }

    private static JsonObject requiredObject(JsonObject root, String name) throws ProtocolException {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonObject()) fail("missing_or_invalid_" + name);
        return value.getAsJsonObject();
    }

    private static String decodeUtf8(byte[] body) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body == null ? new byte[0] : body)).toString();
    }

    private static String requiredString(JsonObject root, String name) throws ProtocolException {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            fail("missing_or_invalid_" + name);
        }
        return value.getAsString();
    }

    private static boolean requiredBoolean(JsonObject root, String name) throws ProtocolException {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            fail("missing_or_invalid_" + name);
        }
        return value.getAsBoolean();
    }

    private static int requiredInt(JsonObject root, String name) throws ProtocolException {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            fail("missing_or_invalid_" + name);
        }
        try {
            return value.getAsInt();
        } catch (NumberFormatException invalid) {
            fail("invalid_" + name);
            return 0;
        }
    }

    private static UUID requiredUuid(JsonObject root, String name) throws ProtocolException {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            fail("missing_or_invalid_" + name);
        }
        try {
            return UUID.fromString(value.getAsString());
        } catch (IllegalArgumentException invalid) {
            fail("invalid_" + name);
            return null;
        }
    }

    private static void fail(String code) throws ProtocolException {
        throw new ProtocolException(code);
    }

    private static final class ProtocolException extends Exception {
        private ProtocolException(String safeCode) {
            super(safeCode);
        }
    }

    /** Carries the safe failure code for a response that could not be turned into an {@link Envelope}. */
    private static final class EnvelopeFailure extends Exception {
        private EnvelopeFailure(String safeCode) {
            super(safeCode);
        }

        private String safeCode() {
            return getMessage();
        }
    }
}
