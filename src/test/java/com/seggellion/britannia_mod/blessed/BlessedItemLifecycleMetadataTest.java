package com.seggellion.britannia_mod.blessed;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every branch of the single canonical blessed-item reader, against real {@link ItemStack}s
 * carrying real {@link DataComponents#CUSTOM_DATA} rather than a mocked tag accessor -- the whole
 * value of this class is that it behaves correctly on the exact component shape the delivery path
 * actually writes, and a fake tag source would prove nothing about that.
 *
 * <p>The asymmetry these tests pin hardest is {@code isBlessed} vs {@code of} on a legacy pre-M6
 * item: true and empty respectively. Collapsing those two into one answer in either direction is
 * a real bug -- one way it strips protection from items issued before M6, the other way it
 * fabricates destruction reports for materializations that have no identity to report.
 */
class BlessedItemLifecycleMetadataTest {

    private static final String DEED_ID = "deed-uuid-0001";

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---------- the happy path ----------

    @Test
    void aFullyStampedBlessedItemParsesEveryField() {
        UUID instance = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        ItemStack stack = stamped(instance.toString(), DEED_ID, owner.toString());

        assertTrue(BlessedItemLifecycleMetadata.isBlessed(stack));
        BlessedItemLifecycleMetadata metadata = BlessedItemLifecycleMetadata.of(stack).orElseThrow();
        assertEquals(instance, metadata.instanceUuid());
        assertEquals(DEED_ID, metadata.deedId());
        assertEquals(owner, metadata.ownerUuid());
    }

    // A non-uuid-shaped legacy entitlement id must be accepted verbatim: deed_id is Rails'
    // BlessedItem.uuid, whose historical values were never promised to be uuid-shaped, and this
    // reader has no business rejecting a real entitlement over a format it was never given.
    @Test
    void aNonUuidShapedDeedIdIsAcceptedVerbatim() {
        UUID instance = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        ItemStack stack = stamped(instance.toString(), "legacy-deed-00017", owner.toString());

        assertEquals("legacy-deed-00017",
            BlessedItemLifecycleMetadata.of(stack).orElseThrow().deedId());
    }

    // Forward compatibility: a field this build does not know about must not defeat the parse.
    @Test
    void anUnrecognisedExtraFieldAlongsideAValidStampIsIgnored() {
        UUID instance = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        CompoundTag tag = tagOf(instance.toString(), DEED_ID, owner.toString());
        tag.putString("some_future_field", "hello");
        ItemStack stack = withCustomData(tag);

        assertTrue(BlessedItemLifecycleMetadata.isBlessed(stack));
        assertEquals(instance, BlessedItemLifecycleMetadata.of(stack).orElseThrow().instanceUuid());
    }

    // ---------- the legacy population: blessed, but not lifecycle-aware ----------

    @Test
    void aLegacyBlessedItemWithNoInstanceUuidIsBlessedButHasNoLifecycleIdentity() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putString("owner", UUID.randomUUID().toString());
        tag.putString("deed_id", DEED_ID);
        // No instance_uuid at all -- the exact pre-M6 shape.
        ItemStack legacy = withCustomData(tag);

        assertTrue(BlessedItemLifecycleMetadata.isBlessed(legacy),
            "rescue and protection must still cover a blessed item issued before M6");
        assertTrue(BlessedItemLifecycleMetadata.of(legacy).isEmpty(),
            "a legacy item has no materialization identity and must never enter the M7 protocol");
    }

    @Test
    void aBlankInstanceUuidIsTreatedAsLegacyRatherThanAsCorruption() {
        ItemStack blank = stamped("   ", DEED_ID, UUID.randomUUID().toString());

        assertTrue(BlessedItemLifecycleMetadata.isBlessed(blank));
        assertTrue(BlessedItemLifecycleMetadata.of(blank).isEmpty());
    }

    @Test
    void anEmptyStringInstanceUuidIsTreatedAsLegacy() {
        ItemStack empty = stamped("", DEED_ID, UUID.randomUUID().toString());

        assertTrue(BlessedItemLifecycleMetadata.isBlessed(empty));
        assertTrue(BlessedItemLifecycleMetadata.of(empty).isEmpty());
    }

    // ---------- malformed lifecycle stamps ----------

    @Test
    void aMalformedInstanceUuidIsRefusedButTheItemIsStillBlessed() {
        ItemStack stack = stamped("not-a-uuid", DEED_ID, UUID.randomUUID().toString());

        assertTrue(BlessedItemLifecycleMetadata.isBlessed(stack),
            "a corrupt lifecycle field must not strip protection from a genuinely blessed item");
        assertTrue(BlessedItemLifecycleMetadata.of(stack).isEmpty());
    }

    @Test
    void aMalformedOwnerUuidIsRefused() {
        ItemStack stack = stamped(UUID.randomUUID().toString(), DEED_ID, "not-a-uuid");

        assertTrue(BlessedItemLifecycleMetadata.isBlessed(stack));
        assertTrue(BlessedItemLifecycleMetadata.of(stack).isEmpty());
    }

    @Test
    void bothUuidsMalformedAtOnceIsStillJustARefusalAndNeverThrows() {
        ItemStack stack = stamped("nope", DEED_ID, "also-nope");

        assertTrue(BlessedItemLifecycleMetadata.of(stack).isEmpty());
    }

    @Test
    void aBlankOrMissingDeedIdIsRefused() {
        UUID instance = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        assertTrue(BlessedItemLifecycleMetadata.of(stamped(instance.toString(), "  ", owner.toString())).isEmpty());
        assertTrue(BlessedItemLifecycleMetadata.of(stamped(instance.toString(), "", owner.toString())).isEmpty());

        CompoundTag noDeed = new CompoundTag();
        noDeed.putBoolean("blessed", true);
        noDeed.putString("owner", owner.toString());
        noDeed.putString("instance_uuid", instance.toString());
        ItemStack stack = withCustomData(noDeed);
        assertTrue(BlessedItemLifecycleMetadata.isBlessed(stack));
        assertTrue(BlessedItemLifecycleMetadata.of(stack).isEmpty());
    }

    @Test
    void aMissingOwnerIsRefused() {
        CompoundTag noOwner = new CompoundTag();
        noOwner.putBoolean("blessed", true);
        noOwner.putString("deed_id", DEED_ID);
        noOwner.putString("instance_uuid", UUID.randomUUID().toString());
        ItemStack stack = withCustomData(noOwner);

        assertTrue(BlessedItemLifecycleMetadata.isBlessed(stack));
        assertTrue(BlessedItemLifecycleMetadata.of(stack).isEmpty());
    }

    // ---------- not blessed at all ----------

    @Test
    void anOrdinaryItemIsNeverBlessed() {
        ItemStack diamond = new ItemStack(Items.DIAMOND);

        assertFalse(BlessedItemLifecycleMetadata.isBlessed(diamond));
        assertTrue(BlessedItemLifecycleMetadata.of(diamond).isEmpty());
    }

    @Test
    void anEmptyStackIsNeverBlessed() {
        assertFalse(BlessedItemLifecycleMetadata.isBlessed(ItemStack.EMPTY));
        assertTrue(BlessedItemLifecycleMetadata.of(ItemStack.EMPTY).isEmpty());
    }

    @Test
    void aNullStackIsNeverBlessedAndNeverThrows() {
        assertFalse(BlessedItemLifecycleMetadata.isBlessed(null));
        assertTrue(BlessedItemLifecycleMetadata.of(null).isEmpty());
    }

    @Test
    void customDataPresentButEmptyIsNotBlessed() {
        ItemStack stack = withCustomData(new CompoundTag());

        assertFalse(BlessedItemLifecycleMetadata.isBlessed(stack));
        assertTrue(BlessedItemLifecycleMetadata.of(stack).isEmpty());
    }

    // The severe version: everything a lifecycle-aware item has EXCEPT the blessed flag. An
    // unrelated item that merely happens to carry these field names must not be adopted.
    @Test
    void customDataCarryingLifecycleFieldsWithoutTheBlessedFlagIsNotBlessed() {
        CompoundTag tag = new CompoundTag();
        tag.putString("owner", UUID.randomUUID().toString());
        tag.putString("deed_id", DEED_ID);
        tag.putString("instance_uuid", UUID.randomUUID().toString());
        ItemStack stack = withCustomData(tag);

        assertFalse(BlessedItemLifecycleMetadata.isBlessed(stack));
        assertTrue(BlessedItemLifecycleMetadata.of(stack).isEmpty());
    }

    @Test
    void anExplicitBlessedFalseIsNotBlessed() {
        CompoundTag tag = tagOf(UUID.randomUUID().toString(), DEED_ID, UUID.randomUUID().toString());
        tag.putBoolean("blessed", false);
        ItemStack stack = withCustomData(tag);

        assertFalse(BlessedItemLifecycleMetadata.isBlessed(stack));
        assertTrue(BlessedItemLifecycleMetadata.of(stack).isEmpty());
    }

    // ---------- corrupt / unexpected NBT never throws ----------

    @Test
    void aBlessedFlagOfTheWrongTagTypeFailsClosedInsteadOfThrowing() {
        CompoundTag tag = new CompoundTag();
        tag.putString("blessed", "true");
        tag.putString("owner", UUID.randomUUID().toString());
        tag.putString("deed_id", DEED_ID);
        tag.putString("instance_uuid", UUID.randomUUID().toString());
        ItemStack stack = withCustomData(tag);

        assertFalse(BlessedItemLifecycleMetadata.isBlessed(stack),
            "a blessed flag that is not a number must read as not-blessed, never as true");
        assertTrue(BlessedItemLifecycleMetadata.of(stack).isEmpty());
    }

    @Test
    void lifecycleFieldsOfTheWrongTagTypeFailClosedInsteadOfThrowing() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putInt("owner", 7);
        ListTag deedList = new ListTag();
        deedList.add(StringTag.valueOf("deed"));
        tag.put("deed_id", deedList);
        tag.put("instance_uuid", new CompoundTag());
        ItemStack stack = withCustomData(tag);

        assertTrue(BlessedItemLifecycleMetadata.isBlessed(stack));
        assertTrue(BlessedItemLifecycleMetadata.of(stack).isEmpty(),
            "wrong-typed lifecycle fields must resolve to empty, not to a thrown exception");
    }

    // ---------- the record's own invariants ----------

    @Test
    void theRecordRefusesToBeConstructedWithoutACompleteIdentity() {
        UUID instance = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        assertThrows(NullPointerException.class,
            () -> new BlessedItemLifecycleMetadata(null, DEED_ID, owner));
        assertThrows(NullPointerException.class,
            () -> new BlessedItemLifecycleMetadata(instance, null, owner));
        assertThrows(NullPointerException.class,
            () -> new BlessedItemLifecycleMetadata(instance, DEED_ID, null));
        assertThrows(IllegalArgumentException.class,
            () -> new BlessedItemLifecycleMetadata(instance, "   ", owner));
    }

    @Test
    void twoReadsOfTheSameStackAreEqual() {
        ItemStack stack = stamped(UUID.randomUUID().toString(), DEED_ID, UUID.randomUUID().toString());

        Optional<BlessedItemLifecycleMetadata> first = BlessedItemLifecycleMetadata.of(stack);
        Optional<BlessedItemLifecycleMetadata> second = BlessedItemLifecycleMetadata.of(stack);
        assertEquals(first, second);
    }

    // ---------- helpers ----------

    private static ItemStack stamped(String instanceUuid, String deedId, String ownerUuid) {
        return withCustomData(tagOf(instanceUuid, deedId, ownerUuid));
    }

    /** The exact four-field shape {@code BlessedItemInventorySync#stamp} writes. */
    private static CompoundTag tagOf(String instanceUuid, String deedId, String ownerUuid) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putString("owner", ownerUuid);
        tag.putString("deed_id", deedId);
        tag.putString("instance_uuid", instanceUuid);
        return tag;
    }

    private static ItemStack withCustomData(CompoundTag tag) {
        ItemStack stack = new ItemStack(Items.DIAMOND);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }
}
