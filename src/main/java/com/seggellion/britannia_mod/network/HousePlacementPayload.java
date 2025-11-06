// HousePlacementPayload.java
package com.seggellion.britannia_mod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HousePlacementPayload(BlockPos targetPos,
                                    int      rotationDeg,
                                    String   styleName)            // use the enum’s name
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HousePlacementPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            "britannia_mod", "house_placement"));

    public static final StreamCodec<FriendlyByteBuf, HousePlacementPayload> STREAM_CODEC =
        StreamCodec.of(HousePlacementPayload::encode, HousePlacementPayload::decode);

    /* ---------- codec ---------- */
   private static void encode(FriendlyByteBuf buf, HousePlacementPayload p) {
    buf.writeBlockPos(p.targetPos());
    buf.writeInt(p.rotationDeg());    // ← ■■■ CHANGED ■■■
    buf.writeUtf(p.styleName());
}

/* ===== decode ===== */
private static HousePlacementPayload decode(FriendlyByteBuf buf) {
    return new HousePlacementPayload(
            buf.readBlockPos(),
            buf.readInt(),             // ← ■■■ CHANGED ■■■
            buf.readUtf()
    );
}
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
