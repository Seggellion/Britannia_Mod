package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Deterministic appearance identity. No mutable gameplay or UI object is retained. */
public record BannerRenderKey(
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
        long dataGeneration,
        long resourceGeneration) {
}
