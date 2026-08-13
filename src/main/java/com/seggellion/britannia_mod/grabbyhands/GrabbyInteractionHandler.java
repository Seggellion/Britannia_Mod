package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.seggellion.britannia_mod.grabbyhands.destruction.GrabbyDestructionService;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The single entry point for every Grabby Hands operation.
 *
 * <h2>Why right-click, and why this is not an Adventure-mode bypass</h2>
 *
 * <p>{@code PlayerInteractEvent.RightClickBlock} is fired as the first statement of
 * {@code ServerPlayerGameMode.useItemOn} — before the spectator branch, before
 * {@code BlockState.useItemOn}/{@code useWithoutItem}, and before {@code ItemStack.useOn}, which is
 * the only place Adventure mode's {@code mayBuild} gate lives. The client sends the packet
 * unconditionally. So this hook reaches the server in Adventure mode with nothing relaxed anywhere.
 *
 * <p>Grabby Hands therefore never touches the break pipeline, never changes a game mode, never
 * enables building, and needs no mixin. An axe gains no general breaking authority from this system
 * because this system does not participate in breaking at all. City protection, house protection and
 * tree harvesting are all untouched, and {@code GrabbyAuthorizationRegressionTest} enforces that.
 *
 * <p>Priority is {@code HIGHEST} because the alternative is losing the race to the block's own
 * behaviour: right-clicking a chair seats you, and a container opens. That follows the shape
 * {@code FlowerInteractionHandler.onRightClickBlock} already established in this project.
 */
public final class GrabbyInteractionHandler {
    private static final GrabbyInteractionHandler INSTANCE = new GrabbyInteractionHandler();
    private static boolean registered;

    private GrabbyInteractionHandler() {
    }

    public static synchronized void register() {
        if (!registered) {
            NeoForge.EVENT_BUS.register(INSTANCE);
            registered = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Main hand only: the event fires once per hand, and a two-hand pickup is not a thing.
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (GrabbyGesture.deferToDecoratorTool(player.getMainHandItem(), player.getOffhandItem())) {
            return;
        }

        if (GrabbyGesture.isPickupGesture(
                player.isShiftKeyDown(), player.getMainHandItem(), player.getOffhandItem())) {
            handlePickup(event, level, player);
            return;
        }
        if (GrabbyGesture.isAxeGesture(player.getMainHandItem())) {
            handleAxe(event, level, player);
            return;
        }
        handlePlacement(event, level, player);
    }

    /**
     * An axe swung at an enrolled object asks whether to destroy it.
     *
     * <p>Nothing is destroyed here. The prompt is only issued once every policy, provenance and reach
     * check has already passed, so the confirmation is an accident guard rather than an authorisation
     * step - a protected object simply produces no question.
     *
     * <p>Suppressing normal use is what stops an axe swing at a chair seating the player instead, and
     * it applies only to enrolled destroyable blocks: everything else falls straight through.
     */
    private void handleAxe(PlayerInteractEvent.RightClickBlock event, ServerLevel level, ServerPlayer player) {
        if (!GrabbyEligibility.axeDestroyableType(level.getBlockState(event.getPos()))) {
            return;
        }
        java.util.Optional<GrabbyDestructionOutcome> refusal =
                GrabbyDestructionService.requestConfirmation(level, player, event.getPos());
        if (refusal.isPresent() && !refusal.get().handled()) {
            // A locked chest, for instance. Falling through lets LockpickingEventHandler take its
            // turn, so holding an axe does not silently disable lockpicking.
            return;
        }
        // Otherwise consumed either way: a player who swung an axe at scenery should get nothing
        // rather than a seat.
        consume(event, refusal.isEmpty());
    }

    private void handlePickup(PlayerInteractEvent.RightClickBlock event, ServerLevel level, ServerPlayer player) {
        GrabbyPickupResult result = GrabbyPickupTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(player), event.getPos());

        if (!result.outcome().handled()) {
            // Not a Grabby object, or not a player-placed one. Leave the interaction completely alone
            // so the block's own behaviour still runs - a sneaking empty-handed player must still be
            // able to interact normally with everything this system does not own.
            return;
        }
        consume(event, result.succeeded());
    }

    /**
     * Holding an enrolled object means "place it", the same way holding any block does in Minecraft.
     *
     * <p>This is also what makes furniture stacking reachable: right-clicking the top of a chair while
     * holding another chair places the second one above rather than seating the player.
     *
     * <p>Anything not enrolled falls straight through, so every other item keeps its existing
     * behaviour against every block — including the ordinary Adventure-mode refusal.
     */
    private void handlePlacement(PlayerInteractEvent.RightClickBlock event, ServerLevel level, ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (GrabbyPlacementTransaction.enrolledBlockOf(held) == null
                && !GrabbyEligibility.hostablePlainItem(held)) {
            return;
        }
        GrabbyPlacementResult result = GrabbyPlacementTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(player), held, event.getHitVec());

        if (!result.outcome().handled()) {
            return;
        }
        consume(event, result.succeeded());
    }

    private void consume(PlayerInteractEvent.RightClickBlock event, boolean succeeded) {
        event.setCanceled(true);
        event.setCancellationResult(succeeded ? InteractionResult.SUCCESS : InteractionResult.FAIL);
    }
}
