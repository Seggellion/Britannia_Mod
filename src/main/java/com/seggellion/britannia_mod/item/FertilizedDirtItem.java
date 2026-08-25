package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.CommunityFarmBlock;
import com.seggellion.britannia_mod.block.CommunityHoedFarmBlock;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class FertilizedDirtItem extends Item {
    public FertilizedDirtItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof CommunityHoedFarmBlock) {
            CommunityHoedFarmBlock.fertilizeCommunityPlot(level, pos, context.getPlayer(), context.getItemInHand());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (state.getBlock() instanceof CommunityFarmBlock) {
            if (!level.isClientSide) {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.literal("Use a farming hoe on this public plot first.").withStyle(ChatFormatting.YELLOW), true);
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (!canConvert(state.getBlock())) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            level.setBlock(pos, BlockRegistry.FARMING_BLOCK.get().defaultBlockState()
                    .setValue(FarmingBlock.HYDRATION, 1), 3);
            if (level.getBlockEntity(pos) instanceof FarmingBlockEntity farmBe) {
                farmBe.setHydration(1);
                farmBe.initializeFertileHarvests();
            }
            level.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
            if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                FarmingSkill.award(serverPlayer, FarmingActionType.TOOL, 1, 1.0f);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean canConvert(Block block) {
        return block == Blocks.DIRT;
    }
}
