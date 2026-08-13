package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.grabbyhands.testsupport.FakeGrabbyActor;
import com.seggellion.britannia_mod.grabbyhands.testsupport.FakeGrabbyWorld;
import com.seggellion.britannia_mod.grabbyhands.testsupport.GrabbyTagBinding;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The M2 atomicity and audio contract.
 *
 * <p>Runs against real {@link GrabbyEligibility}, {@link GrabbyPolicy} and {@link GrabbyMutationGuard}
 * with only the world and the player faked, so the ordering guarantees are exercised as written
 * rather than re-stated by the test.
 */
class GrabbyPickupTransactionTest {
    private static final BlockPos POS = new BlockPos(10, 64, 20);
    private static final UUID PLACER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    /** Stands in for an enrolled chair: a real vanilla block with the Grabby tag bound onto it. */
    private static BlockState enrolledBlock;
    /** A real vanilla block deliberately left out of the tag. */
    private static BlockState unenrolledBlock;

    private List<String> audio;
    private FakeGrabbyWorld world;
    private FakeGrabbyActor actor;

    @BeforeAll
    static void bootstrapMinecraftRegistriesAndBindTheEnrollmentTag() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        GrabbyTagBinding.enrol(Blocks.OAK_STAIRS);
        enrolledBlock = Blocks.OAK_STAIRS.defaultBlockState();
        unenrolledBlock = Blocks.STONE.defaultBlockState();
    }

    @org.junit.jupiter.api.AfterAll
    static void unbindTheEnrollmentTag() {
        GrabbyTagBinding.clear();
    }

    @BeforeEach
    void setUp() {
        GrabbyMutationGuard.reset();
        audio = new ArrayList<>();
        world = new FakeGrabbyWorld(audio);
        actor = new FakeGrabbyActor(audio);
    }

    private void placePlayerOwnedObject() {
        world.place(POS, enrolledBlock,
                GrabbyInstanceState.playerPlaced(PLACER, 50L),
                new ItemStack(Items.OAK_STAIRS));
    }

    // ------------------------------------------------------------------
    // Exactly once
    // ------------------------------------------------------------------

    @Test
    void aSuccessfulPickupCreatesExactlyOneItemAndRemovesExactlyOneObject() {
        placePlayerOwnedObject();

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(world, actor, POS);

        assertEquals(GrabbyPickupOutcome.SUCCESS, result.outcome());
        assertEquals(1, result.worldObjectsRemoved());
        assertEquals(1, result.inventoryItemsCreated());
        assertEquals(1, actor.inventory().size());
        assertTrue(actor.dropped().isEmpty());
        assertFalse(world.occupied(POS));
    }

    @Test
    void aRepeatedInteractionOnAnAlreadyPickedUpObjectDoesNothing() {
        placePlayerOwnedObject();

        GrabbyPickupResult first = GrabbyPickupTransaction.execute(world, actor, POS);
        GrabbyPickupResult second = GrabbyPickupTransaction.execute(world, actor, POS);

        assertEquals(GrabbyPickupOutcome.SUCCESS, first.outcome());
        assertEquals(GrabbyPickupOutcome.NOTHING_THERE, second.outcome());
        assertEquals(1, actor.inventory().size(), "a replayed packet must not duplicate the item");
        assertEquals(0, second.worldObjectsRemoved());
    }

    @Test
    void aCompetingTransactionArrivingMidFlightLosesAndChangesNothing() {
        // Deterministic race: a second attempt is fired from inside the first one's capture step,
        // which is exactly the window a duplicated packet or a second player would land in.
        placePlayerOwnedObject();
        FakeGrabbyActor rival = new FakeGrabbyActor(audio);
        AtomicReference<GrabbyPickupResult> rivalResult = new AtomicReference<>();
        world.onBeforeCapture(pos ->
                rivalResult.compareAndSet(null, GrabbyPickupTransaction.execute(world, rival, pos)));

        GrabbyPickupResult winner = GrabbyPickupTransaction.execute(world, actor, POS);

        assertEquals(GrabbyPickupOutcome.SUCCESS, winner.outcome());
        assertEquals(GrabbyPickupOutcome.ALREADY_IN_PROGRESS, rivalResult.get().outcome());
        assertEquals(0, rivalResult.get().worldObjectsRemoved());
        assertTrue(rival.inventory().isEmpty(), "the losing player receives nothing");
        assertEquals(1, actor.inventory().size());
        assertEquals(1, world.removeCalls(), "the object may only be removed once");
    }

    @Test
    void twoPlayersRacingConcurrentlyProduceExactlyOneWinner() throws InterruptedException {
        placePlayerOwnedObject();
        FakeGrabbyActor rival = new FakeGrabbyActor(audio);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<GrabbyPickupResult> resultA = new AtomicReference<>();
        AtomicReference<GrabbyPickupResult> resultB = new AtomicReference<>();

        Thread threadA = contender(start, resultA, actor);
        Thread threadB = contender(start, resultB, rival);
        threadA.start();
        threadB.start();
        start.countDown();
        threadA.join(TimeUnit.SECONDS.toMillis(10));
        threadB.join(TimeUnit.SECONDS.toMillis(10));

        // Whatever the interleaving, these hold: one object left the world, one item was created.
        int itemsCreated = actor.inventory().size() + rival.inventory().size();
        int successes = (resultA.get().succeeded() ? 1 : 0) + (resultB.get().succeeded() ? 1 : 0);
        assertEquals(1, successes, "exactly one pickup may succeed");
        assertEquals(1, itemsCreated, "exactly one item may be created");
        assertFalse(world.occupied(POS));
        assertTrue(actor.dropped().isEmpty() && rival.dropped().isEmpty());
    }

    private Thread contender(CountDownLatch start, AtomicReference<GrabbyPickupResult> sink, FakeGrabbyActor who) {
        return new Thread(() -> {
            try {
                start.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            sink.set(GrabbyPickupTransaction.execute(world, who, POS));
        });
    }

    // ------------------------------------------------------------------
    // Failure must never destroy the object
    // ------------------------------------------------------------------

    @Test
    void aFullInventoryLeavesTheWorldObjectIntactAndPlaysNothing() {
        placePlayerOwnedObject();
        actor.inventoryFull();

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(world, actor, POS);

        assertEquals(GrabbyPickupOutcome.INVENTORY_FULL, result.outcome());
        assertTrue(world.occupied(POS), "a full inventory must never destroy the object");
        assertEquals(0, world.removeCalls());
        assertEquals(0, result.worldObjectsRemoved());
        assertTrue(audio.isEmpty(), "no cue may fire for a refused pickup");
    }

    @Test
    void aFailedRemovalLeavesNoItemAndNoAudio() {
        placePlayerOwnedObject();
        world.removalFails();

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(world, actor, POS);

        assertEquals(GrabbyPickupOutcome.REMOVAL_FAILED, result.outcome());
        assertTrue(actor.inventory().isEmpty());
        assertTrue(audio.isEmpty());
    }

    @Test
    void anInsertionFailingAfterRemovalDropsTheObjectRatherThanVoidingIt() {
        placePlayerOwnedObject();
        actor.insertionFailsUnexpectedly();

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(world, actor, POS);

        assertEquals(GrabbyPickupOutcome.DROPPED_AT_FEET, result.outcome());
        assertEquals(1, result.worldObjectsRemoved());
        assertEquals(0, result.inventoryItemsCreated());
        assertEquals(1, actor.dropped().size(), "the object must survive as a floor drop");
        assertTrue(actor.inventory().isEmpty());
    }

    // ------------------------------------------------------------------
    // Two-stage audio
    // ------------------------------------------------------------------

    @Test
    void aSuccessfulPickupFiresTheGrabCueThenTheStowCueInThatOrder() {
        placePlayerOwnedObject();

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(world, actor, POS);

        assertTrue(result.grabSoundPlayed());
        assertTrue(result.stowSoundPlayed());
        assertEquals(
                List.of("WORLD:" + GrabbySoundRoles.grab().getLocation(),
                        "PLAYER:" + GrabbySoundRoles.stow().getLocation()),
                audio);
    }

    @Test
    void theGrabAndStowCuesAreDistinctEvents() {
        assertFalse(GrabbySoundRoles.grab().getLocation().equals(GrabbySoundRoles.stow().getLocation()),
                "the two pickup stages must be audibly distinguishable");
    }

    @Test
    void theStowCueIsWithheldWhenNothingWasActuallyStowed() {
        placePlayerOwnedObject();
        actor.insertionFailsUnexpectedly();

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(world, actor, POS);

        assertTrue(result.grabSoundPlayed(), "the object did leave the world, so the grab cue is honest");
        assertFalse(result.stowSoundPlayed());
        assertEquals(List.of("WORLD:" + GrabbySoundRoles.grab().getLocation()), audio,
                "the player must not be told they stowed something they did not");
    }

    @Test
    void theResultRecordRefusesToClaimAStowWithoutAGrab() {
        assertThrowsIllegalArgument(() -> new GrabbyPickupResult(
                GrabbyPickupOutcome.SUCCESS, POS, ItemStack.EMPTY, 1, 1, false, true));
    }

    @Test
    void theResultRecordRefusesImpossibleCounts() {
        assertThrowsIllegalArgument(() -> new GrabbyPickupResult(
                GrabbyPickupOutcome.SUCCESS, POS, ItemStack.EMPTY, 2, 1, true, true));
        assertThrowsIllegalArgument(() -> new GrabbyPickupResult(
                GrabbyPickupOutcome.SUCCESS, POS, ItemStack.EMPTY, 1, -1, true, true));
    }

    private static void assertThrowsIllegalArgument(Runnable runnable) {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, runnable::run);
    }

    // ------------------------------------------------------------------
    // Refusals that leave normal use alone
    // ------------------------------------------------------------------

    @Test
    void anUnmarkedInstanceOfAnEnrolledTypeIsProtectedAndTheInteractionIsNotConsumed() {
        // Britannia scenery: right block type, no player provenance.
        world.place(POS, enrolledBlock, GrabbyInstanceState.worldPlaced(), new ItemStack(Items.OAK_STAIRS));

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(world, actor, POS);

        assertEquals(GrabbyPickupOutcome.NOT_GRABBY_MANAGED, result.outcome());
        assertTrue(world.occupied(POS));
        assertFalse(result.outcome().handled(),
                "a protected object must fall through so its normal use still works");
    }

    @Test
    void anUnenrolledBlockTypeIsIgnoredEntirely() {
        world.place(POS, unenrolledBlock,
                GrabbyInstanceState.playerPlaced(PLACER, 1L), new ItemStack(Items.STONE));

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(world, actor, POS);

        assertEquals(GrabbyPickupOutcome.TYPE_NOT_ENROLLED, result.outcome());
        assertFalse(result.outcome().handled());
        assertTrue(world.occupied(POS));
    }

    @Test
    void anEmptyPositionIsIgnored() {
        assertEquals(GrabbyPickupOutcome.NOTHING_THERE,
                GrabbyPickupTransaction.execute(world, actor, POS).outcome());
    }

    @Test
    void reachIsValidatedServerSide() {
        placePlayerOwnedObject();
        actor.outOfReach();

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(world, actor, POS);

        assertEquals(GrabbyPickupOutcome.OUT_OF_REACH, result.outcome());
        assertTrue(world.occupied(POS));
        assertTrue(audio.isEmpty());
    }

    @Test
    void anObjectInsideSomebodyElsesHouseIsRefusedForAnOrdinaryPlayerButNotForStaff() {
        placePlayerOwnedObject();
        actor.insideSomebodyElsesHouse();

        assertEquals(GrabbyPickupOutcome.DENIED_BY_POLICY,
                GrabbyPickupTransaction.execute(world, actor, POS).outcome());
        assertTrue(world.occupied(POS));

        FakeGrabbyActor staff = new FakeGrabbyActor(audio).insideSomebodyElsesHouse().operator();
        assertEquals(GrabbyPickupOutcome.SUCCESS,
                GrabbyPickupTransaction.execute(world, staff, POS).outcome());
    }

    // ------------------------------------------------------------------
    // Public usability
    // ------------------------------------------------------------------

    @Test
    void aPlayerWhoDidNotPlaceTheObjectMayStillPickItUp() {
        // Provenance marks "Grabby-managed", not "yours". A different player, subject to the same
        // region and house rules, is not refused merely for not being the placer.
        placePlayerOwnedObject();
        FakeGrabbyActor somebodyElse = new FakeGrabbyActor(audio);

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(world, somebodyElse, POS);

        assertEquals(GrabbyPickupOutcome.SUCCESS, result.outcome());
        assertEquals(1, somebodyElse.inventory().size());
    }

    // ------------------------------------------------------------------
    // Guard hygiene
    // ------------------------------------------------------------------

    @Test
    void theClaimIsAlwaysReleasedIncludingOnRefusal() {
        placePlayerOwnedObject();
        actor.inventoryFull();

        GrabbyPickupTransaction.execute(world, actor, POS);
        assertEquals(0, GrabbyMutationGuard.outstandingClaims(),
                "a refused transaction must not strand a lock on the object");

        FakeGrabbyActor second = new FakeGrabbyActor(audio);
        assertSame(GrabbyPickupOutcome.SUCCESS,
                GrabbyPickupTransaction.execute(world, second, POS).outcome());
        assertEquals(0, GrabbyMutationGuard.outstandingClaims());
    }
}
