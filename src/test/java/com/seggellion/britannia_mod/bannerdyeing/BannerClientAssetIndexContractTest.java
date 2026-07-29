package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.client.banner.BannerAssetAvailability;
import com.seggellion.britannia_mod.client.banner.BannerClientAssetIndex;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class BannerClientAssetIndexContractTest {
    private static final Path DEFINITIONS =
            Path.of("src/main/resources/data/britannia_mod/banner_definitions");
    private static final Path PLACEMENT_PROFILES =
            Path.of("src/main/resources/data/britannia_mod/placement_profiles");
    private static final Path INDEX =
            Path.of("src/main/resources/assets/britannia_mod/banner_client_assets.json");
    private static final Path BLOCK_ATLAS =
            Path.of("src/main/resources/assets/minecraft/atlases/blocks.json");

    @Test
    void generatedIndexContainsEveryDeclaredGeometryAndTextureExactlyOnce() throws Exception {
        LinkedHashSet<ResourceLocation> geometries = new LinkedHashSet<>();
        LinkedHashSet<ResourceLocation> textures = new LinkedHashSet<>();
        try (var paths = Files.list(DEFINITIONS)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".json")).sorted().toList()) {
                JsonObject assets = JsonParser.parseString(Files.readString(path))
                        .getAsJsonObject().getAsJsonObject("assets");
                geometries.add(ResourceLocation.parse(assets.get("geometry").getAsString()));
                textures.add(ResourceLocation.parse(assets.get("base_texture").getAsString()));
                textures.add(ResourceLocation.parse(assets.get("dye_mask").getAsString()));
            }
        }
        try (var paths = Files.list(PLACEMENT_PROFILES)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".json")).sorted().toList()) {
                JsonObject profile = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (!profile.has("orientation_mount_geometry")) {
                    continue;
                }
                JsonObject orientationModels = profile.getAsJsonObject("orientation_mount_geometry");
                geometries.add(ResourceLocation.parse(orientationModels.get("wall_parallel").getAsString()));
                geometries.add(ResourceLocation.parse(orientationModels.get("wall_perpendicular").getAsString()));
            }
        }

        assertEquals(12, geometries.size(), "shared geometry IDs must be de-duplicated");
        assertEquals(32, textures.size(), "shared texture IDs must be de-duplicated");
        assertEquals(geometries, BannerClientAssetIndex.geometryModels());
        assertEquals(textures, BannerClientAssetIndex.textures());
        assertFalse(geometries.contains(id("banner/placeholder/x_small")));
        assertTrue(geometries.contains(id("banner/road_guard/geometry")));
        assertTrue(textures.contains(id("banner/road_guard/base_texture")));
        assertTrue(textures.contains(id("banner/road_guard/dye_mask")));
    }

    @Test
    void clientRegistrationUsesGeneratedIndexWithoutBannerSpecificJavaIds() throws Exception {
        String availability = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/client/banner/BannerAssetAvailability.java"));
        String events = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/client/banner/BannerClientEvents.java"));

        assertTrue(availability.contains("BannerClientAssetIndex.geometryModels()"));
        assertTrue(availability.contains("BannerClientAssetIndex.textures()"));
        assertFalse(availability.contains("road_guard"));
        assertTrue(events.contains("ModelResourceLocation.standalone(id)"));
        assertTrue(events.contains("BannerAssetAvailability.EXPECTED_MODELS"));
        assertTrue(BannerAssetAvailability.EXPECTED_MODELS.contains(id("banner/road_guard/geometry")));
    }

    @Test
    void bannerDirectoryIsIncludedInTheMinecraftBlockAtlas() throws Exception {
        JsonObject atlas = JsonParser.parseString(Files.readString(BLOCK_ATLAS)).getAsJsonObject();
        assertEquals(1, atlas.getAsJsonArray("sources").size());
        JsonObject source = atlas.getAsJsonArray("sources").get(0).getAsJsonObject();
        assertEquals(3, source.size());
        assertEquals("directory", source.get("type").getAsString());
        assertEquals("banner", source.get("source").getAsString());
        assertEquals("banner/", source.get("prefix").getAsString());
        assertFalse(source.has("remove"));

        Path textureRoot = Path.of("src/main/resources/assets/britannia_mod/textures");
        for (String texture : List.of(
                "banner/road_guard/base_texture",
                "banner/road_guard/dye_mask",
                "banner/placeholder/base_texture",
                "banner/placeholder/dye_mask",
                "banner/placeholder/missing",
                "banner/mount/brass",
                "banner/mount/iron")) {
            assertTrue(Files.isRegularFile(textureRoot.resolve(texture + ".png")), texture);
            assertTrue(texture.startsWith(source.get("prefix").getAsString()), texture);
        }

        // Directory sources contributed by resource packs are additive. This source neither removes nor
        // replaces vanilla sources, and its narrow path leaves unrelated texture directories alone.
        assertFalse("block/example".startsWith(source.get("prefix").getAsString()));
        ResourceLocation future = id("banner/future_final/base_texture");
        assertTrue(future.getPath().startsWith(source.get("prefix").getAsString()));
    }

    @Test
    void generatedIndexJsonIsStrictAndMatchesRuntimeView() throws Exception {
        JsonObject index = JsonParser.parseString(Files.readString(INDEX)).getAsJsonObject();
        assertEquals(3, index.size());
        assertEquals(1, index.get("schema_version").getAsInt());
        assertEquals(BannerClientAssetIndex.geometryModels().size(),
                index.getAsJsonArray("geometry_models").size());
        assertEquals(BannerClientAssetIndex.textures().size(),
                index.getAsJsonArray("textures").size());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", path);
    }
}
