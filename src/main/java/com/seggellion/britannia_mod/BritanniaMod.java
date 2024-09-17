package com.seggellion.britannia_mod;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.event.ForgeEventHandler;
import com.seggellion.britannia_mod.event.ModEventHandler;
import com.seggellion.britannia_mod.features.MobSpawnControl; 
import com.seggellion.britannia_mod.event.PlayerEventHandler;
import com.seggellion.britannia_mod.features.DiamondToolControl; 
import com.seggellion.britannia_mod.features.RecipeRemovalHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(BritanniaMod.MODID)
public class BritanniaMod {
    public static final String MODID = "britannia_mod";
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public BritanniaMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("BritanniaMod constructor called");

        // Register ModEventHandler on the mod event bus
        modEventBus.register(new ModEventHandler());

        // Register ForgeEventHandler on the Forge event bus
        NeoForge.EVENT_BUS.register(new ForgeEventHandler());

        // Register MobSpawnControl on the Forge event bus to handle mob spawn control
        NeoForge.EVENT_BUS.register(new MobSpawnControl());
        NeoForge.EVENT_BUS.register(new PlayerEventHandler()); 
        NeoForge.EVENT_BUS.register(new DiamondToolControl());
        NeoForge.EVENT_BUS.register(new RecipeRemovalHandler()); 

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
