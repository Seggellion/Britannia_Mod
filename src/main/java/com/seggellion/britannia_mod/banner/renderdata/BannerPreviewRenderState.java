package com.seggellion.britannia_mod.banner.renderdata;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.Objects;
import java.util.Optional;

/** Server-supplied, display-only item appearance descriptor for the dye preview. */
public record BannerPreviewRenderState(
        BannerDefinitionId bannerDefinitionId,
        FabricMaterialId materialId,
        ResolvedColourId resolvedColourId,
        Optional<PigmentId> sourcePigmentId,
        MountId mountId) {
    public BannerPreviewRenderState {
        Objects.requireNonNull(bannerDefinitionId, "bannerDefinitionId");
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(resolvedColourId, "resolvedColourId");
        sourcePigmentId = Objects.requireNonNull(sourcePigmentId, "sourcePigmentId");
        Objects.requireNonNull(mountId, "mountId");
    }

    public static BannerPreviewRenderState from(BannerInstanceState state) {
        Objects.requireNonNull(state, "state");
        return new BannerPreviewRenderState(state.bannerDefinitionId(), state.materialId(),
                state.resolvedColourId(), state.sourcePigmentId(), state.mountId());
    }
}
