package com.seggellion.britannia_mod.quest;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M1: the two shapes of the quest stamp -- TEMPORARY (names its objective's
 * trigger key) and LEGACY (the pre-M1 blanket stamp, which was a permanent reward all along) --
 * and the strip that turns a legacy stack back into a plain one.
 */
class QuestItemStampTest {
    private static final String OWNER = "069a79f4-44e9-4726-a5be-fca90e38aaf5";

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void anUnstampedStackIsNeitherShape() {
        ItemStack plain = new ItemStack(Items.IRON_SHOVEL);
        assertFalse(QuestItemStamp.isStamped(plain));
        assertFalse(QuestItemStamp.isTemporary(plain));
        assertFalse(QuestItemStamp.isLegacyPermanent(QuestItemStamp.read(plain)));
        assertFalse(QuestItemStamp.isStamped(ItemStack.EMPTY));
        assertFalse(QuestItemStamp.isStamped((ItemStack) null));
        assertFalse(QuestItemStamp.isStamped((CompoundTag) null));
        assertTrue(QuestItemStamp.read(ItemStack.EMPTY).isEmpty());
    }

    @Test
    void unrelatedCustomDataIsNotAStamp() {
        ItemStack stack = new ItemStack(Items.DIAMOND);
        CompoundTag tag = new CompoundTag();
        tag.putString("Filler", "keep");
        tag.putString("quest_items", "a near miss, not the key");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        assertFalse(QuestItemStamp.isStamped(stack));
        assertFalse(QuestItemStamp.strip(stack), "nothing to strip");
        assertEquals("keep", QuestItemStamp.read(stack).getString("Filler"));
    }

    @Test
    void aStampWithATriggerKeyIsTemporary() {
        ItemStack ring = new ItemStack(Items.GOLD_INGOT);
        CompoundTag tag = temporaryStamp(41L, "9001", "ring_destroyed");
        ring.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        assertTrue(QuestItemStamp.isStamped(ring));
        assertTrue(QuestItemStamp.isTemporary(ring));
        assertFalse(QuestItemStamp.isLegacyPermanent(QuestItemStamp.read(ring)));
    }

    @Test
    void theBlanketStampWithoutATriggerKeyIsLegacyPermanent() {
        ItemStack coins = new ItemStack(Items.GOLD_NUGGET, 12);
        coins.set(DataComponents.CUSTOM_DATA, CustomData.of(legacyStamp("silver_coin", 7L, "9007")));

        assertTrue(QuestItemStamp.isStamped(coins));
        assertFalse(QuestItemStamp.isTemporary(coins));
        assertTrue(QuestItemStamp.isLegacyPermanent(QuestItemStamp.read(coins)));

        CompoundTag blankKey = legacyStamp("silver_coin", 7L, "9007");
        blankKey.putString(QuestItemStamp.TRIGGER_KEY, "   ");
        assertTrue(QuestItemStamp.isLegacyPermanent(blankKey), "a blank trigger key names no objective");
        assertFalse(QuestItemStamp.isTemporary(blankKey));
    }

    @Test
    void anyOneStampKeyCountsAsStamped() {
        for (String key : new String[] {QuestItemStamp.ITEM, QuestItemStamp.QUEST_ID, QuestItemStamp.QUEST_STATE_ID,
                QuestItemStamp.QUEST_KEY, QuestItemStamp.TRIGGER_KEY}) {
            CompoundTag tag = new CompoundTag();
            if (key.equals(QuestItemStamp.QUEST_ID)) tag.putLong(key, 5L); else tag.putString(key, "x");
            assertTrue(QuestItemStamp.isStamped(tag), key);
        }
        CompoundTag ownerOnly = new CompoundTag();
        ownerOnly.putString(QuestItemStamp.OWNER_UUID, OWNER);
        assertFalse(QuestItemStamp.isStamped(ownerOnly), "an owner without any quest identity is not a stamp");
    }

    @Test
    void strippingALegacyStampRemovesEveryStampKeyAndTheEmptyComponent() {
        ItemStack coins = new ItemStack(Items.GOLD_NUGGET, 12);
        coins.set(DataComponents.CUSTOM_DATA, CustomData.of(legacyStamp("silver_coin", 7L, "9007")));

        assertTrue(QuestItemStamp.strip(coins));

        assertFalse(QuestItemStamp.isStamped(coins));
        assertNull(coins.get(DataComponents.CUSTOM_DATA), "an empty custom-data component is dropped");
        assertEquals(12, coins.getCount(), "stripping never touches the count");
        assertTrue(ItemStack.isSameItemSameComponents(coins, new ItemStack(Items.GOLD_NUGGET, 12)),
            "a stripped stack merges with plain stacks again");
        assertFalse(QuestItemStamp.strip(coins), "a second strip finds nothing");
    }

    @Test
    void strippingKeepsUnrelatedCustomData() {
        ItemStack stack = new ItemStack(Items.IRON_SHOVEL);
        CompoundTag tag = legacyStamp("britannia_shovel", 41L, "9001");
        tag.putString("Filler", "keep");
        tag.putInt("RepairCost", 3);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        assertTrue(QuestItemStamp.strip(stack));

        CompoundTag remaining = QuestItemStamp.read(stack);
        assertFalse(QuestItemStamp.isStamped(remaining));
        assertEquals("keep", remaining.getString("Filler"));
        assertEquals(3, remaining.getInt("RepairCost"));
        for (String key : QuestItemStamp.KEYS) {
            assertFalse(remaining.contains(key), key + " must be gone");
        }
    }

    @Test
    void strippingATemporaryStampAlsoRemovesTheDestroyVolume() {
        ItemStack ring = new ItemStack(Items.GOLD_INGOT);
        ring.set(DataComponents.CUSTOM_DATA, CustomData.of(temporaryStamp(41L, "9001", "ring_destroyed")));

        assertTrue(QuestItemStamp.strip(ring));
        assertNull(ring.get(DataComponents.CUSTOM_DATA));
    }

    @Test
    void strippingAnEmptyOrNullStackIsANoOp() {
        assertFalse(QuestItemStamp.strip(ItemStack.EMPTY));
        assertFalse(QuestItemStamp.strip(null));
    }

    /** Exactly what {@code QuestRewardService#stamp} wrote before M1: every key, no trigger key. */
    static CompoundTag legacyStamp(String itemId, long questId, String questStateId) {
        CompoundTag tag = new CompoundTag();
        tag.putString(QuestItemStamp.ITEM, itemId);
        tag.putString(QuestItemStamp.OWNER_UUID, OWNER);
        tag.putString(QuestItemStamp.OWNER_NAME, "Seggellion");
        if (questId > 0) tag.putLong(QuestItemStamp.QUEST_ID, questId);
        if (questStateId != null && !questStateId.isBlank()) tag.putString(QuestItemStamp.QUEST_STATE_ID, questStateId);
        return tag;
    }

    static CompoundTag temporaryStamp(long questId, String questStateId, String triggerKey) {
        CompoundTag tag = new CompoundTag();
        tag.putString(QuestItemStamp.ITEM, "magic_ring");
        tag.putString(QuestItemStamp.OWNER_UUID, OWNER);
        tag.putLong(QuestItemStamp.QUEST_ID, questId);
        tag.putString(QuestItemStamp.QUEST_STATE_ID, questStateId);
        tag.putString(QuestItemStamp.TRIGGER_KEY, triggerKey);
        tag.putInt(QuestItemStamp.MIN_X, 10);
        tag.putInt(QuestItemStamp.MIN_Y, 60);
        tag.putInt(QuestItemStamp.MIN_Z, -20);
        tag.putInt(QuestItemStamp.MAX_X, 20);
        tag.putInt(QuestItemStamp.MAX_Y, 70);
        tag.putInt(QuestItemStamp.MAX_Z, -10);
        return tag;
    }
}
