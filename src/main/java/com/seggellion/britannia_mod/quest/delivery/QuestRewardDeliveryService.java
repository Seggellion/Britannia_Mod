package com.seggellion.britannia_mod.quest.delivery;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.player.PlayerDataStore;
import com.seggellion.britannia_mod.quest.QuestRewardService;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The durable apply sequence of protocol section 1.8, per delivery, on the server thread:
 *
 * <ol>
 *   <li>If the ledger holds the uuid as {@code applied}/{@code acknowledged}, or the player marker
 *       holds it: do not insert; ensure acknowledgement.</li>
 *   <li>Record {@code pending_local} in the ledger and flush.</li>
 *   <li>Insert the items. If they do not ALL fit, insert none, record {@code queued} (flush),
 *       keep the items in the entry, acknowledge with {@code queued}, tell the player.</li>
 *   <li>Append the uuid to the player marker and force the player-data save.</li>
 *   <li>Mark {@code applied}, flush.</li>
 *   <li>POST the acknowledgement; on success mark {@code acknowledged}; on failure retry on the
 *       reconciliation schedule ({@link QuestRewardDeliveryBackoff}).</li>
 * </ol>
 *
 * <p>The restart table ({@link QuestRewardDeliveryReconciliationDecision}) sits in front of the
 * sequence, so the same entry point serves a fresh transition response, a replayed one, the
 * bootstrap's pending listing, the v2 listing, and the ledger's own open rows.
 *
 * <h2>Player save</h2>
 * The marker is persisted through {@code PlayerList#saveAll()}: the one public path to the
 * vanilla player-file write ({@code PlayerDataStorage#save} is reachable only through the
 * protected {@code PlayerList#save}), which writes the file with {@code StandardOpenOption.SYNC}
 * to a temp file and atomically replaces the real one. It saves every online player's file,
 * stats and advancements; deliveries are rare enough (one per quest transition) that this is an
 * acceptable price for not duplicating the vanilla write.
 */
public final class QuestRewardDeliveryService {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Where a delivery came from, for the log line only. */
    public enum Source { TRANSITION, REPLAYED_TRANSITION, BOOTSTRAP, PENDING_LISTING, LEDGER }

    public enum ApplyOutcome { APPLIED, QUEUED, ALREADY_APPLIED, REJECTED, UNAVAILABLE }

    @FunctionalInterface
    public interface Acknowledger {
        CompletableFuture<QuestRewardDeliveryClient.AcknowledgeResult> acknowledge(
            MinecraftServer server, QuestRewardDeliveryProtocol.AcknowledgementRequest request);
    }

    @FunctionalInterface
    public interface PlayerSaver {
        void save(ServerPlayer player);
    }

    private static final QuestRewardDeliveryClient CLIENT = new QuestRewardDeliveryClient();
    private static final Acknowledger DEFAULT_ACKNOWLEDGER = CLIENT::acknowledge;
    private static final PlayerSaver DEFAULT_PLAYER_SAVER = player -> player.server.getPlayerList().saveAll();
    private static final LongSupplier DEFAULT_CLOCK = System::currentTimeMillis;

    private static volatile Acknowledger acknowledger = DEFAULT_ACKNOWLEDGER;
    private static volatile PlayerSaver playerSaver = DEFAULT_PLAYER_SAVER;
    private static volatile LongSupplier clock = DEFAULT_CLOCK;

    /** Runtime-only scheduling state; a restart starts every schedule from its first step. */
    private static final Set<UUID> ACK_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> NEXT_ACK_ATTEMPT_AT = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ACK_FAILURES = new ConcurrentHashMap<>();
    private static final Set<UUID> REJECTION_LOGGED = ConcurrentHashMap.newKeySet();
    private static final Set<MinecraftServer> RESULT_ENDPOINT_UNSUPPORTED = ConcurrentHashMap.newKeySet();

    private QuestRewardDeliveryService() {}

    /** A delivery carried by a transition response ({@code QuestProxyService}, trigger results). */
    public static ApplyOutcome applyFromTransition(ServerPlayer player, QuestRewardDelivery delivery, boolean replayed,
                                                   String requestUuid, @Nullable QuestModels.QuestResponse response,
                                                   @Nullable JsonObject rawResponse) {
        return apply(player, delivery, replayed ? Source.REPLAYED_TRANSITION : Source.TRANSITION,
            requestUuid, response, rawResponse, false);
    }

    /** A delivery from the bootstrap or the v2 pending listing: no transition response exists. */
    public static ApplyOutcome apply(ServerPlayer player, QuestRewardDelivery delivery, Source source) {
        return apply(player, delivery, source, "", null, null, false);
    }

    /**
     * One of the ledger's own open rows. {@code respectSchedule} is true on the periodic pass,
     * false for login, journal refresh and an explicit reconciliation, which attempt at once.
     */
    public static ApplyOutcome reconcile(ServerPlayer player, QuestRewardDeliveryLedgerEntry entry, boolean respectSchedule) {
        if (player == null || entry == null) return ApplyOutcome.REJECTED;
        if (respectSchedule && !entry.itemsOwed() && !acknowledgementDue(entry.deliveryUuid(), now())) {
            return ApplyOutcome.ALREADY_APPLIED;
        }
        return apply(player, deliveryOf(entry), Source.LEDGER, "", null, null, respectSchedule);
    }

    private static ApplyOutcome apply(ServerPlayer player, QuestRewardDelivery delivery, Source source,
                                      String requestUuid, @Nullable QuestModels.QuestResponse response,
                                      @Nullable JsonObject rawResponse, boolean respectAckSchedule) {
        if (player == null || delivery == null || player.server == null) return ApplyOutcome.REJECTED;
        MinecraftServer server = player.server;
        UUID uuid = delivery.deliveryUuid();
        String rid = requestUuid == null ? "" : requestUuid;

        QuestRewardDeliveryLedgerStore store = QuestRewardDeliveryLedger.store(server);
        if (store.isReadOnlyFutureSchema()) {
            rejected(player, delivery, source, rid, "ledger_read_only", "");
            return ApplyOutcome.UNAVAILABLE;
        }
        Optional<QuestRewardDeliveryLedgerEntry> existing = store.find(uuid);
        if (existing.isPresent() && !existing.get().playerUuid().equals(player.getUUID())) {
            rejected(player, delivery, source, rid, "player_mismatch", existing.get().localState().wireName());
            return ApplyOutcome.REJECTED;
        }

        boolean marker = PlayerDataStore.hasAppliedDelivery(player, uuid);
        QuestRewardDeliveryReconciliationDecision.Action action =
            QuestRewardDeliveryReconciliationDecision.decide(existing.orElse(null), marker);
        long now = now();

        switch (action) {
            case RECORD_AND_INSERT -> {
                List<ItemStack> stacks = QuestRewardService.buildDeliveryStacks(player, delivery, response, rawResponse);
                if (stacks == null) {
                    rejected(player, delivery, source, rid, "unresolvable_item", "");
                    return ApplyOutcome.REJECTED;
                }
                QuestRewardDeliveryLedgerEntry entry = QuestRewardDeliveryLedgerEntry.pendingLocal(delivery, player.getUUID(), now);
                QuestRewardDeliveryLedgerStore.RecordOutcome recorded = QuestRewardDeliveryLedger.record(server, entry);
                if (recorded == QuestRewardDeliveryLedgerStore.RecordOutcome.READ_ONLY_SCHEMA) {
                    rejected(player, delivery, source, rid, "ledger_read_only", "");
                    return ApplyOutcome.UNAVAILABLE;
                }
                if (recorded == QuestRewardDeliveryLedgerStore.RecordOutcome.ALREADY_PRESENT) {
                    entry = store.find(uuid).orElse(entry);
                }
                LOGGER.info("event=quest_delivery_recorded delivery_uuid={} player_uuid={} quest_state_id={} "
                        + "transition_key={} outcome=recorded local_state={} request_uuid={} source={}",
                    uuid, player.getStringUUID(), entry.questStateId(), entry.transitionKey(),
                    entry.localState().wireName(), rid, source);
                return insert(player, entry, stacks, source, rid, respectAckSchedule);
            }
            case INSERT -> {
                QuestRewardDeliveryLedgerEntry entry = existing.orElseThrow();
                List<ItemStack> stacks = QuestRewardService.buildDeliveryStacks(player, deliveryOf(entry), response, rawResponse);
                if (stacks == null) {
                    rejected(player, delivery, source, rid, "unresolvable_item", entry.localState().wireName());
                    return ApplyOutcome.REJECTED;
                }
                return insert(player, entry, stacks, source, rid, respectAckSchedule);
            }
            case REPAIR_APPLIED_THEN_ACKNOWLEDGE -> {
                QuestRewardDeliveryLedgerEntry before = existing.orElseThrow();
                QuestRewardDeliveryLedger.transition(server, uuid, entry -> entry.withApplied(now));
                reconciled(player, delivery, source, rid, "repaired_applied_from_marker",
                    before.localState().wireName() + "->" + QuestRewardDeliveryLocalState.APPLIED.wireName());
                acknowledge(server, uuid, respectAckSchedule);
                return ApplyOutcome.ALREADY_APPLIED;
            }
            case RECORD_APPLIED_THEN_ACKNOWLEDGE -> {
                QuestRewardDeliveryLedgerEntry entry = QuestRewardDeliveryLedgerEntry.pendingLocal(delivery, player.getUUID(), now)
                    .withApplied(now);
                QuestRewardDeliveryLedger.record(server, entry);
                reconciled(player, delivery, source, rid, "recorded_applied_from_marker",
                    QuestRewardDeliveryLocalState.APPLIED.wireName());
                acknowledge(server, uuid, respectAckSchedule);
                return ApplyOutcome.ALREADY_APPLIED;
            }
            case ACKNOWLEDGE_ONLY -> {
                reconciled(player, delivery, source, rid, "already_applied", existing.orElseThrow().localState().wireName());
                acknowledge(server, uuid, respectAckSchedule);
                return ApplyOutcome.ALREADY_APPLIED;
            }
            case NOTHING -> {
                if (source != Source.LEDGER) {
                    reconciled(player, delivery, source, rid, "already_acknowledged",
                        existing.map(entry -> entry.localState().wireName()).orElse(""));
                }
                return ApplyOutcome.ALREADY_APPLIED;
            }
        }
        return ApplyOutcome.REJECTED;
    }

    /** Steps 3 to 6. {@code stacks} is the complete, already-stamped grant. */
    private static ApplyOutcome insert(ServerPlayer player, QuestRewardDeliveryLedgerEntry entry, List<ItemStack> stacks,
                                       Source source, String requestUuid, boolean respectAckSchedule) {
        MinecraftServer server = player.server;
        UUID uuid = entry.deliveryUuid();
        long now = now();
        boolean wasQueued = entry.localState() == QuestRewardDeliveryLocalState.QUEUED;

        // A player on the death screen still has a live inventory object, and the reconciler still
        // sweeps them -- but `PlayerList.respawn` builds a NEW ServerPlayer and, on an ordinary
        // death, does not copy the corpse's inventory. Anything inserted here would be marked
        // applied, acknowledged to Rails, and then thrown away with the body. Queueing is exactly
        // the shape for "cannot place it right now", and the reconciler already retries a queued
        // delivery on the next inventory change and every thirty seconds.
        //
        // This matters most for a hand-in refund: it is the second half of the promise that a
        // removed item always comes back, and dying is not an unusual thing to do while one is in
        // flight.
        if (player.isRemoved() || player.isDeadOrDying()) {
            Optional<QuestRewardDeliveryLedgerEntry> held = QuestRewardDeliveryLedger.transition(server, uuid,
                current -> current.localState() == QuestRewardDeliveryLocalState.QUEUED ? current : current.withQueued(now));
            if (held.isPresent()) {
                LOGGER.info("event=quest_delivery_queued delivery_uuid={} player_uuid={} quest_state_id={} "
                        + "transition_key={} outcome=queued local_state={} request_uuid={} source={} reason=player_not_alive",
                    uuid, player.getStringUUID(), entry.questStateId(), entry.transitionKey(),
                    QuestRewardDeliveryLocalState.QUEUED.wireName(), requestUuid, source);
            }
            QuestRewardDeliveryReconciler.noteQueued(player);
            acknowledge(server, uuid, respectAckSchedule);
            return ApplyOutcome.QUEUED;
        }

        if (!QuestRewardDeliveryInventoryInsertion.insertAll(player.getInventory(), stacks)) {
            Optional<QuestRewardDeliveryLedgerEntry> queued = QuestRewardDeliveryLedger.transition(server, uuid,
                current -> current.localState() == QuestRewardDeliveryLocalState.QUEUED ? current : current.withQueued(now));
            if (queued.isPresent()) {
                LOGGER.info("event=quest_delivery_queued delivery_uuid={} player_uuid={} quest_state_id={} "
                        + "transition_key={} outcome=queued local_state={} request_uuid={} source={}",
                    uuid, player.getStringUUID(), entry.questStateId(), entry.transitionKey(),
                    QuestRewardDeliveryLocalState.QUEUED.wireName(), requestUuid, source);
                QuestRewardDeliveryNotices.queued(player);
            }
            QuestRewardDeliveryReconciler.noteQueued(player);
            acknowledge(server, uuid, respectAckSchedule);
            return ApplyOutcome.QUEUED;
        }
        player.inventoryMenu.broadcastChanges();

        // Step 4: the marker joins the inserted items in the same in-memory player state, then the
        // vanilla player-file write persists both or neither.
        PlayerDataStore.markDeliveryApplied(player, uuid);
        try {
            playerSaver.save(player);
        } catch (RuntimeException saveFailure) {
            LOGGER.warn("event=quest_delivery_player_save_failed delivery_uuid={} player_uuid={} error={}",
                uuid, player.getStringUUID(), saveFailure.toString());
        }

        // Step 5.
        QuestRewardDeliveryLedger.transition(server, uuid, current -> current.withApplied(now));
        LOGGER.info("event=quest_delivery_applied delivery_uuid={} player_uuid={} quest_state_id={} "
                + "transition_key={} outcome=applied local_state={} request_uuid={} source={} was_queued={}",
            uuid, player.getStringUUID(), entry.questStateId(), entry.transitionKey(),
            QuestRewardDeliveryLocalState.APPLIED.wireName(), requestUuid, source, wasQueued);
        if (wasQueued) QuestRewardDeliveryNotices.delivered(player);
        else QuestRewardDeliveryNotices.received(player);

        // Step 6.
        acknowledge(server, uuid, respectAckSchedule);
        return ApplyOutcome.APPLIED;
    }

    /**
     * Posts the entry's current outcome to Rails unless nothing is outstanding, one is already in
     * flight, the schedule says not yet, or this boot already learned the endpoint is missing.
     */
    static void acknowledge(MinecraftServer server, UUID deliveryUuid, boolean respectSchedule) {
        Optional<QuestRewardDeliveryLedgerEntry> found = QuestRewardDeliveryLedger.find(server, deliveryUuid);
        if (found.isEmpty() || !found.get().acknowledgementOutstanding()) return;
        if (RESULT_ENDPOINT_UNSUPPORTED.contains(server)) return;
        long now = now();
        if (respectSchedule && now < NEXT_ACK_ATTEMPT_AT.getOrDefault(deliveryUuid, 0L)) return;
        if (!ACK_IN_FLIGHT.add(deliveryUuid)) return;

        QuestRewardDeliveryLedgerEntry entry = found.get();
        QuestRewardDeliveryProtocol.Outcome outcome = entry.localState() == QuestRewardDeliveryLocalState.QUEUED
            ? QuestRewardDeliveryProtocol.Outcome.QUEUED : QuestRewardDeliveryProtocol.Outcome.APPLIED;
        QuestRewardDeliveryProtocol.AcknowledgementRequest request = new QuestRewardDeliveryProtocol.AcknowledgementRequest(
            deliveryUuid, entry.playerUuid(), outcome, QuestRewardDeliveryProtocol.formatRecordedAt(now), UUID.randomUUID());

        CompletableFuture<QuestRewardDeliveryClient.AcknowledgeResult> future;
        try {
            future = acknowledger.acknowledge(server, request);
        } catch (RuntimeException failure) {
            future = CompletableFuture.failedFuture(failure);
        }
        if (future == null) future = CompletableFuture.completedFuture(new QuestRewardDeliveryClient.Failure("no_response"));
        future.whenComplete((result, failure) ->
            server.execute(() -> completeAcknowledgement(server, request, result, failure)));
    }

    private static void completeAcknowledgement(MinecraftServer server,
                                                QuestRewardDeliveryProtocol.AcknowledgementRequest request,
                                                QuestRewardDeliveryClient.AcknowledgeResult result, Throwable failure) {
        UUID uuid = request.deliveryUuid();
        ACK_IN_FLIGHT.remove(uuid);
        long now = now();
        QuestRewardDeliveryClient.AcknowledgeResult outcome = failure == null && result != null
            ? result : new QuestRewardDeliveryClient.Failure(failureCode(failure));
        Optional<QuestRewardDeliveryLedgerEntry> found = QuestRewardDeliveryLedger.find(server, uuid);
        if (found.isEmpty()) return;
        QuestRewardDeliveryLedgerEntry before = found.get();

        switch (outcome) {
            case QuestRewardDeliveryClient.Acknowledged accepted -> {
                Optional<QuestRewardDeliveryLedgerEntry> after = QuestRewardDeliveryLedger.transition(server, uuid,
                    entry -> entry.withAcknowledged(request.outcome(), now));
                NEXT_ACK_ATTEMPT_AT.remove(uuid);
                ACK_FAILURES.remove(uuid);
                LOGGER.info("event=quest_delivery_acknowledged delivery_uuid={} player_uuid={} quest_state_id={} "
                        + "transition_key={} outcome={} rails_state={} rails_outcome={} duplicate={} local_state={} request_uuid={}",
                    uuid, before.playerUuid(), before.questStateId(), before.transitionKey(),
                    request.outcome().wireName(), accepted.response().state(), accepted.response().outcome(),
                    accepted.response().duplicate(), after.orElse(before).localState().wireName(), request.requestUuid());
            }
            case QuestRewardDeliveryClient.TerminalRejection terminal -> {
                QuestRewardDeliveryLedger.transition(server, uuid, entry -> entry.withTerminalError(terminal.code(), now));
                NEXT_ACK_ATTEMPT_AT.remove(uuid);
                ACK_FAILURES.remove(uuid);
                LOGGER.warn("event=quest_delivery_rejected delivery_uuid={} player_uuid={} quest_state_id={} "
                        + "transition_key={} outcome={} reason={} local_state={} items_dropped={} request_uuid={}",
                    uuid, before.playerUuid(), before.questStateId(), before.transitionKey(),
                    request.outcome().wireName(), terminal.code(), QuestRewardDeliveryLocalState.ACKNOWLEDGED.wireName(),
                    before.itemsOwed(), request.requestUuid());
            }
            case QuestRewardDeliveryClient.EndpointUnsupported unsupported -> {
                if (RESULT_ENDPOINT_UNSUPPORTED.add(server)) {
                    LOGGER.warn("event=quest_delivery_endpoint_unsupported endpoint=quest_reward_delivery_result "
                        + "detail=rails_without_v2_reward_deliveries delivery_uuid={} request_uuid={}; "
                        + "acknowledgements are suspended until the next server start", uuid, request.requestUuid());
                }
            }
            case QuestRewardDeliveryClient.Failure transientFailure -> {
                int failures = ACK_FAILURES.merge(uuid, 1, Integer::sum);
                long delay = QuestRewardDeliveryBackoff.delayMillis(failures);
                NEXT_ACK_ATTEMPT_AT.put(uuid, now + delay);
                QuestRewardDeliveryLedger.note(server, uuid, QuestRewardDeliveryLedgerEntry::withAttempt);
                LOGGER.warn("event=quest_delivery_acknowledgement_failed delivery_uuid={} player_uuid={} quest_state_id={} "
                        + "transition_key={} outcome={} local_state={} error={} failures={} retry_in_ms={} request_uuid={}",
                    uuid, before.playerUuid(), before.questStateId(), before.transitionKey(),
                    request.outcome().wireName(), before.localState().wireName(), transientFailure.safeCode(),
                    failures, delay, request.requestUuid());
            }
        }
    }

    /** Whether the periodic pass may try this delivery's acknowledgement now. */
    public static boolean acknowledgementDue(UUID deliveryUuid, long nowEpochMillis) {
        return !ACK_IN_FLIGHT.contains(deliveryUuid)
            && nowEpochMillis >= NEXT_ACK_ATTEMPT_AT.getOrDefault(deliveryUuid, 0L);
    }

    public static boolean resultEndpointUnsupported(MinecraftServer server) {
        return RESULT_ENDPOINT_UNSUPPORTED.contains(server);
    }

    /** Server stop: every schedule and the once-per-boot flags go with the server. */
    public static void clear(MinecraftServer server) {
        RESULT_ENDPOINT_UNSUPPORTED.remove(server);
        ACK_IN_FLIGHT.clear();
        NEXT_ACK_ATTEMPT_AT.clear();
        ACK_FAILURES.clear();
        REJECTION_LOGGED.clear();
    }

    static QuestRewardDelivery deliveryOf(QuestRewardDeliveryLedgerEntry entry) {
        return new QuestRewardDelivery(entry.deliveryUuid(), entry.questId(), entry.questStateId(),
            entry.transitionKey(), entry.items(), "pending", "");
    }

    static long now() {
        return clock.getAsLong();
    }

    private static void rejected(ServerPlayer player, QuestRewardDelivery delivery, Source source, String requestUuid,
                                 String reason, String localState) {
        // A delivery this side can never apply is re-listed at every trigger; say so once per boot.
        boolean first = REJECTION_LOGGED.add(delivery.deliveryUuid());
        if (!first && "unresolvable_item".equals(reason)) return;
        LOGGER.warn("event=quest_delivery_rejected delivery_uuid={} player_uuid={} quest_state_id={} transition_key={} "
                + "outcome=rejected reason={} local_state={} request_uuid={} source={}",
            delivery.deliveryUuid(), player.getStringUUID(), delivery.questStateId(), delivery.transitionKey(),
            reason, localState, requestUuid, source);
    }

    private static void reconciled(ServerPlayer player, QuestRewardDelivery delivery, Source source, String requestUuid,
                                   String outcome, String localState) {
        LOGGER.info("event=quest_delivery_reconciled delivery_uuid={} player_uuid={} quest_state_id={} transition_key={} "
                + "outcome={} local_state={} request_uuid={} source={}",
            delivery.deliveryUuid(), player.getStringUUID(), delivery.questStateId(), delivery.transitionKey(),
            outcome, localState, requestUuid, source);
    }

    private static String failureCode(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        return cause == null ? "transport_error" : "transport_error:" + cause.getClass().getSimpleName();
    }

    // --- test seams -------------------------------------------------------------------------

    /** Installs a stand-in transport. Tests only; always restore with {@link #resetAcknowledger()}. */
    public static void installAcknowledger(Acknowledger replacement) {
        acknowledger = replacement == null ? DEFAULT_ACKNOWLEDGER : replacement;
    }

    public static void resetAcknowledger() {
        acknowledger = DEFAULT_ACKNOWLEDGER;
    }

    public static void installPlayerSaver(PlayerSaver replacement) {
        playerSaver = replacement == null ? DEFAULT_PLAYER_SAVER : replacement;
    }

    public static void resetPlayerSaver() {
        playerSaver = DEFAULT_PLAYER_SAVER;
    }

    /** Lets a test advance the reconciliation schedule instead of waiting on the wall clock. */
    public static void installClock(LongSupplier replacement) {
        clock = replacement == null ? DEFAULT_CLOCK : replacement;
    }

    public static void resetClock() {
        clock = DEFAULT_CLOCK;
    }

    /** Test-only: forgets a delivery's schedule so a test's random uuid leaves nothing behind. */
    public static void forgetScheduleForTesting(UUID deliveryUuid) {
        ACK_IN_FLIGHT.remove(deliveryUuid);
        NEXT_ACK_ATTEMPT_AT.remove(deliveryUuid);
        ACK_FAILURES.remove(deliveryUuid);
        REJECTION_LOGGED.remove(deliveryUuid);
    }
}
