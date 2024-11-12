// TwoHandedAxeItem.java
package com.seggellion.britannia_mod.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class TwoHandedAxeItem extends TieredItem {
    private static final Logger LOGGER = LogUtils.getLogger();

    public TwoHandedAxeItem(Tier tier, Properties properties) {
        super(tier, properties);
        LOGGER.info("TwoHandedAxeItem initialized.");
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player player) {
        LOGGER.info("Checking if can attack block at {}. Block is log: {}", pos, state.is(BlockTags.LOGS));
        return state.is(BlockTags.LOGS);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        LOGGER.info("Determining destroy speed for block. Block is log: {}", state.is(BlockTags.LOGS));
        return state.is(BlockTags.LOGS) ? 6.0F : super.getDestroySpeed(stack, state);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);
        Player player = context.getPlayer();

        LOGGER.info("useOn called. Player interacting with block at {}. Block is log: {}", pos, state.is(BlockTags.LOGS));

        if (state.is(BlockTags.LOGS) && player != null && !world.isClientSide) {
            world.destroyBlock(pos, true, player);
            LOGGER.info("Log block destroyed successfully.");
            return InteractionResult.SUCCESS;
        }

        LOGGER.info("Block interaction not a log or interaction failed.");
        return super.useOn(context);
    }

    // Helper method to determine if this item should act as a tool for breaking logs
    public boolean isCorrectToolForDrops(BlockState state) {
        boolean canDrop = state.is(BlockTags.LOGS);
        LOGGER.info("Checking if correct tool for block drops. Block is log: {}", canDrop);
        return canDrop;
    }
}
