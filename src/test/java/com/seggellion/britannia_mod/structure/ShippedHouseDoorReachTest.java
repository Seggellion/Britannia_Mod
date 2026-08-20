package com.seggellion.britannia_mod.structure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the shipped structures and measures, on real data, the thing that used to decide
 * whether a door could be unlocked.
 *
 * <p>{@code HouseUtil} used to find a door's house by scanning 4 blocks either way in X and Z
 * and 2 in Y for any lot block at all. A door outside that cube found nothing, took
 * {@code LockableDoorBlock}'s "not a private house" branch, and was refused without a key ever
 * being consulted -- while every shipped structure bakes {@code Locked:1} into its door NBT.
 *
 * <p>The two assertions here are opposite sides of the same change. The old rule genuinely
 * failed on shipped content, so it is measured rather than described. And the rule that
 * replaced it -- the door belongs to the house whose region contains it -- holds for every
 * door in every shipped house, which is what makes it a safe replacement rather than a bigger
 * number.
 */
class ShippedHouseDoorReachTest {

    private static final Path STRUCTURES = Path.of(System.getProperty("britannia.projectDir", "."))
            .resolve("src/main/resources/data/britannia_mod/structures");

    /** The cube the old lookup searched, from HouseUtil before it was replaced. */
    private static final int OLD_RADIUS_XZ = 4;
    private static final int OLD_RADIUS_Y = 2;

    @Test
    void everyDoorInEveryShippedHouseSitsInsideItsOwnStructure() throws IOException {
        for (HouseStyle style : HouseStyle.values()) {
            CompoundTag structure = read(style);
            int[] size = size(structure);

            for (int[] door : doorPositions(structure)) {
                assertTrue(door[0] >= 0 && door[0] < size[0]
                                && door[1] >= 0 && door[1] < size[1]
                                && door[2] >= 0 && door[2] < size[2],
                        style + " has a door at " + describe(door) + " outside its own "
                                + size[0] + "x" + size[1] + "x" + size[2] + " structure box, so no "
                                + "region containment test could ever resolve it");
            }
        }
    }

    @Test
    void theCastleAlreadyHadDoorsTheOldProximityRuleCouldNotReach() throws IOException {
        HouseStyle style = HouseStyle.CASTLE;
        CompoundTag structure = read(style);
        int[] lot = lotPosition(style, size(structure));

        List<int[]> unreachable = new ArrayList<>();
        for (int[] door : doorPositions(structure)) {
            if (!withinOldScan(door, lot)) unreachable.add(door);
        }

        assertFalse(unreachable.isEmpty(),
                "the castle was the evidence that a fixed radius cannot work; if none of its "
                        + "doors is out of reach any more, this test has stopped measuring anything");
        assertTrue(unreachable.size() >= 3,
                "expected the castle's interior doors to be out of the old cube, found "
                        + unreachable.size() + " of " + doorPositions(structure).size());
    }

    @Test
    void theSmallHousesAreWhyNobodyNoticed() throws IOException {
        for (HouseStyle style : HouseStyle.values()) {
            if (style == HouseStyle.CASTLE) continue;

            CompoundTag structure = read(style);
            int[] lot = lotPosition(style, size(structure));
            for (int[] door : doorPositions(structure)) {
                assertTrue(withinOldScan(door, lot),
                        style + " door at " + describe(door) + " was already out of the old cube; "
                                + "the small houses are supposed to be the case that worked");
            }
        }
    }

    /* ------------------------------------------------------------------ */

    private static boolean withinOldScan(int[] door, int[] lot) {
        return Math.abs(door[0] - lot[0]) <= OLD_RADIUS_XZ
                && Math.abs(door[1] - lot[1]) <= OLD_RADIUS_Y
                && Math.abs(door[2] - lot[2]) <= OLD_RADIUS_XZ;
    }

    /** Where StructurePlacer puts the lot block, in un-rotated template space. */
    private static int[] lotPosition(HouseStyle style, int[] size) {
        return new int[] { size[0] / 2 + style.getLotOffsetX(), 1, 0 };
    }

    private static CompoundTag read(HouseStyle style) throws IOException {
        Path path = STRUCTURES.resolve(style.getStructureFile());
        assertTrue(Files.exists(path), "shipped structure missing: " + path);
        try (InputStream in = Files.newInputStream(path);
             DataInputStream data = new DataInputStream(in)) {
            return NbtIo.read(data);
        }
    }

    private static int[] size(CompoundTag structure) {
        ListTag size = structure.getList("size", Tag.TAG_INT);
        return new int[] { size.getInt(0), size.getInt(1), size.getInt(2) };
    }

    /** Lower halves only: the upper half is the same door counted twice. */
    private static List<int[]> doorPositions(CompoundTag structure) {
        ListTag palette = structure.getList("palette", Tag.TAG_COMPOUND);
        boolean[] isLowerDoor = new boolean[palette.size()];
        for (int index = 0; index < palette.size(); index++) {
            CompoundTag entry = palette.getCompound(index);
            String name = entry.getString("Name");
            if (!name.contains("door")) continue;
            CompoundTag properties = entry.getCompound("Properties");
            isLowerDoor[index] = "lower".equals(properties.getString("half"));
        }

        List<int[]> doors = new ArrayList<>();
        ListTag blocks = structure.getList("blocks", Tag.TAG_COMPOUND);
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            if (!isLowerDoor[block.getInt("state")]) continue;
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            doors.add(new int[] { pos.getInt(0), pos.getInt(1), pos.getInt(2) });
        }
        return doors;
    }

    private static String describe(int[] pos) {
        return "(" + pos[0] + "," + pos[1] + "," + pos[2] + ")";
    }
}
