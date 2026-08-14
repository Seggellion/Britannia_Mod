package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDefinition;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderMount;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Immutable resource-generation view of successfully baked models and stitched textures. */
public record BannerAssetAvailability(
        Set<ResourceLocation> models,
        Set<ResourceLocation> textures) {
    public static final ResourceLocation BASE_TEXTURE = id("banner/placeholder/base_texture");
    public static final ResourceLocation DYE_MASK = id("banner/placeholder/dye_mask");
    public static final ResourceLocation MISSING_TEXTURE = id("banner/placeholder/missing");
    public static final ResourceLocation MISSING_MODEL = id("banner/placeholder/missing_item");
    public static final ResourceLocation BRASS_MOUNT_MODEL = id("banner/mount/brass");
    public static final ResourceLocation IRON_MOUNT_MODEL = id("banner/mount/iron");
    public static final ResourceLocation BRASS_MOUNT_TEXTURE = id("banner/mount/brass");
    public static final ResourceLocation IRON_MOUNT_TEXTURE = id("banner/mount/iron");
    public static final Set<ResourceLocation> GEOMETRY_MODELS = BannerClientAssetIndex.geometryModels();
    public static final Set<ResourceLocation> EXPECTED_MODELS = union(
            union(GEOMETRY_MODELS, Set.of(MISSING_MODEL, BRASS_MOUNT_MODEL, IRON_MOUNT_MODEL)),
            BannerAssemblyAssets.MODELS);
    public static final Set<ResourceLocation> EXPECTED_TEXTURES = union(
            union(BannerClientAssetIndex.textures(),
                    Set.of(MISSING_TEXTURE, BRASS_MOUNT_TEXTURE, IRON_MOUNT_TEXTURE)),
            BannerAssemblyAssets.TEXTURES);

    public BannerAssetAvailability {
        models = Set.copyOf(Objects.requireNonNull(models, "models"));
        textures = Set.copyOf(Objects.requireNonNull(textures, "textures"));
    }

    public static BannerAssetAvailability allExpected() {
        return new BannerAssetAvailability(EXPECTED_MODELS, EXPECTED_TEXTURES);
    }

    public BannerRenderFailure failureFor(BannerRenderDefinition definition, BannerRenderMount mount) {
        if (!models.contains(definition.assets().geometry())) return BannerRenderFailure.MISSING_GEOMETRY;
        if (!models.containsAll(definition.orientationMountGeometry().values())) {
            return BannerRenderFailure.MISSING_MOUNT_GEOMETRY;
        }
        if (!textures.contains(definition.assets().baseTexture())) return BannerRenderFailure.MISSING_BASE_TEXTURE;
        if (!textures.contains(definition.assets().dyeMask())) return BannerRenderFailure.MISSING_DYE_MASK;
        if (!models.contains(mount.geometry())) return BannerRenderFailure.MISSING_MOUNT_GEOMETRY;
        if (!textures.contains(mount.texture())) return BannerRenderFailure.MISSING_MOUNT_TEXTURE;
        return BannerRenderFailure.NONE;
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, path);
    }

    private static <T> Set<T> union(Set<T> first, Set<T> second) {
        LinkedHashSet<T> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }
}
