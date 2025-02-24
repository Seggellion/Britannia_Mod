// ClientModSetup.java
package com.seggellion.britannia_mod;

import com.seggellion.britannia_mod.client.renderer.entity.EntityHorseMerchantRenderer;
//import com.seggellion.britannia_mod.client.renderer.entity.EntityJourneymanBlacksmithRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EntityFishMerchantRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EntityWoodMerchantRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EntityMetalMerchantRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.EntityStoneMerchantRenderer;
import com.seggellion.britannia_mod.client.renderer.entity.TownPersonEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.renderer.RenderType;
import com.seggellion.britannia_mod.block.LargeForgeRenderer;
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
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.world.item.Tier;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import net.neoforged.neoforge.common.SimpleTier;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.neoforge.client.event.ClientPlayerChangeGameTypeEvent;

import com.seggellion.britannia_mod.ui.ManaOverlayScreen;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.Minecraft;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.registry.SwordRegistry;
import com.seggellion.britannia_mod.client.ClientOnlyItemRegistry;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterNamedRenderTypesEvent;

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
            return tint | 0xFF000000;
        } else if (tintIndex == 1) {
            // same or different
            int tint = getTintForOreType(oreType);
            return tint | 0xFF000000;
        }
        return -1;
    }, ItemRegistry.PURITY_ORE_ITEM.get());


// Sword tinting
    event.register((stack, tintIndex) -> {
        
        if (!(stack.getItem() instanceof QualitySwordItem swordItem)) {
            return -1; // Default (no tint)
        }

        if (tintIndex == 0) { // Blade tinting
 
        int modelData = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value();
        String metalType = QualitySwordItem.getMaterialFromModelData(modelData);

        int tint = switch (metalType) {
                case "gold" -> 0xCEAD39;       // Gold tint
                case "iron" -> 0xC6C6C6;       // Iron tint
                case "valorite" -> 0x3BA4B9;   // Valorite tint
                default -> 0xFFFFFF;           // Default tint (white)
            };
            return tint | 0xFF000000; // Ensure full opacity
        }

        return -1; // No tint for other layers
    }, 
    SwordRegistry.VIKING_SWORD.get());

}

private static int getTintForOreType(String oreType) {
    return switch (oreType.toLowerCase()) {
        case "tin ore"       -> 0xD8D8D8; // Example: grayish
        case "gold ore"       -> 0xDBD748; // Example: golden
        case "shadow iron ore"-> 0x5C5C5C; // Darker gray
        case "valorite ore"   -> 0x9CB5CE; 
        case "iron ore"   -> 0xFFFFFF; 
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
        event.registerBlockEntityRenderer(BlockRegistry.LARGE_FORGE_BLOCK_ENTITY_TYPE.get(), LargeForgeRenderer::new);
        event.registerBlockEntityRenderer(BlockRegistry.SMALL_FORGE_BLOCK_ENTITY_TYPE.get(), SmallForgeRenderer::new);
    }

    @OnlyIn(Dist.CLIENT)
    public static void onClientSetup(FMLClientSetupEvent event) {


// Initialize the ClientOnlyItemRegistry
        ClientOnlyItemRegistry registry = new ClientOnlyItemRegistry();


        // Register the mana overlay (render it during the HUD)
        ManaOverlayScreen.register();

        event.enqueueWork(() -> {
            // Register the entity renderers
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
