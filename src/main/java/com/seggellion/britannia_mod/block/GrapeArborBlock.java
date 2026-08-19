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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The space a grape arbor fills above its plot.
 *
 * <p>Split out from {@link CornStalkBlock} because the two want opposite things from the same
 * mechanism: a corn stalk is a thin walk-through plant, whereas an arbor is a standing structure a
 * player should collide with. Corn is registered {@code noCollission()}, so sharing that block left
 * the arbor with no collision at all.
 *
 * <p>These blocks draw nothing. The whole arbor is a single model rendered by the plot's block
 * entity renderer, so drawing here would repeat it once per block; their only jobs are to occupy
 * space solidly and to hand interactions back to the plot that owns the plant.
 */
public class GrapeArborBlock extends Block {
    /** Blocks above the plot, 1 at the base of the vine to 3 at the top of the canopy. */
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 1, 3);

    public GrapeArborBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(SEGMENT, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEGMENT);
    }

    /**
     * Solid through the whole block, matching the reference implementation, whose occupancy blocks
     * were full cubes. The arbor's own geometry is mostly broad billboards rather than a narrow
     * stem, so a thin column would let a player walk through leaves and fruit.
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** Invisible: the plot renders the arbor, and a second model here would draw it again. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * Interactions land here rather than on the plot, because the arbor is what a player can see and
     * reach: its canopy stands three blocks up while the plot is buried at the bottom of the plant.
     * Both cutting fruit and turning the arbor are therefore forwarded to the plot that owns it.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(ItemRegistry.INTERIOR_DECORATOR_TOOL.get())) {
            BlockPos anchor = TallCropSupport.findAnchor(level, pos);
            if (anchor != null) {
                ItemInteractionResult rotated = FarmingBlock.rotateCropVisual(level, anchor);
                if (rotated != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                    return rotated;
                }
            }
        }
        if (stack.is(ItemRegistry.SCISSORS.get()) || stack.isEmpty()) {
            return FarmingBlock.harvestTallCropFromSegment(level, pos, player, hand, stack);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        ItemInteractionResult result =
                FarmingBlock.harvestTallCropFromSegment(level, pos, player, InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                ? InteractionResult.PASS
                : InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Breaking any part of the arbor takes the whole plant, leaving no orphaned blocks behind. */
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
