package com.seggellion.britannia_mod.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryReconciler;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

/**
 * On-demand refresh of one player's quest journal (Milestone 5, finding Q-03).
 *
 * <p>{@link ServerQuestTable} is RAM only and is filled exclusively by the login bootstrap, which
 * {@code WorldBootstrapHandler} is explicitly allowed to abandon on a timeout, a queue rejection,
 * a stale generation, or unavailable server authentication. A player whose bootstrap failed had
 * an empty journal for the rest of the session, and because that journal is what authorizes every
 * quest write, every quest action they attempted was refused. Silently, until Milestone 2.
 *
 * <p>The fix is not to trust the empty journal less; it is to stop treating "I have no record" as
 * "there is nothing to have". A miss against a journal that was never loaded now fetches it and
 * re-evaluates, so a failed bootstrap costs one round trip instead of a session.
 *
 * <p>Bounded on purpose: one fetch in flight per player, a cooldown after a failure, and a small
 * cap on how many actions may wait on a single fetch. A refetch triggered by a miss must never
 * become a way to make the server hammer Rails.
 */
public final class QuestJournalRefresh {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RESPONSE_BYTES = 262_144;
    private static final int MAX_WAITERS_PER_PLAYER = 4;
    private static final long FAILURE_COOLDOWN_MILLIS = 15_000L;

    /** The transport, isolated so tests can drive the policy without a Rails server. */
    @FunctionalInterface
    public interface Fetcher {
        /** The player's active journal, or empty when the journal could not be fetched. */
        Optional<List<ClientQuestEntry>> fetch(MinecraftServer server, ServerPlayer player);
    }

    private static final Map<UUID, List<Runnable>> WAITING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();
    private static volatile Fetcher fetcher = QuestJournalRefresh::fetchFromRails;

    private QuestJournalRefresh() {}

    /** Outcome of asking for a refresh, so callers can explain themselves. */
    public enum Request { STARTED, JOINED_IN_FLIGHT, REFUSED_COOLDOWN, REFUSED_TOO_MANY_WAITERS }

    /**
     * Fetches this player's journal and runs {@code whenLoaded} on the server thread afterwards,
     * whether the fetch succeeded or not -- the caller re-evaluates either way, and a failed
     * refresh must still produce an answer rather than leaving the action hanging.
     */
    public static Request refresh(ServerPlayer player, Runnable whenLoaded) {
        if (player == null) return Request.REFUSED_COOLDOWN;
        UUID playerId = player.getUUID();

        Long cooldownUntil = COOLDOWN_UNTIL.get(playerId);
        if (cooldownUntil != null && System.currentTimeMillis() < cooldownUntil) {
            return Request.REFUSED_COOLDOWN;
        }

        boolean[] started = { false };
        boolean[] refusedForCapacity = { false };
        WAITING.compute(playerId, (uuid, waiting) -> {
            if (waiting == null) {
                started[0] = true;
                List<Runnable> fresh = new ArrayList<>();
                fresh.add(whenLoaded);
                return fresh;
            }
            if (waiting.size() >= MAX_WAITERS_PER_PLAYER) {
                refusedForCapacity[0] = true;
                return waiting;
            }
            waiting.add(whenLoaded);
            return waiting;
        });

        if (refusedForCapacity[0]) return Request.REFUSED_TOO_MANY_WAITERS;
        if (!started[0]) return Request.JOINED_IN_FLIGHT;

        MinecraftServer server = player.server;
        try {
            ServerHttpExecutor.submit(server, () -> fetcher.fetch(server, player))
                .whenComplete((quests, failure) -> server.execute(() -> complete(player, quests, failure)));
        } catch (RejectedExecutionException rejected) {
            server.execute(() -> complete(player, Optional.empty(), rejected));
        }
        return Request.STARTED;
    }

    private static void complete(ServerPlayer player, Optional<List<ClientQuestEntry>> quests, Throwable failure) {
        UUID playerId = player.getUUID();
        boolean loaded = failure == null && quests != null && quests.isPresent();

        if (loaded) {
            ServerQuestTable.replaceFromBootstrap(player, quests.get());
            COOLDOWN_UNTIL.remove(playerId);
            LOGGER.info("event=quest_journal_refreshed player_uuid={} quest_count={}",
                player.getStringUUID(), quests.get().size());
            // M3: a journal refresh is one of the delivery reconciliation triggers (protocol 1.8).
            QuestRewardDeliveryReconciler.onJournalRefreshed(player);
            // And of the hand-in ones (protocol 1.5.3): a player who opens their journal after a
            // dialogue that went quiet is asking the same question the sweep answers.
            com.seggellion.britannia_mod.quest.handin.QuestItemHandinReconciler.onJournalRefreshed(player);
            // A refresh is also the first moment after a login where the journal is level with
            // Rails, so it is where a returning player is reminded what their current stage still
            // needs. Throttled per stage and silent when they are already carrying everything.
            for (ClientQuestEntry entry : quests.get()) {
                RowanQuestlineHooks.sendMaterialGuidance(player, entry.questKey());
            }
        } else {
            // A failed refresh must not become a hot loop against a service that is already
            // struggling; the player's next action after the cooldown tries again.
            COOLDOWN_UNTIL.put(playerId, System.currentTimeMillis() + FAILURE_COOLDOWN_MILLIS);
            LOGGER.warn("event=quest_journal_refresh_failed player_uuid={} error={}",
                player.getStringUUID(), failure == null ? "empty_response" : failure.toString());
        }

        List<Runnable> waiting = WAITING.remove(playerId);
        if (waiting == null) return;
        for (Runnable callback : waiting) {
            try {
                callback.run();
            } catch (RuntimeException error) {
                LOGGER.warn("event=quest_journal_refresh_callback_failed player_uuid={} error={}",
                    player.getStringUUID(), error.toString());
            }
        }
    }

    /** Forgets everything remembered about a player; called when they log out. */
    public static void forget(UUID playerId) {
        if (playerId == null) return;
        WAITING.remove(playerId);
        COOLDOWN_UNTIL.remove(playerId);
    }

    private static Optional<List<ClientQuestEntry>> fetchFromRails(MinecraftServer server, ServerPlayer player) {
        try {
            var requestUri = ServerAuthRegistry.credentials(server).orElseThrow().apiUrls()
                .resolvePath(Endpoint.QUEST_JOURNAL, Map.of("player_uuid", player.getStringUUID()));
            HttpURLConnection connection = (HttpURLConnection) requestUri.toURL().openConnection();
            BoundedHttp.configure(connection);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            if (!RailsRequestAuthenticator.apply(connection, server, new byte[0])) {
                throw new IllegalStateException("Server authentication unavailable");
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                LOGGER.warn("event=quest_journal_fetch_rejected player_uuid={} status={}",
                    player.getStringUUID(), status);
                return Optional.empty();
            }

            try (InputStream input = connection.getInputStream()) {
                JsonObject root = JsonParser.parseString(BoundedHttp.readUtf8(input, MAX_RESPONSE_BYTES))
                    .getAsJsonObject();
                return Optional.of(QuestEntryParser.parseAcceptedQuests(root));
            }
        } catch (Exception error) {
            LOGGER.warn("event=quest_journal_fetch_failed player_uuid={} error={}",
                player.getStringUUID(), error.toString());
            return Optional.empty();
        }
    }

    // --- test seams -------------------------------------------------------------------------

    /** Installs a stand-in transport. Tests only; always restore with {@link #resetFetcher()}. */
    public static void installFetcher(Fetcher replacement) {
        fetcher = replacement == null ? QuestJournalRefresh::fetchFromRails : replacement;
    }

    public static void resetFetcher() {
        fetcher = QuestJournalRefresh::fetchFromRails;
    }

    public static void clearCooldown(UUID playerId) {
        if (playerId != null) COOLDOWN_UNTIL.remove(playerId);
    }
}
