package com.seggellion.britannia_mod.quest.handin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability;
import com.seggellion.britannia_mod.player.PlayerDataStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one place a hand-in item is taken (protocol section 1.5).
 *
 * <p>Everything before the mutation is refusal-shaped: parse the demand whole, resolve every
 * requirement whole, count the carried inventory whole, and only then mutate. Everything after it
 * is recovery-shaped: the proof is durable before Rails is told anything, and no answer short of a
 * completion or a Rails-authorised refund is allowed to close the row.
 *
 * <p>The ordering and the crash argument live on {@link QuestHandinLedger}; this class is that
 * document executed. The two are meant to be read together, and any change to one belongs in both.
 *
 * <p>Runs on the server thread. Nothing here is reachable from a client packet except through the
 * player's own claim, which Rails answered {@code handin_required} to -- clients never name an item
 * and never mutate an inventory.
 */
public final class QuestItemHandinService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    /** The client-facing key this server rewrites Rails' {@code handin} block into. */
    public static final String CLIENT_HANDIN_KEY = "handin";

    public static final String STATE_ITEMS_MISSING = "items_missing";
    public static final String STATE_CONSUMED = "consumed";
    public static final String STATE_REFUNDED = "refunded";
    public static final String STATE_UNAVAILABLE = "unavailable";

    private static QuestItemHandinClient client = new QuestItemHandinClient();

    private QuestItemHandinService() {}

    /** Sends one answer back to the screen that asked. Called exactly once per {@link #begin}. */
    @FunctionalInterface
    public interface Reply {
        void send(int statusCode, String body);
    }

    /**
     * Applies an ordinary quest transition response through the existing machinery -- journal,
     * effects, reward delivery, achievement -- and answers with the body to forward.
     *
     * <p>Supplied by the caller rather than reached for, so a hand-in's completion runs the same
     * code every other choice in the game runs. A second copy of that path here would drift from it
     * one fix at a time, which is the mistake Rails' own {@code handin_confirmed} keyword avoids on
     * its side.
     */
    @FunctionalInterface
    public interface CompletionApplier {
        String apply(ServerPlayer player, JsonObject response);
    }

    /** Test seam, mirroring the repository's established service clients. */
    public static void useClientForTesting(QuestItemHandinClient testClient) {
        client = Objects.requireNonNull(testClient, "testClient");
    }

    public static void resetClientForTesting() {
        client = new QuestItemHandinClient();
    }

    /** Whether this transition envelope is a hand-in demand rather than an ordinary answer. */
    public static boolean isHandinRequired(JsonObject root) {
        return root != null && root.has("result") && root.get("result").isJsonPrimitive()
                && QuestItemHandinProtocol.RESULT_HANDIN_REQUIRED.equals(root.get("result").getAsString());
    }

    /**
     * Takes what the hand-in asked for, or explains why it did not.
     *
     * <p>Answers the screen exactly once, whichever branch it takes and however the transport
     * behaves -- a screen left waiting forever is the failure that makes players click again.
     */
    public static void begin(ServerPlayer player, JsonObject railsRoot, CompletionApplier applier,
                             Reply reply) {
        AtomicBoolean answered = new AtomicBoolean();
        Reply once = (status, body) -> {
            if (answered.compareAndSet(false, true)) reply.send(status, body);
        };

        QuestItemHandinProtocol.Demand demand;
        try {
            demand = QuestItemHandinProtocol.parseDemand(railsRoot);
        } catch (QuestItemHandinProtocol.MalformedHandinException malformed) {
            // A demand this build cannot read is one it must not act on: taking the entries it did
            // understand would earn evidence_rejected and leave the player short for nothing.
            LOGGER.warn("event=quest_handin_demand_unreadable player_uuid={} detail={}",
                    player.getStringUUID(), malformed.getMessage());
            once.send(200, unavailable(railsRoot, "demand_unreadable", List.of()));
            return;
        }

        MinecraftServer server = player.server;
        Optional<QuestHandinLedgerEntry> existing = QuestHandinLedger.find(server, demand.handinUuid());
        if (existing.isPresent() && existing.get().localState() != QuestHandinLocalState.PREPARED) {
            // A second claim on a transaction already under way. Rails adopted the same row, so the
            // same transaction id arrived twice; the items are taken at most once because this is
            // where the second attempt stops.
            answerFromExisting(player, existing.get(), railsRoot, once);
            return;
        }

        UUID requestUuid = existing.map(QuestHandinLedgerEntry::requestUuid).orElseGet(UUID::randomUUID);
        if (existing.isEmpty()) {
            QuestHandinLedgerEntry prepared = QuestHandinLedgerEntry.prepared(demand.handinUuid(),
                    player.getUUID(), string(railsRoot, "quest_id"), string(railsRoot, "quest_state_id"),
                    demand.choice(), requestUuid, System.currentTimeMillis());
            QuestHandinLedgerStore.RecordOutcome recorded = QuestHandinLedger.record(server, prepared);
            if (recorded != QuestHandinLedgerStore.RecordOutcome.CREATED) {
                // No durable place to record the transaction means no removal: a read-only ledger
                // or a player at the live-row bound both stop here, before anything is taken.
                LOGGER.warn("event=quest_handin_not_recorded handin_uuid={} player_uuid={} outcome={}",
                        demand.handinUuid(), player.getStringUUID(), recorded);
                once.send(200, unavailable(railsRoot, "ledger_" + recorded.name().toLowerCase(java.util.Locale.ROOT),
                        List.of()));
                return;
            }
        }

        QuestHandinResolution resolution = QuestHandinRequirementResolver.resolve(demand);
        if (resolution instanceof QuestHandinResolution.Refused refused) {
            QuestHandinLedger.transition(server, demand.handinUuid(),
                    entry -> entry.withSettled(QuestHandinLocalState.ABANDONED, System.currentTimeMillis()));
            once.send(200, unavailable(railsRoot, refused.reason(), List.of()));
            return;
        }
        List<QuestHandinRemoval> plan = ((QuestHandinResolution.Resolved) resolution).plan();

        Inventory inventory = player.getInventory();
        List<ItemStack> carried = QuestHandinInventory.carried(inventory);
        QuestHandinInventory.Outcome counted = QuestHandinInventory.plan(carried, plan);
        if (counted instanceof QuestHandinInventory.Insufficient shortfall) {
            // Nothing is taken and nothing is reported to Rails: the transaction stays prepared, so
            // the player can come back with the items and the same claim adopts the same row.
            LOGGER.info("event=quest_handin_items_missing handin_uuid={} player_uuid={} missing={}",
                    demand.handinUuid(), player.getStringUUID(), shortfall.missing().size());
            once.send(200, itemsMissing(railsRoot, plan, shortfall.missing(), demand.missingMessage()));
            return;
        }
        List<QuestHandinInventory.SlotTake> takes = ((QuestHandinInventory.Planned) counted).takes();

        // Step 3 of the sequence: the proof is durable BEFORE the mutation, so the crop this server
        // decided on can never be recomputed differently by a retry.
        if (QuestHandinLedger.transition(server, demand.handinUuid(),
                entry -> entry.withRemovalIntent(plan, System.currentTimeMillis())).isEmpty()) {
            once.send(200, unavailable(railsRoot, "ledger_unavailable", plan));
            return;
        }

        if (!removeDurably(player, demand.handinUuid(), requestUuid, inventory, carried, takes, plan)) {
            QuestHandinLedger.transition(server, demand.handinUuid(), QuestHandinLedgerEntry::withPrepared);
            once.send(200, unavailable(railsRoot, "removal_not_durable", plan));
            return;
        }

        QuestHandinLedger.transition(server, demand.handinUuid(),
                entry -> entry.withRemovedLocally(System.currentTimeMillis()));
        LOGGER.info("event=quest_handin_removed handin_uuid={} player_uuid={} entries={}",
                demand.handinUuid(), player.getStringUUID(), plan.size());

        confirm(player, demand.handinUuid(), railsRoot, applier, once);
    }

    /**
     * The mutation, and the only step that can leave the player's own file changed.
     *
     * <p>The shrink and the marker are two adjacent statements with no I/O, wait or yield between
     * them, so no save can observe one without the other; the forced save then puts both on disk or
     * neither. When that save reports a failure the stacks go straight back, because a removal this
     * server cannot prove is durable is one it must not report.
     *
     * @return true when the items are gone and the proof is in the player's own saved file
     */
    private static boolean removeDurably(ServerPlayer player, UUID handinUuid, UUID requestUuid,
                                         Inventory inventory, List<ItemStack> carried,
                                         List<QuestHandinInventory.SlotTake> takes,
                                         List<QuestHandinRemoval> plan) {
        if (!QuestHandinInventory.removeAll(inventory, carried, takes)) {
            LOGGER.warn("event=quest_handin_inventory_moved handin_uuid={} player_uuid={}",
                    handinUuid, player.getStringUUID());
            return false;
        }
        boolean marked = PlayerDataStore.markHandinRemoved(player,
                new PlayerDataStore.HandinRemovalMarker(handinUuid, requestUuid,
                        System.currentTimeMillis(), plan));
        if (!marked) {
            QuestHandinInventory.restoreAll(inventory, carried, takes);
            player.inventoryMenu.broadcastChanges();
            LOGGER.warn("event=quest_handin_marker_refused handin_uuid={} player_uuid={}",
                    handinUuid, player.getStringUUID());
            return false;
        }
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();

        if (!BankTransferPlayerDurability.forceSave(player)) {
            QuestHandinInventory.restoreAll(inventory, carried, takes);
            PlayerDataStore.forgetHandinRemoval(player, handinUuid);
            player.inventoryMenu.broadcastChanges();
            player.inventoryMenu.broadcastFullState();
            LOGGER.warn("event=quest_handin_player_save_failed handin_uuid={} player_uuid={}",
                    handinUuid, player.getStringUUID());
            return false;
        }
        return true;
    }

    /** Confirms with the stored proof, and answers the screen with whatever Rails says. */
    private static void confirm(ServerPlayer player, UUID handinUuid, JsonObject railsRoot,
                                CompletionApplier applier, Reply reply) {
        MinecraftServer server = player.server;
        Optional<QuestHandinLedgerEntry> current = QuestHandinLedger.transition(server, handinUuid,
                entry -> entry.withConfirming(System.currentTimeMillis()));
        QuestHandinLedgerEntry entry = current.orElseGet(
                () -> QuestHandinLedger.find(server, handinUuid).orElse(null));
        if (entry == null) {
            reply.send(200, unavailable(railsRoot, "ledger_unavailable", List.of()));
            return;
        }
        UUID connectedPlayerId = player.getUUID();
        client.confirm(server, entry.confirmation()).whenComplete((result, failure) -> server.execute(() -> {
            ServerPlayer live = server.getPlayerList().getPlayer(connectedPlayerId);
            QuestHandinLedgerEntry stored = QuestHandinLedger.find(server, handinUuid).orElse(entry);
            String body = settle(live == null ? player : live, stored, result, failure, railsRoot, applier);
            if (live != null) reply.send(200, body);
        }));
    }

    /**
     * Turns Rails' answer into a durable outcome, and into what the player sees.
     *
     * <p>Package-visible so the mapping can be exercised without a transport.
     */
    static String settle(ServerPlayer player, QuestHandinLedgerEntry entry,
                         QuestItemHandinClient.ConfirmResult result, Throwable failure,
                         JsonObject railsRoot, CompletionApplier applier) {
        MinecraftServer server = player.server;
        if (failure != null || result == null) {
            return retryLater(server, entry, "transport_error", railsRoot);
        }
        if (result instanceof QuestItemHandinClient.Failure transportFailure) {
            return retryLater(server, entry, transportFailure.safeCode(), railsRoot);
        }
        if (result instanceof QuestItemHandinClient.EndpointUnsupported) {
            // An older Rails without the hand-in routes. The items are already gone, so this is not
            // a state to shrug at: the row stays open and reconciliation keeps trying, and the
            // player is told plainly rather than left with a dialogue that did nothing.
            return retryLater(server, entry, "endpoint_unsupported", railsRoot);
        }
        QuestItemHandinProtocol.ConfirmationResponse answer =
                ((QuestItemHandinClient.Answered) result).response();
        return apply(player, entry, answer, railsRoot, applier);
    }

    /** Applies one Rails answer to a transaction whose items are already gone. */
    static String apply(ServerPlayer player, QuestHandinLedgerEntry entry,
                        QuestItemHandinProtocol.ConfirmationResponse answer, JsonObject railsRoot,
                        CompletionApplier applier) {
        MinecraftServer server = player.server;
        switch (answer.result()) {
            case CONSUMED, DUPLICATE -> {
                if (answer.response() == null) {
                    // A completion with no response to apply is not a completion. Kept open so
                    // reconciliation can ask again rather than closing a quest that never advanced.
                    return retryLater(server, entry, "completion_without_response", railsRoot);
                }
                String applied = applyOnce(player, entry, answer.response(), applier);
                settleRow(player, entry, QuestHandinLocalState.CONSUMED);
                LOGGER.info("event=quest_handin_consumed handin_uuid={} player_uuid={} duplicate={}",
                        entry.handinUuid(), player.getStringUUID(),
                        answer.result() == QuestItemHandinProtocol.Result.DUPLICATE);
                return withHandinBlock(applied, STATE_CONSUMED, entry.proof(), List.of(), "", "");
            }
            case CANCELLED_REFUNDED -> {
                // Compensation, never an authored give-back: nothing is inserted here. The refund is
                // an ordinary pending reward delivery, so it arrives through the machinery every
                // other grant uses, survives a full pack, and cannot be minted locally.
                settleRow(player, entry, QuestHandinLocalState.REFUNDED);
                com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryReconciler
                        .onJournalRefreshed(player);
                LOGGER.info("event=quest_handin_refunded handin_uuid={} player_uuid={} reason={} delivery={}",
                        entry.handinUuid(), player.getStringUUID(), answer.reason(),
                        answer.refundDeliveryUuid());
                return refunded(railsRoot, entry.proof(), answer.reason());
            }
            case EVIDENCE_REJECTED -> {
                // Rails refused the report and changed nothing: not consumed, not cancelled, not
                // paid. Retrying identical bytes cannot help, so this stops rather than loops -- but
                // the row stays open with its proof, because the items really did leave the pack.
                LOGGER.error("event=quest_handin_evidence_rejected handin_uuid={} player_uuid={} reason={}",
                        entry.handinUuid(), player.getStringUUID(), answer.reason());
                QuestHandinLedger.transition(server, entry.handinUuid(),
                        row -> row.withAttemptFailure("evidence_" + answer.reason()));
                return unavailable(railsRoot, "evidence_rejected", entry.proof());
            }
            case ITEMS_MISSING -> {
                // Unreachable from here: this server only confirms after a removal, and Rails only
                // answers items_missing to a report that took nothing. Treated as a refusal to
                // settle rather than as an outcome, so the row is not closed on a contradiction.
                return retryLater(server, entry, "unexpected_items_missing", railsRoot);
            }
            case CANCELLED, REJECTED -> {
                // The items are gone and Rails proves neither ending. The row is preserved, never
                // closed and never re-removed; an operator has the transaction id and the exact
                // proof of what was taken.
                strand(player, entry, answer.result().wireName());
                return unavailable(railsRoot, "handin_" + answer.result().wireName(), entry.proof());
            }
        }
        return unavailable(railsRoot, "unhandled_result", entry.proof());
    }

    /**
     * Applies the completion exactly once.
     *
     * <p>A row already {@code CONSUMED} means a previous attempt applied it and only the answer was
     * lost, so the response is forwarded without being applied again. The delivery ledger would
     * refuse a second grant anyway; this stops the journal and achievement work as well.
     */
    private static String applyOnce(ServerPlayer player, QuestHandinLedgerEntry entry,
                                    JsonObject response, CompletionApplier applier) {
        if (entry.localState() == QuestHandinLocalState.CONSUMED) {
            LOGGER.info("event=quest_handin_completion_already_applied handin_uuid={} player_uuid={}",
                    entry.handinUuid(), player.getStringUUID());
            return GSON.toJson(response);
        }
        return applier.apply(player, response);
    }

    private static void settleRow(ServerPlayer player, QuestHandinLedgerEntry entry,
                                  QuestHandinLocalState state) {
        // The ledger row goes terminal and is flushed BEFORE the marker is dropped, so a crash in
        // between leaves a stale marker whose row already says the transaction is over.
        QuestHandinLedger.transition(player.server, entry.handinUuid(),
                row -> row.withSettled(state, System.currentTimeMillis()));
        PlayerDataStore.forgetHandinRemoval(player, entry.handinUuid());
    }

    private static void strand(ServerPlayer player, QuestHandinLedgerEntry entry, String reason) {
        QuestHandinLedger.transition(player.server, entry.handinUuid(),
                row -> row.withStranded(reason, System.currentTimeMillis()));
        LOGGER.error("event=quest_handin_stranded handin_uuid={} player_uuid={} reason={} proof={}",
                entry.handinUuid(), player.getStringUUID(), reason, describe(entry.proof()));
    }

    /** Nothing was learned. The row keeps its proof and reconciliation will ask again. */
    private static String retryLater(MinecraftServer server, QuestHandinLedgerEntry entry,
                                     String safeCode, JsonObject railsRoot) {
        QuestHandinLedger.note(server, entry.handinUuid(), row -> row.withAttemptFailure(safeCode));
        LOGGER.warn("event=quest_handin_confirmation_deferred handin_uuid={} reason={} attempts={}",
                entry.handinUuid(), safeCode, entry.attempts());
        return unavailable(railsRoot, safeCode, entry.proof());
    }

    /** A second claim on a transaction already under way. */
    private static void answerFromExisting(ServerPlayer player, QuestHandinLedgerEntry entry,
                                           JsonObject railsRoot, Reply reply) {
        LOGGER.info("event=quest_handin_already_in_flight handin_uuid={} player_uuid={} state={}",
                entry.handinUuid(), player.getStringUUID(), entry.localState());
        if (entry.localState() == QuestHandinLocalState.CONSUMED) {
            reply.send(200, withHandinBlock(GSON.toJson(railsRoot), STATE_CONSUMED, entry.proof(),
                    List.of(), "", ""));
            return;
        }
        if (entry.localState() == QuestHandinLocalState.REFUNDED) {
            reply.send(200, refunded(railsRoot, entry.proof(), entry.lastError()));
            return;
        }
        reply.send(200, unavailable(railsRoot, "handin_in_flight", entry.proof()));
    }

    // --- the bodies the screen reads ------------------------------------------------------

    private static String itemsMissing(JsonObject railsRoot, List<QuestHandinRemoval> requires,
                                       List<QuestItemHandinProtocol.MissingItem> missing,
                                       String message) {
        JsonObject root = railsRoot.deepCopy();
        JsonObject handin = new JsonObject();
        handin.addProperty("state", STATE_ITEMS_MISSING);
        handin.add("requires", itemArray(requires));
        handin.add("missing", missingArray(missing));
        if (!message.isEmpty()) handin.addProperty("message", message);
        root.add(CLIENT_HANDIN_KEY, handin);
        return GSON.toJson(root);
    }

    private static String refunded(JsonObject railsRoot, List<QuestHandinRemoval> removed, String reason) {
        JsonObject root = railsRoot.deepCopy();
        JsonObject handin = new JsonObject();
        handin.addProperty("state", STATE_REFUNDED);
        handin.add("removed", itemArray(removed));
        if (reason != null && !reason.isEmpty()) handin.addProperty("reason", reason);
        root.add(CLIENT_HANDIN_KEY, handin);
        return GSON.toJson(root);
    }

    private static String unavailable(JsonObject railsRoot, String reason, List<QuestHandinRemoval> requires) {
        JsonObject root = railsRoot == null ? new JsonObject() : railsRoot.deepCopy();
        root.addProperty("success", true);
        JsonObject handin = new JsonObject();
        handin.addProperty("state", STATE_UNAVAILABLE);
        handin.addProperty("reason", reason);
        if (!requires.isEmpty()) handin.add("requires", itemArray(requires));
        root.add(CLIENT_HANDIN_KEY, handin);
        return GSON.toJson(root);
    }

    /** Adds the hand-in block to a body the completion path already produced. */
    private static String withHandinBlock(String body, String state, List<QuestHandinRemoval> removed,
                                          List<QuestItemHandinProtocol.MissingItem> missing,
                                          String message, String reason) {
        JsonObject root;
        try {
            root = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException unreadable) {
            return body;
        }
        JsonObject handin = new JsonObject();
        handin.addProperty("state", state);
        if (!removed.isEmpty()) handin.add("removed", itemArray(removed));
        if (!missing.isEmpty()) handin.add("missing", missingArray(missing));
        if (!message.isEmpty()) handin.addProperty("message", message);
        if (!reason.isEmpty()) handin.addProperty("reason", reason);
        root.add(CLIENT_HANDIN_KEY, handin);
        return GSON.toJson(root);
    }

    /**
     * Concrete items only. The resolver and the flag it answered are deliberately not published:
     * they are how Rails and this server talk about a requirement, and the player is owed the
     * answer rather than the question.
     */
    private static JsonArray itemArray(List<QuestHandinRemoval> removals) {
        JsonArray array = new JsonArray();
        for (QuestHandinRemoval removal : removals) {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", removal.itemId());
            entry.addProperty("count", removal.count());
            array.add(entry);
        }
        return array;
    }

    private static JsonArray missingArray(List<QuestItemHandinProtocol.MissingItem> missing) {
        JsonArray array = new JsonArray();
        for (QuestItemHandinProtocol.MissingItem entry : missing) {
            JsonObject row = new JsonObject();
            row.addProperty("item", entry.itemId());
            row.addProperty("count", entry.count());
            array.add(row);
        }
        return array;
    }

    private static String describe(List<QuestHandinRemoval> proof) {
        List<String> parts = new ArrayList<>(proof.size());
        for (QuestHandinRemoval removal : proof) parts.add(removal.itemId() + "x" + removal.count());
        return String.join(",", parts);
    }

    private static String string(JsonObject root, String key) {
        if (root == null || !root.has(key) || !root.get(key).isJsonPrimitive()) return "";
        return root.get(key).getAsString();
    }
}
