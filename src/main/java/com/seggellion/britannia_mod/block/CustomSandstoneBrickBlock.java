package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class CustomSandstoneBrickBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty TEXTURE_VARIANT = IntegerProperty.create("texture_variant", 0, 3);
    public static final BooleanProperty TOP_ROW = BooleanProperty.create("top_row");

    public CustomSandstoneBrickBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(TEXTURE_VARIANT, 0)
            .setValue(TOP_ROW, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TEXTURE_VARIANT, TOP_ROW);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        RandomSource random = context.getLevel().getRandom();
        return this.defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(TEXTURE_VARIANT, random.nextInt(TEXTURE_VARIANT.getPossibleValues().size()))
            .setValue(TOP_ROW, rowAbove(context.getLevel().getBlockState(context.getClickedPos().below())));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN) {
            return state.setValue(TOP_ROW, rowAbove(neighborState));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    private boolean rowAbove(BlockState below) {
        return below.is(this) && !below.getValue(TOP_ROW);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ItemRegistry.INTERIOR_DECORATOR_TOOL.get()) && !player.isSpectator()) {
            if (!level.isClientSide()) {
                int currentVariant = state.getValue(TEXTURE_VARIANT);
                int nextVariant = (currentVariant + 1) % 4;
                level.setBlock(pos, state.setValue(TEXTURE_VARIANT, nextVariant), 3);
                level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
