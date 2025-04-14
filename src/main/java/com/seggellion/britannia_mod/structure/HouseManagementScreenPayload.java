package com.seggellion.britannia_mod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import net.minecraft.network.chat.Component;

import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.UUID;

public record HouseManagementScreenPayload(BlockPos signPos, UUID uuid, String username, String houseType) implements CustomPacketPayload {
    

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
    UUID uuid = buf.readUUID();
    String username = buf.readUtf();
    String houseType = buf.readUtf();
    return new HouseManagementScreenPayload(new BlockPos(x, y, z), uuid, username, houseType);
}


public static void encode(FriendlyByteBuf buf, HouseManagementScreenPayload payload) {
    buf.writeInt(payload.signPos().getX());
    buf.writeInt(payload.signPos().getY());
    buf.writeInt(payload.signPos().getZ());
    buf.writeUUID(payload.uuid());
    buf.writeUtf(payload.username());
    buf.writeUtf(payload.houseType());
}


    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // This is a static helper to send this payload to a client.
public static void send(ServerPlayer player, BlockPos pos) {
    BlockEntity be = player.level().getBlockEntity(pos.below());
    if (!(be instanceof HouseLotBlockEntity lotBE)) {
        player.sendSystemMessage(Component.literal("House data could not be loaded."));
        return;
    }

    UUID uuid = lotBE.getHouseUuid();
    String username = lotBE.getOwner();
    String houseType = lotBE.getHouseType();

    NetworkHandler.sendToPlayer(player, new HouseManagementScreenPayload(pos, uuid, username, houseType));
}


}
