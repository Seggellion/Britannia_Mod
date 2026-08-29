package com.seggellion.britannia_mod.crate;

import static com.seggellion.britannia_mod.crate.CrateVariant.MEDIUM;
import static com.seggellion.britannia_mod.crate.CrateVariant.SMALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What a column writes to disk, and what it does with data that comes back wrong.
 *
 * <p>The rule behind every case here is that saved data may be refused but must never be invented.
 * A column that guessed at a missing variant would read an inventory at the wrong size; one that
 * renumbered a duplicate id would hand two crates the same identity; one that trusted a low
 * {@code NextCrateId} would issue an id already in use. Each of those turns into lost or duplicated
 * items later, so each is corrected or dropped here, loudly.
 */
class CrateStackPersistenceTest {

    @BeforeAll
    static void bootstrap() {
        CrateStackTestSupport.bootstrap();
    }

    /* ─── round trip ─────────────────────────────────────────── */

    @Test
    void aMixedColumnSurvivesSaveAndLoadExactly() {
        CrateStackBlockEntity source = CrateStackTestSupport.emptyStack();
        int a = source.appendCrate(SMALL, Direction.WEST).orElseThrow();
        int b = source.appendCrate(MEDIUM, Direction.WEST).orElseThrow();
        int c = source.appendCrate(SMALL, Direction.WEST).orElseThrow();

        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Bane of Crates"));
        source.containerFor(a).setItem(0, named);
        source.containerFor(a).setItem(8, new ItemStack(Items.GOLD_INGOT, 11));
        source.containerFor(b).setItem(26, new ItemStack(Items.EMERALD, 2));
        source.containerFor(c).setItem(4, new ItemStack(Items.NETHERITE_SCRAP, 1));

        CrateStackBlockEntity loaded = roundTrip(source);

        assertEquals(3, loaded.crateCount());
        assertEquals(source.nextCrateId(), loaded.nextCrateId());
        assertEquals(source.totalHeightHundredths(), loaded.totalHeightHundredths());
        assertEquals(source.requiredCellCount(), loaded.requiredCellCount());

        assertEquals(a, loaded.crates().get(0).id());
        assertEquals(b, loaded.crates().get(1).id());
        assertEquals(c, loaded.crates().get(2).id());
        assertEquals(SMALL, loaded.crateById(a).variant());
        assertEquals(MEDIUM, loaded.crateById(b).variant());
        assertEquals(Direction.WEST, loaded.crateById(b).facing());

        assertEquals(Items.DIAMOND_SWORD, loaded.containerFor(a).getItem(0).getItem());
        assertEquals(Component.literal("Bane of Crates"),
                loaded.containerFor(a).getItem(0).get(DataComponents.CUSTOM_NAME),
                "item components must survive the trip");
        assertEquals(11, loaded.containerFor(a).getItem(8).getCount());
        assertTrue(loaded.containerFor(a).getItem(3).isEmpty(), "empty slots stay empty");
        assertEquals(2, loaded.containerFor(b).getItem(26).getCount());
        assertEquals(Items.NETHERITE_SCRAP, loaded.containerFor(c).getItem(4).getItem());
    }

    @Test
    void theLayoutAfterLoadingIsTheLayoutBeforeSaving() {
        CrateStackBlockEntity source = CrateStackTestSupport.stackOf(MEDIUM, SMALL, MEDIUM);
        CrateStackLayout before = source.layout();

        CrateStackLayout after = roundTrip(source).layout();

        assertEquals(before.totalHundredths(), after.totalHundredths());
        assertEquals(before.requiredCells(), after.requiredCells());
        assertEquals(before.placements(), after.placements());
    }

    @Test
    void anEmptyColumnRoundTripsAsAnEmptyColumn() {
        CrateStackBlockEntity loaded = roundTrip(CrateStackTestSupport.emptyStack());
        assertTrue(loaded.isEmpty());
        assertEquals(0, loaded.requiredCellCount());
        assertEquals(0, loaded.totalHeightHundredths());
    }

    /* ─── malformed data ─────────────────────────────────────── */

    @Test
    void aCrateOfUnknownVariantIsDroppedRatherThanGuessed() {
        CrateStackBlockEntity source = CrateStackTestSupport.stackOf(SMALL, MEDIUM);
        source.containerFor(1).setItem(0, new ItemStack(Items.EMERALD, 3));
        CompoundTag tag = save(source);
        crateEntry(tag, 0).putString("Variant", "enormous");

        CrateStackBlockEntity loaded = load(tag);

        assertEquals(1, loaded.crateCount(), "the unreadable crate is gone");
        assertEquals(MEDIUM, loaded.bottomCrate().variant(), "the readable one is untouched");
        assertEquals(3, loaded.containerFor(1).getItem(0).getCount());
    }

    @Test
    void aDuplicateIdKeepsTheFirstCrateAndDropsTheSecond() {
        CrateStackBlockEntity source = CrateStackTestSupport.stackOf(SMALL, SMALL);
        source.containerFor(0).setItem(0, new ItemStack(Items.DIAMOND, 1));
        source.containerFor(1).setItem(0, new ItemStack(Items.GOLD_INGOT, 1));
        CompoundTag tag = save(source);
        crateEntry(tag, 1).putInt("Id", 0);

        CrateStackBlockEntity loaded = load(tag);

        assertEquals(1, loaded.crateCount());
        assertEquals(0, loaded.bottomCrate().id());
        assertEquals(Items.DIAMOND, loaded.containerFor(0).getItem(0).getItem(),
                "the first crate with that id is the one kept");
    }

    @Test
    void aNextIdThatWouldReissueALiveIdIsRaisedPastIt() {
        CrateStackBlockEntity source = CrateStackTestSupport.stackOf(SMALL, SMALL, SMALL);
        CompoundTag tag = save(source);
        tag.putInt("NextCrateId", 1);

        CrateStackBlockEntity loaded = load(tag);

        assertEquals(3, loaded.nextCrateId(), "the counter must clear every id already in use");
        int fresh = loaded.appendCrate(SMALL, Direction.NORTH).orElseThrow();
        assertEquals(3, fresh);
        assertEquals(4, loaded.crateCount(), "the new crate did not collide with an existing one");
    }

    @Test
    void columnsSavedTallerThanTheCapAreTruncatedFromTheTop() {
        CompoundTag tag = new CompoundTag();
        ListTag crates = new ListTag();
        for (int index = 0; index < 12; index++) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Id", index);
            entry.putString("Variant", "small");
            entry.putString("Facing", "north");
            crates.add(entry);
        }
        tag.put("Crates", crates);
        tag.putInt("NextCrateId", 12);

        CrateStackBlockEntity loaded = load(tag);

        assertEquals(8, loaded.crateCount(), "only what fits inside 64 voxels is kept");
        assertEquals(0, loaded.bottomCrate().id(), "truncation drops the top, not the bottom");
        assertEquals(7, loaded.topCrate().id());
        assertTrue(loaded.requiredCellCount() <= CrateStackLayout.MAX_CELLS);
    }

    @Test
    void anUnreadableFacingFallsBackWithoutCostingTheCrate() {
        CrateStackBlockEntity source = CrateStackTestSupport.stackOf(SMALL);
        source.containerFor(0).setItem(0, new ItemStack(Items.DIAMOND, 6));
        CompoundTag tag = save(source);
        crateEntry(tag, 0).putString("Facing", "sideways");

        CrateStackBlockEntity loaded = load(tag);

        assertEquals(1, loaded.crateCount());
        assertEquals(Direction.NORTH, loaded.bottomCrate().facing());
        assertEquals(6, loaded.containerFor(0).getItem(0).getCount(),
                "a cosmetic field must never cost a crate its contents");
    }

    /** A vertical facing is as unusable as an unreadable one, and just as harmless. */
    @Test
    void aVerticalFacingIsCorrectedRatherThanThrown() {
        CrateStackBlockEntity source = CrateStackTestSupport.stackOf(SMALL);
        CompoundTag tag = save(source);
        crateEntry(tag, 0).putString("Facing", "up");

        CrateStackBlockEntity loaded = load(tag);

        assertEquals(1, loaded.crateCount());
        assertEquals(Direction.NORTH, loaded.bottomCrate().facing());
    }

    @Test
    void loadingReplacesTheColumnRatherThanAppendingToIt() {
        CrateStackBlockEntity target = CrateStackTestSupport.stackOf(MEDIUM, MEDIUM);
        CompoundTag tag = save(CrateStackTestSupport.stackOf(SMALL));

        target.loadWithComponents(tag, RegistryAccess.EMPTY);

        assertEquals(1, target.crateCount());
        assertEquals(SMALL, target.bottomCrate().variant());
        assertNull(target.crateById(99));
        assertNotNull(target.bottomCrate());
        assertFalse(target.isEmpty());
    }

    /* ─── helpers ────────────────────────────────────────────── */

    private static CompoundTag save(CrateStackBlockEntity stack) {
        return stack.saveWithoutMetadata(RegistryAccess.EMPTY);
    }

    private static CrateStackBlockEntity load(CompoundTag tag) {
        CrateStackBlockEntity loaded = CrateStackTestSupport.emptyStack();
        loaded.loadWithComponents(tag, RegistryAccess.EMPTY);
        return loaded;
    }

    private static CrateStackBlockEntity roundTrip(CrateStackBlockEntity source) {
        return load(save(source));
    }

    private static CompoundTag crateEntry(CompoundTag tag, int index) {
        return tag.getList("Crates", Tag.TAG_COMPOUND).getCompound(index);
    }
}
