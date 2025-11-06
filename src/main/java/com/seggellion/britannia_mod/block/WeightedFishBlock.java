// WeightedFishBlock.java
package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.FishBlockEntity;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;


public class WeightedFishBlock extends HorizontalFacingBlock implements EntityBlock {
    public WeightedFishBlock(Properties props) {
        super(props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.seggellion.britannia_mod.block.entity.FishBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FishBlockEntity fbe && stack.getItem() instanceof WeightedFishItem fishItem) {
                fbe.setWeight(fishItem.getWeight(stack));
                fbe.setFishType(fishItem.getFishType(stack));
            }
        }
        super.setPlacedBy(level, pos, state, placer, stack);
    }


 @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        boolean isAdventure = !player.getAbilities().mayBuild && !player.isCreative() && !player.isSpectator();
        if (!isAdventure ) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS; // play hand animation
        }

        // Build the drop with preserved data
        ItemStack drop = new ItemStack(this.asItem());
        BlockEntity be = level.getBlockEntity(pos);
        if (drop.getItem() instanceof WeightedFishItem fishItem && be instanceof FishBlockEntity fbe) {
            fishItem.setWeight(drop, fbe.getWeight());
            fishItem.setFishType(drop, fbe.getFishType());
        }

        // Remove the block and drop the item
        level.removeBlock(pos, false);
        net.minecraft.world.level.block.Block.popResource(level, pos, drop);

        return InteractionResult.CONSUME; // we handled it
    }

}
