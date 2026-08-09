package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Position-free immutable cache identity for an assembled placed render. */
public record BannerPlacedRenderKey(
        BannerAppearanceKey appearanceKey,
        BannerOrientation orientation,
        Direction facing,
        int persistedWidth,
        int persistedHeight,
        Optional<ResourceLocation> geometry,
        Optional<MountId> mountId,
        long dataGeneration,
        long resourceGeneration,
        BannerPlacedRenderFailure failure,
        String diagnosticId) {
}
