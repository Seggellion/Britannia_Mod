package com.seggellion.britannia_mod.bannerdyeing.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.api.PlacementProfileId;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.MountDefinition;
import com.seggellion.britannia_mod.banner.data.PlacementProfile;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationIssue;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationPolicy;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationStage;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Structural decoder and policy coordinator for complete six-registry datasets. */
public final class RegistryDataLoader {
    public PreparedRegistryData prepare(ResourceManager resourceManager) {
        return prepare(DefinitionResourceScanner.scan(resourceManager));
    }

    public PreparedRegistryData prepare(Collection<DefinitionResource> inputResources) {
        List<DefinitionResource> resources = inputResources.stream()
                .sorted(Comparator.comparing((DefinitionResource resource) -> resource.domain().name())
                        .thenComparing(resource -> resource.sourceResource().toString()))
                .toList();
        List<DefinitionEntry<BannerDefinitionId, BannerDefinition>> banners = new ArrayList<>();
        List<DefinitionEntry<FabricMaterialId, FabricMaterialDefinition>> materials = new ArrayList<>();
        List<DefinitionEntry<PigmentId, PigmentDefinition>> pigments = new ArrayList<>();
        List<DefinitionEntry<ResourceLocation, MaterialPalette>> palettes = new ArrayList<>();
        List<DefinitionEntry<MountId, MountDefinition>> mounts = new ArrayList<>();
        List<DefinitionEntry<PlacementProfileId, PlacementProfile>> profiles = new ArrayList<>();
        List<ValidationIssue> issues = new ArrayList<>();
        int decoded = 0;

        for (DefinitionResource resource : resources) {
            if (resource.readError().isPresent()) {
                issues.add(structuralError(resource, "RESOURCE_READ_FAILED", resource.readError().orElseThrow()));
                continue;
            }
            JsonElement json;
            try {
                json = JsonParser.parseString(resource.contents());
            } catch (JsonParseException | IllegalStateException exception) {
                issues.add(structuralError(resource, "MALFORMED_JSON", exception.getMessage()));
                continue;
            }
            boolean success = switch (resource.domain()) {
                case BANNER_DEFINITION -> decode(resource, json, BannerDefinition.CODEC,
                        BannerDefinition::id, banners, issues);
                case FABRIC_MATERIAL -> decode(resource, json, FabricMaterialDefinition.CODEC,
                        FabricMaterialDefinition::id, materials, issues);
                case PIGMENT -> decode(resource, json, PigmentDefinition.CODEC,
                        PigmentDefinition::id, pigments, issues);
                case MATERIAL_PALETTE -> decode(resource, json, MaterialPalette.CODEC,
                        MaterialPalette::id, palettes, issues);
                case MOUNT -> decode(resource, json, MountDefinition.CODEC,
                        MountDefinition::id, mounts, issues);
                case PLACEMENT_PROFILE -> decode(resource, json, PlacementProfile.CODEC,
                        PlacementProfile::id, profiles, issues);
            };
            if (success) {
                decoded++;
            }
        }
        return new PreparedRegistryData(resources.size(), decoded, banners, materials, pigments, palettes,
                mounts, profiles, issues);
    }

    public RegistryLoadResult apply(
            PreparedRegistryData prepared,
            ValidationPolicy policy,
            RegistrySnapshotPublisher publisher) {
        return RegistryPolicyEngine.apply(prepared, policy, publisher);
    }

    private static <I, T> boolean decode(
            DefinitionResource resource,
            JsonElement json,
            Codec<T> codec,
            Function<T, I> idGetter,
            List<DefinitionEntry<I, T>> output,
            List<ValidationIssue> issues) {
        try {
            DataResult<T> result = codec.parse(JsonOps.INSTANCE, json);
            if (result.error().isPresent()) {
                issues.add(structuralError(resource, "CODEC_DECODE_FAILED", result.error().orElseThrow().message()));
                return false;
            }
            T definition = result.result().orElseThrow();
            output.add(new DefinitionEntry<>(idGetter.apply(definition), definition, resource.sourceResource()));
            return true;
        } catch (RuntimeException exception) {
            issues.add(structuralError(resource, "CODEC_DECODE_FAILED", exception.getMessage()));
            return false;
        }
    }

    private static ValidationIssue structuralError(
            DefinitionResource resource, String code, String message) {
        String safeMessage = message == null || message.isBlank() ? "Unknown decoding failure" : message;
        return ValidationIssue.error(ValidationStage.STRUCTURAL_DECODING, resource.domain(), null,
                resource.sourceResource(), code, safeMessage, null);
    }
}
