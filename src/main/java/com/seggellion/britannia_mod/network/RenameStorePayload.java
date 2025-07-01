package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.block.entity.StoreSignBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLLoader;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.client.Minecraft;

public record RenameStorePayload(BlockPos pos, String newName) implements CustomPacketPayload {

    public static final Type<RenameStorePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "rename_store"));

    public static final StreamCodec<FriendlyByteBuf, RenameStorePayload> STREAM_CODEC = StreamCodec.of(
        RenameStorePayload::encode,
        RenameStorePayload::decode
    );

    public static RenameStorePayload decode(FriendlyByteBuf buf) {
        return new RenameStorePayload(buf.readBlockPos(), buf.readUtf());
    }

    public static void encode(FriendlyByteBuf buf, RenameStorePayload payload) {
        buf.writeBlockPos(payload.pos());
        buf.writeUtf(payload.newName());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

public static void send(BlockPos pos, String newName) {
    if (FMLLoader.getDist().isClient()) {
        Minecraft.getInstance().getConnection().send(
            new ServerboundCustomPayloadPacket(new RenameStorePayload(pos, newName))
        );
    }
}


    public static void handle(RenameStorePayload payload, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level.getBlockEntity(payload.pos()) instanceof StoreSignBlockEntity be) {
            be.setStoreName(payload.newName());
        }
    }
}
