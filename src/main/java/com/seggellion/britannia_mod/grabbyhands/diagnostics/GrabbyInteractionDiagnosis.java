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
import com.seggellion.britannia_mod.mixin.ServerGamePacketListenerAccessorMixin;
import com.seggellion.britannia_mod.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Walks one hypothetical sneak-and-right-click through every gate it must survive, in the order the
 * server applies them, and names the first that refuses.
 *
 * <h2>Why this exists rather than more logging</h2>
 *
 * <p>The previous milestone made every Grabby refusal speak, and the feature was still reported as
 * doing nothing. That is only possible when the refusal happens somewhere Grabby cannot speak from -
 * above it, in the packet layer, where the interaction is dropped before
 * {@code PlayerInteractEvent.RightClickBlock} is ever posted. A handler cannot report a refusal it
 * never hears about, so the report has to be assembled from outside the handler.
 *
 * <p>The gates below are the real ones, in the real order:
 *
 * <ol>
 *   <li><b>context</b> - who and where, so the reader can tell whether the right player was
 *       measured. An operator diagnosing themselves is the single easiest way to get a false pass.</li>
 *   <li><b>packet</b> - {@code ServerGamePacketListenerImpl.handleUseItemOn}: feature flags, reach,
 *       hit-vector sanity, build height, pending teleport, world border, spawn protection. Every one
 *       of these drops the packet silently.</li>
 *   <li><b>useItemOn</b> - the block's feature-flag check, then NeoForge posts the event, which is
 *       the first line of code Grabby Hands owns.</li>
 *   <li><b>gesture</b> - {@code GrabbyInteractionHandler} deciding what the click meant.</li>
 *   <li><b>transaction</b> - enrollment, provenance, reach, policy, transport, payload, inventory.</li>
 * </ol>
 *
 * <p>Strictly read-only. No claim is taken, no block is touched, no item moves, and the same query
 * run twice gives the same answer.
 */
public final class GrabbyInteractionDiagnosis {

    /** A single gate: which layer asked, what it asked, what the server answered. */
    public record Gate(String layer, String question, String answer, boolean refuses, boolean informational) {

        static Gate fact(String layer, String question, String answer) {
            return new Gate(layer, question, answer, false, true);
        }

        static Gate check(String layer, String question, boolean passed, String answer) {
            return new Gate(layer, question, answer, !passed, false);
        }

        @Override
        public String toString() {
            String marker = informational ? "         " : (refuses ? "REFUSED  " : "ok       ");
            return marker + layer + " | " + question + " = " + answer;
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

    /** The one line to read: either the blocking gate, or that nothing blocks. */
    public String verdict() {
        return firstRefusal()
                .map(gate -> "FIRST REFUSING GATE: " + gate.layer() + " / " + gate.question())
                .orElse("ALL PRECONDITIONS PASS");
    }

    /**
     * Evaluates the pickup gesture for {@code player} against {@code clickedPos} as the state stands
     * right now.
     *
     * <p>Posture and hand contents are reported as they actually are rather than assumed, because
     * "I was sneaking" is exactly the kind of claim that turns out to be false on the server side.
     */
    public static GrabbyInteractionDiagnosis pickup(ServerLevel level, ServerPlayer player, BlockPos clickedPos) {
        List<Gate> gates = new ArrayList<>();
        GrabbyWorld world = GrabbyWorld.of(level);
        BlockPos root = GrabbyRootResolver.resolveRoot(world, clickedPos);
        BlockState clickedState = level.getBlockState(clickedPos);
        BlockState rootState = level.getBlockState(root);
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        // ---- who and where -----------------------------------------------------------------------
        int permission = GrabbyPolicy.effectivePermissionLevel(player);
        gates.add(Gate.fact("context", "player",
                player.getGameProfile().getName() + " " + player.getUUID()));
        gates.add(Gate.fact("context", "game mode",
                String.valueOf(player.gameMode.getGameModeForPlayer())));
        // Administrator standing is reported because it changes two things and is easy to overlook
        // when staff diagnose themselves: vanilla spawn protection exempts operators outright, and
        // GrabbyPolicy lets staff act inside a structure they do not own. It does NOT let staff take
        // authored scenery - the transaction refuses on provenance before policy is consulted.
        gates.add(Gate.fact("context", "administrator",
                "creative=" + player.isCreative()
                        + " effectivePermissionLevel=" + permission
                        + " isAdministrator=" + GrabbyPolicy.isAdministrator(player)
                        + (GrabbyPolicy.isAdministrator(player)
                                ? "  <-- NOTE: staff are exempt from spawn protection; diagnose the "
                                  + "affected non-operator instead"
                                : "")));
        gates.add(Gate.fact("context", "dimension", level.dimension().location().toString()));
        gates.add(Gate.fact("context", "player position", format(player.position())));
        gates.add(Gate.fact("context", "target position",
                clickedPos.toShortString()
                        + (root.equals(clickedPos) ? "" : " (multiblock root " + root.toShortString() + ")")));
        gates.add(Gate.fact("context", "distance to target",
                String.format("%.2f blocks", Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(clickedPos))))));
        gates.add(Gate.fact("context", "sneaking (server-side flag)",
                Boolean.toString(player.isShiftKeyDown())));
        gates.add(Gate.fact("context", "main hand", describe(mainHand)));
        gates.add(Gate.fact("context", "off hand", describe(offHand)));
        gates.add(Gate.fact("context", "block",
                BuiltInRegistries.BLOCK.getKey(rootState.getBlock()).toString()));

        // ---- server-side tag enrollment, printed as facts before it is judged ---------------------
        gates.add(Gate.fact("tags", ModTags.Blocks.GRABBY_MOVABLE.location().toString(),
                Boolean.toString(rootState.is(ModTags.Blocks.GRABBY_MOVABLE))));
        gates.add(Gate.fact("tags", ModTags.Blocks.GRABBY_AXE_DESTROYABLE.location().toString(),
                Boolean.toString(rootState.is(ModTags.Blocks.GRABBY_AXE_DESTROYABLE))));
        gates.add(Gate.fact("tags", ModTags.Blocks.GRABBY_DEED_PLACED.location().toString(),
                Boolean.toString(rootState.is(ModTags.Blocks.GRABBY_DEED_PLACED))
                        + " (a deed fixture is vetoed even if it is also movable)"));

        // ---- 1. the packet layer, which Grabby Hands never hears from -----------------------------
        boolean itemEnabled = mainHand.isItemEnabled(level.enabledFeatures());
        gates.add(Gate.check("packet", "held item enabled by the level feature flags", itemEnabled,
                itemEnabled ? "yes" : "NO"));

        boolean packetReach = player.canInteractWithBlock(clickedPos, 1.0);
        gates.add(Gate.check("packet", "server-side reach (canInteractWithBlock)", packetReach,
                packetReach ? "yes" : "NO - the server's copy of the player is too far away"));

        boolean hitVectorSane = hitVectorWithinTolerance(player, clickedPos);
        gates.add(Gate.check("packet", "hit vector within one block of the target centre", hitVectorSane,
                hitVectorSane ? "yes" : "NO - the aim point is not on this block"));

        boolean belowCeiling = clickedPos.getY() < level.getMaxBuildHeight();
        gates.add(Gate.check("packet", "below build height", belowCeiling,
                belowCeiling ? "yes" : "NO"));

        Vec3 awaiting = awaitingTeleport(player);
        gates.add(Gate.check("packet", "not waiting on a teleport acknowledgement", awaiting == null,
                awaiting == null
                        ? "yes"
                        : "NO - awaitingPositionFromClient=" + format(awaiting)
                          + "; every use packet is discarded until the client acknowledges"));

        boolean insideBorder = level.getWorldBorder().isWithinBounds(clickedPos);
        gates.add(Gate.check("packet", "inside the world border", insideBorder,
                insideBorder ? "yes" : "NO"));

        boolean spawnProtected = GrabbySpawnProtection.blocksInteraction(level, player, clickedPos);
        gates.add(Gate.check("packet", "vanilla spawn protection allows this position", !spawnProtected,
                spawnProtected
                        ? "NO - inside spawn-protection radius "
                          + GrabbySpawnProtection.radius(level.getServer())
                          + " of world spawn " + level.getSharedSpawnPos().toShortString()
                          + " (distance " + GrabbySpawnProtection.chebyshevDistanceToSpawn(
                                  clickedPos, level.getSharedSpawnPos())
                          + "); the server drops the use packet and never posts RightClickBlock"
                        : "yes"));

        // ---- 2. useItemOn, up to the point NeoForge posts the event -------------------------------
        boolean blockEnabled = clickedState.getBlock().isEnabled(level.enabledFeatures());
        gates.add(Gate.check("useItemOn", "block enabled by the level feature flags", blockEnabled,
                blockEnabled ? "yes" : "NO"));
        gates.add(Gate.fact("event", "GrabbyInteractionHandler registration",
                "NeoForge.EVENT_BUS, priority HIGHEST, main hand only"));

        // ---- 3. gesture recognition ---------------------------------------------------------------
        boolean crouching = player.isShiftKeyDown();
        gates.add(Gate.check("gesture", "server sees the player crouching", crouching,
                crouching ? "yes" : "NO - the server-side shift flag is not set"));
        boolean handsEmpty = mainHand.isEmpty() && offHand.isEmpty();
        gates.add(Gate.check("gesture", "both hands empty", handsEmpty,
                handsEmpty ? "yes" : "NO - main=" + describe(mainHand) + " off=" + describe(offHand)));
        boolean decorator = GrabbyGesture.deferToDecoratorTool(mainHand, offHand);
        gates.add(Gate.check("gesture", "not deferring to the interior decorator tool", !decorator,
                decorator ? "NO - decorator tool held; Grabby Hands stands aside" : "yes"));
        boolean pickupGesture = GrabbyGesture.isPickupGesture(crouching, mainHand, offHand);
        gates.add(Gate.check("gesture", "reads as a pickup gesture", pickupGesture,
                pickupGesture ? "yes" : "NO"));

        // ---- 4. the transaction's own checks, in its own order ------------------------------------
        boolean movableType = GrabbyEligibility.movableType(rootState);
        gates.add(Gate.check("enrollment", "type may be moved by Grabby Hands", movableType,
                movableType ? "yes" : "NO - not in grabby_movable, or vetoed by grabby_deed_placed"));

        GrabbyInstanceState provenance = GrabbyProvenanceAccess.read(level, root);
        boolean managed = provenance.grabbyManaged();
        gates.add(Gate.check("provenance", "this exact block was placed by a player", managed,
                managed
                        ? "yes (placer=" + provenance.placerUuid().map(Object::toString).orElse("redacted")
                          + ", gameTime=" + provenance.placedAtGameTime() + ")"
                        : "NO - " + provenance.provenance()
                          + "; authored scenery and creative-placed blocks are protected by default"));

        boolean grabbyReach = player.canInteractWithBlock(root, 1.0);
        gates.add(Gate.check("transaction", "reach to the resolved root", grabbyReach,
                grabbyReach ? "yes" : "NO"));

        boolean foreign = GrabbyPolicy.insideForeignStructure(root, player);
        gates.add(Gate.fact("policy", "inside a registered structure this player does not own",
                foreign ? "YES" : "no"));
        boolean policyAllows = GrabbyPolicy.mayMutate(
                managed, player.isCreative(), permission, foreign, GrabbyMutationReason.PICKUP);
        gates.add(Gate.check("policy", "protection authorization permits the pickup", policyAllows,
                policyAllows ? "yes" : "NO"));

        Optional<GrabbyTransportRefusal> refusal = world.transportRefusal(root);
        gates.add(Gate.check("transport", "the object itself permits transport", refusal.isEmpty(),
                refusal.map(value -> "NO - " + value).orElse("yes")));

        ItemStack portable = world.capturePortableStack(root);
        boolean hasPortable = portable != null && !portable.isEmpty();
        gates.add(Gate.check("transaction", "the object has a portable item form", hasPortable,
                hasPortable ? describe(portable) : "NO"));

        boolean room = hasPortable && hasRoomFor(player, portable);
        gates.add(Gate.check("inventory", "the pack has room", !hasPortable || room,
                hasPortable ? (room ? "yes" : "NO - pack full") : "n/a"));

        return new GrabbyInteractionDiagnosis(gates, clickedPos, root);
    }

    /**
     * The same tolerance {@code handleUseItemOn} applies to the aim point.
     *
     * <p>Reconstructed from where the player is actually looking, so a click at a grazing angle that
     * production would reject is rejected here too.
     */
    private static boolean hitVectorWithinTolerance(ServerPlayer player, BlockPos pos) {
        if (!(player.pick(20.0D, 0.0F, false)
                instanceof net.minecraft.world.phys.BlockHitResult hit)) {
            return false;
        }
        Vec3 offset = hit.getLocation().subtract(Vec3.atCenterOf(pos));
        double tolerance = 1.0000001;
        return Math.abs(offset.x()) < tolerance
                && Math.abs(offset.y()) < tolerance
                && Math.abs(offset.z()) < tolerance;
    }

    /**
     * The server's pending-teleport position, or null when it is not waiting for one.
     *
     * <p>Read through an accessor mixin because the field is private and there is no vanilla getter.
     * A connection is not guaranteed to exist for every {@code ServerPlayer} a test or a command can
     * reach, so a missing one reports "not waiting" rather than failing the whole diagnosis.
     */
    private static Vec3 awaitingTeleport(ServerPlayer player) {
        if (player.connection instanceof ServerGamePacketListenerAccessorMixin accessor) {
            return accessor.britannia$awaitingPositionFromClient();
        }
        return null;
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

    private static String format(Vec3 vec) {
        return String.format("%.2f, %.2f, %.2f", vec.x, vec.y, vec.z);
    }

    private static String describe(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? "empty"
                : stack.getCount() + "x " + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}
