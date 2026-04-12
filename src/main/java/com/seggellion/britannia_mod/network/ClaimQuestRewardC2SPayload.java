package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.seggellion.britannia_mod.quest.network.QuestModels.ItemData;
import java.util.List;

public record ClaimQuestRewardC2SPayload(List<ItemData> items) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath("britannia_mod", "claim_quest_reward");
    public static final Type<ClaimQuestRewardC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, ClaimQuestRewardC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ClaimQuestRewardC2SPayload decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            java.util.ArrayList<ItemData> list = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ItemData data = new ItemData();
                data.id = ByteBufCodecs.STRING_UTF8.decode(buf);
                data.count = buf.readVarInt();
                list.add(data);
            }
            return new ClaimQuestRewardC2SPayload(list);
        }

        @Override
        public void encode(FriendlyByteBuf buf, ClaimQuestRewardC2SPayload payload) {
            buf.writeVarInt(payload.items.size());
            for (ItemData data : payload.items) {
                ByteBufCodecs.STRING_UTF8.encode(buf, data.id);
                buf.writeVarInt(data.count);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}