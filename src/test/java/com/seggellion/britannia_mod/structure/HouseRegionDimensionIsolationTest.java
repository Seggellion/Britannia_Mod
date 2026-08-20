package com.seggellion.britannia_mod.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.util.HouseUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
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
 * Two houses at the same coordinates in different worlds are two houses.
 *
 * <p>Regions were indexed by chunk alone, which is an identity only if there is one world. A
 * house at X/Z in the Overworld and a house at the same X/Z in the Nether shared a key and
 * therefore shared each other's list: a door in one resolved the other's house, an owner's
 * build rights reached across, and unregistering either emptied the entry both lived in.
 *
 * <p>Nether coordinates are not exotic here -- the same X/Z is eight times as much ground, so
 * any shard with housing in more than one dimension collides constantly rather than rarely.
 *
 * <p>Every access path is exercised, because a fix that only changed the map key would still
 * leave whichever lookup did its own arithmetic reading across worlds.
 */
class HouseRegionDimensionIsolationTest {

    private static final UUID ALICE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID BOB   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    /** The same spot, in two worlds. */
    private static final BlockPos SPOT = new BlockPos(1200, 64, -900);

    private UUID overworldHouse;
    private UUID netherHouse;

    @BeforeEach
    void twoHousesOneCoordinate() {
        StructureRegionManager.getChunkStructureMap().clear();
        overworldHouse = UUID.randomUUID();
        netherHouse = UUID.randomUUID();
        StructureRegionManager.registerStructure(house(overworldHouse, ALICE, Level.OVERWORLD));
        StructureRegionManager.registerStructure(house(netherHouse, BOB, Level.NETHER));
    }

    /* ------------------------------------------------------------------ */
    /*  Lookup                                                             */
    /* ------------------------------------------------------------------ */

    @Test
    void eachDimensionResolvesItsOwnHouse() {
        StructureRecord above = HouseUtil.enclosingStructure(Level.OVERWORLD, SPOT.offset(4, 1, 4));
        StructureRecord below = HouseUtil.enclosingStructure(Level.NETHER, SPOT.offset(4, 1, 4));

        assertNotNull(above, "the Overworld house did not resolve in the Overworld");
        assertNotNull(below, "the Nether house did not resolve in the Nether");
        assertEquals(overworldHouse, above.getHouseUuid());
        assertEquals(netherHouse, below.getHouseUuid(),
                "the Nether lookup returned the Overworld house; the chunk key is still worldless");
    }

    @Test
    void adimensionWithNoHouseThereResolvesNothing() {
        assertNull(HouseUtil.enclosingStructure(Level.END, SPOT.offset(4, 1, 4)),
                "a dimension with no house at this coordinate found one anyway");
    }

    @Test
    void chunkIndexingKeepsTheTwoApart() {
        List<StructureRecord> above = StructureRegionManager.getStructuresInChunk(
                Level.OVERWORLD, SPOT.getX() >> 4, SPOT.getZ() >> 4);
        List<StructureRecord> below = StructureRegionManager.getStructuresInChunk(
                Level.NETHER, SPOT.getX() >> 4, SPOT.getZ() >> 4);

        assertEquals(1, above.size(), "the Overworld chunk holds " + above.size() + " houses");
        assertEquals(1, below.size(), "the Nether chunk holds " + below.size() + " houses");
        assertEquals(overworldHouse, above.get(0).getHouseUuid());
        assertEquals(netherHouse, below.get(0).getHouseUuid());
    }

    /* ------------------------------------------------------------------ */
    /*  Ownership and rights                                               */
    /* ------------------------------------------------------------------ */

    @Test
    void ownershipDoesNotReachAcrossWorlds() {
        // Alice owns the Overworld house; Bob owns the Nether one at the same coordinate.
        assertTrue(HouseBuildRights.ownsHouseAt(Level.OVERWORLD, SPOT.offset(4, 1, 4), ALICE)
                        != HouseBuildRights.ownsHouseAt(Level.NETHER, SPOT.offset(4, 1, 4), ALICE),
                "Alice's ownership answered the same in both worlds");
        assertTrue(HouseBuildRights.ownsHouseAt(Level.OVERWORLD, SPOT.offset(4, 1, 4), ALICE),
                "Alice does not own her own Overworld house");
        assertTrue(!HouseBuildRights.ownsHouseAt(Level.NETHER, SPOT.offset(4, 1, 4), ALICE),
                "Alice was granted build rights inside Bob's Nether house");
        assertTrue(HouseBuildRights.ownsHouseAt(Level.NETHER, SPOT.offset(4, 1, 4), BOB),
                "Bob does not own his own Nether house");
    }

    /* ------------------------------------------------------------------ */
    /*  Removal and replacement                                            */
    /* ------------------------------------------------------------------ */

    @Test
    void removingOneLeavesTheOtherStanding() {
        StructureRegionManager.unregisterStructure(house(overworldHouse, ALICE, Level.OVERWORLD));

        assertNull(HouseUtil.enclosingStructure(Level.OVERWORLD, SPOT.offset(4, 1, 4)),
                "the Overworld house was not removed");
        StructureRecord survivor = HouseUtil.enclosingStructure(Level.NETHER, SPOT.offset(4, 1, 4));
        assertNotNull(survivor,
                "removing the Overworld house took the Nether house with it -- they shared an entry");
        assertEquals(netherHouse, survivor.getHouseUuid());
    }

    @Test
    void replacingOneDoesNotDisturbTheOther() {
        // Re-registering under the same UUID replaces in place; the neighbour world is untouched.
        StructureRegionManager.registerStructure(house(overworldHouse, ALICE, Level.OVERWORLD));

        assertEquals(1, StructureRegionManager.getStructuresInChunk(
                Level.OVERWORLD, SPOT.getX() >> 4, SPOT.getZ() >> 4).size());
        assertEquals(1, StructureRegionManager.getStructuresInChunk(
                Level.NETHER, SPOT.getX() >> 4, SPOT.getZ() >> 4).size());
    }

    /* ------------------------------------------------------------------ */
    /*  Persistence                                                        */
    /* ------------------------------------------------------------------ */

    @Test
    void theDimensionSurvivesTheTripThroughRails() {
        JsonObject row = new JsonObject();
        row.addProperty("uuid", netherHouse.toString());
        row.addProperty("owner_uuid", BOB.toString());
        row.add("structure", StructureRegionCodec.encode(
                house(netherHouse, BOB, Level.NETHER), SPOT));

        StructureRecord restored = StructureRegionCodec.decode(row).orElseThrow();
        assertEquals(Level.NETHER, restored.getDimension(),
                "a restored house came back in the wrong world, which would put its region on top "
                        + "of whatever stands at those coordinates in the Overworld");
    }

    /**
     * A house row written before regions carried a dimension comes back in the Overworld.
     *
     * <p>Deliberately tolerant: refusing those rows would cost every existing house its region,
     * and every one of them is an Overworld house because placement has only ever accepted grass
     * and sand.
     */
    @Test
    void aRowWithNoDimensionIsTreatedAsOverworld() {
        JsonObject structure = StructureRegionCodec.encode(
                house(overworldHouse, ALICE, Level.OVERWORLD), SPOT);
        structure.remove("dimension");

        JsonObject row = new JsonObject();
        row.addProperty("uuid", overworldHouse.toString());
        row.addProperty("owner_uuid", ALICE.toString());
        row.add("structure", structure);

        assertEquals(Level.OVERWORLD, StructureRegionCodec.decode(row).orElseThrow().getDimension());
    }

    @Test
    void rehydratingBothWorldsKeepsThemSeparate() {
        StructureRegionManager.getChunkStructureMap().clear();

        JsonArray rows = new JsonArray();
        rows.add(railsRow(overworldHouse, ALICE, Level.OVERWORLD));
        rows.add(railsRow(netherHouse, BOB, Level.NETHER));

        StructureRegionRehydrator.Result result = StructureRegionRehydrator.apply(rows);
        assertEquals(2, result.registered(), "both houses should restore");

        assertEquals(overworldHouse,
                HouseUtil.enclosingStructure(Level.OVERWORLD, SPOT.offset(4, 1, 4)).getHouseUuid());
        assertEquals(netherHouse,
                HouseUtil.enclosingStructure(Level.NETHER, SPOT.offset(4, 1, 4)).getHouseUuid());
    }

    /* ------------------------------------------------------------------ */

    private static JsonObject railsRow(UUID houseUuid, UUID owner, ResourceKey<Level> dimension) {
        JsonObject row = new JsonObject();
        row.addProperty("uuid", houseUuid.toString());
        row.addProperty("owner_uuid", owner.toString());
        row.add("structure", StructureRegionCodec.encode(house(houseUuid, owner, dimension), SPOT));
        return row;
    }

    private static StructureRecord house(UUID houseUuid, UUID owner, ResourceKey<Level> dimension) {
        AABB structureBox = new AABB(
                SPOT.getX(), SPOT.getY(), SPOT.getZ(),
                SPOT.getX() + 9, SPOT.getY() + 8, SPOT.getZ() + 9);
        AABB fullBox = new AABB(
                structureBox.minX, structureBox.minY - 10, structureBox.minZ,
                structureBox.maxX, structureBox.maxY, structureBox.maxZ);
        return new StructureRecord(owner, structureBox, fullBox, houseUuid,
                "small", "SMALL_BRICK", null, 0, dimension);
    }
}
