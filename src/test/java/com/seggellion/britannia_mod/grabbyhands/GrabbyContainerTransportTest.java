package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.grabbyhands.testsupport.FakeGrabbyActor;
import com.seggellion.britannia_mod.grabbyhands.testsupport.FakeGrabbyWorld;
import com.seggellion.britannia_mod.grabbyhands.testsupport.GrabbyTagBinding;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Container transport: contents must move exactly once, and never twice.
 *
 * <p>The dangerous case is specific and known. {@code BritanniaChestBlock.onRemove} calls
 * {@code Containers.dropContents} on any block change, which is correct when something else destroys
 * the chest and catastrophic during a pickup — the player would receive the whole inventory in an item
 * and again on the floor. These tests cover the contents-serialisation format and the detach step that
 * closes that hole.
 */
class GrabbyContainerTransportTest {
    private static final BlockPos POS = new BlockPos(6, 64, 6);
    private static final UUID PLACER = UUID.fromString("dddddddd-1111-2222-3333-444444444444");
    private static final int SIZE = 27;

    private static BlockState containerState;
    private static HolderLookup.Provider registries;

    private List<String> audio;
    private FakeGrabbyWorld world;
    private FakeGrabbyActor actor;

    @BeforeAll
    static void bootstrapAndEnrolATestContainer() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GrabbyTagBinding.enrol(Blocks.OAK_STAIRS);
        containerState = Blocks.OAK_STAIRS.defaultBlockState();
        registries = new RegistryAccess.ImmutableRegistryAccess(BuiltInRegistries.REGISTRY.stream().toList());
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

    // ------------------------------------------------------------------
    // The serialisation format the containers actually use
    // ------------------------------------------------------------------

    private static NonNullList<ItemStack> filledInventory() {
        NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        items.set(0, new ItemStack(Items.GOLD_INGOT, 5));
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Britannian Steel"));
        named.setDamageValue(11);
        items.set(4, named);
        items.set(26, new ItemStack(Items.BREAD, 3));
        return items;
    }

    /** Mirrors writePortableState then the vanilla restore during BlockItem.place. */
    private static NonNullList<ItemStack> roundTrip(NonNullList<ItemStack> original) {
        CompoundTag written = new CompoundTag();
        ContainerHelper.saveAllItems(written, original, registries);

        ItemStack portable = new ItemStack(Items.CHEST);
        portable.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(written));

        CompoundTag read = portable.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        NonNullList<ItemStack> restored = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(read, restored, registries);
        return restored;
    }

    @Test
    void everyStackInAFilledContainerSurvivesTheRoundTripExactly() {
        NonNullList<ItemStack> original = filledInventory();

        NonNullList<ItemStack> restored = roundTrip(original);

        for (int slot = 0; slot < SIZE; slot++) {
            assertTrue(ItemStack.matches(original.get(slot), restored.get(slot)), "slot " + slot);
        }
    }

    @Test
    void slotPositionsArePreservedRatherThanCompacted() {
        NonNullList<ItemStack> restored = roundTrip(filledInventory());

        assertTrue(restored.get(1).isEmpty(), "an empty slot must stay empty");
        assertEquals(Items.BREAD, restored.get(26).getItem(), "the last slot must not migrate");
    }

    @Test
    void itemDataInsideAContainerSurvives() {
        NonNullList<ItemStack> restored = roundTrip(filledInventory());

        ItemStack sword = restored.get(4);
        assertEquals("Britannian Steel", sword.get(DataComponents.CUSTOM_NAME).getString());
        assertEquals(11, sword.getDamageValue());
    }

    @Test
    void anEmptyContainerRoundTripsAsEmpty() {
        NonNullList<ItemStack> empty = NonNullList.withSize(SIZE, ItemStack.EMPTY);

        NonNullList<ItemStack> restored = roundTrip(empty);

        for (ItemStack stack : restored) {
            assertTrue(stack.isEmpty());
        }
    }

    @Test
    void repeatedRoundTripsDoNotDrift() {
        NonNullList<ItemStack> carried = filledInventory();
        for (int cycle = 0; cycle < 3; cycle++) {
            carried = roundTrip(carried);
        }
        NonNullList<ItemStack> original = filledInventory();
        for (int slot = 0; slot < SIZE; slot++) {
            assertTrue(ItemStack.matches(original.get(slot), carried.get(slot)), "slot " + slot);
        }
    }

    // ------------------------------------------------------------------
    // The double-drop hazard
    // ------------------------------------------------------------------

    private void placeFilledContainer() {
        ItemStack portable = new ItemStack(Items.CHEST);
        CompoundTag written = new CompoundTag();
        ContainerHelper.saveAllItems(written, filledInventory(), registries);
        portable.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(written));

        world.place(POS, containerState, GrabbyInstanceState.playerPlaced(PLACER, 5L), portable);
        world.attachPayload(POS, portable);
    }

    @Test
    void pickingUpAContainerMakesItGiveUpItsContentsBeforeRemoval() {
        placeFilledContainer();

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(world, actor, POS);

        assertEquals(GrabbyPickupOutcome.SUCCESS, result.outcome());
        assertEquals(1, world.detachCalls(),
                "without this the chest's own onRemove would spill a second copy on the floor");
        assertEquals(1, actor.inventory().size());
        assertTrue(actor.dropped().isEmpty(), "nothing may hit the floor during a pickup");
    }

    @Test
    void theContainerItemCarriesItsContentsIntoTheInventory() {
        placeFilledContainer();

        GrabbyPickupTransaction.execute(world, actor, POS);

        ItemStack carried = actor.inventory().get(0);
        CompoundTag data = carried.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        NonNullList<ItemStack> restored = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(data, restored, registries);

        assertEquals(Items.GOLD_INGOT, restored.get(0).getItem());
        assertEquals(5, restored.get(0).getCount());
        assertEquals("Britannian Steel", restored.get(4).get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test
    void aFullInventoryLeavesTheContainerAndItsContentsCompletelyUntouched() {
        placeFilledContainer();
        actor.inventoryFull();

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(world, actor, POS);

        assertEquals(GrabbyPickupOutcome.INVENTORY_FULL, result.outcome());
        assertEquals(0, world.detachCalls(), "a refused pickup must never empty a container");
        assertTrue(world.occupied(POS));
        assertFalse(world.payloadAt(POS).isEmpty());
    }

    @Test
    void aFailedRemovalPutsTheContentsBack() {
        // Unreachable in practice, but the cost of being wrong is a destroyed inventory.
        placeFilledContainer();
        world.removalFails();

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(world, actor, POS);

        assertEquals(GrabbyPickupOutcome.REMOVAL_FAILED, result.outcome());
        assertEquals(1, world.restoreCalls(), "the contents must be handed back");
        assertFalse(world.payloadAt(POS).isEmpty());
        assertTrue(actor.inventory().isEmpty());
    }

    // ------------------------------------------------------------------
    // Refusals the object raises for itself
    // ------------------------------------------------------------------

    @Test
    void aContainerSomebodyIsLookingInsideRefusesToMove() {
        placeFilledContainer();
        world.refusingTransport(GrabbyTransportRefusal.IN_USE);

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(world, actor, POS);

        assertEquals(GrabbyPickupOutcome.IN_USE, result.outcome());
        assertEquals(0, world.detachCalls());
        assertTrue(world.occupied(POS));
        assertTrue(audio.isEmpty(), "a refusal is silent");
    }

    @Test
    void aContainerHoldingAnotherFilledContainerRefusesToMove() {
        placeFilledContainer();
        world.refusingTransport(GrabbyTransportRefusal.NESTED_CONTAINER);

        GrabbyPickupResult result = GrabbyPickupTransaction.execute(world, actor, POS);

        assertEquals(GrabbyPickupOutcome.NESTED_CONTAINER, result.outcome());
        assertTrue(world.occupied(POS));
        assertEquals(0, world.detachCalls());
    }

    @Test
    void aRefusalIsTreatedAsHandledSoTheChestDoesNotAlsoOpen() {
        // The player asked to pick it up. Falling through would open the menu instead, which reads as
        // the game ignoring them.
        assertTrue(GrabbyPickupOutcome.IN_USE.handled());
        assertTrue(GrabbyPickupOutcome.NESTED_CONTAINER.handled());
        assertFalse(GrabbyPickupOutcome.IN_USE.consumedObject());
        assertFalse(GrabbyPickupOutcome.NESTED_CONTAINER.consumedObject());
    }

    @Test
    void anUnmarkedContainerStaysImmovable() {
        world.placeRaw(POS, containerState, new ItemStack(Items.CHEST));

        assertEquals(GrabbyPickupOutcome.NOT_GRABBY_MANAGED,
                GrabbyPickupTransaction.execute(world, actor, POS).outcome());
        assertTrue(world.occupied(POS));
    }
}
