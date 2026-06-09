package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public record EscortArrivedS2CPayload(
        long questId, 
        String triggerKey, 
        String npcName, 
        String npcGender, 
        UUID npcUuid
) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath("britannia_mod", "escort_arrived");
    public static final Type<EscortArrivedS2CPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, EscortArrivedS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EscortArrivedS2CPayload decode(FriendlyByteBuf buf) {
            return new EscortArrivedS2CPayload(
                buf.readLong(),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readUUID()
            );
        }

        @Override
        public void encode(FriendlyByteBuf buf, EscortArrivedS2CPayload payload) {
            buf.writeLong(payload.questId);
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.triggerKey);
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.npcName);
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.npcGender);
            buf.writeUUID(payload.npcUuid);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void send(ServerPlayer player, long questId, String triggerKey, String npcName, String npcGender, UUID npcUuid) {
        player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(
            new EscortArrivedS2CPayload(questId, triggerKey, npcName, npcGender, npcUuid)
        ));
    }
}