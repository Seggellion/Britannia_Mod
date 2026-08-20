package com.seggellion.britannia_mod.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Housing Deed Milestone 2 -- the record round trip.
 *
 * <p>A house region used to die with the process. It is now written to the Rails house row and
 * read back at boot, and this pins the format on the mod side of that wire: what survives the
 * trip, and what a row has to be missing before it is refused.
 *
 * <p>Rotation is the field these tests care about most. Before this milestone it was stored
 * nowhere at all, and it is the one thing that cannot be recovered from anything else -- the
 * style gives the size, the lot block gives the position, but nothing gives the facing.
 */
class StructureRegionPersistenceTest {

    private static final UUID HOUSE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OWNER = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");
    private static final UUID DEED  = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");

    @BeforeEach
    void clearRegions() {
        StructureRegionManager.getChunkStructureMap().clear();
    }

    /* ------------------------------------------------------------------ */
    /*  Round trip                                                         */
    /* ------------------------------------------------------------------ */

    @Test
    void everyFieldOfARegionSurvivesTheTripThroughRails() {
        StructureRecord original = sampleRecord(270);
        BlockPos origin = new BlockPos(100, 64, -200);

        JsonObject row = railsRow(StructureRegionCodec.encode(original, origin));
        // Through a real serialise/parse cycle, because that is what the wire does to it.
        JsonObject reparsed = JsonParser.parseString(row.toString()).getAsJsonObject();

        StructureRecord restored = StructureRegionCodec.decode(reparsed).orElseThrow();

        assertEquals(original.getHouseUuid(), restored.getHouseUuid());
        assertEquals(original.getOwnerUuid(), restored.getOwnerUuid());
        assertEquals(original.getDeedId(), restored.getDeedId());
        assertEquals(original.getSizeId(), restored.getSizeId());
        assertEquals(original.getStyleId(), restored.getStyleId());
        assertEquals(original.getStructureBox(), restored.getStructureBox());
        assertEquals(original.getFullBox(), restored.getFullBox());
        assertEquals(270, restored.getRotationDeg(),
                "rotation is the one field nothing else can reconstruct");
    }

    @Test
    void allFourRotationsRoundTrip() {
        for (int degrees : new int[] { 0, 90, 180, 270 }) {
            JsonObject row = railsRow(StructureRegionCodec.encode(sampleRecord(degrees), BlockPos.ZERO));
            assertEquals(degrees, StructureRegionCodec.decode(row).orElseThrow().getRotationDeg(),
                    "rotation " + degrees + " did not survive");
        }
    }

    @Test
    void theOriginIsCarriedAlongsideTheBoxes() {
        JsonObject structure = StructureRegionCodec.encode(sampleRecord(90), new BlockPos(7, 65, -9));
        JsonObject origin = structure.getAsJsonObject("origin");

        assertEquals(7, origin.get("x").getAsInt());
        assertEquals(65, origin.get("y").getAsInt());
        assertEquals(-9, origin.get("z").getAsInt());
    }

    /* ------------------------------------------------------------------ */
    /*  What decode refuses                                                */
    /* ------------------------------------------------------------------ */

    @Test
    void aHousePlacedBeforeThisMilestoneIsRefusedRatherThanGuessedAt() {
        JsonObject row = new JsonObject();
        row.addProperty("uuid", HOUSE.toString());
        row.addProperty("owner_uuid", OWNER.toString());
        row.add("structure", null);

        assertTrue(StructureRegionCodec.decode(row).isEmpty(),
                "a row with no structure has no rotation, so its box cannot be rebuilt");
    }

    @Test
    void aRowFromAFutureSchemaIsRefused() {
        JsonObject structure = StructureRegionCodec.encode(sampleRecord(90), BlockPos.ZERO);
        structure.addProperty("schema", StructureRegionCodec.SCHEMA_VERSION + 1);

        assertTrue(StructureRegionCodec.decode(railsRow(structure)).isEmpty());
    }

    @Test
    void aTruncatedBoxIsRefusedRatherThanDefaultedToZero() {
        JsonObject structure = StructureRegionCodec.encode(sampleRecord(90), BlockPos.ZERO);
        structure.getAsJsonObject("full_box").remove("min_y");

        assertTrue(StructureRegionCodec.decode(railsRow(structure)).isEmpty(),
                "a half-read box would silently shrink the region a player lives in");
    }

    @Test
    void aRowWithNoOwnerIsRefused() {
        JsonObject row = railsRow(StructureRegionCodec.encode(sampleRecord(90), BlockPos.ZERO));
        row.remove("owner_uuid");

        assertTrue(StructureRegionCodec.decode(row).isEmpty(),
                "a region with no owner grants build rights to nobody and protection against nobody");
    }

    @Test
    void aRowWithNoDeedStillRestores() {
        JsonObject row = railsRow(StructureRegionCodec.encode(sampleRecord(90), BlockPos.ZERO));
        row.remove("deed_id");

        StructureRecord restored = StructureRegionCodec.decode(row).orElseThrow();
        assertNull(restored.getDeedId(), "a vendor-bought house carries no blessed deed id");
    }

    /* ------------------------------------------------------------------ */
    /*  Applying a whole shard                                             */
    /* ------------------------------------------------------------------ */

    @Test
    void oneUnreadableRowDoesNotCostTheShardTheOthers() {
        JsonObject good = railsRow(StructureRegionCodec.encode(sampleRecord(90), BlockPos.ZERO));

        JsonObject legacy = new JsonObject();
        legacy.addProperty("uuid", UUID.randomUUID().toString());
        legacy.addProperty("owner_uuid", OWNER.toString());

        JsonObject broken = railsRow(StructureRegionCodec.encode(sampleRecord(90), BlockPos.ZERO));
        broken.getAsJsonObject("structure").remove("structure_box");
        broken.addProperty("uuid", UUID.randomUUID().toString());

        JsonArray rows = new JsonArray();
        rows.add(good);
        rows.add(legacy);
        rows.add(broken);

        StructureRegionRehydrator.Result result = StructureRegionRehydrator.apply(rows);

        assertEquals(1, result.registered());
        assertEquals(1, result.withoutStructure());
        assertEquals(1, result.malformed());
        assertEquals(3, result.total());
        assertNotNull(StructureRegionManager.getStructureByUuid(HOUSE));
    }

    @Test
    void rehydratingTwiceLeavesOneRecordPerHouse() {
        JsonArray rows = new JsonArray();
        rows.add(railsRow(StructureRegionCodec.encode(sampleRecord(90), BlockPos.ZERO)));

        StructureRegionRehydrator.apply(rows);
        long afterFirst = copiesOfSampleHouse();
        StructureRegionRehydrator.apply(rows);

        // The region spans more than one chunk and is indexed into each of them, so the
        // count is not 1 -- what matters is that restoring again does not grow it, and that
        // no single chunk ends up holding the same house twice.
        assertEquals(afterFirst, copiesOfSampleHouse(),
                "a second restore must replace the region, not duplicate it");
        long worstChunk = StructureRegionManager.getChunkStructureMap().values().stream()
                .mapToLong(records -> records.stream()
                        .filter(record -> record.getHouseUuid().equals(HOUSE)).count())
                .max().orElse(0L);
        assertEquals(1L, worstChunk, "a chunk held the same house more than once");
    }

    @Test
    void aNullPayloadRestoresNothingAndDoesNotThrow() {
        StructureRegionRehydrator.Result result = StructureRegionRehydrator.apply(null);

        assertEquals(0, result.total());
        assertTrue(StructureRegionManager.getChunkStructureMap().isEmpty());
    }

    /* ------------------------------------------------------------------ */
    /*  Fixtures                                                           */
    /* ------------------------------------------------------------------ */

    private static long copiesOfSampleHouse() {
        return StructureRegionManager.getChunkStructureMap().values().stream()
                .flatMap(List::stream)
                .filter(record -> record.getHouseUuid().equals(HOUSE))
                .count();
    }

    private static StructureRecord sampleRecord(int rotationDeg) {
        // The same relationship StructureUtils.makeStructureBoxes produces: the full box is the
        // structure box dropped ten blocks, which is the basement the owner is allowed to dig.
        AABB structureBox = new AABB(100.0D, 64.0D, -200.0D, 109.0D, 72.0D, -191.0D);
        AABB fullBox = new AABB(
                structureBox.minX, structureBox.minY - 10.0D, structureBox.minZ,
                structureBox.maxX, structureBox.maxY, structureBox.maxZ);

        return new StructureRecord(OWNER, structureBox, fullBox, HOUSE, "small", "SMALL_BRICK",
                DEED, rotationDeg);
    }

    /** One row as {@code Api::HousesController#index} renders it. */
    private static JsonObject railsRow(JsonObject structure) {
        JsonObject row = new JsonObject();
        row.addProperty("uuid", HOUSE.toString());
        row.addProperty("owner_uuid", OWNER.toString());
        row.addProperty("deed_id", DEED.toString());
        row.addProperty("house_type", "small_brick");
        row.add("structure", structure);
        return row;
    }
}
