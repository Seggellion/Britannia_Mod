package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The Villa architectural families draw their tracery from {@code ornateness}, the one sheet in the
 * plaster set with an alpha channel. Geometry that samples it has to reach the GPU on a layer that
 * tests alpha; on the solid layer Minecraft ignores alpha outright and every texel the artist left
 * clear comes back as an opaque rectangle.
 *
 * <p>That has already been fixed twice by hand - once in {@code ClientModSetup} for the window
 * families, once by adding {@code "render_type"} to the model JSON - and neither fix left anything
 * behind that would notice it being undone. This is that thing. It reads the blockstates, follows
 * every model they name, opens the PNGs those models sample, and insists that anything with a
 * transparent texel in it says so.
 *
 * <p>Only the families whose convention is a model-declared render type are covered. Plenty of
 * other blocks in the mod get theirs from {@code ItemBlockRenderTypes.setRenderLayer} instead,
 * which is a client-side call this suite cannot see.
 */
class PlasterRenderLayerContractTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = PROJECT.resolve("src/main/resources/assets/britannia_mod");

    /** The Villa architectural families: plaster walls, their windows, and the bannister. */
    private static final List<String> FAMILY_PREFIXES =
        List.of("plaster_", "ornate_wall_", "ornate_sandstone_", "bannister", "sandstone_");

    /** Layers that keep the alpha test. Anything else lets a clear texel render opaque. */
    private static final Set<String> ALPHA_AWARE = Set.of(
        "minecraft:cutout", "cutout", "minecraft:cutout_mipped", "cutout_mipped",
        "minecraft:translucent", "translucent", "minecraft:tripwire", "tripwire");

    private final Map<String, Boolean> alphaCache = new HashMap<>();

    @Test
    void everyVillaModelThatSamplesATransparentTextureDeclaresAnAlphaAwareLayer() throws IOException {
        Map<String, String> offenders = new TreeMap<>();

        for (Path blockstate : villaBlockstates()) {
            for (String modelId : modelsNamedBy(blockstate)) {
                Resolved model = resolve(modelId);
                if (model == null) {
                    continue;
                }
                List<String> transparent = new ArrayList<>();
                for (String texture : model.textures()) {
                    if (hasTransparency(texture)) {
                        transparent.add(texture);
                    }
                }
                if (transparent.isEmpty()) {
                    continue;
                }
                if (model.renderType() == null || !ALPHA_AWARE.contains(model.renderType())) {
                    offenders.put(modelId, blockstate.getFileName() + " -> render_type="
                        + model.renderType() + ", samples " + transparent);
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "These Villa models sample a texture with transparent texels but do not declare a layer "
                + "that tests alpha, so those texels will render as opaque blocks:\n"
                + String.join("\n", offenders.values()));
    }

    @Test
    void theOrnatenessSheetIsStillTheTransparentOneAndIsStillClearNotBlack() throws IOException {
        // The fix only holds while the art keeps its alpha. If the sheet is ever flattened, the
        // tracery becomes a solid rectangle and no render layer can save it.
        assertTrue(hasTransparency("britannia_mod:block/structure/plaster/ornateness"),
            "ornateness.png has lost its alpha channel - the tracery has nothing to cut out");

        BufferedImage image = ImageIO.read(texturePath(
            "britannia_mod:block/structure/plaster/ornateness").toFile());
        int darkClearTexels = 0;
        int clearTexels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) != 0) {
                    continue;
                }
                clearTexels++;
                int luminance = (((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF)) / 3;
                if (luminance < 32) {
                    darkClearTexels++;
                }
            }
        }
        assertTrue(clearTexels > 0, "precondition: the sheet has clear texels");
        assertFalse(darkClearTexels * 4 > clearTexels,
            "most of what the artist left clear in ornateness.png is painted black underneath ("
                + darkClearTexels + " of " + clearTexels + "). Cutout discards it, but any pass that "
                + "samples a mip level or ignores alpha will bleed that black into the tracery edge");
    }

    /* ─── reading the assets ─────────────────────────────────── */

    private record Resolved(String renderType, Set<String> textures) {
    }

    private static List<Path> villaBlockstates() throws IOException {
        try (Stream<Path> files = Files.list(ASSETS.resolve("blockstates"))) {
            return files
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .filter(path -> FAMILY_PREFIXES.stream()
                    .anyMatch(prefix -> path.getFileName().toString().startsWith(prefix)))
                .sorted()
                .toList();
        }
    }

    private static Set<String> modelsNamedBy(Path blockstate) throws IOException {
        JsonObject root = readJson(blockstate);
        Set<String> models = new LinkedHashSet<>();
        if (root.has("variants")) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("variants").entrySet()) {
                collectModels(entry.getValue(), models);
            }
        }
        if (root.has("multipart")) {
            for (JsonElement part : root.getAsJsonArray("multipart")) {
                collectModels(part.getAsJsonObject().get("apply"), models);
            }
        }
        return models;
    }

    private static void collectModels(JsonElement element, Set<String> into) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectModels(child, into));
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("model")) {
            into.add(object.get("model").getAsString());
        }
    }

    /** A model's render type and every texture it or its parents name, following {@code #refs}. */
    private static Resolved resolve(String modelId) throws IOException {
        Map<String, String> textures = new HashMap<>();
        String renderType = null;
        String current = modelId;

        for (int depth = 0; current != null && depth < 10; depth++) {
            Path path = modelPath(current);
            if (path == null || !Files.exists(path)) {
                break;
            }
            JsonObject model = readJson(path);
            if (model.has("textures")) {
                for (Map.Entry<String, JsonElement> entry : model.getAsJsonObject("textures").entrySet()) {
                    textures.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
                }
            }
            if (renderType == null && model.has("render_type")) {
                renderType = model.get("render_type").getAsString();
            }
            current = model.has("parent") ? model.get("parent").getAsString() : null;
        }

        Set<String> resolved = new LinkedHashSet<>();
        for (String value : textures.values()) {
            String texture = value;
            for (int hop = 0; texture.startsWith("#") && hop < 10; hop++) {
                texture = textures.getOrDefault(texture.substring(1), texture);
            }
            if (!texture.startsWith("#")) {
                resolved.add(texture);
            }
        }
        return new Resolved(renderType, resolved);
    }

    private boolean hasTransparency(String textureId) throws IOException {
        Boolean cached = this.alphaCache.get(textureId);
        if (cached != null) {
            return cached;
        }
        boolean transparent = false;
        Path path = texturePath(textureId);
        if (path != null && Files.exists(path)) {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image != null && image.getColorModel().hasAlpha()) {
                outer:
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        if ((image.getRGB(x, y) >>> 24) != 0xFF) {
                            transparent = true;
                            break outer;
                        }
                    }
                }
            }
        }
        this.alphaCache.put(textureId, transparent);
        return transparent;
    }

    private static Path modelPath(String modelId) {
        String[] parts = split(modelId);
        return parts[0].equals("britannia_mod") ? ASSETS.resolve("models/" + parts[1] + ".json") : null;
    }

    private static Path texturePath(String textureId) {
        String[] parts = split(textureId);
        return parts[0].equals("britannia_mod") ? ASSETS.resolve("textures/" + parts[1] + ".png") : null;
    }

    private static String[] split(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? new String[] {"minecraft", id}
                         : new String[] {id.substring(0, colon), id.substring(colon + 1)};
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
