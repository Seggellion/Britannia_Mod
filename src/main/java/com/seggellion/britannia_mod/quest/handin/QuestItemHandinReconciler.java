package com.seggellion.britannia_mod.quest.handin;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.player.PlayerDataStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finishing hand-ins whose answer this server never saw.
 *
 * <p>Every path here obeys one rule: <b>never remove anything again</b>. A transaction that reached
 * a durable removal is completed by re-sending the stored proof, never by taking a second item, and
 * the reconciliation endpoint it consults is read-only on the Rails side precisely so no amount of
 * retrying can cause one.
 *
 * <p>The player's own file is the authority on whether the mutation happened. Where the ledger and
 * the marker disagree, the marker wins in the direction that is safe: a marker without a matching
 * ledger row is enough to confirm on its own, while a ledger row claiming a removal the player's
 * file does not record is refused rather than confirmed -- confirming it would buy a completed
 * quest, or a refund, for items the player still holds.
 */
public final class QuestItemHandinReconciler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The periodic sweep runs at the same cadence as the delivery reconciler's. */
    public static final int TICK_INTERVAL = 20;

    /** A prepared transaction that took nothing is retired after a day rather than kept forever. */
    static final long PREPARED_RETIREMENT_MILLIS = 24L * 60L * 60L * 1000L;

    private static final long FIRST_RETRY_MILLIS = 10_000L;
    private static final long MAX_RETRY_MILLIS = 10L * 60L * 1000L;

    /** At most this many confirmations in flight per player, so a sweep cannot flood the pool. */
    private static final int MAX_IN_FLIGHT_PER_PLAYER = 4;

    /** How often one player's unresolved transactions are asked about. */
    static final long INQUIRY_INTERVAL_MILLIS = 60_000L;

    /**
     * How long a prepared transaction is left alone before Rails is asked whether it still exists.
     *
     * <p>Long enough that a player who clicked, saw a shortfall and walked off to find the item is
     * never interrupted; short enough that a transaction whose run is gone does not sit in the
     * ledger for a day first.
     */
    static final long PREPARED_INQUIRY_AFTER_MILLIS = 5L * 60L * 1000L;

    /** After this many failed confirmations, asking what happened is cheaper than asking again. */
    private static final int INQUIRE_AFTER_ATTEMPTS = 3;

    /** Marks a row this server rebuilt from a player marker because its ledger row was gone. */
    static final String REBUILT_FROM_MARKER = "rebuilt_from_marker";

    private static final Map<MinecraftServer, Integer> TICKS = new ConcurrentHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, Long>> NEXT_ATTEMPT_AT = new ConcurrentHashMap<>();
    private static final Map<MinecraftServer, Set<UUID>> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, Long>> NEXT_INQUIRY_AT = new ConcurrentHashMap<>();

    private static QuestItemHandinService.CompletionApplier applier = (player, response) -> "";
    private static Inquiry inquiry = new QuestItemHandinClient()::reconcile;

    /**
     * Asking Rails what happened to transactions this server has no final answer for.
     *
     * <p>Read-only on the Rails side by design, which is the property that makes it safe to retry at
     * all: nothing it can answer causes a second removal. It is the only way to resolve a row that
     * confirmation alone cannot -- a completion whose response was lost, or a cancellation this
     * server has not yet been told about.
     */
    @FunctionalInterface
    public interface Inquiry {
        java.util.concurrent.CompletableFuture<QuestItemHandinClient.ReconcileResult> reconcile(
                MinecraftServer server, UUID playerUuid, List<UUID> handinUuids);
    }

    public static void installInquiry(Inquiry testInquiry) {
        inquiry = Objects.requireNonNull(testInquiry, "testInquiry");
    }

    /** Always call this in test teardown: the slot is process-wide. */
    public static void resetInquiry() {
        inquiry = new QuestItemHandinClient()::reconcile;
    }

    private QuestItemHandinReconciler() {}

    /**
     * Installs the path a recovered completion is applied through.
     *
     * <p>Set once at server start from the quest proxy, for the same reason the service takes it as
     * an argument: a completion recovered after a restart must run the same journal, reward and
     * achievement code an ordinary choice runs.
     */
    public static void useCompletionApplier(QuestItemHandinService.CompletionApplier completionApplier) {
        applier = Objects.requireNonNull(completionApplier, "completionApplier");
    }

    /**
     * Login is the moment both halves of the evidence are readable at once: the ledger is loaded
     * and the player's own persistent data has just come back from disk. Every restart-recovery
     * decision below needs both, so this is where they are made.
     */
    public static void onLogin(ServerPlayer player) {
        sweep(player, "login");
    }

    public static void onJournalRefreshed(ServerPlayer player) {
        sweep(player, "journal_refresh");
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        int ticks = TICKS.merge(server, 1, Integer::sum);
        if (ticks % TICK_INTERVAL != 0) return;
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            sweep(player, "periodic");
        }
    }

    /**
     * Drops the runtime retry schedule for one player's transactions on logout, so their next login
     * starts from the first attempt rather than serving out a ten-minute back-off.
     *
     * <p>Scoped to that player's own rows. Clearing the whole map here would also drop other
     * players' in-flight guards, and the guard is what stops one transaction being confirmed twice
     * at once.
     */
    public static void forgetPlayer(MinecraftServer server, UUID playerUuid) {
        if (server == null || playerUuid == null) return;
        Set<UUID> owned = new HashSet<>();
        for (QuestHandinLedgerEntry entry : QuestHandinLedgerStore.get(server).entriesFor(playerUuid)) {
            owned.add(entry.handinUuid());
        }
        Map<UUID, Long> schedule = NEXT_ATTEMPT_AT.get(server);
        if (schedule != null) schedule.keySet().removeAll(owned);
        Map<UUID, Long> inquiries = NEXT_INQUIRY_AT.get(server);
        if (inquiries != null) inquiries.keySet().removeAll(owned);
    }

    public static void clear(MinecraftServer server) {
        TICKS.remove(server);
        NEXT_ATTEMPT_AT.remove(server);
        NEXT_INQUIRY_AT.remove(server);
        IN_FLIGHT.remove(server);
    }

    /**
     * Claims the right to confirm one transaction, or answers false because someone already has it.
     *
     * <p>Shared with the live path deliberately. A confirmation posted by the dialogue and one
     * posted by the sweep are the same request for the same transaction, and the sweep runs a second
     * after the click: without one guard covering both, every hand-in whose round trip outlasts a
     * second is confirmed twice, concurrently, and "exactly once" rests on Rails serialising them
     * rather than on anything this side does.
     */
    public static boolean reserve(MinecraftServer server, UUID handinUuid) {
        return IN_FLIGHT.computeIfAbsent(server, key -> ConcurrentHashMap.newKeySet()).add(handinUuid);
    }

    /** Releases a reservation. Always called from the completion callback, on the server thread. */
    public static void release(MinecraftServer server, UUID handinUuid) {
        Set<UUID> inFlight = IN_FLIGHT.get(server);
        if (inFlight != null) inFlight.remove(handinUuid);
    }

    /**
     * One pass over everything this player has outstanding.
     *
     * <p>Runs on the server thread. Reads the ledger and the player's markers, repairs whatever
     * disagreement a crash left, and confirms what is owed.
     */
    static void sweep(ServerPlayer player, String trigger) {
        if (player == null) return;
        MinecraftServer server = player.server;
        QuestHandinLedgerStore store = QuestHandinLedgerStore.get(server);
        if (store.isReadOnlyFutureSchema()) return;

        List<QuestHandinLedgerEntry> rows = store.entriesFor(player.getUUID());
        Set<UUID> known = new HashSet<>();
        rows.forEach(entry -> known.add(entry.handinUuid()));

        adoptOrphanMarkers(player, known, trigger);
        reapSettledMarkers(player, store);

        long now = System.currentTimeMillis();
        int started = 0;
        for (QuestHandinLedgerEntry entry : store.entriesFor(player.getUUID())) {
            if (entry.localState().settled() || entry.localState() == QuestHandinLocalState.STRANDED) {
                continue;
            }
            QuestHandinLedgerEntry repaired = repair(player, entry, now, trigger);
            if (repaired == null || !repaired.localState().confirmationOutstanding()) continue;
            if (started >= MAX_IN_FLIGHT_PER_PLAYER) break;
            if (attemptConfirmation(player, repaired, now)) started++;
        }
        inquire(player, now);
    }

    /**
     * Asks Rails what happened to the transactions this server cannot resolve on its own.
     *
     * <p>Three kinds of row reach here, and none of them can be finished by confirming again.
     *
     * <ul>
     *   <li>A <b>stranded</b> row: the items are gone and the last answer proved neither ending. If
     *       Rails in fact recorded the transaction, this is what finds out -- and a stranded row
     *       resolved this way stops being an operator's problem.</li>
     *   <li>A <b>prepared</b> row that has sat unclaimed: nothing was taken, so learning that its run
     *       is gone simply lets it be closed instead of waiting out a day.</li>
     *   <li>A row whose <b>confirmations keep failing</b>: after a few attempts, asking what happened
     *       is cheaper and more informative than asking the same question again.</li>
     * </ul>
     *
     * <p>Bounded by the Rails limit and rate-limited per player, and it never removes anything: the
     * one thing that must not happen to a shard that has already removed is a second removal, and
     * the endpoint this calls cannot cause one.
     */
    private static void inquire(ServerPlayer player, long now) {
        MinecraftServer server = player.server;
        Map<UUID, Long> schedule = NEXT_INQUIRY_AT.computeIfAbsent(server, key -> new HashMap<>());
        List<UUID> ask = new ArrayList<>();
        for (QuestHandinLedgerEntry entry : QuestHandinLedgerStore.get(server).entriesFor(player.getUUID())) {
            if (!worthAsking(entry, now)) continue;
            Long due = schedule.get(entry.handinUuid());
            if (due != null && due > now) continue;
            ask.add(entry.handinUuid());
            if (ask.size() >= QuestItemHandinProtocol.MAX_RECONCILE_UUIDS) break;
        }
        if (ask.isEmpty()) return;
        ask.forEach(uuid -> schedule.put(uuid, now + INQUIRY_INTERVAL_MILLIS));

        UUID connectedPlayerId = player.getUUID();
        Set<UUID> asked = Set.copyOf(ask);
        inquiry.reconcile(server, connectedPlayerId, ask)
                .whenComplete((result, failure) -> server.execute(() -> {
                    ServerPlayer live = server.getPlayerList().getPlayer(connectedPlayerId);
                    if (live == null || failure != null
                            || !(result instanceof QuestItemHandinClient.Reconciled reconciled)) {
                        return;
                    }
                    // Only the rows this sweep asked about, and only this player's. An answer is a
                    // list of transaction ids, and a transaction id is enough to find any row on the
                    // server: without both checks a malformed or mis-scoped listing could run one
                    // player's completion on another, and close a row belonging to someone who was
                    // not even asked about.
                    reconciled.response().handins().stream()
                            .filter(row -> asked.contains(row.handinUuid()))
                            .forEach(row -> applyInquiry(live, row));
                }));
    }

    private static boolean worthAsking(QuestHandinLedgerEntry entry, long now) {
        return switch (entry.localState()) {
            case STRANDED -> true;
            case PREPARED -> now - entry.recordedAtMillis() > PREPARED_INQUIRY_AFTER_MILLIS;
            case REMOVED_LOCAL, CONFIRMING -> entry.attempts() >= INQUIRE_AFTER_ATTEMPTS;
            default -> false;
        };
    }

    /**
     * What Rails' answer means for one local row.
     *
     * <p>The interesting case is {@code cancelled}: Rails has given up on the transaction and has no
     * record of a removal, but this server's own ledger says otherwise. Confirming with the stored
     * proof is what publishes the compensation -- reconciliation cannot do it, because only the
     * shard knows it removed. So the row is put back where the confirmation sweep will pick it up,
     * and only when the player's own file still vouches for the removal.
     */
    private static void applyInquiry(ServerPlayer player, QuestItemHandinProtocol.ReconcileEntry row) {
        MinecraftServer server = player.server;
        QuestHandinLedgerEntry entry = QuestHandinLedger.find(server, row.handinUuid()).orElse(null);
        if (entry == null || entry.localState().settled()) return;
        if (!entry.playerUuid().equals(player.getUUID())) {
            LOGGER.warn("event=quest_handin_inquiry_wrong_player handin_uuid={} asked_for={}",
                    row.handinUuid(), player.getStringUUID());
            return;
        }
        long now = System.currentTimeMillis();
        // A row that took nothing cannot be settled into a state that means the items are gone --
        // the record refuses to represent one, and an exception here would escape into the server
        // tick loop. It also would not be true: an answer about a transaction this server never
        // removed for is an answer about somebody else's removal, and all it means locally is that
        // there is nothing left to do.
        boolean removedHere = entry.localState().itemsRemoved();
        // A row that took something is only acted on while the player's own file still vouches for
        // the removal. That single rule closes two different ways an answer could pay twice: a row
        // stranded because the marker was missing means the player may still be holding the goods,
        // so completing the quest or accepting a refund for it would hand them both; and a row
        // whose marker has since gone is no longer a removal this server can account for.
        boolean vouched = !removedHere
                || PlayerDataStore.handinRemoval(player, row.handinUuid()).isPresent();
        if (removedHere && !vouched) {
            LOGGER.warn("event=quest_handin_inquiry_unvouched handin_uuid={} outcome={} state={}",
                    row.handinUuid(), row.outcome(), entry.localState());
            return;
        }
        switch (row.outcome()) {
            case CONSUMED -> {
                if (row.response() != null && removedHere) {
                    applier.apply(player, row.response());
                }
                LOGGER.info("event=quest_handin_resolved_by_inquiry handin_uuid={} outcome=consumed removed_here={}",
                        row.handinUuid(), removedHere);
                settle(player, entry, removedHere
                        ? QuestHandinLocalState.CONSUMED : QuestHandinLocalState.ABANDONED, now);
            }
            case CANCELLED_REFUNDED -> {
                LOGGER.info("event=quest_handin_resolved_by_inquiry handin_uuid={} outcome=refunded removed_here={}",
                        row.handinUuid(), removedHere);
                settle(player, entry, removedHere
                        ? QuestHandinLocalState.REFUNDED : QuestHandinLocalState.ABANDONED, now);
            }
            case CANCELLED -> {
                if (!removedHere) {
                    settle(player, entry, QuestHandinLocalState.ABANDONED, now);
                    return;
                }
                // A refusal of the evidence is not a transaction waiting for a refund, it is one
                // Rails will refuse identically every time. Requeueing it would undo the strand and
                // spin the same removal through confirm-refuse-strand once a minute forever, each
                // lap flushing the whole overworld storage twice.
                if (entry.lastError().startsWith("evidence_")) {
                    LOGGER.info("event=quest_handin_requeue_refused handin_uuid={} reason={}",
                            row.handinUuid(), entry.lastError());
                    return;
                }
                // Removed here, unknown to Rails. Its confirmation is what publishes the refund.
                LOGGER.info("event=quest_handin_requeued_for_refund handin_uuid={}", row.handinUuid());
                String reason = entry.lastError();
                QuestHandinLedger.transition(server, row.handinUuid(),
                        stored -> stored.withRemovedLocally(now).withAttemptFailure(reason));
            }
            case UNKNOWN -> {
                // Rails holds no such transaction. Nothing taken means nothing owed; something taken
                // stays exactly where it was, preserved for an operator.
                if (!removedHere) {
                    settle(player, entry, QuestHandinLocalState.ABANDONED, now);
                }
            }
            case PENDING -> {
                // Still open on both sides. A prepared row is simply waiting for the player.
            }
        }
    }

    private static void settle(ServerPlayer player, QuestHandinLedgerEntry entry,
                               QuestHandinLocalState state, long now) {
        QuestHandinLedger.transition(player.server, entry.handinUuid(),
                stored -> stored.withSettled(state, now));
        if (PlayerDataStore.forgetHandinRemoval(player, entry.handinUuid())) {
            com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability.forceSave(player);
        }
    }

    /**
     * A marker whose ledger row is gone.
     *
     * <p>This is the ledger file having been lost or quarantined while the player's own file kept
     * the truth. The marker is self-sufficient by design -- transaction id, correlation id and the
     * exact proof -- so the row is rebuilt from it and confirmed. Rails answers {@code duplicate}
     * if the transaction had in fact already completed, which is safe: the completion's grant is a
     * reward delivery, and that ledger refuses a second application of the same delivery.
     */
    private static void adoptOrphanMarkers(ServerPlayer player, Set<UUID> known, String trigger) {
        for (PlayerDataStore.HandinRemovalMarker marker : PlayerDataStore.handinRemovals(player)) {
            if (known.contains(marker.handinUuid())) continue;
            QuestHandinLedgerEntry rebuilt = new QuestHandinLedgerEntry(marker.handinUuid(),
                    player.getUUID(), "", "", "", marker.proof(), QuestHandinLocalState.REMOVED_LOCAL,
                    marker.requestUuid(), 0, marker.removedAtMillis(), marker.removedAtMillis(), 0L,
                    REBUILT_FROM_MARKER);
            QuestHandinLedgerStore.RecordOutcome outcome = QuestHandinLedger.record(player.server, rebuilt);
            LOGGER.warn("event=quest_handin_marker_adopted handin_uuid={} player_uuid={} outcome={} trigger={}",
                    marker.handinUuid(), player.getStringUUID(), outcome, trigger);
        }
    }

    /**
     * Drops markers whose transaction has finished.
     *
     * <p>The settle path already forces this to disk, so reaching one here means a crash landed in
     * the window between the two. It matters because markers are bounded and never evicted to make
     * room: a leaked one occupies a slot forever, and a player whose slots are full can never hand
     * anything in again. Safe by construction -- only a settled row's marker is taken, and a settled
     * row is one this server has already finished.
     */
    private static void reapSettledMarkers(ServerPlayer player, QuestHandinLedgerStore store) {
        boolean dropped = false;
        for (PlayerDataStore.HandinRemovalMarker marker : PlayerDataStore.handinRemovals(player)) {
            Optional<QuestHandinLedgerEntry> row = store.find(marker.handinUuid());
            if (row.isEmpty() || !row.get().localState().settled()) continue;
            dropped |= PlayerDataStore.forgetHandinRemoval(player, marker.handinUuid());
        }
        if (dropped) {
            com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability.forceSave(player);
            LOGGER.info("event=quest_handin_markers_reaped player_uuid={}", player.getStringUUID());
        }
    }

    /**
     * Brings one row level with what the player's own file says, and answers with the repaired row
     * (or null when it is finished).
     */
    private static QuestHandinLedgerEntry repair(ServerPlayer player, QuestHandinLedgerEntry entry,
                                                  long now, String trigger) {
        MinecraftServer server = player.server;
        boolean marked = PlayerDataStore.handinRemoval(player, entry.handinUuid()).isPresent();

        switch (entry.localState()) {
            case PREPARED -> {
                if (marked) {
                    // Reachable, and the case is worth naming: the forced player save reported a
                    // failure after vanilla had in fact already written the file, so the removal was
                    // rolled back in memory and the row returned to prepared while the disk kept
                    // both the shrink and its marker. The player's own file is the authority on
                    // whether the mutation happened, so it wins.
                    return promoteFromMarker(player, entry, trigger);
                }
                // The long stop, for a shard that could never reach Rails to ask. Nothing was
                // taken, so retiring it costs nothing -- and an abandoned row is re-claimable.
                if (now - entry.recordedAtMillis() > PREPARED_RETIREMENT_MILLIS) {
                    LOGGER.info("event=quest_handin_prepared_retired handin_uuid={} player_uuid={}",
                            entry.handinUuid(), player.getStringUUID());
                    QuestHandinLedger.transition(server, entry.handinUuid(),
                            row -> row.withSettled(QuestHandinLocalState.ABANDONED, now));
                }
                return null;
            }
            case REMOVAL_INTENT -> {
                if (marked) return promoteFromMarker(player, entry, trigger);
                // The intent is durable but the mutation is not in the player's own file, so it did
                // not happen: the shrink and the marker are written together or not at all. Nothing
                // was taken, nothing is owed, and the transaction is free to be claimed again.
                LOGGER.info("event=quest_handin_intent_unwound handin_uuid={} player_uuid={} trigger={}",
                        entry.handinUuid(), player.getStringUUID(), trigger);
                return QuestHandinLedger.transition(server, entry.handinUuid(),
                        QuestHandinLedgerEntry::withPrepared).orElse(null);
            }
            case REMOVED_LOCAL, CONFIRMING -> {
                if (marked) return entry;
                // The ledger says the items are gone and the player's own file does not. The only
                // way here is a player save that failed silently -- vanilla catches its own IO
                // errors and never rethrows -- so this server refuses to confirm rather than buy a
                // completion or a refund for items the player still holds. Preserved, not closed.
                LOGGER.error("event=quest_handin_marker_missing handin_uuid={} player_uuid={} state={}",
                        entry.handinUuid(), player.getStringUUID(), entry.localState());
                QuestHandinLedger.transition(server, entry.handinUuid(),
                        row -> row.withStranded("marker_missing", now));
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private static QuestHandinLedgerEntry promoteFromMarker(ServerPlayer player,
                                                            QuestHandinLedgerEntry entry, String trigger) {
        Optional<PlayerDataStore.HandinRemovalMarker> marker =
                PlayerDataStore.handinRemoval(player, entry.handinUuid());
        if (marker.isEmpty()) return null;
        long now = System.currentTimeMillis();
        LOGGER.info("event=quest_handin_removal_recovered handin_uuid={} player_uuid={} trigger={}",
                entry.handinUuid(), player.getStringUUID(), trigger);
        // The marker's proof, not the ledger's: it is the copy that was written in the same step as
        // the shrink, so it is the one that describes what actually left the pack.
        return QuestHandinLedger.transition(player.server, entry.handinUuid(),
                row -> row.withRemovalIntent(marker.get().proof(), now).withRemovedLocally(now))
                .orElse(null);
    }

    /** Sends the stored proof again, at most one attempt per transaction at a time. */
    private static boolean attemptConfirmation(ServerPlayer player, QuestHandinLedgerEntry entry, long now) {
        MinecraftServer server = player.server;
        Map<UUID, Long> schedule = NEXT_ATTEMPT_AT.computeIfAbsent(server, key -> new HashMap<>());
        if (!reserve(server, entry.handinUuid())) return false;
        Long due = schedule.get(entry.handinUuid());
        if (due != null && due > now) {
            release(server, entry.handinUuid());
            return false;
        }

        UUID connectedPlayerId = player.getUUID();
        UUID handinUuid = entry.handinUuid();
        QuestHandinLedger.transition(server, handinUuid, row -> row.withConfirming(now));
        QuestHandinLedgerEntry sending = QuestHandinLedger.find(server, handinUuid).orElse(entry);
        // The service's own seam, deliberately: a recovered confirmation must go exactly where a
        // live one goes, or a test could prove the live path and miss the recovery path entirely.
        QuestItemHandinService.confirmer().confirm(server, sending.confirmation())
                .whenComplete((result, failure) -> server.execute(() -> {
            release(server, handinUuid);
            ServerPlayer live = server.getPlayerList().getPlayer(connectedPlayerId);
            QuestHandinLedgerEntry stored = QuestHandinLedger.find(server, handinUuid).orElse(sending);
            if (live == null) {
                // Nothing is applied to a player who is gone; the row keeps its proof and the next
                // login sweeps it again. The schedule entry is dropped rather than backed off, so
                // this cannot write a ten-minute delay a moment after logout cleared one -- which
                // would be served out on the next login, exactly what the clear existed to avoid.
                schedule.remove(handinUuid);
                return;
            }
            QuestItemHandinService.settle(live, stored, result, failure, new com.google.gson.JsonObject(), applier);
            QuestHandinLedgerEntry after = QuestHandinLedger.find(server, handinUuid).orElse(stored);
            if (after.localState().settled() || after.localState() == QuestHandinLocalState.STRANDED) {
                schedule.remove(handinUuid);
            } else {
                backOff(schedule, handinUuid, after.attempts());
            }
        }));
        return true;
    }

    private static void backOff(Map<UUID, Long> schedule, UUID handinUuid, int attempts) {
        long delay = Math.min(MAX_RETRY_MILLIS, FIRST_RETRY_MILLIS * (1L << Math.min(6, Math.max(0, attempts - 1))));
        schedule.put(handinUuid, System.currentTimeMillis() + delay);
    }

    /** Every unsettled transaction on this server, for an operator report at boot. */
    public static List<QuestHandinLedgerEntry> outstanding(MinecraftServer server) {
        List<QuestHandinLedgerEntry> found = new ArrayList<>();
        for (QuestHandinLedgerEntry entry : QuestHandinLedgerStore.get(server).openEntries()) {
            if (entry.localState().itemsRemoved()) found.add(entry);
        }
        return List.copyOf(found);
    }
}
