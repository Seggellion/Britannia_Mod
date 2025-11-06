package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.client.screen.StoreSignScreen;
import com.seggellion.britannia_mod.network.NetworkHandler;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

public record StoreSignScreenPayload(BlockPos pos, String storeName, String signType, boolean isAdmin)
        implements CustomPacketPayload {

    public static final Type<StoreSignScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "store_sign_screen"));

    public static final StreamCodec<FriendlyByteBuf, StoreSignScreenPayload> STREAM_CODEC = StreamCodec.of(
            StoreSignScreenPayload::encode,
            StoreSignScreenPayload::decode
    );

    public static StoreSignScreenPayload decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String name = buf.readUtf();
        String type = buf.readUtf();
        boolean isAdmin = buf.readBoolean();
        return new StoreSignScreenPayload(pos, name, type, isAdmin);
    }

    public static void encode(FriendlyByteBuf buf, StoreSignScreenPayload payload) {
        buf.writeBlockPos(payload.pos());
        buf.writeUtf(payload.storeName());
        buf.writeUtf(payload.signType());
        buf.writeBoolean(payload.isAdmin());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, BlockPos pos, String storeName, String signType, boolean isAdmin) {
        NetworkHandler.sendToPlayer(player, new StoreSignScreenPayload(pos, storeName, signType, isAdmin));
    }


}
