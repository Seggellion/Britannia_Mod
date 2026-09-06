package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.crate.CrateFoundation;
import com.seggellion.britannia_mod.crate.CrateStackBreakTargets;
import com.seggellion.britannia_mod.crate.CrateStackBreakTransaction;
import com.seggellion.britannia_mod.crate.CrateStackLayout;
import com.seggellion.britannia_mod.crate.CrateStackShapes;
import com.seggellion.britannia_mod.crate.CrateStackTargetResolver;
import com.seggellion.britannia_mod.crate.LogicalCrateMenuProvider;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Decorative multiblock shell with one server-authoritative inventory on its root cell. */
public final class CrateBlock extends DecorativeMultiblockBlock implements EntityBlock {
    private final int slotCount;
    private final String containerTitleKey;

    public CrateBlock(
            Properties properties,
            int slotCount,
            String containerTitleKey,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            CellShapeFactory shapeFactory) {
        super(properties, minX, maxX, minY, maxY, minZ, maxZ, shapeFactory);
        if (slotCount != 9 && slotCount != 27 && slotCount != 54) {
            throw new IllegalArgumentException("Crates must use a vanilla 1-, 3-, or 6-row inventory");
        }
        this.slotCount = slotCount;
        this.containerTitleKey = Objects.requireNonNull(containerTitleKey, "containerTitleKey");
    }

    public int slotCount() {
        return slotCount;
    }

    public String containerTitleKey() {
        return containerTitleKey;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return hasValidPart(state) && isRoot(state) ? new CrateBlockEntity(pos, state) : null;
    }

    /**
     * Yields the click to the held crate so a crate can be stacked on a crate.
     *
     * <p>Minecraft runs the block interaction before the item's, and {@link #useWithoutItem} consumes
     * it by opening this crate. Without this the placement item is never reached at all, and a crate
     * could only ever be stacked by sneaking. {@code SKIP_DEFAULT_BLOCK_INTERACTION} is the one result
     * that neither consumes the action nor falls through to {@code useWithoutItem}, so the held crate's
     * own {@code useOn} runs and stays the single authority on whether the placement is legal.
     *
     * <p>Scoped to a crate on an upward face: every other item, face, and the empty hand still open
     * this crate exactly as before.
     */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (hit.getDirection() == Direction.UP
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof CrateBlock) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Lets the cell holding a column's overhang draw, when every other non-root cell stays invisible.
     *
     * <p>Chunk geometry is lit against the block it is emitted from, so a crate resting on this
     * crate's lid has to be drawn by the cell it is physically inside - not by the anchor a cell
     * below, which is where its light and ambient occlusion would then be sampled from.
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return CrateFoundation.carriesOverhang(this, state)
                ? RenderShape.MODEL
                : super.getRenderShape(state);
    }

    /**
     * This cell's own art, plus whatever of a column standing on it reaches down into this cell.
     *
     * <p>The overhang has to be answered for here rather than by the column, because a ray only ever
     * tests the blocks it actually passes through. A crate resting on this crate's lid is physically
     * inside this cell, so a player looking at it from the side never reaches the column's own block
     * and would find nothing to click.
     */
    @Override
    public VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape own = authoredShape(state);
        if (level == null || pos == null) {
            return own;
        }
        // Standing on another crate lowers this cell's own art out of its cell.
        double origin = CrateFoundation.originBlocksAt(level, pos, state);
        if (origin != 0.0D) {
            own = CrateStackShapes.shiftIntoCell(own, origin);
        }
        // And a crate standing on this one reaches down into here.
        own = Shapes.or(own, CrateFoundation.restingAbove(level, pos));
        if (!CrateFoundation.carriesOverhang(this, state)) {
            return own;
        }
        VoxelShape withOwn = own;
        return CrateFoundation.columnOn(level, pos, state)
                .map(founded -> Shapes.or(withOwn, CrateStackShapes.cellShape(founded.overhang())))
                .orElse(withOwn);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!hasValidPart(state)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // A large crate standing on this one keeps its own fifty-four slots, and its body reaches
        // down through this cell. Height decides which of the two a click meant, exactly as it does
        // for a column.
        Optional<InteractionResult> restingLarge = CrateFoundation.largeOn(level, pos, state)
                .filter(resting ->
                        CrateFoundation.aboveLid(level, resting.foundationAnchor(), hit.getLocation()))
                .flatMap(resting -> level.getBlockEntity(resting.anchor())
                        instanceof CrateBlockEntity upper
                        ? Optional.of(open(player, upper))
                        : Optional.empty());
        if (restingLarge.isPresent()) {
            return restingLarge.get();
        }

        // A crate resting on this crate's lid keeps its own inventory. Which of the two a click means
        // is decided by height alone: the column owns everything from the lid upwards, this crate
        // everything below, so neither can take the other's clicks.
        Optional<InteractionResult> onTop = CrateFoundation.columnOn(level, pos, state)
                .flatMap(founded -> CrateStackTargetResolver
                        .crateAt(founded.stack(), founded.root(), hit.getLocation())
                        .map(crateId -> {
                            player.openMenu(new LogicalCrateMenuProvider(founded.stack(), crateId));
                            return InteractionResult.CONSUME;
                        }));
        if (onTop.isPresent()) {
            return onTop.get();
        }
        BlockPos anchor = anchorPosition(pos, state);
        BlockState rootState = level.getBlockState(anchor);
        if (!rootState.is(this) || !isRoot(rootState)) {
            return InteractionResult.PASS;
        }
        if (level.getBlockEntity(anchor) instanceof CrateBlockEntity crate) {
            player.openMenu(crate);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Opens one crate, whichever of a stacked pair the click turned out to mean. */
    private static InteractionResult open(Player player, CrateBlockEntity crate) {
        player.openMenu(crate);
        return InteractionResult.CONSUME;
    }

    /**
     * Breaks a crate resting on this crate's lid rather than this crate, when that is what was aimed
     * at.
     *
     * <p>A column founded here begins inside the cell above this crate's anchor, so a swing at one of
     * its lowest crates arrives as a swing at this block. Without this the player aims at a small
     * crate and destroys the large one underneath it, taking fifty-four slots with it.
     *
     * <p>Returning false says this crate was not removed, which is the truth: only the logical crate
     * above it was.
     */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
            boolean willHarvest, FluidState fluid) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            // A crate standing on this one is inside this crate's cells, so a swing aimed at it lands
            // here. Which crate the player meant was decided by height when the swing started; taking
            // apart the one they were pointing at is the whole of the difference.
            Optional<BlockPos> aimedCrate = CrateStackBreakTargets.crateTarget(serverPlayer);
            if (hasValidPart(state) && aimedCrate.isPresent()
                    && !aimedCrate.get().equals(anchorPosition(pos, state))) {
                BlockState aimedState = server.getBlockState(aimedCrate.get());
                if (aimedState.is(this) && isRoot(aimedState)) {
                    destroyStructure(server, aimedCrate.get(), aimedState.getValue(FACING),
                            !player.hasInfiniteMaterials());
                    CrateStackBreakTargets.clear(serverPlayer);
                    // This crate is still standing; only the one resting on it went.
                    return false;
                }
            }
            Optional<CrateFoundation.Founded> founded =
                    CrateFoundation.columnOn(level, pos, state);
            if (founded.isPresent()) {
                BlockPos root = founded.get().root();
                Optional<CrateStackBreakTargets.CrateStackBreakTarget> aimed =
                        CrateStackBreakTargets.current(serverPlayer, root);
                if (aimed.isPresent()) {
                    CrateStackBreakTransaction.Result result = CrateStackBreakTransaction.breakCrate(
                            server, root, aimed.get().crateId(), player);
                    CrateStackBreakTargets.recordCompletion(serverPlayer, root);
                    if (result.removedCrate()) {
                        if (result.columnIsEmpty()) {
                            CrateStackBlock.removeColumn(server, root);
                        }
                        return false;
                    }
                }
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /**
     * Notices when the crate this one was standing on has gone.
     *
     * <p>The dismantle path covers a crate taken apart properly. This covers everything else - a
     * command that erased the cells underneath, or a world that already contains one from before the
     * release path existed - because a crate whose foundation vanished is invisible until something
     * puts it back on its own floor, and an invisible solid block is not something a player can
     * reason about or get rid of.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
            BlockPos neighborPos, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moving);
        if (level instanceof ServerLevel server && !isMutating()) {
            CrateFoundation.repairIfOrphaned(server, pos, state);
        }
    }

    @Override
    protected void beforeDismantle(ServerLevel level, BlockPos anchor, Direction facing) {
        // Anything resting on this crate's lid keeps its crates and its contents; it simply loses the
        // thing it was standing on and settles onto its own floor.
        CrateFoundation.releaseColumn(level, anchor);
        CrateFoundation.releaseRestingLarge(level, anchor);
        if (level.getBlockEntity(anchor) instanceof CrateBlockEntity crate) {
            Containers.dropContents(level, anchor, crate);
            level.updateNeighbourForOutputSignal(anchor, this);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return hasValidPart(state) && isRoot(state);
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }
}
