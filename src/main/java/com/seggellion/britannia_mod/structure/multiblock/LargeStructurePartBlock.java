package com.seggellion.britannia_mod.structure.multiblock;

import com.seggellion.britannia_mod.structure.definition.StructureGeometry;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureTransform;
import com.seggellion.britannia_mod.structure.definition.StructureTransform.HorizontalFacing;
import com.seggellion.britannia_mod.structure.definition.StructureTransform.WorldPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Lightweight occupied cell encoding only facing and its local offset from the anchor. */
public final class LargeStructurePartBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty LOCAL_X = IntegerProperty.create(
            "local_x", 0, StructureGeometry.MAX_LOCAL_X);
    public static final IntegerProperty LOCAL_Y = IntegerProperty.create(
            "local_y", 0, StructureGeometry.MAX_LOCAL_Y);
    public static final IntegerProperty LOCAL_Z = IntegerProperty.create(
            "local_z", 0, StructureGeometry.MAX_LOCAL_Z);
    private static final VoxelShape CELL_SHAPE = Shapes.block();

    public LargeStructurePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LOCAL_X, 0)
                .setValue(LOCAL_Y, 0)
                .setValue(LOCAL_Z, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOCAL_X, LOCAL_Y, LOCAL_Z);
    }

    public BlockState stateFor(Direction facing, LocalOffset offset) {
        if (!facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Part facing must be horizontal");
        }
        if (offset.equals(LocalOffset.ANCHOR)
                || offset.x() < 0 || offset.x() > StructureGeometry.MAX_LOCAL_X
                || offset.y() < 0 || offset.y() > StructureGeometry.MAX_LOCAL_Y
                || offset.z() < 0 || offset.z() > StructureGeometry.MAX_LOCAL_Z) {
            throw new IllegalArgumentException("Offset cannot be encoded as a large-structure part: " + offset);
        }
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(LOCAL_X, offset.x())
                .setValue(LOCAL_Y, offset.y())
                .setValue(LOCAL_Z, offset.z());
    }

    public static LocalOffset localOffset(BlockState state) {
        return new LocalOffset(
                state.getValue(LOCAL_X), state.getValue(LOCAL_Y), state.getValue(LOCAL_Z));
    }

    public static BlockPos anchorPosition(BlockPos partPosition, BlockState state) {
        Direction facing = state.getValue(FACING);
        WorldPosition anchor = StructureTransform.anchorPosition(
                worldPosition(partPosition), horizontalFacing(facing), localOffset(state));
        return new BlockPos(anchor.x(), anchor.y(), anchor.z());
    }

    public static HorizontalFacing horizontalFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> HorizontalFacing.NORTH;
            case EAST -> HorizontalFacing.EAST;
            case SOUTH -> HorizontalFacing.SOUTH;
            case WEST -> HorizontalFacing.WEST;
            default -> throw new IllegalArgumentException("Facing must be horizontal: " + facing);
        };
    }

    public static WorldPosition worldPosition(BlockPos pos) {
        return new WorldPosition(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CELL_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CELL_SHAPE;
    }
}
