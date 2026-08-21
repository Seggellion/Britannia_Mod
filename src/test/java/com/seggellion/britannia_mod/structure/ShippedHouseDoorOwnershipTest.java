package com.seggellion.britannia_mod.structure;

import com.seggellion.britannia_mod.util.HouseUtil;
import com.seggellion.britannia_mod.util.StructureUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 10, step 5: every door in every shipped house answers to its own house.
 *
 * <h2>Why this is the load-bearing half</h2>
 * A door's key decision starts at {@code LockableDoorBlock}, which calls {@code HouseUtil.findLot}
 * — and that is {@code enclosingStructure} followed by a lot lookup inside the region it returned.
 * So which house a door belongs to is decided entirely by region containment, and everything after
 * it (whose lot, whether it is private, whether the held key matches) reads off that one answer.
 * Get the containment wrong for a door and no key can ever open it; get it right and the existing
 * lock tests carry the rest.
 *
 * <p>{@code ShippedHouseDoorReachTest} already measures that every shipped door lies inside its
 * own structure box. That is geometry. This drives the actual lookup, on every door of every one
 * of the ten houses, at real placement coordinates and in every rotation — because a door is
 * resolved at whatever angle the player placed the building, not at zero.
 *
 * <p>The in-world half — a key turning in a lock, a stranger refused — is
 * {@code HouseDoorLotBindingGameTests} and {@code HouseDoorRedstoneGameTests}, which drive real
 * doors at representative positions including a deep castle door and a metal double.
 */
class ShippedHouseDoorOwnershipTest {

    private static final Path STRUCTURES = Path.of(System.getProperty("britannia.projectDir", "."))
            .resolve("src/main/resources/data/britannia_mod/structures");

    private static final UUID OWNER = UUID.fromString("0a0a0a0a-0000-0000-0000-00000000000a");
    private static final UUID STRANGER = UUID.fromString("0b0b0b0b-0000-0000-0000-00000000000b");

    /** Somewhere unremarkable and far from the origin, so no coordinate is accidentally zero. */
    private static final BlockPos PLACED_AT = new BlockPos(2048, 68, -3072);

    @BeforeEach
    void clearRegions() {
        StructureRegionManager.getChunkStructureMap().clear();
    }

    /* ------------------------------------------------------------------ */

    /**
     * Every door of every house, in every rotation, resolves to the house it was built into.
     *
     * <p>Rotation is part of the claim rather than a flourish: the region box and the door both
     * move when a house is turned, and they have to move together. A structure whose declared size
     * disagrees with its NBT would pass at rotation zero and lose its own doors at ninety degrees.
     */
    @Test
    void everyDoorInEveryShippedHouseResolvesToItsOwnHouseInEveryRotation() throws IOException {
        List<String> lost = new ArrayList<>();
        int doorsChecked = 0;

        for (HouseStyle style : HouseStyle.values()) {
            CompoundTag structure = read(style);
            List<int[]> doors = doorPositions(structure);
            assertFalse(doors.isEmpty(), style + " ships no doors at all");

            for (Rotation rotation : Rotation.values()) {
                StructureRegionManager.getChunkStructureMap().clear();
                UUID houseUuid = UUID.randomUUID();
                StructureRegionManager.registerStructure(house(style, rotation, houseUuid, OWNER));

                for (int[] door : doors) {
                    BlockPos placed = PLACED_AT.offset(
                            StructureUtils.getRotatedDoorOffset(rotation,
                                    new BlockPos(door[0], door[1], door[2])));
                    doorsChecked++;

                    StructureRecord found = HouseUtil.enclosingStructure(Level.OVERWORLD, placed);
                    if (found == null) {
                        lost.add(style + " " + rotation + " door " + describe(door)
                                + ": no house contains it, so no key could ever open it");
                    } else if (!houseUuid.equals(found.getHouseUuid())) {
                        lost.add(style + " " + rotation + " door " + describe(door)
                                + ": resolved a different house");
                    }
                }
            }
        }

        assertTrue(lost.isEmpty(), () -> lost.size() + " shipped doors do not answer to their own "
                + "house:" + System.lineSeparator() + "  "
                + String.join(System.lineSeparator() + "  ", lost));
        assertTrue(doorsChecked >= 4 * 10,
                "only " + doorsChecked + " doors were checked; the structures stopped being read");
    }

    /**
     * And at every one of those doors, the owner is the owner and nobody else is.
     *
     * <p>The same region answer feeds {@code HouseBuildRights}, so this is the second thing a door
     * position decides. A door that resolved the right house but the wrong owner would lock its
     * owner out and let a stranger in.
     */
    @Test
    void everyDoorAnswersToItsOwnerAndToNobodyElse() throws IOException {
        for (HouseStyle style : HouseStyle.values()) {
            CompoundTag structure = read(style);
            StructureRegionManager.getChunkStructureMap().clear();
            StructureRegionManager.registerStructure(
                    house(style, Rotation.NONE, UUID.randomUUID(), OWNER));

            for (int[] door : doorPositions(structure)) {
                BlockPos placed = PLACED_AT.offset(door[0], door[1], door[2]);
                assertTrue(HouseBuildRights.ownsHouseAt(Level.OVERWORLD, placed, OWNER),
                        style + " refused its owner at the door " + describe(door));
                assertFalse(HouseBuildRights.ownsHouseAt(Level.OVERWORLD, placed, STRANGER),
                        style + " accepted a stranger at the door " + describe(door));
            }
        }
    }

    /**
     * A neighbouring house does not take them, however close its lot ends up.
     *
     * <p>This is the defect the region rule replaced: proximity picked whichever lot block was
     * nearest, so two houses built wall to wall could swap doors. Containment cannot, and the
     * houses here are placed touching so that the old rule would have had every chance.
     */
    @Test
    void aNeighbourBuiltAgainstTheWallTakesNoneOfTheseDoors() throws IOException {
        for (HouseStyle style : HouseStyle.values()) {
            CompoundTag structure = read(style);
            StructureRegionManager.getChunkStructureMap().clear();

            UUID mine = UUID.randomUUID();
            StructureRegionManager.registerStructure(house(style, Rotation.NONE, mine, OWNER));
            // Wall to wall along X, owned by somebody else.
            BlockPos beside = PLACED_AT.offset(style.getWidth(), 0, 0);
            StructureRegionManager.registerStructure(
                    house(style, Rotation.NONE, UUID.randomUUID(), STRANGER, beside));

            for (int[] door : doorPositions(structure)) {
                BlockPos placed = PLACED_AT.offset(door[0], door[1], door[2]);
                StructureRecord found = HouseUtil.enclosingStructure(Level.OVERWORLD, placed);
                assertEquals(mine, found == null ? null : found.getHouseUuid(),
                        style + " lost the door " + describe(door) + " to the house next door");
            }
        }
    }

    /* ------------------------------------------------------------------ */

    private static StructureRecord house(HouseStyle style, Rotation rotation,
                                         UUID houseUuid, UUID owner) {
        return house(style, rotation, houseUuid, owner, PLACED_AT);
    }

    /** Built exactly the way StructurePlacer builds one, so the box is the shipped box. */
    private static StructureRecord house(HouseStyle style, Rotation rotation,
                                         UUID houseUuid, UUID owner, BlockPos origin) {
        StructureBoxes boxes = StructureUtils.makeStructureBoxes(origin,
                new Vec3i(style.getWidth(), style.getHeight(), style.getDepth()), rotation);
        return new StructureRecord(owner, boxes.structureBox(), boxes.fullBox(), houseUuid,
                style.getSize().name().toLowerCase(java.util.Locale.ROOT), style.name(), null, 0,
                Level.OVERWORLD);
    }

    private static CompoundTag read(HouseStyle style) throws IOException {
        Path path = STRUCTURES.resolve(style.getStructureFile());
        assertTrue(Files.exists(path), "shipped structure missing: " + path);
        try (InputStream in = Files.newInputStream(path);
             DataInputStream data = new DataInputStream(in)) {
            return NbtIo.read(data);
        }
    }

    /** Lower halves only: the upper half is the same door counted twice. */
    private static List<int[]> doorPositions(CompoundTag structure) {
        ListTag palette = structure.getList("palette", Tag.TAG_COMPOUND);
        boolean[] isLowerDoor = new boolean[palette.size()];
        for (int index = 0; index < palette.size(); index++) {
            CompoundTag entry = palette.getCompound(index);
            if (!entry.getString("Name").contains("door")) continue;
            isLowerDoor[index] = "lower".equals(entry.getCompound("Properties").getString("half"));
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
