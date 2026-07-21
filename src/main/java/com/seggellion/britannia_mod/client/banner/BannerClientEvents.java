package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.BannerItemRegistry;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Current NeoForge baked-model registration and reload lifecycle for the shared banner item. */
@EventBusSubscriber(modid = BritanniaMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BannerClientEvents {
    private BannerClientEvents() {
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        BannerAssetAvailability.EXPECTED_MODELS.forEach(id ->
                event.register(ModelResourceLocation.standalone(id)));
    }

    @SubscribeEvent
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ModelResourceLocation itemId = ModelResourceLocation.inventory(BannerItemRegistry.BANNER.getId());
        BakedModel original = event.getModels().get(itemId);
        if (original == null) {
            return;
        }
        BakedModel vanillaMissing = event.getModels().get(ModelBakery.MISSING_MODEL_VARIANT);
        Map<ResourceLocation, BakedModel> baked = new LinkedHashMap<>();
        Set<ResourceLocation> availableModels = new LinkedHashSet<>();
        for (ResourceLocation id : BannerAssetAvailability.EXPECTED_MODELS) {
            BakedModel model = event.getModels().get(ModelResourceLocation.standalone(id));
            if (model != null && model != vanillaMissing) {
                baked.put(id, model);
                availableModels.add(id);
            }
        }
        Set<ResourceLocation> availableTextures = new LinkedHashSet<>();
        for (ResourceLocation id : BannerAssetAvailability.EXPECTED_TEXTURES) {
            ResourceLocation stitched = event.getTextureGetter()
                    .apply(new Material(TextureAtlas.LOCATION_BLOCKS, id)).contents().name();
            if (!stitched.equals(MissingTextureAtlasSprite.getLocation())) {
                availableTextures.add(id);
            }
        }
        BakedModel fallback = baked.getOrDefault(BannerAssetAvailability.MISSING_MODEL, original);
        BannerModelRepository.install(original, baked, fallback,
                new BannerAssetAvailability(availableModels, availableTextures));
        event.getModels().put(itemId, new BannerItemBakedModel(original));
    }

    @SubscribeEvent
    public static void registerItemColours(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != BannerRenderLayer.FABRIC_TINT_INDEX) {
                return -1;
            }
            BannerItemRenderState state = BannerRenderCache.extract(stack);
            return state.fallback() ? -1 : 0xFF000000 | state.displaySrgb();
        }, BannerItemRegistry.BANNER.get());
    }
}
