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

    private static final Map<MinecraftServer, Integer> TICKS = new ConcurrentHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, Long>> NEXT_ATTEMPT_AT = new ConcurrentHashMap<>();
    private static final Map<MinecraftServer, Set<UUID>> IN_FLIGHT = new ConcurrentHashMap<>();

    private static QuestItemHandinClient client = new QuestItemHandinClient();
    private static QuestItemHandinService.CompletionApplier applier = (player, response) -> "";

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

    public static void useClientForTesting(QuestItemHandinClient testClient) {
        client = Objects.requireNonNull(testClient, "testClient");
    }

    public static void resetClientForTesting() {
        client = new QuestItemHandinClient();
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
    }

    public static void clear(MinecraftServer server) {
        TICKS.remove(server);
        NEXT_ATTEMPT_AT.remove(server);
        IN_FLIGHT.remove(server);
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
                    "rebuilt_from_marker");
            QuestHandinLedgerStore.RecordOutcome outcome = QuestHandinLedger.record(player.server, rebuilt);
            LOGGER.warn("event=quest_handin_marker_adopted handin_uuid={} player_uuid={} outcome={} trigger={}",
                    marker.handinUuid(), player.getStringUUID(), outcome, trigger);
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
                    // Cannot normally happen -- a marker is only written with an intent recorded --
                    // but the player's file is the authority on the mutation either way.
                    return promoteFromMarker(player, entry, trigger);
                }
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
        Set<UUID> inFlight = IN_FLIGHT.computeIfAbsent(server, key -> ConcurrentHashMap.newKeySet());
        Map<UUID, Long> schedule = NEXT_ATTEMPT_AT.computeIfAbsent(server, key -> new HashMap<>());
        if (!inFlight.add(entry.handinUuid())) return false;
        Long due = schedule.get(entry.handinUuid());
        if (due != null && due > now) {
            inFlight.remove(entry.handinUuid());
            return false;
        }

        UUID connectedPlayerId = player.getUUID();
        UUID handinUuid = entry.handinUuid();
        QuestHandinLedger.transition(server, handinUuid, row -> row.withConfirming(now));
        QuestHandinLedgerEntry sending = QuestHandinLedger.find(server, handinUuid).orElse(entry);
        client.confirm(server, sending.confirmation()).whenComplete((result, failure) -> server.execute(() -> {
            inFlight.remove(handinUuid);
            ServerPlayer live = server.getPlayerList().getPlayer(connectedPlayerId);
            QuestHandinLedgerEntry stored = QuestHandinLedger.find(server, handinUuid).orElse(sending);
            if (live == null) {
                // Nothing is applied to a player who is gone; the row keeps its proof and the next
                // login sweeps it again.
                backOff(schedule, handinUuid, stored.attempts());
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
