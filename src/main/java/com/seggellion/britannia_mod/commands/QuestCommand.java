package com.seggellion.britannia_mod.commands;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class QuestCommand {
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("quest")
                .then(Commands.literal("clearall")
                    .executes(QuestCommand::clearAllQuests))
        );
    }

    private static int clearAllQuests(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        source.sendSystemMessage(Component.literal("\u00a7eConsulting the gods to abandon your quests..."));
        UUID playerId = player.getUUID();
        ServerHttpExecutor.submit(player.server, () -> requestClearAll(player, playerId))
            .whenComplete((result, error) -> player.server.execute(() -> {
                ServerPlayer current = player.server.getPlayerList().getPlayer(playerId);
                if (current == null) return;
                if (error != null) {
                    current.sendSystemMessage(Component.literal("\u00a7cAn error occurred while contacting the Quest API."));
                } else if (result.success()) {
                    current.sendSystemMessage(Component.literal("\u00a7a" + result.message()));
                } else {
                    current.sendSystemMessage(Component.literal("\u00a7cFailed to clear quests. Server responded with HTTP " + result.status()));
                }
            }));
        return 1;
    }

    private static ClearAllResult requestClearAll(ServerPlayer player, UUID playerId) {
        HttpURLConnection connection = null;
        try {
            var credentials = ServerAuthRegistry.credentials(player.server).orElseThrow();
            connection = (HttpURLConnection) credentials.apiUrls().resolve(Endpoint.QUEST_CLEAR_ALL)
                .toURL().openConnection();
            BoundedHttp.configure(connection);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            JsonObject payload = new JsonObject();
            payload.addProperty("player_uuid", playerId.toString());
            payload.addProperty("shard", credentials.shardName());
            byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

            if (!RailsRequestAuthenticator.apply(connection, player.server, bodyBytes)) {
                throw new IllegalStateException("Server authentication unavailable");
            }

            try (OutputStream output = connection.getOutputStream()) {
                output.write(bodyBytes);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return new ClearAllResult(false, status, "");
            String body = BoundedHttp.readUtf8(connection.getInputStream(), MAX_RESPONSE_BYTES);
            JsonObject response = JsonParser.parseString(body).getAsJsonObject();
            String message = response.has("message") ? response.get("message").getAsString() : "Quests abandoned.";
            if (message.length() > 256) message = message.substring(0, 256);
            return new ClearAllResult(true, status, message);
        } catch (Exception exception) {
            throw new IllegalStateException("Quest API request failed", exception);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private record ClearAllResult(boolean success, int status, String message) {}
}
