package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.seggellion.britannia_mod.grabbyhands.destruction.GrabbyDestructionService;
import com.seggellion.britannia_mod.crate.CrateFoundation;
import com.seggellion.britannia_mod.crate.CrateStackTargetResolver;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
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
        if (GrabbyGesture.offHandBlocksPickup(
                player.isShiftKeyDown(), player.getMainHandItem(), player.getOffhandItem())) {
            explainOccupiedOffHand(level, player, event.getPos());
            // Deliberately falls through rather than returning. Nothing is consumed, so whatever the
            // off-hand item does against this block still happens exactly as it did before.
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
        // Which crate the player meant is decided by height when one is standing on another, because
        // the upper crate's body is physically inside the lower crate's cells and a position alone
        // would always name the lower one.
        BlockPos aimedAtCrate = CrateFoundation.crateRootAt(
                level, event.getPos(), level.getBlockState(event.getPos()),
                event.getHitVec().getLocation());
        // A compact column is several crates a player points at individually, sharing one position
        // and one block entity. Naming the crate they meant is the same question the menus and
        // breaking already ask, so it goes to the same resolver rather than being worked out again.
        Optional<CrateStackTargetResolver.Target> aimedCrate =
                CrateStackTargetResolver.resolve(level, event.getHitVec());
        GrabbyPickupResult result = aimedCrate
                .map(target -> GrabbyPickupTransaction.execute(
                        GrabbyWorld.of(level), GrabbyActor.of(player), target.root(),
                        OptionalInt.of(target.crateId())))
                .orElseGet(() -> GrabbyPickupTransaction.execute(
                        GrabbyWorld.of(level), GrabbyActor.of(player), aimedAtCrate));

        if (!result.outcome().handled()) {
            // Not a Grabby object, or not a player-placed one. Leave the interaction completely alone
            // so the block's own behaviour still runs - a sneaking empty-handed player must still be
            // able to interact normally with everything this system does not own.
            //
            // One thing is said rather than nothing: a player who made the deliberate pickup gesture
            // at an object of an enrolled type, and was refused because that particular one belongs
            // to Britannia rather than to them, otherwise sees exactly what a dead feature looks
            // like. This is the single most common way "grabby hands does nothing" is reported. The
            // interaction is still not consumed, so the block's own behaviour is untouched.
            if (result.outcome() == GrabbyPickupOutcome.NOT_GRABBY_MANAGED) {
                explain(player, "message.britannia_mod.grabby.pickup.not_yours");
            }
            return;
        }
        explain(player, result.outcome().refusalMessageKey());
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

    /**
     * Tells a player whose only mistake was the hand they were not looking at.
     *
     * <p>Scoped as narrowly as the message is useful. It speaks only when the object under the
     * cursor is one this player could actually have picked up — an enrolled type, placed by a
     * player — so an occupied off hand stays silent against scenery, against blocks Grabby Hands
     * does not own, and against thin air. Sneaking with a torch in the off hand is an ordinary thing
     * to do and must not produce a running commentary.
     *
     * <p>Provenance is read directly rather than by running the pickup transaction: nothing may be
     * claimed, captured or mutated to produce a hint.
     */
    private void explainOccupiedOffHand(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockPos root = GrabbyRootResolver.resolveRoot(GrabbyWorld.of(level), pos);
        if (!GrabbyEligibility.movableType(level.getBlockState(root))
                || !GrabbyProvenanceAccess.grabbyManaged(level, root)) {
            return;
        }
        explain(player, "message.britannia_mod.grabby.pickup.off_hand_occupied");
    }

    /**
     * Says why, above the hotbar, when a refusal is one the player can do something about.
     *
     * <p>Refusals that mean "this is not Grabby content" say nothing and never reach here: the
     * object's own behaviour runs instead, and announcing a system the player did not invoke would
     * be noise. Everything else used to be equally silent, which is the single reason a refused
     * pickup is reported as the feature being broken.
     */
    private void explain(ServerPlayer player, String messageKey) {
        if (messageKey != null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(messageKey), true);
        }
    }

    private void consume(PlayerInteractEvent.RightClickBlock event, boolean succeeded) {
        event.setCanceled(true);
        event.setCancellationResult(succeeded ? InteractionResult.SUCCESS : InteractionResult.FAIL);
    }
}
