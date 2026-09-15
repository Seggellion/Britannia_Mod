package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.item.BankItemCodec;
import com.seggellion.britannia_mod.economy.*;
import com.seggellion.britannia_mod.network.payload.SellItemsC2SPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Exercises real inventory, checked disk writes and receipt recovery boundaries. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GameplayTraderRecoveryGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    @GameTest(template = TEMPLATE)
    public static void duplicateRequestsAndChangedComponentsCannotOverReserve(GameTestHelper h) throws Exception {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "M7Selection");
        try {
            player.getInventory().clearContent();
            var original = new ItemStack(Items.APPLE, 3);
            original.set(DataComponents.CUSTOM_NAME, Component.literal("Original"));
            player.getInventory().setItem(0, original.copy());
            var request = new SellItemsC2SPayload.ItemRequest("minecraft:apple", "apple", 2, null);
            var collect = method("collectRequestedItems");
            var selected = (List<?>) collect.invoke(null, player, List.of(request, request));
            var planned = method("planReservation").invoke(null, player, selected);
            var entries = items(planned);
            h.assertTrue(entries.size() == 1 && ((ItemStack) accessor(entries.getFirst(), "stack")).getCount() == 3,
                    "duplicate partial requests exceeded one slot's available goods");
            player.getInventory().getItem(0).set(DataComponents.CUSTOM_NAME, Component.literal("Replacement"));
            h.assertTrue(items(method("planReservation").invoke(null, player, selected)).isEmpty(), "stale component selection was charged");
            h.assertTrue(player.getInventory().getItem(0).getCount() == 3, "preflight validation mutated inventory");
        } finally { h.getLevel().getServer().getPlayerList().remove(player); }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void failedJournalOrPlayerWritePreventsReservationCost(GameTestHelper h) throws Exception {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "M7DiskFailure");
        var store = TraderSaleReservationStore.get(h.getLevel());
        try {
            for (boolean blockJournal : List.of(true, false)) {
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.APPLE, 3));
                var selected = method("collectRequestedItems").invoke(null, player,
                        List.of(new SellItemsC2SPayload.ItemRequest("minecraft:apple", "apple", 2, null)));
                var planned = method("planReservation").invoke(null, player, selected);
                String key = "sale:m7-save-failure:" + UUID.randomUUID();
                Path path = blockJournal ? journal(h) : playerFile(h, player);
                try (var blocked = new BlockedFile(path)) {
                    var reserved = method("reserveItemsDurably").invoke(null, h.getLevel(), player, planned, key, "{}", "http://127.0.0.1:1");
                    h.assertTrue(items(reserved).isEmpty(), "failed write authorized reservation");
                    h.assertTrue(player.getInventory().getItem(0).getCount() == 3, "failed write consumed items");
                    h.assertTrue(!TraderSaleSettlementService.removed(player, key), "failed write left removal marker");
                    h.assertTrue(store.find(key) == null, "failed write left a dispatchable receipt");
                }
                store.flush(h.getLevel());
            }
        } finally { h.getLevel().getServer().getPlayerList().remove(player); }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fullInventoryDefersExactRefundWithoutPartialInsertion(GameTestHelper h) throws Exception {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "M7FullRefund");
        var store = TraderSaleReservationStore.get(h.getLevel());
        String key = "sale:m7-full:" + UUID.randomUUID();
        try {
            var output = new ItemStack(Items.APPLE, 4);
            output.set(DataComponents.CUSTOM_NAME, Component.literal("Exact refund"));
            for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            player.getInventory().setItem(0, output.copyWithCount(62));
            var receipt = receipt(h, player, key, output, TraderSaleReservationReceipt.Status.REFUND_PENDING);
            store.record(receipt);
            h.assertTrue(!TraderSaleSettlementService.deliver(h.getLevel(), player, receipt, false), "partial refund incorrectly delivered");
            h.assertTrue(player.getInventory().getItem(0).getCount() == 62 && store.find(key) != null, "full inventory partially inserted or erased receipt");
            player.getInventory().setItem(1, ItemStack.EMPTY);
            h.assertTrue(TraderSaleSettlementService.deliver(h.getLevel(), player, receipt, false), "refund did not resume with space");
            h.assertTrue(player.getInventory().getItem(0).getCount() == 64
                    && ItemStack.matches(player.getInventory().getItem(1), output.copyWithCount(2)), "refund lost exact components/count");
            h.assertTrue(!TraderSaleSettlementService.deliver(h.getLevel(), player, receipt, false), "stale callback paid again");
        } finally { store.resolve(key); store.flush(h.getLevel()); h.getLevel().getServer().getPlayerList().remove(player); }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void savedDeliveryMarkerSurvivesFailedResolutionAndPreventsDuplicate(GameTestHelper h) throws Exception {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "M7Marker");
        var store = TraderSaleReservationStore.get(h.getLevel());
        String key = "sale:m7-marker:" + UUID.randomUUID();
        try {
            player.getInventory().clearContent();
            var receipt = receipt(h, player, key, new ItemStack(Items.DIAMOND, 7), TraderSaleReservationReceipt.Status.REFUND_PENDING);
            store.record(receipt); store.flush(h.getLevel());
            try (var blocked = new BlockedFile(journal(h))) {
                h.assertTrue(!TraderSaleSettlementService.deliver(h.getLevel(), player, receipt, false), "resolution unexpectedly saved");
                h.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 7 && store.find(key) != null, "delivery not retained pending resolution");
                var saved = NbtIo.readCompressed(playerFile(h, player), NbtAccounter.unlimitedHeap());
                h.assertTrue(saved.getCompound("NeoForgeData").getCompound("BritanniaTraderSaleMarkers").getString(key).equals("delivered"),
                        "delivery marker not in the actual player file");
                TraderSaleSettlementService.deliver(h.getLevel(), player, receipt, false);
                h.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 7, "resolution retry duplicated goods");
            }
            TraderSaleSettlementService.deliver(h.getLevel(), player, receipt, false);
            h.assertTrue(store.find(key) == null && player.getInventory().countItem(Items.DIAMOND) == 7, "resolution failed after disk restored");
        } finally { store.resolve(key); store.flush(h.getLevel()); h.getLevel().getServer().getPlayerList().remove(player); }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unknownLegacyDispatchAndCorruptRefundStayVisible(GameTestHelper h) {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "M7Unknown");
        var store = TraderSaleReservationStore.get(h.getLevel());
        String unknown = "sale:m7-unknown:" + UUID.randomUUID(), corrupt = "sale:m7-corrupt:" + UUID.randomUUID();
        try {
            player.getInventory().clearContent();
            store.record(receipt(h, player, unknown, new ItemStack(Items.DIAMOND, 5), TraderSaleReservationReceipt.Status.DISPATCHED));
            store.record(new TraderSaleReservationReceipt(corrupt, player.getUUID(), List.of(new byte[]{0, 1}),
                    TraderSaleReservationReceipt.Status.REFUND_PENDING, System.currentTimeMillis()));
            TraderSaleReservationRecovery.refundStrandedReservations(h.getLevel(), player);
            h.assertTrue(store.find(unknown) != null && store.find(corrupt) != null, "unknown/corrupt evidence was erased");
            h.assertTrue(player.getInventory().isEmpty(), "unknown dispatch minted goods");
            var modern = new TraderSaleReservationReceipt("sale:m7-roundtrip", player.getUUID(),
                    List.of(BankItemCodec.serialize(new ItemStack(Items.APPLE), h.getLevel().registryAccess())),
                    TraderSaleReservationReceipt.Status.RECONCILING, 1, "{\"city\":\"Jhelom\"}", "http://127.0.0.1:1", "{}");
            var reloaded = TraderSaleReservationReceipt.fromTag(modern.toTag());
            h.assertTrue(reloaded.replayable() && reloaded.requestJson().equals(modern.requestJson())
                    && reloaded.status() == modern.status(), "recovery payload did not round-trip");
        } finally { store.resolve(unknown); store.resolve(corrupt); store.flush(h.getLevel()); h.getLevel().getServer().getPlayerList().remove(player); }
        h.succeed();
    }

    private static TraderSaleReservationReceipt receipt(GameTestHelper h, ServerPlayer player, String key, ItemStack stack, TraderSaleReservationReceipt.Status status) {
        return new TraderSaleReservationReceipt(key, player.getUUID(), List.of(BankItemCodec.serialize(stack, h.getLevel().registryAccess())), status, System.currentTimeMillis());
    }
    private static Method method(String name) {
        var method = Arrays.stream(ServerEconomyService.class.getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        method.setAccessible(true); return method;
    }
    private static Object accessor(Object record, String name) throws Exception {
        var method = record.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(record);
    }
    private static List<?> items(Object reservation) throws Exception { return (List<?>) accessor(reservation, "items"); }
    private static Path journal(GameTestHelper h) { return h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("data/britannia_trader_sale_reservations.dat"); }
    private static Path playerFile(GameTestHelper h, ServerPlayer player) { return h.getLevel().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getStringUUID()+".dat"); }

    /** Reversible synchronous fault injection into only the disposable GameTest world. */
    private static final class BlockedFile implements AutoCloseable {
        final Path target, backup;
        BlockedFile(Path target) throws Exception {
            this.target = target;
            backup = target.resolveSibling(target.getFileName() + ".m7-test-backup-" + UUID.randomUUID());
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) Files.move(target, backup);
            Files.createDirectory(target);
        }
        public void close() throws Exception {
            Files.delete(target); // Empty directory created above; never recursive.
            if (Files.exists(backup)) Files.move(backup, target);
        }
    }
}
