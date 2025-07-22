package com.seggellion.britannia_mod.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;

public record TogglePrivacyPayload(BlockPos pos, boolean makePrivate) implements CustomPacketPayload {

    // britania_mod:toggle_privacy
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "toggle_privacy");

    public static final Type<TogglePrivacyPayload> TYPE = new Type<>(ID);

    /*  👇  note the two generic arguments here  */
    public static final StreamCodec<FriendlyByteBuf, TogglePrivacyPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public TogglePrivacyPayload decode(FriendlyByteBuf buf) {
            return new TogglePrivacyPayload(buf.readBlockPos(), buf.readBoolean());
        }
        @Override public void encode(FriendlyByteBuf buf, TogglePrivacyPayload pkt) {
            buf.writeBlockPos(pkt.pos());
            buf.writeBoolean(pkt.makePrivate());
        }
    };

    @Override public Type<TogglePrivacyPayload> type() { return TYPE; }
}
