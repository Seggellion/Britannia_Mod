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
import net.minecraft.sounds.SoundSource;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.winery.GrapeVariety;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;

import net.minecraft.sounds.SoundEvents;

import net.minecraft.ChatFormatting;
import java.util.List;

public class GrapesItem extends Item {
    public static final String GRAPE_VARIETY_KEY = "GrapeVariety";
    public static final String DEFAULT_VARIETY_ID = "wild_grape";

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

        // Check if player is holding Shift (Sneaking)
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                // 1. Get the variety from the current Grape item
                String variety = getVariety(heldStack);

                // 2. Create the Seed ItemStack
                // REPLACE 'ItemRegistry.GRAPE_SEEDS.get()' with your actual Registry object for the seeds!
                ItemStack seedStack = new ItemStack(ItemRegistry.GRAPE_SEEDS.get());

                // 3. Transfer the variety data to the new seed
                GrapeSeedsItem.setVariety(seedStack, variety);

                // 4. Give the seed to the player (handle full inventory by dropping)
                if (!player.getInventory().add(seedStack)) {
                    player.drop(seedStack, false);
                }

                // 5. Play a sound effect (Pumpkin Carve or Crop Break sounds fit best)
                level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                        SoundEvents.PUMPKIN_CARVE, SoundSource.PLAYERS, 1.0F, 1.0F);

                // 6. Consume the grape (unless in Creative mode)
                if (!player.getAbilities().instabuild) {
                    heldStack.shrink(1);
                }
            }

            // Return success to prevent default behavior (like eating) while shifting
            return InteractionResultHolder.sidedSuccess(heldStack, level.isClientSide());
        }

        // If NOT shifting, allow default behavior (e.g., eating the grape)
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
        String displayName = DEFAULT_VARIETY_ID.equals(varietyId) ? "Wild" : getDisplayNameForVariety(varietyId);
        return displayName + " grapes";
    }

    public static String getGrapeSeedItemName(ItemStack stack) {
        String varietyId = getVariety(stack);
        String displayName = DEFAULT_VARIETY_ID.equals(varietyId) ? "Wild" : getDisplayNameForVariety(varietyId);
        return displayName + " grape seeds";
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
    }
}
