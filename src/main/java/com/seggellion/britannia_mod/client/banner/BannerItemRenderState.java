package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Immutable result of non-mutating, display-only ItemStack extraction. */
public record BannerItemRenderState(
        Optional<BannerDefinitionId> definitionId,
        Optional<FabricMaterialId> materialId,
        Optional<ResolvedColourId> resolvedColourId,
        Optional<MountId> mountId,
        Optional<ResourceLocation> geometry,
        Optional<ResourceLocation> fabricBase,
        Optional<ResourceLocation> dyeMask,
        Optional<ResourceLocation> staticOverlay,
        Optional<ResourceLocation> mountGeometry,
        Optional<ResourceLocation> mountTexture,
        Optional<BannerContentStatus> contentStatus,
        int displaySrgb,
        boolean naturalColour,
        BannerRenderFailure failure,
        String diagnosticId,
        long dataGeneration) {
    public BannerItemRenderState {
        definitionId = Objects.requireNonNull(definitionId, "definitionId");
        materialId = Objects.requireNonNull(materialId, "materialId");
        resolvedColourId = Objects.requireNonNull(resolvedColourId, "resolvedColourId");
        mountId = Objects.requireNonNull(mountId, "mountId");
        geometry = Objects.requireNonNull(geometry, "geometry");
        fabricBase = Objects.requireNonNull(fabricBase, "fabricBase");
        dyeMask = Objects.requireNonNull(dyeMask, "dyeMask");
        staticOverlay = Objects.requireNonNull(staticOverlay, "staticOverlay");
        mountGeometry = Objects.requireNonNull(mountGeometry, "mountGeometry");
        mountTexture = Objects.requireNonNull(mountTexture, "mountTexture");
        contentStatus = Objects.requireNonNull(contentStatus, "contentStatus");
        displaySrgb &= 0xFFFFFF;
        Objects.requireNonNull(failure, "failure");
        diagnosticId = Objects.requireNonNull(diagnosticId, "diagnosticId");
    }

    public boolean fallback() {
        return failure != BannerRenderFailure.NONE;
    }

    public BannerRenderKey key(long resourceGeneration) {
        return new BannerRenderKey(definitionId, materialId, resolvedColourId, mountId,
                geometry, fabricBase, dyeMask, staticOverlay, mountGeometry, mountTexture,
                contentStatus, displaySrgb, naturalColour, failure, dataGeneration, resourceGeneration);
    }
}
