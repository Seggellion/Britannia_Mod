package com.seggellion.britannia_mod.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public record MonsterSpawnScreenS2CPayload(
        BlockPos pos,
        ResourceLocation entityId,
        int radius,
        int minTicks,
        int maxTicks,
        boolean nightOnly,
        int maxEntities,
        List<ResourceLocation> activeEntities
) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "monster_spawn_screen");
    public static final Type<MonsterSpawnScreenS2CPayload> TYPE = new Type<>(TYPE_ID);

    // Custom encode/decode because composite(...) only supports up to 4 fields
  public static final StreamCodec<FriendlyByteBuf, MonsterSpawnScreenS2CPayload> STREAM_CODEC =
        StreamCodec.of(
                (buf, msg) -> {
                    BlockPos.STREAM_CODEC.encode(buf, msg.pos);
                    ResourceLocation.STREAM_CODEC.encode(buf, msg.entityId);
                    buf.writeVarInt(msg.radius);
                    buf.writeVarInt(msg.minTicks);
                    buf.writeVarInt(msg.maxTicks);
                    buf.writeBoolean(msg.nightOnly);
                    buf.writeVarInt(msg.maxEntities);

                    buf.writeVarInt(msg.activeEntities.size());
                    for (ResourceLocation rl : msg.activeEntities) {
                        ResourceLocation.STREAM_CODEC.encode(buf, rl);
                    }
                },
                buf -> {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    ResourceLocation entityId = ResourceLocation.STREAM_CODEC.decode(buf);
                    int radius = buf.readVarInt();
                    int minTicks = buf.readVarInt();
                    int maxTicks = buf.readVarInt();
                    boolean nightOnly = buf.readBoolean();
                    int maxEntities = buf.readVarInt();

                    int n = buf.readVarInt();
                    List<ResourceLocation> active = new java.util.ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        active.add(ResourceLocation.STREAM_CODEC.decode(buf));
                    }

                    return new MonsterSpawnScreenS2CPayload(
                        pos, entityId, radius, minTicks, maxTicks, nightOnly, maxEntities, active
                    );
                }
        );


    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // convenience sender (mirrors StoreSignScreenPayload.send)
    public static void send(net.minecraft.server.level.ServerPlayer player,
                            BlockPos pos, ResourceLocation entityId,
                            int radius, int minTicks, int maxTicks,
                            boolean nightOnly, int maxEntities,List<ResourceLocation> activeEntities) {
        com.seggellion.britannia_mod.network.NetworkHandler.sendToPlayer(
                player,
                new MonsterSpawnScreenS2CPayload(pos, entityId, radius, minTicks, maxTicks, nightOnly, maxEntities, activeEntities)
        );
    }
}
