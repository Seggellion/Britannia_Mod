package com.seggellion.britannia_mod.quest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * The {@code CUSTOM_DATA} stamp a TEMPORARY quest item carries, and the two questions cleanup asks
 * of it (Rowan farming questline M1, discovery D1, protocol section 1.5).
 *
 * <p>Until this milestone {@link QuestRewardService} wrote the stamp onto EVERY granted stack and
 * {@link QuestCleanupService} deleted every stamped stack whose quest had left the journal, so a
 * rewarded shovel, coin or seed vanished at the first login after its quest completed. The stamp
 * now means exactly one thing: "this item exists for an objective and is taken back when the quest
 * ends".
 *
 * <p>Two shapes exist in the world:
 * <ul>
 *   <li><b>Temporary</b>: the stamp plus a non-blank {@link #TRIGGER_KEY}, the objective the item
 *       serves. Cleanup deletes it once its quest is no longer active.</li>
 *   <li><b>Legacy</b>: the stamp without a trigger key, written by the pre-M1 blanket stamping.
 *       Those items were permanent rewards all along (coins, tools, escort silver); cleanup strips
 *       the stamp and keeps the item. They are never deleted.</li>
 * </ul>
 */
public final class QuestItemStamp {
    public static final String ITEM = "quest_item";
    public static final String OWNER_UUID = "quest_owner_uuid";
    /** Written only by the pre-M1 stamp and read by nothing; removed with the rest when stripped. */
    public static final String OWNER_NAME = "quest_owner_name";
    public static final String QUEST_ID = "quest_id";
    public static final String QUEST_STATE_ID = "quest_state_id";
    public static final String QUEST_KEY = "quest_key";
    public static final String TRIGGER_KEY = "quest_trigger_key";
    public static final String MIN_X = "quest_min_x";
    public static final String MIN_Y = "quest_min_y";
    public static final String MIN_Z = "quest_min_z";
    public static final String MAX_X = "quest_max_x";
    public static final String MAX_Y = "quest_max_y";
    public static final String MAX_Z = "quest_max_z";

    /** Every key the stamp has ever consisted of, in either shape. */
    public static final List<String> KEYS = List.of(
        ITEM, OWNER_UUID, OWNER_NAME, QUEST_ID, QUEST_STATE_ID, QUEST_KEY, TRIGGER_KEY,
        MIN_X, MIN_Y, MIN_Z, MAX_X, MAX_Y, MAX_Z);

    private QuestItemStamp() {}

    /** A copy of the stack's custom data; empty for an empty stack or one without any. */
    public static CompoundTag read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** True when the tag carries any quest identity at all, of either shape. */
    public static boolean isStamped(CompoundTag tag) {
        return tag != null
            && (tag.contains(ITEM) || tag.contains(QUEST_ID) || tag.contains(QUEST_STATE_ID)
                || tag.contains(QUEST_KEY) || tag.contains(TRIGGER_KEY));
    }

    public static boolean isStamped(ItemStack stack) {
        return isStamped(read(stack));
    }

    /** A stamp that names its objective: the item is taken back when the quest ends. */
    public static boolean isTemporary(CompoundTag tag) {
        return isStamped(tag) && !tag.getString(TRIGGER_KEY).isBlank();
    }

    public static boolean isTemporary(ItemStack stack) {
        return isTemporary(read(stack));
    }

    /** A stamp with no objective: a pre-M1 blanket stamp on what was always a permanent reward. */
    public static boolean isLegacyPermanent(CompoundTag tag) {
        return isStamped(tag) && tag.getString(TRIGGER_KEY).isBlank();
    }

    /**
     * Removes every stamp key from the stack, dropping the custom-data component entirely when
     * nothing else was in it so the stack merges with plain stacks again.
     *
     * @return whether anything was removed
     */
    public static boolean strip(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag tag = read(stack);
        boolean changed = false;
        for (String key : KEYS) {
            if (tag.contains(key)) {
                tag.remove(key);
                changed = true;
            }
        }
        if (!changed) return false;
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return true;
    }
}
