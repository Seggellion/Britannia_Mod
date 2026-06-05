package com.seggellion.britannia_mod.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Locale;

public class CookedFishSteakItem extends Item {
    public CookedFishSteakItem(Properties properties) {
        super(properties);
    }

    public static void setFishType(ItemStack stack, String fishType) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
        CompoundTag tag = data.copyTag();
        tag.putString("FishType", normalizeFishType(fishType));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static String getFishType(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
        CompoundTag tag = data.copyTag();
        return tag.contains("FishType") ? tag.getString("FishType") : "unknown";
    }

    @Override
    public Component getName(ItemStack stack) {
        String fishType = getFishType(stack);
        if ("unknown".equals(fishType)) {
            return Component.literal("Fish Steak");
        }

        String fishName = toTitleCase(fishType);
        String displayName = fishName.endsWith(" Fish")
                ? fishName + " Steak"
                : fishName + " Fish Steak";
        return Component.literal(displayName);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String fishType = getFishType(stack);
        if (!"unknown".equals(fishType)) {
            tooltip.add(Component.literal("Source: " + toTitleCase(fishType)));
        }
    }

    private static String normalizeFishType(String raw) {
        if (raw == null || raw.isBlank()) return "unknown";
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("[^a-z0-9_]", "");
    }

    private static String toTitleCase(String raw) {
        String normalized = normalizeFishType(raw);
        if ("unknown".equals(normalized)) return "Unknown";

        StringBuilder out = new StringBuilder();
        for (String part : normalized.split("_")) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.length() == 0 ? "Unknown" : out.toString();
    }
}
