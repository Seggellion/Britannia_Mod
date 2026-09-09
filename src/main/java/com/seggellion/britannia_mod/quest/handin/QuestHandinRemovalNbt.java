package com.seggellion.britannia_mod.quest.handin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * NBT for a removal proof, shared by the two places it has to be durable: the world-anchored ledger
 * and the player's own file.
 *
 * <p>One codec rather than two, deliberately. The whole crash-safety argument rests on those two
 * copies describing the same removal, and two hand-written serialisers that drifted apart would
 * break it silently -- a restart would compare a proof against a proof and see a difference that
 * was never in the transaction.
 */
public final class QuestHandinRemovalNbt {

    private static final String KEY_ITEM = "Item";
    private static final String KEY_COUNT = "Count";
    private static final String KEY_INDEX = "RequirementIndex";
    private static final String KEY_RESOLVER = "Resolver";
    private static final String KEY_FLAG_VALUE = "FlagValue";

    private QuestHandinRemovalNbt() {}

    public static CompoundTag toNbt(QuestHandinRemoval removal) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_ITEM, removal.itemId());
        tag.putInt(KEY_COUNT, removal.count());
        tag.putInt(KEY_INDEX, removal.requirementIndex());
        if (removal.answersResolver()) {
            tag.putString(KEY_RESOLVER, removal.resolver());
            tag.putString(KEY_FLAG_VALUE, removal.flagValue());
        }
        return tag;
    }

    /**
     * @throws IllegalArgumentException when the tag does not describe a removal this build would
     *         have written; the caller quarantines the entry rather than acting on half of it
     */
    public static QuestHandinRemoval fromNbt(CompoundTag tag) {
        if (tag == null || !tag.contains(KEY_ITEM, Tag.TAG_STRING)
                || !tag.contains(KEY_COUNT, Tag.TAG_INT) || !tag.contains(KEY_INDEX, Tag.TAG_INT)) {
            throw new IllegalArgumentException("removal proof entry is missing a required field");
        }
        boolean hasResolver = tag.contains(KEY_RESOLVER, Tag.TAG_STRING);
        boolean hasFlagValue = tag.contains(KEY_FLAG_VALUE, Tag.TAG_STRING);
        if (hasResolver != hasFlagValue) {
            throw new IllegalArgumentException("a resolver proof entry carries both resolver and flag value");
        }
        return new QuestHandinRemoval(tag.getInt(KEY_INDEX), tag.getString(KEY_ITEM),
                tag.getInt(KEY_COUNT), hasResolver ? tag.getString(KEY_RESOLVER) : null,
                hasFlagValue ? tag.getString(KEY_FLAG_VALUE) : null);
    }

    public static ListTag toList(List<QuestHandinRemoval> removals) {
        ListTag list = new ListTag();
        removals.forEach(removal -> list.add(toNbt(removal)));
        return list;
    }

    public static List<QuestHandinRemoval> fromList(ListTag list) {
        if (list == null || list.isEmpty()) return List.of();
        if (list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("removal proof must be a list of compounds");
        }
        if (list.size() > QuestHandinRequirement.MAX_REQUIREMENTS) {
            throw new IllegalArgumentException("removal proof is outside its bound");
        }
        List<QuestHandinRemoval> removals = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            removals.add(fromNbt(list.getCompound(index)));
        }
        return List.copyOf(removals);
    }
}
