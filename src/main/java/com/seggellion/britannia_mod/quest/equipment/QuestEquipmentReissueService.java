package com.seggellion.britannia_mod.quest.equipment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.RowanQuestlineHooks;
import com.seggellion.britannia_mod.quest.ServerQuestTable;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDelivery;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryParser;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryService;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Asks Rails for one bounded replacement of mandatory tutorial equipment, and applies the answer
 * (Rowan farming questline M9 item 7).
 *
 * <p>Runs when a player talks to Rowan while on the questline. That is deliberate: the questline's
 * dialogue and its {@code quest_contract/v1} fixtures were frozen at M0, so adding a new dialogue
 * choice would have rewritten a pinned fixture. Talking to the quest giver is the gesture a player
 * already makes when something has gone wrong, and it costs no new UI.
 *
 * <p>Everything durable is Rails': the count, the bound, and the row that says a replacement was
 * granted. The game contributes the one fact Rails cannot have -- what the player is carrying --
 * and says plainly that stored items are invisible to both of us. The replacement itself arrives
 * as an ordinary {@code reward_delivery} and is applied through
 * {@link QuestRewardDeliveryService}, so there is exactly one path by which a quest item reaches a
 * player's pack and exactly one ledger that records it.
 */
public final class QuestEquipmentReissueService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    /** Test seam, so the decision logic can be exercised without a Rails. */
    public interface Transport {
        Answer request(ServerPlayer player, String questKey, String itemId, boolean carried,
                       UUID requestUuid);
    }

    /**
     * What Rails said. {@code delivery} is present only when {@code granted} is true and the
     * answer carried a well-formed delivery.
     */
    public record Answer(boolean granted, String error, QuestRewardDelivery delivery) {
        public static Answer unavailable(String code) {
            return new Answer(false, code == null ? "unavailable" : code, null);
        }
    }

    private static Transport transport = QuestEquipmentReissueService::callRails;

    private QuestEquipmentReissueService() {
    }

    /** Test seam. */
    public static void installTransport(Transport replacement) {
        transport = Objects.requireNonNull(replacement, "Reissue transport is required");
    }

    /** Test seam. */
    public static void resetTransport() {
        transport = QuestEquipmentReissueService::callRails;
    }

    /**
     * Offers to replace the first piece of questline equipment this player is short of.
     *
     * <p>One item per interaction on purpose: a player who lost their whole pack gets their tools
     * back one conversation at a time, which keeps the bound meaningful and keeps the messages
     * readable. Returns what happened, so a caller (and a test) can see the decision without
     * reading chat.
     */
    public static QuestEquipmentReissuePolicy.Outcome offerRecovery(ServerPlayer player) {
        if (player == null) {
            return QuestEquipmentReissuePolicy.Outcome.NOTHING_MISSING;
        }
        String questKey = activeQuestlineKey(player);
        if (questKey == null) {
            return QuestEquipmentReissuePolicy.Outcome.NOTHING_MISSING;
        }
        List<String> missing = QuestEquipmentReissuePolicy.missingEquipment(player.getInventory());
        if (missing.isEmpty()) {
            return QuestEquipmentReissuePolicy.Outcome.NOTHING_MISSING;
        }
        return request(player, questKey, missing.get(0));
    }

    /**
     * Asks for one named item. The carried check happens here, before Rails is troubled, so an
     * allowance is never spent on a player who simply had the tool in a different slot.
     */
    public static QuestEquipmentReissuePolicy.Outcome request(ServerPlayer player, String questKey,
                                                             String itemId) {
        if (!QuestEquipmentReissuePolicy.reissuable(itemId)) {
            return announce(player, QuestEquipmentReissuePolicy.Outcome.UNAVAILABLE, itemId);
        }
        boolean carried = !QuestEquipmentReissuePolicy.isMissing(player.getInventory(), itemId);
        if (carried) {
            return announce(player, QuestEquipmentReissuePolicy.Outcome.ALREADY_CARRIED, itemId);
        }

        Answer answer = transport.request(player, questKey, itemId, false, UUID.randomUUID());
        QuestEquipmentReissuePolicy.Outcome outcome =
                QuestEquipmentReissuePolicy.outcomeFor(answer.granted(), answer.error());
        if (outcome == QuestEquipmentReissuePolicy.Outcome.GRANTED && answer.delivery() != null) {
            QuestRewardDeliveryService.apply(player, answer.delivery(),
                    QuestRewardDeliveryService.Source.TRANSITION);
        }
        LOGGER.info("event=quest_equipment_reissue_requested player_uuid={} quest_key={} item_id={} outcome={}",
                player.getStringUUID(), questKey, itemId, outcome);
        return announce(player, outcome, itemId);
    }

    /** Which stage of Rowan's questline this player is on, or null if none. */
    public static String activeQuestlineKey(ServerPlayer player) {
        for (ClientQuestEntry entry : ServerQuestTable.snapshot(player)) {
            String key = entry.questKey();
            if (key != null && key.startsWith(RowanQuestlineHooks.QUESTLINE_KEY_PREFIX)) {
                return key;
            }
        }
        return null;
    }

    private static QuestEquipmentReissuePolicy.Outcome announce(
            ServerPlayer player, QuestEquipmentReissuePolicy.Outcome outcome, String itemId) {
        String key = QuestEquipmentReissuePolicy.messageKeyFor(outcome);
        if (key == null || player == null) {
            return outcome;
        }
        Component name = Component.translatable(itemTranslationKey(itemId));
        player.displayClientMessage(
                Component.translatable(key, name).withStyle(ChatFormatting.YELLOW), false);
        // Said with every answer except the grant, because every other answer is one a player
        // might otherwise try to argue with by pointing at a chest.
        if (outcome != QuestEquipmentReissuePolicy.Outcome.GRANTED) {
            player.displayClientMessage(
                    Component.translatable(QuestScreenText.EQUIPMENT_STORAGE_CAVEAT)
                            .withStyle(ChatFormatting.GRAY), false);
        }
        return outcome;
    }

    /** {@code britannia_mod:farming_hoe} -> {@code item.britannia_mod.farming_hoe}. */
    public static String itemTranslationKey(String itemId) {
        int colon = itemId == null ? -1 : itemId.indexOf(':');
        if (colon <= 0) {
            return QuestScreenText.ITEM_UNKNOWN;
        }
        return "item." + itemId.substring(0, colon) + "." + itemId.substring(colon + 1);
    }

    /** The real transport: one signed POST, on the HTTP pool, answered synchronously to the caller. */
    private static Answer callRails(ServerPlayer player, String questKey, String itemId,
                                    boolean carried, UUID requestUuid) {
        try {
            return ServerHttpExecutor.submit(player.server,
                    () -> post(player, questKey, itemId, carried, requestUuid)).join();
        } catch (RuntimeException failure) {
            LOGGER.warn("event=quest_equipment_reissue_transport_failed player_uuid={} error={}",
                    player.getStringUUID(), failure.toString());
            return Answer.unavailable("transport_error");
        }
    }

    private static Answer post(ServerPlayer player, String questKey, String itemId,
                               boolean carried, UUID requestUuid) {
        try {
            var credentials = ServerAuthRegistry.credentials(player.server).orElse(null);
            if (credentials == null) {
                return Answer.unavailable("credentials_unavailable");
            }
            HttpURLConnection connection = (HttpURLConnection)
                    credentials.apiUrls().resolve(Endpoint.QUEST_EQUIPMENT_REISSUES).toURL().openConnection();
            BoundedHttp.configure(connection);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");

            JsonObject body = new JsonObject();
            body.addProperty("player_uuid", player.getStringUUID());
            body.addProperty("quest_key", questKey);
            body.addProperty("item_id", itemId);
            body.addProperty("carried", carried);
            body.addProperty("request_uuid", requestUuid.toString());
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);

            if (!RailsRequestAuthenticator.apply(connection, player.server, bytes)) {
                return Answer.unavailable("credentials_unavailable");
            }
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            if (input == null) {
                return Answer.unavailable("empty_response");
            }
            String payload = BoundedHttp.readUtf8(input, MAX_RESPONSE_BYTES);
            return parse(status, payload);
        } catch (Exception error) {
            LOGGER.warn("event=quest_equipment_reissue_failed player_uuid={} error={}",
                    player.getStringUUID(), error.toString());
            return Answer.unavailable("transport_error");
        }
    }

    /** Public so the parsing is testable without a socket. */
    public static Answer parse(int status, String payload) {
        JsonObject root;
        try {
            root = JsonParser.parseString(payload).getAsJsonObject();
        } catch (RuntimeException invalid) {
            return Answer.unavailable("invalid_service_response");
        }
        if (status < 200 || status >= 300) {
            return Answer.unavailable(root.has("error") ? root.get("error").getAsString() : "rails_rejected");
        }
        boolean granted = root.has("granted") && root.get("granted").getAsBoolean();
        if (!granted) {
            return Answer.unavailable(root.has("error") ? root.get("error").getAsString() : "refused");
        }
        QuestRewardDeliveryParser.TransitionResult parsed =
                QuestRewardDeliveryParser.parseTransition(root);
        if (parsed instanceof QuestRewardDeliveryParser.Present present) {
            return new Answer(true, null, present.delivery());
        }
        // Granted with nothing to apply is not a grant we can honour: refuse rather than tell the
        // player they were given something that never arrives.
        return Answer.unavailable("missing_delivery");
    }
}
