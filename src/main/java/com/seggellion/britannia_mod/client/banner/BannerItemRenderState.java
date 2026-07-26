package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Item-context wrapper around the shared immutable appearance. */
public record BannerItemRenderState(BannerAppearanceState appearance) {
    public BannerItemRenderState {
        Objects.requireNonNull(appearance, "appearance");
    }

    public boolean fallback() {
        return appearance.fallback();
    }

    public BannerRenderKey key(long resourceGeneration) {
        return new BannerRenderKey(appearance.withResourceGeneration(resourceGeneration).key());
    }

    public Optional<BannerDefinitionId> definitionId() { return appearance.definitionId(); }
    public Optional<FabricMaterialId> materialId() { return appearance.materialId(); }
    public Optional<ResolvedColourId> resolvedColourId() { return appearance.resolvedColourId(); }
    public Optional<MountId> mountId() { return appearance.mountId(); }
    public Optional<ResourceLocation> geometry() { return appearance.geometry(); }
    public Optional<ResourceLocation> fabricBase() { return appearance.fabricBase(); }
    public Optional<ResourceLocation> dyeMask() { return appearance.dyeMask(); }
    public Optional<ResourceLocation> staticOverlay() { return appearance.staticOverlay(); }
    public Optional<ResourceLocation> mountGeometry() { return appearance.mountGeometry(); }
    public Optional<ResourceLocation> mountTexture() { return appearance.mountTexture(); }
    public Optional<BannerContentStatus> contentStatus() { return appearance.contentStatus(); }
    public Optional<BannerDimensions> definitionDimensions() { return appearance.definitionDimensions(); }
    public int displaySrgb() { return appearance.displaySrgb(); }
    public boolean naturalColour() { return appearance.naturalColour(); }
    public BannerRenderFailure failure() { return appearance.failure(); }
    public String diagnosticId() { return appearance.diagnosticId(); }
    public long dataGeneration() { return appearance.dataGeneration(); }
    public long resourceGeneration() { return appearance.resourceGeneration(); }
}
