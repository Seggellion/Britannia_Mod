// ClientModSetup.java
package com.seggellion.britannia_mod;

import com.seggellion.britannia_mod.client.renderer.entity.EntityHorseMerchantRenderer;
import com.seggellion.britannia_mod.client.renderer.LivingSeatRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EmptyRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.FishTraderEntityRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.QuestGiverEntityRenderer;

import com.seggellion.britannia_mod.client.renderer.entity.SalvageTraderEntityRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.AlcoholTraderEntityRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.CitizenEntityRenderer;
import com.seggellion.britannia_mod.client.renderer.CityNameBlockRenderer;
import com.seggellion.britannia_mod.client.gui.screen.ArchitectScreen;
import com.seggellion.britannia_mod.client.BritainMusicHandler;
import com.seggellion.britannia_mod.client.ModModelLayers;
import com.seggellion.britannia_mod.client.ShameDungeonMusicHandler;
import com.seggellion.britannia_mod.client.SwampEnvironmentEffects;
import com.seggellion.britannia_mod.client.ThinWallClient;
import com.seggellion.britannia_mod.client.house.GhostStructurePreviewRenderer;
import com.seggellion.britannia_mod.client.model.ThinWallModels;
import com.seggellion.britannia_mod.client.renderer.entity.EntityWoodMerchantRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EntityMetalMerchantRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EntityStoneMerchantRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.TownPersonEntityRenderer;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.CropVisualRotation;
import com.seggellion.britannia_mod.farming.GrapeVisualResolver;
import com.seggellion.britannia_mod.client.renderer.ArchitectRenderer;
import com.seggellion.britannia_mod.client.Keybinds;
import com.seggellion.britannia_mod.client.renderer.FarmingBlockEntityRenderer;
import com.seggellion.britannia_mod.client.renderer.FlowerBlockEntityRenderer;
import com.seggellion.britannia_mod.client.renderer.HouseFarmPlotBlockEntityRenderer;
import com.seggellion.britannia_mod.client.renderer.FlowerVisualModels;
import com.seggellion.britannia_mod.client.renderer.ManagedFlowerBlockEntityRenderer;
import com.seggellion.britannia_mod.client.renderer.WineBottleBlockEntityRenderer;
import com.seggellion.britannia_mod.client.renderer.MoongateBlockEntityRenderer;
import com.seggellion.britannia_mod.client.renderer.shrine.ShrineRenderer;
import com.seggellion.britannia_mod.client.banner.BannerBlockEntityRenderer;
import com.seggellion.britannia_mod.client.screen.BritanniaSpawnScreen;
import com.seggellion.britannia_mod.event.ClientEventHandler;
import net.neoforged.neoforge.client.event.ModelEvent;
import com.seggellion.britannia_mod.client.model.StoneFloorGeometryLoader;
import net.minecraft.world.entity.EntityType;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import com.seggellion.britannia_mod.block.LargeForgeRenderer;
import com.seggellion.britannia_mod.block.BlueTentRenderer;
import com.seggellion.britannia_mod.block.AdaptiveRoofRenderer;
import com.seggellion.britannia_mod.block.PurpleTentRenderer;
import com.seggellion.britannia_mod.block.renderer.ArmoireRenderer;
import com.seggellion.britannia_mod.block.renderer.TrainingDummyRenderer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
//import com.seggellion.britannia_mod.block.HouseSignRenderer;
import com.seggellion.britannia_mod.block.SmallForgeRenderer;
import com.seggellion.britannia_mod.block.ChairRenderer;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.client.renderer.entity.BaseBritanniaRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.IbisRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.FlamingoRenderer;
import net.minecraft.client.renderer.entity.CatRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.CustomVillagerRenderer;
import com.seggellion.britannia_mod.client.renderer.ThreeHeightLightRenderer;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.registry.LargeStructureRegistry;
import com.seggellion.britannia_mod.registry.BannerBlockRegistry;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.item.QualityShovelItem;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import com.seggellion.britannia_mod.item.QualityToolItem;
import net.minecraft.world.item.component.CustomModelData;
import com.seggellion.britannia_mod.ui.ManaOverlayScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.minecraft.world.item.Item;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.registry.WeaponRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.registry.BlacksmithItemRegistry;
import com.seggellion.britannia_mod.item.BlacksmithEquipmentItem;
import com.seggellion.britannia_mod.item.BlacksmithItemData;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.skill.crafting.MaterialProfileRegistry;
import com.seggellion.britannia_mod.client.ClientOnlyItemRegistry;
import net.neoforged.bus.api.SubscribeEvent;


@EventBusSubscriber(modid = BritanniaMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModSetup {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean clientGameHandlersRegistered = false;

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(
                com.seggellion.britannia_mod.registry.MenuRegistry.SERVICE_NPC_SPAWN_MENU.get(),
                com.seggellion.britannia_mod.client.screen.ServiceNpcSpawnScreen::new
        );
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        new ClientOnlyItemRegistry().registerSpawnEggColors(event);

        // -- Weapon Tints --
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) return -1;

            String materialName = "iron"; // fallback
            if (stack.getItem() instanceof QualitySwordItem) {
                materialName = QualitySwordItem.getMaterial(stack);
            } else if (stack.getItem() instanceof QualityToolItem) {
                materialName = QualityToolItem.getMaterial(stack);
            } else if (stack.getItem() instanceof QualityShovelItem) {
                materialName = QualityShovelItem.getMaterial(stack);
            }

            // Get the base tint
            int tint = getTintForOreType(materialName);
            // Force Alpha to 100% and strip any existing alpha data
            return 0xFF000000 | (tint & 0xFFFFFF);
            
        }, WeaponRegistry.VIKING_SWORD.get(), WeaponRegistry.DAGGER.get(), ToolRegistry.PICKAXE.get(), ToolRegistry.SHOVEL.get());

        event.register((stack, tintIndex) -> {
            if (tintIndex != 0 || !(stack.getItem() instanceof BlacksmithEquipmentItem equipment)
                    || !equipment.definition().retainsMaterialColor()) return -1;
            String stored = BlacksmithItemData.materialId(stack);
            UOMetalToolMaterial material = stored == null ? UOMetalToolMaterial.IRON
                    : UOMetalToolMaterial.getMaterialByName(stored.replace('_', ' '));
            var profile = material == null ? null : MaterialProfileRegistry.get(material);
            return profile == null ? -1 : 0xFF000000 | profile.tint();
        }, BlacksmithItemRegistry.catalogueItems());


        event.register((stack, tintIndex) -> {
            if (!(stack.getItem() instanceof GradeStoneItem gradeStoneItem)) {
                return -1; // Default (no tint applied)
            }

            String stoneType = gradeStoneItem.getStoneType(stack);
            if (tintIndex == 0) {
                int tint = getTintForStoneType(stoneType);
                return tint | 0xFF000000; 
            } else if (tintIndex == 1) {
                int tint = getTintForStoneType(stoneType);
                return tint | 0xFF000000; 
            }

            return -1; // Default (no tint applied)
        }, ItemRegistry.GRADE_STONE_ITEM.get());


        // -- PurityOreItem Tints (NEW) --
        event.register((stack, tintIndex) -> {
            if (!(stack.getItem() instanceof PurityOreItem purityOreItem)) {
                return -1; // Default (no tint)
            }
            String oreType = purityOreItem.getOreType(stack);

            // Typically, we only color layer0 or layer1. 
            // If your .json has 2 layers, you can handle them differently if you wish.
            if (tintIndex == 0) {
               int tint = getTintForOreType(oreType);
           //      int tint = applyBrightnessTint(getTintForOreType(oreType), 2.0F); 
                return tint | 0xFF000000;
            } else if (tintIndex == 1) {
                // same or different
                int tint = getTintForOreType(oreType);
                return tint | 0xFF000000;
            }
            return -1;
        }, ItemRegistry.PURITY_ORE_ITEM.get());

        //ingots
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) return -1; // Only tint the base layer

            Item item = stack.getItem();
            int tint = getTintForIngotItem(item);

            return tint | 0xFF000000; // Ensure full opacity
        },
            ItemRegistry.SHADOW_IRON_INGOT.get(),
            ItemRegistry.VALORITE_INGOT.get(),
            ItemRegistry.VERITE_INGOT.get(),
            ItemRegistry.AGAPITE_INGOT.get(),
            ItemRegistry.COPPER_INGOT.get(),
            ItemRegistry.TIN_INGOT.get()
        );

        // Pickaxe tinting
        event.register((stack, tintIndex) -> {
            boolean customMetalTool = stack.getItem() instanceof QualityToolItem || stack.getItem() instanceof QualityShovelItem;
            if (!customMetalTool) return -1;
            
            if (tintIndex == 0) {
                String metalType = stack.getItem() instanceof QualityShovelItem
                        ? QualityShovelItem.getMaterial(stack)
                        : QualityToolItem.getMaterial(stack);

                if (metalType == null) return -1; // ✅ prevent crash

                int tint = switch (metalType.toLowerCase()) {
                    case "gold"     -> 0xFFD700;
                    case "iron"     -> 0xC6C6C6;
                    case "valorite" -> 0x3BA4B9;
                    case "agapite"  -> 0xE07EA3;
                    case "verite"   -> 0x40A050;
                    case "copper"   -> 0xB87333;
                    case "tin"      -> 0xC0C0C0;
                    case "silver"   -> 0xE0E0E0;
                    default         -> 0xFFFFFF;
                };

                return tint | 0xFF000000;
            }

            return -1;
        }, ToolRegistry.PICKAXE.get(), ToolRegistry.SHOVEL.get());

    }

    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "stone_floor_loader"),
            StoneFloorGeometryLoader.INSTANCE
        );
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(MoongateBlockEntityRenderer.BILLBOARD_MODEL);
        event.register(ModelResourceLocation.standalone(
            ResourceLocation.parse("britannia_mod:block/structure/thin_wall_stair_fill")
        ));
        event.register(ModelResourceLocation.standalone(
            ResourceLocation.parse("britannia_mod:block/structure/thin_wall_corner_fill")
        ));
        registerFarmingCropModels(event);
        registerFlowerModels(event);
    }

    private static void registerFarmingCropModels(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation grapeModel : GrapeVisualResolver.allModelLocations()) {
            event.register(ModelResourceLocation.standalone(grapeModel));
        }
        for (CropDefinition crop : CropRegistry.all()) {
            if (!CropVisualRotation.isEnabledFor(crop)) {
                continue;
            }
            if ("grapes".equals(crop.id())) {
                continue;
            }
            for (int growthStage = 0; growthStage < crop.growthStages(); growthStage++) {
                if (FarmingBlockEntityRenderer.hasRenderableCropModel(crop, growthStage)) {
                    event.register(ModelResourceLocation.standalone(FarmingBlockEntityRenderer.cropModelLocation(crop, growthStage)));
                }
            }
        }
    }

    private static void registerFlowerModels(ModelEvent.RegisterAdditional event) {
        for (ModelResourceLocation model : FlowerVisualModels.allModelLocations()) {
            event.register(model);
        }
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ThinWallModels.onModifyBakingResults(event);
        new ThinWallClient().onModifyBaking(event);
        FlowerVisualModels.onModelsReloaded();
        FlowerBlockEntityRenderer.onModelsReloaded();
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        ModModelLayers.onRegisterLayerDefinitions(event);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        Keybinds.registerKeys(event);
    }

    @SubscribeEvent
    public static void registerClientPackets(RegisterPayloadHandlersEvent event) {
        ClientEventHandler.registerClientPackets(event);
    }



    private static int applyBrightnessTint(int baseColor, float factor) {
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        r = Math.min(255, (int) (r * factor));
        g = Math.min(255, (int) (g * factor));
        b = Math.min(255, (int) (b * factor));

        return (r << 16) | (g << 8) | b;
    }


    private static int desaturateColor(int rgb, double saturationFactor) {
        // Extract RGB components
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // Convert RGB to HSB
        float[] hsb = rgbToHsb(r, g, b);
        
        // Apply desaturation
        hsb[1] = (float) Math.max(0, Math.min(1, hsb[1] * saturationFactor));
        
        // Convert back to RGB
        return hsbToRgb(hsb[0], hsb[1], hsb[2]);
    }

    // Manual RGB to HSB conversion
    private static float[] rgbToHsb(int r, int g, int b) {
        float[] hsb = new float[3];
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        // Brightness
        hsb[2] = max / 255f;

        // Saturation
        hsb[1] = (max != 0) ? delta / max : 0;

        // Hue
        if (delta == 0) {
            hsb[0] = 0;
        } else {
            float hue;
            if (max == r) {
                hue = ((g - b) / delta) * 60f;
            } else if (max == g) {
                hue = ((b - r) / delta + 2) * 60f;
            } else {
                hue = ((r - g) / delta + 4) * 60f;
            }
            hsb[0] = (hue < 0) ? hue + 360 : hue;
        }
        
        return hsb;
    }

    // Manual HSB to RGB conversion
    private static int hsbToRgb(float hue, float saturation, float brightness) {
        int r = 0, g = 0, b = 0;
        if (saturation == 0) {
            r = g = b = (int) (brightness * 255);
        } else {
            float h = (hue % 360) / 60f;
            int i = (int) h;
            float f = h - i;
            float p = brightness * (1 - saturation);
            float q = brightness * (1 - saturation * f);
            float t = brightness * (1 - saturation * (1 - f));

            switch (i) {
                case 0 -> { r = (int) (brightness * 255); g = (int) (t * 255); b = (int) (p * 255); }
                case 1 -> { r = (int) (q * 255); g = (int) (brightness * 255); b = (int) (p * 255); }
                case 2 -> { r = (int) (p * 255); g = (int) (brightness * 255); b = (int) (t * 255); }
                case 3 -> { r = (int) (p * 255); g = (int) (q * 255); b = (int) (brightness * 255); }
                case 4 -> { r = (int) (t * 255); g = (int) (p * 255); b = (int) (brightness * 255); }
                case 5 -> { r = (int) (brightness * 255); g = (int) (p * 255); b = (int) (q * 255); }
            }
        }
        return (r << 16) | (g << 8) | b;
    }

    private static int adjustBrightness(int color, double factor) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        r = (int) Math.min(255, r * factor);
        g = (int) Math.min(255, g * factor);
        b = (int) Math.min(255, b * factor);

        return (r << 16) | (g << 8) | b;
    }

    private static int applyGreyscaleTint(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // Convert to grayscale by adjusting RGB based on weight
        int grey = (int) ((r * 0.3) + (g * 0.59) + (b * 0.11)); // Weighted for human perception

        return (grey << 16) | (grey << 8) | grey; // Return as grayscale
    }


    private static int applyHueShift(int color, float hueShift, float brightnessFactor) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // Convert RGB to HSB (Hue, Saturation, Brightness)
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        
        // Apply the hue shift and adjust brightness
        float newHue = (hsb[0] + hueShift) % 1.0f;
        float newBrightness = Math.min(1.0f, hsb[2] * brightnessFactor);

        // Convert back to RGB
        int newColor = java.awt.Color.HSBtoRGB(newHue, hsb[1], newBrightness);
        
        return newColor;
    }

    private static int getTintForIngotItem(Item item) {
        if (item == ItemRegistry.SHADOW_IRON_INGOT.get()) return 0x3A3A3A;
        if (item == ItemRegistry.VALORITE_INGOT.get())    return 0x00CCFF;
        if (item == ItemRegistry.VERITE_INGOT.get())      return 0x66FF66;
        if (item == ItemRegistry.AGAPITE_INGOT.get())     return 0xFF9999;
        if (item == ItemRegistry.COPPER_INGOT.get())      return 0xB87333;
        if (item == ItemRegistry.TIN_INGOT.get())         return 0xCCCCCC;
        return 0xFFFFFF; // fallback white if somehow not matched
    };

    private static int getTintForOreType(String oreType) {
        // Remove " ore" if it exists so "Valorite Ore" and "valorite" both become "valorite"
        String type = oreType.toLowerCase().replace(" ore", "").trim();
        
        return switch (type) {
            case "tin"        -> adjustBrightness(desaturateColor(0xC0C0C0, 0.6), 0.95);
            case "silver"     -> adjustBrightness(desaturateColor(0xC0C0C0, 0.5), 3.0);
            case "gold"       -> 0xCEAD39;
            case "iron"       -> 0xC6C6C6;
            case "shadow iron"-> 0x303030; 
            case "valorite"   -> 0x3BA4B9;
            case "agapite"    -> 0xE07EA3;
            case "verite"     -> 0x40A050;
            case "copper"     -> 0xB87333;
            default           -> 0xFFFFFF; // Pure white
        };
    };

    private static int getTintForStoneType(String stoneType) {
        return switch (stoneType.toLowerCase()) {
            case "sandstone" -> 0xFFC96B;  // Light brown
            case "marble" -> 0xE0E0E0;     // Light grey
            case "granite" -> 0xC67A6A;    // Reddish-brown
            case "basalt" -> 0x3D3D3D;     // Dark grey
            default -> 0xFFFFFF;           // Default white
        };
    };


    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {

        // Block Entity Renderers
        event.registerBlockEntityRenderer(BlockRegistry.BLUE_TENT_BLOCK_ENTITY_TYPE.get(), BlueTentRenderer::new);
        event.registerBlockEntityRenderer(BlockRegistry.PURPLE_TENT_BLOCK_ENTITY_TYPE.get(), PurpleTentRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityRegistry.ADAPTIVE_ROOF.get(), AdaptiveRoofRenderer::new);
        event.registerBlockEntityRenderer(com.seggellion.britannia_mod.registry.GrabbyRegistry.PLACED_ITEM_BLOCK_ENTITY.get(),
                com.seggellion.britannia_mod.client.renderer.GrabbyPlacedItemRenderer::new);
        event.registerBlockEntityRenderer(BlockRegistry.ARMOIRE_BLOCK_ENTITY_TYPE.get(), ArmoireRenderer::new);
        event.registerBlockEntityRenderer(BlockRegistry.TRAINING_DUMMY_BLOCK_ENTITY_TYPE.get(), TrainingDummyRenderer::new);
        event.registerBlockEntityRenderer(BlockRegistry.MOONGATE_BLOCK_ENTITY_TYPE.get(), MoongateBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityRegistry.THREE_HEIGHT_LIGHT_BLOCK_ENTITY_TYPE.get(), ThreeHeightLightRenderer::new);
        event.registerBlockEntityRenderer(BlockRegistry.LARGE_FORGE_BLOCK_ENTITY_TYPE.get(), LargeForgeRenderer::new);
        event.registerBlockEntityRenderer(BlockRegistry.SMALL_FORGE_BLOCK_ENTITY_TYPE.get(), SmallForgeRenderer::new);
        event.registerBlockEntityRenderer(BlockRegistry.WOOD_SPAWN_BLOCK_ENTITY_TYPE.get(), CityNameBlockRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityRegistry.ARCHITECT_SPAWN_BLOCK_ENTITY_TYPE.get(), CityNameBlockRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityRegistry.WINE_BOTTLE_BE.get(), WineBottleBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityRegistry.FARMING_BLOCK_BE.get(), FarmingBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityRegistry.FLOWER_BLOCK_BE.get(), FlowerBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityRegistry.HOUSE_FARM_PLOT_BE.get(), HouseFarmPlotBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                BlockEntityRegistry.MANAGED_FLOWER_BE.get(), ManagedFlowerBlockEntityRenderer::new
        );
        event.registerBlockEntityRenderer(LargeStructureRegistry.LARGE_STRUCTURE.get(), ShrineRenderer::new);
        event.registerBlockEntityRenderer(
                BannerBlockRegistry.BANNER_BLOCK_ENTITY.get(), BannerBlockEntityRenderer::new);
        // Entity Renderers
      //  event.registerEntityRenderer(EntityType.VILLAGER, CustomVillagerRenderer::new);
        event.registerEntityRenderer(EntityRegistry.SEAT_ENTITY.get(), LivingSeatRenderer::new);
        event.registerEntityRenderer(EntityRegistry.LAY_ENTITY.get(), EmptyRenderer::new);

        event.registerEntityRenderer(EntityRegistry.CUSTOM_CAT_ENTITY.get(), CatRenderer::new);


        event.registerEntityRenderer(EntityRegistry.MONGBAT_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.5F, 0.25F)); 
        
        // Animal
        event.registerEntityRenderer(EntityRegistry.HIND_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.0F, 0.3F));
        event.registerEntityRenderer(EntityRegistry.GIANT_RAT_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.0F, 0.3F));

        // Monsters
        event.registerEntityRenderer(EntityRegistry.ALLIGATOR_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.0F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.SERPENT_GIANT_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.8F, 0.25F)); // Downscaled
        event.registerEntityRenderer(EntityRegistry.SERPENT_LAVA_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.8F, 0.25F));
        event.registerEntityRenderer(EntityRegistry.SERPENT_ICE_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.6F, 0.25F));
        event.registerEntityRenderer(EntityRegistry.SERPENT_SEA_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.8F, 0.25F));
        event.registerEntityRenderer(EntityRegistry.SERPENT_DEEP_SEA_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.0F, 0.25F));
        event.registerEntityRenderer(EntityRegistry.SERPENT_CRYSTAL_SEA_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.8F, 0.25F));

        event.registerEntityRenderer(EntityRegistry.FIRE_ELEMENTAL_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.2F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.WATER_ELEMENTAL_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.2F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.POISON_ELEMENTAL_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.2F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.ACID_ELEMENTAL_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.2F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.BLOOD_ELEMENTAL_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.2F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.AIR_ELEMENTAL_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.2F, 0.6F));

        event.registerEntityRenderer(EntityRegistry.EARTH_ELEMENTAL_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.1F, 2.0F));
        event.registerEntityRenderer(EntityRegistry.GOLD_ORE_ELEMENTAL_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.1F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.SHADOW_ORE_ELEMENTAL_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.1F, 0.6F));

        event.registerEntityRenderer(EntityRegistry.DULL_COPPER_ELEMENTAL_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.0F, 2.2F));

        event.registerEntityRenderer(EntityRegistry.BRITANNIA_CAT_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.0F, 2.2F));

        event.registerEntityRenderer(EntityRegistry.SERPENT_SILVER_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.0F, 0.5F));
        event.registerEntityRenderer(EntityRegistry.SCORPION_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.8F, 0.5F));
        event.registerEntityRenderer(EntityRegistry.LIZARDMAN_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.0F, 0.5F));
        event.registerEntityRenderer(EntityRegistry.RATMAN_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.7F, 0.4F));
        event.registerEntityRenderer(EntityRegistry.RATMAN_ARCHER_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.7F, 0.4F));
        event.registerEntityRenderer(EntityRegistry.RATMAN_ASSASSIN_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.7F, 0.4F));

        event.registerEntityRenderer(EntityRegistry.HARPY_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.9F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.HEADLESS_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.6F, 0.5F));
        event.registerEntityRenderer(EntityRegistry.ORC_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.8F, 0.8F)); 
        event.registerEntityRenderer(EntityRegistry.ORC_LORD_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.8F, 0.8F)); 
        event.registerEntityRenderer(EntityRegistry.ORC_CLUB_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.8F, 0.8F));

        event.registerEntityRenderer(EntityRegistry.ETTIN_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.1F, 0.8F)); 
        event.registerEntityRenderer(EntityRegistry.OGRE_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.4F, 0.8F)); 
        event.registerEntityRenderer(EntityRegistry.OGRE_ARCTIC_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.3F, 0.8F)); // Upscaled
        event.registerEntityRenderer(EntityRegistry.OGRE_LORD_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.6F, 1.0F)); // Upscaled
        event.registerEntityRenderer(EntityRegistry.OGRE_LORD_ARCTIC_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.5F, 1.0F)); // Upscaled

        event.registerEntityRenderer(EntityRegistry.TROLL_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.3F, 0.7F)); // Upscaled
        event.registerEntityRenderer(EntityRegistry.GARGOYLE_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.8F, 0.8F));
        event.registerEntityRenderer(EntityRegistry.GARGOYLE_DESTROYER_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.85F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.GARGOYLE_ENFORCER_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.82F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.GARGOYLE_STONE_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.75F, 0.6F));

        //needs resizing
        event.registerEntityRenderer(EntityRegistry.HIND_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.8F, 0.8F));
        event.registerEntityRenderer(EntityRegistry.GREAT_HART_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.9F, 0.9F));
        event.registerEntityRenderer(EntityRegistry.BEAR_BROWN_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.2F, 1.2F));
        event.registerEntityRenderer(EntityRegistry.BEAR_BLACK_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.1F, 1.1F));
        event.registerEntityRenderer(EntityRegistry.BEAR_POLAR_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.4F, 1.4F));
        event.registerEntityRenderer(EntityRegistry.BEAR_GRIZZLY_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.3F, 1.3F));
        event.registerEntityRenderer(EntityRegistry.TURKEY_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.8F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.IBIS_ENTITY.get(), IbisRenderer::new);
        event.registerEntityRenderer(EntityRegistry.FLAMINGO_ENTITY.get(), FlamingoRenderer::new);
        event.registerEntityRenderer(EntityRegistry.GORILLA_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.5F, 0.6F));

        event.registerEntityRenderer(EntityRegistry.DAEMON_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.1F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.LICH_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 0.8F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.RAT_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.1F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.WRAITH_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.1F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.GHOUL_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.1F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.SHADE_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.1F, 0.6F));
        event.registerEntityRenderer(EntityRegistry.WISP_ENTITY.get(), context -> new BaseBritanniaRenderer<>(context, 1.1F, 0.6F));


        event.registerEntityRenderer(EntityRegistry.HORSE_MERCHANT_ENTITY.get(), EntityHorseMerchantRenderer::new);
        // event.registerEntityRenderer(EntityRegistry.FISH_MERCHANT_ENTITY.get(), FishTraderEntityRenderer::new);
       
       
        event.registerEntityRenderer(EntityRegistry.WOOD_MERCHANT_ENTITY.get(), EntityWoodMerchantRenderer::new);
        // event.registerEntityRenderer(EntityRegistry.JOURNEYMAN_BLACKSMITH_ENTITY.get(), EntityJourneymanBlacksmithRenderer::new);
        event.registerEntityRenderer(EntityRegistry.STONE_MERCHANT_ENTITY.get(), EntityStoneMerchantRenderer::new);
        event.registerEntityRenderer(EntityRegistry.METAL_MERCHANT_ENTITY.get(), EntityMetalMerchantRenderer::new);
        event.registerEntityRenderer(EntityRegistry.TOWNSPERSON.get(), CitizenEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.ARCHITECT_ENTITY.get(), ArchitectRenderer::new);
        event.registerEntityRenderer(EntityRegistry.FISH_TRADER.get(), FishTraderEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.QUEST_GIVER.get(), QuestGiverEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.SALVAGE_TRADER.get(), SalvageTraderEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.ALCOHOL_TRADER.get(), CitizenEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.SERVICE_NPC.get(), CitizenEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.MEAT_TRADER.get(), CitizenEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.ORE_TRADER.get(), CitizenEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.STONE_TRADER.get(), CitizenEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.GRAIN_TRADER.get(), CitizenEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.PRODUCE_TRADER.get(), CitizenEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.FUR_LEATHER_TRADER.get(), CitizenEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.BAKER.get(), CitizenEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.VENDOR.get(), CitizenEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.TAVERNKEEPER.get(), CitizenEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.COSTERMONGER.get(), CitizenEntityRenderer::new);
    }


    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientEventHandler.onClientSetup(event);

        if (!clientGameHandlersRegistered) {
            Keybinds.registerInputHandler();
            NeoForge.EVENT_BUS.addListener(ClientEventHandler::onGameModeChange);
            NeoForge.EVENT_BUS.addListener(ClientEventHandler::onClientTick);
            NeoForge.EVENT_BUS.addListener(ClientEventHandler::onBlockRightClick);
            NeoForge.EVENT_BUS.addListener(ClientEventHandler::onClientLogin);
            NeoForge.EVENT_BUS.addListener(ClientEventHandler::onClientLogout);
            NeoForge.EVENT_BUS.addListener(ClientEventHandler::onRenderNameTag);
            NeoForge.EVENT_BUS.register(ShameDungeonMusicHandler.class);
            NeoForge.EVENT_BUS.register(BritainMusicHandler.class);
            NeoForge.EVENT_BUS.register(GhostStructurePreviewRenderer.class);
            NeoForge.EVENT_BUS.register(SwampEnvironmentEffects.class);
            clientGameHandlersRegistered = true;
        }

        // Initialize the ClientOnlyItemRegistry
        ClientOnlyItemRegistry registry = new ClientOnlyItemRegistry();

        // Register the mana overlay (render it during the HUD)
        ManaOverlayScreen.register();

        event.enqueueWork(() -> {

            // Register the blocking property for the Order Shield
            ItemProperties.register(
                ItemRegistry.ORDER_SHIELD.get(),
                ResourceLocation.fromNamespaceAndPath("minecraft", "blocking"),
                (stack, world, entity, seed) -> {
                    return entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F;
                }
            );

            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_1X1.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_1X2.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_1X3.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_2X2.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_2X3.get(), RenderType.cutout());

            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.PLASTER_ORNATE_WALL_UPPER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.PLASTER_ORNATE_WALL_1.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.PLASTER_ORNATE_WALL_2.get(), RenderType.cutout());
            // Their glazing samples the transparent "ornateness" sheet; on the solid layer Minecraft
            // ignores alpha and those panes render as opaque black rectangles.
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.PLASTER_WALL_LARGE_WINDOW.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.ORNATE_WALL_LARGE_WINDOW.get(), RenderType.cutout());



            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_COBBLESTONE_1X2.get(), RenderType.cutout());

            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_CROSS_1X1.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_CROSS_1X2.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_CROSS_1X3.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_CROSS_2X2.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_CROSS_2X3.get(), RenderType.cutout());

            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_BIRCH_1X1.get(), RenderType.cutout());

            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.IRON_CEMETERY_GATE_ARCH.get(), RenderType.cutout());
       
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.LYING_SKELETON.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.SITTING_SKELETON.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.SKELETON_TORSO.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WOODEN_OPEN_COFFIN.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WOODEN_COFFIN_SKELETON.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.BRAZIER_SMALL.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.GLOBE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.FERN.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.HEDGE_BUSH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.POOL_OF_BLOOD.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.FOLDED_CLOTH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.MERCHANT_CART_RED.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.MERCHANT_CART_PURPLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.MERCHANT_CART_BLUE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.MERCHANT_CART_GREEN.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.MERCHANT_CART_YELLOW.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.MERCHANT_CART_WHITE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.MARKET_STALL_RED.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.MARKET_STALL_BLUE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.MARKET_STALL_GREEN.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.MARKET_STALL_PURPLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.SCARECROW.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.DRESS_FORM.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.LOOM.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.SPINNING_WHEEL.get(), RenderType.cutout());
            // Cutout rather than translucent: the translucent chunk layer is drawn by a shader
            // pack's gbuffers_water program, and packs discard geometry there that they do not
            // recognise as water, which left the fountain's water invisible in world under Iris
            // while the same model drew correctly in hand through gbuffers_entities. Cutout routes
            // it through gbuffers_terrain instead. The water is 79-96% opaque on every visible
            // pixel, so the alpha test costs little; see FountainWaterGeometryTest.
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.FOUNTAIN.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.SMALL_CRATE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.MEDIUM_CRATE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.LARGE_CRATE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WATER_WELL.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.LADDER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.DISPLAY_CASE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.MOONGATE_BLOCK.get(), RenderType.translucent());

            ItemBlockRenderTypes.setRenderLayer(BlockRegistry.CAVE_FLOOR_BLOCK.get(), RenderType.solid());

            ItemBlockRenderTypes.setRenderLayer(
                BlockRegistry.STATUE_MAN.get(),
                RenderType.translucent()
            );

            // Register custom model data for GradeStoneItem
            registry.registerModelData(
                ItemRegistry.GRADE_STONE_ITEM.get(),
                stack -> {
                    if (stack.getItem() instanceof GradeStoneItem gradeStoneItem) {
                        int customModelData = gradeStoneItem.getCustomModelData(stack);
                        return customModelData;
                    }
                    return 0;
                });

        });
    }
}
