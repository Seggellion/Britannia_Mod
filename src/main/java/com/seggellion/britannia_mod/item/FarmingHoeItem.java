package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.CommunityFarmBlock;
import com.seggellion.britannia_mod.block.CommunityHoedFarmBlock;
import com.seggellion.britannia_mod.block.entity.CommunityFarmBlockEntity;
import com.seggellion.britannia_mod.farming.FarmingActionType;
import com.seggellion.britannia_mod.farming.FarmingSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class FarmingHoeItem extends Item {
    public FarmingHoeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof CommunityHoedFarmBlock) {
            if (!level.isClientSide && context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.literal("This public plot has already been hoed.").withStyle(ChatFormatting.YELLOW), true);
            }
            return InteractionResult.SUCCESS;
        }

        if (!(state.getBlock() instanceof CommunityFarmBlock)) {
            return InteractionResult.PASS;
        }

        ItemInteractionResult result = prepareCommunityPlot(level, pos, state, context.getPlayer(), context.getItemInHand(), context.getHand());
        return result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                ? InteractionResult.PASS
                : InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static ItemInteractionResult prepareCommunityPlot(Level level, BlockPos pos, BlockState state, Player player, ItemStack stack, InteractionHand hand) {
        if (!(state.getBlock() instanceof CommunityFarmBlock) || state.getBlock() instanceof CommunityHoedFarmBlock) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            level.setBlock(pos, BlockRegistry.COMMUNITY_HOED_FARM_BLOCK.get().defaultBlockState(), 3);
            if (level.getBlockEntity(pos) instanceof CommunityFarmBlockEntity communityBe) {
                communityBe.markPrepared(level.getGameTime());
            }
            level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (player != null) {
                player.displayClientMessage(Component.literal("Public plot hoed. Apply fertilized dirt within 3 minutes.").withStyle(ChatFormatting.GREEN), true);
                if (!player.getAbilities().instabuild) {
                    stack.hurtAndBreak(1, player, Player.getSlotForHand(hand));
                }
            }
            if (player instanceof ServerPlayer serverPlayer) {
                FarmingSkill.award(serverPlayer, FarmingActionType.TOOL, 1, 1.0f);
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
