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
    public record ItemRequest(String itemId, String itemName, int quantity) {}

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "buy_merchant_items");
    public static final Type<BuyMerchantItemsC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, BuyMerchantItemsC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BuyMerchantItemsC2SPayload decode(FriendlyByteBuf buf) {
            String city = ByteBufCodecs.STRING_UTF8.decode(buf);
            String role = ByteBufCodecs.STRING_UTF8.decode(buf);
            int entityId = ByteBufCodecs.VAR_INT.decode(buf);
            int size = buf.readVarInt();
            List<ItemRequest> items = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                items.add(new ItemRequest(buf.readUtf(), buf.readUtf(), buf.readVarInt()));
            }
            return new BuyMerchantItemsC2SPayload(city, role, entityId, items);
        }

        @Override
        public void encode(FriendlyByteBuf buf, BuyMerchantItemsC2SPayload payload) {
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.city);
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.role);
            ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
            buf.writeVarInt(payload.items.size());
            for (ItemRequest item : payload.items) {
                buf.writeUtf(item.itemId());
                buf.writeUtf(item.itemName());
                buf.writeVarInt(item.quantity());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
