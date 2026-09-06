package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.grabbyhands.testsupport.FakeGrabbyActor;
import com.seggellion.britannia_mod.grabbyhands.testsupport.FakeGrabbyWorld;
import com.seggellion.britannia_mod.grabbyhands.testsupport.GrabbyTagBinding;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M3 placement contract.
 *
 * <p>Runs against the real {@link GrabbyEligibility}, {@link GrabbyPolicy},
 * {@link GrabbyPlacementTransaction} and {@link GrabbyMutationGuard}, with the world and the player
 * faked. The native placement path itself is vanilla's, so what is asserted here is the policy,
 * enrollment, provenance and fail-closed behaviour Grabby Hands wraps around it.
 */
class GrabbyPlacementTransactionTest {
    private static final BlockPos SUPPORT = new BlockPos(4, 64, 4);
    private static final BlockPos TARGET = new BlockPos(4, 65, 4);

    private static BlockState enrolledBlock;
    private static ItemStack enrolledItem;
    private static ItemStack unenrolledItem;

    private List<String> audio;
    private FakeGrabbyWorld world;
    private FakeGrabbyActor actor;

    @BeforeAll
    static void bootstrapAndEnrollATestBlock() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GrabbyTagBinding.enrol(Blocks.OAK_STAIRS);
        enrolledBlock = Blocks.OAK_STAIRS.defaultBlockState();
        enrolledItem = new ItemStack(Items.OAK_STAIRS);
        unenrolledItem = new ItemStack(Items.STONE);
    }

    @AfterAll
    static void clearEnrollment() {
        GrabbyTagBinding.clear();
    }

    @BeforeEach
    void setUp() {
        GrabbyMutationGuard.reset();
        audio = new ArrayList<>();
        world = new FakeGrabbyWorld(audio);
        actor = new FakeGrabbyActor(audio);
    }

    private static BlockHitResult hitTopOf(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
    }

    private void readyToPlace() {
        actor.placing(world, TARGET, enrolledBlock, enrolledItem.copy());
    }

    // ------------------------------------------------------------------
    // The core capability
    // ------------------------------------------------------------------

    @Test
    void anEnrolledItemPlacesAndTheResultIsMarkedPlayerPlaced() {
        readyToPlace();

        GrabbyPlacementResult result = GrabbyPlacementTransaction.execute(
                world, actor, enrolledItem, hitTopOf(SUPPORT));

        assertEquals(GrabbyPlacementOutcome.SUCCESS, result.outcome());
        assertEquals(1, result.worldObjectsPlaced());
        assertEquals(TARGET, result.position().orElseThrow());
        assertTrue(world.occupied(TARGET));

        GrabbyInstanceState stamped = world.grabbyState(TARGET);
        assertEquals(GrabbyProvenance.PLAYER, stamped.provenance());
        assertEquals(actor.id(), stamped.placerUuid().orElseThrow());
        assertEquals(world.gameTime(), stamped.placedAtGameTime());
    }

    /**
     * The arbitration line: only a click that was never a placement falls through.
     *
     * <p>Grabby consumes the interaction for every outcome it considers handled, which is right for a
     * placement the player asked for and was denied — the spot is protected, unsupported, obstructed,
     * out of reach — because something was attempted and answered. It is wrong for a click that was
     * never a placement request at all, which is why {@code NOT_A_PLACEMENT_GESTURE} joins
     * {@code TYPE_NOT_ENROLLED} on the other side of the line. Widening that set any further would
     * start silently handing real refusals back to the block, so this pins both halves.
     */
    @Test
    void onlyClicksThatWereNeverPlacementsFallThroughWhileEveryRealRefusalStaysHandled() {
        assertFalse(GrabbyPlacementOutcome.TYPE_NOT_ENROLLED.handled(),
                "an unenrolled item must leave the interaction alone");
        assertFalse(GrabbyPlacementOutcome.NOT_A_PLACEMENT_GESTURE.handled(),
                "a click that was never a placement must leave the interaction alone");

        for (GrabbyPlacementOutcome outcome : List.of(
                GrabbyPlacementOutcome.SUCCESS,
                GrabbyPlacementOutcome.PLACED_WITHOUT_PROVENANCE,
                GrabbyPlacementOutcome.NO_VALID_TARGET,
                GrabbyPlacementOutcome.OUT_OF_REACH,
                GrabbyPlacementOutcome.DENIED_BY_POLICY,
                GrabbyPlacementOutcome.REFUSED_BY_BLOCK,
                GrabbyPlacementOutcome.ALREADY_IN_PROGRESS)) {
            assertTrue(outcome.handled(),
                    outcome + " answers a placement the player asked for and must stay consumed");
        }
    }

    @Test
    void aNonEnrolledItemIsLeftEntirelyAloneSoAdventureModeStillRefusesIt() {
        // The whole Adventure-mode narrowness rests on this: anything not enrolled never reaches the
        // native placement path here, and therefore still meets the ordinary ItemStack.useOn gate.
        actor.placing(world, TARGET, Blocks.STONE.defaultBlockState(), unenrolledItem.copy());

        GrabbyPlacementResult result = GrabbyPlacementTransaction.execute(
                world, actor, unenrolledItem, hitTopOf(SUPPORT));

        assertEquals(GrabbyPlacementOutcome.TYPE_NOT_ENROLLED, result.outcome());
        assertFalse(result.outcome().handled(), "the interaction must fall through untouched");
        assertFalse(world.occupied(TARGET));
        assertFalse(actor.itemConsumed());
    }

    @Test
    void aNonBlockItemIsNotEnrolled() {
        assertEquals(GrabbyPlacementOutcome.TYPE_NOT_ENROLLED,
                GrabbyPlacementTransaction.execute(
                        world, actor, new ItemStack(Items.STICK), hitTopOf(SUPPORT)).outcome());
        assertEquals(GrabbyPlacementOutcome.TYPE_NOT_ENROLLED,
                GrabbyPlacementTransaction.execute(
                        world, actor, ItemStack.EMPTY, hitTopOf(SUPPORT)).outcome());
    }

    // ------------------------------------------------------------------
    // The block's own rules stay authoritative
    // ------------------------------------------------------------------

    @Test
    void anObstructedOrUnsupportedPlacementIsRefusedAndCostsNothing() {
        // canSurvive/isUnobstructed said no. That is vanilla's judgement, not a Grabby rule, which is
        // what keeps existing support and collision behaviour intact.
        readyToPlace();
        actor.blockRefusesPlacement();

        GrabbyPlacementResult result = GrabbyPlacementTransaction.execute(
                world, actor, enrolledItem, hitTopOf(SUPPORT));

        assertEquals(GrabbyPlacementOutcome.REFUSED_BY_BLOCK, result.outcome());
        assertEquals(0, result.worldObjectsPlaced());
        assertFalse(world.occupied(TARGET));
        assertFalse(actor.itemConsumed(), "a refused placement must not consume the item");
    }

    @Test
    void noValidDestinationIsRefused() {
        actor.noPlacementTarget();

        assertEquals(GrabbyPlacementOutcome.NO_VALID_TARGET,
                GrabbyPlacementTransaction.execute(world, actor, enrolledItem, hitTopOf(SUPPORT)).outcome());
        assertFalse(actor.itemConsumed());
    }

    @Test
    void theItemIsConsumedOnlyWhenSomethingWasActuallyPlaced() {
        readyToPlace();
        assertFalse(actor.itemConsumed());

        GrabbyPlacementTransaction.execute(world, actor, enrolledItem, hitTopOf(SUPPORT));

        assertTrue(actor.itemConsumed());
    }

    // ------------------------------------------------------------------
    // Policy
    // ------------------------------------------------------------------

    @Test
    void placingInsideSomebodyElsesHouseIsRefusedForOrdinaryPlayersButNotForStaff() {
        readyToPlace();
        actor.insideSomebodyElsesHouse();

        assertEquals(GrabbyPlacementOutcome.DENIED_BY_POLICY,
                GrabbyPlacementTransaction.execute(world, actor, enrolledItem, hitTopOf(SUPPORT)).outcome());
        assertFalse(world.occupied(TARGET));

        FakeGrabbyActor staff = new FakeGrabbyActor(audio)
                .insideSomebodyElsesHouse().operator()
                .placing(world, TARGET, enrolledBlock, enrolledItem.copy());
        assertEquals(GrabbyPlacementOutcome.SUCCESS,
                GrabbyPlacementTransaction.execute(world, staff, enrolledItem, hitTopOf(SUPPORT)).outcome());
    }

    @Test
    void reachIsValidatedServerSideAtTheDestination() {
        readyToPlace();
        actor.outOfReach();

        assertEquals(GrabbyPlacementOutcome.OUT_OF_REACH,
                GrabbyPlacementTransaction.execute(world, actor, enrolledItem, hitTopOf(SUPPORT)).outcome());
        assertFalse(world.occupied(TARGET));
    }

    // ------------------------------------------------------------------
    // Fail closed
    // ------------------------------------------------------------------

    @Test
    void aBlockThatCannotCarryProvenanceIsLeftProtectedRatherThanSilentlyMovable() {
        readyToPlace();
        world.provenanceStampingFails();

        GrabbyPlacementResult result = GrabbyPlacementTransaction.execute(
                world, actor, enrolledItem, hitTopOf(SUPPORT));

        assertEquals(GrabbyPlacementOutcome.PLACED_WITHOUT_PROVENANCE, result.outcome());
        assertTrue(world.occupied(TARGET), "the player's block must not be silently destroyed");
        assertFalse(world.grabbyState(TARGET).grabbyManaged(),
                "an unverifiable object must stay protected, never movable by default");
    }

    @Test
    void aConcurrentTransactionOnTheSameDestinationLoses() {
        readyToPlace();
        try (GrabbyMutationGuard.Claim held = GrabbyMutationGuard.claim(world.levelIdentity(), TARGET)) {
            assertTrue(held.held());

            GrabbyPlacementResult result = GrabbyPlacementTransaction.execute(
                    world, actor, enrolledItem, hitTopOf(SUPPORT));

            assertEquals(GrabbyPlacementOutcome.ALREADY_IN_PROGRESS, result.outcome());
            assertFalse(world.occupied(TARGET));
            assertFalse(actor.itemConsumed());
        }
    }

    // ------------------------------------------------------------------
    // Round trip
    // ------------------------------------------------------------------

    @Test
    void placeThenPickUpThenPlaceAgainKeepsTheObjectGrabbyManagedThroughout() {
        readyToPlace();
        assertEquals(GrabbyPlacementOutcome.SUCCESS,
                GrabbyPlacementTransaction.execute(world, actor, enrolledItem, hitTopOf(SUPPORT)).outcome());
        assertTrue(world.grabbyState(TARGET).grabbyManaged());

        GrabbyPickupResult pickup = GrabbyPickupTransaction.execute(world, actor, TARGET);
        assertEquals(GrabbyPickupOutcome.SUCCESS, pickup.outcome());
        assertFalse(world.occupied(TARGET));

        FakeGrabbyActor again = new FakeGrabbyActor(audio)
                .placing(world, TARGET, enrolledBlock, enrolledItem.copy());
        assertEquals(GrabbyPlacementOutcome.SUCCESS,
                GrabbyPlacementTransaction.execute(world, again, enrolledItem, hitTopOf(SUPPORT)).outcome());

        GrabbyInstanceState restamped = world.grabbyState(TARGET);
        assertTrue(restamped.grabbyManaged());
        assertEquals(again.id(), restamped.placerUuid().orElseThrow(),
                "provenance follows the most recent placer");
    }

    @Test
    void anObjectThatArrivedByAnyOtherMeansStaysImmovable() {
        // Worldgen, a structure template, an admin in Creative: same block type, no Grabby mark.
        world.placeRaw(TARGET, enrolledBlock, enrolledItem.copy());

        assertEquals(GrabbyPickupOutcome.NOT_GRABBY_MANAGED,
                GrabbyPickupTransaction.execute(world, actor, TARGET).outcome());
        assertTrue(world.occupied(TARGET));
    }
}
