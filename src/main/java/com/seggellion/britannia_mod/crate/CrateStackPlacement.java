package com.seggellion.britannia_mod.crate;

import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.world.level.block.Block;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Putting one more crate on a crate, as a player experiences it.
 *
 * <h2>Two starting points, one ending</h2>
 *
 * <p>A player may be aiming at an ordinary crate that has never been stacked on, or at a column that
 * already exists. Both end with a compact column one crate taller, and both run through here so the
 * ordering, the preflight and the failure behaviour are written once.
 *
 * <h2>Preflight, not rollback</h2>
 *
 * <p>The final layout is worked out and every world cell it will need is checked <em>before</em> the
 * legacy crate is replaced or the column is touched. Promoting first and discovering a ceiling second
 * would mean unwinding a conversion that has already moved an inventory, and unwinding is where
 * duplication bugs live. A refused placement here has changed nothing at all: the crate is still a
 * crate, the column is still the same height, no id was issued, and the held stack is untouched.
 */
public final class CrateStackPlacement {

    /**
     * The keys a carried crate item is known to hold.
     *
     * <p>{@code Items} is written by {@code CrateBlockEntity.writePortableState} and {@code id} by
     * {@code BlockItem.setBlockEntityData}. Anything else means the item carries state this code was
     * not written to understand, and the honest response to that is to refuse rather than to adopt
     * what it recognises and quietly drop the rest.
     */
    private static final Set<String> KNOWN_PORTABLE_KEYS = Set.of("Items", "id");

    private CrateStackPlacement() {
    }

    /** Why a compact placement did not happen. */
    public enum Refusal {
        /** Not a crate or a column, or its root has gone. */
        NOT_A_TARGET,
        /** The held item is not a crate compact columns carry. */
        HELD_VARIANT_NOT_SUPPORTED,
        /** The target is a large crate, which stays a conventional multiblock. */
        TARGET_VARIANT_NOT_SUPPORTED,
        /** The column is already as tall as it may be. */
        AT_HEIGHT_CAP,
        /** A cell the column would need is occupied or unavailable. */
        OBSTRUCTED,
        /** The held crate carries state this milestone cannot move into a column. */
        HELD_CRATE_CARRIES_STATE,
        /** The world refused a change; nothing was altered. */
        WORLD_REFUSED
    }

    /** What a placement did. */
    public record Result(OptionalInt crateId, Optional<Refusal> refusal) {

        static Result placed(int crateId) {
            return new Result(OptionalInt.of(crateId), Optional.empty());
        }

        static Result refused(Refusal refusal) {
            return new Result(OptionalInt.empty(), Optional.of(refusal));
        }

        public boolean succeeded() {
            return crateId.isPresent();
        }
    }

    /**
     * Whether this position is something a held crate can be stacked onto at all.
     *
     * <p>Asked before anything else so the interaction layer can decide whether a click is a stacking
     * gesture without starting a transaction to find out.
     */
    public static boolean isCompactTarget(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CrateStackBlock) {
            return true;
        }
        return state.getBlock() instanceof CrateBlock crate
                && crate.cells().size() == 1
                && CrateVariant.forSlotCount(crate.slotCount()).isPresent();
    }

    /**
     * Adds the held crate to whatever is at {@code pos}, promoting it to a column first if needed.
     *
     * <p>Consumes exactly one crate from {@code held} on success, and nothing on failure.
     */
    public static Result place(
            ServerLevel level, BlockPos pos, Player player, ItemStack held, Direction fallbackFacing) {

        Optional<CrateVariant> heldVariant = variantOf(held);
        if (heldVariant.isEmpty()) {
            return Result.refused(Refusal.HELD_VARIANT_NOT_SUPPORTED);
        }
        Optional<NonNullList<ItemStack>> carried = carriedContents(level, held, heldVariant.get());
        if (carried == null) {
            return Result.refused(Refusal.HELD_CRATE_CARRIES_STATE);
        }

        BlockState state = level.getBlockState(pos);
        Result result = state.getBlock() instanceof CrateStackBlock
                ? appendToColumn(level, pos, heldVariant.get(), carried.orElse(null))
                : promoteAndAppend(level, pos, heldVariant.get(), carried.orElse(null), fallbackFacing);

        if (result.succeeded()) {
            BlockPos root = CrateStackBlock.rootOf(pos, level.getBlockState(pos));
            announce(level, root, player);
            if (!player.hasInfiniteMaterials()) {
                held.shrink(1);
            }
        }
        return result;
    }

    /* ─── the two paths ──────────────────────────────────────── */

    private static Result appendToColumn(
            ServerLevel level, BlockPos clicked, CrateVariant variant, NonNullList<ItemStack> carried) {

        BlockPos root = CrateStackBlock.rootOf(clicked, level.getBlockState(clicked));
        if (!(level.getBlockEntity(root) instanceof CrateStackBlockEntity stack)) {
            return Result.refused(Refusal.NOT_A_TARGET);
        }
        Direction facing = stack.topCrate() == null
                ? Direction.NORTH
                : stack.topCrate().facing();

        Result preflight = preflight(level, root, stack.crates(), stack.nextCrateId(), variant, facing,
                stack.originHundredths());
        if (preflight != null) {
            return preflight;
        }
        OptionalInt id = carried == null
                ? stack.appendCrate(variant, facing)
                : stack.appendCrate(variant, facing, carried);
        if (id.isEmpty()) {
            return Result.refused(Refusal.AT_HEIGHT_CAP);
        }
        // The cell count may not have changed - two small crates share one cell - so the client is
        // told explicitly rather than relying on a block having moved.
        CrateStackColumnSync.notifyClients(level, root, CrateStackColumnSync.reconcile(level, root, stack));
        return Result.placed(id.getAsInt());
    }

    /**
     * Turns a lone crate into a column of two, in one operation.
     *
     * <p>The preflight happens against the layout the column will have <em>after</em> both crates are
     * in it, so a pair that needs a second cell discovers a ceiling while the legacy crate is still a
     * legacy crate.
     */
    private static Result promoteAndAppend(
            ServerLevel level,
            BlockPos pos,
            CrateVariant variant,
            NonNullList<ItemStack> carried,
            Direction fallbackFacing) {

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CrateBlock crate)) {
            return Result.refused(Refusal.NOT_A_TARGET);
        }
        Optional<CrateVariant> standing = CrateVariant.forSlotCount(crate.slotCount());
        if (standing.isEmpty() || crate.cells().size() != 1) {
            return Result.refused(Refusal.TARGET_VARIANT_NOT_SUPPORTED);
        }
        Direction facing = state.hasProperty(CrateBlock.FACING)
                ? state.getValue(CrateBlock.FACING)
                : fallbackFacing;

        // Both crates, before either exists in a column.
        List<LogicalCrate> projected = List.of(
                new LogicalCrate(0, standing.get(), facing),
                new LogicalCrate(1, variant, facing));
        CrateStackLayout finalLayout = CrateStackLayout.of(projected);
        if (!CrateStackLayout.withinCap(finalLayout.totalHundredths())) {
            return Result.refused(Refusal.AT_HEIGHT_CAP);
        }
        if (CrateStackColumnSync.preflight(level, pos, finalLayout.requiredCells()) != null) {
            return Result.refused(Refusal.OBSTRUCTED);
        }

        CrateStackPromotion.Result promoted = CrateStackPromotion.promote(level, pos);
        if (!promoted.succeeded()) {
            return Result.refused(promoted.refusal().orElseThrow() == CrateStackPromotion.Refusal.VARIANT_NOT_SUPPORTED
                    ? Refusal.TARGET_VARIANT_NOT_SUPPORTED
                    : Refusal.WORLD_REFUSED);
        }
        CrateStackBlockEntity stack = promoted.promoted().orElseThrow().stack();
        OptionalInt id = carried == null
                ? stack.appendCrate(variant, facing)
                : stack.appendCrate(variant, facing, carried);
        if (id.isEmpty()) {
            // Cannot happen: the cap was checked against the finished layout above.
            return Result.refused(Refusal.AT_HEIGHT_CAP);
        }
        CrateStackColumnSync.notifyClients(level, pos, CrateStackColumnSync.reconcile(level, pos, stack));
        return Result.placed(id.getAsInt());
    }

    /** The checks an append must pass, run against the layout it would produce. */
    private static Result preflight(
            ServerLevel level,
            BlockPos root,
            List<LogicalCrate> existing,
            int nextId,
            CrateVariant variant,
            Direction facing,
            int originHundredths) {

        List<LogicalCrate> projected = new ArrayList<>(existing);
        projected.add(new LogicalCrate(nextId, variant, facing));
        CrateStackLayout layout = CrateStackLayout.of(projected, originHundredths);
        if (!CrateStackLayout.withinCap(layout.totalHundredths())) {
            return Result.refused(Refusal.AT_HEIGHT_CAP);
        }
        CrateStackColumnSync.GrowthRefusal blocked =
                CrateStackColumnSync.preflight(level, root, layout.requiredCells());
        if (blocked == CrateStackColumnSync.GrowthRefusal.AT_HEIGHT_CAP) {
            return Result.refused(Refusal.AT_HEIGHT_CAP);
        }
        return blocked == null ? null : Result.refused(Refusal.OBSTRUCTED);
    }

    /**
     * Starts a compact column on a large crate's lid.
     *
     * <h2>Why this is not promotion</h2>
     *
     * <p>Promotion converts a crate into a column and moves its inventory across. Nothing of the kind
     * happens here: the large crate is untouched, keeps its block entity and its fifty-four slots, and
     * simply has a separate column built above it. The only thing it contributes is the height its lid
     * reaches, which becomes the new column's origin.
     *
     * <p>Everything is decided before anything is written. If a cell the column would need is taken,
     * or the world refuses the block, the large crate, its contents and the held stack are all exactly
     * as they were, because nothing had been changed yet.
     */
    public static Result placeOnFoundation(
            ServerLevel level, BlockPos anchor, Player player, ItemStack held) {

        BlockState anchorState = level.getBlockState(anchor);
        if (!(anchorState.getBlock() instanceof CrateBlock foundation)
                || !CrateFoundation.isFoundation(anchorState)) {
            return Result.refused(Refusal.NOT_A_TARGET);
        }
        Optional<CrateVariant> heldVariant = variantOf(held);
        if (heldVariant.isEmpty()) {
            return Result.refused(Refusal.HELD_VARIANT_NOT_SUPPORTED);
        }
        Optional<NonNullList<ItemStack>> carried = carriedContents(level, held, heldVariant.get());
        if (carried == null) {
            return Result.refused(Refusal.HELD_CRATE_CARRIES_STATE);
        }

        BlockPos root = CrateFoundation.columnRootFor(anchor);
        int origin = CrateFoundation.originFor(foundation);
        Direction facing = anchorState.hasProperty(CrateBlock.FACING)
                ? anchorState.getValue(CrateBlock.FACING)
                : Direction.NORTH;

        CrateStackLayout planned = CrateStackLayout.of(
                List.of(new LogicalCrate(0, heldVariant.get(), facing)), origin);
        if (!CrateStackLayout.withinCap(planned.totalHundredths())) {
            return Result.refused(Refusal.AT_HEIGHT_CAP);
        }
        if (!CrateStackColumnSync.isAvailableFor(level, root, root)) {
            return Result.refused(Refusal.OBSTRUCTED);
        }
        if (CrateStackColumnSync.preflight(level, root, planned.requiredCells()) != null) {
            return Result.refused(Refusal.OBSTRUCTED);
        }

        boolean placed = CrateStackBlock.duringMutation(() -> level.setBlock(
                root,
                BlockRegistry.CRATE_STACK.get().defaultBlockState()
                        .setValue(CrateStackBlock.PART, 0),
                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS));
        if (!placed || !(level.getBlockEntity(root) instanceof CrateStackBlockEntity stack)) {
            return Result.refused(Refusal.WORLD_REFUSED);
        }
        stack.setOriginHundredths(origin);
        OptionalInt id = carried.isEmpty()
                ? stack.appendCrate(heldVariant.get(), facing)
                : stack.appendCrate(heldVariant.get(), facing, carried.get());
        if (id.isEmpty()) {
            CrateStackBlock.duringMutation(() -> level.removeBlock(root, false));
            return Result.refused(Refusal.AT_HEIGHT_CAP);
        }
        stack.setChanged();
        CrateStackColumnSync.notifyClients(
                level, root, CrateStackColumnSync.reconcile(level, root, stack));
        announce(level, anchor, player);
        if (!player.hasInfiniteMaterials()) {
            held.shrink(1);
        }
        return Result.placed(id.getAsInt());
    }

    /* ─── the held item ──────────────────────────────────────── */

    private static Optional<CrateVariant> variantOf(ItemStack held) {
        if (!(held.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof CrateBlock crate)
                || crate.cells().size() != 1) {
            return Optional.empty();
        }
        return CrateVariant.forSlotCount(crate.slotCount());
    }

    /**
     * The contents a carried crate item brings with it.
     *
     * <p>Grabby Hands moves a stocked crate by writing its inventory into the item, so a held crate is
     * not always empty and turning one into a fresh empty crate would destroy whatever it held. Those
     * contents are read back here and adopted by the new logical crate.
     *
     * @return empty for an ordinary fresh crate, the contents for a carried one, and {@code null} when
     *     the item carries something this code does not recognise — which is a refusal, not a value
     */
    private static Optional<NonNullList<ItemStack>> carriedContents(
            ServerLevel level, ItemStack held, CrateVariant variant) {

        CustomData data = held.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY);
        if (data.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag tag = data.copyTag();
        if (!KNOWN_PORTABLE_KEYS.containsAll(tag.getAllKeys())) {
            return null;
        }
        NonNullList<ItemStack> contents =
                NonNullList.withSize(variant.slotCount(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, contents, level.registryAccess());
        return Optional.of(contents);
    }

    /** One placement sound and one game event, wherever the crate landed. */
    private static void announce(ServerLevel level, BlockPos root, Player player) {
        BlockState state = level.getBlockState(root);
        SoundType sound = state.getSoundType(level, root, player);
        level.playSound(player, root, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE, root, GameEvent.Context.of(player, state));
    }
}
