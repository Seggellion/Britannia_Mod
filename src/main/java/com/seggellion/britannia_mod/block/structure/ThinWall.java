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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ThinWall extends Block {

    public static final DirectionProperty FACING  = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty  CORNER   = BooleanProperty.create("corner");
    public static final BooleanProperty  FILLED   = BooleanProperty.create("filled");

    public static final BooleanProperty ALT_TEXTURE = BooleanProperty.create("alt"); 


    /* basic strip shapes (5.33 px thick) */
    private static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 0,     16, 16, 5.33);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 10.66, 16, 16, 16);
    private static final VoxelShape WEST_SHAPE  = Block.box(0, 0, 0,      5.33,16, 16);
    private static final VoxelShape EAST_SHAPE  = Block.box(10.66,0, 0,   16,  16, 16);
    private static final VoxelShape FULL_SHAPE  = Shapes.block();                       // 16³

    public ThinWall(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING,  Direction.NORTH)
            .setValue(CORNER,  false)
            .setValue(FILLED,  false)
            .setValue(ALT_TEXTURE, false)); 
    }

    /* ----- placement ----- */

@Override
public BlockState getStateForPlacement(BlockPlaceContext ctx) {
    Direction face    = ctx.getHorizontalDirection().getOpposite(); 
    Direction gapSide = face.getOpposite();                          
    boolean   filled  = !ctx.getLevel()
                             .getBlockState(ctx.getClickedPos().relative(gapSide))
                             .isAir();
    return defaultBlockState()
            .setValue(FACING, face)
            .setValue(CORNER, false)
            .setValue(FILLED, filled);
}

    /* ----- neighbour updates ----- */


@Override
public BlockState updateShape(BlockState state, Direction fromDir,
                              BlockState neighbour, LevelAccessor level,
                              BlockPos pos,   BlockPos neighbourPos) {

    // keep corner logic
    state = state.setValue(CORNER, isPivot(level, pos, state.getValue(FACING)));

    /* update FILLED only when the neighbour on the GAP side changes */
    if (fromDir == state.getValue(FACING).getOpposite())
        state = state.setValue(FILLED, !neighbour.isAir());

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

    /* ----- visual & collision ----- */

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
        return getShape(s, w, p, c);   // collision == outline
    }

    /* ----- state defs / utility ----- */

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) {
        b.add(FACING, CORNER, FILLED, ALT_TEXTURE);
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
