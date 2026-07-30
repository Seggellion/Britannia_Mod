package com.seggellion.britannia_mod.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Read-only validation for one owner-supplied final-content intake.
 *
 * <p>The input is YAML 1.2 expressed in JSON-compatible syntax, matching the catalogue convention. This class
 * reads the intake, the live catalogue identity list, and referenced source files. It never writes, copies,
 * generates, stages, or publishes content.</p>
 */
public final class FinalContentIntakeValidator {
    public enum Status {
        NOT_READY,
        READY_FOR_INTEGRATION,
        INVALID
    }

    public record PngMetadata(
            String layer,
            String sourceFile,
            int width,
            int height,
            int bitDepth,
            int colourType,
            boolean alphaChannel) {
    }

    public record Result(
            Status status,
            String intakePath,
            String stableId,
            List<String> issues,
            List<PngMetadata> pngMetadata) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(intakePath, "intakePath");
            stableId = stableId == null ? "" : stableId;
            issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
            pngMetadata = List.copyOf(Objects.requireNonNull(pngMetadata, "pngMetadata"));
        }
    }

    private static final Pattern RESOURCE_ID =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2}_[a-z]{2}");
    private static final Set<String> ORIENTATIONS =
            Set.of("wall_parallel", "wall_perpendicular");
    private static final Set<String> MOUNTS =
            Set.of("britannia_mod:brass", "britannia_mod:iron");
    private static final List<String> PNG_LAYERS =
            List.of("base_texture", "dye_mask");
    private static final int FINAL_TEXTURE_WIDTH = 128;
    private static final int FINAL_TEXTURE_HEIGHT = 128;

    private FinalContentIntakeValidator() {
    }

    public static Result validate(Path repositoryRoot, Path intakePath) {
        Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        Objects.requireNonNull(intakePath, "intakePath");
        Path root = repositoryRoot.toAbsolutePath().normalize();
        Path intake = intakePath.toAbsolutePath().normalize();
        List<String> invalid = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<PngMetadata> pngs = new ArrayList<>();

        JsonObject document = readObject(intake, invalid);
        if (document == null) {
            return result(Status.INVALID, intake, "", invalid, missing, pngs);
        }
        rejectRemovedAssetKey(document, "fabric_base", invalid);
        rejectRemovedAssetKey(document, "static_overlay", invalid);

        Integer schemaVersion = integer(document, "schema_version", invalid);
        if (schemaVersion == null) {
            missing.add("schema_version is required");
        } else if (schemaVersion != 1) {
            invalid.add("schema_version must be 1");
        }

        String approvalStatus = text(document, "approval.status", invalid);
        boolean explicitlyApproved = "APPROVED".equals(approvalStatus);
        if (approvalStatus == null || approvalStatus.isBlank()) {
            missing.add("approval.status is required");
        } else if (!Set.of("NOT_APPROVED", "APPROVED").contains(approvalStatus)) {
            invalid.add("approval.status must be NOT_APPROVED or APPROVED");
        } else if (!explicitlyApproved) {
            missing.add("approval.status is NOT_APPROVED");
        }
        String approvedBy = requiredText(document, "approval.approved_by", missing, invalid);
        String approvedDate = requiredText(document, "approval.approved_date", missing, invalid);
        if (approvedDate != null && !approvedDate.isBlank()) {
            try {
                LocalDate.parse(approvedDate);
            } catch (DateTimeParseException exception) {
                invalid.add("approval.approved_date must use YYYY-MM-DD");
            }
        }

        String stableId = requiredText(document, "banner.stable_id", missing, invalid);
        String catalogueGroup = null;
        if (stableId != null && !stableId.isBlank()) {
            if (!RESOURCE_ID.matcher(stableId).matches()) {
                invalid.add("banner.stable_id is not a valid namespaced resource ID");
            } else {
                Map<String, String> catalogueGroups = catalogueGroups(root, invalid);
                if (!catalogueGroups.isEmpty() && !catalogueGroups.containsKey(stableId)) {
                    invalid.add("banner.stable_id is not present in the live catalogue: " + stableId);
                } else {
                    catalogueGroup = catalogueGroups.get(stableId);
                }
            }
        }
        requiredText(document, "banner.final_display_name", missing, invalid);

        Integer width = integer(document, "banner.width_blocks", invalid);
        Integer height = integer(document, "banner.height_blocks", invalid);
        if (width == null) {
            missing.add("banner.width_blocks is required");
        } else if (width < 1 || width > 3) {
            invalid.add("banner.width_blocks must be between 1 and 3");
        }
        if (height == null) {
            missing.add("banner.height_blocks is required");
        } else if (height < 1 || height > 2) {
            invalid.add("banner.height_blocks must be between 1 and 2");
        }

        List<String> orientations = stringList(document, "banner.supported_orientations", invalid);
        if (orientations == null || orientations.isEmpty()) {
            missing.add("banner.supported_orientations requires at least one approved value");
        } else {
            validateDistinctAllowed(orientations, ORIENTATIONS,
                    "banner.supported_orientations", invalid);
            if ("large".equals(catalogueGroup)
                    && !List.of("wall_parallel").equals(orientations)) {
                invalid.add("large banners support wall_parallel orientation only");
            }
        }

        List<String> mounts = stringList(document, "banner.supported_mounts", invalid);
        if (mounts == null || mounts.isEmpty()) {
            missing.add("banner.supported_mounts requires at least one approved value");
        } else {
            validateDistinctAllowed(mounts, MOUNTS, "banner.supported_mounts", invalid);
        }
        String defaultMount = requiredText(document, "banner.default_mount", missing, invalid);
        if (defaultMount != null && !defaultMount.isBlank()) {
            if (!MOUNTS.contains(defaultMount)) {
                invalid.add("banner.default_mount is not an allowed mount: " + defaultMount);
            } else if (mounts != null && !mounts.isEmpty() && !mounts.contains(defaultMount)) {
                invalid.add("banner.default_mount must be included in banner.supported_mounts");
            }
        }

        validateResourceField(document, "banner.placement_profile_id", missing, invalid, false);
        String geometryId =
                validateResourceField(document, "banner.geometry_id", missing, invalid, explicitlyApproved);
        requiredText(document, "banner.geometry_convention", missing, invalid);
        requiredText(document, "localization.language", missing, invalid);
        String language = text(document, "localization.language", invalid);
        if (language != null && !language.isBlank() && !LANGUAGE.matcher(language).matches()) {
            invalid.add("localization.language must use a lower-case language_region code");
        }
        requiredText(document, "localization.value", missing, invalid);

        List<String> layerResourceIds = new ArrayList<>();
        Integer expectedWidth = null;
        Integer expectedHeight = null;
        for (String layer : PNG_LAYERS) {
            String base = "assets." + layer;
            String resourceId =
                    validateResourceField(document, base + ".resource_id", missing, invalid, explicitlyApproved);
            if (resourceId != null && !resourceId.isBlank()) {
                layerResourceIds.add(resourceId);
            }
            String sourceFile = requiredText(document, base + ".source_file", missing, invalid);
            String expectedSha = requiredText(document, base + ".sha256", missing, invalid);
            PngMetadata metadata = validatePng(root, layer, sourceFile, expectedSha, invalid);
            if (metadata != null) {
                pngs.add(metadata);
                if (explicitlyApproved
                        && (metadata.width() != FINAL_TEXTURE_WIDTH
                        || metadata.height() != FINAL_TEXTURE_HEIGHT)) {
                    invalid.add("final " + ("base_texture".equals(layer) ? "base texture" : "dye mask")
                            + " must be exactly 128x128 RGBA");
                }
                if (expectedWidth == null) {
                    expectedWidth = metadata.width();
                    expectedHeight = metadata.height();
                } else if (expectedWidth != metadata.width() || expectedHeight != metadata.height()) {
                    invalid.add("base_texture and dye_mask PNG dimensions must match exactly");
                }
            }
        }
        if (layerResourceIds.size() == PNG_LAYERS.size()
                && new LinkedHashSet<>(layerResourceIds).size() != PNG_LAYERS.size()) {
            invalid.add("base_texture and dye_mask resource IDs must be distinct");
        }
        validateMaskCoverage(root, document, invalid);

        Boolean sharedGeometry = bool(document, "assets.geometry.shared_geometry_approved", invalid);
        if (sharedGeometry == null) {
            missing.add("assets.geometry.shared_geometry_approved is required");
        }
        String geometrySource = text(document, "assets.geometry.source_file", invalid);
        String geometrySha = text(document, "assets.geometry.sha256", invalid);
        if (Boolean.FALSE.equals(sharedGeometry)) {
            if (geometrySource == null || geometrySource.isBlank()) {
                missing.add("assets.geometry.source_file is required unless shared geometry is approved");
            }
            if (geometrySha == null || geometrySha.isBlank()) {
                missing.add("assets.geometry.sha256 is required unless shared geometry is approved");
            }
            validateRegularSource(root, "geometry", geometrySource, geometrySha, invalid);
        } else if (Boolean.TRUE.equals(sharedGeometry)
                && geometryId != null && geometryId.contains("/placeholder/")) {
            invalid.add("approved shared geometry may not use a diagnostic placeholder resource ID");
        } else if (Boolean.TRUE.equals(sharedGeometry)
                && ((geometrySource != null && !geometrySource.isBlank())
                || (geometrySha != null && !geometrySha.isBlank()))) {
            if (geometrySource == null || geometrySource.isBlank()
                    || geometrySha == null || geometrySha.isBlank()) {
                invalid.add("optional approved shared-geometry source_file and sha256 must be supplied together");
            } else {
                validateRegularSource(root, "geometry", geometrySource, geometrySha, invalid);
            }
        }

        validateOptionalReference(root, document, "references.authoring_source_file", invalid);
        validateOptionalReference(root, document, "references.preview_image_file", invalid);

        Boolean originalArt = bool(document, "provenance.original_art", invalid);
        if (!Boolean.TRUE.equals(originalArt)) {
            missing.add("provenance.original_art must be true");
        }
        requiredText(document, "provenance.creator", missing, invalid);
        requiredText(document, "provenance.creation_method", missing, invalid);
        requiredText(document, "provenance.source_project_file", missing, invalid);
        Boolean distribution = bool(document, "provenance.distribution_permission_confirmed", invalid);
        if (!Boolean.TRUE.equals(distribution)) {
            missing.add("provenance.distribution_permission_confirmed must be true");
        }
        Boolean copied = bool(document, "provenance.copied_from_reference_art", invalid);
        if (copied == null) {
            missing.add("provenance.copied_from_reference_art must be explicitly false");
        } else if (copied) {
            invalid.add("provenance.copied_from_reference_art must be false");
        }

        Boolean manualPerformed = bool(document, "manual_verification.performed", invalid);
        if (Boolean.TRUE.equals(manualPerformed)) {
            requiredText(document, "manual_verification.tester", missing, invalid);
            String manualDate =
                    requiredText(document, "manual_verification.date", missing, invalid);
            if (manualDate != null && !manualDate.isBlank()) {
                try {
                    LocalDate.parse(manualDate);
                } catch (DateTimeParseException exception) {
                    invalid.add("manual_verification.date must use YYYY-MM-DD");
                }
            }
        } else if (manualPerformed == null) {
            missing.add("manual_verification.performed is required");
        }

        String requestedStatus = requiredText(
                document, "requested_content_status", missing, invalid);
        if (requestedStatus != null && !requestedStatus.isBlank()) {
            if (!Set.of("placeholder", "in_progress").contains(requestedStatus)) {
                invalid.add("requested_content_status must be placeholder or in_progress during intake");
            } else if (!"in_progress".equals(requestedStatus)) {
                missing.add("requested_content_status must be in_progress before integration");
            }
        }

        if (explicitlyApproved && (approvedBy == null || approvedBy.isBlank())) {
            missing.add("approval.approved_by is required for APPROVED intake");
        }
        Status status = !invalid.isEmpty()
                ? Status.INVALID
                : missing.isEmpty() && explicitlyApproved
                        ? Status.READY_FOR_INTEGRATION
                        : Status.NOT_READY;
        return result(status, intake, stableId, invalid, missing, pngs);
    }

    public static void printReport(Result result, PrintStream output) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(output, "output");
        output.println("Final content intake status: " + result.status());
        output.println("Intake: " + result.intakePath());
        output.println("Stable ID: " + (result.stableId().isBlank() ? "(missing)" : result.stableId()));
        result.pngMetadata().forEach(metadata -> output.printf(Locale.ROOT,
                "PNG %s: source=%s dimensions=%dx%d bit_depth=%d colour_type=%d alpha=%s%n",
                metadata.layer(), metadata.sourceFile(), metadata.width(), metadata.height(),
                metadata.bitDepth(), metadata.colourType(), metadata.alphaChannel()));
        result.issues().forEach(issue -> output.println("Issue: " + issue));
    }

    private static Result result(
            Status status,
            Path intake,
            String stableId,
            List<String> invalid,
            List<String> missing,
            List<PngMetadata> pngs) {
        List<String> issues = new ArrayList<>();
        invalid.forEach(issue -> issues.add("INVALID: " + issue));
        missing.forEach(issue -> issues.add("MISSING: " + issue));
        List<String> orderedIssues = issues.stream().distinct().sorted().toList();
        List<PngMetadata> orderedPngs = pngs.stream()
                .sorted(Comparator.comparing(PngMetadata::layer)).toList();
        return new Result(status, intake.toString().replace('\\', '/'), stableId, orderedIssues, orderedPngs);
    }

    private static JsonObject readObject(Path path, List<String> invalid) {
        if (!Files.isRegularFile(path)) {
            invalid.add("intake file does not exist: " + path.toString().replace('\\', '/'));
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                invalid.add("intake root must be an object");
                return null;
            }
            return parsed.getAsJsonObject();
        } catch (IOException | JsonParseException exception) {
            invalid.add("intake must be valid JSON-compatible YAML 1.2: " + exception.getMessage());
            return null;
        }
    }

    private static Map<String, String> catalogueGroups(Path root, List<String> invalid) {
        Path manifest = root.resolve(BannerScaffoldTool.MANIFEST_PATH);
        try {
            JsonObject object = JsonParser.parseString(
                    Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> groups = new LinkedHashMap<>();
            for (JsonElement element : object.getAsJsonArray("banners")) {
                JsonObject banner = element.getAsJsonObject();
                groups.put(
                        "britannia_mod:" + banner.get("id").getAsString(),
                        banner.get("group").getAsString());
            }
            return Map.copyOf(groups);
        } catch (IOException | RuntimeException exception) {
            invalid.add("live catalogue IDs could not be read: " + exception.getMessage());
            return Map.of();
        }
    }

    private static String validateResourceField(
            JsonObject root,
            String path,
            List<String> missing,
            List<String> invalid,
            boolean rejectPlaceholder) {
        String value = requiredText(root, path, missing, invalid);
        if (value != null && !value.isBlank()) {
            if (!RESOURCE_ID.matcher(value).matches()) {
                invalid.add(path + " is not a valid namespaced resource ID");
            } else if (rejectPlaceholder && value.contains("/placeholder/")) {
                invalid.add(path + " may not use a diagnostic placeholder when approval is APPROVED");
            }
        }
        return value;
    }

    private static void validateDistinctAllowed(
            List<String> values,
            Set<String> allowed,
            String path,
            List<String> invalid) {
        if (new LinkedHashSet<>(values).size() != values.size()) {
            invalid.add(path + " contains duplicate values");
        }
        values.stream().filter(value -> !allowed.contains(value)).sorted().forEach(
                value -> invalid.add(path + " contains unsupported value: " + value));
    }

    private static PngMetadata validatePng(
            Path root,
            String layer,
            String sourceFile,
            String expectedSha,
            List<String> invalid) {
        Path source = resolveSource(root, layer, sourceFile, invalid);
        if (source == null) {
            return null;
        }
        if (!source.getFileName().toString().endsWith(".png")) {
            invalid.add(layer + " source_file must use the lower-case .png extension");
            return null;
        }
        validateSha(source, layer, expectedSha, invalid);
        try {
            byte[] bytes = Files.readAllBytes(source);
            if (bytes.length < 29 || bytes[0] != (byte) 0x89 || bytes[1] != 0x50
                    || bytes[2] != 0x4E || bytes[3] != 0x47) {
                invalid.add(layer + " source_file is not a PNG");
                return null;
            }
            BufferedImage image = ImageIO.read(source.toFile());
            if (image == null) {
                invalid.add(layer + " source_file could not be decoded as PNG");
                return null;
            }
            int bitDepth = Byte.toUnsignedInt(bytes[24]);
            int colourType = Byte.toUnsignedInt(bytes[25]);
            boolean alpha = image.getColorModel().hasAlpha();
            if (bitDepth != 8 || colourType != 6 || !alpha) {
                invalid.add(layer + " source_file must be 8-bit true-colour RGBA PNG (colour type 6)");
            }
            if ("dye_mask".equals(layer)) {
                validateDyeMaskPixels(image, invalid);
            }
            return new PngMetadata(layer, relative(root, source), image.getWidth(), image.getHeight(),
                    bitDepth, colourType, alpha);
        } catch (IOException exception) {
            invalid.add(layer + " source_file could not be read: " + exception.getMessage());
            return null;
        }
    }

    private static void validateDyeMaskPixels(BufferedImage image, List<String> invalid) {
        boolean transparent = false;
        boolean active = false;
        boolean grayscale = true;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    transparent = true;
                    continue;
                }
                active = true;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                int maximum = Math.max(red, Math.max(green, blue));
                int minimum = Math.min(red, Math.min(green, blue));
                if (maximum - minimum > 1) {
                    grayscale = false;
                }
            }
        }
        if (!transparent) {
            invalid.add("dye_mask must contain at least one fully transparent pixel");
        }
        if (!active) {
            invalid.add("dye_mask must contain at least one active pixel");
        }
        if (!grayscale) {
            invalid.add("dye_mask active RGB must be grayscale within a maximum channel difference of 1");
        }
    }

    /** The dye pass may follow a partially transparent edge but must never exceed the base silhouette alpha. */
    private static void validateMaskCoverage(
            Path root, JsonObject document, List<String> invalid) {
        String baseSource = text(document, "assets.base_texture.source_file", invalid);
        String maskSource = text(document, "assets.dye_mask.source_file", invalid);
        BufferedImage base = readValidatedImage(root, baseSource);
        BufferedImage mask = readValidatedImage(root, maskSource);
        if (base == null || mask == null
                || base.getWidth() != mask.getWidth() || base.getHeight() != mask.getHeight()) {
            return;
        }
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                int baseAlpha = base.getRGB(x, y) >>> 24;
                int maskAlpha = mask.getRGB(x, y) >>> 24;
                if (maskAlpha > baseAlpha) {
                    invalid.add("dye_mask alpha must not exceed base_texture alpha at any pixel");
                    return;
                }
            }
        }
    }

    private static BufferedImage readValidatedImage(Path root, String sourceFile) {
        if (sourceFile == null || sourceFile.isBlank()) {
            return null;
        }
        try {
            Path declared = Path.of(sourceFile);
            Path source = root.resolve(declared).normalize();
            if (declared.isAbsolute() || !source.startsWith(root) || !Files.isRegularFile(source)) {
                return null;
            }
            return ImageIO.read(source.toFile());
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static void rejectRemovedAssetKey(
            JsonObject document, String key, List<String> invalid) {
        JsonElement assets = at(document, "assets");
        if (assets != null && assets.isJsonObject() && assets.getAsJsonObject().has(key)) {
            invalid.add("assets." + key
                    + " is removed; the project now uses exactly base_texture + dye_mask");
        }
    }

    private static void validateRegularSource(
            Path root,
            String label,
            String sourceFile,
            String expectedSha,
            List<String> invalid) {
        Path source = resolveSource(root, label, sourceFile, invalid);
        if (source != null) {
            validateSha(source, label, expectedSha, invalid);
        }
    }

    private static Path resolveSource(
            Path root,
            String label,
            String sourceFile,
            List<String> invalid) {
        if (sourceFile == null || sourceFile.isBlank()) {
            return null;
        }
        Path declared;
        try {
            declared = Path.of(sourceFile);
        } catch (RuntimeException exception) {
            invalid.add(label + " source_file is not a valid path");
            return null;
        }
        if (declared.isAbsolute()) {
            invalid.add(label + " source_file must be repository-relative");
            return null;
        }
        Path source = root.resolve(declared).normalize();
        if (!source.startsWith(root)) {
            invalid.add(label + " source_file escapes the repository");
            return null;
        }
        if (!Files.isRegularFile(source)) {
            invalid.add(label + " source_file does not exist: " + sourceFile);
            return null;
        }
        return source;
    }

    private static void validateSha(
            Path source,
            String label,
            String expectedSha,
            List<String> invalid) {
        if (expectedSha == null || expectedSha.isBlank()) {
            return;
        }
        if (!SHA256.matcher(expectedSha).matches()) {
            invalid.add(label + " sha256 must be 64 lower-case hexadecimal characters");
            return;
        }
        try {
            String actual = hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));
            if (!actual.equals(expectedSha)) {
                invalid.add(label + " sha256 does not match source_file");
            }
        } catch (IOException | NoSuchAlgorithmException exception) {
            invalid.add(label + " sha256 could not be verified: " + exception.getMessage());
        }
    }

    private static void validateOptionalReference(
            Path root,
            JsonObject document,
            String path,
            List<String> invalid) {
        String value = text(document, path, invalid);
        if (value != null && !value.isBlank()) {
            resolveSource(root, path, value, invalid);
        }
    }

    private static String requiredText(
            JsonObject root,
            String path,
            List<String> missing,
            List<String> invalid) {
        String value = text(root, path, invalid);
        if (value == null || value.isBlank()) {
            missing.add(path + " is required");
        }
        return value;
    }

    private static String text(JsonObject root, String path, List<String> invalid) {
        JsonElement value = at(root, path);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            invalid.add(path + " must be a string or null");
            return null;
        }
        return value.getAsString();
    }

    private static Integer integer(JsonObject root, String path, List<String> invalid) {
        JsonElement value = at(root, path);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                invalid.add(path + " must be an integer or null");
                return null;
            }
            return value.getAsInt();
        } catch (RuntimeException exception) {
            invalid.add(path + " must be an integer or null");
            return null;
        }
    }

    private static Boolean bool(JsonObject root, String path, List<String> invalid) {
        JsonElement value = at(root, path);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            invalid.add(path + " must be true, false, or null");
            return null;
        }
        return value.getAsBoolean();
    }

    private static List<String> stringList(
            JsonObject root,
            String path,
            List<String> invalid) {
        JsonElement value = at(root, path);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonArray()) {
            invalid.add(path + " must be an array");
            return null;
        }
        List<String> values = new ArrayList<>();
        JsonArray array = value.getAsJsonArray();
        for (JsonElement entry : array) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                invalid.add(path + " must contain only strings");
                return List.of();
            }
            values.add(entry.getAsString());
        }
        return List.copyOf(values);
    }

    private static JsonElement at(JsonObject root, String path) {
        String[] parts = path.split("\\.");
        JsonElement current = root;
        for (String part : parts) {
            if (!current.isJsonObject() || !current.getAsJsonObject().has(part)) {
                return null;
            }
            current = current.getAsJsonObject().get(part);
        }
        return current;
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            value.append(String.format(Locale.ROOT, "%02x", current));
        }
        return value.toString();
    }
}
