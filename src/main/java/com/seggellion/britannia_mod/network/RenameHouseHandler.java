package com.seggellion.britannia_mod.network;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
        try {
            String urlString = ModConfig.API_BASE_URL + "houses/rename" ;

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            CityAPITokenData data = CityAPITokenData.getOrCreate(level);
            String token = data.getApiToken();
            if (!token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            conn.setDoOutput(true);

            JsonObject body = new JsonObject();
            body.addProperty("house_name", newName);
            body.addProperty("house_uuid", houseUuid.toString());

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_NO_CONTENT) {
                player.sendSystemMessage(Component.literal("House name updated!"));
            } else {
                player.sendSystemMessage(Component.literal("Failed to rename house. Response code: " + code));
                LOGGER.warn("Rename failed: {}", code);
            }

        } catch (Exception e) {
            LOGGER.error("API rename error", e);
            player.sendSystemMessage(Component.literal("Error renaming house."));
        }
    }
}
