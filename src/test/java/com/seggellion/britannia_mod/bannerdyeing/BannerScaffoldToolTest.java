package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.tools.BannerScaffoldTool;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BannerScaffoldToolTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @TempDir
    Path temporaryDirectory;

    @Test
    void normalGenerationCreatesEveryExpectedOutput() throws Exception {
        Path root = seed();
        BannerScaffoldTool.RunSummary summary = run(root, false, false).summary;

        assertEquals(33, summary.generatedDefinitions());
        assertEquals(33, countJson(root.resolve("src/main/resources/data/britannia_mod/banner_definitions")));
        assertEquals(7, countSupportingJson(root));
        assertEquals(6, countFiles(root.resolve("src/main/resources/assets/britannia_mod/models/banner/placeholder")));
        assertEquals(3, countFiles(root.resolve("src/main/resources/assets/britannia_mod/textures/banner/placeholder")));
        assertEquals(2, countFiles(root.resolve("src/main/resources/assets/britannia_mod/models/banner/mount")));
        assertEquals(2, countFiles(root.resolve("src/main/resources/assets/britannia_mod/textures/banner/mount")));
        assertTrue(Files.isRegularFile(root.resolve(BannerScaffoldTool.STATUS_PATH)));
        assertTrue(Files.isRegularFile(root.resolve(BannerScaffoldTool.METADATA_PATH)));
        assertTrue(Files.isRegularFile(root.resolve(
                "src/main/resources/assets/britannia_mod/banner_client_assets.json")));
        assertTrue(Files.isRegularFile(root.resolve(
                "src/main/resources/assets/minecraft/atlases/blocks.json")));
        String status = Files.readString(root.resolve(BannerScaffoldTool.STATUS_PATH));
        assertTrue(status.contains("Supported orientations"));
        assertTrue(status.contains("Parallel automated"));
        assertTrue(status.contains("Perpendicular automated"));
        assertTrue(status.contains("Brass automated"));
        assertTrue(status.contains("Iron automated"));
        assertTrue(status.contains("Manual result"));
        assertTrue(status.contains("Final per-definition orientations approved: 1 of 33"));
        assertTrue(status.contains("Final per-definition mounts approved: 1 of 33"));
        assertTrue(status.contains("Final placed artwork intake approved: 1 of 33"));
        assertTrue(status.contains("Banner crafting implemented: no"));
        assertTrue(status.contains("Admin acquisition implemented: yes"));
        assertTrue(status.contains("NPC/shop distribution implemented: no"));
        assertFalse(status.contains("Recipe ID"));
        assertFalse(status.contains("Recipe definitions complete"));
        assertEquals(33, status.lines().filter(line -> line.matches("\\| \\d{2} \\|.*")).count());
        assertFalse(Files.exists(root.resolve("src/main/resources/data/britannia_mod/recipe/banner")));
        assertFalse(Files.exists(root.resolve("src/main/resources/data/britannia_mod/tags/item/banner_fabric")));
        assertFalse(Files.exists(root.resolve("src/main/resources/data/britannia_mod/tags/item/banner_mount")));
    }

    @Test
    void normalAndForceRunsPreserveApprovedRoadGuardAssets() throws Exception {
        Path root = seed();
        Path base = root.resolve(
                "src/main/resources/assets/britannia_mod/textures/banner/road_guard/base_texture.png");
        Path mask = root.resolve(
                "src/main/resources/assets/britannia_mod/textures/banner/road_guard/dye_mask.png");
        Path geometry = root.resolve(
                "src/main/resources/assets/britannia_mod/models/banner/road_guard/geometry.json");
        byte[] baseBefore = Files.readAllBytes(base);
        byte[] maskBefore = Files.readAllBytes(mask);
        byte[] geometryBefore = Files.readAllBytes(geometry);

        run(root, false, false);
        run(root, false, true);

        assertArrayEquals(baseBefore, Files.readAllBytes(base));
        assertArrayEquals(maskBefore, Files.readAllBytes(mask));
        assertArrayEquals(geometryBefore, Files.readAllBytes(geometry));
        JsonObject metadata = JsonParser.parseString(
                Files.readString(root.resolve(BannerScaffoldTool.METADATA_PATH))).getAsJsonObject();
        JsonObject files = metadata.getAsJsonObject("files");
        assertTrue(files.has(root.relativize(base).toString().replace('\\', '/')));
        assertTrue(files.has(root.relativize(mask).toString().replace('\\', '/')));
        assertTrue(files.has(root.relativize(geometry).toString().replace('\\', '/')));
    }

    @Test
    void checkRejectsChangedApprovedRoadGuardAsset() throws Exception {
        Path root = seed();
        Path base = root.resolve(
                "src/main/resources/assets/britannia_mod/textures/banner/road_guard/base_texture.png");
        byte[] changed = Files.readAllBytes(base);
        changed[changed.length - 1] ^= 1;
        Files.write(base, changed);

        BannerScaffoldTool.ScaffoldException exception = assertThrows(
                BannerScaffoldTool.ScaffoldException.class, () -> run(root, true, false));
        assertTrue(exception.getMessage().contains("base texture hash mismatch"), exception.getMessage());
    }

    @Test
    void outputIsDeterministicAcrossNormalRuns() throws Exception {
        Path root = seed();
        run(root, false, false);
        Map<String, byte[]> first = snapshotDeclaredFiles(root);
        run(root, false, false);
        Map<String, byte[]> second = snapshotDeclaredFiles(root);
        assertEquals(first.keySet(), second.keySet());
        first.forEach((path, bytes) -> assertArrayEquals(bytes, second.get(path), path));
    }

    @Test
    void checkPassesAfterGeneration() throws Exception {
        Path root = seed();
        run(root, false, false);
        BannerScaffoldTool.RunSummary summary = run(root, true, false).summary;
        assertEquals(33, summary.activeDefinitions());
        assertEquals(0, summary.disabledDefinitions());
    }

    @Test
    void checkFailsAfterGeneratedFileChanges() throws Exception {
        Path root = seed();
        run(root, false, false);
        Path definition = root.resolve("src/main/resources/data/britannia_mod/banner_definitions/large_01.json");
        Files.writeString(definition, Files.readString(definition) + " ", StandardCharsets.UTF_8);
        assertThrows(BannerScaffoldTool.ScaffoldException.class, () -> run(root, true, false));
    }

    @Test
    void checkFailsWhenGeneratedClientAssetIndexIsMissing() throws Exception {
        Path root = seed();
        run(root, false, false);
        Files.delete(root.resolve("src/main/resources/assets/britannia_mod/banner_client_assets.json"));

        BannerScaffoldTool.ScaffoldException exception = assertThrows(
                BannerScaffoldTool.ScaffoldException.class, () -> run(root, true, false));
        assertTrue(exception.getMessage().contains("missing "
                + "src/main/resources/assets/britannia_mod/banner_client_assets.json"),
                exception.getMessage());
    }

    @Test
    void normalGenerationPreservesCustomizedOutput() throws Exception {
        Path root = seed();
        run(root, false, false);
        Path definition = root.resolve("src/main/resources/data/britannia_mod/banner_definitions/large_01.json");
        byte[] customized = "customized\n".getBytes(StandardCharsets.UTF_8);
        Files.write(definition, customized);

        Invocation invocation = run(root, false, false);
        assertArrayEquals(customized, Files.readAllBytes(definition));
        assertTrue(invocation.summary.customizedFiles().stream().anyMatch(path -> path.endsWith("large_01.json")));
        assertTrue(invocation.output.contains("Preserved customized output"));
    }

    @Test
    void forceWarnsAndOverwritesOnlyDeclaredCustomizedOutputs() throws Exception {
        Path root = seed();
        run(root, false, false);
        Path definition = root.resolve("src/main/resources/data/britannia_mod/banner_definitions/large_01.json");
        Files.writeString(definition, "customized\n", StandardCharsets.UTF_8);
        Path unrelated = root.resolve("src/main/resources/data/britannia_mod/banner_definitions/user_owned.json");
        Files.writeString(unrelated, "user-owned\n", StandardCharsets.UTF_8);

        Invocation forced = run(root, false, true);
        assertTrue(Files.readString(definition).contains("britannia_mod:large_01"));
        assertEquals("user-owned\n", Files.readString(unrelated));
        assertTrue(forced.output.contains("WARNING: --force will overwrite"));
        assertTrue(forced.output.contains("large_01.json"));
    }

    @Test
    void invalidManifestCausesNoPartialRewrite() throws Exception {
        Path root = seed();
        Path sentinel = root.resolve("src/main/resources/data/britannia_mod/banner_definitions/sentinel.json");
        Files.createDirectories(sentinel.getParent());
        Files.writeString(sentinel, "preserve", StandardCharsets.UTF_8);
        mutate(root, object -> object.addProperty("schema_version", 99));

        assertThrows(BannerScaffoldTool.ScaffoldException.class, () -> run(root, false, false));
        assertEquals("preserve", Files.readString(sentinel));
        assertFalse(Files.exists(root.resolve(BannerScaffoldTool.STATUS_PATH)));
    }

    @Test
    void duplicateIdIsRejected() throws Exception {
        Path root = seed();
        mutate(root, object -> banners(object).get(1).getAsJsonObject().addProperty("id", "large_01"));
        assertInvalid(root, "Duplicate stable ID");
    }

    @Test
    void duplicateIndexIsRejected() throws Exception {
        Path root = seed();
        mutate(root, object -> banners(object).get(1).getAsJsonObject().addProperty("index", 1));
        assertInvalid(root, "Duplicate index");
    }

    @Test
    void missingIndexIsRejected() throws Exception {
        Path root = seed();
        mutate(root, object -> banners(object).get(32).getAsJsonObject().addProperty("index", 34));
        assertInvalid(root, "continuous");
    }

    @Test
    void entryCountOtherThanThirtyThreeIsRejected() throws Exception {
        Path root = seed();
        mutate(root, object -> banners(object).remove(32));
        assertInvalid(root, "exactly 33");
    }

    @Test
    void unknownGroupIsRejected() throws Exception {
        Path root = seed();
        mutate(root, object -> banners(object).get(0).getAsJsonObject().addProperty("group", "enormous"));
        assertInvalid(root, "Unknown group");
    }

    @Test
    void invalidSourceReferenceIsRejected() throws Exception {
        Path root = seed();
        mutate(root, object -> banners(object).get(0).getAsJsonObject().addProperty("page", 0));
        assertInvalid(root, "positive");
    }

    @Test
    void outputTraversalIsRejected() throws Exception {
        Path root = seed();
        mutate(root, object -> banners(object).get(0).getAsJsonObject().addProperty("id", "../outside"));
        assertInvalid(root, "Unsafe or invalid stable ID");
        assertFalse(Files.exists(root.resolve("outside.json")));
    }

    @Test
    void unrelatedFilesArePreservedByNormalAndForceRuns() throws Exception {
        Path root = seed();
        Path unrelated = root.resolve("src/main/resources/data/britannia_mod/banner_definitions/notes.txt");
        Files.createDirectories(unrelated.getParent());
        Files.writeString(unrelated, "mine", StandardCharsets.UTF_8);
        run(root, false, false);
        run(root, false, true);
        assertEquals("mine", Files.readString(unrelated));
    }

    @Test
    void localizationMergePreservesUnrelatedKeys() throws Exception {
        Path root = seed();
        run(root, false, false);
        JsonObject language = JsonParser.parseString(Files.readString(root.resolve(BannerScaffoldTool.LOCALIZATION_PATH)))
                .getAsJsonObject();
        assertEquals("Keep Me", language.get("unrelated.key").getAsString());
        assertEquals(33, language.entrySet().stream().filter(entry -> entry.getKey().startsWith("banner.britannia_mod."))
                .count());
    }

    @Test
    void normalRunPreservesCustomizedLocalizationAndForceRestoresIt() throws Exception {
        Path root = seed();
        run(root, false, false);
        Path languagePath = root.resolve(BannerScaffoldTool.LOCALIZATION_PATH);
        String key = "banner.britannia_mod.large_01";
        String customized = Files.readString(languagePath).replace(
                "Large Banner 01 (Name Required)", "Artist Custom Label");
        Files.writeString(languagePath, customized, StandardCharsets.UTF_8);

        Invocation normal = run(root, false, false);
        assertTrue(Files.readString(languagePath).contains("Artist Custom Label"));
        assertTrue(normal.summary.customizedFiles().stream().anyMatch(path -> path.contains(key)));
        Invocation forced = run(root, false, true);
        assertTrue(forced.output.contains("overwrite customized localization"));
        assertTrue(Files.readString(languagePath).contains("Large Banner 01 (Name Required)"));
    }

    private Path seed() throws Exception {
        Path root = temporaryDirectory.resolve("repository");
        Path manifest = root.resolve(BannerScaffoldTool.MANIFEST_PATH);
        Files.createDirectories(manifest.getParent());
        Files.copy(Path.of(BannerScaffoldTool.MANIFEST_PATH), manifest);
        Path language = root.resolve(BannerScaffoldTool.LOCALIZATION_PATH);
        Files.createDirectories(language.getParent());
        Files.writeString(language, "{\n  \"unrelated.key\": \"Keep Me\"\n}\n", StandardCharsets.UTF_8);
        copyApprovedAsset(root,
                "src/main/resources/assets/britannia_mod/textures/banner/road_guard/base_texture.png");
        copyApprovedAsset(root,
                "src/main/resources/assets/britannia_mod/textures/banner/road_guard/dye_mask.png");
        copyApprovedAsset(root,
                "src/main/resources/assets/britannia_mod/models/banner/road_guard/geometry.json");
        return root;
    }

    private static void copyApprovedAsset(Path root, String relative) throws Exception {
        Path destination = root.resolve(relative);
        Files.createDirectories(destination.getParent());
        Files.copy(Path.of(relative), destination);
    }

    private Invocation run(Path root, boolean check, boolean force) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BannerScaffoldTool.RunSummary summary;
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            summary = BannerScaffoldTool.execute(root, new BannerScaffoldTool.Options(check, force), output);
        }
        return new Invocation(summary, bytes.toString(StandardCharsets.UTF_8));
    }

    private void mutate(Path root, Consumer<JsonObject> mutation) throws Exception {
        Path path = root.resolve(BannerScaffoldTool.MANIFEST_PATH);
        JsonObject object = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        mutation.accept(object);
        Files.writeString(path, GSON.toJson(object) + "\n", StandardCharsets.UTF_8);
    }

    private void assertInvalid(Path root, String messagePart) {
        BannerScaffoldTool.ScaffoldException exception = assertThrows(
                BannerScaffoldTool.ScaffoldException.class, () -> run(root, false, false));
        assertTrue(exception.getMessage().contains(messagePart), exception.getMessage());
    }

    private static JsonArray banners(JsonObject object) {
        return object.getAsJsonArray("banners");
    }

    private static long countJson(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json")).count();
        }
    }

    private static long countFiles(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static long countSupportingJson(Path root) throws Exception {
        long count = 0;
        for (String folder : new String[] {"banner_mounts", "placement_profiles"}) {
            count += countJson(root.resolve("src/main/resources/data/britannia_mod/" + folder));
        }
        return count;
    }

    private static Map<String, byte[]> snapshotDeclaredFiles(Path root) throws Exception {
        LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                result.put(relative, Files.readAllBytes(path));
            }
        }
        return result;
    }

    private record Invocation(BannerScaffoldTool.RunSummary summary, String output) {
    }
}
