package com.seggellion.britannia_mod.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shared facing and deliberately authored collision for small decorative props. */
public class DecorativePropBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    private final boolean collidable;

    public DecorativePropBlock(Properties properties, VoxelShape northShape, boolean collidable) {
        super(properties);
        this.collidable = collidable;
        this.shapes.put(Direction.NORTH, northShape);
        this.shapes.put(Direction.EAST, rotateY(northShape, 1));
        this.shapes.put(Direction.SOUTH, rotateY(northShape, 2));
        this.shapes.put(Direction.WEST, rotateY(northShape, 3));
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.getOrDefault(state.getValue(FACING), shapes.get(Direction.NORTH));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return collidable ? getShape(state, level, pos, context) : Shapes.empty();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    private static VoxelShape rotateY(VoxelShape shape, int quarterTurnsClockwise) {
        VoxelShape rotated = shape;
        for (int turn = 0; turn < quarterTurnsClockwise; turn++) {
            VoxelShape next = Shapes.empty();
            for (AABB box : rotated.toAabbs()) {
                next = Shapes.or(next, Shapes.create(new AABB(
                        1.0D - box.maxZ,
                        box.minY,
                        box.minX,
                        1.0D - box.minZ,
                        box.maxY,
                        box.maxX
                )));
            }
            rotated = next;
        }
        return rotated;
    }
}
