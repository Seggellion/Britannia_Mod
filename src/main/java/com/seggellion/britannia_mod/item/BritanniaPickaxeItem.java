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

/*
    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player) {
            LOGGER.info("Block is being mined!!");
            // Handle custom ore drops
            if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) {
                ItemStack purityOreStack = new ItemStack(ItemRegistry.PURITY_ORE_ITEM.get());
                PurityOreItem oreItem = (PurityOreItem) purityOreStack.getItem();

                // Assign custom purity value
                int purity = 1 + level.random.nextInt(5); // Random purity between 1 and 5
                oreItem.setPurity(purityOreStack, purity);
                LOGGER.info("oreItem:", oreItem);
                // Drop the item using Minecraft's drop mechanism
                Block.popResource(level, pos, purityOreStack);
            }
        }
        return super.mineBlock(stack, level, state, pos, entity);
    }
*/
}
