package com.seggellion.britannia_mod.network.payload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record SellItemsC2SPayload(
        String city,
        String role,
        int entityId,
        List<ItemRequest> items
) implements CustomPacketPayload {
    public static final int MAX_ITEMS = 64;
    public static final int MAX_QUANTITY = 1_024;

    public static record ItemRequest(
            String itemId,
            String itemName,
            int quantity,
            @Nullable CompoundTag matchNbt
    ) {}

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "sell_items");
    public static final Type<SellItemsC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, SellItemsC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SellItemsC2SPayload decode(FriendlyByteBuf buf) {
            String city = buf.readUtf(100);
            String role = buf.readUtf(100);
            int entityId = ByteBufCodecs.VAR_INT.decode(buf);

            int size = buf.readVarInt();
            if (size < 0 || size > MAX_ITEMS) throw new IllegalArgumentException("Invalid sell item count");
            List<ItemRequest> items = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String itemId = buf.readUtf(128);
                String itemName = buf.readUtf(256);
                int quantity = buf.readVarInt();
                if (quantity <= 0 || quantity > MAX_QUANTITY) throw new IllegalArgumentException("Invalid sell quantity");
                CompoundTag matchNbt = buf.readNbt();
                items.add(new ItemRequest(itemId, itemName, quantity, matchNbt));
            }

            return new SellItemsC2SPayload(city, role, entityId, items);
        }

        @Override
        public void encode(FriendlyByteBuf buf, SellItemsC2SPayload payload) {
            buf.writeUtf(payload.city, 100);
            buf.writeUtf(payload.role, 100);
            ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);

            if (payload.items.size() > MAX_ITEMS) throw new IllegalArgumentException("Too many sell items");
            buf.writeVarInt(payload.items.size());
            for (ItemRequest item : payload.items) {
                if (item.quantity() <= 0 || item.quantity() > MAX_QUANTITY) {
                    throw new IllegalArgumentException("Invalid sell quantity");
                }
                buf.writeUtf(item.itemId(), 128);
                buf.writeUtf(item.itemName(), 256);
                buf.writeVarInt(item.quantity());
                buf.writeNbt(item.matchNbt());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
