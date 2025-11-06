package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends the City API token from the server to the client after login or bootstrap.
 */
public record ClientboundSyncCityTokenPayload(String token) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "sync_city_token");

    public static final Type<ClientboundSyncCityTokenPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, ClientboundSyncCityTokenPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> ByteBufCodecs.STRING_UTF8.encode(buf, payload.token),
                    buf -> new ClientboundSyncCityTokenPayload(ByteBufCodecs.STRING_UTF8.decode(buf))
            );

    public static void send(ServerPlayer player, String token) {
        NetworkHandler.sendToPlayer(player, new ClientboundSyncCityTokenPayload(token));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
