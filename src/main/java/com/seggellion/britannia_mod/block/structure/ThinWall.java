package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class ThinWall extends Block {

    /* ----------  properties ---------- */

    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty  CORNER = BooleanProperty.create("corner");

    /* ----------  voxel shapes ---------- */

    private static final VoxelShape NORTH_SHAPE = Block.box(0,     0, 0,     16, 16, 5.33);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0,     0, 10.66, 16, 16, 16);
    private static final VoxelShape WEST_SHAPE  = Block.box(0,     0, 0,     5.33,16, 16);
    private static final VoxelShape EAST_SHAPE  = Block.box(10.66, 0, 0,     16, 16, 16);

    /* ----------  ctor / default state ---------- */

    public ThinWall(BlockBehaviour.Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CORNER,  false));
    }


/*  keep imports / shapes / constructor as you already have  */
    /* placement: always start as non-corner */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState()
               .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
               .setValue(CORNER, false);
    }

    /* re-evaluate corner status whenever a neighbour changes */
    @Override
    public BlockState updateShape(BlockState state, Direction fromDir,
                                  BlockState neighbour, LevelAccessor level,
                                  BlockPos pos,   BlockPos neighbourPos)
    {
        boolean pivot = isPivot(level, pos, state.getValue(FACING));
        return state.setValue(CORNER, pivot);
    }

    /** pivot ⇢ has ≥1 parallel-axis ThinWall **and** ≥1 perpendicular-axis ThinWall */
    private boolean isPivot(LevelAccessor level, BlockPos pos, Direction facing)
    {
        Direction.Axis axis = facing.getAxis();

        boolean parallel     = false;   // ThinWall on same axis (E/W for E-W wall)
        boolean perpendicular= false;   // ThinWall on other axis (N/S for E-W wall)

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState n = level.getBlockState(pos.relative(dir));
            if (!(n.getBlock() instanceof ThinWall)) continue;

            if (dir.getAxis() == axis)  parallel      = true;
            else                        perpendicular = true;

            if (parallel && perpendicular) return true;  // early exit
        }
        return false;
    }

/*  remainder of class (voxel shape, stateDefinition, rotate/mirror) unchanged  */


    /* ----------  visual / collision ---------- */

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level,
                               BlockPos pos, CollisionContext ctx)
    {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST  -> WEST_SHAPE;
            case EAST  -> EAST_SHAPE;
            default    -> NORTH_SHAPE;
        };
    }

    /* ----------  misc ---------- */

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) {
        b.add(FACING, CORNER);
    }

    @Override
    public BlockState rotate(BlockState s, net.minecraft.world.level.block.Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, net.minecraft.world.level.block.Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }
}