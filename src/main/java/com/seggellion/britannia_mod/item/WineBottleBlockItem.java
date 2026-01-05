package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.component.WineData;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class WineBottleBlockItem extends BlockItem {

    public WineBottleBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    // UPDATED: Added 'String labelColor' to the end
    public static void setWineData(ItemStack stack, String winery, String type, int year, int quality, String region, String labelColor) {
        stack.set(DataComponentRegistry.WINE_DATA, new WineData(winery, type, year, quality, region, labelColor));
    }

    // Helper to read data
    public static WineData getWineData(ItemStack stack) {
        return stack.getOrDefault(DataComponentRegistry.WINE_DATA, WineData.EMPTY);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        if (stack.has(DataComponentRegistry.WINE_DATA)) {
            WineData data = getWineData(stack);

            if (data.wineryName() == null || data.wineryName().isEmpty()) {
                // Optional: You can choose to show nothing, or "Unlabeled"
                // tooltip.add(Component.literal("Unlabeled").withStyle(ChatFormatting.DARK_GRAY));
                return;
            }

            tooltip.add(Component.literal("Vintner: " + data.wineryName()).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("Region: " + data.region()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Varietal: " + data.grapeType()).withStyle(ChatFormatting.DARK_PURPLE));
            tooltip.add(Component.literal("Vintage: " + data.year()).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.literal("Quality: " + data.quality() + "/100").withStyle(ChatFormatting.GREEN));
        } 
    }
}