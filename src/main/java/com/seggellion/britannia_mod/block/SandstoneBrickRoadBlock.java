package com.seggellion.britannia_mod.block;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class SandstoneBrickRoadBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty TEXTURE_VARIANT = IntegerProperty.create("texture_variant", 0, 3);

    public SandstoneBrickRoadBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(TEXTURE_VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TEXTURE_VARIANT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        RandomSource random = context.getLevel().getRandom();
        Direction randomFacing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int randomVariant = random.nextInt(4);

        return this.defaultBlockState()
            .setValue(FACING, randomFacing)
            .setValue(TEXTURE_VARIANT, randomVariant);
    }
}
