// ItemRegistry.java
package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;


public class ItemRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
            net.minecraft.core.registries.Registries.ITEM, "britannia_mod");
  private static final Logger LOGGER = LogUtils.getLogger();
    // General Items
    public static final DeferredHolder<Item, Item> GOLD_COIN = ITEMS.register("gold_coin",
            () -> new Item(new Item.Properties().stacksTo(99)));
    
    // Spell Ingredients
    public static final DeferredHolder<Item, Item> SPIDERS_SILK = ITEMS.register("spiders_silk",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> MANDRAKE_ROOT = ITEMS.register("mandrake_root",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SULPHUROUS_ASH = ITEMS.register("sulphurous_ash",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> NIGHTSHADE = ITEMS.register("nightshade",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> BLOOD_MOSS = ITEMS.register("blood_moss",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> GARLIC = ITEMS.register("garlic",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> GINSENG = ITEMS.register("ginseng",
            () -> new Item(new Item.Properties()));

    // Magic Items
    public static final DeferredHolder<Item, Item> NIGHT_SIGHT_ITEM = ITEMS.register("night_sight_item",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> HEAL_ITEM = ITEMS.register("heal_item",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> CLUMSY_ITEM = ITEMS.register("clumsy_item",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> CREATE_FOOD_ITEM = ITEMS.register("create_food_item",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> WEAKNESS_ITEM = ITEMS.register("weakness_item",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> REACTIVE_ARMOR_ITEM = ITEMS.register("reactive_armor_item",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> FEEBLEMIND_ITEM = ITEMS.register("feeblemind_item",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> MAGIC_ARROW_ITEM = ITEMS.register("magic_arrow_item",
            () -> new Item(new Item.Properties()));

    // Spawn Eggs
    public static final DeferredHolder<Item, DeferredSpawnEggItem> HORSE_SELLER_SPAWN_EGG = ITEMS.register(
            "horse_seller_spawn_egg",
            () -> new DeferredSpawnEggItem(
                    EntityRegistry.HORSE_SELLER_NPC,
                    0xFFA500,
                    0xffffff,
                    new Item.Properties()
            )
    );

        public static final DeferredHolder<Item, DeferredSpawnEggItem> DAEMON_SPAWN_EGG = ITEMS.register(
            "daemon_spawn_egg",
            () -> new DeferredSpawnEggItem(
                    EntityRegistry.DAEMON_ENTITY,
                    0xcc0000,
                    0xffffff,
                    new Item.Properties()
            )
    );

    public static final DeferredHolder<Item, DeferredSpawnEggItem> MONGBAT_SPAWN_EGG = ITEMS.register(
            "mongbat_spawn_egg",
            () -> new DeferredSpawnEggItem(
                    EntityRegistry.MONGBAT_ENTITY,
                    0x996600,
                    0xffffff,
                    new Item.Properties()
            )
    );

    // Block Items
    public static final DeferredHolder<Item, Item> MOONGATE_BLOCK_ITEM = ITEMS.register(
            "moongate_block", () -> new BlockItem(BlockRegistry.MOONGATE_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> MOONGATE_TOP_ITEM = ITEMS.register(
            "moongate_top", () -> new BlockItem(BlockRegistry.MOONGATE_TOP.get(), new Item.Properties()));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
