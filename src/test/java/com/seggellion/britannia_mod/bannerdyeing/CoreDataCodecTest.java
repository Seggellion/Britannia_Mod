package com.seggellion.britannia_mod.bannerdyeing;

import static com.seggellion.britannia_mod.bannerdyeing.testsupport.CoreDataFixtures.SCHEMA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.banner.data.BannerAssets;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.banner.data.BannerSourceReference;
import com.seggellion.britannia_mod.banner.data.MountDefinition;
import com.seggellion.britannia_mod.banner.data.PlacementProfile;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.CoreDataFixtures;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.dye.palette.MaterialPaletteEntry;
import com.seggellion.britannia_mod.dye.service.DyeResult;
import com.seggellion.britannia_mod.dye.service.MatchType;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CoreDataCodecTest {
    private static final List<Class<?>> COMMON_DATA_CLASSES = List.of(
            DataCodecs.class,
            BannerContentStatus.class,
            BannerDefinition.class,
            BannerDimensions.class,
            BannerAssets.class,
            BannerSourceReference.class,
            FabricMaterialDefinition.class,
            PigmentDefinition.class,
            MaterialPalette.class,
            MaterialPaletteEntry.class,
            MountDefinition.class,
            PlacementProfile.class,
            BannerInstanceState.class,
            DyeTubState.class,
            DyeResult.class,
            MatchType.class);

    static Stream<Arguments> allContracts() {
        return Stream.of(
                Arguments.of("banner definition", BannerDefinition.CODEC, CoreDataFixtures.bannerDefinition()),
                Arguments.of("banner dimensions", BannerDimensions.CODEC, CoreDataFixtures.dimensions()),
                Arguments.of("banner assets", BannerAssets.CODEC, CoreDataFixtures.assets()),
                Arguments.of("source reference", BannerSourceReference.CODEC, CoreDataFixtures.sourceReference()),
                Arguments.of("fabric material", FabricMaterialDefinition.CODEC, CoreDataFixtures.fabricMaterial()),
                Arguments.of("pigment", PigmentDefinition.CODEC, CoreDataFixtures.pigment()),
                Arguments.of("material palette", MaterialPalette.CODEC, CoreDataFixtures.palette()),
                Arguments.of("palette entry", MaterialPaletteEntry.CODEC, CoreDataFixtures.dyedEntry()),
                Arguments.of("mount", MountDefinition.CODEC, CoreDataFixtures.mount()),
                Arguments.of("placement profile", PlacementProfile.CODEC, CoreDataFixtures.placement()),
                Arguments.of("banner instance", BannerInstanceState.CODEC, CoreDataFixtures.dyedBannerState()),
                Arguments.of("dye tub", DyeTubState.CODEC, CoreDataFixtures.finiteTub()),
                Arguments.of("dye result", DyeResult.CODEC, CoreDataFixtures.dyeResult()),
                Arguments.of("match type", MatchType.CODEC, MatchType.NEAREST_COLOUR));
    }

    static Stream<Arguments> requiredFields() {
        return Stream.of(
                Arguments.of("banner definition", BannerDefinition.CODEC, CoreDataFixtures.bannerDefinition(), "id"),
                Arguments.of("banner dimensions", BannerDimensions.CODEC, CoreDataFixtures.dimensions(), "width_blocks"),
                Arguments.of("banner assets", BannerAssets.CODEC, CoreDataFixtures.assets(), "geometry"),
                Arguments.of("source reference", BannerSourceReference.CODEC,
                        CoreDataFixtures.sourceReference(), "page"),
                Arguments.of("fabric material", FabricMaterialDefinition.CODEC,
                        CoreDataFixtures.fabricMaterial(), "natural_colour_id"),
                Arguments.of("pigment", PigmentDefinition.CODEC, CoreDataFixtures.pigment(), "reference_srgb"),
                Arguments.of("material palette", MaterialPalette.CODEC, CoreDataFixtures.palette(), "entries"),
                Arguments.of("palette entry", MaterialPaletteEntry.CODEC,
                        CoreDataFixtures.dyedEntry(), "display_srgb"),
                Arguments.of("mount", MountDefinition.CODEC, CoreDataFixtures.mount(), "texture"),
                Arguments.of("placement profile", PlacementProfile.CODEC,
                        CoreDataFixtures.placement(), "dimensions"),
                Arguments.of("banner instance", BannerInstanceState.CODEC,
                        CoreDataFixtures.dyedBannerState(), "material_id"),
                Arguments.of("dye tub", DyeTubState.CODEC, CoreDataFixtures.finiteTub(), "schema_version"),
                Arguments.of("dye result", DyeResult.CODEC,
                        CoreDataFixtures.dyeResult(), "resolved_colour_id"));
    }

    static Stream<Arguments> minimalContracts() {
        return Stream.of(
                Arguments.of("banner definition", BannerDefinition.CODEC,
                        CoreDataFixtures.minimalBannerDefinition()),
                Arguments.of("banner dimensions", BannerDimensions.CODEC, CoreDataFixtures.dimensions()),
                Arguments.of("banner assets", BannerAssets.CODEC, CoreDataFixtures.assets()),
                Arguments.of("source reference", BannerSourceReference.CODEC,
                        CoreDataFixtures.minimalSourceReference()),
                Arguments.of("fabric material", FabricMaterialDefinition.CODEC,
                        CoreDataFixtures.minimalFabricMaterial()),
                Arguments.of("pigment", PigmentDefinition.CODEC, CoreDataFixtures.minimalPigment()),
                Arguments.of("material palette", MaterialPalette.CODEC, CoreDataFixtures.minimalPalette()),
                Arguments.of("palette entry", MaterialPaletteEntry.CODEC, CoreDataFixtures.minimalEntry()),
                Arguments.of("mount", MountDefinition.CODEC, CoreDataFixtures.minimalMount()),
                Arguments.of("placement profile", PlacementProfile.CODEC, CoreDataFixtures.placement()),
                Arguments.of("banner instance", BannerInstanceState.CODEC,
                        CoreDataFixtures.naturalBannerState()),
                Arguments.of("dye tub", DyeTubState.CODEC, CoreDataFixtures.emptyTub()),
                Arguments.of("dye result", DyeResult.CODEC, CoreDataFixtures.dyeResult()),
                Arguments.of("match type", MatchType.CODEC, MatchType.NATURAL));
    }

    static Stream<Arguments> versionedContracts() {
        return Stream.of(
                Arguments.of("banner definition", BannerDefinition.CODEC, CoreDataFixtures.bannerDefinition()),
                Arguments.of("fabric material", FabricMaterialDefinition.CODEC, CoreDataFixtures.fabricMaterial()),
                Arguments.of("pigment", PigmentDefinition.CODEC, CoreDataFixtures.pigment()),
                Arguments.of("material palette", MaterialPalette.CODEC, CoreDataFixtures.palette()),
                Arguments.of("mount", MountDefinition.CODEC, CoreDataFixtures.mount()),
                Arguments.of("placement profile", PlacementProfile.CODEC, CoreDataFixtures.placement()),
                Arguments.of("banner instance", BannerInstanceState.CODEC, CoreDataFixtures.dyedBannerState()),
                Arguments.of("dye tub", DyeTubState.CODEC, CoreDataFixtures.finiteTub()));
    }

    @ParameterizedTest(name = "{0} persistent codec round-trips")
    @MethodSource("allContracts")
    void persistentCodecRoundTrips(String name, Codec<Object> codec, Object value) {
        JsonElement encoded = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, codec.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @ParameterizedTest(name = "{0} has a minimal valid fixture")
    @MethodSource("minimalContracts")
    void minimalValidFixturesRoundTrip(String name, Codec<Object> codec, Object value) {
        JsonElement encoded = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, codec.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @ParameterizedTest(name = "{0} rejects missing required field")
    @MethodSource("requiredFields")
    void missingRequiredFieldsAreRejected(String name, Codec<Object> codec, Object value, String field) {
        JsonObject encoded = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject();
        encoded.remove(field);
        DataResult<Object> result = codec.parse(JsonOps.INSTANCE, encoded);
        assertTrue(result.error().isPresent(), () -> name + " accepted missing " + field);
        assertTrue(result.error().orElseThrow().message().contains(field));
    }

    @ParameterizedTest(name = "{0} rejects future schema")
    @MethodSource("versionedContracts")
    void futureSchemaVersionsAreRejected(String name, Codec<Object> codec, Object value) {
        JsonObject encoded = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject();
        encoded.addProperty("schema_version", SCHEMA + 1);
        DataResult<Object> result = codec.parse(JsonOps.INSTANCE, encoded);
        assertTrue(result.error().isPresent(), () -> name + " accepted a future schema");
        assertTrue(result.error().orElseThrow().message().contains("Unsupported schema_version"));
    }

    @Test
    void optionalSourceLabelHasMinimalAndFullRoundTrips() {
        assertRoundTrip(BannerSourceReference.CODEC, CoreDataFixtures.minimalSourceReference());
        assertRoundTrip(BannerSourceReference.CODEC, CoreDataFixtures.sourceReference());
        assertEquals("Source Sheet Label", CoreDataFixtures.sourceReference().sourceLabel().orElseThrow());
    }

    @Test
    void bannerInstanceSupportsNaturalAndDyedStates() {
        BannerInstanceState natural = assertRoundTrip(
                BannerInstanceState.CODEC, CoreDataFixtures.naturalBannerState());
        BannerInstanceState dyed = assertRoundTrip(BannerInstanceState.CODEC, CoreDataFixtures.dyedBannerState());
        assertTrue(natural.sourcePigmentId().isEmpty());
        assertEquals(CoreDataFixtures.PIGMENT_ID, dyed.sourcePigmentId().orElseThrow());
        assertStreamRoundTrip(BannerInstanceState.STREAM_CODEC, natural);
        assertStreamRoundTrip(BannerInstanceState.STREAM_CODEC, dyed);
    }

    @Test
    void dyeTubStatesUseCanonicalUnlimitedRepresentation() {
        DyeTubState empty = assertRoundTrip(DyeTubState.CODEC, CoreDataFixtures.emptyTub());
        DyeTubState unlimited = assertRoundTrip(DyeTubState.CODEC, CoreDataFixtures.unlimitedTub());
        DyeTubState finite = assertRoundTrip(DyeTubState.CODEC, CoreDataFixtures.finiteTub());

        assertTrue(empty.pigmentId().isEmpty());
        assertTrue(empty.remainingUses().isEmpty());
        assertTrue(unlimited.remainingUses().isEmpty());
        assertEquals(12, finite.remainingUses().orElseThrow());
        assertStreamRoundTrip(DyeTubState.STREAM_CODEC, empty);
        assertStreamRoundTrip(DyeTubState.STREAM_CODEC, unlimited);
        assertStreamRoundTrip(DyeTubState.STREAM_CODEC, finite);
    }

    @Test
    void stableMatchTypeNamesDoNotUseOrdinals() {
        assertStableEnum(MatchType.CODEC, MatchType.EXPLICIT_MAPPING, "explicit_mapping");
        assertStableEnum(MatchType.CODEC, MatchType.NEAREST_COLOUR, "nearest_colour");
        assertStableEnum(MatchType.CODEC, MatchType.NATURAL, "natural");
        assertTrue(MatchType.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("1")).error().isPresent());
    }

    @Test
    void stableContentStatusNamesDoNotUseOrdinals() {
        assertStableEnum(BannerContentStatus.CODEC, BannerContentStatus.PLACEHOLDER, "placeholder");
        assertStableEnum(BannerContentStatus.CODEC, BannerContentStatus.IN_PROGRESS, "in_progress");
        assertStableEnum(BannerContentStatus.CODEC, BannerContentStatus.COMPLETE, "complete");
        assertTrue(BannerContentStatus.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("draft")).error().isPresent());
    }

    @Test
    void decodingDoesNotRequireLiveRegistryMembership() {
        JsonObject json = BannerInstanceState.CODEC.encodeStart(
                JsonOps.INSTANCE, CoreDataFixtures.naturalBannerState()).getOrThrow().getAsJsonObject();
        json.addProperty("banner_definition_id", "unregistered_namespace:missing_banner");
        json.addProperty("material_id", "unregistered_namespace:missing_material");
        json.addProperty("resolved_colour_id", "unregistered_namespace:missing_colour");
        json.addProperty("mount_id", "unregistered_namespace:missing_mount");

        BannerInstanceState decoded = BannerInstanceState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals("unregistered_namespace:missing_banner", decoded.bannerDefinitionId().toString());
    }

    @Test
    void commonDataClassFilesDoNotReferenceClientPackages() throws IOException {
        for (Class<?> commonClass : COMMON_DATA_CLASSES) {
            String resourceName = "/" + commonClass.getName().replace('.', '/') + ".class";
            try (InputStream stream = commonClass.getResourceAsStream(resourceName)) {
                assertNotNull(stream, "Missing class resource for " + commonClass.getName());
                String constantPool = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(constantPool.contains("net/minecraft/client/"),
                        () -> commonClass.getName() + " references a client-only Minecraft class");
                assertFalse(constantPool.contains("com/mojang/blaze3d/"),
                        () -> commonClass.getName() + " references a client-only rendering class");
            }
        }
    }

    private static <T> T assertRoundTrip(Codec<T> codec, T value) {
        JsonElement encoded = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        T decoded = codec.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(value, decoded);
        return decoded;
    }

    private static <T> void assertStreamRoundTrip(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            codec.encode(buffer, value);
            assertEquals(value, codec.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static <T> void assertStableEnum(Codec<T> codec, T value, String stableName) {
        JsonElement encoded = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(stableName, encoded.getAsString());
        assertEquals(value, codec.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }
}
