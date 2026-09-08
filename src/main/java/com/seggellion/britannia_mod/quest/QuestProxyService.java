package com.seggellion.britannia_mod.quest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.QuestGiverEntity;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.QuestPayloadHandler;
import com.seggellion.britannia_mod.network.payload.ClientboundSyncQuestsPayload;
import com.seggellion.britannia_mod.network.payload.QuestActionC2SPayload;
import com.seggellion.britannia_mod.network.payload.QuestActionResultS2CPayload;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

public final class QuestProxyService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final int MAX_RESPONSE_BYTES = 262_144;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final String INVALID_QUEST_ACTION = "{\"success\":false,\"error\":\"invalid_quest_action\"}";
    /** A turn-in gets one second chance; anything more is guessing. */
    static final int MAX_TURN_IN_ATTEMPTS = 2;

    private QuestProxyService() {}

    public static void handle(ServerPlayer player, QuestActionC2SPayload request) {
        // Every rejection below used to be the same unlogged 422 (finding S-1): several distinct
        // causes, one silent response, no way to tell them apart afterwards.
        if (request == null) {
            QuestActionTelemetry.rejected(player, null, QuestActionTelemetry.Stage.SHAPE,
                "null_payload", ServerQuestTable.journalSize(player == null ? null : player.getUUID()));
            send(player, 0L, 422, INVALID_QUEST_ACTION);
            return;
        }
        if (!isValidShape(request)) {
            reject(player, request, QuestActionTelemetry.Stage.SHAPE, shapeRejectionReason(request));
            return;
        }

        // Milestone 6 (finding Q-05): objective progress is decided by the server. A TRIGGER
        // arriving from a client is either a stale build or someone claiming an objective they
        // have not met, and either way it is not evidence of anything.
        if (request.action() == QuestActionC2SPayload.Action.TRIGGER) {
            reject(player, request, QuestActionTelemetry.Stage.SHAPE, "client_trigger_not_authoritative");
            return;
        }

        ResolvedIntent intent = resolve(player, request);
        if (intent == null) {
            reject(player, request, QuestActionTelemetry.Stage.NPC_RESOLVE, "quest_giver_unresolved");
            return;
        }
        if (!authorizedForCurrentJournal(player, request)) {
            // A miss against a journal we never loaded is not evidence that the quest is not
            // the player's (finding Q-03): the login bootstrap is allowed to fail, and when it
            // does this table stays empty for the session. Fetch it once and decide properly.
            if (!ServerQuestTable.journalLoaded(player.getUUID())) {
                QuestJournalRefresh.Request outcome = QuestJournalRefresh.refresh(player,
                    () -> resumeAfterJournalRefresh(player, request, intent));
                if (outcome == QuestJournalRefresh.Request.STARTED
                    || outcome == QuestJournalRefresh.Request.JOINED_IN_FLIGHT) {
                    LOGGER.info("event=quest_journal_refresh_requested request_uuid={} player_uuid={} outcome={}",
                        request.requestUuid(), player.getStringUUID(), outcome);
                    return;
                }
                reject(player, request, QuestActionTelemetry.Stage.JOURNAL_GATE,
                    outcome == QuestJournalRefresh.Request.REFUSED_COOLDOWN
                        ? "journal_refresh_cooling_down" : "journal_refresh_saturated");
                return;
            }
            reject(player, request, QuestActionTelemetry.Stage.JOURNAL_GATE, journalRejectionReason(player));
            return;
        }

        QuestActionTelemetry.requested(player, request, intent.argument());
        dispatch(player, request, intent, 1);
    }

    /**
     * The second and final look at an action that missed a cold journal. Whatever the refresh
     * achieved, this answers: there is no third attempt, so a player never waits on a loop.
     */
    private static void resumeAfterJournalRefresh(ServerPlayer player, QuestActionC2SPayload request,
                                                  ResolvedIntent intent) {
        if (player.server.getPlayerList().getPlayer(player.getUUID()) != player) {
            QuestActionTelemetry.rejected(player, request, QuestActionTelemetry.Stage.RESPONSE,
                "player_session_changed_before_journal_refresh_completed",
                ServerQuestTable.journalSize(player.getUUID()));
            return;
        }
        if (!authorizedForCurrentJournal(player, request)) {
            reject(player, request, QuestActionTelemetry.Stage.JOURNAL_GATE,
                ServerQuestTable.journalLoaded(player.getUUID())
                    ? "quest_not_in_server_journal_after_refresh"
                    : "server_journal_unavailable");
            return;
        }
        QuestActionTelemetry.requested(player, request, intent.argument());
        dispatch(player, request, intent, 1);
    }

    /**
     * Sends the action to Rails, retrying a turn-in ONCE on an unavailable service.
     *
     * <p>The retry carries the same {@code requestUuid}, which is the entire point: if the first
     * attempt actually committed and only its response was lost, Rails replays that stored
     * completion -- same reward, granted once overall -- instead of refusing a quest it has
     * already finished (finding Q-04). Retrying anything else, or retrying more than once, would
     * be a guess; this is a single, bounded second chance for the one action whose loss costs a
     * player their reward.
     */
    private static void dispatch(ServerPlayer player, QuestActionC2SPayload request,
                                 ResolvedIntent intent, int attempt) {
        MinecraftServer server = player.server;
        String playerUuid = player.getStringUUID();
        UUID connectedPlayerId = player.getUUID();
        try {
            ServerHttpExecutor.submit(server, () -> callRails(server, playerUuid, request, intent))
                .whenComplete((result, failure) -> server.execute(() -> {
                    if (server.getPlayerList().getPlayer(connectedPlayerId) != player) {
                        // The player's session changed while Rails was working. Rails may well
                        // have committed; say so, because the two sides are now out of step
                        // (finding S-2).
                        QuestActionTelemetry.rejected(player, request, QuestActionTelemetry.Stage.RESPONSE,
                            "player_session_changed_response_dropped",
                            ServerQuestTable.journalSize(connectedPlayerId));
                        return;
                    }
                    if (shouldRetry(request, attempt, result, failure)) {
                        LOGGER.warn("event=quest_action_retry request_uuid={} action={} attempt={} status={}",
                            request.requestUuid(), request.action(), attempt,
                            result == null ? -1 : result.statusCode);
                        dispatch(player, request, intent, attempt + 1);
                        return;
                    }
                    if (failure != null || result == null) {
                        QuestActionTelemetry.rejected(player, request, QuestActionTelemetry.Stage.DISPATCH,
                            "quest_service_unavailable", ServerQuestTable.journalSize(connectedPlayerId));
                        send(player, request.requestId(), 503,
                            "{\"success\":false,\"error\":\"quest_service_unavailable\"}");
                        return;
                    }
                    applyAuthoritativeResult(player, request, intent, result);
                    QuestActionTelemetry.result(player, request, result.statusCode,
                        succeeded(result), grantedItemCount(result));
                    send(player, request.requestId(), result.statusCode, result.body);
                }));
        } catch (RejectedExecutionException rejected) {
            QuestActionTelemetry.rejected(player, request, QuestActionTelemetry.Stage.DISPATCH,
                "quest_queue_full", ServerQuestTable.journalSize(connectedPlayerId));
            send(player, request.requestId(), 503, "{\"success\":false,\"error\":\"quest_queue_full\"}");
        }
    }

    /**
     * Whether this attempt earns a retry. Package-visible so the policy can be tested without a
     * server: only a turn-in, only once, and only when the service was unreachable -- never on a
     * 4xx, which is Rails answering deliberately.
     */
    static boolean shouldRetry(QuestActionC2SPayload request, int attempt, Result result, Throwable failure) {
        if (request == null || request.action() != QuestActionC2SPayload.Action.CHOOSE) return false;
        if (attempt >= MAX_TURN_IN_ATTEMPTS) return false;
        if (request.requestUuid() == null || request.requestUuid().isBlank()) return false;
        if (failure != null || result == null) return true;
        return result.statusCode == 503;
    }

    private static void reject(ServerPlayer player, QuestActionC2SPayload request,
                               QuestActionTelemetry.Stage stage, String reason) {
        QuestActionTelemetry.rejected(player, request, stage, reason,
            ServerQuestTable.journalSize(player == null ? null : player.getUUID()));
        send(player, request.requestId(), 422, INVALID_QUEST_ACTION);
    }

    /**
     * Which shape rule failed. The wire response stays the deliberately vague
     * {@code invalid_quest_action}: the detail belongs in the server log, not in a reply to a
     * client that may be probing.
     */
    private static String shapeRejectionReason(QuestActionC2SPayload request) {
        if (request.requestId() <= 0) return "missing_request_id";
        if (request.action() == null) return "missing_action";
        if (request.questGiverUuid() == null) return "missing_quest_giver_uuid";
        if (!validRequestUuid(request.requestUuid())) return "malformed_request_uuid";
        if (request.action() == QuestActionC2SPayload.Action.INTERACT) return "invalid_interact_shape";
        if (request.questId() <= 0) return "missing_quest_id";
        return "invalid_action_arguments";
    }

    /**
     * Distinguishes the two very different journal misses: the quest genuinely is not in a
     * journal we hold, versus we never fetched one at all (finding Q-03 -- a failed login
     * bootstrap silently disables quests for the whole session). M5 makes the second re-fetch.
     */
    private static String journalRejectionReason(ServerPlayer player) {
        return ServerQuestTable.journalLoaded(player.getUUID())
            ? "quest_not_in_server_journal"
            : "server_journal_not_loaded";
    }

    private static boolean succeeded(Result result) {
        if (result.statusCode < 200 || result.statusCode >= 300) return false;
        try {
            return booleanValue(JsonParser.parseString(result.body).getAsJsonObject(), "success", false);
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static int grantedItemCount(Result result) {
        try {
            JsonObject root = JsonParser.parseString(result.body).getAsJsonObject();
            return root.has("granted_items") && root.get("granted_items").isJsonArray()
                ? root.getAsJsonArray("granted_items").size() : 0;
        } catch (RuntimeException invalid) {
            return 0;
        }
    }

    private static Result callRails(MinecraftServer server, String playerUuid, QuestActionC2SPayload request,
                                    ResolvedIntent intent) {
        try {
            Endpoint endpoint = endpoint(request);
            var requestUri = ServerAuthRegistry.credentials(server).orElseThrow().apiUrls()
                .resolvePath(endpoint, pathParameters(request));
            HttpURLConnection connection = (HttpURLConnection) requestUri.toURL().openConnection();
            BoundedHttp.configure(connection);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");

            JsonObject payload = new JsonObject();
            payload.addProperty("player_uuid", playerUuid);
            // The correlation id travels with the request so the Rails log line for this exact
            // action can be found from the Minecraft one, and the reverse.
            payload.addProperty("request_uuid", request.requestUuid() == null ? "" : request.requestUuid());
            switch (request.action()) {
                case INTERACT -> payload.addProperty("npc_name", intent.argument());
                case TRIGGER -> payload.addProperty("trigger_key", intent.argument());
                case CHOOSE -> payload.addProperty("choice_id", intent.argument());
                default -> { }
            }
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);

            if (!RailsRequestAuthenticator.apply(connection, server, bytes)) {
                throw new IllegalStateException("Server authentication unavailable");
            }

            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }

            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
            if (input == null) return new Result(status, "{\"success\":false,\"error\":\"empty_response\"}");
            String body = BoundedHttp.readUtf8(input, MAX_RESPONSE_BYTES);
            try { JsonParser.parseString(body).getAsJsonObject(); }
            catch (RuntimeException invalid) {
                return new Result(502, "{\"success\":false,\"error\":\"invalid_service_response\"}");
            }
            return new Result(status, body);
        } catch (Exception error) {
            LOGGER.warn("event=quest_rails_call_failed request_uuid={} action={} error={}",
                request.requestUuid(), request.action(), error.toString());
            return new Result(503, "{\"success\":false,\"error\":\"quest_service_unavailable\"}");
        }
    }

    private static void applyAuthoritativeResult(ServerPlayer player, QuestActionC2SPayload request,
                                                 ResolvedIntent intent, Result result) {
        if (result.statusCode < 200 || result.statusCode >= 300) {
            LOGGER.warn("event=quest_journal_not_updated request_uuid={} action={} player_uuid={} "
                    + "quest_id={} reason=rails_rejected rails_status={}",
                request.requestUuid(), request.action(), player.getStringUUID(), request.questId(),
                result.statusCode);
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(result.body).getAsJsonObject();
            if (!booleanValue(root, "success", true)) {
                LOGGER.warn("event=quest_journal_not_updated request_uuid={} action={} player_uuid={} "
                        + "quest_id={} reason=rails_reported_failure rails_error={}",
                    request.requestUuid(), request.action(), player.getStringUUID(), request.questId(),
                    string(root, "error"));
                return;
            }

            List<ClientQuestEntry> accepted = QuestEntryParser.parseRailsAcceptSuccess(root, intent.questGiverName());
            accepted.forEach(entry -> {
                ServerQuestTable.addFromRailsAcceptSuccess(player, entry);
                // M9 item 5. The only place a quest becomes accepted, so the only place an
                // accept-time side effect can run exactly once. It runs after the journal row
                // exists, so anything it does is consistent with what the player can see.
                RowanQuestlineHooks.onQuestAccepted(player, entry, intent.questGiverUuid());
            });

            QuestModels.QuestResponse response = GSON.fromJson(root, QuestModels.QuestResponse.class);
            QuestRewardService.apply(player, response, root, request.requestUuid());

            if (request.action() == QuestActionC2SPayload.Action.CHOOSE && hasClientAction(root, "spawn_escort")) {
                String questStateId = string(root, "quest_state_id");
                if (questStateId.isBlank() && !accepted.isEmpty()) questStateId = accepted.get(0).questStateId();
                if (questStateId.isBlank()) {
                    ClientQuestEntry active = ServerQuestTable.findByQuestId(player, request.questId());
                    if (active != null) questStateId = active.questStateId();
                }
                if (!questStateId.isBlank() && !ZERO_UUID.equals(intent.questGiverUuid())) {
                    QuestPayloadHandler.activateEscort(player, request.questId(), questStateId, intent.questGiverUuid());
                }
            }

            if (request.action() == QuestActionC2SPayload.Action.ABANDON
                || booleanValue(root, "completed", false)) {
                ServerQuestTable.removeAfterRailsCompletionSuccessByQuestId(player, request.questId());
            }
            ClientboundSyncQuestsPayload.send(player, ServerQuestTable.snapshot(player));

            // M9 item 7. Talking to the quest giver is the gesture a player already makes when
            // something has gone wrong, so it is where a lost tool is noticed and, within the
            // Rails-held bound, replaced. Runs after the journal is level with Rails, so the
            // active stage it reads is the real one, and does nothing at all when the player is
            // carrying everything the questline requires.
            if (request.action() == QuestActionC2SPayload.Action.INTERACT) {
                com.seggellion.britannia_mod.quest.equipment.QuestEquipmentReissueService
                        .offerRecovery(player);
            }
        } catch (RuntimeException invalid) {
            LOGGER.warn("event=quest_journal_not_updated request_uuid={} action={} player_uuid={} "
                    + "quest_id={} reason=unreadable_response detail={}",
                request.requestUuid(), request.action(), player.getStringUUID(), request.questId(),
                invalid.toString());
        }
    }

    /** Callers must have validated the shape first; {@code handle} does. */
    private static ResolvedIntent resolve(ServerPlayer player, QuestActionC2SPayload request) {
        if (request.action() != QuestActionC2SPayload.Action.INTERACT) {
            String displayName = "";
            if (!ZERO_UUID.equals(request.questGiverUuid())) {
                Entity entity = player.serverLevel().getEntity(request.questGiverUuid());
                if (entity instanceof QuestGiverEntity questGiver && entity.isAlive()
                    && player.distanceToSqr(entity) <= 64.0D) {
                    displayName = displayName(questGiver.getPersonalName());
                }
            }
            return new ResolvedIntent(request.argument(), displayName, request.questGiverUuid());
        }

        Entity entity = player.serverLevel().getEntity(request.questGiverEntityId());
        if (!(entity instanceof QuestGiverEntity questGiver) || !entity.isAlive()
            || player.distanceToSqr(entity) > 64.0D || !entity.getUUID().equals(request.questGiverUuid())) return null;
        String apiId = questGiver.resolveQuestGiverApiId();
        if (!validArgument(apiId)) return null;
        return new ResolvedIntent(apiId, displayName(questGiver.getPersonalName()), entity.getUUID());
    }

    static boolean isValidShape(QuestActionC2SPayload request) {
        if (request == null || request.requestId() <= 0 || request.action() == null
            || request.questGiverUuid() == null || !validRequestUuid(request.requestUuid())) return false;
        if (request.action() == QuestActionC2SPayload.Action.INTERACT) {
            return request.questId() == 0L && request.argument() != null && request.argument().isEmpty()
                && request.questGiverEntityId() > 0 && !ZERO_UUID.equals(request.questGiverUuid());
        }
        if (request.questId() <= 0 || request.questGiverEntityId() != -1) return false;
        return switch (request.action()) {
            case TRIGGER, CHOOSE -> validArgument(request.argument());
            case START, ABANDON -> request.argument() != null && request.argument().isEmpty();
            case INTERACT -> false;
        };
    }

    private static boolean authorizedForCurrentJournal(ServerPlayer player, QuestActionC2SPayload request) {
        return switch (request.action()) {
            case CHOOSE, TRIGGER, ABANDON -> ServerQuestTable.hasActiveQuestId(player, request.questId());
            default -> true;
        };
    }

    private static Endpoint endpoint(QuestActionC2SPayload request) {
        return switch (request.action()) {
            case START -> Endpoint.QUEST_START;
            case TRIGGER -> Endpoint.QUEST_TRIGGER;
            case CHOOSE -> Endpoint.QUEST_TRANSITION;
            case ABANDON -> Endpoint.QUEST_ABANDON;
            case INTERACT -> Endpoint.QUEST_INTERACT;
        };
    }

    private static Map<String, String> pathParameters(QuestActionC2SPayload request) {
        return request.action() == QuestActionC2SPayload.Action.INTERACT
            ? Map.of() : Map.of("quest_id", Long.toString(request.questId()));
    }

    /**
     * The correlation id must be a real UUID string. It authorizes nothing, but it is logged and
     * forwarded to Rails, so an unbounded or control-laden value would be a log-injection vector.
     */
    static boolean validRequestUuid(String value) {
        if (value == null || value.length() != QuestActionC2SPayload.MAX_REQUEST_UUID_LENGTH) return false;
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static boolean validArgument(String value) {
        return value != null && !value.isBlank() && value.length() <= 128
            && value.chars().noneMatch(Character::isISOControl);
    }

    private static boolean hasClientAction(JsonObject root, String actionName) {
        if (!root.has("client_actions") || !root.get("client_actions").isJsonArray()) return false;
        JsonArray actions = root.getAsJsonArray("client_actions");
        if (actions.size() > 32) return false;
        for (JsonElement element : actions) {
            if (!element.isJsonObject()) continue;
            JsonObject action = element.getAsJsonObject();
            if (actionName.equals(string(action, "action")) || actionName.equals(string(action, "type"))) return true;
        }
        return false;
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) return fallback;
        try { return object.get(key).getAsBoolean(); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        try { return object.get(key).getAsString(); }
        catch (RuntimeException ignored) { return ""; }
    }

    private static String internalApiId(String rawName) {
        if (rawName == null || rawName.isBlank()) return "";
        return rawName.contains(":") ? rawName.split(":", 2)[1].trim() : rawName.trim();
    }

    private static String displayName(String rawName) {
        if (rawName == null || rawName.isBlank()) return "";
        return rawName.contains(":") ? rawName.split(":", 2)[0].trim() : rawName.trim();
    }

    private static void send(ServerPlayer player, long requestId, int status, String body) {
        NetworkHandler.sendToPlayer(player, new QuestActionResultS2CPayload(requestId, status, body));
    }

    private record ResolvedIntent(String argument, String questGiverName, UUID questGiverUuid) {}
    record Result(int statusCode, String body) {}
}
