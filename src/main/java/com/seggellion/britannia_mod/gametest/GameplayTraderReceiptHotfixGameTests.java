package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.item.BankItemCodec;
import com.seggellion.britannia_mod.economy.TraderSaleReservationReceipt;
import com.seggellion.britannia_mod.economy.TraderSaleReservationStore;
import com.seggellion.britannia_mod.economy.TraderSaleSettlementService;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** End-to-end settlement regressions for the Patch 18 nested receipt hotfix. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GameplayTraderReceiptHotfixGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String SHARD = "patch18-receipt-hotfix";

    private GameplayTraderReceiptHotfixGameTests() {}

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "patch18_receipt_immediate")
    public static void immediateNestedProductionResponsePaysOnceAndResolves(GameTestHelper h) throws Exception {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "Patch18NestedReceipt");
        player.getInventory().clearContent();
        String key = "sale:patch18:immediate:" + UUID.randomUUID();
        var posts = new AtomicInteger();
        var lookups = new AtomicInteger();
        var arrived = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var fixture = new ReceiptHttpFixture(request -> {
            if (request.path().equals("/api/trader_transactions")) {
                posts.incrementAndGet();
                arrived.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) return new Reply(504, "{}");
                return new Reply(200, productionResponse(key, player.getUUID()).toString());
            }
            if (request.path().equals("/api/trader_sale_receipt")) lookups.incrementAndGet();
            return new Reply(404, "{}");
        });
        Optional<ServerCredentials> priorAuth = installAuth(h, fixture.origin());
        var store = TraderSaleReservationStore.get(h.getLevel());
        store.record(receipt(h, player, key, TraderSaleReservationReceipt.Status.ITEMS_REMOVED,
                fixture.origin(), ""));
        h.assertTrue(store.flush(h.getLevel()), "fixture receipt was not durable before dispatch");
        var cleaned = new AtomicBoolean();
        Runnable cleanup = cleanup(h, player, key, fixture, priorAuth, cleaned, null);
        h.runAtTickTime(399, cleanup);

        TraderSaleSettlementService.dispatch(h.getLevel(), key);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(arrived.getCount() == 0, "waiting for fixture POST");
                    var live = store.find(key);
                    h.assertTrue(live != null && live.status() == TraderSaleReservationReceipt.Status.DISPATCHED,
                            "receipt was not durably DISPATCHED before the HTTP response");
                })
                .thenExecute(release::countDown)
                .thenWaitUntil(() -> {
                    h.assertTrue(count(player, ItemRegistry.COPPER_COIN.get()) == 3,
                            "exact nested transaction 1474 fixture did not deliver three copper");
                    h.assertTrue(store.find(key) == null, "paid receipt did not resolve");
                })
                .thenExecute(() -> {
                    h.assertTrue(posts.get() == 1 && lookups.get() == 0,
                            "immediate success used the wrong endpoint or posted twice");
                    h.assertTrue(count(player, Items.CARROT) == 0, "confirmed sale refunded its carrot");
                    h.assertTrue(!markerSaved(h, player, key), "resolved receipt left a delivery marker on disk");
                    TraderSaleSettlementService.dispatch(h.getLevel(), key);
                    TraderSaleSettlementService.recover(h.getLevel(), player);
                    TraderSaleSettlementService.recover(h.getLevel(), player);
                })
                .thenExecuteAfter(10, () -> {
                    h.assertTrue(count(player, ItemRegistry.COPPER_COIN.get()) == 3,
                            "stale callback/login recovery duplicated the payout");
                    h.assertTrue(posts.get() == 1, "resolved receipt was reposted");
                    cleanup.run();
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "patch18_receipt_lookup")
    public static void existingReconcilingReceiptUsesNestedLookupWithoutReposting(GameTestHelper h) throws Exception {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "Patch18LookupReceipt");
        player.getInventory().clearContent();
        String key = "sale:patch18:reconciling:" + UUID.randomUUID();
        var posts = new AtomicInteger();
        var lookups = new AtomicInteger();
        var fixture = new ReceiptHttpFixture(request -> {
            if (request.path().equals("/api/trader_transactions")) {
                posts.incrementAndGet();
                return new Reply(500, "{}");
            }
            if (request.path().equals("/api/trader_sale_receipt")) {
                lookups.incrementAndGet();
                JsonObject body = new JsonObject();
                body.addProperty("found", true);
                body.add("receipt", productionResponse(key, player.getUUID()));
                return new Reply(200, body.toString());
            }
            return new Reply(404, "{}");
        });
        Optional<ServerCredentials> priorAuth = installAuth(h, fixture.origin());
        var store = TraderSaleReservationStore.get(h.getLevel());
        store.record(receipt(h, player, key, TraderSaleReservationReceipt.Status.RECONCILING,
                fixture.origin(), ""));
        h.assertTrue(store.flush(h.getLevel()), "RECONCILING fixture was not durable");
        var cleaned = new AtomicBoolean();
        Runnable cleanup = cleanup(h, player, key, fixture, priorAuth, cleaned, null);
        h.runAtTickTime(399, cleanup);

        TraderSaleSettlementService.recover(h.getLevel(), player);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(lookups.get() >= 1, "existing receipt did not use authoritative lookup");
                    h.assertTrue(count(player, ItemRegistry.COPPER_COIN.get()) == 3,
                            "nested lookup did not pay the current same-UUID player");
                    h.assertTrue(store.find(key) == null, "recovered receipt did not resolve");
                })
                .thenExecute(() -> {
                    h.assertTrue(posts.get() == 0, "committed receipt was reposted instead of using lookup");
                    h.assertTrue(count(player, Items.CARROT) == 0, "committed lookup refunded sold goods");
                    TraderSaleSettlementService.recover(h.getLevel(), player);
                    TraderSaleSettlementService.recover(h.getLevel(), player);
                })
                .thenExecuteAfter(10, () -> {
                    h.assertTrue(count(player, ItemRegistry.COPPER_COIN.get()) == 3,
                            "repeated recovery duplicated lookup payout");
                    h.assertTrue(posts.get() == 0, "repeated recovery reposted the sale");
                    cleanup.run();
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "patch18_receipt_refusal")
    public static void definitiveInitialRefusalReturnsExactStackAndNeverPays(GameTestHelper h) throws Exception {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "Patch18Refusal");
        player.getInventory().clearContent();
        String key = "sale:patch18:refusal:" + UUID.randomUUID();
        var posts = new AtomicInteger();
        var fixture = new ReceiptHttpFixture(request -> {
            if (request.path().equals("/api/trader_transactions")) {
                posts.incrementAndGet();
                return new Reply(422, "{\"success\":false,\"error\":{\"code\":\"stock_cap\"}}");
            }
            return new Reply(404, "{}");
        });
        Optional<ServerCredentials> priorAuth = installAuth(h, fixture.origin());
        ItemStack exact = new ItemStack(Items.CARROT, 2);
        exact.set(DataComponents.CUSTOM_NAME, Component.literal("Reserved carrots"));
        var store = TraderSaleReservationStore.get(h.getLevel());
        store.record(receipt(h, player, key, TraderSaleReservationReceipt.Status.ITEMS_REMOVED,
                fixture.origin(), "", exact));
        h.assertTrue(store.flush(h.getLevel()), "refusal fixture was not durable");
        var cleaned = new AtomicBoolean();
        Runnable cleanup = cleanup(h, player, key, fixture, priorAuth, cleaned, null);
        h.runAtTickTime(399, cleanup);

        TraderSaleSettlementService.dispatch(h.getLevel(), key);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(store.find(key) == null, "definitive 4xx refund did not resolve");
                    h.assertTrue(player.getInventory().items.stream().anyMatch(stack -> ItemStack.matches(stack, exact)),
                            "definitive 4xx did not return the exact reserved stack");
                })
                .thenExecute(() -> {
                    h.assertTrue(posts.get() == 1, "definitive refusal posted more than once");
                    h.assertTrue(count(player, ItemRegistry.COPPER_COIN.get()) == 0,
                            "definitive refusal also paid currency");
                    cleanup.run();
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "patch18_receipt_wrong_owner")
    public static void mismatchedNestedOwnershipRemainsReconcilingWithoutRefund(GameTestHelper h) throws Exception {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "Patch18WrongOwner");
        player.getInventory().clearContent();
        String key = "sale:patch18:wrong-owner:" + UUID.randomUUID();
        var posts = new AtomicInteger();
        var fixture = new ReceiptHttpFixture(request -> {
            if (request.path().equals("/api/trader_transactions")) {
                posts.incrementAndGet();
                return new Reply(200, productionResponse(key, UUID.randomUUID()).toString());
            }
            return new Reply(404, "{}");
        });
        Optional<ServerCredentials> priorAuth = installAuth(h, fixture.origin());
        var store = TraderSaleReservationStore.get(h.getLevel());
        store.record(receipt(h, player, key, TraderSaleReservationReceipt.Status.ITEMS_REMOVED,
                fixture.origin(), ""));
        h.assertTrue(store.flush(h.getLevel()), "wrong-owner fixture was not durable");
        var cleaned = new AtomicBoolean();
        Runnable cleanup = cleanup(h, player, key, fixture, priorAuth, cleaned, null);
        h.runAtTickTime(399, cleanup);

        TraderSaleSettlementService.dispatch(h.getLevel(), key);
        h.startSequence()
                .thenWaitUntil(() -> {
                    var live = store.find(key);
                    h.assertTrue(live != null && live.status() == TraderSaleReservationReceipt.Status.RECONCILING,
                            "wrong-owner receipt did not fail closed into RECONCILING");
                    h.assertTrue(posts.get() == 1, "wrong-owner fixture did not receive one POST");
                })
                .thenExecute(() -> {
                    h.assertTrue(count(player, ItemRegistry.COPPER_COIN.get()) == 0,
                            "wrong-owner receipt delivered currency");
                    h.assertTrue(count(player, Items.CARROT) == 0,
                            "ambiguous wrong-owner response speculatively refunded goods");
                    cleanup.run();
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "patch18_receipt_full_inventory")
    public static void confirmedFullInventoryPayoutIsAtomicAndMarkerPreventsDuplicate(GameTestHelper h) throws Exception {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "Patch18FullPayout");
        String key = "sale:patch18:full-payout:" + UUID.randomUUID();
        var store = TraderSaleReservationStore.get(h.getLevel());
        JsonObject response = productionResponse(key, player.getUUID());
        var receipt = receipt(h, player, key, TraderSaleReservationReceipt.Status.PAYOUT_PENDING,
                URI.create("http://127.0.0.1:1"), response.toString());
        store.record(receipt);
        h.assertTrue(store.flush(h.getLevel()), "payout fixture was not durable");
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        var blocker = new AtomicReference<BlockedFile>();
        var cleaned = new AtomicBoolean();
        Runnable cleanup = cleanup(h, player, key, null, Optional.empty(), cleaned, blocker);
        try {
            h.assertTrue(!TraderSaleSettlementService.deliver(h.getLevel(), player, receipt, false),
                    "full inventory accepted a partial payout");
            h.assertTrue(count(player, ItemRegistry.COPPER_COIN.get()) == 0 && store.find(key) != null,
                    "full inventory partially inserted coins or erased the receipt");

            player.getInventory().setItem(0, ItemStack.EMPTY);
            blocker.set(new BlockedFile(journal(h)));
            h.assertTrue(!TraderSaleSettlementService.deliver(h.getLevel(), player, receipt, false),
                    "blocked resolution unexpectedly completed");
            h.assertTrue(count(player, ItemRegistry.COPPER_COIN.get()) == 3 && store.find(key) != null,
                    "delivery marker path did not retain exactly one payout pending resolution");
            h.assertTrue(markerSaved(h, player, key), "delivery marker was not durably saved with the coins");

            BlockedFile blocked = blocker.get();
            blocked.close();
            blocker.compareAndSet(blocked, null);
            h.assertTrue(TraderSaleSettlementService.deliver(h.getLevel(), player, receipt, false),
                    "marker recovery did not resolve after journal storage returned");
            h.assertTrue(count(player, ItemRegistry.COPPER_COIN.get()) == 3 && store.find(key) == null,
                    "marker recovery duplicated coins or left the journal pending");
            h.assertTrue(!markerSaved(h, player, key), "marker did not clear after durable journal resolution");
            h.assertTrue(!TraderSaleSettlementService.deliver(h.getLevel(), player, receipt, false),
                    "stale callback delivered a second payout");
            TraderSaleSettlementService.recover(h.getLevel(), player);
            h.assertTrue(count(player, ItemRegistry.COPPER_COIN.get()) == 3,
                    "repeated login recovery duplicated the payout");
        } finally {
            cleanup.run();
        }
        h.succeed();
    }

    private static TraderSaleReservationReceipt receipt(GameTestHelper h, ServerPlayer player, String key,
            TraderSaleReservationReceipt.Status status, URI origin, String settlement) {
        return receipt(h, player, key, status, origin, settlement, new ItemStack(Items.CARROT));
    }

    private static TraderSaleReservationReceipt receipt(GameTestHelper h, ServerPlayer player, String key,
            TraderSaleReservationReceipt.Status status, URI origin, String settlement, ItemStack item) {
        JsonObject request = new JsonObject();
        request.addProperty("shard", SHARD);
        request.addProperty("idempotency_key", key);
        request.addProperty("player_uuid", player.getStringUUID());
        request.addProperty("city", "Jhelom");
        return new TraderSaleReservationReceipt(key, player.getUUID(),
                List.of(BankItemCodec.serialize(item, h.getLevel().registryAccess())), status,
                System.currentTimeMillis(), request.toString(), origin.toString(), settlement);
    }

    private static JsonObject productionResponse(String key, UUID player) {
        return JsonParser.parseString("""
                {
                  "success": true,
                  "transaction": {
                    "transaction_id": 1474,
                    "idempotency_key": "%s",
                    "player_uuid": "%s",
                    "transaction_type": "sell",
                    "currency_grant": {"copper": 3}
                  },
                  "transaction_id": 1474,
                  "idempotency_key": "%s",
                  "currency_grant": {"copper": 3}
                }
                """.formatted(key, player, key)).getAsJsonObject();
    }

    private static Optional<ServerCredentials> installAuth(GameTestHelper h, URI origin) throws Exception {
        var server = h.getLevel().getServer();
        Optional<ServerCredentials> previous = ServerAuthRegistry.credentials(server);
        Constructor<ServerCredentials> constructor = ServerCredentials.class.getDeclaredConstructor(
                String.class, String.class, URI.class, ServerCredentials.Source.class,
                boolean.class, boolean.class, String.class);
        constructor.setAccessible(true);
        ServerAuthRegistry.installForGameTesting(server, constructor.newInstance(
                SHARD, "fixture-secret", origin, ServerCredentials.Source.SERVER_FILE,
                false, false, "patch18-receipt-fixture"));
        return previous;
    }

    private static Runnable cleanup(GameTestHelper h, ServerPlayer player, String key,
            ReceiptHttpFixture fixture, Optional<ServerCredentials> priorAuth, AtomicBoolean cleaned,
            AtomicReference<BlockedFile> blocker) {
        return () -> {
            if (!cleaned.compareAndSet(false, true)) return;
            try {
                BlockedFile blocked = blocker == null ? null : blocker.getAndSet(null);
                if (blocked != null) blocked.close();
            } catch (Exception ignored) {}
            if (fixture != null) fixture.close();
            var store = TraderSaleReservationStore.get(h.getLevel());
            store.resolve(key);
            store.flush(h.getLevel());
            if (h.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
                h.getLevel().getServer().getPlayerList().remove(player);
            }
            if (fixture != null) {
                if (priorAuth.isPresent()) ServerAuthRegistry.installForGameTesting(h.getLevel().getServer(), priorAuth.get());
                else ServerAuthRegistry.clear(h.getLevel().getServer());
            }
        };
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static Path journal(GameTestHelper h) {
        return h.getLevel().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data/britannia_trader_sale_reservations.dat");
    }

    private static Path playerFile(GameTestHelper h, ServerPlayer player) {
        return h.getLevel().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(player.getStringUUID() + ".dat");
    }

    private static boolean markerSaved(GameTestHelper h, ServerPlayer player, String key) {
        try {
            var saved = NbtIo.readCompressed(playerFile(h, player), NbtAccounter.unlimitedHeap());
            return "delivered".equals(saved.getCompound("NeoForgeData")
                    .getCompound("BritanniaTraderSaleMarkers").getString(key));
        } catch (Exception unreadable) {
            throw new RuntimeException(unreadable);
        }
    }

    private record Request(String method, String path, byte[] body) {}
    private record Reply(int status, String body) {}
    @FunctionalInterface private interface Handler { Reply handle(Request request) throws Exception; }

    private static final class ReceiptHttpFixture implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        ReceiptHttpFixture(Handler handler) throws Exception {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            server.createContext("/", exchange -> serve(exchange, handler));
            server.setExecutor(executor);
            server.start();
        }

        URI origin() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }

        private static void serve(HttpExchange exchange, Handler handler) {
            try {
                Reply reply = handler.handle(new Request(exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(), exchange.getRequestBody().readAllBytes()));
                byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(reply.status(), body.length);
                exchange.getResponseBody().write(body);
            } catch (Exception failure) {
                try { exchange.sendResponseHeaders(500, -1); }
                catch (Exception ignored) {}
            } finally {
                exchange.close();
            }
        }

        @Override public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    /** Reversible synchronous fault injection into only the disposable GameTest world. */
    private static final class BlockedFile implements AutoCloseable {
        final Path target;
        final Path backup;

        BlockedFile(Path target) throws Exception {
            this.target = target;
            backup = target.resolveSibling(target.getFileName() + ".patch18-test-backup-" + UUID.randomUUID());
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) Files.move(target, backup);
            Files.createDirectory(target);
        }

        @Override public void close() throws Exception {
            Files.delete(target);
            if (Files.exists(backup)) Files.move(backup, target);
        }
    }
}
