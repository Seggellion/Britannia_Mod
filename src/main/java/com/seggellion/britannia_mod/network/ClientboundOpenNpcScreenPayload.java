package com.seggellion.britannia_mod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

// Your mod classes
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.entity.CitizenEntity;
import com.seggellion.britannia_mod.npc.NpcType;

public record ClientboundOpenNpcScreenPayload(
        NpcType npcType,
        String role,
        String city,
        int entityId
) implements CustomPacketPayload {

    public static final Type<ClientboundOpenNpcScreenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "open_npc_screen"));

    public static final StreamCodec<FriendlyByteBuf, ClientboundOpenNpcScreenPayload>
        STREAM_CODEC = StreamCodec.of(
            ClientboundOpenNpcScreenPayload::encode,
            ClientboundOpenNpcScreenPayload::decode);

    public static ClientboundOpenNpcScreenPayload decode(FriendlyByteBuf buf) {
        NpcType npcType = buf.readEnum(NpcType.class);
        String role = buf.readUtf();
        String city = buf.readUtf();
        int id = buf.readInt();
        return new ClientboundOpenNpcScreenPayload(npcType, role, city, id);
    }

    public static void encode(FriendlyByteBuf buf, ClientboundOpenNpcScreenPayload payload) {
        buf.writeEnum(payload.npcType()); // now unambiguous
        buf.writeUtf(payload.role());
        buf.writeUtf(payload.city());
        buf.writeInt(payload.entityId());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, NpcType type, String role, String city, int entityId) {
        NetworkHandler.sendToPlayer(player,
            new ClientboundOpenNpcScreenPayload(type, role, city, entityId));
    }
}
