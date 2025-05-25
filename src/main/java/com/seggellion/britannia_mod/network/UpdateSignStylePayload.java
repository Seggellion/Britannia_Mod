package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.structure.HouseSignBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateSignStylePayload(BlockPos pos, HouseSignBlock.SignType signType, HouseSignBlock.HolderType holderType)
        implements CustomPacketPayload {

    public static final Type<UpdateSignStylePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "update_sign_style"));

    public static final StreamCodec<FriendlyByteBuf, UpdateSignStylePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBlockPos(payload.pos());
            buf.writeEnum(payload.signType());
            buf.writeEnum(payload.holderType());
        },
        buf -> new UpdateSignStylePayload(
            buf.readBlockPos(),
            buf.readEnum(HouseSignBlock.SignType.class),
            buf.readEnum(HouseSignBlock.HolderType.class)
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
