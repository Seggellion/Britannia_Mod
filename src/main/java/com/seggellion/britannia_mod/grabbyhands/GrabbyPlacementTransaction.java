package com.seggellion.britannia_mod.grabbyhands;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Places one enrolled object into the world in Adventure mode and marks it as player-placed.
 *
 * <h2>Why this is not an Adventure-mode bypass</h2>
 *
 * <p>Adventure mode's only placement gate lives in {@code ItemStack.useOn}. Everything that makes a
 * placement <em>correct</em> — {@code getStateForPlacement}, {@code canSurvive},
 * {@code isUnobstructed}, {@code setPlacedBy}, the place sound, item consumption — lives in
 * {@code BlockItem.place}, which has no permission check at all.
 *
 * <p>So this transaction calls {@code BlockItem.place} directly for enrolled types only. Nothing
 * global is relaxed: no {@code mayBuild}, no game-mode change, no cancelled restriction. An item that
 * is not in {@code britannia_mod:grabby_movable} is refused here and then hits the ordinary Adventure
 * gate exactly as before.
 *
 * <h2>Delegating instead of reimplementing</h2>
 *
 * <p>Because the native path runs, existing behaviour survives without this class knowing about it.
 * Facing is restored by the block. Furniture stacking works because {@code canSurvive} and
 * {@code isUnobstructed} are the real rules — a chair placed on a chair succeeds because chairs are
 * full-cube supports, and an obstructed placement fails because vanilla says so, not because Grabby
 * Hands guessed. A wine bottle keeps its {@code WineData} because {@code setPlacedBy} transfers it.
 * Seating is untouched: this never writes a block state itself.
 */
public final class GrabbyPlacementTransaction {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GrabbyPlacementTransaction() {
    }

    public static GrabbyPlacementResult execute(
            GrabbyWorld world, GrabbyActor actor, ItemStack held, BlockHitResult hit) {

        Block block = enrolledBlockOf(held);
        boolean hostedItem = block == null && GrabbyEligibility.hostablePlainItem(held);
        if (block == null && !hostedItem) {
            return GrabbyPlacementResult.refused(GrabbyPlacementOutcome.TYPE_NOT_ENROLLED);
        }

        // Asked before anything is resolved or claimed, because a click that was never a placement
        // request must leave the world and the block's own behaviour exactly as it found them.
        if (held.getItem() instanceof GrabbyStructurePlacementItem structure
                && !structure.isPlacementGesture(hit)) {
            return GrabbyPlacementResult.refused(GrabbyPlacementOutcome.NOT_A_PLACEMENT_GESTURE);
        }

        Optional<BlockPos> target = actor.resolvePlacementTarget(hit);
        if (target.isEmpty()) {
            return GrabbyPlacementResult.refused(GrabbyPlacementOutcome.NO_VALID_TARGET);
        }
        BlockPos destination = target.get();

        try (GrabbyMutationGuard.Claim claim = GrabbyMutationGuard.claim(world.levelIdentity(), destination)) {
            if (!claim.held()) {
                return GrabbyPlacementResult.refused(GrabbyPlacementOutcome.ALREADY_IN_PROGRESS);
            }
            return hostedItem
                    ? executeHostedClaimed(world, actor, held.copyWithCount(1), destination, hit)
                    : executeClaimed(world, actor, block, destination, hit);
        }
    }

    private static GrabbyPlacementResult executeClaimed(
            GrabbyWorld world, GrabbyActor actor, Block block, BlockPos destination, BlockHitResult hit) {

        if (!actor.canReach(destination)) {
            return GrabbyPlacementResult.refused(GrabbyPlacementOutcome.OUT_OF_REACH);
        }
        if (!GrabbyPolicy.mayPlace(
                actor.isCreative(), actor.permissionLevel(), actor.insideForeignStructure(destination))) {
            return GrabbyPlacementResult.refused(GrabbyPlacementOutcome.DENIED_BY_POLICY);
        }

        // The block's own rules decide from here. An invalid stack or an obstructed space is refused
        // by canSurvive/isUnobstructed, and the source item is not consumed.
        Optional<BlockPos> placed = actor.placeMainHandItem(hit);
        if (placed.isEmpty()) {
            return GrabbyPlacementResult.refused(GrabbyPlacementOutcome.REFUSED_BY_BLOCK);
        }
        BlockPos landedAt = placed.get();

        if (!world.blockState(landedAt).is(block)) {
            // The object is somewhere other than where vanilla said it would be. Types whose
            // getStateForPlacement shifts the position - the armoire family does - would land here,
            // which is precisely why they are not enrolled yet.
            LOGGER.warn("[grabby-hands] Placed {} but {} does not hold it; leaving it protected",
                    block, landedAt);
            return new GrabbyPlacementResult(
                    GrabbyPlacementOutcome.PLACED_WITHOUT_PROVENANCE, landedAt, 1);
        }

        GrabbyInstanceState provenance = GrabbyInstanceState.playerPlaced(actor.id(), world.gameTime());
        if (!world.setGrabbyState(landedAt, provenance)) {
            LOGGER.warn("[grabby-hands] {} at {} cannot carry provenance; leaving it protected",
                    block, landedAt);
            return new GrabbyPlacementResult(
                    GrabbyPlacementOutcome.PLACED_WITHOUT_PROVENANCE, landedAt, 1);
        }

        return new GrabbyPlacementResult(GrabbyPlacementOutcome.SUCCESS, landedAt, 1);
    }

    /**
     * The loose-item path: the object placed is the generic host, and the item becomes its payload.
     *
     * <p>Structurally identical to the native path — same guard, same reach and policy checks, same
     * provenance stamp. The only difference is which block lands and that the host is then told what
     * it is holding.
     */
    private static GrabbyPlacementResult executeHostedClaimed(
            GrabbyWorld world, GrabbyActor actor, ItemStack payload, BlockPos destination, BlockHitResult hit) {

        if (!actor.canReach(destination)) {
            return GrabbyPlacementResult.refused(GrabbyPlacementOutcome.OUT_OF_REACH);
        }
        if (!GrabbyPolicy.mayPlace(
                actor.isCreative(), actor.permissionLevel(), actor.insideForeignStructure(destination))) {
            return GrabbyPlacementResult.refused(GrabbyPlacementOutcome.DENIED_BY_POLICY);
        }

        Optional<BlockPos> placed = actor.placeItemHost(hit);
        if (placed.isEmpty()) {
            return GrabbyPlacementResult.refused(GrabbyPlacementOutcome.REFUSED_BY_BLOCK);
        }
        BlockPos landedAt = placed.get();

        // The payload is attached before provenance, so an object that exists always knows what it
        // is holding even if the provenance stamp then fails.
        if (!world.attachPayload(landedAt, payload)) {
            LOGGER.warn("[grabby-hands] Host at {} could not accept its payload; leaving it protected", landedAt);
            return new GrabbyPlacementResult(
                    GrabbyPlacementOutcome.PLACED_WITHOUT_PROVENANCE, landedAt, 1);
        }
        if (!world.setGrabbyState(landedAt, GrabbyInstanceState.playerPlaced(actor.id(), world.gameTime()))) {
            LOGGER.warn("[grabby-hands] Host at {} cannot carry provenance; leaving it protected", landedAt);
            return new GrabbyPlacementResult(
                    GrabbyPlacementOutcome.PLACED_WITHOUT_PROVENANCE, landedAt, 1);
        }
        return new GrabbyPlacementResult(GrabbyPlacementOutcome.SUCCESS, landedAt, 1);
    }

    /**
     * The block this stack would place, if and only if the type is enrolled.
     *
     * <p>Enrollment is checked against the block, not the item, so the tag stays the single source of
     * truth and an item can never smuggle in an unenrolled block.
     */
    static Block enrolledBlockOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        Block block = blockItem.getBlock();
        return GrabbyEligibility.movableType(block.defaultBlockState()) ? block : null;
    }
}
