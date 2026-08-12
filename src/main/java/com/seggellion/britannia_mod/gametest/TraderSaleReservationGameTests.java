package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.item.BankItemCodec;
import com.seggellion.britannia_mod.economy.TraderSaleReservationReceipt;
import com.seggellion.britannia_mod.economy.TraderSaleReservationRecovery;
import com.seggellion.britannia_mod.economy.TraderSaleReservationStore;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Vendor/Trader Milestone 19.5: proves the durable reservation actually closes
 * the crash window it exists for — items stranded by a crash come back, and the
 * one ambiguous case never duplicates them.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class TraderSaleReservationGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private TraderSaleReservationGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void aCrashAfterRemovalRefundsTheItemsOnRecovery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        // A sale removed 12 iron ingots and the process died before Rails answered.
        String key = "sale:test:" + UUID.randomUUID();
        record(level, key, player.getUUID(), new ItemStack(Items.IRON_INGOT, 12),
                TraderSaleReservationReceipt.Status.DISPATCHED);

        int refunded = TraderSaleReservationRecovery.refundStrandedReservations(level, player);
        check(refunded == 1, "expected one stack refunded, got " + refunded);
        check(count(player, Items.IRON_INGOT) == 12,
                "the stranded stack must come back intact, found " + count(player, Items.IRON_INGOT));
        check(TraderSaleReservationStore.get(level).find(key) == null,
                "a refunded reservation must be resolved, not left to refund again");

        // Recovery is idempotent: a second pass mints nothing.
        check(TraderSaleReservationRecovery.refundStrandedReservations(level, player) == 0,
                "a second recovery pass must refund nothing");
        check(count(player, Items.IRON_INGOT) == 12, "a second pass duplicated items");
        helper.succeed();
    }

    /**
     * The anti-duplication rule. A RESERVED receipt means the removal never
     * became durable, so the player's saved inventory still holds the items:
     * refunding would mint a copy.
     */
    @GameTest(template = TEMPLATE)
    public static void aReservationThatNeverBecameDurableIsResolvedWithoutRefund(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        // The player still holds the goods -- the removal never durably happened.
        player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 5));

        String key = "sale:test:" + UUID.randomUUID();
        record(level, key, player.getUUID(), new ItemStack(Items.GOLD_INGOT, 5),
                TraderSaleReservationReceipt.Status.RESERVED);

        int refunded = TraderSaleReservationRecovery.refundStrandedReservations(level, player);
        check(refunded == 0, "a non-durable reservation must never refund");
        check(count(player, Items.GOLD_INGOT) == 5,
                "the player must keep exactly what they had, found " + count(player, Items.GOLD_INGOT));
        check(TraderSaleReservationStore.get(level).find(key) == null,
                "the receipt must still be resolved so it cannot linger");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void receiptsSurviveTheSaveLoadBoundaryAndOnlyTouchTheirOwnPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        String mine = "sale:test:" + UUID.randomUUID();
        String someoneElse = "sale:test:" + UUID.randomUUID();
        TraderSaleReservationStore store = TraderSaleReservationStore.get(level);
        record(level, mine, player.getUUID(), new ItemStack(Items.DIAMOND, 3),
                TraderSaleReservationReceipt.Status.ITEMS_REMOVED);
        record(level, someoneElse, UUID.randomUUID(), new ItemStack(Items.EMERALD, 7),
                TraderSaleReservationReceipt.Status.ITEMS_REMOVED);

        // A real restart round-trips the store through NBT.
        CompoundTag saved = store.save(new CompoundTag(), level.registryAccess());
        TraderSaleReservationStore reloaded =
                TraderSaleReservationStore.load(saved, level.registryAccess());
        check(reloaded.find(mine) != null && reloaded.find(someoneElse) != null,
                "reservations must survive the save/load boundary");
        check(reloaded.find(mine).decodeItems(level.registryAccess()).getFirst().getCount() == 3,
                "the reloaded receipt lost its item payload");

        int refunded = TraderSaleReservationRecovery.refundStrandedReservations(level, player);
        check(refunded == 1, "only this player's reservation should refund, got " + refunded);
        check(count(player, Items.DIAMOND) == 3, "this player's items must return");
        check(count(player, Items.EMERALD) == 0, "another player's reservation must never pay out here");
        check(TraderSaleReservationStore.get(level).find(someoneElse) != null,
                "another player's reservation must remain for their own login");
        helper.succeed();
    }

    private static void record(ServerLevel level, String key, UUID playerUuid,
                               ItemStack stack, TraderSaleReservationReceipt.Status status) {
        TraderSaleReservationStore store = TraderSaleReservationStore.get(level);
        store.record(new TraderSaleReservationReceipt(
                key, playerUuid,
                List.of(BankItemCodec.serialize(stack, level.registryAccess())),
                status, System.currentTimeMillis()));
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == item) total += stack.getCount();
        }
        return total;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
