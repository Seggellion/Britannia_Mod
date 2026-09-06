package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blessed.BlessedItemLifecycleMetadata;
import com.seggellion.britannia_mod.blessed.delivery.BlessedDeliveryReceipt;
import com.seggellion.britannia_mod.blessed.delivery.BlessedDeliveryReceiptStatus;
import com.seggellion.britannia_mod.blessed.delivery.BlessedDeliveryReceipts;
import com.seggellion.britannia_mod.blessed.delivery.BlessedDestructionReason;
import com.seggellion.britannia_mod.blessed.delivery.BlessedDestructionReporter;
import com.seggellion.britannia_mod.block.entity.TrashBarrelBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.sync.BlessedItemInventorySync;
import com.seggellion.britannia_mod.sync.BlessedItemSyncAPI;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Starfarer M7. What happens to a blessed item the world is trying to destroy.
 *
 * <p>Every rescue path here ends with the item still existing and NOTHING reported. A destruction
 * report asserts that an instance positively no longer exists; an item that was saved plainly
 * still does. The only path that reports is the audited operator action, tested at the end.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BlessedItemRescueGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    // --- G1: dropping is not destruction -------------------------------------

    @GameTest(template = TEMPLATE)
    public static void droppingABlessedItemChangesNoLifecycleState(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        UUID instance = deliverOne(player, "deed-g1");

        ItemStack held = player.getInventory().items.stream()
                .filter(stack -> stack.is(Items.DIAMOND)).findFirst().orElseThrow();
        player.getInventory().clearContent();
        ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(),
                held.copy());
        player.level().addFreshEntity(dropped);

        BlessedDeliveryReceipt receipt = receipt(player, instance);
        check(receipt.status() == BlessedDeliveryReceiptStatus.DELIVERED,
                "dropping leaves the materialization delivered, found " + receipt.status());
        check(!dropped.isRemoved(), "the dropped entity still exists");
        dropped.discard();
        helper.succeed();
    }

    // --- G2: despawn rescue ---------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void anExpiringBlessedItemIsReturnedToItsStampedOwner(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        UUID instance = UUID.randomUUID();

        ItemEntity dropped = spawnBlessed(helper, player, "deed-g2", instance);
        NeoForge.EVENT_BUS.post(new ItemExpireEvent(dropped));

        check(dropped.isRemoved(), "the expiring entity was consumed by the rescue");
        ItemStack rescued = onlyBlessedStack(player);
        check(instance.toString().equals(customData(rescued).getString("instance_uuid")),
                "the SAME instance_uuid came back -- no new identity was minted");
        check(countBlessed(player) == 1, "exactly one copy exists");
        helper.succeed();
    }

    // --- G3: lava rescue ------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aBlessedItemInLavaIsRescuedAndStaysActive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        UUID instance = UUID.randomUUID();

        BlockPos lavaPos = new BlockPos(1, 2, 1);
        helper.setBlock(lavaPos, Blocks.LAVA);

        BlockPos absolute = helper.absolutePos(lavaPos);
        ItemEntity dropped = new ItemEntity(helper.getLevel(),
                absolute.getX() + 0.5D, absolute.getY() + 0.5D, absolute.getZ() + 0.5D,
                blessedStack(player, "deed-g3", instance));
        helper.getLevel().addFreshEntity(dropped);

        // The real per-entity tick is what fires the rescue.
        helper.runAfterDelay(5, () -> {
            check(countBlessed(player) == 1,
                    "the medallion was rescued out of lava to its owner, found " + countBlessed(player));
            ItemStack rescued = onlyBlessedStack(player);
            check(instance.toString().equals(customData(rescued).getString("instance_uuid")),
                    "same instance_uuid -- rescue is not a re-delivery");
            check(!dropped.isAlive() || dropped.isRemoved(), "the burning entity is gone");
            helper.succeed();
        });
    }

    // --- G4: Trash Barrel protection -----------------------------------------

    @GameTest(template = TEMPLATE)
    public static void theTrashBarrelDeletesOrdinaryItemsButSparesBlessedOnes(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BlockRegistry.TRASH_BARREL_BLOCK.get());

        TrashBarrelBlockEntity barrel =
                (TrashBarrelBlockEntity) helper.getBlockEntity(pos);
        UUID instance = UUID.randomUUID();
        barrel.setItem(0, new ItemStack(Items.COBBLESTONE, 12));
        barrel.setItem(1, blessedStack(player, "deed-g4", instance));
        barrel.setItem(2, new ItemStack(Items.DIRT, 3));

        int deleted = barrel.clearOrdinaryContents();

        check(deleted == 2, "both ordinary stacks were emptied, found " + deleted);
        check(barrel.getItem(0).isEmpty(), "the cobblestone is gone");
        check(barrel.getItem(2).isEmpty(), "the dirt is gone");

        ItemStack spared = barrel.getItem(1);
        check(!spared.isEmpty(), "the blessed item survived the timed clear");
        check(instance.toString().equals(customData(spared).getString("instance_uuid")),
                "and kept its lifecycle identity intact");
        helper.succeed();
    }

    /** A pre-M6 blessed deed has no instance_uuid, and is protected all the same. */
    @GameTest(template = TEMPLATE)
    public static void theTrashBarrelAlsoSparesLegacyBlessedItems(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BlockRegistry.TRASH_BARREL_BLOCK.get());
        TrashBarrelBlockEntity barrel = (TrashBarrelBlockEntity) helper.getBlockEntity(pos);

        ItemStack legacy = new ItemStack(Items.DIAMOND);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putString("owner", player.getStringUUID());
        tag.putString("deed_id", "legacy-deed");
        legacy.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        barrel.setItem(0, legacy);
        barrel.setItem(1, new ItemStack(Items.DIRT, 5));

        check(barrel.clearOrdinaryContents() == 1, "only the dirt was emptied");
        check(!barrel.getItem(0).isEmpty(), "the legacy blessed deed survived");
        helper.succeed();
    }

    // --- G5: cross-player possession -----------------------------------------

    @GameTest(template = TEMPLATE)
    public static void anotherPlayerHoldingItStealsNothing(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer holder = helper.makeMockServerPlayerInLevel();
        owner.getInventory().clearContent();
        holder.getInventory().clearContent();
        UUID instance = UUID.randomUUID();

        // Owner's medallion ends up in someone else's pocket.
        holder.getInventory().add(blessedStack(owner, "deed-g5", instance));

        ItemStack carried = holder.getInventory().items.stream()
                .filter(stack -> stack.is(Items.DIAMOND)).findFirst().orElseThrow();
        CompoundTag tag = customData(carried);

        check(owner.getStringUUID().equals(tag.getString("owner")),
                "the stamp still names the owner, not the holder");
        check(instance.toString().equals(tag.getString("instance_uuid")),
                "and the same materialization identity");
        check(countBlessed(owner) == 0,
                "no replacement was conjured for the owner merely because someone else holds it");
        helper.succeed();
    }

    // --- G6: the one path that reports destruction ----------------------------

    @GameTest(template = TEMPLATE)
    public static void anAuditedOperatorDestructionMovesTheReceiptToDestroyed(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        UUID instance = deliverOne(player, "deed-g6");

        boolean reported = BlessedDestructionReporter.reportDestroyed(
                player.serverLevel(), instance, player.getUUID(), BlessedDestructionReason.OPERATOR);

        check(reported, "the operator report was accepted locally");
        check(receipt(player, instance).status() == BlessedDeliveryReceiptStatus.DESTROYED,
                "the receipt is terminal");

        // Idempotent: replaying it is success, not an error.
        check(BlessedDestructionReporter.reportDestroyed(player.serverLevel(), instance,
                player.getUUID(), BlessedDestructionReason.OPERATOR),
                "a replayed destruction report is accepted");
        helper.succeed();
    }

    /** Nothing may be reported destroyed that this shard never delivered. */
    @GameTest(template = TEMPLATE)
    public static void anUnknownInstanceCannotBeReportedDestroyed(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        check(!BlessedDestructionReporter.reportDestroyed(player.serverLevel(), UUID.randomUUID(),
                player.getUUID(), BlessedDestructionReason.OPERATOR),
                "a shard cannot assert the destruction of something it never delivered");
        helper.succeed();
    }

    // --- G7/G8: destroyed is terminal; restoration needs a new identity -------

    @GameTest(template = TEMPLATE)
    public static void aDestroyedInstanceIsNeverRedeliveredEvenIfRailsStillSaysPending(
            GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        UUID instance = deliverOne(player, "deed-g7");
        BlessedDestructionReporter.reportDestroyed(player.serverLevel(), instance,
                player.getUUID(), BlessedDestructionReason.OPERATOR);
        player.getInventory().clearContent();

        // Rails has not heard yet and still offers the old row.
        BlessedItemInventorySync.apply(player, List.of(pending("deed-g7", instance)));

        check(countBlessed(player) == 0,
                "a destroyed instance is never resurrected, found " + countBlessed(player));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aRestorationArrivesAsANewIdentityAndMaterializesExactlyOnce(
            GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        UUID destroyed = deliverOne(player, "deed-g8");
        BlessedDestructionReporter.reportDestroyed(player.serverLevel(), destroyed,
                player.getUUID(), BlessedDestructionReason.OPERATOR);
        player.getInventory().clearContent();

        // Rails restores by minting a NEW materialization for the same entitlement.
        UUID restored = UUID.randomUUID();
        BlessedItemInventorySync.apply(player, List.of(pending("deed-g8", restored)));

        check(countBlessed(player) == 1,
                "the restoration materialized exactly once, found " + countBlessed(player));
        check(restored.toString().equals(customData(onlyBlessedStack(player)).getString("instance_uuid")),
                "under the NEW identity");
        check(receipt(player, destroyed).status() == BlessedDeliveryReceiptStatus.DESTROYED,
                "and the old identity stays destroyed forever");
        helper.succeed();
    }

    // --- helpers --------------------------------------------------------------

    private static BlessedItemSyncAPI.BlessedRow pending(String deedId, UUID instance) {
        return new BlessedItemSyncAPI.BlessedRow("minecraft:diamond", deedId, false,
                instance.toString(), "pending");
    }

    /** Delivers one medallion through the real M6 path and returns its instance uuid. */
    private static UUID deliverOne(ServerPlayer player, String deedId) {
        UUID instance = UUID.randomUUID();
        BlessedItemInventorySync.apply(player, List.of(pending(deedId, instance)));
        return instance;
    }

    private static ItemStack blessedStack(ServerPlayer owner, String deedId, UUID instance) {
        ItemStack stack = new ItemStack(Items.DIAMOND);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putString("owner", owner.getStringUUID());
        tag.putString("deed_id", deedId);
        tag.putString("instance_uuid", instance.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static ItemEntity spawnBlessed(GameTestHelper helper, ServerPlayer owner,
                                           String deedId, UUID instance) {
        BlockPos absolute = helper.absolutePos(new BlockPos(1, 2, 1));
        ItemEntity entity = new ItemEntity(helper.getLevel(),
                absolute.getX() + 0.5D, absolute.getY() + 0.5D, absolute.getZ() + 0.5D,
                blessedStack(owner, deedId, instance));
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static BlessedDeliveryReceipt receipt(ServerPlayer player, UUID instance) {
        Optional<BlessedDeliveryReceipt> found =
                BlessedDeliveryReceipts.find(player.serverLevel(), instance);
        if (found.isEmpty()) throw new GameTestAssertException("no receipt for instance " + instance);
        return found.get();
    }

    private static int countBlessed(ServerPlayer player) {
        return (int) player.getInventory().items.stream()
                .filter(stack -> BlessedItemLifecycleMetadata.isBlessed(stack))
                .count();
    }

    private static ItemStack onlyBlessedStack(ServerPlayer player) {
        return player.getInventory().items.stream()
                .filter(stack -> BlessedItemLifecycleMetadata.isBlessed(stack))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("no blessed stack in inventory"));
    }

    private static CompoundTag customData(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) throw new GameTestAssertException("stack carries no custom data");
        return data.copyTag();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
