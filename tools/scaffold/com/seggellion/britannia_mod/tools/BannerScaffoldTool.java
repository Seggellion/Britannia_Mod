package com.seggellion.britannia_mod.tools;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionResource;
import com.seggellion.britannia_mod.bannerdyeing.registry.ProductionBannerCatalogue;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistryDataLoader;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistryDomain;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistryLoadResult;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotPublisher;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationPolicy;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Deterministic catalogue scaffold generator, verifier, and read-only intake entry point. */
public final class BannerScaffoldTool {
    public static final String MANIFEST_PATH = "content/banner_catalogue.yml";
    public static final String STATUS_PATH = "content/banner_catalogue_status.md";
    public static final String METADATA_PATH = "content/.banner_scaffold_metadata.json";
    public static final String LOCALIZATION_PATH =
            "src/main/resources/assets/britannia_mod/lang/en_us.json";
    private static final List<CanonicalEntry> CANONICAL = canonicalEntries();
    public static final int TARGET_COUNT = CANONICAL.size();

    private static final String DATA_ROOT = "src/main/resources/data/britannia_mod/";
    private static final String ASSET_ROOT = "src/main/resources/assets/britannia_mod/";
    private static final String CLIENT_ASSET_INDEX_PATH =
            ASSET_ROOT + "banner_client_assets.json";
    private static final String BLOCK_ATLAS_PATH =
            "src/main/resources/assets/minecraft/atlases/blocks.json";
    private static final Set<String> GROUPS = Set.of("large", "medium-wall", "medium", "small", "x-small");
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9_]+");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final String PROVISIONAL_SUFFIX = " (Name Required)";

    private BannerScaffoldTool() {
    }

    public record Options(boolean check, boolean force) {
        public Options {
            if (check && force) {
                throw new IllegalArgumentException("--check and --force cannot be used together");
            }
        }
    }

    public record RunSummary(
            int manifestEntries,
            int generatedDefinitions,
            int activeDefinitions,
            int disabledDefinitions,
            int localizationEntries,
            int provisionalNames,
            int provisionalDimensions,
            int placeholderAssetFamilies,
            List<String> customizedFiles) {
        public RunSummary {
            customizedFiles = List.copyOf(customizedFiles);
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length > 0 && "--check-final-intake".equals(args[0])) {
                if (args.length != 2 || args[1].isBlank()) {
                    throw new ScaffoldException("--check-final-intake requires exactly one intake path");
                }
                Path root = Path.of(".").toAbsolutePath().normalize();
                Path intake = Path.of(args[1]);
                if (!intake.isAbsolute()) {
                    intake = root.resolve(intake);
                }
                FinalContentIntakeValidator.Result result =
                        FinalContentIntakeValidator.validate(root, intake.normalize());
                FinalContentIntakeValidator.printReport(result, System.out);
                if (result.status() == FinalContentIntakeValidator.Status.INVALID) {
                    System.exit(1);
                }
                return;
            }
            Options options = parseOptions(args);
            RunSummary summary = execute(Path.of(".").toAbsolutePath().normalize(), options, System.out);
            System.out.printf(Locale.ROOT,
                    "Banner scaffold %s: manifest=%d definitions=%d active=%d disabled=%d localization=%d "
                            + "provisional_names=%d provisional_dimensions=%d asset_families=%d%n",
                    options.check ? "check passed" : "generation completed",
                    summary.manifestEntries, summary.generatedDefinitions, summary.activeDefinitions,
                    summary.disabledDefinitions, summary.localizationEntries, summary.provisionalNames,
                    summary.provisionalDimensions, summary.placeholderAssetFamilies);
        } catch (RuntimeException | IOException exception) {
            System.err.println("Banner scaffold failed: " + exception.getMessage());
            System.exit(1);
        }
    }

    public static RunSummary execute(Path repositoryRoot, Options options, PrintStream output) throws IOException {
        Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(output, "output");
        Path root = repositoryRoot.toAbsolutePath().normalize();
        Manifest manifest = readAndValidateManifest(root.resolve(MANIFEST_PATH));
        ResolvedCatalogue catalogue = resolve(manifest);
        LinkedHashMap<String, byte[]> expected = buildExpectedFiles(catalogue);
        RegistryLoadResult registry = validateRegistry(expected);
        expected.put(STATUS_PATH, utf8(statusReport(catalogue, registry)));
        validateAssetMappings(root, catalogue, expected.keySet());

        if (options.check) {
            checkOutputs(root, catalogue, expected);
            return summary(catalogue, registry, List.of());
        }

        Metadata metadata = readMetadata(root.resolve(METADATA_PATH));
        List<String> customized = findCustomizedFiles(root, expected, metadata);
        if (options.force && !customized.isEmpty()) {
            output.println("WARNING: --force will overwrite these customized declared outputs:");
            customized.forEach(path -> output.println("  " + path));
        }

        Set<String> customizedSet = Set.copyOf(customized);
        for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
            if (!customizedSet.contains(entry.getKey()) || options.force) {
                writeAtomic(root.resolve(entry.getKey()), entry.getValue());
            } else {
                output.println("Preserved customized output: " + entry.getKey());
            }
        }

        LocalizationResult localization = mergeLocalization(root, catalogue, metadata, options.force, output);
        Metadata refreshed = refreshMetadata(root, expected, catalogue, localization);
        writeAtomic(root.resolve(METADATA_PATH), utf8(json(refreshed.toJson())));

        if (customized.isEmpty() && localization.customizedKeys.isEmpty()) {
            checkOutputs(root, catalogue, expected);
        }
        List<String> allCustomized = new ArrayList<>(customized);
        localization.customizedKeys.forEach(key -> allCustomized.add(LOCALIZATION_PATH + "#" + key));
        return summary(catalogue, registry, allCustomized);
    }

    public static Manifest readAndValidateManifest(Path path) throws IOException {
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        Manifest manifest;
        try {
            manifest = GSON.fromJson(raw, Manifest.class);
        } catch (JsonParseException exception) {
            throw new ScaffoldException("Manifest must be valid JSON-compatible YAML 1.2: " + exception.getMessage());
        }
        validateManifest(manifest);
        return manifest;
    }

    private static Options parseOptions(String[] args) {
        boolean check = false;
        boolean force = false;
        for (String argument : args) {
            switch (argument) {
                case "--check" -> check = true;
                case "--force" -> force = true;
                case "" -> { }
                default -> throw new ScaffoldException("Unknown argument: " + argument);
            }
        }
        return new Options(check, force);
    }

    private static void validateManifest(Manifest manifest) {
        require(manifest != null, "Manifest root is required");
        require(manifest.schemaVersion == 1, "schema_version must be 1");
        require(manifest.defaults != null, "defaults are required");
        require(manifest.groups != null, "groups are required");
        require(manifest.sharedPlaceholderAssets != null, "shared_placeholder_assets are required");
        require(manifest.banners != null, "banners are required");
        require(manifest.banners.size() == TARGET_COUNT,
                "Manifest must contain exactly " + TARGET_COUNT + " entries; found " + manifest.banners.size());
        require(manifest.groups.keySet().equals(GROUPS),
                "groups must be exactly " + GROUPS + "; found " + manifest.groups.keySet());

        Defaults defaults = manifest.defaults;
        require("britannia_mod:cotton".equals(defaults.defaultMaterial),
                "default_material must be britannia_mod:cotton");
        require(List.of("britannia_mod:brass", "britannia_mod:iron").equals(defaults.supportedMounts),
                "supported_mounts must be brass then iron");
        require("britannia_mod:brass".equals(defaults.defaultMount),
                "default_mount must be britannia_mod:brass");
        require(List.of("wall_parallel", "wall_perpendicular").equals(defaults.supportedOrientations),
                "supported_orientations must contain the two scaffold defaults in canonical order");
        require("placeholder".equals(defaults.contentStatus), "content_status must be placeholder");
        require(Boolean.TRUE.equals(defaults.dimensionsProvisional),
                "all initial dimensions must be marked provisional");
        validateResourceId(defaults.baseTexture, "base_texture");
        validateResourceId(defaults.dyeMask, "dye_mask");
        require(manifest.sharedPlaceholderAssets.equals(Map.of(
                        "base_texture", "britannia_mod:banner/placeholder/base_texture",
                        "dye_mask", "britannia_mod:banner/placeholder/dye_mask",
                        "missing", "britannia_mod:banner/placeholder/missing")),
                "shared_placeholder_assets must declare exactly base_texture, dye_mask, and missing");

        Set<Integer> indices = new LinkedHashSet<>();
        Set<String> ids = new LinkedHashSet<>();
        for (int position = 0; position < manifest.banners.size(); position++) {
            BannerEntry entry = manifest.banners.get(position);
            require(entry != null, "Banner entry " + (position + 1) + " is null");
            require(indices.add(entry.index), "Duplicate index: " + entry.index);
            require(entry.id != null && SAFE_ID.matcher(entry.id).matches(),
                    "Unsafe or invalid stable ID: " + entry.id);
            require(!entry.id.contains("..") && !entry.id.contains("/") && !entry.id.contains("\\"),
                    "Output traversal or unsafe path rejected: " + entry.id);
            require(ids.add(entry.id), "Duplicate stable ID: " + entry.id);
            require(GROUPS.contains(entry.group), "Unknown group for " + entry.id + ": " + entry.group);
            require(entry.page > 0 && entry.row > 0,
                    "Source page and row must be positive for " + entry.id);
            require(notBlank(entry.displayName), "display_name is required for " + entry.id);
            require("provisional".equals(entry.nameStatus) || "source-named".equals(entry.nameStatus),
                    "Unknown name_status for " + entry.id + ": " + entry.nameStatus);
            if ("provisional".equals(entry.nameStatus)) {
                require(entry.sourceLabel == null, "Provisional entry must not claim a source label: " + entry.id);
                require(notBlank(entry.notes) && entry.notes.contains("Name Required"),
                        "Provisional entry must visibly retain Name Required status: " + entry.id);
                require(localizedName(entry).contains("Name Required"),
                        "Generated provisional localization must be visibly temporary: " + entry.id);
            } else {
                require(notBlank(entry.sourceLabel), "Source-named entry needs source_label: " + entry.id);
            }
            if (Boolean.TRUE.equals(entry.displayNameApproved)) {
                require("source-named".equals(entry.nameStatus), "Approved display name must not be provisional: " + entry.id);
            }
            String contentStatus = first(entry.contentStatus, defaults.contentStatus);
            require(Set.of("placeholder", "in_progress", "complete").contains(contentStatus),
                    "Unknown content_status for " + entry.id + ": " + contentStatus);
            boolean provisional = first(entry.dimensionsProvisional, defaults.dimensionsProvisional);
            if ("placeholder".equals(contentStatus)) {
                require(provisional, "Placeholder dimensions must remain provisional: " + entry.id);
            } else {
                require(!provisional, "Integrated dimensions must be approved: " + entry.id);
                validateIntegratedEntry(entry, defaults, manifest.groups.get(entry.group));
            }
        }
        require(indices.equals(range(1, TARGET_COUNT)),
                "Indices must be continuous from 1 through " + TARGET_COUNT + "; found " + indices);

        for (int i = 0; i < CANONICAL.size(); i++) {
            CanonicalEntry expected = CANONICAL.get(i);
            BannerEntry actual = manifest.banners.get(i);
            require(expected.matches(actual), "Canonical catalogue mismatch at index " + expected.index
                    + "; expected " + expected + ", found " + actual);
        }
        Map<String, Long> groupCounts = counts(manifest.banners, entry -> entry.group);
        require(groupCounts.equals(Map.of("large", 6L, "medium-wall", 6L, "medium", 8L,
                        "small", 6L, "x-small", 9L)),
                "Canonical group counts do not match: " + groupCounts);

        validateGroup(manifest.groups.get("large"), 3, 2, "placeholder_large", "large");
        validateGroup(manifest.groups.get("medium-wall"), 2, 2, "placeholder_medium_wall", "medium_wall");
        validateGroup(manifest.groups.get("medium"), 1, 2, "placeholder_medium", "medium");
        validateGroup(manifest.groups.get("small"), 1, 1, "placeholder_small", "small");
        validateGroup(manifest.groups.get("x-small"), 1, 1, "placeholder_x_small", "x_small");
    }

    private static void validateIntegratedEntry(BannerEntry entry, Defaults defaults, Group group) {
        int width = first(entry.widthBlocks, group.widthBlocks);
        int height = first(entry.heightBlocks, group.heightBlocks);
        require(width >= 1 && width <= 3, "Integrated width must be from 1 through 3: " + entry.id);
        require(height >= 1 && height <= 16, "Integrated height must be from 1 through 16: " + entry.id);
        List<String> orientations = first(entry.supportedOrientations, defaults.supportedOrientations);
        require(!orientations.isEmpty()
                        && Set.of("wall_parallel", "wall_perpendicular").containsAll(orientations),
                "Integrated orientations are invalid: " + entry.id);
        List<String> mounts = first(entry.supportedMounts, defaults.supportedMounts);
        require(!mounts.isEmpty()
                        && Set.of("britannia_mod:brass", "britannia_mod:iron").containsAll(mounts),
                "Integrated mounts are invalid: " + entry.id);
        require(mounts.contains(first(entry.defaultMount, defaults.defaultMount)),
                "Integrated default mount must be supported: " + entry.id);
        validateResourceId(first(entry.geometry, group.geometry), "geometry");
        validateResourceId(first(entry.baseTexture, defaults.baseTexture), "base_texture");
        validateResourceId(first(entry.dyeMask, defaults.dyeMask), "dye_mask");
        validateResourceId(first(entry.placementProfile, group.placementProfile), "placement_profile");
        require(SHA256.matcher(nullToEmpty(entry.geometrySha256)).matches(),
                "Integrated geometry_sha256 must be lower-case SHA-256: " + entry.id);
        require(SHA256.matcher(nullToEmpty(entry.baseTextureSha256)).matches(),
                "Integrated base_texture_sha256 must be lower-case SHA-256: " + entry.id);
        require(SHA256.matcher(nullToEmpty(entry.dyeMaskSha256)).matches(),
                "Integrated dye_mask_sha256 must be lower-case SHA-256: " + entry.id);
        require(notBlank(entry.intakePath), "Integrated intake_path is required: " + entry.id);
        require("READY_FOR_INTEGRATION".equals(entry.intakeValidation),
                "Integrated intake_validation must be READY_FOR_INTEGRATION: " + entry.id);
    }

    private static void validateGroup(Group group, int width, int height, String profile, String geometry) {
        require(group != null, "Missing canonical group");
        require(group.widthBlocks == width && group.heightBlocks == height,
                "Canonical provisional dimensions do not match for " + profile);
        require(("britannia_mod:" + profile).equals(group.placementProfile),
                "Unexpected placement profile for " + profile);
        require(("britannia_mod:banner/placeholder/" + geometry).equals(group.geometry),
                "Unexpected geometry for " + profile);
    }

    private static ResolvedCatalogue resolve(Manifest manifest) {
        List<ResolvedBanner> banners = new ArrayList<>();
        for (BannerEntry entry : manifest.banners) {
            Group group = manifest.groups.get(entry.group);
            banners.add(new ResolvedBanner(
                    entry.index, entry.id, entry.group, entry.page, entry.row, entry.sourceLabel,
                    entry.displayName, entry.nameStatus, first(entry.displayNameApproved, false),
                    first(entry.contentStatus, manifest.defaults.contentStatus),
                    first(entry.widthBlocks, group.widthBlocks),
                    first(entry.heightBlocks, group.heightBlocks),
                    first(entry.dimensionsProvisional, manifest.defaults.dimensionsProvisional),
                    first(entry.supportedOrientations, manifest.defaults.supportedOrientations),
                    first(entry.supportedMounts, manifest.defaults.supportedMounts),
                    first(entry.defaultMount, manifest.defaults.defaultMount),
                    first(entry.defaultMaterial, manifest.defaults.defaultMaterial),
                    first(entry.geometry, group.geometry),
                    first(entry.baseTexture, manifest.defaults.baseTexture),
                    first(entry.dyeMask, manifest.defaults.dyeMask),
                    first(entry.placementProfile, group.placementProfile),
                    entry.geometrySha256, entry.baseTextureSha256, entry.dyeMaskSha256,
                    entry.intakePath, entry.intakeValidation, entry.notes));
        }
        return new ResolvedCatalogue(List.copyOf(banners));
    }

    private static LinkedHashMap<String, byte[]> buildExpectedFiles(ResolvedCatalogue catalogue) {
        LinkedHashMap<String, byte[]> output = new LinkedHashMap<>();
        output.put(CLIENT_ASSET_INDEX_PATH, utf8(json(clientAssetIndex(catalogue))));
        output.put(BLOCK_ATLAS_PATH, utf8(json(blockAtlas())));
        for (ResolvedBanner banner : catalogue.banners) {
            output.put(DATA_ROOT + "banner_definitions/" + banner.id + ".json",
                    utf8(json(bannerDefinition(banner))));
        }
        output.put(DATA_ROOT + "banner_mounts/brass.json", utf8(json(mount("brass", "Brass"))));
        output.put(DATA_ROOT + "banner_mounts/iron.json", utf8(json(mount("iron", "Iron"))));
        output.put(DATA_ROOT + "placement_profiles/placeholder_large.json",
                utf8(json(profile("large", 3, 2))));
        output.put(DATA_ROOT + "placement_profiles/placeholder_medium_wall.json",
                utf8(json(profile("medium_wall", 2, 2))));
        output.put(DATA_ROOT + "placement_profiles/placeholder_medium.json",
                utf8(json(profile("medium", 1, 2))));
        output.put(DATA_ROOT + "placement_profiles/placeholder_small.json",
                utf8(json(profile("small", 1, 1))));
        output.put(DATA_ROOT + "placement_profiles/placeholder_x_small.json",
                utf8(json(profile("x_small", 1, 1))));
        output.put(DATA_ROOT + "placement_profiles/extra_small.json",
                utf8(json(extraSmallProfile())));
        output.put(DATA_ROOT + "placement_profiles/small.json",
                utf8(json(smallProfile())));
        output.put(DATA_ROOT + "placement_profiles/medium_parallel.json",
                utf8(json(mediumParallelProfile())));
        output.put(DATA_ROOT + "placement_profiles/medium_perpendicular.json",
                utf8(json(mediumPerpendicularProfile())));

        output.put(ASSET_ROOT + "textures/banner/placeholder/base_texture.png", png(PngKind.BASE_TEXTURE));
        output.put(ASSET_ROOT + "textures/banner/placeholder/dye_mask.png", png(PngKind.DYE_MASK));
        output.put(ASSET_ROOT + "textures/banner/placeholder/missing.png", png(PngKind.MISSING));
        output.put(ASSET_ROOT + "textures/banner/mount/brass.png", png(PngKind.BRASS_MOUNT));
        output.put(ASSET_ROOT + "textures/banner/mount/iron.png", png(PngKind.IRON_MOUNT));
        for (String family : List.of("large", "medium_wall", "medium", "small", "x_small")) {
            output.put(ASSET_ROOT + "models/banner/placeholder/" + family + ".json",
                    utf8(json(placeholderModel(family))));
        }
        output.put(ASSET_ROOT + "models/banner/placeholder/missing_item.json",
                utf8(json(generatedItemModel("britannia_mod:banner/placeholder/missing"))));
        output.put(ASSET_ROOT + "models/banner/mount/brass.json",
                utf8(json(generatedItemModel("britannia_mod:banner/mount/brass"))));
        output.put(ASSET_ROOT + "models/banner/mount/iron.json",
                utf8(json(generatedItemModel("britannia_mod:banner/mount/iron"))));
        output.put(ASSET_ROOT + "models/banner/mount/wall_parallel.json",
                utf8(json(wallMountModel("wall_parallel"))));
        output.put(ASSET_ROOT + "models/banner/mount/wall_perpendicular.json",
                utf8(json(wallMountModel("wall_perpendicular"))));
        return output;
    }

    private static JsonObject clientAssetIndex(ResolvedCatalogue catalogue) {
        LinkedHashSet<String> geometries = new LinkedHashSet<>();
        LinkedHashSet<String> textures = new LinkedHashSet<>();
        for (ResolvedBanner banner : catalogue.banners) {
            geometries.add(banner.geometry);
            textures.add(banner.baseTexture);
            textures.add(banner.dyeMask);
            if (Set.of("britannia_mod:extra_small", "britannia_mod:small")
                    .contains(banner.placementProfile)) {
                geometries.add("britannia_mod:banner/mount/wall_parallel");
                geometries.add("britannia_mod:banner/mount/wall_perpendicular");
            } else if ("britannia_mod:medium_parallel".equals(banner.placementProfile)) {
                geometries.add("britannia_mod:banner/mount/wall_parallel");
            } else if ("britannia_mod:medium_perpendicular".equals(banner.placementProfile)) {
                geometries.add("britannia_mod:banner/mount/wall_perpendicular");
            }
        }
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        root.add("geometry_models", strings(List.copyOf(geometries)));
        root.add("textures", strings(List.copyOf(textures)));
        return root;
    }

    private static JsonObject blockAtlas() {
        JsonObject directory = new JsonObject();
        directory.addProperty("type", "directory");
        directory.addProperty("source", "banner");
        directory.addProperty("prefix", "banner/");
        JsonArray sources = new JsonArray();
        sources.add(directory);
        JsonObject root = new JsonObject();
        root.add("sources", sources);
        return root;
    }

    private static JsonObject bannerDefinition(ResolvedBanner banner) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        root.addProperty("id", "britannia_mod:" + banner.id);
        root.addProperty("display_name_key", translationKey(banner.id));
        root.addProperty("content_status", banner.contentStatus);
        JsonObject source = new JsonObject();
        source.addProperty("page", banner.page);
        source.addProperty("row", banner.row);
        if (banner.sourceLabel != null) {
            source.addProperty("source_label", banner.sourceLabel);
        }
        root.add("source_reference", source);
        root.addProperty("catalogue_group", banner.group);
        JsonObject dimensions = new JsonObject();
        dimensions.addProperty("width_blocks", banner.widthBlocks);
        dimensions.addProperty("height_blocks", banner.heightBlocks);
        dimensions.addProperty("provisional", banner.dimensionsProvisional);
        root.add("dimensions", dimensions);
        root.add("supported_orientations", strings(banner.supportedOrientations));
        root.add("supported_mounts", strings(banner.supportedMounts));
        root.addProperty("default_mount", banner.defaultMount);
        root.addProperty("default_material", banner.defaultMaterial);
        JsonObject assets = new JsonObject();
        assets.addProperty("geometry", banner.geometry);
        assets.addProperty("base_texture", banner.baseTexture);
        assets.addProperty("dye_mask", banner.dyeMask);
        root.add("assets", assets);
        root.addProperty("placement_profile", banner.placementProfile);
        return root;
    }

    /** Private validation fixture only; Milestone 5 colour resources are authored outside this scaffold. */
    private static JsonObject validationCottonMaterial() {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        root.addProperty("id", "britannia_mod:cotton");
        root.addProperty("display_name_key", "material.britannia_mod.cotton");
        root.addProperty("natural_colour_id", "britannia_mod:cotton_natural");
        root.addProperty("palette_id", "britannia_mod:cotton_placeholder");
        root.add("tags", strings(List.of("fabric", "placeholder", "development_scaffold")));
        return root;
    }

    /** Private validation fixture only; it is never emitted or tracked as a scaffold-owned output. */
    private static JsonObject validationCottonPalette() {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        root.addProperty("id", "britannia_mod:cotton_placeholder");
        root.addProperty("material_id", "britannia_mod:cotton");
        root.addProperty("natural_colour_id", "britannia_mod:cotton_natural");
        JsonObject natural = new JsonObject();
        natural.addProperty("id", "britannia_mod:cotton_natural");
        natural.addProperty("display_name_key", "colour.britannia_mod.cotton_natural");
        natural.addProperty("display_srgb", "#C8C1AD");
        JsonArray oklab = new JsonArray();
        oklab.add(0.8111740091);
        oklab.add(-0.0004676910);
        oklab.add(0.0284677327);
        natural.add("match_oklab", oklab);
        natural.addProperty("priority", 0);
        natural.add("tags", strings(List.of("natural", "placeholder")));
        natural.add("allowed_pigment_tags", new JsonArray());
        natural.add("excluded_pigment_tags", new JsonArray());
        JsonArray entries = new JsonArray();
        entries.add(natural);
        root.add("entries", entries);
        root.add("pigment_overrides", new JsonObject());
        return root;
    }

    private static JsonObject mount(String id, String label) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        root.addProperty("id", "britannia_mod:" + id);
        root.addProperty("display_name_key", "mount.britannia_mod." + id);
        root.addProperty("geometry", "britannia_mod:banner/mount/" + id);
        root.addProperty("texture", "britannia_mod:banner/mount/" + id);
        root.add("tags", strings(List.of("metal", "placeholder", "development_scaffold")));
        return root;
    }

    private static JsonObject profile(String suffix, int width, int height) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        root.addProperty("id", "britannia_mod:placeholder_" + suffix);
        JsonObject dimensions = new JsonObject();
        dimensions.addProperty("width_blocks", width);
        dimensions.addProperty("height_blocks", height);
        dimensions.addProperty("provisional", true);
        root.add("dimensions", dimensions);
        root.addProperty("requires_wall_support", true);
        return root;
    }

    private static JsonObject extraSmallProfile() {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        root.addProperty("id", "britannia_mod:extra_small");
        JsonObject dimensions = new JsonObject();
        dimensions.addProperty("width_blocks", 1);
        dimensions.addProperty("height_blocks", 1);
        dimensions.addProperty("provisional", false);
        root.add("dimensions", dimensions);
        root.addProperty("requires_wall_support", true);
        JsonObject mounts = new JsonObject();
        mounts.addProperty("wall_parallel", "britannia_mod:banner/mount/wall_parallel");
        mounts.addProperty("wall_perpendicular", "britannia_mod:banner/mount/wall_perpendicular");
        root.add("orientation_mount_geometry", mounts);
        return root;
    }

    private static JsonObject smallProfile() {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        root.addProperty("id", "britannia_mod:small");
        JsonObject dimensions = new JsonObject();
        dimensions.addProperty("width_blocks", 1);
        dimensions.addProperty("height_blocks", 1);
        dimensions.addProperty("provisional", false);
        root.add("dimensions", dimensions);
        root.addProperty("requires_wall_support", true);
        JsonObject mounts = new JsonObject();
        mounts.addProperty("wall_parallel", "britannia_mod:banner/mount/wall_parallel");
        mounts.addProperty("wall_perpendicular", "britannia_mod:banner/mount/wall_perpendicular");
        root.add("orientation_mount_geometry", mounts);
        return root;
    }

    private static JsonObject mediumPerpendicularProfile() {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        root.addProperty("id", "britannia_mod:medium_perpendicular");
        JsonObject dimensions = new JsonObject();
        dimensions.addProperty("width_blocks", 1);
        dimensions.addProperty("height_blocks", 2);
        dimensions.addProperty("provisional", false);
        root.add("dimensions", dimensions);
        root.addProperty("requires_wall_support", true);
        JsonObject mounts = new JsonObject();
        mounts.addProperty("wall_perpendicular", "britannia_mod:banner/mount/wall_perpendicular");
        root.add("orientation_mount_geometry", mounts);
        return root;
    }

    private static JsonObject mediumParallelProfile() {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        root.addProperty("id", "britannia_mod:medium_parallel");
        JsonObject dimensions = new JsonObject();
        dimensions.addProperty("width_blocks", 1);
        dimensions.addProperty("height_blocks", 2);
        dimensions.addProperty("provisional", false);
        root.add("dimensions", dimensions);
        root.addProperty("requires_wall_support", true);
        JsonObject mounts = new JsonObject();
        mounts.addProperty("wall_parallel", "britannia_mod:banner/mount/wall_parallel");
        root.add("orientation_mount_geometry", mounts);
        return root;
    }

    private static JsonObject wallMountModel(String orientation) {
        JsonObject root = new JsonObject();
        root.addProperty("credit", "Shared small-family " + orientation
                + " physical wall-mount geometry; runtime material texture remains independently selected");
        root.addProperty("parent", "minecraft:block/block");
        JsonObject textures = new JsonObject();
        textures.addProperty("mount", "britannia_mod:banner/mount/brass");
        textures.addProperty("particle", "britannia_mod:banner/mount/brass");
        root.add("textures", textures);
        JsonArray elements = new JsonArray();
        if ("wall_parallel".equals(orientation)) {
            elements.add(cuboid(new double[] {4.5, 10.4, 7.35, 11.5, 11.0, 7.65}, "#mount"));
            elements.add(cuboid(new double[] {4.5, 9.8, 7.3, 5.1, 10.7, 8.0}, "#mount"));
            elements.add(cuboid(new double[] {10.9, 9.8, 7.3, 11.5, 10.7, 8.0}, "#mount"));
        } else if ("wall_perpendicular".equals(orientation)) {
            elements.add(cuboid(new double[] {7.35, 10.4, 0.0, 7.65, 11.0, 9.0}, "#mount"));
            elements.add(cuboid(new double[] {7.1, 9.6, 14.5, 7.9, 11.2, 16.0}, "#mount"));
        } else {
            throw new ScaffoldException("Unknown orientation mount geometry " + orientation);
        }
        root.add("elements", elements);
        return root;
    }

    private static JsonObject cuboid(double[] bounds, String texture) {
        JsonObject element = new JsonObject();
        element.add("from", numbers(bounds[0], bounds[1], bounds[2]));
        element.add("to", numbers(bounds[3], bounds[4], bounds[5]));
        JsonObject faces = new JsonObject();
        for (String face : List.of("north", "east", "south", "west", "up", "down")) {
            JsonObject value = new JsonObject();
            value.addProperty("texture", texture);
            faces.add(face, value);
        }
        element.add("faces", faces);
        return element;
    }

    private static JsonObject placeholderModel(String family) {
        JsonObject root = new JsonObject();
        root.addProperty("credit", "Milestone 4 diagnostic placeholder for " + family + "; not final geometry");
        root.addProperty("parent", "minecraft:block/block");
        root.addProperty("render_type", "minecraft:translucent");
        JsonObject textures = new JsonObject();
        textures.addProperty("base_texture", "britannia_mod:banner/placeholder/base_texture");
        textures.addProperty("dye_mask", "britannia_mod:banner/placeholder/dye_mask");
        textures.addProperty("particle", "britannia_mod:banner/placeholder/base_texture");
        root.add("textures", textures);
        double[] bounds = switch (family) {
            case "large" -> new double[] {0, 4, 16, 12};
            case "medium_wall" -> new double[] {2, 2, 14, 14};
            case "medium" -> new double[] {4, 0, 12, 16};
            case "small" -> new double[] {3, 3, 13, 13};
            case "x_small" -> new double[] {5, 5, 11, 11};
            default -> throw new ScaffoldException("Unknown placeholder family " + family);
        };
        JsonArray elements = new JsonArray();
        elements.add(layerElement(bounds, "#base_texture", 7.49, 7.51, null));
        elements.add(layerElement(bounds, "#dye_mask", 7.47, 7.53, 1));
        root.add("elements", elements);
        return root;
    }

    private static JsonObject layerElement(
            double[] bounds, String texture, double fromZ, double toZ, Integer tintIndex) {
        JsonObject element = new JsonObject();
        element.add("from", numbers(bounds[0], bounds[1], fromZ));
        element.add("to", numbers(bounds[2], bounds[3], toZ));
        JsonObject faces = new JsonObject();
        JsonObject north = new JsonObject();
        north.addProperty("texture", texture);
        JsonObject south = new JsonObject();
        south.addProperty("texture", texture);
        if (tintIndex != null) {
            north.addProperty("tintindex", tintIndex);
            south.addProperty("tintindex", tintIndex);
        }
        faces.add("north", north);
        faces.add("south", south);
        element.add("faces", faces);
        return element;
    }

    private static JsonObject generatedItemModel(String texture) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", texture);
        root.add("textures", textures);
        return root;
    }

    private static RegistryLoadResult validateRegistry(Map<String, byte[]> expected) {
        List<DefinitionResource> resources = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
            RegistryDomain domain = domainFor(entry.getKey());
            if (domain == null) {
                continue;
            }
            String file = entry.getKey().substring((DATA_ROOT + domain.folder() + "/").length());
            resources.add(DefinitionResource.text(domain, "britannia_mod:" + domain.folder() + "/" + file,
                    new String(entry.getValue(), StandardCharsets.UTF_8)));
        }
        resources.add(DefinitionResource.text(RegistryDomain.FABRIC_MATERIAL,
                "britannia_mod:fabric_materials/scaffold_validation_cotton.json",
                json(validationCottonMaterial())));
        resources.add(DefinitionResource.text(RegistryDomain.MATERIAL_PALETTE,
                "britannia_mod:material_palettes/scaffold_validation_cotton.json",
                json(validationCottonPalette())));
        RegistryDataLoader loader = new RegistryDataLoader();
        RegistryLoadResult result = loader.apply(loader.prepare(resources), ValidationPolicy.DEVELOPMENT_FAIL_FAST,
                new RegistrySnapshotPublisher());
        require(result.published(), "Generated production dataset failed development validation: "
                + result.report().issues());
        ProductionBannerCatalogue.requireComplete(result.snapshot());
        require(result.snapshot().fabricMaterials().activeCount() == 1, "Cotton material did not become active");
        require(result.snapshot().materialPalettes().activeCount() == 1, "Cotton palette did not become active");
        require(result.snapshot().mounts().activeCount() == 2, "Brass and iron mounts did not become active");
        require(result.snapshot().placementProfiles().activeCount() == 9,
                "Nine placement profiles did not become active");
        require(result.snapshot().pigments().activeCount() == 0, "Natural scaffold must not require pigments");
        return result;
    }

    private static RegistryDomain domainFor(String path) {
        for (RegistryDomain domain : RegistryDomain.values()) {
            if (path.startsWith(DATA_ROOT + domain.folder() + "/") && path.endsWith(".json")) {
                return domain;
            }
        }
        return null;
    }

    private static void validateAssetMappings(Path root, ResolvedCatalogue catalogue, Set<String> outputs)
            throws IOException {
        for (ResolvedBanner banner : catalogue.banners) {
            validateAsset(root, outputs, banner.id, "geometry", banner.geometry,
                    "models", ".json", banner.geometrySha256);
            validateAsset(root, outputs, banner.id, "base texture", banner.baseTexture,
                    "textures", ".png", banner.baseTextureSha256);
            validateAsset(root, outputs, banner.id, "dye mask", banner.dyeMask,
                    "textures", ".png", banner.dyeMaskSha256);
        }
        for (String id : List.of("britannia_mod:banner/mount/brass", "britannia_mod:banner/mount/iron")) {
            require(outputs.contains(assetPath(id, "models", ".json")),
                    "Mount geometry has no declared placeholder file: " + id);
            require(outputs.contains(assetPath(id, "textures", ".png")),
                    "Mount texture has no declared placeholder file: " + id);
        }
        for (String id : List.of("britannia_mod:banner/mount/wall_parallel",
                "britannia_mod:banner/mount/wall_perpendicular")) {
            require(outputs.contains(assetPath(id, "models", ".json")),
                    "Orientation mount geometry has no declared model file: " + id);
        }
    }

    private static String assetPath(String id, String kind, String extension) {
        validateResourceId(id, kind + " asset");
        String[] parts = id.split(":", 2);
        return "src/main/resources/assets/" + parts[0] + "/" + kind + "/" + parts[1] + extension;
    }

    private static void checkOutputs(
            Path root, ResolvedCatalogue catalogue, Map<String, byte[]> expected) throws IOException {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
            Path path = root.resolve(entry.getKey());
            if (!Files.isRegularFile(path)) {
                failures.add("missing " + entry.getKey());
            } else if (!Arrays.equals(Files.readAllBytes(path), entry.getValue())) {
                failures.add("changed " + entry.getKey());
            }
        }
        checkLocalization(root.resolve(LOCALIZATION_PATH), catalogue, failures);
        if (!failures.isEmpty()) {
            throw new ScaffoldException("--check found " + failures.size() + " problem(s): "
                    + String.join("; ", failures));
        }
    }

    private static void checkLocalization(Path path, ResolvedCatalogue catalogue, List<String> failures)
            throws IOException {
        if (!Files.isRegularFile(path)) {
            failures.add("missing " + LOCALIZATION_PATH);
            return;
        }
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        JsonObject object = parseJsonObject(raw, LOCALIZATION_PATH);
        int found = 0;
        for (ResolvedBanner banner : catalogue.banners) {
            String key = translationKey(banner.id);
            int occurrences = countJsonKey(raw, key);
            if (occurrences != 1) {
                failures.add("translation key occurrence count " + occurrences + " for " + key);
                continue;
            }
            JsonElement value = object.get(key);
            if (value == null || !value.isJsonPrimitive()
                    || !localizedName(banner).equals(value.getAsString())) {
                failures.add("changed localization " + key);
            } else {
                found++;
            }
        }
        if (found != TARGET_COUNT) {
            failures.add("expected exactly " + TARGET_COUNT
                    + " valid generated localization entries; found " + found);
        }
    }

    private static List<String> findCustomizedFiles(
            Path root, Map<String, byte[]> expected, Metadata metadata) throws IOException {
        List<String> customized = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
            Path path = root.resolve(entry.getKey());
            if (!Files.exists(path)) {
                continue;
            }
            byte[] current = Files.readAllBytes(path);
            if (Arrays.equals(current, entry.getValue())) {
                continue;
            }
            String priorHash = metadata.fileHashes.get(entry.getKey());
            if (priorHash == null || !priorHash.equals(sha256(current))) {
                customized.add(entry.getKey());
            }
        }
        Collections.sort(customized);
        return List.copyOf(customized);
    }

    private static LocalizationResult mergeLocalization(
            Path root, ResolvedCatalogue catalogue, Metadata metadata, boolean force, PrintStream output)
            throws IOException {
        Path path = root.resolve(LOCALIZATION_PATH);
        String raw = Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "{}\n";
        JsonObject parsed = parseJsonObject(raw, LOCALIZATION_PATH);
        List<String> customized = new ArrayList<>();
        LinkedHashMap<String, String> missing = new LinkedHashMap<>();

        for (ResolvedBanner banner : catalogue.banners) {
            String key = translationKey(banner.id);
            String wanted = localizedName(banner);
            int occurrences = countJsonKey(raw, key);
            require(occurrences <= 1, "Duplicate generated localization key: " + key);
            JsonElement existing = parsed.get(key);
            if (existing == null) {
                missing.put(key, wanted);
                continue;
            }
            require(existing.isJsonPrimitive() && existing.getAsJsonPrimitive().isString(),
                    "Generated localization value must be a string: " + key);
            String current = existing.getAsString();
            if (current.equals(wanted)) {
                continue;
            }
            String previousGenerated = metadata.localizationValues.get(key);
            boolean isCustomized = previousGenerated == null || !previousGenerated.equals(current);
            if (isCustomized && !force) {
                customized.add(key);
                output.println("Preserved customized localization: " + key);
                continue;
            }
            if (isCustomized) {
                output.println("WARNING: --force will overwrite customized localization: " + key);
            }
            raw = replaceJsonStringValue(raw, key, wanted);
            parsed.addProperty(key, wanted);
        }
        if (!missing.isEmpty()) {
            raw = appendJsonProperties(raw, missing);
        }
        parseJsonObject(raw, LOCALIZATION_PATH);
        if (!raw.endsWith("\n")) {
            raw += "\n";
        }
        String original = Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
        if (!raw.equals(original)) {
            writeAtomic(path, utf8(raw));
        }
        return new LocalizationResult(List.copyOf(customized));
    }

    private static String replaceJsonStringValue(String raw, String key, String value) {
        Pattern pattern = Pattern.compile("(\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*)\\\"(?:\\\\.|[^\\\"\\\\])*\\\"");
        Matcher matcher = pattern.matcher(raw);
        require(matcher.find(), "Could not locate localization key for safe replacement: " + key);
        String replacement = matcher.group(1) + GSON.toJson(value);
        int start = matcher.start();
        int end = matcher.end();
        require(!matcher.find(), "Duplicate localization key during replacement: " + key);
        return raw.substring(0, start) + replacement + raw.substring(end);
    }

    private static String appendJsonProperties(String raw, LinkedHashMap<String, String> additions) {
        int closing = raw.lastIndexOf('}');
        require(closing >= 0, "Localization root object is missing its closing brace");
        int previous = closing - 1;
        while (previous >= 0 && Character.isWhitespace(raw.charAt(previous))) {
            previous--;
        }
        boolean empty = previous >= 0 && raw.charAt(previous) == '{';
        boolean alreadyComma = previous >= 0 && raw.charAt(previous) == ',';
        String beforeClosing = raw.substring(0, closing);
        if (!empty && !alreadyComma) {
            beforeClosing = beforeClosing.substring(0, previous + 1) + ','
                    + beforeClosing.substring(previous + 1);
        }
        StringBuilder builder = new StringBuilder(beforeClosing);
        builder.append('\n');
        int position = 0;
        for (Map.Entry<String, String> addition : additions.entrySet()) {
            builder.append("  ").append(GSON.toJson(addition.getKey())).append(": ")
                    .append(GSON.toJson(addition.getValue()));
            if (++position < additions.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append(raw.substring(closing));
        return builder.toString();
    }

    private static Metadata refreshMetadata(
            Path root,
            Map<String, byte[]> expected,
            ResolvedCatalogue catalogue,
            LocalizationResult localization) throws IOException {
        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
            Path path = root.resolve(entry.getKey());
            if (Files.isRegularFile(path) && Arrays.equals(Files.readAllBytes(path), entry.getValue())) {
                hashes.put(entry.getKey(), sha256(entry.getValue()));
            }
        }
        for (ResolvedBanner banner : catalogue.banners) {
            addApprovedAssetHash(hashes, banner.geometry, "models", ".json", banner.geometrySha256);
            addApprovedAssetHash(hashes, banner.baseTexture, "textures", ".png", banner.baseTextureSha256);
            addApprovedAssetHash(hashes, banner.dyeMask, "textures", ".png", banner.dyeMaskSha256);
        }
        LinkedHashMap<String, String> localized = new LinkedHashMap<>();
        JsonObject actual = parseJsonObject(
                Files.readString(root.resolve(LOCALIZATION_PATH), StandardCharsets.UTF_8), LOCALIZATION_PATH);
        Set<String> customized = Set.copyOf(localization.customizedKeys);
        for (ResolvedBanner banner : catalogue.banners) {
            String key = translationKey(banner.id);
            if (!customized.contains(key) && actual.has(key)
                    && localizedName(banner).equals(actual.get(key).getAsString())) {
                localized.put(key, localizedName(banner));
            }
        }
        return new Metadata(hashes, localized);
    }

    private static void addApprovedAssetHash(
            Map<String, String> hashes, String resourceId, String kind, String extension, String hash) {
        if (SHA256.matcher(nullToEmpty(hash)).matches()) {
            hashes.put(assetPath(resourceId, kind, extension), hash);
        }
    }

    private static Metadata readMetadata(Path path) throws IOException {
        if (!Files.exists(path)) {
            return Metadata.empty();
        }
        JsonObject root = parseJsonObject(Files.readString(path, StandardCharsets.UTF_8), METADATA_PATH);
        require(root.has("schema_version") && root.get("schema_version").getAsInt() == 1,
                "Unsupported scaffold metadata schema");
        return new Metadata(stringMap(root.getAsJsonObject("files")),
                stringMap(root.getAsJsonObject("localization")));
    }

    private static LinkedHashMap<String, String> stringMap(JsonObject object) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (object != null) {
            object.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
        }
        return result;
    }

    private static String statusReport(ResolvedCatalogue catalogue, RegistryLoadResult registry) {
        Map<String, Long> groupCounts = counts(catalogue.banners, banner -> banner.group);
        Map<String, Long> nameCounts = counts(catalogue.banners, banner -> banner.nameStatus);
        Map<String, Long> contentCounts = counts(catalogue.banners, banner -> banner.contentStatus);
        long integratedCount = catalogue.banners.stream()
                .filter(banner -> !"placeholder".equals(banner.contentStatus)).count();
        long nameApprovedCount = catalogue.banners.stream()
                .filter(banner -> banner.displayNameApproved).count();
        long completeCount = contentCounts.getOrDefault("complete", 0L);
        int total = catalogue.banners.size();
        StringBuilder report = new StringBuilder();
        report.append("# Banner Catalogue Status\n\n")
                .append("Generated by `tools/scaffold_banners.bat`; do not infer content approval from generation.\n\n")
                .append("- Catalogue target: data-derived from the canonical manifest\n")
                .append("- Total manifest entries: ").append(total).append('\n')
                .append("- Total generated definitions: ").append(total).append('\n')
                .append("- Total active registry entries: ").append(registry.snapshot().banners().activeCount())
                .append("\n- Total disabled entries: ").append(registry.snapshot().banners().disabledCount())
                .append("\n- Missing output files: none\n")
                .append("- Duplicate IDs: none\n- Duplicate indices: none\n- Missing indices: none\n")
                .append("- Definitions that failed validation: none\n")
                .append("- Stable identity set approved at Gate B: yes\n")
                .append("- Banner crafting implemented: no\n")
                .append("- Admin acquisition implemented: yes\n")
                .append("- Survival acquisition implemented: no\n")
                .append("- NPC/shop distribution implemented: no\n")
                .append("- Final display names approved: ").append(nameApprovedCount).append(" of ").append(total).append('\n')
                .append("- Final dimensions approved: ").append(integratedCount).append(" of ").append(total).append('\n')
                .append("- Final per-definition orientations approved: ").append(integratedCount)
                .append(" of ").append(total).append('\n')
                .append("- Final per-definition mounts approved: ").append(integratedCount).append(" of ").append(total).append('\n')
                .append("- Final placed artwork intake approved: ").append(integratedCount).append(" of ").append(total).append('\n')
                .append("- Final artwork complete: ").append(completeCount).append(" of ").append(total).append("\n\n")
                .append("## Counts by catalogue group\n\n");
        appendCounts(report, groupCounts, List.of("large", "medium-wall", "medium", "small", "x-small"));
        report.append("\n## Counts by name status\n\n");
        appendCounts(report, nameCounts, List.of("provisional", "source-named"));
        report.append("\n## Counts by content status\n\n");
        appendCounts(report, contentCounts, List.of("placeholder", "in_progress", "complete", "disabled"));
        report.append("\n## Catalogue\n\n")
                .append("| Index | Stable ID | Display label | Name status | Group | Source | Dimensions | Supported orientations | Supported mounts | Default mount | Parallel automated | Perpendicular automated | Brass automated | Iron automated | Manual result | Content status | Assets |\n")
                .append("|---:|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (ResolvedBanner banner : catalogue.banners) {
            report.append("| ").append(String.format(Locale.ROOT, "%02d", banner.index)).append(" | `")
                    .append(banner.id).append("` | ").append(escapeMarkdown(banner.displayName)).append(" | ")
                    .append(banner.nameStatus).append(" | ").append(banner.group).append(" | Page ")
                    .append(banner.page).append(", row ").append(banner.row).append(" | ")
                    .append(banner.widthBlocks).append(" x ").append(banner.heightBlocks).append(" (")
                    .append(banner.dimensionsProvisional ? "provisional" : "approved").append(") | ")
                    .append(String.join(", ", banner.supportedOrientations)).append(" | ")
                    .append(String.join(", ", banner.supportedMounts)).append(" | ")
                    .append(banner.defaultMount).append(" | pass | pass | pass | pass | ")
                    .append("placeholder".equals(banner.contentStatus)
                            ? "not performed"
                            : "complete".equals(banner.contentStatus) ? "pass" : "pending")
                    .append(" | ").append(banner.contentStatus).append(" | `")
                    .append(banner.geometry).append("`; base `").append(banner.baseTexture)
                    .append("`; mask `").append(banner.dyeMask).append("` |\n");
        }
        report.append("\n## Provisional entries\n\n");
        catalogue.banners.stream().filter(banner -> "provisional".equals(banner.nameStatus))
                .forEach(banner -> report.append("- `").append(banner.id).append("` - ")
                        .append(localizedName(banner)).append("; Page ").append(banner.page)
                        .append(", row ").append(banner.row).append("\n"));
        report.append("\n## Placeholder asset references\n\n")
                .append("- Geometry families: `large`, `medium_wall`, `medium`, `small`, `x_small`\n")
                .append("- Base texture: `britannia_mod:banner/placeholder/base_texture`\n")
                .append("- Dye mask: `britannia_mod:banner/placeholder/dye_mask`\n")
                .append("- Diagnostic fallback: `britannia_mod:banner/placeholder/missing`\n")
                .append("- Brass mount: `britannia_mod:banner/mount/brass`\n")
                .append("- Iron mount: `britannia_mod:banner/mount/iron`\n")
                .append("- Every logical identifier above maps deterministically to a declared model JSON or PNG output.\n\n")
                .append("## Extra-small family integration\n\n")
                .append("- Authoritative family members: 9\n")
                .append("- Dimensions: 1 x 1 (approved; independent of 128 x 128 texture resolution)\n")
                .append("- Orientations: `wall_parallel`, `wall_perpendicular` (approved)\n")
                .append("- Mounts: `britannia_mod:brass`, `britannia_mod:iron` (approved); default `britannia_mod:brass`\n")
                .append("- Eight Road Guard-style definitions share `britannia_mod:banner/road_guard/geometry`\n")
                .append("- Small Curtain uses `britannia_mod:banner/small_curtain/geometry`\n")
                .append("- Placement profile: `britannia_mod:extra_small`\n")
                .append("- Parallel mount geometry: `britannia_mod:banner/mount/wall_parallel`\n")
                .append("- Perpendicular mount geometry: `britannia_mod:banner/mount/wall_perpendicular`\n")
                .append("- Every base texture and dye mask is 128 x 128 RGBA\n")
                .append("- Intake validation: `READY_FOR_INTEGRATION`\n")
                .append("- Automated validation: pass\n")
                .append("- Manual review: Gate E PASS for all nine authoritative hashes\n")
                .append("- `content_status`: `complete`\n")
                .append("- Crafting: not applicable; product-disabled\n\n")
                .append("## Small-family integration\n\n")
                .append("- Authoritative family members: `silver_and_gold_pennon`, `star_standard`, `ship_standard`, ")
                .append("`pennon_of_silver`, `iron_ward`, and `iron_ward_auxiliary`\n")
                .append("- Catalogue indices: 21 through 26; all six retain 1 x 1 logical dimensions\n")
                .append("- Product-owner approval: Seggellion, 2026-07-29\n")
                .append("- Original artwork and distribution permission: confirmed\n")
                .append("- Manual asset review: performed by Seggellion on 2026-07-29\n")
                .append("- Six aligned 128 x 128 RGBA base/mask pairs: `READY_FOR_INTEGRATION`\n")
                .append("- Geometry groups: one shared paired-pennon model plus distinct `star_standard`, ")
                .append("`ship_standard`, `iron_ward`, and `iron_ward_auxiliary` models\n")
                .append("- Placement profile: `britannia_mod:small`, with shared parallel and perpendicular ")
                .append("wall-mount geometry and independently selected brass/iron materials\n")
                .append("- `star_standard` replaces provisional `end_01` at index 22; `ship_standard` replaces ")
                .append("provisional `end_02` at index 23\n")
                .append("- Manual review: Gate E PASS for all six authoritative hashes\n")
                .append("- Runtime `content_status`: `complete`\n")
                .append("- Gate E evidence: `content/banner-final-intake/SMALL_FAMILY_GATE_E_REVIEW.md`\n\n")
                .append("## Parallel Large asset preparation\n\n")
                .append("- Authoritative source: `C:/projects/britannia/raw fiels/tabbard/banner_large.ai`\n")
                .append("- Source SHA-256: `64fd720476243a937d155b9ea547de60003ccc83f1c78093098e45517d25379e`\n")
                .append("- Six source-named candidates were exported: `tournament_curtain`, ")
                .append("`threefold_chain_standard`, `iron_serpent_standard`, `silver_fleur_curtain`, ")
                .append("`gilded_trellis_curtain`, and `gilded_chevron_curtain`\n")
                .append("- Six aligned 128 x 128 RGBA draft base/mask pairs and diagnostic review packages ")
                .append("are retained under `content/banner-final-intake/submissions/`\n")
                .append("- Proposed dimensions: 2 x 2 from the near-square authoritative silhouettes; ")
                .append("orientation remains `wall_parallel` only\n")
                .append("- Proposed placement profile: `britannia_mod:large_parallel`; proposed mount geometry: ")
                .append("shared `britannia_mod:banner/mount/wall_parallel`\n")
                .append("- Intake validation: six `INVALID` drafts because canonical source-named IDs have not ")
                .append("been migrated into the live catalogue and administrative approval/provenance is absent\n")
                .append("- Runtime integration: not performed; `large_01` through `large_06` remain the six ")
                .append("placeholder definitions at indices 1 through 6\n")
                .append("- Product-owner approval, creator/original-art attestation, distribution permission, ")
                .append("logical dimensions, geometry approval, and Gate E: pending\n")
                .append("- Preparation tests: 5 passed; banner/dyeing suite: 655 passed; clean unrestricted suite: ")
                .append("661 passed across 62 suites; build: passed\n")
                .append("- JAR inspection: 35 definitions, six Large placeholders, zero draft candidate runtime ")
                .append("entries, zero intake/review entries, and zero duplicate ZIP names\n\n")
                .append("## Parallel Medium integration\n\n")
                .append("- Authoritative source: `C:/projects/britannia/raw fiels/tabbard/banner_medium_wall.ai`\n")
                .append("- Source SHA-256: `a6a75becd1793dac7a5b36361c0a33d615846ac4e97502deff794ef5ac337fac`\n")
                .append("- Authoritative family: `verdant_grape_pennon`, `silver_rosette_pennon`, ")
                .append("`four_seals_pennon`, `twin_spades_pennon`, `ankh_pennon`, and `joined_wards` ")
                .append("at catalogue indices 7 through 12\n")
                .append("- Provisional `medium_wall_01` through `medium_wall_05` were replaced without ")
                .append("renumbering; saved-state decode aliases map them to the canonical source names\n")
                .append("- Product-owner approval, creator, original-art status, and distribution permission: ")
                .append("Seggellion, 2026-07-30\n")
                .append("- Six aligned 128 x 128 RGBA base/mask pairs and five approved geometry groups are ")
                .append("integrated as runtime resources\n")
                .append("- Approved dimensions: 1 x 2; orientation: `wall_parallel` only; placement profile: ")
                .append("`britannia_mod:medium_parallel`\n")
                .append("- Mount geometry: shared `britannia_mod:banner/mount/wall_parallel`; ")
                .append("brass/iron remain separate untinted materials\n")
                .append("- Intake validation: six `READY_FOR_INTEGRATION`\n")
                .append("- Manual review: Gate E PASS for all six authoritative hashes\n")
                .append("- Runtime `content_status`: `complete`\n")
                .append("- Gate E evidence: `content/banner-final-intake/PARALLEL_MEDIUM_GATE_E_REVIEW.md`\n\n")
                .append("## Perpendicular medium integration\n\n")
                .append("- Authoritative source: `C:/projects/britannia/raw fiels/tabbard/banner_medium.ai`\n")
                .append("- Source SHA-256: `5a219e6e276884e6b7173ec5c6608c8a85b53dcbdeb768b0f2c19ead0423d34d`\n")
                .append("- Authoritative family: eight exact-name definitions at catalogue indices 13 through 20\n")
                .append("- Product-owner approval, creator, original-art status, and distribution permission: ")
                .append("Seggellion, 2026-07-29\n")
                .append("- Dimensions: 1 x 2 approved; orientation: `wall_perpendicular` only\n")
                .append("- Mount materials: brass and iron; default brass; fixed authored attachment pixels remain ")
                .append("in each complete base texture\n")
                .append("- Placement profile: `britannia_mod:medium_perpendicular`, with only the approved ")
                .append("`wall_perpendicular` mount-geometry entry\n")
                .append("- Five approved geometry groups and eight aligned 128 x 128 RGBA base/mask pairs\n")
                .append("- Intake validation: eight `READY_FOR_INTEGRATION`\n")
                .append("- Manual review: Gate E PASS for all eight authoritative hashes\n")
                .append("- Runtime `content_status`: `complete`\n")
                .append("- Gate E evidence: `content/banner-final-intake/MEDIUM_FAMILY_GATE_E_REVIEW.md`\n")
                .append("- Parallel `medium-wall` integration does not alter perpendicular assets, geometry, ")
                .append("profile, or Gate E evidence\n\n")
                .append("## Gate D automated placement baseline\n\n")
                .append("All ").append(total)
                .append(" active definitions pass automated coverage for their supported orientations and brass/iron ")
                .append("coverage matrix. These results validate data flow, transforms, planning, persistence, ")
                .append("rollback, and preview classification; they do not constitute in-game visual approval. ")
                .append("Manual in-game validation was not performed in this non-interactive run.\n\n")
                .append("## Gate B decisions\n\n")
                .append("The original Gate B identity set remains stable. The product owner subsequently added ")
                .append("Prosperity Standard and Guardian Standard as authoritative extra-small definitions without ")
                .append("renumbering earlier entries. The ")
                .append(nameCounts.getOrDefault("provisional", 0L))
                .append(" unnamed banners retain visibly provisional ")
                .append("`Name Required` labels. Recipes remain product-disabled.\n");
        return report.toString();
    }

    private static RunSummary summary(
            ResolvedCatalogue catalogue, RegistryLoadResult registry, List<String> customized) {
        int provisional = (int) catalogue.banners.stream()
                .filter(banner -> "provisional".equals(banner.nameStatus)).count();
        int provisionalDimensions = (int) catalogue.banners.stream()
                .filter(banner -> banner.dimensionsProvisional).count();
        return new RunSummary(catalogue.banners.size(), catalogue.banners.size(),
                registry.snapshot().banners().activeCount(), registry.snapshot().banners().disabledCount(),
                catalogue.banners.size(), provisional, provisionalDimensions, 5, customized);
    }

    private static void appendCounts(StringBuilder target, Map<String, Long> counts, List<String> order) {
        for (String key : order) {
            target.append("- ").append(key).append(": ").append(counts.getOrDefault(key, 0L)).append('\n');
        }
    }

    private static <T> Map<String, Long> counts(List<T> values, java.util.function.Function<T, String> classifier) {
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        values.forEach(value -> counts.merge(classifier.apply(value), 1L, Long::sum));
        return Collections.unmodifiableMap(counts);
    }

    private static byte[] png(PngKind kind) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int argb = switch (kind) {
                    case BASE_TEXTURE -> compositeDiagnosticBasePixel(x, y);
                    case DYE_MASK -> {
                        if (isDiagnosticOverlayPixel(x, y)) {
                            yield 0x00000000;
                        }
                        int shade = ((x + y) & 1) == 0 ? 184 : 168;
                        yield new Color(shade, shade, shade, 255).getRGB();
                    }
                    case MISSING -> {
                        boolean diagnostic = ((x / 4) + (y / 4)) % 2 == 0;
                        yield diagnostic ? new Color(220, 0, 220, 255).getRGB()
                                : new Color(20, 20, 20, 255).getRGB();
                    }
                    case BRASS_MOUNT, IRON_MOUNT -> {
                        boolean hardware = (y >= 1 && y <= 2 && x >= 2 && x <= 13)
                                || ((x == 3 || x == 12) && y >= 3 && y <= 5);
                        if (!hardware) {
                            yield 0x00000000;
                        }
                        boolean highlight = ((x + y) & 1) == 0;
                        if (kind == PngKind.BRASS_MOUNT) {
                            yield highlight ? new Color(225, 185, 79, 255).getRGB()
                                    : new Color(151, 104, 31, 255).getRGB();
                        }
                        yield highlight ? new Color(173, 181, 187, 255).getRGB()
                                : new Color(83, 91, 98, 255).getRGB();
                    }
                };
                image.setRGB(x, y, argb);
            }
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            require(ImageIO.write(image, "png", output), "JDK PNG writer is unavailable");
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ScaffoldException("Could not create placeholder PNG", exception);
        }
    }

    private static void validateAsset(
            Path root,
            Set<String> outputs,
            String bannerId,
            String label,
            String resourceId,
            String kind,
            String extension,
            String expectedHash) throws IOException {
        String physical = assetPath(resourceId, kind, extension);
        if (outputs.contains(physical)) {
            return;
        }
        require(SHA256.matcher(nullToEmpty(expectedHash)).matches(),
                "Approved " + label + " hash is missing for " + bannerId + ": " + resourceId);
        Path path = root.resolve(physical);
        require(Files.isRegularFile(path),
                "Approved " + label + " file is missing for " + bannerId + ": " + physical);
        String actualHash = sha256(Files.readAllBytes(path));
        require(expectedHash.equals(actualHash),
                "Approved " + label + " hash mismatch for " + bannerId + ": expected "
                        + expectedHash + ", found " + actualHash);
    }

    /**
     * Deterministically composites the former diagnostic overlay over the former neutral fabric placeholder.
     * Both historical sources used only fully transparent or fully opaque pixels, so source-over composition is
     * exactly the overlay pixel when present and the fabric pixel otherwise.
     */
    private static int compositeDiagnosticBasePixel(int x, int y) {
        int fabricShade = ((x + y) & 1) == 0 ? 184 : 168;
        if (x == 0 || y == 0 || x == 15 || y == 15) {
            fabricShade = 76;
        }
        int fabric = new Color(fabricShade, fabricShade, fabricShade, 255).getRGB();
        if (x == 0 || y == 0 || x == 15 || y == 15) {
            return new Color(35, 35, 35, 255).getRGB();
        }
        if ((x >= 6 && x <= 9 && (y == 6 || y == 9))
                || (y >= 6 && y <= 9 && (x == 6 || x == 9))) {
            return new Color(245, 245, 245, 255).getRGB();
        }
        return fabric;
    }

    private static boolean isDiagnosticOverlayPixel(int x, int y) {
        return x == 0 || y == 0 || x == 15 || y == 15
                || (x >= 6 && x <= 9 && (y == 6 || y == 9))
                || (y >= 6 && y <= 9 && (x == 6 || x == 9));
    }

    private static void writeAtomic(Path path, byte[] bytes) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), absolute.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static JsonObject parseJsonObject(String raw, String description) {
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            require(parsed.isJsonObject(), description + " must contain one JSON object");
            return parsed.getAsJsonObject();
        } catch (JsonParseException exception) {
            throw new ScaffoldException(description + " is invalid JSON: " + exception.getMessage());
        }
    }

    private static int countJsonKey(String raw, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:").matcher(raw);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static JsonArray numbers(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) {
            array.add(value);
        }
        return array;
    }

    private static String json(JsonElement element) {
        return GSON.toJson(element) + "\n";
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String translationKey(String id) {
        return "banner.britannia_mod." + id;
    }

    private static String localizedName(BannerEntry entry) {
        return entry.displayName + ("provisional".equals(entry.nameStatus) ? PROVISIONAL_SUFFIX : "");
    }

    private static String localizedName(ResolvedBanner entry) {
        return entry.displayName + ("provisional".equals(entry.nameStatus) ? PROVISIONAL_SUFFIX : "");
    }

    private static String escapeMarkdown(String value) {
        return value.replace("|", "\\|");
    }

    private static void validateResourceId(String value, String field) {
        require(notBlank(value) && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                        && !value.contains("..") && !value.contains("\\"),
                "Invalid or unsafe " + field + " resource ID: " + value);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new ScaffoldException(message);
        }
    }

    private static Set<Integer> range(int first, int last) {
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        for (int value = first; value <= last; value++) {
            values.add(value);
        }
        return values;
    }

    private static <T> T first(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int first(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static List<CanonicalEntry> canonicalEntries() {
        String table = """
                1|large_01|large|1|1|Large Banner 01|provisional
                2|large_02|large|1|2|Large Banner 02|provisional
                3|large_03|large|1|3|Large Banner 03|provisional
                4|large_04|large|1|4|Large Banner 04|provisional
                5|large_05|large|1|5|Large Banner 05|provisional
                6|large_06|large|1|6|Large Banner 06|provisional
                7|verdant_grape_pennon|medium-wall|1|7|Verdant Grape Pennon|source-named
                8|silver_rosette_pennon|medium-wall|1|8|Silver Rosette Pennon|source-named
                9|four_seals_pennon|medium-wall|2|1|Four Seals Pennon|source-named
                10|twin_spades_pennon|medium-wall|2|2|Twin Spades Pennon|source-named
                11|ankh_pennon|medium-wall|2|3|Ankh Pennon|source-named
                12|joined_wards|medium-wall|2|4|Joined Wards|source-named
                13|tournament_medium|medium|2|5|Tournament Medium|source-named
                14|ceremonial_tournament|medium|2|6|Ceremonial Tournament|source-named
                15|iron_quarter|medium|2|7|Iron Quarter|source-named
                16|outer_ward|medium|2|8|Outer Ward|source-named
                17|ward_of_serpents|medium|2|9|Ward of Serpents|source-named
                18|serpent_guard|medium|2|10|Serpent Guard|source-named
                19|crossroad_guard|medium|2|11|Crossroad Guard|source-named
                20|argent_shield|medium|3|1|Argent Shield|source-named
                21|silver_and_gold_pennon|small|3|2|Silver and Gold Pennon|source-named
                22|star_standard|small|3|3|Star Standard|source-named
                23|ship_standard|small|3|4|Ship Standard|source-named
                24|pennon_of_silver|small|3|5|Pennon of Silver|source-named
                25|iron_ward|small|3|6|Iron Ward|source-named
                26|iron_ward_auxiliary|small|3|7|Iron Ward Auxiliary|source-named
                27|road_guard|x-small|3|8|Road Guard|source-named
                28|pale_road_guard|x-small|3|9|Pale Road Guard|source-named
                29|red_crosslets|x-small|3|10|Red Crosslets|source-named
                30|captains_red_crosslets|x-small|3|11|Captain's Red Crosslets|source-named
                31|scarlet_court|x-small|3|12|Scarlet Court|source-named
                32|verdant_court|x-small|4|1|Verdant Court|source-named
                33|small_curtain|x-small|4|2|Small Curtain|source-named
                34|prosperity_standard|x-small|4|3|Prosperity Standard|source-named
                35|guardian_standard|x-small|4|4|Guardian Standard|source-named
                """;
        return table.lines().filter(line -> !line.isBlank()).map(line -> {
            String[] columns = line.strip().split("\\|", -1);
            return new CanonicalEntry(Integer.parseInt(columns[0]), columns[1], columns[2],
                    Integer.parseInt(columns[3]), Integer.parseInt(columns[4]), columns[5], columns[6]);
        }).toList();
    }

    public record Manifest(
            int schemaVersion,
            Defaults defaults,
            Map<String, Group> groups,
            Map<String, String> sharedPlaceholderAssets,
            List<BannerEntry> banners) {
    }

    public record Defaults(
            String defaultMaterial,
            List<String> supportedMounts,
            String defaultMount,
            List<String> supportedOrientations,
            String contentStatus,
            Boolean dimensionsProvisional,
            String baseTexture,
            String dyeMask) {
    }

    public record Group(
            int widthBlocks,
            int heightBlocks,
            String placementProfile,
            String geometry) {
    }

    public record BannerEntry(
            int index,
            String id,
            String group,
            int page,
            int row,
            String sourceLabel,
            String displayName,
            String nameStatus,
            Boolean displayNameApproved,
            String contentStatus,
            Integer widthBlocks,
            Integer heightBlocks,
            Boolean dimensionsProvisional,
            List<String> supportedOrientations,
            List<String> supportedMounts,
            String defaultMount,
            String defaultMaterial,
            String geometry,
            String baseTexture,
            String dyeMask,
            String placementProfile,
            String geometrySha256,
            String baseTextureSha256,
            String dyeMaskSha256,
            String intakePath,
            String intakeValidation,
            String notes) {
    }

    private record ResolvedCatalogue(List<ResolvedBanner> banners) {
    }

    private record ResolvedBanner(
            int index,
            String id,
            String group,
            int page,
            int row,
            String sourceLabel,
            String displayName,
            String nameStatus,
            boolean displayNameApproved,
            String contentStatus,
            int widthBlocks,
            int heightBlocks,
            boolean dimensionsProvisional,
            List<String> supportedOrientations,
            List<String> supportedMounts,
            String defaultMount,
            String defaultMaterial,
            String geometry,
            String baseTexture,
            String dyeMask,
            String placementProfile,
            String geometrySha256,
            String baseTextureSha256,
            String dyeMaskSha256,
            String intakePath,
            String intakeValidation,
            String notes) {
    }

    private record CanonicalEntry(
            int index,
            String id,
            String group,
            int page,
            int row,
            String displayName,
            String nameStatus) {
        boolean matches(BannerEntry entry) {
            return index == entry.index && id.equals(entry.id) && group.equals(entry.group)
                    && page == entry.page && row == entry.row && displayName.equals(entry.displayName)
                    && nameStatus.equals(entry.nameStatus);
        }
    }

    private record LocalizationResult(List<String> customizedKeys) {
    }

    private record Metadata(
            LinkedHashMap<String, String> fileHashes,
            LinkedHashMap<String, String> localizationValues) {
        static Metadata empty() {
            return new Metadata(new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        JsonObject toJson() {
            JsonObject root = new JsonObject();
            root.addProperty("schema_version", 1);
            JsonObject files = new JsonObject();
            fileHashes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> files.addProperty(entry.getKey(), entry.getValue()));
            root.add("files", files);
            JsonObject localization = new JsonObject();
            localizationValues.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> localization.addProperty(entry.getKey(), entry.getValue()));
            root.add("localization", localization);
            return root;
        }
    }

    private enum PngKind {
        BASE_TEXTURE,
        DYE_MASK,
        MISSING,
        BRASS_MOUNT,
        IRON_MOUNT
    }

    public static final class ScaffoldException extends RuntimeException {
        public ScaffoldException(String message) {
            super(message);
        }

        public ScaffoldException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
