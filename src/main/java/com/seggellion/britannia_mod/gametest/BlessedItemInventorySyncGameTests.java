package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blessed.delivery.BlessedDeliveryReceipt;
import com.seggellion.britannia_mod.blessed.delivery.BlessedDeliveryReceiptStatus;
import com.seggellion.britannia_mod.blessed.delivery.BlessedDeliveryReceipts;
import com.seggellion.britannia_mod.sync.BlessedItemInventorySync;
import com.seggellion.britannia_mod.sync.BlessedItemSyncAPI;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Starfarer M6. Delivery is driven by Rails materialization state plus a durable local receipt.
 *
 * <p>This replaces the M1 characterization suite, whose purpose was to record what the old
 * scan-based code did so this milestone could show exactly what changed:
 *
 * <ul>
 *   <li>M1: absence from the 36 main slots authorised a new item.
 *       M6: only Rails {@code pending} does; absence authorises nothing.</li>
 *   <li>M1: an offhand copy was invisible and a duplicate MERGED into it.
 *       M6: where the item lives is never consulted for delivery.</li>
 *   <li>M1: a malformed id threw out of the batch, abandoning later rows.
 *       M6: the row is logged and skipped; later rows still deliver.</li>
 *   <li>M1: an unregistered id silently became air and vanished.
 *       M6: logged and skipped; no air is delivered.</li>
 * </ul>
 *
 * <p>No Rails server exists in a GameTest, so the delivery acknowledgement always fails here.
 * That is deliberate and is itself a proof: a failed acknowledgement must never produce a second
 * item, and every test below runs under exactly that condition.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BlessedItemInventorySyncGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private static final String VALID_ITEM = "minecraft:diamond";
    private static final Item VALID = Items.DIAMOND;
    private static final String OTHER_ITEM = "minecraft:emerald";
    private static final Item OTHER = Items.EMERALD;

    private static final String UNREGISTERED_ITEM = "britannia_mod:does_not_exist_xyz";
    private static final String MALFORMED_ITEM = "not a valid id";

    // --- G1: pending delivers exactly one, fully stamped ----------------------

    @GameTest(template = TEMPLATE)
    public static void pendingDeliversExactlyOneFullyStampedItem(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        UUID instance = UUID.randomUUID();
        String deedId = "deed-g1";

        BlessedItemInventorySync.apply(player, List.of(pending(VALID_ITEM, deedId, instance)));

        check(mainStacksOf(player, VALID) == 1,
                "expected exactly one delivered stack, found " + mainStacksOf(player, VALID));

        CompoundTag tag = customData(firstStackOf(player, VALID));
        check(tag.getBoolean("blessed"), "blessed flag stamped");
        check(player.getStringUUID().equals(tag.getString("owner")), "owner stamped");
        check(deedId.equals(tag.getString("deed_id")), "deed_id stamped (entitlement identity)");
        check(instance.toString().equals(tag.getString("instance_uuid")),
                "instance_uuid stamped -- M1 asserted this field was ABSENT");
        check(tag.size() == 4, "exactly the four blessed fields, found " + tag.size());
        helper.succeed();
    }

    // --- G2/G4: active never delivers, however empty the inventory ------------

    @GameTest(template = TEMPLATE)
    public static void activeNeverDeliversEvenWithACompletelyEmptyInventory(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        BlessedItemInventorySync.apply(player,
                List.of(active(VALID_ITEM, "deed-g2", UUID.randomUUID())));

        check(totalItems(player) == 0,
                "inventory absence must not authorise a replacement, found " + totalItems(player));
        helper.succeed();
    }

    // --- G3: offhand no longer matters ---------------------------------------

    @GameTest(template = TEMPLATE)
    public static void activeWithTheItemInTheOffhandCreatesNoDuplicate(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        UUID instance = UUID.randomUUID();
        player.getInventory().offhand.set(0, stamped(player, "deed-g3", instance));

        BlessedItemInventorySync.apply(player, List.of(active(VALID_ITEM, "deed-g3", instance)));

        check(mainStacksOf(player, VALID) == 0, "no duplicate landed in the main inventory");
        check(player.getInventory().offhand.get(0).getCount() == 1,
                "and nothing merged into the offhand stack -- the M1 defect");
        check(totalItems(player) == 1, "exactly one exists, found " + totalItems(player));
        helper.succeed();
    }

    // --- G5/G6: bad identifiers are isolated ---------------------------------

    @GameTest(template = TEMPLATE)
    public static void aMalformedIdIsSkippedAndLaterRowsStillDeliver(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        BlessedItemInventorySync.apply(player, List.of(
                pending(MALFORMED_ITEM, "deed-g5-bad", UUID.randomUUID()),
                pending(VALID_ITEM, "deed-g5-good", UUID.randomUUID())));

        check(mainStacksOf(player, VALID) == 1,
                "the valid row behind a malformed one must still deliver -- M1 proved it did not");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anUnregisteredIdIsSkippedAndLaterRowsStillDeliver(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        BlessedItemInventorySync.apply(player, List.of(
                pending(UNREGISTERED_ITEM, "deed-g6-bad", UUID.randomUUID()),
                pending(VALID_ITEM, "deed-g6-good", UUID.randomUUID())));

        check(mainStacksOf(player, VALID) == 1, "the valid row delivered");
        check(totalItems(player) == 1,
                "and no air pseudo-delivery happened, found " + totalItems(player) + " item(s)");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aGoodRowAheadOfABadOneIsUnaffected(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        BlessedItemInventorySync.apply(player, List.of(
                pending(VALID_ITEM, "deed-g5b-good", UUID.randomUUID()),
                pending(MALFORMED_ITEM, "deed-g5b-bad", UUID.randomUUID()),
                pending(OTHER_ITEM, "deed-g5b-third", UUID.randomUUID())));

        check(mainStacksOf(player, VALID) == 1, "the first row delivered");
        check(mainStacksOf(player, OTHER) == 1, "and so did the third, behind the malformed one");
        helper.succeed();
    }

    // --- G7: legacy consumed deed --------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void aUsedRowStillMaterializesNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        BlessedItemInventorySync.apply(player, List.of(
                new BlessedItemSyncAPI.BlessedRow(VALID_ITEM, "deed-g7", true,
                        UUID.randomUUID().toString(), "pending")));

        check(totalItems(player) == 0,
                "a spent deed is never re-delivered, even if Rails sent lifecycle fields");
        helper.succeed();
    }

    // --- fail-closed cases ----------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void aRowWithNoMaterializationIdentityDeliversNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        BlessedItemInventorySync.apply(player,
                List.of(new BlessedItemSyncAPI.BlessedRow(VALID_ITEM, "deed-nolifecycle", false)));

        check(totalItems(player) == 0, "no identity means no delivery -- fail closed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anUnknownLifecycleStateDeliversNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        BlessedItemInventorySync.apply(player, List.of(
                new BlessedItemSyncAPI.BlessedRow(VALID_ITEM, "deed-unknown", false,
                        UUID.randomUUID().toString(), "reticulating")));

        check(totalItems(player) == 0, "an unrecognised state is never treated as deliverable");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aDestroyedInstanceDoesNotSelfRestore(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        BlessedItemInventorySync.apply(player, List.of(
                new BlessedItemSyncAPI.BlessedRow(VALID_ITEM, "deed-destroyed", false,
                        UUID.randomUUID().toString(), "destroyed")));

        check(totalItems(player) == 0, "the mod never grants itself restoration authority");
        helper.succeed();
    }

    // --- G8 / C4: replay ------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void twoSyncsForTheSameInstanceDeliverOnlyOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        UUID instance = UUID.randomUUID();
        BlessedItemSyncAPI.BlessedRow row = pending(VALID_ITEM, "deed-g8", instance);

        BlessedItemInventorySync.apply(player, List.of(row));
        BlessedItemInventorySync.apply(player, List.of(row));

        check(totalItems(player) == 1,
                "the durable receipt suppressed the second delivery, found " + totalItems(player));
        helper.succeed();
    }

    // --- crash windows --------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void aCompletedDeliveryLeavesADeliveredReceipt(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        UUID instance = UUID.randomUUID();

        BlessedItemInventorySync.apply(player, List.of(pending(VALID_ITEM, "deed-c1", instance)));

        Optional<BlessedDeliveryReceipt> receipt =
                BlessedDeliveryReceipts.find(player.serverLevel(), instance);
        check(receipt.isPresent(), "a receipt exists for the delivered instance");
        check(receipt.get().status() == BlessedDeliveryReceiptStatus.DELIVERED,
                "and it records the delivery as complete");
        check(instance.equals(receipt.get().instanceUuid()), "keyed by instance_uuid");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aCrashBetweenReceiptAndItemRecoversToExactlyOneItem(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        UUID instance = UUID.randomUUID();
        String deedId = "deed-c2";

        // Exactly the state a crash in that window leaves behind.
        BlessedDeliveryReceipts.recordPendingDelivery(player.serverLevel(), instance, deedId,
                VALID_ITEM, player.getUUID(), System.currentTimeMillis());

        BlessedItemInventorySync.apply(player, List.of(pending(VALID_ITEM, deedId, instance)));

        check(mainStacksOf(player, VALID) == 1,
                "recovery delivered exactly one, found " + mainStacksOf(player, VALID));
        check(instance.toString().equals(
                        customData(firstStackOf(player, VALID)).getString("instance_uuid")),
                "and reused the SAME instance_uuid -- a second identity is a second entitlement");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aDeliveredReceiptSuppressesRedeliveryWhenRailsStillSaysPending(
            GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        UUID instance = UUID.randomUUID();
        String deedId = "deed-c3";

        BlessedItemInventorySync.apply(player, List.of(pending(VALID_ITEM, deedId, instance)));
        check(totalItems(player) == 1, "first delivery happened");

        // Rails never learned, so it offers the same pending row again.
        BlessedItemInventorySync.apply(player, List.of(pending(VALID_ITEM, deedId, instance)));

        check(totalItems(player) == 1,
                "the delivered receipt replayed the acknowledgement, not the delivery, found "
                        + totalItems(player));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aReceiptBoundToADifferentItemRefusesDelivery(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        UUID instance = UUID.randomUUID();

        BlessedDeliveryReceipts.recordPendingDelivery(player.serverLevel(), instance, "deed-c6",
                OTHER_ITEM, player.getUUID(), System.currentTimeMillis());

        BlessedItemInventorySync.apply(player, List.of(pending(VALID_ITEM, "deed-c6", instance)));

        check(totalItems(player) == 0,
                "a mismatched receipt must block delivery, found " + totalItems(player));
        helper.succeed();
    }

    // --- helpers --------------------------------------------------------------

    private static BlessedItemSyncAPI.BlessedRow pending(String item, String deedId, UUID instance) {
        return new BlessedItemSyncAPI.BlessedRow(item, deedId, false, instance.toString(), "pending");
    }

    private static BlessedItemSyncAPI.BlessedRow active(String item, String deedId, UUID instance) {
        return new BlessedItemSyncAPI.BlessedRow(item, deedId, false, instance.toString(), "active");
    }

    private static ItemStack stamped(ServerPlayer player, String deedId, UUID instance) {
        ItemStack stack = new ItemStack(VALID);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putString("owner", player.getStringUUID());
        tag.putString("deed_id", deedId);
        tag.putString("instance_uuid", instance.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static CompoundTag customData(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) throw new GameTestAssertException("stack carries no custom data");
        return data.copyTag();
    }

    private static int mainStacksOf(ServerPlayer player, Item item) {
        return (int) player.getInventory().items.stream().filter(stack -> stack.is(item)).count();
    }

    private static ItemStack firstStackOf(ServerPlayer player, Item item) {
        return player.getInventory().items.stream()
                .filter(stack -> stack.is(item))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("no stack of " + item + " present"));
    }

    private static int totalItems(ServerPlayer player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) total += stack.getCount();
        for (ItemStack stack : player.getInventory().offhand) total += stack.getCount();
        for (ItemStack stack : player.getInventory().armor) total += stack.getCount();
        return total;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
