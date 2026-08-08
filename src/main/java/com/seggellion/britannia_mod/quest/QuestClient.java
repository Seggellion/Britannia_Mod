package com.seggellion.britannia_mod.quest.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.QuestActionC2SPayload;
import com.seggellion.britannia_mod.network.payload.QuestActionResultS2CPayload;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.ClientQuestTable;
import com.seggellion.britannia_mod.quest.QuestEntryParser;
import com.seggellion.britannia_mod.quest.QuestManager;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class QuestClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();
    private static final Map<Long, PendingRequest> PENDING = new ConcurrentHashMap<>();

    private QuestClient() {}

    public static void startQuest(long questId, Consumer<QuestModels.QuestResponse> callback) {
        send(QuestActionC2SPayload.Action.START, questId, "", -1, zeroUuid(), "", true, callback);
    }

    public static void sendTrigger(long questId, String triggerKey, Consumer<QuestModels.QuestResponse> callback) {
        send(QuestActionC2SPayload.Action.TRIGGER, questId, triggerKey, -1, zeroUuid(), "", true, callback);
    }

    public static void sendTransition(long questId, String choiceId, JsonObject context,
                                      Consumer<QuestModels.QuestResponse> callback) {
        send(QuestActionC2SPayload.Action.CHOOSE, questId, choiceId, -1, questGiverUuidFromContext(context),
            questGiverNameFromContext(context), true, callback);
    }

    public static void abandonQuest(long questId) {
        send(QuestActionC2SPayload.Action.ABANDON, questId, "", -1, zeroUuid(), "", false, ignored -> {});
    }

    public static void interactWithNpc(int entityId, UUID entityUuid, Consumer<QuestModels.QuestResponse> callback) {
        send(QuestActionC2SPayload.Action.INTERACT, 0L, "", entityId, entityUuid, "", true, callback);
    }

    public static void handleProxyResult(QuestActionResultS2CPayload payload) {
        PendingRequest pending = PENDING.remove(payload.requestId());
        if (pending == null) return;
        Minecraft.getInstance().execute(() -> processResponse(payload.statusCode(), payload.responseJson(), pending));
    }

    private static void send(QuestActionC2SPayload.Action action, long questId, String argument,
                             int questGiverEntityId, UUID questGiverUuid, String questGiverName,
                             boolean allowAcceptedQuestSync,
                             Consumer<QuestModels.QuestResponse> callback) {
        if (Minecraft.getInstance().getConnection() == null) {
            callback.accept(failure("Not connected to a server."));
            return;
        }
        long requestId = REQUEST_SEQUENCE.updateAndGet(current -> current == Long.MAX_VALUE ? 1L : current + 1L);
        PENDING.put(requestId, new PendingRequest(callback, questGiverName, allowAcceptedQuestSync));
        NetworkHandler.sendToServer(new QuestActionC2SPayload(
            requestId, action, questId, safe(argument), questGiverEntityId,
            questGiverUuid == null ? zeroUuid() : questGiverUuid));
    }

    private static void processResponse(int status, String rawResponse, PendingRequest pending) {
        QuestModels.QuestResponse response;
        JsonObject rawJson = null;
        try {
            rawJson = JsonParser.parseString(rawResponse).getAsJsonObject();
            response = GSON.fromJson(rawJson, QuestModels.QuestResponse.class);
        } catch (RuntimeException invalid) {
            LOGGER.warn("Quest proxy returned invalid JSON with status {}", status);
            response = failure("The quest service returned an invalid response.");
        }
        if (response == null) response = failure("The quest service returned an empty response.");
        if (status < 200 || status >= 300) response.success = false;

        List<ClientQuestEntry> acceptedQuests = pending.allowAcceptedQuestSync && rawJson != null
            ? QuestEntryParser.parseRailsAcceptSuccess(rawJson, pending.questGiverName)
            : List.of();
        if ((response.questStateId == null || response.questStateId.isBlank()) && !acceptedQuests.isEmpty()) {
            response.questStateId = acceptedQuests.get(0).questStateId();
        }
        if ((response.questGiverName == null || response.questGiverName.isBlank()) && !acceptedQuests.isEmpty()) {
            response.questGiverName = acceptedQuests.get(0).questGiverName();
        }

        if (response.success) {
            QuestManager.getInstance().setCurrentQuestState(response);
            syncQuestJournalFromResponse(response, acceptedQuests);
        }
        pending.callback.accept(response);
    }

    private static void syncQuestJournalFromResponse(QuestModels.QuestResponse response,
                                                     List<ClientQuestEntry> acceptedQuests) {
        if (response == null || !response.success) return;
        if (response.completed) {
            if (response.quest_id > 0) {
                ClientQuestTable.removeAfterRailsCompletionSuccessByQuestId(Long.toString(response.quest_id));
            }
            return;
        }
        acceptedQuests.stream().filter(ClientQuestEntry::hasKey).forEach(ClientQuestTable::addFromRailsAcceptSuccess);
    }

    private static QuestModels.QuestResponse failure(String message) {
        QuestModels.QuestResponse response = new QuestModels.QuestResponse();
        response.success = false;
        response.error = message;
        return response;
    }

    private static String safe(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\p{Cntrl}]", "").trim();
        return cleaned.substring(0, Math.min(cleaned.length(), 128));
    }

    private static String questGiverNameFromContext(JsonObject context) {
        if (context == null || !context.has("quest_giver_name")) return "";
        try { return context.get("quest_giver_name").getAsString(); }
        catch (RuntimeException ignored) { return ""; }
    }

    private static UUID questGiverUuidFromContext(JsonObject context) {
        if (context == null || !context.has("quest_giver_uuid")) return zeroUuid();
        try { return UUID.fromString(context.get("quest_giver_uuid").getAsString()); }
        catch (RuntimeException ignored) { return zeroUuid(); }
    }

    private static UUID zeroUuid() { return new UUID(0L, 0L); }

    private record PendingRequest(Consumer<QuestModels.QuestResponse> callback, String questGiverName,
                                  boolean allowAcceptedQuestSync) {}
}
