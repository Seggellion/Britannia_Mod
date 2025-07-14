package com.seggellion.britannia_mod.network;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.client.gui.screen.ArchitectScreen;
import com.seggellion.britannia_mod.entity.ArchitectEntity;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.shop.Product;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;

import java.util.List;  
import org.slf4j.Logger;

public record ClientboundOpenArchitectScreenPayload(
        int entityId, List<Product> catalog)
        implements CustomPacketPayload {

    public static final Type<ClientboundOpenArchitectScreenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(
            "britannia_mod", "open_architect_screen"));

    public static final StreamCodec<FriendlyByteBuf,
                                    ClientboundOpenArchitectScreenPayload>
        STREAM_CODEC = StreamCodec.of(
            ClientboundOpenArchitectScreenPayload::encode,
            ClientboundOpenArchitectScreenPayload::decode);

public static ClientboundOpenArchitectScreenPayload decode(FriendlyByteBuf buf) {
    int id = buf.readInt();
    int count = buf.readVarInt();
    List<Product> products = new ArrayList<>();
    for (int i = 0; i < count; i++) {
        String itemId = buf.readUtf();
        String itemName = buf.readUtf();
        int price = buf.readVarInt();
        String icon = buf.readUtf();
        products.add(new Product(itemId, itemName, price, ResourceLocation.parse(icon)));
    }
    return new ClientboundOpenArchitectScreenPayload(id, products);
}

public static void encode(FriendlyByteBuf buf, ClientboundOpenArchitectScreenPayload payload) {
    buf.writeInt(payload.entityId());
    buf.writeVarInt(payload.catalog().size());
    for (Product p : payload.catalog()) {
        buf.writeUtf(p.itemId());
        buf.writeUtf(p.name());
        buf.writeVarInt(p.price());
        buf.writeUtf(p.stack().getItem().builtInRegistryHolder().key().location().toString());
    }
}

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void send(ServerPlayer player, ArchitectEntity architect) {
        NetworkHandler.sendToPlayer(player,
            new ClientboundOpenArchitectScreenPayload(
                architect.getId(), architect.catalog()));
    }

}
