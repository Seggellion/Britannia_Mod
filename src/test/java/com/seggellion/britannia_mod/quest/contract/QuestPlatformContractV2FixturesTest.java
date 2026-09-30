package com.seggellion.britannia_mod.quest.contract;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Executable integrity check for the mirrored quest platform v2 fixtures.
 *
 * <p>This test proves only that the checked-in package is the frozen Rails package: the file index,
 * manifest digests, JSON syntax, and declared contract versions. Protocol behaviour belongs to later
 * implementation milestones and is deliberately outside this test.
 */
class QuestPlatformContractV2FixturesTest {
    static final String RESOURCE_ROOT = "quest_platform_contract/v2";
    static final String MANIFEST = "MANIFEST.sha256";
    static final int CONTRACT_VERSION = 2;

    /** The frozen fixture index, in manifest order. */
    static final List<String> EXPECTED_FIXTURES = List.of(
            "capability_matrix.json",
            "client_projection.json",
            "command_replay.json",
            "new_run_pins.json",
            "observation_replay.json");

    @Test
    void manifestListsExactlyTheFrozenFixtureIndexInOrder() {
        assertEquals(EXPECTED_FIXTURES, new ArrayList<>(readManifest().keySet()),
                "MANIFEST.sha256 must list exactly the frozen fixtures in their recorded order");
    }

    @Test
    void fixtureDirectoryContainsOnlyTheManifestAndFrozenFixtureIndex() throws IOException {
        Set<String> expectedFiles = new TreeSet<>(EXPECTED_FIXTURES);
        expectedFiles.add(MANIFEST);

        Set<String> onDisk;
        try (Stream<Path> files = Files.list(fixtureDirectory())) {
            onDisk = files.map(path -> path.getFileName().toString())
                    .collect(Collectors.toCollection(TreeSet::new));
        }
        assertEquals(expectedFiles, onDisk,
                "the resource root must contain only MANIFEST.sha256 and its frozen fixture index");
    }

    @TestFactory
    Stream<DynamicTest> fixturesMatchTheMirroredManifestDigests() {
        return readManifest().entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
            String actual = sha256Hex(normalise(readFixture(entry.getKey())));
            assertEquals(entry.getValue(), actual, () -> "fixture " + entry.getKey()
                    + " drifted from the mirrored MANIFEST.sha256");
        }));
    }

    @TestFactory
    Stream<DynamicTest> fixturesAreWellFormedJsonAndDeclareOnlyContractVersionTwo() {
        return EXPECTED_FIXTURES.stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            JsonElement parsed = JsonParser.parseString(normalise(readFixture(name)));
            assertContractVersions(parsed, name);
        }));
    }

    static void assertContractVersions(JsonElement element, String location) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String childLocation = location + "." + entry.getKey();
                if (entry.getKey().equals("contract_version")) {
                    assertTrue(entry.getValue().isJsonPrimitive()
                                    && entry.getValue().getAsJsonPrimitive().isNumber(),
                            childLocation + " must be the number " + CONTRACT_VERSION);
                    assertEquals(Integer.toString(CONTRACT_VERSION), entry.getValue().toString(),
                            childLocation + " must declare contract version " + CONTRACT_VERSION);
                }
                assertContractVersions(entry.getValue(), childLocation);
            }
        } else if (element.isJsonArray()) {
            for (int index = 0; index < element.getAsJsonArray().size(); index++) {
                assertContractVersions(element.getAsJsonArray().get(index), location + "[" + index + "]");
            }
        }
    }

    static Map<String, String> readManifest() {
        Map<String, String> entries = new LinkedHashMap<>();
        for (String line : normalise(readFixture(MANIFEST)).split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int split = trimmed.indexOf(' ');
            if (split != 64) {
                fail("malformed manifest line: '" + trimmed + "'");
            }
            String digest = trimmed.substring(0, 64);
            String name = trimmed.substring(split).trim();
            if (name.startsWith("*")) {
                name = name.substring(1);
            }
            if (entries.put(name, digest) != null) {
                fail("duplicate manifest entry: " + name);
            }
        }
        assertTrue(!entries.isEmpty(), "MANIFEST.sha256 is empty");
        return entries;
    }

    static String normalise(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    static byte[] readFixture(String name) {
        try {
            return Files.readAllBytes(fixtureDirectory().resolve(name));
        } catch (IOException failure) {
            throw new UncheckedIOException("missing checked-in fixture " + RESOURCE_ROOT + "/" + name, failure);
        }
    }

    static Path fixtureDirectory() {
        URL root = QuestPlatformContractV2FixturesTest.class.getClassLoader().getResource(RESOURCE_ROOT);
        if (root == null) {
            fail("missing checked-in fixture directory: " + RESOURCE_ROOT);
        }
        try {
            return Path.of(root.toURI());
        } catch (URISyntaxException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
