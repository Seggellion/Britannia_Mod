package com.seggellion.britannia_mod.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 4: what a house style claims about itself, against the structure it actually ships.
 *
 * <p>A style is a row of metadata -- structure file, size, lot offset, entrance -- and every
 * field of it can be wrong in a way nothing else notices. A size that disagrees with the NBT
 * gives the house a bounding box that does not match the building, so its region protects thin
 * air on one side and stops short on the other. An entrance offset pointing at the wrong block
 * lands the house somewhere the player did not aim. Neither throws.
 *
 * <p>So every style is checked against its own file, by the same test, rather than the three new
 * houses getting their own copy of it. The villa, patio and keep are the reason this exists;
 * they are not special cases within it.
 */
class HouseStyleRegistrationTest {

    private static final Path STRUCTURES = Path.of(System.getProperty("britannia.projectDir", "."))
            .resolve("src/main/resources/data/britannia_mod/structures");

    /* ------------------------------------------------------------------ */
    /*  Every style, against its own structure                             */
    /* ------------------------------------------------------------------ */

    @ParameterizedTest
    @EnumSource(HouseStyle.class)
    void theStructureExists(HouseStyle style) {
        assertTrue(Files.exists(STRUCTURES.resolve(style.getStructureFile())),
                style + " points at " + style.getStructureFile() + ", which is not shipped");
    }

    @ParameterizedTest
    @EnumSource(HouseStyle.class)
    void theDeclaredSizeMatchesTheNbt(HouseStyle style) throws IOException {
        int[] size = size(read(style));
        assertEquals(size[0], style.getWidth(), style + " width disagrees with its structure");
        assertEquals(size[1], style.getHeight(), style + " height disagrees with its structure");
        assertEquals(size[2], style.getDepth(), style + " depth disagrees with its structure");
    }

    @ParameterizedTest
    @EnumSource(HouseStyle.class)
    void everyBlockLiesInsideTheDeclaredBoundingBox(HouseStyle style) throws IOException {
        CompoundTag structure = read(style);
        ListTag blocks = structure.getList("blocks", Tag.TAG_COMPOUND);
        for (int index = 0; index < blocks.size(); index++) {
            ListTag pos = blocks.getCompound(index).getList("pos", Tag.TAG_INT);
            int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
            assertTrue(x >= 0 && x < style.getWidth()
                            && y >= 0 && y < style.getHeight()
                            && z >= 0 && z < style.getDepth(),
                    style + " has a block at (" + x + "," + y + "," + z + ") outside its declared "
                            + style.getWidth() + "x" + style.getHeight() + "x" + style.getDepth());
        }
    }

    @ParameterizedTest
    @EnumSource(HouseStyle.class)
    void theEntranceOffsetLiesInsideTheStructure(HouseStyle style) {
        BlockPos entrance = style.getDoorOffset();
        assertTrue(entrance.getX() >= 0 && entrance.getX() < style.getWidth()
                        && entrance.getZ() >= 0 && entrance.getZ() < style.getDepth(),
                style + " declares an entrance at " + entrance + ", outside its own footprint");
    }

    /**
     * The offset has to name the real front door, not merely a block inside the house.
     *
     * <p>An offset of {@code (x, 0, z)} means the door at {@code (x, 1, z + 1)} -- which is what
     * {@code (4,0,0)} has always meant for a small house whose door is at {@code (4,1,1)}. If it
     * points anywhere else the house lands offset from where the player aimed, by exactly the
     * distance between the two.
     */
    @ParameterizedTest
    @EnumSource(HouseStyle.class)
    void theEntranceOffsetPointsAtAnActualDoor(HouseStyle style) throws IOException {
        BlockPos entrance = style.getDoorOffset();
        BlockPos expectedDoor = new BlockPos(entrance.getX(), 1, entrance.getZ() + 1);

        List<BlockPos> doors = groundFloorDoors(read(style));
        assertTrue(doors.contains(expectedDoor),
                style + " declares its entrance at " + entrance + ", which means a door at "
                        + expectedDoor + ". Its ground-floor doors are at " + doors + ".");
    }

    /** The lot marker must land on air, not inside the building it is marking. */
    @ParameterizedTest
    @EnumSource(HouseStyle.class)
    void theLotBlockLandsOnAir(HouseStyle style) throws IOException {
        int lotX = style.getWidth() / 2 + style.getLotOffsetX();
        BlockPos lot = new BlockPos(lotX, 1, 0);

        assertTrue(lotX >= 0 && lotX < style.getWidth(),
                style + " puts its lot block at x=" + lotX + ", outside its footprint");
        assertEquals("minecraft:air", blockAt(read(style), lot),
                style + " puts its lot block at " + lot + ", which is not air -- placing it would "
                        + "destroy part of the house");
    }

    /* ------------------------------------------------------------------ */
    /*  Deed identity                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * Deeds are registered by walking this enum, so the mapping cannot drift -- but the id that
     * walk produces is what the item model and the language file have to be named after, and
     * those are hand-written.
     */
    @ParameterizedTest
    @EnumSource(HouseStyle.class)
    void theDeedForThisStyleHasItsModelAndItsName(HouseStyle style) throws IOException {
        String deedId = style.getStructureFile().replace(".nbt", "") + "_deed";
        Path assets = Path.of(System.getProperty("britannia.projectDir", "."))
                .resolve("src/main/resources/assets/britannia_mod");

        assertTrue(Files.exists(assets.resolve("models/item/" + deedId + ".json")),
                style + "'s deed " + deedId + " has no item model, so it shows as a purple cube");
        assertTrue(Files.readString(assets.resolve("lang/en_us.json"))
                        .contains("\"item.britannia_mod." + deedId + "\""),
                style + "'s deed " + deedId + " has no name, so it shows as item.britannia_mod."
                        + deedId);
    }

    /* ------------------------------------------------------------------ */
    /*  The three new houses, and the ones that were already here          */
    /* ------------------------------------------------------------------ */

    @Test
    void thethreeLargerHousesAreRegistered() {
        assertEquals("two_story_villa.nbt", HouseStyle.VILLA.getStructureFile());
        assertEquals("large_patio.nbt", HouseStyle.PATIO.getStructureFile());
        assertEquals("stone_keep.nbt", HouseStyle.KEEP.getStructureFile());

        assertEquals(HouseSize.VILLA, HouseStyle.VILLA.getSize());
        assertEquals(HouseSize.PATIO, HouseStyle.PATIO.getSize());
        assertEquals(HouseSize.KEEP, HouseStyle.KEEP.getSize());

        assertEquals(new BlockPos(3, 0, 5), HouseStyle.VILLA.getDoorOffset());
        assertEquals(new BlockPos(12, 0, 0), HouseStyle.PATIO.getDoorOffset());
        assertEquals(new BlockPos(13, 0, 2), HouseStyle.KEEP.getDoorOffset());
    }

    /**
     * None of the three would have landed where the player aimed under the old rule, which is
     * why the anchor was generalised. Recorded so that reverting the generalisation fails here
     * rather than in a bug report.
     */
    @Test
    void noneOfTheThreeWouldHaveWorkedUnderTheOldCentredAnchor(){
        for (HouseStyle style : List.of(HouseStyle.VILLA, HouseStyle.PATIO, HouseStyle.KEEP)) {
            BlockPos centred = new BlockPos(style.getWidth() / 2, 0, 0);
            assertTrue(!centred.equals(style.getDoorOffset()),
                    style + " now agrees with the old size/2 anchor; if that is genuinely correct "
                            + "this test should go, but check the structure first");
        }
    }

    @Test
    void theSevenOriginalStylesAreUnchanged() {
        for (HouseStyle style : HouseStyle.values()) {
            boolean original = style == HouseStyle.CASTLE || style.getSize() == HouseSize.SMALL;
            if (!original) continue;
            assertEquals(new BlockPos(style.getWidth() / 2, 0, 0), style.getDoorOffset(),
                    style + " moved its entrance; existing houses must land exactly where they did");
        }
        assertEquals(HouseSize.SMALL, HouseStyle.SMALL_BRICK.getSize());
        assertEquals(-2, HouseStyle.SMALL_BRICK.getLotOffsetX());
        assertEquals(HouseSize.CASTLE, HouseStyle.CASTLE.getSize());
        assertEquals(-3, HouseStyle.CASTLE.getLotOffsetX());
    }

    /**
     * The bounding-box maths turns width and depth over each other on a quarter turn, so it
     * takes a non-square footprint to prove it swaps them. The keep carried this check until
     * its 2026-08 rebuild squared it at 26x26; the castle, a block deeper than it is wide,
     * carries it now. A box built from the wrong dimension is 35 wide where the building is
     * 34, and one block short the other way.
     */
    @Test
    void theCastlesNonSquareFootprintRotatesCorrectly() {
        HouseStyle castle = HouseStyle.CASTLE;
        assertEquals(34, castle.getWidth());
        assertEquals(35, castle.getDepth());

        BlockPos origin = new BlockPos(0, 64, 0);
        net.minecraft.core.Vec3i size =
                new net.minecraft.core.Vec3i(castle.getWidth(), castle.getHeight(), castle.getDepth());

        for (Rotation rotation : Rotation.values()) {
            var boxes = com.seggellion.britannia_mod.util.StructureUtils
                    .makeStructureBoxes(origin, size, rotation);
            var box = boxes.structureBox();

            double spanX = box.maxX - box.minX;
            double spanZ = box.maxZ - box.minZ;
            boolean quarterTurn = rotation == Rotation.CLOCKWISE_90
                    || rotation == Rotation.COUNTERCLOCKWISE_90;

            assertEquals(quarterTurn ? castle.getDepth() : castle.getWidth(), spanX,
                    "castle X span wrong under " + rotation);
            assertEquals(quarterTurn ? castle.getWidth() : castle.getDepth(), spanZ,
                    "castle Z span wrong under " + rotation);
            assertEquals(castle.getHeight(), box.maxY - box.minY,
                    "castle height changed under " + rotation);
            assertEquals(box.minY - 10.0D, boxes.fullBox().minY,
                    "the basement should reach ten blocks below, whatever the rotation");
        }
    }

    /* ------------------------------------------------------------------ */

    private static List<BlockPos> groundFloorDoors(CompoundTag structure) {
        ListTag palette = structure.getList("palette", Tag.TAG_COMPOUND);
        boolean[] lowerDoor = new boolean[palette.size()];
        for (int index = 0; index < palette.size(); index++) {
            CompoundTag entry = palette.getCompound(index);
            lowerDoor[index] = entry.getString("Name").contains("door")
                    && "lower".equals(entry.getCompound("Properties").getString("half"));
        }

        List<BlockPos> doors = new ArrayList<>();
        ListTag blocks = structure.getList("blocks", Tag.TAG_COMPOUND);
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            if (!lowerDoor[block.getInt("state")]) continue;
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            doors.add(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)));
        }
        return doors;
    }

    private static String blockAt(CompoundTag structure, BlockPos wanted) {
        ListTag palette = structure.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = structure.getList("blocks", Tag.TAG_COMPOUND);
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            if (pos.getInt(0) == wanted.getX() && pos.getInt(1) == wanted.getY()
                    && pos.getInt(2) == wanted.getZ()) {
                return palette.getCompound(block.getInt("state")).getString("Name");
            }
        }
        return "<nothing>";
    }

    private static int[] size(CompoundTag structure) {
        ListTag size = structure.getList("size", Tag.TAG_INT);
        return new int[] { size.getInt(0), size.getInt(1), size.getInt(2) };
    }

    private static CompoundTag read(HouseStyle style) throws IOException {
        Path path = STRUCTURES.resolve(style.getStructureFile());
        assertNotNull(path);
        try (InputStream in = Files.newInputStream(path);
             DataInputStream data = new DataInputStream(in)) {
            return NbtIo.read(data);
        }
    }
}
