package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CraftBlacksmithItemC2SPayload(String craftableId) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID = 
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "craft_blacksmith_item");
    public static final Type<CraftBlacksmithItemC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, CraftBlacksmithItemC2SPayload> STREAM_CODEC = 
        StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.craftableId),
            buf -> new CraftBlacksmithItemC2SPayload(buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}