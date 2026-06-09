package com.seggellion.britannia_mod.commands;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class QuestCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("quest")
                .then(Commands.literal("clearall")
                    .executes(QuestCommand::clearAllQuests))
        );
    }

    private static int clearAllQuests(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // Ensure a physical player is running this (not the server console or a command block)
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        source.sendSystemMessage(Component.literal("§eConsulting the gods to abandon your quests..."));

        // Run the API call asynchronously so we don't freeze the Minecraft server
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(ModConfig.API_BASE_URL + "quests/clear_all");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                // Attach Auth Tokens
                String token = CityAPITokenData.getClientToken();
                if (token != null && !token.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token);
                String secret = CityAPITokenData.getClientShardSecret();
                if (secret != null && !secret.isEmpty()) conn.setRequestProperty("Shard-Secret", secret);

                // Build Payload
                JsonObject payload = new JsonObject();
                payload.addProperty("player_uuid", player.getUUID().toString());

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int status = conn.getResponseCode();
                
                // Route the message back to the main server thread safely
                player.server.execute(() -> {
                    if (status >= 200 && status < 300) {
                        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                            JsonObject response = JsonParser.parseReader(reader).getAsJsonObject();
                            String message = response.has("message") ? response.get("message").getAsString() : "Quests abandoned.";
                            player.sendSystemMessage(Component.literal("§a" + message));
                        } catch (Exception e) {
                            player.sendSystemMessage(Component.literal("§aQuests abandoned successfully."));
                        }
                    } else {
                        player.sendSystemMessage(Component.literal("§cFailed to clear quests. Server responded with HTTP " + status));
                    }
                });

            } catch (Exception e) {
                player.server.execute(() -> player.sendSystemMessage(Component.literal("§cAn error occurred while contacting the Quest API.")));
            }
        });

        return 1;
    }
}