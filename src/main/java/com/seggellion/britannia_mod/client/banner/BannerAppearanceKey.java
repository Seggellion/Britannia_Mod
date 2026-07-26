package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Shared immutable visual identity for item and placed banner contexts. */
public record BannerAppearanceKey(
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
        Optional<BannerDimensions> definitionDimensions,
        int displaySrgb,
        boolean naturalColour,
        BannerRenderFailure failure,
        String diagnosticId,
        long dataGeneration,
        long resourceGeneration) {
}
