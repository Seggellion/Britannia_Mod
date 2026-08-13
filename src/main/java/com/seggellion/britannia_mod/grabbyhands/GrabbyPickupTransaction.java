package com.seggellion.britannia_mod.grabbyhands;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Moves one placed object out of the world and into a player's inventory, exactly once.
 *
 * <h2>Ordering is the design</h2>
 *
 * <p>Every invariant this milestone owes falls out of the sequence below, so the order of these steps
 * is not incidental and should not be rearranged for tidiness:
 *
 * <ol>
 *   <li><b>Claim</b> the object. A second concurrent attempt — a racing player, a duplicated packet —
 *       loses here and mutates nothing.</li>
 *   <li><b>Validate</b> eligibility, provenance, policy and reach, all server-side.</li>
 *   <li><b>Capture</b> the portable item <em>without mutating anything</em>, so a later refusal costs
 *       nothing.</li>
 *   <li><b>Pre-check inventory room.</b> This is what guarantees a full inventory never destroys the
 *       object you were trying to pick up: we find out before the block is touched.</li>
 *   <li><b>Remove</b> the world object, drop-free.</li>
 *   <li><b>Grab cue</b> — the object has physically left the world.</li>
 *   <li><b>Insert</b> into the inventory.</li>
 *   <li><b>Stow cue</b>, and only if insertion actually succeeded.</li>
 * </ol>
 *
 * <p>The one case the pre-check cannot cover is an inventory that changed between step 4 and step 7.
 * Rather than voiding the item or rolling the block back into a position that may no longer be free,
 * the object is dropped at the player's feet and the stow cue is withheld. Nothing is lost, nothing
 * is duplicated, and the player is not told they stowed something they did not.
 */
public final class GrabbyPickupTransaction {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GrabbyPickupTransaction() {
    }

    public static GrabbyPickupResult execute(GrabbyWorld world, GrabbyActor actor, BlockPos clickedPos) {
        BlockPos root = GrabbyRootResolver.resolveRoot(world, clickedPos);

        try (GrabbyMutationGuard.Claim claim = GrabbyMutationGuard.claim(world.levelIdentity(), root)) {
            if (!claim.held()) {
                return GrabbyPickupResult.refused(GrabbyPickupOutcome.ALREADY_IN_PROGRESS, root);
            }
            return executeClaimed(world, actor, root);
        }
    }

    private static GrabbyPickupResult executeClaimed(GrabbyWorld world, GrabbyActor actor, BlockPos root) {
        BlockState state = world.blockState(root);
        if (state == null || state.isAir()) {
            return GrabbyPickupResult.refused(GrabbyPickupOutcome.NOTHING_THERE, root);
        }
        if (!GrabbyEligibility.movableType(state)) {
            return GrabbyPickupResult.refused(GrabbyPickupOutcome.TYPE_NOT_ENROLLED, root);
        }

        GrabbyInstanceState provenance = world.grabbyState(root);
        if (!provenance.grabbyManaged()) {
            // Britannia scenery and admin decoration land here. The type is enrolled; this instance
            // simply was not placed by a player, which is the protected default.
            return GrabbyPickupResult.refused(GrabbyPickupOutcome.NOT_GRABBY_MANAGED, root);
        }
        if (!actor.canReach(root)) {
            return GrabbyPickupResult.refused(GrabbyPickupOutcome.OUT_OF_REACH, root);
        }
        if (!GrabbyPolicy.mayMutate(
                provenance.grabbyManaged(),
                actor.isCreative(),
                actor.permissionLevel(),
                actor.insideForeignStructure(root),
                GrabbyMutationReason.PICKUP)) {
            return GrabbyPickupResult.refused(GrabbyPickupOutcome.DENIED_BY_POLICY, root);
        }

        // The object's own reasons for refusing come before anything is captured or touched.
        Optional<GrabbyTransportRefusal> refusal = world.transportRefusal(root);
        if (refusal.isPresent()) {
            return GrabbyPickupResult.refused(switch (refusal.get()) {
                case IN_USE -> GrabbyPickupOutcome.IN_USE;
                case NESTED_CONTAINER -> GrabbyPickupOutcome.NESTED_CONTAINER;
            }, root);
        }

        ItemStack portable = world.capturePortableStack(root);
        if (portable == null || portable.isEmpty()) {
            return GrabbyPickupResult.refused(GrabbyPickupOutcome.NO_PORTABLE_FORM, root);
        }
        if (!actor.hasRoomFor(portable)) {
            return GrabbyPickupResult.refused(GrabbyPickupOutcome.INVENTORY_FULL, root);
        }

        // Give up anything the block would otherwise drop from its own removal path, so the object
        // cannot arrive in the inventory and on the floor at the same time.
        boolean detached = world.detachPayloadBeforeRemoval(root);

        if (!world.removeObject(root)) {
            if (detached) {
                // The object gave up its contents for a removal that then did not happen. Put them
                // back rather than leaving an emptied container standing in the world.
                LOGGER.error("[grabby-hands] Removal failed at {} after detaching; restoring", root);
                world.restoreAfterFailedRemoval(root, portable);
            }
            return GrabbyPickupResult.refused(GrabbyPickupOutcome.REMOVAL_FAILED, root);
        }

        world.playWorldSound(root, GrabbySoundRoles.grab());

        // give() may consume the stack, so the reported payload is captured first.
        ItemStack reported = portable.copy();
        if (actor.give(portable)) {
            actor.playPersonalSound(GrabbySoundRoles.stow());
            return new GrabbyPickupResult(
                    GrabbyPickupOutcome.SUCCESS, root, reported, 1, 1, true, true);
        }

        LOGGER.warn("[grabby-hands] Inventory room check passed but insertion failed at {}; dropping at feet", root);
        actor.dropAtFeet(portable);
        return new GrabbyPickupResult(
                GrabbyPickupOutcome.DROPPED_AT_FEET, root, reported, 1, 0, true, false);
    }
}
