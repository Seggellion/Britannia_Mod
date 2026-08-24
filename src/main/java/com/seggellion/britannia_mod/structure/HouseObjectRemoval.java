package com.seggellion.britannia_mod.structure;

import com.seggellion.britannia_mod.grabbyhands.GrabbyActor;
import com.seggellion.britannia_mod.grabbyhands.GrabbyEligibility;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPickupOutcome;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPickupResult;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPickupTransaction;
import com.seggellion.britannia_mod.grabbyhands.GrabbyProvenanceAccess;
import com.seggellion.britannia_mod.grabbyhands.GrabbyWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Taking a stateful object out of a house without losing what it was carrying.
 *
 * <h2>The hole this closes</h2>
 *
 * <p>Once an owner can break blocks in their own house, the ordinary break pipeline decides what
 * comes back, and for this mod's furniture that answer was wrong twice over. Most of these blocks
 * have no loot table at all, so breaking one returned nothing whatsoever; and where a drop did
 * exist it was a fresh item, so a wine bottle came back blank and a chest came back empty while its
 * contents spilled on the floor as a separate pile.
 *
 * <h2>Why it delegates rather than duplicating</h2>
 *
 * <p>"Take this object out of the world with its whole state, exactly once" is already solved, by
 * {@link GrabbyPickupTransaction}: it claims the object so two removals cannot race, captures the
 * portable form through the block's own clone-stack path plus
 * {@code GrabbyPortableState.writePortableState}, detaches the payload so {@code onRemove} cannot
 * spill a second copy, removes the block drop-free, and only then inserts. A container's contents
 * therefore travel inside the container item and nowhere else, which is what makes duplication
 * impossible rather than merely unlikely.
 *
 * <p>So a house removal of an enrolled object <em>is</em> that transaction, reached from the break
 * pipeline instead of from a right-click. Nothing about the rules changes: the transaction still
 * refuses an object somebody has open, a nested container, an object out of reach, and one the
 * player's inventory has no room for — and in every one of those cases the block simply stays where
 * it is, which is a far better outcome than a break that destroys it.
 *
 * <p>Blocks Grabby Hands does not own — the walls, floors and doors an owner actually remodels with
 * — are left entirely alone and break through the ordinary pipeline exactly as before.
 */
public final class HouseObjectRemoval {

    private HouseObjectRemoval() {
    }

    /**
     * Whether this block is one whose removal must carry its state.
     *
     * <p>Enrolled type <em>and</em> player provenance. A structure-placed chair is scenery the house
     * shipped with; it is not the owner's possession and the transaction would refuse it anyway, so
     * asking here keeps the break pipeline from being diverted for nothing.
     */
    public static boolean isStatefulHouseObject(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return GrabbyEligibility.movableType(state)
                && GrabbyProvenanceAccess.grabbyManaged(level, pos);
    }

    /**
     * Takes the object at {@code pos} into {@code player}'s inventory instead of breaking it.
     *
     * @return {@code true} if the break should be cancelled, which is every case: either the object
     *         was taken, or it was refused and must be left standing
     */
    public static boolean take(ServerLevel level, ServerPlayer player, BlockPos pos) {
        GrabbyPickupResult result = GrabbyPickupTransaction.execute(
                GrabbyWorld.of(level), GrabbyActor.of(player), pos);

        if (!result.succeeded() && result.outcome() != GrabbyPickupOutcome.DROPPED_AT_FEET) {
            String messageKey = result.outcome().refusalMessageKey();
            if (messageKey != null) {
                player.displayClientMessage(Component.translatable(messageKey), true);
            }
        }
        return true;
    }
}
