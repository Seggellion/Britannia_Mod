package com.seggellion.britannia_mod.crate;

import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Destroying one visible crate out of a column.
 *
 * <h2>The player breaks a crate, not a position</h2>
 *
 * <p>Minecraft destroys blocks, and a column is several crates inside one. So the block position may
 * survive a break, may lose a continuation cell, or may disappear entirely, depending only on what is
 * left afterwards — none of which the player is aiming at. What they aimed at is a crate id, captured
 * when they started swinging, and that is the only thing this removes.
 *
 * <h2>Order, and why it is this order</h2>
 *
 * <p>The crate is snapshotted, removed from the column, the survivors repack, the world cells
 * reconcile, and only then does anything drop. Dropping first would leave a window where the items
 * exist both on the floor and in a crate that is still in the column; removing first means the crate
 * being emptied is already gone from the authority, so nothing can drop it twice. Survivors are never
 * touched at all — repacking recomputes geometry and does not move an item.
 */
public final class CrateStackBreakTransaction {

    private CrateStackBreakTransaction() {
    }

    /** What happened, and whether the world block should now go. */
    public record Result(boolean removedCrate, boolean columnIsEmpty) {

        static Result nothing() {
            return new Result(false, false);
        }
    }

    /**
     * Removes one crate by identity and drops it where it stood.
     *
     * <p>Safe to call with a target that has since gone: it removes nothing, drops nothing, and says
     * so. That is the ordinary outcome of two players breaking the same crate.
     *
     * @return whether a crate was removed, and whether the column is now empty
     */
    public static Result breakCrate(
            ServerLevel level, BlockPos root, int crateId, Player player) {

        if (!(level.getBlockEntity(root) instanceof CrateStackBlockEntity stack)) {
            return Result.nothing();
        }
        LogicalCrate crate = stack.crateById(crateId);
        if (crate == null) {
            // Somebody else broke it first. Doing nothing is the whole of the correct behaviour: the
            // alternative - falling back to whatever crate now occupies that height - would destroy
            // a crate this player never aimed at.
            return Result.nothing();
        }

        // Where the crate stood, taken before the survivors move.
        Vec3 where = physicalCentre(root, stack.placementOf(crateId));
        CrateVariant variant = crate.variant();

        LogicalCrate removed = stack.removeCrate(crateId).orElse(null);
        if (removed == null) {
            return Result.nothing();
        }
        CrateStackColumnSync.reconcile(level, root, stack);
        CrateStackColumnSync.notifyClientsNextTick(level, root);

        dropCrate(level, where, variant, removed, player);
        return new Result(true, stack.isEmpty());
    }

    /**
     * Drops what a broken crate leaves behind, matching the standalone crate exactly.
     *
     * <p>{@code DecorativeMultiblockBlock.dismantle} spills a crate's contents unconditionally and
     * pops the crate item only when the player is not in creative. Both are mirrored here rather than
     * reinvented, so a crate in a column and a crate on its own leave the same things on the floor.
     */
    private static void dropCrate(
            ServerLevel level, Vec3 where, CrateVariant variant, LogicalCrate removed, Player player) {

        // Dropped one stack at a time so each lands at the crate's own height rather than at the
        // block position the column happens to be rooted on.
        for (ItemStack stack : removed.takeContents()) {
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, where.x, where.y, where.z, stack);
            }
        }

        if (!player.hasInfiniteMaterials()) {
            Block.popResource(level, BlockPos.containing(where), new ItemStack(itemFor(variant)));
        }
    }

    /** The middle of where a crate stood, so its contents fall around it rather than at the floor. */
    private static Vec3 physicalCentre(BlockPos root, CratePlacement placement) {
        double centre = placement == null
                ? 0.5D
                : (placement.baseHundredths() + placement.topHundredths())
                        / 2.0D / (CrateStackLayout.HUNDREDTHS_PER_VOXEL * 16);
        return new Vec3(root.getX() + 0.5D, root.getY() + centre, root.getZ() + 0.5D);
    }

    /** The crate a player receives, which is always the visible variant and never the column. */
    private static Item itemFor(CrateVariant variant) {
        return switch (variant) {
            case SMALL -> ItemRegistry.SMALL_CRATE_ITEM.get();
            case MEDIUM -> ItemRegistry.MEDIUM_CRATE_ITEM.get();
        };
    }

    /** The crate a column would drop for this id, for tests that want it without breaking anything. */
    public static Optional<CrateVariant> variantOf(CrateStackBlockEntity stack, int crateId) {
        LogicalCrate crate = stack.crateById(crateId);
        return crate == null ? Optional.empty() : Optional.of(crate.variant());
    }
}
