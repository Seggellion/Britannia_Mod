package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.block.entity.QuestGiverSpawnBlockEntity;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
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
        assertEquals(List.of("Zorathiel", "Lord British", "Iolo", "Dupre", "Shamino", "Generic Escort", "Generic Combat"),
            QuestGiverSpawnBlockEntity.SUPPORTED_ARCHETYPES,
            "adding an archetype (Rowan included) is a deliberate change on the screen AND the server");

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
        assertEquals(Optional.of("archetype_unsupported"), violation("Rowan", "Britain", "", "female", 5));
        assertEquals(Optional.of("archetype_unsupported"), violation("iolo", "Britain", "", "female", 5));
        assertEquals(Optional.of("archetype_unsupported"), violation("Iolo ", "Britain", "", "female", 5));
        assertEquals(Optional.of("archetype_blank"), violation("", "Britain", "", "female", 5));
        assertEquals(Optional.of("archetype_blank"), violation("   ", "Britain", "", "female", 5));
        assertEquals(Optional.of("archetype_blank"), violation(null, "Britain", "", "female", 5));
        assertFalse(QuestGiverSpawnBlockEntity.supportsArchetype(null));
        assertFalse(QuestGiverSpawnBlockEntity.supportsArchetype("Rowan"));
        assertTrue(QuestGiverSpawnBlockEntity.supportsArchetype("Generic Escort"));
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
    void theWireFormatIsUnchangedAndValidationIsNotPartOfIt() {
        QuestGiverSpawnConfigC2SPayload valid =
            new QuestGiverSpawnConfigC2SPayload(POS, "Generic Combat", "Trinsic", "guard_captain", "male", 12);
        QuestGiverSpawnConfigC2SPayload crafted =
            new QuestGiverSpawnConfigC2SPayload(POS, "Rowan", "", "", "other", -1);

        for (QuestGiverSpawnConfigC2SPayload sent : List.of(valid, crafted)) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                QuestGiverSpawnConfigC2SPayload.STREAM_CODEC.encode(buf, sent);
                QuestGiverSpawnConfigC2SPayload received = QuestGiverSpawnConfigC2SPayload.STREAM_CODEC.decode(buf);
                assertEquals(sent, received);
                assertEquals(sent.isValidShape(), received.isValidShape());
            } finally {
                buf.release();
            }
        }
        assertTrue(valid.isValidShape());
        assertFalse(crafted.isValidShape(), "a crafted packet decodes fine and is refused afterwards");
    }

    private static Optional<String> violation(String npcName, String city, String apiId, String gender, int radius) {
        return new QuestGiverSpawnConfigC2SPayload(POS, npcName, city, apiId, gender, radius).shapeViolation();
    }
}
