package com.seggellion.britannia_mod.quest.delivery;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M3: the all-or-nothing planner over real {@link ItemStack}s, without a
 * server. Vanilla's own {@code Inventory#add} is not used for deliveries because it grants what
 * fits and, for a creative player, reports success for a stack it discarded.
 */
class QuestRewardDeliveryInventoryInsertionTest {
    private static final ToIntFunction<ItemStack> VANILLA_CEILING = stack -> Math.min(99, stack.getMaxStackSize());

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everythingFitsIntoPartialStacksThenEmptySlots() {
        NonNullList<ItemStack> slots = NonNullList.withSize(36, ItemStack.EMPTY);
        slots.set(0, new ItemStack(Items.STONE, 60));
        slots.set(5, new ItemStack(Items.STONE, 64));

        List<ItemStack> planned = QuestRewardDeliveryInventoryInsertion.plan(slots, VANILLA_CEILING,
            List.of(new ItemStack(Items.STONE, 10), new ItemStack(Items.DIAMOND_PICKAXE)));

        assertNotNull(planned);
        assertEquals(64, planned.get(0).getCount(), "the partial stack is topped up first");
        assertEquals(64, planned.get(5).getCount(), "a full stack is left alone");
        assertEquals(Items.STONE, planned.get(1).getItem());
        assertEquals(6, planned.get(1).getCount(), "the remainder opens the first empty slot");
        assertEquals(Items.DIAMOND_PICKAXE, planned.get(2).getItem());
        assertEquals(60, slots.get(0).getCount(), "the plan never touches the real slots");
        assertTrue(slots.get(1).isEmpty());
    }

    @Test
    void aStackThatDoesNotFullyFitRejectsTheWholePlan() {
        NonNullList<ItemStack> slots = NonNullList.withSize(36, ItemStack.EMPTY);
        for (int i = 0; i < 36; i++) slots.set(i, new ItemStack(Items.COBBLESTONE, 64));
        slots.set(3, new ItemStack(Items.COBBLESTONE, 60));

        assertNull(QuestRewardDeliveryInventoryInsertion.plan(slots, VANILLA_CEILING,
            List.of(new ItemStack(Items.COBBLESTONE, 5))), "only four of five would fit");
        assertNull(QuestRewardDeliveryInventoryInsertion.plan(slots, VANILLA_CEILING,
            List.of(new ItemStack(Items.COBBLESTONE, 4), new ItemStack(Items.DIAMOND))),
            "the first stack fits, the second has no slot: nothing may be placed");
        assertNotNull(QuestRewardDeliveryInventoryInsertion.plan(slots, VANILLA_CEILING,
            List.of(new ItemStack(Items.COBBLESTONE, 4))));
        for (ItemStack slot : slots) assertEquals(Items.COBBLESTONE, slot.getItem());
    }

    @Test
    void unstackableItemsOnlyTakeEmptySlotsAndComponentsMustAgreeToMerge() {
        NonNullList<ItemStack> slots = NonNullList.withSize(36, ItemStack.EMPTY);
        slots.set(0, new ItemStack(Items.DIAMOND_PICKAXE));
        ItemStack renamed = new ItemStack(Items.STONE, 1);
        renamed.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("odd"));
        slots.set(1, renamed);

        List<ItemStack> planned = QuestRewardDeliveryInventoryInsertion.plan(slots, VANILLA_CEILING,
            List.of(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.STONE, 3)));

        assertNotNull(planned);
        assertEquals(1, planned.get(0).getCount());
        assertEquals(Items.DIAMOND_PICKAXE, planned.get(2).getItem(), "a tool never merges into a tool");
        assertEquals(1, planned.get(1).getCount(), "a stack with different components is not topped up");
        assertEquals(3, planned.get(3).getCount());
    }

    @Test
    void anEmptyRewardListIsAFitThatChangesNothing() {
        NonNullList<ItemStack> slots = NonNullList.withSize(36, ItemStack.EMPTY);
        List<ItemStack> planned = QuestRewardDeliveryInventoryInsertion.plan(slots, VANILLA_CEILING, List.of());
        assertNotNull(planned);
        for (ItemStack slot : planned) assertTrue(slot.isEmpty());
    }
}
