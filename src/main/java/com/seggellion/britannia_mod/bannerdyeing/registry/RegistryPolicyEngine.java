package com.seggellion.britannia_mod.bannerdyeing.registry;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.api.PlacementProfileId;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.MountDefinition;
import com.seggellion.britannia_mod.banner.data.PlacementProfile;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationIssue;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationPolicy;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationReport;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationSeverity;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationStage;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationSummary;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.colour.ColourMath;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

final class RegistryPolicyEngine {
    private RegistryPolicyEngine() {
    }

    static RegistryLoadResult apply(
            PreparedRegistryData prepared,
            ValidationPolicy policy,
            RegistrySnapshotPublisher publisher) {
        List<ValidationIssue> issues = new ArrayList<>(prepared.structuralIssues());
        WorkingSet working = WorkingSet.from(prepared, issues);

        if (policy == ValidationPolicy.PRODUCTION_DISABLE_INVALID) {
            boolean removed;
            do {
                List<ValidationIssue> pass = validate(working);
                issues.addAll(pass);
                removed = working.disableOwners(pass);
            } while (removed);
        } else {
            issues.addAll(validate(working));
        }

        RegistrySnapshot candidate = working.snapshot();
        ValidationReport report = report(prepared, candidate, issues);
        boolean publish = policy == ValidationPolicy.PRODUCTION_DISABLE_INVALID || !report.hasErrors();
        if (publish) {
            publisher.publish(candidate);
        }
        return new RegistryLoadResult(publish, publisher.current(), report);
    }

    private static List<ValidationIssue> validate(WorkingSet data) {
        List<ValidationIssue> issues = new ArrayList<>();
        validatePigments(data, issues);
        validatePalettes(data, issues);
        validateMaterials(data, issues);
        validateBanners(data, issues);
        return issues.stream().distinct().sorted(ValidationIssue.ORDER).toList();
    }

    private static void validatePigments(WorkingSet data, List<ValidationIssue> issues) {
        for (DefinitionEntry<PigmentId, PigmentDefinition> entry : data.pigments.values()) {
            PigmentDefinition pigment = entry.definition();
            double difference = ColourMath.authoredDifference(pigment.referenceSrgb(), pigment.referenceOklab());
            if (difference > ColourMath.AUTHORED_OKLAB_TOLERANCE) {
                issues.add(error(RegistryDomain.PIGMENT, entry, "PIGMENT_OKLAB_MISMATCH",
                        "Authored reference_oklab differs from computed reference_srgb by " + difference,
                        pigment.id()));
            }
        }
    }

    private static void validatePalettes(WorkingSet data, List<ValidationIssue> issues) {
        for (DefinitionEntry<ResourceLocation, MaterialPalette> entry : data.palettes.values()) {
            MaterialPalette palette = entry.definition();
            FabricMaterialDefinition owner = definition(data.materials.get(palette.materialId()));
            if (owner == null) {
                issues.add(error(RegistryDomain.MATERIAL_PALETTE, entry, "MISSING_PALETTE_OWNER",
                        "Palette owner material does not exist", palette.materialId()));
            } else if (!owner.paletteId().equals(palette.id())) {
                issues.add(error(RegistryDomain.MATERIAL_PALETTE, entry, "PALETTE_OWNER_MISMATCH",
                        "Palette owner material references a different palette", palette.materialId()));
            }
            for (PigmentId pigmentId : palette.pigmentOverrides().keySet()) {
                if (!data.pigments.containsKey(pigmentId)) {
                    issues.add(error(RegistryDomain.MATERIAL_PALETTE, entry, "MISSING_OVERRIDE_PIGMENT",
                            "Pigment override references a missing pigment", pigmentId));
                }
            }
            for (var colour : palette.entries()) {
                double difference = ColourMath.authoredDifference(colour.displaySrgb(), colour.matchOklab());
                if (difference > ColourMath.AUTHORED_OKLAB_TOLERANCE) {
                    issues.add(error(RegistryDomain.MATERIAL_PALETTE, entry, "PALETTE_OKLAB_MISMATCH",
                            "Palette entry " + colour.id()
                                    + " authored match_oklab differs from computed display_srgb by " + difference,
                            colour.id()));
                }
            }
        }
    }

    private static void validateMaterials(WorkingSet data, List<ValidationIssue> issues) {
        for (DefinitionEntry<FabricMaterialId, FabricMaterialDefinition> entry : data.materials.values()) {
            FabricMaterialDefinition material = entry.definition();
            MaterialPalette palette = definition(data.palettes.get(material.paletteId()));
            if (palette == null) {
                issues.add(error(RegistryDomain.FABRIC_MATERIAL, entry, "MISSING_MATERIAL_PALETTE",
                        "Material references a missing palette", material.paletteId()));
                continue;
            }
            if (!palette.materialId().equals(material.id())) {
                issues.add(error(RegistryDomain.FABRIC_MATERIAL, entry, "PALETTE_MATERIAL_MISMATCH",
                        "Referenced palette is owned by a different material", palette.materialId()));
            }
            if (!palette.naturalColourId().equals(material.naturalColourId())) {
                issues.add(error(RegistryDomain.FABRIC_MATERIAL, entry, "NATURAL_COLOUR_MISMATCH",
                        "Material and palette natural colours differ", palette.naturalColourId()));
            }
            boolean naturalPresent = palette.entries().stream()
                    .anyMatch(colour -> colour.id().equals(material.naturalColourId()));
            if (!naturalPresent) {
                issues.add(error(RegistryDomain.FABRIC_MATERIAL, entry, "MISSING_NATURAL_COLOUR",
                        "Material natural colour is not present in its palette", material.naturalColourId()));
            }
        }
    }

    private static void validateBanners(WorkingSet data, List<ValidationIssue> issues) {
        for (DefinitionEntry<BannerDefinitionId, BannerDefinition> entry : data.banners.values()) {
            BannerDefinition banner = entry.definition();
            if (!data.materials.containsKey(banner.defaultMaterial())) {
                issues.add(error(RegistryDomain.BANNER_DEFINITION, entry, "MISSING_DEFAULT_MATERIAL",
                        "Banner default material does not exist", banner.defaultMaterial()));
            }
            if (!data.mounts.containsKey(banner.defaultMount())) {
                issues.add(error(RegistryDomain.BANNER_DEFINITION, entry, "MISSING_DEFAULT_MOUNT",
                        "Banner default mount does not exist", banner.defaultMount()));
            }
            for (MountId mountId : banner.supportedMounts()) {
                if (!mountId.equals(banner.defaultMount()) && !data.mounts.containsKey(mountId)) {
                    issues.add(error(RegistryDomain.BANNER_DEFINITION, entry, "MISSING_SUPPORTED_MOUNT",
                            "Banner supported mount does not exist", mountId));
                }
            }
            PlacementProfile profile = definition(data.profiles.get(banner.placementProfile()));
            if (profile == null) {
                issues.add(error(RegistryDomain.BANNER_DEFINITION, entry, "MISSING_PLACEMENT_PROFILE",
                        "Banner placement profile does not exist", banner.placementProfile()));
            } else if (profile.dimensions().widthBlocks() < banner.dimensions().widthBlocks()
                    || profile.dimensions().heightBlocks() < banner.dimensions().heightBlocks()) {
                issues.add(error(RegistryDomain.BANNER_DEFINITION, entry, "PLACEMENT_DIMENSION_MISMATCH",
                        "Placement profile dimensions cannot contain the banner dimensions", profile.id()));
            } else if (!profile.orientationMountGeometry().isEmpty()
                    && !profile.orientationMountGeometry().keySet()
                    .equals(Set.copyOf(banner.supportedOrientations()))) {
                issues.add(error(RegistryDomain.BANNER_DEFINITION, entry, "ORIENTATION_MOUNT_MISMATCH",
                        "Placement profile mount geometry must match supported banner orientations", profile.id()));
            }
        }
    }

    private static <I, T> ValidationIssue error(
            RegistryDomain domain,
            DefinitionEntry<I, T> entry,
            String code,
            String message,
            Object relatedId) {
        return ValidationIssue.error(ValidationStage.CROSS_REFERENCE, domain, entry.id().toString(),
                entry.sourceResource(), code, message, relatedId.toString());
    }

    private static <I, T> T definition(DefinitionEntry<I, T> entry) {
        return entry == null ? null : entry.definition();
    }

    private static ValidationReport report(
            PreparedRegistryData prepared, RegistrySnapshot snapshot, List<ValidationIssue> issues) {
        List<ValidationIssue> ordered = issues.stream().distinct().sorted(ValidationIssue.ORDER).toList();
        EnumMap<RegistryDomain, Integer> active = new EnumMap<>(RegistryDomain.class);
        EnumMap<RegistryDomain, Integer> disabled = new EnumMap<>(RegistryDomain.class);
        count(snapshot, active, disabled);
        int errors = (int) ordered.stream().filter(issue -> issue.severity() == ValidationSeverity.ERROR).count();
        int warnings = (int) ordered.stream().filter(issue -> issue.severity() == ValidationSeverity.WARNING).count();
        int information = (int) ordered.stream()
                .filter(issue -> issue.severity() == ValidationSeverity.INFORMATION).count();
        ValidationSummary summary = new ValidationSummary(prepared.resourcesDiscovered(), prepared.resourcesDecoded(),
                active, disabled, errors, warnings, information);
        return new ValidationReport(ordered, summary);
    }

    private static void count(
            RegistrySnapshot snapshot,
            Map<RegistryDomain, Integer> active,
            Map<RegistryDomain, Integer> disabled) {
        put(active, disabled, RegistryDomain.BANNER_DEFINITION, snapshot.banners());
        put(active, disabled, RegistryDomain.FABRIC_MATERIAL, snapshot.fabricMaterials());
        put(active, disabled, RegistryDomain.PIGMENT, snapshot.pigments());
        put(active, disabled, RegistryDomain.MATERIAL_PALETTE, snapshot.materialPalettes());
        put(active, disabled, RegistryDomain.MOUNT, snapshot.mounts());
        put(active, disabled, RegistryDomain.PLACEMENT_PROFILE, snapshot.placementProfiles());
    }

    private static void put(
            Map<RegistryDomain, Integer> active,
            Map<RegistryDomain, Integer> disabled,
            RegistryDomain domain,
            DefinitionRegistry<?, ?> registry) {
        active.put(domain, registry.activeCount());
        disabled.put(domain, registry.disabledCount());
    }

    private static final class WorkingSet {
        private final Map<BannerDefinitionId, DefinitionEntry<BannerDefinitionId, BannerDefinition>> banners;
        private final Map<FabricMaterialId, DefinitionEntry<FabricMaterialId, FabricMaterialDefinition>> materials;
        private final Map<PigmentId, DefinitionEntry<PigmentId, PigmentDefinition>> pigments;
        private final Map<ResourceLocation, DefinitionEntry<ResourceLocation, MaterialPalette>> palettes;
        private final Map<MountId, DefinitionEntry<MountId, MountDefinition>> mounts;
        private final Map<PlacementProfileId, DefinitionEntry<PlacementProfileId, PlacementProfile>> profiles;
        private final List<DefinitionEntry<BannerDefinitionId, BannerDefinition>> disabledBanners = new ArrayList<>();
        private final List<DefinitionEntry<FabricMaterialId, FabricMaterialDefinition>> disabledMaterials = new ArrayList<>();
        private final List<DefinitionEntry<PigmentId, PigmentDefinition>> disabledPigments = new ArrayList<>();
        private final List<DefinitionEntry<ResourceLocation, MaterialPalette>> disabledPalettes = new ArrayList<>();
        private final List<DefinitionEntry<MountId, MountDefinition>> disabledMounts = new ArrayList<>();
        private final List<DefinitionEntry<PlacementProfileId, PlacementProfile>> disabledProfiles = new ArrayList<>();

        private WorkingSet(
                Map<BannerDefinitionId, DefinitionEntry<BannerDefinitionId, BannerDefinition>> banners,
                Map<FabricMaterialId, DefinitionEntry<FabricMaterialId, FabricMaterialDefinition>> materials,
                Map<PigmentId, DefinitionEntry<PigmentId, PigmentDefinition>> pigments,
                Map<ResourceLocation, DefinitionEntry<ResourceLocation, MaterialPalette>> palettes,
                Map<MountId, DefinitionEntry<MountId, MountDefinition>> mounts,
                Map<PlacementProfileId, DefinitionEntry<PlacementProfileId, PlacementProfile>> profiles) {
            this.banners = banners;
            this.materials = materials;
            this.pigments = pigments;
            this.palettes = palettes;
            this.mounts = mounts;
            this.profiles = profiles;
        }

        static WorkingSet from(PreparedRegistryData prepared, List<ValidationIssue> issues) {
            WorkingSet data = new WorkingSet(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                    new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
            unique(RegistryDomain.BANNER_DEFINITION, prepared.banners(), data.banners, data.disabledBanners, issues);
            unique(RegistryDomain.FABRIC_MATERIAL, prepared.fabricMaterials(), data.materials,
                    data.disabledMaterials, issues);
            unique(RegistryDomain.PIGMENT, prepared.pigments(), data.pigments, data.disabledPigments, issues);
            unique(RegistryDomain.MATERIAL_PALETTE, prepared.materialPalettes(), data.palettes,
                    data.disabledPalettes, issues);
            unique(RegistryDomain.MOUNT, prepared.mounts(), data.mounts, data.disabledMounts, issues);
            unique(RegistryDomain.PLACEMENT_PROFILE, prepared.placementProfiles(), data.profiles,
                    data.disabledProfiles, issues);
            return data;
        }

        private static <I, T> void unique(
                RegistryDomain domain,
                List<DefinitionEntry<I, T>> entries,
                Map<I, DefinitionEntry<I, T>> active,
                List<DefinitionEntry<I, T>> disabled,
                List<ValidationIssue> issues) {
            Map<I, List<DefinitionEntry<I, T>>> grouped = new LinkedHashMap<>();
            entries.stream().sorted(java.util.Comparator
                    .comparing((DefinitionEntry<I, T> entry) -> entry.id().toString())
                    .thenComparing(entry -> entry.sourceResource().toString()))
                    .forEach(entry -> grouped.computeIfAbsent(entry.id(), ignored -> new ArrayList<>()).add(entry));
            for (Map.Entry<I, List<DefinitionEntry<I, T>>> group : grouped.entrySet()) {
                if (group.getValue().size() == 1) {
                    active.put(group.getKey(), group.getValue().getFirst());
                } else {
                    disabled.addAll(group.getValue());
                    for (DefinitionEntry<I, T> duplicate : group.getValue()) {
                        issues.add(ValidationIssue.error(ValidationStage.CROSS_REFERENCE, domain,
                                duplicate.id().toString(), duplicate.sourceResource(), "DUPLICATE_STABLE_ID",
                                "Multiple effective resources declare the same stable ID", duplicate.id().toString()));
                    }
                }
            }
        }

        boolean disableOwners(List<ValidationIssue> issues) {
            boolean removed = false;
            for (ValidationIssue issue : issues) {
                if (issue.severity() != ValidationSeverity.ERROR || issue.definitionId().isEmpty()) {
                    continue;
                }
                String id = issue.definitionId().orElseThrow();
                removed |= switch (issue.domain()) {
                    case BANNER_DEFINITION -> remove(banners, disabledBanners, id);
                    case FABRIC_MATERIAL -> remove(materials, disabledMaterials, id);
                    case PIGMENT -> remove(pigments, disabledPigments, id);
                    case MATERIAL_PALETTE -> remove(palettes, disabledPalettes, id);
                    case MOUNT -> remove(mounts, disabledMounts, id);
                    case PLACEMENT_PROFILE -> remove(profiles, disabledProfiles, id);
                };
            }
            return removed;
        }

        private static <I, T> boolean remove(
                Map<I, DefinitionEntry<I, T>> active,
                List<DefinitionEntry<I, T>> disabled,
                String id) {
            I key = active.keySet().stream().filter(candidate -> candidate.toString().equals(id)).findFirst().orElse(null);
            if (key == null) {
                return false;
            }
            disabled.add(active.remove(key));
            return true;
        }

        RegistrySnapshot snapshot() {
            return new RegistrySnapshot(
                    new DefinitionRegistry<>(banners, disabledBanners),
                    new DefinitionRegistry<>(materials, disabledMaterials),
                    new DefinitionRegistry<>(pigments, disabledPigments),
                    new DefinitionRegistry<>(palettes, disabledPalettes),
                    new DefinitionRegistry<>(mounts, disabledMounts),
                    new DefinitionRegistry<>(profiles, disabledProfiles));
        }
    }
}
