package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.CommunityFarmBlock;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;

/** Allows a scarecrow to be placed in Adventure mode only over community-farm soil. */
public final class AdventureScarecrowItem extends DecorativeMultiblockItem {
    public AdventureScarecrowItem(DecorativeMultiblockBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean mayPlaceCell(
            UseOnContext context, Player player, BlockPos position, ItemStack stack) {
        if (!isAdventure(player)) {
            return super.mayPlaceCell(context, player, position, stack);
        }
        return isCommunityFarm(context, position.below())
                || isCommunityFarm(context, position.below(2));
    }

    @Override
    protected boolean mayUseSupport(
            UseOnContext context, Player player, BlockPos supportPosition, ItemStack stack) {
        return isAdventure(player) && isCommunityFarm(context, supportPosition)
                || super.mayUseSupport(context, player, supportPosition, stack);
    }

    private static boolean isAdventure(Player player) {
        return player instanceof ServerPlayer serverPlayer
                ? serverPlayer.gameMode.getGameModeForPlayer() == GameType.ADVENTURE
                : !player.getAbilities().mayBuild && !player.isCreative() && !player.isSpectator();
    }

    private static boolean isCommunityFarm(UseOnContext context, BlockPos position) {
        return context.getLevel().getBlockState(position).getBlock() instanceof CommunityFarmBlock;
    }
}
