package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CraftBlacksmithItemC2SPayload(Action action, String craftableId, String targetToken,
                                            String sessionToken) implements CustomPacketPayload {
    public enum Action { CRAFT, REPAIR, SMELT }

    public CraftBlacksmithItemC2SPayload(String craftableId) {
        this(Action.CRAFT, craftableId, "", "");
    }

    public CraftBlacksmithItemC2SPayload(String craftableId, String sessionToken) {
        this(Action.CRAFT, craftableId, "", sessionToken);
    }

    public static CraftBlacksmithItemC2SPayload repair(String token, String sessionToken) {
        return new CraftBlacksmithItemC2SPayload(Action.REPAIR, "", token, sessionToken);
    }

    public static CraftBlacksmithItemC2SPayload smelt(String token, String sessionToken) {
        return new CraftBlacksmithItemC2SPayload(Action.SMELT, "", token, sessionToken);
    }

    public static final ResourceLocation TYPE_ID = 
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "craft_blacksmith_item");
    public static final Type<CraftBlacksmithItemC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, CraftBlacksmithItemC2SPayload> STREAM_CODEC = 
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.action);
                buf.writeUtf(payload.craftableId, 128);
                buf.writeUtf(payload.targetToken, 128);
                buf.writeUtf(payload.sessionToken, 64);
            },
            buf -> new CraftBlacksmithItemC2SPayload(buf.readEnum(Action.class), buf.readUtf(128), buf.readUtf(128),
                    buf.readUtf(64))
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
