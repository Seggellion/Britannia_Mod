package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class IronFenceBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<VerticalPart> PART = EnumProperty.create("part", VerticalPart.class);

    // Precise VoxelShapes (2 pixels thick)
    private static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape WEST_SHAPE = Block.box(14, 0, 0, 16, 16, 16);
    private static final VoxelShape EAST_SHAPE = Block.box(0, 0, 0, 2, 16, 16);

    public IronFenceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, VerticalPart.SINGLE));
    }

    // --- Shape & Collision ---

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (facing) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    // --- State Logic ---

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        LevelAccessor level = context.getLevel();
        BlockState state = this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        
        // Calculate vertical part immediately
        return state.setValue(PART, getVerticalPart(state, level, pos));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // Update vertical part if vertical neighbors change
        if (direction.getAxis().isVertical()) {
            return state.setValue(PART, getVerticalPart(state, level, pos));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // Determine if we are Bottom, Middle, Top, or Single
    private VerticalPart getVerticalPart(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        BlockState below = level.getBlockState(pos.below());

        boolean isSameAbove = above.is(this); 
        boolean isSameBelow = below.is(this);

        if (isSameAbove && isSameBelow) return VerticalPart.MIDDLE;
        if (isSameAbove) return VerticalPart.BOTTOM;
        if (isSameBelow) return VerticalPart.TOP;
        return VerticalPart.SINGLE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }
    
    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // Helper Enum
    public enum VerticalPart implements StringRepresentable {
        SINGLE("single"),
        BOTTOM("bottom"),
        MIDDLE("middle"),
        TOP("top");

        private final String name;
        VerticalPart(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }
}