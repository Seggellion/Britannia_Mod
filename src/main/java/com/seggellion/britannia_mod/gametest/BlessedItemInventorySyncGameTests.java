package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.sync.BlessedItemInventorySync;
import com.seggellion.britannia_mod.sync.BlessedItemSyncAPI;

import net.minecraft.ResourceLocationException;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Starfarer Milestone 1, characterization only: {@link BlessedItemInventorySync#apply} held
 * exactly as it behaves today, before Milestone 6 touches it.
 *
 * <p>Nothing here is an endorsement. Two of these tests pin behaviour that is plainly wrong
 * -- an offhand copy is invisible to the duplicate scan, and one unparseable row throws
 * straight out of {@code apply} and abandons every row behind it -- and they are named and
 * commented so that nobody later reads a green test as a statement of intent. They exist so
 * that when M6 changes that behaviour, the change is visible as a diff to this file rather
 * than as an unexplained difference in a running server.
 *
 * <p>These have to be GameTests. {@code apply} takes a real {@link ServerPlayer}, and by the
 * project's first architecture decision entities cannot be constructed in the plain JUnit
 * harness; {@code makeMockServerPlayerInLevel()} needs a live server and level.
 *
 * <p>Note also that {@code BlessedItemInventorySync.addBlessedItem} is dead code: {@code apply}
 * inlines the identical eight lines and nothing in the source tree calls the private method.
 * It is unreachable, so it is untestable, and no test below pretends otherwise.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BlessedItemInventorySyncGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** A real, registered vanilla item. Deliberately not a Starfarer item -- M1 registers none. */
    private static final String VALID_ITEM = "minecraft:diamond";
    private static final Item VALID = Items.DIAMOND;

    /** Parses cleanly, resolves to nothing. {@code BuiltInRegistries.ITEM} is a defaulted registry. */
    private static final String UNREGISTERED_ITEM = "britannia_mod:does_not_exist_xyz";

    /** No colon, and spaces are outside {@code [a-z0-9/._-]}, so the path assertion rejects it. */
    private static final String MALFORMED_PATH = "not a valid id";

    /** Has a colon, but uppercase is outside {@code [a-z0-9_.-]}, so the namespace assertion rejects it. */
    private static final String MALFORMED_NAMESPACE = "Uppercase:Thing";

    private BlessedItemInventorySyncGameTests() {
    }

    /* ------------------------------------------------------------------ */
    /*  D1 -- a used row is skipped outright                               */
    /* ------------------------------------------------------------------ */

    /** {@code used()} short-circuits before the identifier is even parsed, so nothing is granted. */
    @GameTest(template = TEMPLATE)
    public static void aUsedRowMaterializesNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        BlessedItemInventorySync.apply(player,
                List.of(new BlessedItemSyncAPI.BlessedRow(VALID_ITEM, "deed-1", true)));

        check(totalItems(player) == 0,
                "a used row put " + totalItems(player) + " item(s) in the inventory; used rows are consumed "
                + "grants and must never be re-issued");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  D2 -- an unused row for a real item is granted exactly once        */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE)
    public static void anUnusedRowForARegisteredItemMaterializesExactlyOneStack(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        BlessedItemInventorySync.apply(player,
                List.of(new BlessedItemSyncAPI.BlessedRow(VALID_ITEM, "deed-d2", false)));

        check(mainStacksOf(player, VALID) == 1,
                "expected exactly one stack of " + VALID_ITEM + " in the main inventory, found "
                + mainStacksOf(player, VALID));
        check(totalItems(player) == 1,
                "expected exactly one item in total, found " + totalItems(player));
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  D3 -- the tag shape, including what is deliberately absent         */
    /* ------------------------------------------------------------------ */

    /**
     * The granted stack carries three fields and no more.
     *
     * <p>The {@code instance_uuid} assertion is the before-half of the M6 proof: today there is
     * no per-instance identity on a blessed grant at all, so two grants of the same deed are
     * indistinguishable once they are in a chest. M6 adds {@code instance_uuid} additively --
     * this test says, with a date on it, that it was not there first, which is what makes
     * "additive" a checkable claim rather than an assertion.
     */
    @GameTest(template = TEMPLATE)
    public static void theGrantedStackCarriesBlessedOwnerAndDeedIdAndNothingElse(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        String deedId = "deed-d3";

        BlessedItemInventorySync.apply(player,
                List.of(new BlessedItemSyncAPI.BlessedRow(VALID_ITEM, deedId, false)));

        ItemStack granted = firstNonEmptyMainStack(player);
        check(granted != null, "nothing was granted, so there is no tag to inspect");
        CompoundTag tag = customData(granted);

        check(tag.contains("blessed") && tag.getBoolean("blessed"),
                "the granted stack is not marked blessed: " + tag);
        check(player.getStringUUID().equals(tag.getString("owner")),
                "owner is '" + tag.getString("owner") + "', expected the player's string UUID '"
                + player.getStringUUID() + "'");
        check(deedId.equals(tag.getString("deed_id")),
                "deed_id is '" + tag.getString("deed_id") + "', expected '" + deedId + "'");

        // The before-half of the M6 additive-field proof. If this ever starts failing because
        // instance_uuid is present, that is M6 landing -- update this test, do not delete it.
        check(!tag.contains("instance_uuid"),
                "instance_uuid is already present on a granted blessed stack; M6 is supposed to be "
                + "the change that introduces it, and this test is the record that it was absent before");

        check(tag.size() == 3,
                "the granted tag carries " + tag.size() + " fields (" + tag + "); today it carries exactly "
                + "blessed, owner and deed_id");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  D4 -- the duplicate scan works, for the 36 main slots              */
    /* ------------------------------------------------------------------ */

    /**
     * A matching stack already in a main slot suppresses the grant.
     *
     * <p>Deliberately placed in slot 9 -- the first slot outside the hotbar -- so this proves the
     * scan covers all 36 of {@code inv.items} and not just the nine slots a player can see.
     */
    @GameTest(template = TEMPLATE)
    public static void aMatchingStackInAMainSlotSuppressesASecondCopy(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        String deedId = "deed-d4";
        player.getInventory().items.set(9, blessedStack(player, deedId));

        BlessedItemInventorySync.apply(player,
                List.of(new BlessedItemSyncAPI.BlessedRow(VALID_ITEM, deedId, false)));

        check(mainStacksOf(player, VALID) == 1,
                "the duplicate scan missed a matching stack in main slot 9: found "
                + mainStacksOf(player, VALID) + " stacks");
        check(totalItems(player) == 1,
                "expected the one pre-placed item and nothing more, found " + totalItems(player));
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  D5 -- KNOWN DEFECT: the offhand is outside the scan                */
    /* ------------------------------------------------------------------ */

    /**
     * KNOWN DEFECT, characterized here rather than fixed. M6 fixes it.
     *
     * <p>{@code apply} scans {@code inv.items}, which is the 36 main slots only -- not
     * {@code inv.offhand}, not {@code inv.armor}. A player holding their blessed deed in the
     * offhand therefore looks, to the sync, like a player who has none, and is granted a second
     * one on every login.
     *
     * <p>What actually happens is one degree worse than "a second stack appears". Because the
     * grant is built with byte-identical components, {@code Inventory#add} finds remaining space
     * in slot 40 -- the offhand -- before it looks at any main slot, and merges the duplicate
     * into the very stack the scan failed to see. So the player's offhand stack silently grows
     * from one to two, and no new stack appears anywhere for them to notice.
     *
     * <p>This test asserts that duplication. It is recording a defect. Do not read it as intent.
     */
    @GameTest(template = TEMPLATE)
    public static void characterizationOffhandIsInvisibleToDedupeScanKnownDefect(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        String deedId = "deed-d5";
        // Built exactly as apply() builds it, owner included -- this is what a player who was
        // granted the deed and then moved it to their offhand is actually holding.
        player.getInventory().offhand.set(0, blessedStack(player, deedId));

        BlessedItemInventorySync.apply(player,
                List.of(new BlessedItemSyncAPI.BlessedRow(VALID_ITEM, deedId, false)));

        Inventory inv = player.getInventory();
        int offhandCount = inv.offhand.get(0).getCount();
        int mainStacks = mainStacksOf(player, VALID);

        check(totalItems(player) == 2,
                "the offhand blind spot did not duplicate: total items is " + totalItems(player)
                + " (offhand count " + offhandCount + ", main stacks " + mainStacks + "). If this now "
                + "reads 1, the defect has been fixed and this characterization must be retired.");
        check(offhandCount == 2,
                "expected the duplicate to merge into the offhand stack (count 2), found count "
                + offhandCount + " with " + mainStacks + " matching main stack(s)");
        check(mainStacks == 0,
                "expected the duplicate to land in the offhand rather than a main slot, but "
                + mainStacks + " matching main stack(s) appeared");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  D6 -- the two different kinds of bad identifier                    */
    /* ------------------------------------------------------------------ */

    /**
     * A malformed identifier throws {@link ResourceLocationException} straight out of {@code apply}.
     *
     * <p>{@code ResourceLocation.parse} is the asserting parser -- {@code tryParse} is the one that
     * returns null. Neither {@code apply} nor anything between it and the login handler catches it,
     * so a single bad row from Rails becomes a thrown exception on the server thread.
     *
     * <p>Both failure modes are covered: a path with characters outside {@code [a-z0-9/._-]}, and a
     * namespace with characters outside {@code [a-z0-9_.-]}.
     */
    @GameTest(template = TEMPLATE)
    public static void aMalformedItemIdThrowsOutOfApply(GameTestHelper helper) {
        assertMalformedIdThrows(helper, MALFORMED_PATH, "deed-d6a");
        assertMalformedIdThrows(helper, MALFORMED_NAMESPACE, "deed-d6b");
        helper.succeed();
    }

    /**
     * A well-formed identifier for an item that does not exist fails silently instead.
     *
     * <p>{@code BuiltInRegistries.ITEM} is a defaulted registry, so an unknown key resolves to
     * {@code minecraft:air} rather than null. The grant is then built on air, is empty by
     * construction, and {@code Inventory#add} rejects an empty stack without comment. No throw,
     * no item, no log line -- the row simply evaporates, and the player is never told.
     *
     * <p>This is the quiet half of the same problem as the test above, and it is the harder half:
     * a thrown exception at least leaves a stack trace.
     */
    @GameTest(template = TEMPLATE)
    public static void aWellFormedButUnregisteredItemIdSilentlyGrantsNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        BlessedItemInventorySync.apply(player,
                List.of(new BlessedItemSyncAPI.BlessedRow(UNREGISTERED_ITEM, "deed-d6c", false)));

        check(totalItems(player) == 0,
                "an unregistered item id put " + totalItems(player) + " item(s) in the inventory; today "
                + "it resolves to air and is dropped silently");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  D7 -- one bad row abandons the rest of the batch                   */
    /* ------------------------------------------------------------------ */

    /**
     * KNOWN DEFECT, characterized. A malformed row abandons every row behind it.
     *
     * <p>There is no per-row error handling: the throw from {@code ResourceLocation.parse} unwinds
     * the whole {@code for} loop. So one bad row in a player's blessed-item list costs them every
     * grant that Rails happened to return after it, silently and with no partial-progress record.
     * The order of rows in an HTTP response is not something a player has any control over.
     *
     * <p>This is the before-half of the M6 hardening proof. M6 makes this test's expectation wrong.
     */
    @GameTest(template = TEMPLATE)
    public static void aMalformedRowAbandonsEveryRowBehindIt(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        List<BlessedItemSyncAPI.BlessedRow> rows = List.of(
                new BlessedItemSyncAPI.BlessedRow(MALFORMED_PATH, "deed-d7-bad", false),
                new BlessedItemSyncAPI.BlessedRow(VALID_ITEM, "deed-d7-good", false));

        boolean threw = false;
        try {
            BlessedItemInventorySync.apply(player, rows);
        } catch (ResourceLocationException expected) {
            threw = true;
        }

        check(threw, "a malformed leading row did not throw; the batch apparently survived it now");
        check(totalItems(player) == 0,
                "the good row behind the malformed one materialized " + totalItems(player) + " item(s); "
                + "today the throw abandons the rest of the batch");
        helper.succeed();
    }

    /**
     * The mirror image, which is what makes the previous test a statement about ordering rather
     * than about the good row itself: put the good row first and it is granted, then the bad row
     * throws. The batch is therefore partially applied and the caller has no way to know how far
     * it got.
     */
    @GameTest(template = TEMPLATE)
    public static void rowsAheadOfTheMalformedOneAreGrantedAndTheBatchIsLeftPartiallyApplied(
            GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        List<BlessedItemSyncAPI.BlessedRow> rows = List.of(
                new BlessedItemSyncAPI.BlessedRow(VALID_ITEM, "deed-d7b-good", false),
                new BlessedItemSyncAPI.BlessedRow(MALFORMED_NAMESPACE, "deed-d7b-bad", false));

        boolean threw = false;
        try {
            BlessedItemInventorySync.apply(player, rows);
        } catch (ResourceLocationException expected) {
            threw = true;
        }

        check(threw, "the trailing malformed row did not throw");
        check(totalItems(player) == 1,
                "expected the leading good row to have been granted before the throw, found "
                + totalItems(player) + " item(s)");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  helpers                                                            */
    /* ------------------------------------------------------------------ */

    private static void assertMalformedIdThrows(GameTestHelper helper, String itemName, String deedId) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        Throwable caught = null;
        try {
            BlessedItemInventorySync.apply(player,
                    List.of(new BlessedItemSyncAPI.BlessedRow(itemName, deedId, false)));
        } catch (Throwable t) {
            caught = t;
        }

        check(caught != null,
                "'" + itemName + "' did not throw out of apply(); it left " + totalItems(player)
                + " item(s) in the inventory instead");
        check(caught instanceof ResourceLocationException,
                "'" + itemName + "' threw " + caught.getClass().getName() + " ('" + caught.getMessage()
                + "'), expected ResourceLocationException from ResourceLocation.parse");
        check(totalItems(player) == 0,
                "'" + itemName + "' threw but still left " + totalItems(player) + " item(s) behind");
    }

    /** A stack built the way {@code apply} builds one, so components compare equal. */
    private static ItemStack blessedStack(ServerPlayer player, String deedId) {
        ItemStack stack = new ItemStack(VALID);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putString("owner", player.getStringUUID());
        tag.putString("deed_id", deedId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static CompoundTag customData(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    /** Non-empty stacks of {@code item} in the 36 main slots -- the range {@code apply} scans. */
    private static int mainStacksOf(ServerPlayer player, Item item) {
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(item)) {
                found++;
            }
        }
        return found;
    }

    private static ItemStack firstNonEmptyMainStack(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return null;
    }

    /** Every item the player holds anywhere -- main, armor and offhand -- counted individually. */
    private static int totalItems(ServerPlayer player) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            total += inv.getItem(slot).getCount();
        }
        return total;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
