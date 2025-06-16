package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.DoubleBedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;

public class DoubleBedBlock extends BedBlock {

    public DoubleBedBlock(BlockBehaviour.Properties props) {
        super(DyeColor.RED, props);   // color first, then properties
    }

    /** place the FOOT half facing the player */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(PART, BedPart.FOOT)
                .setValue(OCCUPIED, false);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DoubleBedBlockEntity(pos, state);
    }

@Override
public RenderShape getRenderShape(BlockState state) {
    // Tell the engine: “draw me like an ordinary block model”
    return RenderShape.MODEL;
}

    /** let vanilla sleeping logic run; cut back to a stub for now */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
