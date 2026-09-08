package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.block.entity.QuestGiverSpawnBlockEntity;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M1 (discovery D3): the server-side shape rules for a quest-giver spawn
 * configuration. The wire codec is unchanged -- a malformed payload still decodes -- and the
 * decision to apply it is made afterwards, from these rules and the sender's permissions.
 */
class QuestGiverSpawnConfigPayloadValidationTest {
    private static final BlockPos POS = new BlockPos(120, 64, -40);
    private static final Path SCREEN = Path.of(System.getProperty("britannia.projectDir", "."),
        "src/main/java/com/seggellion/britannia_mod/client/gui/QuestGiverSpawnScreen.java");

    @Test
    void everyArchetypeTheScreenOffersIsAcceptedInTheShapeTheScreenSends() {
        for (String archetype : QuestGiverSpawnBlockEntity.SUPPORTED_ARCHETYPES) {
            String apiId = "Generic Combat".equals(archetype) ? "guard_captain" : "";
            QuestGiverSpawnConfigC2SPayload payload =
                new QuestGiverSpawnConfigC2SPayload(POS, archetype, "Britain", apiId, "female", 5);
            assertEquals(Optional.empty(), payload.shapeViolation(), archetype);
            assertTrue(payload.isValidShape(), archetype);
        }
        assertTrue(new QuestGiverSpawnConfigC2SPayload(POS, "Iolo", "Skara Brae", "", "male", 1).isValidShape());
        assertTrue(new QuestGiverSpawnConfigC2SPayload(POS, "Iolo", "Serpent's Hold", "", "female", 64).isValidShape());
    }

    @Test
    void theServerListMirrorsTheScreenExactly() throws IOException {
        assertEquals(List.of("Zorathiel", "Lord British", "Iolo", "Dupre", "Shamino", "Rowan",
                "Generic Escort", "Generic Combat"),
            QuestGiverSpawnBlockEntity.SUPPORTED_ARCHETYPES,
            "adding an archetype is a deliberate change on the screen AND the server");

        String source = Files.readString(SCREEN, StandardCharsets.UTF_8);
        Matcher list = Pattern.compile("availableNpcs\\s*=\\s*List\\.of\\(([^)]*)\\)").matcher(source);
        assertTrue(list.find(), "QuestGiverSpawnScreen must declare availableNpcs = List.of(...)");
        List<String> screenArchetypes = new ArrayList<>();
        Matcher names = Pattern.compile("\"([^\"]+)\"").matcher(list.group(1));
        while (names.find()) screenArchetypes.add(names.group(1));
        assertEquals(screenArchetypes, QuestGiverSpawnBlockEntity.SUPPORTED_ARCHETYPES,
            "the screen's archetype list and the server's allow-list drifted apart");
    }

    @Test
    void anArchetypeTheSpawnerDoesNotKnowIsRefused() {
        assertEquals(Optional.of("archetype_unsupported"), violation("Blackthorn", "Britain", "", "female", 5));
        assertEquals(Optional.of("archetype_unsupported"), violation("iolo", "Britain", "", "female", 5));
        assertEquals(Optional.of("archetype_unsupported"), violation("Iolo ", "Britain", "", "female", 5));
        assertEquals(Optional.of("archetype_blank"), violation("", "Britain", "", "female", 5));
        assertEquals(Optional.of("archetype_blank"), violation("   ", "Britain", "", "female", 5));
        assertEquals(Optional.of("archetype_blank"), violation(null, "Britain", "", "female", 5));
        assertFalse(QuestGiverSpawnBlockEntity.supportsArchetype(null));
        assertFalse(QuestGiverSpawnBlockEntity.supportsArchetype("Blackthorn"));
        assertTrue(QuestGiverSpawnBlockEntity.supportsArchetype("Generic Escort"));
    }

    /**
     * M6. The archetype string IS the Rails {@code origin_npc}, so it is matched exactly: a near
     * miss must be refused rather than quietly configured into an NPC no quest points at.
     */
    @Test
    void rowanIsSupportedUnderExactlyOneSpelling() {
        assertEquals("Rowan", QuestGiverSpawnBlockEntity.ROWAN_ARCHETYPE);
        assertTrue(QuestGiverSpawnBlockEntity.supportsArchetype("Rowan"));
        assertEquals(Optional.empty(), violation("Rowan", "Britain", "", "female", 5));
        assertEquals(Optional.empty(), violation("Rowan", "Britain", "", "male", 12));

        for (String nearMiss : List.of("rowan", "ROWAN", "Rowan ", " Rowan", "Rowan the Farmer", "Rowan:farmer")) {
            assertEquals(Optional.of("archetype_unsupported"), violation(nearMiss, "Britain", "", "female", 5), nearMiss);
            assertFalse(QuestGiverSpawnBlockEntity.supportsArchetype(nearMiss), nearMiss);
        }
    }

    /**
     * M6. The hint is per-spawner presentation. It is bounded and printable because it is stored in
     * the block's NBT and logged, and it never touches the archetype the payload names.
     */
    @Test
    void theDirectionsHintIsOptionalBoundedAndPrintable() {
        assertEquals(Optional.empty(), directionsViolation(""), "no hint at all is the normal case");
        assertEquals(Optional.empty(), directionsViolation(null), "a null hint is read as none");
        assertEquals(Optional.empty(), directionsViolation("The water well is behind the mill, north gate."));
        assertEquals(Optional.empty(), directionsViolation("x".repeat(QuestGiverSpawnBlockEntity.MAX_DIRECTIONS_LENGTH)));
        assertEquals(Optional.of("directions_too_long"),
            directionsViolation("x".repeat(QuestGiverSpawnBlockEntity.MAX_DIRECTIONS_LENGTH + 1)));
        assertEquals(Optional.of("directions_control_characters"), directionsViolation("north\nthen east"));
        assertEquals(Optional.of("directions_control_characters"), directionsViolation("north\tthen east"));
        assertEquals(Optional.of("directions_control_characters"), directionsViolation("north" + (char) 27 + "[31m"));

        assertEquals("", new QuestGiverSpawnConfigC2SPayload(POS, "Rowan", "Britain", "", "female", 5, null).directions(),
            "the record itself normalises a missing hint, so no rule has to test for null");
    }

    /** The hint decides nothing about identity: both of these configure the same Rails NPC. */
    @Test
    void theDirectionsHintCannotChangeWhichNpcIsBeingConfigured() {
        QuestGiverSpawnConfigC2SPayload plain =
            new QuestGiverSpawnConfigC2SPayload(POS, "Rowan", "Britain", "", "female", 5);
        QuestGiverSpawnConfigC2SPayload hinted =
            new QuestGiverSpawnConfigC2SPayload(POS, "Rowan", "Minoc", "", "female", 5, "Well is by the north gate");

        assertTrue(plain.isValidShape());
        assertTrue(hinted.isValidShape());
        assertEquals("Rowan", plain.npcName());
        assertEquals("Rowan", hinted.npcName());
        assertEquals(plain.npcName(), hinted.npcName());
        assertEquals("", plain.directions());
        assertEquals("Well is by the north gate", hinted.directions());
    }

    /**
     * The value the block would keep, for every path that is not a packet: hand-edited NBT, or a
     * world carried across versions. The packet path refuses these instead, which is why both a
     * refusal and a sanitizer exist.
     */
    @Test
    void aHintFromOutsideThePacketPathIsSanitizedRatherThanRefused() {
        assertEquals("", QuestGiverSpawnBlockEntity.sanitizeDirections(null));
        assertEquals("", QuestGiverSpawnBlockEntity.sanitizeDirections("   "));
        assertEquals("north then east", QuestGiverSpawnBlockEntity.sanitizeDirections("  north then east  "));
        assertEquals("north then east", QuestGiverSpawnBlockEntity.sanitizeDirections("north\nthen\teast"),
            "a control character becomes a space, so the words do not run together");
        assertEquals("north then east", QuestGiverSpawnBlockEntity.sanitizeDirections("north   then \r\n east"),
            "the result is one line");
        assertEquals(QuestGiverSpawnBlockEntity.MAX_DIRECTIONS_LENGTH,
            QuestGiverSpawnBlockEntity.sanitizeDirections("x".repeat(500)).length());
        assertTrue(QuestGiverSpawnBlockEntity.sanitizeDirections("x".repeat(500)).chars().allMatch(c -> c == 'x'));
    }

    @Test
    void theCityIsRequiredBoundedAndPrintable() {
        assertEquals(Optional.of("city_blank"), violation("Iolo", "", "", "female", 5));
        assertEquals(Optional.of("city_blank"), violation("Iolo", "  ", "", "female", 5));
        assertEquals(Optional.of("city_blank"), violation("Iolo", null, "", "female", 5));
        assertEquals(Optional.empty(), violation("Iolo", "x".repeat(QuestGiverSpawnConfigC2SPayload.MAX_TEXT_LENGTH), "", "female", 5));
        assertEquals(Optional.of("city_too_long"),
            violation("Iolo", "x".repeat(QuestGiverSpawnConfigC2SPayload.MAX_TEXT_LENGTH + 1), "", "female", 5));
        assertEquals(Optional.of("city_control_characters"), violation("Iolo", "Brit\nain", "", "female", 5));
        assertEquals(Optional.of("city_control_characters"), violation("Iolo", "Brit" + (char) 7 + "ain", "", "female", 5));
        assertEquals(Optional.empty(), violation("Iolo", "Serpent's Hold", "", "female", 5),
            "punctuation and spaces are printable");
    }

    @Test
    void theRailsApiIdIsBoundedPrintableAndRequiredOnlyForGenericCombat() {
        assertEquals(Optional.of("custom_api_id_missing"), violation("Iolo", "Britain", null, "female", 5));
        assertEquals(Optional.of("custom_api_id_too_long"),
            violation("Iolo", "Britain", "x".repeat(QuestGiverSpawnConfigC2SPayload.MAX_TEXT_LENGTH + 1), "female", 5));
        assertEquals(Optional.of("custom_api_id_control_characters"), violation("Iolo", "Britain", "guard\tcaptain", "female", 5));
        assertEquals(Optional.of("custom_api_id_blank"), violation("Generic Combat", "Britain", "", "male", 5));
        assertEquals(Optional.of("custom_api_id_blank"), violation("Generic Combat", "Britain", "   ", "male", 5));
        assertEquals(Optional.empty(), violation("Generic Combat", "Britain", "guard_captain", "male", 5));
        assertEquals(Optional.empty(), violation("Generic Escort", "Britain", "", "male", 5),
            "an escort needs no api id: the spawner derives it from the route");
    }

    @Test
    void theGenderMustBeOneTheModelKnows() {
        assertEquals(Optional.of("gender_invalid"), violation("Iolo", "Britain", "", "Male", 5));
        assertEquals(Optional.of("gender_invalid"), violation("Iolo", "Britain", "", "other", 5));
        assertEquals(Optional.of("gender_invalid"), violation("Iolo", "Britain", "", "", 5));
        assertEquals(Optional.of("gender_invalid"), violation("Iolo", "Britain", "", null, 5));
        assertEquals(Optional.empty(), violation("Iolo", "Britain", "", "male", 5));
        assertEquals(Optional.empty(), violation("Iolo", "Britain", "", "female", 5));
    }

    @Test
    void theWanderRadiusMustBeALeashTheSpawnerCanEnforce() {
        assertEquals(Optional.empty(), violation("Iolo", "Britain", "", "female", 0),
            "0 keeps the NPC at its post and was accepted before M1");
        assertEquals(Optional.of("spawn_radius_out_of_range"), violation("Iolo", "Britain", "", "female", -1),
            "-1 would mean 'no restriction' to the mob's leash");
        assertEquals(Optional.of("spawn_radius_out_of_range"), violation("Iolo", "Britain", "", "female", Integer.MIN_VALUE));
        assertEquals(Optional.of("spawn_radius_out_of_range"),
            violation("Iolo", "Britain", "", "female", QuestGiverSpawnConfigC2SPayload.MAX_SPAWN_RADIUS + 1));
        assertEquals(Optional.of("spawn_radius_out_of_range"), violation("Iolo", "Britain", "", "female", Integer.MAX_VALUE));
        assertEquals(Optional.empty(), violation("Iolo", "Britain", "", "female", QuestGiverSpawnConfigC2SPayload.MIN_SPAWN_RADIUS));
        assertEquals(Optional.empty(), violation("Iolo", "Britain", "", "female", QuestGiverSpawnConfigC2SPayload.MAX_SPAWN_RADIUS));
    }

    @Test
    void aMissingPositionIsRefusedFirst() {
        QuestGiverSpawnConfigC2SPayload payload = new QuestGiverSpawnConfigC2SPayload(null, "Rowan", "", null, null, 0);
        assertEquals(Optional.of("pos_missing"), payload.shapeViolation());
    }

    @Test
    void anyPayloadStillDecodesAndValidationIsNotPartOfTheCodec() {
        QuestGiverSpawnConfigC2SPayload valid =
            new QuestGiverSpawnConfigC2SPayload(POS, "Generic Combat", "Trinsic", "guard_captain", "male", 12);
        QuestGiverSpawnConfigC2SPayload hinted =
            new QuestGiverSpawnConfigC2SPayload(POS, "Rowan", "Britain", "", "female", 5, "Well behind the mill");
        QuestGiverSpawnConfigC2SPayload crafted =
            new QuestGiverSpawnConfigC2SPayload(POS, "Blackthorn", "", "", "other", -1, "x".repeat(400));

        for (QuestGiverSpawnConfigC2SPayload sent : List.of(valid, hinted, crafted)) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                QuestGiverSpawnConfigC2SPayload.STREAM_CODEC.encode(buf, sent);
                QuestGiverSpawnConfigC2SPayload received = QuestGiverSpawnConfigC2SPayload.STREAM_CODEC.decode(buf);
                assertEquals(sent, received);
                assertEquals(sent.isValidShape(), received.isValidShape());
                assertFalse(buf.isReadable(), "the decoder must consume the whole payload");
            } finally {
                buf.release();
            }
        }
        assertTrue(valid.isValidShape());
        assertTrue(hinted.isValidShape());
        assertFalse(crafted.isValidShape(), "a crafted packet decodes fine and is refused afterwards");
    }

    /**
     * M6 appended the directions hint to a packet that already existed. The bytes a client without
     * a hint sends are unchanged, and a payload carrying no hint encodes to exactly those bytes, so
     * a client that has never heard of the field still configures a spawner.
     */
    @Test
    void aClientThatSendsNoHintStillConfiguresASpawner() {
        FriendlyByteBuf preM6 = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf hintless = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BlockPos.STREAM_CODEC.encode(preM6, POS);
            ByteBufCodecs.STRING_UTF8.encode(preM6, "Rowan");
            ByteBufCodecs.STRING_UTF8.encode(preM6, "Britain");
            ByteBufCodecs.STRING_UTF8.encode(preM6, "");
            ByteBufCodecs.STRING_UTF8.encode(preM6, "female");
            ByteBufCodecs.INT.encode(preM6, 5);
            byte[] preM6Bytes = new byte[preM6.readableBytes()];
            preM6.getBytes(preM6.readerIndex(), preM6Bytes);

            QuestGiverSpawnConfigC2SPayload decoded = QuestGiverSpawnConfigC2SPayload.STREAM_CODEC.decode(preM6);
            assertFalse(preM6.isReadable(), "the pre-M6 packet is complete without a hint");
            assertEquals("Rowan", decoded.npcName());
            assertEquals("Britain", decoded.cityName());
            assertEquals("female", decoded.gender());
            assertEquals(5, decoded.spawnRadius());
            assertEquals("", decoded.directions(), "an absent hint decodes to none, never to a failure");
            assertTrue(decoded.isValidShape(), "an old client must still be able to configure a spawner");

            QuestGiverSpawnConfigC2SPayload.STREAM_CODEC.encode(hintless,
                new QuestGiverSpawnConfigC2SPayload(POS, "Rowan", "Britain", "", "female", 5));
            byte[] hintlessBytes = new byte[hintless.readableBytes()];
            hintless.getBytes(hintless.readerIndex(), hintlessBytes);
            assertArrayEquals(preM6Bytes, hintlessBytes,
                "a save with no hint must be byte-identical to the packet M1 shipped");
        } finally {
            preM6.release();
            hintless.release();
        }
    }

    private static Optional<String> violation(String npcName, String city, String apiId, String gender, int radius) {
        return new QuestGiverSpawnConfigC2SPayload(POS, npcName, city, apiId, gender, radius).shapeViolation();
    }

    private static Optional<String> directionsViolation(String directions) {
        return new QuestGiverSpawnConfigC2SPayload(POS, "Rowan", "Britain", "", "female", 5, directions)
            .shapeViolation();
    }
}
