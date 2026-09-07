package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;

public final class BowlWateringService {
    private BowlWateringService() {}

    public static ItemInteractionResult waterFarm(Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        if (!stack.is(ItemRegistry.BOWL_OF_WATER.get()) || player == null || player.isSpectator()
                || player.getItemInHand(hand) != stack || !level.mayInteract(player, pos)
                || !(level.getBlockState(pos).getBlock() instanceof FarmingBlock)
                || !(level.getBlockEntity(pos) instanceof FarmingBlockEntity soil)) return ItemInteractionResult.FAIL;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!soil.mayPlant(player) || soil.expireEmptySoil(level)) return ItemInteractionResult.FAIL;
        if (soil.getHydration() >= FarmingBlockEntity.MAX_HYDRATION) return ItemInteractionResult.CONSUME;
        soil.water(1);
        level.setBlock(pos, level.getBlockState(pos).setValue(FarmingBlock.HYDRATION, soil.getHydration()), 3);
        finish(level, pos, player, hand, stack);
        if (player instanceof ServerPlayer serverPlayer) FarmingSkill.award(serverPlayer, FarmingActionType.TEND, soil.cropTier(), 1.0f);
        return ItemInteractionResult.CONSUME;
    }

    public static void finish(Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(ItemRegistry.EMPTY_BOWL.get())));
        }
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.7f, 1.0f);
    }
}
