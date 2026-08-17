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
        List<ProductData> products,
        List<Notice> notices
) implements CustomPacketPayload {
    public static final int MAX_PRODUCTS = 256;
    public static final int MAX_NOTICES = 16;

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
        int noticeCount = buf.readVarInt();
        if (noticeCount < 0 || noticeCount > MAX_NOTICES) throw new IllegalArgumentException("Invalid catalog notice count");
        List<Notice> notices = new ArrayList<>(noticeCount);
        for (int index = 0; index < noticeCount; index++) notices.add(Notice.decode(buf));
        return new ClientboundOpenNpcScreenPayload(npcType, role, city, id, List.copyOf(products), List.copyOf(notices));
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
        if (payload.notices() == null || payload.notices().size() > MAX_NOTICES) {
            throw new IllegalArgumentException("Invalid catalog notice count");
        }
        buf.writeVarInt(payload.notices().size());
        payload.notices().forEach(notice -> notice.encode(buf));
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
        sendResolved(player, type, role, city, entityId, products, List.of());
    }

    public static void sendResolved(ServerPlayer player, NpcType type, String role, String city,
                                    int entityId, List<Product> products, List<Notice> notices) {
        List<ProductData> encoded = products.stream().limit(MAX_PRODUCTS).map(ProductData::from).toList();
        NetworkHandler.sendToPlayer(player, new ClientboundOpenNpcScreenPayload(
            type, role, city, entityId, encoded, notices.stream().limit(MAX_NOTICES).toList()));
    }

    /**
     * One reason the trader could not take something, carried as the Rails reason code plus the
     * thing it concerns (a commodity display name, or a denomination for treasury reasons).
     *
     * <p>Codes travel rather than prose so the wording stays client-side with the rest of the
     * NPC voice. Before this existed every failure — an empty offer, a broke city, a full
     * warehouse, a commodity the NPC does not buy — collapsed into the single line "I am not
     * interested in anything you have", which is why a whole class of Trader being misrouted to
     * the retail catalog stayed invisible for months.
     */
    public record Notice(String code, String subject) {
        public static final String COMMODITY_NOT_FOUND = "commodity_not_found";
        public static final String COMMODITY_NOT_BUYABLE = "commodity_not_buyable";
        public static final String COMMODITY_STOCK_CAP_EXCEEDED = "commodity_stock_cap_exceeded";
        public static final String MIXED_PAYOUT_DENOMINATIONS = "mixed_payout_denominations";
        public static final String VALUE_BELOW_DENOMINATION_MINIMUM = "value_below_denomination_minimum";
        public static final String TREASURY_INSUFFICIENT = "treasury_insufficient";
        public static final String TREASURY_DENOMINATION_UNAVAILABLE = "treasury_denomination_unavailable";
        public static final String TRADER_NOT_FOUND = "trader_not_found";
        public static final String TRADER_NOT_ASSIGNED = "trader_not_assigned";

        public Notice {
            if (code == null || code.isBlank() || code.length() > 64
                || subject == null || subject.length() > 128) {
                throw new IllegalArgumentException("Invalid catalog notice");
            }
        }

        static Notice decode(FriendlyByteBuf buf) {
            return new Notice(buf.readUtf(64), buf.readUtf(128));
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(code, 64);
            buf.writeUtf(subject, 128);
        }
    }

    /**
     * @param price          per-item coin price, for display only
     * @param lineValueCopper the row's exact total value in hundredths of a copper, or 0 when no
     *                       server priced it. A per-item coin price cannot express a row worth
     *                       2.5 silver, so a cart summed from {@code price} drifts; this is what
     *                       the cart is actually totalled from.
     * @param quotedQuantity the item count {@code lineValueCopper} covers, or 0 when unpriced
     */
    public record ProductData(String itemId, String name, int price, String currency, String icon,
                              long lineValueCopper, int quotedQuantity) {
        static ProductData decode(FriendlyByteBuf buf) {
            ProductData product = new ProductData(
                buf.readUtf(128), buf.readUtf(256), buf.readVarInt(), buf.readUtf(16), buf.readUtf(256),
                buf.readVarLong(), buf.readVarInt());
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
            buf.writeVarLong(lineValueCopper);
            buf.writeVarInt(quotedQuantity);
        }
        private static ProductData from(Product product) {
            return new ProductData(product.itemId(), product.name(), Math.max(0, product.price()), product.currency(),
                product.iconPath() == null ? "" : product.iconPath().toString(),
                Math.max(0L, product.lineValueCopper()), Math.max(0, product.quotedQuantity()));
        }
        private void validate() {
            if (itemId == null || itemId.isBlank() || itemId.length() > 128
                || name == null || name.isBlank() || name.length() > 256
                || price < 0 || currency == null || currency.isBlank() || currency.length() > 16
                || icon == null || icon.length() > 256
                || lineValueCopper < 0 || quotedQuantity < 0) {
                throw new IllegalArgumentException("Invalid catalog product");
            }
        }
    }
}
