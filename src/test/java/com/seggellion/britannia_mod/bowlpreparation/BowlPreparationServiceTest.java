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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BowlPreparationServiceTest {
    private Item emptyBowl;
    private Item dirt;
    private Item bowlOfDirt;
    private Item dung;
    private Item bowlOfFertileDirt;
    private BowlPreparationService.RecipeSet recipes;

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp() {
        emptyBowl = Items.GLASS_BOTTLE;
        dirt = Items.DIRT;
        bowlOfDirt = Items.CLAY_BALL;
        dung = Items.ROTTEN_FLESH;
        bowlOfFertileDirt = Items.BRICK;
        recipes = new BowlPreparationService.RecipeSet(
                emptyBowl, dirt, bowlOfDirt, dung, bowlOfFertileDirt);
    }

    @Test
    void plansOnlyTheTwoExactFixedHandTuples() {
        BowlPreparationPlan dirtPlan = plan(new ItemStack(emptyBowl), new ItemStack(dirt)).orElseThrow();
        assertEquals(BowlPreparationPlan.Step.DIRT, dirtPlan.step());
        assertEquals(bowlOfDirt, dirtPlan.output());

        BowlPreparationPlan fertile = plan(new ItemStack(bowlOfDirt), new ItemStack(dung)).orElseThrow();
        assertEquals(BowlPreparationPlan.Step.FERTILE_MIX, fertile.step());
        assertEquals(bowlOfFertileDirt, fertile.output());

        assertFalse(plan(new ItemStack(Items.BOWL), new ItemStack(dirt)).isPresent());
        assertFalse(plan(new ItemStack(Items.BUCKET), new ItemStack(dirt)).isPresent());
        assertFalse(plan(new ItemStack(emptyBowl), new ItemStack(Items.COARSE_DIRT)).isPresent());
        assertFalse(plan(new ItemStack(dirt), new ItemStack(emptyBowl)).isPresent());
        assertFalse(plan(new ItemStack(dung), new ItemStack(bowlOfDirt)).isPresent());
    }

    @Test
    void stackedInputsCommitExactlyOnePaidPair() {
        ItemStack main = new ItemStack(emptyBowl, 4);
        ItemStack offhand = new ItemStack(dirt, 3);
        BowlPreparationPlan plan = plan(main, offhand).orElseThrow();

        BowlPreparationService.Commit commit = BowlPreparationService.commit(plan, main, offhand);

        assertTrue(commit.applied());
        assertEquals(3, main.getCount());
        assertEquals(2, offhand.getCount());
        assertTrue(commit.output().is(bowlOfDirt));
        assertEquals(1, commit.output().getCount());
    }

    @Test
    void staleMainOrOffhandRejectsAtomically() {
        ItemStack main = new ItemStack(emptyBowl, 2);
        ItemStack offhand = new ItemStack(dirt, 2);
        BowlPreparationPlan plan = plan(main, offhand).orElseThrow();
        main.grow(1);
        ItemStack changedMain = main.copy();
        ItemStack unchangedOffhand = offhand.copy();

        BowlPreparationService.Commit staleCount = BowlPreparationService.commit(plan, main, offhand);

        assertFalse(staleCount.applied());
        assertTrue(staleCount.output().isEmpty());
        assertTrue(ItemStack.matches(changedMain, main));
        assertTrue(ItemStack.matches(unchangedOffhand, offhand));

        ItemStack secondMain = new ItemStack(bowlOfDirt, 2);
        ItemStack secondOffhand = new ItemStack(dung, 2);
        BowlPreparationPlan secondPlan = plan(secondMain, secondOffhand).orElseThrow();
        secondOffhand.set(DataComponents.CUSTOM_NAME, Component.literal("Changed after planning"));
        ItemStack beforeMain = secondMain.copy();
        ItemStack beforeOffhand = secondOffhand.copy();

        BowlPreparationService.Commit staleComponent =
                BowlPreparationService.commit(secondPlan, secondMain, secondOffhand);

        assertFalse(staleComponent.applied());
        assertTrue(ItemStack.matches(beforeMain, secondMain));
        assertTrue(ItemStack.matches(beforeOffhand, secondOffhand));
    }

    @Test
    void commitsHaveNoCreativeOrInfiniteMaterialsBypass() {
        ItemStack main = new ItemStack(bowlOfDirt, 2);
        ItemStack offhand = new ItemStack(dung, 2);

        BowlPreparationService.Commit commit =
                BowlPreparationService.commit(plan(main, offhand).orElseThrow(), main, offhand);

        assertTrue(commit.applied());
        assertEquals(1, main.getCount());
        assertEquals(1, offhand.getCount());
        assertTrue(commit.output().is(bowlOfFertileDirt));
    }

    private java.util.Optional<BowlPreparationPlan> plan(ItemStack main, ItemStack offhand) {
        return BowlPreparationService.plan(main, offhand, recipes);
    }
}
