package com.seggellion.britannia_mod.bannerdyeing.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.MountDefinition;
import com.seggellion.britannia_mod.banner.data.PlacementProfile;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.CoreDataFixtures;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.network.payload.banner.S2CBannerRegistrySyncPayload;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * The registry-sync payload must reproduce a snapshot exactly: a remote client's
 * {@code BannerDataRegistries} content is whatever survives this round trip, and every
 * client-side banner feature (creative tab population, tooltips, dye previews) validates
 * against it. Runs once against fixture content and once against the full production data
 * files parsed with the same codecs the datapack reload uses.
 */
class BannerRegistrySyncPayloadTest {

    @Test
    void fixtureSnapshotSurvivesWireRoundTripExactly() {
        RegistrySnapshot original = BannerRegistryClientSync.fromActiveDefinitions(
                List.of(CoreDataFixtures.bannerDefinition()),
                List.of(CoreDataFixtures.fabricMaterial()),
                List.of(CoreDataFixtures.pigment()),
                List.of(CoreDataFixtures.palette()),
                List.of(CoreDataFixtures.mount()),
                List.of(CoreDataFixtures.placement()));

        RegistrySnapshot decoded = roundTrip(original);

        assertSnapshotContentEquals(original, decoded);
    }

    @Test
    void fullProductionDataSurvivesWireRoundTripExactly() {
        RegistrySnapshot original = BannerRegistryClientSync.fromActiveDefinitions(
                parseAll("banner_definitions", BannerDefinition.CODEC),
                parseAll("fabric_materials", FabricMaterialDefinition.CODEC),
                parseAll("pigments", PigmentDefinition.CODEC),
                parseAll("material_palettes", MaterialPalette.CODEC),
                parseAll("banner_mounts", MountDefinition.CODEC),
                parseAll("placement_profiles", PlacementProfile.CODEC));
        assertTrue(original.banners().activeCount() >= 35,
                "expected the full production banner catalogue on the test classpath, found "
                        + original.banners().activeCount());

        RegistrySnapshot decoded = roundTrip(original);

        assertSnapshotContentEquals(original, decoded);
    }

    @Test
    void unpublishedPayloadCarriesEmptySnapshot() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        S2CBannerRegistrySyncPayload.STREAM_CODEC.encode(buffer,
                new S2CBannerRegistrySyncPayload(false, RegistrySnapshot.empty()));
        S2CBannerRegistrySyncPayload decoded = S2CBannerRegistrySyncPayload.STREAM_CODEC.decode(buffer);
        assertEquals(false, decoded.published());
        assertEquals(0, decoded.snapshot().banners().activeCount());
    }

    private static RegistrySnapshot roundTrip(RegistrySnapshot original) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        S2CBannerRegistrySyncPayload.STREAM_CODEC.encode(buffer,
                new S2CBannerRegistrySyncPayload(true, original));
        assertTrue(buffer.writerIndex() <= S2CBannerRegistrySyncPayload.MAX_ENCODED_BYTES,
                "encoded size " + buffer.writerIndex() + " exceeds payload ceiling");
        S2CBannerRegistrySyncPayload decoded = S2CBannerRegistrySyncPayload.STREAM_CODEC.decode(buffer);
        assertEquals(true, decoded.published());
        assertEquals(0, buffer.readableBytes(), "payload left undrained bytes on the wire");
        return decoded.snapshot();
    }

    /**
     * Definition records implement value equality, so comparing the active definition lists
     * compares every field of every synced definition.
     */
    private static void assertSnapshotContentEquals(RegistrySnapshot original, RegistrySnapshot decoded) {
        assertEquals(original.banners().activeDefinitions(), decoded.banners().activeDefinitions());
        assertEquals(original.fabricMaterials().activeDefinitions(),
                decoded.fabricMaterials().activeDefinitions());
        assertEquals(original.pigments().activeDefinitions(), decoded.pigments().activeDefinitions());
        assertEquals(original.materialPalettes().activeDefinitions(),
                decoded.materialPalettes().activeDefinitions());
        assertEquals(original.mounts().activeDefinitions(), decoded.mounts().activeDefinitions());
        assertEquals(original.placementProfiles().activeDefinitions(),
                decoded.placementProfiles().activeDefinitions());
    }

    /**
     * Parses every real data file in one domain directory, with the same codec the datapack
     * reload uses. The directory is resolved through the classpath (the test runtime sees main
     * resources as an exploded directory), so the test does not depend on the worker's working
     * directory.
     */
    private static <T> List<T> parseAll(String domainDirectory, Codec<T> codec) {
        java.net.URL url = BannerRegistrySyncPayloadTest.class.getClassLoader()
                .getResource("data/britannia_mod/" + domainDirectory);
        assertNotNull(url, "data directory not on test classpath: " + domainDirectory);
        Path directory;
        try {
            directory = Path.of(url.toURI());
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
        assertTrue(Files.isDirectory(directory), "missing data directory " + directory);
        List<T> definitions = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(file -> file.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(file -> definitions.add(parse(file, codec)));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        assertTrue(!definitions.isEmpty(), "no definitions parsed from " + directory);
        return definitions;
    }

    private static <T> T parse(Path file, Codec<T> codec) {
        try (InputStream stream = Files.newInputStream(file)) {
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            var result = codec.parse(JsonOps.INSTANCE, json);
            T parsed = result.getOrThrow(message -> new IllegalStateException(
                    file + " failed to parse: " + message));
            assertNotNull(parsed);
            return parsed;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
