package com.seggellion.britannia_mod.grabbyhands.block;

import com.mojang.serialization.MapCodec;
import com.seggellion.britannia_mod.grabbyhands.blockentity.GrabbyPlacedItemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A small physical object in the world whose entire identity is the item it holds.
 *
 * <p>Most Grabby content does not need this. Chairs, tables, lamps and wine bottles are already real
 * blocks with their own behaviour, and Grabby Hands moves them through their own native paths. This
 * block exists for the other case: an item that has no block form at all, which a player wants to
 * leave lying on a table.
 *
 * <p>It is deliberately inert. It has no use behaviour, because the thing it represents is an item
 * lying on a surface — inventing an interaction would be inventing gameplay this epic did not ask
 * for. Picking it up uses the ordinary Grabby gesture like everything else.
 *
 * <h2>Drop behaviour</h2>
 *
 * <p>{@link #onRemove} drops the payload, so an explosion or an admin in Creative cannot make a
 * player's item silently vanish. A Grabby pickup detaches the payload first (see
 * {@code GrabbyPayloadHolder}), so the same code path cannot hand out the item twice.
 */
public class GrabbyPlacedItemBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<GrabbyPlacedItemBlock> CODEC = simpleCodec(GrabbyPlacedItemBlock::new);

    /** Low and small: this is an object resting on a surface, not a block of its own. */
    private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 3.0, 12.0);

    public GrabbyPlacedItemBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Needs something sturdy underneath, the same rule {@code WineBottleBlock} already uses.
     *
     * <p>Keeping the rule identical to the existing small-object precedent means players do not have
     * to learn two different notions of "will this stay put".
     */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    /** The block entity renderer draws the stored item; there is no baked model to show. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GrabbyPlacedItemBlockEntity(pos, state);
    }

    /**
     * The portable form of this object is simply the item it holds.
     *
     * <p>Grabby Hands captures through the block's clone-stack path, so returning the payload here is
     * all that is needed for pickup to preserve the item exactly — including any components this
     * class knows nothing about.
     */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof GrabbyPlacedItemBlockEntity host
                ? host.grabbyPayload().copy()
                : ItemStack.EMPTY;
    }

    @Override
    public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!oldState.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof GrabbyPlacedItemBlockEntity host
                && !host.grabbyPayload().isEmpty()) {
            // Something other than a Grabby transaction is destroying this. A Grabby pickup would have
            // detached the payload already, so reaching here with a payload means the item is about to
            // be lost unless it is dropped.
            Block.popResource(level, pos, host.grabbyPayload().copy());
            host.detachGrabbyPayload();
        }
        super.onRemove(oldState, level, pos, newState, isMoving);
    }
}
