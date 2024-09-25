package com.seggellion.britannia_mod;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.event.ForgeEventHandler;
import com.seggellion.britannia_mod.event.ModEventHandler;
import com.seggellion.britannia_mod.features.MobSpawnControl;
import com.seggellion.britannia_mod.event.PlayerEventHandler;
import com.seggellion.britannia_mod.features.DiamondToolControl;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.magic.ManaHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;

@Mod(BritanniaMod.MODID)
public class BritanniaMod {
    public static final String MODID = "britannia_mod";
    private static final Logger LOGGER = LogUtils.getLogger();

    // Deferred register for blocks, items, and creative tabs
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    // Define CREATIVE_TABS
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Registering Reagent Items using DeferredItem
    public static final DeferredItem<Item> SPIDERS_SILK = ITEMS.registerSimpleItem("spiders_silk", new Item.Properties());
    public static final DeferredItem<Item> SULPHUROUS_ASH = ITEMS.registerSimpleItem("sulphurous_ash", new Item.Properties());
    public static final DeferredItem<Item> NIGHT_SIGHT_ITEM = ITEMS.registerSimpleItem("night_sight_item", new Item.Properties());

    // Register a Creative Tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS.register("britannia_tab",
        () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.britannia_tab"))
                .icon(() -> NIGHT_SIGHT_ITEM.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    output.accept(SPIDERS_SILK.get());
                    output.accept(SULPHUROUS_ASH.get());
                    output.accept(NIGHT_SIGHT_ITEM.get());
                }).build());

    public BritanniaMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("BritanniaMod constructor called");

        // Register mod lifecycle event handlers on the mod event bus
        modEventBus.register(new ModEventHandler());
        modEventBus.register(NetworkHandler.class);  // Ensure NetworkHandler is registered on both client and server

        // Register server lifecycle and game-related events on the NeoForge EVENT_BUS
        NeoForge.EVENT_BUS.register(new ForgeEventHandler());
        NeoForge.EVENT_BUS.register(new MobSpawnControl());
        NeoForge.EVENT_BUS.register(new PlayerEventHandler());
        NeoForge.EVENT_BUS.register(new DiamondToolControl());
        ManaHandler.register(); // Register ManaHandler

        // Register mod config
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Register blocks, items, and creative tabs
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        // Ensure client-side setup is registered only on the client
        if (FMLLoader.getDist().isClient()) {
            modEventBus.addListener(ClientModSetup::onClientSetup);
        }
    }
}
