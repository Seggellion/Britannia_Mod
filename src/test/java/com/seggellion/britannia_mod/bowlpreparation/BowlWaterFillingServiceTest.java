package com.seggellion.britannia_mod.bowlpreparation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BowlWaterFillingServiceTest {
    private static final Item EMPTY = Items.GLASS_BOTTLE;
    private static final Item OUTPUT = Items.POTION;

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void plansOnlyTheExactConfiguredEmptyBowl() {
        assertTrue(plan(new ItemStack(EMPTY)).isPresent());
        assertFalse(plan(new ItemStack(Items.BOWL)).isPresent());
        assertFalse(plan(new ItemStack(Items.BUCKET)).isPresent());
        assertFalse(plan(ItemStack.EMPTY).isPresent());
    }

    @Test
    void stackedInputConsumesExactlyOneAndCreatesOneOutput() {
        ItemStack input = new ItemStack(EMPTY, 4);
        BowlWaterFillingPlan plan = plan(input).orElseThrow();

        BowlWaterFillingService.Commit commit = BowlWaterFillingService.commit(plan, input);

        assertTrue(commit.applied());
        assertEquals(3, input.getCount());
        assertTrue(commit.output().is(OUTPUT));
        assertEquals(1, commit.output().getCount());
    }

    @Test
    void changedCountOrComponentsRejectsAtomically() {
        ItemStack countChanged = new ItemStack(EMPTY, 2);
        BowlWaterFillingPlan countPlan = plan(countChanged).orElseThrow();
        countChanged.grow(1);
        ItemStack beforeCountCommit = countChanged.copy();

        BowlWaterFillingService.Commit staleCount =
                BowlWaterFillingService.commit(countPlan, countChanged);

        assertFalse(staleCount.applied());
        assertTrue(staleCount.output().isEmpty());
        assertTrue(ItemStack.matches(beforeCountCommit, countChanged));

        ItemStack componentChanged = new ItemStack(EMPTY);
        BowlWaterFillingPlan componentPlan = plan(componentChanged).orElseThrow();
        componentChanged.set(DataComponents.CUSTOM_NAME, Component.literal("Changed"));
        ItemStack beforeComponentCommit = componentChanged.copy();

        BowlWaterFillingService.Commit staleComponent =
                BowlWaterFillingService.commit(componentPlan, componentChanged);

        assertFalse(staleComponent.applied());
        assertTrue(ItemStack.matches(beforeComponentCommit, componentChanged));
    }

    @Test
    void commitHasNoCreativeOrInfiniteMaterialsBypass() {
        ItemStack input = new ItemStack(EMPTY, 2);

        BowlWaterFillingService.Commit commit =
                BowlWaterFillingService.commit(plan(input).orElseThrow(), input);

        assertTrue(commit.applied());
        assertEquals(1, input.getCount());
        assertTrue(commit.output().is(OUTPUT));
    }

    private static java.util.Optional<BowlWaterFillingPlan> plan(ItemStack input) {
        return BowlWaterFillingService.plan(input, EMPTY, OUTPUT);
    }
}
