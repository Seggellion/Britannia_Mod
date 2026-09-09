package com.seggellion.britannia_mod.quest.handin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Contract {@code quest_item_handin} version 1 (protocol section 1.5): the {@code handin} block
 * Rails publishes with {@code result: "handin_required"}, the body this server posts to
 * {@code /api/v2/quest_item_handins/:handin_uuid/result}, the reconciliation round trip, and the
 * envelopes Rails answers with.
 *
 * <p>The request encoder writes the frozen fixture's exact layout --
 * {@code quest_contract/v1/handin_result_request.json} -- rather than a compact object, and its
 * unit test byte-compares the two. That is not tidiness: the removal proof must be replayable
 * <i>identically</i> on every retry, and the cheapest way to be sure of that is for the bytes to be
 * a pure function of the stored evidence, with a frozen file to check the function against.
 *
 * <p>Everything here refuses rather than guesses. A malformed demand is not a hand-in this mod will
 * take an item for, and a malformed answer is not one it will finish a quest on.
 */
public final class QuestItemHandinProtocol {

    public static final int PROTOCOL_VERSION = 1;

    /** The transition envelope's {@code result} that means "prepared, nothing advanced". */
    public static final String RESULT_HANDIN_REQUIRED = "handin_required";

    /** The key the {@code handin} block hangs off in that envelope. */
    public static final String HANDIN_KEY = "handin";

    public static final int MAX_RESULT_RESPONSE_BYTES = 256 * 1024;
    public static final int MAX_RECONCILE_RESPONSE_BYTES = 512 * 1024;

    /** {@code Reconcile::LIMIT}. Asking for more than this is enumerating, not reconciling. */
    public static final int MAX_RECONCILE_UUIDS = 50;

    /** {@code ResultContract::MAX_MISSING}. */
    public static final int MAX_MISSING_ITEMS = 8;

    /** The 404 that hides "unknown", "wrong shard" and "wrong player" behind one answer. */
    public static final String ERROR_HANDIN_NOT_FOUND = "handin_not_found";

    /** The 400 a malformed body earns. No retry of the same bytes can change it. */
    public static final String ERROR_INVALID_HANDIN_RESULT = "invalid_handin_result";

    private QuestItemHandinProtocol() {}

    // --- results --------------------------------------------------------------------------

    /**
     * Every answer {@code Confirm} can give (protocol section 1.5.6, {@code Confirm::RESULTS}).
     *
     * <p>Three of them are terminal for a transaction that actually removed something:
     * {@link #CONSUMED} and {@link #DUPLICATE} carry the completed quest response, and
     * {@link #CANCELLED_REFUNDED} says the items are coming back through the ordinary reward
     * delivery ledger. The rest answer a confirmation that reported no removal, or refuse the
     * evidence -- and none of them may ever cause a second removal.
     */
    public enum Result {
        CONSUMED("consumed"),
        DUPLICATE("duplicate"),
        ITEMS_MISSING("items_missing"),
        CANCELLED("cancelled"),
        CANCELLED_REFUNDED("cancelled_refunded"),
        EVIDENCE_REJECTED("evidence_rejected"),
        REJECTED("rejected");

        private final String wireName;

        Result(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        /** Whether this answer carries the ordinary quest response that must be applied once. */
        public boolean carriesCompletion() {
            return this == CONSUMED || this == DUPLICATE;
        }

        /**
         * Whether Rails' answer would be the same on a retry of the same body, so there is nothing
         * left to ask.
         *
         * <p>{@link #ITEMS_MISSING} and {@link #EVIDENCE_REJECTED} deliberately are not: Rails
         * leaves the row exactly as it was for both, so a shard that garbled a retry can still send
         * the true report and be answered properly.
         */
        public boolean terminal() {
            return this == CONSUMED || this == DUPLICATE || this == CANCELLED_REFUNDED
                    || this == CANCELLED || this == REJECTED;
        }

        /**
         * Whether this answer proves the protocol's own invariant for a removal that has already
         * happened: either the transition completed exactly once, or the exact items are being
         * durably refunded exactly once.
         *
         * <p>Deliberately narrower than {@link #terminal()}, and the difference is the whole point.
         * {@link #REJECTED} is a settled answer -- Rails holds no such transaction for this shard
         * and player -- but for a local record that proves a removal it settles nothing: no
         * completion and no compensation can follow it. A row in that position is preserved for
         * diagnosis, never closed as though it were finished. {@link #CANCELLED} is the same shape
         * seen from the other side: it is only ever answered to a report that took nothing.
         */
        public boolean settlesRemoval() {
            return this == CONSUMED || this == DUPLICATE || this == CANCELLED_REFUNDED;
        }

        public static Result fromWireName(String value) {
            for (Result result : values()) {
                if (result.wireName.equals(value)) return result;
            }
            throw new MalformedHandinException("unknown hand-in result");
        }
    }

    /** What reconciliation says happened (protocol section 1.5.6, {@code Reconcile::OUTCOMES}). */
    public enum ReconcileOutcome {
        CONSUMED("consumed"),
        CANCELLED_REFUNDED("cancelled_refunded"),
        CANCELLED("cancelled"),
        PENDING("pending"),
        UNKNOWN("unknown");

        private final String wireName;

        ReconcileOutcome(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static ReconcileOutcome fromWireName(String value) {
            for (ReconcileOutcome outcome : values()) {
                if (outcome.wireName.equals(value)) return outcome;
            }
            throw new MalformedHandinException("unknown reconcile outcome");
        }
    }

    // --- records --------------------------------------------------------------------------

    /** One line of "what the player is still short of". */
    public record MissingItem(String itemId, int count) {
        public MissingItem {
            itemId = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
            if (!QuestHandinRequirement.ITEM_ID_PATTERN.matcher(itemId).matches()) {
                throw new IllegalArgumentException("missing item must be a namespaced item id");
            }
            if (count < 0 || count > QuestHandinRequirement.MAX_COUNT) {
                throw new IllegalArgumentException("missing count must be 0..1024");
            }
        }
    }

    /**
     * The {@code handin} block: the demand Rails published, and the transaction it belongs to.
     * Held whole because every field of it is persisted -- the confirmation this server will send,
     * possibly after a restart, is built from this and nothing else.
     */
    public record Demand(UUID handinUuid, String choice, List<QuestHandinRequirement> requirements,
                         String missingMessage) {
        public Demand {
            Objects.requireNonNull(handinUuid, "handinUuid");
            choice = choice == null ? "" : choice.trim();
            requirements = List.copyOf(requirements);
            if (requirements.isEmpty()
                    || requirements.size() > QuestHandinRequirement.MAX_REQUIREMENTS) {
                throw new IllegalArgumentException("a hand-in carries 1..8 requirements");
            }
            missingMessage = missingMessage == null ? "" : missingMessage;
        }
    }

    /** The confirmation body. {@code removedItems} is empty exactly when {@code removed} is false. */
    public record ConfirmationRequest(UUID handinUuid, UUID playerUuid, boolean removed,
                                      List<QuestHandinRemoval> removedItems,
                                      List<MissingItem> missingItems, UUID requestUuid) {
        public ConfirmationRequest {
            Objects.requireNonNull(handinUuid, "handinUuid");
            Objects.requireNonNull(playerUuid, "playerUuid");
            Objects.requireNonNull(requestUuid, "requestUuid");
            removedItems = List.copyOf(removedItems);
            missingItems = List.copyOf(missingItems);
            if (removed && (removedItems.isEmpty()
                    || removedItems.size() > QuestHandinRequirement.MAX_REQUIREMENTS)) {
                throw new IllegalArgumentException("a removal carries 1..8 proof entries");
            }
            // Rails refuses a refusal that carries proof, because guessing which half is true would
            // decide whether the player keeps an item. Refuse it here rather than on the wire.
            if (!removed && !removedItems.isEmpty()) {
                throw new IllegalArgumentException("removal proof may not accompany removed: false");
            }
            if (missingItems.size() > MAX_MISSING_ITEMS) {
                throw new IllegalArgumentException("missing_items carries at most 8 entries");
            }
        }
    }

    /**
     * Rails' answer. {@code response} is the nested ordinary quest response for a completion, or
     * the refund envelope for a cancellation; it is kept as raw JSON because applying it is the
     * existing transition machinery's job, not this codec's.
     */
    public record ConfirmationResponse(Result result, UUID handinUuid, String state, String reason,
                                       List<QuestHandinRemoval> removedItems,
                                       List<MissingItem> missingItems, JsonObject response,
                                       UUID refundDeliveryUuid) {}

    /** One row of a reconciliation answer. */
    public record ReconcileEntry(UUID handinUuid, ReconcileOutcome outcome,
                                 List<QuestHandinRemoval> removedItems, String cancelledReason,
                                 JsonObject response) {}

    public record ReconcileResponse(List<ReconcileEntry> handins) {}

    // --- the demand -----------------------------------------------------------------------

    /**
     * Reads the {@code handin} block out of a {@code handin_required} transition envelope.
     *
     * <p>Refuses the whole demand on any malformed entry. A partially understood hand-in is the one
     * shape that must never reach an inventory: this mod would take what it recognised and report a
     * proof Rails answers {@code evidence_rejected} to, leaving the player short with the quest
     * unfinished and no refund owed.
     */
    public static Demand parseDemand(JsonObject transitionRoot) {
        if (transitionRoot == null || !transitionRoot.has(HANDIN_KEY)
                || !transitionRoot.get(HANDIN_KEY).isJsonObject()) {
            throw new MalformedHandinException("handin_required carries no handin block");
        }
        JsonObject handin = transitionRoot.getAsJsonObject(HANDIN_KEY);
        UUID handinUuid = requireUuid(handin, "handin_uuid");
        String choice = optionalString(handin, "choice");
        String missingMessage = optionalString(handin, "missing_message");

        if (!handin.has("requires") || !handin.get("requires").isJsonArray()) {
            throw new MalformedHandinException("handin.requires must be an array");
        }
        JsonArray requires = handin.getAsJsonArray("requires");
        if (requires.isEmpty() || requires.size() > QuestHandinRequirement.MAX_REQUIREMENTS) {
            throw new MalformedHandinException("handin.requires must carry 1..8 entries");
        }
        List<QuestHandinRequirement> requirements = new ArrayList<>(requires.size());
        for (int index = 0; index < requires.size(); index++) {
            JsonElement raw = requires.get(index);
            if (!raw.isJsonObject()) {
                throw new MalformedHandinException("handin.requires entry must be an object");
            }
            requirements.add(parseRequirement(raw.getAsJsonObject(), index));
        }
        try {
            return new Demand(handinUuid, choice, requirements, missingMessage);
        } catch (IllegalArgumentException invalid) {
            throw new MalformedHandinException(invalid.getMessage());
        }
    }

    private static QuestHandinRequirement parseRequirement(JsonObject entry, int index) {
        boolean namesItem = entry.has("item");
        boolean namesResolver = entry.has("resolver");
        // Rails' validator refuses an entry carrying both on the way out; a row edited past that
        // guard would be ambiguous about what to take, so it is refused here too.
        if (namesItem == namesResolver) {
            throw new MalformedHandinException("a requirement names exactly one of item or resolver");
        }
        int count = entry.has("count") ? requireInt(entry, "count") : 1;
        try {
            if (namesItem) {
                return new QuestHandinRequirement.Literal(index, requireString(entry, "item"), count);
            }
            return new QuestHandinRequirement.Resolver(index, requireString(entry, "resolver"),
                    requireString(entry, "flag"), requireString(entry, "flag_value"), count);
        } catch (IllegalArgumentException invalid) {
            throw new MalformedHandinException(invalid.getMessage());
        }
    }

    // --- the confirmation -----------------------------------------------------------------

    /**
     * The confirmation body, in the frozen fixture's layout: two-space indentation, one field per
     * line in the frozen order, LF endings, a trailing newline.
     *
     * <p>Assembled as text rather than through Gson so the bytes are fully determined by the
     * arguments and can be byte-compared against the fixture. That is safe because every value that
     * reaches it is a canonical UUID, an integer within a checked bound, or a token that
     * {@link QuestHandinRequirement} has already refused quotes, backslashes and control characters
     * in -- so there is nothing here that could need escaping.
     */
    public static byte[] encodeConfirmation(ConfirmationRequest request) {
        Objects.requireNonNull(request, "request");
        StringBuilder body = new StringBuilder(256);
        body.append("{\n");
        body.append("  \"protocol_version\": ").append(PROTOCOL_VERSION).append(",\n");
        body.append("  \"player_uuid\": \"").append(request.playerUuid()).append("\",\n");
        body.append("  \"removed\": ").append(request.removed()).append(",\n");
        if (request.removed()) {
            body.append("  \"removed_items\": [\n");
            for (int index = 0; index < request.removedItems().size(); index++) {
                QuestHandinRemoval removal = request.removedItems().get(index);
                body.append("    {\n");
                body.append("      \"item\": \"").append(removal.itemId()).append("\",\n");
                body.append("      \"count\": ").append(removal.count()).append(",\n");
                body.append("      \"requirement_index\": ").append(removal.requirementIndex());
                if (removal.answersResolver()) {
                    body.append(",\n");
                    body.append("      \"resolver\": \"").append(removal.resolver()).append("\",\n");
                    body.append("      \"flag_value\": \"").append(removal.flagValue()).append("\"\n");
                } else {
                    body.append('\n');
                }
                body.append("    }").append(index + 1 < request.removedItems().size() ? ",\n" : "\n");
            }
            body.append("  ],\n");
        } else if (!request.missingItems().isEmpty()) {
            body.append("  \"missing_items\": [\n");
            for (int index = 0; index < request.missingItems().size(); index++) {
                MissingItem missing = request.missingItems().get(index);
                body.append("    { \"item\": \"").append(missing.itemId())
                        .append("\", \"count\": ").append(missing.count()).append(" }")
                        .append(index + 1 < request.missingItems().size() ? ",\n" : "\n");
            }
            body.append("  ],\n");
        }
        body.append("  \"request_uuid\": \"").append(request.requestUuid()).append("\"\n");
        body.append("}\n");
        return body.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static ConfirmationResponse parseConfirmation(byte[] body) {
        JsonObject root = object(body);
        requireVersion(root);
        Result result = Result.fromWireName(requireString(root, "result"));
        // A rejection names no transaction at all: every reason answers identically so a shard
        // cannot probe another shard's transaction ids by the shape of its own errors.
        UUID handinUuid = root.has("handin_uuid") ? requireUuid(root, "handin_uuid") : null;
        return new ConfirmationResponse(result, handinUuid, optionalString(root, "state"),
                optionalString(root, "reason"), parseRemovedItems(root), parseMissingItems(root),
                optionalObject(root, "response"), parseRefundDeliveryUuid(root));
    }

    private static UUID parseRefundDeliveryUuid(JsonObject root) {
        JsonObject refund = optionalObject(root, "refund_delivery");
        return refund == null ? null : requireUuid(refund, "delivery_uuid");
    }

    // --- reconciliation -------------------------------------------------------------------

    /**
     * The reconciliation body. Read-only on the Rails side by design: it reports and never writes,
     * so it can never cause a second removal -- which is the one thing that must not happen to a
     * shard that has already taken the items.
     */
    public static byte[] encodeReconcile(UUID playerUuid, List<UUID> handinUuids) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        List<UUID> bounded = handinUuids.size() > MAX_RECONCILE_UUIDS
                ? handinUuids.subList(0, MAX_RECONCILE_UUIDS) : handinUuids;
        StringBuilder body = new StringBuilder(128);
        body.append("{\n");
        body.append("  \"protocol_version\": ").append(PROTOCOL_VERSION).append(",\n");
        body.append("  \"player_uuid\": \"").append(playerUuid).append("\",\n");
        body.append("  \"handin_uuids\": [");
        for (int index = 0; index < bounded.size(); index++) {
            body.append(index == 0 ? "" : ", ").append('"').append(bounded.get(index)).append('"');
        }
        body.append("]\n");
        body.append("}\n");
        return body.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static ReconcileResponse parseReconcile(byte[] body) {
        JsonObject root = object(body);
        requireVersion(root);
        if (!root.has("handins") || !root.get("handins").isJsonArray()) {
            throw new MalformedHandinException("handins must be an array");
        }
        JsonArray rows = root.getAsJsonArray("handins");
        if (rows.size() > MAX_RECONCILE_UUIDS) {
            throw new MalformedHandinException("handins is outside its bound");
        }
        List<ReconcileEntry> entries = new ArrayList<>(rows.size());
        for (JsonElement raw : rows) {
            if (!raw.isJsonObject()) {
                throw new MalformedHandinException("handins entry must be an object");
            }
            JsonObject row = raw.getAsJsonObject();
            entries.add(new ReconcileEntry(requireUuid(row, "handin_uuid"),
                    ReconcileOutcome.fromWireName(requireString(row, "outcome")),
                    parseRemovedItems(row), optionalString(row, "cancelled_reason"),
                    optionalObject(row, "response")));
        }
        return new ReconcileResponse(List.copyOf(entries));
    }

    // --- shared parsing -------------------------------------------------------------------

    private static List<QuestHandinRemoval> parseRemovedItems(JsonObject parent) {
        if (!parent.has("removed_items") || parent.get("removed_items").isJsonNull()) {
            return List.of();
        }
        if (!parent.get("removed_items").isJsonArray()) {
            throw new MalformedHandinException("removed_items must be an array");
        }
        JsonArray rows = parent.getAsJsonArray("removed_items");
        if (rows.size() > QuestHandinRequirement.MAX_REQUIREMENTS) {
            throw new MalformedHandinException("removed_items is outside its bound");
        }
        List<QuestHandinRemoval> removals = new ArrayList<>(rows.size());
        for (JsonElement raw : rows) {
            if (!raw.isJsonObject()) {
                throw new MalformedHandinException("removed_items entry must be an object");
            }
            JsonObject row = raw.getAsJsonObject();
            String resolver = optionalString(row, "resolver");
            String flagValue = optionalString(row, "flag_value");
            try {
                removals.add(new QuestHandinRemoval(requireInt(row, "requirement_index"),
                        requireString(row, "item"), requireInt(row, "count"),
                        resolver.isEmpty() ? null : resolver,
                        flagValue.isEmpty() ? null : flagValue));
            } catch (IllegalArgumentException invalid) {
                throw new MalformedHandinException(invalid.getMessage());
            }
        }
        return Collections.unmodifiableList(removals);
    }

    private static List<MissingItem> parseMissingItems(JsonObject parent) {
        if (!parent.has("missing_items") || parent.get("missing_items").isJsonNull()) {
            return List.of();
        }
        if (!parent.get("missing_items").isJsonArray()) {
            throw new MalformedHandinException("missing_items must be an array");
        }
        JsonArray rows = parent.getAsJsonArray("missing_items");
        if (rows.size() > MAX_MISSING_ITEMS) {
            throw new MalformedHandinException("missing_items is outside its bound");
        }
        List<MissingItem> missing = new ArrayList<>(rows.size());
        for (JsonElement raw : rows) {
            if (!raw.isJsonObject()) {
                throw new MalformedHandinException("missing_items entry must be an object");
            }
            JsonObject row = raw.getAsJsonObject();
            try {
                missing.add(new MissingItem(requireItemId(row), requireInt(row, "count")));
            } catch (IllegalArgumentException invalid) {
                throw new MalformedHandinException(invalid.getMessage());
            }
        }
        return Collections.unmodifiableList(missing);
    }

    /** The {@code error} code of an error envelope, or an empty string when there is none. */
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

    private static void requireVersion(JsonObject root) {
        if (requireInt(root, "protocol_version") != PROTOCOL_VERSION) {
            throw new MalformedHandinException("unsupported protocol_version");
        }
    }

    static JsonObject object(byte[] body) {
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new MalformedHandinException("body must be an object");
            return parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException malformed) {
            throw new MalformedHandinException("body is not JSON");
        }
    }

    static int requireInt(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive()
                || !parent.getAsJsonPrimitive(key).isNumber()) {
            throw new MalformedHandinException(key + " must be an integer");
        }
        String raw = parent.get(key).getAsString();
        if (!raw.matches("-?(0|[1-9][0-9]*)")) {
            throw new MalformedHandinException(key + " must be an integer");
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException overflow) {
            throw new MalformedHandinException(key + " is outside its bound");
        }
    }

    static String requireString(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive()
                || !parent.getAsJsonPrimitive(key).isString()) {
            throw new MalformedHandinException(key + " must be a string");
        }
        String value = parent.get(key).getAsString();
        if (value.isBlank() || value.length() > 255) {
            throw new MalformedHandinException(key + " is outside its size bound");
        }
        return value;
    }

    /**
     * An item id under either spelling.
     *
     * <p>Two vocabularies meet on this field. Everything Rails <i>publishes</i> says {@code item};
     * its ledger says {@code id}, because {@code quest_reward_deliveries.items} does and a hand-in
     * and its refund have to be comparable without translating one first. The shortfall list is the
     * one place the ledger spelling can reach the wire untranslated, so this reads either -- exactly
     * as Rails' own {@code ResultContract} does for the same field on the way in.
     *
     * <p>Being strict here would have cost more than it bought: a well-formed answer this parser
     * refused would become {@code malformed_response}, and a transaction whose items are already
     * gone retries a malformed-looking exchange forever.
     */
    static String requireItemId(JsonObject parent) {
        if (parent.has("item")) return requireString(parent, "item");
        return requireString(parent, "id");
    }

    /** A present, non-blank string, or {@code ""}. Never throws on absence. */
    static String optionalString(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonPrimitive()
                || !parent.getAsJsonPrimitive(key).isString()) {
            return "";
        }
        String value = parent.get(key).getAsString();
        return value.length() > 4096 ? "" : value;
    }

    static JsonObject optionalObject(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) return null;
        return parent.getAsJsonObject(key);
    }

    static UUID requireUuid(JsonObject parent, String key) {
        UUID parsed = parseCanonicalUuid(requireString(parent, key));
        if (parsed == null) throw new MalformedHandinException(key + " must be a UUID");
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

    public static final class MalformedHandinException extends RuntimeException {
        public MalformedHandinException(String message) {
            super(message);
        }
    }
}
