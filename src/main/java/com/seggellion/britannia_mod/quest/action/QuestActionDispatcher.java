package com.seggellion.britannia_mod.quest.action;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.network.payload.QuestTriggerResultS2CPayload;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.QuestEntryParser;
import com.seggellion.britannia_mod.quest.QuestJournalRefresh;
import com.seggellion.britannia_mod.quest.QuestObjectiveTriggers;
import com.seggellion.britannia_mod.quest.QuestRewardService;
import com.seggellion.britannia_mod.quest.ServerQuestTable;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server-owned half of contract {@code quest_action_event} (protocol section 2.2 and 2.3):
 * turns a farming mutation the server has already committed into a durable outbox row, posts it,
 * and applies whatever Rails answers.
 *
 * <h2>What may become an event</h2>
 * Only a mutation that (a) actually happened -- every caller publishes AFTER its own success point
 * -- and (b) satisfies a subscription in this player's server-side journal
 * ({@link QuestActionSubscriptions}). Nothing a client says is consulted at any point, and
 * {@code QuestActionC2SPayload.TRIGGER} stays refused where it always was.
 *
 * <h2>Ordering</h2>
 * Enqueue-then-flush-then-attempt, in that order, always: section 2.2 requires the row to be on
 * disk before the first HTTP attempt, so a kill between the harvest and the response cannot lose
 * the event.
 */
public final class QuestActionDispatcher {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final int TICK_INTERVAL = 20;
    /** The watcher's own discipline (finding Q-06), applied to a subscription that just failed. */
    private static final long FAILURE_COOLDOWN_MILLIS = 10_000L;

    /** The transport, isolated exactly as {@code QuestJournalRefresh.Fetcher} is. */
    @FunctionalInterface
    public interface Sender {
        CompletableFuture<QuestActionEventClient.SendResult> send(MinecraftServer server, byte[] body);
    }

    private static final QuestActionEventClient CLIENT = new QuestActionEventClient();
    private static final Sender DEFAULT_SENDER = CLIENT::send;
    private static volatile Sender sender = DEFAULT_SENDER;
    private static volatile Clock clock = System::currentTimeMillis;

    /** RAM-only per-server bookkeeping; everything that must survive a restart is in the outbox. */
    private static final Map<MinecraftServer, State> STATES = new ConcurrentHashMap<>();

    private QuestActionDispatcher() {}

    @FunctionalInterface
    public interface Clock {
        long nowEpochMillis();
    }

    private static final class State {
        final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
        final Map<String, Long> cooldownUntil = new ConcurrentHashMap<>();
        volatile boolean endpointUnsupported;
    }

    public static long now() {
        return clock.nowEpochMillis();
    }

    private static State state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new State());
    }

    // --- publishing -------------------------------------------------------------------------

    /**
     * Records and posts one event per subscription this action satisfies. Returns the events that
     * were enqueued, which is what the GameTests assert on: exactly one per real mutation, none at
     * all for an interaction that failed (those callers never reach this method).
     */
    public static List<UUID> publish(ServerPlayer player, QuestAction action, ServerLevel level,
                                     BlockPos position, QuestActionSubject subject) {
        if (player == null || action == null || subject == null) return List.of();
        MinecraftServer server = player.server;
        if (server == null || level == null || level.isClientSide) return List.of();
        if (!subject.satisfies(action)) {
            // A success point that cannot describe itself is a mod defect, not a Rails one: log it
            // rather than posting an event Rails would answer 400 to forever.
            LOGGER.warn("event=quest_action_incomplete_subject player_uuid={} action={} fields={}",
                player.getStringUUID(), action.wireName(), subject.names());
            return List.of();
        }

        List<ClientQuestEntry> journal = ServerQuestTable.snapshot(player);
        List<QuestActionSubscriptions.Match> matches =
            QuestActionSubscriptions.matches(player.getUUID(), journal, action, subject);
        if (matches.isEmpty()) return List.of();

        State state = state(server);
        long now = now();
        BlockPos where = position == null ? player.blockPosition() : position;
        String dimension = level.dimension().location().toString();
        List<UUID> enqueued = new java.util.ArrayList<>();

        for (QuestActionSubscriptions.Match match : matches) {
            String cooldownKey = cooldownKey(player.getUUID(), match.questStateId(), match.triggerKey());
            Long cooldownUntil = state.cooldownUntil.get(cooldownKey);
            if (cooldownUntil != null && now < cooldownUntil) continue;
            if (QuestActionOutbox.hasOpenEntryFor(server, player.getUUID(), match.questStateId(), match.triggerKey())) {
                // The same objective is already owed to Rails. Sending a second copy could only be
                // answered duplicate or irrelevant, and during an outage it is how one player fills
                // the sixty-four-row bound with one repeated gesture.
                continue;
            }

            QuestActionEvent event = new QuestActionEvent(UUID.randomUUID(), player.getUUID(), action,
                QuestActionEventProtocol.formatOccurredAt(now), dimension,
                where.getX(), where.getY(), where.getZ(), subject,
                QuestActionEvent.Target.of(match.questStateId(), match.triggerKey()));

            QuestActionOutboxStore.RecordOutcome outcome =
                QuestActionOutbox.enqueue(server, QuestActionOutboxEntry.queued(event, now));
            if (outcome != QuestActionOutboxStore.RecordOutcome.CREATED) {
                LOGGER.warn("event=quest_action_not_enqueued player_uuid={} event_uuid={} action={} outcome={}",
                    player.getStringUUID(), event.eventUuid(), action.wireName(), outcome);
                continue;
            }
            LOGGER.info("event=quest_action_enqueued player_uuid={} event_uuid={} action={} quest_state_id={} "
                    + "trigger_key={} advancing={}",
                player.getStringUUID(), event.eventUuid(), action.wireName(), match.questStateId(),
                match.triggerKey(), match.advancing());
            enqueued.add(event.eventUuid());
            attempt(server, QuestActionOutboxEntry.queued(event, now));
        }
        return List.copyOf(enqueued);
    }

    // --- transmission -----------------------------------------------------------------------

    /** Posts one row. Safe to call for a row whose player is offline: the state change is Rails'. */
    public static void attempt(MinecraftServer server, QuestActionOutboxEntry entry) {
        if (server == null || entry == null) return;
        State state = state(server);
        if (state.endpointUnsupported) {
            dropForUnsupportedEndpoint(server, "endpoint_unsupported");
            return;
        }
        if (!state.inFlight.add(entry.eventUuid())) return;

        UUID requestUuid = UUID.randomUUID();
        byte[] body;
        try {
            body = QuestActionEventProtocol.encode(entry.event(), requestUuid);
        } catch (RuntimeException malformed) {
            // The mod built something it cannot encode. Retrying can never fix that, and section
            // 2.3 treats an encoder defect as terminal rather than an eternal retry.
            state.inFlight.remove(entry.eventUuid());
            LOGGER.error("event=quest_action_rejected event_uuid={} action={} reason=unencodable_event",
                entry.eventUuid(), entry.event().action().wireName(), malformed);
            QuestActionOutbox.remove(server, entry.eventUuid());
            return;
        }

        sender.send(server, body).whenComplete((result, failure) -> server.execute(() -> {
            state.inFlight.remove(entry.eventUuid());
            if (failure != null) {
                retryLater(server, entry, "transport_error", requestUuid);
                return;
            }
            handle(server, entry, result, requestUuid);
        }));
    }

    private static void handle(MinecraftServer server, QuestActionOutboxEntry entry,
                               QuestActionEventClient.SendResult result, UUID requestUuid) {
        State state = state(server);
        if (result instanceof QuestActionEventClient.Answered answered) {
            applyAnswer(server, entry, answered.response(), requestUuid);
            return;
        }
        if (result instanceof QuestActionEventClient.TerminalRejection rejection) {
            LOGGER.warn("event=quest_action_rejected event_uuid={} action={} quest_state_id={} trigger_key={} "
                    + "reason={} request_uuid={}",
                entry.eventUuid(), entry.event().action().wireName(), entry.questStateId(),
                entry.triggerKey(), rejection.code(), requestUuid);
            QuestActionOutbox.remove(server, entry.eventUuid());
            cooldown(server, entry);
            return;
        }
        if (result instanceof QuestActionEventClient.EndpointUnsupported) {
            if (!state.endpointUnsupported) {
                state.endpointUnsupported = true;
                LOGGER.warn("event=quest_action_endpoint_unsupported detail=this Rails does not serve "
                    + "/api/v2/quest_action_events; farming objectives fall back to the legacy observers");
            }
            dropForUnsupportedEndpoint(server, "endpoint_unsupported");
            return;
        }
        String code = result instanceof QuestActionEventClient.Failure fail ? fail.safeCode() : "transport_error";
        retryLater(server, entry, code, requestUuid);
    }

    private static void applyAnswer(MinecraftServer server, QuestActionOutboxEntry entry,
                                    QuestActionEventProtocol.Response response, UUID requestUuid) {
        // Every one of the five results is terminal (section 2.3): the row goes first, so a crash
        // between here and the journal update can never resend an event Rails already applied.
        QuestActionOutbox.remove(server, entry.eventUuid());
        LOGGER.info("event=quest_action_{} event_uuid={} action={} quest_state_id={} trigger_key={} "
                + "reason={} request_uuid={}",
            response.result().wireName(), entry.eventUuid(), entry.event().action().wireName(),
            entry.questStateId(), entry.triggerKey(), response.reason(), requestUuid);

        ServerPlayer player = server.getPlayerList().getPlayer(entry.playerUuid());
        if (player == null) return;

        switch (response.result()) {
            case APPLIED, DUPLICATE -> installState(player, entry, response, requestUuid);
            case STALE -> QuestJournalRefresh.refresh(player, () -> {});
            // A refusal cools the objective down for ten seconds, the discipline the watcher has
            // used since finding Q-06. `irrelevant` deliberately does NOT: section 2.3 says only
            // "remove", and Rails answering "nothing subscribes to this" is not a reason to stop
            // reporting the player's NEXT real mutation of the same kind.
            case REJECTED -> cooldown(server, entry);
            case IRRELEVANT -> { }
        }
    }

    /**
     * Installs the state Rails just published: rewards through the delivery ledger, the journal's
     * new objectives, and the existing client notice. Deliberately the same three steps, in the
     * same order, as {@code QuestObjectiveWatcher#applySuccess} -- updating the journal is what
     * stops the objective firing again on the next gesture.
     */
    private static void installState(ServerPlayer player, QuestActionOutboxEntry entry,
                                     QuestActionEventProtocol.Response response, UUID requestUuid) {
        JsonObject root = response.root();
        QuestModels.QuestResponse parsed;
        try {
            parsed = GSON.fromJson(root, QuestModels.QuestResponse.class);
        } catch (RuntimeException malformed) {
            LOGGER.warn("event=quest_action_state_unreadable event_uuid={} error={}",
                entry.eventUuid(), malformed.toString());
            return;
        }
        if (parsed == null) return;
        parsed.success = true;

        QuestRewardService.apply(player, parsed, root, requestUuid.toString());

        String questStateId = response.questStateId().isBlank() ? entry.questStateId() : response.questStateId();
        if (parsed.completed) {
            if (parsed.quest_id > 0) {
                ServerQuestTable.removeAfterRailsCompletionSuccessByQuestId(player, parsed.quest_id);
            }
        } else {
            // One parse, both halves. The objectives are what stop the trigger firing again; the
            // journal detail is what anything reading the mirror between this advance and the next
            // full sync -- the quiet objective notice, the journal screen, a sync raised by
            // something else -- would otherwise show from before it.
            ClientQuestEntry advanced = advancedJournalEntry(root);
            ServerQuestTable.updateJournal(player, questStateId,
                updatedTriggers(advanced, parsed),
                advanced == null ? null : advanced.detail());
        }

        PacketDistributor.sendToPlayer(player,
            new QuestTriggerResultS2CPayload(GSON.toJson(root), parsed.quest_id, entry.triggerKey()));
    }

    /**
     * The journal entry Rails published with this advance, or {@code null} when the response
     * carried none. Both the objectives and the journal detail come from it, so it is parsed once.
     */
    private static ClientQuestEntry advancedJournalEntry(JsonObject root) {
        List<ClientQuestEntry> refreshed = QuestEntryParser.parseAcceptedQuests(root);
        return refreshed.isEmpty() ? null : refreshed.get(0);
    }

    /**
     * The objectives now pending. {@code accepted_quest.triggers} is preferred because it is the
     * published form the subscription matcher reads (resolved placeholders, bound values, per-step
     * {@code done}); the node's raw metadata is the fallback for a response that carries no
     * journal entry.
     */
    private static QuestObjectiveTriggers updatedTriggers(ClientQuestEntry advanced,
                                                          QuestModels.QuestResponse parsed) {
        if (advanced != null) return advanced.triggers();
        return parsed.currentNode == null
            ? QuestObjectiveTriggers.NONE
            : QuestObjectiveTriggers.fromNodeMetadata(parsed.currentNode.metadata);
    }

    private static void retryLater(MinecraftServer server, QuestActionOutboxEntry entry,
                                   String reason, UUID requestUuid) {
        QuestActionOutbox.note(server, entry.eventUuid(), existing -> existing.withFailure(reason, now()))
            .ifPresent(updated -> LOGGER.warn("event=quest_action_retry event_uuid={} action={} quest_state_id={} "
                    + "trigger_key={} reason={} attempts={} next_attempt_in_ms={} request_uuid={}",
                updated.eventUuid(), updated.event().action().wireName(), updated.questStateId(),
                updated.triggerKey(), reason, updated.attempts(),
                QuestActionBackoff.delayMillis(updated.attempts()), requestUuid));
    }

    private static void dropForUnsupportedEndpoint(MinecraftServer server, String reason) {
        int dropped = QuestActionOutbox.removeAll(server);
        if (dropped > 0) {
            LOGGER.warn("event=quest_action_dropped reason={} count={}", reason, dropped);
        }
    }

    private static void cooldown(MinecraftServer server, QuestActionOutboxEntry entry) {
        if (entry.questStateId().isBlank() || entry.triggerKey().isBlank()) return;
        state(server).cooldownUntil.put(
            cooldownKey(entry.playerUuid(), entry.questStateId(), entry.triggerKey()),
            now() + FAILURE_COOLDOWN_MILLIS);
    }

    private static String cooldownKey(UUID playerUuid, String questStateId, String triggerKey) {
        return playerUuid + "|" + questStateId + "|" + triggerKey;
    }

    // --- schedule ---------------------------------------------------------------------------

    /** The periodic pass; called from {@code ServerTickEvent.Post}. */
    public static void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % TICK_INTERVAL != 0) return;
        State state = state(server);
        if (state.endpointUnsupported) return;
        long now = now();
        for (QuestActionOutboxEntry entry : QuestActionOutbox.all(server)) {
            if (entry.dueAt(now)) attempt(server, entry);
        }
    }

    /** Section 2.2: "on player login the player's entries are flushed first". */
    public static void onLogin(ServerPlayer player) {
        if (player == null || player.server == null) return;
        MinecraftServer server = player.server;
        for (QuestActionOutboxEntry entry : QuestActionOutbox.entriesFor(server, player.getUUID())) {
            QuestActionOutbox.note(server, entry.eventUuid(), QuestActionOutboxEntry::dueNow);
            attempt(server, entry.dueNow());
        }
    }

    /** Section 2.2: "on server start the whole outbox is scheduled". */
    public static void onServerStarted(MinecraftServer server) {
        if (server == null) return;
        List<QuestActionOutboxEntry> pending = QuestActionOutbox.all(server);
        if (pending.isEmpty()) return;
        LOGGER.info("event=quest_action_outbox_resumed count={}", pending.size());
        for (QuestActionOutboxEntry entry : pending) {
            QuestActionOutbox.note(server, entry.eventUuid(), QuestActionOutboxEntry::dueNow);
            attempt(server, entry.dueNow());
        }
    }

    public static void forgetPlayer(MinecraftServer server, UUID playerUuid) {
        if (server == null || playerUuid == null) return;
        State state = STATES.get(server);
        if (state == null) return;
        String prefix = playerUuid + "|";
        state.cooldownUntil.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public static void clear(MinecraftServer server) {
        if (server != null) STATES.remove(server);
    }

    // --- test seams -------------------------------------------------------------------------

    /** Installs a stand-in transport. Tests only; always restore with {@link #resetSender()}. */
    public static void installSender(Sender replacement) {
        sender = replacement == null ? DEFAULT_SENDER : replacement;
    }

    public static void resetSender() {
        sender = DEFAULT_SENDER;
    }

    /** Installs a stand-in clock so a test can step over the retry schedule without waiting. */
    public static void installClock(Clock replacement) {
        clock = replacement == null ? System::currentTimeMillis : replacement;
    }

    public static void resetClock() {
        clock = System::currentTimeMillis;
    }

    /** Test-only: forgets the "this Rails has no such route" verdict recorded for a server. */
    public static void resetEndpointSupport(MinecraftServer server) {
        if (server != null) state(server).endpointUnsupported = false;
    }

    public static boolean isEndpointUnsupported(MinecraftServer server) {
        return server != null && state(server).endpointUnsupported;
    }
}
