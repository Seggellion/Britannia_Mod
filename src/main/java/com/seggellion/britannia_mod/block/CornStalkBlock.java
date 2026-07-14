package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.farming.TallCropSupport;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CornStalkBlock extends Block {
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 7);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);
    public static final IntegerProperty CROP_KIND = IntegerProperty.create("crop_kind", 0, 1);
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);

    public CornStalkBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AGE, 0)
                .setValue(PART, 0)
                .setValue(CROP_KIND, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, PART, CROP_KIND);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(ItemRegistry.SCISSORS.get()) || stack.isEmpty()) {
            return FarmingBlock.harvestTallCropFromSegment(level, pos, player, hand, stack);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        ItemInteractionResult result = FarmingBlock.harvestTallCropFromSegment(level, pos, player, InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                ? InteractionResult.PASS
                : InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos anchor = TallCropSupport.findAnchor(level, pos);
            if (anchor != null && level.getBlockEntity(anchor) instanceof FarmingBlockEntity farmBe) {
                FarmingBlock.resetAnnualCropState(level, anchor, level.getBlockState(anchor), farmBe);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
