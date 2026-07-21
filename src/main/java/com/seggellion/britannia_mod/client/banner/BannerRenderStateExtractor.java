package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSnapshot;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDefinition;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderMaterial;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderMount;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Small typed extraction layer. Reads display data only and never validates, repairs, or mutates gameplay state. */
public final class BannerRenderStateExtractor {
    private BannerRenderStateExtractor() {
    }

    public static BannerItemRenderState extract(
            ItemStack stack,
            Item bannerItem,
            DataComponentType<BannerInstanceState> componentType,
            ClientBannerRenderPublication publication,
            BannerAssetAvailability assets) {
        if (stack.getItem() != bannerItem) {
            return fallback(null, publication.generation(), BannerRenderFailure.INVALID_ITEM, itemId(stack));
        }
        BannerInstanceState state = stack.get(componentType);
        if (state == null) {
            return fallback(null, publication.generation(), BannerRenderFailure.MISSING_COMPONENT,
                    "banner_instance_state");
        }
        if (!publication.available()) {
            return fallback(state, publication.generation(), BannerRenderFailure.REGISTRY_UNAVAILABLE,
                    "client_render_data");
        }
        BannerRenderDataSnapshot snapshot = publication.snapshot();
        BannerRenderDefinition definition = snapshot.banners().get(state.bannerDefinitionId());
        if (definition == null) {
            return fallback(state, publication.generation(), BannerRenderFailure.MISSING_DEFINITION,
                    state.bannerDefinitionId().toString());
        }
        BannerRenderMaterial material = snapshot.materials().get(state.materialId());
        if (material == null) {
            return fallback(state, publication.generation(), BannerRenderFailure.MISSING_MATERIAL,
                    state.materialId().toString());
        }
        Integer displaySrgb = material.displaySrgbByColour().get(state.resolvedColourId());
        if (displaySrgb == null) {
            return fallback(state, publication.generation(), BannerRenderFailure.MISSING_COLOUR,
                    state.resolvedColourId().toString());
        }
        BannerRenderMount mount = snapshot.mounts().get(state.mountId());
        if (mount == null) {
            return fallback(state, publication.generation(), BannerRenderFailure.MISSING_MOUNT,
                    state.mountId().toString());
        }
        BannerRenderFailure assetFailure = assets.failureFor(definition, mount);
        if (assetFailure != BannerRenderFailure.NONE) {
            return fallbackWithAssets(state, definition, mount, material, publication.generation(),
                    displaySrgb, assetFailure, diagnosticAssetId(assetFailure, definition, mount));
        }
        return new BannerItemRenderState(
                Optional.of(state.bannerDefinitionId()), Optional.of(state.materialId()),
                Optional.of(state.resolvedColourId()), Optional.of(state.mountId()),
                Optional.of(definition.assets().geometry()), Optional.of(definition.assets().fabricBase()),
                Optional.of(definition.assets().dyeMask()), Optional.of(definition.assets().staticOverlay()),
                Optional.of(mount.geometry()), Optional.of(mount.texture()),
                Optional.of(definition.contentStatus()), displaySrgb,
                state.resolvedColourId().equals(material.naturalColourId()), BannerRenderFailure.NONE,
                "", publication.generation());
    }

    private static BannerItemRenderState fallbackWithAssets(
            BannerInstanceState state,
            BannerRenderDefinition definition,
            BannerRenderMount mount,
            BannerRenderMaterial material,
            long generation,
            int displaySrgb,
            BannerRenderFailure failure,
            String diagnosticId) {
        return new BannerItemRenderState(
                Optional.of(state.bannerDefinitionId()), Optional.of(state.materialId()),
                Optional.of(state.resolvedColourId()), Optional.of(state.mountId()),
                Optional.of(definition.assets().geometry()), Optional.of(definition.assets().fabricBase()),
                Optional.of(definition.assets().dyeMask()), Optional.of(definition.assets().staticOverlay()),
                Optional.of(mount.geometry()), Optional.of(mount.texture()),
                Optional.of(definition.contentStatus()), displaySrgb,
                state.resolvedColourId().equals(material.naturalColourId()), failure, diagnosticId, generation);
    }

    private static BannerItemRenderState fallback(
            BannerInstanceState state, long generation, BannerRenderFailure failure, String diagnosticId) {
        return new BannerItemRenderState(
                optional(state, BannerInstanceState::bannerDefinitionId),
                optional(state, BannerInstanceState::materialId),
                optional(state, BannerInstanceState::resolvedColourId),
                optional(state, BannerInstanceState::mountId),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.<BannerContentStatus>empty(),
                0xFF00FF, false, failure, diagnosticId, generation);
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

    private static String itemId(ItemStack stack) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? stack.getItem().getClass().getName() : id.toString();
    }
}
