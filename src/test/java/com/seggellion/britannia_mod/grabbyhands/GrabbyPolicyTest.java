package com.seggellion.britannia_mod.grabbyhands;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Policy matrix, driven through the primitive overload so no level or player is needed — the same
 * approach {@code FlowerProtectionBypassTest} uses.
 *
 * <p>Argument order: {@code (grabbyManaged, creativeMode, permissionLevel, insideForeignStructure, reason)}.
 */
class GrabbyPolicyTest {
    private static final Set<GrabbyMutationReason> PLAYER_ACTIONS =
            EnumSet.of(GrabbyMutationReason.PICKUP, GrabbyMutationReason.AXE_DESTROY);

    @Test
    void anUnmarkedObjectIsProtectedFromOrdinaryPlayersOnEveryPlayerPath() {
        // Britannia scenery, structure-template furniture, admin decoration: all read as WORLD and
        // must refuse ordinary players even though their block type is enrolled.
        for (GrabbyMutationReason reason : PLAYER_ACTIONS) {
            assertFalse(GrabbyPolicy.mayMutate(false, false, 0, false, reason), reason.name());
            assertFalse(GrabbyPolicy.mayMutate(false, false, 1, false, reason), reason.name());
        }
    }

    @Test
    void staffMayStillRearrangeSceneryThroughCreativeOrOperatorTwo() {
        for (GrabbyMutationReason reason : PLAYER_ACTIONS) {
            assertTrue(GrabbyPolicy.mayMutate(false, true, 0, false, reason), reason.name());
            assertTrue(GrabbyPolicy.mayMutate(false, false, 2, false, reason), reason.name());
        }
    }

    @Test
    void aPlayerPlacedObjectIsMovableAndDestroyableByAnOrdinaryPlayer() {
        for (GrabbyMutationReason reason : PLAYER_ACTIONS) {
            assertTrue(GrabbyPolicy.mayMutate(true, false, 0, false, reason), reason.name());
        }
    }

    @Test
    void mobilityMarksManagedNotMineSoAnyPermittedPlayerMayMoveIt() {
        // There is no placer argument in the policy core at all. That absence is the requirement:
        // a placed object is not privatised to whoever put it down.
        assertTrue(GrabbyPolicy.mayMutate(true, false, 0, false, GrabbyMutationReason.PICKUP));
    }

    @Test
    void insideSomebodyElsesStructureOrdinaryPlayersAreRefusedButStaffAreNot() {
        for (GrabbyMutationReason reason : PLAYER_ACTIONS) {
            assertFalse(GrabbyPolicy.mayMutate(true, false, 0, true, reason), reason.name());
            assertTrue(GrabbyPolicy.mayMutate(true, true, 0, true, reason), reason.name());
            assertTrue(GrabbyPolicy.mayMutate(true, false, 2, true, reason), reason.name());
        }
    }

    @Test
    void environmentalForcesNeverMoveOrDestroyAGrabbyObjectRegardlessOfActor() {
        for (GrabbyMutationReason reason : Set.of(
                GrabbyMutationReason.EXPLOSION, GrabbyMutationReason.FLUID, GrabbyMutationReason.PISTON)) {
            assertFalse(GrabbyPolicy.mayMutate(true, false, 0, false, reason), reason.name());
            assertFalse(GrabbyPolicy.mayMutate(true, true, 4, false, reason), reason.name());
            assertFalse(GrabbyPolicy.mayMutate(false, true, 4, false, reason), reason.name());
        }
    }

    @Test
    void adminRemoveIsStaffOnlyEvenForPlayerPlacedObjects() {
        assertFalse(GrabbyPolicy.mayMutate(true, false, 0, false, GrabbyMutationReason.ADMIN_REMOVE));
        assertTrue(GrabbyPolicy.mayMutate(true, true, 0, false, GrabbyMutationReason.ADMIN_REMOVE));
        assertTrue(GrabbyPolicy.mayMutate(false, false, 2, false, GrabbyMutationReason.ADMIN_REMOVE));
    }

    @Test
    void systemAuthorizedMutationsProceed() {
        assertTrue(GrabbyPolicy.mayMutate(false, false, 0, true, GrabbyMutationReason.SYSTEM_MUTATION));
    }

    @Test
    void aMissingReasonOrMissingStateDenies() {
        assertFalse(GrabbyPolicy.mayMutate(true, true, 4, false, null));
        assertFalse(GrabbyPolicy.mayMove(null, null, null, GrabbyMutationReason.PICKUP));
        assertFalse(GrabbyPolicy.mayDestroy(null, null, null, GrabbyMutationReason.AXE_DESTROY));
    }

    // ------------------------------------------------------------------
    // Placement asks about the destination, not about an instance
    // ------------------------------------------------------------------

    @Test
    void placingOnOpenGroundOrInYourOwnHouseIsAllowed() {
        assertTrue(GrabbyPolicy.mayPlace(false, 0, false));
    }

    @Test
    void placingInsideSomebodyElsesHouseIsRefusedUnlessYouAreStaff() {
        assertFalse(GrabbyPolicy.mayPlace(false, 0, true));
        assertFalse(GrabbyPolicy.mayPlace(false, 1, true));
        assertTrue(GrabbyPolicy.mayPlace(true, 0, true));
        assertTrue(GrabbyPolicy.mayPlace(false, 2, true));
    }

    @Test
    void everyMutationReasonIsClassifiedExactlyOnce() {
        for (GrabbyMutationReason reason : GrabbyMutationReason.values()) {
            assertFalse(reason.systemAuthorized() && reason.environmental(), reason.name());
        }
        assertTrue(GrabbyMutationReason.SYSTEM_MUTATION.systemAuthorized());
        assertFalse(GrabbyMutationReason.PICKUP.environmental());
        assertFalse(GrabbyMutationReason.AXE_DESTROY.environmental());
    }
}
