package com.seggellion.britannia_mod.bannerdyeing;

import static com.seggellion.britannia_mod.bannerdyeing.testsupport.CoreDataFixtures.SCHEMA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.banner.data.BannerSourceReference;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.CoreDataFixtures;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.dye.palette.MaterialPaletteEntry;
import com.seggellion.britannia_mod.dye.service.DyeResult;
import com.seggellion.britannia_mod.dye.service.MatchType;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CoreDataValidationTest {
    @Test
    void widthBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BannerDimensions(0, 1, false));
        assertCodecError(BannerDimensions.CODEC, dimensionsJson(0, 1));
    }

    @Test
    void widthAboveThreeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BannerDimensions(4, 1, false));
        assertCodecError(BannerDimensions.CODEC, dimensionsJson(4, 1));
    }

    @Test
    void zeroAndNegativeHeightAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BannerDimensions(1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new BannerDimensions(1, -1, false));
        assertCodecError(BannerDimensions.CODEC, dimensionsJson(1, 0));
        assertCodecError(BannerDimensions.CODEC, dimensionsJson(1, -1));
    }

    @Test
    void heightAboveDocumentedSafeMaximumIsRejected() {
        int tooTall = BannerDimensions.MAX_HEIGHT_BLOCKS + 1;
        assertThrows(IllegalArgumentException.class, () -> new BannerDimensions(1, tooTall, false));
        assertCodecError(BannerDimensions.CODEC, dimensionsJson(1, tooTall));
    }

    @Test
    void emptyOrientationListIsRejected() {
        JsonObject json = encodedDefinition();
        json.add("supported_orientations", new JsonArray());
        assertCodecError(BannerDefinition.CODEC, json);
        assertThrows(IllegalArgumentException.class, () -> copyDefinition(List.of(),
                CoreDataFixtures.bannerDefinition().supportedMounts(), CoreDataFixtures.BRASS_MOUNT_ID));
    }

    @Test
    void emptySupportedMountListIsRejected() {
        JsonObject json = encodedDefinition();
        json.add("supported_mounts", new JsonArray());
        assertCodecError(BannerDefinition.CODEC, json);
        assertThrows(IllegalArgumentException.class, () -> copyDefinition(
                CoreDataFixtures.bannerDefinition().supportedOrientations(), List.of(),
                CoreDataFixtures.BRASS_MOUNT_ID));
    }

    @Test
    void defaultMountMustBelongToSupportedMounts() {
        JsonObject json = encodedDefinition();
        json.addProperty("default_mount", "britannia_mod:unsupported_mount");
        assertCodecError(BannerDefinition.CODEC, json);
        assertThrows(IllegalArgumentException.class, () -> copyDefinition(
                CoreDataFixtures.bannerDefinition().supportedOrientations(),
                List.of(CoreDataFixtures.IRON_MOUNT_ID), CoreDataFixtures.BRASS_MOUNT_ID));
    }

    @Test
    void sourcePageMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new BannerSourceReference(0, 1, Optional.empty()));
        JsonObject json = sourceJson(0, 1);
        assertCodecError(BannerSourceReference.CODEC, json);
    }

    @Test
    void sourceRowMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new BannerSourceReference(1, 0, Optional.empty()));
        JsonObject json = sourceJson(1, 0);
        assertCodecError(BannerSourceReference.CODEC, json);
    }

    @Test
    void canonicalSrgbRejectsLowercaseShortAndPrefixlessValues() {
        for (String invalid : List.of("#a51c30", "#ABC", "A51C30")) {
            assertThrows(IllegalArgumentException.class, () -> new PigmentDefinition(
                    SCHEMA,
                    CoreDataFixtures.PIGMENT_ID,
                    "pigment.test",
                    invalid,
                    List.of(0.5, 0.1, 0.1),
                    List.of(),
                    "common"));
            JsonObject json = encodedPigment();
            json.addProperty("reference_srgb", invalid);
            assertCodecError(PigmentDefinition.CODEC, json);
        }
    }

    @Test
    void nanOklabComponentIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PigmentDefinition(
                SCHEMA,
                CoreDataFixtures.PIGMENT_ID,
                "pigment.test",
                "#A51C30",
                List.of(Double.NaN, 0.1, 0.1),
                List.of(),
                "common"));
        JsonObject json = encodedPigment();
        json.add("reference_oklab", oklab(Double.NaN));
        assertCodecError(PigmentDefinition.CODEC, json);
    }

    @Test
    void infiniteOklabComponentIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MaterialPaletteEntry(
                CoreDataFixtures.DYED_COLOUR_ID,
                "colour.test",
                "#A81742",
                List.of(0.5, Double.POSITIVE_INFINITY, 0.1),
                0,
                List.of(),
                List.of(),
                List.of()));
        JsonObject json = MaterialPaletteEntry.CODEC.encodeStart(
                JsonOps.INSTANCE, CoreDataFixtures.dyedEntry()).getOrThrow().getAsJsonObject();
        json.add("match_oklab", oklab(Double.POSITIVE_INFINITY));
        assertCodecError(MaterialPaletteEntry.CODEC, json);
    }

    @Test
    void duplicatePaletteEntryIdsAreRejectedLocally() {
        assertThrows(IllegalArgumentException.class, () -> new MaterialPalette(
                SCHEMA,
                CoreDataFixtures.PALETTE_ID,
                CoreDataFixtures.MATERIAL_ID,
                CoreDataFixtures.NATURAL_COLOUR_ID,
                List.of(CoreDataFixtures.naturalEntry(), CoreDataFixtures.naturalEntry()),
                Map.of()));
        JsonObject json = encodedPalette();
        JsonArray entries = json.getAsJsonArray("entries");
        entries.add(entries.get(0).deepCopy());
        assertCodecError(MaterialPalette.CODEC, json);
    }

    @Test
    void naturalColourMustExistInOwningPaletteEntries() {
        assertThrows(IllegalArgumentException.class, () -> new MaterialPalette(
                SCHEMA,
                CoreDataFixtures.PALETTE_ID,
                CoreDataFixtures.MATERIAL_ID,
                CoreDataFixtures.NATURAL_COLOUR_ID,
                List.of(CoreDataFixtures.dyedEntry()),
                Map.of()));
        JsonObject json = encodedPalette();
        json.addProperty("natural_colour_id", "britannia_mod:not_in_entries");
        assertCodecError(MaterialPalette.CODEC, json);
    }

    @Test
    void pigmentOverridesMustTargetOwningPaletteEntries() {
        assertThrows(IllegalArgumentException.class, () -> new MaterialPalette(
                SCHEMA,
                CoreDataFixtures.PALETTE_ID,
                CoreDataFixtures.MATERIAL_ID,
                CoreDataFixtures.NATURAL_COLOUR_ID,
                List.of(CoreDataFixtures.naturalEntry()),
                Map.of(CoreDataFixtures.PIGMENT_ID, CoreDataFixtures.DYED_COLOUR_ID)));
        JsonObject json = encodedPalette();
        json.getAsJsonObject("pigment_overrides").addProperty(
                CoreDataFixtures.PIGMENT_ID.toString(), "britannia_mod:missing_colour");
        assertCodecError(MaterialPalette.CODEC, json);
    }

    @Test
    void emptyPaletteIsRejectedStructurally() {
        JsonObject json = encodedPalette();
        json.add("entries", new JsonArray());
        assertCodecError(MaterialPalette.CODEC, json);
    }

    @Test
    void compatibilityTagsMustBeUniqueLowercaseStableTokens() {
        assertThrows(IllegalArgumentException.class, () -> new MaterialPaletteEntry(
                CoreDataFixtures.DYED_COLOUR_ID, "colour.test", "#A81742",
                CoreDataFixtures.dyedEntry().matchOklab(), 0, List.of(), List.of("Ice"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MaterialPaletteEntry(
                CoreDataFixtures.DYED_COLOUR_ID, "colour.test", "#A81742",
                CoreDataFixtures.dyedEntry().matchOklab(), 0, List.of(), List.of("ice", "ice"), List.of()));
    }

    @Test
    void negativeRemainingDyeUsesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DyeTubState(
                SCHEMA, Optional.of(CoreDataFixtures.PIGMENT_ID), Optional.of(-1)));
        JsonObject json = DyeTubState.CODEC.encodeStart(
                JsonOps.INSTANCE, CoreDataFixtures.finiteTub()).getOrThrow().getAsJsonObject();
        json.addProperty("remaining_uses", -1);
        assertCodecError(DyeTubState.CODEC, json);
    }

    @Test
    void remainingUsesRequireLoadedPigment() {
        assertThrows(IllegalArgumentException.class,
                () -> new DyeTubState(SCHEMA, Optional.empty(), Optional.of(1)));
        JsonObject json = DyeTubState.CODEC.encodeStart(
                JsonOps.INSTANCE, CoreDataFixtures.emptyTub()).getOrThrow().getAsJsonObject();
        json.addProperty("remaining_uses", 1);
        assertCodecError(DyeTubState.CODEC, json);
    }

    @Test
    void negativeAndNonFiniteDyeDistancesAreRejected() {
        for (double invalid : List.of(-0.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException.class,
                    () -> new DyeResult(CoreDataFixtures.DYED_COLOUR_ID, MatchType.NEAREST_COLOUR, invalid));
            JsonObject json = DyeResult.CODEC.encodeStart(
                    JsonOps.INSTANCE, CoreDataFixtures.dyeResult()).getOrThrow().getAsJsonObject();
            json.add("perceptual_distance", new JsonPrimitive(invalid));
            assertCodecError(DyeResult.CODEC, json);
        }
    }

    @Test
    void collectionInputsAreDefensivelyCopiedAndViewsAreImmutable() {
        List<BannerOrientation> orientations = new ArrayList<>(List.of(BannerOrientation.WALL_PARALLEL));
        List<MountId> mounts = new ArrayList<>(List.of(CoreDataFixtures.BRASS_MOUNT_ID));
        BannerDefinition definition = copyDefinition(orientations, mounts, CoreDataFixtures.BRASS_MOUNT_ID);
        orientations.add(BannerOrientation.WALL_PERPENDICULAR);
        mounts.add(CoreDataFixtures.IRON_MOUNT_ID);
        assertEquals(1, definition.supportedOrientations().size());
        assertEquals(1, definition.supportedMounts().size());
        assertThrows(UnsupportedOperationException.class,
                () -> definition.supportedMounts().add(CoreDataFixtures.IRON_MOUNT_ID));

        List<MaterialPaletteEntry> entries = new ArrayList<>(
                List.of(CoreDataFixtures.naturalEntry(), CoreDataFixtures.dyedEntry()));
        Map<PigmentId, ResolvedColourId> overrides = new LinkedHashMap<>();
        overrides.put(CoreDataFixtures.PIGMENT_ID, CoreDataFixtures.DYED_COLOUR_ID);
        MaterialPalette palette = new MaterialPalette(
                SCHEMA,
                CoreDataFixtures.PALETTE_ID,
                CoreDataFixtures.MATERIAL_ID,
                CoreDataFixtures.NATURAL_COLOUR_ID,
                entries,
                overrides);
        entries.clear();
        overrides.clear();
        assertEquals(2, palette.entries().size());
        assertEquals(1, palette.pigmentOverrides().size());
        assertThrows(UnsupportedOperationException.class, () -> palette.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> palette.pigmentOverrides().clear());
    }

    @Test
    void pigmentOklabAndTagsAreDefensivelyCopied() {
        List<Double> oklab = new ArrayList<>(List.of(0.5, 0.1, 0.1));
        List<String> tags = new ArrayList<>(List.of("red"));
        PigmentDefinition pigment = new PigmentDefinition(
                SCHEMA,
                CoreDataFixtures.PIGMENT_ID,
                "pigment.test",
                "#A51C30",
                oklab,
                tags,
                "common");
        oklab.set(0, 0.9);
        tags.add("changed");
        assertEquals(0.5, pigment.referenceOklab().get(0));
        assertEquals(List.of("red"), pigment.tags());
        assertThrows(UnsupportedOperationException.class, () -> pigment.tags().add("mutate"));
    }

    @Test
    void pigmentOverridesSerializeInStableIdentifierOrder() {
        PigmentId first = PigmentId.parse("britannia_mod:a_pigment");
        PigmentId second = PigmentId.parse("britannia_mod:z_pigment");
        Map<PigmentId, ResolvedColourId> overrides = new LinkedHashMap<>();
        overrides.put(second, CoreDataFixtures.DYED_COLOUR_ID);
        overrides.put(first, CoreDataFixtures.NATURAL_COLOUR_ID);
        MaterialPalette palette = new MaterialPalette(
                SCHEMA,
                CoreDataFixtures.PALETTE_ID,
                CoreDataFixtures.MATERIAL_ID,
                CoreDataFixtures.NATURAL_COLOUR_ID,
                List.of(CoreDataFixtures.naturalEntry(), CoreDataFixtures.dyedEntry()),
                overrides);
        JsonObject encoded = MaterialPalette.CODEC.encodeStart(
                JsonOps.INSTANCE, palette).getOrThrow().getAsJsonObject();
        assertEquals(List.of("britannia_mod:a_pigment", "britannia_mod:z_pigment"),
                new ArrayList<>(encoded.getAsJsonObject("pigment_overrides").keySet()));
    }

    private static JsonObject dimensionsJson(int width, int height) {
        JsonObject json = new JsonObject();
        json.addProperty("width_blocks", width);
        json.addProperty("height_blocks", height);
        json.addProperty("provisional", false);
        return json;
    }

    private static JsonObject sourceJson(int page, int row) {
        JsonObject json = new JsonObject();
        json.addProperty("page", page);
        json.addProperty("row", row);
        return json;
    }

    private static JsonArray oklab(double first) {
        JsonArray values = new JsonArray();
        values.add(first);
        values.add(0.1);
        values.add(0.1);
        return values;
    }

    private static JsonObject encodedDefinition() {
        return BannerDefinition.CODEC.encodeStart(
                JsonOps.INSTANCE, CoreDataFixtures.bannerDefinition()).getOrThrow().getAsJsonObject();
    }

    private static JsonObject encodedPigment() {
        return PigmentDefinition.CODEC.encodeStart(
                JsonOps.INSTANCE, CoreDataFixtures.pigment()).getOrThrow().getAsJsonObject();
    }

    private static JsonObject encodedPalette() {
        return MaterialPalette.CODEC.encodeStart(
                JsonOps.INSTANCE, CoreDataFixtures.palette()).getOrThrow().getAsJsonObject();
    }

    private static BannerDefinition copyDefinition(
            List<BannerOrientation> orientations,
            List<MountId> mounts,
            MountId defaultMount) {
        BannerDefinition original = CoreDataFixtures.bannerDefinition();
        return new BannerDefinition(
                original.schemaVersion(),
                original.id(),
                original.displayNameKey(),
                original.contentStatus(),
                original.sourceReference(),
                original.catalogueGroup(),
                original.dimensions(),
                orientations,
                mounts,
                defaultMount,
                original.defaultMaterial(),
                original.assets(),
                original.placementProfile());
    }

    private static <T> void assertCodecError(Codec<T> codec, JsonObject json) {
        assertTrue(codec.parse(JsonOps.INSTANCE, json).error().isPresent(),
                () -> "Expected codec error for " + json);
    }
}
