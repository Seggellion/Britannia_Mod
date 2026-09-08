package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.CommunityFarmBlockEntity;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.farming.FarmingActionType;
import com.seggellion.britannia_mod.farming.FarmingSkill;
import com.seggellion.britannia_mod.quest.action.QuestActionEvents;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
        if (!level.isClientSide) {
            // M9 item 3. Fertilized dirt is four gathering steps and a mixing step of work, and it
            // used to be swallowed by a plot whose hoeing had already run out: the block-entity
            // tick reverts an expired plot, but between the deadline and that tick the plot still
            // looked hoed and still took the dirt. Checked here, before anything is consumed, and
            // the plot is reverted on the spot rather than a tick later.
            if (level.getBlockEntity(pos) instanceof CommunityFarmBlockEntity communityBe
                    && communityBe.preparationExpired(level)) {
                communityBe.clearPreparedExpiry();
                level.setBlock(pos, BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(), 3);
                if (player != null) {
                    player.displayClientMessage(
                            Component.translatable(QuestScreenText.PLOT_EXPIRED)
                                    .withStyle(ChatFormatting.YELLOW), true);
                }
                return ItemInteractionResult.SUCCESS;
            }

            long seedDeadline = level.getGameTime() + FarmingBlockEntity.COMMUNITY_SEED_WINDOW_TICKS;
            boolean fertilized = level.setBlock(pos, BlockRegistry.FARMING_BLOCK.get().defaultBlockState()
                    .setValue(FarmingBlock.HYDRATION, 1)
                    .setValue(FarmingBlock.HAS_SEEDS, false), 3);
            if (level.getBlockEntity(pos) instanceof FarmingBlockEntity farmBe) {
                farmBe.setHydration(1);
                farmBe.startCommunitySeedWindow(seedDeadline);
                farmBe.initializeFertileHarvests();
                // M9 item 1: book the window's own deterministic clock, so it no longer depends on
                // randomTickSpeed and expires at the second Rails also expires it.
                FarmingBlock.scheduleCommunitySeedWindow(level, pos, seedDeadline);
            }
            level.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (player != null) {
                // M8 items 8 and 10: translatable, with the countdown read from the window the
                // block entity enforces instead of a number written into the sentence.
                player.displayClientMessage(Component.translatable(
                                "message.britannia_mod.quest.plot.fertilized_countdown",
                                FarmingBlockEntity.COMMUNITY_SEED_WINDOW_TICKS / 20L)
                        .withStyle(ChatFormatting.GREEN), true);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            if (player instanceof ServerPlayer serverPlayer) {
                FarmingSkill.award(serverPlayer, FarmingActionType.TOOL, 1, 1.0f);
            }
            // Rowan questline M5 (protocol section 2.1): reported only once the fertilized plot
            // block is really in the world and the fertilized dirt has been paid for. Reaching
            // this method at all already required a Fertilized Dirt stack on an already-hoed
            // public plot -- every other item falls through to the block's ordinary use.
            if (fertilized) {
                QuestActionEvents.plotFertilize(player, level, pos);
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
