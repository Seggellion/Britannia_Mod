package com.seggellion.britannia_mod.grabbyhands;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Destroys one placed object with an axe, exactly once.
 *
 * <h2>Not the break pipeline</h2>
 *
 * <p>This never touches {@code BreakEvent}, {@code PlayerEvent.BreakSpeed}, {@code AxeHarvestRules}
 * or {@code blockActionRestricted}. An axe still cannot break anything it could not break before —
 * what it gains is the ability to destroy objects Grabby Hands already owns, through Grabby Hands'
 * own transaction. That is closer to RunUO anyway, where chopping furniture was a targeting action
 * rather than mining.
 *
 * <h2>Contents versus the object itself</h2>
 *
 * <p>The two cases look similar and behave oppositely:
 *
 * <ul>
 *   <li>A <b>container's</b> contents are not the container. They spill into the world, exactly once,
 *       from the block's own {@code onRemove} — the existing safe drop helper, not a reimplementation.
 *       Nothing detaches them first, and the container itself never drops as an item.</li>
 *   <li>A <b>placed-item host's</b> payload <em>is</em> the object. Destroying it must consume the
 *       item rather than politely handing it back, so the payload is detached first and the host's
 *       {@code onRemove} finds nothing to drop.</li>
 * </ul>
 *
 * <p>Nothing here inspects a container or a host. The distinction falls out of which transport
 * interface the block entity implements.
 */
public final class GrabbyDestructionTransaction {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GrabbyDestructionTransaction() {
    }

    public static GrabbyDestructionResult execute(
            GrabbyWorld world, GrabbyActor actor, ItemStack tool, BlockPos clickedPos) {

        BlockPos root = GrabbyRootResolver.resolveRoot(world, clickedPos);

        Optional<GrabbyDestructionOutcome> refusal = refusalFor(world, actor, tool, root);
        if (refusal.isPresent()) {
            return GrabbyDestructionResult.refused(refusal.get(), root);
        }

        try (GrabbyMutationGuard.Claim claim = GrabbyMutationGuard.claim(world.levelIdentity(), root)) {
            if (!claim.held()) {
                // A pickup already owns this object, or a duplicated packet arrived. One winner.
                return GrabbyDestructionResult.refused(GrabbyDestructionOutcome.ALREADY_IN_PROGRESS, root);
            }
            // Re-checked inside the claim: the world may have moved between the prompt and the answer.
            Optional<GrabbyDestructionOutcome> stillRefused = refusalFor(world, actor, tool, root);
            if (stillRefused.isPresent()) {
                return GrabbyDestructionResult.refused(stillRefused.get(), root);
            }
            return executeClaimed(world, actor, root);
        }
    }

    /**
     * Every reason this destruction would be refused, without touching anything.
     *
     * <p>Shared by the confirmation prompt and the destruction itself, so a player is never asked a
     * question whose answer could not be honoured. Confirmation is an accident guard, not an
     * authorisation step: policy runs first, the prompt second.
     *
     * @return the refusal, or empty if the destruction would proceed
     */
    public static Optional<GrabbyDestructionOutcome> refusalFor(
            GrabbyWorld world, GrabbyActor actor, ItemStack tool, BlockPos root) {

        if (!GrabbyAxes.isAxe(tool)) {
            return Optional.of(GrabbyDestructionOutcome.NOT_AN_AXE);
        }
        BlockState state = world.blockState(root);
        if (state == null || state.isAir()) {
            return Optional.of(GrabbyDestructionOutcome.NOTHING_THERE);
        }
        if (!GrabbyEligibility.axeDestroyableType(state)) {
            return Optional.of(GrabbyDestructionOutcome.TYPE_NOT_ENROLLED);
        }
        GrabbyInstanceState provenance = world.grabbyState(root);
        if (!provenance.grabbyManaged()) {
            return Optional.of(GrabbyDestructionOutcome.NOT_GRABBY_MANAGED);
        }
        if (!actor.canReach(root)) {
            return Optional.of(GrabbyDestructionOutcome.OUT_OF_REACH);
        }
        if (!GrabbyPolicy.mayMutate(
                provenance.grabbyManaged(),
                actor.isCreative(),
                actor.permissionLevel(),
                actor.insideForeignStructure(root),
                GrabbyMutationReason.AXE_DESTROY)) {
            return Optional.of(GrabbyDestructionOutcome.DENIED_BY_POLICY);
        }
        if (world.transportRefusal(root).isPresent()) {
            // A container somebody has open, for instance. Destroying it under a viewer is no better
            // than picking it up under one.
            return Optional.of(GrabbyDestructionOutcome.REFUSED_BY_OBJECT);
        }
        if (world.securedAgainstDestruction(root)) {
            // Chopping a locked chest open would spill its contents with no key, no lockpicks and no
            // skill check, leaving the lock system decorative. Carrying it away is still allowed: the
            // lock protects the contents, not the box.
            return Optional.of(GrabbyDestructionOutcome.SECURED);
        }
        return Optional.empty();
    }

    private static GrabbyDestructionResult executeClaimed(GrabbyWorld world, GrabbyActor actor, BlockPos root) {
        BlockState state = world.blockState(root);

        // A host's payload is the object; destroying the object destroys it. A container's contents
        // are not the object and are deliberately left attached so onRemove spills them.
        world.detachObjectPayloadForDestruction(root);

        if (!world.removeObject(root)) {
            LOGGER.error("[grabby-hands] Axe destruction could not remove the object at {}", root);
            return GrabbyDestructionResult.refused(GrabbyDestructionOutcome.REMOVAL_FAILED, root);
        }

        // Only after the object is actually gone. A cue for a destruction that did not happen would
        // be a lie, in the same way a stow cue for a failed insertion would be.
        world.playDestructionEffect(root, state);
        boolean toolDamaged = actor.damageTool();

        return new GrabbyDestructionResult(
                GrabbyDestructionOutcome.SUCCESS, root, 1, true, toolDamaged);
    }
}
