package com.seggellion.britannia_mod.structure;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 3: every house ships twice, in two different formats, and both have to be right.
 *
 * <pre>
 *   assets/britannia_mod/structures/&lt;name&gt;.nbt   gzip (1f 8b)   client ghost preview
 *   data/britannia_mod/structures/&lt;name&gt;.nbt     raw  (0a 00)   server placement
 * </pre>
 *
 * <p>The failure this exists to catch does not throw. {@code ClientEventHandler} reads the
 * client copy with {@code NbtIo.readCompressed}; hand it a raw file, or no file, and the ghost
 * preview simply does not draw. A player aims a deed at the ground and sees nothing, and the
 * server places the house anyway from the other copy. Nothing in the log says why.
 *
 * <p>Regenerate both copies with {@code python tools/ship_structures.py}, which copies the
 * export to {@code assets/} byte for byte and gunzips it into {@code data/}.
 */
class HouseStructureShippingTest {

    private static final Path RESOURCES = Path.of(System.getProperty("britannia.projectDir", "."))
            .resolve("src/main/resources");
    private static final Path CLIENT_COPIES = RESOURCES.resolve("assets/britannia_mod/structures");
    private static final Path SERVER_COPIES = RESOURCES.resolve("data/britannia_mod/structures");

    private static final byte[] GZIP_MAGIC = { 0x1f, (byte) 0x8b };
    private static final byte[] RAW_NBT_MAGIC = { 0x0a, 0x00 };

    @Test
    void everyRegisteredHouseShipsBothCopiesInTheRightFormat() throws IOException {
        for (HouseStyle style : HouseStyle.values()) {
            Path client = CLIENT_COPIES.resolve(style.getStructureFile());
            Path server = SERVER_COPIES.resolve(style.getStructureFile());

            assertTrue(Files.exists(client),
                    style + " has no client copy at " + client + ". Its ghost preview would fail "
                            + "to draw with nothing in the log.");
            assertTrue(Files.exists(server),
                    style + " has no server copy at " + server + ". Placement would fail.");

            assertTrue(isGzip(Files.readAllBytes(client)),
                    style + "'s client copy is not gzip. NbtIo.readCompressed will not read it and "
                            + "the ghost preview will silently do nothing.");
            assertTrue(isRawNbt(Files.readAllBytes(server)),
                    style + "'s server copy is not raw NBT. NbtIo.read will not read it.");
        }
    }

    @Test
    void thetwoCopiesAreTheSameStructure() throws IOException {
        for (HouseStyle style : HouseStyle.values()) {
            byte[] client = Files.readAllBytes(CLIENT_COPIES.resolve(style.getStructureFile()));
            byte[] server = Files.readAllBytes(SERVER_COPIES.resolve(style.getStructureFile()));

            assertArrayEquals(gunzip(client), server,
                    style + " ships two different structures: the server copy is not the client "
                            + "copy unpacked. A player would preview one house and place another.");
        }
    }

    /**
     * The three larger houses are shipped but not yet registered as styles, so the loop above
     * does not reach them. They are held to the same rule here so Milestone 4 inherits files
     * that are already correct.
     */
    @Test
    void theUnregisteredLargerHousesAreShippedCorrectlyToo() throws IOException {
        List<String> pending = new ArrayList<>();
        for (String stub : List.of("two_story_villa", "large_patio", "stone_keep")) {
            Path client = CLIENT_COPIES.resolve(stub + ".nbt");
            Path server = SERVER_COPIES.resolve(stub + ".nbt");
            if (!Files.exists(client) || !Files.exists(server)) {
                pending.add(stub);
                continue;
            }
            assertTrue(isGzip(Files.readAllBytes(client)), stub + " client copy is not gzip");
            assertTrue(isRawNbt(Files.readAllBytes(server)), stub + " server copy is not raw NBT");
            assertArrayEquals(gunzip(Files.readAllBytes(client)), Files.readAllBytes(server),
                    stub + " ships two different structures");
        }
        assertTrue(pending.isEmpty(), "not shipped: " + pending + " -- run tools/ship_structures.py");
    }

    @Test
    void everyShippedClientCopyHasAServerCounterpartAndViceVersa() throws IOException {
        assertEquals(names(CLIENT_COPIES), names(SERVER_COPIES),
                "the two structure folders have drifted apart; one copy of some house is missing");
    }

    /**
     * The format check has to fail on the things that actually go wrong, or its passing means
     * nothing. These are the two mistakes the pipeline is there to prevent: shipping the raw
     * file to the client, and shipping the compressed one to the server.
     */
    @Test
    void theFormatCheckRejectsAMisCompressedCopy() throws IOException {
        byte[] raw = Files.readAllBytes(SERVER_COPIES.resolve(HouseStyle.SMALL_BRICK.getStructureFile()));
        byte[] compressed = Files.readAllBytes(CLIENT_COPIES.resolve(HouseStyle.SMALL_BRICK.getStructureFile()));

        assertFalse(isGzip(raw), "a raw copy must not pass the client-side check");
        assertFalse(isRawNbt(compressed), "a compressed copy must not pass the server-side check");

        assertFalse(isGzip(new byte[0]), "an empty file must not pass as gzip");
        assertFalse(isRawNbt(new byte[0]), "an empty file must not pass as raw NBT");
        assertFalse(isGzip(new byte[] { 0x1f }), "a one-byte file must not pass as gzip");

        // Worth recording, because it is the opposite of what you would assume: a fresh
        // GZIPOutputStream over the server copy does currently reproduce the committed client
        // copy byte for byte, because Java and Minecraft happen to agree on zlib defaults and a
        // zero mtime header. The pipeline copies the export anyway rather than lean on that --
        // an agreement between two compressors is not a guarantee, and a copy needs no argument.
        assertArrayEquals(compressed, gzip(raw),
                "Java and Minecraft gzip output no longer agree. Nothing is broken -- the pipeline "
                        + "copies rather than recompresses -- but the note above is now wrong.");
    }

    /* ------------------------------------------------------------------ */

    private static boolean isGzip(byte[] bytes) {
        return startsWith(bytes, GZIP_MAGIC);
    }

    private static boolean isRawNbt(byte[] bytes) {
        return startsWith(bytes, RAW_NBT_MAGIC);
    }

    private static boolean startsWith(byte[] bytes, byte[] magic) {
        if (bytes.length < magic.length) return false;
        for (int index = 0; index < magic.length; index++) {
            if (bytes[index] != magic[index]) return false;
        }
        return true;
    }

    private static byte[] gunzip(byte[] compressed) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return in.readAllBytes();
        }
    }

    private static byte[] gzip(byte[] plain) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(plain);
        }
        return out.toByteArray();
    }

    private static List<String> names(Path folder) throws IOException {
        try (var files = Files.list(folder)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".nbt"))
                    .sorted()
                    .toList();
        }
    }
}
