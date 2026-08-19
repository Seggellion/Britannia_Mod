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

    /**
     * The slab this family's art draws, measured off {@code dark_stone_window_bottom.json} and
     * {@code _top.json}: a five pixel deep wall panel with a six pixel wide light in the middle.
     * The light is too narrow for a player to fit through, so collision keeps the panel solid
     * rather than modelling the opening - a simplification, but the only one here, and it is the
     * shape the block already presented.
     */
    private static final VoxelShape PANEL_NORTH = Block.box(0, 0, 0, 16, 16, 5);
    private static final VoxelShape PANEL_SOUTH = Block.box(0, 0, 11, 16, 16, 16);
    private static final VoxelShape PANEL_WEST  = Block.box(0, 0, 0, 5, 16, 16);
    private static final VoxelShape PANEL_EAST  = Block.box(11, 0, 0, 16, 16, 16);
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

    /**
     * Which edge of the block this family's art actually lands on for a given {@link #FACING}.
     *
     * <p>It is not the facing edge, and it is not consistently the opposite one either.
     * {@code dark_stone_window} - the only block built on this class - is authored on the far edge
     * from its facing (the panel sits at {@code z 11..16} in the unrotated model), and its
     * blockstate turns east and west the opposite way round from every other wall family here
     * ({@code facing=east} uses {@code "y": 270}, {@code facing=west} uses {@code "y": 90}). The two
     * inversions cancel on the X axis and compound on the Z axis, so the art comes out on the far
     * edge for north and south and on the near edge for east and west.
     *
     * <p>Collision used to assume the near edge for all four, which put the panel a whole block
     * depth away from the art on the north and south facings: the player walked through the window
     * they could see and stopped against nothing. This mapping is what the resources actually draw,
     * and {@code WindowCollisionContractTest} re-derives it from the blockstate and model JSON so it
     * cannot silently drift. Re-authoring the art to the house convention - panel on the near edge,
     * {@code facing=east} on {@code "y": 90} - would collapse this to the identity, but that moves
     * the visible wall in every build that already uses the block, so it is left as an art decision.
     */
    private static VoxelShape panelFor(Direction facing) {
        return switch (facing) {
            case NORTH -> PANEL_SOUTH;
            case SOUTH -> PANEL_NORTH;
            case EAST  -> PANEL_EAST;
            case WEST  -> PANEL_WEST;
            default    -> PANEL_SOUTH;
        };
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter w, BlockPos p, CollisionContext c) {
        return s.getValue(FILLED) ? FULL_SHAPE : panelFor(s.getValue(FACING));
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