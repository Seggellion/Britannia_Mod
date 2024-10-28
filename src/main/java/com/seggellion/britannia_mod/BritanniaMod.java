// BritanniaMod.java
package com.seggellion.britannia_mod;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.registry.*;
import com.seggellion.britannia_mod.event.ClientEventHandler;
import com.seggellion.britannia_mod.ClientModSetup;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.event.ForgeEventHandler;
import com.seggellion.britannia_mod.event.PlayerEventHandler;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.command.ModCommands;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(BritanniaMod.MODID)
public class BritanniaMod {
    public static final String MODID = "britannia_mod";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BritanniaMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing BritanniaMod");

        // Register mod components
        BlockRegistry.register(modEventBus);
        ItemRegistry.register(modEventBus);
        EntityRegistry.register(modEventBus);
        CreativeTabRegistry.register(modEventBus);
        SoundRegistry.register(modEventBus);
        ConfigRegistry.register();  // No longer passes modContainer
        ModAttributes.register(modEventBus); 

        modEventBus.addListener(this::registerEntityAttributes); 
        modEventBus.register(NetworkHandler.class);
        ModSounds.register(modEventBus);
        // Register event handlers
        NeoForge.EVENT_BUS.register(new ForgeEventHandler());
        NeoForge.EVENT_BUS.register(new PlayerEventHandler());

        if (FMLLoader.getDist().isClient()) {
            modEventBus.addListener(ClientEventHandler::onClientSetup);
                modEventBus.addListener(ClientModSetup::onClientSetup);

            NeoForge.EVENT_BUS.register(new ClientEventHandler());
        }
        
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        EntityRegistry.registerAttributes(event);
    }
    

    private void onServerStarting(ServerStartingEvent event) {
        ModCommands.register(event.getServer().getCommands().getDispatcher());
    }
}
