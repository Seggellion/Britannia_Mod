package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BlueTentDeedItem extends DeedItem {
    public BlueTentDeedItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            BlockPos placePos = player.blockPosition().above(); // Place at feet or 1 above
            BlockState state = BlockRegistry.BLUE_TENT.get().defaultBlockState();
            level.setBlock(placePos, state, 3);

            if (!player.getAbilities().instabuild) {
                player.getItemInHand(hand).shrink(1);
            }

            player.displayClientMessage(Component.literal("You place the blue tent."), true);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
