package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenBlacksmithGuiS2CPayload(String ingotId, java.util.List<String> learnedRecipes,
                                          String race, String gender, String sessionToken) implements CustomPacketPayload {

    public OpenBlacksmithGuiS2CPayload(String ingotId) {
        this(ingotId, java.util.List.of(), "human", "female", "");
    }

    public static final ResourceLocation TYPE_ID = 
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "open_blacksmith_gui");
    public static final Type<OpenBlacksmithGuiS2CPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, OpenBlacksmithGuiS2CPayload> STREAM_CODEC = 
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.ingotId, 64);
                buf.writeVarInt(payload.learnedRecipes.size());
                payload.learnedRecipes.forEach(recipe -> buf.writeUtf(recipe, 128));
                buf.writeUtf(payload.race, 32);
                buf.writeUtf(payload.gender, 32);
                buf.writeUtf(payload.sessionToken, 64);
            },
            buf -> {
                String ingot = buf.readUtf(64);
                int size = Math.min(buf.readVarInt(), 4096);
                java.util.List<String> learned = new java.util.ArrayList<>(size);
                for (int i = 0; i < size; i++) learned.add(buf.readUtf(128));
                return new OpenBlacksmithGuiS2CPayload(ingot, java.util.List.copyOf(learned),
                        buf.readUtf(32), buf.readUtf(32), buf.readUtf(64));
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
