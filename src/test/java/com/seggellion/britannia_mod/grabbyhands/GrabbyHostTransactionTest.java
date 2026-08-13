package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.grabbyhands.testsupport.FakeGrabbyActor;
import com.seggellion.britannia_mod.grabbyhands.testsupport.FakeGrabbyWorld;
import com.seggellion.britannia_mod.grabbyhands.testsupport.GrabbyTagBinding;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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
 * The loose-item path end to end: place an item that has no block form, then pick it back up.
 */
class GrabbyHostTransactionTest {
    private static final BlockPos SUPPORT = new BlockPos(2, 64, 2);
    private static final BlockPos TARGET = new BlockPos(2, 65, 2);

    private static BlockState hostState;

    private List<String> audio;
    private FakeGrabbyWorld world;
    private FakeGrabbyActor actor;

    @BeforeAll
    static void bootstrapAndEnrolATestItem() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // A plain vanilla item with no block form stands in for the pilot content.
        GrabbyTagBinding.enrolItem(Items.GOLD_INGOT);
        // The host block itself is enrolled as movable, exactly as the real one is.
        GrabbyTagBinding.enrol(Blocks.OAK_STAIRS);
        hostState = Blocks.OAK_STAIRS.defaultBlockState();
    }

    @AfterAll
    static void clearEnrollment() {
        GrabbyTagBinding.clear();
        GrabbyTagBinding.clearItems();
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

    private static ItemStack namedIngot() {
        ItemStack stack = new ItemStack(Items.GOLD_INGOT, 4);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Trinsic Bar"));
        return stack;
    }

    @Test
    void anEnrolledLooseItemPlacesAsAHostCarryingExactlyOneOfIt() {
        ItemStack held = namedIngot();
        actor.placingHost(world, TARGET, hostState);

        GrabbyPlacementResult result = GrabbyPlacementTransaction.execute(world, actor, held, hitTopOf(SUPPORT));

        assertEquals(GrabbyPlacementOutcome.SUCCESS, result.outcome());
        assertEquals(TARGET, result.position().orElseThrow());

        ItemStack payload = world.payloadAt(TARGET);
        assertEquals(1, payload.getCount(), "one item is set down, not the whole stack");
        assertEquals("Trinsic Bar", payload.get(DataComponents.CUSTOM_NAME).getString());
        assertTrue(world.grabbyState(TARGET).grabbyManaged());
    }

    @Test
    void anUntaggedLooseItemIsLeftAloneEntirely() {
        actor.placingHost(world, TARGET, hostState);

        GrabbyPlacementResult result = GrabbyPlacementTransaction.execute(
                world, actor, new ItemStack(Items.STICK), hitTopOf(SUPPORT));

        assertEquals(GrabbyPlacementOutcome.TYPE_NOT_ENROLLED, result.outcome());
        assertFalse(result.outcome().handled());
        assertFalse(world.occupied(TARGET));
        assertFalse(actor.itemConsumed());
    }

    @Test
    void aRefusedHostPlacementConsumesNothing() {
        actor.placingHost(world, TARGET, hostState).blockRefusesPlacement();

        GrabbyPlacementResult result = GrabbyPlacementTransaction.execute(
                world, actor, namedIngot(), hitTopOf(SUPPORT));

        assertEquals(GrabbyPlacementOutcome.REFUSED_BY_BLOCK, result.outcome());
        assertFalse(world.occupied(TARGET));
        assertFalse(actor.itemConsumed());
    }

    @Test
    void aHostThatCannotAcceptItsPayloadIsLeftProtected() {
        actor.placingHost(world, TARGET, hostState);
        world.payloadAttachFails();

        GrabbyPlacementResult result = GrabbyPlacementTransaction.execute(
                world, actor, namedIngot(), hitTopOf(SUPPORT));

        assertEquals(GrabbyPlacementOutcome.PLACED_WITHOUT_PROVENANCE, result.outcome());
        assertFalse(world.grabbyState(TARGET).grabbyManaged());
    }

    @Test
    void policyAppliesToHostPlacementJustAsItDoesToBlocks() {
        actor.placingHost(world, TARGET, hostState).insideSomebodyElsesHouse();

        assertEquals(GrabbyPlacementOutcome.DENIED_BY_POLICY,
                GrabbyPlacementTransaction.execute(world, actor, namedIngot(), hitTopOf(SUPPORT)).outcome());
        assertFalse(world.occupied(TARGET));
    }

    // ------------------------------------------------------------------
    // The duplication hazard the detach step exists for
    // ------------------------------------------------------------------

    @Test
    void pickingUpAHostDetachesThePayloadBeforeRemovalSoItCannotAlsoDrop() {
        actor.placingHost(world, TARGET, hostState);
        GrabbyPlacementTransaction.execute(world, actor, namedIngot(), hitTopOf(SUPPORT));

        FakeGrabbyActor collector = new FakeGrabbyActor(audio);
        GrabbyPickupResult pickup = GrabbyPickupTransaction.execute(world, collector, TARGET);

        assertEquals(GrabbyPickupOutcome.SUCCESS, pickup.outcome());
        assertEquals(1, world.detachCalls(), "the payload must be given up exactly once");
        assertTrue(world.payloadAt(TARGET).isEmpty(),
                "nothing may remain attached for the block's own removal path to drop");
        assertEquals(1, collector.inventory().size());
        assertFalse(world.occupied(TARGET));
    }

    @Test
    void aRefusedPickupNeverDetachesThePayload() {
        actor.placingHost(world, TARGET, hostState);
        GrabbyPlacementTransaction.execute(world, actor, namedIngot(), hitTopOf(SUPPORT));

        FakeGrabbyActor collector = new FakeGrabbyActor(audio).inventoryFull();
        GrabbyPickupResult pickup = GrabbyPickupTransaction.execute(world, collector, TARGET);

        assertEquals(GrabbyPickupOutcome.INVENTORY_FULL, pickup.outcome());
        assertEquals(0, world.detachCalls(), "a refused pickup must not disturb the object");
        assertFalse(world.payloadAt(TARGET).isEmpty());
        assertTrue(world.occupied(TARGET));
    }

    @Test
    void theItemComesBackFromTheHostWithItsDataIntact() {
        actor.placingHost(world, TARGET, hostState);
        GrabbyPlacementTransaction.execute(world, actor, namedIngot(), hitTopOf(SUPPORT));

        FakeGrabbyActor collector = new FakeGrabbyActor(audio);
        GrabbyPickupTransaction.execute(world, collector, TARGET);

        ItemStack recovered = collector.inventory().get(0);
        assertEquals(Items.GOLD_INGOT, recovered.getItem());
        assertEquals("Trinsic Bar", recovered.get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test
    void aHostPlacedByNobodyStaysImmovable() {
        world.placeRaw(TARGET, hostState, new ItemStack(Items.GOLD_INGOT));

        assertEquals(GrabbyPickupOutcome.NOT_GRABBY_MANAGED,
                GrabbyPickupTransaction.execute(world, actor, TARGET).outcome());
        assertTrue(world.occupied(TARGET));
    }
}
