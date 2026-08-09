package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ServiceNpcSpawnResyncC2SPayload(
        int containerId,
        BlockPos pos,
        UUID spawnPointId,
        long expectedConfigurationRevision
) implements CustomPacketPayload {
    public static final Type<ServiceNpcSpawnResyncC2SPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "service_npc_spawn_resync")
    );
    public static final StreamCodec<FriendlyByteBuf, ServiceNpcSpawnResyncC2SPayload> STREAM_CODEC =
            StreamCodec.of(ServiceNpcSpawnResyncC2SPayload::encode, ServiceNpcSpawnResyncC2SPayload::decode);

    private static void encode(FriendlyByteBuf buffer, ServiceNpcSpawnResyncC2SPayload payload) {
        buffer.writeVarInt(payload.containerId);
        buffer.writeBlockPos(payload.pos);
        buffer.writeUUID(payload.spawnPointId);
        buffer.writeLong(payload.expectedConfigurationRevision);
    }

    private static ServiceNpcSpawnResyncC2SPayload decode(FriendlyByteBuf buffer) {
        ServiceNpcSpawnPayloadCodec.requireReadableLimit(buffer, ServiceNpcSpawnPayloadCodec.MAX_C2S_BYTES);
        return new ServiceNpcSpawnResyncC2SPayload(
                buffer.readVarInt(),
                buffer.readBlockPos(),
                buffer.readUUID(),
                buffer.readLong()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
