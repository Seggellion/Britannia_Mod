package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotTestFactory;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.item.DyeTubItem;
import com.seggellion.britannia_mod.dye.item.DyeTubLoadPlan;
import com.seggellion.britannia_mod.dye.item.DyeTubLoadResult;
import com.seggellion.britannia_mod.dye.item.DyeTubLoadingService;
import com.seggellion.britannia_mod.dye.item.DyeTubStateAccess;
import com.seggellion.britannia_mod.dye.item.PigmentItem;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DyeTubLoadingServiceTest {
    private DataComponentType<DyeTubState> component;
    private DyeTubItem tubItem;
    private PigmentItem redItem;
    private PigmentItem blueItem;
    private RegistrySnapshot production;

    @BeforeAll
    static void bootstrapRegistries() {
        com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone6RegisteredTestContent.ensureRegistered();
    }

    @BeforeEach
    void setUp() throws Exception {
        component = DataComponentRegistry.createDyeTubStateType();
        tubItem = new DyeTubItem(new Item.Properties().stacksTo(1).component(component, DyeTubState.empty()));
        redItem = new PigmentItem(new Item.Properties(), PigmentId.parse("britannia_mod:madder_red"));
        blueItem = new PigmentItem(new Item.Properties(), PigmentId.parse("britannia_mod:woad_blue"));
        production = DyeResolverFixtures.productionSnapshot();
    }

    @Test
    void survivalLoadsEmptyTubAndConsumesExactlyOne() {
        ItemStack tub = new ItemStack(tubItem);
        ItemStack pigment = new ItemStack(redItem, 4);

        DyeTubLoadPlan plan = plan(tub, pigment, production, true);
        DyeTubLoadResult result = DyeTubLoadingService.apply(plan, tub, pigment, false, component);

        assertEquals(DyeTubLoadResult.LOADED, result);
        assertEquals(redItem.pigmentId(), DyeTubStateAccess.read(tub, component).pigmentId().orElseThrow());
        assertTrue(DyeTubStateAccess.read(tub, component).remainingUses().isEmpty());
        assertEquals(3, pigment.getCount());
    }

    @Test
    void creativeLoadsWithoutConsumption() {
        ItemStack tub = new ItemStack(tubItem);
        ItemStack pigment = new ItemStack(redItem, 4);
        DyeTubLoadResult result = DyeTubLoadingService.apply(
                plan(tub, pigment, production, true), tub, pigment, true, component);
        assertEquals(DyeTubLoadResult.LOADED, result);
        assertEquals(4, pigment.getCount());
    }

    @Test
    void replacementIsDistinguishedAndPreservesUnrelatedComponents() {
        ItemStack tub = new ItemStack(tubItem);
        DyeTubStateAccess.write(tub, component, DyeTubState.loadedUnlimited(redItem.pigmentId()));
        tub.set(DataComponents.CUSTOM_NAME, Component.literal("Workshop Tub"));
        ItemStack pigment = new ItemStack(blueItem, 2);

        DyeTubLoadResult result = DyeTubLoadingService.apply(
                plan(tub, pigment, production, true), tub, pigment, false, component);

        assertEquals(DyeTubLoadResult.REPLACED, result);
        assertEquals(blueItem.pigmentId(), DyeTubStateAccess.read(tub, component).pigmentId().orElseThrow());
        assertEquals(Component.literal("Workshop Tub"), tub.get(DataComponents.CUSTOM_NAME));
        assertEquals(1, pigment.getCount());
    }

    @Test
    void samePigmentIsNoOpWithoutConsumptionOrSuccessEffects() {
        ItemStack tub = new ItemStack(tubItem);
        DyeTubState state = DyeTubState.loadedUnlimited(redItem.pigmentId());
        DyeTubStateAccess.write(tub, component, state);
        ItemStack pigment = new ItemStack(redItem, 2);

        DyeTubLoadPlan plan = plan(tub, pigment, production, true);
        DyeTubLoadResult result = DyeTubLoadingService.apply(plan, tub, pigment, false, component);

        assertEquals(DyeTubLoadResult.ALREADY_CONTAINS, result);
        assertFalse(result.emitsSuccessEffects());
        assertEquals(state, DyeTubStateAccess.read(tub, component));
        assertEquals(2, pigment.getCount());
    }

    @Test
    void emptyOffHandFailsAtomically() {
        assertFailureLeavesStacksUnchanged(
                ItemStack.EMPTY, production, true, DyeTubLoadResult.EMPTY_OFF_HAND);
    }

    @Test
    void unknownItemFailsAtomically() {
        assertFailureLeavesStacksUnchanged(
                new ItemStack(new Item(new Item.Properties())), production, true,
                DyeTubLoadResult.INVALID_OFF_HAND_ITEM);
    }

    @Test
    void unavailableRegistryFailsAtomically() {
        assertFailureLeavesStacksUnchanged(
                new ItemStack(redItem, 2), RegistrySnapshot.empty(), false,
                DyeTubLoadResult.REGISTRY_UNAVAILABLE);
    }

    @Test
    void missingDefinitionFailsAtomically() {
        assertFailureLeavesStacksUnchanged(
                new ItemStack(redItem, 2), RegistrySnapshot.empty(), true,
                DyeTubLoadResult.PIGMENT_DEFINITION_MISSING);
    }

    @Test
    void disabledDefinitionFailsAtomically() {
        PigmentDefinition red = production.pigments().require(redItem.pigmentId());
        RegistrySnapshot disabled = RegistrySnapshotTestFactory.pigmentSnapshot(Map.of(), java.util.List.of(red));
        assertFailureLeavesStacksUnchanged(
                new ItemStack(redItem, 2), disabled, true, DyeTubLoadResult.PIGMENT_DISABLED);
    }

    @Test
    void invalidTubFailsAtomically() {
        ItemStack wrongTub = new ItemStack(new Item(new Item.Properties()));
        ItemStack pigment = new ItemStack(redItem, 2);
        ItemStack beforeTub = wrongTub.copy();
        ItemStack beforePigment = pigment.copy();
        DyeTubLoadPlan plan = DyeTubLoadingService.plan(
                wrongTub, tubItem, pigment, lookup(), production, true, component);
        assertEquals(DyeTubLoadResult.INVALID_TUB, plan.result());
        DyeTubLoadingService.apply(plan, wrongTub, pigment, false, component);
        assertTrue(ItemStack.matches(beforeTub, wrongTub));
        assertTrue(ItemStack.matches(beforePigment, pigment));
    }

    @Test
    void stalePlanCannotModifyEitherStack() {
        ItemStack tub = new ItemStack(tubItem);
        ItemStack pigment = new ItemStack(redItem, 2);
        DyeTubLoadPlan plan = plan(tub, pigment, production, true);
        DyeTubState intervening = DyeTubState.loadedUnlimited(blueItem.pigmentId());
        DyeTubStateAccess.write(tub, component, intervening);

        DyeTubLoadResult result = DyeTubLoadingService.apply(plan, tub, pigment, false, component);

        assertEquals(DyeTubLoadResult.STATE_COMPONENT_FAILURE, result);
        assertEquals(intervening, DyeTubStateAccess.read(tub, component));
        assertEquals(2, pigment.getCount());
    }

    @Test
    void successfulLoadingChangesOneTubOnly() {
        ItemStack first = new ItemStack(tubItem);
        ItemStack second = new ItemStack(tubItem);
        ItemStack pigment = new ItemStack(redItem, 2);
        DyeTubLoadingService.apply(plan(first, pigment, production, true), first, pigment, false, component);
        assertNotEquals(DyeTubStateAccess.read(first, component), DyeTubStateAccess.read(second, component));
        assertEquals(DyeTubState.empty(), DyeTubStateAccess.read(second, component));
    }

    private void assertFailureLeavesStacksUnchanged(
            ItemStack pigment,
            RegistrySnapshot snapshot,
            boolean registryAvailable,
            DyeTubLoadResult expected) {
        ItemStack tub = new ItemStack(tubItem);
        tub.set(DataComponents.CUSTOM_NAME, Component.literal("Unchanged"));
        ItemStack beforeTub = tub.copy();
        ItemStack beforePigment = pigment.copy();
        DyeTubLoadPlan plan = plan(tub, pigment, snapshot, registryAvailable);
        assertEquals(expected, plan.result());
        assertEquals(expected, DyeTubLoadingService.apply(plan, tub, pigment, false, component));
        assertTrue(ItemStack.matches(beforeTub, tub));
        assertTrue(ItemStack.matches(beforePigment, pigment));
        assertFalse(expected.emitsSuccessEffects());
    }

    private DyeTubLoadPlan plan(
            ItemStack tub,
            ItemStack pigment,
            RegistrySnapshot snapshot,
            boolean registryAvailable) {
        return DyeTubLoadingService.plan(
                tub, tubItem, pigment, lookup(), snapshot, registryAvailable, component);
    }

    private static Function<Item, Optional<PigmentId>> lookup() {
        return item -> item instanceof PigmentItem pigmentItem
                ? Optional.of(pigmentItem.pigmentId())
                : Optional.empty();
    }
}
