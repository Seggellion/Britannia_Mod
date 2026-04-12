package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenBlacksmithGuiS2CPayload(String ingotId) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID = 
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "open_blacksmith_gui");
    public static final Type<OpenBlacksmithGuiS2CPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, OpenBlacksmithGuiS2CPayload> STREAM_CODEC = 
        StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.ingotId),
            buf -> new OpenBlacksmithGuiS2CPayload(buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}