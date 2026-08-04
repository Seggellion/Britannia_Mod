package com.seggellion.britannia_mod.structure.definition;

import com.seggellion.britannia_mod.structure.definition.DefinitionDiagnostic.Code;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Family;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Variant;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.Dimensions;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.GeometryMode;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.VoxelOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ClientResource;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ResourceAvailability;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ResourceId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates only the shrine/monolith definition contracts needed by this feature. */
public final class StructureDefinitionValidator {
    private static final Pattern RESOURCE_NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern RESOURCE_PATH = Pattern.compile("[a-z0-9/._-]+");
    private static final Dimensions SHRINE_DIMENSIONS = new Dimensions(2, 1, 2);
    private static final Dimensions MONOLITH_DIMENSIONS = new Dimensions(3, 3, 2);
    private static final VoxelOffset MONOLITH_RENDER_OFFSET = new VoxelOffset(0, 16, 0);

    private StructureDefinitionValidator() {
    }

    public static DefinitionValidation validate(Family family) {
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
        String familyLocation = "family:" + family.id().value();

        validateDimensions(family.dimensions(), familyLocation, diagnostics);
        if (!family.anchorOffset().equals(LocalOffset.ANCHOR)) {
            add(diagnostics, Code.INVALID_ANCHOR_OFFSET, familyLocation, "Anchor offset must be [0, 0, 0]");
        }
        validateFootprint(family.dimensions(), family.footprint(), family.anchorOffset(), familyLocation, diagnostics);
        if (family.contentStatus() == null) {
            add(diagnostics, Code.MISSING_CONTENT_STATUS, familyLocation, "Family requires approved/provisional status");
        }
        family.sharedGeometry().ifPresent(resource -> validateResource(resource, familyLocation + ".shared_geometry", diagnostics));

        Map<VariantId, Variant> variantsById = new HashMap<>();
        Set<Integer> cyclePositions = new HashSet<>();
        for (Variant variant : family.variants()) {
            String variantLocation = familyLocation + ".variant:" + variant.id().value();
            if (variantsById.putIfAbsent(variant.id(), variant) != null) {
                add(diagnostics, Code.DUPLICATE_VARIANT_ID, variantLocation, "Duplicate variant ID");
            }
            if (!cyclePositions.add(variant.cyclePosition())) {
                add(diagnostics, Code.DUPLICATE_CYCLE_POSITION, variantLocation, "Duplicate cycle position");
            }
            if (variant.cyclePosition() < 0) {
                add(diagnostics, Code.INVALID_CYCLE_POSITION, variantLocation, "Cycle position must be non-negative");
            }
            validateVariant(family, variant, variantLocation, diagnostics);
        }

        if (family.defaultVariant().isEmpty()) {
            add(diagnostics, Code.MISSING_DEFAULT_VARIANT, familyLocation, "Family has no default variant");
        } else {
            Variant defaultVariant = variantsById.get(family.defaultVariant().orElseThrow());
            if (defaultVariant == null) {
                add(diagnostics, Code.MISSING_DEFAULT_VARIANT, familyLocation, "Default variant is not present");
            } else if (!defaultVariant.enabled()) {
                add(diagnostics, Code.DISABLED_DEFAULT_VARIANT, familyLocation, "Default variant is disabled");
            }
        }

        validateInitialFamilyContract(family, familyLocation, diagnostics);
        return new DefinitionValidation(diagnostics);
    }

    public static boolean compatible(Family family, Variant variant) {
        return variant.familyId().equals(family.id())
                && variant.dimensions().equals(family.dimensions())
                && variant.footprint().equals(family.footprint())
                && variant.placementMode() == family.placementMode()
                && variant.collisionProfile() == family.collisionProfile()
                && variant.renderOrigin() == family.renderOrigin()
                && variant.renderOffsetVoxels().equals(family.renderOffsetVoxels())
                && (!(family.id().value().equals("shrine"))
                        || family.sharedGeometry().filter(resource -> resource.equals(variant.model())).isPresent());
    }

    /** Returns whether a selected runtime variant owns a concrete, syntactically valid resource. */
    public static boolean usableClientResource(ClientResource resource) {
        if (resource.availability() != ResourceAvailability.AVAILABLE || resource.location().isEmpty()) {
            return false;
        }
        ResourceId location = resource.location().orElseThrow();
        return RESOURCE_NAMESPACE.matcher(location.namespace()).matches()
                && RESOURCE_PATH.matcher(location.path()).matches();
    }

    private static void validateVariant(
            Family family, Variant variant, String location, List<DefinitionDiagnostic> diagnostics) {
        validateDimensions(variant.dimensions(), location, diagnostics);
        validateFootprint(variant.dimensions(), variant.footprint(), family.anchorOffset(), location, diagnostics);
        validateResource(variant.model(), location + ".model", diagnostics);
        validateResource(variant.texture(), location + ".texture", diagnostics);
        if (variant.contentStatus() == null) {
            add(diagnostics, Code.MISSING_CONTENT_STATUS, location, "Variant requires approved/provisional status");
        }
        boolean compatible = true;
        if (!variant.familyId().equals(family.id())) {
            compatible = false;
            add(diagnostics, Code.CROSS_FAMILY_VARIANT, location, "Variant belongs to another family");
        }
        if (!variant.dimensions().equals(family.dimensions())) {
            compatible = false;
            add(diagnostics, Code.VARIANT_DIMENSIONS_MISMATCH, location, "Variant dimensions differ from its family");
        }
        if (!variant.footprint().equals(family.footprint())) {
            compatible = false;
            add(diagnostics, Code.VARIANT_FOOTPRINT_MISMATCH, location, "Variant footprint differs from its family");
        }
        if (variant.placementMode() != family.placementMode()) {
            compatible = false;
            add(diagnostics, Code.VARIANT_PLACEMENT_MODE_MISMATCH, location, "Variant placement mode differs from its family");
        }
        if (variant.collisionProfile() != family.collisionProfile()) {
            compatible = false;
            add(diagnostics, Code.VARIANT_COLLISION_PROFILE_MISMATCH, location, "Variant collision profile differs from its family");
        }
        if (variant.renderOrigin() != family.renderOrigin()) {
            compatible = false;
            add(diagnostics, Code.VARIANT_RENDER_ORIGIN_MISMATCH, location, "Variant render origin differs from its family");
        }
        if (!variant.renderOffsetVoxels().equals(family.renderOffsetVoxels())) {
            compatible = false;
            add(diagnostics, Code.VARIANT_RENDER_OFFSET_MISMATCH, location, "Variant render offset differs from its family");
        }
        if (family.id().value().equals("shrine")
                && family.sharedGeometry().filter(resource -> resource.equals(variant.model())).isEmpty()) {
            compatible = false;
            add(diagnostics, Code.SHRINE_GEOMETRY_MISMATCH, location, "Shrine variant changed shared geometry");
        }
        if (variant.enabled() && !compatible) {
            add(diagnostics, Code.CYCLE_CANDIDATE_INCOMPATIBLE, location, "Enabled cycle candidate is incompatible");
        }
    }

    private static void validateInitialFamilyContract(
            Family family, String location, List<DefinitionDiagnostic> diagnostics) {
        if (family.id().value().equals("shrine")) {
            if (!family.dimensions().equals(SHRINE_DIMENSIONS)
                    || !family.footprint().equals(StructureGeometry.rectangularFootprint(SHRINE_DIMENSIONS))
                    || !family.anchorOffset().equals(LocalOffset.ANCHOR)
                    || !family.renderOffsetVoxels().equals(new VoxelOffset(0, 0, 0))) {
                add(diagnostics, Code.SHRINE_FAMILY_CONTRACT_MISMATCH, location, "Shrine contract must be 2 x 1 x 2 with no render correction");
            }
            if (family.geometryMode() != GeometryMode.SHARED || family.sharedGeometry().isEmpty()) {
                add(diagnostics, Code.SHARED_GEOMETRY_REQUIRED, location, "Shrines require one shared geometry identity");
            }
        }
        if (family.id().value().equals("monolith")) {
            if (!family.dimensions().equals(MONOLITH_DIMENSIONS)
                    || !family.footprint().equals(StructureGeometry.rectangularFootprint(MONOLITH_DIMENSIONS))
                    || !family.anchorOffset().equals(LocalOffset.ANCHOR)
                    || !family.renderOffsetVoxels().equals(MONOLITH_RENDER_OFFSET)
                    || family.geometryMode() != GeometryMode.PER_VARIANT) {
                add(diagnostics, Code.MONOLITH_FAMILY_CONTRACT_MISMATCH, location, "Monolith contract must be 3 x 3 x 2 with [0, 16, 0] render offset");
            }
        }
    }

    private static void validateDimensions(
            Dimensions dimensions, String location, List<DefinitionDiagnostic> diagnostics) {
        if (dimensions.width() <= 0 || dimensions.height() <= 0 || dimensions.depth() <= 0) {
            add(diagnostics, Code.INVALID_DIMENSIONS, location, "Dimensions must all be positive");
        }
        if (dimensions.width() > StructureGeometry.MAX_LOCAL_X + 1
                || dimensions.height() > StructureGeometry.MAX_LOCAL_Y + 1
                || dimensions.depth() > StructureGeometry.MAX_LOCAL_Z + 1) {
            add(diagnostics, Code.DIMENSIONS_EXCEED_PART_ENCODING, location, "Dimensions exceed X 0..2, Y 0..2, Z 0..1 encoding");
        }
    }

    private static void validateFootprint(
            Dimensions dimensions,
            List<LocalOffset> footprint,
            LocalOffset anchor,
            String location,
            List<DefinitionDiagnostic> diagnostics) {
        if (footprint.isEmpty()) {
            add(diagnostics, Code.EMPTY_FOOTPRINT, location, "Footprint must not be empty");
        }
        Set<LocalOffset> unique = new HashSet<>();
        int anchorCount = 0;
        for (LocalOffset offset : footprint) {
            if (!unique.add(offset)) {
                add(diagnostics, Code.DUPLICATE_OCCUPIED_OFFSET, location, "Duplicate occupied offset " + offset);
            }
            if (offset.equals(anchor)) {
                anchorCount++;
            }
            if (offset.x() < 0 || offset.y() < 0 || offset.z() < 0
                    || offset.x() >= dimensions.width()
                    || offset.y() >= dimensions.height()
                    || offset.z() >= dimensions.depth()) {
                add(diagnostics, Code.OCCUPIED_OFFSET_OUTSIDE_DIMENSIONS, location, "Occupied offset is outside declared dimensions: " + offset);
            }
            if (offset.x() > StructureGeometry.MAX_LOCAL_X
                    || offset.y() > StructureGeometry.MAX_LOCAL_Y
                    || offset.z() > StructureGeometry.MAX_LOCAL_Z) {
                add(diagnostics, Code.OCCUPIED_OFFSET_EXCEEDS_PART_ENCODING, location,
                        "Occupied offset exceeds X 0..2, Y 0..2, Z 0..1 encoding: " + offset);
            }
        }
        if (anchorCount == 0) {
            add(diagnostics, Code.MISSING_ANCHOR_OFFSET, location, "Footprint does not contain its anchor");
        } else if (anchorCount > 1) {
            add(diagnostics, Code.DUPLICATE_ANCHOR_OFFSET, location, "Footprint contains its anchor more than once");
        }
    }

    private static void validateResource(
            ClientResource resource, String location, List<DefinitionDiagnostic> diagnostics) {
        if (resource.availability() == ResourceAvailability.AVAILABLE && resource.location().isEmpty()) {
            add(diagnostics, Code.RESOURCE_AVAILABILITY_MISMATCH, location, "Available resource requires a location");
        }
        resource.location().ifPresent(resourceId -> validateResourceId(resourceId, location, diagnostics));
    }

    private static void validateResourceId(
            ResourceId resource, String location, List<DefinitionDiagnostic> diagnostics) {
        if (!RESOURCE_NAMESPACE.matcher(resource.namespace()).matches()) {
            add(diagnostics, Code.INVALID_RESOURCE_NAMESPACE, location, "Invalid resource namespace: " + resource.namespace());
        }
        if (!RESOURCE_PATH.matcher(resource.path()).matches()) {
            add(diagnostics, Code.INVALID_RESOURCE_PATH, location, "Invalid resource path: " + resource.path());
        }
    }

    private static void add(
            List<DefinitionDiagnostic> diagnostics, Code code, String location, String message) {
        diagnostics.add(new DefinitionDiagnostic(code, location, message));
    }
}
