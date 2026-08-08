package com.seggellion.britannia_mod.network.payload;

import com.google.gson.*;
import com.seggellion.britannia_mod.entity.ArchitectEntity;
import com.seggellion.britannia_mod.util.SendTransactionToAPI;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import net.neoforged.fml.loading.FMLLoader;

import java.nio.charset.StandardCharsets;
import java.util.List;

public record BuyItemsC2SPayload(JsonArray items, int clientTotal, int architectId) implements CustomPacketPayload {
    public static final int MAX_ITEMS = 64;
    public static final int MAX_QUANTITY = 1_024;

    public static final Type<BuyItemsC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "buy_items"));

public static final StreamCodec<FriendlyByteBuf, BuyItemsC2SPayload> STREAM_CODEC = StreamCodec.of(
    // Encode
    (buf, p) -> {
        String json = p.items.toString();
        if (!isValidIntent(p.items)) throw new IllegalArgumentException("Invalid architect purchase intent");
        buf.writeUtf(json, 32_000);  // ✅ only this
        buf.writeVarInt(p.clientTotal());
        buf.writeVarInt(p.architectId());
    },
    // Decode
    buf -> {
        String json = buf.readUtf(32_000);
        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
        if (!isValidIntent(arr)) throw new IllegalArgumentException("Invalid architect purchase intent");
        int total = buf.readVarInt();
        int architectId = buf.readVarInt();
        return new BuyItemsC2SPayload(arr, total, architectId);
    }
);


    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // Call this client-side when purchasing
    public static void send(JsonArray items, int total, int architectId) {
        if (FMLLoader.getDist().isClient()) {
            Minecraft.getInstance().getConnection().send(
                new ServerboundCustomPayloadPacket(new BuyItemsC2SPayload(items, total, architectId))
            );
        }
    }

    // Server-side handler
    public static void handle(BuyItemsC2SPayload pkt, ServerPlayer player) {
        if (!isValidIntent(pkt.items())) {
            player.sendSystemMessage(Component.literal("Invalid purchase request."));
            return;
        }
        Entity entity = player.level().getEntity(pkt.architectId());
        if (!(entity instanceof ArchitectEntity npc) || !npc.isAlive() || player.distanceToSqr(npc) > 64.0D) {
            player.sendSystemMessage(Component.literal("Architect not found."));
            return;
        }

        List<JsonObject> list = pkt.items().asList().stream()
                                   .map(JsonElement::getAsJsonObject)
                                   .toList();

        SendTransactionToAPI.send(
            player.serverLevel(),
            player.getStringUUID(),
            npc.city(),
            list,
            "purchase",
            "architect",
            npc.getUUID().toString(),
            npc.getPersonalName(),
            player
        );

    }

    static boolean isValidIntent(JsonArray items) {
        if (items == null || items.isEmpty() || items.size() > MAX_ITEMS) return false;
        for (JsonElement element : items) {
            if (!element.isJsonObject()) return false;
            JsonObject item = element.getAsJsonObject();
            if (!boundedString(item, "item_name", 256) || !boundedString(item, "item_id", 128)) return false;
            int quantity;
            try { quantity = item.has("quantity") ? item.get("quantity").getAsInt() : 1; }
            catch (RuntimeException invalid) { return false; }
            if (quantity <= 0 || quantity > MAX_QUANTITY) return false;
        }
        return true;
    }

    private static boolean boundedString(JsonObject item, String key, int maxLength) {
        if (!item.has(key) || item.get(key).isJsonNull()) return false;
        try {
            String value = item.get(key).getAsString();
            return !value.isBlank() && value.length() <= maxLength
                && value.chars().noneMatch(Character::isISOControl);
        } catch (RuntimeException invalid) {
            return false;
        }
    }
}
