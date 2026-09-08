package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.CommunityFarmBlock;
import com.seggellion.britannia_mod.block.CommunityHoedFarmBlock;
import com.seggellion.britannia_mod.block.HouseFarmPlotBlock;
import com.seggellion.britannia_mod.block.entity.CommunityFarmBlockEntity;
import com.seggellion.britannia_mod.block.entity.HouseFarmPlotBlockEntity;
import com.seggellion.britannia_mod.farming.FarmingActionType;
import com.seggellion.britannia_mod.farming.FarmingSkill;
import com.seggellion.britannia_mod.quest.action.QuestActionEvents;
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

        if (state.getBlock() instanceof HouseFarmPlotBlock) {
            return clearHouseFarmPlot(context);
        }

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

    private static InteractionResult clearHouseFarmPlot(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!(level.getBlockEntity(pos) instanceof HouseFarmPlotBlockEntity plot)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)
                || player == null
                || !HouseFarmPlotBlock.mayManagePlot(serverLevel, pos, player)) {
            if (player != null) {
                player.displayClientMessage(Component.literal("You may only clear a house farm plot you own.").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }

        plot.initializeDefaultPoppy(serverLevel);
        if (!plot.clearPersistentAssignment(serverLevel)) {
            player.displayClientMessage(Component.literal("This house farm plot is already clear.").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.SUCCESS;
        }

        level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().hurtAndBreak(1, player, Player.getSlotForHand(context.getHand()));
        }
        if (player instanceof ServerPlayer serverPlayer) {
            FarmingSkill.award(serverPlayer, FarmingActionType.TOOL, 1, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }

    public static ItemInteractionResult prepareCommunityPlot(Level level, BlockPos pos, BlockState state, Player player, ItemStack stack, InteractionHand hand) {
        if (!(state.getBlock() instanceof CommunityFarmBlock) || state.getBlock() instanceof CommunityHoedFarmBlock) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            boolean hoed = level.setBlock(pos, BlockRegistry.COMMUNITY_HOED_FARM_BLOCK.get().defaultBlockState(), 3);
            if (level.getBlockEntity(pos) instanceof CommunityFarmBlockEntity communityBe) {
                communityBe.markPrepared(level.getGameTime());
            }
            level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (player != null) {
                // M8 items 8 and 10. Same message, now translatable, and the countdown is derived
                // from the window the block entity actually enforces rather than a "3 minutes"
                // written into the sentence -- the two had no way of staying in step.
                player.displayClientMessage(Component.translatable(
                                "message.britannia_mod.quest.plot.hoed_countdown",
                                CommunityFarmBlockEntity.PREPARED_EXPIRY_TICKS / 20L)
                        .withStyle(ChatFormatting.GREEN), true);
                if (!player.getAbilities().instabuild) {
                    stack.hurtAndBreak(1, player, Player.getSlotForHand(hand));
                }
            }
            if (player instanceof ServerPlayer serverPlayer) {
                FarmingSkill.award(serverPlayer, FarmingActionType.TOOL, 1, 1.0f);
            }
            // Rowan questline M5 (protocol section 2.1): reported only when the hoed block is
            // really in the world. An already-hoed plot, a private plot and a house plot all
            // returned long before this method, so none of them can report a hoeing.
            if (hoed) {
                QuestActionEvents.plotHoe(player, level, pos);
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
