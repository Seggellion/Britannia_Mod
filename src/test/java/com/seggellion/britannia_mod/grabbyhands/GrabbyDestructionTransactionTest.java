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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Axe destruction: exactly once, only what Grabby Hands owns, and only with a real axe. */
class GrabbyDestructionTransactionTest {
    private static final BlockPos POS = new BlockPos(3, 64, 9);
    private static final UUID PLACER = UUID.fromString("eeeeeeee-1111-2222-3333-444444444444");

    private static BlockState enrolledBlock;
    private static BlockState unenrolledBlock;

    private List<String> audio;
    private FakeGrabbyWorld world;
    private FakeGrabbyActor actor;

    @BeforeAll
    static void bootstrapAndEnrol() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GrabbyTagBinding.enrol(Blocks.OAK_STAIRS);
        enrolledBlock = Blocks.OAK_STAIRS.defaultBlockState();
        unenrolledBlock = Blocks.STONE.defaultBlockState();
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

    private static ItemStack axe() {
        return new ItemStack(Items.IRON_AXE);
    }

    private void placePlayerObject() {
        world.place(POS, enrolledBlock, GrabbyInstanceState.playerPlaced(PLACER, 7L),
                new ItemStack(Items.OAK_STAIRS));
    }

    // ------------------------------------------------------------------
    // Exactly once
    // ------------------------------------------------------------------

    @Test
    void anAxeDestroysAPlayerPlacedObjectExactlyOnce() {
        placePlayerObject();

        GrabbyDestructionResult result = GrabbyDestructionTransaction.execute(world, actor, axe(), POS);

        assertEquals(GrabbyDestructionOutcome.SUCCESS, result.outcome());
        assertEquals(1, result.objectsDestroyed());
        assertFalse(world.occupied(POS));
        assertEquals(1, world.removeCalls());
    }

    @Test
    void aSecondSwingAtTheSamePlaceDestroysNothingMore() {
        placePlayerObject();

        GrabbyDestructionTransaction.execute(world, actor, axe(), POS);
        GrabbyDestructionResult second = GrabbyDestructionTransaction.execute(world, actor, axe(), POS);

        assertEquals(GrabbyDestructionOutcome.NOTHING_THERE, second.outcome());
        assertEquals(0, second.objectsDestroyed());
    }

    @Test
    void anAxeAndAPickupRacingTheSameObjectResolveToOneWinner() {
        placePlayerObject();
        FakeGrabbyActor chopper = new FakeGrabbyActor(audio);
        var chopperResult = new java.util.concurrent.atomic.AtomicReference<GrabbyDestructionResult>();
        world.onBeforeCapture(pos -> chopperResult.compareAndSet(
                null, GrabbyDestructionTransaction.execute(world, chopper, axe(), pos)));

        GrabbyPickupResult pickup = GrabbyPickupTransaction.execute(world, actor, POS);

        assertEquals(GrabbyPickupOutcome.SUCCESS, pickup.outcome());
        assertEquals(GrabbyDestructionOutcome.ALREADY_IN_PROGRESS, chopperResult.get().outcome());
        assertEquals(0, chopperResult.get().objectsDestroyed());
        assertEquals(1, world.removeCalls(), "the object may only leave the world once");
    }

    // ------------------------------------------------------------------
    // Only with a real axe, only what Grabby Hands owns
    // ------------------------------------------------------------------

    @Test
    void aNonAxeCannotUseTheDestructionPathAtAll() {
        placePlayerObject();

        for (ItemStack notAnAxe : List.of(new ItemStack(Items.IRON_SWORD),
                new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.STICK), ItemStack.EMPTY)) {
            GrabbyDestructionResult result =
                    GrabbyDestructionTransaction.execute(world, actor, notAnAxe, POS);
            assertEquals(GrabbyDestructionOutcome.NOT_AN_AXE, result.outcome());
            assertFalse(result.outcome().handled(), "a non-axe interaction must fall through untouched");
        }
        assertTrue(world.occupied(POS));
    }

    @Test
    void sceneryWithNoPlayerProvenanceIsRefused() {
        // The same block type a player could place, but this instance was not placed by one.
        world.placeRaw(POS, enrolledBlock, new ItemStack(Items.OAK_STAIRS));

        GrabbyDestructionResult result = GrabbyDestructionTransaction.execute(world, actor, axe(), POS);

        assertEquals(GrabbyDestructionOutcome.NOT_GRABBY_MANAGED, result.outcome());
        assertTrue(world.occupied(POS));
        assertEquals(0, world.destructionEffects());
    }

    @Test
    void anUnenrolledBlockTypeIsIgnored() {
        world.place(POS, unenrolledBlock, GrabbyInstanceState.playerPlaced(PLACER, 1L),
                new ItemStack(Items.STONE));

        GrabbyDestructionResult result = GrabbyDestructionTransaction.execute(world, actor, axe(), POS);

        assertEquals(GrabbyDestructionOutcome.TYPE_NOT_ENROLLED, result.outcome());
        assertFalse(result.outcome().handled());
        assertTrue(world.occupied(POS));
    }

    @Test
    void reachAndPolicyStillApply() {
        placePlayerObject();
        actor.outOfReach();
        assertEquals(GrabbyDestructionOutcome.OUT_OF_REACH,
                GrabbyDestructionTransaction.execute(world, actor, axe(), POS).outcome());

        FakeGrabbyActor intruder = new FakeGrabbyActor(audio).insideSomebodyElsesHouse();
        assertEquals(GrabbyDestructionOutcome.DENIED_BY_POLICY,
                GrabbyDestructionTransaction.execute(world, intruder, axe(), POS).outcome());

        FakeGrabbyActor staff = new FakeGrabbyActor(audio).insideSomebodyElsesHouse().operator();
        assertEquals(GrabbyDestructionOutcome.SUCCESS,
                GrabbyDestructionTransaction.execute(world, staff, axe(), POS).outcome());
    }

    @Test
    void anObjectThatRefusesTransportAlsoRefusesDestruction() {
        // Chopping a chest somebody has open is no better than pocketing it out from under them.
        placePlayerObject();
        world.refusingTransport(GrabbyTransportRefusal.IN_USE);

        GrabbyDestructionResult result = GrabbyDestructionTransaction.execute(world, actor, axe(), POS);

        assertEquals(GrabbyDestructionOutcome.REFUSED_BY_OBJECT, result.outcome());
        assertTrue(world.occupied(POS));
    }

    // ------------------------------------------------------------------
    // Contents versus the object itself
    // ------------------------------------------------------------------

    @Test
    void aContainersContentsAreLeftAttachedSoTheBlocksOwnRemovalSpillsThem() {
        placePlayerObject();
        world.holdingOccupiedSlots(4);

        GrabbyDestructionResult result = GrabbyDestructionTransaction.execute(world, actor, axe(), POS);

        assertEquals(GrabbyDestructionOutcome.SUCCESS, result.outcome());
        assertEquals(0, world.objectPayloadDetaches(),
                "detaching would suppress the spill; contents must fall out exactly once via onRemove");
    }

    @Test
    void aPlacedItemHostsPayloadIsConsumedRatherThanHandedBack() {
        // The payload IS the object here. Destroying a cheese should destroy the cheese.
        world.place(POS, enrolledBlock, GrabbyInstanceState.playerPlaced(PLACER, 2L),
                new ItemStack(Items.GOLD_INGOT));
        world.attachPayload(POS, new ItemStack(Items.GOLD_INGOT));
        world.asItemHost();

        GrabbyDestructionResult result = GrabbyDestructionTransaction.execute(world, actor, axe(), POS);

        assertEquals(GrabbyDestructionOutcome.SUCCESS, result.outcome());
        assertEquals(1, world.objectPayloadDetaches());
        assertTrue(world.payloadAt(POS).isEmpty(), "the object must not survive its own destruction");
    }

    // ------------------------------------------------------------------
    // Feedback and durability
    // ------------------------------------------------------------------

    @Test
    void theDestructionEffectFiresExactlyOnceAndOnlyAfterTheObjectIsGone() {
        placePlayerObject();

        GrabbyDestructionResult result = GrabbyDestructionTransaction.execute(world, actor, axe(), POS);

        assertTrue(result.effectPlayed());
        assertEquals(1, world.destructionEffects());
        assertEquals(List.of("DESTROY:" + enrolledBlock.getBlock()), audio);
    }

    @Test
    void aRefusedDestructionIsSilent() {
        world.placeRaw(POS, enrolledBlock, new ItemStack(Items.OAK_STAIRS));

        GrabbyDestructionResult result = GrabbyDestructionTransaction.execute(world, actor, axe(), POS);

        assertFalse(result.effectPlayed());
        assertTrue(audio.isEmpty());
    }

    @Test
    void theAxeCostsOneDurabilityPerDestruction() {
        placePlayerObject();

        GrabbyDestructionResult result = GrabbyDestructionTransaction.execute(world, actor, axe(), POS);

        assertTrue(result.toolDamaged());
        assertEquals(1, actor.toolDamage(), "vanilla parity: one swing, one point");
    }

    @Test
    void aCreativePlayersAxeIsNotWornDown() {
        placePlayerObject();
        FakeGrabbyActor staff = new FakeGrabbyActor(audio).creative();

        GrabbyDestructionResult result = GrabbyDestructionTransaction.execute(world, staff, axe(), POS);

        assertEquals(GrabbyDestructionOutcome.SUCCESS, result.outcome());
        assertFalse(result.toolDamaged());
        assertEquals(0, staff.toolDamage());
    }

    @Test
    void aRefusedDestructionCostsNoDurability() {
        world.placeRaw(POS, enrolledBlock, new ItemStack(Items.OAK_STAIRS));

        GrabbyDestructionTransaction.execute(world, actor, axe(), POS);

        assertEquals(0, actor.toolDamage());
    }

    // ------------------------------------------------------------------
    // Material-appropriate audio
    // ------------------------------------------------------------------

    @Test
    void woodAndGlassDoNotSoundAlike() {
        assertNotEquals(
                GrabbySoundRoles.destroy(Blocks.OAK_PLANKS.defaultBlockState()),
                GrabbySoundRoles.destroy(Blocks.GLASS.defaultBlockState()),
                "a wooden chair and a glass bottle must be audibly different");
    }

    @Test
    void theDestructionSoundComesFromTheBlocksOwnDeclaredMaterial() {
        assertEquals(SoundType.WOOD.getBreakSound(),
                GrabbySoundRoles.destroy(Blocks.OAK_PLANKS.defaultBlockState()));
        assertEquals(SoundType.GLASS.getBreakSound(),
                GrabbySoundRoles.destroy(Blocks.GLASS.defaultBlockState()));
        assertEquals(SoundType.METAL.getBreakSound(),
                GrabbySoundRoles.destroy(Blocks.IRON_BLOCK.defaultBlockState()));
    }

    // ------------------------------------------------------------------
    // Result invariants
    // ------------------------------------------------------------------

    @Test
    void theResultRecordRefusesToClaimFeedbackWithoutADestruction() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new GrabbyDestructionResult(GrabbyDestructionOutcome.NOTHING_THERE, POS, 0, true, false));
    }

    @Test
    void theResultRecordRefusesImpossibleCounts() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new GrabbyDestructionResult(GrabbyDestructionOutcome.SUCCESS, POS, 0, false, false));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new GrabbyDestructionResult(GrabbyDestructionOutcome.NOTHING_THERE, POS, 1, false, false));
    }
}
