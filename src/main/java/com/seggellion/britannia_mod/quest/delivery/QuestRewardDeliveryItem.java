package com.seggellion.britannia_mod.quest.delivery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;

/**
 * One line of a reward delivery (Rowan farming questline protocol section 1.2/1.3):
 * {@code {"id": "britannia_mod:britannia_shovel", "count": 1, "temporary": false}}.
 *
 * <p>{@code temporary} is Rails' verdict from section 1.5 and may be absent on the wire when an
 * older Rails publishes a delivery without it; {@code null} here means "no verdict", exactly the
 * value {@link com.seggellion.britannia_mod.quest.QuestTemporaryItemPolicy} treats as "let the
 * destination-node heuristic decide". It is never defaulted to {@code true}: a missing flag must
 * not turn a permanent reward into one the cleanup takes back.
 */
public record QuestRewardDeliveryItem(String id, int count, Boolean temporary) {
    public static final int MAX_COUNT = 1_024;
    public static final int MAX_ID_LENGTH = 128;

    private static final String KEY_ID = "Id";
    private static final String KEY_COUNT = "Count";
    private static final String KEY_TEMPORARY = "Temporary";

    public QuestRewardDeliveryItem {
        Objects.requireNonNull(id, "id");
        if (!validId(id)) throw new IllegalArgumentException("delivery item id is invalid");
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("delivery item count is outside 1.." + MAX_COUNT);
        }
    }

    public static boolean validId(String id) {
        return id != null && !id.isBlank() && id.length() <= MAX_ID_LENGTH
            && id.chars().noneMatch(Character::isISOControl);
    }

    CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_ID, id);
        tag.putInt(KEY_COUNT, count);
        if (temporary != null) tag.putBoolean(KEY_TEMPORARY, temporary);
        return tag;
    }

    static QuestRewardDeliveryItem fromNbt(CompoundTag tag) {
        if (!tag.contains(KEY_ID, Tag.TAG_STRING) || !tag.contains(KEY_COUNT, Tag.TAG_INT)) {
            throw new IllegalArgumentException("missing delivery item id or count");
        }
        Boolean temporary = tag.contains(KEY_TEMPORARY, Tag.TAG_BYTE) ? tag.getBoolean(KEY_TEMPORARY) : null;
        return new QuestRewardDeliveryItem(tag.getString(KEY_ID), tag.getInt(KEY_COUNT), temporary);
    }
}
