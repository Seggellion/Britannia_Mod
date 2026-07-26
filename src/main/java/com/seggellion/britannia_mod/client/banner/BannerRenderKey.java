package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Item-context key wrapping the appearance identity shared with placed rendering. */
public record BannerRenderKey(BannerAppearanceKey appearanceKey) {
    public BannerRenderKey {
        java.util.Objects.requireNonNull(appearanceKey, "appearanceKey");
    }

    /** Compatibility constructor retained for the Milestone 9 cache tests. */
    public BannerRenderKey(
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
        this(new BannerAppearanceKey(definitionId, materialId, resolvedColourId, mountId,
                geometry, fabricBase, dyeMask, staticOverlay, mountGeometry, mountTexture,
                contentStatus, Optional.empty(), displaySrgb, naturalColour, failure, "",
                dataGeneration, resourceGeneration));
    }
}
