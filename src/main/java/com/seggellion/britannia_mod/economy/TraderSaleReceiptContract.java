package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Normalizes every supported authoritative trader-sale receipt shape before settlement.
 *
 * <p>Rails currently emits a flat transaction from receipt lookup, while some successful POST
 * responses wrap that transaction and duplicate selected fields at the root. Older integrations
 * may also leave the transaction under a {@code receipt} wrapper. Every supplied copy is checked;
 * none is silently preferred when two levels disagree.
 */
final class TraderSaleReceiptContract {
    private static final int MAX_COINS_PER_DENOMINATION = 36 * 64;
    private static final List<String> PAYOUT_FIELDS = List.of("currency_grant", "payout", "currency");

    private TraderSaleReceiptContract() {}

    static Result parse(JsonObject response) {
        if (response == null) return Result.rejected("response is missing");
        try {
            List<Node> nodes = nodes(response);
            requireSuccessful(nodes);

            String transactionId = consistent(nodes, List.of("receipt_id", "transaction_id", "id"),
                    "transaction identity", TraderSaleReceiptContract::normalizeIdentifier, true);
            String key = consistent(nodes, List.of("idempotency_key"), "idempotency key",
                    TraderSaleReceiptContract::normalizeRequiredString, true);
            String player = consistent(nodes, List.of("player_uuid"), "player UUID",
                    TraderSaleReceiptContract::normalizeUuid, true);
            String type = consistent(nodes, List.of("transaction_type"), "transaction type",
                    value -> normalizeRequiredString(value).toLowerCase(Locale.ROOT), true);
            if (!"sell".equals(type)) throw new Invalid("transaction type is not sell");

            Payout payout = payout(nodes);
            boolean replay = consistentBoolean(nodes, "idempotent_replay");
            return Result.accepted(new Receipt(transactionId, key, UUID.fromString(player), type,
                    replay, payout.gold(), payout.silver(), payout.copper()));
        } catch (Invalid invalid) {
            return Result.rejected(invalid.getMessage());
        } catch (RuntimeException malformed) {
            return Result.rejected("malformed receipt contract");
        }
    }

    private static List<Node> nodes(JsonObject root) {
        var nodes = new ArrayList<Node>();
        nodes.add(new Node("root", root));
        JsonObject transaction = optionalObject(root, "transaction", "root.transaction");
        if (transaction != null) nodes.add(new Node("transaction", transaction));
        JsonObject receipt = optionalObject(root, "receipt", "root.receipt");
        if (receipt != null) {
            nodes.add(new Node("receipt", receipt));
            JsonObject receiptTransaction = optionalObject(receipt, "transaction", "root.receipt.transaction");
            if (receiptTransaction != null) nodes.add(new Node("receipt.transaction", receiptTransaction));
        }
        return List.copyOf(nodes);
    }

    private static JsonObject optionalObject(JsonObject parent, String key, String label) {
        if (!parent.has(key)) return null;
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonObject()) {
            throw new Invalid(label + " is not an object");
        }
        return value.getAsJsonObject();
    }

    private static void requireSuccessful(List<Node> nodes) {
        for (Node node : nodes) {
            if (!node.value().has("success")) continue;
            JsonElement value = node.value().get("success");
            if (value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isBoolean()) {
                throw new Invalid("success is malformed at " + node.label());
            }
            if (!value.getAsBoolean()) throw new Invalid("success is false at " + node.label());
        }
    }

    private static String consistent(List<Node> nodes, List<String> fields, String label,
                                     Function<JsonElement, String> normalize, boolean required) {
        Set<String> values = new LinkedHashSet<>();
        for (Node node : nodes) {
            for (String field : fields) {
                if (!node.value().has(field)) continue;
                JsonElement value = node.value().get(field);
                try {
                    values.add(normalize.apply(value));
                } catch (RuntimeException malformed) {
                    throw new Invalid(label + " is malformed at " + node.label() + "." + field);
                }
            }
        }
        if (values.size() > 1) throw new Invalid("conflicting " + label + " fields");
        if (values.isEmpty()) {
            if (required) throw new Invalid(label + " is missing");
            return "";
        }
        return values.iterator().next();
    }

    private static String normalizeIdentifier(JsonElement element) {
        JsonPrimitive primitive = primitive(element);
        if (primitive.isNumber()) {
            BigDecimal number = primitive.getAsBigDecimal().stripTrailingZeros();
            if (number.signum() <= 0 || number.scale() > 0) throw new IllegalArgumentException();
            return number.toPlainString();
        }
        if (!primitive.isString()) throw new IllegalArgumentException();
        return normalizeRequiredString(element);
    }

    private static String normalizeRequiredString(JsonElement element) {
        JsonPrimitive primitive = primitive(element);
        if (!primitive.isString()) throw new IllegalArgumentException();
        String value = primitive.getAsString().trim();
        if (value.isEmpty()) throw new IllegalArgumentException();
        return value;
    }

    private static String normalizeUuid(JsonElement element) {
        String value = normalizeRequiredString(element).replace("-", "");
        if (value.length() != 32) throw new IllegalArgumentException();
        String dashed = value.substring(0, 8) + "-" + value.substring(8, 12) + "-"
                + value.substring(12, 16) + "-" + value.substring(16, 20) + "-" + value.substring(20);
        return UUID.fromString(dashed).toString();
    }

    private static JsonPrimitive primitive(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException();
        }
        return element.getAsJsonPrimitive();
    }

    private static boolean consistentBoolean(List<Node> nodes, String field) {
        Boolean result = null;
        for (Node node : nodes) {
            if (!node.value().has(field)) continue;
            JsonElement value = node.value().get(field);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                throw new Invalid(field + " is malformed at " + node.label());
            }
            boolean current = value.getAsBoolean();
            if (result != null && result != current) throw new Invalid("conflicting " + field + " fields");
            result = current;
        }
        return Boolean.TRUE.equals(result);
    }

    private static Payout payout(List<Node> nodes) {
        Set<Payout> payouts = new LinkedHashSet<>();
        for (Node node : nodes) {
            for (String field : PAYOUT_FIELDS) {
                if (!node.value().has(field)) continue;
                JsonElement value = node.value().get(field);
                if (value == null || !value.isJsonObject()) {
                    throw new Invalid(field + " is malformed at " + node.label());
                }
                payouts.add(payoutObject(value.getAsJsonObject(), node.label() + "." + field));
            }
            if (node.value().has("total_gold") || node.value().has("total_silver")
                    || node.value().has("total_copper")) {
                payouts.add(new Payout(
                        denomination(node.value(), "total_gold", node.label()),
                        denomination(node.value(), "total_silver", node.label()),
                        denomination(node.value(), "total_copper", node.label())));
            }
        }
        if (payouts.isEmpty()) throw new Invalid("payout is missing");
        if (payouts.size() > 1) throw new Invalid("conflicting payout fields");
        Payout payout = payouts.iterator().next();
        if ((long) payout.gold() + payout.silver() + payout.copper() == 0) {
            throw new Invalid("payout is zero");
        }
        return payout;
    }

    private static Payout payoutObject(JsonObject value, String label) {
        return new Payout(
                denomination(value, "gold", label),
                denomination(value, "silver", label),
                denomination(value, "copper", label));
    }

    private static int denomination(JsonObject object, String field, String label) {
        if (!object.has(field)) return 0;
        JsonElement value = object.get(field);
        try {
            JsonPrimitive primitive = primitive(value);
            if (!primitive.isNumber()) throw new IllegalArgumentException();
            int amount = primitive.getAsBigDecimal().intValueExact();
            if (amount < 0) throw new Invalid("negative payout denomination at " + label + "." + field);
            if (amount > MAX_COINS_PER_DENOMINATION) {
                throw new Invalid("oversized payout denomination at " + label + "." + field);
            }
            return amount;
        } catch (ArithmeticException | IllegalArgumentException malformed) {
            throw new Invalid("invalid payout denomination at " + label + "." + field);
        }
    }

    record Receipt(String transactionId, String idempotencyKey, UUID playerUuid, String transactionType,
                   boolean idempotentReplay, int gold, int silver, int copper) {
        String mismatch(String expectedKey, UUID expectedPlayer) {
            if (!idempotencyKey.equals(expectedKey)) return "idempotency key does not match reservation";
            if (!playerUuid.equals(expectedPlayer)) return "player UUID does not match reservation";
            if (!"sell".equals(transactionType)) return "transaction type is not sell";
            return "";
        }
    }

    record Result(Receipt receipt, String rejection) {
        static Result accepted(Receipt receipt) { return new Result(receipt, ""); }
        static Result rejected(String reason) { return new Result(null, reason); }
        boolean accepted() { return receipt != null; }
    }

    private record Node(String label, JsonObject value) {}
    private record Payout(int gold, int silver, int copper) {}
    private static final class Invalid extends RuntimeException {
        Invalid(String message) { super(message); }
    }
}
