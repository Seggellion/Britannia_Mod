// ClientModSetup.java
package com.seggellion.britannia_mod;

import com.seggellion.britannia_mod.client.renderer.entity.EntityHorseMerchantRenderer;
import com.seggellion.britannia_mod.client.renderer.LivingSeatRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EmptyRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EntityFishMerchantRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EntityWoodMerchantRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EntityMetalMerchantRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EntityStoneMerchantRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.TownPersonEntityRenderer;
import net.neoforged.neoforge.client.event.ModelEvent;
import com.seggellion.britannia_mod.client.model.StoneFloorGeometryLoader;
import net.minecraft.world.entity.EntityType;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import com.seggellion.britannia_mod.block.LargeForgeRenderer;
import com.seggellion.britannia_mod.block.BlueTentRenderer;
import com.seggellion.britannia_mod.block.AdaptiveRoofRenderer;
import com.seggellion.britannia_mod.block.PurpleTentRenderer;

//import com.seggellion.britannia_mod.block.HouseSignRenderer;
import com.seggellion.britannia_mod.block.SmallForgeRenderer;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.client.renderer.entity.MongbatRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.DaemonRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.LichRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.RatRenderer;
import net.minecraft.client.renderer.entity.CatRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.WraithRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.GhoulRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.ShadeRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.ShadowOreElementalRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.GoldOreElementalRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EarthElementalRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.CustomVillagerRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.WispRenderer;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import com.seggellion.britannia_mod.item.QualityToolItem;
import net.minecraft.world.item.component.CustomModelData;
import com.seggellion.britannia_mod.ui.ManaOverlayScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.minecraft.world.item.Item;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.registry.SwordRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.client.ClientOnlyItemRegistry;
import net.neoforged.bus.api.SubscribeEvent;


public class ClientModSetup {
    private static final Logger LOGGER = LogUtils.getLogger();
  
@SubscribeEvent
public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {

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
    if (!(stack.getItem() instanceof QualityToolItem tool)) return -1;
    
    if (tintIndex == 0) {
        int modelData = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value();
        String material = QualityToolItem.getMaterialFromModelData(modelData);

        if (material == null) return -1; // ✅ prevent crash

        int tint = switch (material.toLowerCase()) {
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
}, ToolRegistry.PICKAXE.get());


// Sword tinting
    event.register((stack, tintIndex) -> {
        
        if (!(stack.getItem() instanceof QualitySwordItem swordItem)) {
            return -1; // Default (no tint)
        }

        if (tintIndex == 0) { // Blade tinting
 
        int modelData = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value();
        String metalType = QualitySwordItem.getMaterialFromModelData(modelData);

        int tint = switch (metalType) {
                case "tin"        -> adjustBrightness(desaturateColor(0xC0C0C0, 0.6), 0.95); // Desaturated gray
                case "silver"     -> adjustBrightness(desaturateColor(0xC0C0C0, 0.5), 3.0); // Brightened silver
                case "gold" -> 0xCEAD39;       // Gold tint
                case "iron" -> 0xC6C6C6;       // Iron tint
                case "valorite" -> 0x3BA4B9;   // Valorite tint
                case "agapite"    -> 0xE07EA3;  // Pink/magenta hue
                case "verite"     -> 0x40A050;  // Green hue
                case "copper"     -> 0xB87333;  // Brownish copper color
                default -> 0xFFFFFF;           // Default tint (white)
            };
            return tint | 0xFF000000; // Ensure full opacity
        }

        return -1; // No tint for other layers
    }, 
    SwordRegistry.VIKING_SWORD.get());

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
    event.register(ModelResourceLocation.standalone(
        ResourceLocation.parse("britannia_mod:block/structure/thin_wall_stair_fill")
    ));
        event.register(ModelResourceLocation.standalone(
        ResourceLocation.parse("britannia_mod:block/structure/thin_wall_corner_fill")
    ));
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
    return switch (oreType.toLowerCase()) {
        case "tin ore"        -> applyHueShift(applyGreyscaleTint(0xC0C0C0), 0.1f, 0.8f); // Light grey, slightly desaturated
        case "silver ore"     -> applyHueShift(applyGreyscaleTint(0xC0C0C0), 0.1f, 3.0f); // Bright silver
        case "gold ore"       -> 0xDBD748; // Example: golden
        case "shadow iron ore"-> 0x5C5C5C; // Darker gray
        case "valorite ore"   -> 0x3E92E3; 
        case "iron ore"   -> 0xFFFFFF; 
        case "agapite ore"    -> 0xF4A6C0;  // Pink/magenta hue
        case "verite ore"     -> 0x40A050;  // Green hue
        case "copper ore"     -> 0x8F4A14;  // Brownish copper color
        default -> 0xFFFFFF;              // fallback
    };
}


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

        event.registerEntityRenderer(EntityType.VILLAGER, CustomVillagerRenderer::new);
        event.registerBlockEntityRenderer(BlockRegistry.BLUE_TENT_BLOCK_ENTITY_TYPE.get(), BlueTentRenderer::new);
            event.registerBlockEntityRenderer(BlockRegistry.PURPLE_TENT_BLOCK_ENTITY_TYPE.get(), PurpleTentRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityRegistry.ADAPTIVE_ROOF.get(), AdaptiveRoofRenderer::new);

        event.registerBlockEntityRenderer(BlockRegistry.LARGE_FORGE_BLOCK_ENTITY_TYPE.get(), LargeForgeRenderer::new);
        event.registerBlockEntityRenderer(BlockRegistry.SMALL_FORGE_BLOCK_ENTITY_TYPE.get(), SmallForgeRenderer::new);
    }


    @OnlyIn(Dist.CLIENT)
    public static void onClientSetup(FMLClientSetupEvent event) {

    Minecraft.getInstance().execute(() -> {
        try {
            LOGGER.info("✅ Loading shader: brightness_shader.json");
            Minecraft.getInstance().gameRenderer.loadEffect(
                ResourceLocation.fromNamespaceAndPath("britannia_mod", "shaders/core/brightness_shader.json")
            );
            LOGGER.info("✅ Shader loaded successfully.");
        } catch (Exception e) {
            LOGGER.error("❌ Error loading shader: {}", e.getMessage());
        }
    });


// Initialize the ClientOnlyItemRegistry
        ClientOnlyItemRegistry registry = new ClientOnlyItemRegistry();


        // Register the mana overlay (render it during the HUD)
        ManaOverlayScreen.register();

        event.enqueueWork(() -> {

            // Register the entity renderers
            EntityRenderers.register(EntityRegistry.SEAT_ENTITY.get(), LivingSeatRenderer::new);
            EntityRenderers.register(EntityRegistry.LAY_ENTITY.get(), EmptyRenderer::new);
            EntityRenderers.register(EntityRegistry.MONGBAT_ENTITY.get(), MongbatRenderer::new);
            EntityRenderers.register(EntityRegistry.DAEMON_ENTITY.get(), DaemonRenderer::new);
            EntityRenderers.register(EntityRegistry.LICH_ENTITY.get(), LichRenderer::new);
            EntityRenderers.register(EntityRegistry.RAT_ENTITY.get(), RatRenderer::new);
            EntityRenderers.register(EntityRegistry.WRAITH_ENTITY.get(), WraithRenderer::new);
            EntityRenderers.register(EntityRegistry.GHOUL_ENTITY.get(), GhoulRenderer::new);
            EntityRenderers.register(EntityRegistry.SHADE_ENTITY.get(), ShadeRenderer::new);
            EntityRenderers.register(EntityRegistry.WISP_ENTITY.get(), WispRenderer::new);
            EntityRenderers.register(EntityRegistry.CUSTOM_CAT_ENTITY.get(), CatRenderer::new);
            EntityRenderers.register(EntityRegistry.EARTH_ELEMENTAL_ENTITY.get(), EarthElementalRenderer::new);
            EntityRenderers.register(EntityRegistry.GOLD_ORE_ELEMENTAL_ENTITY.get(), GoldOreElementalRenderer::new);
            EntityRenderers.register(EntityRegistry.SHADOW_ORE_ELEMENTAL_ENTITY.get(), ShadowOreElementalRenderer::new);
            EntityRenderers.register(EntityRegistry.HORSE_MERCHANT_ENTITY.get(), EntityHorseMerchantRenderer::new);
            EntityRenderers.register(EntityRegistry.FISH_MERCHANT_ENTITY.get(), EntityFishMerchantRenderer::new);
            EntityRenderers.register(EntityRegistry.WOOD_MERCHANT_ENTITY.get(), EntityWoodMerchantRenderer::new);
            //EntityRenderers.register(EntityRegistry.JOURNEYMAN_BLACKSMITH_ENTITY.get(), EntityJourneymanBlacksmithRenderer::new);
            EntityRenderers.register(EntityRegistry.STONE_MERCHANT_ENTITY.get(), EntityStoneMerchantRenderer::new);
            EntityRenderers.register(EntityRegistry.METAL_MERCHANT_ENTITY.get(), EntityMetalMerchantRenderer::new);
            EntityRenderers.register(EntityRegistry.TOWN_PERSON_ENTITY.get(), TownPersonEntityRenderer::new);

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

                ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_CROSS_1X1.get(), RenderType.cutout());
                ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_CROSS_1X2.get(), RenderType.cutout());
                ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_CROSS_1X3.get(), RenderType.cutout());
                ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_CROSS_2X2.get(), RenderType.cutout());
                ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_CROSS_2X3.get(), RenderType.cutout());

                ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WINDOW_BIRCH_1X1.get(), RenderType.cutout());
                ItemBlockRenderTypes.setRenderLayer(BlockRegistry.IRON_FENCE_1.get(), RenderType.cutout());
                ItemBlockRenderTypes.setRenderLayer(BlockRegistry.IRON_FENCE_2.get(), RenderType.cutout());
                ItemBlockRenderTypes.setRenderLayer(BlockRegistry.IRON_CEMETERY_GATE_ARCH.get(), RenderType.cutout());

                ItemBlockRenderTypes.setRenderLayer(BlockRegistry.LYING_SKELETON.get(), RenderType.cutout());
                ItemBlockRenderTypes.setRenderLayer(BlockRegistry.SITTING_SKELETON.get(), RenderType.cutout());
                ItemBlockRenderTypes.setRenderLayer(BlockRegistry.SKELETON_TORSO.get(), RenderType.cutout());
                ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WOODEN_OPEN_COFFIN.get(), RenderType.cutout());
                ItemBlockRenderTypes.setRenderLayer(BlockRegistry.WOODEN_COFFIN_SKELETON.get(), RenderType.cutout());


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
