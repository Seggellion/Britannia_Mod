package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SpawnEscortC2SPayload(String entityType, long questId, java.util.UUID npcUuid, String npcName, String npcGender) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID = 
        ResourceLocation.fromNamespaceAndPath("britannia_mod", "spawn_escort");

    public static final Type<SpawnEscortC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, SpawnEscortC2SPayload> STREAM_CODEC = 
        new StreamCodec<>() {
            @Override
            public SpawnEscortC2SPayload decode(FriendlyByteBuf buf) {
                String entityType = ByteBufCodecs.STRING_UTF8.decode(buf);
                long questId = ByteBufCodecs.VAR_LONG.decode(buf);
                java.util.UUID npcUuid = buf.readUUID(); // Read the UUID
                String npcName = ByteBufCodecs.STRING_UTF8.decode(buf);
                String npcGender = ByteBufCodecs.STRING_UTF8.decode(buf);

                return new SpawnEscortC2SPayload(entityType, questId, npcUuid, npcName, npcGender);
            }

            @Override
            public void encode(FriendlyByteBuf buf, SpawnEscortC2SPayload payload) {
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.entityType);
                ByteBufCodecs.VAR_LONG.encode(buf, payload.questId);
                buf.writeUUID(payload.npcUuid); // Write the UUID
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.npcName());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.npcGender());
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}