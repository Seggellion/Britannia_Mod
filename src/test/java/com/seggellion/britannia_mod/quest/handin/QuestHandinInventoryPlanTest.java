package com.seggellion.britannia_mod.quest.handin;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The counting half of a hand-in, over real {@link ItemStack}s and without a server.
 *
 * <p>This is where "removes exactly the requested item and quantity and nothing else" is actually
 * decided, so the cases below are the rule written out: the exact count comes off, the remainder of
 * the stack stays, everything else in the pack is untouched, and a player short by one unit has a
 * plan refused rather than a stack shrunk.
 */
class QuestHandinInventoryPlanTest {

    /** The carried view is the 36 main slots plus the off-hand, in that order. */
    private static final int CARRIED_SLOTS = QuestHandinInventory.MAIN_SLOT_COUNT + 1;
    private static final int OFFHAND_INDEX = QuestHandinInventory.MAIN_SLOT_COUNT;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exactlyTheRequestedCountIsPlannedAndTheRemainderStays() {
        NonNullList<ItemStack> carried = empty();
        carried.set(4, new ItemStack(Items.CARROT, 17));
        carried.set(9, new ItemStack(Items.DIAMOND_PICKAXE));

        QuestHandinInventory.Planned planned = plan(carried,
                List.of(QuestHandinRemoval.literal(0, "minecraft:carrot", 1)));

        assertEquals(1, planned.takes().size());
        assertEquals(4, planned.takes().get(0).carriedIndex());
        assertEquals(1, planned.takes().get(0).count());
        assertEquals(0, planned.takes().get(0).requirementIndex());
        assertEquals(17, carried.get(4).getCount(), "planning never touches a stack");
        assertEquals(Items.DIAMOND_PICKAXE, carried.get(9).getItem(),
                "an unrelated tool is not part of any plan");
    }

    @Test
    void aToolTheQuestAwardedIsNeverPlannedBecauseNoRequirementNamesIt() {
        NonNullList<ItemStack> carried = empty();
        carried.set(0, new ItemStack(Items.WOODEN_SHOVEL));
        carried.set(1, new ItemStack(Items.BOWL, 2));
        carried.set(2, new ItemStack(Items.BUCKET));
        carried.set(3, new ItemStack(Items.WHEAT_SEEDS, 5));
        carried.set(4, new ItemStack(Items.GOLD_INGOT, 40));
        carried.set(5, new ItemStack(Items.CARROT, 3));

        QuestHandinInventory.Planned planned = plan(carried,
                List.of(QuestHandinRemoval.literal(0, "minecraft:carrot", 1)));

        assertEquals(1, planned.takes().size());
        assertEquals(5, planned.takes().get(0).carriedIndex(),
                "only the carrot is planned -- not the shovel, bowls, bucket, seeds or currency");
    }

    @Test
    void theOffHandCounts() {
        NonNullList<ItemStack> carried = empty();
        carried.set(OFFHAND_INDEX, new ItemStack(Items.CARROT, 1));

        QuestHandinInventory.Planned planned = plan(carried,
                List.of(QuestHandinRemoval.literal(0, "minecraft:carrot", 1)));

        assertEquals(OFFHAND_INDEX, planned.takes().get(0).carriedIndex());
        assertEquals(QuestHandinInventory.OFFHAND_SLOT,
                QuestHandinInventory.inventorySlot(OFFHAND_INDEX),
                "the last carried index is the player's off-hand slot");
    }

    @Test
    void aRequirementSpanningSeveralStacksTakesFromEachInSlotOrder() {
        NonNullList<ItemStack> carried = empty();
        carried.set(2, new ItemStack(Items.CARROT, 2));
        carried.set(7, new ItemStack(Items.CARROT, 1));
        carried.set(OFFHAND_INDEX, new ItemStack(Items.CARROT, 5));

        QuestHandinInventory.Planned planned = plan(carried,
                List.of(QuestHandinRemoval.literal(0, "minecraft:carrot", 4)));

        assertEquals(List.of(2, 7, OFFHAND_INDEX),
                planned.takes().stream().map(QuestHandinInventory.SlotTake::carriedIndex).toList());
        assertEquals(List.of(2, 1, 1),
                planned.takes().stream().map(QuestHandinInventory.SlotTake::count).toList());
        assertEquals(4, planned.takes().stream()
                .mapToInt(QuestHandinInventory.SlotTake::count).sum());
    }

    @Test
    void twoRequirementsForTheSameItemAggregateTheirDemandButKeepTheirOwnProof() {
        NonNullList<ItemStack> carried = empty();
        carried.set(0, new ItemStack(Items.CARROT, 3));

        QuestHandinInventory.Planned planned = plan(carried,
                List.of(QuestHandinRemoval.literal(0, "minecraft:carrot", 1),
                        QuestHandinRemoval.literal(1, "minecraft:carrot", 2)));

        assertEquals(3, planned.takes().stream()
                .mapToInt(QuestHandinInventory.SlotTake::count).sum());
        assertEquals(List.of(0, 1), planned.takes().stream()
                .map(QuestHandinInventory.SlotTake::requirementIndex).toList(),
                "each requirement keeps its own entry; merging them is what Rails refuses");
    }

    @Test
    void twoRequirementsForTheSameItemAreShortWhenOnlyOneCouldBeMet() {
        NonNullList<ItemStack> carried = empty();
        carried.set(0, new ItemStack(Items.CARROT, 2));

        QuestHandinInventory.Insufficient shortfall = assertInstanceOf(
                QuestHandinInventory.Insufficient.class,
                QuestHandinInventory.plan(carried,
                        List.of(QuestHandinRemoval.literal(0, "minecraft:carrot", 1),
                                QuestHandinRemoval.literal(1, "minecraft:carrot", 2))));

        assertEquals(1, shortfall.missing().size());
        assertEquals("minecraft:carrot", shortfall.missing().get(0).itemId());
        assertEquals(1, shortfall.missing().get(0).count(),
                "the second requirement can only be met by one of its two");
    }

    @Test
    void shortByOneMeansNothingIsPlannedAtAll() {
        NonNullList<ItemStack> carried = empty();
        carried.set(0, new ItemStack(Items.CARROT, 2));
        carried.set(1, new ItemStack(Items.WHEAT, 64));

        QuestHandinInventory.Insufficient shortfall = assertInstanceOf(
                QuestHandinInventory.Insufficient.class,
                QuestHandinInventory.plan(carried,
                        List.of(QuestHandinRemoval.literal(0, "minecraft:carrot", 3))));

        assertEquals(1, shortfall.missing().get(0).count());
        assertEquals(2, carried.get(0).getCount());
        assertEquals(64, carried.get(1).getCount());
    }

    @Test
    void aShortfallReportsEveryMissingItemSoThePlayerIsToldTheWholeStory() {
        NonNullList<ItemStack> carried = empty();
        carried.set(0, new ItemStack(Items.CARROT, 1));

        QuestHandinInventory.Insufficient shortfall = assertInstanceOf(
                QuestHandinInventory.Insufficient.class,
                QuestHandinInventory.plan(carried,
                        List.of(QuestHandinRemoval.literal(0, "minecraft:carrot", 2),
                                QuestHandinRemoval.literal(1, "minecraft:wheat", 1))));

        assertEquals(2, shortfall.missing().size());
        assertEquals("minecraft:carrot", shortfall.missing().get(0).itemId());
        assertEquals(1, shortfall.missing().get(0).count());
        assertEquals("minecraft:wheat", shortfall.missing().get(1).itemId());
        assertEquals(1, shortfall.missing().get(1).count());
    }

    @Test
    void anEmptyPackIsShortRatherThanSatisfied() {
        QuestHandinInventory.Insufficient shortfall = assertInstanceOf(
                QuestHandinInventory.Insufficient.class,
                QuestHandinInventory.plan(empty(),
                        List.of(QuestHandinRemoval.literal(0, "minecraft:carrot", 1))));

        assertEquals(1, shortfall.missing().get(0).count());
    }

    @Test
    void anItemIdThatNamesNothingRegisteredIsAShortfallAndNotACrash() {
        NonNullList<ItemStack> carried = empty();
        carried.set(0, new ItemStack(Items.CARROT, 8));

        QuestHandinInventory.Insufficient shortfall = assertInstanceOf(
                QuestHandinInventory.Insufficient.class,
                QuestHandinInventory.plan(carried,
                        List.of(QuestHandinRemoval.literal(0, "britannia_mod:not_an_item", 1))));

        assertEquals("britannia_mod:not_an_item", shortfall.missing().get(0).itemId());
        assertEquals(8, carried.get(0).getCount());
    }

    @Test
    void anExistingStackAlreadySatisfiesTheRequirementWithNoExtraCollection() {
        NonNullList<ItemStack> carried = empty();
        carried.set(11, new ItemStack(Items.CARROT, 64));

        QuestHandinInventory.Planned planned = plan(carried,
                List.of(QuestHandinRemoval.literal(0, "minecraft:carrot", 1)));

        assertEquals(1, planned.takes().size());
        assertEquals(1, planned.takes().get(0).count(),
                "a full stack satisfies a one-unit hand-in; the other 63 are the player's");
    }

    @Test
    void theCarriedViewIsThirtySevenSlotsAndArmourIsNotOneOfThem() {
        assertEquals(37, CARRIED_SLOTS);
        for (int index = 0; index < QuestHandinInventory.MAIN_SLOT_COUNT; index++) {
            assertEquals(index, QuestHandinInventory.inventorySlot(index));
        }
        assertEquals(40, QuestHandinInventory.inventorySlot(OFFHAND_INDEX));
        assertTrue(QuestHandinInventory.inventorySlot(OFFHAND_INDEX)
                        > QuestHandinInventory.MAIN_SLOT_COUNT + 3,
                "the off-hand is past the four armour slots, which this contract never reads");
    }

    private static QuestHandinInventory.Planned plan(List<ItemStack> carried,
                                                     List<QuestHandinRemoval> requirements) {
        return assertInstanceOf(QuestHandinInventory.Planned.class,
                QuestHandinInventory.plan(carried, requirements));
    }

    private static NonNullList<ItemStack> empty() {
        return NonNullList.withSize(CARRIED_SLOTS, ItemStack.EMPTY);
    }
}
