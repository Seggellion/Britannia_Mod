package com.seggellion.britannia_mod.banner.renderdata;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.Objects;

/** Server-supplied, display-only item appearance descriptor for the dye preview. */
public record BannerPreviewRenderState(
        BannerDefinitionId bannerDefinitionId,
        FabricMaterialId materialId,
        ResolvedColourId resolvedColourId,
        MountId mountId) {
    public BannerPreviewRenderState {
        Objects.requireNonNull(bannerDefinitionId, "bannerDefinitionId");
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(resolvedColourId, "resolvedColourId");
        Objects.requireNonNull(mountId, "mountId");
    }

    public static BannerPreviewRenderState from(BannerInstanceState state) {
        Objects.requireNonNull(state, "state");
        return new BannerPreviewRenderState(state.bannerDefinitionId(), state.materialId(),
                state.resolvedColourId(), state.mountId());
    }
}
