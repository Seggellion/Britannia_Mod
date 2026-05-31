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
            String city = ByteBufCodecs.STRING_UTF8.decode(buf);
            String role = ByteBufCodecs.STRING_UTF8.decode(buf);
            int entityId = ByteBufCodecs.VAR_INT.decode(buf);

            int size = buf.readVarInt();
            List<ItemRequest> items = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String itemId = buf.readUtf();
                String itemName = buf.readUtf();
                int quantity = buf.readVarInt();
                CompoundTag matchNbt = buf.readNbt();
                items.add(new ItemRequest(itemId, itemName, quantity, matchNbt));
            }

            return new SellItemsC2SPayload(city, role, entityId, items);
        }

        @Override
        public void encode(FriendlyByteBuf buf, SellItemsC2SPayload payload) {
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.city);
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.role);
            ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);

            buf.writeVarInt(payload.items.size());
            for (ItemRequest item : payload.items) {
                buf.writeUtf(item.itemId());
                buf.writeUtf(item.itemName());
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
