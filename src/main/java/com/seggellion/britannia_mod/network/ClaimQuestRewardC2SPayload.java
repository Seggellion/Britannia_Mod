package com.seggellion.britannia_mod.network.payload;

import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.seggellion.britannia_mod.quest.network.QuestModels.ItemData;
import java.util.List;

public record ClaimQuestRewardC2SPayload(
        List<ItemData> items,
        long questId,
        String destroyTriggerKey,
        String destroyItemTag,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath("britannia_mod", "claim_quest_reward");
    public static final Type<ClaimQuestRewardC2SPayload> TYPE = new Type<>(TYPE_ID);

    public ClaimQuestRewardC2SPayload(List<ItemData> items) {
        this(items, 0L, "", "", 0, 0, 0, 0, 0, 0);
    }

    public static ClaimQuestRewardC2SPayload fromResponse(QuestModels.QuestResponse response) {
        if (response == null) {
            return new ClaimQuestRewardC2SPayload(List.of());
        }

        String triggerKey = "";
        String itemTag = "";
        int minX = 0;
        int minY = 0;
        int minZ = 0;
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;

        if (response.currentNode != null && response.currentNode.metadata != null
                && response.currentNode.metadata.has("destroy_trigger")
                && response.currentNode.metadata.get("destroy_trigger").isJsonObject()) {
            JsonObject destroyTrigger = response.currentNode.metadata.getAsJsonObject("destroy_trigger");
            triggerKey = getString(destroyTrigger, "trigger_key");
            itemTag = getString(destroyTrigger, "item_tag");
            minX = getInt(destroyTrigger, "min_x");
            minY = getInt(destroyTrigger, "min_y");
            minZ = getInt(destroyTrigger, "min_z");
            maxX = getInt(destroyTrigger, "max_x");
            maxY = getInt(destroyTrigger, "max_y");
            maxZ = getInt(destroyTrigger, "max_z");
        }

        return new ClaimQuestRewardC2SPayload(
                response.granted_items,
                response.quest_id,
                triggerKey,
                itemTag,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ
        );
    }

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
            long questId = buf.readLong();
            String destroyTriggerKey = ByteBufCodecs.STRING_UTF8.decode(buf);
            String destroyItemTag = ByteBufCodecs.STRING_UTF8.decode(buf);
            int minX = buf.readInt();
            int minY = buf.readInt();
            int minZ = buf.readInt();
            int maxX = buf.readInt();
            int maxY = buf.readInt();
            int maxZ = buf.readInt();
            return new ClaimQuestRewardC2SPayload(
                    list,
                    questId,
                    destroyTriggerKey,
                    destroyItemTag,
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ
            );
        }

        @Override
        public void encode(FriendlyByteBuf buf, ClaimQuestRewardC2SPayload payload) {
            buf.writeVarInt(payload.items.size());
            for (ItemData data : payload.items) {
                ByteBufCodecs.STRING_UTF8.encode(buf, data.id);
                buf.writeVarInt(data.count);
            }
            buf.writeLong(payload.questId);
            ByteBufCodecs.STRING_UTF8.encode(buf, safeString(payload.destroyTriggerKey));
            ByteBufCodecs.STRING_UTF8.encode(buf, safeString(payload.destroyItemTag));
            buf.writeInt(payload.minX);
            buf.writeInt(payload.minY);
            buf.writeInt(payload.minZ);
            buf.writeInt(payload.maxX);
            buf.writeInt(payload.maxY);
            buf.writeInt(payload.maxZ);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public boolean hasDestroyTriggerContext() {
        return questId > 0 && destroyTriggerKey != null && !destroyTriggerKey.isBlank()
                && destroyItemTag != null && !destroyItemTag.isBlank();
    }

    private static String getString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static int getInt(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) return 0;
        try {
            return object.get(key).getAsInt();
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}
