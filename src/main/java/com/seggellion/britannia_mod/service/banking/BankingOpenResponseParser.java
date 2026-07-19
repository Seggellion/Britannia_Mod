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
 * Parses a raw {@code banking/open} HTTP response into a {@link BankingOpenClientResult},
 * mirroring {@link com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnResponseParser}:
 * a closed outcome enum, cross-validation of status/success/retryable against what Rails
 * itself defines for that outcome, and a hard failure (never a guess) on anything
 * unexpected. Every field name matches docs/banking_open.md exactly.
 */
public final class BankingOpenResponseParser {
    private static final int PROTOCOL_VERSION = 1;

    private BankingOpenResponseParser() {
    }

    public static BankingOpenClientResult parse(int status, byte[] body) {
        if (status == 404) {
            return new BankingOpenClientResult.TransportFailure("endpoint_unavailable");
        }

        final JsonObject root;
        try {
            root = JsonParser.parseString(decodeUtf8(body)).getAsJsonObject();
        } catch (RuntimeException | CharacterCodingException malformed) {
            return genericFailureForStatus(status);
        }

        try {
            return parseProtocol(status, root);
        } catch (ProtocolException incompatible) {
            return genericFailureForStatus(status);
        }
    }

    private static BankingOpenClientResult parseProtocol(int status, JsonObject root) throws ProtocolException {
        int version = requiredInt(root, "protocol_version");
        if (version != PROTOCOL_VERSION) fail("unsupported_response_protocol");
        boolean success = requiredBoolean(root, "success");
        boolean retryable = requiredBoolean(root, "retryable");
        BankingOpenOutcome outcome = BankingOpenOutcome.parse(requiredString(root, "outcome"));
        if (outcome == null) fail("unknown_response_outcome");

        if (status != outcome.expectedHttpStatus()) fail("inconsistent_protocol_envelope");
        if (success != (outcome == BankingOpenOutcome.OPENED)) fail("inconsistent_protocol_envelope");
        if (retryable != outcome.expectedRetryable()) fail("inconsistent_protocol_envelope");

        if (outcome != BankingOpenOutcome.OPENED) {
            return new BankingOpenClientResult.Rejected(outcome, retryable);
        }

        JsonElement accountElement = root.get("account");
        if (accountElement == null || !accountElement.isJsonObject()) fail("missing_account");
        BankingOpenAccount account = parseAccount(accountElement.getAsJsonObject());

        if (!root.has("bank_items") || !root.get("bank_items").isJsonObject()) fail("missing_bank_items");

        return new BankingOpenClientResult.Success(account);
    }

    private static BankingOpenAccount parseAccount(JsonObject account) throws ProtocolException {
        UUID publicId = requiredUuid(account, "public_id");
        String bankingMode = requiredString(account, "banking_mode");
        if (!"global".equals(bankingMode) && !"city_local".equals(bankingMode)) fail("invalid_banking_mode");
        UUID cityPublicId = optionalUuid(account, "city_public_id");
        if ("global".equals(bankingMode) && cityPublicId != null) fail("unexpected_city_for_global_mode");
        if ("city_local".equals(bankingMode) && cityPublicId == null) fail("missing_city_for_city_local_mode");
        int weightLimit = requiredInt(account, "weight_limit");
        if (weightLimit <= 0) fail("invalid_weight_limit");
        double currentWeight = requiredNumber(account, "current_weight");
        if (currentWeight < 0) fail("invalid_current_weight");
        int goldBalance = requiredInt(account, "gold_balance");
        int silverBalance = requiredInt(account, "silver_balance");
        int copperBalance = requiredInt(account, "copper_balance");
        if (goldBalance < 0 || silverBalance < 0 || copperBalance < 0) fail("invalid_currency_balance");
        long revision = requiredLong(account, "revision");
        if (revision < 1) fail("invalid_revision");

        return new BankingOpenAccount(
                publicId, bankingMode, cityPublicId, weightLimit, currentWeight,
                goldBalance, silverBalance, copperBalance, revision
        );
    }

    private static BankingOpenClientResult genericFailureForStatus(int status) {
        if (status == 401 || status == 403) {
            return new BankingOpenClientResult.TransportFailure("authentication_rejected");
        }
        if (status >= 500) {
            return new BankingOpenClientResult.TransportFailure("server_failure_" + status);
        }
        return new BankingOpenClientResult.TransportFailure("malformed_protocol_response");
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
        long value = requiredLong(root, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) fail("invalid_" + name);
        return (int) value;
    }

    private static long requiredLong(JsonObject root, String name) throws ProtocolException {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            fail("missing_or_invalid_" + name);
        }
        try {
            return value.getAsLong();
        } catch (NumberFormatException invalid) {
            fail("invalid_" + name);
            return 0;
        }
    }

    private static double requiredNumber(JsonObject root, String name) throws ProtocolException {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            fail("missing_or_invalid_" + name);
        }
        return value.getAsDouble();
    }

    private static UUID requiredUuid(JsonObject root, String name) throws ProtocolException {
        UUID value = optionalUuid(root, name);
        if (value == null) fail("missing_" + name);
        return value;
    }

    private static UUID optionalUuid(JsonObject root, String name) throws ProtocolException {
        JsonElement value = root.get(name);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) fail("invalid_" + name);
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
}
