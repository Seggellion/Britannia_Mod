package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CommunityHoedFarmBlock extends CommunityFarmBlock {
    public CommunityHoedFarmBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(PREPARED, true));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(ItemRegistry.FERTILIZED_DIRT.get())) {
            return fertilizeCommunityPlot(level, pos, player, stack);
        }

        if (stack.is(ItemRegistry.FARMING_HOE.get())) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("This public plot has already been hoed.").withStyle(ChatFormatting.YELLOW), true);
            }
            return ItemInteractionResult.SUCCESS;
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    public static ItemInteractionResult fertilizeCommunityPlot(Level level, BlockPos pos, Player player, ItemStack stack) {
        return com.seggellion.britannia_mod.farming.FertilizedSoilService.apply(level, pos, player, stack);
    }
}
