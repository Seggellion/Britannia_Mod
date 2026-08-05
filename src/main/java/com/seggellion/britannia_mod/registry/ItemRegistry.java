// ItemRegistry.java
package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.ModSounds;
// import com.seggellion.britannia_mod.item.SmallWoodHouseDeedItem;
import com.seggellion.britannia_mod.registry.SignItemRegistry;
import com.seggellion.britannia_mod.item.InstrumentItem;
import com.seggellion.britannia_mod.item.GrapesItem;
import com.seggellion.britannia_mod.item.CookedFishSteakItem;
import com.seggellion.britannia_mod.item.WeightedCommodityItem;
import com.seggellion.britannia_mod.item.WeightedCookedFoodItem;
import com.seggellion.britannia_mod.item.BlueTentDeedItem;
import com.seggellion.britannia_mod.item.PurpleTentDeedItem;
import com.seggellion.britannia_mod.item.DeedItem;
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
import com.seggellion.britannia_mod.item.InteriorDecoratorToolItem;
import com.seggellion.britannia_mod.item.BritanniaPickaxeItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import com.seggellion.britannia_mod.item.MaterialQualityJewelryItem;
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
import com.seggellion.britannia_mod.item.HouseKeyItem;
import com.seggellion.britannia_mod.item.ChestKeyItem;
import com.seggellion.britannia_mod.item.PitcherJuiceItem;
import com.seggellion.britannia_mod.item.WineBottleItem;
import net.minecraft.world.item.ItemNameBlockItem;
    import net.minecraft.world.item.HoeItem;
import com.seggellion.britannia_mod.item.GrapeSeedsItem;
import com.seggellion.britannia_mod.item.WineBottleBlockItem;
import com.seggellion.britannia_mod.item.BlackSmithsHammerItem;
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
import net.minecraft.world.item.Items;

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
import java.util.HashMap;


import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;


public class ItemRegistry {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
            net.minecraft.core.registries.Registries.ITEM, "britannia_mod");
  private static final Logger LOGGER = LogUtils.getLogger();

    private static Item.Properties food(int nutrition, float saturation) {
        return new Item.Properties().food(
                new net.minecraft.world.food.FoodProperties.Builder()
                        .nutrition(nutrition)
                        .saturationModifier(saturation)
                        .build()
        );
    }

    private static DeferredHolder<Item, WeightedCommodityItem> weightedCommodity(String id) {
        return ITEMS.register(id, () -> new WeightedCommodityItem(new Item.Properties()));
    }

    private static DeferredHolder<Item, WeightedCookedFoodItem> cookedFood(
            String id,
            int nutrition,
            float saturation,
            String foodType,
            double restoreMultiplier
    ) {
        return ITEMS.register(id, () ->
                new WeightedCookedFoodItem(food(1, saturation), foodType, restoreMultiplier));
    }

    // General Items
    public static final DeferredHolder<Item, Item> GOLD_COIN = ITEMS.register("gold_coin",
            () -> new Item(new Item.Properties().stacksTo(99)));
        public static final DeferredHolder<Item, Item> COPPER_COIN = ITEMS.register("copper_coin",
            () -> new Item(new Item.Properties().stacksTo(99)));
        public static final DeferredHolder<Item, Item> SILVER_COIN = ITEMS.register("silver_coin",
            () -> new Item(new Item.Properties().stacksTo(99)));
    public static final DeferredHolder<Item, Item> LOCKPICK_TOOLS = ITEMS.register("lockpick_tools",
            () -> new Item(new Item.Properties().stacksTo(64)));
    
    // Quality Jewlery

    // ItemRegistry.java (snippet)
public static final DeferredHolder<Item, Item> COPPER_NECKLACE = ITEMS.register("copper_necklace",
    () -> new MaterialQualityJewelryItem(MaterialQualityJewelryItem.JewelryType.NECKLACE,
        MaterialQualityJewelryItem.UOMaterial.COPPER,
        new Item.Properties()));

public static final DeferredHolder<Item, Item> COPPER_NECKLACE_2 = ITEMS.register("copper_necklace_2",
    () -> new MaterialQualityJewelryItem(MaterialQualityJewelryItem.JewelryType.NECKLACE,
        MaterialQualityJewelryItem.UOMaterial.COPPER,
        new Item.Properties()));

public static final DeferredHolder<Item, Item> COPPER_EARRINGS = ITEMS.register("copper_earrings",
    () -> new MaterialQualityJewelryItem(MaterialQualityJewelryItem.JewelryType.EARRINGS,
        MaterialQualityJewelryItem.UOMaterial.COPPER,
        new Item.Properties()));

public static final DeferredHolder<Item, Item> COPPER_BRACELET = ITEMS.register("copper_bracelet",
    () -> new MaterialQualityJewelryItem(MaterialQualityJewelryItem.JewelryType.BRACELET,
        MaterialQualityJewelryItem.UOMaterial.COPPER,
        new Item.Properties()));

public static final DeferredHolder<Item, Item> COPPER_RING = ITEMS.register("copper_ring",
    () -> new MaterialQualityJewelryItem(MaterialQualityJewelryItem.JewelryType.RING,
        MaterialQualityJewelryItem.UOMaterial.COPPER,
        new Item.Properties()));

public static final DeferredHolder<Item, Item> COPPER_BEADS = ITEMS.register("copper_beads",
    () -> new MaterialQualityJewelryItem(MaterialQualityJewelryItem.JewelryType.BEADS,
        MaterialQualityJewelryItem.UOMaterial.COPPER,
        new Item.Properties()));

        // Tools

public static final DeferredHolder<Item, Item> BLACKSMITH_HAMMER = ITEMS.register("blacksmith_hammer", 
        () -> new BlackSmithsHammerItem(new Item.Properties().stacksTo(1))
);


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

// quest items
  public static final DeferredHolder<Item, Item> ONE_RING = ITEMS.register("one_ring",
            () -> new Item(new Item.Properties()));
  public static final DeferredHolder<Item, Item> BRITANNIA_RACE_MAP = ITEMS.register("britannia_race_map",
            () -> new Item(new Item.Properties()));

/* =========================================================
       MUSICAL INSTRUMENTS
       Trigger "musicianship" skill gain on right-click.
       ========================================================= */

    // 1. Lap Harp
    public static final DeferredHolder<Item, Item> LAP_HARP = ITEMS.register("lap_harp",
            () -> new InstrumentItem(
                    new Item.Properties().stacksTo(1),
                    ModSounds.LAP_HARP_PLAY, // You must define these in ModSounds
                    ModSounds.LAP_HARP_FAIL
            ));

    // 3. Tamborine
    public static final DeferredHolder<Item, Item> TAMBORINE = ITEMS.register("tamborine",
            () -> new InstrumentItem(
                    new Item.Properties().stacksTo(1),
                    ModSounds.TAMBORINE_PLAY,
                    ModSounds.TAMBORINE_FAIL
            ));

                public static final DeferredHolder<Item, Item> TAMBORINE_RIBBON = ITEMS.register("tamborine_ribbon",
            () -> new InstrumentItem(
                    new Item.Properties().stacksTo(1),
                    ModSounds.TAMBORINE_PLAY,
                    ModSounds.TAMBORINE_FAIL
            ));

    // 3. Lute
    public static final DeferredHolder<Item, Item> LUTE = ITEMS.register("lute",
            () -> new InstrumentItem(
                    new Item.Properties().stacksTo(1),
                    ModSounds.LUTE_PLAY,
                    ModSounds.LUTE_FAIL
            ));

    // 4. Drums
    public static final DeferredHolder<Item, Item> DRUMS = ITEMS.register("drums",
            () -> new InstrumentItem(
                    new Item.Properties().stacksTo(1),
                    ModSounds.DRUM_PLAY,
                    ModSounds.DRUM_FAIL
            ));

    // 5. Violin
    public static final DeferredHolder<Item, Item> VIOLIN = ITEMS.register("violin",
            () -> new InstrumentItem(
                    new Item.Properties().stacksTo(1),
                    ModSounds.VIOLIN_PLAY,
                    ModSounds.VIOLIN_FAIL
            ));

public static final DeferredHolder<Item, GrapesItem> GRAPES = ITEMS.register("grapes",
    () -> new GrapesItem(new Item.Properties().food( 
            new net.minecraft.world.food.FoodProperties.Builder().nutrition(2).saturationModifier(0.3f).build()
    )));

public static final DeferredHolder<Item, WeightedCookedFoodItem> BREAD = cookedFood(
        "bread", 5, 0.6f, "grain", WeightedCookedFoodItem.GRAIN_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> RICE_BREAD = cookedFood(
        "rice_bread", 5, 0.6f, "grain", WeightedCookedFoodItem.GRAIN_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> OAT_BREAD = cookedFood(
        "oat_bread", 5, 0.6f, "grain", WeightedCookedFoodItem.GRAIN_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> BARLEY_BREAD = cookedFood(
        "barley_bread", 5, 0.6f, "grain", WeightedCookedFoodItem.GRAIN_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> RYE_BREAD = cookedFood(
        "rye_bread", 5, 0.6f, "grain", WeightedCookedFoodItem.GRAIN_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> SORGHUM_BREAD = cookedFood(
        "sorghum_bread", 5, 0.6f, "grain", WeightedCookedFoodItem.GRAIN_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> QUINOA_BREAD = cookedFood(
        "quinoa_bread", 5, 0.6f, "grain", WeightedCookedFoodItem.GRAIN_MULTIPLIER);

public static final DeferredHolder<Item, WeightedCookedFoodItem> COOKED_CHICKEN = cookedFood(
        "cooked_chicken", 5, 0.6f, "chicken", WeightedCookedFoodItem.CHICKEN_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> CHICKEN_LEG = cookedFood(
        "chicken_leg", 4, 0.5f, "chicken", WeightedCookedFoodItem.CHICKEN_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> COOKED_CHICKEN_BREAST = cookedFood(
        "cooked_chicken_breast", 5, 0.6f, "chicken", WeightedCookedFoodItem.CHICKEN_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> COOKED_CHICKEN_WING = cookedFood(
        "cooked_chicken_wing", 3, 0.4f, "chicken", WeightedCookedFoodItem.CHICKEN_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> COOKED_BIRD = cookedFood(
        "cooked_bird", 6, 0.7f, "bird", WeightedCookedFoodItem.BIRD_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> CUT_OF_RIBS = cookedFood(
        "cut_of_ribs", 7, 0.8f, "pork", WeightedCookedFoodItem.PORK_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> BEEF_RIBS = cookedFood(
        "beef_ribs", 7, 0.8f, "beef", WeightedCookedFoodItem.BEEF_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> BEEF_BRISKET = cookedFood(
        "beef_brisket", 8, 0.9f, "beef", WeightedCookedFoodItem.BEEF_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> HAM = cookedFood(
        "ham", 7, 0.8f, "pork", WeightedCookedFoodItem.PORK_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> LEG_OF_LAMB = cookedFood(
        "leg_of_lamb", 7, 0.8f, "lamb", WeightedCookedFoodItem.LAMB_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> ROAST_PIG = cookedFood(
        "roast_pig", 8, 0.9f, "pork", WeightedCookedFoodItem.PORK_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> SLICE_OF_BACON = cookedFood(
        "slice_of_bacon", 4, 0.5f, "pork", WeightedCookedFoodItem.PORK_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> SAUSAGE = cookedFood(
        "sausage", 6, 0.7f, "pork", WeightedCookedFoodItem.PORK_MULTIPLIER);
public static final DeferredHolder<Item, CookedFishSteakItem> COOKED_FISH_STEAK = ITEMS.register("cooked_fish_steak",
        () -> new CookedFishSteakItem(food(1, 0.8f)));

public static final DeferredHolder<Item, WeightedCookedFoodItem> APPLE = cookedFood(
        "apple", 4, 0.3f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> BANANA = cookedFood(
        "banana", 4, 0.4f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> CONCORD_GRAPES = cookedFood(
        "concord_grapes", 3, 0.3f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> PEACHES = cookedFood(
        "peaches", 4, 0.4f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> PEARS = cookedFood(
        "pears", 4, 0.4f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> SQUASH = cookedFood(
        "squash", 3, 0.3f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> CARROTS = cookedFood(
        "carrots", 3, 0.4f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> CORN = cookedFood(
        "corn", 3, 0.4f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> CABBAGE = cookedFood(
        "cabbage", 3, 0.3f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> LETTUCE = cookedFood(
        "lettuce", 2, 0.2f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> ONION = cookedFood(
        "onion", 2, 0.2f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> PUMPKIN = cookedFood(
        "pumpkin", 3, 0.3f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> SWEET_PEPPER = cookedFood(
        "sweet_pepper", 3, 0.3f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> BERRIES = cookedFood(
        "berries", 2, 0.2f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> POTATO = cookedFood(
        "potato", 2, 0.2f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);
public static final DeferredHolder<Item, WeightedCookedFoodItem> TOMATO = cookedFood(
        "tomato", 3, 0.3f, "produce", WeightedCookedFoodItem.PRODUCE_MULTIPLIER);

public static final DeferredHolder<Item, Item> BARLEY = ITEMS.register("barley",
        () -> new Item(new Item.Properties()));
public static final DeferredHolder<Item, Item> OATS = ITEMS.register("oats",
        () -> new Item(new Item.Properties()));
public static final DeferredHolder<Item, Item> RYE = ITEMS.register("rye",
        () -> new Item(new Item.Properties()));
public static final DeferredHolder<Item, Item> FLOUR = ITEMS.register("flour",
        () -> new Item(new Item.Properties()));
public static final DeferredHolder<Item, Item> OAT_FLOUR = ITEMS.register("oat_flour",
        () -> new Item(new Item.Properties()));
public static final DeferredHolder<Item, Item> RYE_FLOUR = ITEMS.register("rye_flour",
        () -> new Item(new Item.Properties()));

public static final DeferredHolder<Item, WeightedCommodityItem> RAW_PORK = weightedCommodity("raw_pork");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_PORK_RIBS = weightedCommodity("raw_pork_ribs");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_PORK_BELLY = weightedCommodity("raw_pork_belly");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_PORK_SHOULDER = weightedCommodity("raw_pork_shoulder");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_BEEF = weightedCommodity("raw_beef");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_BEEF_RIBS = weightedCommodity("raw_beef_ribs");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_BEEF_STEAK = weightedCommodity("raw_beef_steak");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_BRISKET = weightedCommodity("raw_brisket");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_CHICKEN = weightedCommodity("raw_chicken");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_CHICKEN_LEG = weightedCommodity("raw_chicken_leg");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_CHICKEN_BREAST = weightedCommodity("raw_chicken_breast");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_CHICKEN_WING = weightedCommodity("raw_chicken_wing");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_LAMB = weightedCommodity("raw_lamb");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_LAMB_CHOP = weightedCommodity("raw_lamb_chop");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_LEG_OF_LAMB = weightedCommodity("raw_leg_of_lamb");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_TURKEY = weightedCommodity("raw_turkey");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_TURKEY_LEG = weightedCommodity("raw_turkey_leg");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_TURKEY_BREAST = weightedCommodity("raw_turkey_breast");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_VENISON = weightedCommodity("raw_venison");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_VENISON_HAUNCH = weightedCommodity("raw_venison_haunch");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_VENISON_STEAK = weightedCommodity("raw_venison_steak");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_RABBIT = weightedCommodity("raw_rabbit");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_RABBIT_LEG = weightedCommodity("raw_rabbit_leg");
public static final DeferredHolder<Item, WeightedCommodityItem> ANIMAL_FAT = weightedCommodity("animal_fat");
public static final DeferredHolder<Item, Item> CHICKEN_EGG = ITEMS.register("chicken_egg",
        () -> new Item(new Item.Properties()));
public static final DeferredHolder<Item, Item> MILK = ITEMS.register("milk",
        () -> new Item(new Item.Properties()));
public static final DeferredHolder<Item, Item> CHEESE = ITEMS.register("cheese",
        () -> new Item(food(4, 0.4f)));
public static final DeferredHolder<Item, Item> BUTTER = ITEMS.register("butter",
        () -> new Item(food(2, 0.2f)));
public static final DeferredHolder<Item, WeightedCommodityItem> RABBIT_PELT = weightedCommodity("rabbit_pelt");
public static final DeferredHolder<Item, WeightedCommodityItem> WOLF_PELT = weightedCommodity("wolf_pelt");
public static final DeferredHolder<Item, WeightedCommodityItem> BEAR_PELT = weightedCommodity("bear_pelt");
public static final DeferredHolder<Item, WeightedCommodityItem> DEER_HIDE = weightedCommodity("deer_hide");
public static final DeferredHolder<Item, WeightedCommodityItem> RAW_HIDE = weightedCommodity("raw_hide");
public static final DeferredHolder<Item, Item> TANNED_LEATHER = ITEMS.register("tanned_leather",
        () -> new Item(new Item.Properties()));
    
public static final DeferredHolder<Item, Item> FARMING_BLOCK_ITEM = ITEMS.register("farming_block",
            () -> new net.minecraft.world.item.BlockItem(BlockRegistry.FARMING_BLOCK.get(), new Item.Properties()));

    // 2. Grape Seeds (Connects to the Block)
public static final DeferredHolder<Item, GrapeSeedsItem> GRAPE_SEEDS = ITEMS.register("grape_seeds",
    () -> new GrapeSeedsItem(new Item.Properties()));

    // The Juice Press Output
// Keep this one as a standard BlockItem (so clicking air with an empty pitcher does nothing)
    public static final DeferredHolder<Item, BlockItem> PITCHER_EMPTY = ITEMS.register("pitcher_empty",
            () -> new BlockItem(BlockRegistry.PITCHER_EMPTY_BLOCK.get(), new Item.Properties().stacksTo(1)));

    // Update these to use your new PitcherJuiceItem class
    public static final DeferredHolder<Item, BlockItem> PITCHER_RED_GRAPE_JUICE = ITEMS.register("pitcher_red_grape_juice",
            () -> new PitcherJuiceItem(BlockRegistry.PITCHER_RED_GRAPE_JUICE_BLOCK.get(), new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, BlockItem> PITCHER_WHITE_GRAPE_JUICE = ITEMS.register("pitcher_white_grape_juice",
            () -> new PitcherJuiceItem(BlockRegistry.PITCHER_WHITE_GRAPE_JUICE_BLOCK.get(), new Item.Properties().stacksTo(1)));



    // 4. Winery Hoe
    public static final DeferredHolder<Item, Item> VINTNER_HOE = ITEMS.register("vintner_hoe",
            () -> new HoeItem(Tiers.IRON, new Item.Properties().attributes(
                    HoeItem.createAttributes(Tiers.IRON, -2.0F, -1.0F))));

    // 6. Block Items (To place the Press and Barrel)
    public static final DeferredHolder<Item, Item> JUICE_PRESS_ITEM = ITEMS.register("juice_press",
            () -> new net.minecraft.world.item.BlockItem(BlockRegistry.JUICE_PRESS.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> WINE_BARREL_ITEM = ITEMS.register("wine_barrel",
            () -> new net.minecraft.world.item.BlockItem(BlockRegistry.WINE_BARREL.get(), new Item.Properties()));

// Note: We register this as a WineBottleBlockItem, passing the BLOCK from above
// Green Bottle
public static final DeferredHolder<Item, WineBottleBlockItem> WINE_BOTTLE_GREEN = ITEMS.register("wine_bottle_green",
        () -> new WineBottleBlockItem(BlockRegistry.WINE_BOTTLE_GREEN_BLOCK.get(), new Item.Properties().stacksTo(16)));

// Brown Bottle
public static final DeferredHolder<Item, WineBottleBlockItem> WINE_BOTTLE_BROWN = ITEMS.register("wine_bottle_brown",
        () -> new WineBottleBlockItem(BlockRegistry.WINE_BOTTLE_BROWN_BLOCK.get(), new Item.Properties().stacksTo(16)));

// Blue Bottle
public static final DeferredHolder<Item, WineBottleBlockItem> WINE_BOTTLE_BLUE = ITEMS.register("wine_bottle_blue",
        () -> new WineBottleBlockItem(BlockRegistry.WINE_BOTTLE_BLUE_BLOCK.get(), new Item.Properties().stacksTo(16)));


// Clear Bottle
public static final DeferredHolder<Item, WineBottleBlockItem> WINE_BOTTLE_CLEAR = ITEMS.register("wine_bottle_clear",
        () -> new WineBottleBlockItem(BlockRegistry.WINE_BOTTLE_CLEAR_BLOCK.get(), new Item.Properties().stacksTo(16)));

    // Tools
        public static final DeferredHolder<Item, Item> SCISSORS = ITEMS.register("scissors",
                () -> new net.minecraft.world.item.ShearsItem(new Item.Properties().durability(238))); 

        public static final DeferredHolder<Item, Item> TURQUOISE_POWDER = ITEMS.register("turquoise_powder",
                () -> new Item(new Item.Properties()));
                
        public static final DeferredHolder<Item, Item> TRELLIS_ITEM = ITEMS.register("trellis",
                () -> new net.minecraft.world.item.BlockItem(com.seggellion.britannia_mod.registry.BlockRegistry.TRELLIS_BLOCK.get(), new Item.Properties()));


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

    public static final DeferredHolder<Item, BlockItem> CAVE_FLOOR_ITEM = ITEMS.register(
        "cave_floor",
        () -> new BlockItem(BlockRegistry.CAVE_FLOOR_BLOCK.get(), new Item.Properties())
    );

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

// ✅ Register matching block item for your Cobblestone Wall
    public static final DeferredHolder<Item, Item> COBBLESTONE_WALL_ITEM =
        ITEMS.register("cobblestone_wall", () ->
            new BlockItem(BlockRegistry.COBBLESTONE_WALL.get(), new Item.Properties()));

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

public static final DeferredHolder<Item, BlockItem> BRICK_FOUNDATION_SANDSTONE_ITEM =
        ITEMS.register("brick_foundation_sandstone",
            () -> new BlockItem(BlockRegistry.BRICK_FOUNDATION_SANDSTONE.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> BRICK_FOUNDATION_DARK_SANDSTONE_ITEM =
        ITEMS.register("brick_foundation_dark_sandstone",
            () -> new BlockItem(BlockRegistry.BRICK_FOUNDATION_DARK_SANDSTONE.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> BRICK_FOUNDATION_SPRUCE_ITEM =
        ITEMS.register("brick_foundation_spruce",
            () -> new BlockItem(BlockRegistry.BRICK_FOUNDATION_SPRUCE.get(), new Item.Properties()));

public static final DeferredHolder<Item, Item> CUSTOM_STONE_STAIRS_ITEM = ITEMS.register(
    "custom_stone_stairs",
    () -> new BlockItem(BlockRegistry.CUSTOM_STONE_STAIRS.get(), new Item.Properties())
);

public static final DeferredHolder<Item, Item> DARK_STONE_STAIRS_ITEM = ITEMS.register(
    "dark_stone_stairs",
    () -> new BlockItem(BlockRegistry.DARK_STONE_STAIRS.get(), new Item.Properties())
);

public static final DeferredHolder<Item, Item> WALNUT_STAIRS_ITEM = ITEMS.register(
    "walnut_stairs",
    () -> new BlockItem(BlockRegistry.WALNUT_STAIRS.get(), new Item.Properties())
);


public static final DeferredHolder<Item, BlockItem> STONE_FLOOR_POLISHED_ITEM =
    ITEMS.register("stone_floor_polished",
        () -> new BlockItem(BlockRegistry.STONE_FLOOR_POLISHED.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> STONE_FOUNDATION_ITEM =
        ITEMS.register("stone_foundation",
            () -> new BlockItem(BlockRegistry.STONE_FOUNDATION.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> COBBLESTONE_FOUNDATION_ITEM =
        ITEMS.register("cobblestone_foundation",
            () -> new BlockItem(BlockRegistry.COBBLESTONE_FOUNDATION.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> BIRCH_WALL_ITEM =
        ITEMS.register("birch_wall",
            () -> new BlockItem(BlockRegistry.BIRCH_WALL.get(), new Item.Properties()));


    public static final DeferredHolder<Item, Item> BIRCH_WALL_BLOCK_ITEM =
        ITEMS.register("birch_wall_block", () ->
            new BlockItem(BlockRegistry.BIRCH_WALL_BLOCK.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> CORRAL_WALL_ITEM =
        ITEMS.register("corral_wall",
            () -> new BlockItem(BlockRegistry.CORRAL_WALL.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> CORRAL_WALL_POLE_ITEM =
        ITEMS.register("corral_wall_pole",
            () -> new BlockItem(BlockRegistry.CORRAL_WALL_POLE.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> CORRAL_CORNER_FENCE_ITEM =
    ITEMS.register("corral_corner_fence", () -> new BlockItem(BlockRegistry.CORRAL_CORNER_FENCE.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> CORRAL_FENCE_ITEM =
    ITEMS.register("corral_fence", () -> new BlockItem(BlockRegistry.CORRAL_FENCE.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> CORRAL_PILLAR_ITEM =
    ITEMS.register("corral_pillar", () -> new BlockItem(BlockRegistry.CORRAL_PILLAR.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> WINDOW_1X1_ITEM =
        ITEMS.register("window_1x1",
            () -> new BlockItem(BlockRegistry.WINDOW_1X1.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WINDOW_1X2_ITEM =
        ITEMS.register("window_1x2",
            () -> new BlockItem(BlockRegistry.WINDOW_1X2.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WINDOW_COBBLESTONE_1X2_ITEM =
        ITEMS.register("window_cobblestone_1x2",
            () -> new BlockItem(BlockRegistry.WINDOW_COBBLESTONE_1X2.get(), new Item.Properties()));


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

public static final DeferredHolder<Item, BlockItem> PIER =
    ITEMS.register("pier", () ->
        new BlockItem(BlockRegistry.PIER.get(), new Item.Properties())
    );

public static final DeferredHolder<Item, BlockItem> FLOWSTONE_1_ITEM =
    ITEMS.register("flowstone_1", () ->
        new BlockItem(BlockRegistry.FLOWSTONE_1.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> FLOWSTONE_2_ITEM =
    ITEMS.register("flowstone_2", () ->
        new BlockItem(BlockRegistry.FLOWSTONE_2.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> FLOWSTONE_3_ITEM =
    ITEMS.register("flowstone_3", () ->
        new BlockItem(BlockRegistry.FLOWSTONE_3.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> FLOWSTONE_4_ITEM =
    ITEMS.register("flowstone_4", () ->
        new BlockItem(BlockRegistry.FLOWSTONE_4.get(), new Item.Properties()));


  public static final Map<Integer, DeferredHolder<Item, BlockItem>> STALAGMITE_ITEMS = new HashMap<>();

    static {
        for (int i = 1; i <= 7; i++) {
            int index = i;
            STALAGMITE_ITEMS.put(index,
                ITEMS.register("stalagmite_" + index, () ->
                    new BlockItem(BlockRegistry.STALAGMITES.get(index).get(), new Item.Properties()))
            );
        }
    }

public static final DeferredHolder<Item, BlockItem> HANGING_LANTERN_ITEM =
    ITEMS.register("hanging_lantern",
        () -> new BlockItem(BlockRegistry.HANGING_LANTERN.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> WINDOW_BIRCH_1X1_ITEM =
        ITEMS.register("window_birch_1x1",
            () -> new BlockItem(BlockRegistry.WINDOW_BIRCH_1X1.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> LOG_WALL_ITEM =
        ITEMS.register("log_wall",
            () -> new BlockItem(BlockRegistry.LOG_WALL.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> LOG_WALL_BLOCK_ITEM =
        ITEMS.register("log_wall_block",
            () -> new BlockItem(BlockRegistry.LOG_WALL_BLOCK.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> STONE_FLOOR_ITEM =
        ITEMS.register("stone_floor",
            () -> new BlockItem(BlockRegistry.STONE_FLOOR.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> STONE_FINIAL_ITEM =
        ITEMS.register("stone_finial",
            () -> new BlockItem(BlockRegistry.STONE_FINIAL.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> DARK_STONE_FINIAL_ITEM =
        ITEMS.register("dark_stone_finial",
            () -> new BlockItem(BlockRegistry.DARK_STONE_FINIAL.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> DARK_STONE_BUTTRESS_ITEM =
        ITEMS.register("dark_stone_buttress",
            () -> new BlockItem(BlockRegistry.DARK_STONE_BUTTRESS.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> STONE_ARCH_ITEM =
        ITEMS.register("stone_arch",
            () -> new BlockItem(BlockRegistry.STONE_ARCH.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> DARK_STONE_ARCH_ITEM =
        ITEMS.register("dark_stone_arch",
            () -> new BlockItem(BlockRegistry.DARK_STONE_ARCH.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> DARK_STONE_WALL_BOTTOM_CURVE_LEFT_ITEM =
        ITEMS.register("dark_stone_wall_bottom_curve_left",
            () -> new BlockItem(BlockRegistry.DARK_STONE_WALL_BOTTOM_CURVE_LEFT.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> DARK_STONE_WALL_BOTTOM_CURVE_RIGHT_ITEM =
        ITEMS.register("dark_stone_wall_bottom_curve_right",
            () -> new BlockItem(BlockRegistry.DARK_STONE_WALL_BOTTOM_CURVE_RIGHT.get(), new Item.Properties()));



public static final DeferredHolder<Item, BlockItem> STONE_WALL_HALF_ITEM =
        ITEMS.register("stone_wall_half",
            () -> new BlockItem(BlockRegistry.STONE_WALL_HALF.get(), new Item.Properties()));


    public static final DeferredHolder<Item, Item> DARK_STONE_WALL_ITEM =
        ITEMS.register("dark_stone_wall", () ->
            new BlockItem(BlockRegistry.DARK_STONE_WALL.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> DARK_STONE_WALL_HALF_ITEM =
        ITEMS.register("dark_stone_wall_half",
            () -> new BlockItem(BlockRegistry.DARK_STONE_WALL_HALF.get(), new Item.Properties()));


    public static final DeferredHolder<Item, Item> STONE_WALL_ITEM =
        ITEMS.register("stone_wall", () ->
            new BlockItem(BlockRegistry.STONE_WALL.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> STONE_WALL_BOTTOM_ITEM =
        ITEMS.register("stone_wall_bottom",
            () -> new BlockItem(BlockRegistry.STONE_WALL_BOTTOM.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> STONE_WALL_BOTTOM_BLOCK =
    ITEMS.register("stone_wall_bottom_block",
        () -> new BlockItem(BlockRegistry.STONE_WALL_BOTTOM_BLOCK.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> STONE_WALL_WINDOW_ITEM =
    ITEMS.register("stone_wall_window",
        () -> new BlockItem(BlockRegistry.STONE_WALL_WINDOW.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> STONE_WALL_TOP_BLOCK =
    ITEMS.register("stone_wall_top_block",
        () -> new BlockItem(BlockRegistry.STONE_WALL_TOP_BLOCK.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> STONE_WALL_TOP_ITEM =
        ITEMS.register("stone_wall_top",
            () -> new BlockItem(BlockRegistry.STONE_WALL_TOP.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> COBBLESTONE_WALL_BOTTOM_ITEM =
        ITEMS.register("cobblestone_wall_bottom",
            () -> new BlockItem(BlockRegistry.COBBLESTONE_WALL_BOTTOM.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> COBBLESTONE_WALL_TOP_ITEM =
        ITEMS.register("cobblestone_wall_top",
            () -> new BlockItem(BlockRegistry.COBBLESTONE_WALL_TOP.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> DARK_STONE_WINDOW =
        ITEMS.register("dark_stone_window",
            () -> new BlockItem(BlockRegistry.DARK_STONE_WINDOW.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> DARK_STONE_WALL_BOTTOM_ITEM =
        ITEMS.register("dark_stone_wall_bottom",
            () -> new BlockItem(BlockRegistry.DARK_STONE_WALL_BOTTOM.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WOODEN_PLANK_FLOOR_ITEM =
    ITEMS.register("wooden_plank_floor", () ->
        new BlockItem(BlockRegistry.WOODEN_PLANK_FLOOR.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WOODEN_PLANK_FLOOR_SLAB_ITEM =
    ITEMS.register("wooden_plank_floor_slab", () ->
        new BlockItem(BlockRegistry.WOODEN_PLANK_FLOOR_SLAB.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WOODEN_BOARD_FLOOR_ITEM =
    ITEMS.register("wooden_board_floor", () ->
        new BlockItem(BlockRegistry.WOODEN_BOARD_FLOOR.get(), new Item.Properties()));


    public static final DeferredHolder<Item, Item> CAVE_ITEM =
        ITEMS.register("cave", () ->
            new BlockItem(BlockRegistry.CAVE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> DUNGEON_WALL_ITEM =
        ITEMS.register("dungeon_wall", () ->
            new BlockItem(BlockRegistry.DUNGEON_WALL.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> DUNGEON_STAIRS_ITEM =
        ITEMS.register("dungeon_stairs", () ->
            new BlockItem(BlockRegistry.DUNGEON_STAIRS.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> OAK_WALL_ITEM =
        ITEMS.register("oak_wall", () ->
            new BlockItem(BlockRegistry.OAK_WALL.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> OAK_WALL_BOTTOM_ITEM =
        ITEMS.register("oak_wall_bottom",
            () -> new BlockItem(BlockRegistry.OAK_WALL_BOTTOM.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> OAK_WALL_TOP_ITEM =
        ITEMS.register("oak_wall_top",
            () -> new BlockItem(BlockRegistry.OAK_WALL_TOP.get(), new Item.Properties()));


    public static final DeferredHolder<Item, Item> BRICK_WALL_ITEM =
        ITEMS.register("brick_wall", () ->
            new BlockItem(BlockRegistry.BRICK_WALL.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> BRICK_WALL_BOTTOM_ITEM =
        ITEMS.register("brick_wall_bottom",
            () -> new BlockItem(BlockRegistry.BRICK_WALL_BOTTOM.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> BRICK_WALL_TOP_ITEM =
        ITEMS.register("brick_wall_top",
            () -> new BlockItem(BlockRegistry.BRICK_WALL_TOP.get(), new Item.Properties()));



    public static final DeferredHolder<Item, Item> PLASTER_STONE_WALL_ITEM =
        ITEMS.register("plaster_stone_wall", () ->
            new BlockItem(BlockRegistry.PLASTER_STONE_WALL.get(), new Item.Properties()));



public static final DeferredHolder<Item, BlockItem> PLASTER_STONE_FOUNDATION_ITEM =
        ITEMS.register("plaster_stone_foundation",
            () -> new BlockItem(BlockRegistry.PLASTER_STONE_FOUNDATION.get(), new Item.Properties()));



public static final DeferredHolder<Item, BlockItem> PLASTER_STONE_WALL_BOTTOM_ITEM =
        ITEMS.register("plaster_stone_wall_bottom",
            () -> new BlockItem(BlockRegistry.PLASTER_STONE_WALL_BOTTOM.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> PLASTER_STONE_WALL_TOP_ITEM =
        ITEMS.register("plaster_stone_wall_top",
            () -> new BlockItem(BlockRegistry.PLASTER_STONE_WALL_TOP.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> PLASTER_WOOD_WALL_ITEM =
        ITEMS.register("plaster_wood_wall", () ->
            new BlockItem(BlockRegistry.PLASTER_WOOD_WALL.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> WOOD_SUPPORT_FLOOR_ITEM =
        ITEMS.register("wood_support_floor", () ->
            new BlockItem(BlockRegistry.WOOD_SUPPORT_FLOOR.get(), new Item.Properties()));



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

public static final DeferredHolder<Item, Item> LOCKABLE_METAL_DOOR_ITEM = ITEMS.register(
    "lockable_metal_door_item",
    () -> new BlockItem(BlockRegistry.LOCKABLE_METAL_DOOR.get(), new Item.Properties())
);

public static final DeferredHolder<Item, Item> WOOD_DOOR_ITEM = ITEMS.register(
    "wood_door_item",
    () -> new BlockItem(BlockRegistry.WOOD_DOOR.get(), new Item.Properties().stacksTo(64))
);

public static final DeferredHolder<Item, Item> WOODEN_GATE_ITEM = ITEMS.register(
    "wooden_gate_item",
    () -> new BlockItem(BlockRegistry.WOODEN_GATE.get(), new Item.Properties().stacksTo(64))
);


public static final DeferredHolder<Item, Item> IRON_FENCE_GATE_ITEM = ITEMS.register(
    "iron_fence_gate",
    () -> new BlockItem(BlockRegistry.IRON_FENCE_GATE.get(), new Item.Properties().stacksTo(64))
);

public static final DeferredHolder<Item, Item> LOCKABLE_WOOD_DOOR_ITEM = ITEMS.register(
    "lockable_wood_door_item",
    () -> new BlockItem(BlockRegistry.LOCKABLE_WOOD_DOOR.get(), new Item.Properties())
);

   public static final DeferredHolder<Item, BlockItem> PENTAGRAM_ITEM =
        ITEMS.register("pentagram",
            () -> new BlockItem(BlockRegistry.PENTAGRAM.get(),
                    new Item.Properties()
                        .stacksTo(64)  
            ));

// chests

    public static final DeferredHolder<Item, Item> CHEST_WOODEN_ITEM =
            ITEMS.register("chest_wooden", () -> new BlockItem(BlockRegistry.CHEST_WOODEN.get(),
                    new Item.Properties()));

    public static final DeferredHolder<Item, Item> CHEST_METAL_ITEM =
            ITEMS.register("chest_metal", () -> new BlockItem(BlockRegistry.CHEST_METAL.get(),
                    new Item.Properties()));

    public static final DeferredHolder<Item, Item> CHEST_METAL_BRONZE_ITEM =
            ITEMS.register("chest_metal_bronze", () -> new BlockItem(BlockRegistry.CHEST_METAL_BRONZE.get(),
                    new Item.Properties()));

    public static final DeferredHolder<Item, Item> BRITANNIA_LOCKABLE_CHEST_ITEM =
            ITEMS.register("britannia_lockable_chest", () -> new BlockItem(BlockRegistry.BRITANNIA_LOCKABLE_CHEST.get(),
                    new Item.Properties()));

// containers

    public static final DeferredHolder<Item, Item> ARMOIRE_BROWN_ITEM =
            ITEMS.register("armoire_brown", () -> new BlockItem(BlockRegistry.ARMOIRE_BROWN.get(),
                    new Item.Properties()));

    public static final DeferredHolder<Item, Item> ARMOIRE_RED_ITEM =
            ITEMS.register("armoire_red", () -> new BlockItem(BlockRegistry.ARMOIRE_RED.get(),
                    new Item.Properties()));

    public static final DeferredHolder<Item, Item> CHEST_OF_DRAWERS_BROWN_ITEM =
            ITEMS.register("chest_of_drawers_brown", () -> new BlockItem(BlockRegistry.CHEST_OF_DRAWERS_BROWN.get(),
                    new Item.Properties()));

                     public static final DeferredHolder<Item, Item> CHEST_OF_DRAWERS_RED_ITEM =
            ITEMS.register("chest_of_drawers_red", () -> new BlockItem(BlockRegistry.CHEST_OF_DRAWERS_RED.get(),
                    new Item.Properties()));


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
public static final DeferredHolder<Item, BlockItem> ANCHOR =
    ITEMS.register("anchor", () -> new BlockItem(BlockRegistry.ANCHOR.get(), new Item.Properties()));
public static final DeferredHolder<Item, BlockItem> IRON_FENCE =
    ITEMS.register("iron_fence", () -> new BlockItem(BlockRegistry.IRON_FENCE.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> WALNUT_FLOOR =
    ITEMS.register("walnut_floor", () -> new BlockItem(BlockRegistry.WALNUT_FLOOR.get(), new Item.Properties()));

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

public static final DeferredHolder<Item, BlockItem> ANKH_ITEM =
        ITEMS.register("ankh",
            () -> new BlockItem(BlockRegistry.ANKH.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> SERPENT_SHIELD_ITEM =
        ITEMS.register("serpent_shield",
            () -> new BlockItem(BlockRegistry.SERPENT_SHIELD.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> CURTAIN_BOTTOM_ITEM =
        ITEMS.register("curtain_bottom",
            () -> new BlockItem(BlockRegistry.CURTAIN_BOTTOM.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> CURTAIN_TOP_ITEM =
        ITEMS.register("curtain_top",
            () -> new BlockItem(BlockRegistry.CURTAIN_TOP.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> CURTAIN_FOUNDATION_ITEM =
        ITEMS.register("curtain_foundation",
            () -> new BlockItem(BlockRegistry.CURTAIN_FOUNDATION.get(), new Item.Properties()));



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

public static final DeferredHolder<Item, TwoHandedAxeItem> TWO_HANDED_AXE =
        ITEMS.register("two_handed_axe", () ->
                new TwoHandedAxeItem(
                        ModToolTiers.TWO_HANDED_AXE_TIER,
                        new Item.Properties()
                                .stacksTo(1)
                                .attributes(TwoHandedAxeItem.createAttributes(ModToolTiers.TWO_HANDED_AXE_TIER))
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

public static final DeferredHolder<Item, BlockItem> QUEST_DESTINATION_BLOCK_ITEM =
            ITEMS.register("quest_destination_block", () ->
                    new BlockItem(BlockRegistry.QUEST_DESTINATION_BLOCK.get(), new Item.Properties()));

// spawners

    public static final DeferredHolder<Item, BlockItem> TRADER_SPAWN_BLOCK_ITEM =
            ITEMS.register("trader_spawn_block", () ->
                    new BlockItem(BlockRegistry.TRADER_SPAWN_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> MERCHANT_SPAWN_BLOCK_ITEM =
            ITEMS.register("merchant_spawn_block", () ->
                    new BlockItem(BlockRegistry.MERCHANT_SPAWN_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> QUEST_GIVER_SPAWN_BLOCK_ITEM =
            ITEMS.register("quest_giver_spawn_block", () ->
                    new BlockItem(BlockRegistry.QUEST_GIVER_SPAWN_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> BRITANNIA_SPAWN_BLOCK_ITEM =
            ITEMS.register("britannia_spawn_block", () ->
                    new BlockItem(BlockRegistry.BRITANNIA_SPAWN_BLOCK.get(), new Item.Properties()));


    public static final DeferredHolder<Item, BlockItem> ARCHITECT_SPAWN_BLOCK_ITEM =
            ITEMS.register("architect_spawn_block", () ->
                    new BlockItem(BlockRegistry.ARCHITECT_SPAWN_BLOCK.get(),
                            new Item.Properties())); 

        public static final DeferredHolder<Item, Item> WOOD_SPAWN_BLOCK_ITEM = ITEMS.register(
            "wood_spawn_block", () -> new BlockItem(BlockRegistry.WOOD_SPAWN_BLOCK.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> METAL_SPAWN_BLOCK_ITEM = ITEMS.register(
            "metal_spawn_block", () -> new BlockItem(BlockRegistry.METAL_SPAWN_BLOCK.get(), new Item.Properties()));

        public static final DeferredHolder<Item, Item> STONE_SPAWN_BLOCK_ITEM = ITEMS.register(
            "stone_spawn_block", () -> new BlockItem(BlockRegistry.STONE_SPAWN_BLOCK.get(), new Item.Properties()));


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

public static final DeferredHolder<Item, Item> HOUSE_KEY = ITEMS.register(
        "house_key",
        () -> new HouseKeyItem(new Item.Properties()));

public static final DeferredHolder<Item, Item> CHEST_KEY = ITEMS.register(
        "chest_key",
        () -> new ChestKeyItem(new Item.Properties()));

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


public static final DeferredHolder<Item, BlockItem> ROPE_ITEM =
    ITEMS.register("rope", () -> new BlockItem(BlockRegistry.ROPE.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> SPITTOON_ITEM =
        ITEMS.register("spittoon",
            () -> new BlockItem(BlockRegistry.SPITTOON.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> TABLE_SETTING_ITEM =
        ITEMS.register("table_setting",
            () -> new BlockItem(BlockRegistry.TABLE_SETTING.get(), new Item.Properties()));


public static final DeferredHolder<Item, BlockItem> WOODEN_POST_ITEM =
    ITEMS.register("wooden_post", () -> new BlockItem(BlockRegistry.WOODEN_POST.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> LAMP_POST_WOODEN_ITEM = ITEMS.register(
            "lamp_post_wooden", () -> new BlockItem(BlockRegistry.WOODEN_LAMP_POST.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> LAMP_POST_REGULAR_ITEM = ITEMS.register(
            "lamp_post_regular", () -> new BlockItem(BlockRegistry.LAMP_POST_REGULAR.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> LAMP_POST_FANCY_ITEM = ITEMS.register(
            "lamp_post_fancy", () -> new BlockItem(BlockRegistry.LAMP_POST_FANCY.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> WALL_SCONCE_ITEM = ITEMS.register(
            "wall_sconce", () -> new BlockItem(BlockRegistry.WALL_SCONCE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> CANDLE_ITEM = ITEMS.register(
            "candle", () -> new BlockItem(BlockRegistry.CANDLE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> WOODEN_CHANDELIER = ITEMS.register(
            "wooden_chandelier", () -> new BlockItem(BlockRegistry.WOODEN_CHANDELIER.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> SMALL_WOODEN_CHANDELIER = ITEMS.register(
            "small_wooden_chandelier", () -> new BlockItem(BlockRegistry.SMALL_WOODEN_CHANDELIER.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> LARGE_WOODEN_CHANDELIER = ITEMS.register(
            "large_wooden_chandelier", () -> new BlockItem(BlockRegistry.LARGE_WOODEN_CHANDELIER.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> LARGE_IRON_CHANDELIER = ITEMS.register(
            "large_iron_chandelier", () -> new BlockItem(BlockRegistry.LARGE_IRON_CHANDELIER.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> SMALL_IRON_CHANDELIER = ITEMS.register(
            "small_iron_chandelier", () -> new BlockItem(BlockRegistry.SMALL_IRON_CHANDELIER.get(), new Item.Properties()));

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
    public static final DeferredHolder<Item, Item> DECORATIVE_WEAPONS_1_ITEM = ITEMS.register(
            "decorative_weapons_1", () -> new BlockItem(BlockRegistry.DECORATIVE_WEAPONS_1.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> DECORATIVE_SHIELD_1_ITEM =
        ITEMS.register("decorative_shield_1",
            () -> new BlockItem(BlockRegistry.DECORATIVE_SHIELD_1.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> DECORATIVE_SHIELD_2_ITEM =
        ITEMS.register("decorative_shield_2",
            () -> new BlockItem(BlockRegistry.DECORATIVE_SHIELD_2.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> DECORATIVE_SHIELD_1BW_ITEM =
        ITEMS.register("decorative_shield_1bw",
            () -> new BlockItem(BlockRegistry.DECORATIVE_SHIELD_1BW.get(), new Item.Properties()));

public static final DeferredHolder<Item, BlockItem> DECORATIVE_SHIELD_2BW_ITEM =
        ITEMS.register("decorative_shield_2bw",
            () -> new BlockItem(BlockRegistry.DECORATIVE_SHIELD_2BW.get(), new Item.Properties()));
// Furniture

    public static final DeferredHolder<Item, Item> DOUBLE_BED_ITEM =
            ITEMS.register("double_bed",
                    () -> new BlockItem(BlockRegistry.DOUBLE_BED.get(),
                                        new Item.Properties().stacksTo(1)));


    public static final DeferredHolder<Item, Item> WATER_TROUGH_BLOCK_ITEM = ITEMS.register(
            "water_trough", () -> new BlockItem(BlockRegistry.WATER_TROUGH_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> WATER_BARREL_BLOCK_ITEM = ITEMS.register(
            "water_barrel", () -> new BlockItem(BlockRegistry.WATER_BARREL_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> TRASH_BARREL_BLOCK_ITEM = ITEMS.register(
            "trash_barrel", () -> new BlockItem(BlockRegistry.TRASH_BARREL_BLOCK.get(), new Item.Properties()));


    public static final DeferredHolder<Item, Item> MAGINCIA_STYLE_THRONE_ITEM = ITEMS.register(
            "magincia_style_throne", () -> new BlockItem(BlockRegistry.MAGINCIA_STYLE_THRONE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> STRAW_CHAIR_ITEM = ITEMS.register(
            "straw_chair", () -> new BlockItem(BlockRegistry.STRAW_CHAIR.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> WOODEN_THRONE_ITEM = ITEMS.register(
            "wooden_throne", () -> new BlockItem(BlockRegistry.WOODEN_THRONE.get(), new Item.Properties()));

  public static final DeferredHolder<Item, Item> BENCH_ITEM = ITEMS.register(
            "bench", () -> new BlockItem(BlockRegistry.BENCH.get(), new Item.Properties()));

  public static final DeferredHolder<Item, Item> FOOTSTOOL_ITEM = ITEMS.register(
            "footstool", () -> new BlockItem(BlockRegistry.FOOTSTOOL.get(), new Item.Properties()));

  public static final DeferredHolder<Item, Item> STOOL_ITEM = ITEMS.register(
            "stool", () -> new BlockItem(BlockRegistry.STOOL.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> CHAIR_VESPER_ITEM = ITEMS.register(
            "chair_vesper", () -> new BlockItem(BlockRegistry.CHAIR_VESPER.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> CHAIR_TRINSIC_ITEM = ITEMS.register(
            "chair_trinsic", () -> new BlockItem(BlockRegistry.CHAIR_TRINSIC.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> WOODEN_CHAIR_ITEM = ITEMS.register(
            "wooden_chair", () -> new BlockItem(BlockRegistry.WOODEN_CHAIR.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> LORD_BRITISH_THRONE_ITEM = ITEMS.register(
            "lord_british_throne", () -> new BlockItem(BlockRegistry.LORD_BRITISH_THRONE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> YEW_TABLE_ITEM = ITEMS.register(
            "yew_table", () -> new BlockItem(BlockRegistry.YEW_TABLE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> SMALL_TABLE_ITEM = ITEMS.register(
            "small_table", () -> new BlockItem(BlockRegistry.SMALL_TABLE.get(), new Item.Properties()));


    public static final DeferredHolder<Item, Item> COUNTER_ITEM = ITEMS.register(
            "counter", () -> new BlockItem(BlockRegistry.COUNTER.get(), new Item.Properties()));



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


            public static final DeferredHolder<Item, Item> BLANK_SIGN_HOLDER = ITEMS.register(
                "blank_sign_holder", () -> new BlockItem(BlockRegistry.BLANK_SIGN_HOLDER.get(), new Item.Properties()));

            public static final DeferredHolder<Item, Item> CHESS_BOARD = ITEMS.register(
                "chess_board", () -> new BlockItem(BlockRegistry.CHESS_BOARD.get(), new Item.Properties()));

    // Villa DoubleWallBlock Items
    public static final DeferredHolder<Item, Item> PLASTER_ORNATE_WALL_UPPER_ITEM = ITEMS.register("plaster_ornate_wall_upper", () -> new BlockItem(BlockRegistry.PLASTER_ORNATE_WALL_UPPER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> PLASTER_ORNATE_WALL_1_ITEM = ITEMS.register("plaster_ornate_wall_1", () -> new BlockItem(BlockRegistry.PLASTER_ORNATE_WALL_1.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> PLASTER_ORNATE_WALL_2_ITEM = ITEMS.register("plaster_ornate_wall_2", () -> new BlockItem(BlockRegistry.PLASTER_ORNATE_WALL_2.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> PLASTER_SMALL_WINDOW_ITEM = ITEMS.register("plaster_small_window", () -> new BlockItem(BlockRegistry.PLASTER_SMALL_WINDOW.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> ORNATE_WALL_LARGE_WINDOW_ITEM = ITEMS.register("ornate_wall_large_window", () -> new BlockItem(BlockRegistry.ORNATE_WALL_LARGE_WINDOW.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> PLASTER_WALL_LARGE_WINDOW_ITEM = ITEMS.register("plaster_wall_large_window", () -> new BlockItem(BlockRegistry.PLASTER_WALL_LARGE_WINDOW.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> PLASTER_WALL_SUPPORT_DIAGONAL_EAST_ITEM = ITEMS.register("plaster_wall_support_diagonal_east", () -> new BlockItem(BlockRegistry.PLASTER_WALL_SUPPORT_DIAGONAL_EAST.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> PLASTER_WALL_SUPPORT_DIAGONAL_SOUTH_ITEM = ITEMS.register("plaster_wall_support_diagonal_south", () -> new BlockItem(BlockRegistry.PLASTER_WALL_SUPPORT_DIAGONAL_SOUTH.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> PLASTER_WALL_SUPPORT_OPEN_ITEM = ITEMS.register("plaster_wall_support_open", () -> new BlockItem(BlockRegistry.PLASTER_WALL_SUPPORT_OPEN.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> PLASTER_WALL_BLANK_ITEM = ITEMS.register("plaster_wall_blank", () -> new BlockItem(BlockRegistry.PLASTER_WALL_BLANK.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> PLASTER_WALL_AND_SUPPORT_BLANK_ITEM = ITEMS.register("plaster_wall_and_support_blank", () -> new BlockItem(BlockRegistry.PLASTER_WALL_AND_SUPPORT_BLANK.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> PLASTER_ARCHWAY_ITEM = ITEMS.register("plaster_archway", () -> new BlockItem(BlockRegistry.PLASTER_ARCHWAY.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> PLASTER_AND_STONE_WINDOW_ITEM = ITEMS.register("plaster_and_stone_window", () -> new BlockItem(BlockRegistry.PLASTER_AND_STONE_WINDOW.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> ORNATE_SANDSTONE_WALL_ITEM = ITEMS.register("ornate_sandstone_wall", () -> new BlockItem(BlockRegistry.ORNATE_SANDSTONE_WALL.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> REGULAR_SANDSTONE_WALL_ITEM = ITEMS.register("regular_sandstone_wall", () -> new BlockItem(BlockRegistry.REGULAR_SANDSTONE_WALL.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> SANDSTONE_BLOCK_WALL_ITEM = ITEMS.register("sandstone_block_wall", () -> new BlockItem(BlockRegistry.SANDSTONE_BLOCK_WALL.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> ORNATE_SANDSTONE_WINDOW_ITEM = ITEMS.register("ornate_sandstone_window", () -> new BlockItem(BlockRegistry.ORNATE_SANDSTONE_WINDOW.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> SANDSTONE_WINDOW_ITEM = ITEMS.register("sandstone_window", () -> new BlockItem(BlockRegistry.SANDSTONE_WINDOW.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> SANDSTONE_POST_ITEM = ITEMS.register("sandstone_post", () -> new BlockItem(BlockRegistry.SANDSTONE_POST.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> ORNATE_SANDSTONE_POST_ITEM = ITEMS.register("ornate_sandstone_post", () -> new BlockItem(BlockRegistry.ORNATE_SANDSTONE_POST.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> SANDSTONE_BATTLEMENT_ITEM = ITEMS.register("sandstone_battlement", () -> new BlockItem(BlockRegistry.SANDSTONE_BATTLEMENT.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> SANDSTONE_COLUMN_ITEM = ITEMS.register("sandstone_column", () -> new BlockItem(BlockRegistry.SANDSTONE_COLUMN.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> CUSTOM_SANDSTONE_BRICK_ITEM = ITEMS.register("custom_sandstone_brick", () -> new BlockItem(BlockRegistry.CUSTOM_SANDSTONE_BRICK.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> LIGHT_SANDSTONE_BRICK_ROAD_ITEM = ITEMS.register("light_sandstone_brick_road", () -> new BlockItem(BlockRegistry.LIGHT_SANDSTONE_BRICK_ROAD.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> MEDIUM_SANDSTONE_BRICK_ROAD_ITEM = ITEMS.register("medium_sandstone_brick_road", () -> new BlockItem(BlockRegistry.MEDIUM_SANDSTONE_BRICK_ROAD.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> DARK_SANDSTONE_BRICK_ROAD_ITEM = ITEMS.register("dark_sandstone_brick_road", () -> new BlockItem(BlockRegistry.DARK_SANDSTONE_BRICK_ROAD.get(), new Item.Properties()));


    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        SignItemRegistry.register(modEventBus);
    }
}
