package com.seggellion.britannia_mod.network;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;
import com.seggellion.britannia_mod.structure.HouseStyle;
import com.seggellion.britannia_mod.registry.ItemRegistry;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fi.iki.elonen.NanoHTTPD;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class DeedHttpServer extends NanoHTTPD {
    private static final Logger LOGGER = LoggerFactory.getLogger("DeedHttpServer");

    private final MinecraftServer server;

    public DeedHttpServer(int port, MinecraftServer server) {
        super(port);
        this.server = server;
    }
    

@Override
public Response serve(IHTTPSession session) {
    LOGGER.info("Incoming request: {} {}", session.getMethod(), session.getUri());

    if (session.getMethod() == Method.POST && "/api/give_deed".equals(session.getUri())) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String body = files.get("postData");

            if (body == null) {
                LOGGER.error("POST data is missing from request");
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\":\"Missing POST data\"}");
            }

            LOGGER.info("Request body: {}", body);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            String item = json.get("item").getAsString();
            String ownerUuidRaw = json.get("owner").getAsString();
            boolean blessed = json.get("blessed").getAsBoolean();

            LOGGER.info("Parsed deed request: item={}, owner={}, blessed={}", item, ownerUuidRaw, blessed);

            UUID playerUuid = parseDashedUuid(ownerUuidRaw); // Minecraft player UUID (owner)
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);

            if (player == null) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"error\":\"Player not online\"}");
            }

            // Determine style
            HouseStyle style;
            if ("castle_deed".equalsIgnoreCase(item)) {
                style = HouseStyle.CASTLE;
            } else {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\":\"Invalid deed type\"}");
            }

            // ✅ Create the item with a new deed UUID and player UUID
            ItemStack deed = new ItemStack(ItemRegistry.deedFor(style));

            UUID deedUuid = UUID.randomUUID(); // ✅ NEW: Unique per deed

            CompoundTag tag = new CompoundTag();
            tag.putBoolean("blessed", blessed);
            tag.putString("owner", playerUuid.toString());  // stored for ownership enforcement
            tag.putString("deed_id", deedUuid.toString());  // NEW: unique ID per deed

            deed.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            deed.set(DataComponents.CUSTOM_NAME, Component.literal("Castle Deed (Blessed)"));

            player.getInventory().add(deed);

            JsonObject successJson = new JsonObject();
            successJson.addProperty("status", "item_granted");
            successJson.addProperty("uuid", deedUuid.toString()); // ✅ This is now the deed's UUID

            return newFixedLengthResponse(Response.Status.OK, "application/json", successJson.toString());

        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Error handling give_deed request", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\":\"Server error\"}");
        }
    }

    LOGGER.warn("Unhandled request: {}", session.getUri());
    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
}



private static UUID parseDashedUuid(String raw) {
    if (raw.length() != 32) {
        throw new IllegalArgumentException("Invalid raw UUID string: " + raw);
    }
    String dashed = raw.replaceFirst(
        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
        "$1-$2-$3-$4-$5"
    );
    return UUID.fromString(dashed);
}


}
