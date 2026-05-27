package com.seggellion.britannia_mod.item;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
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
        return state.is(BlockTags.LOGS) || super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return super.canAttackBlock(state, level, pos, player);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (state.is(BlockTags.LOGS)) {
            return 2.0F;
        }

        return super.getDestroySpeed(stack, state);
    }
}