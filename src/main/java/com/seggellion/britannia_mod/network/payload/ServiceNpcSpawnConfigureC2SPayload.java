package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ServiceNpcSpawnConfigureC2SPayload(
        int containerId,
        BlockPos pos,
        UUID spawnPointId,
        long expectedConfigurationRevision,
        UUID cityPublicId,
        String serviceNpcTypeKey,
        boolean enabled
) implements CustomPacketPayload {
    public static final Type<ServiceNpcSpawnConfigureC2SPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "service_npc_spawn_configure")
    );
    public static final StreamCodec<FriendlyByteBuf, ServiceNpcSpawnConfigureC2SPayload> STREAM_CODEC =
            StreamCodec.of(ServiceNpcSpawnConfigureC2SPayload::encode, ServiceNpcSpawnConfigureC2SPayload::decode);

    private static void encode(FriendlyByteBuf buffer, ServiceNpcSpawnConfigureC2SPayload payload) {
        buffer.writeVarInt(payload.containerId);
        buffer.writeBlockPos(payload.pos);
        buffer.writeUUID(payload.spawnPointId);
        buffer.writeLong(payload.expectedConfigurationRevision);
        buffer.writeUUID(payload.cityPublicId);
        ServiceNpcSpawnPayloadCodec.writeUtf(
                buffer,
                payload.serviceNpcTypeKey,
                ServiceNpcSpawnPayloadCodec.MAX_TYPE_KEY_BYTES
        );
        buffer.writeBoolean(payload.enabled);
    }

    private static ServiceNpcSpawnConfigureC2SPayload decode(FriendlyByteBuf buffer) {
        ServiceNpcSpawnPayloadCodec.requireReadableLimit(buffer, ServiceNpcSpawnPayloadCodec.MAX_C2S_BYTES);
        return new ServiceNpcSpawnConfigureC2SPayload(
                buffer.readVarInt(),
                buffer.readBlockPos(),
                buffer.readUUID(),
                buffer.readLong(),
                buffer.readUUID(),
                ServiceNpcSpawnPayloadCodec.readUtf(buffer, ServiceNpcSpawnPayloadCodec.MAX_TYPE_KEY_BYTES),
                buffer.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
