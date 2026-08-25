package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A full sandstone paving block whose laid direction and surface variation are fixed at placement.
 * Both values live in the block state, so they survive save/reload and never change at render time.
 */
public class SandstonePaverBlock extends Block implements VariantCyclable {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty TEXTURE_VARIANT = IntegerProperty.create("texture_variant", 0, 1);

    public SandstonePaverBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(AXIS, Direction.Axis.X)
            .setValue(TEXTURE_VARIANT, 0));
    }

    @Override
    public IntegerProperty variationProperty() {
        return TEXTURE_VARIANT;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, TEXTURE_VARIANT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return randomPlacementState(context.getLevel().getRandom());
    }

    BlockState randomPlacementState(RandomSource random) {
        return this.defaultBlockState()
            .setValue(AXIS, randomHorizontalAxis(random))
            .setValue(TEXTURE_VARIANT, random.nextInt(TEXTURE_VARIANT.getPossibleValues().size()));
    }

    static Direction.Axis randomHorizontalAxis(RandomSource random) {
        return random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            Direction.Axis rotatedAxis = state.getValue(AXIS) == Direction.Axis.X
                ? Direction.Axis.Z
                : Direction.Axis.X;
            return state.setValue(AXIS, rotatedAxis);
        }
        return state;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return cycleVariation(stack, state, level, pos, player);
    }
}
