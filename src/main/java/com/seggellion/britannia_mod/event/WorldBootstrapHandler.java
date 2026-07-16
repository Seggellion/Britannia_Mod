package com.seggellion.britannia_mod.event;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.city.BootstrapCityRegistryCache;
import com.seggellion.britannia_mod.city.BootstrapCityRegistrySnapshot;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.client.RegionCache;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.network.payload.ClientboundSyncQuestsPayload;
import com.seggellion.britannia_mod.player.PlayerData;
import com.seggellion.britannia_mod.player.PlayerDataStore;
import com.seggellion.britannia_mod.quest.QuestCleanupService;
import com.seggellion.britannia_mod.quest.ServerQuestTable;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.sync.WorldBootstrapAPI;
import com.seggellion.britannia_mod.util.FishCatalog;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorldBootstrapHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer, Coordinator> COORDINATORS = new ConcurrentHashMap<>();

    private WorldBootstrapHandler() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(WorldBootstrapHandler::onLogin);
        NeoForge.EVENT_BUS.addListener(WorldBootstrapHandler::onLogout);
    }

    private static void onLogin(PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        COORDINATORS.computeIfAbsent(player.server, Coordinator::new).submit(player);
    }

    private static void onLogout(PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Coordinator coordinator = COORDINATORS.get(player.server);
        if (coordinator != null) coordinator.invalidate(player.getUUID());
    }

    public static void onServerStopping(MinecraftServer server) {
        Coordinator coordinator = COORDINATORS.remove(server);
        if (coordinator != null) coordinator.close();
        ServerHttpExecutor.shutdown(server);
        BootstrapCityRegistryCache.clear();
        ServiceNpcRegistryCache.clear();
        FishCatalog.clear();
    }

    private static void apply(ServerPlayer player, WorldBootstrapAPI.WorldBootstrapData data) {
        ServiceNpcRegistryCache.replace(data.serviceNpcRegistry());
        try {
            BootstrapCityRegistryCache.replace(BootstrapCityRegistrySnapshot.available(
                data.cities().stream()
                    .map(city -> new BootstrapCityDefinition(UUID.fromString(city.publicId()), city.name()))
                    .toList()
            ));
        } catch (RuntimeException exception) {
            BootstrapCityRegistryCache.clear();
            LOGGER.error("Rejected invalid bootstrap city registry; Service NPC spawn configuration is unavailable", exception);
        }

        FishCatalog.clear();
        data.fish().forEach(FishCatalog::put);
        RegionCache.update(data.regions());
        GrapeVarietyManager.loadFromBootstrap(data.grapes());

        if (data.shardUser() != null) {
            PlayerData playerData = PlayerDataStore.get(player);
            playerData.setPlayerName(player.getGameProfile().getName());
            playerData.syncFromShardUser(data.shardUser());
            PlayerDataStore.save(player, playerData);
        }

        if (!data.cities().isEmpty()) {
            CityManager manager = CityManager.get(player.serverLevel());
            for (WorldBootstrapAPI.CityBootstrapData cityData : data.cities()) {
                var city = manager.getCity(cityData.name());
                if (city == null) continue;
                CityInventory inventory = city.getInventory();
                inventory.updateSupplies(cityData.food(), cityData.wood(), cityData.metal(), cityData.stone(),
                    cityData.textile(), cityData.alcohol(), cityData.tech());
                inventory.updateTreasury(cityData.gold(), cityData.silver(), cityData.copper());
                inventory.clearAllCommodities();
                cityData.quantities().forEach((category, subs) -> subs.forEach((subcategory, items) ->
                    items.forEach((item, quantity) -> inventory.addCommodity(category, subcategory, item, quantity))));
                cityData.weights().forEach((category, subs) -> subs.forEach((subcategory, items) ->
                    items.forEach((item, weight) -> inventory.addCommodityWeight(category, subcategory, item, weight))));
            }
            manager.setDirty();
        }

        ServerQuestTable.replaceFromBootstrap(player, data.acceptedQuests());
        QuestCleanupService.cleanupStaleLocalQuestState(player, data.acceptedQuests());
        ClientboundSyncQuestsPayload.send(player, ServerQuestTable.snapshot(player));
        LOGGER.info("World bootstrap applied: {} fish, {} regions, {} cities",
            data.fish().size(), data.regions().size(), data.cities().size());
    }

    private static final class Coordinator implements AutoCloseable {
        private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();
        private final MinecraftServer server;
        private final ThreadPoolExecutor executor;
        private final BootstrapGenerationTracker generations = new BootstrapGenerationTracker();
        private final BootstrapRequestTracker inFlight = new BootstrapRequestTracker();

        private Coordinator(MinecraftServer server) {
            this.server = server;
            ThreadFactory factory = runnable -> {
                Thread thread = new Thread(runnable, "britannia-bootstrap-" + THREAD_SEQUENCE.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            };
            this.executor = new ThreadPoolExecutor(
                1, 2, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), factory,
                new ThreadPoolExecutor.AbortPolicy()
            );
        }

        private void submit(ServerPlayer player) {
            UUID playerId = player.getUUID();
            long generation = generations.next(playerId);
            if (generation < 0L) return;
            WorldBootstrapAPI.RequestHandle requestHandle = inFlight.register(playerId);
            try {
                CompletableFuture.supplyAsync(() -> WorldBootstrapAPI.fetch(player, requestHandle), executor)
                    .orTimeout(BoundedHttp.OVERALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .whenComplete((data, failure) -> {
                        if (failure != null) requestHandle.cancel();
                        inFlight.complete(playerId, requestHandle);
                        server.execute(() -> complete(player, generation, data, failure));
                    });
            } catch (RejectedExecutionException rejected) {
                inFlight.complete(playerId, requestHandle);
                requestHandle.cancel();
                ServerAuthRegistry.recordBootstrapResult(server, "queue_rejected");
                LOGGER.warn("World bootstrap queue is full; rejecting login bootstrap for {}", player.getGameProfile().getName());
            }
        }

        private void complete(ServerPlayer player, long generation, WorldBootstrapAPI.WorldBootstrapData data, Throwable failure) {
            UUID playerId = player.getUUID();
            if (!generations.isCurrent(playerId, generation)) {
                LOGGER.debug("World bootstrap ignored code=stale_generation");
                return;
            }
            if (server.getPlayerList().getPlayer(playerId) != player) {
                LOGGER.debug("World bootstrap ignored code=cancelled_player_session");
                return;
            }
            if (failure != null || data == null || !data.successful()) {
                String failureCode = classifyFailure(data, failure);
                ServerAuthRegistry.recordBootstrapResult(server, failureCode);
                LOGGER.warn("World bootstrap did not complete code={} player={}",
                    failureCode, player.getGameProfile().getName());
                return;
            }
            apply(player, data);
            ServerAuthRegistry.recordBootstrapResult(server, "success");
        }

        private void invalidate(UUID playerId) {
            generations.invalidate(playerId);
            inFlight.cancel(playerId);
        }

        @Override
        public void close() {
            generations.close();
            inFlight.close();
            executor.shutdownNow();
        }

        private static String classifyFailure(WorldBootstrapAPI.WorldBootstrapData data, Throwable failure) {
            Throwable cause = failure;
            while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof TimeoutException) return "overall_timeout";
            if (cause != null) return "request_error";
            if (data != null && data.failureCode() != null) return data.failureCode();
            return "fetch_failed";
        }
    }
}
