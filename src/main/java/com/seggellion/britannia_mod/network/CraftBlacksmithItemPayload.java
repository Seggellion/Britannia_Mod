package com.seggellion.britannia_mod.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;

public record CraftBlacksmithItemPayload(String targetItemSlug, int ingotCost, float minSkill) implements CustomPacketPayload {

    public static final Type<CraftBlacksmithItemPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "craft_bs_item"));

    public static final StreamCodec<FriendlyByteBuf, CraftBlacksmithItemPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.targetItemSlug);
            buf.writeInt(payload.ingotCost);
            buf.writeFloat(payload.minSkill);
        },
        buf -> new CraftBlacksmithItemPayload(
            buf.readUtf(),
            buf.readInt(),
            buf.readFloat()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}