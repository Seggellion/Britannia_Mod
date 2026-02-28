package com.seggellion.britannia_mod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class BlankSignHolder extends HorizontalDirectionalBlock {
    // Defines the codec required for 1.21 block serialization
    public static final MapCodec<BlankSignHolder> CODEC = simpleCodec(BlankSignHolder::new);

    // 0-3 = metal_sign_holder 1-4
    // 4-5 = wooden_sign_holder 1-2
    public static final IntegerProperty STYLE = IntegerProperty.create("style", 0, 5);

    public BlankSignHolder(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(STYLE, 0));
    }

    // Overrides the abstract codec method from HorizontalDirectionalBlock
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STYLE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Faces the player when placed
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}