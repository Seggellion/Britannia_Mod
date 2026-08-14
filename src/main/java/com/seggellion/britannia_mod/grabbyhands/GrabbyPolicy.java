package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The single authority on whether Grabby Hands may move or destroy a placed object.
 *
 * <h2>What this class deliberately does not do</h2>
 *
 * <p>There is no {@code mayUse}, {@code mayInteract}, {@code mayOpen} or {@code maySit} method here,
 * and there must never be one. Grabby Hands moves objects; it does not decide who may use them.
 * Placing a chair does not make it your chair — any player can still sit on it, any player can still
 * open a placed container, and existing independent systems (the lockable chest's own lock, house
 * privacy, quest gating) remain the only things that restrict use. {@code GrabbyPolicyContractTest}
 * scans this file to keep that true.
 *
 * <h2>Mobility marks "managed", not "mine"</h2>
 *
 * <p>{@link GrabbyInstanceState#placerUuid()} is audit and diagnostics. It is intentionally
 * <em>not</em> compared against the acting player: a player-placed object is movable by any player
 * the surrounding region and house rules already permit to act there. Restricting movement to the
 * original placer would be a new ownership system, which this epic does not introduce.
 *
 * <p>Structure ownership is delegated to {@link StructureRegionManager} rather than duplicated,
 * matching how {@code StructureProtectionHandler} already decides who may modify what inside a house.
 */
public final class GrabbyPolicy {
    /** Same operator level {@code farming.FlowerProtectionService} treats as administrative. */
    public static final int ADMIN_PERMISSION_LEVEL = 2;

    private GrabbyPolicy() {
    }

    // ------------------------------------------------------------------
    // World-aware entry points
    // ------------------------------------------------------------------

    /** Whether {@code actor} may pick this exact placed object up. */
    public static boolean mayMove(
            @Nullable Player actor, BlockPos pos, GrabbyInstanceState state, GrabbyMutationReason reason) {
        return mayMutate(actor, pos, state, reason);
    }

    /** Whether {@code actor} may destroy this exact placed object. */
    public static boolean mayDestroy(
            @Nullable Player actor, BlockPos pos, GrabbyInstanceState state, GrabbyMutationReason reason) {
        return mayMutate(actor, pos, state, reason);
    }

    private static boolean mayMutate(
            @Nullable Player actor, BlockPos pos, GrabbyInstanceState state, GrabbyMutationReason reason) {
        if (state == null) {
            return false;
        }
        return mayMutate(
                state.grabbyManaged(),
                actor != null && actor.isCreative(),
                effectivePermissionLevel(actor),
                insideForeignStructure(pos, actor),
                reason);
    }

    // ------------------------------------------------------------------
    // Pure policy core - primitives only, so it unit-tests without a level or a player
    // ------------------------------------------------------------------

    /**
     * @param grabbyManaged          the instance carries {@link GrabbyProvenance#PLAYER}
     * @param creativeMode           the actor is in Creative
     * @param permissionLevel        the actor's effective operator level
     * @param insideForeignStructure the object sits inside a registered structure the actor does not own
     * @param reason                 why the object is being mutated
     */
    public static boolean mayMutate(
            boolean grabbyManaged,
            boolean creativeMode,
            int permissionLevel,
            boolean insideForeignStructure,
            @Nullable GrabbyMutationReason reason
    ) {
        if (reason == null) {
            return false;
        }
        if (reason.systemAuthorized()) {
            return true;
        }
        // Environmental forces never move or destroy a Grabby object. Player possessions left in the
        // world are not collateral for a creeper or a water bucket, and pistons are never a transport
        // mechanism for them.
        if (reason.environmental()) {
            return false;
        }
        boolean administrator = isAdministrator(creativeMode, permissionLevel);
        if (reason == GrabbyMutationReason.ADMIN_REMOVE) {
            return administrator;
        }
        // Staff may rearrange scenery; ordinary players may not, whatever the type tag says.
        if (!grabbyManaged) {
            return administrator;
        }
        // A player-placed object inside somebody else's house follows that house's existing rules.
        if (insideForeignStructure) {
            return administrator;
        }
        return true;
    }

    /**
     * Whether {@code actor} may place a Grabby object at a destination.
     *
     * <p>Placement asks a different question from movement: there is no instance yet, so there is no
     * provenance to consult. Only the destination matters — you may furnish open ground and your own
     * house, but not the inside of somebody else's.
     *
     * <p>Type enrollment is checked separately by {@link GrabbyEligibility}. This method never widens
     * what may be placed; it only narrows where.
     */
    public static boolean mayPlace(boolean creativeMode, int permissionLevel, boolean insideForeignStructure) {
        return !insideForeignStructure || isAdministrator(creativeMode, permissionLevel);
    }

    public static boolean isAdministrator(boolean creativeMode, int permissionLevel) {
        return creativeMode || permissionLevel >= ADMIN_PERMISSION_LEVEL;
    }

    public static boolean isAdministrator(@Nullable Player player) {
        return player != null
                && (player.isCreative() || effectivePermissionLevel(player) >= ADMIN_PERMISSION_LEVEL);
    }

    public static int effectivePermissionLevel(@Nullable Player player) {
        return player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(ADMIN_PERMISSION_LEVEL)
                ? ADMIN_PERMISSION_LEVEL
                : 0;
    }

    // ------------------------------------------------------------------
    // Structure delegation
    // ------------------------------------------------------------------

    /**
     * Whether {@code pos} lies inside a registered structure that {@code actor} does not own.
     *
     * <p>Deliberately conservative: an unknown actor is treated as owning nothing, so an unattributed
     * mutation inside any structure is foreign.
     *
     * <p>No dimension argument, because {@link StructureRegionManager} is itself keyed only by chunk
     * coordinates. {@code StructureProtectionHandler} has the same characteristic. Not introducing a
     * dimension parameter here avoids implying a precision the underlying registry does not have.
     */
    public static boolean insideForeignStructure(@Nullable BlockPos pos, @Nullable Player actor) {
        if (pos == null) {
            return false;
        }
        List<StructureRecord> records = StructureRegionManager.getStructuresInChunk(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
        if (records == null || records.isEmpty()) {
            return false;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        UUID actorId = actor == null ? null : actor.getUUID();
        boolean insideAny = false;
        for (StructureRecord record : records) {
            if (record.getFullBox() == null || !record.getFullBox().contains(center)) {
                continue;
            }
            insideAny = true;
            if (actorId != null && actorId.equals(record.getOwnerUuid())) {
                return false;
            }
        }
        return insideAny;
    }
}
