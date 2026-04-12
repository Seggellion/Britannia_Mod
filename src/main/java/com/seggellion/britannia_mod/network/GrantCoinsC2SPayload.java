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

public record GrantCoinsC2SPayload(
        int gold,
        int silver,
        int copper,
        String city,
        String receipt,
        List<SoldItem> soldItems
) implements CustomPacketPayload {

    public static record SoldItem(
        String itemId, 
        String itemName, 
        int quantity, 
        double weight, 
        @Nullable CompoundTag nbt
    ) {}

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "grant_coins_c2s");
    public static final Type<GrantCoinsC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, GrantCoinsC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public GrantCoinsC2SPayload decode(FriendlyByteBuf buf) {
            int gold   = ByteBufCodecs.VAR_INT.decode(buf);
            int silver = ByteBufCodecs.VAR_INT.decode(buf);
            int copper = ByteBufCodecs.VAR_INT.decode(buf);
            String city = ByteBufCodecs.STRING_UTF8.decode(buf);
            String receipt = ByteBufCodecs.STRING_UTF8.decode(buf);

            // ✅ Native List Decoding (No JSON)
            int listSize = buf.readVarInt();
            List<SoldItem> sold = new ArrayList<>(listSize);
            
            for (int i = 0; i < listSize; i++) {
                String itemId = buf.readUtf();
                String itemName = buf.readUtf();
                int quantity = buf.readVarInt();
                double weight = buf.readDouble();
                CompoundTag nbt = buf.readNbt(); // Reads the tag or null automatically

                sold.add(new SoldItem(itemId, itemName, quantity, weight, nbt));
            }

            return new GrantCoinsC2SPayload(gold, silver, copper, city, receipt, sold);
        }

        @Override
        public void encode(FriendlyByteBuf buf, GrantCoinsC2SPayload pkt) {
            ByteBufCodecs.VAR_INT.encode(buf, pkt.gold);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.silver);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.copper);
            ByteBufCodecs.STRING_UTF8.encode(buf, pkt.city);
            ByteBufCodecs.STRING_UTF8.encode(buf, pkt.receipt);

            // ✅ Native List Encoding (No JSON)
            buf.writeVarInt(pkt.soldItems.size());
            for (SoldItem i : pkt.soldItems) {
                buf.writeUtf(i.itemId());
                buf.writeUtf(i.itemName());
                buf.writeVarInt(i.quantity());
                buf.writeDouble(i.weight());
                buf.writeNbt(i.nbt()); // Native NBT support
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}