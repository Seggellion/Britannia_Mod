package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.material.FluidState;


public class WindowCollisionBlock extends Block {

    private static final VoxelShape COLLISION_SHAPE = Block.box(0, 0, 0, 16, 16, 5.33);

    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);

    // Define the base shape (north-facing)
    private static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 0, 16, 16, 5.33);


    public WindowCollisionBlock() {
        super(BlockBehaviour.Properties.of()
            .noOcclusion()
            .strength(-1.0F, 3600000.0F)
            .noLootTable()
        );
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

   @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }   

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getCollisionShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> rotateShape(NORTH_SHAPE, Rotation.CLOCKWISE_180);
            case WEST  -> rotateShape(NORTH_SHAPE, Rotation.COUNTERCLOCKWISE_90);
            case EAST  -> rotateShape(NORTH_SHAPE, Rotation.CLOCKWISE_90);
            default    -> NORTH_SHAPE;
        };
    }


private static VoxelShape rotateShape(VoxelShape shape, Rotation rotation) {
    VoxelShape[] buffer = new VoxelShape[]{shape, Shapes.empty()};

    for (AABB box : shape.toAabbs()) {
        AABB rotated = switch (rotation) {
            case NONE -> box;
            case CLOCKWISE_90 -> new AABB(
                    1 - box.maxZ, box.minY, box.minX,
                    1 - box.minZ, box.maxY, box.maxX
            );
            case CLOCKWISE_180 -> new AABB(
                    1 - box.maxX, box.minY, 1 - box.maxZ,
                    1 - box.minX, box.maxY, 1 - box.minZ
            );
            case COUNTERCLOCKWISE_90 -> new AABB(
                    box.minZ, box.minY, 1 - box.maxX,
                    box.maxZ, box.maxY, 1 - box.minX
            );
        };

        buffer[1] = Shapes.or(buffer[1], Shapes.create(rotated));
    }

    return buffer[1];
}


    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        // No-op
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        // Optional: auto-remove if orphaned
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return false;
    }

@Override
public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
    // Return the current block state unchanged, effectively making it unbreakable
    return state;
}

@Override
public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
    return false;
}

@Override
public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
    return 0F; // Unbreakable
}

@Override
public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
    return false; // Prevents destruction
}



}
