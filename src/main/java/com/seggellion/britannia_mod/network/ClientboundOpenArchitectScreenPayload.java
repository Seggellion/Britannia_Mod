package com.seggellion.britannia_mod.network;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.client.gui.screen.ArchitectScreen;
import com.seggellion.britannia_mod.entity.ArchitectEntity;
import com.seggellion.britannia_mod.network.NetworkHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import org.slf4j.Logger;

public record ClientboundOpenArchitectScreenPayload(int entityId) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final CustomPacketPayload.Type<ClientboundOpenArchitectScreenPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "open_architect_screen"));

    public static final StreamCodec<FriendlyByteBuf, ClientboundOpenArchitectScreenPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundOpenArchitectScreenPayload::encode, ClientboundOpenArchitectScreenPayload::decode);

    public static ClientboundOpenArchitectScreenPayload decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        return new ClientboundOpenArchitectScreenPayload(id);
    }

    public static void encode(FriendlyByteBuf buf, ClientboundOpenArchitectScreenPayload payload) {
        buf.writeInt(payload.entityId());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Call this on the client when the packet is received */
    public static void handle(ClientboundOpenArchitectScreenPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(payload.entityId());
        if (entity instanceof ArchitectEntity architect) {
            Screen screen = new ArchitectScreen(architect.catalog(), architect.getId());

            mc.setScreen(screen);
        } else {
            LOGGER.warn("Entity with ID {} is not an ArchitectEntity", payload.entityId());
        }
    }

    /** Call this server-side to send the screen to the player */
    public static void send(ServerPlayer player, ArchitectEntity architect) {
        NetworkHandler.sendToPlayer(player, new ClientboundOpenArchitectScreenPayload(architect.getId()));
    }
}
