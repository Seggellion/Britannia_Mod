package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.util.PickaxeMiningRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.state.BlockState;


public class BritanniaPickaxeItem extends PickaxeItem {
    public BritanniaPickaxeItem(Tier tier, Item.Properties properties) {
        super(tier, properties);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return PickaxeMiningRules.isAllowedMineableBlock(state);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return PickaxeMiningRules.isAllowedMineableBlock(state) ? 2.0F : 0.0F;
    }

}
