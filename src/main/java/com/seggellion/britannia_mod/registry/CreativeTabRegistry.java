// CreativeTabRegistry.java
package com.seggellion.britannia_mod.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.registry.SignItemRegistry;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import com.seggellion.britannia_mod.winery.GrapeVariety;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.seggellion.britannia_mod.structure.HouseStyle;


public class CreativeTabRegistry {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
            net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, "britannia_mod");


    // Tab 1: World & Building Blocks
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_WORLD_TAB = CREATIVE_TABS.register(
        "britannia_world_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.britannia_world_tab"))
            .icon(() -> Item.BY_BLOCK.get(BlockRegistry.QUARTER_DIRT_BLOCK.get()).getDefaultInstance())
            .displayItems((parameters, output) -> {
                // Construction blocks
                safeAccept(output, ItemRegistry.CAVE_FLOOR_ITEM.get());
                safeAccept(output, ItemRegistry.QUARTER_DIRT_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.HALF_DIRT_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.THREE_QUARTER_DIRT_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.QUARTER_GRASS_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.HALF_GRASS_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.THREE_QUARTER_GRASS_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.QUARTER_DEEPSLATE_COBBLESTONE_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.HALF_DEEPSLATE_COBBLESTONE_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.THREE_QUARTER_DEEPSLATE_COBBLESTONE_BLOCK_ITEM.get());

                // Roofs
                safeAccept(output, ItemRegistry.TILE_ROOF_ITEM.get());
                safeAccept(output, ItemRegistry.TILE_ROOF_FLAT_ITEM.get());
                safeAccept(output, ItemRegistry.CEDAR_ROOF_ITEM.get());
                safeAccept(output, ItemRegistry.CEDAR_ROOF_FLAT_ITEM.get());
                safeAccept(output, ItemRegistry.THATCH_ROOF_ITEM.get());
                safeAccept(output, ItemRegistry.THATCH_ROOF_FLAT_ITEM.get());
                safeAccept(output, ItemRegistry.SLATE_ROOF_ITEM.get());
                safeAccept(output, ItemRegistry.SLATE_ROOF_FLAT_ITEM.get());
                safeAccept(output, ItemRegistry.SLATE_ROOF_1_FLAT_ITEM.get());
                safeAccept(output, ItemRegistry.SLATE_ROOF_2_FLAT_ITEM.get());

                // Foundations, Walls, Floors
                safeAccept(output, ItemRegistry.WOODEN_BOARD_FLOOR_ITEM.get());
                 safeAccept(output, ItemRegistry.WOODEN_PLANK_FLOOR_ITEM.get());
                safeAccept(output, ItemRegistry.WOOD_DOOR_ITEM.get());
                safeAccept(output, ItemRegistry.WOODEN_GATE_ITEM.get());
                safeAccept(output, ItemRegistry.IRON_FENCE_GATE_ITEM.get());
                safeAccept(output, ItemRegistry.LOCKABLE_WOOD_DOOR_ITEM.get());
                safeAccept(output, ItemRegistry.METAL_DOOR_ITEM.get());
                safeAccept(output, ItemRegistry.LOCKABLE_METAL_DOOR_ITEM.get());
                safeAccept(output, ItemRegistry.BRICK_FOUNDATION_OAK_ITEM.get());
                safeAccept(output, ItemRegistry.BRICK_FOUNDATION_SPRUCE_ITEM.get());
                safeAccept(output, ItemRegistry.CUSTOM_STONE_STAIRS_ITEM.get());
                safeAccept(output, ItemRegistry.STONE_FOUNDATION_ITEM.get());
                safeAccept(output, ItemRegistry.COBBLESTONE_WALL_ITEM.get());
                safeAccept(output, ItemRegistry.COBBLESTONE_FOUNDATION_ITEM.get());
                safeAccept(output, ItemRegistry.PLASTER_STONE_FOUNDATION_ITEM.get());
                safeAccept(output, ItemRegistry.PLASTER_WOOD_FOUNDATION_ITEM.get());                
                safeAccept(output, ItemRegistry.STONE_FLOOR_POLISHED_ITEM.get());
                safeAccept(output, ItemRegistry.STONE_FLOOR_ITEM.get());

         // ============================================
                // === NEW: WINERY SECTION (Added to end) ===
                // ============================================
                
                // 1. Tools & Utility
                safeAccept(output, ItemRegistry.VINTNER_HOE.get());
                safeAccept(output, ItemRegistry.SCISSORS.get());
                
                // 2. Farming Blocks
                safeAccept(output, ItemRegistry.FARMING_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.TRELLIS_ITEM.get());
                
                // 3. Processing Blocks
                safeAccept(output, ItemRegistry.JUICE_PRESS_ITEM.get());
                safeAccept(output, ItemRegistry.WINE_BARREL_ITEM.get());
                
                // 4. Fertilizers
                safeAccept(output, ItemRegistry.TURQUOISE_POWDER.get());
                safeAccept(output, ItemRegistry.SULPHUROUS_ASH.get());

                // 5. Glassware
                safeAccept(output, ItemRegistry.PITCHER_EMPTY.get());
                safeAccept(output, ItemRegistry.WINE_BOTTLE_GREEN.get());
                safeAccept(output, ItemRegistry.WINE_BOTTLE_BROWN.get());
                safeAccept(output, ItemRegistry.WINE_BOTTLE_BLUE.get());
                safeAccept(output, ItemRegistry.WINE_BOTTLE_CLEAR.get());
                safeAccept(output, ItemRegistry.PITCHER_WHITE_GRAPE_JUICE.get());
                safeAccept(output, ItemRegistry.PITCHER_RED_GRAPE_JUICE.get());

                // 6. Dynamic Seeds & Grapes (The fix for the error)
              // 6. Dynamic Seeds
                for (com.seggellion.britannia_mod.winery.GrapeVariety variety : com.seggellion.britannia_mod.winery.GrapeVarietyManager.getAllVarieties()) {
                    
                    // Add Seed Packet
                    ItemStack seedStack = new ItemStack(ItemRegistry.GRAPE_SEEDS.get());
                    com.seggellion.britannia_mod.item.GrapeSeedsItem.setVariety(seedStack, variety.id());
                    
                    // Set Name
                    seedStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, 
                        Component.literal(variety.displayName() + " Seeds"));
                    
                    output.accept(seedStack);
                }
   

                // Containers
                safeAccept(output, ItemRegistry.CHEST_WOODEN_ITEM.get());
                safeAccept(output, ItemRegistry.ARMOIRE_BROWN_ITEM.get());
                safeAccept(output, ItemRegistry.ARMOIRE_RED_ITEM.get());
                safeAccept(output, ItemRegistry.CHEST_OF_DRAWERS_BROWN_ITEM.get());
                safeAccept(output, ItemRegistry.CHEST_OF_DRAWERS_RED_ITEM.get());
                safeAccept(output, ItemRegistry.CHEST_METAL_ITEM.get());
                safeAccept(output, ItemRegistry.CHEST_METAL_BRONZE_ITEM.get());

                // Walls
                safeAccept(output, ItemRegistry.CAVE_ITEM.get());
                safeAccept(output, ItemRegistry.DUNGEON_WALL_ITEM.get());
                safeAccept(output, ItemRegistry.DUNGEON_STAIRS_ITEM.get());
                safeAccept(output, ItemRegistry.OAK_WALL_ITEM.get());
                safeAccept(output, ItemRegistry.OAK_WALL_BOTTOM_ITEM.get());
                safeAccept(output, ItemRegistry.OAK_WALL_TOP_ITEM.get());
                safeAccept(output, ItemRegistry.BRICK_WALL_ITEM.get());
                safeAccept(output, ItemRegistry.BRICK_WALL_BOTTOM_ITEM.get());
                safeAccept(output, ItemRegistry.BRICK_WALL_TOP_ITEM.get());
                safeAccept(output, ItemRegistry.STONE_WALL_ITEM.get());
                safeAccept(output, ItemRegistry.STONE_WALL_BOTTOM_ITEM.get());
                safeAccept(output, ItemRegistry.STONE_WALL_TOP_ITEM.get());
                safeAccept(output, ItemRegistry.STONE_WALL_TOP_BLOCK.get());
                safeAccept(output, ItemRegistry.STONE_WALL_BOTTOM_BLOCK.get());
                safeAccept(output, ItemRegistry.STONE_WALL_WINDOW_ITEM.get());
                safeAccept(output, ItemRegistry.STONE_WALL_HALF_ITEM.get());
                safeAccept(output, ItemRegistry.DARK_STONE_WINDOW.get());
                safeAccept(output, ItemRegistry.DARK_STONE_STAIRS_ITEM.get());
                safeAccept(output, ItemRegistry.DARK_STONE_WALL_ITEM.get());
                safeAccept(output, ItemRegistry.DARK_STONE_WALL_HALF_ITEM.get());
                safeAccept(output, ItemRegistry.DARK_STONE_WALL_BOTTOM_ITEM.get());
                safeAccept(output, ItemRegistry.DARK_STONE_FINIAL_ITEM.get());
                safeAccept(output, ItemRegistry.STONE_FINIAL_ITEM.get());
                safeAccept(output, ItemRegistry.DARK_STONE_BUTTRESS_ITEM.get());
                safeAccept(output, ItemRegistry.STONE_ARCH_ITEM.get());
                safeAccept(output, ItemRegistry.DARK_STONE_ARCH_ITEM.get());
                safeAccept(output, ItemRegistry.DARK_STONE_WALL_BOTTOM_CURVE_LEFT_ITEM.get());
                safeAccept(output, ItemRegistry.DARK_STONE_WALL_BOTTOM_CURVE_RIGHT_ITEM.get());
                safeAccept(output, ItemRegistry.COBBLESTONE_WALL_BOTTOM_ITEM.get());
                safeAccept(output, ItemRegistry.COBBLESTONE_WALL_TOP_ITEM.get());
                safeAccept(output, ItemRegistry.BIRCH_WALL_ITEM.get());
                safeAccept(output, ItemRegistry.BIRCH_WALL_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.LOG_WALL_ITEM.get());
                safeAccept(output, ItemRegistry.LOG_WALL_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.PLASTER_STONE_WALL_ITEM.get());
                safeAccept(output, ItemRegistry.PLASTER_WOOD_WALL_ITEM.get());
                safeAccept(output, ItemRegistry.PLASTER_WOOD_WALL_BOTTOM_ITEM.get());
                safeAccept(output, ItemRegistry.PLASTER_WOOD_WALL_TOP_ITEM.get());
                safeAccept(output, ItemRegistry.CORRAL_CORNER_FENCE_ITEM.get());
                safeAccept(output, ItemRegistry.CORRAL_FENCE_ITEM.get());
                safeAccept(output, ItemRegistry.CORRAL_PILLAR_ITEM.get());
                safeAccept(output, ItemRegistry.CORRAL_WALL_ITEM.get());
                safeAccept(output, ItemRegistry.CORRAL_WALL_POLE_ITEM.get());

                // Windows
                safeAccept(output, ItemRegistry.WINDOW_1X1_ITEM.get());
                safeAccept(output, ItemRegistry.WINDOW_1X2_ITEM.get());
                safeAccept(output, ItemRegistry.WINDOW_COBBLESTONE_1X2_ITEM.get());
                safeAccept(output, ItemRegistry.WINDOW_1X3_ITEM.get());
                safeAccept(output, ItemRegistry.WINDOW_2X2_ITEM.get());
                safeAccept(output, ItemRegistry.WINDOW_2X3_ITEM.get());
                safeAccept(output, ItemRegistry.WINDOW_CROSS_1X1_ITEM.get());
                safeAccept(output, ItemRegistry.WINDOW_CROSS_1X2_ITEM.get());
                safeAccept(output, ItemRegistry.WINDOW_CROSS_1X3_ITEM.get());
                safeAccept(output, ItemRegistry.WINDOW_CROSS_2X2_ITEM.get());
                safeAccept(output, ItemRegistry.WINDOW_CROSS_2X3_ITEM.get());
                safeAccept(output, ItemRegistry.WINDOW_BIRCH_1X1_ITEM.get());

                // Pillars and statues
                safeAccept(output, ItemRegistry.PILLAR_ITEM.get());
                safeAccept(output, ItemRegistry.STATUE_WOMAN_ITEM.get());
                safeAccept(output, ItemRegistry.STATUE_COUPLE_ITEM.get());
                safeAccept(output, ItemRegistry.STATUE_MAN_ITEM.get());
                safeAccept(output, ItemRegistry.STATUE_STAND_ITEM.get());

                // Lamps                
                safeAccept(output, ItemRegistry.WOODEN_POST_ITEM.get());
                safeAccept(output, ItemRegistry.LAMP_POST_WOODEN_ITEM.get());
                safeAccept(output, ItemRegistry.LAMP_POST_FANCY_ITEM.get());
                safeAccept(output, ItemRegistry.LAMP_POST_REGULAR_ITEM.get());
            }).build());

    // Additional tabs to follow (Decor, Items, Magic, Ores)

    // Tab 2: Decorative & Graveyard
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_DECOR_TAB = CREATIVE_TABS.register(
        "britannia_decor_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.britannia_decor_tab"))
            .icon(() -> ItemRegistry.GRAVESTONE_TYPE_1_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                // Decorative items
            BlockRegistry.STALAGMITES.values().forEach(holder -> {
                    Item item = Item.BY_BLOCK.get(holder.get());
                    if (item != null) safeAccept(output, item);
                });
                safeAccept(output, ItemRegistry.FLOWSTONE_1_ITEM.get());
                safeAccept(output, ItemRegistry.FLOWSTONE_2_ITEM.get());
                safeAccept(output, ItemRegistry.FLOWSTONE_3_ITEM.get());
                safeAccept(output, ItemRegistry.FLOWSTONE_4_ITEM.get());
                safeAccept(output, ItemRegistry.PIER.get());
                safeAccept(output, ItemRegistry.WALL_SCONCE_ITEM.get());
                safeAccept(output, ItemRegistry.CANDLE_ITEM.get());
                safeAccept(output, ItemRegistry.WOODEN_CHANDELIER.get());
                safeAccept(output, ItemRegistry.CANDELABRA_SMALL_ITEM.get());
                safeAccept(output, ItemRegistry.CANDELABRA_TALL_ITEM.get());
                safeAccept(output, ItemRegistry.TORCH_WALL_ITEM.get());
                safeAccept(output, ItemRegistry.TORCH_STANDING_ITEM.get());
                safeAccept(output, ItemRegistry.BRAZIER_SMALL_ITEM.get());
                safeAccept(output, ItemRegistry.TABLE_SETTING_ITEM.get());
                safeAccept(output, ItemRegistry.SPITTOON_ITEM.get());
                safeAccept(output, ItemRegistry.DECORATIVE_WEAPONS_1_ITEM.get());
                safeAccept(output, ItemRegistry.DECORATIVE_SHIELD_1_ITEM.get());
                safeAccept(output, ItemRegistry.DECORATIVE_SHIELD_2_ITEM.get());
                safeAccept(output, ItemRegistry.DECORATIVE_SHIELD_1BW_ITEM.get());
                safeAccept(output, ItemRegistry.DECORATIVE_SHIELD_2BW_ITEM.get());
                safeAccept(output, ItemRegistry.WATER_TROUGH_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.WATER_BARREL_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.MAGINCIA_STYLE_THRONE_ITEM.get());
                safeAccept(output, ItemRegistry.WOODEN_CHAIR_ITEM.get());
                safeAccept(output, ItemRegistry.WOODEN_THRONE_ITEM.get());
                safeAccept(output, ItemRegistry.BENCH_ITEM.get());
                safeAccept(output, ItemRegistry.STOOL_ITEM.get());
                safeAccept(output, ItemRegistry.FOOTSTOOL_ITEM.get());
                safeAccept(output, ItemRegistry.CHAIR_TRINSIC_ITEM.get());
                safeAccept(output, ItemRegistry.CHAIR_VESPER_ITEM.get());
                safeAccept(output, ItemRegistry.STRAW_CHAIR_ITEM.get());
                safeAccept(output, ItemRegistry.LORD_BRITISH_THRONE_ITEM.get());
                safeAccept(output, ItemRegistry.DOUBLE_BED_ITEM.get());
                safeAccept(output, ItemRegistry.CURTAIN_BOTTOM_ITEM.get());
                safeAccept(output, ItemRegistry.CURTAIN_TOP_ITEM.get());
                safeAccept(output, ItemRegistry.CURTAIN_FOUNDATION_ITEM.get());
                safeAccept(output, ItemRegistry.SERPENT_SHIELD_ITEM.get());
                safeAccept(output, ItemRegistry.ANKH_ITEM.get());
                safeAccept(output, ItemRegistry.PENTAGRAM_ITEM.get());
                safeAccept(output, ItemRegistry.ROPE_ITEM.get());

                // Graveyard items
                safeAccept(output, ItemRegistry.BROKEN_WOODEN_GRAVE_CROSS.get());
                safeAccept(output, ItemRegistry.DAMAGED_WOODEN_GRAVE_CROSS.get());
                safeAccept(output, ItemRegistry.DEAD_GRAVE_FLOWER_VASE.get());
                safeAccept(output, ItemRegistry.DEAD_GRAVE_FLOWERS.get());
                safeAccept(output, ItemRegistry.GRAVE_FLOWERS.get());
                safeAccept(output, ItemRegistry.IRON_CEMETERY_GATE_ARCH.get());
                safeAccept(output, ItemRegistry.ANCHOR.get());
                safeAccept(output, ItemRegistry.IRON_FENCE.get());
                safeAccept(output, ItemRegistry.LYING_SKELETON.get());
                safeAccept(output, ItemRegistry.SITTING_SKELETON.get());
                safeAccept(output, ItemRegistry.SKELETON_ARM.get());
                safeAccept(output, ItemRegistry.SKELETON_LEG.get());
                safeAccept(output, ItemRegistry.SKELETON_TORSO.get());
                safeAccept(output, ItemRegistry.WILTED_GRAVE_FLOWER_VASE.get());
                safeAccept(output, ItemRegistry.WILTED_GRAVE_FLOWERS.get());
                safeAccept(output, ItemRegistry.WOODEN_COFFIN_LID.get());
                safeAccept(output, ItemRegistry.WOODEN_COFFIN_SKELETON.get());
                safeAccept(output, ItemRegistry.WOODEN_COFFIN.get());
                safeAccept(output, ItemRegistry.WOODEN_GRAVE_CROSS.get());
                safeAccept(output, ItemRegistry.WOODEN_OPEN_COFFIN.get());

                // Gravestones
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_1_ITEM.get());
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_2_ITEM.get());
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_3_ITEM.get());
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_4_ITEM.get());
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_5_ITEM.get());
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_6_ITEM.get());
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_7_ITEM.get());
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_8_ITEM.get());
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_9_ITEM.get());
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_10_ITEM.get());
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_11_ITEM.get());
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_12_ITEM.get());
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_13_ITEM.get());
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_14_ITEM.get());
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_15_ITEM.get());
                safeAccept(output, ItemRegistry.GRAVESTONE_TYPE_16_ITEM.get());
                safeAccept(output, ItemRegistry.BLANK_SIGN_HOLDER.get());

                // signs
                   safeAccept(output, ItemRegistry.HANGING_LANTERN_ITEM.get());

                SignItemRegistry.STORE_SIGN_ITEMS.forEach((signType, holder) -> {
                    safeAccept(output, holder.get());
                });

            }).build());

    // Tab 3: Items, Tools, and Entities
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_ITEMS_TAB = CREATIVE_TABS.register(
        "britannia_items_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.britannia_items_tab"))
            .icon(() -> ItemRegistry.GARLIC.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                // Reagents and items
                safeAccept(output, ItemRegistry.GARLIC.get());
                safeAccept(output, ItemRegistry.NIGHTSHADE.get());
                safeAccept(output, ItemRegistry.BLOOD_MOSS.get());
                safeAccept(output, ItemRegistry.GINSENG.get());
                safeAccept(output, ItemRegistry.SPIDERS_SILK.get());
                safeAccept(output, ItemRegistry.SULPHUROUS_ASH.get());
                safeAccept(output, ItemRegistry.MANDRAKE_ROOT.get());

                // General items
                
                safeAccept(output, ItemRegistry.CARPET_TELEPORTER_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.MOONGATE_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.MOONGATE_TOP_ITEM.get());
                safeAccept(output, ItemRegistry.DUNGEON_MOONGATE_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.DUNGEON_MOONGATE_TOP_ITEM.get());
                safeAccept(output, ItemRegistry.MOONGATE_LINKING_WAND.get());
                safeAccept(output, ItemRegistry.INTERIOR_DECORATOR_TOOL.get());
                safeAccept(output, ItemRegistry.SMALL_FORGE_BLOCK_ENTITY.get());
                safeAccept(output, ItemRegistry.LARGE_FORGE_BLOCK_ENTITY.get());
                safeAccept(output, ItemRegistry.YEW_TABLE_ITEM.get());
                safeAccept(output, ItemRegistry.SMALL_TABLE_ITEM.get());
                safeAccept(output, ItemRegistry.COUNTER_ITEM.get());
                safeAccept(output, ItemRegistry.GOLD_COIN.get());

                // Tools & weapons
                safeAccept(output, ItemRegistry.TWO_HANDED_AXE.get());
                output.accept(ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3));
                safeAccept(output, ItemRegistry.ORDER_SHIELD.get());
                // Musical Instruments
                safeAccept(output, ItemRegistry.LAP_HARP.get());
//                safeAccept(output, ItemRegistry.STANDING_HARP.get());
                safeAccept(output, ItemRegistry.LUTE.get());
                safeAccept(output, ItemRegistry.DRUMS.get());
                safeAccept(output, ItemRegistry.VIOLIN.get());
                safeAccept(output, ItemRegistry.TAMBORINE.get());
                safeAccept(output, ItemRegistry.TAMBORINE_RIBBON.get());
                // House items
                safeAccept(output, ItemRegistry.HOUSE_SIGN_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.BLUE_TENT_DEED.get());
                safeAccept(output, ItemRegistry.PURPLE_TENT_DEED.get());
                for (HouseStyle style : HouseStyle.values()) {
                    output.accept(ItemRegistry.deedFor(style));
                }

                // Entity spawn eggs
                safeAccept(output, ItemRegistry.MONGBAT_SPAWN_EGG.get());
                safeAccept(output, ItemRegistry.DAEMON_SPAWN_EGG.get());
                safeAccept(output, ItemRegistry.LICH_SPAWN_EGG.get());
                safeAccept(output, ItemRegistry.RAT_SPAWN_EGG.get());
                safeAccept(output, ItemRegistry.WRAITH_SPAWN_EGG.get());
                safeAccept(output, ItemRegistry.GHOUL_SPAWN_EGG.get());
                safeAccept(output, ItemRegistry.SHADE_SPAWN_EGG.get());
                safeAccept(output, ItemRegistry.WISP_SPAWN_EGG.get());
                safeAccept(output, ItemRegistry.EARTH_ELEMENTAL_SPAWN_EGG.get());

                // NPC spawn blocks
                safeAccept(output, ItemRegistry.MONSTER_SPAWN_BLOCK_ITEM.get());   
                safeAccept(output, ItemRegistry.TRADER_SPAWN_BLOCK_ITEM.get());                     
                safeAccept(output, ItemRegistry.SHADE_SPAWN_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.LICH_SPAWN_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.WOOD_SPAWN_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.STONE_SPAWN_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.METAL_SPAWN_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.HORSE_SPAWN_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.BLACKSMITH_SPAWN_BLOCK_ITEM.get());
                safeAccept(output, ItemRegistry.ARCHITECT_SPAWN_BLOCK_ITEM.get());

            }).build());

    // Tab 4: Magic
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_MAGIC_TAB = CREATIVE_TABS.register(
        "britannia_tab_magic", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.britannia_magic_tab"))
            .icon(() -> ItemRegistry.NIGHT_SIGHT_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                safeAccept(output, ItemRegistry.NIGHT_SIGHT_ITEM.get());
                safeAccept(output, ItemRegistry.HEAL_ITEM.get());
                safeAccept(output, ItemRegistry.MAGIC_ARROW_ITEM.get());
                safeAccept(output, ItemRegistry.CLUMSY_ITEM.get());
                safeAccept(output, ItemRegistry.WEAKNESS_ITEM.get());
                safeAccept(output, ItemRegistry.CREATE_FOOD_ITEM.get());
                safeAccept(output, ItemRegistry.FEEBLEMIND_ITEM.get());
                safeAccept(output, ItemRegistry.REACTIVE_ARMOR_ITEM.get());
            }).build());

    // Tab 5: Ores
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_ORE_TAB = CREATIVE_TABS.register(
        "britannia_ore_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.britannia_ore_tab"))
            .icon(() -> Item.BY_BLOCK.get(BlockRegistry.VALORITE_ORE.get()).getDefaultInstance())
            .displayItems((parameters, output) -> {
                safeAccept(output, ItemRegistry.COPPER_ORE_ITEM.get());
                safeAccept(output, ItemRegistry.TIN_ORE_ITEM.get());
                safeAccept(output, ItemRegistry.SILVER_ORE_ITEM.get());
                safeAccept(output, ItemRegistry.GOLD_ORE_ITEM.get());
                safeAccept(output, ItemRegistry.SHADOW_IRON_ORE_ITEM.get());
                safeAccept(output, ItemRegistry.AGAPITE_ORE_ITEM.get());
                safeAccept(output, ItemRegistry.VERITE_ORE_ITEM.get());
                safeAccept(output, ItemRegistry.VALORITE_ORE_ITEM.get());
                safeAccept(output, ItemRegistry.HIGH_PURITY_SILVER_ORE_ITEM.get());
                safeAccept(output, ItemRegistry.IGNEOUS_ROCK_ITEM.get());
                safeAccept(output, ItemRegistry.METAMORPHIC_ROCK_ITEM.get());
                safeAccept(output, ItemRegistry.VOLCANIC_ROCK_ITEM.get());
                safeAccept(output, ItemRegistry.GLACIAL_ROCK_ITEM.get());
            }).build());

    // Utility method
    private static void safeAccept(CreativeModeTab.Output output, Item item) {
        if (item != null) {
            output.accept(item.getDefaultInstance());
        } else {
            System.err.println("Warning: Attempted to add a null item to the creative tab.");
        }
    }


    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}
