package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;

/** Atomic ladder item with a placement exception scoped only to Adventure players using this item. */
public final class AdventureLadderItem extends DecorativeMultiblockItem {
    public AdventureLadderItem(DecorativeMultiblockBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean mayPlaceCell(
            UseOnContext context, Player player, BlockPos position, ItemStack stack) {
        boolean adventure = player instanceof ServerPlayer serverPlayer
                ? serverPlayer.gameMode.getGameModeForPlayer() == GameType.ADVENTURE
                : !player.getAbilities().mayBuild && !player.isCreative() && !player.isSpectator();
        return adventure || super.mayPlaceCell(context, player, position, stack);
    }
}
