package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSnapshot;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDefinition;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderMaterial;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderMount;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** The single non-mutating display projection used by item and placed extraction. */
public final class BannerAppearanceResolver {
    private BannerAppearanceResolver() {
    }

    public static BannerAppearanceState resolve(
            BannerInstanceState state,
            ClientBannerRenderPublication publication,
            BannerAssetAvailability assets,
            long resourceGeneration) {
        if (state == null) {
            return fallback(null, publication.generation(), resourceGeneration,
                    BannerRenderFailure.MISSING_COMPONENT, "banner_instance_state");
        }
        if (!publication.available()) {
            return fallback(state, publication.generation(), resourceGeneration,
                    BannerRenderFailure.REGISTRY_UNAVAILABLE, "client_render_data");
        }
        BannerRenderDataSnapshot snapshot = publication.snapshot();
        BannerRenderDefinition definition = snapshot.banners().get(state.bannerDefinitionId());
        if (definition == null) {
            return fallback(state, publication.generation(), resourceGeneration,
                    BannerRenderFailure.MISSING_DEFINITION, state.bannerDefinitionId().toString());
        }
        BannerRenderMaterial material = snapshot.materials().get(state.materialId());
        if (material == null) {
            return fallback(state, publication.generation(), resourceGeneration,
                    BannerRenderFailure.MISSING_MATERIAL, state.materialId().toString());
        }
        Integer displaySrgb = material.displaySrgbByColour().get(state.resolvedColourId());
        if (displaySrgb == null) {
            return fallback(state, publication.generation(), resourceGeneration,
                    BannerRenderFailure.MISSING_COLOUR, state.resolvedColourId().toString());
        }
        BannerRenderMount mount = snapshot.mounts().get(state.mountId());
        if (mount == null) {
            return fallback(state, publication.generation(), resourceGeneration,
                    BannerRenderFailure.MISSING_MOUNT, state.mountId().toString());
        }
        BannerRenderFailure assetFailure = assets.failureFor(definition, mount);
        String diagnosticId = assetFailure == BannerRenderFailure.NONE
                ? "" : diagnosticAssetId(assetFailure, definition, mount);
        return complete(state, definition, material, mount, displaySrgb,
                assetFailure, diagnosticId, publication.generation(), resourceGeneration);
    }

    public static BannerAppearanceState fallback(
            BannerInstanceState state,
            long dataGeneration,
            long resourceGeneration,
            BannerRenderFailure failure,
            String diagnosticId) {
        return new BannerAppearanceState(
                optional(state, BannerInstanceState::bannerDefinitionId),
                optional(state, BannerInstanceState::materialId),
                optional(state, BannerInstanceState::resolvedColourId),
                optional(state, BannerInstanceState::mountId),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.<BannerContentStatus>empty(), Optional.empty(),
                0xFF00FF, false, failure, diagnosticId, dataGeneration, resourceGeneration);
    }

    private static BannerAppearanceState complete(
            BannerInstanceState state,
            BannerRenderDefinition definition,
            BannerRenderMaterial material,
            BannerRenderMount mount,
            int displaySrgb,
            BannerRenderFailure failure,
            String diagnosticId,
            long dataGeneration,
            long resourceGeneration) {
        return new BannerAppearanceState(
                Optional.of(state.bannerDefinitionId()), Optional.of(state.materialId()),
                Optional.of(state.resolvedColourId()), Optional.of(state.mountId()),
                Optional.of(definition.assets().geometry()), Optional.of(definition.assets().fabricBase()),
                Optional.of(definition.assets().dyeMask()), Optional.of(definition.assets().staticOverlay()),
                Optional.of(mount.geometry()), Optional.of(mount.texture()),
                Optional.of(definition.contentStatus()), Optional.of(definition.dimensions()), displaySrgb,
                state.resolvedColourId().equals(material.naturalColourId()), failure, diagnosticId,
                dataGeneration, resourceGeneration);
    }

    private static <T> Optional<T> optional(
            BannerInstanceState state, java.util.function.Function<BannerInstanceState, T> getter) {
        return state == null ? Optional.empty() : Optional.of(getter.apply(state));
    }

    private static String diagnosticAssetId(
            BannerRenderFailure failure, BannerRenderDefinition definition, BannerRenderMount mount) {
        ResourceLocation id = switch (failure) {
            case MISSING_GEOMETRY -> definition.assets().geometry();
            case MISSING_FABRIC_BASE -> definition.assets().fabricBase();
            case MISSING_DYE_MASK -> definition.assets().dyeMask();
            case MISSING_STATIC_OVERLAY -> definition.assets().staticOverlay();
            case MISSING_MOUNT_GEOMETRY -> mount.geometry();
            case MISSING_MOUNT_TEXTURE -> mount.texture();
            default -> BannerAssetAvailability.MISSING_TEXTURE;
        };
        return id.toString();
    }
}
