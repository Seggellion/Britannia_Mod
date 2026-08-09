package com.seggellion.britannia_mod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.UUID;
import com.seggellion.britannia_mod.skill.crafting.CraftableDef;
import com.seggellion.britannia_mod.skill.crafting.CraftableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Backward-compatible serialization: old stacks simply read deliberate defaults. */
public final class BlacksmithItemData {
    private static final String ROOT = "BritanniaBlacksmithing";
    private BlacksmithItemData() {}

    public static void apply(ItemStack stack, UOMetalToolMaterial material, int quality,
                             String regionId, String regionName) {
        apply(stack, material, quality, regionId, regionName, null);
    }

    public static void apply(ItemStack stack, UOMetalToolMaterial material, int quality,
                             String regionId, String regionName, String recipeId) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        CompoundTag data = root.contains(ROOT) ? root.getCompound(ROOT) : new CompoundTag();
        data.putString("material", material.getMetalName().replace(' ', '_'));
        data.putInt("quality", quality);
        data.putString("region_id", safe(regionId, "unknown"));
        data.putString("region_name", safe(regionName, "Unknown"));
        if (recipeId != null && !recipeId.isBlank()) data.putString("recipe_id", recipeId);
        if (!data.hasUUID("instance_id")) data.putUUID("instance_id", UUID.randomUUID());
        root.put(ROOT, data);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public static void applyMaker(ItemStack stack, UUID makerId, String makerName) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        CompoundTag data = root.contains(ROOT) ? root.getCompound(ROOT) : new CompoundTag();
        data.putUUID("maker_uuid", makerId);
        data.putString("maker_name", makerName);
        root.put(ROOT, data);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        CompoundTag data = root.contains(ROOT) ? root.getCompound(ROOT) : new CompoundTag();
        String material = data.getString("material");
        tooltip.add(Component.literal("Material: " + (material.isBlank() ? "Iron" : title(material))));
        int quality = data.contains("quality") ? data.getInt("quality") : 1;
        tooltip.add(Component.literal("Quality: " + (quality >= 2 ? "Exceptional" : "Normal")));
        String maker = data.getString("maker_name");
        if (!maker.isBlank()) tooltip.add(Component.literal("Crafted by " + maker).withStyle(ChatFormatting.GRAY));
        String region = data.getString("region_name");
        tooltip.add(Component.literal("Origin: " + (region.isBlank() ? "Unknown" : region)).withStyle(ChatFormatting.GRAY));
    }

    public static String materialId(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        CompoundTag data = root.contains(ROOT) ? root.getCompound(ROOT) : new CompoundTag();
        String material = data.getString("material");
        if (material.isBlank()) material = root.getString("Material"); // legacy QualitySwordItem
        return material.isBlank() ? null : material.replace(' ', '_').toLowerCase(java.util.Locale.ROOT);
    }

    public static String recipeId(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        CompoundTag data = root.contains(ROOT) ? root.getCompound(ROOT) : new CompoundTag();
        String stored = data.getString("recipe_id");
        if (!stored.isBlank() && CraftableRegistry.get(stored) != null) return stored;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        CraftableDef legacy = CraftableRegistry.get(path);
        return legacy == null ? null : legacy.id();
    }

    /** Identity token is compared server-side; it never authorizes an operation by itself. */
    public static String identityToken(ItemStack stack) {
        if (stack.isEmpty()) return "";
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        CompoundTag data = root.contains(ROOT) ? root.getCompound(ROOT) : new CompoundTag();
        if (data.hasUUID("instance_id")) return data.getUUID("instance_id").toString();
        return Integer.toHexString((BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + root + "|"
                + stack.getDamageValue() + "|" + stack.getCount()).hashCode());
    }

    public static boolean isSupportedEquipment(ItemStack stack) {
        String recipe = recipeId(stack);
        if (recipe == null) return false;
        CraftableDef def = CraftableRegistry.get(recipe);
        return def != null && def.recyclable()
                && (def.equipmentType().equals("weapon") || def.equipmentType().equals("armor")
                || def.equipmentType().equals("shield"));
    }

    private static String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String title(String value) {
        String s = value.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
