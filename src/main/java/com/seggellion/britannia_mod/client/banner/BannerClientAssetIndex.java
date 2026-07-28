package com.seggellion.britannia_mod.client.banner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-only model/texture registration inputs generated from the authoritative banner catalogue.
 *
 * <p>The index is a classpath asset rather than gameplay data: model registration runs before synchronized
 * definitions are available. The scaffold owns and verifies the index so definition-specific final assets are
 * registered without per-banner Java changes.
 */
public final class BannerClientAssetIndex {
    public static final String RESOURCE_PATH = "assets/britannia_mod/banner_client_assets.json";
    public static final int SCHEMA_VERSION = 1;
    private static final Index CURRENT = load();

    private BannerClientAssetIndex() {
    }

    public static Set<ResourceLocation> geometryModels() {
        return CURRENT.geometryModels();
    }

    public static Set<ResourceLocation> textures() {
        return CURRENT.textures();
    }

    private static Index load() {
        ClassLoader loader = BannerClientAssetIndex.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Missing generated banner client asset index: " + RESOURCE_PATH);
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.size() != 3
                    || !root.has("schema_version")
                    || !root.has("geometry_models")
                    || !root.has("textures")) {
                throw new IllegalStateException("Banner client asset index must contain exactly "
                        + "schema_version, geometry_models, and textures");
            }
            int schemaVersion = root.get("schema_version").getAsInt();
            if (schemaVersion != SCHEMA_VERSION) {
                throw new IllegalStateException("Unsupported banner client asset index schema: " + schemaVersion);
            }
            return new Index(ids(root.getAsJsonArray("geometry_models"), "geometry_models"),
                    ids(root.getAsJsonArray("textures"), "textures"));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Invalid generated banner client asset index: " + RESOURCE_PATH,
                    exception);
        }
    }

    private static Set<ResourceLocation> ids(JsonArray values, String field) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException("Banner client asset index " + field + " must not be empty");
        }
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        for (JsonElement value : values) {
            ResourceLocation id = ResourceLocation.tryParse(value.getAsString());
            if (id == null || !result.add(id)) {
                throw new IllegalStateException("Invalid or duplicate " + field + " entry: " + value);
            }
        }
        return Set.copyOf(result);
    }

    private record Index(Set<ResourceLocation> geometryModels, Set<ResourceLocation> textures) {
    }
}
