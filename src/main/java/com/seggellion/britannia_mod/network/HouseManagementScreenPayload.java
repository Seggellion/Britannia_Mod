package com.seggellion.britannia_mod.network;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.UUID;

public record HouseManagementScreenPayload(BlockPos pos, UUID uuid, String username, String houseType, String houseName) implements CustomPacketPayload {
    
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final CustomPacketPayload.Type<HouseManagementScreenPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "house_management_screen"));

    public static final StreamCodec<FriendlyByteBuf, HouseManagementScreenPayload> STREAM_CODEC = StreamCodec.of(
            HouseManagementScreenPayload::encode,
            HouseManagementScreenPayload::decode
    );

public static HouseManagementScreenPayload decode(FriendlyByteBuf buf) {
    BlockPos pos = buf.readBlockPos();
    UUID uuid = buf.readUUID();
    String username = buf.readUtf();
    String houseType = buf.readUtf();
    String houseName = buf.readUtf();
    return new HouseManagementScreenPayload(pos, uuid, username, houseType, houseName);
}

public static void encode(FriendlyByteBuf buf, HouseManagementScreenPayload payload) {
    buf.writeBlockPos(payload.pos());
    buf.writeUUID(payload.uuid());
    buf.writeUtf(payload.username());
    buf.writeUtf(payload.houseType());
    buf.writeUtf(payload.houseName());
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
    String houseName = lotBE.getHouseName();
    LOGGER.info(" Sending HouseManagementScreenPayload to {}", player.getName().getString());

    NetworkHandler.sendToPlayer(player, new HouseManagementScreenPayload(pos, uuid, username, houseType, houseName));
}


}
