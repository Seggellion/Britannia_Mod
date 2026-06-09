package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.util.AxeHarvestRules;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.state.BlockState;

public class TwoHandedAxeItem extends AxeItem {

    private static final float TWO_HANDED_AXE_DAMAGE_BONUS = 10.0F;
    private static final float TWO_HANDED_AXE_ATTACK_SPEED = -3.5F;

    public TwoHandedAxeItem(Tier tier, Item.Properties properties) {
        super(tier, properties);
    }

    public static ItemAttributeModifiers createAttributes(Tier tier) {
        return DiggerItem.createAttributes(
                tier,
                TWO_HANDED_AXE_DAMAGE_BONUS,
                TWO_HANDED_AXE_ATTACK_SPEED
        );
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return AxeHarvestRules.isAllowedAxeHarvestBlock(state);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (AxeHarvestRules.isAllowedLeafBlock(state)) return 4.0F;
        if (AxeHarvestRules.isAllowedLogBlock(state)) return 2.0F;

        return 0.0F;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }
}
