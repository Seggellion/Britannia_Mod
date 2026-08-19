package com.seggellion.britannia_mod.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.farming.CropQualityCalculator;
import com.seggellion.britannia_mod.winery.GrapeVariety;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;

import net.minecraft.ChatFormatting;
import java.util.List;

public class GrapesItem extends Item {
    public static final String GRAPE_VARIETY_KEY = "GrapeVariety";
    /** Matches the built-in fallback in {@code GrapeVarietyManager}. */
    public static final String DEFAULT_VARIETY_ID = "concord_green";

    public GrapesItem(Properties properties) {
        super(properties);
    }

    public static void setVariety(ItemStack stack, String varietyId) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putString(GRAPE_VARIETY_KEY, varietyId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(getGrapeItemName(stack));
    }

// --- Interaction Logic: Extract Seeds ---
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack heldStack = player.getItemInHand(hand);
        if (CropSeedExtractor.shouldExtract(player, hand)) {
            return CropSeedExtractor.tryExtractSeed(level, player, hand, stack -> {
                ItemStack seedStack = new ItemStack(ItemRegistry.GRAPE_SEEDS.get());
                GrapeSeedsItem.setVariety(seedStack, getVariety(stack));
                return seedStack;
            });
        }

        if (!isConcordVariety(getVariety(heldStack))) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("Only Concord grapes are suitable for eating fresh.").withStyle(ChatFormatting.YELLOW), true);
            }
            return InteractionResultHolder.fail(heldStack);
        }

        return super.use(level, player, hand);
    }

    public static String getVariety(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        String varietyId = tag.contains(GRAPE_VARIETY_KEY) ? tag.getString(GRAPE_VARIETY_KEY) : DEFAULT_VARIETY_ID;
        return varietyId == null || varietyId.isBlank() ? DEFAULT_VARIETY_ID : varietyId;
    }

    public static String getFormattedVarietyName(ItemStack stack) {
        return getDisplayNameForVariety(getVariety(stack));
    }

    public static String getDisplayNameForVariety(String varietyId) {
        String safeVarietyId = varietyId == null || varietyId.isBlank() ? DEFAULT_VARIETY_ID : varietyId;
        GrapeVariety variety = GrapeVarietyManager.getVarietyOrNull(safeVarietyId);

        if (variety != null && variety.getFormattedName() != null && !variety.getFormattedName().isBlank()) {
            return variety.getFormattedName();
        }

        String humanized = humanizeVarietyId(safeVarietyId);
        return humanized.isBlank() ? "Unknown" : humanized;
    }

    public static String getGrapeItemName(ItemStack stack) {
        String varietyId = getVariety(stack);
        return getDisplayNameForVariety(varietyId) + " grapes";
    }

    public static String getGrapeSeedItemName(ItemStack stack) {
        String varietyId = getVariety(stack);
        return getDisplayNameForVariety(varietyId) + " grape seeds";
    }

    private static String humanizeVarietyId(String varietyId) {
        String[] words = varietyId.replace('-', '_').split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                name.append(word.substring(1).toLowerCase());
            }
        }
        return name.toString();
    }

// --- NEW: REGION HANDLING ---
    public static void setRegion(ItemStack stack, String regionId) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putString("GrapeRegion", regionId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static String getRegion(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        // Default to "Unknown" or "Britannia" if no tag exists
        return tag.contains("GrapeRegion") ? tag.getString("GrapeRegion") : "Britannia";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Variety: " + getFormattedVarietyName(stack)).withStyle(ChatFormatting.DARK_PURPLE));
        if (CropQualityCalculator.hasQuality(stack)) {
            tooltip.add(CropQualityCalculator.qualityTooltip(stack));
        }
    }

    /**
     * Recognises the whole Concord family rather than three hardcoded spellings. The catalogue ships
     * Concord as coloured slugs - {@code concord_green}, {@code concord_red} - which the old exact
     * matches missed entirely, so the one grape meant to be edible fresh never was. Anchored on
     * {@code concord_} so a variety that merely starts with those letters is not swept in.
     */
    public static boolean isConcordVariety(String varietyId) {
        String safeVarietyId = varietyId == null ? "" : varietyId.trim().toLowerCase(java.util.Locale.ROOT);
        return "concord".equals(safeVarietyId) || safeVarietyId.startsWith("concord_");
    }
}
