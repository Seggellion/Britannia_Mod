package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.Rotation;

public class ThinWall extends Block {

    /* ─── block‑state properties ─────────────────────────────── */

    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty  CORNER  = BooleanProperty.create("corner");
    public static final BooleanProperty  FILLED  = BooleanProperty.create("filled");

    /** 0 = standard, 1 = alt skin, 2 = alt skin flipped horizontally */
    public static final IntegerProperty STYLE = IntegerProperty.create("style", 0, 2);

    /* ─── voxel shapes ( 16 px ⇒ 1 block ) ───────────────────── */

    private static final VoxelShape NORTH_SHAPE = Block.box(0, 0,     0,   16, 16, 5.33);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 10.66,   16, 16, 16);
    private static final VoxelShape WEST_SHAPE  = Block.box(0, 0,      0,   5.33,16, 16);
    private static final VoxelShape EAST_SHAPE  = Block.box(10.66,0,   0,   16,  16, 16);
    private static final VoxelShape FULL_SHAPE  = Shapes.block();

private static final VoxelShape FILL_E = Block.box( 0, 0, 0, 16, 8, 16);
private static final VoxelShape FILL_W = Block.box( 0, 0, 0, 16, 8, 16); // same, will be rotated
private static final VoxelShape FILL_N = Block.box( 0, 0, 0, 16, 8, 16);
private static final VoxelShape FILL_S = Block.box( 0, 0, 0, 16, 8, 16);

    /* ─── constructor & defaults ─────────────────────────────── */

    public ThinWall(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CORNER, false)
                .setValue(FILLED, false)
                .setValue(STYLE, 0));
    }

    /* ─── placement ──────────────────────────────────────────── */

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face    = ctx.getHorizontalDirection().getOpposite();
        Direction gapSide = face.getOpposite();
            BlockState rear    = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(gapSide));
        boolean    filled  = fillsGap(rear);

        return defaultBlockState()
                .setValue(FACING, face)
                .setValue(CORNER, false)
                .setValue(FILLED, filled)
                .setValue(STYLE, 0);          // always start in standard style
    }

    /* ─── neighbour updates ─────────────────────────────────── */

    @Override
    public BlockState updateShape(BlockState state, Direction fromDir,
                                  BlockState neighbour, LevelAccessor level,
                                  BlockPos pos,   BlockPos neighbourPos) {

        // corner check
        state = state.setValue(CORNER, isPivot(level, pos, state.getValue(FACING)));
        boolean corner = isPivot(level, pos, state.getValue(FACING));

        // filled flag only changes if the neighbour on the gap‑side changes
        if (fromDir == state.getValue(FACING).getOpposite()) {
            state = state.setValue(FILLED, fillsGap(neighbour)); 
        }


        return state;
    }

    private boolean isPivot(LevelAccessor level, BlockPos pos, Direction facing) {
        Direction.Axis axis = facing.getAxis();
        boolean parallel = false, perpendicular = false;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState n = level.getBlockState(pos.relative(dir));
            if (!(n.getBlock() instanceof ThinWall)) continue;

            if (dir.getAxis() == axis) parallel = true;
            else                       perpendicular = true;

            if (parallel && perpendicular) return true;
        }
        return false;
    }

/** rear gap counts as filled only by non-air, non-ThinWall blocks */
private static boolean fillsGap(BlockState rear) {
    if (rear.isAir()) return false;

    if (rear.getBlock() instanceof ThinWall)
        return rear.getValue(ThinWall.FILLED);

    return true;        // any other solid block
}



@Override
public boolean skipRendering(BlockState state,
                             BlockState adjacentState,
                             Direction side) {
    // keep the wall face when the neighbour is any kind of stairs
    if (adjacentState.getBlock() instanceof StairBlock) {
        return false;
    }
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
      
          if (s.getValue(CORNER))
        return FULL_SHAPE;  
      
        return getShape(s, w, p, c);   // outline equals collision
    }

    /* ─── rotation helpers ─────────────────────────────────── */

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    /* ─── state definition ─────────────────────────────────── */

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, CORNER, FILLED, STYLE);
    }
}