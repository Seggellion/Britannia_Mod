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
 * Rowan farming questline, Milestone 0: the executable half of the integration contract.
 *
 * <p>The fixtures under {@code src/test/resources/quest_contract/v1/} are mirrored byte-for-byte
 * (after LF normalisation) in the Rails repository under {@code test/fixtures/quest_contract/v1/},
 * and both repositories check the same {@code MANIFEST.sha256}. A fixture edited on one side
 * without re-mirroring fails this test on the other, which is the whole point: the protocol
 * document is frozen, and the fixtures are the only shape either side may implement against.
 *
 * <p>The later milestones (M2-M5) parse these same files through the real request/response
 * codecs; this test only proves the mirror is intact and every fixture is well-formed JSON with
 * the contract version it claims.
 */
class QuestContractFixturesTest {
    static final String RESOURCE_ROOT = "quest_contract/v1";
    static final String MANIFEST = "MANIFEST.sha256";
    static final int PROTOCOL_VERSION = 1;

    /** The frozen fixture index (protocol document section 7). */
    static final List<String> EXPECTED_FIXTURES = List.of(
            "transition_response_with_delivery.json",
            "pending_deliveries_response.json",
            "delivery_result_request.json",
            "delivery_result_response.json",
            "action_event_request_crop_harvest.json",
            "action_event_response_applied.json",
            "action_event_response_duplicate.json",
            "action_event_response_irrelevant.json",
            "action_event_response_stale.json",
            "action_event_response_rejected.json",
            "handin_result_request.json",
            "handin_result_response_consumed.json",
            "handin_result_response_cancelled_refunded.json",
            "journal_entry_stage5.json",
            "node_metadata_stage5.json");

    @Test
    void manifestListsExactlyTheFrozenFixtureIndex() {
        Map<String, String> manifest = readManifest();
        assertEquals(new TreeSet<>(EXPECTED_FIXTURES), new TreeSet<>(manifest.keySet()),
                "MANIFEST.sha256 must list exactly the fixtures named in the protocol document");
    }

    @Test
    void everyFileInTheFixtureDirectoryIsListedInTheManifest() throws IOException {
        Set<String> onDisk;
        try (Stream<Path> files = Files.list(fixtureDirectory())) {
            onDisk = files.map(path -> path.getFileName().toString())
                    .filter(name -> !name.equals(MANIFEST))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
        assertEquals(new TreeSet<>(readManifest().keySet()), onDisk,
                "an unlisted fixture is invisible to the Rails mirror check");
    }

    @TestFactory
    Stream<DynamicTest> fixturesMatchTheMirroredManifestDigests() {
        return readManifest().entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
            String actual = sha256Hex(normalise(readFixture(entry.getKey())));
            assertEquals(entry.getValue(), actual, () -> "fixture " + entry.getKey()
                    + " drifted from MANIFEST.sha256; re-mirror it in both repositories and regenerate the manifest");
        }));
    }

    @TestFactory
    Stream<DynamicTest> fixturesAreWellFormedAndCarryProtocolVersionOne() {
        return EXPECTED_FIXTURES.stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            JsonElement parsed = JsonParser.parseString(normalise(readFixture(name)));
            assertTrue(parsed.isJsonObject(), name + " must be a JSON object");
            JsonObject object = parsed.getAsJsonObject();
            if (object.has("protocol_version")) {
                assertEquals(PROTOCOL_VERSION, object.get("protocol_version").getAsInt(),
                        name + " must carry protocol_version " + PROTOCOL_VERSION);
            }
            if (object.has("reward_delivery") && object.get("reward_delivery").isJsonObject()) {
                assertEquals(PROTOCOL_VERSION,
                        object.getAsJsonObject("reward_delivery").get("protocol_version").getAsInt(),
                        name + ".reward_delivery must carry protocol_version " + PROTOCOL_VERSION);
            }
        }));
    }

    /**
     * M11: the versions the <b>code</b> sends and accepts, checked against the frozen fixtures
     * rather than against each other.
     *
     * <p>Everything above proves the fixtures say {@code protocol_version: 1}. That is only half of
     * a compatibility check: a constant bumped in the mod would keep every fixture assertion green
     * while the mod posted a body Rails answers 400 to forever, and a parser that stopped enforcing
     * the version would accept a v2 envelope it cannot read. Both constants are therefore pinned to
     * the fixture value, and both parsers are shown refusing anything else.
     */
    @Test
    void theVersionsTheCodeSendsAndAcceptsMatchTheFrozenFixtures() {
        assertEquals(PROTOCOL_VERSION,
                com.seggellion.britannia_mod.quest.action.QuestActionEventProtocol.PROTOCOL_VERSION,
                "the action-event encoder would post a version the frozen contract does not describe");
        assertEquals(PROTOCOL_VERSION,
                com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryProtocol.PROTOCOL_VERSION,
                "the delivery protocol would post a version the frozen contract does not describe");

        // Every fixture that declares a version declares the one the code uses.
        for (String name : EXPECTED_FIXTURES) {
            JsonObject object = JsonParser.parseString(normalise(readFixture(name))).getAsJsonObject();
            if (!object.has("protocol_version")) continue;
            assertEquals(
                    com.seggellion.britannia_mod.quest.action.QuestActionEventProtocol.PROTOCOL_VERSION,
                    object.get("protocol_version").getAsInt(),
                    name + " and the code disagree about the protocol version");
        }

        // And the accepting half: a version the code does not implement is refused, not guessed at.
        String v2 = normalise(readFixture("action_event_response_applied.json"))
                .replace("\"protocol_version\": 1", "\"protocol_version\": 2");
        org.junit.jupiter.api.Assertions.assertThrows(
                com.seggellion.britannia_mod.quest.action.QuestActionEventProtocol
                        .MalformedActionEventException.class,
                () -> com.seggellion.britannia_mod.quest.action.QuestActionEventProtocol
                        .parse(v2.getBytes(StandardCharsets.UTF_8)),
                "the action-event parser accepted an envelope from a protocol it does not implement");
    }

    /**
     * The frozen document and the frozen directory agree about which version this is.
     *
     * <p>{@code quest_contract/v1} is a path, and a path is easy to leave behind: bumping the
     * protocol without moving the fixtures would leave both repositories mirroring v1 files under a
     * v2 contract and nothing would say so.
     */
    @Test
    void theProtocolDocumentDescribesTheVersionTheseFixturesAre() throws IOException {
        Path document = Path.of(System.getProperty("britannia.projectDir", "."))
                .resolve("docs/projects/rowan-farming-questline/ROWAN_FARMING_QUESTLINE_PROTOCOL.md");
        assertTrue(Files.isRegularFile(document), "the frozen protocol document is missing");
        String text = Files.readString(document, StandardCharsets.UTF_8);
        assertTrue(text.contains(RESOURCE_ROOT),
                "the protocol document does not name " + RESOURCE_ROOT + ", so the fixtures this "
                        + "test verifies may belong to a different version of the contract");
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
        URL root = QuestContractFixturesTest.class.getClassLoader().getResource(RESOURCE_ROOT);
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
