package com.seggellion.britannia_mod.bannerdyeing.testsupport;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.api.PlacementProfileId;
import com.seggellion.britannia_mod.banner.data.BannerAssets;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.banner.data.BannerSourceReference;
import com.seggellion.britannia_mod.banner.data.MountDefinition;
import com.seggellion.britannia_mod.banner.data.PlacementProfile;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.BannerDyeingConstants;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.dye.palette.MaterialPaletteEntry;
import com.seggellion.britannia_mod.dye.service.DyeResult;
import com.seggellion.britannia_mod.dye.service.MatchType;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public final class CoreDataFixtures {
    public static final int SCHEMA = BannerDyeingConstants.CURRENT_SCHEMA_VERSION;
    public static final BannerDefinitionId BANNER_ID = BannerDefinitionId.parse("britannia_mod:test_banner");
    public static final FabricMaterialId MATERIAL_ID = FabricMaterialId.parse("britannia_mod:test_silk");
    public static final PigmentId PIGMENT_ID = PigmentId.parse("britannia_mod:test_crimson");
    public static final ResolvedColourId NATURAL_COLOUR_ID =
            ResolvedColourId.parse("britannia_mod:test_silk_natural");
    public static final ResolvedColourId DYED_COLOUR_ID = ResolvedColourId.parse("britannia_mod:test_silk_ruby");
    public static final MountId BRASS_MOUNT_ID = MountId.parse("britannia_mod:test_brass");
    public static final MountId IRON_MOUNT_ID = MountId.parse("britannia_mod:test_iron");
    public static final PlacementProfileId PLACEMENT_ID =
            PlacementProfileId.parse("britannia_mod:test_medium_profile");
    public static final ResourceLocation PALETTE_ID = ResourceLocation.parse("britannia_mod:test_silk_palette");

    private CoreDataFixtures() {
    }

    public static BannerDimensions dimensions() {
        return new BannerDimensions(2, 3, true);
    }

    public static BannerAssets assets() {
        return new BannerAssets(
                ResourceLocation.parse("britannia_mod:banner/test/geometry"),
                ResourceLocation.parse("britannia_mod:banner/test/fabric_base"),
                ResourceLocation.parse("britannia_mod:banner/test/dye_mask"),
                ResourceLocation.parse("britannia_mod:banner/test/static_overlay"));
    }

    public static BannerSourceReference sourceReference() {
        return new BannerSourceReference(2, 9, Optional.of("Source Sheet Label"));
    }

    public static BannerSourceReference minimalSourceReference() {
        return new BannerSourceReference(1, 1, Optional.empty());
    }

    public static BannerDefinition bannerDefinition() {
        return new BannerDefinition(
                SCHEMA,
                BANNER_ID,
                "banner.britannia_mod.test_banner",
                BannerContentStatus.IN_PROGRESS,
                sourceReference(),
                "medium-wall",
                dimensions(),
                List.of(BannerOrientation.WALL_PARALLEL, BannerOrientation.WALL_PERPENDICULAR),
                List.of(BRASS_MOUNT_ID, IRON_MOUNT_ID),
                BRASS_MOUNT_ID,
                MATERIAL_ID,
                assets(),
                PLACEMENT_ID);
    }

    public static BannerDefinition minimalBannerDefinition() {
        return new BannerDefinition(
                SCHEMA,
                BANNER_ID,
                "banner.britannia_mod.test_banner",
                BannerContentStatus.PLACEHOLDER,
                minimalSourceReference(),
                "medium",
                dimensions(),
                List.of(BannerOrientation.WALL_PARALLEL),
                List.of(BRASS_MOUNT_ID),
                BRASS_MOUNT_ID,
                MATERIAL_ID,
                assets(),
                PLACEMENT_ID);
    }

    public static FabricMaterialDefinition fabricMaterial() {
        return new FabricMaterialDefinition(
                SCHEMA,
                MATERIAL_ID,
                "material.britannia_mod.test_silk",
                NATURAL_COLOUR_ID,
                PALETTE_ID,
                List.of("fabric", "fine"));
    }

    public static FabricMaterialDefinition minimalFabricMaterial() {
        return new FabricMaterialDefinition(
                SCHEMA,
                MATERIAL_ID,
                "material.britannia_mod.test_silk",
                NATURAL_COLOUR_ID,
                PALETTE_ID,
                List.of());
    }

    public static PigmentDefinition pigment() {
        return new PigmentDefinition(
                SCHEMA,
                PIGMENT_ID,
                "pigment.britannia_mod.test_crimson",
                "#A51C30",
                List.of(0.48, 0.17, 0.07),
                List.of("red", "common"),
                "common");
    }

    public static PigmentDefinition minimalPigment() {
        return new PigmentDefinition(
                SCHEMA,
                PIGMENT_ID,
                "pigment.britannia_mod.test_crimson",
                "#A51C30",
                List.of(0.48, 0.17, 0.07),
                List.of(),
                "common");
    }

    public static MaterialPaletteEntry naturalEntry() {
        return new MaterialPaletteEntry(
                NATURAL_COLOUR_ID,
                "colour.britannia_mod.test_silk_natural",
                "#E8DDC4",
                List.of(0.88, 0.01, 0.04),
                0,
                List.of("natural"),
                List.of(),
                List.of());
    }

    public static MaterialPaletteEntry minimalEntry() {
        return new MaterialPaletteEntry(
                NATURAL_COLOUR_ID,
                "colour.britannia_mod.test_silk_natural",
                "#E8DDC4",
                List.of(0.88, 0.01, 0.04),
                0,
                List.of(),
                List.of(),
                List.of());
    }

    public static MaterialPaletteEntry dyedEntry() {
        return new MaterialPaletteEntry(
                DYED_COLOUR_ID,
                "colour.britannia_mod.test_silk_ruby",
                "#A81742",
                List.of(0.50, 0.18, 0.05),
                10,
                List.of("red", "rich"),
                List.of("red"),
                List.of("mundane"));
    }

    public static MaterialPalette palette() {
        Map<PigmentId, ResolvedColourId> overrides = new LinkedHashMap<>();
        overrides.put(PIGMENT_ID, DYED_COLOUR_ID);
        return new MaterialPalette(
                SCHEMA,
                PALETTE_ID,
                MATERIAL_ID,
                NATURAL_COLOUR_ID,
                List.of(naturalEntry(), dyedEntry()),
                overrides);
    }

    public static MaterialPalette minimalPalette() {
        return new MaterialPalette(
                SCHEMA,
                PALETTE_ID,
                MATERIAL_ID,
                NATURAL_COLOUR_ID,
                List.of(minimalEntry()),
                Map.of());
    }

    public static MountDefinition mount() {
        return new MountDefinition(
                SCHEMA,
                BRASS_MOUNT_ID,
                "mount.britannia_mod.test_brass",
                ResourceLocation.parse("britannia_mod:banner_mount/test_brass"),
                ResourceLocation.parse("britannia_mod:banner_mount/test_brass_texture"),
                List.of("metal", "decorative"));
    }

    public static MountDefinition minimalMount() {
        return new MountDefinition(
                SCHEMA,
                BRASS_MOUNT_ID,
                "mount.britannia_mod.test_brass",
                ResourceLocation.parse("britannia_mod:banner_mount/test_brass"),
                ResourceLocation.parse("britannia_mod:banner_mount/test_brass_texture"),
                List.of());
    }

    public static PlacementProfile placement() {
        return new PlacementProfile(SCHEMA, PLACEMENT_ID, dimensions(), true);
    }

    public static BannerInstanceState naturalBannerState() {
        return new BannerInstanceState(
                SCHEMA, BANNER_ID, MATERIAL_ID, NATURAL_COLOUR_ID, Optional.empty(), BRASS_MOUNT_ID);
    }

    public static BannerInstanceState dyedBannerState() {
        return new BannerInstanceState(
                SCHEMA, BANNER_ID, MATERIAL_ID, DYED_COLOUR_ID, Optional.of(PIGMENT_ID), BRASS_MOUNT_ID);
    }

    public static DyeTubState emptyTub() {
        return new DyeTubState(SCHEMA, Optional.empty(), Optional.empty());
    }

    public static DyeTubState unlimitedTub() {
        return new DyeTubState(SCHEMA, Optional.of(PIGMENT_ID), Optional.empty());
    }

    public static DyeTubState finiteTub() {
        return new DyeTubState(SCHEMA, Optional.of(PIGMENT_ID), Optional.of(12));
    }

    public static DyeResult dyeResult() {
        return new DyeResult(DYED_COLOUR_ID, MatchType.EXPLICIT_MAPPING, 0.0);
    }
}
