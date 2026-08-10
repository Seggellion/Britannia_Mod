package com.seggellion.britannia_mod.service.guild;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * The production {@code POST /api/guild_training} caller.
 *
 * <p>Runs entirely on {@link ServerHttpExecutor}'s bounded pool — never the tick thread — matching
 * {@code BankingOpenClient}. Marshalling the answer back to the main thread is the caller's job,
 * not this class's.
 */
public final class GuildTrainingClient implements GuildTrainingSkillSetClientPort {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    @Override
    public CompletableFuture<GuildTrainingWriteResult> submit(
            MinecraftServer server, GuildTrainingRequest request
    ) {
        return ServerHttpExecutor.submit(server, () -> call(server, request));
    }

    private static GuildTrainingWriteResult call(MinecraftServer server, GuildTrainingRequest request) {
        try {
            var credentials = ServerAuthRegistry.credentials(server).orElse(null);
            if (credentials == null) {
                return new GuildTrainingWriteResult.TransportFailure("no_shard_credentials");
            }

            HttpURLConnection connection =
                    (HttpURLConnection) credentials.apiUrls().resolve(Endpoint.GUILD_TRAINING).toURL().openConnection();
            BoundedHttp.configure(connection);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);

            byte[] body = requestBody(request).getBytes(StandardCharsets.UTF_8);
            if (!RailsRequestAuthenticator.apply(connection, server, body)) {
                return new GuildTrainingWriteResult.TransportFailure("authentication_unavailable");
            }
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }

            int status = connection.getResponseCode();
            // A refusal carries a JSON body on the error stream; a genuine transport fault does
            // not. Reading the right stream is what keeps "Rails said no" distinguishable from
            // "Rails never answered", which is the distinction the whole ordering rests on.
            String payload = BoundedHttp.readUtf8(
                    status >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                    MAX_RESPONSE_BYTES);
            return parse(status, payload);
        } catch (Exception failure) {
            LOGGER.warn("guild_training request failed", failure);
            return new GuildTrainingWriteResult.TransportFailure("request_error");
        }
    }

    static GuildTrainingWriteResult parse(int status, String payload) {
        JsonObject json;
        try {
            json = JsonParser.parseString(payload == null ? "" : payload).getAsJsonObject();
        } catch (RuntimeException malformed) {
            return new GuildTrainingWriteResult.TransportFailure("malformed_response");
        }

        String outcome = json.has("outcome") && json.get("outcome").isJsonPrimitive()
                ? json.get("outcome").getAsString() : "";

        return switch (outcome) {
            case "APPLIED" -> new GuildTrainingWriteResult.Applied(
                    intOrZero(json, "granted_tenths"),
                    intOrZero(json, "gold_charged"),
                    json.has("skill_value") ? json.get("skill_value").getAsDouble() : 0.0D
            );
            // A replay: Rails already committed this exact purchase under this idempotency key, but
            // the response cannot say how much was granted, and this process cannot know whether
            // the original attempt got as far as taking the coins. Reported as Applied with a zero
            // grant, which GuildTrainingCharge refuses to charge on. The player may get that
            // training free; the alternative is charging twice for one purchase.
            case "ALREADY_APPLIED" -> new GuildTrainingWriteResult.Applied(0, 0,
                    json.has("skill_value") ? json.get("skill_value").getAsDouble() : 0.0D);
            case "REJECTED" -> new GuildTrainingWriteResult.Rejected(
                    json.has("reason") ? json.get("reason").getAsString() : "rejected");
            // RATE_LIMITED, SERVICE_UNAVAILABLE, and anything unrecognised. Never charge on a
            // status this code does not understand.
            default -> new GuildTrainingWriteResult.TransportFailure(
                    outcome.isEmpty() ? "http_" + status : outcome.toLowerCase(java.util.Locale.ROOT));
        };
    }

    private static int intOrZero(JsonObject json, String member) {
        return json.has(member) && json.get(member).isJsonPrimitive() ? json.get(member).getAsInt() : 0;
    }

    private static String requestBody(GuildTrainingRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("world_npc_public_id", request.worldNpcPublicId().toString());
        body.addProperty("player_uuid", request.playerUuid().toString());
        body.addProperty("player_name", request.playerName());
        body.addProperty("skill_slug", request.skillSlug());
        body.addProperty("purchased_tenths", request.purchasedTenths());
        body.addProperty("gold_paid", request.goldPaid());
        body.addProperty("idempotency_key", request.idempotencyKey().toString());
        return body.toString();
    }
}
