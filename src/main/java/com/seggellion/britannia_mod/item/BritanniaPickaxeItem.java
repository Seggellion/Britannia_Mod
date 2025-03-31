package com.seggellion.britannia_mod.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import com.seggellion.britannia_mod.registry.ItemRegistry;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;


public class BritanniaPickaxeItem extends PickaxeItem {
    private static final Logger LOGGER = LogUtils.getLogger();

    public BritanniaPickaxeItem(Tier tier, Item.Properties properties) {
        super(tier, properties);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        // Allow breaking stone and ores in Adventure mode
        return state.is(Blocks.STONE) || state.is(BlockTags.STONE_ORE_REPLACEABLES) || super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        // Custom block-breaking speed for stone-like blocks
        if (state.is(Blocks.STONE) || state.is(BlockTags.STONE_ORE_REPLACEABLES)) {
            return 2.0F; // Adjust speed as needed
        }
        return super.getDestroySpeed(stack, state);
    }

}
