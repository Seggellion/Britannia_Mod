package com.seggellion.britannia_mod.grabbyhands;

import com.mojang.logging.LogUtils;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
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
        return execute(world, actor, clickedPos, OptionalInt.empty());
    }

    /**
     * Picks up one object, which may be one of several living at the same position.
     *
     * <p>Almost every enrolled block is the only thing at its position, and passes no sub-object: the
     * transaction below is the one it has always run. A compact crate column is several crates a
     * player points at individually, sharing one block entity, so the crate they meant is named here
     * rather than inferred from the position - taking the position would take all of them.
     */
    public static GrabbyPickupResult execute(
            GrabbyWorld world, GrabbyActor actor, BlockPos clickedPos, OptionalInt subObject) {
        BlockPos root = GrabbyRootResolver.resolveRoot(world, clickedPos);

        try (GrabbyMutationGuard.Claim claim = GrabbyMutationGuard.claim(world.levelIdentity(), root)) {
            if (!claim.held()) {
                return GrabbyPickupResult.refused(GrabbyPickupOutcome.ALREADY_IN_PROGRESS, root);
            }
            return subObject.isPresent()
                    ? executeSubObject(world, actor, root, subObject.getAsInt())
                    : executeClaimed(world, actor, root);
        }
    }

    /**
     * Carries away one object out of several sharing a position.
     *
     * <p>The same gates as an ordinary pickup, asked in the same order - what it is, who put it there,
     * whether the player can reach it, what policy says, and finally the object's own reasons - and
     * then one step instead of two. An ordinary object gives up its contents and is removed
     * separately; a sub-object is handed over whole, so there is no moment when its contents exist in
     * both the world and the carried item.
     */
    private static GrabbyPickupResult executeSubObject(
            GrabbyWorld world, GrabbyActor actor, BlockPos root, int subObjectId) {

        BlockState state = world.blockState(root);
        if (state == null || state.isAir()) {
            return GrabbyPickupResult.refused(GrabbyPickupOutcome.NOTHING_THERE, root);
        }
        // Admitted for holding objects a player can address one at a time, not for being movable
        // itself: a column is never carried or placed as a column, only the crates inside it are.
        if (!world.hostsSubObjects(root)) {
            return GrabbyPickupResult.refused(GrabbyPickupOutcome.TYPE_NOT_ENROLLED, root);
        }
        GrabbyInstanceState provenance = world.grabbyState(root);
        if (!provenance.grabbyManaged()) {
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
        Optional<GrabbyTransportRefusal> refusal = world.subObjectRefusal(root, subObjectId);
        if (refusal.isPresent()) {
            return GrabbyPickupResult.refused(switch (refusal.get()) {
                case IN_USE -> GrabbyPickupOutcome.IN_USE;
                case NESTED_CONTAINER -> GrabbyPickupOutcome.NESTED_CONTAINER;
            }, root);
        }

        // Asked for before it is taken, so a full inventory does not cost the player the crate.
        Optional<ItemStack> peek = world.peekSubObject(root, subObjectId);
        if (peek.isEmpty()) {
            return GrabbyPickupResult.refused(GrabbyPickupOutcome.NOTHING_THERE, root);
        }
        if (!actor.hasRoomFor(peek.get())) {
            return GrabbyPickupResult.refused(GrabbyPickupOutcome.INVENTORY_FULL, root);
        }

        Optional<ItemStack> carried = world.takeSubObject(root, subObjectId);
        if (carried.isEmpty()) {
            // Somebody else reached it first. Nothing was taken and nothing is owed.
            return GrabbyPickupResult.refused(GrabbyPickupOutcome.NOTHING_THERE, root);
        }
        // Handing it over empties the stack, so what was carried is recorded before it is given.
        ItemStack taken = carried.get().copy();
        actor.give(carried.get());
        world.playWorldSound(root, SoundEvents.ITEM_PICKUP);
        return new GrabbyPickupResult(GrabbyPickupOutcome.SUCCESS, root, taken, 0, 0, false, false);
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
