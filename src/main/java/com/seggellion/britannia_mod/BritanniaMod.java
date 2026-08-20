// BritanniaMod.java
package com.seggellion.britannia_mod;

import com.seggellion.britannia_mod.registry.*;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.magic.ManaHandler;
import com.seggellion.britannia_mod.event.ForgeEventHandler;
import com.seggellion.britannia_mod.event.BlacksmithInteractionEvent;
import com.seggellion.britannia_mod.spawner.DaemonSpawner;
import com.seggellion.britannia_mod.skill.SkillManager;
import com.seggellion.britannia_mod.spawner.BritainCemetarySpawner;
import com.seggellion.britannia_mod.spawner.ShameDungeonSpawner;
import com.seggellion.britannia_mod.spawner.CitySpawner;
import com.seggellion.britannia_mod.event.ShadeEntitySizeHandler;
import com.seggellion.britannia_mod.event.BreakSpeedHandler;
import com.seggellion.britannia_mod.event.InventoryHandler;
import com.seggellion.britannia_mod.event.ChestHandler;
import com.seggellion.britannia_mod.event.GlobalEventHandler;
import com.seggellion.britannia_mod.event.WoodChopEventHandler;
import com.seggellion.britannia_mod.event.PlayerEventHandler;
import com.seggellion.britannia_mod.event.FlowerInteractionHandler;
import com.seggellion.britannia_mod.event.HouseFarmPlotInteractionHandler;
import com.seggellion.britannia_mod.event.ManagedVegetationInteractionHandler;
import com.seggellion.britannia_mod.event.TrainingDummyEventHandler;
import com.seggellion.britannia_mod.event.ParrotProtectionHandler;
import com.seggellion.britannia_mod.event.FishingEventHandler;
import com.seggellion.britannia_mod.event.TreeKarmaHandler;
import com.seggellion.britannia_mod.event.KarmaReductionHandler;
import com.seggellion.britannia_mod.quest.events.QuestEventHandlers;
import com.seggellion.britannia_mod.villager.BlacksmithPOIHandler;
import com.seggellion.britannia_mod.event.BlockRestoreHandler;
import com.seggellion.britannia_mod.event.CustomBlockBreakHandler;
import com.seggellion.britannia_mod.event.ToolInteractionHandler;
import com.seggellion.britannia_mod.event.CityGameModeHandler;
import com.seggellion.britannia_mod.event.LockpickingEventHandler;
//import com.seggellion.britannia_mod.villager.CustomVillagerProfessions;
import com.seggellion.britannia_mod.villager.BlacksmithProfessions;
import com.seggellion.britannia_mod.villager.VillagerTradeUpdater;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.features.MobSpawnControl;
import com.seggellion.britannia_mod.features.RestrictedEquipmentControl;
import com.seggellion.britannia_mod.block.MoongateTickHandler;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.structure.SurvivalZoneHandler;
import com.seggellion.britannia_mod.structure.StructureProtectionHandler;
import com.seggellion.britannia_mod.structure.lifecycle.ShrineIntegrityHandler;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.util.NameLoader;
import com.seggellion.britannia_mod.network.CityDataSync;
import com.seggellion.britannia_mod.network.DeedHttpServer;
import com.seggellion.britannia_mod.network.RailsUpdateServer;
import com.seggellion.britannia_mod.block.entity.BritanniaSpawnBlockEntity;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import com.seggellion.britannia_mod.util.OreVeinLoader;
import com.seggellion.britannia_mod.sync.BlessedItemSyncHandler;
import com.seggellion.britannia_mod.event.WorldBootstrapHandler;
import com.seggellion.britannia_mod.skill.crafting.CraftableRegistry;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnDeliveryProcessor;
import com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataReloadRegistration;
import com.seggellion.britannia_mod.registry.BannerBlockRegistry;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSync;
import com.seggellion.britannia_mod.banner.structure.BannerStructureIntegrityHandler;
import com.seggellion.britannia_mod.banner.placement.BannerOrientationPreferenceLifecycle;
import com.seggellion.britannia_mod.dye.preview.DyePreviewLifecycle;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationConfig;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationManager;
import com.seggellion.britannia_mod.wildresource.WildResourceManager;
import com.seggellion.britannia_mod.wildresource.WildResourceEntries;
import com.seggellion.britannia_mod.wildresource.WildResourceInteractionHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.ChunkPos;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;

import net.minecraft.core.registries.Registries;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.network.chat.Component;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.nio.file.Path;

@Mod(BritanniaMod.MODID)
public class BritanniaMod {
    public static final String MODID = "britannia_mod";
    private static final Logger LOGGER = LogUtils.getLogger();
    private int starvationNotificationTickCounter = 0; // Tick counter for starvation notifications
    private DeedHttpServer deedHttpServer;
    private RailsUpdateServer railsUpdateServer;

    public BritanniaMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing BritanniaMod");
        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                ManagedVegetationConfig.SPEC,
                "britannia-managed-vegetation.toml"
        );
        OreVeinLoader.loadOreVeins();
          BlessedItemSyncHandler.init();
        WorldBootstrapHandler.init();
        com.seggellion.britannia_mod.service.banking.BankTransferReconciliationService.init();
        GrapeVarietyManager.init();
CraftableRegistry.init();
        // Mining milestone 2: load + validate the Mining progression catalogue fail-fast.
        // Definitions only — no break-flow behavior is wired until milestone 3.
        com.seggellion.britannia_mod.mining.Mineables.init();
        // Register mod components
     //   FeatureRegistry.register(modEventBus);
        BlockRegistry.register(modEventBus);
        BlockEntityRegistry.register(modEventBus);
        GrabbyRegistry.register(modEventBus);
        BannerBlockRegistry.register(modEventBus);
        BannerStructureIntegrityHandler.register();
        BannerOrientationPreferenceLifecycle.register(NeoForge.EVENT_BUS);
        ItemRegistry.register(modEventBus);
        LargeStructureRegistry.register(modEventBus);
        ShrineIntegrityHandler.register();
        ManagedVegetationManager.register();
        WildResourceEntries.bootstrap();
        WildResourceManager.register();
        WildResourceInteractionHandler.register();

        BlacksmithItemRegistry.register(modEventBus);
        DyeItemRegistry.register(modEventBus);
        BannerItemRegistry.register(modEventBus);

        WeaponRegistry.register(modEventBus);
        FishRegistry.register(modEventBus);
        PaintingRegistry.register(modEventBus);
        MenuRegistry.register(modEventBus);

        ToolRegistry.register(modEventBus);
        com.seggellion.britannia_mod.economy.crafting.ModIngredients.register(modEventBus);
        EntityRegistry.register(modEventBus);
        BlacksmithProfessions.registerAll(modEventBus);
        DataComponentRegistry.register(modEventBus);
        // CommandRegistry.register(modEventBus);
        CreativeTabRegistry.register(modEventBus);

        SoundRegistry.register(modEventBus);
        ConfigRegistry.register();  // No longer passes modContainer
        ModAttributes.register(modEventBus); 

        modEventBus.addListener(this::registerEntityAttributes); 
        modEventBus.register(NetworkHandler.class);
        modEventBus.register(ModSpawnPlacementRegistry.class);

        ModSounds.register(modEventBus);
        CommandRegistry.register();
        BannerDataReloadRegistration.register(NeoForge.EVENT_BUS);
        BannerRenderDataSync.register(NeoForge.EVENT_BUS);
        com.seggellion.britannia_mod.bannerdyeing.registry.BannerRegistrySync.register(NeoForge.EVENT_BUS);
        DyePreviewLifecycle.register(NeoForge.EVENT_BUS);

        // Register event handlers
        MoongateTickHandler.registerTickEvent(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.register(new ForgeEventHandler());
        NeoForge.EVENT_BUS.register(new PlayerEventHandler());
        NeoForge.EVENT_BUS.register(new FlowerInteractionHandler());
        NeoForge.EVENT_BUS.register(new HouseFarmPlotInteractionHandler());
        NeoForge.EVENT_BUS.register(new ManagedVegetationInteractionHandler());
        NeoForge.EVENT_BUS.register(new TrainingDummyEventHandler());
        // UltimaCraft parrots are protected from all player-caused damage; see the handler.
        NeoForge.EVENT_BUS.register(new ParrotProtectionHandler());
        NeoForge.EVENT_BUS.register(new RestrictedEquipmentControl());
        NeoForge.EVENT_BUS.register(new MobSpawnControl());
        NeoForge.EVENT_BUS.register(new BlockRestoreHandler());

        FishingEventHandler fishingEventHandler = new FishingEventHandler();

        // Register the event handler method directly
        NeoForge.EVENT_BUS.addListener(fishingEventHandler::onItemFished);

        NeoForge.EVENT_BUS.register(DaemonSpawner.class);
        NeoForge.EVENT_BUS.register(VillagerTradeUpdater.class);
        NeoForge.EVENT_BUS.register(BritainCemetarySpawner.class);
        NeoForge.EVENT_BUS.register(ShameDungeonSpawner.class);
        NeoForge.EVENT_BUS.register(CitySpawner.class);
        NeoForge.EVENT_BUS.register(ShadeEntitySizeHandler.class);
        NeoForge.EVENT_BUS.register(GlobalEventHandler.class);
        NeoForge.EVENT_BUS.register(QuestEventHandlers.class);
        NeoForge.EVENT_BUS.register(WoodChopEventHandler.class);
        NeoForge.EVENT_BUS.register(new ToolInteractionHandler());
        NeoForge.EVENT_BUS.register(new CityGameModeHandler());
        // Mining milestone 3: HIGH-priority skill gate; must precede CustomBlockBreakHandler,
        // which mutates the world inside its NORMAL-priority listener.
        NeoForge.EVENT_BUS.register(new com.seggellion.britannia_mod.mining.MiningGateHandler());
        // Mining milestone 7: records player-placed mineables so the place-break loop is not a
        // Mining exploit and players can always dismantle their own construction.
        NeoForge.EVENT_BUS.register(new com.seggellion.britannia_mod.mining.MiningProvenanceHandler());
        // Housing clay supply: the managed deposit rule. HIGH priority for the same reason as
        // the Mining gate -- CustomBlockBreakHandler mutates the world inside its NORMAL listener.
        NeoForge.EVENT_BUS.register(
                new com.seggellion.britannia_mod.deposit.ManagedDepositInteractionHandler());
       NeoForge.EVENT_BUS.register(new CustomBlockBreakHandler());
        NeoForge.EVENT_BUS.register(new ChestHandler());
        NeoForge.EVENT_BUS.register(new LockpickingEventHandler());
        com.seggellion.britannia_mod.grabbyhands.GrabbyInteractionHandler.register();
        com.seggellion.britannia_mod.grabbyhands.destruction.GrabbyDestructionLifecycle.register();
     //   NeoForge.EVENT_BUS.register(new PopulationEventHandler());
       NeoForge.EVENT_BUS.register(new InventoryHandler());
        NeoForge.EVENT_BUS.register(new TreeKarmaHandler());
        NeoForge.EVENT_BUS.register(new KarmaReductionHandler());
        NeoForge.EVENT_BUS.register(new BlacksmithPOIHandler());
        NeoForge.EVENT_BUS.register(new SurvivalZoneHandler());
          NeoForge.EVENT_BUS.register(new StructureProtectionHandler());
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        com.seggellion.britannia_mod.economy.TraderSaleReservationRecovery.register();
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);


        ManaHandler.register();
          SkillManager.init(); 

       
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        EntityRegistry.registerAttributes(event);
    }

    public static CityInventory getCityInventory(ServerLevel serverLevel, String cityName) {
        CityManager cityManager = CityManager.get(serverLevel);
        City city = cityManager.getCity(cityName);
        if (city == null) {
            cityManager.addCity(cityName);
            city = cityManager.getCity(cityName);
        }
        return city.getInventory();
    }

    public static void associateNpcToCity(ServerLevel serverLevel, String cityName, Entity merchant) {
        CityInventory cityInventory = getCityInventory(serverLevel, cityName);
        cityInventory.associateNpc(merchant);
    }

    public static List<UUID> getCityMerchants(ServerLevel serverLevel, String cityName) {
        CityManager cityManager = CityManager.get(serverLevel);
        City city = cityManager.getCity(cityName);
        if (city != null) {
            return city.getInventory().getAssociatedNpcs();
        }
        return Collections.emptyList();
    }

    private void notifyNearbyPlayers(ServerLevel serverLevel, String cityName) {
        // This method can be removed or repurposed since notifications are handled in the tick handler
    }

public void onServerStopping(ServerStoppingEvent event) {
    if (deedHttpServer != null) {
        deedHttpServer.stop();
        LOGGER.info("🛑 DeedHttpServer stopped");
    }
    if (railsUpdateServer != null) {
        railsUpdateServer.stop();
        railsUpdateServer = null;
        LOGGER.info("🛑 railsUpdateServer stopped");
    }



    // === NEW: Monster cleanup ===
    MinecraftServer server = event.getServer();
    ServiceNpcSpawnDeliveryProcessor.stop(server);
    com.seggellion.britannia_mod.worldstate.WorldStateSyncPoller.stop(server);
    WorldBootstrapHandler.onServerStopping(server);
    ServerAuthRegistry.clear(server);
    for (ServerLevel level : server.getAllLevels()) {
        int viewDistance = level.getServer().getPlayerList().getViewDistance();

        for (ServerPlayer player : level.players()) {
            ChunkPos playerChunk = player.chunkPosition();

            for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                    int cx = playerChunk.x + dx;
                    int cz = playerChunk.z + dz;

                    if (!level.hasChunk(cx, cz)) continue;
                    LevelChunk chunk = level.getChunk(cx, cz);

                    chunk.getBlockEntities().values().forEach(be -> {
                        if (be instanceof BritanniaSpawnBlockEntity spawner) {
                            spawner.forceCleanup(level);
                        }
                    });
                }
            }
        }
    }


}

public void onServerStarted(ServerStartedEvent event) {
    // Housing Deed Milestone 2: house regions live in a static map that nothing used to
    // save or load, so every restart silently revoked every owner build right and left
    // every door unable to resolve a lock. Restore them before anything else touches them.
    com.seggellion.britannia_mod.structure.StructureRegionRehydrator.start(event.getServer());
    ServiceNpcSpawnDeliveryProcessor.start(event.getServer());
    com.seggellion.britannia_mod.worldstate.WorldStateSyncPoller.start(event.getServer());
    com.seggellion.britannia_mod.service.banking.BankTransferReconciliationService.runStartupReconciliation(event.getServer());
    // Vendor/Trader Milestone 19.5: report trader-sale reservations stranded by
    // a crash; the refund itself happens on that player's next login.
    com.seggellion.britannia_mod.economy.TraderSaleReservationRecovery.reportStrandedReservations(event.getServer());
    // Vendor/Trader Milestone 20: regional TownPerson population convergence.
    com.seggellion.britannia_mod.population.TownPersonPopulationManager.start(event.getServer());
}

public void onServerTick(ServerTickEvent.Post event) {
    ServiceNpcSpawnDeliveryProcessor.tick(event.getServer());
    com.seggellion.britannia_mod.worldstate.WorldStateSyncPoller.tick(event.getServer());
    com.seggellion.britannia_mod.population.TownPersonPopulationManager.tick(event.getServer());
}

public void onServerStarting(ServerStartingEvent event) {
    MinecraftServer minecraftServer = event.getServer();
    boolean dedicatedServer = minecraftServer instanceof net.minecraft.server.dedicated.DedicatedServer;
    ServerAuthRegistry.initialize(minecraftServer, Path.of("."), dedicatedServer);
    // Mining milestone 2: block registration is complete by now, so every catalogued block id
    // must resolve against the live registry (the catalogue's unresolved-reference check).
    com.seggellion.britannia_mod.mining.Mineables.validateBlockIdsResolve();
    NameLoader.loadNames("assets/britannia_mod/uo_names.xml");

    try {
        // Start the Deed API server (existing)
        deedHttpServer = new DeedHttpServer(8080, event.getServer());
        deedHttpServer.start();
        LOGGER.info("✅ DeedHttpServer started on port 8080");
    } catch (IOException e) {
        LOGGER.error("❌ Failed to start DeedHttpServer", e);
    }

    if (ServerAuthRegistry.credentials(minecraftServer)
        .map(credentials -> credentials.railsUpdateListenerEnabled())
        .orElse(false)) {
    try {
        // Start the Rails Update API server (new)
        railsUpdateServer = new RailsUpdateServer(8081, event.getServer());
        railsUpdateServer.start();
        LOGGER.info("✅ RailsUpdateServer started on port 8081");
    } catch (IOException e) {
        LOGGER.error("❌ Failed to start RailsUpdateServer", e);
    }
    }
}




}
