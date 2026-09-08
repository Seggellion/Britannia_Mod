package com.seggellion.britannia_mod.quest.delivery;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When the delivery sequence runs (protocol section 1.8, step 6 and the failure model):
 *
 * <ul>
 *   <li><b>Login</b>: the bootstrap's {@code pending_reward_deliveries} are applied after the
 *       journal is installed, then the player's open ledger rows are swept.</li>
 *   <li><b>Journal refresh</b>: the v2 pending listing is fetched on demand (rate-limited per
 *       player, skipped for the rest of the boot once the endpoint answered 404), applied, and
 *       the open rows are swept.</li>
 *   <li><b>Periodic</b>: once a second the online players with open rows are visited. A
 *       {@code queued} row retries its insertion when the main inventory changed or every
 *       30 s; a {@code pending_local} row is re-applied; an outstanding acknowledgement is
 *       retried on {@link QuestRewardDeliveryBackoff}'s schedule.</li>
 * </ul>
 *
 * <p>Everything is keyed on ONLINE players. A row for an offline player waits for their next
 * login, which is where the bootstrap re-lists whatever Rails still holds as pending.
 */
public final class QuestRewardDeliveryReconciler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TICK_INTERVAL = 20;
    private static final long PENDING_FETCH_MIN_INTERVAL_MILLIS = 30_000L;

    @FunctionalInterface
    public interface PendingFetcher {
        CompletableFuture<QuestRewardDeliveryClient.PendingResult> fetch(MinecraftServer server, UUID playerUuid);
    }

    private static final QuestRewardDeliveryClient CLIENT = new QuestRewardDeliveryClient();
    private static final PendingFetcher DEFAULT_PENDING_FETCHER = CLIENT::fetchPending;
    private static volatile PendingFetcher pendingFetcher = DEFAULT_PENDING_FETCHER;

    private static final Map<MinecraftServer, State> STATES = new ConcurrentHashMap<>();

    private QuestRewardDeliveryReconciler() {}

    /** Per-server, server-thread-only bookkeeping. */
    private static final class State {
        final Map<UUID, Integer> inventoryFingerprints = new HashMap<>();
        final Map<UUID, Long> lastQueuedRetryAt = new HashMap<>();
        final Map<UUID, Long> lastPendingFetchAt = new HashMap<>();
        final Set<UUID> pendingFetchInFlight = new HashSet<>();
        boolean pendingEndpointUnsupported;
    }

    /** Login: apply what the bootstrap listed, then sweep the ledger. Runs after the journal is installed. */
    public static void onLogin(ServerPlayer player, List<QuestRewardDelivery> bootstrapPending) {
        if (player == null) return;
        Set<UUID> handled = new HashSet<>();
        if (bootstrapPending != null) {
            for (QuestRewardDelivery delivery : bootstrapPending) {
                QuestRewardDeliveryService.apply(player, delivery, QuestRewardDeliveryService.Source.BOOTSTRAP);
                handled.add(delivery.deliveryUuid());
            }
        }
        // The rows Rails just listed have had their immediate attempt; sweeping them again in the
        // same trigger would post a second acknowledgement straight past the backoff.
        sweep(player, handled, false);
        remindIfQueued(player);
    }

    /** Journal refresh: ask Rails what it still holds, then sweep the ledger. */
    public static void onJournalRefreshed(ServerPlayer player) {
        if (player == null) return;
        fetchPendingAndApply(player);
        sweep(player, Set.of(), false);
    }

    /**
     * A pass over the player's open rows outside the protocol's own triggers: owed items are
     * inserted at once, but an acknowledgement that has already failed waits out its backoff.
     * Only login and a journal refresh (section 1.8, step 6) attempt regardless of the schedule.
     */
    public static void reconcileNow(ServerPlayer player) {
        if (player == null) return;
        sweep(player, Set.of(), true);
    }

    /** The periodic pass; called from {@code ServerTickEvent.Post}. */
    public static void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % TICK_INTERVAL != 0) return;
        State state = state(server);
        long now = QuestRewardDeliveryService.now();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerUuid = player.getUUID();
            List<QuestRewardDeliveryLedgerEntry> open = QuestRewardDeliveryLedger.openEntriesFor(server, playerUuid);
            if (open.isEmpty()) {
                state.inventoryFingerprints.remove(playerUuid);
                state.lastQueuedRetryAt.remove(playerUuid);
                continue;
            }

            boolean anyQueued = open.stream().anyMatch(entry -> entry.localState() == QuestRewardDeliveryLocalState.QUEUED);
            boolean queuedDue = false;
            if (anyQueued) {
                int fingerprint = inventoryFingerprint(player);
                Integer previous = state.inventoryFingerprints.put(playerUuid, fingerprint);
                boolean changed = previous != null && previous != fingerprint;
                long lastRetry = state.lastQueuedRetryAt.getOrDefault(playerUuid, 0L);
                queuedDue = changed || now - lastRetry >= QuestRewardDeliveryBackoff.QUEUED_RETRY_MILLIS;
                if (queuedDue) state.lastQueuedRetryAt.put(playerUuid, now);
            }

            for (QuestRewardDeliveryLedgerEntry entry : open) {
                switch (entry.localState()) {
                    case QUEUED -> {
                        if (queuedDue) QuestRewardDeliveryService.reconcile(player, entry, true);
                        else if (entry.acknowledgementOutstanding()
                            && QuestRewardDeliveryService.acknowledgementDue(entry.deliveryUuid(), now)) {
                            QuestRewardDeliveryService.acknowledge(server, entry.deliveryUuid(), true);
                        }
                    }
                    case PENDING_LOCAL, APPLIED -> QuestRewardDeliveryService.reconcile(player, entry, true);
                    case ACKNOWLEDGED -> { }
                }
            }
            if (anyQueued) {
                // The insertion may have changed the inventory; do not read that back as a player edit.
                state.inventoryFingerprints.put(playerUuid, inventoryFingerprint(player));
            }
        }
    }

    /**
     * A delivery was just queued against this inventory: take the baseline the "on inventory
     * change" retry compares against now, so the first change after queueing -- not the second --
     * is the one that triggers it.
     */
    public static void noteQueued(ServerPlayer player) {
        if (player == null || player.server == null) return;
        State state = state(player.server);
        state.inventoryFingerprints.put(player.getUUID(), inventoryFingerprint(player));
        state.lastQueuedRetryAt.put(player.getUUID(), QuestRewardDeliveryService.now());
    }

    public static void forgetPlayer(MinecraftServer server, UUID playerUuid) {
        if (server == null || playerUuid == null) return;
        State state = STATES.get(server);
        if (state == null) return;
        state.inventoryFingerprints.remove(playerUuid);
        state.lastQueuedRetryAt.remove(playerUuid);
        state.lastPendingFetchAt.remove(playerUuid);
    }

    public static void clear(MinecraftServer server) {
        if (server != null) STATES.remove(server);
    }

    public static boolean pendingEndpointUnsupported(MinecraftServer server) {
        State state = server == null ? null : STATES.get(server);
        return state != null && state.pendingEndpointUnsupported;
    }

    /**
     * @param alreadyVisited deliveries this same trigger has already put through the apply
     *                       sequence, so one trigger never produces two Rails calls for one row
     * @param respectSchedule whether an outstanding acknowledgement must wait for its backoff
     */
    private static void sweep(ServerPlayer player, Set<UUID> alreadyVisited, boolean respectSchedule) {
        MinecraftServer server = player.server;
        if (server == null) return;
        for (QuestRewardDeliveryLedgerEntry entry : QuestRewardDeliveryLedger.openEntriesFor(server, player.getUUID())) {
            if (alreadyVisited.contains(entry.deliveryUuid())) continue;
            QuestRewardDeliveryService.reconcile(player, entry, respectSchedule);
        }
    }

    private static void remindIfQueued(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (server == null) return;
        boolean queued = QuestRewardDeliveryLedger.openEntriesFor(server, player.getUUID()).stream()
            .anyMatch(entry -> entry.localState() == QuestRewardDeliveryLocalState.QUEUED);
        if (queued) QuestRewardDeliveryNotices.queued(player);
    }

    private static void fetchPendingAndApply(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (server == null) return;
        State state = state(server);
        if (state.pendingEndpointUnsupported) return;
        UUID playerUuid = player.getUUID();
        long now = QuestRewardDeliveryService.now();
        if (now - state.lastPendingFetchAt.getOrDefault(playerUuid, Long.MIN_VALUE / 2) < PENDING_FETCH_MIN_INTERVAL_MILLIS) return;
        if (!state.pendingFetchInFlight.add(playerUuid)) return;
        state.lastPendingFetchAt.put(playerUuid, now);

        CompletableFuture<QuestRewardDeliveryClient.PendingResult> future;
        try {
            future = pendingFetcher.fetch(server, playerUuid);
        } catch (RuntimeException failure) {
            future = CompletableFuture.failedFuture(failure);
        }
        if (future == null) future = CompletableFuture.completedFuture(new QuestRewardDeliveryClient.Failure("no_response"));
        future.whenComplete((result, failure) -> server.execute(() -> completePendingFetch(server, player, result, failure)));
    }

    private static void completePendingFetch(MinecraftServer server, ServerPlayer player,
                                             QuestRewardDeliveryClient.PendingResult result, Throwable failure) {
        State state = state(server);
        UUID playerUuid = player.getUUID();
        state.pendingFetchInFlight.remove(playerUuid);
        if (server.getPlayerList().getPlayer(playerUuid) != player) return;

        QuestRewardDeliveryClient.PendingResult outcome = failure == null && result != null
            ? result : new QuestRewardDeliveryClient.Failure("transport_error");
        switch (outcome) {
            case QuestRewardDeliveryClient.PendingFetched fetched -> {
                if (!fetched.listing().playerUuid().equals(playerUuid)) {
                    LOGGER.warn("event=quest_delivery_rejected source=pending_listing reason=player_mismatch player_uuid={}",
                        playerUuid);
                    return;
                }
                Set<UUID> listed = new HashSet<>();
                for (QuestRewardDelivery delivery : fetched.listing().deliveries()) {
                    QuestRewardDeliveryService.apply(player, delivery, QuestRewardDeliveryService.Source.PENDING_LISTING);
                    listed.add(delivery.deliveryUuid());
                }
                // The refresh's own sweep already gave every open row its immediate attempt.
                sweep(player, listed, true);
            }
            case QuestRewardDeliveryClient.EndpointUnsupported unsupported -> {
                if (!state.pendingEndpointUnsupported) {
                    state.pendingEndpointUnsupported = true;
                    LOGGER.warn("event=quest_delivery_endpoint_unsupported endpoint=quest_reward_deliveries_pending "
                        + "detail=rails_without_v2_reward_deliveries; on-demand reconciliation is suspended until the next server start");
                }
            }
            case QuestRewardDeliveryClient.Failure transientFailure -> LOGGER.warn(
                "event=quest_delivery_pending_fetch_failed player_uuid={} error={}", playerUuid, transientFailure.safeCode());
        }
    }

    /** A cheap digest of the main inventory: slot, item, count. Components are irrelevant to "did it change". */
    static int inventoryFingerprint(ServerPlayer player) {
        int hash = 17;
        List<ItemStack> slots = player.getInventory().items;
        for (int index = 0; index < slots.size(); index++) {
            ItemStack stack = slots.get(index);
            hash = hash * 31 + index;
            if (stack.isEmpty()) continue;
            hash = hash * 31 + BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
            hash = hash * 31 + stack.getCount();
        }
        return hash;
    }

    private static State state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new State());
    }

    // --- test seams -------------------------------------------------------------------------

    public static void installPendingFetcher(PendingFetcher replacement) {
        pendingFetcher = replacement == null ? DEFAULT_PENDING_FETCHER : replacement;
    }

    public static void resetPendingFetcher() {
        pendingFetcher = DEFAULT_PENDING_FETCHER;
    }

    /** Test-only: lets a journal refresh fetch again without waiting out the per-player interval. */
    public static void clearPendingFetchIntervalForTesting(MinecraftServer server, UUID playerUuid) {
        State state = server == null ? null : STATES.get(server);
        if (state != null && playerUuid != null) state.lastPendingFetchAt.remove(playerUuid);
    }
}
