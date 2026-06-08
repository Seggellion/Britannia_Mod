package com.seggellion.britannia_mod.quest.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import com.seggellion.britannia_mod.quest.QuestManager;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.UUID;

public class QuestClient {
    private static final String BASE_URL = ModConfig.API_BASE_URL;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final ResourceLocation FONT_UO_CLASSIC =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");
    private static final Style UO_STYLE = Style.EMPTY.withFont(FONT_UO_CLASSIC);

    /**
     * Starts a new quest via POST /api/quests/:id/start
     */
    public static void startQuest(long questId, Consumer<QuestModels.QuestResponse> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(BASE_URL + "quests/" + questId + "/start");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                
                attachAuthToken(conn);

                JsonObject payload = new JsonObject();
                payload.addProperty("player_uuid", getLocalPlayerUUID());

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                handleResponse(conn, callback);
            } catch (Exception e) {
                LOGGER.error("Failed to connect to Quest API for startQuest", e);
            }
        });
    }

public static void sendTrigger(long questId, String triggerKey, Consumer<QuestModels.QuestResponse> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(BASE_URL + "quests/" + questId + "/trigger_node");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                
                attachAuthToken(conn);

                JsonObject payload = new JsonObject();
                payload.addProperty("player_uuid", getLocalPlayerUUID());
                payload.addProperty("trigger_key", triggerKey);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
                handleResponse(conn, callback);
            } catch (Exception e) {
                LOGGER.error("Failed to send trigger to Quest API", e);
            }
        });
    }

   /**
     * Processes a transition (choice or trigger) via POST /api/quests/:id/choose
     */
    public static void sendTransition(long questId, String choiceId, JsonObject context, Consumer<QuestModels.QuestResponse> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                // Point to the correct Rails endpoint!
                URL url = new URL(BASE_URL + "quests/" + questId + "/choose");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                
                attachAuthToken(conn);

                JsonObject payload = new JsonObject();
                payload.addProperty("player_uuid", getLocalPlayerUUID());
                payload.addProperty("choice_id", choiceId); // Pass the edge ID in the payload
                
                if (context != null) {
                    payload.add("context", context);
                }

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
                handleResponse(conn, callback);
            } catch (Exception e) {
                LOGGER.error("Failed to process quest transition", e);
            }
        });
    }


    /**
     * Tells the server to abort the interaction if the player walked away from the offer.
     */
    public static void abandonQuest(long questId) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(BASE_URL + "quests/" + questId + "/abandon");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                attachAuthToken(conn);

                JsonObject payload = new JsonObject();
                payload.addProperty("player_uuid", getLocalPlayerUUID());

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                // We don't need a callback here. We just fire-and-forget to clean up the DB.
                int status = conn.getResponseCode();
            } catch (Exception e) {
                LOGGER.error("Failed to abandon quest interaction", e);
            }
        });
    }

private static void handleResponse(HttpURLConnection conn, Consumer<QuestModels.QuestResponse> callback) {
        try {
            int status = conn.getResponseCode();
            
            // 1. Read the stream into a raw String first
            Reader reader = new InputStreamReader(
                status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream(), 
                StandardCharsets.UTF_8
            );
            StringBuilder sb = new StringBuilder();
            int cp;
            while ((cp = reader.read()) != -1) {
                sb.append((char) cp);
            }
            String rawResponse = sb.toString();

            QuestModels.QuestResponse response;
            
            // 2. Try to parse it as JSON
            try {
                response = GSON.fromJson(rawResponse, QuestModels.QuestResponse.class);
            } catch (com.google.gson.JsonSyntaxException e) {
                // 3. IF IT FAILS, PRINT THE EXACT RAILS ERROR!
                LOGGER.error("CRITICAL: Rails did not return JSON. Status: {} | Raw Response: {}", status, rawResponse);
                
                // Create a safe fallback so the client UI doesn't freeze
                response = new QuestModels.QuestResponse();
                response.success = false;
                response.error = "Server crash. Check Minecraft Console for Rails error.";
            }
            
            if (status >= 400 && response.error != null) {
                LOGGER.warn("Quest API Error (HTTP {}): {}", status, response.error);
            } else if (status >= 200 && status < 300 && response.success) {
                // Update the state manager centrally
                QuestManager.getInstance().setCurrentQuestState(response);
            }

            // Always execute the callback on the main Minecraft thread to safely update UI
            final QuestModels.QuestResponse finalResponse = response;
            Minecraft.getInstance().execute(() -> {
                // --- NEW: Process Client Actions (like Achievements) ---
                if (finalResponse != null && finalResponse.success && finalResponse.client_actions != null) {
                    for (QuestModels.ClientAction action : finalResponse.client_actions) {
                        if ("achievement".equals(action.type)) {
                            // 1. Display the Vanilla-style pop-up in the top right
                                Minecraft.getInstance().getToasts().addToast(
                                    SystemToast.multiline(
                                        Minecraft.getInstance(),
                                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION, 
                                        uoMessage("Achievement Unlocked!").withStyle(UO_STYLE.withColor(TextColor.fromRgb(0xFFAA00))),
                                        uoMessage(action.name != null ? action.name : "Quest Completed")
                                    )
                                );
                            // 2. Play the satisfying Level Up / Challenge Complete sound
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                            }
                        }

                        // 2. Check for Stat Gains (Un-nested!)
                        else if ("stat_gain".equals(action.type)) {
                            if (Minecraft.getInstance().player != null) {
                                if (action.fame > 0) {
                                    Minecraft.getInstance().player.sendSystemMessage(
                                        uoMessage("You have gained " + action.fame + " Fame.")
                                    );
                                }
                                if (action.karma > 0) {
                                    Minecraft.getInstance().player.sendSystemMessage(
                                        uoMessage("You have gained " + action.karma + " Karma.")
                                    );
                                }
                            }
                        }


                    }
                }

                callback.accept(finalResponse);
            });
            
        } catch (Exception e) {
            LOGGER.error("Error reading Quest API response", e);
        }
    }

    /**
     * Interacts with an NPC to get their current quest node via POST /api/quests/interact
     */
    public static void interactWithNpc(String npcName, Consumer<QuestModels.QuestResponse> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(BASE_URL + "quests/interact");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                
                attachAuthToken(conn);

                JsonObject payload = new JsonObject();
                payload.addProperty("player_uuid", getLocalPlayerUUID());
                payload.addProperty("npc_name", npcName); // e.g., "Gandalf"

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                handleResponse(conn, callback);
            } catch (Exception e) {
                LOGGER.error("Failed to connect to Quest API for NPC interaction", e);
            }
        });
    }

    // Helper methods mirroring your RailsApi code
    private static void attachAuthToken(HttpURLConnection conn) {
        String token = CityAPITokenData.getClientToken();
        if (token != null && !token.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token);
        String secret = CityAPITokenData.getClientShardSecret();
        if (secret != null && !secret.isEmpty()) conn.setRequestProperty("Shard-Secret", secret);
    }

    private static String getLocalPlayerUUID() {
        if (Minecraft.getInstance().player != null) return Minecraft.getInstance().player.getUUID().toString();
        return "unknown";
    }

    private static MutableComponent uoMessage(String text) {
        return Component.literal(text).withStyle(UO_STYLE);
    }
}
