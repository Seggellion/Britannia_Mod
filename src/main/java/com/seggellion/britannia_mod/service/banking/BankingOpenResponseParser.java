package com.seggellion.britannia_mod.service.banking;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * A defensive bound on the parsed list, not a real business limit -- Rails paginates via
     * {@code next_cursor} (not yet followed by this client) long before a real account could
     * plausibly reach this many available items in one page.
     */
    private static final int MAX_BANK_ITEMS = 10_000;
    /** Mirrors the bound Rails itself enforces on display_name (docs/banking_item_transfer.md). */
    private static final int MAX_BANK_ITEM_NAME_LENGTH = 255;

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

        JsonElement bankItemsElement = root.get("bank_items");
        if (bankItemsElement == null || !bankItemsElement.isJsonObject()) fail("missing_bank_items");
        List<BankItemSummary> bankItems = parseBankItems(bankItemsElement.getAsJsonObject());

        return new BankingOpenClientResult.Success(account, bankItems);
    }

    /**
     * Rails' real {@code bank_items} envelope (Milestone 9 Rails Slice 1): every {@code
     * available} item the account holds, list-view only -- no {@code payload} field exists
     * here (that only appears in {@code banking/withdrawal/prepare}'s response, per
     * docs/banking_item_transfer.md), so only {@code public_id} and {@code weight} are read.
     * {@code next_cursor} is not yet paginated through by this client (this slice's own scope
     * does not require it -- matches Rails' own admission of the same, in its
     * {@code bank_items_envelope} doc comment).
     */
    private static List<BankItemSummary> parseBankItems(JsonObject bankItemsEnvelope) throws ProtocolException {
        JsonElement itemsElement = bankItemsEnvelope.get("items");
        if (itemsElement == null || !itemsElement.isJsonArray()) fail("missing_bank_items_list");
        JsonArray itemsArray = itemsElement.getAsJsonArray();
        if (itemsArray.size() > MAX_BANK_ITEMS) fail("too_many_bank_items");

        List<BankItemSummary> items = new ArrayList<>(itemsArray.size());
        for (JsonElement itemElement : itemsArray) {
            if (!itemElement.isJsonObject()) fail("invalid_bank_item");
            JsonObject item = itemElement.getAsJsonObject();
            UUID publicId = requiredUuid(item, "public_id");
            double weight = requiredNumber(item, "weight");
            if (weight < 0) fail("invalid_bank_item_weight");
            // Milestone 18: identity, when Rails has it. An absent key is the normal case, not an
            // error -- Rails omits these entirely for a row deposited before item identity
            // existed, and will keep doing so for every deposit a pre-Milestone-17 client makes.
            // A malformed value degrades to absent rather than failing the whole response: a name
            // is a courtesy, and losing the player's entire account view over one would be a far
            // worse trade than showing the fallback.
            items.add(new BankItemSummary(publicId, weight, lenientName(item), lenientCount(item), lenientItemKey(item),
                    lenientChequeRedeemable(item)));
        }
        return items;
    }

    /** Bounded here as well as by Rails -- this client does not assume it is talking to a Rails it trusts. */
    private static String lenientName(JsonObject item) {
        JsonElement element = item.get("display_name");
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        String value = element.getAsString();
        return value.length() > MAX_BANK_ITEM_NAME_LENGTH ? value.substring(0, MAX_BANK_ITEM_NAME_LENGTH) : value;
    }

    private static Integer lenientCount(JsonObject item) {
        JsonElement element = item.get("count");
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        int value = element.getAsInt();
        return value >= 1 ? value : null;
    }

    /**
     * Milestone 10: the registry id ({@code "minecraft:diamond_sword"}), read with the same
     * lenient treatment the other identity fields get -- absent or the wrong JSON type degrades
     * to {@code null}, never fails the account view (design §9.5.2).
     *
     * <p>One deliberate divergence from {@link #lenientName}: an over-long value is <b>dropped,
     * not truncated</b>. Truncating a display name loses letters; truncating a registry id
     * produces a <em>different id</em>, which could in principle resolve to a different item --
     * the wrong icon rather than the unknown one. No syntax check happens here: this class stays
     * Minecraft-free, and the resolver owns the one definition of malformed.
     */
    private static String lenientItemKey(JsonObject item) {
        JsonElement element = item.get("item_key");
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        String value = element.getAsString();
        return value.length() > MAX_BANK_ITEM_NAME_LENGTH ? null : value;
    }

    /**
     * Whether this stored row is a cheque that can be cashed from the vault right now.
     *
     * <p>Three-valued on purpose, and the same lenient rule as its siblings: {@code null} means
     * "Rails said nothing", which is every ordinary item and every row deposited before the link
     * existed. Only an explicit {@code true} offers the gesture -- a missing or malformed value
     * must never light up an action that would then fail, which is the dishonest affordance this
     * epic has removed everywhere else.
     */
    private static Boolean lenientChequeRedeemable(JsonObject item) {
        JsonElement element = item.get("cheque_redeemable");
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            return null;
        }
        return element.getAsBoolean();
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
