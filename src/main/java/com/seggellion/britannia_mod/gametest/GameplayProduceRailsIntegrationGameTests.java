package com.seggellion.britannia_mod.gametest;

import com.google.gson.*;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.economy.*;
import com.seggellion.britannia_mod.entity.CitizenEntity;
import com.seggellion.britannia_mod.farming.*;
import com.seggellion.britannia_mod.network.payload.SellItemsC2SPayload;
import com.seggellion.britannia_mod.registry.*;
import com.seggellion.britannia_mod.server.auth.*;
import com.seggellion.britannia_mod.service.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;

/**
 * Opt-in real Rails integration, never a fake passing test when Rails is absent.
 * Normal source sets; excluded from release JAR with the other GameTests.
 * BRITANNIA_M7_INTEGRATION_CONFIG must point at the guarded Rails fixture output.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GameplayProduceRailsIntegrationGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> localRails() {
        String config = System.getenv("BRITANNIA_M7_INTEGRATION_CONFIG");
        if (config == null || config.isBlank()) return List.of();
        return List.of(new TestFunction("m7_live_rails", "britannia_mod.m7_live_produce",
                "britannia_mod:service_npc_spawn_test_empty", 20000, 0, true, h -> {
                    try { run(h, Path.of(config)); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }));
    }

    private static void run(GameTestHelper h, Path configPath) throws Exception {
        var cfg = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
        URI origin = URI.create(cfg.get("origin").getAsString());
        if (!origin.equals(URI.create("http://127.0.0.1:3118"))) throw new IllegalArgumentException("Local test Rails only");
        var proxy = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        var gate = new CountDownLatch(1);
        var lostResponseGate = new CountDownLatch(1);
        var committedLostResponse = new AtomicBoolean();
        var receiptQueries = new AtomicInteger();
        var reconnect = new AtomicReference<ServerPlayer>();
        var secondPayload = new AtomicReference<String>();
        var requestCount = new AtomicInteger();
        var saleBody = new AtomicReference<String>();
        var saleReply = new AtomicReference<JsonObject>();
        var quoteReply = new AtomicReference<JsonObject>();
        var client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
        proxy.createContext("/", exchange -> {
            try {
                byte[] bytes = exchange.getRequestBody().readAllBytes();
                boolean sale = exchange.getRequestURI().getPath().equals("/api/trader_transactions");
                if (sale) {
                    saleBody.set(new String(bytes, StandardCharsets.UTF_8));
                    if (requestCount.incrementAndGet() == 1 && !gate.await(10, TimeUnit.SECONDS))
                        throw new IllegalStateException("reservation inspection timed out");
                }
                var request = HttpRequest.newBuilder(origin.resolve(exchange.getRequestURI()))
                        .timeout(java.time.Duration.ofSeconds(10));
                exchange.getRequestHeaders().forEach((key, values) -> {
                    if (!Set.of("host", "content-length", "connection", "upgrade", "expect", "http2-settings").contains(key.toLowerCase(Locale.ROOT)))
                        for (String value : values) request.header(key, value);
                });
                var response = client.send(request.method(exchange.getRequestMethod(),
                        HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.ofByteArray());
                JsonObject parsed = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (sale) {
                    saleReply.set(parsed);
                    if (requestCount.get() == 3) {
                        secondPayload.set(new String(bytes, StandardCharsets.UTF_8));
                        if (response.statusCode() != 200) throw new IllegalStateException("third sale did not commit");
                        committedLostResponse.set(true);
                        lostResponseGate.await(10, TimeUnit.SECONDS);
                        // Rails committed, but its successful response is lost at the transport boundary.
                        exchange.sendResponseHeaders(502, -1);
                        return;
                    }
                }
                if (exchange.getRequestURI().getPath().equals("/api/trader_sale_receipt")) receiptQueries.incrementAndGet();
                if (exchange.getRequestURI().getPath().equals("/api/economic_buyback_catalog")) quoteReply.set(parsed);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(response.statusCode(), response.body().length);
                exchange.getResponseBody().write(response.body());
            } catch (Exception failure) {
                byte[] body = ("{\"error\":\"local_integration_transport_failure\"}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(502, body.length); exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        proxy.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        proxy.start();
        var server = h.getLevel().getServer();
        var oldAuth = ServerAuthRegistry.credentials(server);
        var oldRegistry = EconomicNpcRegistryCache.snapshot();
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "M7RailsGardener");
        var trader = EntityRegistry.PRODUCE_TRADER.get().create(h.getLevel());
        trader.setPos(h.absolutePos(new net.minecraft.core.BlockPos(3, 2, 3)).getCenter());
        player.setPos(trader.position());
        trader.setNoAi(true); trader.setNoGravity(true);
        trader.setWorldNpcPublicId(UUID.fromString(cfg.get("world_npc").getAsString()));
        String type = cfg.get("npc_type").getAsString();
        trader.setEconomicNpcTypeKey(type); trader.setCityName("Jhelom");
        h.getLevel().addFreshEntity(trader);
        var policy = new AcceptedCommodityPolicy(List.of(new AcceptedCommodityPolicy.Entry("produce", null, null)));
        EconomicNpcRegistryCache.replace(new EconomicNpcRegistrySnapshot(1, "m7-local",
                Map.of(type, new EconomicNpcTypeDefinition(type, "Produce Trader", "trader", "farmer",
                        "britannia_mod:produce_trader", true, true, 1, policy))));
        var credentialConstructor = ServerCredentials.class.getDeclaredConstructor(String.class, String.class,
                URI.class, ServerCredentials.Source.class, boolean.class, boolean.class, String.class);
        credentialConstructor.setAccessible(true);
        ServerAuthRegistry.installForGameTesting(server, credentialConstructor.newInstance(
                cfg.get("shard").getAsString(), cfg.get("secret").getAsString(),
                URI.create("http://127.0.0.1:" + proxy.getAddress().getPort()),
                ServerCredentials.Source.SERVER_FILE, false, false, cfg.get("server").getAsString()));
        var crop = CropRegistry.all().stream().filter(c -> c.id().equals("broccoli")).findFirst().orElseThrow();
        var harvest = new ItemStack(crop.harvestItem().get(), 3);
        CropQualityCalculator.applyQuality(harvest, crop, 83);
        player.getInventory().clearContent(); player.getInventory().setItem(0, harvest.copy());
        var prepare = EconomicBuybackCatalogService.class.getDeclaredMethod("prepare", ServerPlayer.class, CitizenEntity.class);
        prepare.setAccessible(true); Object prepared = prepare.invoke(null, player, trader);
        var fetch = Arrays.stream(EconomicBuybackCatalogService.class.getDeclaredMethods()).filter(m -> m.getName().equals("fetch")).findFirst().orElseThrow();
        fetch.setAccessible(true);
        var quote = CompletableFuture.supplyAsync(() -> {
            try { return (EconomicBuybackCatalogService.Quote) fetch.invoke(null, player, prepared); }
            catch (Exception e) { throw new CompletionException(e); }
        });
        var cleaned = new AtomicBoolean();
        Runnable cleanup = () -> {
            if (!cleaned.compareAndSet(false, true)) return;
            gate.countDown(); lostResponseGate.countDown(); proxy.stop(0); trader.discard();
            if (reconnect.get() != null) server.getPlayerList().remove(reconnect.get());
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player);
            EconomicNpcRegistryCache.replace(oldRegistry);
            if (oldAuth.isPresent()) ServerAuthRegistry.installForGameTesting(server, oldAuth.get());
            else ServerAuthRegistry.clear(server);
        };
        h.runAtTickTime(19999, cleanup);
        var quotedAmount = new AtomicInteger();
        var firstPayload = new AtomicReference<String>();
        var replayFuture = new AtomicReference<CompletableFuture<HttpResponse<String>>>();
        h.startSequence()
            .thenWaitUntil(() -> {
                h.assertTrue(quote.isDone(), "waiting for real Rails quote");
                h.assertTrue(!quote.isCompletedExceptionally(), "real Rails quote failed: " + quote);
                h.assertTrue(quote.join().products().size() == 1, "broccoli absent from real mod quote: " + quoteReply.get());
                quotedAmount.set(quoteReply.get().getAsJsonObject("payout").get("amount").getAsInt());
            })
            .thenExecute(() -> ServerEconomyService.sellRequestedItems(player, request(trader.getId(), 3)))
            .thenWaitUntil(() -> {
                h.assertTrue(requestCount.get() == 1, "waiting for reserved sale HTTP");
                var receipt = TraderSaleReservationStore.get(h.getLevel()).forPlayer(player.getUUID()).getFirst();
                h.assertTrue(receipt.status() == TraderSaleReservationReceipt.Status.DISPATCHED, "not durably dispatched");
                h.assertTrue(ItemStack.matches(harvest, receipt.decodeItems(h.getLevel().registryAccess()).getFirst()), "reserved components/count changed");
                h.assertTrue(count(player, crop.harvestItem().get()) == 0, "goods not reserved");
                firstPayload.set(saleBody.get()); gate.countDown();
            })
            .thenWaitUntil(() -> {
                h.assertTrue(count(player, ItemRegistry.COPPER_COIN.get()) == quotedAmount.get(), "waiting for exact quoted currency: " + saleReply.get());
                h.assertTrue(TraderSaleReservationStore.get(h.getLevel()).forPlayer(player.getUUID()).isEmpty(), "receipt unresolved");
            })
            .thenExecute(() -> {
                replayFuture.set(client.sendAsync(HttpRequest.newBuilder(origin.resolve("/api/trader_transactions"))
                        .header("Shard-Name", cfg.get("shard").getAsString()).header("Shard-Secret", cfg.get("secret").getAsString())
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(firstPayload.get())).build(),
                        HttpResponse.BodyHandlers.ofString()));
            })
            .thenWaitUntil(() -> {
                h.assertTrue(replayFuture.get().isDone(), "waiting for Rails replay");
                var reply = JsonParser.parseString(replayFuture.get().join().body()).getAsJsonObject();
                h.assertTrue(reply.has("idempotent_replay") && reply.get("idempotent_replay").getAsBoolean(), "same-key retry was not replay: " + reply);
                h.assertTrue(count(player, ItemRegistry.COPPER_COIN.get()) == quotedAmount.get(), "replay paid the player twice");
            })
            .thenExecute(() -> {
                player.getInventory().add(harvest.copy());
                ServerEconomyService.sellRequestedItems(player, request(trader.getId(), 3));
            })
            .thenWaitUntil(() -> {
                h.assertTrue(requestCount.get() == 2 && TraderSaleReservationStore.get(h.getLevel()).forPlayer(player.getUUID()).isEmpty(), "waiting for stock-cap rejection/refund");
                h.assertTrue(player.getInventory().items.stream().anyMatch(s -> ItemStack.matches(harvest, s)), "failed sale lost exact harvest");
                h.assertTrue(count(player, ItemRegistry.COPPER_COIN.get()) == quotedAmount.get(), "failed sale paid currency");
            })
            .thenExecute(() -> {
                // Disconnect during the async preflight must cancel before reservation/HTTP.
                ServerEconomyService.sellRequestedItems(player, request(trader.getId(), 2));
                server.getPlayerList().remove(player);
            })
            .thenExecuteAfter(10, () -> {
                h.assertTrue(requestCount.get() == 2, "disconnected preflight dispatched a sale");
                h.assertTrue(TraderSaleReservationStore.get(h.getLevel()).forPlayer(player.getUUID()).isEmpty(), "disconnected preflight left reservation");
            })
            .thenExecute(() -> {
                // Rejoin the saved player, then lose the next response after Rails has committed.
                reconnect.set(rejoin(h, player));
                reconnect.get().setPos(trader.position());
                ServerEconomyService.sellRequestedItems(reconnect.get(), request(trader.getId(), 2));
            })
            .thenWaitUntil(() -> h.assertTrue(committedLostResponse.get(), "waiting for Rails commit before transport loss"))
            .thenExecute(() -> {
                server.getPlayerList().remove(reconnect.get());
                reconnect.set(null);
                lostResponseGate.countDown();
            })
            .thenWaitUntil(() -> {
                var records = TraderSaleReservationStore.get(h.getLevel()).forPlayer(player.getUUID());
                h.assertTrue(records.size() == 1 && records.getFirst().status() == TraderSaleReservationReceipt.Status.RECONCILING,
                        "lost response must stay pending, without a speculative refund");
                h.assertTrue(count(player, ItemRegistry.COPPER_COIN.get()) == quotedAmount.get(), "offline original object received payment");
            })
            .thenExecute(() -> reconnect.set(rejoin(h, player)))
            .thenWaitUntil(() -> {
                h.assertTrue(receiptQueries.get() >= 1, "recovery did not query real Rails receipt");
                h.assertTrue(count(reconnect.get(), ItemRegistry.COPPER_COIN.get()) == quotedAmount.get() + 5,
                        "waiting for one recovered payment on the current player");
                h.assertTrue(count(reconnect.get(), crop.harvestItem().get()) == 1, "committed goods were refunded");
                h.assertTrue(TraderSaleReservationStore.get(h.getLevel()).forPlayer(player.getUUID()).isEmpty(), "recovered receipt not resolved");
            })
            .thenExecute(() -> TraderSaleReservationRecovery.refundStrandedReservations(h.getLevel(), reconnect.get()))
            .thenExecuteAfter(10, () -> {
                h.assertTrue(count(reconnect.get(), ItemRegistry.COPPER_COIN.get()) == quotedAmount.get() + 5, "repeated recovery duplicated currency");
                h.assertTrue(requestCount.get() == 3, "committed receipt unnecessarily reposted");
                try {
                    var evidence = new JsonObject();
                    evidence.addProperty("payout_copper", quotedAmount.get());
                    evidence.addProperty("recovered_copper", 5);
                    evidence.addProperty("first_sale", firstPayload.get());
                    evidence.addProperty("second_sale", secondPayload.get());
                    evidence.addProperty("sale_requests", requestCount.get());
                    evidence.addProperty("receipt_queries", receiptQueries.get());
                    evidence.addProperty("disconnect_scope", "preflight cancellation; response lost after real Rails commit, offline resolution, same-UUID saved player reconnect, one payout");
                    Files.writeString(configPath.resolveSibling("m7-live-mod-evidence.json"), evidence.toString());
                } catch (Exception e) { throw new RuntimeException(e); }
                cleanup.run();
            })
            .thenSucceed();
    }

    private static ServerPlayer rejoin(GameTestHelper h, ServerPlayer prior) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(prior.getGameProfile(), false);
        var joined = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, joined, cookie);
        joined.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return joined;
    }

    private static SellItemsC2SPayload request(int trader, int quantity) {
        return new SellItemsC2SPayload("Jhelom", "produce", trader,
                List.of(new SellItemsC2SPayload.ItemRequest("britannia_mod:broccoli", "broccoli", quantity, null)));
    }
    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().items.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
