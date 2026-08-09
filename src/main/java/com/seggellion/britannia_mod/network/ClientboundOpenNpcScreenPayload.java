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
import com.seggellion.britannia_mod.economy.ServerCatalogService;
import com.seggellion.britannia_mod.shop.Product;

import java.util.ArrayList;
import java.util.List;

public record ClientboundOpenNpcScreenPayload(
        NpcType npcType,
        String role,
        String city,
        int entityId,
        List<ProductData> products
) implements CustomPacketPayload {
    public static final int MAX_PRODUCTS = 256;

    public static final Type<ClientboundOpenNpcScreenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "open_npc_screen"));

    public static final StreamCodec<FriendlyByteBuf, ClientboundOpenNpcScreenPayload>
        STREAM_CODEC = StreamCodec.of(
            ClientboundOpenNpcScreenPayload::encode,
            ClientboundOpenNpcScreenPayload::decode);

    public static ClientboundOpenNpcScreenPayload decode(FriendlyByteBuf buf) {
        NpcType npcType = buf.readEnum(NpcType.class);
        String role = buf.readUtf(100);
        String city = buf.readUtf(100);
        int id = buf.readInt();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_PRODUCTS) throw new IllegalArgumentException("Invalid catalog product count");
        List<ProductData> products = new ArrayList<>(count);
        for (int index = 0; index < count; index++) products.add(ProductData.decode(buf));
        return new ClientboundOpenNpcScreenPayload(npcType, role, city, id, List.copyOf(products));
    }

    public static void encode(FriendlyByteBuf buf, ClientboundOpenNpcScreenPayload payload) {
        buf.writeEnum(payload.npcType()); // now unambiguous
        buf.writeUtf(payload.role(), 100);
        buf.writeUtf(payload.city(), 100);
        buf.writeInt(payload.entityId());
        if (payload.products() == null || payload.products().size() > MAX_PRODUCTS) {
            throw new IllegalArgumentException("Invalid catalog product count");
        }
        buf.writeVarInt(payload.products().size());
        payload.products().forEach(product -> product.encode(buf));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, NpcType type, String role, String city, int entityId) {
        ServerCatalogService.fetchAndSend(player, type, role, city, entityId);
    }

    public static void sendResolved(ServerPlayer player, NpcType type, String role, String city,
                                    int entityId, List<Product> products) {
        List<ProductData> encoded = products.stream().limit(MAX_PRODUCTS).map(ProductData::from).toList();
        NetworkHandler.sendToPlayer(player, new ClientboundOpenNpcScreenPayload(type, role, city, entityId, encoded));
    }

    public record ProductData(String itemId, String name, int price, String currency, String icon) {
        static ProductData decode(FriendlyByteBuf buf) {
            ProductData product = new ProductData(
                buf.readUtf(128), buf.readUtf(256), buf.readVarInt(), buf.readUtf(16), buf.readUtf(256));
            product.validate();
            return product;
        }
        void encode(FriendlyByteBuf buf) {
            validate();
            buf.writeUtf(itemId, 128);
            buf.writeUtf(name, 256);
            buf.writeVarInt(price);
            buf.writeUtf(currency, 16);
            buf.writeUtf(icon, 256);
        }
        private static ProductData from(Product product) {
            return new ProductData(product.itemId(), product.name(), Math.max(0, product.price()), product.currency(),
                product.iconPath() == null ? "" : product.iconPath().toString());
        }
        private void validate() {
            if (itemId == null || itemId.isBlank() || itemId.length() > 128
                || name == null || name.isBlank() || name.length() > 256
                || price < 0 || currency == null || currency.isBlank() || currency.length() > 16
                || icon == null || icon.length() > 256) {
                throw new IllegalArgumentException("Invalid catalog product");
            }
        }
    }
}
