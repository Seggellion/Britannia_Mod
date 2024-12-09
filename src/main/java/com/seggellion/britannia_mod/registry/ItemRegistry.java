// ItemRegistry.java
package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.registry.EntityRegistry;

import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
import com.seggellion.britannia_mod.item.OrderShieldItem;
import com.seggellion.britannia_mod.item.MoongateLinkingWand;
import com.seggellion.britannia_mod.item.WeightedFishItem;


import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items; // Example item

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Tool.Rule;

import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;
import java.util.Optional;
import java.util.List;


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

public static final DeferredHolder<Item, Item> COD = ITEMS.register("cod",
        () -> new WeightedFishItem(new Item.Properties()));

public static final DeferredHolder<Item, Item> SALMON = ITEMS.register("salmon",
        () -> new WeightedFishItem(new Item.Properties()));

public static final DeferredHolder<Item, Item> TUNA = ITEMS.register("tuna",
        () -> new WeightedFishItem(new Item.Properties()));

public static final DeferredHolder<Item, Item> TROUT = ITEMS.register("trout",
        () -> new WeightedFishItem(new Item.Properties()));

public static final DeferredHolder<Item, Item> SWORDFISH = ITEMS.register("swordfish",
        () -> new WeightedFishItem(new Item.Properties()));


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

    public static final DeferredHolder<Item, DeferredSpawnEggItem> LICH_SPAWN_EGG = ITEMS.register(
            "lich_spawn_egg",
            () -> new DeferredSpawnEggItem(
                    EntityRegistry.LICH_ENTITY,
                    0x808080,
                    0xffffff,
                    new Item.Properties()
            )
    );

        public static final DeferredHolder<Item, DeferredSpawnEggItem> WRAITH_SPAWN_EGG = ITEMS.register(
            "wraith_spawn_egg",
            () -> new DeferredSpawnEggItem(
                    EntityRegistry.WRAITH_ENTITY,
                    0xD3D3D3,
                    0xffffff,
                    new Item.Properties()
            )
    );

        public static final DeferredHolder<Item, DeferredSpawnEggItem> SHADE_SPAWN_EGG = ITEMS.register(
            "shade_spawn_egg",
            () -> new DeferredSpawnEggItem(
                    EntityRegistry.SHADE_ENTITY,
                    0x202020,
                    0xffffff,
                    new Item.Properties()
            )
    );    

            public static final DeferredHolder<Item, DeferredSpawnEggItem> EARTH_ELEMENTAL_SPAWN_EGG = ITEMS.register(
            "earth_elemental_spawn_egg",
            () -> new DeferredSpawnEggItem(
                    EntityRegistry.EARTH_ELEMENTAL_ENTITY,
                    0x964B00,
                    0xffffff,
                    new Item.Properties()
            )
    );    

            public static final DeferredHolder<Item, DeferredSpawnEggItem> GHOUL_SPAWN_EGG = ITEMS.register(
            "ghoul_spawn_egg",
            () -> new DeferredSpawnEggItem(
                    EntityRegistry.GHOUL_ENTITY,
                    0x8B0000,
                    0xffffff,
                    new Item.Properties()
            )
    );   

       public static final DeferredHolder<Item, DeferredSpawnEggItem> WISP_SPAWN_EGG = ITEMS.register(
            "wisp_spawn_egg",
            () -> new DeferredSpawnEggItem(
                    EntityRegistry.WISP_ENTITY,
                    0x02D8E9,
                    0xffffff,
                    new Item.Properties()
            )
    );   

public static final DeferredHolder<Item, Item> ORDER_SHIELD = ITEMS.register("order_shield",
    () -> {
        Item shield = new OrderShieldItem(new Item.Properties()
            .stacksTo(1)
            .durability(336));
        LOGGER.info("Registered Order Shield: {}", shield);
        return shield;
    });



// Tools
    // Register TwoHandedAxeItem with Tool rules for logs
 public static final DeferredHolder<Item, Item> TWO_HANDED_AXE = ITEMS.register("two_handed_axe",
            () -> {
                LOGGER.info("Registering TwoHandedAxeItem");
                // Define a TagKey for logs
                TagKey<Block> logTag = TagKey.create(BuiltInRegistries.BLOCK.key(), net.minecraft.tags.BlockTags.LOGS.location());
                // Attempt to retrieve the HolderSet for the logs tag
                Optional<List<Tool.Rule>> toolRules = BuiltInRegistries.BLOCK.getTag(logTag)
                        .map(holderSet -> List.of(new Rule(holderSet, Optional.of(6.0F), Optional.of(true))));
                if (toolRules.isEmpty()) {
                    LOGGER.error("Failed to retrieve the logs tag for Tool.Rule creation");
                    return new TwoHandedAxeItem(Tiers.IRON, new Item.Properties()); // Fallback item without tool properties
                }
                // Create a Tool with the retrieved rules for logs
                Tool tool = new Tool(
                        toolRules.get(), // Rule for logs with speed and can-drop option
                        6.0F, // Default mining speed
                        1     // Damage per block mined
                );
                // Attach Tool component to Item.Properties
                return new TwoHandedAxeItem(Tiers.IRON, new Item.Properties()
                        .component(net.minecraft.core.component.DataComponents.TOOL, tool));
            }
    );
    // Block Items

        public static final DeferredHolder<Item, Item> SHADE_SPAWN_BLOCK_ITEM = ITEMS.register(
            "shade_spawn_block", () -> new BlockItem(BlockRegistry.SHADE_SPAWN_BLOCK.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> LICH_SPAWN_BLOCK_ITEM = ITEMS.register(
            "lich_spawn_block", () -> new BlockItem(BlockRegistry.LICH_SPAWN_BLOCK.get(), new Item.Properties()));


public static final DeferredHolder<Item, Item> DUNGEON_MOONGATE_BLOCK_ITEM = ITEMS.register(
        "dungeon_moongate_block", () -> new BlockItem(BlockRegistry.DUNGEON_MOONGATE_BLOCK.get(), new Item.Properties()));


public static final DeferredHolder<Item, Item> DUNGEON_MOONGATE_TOP_ITEM = ITEMS.register(
        "dungeon_moongate_top", () -> new BlockItem(BlockRegistry.DUNGEON_MOONGATE_TOP.get(), new Item.Properties()));


public static final DeferredHolder<Item, Item> MOONGATE_LINKING_WAND = ITEMS.register(
    "moongate_linking_wand",
    () -> new MoongateLinkingWand(new Item.Properties().stacksTo(1)));


    public static final DeferredHolder<Item, Item> MOONGATE_BLOCK_ITEM = ITEMS.register(
            "moongate_block", () -> new BlockItem(BlockRegistry.MOONGATE_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> MOONGATE_TOP_ITEM = ITEMS.register(
            "moongate_top", () -> new BlockItem(BlockRegistry.MOONGATE_TOP.get(), new Item.Properties()));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
