package com.seggellion.britannia_mod.client.renderer.shrine;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ResourceId;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlockEntity;
import com.seggellion.britannia_mod.structure.render.ShrineRenderSelection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import software.bernie.geckolib.model.GeoModel;

/** Resolves only catalogue-owned paths and falls back without changing synchronized shrine state. */
public final class ShrineGeoModel extends GeoModel<LargeStructureAnchorBlockEntity> {
    static final ResourceLocation DIAGNOSTIC_GEOMETRY = id("geo/shrine_missing.geo.json");
    static final ResourceLocation STATIC_ANIMATION = id("animations/shrine.animation.json");
    static final ResourceLocation MONOLITH_STATIC_ANIMATION = id("animations/monolith.animation.json");
    private static final int MAX_DIAGNOSTICS = 128;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Boolean> DIAGNOSTICS = new LinkedHashMap<>();

    @Override
    public ResourceLocation getModelResource(LargeStructureAnchorBlockEntity anchor) {
        ShrineRenderSelection selection = ShrineRenderSelection.resolve(anchor);
        if (selection.status() != ShrineRenderSelection.Status.READY) {
            diagnostic("state:model:" + selection.familyId().value() + ":" + selection.variantId().value(),
                    "Using diagnostic large-structure model for {}:{} ({})",
                    selection.familyId().value(), selection.variantId().value(), selection.status());
            return DIAGNOSTIC_GEOMETRY;
        }
        ResourceLocation requested = location(selection.geometry().orElseThrow().location().orElseThrow());
        if (!exists(requested)) {
            diagnostic("missing:model:" + requested,
                    "Large-structure geometry resource {} is missing; using bounded diagnostic geometry", requested);
            return DIAGNOSTIC_GEOMETRY;
        }
        return requested;
    }

    @Override
    public ResourceLocation getTextureResource(LargeStructureAnchorBlockEntity anchor) {
        ShrineRenderSelection selection = ShrineRenderSelection.resolve(anchor);
        if (selection.status() != ShrineRenderSelection.Status.READY) {
            diagnostic("state:texture:" + selection.familyId().value() + ":" + selection.variantId().value(),
                    "Using missing-texture diagnostic for structure {}:{} ({})",
                    selection.familyId().value(), selection.variantId().value(), selection.status());
            return MissingTextureAtlasSprite.getLocation();
        }
        ResourceLocation requested = location(selection.texture().orElseThrow().location().orElseThrow());
        if (!exists(requested)) {
            diagnostic("missing:texture:" + requested,
                    "Large-structure texture resource {} is missing; using missing-texture diagnostic", requested);
            return MissingTextureAtlasSprite.getLocation();
        }
        return requested;
    }

    @Override
    public ResourceLocation getAnimationResource(LargeStructureAnchorBlockEntity anchor) {
        return anchor.placedState()
                .filter(state -> state.familyId().equals(ShrineMonolithDefinitions.MONOLITH))
                .map(ignored -> MONOLITH_STATIC_ANIMATION)
                .orElse(STATIC_ANIMATION);
    }

    static synchronized int diagnosticKeyCount() {
        return DIAGNOSTICS.size();
    }

    private static boolean exists(ResourceLocation location) {
        return Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
    }

    private static ResourceLocation location(ResourceId resource) {
        return ResourceLocation.fromNamespaceAndPath(resource.namespace(), resource.path());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", path);
    }

    private static synchronized void diagnostic(String key, String message, Object... arguments) {
        if (DIAGNOSTICS.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        while (DIAGNOSTICS.size() > MAX_DIAGNOSTICS) {
            DIAGNOSTICS.remove(DIAGNOSTICS.keySet().iterator().next());
        }
        LOGGER.warn(message, arguments);
    }
}
