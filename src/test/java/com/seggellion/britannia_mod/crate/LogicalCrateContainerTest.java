package com.seggellion.britannia_mod.crate;

import static com.seggellion.britannia_mod.crate.CrateVariant.MEDIUM;
import static com.seggellion.britannia_mod.crate.CrateVariant.SMALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Each crate in a column is its own container, and stays its own container while the column changes.
 *
 * <p>This is the property the whole architecture is subordinate to. Several crates share one block
 * entity, so the only thing separating one player's inventory from another's is that every container
 * view resolves by crate id on each call. A view that cached a list index would, the moment a crate
 * below it was removed, quietly start reading and writing its neighbour's items.
 */
class LogicalCrateContainerTest {

    @BeforeAll
    static void bootstrap() {
        CrateStackTestSupport.bootstrap();
    }

    /* ─── isolation ──────────────────────────────────────────── */

    @Test
    void eachCrateHoldsItsOwnItems() {
        CrateStackBlockEntity stack = CrateStackTestSupport.emptyStack();
        int a = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        int b = stack.appendCrate(MEDIUM, Direction.NORTH).orElseThrow();

        stack.containerFor(a).setItem(0, new ItemStack(Items.DIAMOND, 3));
        stack.containerFor(b).setItem(0, new ItemStack(Items.GOLD_INGOT, 7));

        assertEquals(Items.DIAMOND, stack.containerFor(a).getItem(0).getItem());
        assertEquals(3, stack.containerFor(a).getItem(0).getCount());
        assertEquals(Items.GOLD_INGOT, stack.containerFor(b).getItem(0).getItem());
        assertEquals(7, stack.containerFor(b).getItem(0).getCount());
        assertEquals(9, stack.containerFor(a).getContainerSize());
        assertEquals(27, stack.containerFor(b).getContainerSize());
    }

    @Test
    void changingOneCrateLeavesTheOthersAlone() {
        CrateStackBlockEntity stack = CrateStackTestSupport.emptyStack();
        int a = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        int b = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        stack.containerFor(a).setItem(0, new ItemStack(Items.DIAMOND, 3));
        stack.containerFor(b).setItem(0, new ItemStack(Items.GOLD_INGOT, 7));

        stack.containerFor(a).clearContent();

        assertTrue(stack.containerFor(a).isEmpty());
        assertEquals(Items.GOLD_INGOT, stack.containerFor(b).getItem(0).getItem());
        assertEquals(7, stack.containerFor(b).getItem(0).getCount());
    }

    @Test
    void removingOneCrateLeavesTheOthersContentsExactlyAsTheyWere() {
        CrateStackBlockEntity stack = CrateStackTestSupport.emptyStack();
        int a = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        int b = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        stack.containerFor(a).setItem(0, new ItemStack(Items.DIAMOND, 3));
        stack.containerFor(b).setItem(0, new ItemStack(Items.GOLD_INGOT, 7));

        LogicalCrate removed = stack.removeCrate(a).orElseThrow();

        assertEquals(Items.GOLD_INGOT, stack.containerFor(b).getItem(0).getItem());
        assertEquals(7, stack.containerFor(b).getItem(0).getCount());
        assertFalse(removed.isEmpty(), "a removed crate leaves holding its own contents");
    }

    /* ─── identity across repacking ──────────────────────────── */

    /**
     * The case ids exist for.
     *
     * <p>A view opened on the top crate must survive a crate below it being removed — which moves this
     * crate down a whole index — and must stop resolving once this crate itself is gone, rather than
     * inheriting whatever took its place.
     */
    @Test
    void aViewFollowsItsCrateDownTheColumnAndDiesWithIt() {
        CrateStackBlockEntity stack = CrateStackTestSupport.emptyStack();
        int a = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        int b = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        int c = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        stack.containerFor(c).setItem(0, new ItemStack(Items.EMERALD, 4));

        Container viewOfC = stack.containerFor(c);
        assertEquals(2, indexOf(stack, c));

        stack.removeCrate(b);

        assertEquals(1, indexOf(stack, c), "C moved down when B was removed");
        assertEquals(Items.EMERALD, viewOfC.getItem(0).getItem(),
                "the view still addresses C, not whichever crate now holds its old index");
        assertEquals(4, viewOfC.getItem(0).getCount());
        assertEquals(9, viewOfC.getContainerSize());

        stack.removeCrate(c);

        assertEquals(0, viewOfC.getContainerSize(), "a view of a departed crate resolves to nothing");
        assertTrue(viewOfC.isEmpty());
        assertTrue(viewOfC.getItem(0).isEmpty());
        assertEquals(1, stack.crateCount());
        assertEquals(a, stack.bottomCrate().id());
    }

    /** Writing through a dead view must not land in a surviving crate. */
    @Test
    void aViewOfADepartedCrateCannotWriteIntoItsNeighbours() {
        CrateStackBlockEntity stack = CrateStackTestSupport.emptyStack();
        int a = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        int b = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        Container viewOfB = stack.containerFor(b);

        stack.removeCrate(b);
        viewOfB.setItem(0, new ItemStack(Items.NETHERITE_INGOT, 1));

        assertTrue(stack.containerFor(a).isEmpty(), "a dead view wrote into a surviving crate");
        assertTrue(viewOfB.getItem(0).isEmpty());
    }

    /* ─── opener state ───────────────────────────────────────── */

    /**
     * Opening one crate must not mark the column as open.
     *
     * <p>Later milestones read this for chest sounds, for whether Grabby Hands will carry a crate, and
     * for simultaneous access; a single count for the whole block entity would make every one of those
     * answer for crates nobody touched.
     */
    @Test
    void openersAreCountedPerCrateRatherThanPerColumn() {
        CrateStackBlockEntity stack = CrateStackTestSupport.emptyStack();
        int a = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        int b = stack.appendCrate(SMALL, Direction.NORTH).orElseThrow();

        stack.crateById(b).incrementOpeners();

        assertEquals(0, stack.crateById(a).openerCount());
        assertEquals(1, stack.crateById(b).openerCount());

        stack.crateById(b).decrementOpeners();
        assertEquals(0, stack.crateById(b).openerCount());

        stack.crateById(b).decrementOpeners();
        assertEquals(0, stack.crateById(b).openerCount(), "an opener count never goes negative");
    }

    private static int indexOf(CrateStackBlockEntity stack, int crateId) {
        for (int index = 0; index < stack.crates().size(); index++) {
            if (stack.crates().get(index).id() == crateId) {
                return index;
            }
        }
        return -1;
    }
}
