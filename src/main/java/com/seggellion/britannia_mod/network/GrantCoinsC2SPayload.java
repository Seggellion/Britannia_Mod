package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;


public record GrantCoinsC2SPayload(
        int gold,
        int silver,
        int copper,
        String city,
        String receipt,
        List<SoldItem> soldItems
) implements CustomPacketPayload {

    public static record SoldItem(String itemId, String itemName, int quantity, double weight) {}

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
            String json = ByteBufCodecs.STRING_UTF8.decode(buf);
            
            List<SoldItem> sold = new ArrayList<>();
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement e : arr) {
                var o = e.getAsJsonObject();
                sold.add(new SoldItem(
                        o.get("item_id").getAsString(),
                        o.get("item_name").getAsString(),
                        o.get("quantity").getAsInt(),
                        o.has("weight") ? o.get("weight").getAsDouble() : 0.0
                ));
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
            JsonArray arr = new JsonArray();
            for (SoldItem i : pkt.soldItems) {
                var o = new com.google.gson.JsonObject();
                o.addProperty("item_id", i.itemId());
                o.addProperty("item_name", i.itemName());
                o.addProperty("quantity", i.quantity());
                o.addProperty("weight", i.weight());
                arr.add(o);
            }
            ByteBufCodecs.STRING_UTF8.encode(buf, arr.toString());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
