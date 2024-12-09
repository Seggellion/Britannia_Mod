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
import com.seggellion.britannia_mod.spawner.BritainCemetarySpawner;
import com.seggellion.britannia_mod.spawner.ShameDungeonSpawner;
import com.seggellion.britannia_mod.client.ShameDungeonMusicHandler;
import com.seggellion.britannia_mod.event.ShadeEntitySizeHandler;
import com.seggellion.britannia_mod.event.PlayerEventHandler;
import com.seggellion.britannia_mod.event.FishingEventHandler;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.features.MobSpawnControl;
import com.seggellion.britannia_mod.features.DiamondToolControl;
import com.seggellion.britannia_mod.block.MoongateTickHandler;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.entity.EntityFishMerchant;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.city.City;


import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;


import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Mod(BritanniaMod.MODID)
public class BritanniaMod {
    public static final String MODID = "britannia_mod";
    private static final Logger LOGGER = LogUtils.getLogger();
    private int foodConsumptionTickCounter = 0; // Tick counter for food consumption
    private int starvationNotificationTickCounter = 0; // Tick counter for starvation notifications

    public BritanniaMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing BritanniaMod");

        // Register mod components
        BlockRegistry.register(modEventBus);
        ItemRegistry.register(modEventBus);
        EntityRegistry.register(modEventBus);
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
        // NeoForge.EVENT_BUS.register(new FishingEventHandler());

        // Create an instance of your event handler
        FishingEventHandler fishingEventHandler = new FishingEventHandler();

        // Register the event handler method directly
        NeoForge.EVENT_BUS.addListener(fishingEventHandler::onItemFished);

        NeoForge.EVENT_BUS.register(DaemonSpawner.class);
        NeoForge.EVENT_BUS.register(BritainCemetarySpawner.class);
        NeoForge.EVENT_BUS.register(ShameDungeonSpawner.class);
        NeoForge.EVENT_BUS.register(ShadeEntitySizeHandler.class);
        NeoForge.EVENT_BUS.addListener(this::onServerTickPre);

        ManaHandler.register();

        if (FMLLoader.getDist().isClient()) {
            modEventBus.addListener(ClientEventHandler::onClientSetup);
            modEventBus.addListener(ClientModSetup::onClientSetup);

            NeoForge.EVENT_BUS.register(new ClientEventHandler());
            modEventBus.register(new ClientOnlyItemRegistry());
            LOGGER.info("Registering ShameDungeonMusicHandler for client-side events.");
            NeoForge.EVENT_BUS.register(ShameDungeonMusicHandler.class);
        }

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        EntityRegistry.registerAttributes(event);
    }


    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel serverLevel = server.getLevel(Level.OVERWORLD); // We defined this as serverLevel


        if (serverLevel != null) {
            // Coordinates where you want the chest to appear
            BlockPos chestPos = new BlockPos(98, -60, 9979);
            
            // Set the block at these coordinates to a chest
            serverLevel.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
            
            // Retrieve the BlockEntity of the chest
            BlockEntity blockEntity = serverLevel.getBlockEntity(chestPos); // Use serverLevel, not overworld

            if (blockEntity instanceof ChestBlockEntity chestEntity) {
                // Clear the chest (optional) and set the first slot to a fishing rod
                chestEntity.setItem(0, new ItemStack(Items.FISHING_ROD));
                chestEntity.setChanged();
            }
        }
    }

 public void onServerTickPre(ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer(); // Correct method to retrieve server
        ServerLevel serverLevel = server.getLevel(Level.OVERWORLD); // Replace with appropriate dimension if needed

        if (serverLevel == null) {
            LOGGER.warn("ServerLevel is null. Skipping tick.");
            return;
        }
        //populate chest
         if (serverLevel != null) {
        BlockPos chestPos = new BlockPos(98, -60, 9979);
        BlockEntity blockEntity = serverLevel.getBlockEntity(chestPos);
            if (blockEntity instanceof ChestBlockEntity chestEntity) {
                if (chestEntity.getItem(0).isEmpty()) {
                    chestEntity.setItem(0, new ItemStack(Items.FISHING_ROD));
                    chestEntity.setChanged();
                }
            }
        }

        // Handle Food Consumption
        foodConsumptionTickCounter++;
        if (foodConsumptionTickCounter >= 1200) { // 1200 ticks = 60 seconds
            LOGGER.warn("60 seconds elapsed. Initiating food consumption for all cities.");

            CityManager cityManager = CityManager.get(serverLevel);
            LOGGER.warn("Number of cities managed by CityManager: {}", cityManager.getCities().size());

            cityManager.getCities().forEach((cityName, city) -> {
                // Consume food resources
                CityInventory cityInventory = city.getInventory();
                cityInventory.consumeFood(); // No argument
            });

            // Reset the food consumption tick counter after consumption
            foodConsumptionTickCounter = 0;
            LOGGER.warn("Food consumption cycle completed and tick counter reset.");
        }

        // Handle Starvation Notifications
        starvationNotificationTickCounter++;
        if (starvationNotificationTickCounter >= 20) { // 20 ticks = 1 second
            LOGGER.debug("Starvation notification check cycle.");

            CityManager cityManager = CityManager.get(serverLevel);
            cityManager.getCities().forEach((cityName, city) -> {
                CityInventory cityInventory = city.getInventory();
                if (cityInventory.isStarving()) {
                    // Iterate through associated merchants
                    List<EntityFishMerchant> merchants = cityInventory.getAssociatedMerchants();
                    for (EntityFishMerchant merchant : merchants) {
                        Level merchantLevel = merchant.level();
                        if (merchantLevel instanceof ServerLevel sLevel) {
                            double radius = 20.0;
                            List<Player> nearbyPlayers = sLevel.getEntitiesOfClass(Player.class,
                                    merchant.getBoundingBox().inflate(radius));

                            for (Player player : nearbyPlayers) {
                                player.displayClientMessage(
                                        Component.literal(cityName + " is starving! Please sell them some food."),
                                        true // Action bar display
                                );
                            }
                        }
                    }
                }
            });

            // Reset the starvation notification tick counter after notifications
            starvationNotificationTickCounter = 0;
            LOGGER.debug("Starvation notification check cycle completed.");
        }
    }

    public static CityInventory getCityInventory(ServerLevel serverLevel, String cityName) {
        LOGGER.warn("Retrieving CityInventory for city: {}", cityName);
        CityManager cityManager = CityManager.get(serverLevel);
        City city = cityManager.getCity(cityName);
        if (city == null) {
            cityManager.addCity(cityName);
            city = cityManager.getCity(cityName);
        }
        return city.getInventory();
    }

    public static void associateNpcToCity(ServerLevel serverLevel, String cityName, EntityFishMerchant merchant) {
        LOGGER.warn("Associating NPC {} to city {}", merchant.getUUID(), cityName);
        CityInventory cityInventory = getCityInventory(serverLevel, cityName);
        cityInventory.associateMerchant(merchant);
        LOGGER.warn("City inventory after association: {}", cityInventory);
    }

    public static List<EntityFishMerchant> getCityMerchants(ServerLevel serverLevel, String cityName) {
        CityManager cityManager = CityManager.get(serverLevel);
        City city = cityManager.getCity(cityName);
        if (city != null) {
            return city.getInventory().getAssociatedMerchants();
        }
        return Collections.emptyList();
    }

    private void notifyNearbyPlayers(ServerLevel serverLevel, String cityName) {
        // This method can be removed or repurposed since notifications are handled in the tick handler
    }
}
