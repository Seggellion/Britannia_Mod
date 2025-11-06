package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.item.PitcherItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

public class WaterTroughBlock extends Block {
      public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Two-block-wide shapes (using 0–32 in the extended axis)
    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 11.0, 16.0),
            Block.box(-16.0, 0.0, 0.0, 0.0, 11.0, 16.0)   // extends west
    );

    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 11.0, 16.0),
            Block.box(16.0, 0.0, 0.0, 32.0, 11.0, 16.0)   // extends east
    );

    private static final VoxelShape SHAPE_WEST = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 11.0, 16.0),
            Block.box(0.0, 0.0, 16.0, 16.0, 11.0, 32.0)   // extends north
    );

    private static final VoxelShape SHAPE_EAST = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 11.0, 16.0),
            Block.box(0.0, 0.0, -16.0, 16.0, 11.0, 0.0)   // extends south
    );


    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.NORTH, SHAPE_NORTH);
        SHAPES.put(Direction.SOUTH, SHAPE_SOUTH);
        SHAPES.put(Direction.EAST, SHAPE_EAST);
        SHAPES.put(Direction.WEST, SHAPE_WEST);
    }

    public WaterTroughBlock(BlockBehaviour.Properties properties) {
        super(properties
                .strength(2.0f)
                .sound(SoundType.WOOD)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPE_NORTH);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof PitcherItem pitcher) {
            if (!level.isClientSide()) {
                pitcher.fillWithWater(stack);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BUCKET_FILL,
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    private static VoxelShape rotateY(VoxelShape shape, int quarterTurnsCW) {
        quarterTurnsCW = ((quarterTurnsCW % 4) + 4) % 4;
        VoxelShape rotated = shape;
        for (int i = 0; i < quarterTurnsCW; i++) rotated = rotateY90CW(rotated);
        return rotated;
    }

    private static VoxelShape rotateY90CW(VoxelShape shape) {
        var boxes = shape.toAabbs();
        VoxelShape out = Shapes.empty();
        for (var b : boxes) {
            double nMinX = 16.0 - b.maxZ;
            double nMaxX = 16.0 - b.minZ;
            double nMinZ = b.minX;
            double nMaxZ = b.maxX;
            out = Shapes.or(out, Shapes.box(nMinX / 16.0, b.minY / 16.0, nMinZ / 16.0, nMaxX / 16.0, b.maxY / 16.0, nMaxZ / 16.0));
        }
        return out.optimize();
    }
}
