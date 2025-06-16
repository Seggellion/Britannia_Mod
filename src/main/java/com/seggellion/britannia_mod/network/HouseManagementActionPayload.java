package com.seggellion.britannia_mod.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLLoader;


public record HouseManagementActionPayload(Action action) implements CustomPacketPayload {

    public enum Action {
        REDEED
        // Additional actions can be added here.
    }

    public static final CustomPacketPayload.Type<HouseManagementActionPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "house_management_action"));

    public static final StreamCodec<FriendlyByteBuf, HouseManagementActionPayload> STREAM_CODEC = StreamCodec.of(
        HouseManagementActionPayload::encode,
        HouseManagementActionPayload::decode
    );

    public static HouseManagementActionPayload decode(FriendlyByteBuf buf) {
        int ordinal = buf.readInt();
        Action action = Action.values()[ordinal];
        return new HouseManagementActionPayload(action);
    }

    public static void encode(FriendlyByteBuf buf, HouseManagementActionPayload payload) {
        buf.writeInt(payload.action().ordinal());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendAction(Action action) {
        if (FMLLoader.getDist().isClient()) {
            Minecraft.getInstance().getConnection().send(
                new ServerboundCustomPayloadPacket(new HouseManagementActionPayload(action))
            );
        }
    }

    
}
