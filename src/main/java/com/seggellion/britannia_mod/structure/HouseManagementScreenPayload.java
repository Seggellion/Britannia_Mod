package com.seggellion.britannia_mod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record HouseManagementScreenPayload(BlockPos signPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HouseManagementScreenPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "house_management_screen"));

    public static final StreamCodec<FriendlyByteBuf, HouseManagementScreenPayload> STREAM_CODEC = StreamCodec.of(
            HouseManagementScreenPayload::encode,
            HouseManagementScreenPayload::decode
    );

    public static HouseManagementScreenPayload decode(FriendlyByteBuf buf) {
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        return new HouseManagementScreenPayload(new BlockPos(x, y, z));
    }

    public static void encode(FriendlyByteBuf buf, HouseManagementScreenPayload payload) {
        buf.writeInt(payload.signPos().getX());
        buf.writeInt(payload.signPos().getY());
        buf.writeInt(payload.signPos().getZ());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // This is a static helper to send this payload to a client.
    public static void send(ServerPlayer player, BlockPos pos) {
        // Replace this with your actual networking call.
        // For example, if you have a network channel registered:
        // MyNetworkChannel.sendToClient(player, new HouseManagementScreenPayload(pos));
        System.out.println("Sending HouseManagementScreenPayload to " + player.getUUID() + " for sign at " + pos);
    }
}
