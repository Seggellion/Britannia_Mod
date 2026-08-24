package com.seggellion.britannia_mod.block;

import com.mojang.serialization.MapCodec;
import com.seggellion.britannia_mod.entity.LivingSeatEntity;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.world.ItemInteractionResult;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;

public class RotatingStoneWallBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public static final MapCodec<RotatingStoneWallBlock> CODEC = simpleCodec(RotatingStoneWallBlock::new);

    /**
     * The art of {@code stone_wall_window} - this class's only registration - element for element
     * from {@code stone_wall/stone_wall_window.json}: a stepped sill, a jamb either side, and two
     * corbel courses narrowing the top of the light. The light itself - {@code x 3..13} above the
     * sill, still open at {@code x 7..9} where it leaves the cell - is what the player reaches
     * through, so it stays open here. The model hugs the facing edge and the blockstate turns it
     * the house way round ({@code facing=east} bakes {@code "y": 90}), so
     * {@link HorizontalShape#rotateFromNorth} applies as it is - unlike {@code dark_stone_window},
     * which needs its east and west swapped.
     */
    private static final VoxelShape FRAME_NORTH = Shapes.or(
            Block.box(0, 0, 0, 16, 2, 4),       // sill, front course
            Block.box(0, 0, 4, 16, 1, 8),       // sill, back course
            Block.box(0, 1, 4, 3, 2, 8),        // sill step beside the recess, west
            Block.box(13, 1, 4, 16, 2, 8),      // sill step beside the recess, east
            Block.box(0, 2, 0, 3, 16, 8),       // west jamb
            Block.box(3, 12, 0, 5, 16, 8),      // corbel, first course west
            Block.box(5, 14, 0, 7, 16, 8),      // corbel, second course west
            Block.box(11, 12, 0, 13, 16, 8),    // corbel, first course east
            Block.box(9, 14, 0, 11, 16, 8),     // corbel, second course east
            Block.box(13, 2, 0, 16, 16, 8))     // east jamb
        .optimize();

    private static final Map<Direction, VoxelShape> FRAME = turnedFrames();

    private static Map<Direction, VoxelShape> turnedFrames() {
        Map<Direction, VoxelShape> frames = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            frames.put(facing, HorizontalShape.rotateFromNorth(FRAME_NORTH, facing));
        }
        return frames;
    }

    public RotatingStoneWallBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Selection and raycasting trace the art exactly: solid over sill, jambs and corbels, open
     * through the light, so a crosshair through the opening reaches the block beyond it. This used
     * to be the collision slab below, which swallowed every click aimed through the visible light.
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return FRAME.get(state.getValue(FACING));
    }

    /**
     * Bodies and projectiles keep meeting the solid half slab they always met. The light is ten
     * pixels wide - a whisker wider than a player - so carving collision open would let players
     * walk through a window every existing build trusts as wall; the opening stays physical wall
     * until the owner asks otherwise.
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos,
                                        CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> Block.box(0, 0, 0, 16, 16, 8);
            case SOUTH -> Block.box(0, 0, 8, 16, 16, 16);
            case WEST  -> Block.box(0, 0, 0, 8, 16, 16);
            case EAST  -> Block.box(8, 0, 0, 16, 16, 16);
            default    -> Block.box(0, 0, 0, 16, 16, 16);
        };
    }
}
