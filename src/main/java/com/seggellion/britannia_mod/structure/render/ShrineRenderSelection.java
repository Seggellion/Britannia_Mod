package com.seggellion.britannia_mod.structure.render;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ClientResource;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlockEntity;
import java.util.Objects;
import java.util.Optional;

/** Pure family-aware lookup result; renderer resolution never mutates authoritative state. */
public record ShrineRenderSelection(
        FamilyId familyId,
        VariantId variantId,
        Optional<ClientResource> geometry,
        Optional<ClientResource> texture,
        Status status) {
    public enum Status {
        READY,
        UNINITIALIZED,
        UNKNOWN_FAMILY,
        UNKNOWN_VARIANT,
        UNAVAILABLE_GEOMETRY,
        UNAVAILABLE_TEXTURE
    }

    public ShrineRenderSelection {
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(variantId, "variantId");
        geometry = Objects.requireNonNull(geometry, "geometry");
        texture = Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(status, "status");
    }

    public static ShrineRenderSelection resolve(LargeStructureAnchorBlockEntity anchor) {
        return anchor.placedState()
                .map(state -> resolve(state.familyId(), state.variantId()))
                .orElseGet(() -> diagnostic(Status.UNINITIALIZED));
    }

    public static ShrineRenderSelection resolve(FamilyId familyId, VariantId variantId) {
        var catalogue = ShrineMonolithDefinitions.catalogue();
        var family = catalogue.family(familyId);
        if (family.isEmpty()) {
            return new ShrineRenderSelection(
                    familyId, variantId, Optional.empty(), Optional.empty(), Status.UNKNOWN_FAMILY);
        }
        var variant = catalogue.variant(familyId, variantId);
        if (variant.isEmpty()) {
            return new ShrineRenderSelection(
                    familyId, variantId, family.orElseThrow().sharedGeometry(),
                    Optional.empty(), Status.UNKNOWN_VARIANT);
        }
        ClientResource geometry = variant.orElseThrow().model();
        ClientResource texture = variant.orElseThrow().texture();
        if (geometry.location().isEmpty()) {
            return new ShrineRenderSelection(
                    familyId, variantId, Optional.of(geometry), Optional.of(texture),
                    Status.UNAVAILABLE_GEOMETRY);
        }
        if (texture.location().isEmpty()) {
            return new ShrineRenderSelection(
                    familyId, variantId, Optional.of(geometry), Optional.of(texture),
                    Status.UNAVAILABLE_TEXTURE);
        }
        return new ShrineRenderSelection(
                familyId, variantId, Optional.of(geometry), Optional.of(texture), Status.READY);
    }

    private static ShrineRenderSelection diagnostic(Status status) {
        return new ShrineRenderSelection(
                ShrineMonolithDefinitions.SHRINE, new VariantId("uninitialized"),
                Optional.empty(), Optional.empty(), status);
    }
}
