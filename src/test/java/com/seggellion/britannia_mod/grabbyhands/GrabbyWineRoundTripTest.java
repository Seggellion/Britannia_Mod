package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.block.entity.WineBottleBlockEntity;
import com.seggellion.britannia_mod.component.WineData;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mandatory wine proof: a non-default bottle survives repeated movement with no state loss.
 *
 * <h2>Why the origin stack exists</h2>
 *
 * <p>Before this milestone, a placed bottle was rebuilt from its six {@link WineData} fields on the
 * way out. Anything else the stack carried — a custom name, a component added by a later feature —
 * was silently discarded on every place-then-pickup cycle. These tests hold the fixed behaviour in
 * place: the bottle is <em>restored</em>, not re-manufactured.
 *
 * <p>The wine fixture is the one already used by {@code BankItemCodecGameTests}, so the values here
 * are the project's own idea of a realistic bottle rather than invented ones.
 */
class GrabbyWineRoundTripTest {
    /** The six real fields, from the fixture the bank tests already use. */
    private static final WineData VINTAGE = new WineData(
            "Britannia Vintners", "Merlot", 1487, 82, "Trinsic", "green");

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        com.seggellion.britannia_mod.grabbyhands.testsupport.GrabbyRegisteredTestContent
                .ensureWineDataRegistered();
        registries = new RegistryAccess.ImmutableRegistryAccess(BuiltInRegistries.REGISTRY.stream().toList());
    }

    /**
     * Stands in for the registered wine bottle item.
     *
     * <p>The mod's own items are not registered under a bare bootstrap, and what is under test is the
     * preservation logic rather than which item id it happens to use.
     */
    private static Item bottleItem() {
        return Items.GLASS_BOTTLE;
    }

    private static ItemStack vintageBottle() {
        ItemStack stack = new ItemStack(bottleItem());
        com.seggellion.britannia_mod.item.WineBottleBlockItem.setWineData(
                stack, VINTAGE.wineryName(), VINTAGE.grapeType(), VINTAGE.year(),
                VINTAGE.quality(), VINTAGE.region(), VINTAGE.labelColor());
        return stack;
    }

    private static WineData wineDataOf(ItemStack stack) {
        return com.seggellion.britannia_mod.item.WineBottleBlockItem.getWineData(stack);
    }

    /** One place-then-pickup cycle, exercising the real preservation decision. */
    private static ItemStack cycle(ItemStack held) {
        // Placement records the exact source stack, as WineBottleBlock.setPlacedBy does.
        ItemStack recorded = held.copyWithCount(1);
        WineData onBlock = wineDataOf(held);
        // Pickup asks the same static helper the block's clone-stack path calls.
        return WineBottleBlockEntity.portableStack(recorded, onBlock, bottleItem());
    }

    // ------------------------------------------------------------------
    // Every meaningful field, not just the id
    // ------------------------------------------------------------------

    @Test
    void allSixWineFieldsSurviveASingleRoundTrip() {
        ItemStack before = vintageBottle();

        WineData after = wineDataOf(cycle(before));

        assertEquals(VINTAGE.wineryName(), after.wineryName());
        assertEquals(VINTAGE.grapeType(), after.grapeType());
        assertEquals(VINTAGE.year(), after.year());
        assertEquals(VINTAGE.quality(), after.quality());
        assertEquals(VINTAGE.region(), after.region());
        assertEquals(VINTAGE.labelColor(), after.labelColor());
        assertEquals(VINTAGE, after, "the whole record must match, not merely field by field");
    }

    @Test
    void aNonDefaultBottleIsActuallyNonDefault() {
        // Guards the test itself: comparing two empty records would prove nothing.
        assertNotEquals(WineData.EMPTY, wineDataOf(vintageBottle()));
    }

    @Test
    void repeatedCyclesDoNotDrift() {
        ItemStack carried = vintageBottle();
        for (int round = 0; round < 4; round++) {
            carried = cycle(carried);
        }
        assertEquals(VINTAGE, wineDataOf(carried));
        assertTrue(ItemStack.matches(vintageBottle(), carried),
                "after several cycles the stack must still match the original exactly");
    }

    // ------------------------------------------------------------------
    // The defect this milestone fixed
    // ------------------------------------------------------------------

    @Test
    void aCustomNameNoLongerDisappearsAcrossACycle() {
        // This is the M0 finding: rebuilding from WineData alone dropped everything else.
        ItemStack named = vintageBottle();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Lord British's Reserve"));

        ItemStack after = cycle(named);

        assertEquals("Lord British's Reserve", after.get(DataComponents.CUSTOM_NAME).getString());
        assertEquals(VINTAGE, wineDataOf(after), "and the wine fields are still intact alongside it");
    }

    @Test
    void componentsNobodyHasThoughtOfSurviveToo() {
        // The point of carrying the whole stack: a property added later needs no code change here.
        ItemStack decorated = vintageBottle();
        decorated.set(DataComponents.REPAIR_COST, 7);
        decorated.set(DataComponents.CUSTOM_NAME, Component.literal("Sealed"));

        ItemStack after = cycle(decorated);

        assertEquals(7, after.get(DataComponents.REPAIR_COST));
        assertTrue(ItemStack.matches(decorated, after));
    }

    @Test
    void theOldRebuildPathWouldHaveLostTheCustomName() {
        // Demonstrates that the fix is load-bearing rather than incidental: with no origin stack
        // recorded, only the six wine fields come back.
        ItemStack named = vintageBottle();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Lord British's Reserve"));

        ItemStack rebuilt = WineBottleBlockEntity.portableStack(
                ItemStack.EMPTY, wineDataOf(named), bottleItem());

        assertEquals(VINTAGE, wineDataOf(rebuilt), "the wine fields still survive the rebuild path");
        assertFalse(rebuilt.has(DataComponents.CUSTOM_NAME),
                "which is exactly the loss the origin stack now prevents");
    }

    // ------------------------------------------------------------------
    // Bottles that arrived some other way
    // ------------------------------------------------------------------

    @Test
    void aWorldgenBottleWithNoOriginStackStillYieldsACorrectItem() {
        // Britannia scenery has no recorded source stack. Rebuilding from the wine fields is the right
        // answer there, and must keep working.
        ItemStack rebuilt = WineBottleBlockEntity.portableStack(ItemStack.EMPTY, VINTAGE, bottleItem());

        assertEquals(bottleItem(), rebuilt.getItem());
        assertEquals(VINTAGE, wineDataOf(rebuilt));
    }

    @Test
    void anEmptyWineRecordRebuildsWithoutThrowing() {
        ItemStack rebuilt = WineBottleBlockEntity.portableStack(ItemStack.EMPTY, WineData.EMPTY, bottleItem());
        assertEquals(WineData.EMPTY, wineDataOf(rebuilt));
    }

    @Test
    void onlyOneBottleComesBackEvenIfTheSourceStackWasLarger() {
        // Wine stacks to 16. Placing one bottle must hand back one bottle.
        ItemStack sixteen = vintageBottle();
        sixteen.setCount(16);

        ItemStack after = cycle(sixteen);

        assertEquals(1, after.getCount());
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Test
    void theOriginStackSurvivesNbtSaveAndReload() {
        // Mirrors what WineBottleBlockEntity writes and reads, i.e. a chunk unload and reload.
        ItemStack original = vintageBottle();
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Cellared"));

        CompoundTag tag = new CompoundTag();
        tag.put("OriginStack", original.save(registries));
        ItemStack restored = ItemStack.parse(registries, tag.getCompound("OriginStack"))
                .orElse(ItemStack.EMPTY);

        assertTrue(ItemStack.matches(original, restored));
        assertEquals(VINTAGE, wineDataOf(restored));
    }

    @Test
    void theWineFieldsThemselvesSurviveTheBlockEntityTagFormat() {
        CompoundTag tag = new CompoundTag();
        tag.putString("WineryName", VINTAGE.wineryName());
        tag.putString("GrapeType", VINTAGE.grapeType());
        tag.putInt("Year", VINTAGE.year());
        tag.putInt("Quality", VINTAGE.quality());
        tag.putString("Region", VINTAGE.region());
        tag.putString("LabelColor", VINTAGE.labelColor());

        WineData reloaded = new WineData(
                tag.getString("WineryName"),
                tag.getString("GrapeType"),
                tag.getInt("Year"),
                tag.getInt("Quality"),
                tag.getString("Region"),
                tag.contains("LabelColor") ? tag.getString("LabelColor") : "red");

        assertEquals(VINTAGE, reloaded);
    }

    @Test
    void wineDataStillRoundTripsThroughItsOwnCodec() {
        // The component codec is what the economy and the bank envelope rely on.
        var encoded = WineData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, VINTAGE)
                .getOrThrow();
        WineData decoded = WineData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(VINTAGE, decoded);
    }
}
