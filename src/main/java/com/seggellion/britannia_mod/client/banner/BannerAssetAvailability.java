package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDefinition;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderMount;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Immutable resource-generation view of successfully baked models and stitched textures. */
public record BannerAssetAvailability(
        Set<ResourceLocation> models,
        Set<ResourceLocation> textures) {
    public static final ResourceLocation FABRIC_BASE = id("banner/placeholder/fabric_base");
    public static final ResourceLocation DYE_MASK = id("banner/placeholder/dye_mask");
    public static final ResourceLocation STATIC_OVERLAY = id("banner/placeholder/static_overlay");
    public static final ResourceLocation MISSING_TEXTURE = id("banner/placeholder/missing");
    public static final ResourceLocation MISSING_MODEL = id("banner/placeholder/missing_item");
    public static final ResourceLocation BRASS_MOUNT_MODEL = id("banner/mount/brass");
    public static final ResourceLocation IRON_MOUNT_MODEL = id("banner/mount/iron");
    public static final ResourceLocation BRASS_MOUNT_TEXTURE = id("banner/mount/brass");
    public static final ResourceLocation IRON_MOUNT_TEXTURE = id("banner/mount/iron");
    public static final Set<ResourceLocation> GEOMETRY_MODELS = Set.of(
            id("banner/placeholder/large"), id("banner/placeholder/medium_wall"),
            id("banner/placeholder/medium"), id("banner/placeholder/small"),
            id("banner/placeholder/x_small"));
    public static final Set<ResourceLocation> EXPECTED_MODELS = Set.of(
            id("banner/placeholder/large"), id("banner/placeholder/medium_wall"),
            id("banner/placeholder/medium"), id("banner/placeholder/small"),
            id("banner/placeholder/x_small"), MISSING_MODEL, BRASS_MOUNT_MODEL, IRON_MOUNT_MODEL);
    public static final Set<ResourceLocation> EXPECTED_TEXTURES = Set.of(
            FABRIC_BASE, DYE_MASK, STATIC_OVERLAY, MISSING_TEXTURE,
            BRASS_MOUNT_TEXTURE, IRON_MOUNT_TEXTURE);

    public BannerAssetAvailability {
        models = Set.copyOf(Objects.requireNonNull(models, "models"));
        textures = Set.copyOf(Objects.requireNonNull(textures, "textures"));
    }

    public static BannerAssetAvailability allExpected() {
        return new BannerAssetAvailability(EXPECTED_MODELS, EXPECTED_TEXTURES);
    }

    public BannerRenderFailure failureFor(BannerRenderDefinition definition, BannerRenderMount mount) {
        if (!models.contains(definition.assets().geometry())) return BannerRenderFailure.MISSING_GEOMETRY;
        if (!textures.contains(definition.assets().fabricBase())) return BannerRenderFailure.MISSING_FABRIC_BASE;
        if (!textures.contains(definition.assets().dyeMask())) return BannerRenderFailure.MISSING_DYE_MASK;
        if (!textures.contains(definition.assets().staticOverlay())) return BannerRenderFailure.MISSING_STATIC_OVERLAY;
        if (!models.contains(mount.geometry())) return BannerRenderFailure.MISSING_MOUNT_GEOMETRY;
        if (!textures.contains(mount.texture())) return BannerRenderFailure.MISSING_MOUNT_TEXTURE;
        return BannerRenderFailure.NONE;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, path);
    }
}
