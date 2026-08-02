package com.seggellion.britannia_mod.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletionException;

public class VerifyCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(VerifyCommand.class);
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("verify")
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(VerifyCommand::verify))
        );
    }

    private static int verify(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        String code = StringArgumentType.getString(context, "code").trim();
        if (!code.matches("[A-Za-z0-9]{8}")) {
            player.sendSystemMessage(Component.literal("Enter the verification code from the website.").withStyle(ChatFormatting.RED));
            return 0;
        }

        GameProfile profile = player.getGameProfile();
        UUID minecraftUuid = profile.getId();
        String minecraftUsername = profile.getName();
        if (minecraftUuid == null || minecraftUsername == null || minecraftUsername.isBlank()) {
            player.sendSystemMessage(Component.literal("Could not read your authenticated Minecraft profile. Please reconnect and try again.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        MinecraftServer server = player.server;
        UUID playerUuid = player.getUUID();
        player.sendSystemMessage(Component.literal("Checking your verification code...").withStyle(ChatFormatting.YELLOW));

        try {
            ServerHttpExecutor.submit(server, () -> requestVerification(server, code, minecraftUuid, minecraftUsername))
                .whenComplete((result, throwable) -> server.execute(() -> sendResult(
                    server,
                    playerUuid,
                    throwable == null ? result : VerificationResult.apiFailure(unwrap(throwable))
                )));
        } catch (RuntimeException e) {
            LOGGER.warn("Could not start Minecraft account verification request", e);
            player.sendSystemMessage(Component.literal("The account verification service could not be reached. Please try again later.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        return 1;
    }

    private static VerificationResult requestVerification(
        MinecraftServer server, String code, UUID minecraftUuid, String minecraftUsername
    ) {
        HttpURLConnection connection = null;
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("code", code);
            payload.addProperty("minecraft_uuid", minecraftUuid.toString());
            payload.addProperty("minecraft_username", minecraftUsername);

            connection = (HttpURLConnection) ServerAuthRegistry.credentials(server).orElseThrow()
                .apiUrls().resolve(Endpoint.MINECRAFT_VERIFY).toURL().openConnection();
            BoundedHttp.configure(connection);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            if (!RailsRequestAuthenticator.apply(connection, server, bodyBytes)) {
                throw new IllegalStateException("Server authentication unavailable");
            }

            try (OutputStream output = connection.getOutputStream()) {
                output.write(bodyBytes);
            }

            int status = connection.getResponseCode();
            InputStream responseStream = status >= 200 && status < 400
                ? connection.getInputStream()
                : connection.getErrorStream();
            String responseBody = responseStream == null
                ? ""
                : BoundedHttp.readUtf8(responseStream, MAX_RESPONSE_BYTES);
            return VerificationResult.fromResponse(status, responseBody);
        } catch (Exception error) {
            throw new IllegalStateException("Minecraft account verification request failed", error);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void sendResult(MinecraftServer server, UUID playerUuid, VerificationResult result) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player == null) {
            return;
        }

        switch (result.status()) {
            case SUCCESS -> player.sendSystemMessage(Component.literal(result.messageOr("Your Minecraft account has been verified."))
                    .withStyle(ChatFormatting.GREEN));
            case INVALID_CODE -> player.sendSystemMessage(Component.literal(result.messageOr("That verification code is invalid. Check the code on the website and try again."))
                    .withStyle(ChatFormatting.RED));
            case EXPIRED_CODE -> player.sendSystemMessage(Component.literal(result.messageOr("That verification code has expired. Please request a new code on the website."))
                    .withStyle(ChatFormatting.RED));
            case ALREADY_USED -> player.sendSystemMessage(Component.literal(result.messageOr("That verification code has already been used."))
                    .withStyle(ChatFormatting.RED));
            case CONFLICT -> player.sendSystemMessage(Component.literal(result.messageOr("This Minecraft account is already linked to another website account."))
                    .withStyle(ChatFormatting.RED));
            case API_FAILURE -> player.sendSystemMessage(Component.literal(result.messageOr("The account verification service could not be reached. Please try again later."))
                    .withStyle(ChatFormatting.RED));
        }

        if (result.cause() != null) {
            LOGGER.warn("Minecraft account verification failed", result.cause());
        } else if (result.status() == VerificationStatus.API_FAILURE) {
            LOGGER.warn("Minecraft account verification API returned an unexpected response: HTTP {}", result.httpStatus());
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private record VerificationResult(VerificationStatus status, int httpStatus, String playerMessage, Throwable cause) {
        static VerificationResult apiFailure(Throwable cause) {
            return new VerificationResult(VerificationStatus.API_FAILURE, 0, "", cause);
        }

        static VerificationResult fromResponse(int httpStatus, String responseBody) {
            String safeBody = responseBody == null ? "" : responseBody;
            JsonObject json = parseJsonObject(safeBody);
            VerificationStatus parsedStatus = parseStatus(httpStatus, json);
            return new VerificationResult(parsedStatus, httpStatus, extractMessage(json), null);
        }

        String messageOr(String fallback) {
            return playerMessage == null || playerMessage.isBlank() ? fallback : playerMessage;
        }

        private static VerificationStatus parseStatus(int httpStatus, JsonObject json) {
            String responseCode = normalizedResponseCode(json);

            if (responseCode.contains("expired")) {
                return VerificationStatus.EXPIRED_CODE;
            }
            if (responseCode.contains("already") && (responseCode.contains("used") || responseCode.contains("verified"))) {
                return VerificationStatus.ALREADY_USED;
            }
            if (responseCode.contains("used")) {
                return VerificationStatus.ALREADY_USED;
            }
            if (responseCode.contains("invalid") || responseCode.contains("not_found") || responseCode.contains("not found")) {
                return VerificationStatus.INVALID_CODE;
            }
            if (responseCode.contains("conflict")
                    || responseCode.contains("already_linked")
                    || responseCode.contains("linked_to_another")
                    || (responseCode.contains("linked") && responseCode.contains("another"))
                    || (responseCode.contains("uuid") && responseCode.contains("taken"))) {
                return VerificationStatus.CONFLICT;
            }

            if (httpStatus >= 200 && httpStatus < 300) {
                return VerificationStatus.SUCCESS;
            }
            if (httpStatus == 404) {
                return VerificationStatus.INVALID_CODE;
            }
            if (httpStatus == 409) {
                return VerificationStatus.CONFLICT;
            }
            if (httpStatus == 410) {
                return VerificationStatus.EXPIRED_CODE;
            }

            return VerificationStatus.API_FAILURE;
        }

        private static JsonObject parseJsonObject(String body) {
            if (body == null || body.isBlank()) {
                return null;
            }

            try {
                JsonElement element = JsonParser.parseString(body);
                return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        private static String normalizedResponseCode(JsonObject json) {
            if (json == null) {
                return "";
            }

            StringBuilder builder = new StringBuilder();
            appendJsonString(builder, json, "status");
            appendJsonString(builder, json, "code");
            appendJsonString(builder, json, "error");
            appendJsonString(builder, json, "errors");
            appendJsonString(builder, json, "reason");
            appendJsonString(builder, json, "message");
            return builder.toString()
                    .toLowerCase(Locale.ROOT)
                    .replace('-', '_');
        }

        private static String extractMessage(JsonObject json) {
            if (json == null) {
                return "";
            }

            String message = firstJsonString(json, "message");
            if (!message.isBlank()) return message;

            message = firstJsonString(json, "error");
            if (!message.isBlank()) return message;

            message = firstJsonString(json, "reason");
            if (!message.isBlank()) return message;

            message = firstJsonString(json, "errors");
            return message;
        }

        private static String firstJsonString(JsonObject json, String key) {
            if (!json.has(key) || json.get(key).isJsonNull()) {
                return "";
            }

            JsonElement value = json.get(key);
            if (value.isJsonPrimitive()) {
                return value.getAsString();
            }

            if (value.isJsonArray()) {
                for (JsonElement element : value.getAsJsonArray()) {
                    if (element.isJsonPrimitive()) {
                        return element.getAsString();
                    }
                }
            }

            return "";
        }

        private static void appendJsonString(StringBuilder builder, JsonObject json, String key) {
            if (!json.has(key) || json.get(key).isJsonNull()) {
                return;
            }

            JsonElement value = json.get(key);
            if (value.isJsonPrimitive()) {
                builder.append(' ').append(value.getAsString());
            } else if (value.isJsonArray()) {
                for (JsonElement element : value.getAsJsonArray()) {
                    if (element.isJsonPrimitive()) {
                        builder.append(' ').append(element.getAsString());
                    }
                }
            }
        }
    }

    private enum VerificationStatus {
        SUCCESS,
        INVALID_CODE,
        EXPIRED_CODE,
        ALREADY_USED,
        CONFLICT,
        API_FAILURE
    }
}
