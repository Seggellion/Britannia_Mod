package com.seggellion.britannia_mod.bannerdyeing.testsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.MountDefinition;
import com.seggellion.britannia_mod.banner.data.PlacementProfile;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionResource;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistryDomain;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import java.util.ArrayList;
import java.util.List;

public final class RegistryDatasetFixtures {
    public static final String BANNER_SOURCE = "britannia_mod:banner_definitions/test_banner.json";
    public static final String MATERIAL_SOURCE = "britannia_mod:fabric_materials/test_silk.json";
    public static final String PIGMENT_SOURCE = "britannia_mod:pigments/test_crimson.json";
    public static final String PALETTE_SOURCE = "britannia_mod:material_palettes/test_silk.json";
    public static final String BRASS_SOURCE = "britannia_mod:banner_mounts/test_brass.json";
    public static final String IRON_SOURCE = "britannia_mod:banner_mounts/test_iron.json";
    public static final String PROFILE_SOURCE = "britannia_mod:placement_profiles/test_medium.json";

    private RegistryDatasetFixtures() {
    }

    public static List<DefinitionResource> valid() {
        MountDefinition brass = CoreDataFixtures.mount();
        MountDefinition iron = new MountDefinition(CoreDataFixtures.SCHEMA, CoreDataFixtures.IRON_MOUNT_ID,
                "mount.britannia_mod.test_iron", brass.geometry(), brass.texture(), List.of("metal"));
        return List.of(
                resource(RegistryDomain.BANNER_DEFINITION, BANNER_SOURCE,
                        BannerDefinition.CODEC, CoreDataFixtures.bannerDefinition()),
                resource(RegistryDomain.FABRIC_MATERIAL, MATERIAL_SOURCE,
                        FabricMaterialDefinition.CODEC, CoreDataFixtures.fabricMaterial()),
                resource(RegistryDomain.PIGMENT, PIGMENT_SOURCE,
                        PigmentDefinition.CODEC, CoreDataFixtures.pigment()),
                resource(RegistryDomain.MATERIAL_PALETTE, PALETTE_SOURCE,
                        MaterialPalette.CODEC, CoreDataFixtures.palette()),
                resource(RegistryDomain.MOUNT, BRASS_SOURCE, MountDefinition.CODEC, brass),
                resource(RegistryDomain.MOUNT, IRON_SOURCE, MountDefinition.CODEC, iron),
                resource(RegistryDomain.PLACEMENT_PROFILE, PROFILE_SOURCE,
                        PlacementProfile.CODEC, CoreDataFixtures.placement()));
    }

    public static List<DefinitionResource> withMutation(
            RegistryDomain domain, String source, java.util.function.Consumer<JsonObject> mutation) {
        List<DefinitionResource> resources = new ArrayList<>(valid());
        int index = indexOf(resources, domain, source);
        JsonObject json = JsonParser.parseString(resources.get(index).contents()).getAsJsonObject();
        mutation.accept(json);
        resources.set(index, DefinitionResource.text(domain, source, json.toString()));
        return List.copyOf(resources);
    }

    public static List<DefinitionResource> missingPalette() {
        return withMutation(RegistryDomain.FABRIC_MATERIAL, MATERIAL_SOURCE,
                json -> json.addProperty("palette_id", "britannia_mod:missing_palette"));
    }

    public static List<DefinitionResource> missingDefaultMount() {
        return withMutation(RegistryDomain.BANNER_DEFINITION, BANNER_SOURCE, json -> {
            JsonArray mounts = new JsonArray();
            mounts.add("britannia_mod:missing_mount");
            json.add("supported_mounts", mounts);
            json.addProperty("default_mount", "britannia_mod:missing_mount");
        });
    }

    public static List<DefinitionResource> missingSupportedMount() {
        return withMutation(RegistryDomain.BANNER_DEFINITION, BANNER_SOURCE, json ->
                json.getAsJsonArray("supported_mounts").set(1,
                        JsonParser.parseString("\"britannia_mod:missing_mount\"")));
    }

    public static List<DefinitionResource> missingPlacementProfile() {
        return withMutation(RegistryDomain.BANNER_DEFINITION, BANNER_SOURCE,
                json -> json.addProperty("placement_profile", "britannia_mod:missing_profile"));
    }

    public static List<DefinitionResource> dimensionMismatch() {
        return withMutation(RegistryDomain.PLACEMENT_PROFILE, PROFILE_SOURCE, json -> {
            JsonObject dimensions = json.getAsJsonObject("dimensions");
            dimensions.addProperty("width_blocks", 1);
            dimensions.addProperty("height_blocks", 2);
        });
    }

    public static List<DefinitionResource> missingOverridePigment() {
        return withMutation(RegistryDomain.MATERIAL_PALETTE, PALETTE_SOURCE, json -> {
            JsonObject overrides = new JsonObject();
            overrides.addProperty("britannia_mod:missing_pigment", CoreDataFixtures.DYED_COLOUR_ID.toString());
            json.add("pigment_overrides", overrides);
        });
    }

    public static List<DefinitionResource> paletteOwnerMismatch() {
        return withMutation(RegistryDomain.MATERIAL_PALETTE, PALETTE_SOURCE,
                json -> json.addProperty("material_id", "britannia_mod:other_material"));
    }

    public static List<DefinitionResource> naturalColourMismatch() {
        return withMutation(RegistryDomain.FABRIC_MATERIAL, MATERIAL_SOURCE,
                json -> json.addProperty("natural_colour_id", CoreDataFixtures.DYED_COLOUR_ID.toString()));
    }

    public static List<DefinitionResource> duplicateBannerId() {
        List<DefinitionResource> resources = new ArrayList<>(valid());
        String contents = resources.get(indexOf(resources, RegistryDomain.BANNER_DEFINITION, BANNER_SOURCE)).contents();
        resources.add(DefinitionResource.text(RegistryDomain.BANNER_DEFINITION,
                "other_pack:banner_definitions/duplicate.json", contents));
        return List.copyOf(resources);
    }

    public static List<DefinitionResource> malformedBanner() {
        return replace(RegistryDomain.BANNER_DEFINITION, BANNER_SOURCE, "{ definitely not json");
    }

    public static List<DefinitionResource> unknownBannerSchema() {
        return withMutation(RegistryDomain.BANNER_DEFINITION, BANNER_SOURCE,
                json -> json.addProperty("schema_version", 99));
    }

    public static List<DefinitionResource> multipleErrors() {
        List<DefinitionResource> resources = new ArrayList<>(missingDefaultMount());
        int material = indexOf(resources, RegistryDomain.FABRIC_MATERIAL, MATERIAL_SOURCE);
        JsonObject json = JsonParser.parseString(resources.get(material).contents()).getAsJsonObject();
        json.addProperty("palette_id", "britannia_mod:missing_palette");
        resources.set(material, DefinitionResource.text(RegistryDomain.FABRIC_MATERIAL, MATERIAL_SOURCE,
                json.toString()));
        resources.add(DefinitionResource.text(RegistryDomain.PIGMENT,
                "britannia_mod:pigments/broken.json", "["));
        return List.copyOf(resources);
    }

    public static List<DefinitionResource> renamedBannerDisplay(String displayNameKey) {
        return withMutation(RegistryDomain.BANNER_DEFINITION, BANNER_SOURCE,
                json -> json.addProperty("display_name_key", displayNameKey));
    }

    private static List<DefinitionResource> replace(RegistryDomain domain, String source, String contents) {
        List<DefinitionResource> resources = new ArrayList<>(valid());
        resources.set(indexOf(resources, domain, source), DefinitionResource.text(domain, source, contents));
        return List.copyOf(resources);
    }

    private static int indexOf(List<DefinitionResource> resources, RegistryDomain domain, String source) {
        for (int index = 0; index < resources.size(); index++) {
            DefinitionResource resource = resources.get(index);
            if (resource.domain() == domain && resource.sourceResource().toString().equals(source)) {
                return index;
            }
        }
        throw new IllegalArgumentException("Missing fixture resource " + source);
    }

    private static <T> DefinitionResource resource(
            RegistryDomain domain, String source, Codec<T> codec, T definition) {
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, definition).getOrThrow();
        return DefinitionResource.text(domain, source, json.toString());
    }
}
