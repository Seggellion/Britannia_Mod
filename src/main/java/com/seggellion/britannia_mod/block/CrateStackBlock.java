package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CrateStackLayout;
import com.seggellion.britannia_mod.crate.CrateStackShapes;
import com.seggellion.britannia_mod.crate.CrateStackSlice;
import com.seggellion.britannia_mod.crate.CrateStackTargetResolver;
import com.seggellion.britannia_mod.crate.LogicalCrateMenuProvider;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The world representation of a compact crate column.
 *
 * <h2>Root and continuation</h2>
 *
 * <p>A column claims one cell per sixteen voxels of packed crates, up to four. {@code PART} says
 * which of those a cell is, and only {@code PART = 0} carries a {@link CrateStackBlockEntity}. Every
 * other cell is occupancy and geometry: it holds the space against other blocks, collides and
 * selects where its share of the crates actually is, and finds its root by counting back down its own
 * {@code PART}. That is the whole of the relationship — a continuation cell owns nothing, decides
 * nothing, and can be rebuilt from the root at any time.
 *
 * <h2>Not a block players own</h2>
 *
 * <p>No item, no recipe, no creative-tab entry, and no place in any structure. Players keep holding
 * {@code small_crate} and {@code medium_crate}; this is only what a position becomes once two of them
 * occupy it.
 */
public class CrateStackBlock extends Block implements EntityBlock {

    /** Which cell of its column this is, counting up from the root at zero. */
    public static final IntegerProperty PART =
            IntegerProperty.create("part", 0, CrateStackLayout.MAX_CELLS - 1);

    /**
     * Guards the world mutations that reshape a column.
     *
     * <p>Same idea as {@code DecorativeMultiblockBlock}'s: reconciling continuation cells means
     * removing and adding blocks that belong to a column that is not being destroyed, and anything
     * that reacts to a cell disappearing has to be able to tell that apart from a player breaking
     * one.
     */
    private static final ThreadLocal<Boolean> MUTATING = ThreadLocal.withInitial(() -> false);

    public CrateStackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    public static boolean isRoot(BlockState state) {
        return state.getBlock() instanceof CrateStackBlock && state.getValue(PART) == 0;
    }

    /** The cell that owns this one, which for a root is itself. */
    public static BlockPos rootOf(BlockPos pos, BlockState state) {
        return state.getBlock() instanceof CrateStackBlock
                ? pos.below(state.getValue(PART))
                : pos;
    }

    /** Runs a column reshuffle with the guard raised, so cell removal is not read as destruction. */
    public static <T> T duringMutation(Supplier<T> mutation) {
        boolean previous = MUTATING.get();
        MUTATING.set(true);
        try {
            return mutation.get();
        } finally {
            MUTATING.set(previous);
        }
    }

    public static boolean isMutating() {
        return MUTATING.get();
    }

    /**
     * Only the root owns state.
     *
     * <p>The single most important line in this class. A continuation cell that produced its own
     * block entity would be a second inventory in a column that is supposed to have exactly one
     * authority, and nothing downstream would be able to tell which was real.
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isRoot(state) ? new CrateStackBlockEntity(pos, state) : null;
    }

    /**
     * Drawn by the chunk mesh, from a model that reads the root's layout.
     *
     * <p>Not a block-entity renderer: chunk-baked geometry gets vanilla lighting per cell and is
     * culled per section, so a four-cell column cannot lose its upper crates when the root leaves the
     * frustum.
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CrateStackShapes.cellShape(sliceAt(level, pos, state));
    }

    /**
     * The same geometry the player can see and select.
     *
     * <p>Kept identical to the outline on purpose for now: the crates are the only thing in a column,
     * so anything a player can walk into is something they should be able to aim at. Later milestones
     * read the hit's height against the layout to decide which crate was aimed at, which needs the
     * outline to follow the art rather than an envelope around it.
     */
    @Override
    public VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    /** Never hands a player the internal block; a column is made of crates, and drops crates. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    /**
     * Yields the click to a held crate only when it is aimed at the top of the column.
     *
     * <p>The same rule the standalone crate follows, and the same reason: Minecraft runs the block's
     * interaction before the item's, so without this a crate held over a column would open it rather
     * than stack onto it. Anything else — another item, another face, an empty hand — falls through to
     * {@link #useWithoutItem} and opens the crate being aimed at.
     */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {

        if (hit.getDirection() != Direction.UP
                || !(stack.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof CrateBlock)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(rootOf(pos, state)) instanceof CrateStackBlockEntity stackEntity)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Only the exposed lid of the whole column is a stacking gesture. Crates pack flush, so a ray
        // arriving from outside cannot reach an interior surface, but the height is checked rather
        // than assumed so a hit from anywhere else still opens a crate.
        return CrateStackTargetResolver.isColumnTop(stackEntity, rootOf(pos, state), hit)
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Opens the crate the player is pointing at, whichever cell of the column they clicked.
     *
     * <p>The position clicked is not the crate's identity: a column's crates all share one block
     * entity, and the one being opened is decided by where the ray met the stack.
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return CrateStackTargetResolver.resolve(level, hit)
                .map(target -> {
                    player.openMenu(new LogicalCrateMenuProvider(target.stack(), target.crateId()));
                    return InteractionResult.CONSUME;
                })
                .orElse(InteractionResult.PASS);
    }

    /**
     * Refuses destruction until the break milestone lands.
     *
     * <h2>Why this is here</h2>
     *
     * <p>Players can now build compact columns in ordinary play, and taking one apart is genuinely
     * hard: a column carries several inventories inside one block entity, its cells must shrink in
     * step, and the client predicts block removal that the server has to correct. None of that exists
     * yet. Until it does, a half-implemented break would silently remove a cell and leave the column
     * inconsistent — with real player inventories inside it.
     *
     * <p>So a column simply cannot be broken. That is visibly wrong and completely safe, which is the
     * right way round for something holding other people's belongings. The break milestone replaces
     * this with per-crate destruction; nothing else should ever need it.
     */
    @Override
    public boolean onDestroyedByPlayer(
            BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest,
            FluidState fluid) {
        return false;
    }

    /** Unbreakable by hand, for the same reason, and without pretending it is merely very hard. */
    @Override
    public float getDestroyProgress(
            BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    /** What this cell shows, resolved from whichever cell owns the column. */
    private static CrateStackSlice sliceAt(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CrateStackBlock)) {
            return CrateStackSlice.empty();
        }
        int part = state.getValue(PART);
        BlockEntity found = level.getBlockEntity(pos.below(part));
        if (!(found instanceof CrateStackBlockEntity stack)) {
            // A cell whose root has gone is geometry with nothing behind it. Reporting nothing keeps
            // it out of the player's way until reconciliation clears it.
            return CrateStackSlice.empty();
        }
        return stack.sliceFor(part);
    }

    /** Exposed so tests can assert an empty column contributes no geometry at all. */
    public static VoxelShape emptyShape() {
        return Shapes.empty();
    }
}
