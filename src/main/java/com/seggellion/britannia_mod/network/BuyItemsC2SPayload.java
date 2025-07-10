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

    public static final Type<BuyItemsC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "buy_items"));

public static final StreamCodec<FriendlyByteBuf, BuyItemsC2SPayload> STREAM_CODEC = StreamCodec.of(
    // Encode
    (buf, p) -> {
        String json = p.items.toString();
        buf.writeUtf(json, 32_000);  // ✅ only this
        buf.writeVarInt(p.clientTotal());
        buf.writeVarInt(p.architectId());
    },
    // Decode
    buf -> {
        String json = buf.readUtf(32_000);
        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
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
        Entity entity = player.level().getEntity(pkt.architectId());
        if (!(entity instanceof ArchitectEntity npc)) {
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
        CloseScreenS2CPayload.send(player);
        player.sendSystemMessage(Component.literal("Fare thee well, adventurer!"));
    }
}
