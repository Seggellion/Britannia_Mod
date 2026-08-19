package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.registry.BlockRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * The invisible half of an oversized window: one cell of frame and glass that a
 * {@link MultiCellWindowBlock} draws but cannot collide with itself.
 *
 * <p>It renders nothing and drops nothing. Its shape is the same edge slab a {@link ThinWall}
 * window gives its own cell, trimmed to the {@link WindowCollisionSpan} of the cell it stands in,
 * and rotated with {@link #FACING} through {@link HorizontalShape} - the same rotation helper every
 * other wall family here uses, so a helper can never drift out of step with the window it backs.
 *
 * <p>It is selectable and breakable on purpose. The player sees window art in this cell, so mining
 * it has to do what mining the window does: {@link #playerWillDestroy} forwards the break to the
 * owning window, which then clears its remaining helpers. A helper that has lost its owner - to an
 * explosion, a {@code /setblock}, or a world edit - breaks on its own rather than standing there as
 * a permanent invisible wall, which is what the previous unbreakable version left behind.
 */
public class WindowCollisionBlock extends Block {

    public static final DirectionProperty FACING =
        DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static final EnumProperty<WindowCollisionSpan> SPAN =
        EnumProperty.create("span", WindowCollisionSpan.class);

    /**
     * Depth of the slab, matching {@link ThinWall}'s own window cells exactly. The art is 6 deep;
     * the extra two thirds of a pixel are left off so a window's own cell and its helper cells
     * present one flat face to walk into instead of a step.
     */
    private static final double DEPTH = 5.33D;

    private static final Map<WindowCollisionSpan, VoxelShape> NORTH_SHAPES =
        new EnumMap<>(WindowCollisionSpan.class);

    static {
        for (WindowCollisionSpan span : WindowCollisionSpan.values()) {
            NORTH_SHAPES.put(span, Block.box(0.0D, span.minY(), 0.0D, 16.0D, span.maxY(), DEPTH));
        }
    }

    public WindowCollisionBlock() {
        super(BlockBehaviour.Properties.of()
            .noOcclusion()
            .strength(1.0F)
            .sound(SoundType.WOOD)
            .noLootTable()
        );
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(SPAN, WindowCollisionSpan.FULL));
    }

    /** The helper state a window wants in a given cell. */
    public static BlockState stateFor(Direction facing, WindowCollisionSpan span) {
        return BlockRegistry.WINDOW_COLLISION.get()
            .defaultBlockState()
            .setValue(FACING, facing)
            .setValue(SPAN, span);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SPAN);
    }

    /* ─── shape ──────────────────────────────────────────────── */

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return HorizontalShape.rotateFromNorth(
            NORTH_SHAPES.get(state.getValue(SPAN)), state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /* ─── breaking ───────────────────────────────────────────── */

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos owner = MultiCellWindowBlock.ownerOf(level, pos, null);
            if (owner != null) {
                // Break the window itself, which clears this helper and its siblings on the way out.
                level.destroyBlock(owner, !player.isCreative(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** A helper never occupies a cell a player could build into while its window still stands. */
    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return false;
    }
}
