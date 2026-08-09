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

/**
 * Display-only appearance shared by item and placed forms.
 *
 * <p>It deliberately retains no stack, entity, level, player, position, source-pigment identity, custom name,
 * registry map, buffer, or timestamp. Pigment presence is reduced to the visual-only recolour flag.</p>
 */
public record BannerAppearanceState(
        Optional<BannerDefinitionId> definitionId,
        Optional<FabricMaterialId> materialId,
        Optional<ResolvedColourId> resolvedColourId,
        Optional<MountId> mountId,
        Optional<ResourceLocation> geometry,
        Optional<ResourceLocation> baseTexture,
        Optional<ResourceLocation> dyeMask,
        Optional<ResourceLocation> mountGeometry,
        Optional<ResourceLocation> mountTexture,
        Optional<BannerContentStatus> contentStatus,
        Optional<BannerDimensions> definitionDimensions,
        int displaySrgb,
        boolean recolourActive,
        BannerRenderFailure failure,
        String diagnosticId,
        long dataGeneration,
        long resourceGeneration) {
    public BannerAppearanceState {
        definitionId = Objects.requireNonNull(definitionId, "definitionId");
        materialId = Objects.requireNonNull(materialId, "materialId");
        resolvedColourId = Objects.requireNonNull(resolvedColourId, "resolvedColourId");
        mountId = Objects.requireNonNull(mountId, "mountId");
        geometry = Objects.requireNonNull(geometry, "geometry");
        baseTexture = Objects.requireNonNull(baseTexture, "baseTexture");
        dyeMask = Objects.requireNonNull(dyeMask, "dyeMask");
        mountGeometry = Objects.requireNonNull(mountGeometry, "mountGeometry");
        mountTexture = Objects.requireNonNull(mountTexture, "mountTexture");
        contentStatus = Objects.requireNonNull(contentStatus, "contentStatus");
        definitionDimensions = Objects.requireNonNull(definitionDimensions, "definitionDimensions");
        displaySrgb &= 0xFFFFFF;
        Objects.requireNonNull(failure, "failure");
        diagnosticId = Objects.requireNonNull(diagnosticId, "diagnosticId");
        if (dataGeneration < 0 || resourceGeneration < 0) {
            throw new IllegalArgumentException("Render generations must be non-negative");
        }
    }

    public boolean fallback() {
        return failure != BannerRenderFailure.NONE;
    }

    public int displayArgb() {
        return 0xFF000000 | displaySrgb;
    }

    public BannerAppearanceState withResourceGeneration(long generation) {
        return new BannerAppearanceState(definitionId, materialId, resolvedColourId, mountId,
                geometry, baseTexture, dyeMask, mountGeometry, mountTexture,
                contentStatus, definitionDimensions, displaySrgb, recolourActive, failure,
                diagnosticId, dataGeneration, generation);
    }

    public BannerAppearanceKey key() {
        return new BannerAppearanceKey(definitionId, materialId, resolvedColourId, mountId,
                geometry, baseTexture, dyeMask, mountGeometry, mountTexture,
                contentStatus, definitionDimensions, displaySrgb, recolourActive, failure,
                diagnosticId, dataGeneration, resourceGeneration);
    }
}
