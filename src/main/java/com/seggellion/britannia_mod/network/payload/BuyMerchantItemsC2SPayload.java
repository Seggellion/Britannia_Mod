package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record BuyMerchantItemsC2SPayload(
        String city,
        String role,
        int entityId,
        List<ItemRequest> items
) implements CustomPacketPayload {
    public static final int MAX_ITEMS = 64;
    public static final int MAX_QUANTITY = 1_024;
    public record ItemRequest(String itemId, String itemName, int quantity) {}

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "buy_merchant_items");
    public static final Type<BuyMerchantItemsC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, BuyMerchantItemsC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BuyMerchantItemsC2SPayload decode(FriendlyByteBuf buf) {
            String city = buf.readUtf(100);
            String role = buf.readUtf(100);
            int entityId = ByteBufCodecs.VAR_INT.decode(buf);
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_ITEMS) throw new IllegalArgumentException("Invalid purchase item count");
            List<ItemRequest> items = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String itemId = buf.readUtf(128);
                String itemName = buf.readUtf(256);
                int quantity = buf.readVarInt();
                if (quantity <= 0 || quantity > MAX_QUANTITY) throw new IllegalArgumentException("Invalid purchase quantity");
                items.add(new ItemRequest(itemId, itemName, quantity));
            }
            return new BuyMerchantItemsC2SPayload(city, role, entityId, items);
        }

        @Override
        public void encode(FriendlyByteBuf buf, BuyMerchantItemsC2SPayload payload) {
            buf.writeUtf(payload.city, 100);
            buf.writeUtf(payload.role, 100);
            ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
            if (payload.items.size() > MAX_ITEMS) throw new IllegalArgumentException("Too many purchase items");
            buf.writeVarInt(payload.items.size());
            for (ItemRequest item : payload.items) {
                if (item.quantity() <= 0 || item.quantity() > MAX_QUANTITY) {
                    throw new IllegalArgumentException("Invalid purchase quantity");
                }
                buf.writeUtf(item.itemId(), 128);
                buf.writeUtf(item.itemName(), 256);
                buf.writeVarInt(item.quantity());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
