package com.seggellion.britannia_mod.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class PaintingItem extends BlockItem {
    public enum PaintingSize { SMALL, MEDIUM, LARGE }

    private final PaintingSize size;

    public PaintingItem(Block block, PaintingSize size, Item.Properties props) {
        super(block, props);
        this.size = size;
    }

    public PaintingSize getSize() {
        return size;
    }


    @Override
    public void appendHoverText(ItemStack stack,
                                Item.TooltipContext context,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);        // Tiny, non-intrusive size hint. Remove this override if you don’t want a tooltip.
        tooltip.add(Component.translatable("tooltip.britannia_mod.painting.size." + size.name().toLowerCase()));
    }
}
