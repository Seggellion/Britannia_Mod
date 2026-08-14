package com.seggellion.britannia_mod.client.banner;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/** Atomically replaces baked model references on every model/resource generation. */
public final class BannerModelRepository {
    private static final AtomicReference<Models> CURRENT = new AtomicReference<>(Models.empty());

    private BannerModelRepository() {
    }

    static void install(
            BakedModel transformModel,
            Map<ResourceLocation, BakedModel> models,
            Map<ResourceLocation, TextureAtlasSprite> textures,
            BakedModel fallback,
            BannerAssetAvailability availability) {
        Models previous = CURRENT.get();
        CURRENT.set(new Models(transformModel, Map.copyOf(models), Map.copyOf(textures), fallback,
                Objects.requireNonNull(availability, "availability"), previous.generation + 1));
        BannerRenderCache.onModelsReloaded();
    }

    public static long generation() {
        return CURRENT.get().generation;
    }

    public static BannerAssetAvailability availability() {
        return CURRENT.get().availability;
    }

    /**
     * Baked assembly model, or empty when it did not bake. Used by the placed-banner renderer,
     * which draws real geometry for the pole and bracket rather than the flat quads the cloth
     * uses; a missing model simply omits that part instead of failing the whole banner.
     */
    public static Optional<BakedModel> model(ResourceLocation id) {
        return Optional.ofNullable(CURRENT.get().models.get(id));
    }

    public static Optional<TextureAtlasSprite> texture(ResourceLocation id) {
        return Optional.ofNullable(CURRENT.get().textures.get(id));
    }

    static BakedModel compose(BannerItemRenderState state) {
        Models models = CURRENT.get();
        if (state.fallback()) {
            return models.fallbackModel();
        }
        BakedModel geometry = models.models.get(state.geometry().orElseThrow());
        BakedModel mount = models.models.get(state.mountGeometry().orElseThrow());
        TextureAtlasSprite baseSprite = models.textures.get(state.baseTexture().orElseThrow());
        TextureAtlasSprite maskSprite = models.textures.get(state.dyeMask().orElseThrow());
        if (geometry == null || mount == null || baseSprite == null || maskSprite == null) {
            return models.fallbackModel();
        }
        BakedModel base = new BannerFilteredBakedModel(
                geometry, BannerFilteredBakedModel.Selection.BASE_TEXTURE, baseSprite);
        if (!state.recolourActive()) {
            return new BannerLayeredBakedModel(models.transformModel, java.util.List.of(base, mount));
        }
        BakedModel mask = new BannerFilteredBakedModel(
                geometry, BannerFilteredBakedModel.Selection.DYE_MASK, maskSprite);
        return new BannerLayeredBakedModel(models.transformModel, java.util.List.of(base, mask, mount));
    }

    private record Models(
            BakedModel transformModel,
            Map<ResourceLocation, BakedModel> models,
            Map<ResourceLocation, TextureAtlasSprite> textures,
            BakedModel fallback,
            BannerAssetAvailability availability,
            long generation) {
        static Models empty() {
            return new Models(null, Map.of(), Map.of(), null,
                    new BannerAssetAvailability(java.util.Set.of(), java.util.Set.of()), 0);
        }

        BakedModel fallbackModel() {
            BakedModel selected = fallback != null ? fallback : transformModel;
            if (selected == null) {
                throw new IllegalStateException("Banner models have not completed their first bake");
            }
            return new BannerLayeredBakedModel(transformModel, java.util.List.of(selected));
        }
    }
}
