package com.seggellion.britannia_mod.banner.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.api.PlacementProfileId;
import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import java.util.List;
import java.util.Objects;

public record BannerDefinition(
        int schemaVersion,
        BannerDefinitionId id,
        String displayNameKey,
        BannerContentStatus contentStatus,
        BannerSourceReference sourceReference,
        String catalogueGroup,
        BannerDimensions dimensions,
        List<BannerOrientation> supportedOrientations,
        List<MountId> supportedMounts,
        MountId defaultMount,
        FabricMaterialId defaultMaterial,
        BannerAssets assets,
        PlacementProfileId placementProfile) {
    private static final Codec<Decoded> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DataCodecs.CURRENT_SCHEMA_VERSION.fieldOf("schema_version").forGetter(Decoded::schemaVersion),
            BannerDefinitionId.CODEC.fieldOf("id").forGetter(Decoded::id),
            DataCodecs.NON_BLANK_STRING.fieldOf("display_name_key").forGetter(Decoded::displayNameKey),
            BannerContentStatus.CODEC.fieldOf("content_status").forGetter(Decoded::contentStatus),
            BannerSourceReference.CODEC.fieldOf("source_reference").forGetter(Decoded::sourceReference),
            DataCodecs.NON_BLANK_STRING.fieldOf("catalogue_group").forGetter(Decoded::catalogueGroup),
            BannerDimensions.CODEC.fieldOf("dimensions").forGetter(Decoded::dimensions),
            BannerOrientation.CODEC.listOf(1, 2).fieldOf("supported_orientations")
                    .forGetter(Decoded::supportedOrientations),
            MountId.CODEC.listOf(1, 32).fieldOf("supported_mounts").forGetter(Decoded::supportedMounts),
            MountId.CODEC.fieldOf("default_mount").forGetter(Decoded::defaultMount),
            FabricMaterialId.CODEC.fieldOf("default_material").forGetter(Decoded::defaultMaterial),
            BannerAssets.CODEC.fieldOf("assets").forGetter(Decoded::assets),
            PlacementProfileId.CODEC.fieldOf("placement_profile").forGetter(Decoded::placementProfile)
    ).apply(instance, Decoded::new));
    public static final Codec<BannerDefinition> CODEC = RAW_CODEC.flatXmap(BannerDefinition::decode, definition ->
            DataResult.success(Decoded.from(definition)));

    public BannerDefinition {
        DataCodecs.requireCurrentSchema(schemaVersion);
        Objects.requireNonNull(id, "id");
        displayNameKey = DataCodecs.requireNonBlank(displayNameKey, "displayNameKey");
        Objects.requireNonNull(contentStatus, "contentStatus");
        Objects.requireNonNull(sourceReference, "sourceReference");
        catalogueGroup = DataCodecs.requireNonBlank(catalogueGroup, "catalogueGroup");
        Objects.requireNonNull(dimensions, "dimensions");
        supportedOrientations = List.copyOf(Objects.requireNonNull(supportedOrientations, "supportedOrientations"));
        supportedMounts = List.copyOf(Objects.requireNonNull(supportedMounts, "supportedMounts"));
        if (supportedOrientations.isEmpty()) {
            throw new IllegalArgumentException("supportedOrientations must not be empty");
        }
        if (supportedMounts.isEmpty()) {
            throw new IllegalArgumentException("supportedMounts must not be empty");
        }
        Objects.requireNonNull(defaultMount, "defaultMount");
        if (!supportedMounts.contains(defaultMount)) {
            throw new IllegalArgumentException("defaultMount must be contained in supportedMounts");
        }
        Objects.requireNonNull(defaultMaterial, "defaultMaterial");
        Objects.requireNonNull(assets, "assets");
        Objects.requireNonNull(placementProfile, "placementProfile");
    }

    private static DataResult<BannerDefinition> decode(Decoded decoded) {
        if (!decoded.supportedMounts.contains(decoded.defaultMount)) {
            return DataResult.error(() -> "default_mount must be contained in supported_mounts");
        }
        return DataResult.success(new BannerDefinition(
                decoded.schemaVersion, decoded.id, decoded.displayNameKey, decoded.contentStatus,
                decoded.sourceReference, decoded.catalogueGroup, decoded.dimensions, decoded.supportedOrientations,
                decoded.supportedMounts, decoded.defaultMount, decoded.defaultMaterial, decoded.assets,
                decoded.placementProfile));
    }

    private record Decoded(
            int schemaVersion,
            BannerDefinitionId id,
            String displayNameKey,
            BannerContentStatus contentStatus,
            BannerSourceReference sourceReference,
            String catalogueGroup,
            BannerDimensions dimensions,
            List<BannerOrientation> supportedOrientations,
            List<MountId> supportedMounts,
            MountId defaultMount,
            FabricMaterialId defaultMaterial,
            BannerAssets assets,
            PlacementProfileId placementProfile) {
        private static Decoded from(BannerDefinition definition) {
            return new Decoded(definition.schemaVersion, definition.id, definition.displayNameKey,
                    definition.contentStatus, definition.sourceReference, definition.catalogueGroup,
                    definition.dimensions, definition.supportedOrientations, definition.supportedMounts,
                    definition.defaultMount, definition.defaultMaterial, definition.assets,
                    definition.placementProfile);
        }
    }
}
