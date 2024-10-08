package com.seggellion.britannia_mod;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.MoongateBlock;
import com.seggellion.britannia_mod.event.ForgeEventHandler;
import com.seggellion.britannia_mod.event.ModEventHandler;
import com.seggellion.britannia_mod.features.MobSpawnControl;
import com.seggellion.britannia_mod.event.PlayerEventHandler;
import com.seggellion.britannia_mod.features.DiamondToolControl;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.magic.ManaHandler;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.event.ClientEventHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            Registries.BLOCK, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
            Registries.ITEM, MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<Item, Item> SPIDERS_SILK = ITEMS.register(
            "spiders_silk", () -> new Item(new Item.Properties()));
        public static final DeferredHolder<Item, Item> MANDRAKE_ROOT = ITEMS.register(
            "mandrake_root", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SULPHUROUS_ASH = ITEMS.register(
            "sulphurous_ash", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> NIGHTSHADE = ITEMS.register(
            "nightshade", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> BLOOD_MOSS = ITEMS.register(
            "blood_moss", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> GARLIC = ITEMS.register(
            "garlic", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> GINSENG = ITEMS.register(
            "ginseng", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> NIGHT_SIGHT_ITEM = ITEMS.register(
            "night_sight_item", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> HEAL_ITEM = ITEMS.register(
            "heal_item", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> CLUMSY_ITEM = ITEMS.register(
            "clumsy_item", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> CREATE_FOOD_ITEM = ITEMS.register(
            "create_food_item", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> WEAKNESS_ITEM = ITEMS.register(
            "weakness_item", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> REACTIVE_ARMOR_ITEM = ITEMS.register(
            "reactive_armor_item", () -> new Item(new Item.Properties()));
        
        public static final DeferredHolder<Item, Item> FEEBLEMIND_ITEM = ITEMS.register(
        "feeblemind_item", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> MAGIC_ARROW_ITEM = ITEMS.register(
            "magic_arrow_item", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Block, Block> MOONGATE_BLOCK = BLOCKS.register(
            "moongate_block", MoongateBlock::new);

    public static final DeferredHolder<Item, Item> MOONGATE_BLOCK_ITEM = ITEMS.register(
            "moongate_block", () -> new BlockItem(MOONGATE_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Block> MOONGATE_TOP = BLOCKS.register(
            "moongate_top", MoongateBlock::new);

    public static final DeferredHolder<Item, Item> MOONGATE_TOP_ITEM = ITEMS.register(
            "moongate_top", () -> new BlockItem(MOONGATE_TOP.get(), new Item.Properties()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE__ITEM_TAB = CREATIVE_TABS.register(
            "britannia_item_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.britannia_item_tab"))
                    .icon(() -> GARLIC.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(GARLIC.get());
                        output.accept(NIGHTSHADE.get());
                        output.accept(BLOOD_MOSS.get());
                        output.accept(GINSENG.get());
                        output.accept(SPIDERS_SILK.get());
                        output.accept(SULPHUROUS_ASH.get());
                        output.accept(MANDRAKE_ROOT.get());
                        output.accept(MOONGATE_BLOCK_ITEM.get());
                        output.accept(MOONGATE_TOP_ITEM.get());
                    }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_MAGIC_TAB = CREATIVE_TABS.register(
            "britannia_tab_magic", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.britannia_magic_tab"))
                    .icon(() -> NIGHT_SIGHT_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(NIGHT_SIGHT_ITEM.get());
                        output.accept(HEAL_ITEM.get());
                        output.accept(MAGIC_ARROW_ITEM.get());
                        output.accept(CLUMSY_ITEM.get());
                        output.accept(WEAKNESS_ITEM.get());
                        output.accept(CREATE_FOOD_ITEM.get());
                        output.accept(FEEBLEMIND_ITEM.get());
                        output.accept(REACTIVE_ARMOR_ITEM.get());
                    }).build());

    public BritanniaMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("BritanniaMod constructor called");

        modEventBus.register(new ModEventHandler());
        modEventBus.register(NetworkHandler.class);

        NeoForge.EVENT_BUS.register(new ForgeEventHandler());
        NeoForge.EVENT_BUS.register(new MobSpawnControl());
        NeoForge.EVENT_BUS.register(new PlayerEventHandler());
        NeoForge.EVENT_BUS.register(new DiamondToolControl());
        ManaHandler.register();

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        ModSounds.SOUND_EVENTS.register(modEventBus);

if (FMLLoader.getDist().isClient()) {
    modEventBus.addListener(ClientModSetup::onClientSetup);
    NeoForge.EVENT_BUS.register(new ClientEventHandler());
}

    }
}
