package com.seggellion.britannia_mod.network;

import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.network.RenameHousePayload;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import com.mojang.logging.LogUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class RenameHouseHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void handle(RenameHousePayload payload, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
             
            BlockPos pos = payload.pos(); 
            BlockPos lotPos = pos.below();
            BlockEntity entity = level.getBlockEntity(lotPos);
            LOGGER.info("🔄 RenameHouseHandler triggered by player: {}", player.getName().getString());
    LOGGER.info("📦 Payload contents → pos: {}, uuid: {}, new name: {}", pos, payload.houseUuid(), payload.newName());

if (!(entity instanceof HouseLotBlockEntity)) {
            LOGGER.warn("❌ Block entity is not a HouseLotBlockEntity. Found: {}", entity.getClass().getSimpleName());
            player.sendSystemMessage(Component.literal("House not found"));
            return;
        }

        HouseLotBlockEntity house = (HouseLotBlockEntity) entity; // ✅ cast once here


        LOGGER.info("🏠 HouseLotBlockEntity found. Owner = {}", house.getOwner());

        if (!house.getOwner().equals(player.getName().getString())) {
            LOGGER.warn("⛔ Ownership mismatch! Player = {}, Owner = {}", player.getName().getString(), house.getOwner());
            player.sendSystemMessage(Component.literal("You are not the owner of this house."));
            return;
        }
      
        house.setHouseName(payload.newName());
        house.setChanged();
        level.sendBlockUpdated(payload.pos(), house.getBlockState(), house.getBlockState(), 3);


        sendRenameRequestToAPI(level, payload.houseUuid(), payload.newName(), player);
        HouseManagementScreenPayload.send(player, pos);

    }

    private static void sendRenameRequestToAPI(ServerLevel level, UUID houseUuid, String newName, ServerPlayer player) {
        UUID playerId = player.getUUID();
        ServerHttpExecutor.submit(level.getServer(), () -> sendRenameRequest(level, houseUuid, newName))
            .whenComplete((code, error) -> level.getServer().execute(() -> {
                ServerPlayer current = level.getServer().getPlayerList().getPlayer(playerId);
                if (current == null) return;
                if (error != null) {
                    current.sendSystemMessage(Component.literal("Error renaming house."));
                } else if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_NO_CONTENT) {
                    current.sendSystemMessage(Component.literal("House name updated!"));
                } else {
                    current.sendSystemMessage(Component.literal("Failed to rename house. Response code: " + code));
                    LOGGER.warn("Rename failed: {}", code);
                }
            }));
    }

    private static int sendRenameRequest(ServerLevel level, UUID houseUuid, String newName) {
        HttpURLConnection conn = null;
        try {
            var requestUri = ServerAuthRegistry.credentials(level.getServer()).orElseThrow().apiUrls()
                    .resolve(Endpoint.HOUSE_RENAME);
            conn = (HttpURLConnection) requestUri.toURL().openConnection();
            BoundedHttp.configure(conn);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            JsonObject body = new JsonObject();
            body.addProperty("house_name", newName);
            body.addProperty("house_uuid", houseUuid.toString());
            byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);

            if (!RailsRequestAuthenticator.apply(conn, level.getServer(), bodyBytes)) throw new IllegalStateException("Server authentication unavailable");

            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            return conn.getResponseCode();

        } catch (Exception e) {
            throw new IllegalStateException("House rename API request failed", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
