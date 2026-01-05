package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BottlingPayload(BlockPos pos, String suffix, String labelColor) implements CustomPacketPayload {

    public static final Type<BottlingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "bottling_payload"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BottlingPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, BottlingPayload::pos,
        ByteBufCodecs.STRING_UTF8, BottlingPayload::suffix,
        ByteBufCodecs.STRING_UTF8, BottlingPayload::labelColor,
        BottlingPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}