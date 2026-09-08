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

class FertileDirtMixingServiceTest {
    private Item fertileBowl;
    private Item waterBowl;
    private Item fertilizedDirt;
    private Item emptyBowl;
    private FertileDirtMixingService.RecipeSet recipes;

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp() {
        fertileBowl = Items.CLAY_BALL;
        waterBowl = Items.POTION;
        fertilizedDirt = Items.DIRT;
        emptyBowl = Items.GLASS_BOTTLE;
        recipes = new FertileDirtMixingService.RecipeSet(
                fertileBowl, waterBowl, fertilizedDirt, emptyBowl);
    }

    @Test
    void plansOnlyTheExactFixedHandFinalTuple() {
        FertileDirtMixingPlan plan =
                plan(new ItemStack(fertileBowl), new ItemStack(waterBowl)).orElseThrow();
        assertEquals(fertilizedDirt, plan.fertilizedDirt());
        assertEquals(emptyBowl, plan.emptyBowl());

        assertFalse(plan(new ItemStack(waterBowl), new ItemStack(fertileBowl)).isPresent());
        assertFalse(plan(new ItemStack(fertileBowl), new ItemStack(Items.WATER_BUCKET)).isPresent());
        assertFalse(plan(new ItemStack(Items.BOWL), new ItemStack(waterBowl)).isPresent());
        assertFalse(plan(ItemStack.EMPTY, new ItemStack(waterBowl)).isPresent());
    }

    @Test
    void onePairCreatesCanonicalDirtAndReturnsBothBowls() {
        ItemStack main = new ItemStack(fertileBowl);
        ItemStack offhand = new ItemStack(waterBowl);

        FertileDirtMixingService.Commit commit =
                FertileDirtMixingService.commit(plan(main, offhand).orElseThrow(), main, offhand);

        assertTrue(commit.applied());
        assertTrue(main.isEmpty());
        assertTrue(offhand.isEmpty());
        assertTrue(commit.fertilizedDirt().is(fertilizedDirt));
        assertEquals(1, commit.fertilizedDirt().getCount());
        assertTrue(commit.returnedBowls().is(emptyBowl));
        assertEquals(FertileDirtMixingService.RETURNED_BOWL_COUNT,
                commit.returnedBowls().getCount());
    }

    @Test
    void stackedInputsConsumeExactlyOnePaidPair() {
        ItemStack main = new ItemStack(fertileBowl, 4);
        ItemStack offhand = new ItemStack(waterBowl, 3);

        FertileDirtMixingService.Commit commit =
                FertileDirtMixingService.commit(plan(main, offhand).orElseThrow(), main, offhand);

        assertTrue(commit.applied());
        assertEquals(3, main.getCount());
        assertEquals(2, offhand.getCount());
        assertEquals(1, commit.fertilizedDirt().getCount());
        assertEquals(2, commit.returnedBowls().getCount());
    }

    @Test
    void staleCountOrComponentsRejectsBothInputsAtomically() {
        ItemStack main = new ItemStack(fertileBowl, 2);
        ItemStack offhand = new ItemStack(waterBowl, 2);
        FertileDirtMixingPlan countPlan = plan(main, offhand).orElseThrow();
        main.grow(1);
        ItemStack beforeMain = main.copy();
        ItemStack beforeOffhand = offhand.copy();

        FertileDirtMixingService.Commit staleCount =
                FertileDirtMixingService.commit(countPlan, main, offhand);

        assertFalse(staleCount.applied());
        assertTrue(staleCount.fertilizedDirt().isEmpty());
        assertTrue(staleCount.returnedBowls().isEmpty());
        assertTrue(ItemStack.matches(beforeMain, main));
        assertTrue(ItemStack.matches(beforeOffhand, offhand));

        ItemStack changedMain = new ItemStack(fertileBowl);
        ItemStack changedOffhand = new ItemStack(waterBowl);
        FertileDirtMixingPlan componentPlan = plan(changedMain, changedOffhand).orElseThrow();
        changedOffhand.set(DataComponents.CUSTOM_NAME, Component.literal("Changed after planning"));
        ItemStack beforeChangedMain = changedMain.copy();
        ItemStack beforeChangedOffhand = changedOffhand.copy();

        FertileDirtMixingService.Commit staleComponent =
                FertileDirtMixingService.commit(componentPlan, changedMain, changedOffhand);

        assertFalse(staleComponent.applied());
        assertTrue(ItemStack.matches(beforeChangedMain, changedMain));
        assertTrue(ItemStack.matches(beforeChangedOffhand, changedOffhand));
    }

    @Test
    void commitHasNoCreativeOrInfiniteMaterialsBypass() {
        ItemStack main = new ItemStack(fertileBowl, 2);
        ItemStack offhand = new ItemStack(waterBowl, 2);

        FertileDirtMixingService.Commit commit =
                FertileDirtMixingService.commit(plan(main, offhand).orElseThrow(), main, offhand);

        assertTrue(commit.applied());
        assertEquals(1, main.getCount());
        assertEquals(1, offhand.getCount());
        assertEquals(1, commit.fertilizedDirt().getCount());
        assertEquals(2, commit.returnedBowls().getCount());
    }

    // ---------------------------------------------------------------- diagnosis

    @Test
    void holdingTheBowlWithAnEmptyOffHandSaysNothingAtAll() {
        // FertileDirtMixingItem is registered on the Bowl of Fertile Dirt and nothing else, so its
        // use() runs on every right-click while that bowl is held -- swinging it, clicking at the
        // air, walking about. The old test "one half of the mix is in hand" was therefore true
        // every single time, and every right-click printed "That bowl is not ready for this. Use an
        // Empty Bowl to gather, and a filled bowl to mix." at a player holding a perfectly correct
        // filled bowl, while never naming the requirement.
        assertEquals(FertileDirtMixingService.Diagnosis.NONE,
                diagnose(new ItemStack(fertileBowl), ItemStack.EMPTY));
        assertEquals(FertileDirtMixingService.Diagnosis.NONE,
                diagnose(new ItemStack(waterBowl), ItemStack.EMPTY),
                "an empty off hand is not an attempted mix whichever bowl is held");
        assertEquals(FertileDirtMixingService.Diagnosis.NONE,
                diagnose(ItemStack.EMPTY, ItemStack.EMPTY));
    }

    @Test
    void theSameSilenceTheDryStepAlreadyKept() {
        // BowlPreparationService is the reference: a filled bowl with an empty off hand has always
        // been silent there. The final mix now behaves the same way in the same situation.
        BowlPreparationService.RecipeSet dry = new BowlPreparationService.RecipeSet(
                Items.GLASS_BOTTLE, Items.GRAVEL, Items.CLAY_BALL, Items.BONE_MEAL, Items.MUD);
        assertEquals(BowlPreparationService.Diagnosis.NONE,
                BowlPreparationService.diagnose(new ItemStack(Items.CLAY_BALL), ItemStack.EMPTY, dry));
    }

    @Test
    void namesTheMissingOffHandItemInsteadOfBlamingTheBowlInHand() {
        // The right bowl in the main hand and the wrong thing in the off hand. The requirement --
        // a Bowl of Water in the off hand -- is the whole answer, and the old message never said it.
        assertEquals(FertileDirtMixingService.Diagnosis.MISSING_OFF_HAND,
                diagnose(new ItemStack(fertileBowl), new ItemStack(Items.WATER_BUCKET)));
        assertEquals(FertileDirtMixingService.Diagnosis.MISSING_OFF_HAND,
                diagnose(new ItemStack(fertileBowl), new ItemStack(emptyBowl)));
        assertEquals(FertileDirtMixingService.Diagnosis.MISSING_OFF_HAND,
                diagnose(new ItemStack(fertileBowl), new ItemStack(Items.STONE)));
    }

    @Test
    void stillNamesTheSwapAndStillCallsAWrongBowlWrong() {
        assertEquals(FertileDirtMixingService.Diagnosis.SWAP_HANDS,
                diagnose(new ItemStack(waterBowl), new ItemStack(fertileBowl)));
        assertEquals(FertileDirtMixingService.Diagnosis.WRONG_BOWL,
                diagnose(new ItemStack(Items.STONE), new ItemStack(waterBowl)));
        assertEquals(FertileDirtMixingService.Diagnosis.WRONG_BOWL,
                diagnose(new ItemStack(waterBowl), new ItemStack(Items.STONE)));
    }

    @Test
    void aValidPairIsNeverDiagnosedAtAll() {
        assertEquals(FertileDirtMixingService.Diagnosis.NONE,
                diagnose(new ItemStack(fertileBowl), new ItemStack(waterBowl)));
    }

    private FertileDirtMixingService.Diagnosis diagnose(ItemStack main, ItemStack offhand) {
        return FertileDirtMixingService.diagnose(main, offhand, recipes);
    }

    private java.util.Optional<FertileDirtMixingPlan> plan(
            ItemStack main,
            ItemStack offhand
    ) {
        return FertileDirtMixingService.plan(main, offhand, recipes);
    }
}
