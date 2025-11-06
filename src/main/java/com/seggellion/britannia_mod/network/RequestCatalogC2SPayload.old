package com.seggellion.britannia_mod.network.payload;

import com.google.gson.*;
import com.seggellion.britannia_mod.entity.ArchitectEntity;
import com.seggellion.britannia_mod.util.SendTransactionToAPI;

import com.seggellion.britannia_mod.network.ClientboundOpenArchitectScreenPayload;
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

public record RequestCatalogC2SPayload(int architectId) implements CustomPacketPayload {
    public static final Type<RequestCatalogC2SPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "request_catalog"));

    public static final StreamCodec<FriendlyByteBuf, RequestCatalogC2SPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.architectId),
            buf -> new RequestCatalogC2SPayload(buf.readVarInt())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

public static void handle(RequestCatalogC2SPayload payload, ServerPlayer player) {
    Entity entity = player.level().getEntity(payload.architectId());
    if (!(entity instanceof ArchitectEntity architect)) return;

    architect.loadCatalogFromRails(() -> {
        if (!architect.getCatalog().isEmpty()) {
            ClientboundOpenArchitectScreenPayload.send(player, architect);
        } else {
            player.sendSystemMessage(Component.literal("The Architect's catalog is still loading..."));
        }
    });
}


}
