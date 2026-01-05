package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

// 1. Update record signature
public record ClientboundSyncCityTokenPayload(String token, String shardSecret) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "sync_city_token");

    public static final Type<ClientboundSyncCityTokenPayload> TYPE = new Type<>(TYPE_ID);

    // 2. Update Stream Codec to handle two strings
    public static final StreamCodec<FriendlyByteBuf, ClientboundSyncCityTokenPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ClientboundSyncCityTokenPayload::token,
                    ByteBufCodecs.STRING_UTF8, ClientboundSyncCityTokenPayload::shardSecret,
                    ClientboundSyncCityTokenPayload::new
            );

    // 3. Update helper method
    public static void send(ServerPlayer player, String token, String shardSecret) {
        NetworkHandler.sendToPlayer(player, new ClientboundSyncCityTokenPayload(token, shardSecret));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}