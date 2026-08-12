package com.seggellion.britannia_mod.quest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Decides whether an item stack satisfies a quest objective's item tag.
 *
 * <p>Lifted out of {@code QuestEventHandlers} so the server-side objective watcher and the
 * item-destruction path share one definition rather than two that can drift. The matching rules
 * are unchanged: stamped quest data first, then a custom display name, then the registry id --
 * quests in this world are authored against all three.
 */
public final class QuestItemMatcher {
    private QuestItemMatcher() {}

    public static boolean matches(ItemStack stack, String targetTag) {
        if (stack == null || stack.isEmpty() || targetTag == null || targetTag.isBlank()) return false;

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.contains("quest_item")
            && customData.copyTag().getString("quest_item").equals(targetTag)) {
            return true;
        }

        if (stack.has(DataComponents.CUSTOM_NAME)) {
            String itemName = stack.get(DataComponents.CUSTOM_NAME).getString();
            if (itemName.equalsIgnoreCase(targetTag)) return true;
        }

        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equalsIgnoreCase(targetTag);
    }
}
