// ItemRegistry.java
package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.registry.EntityRegistry;

// import com.seggellion.britannia_mod.item.SmallWoodHouseDeedItem;
import com.seggellion.britannia_mod.item.BlueTentDeedItem;
import com.seggellion.britannia_mod.item.PurpleTentDeedItem;
import com.seggellion.britannia_mod.item.DeedItem;
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
import com.seggellion.britannia_mod.item.InteriorDecoratorToolItem;
import com.seggellion.britannia_mod.item.BritanniaPickaxeItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.item.ModToolTiers;
import com.seggellion.britannia_mod.item.OrderShieldItem;
import com.seggellion.britannia_mod.item.MoongateLinkingWand;
import com.seggellion.britannia_mod.item.CarpetTeleporterItem;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import com.seggellion.britannia_mod.structure.HouseStyle;
import com.seggellion.britannia_mod.item.DeedItemFactory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Tier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.BlockItem;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.HolderSet.Named;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.advancements.critereon.BlockPredicate; 

import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.registries.RegistryManager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;
import java.util.Optional;
import java.util.List;
import java.util.Collections;
import java.util.UUID;
import java.util.Map;
import java.util.EnumMap;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;


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


// custom fraction blocks

public static final DeferredHolder<Item, Item> QUARTER_DIRT_BLOCK_ITEM = ITEMS.register(
    "quarter_dirt_block",
    () -> new BlockItem(BlockRegistry.QUARTER_DIRT_BLOCK.get(), new Item.Properties())
);


public static final DeferredHolder<Item, Item> HALF_DIRT_BLOCK_ITEM = ITEMS.register(
    "half_dirt_block",
    () -> new BlockItem(BlockRegistry.HALF_DIRT_BLOCK.get(), new Item.Properties())
);

public static final DeferredHolder<Item, Item> THREE_QUARTER_DIRT_BLOCK_ITEM = ITEMS.register(
    "three_quarter_dirt_block",
    () -> new BlockItem(BlockRegistry.THREE_QUARTER_DIRT_BLOCK.get(), new Item.Properties())
);


public static final DeferredHolder<Item, Item> QUARTER_GRASS_BLOCK_ITEM = ITEMS.register(
    "quarter_grass_block",
    () -> new BlockItem(BlockRegistry.QUARTER_GRASS_BLOCK.get(), new Item.Properties())
);

public static final DeferredHolder<Item, Item> HALF_GRASS_BLOCK_ITEM = ITEMS.register(
    "half_grass_block",
    () -> new BlockItem(BlockRegistry.HALF_GRASS_BLOCK.get(), new Item.Properties())
);

public static final DeferredHolder<Item, Item> THREE_QUARTER_GRASS_BLOCK_ITEM = ITEMS.register(
    "three_quarter_grass_block",
    () -> new BlockItem(BlockRegistry.THREE_QUARTER_GRASS_BLOCK.get(), new Item.Properties())
);


public static final DeferredHolder<Item, Item> QUARTER_DEEPSLATE_COBBLESTONE_BLOCK_ITEM = ITEMS.register(
    "quarter_deepslate_cobblestone_block",
    () -> new BlockItem(BlockRegistry.QUARTER_DEEPSLATE_COBBLESTONE_BLOCK.get(), new Item.Properties())
);

public static final DeferredHolder<Item, Item> HALF_DEEPSLATE_COBBLESTONE_BLOCK_ITEM = ITEMS.register(
    "half_deepslate_cobblestone_block",
    () -> new BlockItem(BlockRegistry.HALF_DEEPSLATE_COBBLESTONE_BLOCK.get(), new Item.Properties())
);

public static final DeferredHolder<Item, Item> THREE_QUARTER_DEEPSLATE_COBBLESTONE_BLOCK_ITEM = ITEMS.register(
    "three_quarter_deepslate_cobblestone_block",
    () -> new BlockItem(BlockRegistry.THREE_QUARTER_DEEPSLATE_COBBLESTONE_BLOCK.get(), new Item.Properties())
);


        // Housing tools
public static final DeferredHolder<Item, Item> INTERIOR_DECORATOR_TOOL =
        ITEMS.register("interior_decorator_tool",
            () -> new InteriorDecoratorToolItem(
                    new Item.Properties()
                        .stacksTo(1)            // one tool per slot
                        .durability(1)));


public static final DeferredHolder<Item, Item> DEED_ITEM =
    ITEMS.register("deed_item",
        () -> new DeedItem(new Item.Properties()
            .stacksTo(1)));  // ✅ no creative tab here


    /* ------------- 2) NEW: all house deeds in one map --------- */
public static final Map<HouseStyle, DeferredHolder<Item, Item>> HOUSE_DEEDS =
        new EnumMap<>(HouseStyle.class); // ✅ now by HouseStyle

    static {
        for (HouseStyle style : HouseStyle.values()) {
            HOUSE_DEEDS.put(style, DeedItemFactory.register(ITEMS, style));
        }
    }

    /** Returns the actual Item for the given style; safe after registry events have fired. */
    public static Item deedFor(HouseStyle style) {
        return HOUSE_DEEDS.get(style).get();
    }


// statues

public static final DeferredHolder<Item, BlockItem> STATUE_STAND_ITEM = ITEMS.register("statue_stand", () ->
    new BlockItem(BlockRegistry.STATUE_STAND.get(), new Item.Properties())
);

public static final DeferredHolder<Item, Item> PILLAR_ITEM = ITEMS.register(
    "statue_pillar", () -> new BlockItem(BlockRegistry.STATUE_PILLAR.get(), new Item.Properties())
);

public static final DeferredHolder<Item, Item> STATUE_WOMAN_ITEM = ITEMS.register(
    "statue_woman", () -> new BlockItem(BlockRegistry.STATUE_WOMAN.get(), new Item.Properties())
);

  public static final DeferredHolder<Item, Item> STATUE_COUPLE_ITEM = ITEMS.register(
    "statue_couple", () -> new BlockItem(BlockRegistry.STATUE_COUPLE.get(), new Item.Properties())
);
  
  public static final DeferredHolder<Item, Item> STATUE_MAN_ITEM = ITEMS.register(
    "statue_man", () -> new BlockItem(BlockRegistry.STATUE_MAN.get(), new Item.Properties())
);
  
    /* ------------- convenience getters ----------------------- */




public static final DeferredHolder<Item, Item> BLUE_TENT_ITEM = ITEMS.register(
    "blue_tent", () -> new BlockItem(BlockRegistry.BLUE_TENT.get(), new Item.Properties())
);

public static final DeferredHolder<Item, Item> BLUE_TENT_DEED = ITEMS.register(
    "blue_tent_deed", () -> new BlueTentDeedItem(new Item.Properties())
);

public static final DeferredHolder<Item, Item> PURPLE_TENT_ITEM = ITEMS.register(
    "purple_tent", () -> new BlockItem(BlockRegistry.PURPLE_TENT.get(), new Item.Properties())
);

public static final DeferredHolder<Item, Item> PURPLE_TENT_DEED = ITEMS.register(
    "purple_tent_deed", () -> new PurpleTentDeedItem(new Item.Properties())
);

// roofing items

public static final DeferredHolder<Item, BlockItem> TILE_ROOF_ITEM = ITEMS.register("tile_roof", () ->
    new BlockItem(BlockRegistry.TILE_ROOF.get(), new Item.Properties())
);

public static final DeferredHolder<Item, BlockItem> THATCH_ROOF_ITEM = ITEMS.register("thatch_roof", () ->
    new BlockItem(BlockRegistry.THATCH_ROOF.get(), new Item.Properties())
);


public static final DeferredHolder<Item, BlockItem> CEDAR_ROOF_ITEM = ITEMS.register("cedar_roof", () ->
    new BlockItem(BlockRegistry.CEDAR_ROOF.get(), new Item.Properties())
);

public static final DeferredHolder<Item, BlockItem> SLATE_ROOF_ITEM = ITEMS.register("slate_roof", () ->
    new BlockItem(BlockRegistry.SLATE_ROOF.get(), new Item.Properties())
);

public static final DeferredHolder<Item, BlockItem> TILE_ROOF_FLAT_ITEM = ITEMS.register("tile_roof_flat", () ->
    new BlockItem(BlockRegistry.TILE_ROOF_FLAT.get(), new Item.Properties())
);

public static final DeferredHolder<Item, BlockItem> SLATE_ROOF_FLAT_ITEM = ITEMS.register("slate_roof_flat", () ->
    new BlockItem(BlockRegistry.SLATE_ROOF_FLAT.get(), new Item.Properties())
);

public static final DeferredHolder<Item, BlockItem> SLATE_ROOF_1_FLAT_ITEM = ITEMS.register("slate_roof_1_flat", () ->
    new BlockItem(BlockRegistry.SLATE_ROOF_1_FLAT.get(), new Item.Properties())
);

public static final DeferredHolder<Item, BlockItem> SLATE_ROOF_2_FLAT_ITEM = ITEMS.register("slate_roof_2_flat", () ->
    new BlockItem(BlockRegistry.SLATE_ROOF_2_FLAT.get(), new Item.Properties())
);


public static final DeferredHolder<Item, BlockItem> THATCH_ROOF_FLAT_ITEM = ITEMS.register("thatch_roof_flat", () ->
    new BlockItem(BlockRegistry.THATCH_ROOF_FLAT.get(), new Item.Properties())
);

public static final DeferredHolder<Item, BlockItem> CEDAR_ROOF_FLAT_ITEM = ITEMS.register("cedar_roof_flat", () ->
    new BlockItem(BlockRegistry.CEDAR_ROOF_FLAT.get(), new Item.Properties())
);

// structure items
public static final DeferredHolder<Item, BlockItem> BRICK_FOUNDATION_OAK_ITEM =
        ITEMS.register("brick_foundation_oak",
            () -> new BlockItem(BlockRegistry.BRICK_FOUNDATION_OAK.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> BRICK_FOUNDATION_SPRUCE_ITEM =
        ITEMS.register("brick_foundation_spruce",
            () -> new BlockItem(BlockRegistry.BRICK_FOUNDATION_SPRUCE.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> STONE_FOUNDATION_ITEM =
        ITEMS.register("stone_foundation",
            () -> new BlockItem(BlockRegistry.STONE_FOUNDATION.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> COBBLESTONE_FOUNDATION_ITEM =
        ITEMS.register("cobblestone_foundation",
            () -> new BlockItem(BlockRegistry.COBBLESTONE_FOUNDATION.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> BIRCH_WALL_ITEM =
        ITEMS.register("birch_wall",
            () -> new BlockItem(BlockRegistry.BIRCH_WALL.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WINDOW_1X1_ITEM =
        ITEMS.register("window_1x1",
            () -> new BlockItem(BlockRegistry.WINDOW_1X1.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WINDOW_1X2_ITEM =
        ITEMS.register("window_1x2",
            () -> new BlockItem(BlockRegistry.WINDOW_1X2.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WINDOW_1X3_ITEM =
        ITEMS.register("window_1x3",
            () -> new BlockItem(BlockRegistry.WINDOW_1X3.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WINDOW_2X2_ITEM =
        ITEMS.register("window_2x2",
            () -> new BlockItem(BlockRegistry.WINDOW_2X2.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WINDOW_2X3_ITEM =
        ITEMS.register("window_2x3",
            () -> new BlockItem(BlockRegistry.WINDOW_2X3.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WINDOW_CROSS_1X1_ITEM =
        ITEMS.register("window_cross_1x1",
            () -> new BlockItem(BlockRegistry.WINDOW_CROSS_1X1.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WINDOW_CROSS_1X2_ITEM =
        ITEMS.register("window_cross_1x2",
            () -> new BlockItem(BlockRegistry.WINDOW_CROSS_1X2.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WINDOW_CROSS_1X3_ITEM =
        ITEMS.register("window_cross_1x3",
            () -> new BlockItem(BlockRegistry.WINDOW_CROSS_1X3.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WINDOW_CROSS_2X2_ITEM =
        ITEMS.register("window_cross_2x2",
            () -> new BlockItem(BlockRegistry.WINDOW_CROSS_2X2.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WINDOW_CROSS_2X3_ITEM =
        ITEMS.register("window_cross_2x3",
            () -> new BlockItem(BlockRegistry.WINDOW_CROSS_2X3.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> WINDOW_BIRCH_1X1_ITEM =
        ITEMS.register("window_birch_1x1",
            () -> new BlockItem(BlockRegistry.WINDOW_BIRCH_1X1.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> LOG_WALL_ITEM =
        ITEMS.register("log_wall",
            () -> new BlockItem(BlockRegistry.LOG_WALL.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> STONE_FLOOR_ITEM =
        ITEMS.register("stone_floor",
            () -> new BlockItem(BlockRegistry.STONE_FLOOR.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> STONE_WALL_BOTTOM_ITEM =
        ITEMS.register("stone_wall_bottom",
            () -> new BlockItem(BlockRegistry.STONE_WALL_BOTTOM.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> STONE_WALL_TOP_ITEM =
        ITEMS.register("stone_wall_top",
            () -> new BlockItem(BlockRegistry.STONE_WALL_TOP.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> COBBLESTONE_WALL_BOTTOM_ITEM =
        ITEMS.register("cobblestone_wall_bottom",
            () -> new BlockItem(BlockRegistry.COBBLESTONE_WALL_BOTTOM.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> COBBLESTONE_WALL_TOP_ITEM =
        ITEMS.register("cobblestone_wall_top",
            () -> new BlockItem(BlockRegistry.COBBLESTONE_WALL_TOP.get(), new Item.Properties()));



public static final DeferredHolder<Item, BlockItem> OAK_WALL_BOTTOM_ITEM =
        ITEMS.register("oak_wall_bottom",
            () -> new BlockItem(BlockRegistry.OAK_WALL_BOTTOM.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> OAK_WALL_TOP_ITEM =
        ITEMS.register("oak_wall_top",
            () -> new BlockItem(BlockRegistry.OAK_WALL_TOP.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> BRICK_WALL_BOTTOM_ITEM =
        ITEMS.register("brick_wall_bottom",
            () -> new BlockItem(BlockRegistry.BRICK_WALL_BOTTOM.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> BRICK_WALL_TOP_ITEM =
        ITEMS.register("brick_wall_top",
            () -> new BlockItem(BlockRegistry.BRICK_WALL_TOP.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> PLASTER_STONE_FOUNDATION_ITEM =
        ITEMS.register("plaster_stone_foundation",
            () -> new BlockItem(BlockRegistry.PLASTER_STONE_FOUNDATION.get(), new Item.Properties()));



public static final DeferredHolder<Item, BlockItem> PLASTER_STONE_WALL_BOTTOM_ITEM =
        ITEMS.register("plaster_stone_wall_bottom",
            () -> new BlockItem(BlockRegistry.PLASTER_STONE_WALL_BOTTOM.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> PLASTER_STONE_WALL_TOP_ITEM =
        ITEMS.register("plaster_stone_wall_top",
            () -> new BlockItem(BlockRegistry.PLASTER_STONE_WALL_TOP.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> PLASTER_WOOD_FOUNDATION_ITEM =
        ITEMS.register("plaster_wood_foundation",
            () -> new BlockItem(BlockRegistry.PLASTER_WOOD_FOUNDATION.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> PLASTER_WOOD_WALL_BOTTOM_ITEM =
        ITEMS.register("plaster_wood_wall_bottom",
            () -> new BlockItem(BlockRegistry.PLASTER_WOOD_WALL_BOTTOM.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> PLASTER_WOOD_WALL_TOP_ITEM =
        ITEMS.register("plaster_wood_wall_top",
            () -> new BlockItem(BlockRegistry.PLASTER_WOOD_WALL_TOP.get(), new Item.Properties()));


public static final DeferredHolder<Item, Item> METAL_DOOR_ITEM = ITEMS.register(
    "metal_door_item",
    () -> new BlockItem(BlockRegistry.METAL_DOOR.get(), new Item.Properties().stacksTo(64))
);

// graveyard

public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_1_ITEM =
    ITEMS.register("gravestone_type_1", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_1.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_2_ITEM =
    ITEMS.register("gravestone_type_2", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_2.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_3_ITEM =
    ITEMS.register("gravestone_type_3", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_3.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_4_ITEM =
    ITEMS.register("gravestone_type_4", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_4.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_5_ITEM =
    ITEMS.register("gravestone_type_5", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_5.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_6_ITEM =
    ITEMS.register("gravestone_type_6", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_6.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_7_ITEM =
    ITEMS.register("gravestone_type_7", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_7.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_8_ITEM =
    ITEMS.register("gravestone_type_8", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_8.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_9_ITEM =
    ITEMS.register("gravestone_type_9", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_9.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_10_ITEM =
    ITEMS.register("gravestone_type_10", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_10.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_11_ITEM =
    ITEMS.register("gravestone_type_11", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_11.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_12_ITEM =
    ITEMS.register("gravestone_type_12", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_12.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_13_ITEM =
    ITEMS.register("gravestone_type_13", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_13.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_14_ITEM =
    ITEMS.register("gravestone_type_14", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_14.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_15_ITEM =
    ITEMS.register("gravestone_type_15", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_15.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVESTONE_TYPE_16_ITEM =
    ITEMS.register("gravestone_type_16", () -> new BlockItem(BlockRegistry.GRAVESTONE_TYPE_16.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> BROKEN_WOODEN_GRAVE_CROSS =
    ITEMS.register("broken_wooden_grave_cross", () -> new BlockItem(BlockRegistry.BROKEN_WOODEN_GRAVE_CROSS.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> DAMAGED_WOODEN_GRAVE_CROSS =
    ITEMS.register("damaged_wooden_grave_cross", () -> new BlockItem(BlockRegistry.DAMAGED_WOODEN_GRAVE_CROSS.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> DEAD_GRAVE_FLOWER_VASE =
    ITEMS.register("dead_grave_flower_vase", () -> new BlockItem(BlockRegistry.DEAD_GRAVE_FLOWER_VASE.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> DEAD_GRAVE_FLOWERS =
    ITEMS.register("dead_grave_flowers", () -> new BlockItem(BlockRegistry.DEAD_GRAVE_FLOWERS.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> GRAVE_FLOWERS =
    ITEMS.register("grave_flowers", () -> new BlockItem(BlockRegistry.GRAVE_FLOWERS.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> IRON_CEMETERY_GATE_ARCH =
    ITEMS.register("iron_cemetery_gate_arch", () -> new BlockItem(BlockRegistry.IRON_CEMETERY_GATE_ARCH.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> IRON_FENCE_1 =
    ITEMS.register("iron_fence_1", () -> new BlockItem(BlockRegistry.IRON_FENCE_1.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> IRON_FENCE_2 =
    ITEMS.register("iron_fence_2", () -> new BlockItem(BlockRegistry.IRON_FENCE_2.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> LYING_SKELETON =
    ITEMS.register("lying_skeleton", () -> new BlockItem(BlockRegistry.LYING_SKELETON.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> SITTING_SKELETON =
    ITEMS.register("sitting_skeleton", () -> new BlockItem(BlockRegistry.SITTING_SKELETON.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> SKELETON_ARM =
    ITEMS.register("skeleton_arm", () -> new BlockItem(BlockRegistry.SKELETON_ARM.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> SKELETON_LEG =
    ITEMS.register("skeleton_leg", () -> new BlockItem(BlockRegistry.SKELETON_LEG.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> SKELETON_TORSO =
    ITEMS.register("skeleton_torso", () -> new BlockItem(BlockRegistry.SKELETON_TORSO.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> WILTED_GRAVE_FLOWER_VASE =
    ITEMS.register("wilted_grave_flower_vase", () -> new BlockItem(BlockRegistry.WILTED_GRAVE_FLOWER_VASE.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> WILTED_GRAVE_FLOWERS =
    ITEMS.register("wilted_grave_flowers", () -> new BlockItem(BlockRegistry.WILTED_GRAVE_FLOWERS.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> WOODEN_COFFIN_LID =
    ITEMS.register("wooden_coffin_lid", () -> new BlockItem(BlockRegistry.WOODEN_COFFIN_LID.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> WOODEN_COFFIN_SKELETON =
    ITEMS.register("wooden_coffin_skeleton", () -> new BlockItem(BlockRegistry.WOODEN_COFFIN_SKELETON.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> WOODEN_COFFIN =
    ITEMS.register("wooden_coffin", () -> new BlockItem(BlockRegistry.WOODEN_COFFIN.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> WOODEN_GRAVE_CROSS =
    ITEMS.register("wooden_grave_cross", () -> new BlockItem(BlockRegistry.WOODEN_GRAVE_CROSS.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> WOODEN_OPEN_COFFIN =
    ITEMS.register("wooden_open_coffin", () -> new BlockItem(BlockRegistry.WOODEN_OPEN_COFFIN.get(), new Item.Properties()));


// Ingots / Metals

        public static final DeferredHolder<Item, Item> SHADOW_IRON_INGOT = ITEMS.register("shadow_iron_ingot", () ->
        new Item(new Item.Properties())
        );
        public static final DeferredHolder<Item, Item> VALORITE_INGOT = ITEMS.register("valorite_ingot", () ->
        new Item(new Item.Properties())
        );
        public static final DeferredHolder<Item, Item> VERITE_INGOT = ITEMS.register("verite_ingot", () ->
        new Item(new Item.Properties())
        );
        public static final DeferredHolder<Item, Item> AGAPITE_INGOT = ITEMS.register("agapite_ingot", () ->
        new Item(new Item.Properties())
        );
        public static final DeferredHolder<Item, Item> COPPER_INGOT = ITEMS.register("copper_ingot", () ->
        new Item(new Item.Properties())
        );
        public static final DeferredHolder<Item, Item> SILVER_INGOT = ITEMS.register("silver_ingot", () ->
        new Item(new Item.Properties())
        );
        public static final DeferredHolder<Item, Item> TIN_INGOT = ITEMS.register("tin_ingot", () ->
        new Item(new Item.Properties())
        );


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

    
    public static final DeferredHolder<Item, DeferredSpawnEggItem> RAT_SPAWN_EGG = ITEMS.register(
            "rat_spawn_egg",
            () -> new DeferredSpawnEggItem(
                    EntityRegistry.RAT_ENTITY,
                    0x2a7700,
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
        return shield;
    });



    // Register the Purity Iron Ore item
    public static final DeferredHolder<Item, PurityOreItem> PURITY_ORE_ITEM = ITEMS.register("purity_ore_item", () ->
        new PurityOreItem(
            new Item.Properties()
                .stacksTo(1) // Default stack size
        )
    );

        // Register the Graded Stone Ore item
    public static final DeferredHolder<Item, GradeStoneItem> GRADE_STONE_ITEM = ITEMS.register("grade_stone_item", () ->
        new GradeStoneItem(
            new Item.Properties()
                .stacksTo(1) // Default stack size
        )
    );

public static final DeferredHolder<Item, TwoHandedAxeItem> TWO_HANDED_AXE = ITEMS.register("two_handed_axe", () ->
    new TwoHandedAxeItem(
        ModToolTiers.TWO_HANDED_AXE_TIER, // Custom tier
        new Item.Properties()
            .stacksTo(1)
            .attributes(TwoHandedAxeItem.createAttributes()) // Use the attribute supplier
    )
);

    // ADD THIS for WeightedWoodItem:
    public static final DeferredHolder<Item, Item> WEIGHTED_WOOD_ITEM = ITEMS.register(
        "weighted_wood_item",
        () -> new WeightedWoodItem(new Item.Properties())
    );
    
    // Block Items

 public static final DeferredHolder<Item, Item> HOUSE_SIGN_BLOCK_ITEM = ITEMS.register(
            "house_sign", () -> new BlockItem(BlockRegistry.HOUSE_SIGN_BLOCK.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> SHADE_SPAWN_BLOCK_ITEM = ITEMS.register(
            "shade_spawn_block", () -> new BlockItem(BlockRegistry.SHADE_SPAWN_BLOCK.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> LICH_SPAWN_BLOCK_ITEM = ITEMS.register(
            "lich_spawn_block", () -> new BlockItem(BlockRegistry.LICH_SPAWN_BLOCK.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> WOOD_SPAWN_BLOCK_ITEM = ITEMS.register(
            "wood_spawn_block", () -> new BlockItem(BlockRegistry.WOOD_SPAWN_BLOCK.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> METAL_SPAWN_BLOCK_ITEM = ITEMS.register(
            "metal_spawn_block", () -> new BlockItem(BlockRegistry.METAL_SPAWN_BLOCK.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> STONE_SPAWN_BLOCK_ITEM = ITEMS.register(
            "stone_spawn_block", () -> new BlockItem(BlockRegistry.STONE_SPAWN_BLOCK.get(), new Item.Properties()));


        public static final DeferredHolder<Item, Item> FISH_SPAWN_BLOCK_ITEM = ITEMS.register(
            "fish_spawn_block", () -> new BlockItem(BlockRegistry.FISH_SPAWN_BLOCK.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> HORSE_SPAWN_BLOCK_ITEM = ITEMS.register(
            "horse_spawn_block", () -> new BlockItem(BlockRegistry.HORSE_SPAWN_BLOCK.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> BLACKSMITH_SPAWN_BLOCK_ITEM = ITEMS.register(
            "blacksmith_spawn_block", () -> new BlockItem(BlockRegistry.BLACKSMITH_SPAWN_BLOCK.get(), new Item.Properties()));

// teleporters

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

public static final DeferredHolder<Item, Item> CARPET_TELEPORTER_BLOCK_ITEM = ITEMS.register(
    "carpet_teleporter_block", () ->
        new CarpetTeleporterItem(new Item.Properties()));

// end of teleporters
    public static final DeferredHolder<Item, Item> LARGE_FORGE_BLOCK_ENTITY = ITEMS.register(
            "large_forge_item", () -> new BlockItem(BlockRegistry.LARGE_FORGE_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> SMALL_FORGE_BLOCK_ENTITY = ITEMS.register(
            "small_forge_item", () -> new BlockItem(BlockRegistry.SMALL_FORGE_BLOCK.get(), new Item.Properties()));

// decorations

    public static final DeferredHolder<Item, Item> LAMP_POST_REGULAR_ITEM = ITEMS.register(
            "lamp_post_regular", () -> new BlockItem(BlockRegistry.LAMP_POST_REGULAR.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> LAMP_POST_FANCY_ITEM = ITEMS.register(
            "lamp_post_fancy", () -> new BlockItem(BlockRegistry.LAMP_POST_FANCY.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> CANDELABRA_SMALL_ITEM = ITEMS.register(
            "candelabra_small", () -> new BlockItem(BlockRegistry.CANDELABRA_SMALL.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> CANDELABRA_TALL_ITEM = ITEMS.register(
            "candelabra_tall", () -> new BlockItem(BlockRegistry.CANDELABRA_TALL.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> TORCH_WALL_ITEM = ITEMS.register(
            "torch_wall", () -> new BlockItem(BlockRegistry.TORCH_WALL.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> TORCH_STANDING_ITEM = ITEMS.register(
            "torch_standing", () -> new BlockItem(BlockRegistry.TORCH_STANDING.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> BRAZIER_SMALL_ITEM = ITEMS.register(
            "brazier_small", () -> new BlockItem(BlockRegistry.BRAZIER_SMALL.get(), new Item.Properties()));

// Furniture

    public static final DeferredHolder<Item, Item> DOUBLE_BED_ITEM =
            ITEMS.register("double_bed",
                    () -> new BlockItem(BlockRegistry.DOUBLE_BED.get(),
                                        new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> MAGINCIA_STYLE_THRONE_ITEM = ITEMS.register(
            "magincia_style_throne", () -> new BlockItem(BlockRegistry.MAGINCIA_STYLE_THRONE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> LORD_BRITISH_THRONE_ITEM = ITEMS.register(
            "lord_british_throne", () -> new BlockItem(BlockRegistry.LORD_BRITISH_THRONE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> YEW_TABLE_ITEM = ITEMS.register(
            "yew_table", () -> new BlockItem(BlockRegistry.YEW_TABLE.get(), new Item.Properties()));

        // Custom Ore Items
        public static final DeferredHolder<Item, Item> COPPER_ORE_ITEM = ITEMS.register(
                "copper_ore", () -> new BlockItem(BlockRegistry.COPPER_ORE.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> TIN_ORE_ITEM = ITEMS.register(
                "tin_ore", () -> new BlockItem(BlockRegistry.TIN_ORE.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> SILVER_ORE_ITEM = ITEMS.register(
                "silver_ore", () -> new BlockItem(BlockRegistry.SILVER_ORE.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> GOLD_ORE_ITEM = ITEMS.register(
                "gold_ore", () -> new BlockItem(BlockRegistry.GOLD_ORE.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> SHADOW_IRON_ORE_ITEM = ITEMS.register(
                "shadow_iron_ore", () -> new BlockItem(BlockRegistry.SHADOW_IRON_ORE.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> AGAPITE_ORE_ITEM = ITEMS.register(
                "agapite_ore", () -> new BlockItem(BlockRegistry.AGAPITE_ORE.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> VERITE_ORE_ITEM = ITEMS.register(
                "verite_ore", () -> new BlockItem(BlockRegistry.VERITE_ORE.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> VALORITE_ORE_ITEM = ITEMS.register(
                "valorite_ore", () -> new BlockItem(BlockRegistry.VALORITE_ORE.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> HIGH_PURITY_SILVER_ORE_ITEM = ITEMS.register(
                "high_purity_silver_ore", () -> new BlockItem(BlockRegistry.HIGH_PURITY_SILVER_ORE.get(), new Item.Properties()));

        // Custom Rock Items
        public static final DeferredHolder<Item, Item> IGNEOUS_ROCK_ITEM = ITEMS.register(
                "igneous_rock", () -> new BlockItem(BlockRegistry.IGNEOUS_ROCK.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> METAMORPHIC_ROCK_ITEM = ITEMS.register(
                "metamorphic_rock", () -> new BlockItem(BlockRegistry.METAMORPHIC_ROCK.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> VOLCANIC_ROCK_ITEM = ITEMS.register(
                "volcanic_rock", () -> new BlockItem(BlockRegistry.VOLCANIC_ROCK.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> GLACIAL_ROCK_ITEM = ITEMS.register(
                "glacial_rock", () -> new BlockItem(BlockRegistry.GLACIAL_ROCK.get(), new Item.Properties()));


    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
