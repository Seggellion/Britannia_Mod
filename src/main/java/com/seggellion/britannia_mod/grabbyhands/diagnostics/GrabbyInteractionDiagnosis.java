package com.seggellion.britannia_mod.grabbyhands.diagnostics;

import com.seggellion.britannia_mod.grabbyhands.GrabbyEligibility;
import com.seggellion.britannia_mod.grabbyhands.GrabbyGesture;
import com.seggellion.britannia_mod.grabbyhands.GrabbyInstanceState;
import com.seggellion.britannia_mod.grabbyhands.GrabbyMutationReason;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPolicy;
import com.seggellion.britannia_mod.grabbyhands.GrabbyProvenanceAccess;
import com.seggellion.britannia_mod.grabbyhands.GrabbyRootResolver;
import com.seggellion.britannia_mod.grabbyhands.GrabbyTransportRefusal;
import com.seggellion.britannia_mod.grabbyhands.GrabbyWorld;
import com.seggellion.britannia_mod.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Walks one hypothetical sneak-and-right-click through every gate it must survive, in the order the
 * server applies them, and reports the first one that refuses.
 *
 * <h2>Why this exists rather than more logging</h2>
 *
 * <p>The previous milestone made every Grabby refusal speak, and the feature was still reported as
 * doing nothing. That is only possible when the refusal happens somewhere Grabby cannot speak from -
 * above it, in the packet layer, where the interaction is dropped before
 * {@code PlayerInteractEvent.RightClickBlock} is ever posted. A handler cannot report a refusal it
 * never hears about, so the report has to be assembled from outside the handler. That is this class.
 *
 * <p>The gates below are the real ones, in the real order:
 *
 * <ol>
 *   <li>{@code ServerGamePacketListenerImpl.handleUseItemOn} - spawn protection, world border,
 *       server-side reach, build height. Every one of these drops the packet silently.</li>
 *   <li>{@code ServerPlayerGameMode.useItemOn} - the feature-flag check, then NeoForge posts the
 *       event, which is the first line of code Grabby Hands owns.</li>
 *   <li>{@code GrabbyInteractionHandler} - gesture recognition.</li>
 *   <li>{@code GrabbyPickupTransaction} - enrollment, provenance, reach, policy, transport, payload,
 *       inventory room.</li>
 * </ol>
 *
 * <p>Strictly read-only. No claim is taken, no block is touched, no item moves. Running this can
 * never change the situation it is describing.
 */
public final class GrabbyInteractionDiagnosis {

    /** A single gate: what was asked, what the server answered, and whether that ends the story. */
    public record Gate(String layer, String question, String answer, boolean refuses) {
        @Override
        public String toString() {
            return (refuses ? "REFUSED " : "ok       ") + layer + " | " + question + " = " + answer;
        }
    }

    private final List<Gate> gates;
    private final BlockPos clicked;
    private final BlockPos root;

    private GrabbyInteractionDiagnosis(List<Gate> gates, BlockPos clicked, BlockPos root) {
        this.gates = List.copyOf(gates);
        this.clicked = clicked;
        this.root = root;
    }

    public List<Gate> gates() {
        return gates;
    }

    public BlockPos clickedPos() {
        return clicked;
    }

    public BlockPos rootPos() {
        return root;
    }

    /** The first gate that refuses, which is the only one worth acting on. */
    public Optional<Gate> firstRefusal() {
        return gates.stream().filter(Gate::refuses).findFirst();
    }

    public boolean wouldSucceed() {
        return firstRefusal().isEmpty();
    }

    /**
     * Evaluates the pickup gesture for {@code player} against {@code clickedPos}, as if the player
     * were sneaking with both hands empty at this instant.
     *
     * <p>Posture and hand contents are reported as they actually are rather than assumed, because
     * "I was sneaking" is exactly the kind of claim that turns out to be false on the server side.
     */
    public static GrabbyInteractionDiagnosis pickup(ServerLevel level, ServerPlayer player, BlockPos clickedPos) {
        List<Gate> gates = new ArrayList<>();
        GrabbyWorld world = GrabbyWorld.of(level);
        BlockPos root = GrabbyRootResolver.resolveRoot(world, clickedPos);

        // ---- 1. the packet layer, which Grabby Hands never hears from ----------------------------
        boolean spawnProtected = GrabbySpawnProtection.blocksInteraction(level, player, clickedPos);
        gates.add(new Gate("packet", "vanilla spawn protection allows this position",
                spawnProtected
                        ? "NO - inside spawn-protection radius " + GrabbySpawnProtection.radius(level.getServer())
                          + "; the server drops the use packet and never posts RightClickBlock"
                        : "yes",
                spawnProtected));

        boolean insideBorder = level.getWorldBorder().isWithinBounds(clickedPos);
        gates.add(new Gate("packet", "inside the world border", insideBorder ? "yes" : "NO", !insideBorder));

        boolean packetReach = player.canInteractWithBlock(clickedPos, 1.0);
        gates.add(new Gate("packet", "server-side reach (canInteractWithBlock)",
                packetReach ? "yes" : "NO - the server's copy of the player is too far away", !packetReach));

        boolean belowCeiling = clickedPos.getY() < level.getMaxBuildHeight();
        gates.add(new Gate("packet", "below build height", belowCeiling ? "yes" : "NO", !belowCeiling));

        // ---- 2. useItemOn, up to the point NeoForge posts the event ------------------------------
        BlockState state = level.getBlockState(clickedPos);
        boolean enabled = state.getBlock().isEnabled(level.enabledFeatures());
        gates.add(new Gate("useItemOn", "block is enabled by the level's feature flags",
                enabled ? "yes" : "NO", !enabled));

        gates.add(new Gate("event", "GrabbyInteractionHandler is registered on this server",
                "yes (NeoForge.EVENT_BUS, priority HIGHEST, main hand only)", false));

        // ---- 3. gesture recognition --------------------------------------------------------------
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean crouching = player.isShiftKeyDown();
        gates.add(new Gate("gesture", "server sees the player crouching",
                crouching ? "yes" : "NO - the server-side shift flag is not set", !crouching));
        boolean handsEmpty = mainHand.isEmpty() && offHand.isEmpty();
        gates.add(new Gate("gesture", "both hands empty",
                handsEmpty ? "yes" : "NO - main=" + describe(mainHand) + " off=" + describe(offHand),
                !handsEmpty));
        boolean decorator = GrabbyGesture.deferToDecoratorTool(mainHand, offHand);
        gates.add(new Gate("gesture", "not deferring to the interior decorator tool",
                decorator ? "NO - decorator tool held; Grabby Hands stands aside" : "yes", decorator));
        boolean pickupGesture = GrabbyGesture.isPickupGesture(crouching, mainHand, offHand);
        gates.add(new Gate("gesture", "reads as a pickup gesture",
                pickupGesture ? "yes" : "NO", !pickupGesture));

        // ---- 4. the transaction's own checks, in its own order ------------------------------------
        BlockState rootState = level.getBlockState(root);
        gates.add(new Gate("world", "block at the resolved root",
                BuiltInRegistries.BLOCK.getKey(rootState.getBlock()) + " at " + root.toShortString(), false));

        gates.add(new Gate("tags", "server evaluates " + ModTags.Blocks.GRABBY_MOVABLE.location(),
                Boolean.toString(rootState.is(ModTags.Blocks.GRABBY_MOVABLE)), false));
        gates.add(new Gate("tags", "server evaluates " + ModTags.Blocks.GRABBY_AXE_DESTROYABLE.location(),
                Boolean.toString(rootState.is(ModTags.Blocks.GRABBY_AXE_DESTROYABLE)), false));
        boolean deedPlaced = GrabbyEligibility.deedPlaced(rootState);
        gates.add(new Gate("tags", "server evaluates " + ModTags.Blocks.GRABBY_DEED_PLACED.location(),
                Boolean.toString(deedPlaced), false));

        boolean movableType = GrabbyEligibility.movableType(rootState);
        gates.add(new Gate("enrollment", "type may be moved by Grabby Hands",
                movableType ? "yes" : "NO - not in grabby_movable, or vetoed by grabby_deed_placed",
                !movableType));

        GrabbyInstanceState provenance = GrabbyProvenanceAccess.read(level, root);
        gates.add(new Gate("provenance", "this exact block was placed by a player",
                provenance.grabbyManaged()
                        ? "yes (placer=" + provenance.placerUuid().map(Object::toString).orElse("redacted")
                          + ", gameTime=" + provenance.placedAtGameTime() + ")"
                        : "NO - " + provenance.provenance()
                          + "; Britannia scenery and creative-placed blocks are protected by default",
                !provenance.grabbyManaged()));

        boolean grabbyReach = player.canInteractWithBlock(root, 1.0);
        gates.add(new Gate("transaction", "reach to the resolved root",
                grabbyReach ? "yes" : "NO", !grabbyReach));

        boolean foreign = GrabbyPolicy.insideForeignStructure(root, player);
        int permission = GrabbyPolicy.effectivePermissionLevel(player);
        gates.add(new Gate("policy", "inside a registered structure this player does not own",
                foreign ? "YES" : "no", false));
        gates.add(new Gate("policy", "player standing",
                "gameMode=" + player.gameMode.getGameModeForPlayer()
                        + " creative=" + player.isCreative()
                        + " effectivePermissionLevel=" + permission, false));
        boolean policyAllows = GrabbyPolicy.mayMutate(
                provenance.grabbyManaged(), player.isCreative(), permission, foreign, GrabbyMutationReason.PICKUP);
        gates.add(new Gate("policy", "policy permits the pickup",
                policyAllows ? "yes" : "NO", !policyAllows));

        Optional<GrabbyTransportRefusal> refusal = world.transportRefusal(root);
        gates.add(new Gate("transport", "the object itself permits transport",
                refusal.map(value -> "NO - " + value).orElse("yes"), refusal.isPresent()));

        ItemStack portable = world.capturePortableStack(root);
        boolean hasPortable = portable != null && !portable.isEmpty();
        gates.add(new Gate("transaction", "the object has a portable item form",
                hasPortable ? describe(portable) : "NO", !hasPortable));

        boolean room = hasPortable && hasRoomFor(player, portable);
        gates.add(new Gate("inventory", "the pack has room",
                hasPortable ? (room ? "yes" : "NO - pack full") : "n/a", hasPortable && !room));

        return new GrabbyInteractionDiagnosis(gates, clickedPos, root);
    }

    /**
     * Mirrors {@code GrabbyActor.ServerGrabbyActor.hasRoomFor} without going through the actor,
     * because building an actor to ask one question would imply the transaction is being run.
     */
    private static boolean hasRoomFor(ServerPlayer player, ItemStack stack) {
        if (player.getInventory().getFreeSlot() >= 0) {
            return true;
        }
        if (stack.getMaxStackSize() <= 1) {
            return false;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (!existing.isEmpty()
                    && existing.getCount() < existing.getMaxStackSize()
                    && ItemStack.isSameItemSameComponents(existing, stack)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? "empty"
                : stack.getCount() + "x " + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}
