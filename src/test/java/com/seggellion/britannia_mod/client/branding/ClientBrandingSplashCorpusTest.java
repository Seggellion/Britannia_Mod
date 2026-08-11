package com.seggellion.britannia_mod.client.branding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClientBrandingSplashCorpusTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path SPLASHES =
            PROJECT.resolve("src/main/resources/assets/minecraft/texts/splashes.txt");
    private static final Path VANILLA_HASHES =
            PROJECT.resolve("src/test/resources/client-branding-vanilla-splash-hashes.txt");

    @Test
    void corpusIsStrictUtf8AndWithinTheApprovedSizeRange() throws Exception {
        byte[] bytes = Files.readAllBytes(SPLASHES);
        assertFalse(startsWithUtf8Bom(bytes), "Splash resource must not contain a UTF-8 BOM");

        String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        List<String> lines = text.lines().toList();

        assertTrue(lines.size() >= 120, "Corpus must contain at least 120 accepted lines");
        assertTrue(lines.size() <= 180, "Corpus must contain at most 180 accepted lines");
    }

    @Test
    void everyLineIsTrimmedReadableAndUnique() throws Exception {
        List<String> lines = Files.readAllLines(SPLASHES, StandardCharsets.UTF_8);
        Set<String> caseInsensitive = new HashSet<>();
        Set<String> punctuationInsensitive = new HashSet<>();

        for (String line : lines) {
            assertFalse(line.isBlank(), "Corpus must not contain empty lines");
            assertEquals(line.strip(), line, () -> "Leading or trailing whitespace: " + line);
            assertTrue(line.codePointCount(0, line.length()) <= 55, () -> "Line exceeds ideal limit: " + line);
            assertTrue(line.codePoints().noneMatch(Character::isISOControl), () -> "Control character: " + line);
            assertFalse(canonical(line).isEmpty(), () -> "Line must contain a letter or digit: " + line);
            assertTrue(caseInsensitive.add(line.toLowerCase(Locale.ROOT)), () -> "Duplicate line: " + line);
            assertTrue(punctuationInsensitive.add(canonical(line)), () -> "Near-duplicate line: " + line);
        }
    }

    @Test
    void corpusContainsNoVanilla1211Splash() throws Exception {
        Set<String> vanillaHashes = new HashSet<>(Files.readAllLines(VANILLA_HASHES, StandardCharsets.US_ASCII));
        assertEquals(441, vanillaHashes.size(), "Vanilla 1.21.1 hash fixture must cover every canonical line");

        for (String line : Files.readAllLines(SPLASHES, StandardCharsets.UTF_8)) {
            assertFalse(vanillaHashes.contains(hashPrefix(canonical(line))), () -> "Vanilla splash leaked: " + line);
        }
    }

    private static boolean startsWithUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF;
    }

    private static String canonical(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String hashPrefix(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest, 0, 8);
    }
}
