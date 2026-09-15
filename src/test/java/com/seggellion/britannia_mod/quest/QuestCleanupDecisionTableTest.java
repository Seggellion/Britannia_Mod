package com.seggellion.britannia_mod.quest;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M1 (discovery D1): the cleanup decision table, run over plain slot lists
 * exactly as {@link QuestCleanupService} runs it over a player's inventory, armour and off hand.
 *
 * <pre>
 *   stamp state              quest active   login pass            quit pass (that quest)
 *   -----------------------  ------------   -------------------   ----------------------
 *   temporary (trigger key)  no             DELETE (if owner)     DELETE (if owner)
 *   temporary (trigger key)  yes            keep, still stamped   keep, still stamped (other quest)
 *   legacy (no trigger key)  no             STRIP, keep item      STRIP, keep item
 *   legacy (no trigger key)  yes            keep, still stamped   keep, still stamped (other quest)
 *   unstamped                --             untouched             untouched
 * </pre>
 */
class QuestCleanupDecisionTableTest {
    private static final String OWNER = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final String SOMEONE_ELSE = "1d2f3a4b-5c6d-4e7f-8a9b-0c1d2e3f4a5b";

    private static final ClientQuestEntry KIT_QUEST = new ClientQuestEntry(
        "9101", "4101", "rowan_farming_1", "Rowan", "From Soil to Supper (1 of 5)", "", "", "accepted");
    private static final ClientQuestEntry RING_QUEST = new ClientQuestEntry(
        "9102", "4102", "cast_into_the_fire", "Mitexi", "Cast it into the fire", "", "", "accepted");
    private static final ClientQuestEntry ESCORT_QUEST = new ClientQuestEntry(
        "9103", "4103", "escort_britain_to_vesper", "Mitexi", "Escort to Vesper", "", "", "accepted");

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // --- login / bootstrap pass ------------------------------------------------------------------

    @Test
    void aLegacyStampWhoseQuestEndedIsStrippedAndTheItemKept() {
        ItemStack shovel = legacy(new ItemStack(Items.IRON_SHOVEL), "britannia_shovel", KIT_QUEST, OWNER);
        ItemStack coins = legacy(new ItemStack(Items.GOLD_NUGGET, 12), "silver_coin", KIT_QUEST, OWNER);
        NonNullList<ItemStack> slots = slots(shovel, coins);

        QuestCleanupService.Sweep sweep = QuestCleanupService.sweepForStaleQuests(List.of(slots), OWNER, List.of());

        assertEquals(new QuestCleanupService.Sweep(0, 13), sweep);
        assertSame(shovel, slots.get(0));
        assertSame(coins, slots.get(1));
        assertEquals(12, coins.getCount());
        assertFalse(QuestItemStamp.isStamped(shovel));
        assertFalse(QuestItemStamp.isStamped(coins));
        assertNull(coins.get(DataComponents.CUSTOM_DATA), "the stripped coins merge with plain coins again");
    }

    @Test
    void aLegacyStampWhoseQuestIsStillActiveIsLeftAlone() {
        ItemStack coins = legacy(new ItemStack(Items.GOLD_NUGGET, 12), "silver_coin", KIT_QUEST, OWNER);
        NonNullList<ItemStack> slots = slots(coins);

        QuestCleanupService.Sweep sweep = QuestCleanupService.sweepForStaleQuests(List.of(slots), OWNER, List.of(KIT_QUEST));

        assertEquals(new QuestCleanupService.Sweep(0, 0), sweep);
        assertTrue(QuestItemStamp.isLegacyPermanent(QuestItemStamp.read(slots.get(0))));
        assertEquals(12, slots.get(0).getCount());
    }

    @Test
    void aLegacyStampNamingNoQuestIsStrippedOnTheNextLogin() {
        ItemStack bucket = new ItemStack(Items.BUCKET);
        CompoundTag tag = new CompoundTag();
        tag.putString(QuestItemStamp.ITEM, "minecraft:bucket");
        tag.putString(QuestItemStamp.OWNER_UUID, OWNER);
        tag.putString(QuestItemStamp.OWNER_NAME, "Seggellion");
        bucket.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        NonNullList<ItemStack> slots = slots(bucket);

        QuestCleanupService.Sweep sweep = QuestCleanupService.sweepForStaleQuests(List.of(slots), OWNER, List.of(KIT_QUEST));

        assertEquals(new QuestCleanupService.Sweep(0, 1), sweep);
        assertSame(bucket, slots.get(0));
        assertFalse(QuestItemStamp.isStamped(bucket));
    }

    @Test
    void aLegacyStampIsNeverDeletedWhoeverItNames() {
        ItemStack coins = legacy(new ItemStack(Items.GOLD_NUGGET, 12), "silver_coin", KIT_QUEST, SOMEONE_ELSE);
        NonNullList<ItemStack> slots = slots(coins);

        QuestCleanupService.Sweep sweep = QuestCleanupService.sweepForStaleQuests(List.of(slots), OWNER, List.of());

        assertEquals(new QuestCleanupService.Sweep(0, 12), sweep);
        assertSame(coins, slots.get(0));
        assertFalse(QuestItemStamp.isStamped(coins));
    }

    @Test
    void aTemporaryStampWhoseQuestEndedIsDeleted() {
        ItemStack ring = temporary(new ItemStack(Items.GOLD_INGOT), RING_QUEST, OWNER);
        NonNullList<ItemStack> slots = slots(ring);

        QuestCleanupService.Sweep sweep = QuestCleanupService.sweepForStaleQuests(List.of(slots), OWNER, List.of(KIT_QUEST));

        assertEquals(new QuestCleanupService.Sweep(1, 0), sweep);
        assertTrue(slots.get(0).isEmpty());
    }

    @Test
    void aTemporaryStampWhoseQuestIsStillActiveIsKeptAndStillStamped() {
        ItemStack ring = temporary(new ItemStack(Items.GOLD_INGOT), RING_QUEST, OWNER);
        NonNullList<ItemStack> slots = slots(ring);

        QuestCleanupService.Sweep sweep = QuestCleanupService.sweepForStaleQuests(List.of(slots), OWNER, List.of(RING_QUEST, KIT_QUEST));

        assertEquals(new QuestCleanupService.Sweep(0, 0), sweep);
        assertSame(ring, slots.get(0));
        assertTrue(QuestItemStamp.isTemporary(ring));
        assertEquals("ring_destroyed", QuestItemStamp.read(ring).getString(QuestItemStamp.TRIGGER_KEY));
    }

    @Test
    void aTemporaryStampOwnedBySomeoneElseIsNotThisPlayersToDelete() {
        ItemStack ring = temporary(new ItemStack(Items.GOLD_INGOT), RING_QUEST, SOMEONE_ELSE);
        NonNullList<ItemStack> slots = slots(ring);

        QuestCleanupService.Sweep sweep = QuestCleanupService.sweepForStaleQuests(List.of(slots), OWNER, List.of());

        assertEquals(new QuestCleanupService.Sweep(0, 0), sweep);
        assertSame(ring, slots.get(0));
        assertTrue(QuestItemStamp.isTemporary(ring));
    }

    @Test
    void aTemporaryStampIsMatchedByQuestIdOrQuestKeyWhenItHasNoStateId() {
        ItemStack byId = temporary(new ItemStack(Items.GOLD_INGOT), RING_QUEST, OWNER);
        CompoundTag idOnly = QuestItemStamp.read(byId);
        idOnly.remove(QuestItemStamp.QUEST_STATE_ID);
        byId.set(DataComponents.CUSTOM_DATA, CustomData.of(idOnly));

        ItemStack byKey = temporary(new ItemStack(Items.GOLD_INGOT), RING_QUEST, OWNER);
        CompoundTag keyOnly = QuestItemStamp.read(byKey);
        keyOnly.remove(QuestItemStamp.QUEST_STATE_ID);
        keyOnly.remove(QuestItemStamp.QUEST_ID);
        keyOnly.putString(QuestItemStamp.QUEST_KEY, RING_QUEST.questKey());
        byKey.set(DataComponents.CUSTOM_DATA, CustomData.of(keyOnly));

        NonNullList<ItemStack> active = slots(byId.copy(), byKey.copy());
        assertEquals(new QuestCleanupService.Sweep(0, 0),
            QuestCleanupService.sweepForStaleQuests(List.of(active), OWNER, List.of(RING_QUEST)));

        NonNullList<ItemStack> ended = slots(byId, byKey);
        assertEquals(new QuestCleanupService.Sweep(2, 0),
            QuestCleanupService.sweepForStaleQuests(List.of(ended), OWNER, List.of(KIT_QUEST)));
    }

    @Test
    void unstampedStacksAreNeverTouched() {
        ItemStack plainCoins = new ItemStack(Items.GOLD_NUGGET, 4);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        CompoundTag other = new CompoundTag();
        other.putString("Filler", "keep");
        diamond.set(DataComponents.CUSTOM_DATA, CustomData.of(other));
        NonNullList<ItemStack> slots = slots(plainCoins, diamond, ItemStack.EMPTY);

        assertEquals(new QuestCleanupService.Sweep(0, 0),
            QuestCleanupService.sweepForStaleQuests(List.of(slots), OWNER, List.of()));
        assertEquals(new QuestCleanupService.Sweep(0, 0),
            QuestCleanupService.sweepForQuitQuest(List.of(slots), OWNER, KIT_QUEST));

        assertSame(plainCoins, slots.get(0));
        assertEquals(4, plainCoins.getCount());
        assertSame(diamond, slots.get(1));
        assertEquals("keep", QuestItemStamp.read(diamond).getString("Filler"));
        assertTrue(slots.get(2).isEmpty());
    }

    @Test
    void escortSilverGrantedBeforeM1SurvivesTheRelogAfterTheEscortEnded() {
        ItemStack silver = legacy(new ItemStack(Items.GOLD_NUGGET, 12), "silver_coin", ESCORT_QUEST, OWNER);
        NonNullList<ItemStack> hotbar = slots(silver);

        // Still escorting: nothing happens.
        assertEquals(new QuestCleanupService.Sweep(0, 0),
            QuestCleanupService.sweepForStaleQuests(List.of(hotbar), OWNER, List.of(ESCORT_QUEST)));
        assertEquals(12, hotbar.get(0).getCount());

        // Escort delivered, quest gone from the journal, player logs in again.
        assertEquals(new QuestCleanupService.Sweep(0, 12),
            QuestCleanupService.sweepForStaleQuests(List.of(hotbar), OWNER, List.of()));
        assertSame(silver, hotbar.get(0));
        assertEquals(12, silver.getCount());
        assertFalse(QuestItemStamp.isStamped(silver));
    }

    @Test
    void thePassCoversEverySlotListItIsGiven() {
        NonNullList<ItemStack> main = slots(legacy(new ItemStack(Items.GOLD_NUGGET, 3), "silver_coin", KIT_QUEST, OWNER));
        NonNullList<ItemStack> armor = slots(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
            legacy(new ItemStack(Items.LEATHER_HELMET), "leather_helmet", KIT_QUEST, OWNER));
        NonNullList<ItemStack> offhand = slots(temporary(new ItemStack(Items.GOLD_INGOT), RING_QUEST, OWNER));

        QuestCleanupService.Sweep sweep =
            QuestCleanupService.sweepForStaleQuests(List.of(main, armor, offhand), OWNER, List.of());

        assertEquals(new QuestCleanupService.Sweep(1, 4), sweep);
        assertFalse(QuestItemStamp.isStamped(main.get(0)));
        assertEquals(3, main.get(0).getCount());
        assertFalse(QuestItemStamp.isStamped(armor.get(3)));
        assertEquals(Items.LEATHER_HELMET, armor.get(3).getItem());
        assertTrue(offhand.get(0).isEmpty());
    }

    // --- quit pass -------------------------------------------------------------------------------

    @Test
    void quittingAQuestDeletesItsTemporaryItemsAndStripsItsLegacyOnes() {
        ItemStack ring = temporary(new ItemStack(Items.GOLD_INGOT), RING_QUEST, OWNER);
        ItemStack legacyOfRingQuest = legacy(new ItemStack(Items.GOLD_NUGGET, 5), "gold_coin", RING_QUEST, OWNER);
        ItemStack kitShovel = legacy(new ItemStack(Items.IRON_SHOVEL), "britannia_shovel", KIT_QUEST, OWNER);
        ItemStack otherTemporary = temporary(new ItemStack(Items.GOLD_INGOT), KIT_QUEST, OWNER);
        NonNullList<ItemStack> slots = slots(ring, legacyOfRingQuest, kitShovel, otherTemporary);

        QuestCleanupService.Sweep sweep = QuestCleanupService.sweepForQuitQuest(List.of(slots), OWNER, RING_QUEST);

        assertEquals(new QuestCleanupService.Sweep(1, 5), sweep);
        assertTrue(slots.get(0).isEmpty(), "the quit quest's temporary ring is deleted");
        assertSame(legacyOfRingQuest, slots.get(1));
        assertFalse(QuestItemStamp.isStamped(legacyOfRingQuest), "the quit quest's legacy stamp is stripped");
        assertEquals(5, legacyOfRingQuest.getCount());
        assertTrue(QuestItemStamp.isLegacyPermanent(QuestItemStamp.read(kitShovel)), "another quest's legacy stamp is untouched");
        assertTrue(QuestItemStamp.isTemporary(otherTemporary), "another quest's temporary item is untouched");
    }

    @Test
    void quittingAQuestDoesNotDeleteATemporaryItemOwnedBySomeoneElse() {
        ItemStack ring = temporary(new ItemStack(Items.GOLD_INGOT), RING_QUEST, SOMEONE_ELSE);
        NonNullList<ItemStack> slots = slots(ring);

        assertEquals(new QuestCleanupService.Sweep(0, 0),
            QuestCleanupService.sweepForQuitQuest(List.of(slots), OWNER, RING_QUEST));
        assertSame(ring, slots.get(0));
        assertTrue(QuestItemStamp.isTemporary(ring));
    }

    @Test
    void quittingMatchesByStateIdQuestIdOrQuestKey() {
        ItemStack byState = temporary(new ItemStack(Items.GOLD_INGOT), RING_QUEST, OWNER);
        ItemStack byId = temporary(new ItemStack(Items.GOLD_INGOT), RING_QUEST, OWNER);
        CompoundTag idOnly = QuestItemStamp.read(byId);
        idOnly.remove(QuestItemStamp.QUEST_STATE_ID);
        byId.set(DataComponents.CUSTOM_DATA, CustomData.of(idOnly));
        ItemStack byKey = temporary(new ItemStack(Items.GOLD_INGOT), RING_QUEST, OWNER);
        CompoundTag keyOnly = QuestItemStamp.read(byKey);
        keyOnly.remove(QuestItemStamp.QUEST_STATE_ID);
        keyOnly.remove(QuestItemStamp.QUEST_ID);
        keyOnly.putString(QuestItemStamp.QUEST_KEY, RING_QUEST.questKey());
        byKey.set(DataComponents.CUSTOM_DATA, CustomData.of(keyOnly));
        NonNullList<ItemStack> slots = slots(byState, byId, byKey);

        assertEquals(new QuestCleanupService.Sweep(3, 0),
            QuestCleanupService.sweepForQuitQuest(List.of(slots), OWNER, RING_QUEST));
    }

    // --- fixtures --------------------------------------------------------------------------------

    private static NonNullList<ItemStack> slots(ItemStack... stacks) {
        NonNullList<ItemStack> list = NonNullList.withSize(stacks.length, ItemStack.EMPTY);
        for (int i = 0; i < stacks.length; i++) list.set(i, stacks[i]);
        return list;
    }

    /** The pre-M1 blanket stamp, exactly as {@code QuestRewardService#stamp} wrote it. */
    private static ItemStack legacy(ItemStack stack, String itemId, ClientQuestEntry quest, String owner) {
        CompoundTag tag = new CompoundTag();
        tag.putString(QuestItemStamp.ITEM, itemId);
        tag.putString(QuestItemStamp.OWNER_UUID, owner);
        tag.putString(QuestItemStamp.OWNER_NAME, "Seggellion");
        tag.putLong(QuestItemStamp.QUEST_ID, Long.parseLong(quest.questId()));
        tag.putString(QuestItemStamp.QUEST_STATE_ID, quest.questStateId());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** The M1 temporary stamp: the legacy keys minus the player name, plus the objective's key. */
    private static ItemStack temporary(ItemStack stack, ClientQuestEntry quest, String owner) {
        CompoundTag tag = new CompoundTag();
        tag.putString(QuestItemStamp.ITEM, "magic_ring");
        tag.putString(QuestItemStamp.OWNER_UUID, owner);
        tag.putLong(QuestItemStamp.QUEST_ID, Long.parseLong(quest.questId()));
        tag.putString(QuestItemStamp.QUEST_STATE_ID, quest.questStateId());
        tag.putString(QuestItemStamp.TRIGGER_KEY, "ring_destroyed");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }
}
