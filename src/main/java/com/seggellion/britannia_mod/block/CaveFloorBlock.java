package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CaveFloorBlock extends Block {

    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 16);
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 4);
    public static final IntegerProperty FLIP = IntegerProperty.create("flip", 0, 1);

    public CaveFloorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(LAYERS, 1)
            .setValue(VARIANT, 0)
            .setValue(FLIP, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS, VARIANT, FLIP);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int height = state.getValue(LAYERS);
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, height, 16.0D);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        RandomSource random = context.getLevel().random;
        return this.defaultBlockState()
            .setValue(LAYERS, 1)
            .setValue(VARIANT, random.nextInt(5))
            .setValue(FLIP, random.nextInt(2));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()
            && stack.is(this.asItem())
            && !player.isSpectator()) {

            int current = state.getValue(LAYERS);
            if (current < 16) {
                level.setBlock(pos, state.setValue(LAYERS, current + 1), 3);
                if (!player.isCreative()) stack.shrink(1);
                return ItemInteractionResult.SUCCESS;
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
