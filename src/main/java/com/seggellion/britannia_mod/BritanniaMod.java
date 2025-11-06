// BritanniaMod.java
package com.seggellion.britannia_mod;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.registry.*;
import com.seggellion.britannia_mod.event.ClientEventHandler;
import com.seggellion.britannia_mod.client.ClientOnlyItemRegistry;
import com.seggellion.britannia_mod.ClientModSetup;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.magic.ManaHandler;
import com.seggellion.britannia_mod.event.ForgeEventHandler;
import com.seggellion.britannia_mod.spawner.DaemonSpawner;
import com.seggellion.britannia_mod.client.model.ThinWallModels;
import com.seggellion.britannia_mod.client.Keybinds;
import com.seggellion.britannia_mod.skill.SkillManager;
import com.seggellion.britannia_mod.spawner.BritainCemetarySpawner;
import com.seggellion.britannia_mod.spawner.ShameDungeonSpawner;
import com.seggellion.britannia_mod.spawner.BritainCitySpawner;
import com.seggellion.britannia_mod.client.ShameDungeonMusicHandler;
import com.seggellion.britannia_mod.client.BritainMusicHandler;
import com.seggellion.britannia_mod.client.house.GhostStructurePreviewRenderer;
import com.seggellion.britannia_mod.event.ShadeEntitySizeHandler;
import com.seggellion.britannia_mod.event.BreakSpeedHandler;
import com.seggellion.britannia_mod.event.InventoryHandler;
import com.seggellion.britannia_mod.event.ChestHandler;
import com.seggellion.britannia_mod.event.GlobalEventHandler;
import com.seggellion.britannia_mod.event.WoodChopEventHandler;
import com.seggellion.britannia_mod.event.PlayerEventHandler;
import com.seggellion.britannia_mod.event.FishingEventHandler;
import com.seggellion.britannia_mod.event.TreeKarmaHandler;
import com.seggellion.britannia_mod.event.KarmaReductionHandler;
import com.seggellion.britannia_mod.villager.BlacksmithPOIHandler;
import com.seggellion.britannia_mod.client.ModModelLayers;
import com.seggellion.britannia_mod.event.BlockRestoreHandler;
import com.seggellion.britannia_mod.event.CustomBlockBreakHandler;
import com.seggellion.britannia_mod.event.ToolInteractionHandler;
import com.seggellion.britannia_mod.event.CityGameModeHandler;
//import com.seggellion.britannia_mod.villager.CustomVillagerProfessions;
import com.seggellion.britannia_mod.villager.BlacksmithProfessions;
import com.seggellion.britannia_mod.villager.VillagerTradeUpdater;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.features.MobSpawnControl;
import com.seggellion.britannia_mod.features.DiamondToolControl;
import com.seggellion.britannia_mod.block.MoongateTickHandler;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.structure.SurvivalZoneHandler;
import com.seggellion.britannia_mod.structure.StructureProtectionHandler;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.util.NameLoader;
import com.seggellion.britannia_mod.network.CityDataSync;
import com.seggellion.britannia_mod.network.DeedHttpServer;
import com.seggellion.britannia_mod.network.RailsUpdateServer;
import com.seggellion.britannia_mod.block.entity.MonsterSpawnBlockEntity;

import com.seggellion.britannia_mod.util.OreVeinLoader;
import com.seggellion.britannia_mod.client.ThinWallClient;
import com.seggellion.britannia_mod.sync.BlessedItemSyncHandler;
import com.seggellion.britannia_mod.event.WorldBootstrapHandler;


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
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.slf4j.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

@Mod(BritanniaMod.MODID)
public class BritanniaMod {
    public static final String MODID = "britannia_mod";
    private static final Logger LOGGER = LogUtils.getLogger();
    private int starvationNotificationTickCounter = 0; // Tick counter for starvation notifications
    private DeedHttpServer deedHttpServer;
    private RailsUpdateServer railsUpdateServer;

    public BritanniaMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing BritanniaMod");
        OreVeinLoader.loadOreVeins();
          BlessedItemSyncHandler.init(); 
        WorldBootstrapHandler.init(); 
        // Register mod components
     //   FeatureRegistry.register(modEventBus);
        BlockRegistry.register(modEventBus);
        BlockEntityRegistry.register(modEventBus);
        ItemRegistry.register(modEventBus);
        SwordRegistry.register(modEventBus);
        FishRegistry.register(modEventBus);
        PaintingRegistry.register(modEventBus);
      //  MenuRegistry.register(modEventBus);

        ToolRegistry.register(modEventBus);
        EntityRegistry.register(modEventBus);
        BlacksmithProfessions.registerAll(modEventBus);
    
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

        // Register event handlers
        MoongateTickHandler.registerTickEvent(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.register(new ForgeEventHandler());
        NeoForge.EVENT_BUS.register(new PlayerEventHandler());
        NeoForge.EVENT_BUS.register(new DiamondToolControl());
        NeoForge.EVENT_BUS.register(new MobSpawnControl());
        NeoForge.EVENT_BUS.register(new BlockRestoreHandler());

        FishingEventHandler fishingEventHandler = new FishingEventHandler();

        // Register the event handler method directly
        NeoForge.EVENT_BUS.addListener(fishingEventHandler::onItemFished);

        NeoForge.EVENT_BUS.register(DaemonSpawner.class);
        NeoForge.EVENT_BUS.register(VillagerTradeUpdater.class);
        NeoForge.EVENT_BUS.register(BritainCemetarySpawner.class);
        NeoForge.EVENT_BUS.register(ShameDungeonSpawner.class);
        NeoForge.EVENT_BUS.register(BritainCitySpawner.class);
        NeoForge.EVENT_BUS.register(ShadeEntitySizeHandler.class);
        NeoForge.EVENT_BUS.register(GlobalEventHandler.class);
        NeoForge.EVENT_BUS.register(WoodChopEventHandler.class);
        NeoForge.EVENT_BUS.register(new ToolInteractionHandler());
        NeoForge.EVENT_BUS.register(new CityGameModeHandler());
       NeoForge.EVENT_BUS.register(new CustomBlockBreakHandler());
        NeoForge.EVENT_BUS.register(new ChestHandler());
     //   NeoForge.EVENT_BUS.register(new PopulationEventHandler());
       NeoForge.EVENT_BUS.register(new InventoryHandler());
        NeoForge.EVENT_BUS.register(new TreeKarmaHandler());
        NeoForge.EVENT_BUS.register(new KarmaReductionHandler());
        NeoForge.EVENT_BUS.register(new BlacksmithPOIHandler());
        NeoForge.EVENT_BUS.register(new SurvivalZoneHandler());
          NeoForge.EVENT_BUS.register(new StructureProtectionHandler());

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);


        ManaHandler.register();
          SkillManager.init(); 
        if (FMLLoader.getDist().isClient()) {
            modEventBus.addListener(Keybinds::registerKeys);
            modEventBus.addListener(ClientModSetup::onClientSetup);
            modEventBus.addListener(ClientModSetup::onRegisterItemColors);
            modEventBus.addListener(ClientModSetup::registerRenderers);
            modEventBus.addListener(ClientModSetup::registerGeometryLoaders);
            modEventBus.addListener(ClientModSetup::registerAdditionalModels);
        modEventBus.register(ThinWallModels.class);
  
            ClientEventHandler.register(modEventBus);
            modEventBus.register(new ClientOnlyItemRegistry());
            modEventBus.register(ModModelLayers.class);
            modEventBus.register(new ThinWallClient()); 
            NeoForge.EVENT_BUS.register(ShameDungeonMusicHandler.class);
            NeoForge.EVENT_BUS.register(BritainMusicHandler.class);
            NeoForge.EVENT_BUS.register(GhostStructurePreviewRenderer.class);
            modEventBus.addListener(ClientEventHandler::registerClientPackets);
        }

       
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
        LOGGER.info("🛑 railsUpdateServer stopped");
    }



    // === NEW: Monster cleanup ===
    MinecraftServer server = event.getServer();
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
                        if (be instanceof MonsterSpawnBlockEntity spawner) {
                            spawner.forceCleanup(level);
                        }
                    });
                }
            }
        }
    }


}

public void onServerStarting(ServerStartingEvent event) {
    NameLoader.loadNames("assets/britannia_mod/uo_names.xml");

    try {
        // Start the Deed API server (existing)
        deedHttpServer = new DeedHttpServer(8080, event.getServer());
        deedHttpServer.start();
        LOGGER.info("✅ DeedHttpServer started on port 8080");
    } catch (IOException e) {
        LOGGER.error("❌ Failed to start DeedHttpServer", e);
    }

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
