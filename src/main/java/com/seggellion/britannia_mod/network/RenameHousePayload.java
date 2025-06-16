package com.seggellion.britannia_mod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RenameHousePayload(BlockPos pos,UUID houseUuid, String newName) implements CustomPacketPayload {

    public static final Type<RenameHousePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "rename_house"));

    public static final StreamCodec<FriendlyByteBuf, RenameHousePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBlockPos(payload.pos());
            buf.writeUUID(payload.houseUuid);
            buf.writeUtf(payload.newName);
        },
        buf -> new RenameHousePayload(buf.readBlockPos(), buf.readUUID(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
