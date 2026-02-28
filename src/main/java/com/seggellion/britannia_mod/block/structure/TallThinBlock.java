package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class TallThinBlock extends Block {

    /* ─── block‑state properties ─────────────────────────────── */

    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty   CORNER = BooleanProperty.create("corner");
    public static final BooleanProperty   FILLED = BooleanProperty.create("filled");
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** 0 = standard, 1 = alt skin, 2 = alt skin flipped horizontally */
    public static final IntegerProperty STYLE = IntegerProperty.create("style", 0, 2);

    /* ─── voxel shapes ( 16 px per block, stacked ) ──────────── */

    private static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 0, 16, 16, 5.33);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 10.66, 16, 16, 16);
    private static final VoxelShape WEST_SHAPE  = Block.box(0, 0, 0, 5.33, 16, 16);
    private static final VoxelShape EAST_SHAPE  = Block.box(10.66, 0, 0, 16, 16, 16);
    private static final VoxelShape FULL_SHAPE  = Block.box(0, 0, 0, 16, 16, 16);

    /* ─── constructor & defaults ─────────────────────────────── */

    public TallThinBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CORNER, false)
                .setValue(FILLED, false)
                .setValue(STYLE, 0)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    /* ─── placement logic ────────────────────────────────────── */

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();

        // Ensure there is room for the top half (y + 1)
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            Direction face    = ctx.getHorizontalDirection().getOpposite();
            Direction gapSide = face.getOpposite();
            BlockState rear   = level.getBlockState(pos.relative(gapSide));
            boolean    filled = fillsGap(rear);

            return defaultBlockState()
                    .setValue(FACING, face)
                    .setValue(CORNER, false)
                    .setValue(FILLED, filled)
                    .setValue(STYLE, 0)
                    .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null; // Cancels placement if no room
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        // Automatically place the top half when the bottom half is placed
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    /* ─── breaking & link logic ──────────────────────────────── */

    @Override
    public BlockState updateShape(BlockState state, Direction fromDir, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        DoubleBlockHalf half = state.getValue(HALF);

        // 1. If the other half of the block is broken, break this half too
        if (fromDir.getAxis() == Direction.Axis.Y && half == DoubleBlockHalf.LOWER == (fromDir == Direction.UP)) {
            return neighbour.is(this) && neighbour.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }

        // 2. Corner check
        state = state.setValue(CORNER, isPivot(level, pos, state.getValue(FACING)));

        // 3. Filled flag (only updates from gap-side neighbour)
        if (fromDir == state.getValue(FACING).getOpposite()) {
            state = state.setValue(FILLED, fillsGap(neighbour)); 
        }

        return super.updateShape(state, fromDir, neighbour, level, pos, neighbourPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Drop logic: if breaking the top half, force destroy the bottom half to handle drops properly
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState blockstate = level.getBlockState(pos.below());
            if (blockstate.is(this) && blockstate.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.destroyBlock(pos.below(), true, player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /* ─── connections & shapes ───────────────────────────────── */

    private boolean isPivot(LevelAccessor level, BlockPos pos, Direction facing) {
        Direction.Axis axis = facing.getAxis();
        boolean parallel = false, perpendicular = false;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState n = level.getBlockState(pos.relative(dir));
            if (!(n.getBlock() instanceof TallThinBlock)) continue;

            if (dir.getAxis() == axis) parallel = true;
            else                       perpendicular = true;

            if (parallel && perpendicular) return true;
        }
        return false;
    }

    private static boolean fillsGap(BlockState rear) {
        if (rear.isAir()) return false;
        if (rear.getBlock() instanceof TallThinBlock) return rear.getValue(FILLED);
        return true; 
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        if (adjacentState.getBlock() instanceof StairBlock) return false;
        return super.skipRendering(state, adjacentState, side);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter w, BlockPos p, CollisionContext c) {
        return s.getValue(FILLED) ? FULL_SHAPE : switch (s.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST  -> WEST_SHAPE;
            case EAST  -> EAST_SHAPE;
            default    -> NORTH_SHAPE;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter w, BlockPos p, CollisionContext c) {
        if (s.getValue(CORNER)) return FULL_SHAPE;  
        return getShape(s, w, p, c); 
    }

    /* ─── rotation & state ───────────────────────────────────── */

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, CORNER, FILLED, STYLE, HALF);
    }
}